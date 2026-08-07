package ge.tbegvadze.toon3d.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.Viewport;
import ge.tbegvadze.toon3d.door.DoorManager;
import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.enemy.EnemyManager;
import ge.tbegvadze.toon3d.entity.*;
import ge.tbegvadze.toon3d.hazard.ExplosiveBarrelManager;
import ge.tbegvadze.toon3d.hazard.HazardManager;
import ge.tbegvadze.toon3d.hazard.SpireManager;
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
import ge.tbegvadze.toon3d.entity.boss.BossFactory;
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
import ge.tbegvadze.toon3d.narrative.BarkCatalog;
import ge.tbegvadze.toon3d.narrative.BarkSystem;
import ge.tbegvadze.toon3d.narrative.BarkTrigger;
import ge.tbegvadze.toon3d.narrative.BootCardCatalog;
import ge.tbegvadze.toon3d.narrative.BootCardSystem;
import ge.tbegvadze.toon3d.narrative.BootCardVariant;
import ge.tbegvadze.toon3d.narrative.CodexCatalog;
import ge.tbegvadze.toon3d.narrative.CodexCategory;
import ge.tbegvadze.toon3d.narrative.CodexSystem;
import ge.tbegvadze.toon3d.narrative.ControlHint;
import ge.tbegvadze.toon3d.narrative.ExchangeCatalog;
import ge.tbegvadze.toon3d.narrative.ExchangeSystem;
import ge.tbegvadze.toon3d.narrative.ExchangeTrigger;
import ge.tbegvadze.toon3d.narrative.FramingMenu;
import ge.tbegvadze.toon3d.narrative.IntroBeat;
import ge.tbegvadze.toon3d.narrative.PauseMenuItem;
import ge.tbegvadze.toon3d.narrative.StoryProgress;
import ge.tbegvadze.toon3d.narrative.TitleMenuItem;
import ge.tbegvadze.toon3d.narrative.TitleScreenSystem;
import ge.tbegvadze.toon3d.narrative.StoryRegion;
import ge.tbegvadze.toon3d.narrative.StoryTermCatalog;
import ge.tbegvadze.toon3d.narrative.StorySettings;
import ge.tbegvadze.toon3d.narrative.StoryStrings;
import ge.tbegvadze.toon3d.narrative.StoryTelemetry;
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
import ge.tbegvadze.toon3d.util.StoryStore;
import ge.tbegvadze.toon3d.util.StoryUiConstants;
import ge.tbegvadze.toon3d.util.ItemConstants;
import ge.tbegvadze.toon3d.util.RenderConstants;
import ge.tbegvadze.toon3d.util.RouteMapConstants;
import ge.tbegvadze.toon3d.util.ProgressionConstants;
import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.util.EnemyConstants;

public class World implements Renderable, Disposable, LevelTransitionListener {

    private enum RunPhase { TITLE, BOOT_CARD, PLAYING, FADING_OUT, FADING_IN, LEVEL_UP_OVERLAY, DEAD, INVENTORY_OPEN, WEAPON_INSPECT, SHOP_OPEN, ROUTE_SELECT, EVENT_CHOICE, STORY_EXCHANGE, CODEX_OPEN, PAUSE_MENU, SETTINGS_MENU, ENDING_HOLD }

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
    // Crystal-spire simulation for the Verdant Spiresower (rebuilt per floor, like HazardManager). Holds no
    // GPU resources; cleared on floor teardown so no stamped solid tile is ever leaked.
    private SpireManager           spireManager;
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
    /**
     * The depth at which the current region was entered, so the Organization's demand exchange can
     * land a floor LATER than the region gate (Story UI order-5). The region-entry exchange already
     * fires at the gate; asking two blocking questions with no gameplay between them is the one
     * pacing failure order-6 Part B names by name. -1 before the first region is entered.
     */
    private int                     regionEntryDepth = -1;

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

    /**
     * Unread facility-terminal tiles on the current floor (Story UI order-5's LOG channel). Each entry
     * is a {@code {tileColumn, tileRow}} pair; stepping orthogonally adjacent to one is "the player
     * read what was left on it" and fires the LOG_FOUND moment, reusing the same auto-trigger idiom as
     * the heal station and the EVENT console so reading never costs a button. Refreshed on every floor
     * rebuild and consumed after {@code STORY_LOG_TAKES_PER_FLOOR} takes — a server room holds a dozen
     * terminals and ORA has one opinion about them.
     */
    private java.util.List<int[]> unreadLogTerminals  = new java.util.ArrayList<>();
    private int                   logTakesThisFloor   = 0;

    /**
     * True once this floor's boss has been reported down to the bark layer (narrative-rework
     * order-5 D). Per-floor, because the beat it fires is one-shot in the narrative layer anyway —
     * this only stops the world asking again on every frame after the kill.
     */
    private boolean bossDefeatBarkRequested = false;

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
    private       float                deathBeatTimerSeconds  = 0f;
    private       boolean              resetRequested         = false;
    /** Guards the records against being filed twice when a run ends through two seams at once. */
    private       boolean              runRecordsSaved        = false;

    // -------------------------------------------------------------------------
    // Story UI — BARK LAYER (order-2): the non-blocking one-liner channel.
    // barkSystem is the headless brain (which line, when, one-shot flags, region gating, rate
    // limit); storyBarkRenderer only draws whatever it says is active.  Narrative progress is
    // PERSISTENT (StoryStore), so death never rewinds the story — the pools are gated by the
    // deepest story region ever reached, not by this run.
    // -------------------------------------------------------------------------
    private final BarkSystem         barkSystem;
    private final StoryBarkRenderer  storyBarkRenderer;
    // Story UI — AUDIO (order-7 Part D): ONE shared owner of every story sound (the four speaker
    // stings, the three interface cues), handed to each story renderer. Sound is Disposable and
    // procedurally synthesised, so four channels must not each build their own copy. Silenced
    // wholesale by the codex's SOUND setting; the game is fully understandable with it off.
    private final StoryAudio         storyAudio;
    // Story UI — REPRINT / BOOT CARD (order-3): the modal card shown at the start of every run,
    // which is to say on every reprint. bootCardSystem is the headless brain (which variant, which
    // region-appropriate ORA line, what the instance counter reads, whether continuing is even
    // allowed); bootCardRenderer only draws what it reports.
    private final BootCardSystem     bootCardSystem;
    private final BootCardRenderer   bootCardRenderer;
    // Story UI — EXCHANGES (order-4): the BLOCKING half of the channel. A handful of moments per
    // region stop the world and ask the player to tap one of 2-3 answers; exchangeSystem is the
    // headless brain (which exchange, what the answers do to the hidden stance model, which reply
    // comes back), storyExchangeRenderer only draws what it reports. Rare by design — the bark
    // layer carries ~80% of the story precisely so this one can stay special.
    private final ExchangeSystem         exchangeSystem;
    private final StoryExchangeRenderer  storyExchangeRenderer;
    /** Which answer plate the thumb went down on, so a tap that slides off is cancelled. */
    private int                          exchangePressedIndex = -1;
    /** Seeded stream for "does this moment even ask for a line" rolls (kills). */
    private final java.util.Random   storyMomentRandom;
    private StoryBarkTickSubscriber  storyBarkTickSubscriber;
    /** Edge detector for the low-health bark: fires on the way DOWN through the threshold only. */
    private boolean                  healthAboveLowThreshold = true;
    // Story UI — MOMENT SCHEDULE (order-5): the run-scoped latches behind the cold open and the
    // ending.  Everything else in the schedule is latched PERSISTENTLY by the narrative layer's
    // one-shot flags; these two are per-run because they answer "has this run started / finished
    // yet", which is a question about this run and nothing else.
    private boolean                  coldOpenRequested;
    private boolean                  coreEndingRequested;
    /**
     * Set once the player has committed to an ending.  The run is over: no sim, no ticks, no input.
     * For the three endings that print a card the BOOT_CARD phase is already holding the screen; for
     * MERGE there is no card, and this flag is the whole of "the interface stopped being yours".
     */
    private boolean                  runEnded;
    /** Swipe-to-dismiss tracking: set when a touch goes down inside the bark panel. */
    private boolean                  barkSwipeTracking;
    private float                    barkSwipeStartX;
    private float                    barkSwipeStartY;

    // -------------------------------------------------------------------------
    // Story UI — CODEX, PACING, ACCESSIBILITY, TELEMETRY (order-6): the wrap-up.
    // The codex is the opt-in ARCHIVE of everything the story channels already said, opened from a
    // MENU and never from play; codexSystem is the headless brain (what is unlocked, which tab and
    // entry are open, where the body is scrolled) and codexOverlayRenderer only draws what it
    // reports.  storySettings holds the three accessibility knobs, persisted alongside every other
    // narrative flag.  storyTelemetry is in-memory tuning counters only — no identifiers, no I/O.
    // -------------------------------------------------------------------------
    private final StorySettings        storySettings;
    private final CodexSystem          codexSystem;
    private final CodexOverlayRenderer codexOverlayRenderer;
    private final StoryTelemetry       storyTelemetry = new StoryTelemetry();
    /** The phase to restore when the archive closes — it is a page of a menu, not a detour. */
    private RunPhase                   codexReturnPhase = RunPhase.PLAYING;
    /** Drag-to-scroll tracking for the codex body: where the finger went down, and how far it went. */
    private boolean                    codexDragTracking;
    private float                      codexDragLastY;
    private float                      codexDragTravel;
    private float                      codexTouchDownX;
    private float                      codexTouchDownY;
    private float                      codexScrollAtTouchDown;

    // -------------------------------------------------------------------------
    // Story UI — FRAMING SCREENS (order-8): the screens that BOOK-END play.
    // The launch/title screen (which opens on the game telling the player they are already dead),
    // the in-suit pause menu, the settings screen, the reprint report folded into the death beat,
    // and the way off an ending that authorises no reprint.  titleScreenSystem is the headless
    // brain; framingScreenRenderer draws whatever it and the two menu models report.
    // -------------------------------------------------------------------------
    private final TitleScreenSystem      titleScreenSystem;
    private final FramingScreenRenderer  framingScreenRenderer;
    private final InstanceReportRenderer instanceReportRenderer;
    private final FramingMenu            pauseMenu    = new FramingMenu();
    private final FramingMenu            settingsMenu = new FramingMenu();
    /** Which row the thumb went down on in whichever framing menu is up, so a slide-off cancels. */
    private int      framingPressedRow    = -1;
    /** The phase the settings screen was opened from — it is a page of a menu, not a detour. */
    private RunPhase settingsReturnPhase  = RunPhase.TITLE;
    /** True while the reprint card is showing its REPORT page instead of the card. */
    private boolean  showingInstanceReport;
    /** True when tapping CONTINUE on the card on screen ends this world rather than resuming it. */
    private boolean  bootCardEndsWorld;
    /** How the world Main builds next should open — the death/abandon/ending handshake. */
    private StartMode nextStartMode       = StartMode.TITLE;
    /** MERGE presents no card: the frozen view holds, fading, for this long before standing down. */
    private float    endingHoldSeconds;

    // -------------------------------------------------------------------------
    // Timing accumulators
    // -------------------------------------------------------------------------
    private float alertTimeSeconds    = 0f;
    private float facilityTimeSeconds = 0f;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Creates a new game from a seed, opening on the launch / title screen (Story UI order-8). */
    public World(long runSeed) {
        this(runSeed, StartMode.TITLE);
    }

    /**
     * Creates a new world from a seed, opening the way {@code startMode} asks: on the title screen
     * (app launch, an abandoned run, a finished story) or straight into a run, fading up from black
     * (a reprint, whose card the world that just died already showed).
     */
    public World(long runSeed, StartMode startMode) {
        this(new StartGameLevelGenerator(runSeed), runSeed, startMode);
    }

    /** Creates a World from a pre-built level (file-loaded or test). Uses a random run seed. */
    public World(Level level) {
        this(level, System.currentTimeMillis(), false, null, StartMode.RESPAWN);
    }

    public World(String levelFile) {
        this(new LevelLoader().load(levelFile), System.currentTimeMillis(), false, null,
             StartMode.RESPAWN);
    }

    private World(StartGameLevelGenerator startGen, long runSeed, StartMode startMode) {
        this(startGen.generate(), runSeed, true, startGen, startMode);
    }

    private World(Level initialLevel, long runSeed, boolean startRoom,
                  StartGameLevelGenerator startRoomGen, StartMode startMode) {
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

        // Story UI bark layer (order-2) — run-persistent, but its NARRATIVE state is cross-run:
        // StoryProgress reads/writes the deepest story region reached and the one-shot beat flags
        // through StoryStore (Preferences), so a reprint never replays a beat or rewinds ORA's tone.
        // The string table comes from the externalised asset, falling back to the built-in defaults.
        barkSystem = new BarkSystem(BarkCatalog.defaultRegistry(), StoryStringsLoader.load(),
                                    new StoryProgress(new StoryStore()),
                                    runSeed ^ StoryUiConstants.STORY_BARK_SELECTION_SEED_SALT);
        storyMomentRandom = new java.util.Random(runSeed ^ StoryUiConstants.STORY_MOMENT_SEED_SALT);
        // One audio owner for every story channel (order-7 Part D). Built before the renderers so
        // each can be handed the same instance; disposed once, by this class.
        storyAudio        = new StoryAudio();
        storyBarkRenderer = new StoryBarkRenderer();
        storyBarkRenderer.setBarkSystem(barkSystem);
        storyBarkRenderer.setStoryAudio(storyAudio);

        // Story UI boot card (order-3) — shares the bark layer's StoryProgress, so the card's ORA
        // line is gated by the same persistent deepest-region-reached and its instance counter
        // climbs across deaths and app restarts alike. The string table is loaded once, above.
        StoryStrings storyStrings = StoryStringsLoader.load();
        bootCardSystem = new BootCardSystem(BootCardCatalog.defaultRegistry(), storyStrings,
                                            barkSystem.getProgress(),
                                            runSeed ^ StoryUiConstants.STORY_BOOT_WAKE_SEED_SALT);
        bootCardRenderer = new BootCardRenderer();
        bootCardRenderer.setBootCardSystem(bootCardSystem);
        bootCardRenderer.setStrings(storyStrings);
        bootCardRenderer.setStoryAudio(storyAudio);

        // Story UI exchanges (order-4) — shares the same persistent StoryProgress as the bark layer
        // and the boot card, so a one-shot exchange answered on an earlier run never re-asks, and
        // the hidden stance those answers build survives every death.
        exchangeSystem = new ExchangeSystem(ExchangeCatalog.defaultRegistry(), storyStrings,
                                            barkSystem.getProgress(),
                                            runSeed ^ StoryUiConstants.STORY_EXCHANGE_SELECTION_SEED_SALT);
        storyExchangeRenderer = new StoryExchangeRenderer();
        storyExchangeRenderer.setExchangeSystem(exchangeSystem);
        storyExchangeRenderer.setStrings(storyStrings);
        storyExchangeRenderer.setStoryAudio(storyAudio);

        // Story UI codex + accessibility (order-6) — the archive shares the same persistent
        // StoryProgress as every other channel, so an entry unlocked two runs ago is still there,
        // and the settings persist beside it. Applying them here (rather than per frame) is what
        // makes a size change take effect on the next line rather than on the next launch.
        storySettings        = new StorySettings(new StoryStore());
        codexSystem          = new CodexSystem(CodexCatalog.defaultRegistry(), storyStrings,
                                               barkSystem.getProgress(), storySettings);
        codexOverlayRenderer = new CodexOverlayRenderer();
        codexOverlayRenderer.setCodexSystem(codexSystem);
        codexOverlayRenderer.setStrings(storyStrings);
        applyStoryAccessibilitySettings();

        // Story UI FRAMING SCREENS (order-8) — the launch screen, the two menus, and the report the
        // death beat folds in. The title screen shares the same persistent StoryProgress as every
        // other channel, so it knows whether there is anything to CONTINUE into and which ending
        // (if any) this save has already reached.
        titleScreenSystem      = new TitleScreenSystem(BootCardCatalog.defaultRegistry(), storyStrings,
                                                       barkSystem.getProgress());
        framingScreenRenderer  = new FramingScreenRenderer();
        framingScreenRenderer.setStrings(storyStrings);
        framingScreenRenderer.setTitleScreenSystem(titleScreenSystem);
        instanceReportRenderer = new InstanceReportRenderer();
        instanceReportRenderer.setStrings(storyStrings);

        // HOW THIS WORLD OPENS (order-8). A run is one World, so ending one means building the next;
        // what the player sees at that seam is the whole of StartMode:
        //   TITLE   — the app just launched, or a run was abandoned or finished. The descent waits.
        //   RESPAWN — a reprint. The card was already printed by the world that DIED, which is what
        //             makes death -> reprint ONE presentation; here the screen simply fades up.
        // A file-loaded or test world is neither: it is not a run, so it starts playing at once.
        if (startRoom && startMode == StartMode.TITLE) {
            runPhase = RunPhase.TITLE;
        } else if (startRoom) {
            fadeTimerSeconds = 0f;
            runPhase         = RunPhase.FADING_IN;
        }

        // Event text and hit vignette — run-persistent feedback systems
        eventTextSystem     = new EventTextSystem();
        eventTextRenderer   = new EventTextRenderer(eventTextSystem);
        hitVignetteRenderer = new HitVignetteRenderer();
        abilityFeedback     = new AbilityFeedback(eventTextSystem, impactEffectSystem);
        abilityFeedback.setHealVignetteRenderer(hitVignetteRenderer);
        abilityFeedback.setLegendaryVignetteRenderer(hitVignetteRenderer);

        // Player stat system — seeded from the ONE canonical starting block (BalanceConfig
        // SECTION 7). The game has exactly one difficulty (new-game-balancr order 8): there is
        // no selection step, so a run starts straight into the first floor.
        playerStats = new PlayerStats();
        player.setPlayerStats(playerStats);
        player.setRandomSeed(runSeed ^ 0xD0D6E5EEDL);   // AGILITY dodge rolls join the seeded run
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

        // Permadeath — run stats. The report that used to be its own death screen is now a page of
        // the reprint card's screen (order-8 Part B); its renderer is built with the framing screens.
        runStats             = new RunStats();
        persistentStats      = StatsStore.load();

        // Wire damage listener: player damage triggers vignette flash, screen text, and stats.
        player.setPlayerDamageListener(netDamage -> {
            hitVignetteRenderer.setIntensity(1f);
            eventTextSystem.spawnDamage(netDamage);
            runStats.recordDamageTaken(netDamage);
            runStats.recordFloorDamageTaken(netDamage);   // RUN AUTOPSY (order 9): per-floor drain
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
        // Every weapon's accuracy/crit stream is seeded off the run seed (new-game-balancr order 9)
        // so a run is reproducible end to end; the per-weapon offset keeps the streams independent.
        int weaponSeedOffset = 0;
        for (Weapon weapon : new Weapon[]{shotgun, dblShotgun, plasmaRifle, chaingun, assaultRifle, railgun, incinerator, arcCannon, grenadeLauncher}) {
            weapon.setRandomSeed(runSeed + (weaponSeedOffset++ * 0x9E3779B97F4A7C15L));
            weapon.setEventTextSystem(eventTextSystem);
            weapon.setAmmoInventory(itemInventory);
            weapon.setRangedDamageMultiplier(rangedMultiplier);
            weapon.setPlayerAccuracyMultiplier(accuracyMultiplier);
            weaponRoller.configureRunStart(weapon);
        }

        // Melee slot — the Fist is the always-available fallback; wired at run start and never removed.
        Fist fist = new Fist();
        fist.setRandomSeed(runSeed + (weaponSeedOffset++ * 0x9E3779B97F4A7C15L));
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
            requestRouteMapBarks();
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
        requestRouteMapBarks();
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
        long seed = GameMath.floorSeed(runSeed, currentDepth);
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
        // Restore any spire-stamped tiles on the OUTGOING level before it is replaced, so no solid tile is
        // ever leaked across a floor transition. The SpireManager holds no GPU resources (no dispose()).
        if (spireManager != null) spireManager.clear();
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
        // Seeded from the floor seed (new-game-balancr order 9): spawn/drop/effect rolls replay
        // identically for the same run seed + depth, so a shared seed is a shared run.
        enemyManager           = new EnemyManager(targetLevel, doorManager, currentDepth,
                                                  GameMath.floorSeed(runSeed, currentDepth));
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
            // Story bark (order-2): kills are frequent, barks must not be. Only a fraction of kills
            // even ASK, and the bark layer's own cooldown decides whether that ask is answered.
            if (storyMomentRandom.nextFloat() < StoryUiConstants.STORY_BARK_KILL_CHANCE) {
                barkSystem.request(BarkTrigger.KILL);
            }
        });
        // THE BESTIARY VOICE (narrative-rework order-5 C): the first time the player watches a
        // special ability RESOLVE, ORA says the rule it plays by. Every row is one-shot and
        // mandatory in the narrative layer, so this fires on every resolution and is answered five
        // times in the life of a save — the five rules a player otherwise learns by dying.
        enemyManager.setSpecialResolvedListener(ability ->
                barkSystem.request(BarkTrigger.ENEMY_ABILITY_RESOLVED, ability.name()));
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
        hazardManager = new HazardManager(targetLevel, enemyManager, statusEffectController,
                                          GameMath.floorSeed(runSeed, currentDepth) ^ 0x4A2A5D9BL);
        hazardManager.setExplosiveBarrelManager(explosiveBarrelManager);
        hazardManager.setHazardVisualListener(propRenderer::addDynamicProp);
        explosiveBarrelManager.setDetonationListener(hazardManager::igniteFireFromExplosion);
        // Re-wire the incinerator's tile-ignition sink to THIS floor's HazardManager (rebuilt
        // per floor). Done alongside the other per-floor hazard wiring so it survives transitions.
        if (incinerator != null) {
            incinerator.setHazardIgniteTarget(hazardManager::igniteFire);
        }
        // MOLTEN TRAIL (elemental-golem-cinderforge-colossus): the Colossus lights the tile it vacates
        // through the SAME shipped hazard path, so its fire spreads and chain-detonates barrels for free.
        // Re-wired here alongside the incinerator's sink because HazardManager is rebuilt per floor.
        enemyManager.setHazardIgniteTarget(hazardManager);

        // Crystal-spire simulation (elemental-golem-verdant-spiresower) — the Verdant Spiresower's solid,
        // healing spires. Rebuilt per floor like the HazardManager; it stamps the spire char onto the grid
        // (auto-culled from PropRenderer when a tile is restored) and fires its birth/shatter bursts through
        // the impact system. Wired into EnemyManager through the entity-package SpireHitTarget seam.
        spireManager = new SpireManager(targetLevel, doorManager);
        spireManager.setVisualListener(propRenderer::addDynamicProp);
        spireManager.setImpactEventListener(impactEffectSystem);
        enemyManager.setSpireHitTarget(spireManager);
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
            // A dying magma construct leaves ONE last fire tile under itself
            // (elemental-golem-cinderforge-colossus): the furnace goes out where it fell. Data-driven off
            // the archetype's own trail duration rather than a type switch, so any future magma golem
            // inherits the beat by declaring leavesTrailFireTurns().
            if (deadType.leavesTrailFireTurns() > 0) {
                hazardManager.igniteFire(tileColumn, tileRow, deadType.leavesTrailFireTurns());
            }
        });

        // Boss encounter — built before the pipeline so TickPipeline can slot it in ahead of the
        // ordinary roster (the boss acts first each turn).
        bossFloorController = null;
        bossHudRenderer     = null;
        if (GameMath.isBossFloor(currentDepth) && pendingBossArenaLayout != null) {
            Boss boss = BossFactory.createForDepth(currentDepth, GameMath.floorSeed(runSeed, currentDepth),
                                                   pendingBossArenaLayout);
            if (boss != null) {
                enemyManager.addBoss(boss);
                bossHudRenderer     = new BossHudRenderer();
                bossHudRenderer.setBoss(boss);
                bossFloorController = new BossFloorController(boss, targetLevel, pendingBossArenaLayout, doorManager,
                        enemyManager, hazardManager, bossHudRenderer, eventTextSystem);
            }
        }
        gameState.isBossFloor        = bossFloorController != null;
        bossDefeatBarkRequested      = false;
        // RUN AUTOPSY (order 9): a fresh floor resets the per-floor drain and boss-turn counters.
        runStats.beginFloor(gameState.isBossFloor);

        // The shared assembly (TickPipeline) owns the dispatch ORDER, so a simulated floor
        // (sim.SimWorld) resolves its turns in exactly the order a played floor does.
        tickEventBus = TickPipeline.standardFloor(inventory, hazardManager, spireManager,
                                                  statusEffectController,
                                                  player, enemyManager, playerStats,
                                                  bossFloorController, gameState);
        // RUN AUTOPSY (order 9): count the turns a boss fight actually takes, so the reported number
        // can be compared with the modelled fight length (R-BOSS-FAIR).
        tickEventBus.subscribe(context -> {
            runStats.recordTick();
            runStats.recordBossFloorTurn();
        });

        // Story bark layer (order-2): the turn stream is where "a new enemy family woke up",
        // "the player is circling" and "the player has stopped acting" are visible. Rebuilt with
        // the floor so its per-floor memory (walked tiles) resets exactly when the floor does.
        storyBarkTickSubscriber = new StoryBarkTickSubscriber(barkSystem, exchangeSystem, enemyManager,
                                                              targetLevel.getWidth(),
                                                              targetLevel.getHeight());
        tickEventBus.subscribe(storyBarkTickSubscriber);

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
        // The in-suit pause menu (Story UI order-8 Part C) — a menu, so it costs no turn.
        playerController.setPauseMenuCallback(this::openPauseMenu);
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

        // Story LOG terminals (order-5): every computer terminal on the floor is something the crew
        // left behind, so walking up to one is a moment. Scanned once per floor rebuild — this is the
        // only full-grid pass here and it happens on a level change, never per frame.
        unreadLogTerminals = new java.util.ArrayList<>();
        logTakesThisFloor  = 0;
        for (int tileColumn = 0; tileColumn < targetLevel.getWidth(); tileColumn++) {
            for (int tileRow = 0; tileRow < targetLevel.getHeight(); tileRow++) {
                if (targetLevel.getCell(tileColumn, tileRow) == StoryUiConstants.STORY_LOG_TERMINAL_SYMBOL) {
                    unreadLogTerminals.add(new int[]{tileColumn, tileRow});
                }
            }
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
        // Seeded off the floor seed (order 9 determinism audit) — chip placement is part of the run.
        java.util.Random chipRandom = new java.util.Random(GameMath.floorSeed(runSeed, depth) ^ 0xC417C417L);
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

        java.util.Random random = new java.util.Random(GameMath.floorSeed(runSeed, depth) ^ 0x5309CAFED00DL);
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
        long baseSeed       = GameMath.floorSeed(runSeed, depth);
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
        // OVERLAY PRECEDENCE (order-7 Part E): a hard-pause overlay — death, level-up, inventory,
        // shop, weapon inspect, the nav console, an event choice — owns the screen. Suppress the
        // bark layer while one is open: requests still QUEUE, nothing is delivered or aged, and the
        // queue resumes intact afterwards. Two story panels never stack, and a bark never talks over
        // a modal.
        barkSystem.setSuppressed(runPhase != RunPhase.PLAYING);

        // TITLE phase (order-8 Part A) — the launch screen. Nothing is running: the world behind it
        // is built but frozen and not even drawn, and the descent begins only when the player picks
        // it. The screen opens on the game telling them they are already dead.
        if (runPhase == RunPhase.TITLE) {
            updateTitleScreen(deltaTime);
            return;
        }

        // PAUSE_MENU / SETTINGS_MENU (order-8 Parts C and D) — the in-suit menus. Hard-pause
        // overlays like every other: the world takes no turn, the bark queue is suppressed above and
        // resumes intact, and nothing in either screen is on a timer.
        if (runPhase == RunPhase.PAUSE_MENU) {
            updatePauseMenu();
            return;
        }
        if (runPhase == RunPhase.SETTINGS_MENU) {
            updateSettingsMenu();
            return;
        }

        // ENDING_HOLD (order-8 Part D) — MERGE. There is no card and there is no input: the last
        // frames are the frozen world the player was standing in, fading out, because the interface
        // has simply stopped being theirs (story/06-endings.md).
        if (runPhase == RunPhase.ENDING_HOLD) {
            endingHoldSeconds += deltaTime;
            if (endingHoldSeconds >= StoryUiConstants.STORY_ENDING_MERGE_HOLD_SECONDS) {
                standDownToTitle();
            }
            return;
        }

        // BOOT_CARD phase (order-3) — the reprint card owns the screen before control is handed
        // back at the checkpoint. The world is frozen behind it: no sim, no ticks, no barks.
        //
        // The card is NEVER on a timer and NEVER auto-advances, so a player who wants to sit with
        // the beat can. One tap on CONTINUE always proceeds — even mid-reveal — and a tap anywhere
        // else just finishes the staggered print-in, so the card can never trap anyone.
        //
        // Order-8 hangs two things off this screen. The REPORT page — the run that just ended, in
        // machine voice — so a death is ONE presentation rather than a stats screen competing with
        // the card. And the way off a TERMINAL card (the FREE / KILL endings): those print "NO
        // CHECKPOINT AUTHORIZED FOR REPRINT" and draw no CONTINUE, because that absence IS the
        // ending, so the only thing offered once the card has finished printing is standing down to
        // a title screen the ending has changed.
        if (runPhase == RunPhase.BOOT_CARD) {
            bootCardSystem.update(deltaTime);
            // Audio from the update path, never from render: the SYSTEM sting on the first status
            // line, a dry tick per line after it, ORA's sting when she speaks (order-7 Part D).
            bootCardRenderer.playPendingStoryAudio();
            if (gameViewport != null && Gdx.input.justTouched()) {
                if (touchInputState != null) touchInputState.consumeTapAction();
                cardTouchPosition.set(Gdx.input.getX(), Gdx.input.getY());
                gameViewport.unproject(cardTouchPosition);
                handleBootCardTap(cardTouchPosition.x, cardTouchPosition.y);
                if (resetRequested) return;   // the ending just stood the world down
            }
            if (bootCardSystem.isContinueRequested()) {
                bootCardSystem.clear();
                // Two cards end this world rather than resuming it: the reprint the player was just
                // printed from (the run is over — the next World is the new instance), and OBEY,
                // whose reward is more of the same forever, so continuing from it means exactly
                // that: a fresh print, the same descent, the counter one higher. Nothing else in the
                // game ends this way, which is the horror.
                if (runEnded || bootCardEndsWorld) {
                    saveRunRecordsOnce();
                    requestReset(StartMode.RESPAWN);
                    return;
                }
                runPhase = RunPhase.PLAYING;
            }
            return;
        }

        // STORY_EXCHANGE phase (order-4) — a conversation owns the screen. The world takes NO turns
        // while it is up: this is a turn-based game with an action lock, so pausing for an answer
        // costs the player nothing and there is no real-time pressure on the choice.
        //
        // There is no timer of any kind and nothing auto-selects. The only way past the prompt is to
        // tap an actual answer, which is the whole anti-skip design: the player has to at least
        // glance at what they are choosing between.
        if (runPhase == RunPhase.STORY_EXCHANGE) {
            exchangeSystem.update(deltaTime);
            updateStoryExchangeTouch();
            applyPendingExchangeReward();
            if (exchangeSystem.consumeFinished()) {
                exchangePressedIndex = -1;
                // The taps that answered the exchange also reached the touch controller behind it.
                // Clear them, or the finger that picked an answer immediately steps the player.
                if (touchInputState != null) {
                    touchInputState.resetAllButtonStates();
                    touchInputState.consumeTapAction();
                }
                runPhase = RunPhase.PLAYING;
            }
            return;
        }

        // CODEX_OPEN phase (order-6) — the archive owns the screen. It is a MENU: the world is
        // frozen, no turn passes, no bark is delivered (the queue is suppressed above and resumes
        // intact), and nothing in here is on a timer. The player asked for this screen and the
        // player decides when it ends.
        if (runPhase == RunPhase.CODEX_OPEN) {
            codexSystem.update(deltaTime);
            updateCodexTouch();
            return;
        }

        // DEAD phase (order-8 Part B, restaged by narrative-rework order-2) — the death BEAT: a
        // slow, quiet fade to black, and then THREE WORDS on it. No gory splash and no verdict; the
        // player has just died and may already be frustrated.
        //
        // The stroke is a beat of its OWN, ahead of the reprint card, because the two screens do two
        // different jobs: this one reports a death, the card reports a birth. Printed together —
        // which is what the game used to do — neither lands, and the death arrives buried under a
        // status block, a counter and two buttons. Here there is nothing but the words, and no ORA:
        // she does not get printed, she reloads, so the one second the player has no voice at all is
        // the loneliest the run ever gets. She is the first warm thing on the card that follows.
        //
        // It holds and then hands over by itself — the fade at this seam always has — and a tap
        // skips straight to the card, so an impatient player is never held and a slow one is never
        // rushed. Nothing is lost either way: the card behind it says all of it again, at length.
        if (runPhase == RunPhase.DEAD) {
            deathBeatTimerSeconds += deltaTime;
            boolean strokeSkipped = gameViewport != null && Gdx.input.justTouched()
                    && deathBeatTimerSeconds >= ProgressionConstants.DEATH_BEAT_DURATION_SECONDS;
            if (strokeSkipped || deathBeatTimerSeconds >= deathStrokeEndSeconds()) {
                saveRunRecordsOnce();
                presentReprintCard();
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
                    requestFloorArrivalBarks();
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
                    requestFloorArrivalBarks();
                } else {
                    currentDepth++;
                    playerProgress.setFloorDepth(currentDepth);
                    runStats.recordFloor(currentDepth);
                    rebuildForLevel(buildNextFloor());
                    applyPendingFloorEffects();
                    detectRegionEntry();
                    requestFloorArrivalBarks();
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
                } else if (touchAction == InventoryOverlayRenderer.CloseAction.OPEN_CODEX) {
                    openCodex();
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

        // Story exchange (order-4): a moment (a region entry, a deep-strata milestone) may have held
        // one back while the world was fading between floors — stopping the player inside a modal
        // over a black screen would read as a bug. It opens HERE instead: in play, with the player
        // standing still and nothing else owning the screen. The exchange takes the phase, so the
        // sim below does not run this frame and the world takes no turn while it is up.
        if (openPendingStoryExchange()) {
            return;
        }

        // The ending the player just chose (order-5). Consumed only once the exchange has faded, so
        // the last thing they read as themselves is the reply to their own answer. Everything below
        // is skipped from here on: the run is over.
        commitPendingStoryEnding();
        if (runEnded) {
            return;
        }

        // PLAYING phase — normal game simulation
        runStats.realSecondsPlayed += deltaTime;
        doorManager.update(deltaTime);
        playerController.update(deltaTime);

        // Death check: resolved after the tick fully completes, never mid-tick
        if (player.isDead()) {
            runStats.recordFloor(currentDepth);
            sealRunAutopsy();
            // Snapshot the run BEFORE the records are saved, so "RECORD" still means "this instance
            // beat what the save held" rather than "this instance equals what it just wrote".
            instanceReportRenderer.show(runStats, persistentStats);
            runPhase              = RunPhase.DEAD;
            deathBeatTimerSeconds = 0f;
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

        // Story LOG terminals (order-5): the same adjacency idiom again — walking up to a computer
        // terminal is reading it, and ORA has a line about what the crew left on it.
        updateLogTerminals();

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
        // Story bark layer (order-2): advance the fade/hold clock and deliver the next queued line,
        // then hand its speaker sting to the renderer (audio is an update-path side effect, never a
        // draw-path one). The idle clock only runs here, in PLAYING, so time spent inside an overlay
        // is never mistaken for the player standing still.
        // PACING BUDGET (order-6 Part B): tell the bark layer whether the player is in a fight
        // BEFORE it decides what to deliver, so a line queued mid-spike waits for the lull.
        barkSystem.setCombatSpike(isCombatSpike());
        barkSystem.update(deltaTime);
        archiveDeliveredStoryLine();
        requestCodexCompletionBarks();
        storyBarkRenderer.playPendingSpeakerSting();
        updateStoryBarkTouch();
        if (storyBarkTickSubscriber != null) storyBarkTickSubscriber.update(deltaTime);
        updateLowHealthStoryBark();
        // Story MOMENT SCHEDULE (order-5): the cold open once per run, the tutorial lines as each
        // control becomes useful, and — at the bottom of the descent, with the Core's boss down —
        // the ending choice. All three only ASK; the narrative layer's one-shot flags mean a veteran
        // player gets nothing from the first two and reaches the third exactly once.
        requestColdOpenBarks();
        requestControlHintBarks();
        requestBossDefeatedBark();
        requestCoreEndingExchange();
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
        // FRAMING SCREENS (order-8): while the launch screen owns the app there is no run to look
        // at, so the 3D view, the HUD and every in-run overlay are skipped entirely — the screen is
        // black, a machine prints a death notice on it, and that is all. The archive and the
        // settings screen may sit on top of it, because both are reachable before a run exists.
        if (isTitleFramed()) {
            framingScreenRenderer.renderTitle(camera);
            if (runPhase == RunPhase.SETTINGS_MENU) {
                framingScreenRenderer.renderMenu(camera, StoryUiConstants.STORY_SETTINGS_TITLE_ID,
                                                 settingsMenu);
            }
            if (runPhase == RunPhase.CODEX_OPEN) codexOverlayRenderer.render(camera);
            return;
        }

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
                && runPhase != RunPhase.EVENT_CHOICE
                && runPhase != RunPhase.BOOT_CARD
                && runPhase != RunPhase.STORY_EXCHANGE
                && runPhase != RunPhase.CODEX_OPEN
                && runPhase != RunPhase.PAUSE_MENU
                && runPhase != RunPhase.SETTINGS_MENU
                && runPhase != RunPhase.ENDING_HOLD) {
            touchControllerRenderer.setActionLocked(!playerController.isIdle());
            touchControllerRenderer.render(camera);
        }

        // Event text: rising screen-space text drawn above HUD but below the fade overlay.
        eventTextRenderer.render(camera);
        if (bossHudRenderer != null) bossHudRenderer.render(camera);

        // Story bark (order-2): the non-blocking one-liner panel, at its fixed top-centre anchor —
        // above the HUD, the thumb clusters, the weapon sprite and the mini-map. Drawn only while
        // PLAYING so it never sits on top of a modal overlay (its queue is suppressed there anyway).
        if (runPhase == RunPhase.PLAYING) {
            storyBarkRenderer.render(camera);
        }

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

        // Story exchange (order-4) — the blocking conversation panel. Brings its own dim, which
        // covers the frozen world behind it, so it is drawn above the HUD and event text and below
        // the boot card and the fade. The thumb clusters are not drawn at all in this phase (see
        // the touch-controller condition above), which is why the answer plates are free to use the
        // lower screen those buttons normally own.
        if (runPhase == RunPhase.STORY_EXCHANGE) {
            storyExchangeRenderer.render(camera);
        }

        // Codex (order-6) — the archive. Brings its own scrim over the frozen world, so it sits
        // above the HUD and every in-run overlay, and below only the boot card and the fade. It is
        // opened from a menu and never from play, so it never covers a beat in progress.
        if (runPhase == RunPhase.CODEX_OPEN) {
            codexOverlayRenderer.render(camera);
        }

        // Pause / settings menus (order-8 Parts C and D) — drawn over the frozen floor with their
        // own scrim, above every in-run overlay and below only the boot card and the fade. The thumb
        // clusters are not drawn in either phase (see the touch-controller condition above).
        if (runPhase == RunPhase.PAUSE_MENU) {
            framingScreenRenderer.renderMenu(camera, StoryUiConstants.STORY_PAUSE_TITLE_ID, pauseMenu);
        } else if (runPhase == RunPhase.SETTINGS_MENU) {
            framingScreenRenderer.renderMenu(camera, StoryUiConstants.STORY_SETTINGS_TITLE_ID,
                                             settingsMenu);
        }

        // Reprint / boot card (order-3) — the modal that opens every run. Its own dark full-bleed
        // background covers the frozen world behind it, so it is drawn above every other overlay
        // and below only the fade, exactly like the other hard-pause modals.
        //
        // Order-8 adds two things to this screen and nothing else: the REPORT page (which replaces
        // the card entirely while it is open — one screen at a time, always), and, on a terminal
        // ending card, the dim RETURN plate that stands where the CONTINUE deliberately is not.
        if (runPhase == RunPhase.BOOT_CARD) {
            if (showingInstanceReport) {
                instanceReportRenderer.render(camera);
            } else {
                bootCardRenderer.render(camera);
                float cardFade = bootCardSystem.getVisibleFraction();
                instanceReportRenderer.renderOpenButton(camera, cardFade);
                if (bootCardSystem.isTerminal() && bootCardSystem.isRevealComplete()) {
                    framingScreenRenderer.renderTerminalReturn(camera, cardFade);
                }
            }
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

        // Death beat (order-8 Part B, restaged by narrative-rework order-2): a slow fade to black,
        // and then three words on it. Nothing else is drawn at all — no counter, no status block,
        // no button, no ORA — until the reprint card takes the screen.
        if (runPhase == RunPhase.DEAD) {
            float deathFadeAlpha = Math.min(1f,
                    deathBeatTimerSeconds / ProgressionConstants.DEATH_BEAT_DURATION_SECONDS);
            fadeOverlayRenderer.render(camera, deathFadeAlpha, currentDepth);
            framingScreenRenderer.renderDeathStroke(camera, deathStrokeWordsFade());
        }

        // MERGE (order-8 Part D): the frozen world the player was standing in, going quietly out.
        // No card, no button, no prompt — the interface has stopped being theirs.
        if (runPhase == RunPhase.ENDING_HOLD) {
            float holdFraction = Math.min(1f,
                    endingHoldSeconds / StoryUiConstants.STORY_ENDING_MERGE_HOLD_SECONDS);
            fadeOverlayRenderer.render(camera, holdFraction, currentDepth);
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
        // Story bark layer (order-2): the renderer owns the story panel primitive (batch, shapes,
        // font). BarkSystem itself is headless — no GPU/audio handles, and the stings it plays
        // through belong to the shared storyAudio disposed below.
        storyBarkRenderer.dispose();
        // Reprint / boot card (order-3): the renderer owns a story panel primitive, its own shapes,
        // batch and font. BootCardSystem itself is headless — no GPU handles.
        bootCardRenderer.dispose();
        // Story exchanges (order-4): the renderer owns the story panel primitive and its own
        // shapes/batch/font. ExchangeSystem itself is headless.
        storyExchangeRenderer.dispose();
        // Story audio (order-7 Part D): the ONE owner of every story Sound — the four speaker stings
        // and the three interface cues. The renderers only borrow it, so it is disposed exactly here.
        storyAudio.dispose();
        // Codex (order-6): the archive renderer owns its own shapes, batch and font. CodexSystem
        // itself is headless — no GPU handles.
        codexOverlayRenderer.dispose();
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
        // Framing screens (order-8): the launch/menu renderer and the reprint report each own their
        // own shapes, batch and font. TitleScreenSystem and the two FramingMenus are headless.
        framingScreenRenderer.dispose();
        instanceReportRenderer.dispose();
        if (bossHudRenderer != null) { bossHudRenderer.dispose(); bossHudRenderer = null; }
        player.dispose();
    }

    /**
     * True once this world is finished — the player tapped through a reprint card, abandoned the
     * run, or an ending stood the game down.  {@code Main} then disposes it and builds the next one.
     */
    public boolean isResetRequested() { return resetRequested; }

    /**
     * How the world {@code Main} builds next should open (Story UI order-8): straight into a fresh
     * run after a reprint, or on the launch screen after an abandoned run or a finished story.
     */
    public StartMode getNextStartMode() { return nextStartMode; }

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
            java.util.Random gambleRandom = new java.util.Random(GameMath.floorSeed(runSeed, currentDepth) ^ 0xE7E1D1CEL);
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
                // A hand-set XP grant is authored at DEPTH 1 and re-expressed as the same share of a
                // level at the depth actually played (new-game-balancr order 7) — otherwise the flat
                // number silently decays to nothing against the geometric level curve and a deep EVENT
                // node becomes worthless. R-CALM-COST prices the event node on this scaled value.
                playerProgress.addXp(GameMath.depthScaledRewardXp(effect.grantXp(),
                        BalanceConfig.XP_BASE_REQUIREMENT, BalanceConfig.XP_CURVE_GROWTH_PER_LEVEL,
                        BalanceConfig.EXPECTED_LEVELS_PER_DEPTH, currentDepth));
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
        regionEntryDepth         = currentDepth;

        // STORY GATING (order-2 / order-7 Part A): record the story region this route region maps
        // onto BEFORE asking for beats, so the pools are selected at the new depth. reachRegion()
        // only ever moves forward — a fresh run starting at the surface cannot rewind the story.
        StoryRegion storyRegion = StoryRegion.fromRouteRegionIndex(region.regionIndex());
        barkSystem.getProgress().reachRegion(storyRegion);
        // ORA marks the stratum, then the Organization's cold order. Both are one-shot mandatory
        // beats: a later run re-entering a cleared region says nothing here.
        barkSystem.request(BarkTrigger.REGION_ENTERED);
        barkSystem.request(BarkTrigger.REGION_GATE_ORDER);
        // ...then ORA's aside on the words that just arrived (narrative-rework order-7 B). Asked for
        // on the SAME moment behind a subject key, immediately after the order, so her comment lands
        // behind the transmission rather than in front of it. Only two regions carry a row, so this
        // almost always asks for nothing — she comments when the paperwork has something to point at.
        barkSystem.request(BarkTrigger.REGION_GATE_ORDER, BarkCatalog.GATE_ASIDE_SUBJECT_KEY);
        // ...and, behind each of them, the ONE word this gate is allowed to teach (narrative-rework
        // order-3's vocabulary ladder). Asked for after the beat that describes the thing, so the
        // player always meets the description before the name: "this is where the cutting happened"
        // and then "yield is what these floors were sending up". Which term (if any) is due is data
        // — the catalog picks the shallowest word still unnamed, so there is no region conditional
        // here and adding a term needs no edit at this call site.
        StoryTermCatalog.requestNextIntro(barkSystem, BarkTrigger.REGION_ENTERED);
        StoryTermCatalog.requestNextIntro(barkSystem, BarkTrigger.REGION_GATE_ORDER);
        // ...and the region's one deliberate EXCHANGE (order-4), if it has not been answered on an
        // earlier run. Only HELD here: the region change happens mid-transition, and the exchange
        // opens once the player is actually standing on the floor (openPendingStoryExchange).
        exchangeSystem.request(ExchangeTrigger.REGION_ENTERED);

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

    /**
     * Story barks tied to arriving on a fresh floor (order-2): ORA's line about the room you just
     * walked into, plus — every {@code STORY_BARK_DEEP_STRATA_INTERVAL} floors — a deep-strata
     * milestone, which is the planet's channel.  The planet has no lines in Region 1, so the
     * milestone simply says nothing up there: its silence is the design, not a special case here.
     *
     * <p>Both are requests, not commands: the bark layer's rate limit, priority and one-shot rules
     * decide what (if anything) is actually spoken.
     */
    private void requestFloorArrivalBarks() {
        // THE PER-FLOOR BUDGET (narrative-rework order-8 B) resets HERE, before anything asks: this
        // is the single funnel every floor arrival passes through, and the budget is what makes "an
        // ordinary floor delivers two flavour lines" true rather than hoped for.  The staging room
        // resets it too — a fresh run starts with a clean allowance.
        barkSystem.beginFloor();
        if (isStartingRoom) return;   // the staging room is not a story floor
        // ORA introduces herself BEFORE she starts naming the place (narrative-rework order-2 D then
        // order-3). Both the cold open and this method fire on the first real floor, so asking here
        // first is what keeps "I'm ORA" ahead of "this is the Deepworks" in the queue. Guarded by
        // coldOpenRequested, so the per-frame PLAYING call below is unaffected — that one stays as
        // the catch-all for any path that reaches play without a floor arrival.
        requestColdOpenBarks();
        // Most floors ORA says nothing at all. Commenting on every single arrival is what made her
        // feel like a chatterbox; silence is part of the character (story/dialog/ai-assistant.md).
        // ...and from the Wound down she goes quiet MORE often, on purpose (narrative-rework
        // order-8 F): her saying nothing when the room is bad is the cheapest horror effect the game
        // has, and it is unavailable while something always fires. Rolled as a second, independent
        // chance rather than by re-tuning the arrival chance per region, so the deliberate silence
        // stays legible as its own decision.
        if (storyMomentRandom.nextFloat() < StoryUiConstants.STORY_BARK_FLOOR_ARRIVAL_CHANCE
                && !rollsDeliberateDeepSilence()) {
            barkSystem.request(BarkTrigger.FLOOR_ARRIVAL);
        }
        // One vocabulary naming per floor, never a probability roll: a word the player is about to
        // start reading everywhere is not flavour. The catalog's own region band decides which is
        // due, so the facility is named on floor one, the world it is dug into on floor two, and the
        // reserve waits for the Harvesting Galleries.
        StoryTermCatalog.requestNextIntro(barkSystem, BarkTrigger.FLOOR_ARRIVAL);
        // THE FIRST BOSS (narrative-rework order-5 D): the one enemy that earns a stop, and a BARK
        // rather than an exchange — the player is walking into the fight, and a modal there is
        // cruel. One-shot for the life of the save, so only the first arena anybody ever enters
        // gets it; every later boss floor asks and is answered with silence.
        if (gameState.isBossFloor) {
            barkSystem.request(BarkTrigger.BOSS_ARENA_ENTERED);
        }
        if (currentDepth > 0 && currentDepth % StoryUiConstants.STORY_BARK_DEEP_STRATA_INTERVAL == 0) {
            barkSystem.request(BarkTrigger.DEEP_STRATA);
            // The quiet deep-strata check-in (order-4). Every row is one-shot, so this asks often
            // and almost always gets nothing — which is exactly the budget an exchange should have.
            exchangeSystem.request(ExchangeTrigger.DEEP_STRATA);
        }
        // The Organization's demand for this region (order-5) — held back until at least one floor
        // of gameplay has passed since the gate, so it never lands back-to-back with the region-entry
        // exchange. Only two regions have a row, and both are one-shot, so this almost always asks
        // for nothing.
        if (regionEntryDepth >= 0
                && currentDepth - regionEntryDepth >= StoryUiConstants.STORY_ORGANIZATION_ORDER_FLOOR_DELAY) {
            exchangeSystem.request(ExchangeTrigger.ORGANIZATION_ORDER);
        }
    }

    /**
     * DELIBERATE SILENCE (narrative-rework order-8 F): true when this deep floor arrival is one ORA
     * says nothing on, on purpose.  Only from the Wound down, and gated on the DEEPEST region ever
     * reached (like every other pool in the layer), so a fresh run at the surface is unaffected while
     * a player who has been to the bottom carries her quiet with them.
     *
     * <p>Silence has to be a legitimate RESULT of a moment, not just an accident of the rate limit —
     * "every repeatable moment must be able to produce nothing at all" is the rule this implements.
     */
    private boolean rollsDeliberateDeepSilence() {
        if (!barkSystem.getProgress().getDeepestRegion().isAtLeast(StoryRegion.WOUND)) return false;
        return storyMomentRandom.nextFloat() < StoryUiConstants.STORY_BARK_DEEP_FLOOR_SILENCE_CHANCE;
    }

    /**
     * WHAT THE MAP IS (narrative-rework order-8, HOLE 2): asked for every time the FACILITY NAV
     * console opens, answered at most five times in a save — one one-shot line per region band.
     * Without it the branching descent is the one system the story never accounts for.
     *
     * <p>The console is a hard-pause overlay, so the line is queued here and delivered in the lull on
     * the floor the player then picks.  That ordering is correct rather than merely tolerable: they
     * have just used the thing she is explaining.
     */
    private void requestRouteMapBarks() {
        barkSystem.request(BarkTrigger.MAP_OPENED);
    }

    // -------------------------------------------------------------------------
    // Story MOMENT SCHEDULE (Story UI order-5) — the gameplay sites the catalogs hang off.
    // Every method here only ASKS; the bark and exchange layers own whether anything is said.
    // -------------------------------------------------------------------------

    /**
     * The COLD OPEN (order-5, rewritten by narrative-rework order-2 D): the first thing that happens
     * once control is actually the player's.  ORA introduces herself properly — who is talking, what
     * happened to this body, and why its memory came back short — and then teaches the one control
     * they need.  Every row is one-shot and persistent, so a veteran on their fortieth reprint gets
     * none of it.
     *
     * <p>The beats are asked for IN ORDER, which is what a subject key per {@link IntroBeat} buys:
     * the pool cannot hand back "you died about an hour ago" before "I'm ORA".  A beat whose
     * instance number has not come round yet ({@code isAvailableAt}) is simply not asked for — that
     * is how the second run gets the two lines that only mean anything to somebody who has now died
     * themselves.
     *
     * <p>Fires from the first PLAYING frame of a run rather than from the boot card, so the lines
     * land on the world instead of on a modal the player is still reading.
     */
    private void requestColdOpenBarks() {
        if (coldOpenRequested || isStartingRoom) return;
        coldOpenRequested = true;
        int reprintCount = barkSystem.getProgress().getReprintCount();
        for (IntroBeat beat : IntroBeat.values()) {
            if (!beat.isColdOpen() || !beat.isAvailableAt(reprintCount)) continue;
            barkSystem.request(beat.getTrigger(), beat.getSubjectKey());
        }
        barkSystem.request(BarkTrigger.CONTROL_HINT, ControlHint.MOVE.getSubjectKey());
        // The run-start half of the vocabulary ladder (order-3): the words that only mean something
        // to somebody who has now died themselves. "Reprint" waits for the run after their first
        // death and "checkpoint" for the one after that, so a new player is never handed the
        // machinery of the loop before they have felt it.
        StoryTermCatalog.requestNextIntro(barkSystem, BarkTrigger.RUN_START);
    }

    /**
     * The rest of the tutorial (order-5): each control taught by one ORA line, the first time that
     * control is genuinely useful — an empty magazine, real damage with a medkit in the bag, a second
     * gun, something worth stashing.  Asking every frame is free: each row is one-shot for the life
     * of the save, so after a player's first hour this method never produces anything again.
     *
     * <p>Deliberately NOT "teach everything at the start": six lines at once is a manual, and a
     * manual is the thing this game replaced with a voice.
     */
    private void requestControlHintBarks() {
        // reserveAmmo > 0 is doing real work: it is -1 for melee and for an empty hand (which have
        // nothing to reload) and 0 when the reserve is dry (nothing to reload WITH). Telling a player
        // holding a knife to reload would be the exact failure this channel exists to avoid.
        if (hudState.currentAmmo == 0 && hudState.reserveAmmo > 0) {
            barkSystem.request(BarkTrigger.CONTROL_HINT, ControlHint.RELOAD.getSubjectKey());
        }
        if (hudState.medicalCharges > 0) {
            barkSystem.request(BarkTrigger.CONTROL_HINT, ControlHint.INVENTORY.getSubjectKey());
            if (player.getHealthFraction() <= StoryUiConstants.STORY_BARK_LOW_HEALTH_FRACTION) {
                barkSystem.request(BarkTrigger.CONTROL_HINT, ControlHint.HEAL.getSubjectKey());
            }
        }
        if (countEquippedRangedWeapons() >= 2) {
            barkSystem.request(BarkTrigger.CONTROL_HINT, ControlHint.SWITCH_WEAPON.getSubjectKey());
        }
        requestDistributedIntroBarks();
    }

    /**
     * The distributed half of ORA's introduction (narrative-rework order-2 D): the beats that wait
     * for a thing to happen in front of the player rather than arriving on a schedule.  A door
     * opening is when "I do doors, guns are your department" is worth saying; picking something up
     * is when what carrying anything is WORTH in a permadeath run is worth saying.
     *
     * <p>Polled here beside the control hints for the same reason they are: every row is one-shot for
     * the life of the save, so asking costs nothing and after a player's first hour this produces
     * nothing at all.  The remaining two beats fire from their own moments — the first quiet stretch
     * ({@code StoryBarkTickSubscriber}) and the first terminal read ({@code updateLogTerminals}).
     */
    private void requestDistributedIntroBarks() {
        if (doorManager != null && doorManager.getOpenedDoorCount() > 0) {
            barkSystem.request(IntroBeat.DOORS.getTrigger(), IntroBeat.DOORS.getSubjectKey());
        }
        if (runStats != null && runStats.itemsCollected > 0) {
            barkSystem.request(IntroBeat.CARRIED_ITEMS.getTrigger(),
                               IntroBeat.CARRIED_ITEMS.getSubjectKey());
        }
    }

    /** How many ranged weapons the loadout is currently holding — 2+ makes switching a decision. */
    private int countEquippedRangedWeapons() {
        Loadout loadout = inventory != null ? inventory.getLoadout() : null;
        if (loadout == null) return 0;
        int weaponCount = 0;
        for (int slotIndex = 0; slotIndex < loadout.getSlotCount(); slotIndex++) {
            if (loadout.getSlot(slotIndex) != null) weaponCount++;
        }
        return weaponCount;
    }

    /**
     * The LOG channel (order-5): stepping orthogonally adjacent to a facility terminal is the player
     * reading what the crew left on it, and ORA says one line about it.  Mirrors the auto-doc heal
     * station and the EVENT console exactly, so a log costs no new button and no aiming — which is
     * the only reason a player who does not read will ever read one.
     *
     * <p>The terminal is consumed either way, and the whole floor stops offering takes after
     * {@code STORY_LOG_TAKES_PER_FLOOR}: a server room is a dozen terminals and one opinion.
     *
     * <p>A handful of logs per region also carry an EXCHANGE — held pending, never opened here, so it
     * arrives once the player is standing still (openPendingStoryExchange).
     */
    private void updateLogTerminals() {
        if (unreadLogTerminals.isEmpty() || player == null || player.isDead()) return;
        if (logTakesThisFloor >= StoryUiConstants.STORY_LOG_TAKES_PER_FLOOR) return;
        int playerColumn = MathUtils.floor(player.positionX / Constants.CELL_SIZE);
        int playerRow    = MathUtils.floor(player.positionY / Constants.CELL_SIZE);
        for (int index = 0; index < unreadLogTerminals.size(); index++) {
            int[] terminal = unreadLogTerminals.get(index);
            int manhattan = Math.abs(terminal[0] - playerColumn) + Math.abs(terminal[1] - playerRow);
            if (manhattan > 1) continue;   // not adjacent yet (the terminal tile itself is solid)
            unreadLogTerminals.remove(index);
            logTakesThisFloor++;
            barkSystem.request(BarkTrigger.LOG_FOUND);
            exchangeSystem.request(ExchangeTrigger.LOG_FOUND);
            return;
        }
    }

    /**
     * THE FIRST BOSS'S DEATH (narrative-rework order-5 D) — the REVEAL that rides the one moment
     * every player who gets that far is guaranteed to reach: the facility knew what was down here,
     * drew the room, built a door for it, and kept sending shifts past it.
     *
     * <p>Polled rather than pushed, for the same reason the ending exchange below is: the boss's own
     * defeat path belongs to {@code BossFloorController} and must not learn about the story layer.
     * {@code bossDefeatBarkRequested} keeps it to one ask per floor, and the row's one-shot flag
     * keeps it to one delivery per SAVE — a veteran's tenth boss says nothing.
     */
    private void requestBossDefeatedBark() {
        if (bossDefeatBarkRequested)                                             return;
        if (bossFloorController == null || !bossFloorController.isBossDefeated()) return;
        bossDefeatBarkRequested = true;
        barkSystem.request(BarkTrigger.BOSS_DEFEATED);
    }

    /**
     * THE ENDING (order-5, story/06-endings.md): opens the one fully consequential choice in the
     * game, once, at the bottom.
     *
     * <p>The guard is the edge case order-7 Part A names: the route map is endless, so "the Core"
     * is a story region many floors can belong to, and the ending must NOT open on just any of them.
     * It waits for the deepest region ever reached to be the Core AND for the Core's own boss to be
     * down — a forced convergence the player cannot route around, which is exactly what a final node
     * needs to be. Everything after that (idle, alive, no bark on screen) is the ordinary exchange
     * readiness check, inherited by holding the beat pending rather than opening it here.
     */
    private void requestCoreEndingExchange() {
        if (coreEndingRequested)                                                        return;
        if (barkSystem.getProgress().getDeepestRegion() != StoryRegion.CORE)             return;
        if (bossFloorController == null || !bossFloorController.isBossDefeated())        return;
        coreEndingRequested = exchangeSystem.requestById(ExchangeCatalog.CORE_ENDING_EXCHANGE_ID);
    }

    /**
     * Ends the run the way the player's last answer said to (order-5).  The narrative layer only
     * NAMES the ending; everything that happens because of it happens here.
     *
     * <p>Three of the four subvert the reprint card the player has read a hundred times, which is the
     * strongest instrument the endings have (order-3, story/06-endings.md).  MERGE is the one that
     * does not: it presents no card at all, because the interface simply stops being the player's —
     * so the world is frozen exactly where it stands and nothing else is drawn or simulated.  What
     * surrounds either of those final screens (a return to the title, credits) is order-8's.
     */
    private void commitPendingStoryEnding() {
        BootCardVariant ending = exchangeSystem.consumePendingEndingVariant();
        if (ending == null) return;
        Gdx.app.log("StoryEnding", "ending chosen: " + ending.name());
        runEnded = true;
        // Filed permanently (order-8 Part D): from here on the LAUNCH screen prints this ending's
        // status lines instead of the ordinary death notice. The framing the player has read on
        // every single launch is the instrument, and this is where it is spent.
        barkSystem.getProgress().recordEndingReached(ending.name());
        if (touchInputState != null) touchInputState.resetAllButtonStates();
        if (!presentEndingCard(ending)) {
            // MERGE presents no card, and none is wanted. The interface simply stops being the
            // player's: the frozen view they were standing in holds, fading, and then the game
            // stands down to a title screen the ending has changed (order-8 Part D).
            endingHoldSeconds = 0f;
            runPhase          = RunPhase.ENDING_HOLD;
        }
    }

    /**
     * Opens an exchange a moment held back, if the world is genuinely ready to stop (order-4).
     *
     * <p>The bars are deliberate.  The player must be IDLE (never yanked out of a step or a
     * rotation), alive, and not mid-fade — and any bark on screen is left alone, because two story
     * panels never stack and a line the player is still reading must never be covered.  Nothing is
     * lost by waiting: the exchange simply stays pending until the next frame that qualifies.
     *
     * @return true when an exchange just took the screen (the caller must skip this frame's sim)
     */
    private boolean openPendingStoryExchange() {
        if (runPhase != RunPhase.PLAYING || !exchangeSystem.hasPending()) return false;
        if (player.isDead() || !playerController.isIdle())                return false;
        if (barkSystem.hasActiveBark())                                   return false;
        if (!exchangeSystem.openPending())                                return false;

        // Release every held touch button, so a finger resting on FORWARD does not immediately step
        // the moment the exchange closes.
        if (touchInputState != null) touchInputState.resetAllButtonStates();
        exchangePressedIndex = -1;
        runPhase             = RunPhase.STORY_EXCHANGE;
        // The archive (order-6) files whatever this conversation is about, exactly as a delivered
        // bark does: the codex only ever holds the long version of something already on screen.
        codexSystem.noteLineSpoken(exchangeSystem.getActiveExchangeId());
        // Two sounds, in the order the eye reads them: the panel arriving, then who is speaking.
        storyAudio.playCue(StoryCue.PANEL_OPEN);
        storyExchangeRenderer.playPendingSpeakerSting();
        return true;
    }

    /**
     * Routes touches to the exchange on screen (order-4): press an answer plate, release on it to
     * commit.  Standard phone button behaviour — sliding off a plate before releasing CANCELS the
     * tap, which matters here more than anywhere else in the game, because an exchange answer is
     * recorded permanently and a mis-tap must always be recoverable.
     *
     * <p>There is no blank "next" and no timer: while the prompt is up the only way onward is an
     * actual answer, and the panel waits as long as the player needs.
     */
    private void updateStoryExchangeTouch() {
        if (gameViewport == null || !exchangeSystem.isActive()) {
            exchangePressedIndex = -1;
            return;
        }
        int buttonCount = exchangeSystem.isAwaitingChoice() ? exchangeSystem.getOptionCount()
                        : exchangeSystem.isShowingReply()   ? 1
                        : 0;
        if (buttonCount <= 0) {
            exchangePressedIndex = -1;
            exchangeSystem.setPressedOptionIndex(-1);
            return;
        }

        if (Gdx.input.isTouched()) {
            cardTouchPosition.set(Gdx.input.getX(), Gdx.input.getY());
            gameViewport.unproject(cardTouchPosition);
            if (Gdx.input.justTouched()) {
                exchangePressedIndex = StoryExchangeRenderer.buttonIndexAt(
                        cardTouchPosition.x, cardTouchPosition.y, buttonCount);
            } else if (exchangePressedIndex >= 0 && !StoryExchangeRenderer.isInsideButton(
                    cardTouchPosition.x, cardTouchPosition.y, exchangePressedIndex)) {
                exchangePressedIndex = -1;   // the finger slid off: the tap is cancelled
            }
            exchangeSystem.setPressedOptionIndex(exchangePressedIndex);
            return;
        }

        // Finger lifted while still on the plate it went down on — that is the answer.
        if (exchangePressedIndex < 0) return;
        int committedIndex = exchangePressedIndex;
        exchangePressedIndex = -1;
        exchangeSystem.setPressedOptionIndex(-1);
        if (exchangeSystem.isAwaitingChoice()) {
            storyTelemetry.recordExchangeAnswered(exchangeSystem.getOptionKind(committedIndex));
            // A committed answer gets its own firm cue (order-7 Part D) — the one tap in the story
            // layer that changes something, so it should feel like a switch being thrown.
            storyAudio.playCue(StoryCue.CHOICE_PICKED);
            exchangeSystem.selectOption(committedIndex);
            // A PROBE answer may have named a codex entry; re-check completion so the tab's mark and
            // ORA's completion beat land on the same tap that earned them.
            codexSystem.refresh();
        } else {
            exchangeSystem.requestContinue();
        }
        storyExchangeRenderer.playPendingSpeakerSting();
    }

    /**
     * Grants the small cache a PROBE answer just revealed (order-4's "pay them for reading" lever).
     * The narrative layer never touches the inventory itself — it names an {@code ItemType} and the
     * engine resolves it here, which is what keeps {@code …toon3d.narrative} headless.  Reading the
     * reward clears it, so it can never be granted twice.
     */
    private void applyPendingExchangeReward() {
        int    quantity     = exchangeSystem.getPendingRewardQuantity();
        String itemTypeName = exchangeSystem.consumePendingRewardItemTypeName();
        if (itemTypeName == null || quantity <= 0) return;
        ItemType rewardType;
        try {
            rewardType = ItemType.valueOf(itemTypeName);
        } catch (IllegalArgumentException unknownItem) {
            // A catalog typo must never take the run down; a test guards the shipped names.
            Gdx.app.error("StoryExchange", "unknown reward item type: " + itemTypeName);
            return;
        }
        if (itemInventory.tryAdd(rewardType, quantity)) {
            eventTextSystem.spawnWithColor("FOUND: " + rewardType.getDisplayName().toUpperCase(),
                    EventTextSystem.COLOR_GOLD);
        } else {
            // A full pack must never swallow the payoff silently: the player asked a question, was
            // promised something for it, and has to be told why they did not get it.
            eventTextSystem.spawnWithColor("NO ROOM FOR: " + rewardType.getDisplayName().toUpperCase(),
                    EventTextSystem.COLOR_WHITE);
        }
    }

    /**
     * Routes touches to the bark on screen (order-2).  A bark never disappears on a timer, so the
     * player closes it themselves, two ways:
     * <ul>
     *   <li>TAP the close X in the panel's top-right corner, or</li>
     *   <li>SWIPE — press anywhere on the panel and drag {@code STORY_BARK_SWIPE_DISMISS_DISTANCE}
     *       in any direction.</li>
     * </ul>
     * The bark sits at the top-centre of the screen, clear of every touch button, so consuming
     * input here can never eat a movement or fire tap.  A tap that lands on the panel but is not
     * the X (and never becomes a swipe) does nothing — so a mis-tap cannot silently close a line
     * the player has not read.
     */
    private void updateStoryBarkTouch() {
        if (gameViewport == null || !barkSystem.hasActiveBark() || barkSystem.isActiveBarkDismissed()) {
            barkSwipeTracking = false;
            return;
        }
        if (!Gdx.input.isTouched()) {
            barkSwipeTracking = false;   // finger lifted: a swipe that never travelled far enough
            return;
        }

        cardTouchPosition.set(Gdx.input.getX(), Gdx.input.getY());
        gameViewport.unproject(cardTouchPosition);

        if (Gdx.input.justTouched()) {
            if (StoryBarkRenderer.isInsideCloseButton(cardTouchPosition.x, cardTouchPosition.y)) {
                recordBarkDismissalForTuning();
                barkSystem.dismissActiveBark();
                barkSwipeTracking = false;
                return;
            }
            barkSwipeTracking = StoryBarkRenderer.isInsidePanel(cardTouchPosition.x, cardTouchPosition.y);
            barkSwipeStartX   = cardTouchPosition.x;
            barkSwipeStartY   = cardTouchPosition.y;
            return;
        }

        if (!barkSwipeTracking) return;
        float travelX = cardTouchPosition.x - barkSwipeStartX;
        float travelY = cardTouchPosition.y - barkSwipeStartY;
        float travelDistance = (float) Math.sqrt(travelX * travelX + travelY * travelY);
        if (travelDistance >= StoryUiConstants.STORY_BARK_SWIPE_DISMISS_DISTANCE) {
            recordBarkDismissalForTuning();
            barkSystem.dismissActiveBark();
            barkSwipeTracking = false;
        }
    }

    /**
     * Files how long the player kept the bark on screen before closing it (order-6 Part E).  A line
     * swiped away instantly was skipped, not read, and a pool that keeps getting skipped is a pool
     * to DELETE rather than one to make louder — "if in doubt, cut" is Part B's rule, and this is
     * the only measurement behind it.
     */
    private void recordBarkDismissalForTuning() {
        if (!barkSystem.hasActiveBark() || barkSystem.isActiveBarkDismissed()) return;
        storyTelemetry.recordBarkDismissed(barkSystem.getActiveElapsedSeconds());
    }

    // -------------------------------------------------------------------------
    // Story UI order-7 — the engine seams: app lifecycle, and the "new game" wipe
    // -------------------------------------------------------------------------

    /**
     * The OS took the screen away (Story UI order-7 Part E) — a call, a notification, the player
     * switching apps.  Android delivers this through {@code ApplicationListener.pause()}.
     *
     * <p>Nothing story-side has to be torn down, and that is by design: a modal owns
     * {@link #runPhase}, so an exchange, a boot card or the codex is still exactly where it was when
     * the app comes back — "re-open on resume" costs nothing because the layer never left.  What DOES
     * have to go is transient TOUCH state: a finger that was pressing an answer plate, dragging the
     * codex or mid-swipe on a bark never sends its release event, so without this the plate would
     * stay lit and the next tap anywhere would commit an answer the player never made.
     *
     * <p>The turn-based sim needs no pausing of its own — it advances only on player action, so a
     * backgrounded game is already frozen.
     */
    public void onApplicationPause() {
        exchangePressedIndex = -1;
        exchangeSystem.setPressedOptionIndex(-1);
        barkSwipeTracking = false;
        // A framing menu row the finger was holding when the OS took the screen (order-8): the
        // release never arrives, so the row must not stay lit and must not commit on the way back.
        framingPressedRow = -1;
        pauseMenu.setPressedIndex(-1);
        settingsMenu.setPressedIndex(-1);
        titleScreenSystem.getMenu().setPressedIndex(-1);
        resetCodexDragTracking();
        if (touchInputState != null) {
            touchInputState.resetAllButtonStates();
            touchInputState.consumeTapAction();
        }
    }

    /**
     * The app came back to the foreground (Story UI order-7 Part E).  On Android this can follow a
     * GL context loss, so the story renderers' cached glyph scales are re-pushed from the persisted
     * settings, and any touch left over from the moment of interruption is dropped — a tap that
     * merely restored the app must never also answer the exchange it restored into.
     */
    public void onApplicationResume() {
        applyStoryAccessibilitySettings();
        if (touchInputState != null) {
            touchInputState.resetAllButtonStates();
            touchInputState.consumeTapAction();
        }
    }

    /**
     * NEW GAME (Story UI order-7 Part B): erases the single save — run records, first-encounter tips
     * and every scrap of narrative progress — and re-reads what this world had cached from it.
     *
     * <p>All of it goes together.  Meta-progression and story progression are two halves of one
     * save: clearing the records but leaving the story at the Core (or the reverse) would leave a
     * player in a state no play can produce, with every one-shot beat already spent and no way to
     * earn it back.
     *
     * <p>This is the ONLY thing in the game that moves the story backwards.  Death does not; it is
     * the entire premise that it does not.  The seam exists for order-8's title/settings screen to
     * call behind a confirmation — nothing in a run calls it.
     */
    public void wipeAllPersistentProgress() {
        StatsStore.wipeAllPersistentProgress();   // the whole preferences file, story keys included
        barkSystem.getProgress().reloadFromStore();   // shared by the bark, boot card and exchange systems
        storySettings.reloadFromStore();
        codexSystem.refresh();
        codexOverlayRenderer.refreshLabels();
        applyStoryAccessibilitySettings();
        persistentStats.reset();   // in place: the HUD and death overlay hold this same instance
    }

    // -------------------------------------------------------------------------
    // Story UI order-6 — the codex, the pacing budget, accessibility and telemetry
    // -------------------------------------------------------------------------

    /**
     * Opens the CODEX (order-6 Part A).  It is reached from a MENU — today the inventory header,
     * tomorrow order-8's pause menu — and never from play, which is the whole point: the archive is
     * opt-in, so it must never be able to interrupt anything.
     *
     * <p>The phase it was opened from is remembered and restored on close, so the archive reads as a
     * page of the menu the player was already in rather than as a detour out of it.
     */
    public void openCodex() {
        if (runPhase == RunPhase.CODEX_OPEN) return;
        // The menus the archive can be opened FROM (order-8 added the last two): the inventory
        // header, the pause menu, and the launch screen — where it is readable before a run exists.
        codexReturnPhase = (runPhase == RunPhase.INVENTORY_OPEN || runPhase == RunPhase.PAUSE_MENU
                            || runPhase == RunPhase.TITLE)
                ? runPhase : RunPhase.PLAYING;
        if (touchInputState != null) touchInputState.resetAllButtonStates();
        codexSystem.open();
        codexOverlayRenderer.refreshLabels();
        storyTelemetry.recordCodexOpened();
        resetCodexDragTracking();
        runPhase = RunPhase.CODEX_OPEN;
    }

    /** Closes the archive and returns to whichever menu (or to play) it was opened from. */
    public void closeCodex() {
        codexSystem.close();
        resetCodexDragTracking();
        if (touchInputState != null) {
            touchInputState.resetAllButtonStates();
            touchInputState.consumeTapAction();
        }
        runPhase = codexReturnPhase;
    }

    /**
     * Routes touches to the archive: tap a tab, tap an entry, drag the body to scroll, tap BACK or
     * the close X to leave.  Standard phone behaviour — a drag that travels further than
     * {@code STORY_CODEX_TAP_SLOP} is a SCROLL and never also opens whatever was under the finger,
     * so flicking through a long list can never fling the player into an entry they did not want.
     *
     * <p>Nothing here is timed and nothing auto-advances; the codex waits exactly as long as the
     * player wants to read.
     */
    private void updateCodexTouch() {
        if (gameViewport == null) return;

        if (!Gdx.input.isTouched()) {
            if (codexDragTracking) commitCodexTap();
            resetCodexDragTracking();
            return;
        }

        cardTouchPosition.set(Gdx.input.getX(), Gdx.input.getY());
        gameViewport.unproject(cardTouchPosition);

        if (Gdx.input.justTouched()) {
            codexDragTracking      = true;
            codexTouchDownX        = cardTouchPosition.x;
            codexTouchDownY        = cardTouchPosition.y;
            codexDragLastY         = cardTouchPosition.y;
            codexDragTravel        = 0f;
            codexScrollAtTouchDown = codexSystem.getScrollOffset();
            return;
        }
        if (!codexDragTracking) return;

        // Drag inside the body scrolls it. Y is UP in world space, so dragging the finger DOWN
        // (a decreasing Y) pulls the content up and reveals what is further down the document —
        // which is why the delta is added rather than subtracted.
        float dragDeltaY = codexDragLastY - cardTouchPosition.y;
        codexDragLastY   = cardTouchPosition.y;
        codexDragTravel += Math.abs(dragDeltaY);
        if (CodexOverlayRenderer.isInsideBody(codexTouchDownX, codexTouchDownY)
                && codexDragTravel >= StoryUiConstants.STORY_CODEX_TAP_SLOP) {
            codexSystem.scrollBy(-dragDeltaY * StoryUiConstants.STORY_CODEX_SCROLL_DRAG_SCALE);
        }
    }

    /** The finger lifted: everything that is a TAP rather than a scroll resolves here. */
    private void commitCodexTap() {
        if (codexDragTravel >= StoryUiConstants.STORY_CODEX_TAP_SLOP) {
            // That was a scroll, not a tap. Leave the new offset where the drag put it.
            return;
        }
        float tapX = codexTouchDownX;
        float tapY = codexTouchDownY;

        if (CodexOverlayRenderer.isInsideCloseButton(tapX, tapY)
                || CodexOverlayRenderer.isOutsidePanel(tapX, tapY)) {
            closeCodex();
            return;
        }
        if (codexSystem.isShowingEntry() && CodexOverlayRenderer.isInsideBackButton(tapX, tapY)) {
            codexSystem.closeEntry();
            return;
        }
        int settingIndex = CodexOverlayRenderer.settingIndexAt(tapX, tapY);
        if (settingIndex >= 0) {
            codexSystem.getSettings().cycleSetting(settingIndex);
            applyStoryAccessibilitySettings();
            codexOverlayRenderer.refreshLabels();
            return;
        }
        int tabIndex = CodexOverlayRenderer.categoryTabIndexAt(tapX, tapY);
        if (tabIndex >= 0) {
            codexSystem.selectCategory(CodexCategory.values()[tabIndex]);
            return;
        }
        if (codexSystem.isShowingEntry()) return;   // the body is text, not buttons
        int rowIndex = CodexOverlayRenderer.entryRowIndexAt(tapX, tapY,
                codexSystem.getVisibleEntryCount(), codexScrollAtTouchDown);
        if (rowIndex >= 0 && codexSystem.openEntry(rowIndex)) {
            storyTelemetry.recordCodexEntryOpened();
            codexOverlayRenderer.refreshLabels();   // the NEW dot it just cleared
        }
    }

    private void resetCodexDragTracking() {
        codexDragTracking = false;
        codexDragTravel   = 0f;
    }

    /**
     * Pushes the order-6 accessibility settings into every system and renderer that draws story
     * text.  Called once at construction and again whenever the player changes a setting — never per
     * frame, because a setting change is a tap and a tap is not a frame.
     *
     * <p>The wrap width goes to the HEADLESS systems and the glyph scale to the RENDERERS, and both
     * come from the same setting: that pairing is what makes larger text wrap sooner instead of
     * running off the end of a panel ("wrap, never shrink").
     */
    private void applyStoryAccessibilitySettings() {
        int   lineMaxChars = storySettings.getPanelLineMaxChars();
        float textScale    = storySettings.getStoryTextScale();
        boolean reduceMotion = storySettings.isReduceMotion();

        // The story layer's sound is one switch for all seven tones (order-7 Part D).
        storyAudio.setEnabled(storySettings.isStoryAudioEnabled());

        barkSystem.setLineMaxChars(lineMaxChars);
        exchangeSystem.setLineMaxChars(lineMaxChars);
        bootCardSystem.setLineMaxChars(lineMaxChars);
        bootCardSystem.setReducedTextHold(storySettings.isReduceTextHold());

        storyBarkRenderer.applyAccessibilitySettings(textScale, reduceMotion);
        storyExchangeRenderer.applyAccessibilitySettings(textScale, reduceMotion);
        bootCardRenderer.applyAccessibilitySettings(textScale, reduceMotion);
    }

    /**
     * PACING BUDGET (order-6 Part B): true while enough enemies are awake for this to count as a
     * combat spike.  Non-critical barks are HELD while it is true and arrive in the lull afterwards
     * — a line delivered mid-fight is a line nobody reads, and one that covers the moment the player
     * needed to look at the room is worse than no line at all.
     */
    private boolean isCombatSpike() {
        if (enemyManager == null) return false;
        return enemyManager.countAwakeEnemies() >= StoryUiConstants.STORY_COMBAT_SPIKE_AWAKE_ENEMIES;
    }

    /**
     * ARCHIVING (order-6 Part A): a line that actually reached the screen files the full text of
     * whatever it was about into the codex, and lands in the tuning counters (Part E).
     *
     * <p>Delivery is the trigger, not the request: a queued line that went stale was never told to
     * the player, and archiving it would put a document in the codex that nothing on screen ever
     * pointed at.
     */
    private void archiveDeliveredStoryLine() {
        String deliveredBarkId = barkSystem.consumeJustDeliveredBarkId();
        if (deliveredBarkId == null) return;
        storyTelemetry.recordBarkShown();
        codexSystem.noteLineSpoken(deliveredBarkId);
    }

    /**
     * The codex's completion "perk" (order-6 Part A): ORA notices that the player filled a shelf.
     * That is the entire reward, deliberately — the archive is opt-in, and paying completion in
     * power would turn a thing nobody has to read into a thing everybody has to grind.
     *
     * <p>The beat is fired here rather than inside the narrative layer for the same reason a probe's
     * reward is granted here: the narrative layer names things, and the engine does them.
     */
    private void requestCodexCompletionBarks() {
        CodexCategory completed = codexSystem.consumePendingCompletion();
        if (completed == null) return;
        barkSystem.request(BarkTrigger.CODEX_COMPLETE, completed.getCatalogKey());
    }

    /** In-memory story tuning counters (order-6 Part E).  No identifiers, no I/O, no PII. */
    public StoryTelemetry getStoryTelemetry() {
        return storyTelemetry;
    }

    /** The persisted story accessibility settings (order-6 Part D) — order-8's settings menu reads this. */
    public StorySettings getStorySettings() {
        return storySettings;
    }

    /**
     * Fires the low-health bark on the way DOWN through the threshold only, and re-arms once the
     * player heals back above it — so a fight spent hovering at low HP produces one line, not a
     * stream of them (the bark layer's cooldown is the second guard).
     */
    private void updateLowHealthStoryBark() {
        float healthFraction = player.getHealthFraction();
        if (healthAboveLowThreshold) {
            if (healthFraction > 0f
                    && healthFraction <= StoryUiConstants.STORY_BARK_LOW_HEALTH_FRACTION) {
                healthAboveLowThreshold = false;
                barkSystem.request(BarkTrigger.LOW_HEALTH);
            }
        } else if (healthFraction > StoryUiConstants.STORY_BARK_LOW_HEALTH_FRACTION) {
            healthAboveLowThreshold = true;
        }
    }

    /**
     * Replaces the reprint card with an ENDING's subversion of it (Story UI order-3, the payload of
     * story/06-endings.md).  The card the player has read a hundred times is the strongest
     * instrument the endings have, so each one spends it differently:
     * <ul>
     *   <li>FREE / KILL — the reserve is gone, no checkpoint is authorized, and the run does NOT
     *       reload.  The card has no CONTINUE and this phase has no exit; that absence is the
     *       ending.  Ask {@link #isBootCardTerminal()} to detect it.</li>
     *   <li>OBEY — a cold promotion card, which still continues, because the reward is more of the
     *       same forever.</li>
     *   <li>MERGE — no card at all: {@link BootCardSystem#present} returns false and the run phase
     *       is left alone, because the interface simply stops being the player's.</li>
     * </ul>
     *
     * <p>WHICH ending fires, and what surrounds this screen afterwards, is order-5's schedule and
     * order-8's framing flow.  Order-3 owns only the card itself, so this is the seam they call.
     *
     * @return true when an ending card is now on screen
     */
    public boolean presentEndingCard(BootCardVariant variant) {
        if (variant == null || !bootCardSystem.present(variant)) return false;
        if (touchInputState != null) touchInputState.resetAllButtonStates();
        runPhase = RunPhase.BOOT_CARD;
        return true;
    }

    /** True while a card that ends the run is on screen — the "no reprint" state (order-8 reads it). */
    public boolean isBootCardTerminal() {
        return runPhase == RunPhase.BOOT_CARD && bootCardSystem.isTerminal();
    }

    // -------------------------------------------------------------------------
    // Story UI order-8 — THE FRAMING SCREENS: launch/title, pause, settings, the
    // death -> reprint transition, and the way off an ending.
    // -------------------------------------------------------------------------

    /**
     * THE LAUNCH SCREEN (order-8 Part A).  There is no cheerful title screen: the app opens on black
     * with the machine printing a death notice, the title resolves quietly under it, and a cold menu
     * fades in beneath that.  A tap anywhere before the menu arrives prints the whole screen at once
     * — the paced reveal is mood, and mood is never a gate.
     */
    private void updateTitleScreen(float deltaTime) {
        titleScreenSystem.update(deltaTime);
        // The launch screen sits over a world that is built but not running, and the touch buttons
        // are not drawn — so drop any tap they collected before it can leak into the run.
        if (touchInputState != null) touchInputState.consumeTapAction();
        if (gameViewport == null) return;

        FramingMenu menu = titleScreenSystem.getMenu();
        if (menu.isConfirmOpen()) {
            updateFramingConfirmTouch(menu, true);
            TitleMenuItem confirmed = titleScreenSystem.consumeConfirmedItem();
            if (confirmed != null) applyTitleMenuItem(confirmed);
            return;
        }
        if (!titleScreenSystem.isMenuVisible()) {
            if (Gdx.input.justTouched()) titleScreenSystem.skipReveal();
            return;
        }
        int releasedRow = updateFramingMenuTouch(menu, StoryUiConstants.STORY_TITLE_MENU_TOP_Y);
        if (releasedRow < 0) return;
        TitleMenuItem item = titleScreenSystem.activateRow(releasedRow);
        if (item != null) applyTitleMenuItem(item);
    }

    /** Acts on a title row the player committed to (directly, or through its confirmation). */
    private void applyTitleMenuItem(TitleMenuItem item) {
        switch (item) {
            case CONTINUE_DESCENT:
                startRunFromTitle();
                break;
            case NEW_OPERATOR:
                // The ONE thing in the game that moves the story backwards (order-7 Part B), behind
                // a confirmation, and only ever from here. Records, tips and narrative state clear
                // TOGETHER, then the descent starts over from a save that remembers nothing.
                wipeAllPersistentProgress();
                titleScreenSystem.present();
                startRunFromTitle();
                break;
            case CODEX:
                openCodex();
                break;
            case SETTINGS:
                openSettingsMenu();
                break;
            case QUIT:
            default:
                Gdx.app.exit();
                break;
        }
    }

    /**
     * FIRST-LAUNCH → COLD OPEN HANDOFF (order-8 Part A).  Picking a descent hands straight to the
     * in-run cold open: the reprint boot card first (order-3), which is where ORA's wake line and the
     * instance counter live, and then the floor.  The launch screen's death lines and that card share
     * the same ids and the same styling, so they read as one machine talking without a seam.
     */
    private void startRunFromTitle() {
        framingPressedRow     = -1;
        showingInstanceReport = false;
        if (touchInputState != null) {
            touchInputState.resetAllButtonStates();
            touchInputState.consumeTapAction();
        }
        // EVERY run opens on the reprint card, the very first one included: in-fiction the original
        // self has just died, so a first-timer's boot shows the same notice as their hundredth
        // (story/01-timeline.md).
        if (bootCardSystem.present(BootCardVariant.REPRINT)) {
            bootCardEndsWorld = false;
            runPhase          = RunPhase.BOOT_CARD;
        } else {
            runPhase = RunPhase.PLAYING;
        }
    }

    /**
     * When the whole death beat is over and the reprint card takes the screen: the fade to black,
     * plus the hold on the three words that land on it (narrative-rework order-2).
     */
    private float deathStrokeEndSeconds() {
        return ProgressionConstants.DEATH_BEAT_DURATION_SECONDS
                + StoryUiConstants.STORY_DEATH_STROKE_HOLD_SECONDS;
    }

    /**
     * 0..1 resolve of the death stroke's words.  They arrive only once the screen is fully black —
     * three words hanging over a frozen corridor would read as a HUD element rather than as the end
     * of something — and then hold at full until the card takes over.
     */
    private float deathStrokeWordsFade() {
        float sinceBlack = deathBeatTimerSeconds - ProgressionConstants.DEATH_BEAT_DURATION_SECONDS;
        if (sinceBlack <= 0f) return 0f;
        float fraction = sinceBlack / StoryUiConstants.STORY_DEATH_STROKE_FADE_SECONDS;
        return fraction >= 1f ? 1f : fraction;
    }

    /**
     * The death beat has finished (order-8 Part B): print the player again.  The card takes the
     * screen in the world that DIED, and its CONTINUE hands over to a fresh world that opens
     * already faded up — which is what makes the whole of death → reprint one calm tap-through.
     */
    private void presentReprintCard() {
        showingInstanceReport = false;
        if (touchInputState != null) touchInputState.resetAllButtonStates();
        if (bootCardSystem.present(BootCardVariant.REPRINT)) {
            bootCardEndsWorld = true;
            runPhase          = RunPhase.BOOT_CARD;
        } else {
            // Defensive: a catalog with no reprint card must never strand the player on black.
            requestReset(StartMode.RESPAWN);
        }
    }

    /**
     * Routes one tap on the reprint / ending card (order-8 Part B and D).  Everything here obeys the
     * same rule the card itself does: one tap always advances, and nothing is ever refused silently.
     */
    private void handleBootCardTap(float worldX, float worldY) {
        if (showingInstanceReport) {
            showingInstanceReport = false;   // any tap closes the page; BACK is the labelled way
            return;
        }
        if (instanceReportRenderer.hasReport()
                && InstanceReportRenderer.isInsideOpenButton(worldX, worldY)) {
            showingInstanceReport = true;
            return;
        }
        if (bootCardSystem.isTerminal()) {
            // FREE / KILL: there is no CONTINUE, because nothing reloads. Once the card has finished
            // printing, the only thing on offer is standing down to a title the ending has changed.
            if (bootCardSystem.isRevealComplete()
                    && FramingScreenRenderer.isInsideReturnButton(worldX, worldY)) {
                standDownToTitle();
            } else {
                bootCardSystem.skipReveal();
            }
            return;
        }
        if (BootCardRenderer.isInsideContinueButton(worldX, worldY)) {
            bootCardSystem.requestContinue();
        } else {
            bootCardSystem.skipReveal();
        }
    }

    /**
     * THE PAUSE MENU (order-8 Part C) — the "suit paused" panel: resume, archive, settings, abandon.
     * Opening it suppresses the bark layer and can never open an exchange (order-7 Part E precedence),
     * and it deliberately spoils nothing: no plot summary, no objective recap. The archive holds the
     * lore, one row down, for whoever wants it.
     */
    private void openPauseMenu() {
        if (runPhase != RunPhase.PLAYING) return;
        pauseMenu.clear();
        for (PauseMenuItem item : PauseMenuItem.values()) {
            pauseMenu.addRow(item.getLabelStringId(), null, true);
        }
        framingPressedRow = -1;
        if (touchInputState != null) {
            touchInputState.resetAllButtonStates();
            touchInputState.consumeTapAction();
        }
        runPhase = RunPhase.PAUSE_MENU;
    }

    /** Back to the floor, exactly where it was — the game is turn-based, so nothing moved. */
    private void closePauseMenu() {
        framingPressedRow = -1;
        pauseMenu.setPressedIndex(-1);
        if (touchInputState != null) {
            touchInputState.resetAllButtonStates();
            touchInputState.consumeTapAction();
        }
        runPhase = RunPhase.PLAYING;
    }

    private void updatePauseMenu() {
        if (touchInputState != null) touchInputState.consumeTapAction();
        if (gameViewport == null) return;

        if (pauseMenu.isConfirmOpen()) {
            updateFramingConfirmTouch(pauseMenu, false);
            if (pauseMenu.consumeConfirmedRow() >= 0) abandonRun();
            return;
        }
        int releasedRow = updateFramingMenuTouch(pauseMenu, StoryUiConstants.STORY_FRAME_MENU_TOP_Y);
        if (releasedRow < 0 || releasedRow >= PauseMenuItem.values().length) return;
        PauseMenuItem item = PauseMenuItem.values()[releasedRow];
        // ABANDON_RUN carries a confirmation, so activateRow opens it and commits nothing.
        if (!pauseMenu.activateRow(releasedRow, item.getConfirmStringId())) return;
        switch (item) {
            case RESUME:   closePauseMenu();    break;
            case CODEX:    openCodex();         break;
            case SETTINGS: openSettingsMenu();  break;
            default:                            break;
        }
    }

    /**
     * Ends this run at the player's request.  The records are filed exactly as a death files them —
     * an abandoned run still happened — and the game stands down to the title screen.
     */
    private void abandonRun() {
        runStats.recordFloor(currentDepth);
        sealRunAutopsy();
        standDownToTitle();
    }

    /**
     * THE SETTINGS SCREEN (order-8 Part A / order-6 Part D) — the same four accessibility knobs the
     * codex's footer strip carries, on a screen of their own, reachable from the title and from the
     * pause menu.  One model, two ways in, no second copy of the truth.
     */
    private void openSettingsMenu() {
        settingsReturnPhase = (runPhase == RunPhase.TITLE || runPhase == RunPhase.PAUSE_MENU)
                ? runPhase : RunPhase.PLAYING;
        settingsMenu.rebuildAsSettings(storySettings);
        framingPressedRow = -1;
        if (touchInputState != null) {
            touchInputState.resetAllButtonStates();
            touchInputState.consumeTapAction();
        }
        runPhase = RunPhase.SETTINGS_MENU;
    }

    private void updateSettingsMenu() {
        if (touchInputState != null) touchInputState.consumeTapAction();
        if (gameViewport == null) return;

        int releasedRow = updateFramingMenuTouch(settingsMenu, StoryUiConstants.STORY_FRAME_MENU_TOP_Y);
        if (releasedRow < 0 || !settingsMenu.activateRow(releasedRow, null)) return;
        if (settingsMenu.isSettingsBackRow(releasedRow)) {
            framingPressedRow = -1;
            runPhase          = settingsReturnPhase;
            return;
        }
        // A knob is a tap-to-cycle button, exactly as it is on the codex strip. Pushing the change
        // through here (rather than per frame) is what makes it take effect on the next line drawn.
        storySettings.cycleSetting(releasedRow);
        applyStoryAccessibilitySettings();
        codexOverlayRenderer.refreshLabels();
        settingsMenu.rebuildAsSettings(storySettings);
    }

    /**
     * Press-and-release routing for whichever framing menu is on screen.  Standard phone button
     * behaviour: sliding off a row before releasing CANCELS the tap, which matters most here, where
     * a row can wipe a save.
     *
     * @return the row the finger lifted on, or -1 when nothing was committed this frame
     */
    private int updateFramingMenuTouch(FramingMenu menu, float menuTopY) {
        if (Gdx.input.isTouched()) {
            cardTouchPosition.set(Gdx.input.getX(), Gdx.input.getY());
            gameViewport.unproject(cardTouchPosition);
            int rowUnderFinger = FramingScreenRenderer.menuRowIndexAt(
                    cardTouchPosition.x, cardTouchPosition.y, menu.getRowCount(), menuTopY);
            if (Gdx.input.justTouched()) {
                framingPressedRow = rowUnderFinger;
            } else if (framingPressedRow >= 0 && rowUnderFinger != framingPressedRow) {
                framingPressedRow = -1;   // the finger slid off: the tap is cancelled
            }
            menu.setPressedIndex(framingPressedRow);
            return -1;
        }
        int releasedRow   = framingPressedRow;
        framingPressedRow = -1;
        menu.setPressedIndex(-1);
        return releasedRow;
    }

    /**
     * Routing for an open confirmation.  Only PROCEED commits, and CANCEL is always on screen beside
     * it — every confirmation in the game carries a way back.
     *
     * @param titleOwned true when the title screen owns this menu, so the confirmed row is latched
     *                   as a {@link TitleMenuItem} rather than read back as a raw row index
     */
    private void updateFramingConfirmTouch(FramingMenu menu, boolean titleOwned) {
        if (!Gdx.input.justTouched()) return;
        cardTouchPosition.set(Gdx.input.getX(), Gdx.input.getY());
        gameViewport.unproject(cardTouchPosition);
        if (FramingScreenRenderer.isInsideConfirmYes(cardTouchPosition.x, cardTouchPosition.y)) {
            if (titleOwned) titleScreenSystem.acceptConfirm();
            else            menu.acceptConfirm();
        } else if (FramingScreenRenderer.isInsideConfirmNo(cardTouchPosition.x, cardTouchPosition.y)) {
            menu.cancelConfirm();
        }
    }

    /** True while the launch screen is the backdrop — including the menus that open on top of it. */
    private boolean isTitleFramed() {
        if (runPhase == RunPhase.TITLE) return true;
        if (runPhase == RunPhase.SETTINGS_MENU) return settingsReturnPhase == RunPhase.TITLE;
        if (runPhase == RunPhase.CODEX_OPEN)    return codexReturnPhase   == RunPhase.TITLE;
        return false;
    }

    /**
     * Ends this world and hands {@code Main} a fresh one.  The mode is the whole handshake: a
     * reprint drops straight into a new run (its card has already been read), while an abandoned or
     * finished story returns to a launch screen that now reflects what happened.
     */
    private void requestReset(StartMode startMode) {
        nextStartMode  = startMode;
        resetRequested = true;
    }

    /** Files the run, clears the screen, and stands the game down to the title. */
    private void standDownToTitle() {
        saveRunRecordsOnce();
        bootCardSystem.clear();
        showingInstanceReport = false;
        requestReset(StartMode.TITLE);
    }

    /**
     * Files this run's records exactly once, however the run ended — death, an ending, or the player
     * abandoning it.  Two seams can fire in the same frame (an ending card that also ends the world),
     * and a run counted twice would quietly corrupt every lifetime record in the save.
     */
    private void saveRunRecordsOnce() {
        if (runRecordsSaved) return;
        runRecordsSaved = true;
        StatsStore.updateAndSave(runStats, persistentStats);
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
     * Seals the RUN AUTOPSY (new-game-balancr order 9, part C) and logs it.
     *
     * <p>The death screen shows these six lines so a playtester can report a run without an
     * instrumented build ("died depth 7, starved of shells, two levels behind"); the same block goes
     * to the log so a tuning session has a paste-able record. This is the human half of the order-9
     * proof — the simulator checks that the numbers PLAY as modelled, and this checks that they FEEL
     * right, which no simulator can do.
     */
    private void sealRunAutopsy() {
        Enemy nearestEnemy = enemyManager != null ? nearestLivingEnemy() : null;
        String killedBy = nearestEnemy != null ? nearestEnemy.type.displayName()
                                               : (gameState.isBossFloor ? "Boss floor" : "Hazard");
        int expectedLevel = Math.round(1f + BalanceConfig.EXPECTED_LEVELS_PER_DEPTH * (currentDepth - 1));
        runStats.recordDeath(killedBy, playerProgress.getPlayerLevel(), expectedLevel,
                             scarcestAmmoName());
        Gdx.app.log("RunAutopsy", "--- RUN AUTOPSY ---");
        for (String autopsyLine : runStats.autopsyLines()) {
            Gdx.app.log("RunAutopsy", autopsyLine);
        }
        Gdx.app.log("RunAutopsy", "ROUTE " + runStats.getRouteString());
    }

    /** The closest living enemy to the marine — the best available guess at what landed the last hit. */
    private Enemy nearestLivingEnemy() {
        Enemy nearest = null;
        int bestDistance = Integer.MAX_VALUE;
        int playerTileColumn = GameMath.worldToTile(player.positionX);
        int playerTileRow    = GameMath.worldToTile(player.positionY);
        for (Enemy enemy : enemyManager.getEnemies()) {
            if (!enemy.isAlive()) continue;
            int distance = GameMath.chebyshevDistanceTiles(playerTileColumn, playerTileRow,
                                                           enemy.tileColumn, enemy.tileRow);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest      = enemy;
            }
        }
        return nearest;
    }

    /**
     * The ammo type the run is shortest on, as a share of its reserve cap — the "starved of shells"
     * line of the autopsy. Only counts ammo the player's loadout can actually fire.
     */
    private String scarcestAmmoName() {
        AmmoType scarcest = null;
        float    lowestShare = Float.MAX_VALUE;
        Loadout  loadout = inventory.getLoadout();
        for (int slotIndex = 0; slotIndex < loadout.getSlotCount(); slotIndex++) {
            Weapon weapon = loadout.getSlot(slotIndex);
            if (weapon == null || weapon.getAmmoType() == null) continue;
            AmmoType ammoType = weapon.getAmmoType();
            int   held  = itemInventory.countOf(ammoType.getItemType());
            float share = held / (float) Math.max(1, ammoType.getAmountPerBox());
            if (share < lowestShare) {
                lowestShare = share;
                scarcest    = ammoType;
            }
        }
        return scarcest == null ? "-" : scarcest.getDisplayName();
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
        java.util.Collections.shuffle(shuffledRanged, new java.util.Random(GameMath.floorSeed(runSeed, 0)));

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
        java.util.Collections.shuffle(meleePool, new java.util.Random(GameMath.floorSeed(runSeed, 0) + 1));

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
