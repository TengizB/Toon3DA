package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.RouteMapConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Determinism tests for {@link GameMath#routeNodeSeed}. The whole map is derived from these seeds,
 * so the "same seed => identical map" promise reduces to: the per-node seed is a pure function of
 * (runSeed, depth, laneIndex), and its stream is independent of the per-floor seed.
 */
class RouteDeterminismTest {

    // Mirrors World.floorSeed so we can assert route seeds never collide with floor seeds.
    private static long floorSeed(long runSeed, int depth) {
        return runSeed * 0x9E3779B97F4A7C15L + depth;
    }

    @Test
    void sameInputsProduceTheSameSeed() {
        long first  = GameMath.routeNodeSeed(0xABCDEF12L, 3, 2);
        long second = GameMath.routeNodeSeed(0xABCDEF12L, 3, 2);
        assertEquals(first, second);
    }

    @Test
    void distinctLanesAndDepthsProduceDistinctSeeds() {
        long runSeed = 777L;
        assertNotEquals(GameMath.routeNodeSeed(runSeed, 3, 0), GameMath.routeNodeSeed(runSeed, 3, 1));
        assertNotEquals(GameMath.routeNodeSeed(runSeed, 3, 0), GameMath.routeNodeSeed(runSeed, 4, 0));
    }

    @Test
    void differentRunSeedsProduceDifferentSeeds() {
        assertNotEquals(GameMath.routeNodeSeed(1L, 2, 1), GameMath.routeNodeSeed(2L, 2, 1));
    }

    @Test
    void routeSeedIsIndependentOfTheFloorSeedStream() {
        // The salt must keep a node's roll from ever equalling that floor's LevelGenerator seed.
        long runSeed = 0xDEADBEEFL;
        for (int depth = 1; depth <= 30; depth++) {
            long floor = floorSeed(runSeed, depth);
            long node  = GameMath.routeNodeSeed(runSeed, depth, 0);
            assertNotEquals(floor, node, "route seed collided with floor seed at depth " + depth);
        }
    }

    @Test
    void saltIsAppliedSoZeroRunSeedStillMixes() {
        // With a zero run seed the salt is the only entropy; adjacent lanes must still differ.
        assertNotEquals(0L, GameMath.routeNodeSeed(0L, 0, 0));
        assertNotEquals(GameMath.routeNodeSeed(0L, 0, 0), GameMath.routeNodeSeed(0L, 0, 1));
        assertEquals(RouteMapConstants.ROUTE_NODE_SEED_SALT, 0x526F757465L);
    }
}
