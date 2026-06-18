package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.status.StatusEffect;
import ge.tbegvadze.toon3d.status.StatusType;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.EffectConstants;

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
    // BURNING=0 POISONED=1 BLEED=2 STUNNED=3 BLINDED=4 SLOWED=5 EMPOWERED=6
    // BLEED uses deep crimson screen-edge tint distinct from BURNING orange.
    private static final float[] EFFECT_RED   = { 1.00f, 0.00f, 0.85f, 1.00f, 0.00f, 0.25f, 0.85f };
    private static final float[] EFFECT_GREEN = { 0.45f, 0.80f, 0.05f, 1.00f, 0.00f, 0.40f, 0.10f };
    private static final float[] EFFECT_BLUE  = { 0.00f, 0.15f, 0.10f, 1.00f, 0.00f, 0.70f, 0.00f };

    private final SpriteBatch batch;
    private final Texture     vignetteTexture;

    private final float[] currentIntensity = new float[TYPE_COUNT];
    private final float[] targetIntensity  = new float[TYPE_COUNT];

    public StatusEffectVignetteRenderer() {
        batch           = new SpriteBatch();
        vignetteTexture = buildVignetteTexture();
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
    }

    public void render(OrthographicCamera camera) {
        boolean anyVisible = false;
        for (int typeIndex = 0; typeIndex < TYPE_COUNT; typeIndex++) {
            if (currentIntensity[typeIndex] > 0.001f) { anyVisible = true; break; }
        }
        if (!anyVisible) return;

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

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

    @Override
    public void dispose() {
        batch.dispose();
        vignetteTexture.dispose();
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
}
