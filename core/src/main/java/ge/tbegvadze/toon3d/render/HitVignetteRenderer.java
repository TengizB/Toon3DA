package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.util.Constants;

/**
 * Draws a red radial-gradient vignette over the full screen when the player takes damage.
 * The gradient is baked into a 256×256 texture at construction — zero per-frame work.
 * Call setIntensity(1f) on damage; intensity decays to 0 over HIT_VIGNETTE_FADE_SECONDS.
 */
public final class HitVignetteRenderer implements Disposable {

    private static final int GRADIENT_SIZE = 256;

    private final SpriteBatch batch;
    private final Texture     vignetteTexture;
    private float             intensity;

    public HitVignetteRenderer() {
        batch           = new SpriteBatch();
        vignetteTexture = buildVignetteTexture();
        intensity       = 0f;
    }

    /** Sets the flash intensity to the given value (1 = full hit, decays to 0). */
    public void setIntensity(float newIntensity) {
        intensity = newIntensity;
    }

    /** Call once per frame in World.update() to decay the intensity. */
    public void update(float deltaTime) {
        if (intensity > 0f) {
            intensity = Math.max(0f, intensity - deltaTime / Constants.HIT_VIGNETTE_FADE_SECONDS);
        }
    }

    public void render(OrthographicCamera camera) {
        if (intensity <= 0f) return;
        float alpha = intensity * Constants.HIT_VIGNETTE_MAX_ALPHA;
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        batch.setColor(1f, 0f, 0f, alpha);
        batch.draw(vignetteTexture,
                0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);
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
