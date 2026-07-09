package ge.tbegvadze.toon3d.render;

import ge.tbegvadze.toon3d.route.RouteNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The route-selection overlay — order-3 STUB. It carries the presented candidate nodes and a commit
 * callback so the world flow (ROUTE_SELECT phase -&gt; node commit -&gt; FADING_OUT) is wired and
 * verifiable before the real drawing + touch land in order-4/order-6.
 *
 * <p>No LibGDX drawing here yet: {@link #present(List)} stores the candidates and marks the overlay
 * active; {@code World} drives selection (auto-committing the first candidate in order-3) via
 * {@link #commit(RouteNode)}, which fires {@link NodeCommitListener}. Order-4 replaces the stub body
 * with an actual renderer + touch handling while keeping this present/commit contract intact.
 */
public final class RouteMapOverlay {

    /** Notified when the player (or the order-3 auto-driver) commits to a next node. */
    public interface NodeCommitListener {
        void onNodeCommitted(RouteNode node);
    }

    private List<RouteNode> candidates = Collections.emptyList();
    private NodeCommitListener commitListener;
    private boolean active;

    /** Wires the commit callback (World routes it to its node-&gt;floor pipeline). */
    public void setNodeCommitListener(NodeCommitListener listener) {
        this.commitListener = listener;
    }

    /**
     * Presents the legal next picks and activates the overlay. Called by {@code World} when the
     * player requests a descent.
     *
     * @param nextCandidates the AVAILABLE outgoing nodes from the current route node
     */
    public void present(List<RouteNode> nextCandidates) {
        this.candidates = new ArrayList<>(nextCandidates);
        this.active     = true;
    }

    /** The candidates currently on offer (unmodifiable). Order-4 renders these. */
    public List<RouteNode> getCandidates() {
        return Collections.unmodifiableList(candidates);
    }

    /** Whether the overlay is currently presenting a choice. */
    public boolean isActive() {
        return active;
    }

    /**
     * Commits to a node: deactivates the overlay and fires the listener. The node MUST be one of the
     * presented candidates.
     *
     * @throws IllegalArgumentException if the node was not among the presented candidates
     */
    public void commit(RouteNode node) {
        if (!candidates.contains(node)) {
            throw new IllegalArgumentException("Committed node was not one of the presented candidates");
        }
        active = false;
        candidates = Collections.emptyList();
        if (commitListener != null) {
            commitListener.onNodeCommitted(node);
        }
    }
}
