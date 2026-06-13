package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.enemy.EnemyManager;
import ge.tbegvadze.toon3d.enemy.EnemyState;
import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.status.StatusEffect;
import ge.tbegvadze.toon3d.status.StatusType;
import ge.tbegvadze.toon3d.util.EffectConstants;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ge.tbegvadze.toon3d.util.Constants.*;
import static ge.tbegvadze.toon3d.util.EnemyConstants.*;
import static ge.tbegvadze.toon3d.util.RenderConstants.*;

/**
 * Renders living enemies as distance-sorted, z-buffer-occluded billboard sprites.
 * Two-pass design: pass 1 draws enemy sprites (with hit-flash tint); pass 2 draws
 * health bars (white-pixel texture only, collapses to ~3 texture switches regardless
 * of enemy count). Zero per-frame allocations — all scratch arrays pre-allocated.
 *
 * Sprites are sourced from two 2×2 sprite sheets (blight and infernal). Each sheet
 * is split into four equal TextureRegions at load time; EnemyType maps to its region.
 */
public final class EnemyRenderer implements Renderable, Disposable {

    private final EnemyManager                  enemyManager;
    private final WallRenderer                  wallRenderer;
    private       PropRenderer                  propRenderer  = null;
    private final Map<EnemyType, TextureRegion> textureRegions;
    private final Texture                       blightSheetTexture;
    private final Texture                       infernalSheetTexture;
    private final Texture                       whitePixelTexture;
    private final SpriteBatch                   batch;

    // Pre-allocated depth-sort scratch arrays — sized at construction to initial enemy count
    private final int[]     sortedIndices;
    private final float[]   sortedDepths;

    // Pre-allocated bar geometry scratch arrays for pass 2 — parallel to sortedIndices
    private final float[]   barLeftPositions;
    private final float[]   barBottomPositions;
    private final float[]   barWidths;
    private final float[]   barHeights;
    private final float[]   barFillFractions;
    private final int[]     barHealthCurrents;
    private final int[]     barHealthMaxes;
    private final boolean[] drawBarFlags;

    // Reused per-frame colour buffer written by GameMath.healthBarColor — no allocation
    private final float[]   barColorRgb = new float[3];

    // Name tag and HP text rendering — font and layouts pre-allocated to avoid render-loop allocation
    private final BitmapFont  nameTagFont;
    private final GlyphLayout nameTagLayout;
    private final GlyphLayout hpTextLayout;
    private final StringBuilder hpTextBuilder = new StringBuilder(8);
    // Reusable scratch color for name tag tinting — never allocated inside render()
    private final Color       nameTagColor = new Color();

    private float playerWorldX = 0f;
    private float playerWorldY = 0f;
    private float directionX   = 1f;
    private float directionY   = 0f;
    private float planeX       = 0f;
    private float planeY       = 1f;
    private float alertPulse   = 0f;

    public EnemyRenderer(EnemyManager enemyManager, WallRenderer wallRenderer) {
        this.enemyManager        = enemyManager;
        this.wallRenderer        = wallRenderer;
        int initialCount         = enemyManager.getEnemies().size();
        int scratchSize          = Math.max(1, initialCount);
        this.sortedIndices       = new int[scratchSize];
        this.sortedDepths        = new float[scratchSize];
        this.barLeftPositions    = new float[scratchSize];
        this.barBottomPositions  = new float[scratchSize];
        this.barWidths           = new float[scratchSize];
        this.barHeights          = new float[scratchSize];
        this.barFillFractions    = new float[scratchSize];
        this.barHealthCurrents   = new int[scratchSize];
        this.barHealthMaxes      = new int[scratchSize];
        this.drawBarFlags        = new boolean[scratchSize];
        this.batch               = new SpriteBatch(WALL_PROJECTION_SCREEN_WIDTH);

        // Load sprite sheets; fall back to a solid-colour placeholder when the file is absent
        this.blightSheetTexture   = loadSheetOrFallback(ENEMY_SHEET_BLIGHT_PATH,   0.60f, 0.20f, 0.60f);
        this.infernalSheetTexture = loadSheetOrFallback(ENEMY_SHEET_INFERNAL_PATH, 0.70f, 0.25f, 0.10f);
        this.textureRegions       = buildTextureRegions(blightSheetTexture, infernalSheetTexture);

        this.whitePixelTexture    = buildWhitePixelTexture();
        this.nameTagFont          = new BitmapFont();
        this.nameTagFont.getData().setScale(ENEMY_NAME_TAG_FONT_SCALE);
        this.nameTagLayout        = new GlyphLayout();
        this.hpTextLayout         = new GlyphLayout();
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

    /**
     * Wires in the PropRenderer so enemy sprites are occluded by closer props.
     * PropRenderer is owned and disposed by World — EnemyRenderer holds a non-owning reference.
     */
    public void setPropRenderer(PropRenderer renderer) {
        this.propRenderer = renderer;
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

            TextureRegion region = textureRegions.get(enemy.type);
            if (region == null) continue;

            float tileOffsetX = (enemy.worldCenterX() - playerWorldX) / CELL_SIZE;
            float tileOffsetY = (enemy.worldCenterY() - playerWorldY) / CELL_SIZE;

            float screenCenterColumn = GameMath.spriteScreenColumnCenter(
                    tileOffsetX, tileOffsetY, directionX, directionY,
                    planeX, planeY, WALL_PROJECTION_SCREEN_WIDTH);

            float heightMultiplier   = enemy.type.heightMultiplier();
            float fullWallLineHeight = GameMath.spriteScreenHeight(WALL_PROJECTION_SCREEN_HEIGHT, depth);
            float spriteScreenHeight = fullWallLineHeight * heightMultiplier;
            int   regionWidth        = region.getRegionWidth();
            int   regionHeight       = region.getRegionHeight();
            float aspectRatio        = (float) regionWidth / regionHeight;
            float spriteScreenWidth  = spriteScreenHeight * aspectRatio;

            int leftScreenColumn  = (int)(screenCenterColumn - spriteScreenWidth / 2f);
            int rightScreenColumn = (int)(screenCenterColumn + spriteScreenWidth / 2f);
            int columnSpan        = rightScreenColumn - leftScreenColumn;
            if (columnSpan <= 0) continue;

            float drawBottom = GameMath.wallStripeDrawBottom(WALL_PROJECTION_SCREEN_HEIGHT, fullWallLineHeight);
            // Hovering enemies are shifted upward so they appear to float above the floor
            if (enemy.type == EnemyType.EYE_TYRANT) {
                drawBottom += fullWallLineHeight * EYE_TYRANT_HOVER_OFFSET_FRACTION;
            } else if (enemy.type == EnemyType.MIRE_WRAITH) {
                drawBottom += fullWallLineHeight * MIRE_WRAITH_HOVER_OFFSET_FRACTION;
            }
            float drawTop = drawBottom + spriteScreenHeight;

            float clampedBottom = Math.max(0f, drawBottom);
            float clampedTop    = Math.min((float) WALL_PROJECTION_SCREEN_HEIGHT, drawTop);
            if (clampedTop <= clampedBottom) continue;

            // Compute vertical clip within the region (srcY = 0 is top of region)
            int localSrcY    = GameMath.wallTextureClipSrcY(
                                    drawTop, WALL_PROJECTION_SCREEN_HEIGHT,
                                    spriteScreenHeight, regionHeight);
            int texSrcY      = region.getRegionY() + localSrcY;
            int texSrcHeight = GameMath.wallTextureClipSrcHeight(
                                    clampedTop, clampedBottom,
                                    spriteScreenHeight, regionHeight);
            texSrcHeight = Math.min(texSrcHeight, regionHeight - localSrcY);
            texSrcHeight = Math.max(1, texSrcHeight);

            // Base shade: dormant enemies are darker so they visibly "light up" when alerted
            float baseShade = GameMath.wallShade(depth, WALL_SHADING_FALLOFF);
            if (enemy.state == EnemyState.DORMANT) {
                baseShade *= DORMANT_SHADE_DAMPEN;
            }
            float baseRed   = Math.min(1f, baseShade * (1f + alertPulse * ALERT_WALL_RED_BOOST));
            float baseGreen = baseShade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);
            float baseBlue  = baseShade * (1f - alertPulse * ALERT_WALL_GB_DAMPEN);

            float hitFlashStrength = enemy.getHitFlashStrength();
            float spriteRed   = GameMath.lerpTowardWhite(baseRed,   hitFlashStrength);
            float spriteGreen = GameMath.lerpTowardWhite(baseGreen, hitFlashStrength);
            float spriteBlue  = GameMath.lerpTowardWhite(baseBlue,  hitFlashStrength);

            // Status tint: blend the dominant active effect color over the sprite.
            float tintStrength = EffectConstants.ENEMY_STATUS_TINT_STRENGTH;
            StatusEffect burnEffect   = enemy.getActiveEffects().get(StatusType.BURNING);
            StatusEffect poisonEffect = enemy.getActiveEffects().get(StatusType.POISONED);
            if (burnEffect != null && burnEffect.isActive()) {
                // Orange (1.0, 0.45, 0.0)
                spriteRed   = GameMath.lerp(spriteRed,   1.00f, tintStrength);
                spriteGreen = GameMath.lerp(spriteGreen, 0.45f, tintStrength);
                spriteBlue  = GameMath.lerp(spriteBlue,  0.00f, tintStrength);
            } else if (poisonEffect != null && poisonEffect.isActive()) {
                // Green (0.0, 0.80, 0.15)
                spriteRed   = GameMath.lerp(spriteRed,   0.00f, tintStrength);
                spriteGreen = GameMath.lerp(spriteGreen, 0.80f, tintStrength);
                spriteBlue  = GameMath.lerp(spriteBlue,  0.15f, tintStrength);
            }

            batch.setColor(spriteRed, spriteGreen, spriteBlue, 1f);

            float[] propZBuffer = (propRenderer != null) ? propRenderer.getPropSpriteZBuffer() : null;

            int firstColumn = Math.max(0, leftScreenColumn);
            int lastColumn  = Math.min(WALL_PROJECTION_SCREEN_WIDTH - 1, rightScreenColumn);
            for (int screenColumn = firstColumn; screenColumn <= lastColumn; screenColumn++) {
                if (depth >= wallRenderer.getZBufferUnchecked(screenColumn)) continue;
                if (propZBuffer != null && depth >= propZBuffer[screenColumn]) continue;

                // Map screen column to a pixel x within the region, then offset to sheet coords
                int localSrcX = (screenColumn - leftScreenColumn) * regionWidth / columnSpan;
                localSrcX = MathUtils.clamp(localSrcX, 0, regionWidth - 1);
                int texSrcX = region.getRegionX() + localSrcX;

                batch.draw(region.getTexture(),
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
                                              (float) enemy.health / enemy.maxHealth));

                barLeftPositions[sortedPosition]   = barLeft;
                barBottomPositions[sortedPosition] = barBottomPosition;
                barWidths[sortedPosition]          = barWidth;
                barHeights[sortedPosition]         = barHeight;
                barFillFractions[sortedPosition]   = fillFraction;
                barHealthCurrents[sortedPosition]  = enemy.health;
                barHealthMaxes[sortedPosition]     = enemy.maxHealth;
                drawBarFlags[sortedPosition]       = true;
            }
        }

        batch.setColor(Color.WHITE);
        batch.end();

        // =====================================================================
        // Pass 2: Health bars (white-pixel texture; single batch, ~3 flushes total)
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

                int barCenterColumn = (int)(barLeft + barWidth / 2f);
                if (barCenterColumn < 0 || barCenterColumn >= WALL_PROJECTION_SCREEN_WIDTH) continue;
                if (depth >= wallRenderer.getZBufferUnchecked(barCenterColumn)) continue;

                float borderPixels = ENEMY_HEALTH_BAR_BORDER_PIXELS;

                batch.setColor(ENEMY_HEALTH_BAR_BORDER_RED, ENEMY_HEALTH_BAR_BORDER_GREEN,
                               ENEMY_HEALTH_BAR_BORDER_BLUE, ENEMY_HEALTH_BAR_BORDER_ALPHA);
                batch.draw(whitePixelTexture,
                           barLeft - borderPixels, barBottom - borderPixels,
                           barWidth + 2f * borderPixels, barHeight + 2f * borderPixels,
                           0, 0, 1, 1, false, false);

                batch.setColor(ENEMY_HEALTH_BAR_TRACK_RED, ENEMY_HEALTH_BAR_TRACK_GREEN,
                               ENEMY_HEALTH_BAR_TRACK_BLUE, 1f);
                batch.draw(whitePixelTexture, barLeft, barBottom, barWidth, barHeight,
                           0, 0, 1, 1, false, false);

                float fillWidth = barWidth * fillFraction;
                if (fillWidth > 0.5f) {
                    GameMath.healthBarColor(fillFraction, barColorRgb);
                    batch.setColor(barColorRgb[0], barColorRgb[1], barColorRgb[2], 1f);
                    batch.draw(whitePixelTexture, barLeft, barBottom, fillWidth, barHeight,
                               0, 0, 1, 1, false, false);
                }

                hpTextBuilder.setLength(0);
                hpTextBuilder.append(barHealthCurrents[sortedPosition]);
                hpTextBuilder.append('/');
                hpTextBuilder.append(barHealthMaxes[sortedPosition]);
                nameTagFont.getData().setScale(ENEMY_HP_TEXT_FONT_SCALE);
                hpTextLayout.setText(nameTagFont, hpTextBuilder);
                float hpTextX = barLeft + (barWidth  - hpTextLayout.width)  / 2f;
                float hpTextY = barBottom + barHeight / 2f + hpTextLayout.height / 2f;
                nameTagFont.setColor(ENEMY_HP_TEXT_RED, ENEMY_HP_TEXT_GREEN, ENEMY_HP_TEXT_BLUE, 1f);
                nameTagFont.draw(batch, hpTextLayout, hpTextX, hpTextY);
                nameTagFont.getData().setScale(ENEMY_NAME_TAG_FONT_SCALE);

                Enemy tagEnemy = enemies.get(sortedIndices[sortedPosition]);
                if (depth <= ENEMY_NAME_TAG_MAX_DISTANCE_TILES && !tagEnemy.nameTag.isEmpty()) {
                    nameTagLayout.setText(nameTagFont, tagEnemy.nameTag);
                    float tagX = barLeft + (barWidth - nameTagLayout.width) / 2f;
                    float tagY = barBottom + barHeight + ENEMY_NAME_TAG_BAR_GAP + nameTagLayout.height;
                    resolveNameTagColor(tagEnemy.dungeonLevel, nameTagColor);
                    nameTagFont.setColor(nameTagColor);
                    nameTagFont.draw(batch, nameTagLayout, tagX, tagY);
                }
            }
            batch.setColor(Color.WHITE);
            nameTagFont.setColor(Color.WHITE);
            batch.end();
        }
    }

    private static void resolveNameTagColor(int dungeonLevel, Color out) {
        if (dungeonLevel <= 2) {
            out.set(ENEMY_NAME_TAG_TIER1_R, ENEMY_NAME_TAG_TIER1_G, ENEMY_NAME_TAG_TIER1_B, 1f);
        } else if (dungeonLevel <= 4) {
            out.set(ENEMY_NAME_TAG_TIER2_R, ENEMY_NAME_TAG_TIER2_G, ENEMY_NAME_TAG_TIER2_B, 1f);
        } else if (dungeonLevel <= 5) {
            out.set(ENEMY_NAME_TAG_TIER3_R, ENEMY_NAME_TAG_TIER3_G, ENEMY_NAME_TAG_TIER3_B, 1f);
        } else if (dungeonLevel <= 7) {
            out.set(ENEMY_NAME_TAG_TIER4_R, ENEMY_NAME_TAG_TIER4_G, ENEMY_NAME_TAG_TIER4_B, 1f);
        } else {
            out.set(ENEMY_NAME_TAG_TIER5_R, ENEMY_NAME_TAG_TIER5_G, ENEMY_NAME_TAG_TIER5_B, 1f);
        }
    }

    @Override
    public void dispose() {
        batch.dispose();
        blightSheetTexture.dispose();
        infernalSheetTexture.dispose();
        whitePixelTexture.dispose();
        nameTagFont.dispose();
    }

    // -------------------------------------------------------------------------
    // Texture loading — sprite sheet regions
    // -------------------------------------------------------------------------

    /**
     * Splits each 2×2 sprite sheet into four equal TextureRegions and maps them to
     * the eight EnemyTypes. Regions use pixel (srcX, srcY) coordinates where srcY=0
     * is the top of the image, matching LibGDX's batch.draw() source convention.
     */
    private static Map<EnemyType, TextureRegion> buildTextureRegions(Texture blightSheet,
                                                                      Texture infernalSheet) {
        Map<EnemyType, TextureRegion> map = new HashMap<>();

        // Blight sheet (Q1=top-left, Q2=top-right, Q3=bottom-left, Q4=bottom-right)
        int blightHalfW = blightSheet.getWidth()  / 2;
        int blightHalfH = blightSheet.getHeight() / 2;
        map.put(EnemyType.PLAGUE_HULK,  new TextureRegion(blightSheet, 0,          0,          blightHalfW, blightHalfH));
        map.put(EnemyType.EYE_TYRANT,   new TextureRegion(blightSheet, blightHalfW, 0,          blightHalfW, blightHalfH));
        map.put(EnemyType.IRON_STALKER, new TextureRegion(blightSheet, 0,          blightHalfH, blightHalfW, blightHalfH));
        map.put(EnemyType.MIRE_WRAITH,  new TextureRegion(blightSheet, blightHalfW, blightHalfH, blightHalfW, blightHalfH));

        // Infernal sheet
        int infernalHalfW = infernalSheet.getWidth()  / 2;
        int infernalHalfH = infernalSheet.getHeight() / 2;
        map.put(EnemyType.GORE_BITER,   new TextureRegion(infernalSheet, 0,            0,            infernalHalfW, infernalHalfH));
        map.put(EnemyType.SHELL_BRUTE,  new TextureRegion(infernalSheet, infernalHalfW, 0,            infernalHalfW, infernalHalfH));
        map.put(EnemyType.ACID_DRONE,   new TextureRegion(infernalSheet, 0,            infernalHalfH, infernalHalfW, infernalHalfH));
        map.put(EnemyType.VOID_SHROUD,  new TextureRegion(infernalSheet, infernalHalfW, infernalHalfH, infernalHalfW, infernalHalfH));

        return map;
    }

    private static Texture loadSheetOrFallback(String assetPath, float red, float green, float blue) {
        if (Gdx.files.internal(assetPath).exists()) {
            Texture loaded = new Texture(Gdx.files.internal(assetPath));
            loaded.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            return loaded;
        }
        return generateFallbackSheet(red, green, blue);
    }

    private static Texture generateFallbackSheet(float red, float green, float blue) {
        // 2×2 grid fallback: 128×128 total, each quadrant 64×64 with simple eye pattern
        Pixmap pixmap = new Pixmap(128, 128, Pixmap.Format.RGBA8888);
        pixmap.setColor(red, green, blue, 1f);
        pixmap.fill();
        pixmap.setColor(1f, 1f, 0f, 1f);
        // Simple eye dots in each quadrant
        for (int quadrantRow = 0; quadrantRow < 2; quadrantRow++) {
            for (int quadrantColumn = 0; quadrantColumn < 2; quadrantColumn++) {
                int offsetX = quadrantColumn * 64;
                int offsetY = quadrantRow    * 64;
                pixmap.fillRectangle(offsetX + 12, offsetY + 20, 12, 12);
                pixmap.fillRectangle(offsetX + 40, offsetY + 20, 12, 12);
            }
        }
        pixmap.setColor(0f, 0f, 0f, 1f);
        for (int quadrantRow = 0; quadrantRow < 2; quadrantRow++) {
            for (int quadrantColumn = 0; quadrantColumn < 2; quadrantColumn++) {
                int offsetX = quadrantColumn * 64;
                int offsetY = quadrantRow    * 64;
                pixmap.fillRectangle(offsetX + 15, offsetY + 23, 6, 6);
                pixmap.fillRectangle(offsetX + 43, offsetY + 23, 6, 6);
            }
        }
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
