package ge.tbegvadze.toon3d.input;

import java.util.Collections;
import java.util.List;

import com.badlogic.gdx.math.MathUtils;
import ge.tbegvadze.toon3d.door.DoorManager;
import ge.tbegvadze.toon3d.door.DoorState;
import ge.tbegvadze.toon3d.enemy.EnemyManager;
import ge.tbegvadze.toon3d.entity.*;
import ge.tbegvadze.toon3d.input.touch.TouchAction;
import ge.tbegvadze.toon3d.input.touch.TouchInputState;
import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.item.GroundItem;
import ge.tbegvadze.toon3d.item.Inventory;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.level.KeycardColor;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.progression.PlayerStats;
import ge.tbegvadze.toon3d.render.EventTextSystem;
import ge.tbegvadze.toon3d.status.StatusType;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.ItemConstants;
import ge.tbegvadze.toon3d.util.WeaponConstants;
import ge.tbegvadze.toon3d.world.LevelTransitionListener;
import ge.tbegvadze.toon3d.world.TickCause;
import ge.tbegvadze.toon3d.world.TickEventBus;

public class PlayerController {

    private enum ActionState { IDLE, MOVING, ROTATING, INTERACTING, FIRING, SKIPPING, HEALING }

    private final Player player;
    private final Level level;
    private final DoorManager doorManager;
    private final PlayerInventory inventory;

    private TickEventBus            tickEventBus                      = null;
    private EnemyManager            enemyManager                      = null;
    private BarrelHitTarget         barrelHitTarget                   = null;
    private LevelTransitionListener transitionListener                = null;
    private TouchInputState         touchInputState                   = null;
    private EventTextSystem         eventTextSystem                   = null;
    private Runnable                weaponSwitchCallback              = null;
    private Runnable                inventoryToggleCallback           = null;
    private Runnable                inspectWeaponCallback             = null;
    private Runnable                shopOpenCallback                  = null;
    private Inventory               itemInventory                     = null;
    private Loadout                 loadout                           = null;
    private PlayerStats             playerStats                       = null;
    private List<GroundItem>        groundItems                       = Collections.emptyList();

    /** The weapon GroundItem the player is currently standing on, or null. */
    private GroundItem standingOnWeapon = null;

    private ActionState actionState = ActionState.IDLE;
    private float actionProgress = 0f;

    private float sourcePositionX;
    private float sourcePositionY;
    private float targetPositionX;
    private float targetPositionY;

    private float sourceDirectionAngleRadians;
    private float targetDirectionAngleRadians;

    public PlayerController(Player player, Level level, DoorManager doorManager,
                            PlayerInventory inventory) {
        this.player      = player;
        this.level       = level;
        this.doorManager = doorManager;
        this.inventory   = inventory;
    }

    public void setTickEventBus(TickEventBus bus) {
        this.tickEventBus = bus;
    }

    public void setEnemyManager(EnemyManager manager) {
        this.enemyManager = manager;
    }

    public void setBarrelHitTarget(BarrelHitTarget target) {
        this.barrelHitTarget = target;
    }

    public void setTransitionListener(LevelTransitionListener listener) {
        this.transitionListener = listener;
    }

    public void setEventTextSystem(EventTextSystem system) {
        this.eventTextSystem = system;
    }

    public void setTouchInputState(TouchInputState state) {
        this.touchInputState = state;
    }

    public void setWeaponSwitchCallback(Runnable callback) {
        this.weaponSwitchCallback = callback;
    }

    public void setInventoryToggleCallback(Runnable callback) {
        this.inventoryToggleCallback = callback;
    }

    /** Called by World when the player taps INSPECT while standing on a weapon GroundItem. */
    public void setInspectWeaponCallback(Runnable callback) {
        this.inspectWeaponCallback = callback;
    }

    /** Called by World when the player taps USE while facing a vending machine (shop_order_1). */
    public void setShopOpenCallback(Runnable callback) {
        this.shopOpenCallback = callback;
    }

    /** Returns the weapon GroundItem the player is currently standing on, or null. */
    public GroundItem getStandingOnWeapon() { return standingOnWeapon; }

    /** Clears the standing-on-weapon record; called by World after the weapon is taken or dropped. */
    public void clearStandingOnWeapon() { standingOnWeapon = null; }

    public void setItemInventory(Inventory inventory) {
        this.itemInventory = inventory;
    }

    public void setLoadout(Loadout loadoutReference) {
        this.loadout = loadoutReference;
    }

    /** Injects the player stat system so AGILITY can scale action durations. */
    public void setPlayerStats(PlayerStats stats) {
        this.playerStats = stats;
    }

    /** Replaces the ground item list; called by World after each level build. */
    public void setGroundItems(List<GroundItem> items) {
        this.groundItems = (items != null) ? items : new java.util.ArrayList<>();
    }

    public boolean isIdle() { return actionState == ActionState.IDLE; }

    public void update(float deltaTime) {
        switch (actionState) {
            case MOVING:      advanceMove(deltaTime);        break;
            case ROTATING:    advanceRotate(deltaTime);      break;
            case INTERACTING: advanceInteracting(deltaTime); break;
            case FIRING:      advanceFiring(deltaTime);      break;
            case SKIPPING:    advanceSkipping(deltaTime);    break;
            case HEALING:     advanceHealing(deltaTime);     break;
            case IDLE:        pollInput();                   break;
        }
    }

    private void advanceMove(float deltaTime) {
        float durationMultiplier = (playerStats != null) ? playerStats.getActionDurationMultiplier() : 1.0f;
        float slowMultiplier     = player.getSlowMultiplier();
        actionProgress = Math.min(1f, actionProgress + deltaTime / (Constants.PLAYER_MOVE_DURATION * durationMultiplier * slowMultiplier));
        float easedProgress = GameMath.smoothstep01(actionProgress);
        player.positionX = GameMath.lerp(sourcePositionX, targetPositionX, easedProgress);
        player.positionY = GameMath.lerp(sourcePositionY, targetPositionY, easedProgress);
        if (actionProgress >= 1f) {
            player.positionX = targetPositionX;
            player.positionY = targetPositionY;
            int settledTileColumn = MathUtils.floor(player.positionX / Constants.CELL_SIZE);
            int settledTileRow    = MathUtils.floor(player.positionY / Constants.CELL_SIZE);
            doorManager.notifyPlayerSettled(settledTileColumn, settledTileRow);
            pickUpMedicalIfPresent(settledTileColumn, settledTileRow);
            pickUpArmourIfPresent(settledTileColumn, settledTileRow);
            pickUpKeycardIfPresent(settledTileColumn, settledTileRow);
            pickUpAmmoIfPresent(settledTileColumn, settledTileRow);
            pickUpCreditGroundItemIfPresent(settledTileColumn, settledTileRow);
            pickUpWeaponGroundItemIfPresent(settledTileColumn, settledTileRow);
            checkStairsDescentIfPresent(settledTileColumn, settledTileRow);
            finishAction(true, TickCause.MOVE);
        }
    }

    private void advanceHealing(float deltaTime) {
        actionProgress = Math.min(1f, actionProgress + deltaTime / ItemConstants.PLAYER_HEAL_DURATION);
        if (actionProgress >= 1f) {
            MedicalTier tier   = chooseHealTier(player.getHealth(), player.getMaxHealth());
            int         amount = spendHeal(tier);
            player.applyHealing(amount);
            if (amount > 0 && eventTextSystem != null) {
                eventTextSystem.spawnWithColor("+" + amount + " HP", EventTextSystem.COLOR_GREEN);
            }
            finishAction(true, TickCause.HEAL);
        }
    }

    private void advanceFiring(float deltaTime) {
        actionProgress = Math.min(1f, actionProgress + deltaTime / WeaponConstants.PLAYER_FIRE_DURATION);
        if (actionProgress >= 1f) {
            finishAction(true, TickCause.FIRE);
        }
    }

    private void advanceSkipping(float deltaTime) {
        actionProgress = Math.min(1f, actionProgress + deltaTime / Constants.PLAYER_MOVE_DURATION);
        if (actionProgress >= 1f) {
            finishAction(true, TickCause.SKIP_TURN);
        }
    }

    private void pickUpMedicalIfPresent(int tileColumn, int tileRow) {
        char cell = level.getCell(tileColumn, tileRow);
        if (!Level.isMedicalPickup(cell)) return;
        if (itemInventory == null) return;
        MedicalTier tier = Level.medicalTierOfPickup(cell);
        ItemType    type = tier.getItemType();
        // Stepping onto the tile is the whole interaction — the medkit is stashed as a
        // slotted inventory item (one stack occupies one cell), never applied instantly.
        if (itemInventory.tryAdd(type, 1)) {
            level.consumePickupAt(tileColumn, tileRow);
            if (eventTextSystem != null)
                eventTextSystem.spawnWithColor(type.getDisplayName().toUpperCase() + " +1", EventTextSystem.COLOR_GREEN);
        } else if (eventTextSystem != null) {
            // Inventory full — anti-waste: leave the pickup on the floor for later.
            eventTextSystem.spawn("MED STASH FULL");
        }
    }

    /** True if the marine is carrying at least one stim-pack or field medkit. */
    private boolean hasAnyMedical() {
        return itemInventory != null
                && (itemInventory.countOf(ItemType.MEDKIT_SMALL) > 0
                    || itemInventory.countOf(ItemType.MEDKIT_LARGE) > 0);
    }

    /**
     * Selects the tier to spend, preferring the smallest charge that brings the marine to or
     * above maxHealth. Spends a stim if it alone tops the player off; otherwise spends a
     * medkit. Always returns non-null when hasAnyMedical() is true.
     */
    private MedicalTier chooseHealTier(int currentHealth, int maxHealth) {
        if (itemInventory == null) return MedicalTier.FIELD_MEDKIT;
        int stimCount = itemInventory.countOf(ItemType.MEDKIT_SMALL);
        if (stimCount > 0) {
            int medkitCount     = itemInventory.countOf(ItemType.MEDKIT_LARGE);
            int healthAfterStim = currentHealth + ItemConstants.MEDKIT_STIM_HEAL;
            if (healthAfterStim >= maxHealth || medkitCount == 0) return MedicalTier.STIM;
        }
        return MedicalTier.FIELD_MEDKIT;
    }

    /** Spends one unit of the given tier from the slotted inventory and returns the HP value to restore. */
    private int spendHeal(MedicalTier tier) {
        if (itemInventory == null) return 0;
        ItemType type = tier.getItemType();
        if (itemInventory.countOf(type) <= 0) return 0;
        itemInventory.spend(type, 1);
        return tier.getHealAmount();
    }

    private void pickUpArmourIfPresent(int tileColumn, int tileRow) {
        char cell = level.getCell(tileColumn, tileRow);
        if (Level.isArmourPickup(cell)) {
            // Anti-waste: only consume the pickup if the player is below max armour.
            if (player.getArmor() < player.getMaxArmor()) {
                int restore = Level.armourRestoreOfPickup(cell);
                player.applyArmor(restore);
                level.consumePickupAt(tileColumn, tileRow);
                if (eventTextSystem != null) eventTextSystem.spawnWithColor("+AR", EventTextSystem.COLOR_GREEN);
            }
        }
    }

    private void pickUpKeycardIfPresent(int tileColumn, int tileRow) {
        char cell = level.getCell(tileColumn, tileRow);
        if (Level.isKeycardPickup(cell)) {
            KeycardColor color = Level.keycardColorOfPickup(cell);
            inventory.addKeycard(color);
            level.consumeKeycardAt(tileColumn, tileRow);
        }
    }

    private void pickUpAmmoIfPresent(int tileColumn, int tileRow) {
        char cell = level.getCell(tileColumn, tileRow);
        if (!Level.isAmmoPickup(cell)) return;
        if (itemInventory == null) return;
        AmmoType type = Level.ammoTypeOfPickup(cell);
        if (type == null) return;
        int amount = type.getAmountPerBox();
        // Always consume the floor tile — anti-hoarding: overflow is silently discarded.
        level.consumePickupAt(tileColumn, tileRow);
        int amountBefore = itemInventory.countOf(type.getItemType());
        itemInventory.tryAdd(type.getItemType(), amount);
        int amountAdded  = itemInventory.countOf(type.getItemType()) - amountBefore;
        if (eventTextSystem != null && amountAdded > 0) {
            eventTextSystem.spawnWithColor("+" + amountAdded + " " + type.getDisplayName().toUpperCase(),
                                           EventTextSystem.COLOR_GREEN);
        }
    }

    private void checkStairsDescentIfPresent(int tileColumn, int tileRow) {
        if (transitionListener != null && Level.isStairsDown(level.getCell(tileColumn, tileRow))) {
            transitionListener.onDescentRequested();
        }
    }

    private void advanceInteracting(float deltaTime) {
        actionProgress = Math.min(1f, actionProgress + deltaTime / Constants.DOOR_OPEN_DURATION);
        if (actionProgress >= 1f) {
            finishAction(false, null);
        }
    }

    private void advanceRotate(float deltaTime) {
        float durationMultiplier = (playerStats != null) ? playerStats.getActionDurationMultiplier() : 1.0f;
        float slowMultiplier     = player.getSlowMultiplier();
        actionProgress = Math.min(1f, actionProgress + deltaTime / (Constants.PLAYER_ROTATE_DURATION * durationMultiplier * slowMultiplier));
        float currentAngle = GameMath.lerp(sourceDirectionAngleRadians, targetDirectionAngleRadians, GameMath.smoothstep01(actionProgress));
        player.directionX = MathUtils.cos(currentAngle);
        player.directionY = MathUtils.sin(currentAngle);
        if (actionProgress >= 1f) {
            // Snap to exact cardinal to prevent floating-point drift accumulating over many rotations.
            player.directionX = (float) Math.round(player.directionX);
            player.directionY = (float) Math.round(player.directionY);
            finishAction(false, null);
        }
    }

    private void pickUpCreditGroundItemIfPresent(int tileColumn, int tileRow) {
        for (int index = groundItems.size() - 1; index >= 0; index--) {
            GroundItem item = groundItems.get(index);
            if (item.tileColumn != tileColumn || item.tileRow != tileRow) continue;
            ItemType type = item.stack.getType();
            if (type != ItemType.CREDIT_SMALL && type != ItemType.CREDIT_MEDIUM && type != ItemType.CREDIT_LARGE) continue;
            int amount = item.stack.getQuantity();
            groundItems.remove(index);
            if (playerStats != null) playerStats.addCredits(amount);
            if (eventTextSystem != null) {
                eventTextSystem.spawnWithColor("+" + amount + " CREDITS", EventTextSystem.COLOR_CREDIT_CYAN);
            }
            return;
        }
    }

    private void pickUpWeaponGroundItemIfPresent(int tileColumn, int tileRow) {
        GroundItem found = null;
        for (GroundItem item : groundItems) {
            if (item.tileColumn == tileColumn && item.tileRow == tileRow) {
                found = item;
                break;
            }
        }
        standingOnWeapon = found;
        if (found == null) return;
        // Auto-open the weapon card — the overlay decides equip/swap/convert action.
        if (inspectWeaponCallback != null) {
            inspectWeaponCallback.run();
        }
    }

    public Weapon findWeaponInArsenalForType(ItemType itemType) {
        if (inventory == null || itemType == null) return null;
        for (Weapon weapon : inventory.getArsenal()) {
            if (weaponMatchesItemType(weapon, itemType)) return weapon;
        }
        return null;
    }

    private static boolean weaponMatchesItemType(Weapon weapon, ItemType itemType) {
        switch (itemType) {
            case WEAPON_SHOTGUN:       return weapon instanceof Shotgun && !(weapon instanceof DoubleBarrelShotgun);
            case WEAPON_DOUBLE_BARREL: return weapon instanceof DoubleBarrelShotgun;
            case WEAPON_PLASMA:        return weapon instanceof PlasmaRifle;
            case WEAPON_CHAINGUN:      return weapon instanceof Chaingun;
            case WEAPON_ASSAULT_RIFLE: return weapon instanceof AssaultRifle;
            case WEAPON_RAILGUN:       return weapon instanceof Railgun;
            case WEAPON_INCINERATOR:   return weapon instanceof Incinerator;
            case WEAPON_ROCKET:        return weapon instanceof GrenadeLauncher;
            case WEAPON_PISTOL:        return false; // no Pistol class; falls through to ammo
            case WEAPON_FIST:          return weapon instanceof Fist;
            case WEAPON_KNIFE:         return weapon instanceof CombatKnife;
            case WEAPON_HAMMER:         return weapon instanceof Hammer;
            case WEAPON_CHAINSAW:      return weapon instanceof MeleeChainsaw;
            default:                   return false;
        }
    }

    private boolean isWeaponInLoadout(Weapon weapon) {
        if (loadout == null) return false;
        for (int slotIndex = 0; slotIndex < loadout.getSlotCount(); slotIndex++) {
            if (loadout.getSlot(slotIndex) == weapon) return true;
        }
        return false;
    }

    public static AmmoType weaponItemTypeToAmmoType(ItemType weaponType) {
        if (weaponType == null) return null;
        switch (weaponType) {
            case WEAPON_PISTOL:        return AmmoType.BULLETS;
            case WEAPON_SHOTGUN:       return AmmoType.SHELLS;
            case WEAPON_DOUBLE_BARREL: return AmmoType.SHELLS;
            case WEAPON_CHAINGUN:      return AmmoType.BULLETS;
            case WEAPON_ASSAULT_RIFLE: return AmmoType.BULLETS;
            case WEAPON_PLASMA:        return AmmoType.CELLS;
            case WEAPON_INCINERATOR:   return AmmoType.CELLS;
            case WEAPON_RAILGUN:       return AmmoType.SLUGS;
            case WEAPON_ROCKET:        return AmmoType.ROCKETS;
            case WEAPON_FIST:
            case WEAPON_KNIFE:
            case WEAPON_HAMMER:
            case WEAPON_CHAINSAW:      return null; // melee weapons have no ammo
            default:                   return null;
        }
    }

    private void finishAction(boolean causesTick, TickCause cause) {
        actionState    = ActionState.IDLE;
        actionProgress = 0f;
        if (causesTick && tickEventBus != null) {
            int playerTileColumn = MathUtils.floor(player.positionX / Constants.CELL_SIZE);
            int playerTileRow    = MathUtils.floor(player.positionY / Constants.CELL_SIZE);
            tickEventBus.fireTick(playerTileColumn, playerTileRow, player, cause);
        }
    }

    private void tryHeal() {
        if (!hasAnyMedical()) {
            if (eventTextSystem != null) eventTextSystem.spawn("NO MEDKITS");
            return;
        }
        if (player.getHealth() >= player.getMaxHealth()) {
            if (eventTextSystem != null) eventTextSystem.spawn("ALREADY FULL");
            return;
        }
        actionState    = ActionState.HEALING;
        actionProgress = 0f;
    }

    private void pollInput() {
        if (touchInputState == null) return;

        TouchAction heldAction = touchInputState.getHeldAction();
        TouchAction tapAction  = touchInputState.consumeTapAction();

        // If the player is stunned, any input attempt wastes the turn.
        if (player.hasActiveStun()) {
            if (heldAction != TouchAction.NONE || tapAction != TouchAction.NONE) {
                player.clearStunFlag();
                if (eventTextSystem != null) eventTextSystem.spawn("STUNNED!");
                trySkipTurn();
            }
            return;
        }

        if (tapAction == TouchAction.OPEN_INVENTORY) {
            if (inventoryToggleCallback != null) inventoryToggleCallback.run();
            return;
        }

        if (tapAction == TouchAction.INSPECT_WEAPON) {
            // groundItems.contains() guards against a stale standingOnWeapon reference
            // in case World removed the item from the list before clearStandingOnWeapon() ran.
            if (standingOnWeapon != null && groundItems.contains(standingOnWeapon)
                    && inspectWeaponCallback != null) {
                inspectWeaponCallback.run();
            }
            return;
        }

        if (tapAction == TouchAction.USE_MACHINE) {
            // World only shows the USE button when a machine faces the player, so just route it.
            if (shopOpenCallback != null) shopOpenCallback.run();
            return;
        }

        if (tapAction == TouchAction.SWITCH_WEAPON) {
            trySwitchWeapon();
            return;
        }

        if (tapAction == TouchAction.HEAL) {
            tryHeal();
        } else if (tapAction == TouchAction.FIRE) {
            tryFire();
        } else if (tapAction == TouchAction.SKIP_TURN) {
            trySkipTurn();
        } else if (tapAction == TouchAction.RELOAD) {
            tryReload();
        } else if (heldAction == TouchAction.FORWARD) {
            tryMove(player.directionX, player.directionY);
        } else if (heldAction == TouchAction.BACK) {
            tryMove(-player.directionX, -player.directionY);
        } else if (heldAction == TouchAction.ROTATE_LEFT) {
            startRotation(MathUtils.PI / 2f);
        } else if (heldAction == TouchAction.ROTATE_RIGHT) {
            startRotation(-MathUtils.PI / 2f);
        } else if (heldAction == TouchAction.STRAFE_LEFT) {
            tryMove(-player.directionY, player.directionX);
        } else if (heldAction == TouchAction.STRAFE_RIGHT) {
            tryMove(player.directionY, -player.directionX);
        }
    }

    private void tryFire() {
        Weapon weapon = inventory.getEquippedWeapon();
        if (weapon == null) return;
        if (!weapon.canFire()) {
            if (weapon.isReloading()) trySkipTurn();
            return;
        }

        // Railgun requires a charge turn before the slug is released.
        if (weapon instanceof Railgun) {
            Railgun railgun = (Railgun) weapon;
            if (!railgun.isCharging()) {
                // First press: spin up the capacitor — consume a turn but do not fire.
                railgun.advanceCharge();
                if (eventTextSystem != null) eventTextSystem.spawn("CHARGING...");
                actionState    = ActionState.FIRING;
                actionProgress = 0f;
                return;
            }
            // Second press: advance to full charge and fire this same turn.
            railgun.advanceCharge();
        }

        int playerTileColumn = MathUtils.floor(player.positionX / Constants.CELL_SIZE);
        int playerTileRow    = MathUtils.floor(player.positionY / Constants.CELL_SIZE);
        int facingStepColumn = Math.round(player.directionX);
        int facingStepRow    = Math.round(player.directionY);
        EnemyHitTarget hitTarget = (enemyManager != null) ? enemyManager : null;
        weapon.fire(playerTileColumn, playerTileRow, facingStepColumn, facingStepRow,
                    level, hitTarget, barrelHitTarget, doorManager::blocksSight);
        actionState    = ActionState.FIRING;
        actionProgress = 0f;
    }

    private void tryMove(float moveDirectionX, float moveDirectionY) {
        float newPositionX   = player.positionX + moveDirectionX * Constants.CELL_SIZE;
        float newPositionY   = player.positionY + moveDirectionY * Constants.CELL_SIZE;
        int targetTileColumn = MathUtils.floor(newPositionX / Constants.CELL_SIZE);
        int targetTileRow    = MathUtils.floor(newPositionY / Constants.CELL_SIZE);

        char targetCell = level.getCell(targetTileColumn, targetTileRow);

        if (Level.isDoor(targetCell)) {
            if (Level.isLockedDoor(targetCell) && !doorManager.isUnlocked(targetTileColumn, targetTileRow)) {
                // Locked door: unlock with matching keycard, or deny silently (no turn consumed).
                DoorState doorState = doorManager.getStateAt(targetTileColumn, targetTileRow);
                if (doorState == DoorState.CLOSED) {
                    KeycardColor required = doorManager.getRequiredKeycard(targetTileColumn, targetTileRow);
                    if (required != null && inventory.hasKeycard(required)) {
                        doorManager.unlock(targetTileColumn, targetTileRow);
                        doorManager.requestOpen(targetTileColumn, targetTileRow);
                        actionState    = ActionState.INTERACTING;
                        actionProgress = 0f;
                    }
                    // No matching keycard: silently denied, no turn consumed.
                }
                // OPENING/OPEN/CLOSING states: wait or fall through handled below.
                return;
            }

            // Plain door or already-unlocked keycard door: trigger open on first approach.
            DoorState doorState = doorManager.getStateAt(targetTileColumn, targetTileRow);
            if (doorState == DoorState.CLOSED) {
                doorManager.requestOpen(targetTileColumn, targetTileRow);
                actionState    = ActionState.INTERACTING;
                actionProgress = 0f;
                return;
            }
            if (doorState == DoorState.OPENING) {
                return; // Still animating — wait.
            }
            // OPEN or CLOSING: fall through to normal move below.
        }

        if (level.isBlockedAt(targetTileColumn, targetTileRow, doorManager)) return;
        if (enemyManager != null && enemyManager.isTileOccupiedByEnemy(targetTileColumn, targetTileRow)) return;
        sourcePositionX = player.positionX;
        sourcePositionY = player.positionY;
        targetPositionX = newPositionX;
        targetPositionY = newPositionY;
        actionState     = ActionState.MOVING;
        actionProgress  = 0f;
    }

    private void trySwitchWeapon() {
        Weapon currentWeapon = inventory.getEquippedWeapon();
        if (currentWeapon instanceof Railgun) {
            ((Railgun) currentWeapon).resetCharge();
        }
        Weapon nextWeapon = inventory.switchToNextWeapon();
        if (nextWeapon == null || nextWeapon == currentWeapon) return;
        if (weaponSwitchCallback != null) weaponSwitchCallback.run();
        if (eventTextSystem != null) eventTextSystem.spawn(nextWeapon.getDisplayName());
    }

    /**
     * Selects a specific loadout slot by index.
     * No-op when the slot is empty or the index is out of range.
     * Resets any Railgun charge on the weapon being holstered.
     */
    private void trySelectSlot(int index) {
        if (loadout == null || loadout.getSlot(index) == null) return;
        Weapon currentWeapon = loadout.active();
        if (currentWeapon instanceof Railgun) {
            ((Railgun) currentWeapon).resetCharge();
        }
        loadout.selectSlot(index);
        if (weaponSwitchCallback != null) weaponSwitchCallback.run();
        if (eventTextSystem != null && loadout.active() != null) {
            eventTextSystem.spawn(loadout.active().getDisplayName());
        }
    }

    /**
     * Selects the next (direction = +1) or previous (direction = -1) filled loadout slot.
     * No-op when the loadout has only one filled slot.
     * Resets any Railgun charge on the weapon being holstered.
     */
    private void trySelectSlotRelative(int direction) {
        if (loadout == null) return;
        int activeIndex = loadout.getActiveSlotIndex();
        int targetIndex = (direction > 0)
            ? loadout.nextFilledSlot(activeIndex)
            : loadout.previousFilledSlot(activeIndex);
        if (targetIndex == activeIndex) return;
        Weapon currentWeapon = loadout.active();
        if (currentWeapon instanceof Railgun) {
            ((Railgun) currentWeapon).resetCharge();
        }
        loadout.selectSlot(targetIndex);
        if (weaponSwitchCallback != null) weaponSwitchCallback.run();
        if (eventTextSystem != null && loadout.active() != null) {
            eventTextSystem.spawn(loadout.active().getDisplayName());
        }
    }

    private void trySkipTurn() {
        if (eventTextSystem != null) eventTextSystem.spawn("Turn skipped");
        actionState    = ActionState.SKIPPING;
        actionProgress = 0f;
    }

    private void tryReload() {
        Weapon weapon = inventory.getEquippedWeapon();
        if (weapon == null || weapon.getClipSize() == 0) return;  // null or melee — no reload
        if (weapon.getShotsInClip() >= weapon.getEffectiveClipSize()) {
            if (eventTextSystem != null) eventTextSystem.spawn("CLIP FULL");
            return;
        }
        if (!weapon.requestManualReload()) return;  // already reloading or firing — silent
        actionState    = ActionState.SKIPPING;
        actionProgress = 0f;
    }

    private void startRotation(float angleOffsetRadians) {
        sourceDirectionAngleRadians = MathUtils.atan2(player.directionY, player.directionX);
        targetDirectionAngleRadians = sourceDirectionAngleRadians + angleOffsetRadians;
        actionState    = ActionState.ROTATING;
        actionProgress = 0f;
    }
}
