package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.utils.Disposable;

import java.nio.IntBuffer;

import static ge.tbegvadze.toon3d.util.RenderConstants.SURFACE_MATERIAL_MIP_LEVELS;
import static ge.tbegvadze.toon3d.util.RenderConstants.SURFACE_MATERIAL_SIZE;

/**
 * One floor or ceiling material realised on the GPU for the span pipeline (render-performance-full-hd R4):
 * {@code SURFACE_MATERIAL_MIP_LEVELS} Textures, one per level of {@link SurfaceMaterialRegistry}'s existing
 * box-filtered chain (64, 32, 16, 8), each wrap Repeat/Repeat and filter Nearest/Nearest. No GL mipmaps: the
 * span renderer keeps the per-row level choice, so each row binds exactly the level it would have read.
 *
 * <p>Orientation is the CPU caster's: registry texel (column, row) is pixmap pixel (column, row), and pixmap
 * row 0 is the first row uploaded, i.e. texture V = 0 - so UV (tileX, tileY) samples texel
 * (frac(tileX) * size, frac(tileY) * size), exactly the index the CPU path computed.
 *
 * <p>Built once per floor on the GL thread. The Textures are MANAGED (a {@link PixmapTextureData} with
 * managed = true over a Pixmap this class keeps), so LibGDX re-uploads them after an Android GL context loss;
 * the Pixmaps (about 22 KB per surface) are freed with the Textures in {@link #dispose()}.
 */
public final class SurfaceMaterialTextures implements Disposable {

    private final Texture[] levelTextures = new Texture[SURFACE_MATERIAL_MIP_LEVELS];
    private final Pixmap[]  levelPixmaps  = new Pixmap[SURFACE_MATERIAL_MIP_LEVELS];

    /**
     * @param channelScales per-channel bake scales (red, green, blue), each >= 1, from
     *        {@code GameMath.materialChannelBakeScale}; every texel channel is multiplied by its scale before upload,
     *        so a vertex colour of factor / scale reproduces a factor above 1 without an additive pass. The caller
     *        only passes a scale that keeps every texel within 255 ({@link #maximumChannel}).
     */
    public SurfaceMaterialTextures(String materialId, float[] channelScales) {
        for (int mipLevel = 0; mipLevel < SURFACE_MATERIAL_MIP_LEVELS; mipLevel++) {
            int[] texelsPacked = SurfaceMaterials.registry().getPixels(materialId, mipLevel);
            int edge = SURFACE_MATERIAL_SIZE >> mipLevel;
            Pixmap pixmap = new Pixmap(edge, edge, Pixmap.Format.RGBA8888);
            // RGBA8888 bytes; a big-endian int view writes 0xRRGGBBAA as R, G, B, A (the old backdrop upload).
            IntBuffer pixelBuffer = pixmap.getPixels().asIntBuffer();
            pixelBuffer.position(0);
            for (int texelIndex = 0; texelIndex < edge * edge; texelIndex++) {
                pixelBuffer.put(bake(texelsPacked[texelIndex], channelScales));
            }
            pixelBuffer.position(0);
            Texture texture = new Texture(new PixmapTextureData(pixmap, null, false, false, true));
            texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            levelPixmaps[mipLevel]  = pixmap;
            levelTextures[mipLevel] = texture;
        }
    }

    private static int bake(int packed, float[] channelScales) {
        int red   = Math.min(255, Math.round(((packed >>> 24) & 0xFF) * channelScales[0]));
        int green = Math.min(255, Math.round(((packed >>> 16) & 0xFF) * channelScales[1]));
        int blue  = Math.min(255, Math.round(((packed >>> 8)  & 0xFF) * channelScales[2]));
        return (red << 24) | (green << 16) | (blue << 8) | (packed & 0xFF);
    }

    /**
     * The brightest value of one channel (0 = red, 1 = green, 2 = blue) over the material's level-0 texels (the
     * box-filtered levels can only be darker or equal), for the bake-scale check.
     */
    public static int maximumChannel(String materialId, int channel) {
        int shift = 24 - 8 * channel;
        int maximum = 0;
        for (int packed : SurfaceMaterials.registry().getPixels(materialId, 0)) {
            maximum = Math.max(maximum, (packed >>> shift) & 0xFF);
        }
        return maximum;
    }

    /** The Texture of a mip level (0 = 64x64 ... SURFACE_MATERIAL_MIP_LEVELS - 1). */
    public Texture getLevel(int mipLevel) {
        return levelTextures[mipLevel];
    }

    @Override
    public void dispose() {
        for (int mipLevel = 0; mipLevel < SURFACE_MATERIAL_MIP_LEVELS; mipLevel++) {
            if (levelTextures[mipLevel] != null) levelTextures[mipLevel].dispose();
            if (levelPixmaps[mipLevel] != null) levelPixmaps[mipLevel].dispose();
        }
    }
}
