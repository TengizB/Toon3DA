package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.RouteMapConstants;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless tests for {@link RouteMapGenerator}: determinism, structural connectivity, boss
 * convergence, the anti-starvation / anti-clump guards, combat-generator assignment, and endless
 * lazy extension. Uses freshly populated registries so the shared singletons are never touched.
 */
class RouteMapGeneratorTest {

    private NodeTypeRegistry freshNodeTypes() {
        NodeTypeRegistry registry = new NodeTypeRegistry();
        RouteRegistries.registerNodeTypes(registry);
        return registry;
    }

    private GeneratorRegistry freshGenerators() {
        GeneratorRegistry registry = new GeneratorRegistry();
        RouteRegistries.registerGenerators(registry);
        return registry;
    }

    private RouteMapGenerator generator() {
        return new RouteMapGenerator(freshNodeTypes(), freshGenerators());
    }

    // ---------------------------------------------------------------------
    // Determinism
    // ---------------------------------------------------------------------

    @Test
    void sameSeedProducesAnIdenticalMap() {
        RegionPlan plan = RegionPlan.defaultPlan();
        RouteMap first  = generator().generate(0xC0FFEEL, plan);
        RouteMap second = generator().generate(0xC0FFEEL, plan);
        assertMapsStructurallyEqual(first, second);
    }

    @Test
    void differentSeedsProduceDifferentMaps() {
        RegionPlan plan = RegionPlan.defaultPlan();
        RouteMap first  = generator().generate(1L, plan);
        RouteMap second = generator().generate(2L, plan);
        assertFalse(mapsStructurallyEqual(first, second),
                "two different seeds should not yield identical maps");
    }

    // ---------------------------------------------------------------------
    // Structural connectivity
    // ---------------------------------------------------------------------

    @Test
    void everyNodeReachableNoOrphansNoDeadEndsBossLayersForced() {
        RegionPlan plan = RegionPlan.defaultPlan();
        for (long seed = 0; seed < 40; seed++) {
            RouteMap map = generator().generate(seed, plan);
            assertNoOrphans(map);
            assertNoDeadEnds(map);
            assertBossLayersSingleAndForced(map, plan);
            assertFinalLayerReachableFromStart(map);
        }
    }

    @Test
    void parallelEdgesNeverCrossMoreThanOneLane() {
        RegionPlan plan = RegionPlan.defaultPlan();
        for (long seed = 0; seed < 40; seed++) {
            RouteMap map = generator().generate(seed, plan);
            List<List<RouteNode>> layers = map.getLayers();
            for (int layerIndex = 0; layerIndex < layers.size() - 1; layerIndex++) {
                List<RouteNode> source = layers.get(layerIndex);
                List<RouteNode> target = layers.get(layerIndex + 1);
                if (source.size() < 2 || target.size() < 2) {
                    continue; // convergence / divergence layers are exempt by design
                }
                for (int fromLane = 0; fromLane < source.size(); fromLane++) {
                    for (RouteNode next : source.get(fromLane).outgoing) {
                        assertTrue(
                                GameMath.lanesConnectable(fromLane, source.size(), next.laneIndex, target.size()),
                                "edge crosses more than one lane at layer " + layerIndex
                                        + " seed " + seed);
                    }
                }
            }
        }
    }

    @Test
    void regionGateIsForcedWhenARegionDoesNotEndOnABossFloor() {
        // A custom plan whose only region ends at depth 3 (not a boss floor) must forge a REGION_GATE.
        RegionSpec shortRegion = RegionSpec.builder(0)
                .displayName("TEST WING").themeId("test").depthSpan(1, 3).build();
        RegionPlan plan = new RegionPlan(List.of(shortRegion));
        RouteMap map = generator().generate(99L, plan);

        List<RouteNode> finalLayer = map.getLayers().get(map.getLayerCount() - 1);
        assertEquals(1, finalLayer.size(), "the region's last layer must collapse to one node");
        assertEquals(RouteNodeType.REGION_GATE, finalLayer.get(0).type);
    }

    // ---------------------------------------------------------------------
    // Anti-starvation / anti-clump guards
    // ---------------------------------------------------------------------

    @Test
    void antiStarvationWindowsHonouredAcrossManySeeds() {
        RegionPlan plan = RegionPlan.defaultPlan();
        for (long seed = 0; seed < 100; seed++) {
            RouteMap map = generator().generate(seed, plan);
            List<List<RouteNode>> rollable = rollableLayers(map);

            // No more than SHOP_MAX_PER_WINDOW shops in any SHOP_WINDOW_LAYERS window.
            int shopWindow = RouteMapConstants.SHOP_WINDOW_LAYERS;
            for (int start = 0; start + shopWindow <= rollable.size(); start++) {
                int shops = 0;
                for (int offset = 0; offset < shopWindow; offset++) {
                    for (RouteNode node : rollable.get(start + offset)) {
                        if (node.type == RouteNodeType.SHOP) shops++;
                    }
                }
                assertTrue(shops <= RouteMapConstants.SHOP_MAX_PER_WINDOW,
                        "too many shops (" + shops + ") in a window, seed " + seed);
            }

            // A cache or rest offered within every RESOURCE_RELIEF_WINDOW window.
            int reliefWindow = RouteMapConstants.RESOURCE_RELIEF_WINDOW;
            for (int start = 0; start + reliefWindow <= rollable.size(); start++) {
                boolean relief = false;
                for (int offset = 0; offset < reliefWindow; offset++) {
                    for (RouteNode node : rollable.get(start + offset)) {
                        if (node.type == RouteNodeType.CACHE || node.type == RouteNodeType.REST) {
                            relief = true;
                        }
                    }
                }
                assertTrue(relief, "no cache/rest offered within a relief window, seed " + seed);
            }
        }
    }

    /**
     * PRE-BOSS PROVISIONING GUARANTEE (new-game-balancr order 6): the layer immediately before every boss
     * floor must offer a SHOP or CACHE that WIRES FORWARD to the boss — a reachable "sharpen your knife"
     * stop so the boss tests the BUILD, not whether the player happened to arrive stocked. Proven over 100
     * seeds across every boss floor in the default plan (depths 5, 10, 15).
     */
    @Test
    void everyBossIsPrecededByReachableProvisioningOverAHundredSeeds() {
        RegionPlan plan = RegionPlan.defaultPlan();
        List<String> failures = new ArrayList<>();
        for (long seed = 0; seed < 100; seed++) {
            RouteMap map = generator().generate(seed, plan);
            List<List<RouteNode>> layers = map.getLayers();
            for (int depth = 1; depth < layers.size(); depth++) {
                if (!GameMath.isBossFloor(depth)) continue;
                List<RouteNode> bossLayer = layers.get(depth);
                if (bossLayer.size() != 1 || bossLayer.get(0).type != RouteNodeType.BOSS) continue;
                RouteNode boss = bossLayer.get(0);
                boolean reachableProvisioning = false;
                for (RouteNode node : layers.get(depth - 1)) {
                    boolean provisions = node.type == RouteNodeType.SHOP || node.type == RouteNodeType.CACHE;
                    if (provisions && node.outgoing.contains(boss)) {
                        reachableProvisioning = true;
                        break;
                    }
                }
                if (!reachableProvisioning) {
                    failures.add("seed " + seed + " depth " + depth + ": no reachable SHOP/CACHE before the boss");
                }
            }
        }
        assertTrue(failures.isEmpty(),
                () -> "Pre-boss provisioning guarantee violated:\n" + String.join("\n", failures));
    }

    @Test
    void everyRollableLayerOffersARealChoiceAndCombatPastDepthOne() {
        RegionPlan plan = RegionPlan.defaultPlan();
        for (long seed = 0; seed < 40; seed++) {
            RouteMap map = generator().generate(seed, plan);
            for (List<RouteNode> layer : rollableLayers(map)) {
                int depth = layer.get(0).depth;
                // No layer is entirely one non-combat type.
                boolean allSame = true;
                RouteNodeType first = layer.get(0).type;
                for (RouteNode node : layer) {
                    if (node.type != first) allSame = false;
                }
                boolean combatFamily = first == RouteNodeType.COMBAT || first == RouteNodeType.ELITE;
                assertFalse(allSame && !combatFamily,
                        "layer at depth " + depth + " is all " + first + ", seed " + seed);
                // At least one combat-family node past depth 1.
                if (depth > 1) {
                    boolean hasCombat = false;
                    for (RouteNode node : layer) {
                        if (node.type == RouteNodeType.COMBAT || node.type == RouteNodeType.ELITE) {
                            hasCombat = true;
                        }
                    }
                    assertTrue(hasCombat, "no combat option at depth " + depth + ", seed " + seed);
                }
            }
        }
    }

    @Test
    void firstChoiceLeansSafeOfferingOnlyCombatOrCache() {
        RegionPlan plan = RegionPlan.defaultPlan();
        for (long seed = 0; seed < 40; seed++) {
            RouteMap map = generator().generate(seed, plan);
            for (RouteNode node : map.getLayers().get(1)) { // depth 1 = the opening choice
                assertTrue(node.type == RouteNodeType.COMBAT || node.type == RouteNodeType.CACHE,
                        "opening layer offered " + node.type + ", seed " + seed);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Combat-generator assignment
    // ---------------------------------------------------------------------

    @Test
    void combatNodesAlwaysCarryAStandardPoolGenerator() {
        RegionPlan plan = RegionPlan.defaultPlan();
        Set<GeneratorId> standard = new HashSet<>(freshGenerators().standardPool());
        for (long seed = 0; seed < 40; seed++) {
            RouteMap map = generator().generate(seed, plan);
            for (List<RouteNode> layer : map.getLayers()) {
                for (RouteNode node : layer) {
                    if (node.depth == 0) {
                        continue; // the cosmetic start node (current floor) never rolls a generator
                    }
                    if (node.type == RouteNodeType.COMBAT || node.type == RouteNodeType.ELITE) {
                        assertNotNull(node.chosenGeneratorId, "combat node has no generator, seed " + seed);
                        assertTrue(standard.contains(node.chosenGeneratorId),
                                node.chosenGeneratorId + " is not in the standard pool, seed " + seed);
                    } else {
                        // Special nodes do not roll a generator here (order-7 assigns theirs by profile).
                        assertTrue(node.chosenGeneratorId == null,
                                "special node " + node.type + " unexpectedly carries a generator");
                    }
                }
            }
        }
    }

    @Test
    void everyStandardGeneratorAppearsAcrossManyMaps() {
        // Proxy for the extensibility test: the combat pool is read from the registry, so every
        // registered standard generator must be reachable on some map. A newly registered generator
        // would appear the same way with zero generator-code changes.
        RegionPlan plan = RegionPlan.defaultPlan();
        Set<GeneratorId> seen = new HashSet<>();
        for (long seed = 0; seed < 60; seed++) {
            RouteMap map = generator().generate(seed, plan);
            for (List<RouteNode> layer : map.getLayers()) {
                for (RouteNode node : layer) {
                    if (node.chosenGeneratorId != null) {
                        seen.add(node.chosenGeneratorId);
                    }
                }
            }
        }
        for (GeneratorId id : freshGenerators().standardPool()) {
            assertTrue(seen.contains(id), id + " never appeared on any generated map");
        }
    }

    @Test
    void everyRollableNodeTypeAppearsAcrossManyMaps() {
        // Proxy for the node-type extensibility test: the pool is read from the registry, so every
        // non-forced registered type must be reachable on some map.
        RegionPlan plan = RegionPlan.defaultPlan();
        Set<RouteNodeType> seen = new HashSet<>();
        for (long seed = 0; seed < 80; seed++) {
            RouteMap map = generator().generate(seed, plan);
            for (List<RouteNode> layer : map.getLayers()) {
                for (RouteNode node : layer) {
                    seen.add(node.type);
                }
            }
        }
        for (NodeTypeDefinition definition : freshNodeTypes().allWeightedForPool()) {
            assertTrue(seen.contains(definition.type()),
                    definition.type() + " never appeared on any generated map");
        }
    }

    // ---------------------------------------------------------------------
    // Regions + endless extension
    // ---------------------------------------------------------------------

    @Test
    void mapRecordsEveryRegionBand() {
        RegionPlan plan = RegionPlan.defaultPlan();
        RouteMap map = generator().generate(7L, plan);
        assertEquals(plan.regionCount(), map.getRegions().size());
        assertEquals(plan.finalDepth(), map.getLayerCount() - 1);
    }

    @Test
    void endlessExtensionAppendsAReproducibleBand() {
        RegionPlan plan = RegionPlan.defaultPlan();
        long seed = 123456L;

        RouteMap map = generator().generate(seed, plan);
        int depthBefore = map.getLayerCount() - 1;
        RegionPlan extended = generator().extendWithNextRegion(map, plan);

        assertEquals(depthBefore + RouteMapConstants.REGION_BAND_SIZE, map.getLayerCount() - 1);
        assertEquals(plan.regionCount() + 1, extended.regionCount());
        assertEquals(plan.regionCount() + 1, map.getRegions().size());

        // The appended band is byte-identical to generating the extended plan up front (proves the
        // per-region mixSeed makes lazy extension deterministic).
        RouteMap upfront = generator().generate(seed, extended);
        assertMapsStructurallyEqual(map, upfront);
    }

    @Test
    void shouldExtendTriggersOnlyNearTheFinalLayer() {
        RouteMap map = generator().generate(5L, RegionPlan.defaultPlan());
        int finalDepth = map.getLayerCount() - 1;
        assertFalse(generator().shouldExtend(map, 1));
        assertTrue(generator().shouldExtend(map, finalDepth - RouteMapConstants.EXTENSION_TRIGGER_DISTANCE));
        assertTrue(generator().shouldExtend(map, finalDepth));
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private List<List<RouteNode>> rollableLayers(RouteMap map) {
        List<List<RouteNode>> rollable = new ArrayList<>();
        for (List<RouteNode> layer : map.getLayers()) {
            boolean forced = layer.size() == 1
                    && (layer.get(0).type == RouteNodeType.BOSS
                    || layer.get(0).type == RouteNodeType.REGION_GATE);
            if (!forced && layer.get(0).depth > 0) {
                rollable.add(layer);
            }
        }
        return rollable;
    }

    private void assertNoOrphans(RouteMap map) {
        List<List<RouteNode>> layers = map.getLayers();
        Set<RouteNode> hasIncoming = new HashSet<>();
        for (List<RouteNode> layer : layers) {
            for (RouteNode node : layer) {
                hasIncoming.addAll(node.outgoing);
            }
        }
        for (int layerIndex = 1; layerIndex < layers.size(); layerIndex++) {
            for (RouteNode node : layers.get(layerIndex)) {
                assertTrue(hasIncoming.contains(node),
                        "orphan node at depth " + node.depth + " lane " + node.laneIndex);
            }
        }
    }

    private void assertNoDeadEnds(RouteMap map) {
        List<List<RouteNode>> layers = map.getLayers();
        for (int layerIndex = 0; layerIndex < layers.size() - 1; layerIndex++) {
            for (RouteNode node : layers.get(layerIndex)) {
                assertFalse(node.outgoing.isEmpty(),
                        "dead-end node at depth " + node.depth + " lane " + node.laneIndex);
            }
        }
    }

    private void assertBossLayersSingleAndForced(RouteMap map, RegionPlan plan) {
        List<List<RouteNode>> layers = map.getLayers();
        for (RegionSpec region : plan.regions()) {
            List<RouteNode> layer = layers.get(region.lastDepth());
            assertEquals(1, layer.size(), "region-end layer at depth " + region.lastDepth() + " not single");
            RouteNodeType expected =
                    GameMath.isBossFloor(region.lastDepth()) ? RouteNodeType.BOSS : RouteNodeType.REGION_GATE;
            assertEquals(expected, layer.get(0).type);
        }
    }

    private void assertFinalLayerReachableFromStart(RouteMap map) {
        List<List<RouteNode>> layers = map.getLayers();
        Set<RouteNode> reachable = new HashSet<>();
        Deque<RouteNode> frontier = new ArrayDeque<>();
        RouteNode start = layers.get(0).get(0);
        frontier.add(start);
        reachable.add(start);
        while (!frontier.isEmpty()) {
            RouteNode node = frontier.poll();
            for (RouteNode next : node.outgoing) {
                if (reachable.add(next)) {
                    frontier.add(next);
                }
            }
        }
        boolean finalReached = false;
        for (RouteNode node : layers.get(layers.size() - 1)) {
            if (reachable.contains(node)) finalReached = true;
        }
        assertTrue(finalReached, "no path reaches the final layer from the start");
    }

    private void assertMapsStructurallyEqual(RouteMap first, RouteMap second) {
        assertTrue(mapsStructurallyEqual(first, second), "maps are not structurally identical");
    }

    private boolean mapsStructurallyEqual(RouteMap first, RouteMap second) {
        List<List<RouteNode>> a = first.getLayers();
        List<List<RouteNode>> b = second.getLayers();
        if (a.size() != b.size()) return false;
        for (int layerIndex = 0; layerIndex < a.size(); layerIndex++) {
            List<RouteNode> layerA = a.get(layerIndex);
            List<RouteNode> layerB = b.get(layerIndex);
            if (layerA.size() != layerB.size()) return false;
            for (int lane = 0; lane < layerA.size(); lane++) {
                RouteNode nodeA = layerA.get(lane);
                RouteNode nodeB = layerB.get(lane);
                if (nodeA.type != nodeB.type) return false;
                if (nodeA.depth != nodeB.depth) return false;
                if (nodeA.laneIndex != nodeB.laneIndex) return false;
                if (nodeA.nodeSeed != nodeB.nodeSeed) return false;
                if (nodeA.chosenGeneratorId != nodeB.chosenGeneratorId) return false;
                if (!edgeSignature(nodeA).equals(edgeSignature(nodeB))) return false;
            }
        }
        return true;
    }

    /** A node's outgoing edges as an order-independent set of (depth, lane) targets. */
    private Set<String> edgeSignature(RouteNode node) {
        Set<String> signature = new HashSet<>();
        for (RouteNode next : node.outgoing) {
            signature.add(next.depth + ":" + next.laneIndex);
        }
        return signature;
    }
}
