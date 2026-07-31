package ge.tbegvadze.toon3d.enemy;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import ge.tbegvadze.toon3d.util.BalanceSchema;
import ge.tbegvadze.toon3d.util.EnemyConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for the VERDANT SPIRESOWER's archetype pricing and identity
 * (.claude/agents/ideas/elemental-golem-verdant-spiresower.txt).
 *
 * <p>The sow/heal AI itself (computeSowerPlan / executeSow / the regen tick) runs against the real
 * SpireManager and is exercised end-to-end by the balance simulation gate, which plays whole runs; the
 * SpireManager's tile bookkeeping and the non-optional reachability guard are pinned by
 * {@code level.SpireReachabilityTest}. What is pinned HERE is the promise a future refactor could silently
 * break: the regeneration is priced as an ARMOUR POOL, and that pricing keeps the archetype an in-band
 * SOLDIER so the encounter budget spends it correctly and the balance audit stays green.
 *
 * <p>Pure JVM — {@link EnemyType} carries no LibGDX state, so this runs in CI beside the balance audit.
 */
class SpiresowerTest {

    /** The archetype is a GOLEM-family melee SOLDIER, spawned from '}'. */
    @Test
    void isAGolemMeleeSoldierSpawnedFromBrace() {
        assertEquals(EnemyFamily.GOLEM, EnemyType.VERDANT_SPIRESOWER.family());
        assertEquals(EnemyRole.SOLDIER, EnemyType.VERDANT_SPIRESOWER.role());
        assertEquals(false, EnemyType.VERDANT_SPIRESOWER.isRanged(),
                "the Spiresower fights in melee up close; its spires are terrain, not a ranged attack");
        assertEquals('}', EnemyType.VERDANT_SPIRESOWER.spawnChar());
        assertEquals(1, EnemyType.VERDANT_SPIRESOWER.minSpawnDepth(),
                "one of the two golems present from depth 1 — its mechanic is legible immediately");
    }

    /**
     * The regeneration is priced as an ARMOUR POOL fed to eHP — NOT a bespoke Threat-Point term. The pool
     * is the whole reason the sower survives its priced ~4.8 turns; wiring it to 0 would misprice the floor.
     */
    @Test
    void regenerationIsPricedAsAnArmourPool() {
        assertEquals(EnemyConstants.SPIRESOWER_REGEN_ARMOR_POOL,
                EnemyType.VERDANT_SPIRESOWER.armorPool(), 0.0001f,
                "the armour pool IS the priced regeneration (2 HP/turn * ~2 spires * ~5 turns = 20)");
        // eHP = raw HP + armour pool, since the sower has no dodge or flat reduction.
        assertEquals(EnemyType.VERDANT_SPIRESOWER.maxHealth() + EnemyConstants.SPIRESOWER_REGEN_ARMOR_POOL,
                EnemyType.VERDANT_SPIRESOWER.effectiveHitPoints(), 0.5f,
                "the regen pool flows straight into effective HP");
    }

    /** No SpecialAbility verb is introduced (sowing is a bespoke WIND_UP hook), so the move-set stays empty. */
    @Test
    void introducesNoSpecialAbilityVerb() {
        assertEquals(0, EnemyType.VERDANT_SPIRESOWER.moveSet().length,
                "sowing is a bespoke WIND_UP -> sow hook, not a catalogued SpecialAbility — COVERAGE stays green");
    }

    /** Threat Points and golden ratio sit in the SOLDIER bands (the balance audit's rules). */
    @Test
    void pricesIntoTheSoldierBands() {
        float threatPoints = EnemyType.VERDANT_SPIRESOWER.baseThreatPoints();
        float[] tpBand = BalanceSchema.threatPointBand(EnemyRole.SOLDIER);
        assertNotNull(tpBand);
        assertTrue(threatPoints >= tpBand[0] && threatPoints <= tpBand[1],
                "Spiresower TP " + threatPoints + " must sit in the SOLDIER band " + Arrays.toString(tpBand));

        float goldenRatio = BalanceSchema.goldenRatioOf(EnemyType.VERDANT_SPIRESOWER);
        float[] grBand = BalanceSchema.goldenRatioBand(EnemyRole.SOLDIER);
        assertNotNull(grBand);
        assertTrue(goldenRatio >= grBand[0] && goldenRatio <= grBand[1],
                "Spiresower golden ratio " + goldenRatio + " must sit in the SOLDIER band "
                        + Arrays.toString(grBand));
    }
}
