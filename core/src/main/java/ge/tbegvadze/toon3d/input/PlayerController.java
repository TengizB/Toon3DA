package ge.tbegvadze.toon3d.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.MathUtils;
import ge.tbegvadze.toon3d.door.DoorManager;
import ge.tbegvadze.toon3d.door.DoorState;
import ge.tbegvadze.toon3d.enemy.EnemyManager;
import ge.tbegvadze.toon3d.entity.*;
import ge.tbegvadze.toon3d.level.KeycardColor;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.render.EventTextSystem;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.input.touch.TouchAction;
import ge.tbegvadze.toon3d.input.touch.TouchInputState;
import ge.tbegvadze.toon3d.world.LevelTransitionListener;
import ge.tbegvadze.toon3d.world.TickCause;
import ge.tbegvadze.toon3d.world.TickEventBus;

public class PlayerController {

    private enum ActionState { IDLE, MOVING, ROTATING, INTERACTING, FIRING, SKIPPING, HEALING }

    private final Player player;
    private final Level level;
    private final DoorManager doorManager;
    private final PlayerInventory inventory;

    private TickEventBus            tickEventBus          = null;
    private EnemyManager            enemyManager          = null;
    private BarrelHitTarget         barrelHitTarget       = null;
    private LevelTransitionListener transitionListener    = null;
    private TouchInputState         touchInputState       = null;
    private EventTextSystem         eventTextSystem       = null;
    private Runnable                weaponSwitchCallback  = null;

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
        actionProgress = Math.min(1f, actionProgress + deltaTime / Constants.PLAYER_MOVE_DURATION);
        player.positionX = GameMath.lerp(sourcePositionX, targetPositionX, actionProgress);
        player.positionY = GameMath.lerp(sourcePositionY, targetPositionY, actionProgress);
        if (actionProgress >= 1f) {
            player.positionX = targetPositionX;
            player.positionY = targetPositionY;
            int settledTileColumn = MathUtils.floor(player.positionX / Constants.CELL_SIZE);
            int settledTileRow    = MathUtils.floor(player.positionY / Constants.CELL_SIZE);
            doorManager.notifyPlayerSettled(settledTileColumn, settledTileRow);
            pickUpMedicalIfPresent(settledTileColumn, settledTileRow);
            pickUpArmourIfPresent(settledTileColumn, settledTileRow);
            pickUpKeycardIfPresent(settledTileColumn, settledTileRow);
            checkStairsDescentIfPresent(settledTileColumn, settledTileRow);
            finishAction(true, TickCause.MOVE);
        }
    }

    private void advanceHealing(float deltaTime) {
        actionProgress = Math.min(1f, actionProgress + deltaTime / Constants.PLAYER_HEAL_DURATION);
        if (actionProgress >= 1f) {
            MedicalTier tier   = inventory.chooseHealTier(player.getHealth(), player.getMaxHealth());
            int         amount = inventory.spendHeal(tier);
            player.applyHealing(amount);
            if (amount > 0 && eventTextSystem != null) {
                eventTextSystem.spawnWithColor("+" + amount + " HP", EventTextSystem.COLOR_GREEN);
            }
            finishAction(true, TickCause.HEAL);
        }
    }

    private void advanceFiring(float deltaTime) {
        actionProgress = Math.min(1f, actionProgress + deltaTime / Constants.PLAYER_FIRE_DURATION);
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
        if (Level.isMedicalPickup(cell)) {
            MedicalTier tier = Level.medicalTierOfPickup(cell);
            // Anti-waste: if stash is full, leave the pickup on the floor.
            if (inventory.canAcceptMedical(tier)) {
                inventory.addMedical(tier);
                level.consumePickupAt(tileColumn, tileRow);
                if (eventTextSystem != null) eventTextSystem.spawnWithColor("+HP", EventTextSystem.COLOR_GREEN);
            }
        }
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
        actionProgress = Math.min(1f, actionProgress + deltaTime / Constants.PLAYER_ROTATE_DURATION);
        float currentAngle = GameMath.lerp(sourceDirectionAngleRadians, targetDirectionAngleRadians, actionProgress);
        player.directionX = MathUtils.cos(currentAngle);
        player.directionY = MathUtils.sin(currentAngle);
        if (actionProgress >= 1f) {
            // Snap to exact cardinal to prevent floating-point drift accumulating over many rotations.
            player.directionX = (float) Math.round(player.directionX);
            player.directionY = (float) Math.round(player.directionY);
            finishAction(false, null);
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
        if (!inventory.hasAnyMedical()) return;
        if (player.getHealth() >= player.getMaxHealth()) return;
        actionState    = ActionState.HEALING;
        actionProgress = 0f;
    }

    private void pollInput() {
        TouchAction heldAction = touchInputState != null
            ? touchInputState.getHeldAction()
            : TouchAction.NONE;
        TouchAction tapAction = touchInputState != null
            ? touchInputState.consumeTapAction()
            : TouchAction.NONE;

        // Weapon switching is free — no turn consumed, works regardless of action state.
        if (Gdx.input.isKeyJustPressed(Constants.KEY_SWITCH_WEAPON) || tapAction == TouchAction.SWITCH_WEAPON) {
            trySwitchWeapon();
            return;
        }

        if (Gdx.input.isKeyJustPressed(Constants.KEY_HEAL)) {
            tryHeal();
        } else if (Gdx.input.isKeyJustPressed(Constants.KEY_FIRE) || tapAction == TouchAction.FIRE) {
            tryFire();
        } else if (Gdx.input.isKeyJustPressed(Constants.KEY_SKIP_TURN) || tapAction == TouchAction.SKIP_TURN) {
            trySkipTurn();
        } else if (tapAction == TouchAction.RELOAD) {
            tryReload();
        } else if (Gdx.input.isKeyPressed(Input.Keys.W) || heldAction == TouchAction.FORWARD) {
            tryMove(player.directionX, player.directionY);
        } else if (Gdx.input.isKeyPressed(Input.Keys.S) || heldAction == TouchAction.BACK) {
            tryMove(-player.directionX, -player.directionY);
        } else if (Gdx.input.isKeyPressed(Input.Keys.A) || heldAction == TouchAction.ROTATE_LEFT) {
            startRotation(MathUtils.PI / 2f);  // CCW 90°
        } else if (Gdx.input.isKeyPressed(Input.Keys.D) || heldAction == TouchAction.ROTATE_RIGHT) {
            startRotation(-MathUtils.PI / 2f); // CW 90°
        } else if (Gdx.input.isKeyPressed(Input.Keys.Q) || heldAction == TouchAction.STRAFE_LEFT) {
            tryMove(-player.directionY, player.directionX);
        } else if (Gdx.input.isKeyPressed(Constants.KEY_STRAFE_RIGHT) || heldAction == TouchAction.STRAFE_RIGHT) {
            tryMove(player.directionY, -player.directionX);
        } else if (Gdx.input.isKeyPressed(Constants.KEY_INTERACT)) {
            tryInteract();
        }
    }

    private void tryFire() {
        Weapon weapon = inventory.getEquippedWeapon();
        if (weapon == null || !weapon.canFire()) return;

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

    /**
     * Attempts to interact with the tile directly in front of the player.
     * Currently handles keycard-locked doors only; routes to tryMove for plain doors.
     */
    private void tryInteract() {
        int facingTileColumn = MathUtils.floor(
                (player.positionX + player.directionX * Constants.CELL_SIZE) / Constants.CELL_SIZE);
        int facingTileRow    = MathUtils.floor(
                (player.positionY + player.directionY * Constants.CELL_SIZE) / Constants.CELL_SIZE);
        char targetCell = level.getCell(facingTileColumn, facingTileRow);
        if (Level.isDoor(targetCell)) {
            tryMove(player.directionX, player.directionY);
        }
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
        if (weaponSwitchCallback != null) weaponSwitchCallback.run();
        if (eventTextSystem != null && nextWeapon != null) {
            eventTextSystem.spawn(nextWeapon.getDisplayName());
        }
    }

    private void trySkipTurn() {
        if (eventTextSystem != null) eventTextSystem.spawn("Turn skipped");
        actionState    = ActionState.SKIPPING;
        actionProgress = 0f;
    }

    private void tryReload() {
        Weapon weapon = inventory.getEquippedWeapon();
        if (weapon == null || !weapon.requestManualReload()) return;
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
