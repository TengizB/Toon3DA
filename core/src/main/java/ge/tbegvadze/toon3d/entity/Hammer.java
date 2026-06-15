package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.GameBalance;

import java.util.Random;

/**
 * Hammer — heavy melee weapon.
 * On a connecting hit, attempts to push knockback-eligible enemies one tile
 * directly away from the player. The push is probabilistic (50% chance per hit)
 * and is also blocked by walls, solid props, out-of-bounds tiles, and other
 * enemies (all checked by EnemyManager).
 */
public final class Hammer extends MeleeWeapon {

    private final Random knockbackRandom = new Random();

    public Hammer() {
        super("HAMMER", GameBalance.MELEE_HAMMER_DAMAGE);
    }

    @Override
    protected void onHit(Object target, EnemyHitTarget enemyHitTarget,
                         int targetColumn, int targetRow,
                         int facingStepColumn, int facingStepRow,
                         Level level) {
        if (knockbackRandom.nextFloat() < GameBalance.MELEE_HAMMER_KNOCKBACK_CHANCE) {
            int pushColumn = targetColumn + facingStepColumn * GameBalance.MELEE_HAMMER_KNOCKBACK_TILES;
            int pushRow    = targetRow    + facingStepRow    * GameBalance.MELEE_HAMMER_KNOCKBACK_TILES;
            enemyHitTarget.tryPushEnemy(target, pushColumn, pushRow);
        }
    }
}
