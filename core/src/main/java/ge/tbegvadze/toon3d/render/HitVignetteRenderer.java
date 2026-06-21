package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.EffectConstants;

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

    public HitVignetteRenderer() {
        batch            = new SpriteBatch();
        vignetteTexture  = buildVignetteTexture();
        damageIntensity  = 0f;
        levelUpIntensity = 0f;
        healIntensity    = 0f;
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

    /** Call once per frame in World.update() to decay all intensities. */
    public void update(float deltaTime) {
        if (damageIntensity > 0f) {
            damageIntensity = Math.max(0f, damageIntensity - deltaTime / EffectConstants.HIT_VIGNETTE_FADE_SECONDS);
        }
        if (levelUpIntensity > 0f) {
            levelUpIntensity = Math.max(0f, levelUpIntensity - deltaTime / EffectConstants.LEVEL_UP_VIGNETTE_FADE_SECONDS);
        }
        if (healIntensity > 0f) {
            healIntensity = Math.max(0f, healIntensity - deltaTime / EffectConstants.HEAL_VIGNETTE_FADE_SECONDS);
        }
    }

    public void render(OrthographicCamera camera) {
        if (damageIntensity <= 0f && levelUpIntensity <= 0f && healIntensity <= 0f) return;
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
                // RGBA: red=255, green=0, blue=0, alpha=edgeAlpha
                pixmap.drawPixel(pixelX, pixelY, (255 << 24) | (0 << 16) | (0 << 8) | alphaInt);
            }
        }
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }
}
