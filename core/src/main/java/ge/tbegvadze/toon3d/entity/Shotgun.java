package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.WeaponConstants;

/**
 * Single-shell, one-step-reload shotgun.
 *
 * Stats: damage 50, clipSize 1, reloadTime 1 tick, dropCoeff 0.18, range 5.
 * One shot depletes the clip; one completed tile-step reloads it.
 *
 * marchShot() walks the facing direction tile by tile up to SHOTGUN_RANGE_TILES.
 * Stops at the first wall.
 *
 * Worked damage table (coefficient 0.18, floor 0.15):
 *   distance 1: 50 × 0.82 = 41   (bread-and-butter adjacent shot)
 *   distance 2: 50 × 0.64 = 32
 *   distance 3: 50 × 0.46 = 23
 *   distance 4: 50 × 0.28 = 14
 *   distance 5: 50 × 0.15 =  8   (clamped by floor; edge of range)
 */
public class Shotgun extends Weapon {

    public Shotgun() {
        super("SHOTGUN",
              WeaponConstants.SHOTGUN_DAMAGE,
              WeaponConstants.SHOTGUN_CLIP_SIZE,
              WeaponConstants.SHOTGUN_RELOAD_TIME_TICKS,
              WeaponConstants.SHOTGUN_DAMAGE_DROP_COEFF,
              WeaponConstants.SHOTGUN_RANGE_TILES,
              AmmoType.SHELLS);
        setBaseAccuracy(WeaponConstants.SHOTGUN_BASE_ACCURACY);
    }

    @Override public boolean isMelee() { return false; }

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
                    int damageThisHit = damageAtDistance(distanceTiles);
                    setLastHitEnemy(hitEnemy, damageThisHit, enemyHitTarget.isAtFullHp(hitEnemy));
                    enemyHitTarget.applyDamageTo(hitEnemy, damageThisHit);
                    dispatchHitCallbacks(new FireResult(false, distanceTiles));
                    clearLastHit();
                    if (!WeaponConstants.SHOTGUN_PENETRATION) {
                        return new FireResult(false, distanceTiles);
                    }
                }
            }
        }
        return FireResult.MISSED;
    }

    @Override public String getNormalTexturePath() { return WeaponConstants.SHOTGUN_NORMAL_TEXTURE_PATH; }
    @Override public String getFireTexturePath()   { return WeaponConstants.SHOTGUN_FIRE_TEXTURE_PATH;   }
    @Override public String getReloadTexturePath() { return WeaponConstants.SHOTGUN_RELOAD_TEXTURE_PATH; }
}
