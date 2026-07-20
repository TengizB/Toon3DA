package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.door.DoorManager;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.render.tilesetgfx.EnvironmentTextureSet;
import ge.tbegvadze.toon3d.render.tilesetgfx.TextureGeneratorRegistry;
import ge.tbegvadze.toon3d.tileset.LevelPalette;
import ge.tbegvadze.toon3d.tileset.TilesetRegistries;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.TilesetConstants;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;

import static ge.tbegvadze.toon3d.util.Constants.*;
import static ge.tbegvadze.toon3d.util.RenderConstants.*;

/**
 * Renders the Doom-style 3D wall projection for one frame.
 *
 * Pipeline per frame (single SpriteBatch, two GPU draw calls):
 *   1. Ceiling and floor — two rects using a 1×1 white pixel texture tinted to the correct colour.
 *   2. Wall columns — one draw per screen column, sampling the wall texture at the computed
 *      texture column (wallHitPosition → texColumn). The texture switch from the white pixel to
 *      the wall texture triggers the only automatic batch flush; all 1280 wall draws then drain
 *      into one GPU call at batch.end().
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
    private final Texture doorTexture;
    private final Texture doorTextureRed;
    private final Texture doorTextureYellow;
    private final Texture doorTextureBlue;
    private final Texture whitePixelTexture;
    // Perpendicular wall distance per screen column — used for sprite depth-clipping later.
    private final float[] zBuffer;

    // TILESET SYSTEM (order-7): the wall + column art is no longer owned by this renderer. Each level's
    // EnvironmentTextureSet (owned/disposed by World, realized from the level's LevelPalette) supplies the
    // textures; WallRenderer resolves symbol -> sprite id (via the palette) -> Texture (via the set) ONCE
    // per level in setEnvironmentTextureSet() and caches the result in these char-indexed arrays. The hot
    // per-column draw path still does a single O(1) array read (no per-frame map lookup, no allocation),
    // preserving the parallel-worker performance profile — the workers read these immutable-for-the-level
    // arrays via the happens-before edge from the per-level setter running on the GL thread before
    // render(). With the legacy palette the arrays hold exactly the same textures the old static table did,
    // so the look is unchanged; a varied palette (order-8) fills them with different accent art.
    // See docs/environment-tileset-system.txt (section 7 / order-7).
    private final Texture[] levelWallTextures = new Texture[128];
    private final int[]     levelWallWidths   = new int[128];
    private final int[]     levelWallHeights  = new int[128];
    // Column ('P') resolves the same way; the column path is a separate branch, so its resolved texture is
    // cached in dedicated fields rather than the char array.
    private static final char COLUMN_SYMBOL = TilesetConstants.FLEXIBLE_COLUMN_SYMBOLS.charAt(0);
    private Texture levelColumnTexture;
    private int     levelColumnTextureWidth;
    private int     levelColumnTextureHeight;

    // Door textures ARE still owned here: doors are FIXED gameplay symbols, not environment sprites, so
    // they are not part of any level's EnvironmentTextureSet. Char-indexed table (ASCII index 0–127);
    // Arrays.fill initialises every slot to the plain-door default, named chars override it.
    private final Texture[] doorTextureTable;

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

    // TILESET SYSTEM (order-7): the per-level texture supply realized from this level's palette. Set
    // each level by World via setEnvironmentTextureSet(), which pre-resolves the wall + column textures
    // from it into the char-indexed arrays above. The set is the OWNER of those wall/column textures;
    // WallRenderer never disposes them (World disposes the set).
    private EnvironmentTextureSet environmentTextureSet;

    // Tile-space player position computed once per frame; workers read via happens-before from
    // ExecutorService.execute(), which is established after these fields are written.
    private float cachedPlayerTileX;
    private float cachedPlayerTileY;

    // --- Structured concurrency: parallel DDA compute phase ---
    private final WallColumnResult[] columnResults;
    private final int                workerCount;
    private final ExecutorService    workerPool;
    private final WallColumnWorker[] workers;
    private final Phaser             phaser;

    // -------------------------------------------------------------------------
    // Per-column compute result — populated by worker threads, consumed by the
    // GL-thread draw pass. Static so it carries no implicit outer-class reference.
    // -------------------------------------------------------------------------
    private static final class WallColumnResult {
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
        // Each column can produce up to 2 draws (background surface + door panel overlay).
        this.batch   = new SpriteBatch(2 * WALL_PROJECTION_SCREEN_WIDTH + 2);
        this.zBuffer = new float[WALL_PROJECTION_SCREEN_WIDTH];

        // Wall + column textures are no longer built or owned here (order-7). They are supplied per level
        // by the EnvironmentTextureSet and cached into levelWallTextures/levelColumn* by
        // setEnvironmentTextureSet(); the per-char generate*WallTexture()/generateColumnTexture() routines
        // now live behind the TextureGeneratorRegistry (see registerTextureGenerators). Doors are FIXED
        // gameplay symbols, not environment sprites, so their textures are still built and owned here.
        doorTexture      = loadOrGenerateDoorTexture(LAB_DOOR_CLOSED_PATH, 0f, 0f, 0f);

        doorTextureRed    = loadOrGenerateDoorTexture(LAB_DOOR_RED_PATH,    0.90f, 0.13f, 0.13f);
        doorTextureYellow = loadOrGenerateDoorTexture(LAB_DOOR_YELLOW_PATH, 0.97f, 0.80f, 0.12f);
        doorTextureBlue   = loadOrGenerateDoorTexture(LAB_DOOR_BLUE_PATH,   0.16f, 0.48f, 0.97f);

        Pixmap whitePixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        whitePixel.setColor(Color.WHITE);
        whitePixel.fill();
        whitePixelTexture = new Texture(whitePixel);
        whitePixel.dispose();

        doorTextureTable = new Texture[128];
        Arrays.fill(doorTextureTable, doorTexture);
        doorTextureTable['R'] = doorTextureRed;
        doorTextureTable['Y'] = doorTextureYellow;
        doorTextureTable['B'] = doorTextureBlue;

        // --- Parallel compute infrastructure ---
        columnResults = new WallColumnResult[WALL_PROJECTION_SCREEN_WIDTH];
        for (int columnIndex = 0; columnIndex < WALL_PROJECTION_SCREEN_WIDTH; columnIndex++) {
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

    private static Texture loadWallTexture(String path) {
        Texture texture = new Texture(Gdx.files.internal(path));
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return texture;
    }

    /**
     * Tries to load a door texture from disk; generates a procedural keycard door if absent.
     * keycardRed/Green/Blue is the security-tier colour woven through the procedural door
     * (hazard chevrons, reader panel, seam light strips). Pass (0, 0, 0) for a plain door.
     */
    private static Texture loadOrGenerateDoorTexture(String path,
                                                     float keycardRed,
                                                     float keycardGreen,
                                                     float keycardBlue) {
        if (Gdx.files.internal(path).exists()) {
            return loadWallTexture(path);
        }
        return generateProceduralDoorTexture(keycardRed, keycardGreen, keycardBlue);
    }

    /** Clamps a colour channel to the [0, 1] range. */
    private static float clampUnit(float value) {
        return value < 0f ? 0f : (value > 1f ? 1f : value);
    }

    /** Draws a small recessed bolt stud (socket shadow, head, specular dot) at the given centre. */
    private static void drawDoorBoltStud(Pixmap pixmap, int centerX, int centerY) {
        pixmap.setColor(0.05f, 0.05f, 0.06f, 1f);
        pixmap.fillCircle(centerX, centerY, 6);          // socket shadow
        pixmap.setColor(0.34f, 0.35f, 0.40f, 1f);
        pixmap.fillCircle(centerX, centerY, 4);          // bolt head
        pixmap.setColor(0.55f, 0.57f, 0.62f, 1f);
        pixmap.fillCircle(centerX - 1, centerY - 1, 2);  // specular highlight
    }

    /** Fills a recessed leaf panel with a beveled border (top/left highlight, bottom/right shadow). */
    private static void drawRecessedDoorPanel(Pixmap pixmap, int x, int y, int width, int height) {
        if (width <= 4 || height <= 4) return;
        pixmap.setColor(0.165f, 0.175f, 0.205f, 1f);
        pixmap.fillRectangle(x, y, width, height);
        pixmap.setColor(0.30f, 0.31f, 0.35f, 1f);             // top + left highlight
        pixmap.fillRectangle(x, y, width, 2);
        pixmap.fillRectangle(x, y, 2, height);
        pixmap.setColor(0.075f, 0.08f, 0.10f, 1f);            // bottom + right shadow
        pixmap.fillRectangle(x, y + height - 2, width, 2);
        pixmap.fillRectangle(x + width - 2, y, 2, height);
    }

    /**
     * Generates a high-resolution (512×512) sci-fi keycard blast door.
     *
     * Twin-leaf armoured panel split by a central vertical seam inside a beveled, bolted frame.
     * The keycard tier colour ({@code keycardRed/Green/Blue}) drives every accent: diagonal
     * hazard chevrons top and bottom, twin glowing light strips flanking the seam, and a central
     * keycard reader (status LED, card slot, keypad). Passing (0, 0, 0) yields a neutral steel
     * door with grey accents — the plain-door fallback when no door asset file is present.
     *
     * Layers are drawn back-to-front so the frame, seam and reader composite over the base.
     */
    private static Texture generateProceduralDoorTexture(float keycardRed,
                                                         float keycardGreen,
                                                         float keycardBlue) {
        final int size = 512;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        boolean keycardThemed = (keycardRed + keycardGreen + keycardBlue) > 0.01f;
        // Accent palette derived from the tier colour (neutral steel-grey when un-themed).
        float accentRed   = keycardThemed ? clampUnit(keycardRed)   : 0.46f;
        float accentGreen = keycardThemed ? clampUnit(keycardGreen) : 0.47f;
        float accentBlue  = keycardThemed ? clampUnit(keycardBlue)  : 0.52f;
        float accentDarkRed   = accentRed   * 0.45f;
        float accentDarkGreen = accentGreen * 0.45f;
        float accentDarkBlue  = accentBlue  * 0.45f;
        float accentGlowRed   = clampUnit(accentRed   * 0.6f + 0.4f);
        float accentGlowGreen = clampUnit(accentGreen * 0.6f + 0.4f);
        float accentGlowBlue  = clampUnit(accentBlue  * 0.6f + 0.4f);

        final int frame      = 38;        // armoured border thickness
        final int seamCenter = size / 2;  // central vertical seam
        final int seamHalf   = 7;

        // ── 1. Brushed-steel base: vertical streaks + fine grain ──
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                float streak = ((column * 13 + (row / 6) * 7) % 17 - 8) / 220f;
                float grain  = ((row * 7 + column * 11) % 13 - 6) / 200f;
                pixmap.setColor(clampUnit(0.205f + streak + grain),
                                clampUnit(0.215f + streak + grain),
                                clampUnit(0.245f + streak + grain), 1f);
                pixmap.drawPixel(column, row);
            }
        }

        // ── 2. Recessed leaf panels: three stacked panels per leaf ──
        int leftLeafX  = frame + 6;
        int leftLeafW  = (seamCenter - seamHalf - 6) - leftLeafX;
        int rightLeafX = seamCenter + seamHalf + 6;
        int rightLeafW = (size - frame - 6) - rightLeafX;
        int panelRegionTop    = frame + 8;
        int panelRegionHeight = size - 2 * frame - 16;
        int panelGap          = 10;
        int panelHeight       = (panelRegionHeight - 2 * panelGap) / 3;
        for (int panelIndex = 0; panelIndex < 3; panelIndex++) {
            int panelY = panelRegionTop + panelIndex * (panelHeight + panelGap);
            drawRecessedDoorPanel(pixmap, leftLeafX,  panelY, leftLeafW,  panelHeight);
            drawRecessedDoorPanel(pixmap, rightLeafX, panelY, rightLeafW, panelHeight);
        }

        // ── 3. Hazard chevron bands (tier colour vs near-black diagonals) top and bottom ──
        int chevronHeight = 46;
        int[] chevronBandY = { frame + 8, size - frame - 8 - chevronHeight };
        int chevronInsetX  = frame + 4;
        int chevronSpanW   = size - 2 * frame - 8;
        for (int band = 0; band < 2; band++) {
            int bandY0 = chevronBandY[band];
            for (int row = bandY0; row < bandY0 + chevronHeight; row++) {
                for (int column = chevronInsetX; column < chevronInsetX + chevronSpanW; column++) {
                    int diagonal = ((column + row) % 32 + 32) % 32;
                    if (diagonal < 16) {
                        pixmap.setColor(accentRed, accentGreen, accentBlue, 1f);
                    } else {
                        pixmap.setColor(0.07f, 0.07f, 0.08f, 1f);
                    }
                    pixmap.drawPixel(column, row);
                }
            }
            pixmap.setColor(0.05f, 0.05f, 0.06f, 1f);  // dark rails framing the band
            pixmap.fillRectangle(chevronInsetX, bandY0, chevronSpanW, 3);
            pixmap.fillRectangle(chevronInsetX, bandY0 + chevronHeight - 3, chevronSpanW, 3);
        }

        // ── 4. Central seam groove + twin glowing tier-colour light strips ──
        int seamTop    = frame;
        int seamHeight = size - 2 * frame;
        pixmap.setColor(0.04f, 0.04f, 0.05f, 1f);
        pixmap.fillRectangle(seamCenter - seamHalf, seamTop, 2 * seamHalf, seamHeight);
        pixmap.setColor(0.28f, 0.29f, 0.34f, 1f);     // bright meeting line
        pixmap.fillRectangle(seamCenter - 1, seamTop, 2, seamHeight);
        pixmap.setColor(accentDarkRed, accentDarkGreen, accentDarkBlue, 1f);
        pixmap.fillRectangle(seamCenter - seamHalf - 5, seamTop, 3, seamHeight);
        pixmap.fillRectangle(seamCenter + seamHalf + 2, seamTop, 3, seamHeight);
        pixmap.setColor(accentGlowRed, accentGlowGreen, accentGlowBlue, 1f);   // bright cores
        pixmap.fillRectangle(seamCenter - seamHalf - 4, seamTop, 1, seamHeight);
        pixmap.fillRectangle(seamCenter + seamHalf + 3, seamTop, 1, seamHeight);

        // ── 5. Outer armoured frame with bevel and corner/edge bolts ──
        pixmap.setColor(0.135f, 0.145f, 0.175f, 1f);
        pixmap.fillRectangle(0, 0, size, frame);
        pixmap.fillRectangle(0, size - frame, size, frame);
        pixmap.fillRectangle(0, 0, frame, size);
        pixmap.fillRectangle(size - frame, 0, frame, size);
        pixmap.setColor(0.30f, 0.31f, 0.36f, 1f);     // outer highlight (top, left)
        pixmap.fillRectangle(0, 0, size, 3);
        pixmap.fillRectangle(0, 0, 3, size);
        pixmap.setColor(0.06f, 0.06f, 0.07f, 1f);     // outer shadow (bottom, right)
        pixmap.fillRectangle(0, size - 3, size, 3);
        pixmap.fillRectangle(size - 3, 0, 3, size);
        pixmap.setColor(0.07f, 0.07f, 0.085f, 1f);    // inner lip separating frame from leaves
        pixmap.fillRectangle(frame - 3, frame - 3, size - 2 * (frame - 3), 3);
        pixmap.fillRectangle(frame - 3, size - frame, size - 2 * (frame - 3), 3);
        pixmap.fillRectangle(frame - 3, frame - 3, 3, size - 2 * (frame - 3));
        pixmap.fillRectangle(size - frame, frame - 3, 3, size - 2 * (frame - 3));
        for (int position = frame / 2; position < size; position += 72) {
            drawDoorBoltStud(pixmap, position, frame / 2);
            drawDoorBoltStud(pixmap, position, size - frame / 2);
            drawDoorBoltStud(pixmap, frame / 2, position);
            drawDoorBoltStud(pixmap, size - frame / 2, position);
        }

        // ── 6. Central keycard reader: housing, status LED, card slot, keypad ──
        int readerWidth  = 96;
        int readerHeight = 132;
        int readerX = seamCenter - readerWidth / 2;
        int readerY = size / 2 - readerHeight / 2;
        pixmap.setColor(0.10f, 0.105f, 0.13f, 1f);
        pixmap.fillRectangle(readerX, readerY, readerWidth, readerHeight);
        pixmap.setColor(0.30f, 0.31f, 0.36f, 1f);     // bevel highlight
        pixmap.fillRectangle(readerX, readerY, readerWidth, 2);
        pixmap.fillRectangle(readerX, readerY, 2, readerHeight);
        pixmap.setColor(0.05f, 0.05f, 0.06f, 1f);     // bevel shadow
        pixmap.fillRectangle(readerX, readerY + readerHeight - 2, readerWidth, 2);
        pixmap.fillRectangle(readerX + readerWidth - 2, readerY, 2, readerHeight);
        drawDoorBoltStud(pixmap, readerX + 8, readerY + 8);
        drawDoorBoltStud(pixmap, readerX + readerWidth - 8, readerY + 8);
        drawDoorBoltStud(pixmap, readerX + 8, readerY + readerHeight - 8);
        drawDoorBoltStud(pixmap, readerX + readerWidth - 8, readerY + readerHeight - 8);
        int ledCenterY = readerY + 24;
        pixmap.setColor(accentDarkRed, accentDarkGreen, accentDarkBlue, 1f);   // LED halo
        pixmap.fillCircle(seamCenter, ledCenterY, 11);
        pixmap.setColor(accentRed, accentGreen, accentBlue, 1f);
        pixmap.fillCircle(seamCenter, ledCenterY, 7);
        pixmap.setColor(accentGlowRed, accentGlowGreen, accentGlowBlue, 1f);
        pixmap.fillCircle(seamCenter, ledCenterY, 3);
        int slotY = readerY + 54;                      // card slot (bright accent slit)
        pixmap.setColor(0.03f, 0.03f, 0.04f, 1f);
        pixmap.fillRectangle(readerX + 14, slotY, readerWidth - 28, 12);
        pixmap.setColor(accentGlowRed, accentGlowGreen, accentGlowBlue, 1f);
        pixmap.fillRectangle(readerX + 16, slotY + 4, readerWidth - 32, 3);
        pixmap.setColor(accentDarkRed, accentDarkGreen, accentDarkBlue, 1f);   // keypad dots (3×2)
        for (int keypadRow = 0; keypadRow < 2; keypadRow++) {
            for (int keypadColumn = 0; keypadColumn < 3; keypadColumn++) {
                pixmap.fillCircle(readerX + 26 + keypadColumn * 22,
                                  readerY + 92 + keypadRow * 22, 5);
            }
        }

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    /**
     * Registers this renderer's wall + column texture generators into the tileset texture-generator
     * registry (symbol/sprite-reuse order-6). One line per sprite id, each delegating to the SAME
     * procedural routine (or disk load) the constructor uses today, so the realized textures are
     * byte-identical to the constructor-built table — order-6 changes WHERE the textures live, not how
     * they look. Called once at startup by {@code TilesetGfxBootstrap}; the ids registered here must
     * match the WALL/COLUMN sprite ids in {@code TilesetRegistries} (the startup assertion enforces it).
     *
     * <p>Disk-backed walls (plain/conduit/vent/terminal/wires) reproduce today's load path exactly;
     * 'hazard' reproduces the constructor's load-or-generate fallback.
     */
    public static void registerTextureGenerators(TextureGeneratorRegistry registry) {
        registry.register("wall_plain",       () -> loadWallTexture(LAB_WALL_PLAIN_PATH));
        registry.register("wall_conduit",     () -> loadWallTexture(LAB_WALL_CONDUIT_PATH));
        registry.register("wall_vent",        () -> loadWallTexture(LAB_WALL_VENT_PATH));
        registry.register("wall_terminal",    () -> loadWallTexture(LAB_WALL_TERMINAL_PATH));
        registry.register("wall_wires",       () -> loadWallTexture(LAB_WALL_WIRES_PATH));
        registry.register("wall_hazard",      WallRenderer::loadOrGenerateHazardWallTexture);
        registry.register("wall_rust",        WallRenderer::generateRustWallTexture);
        registry.register("wall_gore",        WallRenderer::generateGoreWallTexture);
        registry.register("wall_bulkhead",    WallRenderer::generateBulkheadWallTexture);
        registry.register("wall_glass",       WallRenderer::generateGlassWallTexture);
        registry.register("wall_bio",         WallRenderer::generateBioWallTexture);
        registry.register("wall_emergency",   WallRenderer::generateEmergWallTexture);
        registry.register("wall_medical",     WallRenderer::generateMedWallTexture);
        registry.register("wall_cryo",        WallRenderer::generateCryoWallTexture);
        registry.register("wall_radiation",   WallRenderer::generateRadWallTexture);
        registry.register("wall_blast",       WallRenderer::generateBlastWallTexture);
        registry.register("wall_holo_data",   WallRenderer::generateHoloDataWallTexture);
        registry.register("wall_force_field", WallRenderer::generateForceFieldWallTexture);
        registry.register("wall_stellar_viewport",   WallRenderer::generateStellarViewportWallTexture);
        registry.register("wall_stellar_magrail",    WallRenderer::generateStellarMagrailWallTexture);
        registry.register("wall_stellar_hull_plate", WallRenderer::generateStellarHullPlateWallTexture);
        registry.register(TilesetRegistries.SPRITE_ID_COLUMN_ROUND,  WallRenderer::generateColumnTexture);
        registry.register(TilesetRegistries.SPRITE_ID_COLUMN_SQUARE, WallRenderer::generateColumnSquareTexture);
        registry.register(TilesetRegistries.SPRITE_ID_WALL_HEX_PLATE, WallRenderer::generateHexPlateWallTexture);
        registry.register(TilesetRegistries.SPRITE_ID_WALL_PLAIN_SANDSTONE, WallRenderer::generateSandstoneWallTexture);
        registry.register(TilesetRegistries.SPRITE_ID_WALL_PLAIN_CLEAN,     WallRenderer::generateCleanLabWallTexture);
        registry.register(TilesetRegistries.SPRITE_ID_WALL_PLAIN_TILED,     WallRenderer::generateTiledWallTexture);
    }

    /**
     * Load-or-generate for the hazard wall, mirroring the constructor: use the disk asset when present,
     * otherwise the procedural caution-stripe texture. Kept as a helper so the generator registration
     * reads as one line and shares the exact fallback logic.
     */
    private static Texture loadOrGenerateHazardWallTexture() {
        return Gdx.files.internal(LAB_WALL_HAZARD_PATH).exists()
               ? loadWallTexture(LAB_WALL_HAZARD_PATH)
               : generateHazardWallTexture();
    }

    private static Texture generateColumnTexture() {
        int textureWidth  = COLUMN_TEXTURE_WIDTH;
        int textureHeight = COLUMN_TEXTURE_HEIGHT;
        Pixmap pixmap = new Pixmap(textureWidth, textureHeight, Pixmap.Format.RGBA8888);
        // Stone base: medium gray with a subtle pseudo-random variation per pixel.
        for (int row = 0; row < textureHeight; row++) {
            for (int column = 0; column < textureWidth; column++) {
                float variation = ((row * 7 + column * 11) % 13 - 6) / 90f;
                float gray = 0.50f + variation;
                pixmap.setColor(gray, gray, gray + 0.02f, 1f);
                pixmap.drawPixel(column, row);
            }
        }
        // Horizontal mortar joints at each stone course line.
        pixmap.setColor(0.28f, 0.28f, 0.30f, 1f);
        for (int row = COLUMN_MORTAR_SPACING; row < textureHeight; row += COLUMN_MORTAR_SPACING) {
            pixmap.fillRectangle(0, row, textureWidth, 4);
        }
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    /**
     * order-8 REQUIREMENT PROOF 2 — the square column sprite. A boxy gunmetal support pillar: a
     * flat metal panel with bright vertical edge highlights (so the billboard reads as a squared-off
     * column rather than a rounded stone one), horizontal panel seams, and corner rivets. It shares
     * the same texture dimensions as {@link #generateColumnTexture} and is drawn through the identical
     * billboard/ray path, so no new geometry is required to SHIP THE SPRITE; the FLEXIBLE 'P' slot now
     * resolves to this OR the round column per level (see docs/environment-tileset-system.txt §8,
     * SQUARE-COLUMN GEOMETRY note). Deferred: true ray-BOX cross-section — the pillar still uses the
     * ray-circle collision of the round column, only the surface art differs.
     */
    private static Texture generateColumnSquareTexture() {
        int textureWidth  = COLUMN_TEXTURE_WIDTH;
        int textureHeight = COLUMN_TEXTURE_HEIGHT;
        Pixmap pixmap = new Pixmap(textureWidth, textureHeight, Pixmap.Format.RGBA8888);
        // Gunmetal panel base with a faint per-pixel grain. Slightly cooler/darker than the round
        // stone column so the two read as different materials at a glance.
        for (int row = 0; row < textureHeight; row++) {
            for (int column = 0; column < textureWidth; column++) {
                float grain = ((row * 7 + column * 11) % 13 - 6) / 120f;
                float gray  = 0.42f + grain;
                pixmap.setColor(gray, gray, gray + 0.05f, 1f);
                pixmap.drawPixel(column, row);
            }
        }
        // Bright vertical edge highlights near the left/right faces read the pillar as squared-off.
        int edgeWidth = Math.max(2, textureWidth / 12);
        for (int row = 0; row < textureHeight; row++) {
            for (int column = 0; column < edgeWidth; column++) {
                float edgeShade = 0.68f - (column / (float) edgeWidth) * 0.20f;
                pixmap.setColor(edgeShade, edgeShade, edgeShade + 0.05f, 1f);
                pixmap.drawPixel(column, row);
                pixmap.drawPixel(textureWidth - 1 - column, row);
            }
        }
        // Recessed dark panel seams at each course line (horizontal), thinner than the stone mortar.
        pixmap.setColor(0.20f, 0.20f, 0.23f, 1f);
        for (int row = COLUMN_MORTAR_SPACING; row < textureHeight; row += COLUMN_MORTAR_SPACING) {
            pixmap.fillRectangle(edgeWidth, row, textureWidth - 2 * edgeWidth, 3);
        }
        // Corner rivets at each seam intersection near the panel edges.
        pixmap.setColor(0.72f, 0.72f, 0.76f, 1f);
        int rivetInset = edgeWidth + 3;
        for (int row = COLUMN_MORTAR_SPACING; row < textureHeight; row += COLUMN_MORTAR_SPACING) {
            pixmap.fillRectangle(rivetInset, row - 4, 3, 3);
            pixmap.fillRectangle(textureWidth - rivetInset - 3, row - 4, 3, 3);
        }
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    /**
     * order-9 RECIPE A acceptance sprite — the hex-plate accent wall ("wall_hex_plate"). A tessellated
     * honeycomb of hexagonal steel panels: recessed dark seams between plates and a soft
     * centre-to-edge shade per panel with a faint per-panel tint. The honeycomb is the Voronoi diagram of
     * a triangular lattice (each lattice point's Voronoi cell is a regular flat-top hexagon), so a pixel
     * is a SEAM when its two nearest lattice points are almost equidistant (it sits on a cell boundary),
     * and a PANEL otherwise, shaded by its distance to the owning lattice point.
     *
     * <p>Added by REGISTRATION ALONE (RECIPE A): this generator + its one registration line in
     * {@link #registerTextureGenerators}, plus the sprite definition in {@code TilesetRegistries}. No
     * edits to the draw loop, {@code LevelGenerator}, {@code PropRenderer}, or {@code Level} — the
     * FLEXIBLE wall symbols resolve to it per level through the allocator. See
     * docs/environment-tileset-system.txt §9.
     */
    private static Texture generateHexPlateWallTexture() {
        int   size       = HEX_PLATE_WALL_TEXTURE_SIZE;
        float spacingX   = HEX_PLATE_CELL_RADIUS;
        float spacingY   = HEX_PLATE_CELL_RADIUS * (float) (Math.sqrt(3.0) / 2.0);
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        for (int pixelRow = 0; pixelRow < size; pixelRow++) {
            for (int pixelColumn = 0; pixelColumn < size; pixelColumn++) {
                float nearestSquared       = Float.MAX_VALUE;
                float secondNearestSquared = Float.MAX_VALUE;
                int   nearestLatticeRow    = 0;
                int   nearestLatticeColumn = 0;

                int centreLatticeRow = Math.round(pixelRow / spacingY);
                for (int latticeRow = centreLatticeRow - 1; latticeRow <= centreLatticeRow + 1; latticeRow++) {
                    float latticeCentreY = latticeRow * spacingY;
                    float rowOffsetX     = (latticeRow & 1) == 0 ? 0f : spacingX / 2f;
                    int   centreLatticeColumn = Math.round((pixelColumn - rowOffsetX) / spacingX);
                    for (int latticeColumn = centreLatticeColumn - 1; latticeColumn <= centreLatticeColumn + 1; latticeColumn++) {
                        float latticeCentreX = latticeColumn * spacingX + rowOffsetX;
                        float differenceX    = pixelColumn - latticeCentreX;
                        float differenceY    = pixelRow    - latticeCentreY;
                        float distanceSquared = differenceX * differenceX + differenceY * differenceY;
                        if (distanceSquared < nearestSquared) {
                            secondNearestSquared = nearestSquared;
                            nearestSquared       = distanceSquared;
                            nearestLatticeRow    = latticeRow;
                            nearestLatticeColumn = latticeColumn;
                        } else if (distanceSquared < secondNearestSquared) {
                            secondNearestSquared = distanceSquared;
                        }
                    }
                }

                float edgeMargin = (float) Math.sqrt(secondNearestSquared) - (float) Math.sqrt(nearestSquared);
                if (edgeMargin < HEX_PLATE_SEAM_WIDTH) {
                    // Recessed dark groove between hexagonal panels.
                    pixmap.setColor(0.16f, 0.16f, 0.19f, 1f);
                } else {
                    // Steel panel: brighter at the centre, subtly darker toward the seam, with a faint
                    // deterministic per-panel tint so neighbouring plates read as separate pieces.
                    float centreFade = Math.min(1f, (float) Math.sqrt(nearestSquared) / HEX_PLATE_CELL_RADIUS);
                    float panelTint  = ((nearestLatticeRow * 73 + nearestLatticeColumn * 31) % 7 - 3) / 90f;
                    float gray       = 0.46f - centreFade * 0.12f + panelTint;
                    pixmap.setColor(Math.max(0f, gray), Math.max(0f, gray), Math.max(0f, gray + 0.04f), 1f);
                }
                pixmap.drawPixel(pixelColumn, pixelRow);
            }
        }

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    /**
     * Alternate BASE WALL — "wall_plain_sandstone": a warm, light sandstone-block facility. Staggered
     * ashlar masonry (a running-bond course pattern) in warm tan, with pale grout joints, a soft per-block
     * bevel, and a deterministic per-block tint so neighbouring stones differ. Choosing it makes the whole
     * floor read as an older, warmer stone facility rather than the neutral metal default. Designed to the
     * BASE-WALL brief: a strong FACILITY IDENTITY (warm hue + stone material) yet LIGHT (never dark),
     * horizontally tileable (the block width and course shift both divide the texture width, so the bond
     * wraps and the left/right edges meet seamlessly), and vertically EVEN in brightness (no bright band —
     * distance-shading multiplies the texture, so a band would look like fake lighting). Deterministic: the
     * same pixels every run. Reached only through the allocator's per-level base-wall roll for 'x' on
     * generated levels — see docs/environment-tileset-system.txt (BASE-WALL VARIETY). Added by REGISTRATION
     * ALONE (its sprite definition in {@code TilesetRegistries} + one line in
     * {@link #registerTextureGenerators}); no draw-loop, {@code LevelGenerator}, or {@code Level} edits.
     */
    private static Texture generateSandstoneWallTexture() {
        int size    = SANDSTONE_WALL_TEXTURE_SIZE;
        int blockWidth  = SANDSTONE_BLOCK_WIDTH;
        int blockHeight = SANDSTONE_BLOCK_HEIGHT;
        int mortar  = SANDSTONE_MORTAR_THICKNESS;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        for (int row = 0; row < size; row++) {
            int course = row / blockHeight;
            // Running bond: every other course is shifted half a block, so vertical joints stagger. The
            // shift is a divisor of the width, so the pattern still wraps seamlessly across tiles.
            int courseShift = (course & 1) == 0 ? 0 : blockWidth / 2;
            int localY = row % blockHeight;
            for (int column = 0; column < size; column++) {
                int shiftedColumn = (column + courseShift) % blockWidth;
                boolean horizontalJoint = localY < mortar;
                boolean verticalJoint   = shiftedColumn < mortar;

                float grain = ((row * 7 + column * 11) % 13 - 6) / 200f;
                if (horizontalJoint || verticalJoint) {
                    // Pale warm grout — LIGHTER than the blocks so joints read without darkening the wall.
                    pixmap.setColor(0.80f + grain, 0.75f + grain, 0.66f + grain, 1f);
                } else {
                    // Warm sandstone block with a deterministic per-block tint so neighbours differ, plus a
                    // soft top+left bevel highlight and a gentle bottom+right shade (both stay light).
                    int blockIndexX = (column + courseShift) / blockWidth;
                    float blockTint = ((blockIndexX * 37 + course * 53) % 9 - 4) / 70f;
                    float baseRed   = 0.80f + blockTint + grain;
                    float baseGreen = 0.68f + blockTint + grain;
                    float baseBlue  = 0.50f + blockTint + grain;
                    boolean bevelLight = localY < mortar + 3 || shiftedColumn < mortar + 3;
                    boolean bevelShade = localY >= blockHeight - 3 || shiftedColumn >= blockWidth - 3;
                    if (bevelLight) {
                        baseRed += 0.08f; baseGreen += 0.08f; baseBlue += 0.07f;
                    } else if (bevelShade) {
                        baseRed -= 0.07f; baseGreen -= 0.06f; baseBlue -= 0.05f;
                    }
                    pixmap.setColor(Math.max(0f, Math.min(1f, baseRed)),
                                    Math.max(0f, Math.min(1f, baseGreen)),
                                    Math.max(0f, Math.min(1f, baseBlue)), 1f);
                }
                pixmap.drawPixel(column, row);
            }
        }

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    /**
     * Alternate BASE WALL — "wall_plain_clean": a bright, airy clinical-lab facility. Large off-white
     * composite panels on a recessed grid (one central vertical seam, evenly-spaced horizontal seams),
     * each panel carrying a soft centre sheen, with light steel corner bolts at the seam intersections.
     * A high-key COOL palette — deliberately the opposite feel of the warm sandstone wall — so choosing it
     * makes the floor read as a pristine science wing. Same BASE-WALL brief: LIGHT (never dark),
     * horizontally tileable (the vertical seam is interior, the left/right edges stay plain panel so tiles
     * meet seamlessly), and vertically even in brightness. A fixed-seed {@link Random} adds a faint speckle
     * so the wall is byte-identical every run (permadeath-fair). Reached only through the allocator's
     * per-level base-wall roll for 'x'.
     */
    private static Texture generateCleanLabWallTexture() {
        int size   = CLEANLAB_WALL_TEXTURE_SIZE;
        int seam   = CLEANLAB_SEAM_THICKNESS;
        int panelHeight = size / CLEANLAB_PANEL_ROWS; // horizontal seams on multiples of this
        int verticalSeamColumn = size / 2;            // single interior vertical seam (edges stay plain)
        Random random = new Random(CLEANLAB_WALL_SEED);
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        for (int row = 0; row < size; row++) {
            int localY = row % panelHeight;
            for (int column = 0; column < size; column++) {
                float speckle = (random.nextFloat() - 0.5f) * 0.02f;
                // Soft per-panel sheen: brightest a little above panel centre, easing to the edges — a
                // gentle lift, not a hard band, so overall luminance stays even.
                float panelCentreDistance = Math.abs(localY - panelHeight * 0.42f) / panelHeight;
                float sheen = (0.10f - panelCentreDistance * 0.14f);
                float baseRed   = 0.80f + sheen + speckle;
                float baseGreen = 0.83f + sheen + speckle;
                float baseBlue  = 0.87f + sheen + speckle; // cool: blue leads
                pixmap.setColor(Math.max(0f, Math.min(1f, baseRed)),
                                Math.max(0f, Math.min(1f, baseGreen)),
                                Math.max(0f, Math.min(1f, baseBlue)), 1f);
                pixmap.drawPixel(column, row);
            }
        }

        // Recessed grid seams: a cool mid line with a bright lip on the lit side.
        // Horizontal seams at each panel course (skip row 0 so the top edge stays plain panel).
        for (int seamRow = panelHeight; seamRow < size; seamRow += panelHeight) {
            pixmap.setColor(0.62f, 0.67f, 0.74f, 1f);
            pixmap.fillRectangle(0, seamRow, size, seam);
            pixmap.setColor(0.92f, 0.95f, 0.98f, 1f);
            pixmap.fillRectangle(0, seamRow - 1, size, 1);
        }
        // One interior vertical seam (edges stay plain so the texture wraps).
        pixmap.setColor(0.62f, 0.67f, 0.74f, 1f);
        pixmap.fillRectangle(verticalSeamColumn, 0, seam, size);
        pixmap.setColor(0.92f, 0.95f, 0.98f, 1f);
        pixmap.fillRectangle(verticalSeamColumn - 1, 0, 1, size);

        // Light steel corner bolts where the vertical seam crosses each horizontal seam.
        for (int seamRow = panelHeight; seamRow < size; seamRow += panelHeight) {
            pixmap.setColor(0.70f, 0.74f, 0.80f, 1f);
            pixmap.fillRectangle(verticalSeamColumn - 3, seamRow - 3, 3, 3);
            pixmap.fillRectangle(verticalSeamColumn + seam, seamRow - 3, 3, 3);
        }

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    /**
     * Alternate BASE WALL — "wall_plain_tiled": a light sage/teal glazed CERAMIC-TILE facility (a
     * hydroponics / sanitation wing). A tight grid of small glazed tiles: pale grout lines, a soft
     * top-left glaze sheen on each tile, and a faint deterministic per-tile tint so no two glazes match.
     * Its GREEN-TEAL hue and small tile scale set it apart from the warm sandstone blocks and the bright
     * blue-white composite panels — a third, unmistakable facility identity. Same BASE-WALL brief: LIGHT
     * (never dark), horizontally tileable (the tile size divides the texture width, so the grid is
     * perfectly periodic and wraps), and vertically even in brightness (thin regular grout, gentle sheen —
     * no bright band). A fixed-seed {@link Random} adds a faint per-tile tint so the wall is byte-identical
     * every run (permadeath-fair). Reached only through the allocator's per-level base-wall roll for 'x'.
     */
    private static Texture generateTiledWallTexture() {
        int size  = TILED_WALL_TEXTURE_SIZE;
        int tile  = TILED_WALL_TILE_SIZE;
        int grout = TILED_WALL_GROUT_THICKNESS;
        Random random = new Random(TILED_WALL_SEED);

        // Deterministic per-tile tint, keyed by tile grid position (independent of pixel iteration order).
        int tilesPerAxis = size / tile;
        float[][] tileTint = new float[tilesPerAxis][tilesPerAxis];
        for (int tileRow = 0; tileRow < tilesPerAxis; tileRow++) {
            for (int tileColumn = 0; tileColumn < tilesPerAxis; tileColumn++) {
                tileTint[tileRow][tileColumn] = (random.nextFloat() - 0.5f) * 0.06f;
            }
        }

        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        for (int row = 0; row < size; row++) {
            int localY = row % tile;
            for (int column = 0; column < size; column++) {
                int localX = column % tile;
                boolean groutLine = localX < grout || localY < grout;
                if (groutLine) {
                    // Pale cool grout — LIGHTER than the glaze so joints read without darkening the wall.
                    pixmap.setColor(0.82f, 0.85f, 0.83f, 1f);
                } else {
                    float tint = tileTint[row / tile][column / tile];
                    // Sage/teal glaze base (green + blue lead the red for a cool, verdant read).
                    float baseRed   = 0.58f + tint;
                    float baseGreen = 0.76f + tint;
                    float baseBlue  = 0.70f + tint;
                    // Soft top-left glaze sheen: a diagonal highlight easing away from the tile's lit corner.
                    float sheen = (tile - localX - localY) / (float) (2 * tile); // ~0.5 top-left -> ~-0.5 bottom-right
                    float lift  = Math.max(0f, sheen) * 0.12f;
                    pixmap.setColor(Math.max(0f, Math.min(1f, baseRed + lift)),
                                    Math.max(0f, Math.min(1f, baseGreen + lift)),
                                    Math.max(0f, Math.min(1f, baseBlue + lift)), 1f);
                }
                pixmap.drawPixel(column, row);
            }
        }

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    /**
     * Generates a yellow/black diagonal hazard stripe wall texture.
     * The pattern emulates industrial caution tape: alternating 45° bands inside a
     * dark-steel border frame with corner rivets. No asset file required.
     */
    private static Texture generateHazardWallTexture() {
        int textureSize = HAZARD_WALL_TEXTURE_SIZE;
        Pixmap pixmap = new Pixmap(textureSize, textureSize, Pixmap.Format.RGBA8888);

        int bandWidth = 44; // pixels per yellow+black diagonal band pair

        for (int row = 0; row < textureSize; row++) {
            for (int column = 0; column < textureSize; column++) {
                int diagonalPosition = ((row + column) % bandWidth + bandWidth) % bandWidth;
                boolean isYellow = diagonalPosition < (bandWidth / 2);
                float noise = ((row * 7 + column * 11) % 13 - 6) / 120f;

                if (isYellow) {
                    float red   = Math.min(1f, 0.93f + noise * 0.04f);
                    float green = Math.min(1f, 0.79f + noise * 0.07f);
                    float blue  = Math.max(0f, 0.04f + noise * 0.02f);
                    pixmap.setColor(red, green, blue, 1f);
                } else {
                    float gray = Math.max(0f, 0.09f + noise * 0.03f);
                    pixmap.setColor(gray, gray, gray + 0.02f, 1f);
                }
                pixmap.drawPixel(column, row);
            }
        }

        // Dark-steel border frame (8 px each side)
        pixmap.setColor(0x33 / 255f, 0x33 / 255f, 0x3A / 255f, 1f);
        pixmap.fillRectangle(0, 0, textureSize, 8);
        pixmap.fillRectangle(0, textureSize - 8, textureSize, 8);
        pixmap.fillRectangle(0, 0, 8, textureSize);
        pixmap.fillRectangle(textureSize - 8, 0, 8, textureSize);

        // Corner rivets
        pixmap.setColor(0x58 / 255f, 0x58 / 255f, 0x64 / 255f, 1f);
        int rivetSize   = 10;
        int rivetOffset = 14;
        pixmap.fillRectangle(rivetOffset, rivetOffset, rivetSize, rivetSize);
        pixmap.fillRectangle(textureSize - rivetOffset - rivetSize, rivetOffset, rivetSize, rivetSize);
        pixmap.fillRectangle(rivetOffset, textureSize - rivetOffset - rivetSize, rivetSize, rivetSize);
        pixmap.fillRectangle(textureSize - rivetOffset - rivetSize,
                             textureSize - rivetOffset - rivetSize, rivetSize, rivetSize);

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    /**
     * Generates a corroded steel wall texture.
     * Steel base overpainted with organic rust blotches (maroon → orange → ochre),
     * heaviest at the bottom (water wicks up). Panel seams at 1/3 and 2/3 height,
     * corroded rivet heads with drip streaks, and black perforation pits.
     */
    private static Texture generateRustWallTexture() {
        int    size   = RUST_WALL_TEXTURE_SIZE;
        Random random = new Random(RUST_WALL_SEED);
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        int[] blobCenterX = new int[RUST_BLOB_COUNT];
        int[] blobCenterY = new int[RUST_BLOB_COUNT];
        int[] blobRadius  = new int[RUST_BLOB_COUNT];
        int[] blobTone    = new int[RUST_BLOB_COUNT]; // 0=deep, 1=mid, 2=pale
        for (int blobIndex = 0; blobIndex < RUST_BLOB_COUNT; blobIndex++) {
            blobCenterX[blobIndex] = random.nextInt(size);
            blobCenterY[blobIndex] = random.nextInt(size);
            blobRadius[blobIndex]  = 16 + random.nextInt(42);
            blobTone[blobIndex]    = random.nextInt(3);
        }

        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                float bottomFactor = (float) row / (size - 1);
                float grain = ((row * 7 + column * 11) % 13 - 6) / 100f;

                float totalRustWeight = 0f;
                float blendRed = 0f, blendGreen = 0f, blendBlue = 0f;
                for (int blobIndex = 0; blobIndex < RUST_BLOB_COUNT; blobIndex++) {
                    float differenceX = column - blobCenterX[blobIndex];
                    float differenceY = row    - blobCenterY[blobIndex];
                    float distance    = (float) Math.sqrt(differenceX * differenceX + differenceY * differenceY);
                    float weight      = GameMath.radialFalloff(distance, blobRadius[blobIndex]);
                    if (weight > 0f) {
                        float toneRed, toneGreen, toneBlue;
                        if (blobTone[blobIndex] == 0) {
                            toneRed = 0.34f; toneGreen = 0.16f; toneBlue = 0.09f; // deep maroon
                        } else if (blobTone[blobIndex] == 1) {
                            toneRed = 0.55f; toneGreen = 0.27f; toneBlue = 0.10f; // rust orange
                        } else {
                            toneRed = 0.62f; toneGreen = 0.45f; toneBlue = 0.22f; // pale ochre
                        }
                        blendRed   += weight * toneRed;
                        blendGreen += weight * toneGreen;
                        blendBlue  += weight * toneBlue;
                        totalRustWeight += weight;
                    }
                }
                float rustAmount = Math.min(1f, totalRustWeight) * bottomFactor;
                if (totalRustWeight > 0f) {
                    blendRed   /= totalRustWeight;
                    blendGreen /= totalRustWeight;
                    blendBlue  /= totalRustWeight;
                }

                float steelRed   = 0.30f + grain;
                float steelGreen = 0.31f + grain;
                float steelBlue  = 0.34f + grain;
                float red   = steelRed   + rustAmount * (blendRed   - steelRed);
                float green = steelGreen + rustAmount * (blendGreen - steelGreen);
                float blue  = steelBlue  + rustAmount * (blendBlue  - steelBlue);
                pixmap.setColor(Math.max(0f, Math.min(1f, red)),
                                Math.max(0f, Math.min(1f, green)),
                                Math.max(0f, Math.min(1f, blue)), 1f);
                pixmap.drawPixel(column, row);
            }
        }

        // Panel seams at 1/3 and 2/3 height
        pixmap.setColor(0.14f, 0.13f, 0.13f, 1f);
        pixmap.fillRectangle(0, size / 3, size, 3);
        pixmap.fillRectangle(0, 2 * size / 3, size, 3);

        // Corroded rivets and drip streaks at each seam
        int[] seamRows = { size / 3, 2 * size / 3 };
        for (int seamRow : seamRows) {
            for (int rivetX = size / 4; rivetX < size; rivetX += size / 4) {
                pixmap.setColor(0.20f, 0.14f, 0.10f, 1f);
                pixmap.fillRectangle(rivetX - 2, seamRow - 2, 5, 5);
                int dripLength = 16 + random.nextInt(32);
                for (int dripRow = seamRow + 5; dripRow < Math.min(size, seamRow + 5 + dripLength); dripRow++) {
                    float dripFade = 1f - (float)(dripRow - seamRow - 5) / dripLength;
                    float dripDark = 0.26f * dripFade;
                    pixmap.setColor(Math.max(0f, dripDark),
                                    Math.max(0f, dripDark * 0.5f),
                                    Math.max(0f, dripDark * 0.27f), 1f);
                    pixmap.fillRectangle(rivetX, dripRow, 2, 1);
                }
            }
        }

        // Perforation pits in the heavier bottom rust zone
        for (int pitIndex = 0; pitIndex < RUST_PIT_COUNT; pitIndex++) {
            int pitColumn = random.nextInt(size);
            int pitRow    = size / 2 + random.nextInt(size / 2);
            pixmap.setColor(0.05f, 0.04f, 0.04f, 1f);
            pixmap.fillRectangle(pitColumn, pitRow, 3, 3);
        }

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    /**
     * Generates a wet demonic flesh wall texture ('G') — full procedural redesign.
     *
     * Layered, fully procedural, and tileable. A metaball "flesh field" (toroidally wrapped
     * so the membrane masses tile seamlessly) drives every layer:
     *   1. Membrane body — deep visceral maroon in the recesses, raw bright muscle on the
     *      raised masses, with bump-relief shading derived from the field gradient (light from
     *      the top-left) so the bulges read as three-dimensional.
     *   2. Wet sheen — soft specular blooms on the upper-left of each fleshy bulge.
     *   3. Subdermal vessels and sinew veins — smooth float-walks (no 8-direction blockiness)
     *      with tapering thickness and a glistening highlight edge.
     *   4. Open gashes — parted lip rims, a dark wound cavity, a bright glistening interior,
     *      and a blood drip running downward from the base of each wound.
     *   5. Pustules — domed boils with rim shadow and dome highlight; a third run sickly-yellow
     *      (infected).
     *   6. Bone nodules, pores, and scattered wet glints.
     */
    private static Texture generateGoreWallTexture() {
        int    size   = GORE_WALL_TEXTURE_SIZE;
        Random random = new Random(GORE_WALL_SEED);
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        // ── Flesh field: metaballs spread across the whole tile, distance wrapped toroidally ──
        int[] blobCenterX = new int[GORE_BLOB_COUNT];
        int[] blobCenterY = new int[GORE_BLOB_COUNT];
        int[] blobRadius  = new int[GORE_BLOB_COUNT];
        for (int blobIndex = 0; blobIndex < GORE_BLOB_COUNT; blobIndex++) {
            blobCenterX[blobIndex] = random.nextInt(size);
            blobCenterY[blobIndex] = random.nextInt(size);
            blobRadius[blobIndex]  = GORE_BLOB_RADIUS_MIN + random.nextInt(GORE_BLOB_RADIUS_SPAN);
        }
        float[] fleshWeightMap = new float[size * size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                float totalWeight = 0f;
                for (int blobIndex = 0; blobIndex < GORE_BLOB_COUNT; blobIndex++) {
                    float differenceX = column - blobCenterX[blobIndex];
                    float differenceY = row    - blobCenterY[blobIndex];
                    if (differenceX >  size / 2f) differenceX -= size;
                    if (differenceX < -size / 2f) differenceX += size;
                    if (differenceY >  size / 2f) differenceY -= size;
                    if (differenceY < -size / 2f) differenceY += size;
                    float distance = (float) Math.sqrt(differenceX * differenceX + differenceY * differenceY);
                    totalWeight += GameMath.smoothstep01(GameMath.radialFalloff(distance, blobRadius[blobIndex]));
                }
                fleshWeightMap[row * size + column] = Math.min(1f, totalWeight);
            }
        }

        // ── Membrane body with bump-relief shading (light from the top-left) ──
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                float fleshWeight = fleshWeightMap[row * size + column];
                float grain  = ((row * 7 + column * 11) % 13 - 6) / 120f;
                float mottle = (float)(Math.sin(column * 0.11 + row * 0.05)
                                     * Math.sin(row * 0.13 - column * 0.04)) * 0.05f;

                int leftColumn  = column > 0        ? column - 1 : column;
                int rightColumn = column < size - 1 ? column + 1 : column;
                int upRow        = row > 0          ? row - 1    : row;
                int downRow      = row < size - 1   ? row + 1    : row;
                float gradientX = fleshWeightMap[row * size + rightColumn] - fleshWeightMap[row * size + leftColumn];
                float gradientY = fleshWeightMap[downRow * size + column]  - fleshWeightMap[upRow * size + column];
                float emboss    = -(gradientX * -0.7f + gradientY * -0.7f) * GORE_EMBOSS_STRENGTH;

                float red, green, blue;
                if (fleshWeight > GORE_FLESH_THRESHOLD) {
                    float interpolationFactor = (fleshWeight - GORE_FLESH_THRESHOLD) / (1f - GORE_FLESH_THRESHOLD);
                    red   = 0.30f + interpolationFactor * 0.46f;   // dark visceral → bright muscle
                    green = 0.05f + interpolationFactor * 0.14f;
                    blue  = 0.06f + interpolationFactor * 0.13f;
                } else {
                    float bottomFactor = (float) row / (size - 1);  // bloodier toward the bottom
                    red   = 0.20f + bottomFactor * 0.05f;
                    green = 0.035f;
                    blue  = 0.05f;
                }
                red   += grain + mottle + emboss * 0.30f;
                green += grain * 0.6f + mottle * 0.5f + emboss * 0.16f;
                blue  += grain * 0.6f + mottle * 0.5f + emboss * 0.16f;
                pixmap.setColor(clampUnit(red), clampUnit(green), clampUnit(blue), 1f);
                pixmap.drawPixel(column, row);
            }
        }

        // ── Wet sheen: soft specular blooms on the upper-left of fleshy bulges (toroidal) ──
        float[] sheenBoost = new float[size * size];
        for (int sheenIndex = 0; sheenIndex < GORE_SHEEN_COUNT; sheenIndex++) {
            int blobIndex = sheenIndex % GORE_BLOB_COUNT;
            int sheenCenterX = blobCenterX[blobIndex] - 6 - random.nextInt(6);
            int sheenCenterY = blobCenterY[blobIndex] - 6 - random.nextInt(6);
            int sheenRadius  = 8 + random.nextInt(10);
            for (int offsetY = -sheenRadius; offsetY <= sheenRadius; offsetY++) {
                for (int offsetX = -sheenRadius; offsetX <= sheenRadius; offsetX++) {
                    int pixelColumn = ((sheenCenterX + offsetX) % size + size) % size;
                    int pixelRow    = ((sheenCenterY + offsetY) % size + size) % size;
                    float distance = (float) Math.sqrt(offsetX * offsetX + offsetY * offsetY);
                    float falloff  = GameMath.smoothstep01(GameMath.radialFalloff(distance, sheenRadius));
                    if (falloff <= 0f || fleshWeightMap[pixelRow * size + pixelColumn] < 0.5f) continue;
                    sheenBoost[pixelRow * size + pixelColumn] += falloff * 0.22f;
                }
            }
        }
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                float boost = sheenBoost[row * size + column];
                if (boost <= 0f) continue;
                int pixel = pixmap.getPixel(column, row);
                float red   = ((pixel >>> 24) & 0xFF) / 255f + boost;
                float green = ((pixel >>> 16) & 0xFF) / 255f + boost * 0.55f;
                float blue  = ((pixel >>>  8) & 0xFF) / 255f + boost * 0.5f;
                pixmap.setColor(clampUnit(red), clampUnit(green), clampUnit(blue), 1f);
                pixmap.drawPixel(column, row);
            }
        }

        // ── Subdermal vessels: faint purple smooth float-walks beneath the sheen ──
        for (int veinIndex = 0; veinIndex < GORE_SUBDERMAL_VEIN_COUNT; veinIndex++) {
            float positionX = random.nextInt(size);
            float positionY = random.nextInt(size);
            float angleRadians = random.nextFloat() * (float)(Math.PI * 2);
            float driftRadians = (random.nextFloat() - 0.5f) * 0.06f;
            for (int stepIndex = 0; stepIndex < GORE_VEIN_STEPS; stepIndex++) {
                int veinColumn = Math.round(positionX);
                int veinRow    = Math.round(positionY);
                if (veinColumn < 0 || veinColumn >= size || veinRow < 0 || veinRow >= size) break;
                if (fleshWeightMap[veinRow * size + veinColumn] > 0.42f) {
                    pixmap.setColor(0.24f, 0.05f, 0.14f, 0.7f);
                    pixmap.drawPixel(veinColumn, veinRow);
                }
                angleRadians += driftRadians + (random.nextFloat() - 0.5f) * 0.32f;
                positionX += (float) Math.cos(angleRadians);
                positionY += (float) Math.sin(angleRadians);
            }
        }

        // ── Sinew veins: tapering crimson vessels with a wet highlight edge ──
        for (int veinIndex = 0; veinIndex < GORE_VEIN_COUNT; veinIndex++) {
            float positionX = random.nextInt(size);
            float positionY = random.nextInt(size);
            float angleRadians = random.nextFloat() * (float)(Math.PI * 2);
            float driftRadians = (random.nextFloat() - 0.5f) * 0.05f;
            int   veinLife = 40 + random.nextInt(GORE_VEIN_STEPS);
            for (int stepIndex = 0; stepIndex < veinLife; stepIndex++) {
                int veinColumn = Math.round(positionX);
                int veinRow    = Math.round(positionY);
                if (veinColumn < 0 || veinColumn >= size || veinRow < 0 || veinRow >= size) break;
                if (fleshWeightMap[veinRow * size + veinColumn] > 0.34f) {
                    float taper = 1f - (float) stepIndex / veinLife;     // thick root → thin tip
                    pixmap.setColor(0.11f, 0.01f, 0.02f, 1f);            // dark vessel body
                    if (taper > 0.6f) pixmap.fillCircle(veinColumn, veinRow, 1);
                    else              pixmap.drawPixel(veinColumn, veinRow);
                    pixmap.setColor(0.58f + taper * 0.12f, 0.08f, 0.10f, 1f);  // crimson core
                    pixmap.drawPixel(veinColumn, veinRow);
                    pixmap.setColor(0.95f, 0.45f, 0.46f, 0.5f);          // glistening highlight
                    int highlightColumn = veinColumn - Math.round((float) Math.sin(angleRadians));
                    int highlightRow    = veinRow    + Math.round((float) Math.cos(angleRadians));
                    if (highlightColumn >= 0 && highlightColumn < size
                            && highlightRow >= 0 && highlightRow < size) {
                        pixmap.drawPixel(highlightColumn, highlightRow);
                    }
                }
                angleRadians += driftRadians + (random.nextFloat() - 0.5f) * 0.30f;
                positionX += (float) Math.cos(angleRadians);
                positionY += (float) Math.sin(angleRadians);
            }
        }

        // ── Open gashes: parted slits with glistening interior + a blood drip ──
        for (int gashIndex = 0; gashIndex < GORE_GASH_COUNT; gashIndex++) {
            int gashColumn = 20;
            int gashRow    = 20;
            int placementTries = 0;
            do {
                gashColumn = 20 + random.nextInt(size - 40);
                gashRow    = 20 + random.nextInt(size - 90);
                placementTries++;
            } while (fleshWeightMap[gashRow * size + gashColumn] < 0.4f && placementTries < 40);
            if (fleshWeightMap[gashRow * size + gashColumn] < 0.4f) continue;

            int gashLength = 18 + random.nextInt(34);
            int gashCurve  = random.nextInt(7) - 3;
            for (int stepIndex = 0; stepIndex < gashLength; stepIndex++) {
                int woundRow = gashRow + stepIndex;
                if (woundRow >= size) break;
                int woundColumn = gashColumn + (int) Math.round(Math.sin(stepIndex * 0.2) * gashCurve);
                int halfWidth   = Math.max(1, (int) Math.round(
                        Math.sin((float) stepIndex / gashLength * Math.PI) * 4));
                pixmap.setColor(0.62f, 0.20f, 0.18f, 1f);                 // parted lip rims
                if (woundColumn - halfWidth - 1 >= 0)   pixmap.drawPixel(woundColumn - halfWidth - 1, woundRow);
                if (woundColumn + halfWidth + 1 < size) pixmap.drawPixel(woundColumn + halfWidth + 1, woundRow);
                pixmap.setColor(0.10f, 0.0f, 0.01f, 1f);                  // dark wound cavity
                pixmap.fillRectangle(woundColumn - halfWidth, woundRow, halfWidth * 2, 1);
                pixmap.setColor(0.85f, 0.18f, 0.20f, 1f);                 // glistening interior
                pixmap.drawPixel(woundColumn, woundRow);
            }
            // Blood drip from the base of the gash.
            int dripColumn = gashColumn;
            int dripRow    = gashRow + gashLength;
            int dripLength = 10 + random.nextInt(26);
            for (int stepIndex = 0; stepIndex < dripLength; stepIndex++) {
                int pixelRow = dripRow + stepIndex;
                if (pixelRow >= size) break;
                float fade = 1f - (float) stepIndex / dripLength;
                pixmap.setColor(0.32f * fade + 0.04f, 0.01f, 0.02f, 1f);
                pixmap.drawPixel(dripColumn, pixelRow);
                if (stepIndex % 5 == 0) dripColumn += random.nextInt(3) - 1;
            }
        }

        // ── Pustules / boils: domed nodules, some sickly-yellow (infected) ──
        for (int pustuleIndex = 0; pustuleIndex < GORE_PUSTULE_COUNT; pustuleIndex++) {
            int pustuleColumn = random.nextInt(size);
            int pustuleRow    = random.nextInt(size);
            if (fleshWeightMap[pustuleRow * size + pustuleColumn] < 0.5f) continue;
            int pustuleRadius = 3 + random.nextInt(5);
            boolean infected  = random.nextFloat() < 0.35f;
            pixmap.setColor(0.10f, 0.01f, 0.02f, 1f);                     // dark base ring
            pixmap.fillCircle(pustuleColumn, pustuleRow, pustuleRadius + 1);
            for (int offsetY = -pustuleRadius; offsetY <= pustuleRadius; offsetY++) {
                for (int offsetX = -pustuleRadius; offsetX <= pustuleRadius; offsetX++) {
                    int pixelColumn = pustuleColumn + offsetX;
                    int pixelRow    = pustuleRow    + offsetY;
                    if (pixelColumn < 0 || pixelColumn >= size || pixelRow < 0 || pixelRow >= size) continue;
                    float distance = (float) Math.sqrt(offsetX * offsetX + offsetY * offsetY);
                    if (distance > pustuleRadius) continue;
                    float dome = 1f - distance / pustuleRadius;
                    float lit  = clampUnit(dome + (-offsetX - offsetY) / (float)(2 * pustuleRadius) * 0.7f);
                    float red, green, blue;
                    if (infected) { red = 0.55f + lit * 0.40f; green = 0.42f + lit * 0.45f; blue = 0.10f + lit * 0.20f; }
                    else          { red = 0.40f + lit * 0.50f; green = 0.06f + lit * 0.30f; blue = 0.07f + lit * 0.24f; }
                    pixmap.setColor(clampUnit(red), clampUnit(green), clampUnit(blue), 1f);
                    pixmap.drawPixel(pixelColumn, pixelRow);
                }
            }
            pixmap.setColor(1f, 0.85f, 0.82f, 0.9f);                      // wet specular dot
            if (pustuleColumn - pustuleRadius / 2 >= 0 && pustuleRow - pustuleRadius / 2 >= 0) {
                pixmap.drawPixel(pustuleColumn - pustuleRadius / 2, pustuleRow - pustuleRadius / 2);
            }
        }

        // ── Bone / teeth nodules pushing through the meat ──
        for (int boneIndex = 0; boneIndex < GORE_BONE_COUNT; boneIndex++) {
            int boneColumn = random.nextInt(size);
            int boneRow    = random.nextInt(size);
            if (fleshWeightMap[boneRow * size + boneColumn] < 0.6f) continue;
            int boneRadius = 2 + random.nextInt(3);
            pixmap.setColor(0.14f, 0.02f, 0.03f, 1f);                     // socket shadow
            pixmap.fillCircle(boneColumn, boneRow, boneRadius + 1);
            pixmap.setColor(0.86f, 0.82f, 0.70f, 1f);                     // bone body
            pixmap.fillCircle(boneColumn, boneRow, boneRadius);
            pixmap.setColor(0.96f, 0.94f, 0.86f, 1f);                     // bright tip
            if (boneColumn - 1 >= 0 && boneRow - 1 >= 0) pixmap.drawPixel(boneColumn - 1, boneRow - 1);
        }

        // ── Pores / follicles ──
        for (int poreIndex = 0; poreIndex < GORE_PORE_COUNT; poreIndex++) {
            int poreColumn = random.nextInt(size);
            int poreRow    = random.nextInt(size);
            if (fleshWeightMap[poreRow * size + poreColumn] > GORE_FLESH_THRESHOLD) {
                pixmap.setColor(0.06f, 0.0f, 0.01f, 1f);
                pixmap.drawPixel(poreColumn, poreRow);
            }
        }

        // ── Wet glints across the high flesh ──
        for (int glintIndex = 0; glintIndex < GORE_GLINT_COUNT; glintIndex++) {
            int glintColumn = random.nextInt(size);
            int glintRow    = random.nextInt(size);
            if (fleshWeightMap[glintRow * size + glintColumn] > 0.55f) {
                pixmap.setColor(0.98f, 0.62f, 0.60f, 0.8f);
                pixmap.drawPixel(glintColumn, glintRow);
            }
        }

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    /**
     * Generates a heavy armored bulkhead plate texture.
     * Gunmetal blue-grey field with a beveled recessed center panel, a dense bolt grid
     * around the inner frame, a hydraulic door seam splitting the panel, and a faded
     * stencil chevron hull-section marker.
     */
    private static Texture generateBulkheadWallTexture() {
        int    size   = BULKHEAD_WALL_TEXTURE_SIZE;
        int    border = BULKHEAD_FRAME_WIDTH;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        // Gunmetal base with fine grain
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                float grain = ((row * 7 + column * 11) % 13 - 6) / 150f;
                pixmap.setColor(Math.max(0f, Math.min(1f, 0.30f + grain)),
                                Math.max(0f, Math.min(1f, 0.33f + grain)),
                                Math.max(0f, Math.min(1f, 0.39f + grain)), 1f);
                pixmap.drawPixel(column, row);
            }
        }

        // Recessed center panel (darker)
        pixmap.setColor(0.24f, 0.27f, 0.33f, 1f);
        pixmap.fillRectangle(border, border, size - 2 * border, size - 2 * border);

        // Bevel: top-left lit edge (raised lip), bottom-right shadow edge
        pixmap.setColor(0.46f, 0.50f, 0.57f, 1f);
        pixmap.fillRectangle(border - 3, border - 3, size - 2 * border + 6, 3); // top highlight
        pixmap.fillRectangle(border - 3, border - 3, 3, size - 2 * border + 6); // left highlight
        pixmap.setColor(0.13f, 0.15f, 0.19f, 1f);
        pixmap.fillRectangle(border - 3, size - border, size - 2 * border + 6, 3); // bottom shadow
        pixmap.fillRectangle(size - border, border - 3, 3, size - 2 * border + 6); // right shadow

        // Bolt grid: shadow offset then lit head
        int boltSpacing = BULKHEAD_BOLT_SPACING;
        // Top and bottom rows
        for (int boltX = border + boltSpacing / 2; boltX < size - border; boltX += boltSpacing) {
            pixmap.setColor(0.08f, 0.09f, 0.11f, 1f);
            pixmap.fillRectangle(boltX + 2, border + 4, 5, 5);
            pixmap.fillRectangle(boltX + 2, size - border - 8, 5, 5);
            pixmap.setColor(0.58f, 0.62f, 0.68f, 1f);
            pixmap.fillRectangle(boltX, border + 2, 5, 5);
            pixmap.fillRectangle(boltX, size - border - 10, 5, 5);
        }
        // Left and right columns
        for (int boltY = border + boltSpacing / 2; boltY < size - border; boltY += boltSpacing) {
            pixmap.setColor(0.08f, 0.09f, 0.11f, 1f);
            pixmap.fillRectangle(border + 4, boltY + 2, 5, 5);
            pixmap.fillRectangle(size - border - 8, boltY + 2, 5, 5);
            pixmap.setColor(0.58f, 0.62f, 0.68f, 1f);
            pixmap.fillRectangle(border + 2, boltY, 5, 5);
            pixmap.fillRectangle(size - border - 10, boltY, 5, 5);
        }

        // Hydraulic seam at center of panel
        pixmap.setColor(0.06f, 0.07f, 0.09f, 1f);
        pixmap.fillRectangle(border, size / 2, size - 2 * border, 3);
        pixmap.setColor(0.46f, 0.50f, 0.57f, 1f);
        pixmap.fillRectangle(border, size / 2 - 2, size - 2 * border, 2); // highlight above seam

        // Faded stencil chevron in top-right of center panel
        int stencilX = size - border - 36;
        int stencilY = border + 12;
        // Blend stencil color (70% yellow-gold, 30% panel dark)
        pixmap.setColor(0.70f * 0.70f + 0.24f * 0.30f, 0.66f * 0.70f + 0.27f * 0.30f,
                        0.30f * 0.70f + 0.33f * 0.30f, 1f);
        for (int chevronRow = 0; chevronRow < 14; chevronRow++) {
            int indent = chevronRow < 7 ? chevronRow : 13 - chevronRow;
            pixmap.fillRectangle(stencilX + indent * 2, stencilY + chevronRow, 2, 1);
            if (indent > 0) pixmap.fillRectangle(stencilX + (indent - 1) * 2, stencilY + chevronRow, 2, 1);
        }

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    /**
     * Generates a reinforced containment glass wall texture ('N').
     * Steel frame base with a tinted inner glass pane, ghost silhouette smears,
     * diagonal sheen streaks, a spiderweb crack from an off-center impact point,
     * and a beveled frame border.
     */
    private static Texture generateGlassWallTexture() {
        int    size   = GLASS_WALL_TEXTURE_SIZE;
        Random random = new Random(GLASS_WALL_SEED);
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        // Layer 1: Steel frame base with fine grain
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                float grain = ((row * 7 + column * 11) % 13 - 6) / 150f;
                pixmap.setColor(Math.max(0f, Math.min(1f, 0.28f + grain)),
                                Math.max(0f, Math.min(1f, 0.31f + grain)),
                                Math.max(0f, Math.min(1f, 0.36f + grain)), 1f);
                pixmap.drawPixel(column, row);
            }
        }

        // Layer 2: Inner glass pane — tinted dark blue-green body
        pixmap.setColor(0.07f, 0.13f, 0.15f, 1f);
        pixmap.fillRectangle(28, 28, 200, 200);

        // Layer 3: Ghost silhouette blobs — soft radial smears inside the pane
        for (int blobIndex = 0; blobIndex < GLASS_GHOST_BLOB_COUNT; blobIndex++) {
            int blobCenterColumn = 68 + blobIndex * 44;
            int blobCenterRow    = 80 + blobIndex * 28;
            int blobRadius       = 36;
            for (int pixelRow = blobCenterRow - blobRadius; pixelRow <= blobCenterRow + blobRadius; pixelRow++) {
                for (int pixelColumn = blobCenterColumn - blobRadius; pixelColumn <= blobCenterColumn + blobRadius; pixelColumn++) {
                    if (pixelColumn < 28 || pixelColumn >= 228 || pixelRow < 28 || pixelRow >= 228) continue;
                    float differenceX = pixelColumn - blobCenterColumn;
                    float differenceY = pixelRow    - blobCenterRow;
                    float distance    = (float) Math.sqrt(differenceX * differenceX + differenceY * differenceY);
                    float weight      = GameMath.radialFalloff(distance, blobRadius);
                    if (weight <= 0f) continue;
                    // Read current pixel and blend ghost color on top
                    float blendedRed   = 0.07f + weight * (0.20f - 0.07f);
                    float blendedGreen = 0.13f + weight * (0.24f - 0.13f);
                    float blendedBlue  = 0.15f + weight * (0.22f - 0.15f);
                    pixmap.setColor(Math.min(1f, blendedRed),
                                    Math.min(1f, blendedGreen),
                                    Math.min(1f, blendedBlue), 1f);
                    pixmap.drawPixel(pixelColumn, pixelRow);
                }
            }
        }

        // Layer 4: Two diagonal sheen streaks across the pane (top-left toward bottom-right)
        pixmap.setColor(0.55f, 0.72f, 0.74f, 1f);
        for (int streakIndex = 0; streakIndex < GLASS_SHEEN_STREAK_COUNT; streakIndex++) {
            int streakOffset = 30 + streakIndex * 60;
            for (int stepIndex = 0; stepIndex < 180; stepIndex++) {
                int pixelColumn = 28 + streakOffset + stepIndex;
                int pixelRow    = 28 + stepIndex;
                if (pixelColumn >= 228 || pixelRow >= 228) break;
                pixmap.drawPixel(pixelColumn, pixelRow);
            }
        }

        // Layer 5: Spiderweb crack from impact point at (170, 90)
        int impactColumn = 170;
        int impactRow    = 90;
        for (int branchIndex = 0; branchIndex < GLASS_CRACK_BRANCH_COUNT; branchIndex++) {
            int   crackColumn      = impactColumn;
            int   crackRow         = impactRow;
            float crackAngleRadians = (float)(Math.PI * 2) * branchIndex / GLASS_CRACK_BRANCH_COUNT
                                      + (random.nextFloat() - 0.5f) * 0.6f;
            for (int stepIndex = 0; stepIndex < GLASS_CRACK_STEPS; stepIndex++) {
                if (crackColumn < 28 || crackColumn >= 228 || crackRow < 28 || crackRow >= 228) break;
                // 1px shadow offset in darker color
                if (crackColumn + 1 < size && crackRow + 1 < size) {
                    pixmap.setColor(0.02f, 0.05f, 0.06f, 1f);
                    pixmap.drawPixel(crackColumn + 1, crackRow + 1);
                }
                // 2px crack core
                pixmap.setColor(0.78f, 0.85f, 0.88f, 1f);
                pixmap.drawPixel(crackColumn, crackRow);
                if (crackColumn + 1 < size) pixmap.drawPixel(crackColumn + 1, crackRow);
                crackAngleRadians += (random.nextFloat() - 0.5f) * 0.6f;
                crackColumn       += (int) Math.round(Math.cos(crackAngleRadians));
                crackRow          += (int) Math.round(Math.sin(crackAngleRadians));
            }
        }

        // Layer 6: Beveled frame border
        // Top and left edges — lit highlight
        pixmap.setColor(0.46f, 0.50f, 0.57f, 1f);
        pixmap.fillRectangle(28, 28, 200, 3);
        pixmap.fillRectangle(28, 28, 3, 200);
        // Bottom and right edges — shadow
        pixmap.setColor(0.12f, 0.14f, 0.18f, 1f);
        pixmap.fillRectangle(28, 225, 200, 3);
        pixmap.fillRectangle(225, 28, 3, 200);

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    /**
     * Generates a bio-containment / quarantine wall texture ('Q').
     * White panel base with horizontal seams, a diagonal black-and-green warning stripe band
     * across the lower third, an approximate biohazard trefoil stencil, and a green glow line.
     */
    private static Texture generateBioWallTexture() {
        int    size   = BIO_WALL_TEXTURE_SIZE;
        Random random = new Random(BIO_WALL_SEED);
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        // Layer 1: White panel base with fine grain
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                float grain = ((row * 7 + column * 11) % 13 - 6) / 150f;
                pixmap.setColor(Math.max(0f, Math.min(1f, 0.74f + grain)),
                                Math.max(0f, Math.min(1f, 0.77f + grain)),
                                Math.max(0f, Math.min(1f, 0.74f + grain)), 1f);
                pixmap.drawPixel(column, row);
            }
        }

        // Layer 2: Two horizontal panel seams at 1/3 and 2/3 height
        pixmap.setColor(0.40f, 0.44f, 0.41f, 1f);
        pixmap.fillRectangle(0, size / 3, size, 2);
        pixmap.fillRectangle(0, 2 * size / 3, size, 2);

        // Layer 3: Diagonal warning stripe band across rows 170..230
        for (int row = 170; row <= 230 && row < size; row++) {
            for (int column = 0; column < size; column++) {
                int stripePhase = ((row + column) % BIO_STRIPE_PITCH + BIO_STRIPE_PITCH) % BIO_STRIPE_PITCH;
                if (stripePhase < BIO_STRIPE_PITCH / 2) {
                    // Black stripe
                    pixmap.setColor(0.06f, 0.07f, 0.06f, 1f);
                } else {
                    // Green stripe
                    pixmap.setColor(0.28f, 0.78f, 0.30f, 1f);
                }
                pixmap.drawPixel(column, row);
            }
        }

        // Layer 4: Biohazard trefoil approximation at center (96, 100)
        // Three overlapping rectangular blocks rotated 0°, 120°, 240° (approximated as
        // axis-aligned + diagonal fills). Center disc first.
        int trefoilCenterColumn = 96;
        int trefoilCenterRow    = 100;
        // Mix 80% of (0.10, 0.10, 0.10) with 20% of panel white (0.74, 0.77, 0.74)
        float trefoilRed   = 0.10f * 0.80f + 0.74f * 0.20f;
        float trefoilGreen = 0.10f * 0.80f + 0.77f * 0.20f;
        float trefoilBlue  = 0.10f * 0.80f + 0.74f * 0.20f;
        pixmap.setColor(trefoilRed, trefoilGreen, trefoilBlue, 1f);
        // Center disc (radius 8)
        pixmap.fillRectangle(trefoilCenterColumn - 8, trefoilCenterRow - 8, 16, 16);
        // Lobe 0: upward arc block
        pixmap.fillRectangle(trefoilCenterColumn - 8, trefoilCenterRow - 28, 16, 16);
        // Lobe 1: lower-right arc block (approx 120° rotation)
        pixmap.fillRectangle(trefoilCenterColumn + 8, trefoilCenterRow + 4, 16, 16);
        // Lobe 2: lower-left arc block (approx 240° rotation)
        pixmap.fillRectangle(trefoilCenterColumn - 24, trefoilCenterRow + 4, 16, 16);
        // Gap ring between lobes and center — redraw center disc in panel white to punch gap
        pixmap.setColor(0.74f, 0.77f, 0.74f, 1f);
        pixmap.fillRectangle(trefoilCenterColumn - 4, trefoilCenterRow - 4, 8, 8);

        // Layer 5: green glow line just below the stripe band at row 230
        pixmap.setColor(0.18f, 0.55f, 0.22f, 1f);
        if (230 < size) {
            pixmap.fillRectangle(0, 230, size, 2);
        }

        // Suppress unused variable warning — random is seeded for determinism but
        // this texture's layers are position-driven rather than random-walk driven.
        random.nextInt();

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    /**
     * Generates an emergency lighting strip wall texture ('S').
     * Dark steel base with a vertical red glow gradient centred on a recessed channel,
     * a bright red strip core with white-hot centre line, and evenly spaced cage housings.
     */
    private static Texture generateEmergWallTexture() {
        int    size   = EMERG_WALL_TEXTURE_SIZE;
        Random random = new Random(EMERG_WALL_SEED);
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        // Layer 1: Dark steel base with grain, then Layer 2: vertical glow gradient applied per pixel
        int stripCenterRow = 116;
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                float grain    = ((row * 7 + column * 11) % 13 - 6) / 150f;
                float steelRed   = Math.max(0f, Math.min(1f, 0.20f + grain));
                float steelGreen = Math.max(0f, Math.min(1f, 0.21f + grain));
                float steelBlue  = Math.max(0f, Math.min(1f, 0.24f + grain));
                float distanceFromStrip = Math.abs(row - stripCenterRow);
                float glowWeight = Math.max(0f, 1f - distanceFromStrip / 44f);
                float red   = steelRed   + glowWeight * (0.55f - steelRed);
                float green = steelGreen * (1f - glowWeight * 0.85f);
                float blue  = steelBlue  * (1f - glowWeight * 0.95f);
                pixmap.setColor(Math.max(0f, Math.min(1f, red)),
                                Math.max(0f, Math.min(1f, green)),
                                Math.max(0f, Math.min(1f, blue)), 1f);
                pixmap.drawPixel(column, row);
            }
        }

        // Layer 3: Recessed channel
        pixmap.setColor(0.08f, 0.06f, 0.06f, 1f);
        pixmap.fillRectangle(0, 110, size, 6);

        // Layer 4: Red strip core and white-hot centre line
        pixmap.setColor(0.95f, 0.18f, 0.16f, 1f);
        pixmap.fillRectangle(0, 114, size, 4);
        pixmap.setColor(1.00f, 0.70f, 0.70f, 1f);
        for (int column = 0; column < size; column++) {
            pixmap.drawPixel(column, 116);
            pixmap.drawPixel(column, 117);
        }

        // Layer 5: Cage housings every (size / EMERG_CAGE_COUNT) columns
        int cageSpacing = size / EMERG_CAGE_COUNT;
        for (int cageIndex = 0; cageIndex < EMERG_CAGE_COUNT; cageIndex++) {
            int cageCenterColumn = cageIndex * cageSpacing + cageSpacing / 2;
            pixmap.setColor(0.10f, 0.10f, 0.11f, 1f);
            // Crosshatch of pixels centred on the strip row
            for (int offsetRow = -4; offsetRow <= 2; offsetRow++) {
                for (int offsetColumn = -4; offsetColumn <= 4; offsetColumn++) {
                    int pixelColumn = cageCenterColumn + offsetColumn;
                    int pixelRow    = stripCenterRow   + offsetRow;
                    if (pixelColumn >= 0 && pixelColumn < size && pixelRow >= 0 && pixelRow < size) {
                        pixmap.drawPixel(pixelColumn, pixelRow);
                    }
                }
            }
        }

        // Suppress unused variable warning — seeded for determinism
        random.nextInt();

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    /**
     * Generates a medical tile wall texture ('M').
     * Checkerboard white/off-white tiles with 1px grout borders, a green accent stripe,
     * a red medical cross on a white background, and scattered blood flecks.
     */
    private static Texture generateMedWallTexture() {
        int    size   = MED_WALL_TEXTURE_SIZE;
        Random random = new Random(MED_WALL_SEED);
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        // Layer 1: Tile grid — checker pattern with 1px grout at tile boundaries
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                boolean isGrout = (row % MED_TILE_SIZE == 0) || (column % MED_TILE_SIZE == 0);
                if (isGrout) {
                    pixmap.setColor(0.34f, 0.36f, 0.36f, 1f);
                } else {
                    int tileIndexColumn = column / MED_TILE_SIZE;
                    int tileIndexRow    = row    / MED_TILE_SIZE;
                    boolean isWhiteTile = ((tileIndexColumn + tileIndexRow) & 1) == 0;
                    if (isWhiteTile) {
                        pixmap.setColor(0.82f, 0.84f, 0.83f, 1f);
                    } else {
                        pixmap.setColor(0.72f, 0.74f, 0.73f, 1f);
                    }
                }
                pixmap.drawPixel(column, row);
            }
        }

        // Layer 2: Green accent stripe at row 152, 6px tall
        pixmap.setColor(0.20f, 0.62f, 0.45f, 1f);
        pixmap.fillRectangle(0, 152, size, 6);

        // Layer 3: Medical cross at approx (120, 56) — white 40×40 backing, red cross bars
        int crossCenterColumn = 120;
        int crossCenterRow    = 56;
        // White background square
        pixmap.setColor(0.90f, 0.92f, 0.90f, 1f);
        pixmap.fillRectangle(crossCenterColumn - 20, crossCenterRow - 20, 40, 40);
        // Red cross — 16×40 vertical bar and 40×16 horizontal bar
        pixmap.setColor(0.80f, 0.16f, 0.16f, 1f);
        pixmap.fillRectangle(crossCenterColumn - 8,  crossCenterRow - 20, 16, 40);
        pixmap.fillRectangle(crossCenterColumn - 20, crossCenterRow - 8,  40, 16);

        // Layer 4: Blood flecks scattered in the lower half
        for (int fleckIndex = 0; fleckIndex < MED_BLOOD_FLECK_COUNT; fleckIndex++) {
            int fleckColumn = random.nextInt(size);
            int fleckRow    = size / 2 + random.nextInt(size / 2);
            int fleckSize   = 2 + random.nextInt(3); // 2 to 4 pixels
            pixmap.setColor(0.36f, 0.05f, 0.05f, 1f);
            pixmap.fillRectangle(fleckColumn, fleckRow, fleckSize, fleckSize);
        }

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    /**
     * Generates a cryo / frost-damaged wall texture ('Z').
     * Cold steel base with a radial + edge frost rime overlay, a horizontal coolant pipe seam
     * with hanging icicles, thin frost fracture lines, and scattered ice glints.
     */
    private static Texture generateCryoWallTexture() {
        int    size   = CRYO_WALL_TEXTURE_SIZE;
        Random random = new Random(CRYO_WALL_SEED);
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        // Pre-compute frost weight map: radialFalloff from 4 corners + bottom-edge gradient
        float[] frostWeightMap = new float[size * size];
        int[][] cornerCenters = { {0, 0}, {size - 1, 0}, {0, size - 1}, {size - 1, size - 1} };
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                float cornerWeight = 0f;
                for (int[] corner : cornerCenters) {
                    float differenceX = column - corner[0];
                    float differenceY = row    - corner[1];
                    float distance    = (float) Math.sqrt(differenceX * differenceX + differenceY * differenceY);
                    cornerWeight = Math.max(cornerWeight,
                                            GameMath.radialFalloff(distance, CRYO_FROST_CORNER_RADIUS));
                }
                float edgeGradient = (float) row / (size - 1) * 0.8f;
                frostWeightMap[row * size + column] = Math.min(1f, cornerWeight + edgeGradient);
            }
        }

        // Layer 1: Cold steel base with grain, Layer 2: frost rime overlay blended in per pixel
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                float grain      = ((row * 7 + column * 11) % 13 - 6) / 150f;
                float steelRed   = Math.max(0f, Math.min(1f, 0.34f + grain));
                float steelGreen = Math.max(0f, Math.min(1f, 0.40f + grain));
                float steelBlue  = Math.max(0f, Math.min(1f, 0.46f + grain));
                float frostWeight = frostWeightMap[row * size + column];
                // Two-stop lerp: steel -> frost blue -> near-white
                float midRed   = 0.62f; float midGreen = 0.76f; float midBlue = 0.84f;
                float highRed  = 0.86f; float highGreen = 0.93f; float highBlue = 0.97f;
                float red, green, blue;
                if (frostWeight < 0.5f) {
                    float t = frostWeight * 2f;
                    red   = steelRed   + t * (midRed   - steelRed);
                    green = steelGreen + t * (midGreen - steelGreen);
                    blue  = steelBlue  + t * (midBlue  - steelBlue);
                } else {
                    float t = (frostWeight - 0.5f) * 2f;
                    red   = midRed   + t * (highRed   - midRed);
                    green = midGreen + t * (highGreen - midGreen);
                    blue  = midBlue  + t * (highBlue  - midBlue);
                }
                pixmap.setColor(Math.max(0f, Math.min(1f, red)),
                                Math.max(0f, Math.min(1f, green)),
                                Math.max(0f, Math.min(1f, blue)), 1f);
                pixmap.drawPixel(column, row);
            }
        }

        // Layer 3: Horizontal coolant pipe seam at row 102
        pixmap.setColor(0.24f, 0.28f, 0.33f, 1f);
        pixmap.fillRectangle(0, 102, size, 4);
        pixmap.setColor(0.50f, 0.65f, 0.78f, 1f);
        pixmap.fillRectangle(0, 100, size, 2); // highlight above seam

        // Layer 4: Icicles hanging from the pipe seam
        int[] icicleColumns = new int[CRYO_ICICLE_COUNT];
        for (int icicleIndex = 0; icicleIndex < CRYO_ICICLE_COUNT; icicleIndex++) {
            icicleColumns[icicleIndex] = 10 + icicleIndex * (size / CRYO_ICICLE_COUNT);
        }
        for (int icicleIndex = 0; icicleIndex < CRYO_ICICLE_COUNT; icicleIndex++) {
            int icicleLength  = 16 + random.nextInt(14); // 16..29 rows
            int icicleBaseCol = icicleColumns[icicleIndex];
            for (int stepIndex = 0; stepIndex < icicleLength; stepIndex++) {
                int pixelRow    = 106 + stepIndex;
                int halfWidth   = stepIndex / 2;
                if (pixelRow >= size) break;
                // Fade from icy blue at base to lighter tip
                float tipFraction = (float) stepIndex / icicleLength;
                float icicleRed   = 0.50f + tipFraction * 0.30f;
                float icicleGreen = 0.70f + tipFraction * 0.20f;
                float icicleBlue  = 0.82f + tipFraction * 0.12f;
                pixmap.setColor(Math.min(1f, icicleRed),
                                Math.min(1f, icicleGreen),
                                Math.min(1f, icicleBlue), 1f);
                for (int offsetColumn = -halfWidth; offsetColumn <= halfWidth; offsetColumn++) {
                    int pixelColumn = icicleBaseCol + offsetColumn;
                    if (pixelColumn >= 0 && pixelColumn < size) {
                        pixmap.drawPixel(pixelColumn, pixelRow);
                    }
                }
            }
        }

        // Layer 5: Frost fracture lines — thin random-walk lines in frost zones
        for (int fractureIndex = 0; fractureIndex < CRYO_FRACTURE_COUNT; fractureIndex++) {
            // Start from a frost-heavy area (corners / bottom)
            int   crackColumn      = random.nextInt(size / 3) + (fractureIndex * size / CRYO_FRACTURE_COUNT);
            int   crackRow         = size - 1 - random.nextInt(size / 4);
            float crackAngleRadians = -(float)(Math.PI / 2) + (random.nextFloat() - 0.5f) * 1.2f;
            for (int stepIndex = 0; stepIndex < 50; stepIndex++) {
                if (crackColumn < 0 || crackColumn >= size || crackRow < 0 || crackRow >= size) break;
                if (frostWeightMap[crackRow * size + crackColumn] > 0.25f) {
                    pixmap.setColor(0.50f, 0.70f, 0.82f, 1f);
                    pixmap.drawPixel(crackColumn, crackRow);
                }
                crackAngleRadians += (random.nextFloat() - 0.5f) * 0.5f;
                crackColumn       += (int) Math.round(Math.cos(crackAngleRadians));
                crackRow          += (int) Math.round(Math.sin(crackAngleRadians));
            }
        }

        // Layer 6: Ice glints — bright 1px specks in frost zones
        for (int glintIndex = 0; glintIndex < CRYO_GLINT_COUNT; glintIndex++) {
            int glintColumn = random.nextInt(size);
            int glintRow    = random.nextInt(size);
            if (frostWeightMap[glintRow * size + glintColumn] > 0.40f) {
                pixmap.setColor(0.95f, 0.98f, 1.00f, 1f);
                pixmap.drawPixel(glintColumn, glintRow);
            }
        }

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    /**
     * Generates a radiation-burned wall texture ('U').
     * Scorched black base with heavy grain, a radial heat halo from a hot-spot,
     * branching green-yellow crack network, and scattered radiation dust specks.
     */
    private static Texture generateRadWallTexture() {
        int    size     = RAD_WALL_TEXTURE_SIZE;
        Random random   = new Random(RAD_WALL_SEED);
        int    hotColumn = 170;
        int    hotRow    = 180;
        Pixmap pixmap   = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        // Layer 1: Scorched black base with heavy grain
        // Layer 2: Heat halo from hot-spot blended in per pixel
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                float grain = ((row * 7 + column * 11) % 13 - 6) / 60f;
                float baseRed   = Math.max(0f, Math.min(1f, 0.08f + grain));
                float baseGreen = Math.max(0f, Math.min(1f, 0.08f + grain));
                float baseBlue  = Math.max(0f, Math.min(1f, 0.07f + grain));
                float differenceX = column - hotColumn;
                float differenceY = row    - hotRow;
                float distance    = (float) Math.sqrt(differenceX * differenceX + differenceY * differenceY);
                float haloWeight  = GameMath.radialFalloff(distance, 100f);
                float red   = baseRed   + haloWeight * (0.22f - baseRed);
                float green = baseGreen + haloWeight * (0.16f - baseGreen);
                float blue  = baseBlue  + haloWeight * (0.08f - baseBlue);
                pixmap.setColor(Math.max(0f, Math.min(1f, red)),
                                Math.max(0f, Math.min(1f, green)),
                                Math.max(0f, Math.min(1f, blue)), 1f);
                pixmap.drawPixel(column, row);
            }
        }

        // Layer 3: Crack network — branching random-walk lines from near the hot-spot
        for (int crackIndex = 0; crackIndex < RAD_CRACK_COUNT; crackIndex++) {
            int   crackColumn      = hotColumn + random.nextInt(21) - 10;
            int   crackRow         = hotRow    + random.nextInt(21) - 10;
            float crackAngleRadians = (float)(Math.PI * 2) * crackIndex / RAD_CRACK_COUNT
                                      + (random.nextFloat() - 0.5f) * 0.4f;
            for (int stepIndex = 0; stepIndex < RAD_CRACK_STEPS; stepIndex++) {
                if (crackColumn < 0 || crackColumn >= size || crackRow < 0 || crackRow >= size) break;
                // 2px glow halo
                pixmap.setColor(0.40f, 0.62f, 0.14f, 1f);
                if (crackColumn + 1 < size) pixmap.drawPixel(crackColumn + 1, crackRow);
                if (crackRow    + 1 < size) pixmap.drawPixel(crackColumn, crackRow + 1);
                // 1px bright core
                pixmap.setColor(0.85f, 0.95f, 0.30f, 1f);
                pixmap.drawPixel(crackColumn, crackRow);
                crackAngleRadians += (random.nextFloat() - 0.5f) * 0.5f;
                crackColumn       += (int) Math.round(Math.cos(crackAngleRadians));
                crackRow          += (int) Math.round(Math.sin(crackAngleRadians));
            }
        }

        // Layer 4: Radiation dust — 1px specks weighted toward hot-spot
        for (int dustIndex = 0; dustIndex < RAD_DUST_COUNT; dustIndex++) {
            // Bias toward hot-spot: place within a range that skews toward (hotColumn, hotRow)
            int dustColumn = (hotColumn + random.nextInt(size)) % size;
            int dustRow    = (hotRow    + random.nextInt(size)) % size;
            pixmap.setColor(0.55f, 0.58f, 0.22f, 1f);
            pixmap.fillRectangle(dustColumn, dustRow, 2, 2);
        }

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    /**
     * Generates a blast-scarred wall texture ('X').
     * Lab steel base with soft scorch smear blobs, bullet pock marks (small discs with
     * highlight), larger blast craters with radial scorch streaks and crater rims,
     * and through-holes punched all the way to near-black.
     */
    private static Texture generateBlastWallTexture() {
        int    size   = BLAST_WALL_TEXTURE_SIZE;
        Random random = new Random(BLAST_WALL_SEED);
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        // Layer 1: Lab steel base with grain
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                float grain = ((row * 7 + column * 11) % 13 - 6) / 150f;
                pixmap.setColor(Math.max(0f, Math.min(1f, 0.27f + grain)),
                                Math.max(0f, Math.min(1f, 0.28f + grain)),
                                Math.max(0f, Math.min(1f, 0.31f + grain)), 1f);
                pixmap.drawPixel(column, row);
            }
        }

        // Layer 2: Scorch smear blobs — soft radialFalloff darkening
        int[] scorchCenterColumn = new int[BLAST_SCORCH_COUNT];
        int[] scorchCenterRow    = new int[BLAST_SCORCH_COUNT];
        int[] scorchRadius       = new int[BLAST_SCORCH_COUNT];
        for (int scorchIndex = 0; scorchIndex < BLAST_SCORCH_COUNT; scorchIndex++) {
            scorchCenterColumn[scorchIndex] = random.nextInt(size);
            scorchCenterRow[scorchIndex]    = random.nextInt(size);
            scorchRadius[scorchIndex]       = 30 + random.nextInt(22); // 30..51
        }
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                float maxScorchWeight = 0f;
                for (int scorchIndex = 0; scorchIndex < BLAST_SCORCH_COUNT; scorchIndex++) {
                    float differenceX = column - scorchCenterColumn[scorchIndex];
                    float differenceY = row    - scorchCenterRow[scorchIndex];
                    float distance    = (float) Math.sqrt(differenceX * differenceX + differenceY * differenceY);
                    maxScorchWeight   = Math.max(maxScorchWeight,
                                                  GameMath.radialFalloff(distance, scorchRadius[scorchIndex]));
                }
                if (maxScorchWeight > 0f) {
                    float grain = ((row * 7 + column * 11) % 13 - 6) / 150f;
                    float baseRed   = Math.max(0f, Math.min(1f, 0.27f + grain));
                    float baseGreen = Math.max(0f, Math.min(1f, 0.28f + grain));
                    float baseBlue  = Math.max(0f, Math.min(1f, 0.31f + grain));
                    float red   = baseRed   + maxScorchWeight * (0.12f - baseRed);
                    float green = baseGreen + maxScorchWeight * (0.10f - baseGreen);
                    float blue  = baseBlue  + maxScorchWeight * (0.09f - baseBlue);
                    pixmap.setColor(Math.max(0f, red), Math.max(0f, green), Math.max(0f, blue), 1f);
                    pixmap.drawPixel(column, row);
                }
            }
        }

        // Layer 3: Bullet pock marks — small 2-3px discs with top-left highlight
        for (int holeIndex = 0; holeIndex < BLAST_BULLET_HOLE_COUNT; holeIndex++) {
            int holeCenterColumn = random.nextInt(size);
            int holeCenterRow    = random.nextInt(size);
            int holeRadius       = 2 + random.nextInt(3); // radius 2..4 → disc 5..9 px across
            for (int pixelRow = holeCenterRow - holeRadius; pixelRow <= holeCenterRow + holeRadius; pixelRow++) {
                for (int pixelColumn = holeCenterColumn - holeRadius; pixelColumn <= holeCenterColumn + holeRadius; pixelColumn++) {
                    if (pixelColumn < 0 || pixelColumn >= size || pixelRow < 0 || pixelRow >= size) continue;
                    float differenceX = pixelColumn - holeCenterColumn;
                    float differenceY = pixelRow    - holeCenterRow;
                    if (differenceX * differenceX + differenceY * differenceY <= (float)(holeRadius * holeRadius)) {
                        pixmap.setColor(0.05f, 0.05f, 0.06f, 1f);
                        pixmap.drawPixel(pixelColumn, pixelRow);
                    }
                }
            }
            // 1px top-left highlight on the rim
            if (holeCenterColumn - holeRadius >= 0 && holeCenterRow - holeRadius >= 0) {
                pixmap.setColor(0.46f, 0.47f, 0.50f, 1f);
                pixmap.drawPixel(holeCenterColumn - holeRadius, holeCenterRow - holeRadius);
            }
        }

        // Layer 4: Blast craters — larger 8-14px discs with radial scorch streaks and crater rims
        for (int craterIndex = 0; craterIndex < BLAST_CRATER_COUNT; craterIndex++) {
            int craterCenterColumn = random.nextInt(size);
            int craterCenterRow    = random.nextInt(size);
            int craterRadius       = 8 + random.nextInt(8); // 8..15 → disc 16..30 px across
            int streakCount        = 4 + random.nextInt(3); // 4..6 radial streaks

            // Dark crater disc
            for (int pixelRow = craterCenterRow - craterRadius; pixelRow <= craterCenterRow + craterRadius; pixelRow++) {
                for (int pixelColumn = craterCenterColumn - craterRadius; pixelColumn <= craterCenterColumn + craterRadius; pixelColumn++) {
                    if (pixelColumn < 0 || pixelColumn >= size || pixelRow < 0 || pixelRow >= size) continue;
                    float differenceX = pixelColumn - craterCenterColumn;
                    float differenceY = pixelRow    - craterCenterRow;
                    if (differenceX * differenceX + differenceY * differenceY <= (float)(craterRadius * craterRadius)) {
                        pixmap.setColor(0.05f, 0.05f, 0.06f, 1f);
                        pixmap.drawPixel(pixelColumn, pixelRow);
                    }
                }
            }

            // Radial scorch streaks
            for (int streakIndex = 0; streakIndex < streakCount; streakIndex++) {
                float streakAngleRadians = (float)(Math.PI * 2) * streakIndex / streakCount;
                int   streakColumn       = craterCenterColumn;
                int   streakRow          = craterCenterRow;
                for (int stepIndex = 0; stepIndex < craterRadius + 16; stepIndex++) {
                    if (streakColumn < 0 || streakColumn >= size || streakRow < 0 || streakRow >= size) break;
                    pixmap.setColor(0.12f, 0.10f, 0.09f, 1f);
                    pixmap.fillRectangle(streakColumn, streakRow, 2, 2);
                    streakColumn += (int) Math.round(Math.cos(streakAngleRadians));
                    streakRow    += (int) Math.round(Math.sin(streakAngleRadians));
                }
            }

            // Crater rim circle (1px)
            pixmap.setColor(0.46f, 0.47f, 0.50f, 1f);
            for (int rimStep = 0; rimStep < 96; rimStep++) {
                float rimAngleRadians = (float)(Math.PI * 2) * rimStep / 96;
                int   rimColumn       = craterCenterColumn + (int) Math.round(craterRadius * Math.cos(rimAngleRadians));
                int   rimRow          = craterCenterRow    + (int) Math.round(craterRadius * Math.sin(rimAngleRadians));
                if (rimColumn >= 0 && rimColumn < size && rimRow >= 0 && rimRow < size) {
                    pixmap.drawPixel(rimColumn, rimRow);
                }
            }
        }

        // Layer 5: Through-holes — very small near-black 4px discs
        for (int throughIndex = 0; throughIndex < BLAST_THROUGH_HOLE_COUNT; throughIndex++) {
            int holeCenterColumn = random.nextInt(size);
            int holeCenterRow    = random.nextInt(size);
            int holeRadius       = 4; // 8px disc
            for (int pixelRow = holeCenterRow - holeRadius; pixelRow <= holeCenterRow + holeRadius; pixelRow++) {
                for (int pixelColumn = holeCenterColumn - holeRadius; pixelColumn <= holeCenterColumn + holeRadius; pixelColumn++) {
                    if (pixelColumn < 0 || pixelColumn >= size || pixelRow < 0 || pixelRow >= size) continue;
                    float differenceX = pixelColumn - holeCenterColumn;
                    float differenceY = pixelRow    - holeCenterRow;
                    if (differenceX * differenceX + differenceY * differenceY <= (float)(holeRadius * holeRadius)) {
                        pixmap.setColor(0.02f, 0.02f, 0.02f, 1f);
                        pixmap.drawPixel(pixelColumn, pixelRow);
                    }
                }
            }
        }

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
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

    public void setAlertPulse(float pulse) {
        this.alertPulse = pulse;
    }

    public void setLightingTime(float timeSeconds) {
        this.lightingTimeSeconds = timeSeconds;
    }

    /**
     * Supplies this level's realized texture set (symbol/sprite-reuse order-6/7) and PRE-RESOLVES the
     * per-level wall + column textures from it. Called once per level by World, right after the set is
     * built from the level's palette, on the GL thread BEFORE any render() — so the char-indexed arrays it
     * fills are safely published to the render workers (happens-before via the executor dispatch each
     * frame) and the hot per-column path keeps a single O(1) array read with no per-frame map lookup or
     * allocation. Resolution: symbol -> sprite id (level palette) -> Texture + intrinsic size (the set).
     */
    public void setEnvironmentTextureSet(EnvironmentTextureSet textureSet) {
        this.environmentTextureSet = textureSet;
        resolveLevelWallAndColumnTextures(textureSet);
    }

    /**
     * Fills the char-indexed wall arrays and the column fields from this level's palette + texture set.
     * Every non-wall slot defaults to the plain-wall texture so an unrecognised char can never NPE the
     * draw (the array is only indexed by chars for which {@link Level#isWall} is true, but the default
     * keeps the invariant the old static table held). With the legacy palette this reproduces the former
     * table entry-for-entry, so the look is unchanged.
     */
    private void resolveLevelWallAndColumnTextures(EnvironmentTextureSet textureSet) {
        LevelPalette palette = level.getPalette();

        // Plain-wall is a FIXED binding present in every palette (and therefore every realized set), so it
        // is always available as the NPE-safe default.
        Texture plainTexture = textureSet.textureFor(TilesetConstants.FIXED_WALL_SPRITE_ID);
        int     plainWidth   = textureSet.widthFor(TilesetConstants.FIXED_WALL_SPRITE_ID);
        int     plainHeight  = textureSet.heightFor(TilesetConstants.FIXED_WALL_SPRITE_ID);
        Arrays.fill(levelWallTextures, plainTexture);
        Arrays.fill(levelWallWidths,   plainWidth);
        Arrays.fill(levelWallHeights,  plainHeight);

        for (int charIndex = 0; charIndex < levelWallTextures.length; charIndex++) {
            char symbol = (char) charIndex;
            if (!Level.isWall(symbol)) continue;
            String spriteId = palette.spriteIdOf(symbol);
            if (spriteId == null) continue; // defensive: a wall char with no binding keeps the plain default
            levelWallTextures[charIndex] = textureSet.textureFor(spriteId);
            levelWallWidths[charIndex]   = textureSet.widthFor(spriteId);
            levelWallHeights[charIndex]  = textureSet.heightFor(spriteId);
        }

        // Column 'P' — resolves to the round OR (order-8) square column sprite for this level. 'P' is a
        // FLEXIBLE COLUMN symbol bound in every palette (legacy binds round; the allocator always fills
        // it), so spriteIdOf is non-null in practice; the null branch keeps the plain-wall default
        // (always present in the set) so a missing binding degrades gracefully instead of NPE-ing in the
        // hot render path — the same defence the wall loop above uses.
        String columnSpriteId = palette.spriteIdOf(COLUMN_SYMBOL);
        if (columnSpriteId == null) {
            levelColumnTexture       = plainTexture;
            levelColumnTextureWidth  = plainWidth;
            levelColumnTextureHeight = plainHeight;
        } else {
            levelColumnTexture       = textureSet.textureFor(columnSpriteId);
            levelColumnTextureWidth  = textureSet.widthFor(columnSpriteId);
            levelColumnTextureHeight = textureSet.heightFor(columnSpriteId);
        }
    }

    /** The current per-level texture set, or null before the first level is wired (order-6 plumbing). */
    public EnvironmentTextureSet getEnvironmentTextureSet() {
        return environmentTextureSet;
    }

    /** Returns the perpendicular wall distance (in tile units) for a given screen column. */
    public float getZBufferAt(int screenColumn) {
        return zBuffer[MathUtils.clamp(screenColumn, 0, WALL_PROJECTION_SCREEN_WIDTH - 1)];
    }

    /**
     * Returns the perpendicular wall distance for the given screen column without bounds checking.
     * Callers must guarantee screenColumn is in [0, WALL_PROJECTION_SCREEN_WIDTH).
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
            int columnsPerWorker = WALL_PROJECTION_SCREEN_WIDTH / workerCount;
            for (int workerIndex = 0; workerIndex < workers.length; workerIndex++) {
                int startColumn = workerIndex * columnsPerWorker;
                int endColumn   = startColumn + columnsPerWorker;
                workers[workerIndex].configure(startColumn, endColumn);
                workerPool.execute(workers[workerIndex]);
            }
            // Main thread handles the tail chunk (absorbs any remainder columns).
            int mainThreadStart = workers.length * columnsPerWorker;
            for (int screenColumn = mainThreadStart; screenColumn < WALL_PROJECTION_SCREEN_WIDTH; screenColumn++) {
                computeWallColumn(screenColumn);
            }
            // Arrive and block until all background workers have finished their ranges.
            phaser.arriveAndAwaitAdvance();
        } else {
            for (int screenColumn = 0; screenColumn < WALL_PROJECTION_SCREEN_WIDTH; screenColumn++) {
                computeWallColumn(screenColumn);
            }
        }

        // Phase 2 — GL-thread draw pass.
        // Reads the fully-populated columnResults[] and issues SpriteBatch draw calls.
        // All OpenGL state changes happen on the GL thread only.
        batch.begin();
        for (int screenColumn = 0; screenColumn < WALL_PROJECTION_SCREEN_WIDTH; screenColumn++) {
            WallColumnResult result = columnResults[screenColumn];
            if (!result.drawSurface && !result.drawDoorPanel) continue;

            if (result.drawSurface) {
                batch.setColor(result.surfaceRed, result.surfaceGreen, result.surfaceBlue, 1f);
                batch.draw(result.surfaceTexture,
                           screenColumn * WALL_COLUMN_WIDTH, result.surfaceDrawBottom,
                           WALL_COLUMN_WIDTH, result.surfaceDrawTop - result.surfaceDrawBottom,
                           result.surfaceTexColumn, result.surfaceTexSrcY,
                           1, result.surfaceTexSrcHeight,
                           false, false);
            }

            if (result.drawDoorPanel) {
                batch.setColor(result.doorPanelRed, result.doorPanelGreen, result.doorPanelBlue, 1f);
                batch.draw(result.doorPanelTexture,
                           screenColumn * WALL_COLUMN_WIDTH, result.doorPanelDrawBottom,
                           WALL_COLUMN_WIDTH, result.doorPanelDrawTop - result.doorPanelDrawBottom,
                           result.doorPanelTexColumn, result.doorPanelTexSrcY,
                           1, result.doorPanelTexSrcHeight,
                           false, false);
            }
        }
        // Reset to white so no alert tint leaks into other renderers that share GL state.
        batch.setColor(Color.WHITE);
        batch.end();
    }

    /**
     * Computes all render data for one screen column and writes it into columnResults[screenColumn].
     * Also writes the z-buffer value for the column. Safe to call from any thread since each
     * column index is owned by exactly one concurrent caller.
     */
    private void computeWallColumn(int screenColumn) {
        WallColumnResult result = columnResults[screenColumn];
        result.drawSurface   = false;
        result.drawDoorPanel = false;

        float playerTileX = cachedPlayerTileX;
        float playerTileY = cachedPlayerTileY;

        float cameraParameter = GameMath.cameraPlaneParameter(screenColumn, WALL_PROJECTION_SCREEN_WIDTH);
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
                int   columnTexColumn = GameMath.textureColumn(columnU, levelColumnTextureWidth);

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
                                       lineHeight, levelColumnTextureHeight);
                int texSrcHeight = GameMath.wallTextureClipSrcHeight(
                                       drawTop, drawBottom,
                                       lineHeight, levelColumnTextureHeight);
                texSrcHeight = Math.min(texSrcHeight, levelColumnTextureHeight - texSrcY);
                texSrcHeight = Math.max(1, texSrcHeight);

                result.drawSurface         = true;
                result.surfaceTexture      = levelColumnTexture;
                result.surfaceTexColumn    = columnTexColumn;
                result.surfaceTexSrcY      = texSrcY;
                result.surfaceTexSrcHeight = texSrcHeight;
                result.surfaceDrawBottom   = drawBottom;
                result.surfaceDrawTop      = drawTop;
                result.surfaceRed   = Math.min(1f, shade * (1f + alertPulse * ALERT_WALL_RED_BOOST));
                result.surfaceGreen = shade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
                result.surfaceBlue  = shade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
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

                // O(1) array lookup into this level's pre-resolved wall textures (order-7): the palette +
                // texture-set resolution happened once in setEnvironmentTextureSet(), so the hot path stays
                // a single array read per column.
                int     hitCharIndex          = hitWallCell & 0x7F;
                Texture selectedTexture       = levelWallTextures[hitCharIndex];
                int     selectedTextureWidth  = levelWallWidths[hitCharIndex];
                int     selectedTextureHeight = levelWallHeights[hitCharIndex];
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
                result.surfaceTexture      = selectedTexture;
                result.surfaceTexColumn    = wallTextureColumn;
                result.surfaceTexSrcY      = texSrcY;
                result.surfaceTexSrcHeight = texSrcHeight;
                result.surfaceDrawBottom   = drawBottom;
                result.surfaceDrawTop      = drawTop;
                result.surfaceRed   = Math.min(1f, shade * (1f + alertPulse * ALERT_WALL_RED_BOOST));
                result.surfaceGreen = shade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
                result.surfaceBlue  = shade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
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
                    Texture selectedDoorTexture   = doorTextureTable[doorHitCell & 0x7F];
                    int     selectedDoorTexWidth  = selectedDoorTexture.getWidth();
                    int     selectedDoorTexHeight = selectedDoorTexture.getHeight();

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
                    result.doorPanelTexture       = selectedDoorTexture;
                    result.doorPanelTexColumn     = doorTexColumn;
                    result.doorPanelTexSrcY       = doorTexSrcY;
                    result.doorPanelTexSrcHeight  = doorTexSrcHeight;
                    result.doorPanelDrawBottom    = clampedPanelBottom;
                    result.doorPanelDrawTop       = clampedPanelTop;
                    result.doorPanelRed   = Math.min(1f, shade * (1f + alertPulse * ALERT_WALL_RED_BOOST));
                    result.doorPanelGreen = shade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
                    result.doorPanelBlue  = shade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
                }
            }
        }
    }

    @Override
    public void dispose() {
        batch.dispose();
        // Wall + column textures are owned/disposed by the per-level EnvironmentTextureSet (World's dispose
        // chain), NOT here — order-7 moved that ownership to the set. Disposing them here would double-free.
        // WallRenderer still owns only the door textures and the 1×1 white pixel it builds itself.
        doorTexture.dispose();
        doorTextureRed.dispose();
        doorTextureYellow.dispose();
        doorTextureBlue.dispose();
        whitePixelTexture.dispose();
        if (workerPool != null) {
            workerPool.shutdown();
        }
    }

    /**
     * Generates a holo-data display wall texture ('D').
     * Near-black glass panel with glowing cyan data bars, a readout grid (mostly cyan, some amber
     * warnings), a subtle vertical scan band, and a thin steel bezel framing the panel edges.
     */
    private static Texture generateHoloDataWallTexture() {
        int    size   = HOLO_DATA_WALL_TEXTURE_SIZE;
        Random random = new Random(HOLO_DATA_WALL_SEED);
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        // Glass panel base
        pixmap.setColor(0.04f, 0.05f, 0.07f, 1f);
        pixmap.fill();

        // Subtle vertical scan band — one third from left, slightly brighter
        int scanBandLeft = size / 3;
        for (int column = scanBandLeft; column < scanBandLeft + size / 6; column++) {
            for (int row = 8; row < size - 8; row++) {
                pixmap.setColor(0.07f, 0.10f, 0.13f, 1f);
                pixmap.drawPixel(column, row);
            }
        }

        // Horizontal data bars (varying fill widths) — confined to the top region so the
        // readout grid below stays visible (the original full-height spacing pushed the grid
        // off-texture). Bars occupy the top ~40%, the grid fills the rest.
        int barHeight = 12;
        int barMargin = 12;
        int panelInnerWidth  = size - 2 * barMargin;
        int barRegionHeight  = panelInnerWidth * 2 / 5;
        int barSpacing       = barRegionHeight / HOLO_DATA_BAR_COUNT;
        for (int barIndex = 0; barIndex < HOLO_DATA_BAR_COUNT; barIndex++) {
            int barTop  = barMargin + barIndex * barSpacing;
            int fillWidth = (int)((0.40f + random.nextFloat() * 0.50f) * panelInnerWidth);
            // Dim background track
            pixmap.setColor(0.10f, 0.45f, 0.55f, 1f);
            pixmap.fillRectangle(barMargin, barTop, panelInnerWidth, barHeight);
            // Bright filled portion
            pixmap.setColor(0.35f, 0.95f, 1.00f, 1f);
            pixmap.fillRectangle(barMargin, barTop, fillWidth, barHeight);
        }

        // Readout grid — small squares below the bar region
        int gridTop   = barMargin + barRegionHeight + 12;
        int cellSize  = panelInnerWidth / HOLO_DATA_READOUT_COLUMNS;
        int cellInner = cellSize - 4;
        for (int gridRow = 0; gridRow < HOLO_DATA_READOUT_ROWS; gridRow++) {
            for (int gridColumn = 0; gridColumn < HOLO_DATA_READOUT_COLUMNS; gridColumn++) {
                int cellLeft = barMargin + gridColumn * cellSize;
                int cellTop  = gridTop  + gridRow    * cellSize;
                if (cellTop + cellInner >= size - barMargin) continue;
                boolean amber = random.nextFloat() < 0.10f;
                if (amber) {
                    pixmap.setColor(1.00f, 0.65f, 0.15f, 1f);
                } else {
                    boolean bright = random.nextFloat() < 0.60f;
                    if (bright) {
                        pixmap.setColor(0.35f, 0.95f, 1.00f, 1f);
                    } else {
                        pixmap.setColor(0.10f, 0.45f, 0.55f, 1f);
                    }
                }
                pixmap.fillRectangle(cellLeft + 1, cellTop + 1, cellInner, cellInner);
            }
        }

        // Steel bezel — 8px on all edges
        pixmap.setColor(0.18f, 0.20f, 0.24f, 1f);
        pixmap.fillRectangle(0,        0,        size, 8);
        pixmap.fillRectangle(0,        size - 8, size, 8);
        pixmap.fillRectangle(0,        0,        8,    size);
        pixmap.fillRectangle(size - 8, 0,        8,    size);

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    /**
     * Generates a force-field arc wall texture ('F').
     * Near-transparent dark base with bright electric-blue vertical arc lines, two horizontal
     * energy bands, glowing intersection nodes, and dark steel emitter posts on the left and
     * right edges that anchor the field visually.
     */
    private static Texture generateForceFieldWallTexture() {
        int    size   = FORCE_FIELD_WALL_TEXTURE_SIZE;
        Random random = new Random(FORCE_FIELD_WALL_SEED);
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        // Near-transparent dark base
        pixmap.setColor(0.03f, 0.05f, 0.10f, 1f);
        pixmap.fill();

        // Emitter posts — left and right edges (16px wide, dark steel with cyan glow slot)
        int postWidth  = 16;
        int slotHeight = size / 3;
        int slotTop    = (size - slotHeight) / 2;
        pixmap.setColor(0.16f, 0.18f, 0.22f, 1f);
        pixmap.fillRectangle(0,          0, postWidth, size);
        pixmap.fillRectangle(size - postWidth, 0, postWidth, size);
        // Glowing slot on each post
        pixmap.setColor(0.30f, 0.70f, 1.00f, 1f);
        pixmap.fillRectangle(4,              slotTop, 8, slotHeight);
        pixmap.fillRectangle(size - postWidth + 4, slotTop, 8, slotHeight);

        // Vertical arc lines with slight zigzag
        int[] arcXPositions = new int[FORCE_FIELD_ARC_COUNT];
        int usableWidth = size - 2 * postWidth;
        for (int arcIndex = 0; arcIndex < FORCE_FIELD_ARC_COUNT; arcIndex++) {
            arcXPositions[arcIndex] = postWidth + (arcIndex + 1) * usableWidth / (FORCE_FIELD_ARC_COUNT + 1);
        }
        for (int arcIndex = 0; arcIndex < FORCE_FIELD_ARC_COUNT; arcIndex++) {
            int arcX = arcXPositions[arcIndex];
            for (int row = 0; row < size; row++) {
                int zigzag = (int)(Math.sin(row * 0.15 + arcIndex) * 4);
                int drawX  = Math.max(postWidth, Math.min(size - postWidth - 1, arcX + zigzag));
                // Outer glow
                pixmap.setColor(0.30f, 0.70f, 1.00f, 1f);
                if (drawX > 0)        pixmap.drawPixel(drawX - 1, row);
                if (drawX < size - 1) pixmap.drawPixel(drawX + 1, row);
                // Bright core
                pixmap.setColor(0.85f, 0.95f, 1.00f, 1f);
                pixmap.drawPixel(drawX, row);
            }
        }

        // Horizontal energy bands
        int[] bandRows = new int[FORCE_FIELD_BAND_COUNT];
        for (int bandIndex = 0; bandIndex < FORCE_FIELD_BAND_COUNT; bandIndex++) {
            bandRows[bandIndex] = size / (FORCE_FIELD_BAND_COUNT + 1) * (bandIndex + 1);
        }
        for (int bandRow : bandRows) {
            pixmap.setColor(0.30f, 0.70f, 1.00f, 1f);
            pixmap.fillRectangle(postWidth, bandRow - 2, usableWidth, 5);
            pixmap.setColor(0.85f, 0.95f, 1.00f, 1f);
            pixmap.fillRectangle(postWidth, bandRow,     usableWidth, 2);
        }

        // Glowing nodes at arc–band intersections
        for (int arcIndex = 0; arcIndex < FORCE_FIELD_ARC_COUNT; arcIndex++) {
            for (int bandRow : bandRows) {
                int nodeX = arcXPositions[arcIndex];
                pixmap.setColor(0.55f, 0.85f, 1.00f, 1f);
                pixmap.fillRectangle(nodeX - 4, bandRow - 4, 9, 9);
                pixmap.setColor(0.85f, 0.95f, 1.00f, 1f);
                pixmap.fillRectangle(nodeX - 2, bandRow - 2, 5, 5);
            }
        }

        // Suppress unused variable warning — seeded for determinism
        random.nextInt();

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    // =========================================================================================
    // STELLAR OBSERVATORY — walls '"' (Viewport dome), ''' (Magrail conduit), '`' (Hull plate).
    // See .claude/agents/ideas/stellar-observatory-gravity-well-room.txt → TEXTURE / SPRITE SPEC
    // for the full per-wall build spec. All three reuse RenderConstants' shared STELLAR_* cool
    // palette so this biome's walls read as one consistent material system with the matching
    // props another pass wires into PropRenderer. Baked once at load time and reused across every
    // tile of that wall type, exactly like every other procedural wall generator in this file —
    // there is no per-tile-instance texture variation anywhere in this pipeline.
    // =========================================================================================

    /**
     * Alpha-blends (red, green, blue) onto the pixel already at (column, row), lerping the
     * existing colour toward the given colour by {@code alpha} (0 = no change, 1 = fully
     * replaced). Pixmap's draw calls used by these procedural generators have no built-in
     * blending, so every "soft glow" / "low-alpha overlay" layer composites manually this way —
     * the same manual-blend pattern the Gore wall's wet-sheen pass uses above. Silently no-ops
     * for out-of-bounds coordinates so callers never need their own bounds checks.
     */
    private static void blendPixel(Pixmap pixmap, int column, int row,
                                   float red, float green, float blue, float alpha) {
        if (alpha <= 0f) return;
        if (column < 0 || row < 0 || column >= pixmap.getWidth() || row >= pixmap.getHeight()) return;
        int existingPixel   = pixmap.getPixel(column, row);
        float existingRed   = ((existingPixel >>> 24) & 0xFF) / 255f;
        float existingGreen = ((existingPixel >>> 16) & 0xFF) / 255f;
        float existingBlue  = ((existingPixel >>>  8) & 0xFF) / 255f;
        float blendedRed    = existingRed   + (red   - existingRed)   * alpha;
        float blendedGreen  = existingGreen + (green - existingGreen) * alpha;
        float blendedBlue   = existingBlue  + (blue  - existingBlue)  * alpha;
        pixmap.setColor(clampUnit(blendedRed), clampUnit(blendedGreen), clampUnit(blendedBlue), 1f);
        pixmap.drawPixel(column, row);
    }

    /**
     * Darkens the outer border of a square wall-texture Pixmap toward black, strongest at the
     * four corners and fading to no darkening at the vignette band's inner edge. Shared finishing
     * pass for all three STELLAR OBSERVATORY wall generators ("corner vignette: darken the outer
     * 8% border of the frame" in the design spec).
     */
    private static void applyStellarCornerVignette(Pixmap pixmap, int size) {
        int vignetteThickness = Math.round(size * 0.08f);
        if (vignetteThickness <= 0) return;
        for (int row = 0; row < size; row++) {
            int distanceFromTopOrBottom = Math.min(row, size - 1 - row);
            for (int column = 0; column < size; column++) {
                int distanceFromLeftOrRight = Math.min(column, size - 1 - column);
                int distanceFromEdge = Math.min(distanceFromTopOrBottom, distanceFromLeftOrRight);
                if (distanceFromEdge >= vignetteThickness) continue;
                float darkenStrength = 0.40f * (1f - (float) distanceFromEdge / vignetteThickness);
                blendPixel(pixmap, column, row, 0f, 0f, 0f, darkenStrength);
            }
        }
    }

    /**
     * True if (column, row) falls inside a rectangular pane whose top-left and top-right corners
     * are rounded off with {@code cornerRadius} — the Viewport dome wall's curved-window
     * silhouette. Below the rounded band the pane is a plain rectangle; within the rounded band a
     * pixel is inside only if it falls within the corresponding rounding-circle's radius.
     */
    private static boolean isInsideRoundedTopPane(int column, int row,
                                                  int paneLeft, int paneRight,
                                                  int paneBottom, int paneTop,
                                                  int cornerRadius) {
        if (column < paneLeft || column >= paneRight || row < paneBottom || row >= paneTop) return false;
        int cornerBandBottomRow = paneTop - cornerRadius;
        if (row < cornerBandBottomRow) return true; // below the rounded band — full rectangle width
        boolean nearLeftCorner  = column < paneLeft + cornerRadius;
        boolean nearRightCorner = column >= paneRight - cornerRadius;
        if (!nearLeftCorner && !nearRightCorner) return true; // top band but not near either corner
        int cornerCenterColumn = nearLeftCorner ? (paneLeft + cornerRadius) : (paneRight - cornerRadius);
        float differenceColumn = column - cornerCenterColumn;
        float differenceRow    = row - cornerBandBottomRow;
        return (differenceColumn * differenceColumn + differenceRow * differenceRow)
                <= (float) (cornerRadius * cornerRadius);
    }

    /**
     * Generates the Viewport dome wall texture ('"') — a curved reinforced star-window onto deep
     * space. See TEXTURE / SPRITE SPEC → WALL '"' — VIEWPORT DOME in the design doc referenced
     * above for the full build spec.
     *
     * Build order (back to front): brushed-titanium frame → rounded-top glass inset (indigo→black
     * vertical gradient) → soft nebula cloud bands → starfield with a few cross-glints → a baked
     * planet crescent → hairline muntins → a diagonal reflection streak → a glowing cyan sill
     * strip with LEDs → a corner vignette.
     */
    private static Texture generateStellarViewportWallTexture() {
        int    size   = STELLAR_VIEWPORT_WALL_TEXTURE_SIZE;
        Random random = new Random(STELLAR_VIEWPORT_WALL_SEED);
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        // Pane bounding box: tall panoramic window, rounded top, a sill margin at the bottom for
        // the status strip, and a mullion frame margin all around.
        int paneLeft     = 26;
        int paneRight    = size - 26;
        int paneBottom   = 40;
        int paneTop      = size - 22;
        int cornerRadius = STELLAR_VIEWPORT_CORNER_RADIUS;

        // ── 1. Frame base: brushed titanium with a vertical streak noise (+/-4% luma) ──
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                float streak = ((column * 13 + (row / 5) * 7) % 17 - 8) / 220f; // vertical brush streak
                float grain  = ((row * 7 + column * 11) % 13 - 6) / 260f;
                pixmap.setColor(clampUnit(STELLAR_HULL_TITANIUM_R + streak + grain),
                                clampUnit(STELLAR_HULL_TITANIUM_G + streak + grain),
                                clampUnit(STELLAR_HULL_TITANIUM_B + streak + grain), 1f);
                pixmap.drawPixel(column, row);
            }
        }

        // ── 2. Pane inset: rounded-top rectangle, vertical gradient VOID_INDIGO (top) → VOID_BLACK (bottom) ──
        boolean[] paneMask = new boolean[size * size];
        for (int row = paneBottom; row < paneTop; row++) {
            for (int column = paneLeft; column < paneRight; column++) {
                if (!isInsideRoundedTopPane(column, row, paneLeft, paneRight, paneBottom, paneTop, cornerRadius)) continue;
                paneMask[row * size + column] = true;
                float verticalFraction = (float) (paneTop - row) / (paneTop - paneBottom); // 0 top .. 1 bottom
                float red   = STELLAR_VOID_INDIGO_R + verticalFraction * (STELLAR_VOID_BLACK_R - STELLAR_VOID_INDIGO_R);
                float green = STELLAR_VOID_INDIGO_G + verticalFraction * (STELLAR_VOID_BLACK_G - STELLAR_VOID_INDIGO_G);
                float blue  = STELLAR_VOID_INDIGO_B + verticalFraction * (STELLAR_VOID_BLACK_B - STELLAR_VOID_INDIGO_B);
                pixmap.setColor(red, green, blue, 1f);
                pixmap.drawPixel(column, row);
            }
        }

        // ── 3. Nebula: soft magenta/teal cloud bands, low-alpha blurred blobs ──
        for (int bandIndex = 0; bandIndex < STELLAR_VIEWPORT_NEBULA_BAND_COUNT; bandIndex++) {
            boolean magentaBand  = (bandIndex % 2 == 0);
            int centerColumn = paneLeft + 20 + random.nextInt(Math.max(1, (paneRight - paneLeft) - 40));
            int centerRow    = paneBottom + 30 + random.nextInt(Math.max(1, (paneTop - paneBottom) - 60));
            int radiusX = 46 + random.nextInt(30);
            int radiusY = 20 + random.nextInt(14);
            float bandRed   = magentaBand ? STELLAR_NEBULA_MAGENTA_R : STELLAR_NEBULA_TEAL_R;
            float bandGreen = magentaBand ? STELLAR_NEBULA_MAGENTA_G : STELLAR_NEBULA_TEAL_G;
            float bandBlue  = magentaBand ? STELLAR_NEBULA_MAGENTA_B : STELLAR_NEBULA_TEAL_B;
            for (int offsetY = -radiusY; offsetY <= radiusY; offsetY++) {
                for (int offsetX = -radiusX; offsetX <= radiusX; offsetX++) {
                    int pixelColumn = centerColumn + offsetX;
                    int pixelRow    = centerRow + offsetY;
                    if (pixelColumn < paneLeft || pixelColumn >= paneRight
                            || pixelRow < paneBottom || pixelRow >= paneTop) continue;
                    if (!paneMask[pixelRow * size + pixelColumn]) continue;
                    float normalizedDistance = (float) Math.sqrt(
                            (offsetX * offsetX) / (float) (radiusX * radiusX)
                          + (offsetY * offsetY) / (float) (radiusY * radiusY));
                    float falloff = GameMath.smoothstep01(GameMath.radialFalloff(normalizedDistance, 1f));
                    if (falloff <= 0f) continue;
                    blendPixel(pixmap, pixelColumn, pixelRow, bandRed, bandGreen, bandBlue, falloff * 0.22f);
                }
            }
        }

        // ── 4. Starfield: scattered star points, a few with a cross-glint ──
        for (int starIndex = 0; starIndex < STELLAR_VIEWPORT_STAR_COUNT; starIndex++) {
            int starColumn = paneLeft + random.nextInt(Math.max(1, paneRight - paneLeft));
            int starRow    = paneBottom + random.nextInt(Math.max(1, paneTop - paneBottom));
            if (!paneMask[starRow * size + starColumn]) continue;
            boolean blueStar = random.nextBoolean();
            float starRed   = blueStar ? STELLAR_STAR_BLUE_R : STELLAR_STAR_WHITE_R;
            float starGreen = blueStar ? STELLAR_STAR_BLUE_G : STELLAR_STAR_WHITE_G;
            float starBlue  = blueStar ? STELLAR_STAR_BLUE_B : STELLAR_STAR_WHITE_B;
            pixmap.setColor(starRed, starGreen, starBlue, 1f);
            pixmap.drawPixel(starColumn, starRow);
            if (starIndex < STELLAR_VIEWPORT_STAR_GLINT_COUNT) {
                if (starColumn - 1 >= paneLeft)  pixmap.drawPixel(starColumn - 1, starRow);
                if (starColumn + 1 < paneRight)  pixmap.drawPixel(starColumn + 1, starRow);
                if (starRow - 1 >= paneBottom)   pixmap.drawPixel(starColumn, starRow - 1);
                if (starRow + 1 < paneTop)       pixmap.drawPixel(starColumn, starRow + 1);
            }
        }

        // ── 5. Planet crescent: one dim rimlit crescent, low in the pane ──
        int planetCenterColumn = paneLeft + (int) ((paneRight - paneLeft) * 0.30f);
        int planetCenterRow    = paneBottom + (int) ((paneTop - paneBottom) * 0.20f);
        int planetRadius = 16;
        for (int offsetY = -planetRadius; offsetY <= planetRadius; offsetY++) {
            for (int offsetX = -planetRadius; offsetX <= planetRadius; offsetX++) {
                int pixelColumn = planetCenterColumn + offsetX;
                int pixelRow    = planetCenterRow + offsetY;
                if (pixelColumn < paneLeft || pixelColumn >= paneRight
                        || pixelRow < paneBottom || pixelRow >= paneTop) continue;
                if (!paneMask[pixelRow * size + pixelColumn]) continue;
                float distance = (float) Math.sqrt(offsetX * offsetX + offsetY * offsetY);
                if (distance > planetRadius) continue;
                // Dark interior disc; a bright rim only along the upper-left edge (rimlit crescent).
                boolean rimEdge = distance > planetRadius - 3f && offsetX <= 0 && offsetY >= 0;
                if (rimEdge) {
                    pixmap.setColor(STELLAR_STAR_BLUE_R, STELLAR_STAR_BLUE_G, STELLAR_STAR_BLUE_B, 1f);
                } else {
                    pixmap.setColor(STELLAR_VOID_BLACK_R, STELLAR_VOID_BLACK_G, STELLAR_VOID_BLACK_B, 1f);
                }
                pixmap.drawPixel(pixelColumn, pixelRow);
            }
        }

        // ── 6. Muntins: 1 vertical + 1 horizontal hairline structural bar crossing the pane ──
        int muntinColumn = (paneLeft + paneRight) / 2;
        int muntinRow    = (paneBottom + paneTop) / 2;
        pixmap.setColor(STELLAR_HULL_STEEL_DARK_R, STELLAR_HULL_STEEL_DARK_G, STELLAR_HULL_STEEL_DARK_B, 1f);
        pixmap.fillRectangle(muntinColumn, paneBottom, 1, paneTop - paneBottom);
        pixmap.fillRectangle(paneLeft, muntinRow, paneRight - paneLeft, 1);

        // ── 7. Reflection: soft diagonal double-glaze highlight streak down one edge ──
        for (int stepIndex = 0; stepIndex < (paneTop - paneBottom); stepIndex++) {
            int pixelColumn = paneLeft + 14 + stepIndex / 2;
            int pixelRow    = paneBottom + stepIndex;
            if (pixelColumn >= paneRight || pixelRow >= paneTop) break;
            if (!paneMask[pixelRow * size + pixelColumn]) continue;
            blendPixel(pixmap, pixelColumn, pixelRow,
                       STELLAR_STAR_WHITE_R, STELLAR_STAR_WHITE_G, STELLAR_STAR_WHITE_B, 0.12f);
            blendPixel(pixmap, pixelColumn + 1, pixelRow,
                       STELLAR_STAR_WHITE_R, STELLAR_STAR_WHITE_G, STELLAR_STAR_WHITE_B, 0.06f);
        }

        // ── 8. Sill strip: cyan status bar along the bottom frame with a soft bloom + LEDs ──
        int sillTop = paneBottom - 12;
        pixmap.setColor(STELLAR_CYAN_GLOW_R * 0.35f, STELLAR_CYAN_GLOW_G * 0.35f, STELLAR_CYAN_GLOW_B * 0.35f, 1f);
        pixmap.fillRectangle(paneLeft, sillTop, paneRight - paneLeft, 6);
        pixmap.setColor(STELLAR_CYAN_GLOW_R, STELLAR_CYAN_GLOW_G, STELLAR_CYAN_GLOW_B, 1f);
        pixmap.fillRectangle(paneLeft, sillTop + 2, paneRight - paneLeft, 2);
        for (int ledIndex = 0; ledIndex < STELLAR_VIEWPORT_SILL_LED_COUNT; ledIndex++) {
            int ledColumn = paneLeft + (paneRight - paneLeft) * (ledIndex + 1) / (STELLAR_VIEWPORT_SILL_LED_COUNT + 1);
            pixmap.setColor(STELLAR_CYAN_GLOW_R, STELLAR_CYAN_GLOW_G, STELLAR_CYAN_GLOW_B, 1f);
            pixmap.fillCircle(ledColumn, sillTop + 3, 3);
            pixmap.setColor(STELLAR_STAR_WHITE_R, STELLAR_STAR_WHITE_G, STELLAR_STAR_WHITE_B, 1f);
            pixmap.fillCircle(ledColumn, sillTop + 3, 1);
        }

        // ── 9. Corner vignette ──
        applyStellarCornerVignette(pixmap, size);

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    /** Draws a small diamond-shaped junction LED (bright centre, dark bezel) centred at (centerX, centerY). */
    private static void drawStellarDiamondNode(Pixmap pixmap, int centerX, int centerY,
                                               float red, float green, float blue) {
        int outerRadius = 5;
        for (int offsetY = -outerRadius; offsetY <= outerRadius; offsetY++) {
            for (int offsetX = -outerRadius; offsetX <= outerRadius; offsetX++) {
                int manhattanDistance = Math.abs(offsetX) + Math.abs(offsetY);
                if (manhattanDistance > outerRadius) continue;
                int pixelColumn = centerX + offsetX;
                int pixelRow    = centerY + offsetY;
                if (pixelColumn < 0 || pixelColumn >= pixmap.getWidth()
                        || pixelRow < 0 || pixelRow >= pixmap.getHeight()) continue;
                if (manhattanDistance >= outerRadius - 1) {
                    pixmap.setColor(STELLAR_HULL_STEEL_DARK_R * 0.4f, STELLAR_HULL_STEEL_DARK_G * 0.4f,
                                    STELLAR_HULL_STEEL_DARK_B * 0.4f, 1f);
                } else {
                    pixmap.setColor(red, green, blue, 1f);
                }
                pixmap.drawPixel(pixelColumn, pixelRow);
            }
        }
    }

    /**
     * Generates the Magrail conduit wall texture (''') — a brushed hull panel threaded with
     * vertical magnetic-rail conduits pulsing violet → cyan (the "traveling current" look). See
     * TEXTURE / SPRITE SPEC → WALL ''' — MAGRAIL CONDUIT in the design doc referenced above.
     *
     * Build order: brushed-titanium base → recessed dark channel housings → emissive violet→cyan
     * rail cores → soft additive bloom bleed → diamond junction LED nodes → a faint frost halo
     * near the coldest node → corner vignette. The traveling-current pulse itself is baked as a
     * static gradient here; any live animation must tint-scale this sprite via the global
     * tick/flicker hook at render time, never reallocate the Pixmap (see design doc ANIMATION note).
     */
    private static Texture generateStellarMagrailWallTexture() {
        int    size   = STELLAR_MAGRAIL_WALL_TEXTURE_SIZE;
        Random random = new Random(STELLAR_MAGRAIL_WALL_SEED);
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        // ── 1. Base: brushed titanium with a vertical streak noise (+/-4% luma) ──
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                float streak = ((column * 13 + (row / 5) * 7) % 17 - 8) / 220f;
                float grain  = ((row * 7 + column * 11) % 13 - 6) / 260f;
                pixmap.setColor(clampUnit(STELLAR_HULL_TITANIUM_R + streak + grain),
                                clampUnit(STELLAR_HULL_TITANIUM_G + streak + grain),
                                clampUnit(STELLAR_HULL_TITANIUM_B + streak + grain), 1f);
                pixmap.drawPixel(column, row);
            }
        }

        int channelCount = STELLAR_MAGRAIL_CHANNEL_COUNT;
        int channelWidth = 4;
        int[] channelCenters = new int[channelCount];
        for (int channelIndex = 0; channelIndex < channelCount; channelIndex++) {
            channelCenters[channelIndex] = size * (channelIndex + 1) / (channelCount + 1);
        }

        for (int channelIndex = 0; channelIndex < channelCount; channelIndex++) {
            int channelCenter = channelCenters[channelIndex];
            int channelLeft   = channelCenter - channelWidth / 2;

            // ── 2. Recessed channel housing with a 1px inner shadow lip ──
            pixmap.setColor(STELLAR_HULL_STEEL_DARK_R, STELLAR_HULL_STEEL_DARK_G, STELLAR_HULL_STEEL_DARK_B, 1f);
            pixmap.fillRectangle(channelLeft, 0, channelWidth, size);
            pixmap.setColor(STELLAR_HULL_STEEL_DARK_R * 0.5f, STELLAR_HULL_STEEL_DARK_G * 0.5f,
                            STELLAR_HULL_STEEL_DARK_B * 0.5f, 1f);
            pixmap.fillRectangle(channelLeft, 0, 1, size);
            pixmap.fillRectangle(channelLeft + channelWidth - 1, 0, 1, size);

            int firstNodeRow = -1;

            // ── 3. Rail core: vertical gradient violet (top) → cyan (bottom) ──
            for (int row = 0; row < size; row++) {
                float verticalFraction = (float) row / (size - 1); // 0 top .. 1 bottom
                float railRed   = STELLAR_MAGRAIL_VIOLET_R + verticalFraction * (STELLAR_MAGRAIL_CYAN_R - STELLAR_MAGRAIL_VIOLET_R);
                float railGreen = STELLAR_MAGRAIL_VIOLET_G + verticalFraction * (STELLAR_MAGRAIL_CYAN_G - STELLAR_MAGRAIL_VIOLET_G);
                float railBlue  = STELLAR_MAGRAIL_VIOLET_B + verticalFraction * (STELLAR_MAGRAIL_CYAN_B - STELLAR_MAGRAIL_VIOLET_B);
                pixmap.setColor(railRed, railGreen, railBlue, 1f);
                pixmap.fillRectangle(channelLeft + 1, row, channelWidth - 2, 1);

                // ── 4. Bloom: soft additive halo bleeding 3-4px onto the plate either side ──
                for (int bloomOffset = 1; bloomOffset <= 4; bloomOffset++) {
                    float bloomAlpha = 0.16f * (1f - (bloomOffset - 1) / 4f);
                    blendPixel(pixmap, channelLeft - bloomOffset, row, railRed, railGreen, railBlue, bloomAlpha);
                    blendPixel(pixmap, channelLeft + channelWidth - 1 + bloomOffset, row, railRed, railGreen, railBlue, bloomAlpha);
                }
            }

            // ── 5. Junction nodes: a diamond LED every STELLAR_MAGRAIL_NODE_SPACING px ──
            int nodeSpacing = STELLAR_MAGRAIL_NODE_SPACING;
            for (int nodeRow = nodeSpacing / 2; nodeRow < size; nodeRow += nodeSpacing) {
                drawStellarDiamondNode(pixmap, channelCenter, nodeRow,
                                       STELLAR_ICE_BLUE_R, STELLAR_ICE_BLUE_G, STELLAR_ICE_BLUE_B);
                if (firstNodeRow < 0) firstNodeRow = nodeRow;
            }

            // ── 6. Frost halo — implies condensation near the coldest node (first channel only) ──
            if (channelIndex == 0 && firstNodeRow >= 0) {
                int frostRadius = 14;
                for (int offsetY = -frostRadius; offsetY <= frostRadius; offsetY++) {
                    for (int offsetX = -frostRadius; offsetX <= frostRadius; offsetX++) {
                        float distance = (float) Math.sqrt(offsetX * offsetX + offsetY * offsetY);
                        float falloff  = GameMath.smoothstep01(GameMath.radialFalloff(distance, frostRadius));
                        if (falloff <= 0f) continue;
                        blendPixel(pixmap, channelCenter + offsetX, firstNodeRow + offsetY,
                                   STELLAR_FROST_WHITE_R, STELLAR_FROST_WHITE_G, STELLAR_FROST_WHITE_B, falloff * 0.18f);
                    }
                }
            }
        }

        // Suppress unused variable warning — seeded for determinism; channel/node placement here
        // is deterministic by index rather than random-walk driven (same idiom as the Bio and
        // Force-Field wall generators above).
        random.nextInt();

        applyStellarCornerVignette(pixmap, size);

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    /** Builds `divisions + 1` cell-boundary positions evenly dividing [0, size) into `divisions` cells. */
    private static int[] buildStellarGridBounds(int size, int divisions) {
        int[] bounds = new int[divisions + 1];
        for (int index = 0; index <= divisions; index++) {
            bounds[index] = size * index / divisions;
        }
        return bounds;
    }

    /** Draws a small border rivet: a bright dot with a 1px dark shadow offset up-right. */
    private static void drawStellarRivet(Pixmap pixmap, int centerX, int centerY) {
        pixmap.setColor(STELLAR_HULL_STEEL_DARK_R * 0.6f, STELLAR_HULL_STEEL_DARK_G * 0.6f,
                        STELLAR_HULL_STEEL_DARK_B * 0.6f, 1f);
        pixmap.fillCircle(centerX + 1, centerY - 1, 2); // shadow
        pixmap.setColor(STELLAR_HULL_STEEL_LIGHT_R, STELLAR_HULL_STEEL_LIGHT_G, STELLAR_HULL_STEEL_LIGHT_B, 1f);
        pixmap.fillCircle(centerX, centerY, 2);          // bolt head
    }

    /**
     * Draws a faint, low-alpha stencilled "hull-section code" mark — a short row of abstract dash
     * blocks (procedural Pixmap generation has no font renderer available, so this suggests an
     * alphanumeric code rather than rendering real glyphs; same abstraction the Bulkhead wall's
     * stencil chevron above uses).
     */
    private static void drawStellarHullStencil(Pixmap pixmap, int left, int bottom) {
        int[] dashWidths = { 4, 2, 2, 4, 2, 4, 4 };
        int   cursorX    = left;
        for (int dashWidth : dashWidths) {
            for (int offsetX = 0; offsetX < dashWidth; offsetX++) {
                blendPixel(pixmap, cursorX + offsetX, bottom,
                           STELLAR_HULL_STEEL_DARK_R, STELLAR_HULL_STEEL_DARK_G, STELLAR_HULL_STEEL_DARK_B, 0.45f);
            }
            cursorX += dashWidth + 2;
        }
    }

    /**
     * Generates the Hull plate wall texture ('`') — the default, clean riveted-titanium shell
     * wall of the rotunda; lighter and far less battle-scarred than the Bulkhead wall ('k'). See
     * TEXTURE / SPRITE SPEC → WALL '`' — HULL PLATE in the design doc referenced above.
     *
     * Build order: brushed-titanium base (horizontal anisotropic streak) → panel seam grid with a
     * bevel highlight → rivet-dot borders → a soft diagonal specular sheen band → a couple of
     * status LEDs → a faint stencilled hull-section code → corner vignette.
     */
    private static Texture generateStellarHullPlateWallTexture() {
        int    size   = STELLAR_HULLPLATE_WALL_TEXTURE_SIZE;
        Random random = new Random(STELLAR_HULLPLATE_WALL_SEED);
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        // ── 1. Base: brushed titanium, HORIZONTAL anisotropic streak noise (+/-5% luma) ──
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                float streak = ((row * 13 + (column / 5) * 7) % 17 - 8) / 180f; // horizontal brush streak
                float grain  = ((row * 7 + column * 11) % 13 - 6) / 260f;
                pixmap.setColor(clampUnit(STELLAR_HULL_TITANIUM_R + streak + grain),
                                clampUnit(STELLAR_HULL_TITANIUM_G + streak + grain),
                                clampUnit(STELLAR_HULL_TITANIUM_B + streak + grain), 1f);
                pixmap.drawPixel(column, row);
            }
        }

        // ── 2. Panel seams: divide into a grid with 1px dark seams + a lighter bevel highlight ──
        int   gridColumns   = STELLAR_HULLPLATE_GRID_COLUMNS;
        int   gridRows      = STELLAR_HULLPLATE_GRID_ROWS;
        int[] plateColumnBounds = buildStellarGridBounds(size, gridColumns);
        int[] plateRowBounds    = buildStellarGridBounds(size, gridRows);
        for (int seamIndex = 1; seamIndex < gridColumns; seamIndex++) {
            int seamColumn = plateColumnBounds[seamIndex];
            pixmap.setColor(STELLAR_HULL_STEEL_DARK_R, STELLAR_HULL_STEEL_DARK_G, STELLAR_HULL_STEEL_DARK_B, 1f);
            pixmap.fillRectangle(seamColumn, 0, 1, size);
            pixmap.setColor(STELLAR_HULL_STEEL_LIGHT_R, STELLAR_HULL_STEEL_LIGHT_G, STELLAR_HULL_STEEL_LIGHT_B, 1f);
            pixmap.fillRectangle(seamColumn + 1, 0, 1, size); // bevel highlight to the right of the seam
        }
        for (int seamIndex = 1; seamIndex < gridRows; seamIndex++) {
            int seamRow = plateRowBounds[seamIndex];
            pixmap.setColor(STELLAR_HULL_STEEL_DARK_R, STELLAR_HULL_STEEL_DARK_G, STELLAR_HULL_STEEL_DARK_B, 1f);
            pixmap.fillRectangle(0, seamRow, size, 1);
            pixmap.setColor(STELLAR_HULL_STEEL_LIGHT_R, STELLAR_HULL_STEEL_LIGHT_G, STELLAR_HULL_STEEL_LIGHT_B, 1f);
            pixmap.fillRectangle(0, seamRow + 1, size, 1); // bevel highlight above the seam
        }

        // ── 3. Rivets: a border of small dots around each plate ──
        int rivetSpacing = STELLAR_HULLPLATE_RIVET_SPACING;
        for (int plateColumnIndex = 0; plateColumnIndex < gridColumns; plateColumnIndex++) {
            int plateLeft  = plateColumnBounds[plateColumnIndex];
            int plateRight = plateColumnBounds[plateColumnIndex + 1];
            for (int plateRowIndex = 0; plateRowIndex < gridRows; plateRowIndex++) {
                int plateBottom = plateRowBounds[plateRowIndex];
                int plateTop    = plateRowBounds[plateRowIndex + 1];
                for (int rivetColumn = plateLeft + 6; rivetColumn < plateRight - 4; rivetColumn += rivetSpacing) {
                    drawStellarRivet(pixmap, rivetColumn, plateBottom + 5);
                    drawStellarRivet(pixmap, rivetColumn, plateTop - 5);
                }
                for (int rivetRow = plateBottom + 6; rivetRow < plateTop - 4; rivetRow += rivetSpacing) {
                    drawStellarRivet(pixmap, plateLeft + 5, rivetRow);
                    drawStellarRivet(pixmap, plateRight - 5, rivetRow);
                }
            }
        }

        // ── 4. Brushed sheen: one soft diagonal specular gleam sweeping the whole tile ──
        for (int stepIndex = 0; stepIndex < size + size / 2; stepIndex++) {
            int pixelColumn = stepIndex;
            int pixelRow    = stepIndex - size / 4;
            if (pixelColumn >= size) break;
            if (pixelRow < 0 || pixelRow >= size) continue;
            blendPixel(pixmap, pixelColumn, pixelRow,
                       STELLAR_STAR_WHITE_R, STELLAR_STAR_WHITE_G, STELLAR_STAR_WHITE_B, 0.08f);
            blendPixel(pixmap, pixelColumn, pixelRow + 1,
                       STELLAR_STAR_WHITE_R, STELLAR_STAR_WHITE_G, STELLAR_STAR_WHITE_B, 0.04f);
        }

        // ── 5. Status LEDs near a seam junction (baked; single texture reused across every tile) ──
        if (gridColumns > 1 && gridRows > 1) {
            int junctionColumn = plateColumnBounds[1];
            int junctionRow    = plateRowBounds[1];
            pixmap.setColor(STELLAR_LED_GREEN_R, STELLAR_LED_GREEN_G, STELLAR_LED_GREEN_B, 1f);
            pixmap.fillCircle(junctionColumn - 14, junctionRow - 14, 3);
            pixmap.setColor(STELLAR_LED_AMBER_R, STELLAR_LED_AMBER_G, STELLAR_LED_AMBER_B, 1f);
            pixmap.fillCircle(junctionColumn + 14, junctionRow + 14, 3);
        }

        // ── 6. Stencil: a faint hull-section code, low alpha, in the bottom-left plate ──
        drawStellarHullStencil(pixmap, 24, 20);

        // ── 7. Corner vignette ──
        applyStellarCornerVignette(pixmap, size);

        // Suppress unused variable warning — seeded for determinism; every layer above is
        // position-driven rather than random-walk driven (same idiom as the Bio wall generator).
        random.nextInt();

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }
}
