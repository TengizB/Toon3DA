package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.level.BossArenaGenerator;
import ge.tbegvadze.toon3d.level.CavernGenerator;
import ge.tbegvadze.toon3d.level.LevelGenerator;
import ge.tbegvadze.toon3d.level.LinearCorridorGenerator;
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
        registerLevelProfiles(LEVEL_PROFILES, GENERATORS);
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

        // CavernGenerator has no config-aware constructor — it defaults internally.
        registry.register(GeneratorId.CAVERN, (seed, config) -> new CavernGenerator(seed));

        // BossArenaGenerator is a bespoke fixed arena; it ignores both seed and config.
        registry.register(GeneratorId.BOSS_ARENA, (seed, config) -> new BossArenaGenerator());
    }

    /**
     * Registers the order-3 level profiles. Only the {@link DefaultCombatProfile} ships now; it is
     * registered under its own id AND set as the fallback, so every node type (special profiles land
     * in order-7) resolves to a playable combat floor. Exposed (package-private) so tests can populate
     * a fresh registry without touching the shared singleton.
     */
    static void registerLevelProfiles(NodeLevelProfileRegistry registry, GeneratorRegistry generators) {
        DefaultCombatProfile defaultProfile = new DefaultCombatProfile(generators);
        registry.register(defaultProfile);
        registry.setFallback(defaultProfile);
    }
}
