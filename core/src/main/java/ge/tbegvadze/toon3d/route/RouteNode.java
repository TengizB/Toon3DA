package ge.tbegvadze.toon3d.route;

import java.util.ArrayList;
import java.util.List;

/**
 * One node instance on a generated {@link RouteMap}. Mutable run-state: the generator (order-2)
 * fills in the layout fields, and the cursor logic in {@link RouteMap} mutates {@link #status} and
 * {@link #revealed} as the player travels.
 *
 * <p>Pure state — no rendering, no LibGDX imports. Colours/icons are resolved in the render layer
 * from the node's {@link NodeTypeDefinition}.
 */
public final class RouteNode {

    /** The kind of node — its metadata lives in the {@link NodeTypeRegistry}. */
    public RouteNodeType type;

    /** The floor number this node represents (1-based, matches {@code World.currentDepth}). */
    public int depth;

    /** Column / lane within the region band — a layout hint for the overlay. */
    public int laneIndex;

    /** Resolved display label (may be templated with depth/region at generation time). */
    public String resolvedLabel;

    /** Resolved one-line hint (may be templated). */
    public String resolvedHint;

    /**
     * The generator chosen for this node. Rolled from the standard pool at MAP-GEN time for COMBAT
     * nodes (so the map is fully deterministic); fixed by the level profile for special nodes.
     * Null until resolved.
     */
    public GeneratorId chosenGeneratorId;

    /** Optional modifier (e.g. an ELITE affix — order-9). Null when the node has none. */
    public NodeAffix affix;

    /** Cursor-relative status. Defaults to LOCKED until the generator/cursor promotes it. */
    public NodeStatus status = NodeStatus.LOCKED;

    /** The nodes reachable from this one (forward-only DAG edges). */
    public final List<RouteNode> outgoing = new ArrayList<>();

    /** Fog: is the node's type shown, or a '?' placeholder (order-6)? */
    public boolean revealed;

    /**
     * Derived per-node seed. Every per-node roll (generator pick, affix, mystery outcome) draws from
     * this so nothing depends on visit ORDER. Set from
     * {@code GameMath.routeNodeSeed(runSeed, depth, laneIndex)} at generation time.
     */
    public long nodeSeed;

    public RouteNode(RouteNodeType type, int depth, int laneIndex) {
        this.type      = type;
        this.depth     = depth;
        this.laneIndex = laneIndex;
    }
}
