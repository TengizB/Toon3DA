package ge.tbegvadze.toon3d.enemy;

/**
 * Cosmetic callback notified whenever an enemy lands an attack this turn.
 * Implemented by EnemyAttackEffectSystem to spawn projectile/lunge visuals.
 * Kept in the enemy package to avoid a circular enemy ↔ render package dependency.
 */
public interface EnemyAttackListener {
    void onMeleeAttack(Enemy enemy);
    void onRangedAttack(Enemy enemy, int playerColumn, int playerRow);
}
