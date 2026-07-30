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
import ge.tbegvadze.toon3d.entity.boss.Boss;
import ge.tbegvadze.toon3d.entity.boss.DangerTileSet;
import ge.tbegvadze.toon3d.status.StatusEffect;
import ge.tbegvadze.toon3d.status.StatusType;
import ge.tbegvadze.toon3d.util.CombatPalette;
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
    // Elemental golem 2x2 sheet — only Q4 (AURIC_SENTINEL) has a shipped archetype today.
    private final Texture                       elementalGolemSheetTexture;
    // Necrotic faction — one standalone Texture per archetype (drawn full-frame).
    private final Texture                       blightCorruptorTexture;
    private final Texture                       vortexEyeTexture;
    private final Texture                       ghoulTexture;
    private final Texture                       crawlerTexture;
    private final Texture                       revenantTexture;
    // Overseer boss — dedicated full-frame boss portrait (ORDER 8: wire the existing asset instead of the
    // blight-sheet placeholder, so the reworked mobile Overseer renders as its own sprite at boss height).
    private final Texture                       overseerBossTexture;
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

    // Pre-allocated orbiting-shard-ring cache (elemental-golem-auric-sentinel) — parallel to
    // sortedIndices, populated in pass 1 and consumed by the shard pass (pass 1.55). The ring draws every
    // frame for every visible Sentinel, so the geometry is cached rather than recomputed, and NOTHING
    // here allocates: the per-shard transforms are plain floats resolved inside the draw loop.
    private final float[]   shardDepths;
    private final boolean[] drawShardFlags;

    // Pre-allocated CRUST-PLATE / heat-haze cache (elemental-golem-cinderforge-colossus) — parallel to
    // sortedIndices, populated in pass 1 and consumed by the crust pass (pass 1.56). Only a flag is
    // needed: the pass reuses the fx* billboard geometry and sortedDepths, and every per-plate transform
    // is a local float. Nothing here allocates.
    private final boolean[] drawCrustFlags;

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

    // Reused per-frame RGB buffer for the current boss telegraph quad colour (ORDER 8) — no allocation
    private final float[]   bossTelegraphColorRgb = new float[3];

    // Name tag and HP text rendering — font and layouts pre-allocated to avoid render-loop allocation
    private final BitmapFont  nameTagFont;
    private final GlyphLayout nameTagLayout;
    private final GlyphLayout hpTextLayout;
    // Line height of the name-tag font at its default scale — used to reserve on-screen headroom for
    // the floating UI cluster so it never clips off the top when an enemy is point-blank.
    private final float       nameTagLineHeight;
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
        this.shardDepths         = new float[scratchSize];
        this.drawShardFlags      = new boolean[scratchSize];
        this.drawCrustFlags      = new boolean[scratchSize];
        this.intentCenterXs      = new float[scratchSize];
        this.intentClusterTops   = new float[scratchSize];
        this.drawIntentFlags     = new boolean[scratchSize];
        this.shapeRenderer       = new ShapeRenderer();
        this.batch               = new SpriteBatch(WALL_PROJECTION_SCREEN_WIDTH);

        // Load sprite sheets; fall back to a solid-colour placeholder when the file is absent
        this.blightSheetTexture   = loadSheetOrFallback(ENEMY_SHEET_BLIGHT_PATH,   0.60f, 0.20f, 0.60f);
        this.infernalSheetTexture = loadSheetOrFallback(ENEMY_SHEET_INFERNAL_PATH, 0.70f, 0.25f, 0.10f);
        // Elemental golems — amber/gold fallback if the shared sheet is absent.
        this.elementalGolemSheetTexture = loadSheetOrFallback(ENEMY_SHEET_ELEMENTAL_GOLEMS_PATH,
                0.85f, 0.65f, 0.20f);
        // Necrotic faction — each archetype is a single standalone PNG drawn full-frame.
        this.blightCorruptorTexture = loadSheetOrFallback(ENEMY_BLIGHT_CORRUPTOR_PATH, 0.40f, 0.60f, 0.20f);
        this.vortexEyeTexture       = loadSheetOrFallback(ENEMY_VORTEX_EYE_PATH,       0.55f, 0.25f, 0.70f);
        this.ghoulTexture           = loadSheetOrFallback(ENEMY_GHOUL_PATH,            0.45f, 0.55f, 0.35f);
        this.crawlerTexture         = loadSheetOrFallback(ENEMY_CRAWLER_PATH,          0.55f, 0.45f, 0.30f);
        this.revenantTexture        = loadSheetOrFallback(ENEMY_REVENANT_PATH,         0.50f, 0.50f, 0.55f);
        // Overseer boss portrait — its own full-frame texture (ORDER 8), cyan-tinted fallback if absent.
        this.overseerBossTexture    = loadSheetOrFallback(ENEMY_BOSS_OVERSEER_PATH,     0.55f, 0.80f, 0.95f);
        this.textureRegions       = buildTextureRegions(blightSheetTexture, infernalSheetTexture,
                                                       elementalGolemSheetTexture);
        // Map the standalone PNGs as full-frame regions (one whole image per archetype).
        textureRegions.put(EnemyType.BLIGHT_CORRUPTOR, new TextureRegion(blightCorruptorTexture));
        textureRegions.put(EnemyType.VORTEX_EYE,       new TextureRegion(vortexEyeTexture));
        textureRegions.put(EnemyType.GHOUL,            new TextureRegion(ghoulTexture));
        textureRegions.put(EnemyType.CRAWLER,          new TextureRegion(crawlerTexture));
        textureRegions.put(EnemyType.REVENANT,         new TextureRegion(revenantTexture));
        // Overseer boss — override the blight-sheet placeholder with its dedicated full-frame texture.
        textureRegions.put(EnemyType.OVERSEER,         new TextureRegion(overseerBossTexture));

        this.whitePixelTexture    = buildWhitePixelTexture();
        this.nameTagFont          = new BitmapFont();
        this.nameTagFont.getData().setScale(ENEMY_NAME_TAG_FONT_SCALE);
        this.nameTagLayout        = new GlyphLayout();
        this.hpTextLayout         = new GlyphLayout();
        this.nameTagLineHeight    = this.nameTagFont.getLineHeight();
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

            // ORDER 2: render at the interpolated slide position (renderCenter*), so a boss mid-dash
            // is culled/depth-sorted where the sprite actually is. No-op for a settled enemy.
            float tileOffsetX = (enemy.renderCenterX() - playerWorldX) / CELL_SIZE;
            float tileOffsetY = (enemy.renderCenterY() - playerWorldY) / CELL_SIZE;
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
            drawShardFlags[sortedPosition]  = false;
            drawCrustFlags[sortedPosition]  = false;
            drawIntentFlags[sortedPosition] = false;
            float attackAnimStrength      = enemy.getAttackAnimStrength();

            TextureRegion region = textureRegions.get(enemy.type);
            if (region == null) continue;

            // ORDER 2: draw the billboard at the interpolated slide position so a dashing boss visibly
            // sweeps across the tiles it crossed instead of popping. Health bar / intent / beam passes
            // reuse the screenCenterColumn computed here, so they follow the slide automatically.
            float tileOffsetX = (enemy.renderCenterX() - playerWorldX) / CELL_SIZE;
            float tileOffsetY = (enemy.renderCenterY() - playerWorldY) / CELL_SIZE;

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

            // Order-6 power indicators — layered over any DoT tint so a marked target still reads.
            // Colours come from the shared CombatPalette (one combat colour language): VULNERABLE red
            // ("hit this now"), EXPOSED purple (Block shredded), WEAK a grey-purple dampening.
            if (enemy.getActiveEffects().get(StatusType.VULNERABLE).isActive()) {
                spriteRed   = GameMath.lerp(spriteRed,   CombatPalette.VULNERABLE_RED,   tintStrength);
                spriteGreen = GameMath.lerp(spriteGreen, CombatPalette.VULNERABLE_GREEN, tintStrength);
                spriteBlue  = GameMath.lerp(spriteBlue,  CombatPalette.VULNERABLE_BLUE,  tintStrength);
            }
            if (enemy.isExposed()) {
                spriteRed   = GameMath.lerp(spriteRed,   CombatPalette.EXPOSED_RED,   tintStrength);
                spriteGreen = GameMath.lerp(spriteGreen, CombatPalette.EXPOSED_GREEN, tintStrength);
                spriteBlue  = GameMath.lerp(spriteBlue,  CombatPalette.EXPOSED_BLUE,  tintStrength);
            }
            if (enemy.getActiveEffects().get(StatusType.WEAK).isActive()) {
                spriteRed   = GameMath.lerp(spriteRed,   CombatPalette.WEAK_RED,   tintStrength);
                spriteGreen = GameMath.lerp(spriteGreen, CombatPalette.WEAK_GREEN, tintStrength);
                spriteBlue  = GameMath.lerp(spriteBlue,  CombatPalette.WEAK_BLUE,  tintStrength);
            }

            // Telegraph tint: brief danger flash at the instant of attack. A priming SELF_DESTRUCT
            // (plague-hulk-self-destruct.txt) gets its own sickly-green over-pressurize glow instead
            // of the shared red melee/charge flash, so the brace visibly reads as a bomb, not a swing.
            float telegraphStrength = enemy.getTelegraphStrength();
            if (telegraphStrength > 0f) {
                if (enemy.state == EnemyState.SELF_DESTRUCTING) {
                    spriteRed   = GameMath.lerp(spriteRed,   INTENT_COLOR_SELF_DESTRUCT_RED,   telegraphStrength);
                    spriteGreen = GameMath.lerp(spriteGreen, INTENT_COLOR_SELF_DESTRUCT_GREEN, telegraphStrength);
                    spriteBlue  = GameMath.lerp(spriteBlue,  INTENT_COLOR_SELF_DESTRUCT_BLUE,  telegraphStrength);
                } else {
                    spriteRed   = GameMath.lerp(spriteRed,   EffectConstants.MELEE_TELEGRAPH_R, telegraphStrength);
                    spriteGreen = GameMath.lerp(spriteGreen, EffectConstants.MELEE_TELEGRAPH_G, telegraphStrength);
                    spriteBlue  = GameMath.lerp(spriteBlue,  EffectConstants.MELEE_TELEGRAPH_B, telegraphStrength);
                }
            }

            // AURIC SENTINEL shard states (elemental-golem-auric-sentinel), in the order they must read:
            //   1. EXPOSED — a shardless Sentinel drops from amber glow to a dull violet matte. It looks
            //      fragile because it IS: no shards, 70 HP and no answer. This is the "hit it NOW" cue.
            //   2. ABSORB  — a GOLD flash instead of the usual white hit flash. The enemy took a hit and
            //      its health did not move, and the player must see that difference instantly; without
            //      it the Sentinel reads as a bullet sponge rather than a puzzle. Drawn after the matte
            //      so the flash still reads on an already-exposed body (a hit that ate its LAST shard).
            if (enemy.type.maxShards() > 0) {
                if (enemy.shardCount == 0) {
                    spriteRed   = GameMath.lerp(spriteRed,   AURIC_EXPOSED_TINT_R, AURIC_EXPOSED_TINT_STRENGTH);
                    spriteGreen = GameMath.lerp(spriteGreen, AURIC_EXPOSED_TINT_G, AURIC_EXPOSED_TINT_STRENGTH);
                    spriteBlue  = GameMath.lerp(spriteBlue,  AURIC_EXPOSED_TINT_B, AURIC_EXPOSED_TINT_STRENGTH);
                }
                float shardFlashStrength = enemy.getShardAbsorbFlashStrength();
                if (shardFlashStrength > 0f) {
                    spriteRed   = GameMath.lerp(spriteRed,   AURIC_ABSORB_FLASH_R, shardFlashStrength);
                    spriteGreen = GameMath.lerp(spriteGreen, AURIC_ABSORB_FLASH_G, shardFlashStrength);
                    spriteBlue  = GameMath.lerp(spriteBlue,  AURIC_ABSORB_FLASH_B, shardFlashStrength);
                }
            }

            // CINDERFORGE COLOSSUS — SEAM GLOW = SOFTNESS (elemental-golem-cinderforge-colossus). The
            // sprite's tint is driven by the eased crust level, so "why did that shot feel weak" is
            // always answerable by LOOKING at it: blazing orange at zero stacks, darkened charcoal at a
            // full shell. The Q2 art is already a lattice of dark crust over glowing seams, so this only
            // turns up or down what the sprite is already saying.
            //
            // CRITICAL: the cooled state must never be confusable with the DORMANT_SHADE_DAMPEN darkening
            // used for a SLEEPING enemy — "hardened" reading as "asleep" would invert the tactical
            // meaning entirely. So a cooled Colossus keeps a hot orange RIM: its red channel is floored,
            // and the additive seam/haze pass below still burns. A dormant enemy has neither.
            if (enemy.type.crustMaxStacks() > 0) {
                float crustLevel = enemy.getCrustTintLevel();
                float targetRed   = GameMath.lerp(CINDERFORGE_TINT_HOT_R, CINDERFORGE_TINT_COOLED_R, crustLevel);
                float targetGreen = GameMath.lerp(CINDERFORGE_TINT_HOT_G, CINDERFORGE_TINT_COOLED_G, crustLevel);
                float targetBlue  = GameMath.lerp(CINDERFORGE_TINT_HOT_B, CINDERFORGE_TINT_COOLED_B, crustLevel);
                spriteRed   = GameMath.lerp(spriteRed,   targetRed,   CINDERFORGE_TINT_STRENGTH);
                spriteGreen = GameMath.lerp(spriteGreen, targetGreen, CINDERFORGE_TINT_STRENGTH);
                spriteBlue  = GameMath.lerp(spriteBlue,  targetBlue,  CINDERFORGE_TINT_STRENGTH);
                spriteRed   = Math.max(spriteRed, baseShade * CINDERFORGE_RIM_MIN_RED);
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

            // Boss state feedback (ORDER 8): overlay the enraged (phase 2) or repairing (ORDER 5) tint so
            // both states are unmistakable at a glance. REPAIRING wins when both could apply — it is the
            // "go stop it" beat, and a hot-red enrage tint under a green heal glow would read muddily.
            if (enemy instanceof Boss) {
                Boss bossEnemy = (Boss) enemy;
                if (bossEnemy.regenState.healActive) {
                    float pulse    = 0.5f + 0.5f * MathUtils.sin(
                            statusAnimationClock * BOSS_REPAIR_TINT_PULSE_HZ * MathUtils.PI2);
                    float strength = Math.min(1f, BOSS_REPAIR_TINT_STRENGTH
                            + BOSS_REPAIR_TINT_PULSE_AMOUNT * pulse);
                    spriteRed   = GameMath.lerp(spriteRed,   BOSS_REPAIR_TINT_R, strength);
                    spriteGreen = GameMath.lerp(spriteGreen, BOSS_REPAIR_TINT_G, strength);
                    spriteBlue  = GameMath.lerp(spriteBlue,  BOSS_REPAIR_TINT_B, strength);
                } else if (bossEnemy.phase >= 2) {
                    float pulse    = 0.5f + 0.5f * MathUtils.sin(
                            statusAnimationClock * BOSS_ENRAGE_TINT_PULSE_HZ * MathUtils.PI2);
                    float strength = Math.min(1f, BOSS_ENRAGE_TINT_STRENGTH
                            + BOSS_ENRAGE_TINT_PULSE_AMOUNT * pulse);
                    spriteRed   = GameMath.lerp(spriteRed,   BOSS_ENRAGE_TINT_R, strength);
                    spriteGreen = GameMath.lerp(spriteGreen, BOSS_ENRAGE_TINT_G, strength);
                    spriteBlue  = GameMath.lerp(spriteBlue,  BOSS_ENRAGE_TINT_B, strength);
                }
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

            // Cache the shard-ring draw for pass 1.55 — the fx* geometry above is exactly what the ring
            // needs, so only the depth and the flag are extra. Flagged even at zero shards is pointless,
            // so an empty ring simply never enters the pass.
            shardDepths[sortedPosition]     = depth;
            drawShardFlags[sortedPosition]  = enemy.shardCount > 0;

            // Cache the crust-plate / heat-haze draw for pass 1.56. Every magma construct gets the haze
            // (it is always hot, even bare), so the flag is the ARCHETYPE, not the live stack count.
            drawCrustFlags[sortedPosition]  = enemy.type.crustMaxStacks() > 0;

            // Dash afterimage / motion trail (ORDER 8): draw fading ghost billboards at the trailing tiles
            // of the slide path so a multi-tile boss dash reads as a deliberate sprint, not a teleport
            // (Fairness F3). Emitted here — before the solid sprite's draw loop below — so the live sprite
            // renders over the ghosts. Only while a slide is active.
            if (enemy instanceof Boss && enemy.isSliding()) {
                drawBossAfterimageTrail(enemy, region, heightMultiplier,
                        spriteRed, spriteGreen, spriteBlue);
            }

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

            // ---- Floating UI cluster geometry (health bar + name tag + intent icon) ----
            // Computed once per alerted enemy and shared by the bar cache (pass 2) and the intent
            // anchor (pass 2.5) so both stay aligned. Every element is clamped inside the screen so a
            // point-blank enemy — whose billboard fills the view — never has its cluster slide off the
            // top or off the sides. Distant enemies are unaffected (their raw anchor is already inside).
            if (enemy.isAlerted()) {
                // Bar size clamped so a screen-filling sprite does not spawn a screen-spanning bar.
                float barWidth  = Math.min(ENEMY_HEALTH_BAR_MAX_WIDTH_PIXELS,
                                           spriteScreenWidth * ENEMY_HEALTH_BAR_WIDTH_FRACTION);
                float barHeight = Math.min(ENEMY_HEALTH_BAR_MAX_PIXELS,
                                           Math.max(ENEMY_HEALTH_BAR_MIN_PIXELS,
                                                    spriteScreenHeight * ENEMY_HEALTH_BAR_HEIGHT_FRACTION));

                // Horizontal clamp: keep the whole cluster (widest element is the bar) fully on screen.
                float halfBarWidth = barWidth / 2f;
                float uiCenterX    = MathUtils.clamp(screenCenterColumn,
                        ENEMY_UI_SCREEN_EDGE_MARGIN + halfBarWidth,
                        WALL_PROJECTION_SCREEN_WIDTH - ENEMY_UI_SCREEN_EDGE_MARGIN - halfBarWidth);
                float barLeft      = uiCenterX - halfBarWidth;

                // Vertical clamp: reserve headroom above the bar bottom for the tallest possible stack
                // (name tag + intent icon with its number band and word label) so nothing clips the top.
                float nameTagBand = nameTagLineHeight + ENEMY_NAME_TAG_BAR_GAP;
                float labelBand   = nameTagLineHeight * (INTENT_LABEL_FONT_SCALE / ENEMY_NAME_TAG_FONT_SCALE)
                                    + INTENT_LABEL_GAP_ABOVE_ICON + 2f * INTENT_LABEL_PLATE_PAD_Y;
                float intentBand  = INTENT_ICON_NAMETAG_CLEARANCE
                                    + INTENT_ICON_MAX_SIZE * INTENT_NUMBER_BAND_FRACTION
                                    + INTENT_ICON_Y_OFFSET_ABOVE_HEALTHBAR
                                    + INTENT_ICON_MAX_SIZE * (1f + INTENT_POP_SCALE_BONUS)
                                    + INTENT_ICON_BOB_AMPLITUDE
                                    + labelBand;
                float clusterHeadroom = barHeight + nameTagBand + intentBand;
                float maxBarBottom    = WALL_PROJECTION_SCREEN_HEIGHT
                                        - ENEMY_UI_SCREEN_EDGE_MARGIN - clusterHeadroom;
                float rawBarBottom      = drawTop + spriteScreenHeight * ENEMY_HEALTH_BAR_GAP_FRACTION;
                float barBottomPosition = Math.min(rawBarBottom, maxBarBottom);

                // Bar cache for pass 2 — only within the bar draw distance.
                if (depth <= ENEMY_HEALTH_BAR_MAX_DISTANCE_TILES) {
                    float fillFraction = Math.max(0f, Math.min(1f,
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

                // Intent anchor for the telegraph pass (order-2). Only alerted enemies with a committed
                // plan reveal intent — DORMANT/un-committed enemies show nothing (no info leak). Uses the
                // SAME clamped bar geometry so the icon stacks cleanly above the on-screen cluster.
                if (enemy.plannedAction.committed && depth <= INTENT_ICON_MAX_DISTANCE_TILES) {
                    intentCenterXs[sortedPosition]    = uiCenterX;
                    intentClusterTops[sortedPosition] = barBottomPosition + barHeight
                                                        + INTENT_ICON_NAMETAG_CLEARANCE;
                    drawIntentFlags[sortedPosition]   = true;
                }
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
        // Pass 1.55: ORBITING SHARD RING (elemental-golem-auric-sentinel) — THE SIGNATURE.
        // One additive gold quad per live shard, orbiting the billboard. This ring is the archetype's
        // entire readability contract: the count is simultaneously the enemy's remaining armour (each
        // shard eats one hit outright) and its remaining ammunition (each shot spends one), so the
        // player reads both off the same ring with no HUD, no bar and no text. Drawn AFTER the sprite
        // so the shards sit in front of the body, and behind the health-bar cluster.
        // Zero allocations: every per-shard transform is a local float from GameMath.
        // =====================================================================
        boolean anyShardRing = false;
        for (int sortedPosition = 0; sortedPosition < visibleCount; sortedPosition++) {
            if (drawShardFlags[sortedPosition]) { anyShardRing = true; break; }
        }
        if (anyShardRing) {
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE); // additive gold
            batch.begin();
            for (int sortedPosition = 0; sortedPosition < visibleCount; sortedPosition++) {
                if (!drawShardFlags[sortedPosition]) continue;
                Enemy enemy = enemies.get(sortedIndices[sortedPosition]);

                float centerColumn       = fxCenterColumns[sortedPosition];
                int   centerScreenColumn = (int) centerColumn;
                if (centerScreenColumn < 0 || centerScreenColumn >= WALL_PROJECTION_SCREEN_WIDTH) continue;
                // Same centre-column wall occlusion test the bars and affliction overlays use.
                if (shardDepths[sortedPosition] >= wallRenderer.getZBufferUnchecked(centerScreenColumn)) continue;

                float spriteHeight = fxSpriteHeights[sortedPosition];
                float spriteWidth  = fxSpriteWidths[sortedPosition];
                float orbitRadius  = spriteWidth  * AURIC_SHARD_ORBIT_RADIUS_FRACTION;
                float ringCenterY  = fxDrawBottoms[sortedPosition]
                                     + spriteHeight * AURIC_SHARD_ORBIT_HEIGHT_FRACTION;
                float nominalSize  = spriteHeight * AURIC_SHARD_SIZE_FRACTION;
                // After ANY change to the count the survivors slide from their OLD spacing to their new
                // even spacing, so a lost shard reads as the ring closing up rather than as a jump.
                float respaceProgress = enemy.getShardRespaceProgress();

                batch.setColor(AURIC_SHARD_R, AURIC_SHARD_G, AURIC_SHARD_B, 1f);
                for (int shardIndex = 0; shardIndex < enemy.shardCount; shardIndex++) {
                    float angleRadians = GameMath.shardOrbitAngleRadians(
                            statusAnimationClock, AURIC_SHARD_ORBIT_SPEED, shardIndex,
                            enemy.shardCount, enemy.shardCountBeforeAbsorb, respaceProgress);
                    float shardCenterX = centerColumn
                            + GameMath.shardOrbitOffsetX(angleRadians, orbitRadius);
                    // Independent slow bob per shard, phase-offset by index so they never lock step.
                    float shardCenterY = ringCenterY + GameMath.pickupBobOffset(
                            statusAnimationClock, AURIC_SHARD_BOB_SPEED, AURIC_SHARD_BOB_AMPLITUDE,
                            shardIndex * MathUtils.PI2 / Math.max(1, enemy.shardCount), spriteHeight);
                    float shardSize = nominalSize
                            * GameMath.shardOrbitSizeScale(angleRadians, AURIC_SHARD_FAR_SIDE_SIZE_FALLOFF);
                    // Rotate each quad by its own orbit angle so the shards visibly tumble as they sweep.
                    drawCenteredQuad(shardCenterX, shardCenterY, shardSize, shardSize,
                            angleRadians * MathUtils.radiansToDegrees);
                }
            }
            batch.setColor(Color.WHITE);
            batch.end();
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA); // restore alpha blend
        }

        // =====================================================================
        // Pass 1.56: CRUST PLATES + HEAT HAZE (elemental-golem-cinderforge-colossus).
        // The plates are a LITERAL COUNT of the Colossus's armour — one dark plate per live crust stack,
        // at fixed body positions (shoulders, chest, hips) — drawn in ordinary alpha blend because they
        // must DARKEN the body, which additive blending cannot do. A glowing molten seam is drawn
        // additively under each one, and a shimmer band at the feet sells the mass and the temperature.
        //
        // Same readability discipline as the Auric ring: the count lives on the BODY, legible at a glance
        // and at distance, with no HUD, no bar and no text. Drawn after the sprite so the plates sit on
        // the body, and before the health-bar cluster. Zero allocations — every transform is a local float.
        // =====================================================================
        boolean anyCrust = false;
        for (int sortedPosition = 0; sortedPosition < visibleCount; sortedPosition++) {
            if (drawCrustFlags[sortedPosition]) { anyCrust = true; break; }
        }
        if (anyCrust) {
            // --- Sub-pass A: the dark plates, in the DEFAULT alpha blend (they subtract light).
            batch.begin();
            for (int sortedPosition = 0; sortedPosition < visibleCount; sortedPosition++) {
                if (!drawCrustFlags[sortedPosition]) continue;
                Enemy enemy = enemies.get(sortedIndices[sortedPosition]);
                if (enemy.crustStacks <= 0) continue;   // a bare Colossus wears no plates
                if (isCrustBillboardOccluded(sortedPosition)) continue;

                float centerColumn = fxCenterColumns[sortedPosition];
                float spriteHeight = fxSpriteHeights[sortedPosition];
                float spriteWidth  = fxSpriteWidths[sortedPosition];
                float drawBottom   = fxDrawBottoms[sortedPosition];
                float plateWidth   = spriteWidth  * CINDERFORGE_CRUST_PLATE_WIDTH_FRACTION;
                float plateHeight  = spriteHeight * CINDERFORGE_CRUST_PLATE_HEIGHT_FRACTION;

                batch.setColor(CINDERFORGE_CRUST_PLATE_R, CINDERFORGE_CRUST_PLATE_G,
                               CINDERFORGE_CRUST_PLATE_B, CINDERFORGE_CRUST_PLATE_ALPHA);
                int plateCount = Math.min(enemy.crustStacks, CINDERFORGE_CRUST_PLATE_OFFSET_X.length);
                for (int plateIndex = 0; plateIndex < plateCount; plateIndex++) {
                    float plateCenterX = centerColumn
                            + spriteWidth  * CINDERFORGE_CRUST_PLATE_OFFSET_X[plateIndex];
                    float plateCenterY = drawBottom
                            + spriteHeight * CINDERFORGE_CRUST_PLATE_OFFSET_Y[plateIndex];
                    drawCenteredQuad(plateCenterX, plateCenterY, plateWidth, plateHeight, 0f);
                }
            }
            batch.setColor(Color.WHITE);
            batch.end();

            // --- Sub-pass B: the additive molten glow — a seam under each plate plus the base shimmer.
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE); // additive magma
            batch.begin();
            for (int sortedPosition = 0; sortedPosition < visibleCount; sortedPosition++) {
                if (!drawCrustFlags[sortedPosition]) continue;
                Enemy enemy = enemies.get(sortedIndices[sortedPosition]);
                if (isCrustBillboardOccluded(sortedPosition)) continue;

                float centerColumn = fxCenterColumns[sortedPosition];
                float spriteHeight = fxSpriteHeights[sortedPosition];
                float spriteWidth  = fxSpriteWidths[sortedPosition];
                float drawBottom   = fxDrawBottoms[sortedPosition];

                // Molten seam under each plate: the crust is a crust OVER something still burning, and
                // this line is what keeps a fully cooled Colossus from ever reading as switched off.
                float plateWidth  = spriteWidth  * CINDERFORGE_CRUST_PLATE_WIDTH_FRACTION;
                float plateHeight = spriteHeight * CINDERFORGE_CRUST_PLATE_HEIGHT_FRACTION;
                float seamHeight  = plateHeight * CINDERFORGE_CRUST_SEAM_HEIGHT_FRACTION;
                batch.setColor(CINDERFORGE_TINT_HOT_R, CINDERFORGE_TINT_HOT_G, CINDERFORGE_TINT_HOT_B, 1f);
                int plateCount = Math.min(enemy.crustStacks, CINDERFORGE_CRUST_PLATE_OFFSET_X.length);
                for (int plateIndex = 0; plateIndex < plateCount; plateIndex++) {
                    float seamCenterX = centerColumn
                            + spriteWidth  * CINDERFORGE_CRUST_PLATE_OFFSET_X[plateIndex];
                    float seamCenterY = drawBottom
                            + spriteHeight * CINDERFORGE_CRUST_PLATE_OFFSET_Y[plateIndex]
                            - plateHeight / 2f;
                    drawCenteredQuad(seamCenterX, seamCenterY, plateWidth, seamHeight, 0f);
                }

                // HEAT HAZE: a shimmer band at the feet, sold as a few additive slivers whose horizontal
                // offset oscillates out of phase. This renderer draws the sprite one texture COLUMN at a
                // time, so a true refraction re-sample has nowhere to live; the shimmer reads the same
                // from a metre away and costs four quads.
                float bandHeight   = spriteHeight * CINDERFORGE_HEAT_HAZE_ZONE_FRACTION;
                float sliverHeight = bandHeight / CINDERFORGE_HEAT_HAZE_SLIVERS;
                float amplitude    = spriteWidth * CINDERFORGE_HEAT_HAZE_AMPLITUDE;
                batch.setColor(CINDERFORGE_TINT_HOT_R, CINDERFORGE_TINT_HOT_G, CINDERFORGE_TINT_HOT_B,
                               CINDERFORGE_HEAT_HAZE_ALPHA);
                for (int sliverIndex = 0; sliverIndex < CINDERFORGE_HEAT_HAZE_SLIVERS; sliverIndex++) {
                    // Phase-offset per sliver so the band ripples rather than sliding as one block.
                    float phase = sliverIndex * MathUtils.PI2 / CINDERFORGE_HEAT_HAZE_SLIVERS;
                    float wobble = amplitude * MathUtils.sin(
                            statusAnimationClock * CINDERFORGE_HEAT_HAZE_SPEED + phase);
                    float sliverCenterY = drawBottom + sliverHeight * (sliverIndex + 0.5f);
                    drawCenteredQuad(centerColumn + wobble, sliverCenterY,
                                     spriteWidth * 0.9f, sliverHeight * 0.5f, 0f);
                }
            }
            batch.setColor(Color.WHITE);
            batch.end();
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA); // restore alpha blend
        }

        // =====================================================================
        // Pass 1.6: SUMMON telegraph — pulsing spore runes on the pre-selected empty spawn cells
        // For every enemy whose committed SPECIAL is SUMMON, draw an additive summoning circle on each
        // target tile's floor so the incoming spawn is a readable, dodge-able intent on the ground
        // (strategy-combat-order-5). Reuses the pass-1 billboard projection and the same center-column
        // wall Z-buffer occlusion as the bars; iterates the full list (summoners are few) — no allocation.
        // =====================================================================
        boolean anySummonTelegraph = false;
        for (int enemyIndex = 0; enemyIndex < enemyCount; enemyIndex++) {
            if (isSummonTelegraphing(enemies.get(enemyIndex))) { anySummonTelegraph = true; break; }
        }
        if (anySummonTelegraph) {
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE); // additive glow
            batch.begin();
            for (int enemyIndex = 0; enemyIndex < enemyCount; enemyIndex++) {
                Enemy enemy = enemies.get(enemyIndex);
                if (!isSummonTelegraphing(enemy)) continue;
                for (int targetIndex = 0; targetIndex < enemy.summonTargetCount; targetIndex++) {
                    drawSummonTargetMarker(enemy.summonTargetColumns[targetIndex],
                                           enemy.summonTargetRows[targetIndex]);
                }
            }
            batch.setColor(Color.WHITE);
            batch.end();
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA); // restore default alpha blend
        }

        // =====================================================================
        // Pass 1.7: Boss telegraph floor overlay (boss-fight-mobile-overseer ORDER 8)
        // For every boss with an armed DangerTileSet, draw a flat coloured quad on each danger tile's
        // floor — one COLOUR + SHAPE per threat (Fairness F6) so the player reads what lands where a turn
        // ahead. Reuses the same billboard projection + centre-column wall Z test as the summon marker;
        // iterates the (few) enemies, boss-filtered — no allocation.
        // =====================================================================
        boolean anyBossTelegraph = false;
        for (int enemyIndex = 0; enemyIndex < enemyCount; enemyIndex++) {
            Enemy enemy = enemies.get(enemyIndex);
            if (enemy instanceof Boss && enemy.isAlive() && ((Boss) enemy).dangerTileSet.isActive()) {
                anyBossTelegraph = true;
                break;
            }
        }
        if (anyBossTelegraph) {
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE); // additive glow
            batch.begin();
            for (int enemyIndex = 0; enemyIndex < enemyCount; enemyIndex++) {
                Enemy enemy = enemies.get(enemyIndex);
                if (!(enemy instanceof Boss) || !enemy.isAlive()) continue;
                Boss bossEnemy = (Boss) enemy;
                DangerTileSet dangerTileSet = bossEnemy.dangerTileSet;
                if (!dangerTileSet.isActive()) continue;
                DangerTileSet.TelegraphKind kind = dangerTileSet.getTelegraphKind();
                List<DangerTileSet.MarkedTile> tiles = dangerTileSet.getTiles();
                for (int tileIndex = 0; tileIndex < tiles.size(); tileIndex++) {
                    DangerTileSet.MarkedTile tile = tiles.get(tileIndex);
                    drawBossTelegraphTile(tile.tileColumn, tile.tileRow, kind, bossEnemy);
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

            // Priority read (strategy-combat-order-6): find the biggest committed hit this turn so its
            // frame can be brightened — teaching the "who to kill first" read without ever auto-playing.
            int highestPredictedDamage = 0;
            for (int sortedPosition = 0; sortedPosition < visibleCount; sortedPosition++) {
                if (!drawIntentFlags[sortedPosition]) continue;
                int predicted = enemies.get(sortedIndices[sortedPosition]).plannedAction.predictedDamage;
                if (predicted > highestPredictedDamage) highestPredictedDamage = predicted;
            }

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
                float numberBand = showsNumber ? iconSize * INTENT_NUMBER_BAND_FRACTION : 0f;
                float baseY = intentClusterTops[sortedPosition] + bob;
                float iconCenterY = baseY + numberBand + INTENT_ICON_Y_OFFSET_ABOVE_HEALTHBAR + drawnSize / 2f;

                boolean locked = verb == IntentVerb.ATTACK_RANGED
                        && isSameCardinalTile(enemy.tileColumn, enemy.tileRow, playerTileColumn, playerTileRow);

                resolveIntentFrameColor(verb, intentFrameColor);
                // SELF_DESTRUCT (Plague Hulk finisher) overrides the shared purple SPECIAL fill with a
                // sickly green so it is never mistaken for a buff/debuff/summon/area-strike telegraph.
                if (verb == IntentVerb.SPECIAL && plan.specialAbility == SpecialAbility.SELF_DESTRUCT) {
                    intentFrameColor[0] = INTENT_COLOR_SELF_DESTRUCT_RED;
                    intentFrameColor[1] = INTENT_COLOR_SELF_DESTRUCT_GREEN;
                    intentFrameColor[2] = INTENT_COLOR_SELF_DESTRUCT_BLUE;
                }
                // Priority read (order-6): brighten the frame of the biggest committed hit AND of
                // support casters (BUFF_SELF) — "kill the buffer / the big number first" — so the
                // salient target pops. Pure emphasis on the existing frame; never an auto-play prompt.
                boolean isPriorityThreat = highestPredictedDamage > 0
                        && plan.predictedDamage == highestPredictedDamage;
                boolean isSupportCaster  = verb == IntentVerb.SPECIAL
                        && plan.specialAbility == SpecialAbility.BUFF_SELF;
                if (isPriorityThreat || isSupportCaster) {
                    intentFrameColor[0] = GameMath.lerp(intentFrameColor[0], 1f, INTENT_PRIORITY_FRAME_BRIGHTEN);
                    intentFrameColor[1] = GameMath.lerp(intentFrameColor[1], 1f, INTENT_PRIORITY_FRAME_BRIGHTEN);
                    intentFrameColor[2] = GameMath.lerp(intentFrameColor[2], 1f, INTENT_PRIORITY_FRAME_BRIGHTEN);
                }
                drawIntentFrame(centerX, iconCenterY, drawnSize, intentFrameColor, locked);
                drawIntentGlyph(verb, enemy, plan, centerX, iconCenterY, drawnSize);

                if (showsNumber && depth <= INTENT_DAMAGE_MAX_DISTANCE_TILES) {
                    boolean isBlock = verb == IntentVerb.DEFEND;
                    int value = isBlock ? plan.blockGain : plan.predictedDamage;
                    if (value > 0) {
                        drawIntentNumber(centerX, baseY + numberBand, value, isBlock);
                    }
                }

                // Plain-word label above the icon ("Attacking", "Moving", ...) so a new player never
                // has to decode the glyph. Colour-matched to the frame for a single reading; suppressed
                // at range to keep distant rooms clean.
                if (depth <= INTENT_LABEL_MAX_DISTANCE_TILES) {
                    float iconTop = iconCenterY + drawnSize / 2f;
                    drawIntentLabel(intentLabelText(verb, enemy, plan), centerX, iconTop,
                            intentFrameColor[0], intentFrameColor[1], intentFrameColor[2]);
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

    /** True when this enemy is telegraphing a SUMMON (committed SPECIAL/SUMMON with live target cells). */
    private static boolean isSummonTelegraphing(Enemy enemy) {
        return enemy.isAlive()
                && enemy.plannedAction.committed
                && enemy.plannedAction.verb == IntentVerb.SPECIAL
                && enemy.plannedAction.specialAbility == SpecialAbility.SUMMON
                && enemy.summonTargetCount > 0;
    }

    /*
     * Draws one pulsing "spore rune" on a pre-selected summon target tile's floor (inside an already-open
     * additive batch). Projects the tile centre through the same billboard math the sprites use, culls it
     * behind the player / out of range / behind a nearer wall (centre-column Z test), then draws a central
     * glow mote ringed by orbiting motes squashed vertically so the circle reads as lying flat on the
     * ground. Everything breathes on a wall-clock pulse — cosmetic only, never read by the simulation.
     */
    private void drawSummonTargetMarker(int tileColumn, int tileRow) {
        float worldCenterX = tileColumn * CELL_SIZE + CELL_SIZE / 2f;
        float worldCenterY = tileRow    * CELL_SIZE + CELL_SIZE / 2f;
        float tileOffsetX  = (worldCenterX - playerWorldX) / CELL_SIZE;
        float tileOffsetY  = (worldCenterY - playerWorldY) / CELL_SIZE;

        float depth = GameMath.spriteDepth(tileOffsetX, tileOffsetY, directionX, directionY);
        if (depth <= PROP_BEHIND_PLAYER_EPSILON_TILES) return;
        if (depth > SUMMON_MARKER_MAX_DISTANCE_TILES)  return;

        float screenCenterColumn = GameMath.spriteScreenColumnCenter(
                tileOffsetX, tileOffsetY, directionX, directionY, planeX, planeY, WALL_PROJECTION_SCREEN_WIDTH);
        int centerColumn = (int) screenCenterColumn;
        if (centerColumn < 0 || centerColumn >= WALL_PROJECTION_SCREEN_WIDTH) return;
        if (depth >= wallRenderer.getZBufferUnchecked(centerColumn)) return; // occluded by a nearer wall

        float fullWallLineHeight = GameMath.spriteScreenHeight(WALL_PROJECTION_SCREEN_HEIGHT, depth);
        float floorY     = GameMath.wallStripeDrawBottom(WALL_PROJECTION_SCREEN_HEIGHT, fullWallLineHeight);
        float markerSize = fullWallLineHeight * SUMMON_MARKER_SIZE_FRACTION;
        if (markerSize < 1f) return;
        float centerY = floorY + markerSize * SUMMON_MARKER_FLOOR_LIFT_FRACTION;

        float pulse = 0.5f + 0.5f * (float) Math.sin(
                statusAnimationClock * SUMMON_MARKER_PULSE_HZ * MathUtils.PI2);
        float ringRadius = markerSize * (0.35f + 0.15f * pulse);
        float moteSize   = markerSize * 0.22f;
        float alpha      = SUMMON_MARKER_MAX_ALPHA * (0.45f + 0.55f * pulse);

        batch.setColor(SUMMON_MARKER_R, SUMMON_MARKER_G, SUMMON_MARKER_B, alpha);
        batch.draw(whitePixelTexture, screenCenterColumn - moteSize / 2f, centerY - moteSize / 2f,
                moteSize, moteSize, 0, 0, 1, 1, false, false);

        for (int moteIndex = 0; moteIndex < SUMMON_MARKER_MOTE_COUNT; moteIndex++) {
            float angle = moteIndex / (float) SUMMON_MARKER_MOTE_COUNT * MathUtils.PI2
                    + statusAnimationClock * SUMMON_MARKER_PULSE_HZ;
            float moteX = screenCenterColumn + (float) Math.cos(angle) * ringRadius;
            float moteY = centerY + (float) Math.sin(angle) * ringRadius * 0.5f; // squash → floor plane
            batch.draw(whitePixelTexture, moteX - moteSize / 2f, moteY - moteSize / 2f,
                    moteSize, moteSize, 0, 0, 1, 1, false, false);
        }
    }

    /*
     * Draws one boss telegraph quad on a danger tile's floor (ORDER 8), inside the already-open additive
     * batch. Projects the tile centre through the same billboard math the summon marker uses and applies
     * the same cull + centre-column wall Z test. The COLOUR comes from the threat kind (the learned
     * vocabulary); the SHAPE also varies by kind so the overlay is legible without colour (Fairness F6,
     * colour-blind safety): CHARGE → a slim red LINE bar, MELEE → an ORANGE ring of motes, fire/toxic/
     * generic → a filled PATCH squashed onto the floor plane, summon → a PURPLE rune circle. Everything
     * breathes on the wall-clock pulse. Cosmetic only — never read by the simulation.
     */
    private void drawBossTelegraphTile(int tileColumn, int tileRow,
                                       DangerTileSet.TelegraphKind kind, Boss boss) {
        float worldCenterX = tileColumn * CELL_SIZE + CELL_SIZE / 2f;
        float worldCenterY = tileRow    * CELL_SIZE + CELL_SIZE / 2f;
        float tileOffsetX  = (worldCenterX - playerWorldX) / CELL_SIZE;
        float tileOffsetY  = (worldCenterY - playerWorldY) / CELL_SIZE;

        float depth = GameMath.spriteDepth(tileOffsetX, tileOffsetY, directionX, directionY);
        if (depth <= PROP_BEHIND_PLAYER_EPSILON_TILES)   return;
        if (depth > BOSS_TELEGRAPH_MAX_DISTANCE_TILES)   return;

        float screenCenterColumn = GameMath.spriteScreenColumnCenter(
                tileOffsetX, tileOffsetY, directionX, directionY, planeX, planeY,
                WALL_PROJECTION_SCREEN_WIDTH);
        int centerColumn = (int) screenCenterColumn;
        if (centerColumn < 0 || centerColumn >= WALL_PROJECTION_SCREEN_WIDTH) return;
        if (depth >= wallRenderer.getZBufferUnchecked(centerColumn)) return; // occluded by a nearer wall

        float fullWallLineHeight = GameMath.spriteScreenHeight(WALL_PROJECTION_SCREEN_HEIGHT, depth);
        float floorY      = GameMath.wallStripeDrawBottom(WALL_PROJECTION_SCREEN_HEIGHT, fullWallLineHeight);
        float markerSize  = fullWallLineHeight * BOSS_TELEGRAPH_SIZE_FRACTION;
        if (markerSize < 1f) return;
        float centerY     = floorY + markerSize * BOSS_TELEGRAPH_FLOOR_LIFT_FRACTION;

        float pulse = 0.5f + 0.5f * MathUtils.sin(
                statusAnimationClock * BOSS_TELEGRAPH_PULSE_HZ * MathUtils.PI2);
        float alpha = BOSS_TELEGRAPH_MAX_ALPHA * (0.45f + 0.55f * pulse);

        // Resolve the threat colour into the reusable scratch buffer (no allocation).
        resolveTelegraphColor(kind, boss, bossTelegraphColorRgb);
        float red   = bossTelegraphColorRgb[0];
        float green = bossTelegraphColorRgb[1];
        float blue  = bossTelegraphColorRgb[2];
        batch.setColor(red, green, blue, alpha);

        switch (kind) {
            case CHARGE_LINE: {
                // Slim horizontal bar lying on the floor — the "line" shape of the imminent lunge.
                float barWidth  = markerSize * BOSS_TELEGRAPH_FILL_FRACTION;
                float barHeight = markerSize * BOSS_TELEGRAPH_LINE_THICKNESS_FRACTION;
                batch.draw(whitePixelTexture, screenCenterColumn - barWidth / 2f, centerY - barHeight / 2f,
                        barWidth, barHeight, 0, 0, 1, 1, false, false);
                break;
            }
            case MELEE_RING:
            case SUMMON_RUNES: {
                // Ring / rune circle of orbiting motes squashed onto the floor plane.
                float ringRadius = markerSize * (0.35f + 0.15f * pulse);
                float moteSize   = markerSize * BOSS_TELEGRAPH_MOTE_SIZE_FRACTION;
                for (int moteIndex = 0; moteIndex < BOSS_TELEGRAPH_MOTE_COUNT; moteIndex++) {
                    float angle = moteIndex / (float) BOSS_TELEGRAPH_MOTE_COUNT * MathUtils.PI2
                            + statusAnimationClock * BOSS_TELEGRAPH_PULSE_HZ;
                    float moteX = screenCenterColumn + (float) Math.cos(angle) * ringRadius;
                    float moteY = centerY + (float) Math.sin(angle) * ringRadius * 0.5f; // squash → floor
                    batch.draw(whitePixelTexture, moteX - moteSize / 2f, moteY - moteSize / 2f,
                            moteSize, moteSize, 0, 0, 1, 1, false, false);
                }
                break;
            }
            case HAZARD_FIRE:
            case HAZARD_TOXIC:
            case GENERIC:
            default: {
                // Filled patch squashed vertically so it reads as lying flat where the hazard lands.
                float patchWidth  = markerSize * BOSS_TELEGRAPH_FILL_FRACTION;
                float patchHeight = markerSize * BOSS_TELEGRAPH_FILL_FRACTION * 0.5f; // floor-plane squash
                batch.draw(whitePixelTexture, screenCenterColumn - patchWidth / 2f, centerY - patchHeight / 2f,
                        patchWidth, patchHeight, 0, 0, 1, 1, false, false);
                break;
            }
        }
    }

    /** Writes the telegraph threat colour (RGB) into {@code out} for the given kind; GENERIC uses the boss accent. */
    private static void resolveTelegraphColor(DangerTileSet.TelegraphKind kind, Boss boss, float[] out) {
        switch (kind) {
            case CHARGE_LINE:
                out[0] = BOSS_TELEGRAPH_CHARGE_R; out[1] = BOSS_TELEGRAPH_CHARGE_G; out[2] = BOSS_TELEGRAPH_CHARGE_B; break;
            case MELEE_RING:
                out[0] = BOSS_TELEGRAPH_MELEE_R;  out[1] = BOSS_TELEGRAPH_MELEE_G;  out[2] = BOSS_TELEGRAPH_MELEE_B;  break;
            case HAZARD_FIRE:
                out[0] = BOSS_TELEGRAPH_FIRE_R;   out[1] = BOSS_TELEGRAPH_FIRE_G;   out[2] = BOSS_TELEGRAPH_FIRE_B;   break;
            case HAZARD_TOXIC:
                out[0] = BOSS_TELEGRAPH_TOXIC_R;  out[1] = BOSS_TELEGRAPH_TOXIC_G;  out[2] = BOSS_TELEGRAPH_TOXIC_B;  break;
            case SUMMON_RUNES:
                out[0] = BOSS_TELEGRAPH_SUMMON_R; out[1] = BOSS_TELEGRAPH_SUMMON_G; out[2] = BOSS_TELEGRAPH_SUMMON_B; break;
            case GENERIC:
            default:
                // Fall back to the boss's own accent so unclassified telegraphs still read as "this boss".
                out[0] = boss.accentRed; out[1] = boss.accentGreen; out[2] = boss.accentBlue; break;
        }
    }

    /*
     * Draws the boss dash afterimage trail (ORDER 8) inside the already-open pass-1 sprite batch. Samples
     * a few progress points BEHIND the live slide position, projects each through the same billboard math
     * the solid sprite uses, and draws a fading full-region ghost at each (nearest ghost brightest). Each
     * ghost is culled behind the player / out of range / behind a nearer wall via the same centre-column
     * wall Z test the summon marker uses. Ghosts reuse the live sprite's tinted shade at reduced alpha so
     * they read as motion smear, not a second creature. Allocation-free; restores full opacity on exit.
     */
    private void drawBossAfterimageTrail(Enemy enemy, TextureRegion region, float heightMultiplier,
                                         float baseRed, float baseGreen, float baseBlue) {
        float liveProgress = enemy.slideProgress();
        int   regionWidth  = region.getRegionWidth();
        int   regionHeight = region.getRegionHeight();
        float aspectRatio  = (float) regionWidth / regionHeight;

        for (int ghostIndex = 1; ghostIndex <= BOSS_AFTERIMAGE_COUNT; ghostIndex++) {
            float rawProgress = liveProgress - ghostIndex * BOSS_AFTERIMAGE_PROGRESS_STEP;
            if (rawProgress <= 0f) continue;   // ghost would sit at/behind the origin tile

            float easedProgress = GameMath.smoothstep01(rawProgress);
            float ghostColumn   = enemy.sampleSlideColumn(easedProgress);
            float ghostRow      = enemy.sampleSlideRow(easedProgress);

            float worldCenterX = ghostColumn * CELL_SIZE + CELL_SIZE / 2f;
            float worldCenterY = ghostRow    * CELL_SIZE + CELL_SIZE / 2f;
            float tileOffsetX  = (worldCenterX - playerWorldX) / CELL_SIZE;
            float tileOffsetY  = (worldCenterY - playerWorldY) / CELL_SIZE;

            float depth = GameMath.spriteDepth(tileOffsetX, tileOffsetY, directionX, directionY);
            if (depth <= PROP_BEHIND_PLAYER_EPSILON_TILES) continue;
            if (depth > MAX_ENEMY_DRAW_DISTANCE_TILES)     continue;

            float screenCenterColumn = GameMath.spriteScreenColumnCenter(
                    tileOffsetX, tileOffsetY, directionX, directionY, planeX, planeY,
                    WALL_PROJECTION_SCREEN_WIDTH);
            int centerColumn = (int) screenCenterColumn;
            if (centerColumn < 0 || centerColumn >= WALL_PROJECTION_SCREEN_WIDTH) continue;
            if (depth >= wallRenderer.getZBufferUnchecked(centerColumn)) continue; // behind a nearer wall

            float fullWallLineHeight = GameMath.spriteScreenHeight(WALL_PROJECTION_SCREEN_HEIGHT, depth);
            float ghostHeight        = fullWallLineHeight * heightMultiplier;
            float ghostWidth         = ghostHeight * aspectRatio;
            if (ghostWidth < 1f || ghostHeight < 1f) continue;
            float drawBottom         = GameMath.wallStripeDrawBottom(WALL_PROJECTION_SCREEN_HEIGHT,
                    fullWallLineHeight);

            float alpha = BOSS_AFTERIMAGE_START_ALPHA
                    * (float) Math.pow(BOSS_AFTERIMAGE_ALPHA_FALLOFF, ghostIndex - 1);
            if (alpha <= 0.01f) continue;

            batch.setColor(baseRed, baseGreen, baseBlue, alpha);
            batch.draw(region, screenCenterColumn - ghostWidth / 2f, drawBottom, ghostWidth, ghostHeight);
        }
        batch.setColor(Color.WHITE);
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
            case SELF_DESTRUCT: {
                // Bomb — a round diamond body with a diagonal fuse and a spark at its tip.
                drawCenteredQuad(centerX, centerY, glyph * 0.60f, glyph * 0.60f, 45f);
                float fuseLength = glyph * 0.34f;
                float fuseTipX   = centerX + MathUtils.cosDeg(60f) * fuseLength;
                float fuseTipY   = centerY + MathUtils.sinDeg(60f) * fuseLength;
                drawCenteredQuad((centerX + fuseTipX) * 0.5f, (centerY + fuseTipY) * 0.5f,
                        fuseLength, thickness * 0.8f, 60f);
                drawCenteredQuad(fuseTipX, fuseTipY, thickness * 2.4f, thickness * 2.4f, 45f);
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

    /**
     * The plain-word description shown under an intent icon for new-player legibility. Returns interned
     * string literals only (no per-frame allocation). SPECIAL resolves to its sub-ability's verb; a
     * SELF_DESTRUCT special additionally needs the caster's live countdown, hence the enemy parameter.
     */
    private static String intentLabelText(IntentVerb verb, Enemy enemy, PlannedAction plan) {
        switch (verb) {
            case ATTACK_MELEE:  return "Attacking";
            case ATTACK_RANGED: return "Shooting";
            case WIND_UP:       return "Charging";
            case MOVE:          return "Moving";
            case DEFEND:        return "Defending";
            case SPECIAL:       return specialLabelText(plan.specialAbility, enemy);
            case STUNNED:       return "Stunned";
            case WAIT:
            default:            return "Waiting";
        }
    }

    // Pre-built interned labels for the SELF_DESTRUCT countdown — indexed by turnsRemaining so the
    // live number never requires a per-frame String allocation (project rule: no allocation in render).
    private static final String[] SELF_DESTRUCT_COUNTDOWN_LABELS = {
            "DETONATING", "SELF-DESTRUCT · 1", "SELF-DESTRUCT · 2",
            "SELF-DESTRUCT · 3", "SELF-DESTRUCT · 4", "SELF-DESTRUCT · 5"
    };

    /** The plain-word label for a SPECIAL ability (buff/debuff/summon/area); "Special" if unknown. */
    private static String specialLabelText(SpecialAbility ability, Enemy enemy) {
        if (ability == null) return "Special";
        switch (ability) {
            case BUFF_SELF:     return "Empowering";
            case DEBUFF_PLAYER: return "Weakening";
            case SUMMON:        return "Summoning";
            case AREA_STRIKE:   return "Area Attack";
            case SELF_DESTRUCT: {
                int index = MathUtils.clamp(enemy.selfDestructTurnsRemaining,
                        0, SELF_DESTRUCT_COUNTDOWN_LABELS.length - 1);
                return SELF_DESTRUCT_COUNTDOWN_LABELS[index];
            }
            default:            return "Special";
        }
    }

    /**
     * Draws the intent word label centered at {@code centerX}, sitting just above {@code iconTopY}.
     * A dark backing plate is drawn first so the words stay readable over bright/noisy walls, then the
     * main text — the frame colour brightened toward white so tinted hues (blue/purple) stay legible.
     */
    private void drawIntentLabel(String label, float centerX, float iconTopY,
                                 float red, float green, float blue) {
        nameTagFont.getData().setScale(INTENT_LABEL_FONT_SCALE);
        hpTextLayout.setText(nameTagFont, label);
        float textWidth  = hpTextLayout.width;
        float textHeight = hpTextLayout.height;
        float textX = centerX - textWidth / 2f;
        float textY = iconTopY + INTENT_LABEL_GAP_ABOVE_ICON + INTENT_LABEL_PLATE_PAD_Y + textHeight;

        // Backing plate: a dark quad behind the glyph box (font.draw y is the TOP of the text, so the
        // box spans [textY - textHeight, textY]).
        batch.setColor(INTENT_LABEL_PLATE_RED, INTENT_LABEL_PLATE_GREEN,
                INTENT_LABEL_PLATE_BLUE, INTENT_LABEL_PLATE_ALPHA);
        batch.draw(whitePixelTexture,
                textX - INTENT_LABEL_PLATE_PAD_X, textY - textHeight - INTENT_LABEL_PLATE_PAD_Y,
                textWidth + 2f * INTENT_LABEL_PLATE_PAD_X, textHeight + 2f * INTENT_LABEL_PLATE_PAD_Y,
                0, 0, 1, 1, false, false);
        batch.setColor(Color.WHITE);

        float legibleRed   = GameMath.lerpTowardWhite(red,   INTENT_LABEL_WHITEN);
        float legibleGreen = GameMath.lerpTowardWhite(green, INTENT_LABEL_WHITEN);
        float legibleBlue  = GameMath.lerpTowardWhite(blue,  INTENT_LABEL_WHITEN);
        nameTagFont.setColor(legibleRed, legibleGreen, legibleBlue, 1f);
        nameTagFont.draw(batch, hpTextLayout, textX, textY);
        nameTagFont.getData().setScale(ENEMY_NAME_TAG_FONT_SCALE);
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
     * The same centre-column wall-occlusion test the shard, affliction and health-bar passes use, hoisted
     * into a helper because the crust pass runs it in two sub-passes. Returns true when the billboard's
     * centre column is off-screen or hidden behind a nearer wall, so the caller skips its overlay.
     */
    private boolean isCrustBillboardOccluded(int sortedPosition) {
        int centerScreenColumn = (int) fxCenterColumns[sortedPosition];
        if (centerScreenColumn < 0 || centerScreenColumn >= WALL_PROJECTION_SCREEN_WIDTH) return true;
        return sortedDepths[sortedPosition] >= wallRenderer.getZBufferUnchecked(centerScreenColumn);
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
        elementalGolemSheetTexture.dispose();
        blightCorruptorTexture.dispose();
        vortexEyeTexture.dispose();
        ghoulTexture.dispose();
        crawlerTexture.dispose();
        revenantTexture.dispose();
        overseerBossTexture.dispose();
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
                                                                      Texture infernalSheet,
                                                                      Texture elementalGolemSheet) {
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

        // Elemental golem sheet. Q1 (0,0) and Q3 (0,halfH) are the Rimeshell Lancer and Verdant
        // Spiresower — art that ships ahead of its archetypes (.claude/agents/ideas/elemental-golem-*.txt)
        // and so is not mapped yet. An unmapped type is skipped by the draw loop's null-region guard,
        // never drawn from a wrong quadrant. TextureRegion y=0 is the TOP of the image, so Q2 (the
        // Cinderforge Colossus) is the TOP-RIGHT quadrant.
        int golemHalfW = elementalGolemSheet.getWidth()  / 2;
        int golemHalfH = elementalGolemSheet.getHeight() / 2;
        map.put(EnemyType.CINDERFORGE_COLOSSUS,
                new TextureRegion(elementalGolemSheet, golemHalfW, 0,          golemHalfW, golemHalfH));
        map.put(EnemyType.AURIC_SENTINEL,
                new TextureRegion(elementalGolemSheet, golemHalfW, golemHalfH, golemHalfW, golemHalfH));

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
