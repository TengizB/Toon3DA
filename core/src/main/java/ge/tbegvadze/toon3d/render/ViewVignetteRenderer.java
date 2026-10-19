package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.util.ArtDirection;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameMath;

/**
 * Per-mood corner vignette over the 3D view (rule R10 of visual-identity-order-1). Drawn after every
 * world-space pass and immediately before the weapon sprite, so the gun, HUD and overlays are not darkened.
 *
 * <p>One white, Linear-filtered 128x72 texture whose ALPHA channel is the radial ramp
 * ({@link GameMath#vignetteAlpha}) is built once at construction. The mood supplies the colour (its ceiling
 * horizon colour, so corners melt into the haze) and strength through the batch colour. Owns its own
 * SpriteBatch (the same pattern as HitVignetteRenderer; the draw is one quad, so sharing is not worth the
 * coupling). Nothing allocates in {@link #render}.
 */
public final class ViewVignetteRenderer implements Renderable, Disposable {

    private final SpriteBatch batch;
    private final Texture     vignetteTexture;

    private float colourRed   = 0f;
    private float colourGreen = 0f;
    private float colourBlue  = 0f;
    private float alpha       = 0f;

    public ViewVignetteRenderer() {
        batch           = new SpriteBatch(1);
        vignetteTexture = buildVignetteTexture();
    }

    /** Sets colour and strength from the mood. The packed RGBA is unpacked here, once per floor. */
    public void setMood(LightingMood mood) {
        if (mood == null) {
            alpha = 0f;
            return;
        }
        int packed  = mood.horizonCeilingPacked();
        colourRed   = ((packed >>> 24) & 0xFF) / 255f;
        colourGreen = ((packed >>> 16) & 0xFF) / 255f;
        colourBlue  = ((packed >>> 8) & 0xFF) / 255f;
        alpha       = mood.vignetteAlpha();
    }

    @Override
    public void render(OrthographicCamera camera) {
        if (alpha <= 0f) return;
        float margin = ArtDirection.VIGNETTE_SHAKE_MARGIN_WORLD_UNITS;
        batch.setProjectionMatrix(camera.combined);
        batch.setColor(colourRed, colourGreen, colourBlue, alpha);
        batch.begin();
        batch.draw(vignetteTexture,
                   -margin, -margin,
                   Constants.WORLD_WIDTH + 2f * margin, Constants.WORLD_HEIGHT + 2f * margin);
        batch.end();
    }

    private static Texture buildVignetteTexture() {
        int width  = ArtDirection.VIGNETTE_TEXTURE_WIDTH;
        int height = ArtDirection.VIGNETTE_TEXTURE_HEIGHT;
        float margin = ArtDirection.VIGNETTE_SHAKE_MARGIN_WORLD_UNITS;
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                // The quad is oversized by the shake margin, so map each texel centre to its WORLD position
                // on that quad and measure the radius against the 1280x720 VIEW: the view corner is exactly
                // radius 1 (full alpha) and the margin beyond it clamps to full alpha inside vignetteAlpha.
                float worldX = -margin + (column + 0.5f) / width  * (Constants.WORLD_WIDTH  + 2f * margin);
                float worldY = -margin + (row    + 0.5f) / height * (Constants.WORLD_HEIGHT + 2f * margin);
                float radius = GameMath.vignetteNormalisedRadius(worldX, worldY,
                        Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);
                float pixelAlpha = GameMath.vignetteAlpha(radius, ArtDirection.VIGNETTE_CLEAR_RADIUS_FRACTION);
                pixmap.drawPixel(column, row, 0xFFFFFF00 | Math.round(pixelAlpha * 255f));
            }
        }
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        texture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        pixmap.dispose();
        return texture;
    }

    @Override
    public void dispose() {
        batch.dispose();
        vignetteTexture.dispose();
    }
}
