package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.RouteMapConstants;

/**
 * The v1 NODE EV LEDGER catalog (new-game-balancr order 7): one {@link NodeEconomics} row for
 * every node type, every ELITE affix and every MYSTERY outcome the map can offer. This is the
 * route-map twin of {@code BalanceSchema}'s weapon / enemy / heal registries — the place the
 * branching map declares its PRICE to the balance contract.
 *
 * <p>Every number here is read from {@link BalanceConfig} SECTION 19 (or from the payoff
 * constants the profiles themselves stamp), so a row can never drift from the floor the profile
 * actually builds: change the payoff, and the price changes with it.
 *
 * <p><b>Adding priced route content is ONE {@code register(...)} line here</b> — the same recipe
 * as adding a node type or an affix. Adding it WITHOUT a line fails R-ROUTE-PRICED under
 * {@code ./gradlew test}.
 *
 * <p>HONESTY NOTE — threat is priced at what the floor ACTUALLY spawns, not at the budget scale
 * the profile requests: {@code MedBayGenerator} (REST), {@code EventRoomGenerator} (EVENT) and
 * {@code GateAirlockGenerator} (REGION_GATE) emit ZERO enemy spawn points, so their calm budget
 * override has nothing to spend and their priced threat is 0. That is the HONEST-SAFE-NODE rule
 * as arithmetic rather than adjective.
 *
 * <p>Pure / headless — no LibGDX imports.
 */
public final class RouteEconomics {

    private RouteEconomics() {}

    /** Ledger id of the standard COMBAT node — the reference every route band is measured against. */
    public static final String STANDARD_COMBAT_ID = "combat";

    // Guaranteed heal values, assembled from the SAME payoff constants the profiles stamp. Armour is
    // priced at the VEST value each node carries from its *_ARMOUR_VEST_DEPTH (depths 4/6 onward),
    // which covers the majority of the audited 1..15 range. Power ladder (balance-overhaul order 1,
    // R10): every heal is a FRACTION of the player's max, so it is priced here at the depth-1 player
    // and keeps its relative value at every depth by construction (it grows with vitality).
    private static final int MEDKIT_FULL_HEAL =
            GameMath.fractionOfMaximum(BalanceConfig.PLAYER_MAX_HEALTH, BalanceConfig.MEDKIT_FULL_HEAL_FRACTION);
    private static final int MEDKIT_STIM_HEAL =
            GameMath.fractionOfMaximum(BalanceConfig.PLAYER_MAX_HEALTH, BalanceConfig.MEDKIT_STIM_HEAL_FRACTION);
    private static final int ARMOUR_SHARD_VALUE =
            GameMath.fractionOfMaximum(BalanceConfig.PLAYER_MAX_ARMOR, BalanceConfig.ARMOUR_SHARD_FRACTION);
    private static final int ARMOUR_VEST_VALUE =
            GameMath.fractionOfMaximum(BalanceConfig.PLAYER_MAX_ARMOR, BalanceConfig.ARMOUR_VEST_FRACTION);

    /**
     * The MED-BAY's total healing value as a FRACTION of the player's effective hit points: the
     * auto-doc's max-HP-share heal plus the clinic's take-away stock. Both are priced as an eHP
     * fraction rather than flat hit points because both hold their relative value as depth scales —
     * the auto-doc heals a share of max HP, and every take-away medkit/armour pickup is itself a
     * fraction of max (R10), so the clinic's stock COUNT no longer needs to scale with depth.
     */
    private static final float REST_HEAL_EFFECTIVE_HIT_POINT_FRACTION =
            (RouteMapConstants.REST_HEAL_FRACTION * BalanceConfig.PLAYER_MAX_HEALTH
                    + BalanceConfig.REST_MEDKITS * MEDKIT_FULL_HEAL
                    + BalanceConfig.REST_STIMS   * MEDKIT_STIM_HEAL
                    + BalanceConfig.REST_ARMOUR  * ARMOUR_VEST_VALUE)
                    / BalanceConfig.REFERENCE_PLAYER_EHP;

    /**
     * The healing an EVENT choice is expected to hand over, as an eHP fraction: the v1 catalogue's
     * survival payoffs are a FULL patch-up and armour-to-full (fractions of the player's own maxima),
     * scaled by the share of choices that pick one.
     */
    private static final float EVENT_HEAL_EFFECTIVE_HIT_POINT_FRACTION =
            BalanceConfig.ROUTE_EVENT_EXPECTED_CHOICE_SHARE
                    * (BalanceConfig.PLAYER_MAX_HEALTH + BalanceConfig.PLAYER_MAX_ARMOR)
                    / BalanceConfig.REFERENCE_PLAYER_EHP;

    /**
     * The upgrade opportunity a floor's own weapon drops are worth (balance-overhaul order 2): the spec's
     * expected on-level weapon offers x {@link BalanceConfig#ROUTE_UPGRADE_OPPORTUNITY_PER_ON_LEVEL_WEAPON}.
     */
    static float floorWeaponUpgradeOpportunity(NodeSupplySpec spec) {
        return GameMath.expectedOnLevelWeaponOffers(spec.weaponDrops(), spec.weaponDropChance(),
                spec.weaponLevelOffsetMin(), spec.weaponLevelOffsetMax())
                * BalanceConfig.ROUTE_UPGRADE_OPPORTUNITY_PER_ON_LEVEL_WEAPON;
    }

    /**
     * Registers the whole v1 ledger. Exposed so tests (and
     * {@code RouteRegistries.nodeEconomics()}) can populate a fresh registry.
     */
    public static void registerAll(NodeEconomicsRegistry registry) {
        registerNodes(registry);
        registerAffixes(registry);
        registerMysteryOutcomes(registry);
    }

    // =========================================================================
    // NODE ROWS — one per RouteNodeType.
    // =========================================================================

    private static void registerNodes(NodeEconomicsRegistry registry) {
        // COMBAT / ELITE / CACHE / SHOP (balance-overhaul order 2): their threat is the node's
        // NodeSupplySpec threat scale and their supply is the spec's PLAN (RouteEconomicsModel prices it
        // with the planner's own formulas) — one source, so the price tag is the floor the generator builds.
        // COMBAT — the reference node. Everything else is priced as a premium or a discount on it.
        registry.register(NodeEconomics.node(RouteNodeType.COMBAT, STANDARD_COMBAT_ID)
                .displayName("HOSTILE ZONE")
                .budgetScale(NodeSupplySpecs.combat().threatScale())
                .upgradeOpportunity(floorWeaponUpgradeOpportunity(NodeSupplySpecs.combat()))
                .build());

        // ELITE — the DANGER premium: 1.6x the threat, paid for by the vault share, the heavier armour
        // plan with its guaranteed vest, more chips and the +1..+2 weapon behind the anchor group.
        registry.register(NodeEconomics.node(RouteNodeType.ELITE, "elite")
                .displayName("ELITE HOTZONE")
                .budgetScale(NodeSupplySpecs.elite().threatScale())
                .upgradeOpportunity(floorWeaponUpgradeOpportunity(NodeSupplySpecs.elite()))
                .build());

        // CACHE — a CALM depot: light stragglers, a net-GAIN supply plan (drain target below zero).
        registry.register(NodeEconomics.node(RouteNodeType.CACHE, "cache")
                .displayName("SUPPLY CACHE")
                .budgetScale(NodeSupplySpecs.cache().threatScale())
                .upgradeOpportunity(floorWeaponUpgradeOpportunity(NodeSupplySpecs.cache()))
                .build());

        // SHOP — a standard floor with light resistance. The PURCHASE is paid for at a fair price
        // (GameMath.shopPrice), so only the "buy exactly what you need" surplus is credited.
        registry.register(NodeEconomics.node(RouteNodeType.SHOP, "shop")
                .displayName("BLACK MARKET")
                .budgetScale(NodeSupplySpecs.shop().threatScale())
                .upgradeOpportunity(BalanceConfig.ROUTE_UPGRADE_OPPORTUNITY_SHOP)
                .build());

        // REST — the honest sanctuary: MedBayGenerator emits ZERO spawn points, so priced threat is 0.
        registry.register(NodeEconomics.node(RouteNodeType.REST, "rest")
                .displayName("MED-BAY")
                .budgetScale(0f)
                .guaranteedHealEffectiveHitPointFraction(REST_HEAL_EFFECTIVE_HIT_POINT_FRACTION)
                .build());

        // MYSTERY — the hidden table. Its threat / resources / EV are the WEIGHTED aggregate of the
        // MYSTERY_OUTCOME rows below (resolved by RouteEconomicsModel), so this row carries no
        // payoff of its own; the flag is what makes it read GAMBLE in the derived pip tier.
        registry.register(NodeEconomics.node(RouteNodeType.MYSTERY, "mystery")
                .displayName("UNKNOWN SIGNAL")
                .hiddenOutcomeTable(true)
                .build());

        // EVENT — a curated non-combat beat: zero spawns, a small curated room, one typed choice.
        registry.register(NodeEconomics.node(RouteNodeType.EVENT, "event")
                .displayName("DISTRESS BEACON")
                .budgetScale(0f)
                .guaranteedAmmoBoxes(BalanceConfig.ROUTE_EVENT_EXPECTED_CHOICE_SHARE
                        * BalanceConfig.EVENT_AMMO_BOXES)
                // The v1 event choices heal by FRACTION (a full patch-up, armour to full), not by a flat
                // pickup value, so the ledger prices them as an eHP share — depth-honest by construction.
                .guaranteedHealEffectiveHitPointFraction(EVENT_HEAL_EFFECTIVE_HIT_POINT_FRACTION)
                .guaranteedExperiencePoints(BalanceConfig.ROUTE_EVENT_EXPECTED_CHOICE_SHARE
                        * BalanceConfig.EVENT_XP_LARGE)
                .build());

        // BOSS — a forced SET_PIECE. Its HP/damage/reward are DERIVED by BossBalance and governed by
        // R-BOSS-* (order 6), so it is exempt from the node EV bands; the row exists so the map's
        // coverage rule is complete and its pips derive like every other node.
        registry.register(NodeEconomics.node(RouteNodeType.BOSS, "boss")
                .displayName("BOSS ARENA")
                .budgetScale(BalanceConfig.ROUTE_BOSS_THREAT_SCALE)
                .forced(true)
                .build());

        // REGION_GATE — the ceremonial bulkhead: zero spawns, a pacing breath between acts.
        registry.register(NodeEconomics.node(RouteNodeType.REGION_GATE, "region_gate")
                .displayName("REGION GATE")
                .budgetScale(0f)
                .forced(true)
                .build());
    }

    // =========================================================================
    // AFFIX ROWS — a threat MULTIPLIER plus the vault premium that pays for it.
    // =========================================================================

    private static void registerAffixes(NodeEconomicsRegistry registry) {
        // IRRADIATED / FORTIFIED / VOLATILE are threat-NEUTRAL in budget terms (they change the
        // arena's hazards and cover, not the Threat Points spent), so they price at 1.0x with no
        // vault premium — R-RISK-PREMIUM keeps them honest if that ever changes.
        registry.register(NodeEconomics.affix(RouteMapConstants.AFFIX_IRRADIATED_ID)
                .displayName("IRRADIATED").build());

        registry.register(NodeEconomics.affix(RouteMapConstants.AFFIX_OVERCLOCKED_ID)
                .displayName("OVERCLOCKED")
                .budgetScale(BalanceConfig.AFFIX_OVERCLOCKED_BUDGET_MULT)
                .guaranteedAmmoBoxes(BalanceConfig.AFFIX_OVERCLOCKED_VAULT_AMMO_BOXES)
                .build());

        registry.register(NodeEconomics.affix(RouteMapConstants.AFFIX_SWARM_ID)
                .displayName("SWARM")
                .budgetScale(BalanceConfig.AFFIX_SWARM_BUDGET_MULT)
                .guaranteedAmmoBoxes(BalanceConfig.AFFIX_SWARM_VAULT_AMMO_BOXES)
                .build());

        registry.register(NodeEconomics.affix(RouteMapConstants.AFFIX_FORTIFIED_ID)
                .displayName("FORTIFIED")
                .guaranteedHealHitPoints(BalanceConfig.AFFIX_FORTIFIED_EXTRA_ARMOUR
                        * ARMOUR_SHARD_VALUE)
                .build());

        registry.register(NodeEconomics.affix(RouteMapConstants.AFFIX_VOLATILE_ID)
                .displayName("VOLATILE").build());
    }

    // =========================================================================
    // MYSTERY OUTCOME ROWS — the hidden table, priced row by row.
    // =========================================================================

    private static void registerMysteryOutcomes(NodeEconomicsRegistry registry) {
        // VAULT JACKPOT — the dream pull: lightly guarded, stuffed with loot.
        registry.register(NodeEconomics.mysteryOutcome(MysteryOutcome.VAULT_JACKPOT.name())
                .displayName("MYSTERY: VAULT JACKPOT")
                .tableWeight(BalanceConfig.MYSTERY_WEIGHT_VAULT)
                .scanToneId(MysteryOutcome.VAULT_JACKPOT.scanTone().name())
                .budgetScale(BalanceConfig.ROUTE_LIGHT_BUDGET_SCALE)
                .guaranteedAmmoBoxes(BalanceConfig.MYSTERY_VAULT_AMMO_BOXES)
                .guaranteedHealHitPoints(BalanceConfig.ELITE_MEDKITS * MEDKIT_FULL_HEAL
                        + BalanceConfig.ELITE_ARMOUR * ARMOUR_VEST_VALUE)
                .upgradeOpportunity(BalanceConfig.ROUTE_UPGRADE_OPPORTUNITY_ELITE)
                .build());

        // SECRET WARREN — a normal-budget floor that rewards searching (gated loot).
        registry.register(NodeEconomics.mysteryOutcome(MysteryOutcome.SECRET_WARREN.name())
                .displayName("MYSTERY: SECRET WARREN")
                .tableWeight(BalanceConfig.MYSTERY_WEIGHT_SECRET_WARREN)
                .scanToneId(MysteryOutcome.SECRET_WARREN.scanTone().name())
                .budgetScale(1f)
                .guaranteedAmmoBoxes(BalanceConfig.MYSTERY_WARREN_AMMO_BOXES)
                .guaranteedHealHitPoints(BalanceConfig.MYSTERY_WARREN_MEDKITS
                        * MEDKIT_FULL_HEAL)
                .upgradeOpportunity(BalanceConfig.ROUTE_UPGRADE_OPPORTUNITY_COMBAT)
                .build());

        // TRAP GAUNTLET — the hazards are the threat, not a swarm: a calm roster, a hazard bite,
        // a modest reward at the exit. The expected hazard damage is priced as NEGATIVE healing.
        registry.register(NodeEconomics.mysteryOutcome(MysteryOutcome.TRAP_GAUNTLET.name())
                .displayName("MYSTERY: TRAP GAUNTLET")
                .tableWeight(BalanceConfig.MYSTERY_WEIGHT_TRAP_GAUNTLET)
                .scanToneId(MysteryOutcome.TRAP_GAUNTLET.scanTone().name())
                .budgetScale(BalanceConfig.ROUTE_CALM_BUDGET_SCALE)
                .guaranteedAmmoBoxes(BalanceConfig.MYSTERY_TRAP_AMMO_BOXES)
                .guaranteedHealHitPoints(BalanceConfig.MYSTERY_TRAP_MEDKITS * MEDKIT_FULL_HEAL
                        - BalanceConfig.ROUTE_TRAP_GAUNTLET_HAZARD_HIT_POINTS)
                .upgradeOpportunity(BalanceConfig.ROUTE_UPGRADE_OPPORTUNITY_CACHE)
                .build());

        // AMBUSH — delegates to the ELITE hotzone, so it inherits the ELITE row's economics whole.
        registry.register(NodeEconomics.mysteryOutcome(MysteryOutcome.AMBUSH.name())
                .displayName("MYSTERY: AMBUSH")
                .tableWeight(BalanceConfig.MYSTERY_WEIGHT_AMBUSH)
                .scanToneId(MysteryOutcome.AMBUSH.scanTone().name())
                .delegateId("elite")
                .build());

        // LORE SIGNAL — delegates to the MED-BAY: near-combat-free, a heal and a story beat.
        registry.register(NodeEconomics.mysteryOutcome(MysteryOutcome.LORE_SIGNAL.name())
                .displayName("MYSTERY: LORE SIGNAL")
                .tableWeight(BalanceConfig.MYSTERY_WEIGHT_LORE_SIGNAL)
                .scanToneId(MysteryOutcome.LORE_SIGNAL.scanTone().name())
                .delegateId("rest")
                .build());

        // MALFUNCTION — the bad-but-survivable pull: a failing sector, no reward, unlit (its config
        // switches the lights off; its supply is the MYSTERY spec's plan, order 6 retunes it) and a hazard
        // bite on the way.
        registry.register(NodeEconomics.mysteryOutcome(MysteryOutcome.MALFUNCTION.name())
                .displayName("MYSTERY: MALFUNCTION")
                .tableWeight(BalanceConfig.MYSTERY_WEIGHT_MALFUNCTION)
                .scanToneId(MysteryOutcome.MALFUNCTION.scanTone().name())
                .budgetScale(BalanceConfig.ROUTE_CALM_BUDGET_SCALE)
                .guaranteedHealHitPoints(-BalanceConfig.ROUTE_MALFUNCTION_HAZARD_HIT_POINTS)
                .build());
    }
}
