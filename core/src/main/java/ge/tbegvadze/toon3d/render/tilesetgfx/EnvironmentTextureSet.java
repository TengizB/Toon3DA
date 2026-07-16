package ge.tbegvadze.toon3d.render.tilesetgfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.render.PropRenderer;
import ge.tbegvadze.toon3d.tileset.LevelPalette;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The per-level, disposable bundle of environment textures (symbol/sprite-reuse order-6). This is the
 * literal payoff of "no need to load all walls into every level": a level realizes textures ONLY for the
 * handful of sprite ids its {@link LevelPalette} references, holds them for the level's lifetime, and
 * frees them on teardown. Small GPU footprint per level; unbounded art library overall.
 *
 * <p>Built at level load via {@link #realize} (on the GL thread — it creates {@link Texture}s), owned by
 * {@code World}, and set on {@code WallRenderer}/{@code PropRenderer} per level. order-6 wires this supply
 * and its dispose path; order-7 flips the renderers to actually DRAW from it. Because it is fully built
 * before any frame renders, {@code WallRenderer}'s worker threads can read {@link #textureFor} results
 * without synchronisation (happens-before from level construction).
 *
 * <p>MEMORY RULE: exactly the palette's distinct sprites are resident, nothing else — de-duplicated so two
 * symbols bound to the same sprite id share one {@link Texture}. {@link #dispose()} frees every realized
 * texture and MUST be called on level teardown.
 */
public final class EnvironmentTextureSet implements Disposable {

    private final Map<String, Texture> texturesBySpriteId;
    private final Map<String, Integer> widthBySpriteId;
    private final Map<String, Integer> heightBySpriteId;

    private EnvironmentTextureSet(Map<String, Texture> texturesBySpriteId,
                                  Map<String, Integer> widthBySpriteId,
                                  Map<String, Integer> heightBySpriteId) {
        this.texturesBySpriteId = texturesBySpriteId;
        this.widthBySpriteId    = widthBySpriteId;
        this.heightBySpriteId   = heightBySpriteId;
    }

    /**
     * Realizes the textures for exactly the sprite ids the palette references — one texture per DISTINCT
     * id (dedupe: symbols sharing a sprite id share one texture). Runs at level load on the GL thread.
     *
     * @param palette this level's symbol → sprite-id binding (never null; the level always carries one)
     * @param generators the sprite-id → procedural-generator registry ({@code TilesetGfxBootstrap.generators()})
     * @return a new set owning every realized texture; caller disposes it on level teardown
     */
    public static EnvironmentTextureSet realize(LevelPalette palette, TextureGeneratorRegistry generators) {
        Set<String> spriteIds = palette.distinctSpriteIds();
        Map<String, Texture> textures = new HashMap<>();
        Map<String, Integer> widths   = new HashMap<>();
        Map<String, Integer> heights  = new HashMap<>();
        for (String spriteId : spriteIds) {
            Texture texture = generators.generate(spriteId);
            textures.put(spriteId, texture);
            widths.put(spriteId, texture.getWidth());
            heights.put(spriteId, texture.getHeight());
        }
        // Dev-time confirmation that a level holds only its subset, not the whole library.
        if (Gdx.app != null) {
            Gdx.app.debug("EnvironmentTextureSet",
                    "realized " + textures.size() + " textures for this level's palette");
        }
        return new EnvironmentTextureSet(textures, widths, heights);
    }

    /**
     * The realized texture for a sprite id.
     *
     * @throws IllegalArgumentException if this level's palette never referenced that sprite (so it was not
     *         realized) — a caller asking for art outside the level's subset is a bug, not silent null.
     */
    public Texture textureFor(String spriteId) {
        Texture texture = texturesBySpriteId.get(spriteId);
        if (texture == null) {
            throw new IllegalArgumentException("Sprite not realized for this level: " + spriteId);
        }
        return texture;
    }

    /** Intrinsic pixel width of the realized texture for a sprite id. */
    public int widthFor(String spriteId) {
        Integer width = widthBySpriteId.get(spriteId);
        if (width == null) {
            throw new IllegalArgumentException("Sprite not realized for this level: " + spriteId);
        }
        return width;
    }

    /** Intrinsic pixel height of the realized texture for a sprite id. */
    public int heightFor(String spriteId) {
        Integer height = heightBySpriteId.get(spriteId);
        if (height == null) {
            throw new IllegalArgumentException("Sprite not realized for this level: " + spriteId);
        }
        return height;
    }

    /** Whether this level realized a texture for the given sprite id. */
    public boolean contains(String spriteId) {
        return texturesBySpriteId.containsKey(spriteId);
    }

    /** Number of realized textures (the level's palette subset size). */
    public int size() {
        return texturesBySpriteId.size();
    }

    /**
     * Frees every realized texture on level teardown. Routes disposal through
     * {@link PropRenderer#disposeGeneratedTexture} so prop/decal textures also drop their entry in
     * PropRenderer's static opacity-mask cache (harmless no-op for wall/column textures) — otherwise that
     * cache would leak one mask per prop sprite per level transition. Idempotent-safe against being the
     * last owner: World nulls its reference right after.
     */
    @Override
    public void dispose() {
        for (Texture texture : texturesBySpriteId.values()) {
            PropRenderer.disposeGeneratedTexture(texture);
        }
        texturesBySpriteId.clear();
        widthBySpriteId.clear();
        heightBySpriteId.clear();
    }
}
