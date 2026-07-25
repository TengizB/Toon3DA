package ge.tbegvadze.toon3d.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.Viewport;
import ge.tbegvadze.toon3d.door.DoorManager;
import ge.tbegvadze.toon3d.enemy.EnemyManager;
import ge.tbegvadze.toon3d.entity.*;
import ge.tbegvadze.toon3d.hazard.ExplosiveBarrelManager;
import ge.tbegvadze.toon3d.hazard.HazardManager;
import ge.tbegvadze.toon3d.hud.HudRenderer;
import ge.tbegvadze.toon3d.input.PlayerController;
import ge.tbegvadze.toon3d.input.touch.TouchAction;
import ge.tbegvadze.toon3d.input.touch.TouchControllerRenderer;
import ge.tbegvadze.toon3d.input.touch.TouchInputState;
import ge.tbegvadze.toon3d.item.GroundItem;
import ge.tbegvadze.toon3d.item.Inventory;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.entity.boss.Boss;
import ge.tbegvadze.toon3d.entity.boss.BossAttackPattern;
import ge.tbegvadze.toon3d.entity.boss.CorruptorPhase1Pattern;
import ge.tbegvadze.toon3d.entity.boss.CorruptorPhase2Pattern;
import ge.tbegvadze.toon3d.entity.boss.HellBaronPhase1Pattern;
import ge.tbegvadze.toon3d.entity.boss.HellBaronPhase2Pattern;
import ge.tbegvadze.toon3d.entity.boss.HunterKillerPattern;
import ge.tbegvadze.toon3d.level.BossArenaGenerator;
import ge.tbegvadze.toon3d.level.BossArenaLayout;
import ge.tbegvadze.toon3d.level.CavernGenerator;
import ge.tbegvadze.toon3d.level.ILevelGenerator;
import ge.tbegvadze.toon3d.level.LevelGenConfig;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.level.LevelGenerator;
import ge.tbegvadze.toon3d.level.LinearCorridorGenerator;
import ge.tbegvadze.toon3d.level.LevelLoader;
import ge.tbegvadze.toon3d.level.RoomBlueprints;
import ge.tbegvadze.toon3d.level.StartGameLevelGenerator;
import ge.tbegvadze.toon3d.level.WeaponSpawnPoint;
import ge.tbegvadze.toon3d.render.BossHudRenderer;
import ge.tbegvadze.toon3d.progression.LevelUpOverlayRenderer;
import ge.tbegvadze.toon3d.progression.PlayerProgress;
import ge.tbegvadze.toon3d.progression.PlayerStats;
import ge.tbegvadze.toon3d.progression.UpgradeCard;
import ge.tbegvadze.toon3d.progression.UpgradeCardDeck;
import ge.tbegvadze.toon3d.progression.Attribute;
import ge.tbegvadze.toon3d.render.*;
import ge.tbegvadze.toon3d.render.WeaponInspectOverlayRenderer;
import ge.tbegvadze.toon3d.render.tilesetgfx.EnvironmentTextureSet;
import ge.tbegvadze.toon3d.render.tilesetgfx.TilesetGfxBootstrap;
import ge.tbegvadze.toon3d.route.AmmoCacheRequest;
import ge.tbegvadze.toon3d.route.EnemyBudgetOverride;
import ge.tbegvadze.toon3d.route.EventChoice;
import ge.tbegvadze.toon3d.route.EventEffect;
import ge.tbegvadze.toon3d.route.FacilityEvent;
import ge.tbegvadze.toon3d.route.FloorEffects;
import ge.tbegvadze.toon3d.route.GuaranteedContent;
import ge.tbegvadze.toon3d.route.GuaranteedContentStamper;
import ge.tbegvadze.toon3d.route.LevelPlan;
import ge.tbegvadze.toon3d.route.Placement;
import ge.tbegvadze.toon3d.route.NodeLevelProfile;
import ge.tbegvadze.toon3d.route.NodeTypeDefinition;
import ge.tbegvadze.toon3d.route.RegionAmbience;
import ge.tbegvadze.toon3d.route.RegionPlan;
import ge.tbegvadze.toon3d.route.RegionSpec;
import ge.tbegvadze.toon3d.route.RouteMap;
import ge.tbegvadze.toon3d.route.RouteMapGenerator;
import ge.tbegvadze.toon3d.route.RouteNode;
import ge.tbegvadze.toon3d.route.RouteNodeType;
import ge.tbegvadze.toon3d.route.RouteRegistries;
import ge.tbegvadze.toon3d.shop.DefaultShopOfferSource;
import ge.tbegvadze.toon3d.shop.ShopAbilityService;
import ge.tbegvadze.toon3d.shop.ShopContext;
import ge.tbegvadze.toon3d.shop.ShopEffectApplier;
import ge.tbegvadze.toon3d.shop.ShopEffectRouter;
import ge.tbegvadze.toon3d.shop.ShopRoller;
import ge.tbegvadze.toon3d.shop.ShopEntry;
import ge.tbegvadze.toon3d.shop.ShopSupplyService;
import ge.tbegvadze.toon3d.shop.ShopTransaction;
import ge.tbegvadze.toon3d.shop.ShopWeaponService;
import ge.tbegvadze.toon3d.shop.VendingMachine;
import ge.tbegvadze.toon3d.status.StatusEffectController;
import ge.tbegvadze.toon3d.tileset.TilesetRegistries;
import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.BossBalance;
import ge.tbegvadze.toon3d.util.BossStats;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameBalance;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.LevelGenConstants;
import ge.tbegvadze.toon3d.util.StatsStore;
import ge.tbegvadze.toon3d.util.ItemConstants;
import ge.tbegvadze.toon3d.util.RenderConstants;
import ge.tbegvadze.toon3d.util.RouteMapConstants;
import ge.tbegvadze.toon3d.util.ProgressionConstants;
import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.util.EnemyConstants;

public class World implements Renderable, Disposable, LevelTransitionListener {

    private enum RunPhase { PLAYING, FADING_OUT, FADING_IN, LEVEL_UP_OVERLAY, DEAD, INVENTORY_OPEN, WEAPON_INSPECT, SHOP_OPEN, ROUTE_SELECT, EVENT_CHOICE }

    // -------------------------------------------------------------------------
    // Run-persistent resources — kept alive across all floor transitions
    // -------------------------------------------------------------------------
    private final long                   runSeed;
    private final WeaponRoller           weaponRoller;
    private final Player                 player;
    private final PlayerInventory        inventory;
    private final GameState              gameState;
    private final HudState               hudState;
    private final WeaponHudRenderer      weaponHudRenderer;
    private final HudRenderer            hudRenderer;
    private final ImpactEffectSystem     impactEffectSystem;
    private final ImpactEffectRenderer   impactEffectRenderer;
    private final FadeOverlayRenderer    fadeOverlayRenderer;
    private final EventTextSystem        eventTextSystem;
    private final EventTextRenderer      eventTextRenderer;
    private final HitVignetteRenderer    hitVignetteRenderer;
    private final GuardShieldRenderer    guardShieldRenderer;
    // Run-persistent ability visual feedback — no GPU resources, no dispose needed
    private final AbilityFeedback        abilityFeedback;
    private final PlayerProgress         playerProgress;
    private final LevelUpOverlayRenderer levelUpOverlayRenderer;
    /** Draws the budget-equal upgrade cards offered on each level-up (idea 5: build diversity). */
    private final UpgradeCardDeck        upgradeCardDeck;
    /** The cards currently on offer; refilled by the deck when a level-up begins. */
    private final UpgradeCard[]          offeredCards = new UpgradeCard[GameBalance.LEVEL_UP_CARDS_OFFERED];
    private int                          offeredCardCount = 0;

    // Touch controller — null on desktop (platform-gated to touch screens)
    private TouchInputState         touchInputState;
    private TouchControllerRenderer touchControllerRenderer;
    private Viewport                gameViewport;
    private final Vector2           cardTouchPosition = new Vector2();

    // Route-map (order-6) pointer tracking: distinguishes a tap from a vertical pan drag on the
    // FACILITY NAV console. Reset on each present(); consumed by handleRouteSelectTouch().
    private boolean routePointerDown;
    private boolean routePointerDragging;
    private float   routePointerStartY;
    private float   routePointerLastY;

    // -------------------------------------------------------------------------
    // Level-dependent resources — rebuilt on every floor descent
    // -------------------------------------------------------------------------
    private Level                  level;
    private FloorCeilingRenderer   floorCeilingRenderer;
    private DoorManager            doorManager;
    private WallRenderer           wallRenderer;
    private PropRenderer           propRenderer;
    // TILESET SYSTEM (order-6): per-level texture supply realized from the level's palette; owned here,
    // set on the wall/prop renderers each level, disposed on level teardown (leak-free across transitions).
    private EnvironmentTextureSet  environmentTextureSet;
    private EnemyRenderer          enemyRenderer;
    private LevelRenderer          levelRenderer;
    private EnemyManager           enemyManager;
    private AbilityResolver        abilityResolver;
    private EnemyAttackEffectSystem enemyAttackEffectSystem;
    private ExplosiveBarrelManager explosiveBarrelManager;
    private HazardManager          hazardManager;
    // Held so the per-floor HazardManager rebuild can re-wire the incinerator's tile-ignition sink.
    private Incinerator            incinerator;
    private TickEventBus           tickEventBus;
    private PlayerController       playerController;
    // Boss encounter — null on non-boss floors
    private BossFloorController    bossFloorController;
    private BossHudRenderer        bossHudRenderer;

    // -------------------------------------------------------------------------
    // Transition state
    // -------------------------------------------------------------------------
    private RunPhase runPhase         = RunPhase.PLAYING;
    private float    fadeTimerSeconds = 0f;
    private int      currentDepth     = RenderConstants.STARTING_DEPTH;

    // -------------------------------------------------------------------------
    // Branching route map (route-map order-3) — the node->floor pipeline. The map is generated once
    // when the run leaves the staging room and drives which generator/config builds each next floor.
    // Null on file-loaded / test worlds, which fall back to the depth-driven default selection.
    // -------------------------------------------------------------------------
    private final RouteMapGenerator routeMapGenerator;
    private final RouteMapOverlay   routeMapOverlay;
    /** The "FACILITY NAV" console visual (order-4). Drawn while runPhase == ROUTE_SELECT. */
    private final RouteMapOverlayRenderer routeMapOverlayRenderer;
    private RouteMap                routeMap;
    /** The plan the map was last built from; grows as endless bands are appended. */
    private RegionPlan              routePlan;
    /** The node the player committed to; the next floor is built from it. Null before the first commit. */
    private RouteNode               pendingNode;
    /**
     * TEMPORARY TESTING ({@link RouteMapConstants#BOSS_TEST_ALWAYS_CLICKABLE}): set when a BOSS node was
     * tapped out of turn, so the FADING_OUT completion jumps {@code currentDepth} onto the boss's real
     * (boss-floor) depth — otherwise {@code GameMath.isBossFloor()} is false and the boss never spawns.
     */
    private boolean                pendingBossTestJump;
    /**
     * Presentation / telemetry effects the last-built floor asked for (order-9): red alert, a reveal
     * sting, and the resolved MYSTERY outcome to log. Applied AFTER {@code rebuildForLevel} (which
     * resets red alert), so it survives the rebuild. Reset to {@link FloorEffects#NONE} each build.
     */
    private FloorEffects            pendingFloorEffects = FloorEffects.NONE;
    /**
     * The layout of the boss arena built for the current floor (boss ORDER 7). Captured whenever the
     * floor's generator is a {@link BossArenaGenerator} and consumed by {@code buildLevelDependentResources}
     * to place the boss and wire {@link BossFloorController}. Null on every non-boss floor.
     */
    private BossArenaLayout         pendingBossArenaLayout;

    // -------------------------------------------------------------------------
    // Narrative EVENT nodes + region theming (route-map order-10)
    // -------------------------------------------------------------------------
    /** The "FACILITY EVENT" choice overlay (order-10). Drawn while runPhase == EVENT_CHOICE. */
    private final EventChoiceOverlayRenderer eventChoiceOverlayRenderer;
    /**
     * Armed event-station tiles on the current floor ({@code {tileColumn, tileRow}} pairs copied from
     * the level, like the heal stations). When the player steps adjacent to one, the floor's event
     * opens; the station is then consumed so an event fires exactly once. Empty on non-EVENT floors.
     */
    private java.util.List<int[]>   armedEventStations = new java.util.ArrayList<>();
    /** The event id this floor hosts (from {@link LevelPlan#eventId()}), or null on a non-EVENT floor. */
    private String                  pendingEventId;
    /** The event currently shown on the EVENT_CHOICE overlay, or null when not choosing. */
    private FacilityEvent           activeEvent;
    /**
     * A one-shot BONUS added to the NEXT floor's encounter budget (order-10 EVENT effect, e.g. an
     * escort makes the next floor hotter). Folded into that floor's budget in {@code buildFloorForNode}
     * and then cleared, so it never compounds across floors.
     */
    private float                   pendingNextFloorBudgetBonus = 0f;
    /**
     * The region index whose entry was last announced, so crossing into a new region fires the
     * "ENTERING: …" beat + theming + telemetry exactly once. -1 before the first floor.
     */
    private int                     lastAnnouncedRegionIndex = -1;

    // -------------------------------------------------------------------------
    // Player stat system — persistent across floor transitions (Order 6)
    // -------------------------------------------------------------------------
    private final PlayerStats playerStats;

    // -------------------------------------------------------------------------
    // Status effect system — run-persistent; player effects cleared on floor change
    // -------------------------------------------------------------------------
    private final StatusEffectController       statusEffectController;
    private final StatusEffectVignetteRenderer statusEffectVignetteRenderer;

    // -------------------------------------------------------------------------
    // Slot-based item inventory — holds items AND ammo reserve stacks (Order 6)
    // -------------------------------------------------------------------------
    private final Inventory                itemInventory;
    private final InventoryOverlayRenderer inventoryOverlayRenderer;

    // -------------------------------------------------------------------------
    // Weapon inspect overlay — shown when player taps INSPECT on a ground weapon
    // -------------------------------------------------------------------------
    private final WeaponInspectOverlayRenderer weaponInspectOverlayRenderer;

    // -------------------------------------------------------------------------
    // Shop — UAC Fabricator vending machines (shop_order_1). The overlay is run-persistent
    // (one renderer reused every floor); the machine list is level-dependent (1-2 per floor).
    // -------------------------------------------------------------------------
    private final ShopOverlayRenderer  shopOverlayRenderer;
    /** Animated procedural billboard for the floor's machines (shop_order_6); rebuilt per floor. */
    private ShopMachineRenderer        shopMachineRenderer;
    private final ShopRoller           shopRoller = new ShopRoller(new DefaultShopOfferSource());
    /** Delivers a shop purchase's effect (weapons/supplies/abilities); consumed by the buy tap (shop_order_5). */
    private final ShopEffectApplier    shopEffectApplier;
    private java.util.List<VendingMachine> vendingMachines = new java.util.ArrayList<>();
    /** Machine the player currently faces (drives the contextual USE button); null when none. */
    private VendingMachine machineInFront = null;
    /** Machine whose shop is currently open; null unless runPhase == SHOP_OPEN. */
    private VendingMachine openMachine    = null;

    /**
     * Charged auto-doc heal-station tiles on the current floor (route-map order-8 MED-BAY). Each entry
     * is a {@code {tileColumn, tileRow}} pair; stepping adjacent to one triggers a one-time heal and
     * removes it (consumed = powered down). Refreshed from the level on every floor rebuild. Empty on
     * ordinary floors.
     */
    private java.util.List<int[]> chargedHealStations = new java.util.ArrayList<>();

    // -------------------------------------------------------------------------
    // Ground items — weapon pickups placed by LevelGenerator; rebuilt per floor
    // -------------------------------------------------------------------------
    private java.util.List<GroundItem> groundItems;

    // -------------------------------------------------------------------------
    // Start room — weapon-selection staging state; null after the room is left
    // -------------------------------------------------------------------------
    private boolean                    isStartingRoom               = false;
    private boolean                    startRoomChoiceResolved      = false;
    private java.util.List<Weapon>     startRoomWeapons             = null;
    private java.util.List<GroundItem> startRoomGroundItems         = null;
    private boolean                    startRoomMeleeChoiceResolved = false;
    private java.util.List<Weapon>     startRoomMeleeWeapons        = null;
    private java.util.List<GroundItem> startRoomMeleeGroundItems    = null;

    // -------------------------------------------------------------------------
    // Permadeath — run stats, death beat animation, and reset handshake
    // -------------------------------------------------------------------------
    private final RunStats             runStats;
    private final PersistentStats      persistentStats;
    private final DeathOverlayRenderer deathOverlayRenderer;
    private       float                deathBeatTimerSeconds  = 0f;
    private       float                deathBlinkTimerSeconds = 0f;
    private       boolean              resetRequested         = false;

    // -------------------------------------------------------------------------
    // Timing accumulators
    // -------------------------------------------------------------------------
    private float alertTimeSeconds    = 0f;
    private float facilityTimeSeconds = 0f;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Creates a new run from a seed; starts in the weapon-selection staging room. */
    public World(long runSeed) {
        this(new StartGameLevelGenerator(runSeed), runSeed);
    }

    /** Creates a World from a pre-built level (file-loaded or test). Uses a random run seed. */
    public World(Level level) {
        this(level, System.currentTimeMillis(), false, null);
    }

    public World(String levelFile) {
        this(new LevelLoader().load(levelFile), System.currentTimeMillis(), false, null);
    }

    private World(StartGameLevelGenerator startGen, long runSeed) {
        this(startGen.generate(), runSeed, true, startGen);
    }

    private World(Level initialLevel, long runSeed, boolean startRoom, StartGameLevelGenerator startRoomGen) {
        this.runSeed        = runSeed;
        this.weaponRoller   = new WeaponRoller(runSeed);
        this.isStartingRoom = startRoom;

        // Route-map subsystem (order-3). bootstrap() is idempotent, so the death->new-run rebuild is
        // safe. The map itself is generated lazily at staging-room exit (see the FADING_OUT branch).
        RouteRegistries.bootstrap();
        // Tileset art catalog (symbol/sprite-reuse order-1). Idempotent, like RouteRegistries; nothing
        // reads it yet — it registers the complete inventory of today's wall/column/prop/decal art so
        // later orders' allocator + renderers can enumerate it instead of hardcoding a sprite list.
        TilesetRegistries.bootstrap();
        // Render-side texture-generator registry (symbol/sprite-reuse order-6). Idempotent. Maps every v1
        // sprite id to the procedural code that draws it and asserts that set matches the art catalog, so
        // a level's EnvironmentTextureSet can realize just its palette's textures. Must run after the
        // catalog bootstrap above (the assertion cross-checks against it).
        TilesetGfxBootstrap.bootstrap();
        // Room-blueprint catalog (symbol/sprite-reuse order-5). Idempotent. Registers every v1 room as a
        // self-describing RoomBlueprint; nothing selects from it yet (generate() still uses the internal
        // RoomType tag until order-8), it exists so the registry seam is live and enumerable.
        RoomBlueprints.bootstrap();
        routeMapGenerator = new RouteMapGenerator(RouteRegistries.nodeTypes(), RouteRegistries.generators());
        // Order-9: supply the ELITE affix catalog so hotzones can roll a readable twist (empty pool =
        // no affix rolls). MYSTERY uses its own entry-time outcome table, not a map-gen affix.
        routeMapGenerator.setEliteAffixPool(RouteRegistries.affixes().elitePool());
        routeMapOverlay   = new RouteMapOverlay();
        routeMapOverlay.setNodeCommitListener(this::commitRouteNode);
        routeMapOverlayRenderer = new RouteMapOverlayRenderer();

        // Player faces north (toward the portal) in the start room; east otherwise.
        float initialDirectionX = startRoom ? 0f : 1f;
        float initialDirectionY = startRoom ? 1f : 0f;
        player             = new Player(findPlayerStartX(initialLevel), findPlayerStartY(initialLevel),
                                        initialDirectionX, initialDirectionY);
        inventory          = new PlayerInventory();
        gameState          = new GameState();
        hudState           = new HudState();
        impactEffectSystem = new ImpactEffectSystem();

        // Progression — lives for the entire run; not reset between floors
        playerProgress         = new PlayerProgress();
        levelUpOverlayRenderer = new LevelUpOverlayRenderer(playerProgress);
        eventChoiceOverlayRenderer = new EventChoiceOverlayRenderer();
        // Card deck seeded off the run seed so a given run's level-up offers are reproducible.
        upgradeCardDeck        = new UpgradeCardDeck(runSeed ^ 0xCA12D00D5EED1234L);

        // Event text and hit vignette — run-persistent feedback systems
        eventTextSystem     = new EventTextSystem();
        eventTextRenderer   = new EventTextRenderer(eventTextSystem);
        hitVignetteRenderer = new HitVignetteRenderer();
        abilityFeedback     = new AbilityFeedback(eventTextSystem, impactEffectSystem);
        abilityFeedback.setHealVignetteRenderer(hitVignetteRenderer);
        abilityFeedback.setLegendaryVignetteRenderer(hitVignetteRenderer);

        // Player stat system — seeded from MARINE difficulty for now; difficulty selection
        // will be wired when the run-setup screen (order_18) is implemented.
        playerStats = new PlayerStats(PlayerStats.Difficulty.MARINE);
        player.setPlayerStats(playerStats);
        // TOUGHNESS increases maxHealth without auto-healing; heal to full once at run
        // start so the player always begins with a full HP bar.
        player.applyHealing(player.getMaxHealth());

        itemInventory            = new Inventory();
        inventoryOverlayRenderer = new InventoryOverlayRenderer(itemInventory);

        // Single applier that delivers every shop purchase (shop_order_3 weapons + shop_order_4
        // supplies/abilities). The buy-tap that consumes it is wired by shop_order_5.
        shopEffectApplier = new ShopEffectRouter(
                new ShopWeaponService(weaponRoller),
                new ShopSupplyService(itemInventory),
                new ShopAbilityService(this::grantShopAbility));

        shopOverlayRenderer = new ShopOverlayRenderer();

        weaponInspectOverlayRenderer = new WeaponInspectOverlayRenderer();
        weaponInspectOverlayRenderer.setOnTake(this::resolveWeaponTake);
        weaponInspectOverlayRenderer.setOnConvertToAmmo(this::resolveWeaponConvert);
        weaponInspectOverlayRenderer.setOnEvictSlot(this::resolveWeaponEvict);
        weaponInspectOverlayRenderer.setOnClose(this::closeWeaponInspect);

        // Permadeath — run stats and death overlay
        runStats             = new RunStats();
        persistentStats      = StatsStore.load();
        deathOverlayRenderer = new DeathOverlayRenderer();

        // Wire damage listener: player damage triggers vignette flash, screen text, and stats.
        player.setPlayerDamageListener(netDamage -> {
            hitVignetteRenderer.setIntensity(1f);
            eventTextSystem.spawnDamage(netDamage);
            runStats.recordDamageTaken(netDamage);
        });

        // GUARD stance (strategy-combat-order-4): the shield-arc overlay + the directional
        // GUARDED (front, blue) / FLANKED (side-back, red) hit feedback that teaches the arc.
        guardShieldRenderer = new GuardShieldRenderer(player);
        player.setGuardHitListener((frontArc, guardedDamage, attackerWorldX, attackerWorldY) -> {
            if (frontArc) {
                eventTextSystem.spawnWithColor("GUARDED " + guardedDamage, EventTextSystem.COLOR_BLUE);
            } else {
                eventTextSystem.spawnWithColor("FLANKED " + guardedDamage, EventTextSystem.COLOR_RED);
                hitVignetteRenderer.setIntensity(1f);
            }
        });

        // Build all weapon instances and wire them to the shared systems.
        Shotgun             shotgun         = new Shotgun();
        DoubleBarrelShotgun dblShotgun      = new DoubleBarrelShotgun();
        PlasmaRifle         plasmaRifle     = new PlasmaRifle();
        Chaingun            chaingun        = new Chaingun();
        AssaultRifle        assaultRifle    = new AssaultRifle();
        Railgun             railgun         = new Railgun();
        Incinerator         incinerator     = new Incinerator();
        this.incinerator    = incinerator; // kept so per-floor hazard wiring can reach it
        ArcCannon           arcCannon       = new ArcCannon();
        GrenadeLauncher     grenadeLauncher = new GrenadeLauncher();
        float rangedMultiplier   = playerStats.getRangedDamageMultiplier();
        float accuracyMultiplier = playerStats.getAccuracyMultiplier();
        for (Weapon weapon : new Weapon[]{shotgun, dblShotgun, plasmaRifle, chaingun, assaultRifle, railgun, incinerator, arcCannon, grenadeLauncher}) {
            weapon.setEventTextSystem(eventTextSystem);
            weapon.setAmmoInventory(itemInventory);
            weapon.setRangedDamageMultiplier(rangedMultiplier);
            weapon.setPlayerAccuracyMultiplier(accuracyMultiplier);
            weaponRoller.configureRunStart(weapon);
        }

        // Melee slot — the Fist is the always-available fallback; wired at run start and never removed.
        Fist fist = new Fist();
        fist.setEventTextSystem(eventTextSystem);
        fist.setPlayerAccuracyMultiplier(accuracyMultiplier);
        weaponRoller.configureRunStart(fist);
        inventory.setMeleeWeapon(fist);

        if (startRoom) {
            // Start room: full arsenal registered for the HUD renderer, but loadout is empty
            // so the player is unarmed until they walk onto a weapon offer.
            inventory.setArsenal(java.util.List.of(shotgun, dblShotgun, plasmaRifle, chaingun,
                                                   assaultRifle, railgun, incinerator, arcCannon, grenadeLauncher));
            inventory.clearLoadout();
            // Re-set melee weapon after clearLoadout (which resets meleeSelected but not the weapon itself).
            inventory.setMeleeWeapon(fist);
            // No starting ammo — starter reserve comes bundled with the chosen weapon.
        } else {
            // Normal floor entry: equip Chaingun + Shotgun with starting ammo reserves.
            inventory.setArsenal(java.util.List.of(chaingun, shotgun, dblShotgun, plasmaRifle,
                                                   assaultRifle, railgun, incinerator, arcCannon, grenadeLauncher));
            itemInventory.tryAdd(ItemType.AMMO_BULLETS, ItemConstants.AMMO_START_BULLETS);
            itemInventory.tryAdd(ItemType.AMMO_SHELLS,  ItemConstants.AMMO_START_SHELLS);
        }

        weaponHudRenderer    = new WeaponHudRenderer(inventory.getArsenal());
        // Register the Fist so the renderer can look up its texture by class.
        // Other melee weapons are registered via registerAdditionalWeapon() when acquired.
        weaponHudRenderer.registerAdditionalWeapon(fist);
        if (startRoom) {
            // Player starts unarmed; clear the initially-selected weapon in the renderer.
            weaponHudRenderer.setEquippedWeapon(null);
        }
        // Wire the weapon slots panel so it can read loadout state and render thumbnails.
        inventoryOverlayRenderer.setPlayerInventory(inventory);
        inventoryOverlayRenderer.setWeaponHudRenderer(weaponHudRenderer);
        inventoryOverlayRenderer.setPlayerStats(playerStats);
        hudRenderer          = new HudRenderer(player, hudState);
        hudRenderer.setPlayerInventory(inventory);
        impactEffectRenderer = new ImpactEffectRenderer(impactEffectSystem);
        fadeOverlayRenderer  = new FadeOverlayRenderer();

        // Status effect system — run-persistent; controller keeps player state across floors
        statusEffectController       = new StatusEffectController();
        statusEffectController.setEventTextSystem(eventTextSystem);
        statusEffectController.setImpactEffectSystem(impactEffectSystem);
        statusEffectVignetteRenderer = new StatusEffectVignetteRenderer();

        // Build level-dependent resources for the first floor
        level = initialLevel;
        buildLevelDependentResources(initialLevel, startRoom ? startRoomGen : null);

        if (startRoom) {
            setupStartRoomWeaponOffers(startRoomGen, runSeed);
        }
    }

    // -------------------------------------------------------------------------
    // LevelTransitionListener
    // -------------------------------------------------------------------------

    @Override
    public void onDescentRequested() {
        if (isStartingRoom && !startRoomChoiceResolved) {
            if (eventTextSystem != null) eventTextSystem.spawn("ARM YOURSELF FIRST");
            return;
        }
        if (runPhase != RunPhase.PLAYING) {
            return;
        }

        // Staging-room exit: generate the route map (once) from the run seed, then present the depth-0 ->
        // depth-1 lanes through the same FACILITY NAV console every later portal uses. The player's first
        // floor is now chosen, not auto-picked.
        if (isStartingRoom) {
            if (routeMap == null) {
                routePlan = RegionPlan.defaultPlan();
                routeMap  = routeMapGenerator.generate(runSeed, routePlan);
            }
            java.util.List<RouteNode> firstPicks = routeMap.getSelectableNext();
            if (firstPicks.isEmpty()) {
                // Defensive: no legal opening move. Fall back to a straight descent.
                fadeTimerSeconds = 0f;
                runPhase = RunPhase.FADING_OUT;
                return;
            }
            routeMapOverlay.present(firstPicks);
            routeMapOverlayRenderer.present(routeMap);
            routePointerDown = false;
            routePointerDragging = false;
            runPhase = RunPhase.ROUTE_SELECT;
            return;
        }

        // Route-map-less world (file-loaded/test): there is no node choice to present. Hand straight to
        // the transition machinery, which builds the next floor from the depth-driven fallback.
        if (routeMap == null) {
            fadeTimerSeconds = 0f;
            runPhase = RunPhase.FADING_OUT;
            return;
        }

        // Normal descent: present the next-node choice. Keep the graph ahead of the player first so the
        // descent never runs out of map (endless lazy extension).
        ensureRouteExtended();
        java.util.List<RouteNode> nextPicks = routeMap.getSelectableNext();
        if (nextPicks.isEmpty()) {
            // Defensive: no legal move (should not happen once extension has run). Fall back to a
            // straight descent so the run can never soft-lock at the portal.
            fadeTimerSeconds = 0f;
            runPhase = RunPhase.FADING_OUT;
            return;
        }
        // A forced convergence (BOSS / REGION_GATE) is not really a choice; optionally skip the card.
        if (!RouteMapConstants.SHOW_FORCED_NODE_CARD && routeMap.isBossNext()) {
            commitRouteNode(nextPicks.get(0));
            return;
        }
        routeMapOverlay.present(nextPicks);
        routeMapOverlayRenderer.present(routeMap);
        routePointerDown = false;
        routePointerDragging = false;
        runPhase = RunPhase.ROUTE_SELECT;
    }

    /**
     * Forwards the current touch to the FACILITY NAV console (order-6). Polls the raw pointer and
     * classifies the gesture: a small movement is a TAP (focus a card / press ENGAGE / toggle framing),
     * a larger vertical movement is a PAN drag. All coordinates are unprojected through the game
     * viewport first (project invariant — never raw screen pixels). ENGAGE / INVALID / FOCUS taps get
     * their out-of-renderer feedback here (haptics live in the renderer; the console-thunk shake needs
     * World's ImpactEffectSystem).
     */
    private void handleRouteSelectTouch() {
        if (gameViewport == null) {
            return;
        }
        if (Gdx.input.isTouched()) {
            cardTouchPosition.set(Gdx.input.getX(), Gdx.input.getY());
            gameViewport.unproject(cardTouchPosition);
            float touchY = cardTouchPosition.y;
            if (!routePointerDown) {
                routePointerDown = true;
                routePointerDragging = false;
                routePointerStartY = touchY;
                routePointerLastY = touchY;
                routeMapOverlayRenderer.onPointerDown(cardTouchPosition.x, cardTouchPosition.y);
            } else {
                if (!routePointerDragging
                        && Math.abs(touchY - routePointerStartY) > RouteMapConstants.DRAG_START_THRESHOLD) {
                    routePointerDragging = true;
                    routePointerLastY = touchY;   // reset baseline so the pan doesn't jump on hand-off
                }
                if (routePointerDragging) {
                    routeMapOverlayRenderer.panByDrag(touchY - routePointerLastY);
                    routePointerLastY = touchY;
                }
            }
            return;
        }

        // Pointer released.
        if (routePointerDown) {
            if (routePointerDragging) {
                routeMapOverlayRenderer.onDragReleased();
            } else {
                cardTouchPosition.set(Gdx.input.getX(), Gdx.input.getY());
                gameViewport.unproject(cardTouchPosition);
                RouteMapOverlayRenderer.PointerResult result =
                        routeMapOverlayRenderer.onTap(cardTouchPosition.x, cardTouchPosition.y);
                reactToRouteTap(result);
            }
            if (touchInputState != null) {
                touchInputState.consumeTapAction();   // never leak a tap into the hidden move cluster
            }
            routePointerDown = false;
            routePointerDragging = false;
        }
    }

    /** Fires the World-side feedback for a route-console tap and honours the CANCEL back-out policy. */
    private void reactToRouteTap(RouteMapOverlayRenderer.PointerResult result) {
        switch (result) {
            case ENGAGED:
                impactEffectSystem.triggerUiThunk(RouteMapConstants.ENGAGE_THUNK_MAGNITUDE,
                                                  RouteMapConstants.ENGAGE_THUNK_SECONDS);
                break;
            case CANCEL:
                // Only reachable when ALLOW_PORTAL_BACKOUT is on: leave the map and resume PLAYING on the
                // same floor (the portal re-triggers on the next step).
                routeMapOverlay.cancel();
                routePointerDown = false;
                runPhase = RunPhase.PLAYING;
                break;
            default:
                // FOCUS_CHANGED / INVALID / FRAMING_TOGGLED / NONE — the renderer already gave the
                // in-console feedback (ring, red flash, haptic). Nothing more to do here.
                break;
        }
    }

    /**
     * Commits the player's chosen next node and hands back to the existing fade transition. Wired as
     * the {@link RouteMapOverlay} commit callback (order-3 auto-commits; order-4/order-6 drive it from
     * touch). The next floor is built from {@link #pendingNode} in the FADING_OUT completion branch.
     */
    private void commitRouteNode(RouteNode next) {
        // TEMPORARY TESTING (RouteMapConstants.BOSS_TEST_ALWAYS_CLICKABLE): a BOSS node tapped out of
        // turn is not a legal AVAILABLE next pick, so routeMap.commitTo() would reject it. Drop straight
        // into the boss arena from pendingNode without advancing the route cursor, so the tester lands
        // on the boss floor and the map is left untouched for whenever they resume the real run.
        if (RouteMapConstants.BOSS_TEST_ALWAYS_CLICKABLE
                && next.type == RouteNodeType.BOSS
                && !routeMap.getSelectableNext().contains(next)) {
            pendingNode = next;
            pendingBossTestJump = true;
            recordRouteCommit(next);
            fadeTimerSeconds = 0f;
            runPhase = RunPhase.FADING_OUT;
            return;
        }
        routeMap.commitTo(next);
        pendingNode = next;
        recordRouteCommit(next);
        fadeTimerSeconds = 0f;
        runPhase = RunPhase.FADING_OUT;
    }

    /**
     * Endless-mode safety: extend the map with the next region band once the player nears its final
     * layer, so {@link RouteMap#getSelectableNext()} always has a legal move to offer.
     */
    private void ensureRouteExtended() {
        if (routeMap != null && routePlan != null
                && routeMapGenerator.shouldExtend(routeMap, currentDepth)) {
            routePlan = routeMapGenerator.extendWithNextRegion(routeMap, routePlan);
        }
    }

    /** Appends the committed node to the run's shareable route string and logs the beat. */
    private void recordRouteCommit(RouteNode node) {
        NodeTypeDefinition definition = RouteRegistries.nodeTypes().get(node.type);
        runStats.recordRouteNode(definition.id());
        Gdx.app.log("RouteMap", "commit depth=" + node.depth + " node=" + definition.id()
                + " route=" + runStats.getRouteString());
    }

    /**
     * Builds the next floor's {@link Level}. The production path (a real run with a route map) runs the
     * node-&gt;floor pipeline off {@link #pendingNode}; a route-map-less world (file-loaded / test) uses
     * the depth-driven default selection so it still descends.
     */
    private Level buildNextFloor() {
        long seed = floorSeed(runSeed, currentDepth);
        // Default: no floor effects. buildFloorForNode overwrites this from the resolved plan (order-9).
        pendingFloorEffects = FloorEffects.NONE;
        // Cleared here and re-captured below only when this floor's generator is a boss arena (ORDER 7).
        pendingBossArenaLayout = null;
        if (routeMap == null || pendingNode == null) {
            ILevelGenerator generator = pickLevelGenerator(currentDepth, seed);
            Level built = generator.generate(currentDepth);
            captureBossArenaLayout(generator);
            return built;
        }
        return buildFloorForNode(pendingNode, currentDepth, seed);
    }

    /**
     * The node-&gt;floor pipeline (route-map order-3). Resolves the node's {@link NodeLevelProfile},
     * asks it for a {@link LevelPlan} (generator + config + guarantees), builds the level, then replays
     * the post-generation guarantees. The RAW {@code depth} is threaded straight through to
     * {@code generate(depth)} — a node changes a floor's KIND/flavour and rewards, never the depth ramp
     * the difficulty scaler reads (roguelike_order_16 invariant).
     */
    private Level buildFloorForNode(RouteNode node, int depth, long seed) {
        NodeTypeDefinition definition = RouteRegistries.nodeTypes().get(node.type);
        NodeLevelProfile   profile    = RouteRegistries.levelProfiles().getOrDefault(definition.levelProfileId());
        LevelPlan          plan       = profile.resolve(node, depth, seed);
        // Fold the node's encounter-budget override into the config the generator receives. The
        // depth ramp is still applied first inside EncounterBudgetPlanner (order-3 invariant); this
        // only scales how much of that depth budget the floor spends. A null override leaves 1.0. A
        // one-shot next-floor bonus from a previous EVENT choice (order-10, e.g. an escort) is folded
        // in on top and then consumed, so it applies to exactly this floor and never compounds.
        EnemyBudgetOverride budget = applyNextFloorBudgetBonus(plan.enemyBudget());
        pendingNextFloorBudgetBonus = 0f;
        LevelGenConfig config = applyEnemyBudget(plan.config(), budget);
        ILevelGenerator    generator  = RouteRegistries.generators().create(plan.generatorId(), seed, config);
        Level built = generator.generate(depth);
        captureBossArenaLayout(generator);
        for (GuaranteedContent guarantee : plan.guarantees()) {
            guarantee.applyTo(built);
        }
        // Owned-ammo cache (order-8): the profile declares the intent; World fulfils it here because
        // only World knows the arsenal, so a cache never dumps ammo the player can't use.
        fulfillAmmoCache(built, plan.ammoCache(), seed);
        // Presentation / telemetry effects (order-9): stash them so applyPendingFloorEffects() can run
        // AFTER rebuildForLevel (which resets red alert). A profile is headless and cannot touch these.
        pendingFloorEffects = plan.floorEffects();
        // Narrative EVENT (order-10): remember which event this floor hosts so rebuildForLevel can arm
        // it on the room's event station. Null on every non-EVENT floor.
        pendingEventId = plan.eventId();
        Gdx.app.log("RouteMap", "build depth=" + depth + " node=" + definition.id()
                + " generator=" + plan.generatorId().stableId());
        return built;
    }

    /**
     * Folds a one-shot next-floor budget bonus (route-map order-10 EVENT effect) into the plan's
     * encounter-budget override. Returns the plan's override unchanged when no bonus is pending; else a
     * scaled override that multiplies the base scale (1.0 when the plan had none) by {@code 1 + bonus}.
     * Clamped by {@link EnemyBudgetOverride}; the depth ramp is still applied first (order-3 invariant).
     */
    private EnemyBudgetOverride applyNextFloorBudgetBonus(EnemyBudgetOverride planOverride) {
        if (pendingNextFloorBudgetBonus <= 0f) {
            return planOverride;
        }
        float baseScale = planOverride != null ? planOverride.budgetScale() : 1.0f;
        return EnemyBudgetOverride.scaled(baseScale * (1f + pendingNextFloorBudgetBonus));
    }

    /**
     * Applies the last-built floor's {@link FloorEffects} (route-map order-9), which a headless
     * {@link NodeLevelProfile} cannot touch itself. Runs AFTER {@code rebuildForLevel} (which clears
     * red alert on every floor transition), so a DANGER floor's pulse survives the rebuild. Idempotent
     * for a plain floor — {@link FloorEffects#NONE} does nothing.
     */
    private void applyPendingFloorEffects() {
        FloorEffects effects = pendingFloorEffects;
        if (effects == null || !effects.hasAny()) {
            return;
        }
        if (effects.redAlertActive()) {
            gameState.redAlert = true;
            alertTimeSeconds   = 0f;
        }
        if (effects.stingText() != null && eventTextSystem != null) {
            eventTextSystem.spawnWithColor(effects.stingText(), EventTextSystem.COLOR_RED);
        }
        // The resolved-but-hidden outcome is logged so the post-run route story is complete: it annotates
        // the node token committed at route-select time (e.g. "mystery" -> "mystery:VAULT").
        if (effects.statLogToken() != null) {
            runStats.annotateLastRouteNode(effects.statLogToken());
        }
    }

    /**
     * Stamps a SUPPLY CACHE's ammo boxes across the player's OWNED ammo types (route-map order-8).
     * The boxes are distributed as evenly as possible over the owned types, falling back to ALL ammo
     * types if the player carries no ammo-using weapon (a melee-only run still gets a spread). Uses the
     * same deterministic-from-floor-seed {@link GuaranteedContentStamper} the guarantees use, so a
     * given (floor, arsenal) always lands the same boxes.
     */
    private void fulfillAmmoCache(Level targetLevel, AmmoCacheRequest request, long seed) {
        if (targetLevel == null || request == null || request.boxCount() <= 0) return;
        java.util.List<Character> ammoSymbols = collectOwnedAmmoSymbols();
        if (ammoSymbols.isEmpty()) {
            for (AmmoType ammoType : AmmoType.values()) {
                ammoSymbols.add(ammoType.getPickupTileChar());
            }
        }
        Placement placement = request.placement();
        int typeCount = ammoSymbols.size();
        int totalBoxes = request.boxCount();
        // Even distribution: the first (totalBoxes % typeCount) types get one extra box.
        for (int typeIndex = 0; typeIndex < typeCount; typeIndex++) {
            int boxesForType = totalBoxes / typeCount + (typeIndex < totalBoxes % typeCount ? 1 : 0);
            if (boxesForType <= 0) continue;
            // Salt the seed per type so the stamper's per-symbol placement stays reproducible but the
            // types don't all resolve against the same tile order.
            long typeSeed = seed ^ (0x9E3779B97F4A7C15L * (typeIndex + 1L));
            GuaranteedContentStamper.stamp(targetLevel, ammoSymbols.get(typeIndex), boxesForType,
                    placement, typeSeed);
        }
    }

    /** The distinct ammo-pickup symbols for the ammo types the player's equipped weapons consume. */
    private java.util.List<Character> collectOwnedAmmoSymbols() {
        java.util.List<Character> symbols = new java.util.ArrayList<>();
        Loadout loadout = inventory.getLoadout();
        if (loadout != null) {
            for (int slotIndex = 0; slotIndex < loadout.getSlotCount(); slotIndex++) {
                Weapon weapon = loadout.getSlot(slotIndex);
                if (weapon == null) continue;
                AmmoType ammoType = weapon.getAmmoType();
                if (ammoType == null) continue;
                char symbol = ammoType.getPickupTileChar();
                if (!symbols.contains(symbol)) symbols.add(symbol);
            }
        }
        return symbols;
    }

    /**
     * Returns the generator config to use, with the node's {@link EnemyBudgetOverride} folded in.
     * A null override leaves the config untouched; a null config with an override gets a fresh
     * default config carrying the scale (generators that ignore config still ignore it).
     */
    private LevelGenConfig applyEnemyBudget(LevelGenConfig config, EnemyBudgetOverride override) {
        if (override == null) {
            return config;
        }
        LevelGenConfig effective = config != null ? config : new LevelGenConfig();
        effective.enemyBudgetScale = override.budgetScale();
        return effective;
    }

    // -------------------------------------------------------------------------
    // Level rebuild
    // -------------------------------------------------------------------------

    private void rebuildForLevel(Level newLevel) {
        // Dispose all level-dependent GPU resources before replacing them.
        // Failing to dispose here causes a Texture/SpriteBatch leak per floor.
        floorCeilingRenderer.dispose();
        wallRenderer.dispose();
        propRenderer.dispose();
        // TILESET SYSTEM (order-6): free this floor's realized textures before the next floor rebuilds
        // its own subset — keeps GPU footprint at one level's palette, not an accumulation across floors.
        if (environmentTextureSet != null) { environmentTextureSet.dispose(); environmentTextureSet = null; }
        if (shopMachineRenderer != null) shopMachineRenderer.dispose();
        enemyRenderer.dispose();
        enemyAttackEffectSystem.dispose();
        levelRenderer.dispose();
        if (bossHudRenderer != null) { bossHudRenderer.dispose(); bossHudRenderer = null; }
        bossFloorController = null;

        // Reposition player at the new spawn point before rebuilding systems so
        // any system that reads player position during init sees the correct tile.
        level            = newLevel;
        player.positionX = findPlayerStartX(level);
        player.positionY = findPlayerStartY(level);
        player.directionX = 1f;
        player.directionY = 0f;

        // Clear player status effects on floor transition (fresh floor = clean slate).
        statusEffectController.clearPlayerEffects(player);

        buildLevelDependentResources(newLevel);

        alertTimeSeconds   = 0f;
        gameState.redAlert = false;
    }

    private void buildLevelDependentResources(Level targetLevel) {
        buildLevelDependentResources(targetLevel, null);
    }

    private void buildLevelDependentResources(Level targetLevel, StartGameLevelGenerator startRoomGen) {
        doorManager            = new DoorManager(targetLevel);
        floorCeilingRenderer   = new FloorCeilingRenderer(targetLevel);
        wallRenderer           = new WallRenderer(targetLevel, doorManager);
        propRenderer           = new PropRenderer(targetLevel, wallRenderer);
        // TILESET SYSTEM (order-6): realize ONLY this level's palette textures (subset per level, not
        // the whole library) and hand the set to the renderers. order-6 only wires the supply + dispose
        // path — the renderers still draw from their own tables until order-7 reads from the set. Built on
        // the GL thread here (level load); disposed in rebuildForLevel/dispose so transitions stay leak-free.
        environmentTextureSet  = EnvironmentTextureSet.realize(targetLevel.getPalette(),
                                                               TilesetGfxBootstrap.generators());
        wallRenderer.setEnvironmentTextureSet(environmentTextureSet);
        propRenderer.setEnvironmentTextureSet(environmentTextureSet);
        propRenderer.setWeaponTierMap(buildWeaponTierMap());
        shopMachineRenderer    = new ShopMachineRenderer(targetLevel, wallRenderer);
        shopMachineRenderer.setPropRenderer(propRenderer);
        levelRenderer          = new LevelRenderer(targetLevel, doorManager);
        enemyManager           = new EnemyManager(targetLevel, doorManager, currentDepth);
        levelRenderer.setEnemies(enemyManager.getEnemies());
        abilityResolver        = new AbilityResolver(enemyManager, eventTextSystem, player, runSeed);
        abilityResolver.setKillXpListener(xpAwarded -> playerProgress.addXp(xpAwarded));
        abilityResolver.setPlayerInventory(itemInventory);
        abilityResolver.setStatusEffectController(statusEffectController);
        abilityResolver.setAbilityFeedback(abilityFeedback);
        for (Weapon weapon : inventory.getArsenal()) {
            weapon.setAbilityResolver(abilityResolver);
        }
        MeleeWeapon meleeWeapon = inventory.getMeleeWeapon();
        if (meleeWeapon != null) {
            meleeWeapon.setAbilityResolver(abilityResolver);
        }
        explosiveBarrelManager = new ExplosiveBarrelManager(targetLevel, enemyManager, player);
        enemyRenderer          = new EnemyRenderer(enemyManager, wallRenderer);
        enemyManager.setImpactEventListener(impactEffectSystem);
        enemyManager.setKillXpListener(xpAwarded -> playerProgress.addXp(xpAwarded));
        enemyManager.setKillEventListener((nameTag, xpAwarded) -> {
            eventTextSystem.spawnWithColor(nameTag + " +" + xpAwarded + "XP", EventTextSystem.COLOR_GREEN);
            runStats.recordKill();
        });
        enemyManager.setKillCreditListener((baseReward, dungeonDepth) -> {
            int scaled = Math.round(baseReward * (1f + (dungeonDepth - 1) * GameBalance.CREDIT_DEPTH_SCALE));
            playerStats.addCredits(scaled);
            runStats.recordCreditsEarned(scaled);
        });
        // Never-softlock emergency ammo lifeline telemetry (new-game-balancr order 3, part D).
        enemyManager.setEmergencySupplyListener(runStats::recordEmergencySupplyTrigger);
        enemyManager.setPlayerFlatDamageBonus(playerProgress.getFlatDamageBonus());
        // Carry the run's accumulated STRENGTH/MARKSMANSHIP damage multipliers onto the fresh manager.
        refreshPlayerDamageMultipliers();
        enemyManager.setLoadout(inventory.getLoadout());
        enemyManager.setDropPlacedListener((tileColumn, tileRow, dropChar) ->
                propRenderer.addDynamicProp(tileColumn, tileRow, dropChar));
        explosiveBarrelManager.setImpactEventListener(impactEffectSystem);
        enemyRenderer.setPropRenderer(propRenderer);

        enemyAttackEffectSystem = new EnemyAttackEffectSystem(wallRenderer);
        enemyManager.setEnemyAttackListener(enemyAttackEffectSystem);

        enemyManager.setStatusEffectController(statusEffectController);
        enemyManager.setEventTextSystem(eventTextSystem); // "BLOCKED N" floater on Block absorption (order-3)

        // Terrain hazards (idea 4, Pillar 3) — two-sided fire/toxic chain-reaction system.
        // HazardManager holds no GPU resources, so it needs no dispose; it is rebuilt per floor.
        hazardManager = new HazardManager(targetLevel, enemyManager, statusEffectController);
        hazardManager.setExplosiveBarrelManager(explosiveBarrelManager);
        hazardManager.setHazardVisualListener(propRenderer::addDynamicProp);
        explosiveBarrelManager.setDetonationListener(hazardManager::igniteFireFromExplosion);
        // Re-wire the incinerator's tile-ignition sink to THIS floor's HazardManager (rebuilt
        // per floor). Done alongside the other per-floor hazard wiring so it survives transitions.
        if (incinerator != null) {
            incinerator.setHazardIgniteTarget(hazardManager::igniteFire);
        }
        enemyManager.setEnemyDeathHazardListener((deadType, tileColumn, tileRow, selfDestructMassive) -> {
            // Plague Hulk leaves a lingering toxic cloud where it dies (area-denial verb). A Hulk
            // that survived its self-destruct countdown to detonate (plague-hulk-self-destruct.txt)
            // leaves the MASSIVE cloud instead of the ordinary minimal one — killing it first is
            // the reward for committing the damage.
            if (deadType == EnemyType.PLAGUE_HULK) {
                int radius = selfDestructMassive
                        ? BalanceConfig.PLAGUE_HULK_SELF_DESTRUCT_TOXIC_RADIUS_TILES
                        : BalanceConfig.HAZARD_PLAGUE_HULK_DEATH_CLOUD_RADIUS;
                hazardManager.spawnToxicCloud(tileColumn, tileRow, radius);
            }
        });

        tickEventBus = new TickEventBus();
        tickEventBus.subscribe(new WeaponReloadSubscriber(inventory));
        // Hazards tick BEFORE status so a tile's burn/poison lands the same turn you stand in it.
        tickEventBus.subscribe(new HazardTickSubscriber(hazardManager));
        tickEventBus.subscribe(new StatusEffectSubscriber(statusEffectController, player, enemyManager));
        // Decrement Bulwark Rounds temp-armor turn counters each player action.
        tickEventBus.subscribe(context -> playerStats.tickTempArmor());

        // Boss encounter — wired before EnemyTurnSubscriber so the boss acts first each turn.
        bossFloorController = null;
        bossHudRenderer     = null;
        if (GameMath.isBossFloor(currentDepth) && pendingBossArenaLayout != null) {
            Boss boss = createBossForDepth(currentDepth, floorSeed(runSeed, currentDepth), pendingBossArenaLayout);
            if (boss != null) {
                enemyManager.addBoss(boss);
                bossHudRenderer     = new BossHudRenderer();
                bossHudRenderer.setBoss(boss);
                bossFloorController = new BossFloorController(boss, targetLevel, pendingBossArenaLayout, doorManager,
                        enemyManager, hazardManager, bossHudRenderer, eventTextSystem);
                tickEventBus.subscribe(bossFloorController);
            }
        }
        gameState.isBossFloor = bossFloorController != null;

        tickEventBus.subscribe(new EnemyTurnSubscriber(enemyManager, gameState));

        // Create the controller before building ground items so weapon roll generation
        // can look up arsenal weapons via findWeaponInArsenalForType().
        playerController = new PlayerController(player, targetLevel, doorManager, inventory);
        playerController.setEnemyManager(enemyManager);
        playerController.setBarrelHitTarget(explosiveBarrelManager);
        playerController.setTickEventBus(tickEventBus);
        playerController.setTransitionListener(this);
        playerController.setEventTextSystem(eventTextSystem);
        playerController.setMoveBlockedListener(impactEffectSystem::triggerBump);
        playerController.setItemInventory(itemInventory);
        playerController.setLoadout(inventory.getLoadout());
        playerController.setPlayerStats(playerStats);
        playerController.setHealUsedListener(runStats::recordHealUsed); // resource-economy telemetry (order 3)
        playerController.setAmmoPickedUpListener(runStats::recordAmmoPickedUp); // resource-economy telemetry (order 3)
        playerController.setWeaponSwitchCallback(
            () -> weaponHudRenderer.setEquippedWeapon(inventory.getEquippedWeapon()));
        playerController.setInventoryToggleCallback(this::openInventory);
        playerController.setInspectWeaponCallback(this::openWeaponInspectOverlay);
        playerController.setShopOpenCallback(this::openShop);
        if (touchInputState != null) {
            playerController.setTouchInputState(touchInputState);
        }

        // Shop machines — placed before ground items / credit chips so those seeders see the
        // '@' solid tiles and skip them. 1-2 per non-boss floor (guaranteed presence).
        seedVendingMachines(targetLevel, currentDepth);
        shopMachineRenderer.setMachines(vendingMachines);

        // Auto-doc heal stations (route-map order-8 MED-BAY): copy the tiles the level registered so
        // the per-turn adjacency check can charge/consume them. Empty on ordinary floors.
        chargedHealStations = new java.util.ArrayList<>();
        for (int[] station : targetLevel.getHealStationTiles()) {
            chargedHealStations.add(new int[]{station[0], station[1]});
        }

        // Narrative EVENT stations (route-map order-10): copy the interactable tiles the EVENT room
        // registered so the per-turn adjacency check can open the choice overlay. The floor's event id
        // is pendingEventId (set in buildFloorForNode). Empty on non-EVENT floors, so this is a no-op
        // on ordinary / file-loaded / test worlds (which never mark an event station).
        armedEventStations = new java.util.ArrayList<>();
        for (int[] station : targetLevel.getEventStationTiles()) {
            armedEventStations.add(new int[]{station[0], station[1]});
        }

        // Build ground items from weapon spawn points. Each item gets a pre-rolled
        // WeaponRoll so the compare card can show accurate stats before pickup.
        // Tier is depth-gated inside the roller (new-game-balancr order 2): a region's drops roll from
        // its band (region 1 COMMON..UNCOMMON, region 2 UNCOMMON..RARE, ...), so the arsenal the game
        // supplies keeps pace with the expected gear curve — finding better weapons is what survives.
        groundItems = new java.util.ArrayList<>();
        // THE PITY RULE: track upgrades placed this region so a region never ends starved of its curve.
        int upgradeRegionIndex = Math.max(0, currentDepth - 1) / BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE;
        runStats.enterUpgradeRegion(upgradeRegionIndex);
        GroundItem firstWeaponGroundItem = null;
        Weapon     firstWeaponBase       = null;
        for (WeaponSpawnPoint spawnPoint : targetLevel.getWeaponSpawnPoints()) {
            GroundItem groundItem = new GroundItem(spawnPoint.tileColumn, spawnPoint.tileRow,
                                                   spawnPoint.weaponItemType, 1);
            Weapon baseWeapon = playerController.findWeaponInArsenalForType(spawnPoint.weaponItemType);
            if (baseWeapon == null) {
                // Melee weapons aren't in the ranged arsenal — build a transient instance to roll from.
                baseWeapon = createMeleeWeaponForType(spawnPoint.weaponItemType);
            }
            if (baseWeapon != null) {
                groundItem.weaponRoll = weaponRoller.rollToSnapshot(baseWeapon, currentDepth);
                if (firstWeaponGroundItem == null) {
                    firstWeaponGroundItem = groundItem;
                    firstWeaponBase       = baseWeapon;
                }
                if (isWeaponUpgrade(groundItem.weaponRoll)) {
                    runStats.recordWeaponUpgradeSeen();
                }
            }
            groundItems.add(groundItem);
        }
        // On a region's LAST floor, if no in-band upgrade was placed all region, force one onto the
        // floor's first weapon drop so the run is never starved of its gear curve by RNG (the pity rule).
        boolean regionLastFloor = currentDepth % BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE == 0;
        if (regionLastFloor && runStats.regionUpgradeQuotaUnmet()
                && firstWeaponGroundItem != null && firstWeaponBase != null) {
            firstWeaponGroundItem.weaponRoll =
                    weaponRoller.rollGuaranteedUpgradeToSnapshot(firstWeaponBase, currentDepth);
            runStats.recordWeaponUpgradeSeen();
        }
        // The starting room's weapon/melee offer tiles aren't registered as ground items yet
        // (setupStartRoomWeaponOffers runs after this method returns), so reserve them here
        // to keep credit chips from landing on the same tile as a weapon offer. Every open
        // orthogonal neighbor of a vending machine is a valid interaction stand tile (see
        // findMachineFacingPlayer — the player can face the machine from any open side, not just
        // the one facingStepColumn/Row cosmetically orients the sprite toward), so all of them are
        // reserved too, keeping a credit chip from ever landing where the player must stand to shop.
        java.util.List<int[]> reservedTiles = new java.util.ArrayList<>();
        if (startRoomGen != null) {
            for (int offerIndex = 0; offerIndex < LevelGenConstants.START_ROOM_WEAPON_OFFER_COUNT; offerIndex++) {
                reservedTiles.add(new int[]{startRoomGen.getWeaponTileColumn(offerIndex),
                                            startRoomGen.getWeaponTileRow(offerIndex)});
            }
            for (int offerIndex = 0; offerIndex < LevelGenConstants.START_ROOM_MELEE_OFFER_COUNT; offerIndex++) {
                reservedTiles.add(new int[]{startRoomGen.getMeleeTileColumn(offerIndex),
                                            startRoomGen.getMeleeTileRow(offerIndex)});
            }
        }
        int[][] orthogonalStandSteps = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        for (int machineIndex = 0; machineIndex < vendingMachines.size(); machineIndex++) {
            VendingMachine machine = vendingMachines.get(machineIndex);
            for (int[] step : orthogonalStandSteps) {
                int neighborColumn = machine.tileColumn + step[0];
                int neighborRow    = machine.tileRow    + step[1];
                if (!isSolidForMachine(targetLevel, neighborColumn, neighborRow)) {
                    reservedTiles.add(new int[]{neighborColumn, neighborRow});
                }
            }
        }
        seedCreditChips(targetLevel, groundItems, currentDepth, reservedTiles);

        propRenderer.setGroundItems(groundItems);
        levelRenderer.setGroundItems(groundItems);
        playerController.setGroundItems(groundItems);
    }

    private void seedCreditChips(Level targetLevel, java.util.List<GroundItem> items, int depth,
                                  java.util.List<int[]> reservedTiles) {
        java.util.Random chipRandom = new java.util.Random();
        int chipCount = GameBalance.CREDIT_CHIPS_PER_FLOOR_MIN
                + chipRandom.nextInt(GameBalance.CREDIT_CHIPS_PER_FLOOR_MAX
                                     - GameBalance.CREDIT_CHIPS_PER_FLOOR_MIN + 1);

        java.util.List<int[]> walkableTiles = new java.util.ArrayList<>();
        for (int tileColumn = 0; tileColumn < targetLevel.getWidth(); tileColumn++) {
            for (int tileRow = 0; tileRow < targetLevel.getHeight(); tileRow++) {
                char cell = targetLevel.getCell(tileColumn, tileRow);
                if (Level.isWall(cell) || Level.isPropSolid(cell) || Level.isStairsDown(cell)) continue;
                // Never stack a credit chip onto another grid-encoded pickup (keycard, medical,
                // armour, ammo) or onto a weapon ground item already seeded into items.
                if (Level.isKeycardPickup(cell) || Level.isMedicalPickup(cell)
                        || Level.isArmourPickup(cell) || Level.isAmmoPickup(cell)) continue;
                if (isOccupiedByGroundItem(items, tileColumn, tileRow)) continue;
                if (isReservedTile(reservedTiles, tileColumn, tileRow)) continue;
                walkableTiles.add(new int[]{tileColumn, tileRow});
            }
        }

        int totalWeight = ItemConstants.CREDIT_SPAWN_WEIGHT_SMALL
                + ItemConstants.CREDIT_SPAWN_WEIGHT_MEDIUM
                + ItemConstants.CREDIT_SPAWN_WEIGHT_LARGE;
        for (int chipIndex = 0; chipIndex < chipCount && !walkableTiles.isEmpty(); chipIndex++) {
            int[] tile = walkableTiles.remove(chipRandom.nextInt(walkableTiles.size()));
            int tierRoll = chipRandom.nextInt(totalWeight);
            ItemType chipType;
            int chipAmount;
            if (tierRoll < ItemConstants.CREDIT_SPAWN_WEIGHT_SMALL) {
                chipType   = ItemType.CREDIT_SMALL;
                chipAmount = ItemConstants.CREDIT_SMALL_BASE
                        + chipRandom.nextInt(ItemConstants.CREDIT_SMALL_JITTER * 2 + 1)
                        - ItemConstants.CREDIT_SMALL_JITTER;
            } else if (tierRoll < ItemConstants.CREDIT_SPAWN_WEIGHT_SMALL
                                  + ItemConstants.CREDIT_SPAWN_WEIGHT_MEDIUM) {
                chipType   = ItemType.CREDIT_MEDIUM;
                chipAmount = ItemConstants.CREDIT_MEDIUM_BASE
                        + chipRandom.nextInt(ItemConstants.CREDIT_MEDIUM_JITTER * 2 + 1)
                        - ItemConstants.CREDIT_MEDIUM_JITTER;
            } else {
                chipType   = ItemType.CREDIT_LARGE;
                chipAmount = ItemConstants.CREDIT_LARGE_BASE
                        + chipRandom.nextInt(ItemConstants.CREDIT_LARGE_JITTER * 2 + 1)
                        - ItemConstants.CREDIT_LARGE_JITTER;
            }
            items.add(new GroundItem(tile[0], tile[1], chipType, Math.max(1, chipAmount)));
        }
    }

    /** True if any ground item (e.g. a weapon spawn) already occupies this tile. */
    private boolean isOccupiedByGroundItem(java.util.List<GroundItem> items, int tileColumn, int tileRow) {
        for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
            GroundItem item = items.get(itemIndex);
            if (item.tileColumn == tileColumn && item.tileRow == tileRow) return true;
        }
        return false;
    }

    /** True if this tile is reserved for a not-yet-seeded item (e.g. a start-room weapon offer). */
    private boolean isReservedTile(java.util.List<int[]> reservedTiles, int tileColumn, int tileRow) {
        if (reservedTiles == null) return false;
        for (int tileIndex = 0; tileIndex < reservedTiles.size(); tileIndex++) {
            int[] reservedTile = reservedTiles.get(tileIndex);
            if (reservedTile[0] == tileColumn && reservedTile[1] == tileRow) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Shop — UAC Fabricator vending machine placement (shop_order_1)
    // -------------------------------------------------------------------------

    /**
     * Places 1-2 vending machines on the floor (guaranteed presence on EVERY floor, including the
     * weapon-selection starting room and boss arenas). Each machine occupies a wall-mounted,
     * reachable, non-softlocking floor tile that is stamped solid ('@') and registered as a
     * billboard. Runs after enemies exist (so machine tiles dodge enemies) and before
     * ground-item/credit-chip seeding (so those seeders skip the solid tile).
     */
    private void seedVendingMachines(Level targetLevel, int depth) {
        vendingMachines = new java.util.ArrayList<>();
        machineInFront  = null;
        // Shops appear on every floor now — the starting room and boss arenas included. Placement
        // still requires an eligible wall-mounted tile; if a floor has none, no machine is placed.

        java.util.Random random = new java.util.Random(floorSeed(runSeed, depth) ^ 0x5309CAFED00DL);
        int machineCount = GameBalance.SHOP_MIN_PER_FLOOR;
        if (GameBalance.SHOP_MAX_PER_FLOOR > GameBalance.SHOP_MIN_PER_FLOOR
                && random.nextFloat() < GameBalance.SHOP_SECOND_MACHINE_CHANCE) {
            machineCount = GameBalance.SHOP_MAX_PER_FLOOR;
        }

        int startColumn = MathUtils.floor(findPlayerStartX(targetLevel) / Constants.CELL_SIZE);
        int startRow    = MathUtils.floor(findPlayerStartY(targetLevel) / Constants.CELL_SIZE);

        java.util.List<VendingMachine> candidates = new java.util.ArrayList<>();
        for (int tileColumn = 0; tileColumn < targetLevel.getWidth(); tileColumn++) {
            for (int tileRow = 0; tileRow < targetLevel.getHeight(); tileRow++) {
                if (tileColumn == startColumn && tileRow == startRow) continue;
                VendingMachine candidate = tryMakeMachineCandidate(targetLevel, tileColumn, tileRow);
                if (candidate != null) candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) return;

        // Shuffle for variety, then place up to machineCount, keeping a second machine spread out.
        // The first shuffled candidate always places (no prior machine to be "too close" to), so a
        // floor with any eligible tile is never shop-less.
        java.util.Collections.shuffle(candidates, random);
        for (int index = 0; index < candidates.size() && vendingMachines.size() < machineCount; index++) {
            VendingMachine candidate = candidates.get(index);
            boolean tooClose = false;
            for (int placedIndex = 0; placedIndex < vendingMachines.size(); placedIndex++) {
                VendingMachine placed = vendingMachines.get(placedIndex);
                int manhattan = Math.abs(placed.tileColumn - candidate.tileColumn)
                              + Math.abs(placed.tileRow - candidate.tileRow);
                if (manhattan < GameBalance.SHOP_TWO_MACHINE_MIN_SPACING) { tooClose = true; break; }
            }
            if (tooClose) continue;
            placeMachine(targetLevel, candidate);
        }

        rollMachineStock(depth);
    }

    /**
     * Rolls each placed machine's ShopStock (shop_order_2). On a two-machine floor with bias
     * enabled, machine 0 leans toward upgrades and machine 1 toward supplies so visiting both is
     * worthwhile. Each machine gets its own seed derived from the floor seed for reproducibility.
     */
    private void rollMachineStock(int depth) {
        if (vendingMachines.isEmpty()) return;
        ShopContext context = buildShopContext(depth);
        long baseSeed       = floorSeed(runSeed, depth);
        boolean twoMachineBias = GameBalance.SHOP_TWO_MACHINE_BIAS && vendingMachines.size() > 1;
        for (int index = 0; index < vendingMachines.size(); index++) {
            VendingMachine machine = vendingMachines.get(index);
            ShopRoller.RollBias bias = ShopRoller.RollBias.NONE;
            if (twoMachineBias) {
                bias = (index == 0) ? ShopRoller.RollBias.UPGRADE : ShopRoller.RollBias.SUPPLY;
            }
            long machineSeed = baseSeed ^ (0x9E3779B97F4A7C15L * (index + 1));
            machine.stock = shopRoller.roll(context, machineSeed, bias);
        }
    }

    /** Builds the player snapshot the shop roller reads: owned weapons (loadout + melee) and depth. */
    private ShopContext buildShopContext(int depth) {
        java.util.List<WeaponProfile> ownedWeapons = new java.util.ArrayList<>();
        Loadout loadout = inventory.getLoadout();
        if (loadout != null) {
            for (int slotIndex = 0; slotIndex < loadout.getSlotCount(); slotIndex++) {
                Weapon weapon = loadout.getSlot(slotIndex);
                if (weapon != null) ownedWeapons.add(weapon);
            }
        }
        MeleeWeapon meleeWeapon = inventory.getMeleeWeapon();
        if (meleeWeapon != null) ownedWeapons.add(meleeWeapon);
        return new ShopContext(depth, ownedWeapons);
    }

    /**
     * Stamps the machine's tile solid ('@') for collision and records the entity. The '@' tile is
     * NOT registered as a PropRenderer billboard — {@link ShopMachineRenderer} draws the animated
     * fabricator sprite for these tiles instead (shop_order_6), so a static vendor prop here would
     * double-draw over it.
     */
    private void placeMachine(Level targetLevel, VendingMachine machine) {
        targetLevel.setCell(machine.tileColumn, machine.tileRow, '@');
        vendingMachines.add(machine);
    }

    /**
     * Returns a machine candidate for the tile, or null if it is ineligible. Eligible = plain floor,
     * not enemy/weapon-spawn occupied, with at least one orthogonal wall (mount) and one orthogonal
     * open floor (stand tile), and making the tile solid does not locally disconnect its neighbors.
     */
    private VendingMachine tryMakeMachineCandidate(Level targetLevel, int tileColumn, int tileRow) {
        if (!isPlainFloorTile(targetLevel, tileColumn, tileRow)) return null;
        if (isTileOccupiedForMachine(targetLevel, tileColumn, tileRow)) return null;

        int wallCount    = 0;
        int firstOpenCol = 0, firstOpenRow = 0;
        boolean hasOpen  = false;
        int[][] orthogonalSteps = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        for (int[] step : orthogonalSteps) {
            int neighborColumn = tileColumn + step[0];
            int neighborRow    = tileRow + step[1];
            if (isSolidForMachine(targetLevel, neighborColumn, neighborRow)) {
                wallCount++;
            } else if (!hasOpen) {
                firstOpenCol = neighborColumn;
                firstOpenRow = neighborRow;
                hasOpen      = true;
            }
        }
        if (wallCount < 1 || !hasOpen) return null;
        if (!orthOpenNeighborsLocallyConnected(targetLevel, tileColumn, tileRow)) return null;

        // Face toward the stand tile (first open orthogonal neighbor).
        return new VendingMachine(tileColumn, tileRow, firstOpenCol - tileColumn, firstOpenRow - tileRow);
    }

    private boolean isPlainFloorTile(Level targetLevel, int tileColumn, int tileRow) {
        char cell = targetLevel.getCell(tileColumn, tileRow);
        return cell == ' ' || cell == 'l' || cell == 'u' || cell == 'f';
    }

    /** Out-of-bounds and wall/solid-prop/column tiles block movement — treated as "wall" for mounting. */
    private boolean isSolidForMachine(Level targetLevel, int tileColumn, int tileRow) {
        if (tileColumn < 0 || tileColumn >= targetLevel.getWidth()
                || tileRow < 0 || tileRow >= targetLevel.getHeight()) return true;
        char cell = targetLevel.getCell(tileColumn, tileRow);
        return Level.isWall(cell) || Level.isPropSolid(cell) || Level.isColumn(cell);
    }

    private boolean isTileOccupiedForMachine(Level targetLevel, int tileColumn, int tileRow) {
        if (enemyManager != null && enemyManager.isTileOccupiedByEnemy(tileColumn, tileRow)) return true;
        for (WeaponSpawnPoint spawnPoint : targetLevel.getWeaponSpawnPoints()) {
            if (spawnPoint.tileColumn == tileColumn && spawnPoint.tileRow == tileRow) return true;
        }
        return false;
    }

    /**
     * Local articulation test: making (tileColumn,tileRow) solid must not disconnect its open
     * orthogonal neighbors. They are safe if they all belong to one 8-connected component of open
     * tiles within the 3x3 block around the tile (excluding the tile itself). One (or zero) open
     * orthogonal neighbor is trivially safe (blocking a dead-end never disconnects anything).
     */
    private boolean orthOpenNeighborsLocallyConnected(Level targetLevel, int tileColumn, int tileRow) {
        int[][] orthogonalSteps = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        java.util.List<int[]> openNeighbors = new java.util.ArrayList<>();
        for (int[] step : orthogonalSteps) {
            int neighborColumn = tileColumn + step[0];
            int neighborRow    = tileRow + step[1];
            if (!isSolidForMachine(targetLevel, neighborColumn, neighborRow)) {
                openNeighbors.add(new int[]{neighborColumn, neighborRow});
            }
        }
        if (openNeighbors.size() <= 1) return true;

        // Flood-fill 8-connected through open tiles inside the 3x3 block, excluding the center tile.
        boolean[][] visited = new boolean[3][3];
        java.util.ArrayDeque<int[]> frontier = new java.util.ArrayDeque<>();
        int[] start = openNeighbors.get(0);
        visited[start[0] - tileColumn + 1][start[1] - tileRow + 1] = true;
        frontier.push(start);
        while (!frontier.isEmpty()) {
            int[] current = frontier.pop();
            for (int deltaColumn = -1; deltaColumn <= 1; deltaColumn++) {
                for (int deltaRow = -1; deltaRow <= 1; deltaRow++) {
                    if (deltaColumn == 0 && deltaRow == 0) continue;
                    int nextColumn = current[0] + deltaColumn;
                    int nextRow    = current[1] + deltaRow;
                    if (nextColumn < tileColumn - 1 || nextColumn > tileColumn + 1
                            || nextRow < tileRow - 1 || nextRow > tileRow + 1) continue;
                    if (nextColumn == tileColumn && nextRow == tileRow) continue; // the tile being blocked
                    int localColumn = nextColumn - tileColumn + 1;
                    int localRow    = nextRow - tileRow + 1;
                    if (visited[localColumn][localRow]) continue;
                    if (isSolidForMachine(targetLevel, nextColumn, nextRow)) continue;
                    visited[localColumn][localRow] = true;
                    frontier.push(new int[]{nextColumn, nextRow});
                }
            }
        }
        for (int index = 0; index < openNeighbors.size(); index++) {
            int[] neighbor = openNeighbors.get(index);
            if (!visited[neighbor[0] - tileColumn + 1][neighbor[1] - tileRow + 1]) return false;
        }
        return true;
    }

    /** Returns the machine the player is directly facing (front tile), or null. */
    private VendingMachine findMachineFacingPlayer() {
        if (vendingMachines.isEmpty()) return null;
        int frontColumn = getPlayerTileColumn() + Math.round(player.directionX);
        int frontRow    = getPlayerTileRow()    + Math.round(player.directionY);
        for (int index = 0; index < vendingMachines.size(); index++) {
            VendingMachine machine = vendingMachines.get(index);
            if (machine.isAtTile(frontColumn, frontRow)) return machine;
        }
        return null;
    }

    /**
     * The single applier that delivers any shop purchase's effect (weapons, supplies, abilities).
     * shop_order_5's buy-tap passes it to {@link ge.tbegvadze.toon3d.shop.ShopTransaction#buy}.
     */
    public ShopEffectApplier getShopEffectApplier() {
        return shopEffectApplier;
    }

    /** Opens the fabricator shop overlay for the machine in front of the player. No turn is spent. */
    private void openShop() {
        if (runPhase != RunPhase.PLAYING || machineInFront == null) return;
        if (touchInputState != null) touchInputState.resetAllButtonStates();
        openMachine = machineInFront;
        shopOverlayRenderer.open(openMachine, playerStats.getCredits());
        runPhase = RunPhase.SHOP_OPEN;
    }

    /** Closes the shop overlay and returns to PLAYING. Menu pause — no world tick fires. */
    private void closeShop() {
        openMachine = null;
        runPhase = RunPhase.PLAYING;
    }

    /**
     * Settles a confirmed shop purchase (shop_order_5). Runs the transaction against the player's
     * wallet and the shared effect router, then reports the outcome back to the overlay so the card
     * flashes on success (or blips on failure). No turn is spent — the shop is a menu pause (part 1).
     */
    private void resolveShopPurchase(int entryIndex) {
        if (openMachine == null || openMachine.stock == null) return;
        if (entryIndex < 0 || entryIndex >= openMachine.stock.size()) return;
        ShopEntry entry = openMachine.stock.get(entryIndex);
        ShopTransaction.Result result =
                ShopTransaction.buy(entry, playerStats, shopEffectApplier);
        boolean purchased = result == ShopTransaction.Result.PURCHASED;
        if (purchased) {
            runStats.recordCreditsSpent(entry.price); // resource-economy telemetry (order 3)
            shopOverlayRenderer.setCredits(playerStats.getCredits());
            // Kick off the machine's one-shot DISPENSE animation (shop_order_6): window flash,
            // status blink, product drop into the tray. Cosmetic only — no world tick.
            openMachine.triggerDispense(facilityTimeSeconds);
        }
        // Push a short receipt line to the overlay so the player SEES what the purchase delivered
        // (or why it failed). This is rendered inside the shop overlay — event text would be hidden
        // behind the overlay's dim quad — so it is the only on-screen confirmation of receipt.
        shopOverlayRenderer.setReceipt(shopReceiptText(entry, result), purchased);
        shopOverlayRenderer.onPurchaseResult(entryIndex, purchased);
    }

    /**
     * Builds the one-line receipt shown in the shop overlay after a buy tap. A success names the
     * exact item received (ammo offers already carry their count in {@code displayName}), which makes
     * it obvious the goods actually landed — previously a bought item gave no on-screen receipt.
     * A rejection states the reason so a failed tap never looks like a silent loss of credits.
     */
    private static String shopReceiptText(ShopEntry entry, ShopTransaction.Result result) {
        if (entry == null) return "";
        switch (result) {
            case PURCHASED:            return "ACQUIRED: " + entry.displayName.toUpperCase();
            case INSUFFICIENT_CREDITS: return "NOT ENOUGH CREDITS";
            case CANNOT_APPLY:         return "NO ROOM FOR THAT";
            case SOLD_OUT:             return "SOLD OUT";
            default:                   return "";
        }
    }

    // -------------------------------------------------------------------------
    // Public accessors
    // -------------------------------------------------------------------------

    /**
     * Activates touch controls if the device has a multitouch screen.
     * Call once from Main.create() after the viewport is ready.
     */
    public void initTouchControls(Viewport viewport) {
        if (!Gdx.input.isPeripheralAvailable(Input.Peripheral.MultitouchScreen)) return;
        gameViewport            = viewport;
        touchInputState         = new TouchInputState(viewport);
        touchControllerRenderer = new TouchControllerRenderer(touchInputState);
        Gdx.input.setInputProcessor(touchInputState);
        if (playerController != null) playerController.setTouchInputState(touchInputState);
    }

    public GameState getGameState()  { return gameState; }
    public int       getCurrentDepth() { return currentDepth; }

    public float getPlayerX() { return player.positionX; }
    public float getPlayerY() { return player.positionY; }

    public int getPlayerTileColumn() { return MathUtils.floor(player.positionX / Constants.CELL_SIZE); }
    public int getPlayerTileRow()    { return MathUtils.floor(player.positionY / Constants.CELL_SIZE); }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    public void update(float deltaTime) {
        // DEAD phase — death beat then death screen; no game simulation
        if (runPhase == RunPhase.DEAD) {
            deathBeatTimerSeconds += deltaTime;
            if (deathBeatTimerSeconds >= ProgressionConstants.DEATH_BEAT_DURATION_SECONDS) {
                deathBlinkTimerSeconds += deltaTime;
                if (touchInputState != null && Gdx.input.justTouched()) {
                    StatsStore.updateAndSave(runStats, persistentStats);
                    resetRequested = true;
                }
            }
            return;
        }

        if (runPhase == RunPhase.FADING_OUT) {
            fadeTimerSeconds += deltaTime;
            if (fadeTimerSeconds >= RenderConstants.LEVEL_TRANSITION_FADE_OUT_SECONDS) {
                if (pendingBossTestJump) {
                    // TEMPORARY TESTING (RouteMapConstants.BOSS_TEST_ALWAYS_CLICKABLE): land directly on
                    // the boss node's real (boss-floor) depth so GameMath.isBossFloor() holds and the
                    // boss actually spawns. Overrides both the staging-room and normal-descent depth
                    // handling; the route cursor was intentionally left untouched by commitRouteNode.
                    pendingBossTestJump          = false;
                    isStartingRoom               = false;
                    startRoomWeapons             = null;
                    startRoomGroundItems         = null;
                    startRoomMeleeWeapons        = null;
                    startRoomMeleeGroundItems    = null;
                    currentDepth                 = pendingNode.depth;
                    playerProgress.setFloorDepth(currentDepth);
                    runStats.recordFloor(currentDepth);
                    rebuildForLevel(buildNextFloor());
                    applyPendingFloorEffects();
                    detectRegionEntry();
                } else if (isStartingRoom) {
                    // Leaving the staging room: the real run begins. The normal path already generated
                    // the route map and player-committed the depth-1 node in
                    // onDescentRequested/commitRouteNode (pendingNode is set). In the defensive
                    // no-legal-move fallback inside onDescentRequested, pendingNode stays null and
                    // buildNextFloor()'s own null guard falls back to the depth-driven generator.
                    // currentDepth stays at STARTING_DEPTH so floor 1 is the first floor either way.
                    isStartingRoom               = false;
                    startRoomWeapons             = null;
                    startRoomGroundItems         = null;
                    startRoomMeleeWeapons        = null;
                    startRoomMeleeGroundItems    = null;
                    playerProgress.setFloorDepth(currentDepth);
                    runStats.recordFloor(currentDepth);
                    rebuildForLevel(buildNextFloor());
                    applyPendingFloorEffects();
                    detectRegionEntry();
                } else {
                    currentDepth++;
                    playerProgress.setFloorDepth(currentDepth);
                    runStats.recordFloor(currentDepth);
                    rebuildForLevel(buildNextFloor());
                    applyPendingFloorEffects();
                    detectRegionEntry();
                }
                fadeTimerSeconds = 0f;
                runPhase = RunPhase.FADING_IN;
            }
            return;
        }
        if (runPhase == RunPhase.FADING_IN) {
            fadeTimerSeconds += deltaTime;
            if (fadeTimerSeconds >= RenderConstants.LEVEL_TRANSITION_FADE_IN_SECONDS) {
                fadeTimerSeconds = 0f;
                runPhase = RunPhase.PLAYING;
            }
            return;
        }

        // INVENTORY_OPEN — world paused; route input to the overlay
        if (runPhase == RunPhase.INVENTORY_OPEN) {
            inventoryOverlayRenderer.handleInput(deltaTime);
            if (touchInputState != null && gameViewport != null && Gdx.input.justTouched()) {
                // Discard any pending tap action so the invisible OPEN_INVENTORY button area
                // cannot accidentally close the overlay. All closing goes through the overlay's
                // own visible CLOSE button, the header bar, and the USE/DROP buttons.
                touchInputState.consumeTapAction();
                cardTouchPosition.set(Gdx.input.getX(), Gdx.input.getY());
                gameViewport.unproject(cardTouchPosition);
                InventoryOverlayRenderer.CloseAction touchAction =
                        inventoryOverlayRenderer.handleTouchAt(cardTouchPosition.x, cardTouchPosition.y);
                if (touchAction == InventoryOverlayRenderer.CloseAction.CLOSE_FREE) {
                    closeInventory(false);
                } else if (touchAction == InventoryOverlayRenderer.CloseAction.CLOSE_WITH_TURN) {
                    closeInventory(true);
                }
            }
            return;
        }

        // WEAPON_INSPECT — world paused while player examines a ground weapon
        if (runPhase == RunPhase.WEAPON_INSPECT) {
            weaponInspectOverlayRenderer.setFacilityTime(facilityTimeSeconds);
            if (gameViewport != null && Gdx.input.justTouched()) {
                if (touchInputState != null) touchInputState.consumeTapAction();
                cardTouchPosition.set(Gdx.input.getX(), Gdx.input.getY());
                gameViewport.unproject(cardTouchPosition);
                weaponInspectOverlayRenderer.handleTap(cardTouchPosition.x, cardTouchPosition.y);
            }
            return;
        }

        // SHOP_OPEN — world paused while the player browses a vending machine. No turn passes;
        // taps drive the card grid's buy flow (confirm step) or dismiss the overlay (part 5).
        if (runPhase == RunPhase.SHOP_OPEN) {
            shopOverlayRenderer.setCredits(playerStats.getCredits());
            if (touchInputState != null && gameViewport != null && Gdx.input.justTouched()) {
                touchInputState.consumeTapAction();
                cardTouchPosition.set(Gdx.input.getX(), Gdx.input.getY());
                gameViewport.unproject(cardTouchPosition);
                ShopOverlayRenderer.TouchOutcome outcome =
                        shopOverlayRenderer.handleTouch(cardTouchPosition.x, cardTouchPosition.y);
                if (outcome == ShopOverlayRenderer.TouchOutcome.CLOSED) {
                    closeShop();
                } else if (outcome == ShopOverlayRenderer.TouchOutcome.PURCHASE_CONFIRMED) {
                    resolveShopPurchase(shopOverlayRenderer.getPendingBuyIndex());
                }
            }
            return;
        }

        // ROUTE_SELECT — world paused (like SHOP_OPEN) while the "FACILITY NAV" console is shown.
        // Order-6: the player drives selection by touch — tap a candidate card to FOCUS it, then tap
        // ENGAGE to COMMIT (two-step, so a fat-finger can never dive). Vertical drag pans the schematic
        // to plan ahead. The console owns the OPENING/IDLE/COMMIT timeline + all hit-testing; World only
        // forwards unprojected touches and fires out-of-renderer feedback (console-thunk shake). When
        // the COMMIT flare finishes, commit() fires onNodeCommitted -> commitRouteNode -> FADING_OUT.
        if (runPhase == RunPhase.ROUTE_SELECT) {
            if (routeMapOverlay.isActive()) {
                java.util.List<RouteNode> candidates = routeMapOverlay.getCandidates();
                if (candidates.isEmpty()) {
                    // No candidates (defensive) — resume a straight descent so the run never stalls.
                    fadeTimerSeconds = 0f;
                    runPhase = RunPhase.FADING_OUT;
                    return;
                }
                routeMapOverlayRenderer.setRedAlert(gameState.redAlert);
                routeMapOverlayRenderer.update(deltaTime);
                impactEffectSystem.update(deltaTime);   // lets an ENGAGE console-thunk shake decay
                handleRouteSelectTouch();
                if (routeMapOverlayRenderer.isCommitFinished()) {
                    RouteNode chosen = routeMapOverlayRenderer.getCommittedNode();
                    routeMapOverlay.commit(chosen != null ? chosen : candidates.get(0));
                }
            }
            return;
        }

        // LEVEL_UP_OVERLAY — game paused while player picks an upgrade card
        if (runPhase == RunPhase.LEVEL_UP_OVERLAY) {
            if (gameViewport != null && Gdx.input.justTouched()) {
                cardTouchPosition.set(Gdx.input.getX(), Gdx.input.getY());
                gameViewport.unproject(cardTouchPosition);
                int tappedIndex = levelUpOverlayRenderer.getTappedCardIndex(cardTouchPosition.x, cardTouchPosition.y);
                UpgradeCard tappedCard = levelUpOverlayRenderer.getOfferedCard(tappedIndex);
                if (tappedCard != null) {
                    applyUpgradeCard(tappedCard);
                }
            }
            return;
        }

        // EVENT_CHOICE — game paused while the player picks a narrative choice (order-10). Mirrors the
        // LEVEL_UP_OVERLAY: unproject the tap through the game viewport, hit-test the choice cards, and
        // apply the picked choice's typed effect. Tapping a card resolves the event and resumes PLAYING.
        if (runPhase == RunPhase.EVENT_CHOICE) {
            if (gameViewport != null && Gdx.input.justTouched()) {
                cardTouchPosition.set(Gdx.input.getX(), Gdx.input.getY());
                gameViewport.unproject(cardTouchPosition);
                int tappedIndex = eventChoiceOverlayRenderer.getTappedChoiceIndex(
                        cardTouchPosition.x, cardTouchPosition.y);
                EventChoice tappedChoice = eventChoiceOverlayRenderer.getChoice(tappedIndex);
                if (tappedChoice != null) {
                    applyEventChoice(tappedChoice);
                    activeEvent = null;
                    runPhase = RunPhase.PLAYING;
                }
            }
            return;
        }

        // PLAYING phase — normal game simulation
        runStats.realSecondsPlayed += deltaTime;
        doorManager.update(deltaTime);
        playerController.update(deltaTime);

        // Death check: resolved after the tick fully completes, never mid-tick
        if (player.isDead()) {
            runStats.recordFloor(currentDepth);
            deathOverlayRenderer.show(runStats, persistentStats);
            runPhase              = RunPhase.DEAD;
            deathBeatTimerSeconds = 0f;
            deathBlinkTimerSeconds = 0f;
            return;
        }

        // Update contextual INSPECT button and HUD label based on the tile the player occupies
        if (touchInputState != null) {
            GroundItem standingOnWeapon = playerController.getStandingOnWeapon();
            boolean standingOnGroundWeapon = standingOnWeapon != null
                    && groundItems.contains(standingOnWeapon);
            touchInputState.setInspectButtonVisible(standingOnGroundWeapon);
            hudRenderer.setGroundWeaponLabel(standingOnGroundWeapon
                    ? standingOnWeapon.stack.getType().getDisplayName() : null);

            // Contextual USE button — visible only while the player faces a vending machine.
            machineInFront = findMachineFacingPlayer();
            touchInputState.setUseMachineButtonVisible(machineInFront != null);
        }

        // Auto-doc heal stations (order-8): a one-time free heal fires the moment the player steps
        // adjacent to a still-charged station, reusing the medkit heal path. Consumed on use.
        updateHealStations();

        // Narrative EVENT stations (order-10): the moment the player steps adjacent to the room's
        // interactable, the floor's event opens its choice overlay (mirrors the heal-station adjacency
        // trigger, so no new touch button or aiming is needed). No-op on non-EVENT floors.
        updateEventStations();
        if (runPhase == RunPhase.EVENT_CHOICE) {
            return; // the event overlay just opened — pause the sim for this frame
        }

        if (touchInputState != null) {
            touchInputState.update(deltaTime);
        }
        Weapon equippedWeapon = inventory.getEquippedWeapon();
        if (equippedWeapon != null) {
            equippedWeapon.update(deltaTime);
        }
        enemyManager.advanceHitFlash(deltaTime);
        enemyAttackEffectSystem.update(deltaTime);
        statusEffectVignetteRenderer.update(deltaTime, player);
        impactEffectSystem.setPlayerState(player.positionX, player.positionY,
                player.directionX, player.directionY, player.fieldOfViewRadians);
        impactEffectSystem.update(deltaTime);
        eventTextSystem.update(deltaTime);
        abilityFeedback.update(deltaTime);
        hitVignetteRenderer.update(deltaTime);
        guardShieldRenderer.update(deltaTime);
        if (bossHudRenderer != null) bossHudRenderer.update(deltaTime);

        // Transition to level-up overlay as soon as XP threshold is crossed
        if (playerProgress.hasPendingLevelUp() && !player.isDead()) {
            if (touchInputState != null) {
                touchInputState.resetAllButtonStates();
            }
            // Draw a fresh set of budget-equal cards ONCE as the overlay opens.
            offeredCardCount = upgradeCardDeck.draw(offeredCards, GameBalance.LEVEL_UP_CARDS_OFFERED);
            levelUpOverlayRenderer.setOfferedCards(offeredCards, offeredCardCount);
            runPhase = RunPhase.LEVEL_UP_OVERLAY;
        }
    }

    // -------------------------------------------------------------------------
    // Render
    // -------------------------------------------------------------------------

    @Override
    public void render(OrthographicCamera camera) {
        facilityTimeSeconds += Gdx.graphics.getDeltaTime();
        floorCeilingRenderer.setLightingTime(facilityTimeSeconds);
        wallRenderer.setLightingTime(facilityTimeSeconds);
        propRenderer.setLightingTime(facilityTimeSeconds);

        if (gameState.redAlert) {
            alertTimeSeconds += Gdx.graphics.getDeltaTime();
        } else {
            alertTimeSeconds = 0f;
        }
        float currentAlertPulse = gameState.redAlert
                ? Math.max(0f, (float) Math.sin(
                        alertTimeSeconds * RenderConstants.ALERT_PULSE_SPEED_RADIANS_PER_SECOND))
                : 0f;
        wallRenderer.setAlertPulse(currentAlertPulse);

        float shakeOffsetX = impactEffectSystem.getShakeOffsetX();
        float shakeOffsetY = impactEffectSystem.getShakeOffsetY();
        boolean isShaking  = shakeOffsetX != 0f || shakeOffsetY != 0f;
        if (isShaking) {
            camera.translate(shakeOffsetX, shakeOffsetY, 0f);
            camera.update();
        }

        // Use effective FOV so BLINDED status clamps the visible cone to 30°.
        float effectiveFovRadians = player.getEffectiveFovRadians();

        floorCeilingRenderer.setPlayerState(player.positionX, player.positionY,
                player.directionX, player.directionY, effectiveFovRadians);
        floorCeilingRenderer.setAlertPulse(currentAlertPulse);
        floorCeilingRenderer.render(camera);

        wallRenderer.setPlayerState(player.positionX, player.positionY,
                player.directionX, player.directionY, effectiveFovRadians);
        wallRenderer.render(camera);

        propRenderer.setPlayerState(player.positionX, player.positionY,
                player.directionX, player.directionY, effectiveFovRadians);
        propRenderer.setAlertPulse(currentAlertPulse);
        propRenderer.render(camera);

        // Shop machines (shop_order_6) — animated fabricator billboards, drawn in the prop pass so
        // they write into the prop z-buffer and enemies/weapon HUD draw over them correctly.
        shopMachineRenderer.setPlayerState(player.positionX, player.positionY,
                player.directionX, player.directionY, effectiveFovRadians);
        shopMachineRenderer.setLightingTime(facilityTimeSeconds);
        shopMachineRenderer.setAlertPulse(currentAlertPulse);
        shopMachineRenderer.render(camera);

        enemyRenderer.setPlayerState(player.positionX, player.positionY,
                player.directionX, player.directionY, effectiveFovRadians);
        enemyRenderer.setAlertPulse(currentAlertPulse);
        enemyRenderer.render(camera);

        enemyAttackEffectSystem.setPlayerState(player.positionX, player.positionY,
                player.directionX, player.directionY, effectiveFovRadians);
        enemyAttackEffectSystem.render(camera);

        impactEffectRenderer.renderWorldEffects(camera);
        weaponHudRenderer.render(camera);

        if (isShaking) {
            camera.translate(-shakeOffsetX, -shakeOffsetY, 0f);
            camera.update();
        }

        impactEffectRenderer.renderScreenOverlays(camera);

        // Guard shield arc: translucent blue rim across the screen bottom while the player is braced.
        guardShieldRenderer.render(camera);

        // Hit vignette: red edge glow drawn under the minimap and HUD panels.
        hitVignetteRenderer.render(camera);
        // Status effect vignettes: per-effect colored edge tints (burn orange, poison green, etc.)
        statusEffectVignetteRenderer.render(camera);

        levelRenderer.setPlayerWorldPosition(player.positionX, player.positionY);
        levelRenderer.setAnimationClock(facilityTimeSeconds);
        levelRenderer.render(camera);

        player.render(camera);

        // Populate HudState each frame so renderers read current values without polling Player directly
        hudState.alertActive    = false;
        hudState.playerLevel    = playerProgress.getPlayerLevel();
        hudState.xpFraction     = playerProgress.getXpFraction();
        hudState.xpForNextLevel = playerProgress.getXpForNextLevel();
        hudState.medicalCharges = itemInventory.countOf(ItemType.MEDKIT_SMALL) + itemInventory.countOf(ItemType.MEDKIT_LARGE);
        hudState.credits        = playerStats.getCredits();
        Weapon hudWeapon = inventory.getEquippedWeapon();
        if (hudWeapon != null) {
            hudState.currentAmmo = hudWeapon.getShotsInClip();
            hudState.clipSize    = hudWeapon.getEffectiveClipSize();
            hudState.reserveAmmo = hudWeapon.getReserveAmmo();
        } else {
            hudState.currentAmmo = 0;
            hudState.clipSize    = 1;
            hudState.reserveAmmo = -1;
        }
        float deltaTime = Gdx.graphics.getDeltaTime();
        hudRenderer.update(deltaTime);
        hudRenderer.render(camera);

        // Level-up overlay and inventory overlay each take full control of the screen —
        // hide touch buttons so they don't obscure cards or inventory slots.
        if (runPhase == RunPhase.LEVEL_UP_OVERLAY) {
            levelUpOverlayRenderer.render(camera);
        } else if (runPhase != RunPhase.WEAPON_INSPECT
                && touchControllerRenderer != null
                && runPhase != RunPhase.INVENTORY_OPEN
                && runPhase != RunPhase.SHOP_OPEN
                && runPhase != RunPhase.ROUTE_SELECT
                && runPhase != RunPhase.EVENT_CHOICE) {
            touchControllerRenderer.setActionLocked(!playerController.isIdle());
            touchControllerRenderer.render(camera);
        }

        // Event text: rising screen-space text drawn above HUD but below the fade overlay.
        eventTextRenderer.render(camera);
        if (bossHudRenderer != null) bossHudRenderer.render(camera);

        // Inventory overlay — drawn above HUD and event text, below fade/death overlays.
        if (runPhase == RunPhase.INVENTORY_OPEN) {
            inventoryOverlayRenderer.setTime(facilityTimeSeconds);
            inventoryOverlayRenderer.setCurrentDepth(currentDepth);
            inventoryOverlayRenderer.render(camera);
        }

        // Weapon inspect overlay — modal stat card drawn above HUD and event text.
        if (runPhase == RunPhase.WEAPON_INSPECT) {
            weaponInspectOverlayRenderer.render(camera);
        }

        // Shop overlay — paused fabricator UI drawn above HUD and event text, below fade/death overlays.
        if (runPhase == RunPhase.SHOP_OPEN) {
            shopOverlayRenderer.render(camera);
        }

        // Route-map "FACILITY NAV" console — paused branching-route UI, drawn below fade/death overlays.
        if (runPhase == RunPhase.ROUTE_SELECT) {
            routeMapOverlayRenderer.render(camera);
        }

        // Narrative EVENT choice overlay (order-10) — paused choice cards, drawn below fade/death overlays.
        if (runPhase == RunPhase.EVENT_CHOICE) {
            eventChoiceOverlayRenderer.render(camera);
        }

        // Fade overlay drawn last — covers every other layer including the HUD.
        if (runPhase == RunPhase.FADING_OUT || runPhase == RunPhase.FADING_IN) {
            float fadeAlpha;
            if (runPhase == RunPhase.FADING_OUT) {
                fadeAlpha = Math.min(1f, fadeTimerSeconds / RenderConstants.LEVEL_TRANSITION_FADE_OUT_SECONDS);
            } else {
                fadeAlpha = Math.max(0f, 1f - fadeTimerSeconds / RenderConstants.LEVEL_TRANSITION_FADE_IN_SECONDS);
            }
            fadeOverlayRenderer.render(camera, fadeAlpha, currentDepth);
        }

        // Death beat: fade to black over DEATH_BEAT_DURATION; then show the death report.
        if (runPhase == RunPhase.DEAD) {
            if (deathBeatTimerSeconds < ProgressionConstants.DEATH_BEAT_DURATION_SECONDS) {
                float deathFadeAlpha = deathBeatTimerSeconds / ProgressionConstants.DEATH_BEAT_DURATION_SECONDS;
                fadeOverlayRenderer.render(camera, deathFadeAlpha, currentDepth);
            } else {
                boolean showPrompt = deathBlinkTimerSeconds % 1.0f < 0.5f;
                deathOverlayRenderer.render(camera, showPrompt);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Dispose
    // -------------------------------------------------------------------------

    @Override
    public void dispose() {
        floorCeilingRenderer.dispose();
        wallRenderer.dispose();
        propRenderer.dispose();
        // TILESET SYSTEM (order-6): free the current floor's realized textures at world shutdown.
        if (environmentTextureSet != null) { environmentTextureSet.dispose(); environmentTextureSet = null; }
        if (shopMachineRenderer != null) shopMachineRenderer.dispose();
        enemyRenderer.dispose();
        enemyAttackEffectSystem.dispose();
        levelRenderer.dispose();
        weaponHudRenderer.dispose();
        hudRenderer.dispose();
        impactEffectRenderer.dispose();
        fadeOverlayRenderer.dispose();
        eventTextRenderer.dispose();
        hitVignetteRenderer.dispose();
        guardShieldRenderer.dispose();
        levelUpOverlayRenderer.dispose();
        eventChoiceOverlayRenderer.dispose();
        inventoryOverlayRenderer.dispose();
        weaponInspectOverlayRenderer.dispose();
        shopOverlayRenderer.dispose();
        routeMapOverlayRenderer.dispose();
        statusEffectVignetteRenderer.dispose();
        if (touchControllerRenderer != null) touchControllerRenderer.dispose();
        deathOverlayRenderer.dispose();
        if (bossHudRenderer != null) { bossHudRenderer.dispose(); bossHudRenderer = null; }
        player.dispose();
    }

    /** Returns true once after the player acknowledges the death screen; Main recreates the World. */
    public boolean isResetRequested() { return resetRequested; }

    // -------------------------------------------------------------------------
    // Inventory overlay — open/close
    // -------------------------------------------------------------------------

    /** Opens the inventory overlay. Only valid from PLAYING phase; ignored otherwise. */
    private void openInventory() {
        if (runPhase != RunPhase.PLAYING) return;
        if (touchInputState != null) touchInputState.resetAllButtonStates();
        inventoryOverlayRenderer.onOpen();
        runPhase = RunPhase.INVENTORY_OPEN;
    }

    /**
     * Closes the inventory overlay and returns to PLAYING.
     * When spendTurn is true, fires one world tick so enemies react (USE / DROP actions).
     */
    private void closeInventory(boolean spendTurn) {
        runPhase = RunPhase.PLAYING;
        if (spendTurn) {
            applyInventoryConsumableEffect();
            if (tickEventBus != null) {
                int playerTileColumn = MathUtils.floor(player.positionX / Constants.CELL_SIZE);
                int playerTileRow    = MathUtils.floor(player.positionY / Constants.CELL_SIZE);
                tickEventBus.fireTick(playerTileColumn, playerTileRow, player, TickCause.SKIP_TURN);
            }
        }
    }

    /**
     * Fires the auto-doc's one-time heal when the player is orthogonally adjacent to a charged
     * heal-station tile (route-map order-8 MED-BAY). The heal reuses the medkit application path
     * (HP + green event text + heal particles + edge vignette) and the station is consumed (removed)
     * so it can never be farmed — one heal per station, one station per rest floor.
     */
    private void updateHealStations() {
        if (chargedHealStations.isEmpty() || player == null || player.isDead()) return;
        int playerColumn = MathUtils.floor(player.positionX / Constants.CELL_SIZE);
        int playerRow    = MathUtils.floor(player.positionY / Constants.CELL_SIZE);
        for (int index = 0; index < chargedHealStations.size(); index++) {
            int[] station = chargedHealStations.get(index);
            int manhattan = Math.abs(station[0] - playerColumn) + Math.abs(station[1] - playerRow);
            if (manhattan > 1) continue; // not adjacent yet (the station tile itself is solid)
            triggerAutoDocHeal();
            chargedHealStations.remove(index);
            return; // one heal per update; a rest floor has a single station anyway
        }
    }

    /** Applies the auto-doc's one-shot heal, mirroring the medkit restore feedback. */
    private void triggerAutoDocHeal() {
        int healAmount = Math.round(player.getMaxHealth() * RouteMapConstants.REST_HEAL_FRACTION);
        if (healAmount <= 0) return;
        player.applyHealing(healAmount);
        if (eventTextSystem != null) {
            eventTextSystem.spawnWithColor("AUTO-DOC +" + healAmount + " HP", EventTextSystem.COLOR_GREEN);
        }
        if (impactEffectSystem != null) impactEffectSystem.spawnHealParticles();
        if (hitVignetteRenderer != null) hitVignetteRenderer.triggerHeal();
    }

    // -------------------------------------------------------------------------
    // Narrative EVENT nodes (route-map order-10)
    // -------------------------------------------------------------------------

    /**
     * Opens the floor's narrative event the moment the player steps orthogonally adjacent to its
     * interactable (route-map order-10 EVENT node), mirroring the auto-doc heal-station trigger so no
     * new touch button or aiming is needed. The station is consumed (cleared) as it opens, so an event
     * fires exactly once. No-op on non-EVENT floors (no armed stations) or if the event id no longer
     * resolves (graceful degradation — a quiet room).
     */
    private void updateEventStations() {
        if (armedEventStations.isEmpty() || player == null || player.isDead()) return;
        int playerColumn = MathUtils.floor(player.positionX / Constants.CELL_SIZE);
        int playerRow    = MathUtils.floor(player.positionY / Constants.CELL_SIZE);
        for (int index = 0; index < armedEventStations.size(); index++) {
            int[] station = armedEventStations.get(index);
            int manhattan = Math.abs(station[0] - playerColumn) + Math.abs(station[1] - playerRow);
            if (manhattan > 1) continue; // not adjacent yet (the interactable tile itself is solid)
            FacilityEvent event = pendingEventId != null
                    ? RouteRegistries.events().get(pendingEventId) : null;
            armedEventStations.clear(); // consume every station: the beat fires once per floor
            if (event == null) {
                return; // no event resolved — leave the room quiet rather than open an empty overlay
            }
            activeEvent = event;
            eventChoiceOverlayRenderer.present(event);
            if (touchInputState != null) {
                touchInputState.resetAllButtonStates();
            }
            runPhase = RunPhase.EVENT_CHOICE;
            return;
        }
    }

    /**
     * Applies one picked {@link EventChoice}'s typed {@link EventEffect} (route-map order-10) through
     * EXISTING systems — heal, armour, ammo, XP, map reveal, next-floor budget — so there is no bespoke
     * per-event code path. A GAMBLE choice rolls the floor's own seed: on success the effect applies and
     * the result line shows; on failure a small survivable bite plus the fail line (never a lost run).
     * The picked choice's token is appended to the run's route story.
     */
    private void applyEventChoice(EventChoice choice) {
        EventEffect effect = choice.effect();
        boolean success = true;
        if (effect.isGamble()) {
            // Deterministic per-floor roll so a seed always resolves the gamble the same way.
            java.util.Random gambleRandom = new java.util.Random(floorSeed(runSeed, currentDepth) ^ 0xE7E1D1CEL);
            success = gambleRandom.nextFloat() < effect.gambleLootChance();
        }

        if (success) {
            if (effect.healFraction() > 0f && player != null) {
                int healAmount = Math.round(player.getMaxHealth() * effect.healFraction());
                if (healAmount > 0) {
                    player.applyHealing(healAmount);
                    if (impactEffectSystem != null) impactEffectSystem.spawnHealParticles();
                    if (hitVignetteRenderer != null) hitVignetteRenderer.triggerHeal();
                }
            }
            if (effect.armourToFull() && player != null) {
                player.applyArmor(player.getMaxArmor());
            }
            if (effect.ammoBoxes() > 0) {
                grantAmmoBoxes(effect.ammoBoxes());
            }
            if (effect.grantXp() > 0) {
                playerProgress.addXp(effect.grantXp());
            }
            if (effect.revealNextRegion()) {
                revealNextRegion();
            }
            if (effect.nextFloorBudgetBonus() > 0f) {
                pendingNextFloorBudgetBonus += effect.nextFloorBudgetBonus();
            }
            if (effect.resultText() != null && eventTextSystem != null) {
                eventTextSystem.spawnWithColor(effect.resultText(), EventTextSystem.COLOR_GREEN);
            }
        } else {
            // Lost gamble: a survivable bite + the fail sting (worst case wastes the hop, never fatal-by-design).
            if (effect.gambleFailDamageFraction() > 0f && player != null) {
                int damage = Math.round(player.getMaxHealth() * effect.gambleFailDamageFraction());
                if (damage > 0) player.applyDamage(damage);
            }
            String failLine = effect.failText() != null ? effect.failText() : "IT WAS A TRAP";
            if (eventTextSystem != null) {
                eventTextSystem.spawnWithColor(failLine, EventTextSystem.COLOR_RED);
            }
        }

        // Log the chosen outcome so the post-run route story records the decision (order-10). The EVENT
        // node's token is already "event:<id>"; this appends the choice, e.g. "event:distress_beacon:answered".
        String token = effect.logToken();
        if (token != null && !success) {
            token = token + "_failed";
        }
        if (token != null) {
            runStats.annotateLastRouteNode(token);
        }
        Gdx.app.log("RouteMap", "event=" + (activeEvent != null ? activeEvent.id() : "?")
                + " choice=" + choice.label() + " success=" + success);
    }

    /**
     * Grants {@code boxCount} ammo boxes spread across the player's OWNED ammo types (route-map
     * order-10 EVENT reward), reusing each type's {@code amountPerBox}. Falls back to ALL ammo types for
     * a melee-only run so the reward is never wasted. Directly credits the reserve (an event hands the
     * ammo over, rather than stamping tiles on the current floor).
     */
    private void grantAmmoBoxes(int boxCount) {
        java.util.List<AmmoType> ammoTypes = collectOwnedAmmoTypes();
        if (ammoTypes.isEmpty()) {
            for (AmmoType ammoType : AmmoType.values()) ammoTypes.add(ammoType);
        }
        int typeCount = ammoTypes.size();
        // Distribute the boxes as evenly as possible across owned types (first types get any remainder).
        for (int typeIndex = 0; typeIndex < typeCount; typeIndex++) {
            int boxesForType = boxCount / typeCount + (typeIndex < boxCount % typeCount ? 1 : 0);
            if (boxesForType <= 0) continue;
            AmmoType ammoType = ammoTypes.get(typeIndex);
            itemInventory.addAmmo(ammoType, boxesForType * ammoType.getAmountPerBox());
        }
    }

    /** The distinct ammo types the player's equipped loadout can use (empty for a melee-only run). */
    private java.util.List<AmmoType> collectOwnedAmmoTypes() {
        java.util.List<AmmoType> types = new java.util.ArrayList<>();
        Loadout loadout = inventory.getLoadout();
        if (loadout != null) {
            for (int slotIndex = 0; slotIndex < loadout.getSlotCount(); slotIndex++) {
                Weapon weapon = loadout.getSlot(slotIndex);
                if (weapon == null) continue;
                AmmoType ammoType = weapon.getAmmoType();
                if (ammoType != null && !types.contains(ammoType)) types.add(ammoType);
            }
        }
        return types;
    }

    /**
     * Reveals the NEXT region on the route map (route-map order-10 EVENT reward — a free scan), reusing
     * {@link RouteMap#revealRegion(int)}. No-op on a route-less world or when there is no deeper region
     * yet mapped. The next region is the one after the one that owns the current depth.
     */
    private void revealNextRegion() {
        if (routeMap == null || routePlan == null) return;
        int currentRegionIndex;
        try {
            currentRegionIndex = routePlan.regionForDepth(currentDepth).regionIndex();
        } catch (IllegalArgumentException outOfPlan) {
            return; // depth not covered by the plan (defensive) — nothing to reveal
        }
        int nextRegionIndex = currentRegionIndex + 1;
        if (nextRegionIndex < 0 || nextRegionIndex >= routeMap.getRegions().size()) return;
        routeMap.revealRegion(nextRegionIndex);
        if (eventTextSystem != null) {
            eventTextSystem.spawnWithColor("SECTOR MAPPED", EventTextSystem.COLOR_GOLD);
        }
    }

    /**
     * Announces + logs a region transition the first time the descent enters each region (route-map
     * order-10 theming). Reads the region's {@link RegionAmbience} identity for its display name and
     * gate flavour, shows the "ENTERING: …" beat, and records the region to the run story. Generic — it
     * fires on every region change (boss or gate), not just on a gate node. No-op on a route-less world.
     */
    private void detectRegionEntry() {
        if (routePlan == null) return;
        RegionSpec region;
        try {
            region = routePlan.regionForDepth(currentDepth);
        } catch (IllegalArgumentException outOfPlan) {
            return; // depth not covered by the plan (defensive)
        }
        if (region.regionIndex() == lastAnnouncedRegionIndex) {
            return; // already announced this region
        }
        lastAnnouncedRegionIndex = region.regionIndex();
        RegionAmbience ambience = RouteRegistries.regionAmbience().getOrFallback(region.themeId());
        String regionName = ambience != null ? ambience.displayName() : region.displayName();
        if (eventTextSystem != null) {
            eventTextSystem.spawnWithColor(RouteMapConstants.GATE_ANNOUNCE_PREFIX + regionName,
                    EventTextSystem.COLOR_GOLD);
            if (ambience != null) {
                eventTextSystem.spawnWithColor(ambience.gateFlavour(), EventTextSystem.COLOR_WHITE);
            }
        }
        runStats.recordRegionEntry(region.regionIndex(), regionName);
        Gdx.app.log("RouteMap", "region-entry index=" + region.regionIndex() + " name=" + regionName
                + (ambience != null ? " faction=" + ambience.enemyFactionId()
                        + " music=" + ambience.musicId() : ""));
    }

    private void applyInventoryConsumableEffect() {
        if (inventoryOverlayRenderer == null) return;
        ItemType consumed = inventoryOverlayRenderer.getLastConsumedItemType();
        if (consumed == null) return;
        int healAmount = 0;
        if (consumed == ItemType.MEDKIT_SMALL) {
            healAmount = ItemConstants.MEDKIT_STIM_HEAL;
        } else if (consumed == ItemType.MEDKIT_LARGE) {
            healAmount = ItemConstants.MEDKIT_FULL_HEAL;
        }
        if (healAmount > 0) {
            player.applyHealing(healAmount);
            runStats.recordHealUsed(); // resource-economy telemetry (order 3)
            if (eventTextSystem != null) {
                eventTextSystem.spawnWithColor("+" + healAmount + " HP", EventTextSystem.COLOR_GREEN);
            }
            // Visual heal feedback: rising green '+' particles and a soft green edge vignette,
            // mirroring the weapon lifesteal procs so a medkit reads as an obvious restore.
            impactEffectSystem.spawnHealParticles();
            hitVignetteRenderer.triggerHeal();
        }
    }

    // -------------------------------------------------------------------------
    // Weapon inspect overlay — open/close and resolution callbacks
    // -------------------------------------------------------------------------

    /** Opens the weapon inspect overlay for the weapon the player is currently standing on. */
    private void openWeaponInspectOverlay() {
        if (runPhase != RunPhase.PLAYING || playerController == null) return;
        GroundItem standingOn = playerController.getStandingOnWeapon();
        if (standingOn == null || !groundItems.contains(standingOn)) return;
        if (touchInputState != null) touchInputState.resetAllButtonStates();
        Weapon arsenalWeapon = findWeaponForGroundItem(standingOn);
        boolean meleeGround  = arsenalWeapon instanceof MeleeWeapon;
        // For a melee ground weapon compare against the held melee; otherwise use the active ranged
        // slot so the ACTIVE column shows comparable stats rather than mismatched weapon classes.
        Weapon activeWeapon  = meleeGround ? inventory.getMeleeWeapon() : inventory.getLoadout().active();
        int    convertAmount = computeConvertAmount(standingOn);
        weaponInspectOverlayRenderer.setFacilityTime(facilityTimeSeconds);
        weaponInspectOverlayRenderer.show(standingOn, standingOn.weaponRoll,
                arsenalWeapon, activeWeapon, inventory.getLoadout(), isStartingRoom, convertAmount, meleeGround);
        runPhase = RunPhase.WEAPON_INSPECT;
    }

    private Weapon findWeaponForGroundItem(GroundItem groundItem) {
        if (groundItem == null) return null;
        if (startRoomMeleeGroundItems != null) {
            int meleeIndex = startRoomMeleeGroundItems.indexOf(groundItem);
            if (meleeIndex >= 0 && meleeIndex < startRoomMeleeWeapons.size()) {
                return startRoomMeleeWeapons.get(meleeIndex);
            }
        }
        Weapon arsenalWeapon = (playerController != null)
                ? playerController.findWeaponInArsenalForType(groundItem.stack.getType())
                : null;
        if (arsenalWeapon == null) {
            // Melee weapons aren't kept in the ranged arsenal — build a transient instance so the
            // inspect card can show accurate melee base stats.
            arsenalWeapon = createMeleeWeaponForType(groundItem.stack.getType());
        }
        return arsenalWeapon;
    }

    private int computeConvertAmount(GroundItem groundItem) {
        if (groundItem == null || isStartingRoom) return 0;
        AmmoType ammoType = PlayerController.weaponItemTypeToAmmoType(groundItem.stack.getType());
        return (ammoType != null) ? ammoType.getAmountPerBox() : 0;
    }

    /** Closes the weapon inspect overlay and returns to PLAYING. */
    private void closeWeaponInspect() {
        weaponInspectOverlayRenderer.hide();
        if (touchInputState != null) touchInputState.resetAllButtonStates();
        runPhase = RunPhase.PLAYING;
    }

    /**
     * EQUIP resolution: called when the player taps the primary action button on the card.
     * Handles three cases: start-room melee pick, start-room ranged pick, and normal pickup.
     * For normal pickups the ground WeaponRoll is applied to the arsenal singleton before equipping.
     */
    private void resolveWeaponTake() {
        if (playerController == null) { closeWeaponInspect(); return; }
        GroundItem standingOn = playerController.getStandingOnWeapon();
        if (standingOn == null || !groundItems.contains(standingOn)) { closeWeaponInspect(); return; }

        // Start-room melee branch
        if (!startRoomMeleeChoiceResolved && startRoomMeleeGroundItems != null) {
            int meleeIndex = startRoomMeleeGroundItems.indexOf(standingOn);
            if (meleeIndex >= 0) {
                resolveStartRoomMeleeTake(standingOn, meleeIndex);
                closeWeaponInspect();
                return;
            }
        }

        // Start-room ranged branch
        if (!startRoomChoiceResolved && startRoomGroundItems != null) {
            int rangedIndex = startRoomGroundItems.indexOf(standingOn);
            if (rangedIndex >= 0) {
                resolveStartRoomRangedTake(standingOn, rangedIndex);
                closeWeaponInspect();
                return;
            }
        }

        // Melee pickup — route to the dedicated melee (first) slot, never a gun slot. Melee weapons
        // are not stored in the ranged arsenal, so this must be handled before the ranged path below
        // (which would otherwise find nothing and silently drop the pickup).
        ItemType groundType = standingOn.stack.getType();
        if (isMeleeWeaponType(groundType)) {
            resolveMeleeTake(standingOn, groundType);
            closeWeaponInspect();
            return;
        }

        // Normal ranged pickup.
        Weapon weapon = playerController.findWeaponInArsenalForType(groundType);
        if (weapon == null) { closeWeaponInspect(); return; }
        WeaponRoll groundRoll = standingOn.weaponRoll;
        Loadout    loadout    = inventory.getLoadout();
        int        existingSlot = loadout.slotIndexOf(weapon);

        if (existingSlot >= 0) {
            // The player already carries this weapon TYPE. Because the arsenal keeps a single
            // instance per type, the loadout can never hold two of the same type — so this is
            // never a "second pickup" but a VARIANT SWAP: replace the held weapon's roll with
            // the ground roll and drop the old variant so the exchange is reversible.
            // A different level OR tier OR ability set counts as a genuinely different weapon.
            swapVariantInSlot(existingSlot, weapon, standingOn, groundRoll);
            closeWeaponInspect();
            return;
        }

        // Brand-new weapon type — apply the ground roll then equip into a free slot.
        if (groundRoll != null && groundRoll.tier != null) {
            weapon.configureRoll(groundRoll.weaponLevel,
                                 groundRoll.tier,
                                 groundRoll.abilities != null ? groundRoll.abilities : new AbilityInstance[0]);
        }
        loadout.tryEquip(weapon);
        inventory.selectRangedActive();
        weaponHudRenderer.setEquippedWeapon(inventory.getEquippedWeapon());
        groundItems.remove(standingOn);
        playerController.clearStandingOnWeapon();
        if (eventTextSystem != null) {
            eventTextSystem.spawnWithColor("EQUIPPED: " + weapon.getDisplayName(), EventTextSystem.COLOR_GREEN);
        }
        fireTurnTick();
        closeWeaponInspect();
    }

    /**
     * Swaps the variant held in {@code slotIndex} for the ground weapon's variant.
     *
     * Same weapon type, different roll: the held singleton is reconfigured to the ground roll
     * and the player's previous variant is dropped on their tile (old roll preserved) so the
     * swap can be undone by stepping back onto it. If the ground roll is identical to the held
     * one there is nothing to swap, so the duplicate is converted to ammo instead (or simply
     * discarded in the start room where conversion is disabled).
     */
    private void swapVariantInSlot(int slotIndex, Weapon heldWeapon,
                                   GroundItem standingOn, WeaponRoll groundRoll) {
        WeaponRoll heldRoll = WeaponRoll.fromWeapon(heldWeapon);

        // Identical variant — no meaningful swap. Convert to ammo (reuses the convert path)
        // when allowed, otherwise just remove the duplicate from the floor.
        boolean sameVariant = (groundRoll == null && heldRoll == null)
                || (groundRoll != null && groundRoll.matches(heldRoll));
        if (sameVariant) {
            if (computeConvertAmount(standingOn) > 0) {
                convertGroundWeaponToAmmo(standingOn);
            } else if (eventTextSystem != null) {
                eventTextSystem.spawn("ALREADY EQUIPPED");
            }
            groundItems.remove(standingOn);
            playerController.clearStandingOnWeapon();
            fireTurnTick();
            return;
        }

        // Apply the ground variant to the held singleton.
        if (groundRoll != null && groundRoll.tier != null) {
            heldWeapon.configureRoll(groundRoll.weaponLevel, groundRoll.tier,
                    groundRoll.abilities != null ? groundRoll.abilities : new AbilityInstance[0]);
        }

        // Drop the player's previous variant on their tile so the swap is reversible.
        int playerTileColumn = MathUtils.floor(player.positionX / Constants.CELL_SIZE);
        int playerTileRow    = MathUtils.floor(player.positionY / Constants.CELL_SIZE);
        GroundItem droppedItem = new GroundItem(playerTileColumn, playerTileRow,
                weaponClassToItemType(heldWeapon), 1);
        droppedItem.weaponRoll = heldRoll;
        spawnGroundItem(droppedItem);

        inventory.getLoadout().selectSlot(slotIndex);
        inventory.selectRangedActive();
        weaponHudRenderer.setEquippedWeapon(inventory.getEquippedWeapon());
        groundItems.remove(standingOn);
        playerController.clearStandingOnWeapon();
        if (eventTextSystem != null) {
            eventTextSystem.spawnWithColor("SWAPPED: " + heldWeapon.getDisplayName(), EventTextSystem.COLOR_GREEN);
        }
        fireTurnTick();
    }

    private void resolveStartRoomRangedTake(GroundItem standingOn, int rangedIndex) {
        Weapon chosenWeapon = startRoomWeapons.get(rangedIndex);
        // The arsenal weapon already has the correct roll applied during setupStartRoomWeaponOffers.
        inventory.getLoadout().tryEquip(chosenWeapon);
        inventory.selectRangedActive();
        weaponHudRenderer.setEquippedWeapon(chosenWeapon);
        addStarterAmmoForWeapon(chosenWeapon);
        // Collect unchosen offers first, then remove — avoids any confusion with concurrent mutation.
        java.util.List<GroundItem> toRemove = new java.util.ArrayList<>();
        for (int otherIndex = 0; otherIndex < startRoomGroundItems.size(); otherIndex++) {
            if (otherIndex != rangedIndex) toRemove.add(startRoomGroundItems.get(otherIndex));
        }
        groundItems.removeAll(toRemove);
        groundItems.remove(standingOn);
        playerController.clearStandingOnWeapon();
        startRoomChoiceResolved = true;
        if (eventTextSystem != null) {
            eventTextSystem.spawnWithColor("EQUIPPED: " + chosenWeapon.getDisplayName(), EventTextSystem.COLOR_GREEN);
        }
    }

    private void resolveStartRoomMeleeTake(GroundItem standingOn, int meleeIndex) {
        MeleeWeapon chosenMelee = (MeleeWeapon) startRoomMeleeWeapons.get(meleeIndex);
        inventory.setMeleeWeapon(chosenMelee);
        inventory.selectMeleeActive();
        weaponHudRenderer.setEquippedWeapon(chosenMelee);
        java.util.List<GroundItem> toRemove = new java.util.ArrayList<>();
        for (int otherIndex = 0; otherIndex < startRoomMeleeGroundItems.size(); otherIndex++) {
            if (otherIndex != meleeIndex) toRemove.add(startRoomMeleeGroundItems.get(otherIndex));
        }
        groundItems.removeAll(toRemove);
        groundItems.remove(standingOn);
        playerController.clearStandingOnWeapon();
        startRoomMeleeChoiceResolved = true;
        if (eventTextSystem != null) {
            eventTextSystem.spawnWithColor("MELEE: " + chosenMelee.getDisplayName(), EventTextSystem.COLOR_GREEN);
        }
    }

    /**
     * EVICT resolution: remove the weapon in the given loadout slot, drop it on the player's
     * current tile, equip the ground weapon in its place, and fire one tick.
     */
    private void resolveWeaponEvict(int slotIndex) {
        if (playerController == null) { closeWeaponInspect(); return; }
        GroundItem standingOn = playerController.getStandingOnWeapon();
        if (standingOn == null || !groundItems.contains(standingOn)) { closeWeaponInspect(); return; }
        Weapon newWeapon = playerController.findWeaponInArsenalForType(standingOn.stack.getType());
        if (newWeapon == null) { closeWeaponInspect(); return; }

        // If the ground weapon's TYPE already occupies a different slot, evicting the chosen slot
        // and equipping it would place the single arsenal instance into two slots at once.
        // Redirect to a variant swap on the slot that actually holds this type instead, leaving
        // the tapped slot untouched.
        int existingSlot = inventory.getLoadout().slotIndexOf(newWeapon);
        if (existingSlot >= 0 && existingSlot != slotIndex) {
            swapVariantInSlot(existingSlot, newWeapon, standingOn, standingOn.weaponRoll);
            closeWeaponInspect();
            return;
        }

        // Snapshot the slot weapon's roll BEFORE any modifications.
        // For same-class swaps (e.g., Chaingun for Chaingun) evicted == newWeapon (same singleton),
        // so we must capture the old roll now before applying the ground roll.
        Weapon     slotWeapon  = inventory.getLoadout().getSlot(slotIndex);
        WeaponRoll evictedRoll = (slotWeapon != null) ? WeaponRoll.fromWeapon(slotWeapon) : null;

        Weapon evicted = inventory.getLoadout().removeSlot(slotIndex);

        // Apply the ground weapon's roll to the singleton that is about to be equipped.
        WeaponRoll groundRoll = standingOn.weaponRoll;
        if (groundRoll != null && groundRoll.tier != null) {
            newWeapon.configureRoll(groundRoll.weaponLevel, groundRoll.tier,
                    groundRoll.abilities != null ? groundRoll.abilities : new AbilityInstance[0]);
        }

        if (evicted != null) {
            int playerTileColumn = MathUtils.floor(player.positionX / Constants.CELL_SIZE);
            int playerTileRow    = MathUtils.floor(player.positionY / Constants.CELL_SIZE);
            GroundItem droppedItem = new GroundItem(playerTileColumn, playerTileRow, weaponClassToItemType(evicted), 1);
            droppedItem.weaponRoll = evictedRoll;
            spawnGroundItem(droppedItem);
            if (eventTextSystem != null) eventTextSystem.spawn("DROPPED: " + evicted.getDisplayName());
        }
        inventory.getLoadout().tryEquip(newWeapon);
        inventory.selectRangedActive();
        weaponHudRenderer.setEquippedWeapon(inventory.getEquippedWeapon());
        groundItems.remove(standingOn);
        playerController.clearStandingOnWeapon();
        if (eventTextSystem != null) {
            eventTextSystem.spawnWithColor("EQUIPPED: " + newWeapon.getDisplayName(), EventTextSystem.COLOR_GREEN);
        }
        fireTurnTick();
        closeWeaponInspect();
    }

    /**
     * CONVERT resolution: exchange the ground weapon for ammo of the matching type and fire
     * one tick. Used when the player already carries this weapon in their loadout.
     */
    private void resolveWeaponConvert() {
        if (playerController == null) { closeWeaponInspect(); return; }
        GroundItem standingOn = playerController.getStandingOnWeapon();
        if (standingOn == null || !groundItems.contains(standingOn)) { closeWeaponInspect(); return; }
        convertGroundWeaponToAmmo(standingOn);
        groundItems.remove(standingOn);
        playerController.clearStandingOnWeapon();
        fireTurnTick();
        closeWeaponInspect();
    }

    /**
     * Adds ammo for the ground weapon's type to the inventory and spawns feedback text.
     * Does NOT remove the ground item, fire a tick, or close the overlay — the caller owns
     * that cleanup so this can be reused both by the CONVERT button and the identical-variant
     * branch of {@link #swapVariantInSlot}.
     */
    private void convertGroundWeaponToAmmo(GroundItem standingOn) {
        AmmoType ammoType = PlayerController.weaponItemTypeToAmmoType(standingOn.stack.getType());
        if (ammoType != null) {
            int ammoAmount  = ammoType.getAmountPerBox();
            int countBefore = itemInventory.countOf(ammoType.getItemType());
            itemInventory.tryAdd(ammoType.getItemType(), ammoAmount);
            int ammoAdded   = itemInventory.countOf(ammoType.getItemType()) - countBefore;
            if (eventTextSystem != null) {
                if (ammoAdded > 0) {
                    String msg = "+" + ammoAdded + " " + ammoType.getDisplayName().toUpperCase();
                    if (ammoAdded < ammoAmount) msg += " (INV FULL)";
                    eventTextSystem.spawnWithColor(msg, EventTextSystem.COLOR_GREEN);
                } else {
                    eventTextSystem.spawn("INVENTORY FULL");
                }
            }
        } else {
            if (eventTextSystem != null) eventTextSystem.spawn("Discarded");
        }
    }

    /** Adds a GroundItem to the live list; PropRenderer and LevelRenderer share the reference. */
    private void spawnGroundItem(GroundItem item) {
        groundItems.add(item);
    }

    /** Fires one world tick from the player's current tile (SKIP_TURN cause). */
    private void fireTurnTick() {
        if (tickEventBus == null) return;
        int playerTileColumn = MathUtils.floor(player.positionX / Constants.CELL_SIZE);
        int playerTileRow    = MathUtils.floor(player.positionY / Constants.CELL_SIZE);
        tickEventBus.fireTick(playerTileColumn, playerTileRow, player, TickCause.SKIP_TURN);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Applies the chosen upgrade card's stat deltas, advances the level, refreshes every derived
     * combat value, and returns the game to PLAYING state (idea 5: build diversity).
     *
     * <p>Each card is budget-equal, so whichever card the player picks their TOTAL power advances
     * by the same amount — only its SHAPE changes. Attribute deltas flow into {@link PlayerStats}
     * (read live for dodge / speed / reduction / accuracy); HP / armour / flat-damage deltas are
     * pushed to the Player and EnemyManager so they take effect immediately.</p>
     */
    private void applyUpgradeCard(UpgradeCard card) {
        // The boon's stat effects (shared with the shop's paid path, shop_order_4).
        applyUpgradeCardEffects(card);

        // Level-up ceremony: advance the level and record the pick so future offers lean into this
        // build. (The shop path deliberately does NOT do this — a purchase is not a level-up.)
        playerProgress.advanceLevel();
        upgradeCardDeck.registerPick(card);

        if (touchInputState != null) {
            touchInputState.resetAllButtonStates();
        }
        runPhase = RunPhase.PLAYING;
    }

    /**
     * Applies one boon's permanent-for-run stat effects and refreshes every derived combat value —
     * the single source of truth for "what a boon does". Shared by the level-up screen
     * ({@link #applyUpgradeCard}) and the UAC Fabricator's paid PLAYER_ABILITY path
     * ({@link #grantShopAbility}, shop_order_4), so a bought boon is identical to a level-up pick.
     *
     * <p>Attribute deltas flow into {@link PlayerStats} (read live for dodge / speed / reduction /
     * accuracy); HP / armour / flat-damage deltas are pushed to the Player and EnemyManager so they
     * take effect immediately. This method changes NO run/level/UI state — only the boon's effects.
     */
    private void applyUpgradeCardEffects(UpgradeCard card) {
        // 1. Permanent attribute deltas — read live by PlayerStats-backed systems.
        if (card.strengthDelta != 0)     playerStats.addPermanent(Attribute.STRENGTH,     card.strengthDelta);
        if (card.agilityDelta != 0)      playerStats.addPermanent(Attribute.AGILITY,      card.agilityDelta);
        if (card.toughnessDelta != 0)    playerStats.addPermanent(Attribute.TOUGHNESS,    card.toughnessDelta);
        if (card.marksmanshipDelta != 0) playerStats.addPermanent(Attribute.MARKSMANSHIP, card.marksmanshipDelta);

        // 2. Health: a TOUGHNESS point also raises the max-HP pool (its flat reduction is read live).
        int maxHealthDelta = card.maxHealthDelta + card.toughnessDelta * GameBalance.TGH_HP_PER_POINT;
        if (maxHealthDelta != 0) player.adjustMaxHealth(maxHealthDelta);

        // 3. Armour.
        if (card.maxArmorDelta != 0) player.adjustMaxArmor(card.maxArmorDelta);

        // 4. Flat per-shot damage — accumulated in progression, pushed to the damage pipeline.
        if (card.flatDamageDelta != 0) {
            playerProgress.addFlatDamageBonus(card.flatDamageDelta);
            enemyManager.setPlayerFlatDamageBonus(playerProgress.getFlatDamageBonus());
        }

        // 5. Re-push the STRENGTH/MARKSMANSHIP damage multipliers so they take effect immediately.
        refreshPlayerDamageMultipliers();
    }

    /**
     * Grants a boon bought at a UAC Fabricator (shop_order_4). Reuses the level-up effect path
     * ({@link #applyUpgradeCardEffects}) so a paid boon matches the free one exactly, but spends NO
     * XP level and records no level-up pick — buying is not levelling. This is the
     * {@link ge.tbegvadze.toon3d.shop.ShopAbilityGrantor} the shop's ability service calls on purchase.
     */
    private void grantShopAbility(UpgradeCard boon) {
        applyUpgradeCardEffects(boon);
    }

    /**
     * Pushes the player's current melee/ranged damage multipliers (from STRENGTH / MARKSMANSHIP)
     * to the EnemyManager, which applies them centrally. Call after any attribute change and after
     * each floor rebuild so a fresh EnemyManager inherits the run's accumulated stats.
     */
    private void refreshPlayerDamageMultipliers() {
        enemyManager.setPlayerMeleeDamageMultiplier(playerStats.getMeleeDamageMultiplier());
        enemyManager.setPlayerRangedDamageMultiplier(playerStats.getRangedDamageMultiplier());
    }

    /*
     * Formula: floorSeed
     * Derivation: splitmix-style mixing — large prime multiplication distributes the
     *             seed bits, then adding depth ensures unique output per floor even when
     *             two run seeds differ only in low bits.
     * Edge cases: long overflow wraps safely; depth=0 still produces a valid seed.
     */
    private static long floorSeed(long runSeed, int depth) {
        return runSeed * 0x9E3779B97F4A7C15L + depth;
    }

    private static ILevelGenerator pickGenerator(long seed) {
        // XOR with a constant so the generator selection is independent of the floor layout seed.
        java.util.Random selectionRandom = new java.util.Random(seed ^ 0xDEADBEEFL);
        switch (selectionRandom.nextInt(3)) {
            case 0:  return new LevelGenerator(seed);
            case 1:  return new LinearCorridorGenerator(seed);
            default: return new CavernGenerator(seed);
        }
    }

    private static ILevelGenerator pickLevelGenerator(int depth, long seed) {
        if (GameMath.isBossFloor(depth)) return new BossArenaGenerator(seed);
        return pickGenerator(seed);
    }

    /** Records a boss-arena generator's chosen layout (ORDER 7) for this floor; no-op for other generators. */
    private void captureBossArenaLayout(ILevelGenerator generator) {
        if (generator instanceof BossArenaGenerator) {
            pendingBossArenaLayout = ((BossArenaGenerator) generator).getLayout();
        }
    }

    private static Boss createBossForDepth(int depth, long bossSeed, BossArenaLayout arenaLayout) {
        int spawnColumn = arenaLayout.bossColumn;
        int spawnRow    = arenaLayout.bossRow;
        // HP and every verb's damage are DERIVED from depth (order 6): the boss's whole stat block comes
        // from BossBalance, never a flat constant. The archetype rotation matches BossBalance.archetypeForDepth.
        BossBalance.Archetype archetype = BossBalance.archetypeForDepth(depth);
        BossStats stats = BossBalance.statsForDepth(archetype, depth);
        Boss boss;
        switch (archetype) {
            case OVERSEER:
                boss = new Boss(EnemyType.OVERSEER, spawnColumn, spawnRow,
                        "The Overseer", "Eye of the Abyss", "OVERSEER DESTROYED",
                        EnemyConstants.OVERSEER_ACCENT_R, EnemyConstants.OVERSEER_ACCENT_G,
                        EnemyConstants.OVERSEER_ACCENT_B, 1.80f,
                        new HunterKillerPattern(HunterKillerPattern.Pool.PHASE1, bossSeed),
                        new HunterKillerPattern(HunterKillerPattern.Pool.PHASE2, bossSeed));
                boss.nameTag = "The Overseer LVL " + depth;
                break;
            case CORRUPTOR:
                boss = new Boss(EnemyType.CORRUPTOR, spawnColumn, spawnRow,
                        "The Corruptor", "Herald of Decay", "CORRUPTOR PURGED",
                        EnemyConstants.CORRUPTOR_ACCENT_R, EnemyConstants.CORRUPTOR_ACCENT_G,
                        EnemyConstants.CORRUPTOR_ACCENT_B, 1.60f,
                        new CorruptorPhase1Pattern(), new CorruptorPhase2Pattern());
                boss.nameTag = "The Corruptor LVL " + depth;
                break;
            case HELL_BARON:
            default:
                boss = new Boss(EnemyType.HELL_BARON, spawnColumn, spawnRow,
                        "Hell Baron", "Lord of Flame", "HELL BARON FALLS",
                        EnemyConstants.HELL_BARON_ACCENT_R, EnemyConstants.HELL_BARON_ACCENT_G,
                        EnemyConstants.HELL_BARON_ACCENT_B, 2.00f,
                        new HellBaronPhase1Pattern(), new HellBaronPhase2Pattern());
                boss.nameTag = "Hell Baron LVL " + depth;
                break;
        }
        boss.stats        = stats;
        boss.maxHealth    = stats.effectiveHitPoints;
        boss.health       = stats.effectiveHitPoints;
        boss.dungeonLevel = depth;
        return boss;
    }

    private static float findPlayerStartX(Level level) {
        for (int tileRow = 0; tileRow < level.getHeight(); tileRow++) {
            for (int tileColumn = 0; tileColumn < level.getWidth(); tileColumn++) {
                if (level.getCell(tileColumn, tileRow) == 'p') return tileColumn * Constants.CELL_SIZE + Constants.CELL_SIZE / 2f;
            }
        }
        return Constants.WORLD_WIDTH / 2f;
    }

    private static float findPlayerStartY(Level level) {
        for (int tileRow = 0; tileRow < level.getHeight(); tileRow++) {
            for (int tileColumn = 0; tileColumn < level.getWidth(); tileColumn++) {
                if (level.getCell(tileColumn, tileRow) == 'p') return tileRow * Constants.CELL_SIZE + Constants.CELL_SIZE / 2f;
            }
        }
        return Constants.WORLD_HEIGHT / 2f;
    }

    // -------------------------------------------------------------------------
    // Start room — weapon offer setup and selection logic
    // -------------------------------------------------------------------------

    /**
     * Spawns three randomly chosen weapon GroundItems at the pre-computed offer tiles,
     * adds them to the live ground-item list, and registers the weapon-selection callback
     * on the PlayerController.
     *
     * Must be called after buildLevelDependentResources() so that groundItems and
     * playerController both exist.
     */
    private void setupStartRoomWeaponOffers(StartGameLevelGenerator startGen, long runSeed) {
        // --- Ranged offers: pick 3 from the shuffled ranged arsenal ---
        java.util.List<Weapon> shuffledRanged = new java.util.ArrayList<>(inventory.getArsenal());
        java.util.Collections.shuffle(shuffledRanged, new java.util.Random(floorSeed(runSeed, 0)));

        int rangedOfferCount = Math.min(
                ge.tbegvadze.toon3d.util.LevelGenConstants.START_ROOM_WEAPON_OFFER_COUNT,
                shuffledRanged.size());

        startRoomWeapons     = new java.util.ArrayList<>();
        startRoomGroundItems = new java.util.ArrayList<>();

        ge.tbegvadze.toon3d.entity.WeaponTier startRoomMaxTier =
                GameBalance.START_ROOM_ANY_TIER_ENABLED
                        ? ge.tbegvadze.toon3d.entity.WeaponTier.LEGENDARY
                        : GameBalance.START_ROOM_OFFER_MAX_TIER;

        for (int offerIndex = 0; offerIndex < rangedOfferCount; offerIndex++) {
            Weapon     offeredWeapon = shuffledRanged.get(offerIndex);
            weaponRoller.rollWeaponWithMinTier(offeredWeapon, 1,
                    GameBalance.START_ROOM_OFFER_MIN_TIER, startRoomMaxTier);
            ItemType   itemType      = weaponClassToItemType(offeredWeapon);
            GroundItem groundItem    = new GroundItem(
                    startGen.getWeaponTileColumn(offerIndex),
                    startGen.getWeaponTileRow(offerIndex),
                    itemType, 1);
            groundItem.weaponRoll = WeaponRoll.fromWeapon(offeredWeapon);
            groundItems.add(groundItem);
            startRoomWeapons.add(offeredWeapon);
            startRoomGroundItems.add(groundItem);
        }

        // --- Melee offers: 3 non-fist melee weapons (Fist is already the default) ---
        java.util.List<Weapon> meleePool = new java.util.ArrayList<>(
                java.util.Arrays.asList(new CombatKnife(), new Hammer(), new MeleeChainsaw()));
        java.util.Collections.shuffle(meleePool, new java.util.Random(floorSeed(runSeed, 0) + 1));

        int meleeOfferCount = Math.min(
                ge.tbegvadze.toon3d.util.LevelGenConstants.START_ROOM_MELEE_OFFER_COUNT,
                meleePool.size());

        startRoomMeleeWeapons     = new java.util.ArrayList<>();
        startRoomMeleeGroundItems = new java.util.ArrayList<>();

        for (int offerIndex = 0; offerIndex < meleeOfferCount; offerIndex++) {
            Weapon     offeredWeapon = meleePool.get(offerIndex);
            weaponRoller.rollWeaponWithMinTier(offeredWeapon, 1,
                    GameBalance.START_ROOM_OFFER_MIN_TIER, startRoomMaxTier);
            ItemType   itemType      = weaponClassToItemType(offeredWeapon);
            GroundItem groundItem    = new GroundItem(
                    startGen.getMeleeTileColumn(offerIndex),
                    startGen.getMeleeTileRow(offerIndex),
                    itemType, 1);
            groundItem.weaponRoll = WeaponRoll.fromWeapon(offeredWeapon);
            groundItems.add(groundItem);
            startRoomMeleeWeapons.add(offeredWeapon);
            startRoomMeleeGroundItems.add(groundItem);
        }
    }

    /** Grants the starter ammo reserve appropriate for the weapon class just chosen. */
    private void addStarterAmmoForWeapon(Weapon weapon) {
        AmmoType ammoType = null;
        int      amount   = 0;
        if (weapon instanceof Shotgun || weapon instanceof DoubleBarrelShotgun) {
            ammoType = AmmoType.SHELLS;
            amount   = ItemConstants.START_ROOM_AMMO_SHELLS;
        } else if (weapon instanceof PlasmaRifle || weapon instanceof Incinerator
                || weapon instanceof ArcCannon) {
            ammoType = AmmoType.CELLS;
            amount   = ItemConstants.START_ROOM_AMMO_CELLS;
        } else if (weapon instanceof Chaingun || weapon instanceof AssaultRifle) {
            ammoType = AmmoType.BULLETS;
            amount   = ItemConstants.START_ROOM_AMMO_BULLETS;
        } else if (weapon instanceof Railgun) {
            ammoType = AmmoType.SLUGS;
            amount   = ItemConstants.START_ROOM_AMMO_SLUGS;
        } else if (weapon instanceof GrenadeLauncher) {
            ammoType = AmmoType.ROCKETS;
            amount   = ItemConstants.START_ROOM_AMMO_ROCKETS;
        }
        if (ammoType != null) {
            itemInventory.tryAdd(ammoType.getItemType(), amount);
        }
    }

    /**
     * Maps a weapon instance to the closest matching ItemType for GroundItem rendering.
     * This is used only for billboard appearance — the actual weapon equipped is tracked
     * separately in startRoomWeapons.
     */
    private static ItemType weaponClassToItemType(Weapon weapon) {
        if (weapon instanceof DoubleBarrelShotgun) return ItemType.WEAPON_DOUBLE_BARREL;
        if (weapon instanceof Shotgun)             return ItemType.WEAPON_SHOTGUN;
        if (weapon instanceof Chaingun)            return ItemType.WEAPON_CHAINGUN;
        if (weapon instanceof AssaultRifle)        return ItemType.WEAPON_ASSAULT_RIFLE;
        if (weapon instanceof Railgun)             return ItemType.WEAPON_RAILGUN;
        if (weapon instanceof Incinerator)         return ItemType.WEAPON_INCINERATOR;
        if (weapon instanceof ArcCannon)           return ItemType.WEAPON_ARC_CANNON;
        if (weapon instanceof PlasmaRifle)         return ItemType.WEAPON_PLASMA;
        if (weapon instanceof GrenadeLauncher)     return ItemType.WEAPON_ROCKET;
        if (weapon instanceof MeleeChainsaw)       return ItemType.WEAPON_CHAINSAW;
        if (weapon instanceof Hammer)               return ItemType.WEAPON_HAMMER;
        if (weapon instanceof CombatKnife)         return ItemType.WEAPON_KNIFE;
        if (weapon instanceof Fist)                return ItemType.WEAPON_FIST;
        return ItemType.WEAPON_PISTOL;
    }

    /** True when the ground item type is a melee weapon (belongs in the melee/first slot, never a gun slot). */
    private static boolean isMeleeWeaponType(ItemType type) {
        return type == ItemType.WEAPON_KNIFE
            || type == ItemType.WEAPON_HAMMER
            || type == ItemType.WEAPON_CHAINSAW;
    }

    /**
     * Builds and configures a fresh melee weapon instance for the given item type, or returns null
     * for non-melee types. Melee weapons are not kept in the ranged arsenal (the way guns are), so
     * they are constructed on demand both for the inspect card's base stats and for actual pickup.
     */
    private MeleeWeapon createMeleeWeaponForType(ItemType type) {
        MeleeWeapon melee;
        if (type == ItemType.WEAPON_KNIFE)         melee = new CombatKnife();
        else if (type == ItemType.WEAPON_HAMMER)   melee = new Hammer();
        else if (type == ItemType.WEAPON_CHAINSAW) melee = new MeleeChainsaw();
        else return null;
        melee.setEventTextSystem(eventTextSystem);
        melee.setPlayerAccuracyMultiplier(playerStats.getAccuracyMultiplier());
        weaponRoller.configureRunStart(melee);
        return melee;
    }

    /**
     * Whether a rolled weapon drop counts as a real UPGRADE for the pity rule (new-game-balancr
     * order 2): a tier of UNCOMMON or better. COMMON drops are vanilla and do not satisfy a region's
     * guaranteed-upgrade quota.
     */
    private boolean isWeaponUpgrade(WeaponRoll weaponRoll) {
        return weaponRoll != null
                && weaponRoll.tier.ordinal() >= WeaponRoller.upgradeTierThreshold().ordinal();
    }

    /**
     * Equips a melee ground weapon into the dedicated melee (first) slot — melee weapons can only ever
     * go here, never into a gun slot. The previously held melee weapon (unless it is the default Fist)
     * is dropped on the player's tile so the swap is reversible. Applies the ground roll, selects melee
     * as the active weapon, and advances the world one turn.
     */
    private void resolveMeleeTake(GroundItem standingOn, ItemType groundType) {
        MeleeWeapon newMelee = createMeleeWeaponForType(groundType);
        if (newMelee == null) return;

        WeaponRoll groundRoll = standingOn.weaponRoll;
        if (groundRoll != null && groundRoll.tier != null) {
            newMelee.configureRoll(groundRoll.weaponLevel, groundRoll.tier,
                    groundRoll.abilities != null ? groundRoll.abilities : new AbilityInstance[0]);
        }

        // Drop the previous melee weapon (except the permanent Fist fallback) so the swap can be undone.
        MeleeWeapon previousMelee = inventory.getMeleeWeapon();
        if (previousMelee != null && !(previousMelee instanceof Fist)) {
            int playerTileColumn = MathUtils.floor(player.positionX / Constants.CELL_SIZE);
            int playerTileRow    = MathUtils.floor(player.positionY / Constants.CELL_SIZE);
            GroundItem droppedItem = new GroundItem(playerTileColumn, playerTileRow,
                    weaponClassToItemType(previousMelee), 1);
            droppedItem.weaponRoll = WeaponRoll.fromWeapon(previousMelee);
            spawnGroundItem(droppedItem);
            if (eventTextSystem != null) eventTextSystem.spawn("DROPPED: " + previousMelee.getDisplayName());
        }

        inventory.setMeleeWeapon(newMelee);
        inventory.selectMeleeActive();
        weaponHudRenderer.setEquippedWeapon(inventory.getEquippedWeapon());
        groundItems.remove(standingOn);
        playerController.clearStandingOnWeapon();
        if (eventTextSystem != null) {
            eventTextSystem.spawnWithColor("MELEE: " + newMelee.getDisplayName(), EventTextSystem.COLOR_GREEN);
        }
        fireTurnTick();
    }

    /** Builds a snapshot map of ItemType → WeaponTier for all weapons currently in the arsenal. */
    private java.util.Map<ItemType, WeaponTier> buildWeaponTierMap() {
        java.util.Map<ItemType, WeaponTier> tierMap = new java.util.HashMap<>();
        for (Weapon weapon : inventory.getArsenal()) {
            tierMap.put(weaponClassToItemType(weapon), weapon.getTier());
        }
        MeleeWeapon meleeWeapon = inventory.getMeleeWeapon();
        if (meleeWeapon != null) {
            tierMap.put(weaponClassToItemType(meleeWeapon), meleeWeapon.getTier());
        }
        return tierMap;
    }
}
