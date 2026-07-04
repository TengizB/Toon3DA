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

    /**
     * Applies (or refreshes) a BURNING damage-over-time status on the given enemy.
     * Used by the Incinerator so its cone leaves enemies on fire for several turns
     * after the trigger pull. Routes into the shared StatusEffectController so the
     * burn ticks, respects fire resistance/immunity, and attributes DoT kills.
     * Default: no-op; EnemyManager overrides with the real status application.
     *
     * @param enemy            the token returned by enemyAt()
     * @param turns            burn duration in world turns
     * @param magnitudePerTurn burn damage applied each turn
     */
    default void applyBurningStatus(Object enemy, int turns, int magnitudePerTurn) {}

    /**
     * Applies (or refreshes) a VULNERABLE mark on the given enemy (strategy-combat-order-6): while it
     * lasts the target takes more damage from every incoming hit, and each application adds a stack up
     * to the balance-capped maximum. The "marking shot" setup — land a cheap mark, then dump the big
     * weapon for a burst that clears the golden-ratio TTK a turn early.
     * Default: no-op; EnemyManager overrides with the real status application.
     *
     * @param enemy the token returned by enemyAt()
     * @param turns mark duration in world turns
     */
    default void applyVulnerableStatus(Object enemy, int turns) {}

    /**
     * Applies (or refreshes) an EXPOSED flag on the given enemy (strategy-combat-order-6): the next hit
     * into the target ignores its Block. The dedicated answer to turtling, Block-stacking enemies for a
     * build without an armor-piercing weapon. Default: no-op; EnemyManager overrides.
     *
     * @param enemy the token returned by enemyAt()
     * @param turns how many world turns the flag stays armed before it lapses unused
     */
    default void applyExposedStatus(Object enemy, int turns) {}
}
