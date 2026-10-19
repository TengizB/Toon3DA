package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.level.BossArenaGenerator;
import ge.tbegvadze.toon3d.level.CavernGenerator;
import ge.tbegvadze.toon3d.level.EventRoomGenerator;
import ge.tbegvadze.toon3d.level.GateAirlockGenerator;
import ge.tbegvadze.toon3d.level.LevelGenerator;
import ge.tbegvadze.toon3d.level.LinearCorridorGenerator;
import ge.tbegvadze.toon3d.level.MedBayGenerator;
import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.RenderConstants;
import ge.tbegvadze.toon3d.util.RouteMapConstants;

/**
 * The one place the route-map registries are populated. {@link #bootstrap()} registers every v1
 * node type and every existing generator; it is invoked once from the {@code World.create()} path
 * (wired in order-3). Later ideas append their own registrations here (or in their own bootstrap
 * hook), which is the ONLY edit needed to make a new node type or generator appear on the map.
 *
 * <p>Holds the shared registry singletons that the rest of the game reads. {@link #bootstrap()} is
 * idempotent — calling it more than once is a no-op — so repeated {@code World.create()} calls
 * (e.g. after a death-screen restart) are safe.
 */
public final class RouteRegistries {

    private static final NodeTypeRegistry         NODE_TYPES     = new NodeTypeRegistry();
    private static final GeneratorRegistry        GENERATORS     = new GeneratorRegistry();
    private static final NodeLevelProfileRegistry LEVEL_PROFILES = new NodeLevelProfileRegistry();
    private static final NodeAffixRegistry        AFFIXES        = new NodeAffixRegistry();
    private static final FacilityEventRegistry    EVENTS         = new FacilityEventRegistry();
    private static final RegionAmbienceRegistry   REGION_AMBIENCE = new RegionAmbienceRegistry();
    private static final NodeEconomicsRegistry    NODE_ECONOMICS  = new NodeEconomicsRegistry();
    private static final NodeSupplySpecRegistry   NODE_SUPPLY_SPECS = new NodeSupplySpecRegistry();
    private static boolean bootstrapped = false;
    // Per-registry latches so the three registries the headless BALANCE AUDIT reads (node types,
    // generators, node economics) can be pulled in isolation — the audit prices and walks the map
    // without booting the profile / event / ambience content. Each still registers exactly once.
    private static boolean nodeTypesRegistered     = false;
    private static boolean generatorsRegistered    = false;
    private static boolean nodeEconomicsRegistered = false;
    private static boolean affixesRegistered       = false;
    private static boolean nodeSupplySpecsRegistered = false;
    // Balance-overhaul order 6 (F15): the EVENT ledger row prices the event CATALOG, so the audit reads it too.
    private static boolean eventsRegistered        = false;

    private RouteRegistries() {}

    /** The shared node-type registry (populated on first access; registration happens exactly once). */
    public static synchronized NodeTypeRegistry nodeTypes() {
        if (!nodeTypesRegistered) {
            nodeTypesRegistered = true;
            registerNodeTypes(NODE_TYPES);
        }
        return NODE_TYPES;
    }

    /** The shared generator registry (populated on first access; registration happens exactly once). */
    public static synchronized GeneratorRegistry generators() {
        if (!generatorsRegistered) {
            generatorsRegistered = true;
            registerGenerators(GENERATORS);
        }
        return GENERATORS;
    }

    /**
     * The shared NODE EV LEDGER (new-game-balancr order 7) — every node type, affix and mystery
     * outcome with its priced threat / resources / progress. Populated on first access so the
     * headless balance audit can read the map's prices without booting the whole route subsystem.
     */
    public static synchronized NodeEconomicsRegistry nodeEconomics() {
        if (!nodeEconomicsRegistered) {
            nodeEconomicsRegistered = true;
            RouteEconomics.registerAll(NODE_ECONOMICS);
        }
        return NODE_ECONOMICS;
    }

    /**
     * The shared NODE SUPPLY SPECS (balance-overhaul order 2, S10) — what a floor of each node type
     * contains. Populated on first access so the generators, the headless balance audit and the route
     * ledger all read the same rows without booting the whole route subsystem.
     */
    public static synchronized NodeSupplySpecRegistry nodeSupplySpecs() {
        if (!nodeSupplySpecsRegistered) {
            nodeSupplySpecsRegistered = true;
            NodeSupplySpecs.registerAll(NODE_SUPPLY_SPECS);
        }
        return NODE_SUPPLY_SPECS;
    }

    /** The shared node-level-profile registry (order-3: node -&gt; floor pipeline). */
    public static NodeLevelProfileRegistry levelProfiles() {
        return LEVEL_PROFILES;
    }

    /** The shared ELITE node-affix registry (order-9; populated on first access, exactly once). */
    public static synchronized NodeAffixRegistry affixes() {
        if (!affixesRegistered) {
            affixesRegistered = true;
            registerAffixes(AFFIXES);
        }
        return AFFIXES;
    }

    /** The shared narrative EVENT registry (order-10). */
    public static synchronized FacilityEventRegistry events() {
        if (!eventsRegistered) {
            eventsRegistered = true;
            registerEvents(EVENTS);
        }
        return EVENTS;
    }

    /** The shared region-ambience / theming registry (order-10). */
    public static RegionAmbienceRegistry regionAmbience() {
        return REGION_AMBIENCE;
    }

    /** Whether {@link #bootstrap()} has already run. */
    public static boolean isBootstrapped() {
        return bootstrapped;
    }

    /**
     * Registers all v1 node types and generators into the shared registries. Idempotent: safe to
     * call repeatedly.
     */
    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        // Through the latched accessors, so a registry already pulled in by the balance audit (or by
        // any earlier caller) is never registered twice.
        nodeTypes();
        generators();
        nodeEconomics();
        nodeSupplySpecs();
        affixes();
        events();
        registerRegionAmbience(REGION_AMBIENCE);
        registerLevelProfiles(LEVEL_PROFILES, GENERATORS, AFFIXES, EVENTS);
        bootstrapped = true;
    }

    /**
     * Registers the v1 node types into the given registry. Exposed (package-private) so tests can
     * populate a fresh registry without touching the shared singleton.
     */
    static void registerNodeTypes(NodeTypeRegistry registry) {
        registry.register(NodeTypeDefinition.builder(RouteNodeType.COMBAT)
                .id("combat").displayName("HOSTILE ZONE")
                .hintLine("Standard resistance. Clear it to descend.")
                .baseWeight(RouteMapConstants.NODE_WEIGHT_COMBAT)
                .dangerTier(DangerTier.STANDARD).accentColorId("combat")
                .forced(false).levelProfileId("combat_standard").iconPainterId("icon_combat")
                .supplyPromise("RESISTANCE", "STANDARD SUPPLY")
                .build());

        registry.register(NodeTypeDefinition.builder(RouteNodeType.ELITE)
                .id("elite").displayName("ELITE HOTZONE")
                .hintLine("Heavy resistance. Elite drops on clear.")
                .baseWeight(RouteMapConstants.NODE_WEIGHT_ELITE)
                .dangerTier(DangerTier.DANGER).accentColorId("elite")
                .forced(false).levelProfileId("elite_hotzone").iconPainterId("icon_elite")
                .supplyPromise("HEAVY RESISTANCE", "WEAPON +2 LV, RARE+")
                .build());

        registry.register(NodeTypeDefinition.builder(RouteNodeType.CACHE)
                .id("cache").displayName("SUPPLY CACHE")
                .hintLine("Light resistance. A supply surplus to restock.")
                .baseWeight(RouteMapConstants.NODE_WEIGHT_CACHE)
                .dangerTier(DangerTier.CALM).accentColorId("cache")
                .forced(false).levelProfileId("supply_cache").iconPainterId("icon_cache")
                .supplyPromise("LIGHT RESISTANCE", "SUPPLIES")
                .build());

        registry.register(NodeTypeDefinition.builder(RouteNodeType.SHOP)
                .id("shop").displayName("BLACK MARKET")
                .hintLine("Spend credits on gear. Light resistance.")
                .baseWeight(RouteMapConstants.NODE_WEIGHT_SHOP)
                .dangerTier(DangerTier.CALM).accentColorId("shop")
                .forced(false).levelProfileId("shop").iconPainterId("icon_shop")
                .supplyPromise("LIGHT RESISTANCE", "FABRICATORS")
                .build());

        registry.register(NodeTypeDefinition.builder(RouteNodeType.REST)
                .id("rest").displayName("MED-BAY")
                .hintLine("Recover health and regroup. No threat.")
                .baseWeight(RouteMapConstants.NODE_WEIGHT_REST)
                .dangerTier(DangerTier.CALM).accentColorId("rest")
                .forced(false).levelProfileId("rest_medbay").iconPainterId("icon_rest")
                .build());

        registry.register(NodeTypeDefinition.builder(RouteNodeType.MYSTERY)
                .id("mystery").displayName("UNKNOWN SIGNAL")
                .hintLine("Contents unknown. Risk and reward both.")
                .baseWeight(RouteMapConstants.NODE_WEIGHT_MYSTERY)
                .dangerTier(DangerTier.GAMBLE).accentColorId("mystery")
                .forced(false).levelProfileId("mystery").iconPainterId("icon_mystery")
                .build());

        registry.register(NodeTypeDefinition.builder(RouteNodeType.EVENT)
                .id("event").displayName("DISTRESS BEACON")
                .hintLine("A narrative encounter. Choices matter.")
                .baseWeight(RouteMapConstants.NODE_WEIGHT_EVENT)
                .dangerTier(DangerTier.CALM).accentColorId("event")
                .forced(false).levelProfileId("event").iconPainterId("icon_event")
                .build());

        registry.register(NodeTypeDefinition.builder(RouteNodeType.BOSS)
                .id("boss").displayName("BOSS ARENA")
                .hintLine("A facility guardian blocks the descent.")
                .baseWeight(0f)
                .dangerTier(DangerTier.SET_PIECE).accentColorId("boss")
                .forced(true).levelProfileId("boss").iconPainterId("icon_boss")
                .build());

        registry.register(NodeTypeDefinition.builder(RouteNodeType.REGION_GATE)
                .id("region_gate").displayName("REGION GATE")
                .hintLine("The threshold to a deeper sector of the facility.")
                .baseWeight(0f)
                .dangerTier(DangerTier.SET_PIECE).accentColorId("gate")
                .forced(true).levelProfileId("region_gate").iconPainterId("icon_gate")
                .build());
    }

    /**
     * Registers the four existing generators into the given registry, wrapping each by stable id.
     * Exposed (package-private) so tests can populate a fresh registry.
     */
    static void registerGenerators(GeneratorRegistry registry) {
        // Existing generators take their seed in the constructor; the factory closes over it.
        // Config may be null for generators that ignore it (they default internally).
        registry.register(GeneratorId.ROOMS_MST, (seed, config) ->
                config != null ? new LevelGenerator(seed, config) : new LevelGenerator(seed));

        registry.register(GeneratorId.LINEAR_CORRIDOR, (seed, config) ->
                config != null ? new LinearCorridorGenerator(seed, config) : new LinearCorridorGenerator(seed));

        // CavernGenerator honours the encounter-budget scale (route-map order-7) and the order-2 supply
        // fields from config; it defaults every other generation parameter internally.
        registry.register(GeneratorId.CAVERN, (seed, config) -> new CavernGenerator(seed, config));

        // BossArenaGenerator is a bespoke, seed-driven procedural arena (boss ORDER 7): the seed drives
        // its room size, cover/alcove/lighting jitter, and fairness re-rolls. It reads only the order-2 supply
        // fields from config.
        registry.register(GeneratorId.BOSS_ARENA, (seed, config) -> new BossArenaGenerator(seed, config));

        // MedBayGenerator is the bespoke REST-node clinic (route-map order-8); seed drives its only
        // variation (alcove side + pod count). It reads only the order-2 supply fields from config and is NOT in the standard pool.
        registry.register(GeneratorId.MED_BAY, (seed, config) -> new MedBayGenerator(seed, config));

        // EventRoomGenerator is the bespoke EVENT-node chamber (route-map order-10); a small curated
        // room around one interactable. Seed drives only decal variation. NOT in the standard pool.
        registry.register(GeneratorId.EVENT_ROOM, (seed, config) -> new EventRoomGenerator(seed, config));

        // GateAirlockGenerator is the bespoke REGION_GATE ceremonial bulkhead (route-map order-10).
        // Seed drives only sparse dressing. It reads only the order-2 supply fields from config and is NOT in the standard pool.
        registry.register(GeneratorId.GATE_AIRLOCK, (seed, config) -> new GateAirlockGenerator(seed, config));
    }

    /**
     * Registers the level profiles. {@link DefaultCombatProfile} is registered under its own id AND
     * set as the fallback, so every node type still resolves to a playable combat floor. Order-7 adds
     * the two thin special profiles that mostly forward to existing systems ({@link ShopProfile},
     * {@link BossProfile}); order-8 the CALM depot/clinic ({@link CacheProfile}, {@link RestProfile});
     * order-9 the DANGER / GAMBLE pair ({@link EliteProfile}, {@link MysteryProfile}). The remaining
     * EVENT / GATE profiles land in order-10 as one {@code register(...)} line each. Exposed
     * (package-private) so tests can populate a fresh registry without touching the shared singleton.
     */
    static void registerLevelProfiles(NodeLevelProfileRegistry registry, GeneratorRegistry generators,
                                      NodeAffixRegistry affixes, FacilityEventRegistry events) {
        DefaultCombatProfile defaultProfile = new DefaultCombatProfile(generators, affixes);
        registry.register(defaultProfile);
        registry.setFallback(defaultProfile);

        // Order-7 special profiles (thin forwarders to existing systems).
        registry.register(new ShopProfile(generators));
        registry.register(new BossProfile());

        // Order-8 CALM-node profiles: the SUPPLY CACHE depot and the MED-BAY / REST clinic.
        registry.register(new CacheProfile());
        registry.register(new RestProfile());

        // Order-9 DANGER / GAMBLE profiles: the ELITE hotzone and the MYSTERY meta-profile. MYSTERY
        // delegates to other profiles, so it takes the profile registry it is being added to.
        registry.register(new EliteProfile(generators, affixes));
        registry.register(new MysteryProfile(registry, generators));

        // Order-10 NARRATIVE / THEMING profiles: the EVENT beat (rolls an event from the registry)
        // and the ceremonial REGION_GATE bulkhead (a calm airlock floor). Both replace the
        // DefaultCombatProfile fallback for their node types. The region-entry ANNOUNCE + theming is
        // applied generically by World when the descent crosses into a new region (RegionAmbience),
        // so it fires on every region transition — boss or gate — not just on a gate node.
        registry.register(new EventProfile(events));
        registry.register(new GateProfile());
    }

    /**
     * Registers the v1 narrative EVENT catalog (order-10). Each event is a small curated beat with a
     * title, narrative lines, and 1-3 choices; every choice applies through EXISTING effect systems
     * (heal / ammo / xp / reveal / next-floor budget) via a typed {@link EventEffect} bundle, so there
     * is NO bespoke per-event code path — adding an event is one {@link FacilityEvent} row here.
     * Exposed (package-private) so tests can populate a fresh registry.
     */
    static void registerEvents(FacilityEventRegistry registry) {
        FacilityEvents.registerAll(registry);
    }

    /**
     * Registers the region-ambience / theming catalog (order-10; region E by balance-overhaul order 6):
     * five acts (OUTER FACILITY, RESEARCH WING, REACTOR DEPTHS, THE BREACH, THE CORE) keyed by theme id, each carrying its crest, overlay
     * tint, wall palette, enemy faction, ambience hooks, boss, and gate flavour. NOTHING hard-codes
     * the list — a new region is one row. Exposed (package-private) so tests can populate a fresh
     * registry without touching the shared singleton.
     */
    static void registerRegionAmbience(RegionAmbienceRegistry registry) {
        RegionAmbience outerFacility = RegionAmbience.builder(RouteMapConstants.REGION_A_THEME)
                .displayName(RouteMapConstants.REGION_A_NAME)
                .crestPainterId("crest_outer_facility")
                .wallPalette(RouteMapConstants.REGION_A_WALL_PALETTE)
                .enemyFactionId(RouteMapConstants.REGION_A_FACTION)
                .lightingMoodId(RouteMapConstants.REGION_A_LIGHTING)
                .musicId(RouteMapConstants.REGION_A_MUSIC)
                .redAlertFrequency(RouteMapConstants.REGION_A_RED_ALERT_FREQUENCY)
                .bossId(RouteMapConstants.REGION_A_BOSS)
                .gateFlavour(RouteMapConstants.REGION_A_GATE_FLAVOUR)
                .build();

        RegionAmbience researchWing = RegionAmbience.builder(RouteMapConstants.REGION_B_THEME)
                .displayName(RouteMapConstants.REGION_B_NAME)
                .crestPainterId("crest_research_wing")
                .wallPalette(RouteMapConstants.REGION_B_WALL_PALETTE)
                .enemyFactionId(RouteMapConstants.REGION_B_FACTION)
                .lightingMoodId(RouteMapConstants.REGION_B_LIGHTING)
                .musicId(RouteMapConstants.REGION_B_MUSIC)
                .redAlertFrequency(RouteMapConstants.REGION_B_RED_ALERT_FREQUENCY)
                .bossId(RouteMapConstants.REGION_B_BOSS)
                .gateFlavour(RouteMapConstants.REGION_B_GATE_FLAVOUR)
                .build();

        RegionAmbience reactorDepths = RegionAmbience.builder(RouteMapConstants.REGION_C_THEME)
                .displayName(RouteMapConstants.REGION_C_NAME)
                .crestPainterId("crest_reactor_depths")
                .wallPalette(RouteMapConstants.REGION_C_WALL_PALETTE)
                .enemyFactionId(RouteMapConstants.REGION_C_FACTION)
                .lightingMoodId(RouteMapConstants.REGION_C_LIGHTING)
                .musicId(RouteMapConstants.REGION_C_MUSIC)
                .redAlertFrequency(RouteMapConstants.REGION_C_RED_ALERT_FREQUENCY)
                .bossId(RouteMapConstants.REGION_C_BOSS)
                .gateFlavour(RouteMapConstants.REGION_C_GATE_FLAVOUR)
                .build();

        RegionAmbience theBreach = RegionAmbience.builder(RouteMapConstants.REGION_D_THEME)
                .displayName(RouteMapConstants.REGION_D_NAME)
                .crestPainterId("crest_the_breach")
                .wallPalette(RouteMapConstants.REGION_D_WALL_PALETTE)
                .enemyFactionId(RouteMapConstants.REGION_D_FACTION)
                .lightingMoodId(RouteMapConstants.REGION_D_LIGHTING)
                .musicId(RouteMapConstants.REGION_D_MUSIC)
                .redAlertFrequency(RouteMapConstants.REGION_D_RED_ALERT_FREQUENCY)
                .bossId(RouteMapConstants.REGION_D_BOSS)
                .gateFlavour(RouteMapConstants.REGION_D_GATE_FLAVOUR)
                .build();

        RegionAmbience theCore = RegionAmbience.builder(RouteMapConstants.REGION_E_THEME)
                .displayName(RouteMapConstants.REGION_E_NAME)
                .crestPainterId("crest_the_core")
                .wallPalette(RouteMapConstants.REGION_E_WALL_PALETTE)
                .enemyFactionId(RouteMapConstants.REGION_E_FACTION)
                .lightingMoodId(RouteMapConstants.REGION_E_LIGHTING)
                .musicId(RouteMapConstants.REGION_E_MUSIC)
                .redAlertFrequency(RouteMapConstants.REGION_E_RED_ALERT_FREQUENCY)
                .bossId(RouteMapConstants.REGION_E_BOSS)
                .gateFlavour(RouteMapConstants.REGION_E_GATE_FLAVOUR)
                .build();

        registry.register(outerFacility);
        registry.register(researchWing);
        registry.register(reactorDepths);
        registry.register(theBreach);
        registry.register(theCore);
        // The run is finite (balance-overhaul order 6): an unnamed theme falls back to the final act.
        registry.setFallback(theCore);
    }

    /**
     * Registers the v1 ELITE affix catalog (order-9). Each affix is a bundle of TYPED modifiers routed
     * through existing enemy / hazard / balance systems — adding a new affix is one register() line
     * here, no bespoke code path. Exposed (package-private) so tests can populate a fresh registry.
     */
    static void registerAffixes(NodeAffixRegistry registry) {
        // IRRADIATED — extra radioactive barrels + a hazard-pool read.
        registry.register(NodeAffixDefinition.builder(RouteMapConstants.AFFIX_IRRADIATED_ID, "IRRADIATED")
                .ruleLine(RouteMapConstants.AFFIX_IRRADIATED_RULE_LINE)
                .radioactiveBarrelWeightBonus(RouteMapConstants.AFFIX_IRRADIATED_BARREL_WEIGHT_BONUS)
                .extraRadioactiveBarrels(RouteMapConstants.AFFIX_IRRADIATED_EXTRA_BARRELS)
                .build());

        // OVERCLOCKED — enemies hit harder (approximated as more depth Threat spent). The raised threat
        // is PAID FOR by a richer vault (R-RISK-PREMIUM, order 7): danger must never be a sucker bet.
        registry.register(NodeAffixDefinition.builder(RouteMapConstants.AFFIX_OVERCLOCKED_ID, "OVERCLOCKED")
                .ruleLine(RouteMapConstants.AFFIX_OVERCLOCKED_RULE_LINE)
                .budgetScaleMultiplier(RouteMapConstants.AFFIX_OVERCLOCKED_BUDGET_MULT)
                .extraVaultAmmoBoxes(RouteMapConstants.AFFIX_OVERCLOCKED_VAULT_AMMO_BOXES)
                .build());

        // SWARM — the one exception to fewer-bodies: many chaff, a crowd-control test. The biggest
        // threat multiplier in the catalog, so it carries the biggest vault premium.
        registry.register(NodeAffixDefinition.builder(RouteMapConstants.AFFIX_SWARM_ID, "SWARM")
                .ruleLine(RouteMapConstants.AFFIX_SWARM_RULE_LINE)
                .budgetScaleMultiplier(RouteMapConstants.AFFIX_SWARM_BUDGET_MULT)
                .extraVaultAmmoBoxes(RouteMapConstants.AFFIX_SWARM_VAULT_AMMO_BOXES)
                .build());

        // FORTIFIED — armour + more cover columns (a slugfest).
        registry.register(NodeAffixDefinition.builder(RouteMapConstants.AFFIX_FORTIFIED_ID, "FORTIFIED")
                .ruleLine(RouteMapConstants.AFFIX_FORTIFIED_RULE_LINE)
                .extraColumns(RouteMapConstants.AFFIX_FORTIFIED_EXTRA_COLUMNS)
                .extraArmourPickups(RouteMapConstants.AFFIX_FORTIFIED_EXTRA_ARMOUR)
                .build());

        // VOLATILE — extra explosive barrels: the arena itself is a weapon for both sides.
        registry.register(NodeAffixDefinition.builder(RouteMapConstants.AFFIX_VOLATILE_ID, "VOLATILE")
                .ruleLine(RouteMapConstants.AFFIX_VOLATILE_RULE_LINE)
                .extraExplosiveBarrels(RouteMapConstants.AFFIX_VOLATILE_EXTRA_BARRELS)
                .build());

        // ---- FLOOR TWISTS (floor-twists) — FLOOR_TWIST scope: only ever on a COMBAT node (R1) ----
        // A twist is a bundle of the same typed modifiers. Adding a fourth twist built from these kinds is
        // ONE row here + its NodeEconomics row + its SECTION 27 numbers + its rule line (+ a TeachingTopic).

        // BLACKOUT — information for perception: no mini-map, the floor dark (exit lit), and dormant
        // enemies wake only from much closer. Priced threat-neutral (R-TWIST T2).
        registry.register(NodeAffixDefinition.builder(RouteMapConstants.TWIST_BLACKOUT_ID,
                        RouteMapConstants.TWIST_BLACKOUT_NAME)
                .scope(AffixScope.FLOOR_TWIST)
                .weight(BalanceConfig.TWIST_WEIGHT_BLACKOUT)
                .minDepth(BalanceConfig.TWIST_MIN_DEPTH_BLACKOUT)
                .ruleLine(RouteMapConstants.TWIST_BLACKOUT_RULE_LINE)
                .minimapDisabled(true)
                .lightMultiplier(RenderConstants.TWIST_BLACKOUT_LIGHT_MULTIPLIER)
                .wakePerception(BalanceConfig.TWIST_BLACKOUT_WAKE_RADIUS_TILES,
                        BalanceConfig.TWIST_BLACKOUT_WAKE_SIGHT_TILES)
                .build());

        // SALVAGE — more resistance, more supply lying around (the SWARM budget override + scattered loot).
        registry.register(NodeAffixDefinition.builder(RouteMapConstants.TWIST_SALVAGE_ID,
                        RouteMapConstants.TWIST_SALVAGE_NAME)
                .scope(AffixScope.FLOOR_TWIST)
                .weight(BalanceConfig.TWIST_WEIGHT_SALVAGE)
                .minDepth(BalanceConfig.TWIST_MIN_DEPTH_SALVAGE)
                .ruleLine(RouteMapConstants.TWIST_SALVAGE_RULE_LINE)
                .budgetScaleMultiplier(BalanceConfig.TWIST_SALVAGE_BUDGET_MULT)
                .scatteredAmmoBoxes(BalanceConfig.TWIST_SALVAGE_AMMO_BOXES)
                .extraStims(BalanceConfig.TWIST_SALVAGE_STIMS)
                .extraArmourShards(BalanceConfig.TWIST_SALVAGE_ARMOUR_SHARDS)
                .build());

        // HUNTED — tempo: after TWIST_HUNTED_ARRIVAL_TURNS an Iron Stalker is printed where you arrived
        // and walks your trail. The hunter is named, not imported (route never depends on enemy).
        registry.register(NodeAffixDefinition.builder(RouteMapConstants.TWIST_HUNTED_ID,
                        RouteMapConstants.TWIST_HUNTED_NAME)
                .scope(AffixScope.FLOOR_TWIST)
                .weight(BalanceConfig.TWIST_WEIGHT_HUNTED)
                .minDepth(BalanceConfig.TWIST_MIN_DEPTH_HUNTED)
                .ruleLine(RouteMapConstants.TWIST_HUNTED_RULE_LINE)
                .hunterEnemyTypeName(RouteMapConstants.TWIST_HUNTED_ENEMY_TYPE_NAME)
                .build());
    }
}
