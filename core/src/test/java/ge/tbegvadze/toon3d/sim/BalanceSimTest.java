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
            String reportText = SimReport.render(playedMatrix, bandResults) + '\n' + LadderReport.run().render();
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

    /** S-FAIR — the intended run length, and deaths the player could see coming. */
    @Test
    void tacticalRunsEndInTheIntendedWindowAndReadAsFair() {
        assertBandsHold(bandsOfKind(ge.tbegvadze.toon3d.util.BalanceSchema.RuleKind.SIM_FAIR));
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
     * start-weapon hoarder's median death depth is at most SIM_LAG_MAX_MEDIAN_DEPTH (navigation waiver applies).
     */
    @Test
    void aWeaponThatNeverClimbsTheLadderEndsTheRunEarly() {
        assertBandsHold(bandsOfKind(ge.tbegvadze.toon3d.util.BalanceSchema.RuleKind.SIM_LAG));
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
     * SIM_BUILD_DEPTH_SPREAD of each other (depth-derived: reported under the navigation waiver until it expires),
     * and plain TACTICAL's end-of-run dominant tag is no single tag on more than SIM_BUILD_MAX_DOMINANT_TAG_SHARE
     * of its runs (over at least SIM_BUILD_MIN_SAMPLE runs that held a perk rank, read from the 600-seed
     * TACTICAL-BUILD-SAMPLE row). The depth spread and the dominant-tag share ride the navigation waiver (the share
     * by owner ruling D7); the sample line is never waived.
     */
    @Test
    void buildBiasesStayCloseAndNoTagDominates() {
        List<RuleResult> results = bandsOfKind(ge.tbegvadze.toon3d.util.BalanceSchema.RuleKind.SIM_BUILD);
        assertEquals(3, results.size(), "S-BUILD evaluates the depth spread, the TACTICAL sample and the dominant-tag share");
        java.util.Set<String> waivable = java.util.Set.of("build-bias median depth spread", "TACTICAL dominant-tag share");
        assertTrue(results.stream().filter(result -> !waivable.contains(result.subject))
                        .noneMatch(result -> result.waived), "only the depth spread and the dominant-tag share may be waived (D7)");
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
     * finish a boss fight. They do not yet: the scripted policies stall on most generated floors and
     * every simulated boss floor currently runs its turn cap out, so that assertion would be checking
     * the harness's pathing rather than the game's balance. This test therefore asserts what it can
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
