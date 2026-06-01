package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.util.Constants;

/**
 * Full-screen black fade overlay for level transitions.
 *
 * Renders a solid black rectangle scaled to the full viewport, with a "SUBLEVEL N"
 * depth banner visible when the overlay is mostly opaque. Drawn last in the render
 * order so it covers every other layer including the HUD.
 *
 * Zero per-frame allocations: pixelTexture is a 1×1 white pixel created at startup;
 * bannerBuilder is pre-allocated. SpriteBatch capacity = 1 (single full-screen quad).
 */
public class FadeOverlayRenderer implements Disposable {

    private static final float BANNER_FADE_IN_THRESHOLD = 0.60f;

    private final SpriteBatch   batch;
    private final Texture       pixelTexture;
    private final BitmapFont    font;
    private final StringBuilder bannerBuilder = new StringBuilder(24);

    public FadeOverlayRenderer() {
        batch = new SpriteBatch(1);
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(1f, 1f, 1f, 1f);
        pixmap.fill();
        pixelTexture = new Texture(pixmap);
        pixmap.dispose();
        font = new BitmapFont();
        font.getRegion().getTexture().setFilter(
                Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    }

    /**
     * Renders the fade overlay at the given opacity.
     * When fadeAlpha exceeds BANNER_FADE_IN_THRESHOLD, also renders the "SUBLEVEL N" banner.
     *
     * @param camera     projection matrix source — must be the un-shaken world camera
     * @param fadeAlpha  0 = fully transparent (no draw), 1 = fully opaque black screen
     * @param depthNumber  current floor depth, shown in the banner
     */
    public void render(OrthographicCamera camera, float fadeAlpha, int depthNumber) {
        if (fadeAlpha <= 0f) return;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        batch.setColor(0f, 0f, 0f, fadeAlpha);
        batch.draw(pixelTexture, 0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);

        if (fadeAlpha > BANNER_FADE_IN_THRESHOLD) {
            float textAlpha = Math.min(1f, (fadeAlpha - BANNER_FADE_IN_THRESHOLD)
                                           / (1f - BANNER_FADE_IN_THRESHOLD));
            font.getData().setScale(2.2f);
            font.setColor(0.85f, 0.20f, 0.20f, textAlpha);
            bannerBuilder.setLength(0);
            bannerBuilder.append("SUBLEVEL ");
            bannerBuilder.append(depthNumber);
            float textX = Constants.WORLD_WIDTH  / 2f - 110f;
            float textY = Constants.WORLD_HEIGHT / 2f + 20f;
            font.draw(batch, bannerBuilder, textX, textY);
        }

        batch.setColor(1f, 1f, 1f, 1f);
        batch.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    @Override
    public void dispose() {
        batch.dispose();
        pixelTexture.dispose();
        font.dispose();
    }
}
