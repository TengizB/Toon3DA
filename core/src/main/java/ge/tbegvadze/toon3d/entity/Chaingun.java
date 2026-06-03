package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.Constants;

/**
 * Triple-barrel rotary chaingun — rapid sustained fire filling the medium-range damage niche.
 *
 * Stats: damage 10, clipSize 8, reloadTime 3 ticks, dropCoeff 0.10, range 8 tiles.
 * marchShot() walks the facing direction tile by tile up to CHAINGUN_RANGE_TILES.
 * Stops at the first wall, closed door, or explosive barrel.
 * No penetration — bullets are stopped by the first enemy contacted.
 *
 * Damage table (coefficient 0.10, floor 0.15):
 *   distance 1: 10 × 0.90 = 9    (adjacent — light but fast)
 *   distance 2: 10 × 0.80 = 8
 *   distance 4: 10 × 0.60 = 6
 *   distance 6: 10 × 0.40 = 4
 *   distance 8: 10 × 0.20 = 2    (minimum floor; edge of range)
 */
public class Chaingun extends Weapon {

    public Chaingun() {
        super(Constants.CHAINGUN_DISPLAY_NAME,
              Constants.CHAINGUN_DAMAGE,
              Constants.CHAINGUN_CLIP_SIZE,
              Constants.CHAINGUN_RELOAD_TIME_TICKS,
              Constants.CHAINGUN_DAMAGE_DROP_COEFF,
              Constants.CHAINGUN_RANGE_TILES);
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
                    if (!Constants.CHAINGUN_PENETRATION) {
                        return new FireResult(false, distanceTiles);
                    }
                }
            }
        }
        return FireResult.MISSED;
    }

    /** Override ammo prefix to show ROUNDS instead of the default SHELLS. */
    @Override
    public String hudAmmoString() {
        if (visualState == WeaponVisualState.RELOADING) return "RELOAD";
        return "ROUNDS " + shotsInClip + "/" + clipSize;
    }

    @Override public String getNormalTexturePath() { return Constants.CHAINGUN_NORMAL_TEXTURE_PATH; }
    @Override public String getFireTexturePath()   { return Constants.CHAINGUN_FIRE_TEXTURE_PATH;   }
    @Override public String getReloadTexturePath() { return Constants.CHAINGUN_RELOAD_TEXTURE_PATH; }
}
