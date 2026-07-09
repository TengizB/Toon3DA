package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.RouteMapConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds a fresh, unique-every-run, forward-only branching route graph (a layered DAG, Slay-the-Spire
 * style) and fills every node with a type — and, for combat nodes, a concrete generator id. This is
 * the direct answer to "the roadmap must be random each time": from the run's master seed it produces
 * a fully deterministic {@link RouteMap} (same seed => byte-identical map), yet different runs get
 * different seeds and therefore different maps.
 *
 * <h2>Determinism model</h2>
 * <ul>
 *   <li>Each region band is generated from its own sub-stream {@code mixSeed(runSeed, regionIndex)},
 *       so a band is identical whether it is generated at run start or appended later by endless-mode
 *       extension. STRUCTURAL rolls (layer widths, spread edges) draw from that band stream.</li>
 *   <li>Every per-node CONTENT roll (node type, combat generator, affix) draws from an independent
 *       stream derived from the node's fixed {@code nodeSeed} — never from visit order — so the
 *       branch you could have taken is identical to the branch you did take.</li>
 *   <li>The anti-starvation / anti-clump guards are a deterministic post-pass (no RNG; they repair by
 *       fixed lane order), keeping the map reproducible.</li>
 * </ul>
 *
 * <p>Headless / pure — no LibGDX. All non-trivial math (weighted pick, lane projection, seed mix)
 * lives in {@link GameMath}; this class only orchestrates it.
 */
public final class RouteMapGenerator {

    // Independent per-node content sub-streams, so the type / generator / affix rolls of one node
    // never correlate with each other.
    private static final long TYPE_STREAM_SALT      = 1L;
    private static final long GENERATOR_STREAM_SALT = 2L;
    private static final long AFFIX_STREAM_SALT      = 3L;

    private final NodeTypeRegistry nodeTypes;
    private final GeneratorRegistry generators;

    // Affix catalogs — order-2 reserves the roll HOOK only; order-9 supplies real catalogs via the
    // setters. Empty by default, so no affix ever rolls until a catalog is injected.
    private List<NodeAffix> eliteAffixPool   = Collections.emptyList();
    private List<NodeAffix> mysteryAffixPool = Collections.emptyList();

    public RouteMapGenerator(NodeTypeRegistry nodeTypes, GeneratorRegistry generators) {
        this.nodeTypes  = nodeTypes;
        this.generators = generators;
    }

    /** Supplies the ELITE affix catalog (order-9). Empty = no affix rolls. */
    public void setEliteAffixPool(List<NodeAffix> pool) {
        this.eliteAffixPool = new ArrayList<>(pool);
    }

    /** Supplies the MYSTERY affix catalog (order-9). Empty = no affix rolls. */
    public void setMysteryAffixPool(List<NodeAffix> pool) {
        this.mysteryAffixPool = new ArrayList<>(pool);
    }

    // =========================================================================
    // Public entry points
    // =========================================================================

    /**
     * Generates the whole map for a run: a start node (layer 0, the floor the player stands on)
     * followed by every region band in the plan. The cursor is initialised on the start node.
     *
     * @param runSeed the run's master seed — the sole source of the map's randomness
     * @param plan    the data-driven act structure (see {@link RegionPlan#defaultPlan()})
     */
    public RouteMap generate(long runSeed, RegionPlan plan) {
        List<List<RouteNode>> allLayers = new ArrayList<>();

        // Layer 0 — the CURRENT floor. Its type is cosmetic (the player already stands here); it is
        // revealed so the overlay can render a "you are here" marker. It never rolls a generator.
        RouteNode startNode = makeNode(RouteNodeType.COMBAT, 0, 0, runSeed);
        startNode.revealed = true;
        allLayers.add(singletonLayer(startNode));

        // Rollable layers built so far, in order, used as READ-ONLY context so the sliding window
        // guards catch windows straddling a region boundary. Only the current band is ever mutated,
        // which keeps each band's result independent of later bands (deterministic lazy extension).
        List<List<RouteNode>> accumulatedRollable = new ArrayList<>();
        List<RouteNode> previousLayer = allLayers.get(0);
        for (RegionSpec region : plan.regions()) {
            List<List<RouteNode>> bandLayers = buildBand(runSeed, region, previousLayer);
            applyWindowGuards(bandLayers, accumulatedRollable);
            finaliseNodes(bandLayers, region);
            allLayers.addAll(bandLayers);
            collectRollable(bandLayers, accumulatedRollable);
            previousLayer = bandLayers.get(bandLayers.size() - 1);
        }

        RouteMap map = new RouteMap(allLayers, runSeed);
        for (RegionSpec region : plan.regions()) {
            map.addRegion(region.toRegion());
        }
        map.initializeCursor(startNode);
        return map;
    }

    /**
     * Endless-mode lazy extension: appends one more "THE BREACH" band to an existing map and wires it
     * onto the current final layer. Re-seeded from {@code mixSeed(runSeed, regionIndex)}, and the
     * window guards see the map's existing rollable layers as read-only context, so the appended band
     * is byte-identical to the band the same plan would have produced up front.
     *
     * @param map  the map to extend in place
     * @param plan the plan the map was last built from
     * @return the extended plan (the map now spans one region deeper)
     */
    public RegionPlan extendWithNextRegion(RouteMap map, RegionPlan plan) {
        RegionPlan extended    = plan.withAppendedBreachBand();
        RegionSpec newRegion   = extended.regions().get(extended.regionCount() - 1);
        List<RouteNode> prevLast = map.getLayers().get(map.getLayerCount() - 1);

        List<List<RouteNode>> accumulatedRollable = new ArrayList<>();
        collectRollable(map.getLayers(), accumulatedRollable);

        List<List<RouteNode>> bandLayers = buildBand(map.getRunSeed(), newRegion, prevLast);
        applyWindowGuards(bandLayers, accumulatedRollable);
        finaliseNodes(bandLayers, newRegion);
        map.appendLayers(bandLayers);
        map.addRegion(newRegion.toRegion());
        return extended;
    }

    /**
     * Whether the map should be extended now: true once the player's current depth comes within
     * {@link RouteMapConstants#EXTENSION_TRIGGER_DISTANCE} of the map's final layer.
     */
    public boolean shouldExtend(RouteMap map, int currentDepth) {
        int finalDepth = map.getLayerCount() - 1;
        return finalDepth - currentDepth <= RouteMapConstants.EXTENSION_TRIGGER_DISTANCE;
    }

    // =========================================================================
    // Band construction
    // =========================================================================

    /**
     * Builds every layer of one region band and wires it onto {@code previousLayer}, rolling node
     * types and applying the per-layer anti-clump guards. Structural rolls draw from the band's own
     * sub-stream {@code mixSeed(runSeed, regionIndex)} so the band is reproducible in isolation. The
     * windowed guards and finalisation are applied by the caller once the band is built.
     */
    private List<List<RouteNode>> buildBand(long runSeed, RegionSpec region, List<RouteNode> previousLayer) {
        RouteRng bandRng = new RouteRng(GameMath.mixSeed(runSeed, region.regionIndex()));
        List<List<RouteNode>> bandLayers = new ArrayList<>();
        List<RouteNode> priorLayer = previousLayer;

        for (int depth = region.firstDepth(); depth <= region.lastDepth(); depth++) {
            List<RouteNode> layer;
            if (depth == region.lastDepth()) {
                // Forced convergence: a boss on boss floors (GameMath.isBossFloor is the single
                // authority), otherwise a region gate. Either way the layer collapses to one node.
                RouteNodeType forcedType =
                        GameMath.isBossFloor(depth) ? RouteNodeType.BOSS : RouteNodeType.REGION_GATE;
                layer = singletonLayer(makeNode(forcedType, depth, 0, runSeed));
            } else {
                int width = bandRng.nextIntInclusive(
                        RouteMapConstants.BRANCH_WIDTH_MINIMUM, RouteMapConstants.BRANCH_WIDTH_MAXIMUM);
                boolean firstChoiceOfRun = depth == 1;
                layer = new ArrayList<>(width);
                for (int laneIndex = 0; laneIndex < width; laneIndex++) {
                    RouteNode node = makeNode(RouteNodeType.COMBAT, depth, laneIndex, runSeed);
                    node.type = rollNodeType(node, region, firstChoiceOfRun);
                    layer.add(node);
                }
                applyPerLayerGuards(layer, depth);
            }
            wireLayers(priorLayer, layer, bandRng);
            bandLayers.add(layer);
            priorLayer = layer;
        }
        return bandLayers;
    }

    /** Appends {@code bandLayers}' rollable (non-forced) layers to the running context list. */
    private void collectRollable(List<List<RouteNode>> bandLayers, List<List<RouteNode>> accumulator) {
        for (List<RouteNode> layer : bandLayers) {
            if (!isForcedLayer(layer)) {
                accumulator.add(layer);
            }
        }
    }

    // =========================================================================
    // Node-type roll
    // =========================================================================

    /**
     * Rolls a node type from the registry pool, scaled by the region's per-type multipliers. When
     * {@code firstChoiceOfRun} is set (the very first branch), the pool is biased hard toward COMBAT
     * and CACHE so new players are not gambling before they have footing.
     */
    private RouteNodeType rollNodeType(RouteNode node, RegionSpec region, boolean firstChoiceOfRun) {
        List<NodeTypeDefinition> pool = nodeTypes.allWeightedForPool();
        float[] weights = new float[pool.size()];
        for (int index = 0; index < pool.size(); index++) {
            NodeTypeDefinition definition = pool.get(index);
            float weight = definition.baseWeight() * region.nodeTypeMultiplier(definition.type());
            if (firstChoiceOfRun
                    && definition.type() != RouteNodeType.COMBAT
                    && definition.type() != RouteNodeType.CACHE) {
                weight = 0f; // safe-start bias: offer only COMBAT / CACHE on the opening layer
            }
            weights[index] = weight;
        }
        RouteRng rng = new RouteRng(GameMath.mixSeed(node.nodeSeed, TYPE_STREAM_SALT));
        int chosen = GameMath.weightedChoiceIndex(weights, rng.nextFloat01());
        return pool.get(chosen).type();
    }

    // =========================================================================
    // Anti-clump guards (per layer)
    // =========================================================================

    /**
     * Per-layer guards, applied in fixed lane order for reproducibility:
     * <ol>
     *   <li>no layer is ALL the same non-combat type (guarantees a real choice every hop);</li>
     *   <li>at least one COMBAT-family node per layer once past depth 1 (this is still a combat game).</li>
     * </ol>
     * Both repair by converting a deterministically chosen node to COMBAT.
     */
    private void applyPerLayerGuards(List<RouteNode> layer, int depth) {
        if (layer.size() >= 2 && allSameNonCombatType(layer)) {
            // Convert the highest-lane node so the layer still offers a genuine alternative.
            layer.get(layer.size() - 1).type = RouteNodeType.COMBAT;
        }
        if (depth > 1 && !hasCombatFamily(layer)) {
            // Convert the lowest-lane node to a combat node.
            layer.get(0).type = RouteNodeType.COMBAT;
        }
    }

    private boolean allSameNonCombatType(List<RouteNode> layer) {
        RouteNodeType first = layer.get(0).type;
        if (isCombatFamily(first)) {
            return false;
        }
        for (RouteNode node : layer) {
            if (node.type != first) {
                return false;
            }
        }
        return true;
    }

    private boolean hasCombatFamily(List<RouteNode> layer) {
        for (RouteNode node : layer) {
            if (isCombatFamily(node.type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCombatFamily(RouteNodeType type) {
        return type == RouteNodeType.COMBAT || type == RouteNodeType.ELITE;
    }

    // =========================================================================
    // Anti-starvation guards (sliding windows over the band's non-forced layers)
    // =========================================================================

    /**
     * Windowed guards over the concatenation of the already-built {@code contextRollable} layers
     * (read-only) and this band's rollable layers (mutable):
     * <ul>
     *   <li>no more than {@link RouteMapConstants#SHOP_MAX_PER_WINDOW} SHOP nodes in any
     *       {@link RouteMapConstants#SHOP_WINDOW_LAYERS}-wide window (economy pacing);</li>
     *   <li>a CACHE or REST is OFFERED at least once every
     *       {@link RouteMapConstants#RESOURCE_RELIEF_WINDOW} layers (protects the ammo economy).</li>
     * </ul>
     * Windows straddling the region boundary are covered because the previous band's tail layers are
     * included as context; repairs only ever touch the CURRENT band (indices &gt;= firstMutableIndex),
     * so earlier bands stay frozen. Every context-only window was already validated when its own band
     * was processed, which guarantees any offending window here contains a mutable node to repair.
     * Deterministic repair with a hard attempt cap so an over-constrained pool can never hang.
     */
    private void applyWindowGuards(List<List<RouteNode>> bandLayers, List<List<RouteNode>> contextRollable) {
        List<List<RouteNode>> combined = new ArrayList<>(contextRollable);
        int firstMutableIndex = combined.size();
        collectRollable(bandLayers, combined);
        enforceShopWindow(combined, firstMutableIndex);
        enforceReliefWindow(combined, firstMutableIndex);
    }

    private void enforceShopWindow(List<List<RouteNode>> rollable, int firstMutableIndex) {
        int window = RouteMapConstants.SHOP_WINDOW_LAYERS;
        for (int start = 0; start + window <= rollable.size(); start++) {
            if (start + window <= firstMutableIndex) {
                continue; // window lies entirely in frozen context — already validated
            }
            int attempts = 0;
            while (countType(rollable, start, window, RouteNodeType.SHOP)
                    > RouteMapConstants.SHOP_MAX_PER_WINDOW
                    && attempts++ < RouteMapConstants.GUARD_REPAIR_ATTEMPT_CAP) {
                // Convert the latest, highest-lane surplus shop that lives in the mutable band.
                RouteNode surplus = lastMutableNodeOfType(
                        rollable, start, window, firstMutableIndex, RouteNodeType.SHOP);
                if (surplus == null) {
                    break;
                }
                surplus.type = RouteNodeType.COMBAT;
            }
        }
    }

    private void enforceReliefWindow(List<List<RouteNode>> rollable, int firstMutableIndex) {
        int window = RouteMapConstants.RESOURCE_RELIEF_WINDOW;
        for (int start = 0; start + window <= rollable.size(); start++) {
            if (start + window <= firstMutableIndex) {
                continue; // window lies entirely in frozen context — already validated
            }
            if (windowOffersRelief(rollable, start, window)) {
                continue;
            }
            // No cache/rest anywhere in the window — plant a CACHE on a converted mutable node,
            // preferring a non-combat-family node late in the window so a combat option survives.
            RouteNode target = pickReliefConversionTarget(rollable, start, window, firstMutableIndex);
            if (target != null) {
                target.type = RouteNodeType.CACHE;
            }
        }
    }

    private boolean windowOffersRelief(List<List<RouteNode>> rollable, int start, int window) {
        for (int offset = 0; offset < window; offset++) {
            for (RouteNode node : rollable.get(start + offset)) {
                if (node.type == RouteNodeType.CACHE || node.type == RouteNodeType.REST) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Chooses which MUTABLE node to convert to a CACHE for relief. Scans the window from its LAST
     * layer backward (relief as late as possible), preferring a non-combat-family node so the layer
     * keeps a combat option; falls back to the last mutable layer's highest-lane node.
     */
    private RouteNode pickReliefConversionTarget(List<List<RouteNode>> rollable, int start, int window,
                                                 int firstMutableIndex) {
        for (int offset = window - 1; offset >= 0; offset--) {
            if (start + offset < firstMutableIndex) {
                break; // remaining layers are frozen context
            }
            List<RouteNode> layer = rollable.get(start + offset);
            for (int lane = layer.size() - 1; lane >= 0; lane--) {
                if (!isCombatFamily(layer.get(lane).type)) {
                    return layer.get(lane);
                }
            }
        }
        for (int offset = window - 1; offset >= 0; offset--) {
            if (start + offset < firstMutableIndex) {
                break;
            }
            List<RouteNode> layer = rollable.get(start + offset);
            return layer.get(layer.size() - 1);
        }
        return null;
    }

    private int countType(List<List<RouteNode>> rollable, int start, int window, RouteNodeType type) {
        int count = 0;
        for (int offset = 0; offset < window; offset++) {
            for (RouteNode node : rollable.get(start + offset)) {
                if (node.type == type) {
                    count++;
                }
            }
        }
        return count;
    }

    private RouteNode lastMutableNodeOfType(List<List<RouteNode>> rollable, int start, int window,
                                            int firstMutableIndex, RouteNodeType type) {
        RouteNode found = null;
        for (int offset = 0; offset < window; offset++) {
            if (start + offset < firstMutableIndex) {
                continue; // never repair a frozen context layer
            }
            for (RouteNode node : rollable.get(start + offset)) {
                if (node.type == type) {
                    found = node; // keep advancing so we end on the latest, highest-lane match
                }
            }
        }
        return found;
    }

    // =========================================================================
    // Finalisation — labels, combat generators, affixes (after all type mutations)
    // =========================================================================

    /**
     * Final pass over every node once its type is settled: resolve display label/hint, roll the
     * combat generator for COMBAT/ELITE nodes from the region's biased standard pool, and run the
     * affix hook. Deferred to here so a guard that changed a node's type still gets a correct
     * generator and label.
     */
    private void finaliseNodes(List<List<RouteNode>> bandLayers, RegionSpec region) {
        for (List<RouteNode> layer : bandLayers) {
            for (RouteNode node : layer) {
                NodeTypeDefinition definition = nodeTypes.get(node.type);
                node.resolvedLabel = definition.displayName();
                node.resolvedHint  = definition.hintLine();
                node.revealed      = RouteMapConstants.NODE_REVEALED_BY_DEFAULT;
                if (isCombatFamily(node.type)) {
                    node.chosenGeneratorId = rollCombatGenerator(node, region);
                }
                rollAffix(node);
            }
        }
    }

    /**
     * Rolls the level generator for a combat node from the registry's standard pool, weighted by the
     * region's per-generator leans. The pool is always read from the registry, so a newly registered
     * generator appears automatically; a region only biases the ids it names.
     */
    private GeneratorId rollCombatGenerator(RouteNode node, RegionSpec region) {
        List<GeneratorId> pool = generators.standardPool();
        if (pool.isEmpty()) {
            return null; // no standard generators registered — order-3 falls back safely
        }
        float[] weights = new float[pool.size()];
        for (int index = 0; index < pool.size(); index++) {
            weights[index] = region.generatorWeight(pool.get(index));
        }
        RouteRng rng = new RouteRng(GameMath.mixSeed(node.nodeSeed, GENERATOR_STREAM_SALT));
        int chosen = GameMath.weightedChoiceIndex(weights, rng.nextFloat01());
        return pool.get(chosen);
    }

    /**
     * Affix roll HOOK (order-2 reserves the field + seed; order-9 defines the catalog). ELITE and
     * MYSTERY nodes may roll an affix from their catalog; with the default empty catalogs this is a
     * no-op, so no affix ever appears until order-9 supplies one.
     */
    private void rollAffix(RouteNode node) {
        List<NodeAffix> catalog;
        float chance;
        if (node.type == RouteNodeType.ELITE) {
            catalog = eliteAffixPool;
            chance  = RouteMapConstants.AFFIX_ROLL_CHANCE_ELITE;
        } else if (node.type == RouteNodeType.MYSTERY) {
            catalog = mysteryAffixPool;
            chance  = RouteMapConstants.AFFIX_ROLL_CHANCE_MYSTERY;
        } else {
            return;
        }
        if (catalog.isEmpty() || chance <= 0f) {
            return;
        }
        RouteRng rng = new RouteRng(GameMath.mixSeed(node.nodeSeed, AFFIX_STREAM_SALT));
        if (rng.nextChance(chance)) {
            float[] weights = new float[catalog.size()];
            java.util.Arrays.fill(weights, 1f); // uniform until order-9 gives affixes their own weights
            node.affix = catalog.get(GameMath.weightedChoiceIndex(weights, rng.nextFloat01()));
        }
    }

    // =========================================================================
    // Edge wiring
    // =========================================================================

    /**
     * Wires forward edges from {@code source} into {@code target}, guaranteeing every source has an
     * outgoing edge and every target an incoming one. Convergence/divergence layers (width 1 on
     * either side — bosses, gates, the start node) fan fully in or out. Parallel sections wire each
     * source to its projected lane centre plus, rolled, that centre's +/-1 neighbours, then repair any
     * orphan target from its nearest centre — all within the one-lane readability rule.
     */
    private void wireLayers(List<RouteNode> source, List<RouteNode> target, RouteRng bandRng) {
        int sourceWidth = source.size();
        int targetWidth = target.size();

        if (sourceWidth == 1) {
            RouteNode single = source.get(0);
            for (RouteNode next : target) {
                connect(single, next);
            }
            return;
        }
        if (targetWidth == 1) {
            RouteNode sink = target.get(0);
            for (RouteNode previous : source) {
                connect(previous, sink);
            }
            return;
        }

        boolean[] targetCovered = new boolean[targetWidth];
        for (int fromLane = 0; fromLane < sourceWidth; fromLane++) {
            RouteNode sourceNode = source.get(fromLane);
            int centre = GameMath.projectedLaneCentre(fromLane, sourceWidth, targetWidth);
            connect(sourceNode, target.get(centre));
            targetCovered[centre] = true;
            for (int neighbour = centre - 1; neighbour <= centre + 1; neighbour += 2) {
                if (neighbour >= 0 && neighbour < targetWidth
                        && bandRng.nextChance(RouteMapConstants.BRANCH_SPREAD_CHANCE)) {
                    connect(sourceNode, target.get(neighbour));
                    targetCovered[neighbour] = true;
                }
            }
        }
        // Repair: every target must have an incoming edge. Connect each orphan from the source whose
        // projected centre is nearest it (guaranteed within one lane for the configured widths).
        for (int toLane = 0; toLane < targetWidth; toLane++) {
            if (targetCovered[toLane]) {
                continue;
            }
            int bestSource   = 0;
            int bestDistance = Integer.MAX_VALUE;
            for (int fromLane = 0; fromLane < sourceWidth; fromLane++) {
                int distance = Math.abs(
                        GameMath.projectedLaneCentre(fromLane, sourceWidth, targetWidth) - toLane);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestSource   = fromLane;
                }
            }
            connect(source.get(bestSource), target.get(toLane));
            targetCovered[toLane] = true;
        }
    }

    private void connect(RouteNode source, RouteNode target) {
        if (!source.outgoing.contains(target)) {
            source.outgoing.add(target);
        }
    }

    // =========================================================================
    // Small helpers
    // =========================================================================

    private RouteNode makeNode(RouteNodeType type, int depth, int laneIndex, long runSeed) {
        RouteNode node = new RouteNode(type, depth, laneIndex);
        node.nodeSeed = GameMath.routeNodeSeed(runSeed, depth, laneIndex);
        return node;
    }

    private static List<RouteNode> singletonLayer(RouteNode node) {
        List<RouteNode> layer = new ArrayList<>(1);
        layer.add(node);
        return layer;
    }

    private static boolean isForcedLayer(List<RouteNode> layer) {
        return layer.size() == 1
                && (layer.get(0).type == RouteNodeType.BOSS
                || layer.get(0).type == RouteNodeType.REGION_GATE);
    }
}
