package ge.tbegvadze.toon3d.tileset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one authoritative, data-driven lookup of every {@link EnvironmentSpriteDefinition}, keyed by its
 * stable id. This is the ART CATALOG — the thing that is currently scattered across
 * {@code WallRenderer}'s texture table and {@code PropRenderer}'s per-char switch. Nothing downstream
 * may hard-code a sprite/category list: the allocator and the renderers ask this registry.
 *
 * <p>Populated once at startup through {@link TilesetRegistries#bootstrap()}. Duplicate ids throw
 * (same discipline as the route registries), so a copy-paste id collision fails loudly at startup
 * rather than silently shadowing art. A fresh instance can be created directly in tests.
 */
public final class EnvironmentSpriteRegistry {

    // Insertion-ordered so allInCategory() and size() are stable / reproducible across runs.
    private final Map<String, EnvironmentSpriteDefinition> definitions = new LinkedHashMap<>();
    private final Map<TileCategory, List<EnvironmentSpriteDefinition>> byCategory =
            new EnumMap<>(TileCategory.class);

    /**
     * Registers a sprite definition. Its {@link EnvironmentSpriteDefinition#id()} is the key.
     *
     * @throws IllegalStateException if that id is already registered (each sprite registers once)
     */
    public void register(EnvironmentSpriteDefinition definition) {
        String id = definition.id();
        if (definitions.containsKey(id)) {
            throw new IllegalStateException("Environment sprite already registered: " + id);
        }
        definitions.put(id, definition);
        byCategory.computeIfAbsent(definition.category(), category -> new ArrayList<>()).add(definition);
    }

    /**
     * @return the definition for a sprite id
     * @throws IllegalArgumentException if the id was never registered (never null for a registered id)
     */
    public EnvironmentSpriteDefinition get(String spriteId) {
        EnvironmentSpriteDefinition definition = definitions.get(spriteId);
        if (definition == null) {
            throw new IllegalArgumentException("Environment sprite not registered: " + spriteId);
        }
        return definition;
    }

    /** Whether a sprite has been registered under the given id. */
    public boolean contains(String spriteId) {
        return definitions.containsKey(spriteId);
    }

    /**
     * The pool the allocator draws variety from: every registered sprite in the given category, in
     * registration order.
     *
     * @return an unmodifiable list (possibly empty), never null
     */
    public List<EnvironmentSpriteDefinition> allInCategory(TileCategory category) {
        List<EnvironmentSpriteDefinition> pool = byCategory.get(category);
        if (pool == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(pool);
    }

    /** Total number of registered sprites across all categories. */
    public int size() {
        return definitions.size();
    }
}
