package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.WeaponConstants;

/**
 * Precision automatic bullet rifle — the mid-range workhorse.
 *
 * Shares BULLETS ammunition with the Chaingun but fills a different role: where the
 * Chaingun sprays a 3-round burst at low per-bullet accuracy, the Assault Rifle fires
 * one accurate, hard-hitting round per press at longer effective range.
 *
 * Firing mechanic:
 *   A single hitscan bullet marches the facing direction tile by tile up to
 *   ASSAULT_RIFLE_RANGE_TILES. It stops at the first wall, blocking door, explosive
 *   barrel, or enemy. No penetration — the first solid target stops the bullet.
 *
 * Stats: damage 20, clipSize 30, reloadTime 1 tick, dropCoeff 0.08, range 10 tiles.
 *
 * Damage table (coefficient 0.08, floor 0.15):
 *   distance 1:  20 × 0.92 = 18   distance 4: 20 × 0.68 = 14
 *   distance 6:  20 × 0.52 = 10   distance 8: 20 × 0.36 = 7
 *   distance 10: 20 × 0.20 =  4   (minimum floor applies at extreme range)
 */
public class AssaultRifle extends Weapon {

    public AssaultRifle() {
        super(WeaponConstants.ASSAULT_RIFLE_DISPLAY_NAME,
              WeaponConstants.ASSAULT_RIFLE_DAMAGE,
              WeaponConstants.ASSAULT_RIFLE_CLIP_SIZE,
              WeaponConstants.ASSAULT_RIFLE_RELOAD_TIME_TICKS,
              WeaponConstants.ASSAULT_RIFLE_DAMAGE_DROP_COEFF,
              WeaponConstants.ASSAULT_RIFLE_RANGE_TILES,
              AmmoType.BULLETS);
        setBaseAccuracy(WeaponConstants.ASSAULT_RIFLE_BASE_ACCURACY);
    }

    @Override public boolean isMelee()    { return false; }
    @Override public ItemType getItemType() { return ItemType.WEAPON_ASSAULT_RIFLE; }

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
            if (isShotBlockingCover(targetCell)) {
                return FireResult.HIT_WALL; // column / solid prop blocks the shot
            }
            if (enemyHitTarget != null) {
                Object hitEnemy = enemyHitTarget.enemyAt(targetColumn, targetRow);
                if (hitEnemy != null) {
                    int damageThisHit = damageAtDistance(distanceTiles);
                    setLastHitEnemy(hitEnemy, damageThisHit, enemyHitTarget.isAtFullHp(hitEnemy));
                    enemyHitTarget.applyDamageTo(hitEnemy, damageThisHit);
                    dispatchHitCallbacks(new FireResult(false, distanceTiles));
                    clearLastHit();
                    if (!WeaponConstants.ASSAULT_RIFLE_PENETRATION) {
                        return new FireResult(false, distanceTiles);
                    }
                }
            }
        }
        return FireResult.MISSED;
    }

    @Override public String getNormalTexturePath() { return WeaponConstants.ASSAULT_RIFLE_NORMAL_TEXTURE_PATH; }
    @Override public String getFireTexturePath()   { return WeaponConstants.ASSAULT_RIFLE_FIRE_TEXTURE_PATH;   }
    @Override public String getReloadTexturePath() { return WeaponConstants.ASSAULT_RIFLE_RELOAD_TEXTURE_PATH; }
}
