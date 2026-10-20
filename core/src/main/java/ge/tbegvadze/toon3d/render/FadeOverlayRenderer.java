package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.util.ArtDirection;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.EffectConstants;

/**
 * Full-screen black fade overlay for level transitions.
 *
 * Renders a solid black rectangle scaled to the full viewport, with a "SUBLEVEL N" depth readout
 * visible when the overlay is mostly opaque (visual-identity-order-6 R11: the elevator is the
 * facility's — a FACILITY_CYAN_DIM label and the number in UI_TEXT_PRIMARY, both HEAVY). Drawn last
 * in the render order so it covers every other layer including the HUD. The death beat and the
 * MERGE hold use the fade WITHOUT the readout: nothing but the death stroke may be on that black.
 *
 * Zero per-frame allocations: pixelTexture is a 1×1 white pixel created at startup; the label is
 * measured once; numberBuilder is pre-allocated. SpriteBatch capacity = 1 (single full-screen quad).
 */
public class FadeOverlayRenderer implements Disposable {

    private final SpriteBatch   batch;
    private final Texture       pixelTexture;
    private final BitmapFont    font;
    private final StringBuilder numberBuilder = new StringBuilder(8);
    /** Advance of the label plus one word space at the readout scale, measured once so the number follows it. */
    private final float         labelWidth;

    public FadeOverlayRenderer() {
        batch = new SpriteBatch(1);
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(1f, 1f, 1f, 1f);
        pixmap.fill();
        pixelTexture = new Texture(pixmap);
        pixmap.dispose();
        font = GameFonts.newFont(FontFace.HEAVY);
        font.getData().setScale(EffectConstants.FADE_DEPTH_READOUT_SCALE);
        // GlyphLayout's width ends at the last glyph, so the word space before the number is added explicitly.
        labelWidth = new GlyphLayout(font, EffectConstants.FADE_DEPTH_READOUT_LABEL).width
                + font.getData().spaceXadvance;   // already scaled by setScale
        font.getData().setScale(1f);
    }

    /**
     * Renders the fade overlay at the given opacity, with the depth readout.
     *
     * @param camera     projection matrix source — must be the un-shaken world camera
     * @param fadeAlpha  0 = fully transparent (no draw), 1 = fully opaque black screen
     * @param depthNumber  current floor depth, shown in the readout
     */
    public void render(OrthographicCamera camera, float fadeAlpha, int depthNumber) {
        render(camera, fadeAlpha, depthNumber, true);
    }

    /**
     * Renders the fade overlay; when {@code showDepthReadout} and fadeAlpha exceeds
     * FADE_DEPTH_READOUT_THRESHOLD, also the "SUBLEVEL N" readout.
     */
    public void render(OrthographicCamera camera, float fadeAlpha, int depthNumber, boolean showDepthReadout) {
        if (fadeAlpha <= 0f) return;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        batch.setColor(ArtDirection.UI_INK_BLACK_RED, ArtDirection.UI_INK_BLACK_GREEN, ArtDirection.UI_INK_BLACK_BLUE,
                       fadeAlpha);
        batch.draw(pixelTexture, 0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);

        float threshold = EffectConstants.FADE_DEPTH_READOUT_THRESHOLD;
        if (showDepthReadout && fadeAlpha > threshold) {
            float textAlpha = Math.min(1f, (fadeAlpha - threshold) / (1f - threshold));
            float textX = Constants.WORLD_WIDTH  / 2f - EffectConstants.FADE_DEPTH_READOUT_OFFSET_X;
            float textY = Constants.WORLD_HEIGHT / 2f + EffectConstants.FADE_DEPTH_READOUT_OFFSET_Y;
            font.getData().setScale(EffectConstants.FADE_DEPTH_READOUT_SCALE);
            font.setColor(ArtDirection.FACILITY_CYAN_DIM_RED, ArtDirection.FACILITY_CYAN_DIM_GREEN,
                          ArtDirection.FACILITY_CYAN_DIM_BLUE, textAlpha);
            font.draw(batch, EffectConstants.FADE_DEPTH_READOUT_LABEL, textX, textY);
            numberBuilder.setLength(0);
            numberBuilder.append(depthNumber);
            font.setColor(ArtDirection.UI_TEXT_PRIMARY_RED, ArtDirection.UI_TEXT_PRIMARY_GREEN,
                          ArtDirection.UI_TEXT_PRIMARY_BLUE, textAlpha);
            font.draw(batch, numberBuilder, textX + labelWidth, textY);
            font.getData().setScale(1f);
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
