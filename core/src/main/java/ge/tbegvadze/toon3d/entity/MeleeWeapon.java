package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.WeaponConstants;

/**
 * Abstract base for all melee weapons (Fist, CombatKnife, SteelPipe, Chainsaw).
 *
 * Instant adjacent-tile resolution — no projectile march:
 *   targetColumn = playerTileColumn + facingStepColumn
 *   targetRow    = playerTileRow    + facingStepRow
 *   If a living enemy occupies (targetColumn, targetRow): HIT, apply damage.
 *   Otherwise: WHIFF, turn still consumed, enemies still retaliate.
 *
 * No ammo, no reload. clipSize=0 is the sentinel used by the HUD to show "MELEE"
 * instead of a bullet count. canFire() only gates on the NORMAL visual state.
 */
public abstract class MeleeWeapon extends Weapon {

    protected MeleeWeapon(String displayName, int baseDamage) {
        // clipSize=0 → no ammo, no auto-reload; reloadTime=0; no ammoType.
        super(displayName, baseDamage, 0, 0, 0f, 1, null);
    }

    @Override
    public boolean canFire() {
        return visualState == WeaponVisualState.NORMAL;
    }

    @Override
    public boolean requestManualReload() {
        return false;
    }

    /**
     * Melee fire: sets FIRING state and calls instant melee resolution.
     * Does NOT decrement shotsInClip (melee has no ammo).
     */
    @Override
    public FireResult fire(int playerTileColumn, int playerTileRow,
                           int facingStepColumn, int facingStepRow,
                           Level level, EnemyHitTarget enemyHitTarget,
                           BarrelHitTarget barrelHitTarget, DoorBlocksQuery doorBlocksQuery) {
        visualState           = WeaponVisualState.FIRING;
        fireFlashTimerSeconds = WeaponConstants.FIRE_FLASH_DURATION;
        flashCycleCount++;
        return marchShot(playerTileColumn, playerTileRow, facingStepColumn, facingStepRow,
                         level, enemyHitTarget, barrelHitTarget, doorBlocksQuery);
    }

    /**
     * Instant adjacent-tile hit resolution.
     * Queries the single tile directly ahead of the player.
     * A miss still costs a turn (enemies retaliate) — see design doc.
     */
    @Override
    protected FireResult marchShot(int playerTileColumn, int playerTileRow,
                                   int facingStepColumn, int facingStepRow,
                                   Level level, EnemyHitTarget enemyHitTarget,
                                   BarrelHitTarget barrelHitTarget, DoorBlocksQuery doorBlocksQuery) {
        int targetColumn = playerTileColumn + facingStepColumn;
        int targetRow    = playerTileRow    + facingStepRow;

        if (enemyHitTarget == null) {
            spawnEventText("Whiff!");
            return FireResult.MISSED;
        }
        Object target = enemyHitTarget.enemyAt(targetColumn, targetRow);
        if (target == null) {
            spawnEventText("Whiff!");
            return FireResult.MISSED;
        }

        enemyHitTarget.applyDamageTo(target, computeDamage());
        onHit(target, enemyHitTarget, targetColumn, targetRow, facingStepColumn, facingStepRow, level);
        return new FireResult(false, 1);
    }

    /** Returns the final damage to deal. Subclasses may apply multipliers here. */
    protected int computeDamage() {
        return damage;
    }

    /**
     * Called after damage is applied on a connecting hit.
     * Override in subclasses to apply knockback or status effects.
     */
    protected void onHit(Object target, EnemyHitTarget enemyHitTarget,
                         int targetColumn, int targetRow,
                         int facingStepColumn, int facingStepRow,
                         Level level) {
    }

    @Override
    public String getNormalTexturePath() {
        return "textures/melee/" + getClass().getSimpleName().toLowerCase() + "_normal.png";
    }

    @Override
    public String getFireTexturePath() {
        return "textures/melee/" + getClass().getSimpleName().toLowerCase() + "_fire.png";
    }

    @Override
    public String getReloadTexturePath() {
        return "textures/melee/" + getClass().getSimpleName().toLowerCase() + "_reload.png";
    }

    @Override
    public String hudAmmoString() {
        return "MELEE";
    }
}
