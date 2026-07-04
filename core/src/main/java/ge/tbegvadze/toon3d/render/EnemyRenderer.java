package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.enemy.EnemyManager;
import ge.tbegvadze.toon3d.enemy.EnemyState;
import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.enemy.IntentVerb;
import ge.tbegvadze.toon3d.enemy.PlannedAction;
import ge.tbegvadze.toon3d.enemy.SpecialAbility;
import ge.tbegvadze.toon3d.status.StatusEffect;
import ge.tbegvadze.toon3d.status.StatusType;
import ge.tbegvadze.toon3d.util.EffectConstants;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ge.tbegvadze.toon3d.util.Constants.*;
import static ge.tbegvadze.toon3d.util.EnemyConstants.*;
import static ge.tbegvadze.toon3d.util.IntentConstants.*;
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
    // Necrotic faction — one standalone Texture per archetype (drawn full-frame).
    private final Texture                       blightCorruptorTexture;
    private final Texture                       vortexEyeTexture;
    private final Texture                       ghoulTexture;
    private final Texture                       crawlerTexture;
    private final Texture                       revenantTexture;
    private final Texture                       whitePixelTexture;
    private final SpriteBatch                   batch;
    private final ShapeRenderer                 shapeRenderer;

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

    // Pre-allocated EYE_TYRANT beam cache — parallel to sortedIndices, populated in pass 1
    private final float[]   beamScreenXs;
    private final float[]   beamScreenYs;
    private final float[]   beamStrengths;
    private final boolean[] drawBeamFlags;

    // Pre-allocated affliction-overlay geometry cache — parallel to sortedIndices, populated in
    // pass 1 and consumed by the animated fire/toxin/bleed overlay pass (pass 1.5).
    private final float[]   fxCenterColumns;
    private final float[]   fxDrawBottoms;
    private final float[]   fxSpriteHeights;
    private final float[]   fxSpriteWidths;
    private final boolean[] fxDrawFlags;

    // Pre-allocated intent-icon geometry cache — parallel to sortedIndices, populated in pass 1 and
    // consumed by the intent-telegraph pass (strategy-combat-order-2). Only the screen center X and the
    // top of the health-bar/name-tag cluster are cached; the icon size, per-verb glyph, damage number,
    // and animation are resolved in the icon pass by reading the enemy's committed PlannedAction.
    private final float[]   intentCenterXs;
    private final float[]   intentClusterTops;
    private final boolean[] drawIntentFlags;

    // Fixed per-particle phase offsets so overlay particles are evenly but non-uniformly spread.
    // Static so the table is built once per JVM load and shared by every EnemyRenderer instance.
    private static final float[] AFFLICTION_PHASE_OFFSETS =
            { 0.00f, 0.61f, 0.24f, 0.83f, 0.42f, 0.13f, 0.72f, 0.51f };

    // Wall-clock accumulator driving the looping affliction overlay animation. Cosmetic only —
    // never read by game logic, so wall-clock (not turn-based) time is correct here.
    private float statusAnimationClock = 0f;

    // Reused per-frame colour buffer written by GameMath.healthBarColor — no allocation
    private final float[]   barColorRgb = new float[3];

    // Reused per-frame RGBA buffer for the current intent-icon frame colour — no allocation
    private final float[]   intentFrameColor = new float[4];

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
        // Allocate at least CORRUPTOR_MINION_CAP + 2 slots so boss fights (boss + minions)
        // never overflow the sort/bar scratch arrays when enemies are added mid-floor.
        int scratchSize          = Math.max(CORRUPTOR_MINION_CAP + 2, initialCount);
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
        this.beamScreenXs        = new float[scratchSize];
        this.beamScreenYs        = new float[scratchSize];
        this.beamStrengths       = new float[scratchSize];
        this.drawBeamFlags       = new boolean[scratchSize];
        this.fxCenterColumns     = new float[scratchSize];
        this.fxDrawBottoms       = new float[scratchSize];
        this.fxSpriteHeights     = new float[scratchSize];
        this.fxSpriteWidths      = new float[scratchSize];
        this.fxDrawFlags         = new boolean[scratchSize];
        this.intentCenterXs      = new float[scratchSize];
        this.intentClusterTops   = new float[scratchSize];
        this.drawIntentFlags     = new boolean[scratchSize];
        this.shapeRenderer       = new ShapeRenderer();
        this.batch               = new SpriteBatch(WALL_PROJECTION_SCREEN_WIDTH);

        // Load sprite sheets; fall back to a solid-colour placeholder when the file is absent
        this.blightSheetTexture   = loadSheetOrFallback(ENEMY_SHEET_BLIGHT_PATH,   0.60f, 0.20f, 0.60f);
        this.infernalSheetTexture = loadSheetOrFallback(ENEMY_SHEET_INFERNAL_PATH, 0.70f, 0.25f, 0.10f);
        // Necrotic faction — each archetype is a single standalone PNG drawn full-frame.
        this.blightCorruptorTexture = loadSheetOrFallback(ENEMY_BLIGHT_CORRUPTOR_PATH, 0.40f, 0.60f, 0.20f);
        this.vortexEyeTexture       = loadSheetOrFallback(ENEMY_VORTEX_EYE_PATH,       0.55f, 0.25f, 0.70f);
        this.ghoulTexture           = loadSheetOrFallback(ENEMY_GHOUL_PATH,            0.45f, 0.55f, 0.35f);
        this.crawlerTexture         = loadSheetOrFallback(ENEMY_CRAWLER_PATH,          0.55f, 0.45f, 0.30f);
        this.revenantTexture        = loadSheetOrFallback(ENEMY_REVENANT_PATH,         0.50f, 0.50f, 0.55f);
        this.textureRegions       = buildTextureRegions(blightSheetTexture, infernalSheetTexture);
        // Map the standalone PNGs as full-frame regions (one whole image per archetype).
        textureRegions.put(EnemyType.BLIGHT_CORRUPTOR, new TextureRegion(blightCorruptorTexture));
        textureRegions.put(EnemyType.VORTEX_EYE,       new TextureRegion(vortexEyeTexture));
        textureRegions.put(EnemyType.GHOUL,            new TextureRegion(ghoulTexture));
        textureRegions.put(EnemyType.CRAWLER,          new TextureRegion(crawlerTexture));
        textureRegions.put(EnemyType.REVENANT,         new TextureRegion(revenantTexture));

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

        // Advance the affliction overlay animation clock (wall-clock; cosmetic only).
        statusAnimationClock += Gdx.graphics.getDeltaTime();

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

            drawBarFlags[sortedPosition]    = false;
            drawBeamFlags[sortedPosition]   = false;
            fxDrawFlags[sortedPosition]     = false;
            drawIntentFlags[sortedPosition] = false;
            float attackAnimStrength      = enemy.getAttackAnimStrength();

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

            // Melee lunge: scale sprite up during attack animation
            float lungeCurve = 0f;
            if (!enemy.type.isRanged() && attackAnimStrength > 0f) {
                lungeCurve         = GameMath.attackLungeCurve(attackAnimStrength);
                float scaleBonus   = 1f + lungeCurve * EffectConstants.ENEMY_LUNGE_SCALE_BONUS;
                spriteScreenHeight *= scaleBonus;
                spriteScreenWidth  *= scaleBonus;
            }

            int leftScreenColumn  = (int)(screenCenterColumn - spriteScreenWidth / 2f);
            int rightScreenColumn = (int)(screenCenterColumn + spriteScreenWidth / 2f);
            int columnSpan        = rightScreenColumn - leftScreenColumn;
            if (columnSpan <= 0) continue;

            float drawBottom = GameMath.wallStripeDrawBottom(WALL_PROJECTION_SCREEN_HEIGHT, fullWallLineHeight);
            // EYE_TYRANT hovers; all other enemies (including MIRE_WRAITH) stand on the floor.
            if (enemy.type == EnemyType.EYE_TYRANT) {
                drawBottom += fullWallLineHeight * EYE_TYRANT_HOVER_OFFSET_FRACTION;
            }
            // Melee lunge: nudge sprite downward to sell the forward surge
            if (lungeCurve > 0f) {
                drawBottom -= fullWallLineHeight * EffectConstants.ENEMY_LUNGE_DROP_FRACTION * lungeCurve;
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

            // Base shade: apply distance falloff then clamp to min so sprites sit in the world.
            // Active enemies use ENEMY_SPRITE_SHADE_MIN_BRIGHTNESS; dormant enemies are additionally
            // darkened so they visibly "light up" when alerted.
            float baseShade = Math.max(GameMath.wallShade(depth, WALL_SHADING_FALLOFF),
                                       ENEMY_SPRITE_SHADE_MIN_BRIGHTNESS);
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

            // Telegraph tint: brief danger flash at the instant of attack
            float telegraphStrength = enemy.getTelegraphStrength();
            if (telegraphStrength > 0f) {
                spriteRed   = GameMath.lerp(spriteRed,   EffectConstants.MELEE_TELEGRAPH_R, telegraphStrength);
                spriteGreen = GameMath.lerp(spriteGreen, EffectConstants.MELEE_TELEGRAPH_G, telegraphStrength);
                spriteBlue  = GameMath.lerp(spriteBlue,  EffectConstants.MELEE_TELEGRAPH_B, telegraphStrength);
            }

            // Block plating shimmer (strategy-combat-order-3): a translucent steely-blue tint while the
            // enemy holds active Block, gently pulsing so a braced enemy visibly "plates over."
            if (enemy.block > 0) {
                float shimmer = ENEMY_BLOCK_TINT_STRENGTH + ENEMY_BLOCK_TINT_SHIMMER_AMOUNT
                        * MathUtils.sin(statusAnimationClock * ENEMY_BLOCK_TINT_SHIMMER_HZ * MathUtils.PI2);
                shimmer = Math.max(0f, Math.min(1f, shimmer));
                spriteRed   = GameMath.lerp(spriteRed,   ENEMY_BLOCK_TINT_RED,   shimmer);
                spriteGreen = GameMath.lerp(spriteGreen, ENEMY_BLOCK_TINT_GREEN, shimmer);
                spriteBlue  = GameMath.lerp(spriteBlue,  ENEMY_BLOCK_TINT_BLUE,  shimmer);
            }

            // EYE_TYRANT instant beam: cache screen position for pass 3
            if (enemy.type == EnemyType.EYE_TYRANT && attackAnimStrength > 0f) {
                beamScreenXs[sortedPosition]  = screenCenterColumn;
                beamScreenYs[sortedPosition]  = drawBottom + spriteScreenHeight / 2f;
                beamStrengths[sortedPosition] = attackAnimStrength;
                drawBeamFlags[sortedPosition] = true;
            }

            // Cache billboard geometry for the affliction overlay pass (pass 1.5).
            fxCenterColumns[sortedPosition] = screenCenterColumn;
            fxDrawBottoms[sortedPosition]   = drawBottom;
            fxSpriteHeights[sortedPosition] = spriteScreenHeight;
            fxSpriteWidths[sortedPosition]  = spriteScreenWidth;
            fxDrawFlags[sortedPosition]     = true;

            batch.setColor(spriteRed, spriteGreen, spriteBlue, 1f);

            float[] propZBuffer      = (propRenderer != null) ? propRenderer.getPropSpriteZBuffer()      : null;
            float[] propColumnBottom = (propRenderer != null) ? propRenderer.getPropSpriteColumnBottom() : null;
            float[] propColumnTop    = (propRenderer != null) ? propRenderer.getPropSpriteColumnTop()    : null;
            float   columnHeightSpan = clampedTop - clampedBottom;

            int firstColumn = Math.max(0, leftScreenColumn);
            int lastColumn  = Math.min(WALL_PROJECTION_SCREEN_WIDTH - 1, rightScreenColumn);
            for (int screenColumn = firstColumn; screenColumn <= lastColumn; screenColumn++) {
                if (depth >= wallRenderer.getZBufferUnchecked(screenColumn)) continue;

                // Map screen column to a pixel x within the region, then offset to sheet coords
                int localSrcX = (screenColumn - leftScreenColumn) * regionWidth / columnSpan;
                localSrcX = MathUtils.clamp(localSrcX, 0, regionWidth - 1);
                int texSrcX = region.getRegionX() + localSrcX;

                if (propZBuffer != null && depth >= propZBuffer[screenColumn]) {
                    // A closer prop blocks this column. Draw the enemy in two segments around
                    // the prop's vertical extent so the enemy stays visible above and below
                    // small props (e.g. ammo pickups) that don't fill the full column height.
                    float propBottom = propColumnBottom[screenColumn];
                    float propTop    = propColumnTop[screenColumn];

                    // Above-prop segment: screen rows [max(propTop, clampedBottom), clampedTop]
                    float aboveSegBottom = Math.max(propTop, clampedBottom);
                    if (aboveSegBottom < clampedTop && columnHeightSpan > 0f) {
                        int aboveSrcHeight = Math.max(1,
                                (int)(texSrcHeight * (clampedTop - aboveSegBottom) / columnHeightSpan));
                        aboveSrcHeight = Math.min(aboveSrcHeight, texSrcHeight);
                        batch.draw(region.getTexture(),
                                   screenColumn * WALL_COLUMN_WIDTH, aboveSegBottom,
                                   WALL_COLUMN_WIDTH, clampedTop - aboveSegBottom,
                                   texSrcX, texSrcY, 1, aboveSrcHeight,
                                   false, false);
                    }

                    // Below-prop segment: screen rows [clampedBottom, min(propBottom, clampedTop)]
                    float belowSegTop = Math.min(propBottom, clampedTop);
                    if (clampedBottom < belowSegTop && columnHeightSpan > 0f) {
                        int belowSrcYOffset = (int)(texSrcHeight * (clampedTop - belowSegTop) / columnHeightSpan);
                        int belowSrcY       = texSrcY + belowSrcYOffset;
                        int belowSrcHeight  = texSrcHeight - belowSrcYOffset;
                        if (belowSrcHeight > 0) {
                            batch.draw(region.getTexture(),
                                       screenColumn * WALL_COLUMN_WIDTH, clampedBottom,
                                       WALL_COLUMN_WIDTH, belowSegTop - clampedBottom,
                                       texSrcX, belowSrcY, 1, belowSrcHeight,
                                       false, false);
                        }
                    }
                } else {
                    batch.draw(region.getTexture(),
                               screenColumn * WALL_COLUMN_WIDTH, clampedBottom,
                               WALL_COLUMN_WIDTH, clampedTop - clampedBottom,
                               texSrcX, texSrcY, 1, texSrcHeight,
                               false, false);
                }
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

            // Cache intent-icon anchor for the telegraph pass (order-2). Only alerted enemies with a
            // committed plan reveal intent — DORMANT/un-committed enemies show nothing (no info leak).
            // The anchor is the top of the health-bar + name-tag cluster so the icon stacks above it.
            if (enemy.isAlerted() && enemy.plannedAction.committed
                    && depth <= INTENT_ICON_MAX_DISTANCE_TILES) {
                float healthBarBottom = drawTop + spriteScreenHeight * ENEMY_HEALTH_BAR_GAP_FRACTION;
                float healthBarHeight = Math.max(ENEMY_HEALTH_BAR_MIN_PIXELS,
                                                 spriteScreenHeight * ENEMY_HEALTH_BAR_HEIGHT_FRACTION);
                intentCenterXs[sortedPosition]    = screenCenterColumn;
                intentClusterTops[sortedPosition] = healthBarBottom + healthBarHeight
                                                    + INTENT_ICON_NAMETAG_CLEARANCE;
                drawIntentFlags[sortedPosition]   = true;
            }
        }

        batch.setColor(Color.WHITE);
        batch.end();

        // =====================================================================
        // Pass 1.5: Animated affliction overlays (fire / toxin / bleed)
        // Additive-blended glow drawn over the body but under the health bars, so a
        // burning enemy visibly burns, a poisoned one bubbles, a bleeding one drips.
        // =====================================================================
        boolean anyAffliction = false;
        for (int sortedPosition = 0; sortedPosition < visibleCount; sortedPosition++) {
            if (!fxDrawFlags[sortedPosition]) continue;
            if (hasAnyAffliction(enemies.get(sortedIndices[sortedPosition]))) { anyAffliction = true; break; }
        }

        if (anyAffliction) {
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE); // additive glow
            batch.begin();
            for (int sortedPosition = 0; sortedPosition < visibleCount; sortedPosition++) {
                if (!fxDrawFlags[sortedPosition]) continue;
                Enemy enemy = enemies.get(sortedIndices[sortedPosition]);

                float centerColumn = fxCenterColumns[sortedPosition];
                int   centerScreenColumn = (int) centerColumn;
                if (centerScreenColumn < 0 || centerScreenColumn >= WALL_PROJECTION_SCREEN_WIDTH) continue;
                // Occlude behind walls using the same center-column depth test as the health bars.
                if (sortedDepths[sortedPosition] >= wallRenderer.getZBufferUnchecked(centerScreenColumn)) continue;

                float bottom = fxDrawBottoms[sortedPosition];
                float height = fxSpriteHeights[sortedPosition];
                float width  = fxSpriteWidths[sortedPosition];

                StatusEffect burn   = enemy.getActiveEffects().get(StatusType.BURNING);
                StatusEffect poison = enemy.getActiveEffects().get(StatusType.POISONED);
                StatusEffect bleed  = enemy.getActiveEffects().get(StatusType.BLEED);

                if (burn != null && burn.isActive()) {
                    drawAfflictionParticles(centerColumn, bottom, height, width,
                            EffectConstants.AFFLICTION_FIRE_RISE_HZ, EffectConstants.AFFLICTION_FIRE_WOBBLE_HZ,
                            EffectConstants.AFFLICTION_FIRE_WOBBLE_FRAC, EffectConstants.AFFLICTION_FIRE_ZONE_FRAC,
                            EffectConstants.AFFLICTION_FIRE_SIZE_FRAC, EffectConstants.AFFLICTION_FIRE_ALPHA,
                            false, 0f,
                            EffectConstants.AFFLICTION_FIRE_LOW_R, EffectConstants.AFFLICTION_FIRE_LOW_G, EffectConstants.AFFLICTION_FIRE_LOW_B,
                            EffectConstants.AFFLICTION_FIRE_HIGH_R, EffectConstants.AFFLICTION_FIRE_HIGH_G, EffectConstants.AFFLICTION_FIRE_HIGH_B);
                }
                if (poison != null && poison.isActive()) {
                    drawAfflictionParticles(centerColumn, bottom, height, width,
                            EffectConstants.AFFLICTION_POISON_RISE_HZ, EffectConstants.AFFLICTION_POISON_WOBBLE_HZ,
                            EffectConstants.AFFLICTION_POISON_WOBBLE_FRAC, EffectConstants.AFFLICTION_POISON_ZONE_FRAC,
                            EffectConstants.AFFLICTION_POISON_SIZE_FRAC, EffectConstants.AFFLICTION_POISON_ALPHA,
                            false, 0f,
                            EffectConstants.AFFLICTION_POISON_LOW_R, EffectConstants.AFFLICTION_POISON_LOW_G, EffectConstants.AFFLICTION_POISON_LOW_B,
                            EffectConstants.AFFLICTION_POISON_HIGH_R, EffectConstants.AFFLICTION_POISON_HIGH_G, EffectConstants.AFFLICTION_POISON_HIGH_B);
                }
                if (bleed != null && bleed.isActive()) {
                    drawAfflictionParticles(centerColumn, bottom, height, width,
                            EffectConstants.AFFLICTION_BLEED_FALL_HZ, EffectConstants.AFFLICTION_BLEED_FALL_HZ,
                            EffectConstants.AFFLICTION_BLEED_WOBBLE_FRAC, EffectConstants.AFFLICTION_BLEED_ZONE_FRAC,
                            EffectConstants.AFFLICTION_BLEED_SIZE_FRAC, EffectConstants.AFFLICTION_BLEED_ALPHA,
                            true, EffectConstants.AFFLICTION_BLEED_ZONE_FRAC,
                            EffectConstants.AFFLICTION_BLEED_R, EffectConstants.AFFLICTION_BLEED_G, EffectConstants.AFFLICTION_BLEED_B,
                            EffectConstants.AFFLICTION_BLEED_R, EffectConstants.AFFLICTION_BLEED_G, EffectConstants.AFFLICTION_BLEED_B);
                }
            }
            batch.setColor(Color.WHITE);
            batch.end();
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA); // restore default alpha blend
        }

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

                if (depth <= ENEMY_HP_TEXT_MAX_DISTANCE_TILES) {
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
                }

                Enemy tagEnemy = enemies.get(sortedIndices[sortedPosition]);
                if (depth <= ENEMY_NAME_TAG_MAX_DISTANCE_TILES && !tagEnemy.nameTag.isEmpty()) {
                    nameTagLayout.setText(nameTagFont, tagEnemy.nameTag);
                    float tagX = barLeft + (barWidth - nameTagLayout.width) / 2f;
                    float tagY = barBottom + barHeight + ENEMY_NAME_TAG_BAR_GAP + nameTagLayout.height;
                    resolveNameTagColor(tagEnemy.dungeonLevel, nameTagColor);
                    nameTagFont.setColor(nameTagColor);
                    nameTagFont.draw(batch, nameTagLayout, tagX, tagY);
                }

                // Active-Block number (strategy-combat-order-3): the live shield value shown in
                // shield-blue just left of the health bar while the enemy holds Block. Distinct from
                // the intent icon's telegraphed number (order-2) — this is Block the enemy HAS now.
                if (tagEnemy.block > 0 && depth <= ENEMY_BLOCK_NUMBER_MAX_DISTANCE_TILES) {
                    hpTextBuilder.setLength(0);
                    hpTextBuilder.append(tagEnemy.block);
                    nameTagFont.getData().setScale(ENEMY_BLOCK_NUMBER_FONT_SCALE);
                    hpTextLayout.setText(nameTagFont, hpTextBuilder);
                    float blockTextX = barLeft - ENEMY_BLOCK_NUMBER_BAR_GAP - hpTextLayout.width;
                    float blockTextY = barBottom + barHeight / 2f + hpTextLayout.height / 2f;
                    nameTagFont.setColor(ENEMY_BLOCK_NUMBER_RED, ENEMY_BLOCK_NUMBER_GREEN,
                            ENEMY_BLOCK_NUMBER_BLUE, 1f);
                    nameTagFont.draw(batch, hpTextLayout, blockTextX, blockTextY);
                    nameTagFont.getData().setScale(ENEMY_NAME_TAG_FONT_SCALE);
                }
            }
            batch.setColor(Color.WHITE);
            nameTagFont.setColor(Color.WHITE);
            batch.end();
        }

        // =====================================================================
        // Pass 2.5: Enemy intent telegraph icons (strategy-combat-order-2)
        // For every alerted enemy with a committed plan, draw the floating "what happens next"
        // frame + procedural glyph (+ damage/Block number) stacked above the health-bar cluster.
        // Reuses pass-1 projection, the same center-column Z-buffer occlusion test as the bars, and
        // the white-pixel texture + name-tag font — zero new allocation.
        // =====================================================================
        boolean anyIntent = false;
        for (int sortedPosition = 0; sortedPosition < visibleCount; sortedPosition++) {
            if (drawIntentFlags[sortedPosition]) { anyIntent = true; break; }
        }
        if (anyIntent) {
            int playerTileColumn = (int) (playerWorldX / CELL_SIZE);
            int playerTileRow    = (int) (playerWorldY / CELL_SIZE);
            batch.begin();
            for (int sortedPosition = 0; sortedPosition < visibleCount; sortedPosition++) {
                if (!drawIntentFlags[sortedPosition]) continue;

                float depth   = sortedDepths[sortedPosition];
                float centerX = intentCenterXs[sortedPosition];
                int   centerColumn = (int) centerX;
                if (centerColumn < 0 || centerColumn >= WALL_PROJECTION_SCREEN_WIDTH) continue;
                if (depth >= wallRenderer.getZBufferUnchecked(centerColumn)) continue; // occluded by wall

                Enemy         enemy = enemies.get(sortedIndices[sortedPosition]);
                PlannedAction plan  = enemy.plannedAction;
                IntentVerb    verb  = plan.verb;

                float iconSize = GameMath.intentIconSize(INTENT_ICON_SIZE_WORLD, depth,
                        INTENT_ICON_REFERENCE_DISTANCE_TILES, INTENT_ICON_MIN_SIZE, INTENT_ICON_MAX_SIZE);
                // WIND_UP pulses in sync with the sprite's telegraph flash; other verbs don't pulse.
                float telegraphStrength = (verb == IntentVerb.WIND_UP) ? enemy.getTelegraphStrength() : 0f;
                float scale = GameMath.intentIconScale(enemy.getIntentPopStrength(), INTENT_POP_SCALE_BONUS,
                        telegraphStrength, INTENT_WINDUP_PULSE_SCALE_BONUS);
                float drawnSize = iconSize * scale;

                float phaseRadians = (enemy.getId() % 16) / 16f * MathUtils.PI2;
                float bob = GameMath.intentIconBobOffset(statusAnimationClock, phaseRadians,
                        INTENT_ICON_BOB_AMPLITUDE, INTENT_ICON_BOB_HZ);

                // SPECIAL abilities show a number only when they deal damage (AREA_STRIKE); buffs,
                // debuffs and summons carry no predicted-damage number (order-5).
                boolean showsNumber = intentShowsNumber(verb)
                        || (verb == IntentVerb.SPECIAL && plan.predictedDamage > 0);
                float numberBand = showsNumber ? iconSize * 0.55f : 0f;
                float baseY = intentClusterTops[sortedPosition] + bob;
                float iconCenterY = baseY + numberBand + INTENT_ICON_Y_OFFSET_ABOVE_HEALTHBAR + drawnSize / 2f;

                boolean locked = verb == IntentVerb.ATTACK_RANGED
                        && isSameCardinalTile(enemy.tileColumn, enemy.tileRow, playerTileColumn, playerTileRow);

                resolveIntentFrameColor(verb, intentFrameColor);
                drawIntentFrame(centerX, iconCenterY, drawnSize, intentFrameColor, locked);
                drawIntentGlyph(verb, enemy, plan, centerX, iconCenterY, drawnSize);

                if (showsNumber && depth <= INTENT_DAMAGE_MAX_DISTANCE_TILES) {
                    boolean isBlock = verb == IntentVerb.DEFEND;
                    int value = isBlock ? plan.blockGain : plan.predictedDamage;
                    if (value > 0) {
                        drawIntentNumber(centerX, baseY + numberBand, value, isBlock);
                    }
                }
            }
            batch.setColor(Color.WHITE);
            nameTagFont.setColor(Color.WHITE);
            batch.end();
        }

        // =====================================================================
        // Pass 3: EYE_TYRANT instant beams (ShapeRenderer, drawn over all sprites)
        // =====================================================================
        boolean anyBeams = false;
        for (int sortedPosition = 0; sortedPosition < visibleCount; sortedPosition++) {
            if (drawBeamFlags[sortedPosition]) { anyBeams = true; break; }
        }
        if (anyBeams) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            shapeRenderer.setProjectionMatrix(camera.combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            float playerScreenX = WALL_PROJECTION_SCREEN_WIDTH  / 2f;
            float playerScreenY = WALL_PROJECTION_SCREEN_HEIGHT / 2f;
            for (int sortedPosition = 0; sortedPosition < visibleCount; sortedPosition++) {
                if (!drawBeamFlags[sortedPosition]) continue;
                shapeRenderer.setColor(EffectConstants.EYE_TYRANT_BEAM_R,
                                       EffectConstants.EYE_TYRANT_BEAM_G,
                                       EffectConstants.EYE_TYRANT_BEAM_B,
                                       beamStrengths[sortedPosition]);
                shapeRenderer.rectLine(beamScreenXs[sortedPosition], beamScreenYs[sortedPosition],
                                       playerScreenX, playerScreenY,
                                       EffectConstants.ENEMY_PROJECTILE_BEAM_THICKNESS);
            }
            shapeRenderer.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);
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

    /** True when the enemy has any damage-over-time affliction that draws an animated overlay. */
    private static boolean hasAnyAffliction(Enemy enemy) {
        return enemy.getActiveEffects().get(StatusType.BURNING).isActive()
                || enemy.getActiveEffects().get(StatusType.POISONED).isActive()
                || enemy.getActiveEffects().get(StatusType.BLEED).isActive();
    }

    /*
     * Draws one animated affliction overlay (a column of looping particles) on an enemy billboard.
     * Each particle loops on a per-index phase in [0,1): rising types climb from the sprite base to
     * `zoneFrac` of its height; falling types (bleed) drip down from `startFrac` height to the feet.
     * Horizontal position = center ± a per-index spread plus a time-driven sine wobble. Size shrinks
     * and alpha follows sin(phase·π) so each particle fades in at birth and out at the end of its loop.
     * Color lerps low→high by phase (fire yellow→red; poison pale→toxic; bleed constant crimson).
     * All draws use the additive-blended white-pixel quad set up by the caller — no allocation.
     * Screen space is Y-up (origin bottom-left); larger Y is higher on screen, matching the sprite.
     */
    private void drawAfflictionParticles(float centerX, float bottom, float height, float width,
                                         float riseHz, float wobbleHz, float wobbleFrac, float zoneFrac,
                                         float sizeFrac, float maxAlpha, boolean falling, float startFrac,
                                         float lowRed, float lowGreen, float lowBlue,
                                         float highRed, float highGreen, float highBlue) {
        int particleCount = EffectConstants.AFFLICTION_FX_PARTICLES;
        for (int particleIndex = 0; particleIndex < particleCount; particleIndex++) {
            float seed  = AFFLICTION_PHASE_OFFSETS[particleIndex % AFFLICTION_PHASE_OFFSETS.length];
            float raw   = statusAnimationClock * riseHz + seed;
            float phase = raw - (float) Math.floor(raw);            // looping progress 0..1

            float verticalFraction = falling ? startFrac * (1f - phase) : phase * zoneFrac;
            float particleY        = bottom + verticalFraction * height;

            float wobble = (float) Math.sin(statusAnimationClock * wobbleHz * MathUtils.PI2 + seed * MathUtils.PI2);
            float particleX = centerX + width * wobbleFrac * ((seed - 0.5f) * 1.4f + wobble * 0.4f);

            float size  = width * sizeFrac * (1f - phase * 0.5f);
            float alpha = maxAlpha * (float) Math.sin(phase * Math.PI); // 0 at birth/death, peak mid-life
            if (size <= 0f || alpha <= 0.01f) continue;

            float red   = GameMath.lerp(lowRed,   highRed,   phase);
            float green = GameMath.lerp(lowGreen, highGreen, phase);
            float blue  = GameMath.lerp(lowBlue,  highBlue,  phase);

            batch.setColor(red, green, blue, alpha);
            batch.draw(whitePixelTexture, particleX - size / 2f, particleY - size / 2f, size, size,
                    0, 0, 1, 1, false, false);
        }
    }

    // -------------------------------------------------------------------------
    // Intent telegraph icons (strategy-combat-order-2) — procedural, no textures
    // -------------------------------------------------------------------------

    /** True for verbs whose intent carries a magnitude number (damage, or Block for DEFEND). */
    private static boolean intentShowsNumber(IntentVerb verb) {
        return verb == IntentVerb.ATTACK_MELEE
                || verb == IntentVerb.ATTACK_RANGED
                || verb == IntentVerb.WIND_UP
                || verb == IntentVerb.DEFEND;
    }

    /** True when two tiles share a cardinal line (same column OR same row) — the "locked" ranged case. */
    private static boolean isSameCardinalTile(int fromColumn, int fromRow, int toColumn, int toRow) {
        return fromColumn == toColumn || fromRow == toRow;
    }

    /** Writes the per-verb frame RGBA into {@code out} (WAIT is dimmed via a lower alpha). */
    private static void resolveIntentFrameColor(IntentVerb verb, float[] out) {
        float alpha = INTENT_FRAME_ALPHA;
        switch (verb) {
            case ATTACK_MELEE:  out[0] = INTENT_COLOR_ATTACK_RED;  out[1] = INTENT_COLOR_ATTACK_GREEN;  out[2] = INTENT_COLOR_ATTACK_BLUE;  break;
            case ATTACK_RANGED: out[0] = INTENT_COLOR_RANGED_RED;  out[1] = INTENT_COLOR_RANGED_GREEN;  out[2] = INTENT_COLOR_RANGED_BLUE;  break;
            case WIND_UP:       out[0] = INTENT_COLOR_WINDUP_RED;  out[1] = INTENT_COLOR_WINDUP_GREEN;  out[2] = INTENT_COLOR_WINDUP_BLUE;  break;
            case MOVE:          out[0] = INTENT_COLOR_MOVE_RED;    out[1] = INTENT_COLOR_MOVE_GREEN;    out[2] = INTENT_COLOR_MOVE_BLUE;    break;
            case DEFEND:        out[0] = INTENT_COLOR_DEFEND_RED;  out[1] = INTENT_COLOR_DEFEND_GREEN;  out[2] = INTENT_COLOR_DEFEND_BLUE;  break;
            case SPECIAL:       out[0] = INTENT_COLOR_SPECIAL_RED; out[1] = INTENT_COLOR_SPECIAL_GREEN; out[2] = INTENT_COLOR_SPECIAL_BLUE; break;
            case STUNNED:       out[0] = INTENT_COLOR_STUNNED_RED; out[1] = INTENT_COLOR_STUNNED_GREEN; out[2] = INTENT_COLOR_STUNNED_BLUE; break;
            case WAIT:
            default:            out[0] = INTENT_COLOR_WAIT_RED;    out[1] = INTENT_COLOR_WAIT_GREEN;    out[2] = INTENT_COLOR_WAIT_BLUE;
                                alpha  = INTENT_FRAME_ALPHA * INTENT_WAIT_ALPHA_SCALE; break;
        }
        out[3] = alpha;
    }

    /**
     * Draws the rounded-square intent frame: an optional bright "locked" outline (deadly ranged lane),
     * a near-black border, then the verb-tinted fill. All quads are axis-aligned white-pixel draws.
     */
    private void drawIntentFrame(float centerX, float centerY, float size, float[] color, boolean locked) {
        if (locked) {
            float lockedSpan = size + 2f * (INTENT_FRAME_BORDER_PIXELS + INTENT_LOCKED_OUTLINE_PIXELS);
            batch.setColor(INTENT_LOCKED_OUTLINE_RED, INTENT_LOCKED_OUTLINE_GREEN, INTENT_LOCKED_OUTLINE_BLUE, color[3]);
            drawCenteredQuad(centerX, centerY, lockedSpan, lockedSpan, 0f);
        }
        float borderSpan = size + 2f * INTENT_FRAME_BORDER_PIXELS;
        batch.setColor(INTENT_FRAME_BORDER_RED, INTENT_FRAME_BORDER_GREEN, INTENT_FRAME_BORDER_BLUE, color[3]);
        drawCenteredQuad(centerX, centerY, borderSpan, borderSpan, 0f);
        batch.setColor(color[0], color[1], color[2], color[3]);
        drawCenteredQuad(centerX, centerY, size, size, 0f);
    }

    /** Dispatches the per-verb procedural glyph, drawn in bright ink centered in the frame. */
    private void drawIntentGlyph(IntentVerb verb, Enemy enemy, PlannedAction plan,
                                 float centerX, float centerY, float size) {
        batch.setColor(INTENT_GLYPH_RED, INTENT_GLYPH_GREEN, INTENT_GLYPH_BLUE, 1f);
        float glyph = size * 0.58f;
        float thickness = Math.max(2f, size * 0.12f);
        switch (verb) {
            case ATTACK_MELEE:
                // Fang/spike — a single diamond (rotated square).
                drawCenteredQuad(centerX, centerY, glyph * 0.60f, glyph * 0.60f, 45f);
                break;
            case ATTACK_RANGED:
                drawArrowGlyph(centerX, centerY, glyph, thickness, cardinalGlyphDegrees(enemy, plan));
                break;
            case WIND_UP: {
                // Loaded spring — two diamonds stacked along the charge direction.
                float degrees = cardinalGlyphDegrees(enemy, plan);
                float unitX = MathUtils.cosDeg(degrees);
                float unitY = MathUtils.sinDeg(degrees);
                float offset = glyph * 0.22f;
                drawCenteredQuad(centerX - unitX * offset, centerY - unitY * offset, glyph * 0.48f, glyph * 0.48f, 45f);
                drawCenteredQuad(centerX + unitX * offset, centerY + unitY * offset, glyph * 0.48f, glyph * 0.48f, 45f);
                break;
            }
            case MOVE:
                // Two footprints stepping diagonally.
                drawCenteredQuad(centerX - glyph * 0.18f, centerY - glyph * 0.12f, glyph * 0.28f, glyph * 0.28f, 0f);
                drawCenteredQuad(centerX + glyph * 0.18f, centerY + glyph * 0.12f, glyph * 0.28f, glyph * 0.28f, 0f);
                break;
            case DEFEND:
                // Shield — a plate over a pointed bottom.
                drawCenteredQuad(centerX, centerY + glyph * 0.10f, glyph * 0.50f, glyph * 0.42f, 0f);
                drawCenteredQuad(centerX, centerY - glyph * 0.22f, glyph * 0.34f, glyph * 0.34f, 45f);
                break;
            case SPECIAL:
                drawSpecialGlyph(plan.specialAbility, centerX, centerY, glyph, thickness);
                break;
            case STUNNED:
                // X — a wasted turn.
                drawCenteredQuad(centerX, centerY, glyph * 0.80f, thickness, 45f);
                drawCenteredQuad(centerX, centerY, glyph * 0.80f, thickness, 135f);
                break;
            case WAIT:
            default:
                // Ellipsis — three dots signalling "no threat this turn."
                float dot = glyph * 0.16f;
                drawCenteredQuad(centerX - glyph * 0.28f, centerY, dot, dot, 0f);
                drawCenteredQuad(centerX,                 centerY, dot, dot, 0f);
                drawCenteredQuad(centerX + glyph * 0.28f, centerY, dot, dot, 0f);
                break;
        }
    }

    /**
     * Draws the per-ability sub-glyph inside the purple SPECIAL frame (strategy-combat-order-5): an
     * up-arrow for a self-buff, a down-arrow for a player debuff, a spawn-rune for a summon, and a
     * radiating burst for an area strike. A null ability (defensive fallback) draws the generic
     * eight-point starburst. Screen space is Y-up, so "up" is +Y (90°).
     */
    private void drawSpecialGlyph(SpecialAbility ability, float centerX, float centerY,
                                  float glyph, float thickness) {
        SpecialAbility.SubGlyph subGlyph = ability == null ? null : ability.telegraphSubGlyph();
        if (subGlyph == null) {
            // Generic starburst — a four-armed cross plus a rotated cross (8-point star).
            drawCenteredQuad(centerX, centerY, glyph * 0.78f, thickness, 0f);
            drawCenteredQuad(centerX, centerY, glyph * 0.78f, thickness, 90f);
            drawCenteredQuad(centerX, centerY, glyph * 0.70f, thickness * 0.8f, 45f);
            drawCenteredQuad(centerX, centerY, glyph * 0.70f, thickness * 0.8f, 135f);
            return;
        }
        switch (subGlyph) {
            case BUFF:
                // Up-arrow — "getting stronger."
                drawArrowGlyph(centerX, centerY, glyph, thickness, 90f);
                break;
            case DEBUFF:
                // Down-arrow — "you're being weakened."
                drawArrowGlyph(centerX, centerY, glyph, thickness, -90f);
                break;
            case SUMMON: {
                // Spawn-rune — a parent diamond shedding two smaller offspring diamonds below it.
                drawCenteredQuad(centerX, centerY + glyph * 0.16f, glyph * 0.36f, glyph * 0.36f, 45f);
                float offspring = glyph * 0.22f;
                drawCenteredQuad(centerX - glyph * 0.26f, centerY - glyph * 0.22f, offspring, offspring, 45f);
                drawCenteredQuad(centerX + glyph * 0.26f, centerY - glyph * 0.22f, offspring, offspring, 45f);
                break;
            }
            case AREA: {
                // Burst — a core diamond with four diamonds radiating outward along the cardinals.
                float radius = glyph * 0.30f;
                drawCenteredQuad(centerX, centerY, glyph * 0.24f, glyph * 0.24f, 45f);
                float shard = glyph * 0.20f;
                drawCenteredQuad(centerX + radius, centerY, shard, shard, 45f);
                drawCenteredQuad(centerX - radius, centerY, shard, shard, 45f);
                drawCenteredQuad(centerX, centerY + radius, shard, shard, 45f);
                drawCenteredQuad(centerX, centerY - radius, shard, shard, 45f);
                break;
            }
            default:
                break;
        }
    }

    /** Draws a directional arrow glyph: a shaft with a diamond head pointing at {@code degrees}. */
    private void drawArrowGlyph(float centerX, float centerY, float glyph, float thickness, float degrees) {
        drawCenteredQuad(centerX, centerY, glyph * 0.80f, thickness, degrees);
        float tipX = centerX + MathUtils.cosDeg(degrees) * glyph * 0.40f;
        float tipY = centerY + MathUtils.sinDeg(degrees) * glyph * 0.40f;
        drawCenteredQuad(tipX, tipY, thickness * 2.2f, thickness * 2.2f, degrees + 45f);
    }

    /**
     * Returns the glyph rotation (screen degrees, 0 = pointing right) from the enemy tile toward its
     * committed target tile. Screen space here is Y-up, so +row maps to "up." Defaults to 0 when the
     * enemy is already on the target tile.
     */
    private static float cardinalGlyphDegrees(Enemy enemy, PlannedAction plan) {
        int differenceColumn = plan.targetColumn - enemy.tileColumn;
        int differenceRow    = plan.targetRow    - enemy.tileRow;
        if (differenceColumn == 0 && differenceRow == 0) return 0f;
        return MathUtils.atan2(differenceRow, differenceColumn) * MathUtils.radiansToDegrees;
    }

    /** Draws the intent magnitude number centered horizontally at {@code centerX}, top-aligned at {@code topY}. */
    private void drawIntentNumber(float centerX, float topY, int value, boolean isBlock) {
        hpTextBuilder.setLength(0);
        hpTextBuilder.append(value);
        nameTagFont.getData().setScale(INTENT_DAMAGE_NUMBER_SCALE);
        hpTextLayout.setText(nameTagFont, hpTextBuilder);
        float textX = centerX - hpTextLayout.width / 2f;
        if (isBlock) {
            nameTagFont.setColor(INTENT_BLOCK_NUMBER_RED, INTENT_BLOCK_NUMBER_GREEN, INTENT_BLOCK_NUMBER_BLUE, 1f);
        } else {
            nameTagFont.setColor(INTENT_DAMAGE_NUMBER_RED, INTENT_DAMAGE_NUMBER_GREEN, INTENT_DAMAGE_NUMBER_BLUE, 1f);
        }
        nameTagFont.draw(batch, hpTextLayout, textX, topY);
        nameTagFont.getData().setScale(ENEMY_NAME_TAG_FONT_SCALE);
    }

    /**
     * Draws a white-pixel quad centered at {@code (centerX, centerY)} with the given size, rotated by
     * {@code rotationDegrees} about its center. The batch colour must be set by the caller.
     */
    private void drawCenteredQuad(float centerX, float centerY, float width, float height, float rotationDegrees) {
        batch.draw(whitePixelTexture,
                   centerX - width / 2f, centerY - height / 2f,
                   width / 2f, height / 2f,
                   width, height,
                   1f, 1f,
                   rotationDegrees,
                   0, 0, 1, 1,
                   false, false);
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapeRenderer.dispose();
        blightSheetTexture.dispose();
        infernalSheetTexture.dispose();
        blightCorruptorTexture.dispose();
        vortexEyeTexture.dispose();
        ghoulTexture.dispose();
        crawlerTexture.dispose();
        revenantTexture.dispose();
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

        // Boss types — reuse the blight sheet PLAGUE_HULK region as a placeholder until
        // dedicated boss texture assets are available.
        TextureRegion bossPlaceholder = new TextureRegion(blightSheet, 0, 0, blightHalfW, blightHalfH);
        map.put(EnemyType.OVERSEER,    bossPlaceholder);
        map.put(EnemyType.CORRUPTOR,   bossPlaceholder);
        map.put(EnemyType.HELL_BARON,  bossPlaceholder);

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
