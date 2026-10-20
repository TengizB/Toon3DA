package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import ge.tbegvadze.toon3d.util.ArtDirection;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.RenderConstants;

/**
 * Shared contact-shadow helper (visual-identity-order-2 R5/R6). Each billboard renderer builds and
 * OWNS (and disposes) its own tiny texture from {@link #buildTexture()}, then calls
 * {@link #draw} for every upright billboard BEFORE drawing the sprite itself.
 */
public final class ContactShadow {

    private ContactShadow() { }

    /** 64x32 white ellipse whose alpha is GameMath.contactShadowAlpha(r); Linear filter. Caller disposes. */
    public static Texture buildTexture() {
        int width  = RenderConstants.CONTACT_SHADOW_TEXTURE_WIDTH;
        int height = RenderConstants.CONTACT_SHADOW_TEXTURE_HEIGHT;
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        for (int texelRow = 0; texelRow < height; texelRow++) {
            float normalisedY = (texelRow + 0.5f) / height * 2f - 1f;
            for (int texelColumn = 0; texelColumn < width; texelColumn++) {
                float normalisedX = (texelColumn + 0.5f) / width * 2f - 1f;
                float radius = (float) Math.sqrt(normalisedX * normalisedX + normalisedY * normalisedY);
                pixmap.setColor(1f, 1f, 1f, GameMath.contactShadowAlpha(radius));
                pixmap.drawPixel(texelColumn, texelRow);
            }
        }
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return texture;
    }

    /** R6 alpha: CONTACT_SHADOW_ALPHA x (1 - CONTACT_SHADOW_FOG_FADE x fog), fog from the mood (0 if none). */
    public static float alphaAtDepth(LightingMood mood, float depthTiles) {
        float fog = (mood == null) ? 0f
                : GameMath.atmosphereFogFactor(depthTiles, mood.fogStartTiles(), mood.fogEndTiles());
        return RenderConstants.CONTACT_SHADOW_ALPHA * (1f - RenderConstants.CONTACT_SHADOW_FOG_FADE * fog);
    }

    /**
     * Draws the shadow ellipse as one world-unit-wide quad per column, skipping every column the wall
     * z-buffer hides ({@code depth >= zBuffer}), exactly like the sprite's own column loop. The batch
     * must be begun; its colour is set here (WORLD_SHADOW, not graded) and left set - the caller sets
     * its own sprite colour afterwards. No allocation.
     *
     * @param centerWorldX   billboard centre in WORLD units
     * @param floorLineY     billboard floor line (draw bottom BEFORE hover/lunge), world units
     * @param shadowWidth    already scaled by the CONTACT_SHADOW_WIDTH_FRACTION_* for the billboard kind
     * @param alpha          final alpha (use {@link #alphaAtDepth}, times any hover scale)
     */
    public static void draw(SpriteBatch batch, Texture shadowTexture, WallRenderer wallRenderer,
                            int projectionColumnCount, float projectionColumnWidth,
                            float centerWorldX, float floorLineY, float shadowWidth,
                            float depth, float alpha) {
        if (shadowWidth <= 0f || alpha <= 0f) return;
        float shadowHeight = shadowWidth * RenderConstants.CONTACT_SHADOW_HEIGHT_RATIO;
        float leftWorldX   = centerWorldX - shadowWidth * 0.5f;
        float bottomY      = floorLineY - shadowHeight * 0.5f;
        int firstColumn = Math.max(0, (int) Math.floor(leftWorldX / projectionColumnWidth));
        int lastColumn  = Math.min(projectionColumnCount - 1,
                (int) Math.floor((leftWorldX + shadowWidth) / projectionColumnWidth));
        batch.setColor(ArtDirection.WORLD_SHADOW_RED, ArtDirection.WORLD_SHADOW_GREEN,
                ArtDirection.WORLD_SHADOW_BLUE, alpha);
        for (int screenColumn = firstColumn; screenColumn <= lastColumn; screenColumn++) {
            if (depth >= wallRenderer.getZBufferUnchecked(screenColumn)) continue;
            float columnLeft = screenColumn * projectionColumnWidth;
            float textureLeft  = Math.max(0f, Math.min(1f, (columnLeft - leftWorldX) / shadowWidth));
            float textureRight = Math.max(0f, Math.min(1f,
                    (columnLeft + projectionColumnWidth - leftWorldX) / shadowWidth));
            batch.draw(shadowTexture, columnLeft, bottomY, projectionColumnWidth, shadowHeight,
                    textureLeft, 1f, textureRight, 0f);
        }
    }
}
