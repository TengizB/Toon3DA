package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.Constants;

/**
 * Four-shot plasma rifle with full line penetration.
 *
 * Stats: damage 18, clipSize 4, reloadTime 3 ticks, dropCoeff 0.10, range 8.
 * Each shot costs one clip charge; after four shots the weapon reloads over
 * three tile-steps. Plasma bolts pierce every enemy in the facing line —
 * unlike the shotgun which stops at the first target.
 *
 * Damage table (coefficient 0.10, floor 0.15):
 *   distance 1: 18 × 0.90 = 16   distance 4: 18 × 0.60 = 11
 *   distance 6: 18 × 0.40 =  7   distance 8: 18 × 0.20 =  4
 */
public class PlasmaRifle extends Weapon {

    public PlasmaRifle() {
        super("PLASMA RIFLE",
              Constants.PLASMA_RIFLE_DAMAGE,
              Constants.PLASMA_RIFLE_CLIP_SIZE,
              Constants.PLASMA_RIFLE_RELOAD_TIME_TICKS,
              Constants.PLASMA_RIFLE_DAMAGE_DROP_COEFF,
              Constants.PLASMA_RIFLE_RANGE_TILES,
              AmmoType.CELLS);
    }

    @Override
    protected FireResult marchShot(int playerTileColumn, int playerTileRow,
                                   int facingStepColumn, int facingStepRow,
                                   Level level, EnemyHitTarget enemyHitTarget,
                                   BarrelHitTarget barrelHitTarget, DoorBlocksQuery doorBlocksQuery) {
        boolean hitEnemy = false;
        for (int distanceTiles = 1; distanceTiles <= range; distanceTiles++) {
            int  targetColumn = playerTileColumn + facingStepColumn * distanceTiles;
            int  targetRow    = playerTileRow    + facingStepRow    * distanceTiles;
            char targetCell   = level.getCell(targetColumn, targetRow);

            if (Level.isWall(targetCell)) {
                return hitEnemy ? new FireResult(true, distanceTiles) : FireResult.HIT_WALL;
            }
            if (Level.isDoor(targetCell)
                    && doorBlocksQuery != null && doorBlocksQuery.blocksShotAt(targetColumn, targetRow)) {
                return hitEnemy ? new FireResult(true, distanceTiles) : FireResult.HIT_WALL;
            }
            if (barrelHitTarget != null && barrelHitTarget.isExplosiveBarrel(targetColumn, targetRow)) {
                barrelHitTarget.onExplosiveBarrelHit(targetColumn, targetRow);
                return hitEnemy ? new FireResult(true, distanceTiles) : FireResult.HIT_WALL;
            }
            if (enemyHitTarget != null) {
                Object enemy = enemyHitTarget.enemyAt(targetColumn, targetRow);
                if (enemy != null) {
                    enemyHitTarget.applyDamageTo(enemy, damageAtDistance(distanceTiles));
                    hitEnemy = true;
                    // Penetration: continue the bolt through this enemy into the next tile.
                    if (!Constants.PLASMA_RIFLE_PENETRATION) {
                        return new FireResult(false, distanceTiles);
                    }
                }
            }
        }
        return hitEnemy ? new FireResult(false, range) : FireResult.MISSED;
    }

    @Override
    public String hudAmmoString() {
        if (visualState == WeaponVisualState.RELOADING) return "RELOAD";
        return "PLASMA " + shotsInClip + "/" + clipSize;
    }

    @Override public String getNormalTexturePath() { return Constants.PLASMA_RIFLE_NORMAL_TEXTURE_PATH; }
    @Override public String getFireTexturePath()   { return Constants.PLASMA_RIFLE_FIRE_TEXTURE_PATH;   }
    @Override public String getReloadTexturePath() { return Constants.PLASMA_RIFLE_RELOAD_TEXTURE_PATH; }
}
