package ge.tbegvadze.toon3d.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.BalanceSchema;
import ge.tbegvadze.toon3d.util.BalanceSchema.RuleKind;
import ge.tbegvadze.toon3d.util.BalanceSchema.RuleResult;

/**
 * The claims the balance model makes about how the game PLAYS, evaluated against a played matrix
 * (new-game-balancr order 9).
 *
 * <p>Every other rule in the authority is a statement about NUMBERS and can be checked from
 * {@code BalanceConfig} alone; these are statements about BEHAVIOUR and need runs. They are still
 * ordinary schema rules — same {@link RuleResult} shape, same waiver lookup, same report tables —
 * they just take the simulated matrix as their input, which is why they live beside the simulator
 * instead of inside the (input-free) schema.
 *
 * <ul>
 *   <li>S-GATE     — the hoarder can never clear the first boss (the headline guarantee)</li>
 *   <li>S-FAIR     — the RUN OUTCOME of the finite 25-floor run (win rates, reach, death depths, the first boss —
 *                    balance-overhaul order 6, F8) and deaths that read as fair</li>
 *   <li>S-SKILL    — playing well is worth depths; the gap IS the difficulty range</li>
 *   <li>S-ROUTE    — the priced map's drain survives contact with play</li>
 *   <li>S-ECONOMY  — the supply a played floor yields tracks the supply its planner put down</li>
 *   <li>S-SUPPLY   — TACTICAL leaves a COMBAT floor hurt, and no floor is below its heal floor</li>
 *   <li>S-SOFTLOCK — a run may end, but never get stuck unable to damage anything</li>
 *   <li>S-NAVIGATION — the scripted player finishes the floors it plays (balance-overhaul order 7; replaced the
 *                    navigation waiver every depth-derived band used to carry)</li>
 *   <li>S-LAG      — a weapon that never climbs the power ladder ends the run early (balance-overhaul order 1)</li>
 *   <li>S-SWITCH   — a competent player switches guns by matchup when the game tells it to (balance-overhaul order 3)</li>
 *   <li>S-HEAVY    — a player who reads the red floor answers the heavies committed at it; one who ignores it does not (balance-overhaul order 4)</li>
 *   <li>S-BUILD    — no build bias is a trap or a must-pick, and plain TACTICAL does not end every run as the same character (balance-overhaul order 5)</li>
 *   <li>S-SHOP     — the shop is a place to spend: TACTICAL buys every visit and dies with little banked (balance-overhaul order 6)</li>
 *   <li>S-BOSS     — every boss is CONSISTENT: a fair clear rate, a steady fight length, a hurt-but-standing player (balance-overhaul order 6)</li>
 * </ul>
 */
public final class BehavioralBands {

    private BehavioralBands() {}

    public static final String NAIVE_ID    = "NAIVE";
    public static final String TACTICAL_ID = "TACTICAL";
    public static final String HOARDER_ID  = "HOARDER-START-WEAPON";
    /** Plain TACTICAL over SIM_BUILD_SAMPLE_SEED_COUNT seeds — the S-BUILD dominant-tag run set. */
    public static final String TACTICAL_BUILD_SAMPLE_ID = "TACTICAL-BUILD-SAMPLE";

    /** Evaluates every band the supplied matrix has the data for. Missing policies are skipped. */
    public static List<RuleResult> evaluate(Map<String, PolicySummary> matrix) {
        List<RuleResult> results = new ArrayList<>();
        PolicySummary naive    = matrix.get(NAIVE_ID);
        PolicySummary tactical = matrix.get(TACTICAL_ID);
        PolicySummary hoarder  = matrix.get(HOARDER_ID);

        if (hoarder != null)  results.addAll(gateResults(hoarder));
        if (hoarder != null)  results.add(lagResult(hoarder));
        if (tactical != null) results.addAll(fairResults(tactical, naive));
        if (tactical != null && naive != null) results.add(skillResult(tactical, naive));
        if (tactical != null) results.add(routeResult(tactical));
        if (tactical != null) results.addAll(economyResults(tactical));
        if (tactical != null) results.addAll(supplyResults(tactical));
        if (tactical != null) results.addAll(switchResults(tactical));
        if (tactical != null) results.addAll(heavyResults(tactical, naive));
        if (tactical != null) results.addAll(shopResults(tactical));
        if (tactical != null) results.addAll(bossResults(tactical));
        results.addAll(buildResults(matrix, matrix.get(TACTICAL_BUILD_SAMPLE_ID)));
        for (PolicySummary summary : matrix.values()) results.add(softlockResult(summary));
        for (PolicySummary summary : matrix.values()) results.add(navigationResult(summary));
        return results;
    }

    /** The four build-biased TACTICAL variant ids S-BUILD's depth part compares (sim/BuildBias). */
    public static List<String> buildBiasIds() {
        List<String> ids = new ArrayList<>();
        for (BuildBias bias : BuildBias.values()) {
            if (bias.isBiased()) ids.add(TACTICAL_ID + bias.policyIdSuffix);
        }
        return ids;
    }

    /**
     * S-BUILD (balance-overhaul order 5, A6). DEPTH part: the build-biased variants' median death depths satisfy
     * max / min <= 1 + SIM_BUILD_DEPTH_SPREAD (no trap bias, no must-pick bias) — depth-derived and ENFORCED since
     * balance-overhaul order 7 (navigation fixed); skipped unless all four variants are in the matrix.
     * DOMINANT-TAG part (rides the order-7 round-pending waiver; its sample line is enforced): over the plain-TACTICAL BUILD SAMPLE row's runs that held a perk rank at the
     * end (at least SIM_BUILD_MIN_SAMPLE of them), no single BuildTag is the end-of-run highest-rank tag on more
     * than SIM_BUILD_MAX_DOMINANT_TAG_SHARE of them. Runs with no rank are excluded and counted in the detail;
     * skipped when the sample row is not in the matrix.
     */
    private static List<RuleResult> buildResults(Map<String, PolicySummary> matrix, PolicySummary tactical) {
        List<RuleResult> results = new ArrayList<>();
        List<String> biasIds = buildBiasIds();
        float shallowest = Float.POSITIVE_INFINITY;
        float deepest    = 0f;
        StringBuilder medians = new StringBuilder();
        boolean allPresent = true;
        for (String id : biasIds) {
            PolicySummary variant = matrix.get(id);
            if (variant == null) {
                allPresent = false;
                continue;
            }
            float median = variant.medianEndingDepth();
            shallowest = Math.min(shallowest, median);
            deepest    = Math.max(deepest, median);
            if (medians.length() > 0) medians.append(", ");
            medians.append(id.substring(TACTICAL_ID.length() + 1)).append(' ').append(String.format("%.1f", median));
        }
        if (allPresent && !biasIds.isEmpty()) {
            float spread = shallowest <= 0f ? Float.POSITIVE_INFINITY : deepest / shallowest;
            float maximum = 1f + BalanceConfig.SIM_BUILD_DEPTH_SPREAD;
            results.add(BalanceSchema.result(RuleKind.SIM_BUILD, "build-bias median depth spread",
                    spread, 1f, maximum, spread <= maximum, "max / min median death depth: " + medians));
        }

        if (tactical == null) return results;
        int[] dominantCounts = tactical.dominantTagCounts();
        int excluded = tactical.runsWithNoPerkRank();
        int sample = tactical.runCount() - excluded;
        results.add(BalanceSchema.result(RuleKind.SIM_BUILD, "TACTICAL runs holding a perk rank",
                sample, BalanceConfig.SIM_BUILD_MIN_SAMPLE, Float.POSITIVE_INFINITY,
                sample >= BalanceConfig.SIM_BUILD_MIN_SAMPLE,
                "of " + tactical.runCount() + " " + tactical.policyId + " runs; " + excluded
                        + " with no perk rank excluded; keeps the share below off an empty sample"));
        int topCount = 0;
        ge.tbegvadze.toon3d.progression.BuildTag topTag = null;
        StringBuilder distribution = new StringBuilder();
        for (ge.tbegvadze.toon3d.progression.BuildTag tag : ge.tbegvadze.toon3d.progression.BuildTag.values()) {
            int count = dominantCounts[tag.ordinal()];
            if (count > topCount) {
                topCount = count;
                topTag = tag;
            }
            if (count > 0) {
                if (distribution.length() > 0) distribution.append(", ");
                distribution.append(tag.name()).append(' ').append(count);
            }
        }
        boolean measured = sample > 0;
        float share = measured ? topCount / (float) sample : 0f;
        results.add(BalanceSchema.result(RuleKind.SIM_BUILD, "TACTICAL dominant-tag share",
                share, 0f, BalanceConfig.SIM_BUILD_MAX_DOMINANT_TAG_SHARE,
                measured && share <= BalanceConfig.SIM_BUILD_MAX_DOMINANT_TAG_SHARE,
                measured ? "top " + topTag + "; " + distribution + "; " + tactical.runsWithTiedDominantTag()
                        + " decided by a tie (earliest tag wins)" : "no TACTICAL run held a perk rank"));
        return results;
    }

    /**
     * S-SHOP (balance-overhaul order 6, F14): the shop is a place to spend, not to window-shop. TACTICAL buys at
     * least SIM_SHOP_MIN_OFFERS_PER_VISIT offers per Fabricator visit, and its median credits held when a lost run
     * ends are at most SIM_SHOP_MAX_UNSPENT_FLOORS floors of the modelled income at that depth.
     */
    private static List<RuleResult> shopResults(PolicySummary tactical) {
        List<RuleResult> results = new ArrayList<>();
        int visits = tactical.fabricatorVisits();
        float perVisit = tactical.offersPerFabricatorVisit();
        results.add(BalanceSchema.result(RuleKind.SIM_SHOP, "TACTICAL offers bought per Fabricator visit",
                perVisit, BalanceConfig.SIM_SHOP_MIN_OFFERS_PER_VISIT, Float.POSITIVE_INFINITY,
                visits > 0 && perVisit >= BalanceConfig.SIM_SHOP_MIN_OFFERS_PER_VISIT,
                visits + " visits over " + tactical.runCount() + " runs"));
        float unspent = tactical.medianUnspentFloorsOfIncomeAtLoss();
        results.add(BalanceSchema.result(RuleKind.SIM_SHOP, "TACTICAL median unspent credits at death (floors of income)",
                unspent, 0f, BalanceConfig.SIM_SHOP_MAX_UNSPENT_FLOORS,
                unspent <= BalanceConfig.SIM_SHOP_MAX_UNSPENT_FLOORS, "credits held when a lost run ended"));
        return results;
    }

    /**
     * S-BOSS (balance-overhaul order 6, F10): for each BossTable row, over the TACTICAL seeds that reach it — the
     * clear rate in [SIM_BOSS_CLEAR_RATE_MIN, MAX] (the first boss at least SIM_FIRST_BOSS_TACTICAL_CLEAR_MIN); the
     * fight-length CoV at most SIM_BOSS_MAX_FIGHT_TURNS_COV; the median health fraction at the kill in
     * [SIM_BOSS_KILL_HEALTH_MIN, MAX]. A row with fewer than SIM_BOSS_MIN_SAMPLE kills reports its CoV and median
     * health as unmeasured (out of band). Depth-derived — rides the order-7 round-pending waiver until a round meets it.
     */
    private static List<RuleResult> bossResults(PolicySummary tactical) {
        List<RuleResult> results = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < BalanceConfig.BOSS_TABLE_DEPTHS.length; rowIndex++) {
            int depth = BalanceConfig.BOSS_TABLE_DEPTHS[rowIndex];
            List<FloorLedger> floors = tactical.bossFloorsAt(depth);
            List<Float> turns = new ArrayList<>();
            List<Float> health = new ArrayList<>();
            for (FloorLedger floor : floors) {
                if (!floor.bossKilled || floor.bossKillTurn < 0) continue;
                turns.add((float) floor.bossFightTurns());
                health.add(floor.bossKillHealthFraction);
            }
            int reached = floors.size();
            int cleared = turns.size();
            float clearRate = reached == 0 ? 0f : cleared / (float) reached;
            float clearMin = rowIndex == 0 ? BalanceConfig.SIM_FIRST_BOSS_TACTICAL_CLEAR_MIN : BalanceConfig.SIM_BOSS_CLEAR_RATE_MIN;
            results.add(BalanceSchema.result(RuleKind.SIM_BOSS, "boss depth " + depth + " TACTICAL clear rate",
                    clearRate, clearMin, BalanceConfig.SIM_BOSS_CLEAR_RATE_MAX,
                    reached > 0 && clearRate >= clearMin && clearRate <= BalanceConfig.SIM_BOSS_CLEAR_RATE_MAX,
                    cleared + " of " + reached + " seeds that reached it"));
            boolean measured = cleared >= BalanceConfig.SIM_BOSS_MIN_SAMPLE;
            float mean = 0f;
            for (float value : turns) mean += value;
            mean = cleared == 0 ? 0f : mean / cleared;
            float variance = 0f;
            for (float value : turns) variance += (value - mean) * (value - mean);
            float coefficient = cleared == 0 || mean <= 0f ? 0f : (float) Math.sqrt(variance / cleared) / mean;
            results.add(BalanceSchema.result(RuleKind.SIM_BOSS, "boss depth " + depth + " fight turns CoV",
                    coefficient, 0f, BalanceConfig.SIM_BOSS_MAX_FIGHT_TURNS_COV,
                    measured && coefficient <= BalanceConfig.SIM_BOSS_MAX_FIGHT_TURNS_COV,
                    String.format("mean %.1f turns over %d kills (needs >= %d)", mean, cleared, BalanceConfig.SIM_BOSS_MIN_SAMPLE)));
            java.util.Collections.sort(health);
            float medianHealth = health.isEmpty() ? 0f : (health.size() % 2 == 1 ? health.get(health.size() / 2)
                    : (health.get(health.size() / 2 - 1) + health.get(health.size() / 2)) / 2f);
            results.add(BalanceSchema.result(RuleKind.SIM_BOSS, "boss depth " + depth + " median health at the kill",
                    medianHealth, BalanceConfig.SIM_BOSS_KILL_HEALTH_MIN, BalanceConfig.SIM_BOSS_KILL_HEALTH_MAX,
                    measured && medianHealth >= BalanceConfig.SIM_BOSS_KILL_HEALTH_MIN
                            && medianHealth <= BalanceConfig.SIM_BOSS_KILL_HEALTH_MAX,
                    "health fraction the turn the boss died, over " + cleared + " kills"));
        }
        return results;
    }

    /** S-GATE: the starting loadout cannot beat the first boss, no matter how well it is played. */
    private static List<RuleResult> gateResults(PolicySummary hoarder) {
        List<RuleResult> results = new ArrayList<>();
        float clearedFraction = hoarder.clearedFirstBossFraction();
        results.add(BalanceSchema.result(RuleKind.SIM_GATE, "HOARDER first-boss clears",
                clearedFraction, 0f, BalanceConfig.SIM_GATE_MAX_CLEAR_FRACTION,
                clearedFraction <= BalanceConfig.SIM_GATE_MAX_CLEAR_FRACTION,
                hoarder.runCount() + " seeds; R-BOSS-GATE proves the same thing on paper"));
        return results;
    }

    /**
     * S-LAG (balance-overhaul order 1): a run whose weapon never climbs the power ladder must end early —
     * the median ending depth of the start-weapon hoarder is at most SIM_LAG_MAX_MEDIAN_DEPTH.
     */
    private static RuleResult lagResult(PolicySummary hoarder) {
        float medianDepth = hoarder.medianEndingDepth();
        return BalanceSchema.result(RuleKind.SIM_LAG, "HOARDER-START-WEAPON median death depth",
                medianDepth, 0f, BalanceConfig.SIM_LAG_MAX_MEDIAN_DEPTH,
                medianDepth <= BalanceConfig.SIM_LAG_MAX_MEDIAN_DEPTH,
                hoarder.runCount() + " seeds; " + hoarder.stalledRunCount() + " stalled");
    }

    /**
     * S-FAIR — THE RUN OUTCOME (balance-overhaul order 6, F8; REPLACES the TACTICAL median-death-depth band
     * [8, 14]) — and deaths the player could see coming. Out-of-band lines ride the order-7 round-pending waiver until a round meets them.
     */
    private static List<RuleResult> fairResults(PolicySummary tactical, PolicySummary naive) {
        List<RuleResult> results = new ArrayList<>();
        String stallNote = tactical.runCount() + " seeds; " + tactical.stalledRunCount() + " stalled";

        float winRate = tactical.winRate();
        results.add(BalanceSchema.result(RuleKind.SIM_FAIR, "TACTICAL win rate",
                winRate, BalanceConfig.SIM_TACTICAL_WIN_RATE_MIN, BalanceConfig.SIM_TACTICAL_WIN_RATE_MAX,
                winRate >= BalanceConfig.SIM_TACTICAL_WIN_RATE_MIN && winRate <= BalanceConfig.SIM_TACTICAL_WIN_RATE_MAX,
                "clears the depth-" + BalanceConfig.RUN_FINAL_DEPTH + " boss; " + stallNote));

        float reach = tactical.reachedDepthFraction(BalanceConfig.SIM_TACTICAL_REACH_DEPTH);
        results.add(BalanceSchema.result(RuleKind.SIM_FAIR, "TACTICAL reaches depth " + BalanceConfig.SIM_TACTICAL_REACH_DEPTH,
                reach, BalanceConfig.SIM_TACTICAL_REACH_FRACTION_MIN, 1f,
                reach >= BalanceConfig.SIM_TACTICAL_REACH_FRACTION_MIN, stallNote));

        float lossDepth = tactical.medianLossDepth();
        results.add(BalanceSchema.result(RuleKind.SIM_FAIR, "TACTICAL median death depth (losses)",
                lossDepth, BalanceConfig.SIM_TACTICAL_LOSS_DEPTH_MIN, BalanceConfig.SIM_TACTICAL_LOSS_DEPTH_MAX,
                lossDepth >= BalanceConfig.SIM_TACTICAL_LOSS_DEPTH_MIN
                        && lossDepth <= BalanceConfig.SIM_TACTICAL_LOSS_DEPTH_MAX, stallNote));

        int firstBossDepth = BalanceConfig.BOSS_TABLE_DEPTHS[0];
        int tacticalReachingBoss = tactical.runsReachingDepth(firstBossDepth);
        float tacticalBossClear = tacticalReachingBoss == 0 ? 0f
                : tactical.runsClearingFirstBoss() / (float) tacticalReachingBoss;
        results.add(BalanceSchema.result(RuleKind.SIM_FAIR, "TACTICAL first-boss clears (of seeds reaching it)",
                tacticalBossClear, BalanceConfig.SIM_FIRST_BOSS_TACTICAL_CLEAR_MIN, 1f,
                tacticalReachingBoss > 0 && tacticalBossClear >= BalanceConfig.SIM_FIRST_BOSS_TACTICAL_CLEAR_MIN,
                tactical.runsClearingFirstBoss() + " of " + tacticalReachingBoss + " seeds that reached depth " + firstBossDepth));

        if (naive != null) {
            float naiveWin = naive.winRate();
            results.add(BalanceSchema.result(RuleKind.SIM_FAIR, "NAIVE win rate",
                    naiveWin, 0f, BalanceConfig.SIM_NAIVE_WIN_RATE_MAX,
                    naiveWin <= BalanceConfig.SIM_NAIVE_WIN_RATE_MAX, naive.runCount() + " seeds"));
            float naiveDepth = naive.medianEndingDepth();
            results.add(BalanceSchema.result(RuleKind.SIM_FAIR, "NAIVE median death depth",
                    naiveDepth, BalanceConfig.SIM_NAIVE_DEPTH_MIN, BalanceConfig.SIM_NAIVE_DEPTH_MAX,
                    naiveDepth >= BalanceConfig.SIM_NAIVE_DEPTH_MIN && naiveDepth <= BalanceConfig.SIM_NAIVE_DEPTH_MAX,
                    naive.runCount() + " seeds; " + naive.stalledRunCount() + " stalled"));
            float naiveReach = naive.reachedDepthFraction(firstBossDepth);
            results.add(BalanceSchema.result(RuleKind.SIM_FAIR, "NAIVE reaches the first boss",
                    naiveReach, BalanceConfig.SIM_FIRST_BOSS_NAIVE_REACH_MIN, 1f,
                    naiveReach >= BalanceConfig.SIM_FIRST_BOSS_NAIVE_REACH_MIN, "depth " + firstBossDepth));
            float naiveClear = naive.clearedFirstBossFraction();
            results.add(BalanceSchema.result(RuleKind.SIM_FAIR, "NAIVE first-boss clears",
                    naiveClear, BalanceConfig.SIM_FIRST_BOSS_NAIVE_CLEAR_MIN, 1f,
                    naiveClear >= BalanceConfig.SIM_FIRST_BOSS_NAIVE_CLEAR_MIN, "of all " + naive.runCount() + " seeds"));
        }

        float readableFraction = tactical.readableDeathFraction();
        results.add(BalanceSchema.result(RuleKind.SIM_FAIR, "TACTICAL readable deaths",
                readableFraction, BalanceConfig.SIM_FAIR_READABLE_DEATH_MIN, 1f,
                readableFraction >= BalanceConfig.SIM_FAIR_READABLE_DEATH_MIN,
                "telegraphed intent or a sub-25% resource state before the fatal turn"));
        return results;
    }

    /** S-SKILL: the depth a competent player buys over an unthinking one. */
    private static RuleResult skillResult(PolicySummary tactical, PolicySummary naive) {
        float gap = tactical.medianEndingDepth() - naive.medianEndingDepth();
        return BalanceSchema.result(RuleKind.SIM_SKILL, "TACTICAL vs NAIVE depth gap",
                gap, BalanceConfig.SIM_SKILL_DEPTH_GAP_MIN, Float.POSITIVE_INFINITY,
                gap >= BalanceConfig.SIM_SKILL_DEPTH_GAP_MIN,
                String.format("TACTICAL %.1f vs NAIVE %.1f",
                        tactical.medianEndingDepth(), naive.medianEndingDepth()));
    }

    /**
     * S-ROUTE: the per-floor net HP drain the runs actually paid, against the order-7 modelled
     * trajectory band, widened by the play tolerance (a policy can dodge a fight the ledger charges
     * for, and does).
     */
    private static RuleResult routeResult(PolicySummary tactical) {
        int   fullPool  = BalanceConfig.PLAYER_MAX_HEALTH + BalanceConfig.PLAYER_MAX_ARMOR;
        float drain     = tactical.meanNetDrainFraction(fullPool);
        float tolerance = BalanceConfig.SIM_ROUTE_TRAJECTORY_TOLERANCE;
        float minimum   = BalanceConfig.ROUTE_TRAJECTORY_DRAIN_MIN - tolerance;
        float maximum   = BalanceConfig.ROUTE_TRAJECTORY_DRAIN_MAX + tolerance;
        return BalanceSchema.result(RuleKind.SIM_ROUTE, "played per-floor net drain",
                drain, minimum, maximum, drain >= minimum && drain <= maximum,
                "order-7 modelled band widened by the play tolerance");
    }

    /** S-ECONOMY: the played scarcity ratio tracks the model, and the lifeline stays a lifeline. */
    private static List<RuleResult> economyResults(PolicySummary tactical) {
        List<RuleResult> results = new ArrayList<>();
        float supplyShare = tactical.meanExperiencedSupplyShare();
        boolean measured  = !Float.isNaN(supplyShare);
        results.add(BalanceSchema.result(RuleKind.SIM_ECONOMY, "experienced vs planned supply",
                measured ? supplyShare : 0f, BalanceConfig.SIM_ECONOMY_SUPPLY_SHARE_MIN,
                BalanceConfig.SIM_ECONOMY_SUPPLY_SHARE_MAX,
                measured && supplyShare >= BalanceConfig.SIM_ECONOMY_SUPPLY_SHARE_MIN
                        && supplyShare <= BalanceConfig.SIM_ECONOMY_SUPPLY_SHARE_MAX,
                measured ? "mean share of each exited floor's planned ammo picked up" : "no exited floor planned ammo"));

        float emergencyFraction = tactical.emergencySupplyFloorFraction();
        results.add(BalanceSchema.result(RuleKind.SIM_ECONOMY, "emergency lifeline floors",
                emergencyFraction, 0f, BalanceConfig.SIM_ECONOMY_EMERGENCY_MAX_FRACTION,
                emergencyFraction <= BalanceConfig.SIM_ECONOMY_EMERGENCY_MAX_FRACTION,
                "the never-softlock ammo drop is a backstop, not a supply line"));
        return results;
    }

    /**
     * S-SUPPLY (balance-overhaul order 2, A7): TACTICAL leaves a COMBAT floor hurt but standing — its mean
     * health fraction at the exit inside [SIM_SUPPLY_EXIT_HEALTH_MIN, MAX] (AS2: "usually full HP" was the
     * complaint) — and NO played floor of any run sits below its S4 heal floor.
     */
    private static List<RuleResult> supplyResults(PolicySummary tactical) {
        List<RuleResult> results = new ArrayList<>();
        float exitHealth = tactical.meanCombatExitHealthFraction();
        boolean measured = !Float.isNaN(exitHealth);
        results.add(BalanceSchema.result(RuleKind.SIM_SUPPLY, "TACTICAL COMBAT exit health",
                measured ? exitHealth : 0f, BalanceConfig.SIM_SUPPLY_EXIT_HEALTH_MIN,
                BalanceConfig.SIM_SUPPLY_EXIT_HEALTH_MAX,
                measured && exitHealth >= BalanceConfig.SIM_SUPPLY_EXIT_HEALTH_MIN
                        && exitHealth <= BalanceConfig.SIM_SUPPLY_EXIT_HEALTH_MAX,
                measured ? "mean health fraction leaving a COMBAT floor by its exit" : "no COMBAT floor exited"));
        int belowFloor = tactical.floorsBelowHealFloor();
        results.add(BalanceSchema.result(RuleKind.SIM_SUPPLY, "floors below the heal floor",
                belowFloor, 0f, 0f, belowFloor == 0, "reachable heal value under the S4 floor"));
        return results;
    }

    /**
     * S-SWITCH (balance-overhaul order 3, A8 as re-stated by D3): the C4 hint WORKS — enough COMBAT floors
     * show one (SIM_SWITCH_MIN_HINTED_COMBAT_FLOORS), TACTICAL takes it (SIM_SWITCH_MIN_TAKE_RATE), and every
     * floor that shows one produces a matchup switch (SIM_SWITCH_MIN_PER_HINTED_COMBAT_FLOOR). The raw
     * per-COMBAT-floor count is reported, not banded: it measures the start kit's class spread, not the hint.
     */
    private static List<RuleResult> switchResults(PolicySummary tactical) {
        List<RuleResult> results = new ArrayList<>();
        int[] floors = tactical.combatFloorsAndHintedCombatFloors();
        int hintedFloors = floors[1];
        results.add(BalanceSchema.result(RuleKind.SIM_SWITCH, "TACTICAL COMBAT floors showing a hint",
                hintedFloors, BalanceConfig.SIM_SWITCH_MIN_HINTED_COMBAT_FLOORS, Float.POSITIVE_INFINITY,
                hintedFloors >= BalanceConfig.SIM_SWITCH_MIN_HINTED_COMBAT_FLOORS,
                "of " + floors[0] + " COMBAT floors; keeps the two lines below off an empty sample"));
        float takeRate = tactical.hintTakeRate();
        boolean takeMeasured = !Float.isNaN(takeRate);
        results.add(BalanceSchema.result(RuleKind.SIM_SWITCH, "TACTICAL hint take rate",
                takeMeasured ? takeRate : 0f, BalanceConfig.SIM_SWITCH_MIN_TAKE_RATE, Float.POSITIVE_INFINITY,
                takeMeasured && takeRate >= BalanceConfig.SIM_SWITCH_MIN_TAKE_RATE,
                takeMeasured ? "hint-driven switches / hint episodes" : "no hint appeared"));
        float perHintedFloor = tactical.matchupSwitchesPerHintedCombatFloor();
        boolean floorMeasured = !Float.isNaN(perHintedFloor);
        float perCombatFloor = tactical.meanMatchupSwitchesPerCombatFloor();
        results.add(BalanceSchema.result(RuleKind.SIM_SWITCH, "TACTICAL switches per hinted COMBAT floor",
                floorMeasured ? perHintedFloor : 0f, BalanceConfig.SIM_SWITCH_MIN_PER_HINTED_COMBAT_FLOOR,
                Float.POSITIVE_INFINITY,
                floorMeasured && perHintedFloor >= BalanceConfig.SIM_SWITCH_MIN_PER_HINTED_COMBAT_FLOOR,
                String.format("info: %.2f per COMBAT floor overall (kit-limited, not banded — D3)",
                        Float.isNaN(perCombatFloor) ? 0f : perCombatFloor)));
        return results;
    }

    /**
     * S-HEAVY (balance-overhaul order 4, A8): of the heavies COMMITTED AT the player, TACTICAL answers (avoids,
     * guards, staggers or kills first) at least SIM_HEAVY_TACTICAL_MIN_SUCCESS and NAIVE — which ignores
     * telegraphs — at most SIM_HEAVY_NAIVE_MAX_SUCCESS; and TACTICAL met at least SIM_HEAVY_MIN_SAMPLE of them,
     * so the share cannot pass vacuously. The NAIVE line is skipped when no NAIVE run is in the matrix.
     */
    private static List<RuleResult> heavyResults(PolicySummary tactical, PolicySummary naive) {
        List<RuleResult> results = new ArrayList<>();
        int sample = tactical.heavyCommittedAtPlayer();
        results.add(BalanceSchema.result(RuleKind.SIM_HEAVY, "TACTICAL heavies committed at it",
                sample, BalanceConfig.SIM_HEAVY_MIN_SAMPLE, Float.POSITIVE_INFINITY,
                sample >= BalanceConfig.SIM_HEAVY_MIN_SAMPLE,
                "keeps the share below off an empty sample"));
        float share = tactical.heavySuccessShare();
        boolean measured = !Float.isNaN(share);
        results.add(BalanceSchema.result(RuleKind.SIM_HEAVY, "TACTICAL heavies answered",
                measured ? share : 0f, BalanceConfig.SIM_HEAVY_TACTICAL_MIN_SUCCESS, 1f,
                measured && share >= BalanceConfig.SIM_HEAVY_TACTICAL_MIN_SUCCESS,
                measured ? "(avoided + guarded + staggered + killed first) / committed at the player"
                         : "no heavy was committed at TACTICAL"));
        if (naive != null) {
            float naiveShare = naive.heavySuccessShare();
            boolean naiveMeasured = !Float.isNaN(naiveShare);
            results.add(BalanceSchema.result(RuleKind.SIM_HEAVY, "NAIVE heavies answered",
                    naiveMeasured ? naiveShare : 0f, 0f, BalanceConfig.SIM_HEAVY_NAIVE_MAX_SUCCESS,
                    !naiveMeasured || naiveShare <= BalanceConfig.SIM_HEAVY_NAIVE_MAX_SUCCESS,
                    naiveMeasured ? naive.heavyCommittedAtPlayer() + " committed; NAIVE ignores telegraphs"
                                  : "no heavy was committed at NAIVE"));
        }
        return results;
    }

    /**
     * S-NAVIGATION (balance-overhaul order 7, P8 / A1): the scripted player finishes the floors it plays — at most
     * SIM_MAX_STALLED_FLOOR_FRACTION of played floors may run the turn cap out. This is the condition every depth-
     * derived band depends on; it replaced the order-9 navigation waiver. Never waived.
     */
    private static RuleResult navigationResult(PolicySummary summary) {
        float stalledShare = summary.stalledFloorFraction();
        return BalanceSchema.result(RuleKind.SIM_NAVIGATION, summary.policyId + " stalled floors",
                stalledShare, 0f, BalanceConfig.SIM_MAX_STALLED_FLOOR_FRACTION,
                stalledShare <= BalanceConfig.SIM_MAX_STALLED_FLOOR_FRACTION,
                summary.stalledRunCount() + " stalled runs; share of every played floor");
    }

    /** S-SOFTLOCK: no run may end unable to damage anything. Zero, by contract, for every policy. */
    private static RuleResult softlockResult(PolicySummary summary) {
        float softlockFraction = summary.softlockFraction();
        return BalanceSchema.result(RuleKind.SIM_SOFTLOCK, summary.policyId + " softlocks",
                softlockFraction, 0f, BalanceConfig.SIM_SOFTLOCK_MAX_FRACTION,
                softlockFraction <= BalanceConfig.SIM_SOFTLOCK_MAX_FRACTION,
                summary.runCount() + " seeds");
    }
}
