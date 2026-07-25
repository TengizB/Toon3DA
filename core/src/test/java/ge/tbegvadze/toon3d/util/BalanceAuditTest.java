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

    /** R-SCARCITY-DEPTH (order 3): the scarcity ratio S holds [0.75, 0.95] at every depth 1..15. */
    @Test
    void scarcityHoldsAtEveryDepthOneToFifteen() {
        assertNoViolations(BalanceSchema.scarcityDepthResults());
    }

    /** R-HEALDRAIN-DEPTH (order 3): the per-floor net HP drain holds [5%, 15%] at every depth 1..15. */
    @Test
    void healDrainHoldsAtEveryDepthOneToFifteen() {
        assertNoViolations(BalanceSchema.healDrainDepthResults());
    }

    /** R-CREDITS (order 3): expected region income / expected purchase-bundle price stays in [0.9, 1.4]. */
    @Test
    void creditIncomeAffordsTheExpectedBundleEveryRegion() {
        assertNoViolations(BalanceSchema.creditResults());
    }

    /**
     * Order-3 NEVER-SOFTLOCK rule (GameMath.emergencySupplyTriggers). The acceptance criterion, proven
     * with SYNTHETIC inventories: the emergency ammo lifeline fires in a STARVED state and never in a
     * normal one, and honours the once-per-floor cap. eHP/damage numbers are arbitrary synthetic totals.
     */
    @Test
    void emergencySupplyFiresOnlyWhenStarvedAndOncePerFloor() {
        float fraction = BalanceConfig.EMERGENCY_SUPPLY_FRACTION; // 0.25
        float remainingDemand = 400f;                             // 400 eHP left on the floor
        float threshold = GameMath.emergencySupplyDemandThreshold(remainingDemand, fraction); // 100

        // STARVED: total potential damage (reserves*efficiency + melee) is below the threshold — fires.
        assertTrue(GameMath.emergencySupplyTriggers(60f, remainingDemand, fraction, false),
                "a starved player (60 potential dmg < 100 threshold) must trigger the lifeline");

        // NORMAL / hoarder: comfortably above the threshold — never fires (far below the scarcity band).
        assertTrue(!GameMath.emergencySupplyTriggers(500f, remainingDemand, fraction, false),
                "a well-supplied player (500 potential dmg) must never trigger the lifeline");
        assertTrue(!GameMath.emergencySupplyTriggers(threshold + 1f, remainingDemand, fraction, false),
                "just above the threshold must not trigger");

        // ONCE PER FLOOR: even while starved, a second trigger is suppressed after the first grant.
        assertTrue(!GameMath.emergencySupplyTriggers(10f, remainingDemand, fraction, true),
                "the lifeline is capped once per floor — a second starved kill must not re-trigger");

        // CLEARED FLOOR: nothing left to fight -> no lifeline regardless of reserves.
        assertTrue(!GameMath.emergencySupplyTriggers(0f, 0f, fraction, false),
                "an empty floor (no remaining demand) never triggers the lifeline");
    }

    /** R-XP-PACE (order 4): every floor awards [1.0, 1.3] level-ups worth of XP at depths 1..15. */
    @Test
    void xpPacingHoldsAtEveryDepthOneToFifteen() {
        assertNoViolations(BalanceSchema.xpPaceResults());
    }

    /** R-CARD-BREAKPOINT (order 4): every level-up card crosses >= 1 combat breakpoint in some region. */
    @Test
    void everyUpgradeCardCrossesABreakpoint() {
        assertNoViolations(BalanceSchema.cardBreakpointResults());
    }

    /**
     * R-REGION (order 5): the region danger dial is bounded and monotonic, the lethal regions (C/D)
     * out-dial region A measurably, and the per-fight depth-coupling holds in every region lane.
     */
    @Test
    void regionDangerDialIsBoundedMonotonicAndFair() {
        assertNoViolations(BalanceSchema.regionResults());
    }

    /**
     * Order-5 COVERAGE (special verbs): every catalogued enemy SPECIAL verb has a registered
     * cycle-averaged Threat-Point equivalence (or an explicit exempt classification) — a new verb without
     * one is an unpriced-content COVERAGE violation, exactly like an unpriced weapon or ammo type.
     */
    @Test
    void everySpecialVerbHasARegisteredEquivalence() {
        java.util.List<String> unpriced = new java.util.ArrayList<>();
        for (ge.tbegvadze.toon3d.enemy.SpecialAbility ability
                : ge.tbegvadze.toon3d.enemy.SpecialAbility.values()) {
            if (BalanceSchema.specialVerbPricing(ability) == null) {
                unpriced.add("  " + ability.name() + " has no SpecialVerbPricing registered");
            }
        }
        assertTrue(unpriced.isEmpty(),
                () -> "Unpriced enemy special verbs (register a SpecialVerbPricing equivalence):\n"
                        + String.join("\n", unpriced));
    }

    /**
     * Order-5 PACK COHERENCE acceptance criterion: chaff ALWAYS spawns in packs of >= CHAFF_PACK_MIN.
     * Proven over 100 seeds x depths 1..15 by planning the encounter roster and asserting no CHAFF-role
     * type ever appears alone (the golden-band chaff exemption assumes packs — the spawner guarantees it).
     */
    @Test
    void chaffAlwaysSpawnsInPacksOverAHundredSeeds() {
        java.util.List<String> lonePacks = new java.util.ArrayList<>();
        for (long seed = 0; seed < 100; seed++) {
            for (int depth = 1; depth <= 15; depth++) {
                ge.tbegvadze.toon3d.level.EncounterBudgetPlanner.Plan plan =
                        new ge.tbegvadze.toon3d.level.EncounterBudgetPlanner(
                                depth, new java.util.Random(seed * 97L + depth)).plan();
                java.util.EnumMap<ge.tbegvadze.toon3d.enemy.EnemyType, Integer> counts =
                        new java.util.EnumMap<>(ge.tbegvadze.toon3d.enemy.EnemyType.class);
                for (ge.tbegvadze.toon3d.enemy.EnemyType type : plan.enemies()) {
                    counts.merge(type, 1, Integer::sum);
                }
                for (java.util.Map.Entry<ge.tbegvadze.toon3d.enemy.EnemyType, Integer> entry
                        : counts.entrySet()) {
                    if (entry.getKey().role() == ge.tbegvadze.toon3d.enemy.EnemyRole.CHAFF
                            && entry.getValue() < BalanceConfig.CHAFF_PACK_MIN) {
                        lonePacks.add(String.format("  seed=%d depth=%d %s count=%d (< %d)",
                                seed, depth, entry.getKey().displayName(), entry.getValue(),
                                BalanceConfig.CHAFF_PACK_MIN));
                    }
                }
            }
        }
        assertTrue(lonePacks.isEmpty(),
                () -> "Chaff spawned below the minimum pack size:\n" + String.join("\n", lonePacks));
    }

    /**
     * Order-5 acceptance criterion (enemy eHP path): every archetype's eHP runs through the shared
     * GameMath.effectiveHitPoints primitive via EnemyType.effectiveHitPoints() — no raw-HP shortcut. With
     * today's all-zero mitigation the result still equals raw maxHealth, but through the one formula.
     */
    @Test
    void enemyEffectiveHitPointsRunThroughTheSharedPrimitive() {
        for (ge.tbegvadze.toon3d.enemy.EnemyType type
                : ge.tbegvadze.toon3d.enemy.EnemyType.values()) {
            float viaPrimitive = GameMath.enemyEffectiveHitPoints(type.maxHealth(), type.armorPool(),
                    type.dodgeChance(), type.flatReduction(), BalanceConfig.REFERENCE_PLAYER_DPT);
            assertTrue(Math.abs(type.effectiveHitPoints() - viaPrimitive) < 1e-3f,
                    () -> type.displayName() + " eHP must come from GameMath.enemyEffectiveHitPoints");
        }
    }

    /**
     * Order-4 CATCH-UP rule (GameMath.catchUpScaledXp), the acceptance criterion proven with SYNTHETIC
     * level/depth states: the forward-only rubber band engages when the player is more than one level
     * BELOW the expected level for their depth, and NEVER when at or above it.
     */
    @Test
    void catchUpEngagesOnlyWhenUnderLevelled() {
        float multiplier = BalanceConfig.XP_CATCHUP_MULTIPLIER; // 1.5
        int baseXp = 100;
        // expectedLevelAtDepth(1.0, 5) == 5. A level-2 player at depth 5 is 3 levels behind -> boosted.
        int expectedAtDepth5 = GameMath.expectedLevelAtDepth(BalanceConfig.EXPECTED_LEVELS_PER_DEPTH, 5);
        assertTrue(expectedAtDepth5 == 5, "expected level at depth 5 should be 5 at 1 level/floor");

        // UNDER-LEVELLED (more than one level behind) -> scaled up.
        assertTrue(GameMath.catchUpScaledXp(baseXp, 2, expectedAtDepth5, multiplier) == Math.round(baseXp * multiplier),
                "a player 3 levels behind must receive catch-up-boosted XP");
        assertTrue(GameMath.catchUpScaledXp(baseXp, 3, expectedAtDepth5, multiplier) == Math.round(baseXp * multiplier),
                "a player 2 levels behind (playerLevel < expected-1) must be boosted");

        // AT OR ABOVE (within the one-level slack, exactly at, or ahead) -> untouched.
        assertTrue(GameMath.catchUpScaledXp(baseXp, 4, expectedAtDepth5, multiplier) == baseXp,
                "a player exactly one level behind (within slack) must NOT be boosted");
        assertTrue(GameMath.catchUpScaledXp(baseXp, 5, expectedAtDepth5, multiplier) == baseXp,
                "a player at the expected level must NOT be boosted");
        assertTrue(GameMath.catchUpScaledXp(baseXp, 8, expectedAtDepth5, multiplier) == baseXp,
                "an AHEAD player must never be slowed — the band is forward-only");
        // Depth 1: expected level 1, so no player can be under-levelled -> never boosted.
        int expectedAtDepth1 = GameMath.expectedLevelAtDepth(BalanceConfig.EXPECTED_LEVELS_PER_DEPTH, 1);
        assertTrue(GameMath.catchUpScaledXp(baseXp, 1, expectedAtDepth1, multiplier) == baseXp,
                "at depth 1 the starting player is on-curve and never boosted");
    }

    /**
     * R-BOSS-GATE (order 6): with the DEPTH-1 starting loadout, every boss is un-winnable — the start
     * weapon needs at least 2x as many turns to kill the boss as the player survives WITH maximum heals.
     * Beating a boss with the starting weapon is arithmetically impossible, not merely hard.
     */
    @Test
    void startingLoadoutCannotWinAnyBossFight() {
        assertNoViolations(BalanceSchema.bossGateResults());
    }

    /**
     * R-BOSS-FAIR (order 6): the EXPECTED loadout at each boss depth gets a real but winnable fight — the
     * length lands in [target, 1.5*target] turns and the survival ratio in [0.4, 0.7].
     */
    @Test
    void expectedLoadoutGetsAFairBossFight() {
        assertNoViolations(BalanceSchema.bossFairResults());
    }

    /**
     * R-BOSS-VERB-CAP (order 6): every DERIVED boss verb respects the single-hit fairness caps — no hit
     * over 35% of reference eHP, and any hit over 25% is telegraphed. Boss verbs are the likeliest
     * fairness-bypass path, so every one is checked at its derived value.
     */
    @Test
    void everyBossVerbRespectsTheSingleHitCaps() {
        assertNoViolations(BalanceSchema.bossVerbCapResults());
    }

    /**
     * R-BOSS-REWARD (order 6): every boss's credit reward is at least its modelled fight consumption times
     * the risk premium — a boss floor is a net-positive payday, never a resource loss for progressing.
     */
    @Test
    void bossRewardRefundsTheFightPlusPremium() {
        assertNoViolations(BalanceSchema.bossRewardResults());
    }

    /**
     * R-BOSS-AMMO (order 6): each boss fight's ammo demand is coverable by a full reserve at that depth plus
     * the arena's placed ammo budget — the build check tests your BUILD, not whether you arrived with full
     * pockets.
     */
    @Test
    void bossFightAmmoDemandIsCoverable() {
        assertNoViolations(BalanceSchema.bossAmmoResults());
    }

    /**
     * Order-6 acceptance criterion (zero flat boss constants): boss HP and every verb's damage are DERIVED
     * per depth by {@link BossBalance}, so a DEEPER endless-mode boss re-derives strictly MORE HP than its
     * canonical instance — proof the stats are computed from depth, not read from a flat constant.
     */
    @Test
    void bossStatsScaleWithDepthNotFlatConstants() {
        for (BossBalance.Archetype archetype : BossBalance.Archetype.values()) {
            BossStats canonical = BossBalance.statsForDepth(archetype, archetype.canonicalDepth);
            // The same archetype re-fought a full boss-rotation deeper (endless mode) must be strictly tankier.
            int deeperDepth = archetype.canonicalDepth + 3 * Constants.BOSS_FLOOR_INTERVAL;
            BossStats deeper = BossBalance.statsForDepth(archetype, deeperDepth);
            assertTrue(deeper.effectiveHitPoints > canonical.effectiveHitPoints,
                    () -> archetype.displayName + " deeper HP " + deeper.effectiveHitPoints
                            + " must exceed canonical " + canonical.effectiveHitPoints + " (derived, not flat)");
            assertTrue(deeper.xpReward > canonical.xpReward,
                    () -> archetype.displayName + " deeper XP must exceed canonical (reward scales with depth)");
        }
    }

    /**
     * R-ROUTE-PRICED (order 7): every route node type, ELITE affix and MYSTERY outcome carries a
     * NodeEconomics row. The map can never again gain content the balance contract cannot see.
     */
    @Test
    void everyRouteNodeAffixAndOutcomeIsPriced() {
        assertNoViolations(BalanceSchema.routePricedResults());
    }

    /**
     * R-RISK-PREMIUM (order 7): the ELITE node and every affixed variant pay a reward premium in
     * [1.0, 1.2] of their threat premium — danger pays, never free, never a sucker bet.
     */
    @Test
    void dangerNodesPayARewardPremiumForTheirThreat() {
        assertNoViolations(BalanceSchema.riskPremiumResults());
    }

    /** R-CALM-COST (order 7): every calm node's EV sits below a combat node's by the banded discount. */
    @Test
    void calmNodesCostTempoAndLoot() {
        assertNoViolations(BalanceSchema.calmCostResults());
    }

    /**
     * R-MYSTERY-EV (order 7): the weighted mystery table really is worth about a combat floor
     * (±15%), and the worst pull stays "bad but survivable" as arithmetic rather than adjective.
     */
    @Test
    void theMysteryTableIsWorthAboutACombatFloor() {
        assertNoViolations(BalanceSchema.mysteryExpectedValueResults());
    }

    /** R-PIPS-DERIVED (order 7): displayed risk pips equal the tier DERIVED from priced threat. */
    @Test
    void riskPipsMatchTheDerivedDangerTier() {
        assertNoViolations(BalanceSchema.derivedPipResults());
    }

    /** R-HONEST-SAFE (order 7): a safe-looking node IS safe, and scan tones partition by price. */
    @Test
    void safeLookingNodesAreSafeAndScanTonesAreHonest() {
        assertNoViolations(BalanceSchema.honestSafeResults());
    }

    /**
     * R-TRAJECTORY (order 7): the JOURNEY is the audited unit. All three deterministic path policies
     * (SAFEST / DEADLIEST / BALANCED) walked through 100 real generated maps stay inside the route
     * bands at every region boundary — the band ENDS are the game's real difficulty range.
     */
    @Test
    void everyRoutePolicyStaysInBandOverAHundredSeeds() {
        assertNoViolations(BalanceSchema.trajectoryResults());
    }

    /**
     * R-ROUTE-GUARANTEES (order 7): over the same seed sweep, no lane strands a run — an upgrade and
     * a calm node stay reachable inside every region, the pre-boss layer offers reachable
     * provisioning, and every selectable layer offers at least two distinct node types.
     */
    @Test
    void noLaneStrandsARunOverAHundredSeeds() {
        assertNoViolations(BalanceSchema.routeGuaranteeResults());
    }

    /**
     * Order-7 acceptance criterion (the printed difficulty RANGE): the SAFEST and DEADLIEST policies
     * must actually differ — if every policy walked the same numbers the route map would not be a
     * difficulty planner at all. Proven on the two quantities the player feels: the resources banked
     * (cumulative scarcity) and the levels earned (XP pace).
     */
    @Test
    void safestAndDeadliestRoutesSpanARealDifficultyRange() {
        ge.tbegvadze.toon3d.route.NodeEconomicsRegistry ledger = BalanceSchema.routeLedger();
        ge.tbegvadze.toon3d.route.RouteEconomicsModel.ModelFloor floor = BalanceSchema.routeModelFloor();
        float safestScarcity = 0f, deadliestScarcity = 0f, safestPace = 0f, deadliestPace = 0f;
        int samples = 0;
        for (long seed = 0; seed < 25; seed++) {
            ge.tbegvadze.toon3d.route.RouteMapGenerator generator =
                    new ge.tbegvadze.toon3d.route.RouteMapGenerator(
                            ge.tbegvadze.toon3d.route.RouteRegistries.nodeTypes(),
                            ge.tbegvadze.toon3d.route.RouteRegistries.generators());
            generator.setEliteAffixPool(ge.tbegvadze.toon3d.route.RouteRegistries.affixes().elitePool());
            ge.tbegvadze.toon3d.route.RouteMap map = generator.generate(seed,
                    ge.tbegvadze.toon3d.route.RegionPlan.defaultPlan());
            java.util.List<ge.tbegvadze.toon3d.route.RouteEconomicsModel.TrajectorySample> safest =
                    ge.tbegvadze.toon3d.route.RouteEconomicsModel.walk(map,
                            ge.tbegvadze.toon3d.route.RouteEconomicsModel.PathPolicy.SAFEST, ledger, floor, 15);
            java.util.List<ge.tbegvadze.toon3d.route.RouteEconomicsModel.TrajectorySample> deadliest =
                    ge.tbegvadze.toon3d.route.RouteEconomicsModel.walk(map,
                            ge.tbegvadze.toon3d.route.RouteEconomicsModel.PathPolicy.DEADLIEST, ledger, floor, 15);
            int paired = Math.min(safest.size(), deadliest.size());
            for (int index = 0; index < paired; index++) {
                safestScarcity    += safest.get(index).scarcityRatio;
                deadliestScarcity += deadliest.get(index).scarcityRatio;
                safestPace        += safest.get(index).experiencePace;
                deadliestPace     += deadliest.get(index).experiencePace;
                samples++;
            }
        }
        assertTrue(samples > 0, "the trajectory walker must produce region-boundary samples");
        assertTrue(safestScarcity > deadliestScarcity,
                "the SAFEST route must bank more ammo than the DEADLIEST one");
        assertTrue(deadliestPace > safestPace,
                "the DEADLIEST route must earn more XP than the SAFEST one — that is what it buys");
    }

    /** The full sweep — belt-and-braces over the per-kind tests (catches rule kinds added later). */
    @Test
    void fullSchemaSweepHasNoViolations() {
        assertNoViolations(BalanceSchema.evaluate());
    }
}
