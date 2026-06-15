package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.GameBalance;

/**
 * Steel Pipe — crowd-control melee weapon.
 * On a connecting hit, attempts to push knockback-eligible enemies one tile
 * directly away from the player. The push is blocked by walls, solid props,
 * out-of-bounds tiles, and other enemies (all checked by EnemyManager).
 */
public final class SteelPipe extends MeleeWeapon {

    public SteelPipe() {
        super("PIPE", GameBalance.MELEE_PIPE_DAMAGE);
    }

    @Override
    protected void onHit(Object target, EnemyHitTarget enemyHitTarget,
                         int targetColumn, int targetRow,
                         int facingStepColumn, int facingStepRow,
                         Level level) {
        int pushColumn = targetColumn + facingStepColumn * GameBalance.MELEE_PIPE_KNOCKBACK_TILES;
        int pushRow    = targetRow    + facingStepRow    * GameBalance.MELEE_PIPE_KNOCKBACK_TILES;
        enemyHitTarget.tryPushEnemy(target, pushColumn, pushRow);
    }
}
