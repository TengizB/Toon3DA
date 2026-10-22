package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.door.DoorManager;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.render.tilesetgfx.RegionWallAtlas;
import ge.tbegvadze.toon3d.tileset.RegionWallSet;
import ge.tbegvadze.toon3d.tileset.RegionWallSets;
import ge.tbegvadze.toon3d.tileset.WallGroup;
import ge.tbegvadze.toon3d.tileset.WallSlot;
import ge.tbegvadze.toon3d.tileset.WallSlotTable;
import ge.tbegvadze.toon3d.util.ArtDirection;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.RenderResolution;
import ge.tbegvadze.toon3d.util.TilesetConstants;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;

import static ge.tbegvadze.toon3d.util.Constants.*;
import static ge.tbegvadze.toon3d.util.RenderConstants.*;

/**
 * Renders the Doom-style 3D wall projection for one frame.
 *
 * Pipeline per frame (single SpriteBatch; generated-wall-art order 2):
 *   1. BASE pass — one draw per screen column (equal neighbouring slices merged, R18), unblended on the
 *      opaque region atlas (R16): every wall, door and column face of the floor's region set, with its BASE
 *      variant per face and its distance mip (0/1/2) picked by pure hashes (R6). One texture bind.
 *   2. MUZZLE LIGHT additive pass (only while a pulse is active).
 *   3. GLOW pass — the region set's emissive layer from the glow atlas, NORMAL blend, ungraded, distance-
 *      faded and BLACKOUT-dimmed, with animated frames. One texture bind.
 *   See docs/wall-art-creation-guide.txt section 13 and docs/wall-renderer-guide.txt.
 *
 * Texture column derivation (see GameMath for full proofs):
 *   wallHitPosition  — fractional position [0,1) along the struck wall face
 *   wallHitMirrored  — corrected for direction so the texture reads consistently from all sides
 *   texColumn        — (int)(wallHitMirrored × textureWidth), clamped to [0, width−1]
 *
 * Camera-plane sign convention (Y-up world):
 *   planeX = −dirY × scale,  planeY = dirX × scale      (CCW-rotated direction)
 *   cameraParameter = 1 − 2 × column / screenWidth       (INVERTED vs Lodev Y-down)
 *   See GameMath.cameraPlaneX/Y and GameMath.cameraPlaneParameter for derivations.
 *
 * Multithreading:
 *   The per-column DDA computation (ray casting + shading math) runs across a fixed worker pool
 *   — same structured-concurrency pattern used by FloorCeilingRenderer. Each worker fills a
 *   disjoint range of columnResults[] and zBuffer[]; no synchronisation is needed during the
 *   compute phase. A Phaser barrier ensures all columns are computed before the GL-thread draw
 *   pass begins. All SpriteBatch calls happen on the GL thread only.
 */
public class WallRenderer implements Renderable, Disposable {

    private final Level level;
    private final DoorManager doorManager;
    private final SpriteBatch batch;
    private final Texture whitePixelTexture;
    // Perpendicular wall distance per screen column — used for sprite depth-clipping later.
    private final float[] zBuffer;

    // REGION WALL ATLAS (generated-wall-art order 2): every wall, door and column face of the floor comes from
    // the floor's RegionWallAtlas (owned and disposed by World with the floor). setRegionWallAtlas() resolves,
    // ONCE per level, each char to its face indices in these tables, so the hot per-column path reads plain
    // int arrays (no map lookup, no allocation). Workers read them through the happens-before edge of the
    // executor dispatch (the setter runs on the GL thread before any render()).
    private static final int ASCII_TABLE_SIZE = 128;
    private RegionWallAtlas regionWallAtlas;
    // The texture the base pass draws from: the region atlas, or the white pixel when no atlas could load (R7).
    private Texture wallSourceTexture;
    // Texels per face side at mip 0 (WALL_FACE_SIZE with an atlas, 1 for the white-pixel fallback).
    private int     wallFaceSize = 1;
    // Wall char -> [start, start + count) into wallFaceIndices: the BASE variants in order (count 1 otherwise).
    private final int[] wallFaceStart   = new int[ASCII_TABLE_SIZE];
    private final int[] wallFaceCount   = new int[ASCII_TABLE_SIZE];
    private final int[] wallFaceIndices = new int[ASCII_TABLE_SIZE];
    // Door char -> face (d R Y B; any other door char = the plain door).
    private final int[] doorFaceByChar  = new int[ASCII_TABLE_SIZE];
    // The floor's column face (the set's round or square column, picked per floor).
    private int columnFace;
    // Floor seed the per-face picks hash with (GameMath.wallVariantIndex): same seed, same walls (R6).
    private long wallVariantSeed;
    // R16: true when every wall/column/door texture of this level is fully opaque (base pass unblended).
    private boolean levelSurfacesOpaque;

    // Runtime render-resolution authority (util/RenderResolution) — see setRenderResolution(). Defaults
    // to HD so a WallRenderer never rendered through World.applyStoryAccessibilitySettings() still
    // reproduces the historic fixed 1280-column behaviour byte-identically (design rule R5).
    private RenderResolution renderResolution    = RenderResolution.HD;
    private int              projectionColumnCount = renderResolution.getProjectionColumnCount();
    /*
     * Formula: projection column width in world units
     * Derivation: the 3D view always projects onto the fixed Constants.WORLD_WIDTH (1280) world-unit
     *   screen (FitViewport never changes); raising the column count only narrows how much of that
     *   fixed world width a single column covers: columnWidth = WORLD_WIDTH / projectionColumnCount.
     *   HD: 1280 / 1280 = 1.0 (the historic fixed column width, byte-identical). FULL_HD: 1280 / 1920 ≈ 0.667.
     * Edge cases: projectionColumnCount is always a positive RenderResolution constant, never zero.
     */
    private float             columnWidth          = WORLD_WIDTH / (float) projectionColumnCount;

    private float playerWorldX        = 0f;
    private float playerWorldY        = 0f;
    private float directionX          = 1f;
    private float directionY          = 0f;
    // Camera-plane vectors cached in setPlayerState() to avoid Math.tan() inside render().
    private float cachedPlaneX        = 0f;
    private float cachedPlaneY        = CAMERA_PLANE_SCALE;
    // Current alert pulse intensity [0, 1]; 0 = normal lighting, 1 = full red wash.
    private float alertPulse          = 0f;
    // Monotonically increasing facility clock used to evaluate 'f' (flickering) tile brightness.
    private float lightingTimeSeconds = 0f;

    // Tile-space player position computed once per frame; workers read via happens-before from
    // ExecutorService.execute(), which is established after these fields are written.
    private float cachedPlayerTileX;
    private float cachedPlayerTileY;

    // --- Structured concurrency: parallel DDA compute phase ---
    private final WallColumnResult[] columnResults;

    // ATMOSPHERE GRADE (visual-identity-order-1, R1): per-floor colour grade. Null = no tint (old behaviour).
    // Set by World before the floor's first frame and never while workers run, so workers only read it.
    private AtmosphereGrade atmosphereGrade;
    private final int                workerCount;
    private final ExecutorService    workerPool;
    private final WallColumnWorker[] workers;
    private final Phaser             phaser;

    // -------------------------------------------------------------------------
    // Per-column compute result — populated by worker threads, consumed by the
    // GL-thread draw pass. Static so it carries no implicit outer-class reference.
    // -------------------------------------------------------------------------
    private static final class WallColumnResult {
        // Per-column grade scratch (red, green, blue): each column is owned by one worker at a time, and
        // AtmosphereGrade.evaluateInto is the thread-safe variant, so no shared mutable state is touched.
        // [0..2] = graded tint, [3..5] = muzzle light-add (visual-identity-order-2 R8).
        final float[] gradeScratch = new float[6];

        // Wall or cylinder surface drawn behind any door panel.
        boolean drawSurface;
        Texture surfaceTexture;
        int     surfaceTexColumn;
        int     surfaceTexSrcY;
        int     surfaceTexSrcHeight;
        float   surfaceDrawBottom;
        float   surfaceDrawTop;
        float   surfaceRed;
        float   surfaceGreen;
        float   surfaceBlue;
        // Muzzle-light add for the surface (R8): drawn as a separate ADDITIVE pass, never clamped into the
        // multiplier above. All zero when no pulse is active.
        float   surfaceLightRed;
        float   surfaceLightGreen;
        float   surfaceLightBlue;

        // Door panel composited on top of the surface (present when door is mid-animation).
        boolean drawDoorPanel;
        Texture doorPanelTexture;
        int     doorPanelTexColumn;
        int     doorPanelTexSrcY;
        int     doorPanelTexSrcHeight;
        float   doorPanelDrawBottom;
        float   doorPanelDrawTop;
        float   doorPanelRed;
        float   doorPanelGreen;
        float   doorPanelBlue;
        float   doorPanelLightRed;
        float   doorPanelLightGreen;
        float   doorPanelLightBlue;

        // Wall GLOW (generated-wall-art order 2): the emissive layer of the surface / door panel, drawn after the
        // base and muzzle passes from the glow atlas, NORMAL blend, ungraded; alpha = distance fade x blackout.
        boolean drawSurfaceGlow;
        int     surfaceGlowTexColumn;
        int     surfaceGlowTexSrcY;
        float   surfaceGlowAlpha;
        boolean drawDoorPanelGlow;
        int     doorPanelGlowTexColumn;
        int     doorPanelGlowTexSrcY;
        float   doorPanelGlowAlpha;
    }

    // -------------------------------------------------------------------------
    // Pre-allocated worker task — processes a contiguous range of screen columns.
    // Non-static so it can access outer-instance fields via happens-before.
    // -------------------------------------------------------------------------
    private final class WallColumnWorker implements Runnable {
        private int startColumn;
        private int endColumn;

        void configure(int startColumn, int endColumn) {
            this.startColumn = startColumn;
            this.endColumn   = endColumn;
        }

        @Override
        public void run() {
            try {
                for (int screenColumn = startColumn; screenColumn < endColumn; screenColumn++) {
                    computeWallColumn(screenColumn);
                }
            } finally {
                phaser.arrive();
            }
        }
    }

    public WallRenderer(Level level, DoorManager doorManager) {
        this.level       = level;
        this.doorManager = doorManager;
        // Each column can produce up to 2 draws (background surface + door panel overlay). Sized for the
        // largest render-resolution tier (RENDER_RESOLUTION_MAX_COLUMNS) so setRenderResolution() only
        // ever swaps the runtime column count/width — never reallocates these arrays/batch.
        this.batch   = new SpriteBatch(2 * RENDER_RESOLUTION_MAX_COLUMNS + 2);
        this.zBuffer = new float[RENDER_RESOLUTION_MAX_COLUMNS];

        Pixmap whitePixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        whitePixel.setColor(Color.WHITE);
        whitePixel.fill();
        whitePixelTexture = new Texture(whitePixel);
        whitePixel.dispose();

        wallSourceTexture = whitePixelTexture;
        levelSurfacesOpaque = true;

        // --- Parallel compute infrastructure ---
        columnResults = new WallColumnResult[RENDER_RESOLUTION_MAX_COLUMNS];
        for (int columnIndex = 0; columnIndex < RENDER_RESOLUTION_MAX_COLUMNS; columnIndex++) {
            columnResults[columnIndex] = new WallColumnResult();
        }

        workerCount = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
        phaser      = new Phaser(workerCount);

        if (workerCount > 1) {
            workerPool = Executors.newFixedThreadPool(workerCount - 1);
            workers    = new WallColumnWorker[workerCount - 1];
            for (int workerIndex = 0; workerIndex < workers.length; workerIndex++) {
                workers[workerIndex] = new WallColumnWorker();
            }
        } else {
            workerPool = null;
            workers    = new WallColumnWorker[0];
        }
    }

    /** Clamps a colour channel to the [0, 1] range. */
    private static float clampUnit(float value) {
        return value < 0f ? 0f : (value > 1f ? 1f : value);
    }

    public void setPlayerState(float worldX, float worldY,
                               float playerDirectionX, float playerDirectionY,
                               float playerFieldOfViewRadians) {
        this.playerWorldX = worldX;
        this.playerWorldY = worldY;
        this.directionX   = playerDirectionX;
        this.directionY   = playerDirectionY;
        float planeScale  = (float) Math.tan(playerFieldOfViewRadians / 2.0);
        this.cachedPlaneX = GameMath.cameraPlaneX(playerDirectionY, planeScale);
        this.cachedPlaneY = GameMath.cameraPlaneY(playerDirectionX, planeScale);
    }

    /**
     * The SINGLE runtime authority for the 3D view's internal projection resolution (checkpoint contract
     * C3). Every other scene renderer that holds this WallRenderer (Prop, Enemy, ShopMachine,
     * EnemyAttackEffectSystem) reads {@link #getProjectionColumnCount()} / {@link #getColumnWidth()} each
     * render() call instead of keeping its own copy, so nothing can drift out of sync with this setting.
     * Swaps two primitives only — zBuffer/columnResults/batch are already sized for
     * {@code RENDER_RESOLUTION_MAX_COLUMNS}, so this never allocates or reallocates.
     */
    public void setRenderResolution(RenderResolution renderResolution) {
        this.renderResolution      = renderResolution;
        this.projectionColumnCount = renderResolution.getProjectionColumnCount();
        this.columnWidth           = WORLD_WIDTH / (float) projectionColumnCount;
    }

    /** The current runtime ray-column count (HD 1280 / FULL_HD 1920) — see {@link #setRenderResolution}. */
    public int getProjectionColumnCount() {
        return projectionColumnCount;
    }

    /** The current runtime width, in world units, of one projection column — see {@link #setRenderResolution}. */
    public float getColumnWidth() {
        return columnWidth;
    }

    /** Sets the floor's colour grade (R1). Null disables the tint. Call before the floor's first frame. */
    public void setAtmosphereGrade(AtmosphereGrade grade) {
        this.atmosphereGrade = grade;
    }

    /**
     * Writes the R1 environment tint for a surface {@code perpWallDistanceTiles} tiles away into
     * {@code result.gradeScratch} (white when no grade is set). Thread-safe; allocation-free.
     */
    private void evaluateEnvironmentGrade(WallColumnResult result, float perpWallDistanceTiles) {
        AtmosphereGrade grade = atmosphereGrade;
        if (grade == null) {
            result.gradeScratch[0] = 1f;
            result.gradeScratch[1] = 1f;
            result.gradeScratch[2] = 1f;
            result.gradeScratch[3] = 0f;
            result.gradeScratch[4] = 0f;
            result.gradeScratch[5] = 0f;
            return;
        }
        AtmosphereGrade.evaluateInto(grade.getMood(), perpWallDistanceTiles,
                ArtDirection.GRADE_STRENGTH_ENVIRONMENT, result.gradeScratch, 0);
        // Muzzle light: read-only on the frame snapshot (AtmosphereGrade threading contract).
        grade.lightAddInto(perpWallDistanceTiles, result.gradeScratch, 3);
    }

    public void setAlertPulse(float pulse) {
        this.alertPulse = pulse;
    }

    public void setLightingTime(float timeSeconds) {
        this.lightingTimeSeconds = timeSeconds;
    }

    /**
     * Supplies this floor's region wall atlas and the floor seed, and PRE-RESOLVES every wall, door and column
     * char to its face indices (generated-wall-art order 2). Called once per level by World on the GL thread
     * BEFORE any render(). A null atlas (R7: not even set A could load) draws every wall with the white pixel.
     * Every wall symbol resolves to the floor's set through {@link WallSlotTable} and the set's R3 fallback.
     */
    public void setRegionWallAtlas(RegionWallAtlas atlas, long floorSeed) {
        this.regionWallAtlas = atlas;
        this.wallVariantSeed = floorSeed;
        Arrays.fill(wallFaceStart, 0);
        Arrays.fill(wallFaceCount, 1);
        Arrays.fill(wallFaceIndices, 0);
        Arrays.fill(doorFaceByChar, 0);
        columnFace = 0;
        if (atlas == null) {
            wallSourceTexture = whitePixelTexture;
            wallFaceSize      = 1;
            levelSurfacesOpaque = true;
            return;
        }
        wallSourceTexture = atlas.atlasTexture();
        wallFaceSize      = WALL_FACE_SIZE;
        RegionWallSet set = RegionWallSets.registry().resolve(atlas.setId());
        int nextIndex = 0;
        String symbols = WallSlotTable.symbols();
        for (int symbolIndex = 0; symbolIndex < symbols.length(); symbolIndex++) {
            char symbol = symbols.charAt(symbolIndex);
            java.util.List<String> ids = set.resolve(WallSlotTable.groupOf(symbol), WallSlotTable.slotOf(symbol));
            wallFaceStart[symbol] = nextIndex;
            wallFaceCount[symbol] = ids.size();
            for (String id : ids) wallFaceIndices[nextIndex++] = atlas.faceIndex(id);
        }
        // Any other char that reaches the wall path (never, by Level.isWall) keeps the hall BASE.
        char hallBase = WallSlotTable.symbolFor(WallGroup.HALL, WallSlot.BASE);
        for (int charIndex = 0; charIndex < ASCII_TABLE_SIZE; charIndex++) {
            if (!WallSlotTable.isWallSymbol((char) charIndex)) {
                wallFaceStart[charIndex] = wallFaceStart[hallBase];
                wallFaceCount[charIndex] = wallFaceCount[hallBase];
            }
        }
        int plainDoor = atlas.faceIndex(set.doorIdFor(TilesetConstants.WALL_SET_DOOR_SYMBOLS.charAt(0)));
        Arrays.fill(doorFaceByChar, plainDoor);
        for (int doorIndex = 0; doorIndex < TilesetConstants.WALL_SET_DOOR_SYMBOLS.length(); doorIndex++) {
            char doorSymbol = TilesetConstants.WALL_SET_DOOR_SYMBOLS.charAt(doorIndex);
            doorFaceByChar[doorSymbol] = atlas.faceIndex(set.doorIdFor(doorSymbol));
        }
        java.util.List<String> columns = set.columnIds();
        columnFace = atlas.faceIndex(columns.get(
                GameMath.wallVariantIndex(0, 0, WALL_COLUMN_PICK_SIDE, floorSeed, columns.size())));
        // R16: the region atlas is RGB by contract (guide section 12), so the base pass can run unblended.
        levelSurfacesOpaque = true;
    }

    /** R16: whether this level's base pass runs with blending disabled (every drawable surface opaque). */
    public boolean isLevelSurfacesOpaque() {
        return levelSurfacesOpaque;
    }

    /** Returns the perpendicular wall distance (in tile units) for a given screen column. */
    public float getZBufferAt(int screenColumn) {
        return zBuffer[MathUtils.clamp(screenColumn, 0, projectionColumnCount - 1)];
    }

    /**
     * Returns the perpendicular wall distance for the given screen column without bounds checking.
     * Callers must guarantee screenColumn is in [0, projectionColumnCount).
     */
    public float getZBufferUnchecked(int screenColumn) {
        return zBuffer[screenColumn];
    }

    @Override
    public void render(OrthographicCamera camera) {
        batch.setProjectionMatrix(camera.combined);

        // Tile-space player position written before workers are submitted; the
        // happens-before from ExecutorService.execute() makes them visible to workers.
        cachedPlayerTileX = playerWorldX / CELL_SIZE;
        cachedPlayerTileY = playerWorldY / CELL_SIZE;

        // Phase 1 — Parallel DDA compute.
        // Each column is fully independent: workers write to disjoint ranges of
        // columnResults[] and zBuffer[]; all other accesses (level, doorManager,
        // textures, player-state fields) are read-only during this phase.
        if (workerCount > 1) {
            int columnsPerWorker = projectionColumnCount / workerCount;
            for (int workerIndex = 0; workerIndex < workers.length; workerIndex++) {
                int startColumn = workerIndex * columnsPerWorker;
                int endColumn   = startColumn + columnsPerWorker;
                workers[workerIndex].configure(startColumn, endColumn);
                workerPool.execute(workers[workerIndex]);
            }
            // Main thread handles the tail chunk (absorbs any remainder columns).
            int mainThreadStart = workers.length * columnsPerWorker;
            for (int screenColumn = mainThreadStart; screenColumn < projectionColumnCount; screenColumn++) {
                computeWallColumn(screenColumn);
            }
            // Arrive and block until all background workers have finished their ranges.
            phaser.arriveAndAwaitAdvance();
        } else {
            for (int screenColumn = 0; screenColumn < projectionColumnCount; screenColumn++) {
                computeWallColumn(screenColumn);
            }
        }

        // Phase 2 — GL-thread draw pass.
        // Reads the fully-populated columnResults[] and issues SpriteBatch draw calls.
        // All OpenGL state changes happen on the GL thread only.
        batch.begin();
        // R16: opaque level -> blending is a no-op in the base pass; skip its fill-rate cost (pixel-identical).
        if (levelSurfacesOpaque) batch.disableBlending();
        int baseColumn = 0;
        while (baseColumn < projectionColumnCount) {
            int screenColumn = baseColumn;
            WallColumnResult result = columnResults[screenColumn];
            if (!result.drawSurface && !result.drawDoorPanel) {
                baseColumn++;
                continue;
            }

            // R18 W-MERGE: neighbouring columns whose surface slice is EXACTLY equal (same texture, texel
            // column, source rows, screen rows and colour) and that carry no door panel are one wider quad
            // sampling the same 1-texel source column - the pixels are those of the separate columns.
            int mergedColumnCount = 1;
            if (result.drawSurface && !result.drawDoorPanel) {
                while (screenColumn + mergedColumnCount < projectionColumnCount
                        && isSameSurfaceSlice(result, columnResults[screenColumn + mergedColumnCount])) {
                    mergedColumnCount++;
                }
            }

            if (result.drawSurface) {
                batch.setColor(result.surfaceRed, result.surfaceGreen, result.surfaceBlue, 1f);
                batch.draw(result.surfaceTexture,
                           screenColumn * columnWidth, result.surfaceDrawBottom,
                           mergedColumnCount * columnWidth, result.surfaceDrawTop - result.surfaceDrawBottom,
                           result.surfaceTexColumn, result.surfaceTexSrcY,
                           1, result.surfaceTexSrcHeight,
                           false, false);
            }

            if (result.drawDoorPanel) {
                batch.setColor(result.doorPanelRed, result.doorPanelGreen, result.doorPanelBlue, 1f);
                batch.draw(result.doorPanelTexture,
                           screenColumn * columnWidth, result.doorPanelDrawBottom,
                           columnWidth, result.doorPanelDrawTop - result.doorPanelDrawBottom,
                           result.doorPanelTexColumn, result.doorPanelTexSrcY,
                           1, result.doorPanelTexSrcHeight,
                           false, false);
            }
            baseColumn += mergedColumnCount;
        }

        // Phase 3 - MUZZLE LIGHT additive pass (visual-identity-order-2 R8). The base pass above is exactly
        // the pre-light grade (clamped multiplier). The light is ADDED in a second pass, albedo x lightAdd,
        // so it can exceed the multiplier clamp: only the final framebuffer colour clamps. The blend func
        // switches exactly twice (to additive, back to normal) and the whole pass is skipped at intensity
        // 0. A column carrying a door panel lights the panel only (the surface it overlaps is covered).
        batch.enableBlending();
        AtmosphereGrade grade = atmosphereGrade;
        if (grade != null && grade.isLightActive()) {
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            for (int screenColumn = 0; screenColumn < projectionColumnCount; screenColumn++) {
                WallColumnResult result = columnResults[screenColumn];
                if (result.drawDoorPanel) {
                    if (result.doorPanelLightRed + result.doorPanelLightGreen + result.doorPanelLightBlue > 0f) {
                        batch.setColor(result.doorPanelLightRed, result.doorPanelLightGreen,
                                       result.doorPanelLightBlue, 1f);
                        batch.draw(result.doorPanelTexture,
                                   screenColumn * columnWidth, result.doorPanelDrawBottom,
                                   columnWidth, result.doorPanelDrawTop - result.doorPanelDrawBottom,
                                   result.doorPanelTexColumn, result.doorPanelTexSrcY,
                                   1, result.doorPanelTexSrcHeight,
                                   false, false);
                    }
                } else if (result.drawSurface
                        && result.surfaceLightRed + result.surfaceLightGreen + result.surfaceLightBlue > 0f) {
                    batch.setColor(result.surfaceLightRed, result.surfaceLightGreen,
                                   result.surfaceLightBlue, 1f);
                    batch.draw(result.surfaceTexture,
                               screenColumn * columnWidth, result.surfaceDrawBottom,
                               columnWidth, result.surfaceDrawTop - result.surfaceDrawBottom,
                               result.surfaceTexColumn, result.surfaceTexSrcY,
                               1, result.surfaceTexSrcHeight,
                               false, false);
                }
            }
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }

        // Phase 4 - WALL GLOW (generated-wall-art order 2, R4/R5): the emissive layer from the glow atlas (one bind),
        // NORMAL blend, exempt from the grade, the red-alert dampen and the muzzle light; only the distance fade
        // and BLACKOUT's light multiplier dim it. A column with a door panel shows the panel's glow only.
        RegionWallAtlas atlas = regionWallAtlas;
        if (atlas != null) {
            Texture glowTexture = atlas.glowTexture();
            for (int screenColumn = 0; screenColumn < projectionColumnCount; screenColumn++) {
                WallColumnResult result = columnResults[screenColumn];
                if (result.drawDoorPanel) {
                    if (!result.drawDoorPanelGlow) continue;
                    batch.setColor(1f, 1f, 1f, result.doorPanelGlowAlpha);
                    batch.draw(glowTexture,
                               screenColumn * columnWidth, result.doorPanelDrawBottom,
                               columnWidth, result.doorPanelDrawTop - result.doorPanelDrawBottom,
                               result.doorPanelGlowTexColumn, result.doorPanelGlowTexSrcY,
                               1, result.doorPanelTexSrcHeight,
                               false, false);
                } else if (result.drawSurface && result.drawSurfaceGlow) {
                    batch.setColor(1f, 1f, 1f, result.surfaceGlowAlpha);
                    batch.draw(glowTexture,
                               screenColumn * columnWidth, result.surfaceDrawBottom,
                               columnWidth, result.surfaceDrawTop - result.surfaceDrawBottom,
                               result.surfaceGlowTexColumn, result.surfaceGlowTexSrcY,
                               1, result.surfaceTexSrcHeight,
                               false, false);
                }
            }
        }

        // Reset to white so no alert tint leaks into other renderers that share GL state.
        batch.setColor(Color.WHITE);
        batch.end();
    }

    /** Distance LOD (R6): mip 0/1/2 from the stripe height; always 0 for the white-pixel fallback. */
    private int wallMipLevel(float lineHeight) {
        if (regionWallAtlas == null) return 0;
        return GameMath.wallMipLevel(lineHeight, WALL_FACE_SIZE, WALL_MIP_BIAS, WALL_MAX_MIP);
    }

    /**
     * Fills the surface (or door panel) GLOW of one column when {@code face} has emissive pixels: the glow atlas
     * rect at the same mip and texel, or — at mip 0 on an animated face — the current animation frame. The frame
     * is a pure function of the world clock and a per-face phase hashed from (tile, side, floor seed); an
     * "animate": "some" face runs on a seeded one face in WALL_GLOW_SOME_ANIMATE_ONE_IN, the others hold frame 0
     * (R6). Alpha = distance fade x the floor's light multiplier (BLACKOUT dims glow by its factor only, R5).
     * Allocation-free; called from the worker threads.
     */
    private void assignGlow(WallColumnResult result, boolean doorPanel, int face, int mip, int texColumn, int texSrcY,
                            float distanceTiles, int tileColumn, int tileRow, int side) {
        RegionWallAtlas atlas = regionWallAtlas;
        if (atlas == null || !atlas.isGlowing(face)) return;
        int originX = atlas.sourceX(mip, face);
        int originY = atlas.sourceY(mip, face);
        int frameCount = atlas.glowFrameCount(face);
        int mode = atlas.animateMode(face);
        if (mip == 0 && frameCount > 1 && mode != RegionWallAtlas.ANIMATE_NONE) {
            boolean animated = mode == RegionWallAtlas.ANIMATE_ALL
                    || GameMath.wallVariantIndex(tileColumn, tileRow, side, wallVariantSeed ^ face,
                                                 WALL_GLOW_SOME_ANIMATE_ONE_IN) == 0;
            int frame = 0;
            if (animated) {
                int bucket = GameMath.wallVariantIndex(tileColumn, tileRow, side + WALL_GLOW_PHASE_SIDE_OFFSET,
                                                       wallVariantSeed, WALL_GLOW_PHASE_BUCKETS);
                float phaseSeconds = atlas.glowCycleSeconds(face) * bucket / WALL_GLOW_PHASE_BUCKETS;
                frame = GameMath.wallGlowFrameIndex(lightingTimeSeconds, phaseSeconds,
                                                    atlas.glowFrameSeconds(face), frameCount);
            }
            originX = atlas.glowFrameX(face, frame);
            originY = atlas.glowFrameY(face, frame);
        }
        float alpha = GameMath.wallGlowDistanceAlpha(distanceTiles, WALL_GLOW_FADE_START_TILES,
                WALL_GLOW_FADE_END_TILES, WALL_GLOW_MIN_ALPHA) * clampUnit(level.getLightMultiplier());
        if (doorPanel) {
            result.drawDoorPanelGlow      = true;
            result.doorPanelGlowTexColumn = originX + texColumn;
            result.doorPanelGlowTexSrcY   = originY + texSrcY;
            result.doorPanelGlowAlpha     = alpha;
        } else {
            result.drawSurfaceGlow      = true;
            result.surfaceGlowTexColumn = originX + texColumn;
            result.surfaceGlowTexSrcY   = originY + texSrcY;
            result.surfaceGlowAlpha     = alpha;
        }
    }

    /** Top-left atlas x of {@code face} at {@code mip}; 0 for the white-pixel fallback. */
    private int faceOriginX(int mip, int face) {
        RegionWallAtlas atlas = regionWallAtlas;
        return atlas != null ? atlas.sourceX(mip, face) : 0;
    }

    /** Top-left atlas y (row 0 = top) of {@code face} at {@code mip}; 0 for the white-pixel fallback. */
    private int faceOriginY(int mip, int face) {
        RegionWallAtlas atlas = regionWallAtlas;
        return atlas != null ? atlas.sourceY(mip, face) : 0;
    }

    /** R18: true when {@code next} would draw exactly the surface slice {@code first} draws, with no door panel. */
    private static boolean isSameSurfaceSlice(WallColumnResult first, WallColumnResult next) {
        return next.drawSurface && !next.drawDoorPanel
                && next.surfaceTexture == first.surfaceTexture
                && next.surfaceTexColumn == first.surfaceTexColumn
                && next.surfaceTexSrcY == first.surfaceTexSrcY
                && next.surfaceTexSrcHeight == first.surfaceTexSrcHeight
                && next.surfaceDrawBottom == first.surfaceDrawBottom
                && next.surfaceDrawTop == first.surfaceDrawTop
                && next.surfaceRed == first.surfaceRed
                && next.surfaceGreen == first.surfaceGreen
                && next.surfaceBlue == first.surfaceBlue;
    }

    /**
     * Computes all render data for one screen column and writes it into columnResults[screenColumn].
     * Also writes the z-buffer value for the column. Safe to call from any thread since each
     * column index is owned by exactly one concurrent caller.
     */
    private void computeWallColumn(int screenColumn) {
        WallColumnResult result = columnResults[screenColumn];
        result.drawSurface       = false;
        result.drawDoorPanel     = false;
        result.drawSurfaceGlow   = false;
        result.drawDoorPanelGlow = false;

        float playerTileX = cachedPlayerTileX;
        float playerTileY = cachedPlayerTileY;

        float cameraParameter = GameMath.cameraPlaneParameter(screenColumn, projectionColumnCount);
        float rayDirectionX   = GameMath.cameraPlaneRayDirectionX(directionX, cachedPlaneX, cameraParameter);
        float rayDirectionY   = GameMath.cameraPlaneRayDirectionY(directionY, cachedPlaneY, cameraParameter);

        float deltaDistanceX = GameMath.ddaDeltaDistanceX(rayDirectionX);
        float deltaDistanceY = GameMath.ddaDeltaDistanceY(rayDirectionY);

        int tileColumn = MathUtils.floor(playerTileX);
        int tileRow    = MathUtils.floor(playerTileY);

        float sideDistanceX = GameMath.ddaInitialSideDistanceX(playerTileX, tileColumn, rayDirectionX, deltaDistanceX);
        float sideDistanceY = GameMath.ddaInitialSideDistanceY(playerTileY, tileRow,    rayDirectionY, deltaDistanceY);

        int stepColumn = (rayDirectionX < 0f) ? -1 : 1;
        int stepRow    = (rayDirectionY < 0f) ? -1 : 1;

        boolean hitWall             = false;
        boolean crossedVerticalLine = false;
        float   perpWallDistance    = RAY_MAX_LENGTH_CELLS;
        char    hitWallCell         = 'x';
        // When the ray passes through a mid-animation door we save its data here and
        // continue casting so we can render the background surface behind it too.
        boolean hitPartialDoor             = false;
        float   doorPerpWallDistance       = 0f;
        int     doorHitTileColumn          = 0;
        int     doorHitTileRow             = 0;
        boolean doorHitCrossedVerticalLine = false;
        float   doorHitOpenFraction        = 0f;
        char    doorHitCell                = 'd';

        while (true) {
            if (sideDistanceX < sideDistanceY) {
                sideDistanceX      += deltaDistanceX;
                tileColumn         += stepColumn;
                crossedVerticalLine = true;
            } else {
                sideDistanceY      += deltaDistanceY;
                tileRow            += stepRow;
                crossedVerticalLine = false;
            }

            perpWallDistance = crossedVerticalLine
                    ? GameMath.perpWallDistance(sideDistanceX, deltaDistanceX)
                    : GameMath.perpWallDistance(sideDistanceY, deltaDistanceY);

            if (perpWallDistance >= RAY_MAX_LENGTH_CELLS) {
                perpWallDistance = RAY_MAX_LENGTH_CELLS;
                break;
            }

            char cell = level.getCell(tileColumn, tileRow);
            if (Level.isColumn(cell)) {
                float columnCenterTileX = tileColumn + 0.5f;
                float columnCenterTileY = tileRow    + 0.5f;
                float columnHitDistance = GameMath.rayCircleIntersection(
                        playerTileX, playerTileY,
                        rayDirectionX, rayDirectionY,
                        columnCenterTileX, columnCenterTileY,
                        COLUMN_RADIUS_TILES);
                if (columnHitDistance > 0f && columnHitDistance < RAY_MAX_LENGTH_CELLS) {
                    hitWall          = true;
                    hitWallCell      = cell;
                    perpWallDistance = columnHitDistance;
                    break;
                }
                continue; // Ray misses column — passes around it into the cell beyond.
            }
            if (Level.isWall(cell)) {
                hitWall     = true;
                hitWallCell = cell;
                break;
            }
            if (Level.isDoor(cell)) {
                float openFraction = doorManager.openFractionAtFast(tileColumn, tileRow);
                if (openFraction >= DOOR_OPEN_THROUGH_THRESHOLD) {
                    continue; // Fully open — ray passes through into the room beyond.
                }
                if (!hitPartialDoor) {
                    hitPartialDoor             = true;
                    doorPerpWallDistance       = perpWallDistance;
                    doorHitTileColumn          = tileColumn;
                    doorHitTileRow             = tileRow;
                    doorHitCrossedVerticalLine = crossedVerticalLine;
                    doorHitOpenFraction        = openFraction;
                    doorHitCell                = cell;
                }
                if (openFraction <= 0f) {
                    break; // Closed door — fully opaque, terminate ray here.
                }
                continue; // Partially open — keep casting to find the surface behind.
            }
        }

        // Use the door's (closer) distance for Z-buffer so sprites don't bleed through the panel.
        // Each column writes to its own index — no race condition with concurrent workers.
        zBuffer[screenColumn] = hitPartialDoor ? doorPerpWallDistance : perpWallDistance;

        if (!hitWall && !hitPartialDoor) return;

        // --- Step 1: Compute render data for the background surface (wall or column) ---
        if (hitWall) {
            float lineHeight      = GameMath.wallStripeHeight(WALL_PROJECTION_SCREEN_HEIGHT, perpWallDistance);
            float unclampedBottom = GameMath.wallStripeDrawBottom(WALL_PROJECTION_SCREEN_HEIGHT, lineHeight);
            float unclampedTop    = GameMath.wallStripeDrawTop(WALL_PROJECTION_SCREEN_HEIGHT, lineHeight);
            float drawBottom      = Math.max(0f, unclampedBottom);
            float drawTop         = Math.min((float) WALL_PROJECTION_SCREEN_HEIGHT, unclampedTop);

            if (Level.isColumn(hitWallCell)) {
                // tileColumn/tileRow = the cylinder's tile at DDA exit.
                float columnCenterTileX = tileColumn + 0.5f;
                float columnCenterTileY = tileRow    + 0.5f;
                float hitTileX          = playerTileX + perpWallDistance * rayDirectionX;
                float hitTileY          = playerTileY + perpWallDistance * rayDirectionY;

                float columnU         = GameMath.columnTextureU(hitTileX, hitTileY,
                                                                 columnCenterTileX, columnCenterTileY);
                int   face            = columnFace;
                int   mip             = wallMipLevel(lineHeight);
                int   faceSize        = wallFaceSize >> mip;
                int   columnTexColumn = GameMath.textureColumn(columnU, faceSize);

                // Lambert shading: dot of the outward surface normal with the world light direction.
                float normalX          = (hitTileX - columnCenterTileX) / COLUMN_RADIUS_TILES;
                float normalY          = (hitTileY - columnCenterTileY) / COLUMN_RADIUS_TILES;
                float lambertian       = Math.max(0f,
                                                   normalX * COLUMN_LIGHT_DIRECTION_X
                                                 + normalY * COLUMN_LIGHT_DIRECTION_Y);
                float cylindricalShade = COLUMN_SHADE_MIN + (1f - COLUMN_SHADE_MIN) * lambertian;

                float columnTileBrightness = level.getTileBrightness(tileColumn, tileRow, lightingTimeSeconds);
                float shade = Math.max(
                        Math.min(
                            GameMath.wallShade(perpWallDistance, WALL_SHADING_FALLOFF)
                                * cylindricalShade * columnTileBrightness,
                            MAX_LIGHTING_SHADE),
                        WALL_SHADE_MIN_BRIGHTNESS);

                int texSrcY      = GameMath.wallTextureClipSrcY(
                                       unclampedTop, WALL_PROJECTION_SCREEN_HEIGHT,
                                       lineHeight, faceSize);
                int texSrcHeight = GameMath.wallTextureClipSrcHeight(
                                       drawTop, drawBottom,
                                       lineHeight, faceSize);
                texSrcHeight = Math.min(texSrcHeight, faceSize - texSrcY);
                texSrcHeight = Math.max(1, texSrcHeight);

                result.drawSurface         = true;
                result.surfaceTexture      = wallSourceTexture;
                result.surfaceTexColumn    = faceOriginX(mip, face) + columnTexColumn;
                result.surfaceTexSrcY      = faceOriginY(mip, face) + texSrcY;
                assignGlow(result, false, face, mip, columnTexColumn, texSrcY, perpWallDistance,
                           tileColumn, tileRow, 0);
                result.surfaceTexSrcHeight = texSrcHeight;
                result.surfaceDrawBottom   = drawBottom;
                result.surfaceDrawTop      = drawTop;
                evaluateEnvironmentGrade(result, perpWallDistance);
                float[] columnGrade = result.gradeScratch;
                float gradedRed   = clampUnit(shade * columnGrade[0]);
                float gradedGreen = clampUnit(shade * columnGrade[1]);
                float gradedBlue  = clampUnit(shade * columnGrade[2]);
                result.surfaceRed   = Math.min(1f, gradedRed * (1f + alertPulse * ALERT_WALL_RED_BOOST));
                result.surfaceGreen = gradedGreen * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
                result.surfaceBlue  = gradedBlue * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
                result.surfaceLightRed   = columnGrade[3];
                result.surfaceLightGreen = columnGrade[4];
                result.surfaceLightBlue  = columnGrade[5];
            } else {
                float wallHitFraction         = GameMath.wallHitPosition(playerTileX, playerTileY,
                                                                         rayDirectionX, rayDirectionY,
                                                                         perpWallDistance, crossedVerticalLine);
                float wallHitFractionMirrored = GameMath.wallHitPositionMirrored(wallHitFraction,
                                                                                  rayDirectionX, rayDirectionY,
                                                                                  crossedVerticalLine);

                // Floor tile adjacent to the hit wall face — its brightness spills onto the wall.
                int   adjacentFloorColumn = crossedVerticalLine ? tileColumn - stepColumn : tileColumn;
                int   adjacentFloorRow    = crossedVerticalLine ? tileRow                 : tileRow - stepRow;
                float wallTileBrightness  = level.getTileBrightness(adjacentFloorColumn, adjacentFloorRow, lightingTimeSeconds);

                // O(1) array reads into this level's pre-resolved face tables (setRegionWallAtlas).
                // BASE variant per FACE (R6): a pure hash of (tile, side, floor seed), so turning never pops.
                int     hitCharIndex          = hitWallCell & 0x7F;
                int     side                  = crossedVerticalLine ? (stepColumn > 0 ? 0 : 1) : (stepRow > 0 ? 2 : 3);
                int     variantCount          = wallFaceCount[hitCharIndex];
                int     variant               = variantCount > 1
                        ? GameMath.wallVariantIndex(tileColumn, tileRow, side, wallVariantSeed, variantCount) : 0;
                int     face                  = wallFaceIndices[wallFaceStart[hitCharIndex] + variant];
                int     mip                   = wallMipLevel(lineHeight);
                int     selectedTextureWidth  = wallFaceSize >> mip;
                int     selectedTextureHeight = wallFaceSize >> mip;
                int     wallTextureColumn     = GameMath.textureColumn(wallHitFractionMirrored, selectedTextureWidth);

                float shade = GameMath.wallShade(perpWallDistance, WALL_SHADING_FALLOFF);
                if (!crossedVerticalLine) shade *= HORIZONTAL_FACE_SHADE_MULTIPLIER;
                shade = Math.max(Math.min(shade * wallTileBrightness, MAX_LIGHTING_SHADE), WALL_SHADE_MIN_BRIGHTNESS);

                int texSrcY      = GameMath.wallTextureClipSrcY(
                                       unclampedTop, WALL_PROJECTION_SCREEN_HEIGHT,
                                       lineHeight, selectedTextureHeight);
                int texSrcHeight = GameMath.wallTextureClipSrcHeight(
                                       drawTop, drawBottom,
                                       lineHeight, selectedTextureHeight);
                texSrcHeight = Math.min(texSrcHeight, selectedTextureHeight - texSrcY);
                texSrcHeight = Math.max(1, texSrcHeight);

                result.drawSurface         = true;
                result.surfaceTexture      = wallSourceTexture;
                result.surfaceTexColumn    = faceOriginX(mip, face) + wallTextureColumn;
                result.surfaceTexSrcY      = faceOriginY(mip, face) + texSrcY;
                assignGlow(result, false, face, mip, wallTextureColumn, texSrcY, perpWallDistance,
                           tileColumn, tileRow, side);
                result.surfaceTexSrcHeight = texSrcHeight;
                result.surfaceDrawBottom   = drawBottom;
                result.surfaceDrawTop      = drawTop;
                evaluateEnvironmentGrade(result, perpWallDistance);
                float[] wallGrade = result.gradeScratch;
                float gradedRed   = clampUnit(shade * wallGrade[0]);
                float gradedGreen = clampUnit(shade * wallGrade[1]);
                float gradedBlue  = clampUnit(shade * wallGrade[2]);
                result.surfaceRed   = Math.min(1f, gradedRed * (1f + alertPulse * ALERT_WALL_RED_BOOST));
                result.surfaceGreen = gradedGreen * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
                result.surfaceBlue  = gradedBlue * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
                result.surfaceLightRed   = wallGrade[3];
                result.surfaceLightGreen = wallGrade[4];
                result.surfaceLightBlue  = wallGrade[5];
            }
        }

        // --- Step 2: Compute render data for the door panel overlay ---
        // The panel is anchored at the ceiling; its bottom edge rises as the door opens,
        // so the background rendered in step 1 is progressively revealed below it.
        if (hitPartialDoor) {
            float doorLineHeight      = GameMath.wallStripeHeight(WALL_PROJECTION_SCREEN_HEIGHT, doorPerpWallDistance);
            float doorUnclampedBottom = GameMath.wallStripeDrawBottom(WALL_PROJECTION_SCREEN_HEIGHT, doorLineHeight);
            float doorUnclampedTop    = GameMath.wallStripeDrawTop(WALL_PROJECTION_SCREEN_HEIGHT, doorLineHeight);
            float doorDrawTop         = Math.min((float) WALL_PROJECTION_SCREEN_HEIGHT, doorUnclampedTop);

            float doorWallHitFraction         = GameMath.wallHitPosition(playerTileX, playerTileY,
                                                                         rayDirectionX, rayDirectionY,
                                                                         doorPerpWallDistance,
                                                                         doorHitCrossedVerticalLine);
            float doorWallHitFractionMirrored = GameMath.wallHitPositionMirrored(doorWallHitFraction,
                                                                                  rayDirectionX, rayDirectionY,
                                                                                  doorHitCrossedVerticalLine);

            int   doorAdjacentFloorColumn = doorHitCrossedVerticalLine
                    ? doorHitTileColumn - stepColumn : doorHitTileColumn;
            int   doorAdjacentFloorRow    = doorHitCrossedVerticalLine
                    ? doorHitTileRow : doorHitTileRow - stepRow;
            float doorTileBrightness = level.getTileBrightness(
                    doorAdjacentFloorColumn, doorAdjacentFloorRow, lightingTimeSeconds);

            float panelHeight = GameMath.doorPanelHeight(doorLineHeight, doorHitOpenFraction);
            if (panelHeight >= 1f) {
                float unclampedPanelBottom = GameMath.doorPanelBottom(
                        doorUnclampedBottom, doorUnclampedTop, doorHitOpenFraction);
                float clampedPanelBottom   = Math.max(0f, unclampedPanelBottom);
                float clampedPanelTop      = doorDrawTop;

                if (clampedPanelTop > clampedPanelBottom) {
                    // O(1) array lookup for door texture variant (plain/red/yellow/blue).
                    // Each variant may have its own resolution (the keycard doors are generated
                    // independently of the file-loaded plain door), so clip against the SELECTED
                    // texture's own dimensions rather than a shared plain-door size.
                    int     doorFace              = doorFaceByChar[doorHitCell & 0x7F];
                    int     doorMip               = wallMipLevel(doorLineHeight);
                    int     selectedDoorTexWidth  = wallFaceSize >> doorMip;
                    int     selectedDoorTexHeight = wallFaceSize >> doorMip;

                    int doorTexSrcY      = GameMath.wallTextureClipSrcY(
                                               doorUnclampedTop, WALL_PROJECTION_SCREEN_HEIGHT,
                                               panelHeight, selectedDoorTexHeight);
                    int doorTexSrcHeight = GameMath.wallTextureClipSrcHeight(
                                               clampedPanelTop, clampedPanelBottom,
                                               panelHeight, selectedDoorTexHeight);
                    doorTexSrcHeight = Math.min(doorTexSrcHeight, selectedDoorTexHeight - doorTexSrcY);
                    doorTexSrcHeight = Math.max(1, doorTexSrcHeight);

                    int     doorTexColumn       = GameMath.textureColumn(doorWallHitFractionMirrored, selectedDoorTexWidth);
                    float shade = GameMath.wallShade(doorPerpWallDistance, WALL_SHADING_FALLOFF);
                    if (!doorHitCrossedVerticalLine) shade *= HORIZONTAL_FACE_SHADE_MULTIPLIER;
                    shade = Math.max(Math.min(shade * doorTileBrightness, MAX_LIGHTING_SHADE), WALL_SHADE_MIN_BRIGHTNESS);

                    result.drawDoorPanel          = true;
                    result.doorPanelTexture       = wallSourceTexture;
                    result.doorPanelTexColumn     = faceOriginX(doorMip, doorFace) + doorTexColumn;
                    result.doorPanelTexSrcY       = faceOriginY(doorMip, doorFace) + doorTexSrcY;
                    int doorSide = doorHitCrossedVerticalLine ? (stepColumn > 0 ? 0 : 1) : (stepRow > 0 ? 2 : 3);
                    assignGlow(result, true, doorFace, doorMip, doorTexColumn, doorTexSrcY, doorPerpWallDistance,
                               doorHitTileColumn, doorHitTileRow, doorSide);
                    result.doorPanelTexSrcHeight  = doorTexSrcHeight;
                    result.doorPanelDrawBottom    = clampedPanelBottom;
                    result.doorPanelDrawTop       = clampedPanelTop;
                    evaluateEnvironmentGrade(result, doorPerpWallDistance);
                    float[] doorGrade = result.gradeScratch;
                    float doorGradedRed   = clampUnit(shade * doorGrade[0]);
                    float doorGradedGreen = clampUnit(shade * doorGrade[1]);
                    float doorGradedBlue  = clampUnit(shade * doorGrade[2]);
                    result.doorPanelRed   = Math.min(1f, doorGradedRed * (1f + alertPulse * ALERT_WALL_RED_BOOST));
                    result.doorPanelGreen = doorGradedGreen * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
                    result.doorPanelBlue  = doorGradedBlue * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
                    result.doorPanelLightRed   = doorGrade[3];
                    result.doorPanelLightGreen = doorGrade[4];
                    result.doorPanelLightBlue  = doorGrade[5];
                }
            }
        }
    }

    @Override
    public void dispose() {
        batch.dispose();
        // Wall, door and column faces live in the floor's RegionWallAtlas, owned and disposed by World with the
        // floor. WallRenderer owns only the 1x1 white pixel it builds itself.
        whitePixelTexture.dispose();
        if (workerPool != null) {
            workerPool.shutdown();
        }
    }
}
