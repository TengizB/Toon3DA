package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.enemy.EnemyManager;
import ge.tbegvadze.toon3d.enemy.EnemyState;
import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ge.tbegvadze.toon3d.util.Constants.*;

/**
 * Renders living enemies as distance-sorted, z-buffer-occluded billboard sprites.
 * Two-pass design: pass 1 draws enemy sprites (with hit-flash tint); pass 2 draws
 * health bars (white-pixel texture only, collapses to ~3 texture switches regardless
 * of enemy count). Zero per-frame allocations — all scratch arrays pre-allocated.
 */
public final class EnemyRenderer implements Renderable, Disposable {

    private final EnemyManager            enemyManager;
    private final WallRenderer            wallRenderer;
    private final Map<EnemyType, Texture> textures;
    private final Texture                 whitePixelTexture;
    private final SpriteBatch             batch;

    // Pre-allocated depth-sort scratch arrays — sized at construction to initial enemy count
    private final int[]     sortedIndices;
    private final float[]   sortedDepths;

    // Pre-allocated bar geometry scratch arrays for pass 2 — parallel to sortedIndices
    private final float[]   barLeftPositions;
    private final float[]   barBottomPositions;
    private final float[]   barWidths;
    private final float[]   barHeights;
    private final float[]   barFillFractions;
    private final boolean[] drawBarFlags;

    // Reused per-frame colour buffer written by GameMath.healthBarColor — no allocation
    private final float[]   barColorRgb = new float[3];

    private float playerWorldX = 0f;
    private float playerWorldY = 0f;
    private float directionX   = 1f;
    private float directionY   = 0f;
    private float planeX       = 0f;
    private float planeY       = 1f;
    private float alertPulse   = 0f;

    public EnemyRenderer(EnemyManager enemyManager, WallRenderer wallRenderer) {
        this.enemyManager       = enemyManager;
        this.wallRenderer       = wallRenderer;
        int initialCount        = enemyManager.getEnemies().size();
        int scratchSize         = Math.max(1, initialCount);
        this.sortedIndices      = new int[scratchSize];
        this.sortedDepths       = new float[scratchSize];
        this.barLeftPositions   = new float[scratchSize];
        this.barBottomPositions = new float[scratchSize];
        this.barWidths          = new float[scratchSize];
        this.barHeights         = new float[scratchSize];
        this.barFillFractions   = new float[scratchSize];
        this.drawBarFlags       = new boolean[scratchSize];
        this.batch              = new SpriteBatch(WALL_PROJECTION_SCREEN_WIDTH);
        this.textures           = buildTextures();
        this.whitePixelTexture  = buildWhitePixelTexture();
    }

    public void setPlayerState(float worldX, float worldY,
                               float playerDirectionX, float playerDirectionY,
                               float fieldOfViewRadians) {
        this.playerWorldX = worldX;
        this.playerWorldY = worldY;
        this.directionX   = playerDirectionX;
        this.directionY   = playerDirectionY;
        float planeScale  = (float) Math.tan(fieldOfViewRadians / 2.0);
        this.planeX       = GameMath.cameraPlaneX(playerDirectionY, planeScale);
        this.planeY       = GameMath.cameraPlaneY(playerDirectionX, planeScale);
    }

    public void setAlertPulse(float pulse) {
        this.alertPulse = pulse;
    }

    @Override
    public void render(OrthographicCamera camera) {
        List<Enemy> enemies = enemyManager.getEnemies();
        int enemyCount = enemies.size();
        if (enemyCount == 0) return;

        // --- Cull enemies behind the player or too far ---
        int visibleCount = 0;
        for (int enemyIndex = 0; enemyIndex < enemyCount; enemyIndex++) {
            Enemy enemy = enemies.get(enemyIndex);
            if (!enemy.isAlive()) continue;

            float tileOffsetX = (enemy.worldCenterX() - playerWorldX) / CELL_SIZE;
            float tileOffsetY = (enemy.worldCenterY() - playerWorldY) / CELL_SIZE;
            float depth       = GameMath.spriteDepth(tileOffsetX, tileOffsetY, directionX, directionY);
            if (depth <= PROP_BEHIND_PLAYER_EPSILON_TILES) continue;
            if (depth > MAX_ENEMY_DRAW_DISTANCE_TILES)     continue;

            if (visibleCount < sortedIndices.length) {
                sortedIndices[visibleCount] = enemyIndex;
                sortedDepths[visibleCount]  = depth;
                visibleCount++;
            }
        }

        if (visibleCount == 0) return;

        // --- Insertion-sort farthest-first (painter's algorithm) ---
        for (int sortPass = 1; sortPass < visibleCount; sortPass++) {
            int   insertIndex = sortedIndices[sortPass];
            float insertDepth = sortedDepths[sortPass];
            int   insertAt    = sortPass - 1;
            while (insertAt >= 0 && sortedDepths[insertAt] < insertDepth) {
                sortedIndices[insertAt + 1] = sortedIndices[insertAt];
                sortedDepths[insertAt + 1]  = sortedDepths[insertAt];
                insertAt--;
            }
            sortedIndices[insertAt + 1] = insertIndex;
            sortedDepths[insertAt + 1]  = insertDepth;
        }

        batch.setProjectionMatrix(camera.combined);

        // =====================================================================
        // Pass 1: Enemy sprites with hit-flash tint
        // =====================================================================
        batch.begin();

        for (int sortedPosition = 0; sortedPosition < visibleCount; sortedPosition++) {
            int   enemyIndex = sortedIndices[sortedPosition];
            float depth      = sortedDepths[sortedPosition];
            Enemy enemy      = enemies.get(enemyIndex);

            drawBarFlags[sortedPosition] = false;

            Texture texture = textures.get(enemy.type);
            if (texture == null) continue;

            float tileOffsetX = (enemy.worldCenterX() - playerWorldX) / CELL_SIZE;
            float tileOffsetY = (enemy.worldCenterY() - playerWorldY) / CELL_SIZE;

            float screenCenterColumn = GameMath.spriteScreenColumnCenter(
                    tileOffsetX, tileOffsetY, directionX, directionY,
                    planeX, planeY, WALL_PROJECTION_SCREEN_WIDTH);

            float heightMultiplier   = enemy.type.heightMultiplier();
            float fullWallLineHeight = GameMath.spriteScreenHeight(WALL_PROJECTION_SCREEN_HEIGHT, depth);
            float spriteScreenHeight = fullWallLineHeight * heightMultiplier;
            float aspectRatio        = (float) texture.getWidth() / texture.getHeight();
            float spriteScreenWidth  = spriteScreenHeight * aspectRatio;

            int leftScreenColumn  = (int)(screenCenterColumn - spriteScreenWidth / 2f);
            int rightScreenColumn = (int)(screenCenterColumn + spriteScreenWidth / 2f);
            int columnSpan        = rightScreenColumn - leftScreenColumn;
            if (columnSpan <= 0) continue;

            float drawBottom = GameMath.wallStripeDrawBottom(WALL_PROJECTION_SCREEN_HEIGHT, fullWallLineHeight);
            if (enemy.type == EnemyType.VORTEX_EYE) {
                drawBottom += fullWallLineHeight * VORTEX_EYE_HOVER_OFFSET_FRACTION;
            }
            float drawTop = drawBottom + spriteScreenHeight;

            float clampedBottom = Math.max(0f, drawBottom);
            float clampedTop    = Math.min((float) WALL_PROJECTION_SCREEN_HEIGHT, drawTop);
            if (clampedTop <= clampedBottom) continue;

            int textureWidth  = texture.getWidth();
            int textureHeight = texture.getHeight();
            int texSrcY       = GameMath.wallTextureClipSrcY(
                                    drawTop, WALL_PROJECTION_SCREEN_HEIGHT,
                                    spriteScreenHeight, textureHeight);
            int texSrcHeight  = GameMath.wallTextureClipSrcHeight(
                                    clampedTop, clampedBottom,
                                    spriteScreenHeight, textureHeight);
            texSrcHeight = Math.min(texSrcHeight, textureHeight - texSrcY);
            texSrcHeight = Math.max(1, texSrcHeight);

            // Base shade: dormant enemies are darker so they visibly "light up" when alerted
            float baseShade = GameMath.wallShade(depth, WALL_SHADING_FALLOFF);
            if (enemy.state == EnemyState.DORMANT) {
                baseShade *= DORMANT_SHADE_DAMPEN;
            }
            float baseRed   = Math.min(1f, baseShade * (1f + alertPulse * ALERT_WALL_RED_BOOST));
            float baseGreen = baseShade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
            float baseBlue  = baseShade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);

            // Hit flash: lerp the shade toward white — flashes the sprite silhouette
            // (SpriteBatch tinting respects per-texel alpha, so only opaque pixels whiten)
            float hitFlashStrength = enemy.getHitFlashStrength();
            float spriteRed   = GameMath.lerpTowardWhite(baseRed,   hitFlashStrength);
            float spriteGreen = GameMath.lerpTowardWhite(baseGreen, hitFlashStrength);
            float spriteBlue  = GameMath.lerpTowardWhite(baseBlue,  hitFlashStrength);
            batch.setColor(spriteRed, spriteGreen, spriteBlue, 1f);

            int firstColumn = Math.max(0, leftScreenColumn);
            int lastColumn  = Math.min(WALL_PROJECTION_SCREEN_WIDTH - 1, rightScreenColumn);
            for (int screenColumn = firstColumn; screenColumn <= lastColumn; screenColumn++) {
                if (depth >= wallRenderer.getZBufferUnchecked(screenColumn)) continue;

                int texSrcX = (screenColumn - leftScreenColumn) * textureWidth / columnSpan;
                texSrcX = MathUtils.clamp(texSrcX, 0, textureWidth - 1);

                batch.draw(texture,
                           screenColumn * WALL_COLUMN_WIDTH, clampedBottom,
                           WALL_COLUMN_WIDTH, clampedTop - clampedBottom,
                           texSrcX, texSrcY, 1, texSrcHeight,
                           false, false);
            }

            // Cache bar geometry for pass 2; only for alerted enemies within bar draw distance
            if (enemy.isAlerted() && depth <= ENEMY_HEALTH_BAR_MAX_DISTANCE_TILES) {
                float barWidth          = spriteScreenWidth * ENEMY_HEALTH_BAR_WIDTH_FRACTION;
                float barHeight         = Math.max(ENEMY_HEALTH_BAR_MIN_PIXELS,
                                                   spriteScreenHeight * ENEMY_HEALTH_BAR_HEIGHT_FRACTION);
                float barLeft           = screenCenterColumn - barWidth / 2f;
                float barBottomPosition = drawTop + spriteScreenHeight * ENEMY_HEALTH_BAR_GAP_FRACTION;
                float fillFraction      = Math.max(0f, Math.min(1f,
                                              (float) enemy.health / enemy.type.maxHealth()));

                barLeftPositions[sortedPosition]   = barLeft;
                barBottomPositions[sortedPosition] = barBottomPosition;
                barWidths[sortedPosition]          = barWidth;
                barHeights[sortedPosition]         = barHeight;
                barFillFractions[sortedPosition]   = fillFraction;
                drawBarFlags[sortedPosition]       = true;
            }
        }

        batch.setColor(Color.WHITE);
        batch.end();

        // =====================================================================
        // Pass 2: Health bars (white-pixel texture; single batch, ~3 flushes total)
        // Drawn over all sprites so near-enemy bars are never hidden by far-enemy bodies.
        // Per-enemy z-test at the bar's horizontal center keeps bars behind walls.
        // =====================================================================
        boolean anyBarsToRender = false;
        for (int sortedPosition = 0; sortedPosition < visibleCount; sortedPosition++) {
            if (drawBarFlags[sortedPosition]) {
                anyBarsToRender = true;
                break;
            }
        }

        if (anyBarsToRender) {
            batch.begin();
            for (int sortedPosition = 0; sortedPosition < visibleCount; sortedPosition++) {
                if (!drawBarFlags[sortedPosition]) continue;

                float depth        = sortedDepths[sortedPosition];
                float barLeft      = barLeftPositions[sortedPosition];
                float barBottom    = barBottomPositions[sortedPosition];
                float barWidth     = barWidths[sortedPosition];
                float barHeight    = barHeights[sortedPosition];
                float fillFraction = barFillFractions[sortedPosition];

                // Single centre-column z-test: bar appears/disappears as enemy rounds a corner
                int barCenterColumn = (int)(barLeft + barWidth / 2f);
                if (barCenterColumn < 0 || barCenterColumn >= WALL_PROJECTION_SCREEN_WIDTH) continue;
                if (depth >= wallRenderer.getZBufferUnchecked(barCenterColumn)) continue;

                float borderPixels = ENEMY_HEALTH_BAR_BORDER_PIXELS;

                // Layer 1: Border/backdrop — semi-transparent dark frame so the bar reads on bright walls
                batch.setColor(ENEMY_HEALTH_BAR_BORDER_RED, ENEMY_HEALTH_BAR_BORDER_GREEN,
                               ENEMY_HEALTH_BAR_BORDER_BLUE, ENEMY_HEALTH_BAR_BORDER_ALPHA);
                batch.draw(whitePixelTexture,
                           barLeft - borderPixels, barBottom - borderPixels,
                           barWidth + 2f * borderPixels, barHeight + 2f * borderPixels,
                           0, 0, 1, 1, false, false);

                // Layer 2: Empty track — full-width dark red-gray shows missing health
                batch.setColor(ENEMY_HEALTH_BAR_TRACK_RED, ENEMY_HEALTH_BAR_TRACK_GREEN,
                               ENEMY_HEALTH_BAR_TRACK_BLUE, 1f);
                batch.draw(whitePixelTexture, barLeft, barBottom, barWidth, barHeight,
                           0, 0, 1, 1, false, false);

                // Layer 3: Health fill — green→yellow→red gradient, grows from the left
                float fillWidth = barWidth * fillFraction;
                if (fillWidth > 0.5f) {
                    GameMath.healthBarColor(fillFraction, barColorRgb);
                    batch.setColor(barColorRgb[0], barColorRgb[1], barColorRgb[2], 1f);
                    batch.draw(whitePixelTexture, barLeft, barBottom, fillWidth, barHeight,
                               0, 0, 1, 1, false, false);
                }
            }
            batch.setColor(Color.WHITE);
            batch.end();
        }
    }

    @Override
    public void dispose() {
        batch.dispose();
        whitePixelTexture.dispose();
        for (Texture texture : textures.values()) {
            texture.dispose();
        }
    }

    // -------------------------------------------------------------------------
    // Texture loading
    // -------------------------------------------------------------------------

    private static Map<EnemyType, Texture> buildTextures() {
        Map<EnemyType, Texture> map = new HashMap<>();
        map.put(EnemyType.CORRUPTOR,  loadOrGenerateFallback(ENEMY_CORRUPTOR_PATH,  generateFallback(0.15f, 0.55f, 0.15f)));
        map.put(EnemyType.VORTEX_EYE, loadOrGenerateFallback(ENEMY_VORTEX_EYE_PATH, generateFallback(0.50f, 0.10f, 0.70f)));
        return map;
    }

    private static Texture loadOrGenerateFallback(String assetPath, Texture fallback) {
        if (Gdx.files.internal(assetPath).exists()) {
            fallback.dispose();
            Texture loaded = new Texture(Gdx.files.internal(assetPath));
            loaded.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            return loaded;
        }
        return fallback;
    }

    private static Texture generateFallback(float red, float green, float blue) {
        Pixmap pixmap = new Pixmap(64, 96, Pixmap.Format.RGBA8888);
        pixmap.setColor(red, green, blue, 1f);
        pixmap.fill();
        // Simple eyes pattern
        pixmap.setColor(1f, 1f, 0f, 1f);
        pixmap.fillRectangle(12, 28, 14, 14);
        pixmap.fillRectangle(38, 28, 14, 14);
        pixmap.setColor(0f, 0f, 0f, 1f);
        pixmap.fillRectangle(16, 32, 6, 6);
        pixmap.fillRectangle(42, 32, 6, 6);
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }

    private static Texture buildWhitePixelTexture() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(1f, 1f, 1f, 1f);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }
}
