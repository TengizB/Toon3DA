package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.GameMath;

import java.nio.IntBuffer;

import static ge.tbegvadze.toon3d.util.Constants.*;

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
 * Y-up adaptations vs. Lodev (Y-down):
 *   - pixelOffset = screenHeight/2 − drawY  (Y-up; Lodev uses drawY − screenHeight/2)
 *   - rayDirLeft  = direction + plane        (column 0 → cameraParameter = +1 in Y-up)
 *   - rayDirRight = direction − plane        (column W-1 → cameraParameter ≈ −1)
 *   - Floor pixel at Y-up row drawY → backbuffer row (backdropHeight-1-drawY) so
 *     that image row 0 (visual top) maps to the visual top when LibGDX renders it.
 *
 * Resolution:
 *   Backdrop is FLOOR_BACKDROP_WIDTH × FLOOR_BACKDROP_HEIGHT
 *   (= WORLD_WIDTH/2 × WORLD_HEIGHT/2 by default). SpriteBatch upscales it to
 *   the full viewport, giving a 2× pixel size that also looks intentionally retro.
 */
public class FloorCeilingRenderer implements Renderable, Disposable {

    private final Level level;
    private final SpriteBatch batch;
    private final Pixmap backdropPixmap;
    private final Texture backdropTexture;
    private final int[] backbuffer;

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

    public FloorCeilingRenderer(Level level) {
        this.level = level;
        batch = new SpriteBatch(1);

        backdropPixmap   = new Pixmap(FLOOR_BACKDROP_WIDTH, FLOOR_BACKDROP_HEIGHT, Pixmap.Format.RGBA8888);
        backdropTexture  = new Texture(backdropPixmap);
        backbuffer       = new int[FLOOR_BACKDROP_WIDTH * FLOOR_BACKDROP_HEIGHT];

        floorTextureWidth    = 64;
        floorTextureHeight   = 64;
        floorTexelsPacked    = generateFloorTexture(floorTextureWidth, floorTextureHeight);

        ceilingTextureWidth  = 64;
        ceilingTextureHeight = 64;
        ceilingTexelsPacked  = generateCeilingTexture(ceilingTextureWidth, ceilingTextureHeight);
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
        float playerTileX = playerWorldX / CELL_SIZE;
        float playerTileY = playerWorldY / CELL_SIZE;

        float planeScale = (float) Math.tan(fieldOfViewRadians / 2.0);
        // Camera-plane vectors (Y-up convention, same as WallRenderer).
        float planeX = GameMath.cameraPlaneX(directionY, planeScale);
        float planeY = GameMath.cameraPlaneY(directionX, planeScale);

        // In Y-up, column 0 → cameraParameter = +1 → leftmost ray = direction + plane.
        // Column W-1 → cameraParameter ≈ −1 → rightmost ray = direction − plane.
        float rayDirLeftX  = directionX + planeX;
        float rayDirLeftY  = directionY + planeY;
        float rayDirRightX = directionX - planeX;
        float rayDirRightY = directionY - planeY;

        // Iterate over floor rows in the backdrop (backdrop height/2 rows).
        // drawY is in backdrop coordinates (Y-up: 0 = bottom of backdrop).
        int backdropHorizonRow = FLOOR_BACKDROP_HEIGHT / 2;

        for (int drawY = 0; drawY < backdropHorizonRow; drawY++) {
            // pixelOffset: distance in pixels below the horizon in FULL-screen space.
            // We scale drawY to full-screen space: drawY_full = drawY * FLOOR_BACKDROP_SCALE_DIVISOR.
            int drawYFullScreen = drawY * FLOOR_BACKDROP_SCALE_DIVISOR;
            int pixelOffset = GameMath.floorPixelOffsetBelowHorizon(drawYFullScreen, WORLD_HEIGHT);

            float rowDistance = GameMath.floorRowDistance(pixelOffset, WORLD_HEIGHT);
            // Clamp to max visible distance to avoid extremely large values near the horizon.
            rowDistance = Math.min(rowDistance, FLOOR_MAX_VISIBLE_DISTANCE_CELLS);

            float floorStepX = GameMath.floorStepTileComponent(
                    rowDistance, rayDirRightX, rayDirLeftX, FLOOR_BACKDROP_WIDTH);
            float floorStepY = GameMath.floorStepTileComponent(
                    rowDistance, rayDirRightY, rayDirLeftY, FLOOR_BACKDROP_WIDTH);

            float floorTileX = GameMath.floorOriginTileComponent(playerTileX, rowDistance, rayDirLeftX);
            float floorTileY = GameMath.floorOriginTileComponent(playerTileY, rowDistance, rayDirLeftY);

            float shade = GameMath.floorShade(rowDistance, FLOOR_SHADING_FALLOFF);

            for (int drawX = 0; drawX < FLOOR_BACKDROP_WIDTH; drawX++) {
                // Integer tile coordinates for this pixel — needed for tile-brightness lookup.
                int floorTileColumn = (int) Math.floor(floorTileX);
                int floorTileRow    = (int) Math.floor(floorTileY);

                int floorTexelColumn = GameMath.floorTexelIndex(floorTileX, floorTextureWidth);
                int floorTexelRow    = GameMath.floorTexelIndex(floorTileY, floorTextureHeight);
                int floorRawPixel    = floorTexelsPacked[floorTexelRow * floorTextureWidth + floorTexelColumn];

                int ceilTexelColumn = GameMath.floorTexelIndex(floorTileX, ceilingTextureWidth);
                int ceilTexelRow    = GameMath.floorTexelIndex(floorTileY, ceilingTextureHeight);
                int ceilRawPixel    = ceilingTexelsPacked[ceilTexelRow * ceilingTextureWidth + ceilTexelColumn];

                // Per-tile brightness from the floor tile directly below/above this pixel.
                float tileBrightness      = level.getTileBrightness(floorTileColumn, floorTileRow, lightingTimeSeconds);
                float floorShadeAdjusted  = Math.min(shade * tileBrightness, MAX_LIGHTING_SHADE);

                int floorPixel = GameMath.applyShadeToPackedRGBA(
                        floorRawPixel, floorShadeAdjusted, ALERT_WALL_RED_BOOST, ALERT_WALL_GB_DAMPEN, alertPulse);

                float ceilShadeAdjusted = Math.min(shade * tileBrightness, MAX_LIGHTING_SHADE);
                int ceilPixel = GameMath.applyShadeToPackedRGBA(
                        ceilRawPixel, ceilShadeAdjusted,
                        ALERT_CEILING_TINT_STRENGTH, ALERT_WALL_GB_DAMPEN, alertPulse);

                // Y-up image indexing: backbuffer row 0 = image top = visual top.
                // Floor Y-up row drawY → image row (backdropHeight-1-drawY) → visual bottom half.
                // Ceiling mirror row (backdropHeight-1-drawY) → image row drawY → visual top half.
                backbuffer[(FLOOR_BACKDROP_HEIGHT - 1 - drawY) * FLOOR_BACKDROP_WIDTH + drawX] = floorPixel;
                backbuffer[drawY * FLOOR_BACKDROP_WIDTH + drawX]                               = ceilPixel;

                floorTileX += floorStepX;
                floorTileY += floorStepY;
            }
        }

        // Fill the exact horizon row (if any) with the floor ambient colour.
        if (backdropHorizonRow * 2 < FLOOR_BACKDROP_HEIGHT) {
            int horizonRow = FLOOR_BACKDROP_HEIGHT / 2;
            for (int drawX = 0; drawX < FLOOR_BACKDROP_WIDTH; drawX++) {
                backbuffer[horizonRow * FLOOR_BACKDROP_WIDTH + drawX] = FLOOR_AMBIENT_COLOUR_PACKED;
            }
        }

        // Upload backbuffer → Pixmap → Texture.
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

    @Override
    public void dispose() {
        batch.dispose();
        backdropPixmap.dispose();
        backdropTexture.dispose();
    }
}
