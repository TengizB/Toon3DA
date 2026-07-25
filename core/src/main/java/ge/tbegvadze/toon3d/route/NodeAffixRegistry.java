package ge.tbegvadze.toon3d.route;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The data-driven catalog of ELITE {@link NodeAffixDefinition}s (order-9). Mirrors the other route
 * registries: register once at bootstrap, then the rest of the game reads it. Two consumers —
 * {@link RouteMapGenerator} draws the token POOL to roll an affix onto an ELITE node at map-gen, and
 * {@link EliteProfile} looks the definition back up by the node's affix id to apply its modifiers.
 *
 * <p>Adding a new affix is one {@link #register} line in {@link RouteRegistries}; because affixes are
 * only bundles of typed modifiers, no other code changes. Populated once at startup; a shared
 * instance is exposed by {@link RouteRegistries#affixes()}. Pure / headless — no LibGDX imports.
 */
public final class NodeAffixRegistry {

    // Insertion-ordered so the token pool and the derived weights are deterministic run to run.
    private final Map<String, NodeAffixDefinition> definitions = new LinkedHashMap<>();

    /**
     * Registers an affix definition under its {@link NodeAffixDefinition#id()}.
     *
     * @throws IllegalStateException if that id is already registered
     */
    public void register(NodeAffixDefinition definition) {
        String id = Objects.requireNonNull(definition, "definition").id();
        if (definitions.containsKey(id)) {
            throw new IllegalStateException("Affix already registered: " + id);
        }
        definitions.put(id, definition);
    }

    /** The full definition for an affix id, or {@code null} if none is registered. */
    public NodeAffixDefinition definition(String affixId) {
        return affixId == null ? null : definitions.get(affixId);
    }

    /**
     * The ELITE affix token pool the generator rolls from (one {@link NodeAffix} per registered
     * definition, in registration order). Empty until affixes are registered, so no affix ever rolls
     * before the catalog is populated. A fresh list each call — never held across frames.
     */
    public List<NodeAffix> elitePool() {
        List<NodeAffix> pool = new ArrayList<>(definitions.size());
        for (NodeAffixDefinition definition : definitions.values()) {
            pool.add(definition.toToken());
        }
        return pool;
    }

    /**
     * Every registered affix id, in registration order. Read by the balance audit's R-ROUTE-PRICED
     * coverage rule (order 7): an affix with no {@code NodeEconomics} row fails the build.
     */
    public List<String> ids() {
        return new ArrayList<>(definitions.keySet());
    }

    /** How many affixes are registered. */
    public int size() {
        return definitions.size();
    }
}
