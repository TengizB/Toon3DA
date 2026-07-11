package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.level.BossArenaGenerator;
import ge.tbegvadze.toon3d.level.CavernGenerator;
import ge.tbegvadze.toon3d.level.EventRoomGenerator;
import ge.tbegvadze.toon3d.level.GateAirlockGenerator;
import ge.tbegvadze.toon3d.level.LevelGenerator;
import ge.tbegvadze.toon3d.level.LinearCorridorGenerator;
import ge.tbegvadze.toon3d.level.MedBayGenerator;
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
    private static boolean bootstrapped = false;

    private RouteRegistries() {}

    /** The shared node-type registry. */
    public static NodeTypeRegistry nodeTypes() {
        return NODE_TYPES;
    }

    /** The shared generator registry. */
    public static GeneratorRegistry generators() {
        return GENERATORS;
    }

    /** The shared node-level-profile registry (order-3: node -&gt; floor pipeline). */
    public static NodeLevelProfileRegistry levelProfiles() {
        return LEVEL_PROFILES;
    }

    /** The shared ELITE node-affix registry (order-9). */
    public static NodeAffixRegistry affixes() {
        return AFFIXES;
    }

    /** The shared narrative EVENT registry (order-10). */
    public static FacilityEventRegistry events() {
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
        registerNodeTypes(NODE_TYPES);
        registerGenerators(GENERATORS);
        registerAffixes(AFFIXES);
        registerEvents(EVENTS);
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
                .build());

        registry.register(NodeTypeDefinition.builder(RouteNodeType.ELITE)
                .id("elite").displayName("ELITE HOTZONE")
                .hintLine("Heavy resistance. Elite drops on clear.")
                .baseWeight(RouteMapConstants.NODE_WEIGHT_ELITE)
                .dangerTier(DangerTier.DANGER).accentColorId("elite")
                .forced(false).levelProfileId("elite_hotzone").iconPainterId("icon_elite")
                .build());

        registry.register(NodeTypeDefinition.builder(RouteNodeType.CACHE)
                .id("cache").displayName("SUPPLY CACHE")
                .hintLine("Guaranteed ammo + medkit. Light resistance.")
                .baseWeight(RouteMapConstants.NODE_WEIGHT_CACHE)
                .dangerTier(DangerTier.CALM).accentColorId("cache")
                .forced(false).levelProfileId("supply_cache").iconPainterId("icon_cache")
                .build());

        registry.register(NodeTypeDefinition.builder(RouteNodeType.SHOP)
                .id("shop").displayName("BLACK MARKET")
                .hintLine("Spend credits on gear. No threat.")
                .baseWeight(RouteMapConstants.NODE_WEIGHT_SHOP)
                .dangerTier(DangerTier.CALM).accentColorId("shop")
                .forced(false).levelProfileId("shop").iconPainterId("icon_shop")
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

        // CavernGenerator honours only the encounter-budget scale from config (route-map order-7);
        // it defaults every other generation parameter internally.
        registry.register(GeneratorId.CAVERN, (seed, config) ->
                config != null ? new CavernGenerator(seed, config.enemyBudgetScale) : new CavernGenerator(seed));

        // BossArenaGenerator is a bespoke, seed-driven procedural arena (boss ORDER 7): the seed drives
        // its room size, cover/alcove/lighting jitter, and fairness re-rolls. It ignores config.
        registry.register(GeneratorId.BOSS_ARENA, (seed, config) -> new BossArenaGenerator(seed));

        // MedBayGenerator is the bespoke REST-node clinic (route-map order-8); seed drives its only
        // variation (alcove side + pod count). It ignores config and is NOT in the standard pool.
        registry.register(GeneratorId.MED_BAY, (seed, config) -> new MedBayGenerator(seed));

        // EventRoomGenerator is the bespoke EVENT-node chamber (route-map order-10); a small curated
        // room around one interactable. Seed drives only decal variation. NOT in the standard pool.
        registry.register(GeneratorId.EVENT_ROOM, (seed, config) -> new EventRoomGenerator(seed));

        // GateAirlockGenerator is the bespoke REGION_GATE ceremonial bulkhead (route-map order-10).
        // Seed drives only sparse dressing. It ignores config and is NOT in the standard pool.
        registry.register(GeneratorId.GATE_AIRLOCK, (seed, config) -> new GateAirlockGenerator(seed));
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
        DefaultCombatProfile defaultProfile = new DefaultCombatProfile(generators);
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
     * Registers the v1 region-ambience / theming catalog (order-10): four acts (OUTER FACILITY,
     * RESEARCH WING, REACTOR DEPTHS, THE BREACH) keyed by theme id, each carrying its crest, overlay
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

        registry.register(outerFacility);
        registry.register(researchWing);
        registry.register(reactorDepths);
        registry.register(theBreach);
        // THE BREACH is the endless cycle's identity — fall back to it for any unnamed deeper theme.
        registry.setFallback(theBreach);
    }

    /**
     * Registers the v1 ELITE affix catalog (order-9). Each affix is a bundle of TYPED modifiers routed
     * through existing enemy / hazard / balance systems — adding a new affix is one register() line
     * here, no bespoke code path. Exposed (package-private) so tests can populate a fresh registry.
     */
    static void registerAffixes(NodeAffixRegistry registry) {
        // IRRADIATED — extra radioactive barrels + a hazard-pool read.
        registry.register(NodeAffixDefinition.builder(RouteMapConstants.AFFIX_IRRADIATED_ID, "IRRADIATED")
                .radioactiveBarrelWeightBonus(RouteMapConstants.AFFIX_IRRADIATED_BARREL_WEIGHT_BONUS)
                .extraRadioactiveBarrels(RouteMapConstants.AFFIX_IRRADIATED_EXTRA_BARRELS)
                .build());

        // OVERCLOCKED — enemies hit harder (approximated as more depth Threat spent).
        registry.register(NodeAffixDefinition.builder(RouteMapConstants.AFFIX_OVERCLOCKED_ID, "OVERCLOCKED")
                .budgetScaleMultiplier(RouteMapConstants.AFFIX_OVERCLOCKED_BUDGET_MULT)
                .build());

        // SWARM — the one exception to fewer-bodies: many chaff, a crowd-control test.
        registry.register(NodeAffixDefinition.builder(RouteMapConstants.AFFIX_SWARM_ID, "SWARM")
                .budgetScaleMultiplier(RouteMapConstants.AFFIX_SWARM_BUDGET_MULT)
                .build());

        // FORTIFIED — armour + more cover columns (a slugfest).
        registry.register(NodeAffixDefinition.builder(RouteMapConstants.AFFIX_FORTIFIED_ID, "FORTIFIED")
                .extraColumns(RouteMapConstants.AFFIX_FORTIFIED_EXTRA_COLUMNS)
                .extraArmourPickups(RouteMapConstants.AFFIX_FORTIFIED_EXTRA_ARMOUR)
                .build());

        // VOLATILE — extra explosive barrels: the arena itself is a weapon for both sides.
        registry.register(NodeAffixDefinition.builder(RouteMapConstants.AFFIX_VOLATILE_ID, "VOLATILE")
                .extraExplosiveBarrels(RouteMapConstants.AFFIX_VOLATILE_EXTRA_BARRELS)
                .build());
    }
}
