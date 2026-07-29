package ge.tbegvadze.toon3d.enemy;

import org.junit.jupiter.api.Test;

import java.util.Random;

import ge.tbegvadze.toon3d.level.EncounterBudgetPlanner;
import ge.tbegvadze.toon3d.util.EnemyConstants;
import ge.tbegvadze.toon3d.util.GameMath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for the AURIC SENTINEL's orbiting-shard economy
 * (.claude/agents/ideas/elemental-golem-auric-sentinel.txt).
 *
 * <p>The archetype's whole design rests on four promises, and each one is a rule a future refactor
 * could silently break, so each one is pinned here:
 *   1. A shard nullifies ONE damage INSTANCE whatever its size — a railgun slug and a chaingun tick
 *      cost exactly the same. This is what makes the enemy a LOADOUT test rather than a damage sponge.
 *   2. DoT BYPASSES shards entirely — the safety valve that keeps status builds from being countered.
 *   3. Shards are ammunition as well as armour: an empty ring cannot attack at all, so the player's
 *      aggression disarms the enemy.
 *   4. The ring regrows on its own clock, capped, so a passive player faces a permanently full ring.
 *
 * <p>Pure JVM — {@link Enemy} and {@link EnemyType} carry no LibGDX state, so this runs in CI beside
 * the balance audit.
 */
class AuricSentinelShardTest {

    private static Enemy newSentinel() {
        return new Enemy(EnemyType.AURIC_SENTINEL, 5, 5);
    }

    @Test
    void spawnsWithAFullRing() {
        assertEquals(EnemyConstants.AURIC_MAX_SHARDS, newSentinel().shardCount,
                "a Sentinel must enter the fight with a full ring");
    }

    /** PROMISE 1 — absorption is per INSTANCE and total, so hit SIZE is irrelevant to a shard. */
    @Test
    void anyHitSizeCostsExactlyOneShardAndNoHealth() {
        Enemy sentinel = newSentinel();
        int fullHealth = sentinel.health;

        sentinel.applyDamage(90);   // a railgun slug
        assertEquals(EnemyConstants.AURIC_MAX_SHARDS - 1, sentinel.shardCount);
        assertEquals(fullHealth, sentinel.health, "an absorbed hit must not touch HP");

        sentinel.applyDamage(10);   // a chaingun tick — worth exactly the same to a shard
        assertEquals(EnemyConstants.AURIC_MAX_SHARDS - 2, sentinel.shardCount);
        assertEquals(fullHealth, sentinel.health);
    }

    /** PROMISE 1 (cont.) — once the ring is empty, damage lands on the glassy body in full. */
    @Test
    void damageReachesHealthOnlyOnceTheRingIsEmpty() {
        Enemy sentinel = newSentinel();
        int fullHealth = sentinel.health;
        for (int shard = 0; shard < EnemyConstants.AURIC_MAX_SHARDS; shard++) {
            sentinel.applyDamage(25);
        }
        assertEquals(0, sentinel.shardCount);
        assertEquals(fullHealth, sentinel.health, "stripping the ring must not itself deal damage");

        sentinel.applyDamage(25);
        assertEquals(fullHealth - 25, sentinel.health, "an exposed Sentinel takes full damage");
    }

    /**
     * PROMISE 1 (cont.) — the "wide/rapid weapons strip more shards" reward must FALL OUT of the
     * per-instance rule rather than being special-cased, so several separate damage calls in one
     * trigger pull (a Chaingun burst, a multi-tile march, an ability's bonus hit) strip several shards.
     */
    @Test
    void separateDamageInstancesEachStripAShard() {
        Enemy sentinel = newSentinel();
        for (int bullet = 0; bullet < 3; bullet++) {
            sentinel.applyDamage(17);   // one three-round burst, resolved as three applyDamage calls
        }
        assertEquals(0, sentinel.shardCount, "a three-hit burst must clear a three-shard ring");
    }

    /** PROMISE 2 — the balance safety valve: status damage ignores the ring completely. */
    @Test
    void damageOverTimeBypassesShardsEntirely() {
        Enemy sentinel = newSentinel();
        int fullHealth = sentinel.health;

        sentinel.applyDoTDamage(8);

        assertEquals(EnemyConstants.AURIC_MAX_SHARDS, sentinel.shardCount,
                "a burn tick must not consume a shard");
        assertEquals(fullHealth - 8, sentinel.health, "a burn tick must reach HP through the ring");
    }

    /** PROMISE 3 — firing SPENDS a shard, and an empty ring leaves the enemy unable to attack. */
    @Test
    void firingSpendsAShardAndAnEmptyRingCannotFire() {
        Enemy sentinel = newSentinel();
        for (int shot = 0; shot < EnemyConstants.AURIC_MAX_SHARDS; shot++) {
            assertTrue(sentinel.spendShardForShot(), "an armed Sentinel must be able to fire");
        }
        assertEquals(0, sentinel.shardCount);
        assertFalse(sentinel.spendShardForShot(), "a shardless Sentinel is disarmed and cannot fire");
        assertFalse(sentinel.hasShard());
    }

    /**
     * The gold flash means "your damage was nullified", so only an ABSORB may arm it — the enemy's own
     * shot must not counterfeit that signal. Both events still re-space the ring, which is a separate
     * animation. This is the archetype's most important readability rule, so it is pinned.
     */
    @Test
    void onlyAnAbsorbArmsTheGoldFlashThoughBothEventsRespaceTheRing() {
        Enemy absorbing = newSentinel();
        absorbing.applyDamage(30);
        assertTrue(absorbing.getShardAbsorbFlashStrength() > 0f,
                "an absorbed hit must flash gold");
        assertTrue(absorbing.getShardRespaceProgress() < 1f, "an absorb must re-space the ring");

        Enemy firing = newSentinel();
        firing.spendShardForShot();
        assertEquals(0f, firing.getShardAbsorbFlashStrength(), 0f,
                "firing must NOT play the 'your damage did nothing' flash");
        assertTrue(firing.getShardRespaceProgress() < 1f, "a launch must still re-space the ring");
    }

    /** PROMISE 4 — the ring returns on a fixed cadence and never exceeds the cap. */
    @Test
    void ringRegrowsOnCadenceAndStopsAtTheCap() {
        Enemy sentinel = newSentinel();
        for (int shard = 0; shard < EnemyConstants.AURIC_MAX_SHARDS; shard++) {
            sentinel.applyDamage(25);
        }
        assertEquals(0, sentinel.shardCount);

        for (int turn = 1; turn < EnemyConstants.AURIC_SHARD_REGEN_TURNS; turn++) {
            assertFalse(sentinel.advanceShardRegrowth(), "no shard before the cadence elapses");
        }
        assertTrue(sentinel.advanceShardRegrowth(), "a shard returns on the cadence turn");
        assertEquals(1, sentinel.shardCount);

        for (int turn = 0; turn < EnemyConstants.AURIC_SHARD_REGEN_TURNS * 5; turn++) {
            sentinel.advanceShardRegrowth();
        }
        assertEquals(EnemyConstants.AURIC_MAX_SHARDS, sentinel.shardCount,
                "regrowth must stop at a full ring, never overfill it");
    }

    /** Every other archetype must be completely untouched by the shard hook. */
    @Test
    void archetypesWithoutShardsAreUnaffected() {
        Enemy hulk = new Enemy(EnemyType.PLAGUE_HULK, 4, 4);
        int fullHealth = hulk.health;

        assertEquals(0, hulk.shardCount);
        hulk.applyDamage(20);
        assertEquals(fullHealth - 20, hulk.health, "a shardless archetype takes damage as before");
        assertFalse(hulk.advanceShardRegrowth(), "a shardless archetype never grows shards");
        assertEquals(0, hulk.shardCount);
    }

    /**
     * FLOOR BAND — the Sentinel tests a loadout decision the player cannot make on floor 1, so the
     * encounter planner must not field it before {@code minSpawnDepth}. The gate is DATA on
     * {@link EnemyType}, so this also guards the general mechanism.
     */
    @Test
    void encounterPlannerHonoursTheMinimumSpawnDepth() {
        int minimumDepth = EnemyType.AURIC_SENTINEL.minSpawnDepth();
        for (int depth = 1; depth < minimumDepth; depth++) {
            for (long seed = 1; seed <= 40; seed++) {
                assertFalse(
                        new EncounterBudgetPlanner(depth, new Random(seed)).plan().enemies()
                                .contains(EnemyType.AURIC_SENTINEL),
                        "a Sentinel must never be planned on depth " + depth);
            }
        }

        boolean appearsAtOrAboveTheBand = false;
        for (long seed = 1; seed <= 40 && !appearsAtOrAboveTheBand; seed++) {
            appearsAtOrAboveTheBand = new EncounterBudgetPlanner(minimumDepth, new Random(seed))
                    .plan().enemies().contains(EnemyType.AURIC_SENTINEL);
        }
        assertTrue(appearsAtOrAboveTheBand,
                "the Sentinel must actually be reachable at its own band depth");
    }

    /**
     * The orbit ring is the archetype's entire readability contract (the live count IS the visible
     * armour AND the visible ammunition), so its geometry is pinned like any other rule.
     */
    @Test
    void orbitRingGeometryIsEvenlySpacedAndDepthCued() {
        float firstAngle  = GameMath.shardOrbitAngleRadians(0f, 1f, 0, 3, 3, 1f);
        float secondAngle = GameMath.shardOrbitAngleRadians(0f, 1f, 1, 3, 3, 1f);
        assertEquals((float) (2 * Math.PI / 3), secondAngle - firstAngle, 1e-4f,
                "three shards must sit 120 degrees apart");

        // Losing a shard slides the survivors from 120 to 180 degrees rather than snapping.
        float halfwayRespaced = GameMath.shardOrbitAngleRadians(0f, 1f, 1, 2, 3, 0.5f);
        assertEquals((float) (2 * Math.PI / 3 + Math.PI) / 2f, halfwayRespaced, 1e-4f,
                "the ring must close up smoothly after an absorb");

        assertEquals(1f, GameMath.shardOrbitSizeScale((float) (Math.PI / 2), 0.35f), 1e-4f,
                "the near side of the orbit draws at nominal size");
        assertEquals(0.65f, GameMath.shardOrbitSizeScale((float) (-Math.PI / 2), 0.35f), 1e-4f,
                "the far side draws smaller, so the ring reads as an orbit and not a flat halo");
        assertTrue(GameMath.shardOrbitSizeScale((float) (-Math.PI / 2), 1f) > 0f,
                "the size scale must stay positive even at a degenerate falloff");

        assertEquals(40f, GameMath.shardOrbitOffsetX(0f, 40f), 1e-3f);
        assertEquals(-40f, GameMath.shardOrbitOffsetX((float) Math.PI, 40f), 1e-3f);
        assertEquals(0f, GameMath.shardOrbitOffsetX(1.2f, 0f), 0f);
    }
}
