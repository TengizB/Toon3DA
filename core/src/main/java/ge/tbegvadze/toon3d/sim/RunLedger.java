package ge.tbegvadze.toon3d.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * The complete record of ONE simulated run (new-game-balancr order 9): where it died, what killed
 * it, and what every floor cost along the way.
 *
 * <p>This is the raw material the behavioural bands (S-GATE / S-FAIR / S-SKILL / S-ROUTE /
 * S-ECONOMY / S-SOFTLOCK) are computed from. It is also, deliberately, the same shape as the live
 * RUN AUTOPSY the death screen shows a human playtester, so a simulated death and a reported death
 * can be read side by side.
 */
public final class RunLedger {

    /** Why the run ended. */
    public enum Ending {
        /** The marine was killed. The normal ending. */
        KILLED,
        /** The run hit the simulator's depth ceiling still alive (a "win" for band purposes). */
        SURVIVED_TO_CEILING,
        /** The run stalled: a floor could not be finished within the turn cap. */
        STALLED
    }

    public final String policyId;
    public final long   runSeed;

    /** Deepest floor reached (1-based). */
    public int depthReached = 1;

    /** The floor the run ENDED on — the "died depth N" a playtester reports. */
    public int endingDepth = 1;

    public Ending ending = Ending.KILLED;

    /** Display name of whatever landed the killing blow, or "-" when the run did not end in death. */
    public String killedBy = "-";

    /**
     * True when the fatal turn was preceded by a COMMITTED enemy intent — the player was shown the
     * incoming hit before it landed (the readability half of the S-FAIR band).
     */
    public boolean deathWasTelegraphed;

    /**
     * True when the marine entered the fatal turn already below the resource-crisis threshold
     * (vitality, ammo or heals under 25%) — a death the player walked into, not an ambush.
     */
    public boolean deathFollowedResourceCrisis;

    /** True when the run could not damage anything any more: enemies alive, every weapon dry. */
    public boolean softlocked;

    /** True when the run killed the FIRST boss (the S-GATE subject). */
    public boolean clearedFirstBoss;

    /** Player level when the run ended. */
    public int finalPlayerLevel = 1;

    /** Total credits spent at vending machines across the run. */
    public int creditsSpent;

    /** Times the never-softlock emergency ammo lifeline fired. */
    public int emergencySupplyTriggers;

    /** One entry per floor entered, in descent order. */
    public final List<FloorLedger> floors = new ArrayList<>();

    /** The route the run walked, as node-id tokens in descent order (the S-ROUTE subject). */
    public final List<String> routeTokens = new ArrayList<>();

    public RunLedger(String policyId, long runSeed) {
        this.policyId = policyId;
        this.runSeed  = runSeed;
    }

    /** Sum of one int field across every floor, via a getter — keeps the aggregate code declarative. */
    public int total(java.util.function.ToIntFunction<FloorLedger> field) {
        int sum = 0;
        for (FloorLedger floor : floors) sum += field.applyAsInt(floor);
        return sum;
    }

    /** The floor record for a depth, or null when the run never got there. */
    public FloorLedger floorAt(int depth) {
        for (FloorLedger floor : floors) if (floor.depth == depth) return floor;
        return null;
    }

    /** A one-line summary, the same shape as the death screen's autopsy header. */
    public String summaryLine() {
        return String.format("%s seed=%d depth=%d ending=%s killedBy=%s level=%d telegraphed=%s crisis=%s",
                policyId, runSeed, endingDepth, ending, killedBy, finalPlayerLevel,
                deathWasTelegraphed, deathFollowedResourceCrisis);
    }
}
