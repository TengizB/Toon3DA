package ge.tbegvadze.toon3d.util;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /**
     * Asserts an ADMISSION rule found content and admitted all of it. An ADMISSION failure is a missing declaration
     * or pricing row, never a band verdict, so there is no waiver to reach for.
     */
    private static void assertAdmitted(List<BalanceSchema.RuleResult> results) {
        assertFalse(results.isEmpty(), "an admission rule that finds no content is itself broken");
        List<String> violationLines = new ArrayList<>();
        for (BalanceSchema.RuleResult result : results) {
            if (result.isViolation()) violationLines.add("  " + result);
        }
        assertTrue(violationLines.isEmpty(),
                () -> "ADMISSION failures are missing declarations or pricing rows and can never be waived (G5) "
                        + "— add the declaration; see docs/game-balance-authority.txt ADD CONTENT:\n"
                        + String.join("\n", violationLines));
    }

    /**
     * R-ROLE (balance-overhaul order 3 — replaces R-WEAPON's power-score bands and the Railgun's
     * scarcity waiver): every ranged class is the best answer somewhere and never everywhere, on the
     * reference scenarios at depths 5 and 15 — niche, generalist bound, no dominance, risk pays, ammo
     * feasible. Also pins that no WEAPON_ROLE result is ever waived (the Railgun waiver is gone, AS8).
     */
    @Test
    void weaponRolesHoldOnTheReferenceScenarios() {
        List<BalanceSchema.RuleResult> results = BalanceSchema.weaponRoleResults();
        assertNoViolations(results);
        assertTrue(BalanceSchema.activeWaivers().stream()
                        .noneMatch(waiver -> waiver.kind == BalanceSchema.RuleKind.WEAPON_ROLE),
                "R-ROLE carries no waiver");
        assertTrue(results.stream().anyMatch(result -> result.subject.startsWith("R-ROLE-4")),
                "R-ROLE-4 is evaluated for the SPREAD class");
    }

    /** R-ENEMY: every non-boss archetype's threat points land in its role band. */
    @Test
    void enemyThreatPointsLandInRoleBands() {
        assertNoViolations(BalanceSchema.enemyThreatPointResults());
    }

    /**
     * R-ENEMY part 2 (balance-overhaul order 1, R8): every archetype's depth-1 hits-to-kill (on-curve Assault
     * Rifle at 3 tiles) and hits-to-die (205-eHP start player) land in its role's bands. Replaces the
     * golden-ratio bands (and their chaff / mini-elite exemptions): every role now has a target.
     */
    @Test
    void enemyFightLengthsLandInTheirRoleHitBands() {
        assertNoViolations(BalanceSchema.enemyHitResults());
    }

    /**
     * R-PERK PK1 FELT (balance-overhaul order 5) — REPLACES R-CARD (every level-up card priced into the
     * LEVEL_UP_BUDGET_PP band; OVERRIDE CLAUSE): a level-up is no longer priced to be EQUAL, every perk rank
     * must be FELT on its tag's niche at every reference depth.
     */
    @Test
    void everyPerkRankIsFeltOnItsNicheReplacingTheCardBudget() {
        assertNoViolations(BalanceSchema.perkFeltResults());
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

    /**
     * R-HEAVY (balance-overhaul order 4): every HEAVY verb deals [0.40, 0.60] of the on-curve player's max HP
     * at depths 1..25, is committed at least one turn ahead, and a guarding player takes at most 12%.
     */
    @Test
    void heavyAttacksHoldTheDamageBandAndAreGuardCapped() {
        assertNoViolations(BalanceSchema.heavyResults());
    }

    /**
     * R-HAZARD (balance-overhaul order 4, H13/H14): barrel blasts are fractions of the reference soldier / the
     * player's max HP that kill every chaff at every depth, and barrels sit in >= 40% of the group rooms on
     * COMBAT / ELITE floors only.
     */
    @Test
    void barrelsStayMeaningfulAtEveryDepthAndSitBesideGroups() {
        assertNoViolations(BalanceSchema.hazardResults());
        assertNoViolations(BalanceSchema.hazardPlacementResults());
    }

    // R-DEPTH (depth coupling, 1..15) was RETIRED by balance-overhaul order 1 and REPLACED by R-LADDER L1
    // (on-curve TTK/TTD flat within +/-15% at 1..25), asserted by thePowerLadderHoldsAtEveryDepthToTheRunEnd.

    /**
     * R-SUPPLY generator sweep — TRACKING (replaces R-SCARCITY, balance-overhaul order 2 override): on every
     * generator x node type x audited depth x 30 seeds, every category's placed supply is within +/-10% of
     * the plan derived from that floor's own roster (or one pickup's rounding), nothing is left unplaced,
     * and a quarter of the eligible supply rides on carriers.
     */
    @Test
    void everyGeneratedFloorTracksItsOwnDemand() {
        assertNoViolations(BalanceSchema.supplySweepResults("tracking", "unplaced", "carrier share"));
    }

    /**
     * R-SUPPLY, the ELITE promise (balance-overhaul order 2, A6 / C2): on every combat generator and
     * audited depth the ELITE floor spends ~1.6x a COMBAT floor's Threat Points, and every ELITE floor
     * places its reward weapon at level >= d+1 in or past the anchor group's room.
     */
    @Test
    void everyEliteFloorPaysItsThreatWithAWeaponBehindTheAnchor() {
        assertNoViolations(BalanceSchema.eliteRewardResults());
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

    /**
     * C-ITEM (K8) — folds the retired COVERAGE kind's consumable rows (its relic row moved to R-RELIC-SOURCE
     * "resolves", asserted by {@code theRelicPoolAlwaysFillsAnOffer}): every non-weapon ItemType declares a supply stance
     * (planner category, or not floor supplied with a reason); a consumable is a priced heal or a declared utility;
     * a HEAL stance has a HealPickupSpec; stackables have a stack size; descriptions are non-blank.
     */
    @Test
    void everyItemIsAdmitted() {
        assertAdmitted(BalanceSchema.admissionResults(BalanceSchema.AdmissionRule.ITEM));
    }

    /**
     * C-STATUS (K10): every StatusType has a declaration whose named magnitude and duration constants resolve and
     * are positive (a DOT_FRACTION magnitude a fraction in (0, 1)), with its armour interaction and any cleanse item
     * (a CONSUMABLE) declared.
     */
    @Test
    void everyStatusEffectIsAdmitted() {
        assertAdmitted(BalanceSchema.admissionResults(BalanceSchema.AdmissionRule.STATUS));
    }

    /**
     * C-HAZARD (K11): every declared hazard (barrels E / g, fire i, toxic q) names resolving constants (player side a
     * fraction in (0, 1]), a recognised tile symbol, a declared applied status, a damage class and an existing
     * placement owner.
     */
    @Test
    void everyHazardIsAdmitted() {
        assertAdmitted(BalanceSchema.admissionResults(BalanceSchema.AdmissionRule.HAZARD));
    }

    /**
     * C-ROOM (K12): every registered RoomBlueprint declares a supply stance, and a SUPPLY_NEUTRAL room's build(), run
     * in isolation over a seed sweep, stamps no pickup and no enemy-spawn symbol.
     */
    @Test
    void everyRoomBlueprintIsAdmitted() {
        assertAdmitted(BalanceSchema.admissionResults(BalanceSchema.AdmissionRule.ROOM));
    }

    /**
     * C-GENERATOR (K13): every GeneratorId is registered, builds headlessly, serves at least one node type and has
     * R-SUPPLY generator-sweep results for each type it serves (its floors go through the shared supply pipeline).
     */
    @Test
    void everyLevelGeneratorIsAdmitted() {
        assertAdmitted(BalanceSchema.admissionResults(BalanceSchema.AdmissionRule.GENERATOR));
    }

    /**
     * C-ENEMY (K1): every EnemyType declares role, family, trait, positive stats, a known positional multiplier, a
     * tactical verb, a priced move-set, an R-HEAVY-evaluated wind-up and a legal first depth; every non-boss also has
     * its role's TP and hit bands (the old COVERAGE "enemy" row), finite positive threat, derived XP, a unique spawn
     * symbol, a group-template slot and an ORA archetype line. Also pins G5: an ADMISSION waiver cannot be registered.
     */
    @Test
    void everyEnemyArchetypeIsAdmitted() {
        List<BalanceSchema.RuleResult> results = BalanceSchema.admissionResults(BalanceSchema.AdmissionRule.ENEMY);
        assertAdmitted(results);
        assertEquals(ge.tbegvadze.toon3d.enemy.EnemyType.values().length, results.size(),
                "one C-ENEMY result per EnemyType");
        assertTrue(BalanceSchema.activeWaivers().stream()
                        .noneMatch(waiver -> waiver.kind == BalanceSchema.RuleKind.ADMISSION),
                "ADMISSION is never waived (G5)");
    }

    /**
     * C-FAMILY (K2): every EnemyFamily has a trait, its own ALERT / ATTACK / DEATH voice in the audio catalog and both
     * bestiary lines; every EnemyTrait has a positive matchup cell for every DamageClass, a glyph unless FLESH, and a
     * WeaponRoleModel reference scenario whose archetype presents it.
     */
    @Test
    void everyEnemyFamilyAndTraitIsAdmitted() {
        assertAdmitted(BalanceSchema.admissionResults(BalanceSchema.AdmissionRule.FAMILY));
    }

    /**
     * C-VERB (K3) — folds the old COVERAGE "special verb" row and {@code everySpecialVerbHasARegisteredEquivalence}:
     * every SpecialAbility has a SpecialVerbPricing; a damaging verb is a declared HEAVY committed at least a turn
     * ahead and evaluated by R-HEAVY; a summon verb has positive caps; ORA has an ability line for it.
     */
    @Test
    void everySpecialVerbIsAdmitted() {
        List<BalanceSchema.RuleResult> results = BalanceSchema.admissionResults(BalanceSchema.AdmissionRule.VERB);
        assertAdmitted(results);
        for (ge.tbegvadze.toon3d.enemy.SpecialAbility ability : ge.tbegvadze.toon3d.enemy.SpecialAbility.values()) {
            assertTrue(BalanceSchema.specialVerbPricing(ability) != null,
                    ability.name() + " has no SpecialVerbPricing registered");
        }
    }

    /**
     * C-BOSS (K4): every BossTable row has a legal boss depth, positive dials, boss verbs, derived stats and
     * R-BOSS-GATE / R-BOSS-FAIR results; every BOSS-role EnemyType is fought on some row.
     */
    @Test
    void everyBossIsAdmitted() {
        assertAdmitted(BalanceSchema.admissionResults(BalanceSchema.AdmissionRule.BOSS));
    }

    /**
     * C-WEAPON (K5) — folds the old COVERAGE "weapon item" row: every WEAPON ItemType is classified; a ranged one has
     * a RoleWeapon row with class, ammo type and positive base hit / clip / range, a declared niche naming a real
     * scenario (BALLISTIC is the generalist) that R-ROLE results reach, and a place in the planner's drop pool; a
     * melee one is BLADE or BLUNT.
     */
    @Test
    void everyWeaponIsAdmitted() {
        assertAdmitted(BalanceSchema.admissionResults(BalanceSchema.AdmissionRule.WEAPON));
    }

    /**
     * C-ABILITY (K6): every WeaponAbility declares family, trigger, rank and build tag, prices to a finite
     * non-negative PP at every weapon level 1..MAX_WEAPON_LEVEL, and a SIGNATURE ability is rollable on some weapon.
     */
    @Test
    void everyWeaponAbilityIsAdmitted() {
        assertAdmitted(BalanceSchema.admissionResults(BalanceSchema.AdmissionRule.ABILITY));
    }

    /**
     * C-PERK (K7): every Perk and Keystone declares its tag, effects and text and is reached by PK1 / R-KEYSTONE;
     * every BuildTag has a perk, exactly one keystone and an R-CARRY row (a waived SCAVENGER row still counts).
     */
    @Test
    void everyPerkKeystoneAndBuildTagIsAdmitted() {
        assertAdmitted(BalanceSchema.admissionResults(BalanceSchema.AdmissionRule.PERK));
    }

    /**
     * C-AMMO (K9) — folds the old COVERAGE "ammo type" row: every AmmoType has a scarcity row, positive generosity,
     * reserve cap, box size and banking target, an AMMO pickup item, and the shop prices ammo at a positive rate.
     */
    @Test
    void everyAmmoTypeIsAdmitted() {
        assertAdmitted(BalanceSchema.admissionResults(BalanceSchema.AdmissionRule.AMMO));
    }

    // R-GEARGATE (region gear step + golden-ratio gate) was RETIRED by balance-overhaul order 1 and REPLACED
    // by R-LADDER L2/L4 (per-floor lag multipliers, level felt), asserted by
    // thePowerLadderHoldsAtEveryDepthToTheRunEnd — a stronger, per-floor version of the same property.

    /**
     * R-ABILITY (balance-overhaul order 5, B5/B6 — replaces order 2's "priced within the richest tier's
     * ceiling"): every surviving ability is priced and FELT (>= ABILITY_MIN_FELT_PP at its expected weapon level)
     * and tagged; each tier's count and budget match B5; budgets rise with rarity; and the cheapest and priciest
     * draw of every tier's shape lands inside its band at every weapon level, for a gun and a melee weapon.
     */
    @Test
    void everySurvivingAbilityIsFeltAndEveryTierShapeFitsItsBand() {
        assertNoViolations(BalanceSchema.abilityBudgetResults());
    }

    /**
     * R-ABILITY roll invariant (A4; replaces order 2's ceiling-only sweep): every real roll carries EXACTLY its
     * tier's count, its MAJORs and (LEGENDARY) its signature, with a total INSIDE the band — a floor as well as a
     * ceiling. Asserts the schema's sampled sweep (ABILITY_ROLL_SWEEP_SEEDS x 5 tiers x ABILITY_ROLL_SWEEP_DEPTHS,
     * gun and melee), then the full level grid (200 seeds x every level) the old ceiling sweep covered.
     */
    @Test
    void everyRollMeetsItsTierCountAndStaysInsideItsBand() {
        assertNoViolations(BalanceSchema.abilityRollSweepResults());
        java.util.List<String> violations = new java.util.ArrayList<>();
        for (long seed = 0; seed < 200; seed++) {
            ge.tbegvadze.toon3d.entity.WeaponRoller roller =
                    new ge.tbegvadze.toon3d.entity.WeaponRoller(seed);
            for (ge.tbegvadze.toon3d.entity.WeaponTier tier
                    : ge.tbegvadze.toon3d.entity.WeaponTier.values()) {
                for (boolean isMelee : new boolean[]{false, true}) {
                    for (int level = 1; level <= WeaponConstants.MAX_WEAPON_LEVEL; level++) {
                        ge.tbegvadze.toon3d.entity.AbilityInstance[] abilities =
                                roller.rollAbilitySet(isMelee, tier, level);
                        float total = ge.tbegvadze.toon3d.entity.WeaponRoller
                                .totalAbilityPowerPoints(abilities);
                        boolean countHolds = abilities.length == tier.totalAbilityCount();
                        if (!countHolds || !ge.tbegvadze.toon3d.entity.WeaponRoller.isWithinTierBand(tier, total)) {
                            violations.add(String.format(
                                    "  seed=%d tier=%s melee=%b level=%d count=%d/%d total=%.2f band=%.2f..%.2f",
                                    seed, tier, isMelee, level, abilities.length, tier.totalAbilityCount(), total,
                                    tier.abilityPowerPointFloor(), tier.abilityPowerPointCeiling()));
                        }
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                () -> "Ability rolls broke their tier's count or band:\n" + String.join("\n", violations));
    }

    /**
     * R-SUPPLY generator sweep — SPREAD (replaces R-SCARCITY-DEPTH, balance-overhaul order 2 override): at
     * every audited depth no room / chamber holds more than 35% of any category's pickups.
     */
    @Test
    void everyGeneratedFloorSpreadsItsSupplyAtEveryDepth() {
        assertNoViolations(BalanceSchema.supplySweepResults("room-share"));
    }

    /**
     * R-SUPPLY generator sweep — HEAL FLOOR (replaces R-HEALDRAIN-DEPTH, balance-overhaul order 2 override):
     * every non-BOSS / REST / REGION_GATE floor of every generator carries at least one medkit's worth of
     * heal value reachable without a keycard, half of it in the first half of the floor.
     */
    @Test
    void everyGeneratedFloorCarriesTheHealFloorAtEveryDepth() {
        assertNoViolations(BalanceSchema.supplySweepResults("heal floor"));
    }

    /**
     * R-CREDITS v2 (balance-overhaul order 6, F12 — replaced the order-3 band [0.9, 1.4] on one weapon-class buy per
     * region): for each of the five regions, income per floor / the NEEDED BUNDLE per floor (LEVEL UP rung + 1.5
     * consumables + the region's tier step amortised) lands in [1.10, 1.40]; one line per region.
     */
    @Test
    void creditIncomeAffordsTheExpectedBundleEveryRegion() {
        assertEquals(ge.tbegvadze.toon3d.util.RouteMapConstants.FIXED_REGION_COUNT, BalanceSchema.creditResults().size(),
                "R-CREDITS v2 reports every region of the finite run");
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

    /** R-XP-PACE (order 4; horizon 1..25 since balance-overhaul order 1): every floor awards [1.0, 1.3] level-ups of XP. */
    @Test
    void xpPacingHoldsAtEveryDepthToTheRunEnd() {
        assertNoViolations(BalanceSchema.xpPaceResults());
    }

    /**
     * R-PERK PK1 BREAKPOINT (balance-overhaul order 5) — R-CARD-BREAKPOINT folded in (OVERRIDE CLAUSE): every
     * perk's max rank crosses >= 1 whole TTK / TTD step (or its D4 resource step) on its niche.
     */
    @Test
    void everyPerkCrossesABreakpointByItsMaxRank() {
        assertNoViolations(BalanceSchema.perkBreakpointResults());
    }

    /**
     * R-PERK (balance-overhaul order 5): PK1 FELT — every perk rank improves its niche by at least
     * PERK_FELT_MIN_GAIN at every reference depth and its max rank crosses a whole TTK / TTD step; PK2
     * FOCUSED — no non-VITALITY perk improves all eight scenarios past the bound, no VITALITY rank any one.
     */
    @Test
    void everyPerkRankIsFeltAndFocused() {
        assertNoViolations(BalanceSchema.perkResults());
    }

    /** R-KEYSTONE (balance-overhaul order 5): every keystone buys its niche and pays a real cost elsewhere. */
    @Test
    void everyKeystoneTradesANicheForACost() {
        assertNoViolations(BalanceSchema.keystoneResults());
    }

    /**
     * R-CARRY (balance-overhaul order 5): every tag's expected depth-15 build lifts the player within
     * 1 + BUILD_CARRY_TOLERANCE of every other tag's — no trap build, no dominant build.
     */
    @Test
    void noBuildIsATrapOrDominant() {
        assertNoViolations(BalanceSchema.buildCarryResults());
    }

    /**
     * R-RELIC (relics-run-changing-artifacts): every non-CURSED relic's gain lands in its rarity x valuation band
     * at every reference depth — felt, never dominant. Also pins that the rule actually evaluated every relic.
     */
    @Test
    void everyRelicPricesIntoItsRarityBand() {
        List<BalanceSchema.RuleResult> results = BalanceSchema.relicResults();
        assertNoViolations(results);
        assertTrue(!results.isEmpty(), "R-RELIC evaluated at least one relic");
    }

    /**
     * R-RELIC-CURSE: every CURSED relic nets a gain inside the CURSED band, and its cost effects alone cost a real
     * loss somewhere (the R-KEYSTONE cost idiom).
     */
    @Test
    void everyCursedRelicTradesAGainForARealCost() {
        List<BalanceSchema.RuleResult> results = BalanceSchema.relicCurseResults();
        assertNoViolations(results);
        assertTrue(results.stream().anyMatch(result -> result.subject.endsWith(" cost")),
                "R-RELIC-CURSE evaluated a cursed relic's cost");
    }

    /**
     * R-RELIC-STACK: at every boss depth a run's expected relics never out-carry one committed build, and the
     * luckiest hand stays inside the build spread.
     */
    @Test
    void noRelicHandOutCarriesACommittedBuild() {
        assertNoViolations(BalanceSchema.relicStackResults());
    }

    /**
     * R-RELIC-SOURCE: every offer can fill, weights sum to 1, ids are unique, every curse states and carries its
     * cost, the perk-rank price anchor matches the measured build, the route offers relics at all, and every relic
     * resolves its rarity, trigger, valuation and band (the retired COVERAGE kind's relic row).
     */
    @Test
    void theRelicPoolAlwaysFillsAnOffer() {
        List<BalanceSchema.RuleResult> results = BalanceSchema.relicSourceResults();
        assertNoViolations(results);
        assertTrue(results.stream().anyMatch(result -> result.subject.startsWith("R-RELIC-SOURCE resolves ")),
                "every relic's resolution is checked");
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
     * PACK COHERENCE (order 5; re-stated by balance-overhaul order 2): chaff never spawns alone. Since order 2
     * every chaff slot of every group template fields at least GROUP_CHAFF_SLOT_MIN of ONE archetype, so no
     * CHAFF-role type ever appears fewer times than that on a planned floor. Proven over 100 seeds x depths
     * 1..15 for every node kind that fields enemies (COMBAT, ELITE, CALM).
     */
    @Test
    void chaffAlwaysSpawnsInPacksOverAHundredSeeds() {
        java.util.List<String> lonePacks = new java.util.ArrayList<>();
        ge.tbegvadze.toon3d.route.NodeSupplySpec[] specs = {
                ge.tbegvadze.toon3d.route.NodeSupplySpecs.combat(),
                ge.tbegvadze.toon3d.route.NodeSupplySpecs.elite(),
                ge.tbegvadze.toon3d.route.NodeSupplySpecs.cache()};
        for (ge.tbegvadze.toon3d.route.NodeSupplySpec spec : specs) {
            for (long seed = 0; seed < 100; seed++) {
                for (int depth = 1; depth <= 15; depth++) {
                    ge.tbegvadze.toon3d.level.EncounterBudgetPlanner.Plan plan =
                            new ge.tbegvadze.toon3d.level.EncounterBudgetPlanner(
                                    depth, new java.util.Random(seed * 97L + depth), 1f, spec).plan();
                    java.util.EnumMap<ge.tbegvadze.toon3d.enemy.EnemyType, Integer> counts =
                            new java.util.EnumMap<>(ge.tbegvadze.toon3d.enemy.EnemyType.class);
                    for (ge.tbegvadze.toon3d.enemy.EnemyType type : plan.enemies()) {
                        counts.merge(type, 1, Integer::sum);
                    }
                    for (java.util.Map.Entry<ge.tbegvadze.toon3d.enemy.EnemyType, Integer> entry
                            : counts.entrySet()) {
                        if (entry.getKey().role() == ge.tbegvadze.toon3d.enemy.EnemyRole.CHAFF
                                && entry.getValue() < BalanceConfig.GROUP_CHAFF_SLOT_MIN) {
                            lonePacks.add(String.format("  %s seed=%d depth=%d %s count=%d (< %d)",
                                    spec.type(), seed, depth, entry.getKey().displayName(), entry.getValue(),
                                    BalanceConfig.GROUP_CHAFF_SLOT_MIN));
                        }
                    }
                }
            }
        }
        assertTrue(lonePacks.isEmpty(),
                () -> "Chaff spawned below the minimum pack size:\n" + String.join("\n", lonePacks));
    }

    /**
     * Order-5 acceptance criterion (enemy eHP path): every archetype's eHP runs through the shared
     * GameMath.effectiveHitPoints primitive via EnemyType.neutralEffectiveHitPoints() — no raw-HP shortcut.
     * With today's all-zero mitigation the result still equals raw maxHealth, but through the one formula.
     * Balance-overhaul order 3 (CP2): the PRICED eHP (EnemyType.effectiveHitPoints, what TP/XP read) is
     * that neutral eHP through GameMath.traitAdjustedEnemyEffectiveHitPoints at the generalist's
     * weighted matchup — never a second hand-rolled formula.
     */
    @Test
    void enemyEffectiveHitPointsRunThroughTheSharedPrimitive() {
        for (ge.tbegvadze.toon3d.enemy.EnemyType type
                : ge.tbegvadze.toon3d.enemy.EnemyType.values()) {
            float viaPrimitive = GameMath.enemyEffectiveHitPoints(type.maxHealth(), type.armorPool(),
                    type.dodgeChance(), type.flatReduction(), BalanceConfig.REFERENCE_PLAYER_DPT);
            assertTrue(Math.abs(type.neutralEffectiveHitPoints() - viaPrimitive) < 1e-3f,
                    () -> type.displayName() + " eHP must come from GameMath.enemyEffectiveHitPoints");
            float referenceMultiplier = GameMath.matchupReferenceMultiplier(
                    ge.tbegvadze.toon3d.entity.MatchupCatalog.shared().multiplier(
                            ge.tbegvadze.toon3d.entity.DamageClass.BALLISTIC, type.trait()),
                    BalanceConfig.MATCHUP_TP_REFERENCE_WEIGHT);
            float priced = GameMath.traitAdjustedEnemyEffectiveHitPoints(viaPrimitive, referenceMultiplier);
            assertTrue(Math.abs(type.effectiveHitPoints() - priced) < 1e-3f,
                    () -> type.displayName() + " priced eHP must be the neutral eHP at the generalist's matchup");
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
     * Order-6 acceptance criterion (zero flat boss constants), re-pointed at the BOSS TABLE (balance-overhaul
     * order 6, F3): boss HP and every verb's damage are DERIVED per row by {@link BossBalance}, so an archetype
     * that fights on two rows (the Overseer at 5 and 20, the Corruptor at 10 and 25) re-derives strictly MORE HP
     * and XP on the deeper one — proof the stats are computed from the row, not read from a flat constant. Also
     * pins the table's shape: one row per boss floor of the finite run, the last at RUN_FINAL_DEPTH.
     */
    @Test
    void bossStatsScaleWithDepthNotFlatConstants() {
        java.util.List<ge.tbegvadze.toon3d.route.BossTable.Row> rows =
                ge.tbegvadze.toon3d.route.BossTable.standard().rows();
        assertEquals(BalanceConfig.RUN_FINAL_DEPTH / Constants.BOSS_FLOOR_INTERVAL, rows.size(),
                "one BossTable row per boss floor of the finite run");
        assertEquals(BalanceConfig.RUN_FINAL_DEPTH, rows.get(rows.size() - 1).depth,
                "the last BossTable row is the run-final boss");
        for (ge.tbegvadze.toon3d.route.BossTable.Row shallow : rows) {
            for (ge.tbegvadze.toon3d.route.BossTable.Row deep : rows) {
                if (deep.archetype != shallow.archetype || deep.depth <= shallow.depth) continue;
                BossStats canonical = BossBalance.statsForRow(shallow);
                BossStats deeper    = BossBalance.statsForRow(deep);
                assertTrue(deeper.effectiveHitPoints > canonical.effectiveHitPoints,
                        () -> shallow.archetype.displayName + " depth " + deep.depth + " HP " + deeper.effectiveHitPoints
                                + " must exceed depth " + shallow.depth + "'s " + canonical.effectiveHitPoints);
                assertTrue(deeper.xpReward > canonical.xpReward,
                        () -> shallow.archetype.displayName + " deeper XP must exceed shallower (reward scales with depth)");
            }
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

    /**
     * R-TWIST (floor-twists): every COMBAT floor twist is priced against plain COMBAT — a threat-raising
     * twist pays inside the risk-premium band (T1), a neutral one stays near COMBAT's EV (T2), and the
     * HUNTED ledger row never under-prices its hunter (T3).
     */
    @Test
    void floorTwistsArePricedAgainstPlainCombat() {
        java.util.List<BalanceSchema.RuleResult> results = BalanceSchema.twistResults();
        assertTrue(results.size() >= ge.tbegvadze.toon3d.route.RouteRegistries.affixes()
                .definitionsFor(ge.tbegvadze.toon3d.route.AffixScope.FLOOR_TWIST).size(),
                "every registered floor twist must be audited by R-TWIST");
        assertNoViolations(results);
    }

    /** R-CALM-COST (order 7): every calm node's EV sits below a combat node's by the banded discount. */
    @Test
    void calmNodesCostTempoAndLoot() {
        assertNoViolations(BalanceSchema.calmCostResults());
    }

    /**
     * R-MYSTERY-EV v2 (balance-overhaul order 6, F15 — replaced the order-7 "+/-15% of a combat floor"): the
     * weighted mystery table, paid in ladder currencies, is worth at least a combat floor and at most +30%, its
     * outcomes spread (a gamble, not a disguised COMBAT), and the worst pull stays "bad but survivable".
     */
    @Test
    void theMysteryTableIsWorthAboutACombatFloor() {
        List<BalanceSchema.RuleResult> results = BalanceSchema.mysteryExpectedValueResults();
        assertTrue(results.stream().anyMatch(result -> result.subject.equals("weighted table EV / combat EV")));
        assertTrue(results.stream().anyMatch(result -> result.subject.equals("outcome spread / combat EV")));
        assertNoViolations(results);
    }

    /**
     * R-EVENT-VALUE (balance-overhaul order 6, F15): every registered EVENT offers a choice, priced in ladder
     * currencies, worth at least one COMBAT floor's supply at every depth of the run — one line per event.
     */
    @Test
    void everyEventOffersAChoiceWorthACombatFloorsSupply() {
        List<BalanceSchema.RuleResult> results = BalanceSchema.eventValueResults();
        assertEquals(ge.tbegvadze.toon3d.route.RouteRegistries.events().size(), results.size(),
                "one R-EVENT-VALUE line per registered event");
        assertNoViolations(results);
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
     * and the levels earned (XP pace), over the whole finite run (balance-overhaul order 6: horizon 15 -> 25).
     * The banked resource is HEALTH (per-floor net drain): since balance-overhaul order 2 the SupplyPlanner pins
     * every floor's ammo to its own demand, so cumulative ammo scarcity is near-identical across policies (0.95
     * vs 0.93 before order 6), and order 6 (F15) removed the last route-dependent ammo grant (the EVENT ammo
     * boxes). What a calm route banks, and a hot route spends, is the player's health.
     */
    @Test
    void safestAndDeadliestRoutesSpanARealDifficultyRange() {
        ge.tbegvadze.toon3d.route.NodeEconomicsRegistry ledger = BalanceSchema.routeLedger();
        ge.tbegvadze.toon3d.route.RouteEconomicsModel.ModelFloor floor = BalanceSchema.routeModelFloor();
        float safestDrain = 0f, deadliestDrain = 0f, safestPace = 0f, deadliestPace = 0f;
        int samples = 0;
        for (long seed = 0; seed < 25; seed++) {
            ge.tbegvadze.toon3d.route.RouteMapGenerator generator =
                    new ge.tbegvadze.toon3d.route.RouteMapGenerator(
                            ge.tbegvadze.toon3d.route.RouteRegistries.nodeTypes(),
                            ge.tbegvadze.toon3d.route.RouteRegistries.generators());
            generator.setEliteAffixPool(ge.tbegvadze.toon3d.route.RouteRegistries.affixes().elitePool());
            generator.setTwistPool(ge.tbegvadze.toon3d.route.RouteRegistries.affixes()
                    .definitionsFor(ge.tbegvadze.toon3d.route.AffixScope.FLOOR_TWIST));
            ge.tbegvadze.toon3d.route.RouteMap map = generator.generate(seed,
                    ge.tbegvadze.toon3d.route.RegionPlan.defaultPlan());
            java.util.List<ge.tbegvadze.toon3d.route.RouteEconomicsModel.TrajectorySample> safest =
                    ge.tbegvadze.toon3d.route.RouteEconomicsModel.walk(map,
                            ge.tbegvadze.toon3d.route.RouteEconomicsModel.PathPolicy.SAFEST, ledger, floor,
                            BalanceConfig.RUN_FINAL_DEPTH);
            java.util.List<ge.tbegvadze.toon3d.route.RouteEconomicsModel.TrajectorySample> deadliest =
                    ge.tbegvadze.toon3d.route.RouteEconomicsModel.walk(map,
                            ge.tbegvadze.toon3d.route.RouteEconomicsModel.PathPolicy.DEADLIEST, ledger, floor,
                            BalanceConfig.RUN_FINAL_DEPTH);
            int paired = Math.min(safest.size(), deadliest.size());
            for (int index = 0; index < paired; index++) {
                safestDrain       += safest.get(index).netDrainFraction;
                deadliestDrain    += deadliest.get(index).netDrainFraction;
                safestPace        += safest.get(index).experiencePace;
                deadliestPace     += deadliest.get(index).experiencePace;
                samples++;
            }
        }
        assertTrue(samples > 0, "the trajectory walker must produce region-boundary samples");
        assertTrue(safestDrain < deadliestDrain,
                "the SAFEST route must bank more health (a lower net drain) than the DEADLIEST one");
        assertTrue(deadliestPace > safestPace,
                "the DEADLIEST route must earn more XP than the SAFEST one — that is what it buys");
    }

    /**
     * R-SINGLE-DIFFICULTY (order 8): exactly one starting-attribute block, and no constant, nested
     * type or enum constant anywhere in the scanned classes carries mode-family naming.
     */
    @Test
    void theGameHasExactlyOneDifficulty() {
        assertNoViolations(BalanceSchema.singleDifficultyResults());
    }

    /**
     * R-SINGLE-DIFFICULTY, source-level half: the reflection sweep can only see classes it is handed,
     * so this walks every main source file and fails on the mode names themselves. After order 8
     * there are zero matches in core/src/main — a mode system cannot come back through a file the
     * schema does not scan (a new Screen, a menu, a save field).
     *
     * <p>BalanceSchema.java is excluded because it is the file that DEFINES the banned tokens; its own
     * members are covered by the reflection half above.
     */
    @Test
    void noModeNamingSurvivesAnywhereInTheMainSources() {
        File sourceRoot = locateMainSourceRoot();
        List<String> offendingLines = new ArrayList<>();
        collectModeNamingMatches(sourceRoot, offendingLines);
        assertTrue(offendingLines.isEmpty(),
                () -> "The game has exactly ONE difficulty (new-game-balancr order 8). Mode-family "
                        + "naming found in the main sources:\n" + String.join("\n", offendingLines));
    }

    /** Mode names with zero legitimate use in this codebase — the source-sweep ban list. */
    private static final String[] SOURCE_MODE_TOKENS = { "RECRUIT", "NIGHTMARE", "ULTRA", "STAT_BASE_" };

    /** The one file allowed to name the banned tokens: the rule that bans them. */
    private static final String MODE_RULE_FILE_NAME = "BalanceSchema.java";

    /**
     * Finds {@code core/src/main/java} from the test's working directory, which Gradle sets to the
     * module project directory. Both the module-relative and repo-relative layouts are accepted, and
     * a few parent levels are searched so the test survives being run from either root.
     */
    private static File locateMainSourceRoot() {
        File directory = new File("").getAbsoluteFile();
        for (int level = 0; level < 5 && directory != null; level++, directory = directory.getParentFile()) {
            File moduleRelative = new File(directory, "src/main/java");
            if (moduleRelative.isDirectory()) return moduleRelative;
            File repoRelative = new File(directory, "core/src/main/java");
            if (repoRelative.isDirectory()) return repoRelative;
        }
        throw new IllegalStateException("cannot locate core/src/main/java from " + new File("").getAbsolutePath());
    }

    /** Recursively records every {@code path:line} in the tree whose text carries a banned mode name. */
    private static void collectModeNamingMatches(File directory, List<String> offendingLines) {
        File[] entries = directory.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            if (entry.isDirectory()) {
                collectModeNamingMatches(entry, offendingLines);
                continue;
            }
            if (!entry.getName().endsWith(".java") || entry.getName().equals(MODE_RULE_FILE_NAME)) continue;
            List<String> sourceLines;
            try {
                sourceLines = Files.readAllLines(entry.toPath(), StandardCharsets.UTF_8);
            } catch (IOException readFailure) {
                throw new IllegalStateException("cannot read source file " + entry, readFailure);
            }
            for (int lineIndex = 0; lineIndex < sourceLines.size(); lineIndex++) {
                String sourceLine = sourceLines.get(lineIndex);
                for (String token : SOURCE_MODE_TOKENS) {
                    if (sourceLine.contains(token)) {
                        offendingLines.add("  " + entry.getPath() + ":" + (lineIndex + 1) + " — " + sourceLine.trim());
                        break;
                    }
                }
            }
        }
    }

    /**
     * R-LADDER (balance-overhaul order 1): the power ladder at every depth 1..RUN_FINAL_DEPTH for every
     * non-boss role — on-curve fights stay flat, a lagging weapon is punished, running ahead is bounded,
     * and a weapon level, a rarity step and a character level are each FELT.
     */
    @Test
    void thePowerLadderHoldsAtEveryDepthToTheRunEnd() {
        assertNoViolations(BalanceSchema.ladderResults());
    }

    /** R-LADDER-AFFORD (balance-overhaul order 1): the shop LEVEL UP rung is at most half a combat floor's credits. */
    @Test
    void theLevelUpRungIsAffordableEveryFloor() {
        assertNoViolations(BalanceSchema.ladderAffordResults());
    }

    /**
     * R-SUPPLY, planner level (balance-overhaul order 2): every node spec x audit depth x audit seed is
     * planned against a real roster and placed on a synthetic floor — heal floor keycard-free and half
     * of it early, every category within +/-10% of plan (or one pickup's rounding), nothing unplaced, no
     * room over 35% of a category, a quarter of the eligible supply on carriers.
     */
    @Test
    void theSupplyPlannerTracksDemandOnEverySpec() {
        assertNoViolations(BalanceSchema.supplyPlannerResults());
    }

    /**
     * R-DENSITY, encounter half (balance-overhaul order 2, A3 / A5): on every generator x COMBAT / ELITE /
     * CACHE / SHOP x audited depth x 30 seeds, the floor fields its E1 body target; COMBAT and ELITE floors
     * keep >= 75% of enemies in groups of two or more, at least two groups of three or more and at most
     * three lone enemies (E4), and meet their first group within 18 walk tiles of the start (12 on depth 1),
     * never in the start room (E5).
     */
    @Test
    void everyCombatFloorFieldsItsGroupsAndAFirstContact() {
        assertNoViolations(BalanceSchema.densitySweepResults("bodies", "grouped", "big groups", "lone",
                "first contact", "start room"));
    }

    /**
     * R-DENSITY, footprint half (balance-overhaul order 2 E6, owner override 2026-10-01): every floor of the
     * three combat layouts builds within +/-15% of its target — the layout's ORIGINAL walkable size cut by
     * 50% / 40% / 30% / 25% on floors 1-4 and 20% from floor 5. E7 density is reported, not enforced.
     */
    @Test
    void everyGeneratedFloorHoldsItsFootprint() {
        assertNoViolations(BalanceSchema.densitySweepResults("footprint"));
    }

    /** The full sweep — belt-and-braces over the per-kind tests (catches rule kinds added later). */
    @Test
    void fullSchemaSweepHasNoViolations() {
        assertNoViolations(BalanceSchema.evaluate());
    }
}
