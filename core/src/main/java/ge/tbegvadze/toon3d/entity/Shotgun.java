package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.Constants;

/**
 * Single-shell, one-step-reload shotgun.
 *
 * Stats: damage 24, clipSize 1, reloadTime 1 tick, dropCoeff 0.18, range 5.
 * One shot depletes the clip; one completed tile-step reloads it.
 *
 * marchShot() walks the facing direction tile by tile up to SHOTGUN_RANGE_TILES.
 * Stops at the first wall. Enemy application is stubbed — wire in when
 * EnemyManager is available (see inline comment).
 *
 * Worked damage table (coefficient 0.18, floor 0.15):
 *   distance 1: 24 × 0.82 = 20   (bread-and-butter adjacent shot)
 *   distance 2: 24 × 0.64 = 15
 *   distance 3: 24 × 0.46 = 11
 *   distance 4: 24 × 0.28 =  7
 *   distance 5: 24 × 0.15 =  4   (clamped by floor; edge of range)
 */
public class Shotgun extends Weapon {

    public Shotgun() {
        super("SHOTGUN",
              Constants.SHOTGUN_DAMAGE,
              Constants.SHOTGUN_CLIP_SIZE,
              Constants.SHOTGUN_RELOAD_TIME_TICKS,
              Constants.SHOTGUN_DAMAGE_DROP_COEFF,
              Constants.SHOTGUN_RANGE_TILES);
    }

    @Override
    protected FireResult marchShot(int playerTileColumn, int playerTileRow,
                                   int facingStepColumn, int facingStepRow,
                                   Level level, EnemyHitTarget enemyHitTarget,
                                   BarrelHitTarget barrelHitTarget, DoorBlocksQuery doorBlocksQuery) {
        for (int distanceTiles = 1; distanceTiles <= range; distanceTiles++) {
            int targetColumn = playerTileColumn + facingStepColumn * distanceTiles;
            int targetRow    = playerTileRow    + facingStepRow    * distanceTiles;
            char targetCell  = level.getCell(targetColumn, targetRow);
            if (Level.isWall(targetCell)) {
                return FireResult.HIT_WALL;
            }
            if (Level.isDoor(targetCell)
                    && doorBlocksQuery != null && doorBlocksQuery.blocksShotAt(targetColumn, targetRow)) {
                return FireResult.HIT_WALL;
            }
            if (barrelHitTarget != null && barrelHitTarget.isExplosiveBarrel(targetColumn, targetRow)) {
                barrelHitTarget.onExplosiveBarrelHit(targetColumn, targetRow);
                return FireResult.HIT_WALL;
            }
            if (enemyHitTarget != null) {
                Object hitEnemy = enemyHitTarget.enemyAt(targetColumn, targetRow);
                if (hitEnemy != null) {
                    enemyHitTarget.applyDamageTo(hitEnemy, damageAtDistance(distanceTiles));
                    if (!Constants.SHOTGUN_PENETRATION) {
                        return new FireResult(false, distanceTiles);
                    }
                }
            }
        }
        return FireResult.MISSED;
    }

    @Override public String getNormalTexturePath() { return Constants.SHOTGUN_NORMAL_TEXTURE_PATH; }
    @Override public String getFireTexturePath()   { return Constants.SHOTGUN_FIRE_TEXTURE_PATH;   }
    @Override public String getReloadTexturePath() { return Constants.SHOTGUN_RELOAD_TEXTURE_PATH; }
}
