package ge.tbegvadze.toon3d.sim;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.BalanceSchema.RuleResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE SLOW GATE — {@code ./gradlew balanceSim} (new-game-balancr order 9).
 *
 * <p>{@code ./gradlew test} proves the balance NUMBERS are self-consistent. This proves the game
 * PLAYS the way those numbers claim: it plays {@code SIM_SEED_COUNT} whole runs per policy through
 * the real systems and checks the six behavioural bands. It is tagged {@code balanceSim} and excluded
 * from the fast suite, because minutes of simulated play do not belong on every commit.
 *
 * <p>Bands that are currently out of band with a registered waiver still PRINT (see the report at
 * {@code build/reports/balance-sim/summary.txt}) but do not fail the build — the waiver, its reason
 * and its expiry condition live in {@code BalanceSchema} and the authority doc, never here.
 */
@Tag("balanceSim")
class BalanceSimTest {

    /** Runs the shipping matrix once and shares it across the band assertions in this class. */
    private static Map<String, PolicySummary> matrix;

    private static synchronized Map<String, PolicySummary> matrix() {
        if (matrix == null) {
            matrix = BalanceSimulator.runStandardMatrix();
            writeReport(matrix);
        }
        return matrix;
    }

    private static void writeReport(Map<String, PolicySummary> playedMatrix) {
        try {
            List<RuleResult> bandResults = BehavioralBands.evaluate(playedMatrix);
            // Balance-overhaul order 1: the LADDER REPORT (played probes at every depth) rides along as OUTPUT.
            // Balance-overhaul order 7 (A4): the ROUND SUMMARY page leads — a playtest round's analysis starts there.
            LadderReport ladder = LadderReport.run();
            String reportText = RoundSummary.render(playedMatrix, bandResults, ladder) + '\n'
                    + SimReport.render(playedMatrix, bandResults) + '\n' + ladder.render();
            Path written = SimReport.write(Path.of("").toAbsolutePath(), reportText);
            System.out.println("balance-sim report: " + written);
            System.out.println(reportText);
        } catch (java.io.IOException failure) {
            // A report is an aid, never a gate — the assertions below still run.
            System.out.println("balance-sim report could not be written: " + failure.getMessage());
        }
    }

    /** Fails on any band that is out of band AND not explicitly waived. */
    private static void assertBandsHold(List<RuleResult> results) {
        List<String> violations = new ArrayList<>();
        for (RuleResult result : results) {
            if (result.isViolation()) violations.add("  " + result);
        }
        assertTrue(violations.isEmpty(),
                () -> "Behavioural band violations (the game does not play the way the model claims):\n"
                        + String.join("\n", violations));
    }

    private static List<RuleResult> bandsOfKind(ge.tbegvadze.toon3d.util.BalanceSchema.RuleKind kind) {
        List<RuleResult> selected = new ArrayList<>();
        for (RuleResult result : BehavioralBands.evaluate(matrix())) {
            if (result.kind == kind) selected.add(result);
        }
        return selected;
    }

    /** S-GATE — the headline guarantee: the starting loadout never beats the first boss. */
    @Test
    void hoarderNeverClearsTheFirstBoss() {
        assertBandsHold(bandsOfKind(ge.tbegvadze.toon3d.util.BalanceSchema.RuleKind.SIM_GATE));
    }

    /**
     * S-FAIR — THE RUN OUTCOME of the finite 25-floor run (balance-overhaul order 6, F8: win rates, reach, loss
     * depths, the first boss; replaces the TACTICAL median-death-depth band) and deaths the player could see coming.
     * All eight run-outcome lines are reported; out-of-band ones ride the order-7 round-pending waiver until a
     * playtest round meets them (balance-overhaul order 7 deleted the navigation waiver). A2(b): no ledger ever holds a floor past the
     * run-final depth, and every VICTORY ended on it.
     */
    @Test
    void tacticalRunsEndInTheIntendedWindowAndReadAsFair() {
        List<RuleResult> results = bandsOfKind(ge.tbegvadze.toon3d.util.BalanceSchema.RuleKind.SIM_FAIR);
        java.util.Set<String> runOutcome = java.util.Set.of("TACTICAL win rate",
                "TACTICAL reaches depth " + BalanceConfig.SIM_TACTICAL_REACH_DEPTH,
                "TACTICAL median death depth (losses)", "TACTICAL first-boss clears (of seeds reaching it)",
                "NAIVE win rate", "NAIVE median death depth", "NAIVE reaches the first boss", "NAIVE first-boss clears");
        for (String subject : runOutcome) {
            assertTrue(results.stream().anyMatch(result -> result.subject.equals(subject)),
                    () -> "S-FAIR must report the F8 run-outcome line '" + subject + "'");
        }
        assertBandsHold(results);
        for (PolicySummary summary : matrix().values()) {
            for (RunLedger run : summary.runs) {
                assertTrue(run.depthReached <= BalanceConfig.RUN_FINAL_DEPTH,
                        () -> summary.policyId + " seed " + run.runSeed + " reached floor " + run.depthReached
                                + " — there is no floor past the run-final boss");
                if (run.ending == RunLedger.Ending.VICTORY) {
                    assertEquals(BalanceConfig.RUN_FINAL_DEPTH, run.endingDepth,
                            () -> summary.policyId + " seed " + run.runSeed + " won somewhere other than the final floor");
                }
            }
        }
    }

    /** S-SKILL — playing well must be worth depths. */
    @Test
    void playingWellBuysDepth() {
        assertBandsHold(bandsOfKind(ge.tbegvadze.toon3d.util.BalanceSchema.RuleKind.SIM_SKILL));
    }

    /** S-ROUTE — the priced map's drain survives contact with play. */
    @Test
    void playedTrajectoryMatchesThePricedMap() {
        assertBandsHold(bandsOfKind(ge.tbegvadze.toon3d.util.BalanceSchema.RuleKind.SIM_ROUTE));
    }

    /** S-ECONOMY — the supply a played floor yields tracks the supply its planner put down (re-based, order 2). */
    @Test
    void playedEconomyMatchesTheModelledEconomy() {
        assertBandsHold(bandsOfKind(ge.tbegvadze.toon3d.util.BalanceSchema.RuleKind.SIM_ECONOMY));
    }

    /**
     * S-SUPPLY (balance-overhaul order 2, A7) — TACTICAL leaves a COMBAT floor hurt but standing (mean exit
     * health fraction in band) and no played floor sits below its S4 heal floor.
     */
    @Test
    void combatFloorsAreLeftHurtAndNeverBelowTheHealFloor() {
        assertBandsHold(bandsOfKind(ge.tbegvadze.toon3d.util.BalanceSchema.RuleKind.SIM_SUPPLY));
    }

    /**
     * S-LAG (balance-overhaul order 1) — a run whose weapon never climbs the power ladder ends early: the
     * start-weapon hoarder's median death depth is at most SIM_LAG_MAX_MEDIAN_DEPTH. Enforced since balance-overhaul
     * order 7 (navigation fixed) and never waived.
     */
    @Test
    void aWeaponThatNeverClimbsTheLadderEndsTheRunEarly() {
        List<RuleResult> results = bandsOfKind(ge.tbegvadze.toon3d.util.BalanceSchema.RuleKind.SIM_LAG);
        assertTrue(results.stream().noneMatch(result -> result.waived), "S-LAG is no longer waived (order 7)");
        assertBandsHold(results);
    }

    /**
     * S-SWITCH (balance-overhaul order 3, A8 re-stated by D3) — the C4 SWITCH hint works: enough COMBAT
     * floors show one, TACTICAL takes it, and every hinted COMBAT floor yields a switch. Not waived.
     */
    @Test
    void tacticalSwitchesWeaponsByMatchup() {
        List<RuleResult> results = bandsOfKind(ge.tbegvadze.toon3d.util.BalanceSchema.RuleKind.SIM_SWITCH);
        assertEquals(3, results.size(), "S-SWITCH evaluates hinted floors, take rate and per-hinted-floor switches");
        assertTrue(results.stream().noneMatch(result -> result.waived), "S-SWITCH is never waived");
        assertBandsHold(results);
    }

    /**
     * S-HEAVY (balance-overhaul order 4, A8) — a player who reads the red floor answers the heavies committed at
     * it: TACTICAL avoids / guards / staggers / kills first at least SIM_HEAVY_TACTICAL_MIN_SUCCESS of them
     * (over at least SIM_HEAVY_MIN_SAMPLE), NAIVE at most SIM_HEAVY_NAIVE_MAX_SUCCESS. Not waived.
     */
    @Test
    void tacticalAnswersHeavyAttacksAndNaiveDoesNot() {
        List<RuleResult> results = bandsOfKind(ge.tbegvadze.toon3d.util.BalanceSchema.RuleKind.SIM_HEAVY);
        assertEquals(3, results.size(), "S-HEAVY evaluates the TACTICAL sample, the TACTICAL share and the NAIVE share");
        assertTrue(results.stream().noneMatch(result -> result.waived), "S-HEAVY is never waived");
        assertBandsHold(results);
    }

    /**
     * S-BUILD (balance-overhaul order 5, A6) — the four build-biased TACTICAL variants reach median depths within
     * SIM_BUILD_DEPTH_SPREAD of each other (depth-derived; enforced since balance-overhaul order 7),
     * and plain TACTICAL's end-of-run dominant tag is no single tag on more than SIM_BUILD_MAX_DOMINANT_TAG_SHARE
     * of its runs (over at least SIM_BUILD_MIN_SAMPLE runs that held a perk rank, read from the 600-seed
     * TACTICAL-BUILD-SAMPLE row). Only the dominant-tag share may be waived (the order-7 round-pending waiver,
     * a round-1 J2 question); the depth spread and the sample line are enforced.
     */
    @Test
    void buildBiasesStayCloseAndNoTagDominates() {
        List<RuleResult> results = bandsOfKind(ge.tbegvadze.toon3d.util.BalanceSchema.RuleKind.SIM_BUILD);
        assertEquals(3, results.size(), "S-BUILD evaluates the depth spread, the TACTICAL sample and the dominant-tag share");
        assertTrue(results.stream().filter(result -> !result.subject.equals("TACTICAL dominant-tag share"))
                        .noneMatch(result -> result.waived), "only the dominant-tag share may be waived (order 7)");
        assertBandsHold(results);
    }

    /**
     * S-SHOP (balance-overhaul order 6, F14) — the shop is a place to spend: TACTICAL buys at least 1.5 offers per
     * Fabricator visit (on the order-7 round-pending waiver until a round meets it) and dies holding at most 1.5 floors of income (never waived).
     */
    @Test
    void tacticalSpendsAtTheShop() {
        List<RuleResult> results = bandsOfKind(ge.tbegvadze.toon3d.util.BalanceSchema.RuleKind.SIM_SHOP);
        assertEquals(2, results.size(), "S-SHOP evaluates offers per visit and unspent credits at death");
        assertTrue(results.stream().filter(result -> !result.subject.equals("TACTICAL offers bought per Fabricator visit"))
                        .noneMatch(result -> result.waived), "only the round-pending offers line may be waived");
        assertBandsHold(results);
    }

    /**
     * S-BOSS (balance-overhaul order 6, F10) — every boss is consistent: per BossTable row, over the TACTICAL seeds
     * that reach it, a clear rate in [0.50, 0.90] (the first boss >= 0.75), a fight-length CoV <= 0.30 and a median
     * health at the kill in [0.30, 0.60]. Three lines per row, all depth-derived (order-7 round-pending waiver until a round meets them).
     */
    @Test
    void everyBossIsAConsistentFight() {
        List<RuleResult> results = bandsOfKind(ge.tbegvadze.toon3d.util.BalanceSchema.RuleKind.SIM_BOSS);
        assertEquals(3 * BalanceConfig.BOSS_TABLE_DEPTHS.length, results.size(),
                "S-BOSS reports clear rate, fight-turns CoV and health at the kill for every BossTable row");
        assertBandsHold(results);
    }

    /**
     * S-NAVIGATION (balance-overhaul order 7, P8 / A1) — every policy in the matrix finishes the floors it plays:
     * at most SIM_MAX_STALLED_FLOOR_FRACTION of its played floors may run the turn cap out. One line per policy,
     * never waived — every depth-derived band stands on it.
     */
    @Test
    void scriptedPlayersFinishTheFloorsTheyPlay() {
        List<RuleResult> results = bandsOfKind(ge.tbegvadze.toon3d.util.BalanceSchema.RuleKind.SIM_NAVIGATION);
        assertEquals(matrix().size(), results.size(), "S-NAVIGATION reports one stalled-floor line per policy");
        assertTrue(results.stream().noneMatch(result -> result.waived), "S-NAVIGATION is never waived");
        assertBandsHold(results);
    }

    /**
     * S-RELIC (relics-run-changing-artifacts, CP8) — no relic is a trap and none is mandatory: the COMMON / RARE /
     * LEGENDARY probe rows (SR1) and the CURSED row (SR2) sit inside their mean-depth bands over the NO-RELIC
     * baseline, LEGENDARY is felt over COMMON (SR3), every sampled non-affine relic is inside its own band (SR4),
     * every non-affine relic was sampled (SR5), and standard TACTICAL actually holds relics (SR6).
     */
    @Test
    void noRelicIsATrapAndNoneIsMandatory() {
        List<RuleResult> results = bandsOfKind(ge.tbegvadze.toon3d.util.BalanceSchema.RuleKind.SIM_RELIC);
        for (String prefix : List.of("SR1 ", "SR2 ", "SR3 ", "SR4 ", "SR5 ", "SR6 ")) {
            assertTrue(results.stream().anyMatch(result -> result.subject.startsWith(prefix)),
                    () -> "S-RELIC must report its " + prefix.trim() + " line");
        }
        assertEquals(3, results.stream().filter(result -> result.subject.startsWith("SR1 ")).count(),
                "SR1 reads the COMMON, RARE and LEGENDARY probe rows");
        assertBandsHold(results);
    }

    /**
     * S-TWIST (floor-twists, CP8) — no floor twist is a death trap: every twist reports its ST1 death-rate line
     * (n/a while under-sampled) and its ST2 "it was dealt" line, none is waived, and all of them hold.
     */
    @Test
    void noFloorTwistIsADeathTrap() {
        List<RuleResult> results = bandsOfKind(ge.tbegvadze.toon3d.util.BalanceSchema.RuleKind.SIM_TWIST);
        int twistCount = BehavioralBands.twistIds().size();
        assertEquals(twistCount, results.stream().filter(result -> result.subject.startsWith("ST1 ")).count(),
                "S-TWIST reports one ST1 line per floor twist");
        assertEquals(twistCount, results.stream().filter(result -> result.subject.startsWith("ST2 ")).count(),
                "S-TWIST reports one ST2 line per floor twist");
        assertTrue(results.stream().noneMatch(result -> result.waived), "S-TWIST is never waived");
        assertBandsHold(results);
    }

    /** S-SOFTLOCK — a run may end, but never get stuck unable to damage anything. */
    @Test
    void noRunEndsUnableToDamageAnything() {
        assertBandsHold(bandsOfKind(ge.tbegvadze.toon3d.util.BalanceSchema.RuleKind.SIM_SOFTLOCK));
    }

    /**
     * THE SABOTAGE CHECK (order-9 acceptance criterion): the S-GATE metric must MOVE when the thing
     * the gate exists to catch is deliberately broken. A gate whose input never responds to a
     * balance change proves nothing, so this quadruples the starting loadout's damage — the shape of
     * the original boss-cheese bug — and asserts that the shipping loadout is at zero clears while
     * the sabotaged one measurably out-fights it.
     *
     * <p>The stronger claim ("S-GATE turns RED under sabotage") needs the sabotaged runs to actually
     * clear a boss. Since balance-overhaul order 7 the policies finish their floors and fight the boss, but
     * no policy clears the first boss yet (the round-pending S-BOSS state), so that assertion would still
     * be checking an out-of-band game rather than the gate. This test therefore asserts what it can
     * honestly measure today and reports the rest; see the CHANGE PROTOCOL notes in
     * docs/game-balance-authority.txt.
     */
    @Test
    void sabotagingTheStartingLoadoutMovesTheGateMetric() {
        SimSettings sabotaged = SimSettings.standard()
                .withStartingWeaponDamageMultiplier(SABOTAGE_DAMAGE_MULTIPLIER);
        PolicySummary honest = BalanceSimulator.runPolicy(BalanceSimulator.hoarderPolicy(),
                SABOTAGE_SEED_COUNT, BalanceSimulator.DEFAULT_SEED_OFFSET, SimSettings.standard());
        PolicySummary cheated = BalanceSimulator.runPolicy(BalanceSimulator.hoarderPolicy(),
                SABOTAGE_SEED_COUNT, BalanceSimulator.DEFAULT_SEED_OFFSET, sabotaged);

        assertEquals(0f, honest.clearedFirstBossFraction(), 0f,
                "the shipping starting loadout must never clear the first boss");
        assertTrue(cheated.meanKillsPerRun() > honest.meanKillsPerRun(),
                () -> "the gate's input must respond to weapon power: honest "
                        + honest.meanKillsPerRun() + " kills/run vs sabotaged "
                        + cheated.meanKillsPerRun());
        assertTrue(cheated.clearedFirstBossFraction() >= honest.clearedFirstBossFraction(),
                "sabotage may only ever make the gate metric worse, never better");
    }

    /** DETERMINISM: the same seed and policy must replay the same run, byte for byte. */
    @Test
    void theSameSeedReplaysTheSameRun() {
        long seed = BalanceSimulator.DEFAULT_SEED_OFFSET + 7;
        RunLedger first  = new SimWorld(seed, new TacticalPolicy(seed), SimSettings.standard()).play();
        RunLedger second = new SimWorld(seed, new TacticalPolicy(seed), SimSettings.standard()).play();
        assertEquals(first.summaryLine(), second.summaryLine(),
                "same seed + same policy must produce the same run");
        assertEquals(first.floors.size(), second.floors.size());
        for (int floorIndex = 0; floorIndex < first.floors.size(); floorIndex++) {
            FloorLedger firstFloor  = first.floors.get(floorIndex);
            FloorLedger secondFloor = second.floors.get(floorIndex);
            assertEquals(firstFloor.turnsSpent,  secondFloor.turnsSpent);
            assertEquals(firstFloor.healthLost,  secondFloor.healthLost);
            assertEquals(firstFloor.ammoSpent,   secondFloor.ammoSpent);
            assertEquals(firstFloor.enemiesKilled, secondFloor.enemiesKilled);
        }
    }

    /** Damage multiplier applied to the starting loadout by the sabotage test. */
    private static final float SABOTAGE_DAMAGE_MULTIPLIER = 4.0f;
    /** Seeds used by the sabotage test — enough to show the gate reacting, not a distribution. */
    private static final int   SABOTAGE_SEED_COUNT = Math.max(20, BalanceConfig.SIM_SEED_COUNT / 10);
}
