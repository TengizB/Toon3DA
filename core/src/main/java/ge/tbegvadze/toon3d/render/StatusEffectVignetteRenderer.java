package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.status.StatusEffect;
import ge.tbegvadze.toon3d.status.StatusType;
import ge.tbegvadze.toon3d.util.CombatPalette;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.EffectConstants;
import ge.tbegvadze.toon3d.util.GameMath;

/**
 * Draws colored screen-edge vignettes to communicate which status effects are
 * active on the player.  Each effect has a distinct color:
 *
 *   BURNING   → orange (1.0, 0.45, 0.0)   gentle flicker tint on fire edges
 *   POISONED  → green  (0.0, 0.80, 0.15)  intensity scales with stack count
 *   BLEED     → crimson(0.85,0.05, 0.10)  deep red edge pulse, distinct from BURNING
 *   STUNNED   → white  (1.0, 1.0,  1.0)   brief flash (handled via event text)
 *   BLINDED   → black  (0.0, 0.0,  0.0)   heavy tunnel-vision overlay
 *   SLOWED    → blue   (0.25,0.40, 0.70)  cold sluggish edge tint
 *   EMPOWERED → red    (0.85,0.10, 0.0)   adrenaline glow (distinct from damage red)
 *
 * Intensities smoothly fade in/out between turns using real-time delta so the
 * screen never snaps; the gameplay state is turn-based but the visuals are fluid.
 * Uses a single radial-gradient texture shared across all overlay draws — only the
 * SpriteBatch color changes per draw.  Zero per-frame allocations.
 */
public final class StatusEffectVignetteRenderer implements Disposable {

    private static final int GRADIENT_SIZE = 256;
    private static final StatusType[] STATUS_TYPES = StatusType.values();
    private static final int TYPE_COUNT = STATUS_TYPES.length;

    // Per-effect RGB color values (matched to StatusType ordinals)
    // BURNING=0 POISONED=1 BLEED=2 STUNNED=3 BLINDED=4 SLOWED=5 EMPOWERED=6 VULNERABLE=7 WEAK=8 EXPOSED=9
    // BLEED uses deep crimson screen-edge tint distinct from BURNING orange.
    // The order-6 powers (7-9) share the combat colour language: VULNERABLE red (danger — you take
    // more), WEAK muted grey-purple (your output is dimmed), EXPOSED purple (special/anti-turtle).
    private static final float[] EFFECT_RED   = { 1.00f, 0.00f, 0.85f, 1.00f, 0.00f, 0.25f, 0.85f,
            CombatPalette.VULNERABLE_RED,   CombatPalette.WEAK_RED,   CombatPalette.EXPOSED_RED };
    private static final float[] EFFECT_GREEN = { 0.45f, 0.80f, 0.05f, 1.00f, 0.00f, 0.40f, 0.10f,
            CombatPalette.VULNERABLE_GREEN, CombatPalette.WEAK_GREEN, CombatPalette.EXPOSED_GREEN };
    private static final float[] EFFECT_BLUE  = { 0.00f, 0.15f, 0.10f, 1.00f, 0.00f, 0.70f, 0.00f,
            CombatPalette.VULNERABLE_BLUE,  CombatPalette.WEAK_BLUE,  CombatPalette.EXPOSED_BLUE };
    static {
        // Fail fast if a new StatusType was added without extending the per-effect colour arrays.
        if (EFFECT_RED.length != TYPE_COUNT || EFFECT_GREEN.length != TYPE_COUNT || EFFECT_BLUE.length != TYPE_COUNT) {
            throw new IllegalStateException("EFFECT_* colour arrays must match StatusType count");
        }
    }

    private final SpriteBatch batch;
    private final Texture     vignetteTexture;
    // White (tintable-to-any-hue) radial gradient used by the low-HP layers; the red
    // vignetteTexture above can only draw red, so low-HP darkening needs a neutral texture.
    private final Texture     lowHpVignetteTexture;

    private final float[] currentIntensity = new float[TYPE_COUNT];
    private final float[] targetIntensity  = new float[TYPE_COUNT];

    // ── Low-HP heartbeat state (1A) — a persistent condition re-evaluated from HP each frame ──
    private float   lowHpPresence     = 0f;  // smoothed 0..1 presence of the low-HP state
    private float   lowHpCriticalT    = 0f;  // 0 at the WOUNDED fraction, 1 at/below the CRITICAL fraction
    private float   lowHpClockSeconds = 0f;  // wall-clock accumulator driving the heartbeat phase
    private boolean lowHpWasActive    = false;

    public StatusEffectVignetteRenderer() {
        batch                = new SpriteBatch();
        vignetteTexture      = buildVignetteTexture();
        lowHpVignetteTexture = buildWhiteVignetteTexture();
    }

    /**
     * Called every frame in World.update() BEFORE render().
     * Reads the player's active effects and smoothly lerps vignette intensities toward
     * their targets so the visuals fade in and out fluidly between turns.
     */
    public void update(float deltaTime, Player player) {
        for (int typeIndex = 0; typeIndex < TYPE_COUNT; typeIndex++) {
            StatusEffect effect = player.getActiveEffects().get(STATUS_TYPES[typeIndex]);
            if (effect.isActive()) {
                float poisonScale = (STATUS_TYPES[typeIndex] == StatusType.POISONED)
                        ? Math.min(1.0f, (float) effect.getStacks() / EffectConstants.POISON_MAX_STACKS)
                        : 1.0f;
                targetIntensity[typeIndex] = poisonScale;
            } else {
                targetIntensity[typeIndex] = 0.0f;
            }

            float target = targetIntensity[typeIndex];
            if (currentIntensity[typeIndex] < target) {
                currentIntensity[typeIndex] = Math.min(target,
                        currentIntensity[typeIndex] + deltaTime / EffectConstants.STATUS_VIGNETTE_FADE_IN_SECONDS);
            } else if (currentIntensity[typeIndex] > target) {
                currentIntensity[typeIndex] = Math.max(target,
                        currentIntensity[typeIndex] - deltaTime / EffectConstants.STATUS_VIGNETTE_FADE_OUT_SECONDS);
            }
        }

        updateLowHp(deltaTime, player);
    }

    /**
     * Recomputes the persistent low-HP heartbeat state from the player's current HP fraction.
     * Below LOW_HP_WOUNDED_FRACTION the screen breathes red and the edges darken; both scale
     * continuously toward LOW_HP_CRITICAL_FRACTION (and below) via {@code lowHpCriticalT}.
     * A short fade smooths entering/leaving the state so nothing snaps when HP crosses a threshold
     * or a heal lifts the player back out of danger. Dropping fresh into low-HP restarts the
     * rhythm so the first heartbeat lands immediately as a strong "lub".
     */
    private void updateLowHp(float deltaTime, Player player) {
        lowHpClockSeconds += deltaTime;

        float healthFraction = player.getHealthFraction();
        boolean lowActive = player.isAlive()
                && healthFraction <= EffectConstants.LOW_HP_WOUNDED_FRACTION;
        if (lowActive && !lowHpWasActive) {
            lowHpClockSeconds = 0f;
        }
        lowHpWasActive = lowActive;

        float presenceTarget = lowActive ? 1f : 0f;
        if (lowHpPresence < presenceTarget) {
            lowHpPresence = Math.min(presenceTarget,
                    lowHpPresence + deltaTime / EffectConstants.LOW_HP_FADE_SECONDS);
        } else if (lowHpPresence > presenceTarget) {
            lowHpPresence = Math.max(presenceTarget,
                    lowHpPresence - deltaTime / EffectConstants.LOW_HP_FADE_SECONDS);
        }

        float woundedToCritical = EffectConstants.LOW_HP_WOUNDED_FRACTION
                - EffectConstants.LOW_HP_CRITICAL_FRACTION;
        float rawCritical = woundedToCritical <= 0f ? 1f
                : (EffectConstants.LOW_HP_WOUNDED_FRACTION - healthFraction) / woundedToCritical;
        lowHpCriticalT = Math.max(0f, Math.min(1f, rawCritical));
    }

    public void render(OrthographicCamera camera) {
        boolean lowHpVisible = lowHpPresence > 0.001f;
        boolean anyStatus = false;
        for (int typeIndex = 0; typeIndex < TYPE_COUNT; typeIndex++) {
            if (currentIntensity[typeIndex] > 0.001f) { anyStatus = true; break; }
        }
        if (!lowHpVisible && !anyStatus) return;

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        // Low-HP layers draw FIRST so status tints (burn/poison/etc.) still read on top of them.
        if (lowHpVisible) {
            renderLowHp();
        }

        for (int typeIndex = 0; typeIndex < TYPE_COUNT; typeIndex++) {
            float intensity = currentIntensity[typeIndex];
            if (intensity <= 0.001f) continue;

            float maxAlpha = (STATUS_TYPES[typeIndex] == StatusType.BLINDED)
                    ? EffectConstants.STATUS_BLIND_VIGNETTE_ALPHA
                    : EffectConstants.STATUS_VIGNETTE_MAX_ALPHA;
            float alpha = intensity * maxAlpha;

            batch.setColor(EFFECT_RED[typeIndex], EFFECT_GREEN[typeIndex], EFFECT_BLUE[typeIndex], alpha);
            batch.draw(vignetteTexture, 0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);
        }

        batch.setColor(1f, 1f, 1f, 1f);
        batch.end();
    }

    /**
     * Draws the two low-HP layers inside an already-open batch:
     *   1. Tunnel-vision darkening — near-black radial edges that deepen as HP nears zero.
     *   2. Red "breathing" — a heartbeat-driven red edge pulse whose period shortens with severity.
     * Both scale with the smoothed presence so they fade in/out rather than snapping.
     */
    private void renderLowHp() {
        // 1) Darkening — steady tunnel vision, deepening from LOW_HP_DARKEN_MIN_SCALE up to full.
        float darkenScale = lowHpPresence
                * GameMath.lerp(EffectConstants.LOW_HP_DARKEN_MIN_SCALE, 1f, lowHpCriticalT);
        float darkenAlpha = darkenScale * EffectConstants.LOW_HP_DARKEN_MAX_ALPHA;
        batch.setColor(EffectConstants.LOW_HP_DARKEN_R, EffectConstants.LOW_HP_DARKEN_G,
                EffectConstants.LOW_HP_DARKEN_B, darkenAlpha);
        batch.draw(lowHpVignetteTexture, 0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);

        // 2) Breathing — heartbeat envelope; faster (shorter period) the closer to death.
        float period = GameMath.lerp(EffectConstants.LOW_HP_PERIOD_WOUNDED_SECONDS,
                EffectConstants.LOW_HP_PERIOD_CRITICAL_SECONDS, lowHpCriticalT);
        float beat = GameMath.heartbeatPulse(lowHpClockSeconds, period,
                EffectConstants.LOW_HP_THUMP_WIDTH_SECONDS,
                EffectConstants.LOW_HP_ECHO_GAP_SECONDS,
                EffectConstants.LOW_HP_ECHO_STRENGTH);
        float breathAlpha = lowHpPresence * beat * EffectConstants.LOW_HP_BREATH_MAX_ALPHA;
        batch.setColor(EffectConstants.LOW_HP_RED, EffectConstants.LOW_HP_GREEN,
                EffectConstants.LOW_HP_BLUE, breathAlpha);
        batch.draw(lowHpVignetteTexture, 0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);
    }

    @Override
    public void dispose() {
        batch.dispose();
        vignetteTexture.dispose();
        lowHpVignetteTexture.dispose();
    }

    /*
     * Formula: radial vignette gradient
     * Derivation: identical to HitVignetteRenderer — normalised distance from centre
     *             squared for smooth falloff; centre = transparent, rim = opaque.
     *             The SpriteBatch color tints the white gradient to the per-effect hue.
     * Edge cases: corners have d² > 1, clamped to 1 (fully opaque at extreme corners).
     */
    private static Texture buildVignetteTexture() {
        Pixmap pixmap    = new Pixmap(GRADIENT_SIZE, GRADIENT_SIZE, Pixmap.Format.RGBA8888);
        float halfSize   = GRADIENT_SIZE / 2f;
        for (int pixelY = 0; pixelY < GRADIENT_SIZE; pixelY++) {
            for (int pixelX = 0; pixelX < GRADIENT_SIZE; pixelX++) {
                float normalX        = (pixelX - halfSize) / halfSize;
                float normalY        = (pixelY - halfSize) / halfSize;
                float distanceSquared = normalX * normalX + normalY * normalY;
                float edgeAlpha      = Math.min(1f, distanceSquared);
                int   alphaInt       = (int) (edgeAlpha * 255f);
                pixmap.drawPixel(pixelX, pixelY, (255 << 24) | (0 << 16) | (0 << 8) | alphaInt);
            }
        }
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    /*
     * Formula: radial vignette gradient (white base)
     * Derivation: identical falloff to buildVignetteTexture, but the RGB is white (255,255,255)
     *             so SpriteBatch.setColor() can tint it to ANY hue. The red-based texture above
     *             multiplies away non-red channels; the low-HP darkening needs a near-black tint,
     *             so it must draw against this neutral white gradient instead.
     * Edge cases: corners have d² > 1, clamped to 1 (fully opaque at extreme corners).
     */
    private static Texture buildWhiteVignetteTexture() {
        Pixmap pixmap    = new Pixmap(GRADIENT_SIZE, GRADIENT_SIZE, Pixmap.Format.RGBA8888);
        float halfSize   = GRADIENT_SIZE / 2f;
        for (int pixelY = 0; pixelY < GRADIENT_SIZE; pixelY++) {
            for (int pixelX = 0; pixelX < GRADIENT_SIZE; pixelX++) {
                float normalX         = (pixelX - halfSize) / halfSize;
                float normalY         = (pixelY - halfSize) / halfSize;
                float distanceSquared = normalX * normalX + normalY * normalY;
                float edgeAlpha       = Math.min(1f, distanceSquared);
                int   alphaInt        = (int) (edgeAlpha * 255f);
                pixmap.drawPixel(pixelX, pixelY, (255 << 24) | (255 << 16) | (255 << 8) | alphaInt);
            }
        }
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }
}
