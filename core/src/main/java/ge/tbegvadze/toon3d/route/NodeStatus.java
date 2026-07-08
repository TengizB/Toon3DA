package ge.tbegvadze.toon3d.route;

/**
 * Run-time status of a {@link RouteNode} relative to the player's cursor. Drives which nodes the
 * overlay draws as selectable, dimmed, or highlighted (order-4/order-6).
 */
public enum NodeStatus {
    /** Not yet reachable — a legal pick lies further along the graph. */
    LOCKED,
    /** A legal next pick from the current node. */
    AVAILABLE,
    /** Already travelled through. */
    VISITED,
    /** The node the player currently occupies ("you are here"). */
    CURRENT,
    /** A sibling of a node the player picked instead — this branch is now closed. */
    BYPASSED
}
