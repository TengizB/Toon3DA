package ge.tbegvadze.toon3d.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The NODE EV LEDGER: every priced route subject, keyed by its ledger id (new-game-balancr
 * order 7). One {@link NodeEconomics} row per node type, per ELITE affix, and per MYSTERY
 * outcome — the registry the balance audit reads so the branching map is inside the contract.
 *
 * <p>Same extensibility promise as {@link NodeTypeRegistry}: adding priced route content is one
 * {@code register(...)} line in {@link RouteEconomics}. Adding it WITHOUT a row is a
 * R-ROUTE-PRICED coverage violation and fails {@code ./gradlew test}, exactly as an unpriced
 * enemy or ammo type does (order 5's discipline, applied to the map).
 *
 * <p>Insertion-ordered so the BalanceReport ROUTE ECONOMICS table prints in registration order.
 * Pure / headless — no LibGDX imports.
 */
public final class NodeEconomicsRegistry {

    private final Map<String, NodeEconomics>        byId       = new LinkedHashMap<>();
    private final Map<RouteNodeType, NodeEconomics> byNodeType = new java.util.EnumMap<>(RouteNodeType.class);

    /**
     * Registers one priced row.
     *
     * @throws IllegalStateException if that ledger id (or node type) was already registered
     */
    public void register(NodeEconomics economics) {
        if (byId.containsKey(economics.id())) {
            throw new IllegalStateException("Node economics already registered: " + economics.id());
        }
        byId.put(economics.id(), economics);
        if (economics.nodeType() != null) {
            if (byNodeType.containsKey(economics.nodeType())) {
                throw new IllegalStateException("Node economics already registered for type: "
                        + economics.nodeType());
            }
            byNodeType.put(economics.nodeType(), economics);
        }
    }

    /** The row for a ledger id, or {@code null} when nothing is registered under it. */
    public NodeEconomics get(String id) {
        return byId.get(id);
    }

    /** The row for a node type, or {@code null} when that type is unpriced (a coverage violation). */
    public NodeEconomics forNodeType(RouteNodeType type) {
        return byNodeType.get(type);
    }

    /** Whether a row exists for this ledger id. */
    public boolean isRegistered(String id) {
        return byId.containsKey(id);
    }

    /** Number of registered rows. */
    public int size() {
        return byId.size();
    }

    /** Every registered row in registration order (an unmodifiable view). */
    public List<NodeEconomics> all() {
        return Collections.unmodifiableList(new ArrayList<>(byId.values()));
    }

    /** Every registered row of one kind, in registration order. */
    public List<NodeEconomics> allOfKind(NodeEconomics.Kind kind) {
        List<NodeEconomics> selected = new ArrayList<>();
        for (NodeEconomics economics : byId.values()) {
            if (economics.kind() == kind) {
                selected.add(economics);
            }
        }
        return Collections.unmodifiableList(selected);
    }
}
