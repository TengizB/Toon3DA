package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.WeaponConstants;

/**
 * Break-open double-barrel shotgun — fires one barrel per shot, two shots before reload.
 *
 * Stats: damage 32, clipSize 2, reloadTime 1 tick, dropCoeff 0.22, range 4 tiles.
 * Each fire() call depletes one barrel (shotsInClip decremented by 1 per fire).
 * Both barrels must be empty before the break-open reload begins, costing 1 movement tick.
 * No penetration — the blast dissipates on the first enemy contacted.
 *
 * marchShot() walks the facing direction tile by tile up to DBL_SHOTGUN_RANGE_TILES.
 * Stops at the first wall, closed door, or explosive barrel. Hits the first enemy and
 * returns — pellets do not pierce through multiple targets.
 *
 * Damage table (coefficient 0.22, floor 0.15):
 *   distance 1: 32 × 0.78 = 25   (adjacent — devastating burst damage)
 *   distance 2: 32 × 0.56 = 18
 *   distance 3: 32 × 0.34 = 11
 *   distance 4: 32 × 0.15 =  5   (clamped by floor; edge of range)
 */
public class DoubleBarrelShotgun extends Weapon {

    public DoubleBarrelShotgun() {
        super(WeaponConstants.DBL_SHOTGUN_DISPLAY_NAME,
              WeaponConstants.DBL_SHOTGUN_DAMAGE,
              WeaponConstants.DBL_SHOTGUN_CLIP_SIZE,
              WeaponConstants.DBL_SHOTGUN_RELOAD_TIME_TICKS,
              WeaponConstants.DBL_SHOTGUN_DAMAGE_DROP_COEFF,
              WeaponConstants.DBL_SHOTGUN_RANGE_TILES,
              AmmoType.SHELLS);
        setBaseAccuracy(WeaponConstants.DOUBLE_BARREL_SHOTGUN_BASE_ACCURACY);
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
                    setLastHitEnemy(hitEnemy, damageThisHit);
                    enemyHitTarget.applyDamageTo(hitEnemy, damageThisHit);
                    if (!WeaponConstants.DBL_SHOTGUN_PENETRATION) {
                        return new FireResult(false, distanceTiles);
                    }
                }
            }
        }
        return FireResult.MISSED;
    }

    @Override public String getNormalTexturePath() { return WeaponConstants.DBL_SHOTGUN_NORMAL_TEXTURE_PATH; }
    @Override public String getFireTexturePath()   { return WeaponConstants.DBL_SHOTGUN_FIRE_TEXTURE_PATH;   }
    @Override public String getReloadTexturePath() { return WeaponConstants.DBL_SHOTGUN_RELOAD_TEXTURE_PATH; }
}
