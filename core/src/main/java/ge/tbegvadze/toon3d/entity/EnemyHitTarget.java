package ge.tbegvadze.toon3d.entity;

/**
 * Narrow interface passed into Weapon.fire() so the shot can query and damage enemies
 * without creating a direct dependency from entity → enemy.
 * EnemyManager implements this interface.
 */
public interface EnemyHitTarget {

    /**
     * Returns the token representing the living enemy occupying the given tile,
     * or null if no living enemy is there.
     */
    Object enemyAt(int tileColumn, int tileRow);

    /** Applies the given damage amount to the enemy returned by enemyAt(). */
    void applyDamageTo(Object enemy, int amount);

    /**
     * Attempts to move the given enemy to (targetColumn, targetRow).
     * Implementations should check wall collision, prop collision, other-enemy occupancy,
     * and bounds before moving. Returns true if the push succeeded.
     * Default: no-op (returns false); override in EnemyManager.
     */
    default boolean tryPushEnemy(Object enemy, int targetColumn, int targetRow) {
        return false;
    }

    /**
     * Signals that the next applyDamageTo() call originates from a melee weapon.
     * Must be called immediately before applyDamageTo() on a confirmed hit.
     * Default: no-op; EnemyManager overrides to set an internal flag used in killEnemy().
     */
    default void notifyMeleeAttack() {}

    /**
     * Returns true if the given enemy is currently at maximum HP.
     * Called before applyDamageTo() to determine whether the target was at full health
     * before the shot's primary damage is applied (used for OPENING_SALVO ability).
     * Default returns false; EnemyManager overrides with a real check.
     */
    default boolean isAtFullHp(Object enemyObject) {
        return false;
    }
}
