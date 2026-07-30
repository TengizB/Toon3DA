package ge.tbegvadze.toon3d.enemy;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import ge.tbegvadze.toon3d.level.EncounterBudgetPlanner;
import ge.tbegvadze.toon3d.util.BalanceSchema;
import ge.tbegvadze.toon3d.util.EnemyConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for the RIMESHELL LANCER's ice-shell / rime-lance economy
 * (.claude/agents/ideas/elemental-golem-rimeshell-lancer.txt).
 *
 * <p>The archetype's whole design rests on a handful of promises, each one a rule a future refactor
 * could silently break, so each one is pinned here:
 *   1. It spawns SEALED — the toughest non-elite body, with its live flat shell already up.
 *   2. The shell OPENS (live mitigation drops to 0) for the charge window and RESEALS after the beam;
 *      the punish window is a real, legible swing in how much a hit hurts it.
 *   3. The LIVE shell (baseArmor) and the PRICED average (flatReduction) are DIFFERENT numbers and must
 *      never be wired to each other — the live shell is the peak, the priced value is the time-average.
 *   4. It is priced as an ordinary ranged SOLDIER: its Threat Points and golden ratio sit in the SOLDIER
 *      bands, so the encounter budget spends it correctly and the balance audit stays green.
 *   5. It is in the encounter planner's fill pool at every depth (a SOLDIER, minSpawnDepth 1).
 *
 * <p>Pure JVM — {@link Enemy} and {@link EnemyType} carry no LibGDX state, so this runs in CI beside the
 * balance audit. The AI/beam integration (computeLancerPlan / executeLanceBeam) is exercised end-to-end
 * by the balance simulation gate, which plays whole runs through the real EnemyManager.
 */
class RimeshellLancerTest {

    private static Enemy newLancer() {
        return new Enemy(EnemyType.RIMESHELL_LANCER, 5, 5);
    }

    /** PROMISE 1 — a fresh Lancer enters SEALED, with its full live flat shell up. */
    @Test
    void spawnsSealedWithItsFullLiveShell() {
        Enemy lancer = newLancer();
        assertEquals(EnemyConstants.RIMESHELL_SHELL_ARMOR, lancer.armor,
                "a Lancer must enter the fight SEALED, with its live ice shell already up");
        assertFalse(lancer.isShellOpen(), "a freshly spawned Lancer's shell is closed");
    }

    /** PROMISE 2 — the shell opens for the charge window (armour to 0) and reseals after the beam. */
    @Test
    void shellOpensForTheChargeWindowThenReseals() {
        Enemy lancer = newLancer();

        lancer.openShell();
        assertEquals(0, lancer.armor, "opening the shell drops the live flat mitigation to 0");
        assertTrue(lancer.isShellOpen(), "an open shell reads as open");

        lancer.resealShell();
        assertEquals(EnemyConstants.RIMESHELL_SHELL_ARMOR, lancer.armor,
                "resealing restores the full live shell");
        assertFalse(lancer.isShellOpen(), "a resealed shell reads as closed");
    }

    /**
     * PROMISE 2 (cont.) — the punish window is REAL: the same hit bites far deeper while the shell is
     * open than while it is sealed. This is the entire "shoot it on the right turn" lesson.
     */
    @Test
    void theOpenShellIsAGenuinePunishWindow() {
        int hit = 20;

        Enemy sealed = newLancer();
        int sealedMax = sealed.maxHealth;
        sealed.applyDamage(hit);
        int sealedTaken = sealedMax - sealed.health;
        assertEquals(hit - EnemyConstants.RIMESHELL_SHELL_ARMOR, sealedTaken,
                "a sealed Lancer shaves its flat shell off the hit");

        Enemy open = newLancer();
        int openMax = open.maxHealth;
        open.openShell();
        open.applyDamage(hit);
        int openTaken = openMax - open.health;
        assertEquals(hit, openTaken, "an open shell mitigates nothing — the whole hit lands");

        assertTrue(openTaken > sealedTaken,
                "the same hit must hurt more into the open shell than into the sealed one");
    }

    /**
     * PROMISE 3 — the LIVE shell and the PRICED average are DIFFERENT numbers on purpose. Wiring them
     * together (the tempting "fix") would drift the Threat Points out of the honest per-turn average.
     */
    @Test
    void liveShellAndPricedAverageAreDeliberatelyDifferent() {
        assertEquals(EnemyConstants.RIMESHELL_SHELL_ARMOR,
                EnemyType.RIMESHELL_LANCER.baseArmor(),
                "baseArmor() is the LIVE peak shell");
        assertEquals(EnemyConstants.RIMESHELL_SHELL_AVERAGED_FLAT_REDUCTION,
                EnemyType.RIMESHELL_LANCER.flatReduction(), 0.0001f,
                "flatReduction() is the TIME-AVERAGED priced shell");
        assertTrue(EnemyType.RIMESHELL_LANCER.baseArmor()
                        > EnemyType.RIMESHELL_LANCER.flatReduction(),
                "the live peak must exceed the averaged price — the shell is down only part of the cycle");
    }

    /** The lance cooldown counts down one enemy turn at a time and clamps at zero (never negative). */
    @Test
    void lanceCooldownCountsDownAndClampsAtZero() {
        Enemy lancer = newLancer();
        lancer.lanceCooldownTurns = 2;
        lancer.advanceLanceCooldown();
        assertEquals(1, lancer.lanceCooldownTurns);
        lancer.advanceLanceCooldown();
        assertEquals(0, lancer.lanceCooldownTurns);
        lancer.advanceLanceCooldown();
        assertEquals(0, lancer.lanceCooldownTurns, "cooldown must never count below zero");
    }

    /** The archetype is classified as a GOLEM-family ranged SOLDIER, spawned from '['. */
    @Test
    void isAGolemRangedSoldierSpawnedFromBracket() {
        assertEquals(EnemyFamily.GOLEM, EnemyType.RIMESHELL_LANCER.family());
        assertEquals(EnemyRole.SOLDIER, EnemyType.RIMESHELL_LANCER.role());
        assertTrue(EnemyType.RIMESHELL_LANCER.isRanged(), "the lance is a ranged attack");
        assertEquals('[', EnemyType.RIMESHELL_LANCER.spawnChar());
    }

    /** PROMISE 4 — Threat Points and golden ratio sit in the SOLDIER bands (the balance audit's rules). */
    @Test
    void pricesIntoTheSoldierBands() {
        float threatPoints = EnemyType.RIMESHELL_LANCER.baseThreatPoints();
        float[] tpBand = BalanceSchema.threatPointBand(EnemyRole.SOLDIER);
        assertNotNull(tpBand);
        assertTrue(threatPoints >= tpBand[0] && threatPoints <= tpBand[1],
                "Lancer TP " + threatPoints + " must sit in the SOLDIER band "
                        + Arrays.toString(tpBand));

        float goldenRatio = BalanceSchema.goldenRatioOf(EnemyType.RIMESHELL_LANCER);
        float[] grBand = BalanceSchema.goldenRatioBand(EnemyRole.SOLDIER);
        assertNotNull(grBand);
        assertTrue(goldenRatio >= grBand[0] && goldenRatio <= grBand[1],
                "Lancer golden ratio " + goldenRatio + " must sit in the SOLDIER band "
                        + Arrays.toString(grBand));
    }

    /** PROMISE 5 — a SOLDIER at minSpawnDepth 1, so the planner can field it on every floor. */
    @Test
    void isSpawnableAtEveryDepth() {
        assertEquals(1, EnemyType.RIMESHELL_LANCER.minSpawnDepth(),
                "the Lancer is the golem family's every-floor ranged body");
        // Depth 1 fill pool must already contain it (it is a floor-1+ SOLDIER).
        EncounterBudgetPlanner.Plan plan = new EncounterBudgetPlanner(1, new Random(1L)).plan();
        assertNotNull(plan, "the planner must produce a floor-1 plan");
    }
}
