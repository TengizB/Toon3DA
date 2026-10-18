package ge.tbegvadze.toon3d.util;

import ge.tbegvadze.toon3d.progression.BuildStats;
import ge.tbegvadze.toon3d.progression.BuildTag;
import ge.tbegvadze.toon3d.progression.PerkEffect;
import ge.tbegvadze.toon3d.progression.PerkModifier;
import ge.tbegvadze.toon3d.relic.RelicCatalog;
import ge.tbegvadze.toon3d.relic.RelicDefinition;
import ge.tbegvadze.toon3d.relic.RelicRarity;
import ge.tbegvadze.toon3d.relic.RelicRegistry;
import ge.tbegvadze.toon3d.relic.RelicSource;
import ge.tbegvadze.toon3d.relic.RelicValuation;
import ge.tbegvadze.toon3d.route.BossTable;
import ge.tbegvadze.toon3d.route.FacilityEvent;
import ge.tbegvadze.toon3d.route.NodeTypeDefinition;
import ge.tbegvadze.toon3d.route.RouteNodeType;
import ge.tbegvadze.toon3d.route.RouteRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * THE RELIC VALUATION (relics-run-changing-artifacts CP4) — every relic priced in the balance contract's own
 * terms, for R-RELIC / R-RELIC-CURSE / R-RELIC-STACK / R-RELIC-SOURCE (CP5), the route's offer pricing (CP6)
 * and TacticalPolicy's card choice (CP8). Pure, headless, deterministic; every table is lazily cached behind
 * {@code synchronized}.
 *
 * <p><b>A relic's lift</b> is the R-CARRY measurement of its effects alone:
 * {@link BuildValuationModel#carryOf}({@code BuildStats.ofEffects(effects)}, affinity, depth) — the
 * affinity tag's two-gun kit, or the best pair from every weapon without one. A SUPPLY relic's GAIN is the
 * {@link BuildNicheMetric#SUPPLY_TOTAL} ratio instead (the carry conversion under-reads supply, the reason
 * SCAVENGER's R-CARRY row is waived).
 *
 * <p><b>The hand.</b> Expected relics by a depth = the start take share + one per boss passed + the route's
 * node offer rate x floors. The expected hand multiplies each source's rarity-weighted mean lift raised to
 * its expected count ({@link GameMath#relicExpectedHandLift}); the best hand is the highest lifts reachable by
 * that depth (LEGENDARY at most one per boss passed, CURSED only once a node floor exists).
 *
 * <p>MODEL ASSUMPTIONS (stated once; BalanceConfig SECTION 26 VALUATION ASSUMPTIONS): a boss offer belongs to
 * the boss index = BossTable rows strictly shallower than its depth; a hand AT a depth holds what was
 * acquired before it (bosses strictly shallower); every floor 1..depth-1 is one weighted node pick (the boss
 * floors among them are a small over-count); an offer is worth its rarity-weighted MEAN card (not the best of
 * its cards); lifts are evaluated at the depth asked, not the depth of acquisition.
 */
public final class RelicValuationModel {

    private RelicValuationModel() {}

    /** The event CP6 registers for the QUARANTINE_LOCKER source (read by id; relic never imports route). */
    public static final String QUARANTINE_LOCKER_EVENT_ID = "quarantine_locker";

    private static final Map<String, Float> CARRY_LIFT_CACHE = new HashMap<>();
    private static final Map<String, Float> GAIN_CACHE = new HashMap<>();
    private static final Map<String, Float> MEAN_LIFT_CACHE = new HashMap<>();

    private static RelicRegistry registry() {
        return RelicCatalog.registry();
    }

    private static String key(String id, int depth) {
        return id + '@' + depth;
    }

    // =====================================================================================
    // ONE RELIC
    // =====================================================================================

    /** The relic's carry lift at a depth: carryOf(its effects alone, its affinity, depth).score. */
    public static synchronized float carryLift(RelicDefinition definition, int depth) {
        String cacheKey = key(definition.id(), depth);
        Float cached = CARRY_LIFT_CACHE.get(cacheKey);
        if (cached != null) return cached;
        float lift = BuildValuationModel.carryOf(BuildStats.ofEffects(definition.effects()), definition.affinity(), depth).score;
        CARRY_LIFT_CACHE.put(cacheKey, lift);
        return lift;
    }

    /**
     * The relic's banded GAIN at a depth: CARRY -> {@link #carryLift}; SUPPLY -> the floor's SUPPLY_TOTAL with
     * the relic over without it (power points, {@code BuildValuationModel.supply(...).total()}).
     */
    public static synchronized float gain(RelicDefinition definition, int depth) {
        if (definition.valuation() == RelicValuation.CARRY) return carryLift(definition, depth);
        String cacheKey = key(definition.id(), depth);
        Float cached = GAIN_CACHE.get(cacheKey);
        if (cached != null) return cached;
        BuildValuationModel.Context context = BuildValuationModel.context(depth);
        float before = BuildValuationModel.supply(context, new BuildStats()).total();
        float after = BuildValuationModel.supply(context, BuildStats.ofEffects(definition.effects())).total();
        float ratio = before > 0f ? after / before : 1f;
        GAIN_CACHE.put(cacheKey, ratio);
        return ratio;
    }

    /** The RELIC_REFERENCE_DEPTHS entry closest to a depth (a tie goes to the shallower one). */
    public static int nearestReferenceDepth(int depth) {
        int[] references = BalanceConfig.RELIC_REFERENCE_DEPTHS;
        int nearest = references[0];
        for (int reference : references) {
            if (Math.abs(reference - depth) < Math.abs(nearest - depth)) nearest = reference;
        }
        return nearest;
    }

    // =====================================================================================
    // POOLS AND SOURCES
    // =====================================================================================

    /** The arithmetic mean carry lift of every registered relic of a rarity at a depth (1 for an empty rarity). */
    public static synchronized float meanCarryLift(RelicRarity rarity, int depth) {
        String cacheKey = key(rarity.name(), depth);
        Float cached = MEAN_LIFT_CACHE.get(cacheKey);
        if (cached != null) return cached;
        float sum = 0f;
        int count = 0;
        for (RelicDefinition definition : registry().all()) {
            if (definition.rarity() != rarity) continue;
            sum += carryLift(definition, depth);
            count++;
        }
        float mean = count == 0 ? 1f : sum / count;
        MEAN_LIFT_CACHE.put(cacheKey, mean);
        return mean;
    }

    /** One offer's rarity-weighted mean carry lift: sum over the source's rarities of weight x meanCarryLift. */
    static float sourceMeanCarryLift(RelicSource source, int bossIndex, int depth) {
        RelicRarity[] rarities = source.allowedRarities();
        float[] weights = source.rarityWeightsFor(bossIndex);
        float weighted = 0f;
        float total = 0f;
        for (int index = 0; index < rarities.length && index < weights.length; index++) {
            weighted += weights[index] * meanCarryLift(rarities[index], depth);
            total += weights[index];
        }
        return total > 0f ? weighted / total : 1f;
    }

    /** The boss index of a boss offer at a depth: the BossTable rows strictly shallower than it. */
    static int bossIndexAt(int depth) {
        return bossesPassedBefore(depth);
    }

    /** BossTable rows strictly shallower than a depth (the boss offers a hand at that depth has seen). */
    static int bossesPassedBefore(int depth) {
        int passed = 0;
        for (BossTable.Row row : BossTable.standard().rows()) {
            if (row.depth < depth) passed++;
        }
        return passed;
    }

    /** Power points an offer from a source at a depth is worth on the route: relicPowerPoints(its mean lift). */
    public static float offerPowerPoints(RelicSource source, int depth) {
        return GameMath.relicPowerPoints(sourceMeanCarryLift(source, bossIndexAt(depth), depth));
    }

    // =====================================================================================
    // THE ROUTE'S OFFER RATE
    // =====================================================================================

    /** Expected relic offers TAKEN per floor from route nodes (RELIC_CACHE mystery + broken quarantine lockers). */
    public static float nodeOfferRatePerFloor() {
        return mysteryCacheRatePerFloor() + lockerRatePerFloor();
    }

    /** The RELIC_CACHE half of {@link #nodeOfferRatePerFloor} (an event weight of 0 drops the locker term). */
    static float mysteryCacheRatePerFloor() {
        return GameMath.relicNodeOfferRate(nodeWeight(RouteNodeType.MYSTERY), 0f, nodeWeightTotal(),
                BalanceConfig.MYSTERY_WEIGHT_RELIC_CACHE, mysteryWeightTotal(), 0f, 1f, 0f);
    }

    /** The quarantine_locker half of {@link #nodeOfferRatePerFloor}. */
    static float lockerRatePerFloor() {
        return GameMath.relicNodeOfferRate(0f, nodeWeight(RouteNodeType.EVENT), nodeWeightTotal(),
                0f, 1f, lockerEventWeight(), eventWeightTotal(), BalanceConfig.RELIC_VALUATION_EVENT_TAKE_SHARE);
    }

    private static float nodeWeight(RouteNodeType type) {
        NodeTypeDefinition definition = RouteRegistries.nodeTypes().get(type);
        return definition == null ? 0f : definition.baseWeight();
    }

    private static float nodeWeightTotal() {
        float total = 0f;
        for (NodeTypeDefinition definition : RouteRegistries.nodeTypes().allWeightedForPool()) {
            total += definition.baseWeight();
        }
        return total;
    }

    /**
     * The mystery pool's total with RELIC_CACHE in it: the six shipped outcome weights (RouteMapConstants,
     * mirrored from BalanceConfig) + MYSTERY_WEIGHT_RELIC_CACHE. CP6 adds the outcome row; it must not add a
     * second cache weight here. A new mystery outcome must be added to this sum (MysteryOutcome has no public
     * weight accessor).
     */
    private static float mysteryWeightTotal() {
        return RouteMapConstants.MYSTERY_WEIGHT_VAULT + RouteMapConstants.MYSTERY_WEIGHT_SECRET_WARREN
                + RouteMapConstants.MYSTERY_WEIGHT_TRAP_GAUNTLET + RouteMapConstants.MYSTERY_WEIGHT_AMBUSH
                + RouteMapConstants.MYSTERY_WEIGHT_LORE_SIGNAL + RouteMapConstants.MYSTERY_WEIGHT_MALFUNCTION
                + BalanceConfig.MYSTERY_WEIGHT_RELIC_CACHE;
    }

    /** The locker's event weight: its registered weight once CP6 registers it, else the stated assumption. */
    private static float lockerEventWeight() {
        FacilityEvent locker = RouteRegistries.events().get(QUARANTINE_LOCKER_EVENT_ID);
        return locker != null ? locker.weight() : BalanceConfig.RELIC_VALUATION_LOCKER_EVENT_WEIGHT;
    }

    /** The event pool's total with the locker in it exactly once. */
    private static float eventWeightTotal() {
        float total = 0f;
        for (FacilityEvent event : RouteRegistries.events().all()) {
            if (QUARANTINE_LOCKER_EVENT_ID.equals(event.id())) continue;
            total += event.weight();
        }
        return total + lockerEventWeight();
    }

    // =====================================================================================
    // THE HAND
    // =====================================================================================

    /** Expected relics held at a depth: start take share + bosses passed + (depth - 1) x node offer rate. */
    public static float expectedRelicCount(int depth) {
        return BalanceConfig.RELIC_VALUATION_START_TAKE_SHARE + bossesPassedBefore(depth)
                + Math.max(0, depth - 1) * nodeOfferRatePerFloor();
    }

    /**
     * The expected hand's carry lift at a depth: the start COMMON mean ^ take share, each boss passed's
     * rarity-weighted mean, the mystery caches' mean ^ (floors x cache rate) and the cursed mean ^ (floors x
     * locker rate) — {@link GameMath#relicExpectedHandLift}.
     */
    public static float expectedHandLift(int depth) {
        int bosses = bossesPassedBefore(depth);
        int terms = 3 + bosses;
        float[] means = new float[terms];
        float[] counts = new float[terms];
        int floors = Math.max(0, depth - 1);
        means[0] = sourceMeanCarryLift(RelicSource.RUN_START, 0, depth);
        counts[0] = BalanceConfig.RELIC_VALUATION_START_TAKE_SHARE;
        means[1] = sourceMeanCarryLift(RelicSource.MYSTERY_CACHE, 0, depth);
        counts[1] = floors * mysteryCacheRatePerFloor();
        means[2] = sourceMeanCarryLift(RelicSource.QUARANTINE_LOCKER, 0, depth);
        counts[2] = floors * lockerRatePerFloor();
        for (int bossIndex = 0; bossIndex < bosses; bossIndex++) {
            means[3 + bossIndex] = sourceMeanCarryLift(RelicSource.BOSS, bossIndex, depth);
            counts[3 + bossIndex] = 1f;
        }
        return GameMath.relicExpectedHandLift(means, counts, terms);
    }

    /**
     * The luckiest hand at a depth: the round(expectedRelicCount) highest carry lifts reachable (capped at
     * RELIC_MAX_HELD; LEGENDARY at most one per boss passed; CURSED only past depth 1) —
     * {@link GameMath#relicHandLift}. AFFINITY-CONSISTENT (CP5 hub decision): one two-gun build cannot use every
     * tag-affine relic at once, so a hand holds the non-affine relics plus the affine relics of at most ONE
     * {@link BuildTag}; the result is the best over every tag and the no-affine hand.
     */
    public static float bestHandLift(int depth) {
        float best = bestHandLift(depth, null);
        for (BuildTag tag : BuildTag.values()) {
            best = Math.max(best, bestHandLift(depth, tag));
        }
        return best;
    }

    /** The luckiest hand whose only tag-affine relics carry {@code allowedAffinity} (null = non-affine relics only). */
    static float bestHandLift(int depth, BuildTag allowedAffinity) {
        int count = Math.min(BalanceConfig.RELIC_MAX_HELD, Math.round(expectedRelicCount(depth)));
        int legendaryCap = bossesPassedBefore(depth);
        boolean cursedReachable = depth > 1 && lockerRatePerFloor() > 0f;
        List<Float> lifts = new ArrayList<>();
        List<Boolean> legendary = new ArrayList<>();
        for (RelicDefinition definition : registry().all()) {
            if (definition.affinity() != null && definition.affinity() != allowedAffinity) continue;
            if (definition.rarity() == RelicRarity.CURSED && !cursedReachable) continue;
            if (definition.rarity() == RelicRarity.LEGENDARY && legendaryCap == 0) continue;
            lifts.add(carryLift(definition, depth));
            legendary.add(definition.rarity() == RelicRarity.LEGENDARY);
        }
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < lifts.size(); index++) order.add(index);
        Collections.sort(order, (first, second) -> Float.compare(lifts.get(second), lifts.get(first)));
        float[] chosen = new float[count];
        int filled = 0;
        int legendaryTaken = 0;
        for (int index : order) {
            if (filled >= count) break;
            if (legendary.get(index)) {
                if (legendaryTaken >= legendaryCap) continue;
                legendaryTaken++;
            }
            chosen[filled++] = lifts.get(index);
        }
        return GameMath.relicHandLift(chosen, filled);
    }

    // =====================================================================================
    // THE CURSE (R-RELIC-CURSE / R-RELIC-SOURCE)
    // =====================================================================================

    /** The relic's COST effects alone ({@link PerkModifier#isCostAt}); empty for a relic with none. */
    public static PerkEffect[] costEffects(RelicDefinition definition) {
        List<PerkEffect> costs = new ArrayList<>();
        for (PerkEffect effect : definition.effects()) {
            if (effect.kind.isCostAt(effect.magnitudeAt(1))) costs.add(effect);
        }
        return costs.toArray(new PerkEffect[0]);
    }

    /**
     * What a cursed relic's price costs the player at a depth: worstLoss over EVERY probe (null niche) of its cost
     * effects alone (CP5 hub decision — worstLoss of the whole relic reports spurious losses for pure gains).
     * Null when the relic has no cost effect.
     */
    public static BuildValuationModel.Loss costLoss(RelicDefinition definition, int depth) {
        PerkEffect[] costs = costEffects(definition);
        if (costs.length == 0) return null;
        return BuildValuationModel.worstLoss(BuildStats.ofEffects(costs), null, depth);
    }
}
