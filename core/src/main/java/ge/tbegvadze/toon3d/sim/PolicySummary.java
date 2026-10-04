package ge.tbegvadze.toon3d.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.GameMath;
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
     * S-ECONOMY (re-based by balance-overhaul order 2): the mean share of each EXITED floor's planned ammo
     * units the runs actually picked up. NaN when no exited floor planned any ammo.
     */
    public float meanExperiencedSupplyShare() {
        float total = 0f;
        int   floors = 0;
        for (RunLedger run : runs) {
            for (FloorLedger floor : run.floors) {
                if (!floor.exited) continue;
                float share = floor.experiencedSupplyShare();
                if (Float.isNaN(share)) continue;
                total += share;
                floors++;
            }
        }
        return floors == 0 ? Float.NaN : total / floors;
    }

    /** S-SUPPLY: mean health fraction on LEAVING a COMBAT floor through its exit. NaN when none was left. */
    public float meanCombatExitHealthFraction() {
        float total = 0f;
        int   floors = 0;
        for (RunLedger run : runs) {
            for (FloorLedger floor : run.floors) {
                if (!floor.exited || !"COMBAT".equals(floor.nodeType)) continue;
                total += floor.healthFractionOnExit;
                floors++;
            }
        }
        return floors == 0 ? Float.NaN : total / floors;
    }

    /**
     * S-SWITCH (balance-overhaul order 3): hint-driven SWITCH taps per played COMBAT floor (stalled floors
     * included — a switch is a fight event, not a navigation one). NaN when no COMBAT floor was played.
     */
    public float meanMatchupSwitchesPerCombatFloor() {
        int switches = 0;
        int floors   = 0;
        for (RunLedger run : runs) {
            for (FloorLedger floor : run.floors) {
                if (!"COMBAT".equals(floor.nodeType)) continue;
                switches += floor.matchupSwitches;
                floors++;
            }
        }
        return floors == 0 ? Float.NaN : switches / (float) floors;
    }

    /** Hint take rate: hint-driven switches / hint episodes over every played floor (NaN when no hint). */
    public float hintTakeRate() {
        int switches = 0;
        int episodes = 0;
        for (RunLedger run : runs) {
            for (FloorLedger floor : run.floors) {
                switches += floor.matchupSwitches;
                episodes += floor.hintEpisodes;
            }
        }
        return episodes == 0 ? Float.NaN : switches / (float) episodes;
    }

    /** Hint-driven switches per COMBAT floor ON WHICH A HINT APPEARED (NaN when none did). */
    public float matchupSwitchesPerHintedCombatFloor() {
        int switches = 0;
        int floors   = 0;
        for (RunLedger run : runs) {
            for (FloorLedger floor : run.floors) {
                if (!"COMBAT".equals(floor.nodeType) || floor.hintEpisodes == 0) continue;
                switches += floor.matchupSwitches;
                floors++;
            }
        }
        return floors == 0 ? Float.NaN : switches / (float) floors;
    }

    /** COMBAT floors played, and how many of them showed a C4 hint at least once. */
    public int[] combatFloorsAndHintedCombatFloors() {
        int combat = 0;
        int hinted = 0;
        for (RunLedger run : runs) {
            for (FloorLedger floor : run.floors) {
                if (!"COMBAT".equals(floor.nodeType)) continue;
                combat++;
                if (floor.hintEpisodes > 0) hinted++;
            }
        }
        return new int[]{combat, hinted};
    }

    /** S-SUPPLY: played floors whose reachable heal value fell short of their S4 heal floor. */
    public int floorsBelowHealFloor() {
        int count = 0;
        for (RunLedger run : runs) {
            for (FloorLedger floor : run.floors) {
                if (!floor.healFloorMet()) count++;
            }
        }
        return count;
    }

    /** Every played floor of every run, in order (the FLOOR REPORT's input). */
    public List<FloorLedger> allFloors() {
        List<FloorLedger> floors = new java.util.ArrayList<>();
        for (RunLedger run : runs) floors.addAll(run.floors);
        return floors;
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
                // The on-curve character level of THE expected-player model (balance-overhaul order 1).
                float expectedLevel = GameMath.expectedCharacterLevelAtDepth(floor.depth);
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
