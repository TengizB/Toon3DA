package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * Per-screen-column record of the nearest billboard slices drawn this frame, shared by every
 * billboard renderer (props, ground items, shop machines, enemies).
 *
 * <p>Billboards are drawn by several renderers in a fixed order (props → shop machines → enemies),
 * not in one global far→near pass. A sprite drawn LATER but standing FARTHER away than a sprite
 * already on screen would therefore paint over it. The old fix carved a rectangular gap out of the
 * later sprite around the nearer sprite's opaque vertical extent — which left a hole wherever the
 * nearer sprite's texture was transparent inside that extent (the gaps of a weapon pickup showed
 * the floor instead of the enemy behind it).
 *
 * <p>The correct composite is cheap with 1-pixel-wide column draws: the later, farther sprite draws
 * its WHOLE column, then {@link #redrawInFront} re-draws the recorded slices of the nearer sprites
 * on top of it. The nearer sprite's opaque pixels cover the farther one again and its transparent
 * pixels let it show through — exactly what a single depth-sorted pass would have produced.
 *
 * <p>Up to {@link #LAYER_COUNT} nearest slices are kept per column (nearest in layer 0), so two
 * overlapping props in front of an enemy are both restored. Only opaque, normal-alpha-blended
 * sprite slices are recorded; additive glows and flat floor decals are not.
 */
public final class SpriteOccluderBuffer {

    /** Nearest slices kept per screen column. */
    public static final int LAYER_COUNT = 2;

    private final float[][]   depths;
    private final Texture[][] textures;
    private final int[][]     textureSourceXs;
    private final int[][]     textureSourceYs;
    private final int[][]     textureSourceHeights;
    private final float[][]   drawBottoms;
    private final float[][]   drawTops;
    private final float[][]   packedColors;

    public SpriteOccluderBuffer(int maximumColumnCount) {
        depths               = new float[LAYER_COUNT][maximumColumnCount];
        textures             = new Texture[LAYER_COUNT][maximumColumnCount];
        textureSourceXs      = new int[LAYER_COUNT][maximumColumnCount];
        textureSourceYs      = new int[LAYER_COUNT][maximumColumnCount];
        textureSourceHeights = new int[LAYER_COUNT][maximumColumnCount];
        drawBottoms          = new float[LAYER_COUNT][maximumColumnCount];
        drawTops             = new float[LAYER_COUNT][maximumColumnCount];
        packedColors         = new float[LAYER_COUNT][maximumColumnCount];
        clear();
    }

    /** Forgets every recorded slice. Called once per frame before the first billboard is drawn. */
    public void clear() {
        for (int layer = 0; layer < LAYER_COUNT; layer++) {
            java.util.Arrays.fill(depths[layer], Float.MAX_VALUE);
            java.util.Arrays.fill(textures[layer], null);
        }
    }

    /** Depth of the nearest recorded slice in this column, or {@code Float.MAX_VALUE} when none. */
    public float getNearestDepth(int screenColumn) {
        return depths[0][screenColumn];
    }

    /**
     * Records one drawn sprite column slice, keeping the column's {@link #LAYER_COUNT} nearest
     * slices sorted nearest-first. Order of arrival does not matter.
     */
    public void record(int screenColumn, float depth, Texture texture,
                       int textureSourceX, int textureSourceY, int textureSourceHeight,
                       float drawBottom, float drawTop, float packedColor) {
        int insertLayer = -1;
        for (int layer = 0; layer < LAYER_COUNT; layer++) {
            if (depth < depths[layer][screenColumn]) {
                insertLayer = layer;
                break;
            }
        }
        if (insertLayer < 0) return; // farther than every kept slice — never needs restoring

        for (int layer = LAYER_COUNT - 1; layer > insertLayer; layer--) {
            depths[layer][screenColumn]               = depths[layer - 1][screenColumn];
            textures[layer][screenColumn]             = textures[layer - 1][screenColumn];
            textureSourceXs[layer][screenColumn]      = textureSourceXs[layer - 1][screenColumn];
            textureSourceYs[layer][screenColumn]      = textureSourceYs[layer - 1][screenColumn];
            textureSourceHeights[layer][screenColumn] = textureSourceHeights[layer - 1][screenColumn];
            drawBottoms[layer][screenColumn]          = drawBottoms[layer - 1][screenColumn];
            drawTops[layer][screenColumn]             = drawTops[layer - 1][screenColumn];
            packedColors[layer][screenColumn]         = packedColors[layer - 1][screenColumn];
        }
        depths[insertLayer][screenColumn]               = depth;
        textures[insertLayer][screenColumn]             = texture;
        textureSourceXs[insertLayer][screenColumn]      = textureSourceX;
        textureSourceYs[insertLayer][screenColumn]      = textureSourceY;
        textureSourceHeights[insertLayer][screenColumn] = textureSourceHeight;
        drawBottoms[insertLayer][screenColumn]          = drawBottom;
        drawTops[insertLayer][screenColumn]             = drawTop;
        packedColors[insertLayer][screenColumn]         = packedColor;
    }

    /**
     * Re-draws, farthest first, every recorded slice in this column that is nearer than
     * {@code depth}, on top of whatever the caller just drew there. The batch must be drawing with
     * normal alpha blending; its colour is restored afterwards.
     */
    public void redrawInFront(SpriteBatch batch, int screenColumn, float depth, float columnWidth) {
        if (depth <= depths[0][screenColumn]) return; // nothing nearer in this column
        float callerPackedColor = batch.getPackedColor();
        for (int layer = LAYER_COUNT - 1; layer >= 0; layer--) {
            Texture texture = textures[layer][screenColumn];
            if (texture == null || depths[layer][screenColumn] >= depth) continue;
            batch.setPackedColor(packedColors[layer][screenColumn]);
            float drawBottom = drawBottoms[layer][screenColumn];
            batch.draw(texture,
                       screenColumn * columnWidth, drawBottom,
                       columnWidth, drawTops[layer][screenColumn] - drawBottom,
                       textureSourceXs[layer][screenColumn], textureSourceYs[layer][screenColumn],
                       1, textureSourceHeights[layer][screenColumn],
                       false, false);
        }
        batch.setPackedColor(callerPackedColor);
    }
}
