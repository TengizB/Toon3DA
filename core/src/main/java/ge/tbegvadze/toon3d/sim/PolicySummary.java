package ge.tbegvadze.toon3d.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.BalanceSchema;

/**
 * One policy's runs, reduced to the handful of numbers the behavioural bands actually assert on
 * (new-game-balancr order 9).
 *
 * <p>Distributions, not averages, where it matters: the depth band is stated on the MEDIAN because a
 * single lucky run to the ceiling must not drag the claim upward. Everything here is derived from
 * {@link RunLedger}s and nothing is cached — recompute, print, compare.
 */
public final class PolicySummary {

    public final String          policyId;
    public final List<RunLedger> runs;

    public PolicySummary(String policyId, List<RunLedger> runs) {
        this.policyId = policyId;
        this.runs     = runs;
    }

    public int runCount() { return runs.size(); }

    /**
     * The median depth runs ENDED on — the "how deep does this player get?" headline. Returned as a
     * float so an even sample averages its two middle runs instead of silently rounding down.
     */
    public float medianEndingDepth() {
        List<Integer> depths = new ArrayList<>(runs.size());
        for (RunLedger run : runs) depths.add(run.endingDepth);
        return median(depths);
    }

    /** Fraction of runs that killed the FIRST boss (the S-GATE subject). */
    public float clearedFirstBossFraction() {
        int cleared = 0;
        for (RunLedger run : runs) if (run.clearedFirstBoss) cleared++;
        return fraction(cleared, runs.size());
    }

    /**
     * Fraction of DEATHS that were readable: the fatal turn was either preceded by a committed enemy
     * intent, or entered in a resource crisis. Runs that did not end in death are excluded — they
     * have no death to judge.
     */
    public float readableDeathFraction() {
        int deaths = 0;
        int readable = 0;
        for (RunLedger run : runs) {
            if (run.ending != RunLedger.Ending.KILLED) continue;
            deaths++;
            if (run.deathWasTelegraphed || run.deathFollowedResourceCrisis) readable++;
        }
        return fraction(readable, deaths);
    }

    /** Fraction of runs that ended unable to damage anything (the S-SOFTLOCK subject). */
    public float softlockFraction() {
        int softlocked = 0;
        for (RunLedger run : runs) if (run.softlocked) softlocked++;
        return fraction(softlocked, runs.size());
    }

    /** Fraction of floors on which the never-softlock emergency ammo lifeline fired. */
    public float emergencySupplyFloorFraction() {
        int floors = 0;
        int fired  = 0;
        for (RunLedger run : runs) {
            for (FloorLedger floor : run.floors) {
                floors++;
                if (floor.emergencySupplyFired) fired++;
            }
        }
        return fraction(fired, floors);
    }

    /**
     * Mean absolute gap between the scarcity ratio S the runs EXPERIENCED on a floor and the S the
     * order-3 model predicts for that depth — the S-ECONOMY subject.
     */
    public float meanScarcityGapVersusModel() {
        float totalGap = 0f;
        int   samples  = 0;
        for (RunLedger run : runs) {
            for (FloorLedger floor : run.floors) {
                if (floor.demandDamage <= 0f) continue;
                float experienced = floor.experiencedScarcityRatio();
                float modelled    = BalanceSchema.modelledScarcityAtDepth(floor.depth);
                totalGap += Math.abs(experienced - modelled);
                samples++;
            }
        }
        return samples == 0 ? 0f : totalGap / samples;
    }

    /**
     * Mean per-floor NET hit-point drain as a fraction of the full vitality pool — the played
     * counterpart of the order-7 modelled trajectory drain (the S-ROUTE subject).
     */
    public float meanNetDrainFraction(int fullVitalityPool) {
        float totalDrain = 0f;
        int   floors     = 0;
        for (RunLedger run : runs) {
            for (FloorLedger floor : run.floors) {
                totalDrain += floor.netDrainFraction(fullVitalityPool);
                floors++;
            }
        }
        return floors == 0 ? 0f : totalDrain / floors;
    }

    /** Mean gap between the player's level and the level the depth expects (negative = behind). */
    public float meanLevelVersusExpected() {
        float totalGap = 0f;
        int   samples  = 0;
        for (RunLedger run : runs) {
            for (FloorLedger floor : run.floors) {
                float expectedLevel = 1f + BalanceConfig.EXPECTED_LEVELS_PER_DEPTH * (floor.depth - 1);
                totalGap += floor.playerLevelOnArrival - expectedLevel;
                samples++;
            }
        }
        return samples == 0 ? 0f : totalGap / samples;
    }

    /** Mean turns spent on the boss floors that were actually fought. */
    public float meanBossFloorTurns() {
        int   floors = 0;
        float turns  = 0f;
        for (RunLedger run : runs) {
            for (FloorLedger floor : run.floors) {
                if (!floor.bossFloor) continue;
                floors++;
                turns += floor.turnsSpent;
            }
        }
        return floors == 0 ? 0f : turns / floors;
    }

    /** Fraction of runs that reached a boss floor at all — the denominator S-GATE needs to mean anything. */
    public float bossFloorReachedFraction() {
        int reached = 0;
        for (RunLedger run : runs) {
            for (FloorLedger floor : run.floors) {
                if (floor.bossFloor) { reached++; break; }
            }
        }
        return fraction(reached, runs.size());
    }

    /** Mean enemies killed per run — a coarse "is the gun working?" signal for the sabotage check. */
    public float meanKillsPerRun() {
        int kills = 0;
        for (RunLedger run : runs) kills += run.total(floor -> floor.enemiesKilled);
        return runs.isEmpty() ? 0f : kills / (float) runs.size();
    }

    /** Runs that could not finish a floor inside the turn cap — a policy failure, reported not hidden. */
    public int stalledRunCount() {
        int stalled = 0;
        for (RunLedger run : runs) if (run.ending == RunLedger.Ending.STALLED) stalled++;
        return stalled;
    }

    private static float median(List<Integer> values) {
        if (values.isEmpty()) return 0f;
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) return sorted.get(middle);
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2f;
    }

    private static float fraction(int part, int whole) {
        return whole == 0 ? 0f : part / (float) whole;
    }
}
