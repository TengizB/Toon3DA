package ge.tbegvadze.toon3d.enemy;

import org.junit.jupiter.api.Test;

import java.util.Random;

import ge.tbegvadze.toon3d.level.EncounterBudgetPlanner;
import ge.tbegvadze.toon3d.util.EnemyConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for the CINDERFORGE COLOSSUS's cooling-crust and molten-trail economy
 * (.claude/agents/ideas/elemental-golem-cinderforge-colossus.txt).
 *
 * <p>The archetype's whole design rests on a handful of promises, each one a rule a future refactor
 * could silently break, so each one is pinned here:
 *   1. It hardens by one stack per uninterrupted enemy turn, capped, and each stack is real flat armour.
 *   2. ANY damage — weapon OR status DoT — shatters the WHOLE shell at once; a DoT tick counts, or a
 *      Burning/Bleed build could never express the counterplay.
 *   3. Breaking a FULLY cooled shell is amplified; breaking a partial one is not.
 *   4. The shell protects the very blow that breaks it (mitigation runs before the shatter).
 *   5. Its per-Colossus live-trail-tile cap is honoured, so a long chase cannot carpet a floor.
 *
 * <p>Pure JVM — {@link Enemy} and {@link EnemyType} carry no LibGDX state, so this runs in CI beside the
 * balance audit.
 */
class CinderforgeColossusTest {

    private static Enemy newColossus() {
        return new Enemy(EnemyType.CINDERFORGE_COLOSSUS, 5, 5);
    }

    /** PROMISE 1 — a fresh Colossus enters BARE (no free opening armour), then cools one stack per turn. */
    @Test
    void spawnsBareAndCoolsOneStackPerUninterruptedTurn() {
        Enemy colossus = newColossus();
        assertEquals(0, colossus.crustStacks, "a Colossus must enter the fight bare and soft");
        assertEquals(0, colossus.armor, "no crust means no armour");

        assertTrue(colossus.advanceCrustCooling(), "an untouched turn must add a stack");
        assertEquals(1, colossus.crustStacks);
        assertEquals(EnemyConstants.CINDERFORGE_CRUST_ARMOR_PER_STACK, colossus.armor,
                "each stack is real flat armour");

        colossus.advanceCrustCooling();
        colossus.advanceCrustCooling();
        assertEquals(EnemyConstants.CINDERFORGE_CRUST_MAX_STACKS, colossus.crustStacks);
        assertFalse(colossus.advanceCrustCooling(), "cooling must stop at a full shell, never overfill it");
        assertEquals(EnemyConstants.CINDERFORGE_CRUST_MAX_STACKS
                        * EnemyConstants.CINDERFORGE_CRUST_ARMOR_PER_STACK, colossus.armor);
    }

    /** PROMISE 1 (cont.) — a turn in which it was hurt grows NO stack; hesitation is the only fuel. */
    @Test
    void aTurnInWhichItTookDamageGrowsNoStack() {
        Enemy colossus = newColossus();
        colossus.advanceCrustCooling();                 // 1 stack
        colossus.applyDamage(10);                        // hit it — flag set, shell shattered
        assertEquals(0, colossus.crustStacks, "any hit shatters the whole shell");
        assertFalse(colossus.advanceCrustCooling(),
                "the turn it was hurt must grow nothing — it re-hardens from bare");
        assertEquals(0, colossus.crustStacks);
    }

    /** PROMISE 2 — status DoT still resets the crust, or a burn build cannot express the counterplay. */
    @Test
    void damageOverTimeStillShattersTheCrust() {
        Enemy colossus = newColossus();
        colossus.advanceCrustCooling();
        colossus.advanceCrustCooling();                 // 2 stacks
        int fullHealth = colossus.health;

        colossus.applyDoTDamage(6);                      // a burn tick — bypasses armour, NOT the reset
        assertEquals(0, colossus.crustStacks, "a DoT tick must shatter the crust like any other damage");
        assertEquals(0, colossus.armor, "the shattered shell removes its armour contribution");
        assertEquals(fullHealth - 6, colossus.health, "the DoT still bites straight into HP");
        assertFalse(colossus.advanceCrustCooling(), "the DoT turn counts as damage — no stack forms");
    }

    /** PROMISE 3 — a FULL-shell shatter is amplified; a partial-shell break is not. */
    @Test
    void onlyAFullShellPaysTheShatterBonus() {
        Enemy fullShell = newColossus();
        for (int stack = 0; stack < EnemyConstants.CINDERFORGE_CRUST_MAX_STACKS; stack++) {
            fullShell.advanceCrustCooling();
        }
        assertTrue(fullShell.hasFullCrust());
        int expectedFull = Math.round(50 * EnemyConstants.CINDERFORGE_CRUST_SHATTER_MULTIPLIER);
        assertEquals(expectedFull, fullShell.crustShatterAmplified(50),
                "breaking a full shell amplifies the hit");

        Enemy partialShell = newColossus();
        partialShell.advanceCrustCooling();              // 1 stack, not full
        assertFalse(partialShell.hasFullCrust());
        assertEquals(50, partialShell.crustShatterAmplified(50),
                "chipping a partial shell is NOT amplified");
    }

    /** PROMISE 4 — the shell reduces the very hit that breaks it (mitigation runs before the shatter). */
    @Test
    void theCrustProtectsTheBlowThatBreaksIt() {
        Enemy colossus = newColossus();
        colossus.advanceCrustCooling();                  // 1 stack -> ARMOR_PER_STACK flat armour
        int fullHealth = colossus.health;

        // A modest hit: it is NOT a full shell, so no bonus; the one stack of armour still reduces it,
        // then the shell shatters. HP taken = hit - one stack of flat armour.
        colossus.applyDamage(10);
        int expectedDamage = 10 - EnemyConstants.CINDERFORGE_CRUST_ARMOR_PER_STACK;
        assertEquals(fullHealth - expectedDamage, colossus.health,
                "the crust must reduce the blow before it shatters");
        assertEquals(0, colossus.crustStacks, "and then it is gone");
    }

    /** A bare Colossus (no shell) takes full damage, exactly like any plain enemy. */
    @Test
    void aBareColossusTakesFullDamage() {
        Enemy colossus = newColossus();
        int fullHealth = colossus.health;
        colossus.applyDamage(25);
        assertEquals(fullHealth - 25, colossus.health);
    }

    /** PROMISE 5 — the per-Colossus live-trail cap is honoured; the oldest tiles expire first. */
    @Test
    void trailTilesRespectTheLiveCapAndExpire() {
        Enemy colossus = newColossus();
        int cap = EnemyConstants.CINDERFORGE_TRAIL_MAX_LIVE_TILES;
        for (int tile = 0; tile < cap; tile++) {
            assertTrue(colossus.canLightTrailTile(), "under the cap the Colossus may light another tile");
            colossus.recordTrailTile(EnemyConstants.CINDERFORGE_TRAIL_FIRE_TURNS);
        }
        assertEquals(cap, colossus.liveTrailTileCount());
        assertFalse(colossus.canLightTrailTile(), "at the cap it may not light another tile");

        // Age the oldest tiles out: after their full lifetime elapses, the live count returns to zero
        // and the Colossus may light fresh trail again.
        for (int turn = 0; turn < EnemyConstants.CINDERFORGE_TRAIL_FIRE_TURNS; turn++) {
            colossus.advanceTrailTiles();
        }
        assertEquals(0, colossus.liveTrailTileCount(), "expired trail tiles free up the cap");
        assertTrue(colossus.canLightTrailTile());
    }

    /** Every other archetype is completely untouched by the crust and trail hooks. */
    @Test
    void archetypesWithoutCrustOrTrailAreUnaffected() {
        Enemy hulk = new Enemy(EnemyType.PLAGUE_HULK, 4, 4);
        assertEquals(0, hulk.crustStacks);
        assertFalse(hulk.advanceCrustCooling(), "a crustless archetype never hardens");
        assertFalse(hulk.canLightTrailTile(), "a trailless archetype never lights fire");
        int fullHealth = hulk.health;
        hulk.applyDamage(20);
        assertEquals(fullHealth - 20, hulk.health, "damage is byte-identical to before");
    }

    /**
     * FLOOR BAND — the Colossus tests "can you keep firing", which floor 1 cannot answer, so the
     * encounter planner must not seat it as an anchor before {@code minSpawnDepth}. The gate is DATA on
     * {@link EnemyType}, applied to the ANCHOR pool as well as the fill pool (see
     * {@code EncounterBudgetPlanner.spawnableAtDepth}). This test pins the real regression risk: the gate
     * must hold BELOW the band on every seed, and the archetype must be genuinely reachable AT OR ABOVE
     * it.
     *
     * <p>Note on the reachability side: the Colossus is a BRUISER ANCHOR, chosen at most once per floor
     * as the largest anchor that fits the reserve — unlike a FILL-pool archetype (the Auric Sentinel),
     * which gets many picks per floor and so appears at exactly its band depth. Its 90-TP body does not
     * fit the anchor reserve on the tight floor-2 budget, so in practice it begins anchoring from floor
     * 3, where floors are large enough. The DATA gate opening at 2 is deliberate (the doc bands it "2+"),
     * and hand-authored levels may place '{' at depth 2 directly; the reachability scan therefore sweeps
     * a small window at and above the band rather than asserting the band's exact floor.
     */
    @Test
    void encounterPlannerHonoursTheMinimumSpawnDepth() {
        int minimumDepth = EnemyType.CINDERFORGE_COLOSSUS.minSpawnDepth();
        for (int depth = 1; depth < minimumDepth; depth++) {
            for (long seed = 1; seed <= 40; seed++) {
                assertFalse(
                        new EncounterBudgetPlanner(depth, new Random(seed)).plan().enemies()
                                .contains(EnemyType.CINDERFORGE_COLOSSUS),
                        "a Colossus must never be planned on depth " + depth);
            }
        }

        boolean appearsAtOrAboveTheBand = false;
        for (int depth = minimumDepth; depth <= minimumDepth + 2 && !appearsAtOrAboveTheBand; depth++) {
            for (long seed = 1; seed <= 200 && !appearsAtOrAboveTheBand; seed++) {
                appearsAtOrAboveTheBand = new EncounterBudgetPlanner(depth, new Random(seed))
                        .plan().enemies().contains(EnemyType.CINDERFORGE_COLOSSUS);
            }
        }
        assertTrue(appearsAtOrAboveTheBand,
                "the Colossus must actually be reachable at or above its own band depth");
    }
}
