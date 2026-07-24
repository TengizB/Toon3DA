package ge.tbegvadze.toon3d.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LAYER 3 of the Balance Authority — ENFORCEMENT (see {@code docs/game-balance-authority.txt}).
 *
 * <p>A plain JUnit test that runs every rule registered in {@link BalanceSchema} and FAILS THE
 * BUILD on any unwaived violation: {@code ./gradlew test} is the gatekeeper. Pure JVM — only
 * {@link BalanceConfig} + {@link GameMath} + {@link BalanceSchema} and the headless content enums,
 * no LibGDX state — so it runs in CI.
 *
 * <p>One test method per rule kind, so a failure names the exact rule family, and the assertion
 * message lists every offending subject with its value and band. When a value must deliberately
 * sit outside its band, register an explicit waiver in {@link BalanceSchema} (and mirror it in the
 * authority doc) — never weaken a band to make a test pass.
 */
class BalanceAuditTest {

    private static void assertNoViolations(List<BalanceSchema.RuleResult> results) {
        List<String> violationLines = new ArrayList<>();
        for (BalanceSchema.RuleResult result : results) {
            if (result.isViolation()) violationLines.add("  " + result);
        }
        assertTrue(violationLines.isEmpty(),
                () -> "Balance schema violations (tune the value in BalanceConfig back into band, "
                        + "or register an explicit waiver in BalanceSchema):\n"
                        + String.join("\n", violationLines));
    }

    /** R-WEAPON: every registered ranged weapon's power score lands in its declared role band. */
    @Test
    void weaponPowerScoresLandInRoleBands() {
        assertNoViolations(BalanceSchema.weaponPowerResults());
    }

    /** R-ENEMY: every non-boss archetype's threat points land in its role band. */
    @Test
    void enemyThreatPointsLandInRoleBands() {
        assertNoViolations(BalanceSchema.enemyThreatPointResults());
    }

    /** R-ENEMY: soldier/bruiser golden ratios land in band (chaff pack-exempt, mini-elite spike-exempt). */
    @Test
    void enemyGoldenRatiosLandInRoleBands() {
        assertNoViolations(BalanceSchema.enemyGoldenRatioResults());
    }

    /** R-CARD: every level-up card prices into the power-point budget band. */
    @Test
    void upgradeCardsPriceIntoTheBudgetBand() {
        assertNoViolations(BalanceSchema.cardBudgetResults());
    }

    /** R-HEAL: every heal/armour pickup buys a survival-turn count inside its size band. */
    @Test
    void healPickupsPriceIntoSurvivalTurnBands() {
        assertNoViolations(BalanceSchema.healPricingResults());
    }

    /** R-TELEGRAPH: no un-readable attack over the un-telegraphed cap; no boss hit over the hard cap. */
    @Test
    void attacksRespectTheTelegraphContract() {
        assertNoViolations(BalanceSchema.telegraphResults());
    }

    /** R-DEPTH: the depth-coupling ratio holds its band across depths 1..15. */
    @Test
    void depthCouplingHoldsThroughDepthFifteen() {
        assertNoViolations(BalanceSchema.depthCouplingResults());
    }

    /** R-SCARCITY: model-floor supply/demand, per-weapon shares, and heal net-drain all in band. */
    @Test
    void scarcityModelHoldsOnTheModelFloor() {
        assertNoViolations(BalanceSchema.scarcityResults());
    }

    /** R-DOT: exactly one definition per status; every shim field re-exports BalanceConfig exactly. */
    @Test
    void statusDefinitionsAreUniqueAndShimsDoNotDiverge() {
        assertNoViolations(BalanceSchema.dotUniquenessResults());
    }

    /** R-FLAGS: no live test/debug flag ships true. */
    @Test
    void noLiveTestOrDebugFlags() {
        assertNoViolations(BalanceSchema.flagResults());
    }

    /** COVERAGE: every weapon item, consumable, ammo type, and enemy role is classified/priced. */
    @Test
    void allContentIsCoveredByTheSchema() {
        assertNoViolations(BalanceSchema.coverageResults());
    }

    /** R-GEARGATE: the starting loadout is fair in region 1, reads underpowered by the gate, on-curve stays fair. */
    @Test
    void theGearGateExistsAndTheStartIsFair() {
        assertNoViolations(BalanceSchema.gearGateResults());
    }

    /** R-ABILITY: every ability is priced and fits the richest tier's ceiling; tier budgets are monotonic. */
    @Test
    void everyAbilityIsPricedWithinItsTierBudget() {
        assertNoViolations(BalanceSchema.abilityBudgetResults());
    }

    /**
     * R-ABILITY (the roll invariant): NO rolled weapon — at any tier, level, melee-ness, or seed —
     * carries more ability PP than its tier's ceiling. In particular a legendary never exceeds
     * {@code 30 * 1.2 = 36} (the acceptance criterion). The WeaponRoller enforces this by construction;
     * this sweep proves it empirically over a grid of rolls.
     */
    @Test
    void everyBudgetedRollStaysWithinItsTierCeiling() {
        java.util.List<String> overspends = new java.util.ArrayList<>();
        for (long seed = 0; seed < 200; seed++) {
            ge.tbegvadze.toon3d.entity.WeaponRoller roller =
                    new ge.tbegvadze.toon3d.entity.WeaponRoller(seed);
            for (ge.tbegvadze.toon3d.entity.WeaponTier tier
                    : ge.tbegvadze.toon3d.entity.WeaponTier.values()) {
                float ceiling = ge.tbegvadze.toon3d.entity.WeaponRoller.tierAbilityPowerPointBudget(tier)
                        * (1f + BalanceConfig.TIER_ABILITY_PP_TOLERANCE);
                for (boolean isMelee : new boolean[]{false, true}) {
                    for (int level = 1; level <= 10; level++) {
                        ge.tbegvadze.toon3d.entity.AbilityInstance[] abilities =
                                roller.rollAbilitySet(isMelee, tier, level);
                        float total = ge.tbegvadze.toon3d.entity.WeaponRoller
                                .totalAbilityPowerPoints(abilities);
                        if (total > ceiling + 1e-3f) {
                            overspends.add(String.format(
                                    "  seed=%d tier=%s melee=%b level=%d total=%.2f > ceiling=%.2f",
                                    seed, tier, isMelee, level, total, ceiling));
                        }
                    }
                }
            }
        }
        assertTrue(overspends.isEmpty(),
                () -> "Budgeted ability rolls exceeded their tier ceiling:\n" + String.join("\n", overspends));
    }

    /** The full sweep — belt-and-braces over the per-kind tests (catches rule kinds added later). */
    @Test
    void fullSchemaSweepHasNoViolations() {
        assertNoViolations(BalanceSchema.evaluate());
    }
}
