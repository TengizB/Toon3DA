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
}
