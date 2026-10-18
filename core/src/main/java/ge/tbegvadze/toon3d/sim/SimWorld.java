package ge.tbegvadze.toon3d.sim;

import java.util.ArrayList;
import java.util.List;

import ge.tbegvadze.toon3d.door.DoorManager;
import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.enemy.EnemyManager;
import ge.tbegvadze.toon3d.entity.MatchupCatalog;
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
import ge.tbegvadze.toon3d.progression.BuildTag;
import ge.tbegvadze.toon3d.progression.Keystone;
import ge.tbegvadze.toon3d.progression.Perk;
import ge.tbegvadze.toon3d.progression.PerkDeck;
import ge.tbegvadze.toon3d.progression.PlayerBuild;
import ge.tbegvadze.toon3d.relic.RelicCatalog;
import ge.tbegvadze.toon3d.relic.RelicDefinition;
import ge.tbegvadze.toon3d.relic.RelicOffer;
import ge.tbegvadze.toon3d.relic.RelicOfferRoller;
import ge.tbegvadze.toon3d.relic.RelicRarity;
import ge.tbegvadze.toon3d.relic.RelicSource;
import ge.tbegvadze.toon3d.route.BossTable;
import ge.tbegvadze.toon3d.route.EnemyBudgetOverride;
import ge.tbegvadze.toon3d.route.GuaranteedContent;
import ge.tbegvadze.toon3d.route.LevelPlan;
import ge.tbegvadze.toon3d.route.NodeLevelProfile;
import ge.tbegvadze.toon3d.route.NodeTypeDefinition;
import ge.tbegvadze.toon3d.route.RegionPlan;
import ge.tbegvadze.toon3d.route.RouteMap;
import ge.tbegvadze.toon3d.route.RouteMapGenerator;
import ge.tbegvadze.toon3d.route.RouteNode;
import ge.tbegvadze.toon3d.route.RouteNodeType;
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
    private final PerkDeck            perkDeck;
    /** The run's build (balance-overhaul order 5) — mirrors World.playerBuild. */
    private final PlayerBuild         playerBuild = new PlayerBuild();
    /** The run's held relics and trigger runner (relics-run-changing-artifacts) — mirrors World; no feedback listener. */
    private final ge.tbegvadze.toon3d.relic.RelicHand    relicHand    = new ge.tbegvadze.toon3d.relic.RelicHand();
    private final ge.tbegvadze.toon3d.relic.RelicRuntime relicRuntime = new ge.tbegvadze.toon3d.relic.RelicRuntime();
    private final java.util.EnumSet<BuildTag> carriedTagsScratch = java.util.EnumSet.noneOf(BuildTag.class);
    /** The offer being decided (CP8): filled by the real roller, never allocated per offer. */
    private final RelicOffer          relicOffer = new RelicOffer();
    private final java.util.EnumSet<BuildTag> relicTagsScratch = java.util.EnumSet.noneOf(BuildTag.class);
    /** RUN_START fires once per run, on depth-1 arrival (mirrors World.queueRunStartRelicOffer). */
    private boolean                   runStartRelicResolved;
    /** BOSS fires once per boss floor, on the defeat (mirrors World.queueBossRelicOffer). */
    private boolean                   bossRelicResolvedThisFloor;
    private int                       appliedBuildMaxHealthBonus = 0;
    private int                       appliedBuildMaxArmorBonus  = 0;
    private final int[]               appliedBuildAttributeGrants = new int[Attribute.values().length];
    private final WeaponRoller        weaponRoller;
    private final StatusEffectController statusEffectController;
    private final GameState           gameState = new GameState();
    private final ScriptedActionSource actionSource = new ScriptedActionSource();
    private final Perk[]              offeredPerks = new Perk[BalanceConfig.PERKS_OFFERED];
    private final Keystone[]          offeredKeystones = new Keystone[BalanceConfig.KEYSTONES_OFFERED];
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
    /** The C4 SWITCH hint's weapon this turn (null = no hint) — balance-overhaul order 3. */
    private Weapon                 switchHintWeapon;
    private List<GroundItem>       groundItems = new ArrayList<>();
    private SimNavigator           navigator;
    private SimView                view;
    private FloorLedger            floorLedger;
    /** The run-long source-less DoT total when this floor began (HEAVY REPORT: hazard damage to enemies). */
    private int                    hazardDamageBaseline;
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
        MatchupCatalog.bootstrap();
        TilesetRegistries.bootstrap();
        RoomBlueprints.bootstrap();

        routeMapGenerator = new RouteMapGenerator(RouteRegistries.nodeTypes(), RouteRegistries.generators());
        routeMapGenerator.setEliteAffixPool(RouteRegistries.affixes().elitePool());

        weaponRoller   = new WeaponRoller(runSeed);
        playerStats    = new PlayerStats();
        playerProgress = new PlayerProgress();
        perkDeck       = new PerkDeck(runSeed ^ 0xCA12D00D5EED1234L);
        itemInventory  = new Inventory();
        inventory      = new PlayerInventory();
        statusEffectController = new StatusEffectController();
        statusEffectController.setBuildStats(playerBuild.stats());

        player = new Player(0f, 0f, 1f, 0f);
        player.setPlayerStats(playerStats);
        player.setBuildStats(playerBuild.stats());
        player.setRandomSeed(runSeed ^ 0xD0D6E5EEDL);
        player.applyHealing(player.getMaxHealth());
        player.setPlayerDamageListener(this::onPlayerDamaged);

        shopEffectApplier = new ShopEffectRouter(
                new ShopWeaponService(weaponRoller),
                new ShopSupplyService(itemInventory),
                new ShopAbilityService(this::grantShopAbility, playerBuild::isMaxed));

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
        // The expected level-up PERK power of that character, exactly as the ExpectedPlayer model prices it
        // (EXPECTED_PERK_POWER_PP_PER_PICK per level — balance-overhaul order 5): the offence share as a %-lift
        // on every hit, the flat-defence share as extra max HP.
        float perkBudgetLevels = (characterLevel - 1) * BalanceConfig.EXPECTED_PERK_POWER_PP_PER_PICK / 100f;
        ladderProbeOffenceLift = 1f + BalanceConfig.LADDER_EXPECTED_OFFENCE_BUDGET_FRACTION * perkBudgetLevels;
        player.adjustMaxHealth(Math.round(BalanceConfig.REFERENCE_PLAYER_EHP
                * BalanceConfig.LADDER_EXPECTED_DEFENCE_BUDGET_FRACTION * perkBudgetLevels));
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
            resolveRunStartRelic();
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
            payMysteryOutcome();
            if (currentDepth >= BalanceConfig.RUN_FINAL_DEPTH) {
                // THE FINITE RUN (balance-overhaul order 6, F4): the run-final boss floor is cleared —
                // VICTORY, mirroring World.recordFinalClearVictory. No ledger ever holds a floor 26.
                ledger.ending      = RunLedger.Ending.VICTORY;
                ledger.endingDepth = currentDepth;
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
        ledger.creditsHeldAtEnd        = playerStats.getCredits();
        for (BuildTag tag : BuildTag.values()) ledger.ranksByTagAtEnd[tag.ordinal()] = playerBuild.ranksInTag(tag);
        ledger.dominantTagAtEnd        = playerBuild.highestRankTag();
        ledger.emergencySupplyTriggers = ledger.total(floor -> floor.emergencySupplyFired ? 1 : 0);
        for (int index = 0; index < relicHand.count(); index++) ledger.relicIdsAtEnd.add(relicHand.get(index).id());
        return ledger;
    }

    // =====================================================================================
    // ROUTE
    // =====================================================================================

    /** Presents the legal next nodes to the policy and commits its pick (mirrors World.commitRouteNode). */
    private void commitNextNode() {
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
        abilityResolver.setPlayerInventory(itemInventory);
        abilityResolver.setStatusEffectController(statusEffectController);
        for (Weapon weapon : arsenal) weapon.setAbilityResolver(abilityResolver);
        syncWeaponThreatLevels();
        MeleeWeapon meleeWeapon = inventory.getMeleeWeapon();
        if (meleeWeapon != null) meleeWeapon.setAbilityResolver(abilityResolver);

        explosiveBarrelManager = new ExplosiveBarrelManager(level, enemyManager, player, currentDepth);
        enemyManager.setRelicBlastTarget(explosiveBarrelManager);
        doorManager.setDoorOpenedListener(relicRuntime);
        enemyManager.setKillXpListener(playerProgress::addXp);
        enemyManager.setKillEventListener((nameTag, xpAwarded) -> floorLedger.enemiesKilled++);
        enemyManager.setKillCreditListener((baseReward, dungeonDepth) ->
                playerStats.addCredits(Math.round(baseReward
                        * (1f + (dungeonDepth - 1) * GameBalance.CREDIT_DEPTH_SCALE))));
        enemyManager.setEmergencySupplyListener(() -> floorLedger.emergencySupplyFired = true);
        // MATCHUP REPORT (balance-overhaul order 3): every landed player hit's damage, bucketed by the damage
        // class the activation armed (EnemyManager's matchup step), plus — for a ladder probe — the hit's size.
        enemyManager.setPlayerHitListener((enemyType, enemyMaxHealth, damage) -> {
            ge.tbegvadze.toon3d.entity.DamageClass damageClass = enemyManager.getActivationDamageClass();
            if (damageClass != null) floorLedger.damageByClass[damageClass.ordinal()] += damage;
            if (settings.isLadderProbe()) {
                ledger.ladderHitsToKillByRole.get(enemyType.role().ordinal()).add(enemyMaxHealth / (float) damage);
            }
        });
        // ... and every non-NEUTRAL hit's M4 word, through the real C1 seam.
        enemyManager.setImpactEventListener(new ge.tbegvadze.toon3d.entity.ImpactEventListener() {
            @Override public void onEnemyHit(float worldX, float worldY, float heightMultiplier, int damageDealt) {}
            @Override public void onEnemyKilled(float worldX, float worldY, float heightMultiplier, int killingBlowDamage) {}
            @Override public void onEnemyMatchupHit(float worldX, float worldY, float heightMultiplier,
                                                    ge.tbegvadze.toon3d.entity.MatchupOutcome outcome, boolean firstOfKind) {
                if (outcome == ge.tbegvadze.toon3d.entity.MatchupOutcome.EFFECTIVE) floorLedger.effectiveHits++;
                else if (outcome == ge.tbegvadze.toon3d.entity.MatchupOutcome.RESISTED) floorLedger.resistedHits++;
            }
        });
        if (settings.isLadderProbe()) {
            enemyManager.setEnemyAttackListener(new ge.tbegvadze.toon3d.enemy.EnemyAttackListener() {
                @Override public void onMeleeAttack(Enemy enemy) { pendingAttackerRole = enemy.type.role(); }
                @Override public void onRangedAttack(Enemy enemy, int playerColumn, int playerRow) {
                    pendingAttackerRole = enemy.type.role();
                }
            });
        }
        // HEAVY REPORT (balance-overhaul order 4, CP7b): what became of every heavy committed at the player.
        enemyManager.setHeavyOutcomeListener(new ge.tbegvadze.toon3d.enemy.HeavyOutcomeListener() {
            @Override public void onHeavyCommittedAtPlayer(Enemy enemy, ge.tbegvadze.toon3d.enemy.HeavyAttack kind) {
                floorLedger.heavyCommittedAtPlayer++;
                floorLedger.heavyCommittedByKind[kind.ordinal()]++;
            }
            @Override public void onHeavyResolved(Enemy enemy, ge.tbegvadze.toon3d.enemy.HeavyAttack kind,
                                                  ge.tbegvadze.toon3d.enemy.HeavyOutcome outcome) {
                switch (outcome) {
                    case AVOIDED:      floorLedger.heavyAvoided++;     break;
                    case GUARDED:      floorLedger.heavyGuarded++;     break;
                    case STAGGERED:    floorLedger.heavyStaggered++;   break;
                    case KILLED_FIRST: floorLedger.heavyKilledFirst++; break;
                    case LANDED:       floorLedger.heavyLanded++;      break;
                }
                // The killing blow was a heavy: it resolved (hit the player) and the player is dead after it.
                if ((outcome == ge.tbegvadze.toon3d.enemy.HeavyOutcome.LANDED
                        || outcome == ge.tbegvadze.toon3d.enemy.HeavyOutcome.GUARDED) && !player.isAlive()) {
                    floorLedger.diedToHeavy = true;
                }
            }
            @Override public void onArmourBlockedStatus() { floorLedger.statusesBlockedByArmour++; }
        });
        enemyManager.setPlayerFlatDamageBonus(playerProgress.getFlatDamageBonus());
        enemyManager.setPlayerMeleeDamageMultiplier(playerStats.getMeleeDamageMultiplier() * ladderProbeOffenceLift);
        enemyManager.setPlayerRangedDamageMultiplier(playerRangedDamageMultiplier() * ladderProbeOffenceLift);
        enemyManager.setLoadout(inventory.getLoadout());
        enemyManager.setStatusEffectController(statusEffectController);
        // THE BUILD (balance-overhaul order 5) — mirrors World.buildLevelDependentResources.
        enemyManager.setBuildStats(playerBuild.stats());
        enemyManager.setPlayerReference(player);
        player.resetFloorBuildState();
        relicRuntime.beginFloor(level, player, inventory, explosiveBarrelManager);

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
        hazardDamageBaseline             = statusEffectController.getHazardDamageToEnemies();
        switchHintWeapon                 = null;
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
        bossRelicResolvedThisFloor = false;
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

            updateMatchupHint();
            Weapon hintBeforeAction = switchHintWeapon;
            TouchAction action = policy.chooseAction(view);
            if (action == null || action == TouchAction.NONE) action = TouchAction.SKIP_TURN;
            int ammoBefore     = countAllAmmo();
            int healthLostAtStart = floorLedger.healthLost;
            int columnBefore   = view.playerTileColumn();
            int rowBefore      = view.playerTileRow();

            stepOneAction(action);
            // S-SWITCH: a SWITCH tap taken while the C4 hint was up that landed on the hinted gun.
            if (action == TouchAction.SWITCH_WEAPON && hintBeforeAction != null
                    && inventory.getEquippedWeapon() == hintBeforeAction) {
                floorLedger.matchupSwitches++;
            }

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

            if (floorBoss != null && floorLedger.bossKillTurn < 0 && !floorBoss.isAlive()) {
                // S-BOSS (balance-overhaul order 6, F10): the turn the boss died and the health it cost.
                floorLedger.bossKillTurn           = turnsOnFloor;
                floorLedger.bossKillHealthFraction = healthFraction();
            }
            if (!player.isAlive()) {
                recordDeath(threatWasTelegraphed, inResourceCrisis);
                break;
            }
            resolvePendingLevelUps();
            resolveBossRelicOffer();
        }
        floorLedger.doorsOpened = doorManager.getOpenedDoorCount();
        floorLedger.hazardDamageToEnemies = explosiveBarrelManager.getEnemyBlastDamageDealt()
                + (statusEffectController.getHazardDamageToEnemies() - hazardDamageBaseline);
        floorLedger.enemiesLeftAlive = countLiveEnemies();
        floorLedger.exited           = descentRequested;
        floorLedger.healthFractionOnExit = healthFraction();
        recordAmmoReserveFractions();
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

    /** Draws the perk offer and applies the policy's pick until the pending queue is empty (mirrors World). */
    private void resolvePendingLevelUps() {
        int guard = 0;
        while (playerProgress.hasPendingLevelUp() && player.isAlive() && guard++ < 16) {
            // B4 / AS2: a keystone level offers keystones INSTEAD of perks (mirrors World).
            int keystoneCount = PerkDeck.isKeystoneLevel(playerProgress.getPlayerLevel() + 1)
                    ? perkDeck.drawKeystones(offeredKeystones, playerBuild) : 0;
            if (keystoneCount > 0) {
                Keystone chosenKeystone = policy.chooseKeystone(offeredKeystones, keystoneCount, view);
                if (chosenKeystone == null) chosenKeystone = offeredKeystones[0];
                for (int index = 0; index < keystoneCount; index++) ledger.keystoneOffered[offeredKeystones[index].ordinal()]++;
                ledger.keystonePicked[chosenKeystone.ordinal()]++;
                playerBuild.addKeystone(chosenKeystone);
            } else {
                int offeredCount = perkDeck.drawPerks(offeredPerks, playerBuild, carriedBuildTags());
                if (offeredCount > 0) {
                    Perk chosen = policy.choosePerk(offeredPerks, offeredCount, view);
                    if (chosen == null) chosen = offeredPerks[0];
                    for (int index = 0; index < offeredCount; index++) ledger.perkOffered[offeredPerks[index].ordinal()]++;
                    ledger.perkPicked[chosen.ordinal()]++;
                    playerBuild.addPerkRank(chosen);
                }
            }
            playerProgress.advanceLevel();
            int newLevel = playerProgress.getPlayerLevel();
            player.adjustMaxHealth(PlayerStats.vitalityGrowthDelta(BalanceConfig.PLAYER_MAX_HEALTH, newLevel));
            player.adjustMaxArmor(PlayerStats.vitalityGrowthDelta(BalanceConfig.PLAYER_MAX_ARMOR, newLevel));
            applyBuildEffects();
        }
    }

    /** Mirrors World.applyBuildEffects: attribute grants, the max HP / armour deltas, the damage multipliers. */
    private void applyBuildEffects() {
        for (Attribute attribute : Attribute.values()) {
            int granted = playerBuild.attributeGrantTotal(attribute);
            int delta = granted - appliedBuildAttributeGrants[attribute.ordinal()];
            if (delta != 0) playerStats.addPermanent(attribute, delta);
            appliedBuildAttributeGrants[attribute.ordinal()] = granted;
        }
        float vitalityScale = GameMath.playerVitalityScale(playerProgress.getPlayerLevel());
        int healthBonus = GameMath.buildMaxPoolBonus(BalanceConfig.PLAYER_MAX_HEALTH, vitalityScale,
                playerBuild.stats().maxHealthMultiplier());
        int armorBonus  = GameMath.buildMaxPoolBonus(BalanceConfig.PLAYER_MAX_ARMOR, vitalityScale,
                playerBuild.stats().maxArmorMultiplier());
        if (healthBonus != appliedBuildMaxHealthBonus) player.adjustMaxHealth(healthBonus - appliedBuildMaxHealthBonus);
        if (armorBonus  != appliedBuildMaxArmorBonus)  player.adjustMaxArmor(armorBonus - appliedBuildMaxArmorBonus);
        appliedBuildMaxHealthBonus = healthBonus;
        appliedBuildMaxArmorBonus  = armorBonus;
        inventory.getLoadout().setUnlockedExtraSlots(playerBuild.stats().extraLoadoutSlots());
        for (Weapon weapon : arsenal) weapon.onBuildChanged();
        MeleeWeapon meleeWeapon = inventory.getMeleeWeapon();
        if (meleeWeapon != null) meleeWeapon.onBuildChanged();
        if (enemyManager != null) {
            enemyManager.setPlayerMeleeDamageMultiplier(playerStats.getMeleeDamageMultiplier() * ladderProbeOffenceLift);
            enemyManager.setPlayerRangedDamageMultiplier(playerRangedDamageMultiplier() * ladderProbeOffenceLift);
        }
    }

    /** Mirrors World.carriedBuildTags: loadout guns' and a chosen (non-Fist) melee weapon's tags, plus their abilities' tags. */
    java.util.EnumSet<BuildTag> carriedBuildTags() {
        carriedTagsScratch.clear();
        Loadout loadout = inventory.getLoadout();
        for (int slotIndex = 0; slotIndex < loadout.getSlotCount(); slotIndex++) {
            Weapon weapon = loadout.getSlot(slotIndex);
            BuildTag tag = weapon != null ? BuildTag.forDamageClass(weapon.damageClass()) : null;
            if (tag != null) carriedTagsScratch.add(tag);
            if (weapon != null) weapon.addAbilityBuildTags(carriedTagsScratch);
        }
        MeleeWeapon meleeWeapon = inventory.getMeleeWeapon();
        if (meleeWeapon != null && !meleeWeapon.isLevelGapExempt()) {
            BuildTag tag = BuildTag.forDamageClass(meleeWeapon.damageClass());
            if (tag != null) carriedTagsScratch.add(tag);
            meleeWeapon.addAbilityBuildTags(carriedTagsScratch);
        }
        return carriedTagsScratch;
    }

    /** The run's held relics (CP8: SimWorld acquisition). */
    ge.tbegvadze.toon3d.relic.RelicHand relicHand() {
        return relicHand;
    }

    /** The run's build (read-only by convention) — SimView exposes it to the policies. */
    PlayerBuild playerBuild() {
        return playerBuild;
    }

    /**
     * The MYSTERY payout on leaving the sector (balance-overhaul order 6, F15) — mirrors World.payMysteryOutcomeOnce
     * / applyLadderEffect with the same seeds and currencies, so the played economy sees what device play pays.
     */
    private void payMysteryOutcome() {
        if (pendingNode == null || pendingNode.type != RouteNodeType.MYSTERY || GameMath.isBossFloor(currentDepth)) return;
        ge.tbegvadze.toon3d.route.EventEffect effect =
                ge.tbegvadze.toon3d.route.MysteryOutcome.rollFor(pendingNode.nodeSeed).reward();
        Weapon equipped = inventory.getEquippedWeapon();
        if (effect.weaponLevels() > 0 && equipped != null) {
            int ceiling = Math.min(ge.tbegvadze.toon3d.util.WeaponConstants.MAX_WEAPON_LEVEL, Math.max(1, currentDepth) + 1);
            for (int level = 0; level < effect.weaponLevels(); level++) {
                if (equipped.isLevelGapExempt() || !equipped.canLevelUp() || equipped.getWeaponLevel() >= ceiling) break;
                weaponRoller.applyLevelUp(equipped);
            }
        }
        if (effect.perkRank()) {
            java.util.Random random = new java.util.Random(pendingNode.nodeSeed ^ 0x4D59535445525921L);
            java.util.EnumSet<BuildTag> carried = carriedBuildTags();
            List<Perk> preferred = new ArrayList<>();
            List<Perk> fallback  = new ArrayList<>();
            for (Perk perk : Perk.values()) {
                if (playerBuild.isMaxed(perk)) continue;
                fallback.add(perk);
                if (carried.contains(perk.tag)) preferred.add(perk);
            }
            List<Perk> pool = preferred.isEmpty() ? fallback : preferred;
            if (!pool.isEmpty()) grantShopAbility(pool.get(random.nextInt(pool.size())));
        }
        if (effect.healFraction() > 0f) player.applyTreatment(Math.round(player.getMaxHealth() * effect.healFraction()));
        if (effect.armourFraction() > 0f) player.applyArmor(Math.round(player.getMaxArmor() * effect.armourFraction()));
        float floorIncome = ge.tbegvadze.toon3d.util.BalanceSchema.combatFloorCreditIncome(currentDepth);
        if (effect.creditFloors() > 0f) playerStats.addCredits(Math.round(effect.creditFloors() * floorIncome));
        if (effect.creditCostFloors() > 0f) {
            int toll = Math.min(playerStats.getCredits(), Math.round(effect.creditCostFloors() * floorIncome));
            if (toll > 0) playerStats.spendCredits(toll);
        }
        // The MYSTERY relic cache (mirrors World.applyLadderEffect): World queues it and opens it on the next PLAYING
        // frame; the roll is seeded by (run, depth, source) alone, so resolving it here draws the same cards.
        if (effect.relicOffer() != null) offerRelic(effect.relicOffer(), currentDepth, 0);
    }

    // =====================================================================================
    // RELICS (relics-run-changing-artifacts, CP8) — mirrors World's acquisition
    // =====================================================================================

    /**
     * RUN_START on depth-1 arrival, once per run (mirrors World.queueRunStartRelicOffer, AS13). A S-RELIC probe row
     * leaves the offer and is instead GRANTED one relic of its rarity, round-robin over that rarity's non-affine
     * pool by run index (AS17). A LADDER probe takes no relic: it measures the on-curve player, which carries none (D3).
     */
    private void resolveRunStartRelic() {
        if (runStartRelicResolved || settings.isLadderProbe()) return;
        runStartRelicResolved = true;
        offerRelic(RelicSource.RUN_START, Math.max(1, currentDepth), 0);
        RelicRarity probeRarity = policy.startingRelicProbe();
        if (probeRarity == null) return;
        List<RelicDefinition> pool = nonAffinePool(probeRarity);
        if (pool.isEmpty()) return;
        long runIndex = runSeed - BalanceSimulator.DEFAULT_SEED_OFFSET;
        RelicDefinition granted = pool.get((int) Math.floorMod(runIndex, (long) pool.size()));
        if (bondRelic(granted)) ledger.grantedProbeRelicId = granted.id();
    }

    /** The rarity's relics with no build-tag affinity, in registration order — the probe rows' round-robin pool. */
    static List<RelicDefinition> nonAffinePool(RelicRarity rarity) {
        List<RelicDefinition> pool = new ArrayList<>();
        for (RelicDefinition definition : RelicCatalog.registry().allOfRarity(rarity)) {
            if (definition.affinity() == null) pool.add(definition);
        }
        return pool;
    }

    /**
     * BOSS on the defeat, once per boss floor, never on the run-final boss (that is the VICTORY); the boss index is
     * the count of BossTable rows shallower than this floor — exactly World.queueBossRelicOffer.
     */
    private void resolveBossRelicOffer() {
        if (bossRelicResolvedThisFloor || floorBoss == null || floorBoss.isAlive() || !player.isAlive()) return;
        bossRelicResolvedThisFloor = true;
        if (currentDepth >= BalanceConfig.RUN_FINAL_DEPTH) return;
        int bossIndex = 0;
        for (BossTable.Row row : BossTable.standard().rows()) {
            if (row.depth < currentDepth) bossIndex++;
        }
        offerRelic(RelicSource.BOSS, currentDepth, bossIndex);
    }

    /**
     * Rolls an offer through the real roller with World's seed and tag rule, then lets the policy bond a card or
     * LEAVE IT. An offer that rolls no cards (full hand, empty pool) is skipped, as on device.
     */
    private void offerRelic(RelicSource source, int depth, int bossIndex) {
        RelicOfferRoller.roll(source, depth, bossIndex, relicHand, playerBuild.stats(), relicEligibilityTags(),
                RelicOfferRoller.seedFor(runSeed, depth, source), relicOffer, RelicCatalog.registry());
        if (relicOffer.count == 0) return;
        ledger.relicOffersSeen[source.ordinal()]++;
        RelicDefinition chosen = policy.declinesRelicOffers() ? null
                : policy.chooseRelic(relicOffer.cards, relicOffer.count, view);
        if (chosen != null && !isInOffer(chosen)) chosen = relicOffer.cards[0];
        if (chosen != null && bondRelic(chosen)) ledger.relicOffersTaken[source.ordinal()]++;
        relicOffer.clear();
    }

    private boolean isInOffer(RelicDefinition definition) {
        for (int index = 0; index < relicOffer.count; index++) {
            if (relicOffer.cards[index] == definition) return true;
        }
        return false;
    }

    /** Bonds a relic (mirrors World.chooseRelicOfferCard): the hand folds it into the build, the build re-derives. */
    private boolean bondRelic(RelicDefinition definition) {
        if (!relicHand.add(definition, playerBuild)) return false;
        applyBuildEffects();
        return true;
    }

    /** Mirrors World.relicEligibilityTags: carried weapons' tags plus every tag holding a perk rank (R5). */
    private java.util.EnumSet<BuildTag> relicEligibilityTags() {
        relicTagsScratch.clear();
        relicTagsScratch.addAll(carriedBuildTags());
        for (BuildTag tag : BuildTag.values()) {
            if (playerBuild.ranksInTag(tag) > 0) relicTagsScratch.add(tag);
        }
        return relicTagsScratch;
    }

    /** A perk rank bought at the Fabricator (mirrors World.grantShopAbility): no level spent. */
    private void grantShopAbility(Perk perk) {
        if (playerBuild.addPerkRank(perk)) applyBuildEffects();
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
            if (!loadout.tryEquip(weapon)) {
                // A full loadout: the policy may give up a slot for a new class (balance-overhaul order 3).
                int replaced = policy.slotToReplaceForGroundWeapon(weapon, loadout, view);
                if (replaced < 0) return;
                loadout.removeSlot(replaced);
                if (!loadout.tryEquip(weapon)) return;
            }
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
        // F13 (balance-overhaul order 6), mirroring World.seedVendingMachines / rollMachineStock: one machine,
        // two on a SHOP node (upgrade / supply biased, at the SHOP-node discount). Same per-machine seeds.
        boolean shopNode  = pendingNode != null && pendingNode.type == RouteNodeType.SHOP;
        int machineCount  = shopNode ? GameBalance.SHOP_NODE_MACHINE_COUNT : GameBalance.SHOP_MACHINES_PER_FLOOR;
        ShopContext context = buildShopContext(shopNode ? GameBalance.SHOP_NODE_PRICE_MULTIPLIER : 1f);
        long baseSeed = GameMath.floorSeed(runSeed, currentDepth);
        boolean twoMachineBias = GameBalance.SHOP_TWO_MACHINE_BIAS && machineCount > 1;
        ledger.fabricatorVisits++;
        for (int machineIndex = 0; machineIndex < machineCount; machineIndex++) {
            ShopRoller.RollBias bias = !twoMachineBias ? ShopRoller.RollBias.NONE
                    : (machineIndex == 0 ? ShopRoller.RollBias.UPGRADE : ShopRoller.RollBias.SUPPLY);
            ShopStock stock = shopRoller.roll(context, baseSeed ^ (0x9E3779B97F4A7C15L * (machineIndex + 1)), bias);
            for (int entryIndex = 0; entryIndex < stock.size(); entryIndex++) {
                ShopEntry entry = stock.get(entryIndex);
                if (entry.isSoldOut()) continue;
                if (!policy.buyShopEntry(entry, view)) continue;
                ShopTransaction.Result result = ShopTransaction.buy(entry, playerStats, shopEffectApplier);
                if (result == ShopTransaction.Result.PURCHASED) {
                    ledger.creditsSpent += entry.price;
                    ledger.shopPurchases++;
                }
            }
        }
    }

    private ShopContext buildShopContext(float nodePriceMultiplier) {
        List<WeaponProfile> ownedWeapons = new ArrayList<>();
        Loadout loadout = inventory.getLoadout();
        for (int slotIndex = 0; slotIndex < loadout.getSlotCount(); slotIndex++) {
            Weapon weapon = loadout.getSlot(slotIndex);
            if (weapon != null) ownedWeapons.add(weapon);
        }
        MeleeWeapon meleeWeapon = inventory.getMeleeWeapon();
        if (meleeWeapon != null) ownedWeapons.add(meleeWeapon);
        return new ShopContext(currentDepth, ownedWeapons, inventory.getEquippedWeapon(), playerBuild,
                nodePriceMultiplier);
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
        floorLedger.ammoHeldFractionAtDeath = carriedAmmoHeldFraction();
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

    /** True when a gun in the loadout fires this ammo type. */
    private boolean isAmmoTypeCarried(AmmoType ammoType) {
        Loadout loadout = inventory.getLoadout();
        for (int slotIndex = 0; slotIndex < loadout.getSlotCount(); slotIndex++) {
            Weapon weapon = loadout.getSlot(slotIndex);
            if (weapon != null && weapon.getAmmoType() == ammoType) return true;
        }
        return false;
    }

    /** ROUND SUMMARY G: each carried type's reserve as a fraction of its cap; NaN for a type nothing fires. */
    private void recordAmmoReserveFractions() {
        for (AmmoType ammoType : AmmoType.values()) {
            floorLedger.ammoReserveFractionOnExit[ammoType.ordinal()] =
                    isAmmoTypeCarried(ammoType) && ammoType.getReserveCap() > 0
                            ? itemInventory.countOf(ammoType.getItemType()) / (float) ammoType.getReserveCap()
                            : Float.NaN;
        }
    }

    /** ROUND SUMMARY T4: the reserve held across every carried ammo type, over their summed caps (0 when none). */
    private float carriedAmmoHeldFraction() {
        int held = 0;
        int cap  = 0;
        for (AmmoType ammoType : AmmoType.values()) {
            if (!isAmmoTypeCarried(ammoType)) continue;
            held += itemInventory.countOf(ammoType.getItemType());
            cap  += ammoType.getReserveCap();
        }
        return cap > 0 ? held / (float) cap : 0f;
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
            weapon.setBuildStats(playerBuild.stats());
            weaponRoller.configureRunStart(weapon);
            arsenal.add(weapon);
        }
        Fist fist = new Fist();
        fist.setRandomSeed(runSeed + (weaponSeedOffset * 0x9E3779B97F4A7C15L));
        fist.setPlayerAccuracyMultiplier(accuracyMultiplier);
        fist.setBuildStats(playerBuild.stats());
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

    /** The weapon the C4 SWITCH hint currently points at, or null (balance-overhaul order 3). */
    Weapon switchHintWeapon() { return switchHintWeapon; }

    /**
     * The C4 SWITCH hint, computed exactly as World.updateMatchupHint does: the first alerted enemy in the
     * facing lane within the equipped weapon's range (MatchupAdvisor.findTarget); if the equipped gun is
     * RESISTED by it and a carried gun with ammo is EFFECTIVE, that gun (MatchupAdvisor.hintWeapon). Pushed
     * into the real PlayerController, so a SWITCH tap jumps straight to it — the shipping semantics.
     */
    private void updateMatchupHint() {
        Weapon equipped = inventory.getEquippedWeapon();
        Weapon hint = null;
        if (equipped != null && enemyManager != null && playerController != null && playerController.isIdle()) {
            int stepColumn = Math.round(player.directionX);
            int stepRow    = Math.round(player.directionY);
            int column     = GameMath.worldToTile(player.positionX);
            int row        = GameMath.worldToTile(player.positionY);
            Enemy target = ge.tbegvadze.toon3d.entity.MatchupAdvisor.findTarget(enemyManager.getEnemies(), level,
                    doorManager, column, row, stepColumn, stepRow, equipped.getEffectiveRange());
            if (target != null) {
                int distance = Math.abs(target.tileColumn - column) + Math.abs(target.tileRow - row);
                hint = ge.tbegvadze.toon3d.entity.MatchupAdvisor.hintWeapon(equipped, inventory.getLoadout(),
                        target.type.trait(), distance);
            }
        }
        if (hint != null && hint != switchHintWeapon && floorLedger != null) {
            floorLedger.hintEpisodes++;   // a hint appears (or points at a different gun): a new episode
        }
        switchHintWeapon = hint;
        if (playerController != null) playerController.setSwitchHintWeapon(hint);
    }

    /**
     * The stairs-down tile of the current floor as {column, row}, or null when the floor has none YET. A boss
     * floor's stairs are walled up by {@code BossFloorController} until the boss dies and restored on the kill,
     * so a floor built without an exit is re-scanned until one appears (balance-overhaul order 7, P8: the
     * cached null used to outlive the boss, and a cleared boss floor stalled at its turn cap).
     */
    int[] exitTile() {
        if (exitTile == null && level != null) exitTile = locateExitTile(level);
        return exitTile;
    }

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
