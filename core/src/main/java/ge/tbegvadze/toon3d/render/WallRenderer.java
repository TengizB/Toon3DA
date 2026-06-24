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
import ge.tbegvadze.toon3d.util.GameMath;

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
    private final Texture wallTexturePlain;
    private final Texture wallTextureConduit;
    private final Texture wallTextureVent;
    private final Texture wallTextureTerminal;
    private final Texture wallTextureWires;
    private final Texture wallTextureHazard;
    private final Texture wallTextureRust;
    private final Texture wallTextureGore;
    private final Texture wallTextureBulkhead;
    private final Texture wallTextureGlass;
    private final Texture wallTextureBio;
    private final Texture wallTextureEmerg;
    private final Texture wallTextureMed;
    private final Texture wallTextureCryo;
    private final Texture wallTextureRad;
    private final Texture wallTextureBlast;
    private final Texture wallTextureHoloData;
    private final Texture wallTextureForceField;
    private final Texture doorTexture;
    private final Texture doorTextureRed;
    private final Texture doorTextureYellow;
    private final Texture doorTextureBlue;
    private final Texture columnTexture;
    private final Texture whitePixelTexture;
    // Perpendicular wall distance per screen column — used for sprite depth-clipping later.
    private final float[] zBuffer;

    // Cached at load time to avoid calling texture.getWidth()/getHeight() inside render().
    private final int wallTexturePlainWidth;
    private final int wallTexturePlainHeight;
    private final int wallTextureConduitWidth;
    private final int wallTextureConduitHeight;
    private final int wallTextureVentWidth;
    private final int wallTextureVentHeight;
    private final int wallTextureTerminalWidth;
    private final int wallTextureTerminalHeight;
    private final int wallTextureWiresWidth;
    private final int wallTextureWiresHeight;
    private final int wallTextureHazardWidth;
    private final int wallTextureHazardHeight;
    private final int wallTextureRustWidth;
    private final int wallTextureRustHeight;
    private final int wallTextureGoreWidth;
    private final int wallTextureGoreHeight;
    private final int wallTextureBulkheadWidth;
    private final int wallTextureBulkheadHeight;
    private final int wallTextureGlassWidth;
    private final int wallTextureGlassHeight;
    private final int wallTextureBioWidth;
    private final int wallTextureBioHeight;
    private final int wallTextureEmergWidth;
    private final int wallTextureEmergHeight;
    private final int wallTextureMedWidth;
    private final int wallTextureMedHeight;
    private final int wallTextureCryoWidth;
    private final int wallTextureCryoHeight;
    private final int wallTextureRadWidth;
    private final int wallTextureRadHeight;
    private final int wallTextureBlastWidth;
    private final int wallTextureBlastHeight;
    private final int wallTextureHoloDataWidth;
    private final int wallTextureHoloDataHeight;
    private final int wallTextureForceFieldWidth;
    private final int wallTextureForceFieldHeight;
    private final int columnTextureWidth;
    private final int columnTextureHeight;

    // Char-indexed lookup tables (ASCII index 0–127) replace three switch statements per column.
    // Arrays.fill initialises every slot to the plain-wall default; named chars override it.
    private final Texture[] wallTextureTable;
    private final int[]     wallWidthTable;
    private final int[]     wallHeightTable;
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

        wallTexturePlain    = loadWallTexture(LAB_WALL_PLAIN_PATH);
        wallTextureConduit  = loadWallTexture(LAB_WALL_CONDUIT_PATH);
        wallTextureVent     = loadWallTexture(LAB_WALL_VENT_PATH);
        wallTextureTerminal = loadWallTexture(LAB_WALL_TERMINAL_PATH);
        wallTextureWires    = loadWallTexture(LAB_WALL_WIRES_PATH);
        wallTextureHazard   = Gdx.files.internal(LAB_WALL_HAZARD_PATH).exists()
                              ? loadWallTexture(LAB_WALL_HAZARD_PATH)
                              : generateHazardWallTexture();

        wallTexturePlainWidth    = wallTexturePlain.getWidth();
        wallTexturePlainHeight   = wallTexturePlain.getHeight();
        wallTextureConduitWidth  = wallTextureConduit.getWidth();
        wallTextureConduitHeight = wallTextureConduit.getHeight();
        wallTextureVentWidth     = wallTextureVent.getWidth();
        wallTextureVentHeight    = wallTextureVent.getHeight();
        wallTextureTerminalWidth  = wallTextureTerminal.getWidth();
        wallTextureTerminalHeight = wallTextureTerminal.getHeight();
        wallTextureWiresWidth    = wallTextureWires.getWidth();
        wallTextureWiresHeight   = wallTextureWires.getHeight();
        wallTextureHazardWidth   = wallTextureHazard.getWidth();
        wallTextureHazardHeight  = wallTextureHazard.getHeight();

        wallTextureRust     = generateRustWallTexture();
        wallTextureGore     = generateGoreWallTexture();
        wallTextureBulkhead = generateBulkheadWallTexture();

        wallTextureRustWidth      = wallTextureRust.getWidth();
        wallTextureRustHeight     = wallTextureRust.getHeight();
        wallTextureGoreWidth      = wallTextureGore.getWidth();
        wallTextureGoreHeight     = wallTextureGore.getHeight();
        wallTextureBulkheadWidth  = wallTextureBulkhead.getWidth();
        wallTextureBulkheadHeight = wallTextureBulkhead.getHeight();

        wallTextureGlass = generateGlassWallTexture();
        wallTextureBio   = generateBioWallTexture();
        wallTextureEmerg = generateEmergWallTexture();
        wallTextureMed   = generateMedWallTexture();
        wallTextureCryo  = generateCryoWallTexture();
        wallTextureRad   = generateRadWallTexture();
        wallTextureBlast     = generateBlastWallTexture();
        wallTextureHoloData  = generateHoloDataWallTexture();
        wallTextureForceField = generateForceFieldWallTexture();

        wallTextureGlassWidth  = wallTextureGlass.getWidth();
        wallTextureGlassHeight = wallTextureGlass.getHeight();
        wallTextureBioWidth    = wallTextureBio.getWidth();
        wallTextureBioHeight   = wallTextureBio.getHeight();
        wallTextureEmergWidth  = wallTextureEmerg.getWidth();
        wallTextureEmergHeight = wallTextureEmerg.getHeight();
        wallTextureMedWidth    = wallTextureMed.getWidth();
        wallTextureMedHeight   = wallTextureMed.getHeight();
        wallTextureCryoWidth   = wallTextureCryo.getWidth();
        wallTextureCryoHeight  = wallTextureCryo.getHeight();
        wallTextureRadWidth    = wallTextureRad.getWidth();
        wallTextureRadHeight   = wallTextureRad.getHeight();
        wallTextureBlastWidth       = wallTextureBlast.getWidth();
        wallTextureBlastHeight      = wallTextureBlast.getHeight();
        wallTextureHoloDataWidth    = wallTextureHoloData.getWidth();
        wallTextureHoloDataHeight   = wallTextureHoloData.getHeight();
        wallTextureForceFieldWidth  = wallTextureForceField.getWidth();
        wallTextureForceFieldHeight = wallTextureForceField.getHeight();

        doorTexture      = loadOrGenerateDoorTexture(LAB_DOOR_CLOSED_PATH, 0f, 0f, 0f);

        doorTextureRed    = loadOrGenerateDoorTexture(LAB_DOOR_RED_PATH,    0.90f, 0.13f, 0.13f);
        doorTextureYellow = loadOrGenerateDoorTexture(LAB_DOOR_YELLOW_PATH, 0.97f, 0.80f, 0.12f);
        doorTextureBlue   = loadOrGenerateDoorTexture(LAB_DOOR_BLUE_PATH,   0.16f, 0.48f, 0.97f);

        columnTexture      = generateColumnTexture();
        columnTextureWidth  = columnTexture.getWidth();
        columnTextureHeight = columnTexture.getHeight();

        Pixmap whitePixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        whitePixel.setColor(Color.WHITE);
        whitePixel.fill();
        whitePixelTexture = new Texture(whitePixel);
        whitePixel.dispose();

        // --- Texture lookup tables (char → texture / width / height) ---
        // Fill entire range with the plain-wall default so any unrecognised wall char
        // renders as the plain texture rather than throwing a NullPointerException.
        wallTextureTable = new Texture[128];
        wallWidthTable   = new int[128];
        wallHeightTable  = new int[128];
        Arrays.fill(wallTextureTable, wallTexturePlain);
        Arrays.fill(wallWidthTable,   wallTexturePlainWidth);
        Arrays.fill(wallHeightTable,  wallTexturePlainHeight);
        wallTextureTable['c'] = wallTextureConduit;   wallWidthTable['c'] = wallTextureConduitWidth;   wallHeightTable['c'] = wallTextureConduitHeight;
        wallTextureTable['v'] = wallTextureVent;      wallWidthTable['v'] = wallTextureVentWidth;      wallHeightTable['v'] = wallTextureVentHeight;
        wallTextureTable['t'] = wallTextureTerminal;  wallWidthTable['t'] = wallTextureTerminalWidth;  wallHeightTable['t'] = wallTextureTerminalHeight;
        wallTextureTable['w'] = wallTextureWires;     wallWidthTable['w'] = wallTextureWiresWidth;     wallHeightTable['w'] = wallTextureWiresHeight;
        wallTextureTable['h'] = wallTextureHazard;    wallWidthTable['h'] = wallTextureHazardWidth;    wallHeightTable['h'] = wallTextureHazardHeight;
        wallTextureTable['j'] = wallTextureRust;      wallWidthTable['j'] = wallTextureRustWidth;      wallHeightTable['j'] = wallTextureRustHeight;
        wallTextureTable['G'] = wallTextureGore;      wallWidthTable['G'] = wallTextureGoreWidth;      wallHeightTable['G'] = wallTextureGoreHeight;
        wallTextureTable['k'] = wallTextureBulkhead;  wallWidthTable['k'] = wallTextureBulkheadWidth;  wallHeightTable['k'] = wallTextureBulkheadHeight;
        wallTextureTable['N'] = wallTextureGlass;     wallWidthTable['N'] = wallTextureGlassWidth;     wallHeightTable['N'] = wallTextureGlassHeight;
        wallTextureTable['Q'] = wallTextureBio;       wallWidthTable['Q'] = wallTextureBioWidth;       wallHeightTable['Q'] = wallTextureBioHeight;
        wallTextureTable['S'] = wallTextureEmerg;     wallWidthTable['S'] = wallTextureEmergWidth;     wallHeightTable['S'] = wallTextureEmergHeight;
        wallTextureTable['M'] = wallTextureMed;       wallWidthTable['M'] = wallTextureMedWidth;       wallHeightTable['M'] = wallTextureMedHeight;
        wallTextureTable['Z'] = wallTextureCryo;      wallWidthTable['Z'] = wallTextureCryoWidth;      wallHeightTable['Z'] = wallTextureCryoHeight;
        wallTextureTable['U'] = wallTextureRad;       wallWidthTable['U'] = wallTextureRadWidth;       wallHeightTable['U'] = wallTextureRadHeight;
        wallTextureTable['X'] = wallTextureBlast;      wallWidthTable['X'] = wallTextureBlastWidth;      wallHeightTable['X'] = wallTextureBlastHeight;
        wallTextureTable['D'] = wallTextureHoloData;  wallWidthTable['D'] = wallTextureHoloDataWidth;   wallHeightTable['D'] = wallTextureHoloDataHeight;
        wallTextureTable['F'] = wallTextureForceField; wallWidthTable['F'] = wallTextureForceFieldWidth; wallHeightTable['F'] = wallTextureForceFieldHeight;

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

    private static Texture generateColumnTexture() {
        int textureWidth  = 64;
        int textureHeight = 128;
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
        // Horizontal mortar joints every 20 rows (stone course lines).
        pixmap.setColor(0.28f, 0.28f, 0.30f, 1f);
        for (int row = 20; row < textureHeight; row += 20) {
            pixmap.fillRectangle(0, row, textureWidth, 2);
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
        int textureSize = 128;
        Pixmap pixmap = new Pixmap(textureSize, textureSize, Pixmap.Format.RGBA8888);

        int bandWidth = 22; // pixels per yellow+black diagonal band pair

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

        // Dark-steel border frame (4 px each side)
        pixmap.setColor(0x33 / 255f, 0x33 / 255f, 0x3A / 255f, 1f);
        pixmap.fillRectangle(0, 0, textureSize, 4);
        pixmap.fillRectangle(0, textureSize - 4, textureSize, 4);
        pixmap.fillRectangle(0, 0, 4, textureSize);
        pixmap.fillRectangle(textureSize - 4, 0, 4, textureSize);

        // Corner rivets
        pixmap.setColor(0x58 / 255f, 0x58 / 255f, 0x64 / 255f, 1f);
        int rivetSize   = 5;
        int rivetOffset = 7;
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
            blobRadius[blobIndex]  = 8 + random.nextInt(21);
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
        pixmap.fillRectangle(0, size / 3, size, 2);
        pixmap.fillRectangle(0, 2 * size / 3, size, 2);

        // Corroded rivets and drip streaks at each seam
        int[] seamRows = { size / 3, 2 * size / 3 };
        for (int seamRow : seamRows) {
            for (int rivetX = size / 4; rivetX < size; rivetX += size / 4) {
                pixmap.setColor(0.20f, 0.14f, 0.10f, 1f);
                pixmap.fillRectangle(rivetX - 1, seamRow - 1, 3, 3);
                int dripLength = 8 + random.nextInt(16);
                for (int dripRow = seamRow + 3; dripRow < Math.min(size, seamRow + 3 + dripLength); dripRow++) {
                    float dripFade = 1f - (float)(dripRow - seamRow - 3) / dripLength;
                    float dripDark = 0.26f * dripFade;
                    pixmap.setColor(Math.max(0f, dripDark),
                                    Math.max(0f, dripDark * 0.5f),
                                    Math.max(0f, dripDark * 0.27f), 1f);
                    pixmap.drawPixel(rivetX, dripRow);
                }
            }
        }

        // Perforation pits in the heavier bottom rust zone
        for (int pitIndex = 0; pitIndex < RUST_PIT_COUNT; pitIndex++) {
            int pitColumn = random.nextInt(size);
            int pitRow    = size / 2 + random.nextInt(size / 2);
            pixmap.setColor(0.05f, 0.04f, 0.04f, 1f);
            pixmap.fillRectangle(pitColumn, pitRow, 2, 2);
        }

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    /**
     * Generates a demonic flesh-infestation wall texture.
     * Grey steel base overgrown with corner-anchored fleshy blobs (dark meat → bright flesh),
     * sinew veins via random walks, wet specular glints, pale bone nodules, and radiating cracks.
     */
    private static Texture generateGoreWallTexture() {
        int    size   = GORE_WALL_TEXTURE_SIZE;
        Random random = new Random(GORE_WALL_SEED);
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        int[] blobCenterX = new int[GORE_BLOB_COUNT];
        int[] blobCenterY = new int[GORE_BLOB_COUNT];
        int[] blobRadius  = new int[GORE_BLOB_COUNT];
        for (int blobIndex = 0; blobIndex < GORE_BLOB_COUNT; blobIndex++) {
            blobCenterX[blobIndex] = (int)(random.nextFloat() * size * 0.70f);
            blobCenterY[blobIndex] = (int)(random.nextFloat() * size * 0.70f);
            blobRadius[blobIndex]  = 10 + random.nextInt(21);
        }

        float[] fleshWeightMap = new float[size * size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                float totalWeight = 0f;
                for (int blobIndex = 0; blobIndex < GORE_BLOB_COUNT; blobIndex++) {
                    float differenceX = column - blobCenterX[blobIndex];
                    float differenceY = row    - blobCenterY[blobIndex];
                    float distance    = (float) Math.sqrt(differenceX * differenceX + differenceY * differenceY);
                    totalWeight += GameMath.radialFalloff(distance, blobRadius[blobIndex]);
                }
                fleshWeightMap[row * size + column] = Math.min(1f, totalWeight);
            }
        }

        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                float fleshWeight = fleshWeightMap[row * size + column];
                float grain = ((row * 7 + column * 11) % 13 - 6) / 120f;
                float red, green, blue;
                if (fleshWeight > GORE_FLESH_THRESHOLD) {
                    float interpolationFactor = (fleshWeight - GORE_FLESH_THRESHOLD) / (1f - GORE_FLESH_THRESHOLD);
                    red   = Math.max(0f, Math.min(1f, 0.20f + interpolationFactor * 0.25f + grain));
                    green = Math.max(0f, Math.min(1f, 0.04f + interpolationFactor * 0.04f));
                    blue  = Math.max(0f, Math.min(1f, 0.05f + interpolationFactor * 0.04f));
                } else {
                    red   = Math.max(0f, Math.min(1f, 0.26f + grain));
                    green = Math.max(0f, Math.min(1f, 0.27f + grain));
                    blue  = Math.max(0f, Math.min(1f, 0.29f + grain));
                }
                pixmap.setColor(red, green, blue, 1f);
                pixmap.drawPixel(column, row);
            }
        }

        // Veins — random walks through flesh region
        for (int veinIndex = 0; veinIndex < GORE_VEIN_COUNT; veinIndex++) {
            int   veinColumn = random.nextInt(size / 2);
            int   veinRow    = random.nextInt(size / 2);
            float veinAngleRadians = random.nextFloat() * (float)(Math.PI * 2);
            for (int stepIndex = 0; stepIndex < 30; stepIndex++) {
                if (veinColumn < 0 || veinColumn >= size || veinRow < 0 || veinRow >= size) break;
                if (fleshWeightMap[veinRow * size + veinColumn] > 0.40f) {
                    pixmap.setColor(0.72f, 0.12f, 0.14f, 1f);
                    pixmap.drawPixel(veinColumn, veinRow);
                }
                veinAngleRadians += (random.nextFloat() - 0.5f) * 0.8f;
                veinColumn       += (int) Math.round(Math.cos(veinAngleRadians));
                veinRow          += (int) Math.round(Math.sin(veinAngleRadians));
            }
        }

        // Wet specular glints
        for (int glintIndex = 0; glintIndex < GORE_GLINT_COUNT; glintIndex++) {
            int glintColumn = random.nextInt(size);
            int glintRow    = random.nextInt(size);
            if (fleshWeightMap[glintRow * size + glintColumn] > 0.50f) {
                pixmap.setColor(0.95f, 0.55f, 0.55f, 1f);
                pixmap.drawPixel(glintColumn, glintRow);
            }
        }

        // Bone nodules pushing through meat
        for (int boneIndex = 0; boneIndex < GORE_BONE_COUNT; boneIndex++) {
            int boneColumn = random.nextInt(size / 2);
            int boneRow    = random.nextInt(size / 2);
            if (fleshWeightMap[boneRow * size + boneColumn] > 0.60f) {
                pixmap.setColor(0.20f, 0.04f, 0.05f, 1f);
                pixmap.fillRectangle(boneColumn - 2, boneRow - 2, 5, 5);
                pixmap.setColor(0.80f, 0.76f, 0.66f, 1f);
                pixmap.fillRectangle(boneColumn - 1, boneRow - 1, 3, 3);
            }
        }

        // Cracks radiating from top-left corner across the steel/flesh boundary
        for (int crackIndex = 0; crackIndex < 3; crackIndex++) {
            int   crackColumn      = crackIndex * 5;
            int   crackRow         = crackIndex * 4;
            float crackAngleRadians = (float)(Math.PI / 4) + (random.nextFloat() - 0.5f) * 0.6f;
            for (int stepIndex = 0; stepIndex < 40; stepIndex++) {
                if (crackColumn < 0 || crackColumn >= size || crackRow < 0 || crackRow >= size) break;
                pixmap.setColor(0.03f, 0.02f, 0.02f, 1f);
                pixmap.drawPixel(crackColumn, crackRow);
                crackAngleRadians += (random.nextFloat() - 0.5f) * 0.4f;
                crackColumn       += (int) Math.round(Math.cos(crackAngleRadians));
                crackRow          += (int) Math.round(Math.sin(crackAngleRadians));
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
        pixmap.fillRectangle(border - 2, border - 2, size - 2 * border + 4, 2); // top highlight
        pixmap.fillRectangle(border - 2, border - 2, 2, size - 2 * border + 4); // left highlight
        pixmap.setColor(0.13f, 0.15f, 0.19f, 1f);
        pixmap.fillRectangle(border - 2, size - border, size - 2 * border + 4, 2); // bottom shadow
        pixmap.fillRectangle(size - border, border - 2, 2, size - 2 * border + 4); // right shadow

        // Bolt grid: shadow offset then lit head
        int boltSpacing = BULKHEAD_BOLT_SPACING;
        // Top and bottom rows
        for (int boltX = border + boltSpacing / 2; boltX < size - border; boltX += boltSpacing) {
            pixmap.setColor(0.08f, 0.09f, 0.11f, 1f);
            pixmap.fillRectangle(boltX + 1, border + 2, 3, 3);
            pixmap.fillRectangle(boltX + 1, size - border - 4, 3, 3);
            pixmap.setColor(0.58f, 0.62f, 0.68f, 1f);
            pixmap.fillRectangle(boltX, border + 1, 3, 3);
            pixmap.fillRectangle(boltX, size - border - 5, 3, 3);
        }
        // Left and right columns
        for (int boltY = border + boltSpacing / 2; boltY < size - border; boltY += boltSpacing) {
            pixmap.setColor(0.08f, 0.09f, 0.11f, 1f);
            pixmap.fillRectangle(border + 2, boltY + 1, 3, 3);
            pixmap.fillRectangle(size - border - 4, boltY + 1, 3, 3);
            pixmap.setColor(0.58f, 0.62f, 0.68f, 1f);
            pixmap.fillRectangle(border + 1, boltY, 3, 3);
            pixmap.fillRectangle(size - border - 5, boltY, 3, 3);
        }

        // Hydraulic seam at center of panel
        pixmap.setColor(0.06f, 0.07f, 0.09f, 1f);
        pixmap.fillRectangle(border, size / 2, size - 2 * border, 2);
        pixmap.setColor(0.46f, 0.50f, 0.57f, 1f);
        pixmap.fillRectangle(border, size / 2 - 1, size - 2 * border, 1); // highlight above seam

        // Faded stencil chevron in top-right of center panel
        int stencilX = size - border - 18;
        int stencilY = border + 6;
        // Blend stencil color (70% yellow-gold, 30% panel dark)
        pixmap.setColor(0.70f * 0.70f + 0.24f * 0.30f, 0.66f * 0.70f + 0.27f * 0.30f,
                        0.30f * 0.70f + 0.33f * 0.30f, 1f);
        for (int chevronRow = 0; chevronRow < 7; chevronRow++) {
            int indent = chevronRow <= 3 ? chevronRow : 6 - chevronRow;
            pixmap.drawPixel(stencilX + indent, stencilY + chevronRow);
            if (indent > 0) pixmap.drawPixel(stencilX + indent - 1, stencilY + chevronRow);
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
        pixmap.fillRectangle(14, 14, 100, 100);

        // Layer 3: Ghost silhouette blobs — soft radial smears inside the pane
        for (int blobIndex = 0; blobIndex < GLASS_GHOST_BLOB_COUNT; blobIndex++) {
            int blobCenterColumn = 34 + blobIndex * 22;
            int blobCenterRow    = 40 + blobIndex * 14;
            int blobRadius       = 18;
            for (int pixelRow = blobCenterRow - blobRadius; pixelRow <= blobCenterRow + blobRadius; pixelRow++) {
                for (int pixelColumn = blobCenterColumn - blobRadius; pixelColumn <= blobCenterColumn + blobRadius; pixelColumn++) {
                    if (pixelColumn < 14 || pixelColumn >= 114 || pixelRow < 14 || pixelRow >= 114) continue;
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
            int streakOffset = 15 + streakIndex * 30;
            for (int stepIndex = 0; stepIndex < 90; stepIndex++) {
                int pixelColumn = 14 + streakOffset + stepIndex;
                int pixelRow    = 14 + stepIndex;
                if (pixelColumn >= 114 || pixelRow >= 114) break;
                pixmap.drawPixel(pixelColumn, pixelRow);
            }
        }

        // Layer 5: Spiderweb crack from impact point at (85, 45)
        int impactColumn = 85;
        int impactRow    = 45;
        for (int branchIndex = 0; branchIndex < GLASS_CRACK_BRANCH_COUNT; branchIndex++) {
            int   crackColumn      = impactColumn;
            int   crackRow         = impactRow;
            float crackAngleRadians = (float)(Math.PI * 2) * branchIndex / GLASS_CRACK_BRANCH_COUNT
                                      + (random.nextFloat() - 0.5f) * 0.6f;
            for (int stepIndex = 0; stepIndex < GLASS_CRACK_STEPS; stepIndex++) {
                if (crackColumn < 14 || crackColumn >= 114 || crackRow < 14 || crackRow >= 114) break;
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
        pixmap.fillRectangle(14, 14, 100, 2);
        pixmap.fillRectangle(14, 14, 2, 100);
        // Bottom and right edges — shadow
        pixmap.setColor(0.12f, 0.14f, 0.18f, 1f);
        pixmap.fillRectangle(14, 112, 100, 2);
        pixmap.fillRectangle(112, 14, 2, 100);

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
        pixmap.fillRectangle(0, size / 3, size, 1);
        pixmap.fillRectangle(0, 2 * size / 3, size, 1);

        // Layer 3: Diagonal warning stripe band across rows 85..115
        for (int row = 85; row <= 115 && row < size; row++) {
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

        // Layer 4: Biohazard trefoil approximation at center (48, 50)
        // Three overlapping rectangular blocks rotated 0°, 120°, 240° (approximated as
        // axis-aligned + diagonal fills). Center disc first.
        int trefoilCenterColumn = 48;
        int trefoilCenterRow    = 50;
        // Mix 80% of (0.10, 0.10, 0.10) with 20% of panel white (0.74, 0.77, 0.74)
        float trefoilRed   = 0.10f * 0.80f + 0.74f * 0.20f;
        float trefoilGreen = 0.10f * 0.80f + 0.77f * 0.20f;
        float trefoilBlue  = 0.10f * 0.80f + 0.74f * 0.20f;
        pixmap.setColor(trefoilRed, trefoilGreen, trefoilBlue, 1f);
        // Center disc (radius 4)
        pixmap.fillRectangle(trefoilCenterColumn - 4, trefoilCenterRow - 4, 8, 8);
        // Lobe 0: upward arc block
        pixmap.fillRectangle(trefoilCenterColumn - 4, trefoilCenterRow - 14, 8, 8);
        // Lobe 1: lower-right arc block (approx 120° rotation)
        pixmap.fillRectangle(trefoilCenterColumn + 4, trefoilCenterRow + 2, 8, 8);
        // Lobe 2: lower-left arc block (approx 240° rotation)
        pixmap.fillRectangle(trefoilCenterColumn - 12, trefoilCenterRow + 2, 8, 8);
        // Gap ring between lobes and center — redraw center disc in panel white to punch gap
        pixmap.setColor(0.74f, 0.77f, 0.74f, 1f);
        pixmap.fillRectangle(trefoilCenterColumn - 2, trefoilCenterRow - 2, 4, 4);

        // Layer 5: 1px green glow line just below the stripe band at row 115
        pixmap.setColor(0.18f, 0.55f, 0.22f, 1f);
        if (115 < size) {
            pixmap.fillRectangle(0, 115, size, 1);
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
        int stripCenterRow = 58;
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                float grain    = ((row * 7 + column * 11) % 13 - 6) / 150f;
                float steelRed   = Math.max(0f, Math.min(1f, 0.20f + grain));
                float steelGreen = Math.max(0f, Math.min(1f, 0.21f + grain));
                float steelBlue  = Math.max(0f, Math.min(1f, 0.24f + grain));
                float distanceFromStrip = Math.abs(row - stripCenterRow);
                float glowWeight = Math.max(0f, 1f - distanceFromStrip / 22f);
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
        pixmap.fillRectangle(0, 55, size, 3);

        // Layer 4: Red strip core and white-hot centre line
        pixmap.setColor(0.95f, 0.18f, 0.16f, 1f);
        pixmap.fillRectangle(0, 57, size, 2);
        pixmap.setColor(1.00f, 0.70f, 0.70f, 1f);
        for (int column = 0; column < size; column++) {
            pixmap.drawPixel(column, 58);
        }

        // Layer 5: Cage housings every (size / EMERG_CAGE_COUNT) columns
        int cageSpacing = size / EMERG_CAGE_COUNT;
        for (int cageIndex = 0; cageIndex < EMERG_CAGE_COUNT; cageIndex++) {
            int cageCenterColumn = cageIndex * cageSpacing + cageSpacing / 2;
            pixmap.setColor(0.10f, 0.10f, 0.11f, 1f);
            // 5-wide × 4-tall crosshatch of pixels centred on strip row
            for (int offsetRow = -2; offsetRow <= 1; offsetRow++) {
                for (int offsetColumn = -2; offsetColumn <= 2; offsetColumn++) {
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

        // Layer 2: Green accent stripe at row 76, 3px tall
        pixmap.setColor(0.20f, 0.62f, 0.45f, 1f);
        pixmap.fillRectangle(0, 76, size, 3);

        // Layer 3: Medical cross at approx (60, 28) — white 20×20 backing, red cross bars
        int crossCenterColumn = 60;
        int crossCenterRow    = 28;
        // White background square
        pixmap.setColor(0.90f, 0.92f, 0.90f, 1f);
        pixmap.fillRectangle(crossCenterColumn - 10, crossCenterRow - 10, 20, 20);
        // Red cross — 8×20 vertical bar and 20×8 horizontal bar
        pixmap.setColor(0.80f, 0.16f, 0.16f, 1f);
        pixmap.fillRectangle(crossCenterColumn - 4,  crossCenterRow - 10, 8,  20);
        pixmap.fillRectangle(crossCenterColumn - 10, crossCenterRow - 4,  20, 8);

        // Layer 4: Blood flecks scattered in the lower half
        for (int fleckIndex = 0; fleckIndex < MED_BLOOD_FLECK_COUNT; fleckIndex++) {
            int fleckColumn = random.nextInt(size);
            int fleckRow    = size / 2 + random.nextInt(size / 2);
            int fleckSize   = 1 + random.nextInt(2); // 1 or 2 pixels
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

        // Layer 3: Horizontal coolant pipe seam at row 51
        pixmap.setColor(0.24f, 0.28f, 0.33f, 1f);
        pixmap.fillRectangle(0, 51, size, 2);
        pixmap.setColor(0.50f, 0.65f, 0.78f, 1f);
        pixmap.fillRectangle(0, 50, size, 1); // 1px highlight above seam

        // Layer 4: Icicles hanging from the pipe seam
        int[] icicleColumns = new int[CRYO_ICICLE_COUNT];
        for (int icicleIndex = 0; icicleIndex < CRYO_ICICLE_COUNT; icicleIndex++) {
            icicleColumns[icicleIndex] = 10 + icicleIndex * (size / CRYO_ICICLE_COUNT);
        }
        for (int icicleIndex = 0; icicleIndex < CRYO_ICICLE_COUNT; icicleIndex++) {
            int icicleLength  = 8 + random.nextInt(7); // 8..14 rows
            int icicleBaseCol = icicleColumns[icicleIndex];
            for (int stepIndex = 0; stepIndex < icicleLength; stepIndex++) {
                int pixelRow    = 53 + stepIndex;
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
            for (int stepIndex = 0; stepIndex < 25; stepIndex++) {
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
        int    hotColumn = 85;
        int    hotRow    = 90;
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
                float haloWeight  = GameMath.radialFalloff(distance, 50f);
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
            pixmap.drawPixel(dustColumn, dustRow);
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
            scorchRadius[scorchIndex]       = 15 + random.nextInt(11); // 15..25
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
            int holeRadius       = 1 + random.nextInt(2); // radius 1 or 2 → disc 2 or 3 px across
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
            int craterRadius       = 4 + random.nextInt(4); // 4..7 → disc 8..14 px across
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
                for (int stepIndex = 0; stepIndex < craterRadius + 8; stepIndex++) {
                    if (streakColumn < 0 || streakColumn >= size || streakRow < 0 || streakRow >= size) break;
                    pixmap.setColor(0.12f, 0.10f, 0.09f, 1f);
                    pixmap.drawPixel(streakColumn, streakRow);
                    streakColumn += (int) Math.round(Math.cos(streakAngleRadians));
                    streakRow    += (int) Math.round(Math.sin(streakAngleRadians));
                }
            }

            // Crater rim circle (1px)
            pixmap.setColor(0.46f, 0.47f, 0.50f, 1f);
            for (int rimStep = 0; rimStep < 64; rimStep++) {
                float rimAngleRadians = (float)(Math.PI * 2) * rimStep / 64;
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
            int holeRadius       = 2; // 4px disc
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
                int   columnTexColumn = GameMath.textureColumn(columnU, columnTextureWidth);

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
                                       lineHeight, columnTextureHeight);
                int texSrcHeight = GameMath.wallTextureClipSrcHeight(
                                       drawTop, drawBottom,
                                       lineHeight, columnTextureHeight);
                texSrcHeight = Math.min(texSrcHeight, columnTextureHeight - texSrcY);
                texSrcHeight = Math.max(1, texSrcHeight);

                result.drawSurface         = true;
                result.surfaceTexture      = columnTexture;
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

                // O(1) array lookup replaces three sequential switch statements.
                int     hitCharIndex          = hitWallCell & 0x7F;
                Texture selectedTexture       = wallTextureTable[hitCharIndex];
                int     selectedTextureWidth  = wallWidthTable[hitCharIndex];
                int     selectedTextureHeight = wallHeightTable[hitCharIndex];
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
        wallTexturePlain.dispose();
        wallTextureConduit.dispose();
        wallTextureVent.dispose();
        wallTextureTerminal.dispose();
        wallTextureWires.dispose();
        wallTextureHazard.dispose();
        wallTextureRust.dispose();
        wallTextureGore.dispose();
        wallTextureBulkhead.dispose();
        wallTextureGlass.dispose();
        wallTextureBio.dispose();
        wallTextureEmerg.dispose();
        wallTextureMed.dispose();
        wallTextureCryo.dispose();
        wallTextureRad.dispose();
        wallTextureBlast.dispose();
        wallTextureHoloData.dispose();
        wallTextureForceField.dispose();
        doorTexture.dispose();
        doorTextureRed.dispose();
        doorTextureYellow.dispose();
        doorTextureBlue.dispose();
        columnTexture.dispose();
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
            for (int row = 4; row < size - 4; row++) {
                pixmap.setColor(0.07f, 0.10f, 0.13f, 1f);
                pixmap.drawPixel(column, row);
            }
        }

        // Horizontal data bars (varying fill widths)
        int barHeight = 6;
        int barMargin = 6;
        for (int barIndex = 0; barIndex < HOLO_DATA_BAR_COUNT; barIndex++) {
            int barTop  = barMargin + barIndex * ((size - 2 * barMargin) / HOLO_DATA_BAR_COUNT);
            int fillWidth = (int)((0.40f + random.nextFloat() * 0.50f) * (size - 2 * barMargin));
            // Dim background track
            pixmap.setColor(0.10f, 0.45f, 0.55f, 1f);
            pixmap.fillRectangle(barMargin, barTop, size - 2 * barMargin, barHeight);
            // Bright filled portion
            pixmap.setColor(0.35f, 0.95f, 1.00f, 1f);
            pixmap.fillRectangle(barMargin, barTop, fillWidth, barHeight);
        }

        // Readout grid — small squares below the bars
        int gridTop   = barMargin + HOLO_DATA_BAR_COUNT * ((size - 2 * barMargin) / HOLO_DATA_BAR_COUNT) + 8;
        int cellSize  = (size - 2 * barMargin) / HOLO_DATA_READOUT_COLUMNS;
        int cellInner = cellSize - 2;
        for (int gridRow = 0; gridRow < HOLO_DATA_READOUT_ROWS; gridRow++) {
            for (int gridColumn = 0; gridColumn < HOLO_DATA_READOUT_COLUMNS; gridColumn++) {
                int cellLeft = barMargin + gridColumn * cellSize;
                int cellTop  = gridTop  + gridRow    * cellSize;
                if (cellTop + cellInner >= size - 4) continue;
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

        // Steel bezel — 4px on all edges
        pixmap.setColor(0.18f, 0.20f, 0.24f, 1f);
        pixmap.fillRectangle(0,        0,        size, 4);
        pixmap.fillRectangle(0,        size - 4, size, 4);
        pixmap.fillRectangle(0,        0,        4,    size);
        pixmap.fillRectangle(size - 4, 0,        4,    size);

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

        // Emitter posts — left and right edges (8px wide, dark steel with cyan glow slot)
        int postWidth  = 8;
        int slotHeight = size / 3;
        int slotTop    = (size - slotHeight) / 2;
        pixmap.setColor(0.16f, 0.18f, 0.22f, 1f);
        pixmap.fillRectangle(0,          0, postWidth, size);
        pixmap.fillRectangle(size - postWidth, 0, postWidth, size);
        // Glowing slot on each post
        pixmap.setColor(0.30f, 0.70f, 1.00f, 1f);
        pixmap.fillRectangle(2,              slotTop, 4, slotHeight);
        pixmap.fillRectangle(size - postWidth + 2, slotTop, 4, slotHeight);

        // Vertical arc lines with slight zigzag
        int[] arcXPositions = new int[FORCE_FIELD_ARC_COUNT];
        int usableWidth = size - 2 * postWidth;
        for (int arcIndex = 0; arcIndex < FORCE_FIELD_ARC_COUNT; arcIndex++) {
            arcXPositions[arcIndex] = postWidth + (arcIndex + 1) * usableWidth / (FORCE_FIELD_ARC_COUNT + 1);
        }
        for (int arcIndex = 0; arcIndex < FORCE_FIELD_ARC_COUNT; arcIndex++) {
            int arcX = arcXPositions[arcIndex];
            for (int row = 0; row < size; row++) {
                int zigzag = (int)(Math.sin(row * 0.15 + arcIndex) * 2);
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
            pixmap.fillRectangle(postWidth, bandRow - 1, usableWidth, 3);
            pixmap.setColor(0.85f, 0.95f, 1.00f, 1f);
            pixmap.fillRectangle(postWidth, bandRow,     usableWidth, 1);
        }

        // Glowing nodes at arc–band intersections
        for (int arcIndex = 0; arcIndex < FORCE_FIELD_ARC_COUNT; arcIndex++) {
            for (int bandRow : bandRows) {
                int nodeX = arcXPositions[arcIndex];
                pixmap.setColor(0.55f, 0.85f, 1.00f, 1f);
                pixmap.fillRectangle(nodeX - 2, bandRow - 2, 5, 5);
                pixmap.setColor(0.85f, 0.95f, 1.00f, 1f);
                pixmap.fillRectangle(nodeX - 1, bandRow - 1, 3, 3);
            }
        }

        // Suppress unused variable warning — seeded for determinism
        random.nextInt();

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }
}
