package ge.tbegvadze.toon3d.route;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cursor and status-transition tests for {@link RouteMap}. Builds tiny hand-wired graphs (order-1
 * has no generator yet — that is order-2) to exercise the state machine directly.
 */
class RouteMapTest {

    /** Builds a diamond: start -> {left, right} -> join. */
    private RouteMap diamond() {
        RouteNode start = new RouteNode(RouteNodeType.COMBAT, 1, 0);
        RouteNode left  = new RouteNode(RouteNodeType.CACHE, 2, 0);
        RouteNode right = new RouteNode(RouteNodeType.ELITE, 2, 1);
        RouteNode join  = new RouteNode(RouteNodeType.COMBAT, 3, 0);
        start.outgoing.add(left);
        start.outgoing.add(right);
        left.outgoing.add(join);
        right.outgoing.add(join);

        List<List<RouteNode>> layers = new ArrayList<>();
        layers.add(List.of(start));
        layers.add(List.of(left, right));
        layers.add(List.of(join));

        RouteMap map = new RouteMap(layers, 12345L);
        map.initializeCursor(start);
        return map;
    }

    @Test
    void initializeCursorSetsCurrentAndMakesNextAvailable() {
        RouteMap map = diamond();
        RouteNode start = map.getCurrent();
        assertEquals(NodeStatus.CURRENT, start.status);
        List<RouteNode> selectable = map.getSelectableNext();
        assertEquals(2, selectable.size());
        for (RouteNode node : selectable) {
            assertEquals(NodeStatus.AVAILABLE, node.status);
        }
    }

    @Test
    void commitToRestatusesCurrentVisitedSiblingBypassedAndAdvances() {
        RouteMap map = diamond();
        RouteNode start = map.getCurrent();
        List<RouteNode> selectable = map.getSelectableNext();
        RouteNode chosen  = selectable.get(0);
        RouteNode sibling = selectable.get(1);

        map.commitTo(chosen);

        assertEquals(NodeStatus.VISITED, start.status);
        assertEquals(NodeStatus.CURRENT, chosen.status);
        assertEquals(NodeStatus.BYPASSED, sibling.status);
        assertEquals(chosen, map.getCurrent());
        // The join node beyond the chosen branch is now a legal pick.
        assertEquals(1, map.getSelectableNext().size());
        assertEquals(NodeStatus.AVAILABLE, chosen.outgoing.get(0).status);
    }

    @Test
    void commitToRejectsANonAvailableNode() {
        RouteMap map = diamond();
        RouteNode join = map.getLayers().get(2).get(0); // LOCKED, not an outgoing edge of current
        assertThrows(IllegalArgumentException.class, () -> map.commitTo(join));
    }

    @Test
    void isBossNextTrueOnlyWhenEveryNextIsBoss() {
        RouteNode start = new RouteNode(RouteNodeType.COMBAT, 1, 0);
        RouteNode boss  = new RouteNode(RouteNodeType.BOSS, 2, 0);
        start.outgoing.add(boss);
        List<List<RouteNode>> layers = new ArrayList<>();
        layers.add(List.of(start));
        layers.add(List.of(boss));
        RouteMap map = new RouteMap(layers, 1L);
        map.initializeCursor(start);
        assertTrue(map.isBossNext());

        // A diamond (non-boss picks) must report false.
        assertFalse(diamond().isBossNext());
    }

    @Test
    void revealRegionLiftsFogForNodesInThatBand() {
        RouteMap map = diamond();
        map.addRegion(new RouteRegion(0, "SECTOR ALPHA", 1, 2, "alpha"));
        map.addRegion(new RouteRegion(1, "SECTOR BETA", 3, 3, "beta"));

        map.revealRegion(0);
        for (List<RouteNode> layer : map.getLayers()) {
            for (RouteNode node : layer) {
                if (node.depth <= 2) {
                    assertTrue(node.revealed, "depth " + node.depth + " should be revealed");
                } else {
                    assertFalse(node.revealed, "depth " + node.depth + " should still be fogged");
                }
            }
        }
    }

    @Test
    void constructorDefensivelyCopiesLayers() {
        RouteNode start = new RouteNode(RouteNodeType.COMBAT, 1, 0);
        List<List<RouteNode>> layers = new ArrayList<>();
        List<RouteNode> firstLayer = new ArrayList<>();
        firstLayer.add(start);
        layers.add(firstLayer);

        RouteMap map = new RouteMap(layers, 1L);
        firstLayer.add(new RouteNode(RouteNodeType.ELITE, 1, 1)); // mutate original after construction
        assertEquals(1, map.getLayers().get(0).size(), "map must not see post-construction mutation");
    }
}
