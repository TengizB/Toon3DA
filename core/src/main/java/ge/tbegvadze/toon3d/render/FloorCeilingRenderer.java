package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.RenderResolution;

import java.nio.IntBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;

import static ge.tbegvadze.toon3d.util.Constants.*;
import static ge.tbegvadze.toon3d.util.RenderConstants.*;

/**
 * Renders the textured floor and ceiling as a full-screen backdrop using
 * Lodev floor-casting adapted for Y-up coordinates.
 *
 * Pipeline per frame (one SpriteBatch, one GPU draw call):
 *   1. For every row in the bottom half of the backdrop (floor rows) compute
 *      the world-space tile coordinate of each pixel via back-projection and
 *      write a shaded texel into a CPU int[] backbuffer.
 *   2. Mirror each floor row into its ceiling counterpart (same row distance,
 *      different texture and ambient colour).
 *   3. Upload the backbuffer to a Pixmap → Texture via ByteBuffer.
 *   4. Draw the texture stretched to fill the full 1280×720 screen with a
 *      single batch.draw() call.
 *
 * Multithreading: rows are split across a fixed thread pool. Each thread writes
 * to non-overlapping index ranges of backbuffer[], so no synchronisation is
 * needed during the fill phase. A Phaser barrier ensures all rows are complete
 * before the GPU upload begins.
 *
 * Y-up adaptations vs. Lodev (Y-down):
 *   - pixelOffset = screenHeight/2 − drawY  (Y-up; Lodev uses drawY − screenHeight/2)
 *   - rayDirLeft  = direction + plane        (column 0 → cameraParameter = +1 in Y-up)
 *   - rayDirRight = direction − plane        (column W-1 → cameraParameter ≈ −1)
 *   - Floor pixel at Y-up row drawY → backbuffer row (backdropHeight-1-drawY) so
 *     that image row 0 (visual top) maps to the visual top when LibGDX renders it.
 *
 * Resolution:
 *   Backdrop size is runtime-switchable via {@code util.RenderResolution} (see
 *   {@link #setRenderResolution}) — HD reproduces the historic fixed 320×180 backdrop
 *   (= WORLD_WIDTH/4 × WORLD_HEIGHT/4) byte-identically; FULL_HD raises it to 480×270.
 *   SpriteBatch upscales it to the full viewport regardless of tier, for a retro pixel
 *   look and significant CPU savings.
 */
public class FloorCeilingRenderer implements Renderable, Disposable {

    private final Level level;
    private final SpriteBatch batch;
    // Backdrop resolution is runtime-switchable (util/RenderResolution, checkpoint contract C5), so
    // these are rebuilt (old disposed, new allocated) by setRenderResolution() only on a CHANGE —
    // never per frame. Default HD before any set call, so a renderer that never has the setting pushed
    // into it (e.g. a headless caller) reproduces today's fixed 320x180 behaviour byte-identically.
    private RenderResolution renderResolution = RenderResolution.HD;
    private int   backdropWidth  = renderResolution.getFloorBackdropWidth();
    private int   backdropHeight = renderResolution.getFloorBackdropHeight();
    /*
     * Formula: full-screen pixel rows per backdrop row
     * Derivation: the backdrop row count no longer always divides WORLD_HEIGHT evenly (FULL_HD:
     *   720 / 270 = 2.667), so the old integer FLOOR_BACKDROP_SCALE_DIVISOR (always 4) is replaced by
     *   this runtime float ratio, recomputed whenever the backdrop is (re)allocated.
     *   HD: 720 / 180 = 4.0 exactly (byte-identical to the old constant). FULL_HD: 720 / 270 ≈ 2.667.
     * Edge cases: backdropHeight is always a positive RenderResolution-derived value, never zero.
     */
    private float rowsPerBackdropRow = WORLD_HEIGHT / (float) backdropHeight;
    private Pixmap backdropPixmap;
    private Texture backdropTexture;
    private int[] backbuffer;

    private final int[] floorTexelsPacked;
    private final int   floorTextureWidth;
    private final int   floorTextureHeight;

    private final int[] ceilingTexelsPacked;
    private final int   ceilingTextureWidth;
    private final int   ceilingTextureHeight;

    private float playerWorldX        = 0f;
    private float playerWorldY        = 0f;
    private float directionX          = 1f;
    private float directionY          = 0f;
    private float fieldOfViewRadians  = PLAYER_FIELD_OF_VIEW_RADIANS;
    private float alertPulse          = 0f;
    // Monotonically increasing facility clock; drives 'f' (flickering) tile brightness.
    private float lightingTimeSeconds = 0f;

    // --- Thread pool ---
    private final int              workerCount;
    private final ExecutorService  workerPool;
    private final FloorRowWorker[] workers;
    private final Phaser           phaser;

    // Per-frame ray geometry written before threads are launched, read-only during fill.
    private float cachedPlayerTileX;
    private float cachedPlayerTileY;
    private float cachedRayDirLeftX;
    private float cachedRayDirLeftY;
    private float cachedRayDirRightX;
    private float cachedRayDirRightY;

    public FloorCeilingRenderer(Level level) {
        this.level = level;
        batch = new SpriteBatch(1);

        backdropPixmap  = new Pixmap(backdropWidth, backdropHeight, Pixmap.Format.RGBA8888);
        backdropTexture = new Texture(backdropPixmap);
        backbuffer      = new int[backdropWidth * backdropHeight];

        floorTextureWidth    = 64;
        floorTextureHeight   = 64;
        floorTexelsPacked    = generateFloorTexture(floorTextureWidth, floorTextureHeight);

        ceilingTextureWidth  = 64;
        ceilingTextureHeight = 64;
        ceilingTexelsPacked  = generateCeilingTexture(ceilingTextureWidth, ceilingTextureHeight);

        // Use up to 4 threads (including the main GL thread acting as one worker).
        workerCount = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
        phaser      = new Phaser(workerCount);

        if (workerCount > 1) {
            workerPool = Executors.newFixedThreadPool(workerCount - 1);
            workers    = new FloorRowWorker[workerCount - 1];
            for (int workerIndex = 0; workerIndex < workers.length; workerIndex++) {
                workers[workerIndex] = new FloorRowWorker();
            }
        } else {
            workerPool = null;
            workers    = new FloorRowWorker[0];
        }
    }

    // Procedural dark concrete floor texture: base dark gray with subtle grid lines.
    private static int[] generateFloorTexture(int width, int height) {
        int[] pixels = new int[width * height];
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                boolean isGridLine = (column == 0) || (row == 0);
                int baseR = isGridLine ? 0x28 : 0x20;
                int baseG = isGridLine ? 0x28 : 0x20;
                int baseB = isGridLine ? 0x2A : 0x22;
                // Subtle noise: alternate a couple of shades in a tile pattern.
                int noisePattern = ((column / 8 + row / 8) & 1) == 0 ? 0 : 4;
                pixels[row * width + column] = ((baseR + noisePattern) << 24)
                                             | ((baseG + noisePattern) << 16)
                                             | ((baseB + noisePattern) <<  8)
                                             | 0xFF;
            }
        }
        return pixels;
    }

    // Procedural dark-navy ceiling texture: dark base with lighter rectangular fixture.
    private static int[] generateCeilingTexture(int width, int height) {
        int[] pixels = new int[width * height];
        int fixtureLeft   = width  / 4;
        int fixtureRight  = width  * 3 / 4;
        int fixtureTop    = height / 4;
        int fixtureBottom = height * 3 / 4;
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                boolean inFixture = column >= fixtureLeft && column < fixtureRight
                                 && row    >= fixtureTop  && row    < fixtureBottom;
                int baseR = inFixture ? 0x30 : 0x14;
                int baseG = inFixture ? 0x30 : 0x14;
                int baseB = inFixture ? 0x38 : 0x20;
                pixels[row * width + column] = (baseR << 24) | (baseG << 16) | (baseB << 8) | 0xFF;
            }
        }
        return pixels;
    }

    /**
     * Applies the runtime render-resolution setting (checkpoint contract C5). A no-op when the tier is
     * unchanged; otherwise disposes the current backdrop Pixmap/Texture and reallocates them (plus the
     * CPU backbuffer) at the new tier's dimensions, and recomputes {@link #rowsPerBackdropRow}. Must run
     * on the render thread (Pixmap/Texture construction) — never call this from render() itself; World
     * calls it from {@code applyStoryAccessibilitySettings()}, not per frame.
     */
    public void setRenderResolution(RenderResolution renderResolution) {
        if (this.renderResolution == renderResolution) return;
        this.renderResolution = renderResolution;
        this.backdropWidth    = renderResolution.getFloorBackdropWidth();
        this.backdropHeight   = renderResolution.getFloorBackdropHeight();
        this.rowsPerBackdropRow = WORLD_HEIGHT / (float) backdropHeight;

        backdropPixmap.dispose();
        backdropTexture.dispose();
        backdropPixmap  = new Pixmap(backdropWidth, backdropHeight, Pixmap.Format.RGBA8888);
        backdropTexture = new Texture(backdropPixmap);
        backbuffer      = new int[backdropWidth * backdropHeight];
    }

    public void setPlayerState(float worldX, float worldY,
                               float playerDirectionX, float playerDirectionY,
                               float playerFieldOfViewRadians) {
        this.playerWorldX       = worldX;
        this.playerWorldY       = worldY;
        this.directionX         = playerDirectionX;
        this.directionY         = playerDirectionY;
        this.fieldOfViewRadians = playerFieldOfViewRadians;
    }

    public void setAlertPulse(float pulse) {
        this.alertPulse = pulse;
    }

    public void setLightingTime(float timeSeconds) {
        this.lightingTimeSeconds = timeSeconds;
    }

    @Override
    public void render(OrthographicCamera camera) {
        // Compute ray geometry once and store in fields visible to worker threads.
        // Java executor guarantees happens-before between execute() and task start,
        // so workers safely read these fields without additional synchronisation.
        cachedPlayerTileX = playerWorldX / CELL_SIZE;
        cachedPlayerTileY = playerWorldY / CELL_SIZE;

        float planeScale = (float) Math.tan(fieldOfViewRadians / 2.0);
        float planeX = GameMath.cameraPlaneX(directionY, planeScale);
        float planeY = GameMath.cameraPlaneY(directionX, planeScale);

        cachedRayDirLeftX  = directionX + planeX;
        cachedRayDirLeftY  = directionY + planeY;
        cachedRayDirRightX = directionX - planeX;
        cachedRayDirRightY = directionY - planeY;

        int backdropHorizonRow = backdropHeight / 2;

        if (workerCount > 1) {
            int rowsPerWorker = backdropHorizonRow / workerCount;
            for (int workerIndex = 0; workerIndex < workers.length; workerIndex++) {
                int startRow = workerIndex * rowsPerWorker;
                int endRow   = startRow + rowsPerWorker;
                workers[workerIndex].configure(startRow, endRow);
                workerPool.execute(workers[workerIndex]);
            }
            // Main thread handles the last chunk (includes any remainder rows).
            fillRows((workers.length) * rowsPerWorker, backdropHorizonRow);
            // Arrive and wait for all background workers to finish.
            phaser.arriveAndAwaitAdvance();
        } else {
            fillRows(0, backdropHorizonRow);
        }

        // Fill the exact horizon row (if any) with the floor ambient colour.
        if (backdropHorizonRow * 2 < backdropHeight) {
            int horizonRow = backdropHeight / 2;
            for (int drawX = 0; drawX < backdropWidth; drawX++) {
                backbuffer[horizonRow * backdropWidth + drawX] = FLOOR_AMBIENT_COLOUR_PACKED;
            }
        }

        // Upload backbuffer → Pixmap → Texture (must happen on GL thread).
        IntBuffer pixelBuffer = backdropPixmap.getPixels().asIntBuffer();
        pixelBuffer.position(0);
        pixelBuffer.put(backbuffer);
        pixelBuffer.position(0);
        backdropTexture.draw(backdropPixmap, 0, 0);

        // Draw the backdrop stretched to fill the full screen.
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        batch.draw(backdropTexture, 0, 0, WORLD_WIDTH, WORLD_HEIGHT);
        batch.end();
    }

    /**
     * Fills backbuffer rows [startRow, endRow) for both the floor (bottom half)
     * and their mirrored ceiling rows (top half). Called from the main thread and
     * from pre-allocated worker threads concurrently; each thread owns a disjoint
     * row range so no synchronisation is needed on backbuffer writes.
     */
    private void fillRows(int startRow, int endRow) {
        // Texture dimensions are powers of 2 (64), so bitmask wrapping is safe and branchless.
        final int floorTexWidthMask  = floorTextureWidth  - 1;
        final int floorTexHeightMask = floorTextureHeight - 1;
        final int ceilTexWidthMask   = ceilingTextureWidth  - 1;
        final int ceilTexHeightMask  = ceilingTextureHeight - 1;

        for (int drawY = startRow; drawY < endRow; drawY++) {
            // Float-safe row->full-screen mapping (checkpoint contract C5): rowsPerBackdropRow =
            // WORLD_HEIGHT / backdropHeight, which is no longer always an integer at a non-HD tier (the
            // old FLOOR_BACKDROP_SCALE_DIVISOR constant assumed exactly 4). At HD this is 4.0f exactly.
            float drawYFullScreen = drawY * rowsPerBackdropRow;
            float pixelOffset     = GameMath.floorPixelOffsetBelowHorizon(drawYFullScreen, WORLD_HEIGHT);

            float rowDistance = GameMath.floorRowDistance(pixelOffset, WORLD_HEIGHT);
            rowDistance = Math.min(rowDistance, FLOOR_MAX_VISIBLE_DISTANCE_CELLS);

            float floorStepX = GameMath.floorStepTileComponent(
                    rowDistance, cachedRayDirRightX, cachedRayDirLeftX, backdropWidth);
            float floorStepY = GameMath.floorStepTileComponent(
                    rowDistance, cachedRayDirRightY, cachedRayDirLeftY, backdropWidth);

            float floorTileX = GameMath.floorOriginTileComponent(cachedPlayerTileX, rowDistance, cachedRayDirLeftX);
            float floorTileY = GameMath.floorOriginTileComponent(cachedPlayerTileY, rowDistance, cachedRayDirLeftY);

            float shade = GameMath.floorShade(rowDistance, FLOOR_SHADING_FALLOFF);

            // Pre-compute base backbuffer offsets for this row pair to avoid repeated multiplications.
            int floorRowOffset   = (backdropHeight - 1 - drawY) * backdropWidth;
            int ceilingRowOffset = drawY * backdropWidth;

            for (int drawX = 0; drawX < backdropWidth; drawX++) {
                // Compute integer tile and UV fractional part in one step, avoiding
                // a second Math.floor() call for the texel index derivation.
                int   tileColumn = MathUtils.floor(floorTileX);
                int   tileRow    = MathUtils.floor(floorTileY);
                float fracX      = floorTileX - tileColumn;
                float fracY      = floorTileY - tileRow;

                // Bitmask wrap is equivalent to modulo for power-of-2 sizes and avoids
                // clamping: (int)(frac * 64) can be 64 for frac==1.0, but 64 & 63 = 0.
                int floorTexelColumn = (int)(fracX * floorTextureWidth)  & floorTexWidthMask;
                int floorTexelRow    = (int)(fracY * floorTextureHeight) & floorTexHeightMask;
                int floorRawPixel    = floorTexelsPacked[floorTexelRow * floorTextureWidth + floorTexelColumn];

                int ceilTexelColumn  = (int)(fracX * ceilingTextureWidth)  & ceilTexWidthMask;
                int ceilTexelRow     = (int)(fracY * ceilingTextureHeight) & ceilTexHeightMask;
                int ceilRawPixel     = ceilingTexelsPacked[ceilTexelRow * ceilingTextureWidth + ceilTexelColumn];

                // Tile brightness is the same for both the floor and its mirrored ceiling row.
                float tileBrightness     = level.getTileBrightness(tileColumn, tileRow, lightingTimeSeconds);
                float adjustedShade      = Math.min(shade * tileBrightness, MAX_LIGHTING_SHADE);

                int floorPixel = GameMath.applyShadeToPackedRGBA(
                        floorRawPixel, adjustedShade,
                        ALERT_WALL_RED_BOOST, ALERT_WALL_GB_DAMPEN, alertPulse);
                int ceilPixel = GameMath.applyShadeToPackedRGBA(
                        ceilRawPixel, adjustedShade,
                        ALERT_CEILING_TINT_STRENGTH, ALERT_WALL_GB_DAMPEN, alertPulse);

                backbuffer[floorRowOffset   + drawX] = floorPixel;
                backbuffer[ceilingRowOffset + drawX] = ceilPixel;

                floorTileX += floorStepX;
                floorTileY += floorStepY;
            }
        }
    }

    /** Pre-allocated task submitted to the thread pool once per frame per background worker. */
    private final class FloorRowWorker implements Runnable {
        private int startRow;
        private int endRow;

        void configure(int startRow, int endRow) {
            this.startRow = startRow;
            this.endRow   = endRow;
        }

        @Override
        public void run() {
            try {
                fillRows(startRow, endRow);
            } finally {
                phaser.arrive();
            }
        }
    }

    @Override
    public void dispose() {
        batch.dispose();
        backdropPixmap.dispose();
        backdropTexture.dispose();
        if (workerPool != null) {
            workerPool.shutdown();
        }
    }
}
