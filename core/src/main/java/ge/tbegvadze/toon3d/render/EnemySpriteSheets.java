package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import ge.tbegvadze.toon3d.util.EnemyConstants;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Session-lifetime holder of the conditioned enemy sprite sheets (visual-identity-order-2, R1-R3).
 *
 * <p>The enemy PNGs carry a baked translucent dark halo. Each sheet is loaded into a Pixmap once,
 * hard-alpha-cut (alpha below {@link EnemyConstants#ENEMY_SPRITE_ALPHA_CUT} becomes RGBA 0, otherwise
 * alpha becomes 255 and RGB is kept), uploaded, and kept for the whole app session. It is static,
 * not World-owned, because the World is rebuilt per run while the cache is per process. Main disposes
 * it through {@link #disposeAll()}; {@code EnemyRenderer.dispose()} must NOT dispose these textures.
 *
 * <p>ONE LOG PER SESSION: sheets load lazily per {@link #get}, so the timing log cannot be emitted per
 * call. The first {@code EnemyRenderer} construction brackets its loads with {@link #beginLoadBatch()} /
 * {@link #endLoadBatch()}; the end call logs "EnemySpriteSheets conditioned N sheets in M ms" only if
 * it has not logged yet and at least one sheet was conditioned. Every later floor or run hits the
 * cache, conditions nothing and logs nothing.
 */
public final class EnemySpriteSheets {

    private static final Map<String, Texture> CACHE = new HashMap<>();

    private static boolean logged;
    private static boolean batchOpen;
    private static int     batchSheetCount;
    private static long    batchNanoseconds;

    private EnemySpriteSheets() { }

    /** Starts timing a group of {@link #get} calls (the first EnemyRenderer construction). */
    public static void beginLoadBatch() {
        batchOpen        = true;
        batchSheetCount  = 0;
        batchNanoseconds = 0L;
    }

    /** Ends the group; logs once per session if sheets were conditioned during it. */
    public static void endLoadBatch() {
        if (batchOpen && !logged && batchSheetCount > 0) {
            logged = true;
            Gdx.app.log("EnemySpriteSheets", "EnemySpriteSheets conditioned " + batchSheetCount
                    + " sheets in " + (batchNanoseconds / 1_000_000L) + " ms");
        }
        batchOpen = false;
    }

    /**
     * Returns the cached conditioned texture for {@code path} (Nearest filter), loading and
     * conditioning it on first request. When the file is absent a procedural opaque fallback sheet is
     * built (not conditioned) and cached under the same key.
     */
    public static Texture get(String path, float fallbackRed, float fallbackGreen, float fallbackBlue) {
        Texture cached = CACHE.get(path);
        if (cached != null) {
            return cached;
        }
        long startNanoseconds = System.nanoTime();
        Texture built;
        if (Gdx.files.internal(path).exists()) {
            built = loadAndCondition(path);
        } else {
            built = generateFallbackSheet(fallbackRed, fallbackGreen, fallbackBlue);
        }
        CACHE.put(path, built);
        if (batchOpen) {
            batchSheetCount++;
            batchNanoseconds += System.nanoTime() - startNanoseconds;
        }
        return built;
    }

    /** Disposes every cached sheet. Called once from Main.dispose(), after the world is disposed. */
    public static void disposeAll() {
        for (Texture texture : CACHE.values()) {
            texture.dispose();
        }
        CACHE.clear();
        logged = false;
        batchOpen = false;
    }

    // -------------------------------------------------------------------------

    private static Texture loadAndCondition(String path) {
        Pixmap decoded = new Pixmap(Gdx.files.internal(path));
        Pixmap rgba = decoded;
        if (decoded.getFormat() != Pixmap.Format.RGBA8888) {
            rgba = new Pixmap(decoded.getWidth(), decoded.getHeight(), Pixmap.Format.RGBA8888);
            rgba.setBlending(Pixmap.Blending.None);
            rgba.drawPixmap(decoded, 0, 0);
            decoded.dispose();
        }
        applyAlphaCut(rgba);
        Texture texture = new Texture(rgba);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        rgba.dispose();
        return texture;
    }

    /**
     * R1: one bulk pass over the RGBA8888 bytes. Alpha is the 4th byte of each texel regardless of the
     * buffer's byte order (RGBA8888 is R,G,B,A in memory). A texel is cut when alpha / 255 is below the
     * threshold; kept texels are made fully opaque.
     */
    private static void applyAlphaCut(Pixmap rgba) {
        ByteBuffer pixels = rgba.getPixels();
        int byteCount = rgba.getWidth() * rgba.getHeight() * 4;
        byte[] data = new byte[byteCount];
        pixels.position(0);
        pixels.get(data, 0, byteCount);
        float cut = EnemyConstants.ENEMY_SPRITE_ALPHA_CUT;
        for (int texelStart = 0; texelStart < byteCount; texelStart += 4) {
            int alpha = data[texelStart + 3] & 0xFF;
            if (alpha / 255f < cut) {
                data[texelStart]     = 0;
                data[texelStart + 1] = 0;
                data[texelStart + 2] = 0;
                data[texelStart + 3] = 0;
            } else {
                data[texelStart + 3] = (byte) 0xFF;
            }
        }
        pixels.position(0);
        pixels.put(data, 0, byteCount);
        pixels.position(0);
    }

    private static Texture generateFallbackSheet(float red, float green, float blue) {
        // 2x2 grid fallback: 128x128 total, each quadrant 64x64 with simple eye pattern
        Pixmap pixmap = new Pixmap(128, 128, Pixmap.Format.RGBA8888);
        pixmap.setColor(red, green, blue, 1f);
        pixmap.fill();
        pixmap.setColor(1f, 1f, 0f, 1f);
        for (int quadrantRow = 0; quadrantRow < 2; quadrantRow++) {
            for (int quadrantColumn = 0; quadrantColumn < 2; quadrantColumn++) {
                int offsetX = quadrantColumn * 64;
                int offsetY = quadrantRow    * 64;
                pixmap.fillRectangle(offsetX + 12, offsetY + 20, 12, 12);
                pixmap.fillRectangle(offsetX + 40, offsetY + 20, 12, 12);
            }
        }
        pixmap.setColor(0f, 0f, 0f, 1f);
        for (int quadrantRow = 0; quadrantRow < 2; quadrantRow++) {
            for (int quadrantColumn = 0; quadrantColumn < 2; quadrantColumn++) {
                int offsetX = quadrantColumn * 64;
                int offsetY = quadrantRow    * 64;
                pixmap.fillRectangle(offsetX + 15, offsetY + 23, 6, 6);
                pixmap.fillRectangle(offsetX + 43, offsetY + 23, 6, 6);
            }
        }
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
    }
}
