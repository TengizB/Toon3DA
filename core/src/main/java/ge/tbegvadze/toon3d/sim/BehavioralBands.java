package ge.tbegvadze.toon3d.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.BalanceSchema;
import ge.tbegvadze.toon3d.util.BalanceSchema.RuleKind;
import ge.tbegvadze.toon3d.util.BalanceSchema.RuleResult;

/**
 * The six claims the balance model makes about how the game PLAYS, evaluated against a played matrix
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
 *   <li>S-FAIR     — deaths land in the intended depth window and read as fair</li>
 *   <li>S-SKILL    — playing well is worth depths; the gap IS the difficulty range</li>
 *   <li>S-ROUTE    — the priced map's drain survives contact with play</li>
 *   <li>S-ECONOMY  — the supply a played floor yields tracks the supply its planner put down</li>
 *   <li>S-SUPPLY   — TACTICAL leaves a COMBAT floor hurt, and no floor is below its heal floor</li>
 *   <li>S-SOFTLOCK — a run may end, but never get stuck unable to damage anything</li>
 *   <li>S-LAG      — a weapon that never climbs the power ladder ends the run early (balance-overhaul order 1)</li>
 * </ul>
 */
public final class BehavioralBands {

    private BehavioralBands() {}

    public static final String NAIVE_ID    = "NAIVE";
    public static final String TACTICAL_ID = "TACTICAL";
    public static final String HOARDER_ID  = "HOARDER-START-WEAPON";

    /** Evaluates every band the supplied matrix has the data for. Missing policies are skipped. */
    public static List<RuleResult> evaluate(Map<String, PolicySummary> matrix) {
        List<RuleResult> results = new ArrayList<>();
        PolicySummary naive    = matrix.get(NAIVE_ID);
        PolicySummary tactical = matrix.get(TACTICAL_ID);
        PolicySummary hoarder  = matrix.get(HOARDER_ID);

        if (hoarder != null)  results.addAll(gateResults(hoarder));
        if (hoarder != null)  results.add(lagResult(hoarder));
        if (tactical != null) results.addAll(fairResults(tactical));
        if (tactical != null && naive != null) results.add(skillResult(tactical, naive));
        if (tactical != null) results.add(routeResult(tactical));
        if (tactical != null) results.addAll(economyResults(tactical));
        if (tactical != null) results.addAll(supplyResults(tactical));
        for (PolicySummary summary : matrix.values()) results.add(softlockResult(summary));
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

    /** S-FAIR: the intended run length, and deaths the player could see coming. */
    private static List<RuleResult> fairResults(PolicySummary tactical) {
        List<RuleResult> results = new ArrayList<>();
        float medianDepth = tactical.medianEndingDepth();
        results.add(BalanceSchema.result(RuleKind.SIM_FAIR, "TACTICAL median death depth",
                medianDepth, BalanceConfig.SIM_TARGET_DEPTH_MIN, BalanceConfig.SIM_TARGET_DEPTH_MAX,
                medianDepth >= BalanceConfig.SIM_TARGET_DEPTH_MIN
                        && medianDepth <= BalanceConfig.SIM_TARGET_DEPTH_MAX,
                tactical.runCount() + " seeds; " + tactical.stalledRunCount() + " stalled"));

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

    /** S-SOFTLOCK: no run may end unable to damage anything. Zero, by contract, for every policy. */
    private static RuleResult softlockResult(PolicySummary summary) {
        float softlockFraction = summary.softlockFraction();
        return BalanceSchema.result(RuleKind.SIM_SOFTLOCK, summary.policyId + " softlocks",
                softlockFraction, 0f, BalanceConfig.SIM_SOFTLOCK_MAX_FRACTION,
                softlockFraction <= BalanceConfig.SIM_SOFTLOCK_MAX_FRACTION,
                summary.runCount() + " seeds");
    }
}
