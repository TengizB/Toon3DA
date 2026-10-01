package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.util.BalanceConfig;

/**
 * The shipped {@link NodeSupplySpec} rows (balance-overhaul order 2, rule S10) — one factory per node
 * type and {@link #registerAll}, the ONE place they are registered. Every number is a
 * {@link BalanceConfig} SECTION 21 constant.
 *
 * <p>COMBAT / ELITE / CACHE / SHOP carry the order-2 contents. REST / EVENT / MYSTERY / BOSS /
 * REGION_GATE carry TODAY'S contents expressed as a spec (order 6 retunes REST / EVENT / MYSTERY): their
 * bespoke stock still rides the profiles' guarantees, and their enemy counts still ride the profiles'
 * {@link EnemyBudgetOverride}s, so every generator goes through the planner without changing what those
 * floors hold.
 *
 * <p>Pure / headless — no LibGDX imports.
 */
public final class NodeSupplySpecs {

    private NodeSupplySpecs() {}

    /** Registers one row per {@link RouteNodeType}. */
    public static void registerAll(NodeSupplySpecRegistry registry) {
        registry.register(combat());
        registry.register(elite());
        registry.register(cache());
        registry.register(shop());
        registry.register(rest());
        registry.register(mystery());
        registry.register(event());
        registry.register(boss());
        registry.register(regionGate());
    }

    /** COMBAT — "RESISTANCE / STANDARD SUPPLY". Also the default for a floor built without a node. */
    public static NodeSupplySpec combat() {
        return NodeSupplySpec.builder(RouteNodeType.COMBAT, NodeSupplySpec.EncounterKind.COMBAT)
                .threat(BalanceConfig.NODE_SUPPLY_COMBAT_THREAT)
                .bodies(BalanceConfig.NODE_SUPPLY_COMBAT_BODIES)
                .ammoRatio(BalanceConfig.NODE_SUPPLY_COMBAT_AMMO_RATIO)
                .drainTarget(BalanceConfig.NODE_SUPPLY_COMBAT_DRAIN_TARGET)
                .armourShare(BalanceConfig.NODE_SUPPLY_COMBAT_ARMOUR_SHARE)
                .credits(BalanceConfig.NODE_SUPPLY_COMBAT_CREDITS)
                .weapons(BalanceConfig.NODE_SUPPLY_COMBAT_WEAPONS,
                        BalanceConfig.NODE_SUPPLY_COMBAT_WEAPON_OFFSET_MIN,
                        BalanceConfig.NODE_SUPPLY_COMBAT_WEAPON_OFFSET_MAX)
                .shapeRules()
                .density(BalanceConfig.DENSITY_COMBAT_MIN, BalanceConfig.DENSITY_COMBAT_MAX)
                .build();
    }

    /** ELITE — "HEAVY RESISTANCE / WEAPON +2 LV, RARE+": a WARBAND anchor guarding the reward weapon. */
    public static NodeSupplySpec elite() {
        return NodeSupplySpec.builder(RouteNodeType.ELITE, NodeSupplySpec.EncounterKind.ELITE)
                .threat(BalanceConfig.NODE_SUPPLY_ELITE_THREAT)
                .bodies(BalanceConfig.NODE_SUPPLY_ELITE_BODIES)
                .ammoRatio(BalanceConfig.NODE_SUPPLY_ELITE_AMMO_RATIO)
                .vaultAmmoRatio(BalanceConfig.NODE_SUPPLY_ELITE_VAULT_AMMO_RATIO)
                .drainTarget(BalanceConfig.NODE_SUPPLY_ELITE_DRAIN_TARGET)
                .armourShare(BalanceConfig.NODE_SUPPLY_ELITE_ARMOUR_SHARE)
                .guaranteedVest()
                .credits(BalanceConfig.NODE_SUPPLY_ELITE_CREDITS)
                .weapons(BalanceConfig.NODE_SUPPLY_ELITE_WEAPONS,
                        BalanceConfig.NODE_SUPPLY_ELITE_WEAPON_OFFSET_MIN,
                        BalanceConfig.NODE_SUPPLY_ELITE_WEAPON_OFFSET_MAX)
                .weaponTierBonus(BalanceConfig.NODE_SUPPLY_ELITE_WEAPON_TIER_BONUS)
                .weaponBehindAnchor()
                .shapeRules()
                .density(BalanceConfig.DENSITY_ELITE_MIN, BalanceConfig.DENSITY_ELITE_MAX)
                .footprintLowerHalf()
                .build();
    }

    /** CACHE — "LIGHT RESISTANCE / SUPPLIES": a few stragglers and a net heal gain. */
    public static NodeSupplySpec cache() {
        return NodeSupplySpec.builder(RouteNodeType.CACHE, NodeSupplySpec.EncounterKind.CALM)
                .threat(BalanceConfig.NODE_SUPPLY_CACHE_THREAT)
                .bodies(BalanceConfig.NODE_SUPPLY_CACHE_BODIES)
                .ammoRatio(BalanceConfig.NODE_SUPPLY_CACHE_AMMO_RATIO)
                .drainTarget(BalanceConfig.NODE_SUPPLY_CACHE_DRAIN_TARGET)
                .armourShare(BalanceConfig.NODE_SUPPLY_CACHE_ARMOUR_SHARE)
                .credits(BalanceConfig.NODE_SUPPLY_CACHE_CREDITS)
                .weapons(BalanceConfig.NODE_SUPPLY_CACHE_WEAPONS, 0, 0)
                .weaponDropChance(BalanceConfig.NODE_SUPPLY_CACHE_WEAPON_CHANCE)
                .density(BalanceConfig.DENSITY_CALM_MIN, BalanceConfig.DENSITY_CALM_MAX)
                .build();
    }

    /** SHOP — "LIGHT RESISTANCE / FABRICATORS": the vending machines are the payoff, no weapon drop. */
    public static NodeSupplySpec shop() {
        return NodeSupplySpec.builder(RouteNodeType.SHOP, NodeSupplySpec.EncounterKind.CALM)
                .threat(BalanceConfig.NODE_SUPPLY_SHOP_THREAT)
                .bodies(BalanceConfig.NODE_SUPPLY_SHOP_BODIES)
                .ammoRatio(BalanceConfig.NODE_SUPPLY_SHOP_AMMO_RATIO)
                .drainTarget(BalanceConfig.NODE_SUPPLY_SHOP_DRAIN_TARGET)
                .armourShare(BalanceConfig.NODE_SUPPLY_SHOP_ARMOUR_SHARE)
                .credits(BalanceConfig.NODE_SUPPLY_SHOP_CREDITS)
                .density(BalanceConfig.DENSITY_CALM_MIN, BalanceConfig.DENSITY_CALM_MAX)
                .build();
    }

    /** REST — today's clinic: no planned enemies, no heal floor (the auto-doc is the heal), chips only. */
    public static NodeSupplySpec rest() {
        return NodeSupplySpec.builder(RouteNodeType.REST, NodeSupplySpec.EncounterKind.NONE)
                .threat(0f)
                .bodies(0f)
                .noHealFloor()
                .credits(BalanceConfig.NODE_SUPPLY_COMBAT_CREDITS)
                .build();
    }

    /**
     * MYSTERY — the hidden outcome's own budget override scales the floor; supplied like COMBAT until
     * order 6 retunes each outcome. The outcome's bespoke vault / warren / trap stock still rides its
     * guarantees.
     */
    public static NodeSupplySpec mystery() {
        return NodeSupplySpec.builder(RouteNodeType.MYSTERY, NodeSupplySpec.EncounterKind.COMBAT)
                .threat(BalanceConfig.NODE_SUPPLY_MYSTERY_THREAT)
                .bodies(BalanceConfig.NODE_SUPPLY_MYSTERY_THREAT)
                .ammoRatio(BalanceConfig.NODE_SUPPLY_COMBAT_AMMO_RATIO)
                .drainTarget(BalanceConfig.NODE_SUPPLY_COMBAT_DRAIN_TARGET)
                .armourShare(BalanceConfig.NODE_SUPPLY_COMBAT_ARMOUR_SHARE)
                .credits(BalanceConfig.NODE_SUPPLY_COMBAT_CREDITS)
                .weapons(BalanceConfig.NODE_SUPPLY_COMBAT_WEAPONS,
                        BalanceConfig.NODE_SUPPLY_COMBAT_WEAPON_OFFSET_MIN,
                        BalanceConfig.NODE_SUPPLY_COMBAT_WEAPON_OFFSET_MAX)
                .build();
    }

    /** EVENT — today's story room: no planned enemies; the S4 heal floor still applies. */
    public static NodeSupplySpec event() {
        return NodeSupplySpec.builder(RouteNodeType.EVENT, NodeSupplySpec.EncounterKind.NONE)
                .threat(BalanceConfig.NODE_SUPPLY_EVENT_THREAT)
                .bodies(0f)
                .credits(BalanceConfig.NODE_SUPPLY_COMBAT_CREDITS)
                .build();
    }

    /** BOSS — the arena: the boss is seeded by World; ammo = the R-BOSS-AMMO arena budget; no heal floor. */
    public static NodeSupplySpec boss() {
        return NodeSupplySpec.builder(RouteNodeType.BOSS, NodeSupplySpec.EncounterKind.NONE)
                .threat(0f)
                .bodies(0f)
                .bossArenaAmmo()
                .noHealFloor()
                .credits(BalanceConfig.NODE_SUPPLY_COMBAT_CREDITS)
                .build();
    }

    /** REGION_GATE — the airlock: threat 0, no heal floor, chips only. */
    public static NodeSupplySpec regionGate() {
        return NodeSupplySpec.builder(RouteNodeType.REGION_GATE, NodeSupplySpec.EncounterKind.NONE)
                .threat(BalanceConfig.NODE_SUPPLY_GATE_THREAT)
                .bodies(0f)
                .noHealFloor()
                .credits(BalanceConfig.NODE_SUPPLY_COMBAT_CREDITS)
                .build();
    }
}
