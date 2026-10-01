package ge.tbegvadze.toon3d.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The registry of {@link NodeSupplySpec} rows — one per {@link RouteNodeType} (balance-overhaul order 2,
 * rule S10). Populated by {@link NodeSupplySpecs#registerAll} through {@link RouteRegistries}; adding a
 * node type's contents is one {@code register(...)} row, never a switch.
 *
 * <p>Pure / headless — no LibGDX imports.
 */
public final class NodeSupplySpecRegistry {

    private final Map<RouteNodeType, NodeSupplySpec> byType = new EnumMap<>(RouteNodeType.class);

    /**
     * Registers one node type's spec.
     *
     * @throws IllegalStateException if that node type already has a spec
     */
    public void register(NodeSupplySpec spec) {
        if (byType.containsKey(spec.type())) {
            throw new IllegalStateException("Node supply spec already registered: " + spec.type());
        }
        byType.put(spec.type(), spec);
    }

    /** The spec for a node type, or {@code null} when none is registered (a coverage violation). */
    public NodeSupplySpec get(RouteNodeType type) {
        return byType.get(type);
    }

    /** The spec for a node type, falling back to the COMBAT row (then the built-in default) when absent. */
    public NodeSupplySpec getOrCombat(RouteNodeType type) {
        NodeSupplySpec spec = type == null ? null : byType.get(type);
        if (spec != null) {
            return spec;
        }
        NodeSupplySpec combat = byType.get(RouteNodeType.COMBAT);
        return combat != null ? combat : NodeSupplySpecs.combat();
    }

    /** Whether a spec exists for this node type. */
    public boolean isRegistered(RouteNodeType type) {
        return byType.containsKey(type);
    }

    /** Every registered spec, in node-type declaration order. */
    public List<NodeSupplySpec> all() {
        return Collections.unmodifiableList(new ArrayList<>(byType.values()));
    }
}
