package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.EffectConstants;
import ge.tbegvadze.toon3d.util.GameMath;

/**
 * Draws a radial-gradient vignette over the full screen.
 * Two independent overlays share one texture:
 *   - Red (damage)    — triggered by setDamageIntensity(1f)
 *   - Cyan (level-up) — triggered by setLevelUpIntensity(1f)
 * The gradient is baked into a 256×256 texture at construction — zero per-frame work.
 */
public final class HitVignetteRenderer implements Disposable {

    private static final int GRADIENT_SIZE = 256;

    private final SpriteBatch batch;
    private final Texture     vignetteTexture;
    private float             damageIntensity;
    private float             levelUpIntensity;
    private float             healIntensity;
    private float             legendaryIntensity;
    private float             legendaryRed;
    private float             legendaryGreen;
    private float             legendaryBlue;
    // Heavy off-screen edge pulse (balance-overhaul order 4, H16): side mask set by World each update.
    private int               heavyEdgeSideMask;
    private float             heavyEdgeClockSeconds;
    private final Texture     whitePixelTexture;

    public HitVignetteRenderer() {
        batch              = new SpriteBatch();
        vignetteTexture    = buildVignetteTexture();
        whitePixelTexture  = buildWhitePixelTexture();
        damageIntensity    = 0f;
        levelUpIntensity   = 0f;
        healIntensity      = 0f;
        legendaryIntensity = 0f;
        legendaryRed       = 1f;
        legendaryGreen     = 1f;
        legendaryBlue      = 1f;
    }

    /** Triggers the red damage vignette at full intensity. */
    public void setIntensity(float newIntensity) {
        damageIntensity = newIntensity;
    }

    /** Triggers the cyan level-up vignette at full intensity. */
    public void setLevelUpIntensity(float newIntensity) {
        levelUpIntensity = newIntensity;
    }

    /** Triggers the green heal vignette at full intensity (call on any heal ability proc). */
    public void triggerHeal() {
        healIntensity = 1f;
    }

    /**
     * Triggers a slow colored vignette breath for legendary ability procs.
     * Color is set per-trigger so SOULFORGE breathes gold, HELLFIRE_NOVA breathes ember, etc.
     */
    public void triggerLegendary(float red, float green, float blue) {
        legendaryIntensity = 1f;
        legendaryRed       = red;
        legendaryGreen     = green;
        legendaryBlue      = blue;
    }

    /**
     * Sets which screen edges pulse red because an off-screen heavy has the player's tile marked
     * (GameMath.OFFSCREEN_SIDE_* bit mask; 0 = none). Pushed by World every update.
     */
    public void setHeavyEdgeSideMask(int sideMask) {
        heavyEdgeSideMask = sideMask;
    }

    /** Call once per frame in World.update() to decay all intensities. */
    public void update(float deltaTime) {
        if (heavyEdgeSideMask != 0) heavyEdgeClockSeconds += deltaTime;
        if (damageIntensity > 0f) {
            damageIntensity = Math.max(0f, damageIntensity - deltaTime / EffectConstants.HIT_VIGNETTE_FADE_SECONDS);
        }
        if (levelUpIntensity > 0f) {
            levelUpIntensity = Math.max(0f, levelUpIntensity - deltaTime / EffectConstants.LEVEL_UP_VIGNETTE_FADE_SECONDS);
        }
        if (healIntensity > 0f) {
            healIntensity = Math.max(0f, healIntensity - deltaTime / EffectConstants.HEAL_VIGNETTE_FADE_SECONDS);
        }
        if (legendaryIntensity > 0f) {
            legendaryIntensity = Math.max(0f, legendaryIntensity - deltaTime / EffectConstants.LEGENDARY_VIGNETTE_FADE_SECONDS);
        }
    }

    public void render(OrthographicCamera camera) {
        if (damageIntensity <= 0f && levelUpIntensity <= 0f
                && healIntensity <= 0f && legendaryIntensity <= 0f && heavyEdgeSideMask == 0) return;
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        if (damageIntensity > 0f) {
            float alpha = damageIntensity * EffectConstants.HIT_VIGNETTE_MAX_ALPHA;
            batch.setColor(1f, 0f, 0f, alpha);
            batch.draw(vignetteTexture, 0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);
        }
        if (levelUpIntensity > 0f) {
            float alpha = levelUpIntensity * EffectConstants.LEVEL_UP_VIGNETTE_MAX_ALPHA;
            batch.setColor(0f, 0.85f, 1f, alpha);
            batch.draw(vignetteTexture, 0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);
        }
        if (healIntensity > 0f) {
            float alpha = healIntensity * EffectConstants.HEAL_VIGNETTE_MAX_ALPHA;
            batch.setColor(0.25f, 1f, 0.25f, alpha);
            batch.draw(vignetteTexture, 0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);
        }
        if (legendaryIntensity > 0f) {
            float alpha = legendaryIntensity * EffectConstants.LEGENDARY_VIGNETTE_MAX_ALPHA;
            batch.setColor(legendaryRed, legendaryGreen, legendaryBlue, alpha);
            batch.draw(vignetteTexture, 0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);
        }
        if (heavyEdgeSideMask != 0) {
            float alpha = GameMath.pulseMultiplier(heavyEdgeClockSeconds, EffectConstants.HEAVY_EDGE_PULSE_HZ,
                    EffectConstants.HEAVY_EDGE_PULSE_MIN_ALPHA, EffectConstants.HEAVY_EDGE_PULSE_MAX_ALPHA);
            batch.setColor(EffectConstants.HEAVY_EDGE_PULSE_R, EffectConstants.HEAVY_EDGE_PULSE_G,
                    EffectConstants.HEAVY_EDGE_PULSE_B, alpha);
            float bandWidth = Constants.WORLD_WIDTH * EffectConstants.HEAVY_EDGE_PULSE_BAND_FRACTION;
            if ((heavyEdgeSideMask & GameMath.OFFSCREEN_SIDE_LEFT) != 0) {
                batch.draw(whitePixelTexture, 0f, 0f, bandWidth, Constants.WORLD_HEIGHT);
            }
            if ((heavyEdgeSideMask & GameMath.OFFSCREEN_SIDE_RIGHT) != 0) {
                batch.draw(whitePixelTexture, Constants.WORLD_WIDTH - bandWidth, 0f,
                        bandWidth, Constants.WORLD_HEIGHT);
            }
        }
        batch.setColor(1f, 1f, 1f, 1f);
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        vignetteTexture.dispose();
        whitePixelTexture.dispose();
    }

    private static Texture buildWhitePixelTexture() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(1f, 1f, 1f, 1f);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    /*
     * Formula: radial vignette gradient
     * Derivation: for each pixel compute normalised distance from centre (0..1), square it
     *             for a smooth falloff, then invert so the centre is transparent and edges opaque.
     *             Squaring d keeps the centre clear while pushing the red strongly to the rim.
     * Edge cases: pixels exactly at the corners have d > 1 (clamped to 1 → fully opaque edge).
     */
    private static Texture buildVignetteTexture() {
        Pixmap pixmap = new Pixmap(GRADIENT_SIZE, GRADIENT_SIZE, Pixmap.Format.RGBA8888);
        float halfSize = GRADIENT_SIZE / 2f;
        for (int pixelY = 0; pixelY < GRADIENT_SIZE; pixelY++) {
            for (int pixelX = 0; pixelX < GRADIENT_SIZE; pixelX++) {
                float normalX        = (pixelX - halfSize) / halfSize;
                float normalY        = (pixelY - halfSize) / halfSize;
                float distanceSquared = normalX * normalX + normalY * normalY;
                float edgeAlpha      = Math.min(1f, distanceSquared);
                int   alphaInt       = (int) (edgeAlpha * 255f);
                // White-with-radial-alpha so batch.setColor() tints faithfully in any hue
                pixmap.drawPixel(pixelX, pixelY, (255 << 24) | (255 << 16) | (255 << 8) | alphaInt);
            }
        }
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }
}
