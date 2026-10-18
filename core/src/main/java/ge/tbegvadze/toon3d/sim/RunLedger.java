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
        /** The run hit a SHALLOWER-than-final simulator depth ceiling still alive (smoke/probe settings). */
        SURVIVED_TO_CEILING,
        /**
         * The run killed the run-final boss at BalanceConfig.RUN_FINAL_DEPTH (balance-overhaul order 6, F4):
         * the descent is won and there is no floor after it.
         */
        VICTORY,
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

    /**
     * LADDER probe samples (balance-overhaul order 1), indexed by EnemyRole ordinal: every player hit that
     * landed records the hits its size would need to kill that enemy (enemy max HP / hit damage), and every
     * attributed enemy hit taken records the hits of that size the marine's full pool survives (pool / hit).
     * Empty outside a probe run.
     */
    public final List<List<Float>> ladderHitsToKillByRole = newRoleSampleLists();
    public final List<List<Float>> ladderHitsToDieByRole  = newRoleSampleLists();

    private static List<List<Float>> newRoleSampleLists() {
        List<List<Float>> lists = new ArrayList<>();
        for (int roleIndex = 0; roleIndex < ge.tbegvadze.toon3d.enemy.EnemyRole.values().length; roleIndex++) {
            lists.add(new ArrayList<>());
        }
        return lists;
    }

    /**
     * BUILD REPORT raw counts (balance-overhaul order 5, S-BUILD), indexed by {@code Perk} / {@code Keystone}
     * ordinal: how often each was on a level-up screen, and how often the policy took it. Shop-bought perk
     * ranks are not offers and are not counted here (they still count toward {@link #ranksByTagAtEnd}).
     */
    public final int[] perkOffered     = new int[ge.tbegvadze.toon3d.progression.Perk.values().length];
    public final int[] perkPicked      = new int[ge.tbegvadze.toon3d.progression.Perk.values().length];
    public final int[] keystoneOffered = new int[ge.tbegvadze.toon3d.progression.Keystone.values().length];
    public final int[] keystonePicked  = new int[ge.tbegvadze.toon3d.progression.Keystone.values().length];

    /** Perk ranks per {@code BuildTag} ordinal when the run ended (level-up picks and shop ranks alike). */
    public final int[] ranksByTagAtEnd = new int[ge.tbegvadze.toon3d.progression.BuildTag.values().length];

    /**
     * {@code PlayerBuild.highestRankTag()} when the run ended — the S-BUILD dominant tag; null when the run
     * never held a perk rank (excluded from the dominant-tag share).
     */
    public ge.tbegvadze.toon3d.progression.BuildTag dominantTagAtEnd;

    /** Total credits spent at vending machines across the run. */
    public int creditsSpent;

    /** S-SHOP (balance-overhaul order 6, F14): Fabricator visits (one per exited non-boss floor) and offers bought. */
    public int fabricatorVisits;
    public int shopPurchases;
    /** Credits the run still held when it ended — the "unspent at death" S-SHOP reads. */
    public int creditsHeldAtEnd;

    /** Times the never-softlock emergency ammo lifeline fired. */
    public int emergencySupplyTriggers;

    /** RELIC REPORT (relics-run-changing-artifacts): the ids of the relics held when the run ended, in bond order. */
    public final List<String> relicIdsAtEnd = new ArrayList<>();

    /** The relic a S-RELIC probe row was GRANTED at depth-1 arrival, or null (every other row). */
    public String grantedProbeRelicId;

    /** Relic offers that rolled at least one card, and the ones the policy bonded, by RelicSource ordinal. */
    public final int[] relicOffersSeen  = new int[ge.tbegvadze.toon3d.relic.RelicSource.values().length];
    public final int[] relicOffersTaken = new int[ge.tbegvadze.toon3d.relic.RelicSource.values().length];

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
