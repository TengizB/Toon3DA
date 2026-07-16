package ge.tbegvadze.toon3d.render.tilesetgfx;

import com.badlogic.gdx.graphics.Texture;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Maps a stable sprite id to the {@link EnvironmentTextureGenerator} that draws it (symbol/sprite-reuse
 * order-6) — the render-side companion to the headless {@code EnvironmentSpriteRegistry} art catalog.
 * The catalog says WHAT art exists; this registry says HOW each piece becomes a {@link Texture}. Every
 * registered sprite definition MUST have exactly one matching generator here and vice-versa; the
 * {@link TilesetGfxBootstrap#bootstrap()} startup assertion guards that (no orphan either way).
 *
 * <p>A process-wide singleton (accessed via {@link TilesetGfxBootstrap#generators()}), populated once at
 * startup by a single thread and never mutated afterwards; readers observe the completed registry through
 * the happens-before edge from that one-time population, so no locking is needed as long as no code
 * registers after bootstrap (nothing does). Generators are lazily
 * INVOKED per level by {@link EnvironmentTextureSet#realize} — registering one here does not build a
 * texture; only {@link #generate(String)} does. Duplicate-id registration throws (route-registry
 * discipline), so a copy-paste id collision fails loudly at startup rather than silently shadowing art.
 */
public final class TextureGeneratorRegistry {

    // Insertion-ordered so ids() is stable / reproducible across runs.
    private final Map<String, EnvironmentTextureGenerator> generators = new LinkedHashMap<>();

    /**
     * Registers the procedural generator for a sprite id.
     *
     * @throws IllegalStateException if that id is already registered (each sprite registers once)
     */
    public void register(String spriteId, EnvironmentTextureGenerator generator) {
        if (spriteId == null || generator == null) {
            throw new NullPointerException("spriteId and generator must be non-null");
        }
        if (generators.containsKey(spriteId)) {
            throw new IllegalStateException("Texture generator already registered: " + spriteId);
        }
        generators.put(spriteId, generator);
    }

    /** Whether a generator has been registered for the given sprite id. */
    public boolean has(String spriteId) {
        return generators.containsKey(spriteId);
    }

    /**
     * Runs the registered generator and returns a fresh {@link Texture} for the sprite id. Must be
     * called on the GL thread. The caller owns the returned texture.
     *
     * @throws IllegalArgumentException if no generator is registered for the id
     */
    public Texture generate(String spriteId) {
        EnvironmentTextureGenerator generator = generators.get(spriteId);
        if (generator == null) {
            throw new IllegalArgumentException("No texture generator registered for sprite: " + spriteId);
        }
        return generator.generate();
    }

    /**
     * The registered sprite ids, in registration order, as an unmodifiable snapshot. Called only at
     * startup (the id-set match check) and in tests, never per frame, so the small copy is free.
     */
    public Set<String> ids() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(generators.keySet()));
    }

    /** Number of registered generators. */
    public int size() {
        return generators.size();
    }
}
