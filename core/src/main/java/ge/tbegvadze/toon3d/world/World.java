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
import ge.tbegvadze.toon3d.hud.HudRenderer;
import ge.tbegvadze.toon3d.input.PlayerController;
import ge.tbegvadze.toon3d.input.touch.TouchAction;
import ge.tbegvadze.toon3d.input.touch.TouchControllerRenderer;
import ge.tbegvadze.toon3d.input.touch.TouchInputState;
import ge.tbegvadze.toon3d.item.GroundItem;
import ge.tbegvadze.toon3d.item.Inventory;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.level.CavernGenerator;
import ge.tbegvadze.toon3d.level.ILevelGenerator;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.level.LevelGenerator;
import ge.tbegvadze.toon3d.level.LinearCorridorGenerator;
import ge.tbegvadze.toon3d.level.LevelLoader;
import ge.tbegvadze.toon3d.level.StartGameLevelGenerator;
import ge.tbegvadze.toon3d.level.WeaponSpawnPoint;
import ge.tbegvadze.toon3d.progression.LevelUpOverlayRenderer;
import ge.tbegvadze.toon3d.progression.LevelUpReward;
import ge.tbegvadze.toon3d.progression.PlayerProgress;
import ge.tbegvadze.toon3d.progression.PlayerStats;
import ge.tbegvadze.toon3d.render.*;
import ge.tbegvadze.toon3d.status.StatusEffectController;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameBalance;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.StatsStore;
import ge.tbegvadze.toon3d.util.ItemConstants;
import ge.tbegvadze.toon3d.util.RenderConstants;
import ge.tbegvadze.toon3d.util.ProgressionConstants;

public class World implements Renderable, Disposable, LevelTransitionListener {

    private enum RunPhase { PLAYING, FADING_OUT, FADING_IN, LEVEL_UP_OVERLAY, DEAD, INVENTORY_OPEN }

    // -------------------------------------------------------------------------
    // Run-persistent resources — kept alive across all floor transitions
    // -------------------------------------------------------------------------
    private final long                   runSeed;
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
    private final PlayerProgress         playerProgress;
    private final LevelUpOverlayRenderer levelUpOverlayRenderer;

    // Touch controller — null on desktop (platform-gated to touch screens)
    private TouchInputState         touchInputState;
    private TouchControllerRenderer touchControllerRenderer;
    private Viewport                gameViewport;
    private final Vector2           cardTouchPosition = new Vector2();

    // -------------------------------------------------------------------------
    // Level-dependent resources — rebuilt on every floor descent
    // -------------------------------------------------------------------------
    private Level                  level;
    private FloorCeilingRenderer   floorCeilingRenderer;
    private DoorManager            doorManager;
    private WallRenderer           wallRenderer;
    private PropRenderer           propRenderer;
    private EnemyRenderer          enemyRenderer;
    private LevelRenderer          levelRenderer;
    private EnemyManager           enemyManager;
    private EnemyAttackEffectSystem enemyAttackEffectSystem;
    private ExplosiveBarrelManager explosiveBarrelManager;
    private TickEventBus           tickEventBus;
    private PlayerController       playerController;

    // -------------------------------------------------------------------------
    // Transition state
    // -------------------------------------------------------------------------
    private RunPhase runPhase         = RunPhase.PLAYING;
    private float    fadeTimerSeconds = 0f;
    private int      currentDepth     = RenderConstants.STARTING_DEPTH;

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
    // Ground items — weapon pickups placed by LevelGenerator; rebuilt per floor
    // -------------------------------------------------------------------------
    private java.util.List<GroundItem> groundItems;

    // -------------------------------------------------------------------------
    // Start room — weapon-selection staging state; null after the room is left
    // -------------------------------------------------------------------------
    private boolean                    isStartingRoom          = false;
    private boolean                    startRoomChoiceResolved = false;
    private java.util.List<Weapon>     startRoomWeapons        = null;
    private java.util.List<GroundItem> startRoomGroundItems    = null;

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
        this(new StartGameLevelGenerator(), runSeed);
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
        this.isStartingRoom = startRoom;

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

        // Event text and hit vignette — run-persistent feedback systems
        eventTextSystem     = new EventTextSystem();
        eventTextRenderer   = new EventTextRenderer(eventTextSystem);
        hitVignetteRenderer = new HitVignetteRenderer();

        // Player stat system — seeded from MARINE difficulty for now; difficulty selection
        // will be wired when the run-setup screen (order_18) is implemented.
        playerStats = new PlayerStats(PlayerStats.Difficulty.MARINE);
        player.setPlayerStats(playerStats);
        // TOUGHNESS increases maxHealth without auto-healing; heal to full once at run
        // start so the player always begins with a full HP bar.
        player.applyHealing(player.getMaxHealth());

        itemInventory            = new Inventory();
        inventoryOverlayRenderer = new InventoryOverlayRenderer(itemInventory);

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

        // Build all weapon instances and wire them to the shared systems.
        Shotgun             shotgun         = new Shotgun();
        DoubleBarrelShotgun dblShotgun      = new DoubleBarrelShotgun();
        PlasmaRifle         plasmaRifle     = new PlasmaRifle();
        Chaingun            chaingun        = new Chaingun();
        Railgun             railgun         = new Railgun();
        Incinerator         incinerator     = new Incinerator();
        GrenadeLauncher     grenadeLauncher = new GrenadeLauncher();
        float rangedMultiplier = playerStats.getRangedDamageMultiplier();
        for (Weapon weapon : new Weapon[]{shotgun, dblShotgun, plasmaRifle, chaingun, railgun, incinerator, grenadeLauncher}) {
            weapon.setEventTextSystem(eventTextSystem);
            weapon.setAmmoInventory(itemInventory);
            weapon.setRangedDamageMultiplier(rangedMultiplier);
        }

        if (startRoom) {
            // Start room: full arsenal registered for the HUD renderer, but loadout is empty
            // so the player is unarmed until they walk onto a weapon offer.
            inventory.setArsenal(java.util.List.of(shotgun, dblShotgun, plasmaRifle, chaingun,
                                                   railgun, incinerator, grenadeLauncher));
            inventory.clearLoadout();
            // No starting ammo — starter reserve comes bundled with the chosen weapon.
        } else {
            // Normal floor entry: equip Chaingun + Shotgun with starting ammo reserves.
            inventory.setArsenal(java.util.List.of(chaingun, shotgun, dblShotgun, plasmaRifle,
                                                   railgun, incinerator, grenadeLauncher));
            itemInventory.tryAdd(ItemType.AMMO_BULLETS, ItemConstants.AMMO_START_BULLETS);
            itemInventory.tryAdd(ItemType.AMMO_SHELLS,  ItemConstants.AMMO_START_SHELLS);
        }

        weaponHudRenderer    = new WeaponHudRenderer(inventory.getArsenal());
        if (startRoom) {
            // Player starts unarmed; clear the initially-selected weapon in the renderer.
            weaponHudRenderer.setEquippedWeapon(null);
        }
        hudRenderer          = new HudRenderer(player, hudState);
        hudRenderer.setLoadout(inventory.getLoadout());
        impactEffectRenderer = new ImpactEffectRenderer(impactEffectSystem);
        fadeOverlayRenderer  = new FadeOverlayRenderer();

        // Status effect system — run-persistent; controller keeps player state across floors
        statusEffectController       = new StatusEffectController();
        statusEffectController.setEventTextSystem(eventTextSystem);
        statusEffectVignetteRenderer = new StatusEffectVignetteRenderer();

        // Build level-dependent resources for the first floor
        level = initialLevel;
        buildLevelDependentResources(initialLevel);

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
        if (runPhase == RunPhase.PLAYING) {
            fadeTimerSeconds = 0f;
            runPhase = RunPhase.FADING_OUT;
        }
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
        enemyRenderer.dispose();
        enemyAttackEffectSystem.dispose();
        levelRenderer.dispose();

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
        doorManager            = new DoorManager(targetLevel);
        floorCeilingRenderer   = new FloorCeilingRenderer(targetLevel);
        wallRenderer           = new WallRenderer(targetLevel, doorManager);
        propRenderer           = new PropRenderer(targetLevel, wallRenderer);
        levelRenderer          = new LevelRenderer(targetLevel, doorManager);
        enemyManager           = new EnemyManager(targetLevel, doorManager, currentDepth);
        explosiveBarrelManager = new ExplosiveBarrelManager(targetLevel, enemyManager, player);
        enemyRenderer          = new EnemyRenderer(enemyManager, wallRenderer);
        enemyManager.setImpactEventListener(impactEffectSystem);
        enemyManager.setKillXpListener(xpAwarded -> playerProgress.addXp(xpAwarded));
        enemyManager.setKillEventListener((nameTag, xpAwarded) -> {
            eventTextSystem.spawnWithColor(nameTag + " +" + xpAwarded + "XP", EventTextSystem.COLOR_GREEN);
            runStats.recordKill();
        });
        enemyManager.setPlayerFlatDamageBonus(playerProgress.getFlatDamageBonus());
        enemyManager.setDropPlacedListener((tileColumn, tileRow, dropChar) ->
                propRenderer.addDynamicProp(tileColumn, tileRow, dropChar));
        explosiveBarrelManager.setImpactEventListener(impactEffectSystem);
        enemyRenderer.setPropRenderer(propRenderer);

        enemyAttackEffectSystem = new EnemyAttackEffectSystem(wallRenderer);
        enemyManager.setEnemyAttackListener(enemyAttackEffectSystem);

        enemyManager.setStatusEffectController(statusEffectController);

        tickEventBus = new TickEventBus();
        tickEventBus.subscribe(new WeaponReloadSubscriber(inventory));
        tickEventBus.subscribe(new StatusEffectSubscriber(statusEffectController, player, enemyManager));
        tickEventBus.subscribe(new EnemyTurnSubscriber(enemyManager, gameState));

        // Build ground items from weapon spawn points placed by the level generator.
        groundItems = new java.util.ArrayList<>();
        for (WeaponSpawnPoint spawnPoint : targetLevel.getWeaponSpawnPoints()) {
            groundItems.add(new GroundItem(spawnPoint.tileColumn, spawnPoint.tileRow,
                                           spawnPoint.weaponItemType, 1));
        }
        propRenderer.setGroundItems(groundItems);
        levelRenderer.setGroundItems(groundItems);

        playerController = new PlayerController(player, targetLevel, doorManager, inventory);
        playerController.setEnemyManager(enemyManager);
        playerController.setBarrelHitTarget(explosiveBarrelManager);
        playerController.setTickEventBus(tickEventBus);
        playerController.setTransitionListener(this);
        playerController.setEventTextSystem(eventTextSystem);
        playerController.setItemInventory(itemInventory);
        playerController.setLoadout(inventory.getLoadout());
        playerController.setPlayerStats(playerStats);
        playerController.setGroundItems(groundItems);
        playerController.setWeaponSwitchCallback(
            () -> weaponHudRenderer.setEquippedWeapon(inventory.getEquippedWeapon()));
        playerController.setInventoryToggleCallback(this::openInventory);
        if (touchInputState != null) {
            playerController.setTouchInputState(touchInputState);
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
                if (isStartingRoom) {
                    // Leaving the staging room: generate the real first dungeon floor.
                    // currentDepth stays at STARTING_DEPTH so floor 1 is the first dungeon floor.
                    isStartingRoom       = false;
                    startRoomWeapons     = null;
                    startRoomGroundItems = null;
                    runStats.recordFloor(currentDepth);
                    rebuildForLevel(pickGenerator(floorSeed(runSeed, currentDepth)).generate());
                } else {
                    currentDepth++;
                    runStats.recordFloor(currentDepth);
                    rebuildForLevel(pickGenerator(floorSeed(runSeed, currentDepth)).generate());
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

        // LEVEL_UP_OVERLAY — game paused while player picks a stat upgrade
        if (runPhase == RunPhase.LEVEL_UP_OVERLAY) {
            if (gameViewport != null && Gdx.input.justTouched()) {
                cardTouchPosition.set(Gdx.input.getX(), Gdx.input.getY());
                gameViewport.unproject(cardTouchPosition);
                LevelUpReward tapped = levelUpOverlayRenderer.getTappedReward(cardTouchPosition.x, cardTouchPosition.y);
                if (tapped != null) {
                    applyLevelUpReward(tapped);
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
        hitVignetteRenderer.update(deltaTime);

        // Transition to level-up overlay as soon as XP threshold is crossed
        if (playerProgress.hasPendingLevelUp() && !player.isDead()) {
            if (touchInputState != null) {
                touchInputState.resetAllButtonStates();
            }
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

        alertTimeSeconds  = 0f;
        float currentAlertPulse = 0f;
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

        // Hit vignette: red edge glow drawn under the minimap and HUD panels.
        hitVignetteRenderer.render(camera);
        // Status effect vignettes: per-effect colored edge tints (burn orange, poison green, etc.)
        statusEffectVignetteRenderer.render(camera);

        levelRenderer.setPlayerWorldPosition(player.positionX, player.positionY);
        levelRenderer.render(camera);

        player.render(camera);

        // Populate HudState each frame so renderers read current values without polling Player directly
        hudState.alertActive    = false;
        hudState.playerLevel    = playerProgress.getPlayerLevel();
        hudState.xpFraction     = playerProgress.getXpFraction();
        hudState.xpForNextLevel = playerProgress.getXpForNextLevel();
        Weapon hudWeapon = inventory.getEquippedWeapon();
        if (hudWeapon != null) {
            hudState.currentAmmo = hudWeapon.getShotsInClip();
            hudState.clipSize    = hudWeapon.getClipSize();
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
        } else if (touchControllerRenderer != null && runPhase != RunPhase.INVENTORY_OPEN) {
            touchControllerRenderer.setActionLocked(!playerController.isIdle());
            touchControllerRenderer.render(camera);
        }

        // Event text: rising screen-space text drawn above HUD but below the fade overlay.
        eventTextRenderer.render(camera);

        // Inventory overlay — drawn above HUD and event text, below fade/death overlays.
        if (runPhase == RunPhase.INVENTORY_OPEN) {
            inventoryOverlayRenderer.setTime(facilityTimeSeconds);
            inventoryOverlayRenderer.setCurrentDepth(currentDepth);
            inventoryOverlayRenderer.render(camera);
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
        enemyRenderer.dispose();
        enemyAttackEffectSystem.dispose();
        levelRenderer.dispose();
        weaponHudRenderer.dispose();
        hudRenderer.dispose();
        impactEffectRenderer.dispose();
        fadeOverlayRenderer.dispose();
        eventTextRenderer.dispose();
        hitVignetteRenderer.dispose();
        levelUpOverlayRenderer.dispose();
        inventoryOverlayRenderer.dispose();
        statusEffectVignetteRenderer.dispose();
        if (touchControllerRenderer != null) touchControllerRenderer.dispose();
        deathOverlayRenderer.dispose();
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
        if (spendTurn && tickEventBus != null) {
            int playerTileColumn = MathUtils.floor(player.positionX / Constants.CELL_SIZE);
            int playerTileRow    = MathUtils.floor(player.positionY / Constants.CELL_SIZE);
            tickEventBus.fireTick(playerTileColumn, playerTileRow, player, TickCause.SKIP_TURN);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Applies the chosen level-up reward and returns the game to PLAYING state. */
    private void applyLevelUpReward(LevelUpReward reward) {
        playerProgress.applyLevelUpReward(reward);
        if (reward == LevelUpReward.HP_BOOST) {
            player.increaseMaxHealth(GameBalance.LEVEL_UP_HP_BONUS);
        } else if (reward == LevelUpReward.ARMOR_BOOST) {
            player.increaseMaxArmor(GameBalance.LEVEL_UP_ARMOR_BONUS);
        } else if (reward == LevelUpReward.DAMAGE_BOOST) {
            enemyManager.setPlayerFlatDamageBonus(playerProgress.getFlatDamageBonus());
        }
        if (touchInputState != null) {
            touchInputState.resetAllButtonStates();
        }
        runPhase = RunPhase.PLAYING;
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
        java.util.List<Weapon> shuffled = new java.util.ArrayList<>(inventory.getArsenal());
        java.util.Collections.shuffle(shuffled, new java.util.Random(floorSeed(runSeed, 0)));

        int offerCount = Math.min(
                ge.tbegvadze.toon3d.util.LevelGenConstants.START_ROOM_WEAPON_OFFER_COUNT,
                shuffled.size());

        startRoomWeapons     = new java.util.ArrayList<>();
        startRoomGroundItems = new java.util.ArrayList<>();

        for (int offerIndex = 0; offerIndex < offerCount; offerIndex++) {
            Weapon    offeredWeapon = shuffled.get(offerIndex);
            ItemType  itemType      = weaponClassToItemType(offeredWeapon);
            GroundItem groundItem   = new GroundItem(
                    startGen.getWeaponTileColumn(offerIndex),
                    startGen.getWeaponTileRow(offerIndex),
                    itemType, 1);
            groundItems.add(groundItem);
            startRoomWeapons.add(offeredWeapon);
            startRoomGroundItems.add(groundItem);
        }

        playerController.setWeaponGroundItemPickedUpCallback(this::handleStartRoomWeaponPickup);
    }

    /**
     * Invoked by PlayerController when the player steps onto one of the three weapon offer tiles.
     * Equips the chosen weapon, grants starter ammo, and despawns the unchosen offers.
     */
    private void handleStartRoomWeaponPickup(GroundItem pickedItem) {
        if (startRoomChoiceResolved || startRoomWeapons == null || startRoomGroundItems == null) return;

        int pickedIndex = startRoomGroundItems.indexOf(pickedItem);
        if (pickedIndex < 0) return;

        Weapon chosenWeapon = startRoomWeapons.get(pickedIndex);
        inventory.getLoadout().tryEquip(chosenWeapon);
        weaponHudRenderer.setEquippedWeapon(chosenWeapon);
        addStarterAmmoForWeapon(chosenWeapon);

        // Despawn the unchosen offers from the live ground-item list.
        for (int otherIndex = 0; otherIndex < startRoomGroundItems.size(); otherIndex++) {
            if (otherIndex != pickedIndex) {
                groundItems.remove(startRoomGroundItems.get(otherIndex));
            }
        }

        startRoomChoiceResolved = true;

        if (eventTextSystem != null) {
            eventTextSystem.spawnWithColor(
                    "EQUIPPED: " + chosenWeapon.getDisplayName(), EventTextSystem.COLOR_GREEN);
        }
    }

    /** Grants the starter ammo reserve appropriate for the weapon class just chosen. */
    private void addStarterAmmoForWeapon(Weapon weapon) {
        AmmoType ammoType = null;
        int      amount   = 0;
        if (weapon instanceof Shotgun || weapon instanceof DoubleBarrelShotgun) {
            ammoType = AmmoType.SHELLS;
            amount   = ItemConstants.START_ROOM_AMMO_SHELLS;
        } else if (weapon instanceof PlasmaRifle || weapon instanceof Incinerator) {
            ammoType = AmmoType.CELLS;
            amount   = ItemConstants.START_ROOM_AMMO_CELLS;
        } else if (weapon instanceof Chaingun) {
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
        if (weapon instanceof Shotgun || weapon instanceof DoubleBarrelShotgun) return ItemType.WEAPON_SHOTGUN;
        if (weapon instanceof PlasmaRifle || weapon instanceof Incinerator)      return ItemType.WEAPON_PLASMA;
        if (weapon instanceof GrenadeLauncher)                                   return ItemType.WEAPON_ROCKET;
        return ItemType.WEAPON_PISTOL;
    }
}
