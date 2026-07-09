package ge.tbegvadze.toon3d.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The whole run's route graph plus the player's cursor. A forward-only DAG stored as LAYERS
 * (index = hop/floor), which keeps the graph forward-only and the overlay layout simple.
 *
 * <p>Pure state — NO rendering, NO LibGDX imports. Generated once per run by order-2's
 * {@code RouteMapGenerator} from the run's master seed, so a given seed always yields an identical
 * map (required for permadeath fairness, seed-sharing, and run-stats replay). Owned by {@code World}
 * (order-3).
 */
public final class RouteMap {

    private final List<List<RouteNode>> layers;
    private final long runSeed;
    private final List<RouteRegion> regions = new ArrayList<>();
    private RouteNode currentNode;

    /**
     * @param layers  outer list = hops/floors; each inner list = that layer's nodes. Defensively
     *                copied so later mutation of the caller's lists cannot corrupt the map.
     * @param runSeed the run's master seed this map was generated from
     */
    public RouteMap(List<List<RouteNode>> layers, long runSeed) {
        this.layers  = new ArrayList<>(layers.size());
        for (List<RouteNode> layer : layers) {
            this.layers.add(new ArrayList<>(layer));
        }
        this.runSeed = runSeed;
    }

    /** The run's master seed. Exposes reproducibility — same seed produces an identical map. */
    public long getRunSeed() {
        return runSeed;
    }

    /**
     * The layers, as an unmodifiable view (inner lists are also unmodifiable). The RouteNodes
     * themselves stay mutable — status changes flow through this map's cursor methods.
     */
    public List<List<RouteNode>> getLayers() {
        List<List<RouteNode>> view = new ArrayList<>(layers.size());
        for (List<RouteNode> layer : layers) {
            view.add(Collections.unmodifiableList(layer));
        }
        return Collections.unmodifiableList(view);
    }

    /** The number of layers (hops) currently in the map. */
    public int getLayerCount() {
        return layers.size();
    }

    /**
     * Appends freshly generated layers to the end of the map (endless-mode lazy extension, order-2).
     * The caller (the generator) must have already wired the incoming edges from the current final
     * layer's nodes into the first appended layer, since those existing node instances are the real,
     * mutable ones returned by {@link #getLayers()}. The lists are defensively copied.
     *
     * @param newLayers outer list = new hops appended in order; each inner list = that layer's nodes
     */
    public void appendLayers(List<List<RouteNode>> newLayers) {
        for (List<RouteNode> layer : newLayers) {
            layers.add(new ArrayList<>(layer));
        }
    }

    /** The act bands. Populated by the generator (order-2); empty until then. */
    public List<RouteRegion> getRegions() {
        return Collections.unmodifiableList(regions);
    }

    /** Adds a region band (generator-side, order-2). */
    public void addRegion(RouteRegion region) {
        regions.add(region);
    }

    /** The node the player currently occupies. */
    public RouteNode getCurrent() {
        return currentNode;
    }

    /**
     * Establishes the starting cursor position: marks {@code startNode} CURRENT and promotes each of
     * its outgoing edges from LOCKED to AVAILABLE. Called once by the generator after the graph is
     * wired. This is cursor bookkeeping, not graph generation.
     */
    public void initializeCursor(RouteNode startNode) {
        this.currentNode = startNode;
        startNode.status = NodeStatus.CURRENT;
        for (RouteNode next : startNode.outgoing) {
            if (next.status == NodeStatus.LOCKED) {
                next.status = NodeStatus.AVAILABLE;
            }
        }
    }

    /**
     * The legal next picks from the current node — its outgoing edges filtered to AVAILABLE.
     *
     * @return an unmodifiable list (empty if there is no current node or no legal move)
     */
    public List<RouteNode> getSelectableNext() {
        List<RouteNode> selectable = new ArrayList<>();
        if (currentNode != null) {
            for (RouteNode next : currentNode.outgoing) {
                if (next.status == NodeStatus.AVAILABLE) {
                    selectable.add(next);
                }
            }
        }
        return Collections.unmodifiableList(selectable);
    }

    /**
     * Advances the cursor to {@code next} and restatuses the affected nodes:
     * <ul>
     *   <li>the old current node becomes VISITED;</li>
     *   <li>its other selectable siblings (the branches not taken) become BYPASSED;</li>
     *   <li>{@code next} becomes CURRENT;</li>
     *   <li>{@code next}'s LOCKED outgoing edges become AVAILABLE.</li>
     * </ul>
     *
     * @throws IllegalStateException    if there is no current node
     * @throws IllegalArgumentException if {@code next} is not an AVAILABLE outgoing edge of current
     */
    public void commitTo(RouteNode next) {
        if (currentNode == null) {
            throw new IllegalStateException("commitTo called before the cursor was initialized");
        }
        if (!currentNode.outgoing.contains(next) || next.status != NodeStatus.AVAILABLE) {
            throw new IllegalArgumentException("Node is not an available next pick from the current node");
        }
        currentNode.status = NodeStatus.VISITED;
        for (RouteNode sibling : currentNode.outgoing) {
            if (sibling != next && sibling.status == NodeStatus.AVAILABLE) {
                sibling.status = NodeStatus.BYPASSED;
            }
        }
        next.status = NodeStatus.CURRENT;
        for (RouteNode following : next.outgoing) {
            if (following.status == NodeStatus.LOCKED) {
                following.status = NodeStatus.AVAILABLE;
            }
        }
        currentNode = next;
    }

    /** Whether every legal next pick is a forced BOSS convergence (i.e. the boss is unavoidable next). */
    public boolean isBossNext() {
        List<RouteNode> selectable = getSelectableNext();
        if (selectable.isEmpty()) {
            return false;
        }
        for (RouteNode next : selectable) {
            if (next.type != RouteNodeType.BOSS) {
                return false;
            }
        }
        return true;
    }

    /** Reveals a single node (lifts its fog so its type is shown instead of '?'). */
    public void reveal(RouteNode node) {
        node.revealed = true;
    }

    /**
     * Reveals every node whose depth falls within the region at {@code regionIndex}.
     *
     * @throws IndexOutOfBoundsException if no such region exists
     */
    public void revealRegion(int regionIndex) {
        RouteRegion region = regions.get(regionIndex);
        for (List<RouteNode> layer : layers) {
            for (RouteNode node : layer) {
                if (region.containsDepth(node.depth)) {
                    node.revealed = true;
                }
            }
        }
    }
}
