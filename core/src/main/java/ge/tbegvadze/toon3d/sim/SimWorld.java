package ge.tbegvadze.toon3d.sim;

import java.util.ArrayList;
import java.util.List;

import ge.tbegvadze.toon3d.door.DoorManager;
import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.enemy.EnemyManager;
import ge.tbegvadze.toon3d.entity.AbilityResolver;
import ge.tbegvadze.toon3d.entity.ArcCannon;
import ge.tbegvadze.toon3d.entity.AssaultRifle;
import ge.tbegvadze.toon3d.entity.Chaingun;
import ge.tbegvadze.toon3d.entity.DoubleBarrelShotgun;
import ge.tbegvadze.toon3d.entity.Fist;
import ge.tbegvadze.toon3d.entity.GrenadeLauncher;
import ge.tbegvadze.toon3d.entity.Incinerator;
import ge.tbegvadze.toon3d.entity.Loadout;
import ge.tbegvadze.toon3d.entity.MeleeWeapon;
import ge.tbegvadze.toon3d.entity.PlasmaRifle;
import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.entity.PlayerInventory;
import ge.tbegvadze.toon3d.entity.Railgun;
import ge.tbegvadze.toon3d.entity.Shotgun;
import ge.tbegvadze.toon3d.entity.Weapon;
import ge.tbegvadze.toon3d.entity.WeaponProfile;
import ge.tbegvadze.toon3d.entity.WeaponRoll;
import ge.tbegvadze.toon3d.entity.WeaponRoller;
import ge.tbegvadze.toon3d.entity.boss.Boss;
import ge.tbegvadze.toon3d.entity.boss.BossFactory;
import ge.tbegvadze.toon3d.hazard.ExplosiveBarrelManager;
import ge.tbegvadze.toon3d.hazard.HazardManager;
import ge.tbegvadze.toon3d.hazard.SpireManager;
import ge.tbegvadze.toon3d.input.PlayerController;
import ge.tbegvadze.toon3d.input.touch.TouchAction;
import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.item.GroundItem;
import ge.tbegvadze.toon3d.item.Inventory;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.level.BossArenaGenerator;
import ge.tbegvadze.toon3d.level.BossArenaLayout;
import ge.tbegvadze.toon3d.level.ILevelGenerator;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.level.LevelGenConfig;
import ge.tbegvadze.toon3d.level.RoomBlueprints;
import ge.tbegvadze.toon3d.level.WeaponSpawnPoint;
import ge.tbegvadze.toon3d.progression.Attribute;
import ge.tbegvadze.toon3d.progression.PlayerProgress;
import ge.tbegvadze.toon3d.progression.PlayerStats;
import ge.tbegvadze.toon3d.progression.UpgradeCard;
import ge.tbegvadze.toon3d.progression.UpgradeCardDeck;
import ge.tbegvadze.toon3d.route.EnemyBudgetOverride;
import ge.tbegvadze.toon3d.route.GuaranteedContent;
import ge.tbegvadze.toon3d.route.LevelPlan;
import ge.tbegvadze.toon3d.route.NodeLevelProfile;
import ge.tbegvadze.toon3d.route.NodeTypeDefinition;
import ge.tbegvadze.toon3d.route.RegionPlan;
import ge.tbegvadze.toon3d.route.RouteMap;
import ge.tbegvadze.toon3d.route.RouteMapGenerator;
import ge.tbegvadze.toon3d.route.RouteNode;
import ge.tbegvadze.toon3d.route.RouteRegistries;
import ge.tbegvadze.toon3d.shop.ShopContext;
import ge.tbegvadze.toon3d.shop.ShopEffectApplier;
import ge.tbegvadze.toon3d.shop.ShopEffectRouter;
import ge.tbegvadze.toon3d.shop.ShopEntry;
import ge.tbegvadze.toon3d.shop.ShopRoller;
import ge.tbegvadze.toon3d.shop.ShopStock;
import ge.tbegvadze.toon3d.shop.ShopSupplyService;
import ge.tbegvadze.toon3d.shop.ShopTransaction;
import ge.tbegvadze.toon3d.shop.ShopWeaponService;
import ge.tbegvadze.toon3d.shop.ShopAbilityService;
import ge.tbegvadze.toon3d.shop.DefaultShopOfferSource;
import ge.tbegvadze.toon3d.status.StatusEffectController;
import ge.tbegvadze.toon3d.tileset.TilesetRegistries;
import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameBalance;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.ItemConstants;
import ge.tbegvadze.toon3d.world.GameState;
import ge.tbegvadze.toon3d.world.LevelTransitionListener;
import ge.tbegvadze.toon3d.world.TickEventBus;
import ge.tbegvadze.toon3d.world.TickPipeline;
import ge.tbegvadze.toon3d.world.BossFloorController;

/**
 * ONE simulated run, played turn by turn through the REAL systems (new-game-balancr order 9).
 *
 * <p>This is the proof half of the balance authority. A green {@code BalanceReport} shows the
 * numbers are self-consistent; it cannot show the game PLAYS the way the model claims. Because the
 * game is strictly turn-based — one action = one turn, no physics, no aim skill — a whole run is
 * simulable, and this class plays it: real {@code LevelGenerator} floors through the real route
 * pipeline, real {@code EncounterBudgetPlanner} rosters, real {@code EnemyManager} AI turns, the
 * real damage/drop/XP/economy pipeline, and the real {@code PlayerController} action entry points —
 * with a scripted {@link PlayerPolicy} in place of touch input.
 *
 * <p>WHAT IS AND IS NOT SIMULATED. Everything that changes a number is real. What is skipped is the
 * render layer and the overlay CHOREOGRAPHY (fades, card animations, the FACILITY NAV console's
 * two-step commit, the shop's machine walk-up): the policy's choice is applied directly through the
 * same effect path the overlay would call. The two deliberate simplifications are documented at
 * their call sites: {@link #visitFabricator} (a floor's shop is offered once at descent instead of
 * being walked to) and the staging room (skipped — a run starts with the standard loadout, which is
 * what every band is stated against).
 *
 * <p>DETERMINISM. Every stream is seeded from {@code runSeed}: level generation, the route map, the
 * weapon roller, the card deck, and — since order 9 — the enemy spawn/drop/effect rolls, the weapon
 * accuracy rolls, the hazard rolls and the dodge roll. Same seed + same policy ⇒ byte-identical
 * ledger. {@code BalanceSimTest} asserts it.
 */
public final class SimWorld implements LevelTransitionListener {

    // -------------------------------------------------------------------------
    // Run-scoped state (lives for the whole descent)
    // -------------------------------------------------------------------------

    private final long         runSeed;
    private final PlayerPolicy policy;
    private final SimSettings  settings;
    private final RunLedger    ledger;

    private final Player              player;
    private final PlayerInventory     inventory;
    private final Inventory           itemInventory;
    private final PlayerStats         playerStats;
    private final PlayerProgress      playerProgress;
    private final UpgradeCardDeck     upgradeCardDeck;
    private final WeaponRoller        weaponRoller;
    private final StatusEffectController statusEffectController;
    private final GameState           gameState = new GameState();
    private final ScriptedActionSource actionSource = new ScriptedActionSource();
    private final UpgradeCard[]       offeredCards = new UpgradeCard[GameBalance.LEVEL_UP_CARDS_OFFERED];
    private final List<Weapon>        arsenal = new ArrayList<>();
    private final ShopRoller          shopRoller = new ShopRoller(new DefaultShopOfferSource());
    private final ShopEffectApplier   shopEffectApplier;
    private Incinerator               incinerator;

    private final RouteMapGenerator routeMapGenerator;
    private RouteMap                routeMap;
    private RegionPlan              routePlan;
    private RouteNode               pendingNode;
    private BossArenaLayout         pendingBossArenaLayout;

    private int     currentDepth = BalanceConfig.SIM_STARTING_DEPTH;
    private int     turnsOnFloor;
    private boolean descentRequested;

    // -------------------------------------------------------------------------
    // Floor-scoped state (rebuilt on every descent)
    // -------------------------------------------------------------------------

    private Level                  level;
    private DoorManager            doorManager;
    private EnemyManager           enemyManager;
    private HazardManager          hazardManager;
    private ExplosiveBarrelManager explosiveBarrelManager;
    private AbilityResolver        abilityResolver;
    private BossFloorController    bossFloorController;
    private Boss                   floorBoss;
    private TickEventBus           tickEventBus;
    private PlayerController       playerController;
    private List<GroundItem>       groundItems = new ArrayList<>();
    private SimNavigator           navigator;
    private SimView                view;
    private FloorLedger            floorLedger;
    private int[]                  exitTile;
    /**
     * Supply tiles the marine has stood on and could NOT consume (full armour, a full med stash).
     * Without this a policy walks onto the same untakeable pickup for the rest of the floor: the
     * tile is still a pickup, so it is still the "nearest supply", forever.
     */
    private final java.util.Set<Long> exhaustedSupplyTiles = new java.util.HashSet<>();
    /** The tile the last refused step aimed at, and how many times in a row it has been refused. */
    private long lastRefusedTileKey  = Long.MIN_VALUE;
    private int  consecutiveRefusals;

    /** Set while a floor is being torn down so a queued weapon-inspect callback cannot fire late. */
    private boolean floorActive;

    public SimWorld(long runSeed, PlayerPolicy policy, SimSettings settings) {
        this.runSeed  = runSeed;
        this.policy   = policy;
        this.settings = settings;
        this.ledger   = new RunLedger(policy.id(), runSeed);

        // The same idempotent registry bootstraps World runs (route nodes, tileset art, rooms). The
        // render-side texture generators are deliberately NOT bootstrapped — no GL context here.
        RouteRegistries.bootstrap();
        TilesetRegistries.bootstrap();
        RoomBlueprints.bootstrap();

        routeMapGenerator = new RouteMapGenerator(RouteRegistries.nodeTypes(), RouteRegistries.generators());
        routeMapGenerator.setEliteAffixPool(RouteRegistries.affixes().elitePool());

        weaponRoller   = new WeaponRoller(runSeed);
        playerStats    = new PlayerStats();
        playerProgress = new PlayerProgress();
        upgradeCardDeck = new UpgradeCardDeck(runSeed ^ 0xCA12D00D5EED1234L);
        itemInventory  = new Inventory();
        inventory      = new PlayerInventory();
        statusEffectController = new StatusEffectController();

        player = new Player(0f, 0f, 1f, 0f);
        player.setPlayerStats(playerStats);
        player.setRandomSeed(runSeed ^ 0xD0D6E5EEDL);
        player.applyHealing(player.getMaxHealth());
        player.setPlayerDamageListener(this::onPlayerDamaged);

        shopEffectApplier = new ShopEffectRouter(
                new ShopWeaponService(weaponRoller),
                new ShopSupplyService(itemInventory),
                new ShopAbilityService(this::applyUpgradeCardEffects));

        buildStartingLoadout();
        if (settings.isLadderProbe()) {
            currentDepth = settings.ladderProbeDepth();
            applyLadderProbeKit();
        }
    }

    /**
     * The LADDER probe kit (balance-overhaul order 1): every gun at the floor's threat level with the
     * region's lowest drop tier (on-curve kit) or at level 1 COMMON (start kit); the character at the
     * on-curve level with its vitality growth for BOTH kits, so the report isolates the weapon ladder;
     * full reserves so a probe measures hits, not ammo luck.
     */
    private void applyLadderProbeKit() {
        boolean onCurve = settings.ladderProbeOnCurveKit();
        int weaponLevel = onCurve ? currentDepth : 1;
        ge.tbegvadze.toon3d.entity.WeaponTier[] tiers = ge.tbegvadze.toon3d.entity.WeaponTier.values();
        int[] regionMinimumTiers = BalanceConfig.WEAPON_DROP_TIER_MIN_BY_REGION;
        int region = Math.min(regionMinimumTiers.length - 1,
                GameMath.regionIndexAtDepth(currentDepth, BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE));
        ge.tbegvadze.toon3d.entity.WeaponTier tier = onCurve
                ? tiers[Math.min(tiers.length - 1, regionMinimumTiers[region])]
                : ge.tbegvadze.toon3d.entity.WeaponTier.COMMON;
        for (Weapon weapon : arsenal) {
            weapon.configureRoll(weaponLevel, tier, new ge.tbegvadze.toon3d.entity.AbilityInstance[0]);
        }
        int characterLevel = GameMath.expectedCharacterLevelAtDepth(currentDepth);
        for (int level = 2; level <= characterLevel; level++) {
            player.adjustMaxHealth(PlayerStats.vitalityGrowthDelta(BalanceConfig.PLAYER_MAX_HEALTH, level));
            player.adjustMaxArmor(PlayerStats.vitalityGrowthDelta(BalanceConfig.PLAYER_MAX_ARMOR, level));
        }
        // The expected level-up CARDS of that character, exactly as the ExpectedPlayer model prices them: the
        // offence share as a %-lift on every hit, the flat-defence share as extra max HP.
        float cardBudgetLevels = (characterLevel - 1) * BalanceConfig.LEVEL_UP_BUDGET_PP / 100f;
        ladderProbeOffenceLift = 1f + BalanceConfig.LADDER_EXPECTED_OFFENCE_BUDGET_FRACTION * cardBudgetLevels;
        player.adjustMaxHealth(Math.round(BalanceConfig.REFERENCE_PLAYER_EHP
                * BalanceConfig.LADDER_EXPECTED_DEFENCE_BUDGET_FRACTION * cardBudgetLevels));
        player.applyHealing(player.getMaxHealth());
        player.applyArmor(player.getMaxArmor());
        for (AmmoType ammoType : AmmoType.values()) {
            itemInventory.tryAdd(ammoType.getItemType(), LADDER_PROBE_AMMO_FILL);
        }
    }

    /** The probe's expected offence-card lift on every player hit (1.0 outside a LADDER probe). */
    private float ladderProbeOffenceLift = 1f;

    /** Rounds of every ammo type a LADDER probe starts with (the inventory's own caps apply). */
    private static final int LADDER_PROBE_AMMO_FILL = 999;

    /** The role of the enemy whose attack is resolving right now, so the next damage event is attributed to it. */
    private ge.tbegvadze.toon3d.enemy.EnemyRole pendingAttackerRole;

    // =====================================================================================
    // RUN
    // =====================================================================================

    /**
     * Plays the whole run and returns its ledger. The run ends when the marine dies, when the
     * simulator's depth ceiling is reached, or when a floor stalls past its turn cap.
     */
    public RunLedger play() {
        routePlan = RegionPlan.defaultPlan();
        routeMap  = routeMapGenerator.generate(runSeed, routePlan);
        // The staging room is skipped: it is a weapon-choice vestibule, and every band is stated
        // against the standard starting loadout (BalanceConfig SECTION 7). The run's FIRST real choice
        // is therefore the depth-1 route node, exactly as on device. A LADDER probe starts mid-descent,
        // so it plays the depth-driven floor (no route node — World's own fallback).
        if (settings.isLadderProbe()) {
            pendingNode = null;
        } else {
            commitNextNode();
        }

        while (currentDepth <= settings.depthCeiling()) {
            buildFloor();
            playFloor();
            if (!player.isAlive()) {
                ledger.ending      = RunLedger.Ending.KILLED;
                ledger.endingDepth = currentDepth;
                break;
            }
            if (!descentRequested) {
                // The floor could not be finished inside its turn cap.
                ledger.ending      = RunLedger.Ending.STALLED;
                ledger.endingDepth = currentDepth;
                // A TRUE softlock is "cannot damage anything": no ranged ammo AND no melee weapon,
                // with enemies still standing. The Fist is wired at run start and never removed, so
                // this is structurally impossible — which is exactly what the S-SOFTLOCK band exists
                // to keep true. Running the floor's turn cap out is a STALL, not a softlock.
                ledger.softlocked  = floorLedger.enemiesLeftAlive > 0
                                     && inventory.getLoadout().hasNoUsableAmmo()
                                     && inventory.getMeleeWeapon() == null;
                break;
            }
            visitFabricator();
            descentRequested = false;
            currentDepth++;
            if (currentDepth > settings.depthCeiling()) {
                ledger.ending      = RunLedger.Ending.SURVIVED_TO_CEILING;
                ledger.endingDepth = settings.depthCeiling();
                break;
            }
            commitNextNode();
        }

        ledger.finalPlayerLevel        = playerProgress.getPlayerLevel();
        ledger.emergencySupplyTriggers = ledger.total(floor -> floor.emergencySupplyFired ? 1 : 0);
        return ledger;
    }

    // =====================================================================================
    // ROUTE
    // =====================================================================================

    /** Presents the legal next nodes to the policy and commits its pick (mirrors World.commitRouteNode). */
    private void commitNextNode() {
        if (routeMapGenerator.shouldExtend(routeMap, currentDepth)) {
            routePlan = routeMapGenerator.extendWithNextRegion(routeMap, routePlan);
        }
        List<RouteNode> candidates = routeMap.getSelectableNext();
        if (candidates.isEmpty()) {
            pendingNode = null;   // defensive: fall back to the depth-driven generator, like World
            return;
        }
        RouteNode chosen = policy.chooseNode(candidates, view);
        if (chosen == null || !candidates.contains(chosen)) chosen = candidates.get(0);
        routeMap.commitTo(chosen);
        pendingNode = chosen;
        NodeTypeDefinition definition = RouteRegistries.nodeTypes().get(chosen.type);
        ledger.routeTokens.add(definition.id());
    }

    // =====================================================================================
    // FLOOR BUILD — mirrors World.buildNextFloor + World.buildLevelDependentResources, minus renderers
    // =====================================================================================

    private void buildFloor() {
        long seed = GameMath.floorSeed(runSeed, currentDepth);
        pendingBossArenaLayout = null;
        level = generateLevel(seed);

        doorManager  = new DoorManager(level);
        enemyManager = new EnemyManager(level, doorManager, currentDepth, seed);
        abilityResolver = new AbilityResolver(enemyManager, null, player, runSeed);
        abilityResolver.setKillXpListener(playerProgress::addXp);
        abilityResolver.setPlayerInventory(itemInventory);
        abilityResolver.setStatusEffectController(statusEffectController);
        for (Weapon weapon : arsenal) weapon.setAbilityResolver(abilityResolver);
        syncWeaponThreatLevels();
        MeleeWeapon meleeWeapon = inventory.getMeleeWeapon();
        if (meleeWeapon != null) meleeWeapon.setAbilityResolver(abilityResolver);

        explosiveBarrelManager = new ExplosiveBarrelManager(level, enemyManager, player);
        enemyManager.setKillXpListener(playerProgress::addXp);
        enemyManager.setKillEventListener((nameTag, xpAwarded) -> floorLedger.enemiesKilled++);
        enemyManager.setKillCreditListener((baseReward, dungeonDepth) ->
                playerStats.addCredits(Math.round(baseReward
                        * (1f + (dungeonDepth - 1) * GameBalance.CREDIT_DEPTH_SCALE))));
        enemyManager.setEmergencySupplyListener(() -> floorLedger.emergencySupplyFired = true);
        if (settings.isLadderProbe()) {
            enemyManager.setPlayerHitListener((enemyType, enemyMaxHealth, damage) ->
                    ledger.ladderHitsToKillByRole.get(enemyType.role().ordinal()).add(enemyMaxHealth / (float) damage));
            enemyManager.setEnemyAttackListener(new ge.tbegvadze.toon3d.enemy.EnemyAttackListener() {
                @Override public void onMeleeAttack(Enemy enemy) { pendingAttackerRole = enemy.type.role(); }
                @Override public void onRangedAttack(Enemy enemy, int playerColumn, int playerRow) {
                    pendingAttackerRole = enemy.type.role();
                }
            });
        }
        enemyManager.setPlayerFlatDamageBonus(playerProgress.getFlatDamageBonus());
        enemyManager.setPlayerMeleeDamageMultiplier(playerStats.getMeleeDamageMultiplier() * ladderProbeOffenceLift);
        enemyManager.setPlayerRangedDamageMultiplier(playerRangedDamageMultiplier() * ladderProbeOffenceLift);
        enemyManager.setLoadout(inventory.getLoadout());
        enemyManager.setStatusEffectController(statusEffectController);

        hazardManager = new HazardManager(level, enemyManager, statusEffectController,
                                          seed ^ 0x4A2A5D9BL);
        hazardManager.setExplosiveBarrelManager(explosiveBarrelManager);
        explosiveBarrelManager.setDetonationListener(hazardManager::igniteFireFromExplosion);
        if (incinerator != null) incinerator.setHazardIgniteTarget(hazardManager::igniteFire);
        // The Colossus's MOLTEN TRAIL must be live in the SIMULATOR too, or the behavioural bands would
        // measure a floor whose terrain danger simply does not exist (SimWorld plays through the REAL
        // systems — that is the whole point of the order-9 gate).
        enemyManager.setHazardIgniteTarget(hazardManager);
        // The Verdant Spiresower's crystal SPIRES must be live in the SIMULATOR too, or the behavioural
        // bands would measure a sower that never sows and never regenerates — modelling it as a plain
        // 100-HP soldier and mispricing its floors. SpireManager is headless (no GPU / listener needed).
        SpireManager spireManager = new SpireManager(level, doorManager);
        enemyManager.setSpireHitTarget(spireManager);
        enemyManager.setEnemyDeathHazardListener((deadType, tileColumn, tileRow, selfDestructMassive) -> {
            if (deadType == ge.tbegvadze.toon3d.enemy.EnemyType.PLAGUE_HULK) {
                hazardManager.spawnToxicCloud(tileColumn, tileRow, selfDestructMassive
                        ? BalanceConfig.PLAGUE_HULK_SELF_DESTRUCT_TOXIC_RADIUS_TILES
                        : BalanceConfig.HAZARD_PLAGUE_HULK_DEATH_CLOUD_RADIUS);
            }
            if (deadType.leavesTrailFireTurns() > 0) {
                hazardManager.igniteFire(tileColumn, tileRow, deadType.leavesTrailFireTurns());
            }
        });

        bossFloorController = null;
        floorBoss           = null;
        if (GameMath.isBossFloor(currentDepth) && pendingBossArenaLayout != null) {
            Boss boss = BossFactory.createForDepth(currentDepth, seed, pendingBossArenaLayout);
            if (boss != null) {
                enemyManager.addBoss(boss);
                floorBoss           = boss;
                // Null HUD / event-text sinks: the fight is real, only its presentation is absent.
                bossFloorController = new BossFloorController(boss, level, pendingBossArenaLayout,
                        doorManager, enemyManager, hazardManager, null, null);
            }
        }
        gameState.isBossFloor = bossFloorController != null;

        // Same assembly the played game uses, so the turn ORDER is identical (see TickPipeline).
        tickEventBus = TickPipeline.standardFloor(inventory, hazardManager, spireManager,
                                                  statusEffectController,
                                                  player, enemyManager, playerStats,
                                                  bossFloorController, gameState, null);

        playerController = new PlayerController(player, level, doorManager, inventory);
        playerController.setEnemyManager(enemyManager);
        playerController.setBarrelHitTarget(explosiveBarrelManager);
        playerController.setTickEventBus(tickEventBus);
        playerController.setTransitionListener(this);
        playerController.setItemInventory(itemInventory);
        playerController.setLoadout(inventory.getLoadout());
        playerController.setPlayerStats(playerStats);
        playerController.setHealUsedListener(() -> floorLedger.healsUsed++);
        playerController.setAmmoPickedUpListener(units -> floorLedger.ammoGained += units);
        playerController.setInspectWeaponCallback(this::resolveGroundWeapon);
        playerController.setActionSource(actionSource);

        buildGroundItems();
        playerController.setGroundItems(groundItems);

        player.positionX = findPlayerStartCoordinate(level, true);
        player.positionY = findPlayerStartCoordinate(level, false);
        player.directionX = 1f;
        player.directionY = 0f;
        statusEffectController.clearPlayerEffects(player);

        exhaustedSupplyTiles.clear();
        lastRefusedTileKey  = Long.MIN_VALUE;
        consecutiveRefusals = 0;
        exitTile  = locateExitTile(level);
        navigator = new SimNavigator(level, doorManager, this::canPassDoor);
        view      = new SimView(level, doorManager, enemyManager, player, itemInventory, this);

        floorLedger                      = new FloorLedger();
        floorLedger.depth                = currentDepth;
        floorLedger.bossFloor            = bossFloorController != null;
        floorLedger.playerLevelOnArrival = playerProgress.getPlayerLevel();
        floorLedger.demandDamage         = measureFloorDemandDamage();
        floorLedger.healthFractionOnEntry = healthFraction();
        recordPlannedContent(level.getFloorContentReport());
        ledger.floors.add(floorLedger);
        ledger.depthReached = Math.max(ledger.depthReached, currentDepth);
        turnsOnFloor        = 0;
        floorActive         = true;
    }

    /** Runs the node -&gt; floor pipeline (mirrors World.buildFloorForNode); falls back to depth-driven. */
    private Level generateLevel(long seed) {
        if (pendingNode == null) {
            ILevelGenerator generator = GameMath.isBossFloor(currentDepth)
                    ? new BossArenaGenerator(seed)
                    : RouteRegistries.generators().create(
                            RouteRegistries.generators().standardPool().get(0), seed, new LevelGenConfig());
            Level built = generator.generate(currentDepth);
            captureBossArenaLayout(generator);
            return built;
        }
        NodeTypeDefinition definition = RouteRegistries.nodeTypes().get(pendingNode.type);
        NodeLevelProfile   profile    = RouteRegistries.levelProfiles().getOrDefault(definition.levelProfileId());
        LevelPlan          plan       = profile.resolve(pendingNode, currentDepth, seed);
        LevelGenConfig     config     = applyFloorSupplyInputs(applyEnemyBudget(plan.config(), plan.enemyBudget()));
        ILevelGenerator    generator  = RouteRegistries.generators().create(plan.generatorId(), seed, config);
        Level built = generator.generate(currentDepth);
        captureBossArenaLayout(generator);
        for (GuaranteedContent guarantee : plan.guarantees()) {
            guarantee.applyTo(built);
        }
        return built;
    }

    private void captureBossArenaLayout(ILevelGenerator generator) {
        if (generator instanceof BossArenaGenerator) {
            pendingBossArenaLayout = ((BossArenaGenerator) generator).getLayout();
        }
    }

    /**
     * Folds the order-2 supply inputs into the config (mirrors World.applyFloorSupplyInputs): the ammo
     * types of the weapons the simulated player carries at floor build.
     */
    private LevelGenConfig applyFloorSupplyInputs(LevelGenConfig config) {
        LevelGenConfig effective = config != null ? config : new LevelGenConfig();
        java.util.EnumSet<ge.tbegvadze.toon3d.item.AmmoType> carried =
                java.util.EnumSet.noneOf(ge.tbegvadze.toon3d.item.AmmoType.class);
        ge.tbegvadze.toon3d.entity.Loadout loadout = inventory.getLoadout();
        if (loadout != null) {
            for (int slotIndex = 0; slotIndex < loadout.getSlotCount(); slotIndex++) {
                Weapon weapon = loadout.getSlot(slotIndex);
                if (weapon != null && weapon.getAmmoType() != null) carried.add(weapon.getAmmoType());
            }
        }
        effective.carriedAmmoTypes = carried;
        // S9: the two-floor weapon cadence (mirrors World / RunStats.weaponCadenceDue).
        effective.weaponCadenceDue = !lastNonBossFloorOfferedOnLevelWeapon;
        return effective;
    }

    /** Folds a node's encounter-budget override into the plan's config (mirrors World.applyEnemyBudget). */
    private static LevelGenConfig applyEnemyBudget(LevelGenConfig config, EnemyBudgetOverride override) {
        if (override == null) return config;
        LevelGenConfig effective = config != null ? config : new LevelGenConfig();
        effective.enemyBudgetScale = override.budgetScale();
        return effective;
    }

    /** The two-floor weapon cadence (balance-overhaul order 2, S9; mirrors RunStats). */
    private boolean lastNonBossFloorOfferedOnLevelWeapon = true;

    /** Rolls each weapon spawn point into a GroundItem with a real WeaponRoll (mirrors World). */
    private void buildGroundItems() {
        groundItems = new ArrayList<>();
        boolean offeredOnLevelWeapon = false;
        for (WeaponSpawnPoint spawnPoint : level.getWeaponSpawnPoints()) {
            GroundItem groundItem = new GroundItem(spawnPoint.tileColumn, spawnPoint.tileRow,
                                                   spawnPoint.weaponItemType, 1);
            Weapon baseWeapon = playerController.findWeaponInArsenalForType(spawnPoint.weaponItemType);
            if (baseWeapon != null) {
                // A PLANNED drop (order 2, S9) rolls at its planned level offset / tier floor (mirrors World).
                groundItem.weaponRoll = spawnPoint.planned
                        ? weaponRoller.rollPlannedToSnapshot(baseWeapon, currentDepth,
                                spawnPoint.levelOffset, spawnPoint.tierBonus)
                        : weaponRoller.rollToSnapshot(baseWeapon, currentDepth);
                if (groundItem.weaponRoll.weaponLevel >= currentDepth) offeredOnLevelWeapon = true;
            }
            groundItems.add(groundItem);
        }
        // The weapon cadence (mirrors RunStats.recordFloorWeaponOffer): a boss floor neither pays nor breaks it.
        if (!GameMath.isBossFloor(currentDepth)) lastNonBossFloorOfferedOnLevelWeapon = offeredOnLevelWeapon;
    }

    // =====================================================================================
    // FLOOR PLAY — the turn loop
    // =====================================================================================

    private void playFloor() {
        int turnCap = settings.turnsPerFloorCap();
        while (turnsOnFloor < turnCap && player.isAlive() && !descentRequested) {
            boolean threatWasTelegraphed = view.telegraphedThreatVisible();
            boolean inResourceCrisis     = isInResourceCrisis();

            TouchAction action = policy.chooseAction(view);
            if (action == null || action == TouchAction.NONE) action = TouchAction.SKIP_TURN;
            int ammoBefore     = countAllAmmo();
            int healthLostAtStart = floorLedger.healthLost;
            int columnBefore   = view.playerTileColumn();
            int rowBefore      = view.playerTileRow();

            stepOneAction(action);

            // A movement the game REFUSED (a keycard door with no key, an unmodelled blocker) leaves
            // the marine where it stood. Teach the navigator that tile so the policy re-routes instead
            // of burning the floor's whole turn budget walking into it — the same thing a player does
            // after the second bump.
            noteRefusedStepIfBlocked(action, columnBefore, rowBefore);
            noteUntakeableSupplyUnderfoot();

            floorLedger.ammoSpent += Math.max(0, ammoBefore - countAllAmmo());
            turnsOnFloor++;
            floorLedger.turnsSpent = turnsOnFloor;
            if (floorLedger.turnsToFirstDamageExchange < 0
                    && (ammoBefore > countAllAmmo() || floorLedger.healthLost > healthLostAtStart)) {
                floorLedger.turnsToFirstDamageExchange = turnsOnFloor;
            }

            if (!player.isAlive()) {
                recordDeath(threatWasTelegraphed, inResourceCrisis);
                break;
            }
            resolvePendingLevelUps();
        }
        floorLedger.enemiesLeftAlive = countLiveEnemies();
        floorLedger.exited           = descentRequested;
        floorLedger.healthFractionOnExit = healthFraction();
        if (floorBoss != null) floorLedger.bossKilled = !floorBoss.isAlive();
        if (floorLedger.bossFloor && floorLedger.bossKilled
                && currentDepth == Constants.BOSS_FLOOR_INTERVAL) {
            ledger.clearedFirstBoss = true;
        }
        floorActive = false;
    }

    /**
     * The lock-and-key answer for the navigator: a plain door always opens; a locked one opens only
     * once unlocked or while the run carries its keycard (the same check {@code PlayerController}
     * makes when the step is actually taken).
     */
    private boolean canPassDoor(int tileColumn, int tileRow) {
        char cell = level.getCell(tileColumn, tileRow);
        if (!Level.isLockedDoor(cell)) return true;
        if (doorManager.isUnlocked(tileColumn, tileRow)) return true;
        ge.tbegvadze.toon3d.level.KeycardColor required =
                doorManager.getRequiredKeycard(tileColumn, tileRow);
        return required != null && inventory.hasKeycard(required);
    }

    /** Records a pickup the marine is standing on but could not take, so it stops being a goal. */
    private void noteUntakeableSupplyUnderfoot() {
        int tileColumn = view.playerTileColumn();
        int tileRow    = view.playerTileRow();
        char cell      = level.getCell(tileColumn, tileRow);
        if (Level.isAmmoPickup(cell) || Level.isMedicalPickup(cell) || Level.isArmourPickup(cell)) {
            exhaustedSupplyTiles.add(tileKey(tileColumn, tileRow));
        }
    }

    /** True when this pickup tile has already been walked over without being consumable. */
    boolean isSupplyTileExhausted(int tileColumn, int tileRow) {
        return exhaustedSupplyTiles.contains(tileKey(tileColumn, tileRow));
    }

    private static long tileKey(int tileColumn, int tileRow) {
        return ((long) tileColumn << 32) ^ (tileRow & 0xFFFFFFFFL);
    }

    /** Marks the tile a rejected movement aimed at, so the navigator stops planning through it. */
    private void noteRefusedStepIfBlocked(TouchAction action, int columnBefore, int rowBefore) {
        int stepColumn;
        int stepRow;
        switch (action) {
            case FORWARD:      stepColumn =  view.facingStepColumn(); stepRow =  view.facingStepRow(); break;
            case BACK:         stepColumn = -view.facingStepColumn(); stepRow = -view.facingStepRow(); break;
            case STRAFE_LEFT:  stepColumn = -view.facingStepRow();    stepRow =  view.facingStepColumn(); break;
            case STRAFE_RIGHT: stepColumn =  view.facingStepRow();    stepRow = -view.facingStepColumn(); break;
            default:           return;   // not a movement: nothing was refused
        }
        if (view.playerTileColumn() != columnBefore || view.playerTileRow() != rowBefore) return;
        int targetColumn = columnBefore + stepColumn;
        int targetRow    = rowBefore    + stepRow;
        // Doors are never "refused": a plain one opens next turn, and a locked one is handled by the
        // navigator's keycard rule, which re-opens the route the moment the key is picked up.
        if (Level.isDoor(level.getCell(targetColumn, targetRow))) return;
        // An enemy standing in the way is a temporary blocker, not a wall.
        if (enemyManager.isTileOccupiedByEnemy(targetColumn, targetRow)) return;
        // A single failure proves nothing — a stun, a door mid-swing, or an enemy that has just moved
        // all cost a turn without moving the marine. Only a tile that refuses REPEATEDLY is written
        // off, so the navigator never walls the marine into a room it could actually leave.
        long targetKey = tileKey(targetColumn, targetRow);
        if (targetKey != lastRefusedTileKey) {
            lastRefusedTileKey   = targetKey;
            consecutiveRefusals  = 1;
            return;
        }
        if (++consecutiveRefusals >= BalanceConfig.SIM_REFUSALS_BEFORE_TILE_WRITTEN_OFF) {
            navigator.markRefused(targetColumn, targetRow);
        }
    }

    /**
     * Presents ONE action to the real controller and drains it to completion. The controller's own
     * state machine decides whether the action is legal, whether it consumes a turn, and when the
     * tick fires — the simulator never bypasses it. A generous fixed time step means every animation
     * (slide, rotate, fire, door) completes inside the loop; the cap is a safety net, never a rule.
     */
    private void stepOneAction(TouchAction action) {
        syncWeaponThreatLevels();
        // A missed enemy attack must not credit a later hazard/DoT tick to its role (LADDER REPORT noise).
        pendingAttackerRole = null;
        actionSource.present(action);
        for (int step = 0; step < BalanceConfig.SIM_MAX_STEPS_PER_ACTION; step++) {
            doorManager.update(BalanceConfig.SIM_TIME_STEP_SECONDS);
            Weapon equippedWeapon = inventory.getEquippedWeapon();
            if (equippedWeapon != null) equippedWeapon.update(BalanceConfig.SIM_TIME_STEP_SECONDS);
            playerController.update(BalanceConfig.SIM_TIME_STEP_SECONDS);
            if (step > 0 && playerController.isIdle()) break;
        }
        actionSource.clear();
        syncWeaponThreatLevels();
    }

    /** Mirrors World.syncWeaponThreatLevels: every owned weapon is compared against this floor's depth. */
    private void syncWeaponThreatLevels() {
        inventory.syncFloorThreatLevel(Math.max(1, currentDepth));
    }

    /** Draws and applies level-up cards until the pending queue is empty (mirrors World). */
    private void resolvePendingLevelUps() {
        int guard = 0;
        while (playerProgress.hasPendingLevelUp() && player.isAlive() && guard++ < 16) {
            int offeredCount = upgradeCardDeck.draw(offeredCards, GameBalance.LEVEL_UP_CARDS_OFFERED);
            UpgradeCard chosen = policy.chooseCard(offeredCards, offeredCount, view);
            if (chosen == null && offeredCount > 0) chosen = offeredCards[0];
            if (chosen == null) break;
            applyUpgradeCardEffects(chosen);
            playerProgress.advanceLevel();
            int newLevel = playerProgress.getPlayerLevel();
            player.adjustMaxHealth(PlayerStats.vitalityGrowthDelta(BalanceConfig.PLAYER_MAX_HEALTH, newLevel));
            player.adjustMaxArmor(PlayerStats.vitalityGrowthDelta(BalanceConfig.PLAYER_MAX_ARMOR, newLevel));
            upgradeCardDeck.registerPick(chosen);
        }
    }

    /** The boon effect path shared by level-up picks and shop-bought abilities (mirrors World). */
    private void applyUpgradeCardEffects(UpgradeCard card) {
        if (card.strengthDelta != 0)     playerStats.addPermanent(Attribute.STRENGTH,     card.strengthDelta);
        if (card.agilityDelta != 0)      playerStats.addPermanent(Attribute.AGILITY,      card.agilityDelta);
        if (card.toughnessDelta != 0)    playerStats.addPermanent(Attribute.TOUGHNESS,    card.toughnessDelta);
        if (card.marksmanshipDelta != 0) playerStats.addPermanent(Attribute.MARKSMANSHIP, card.marksmanshipDelta);
        int maxHealthDelta = card.maxHealthDelta + card.toughnessDelta * GameBalance.TGH_HP_PER_POINT;
        if (maxHealthDelta != 0) player.adjustMaxHealth(maxHealthDelta);
        if (card.maxArmorDelta != 0) player.adjustMaxArmor(card.maxArmorDelta);
        if (card.flatDamageDelta != 0) {
            playerProgress.addFlatDamageBonus(card.flatDamageDelta);
            enemyManager.setPlayerFlatDamageBonus(playerProgress.getFlatDamageBonus());
        }
        enemyManager.setPlayerMeleeDamageMultiplier(playerStats.getMeleeDamageMultiplier() * ladderProbeOffenceLift);
        enemyManager.setPlayerRangedDamageMultiplier(playerRangedDamageMultiplier() * ladderProbeOffenceLift);
    }

    /**
     * The ranged damage multiplier the damage pipeline actually applies, including the SABOTAGE knob
     * ({@link SimSettings}). It is 1.0x for every real run; the acceptance check raises it to prove
     * the gate's input responds to weapon power.
     */
    private float playerRangedDamageMultiplier() {
        return playerStats.getRangedDamageMultiplier() * settings.startingWeaponDamageMultiplier();
    }

    /**
     * The weapon-pickup decision. On device the controller opens the inspect card and the player
     * taps TAKE; here the policy answers the same question and the take is applied through the same
     * roll/loadout calls World's {@code resolveWeaponTake} makes.
     */
    private void resolveGroundWeapon() {
        if (!floorActive) return;
        GroundItem standingOn = playerController.getStandingOnWeapon();
        if (standingOn == null || !groundItems.contains(standingOn)) return;
        Weapon weapon = playerController.findWeaponInArsenalForType(standingOn.stack.getType());
        if (weapon == null) return;   // melee offers are not part of the simulated arsenal
        Loadout    loadout      = inventory.getLoadout();
        int        existingSlot = loadout.slotIndexOf(weapon);
        WeaponRoll heldRoll     = existingSlot >= 0 ? WeaponRoll.fromWeapon(weapon) : null;
        if (!policy.acceptGroundWeapon(standingOn.weaponRoll, heldRoll, view)) return;

        WeaponRoll groundRoll = standingOn.weaponRoll;
        if (groundRoll != null && groundRoll.tier != null) {
            weapon.configureRoll(groundRoll.weaponLevel, groundRoll.tier,
                                 groundRoll.abilities != null ? groundRoll.abilities
                                                              : new ge.tbegvadze.toon3d.entity.AbilityInstance[0]);
        }
        if (existingSlot < 0) {
            loadout.tryEquip(weapon);
            inventory.selectRangedActive();
            addStarterAmmoForWeapon(weapon);
        }
        groundItems.remove(standingOn);
        playerController.clearStandingOnWeapon();
    }

    /**
     * The floor's UAC Fabricator, offered once at descent.
     *
     * <p>SIMPLIFICATION (documented deliberately): on device the machine is a tile the player walks
     * up to, and a floor has one or two of them. The stock ROLL, the prices, the credit balance and
     * the effect application are all the real ones ({@link ShopRoller} / {@link ShopTransaction}), so
     * the economy the S-ECONOMY band measures is real; only the walk-up is skipped. A policy that
     * never buys (NAIVE) is unaffected.
     */
    private void visitFabricator() {
        if (GameMath.isBossFloor(currentDepth)) return;
        ShopContext context = buildShopContext();
        ShopStock   stock   = shopRoller.roll(context, GameMath.floorSeed(runSeed, currentDepth)
                                                       ^ 0x9E3779B97F4A7C15L, ShopRoller.RollBias.NONE);
        for (int entryIndex = 0; entryIndex < stock.size(); entryIndex++) {
            ShopEntry entry = stock.get(entryIndex);
            if (entry.isSoldOut()) continue;
            if (!policy.buyShopEntry(entry, view)) continue;
            ShopTransaction.Result result = ShopTransaction.buy(entry, playerStats, shopEffectApplier);
            if (result == ShopTransaction.Result.PURCHASED) {
                ledger.creditsSpent += entry.price;
            }
        }
    }

    private ShopContext buildShopContext() {
        List<WeaponProfile> ownedWeapons = new ArrayList<>();
        Loadout loadout = inventory.getLoadout();
        for (int slotIndex = 0; slotIndex < loadout.getSlotCount(); slotIndex++) {
            Weapon weapon = loadout.getSlot(slotIndex);
            if (weapon != null) ownedWeapons.add(weapon);
        }
        MeleeWeapon meleeWeapon = inventory.getMeleeWeapon();
        if (meleeWeapon != null) ownedWeapons.add(meleeWeapon);
        return new ShopContext(currentDepth, ownedWeapons, inventory.getEquippedWeapon());
    }

    // =====================================================================================
    // TELEMETRY
    // =====================================================================================

    private void onPlayerDamaged(int netDamage) {
        if (floorLedger != null) floorLedger.healthLost += netDamage;
        if (pendingAttackerRole != null && netDamage > 0) {
            float fullPool = player.getMaxHealth() + player.getMaxArmor();
            ledger.ladderHitsToDieByRole.get(pendingAttackerRole.ordinal()).add(fullPool / netDamage);
        }
        pendingAttackerRole = null;
    }

    /** Player health over max health — the S-SUPPLY measure (armour excluded: it is not HP). */
    private float healthFraction() {
        return player.getMaxHealth() <= 0 ? 0f : player.getHealth() / (float) player.getMaxHealth();
    }

    /** Copies what the shared FloorPopulator planned for this floor into the FLOOR REPORT fields. */
    private void recordPlannedContent(ge.tbegvadze.toon3d.level.FloorContentReport report) {
        if (report == null) return;
        floorLedger.generatorName  = report.generatorName;
        floorLedger.nodeType       = report.spec.type().name();
        floorLedger.enemiesSpawned = report.enemyCount;
        floorLedger.groups         = report.groupCount();
        floorLedger.healValuePlaced = report.placement.placedValue(ge.tbegvadze.toon3d.level.SupplyCategory.HEAL)
                + report.placement.placedValue(ge.tbegvadze.toon3d.level.SupplyCategory.ARMOUR);
        floorLedger.healFloorFraction = report.plan.healFloorValue;
        float reachable = 0f;
        for (ge.tbegvadze.toon3d.level.SupplyPlacement.GroundPlacement ground : report.placement.ground()) {
            if (ground.pickup.category == ge.tbegvadze.toon3d.level.SupplyCategory.HEAL && ground.reachableWithoutKeycard) {
                reachable += ground.pickup.value;
            }
        }
        floorLedger.reachableHealValue = reachable;
        floorLedger.ammoPlannedDamage  = report.plan.plannedValue(ge.tbegvadze.toon3d.level.SupplyCategory.AMMO);
        floorLedger.plannedScarcityRatio = GameMath.scarcityRatio(floorLedger.ammoPlannedDamage,
                report.plan.rosterEffectiveHitPoints);
        int units = 0;
        for (ge.tbegvadze.toon3d.level.PlannedPickup pickup : report.plan.pickups()) {
            if (pickup.category != ge.tbegvadze.toon3d.level.SupplyCategory.AMMO) continue;
            ge.tbegvadze.toon3d.item.AmmoType ammoType = ge.tbegvadze.toon3d.item.AmmoType.fromPickupChar(pickup.symbol);
            if (ammoType != null) units += ammoType.getAmountPerBox();
        }
        floorLedger.ammoPlannedUnits = units;
    }

    private void recordDeath(boolean threatWasTelegraphed, boolean inResourceCrisis) {
        floorLedger.diedWithNoHealsHeld = view.medkitCount() == 0;
        ledger.ending                      = RunLedger.Ending.KILLED;
        ledger.endingDepth                 = currentDepth;
        ledger.deathWasTelegraphed         = threatWasTelegraphed;
        ledger.deathFollowedResourceCrisis = inResourceCrisis;
        ledger.killedBy                    = describeLikelyKiller();
    }

    /** The nearest living enemy at the moment of death — the "what killed me" line of the autopsy. */
    private String describeLikelyKiller() {
        Enemy nearest = view.nearestEnemy();
        if (nearest != null) return nearest.type.displayName();
        return floorLedger.bossFloor ? "Boss floor hazard" : "Hazard";
    }

    /**
     * True when the marine enters a turn already in trouble: vitality, heals or ammo below the
     * crisis fraction. Half of the S-FAIR readability test — a death here is one the player walked
     * into with the warning signs on screen, not an ambush.
     */
    private boolean isInResourceCrisis() {
        if (view.vitalityFraction() < BalanceConfig.SIM_RESOURCE_CRISIS_FRACTION) return true;
        if (view.medkitCount() == 0 && view.vitalityFraction() < 0.5f) return true;
        return inventory.getLoadout().hasNoUsableAmmo();
    }

    /** Sum of every spawned enemy's effective hit points — the DEMAND term of the scarcity ratio S. */
    private float measureFloorDemandDamage() {
        float demand = 0f;
        for (Enemy enemy : enemyManager.getEnemies()) {
            demand += enemy.maxHealth;
        }
        return demand;
    }

    private int countAllAmmo() {
        int total = 0;
        for (AmmoType ammoType : AmmoType.values()) {
            total += itemInventory.countOf(ammoType.getItemType());
        }
        Loadout loadout = inventory.getLoadout();
        for (int slotIndex = 0; slotIndex < loadout.getSlotCount(); slotIndex++) {
            Weapon weapon = loadout.getSlot(slotIndex);
            if (weapon != null && weapon.getAmmoType() != null) total += weapon.getShotsInClip();
        }
        return total;
    }

    private int countLiveEnemies() {
        int count = 0;
        for (Enemy enemy : enemyManager.getEnemies()) if (enemy.isAlive()) count++;
        return count;
    }

    // =====================================================================================
    // SETUP HELPERS
    // =====================================================================================

    /** The standard non-staging-room loadout: Chaingun + Shotgun with their starting reserves. */
    private void buildStartingLoadout() {
        Shotgun             shotgun         = new Shotgun();
        DoubleBarrelShotgun doubleBarrel    = new DoubleBarrelShotgun();
        PlasmaRifle         plasmaRifle     = new PlasmaRifle();
        Chaingun            chaingun        = new Chaingun();
        AssaultRifle        assaultRifle    = new AssaultRifle();
        Railgun             railgun         = new Railgun();
        Incinerator         incineratorGun  = new Incinerator();
        ArcCannon           arcCannon       = new ArcCannon();
        GrenadeLauncher     grenadeLauncher = new GrenadeLauncher();
        this.incinerator = incineratorGun;

        float rangedMultiplier   = playerStats.getRangedDamageMultiplier();
        float accuracyMultiplier = playerStats.getAccuracyMultiplier();
        int   weaponSeedOffset   = 0;
        for (Weapon weapon : new Weapon[]{chaingun, shotgun, doubleBarrel, plasmaRifle, assaultRifle,
                                          railgun, incineratorGun, arcCannon, grenadeLauncher}) {
            weapon.setRandomSeed(runSeed + (weaponSeedOffset++ * 0x9E3779B97F4A7C15L));
            weapon.setAmmoInventory(itemInventory);
            weapon.setRangedDamageMultiplier(rangedMultiplier);
            weapon.setPlayerAccuracyMultiplier(accuracyMultiplier);
            weaponRoller.configureRunStart(weapon);
            arsenal.add(weapon);
        }
        Fist fist = new Fist();
        fist.setRandomSeed(runSeed + (weaponSeedOffset * 0x9E3779B97F4A7C15L));
        fist.setPlayerAccuracyMultiplier(accuracyMultiplier);
        weaponRoller.configureRunStart(fist);
        inventory.setMeleeWeapon(fist);

        inventory.setArsenal(List.of(chaingun, shotgun, doubleBarrel, plasmaRifle, assaultRifle,
                                     railgun, incineratorGun, arcCannon, grenadeLauncher));
        itemInventory.tryAdd(ItemType.AMMO_BULLETS, ItemConstants.AMMO_START_BULLETS);
        itemInventory.tryAdd(ItemType.AMMO_SHELLS,  ItemConstants.AMMO_START_SHELLS);
    }

    /** The starter reserve bundled with a newly taken weapon (mirrors World.addStarterAmmoForWeapon). */
    private void addStarterAmmoForWeapon(Weapon weapon) {
        AmmoType ammoType = weapon.getAmmoType();
        if (ammoType == null) return;
        int amount;
        switch (ammoType) {
            case SHELLS:  amount = ItemConstants.START_ROOM_AMMO_SHELLS;  break;
            case CELLS:   amount = ItemConstants.START_ROOM_AMMO_CELLS;   break;
            case BULLETS: amount = ItemConstants.START_ROOM_AMMO_BULLETS; break;
            case SLUGS:   amount = ItemConstants.START_ROOM_AMMO_SLUGS;   break;
            case ROCKETS: amount = ItemConstants.START_ROOM_AMMO_ROCKETS; break;
            default:      amount = 0;                                     break;
        }
        if (amount > 0) itemInventory.tryAdd(ammoType.getItemType(), amount);
    }

    /** Finds the 'p' start tile's centre coordinate (mirrors World.findPlayerStartX/Y). */
    private static float findPlayerStartCoordinate(Level level, boolean wantColumn) {
        for (int tileRow = 0; tileRow < level.getHeight(); tileRow++) {
            for (int tileColumn = 0; tileColumn < level.getWidth(); tileColumn++) {
                if (level.getCell(tileColumn, tileRow) == 'p') {
                    int tileIndex = wantColumn ? tileColumn : tileRow;
                    return tileIndex * Constants.CELL_SIZE + Constants.CELL_SIZE / 2f;
                }
            }
        }
        return (wantColumn ? Constants.WORLD_WIDTH : Constants.WORLD_HEIGHT) / 2f;
    }

    // =====================================================================================
    // LevelTransitionListener + view accessors
    // =====================================================================================

    @Override
    public void onDescentRequested() {
        descentRequested = true;
    }

    int    getCurrentDepth()  { return currentDepth; }
    int    getTurnsOnFloor()  { return turnsOnFloor; }
    boolean isBossFloor()     { return bossFloorController != null; }
    Weapon getEquippedWeapon() { return inventory.getEquippedWeapon(); }

    /** The stairs-down tile of the current floor as {column, row}, or null when the floor has none. */
    int[] exitTile() { return exitTile; }

    private static int[] locateExitTile(Level level) {
        for (int tileRow = 0; tileRow < level.getHeight(); tileRow++) {
            for (int tileColumn = 0; tileColumn < level.getWidth(); tileColumn++) {
                if (Level.isStairsDown(level.getCell(tileColumn, tileRow))) {
                    return new int[]{tileColumn, tileRow};
                }
            }
        }
        return null;
    }

    SimNavigator navigator() { return navigator; }

    List<GroundItem> groundItems() { return groundItems; }

    Loadout loadout() { return inventory.getLoadout(); }
}
