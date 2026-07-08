package ge.tbegvadze.toon3d.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The data-driven lookup of every {@link NodeTypeDefinition}, keyed by {@link RouteNodeType}.
 *
 * <p>This is the load-bearing extensibility promise for NODES: adding a node type means registering
 * one {@link NodeTypeDefinition} here (via {@link RouteRegistries#bootstrap()}). Nothing downstream
 * may hard-code the node list — the pool builder, overlay, and level pipeline all ask this registry.
 *
 * <p>Populated once at startup. A single shared instance is exposed by
 * {@link RouteRegistries#nodeTypes()}; a fresh instance can be created directly in tests.
 */
public final class NodeTypeRegistry {

    private final Map<RouteNodeType, NodeTypeDefinition> definitions = new EnumMap<>(RouteNodeType.class);

    /**
     * Registers a definition. Its {@link NodeTypeDefinition#type()} is the key.
     *
     * @throws IllegalStateException if that type is already registered (each type registers once)
     */
    public void register(NodeTypeDefinition definition) {
        RouteNodeType type = definition.type();
        if (definitions.containsKey(type)) {
            throw new IllegalStateException("Node type already registered: " + type);
        }
        definitions.put(type, definition);
    }

    /**
     * @return the definition for a type
     * @throws IllegalArgumentException if the type was never registered
     */
    public NodeTypeDefinition get(RouteNodeType type) {
        NodeTypeDefinition definition = definitions.get(type);
        if (definition == null) {
            throw new IllegalArgumentException("Node type not registered: " + type);
        }
        return definition;
    }

    /** Whether a definition has been registered for the given type. */
    public boolean isRegistered(RouteNodeType type) {
        return definitions.containsKey(type);
    }

    /** Number of registered definitions. */
    public int size() {
        return definitions.size();
    }

    /**
     * The subset eligible to be rolled into the random pool — every registered, non-{@code forced}
     * type. {@code forced} types (BOSS, REGION_GATE) are excluded because the generator injects them
     * deliberately rather than rolling them.
     *
     * @return an unmodifiable list, stable in {@link RouteNodeType} declaration order
     */
    public List<NodeTypeDefinition> allWeightedForPool() {
        List<NodeTypeDefinition> pool = new ArrayList<>();
        for (RouteNodeType type : RouteNodeType.values()) {
            NodeTypeDefinition definition = definitions.get(type);
            if (definition != null && !definition.forced()) {
                pool.add(definition);
            }
        }
        return Collections.unmodifiableList(pool);
    }
}
