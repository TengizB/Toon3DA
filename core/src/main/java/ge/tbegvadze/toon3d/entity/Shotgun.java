package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.WeaponConstants;

/**
 * Single-shell, one-step-reload shotgun — the SPREAD point-blank role (balance-overhaul order 3, W1).
 *
 * One shot depletes the 1-shell clip; one completed tile-step reloads it. marchShot() walks the facing
 * direction tile by tile up to SHOTGUN_RANGE_TILES and stops at the first wall / door / cover / enemy.
 *
 * Falloff is a per-tile TABLE, BalanceConfig.SHOTGUN_FALLOFF_BY_TILE (index = distance - 1; beyond the
 * table the shot does nothing), applied to the ladder-scaled base damage (see damageAtDistance). A hit
 * at 1 tile knocks a non-BOSS, non-MINI_ELITE target back one tile; a hit at <= 2 tiles staggers it
 * (SpreadImpact). The numbers are SECTION 22 data — never restate them here.
 */
public class Shotgun extends Weapon {

    public Shotgun() {
        super("SHOTGUN",
              WeaponConstants.SHOTGUN_DAMAGE,
              WeaponConstants.SHOTGUN_CLIP_SIZE,
              WeaponConstants.SHOTGUN_RELOAD_TIME_TICKS,
              WeaponConstants.TABLE_FALLOFF_DROP_COEFFICIENT,
              WeaponConstants.SHOTGUN_RANGE_TILES,
              AmmoType.SHELLS);
        setBaseAccuracy(WeaponConstants.SHOTGUN_BASE_ACCURACY);
    }

    @Override public boolean isMelee()    { return false; }
    @Override public ItemType getItemType() { return ItemType.WEAPON_SHOTGUN; }
    @Override public DamageClass damageClass() { return DamageClass.SPREAD; }

    /**
     * Per-tile falloff TABLE (balance-overhaul order 3, W1) instead of the base drop coefficient; the
     * ladder and fire-cycle terms are the shared {@link #damageWithFalloff} composition.
     */
    @Override
    public int damageAtDistance(int distanceTiles) {
        return damageWithFalloff(GameMath.shotgunFalloffAtTile(WeaponConstants.SHOTGUN_FALLOFF_BY_TILE, distanceTiles));
    }

    @Override
    protected FireResult marchShot(int playerTileColumn, int playerTileRow,
                                   int facingStepColumn, int facingStepRow,
                                   Level level, EnemyHitTarget enemyHitTarget,
                                   BarrelHitTarget barrelHitTarget, DoorBlocksQuery doorBlocksQuery) {
        // OVERPENETRATION: the slug pierces up to overpenetrationExtraTargets() enemies beyond the
        // first before it stops. Without the ability extraTargets is 0 and the shot stops on the
        // first enemy exactly as before.
        int enemiesHit      = 0;
        int extraTargets    = overpenetrationExtraTargets();
        int lastHitDistance = 0;
        for (int distanceTiles = 1; distanceTiles <= range; distanceTiles++) {
            int targetColumn = playerTileColumn + facingStepColumn * distanceTiles;
            int targetRow    = playerTileRow    + facingStepRow    * distanceTiles;
            char targetCell  = level.getCell(targetColumn, targetRow);
            if (Level.isWall(targetCell)) {
                return enemiesHit > 0 ? new FireResult(true, distanceTiles) : FireResult.HIT_WALL;
            }
            if (Level.isDoor(targetCell)
                    && doorBlocksQuery != null && doorBlocksQuery.blocksShotAt(targetColumn, targetRow)) {
                return enemiesHit > 0 ? new FireResult(true, distanceTiles) : FireResult.HIT_WALL;
            }
            if (barrelHitTarget != null && barrelHitTarget.isExplosiveBarrel(targetColumn, targetRow)) {
                barrelHitTarget.onExplosiveBarrelHit(targetColumn, targetRow);
                return enemiesHit > 0 ? new FireResult(true, distanceTiles) : FireResult.HIT_WALL;
            }
            if (isShotBlockingCover(targetCell)) {
                // column / solid prop blocks the shot (a crystal spire on the tile takes the hit)
                hitSpireCover(enemyHitTarget, targetColumn, targetRow, distanceTiles);
                return enemiesHit > 0 ? new FireResult(true, distanceTiles) : FireResult.HIT_WALL;
            }
            if (enemyHitTarget != null) {
                Object hitEnemy = enemyHitTarget.enemyAt(targetColumn, targetRow);
                if (hitEnemy != null) {
                    int damageThisHit = damageAtDistance(distanceTiles);
                    setLastHitEnemy(hitEnemy, damageThisHit, enemyHitTarget.isAtFullHp(hitEnemy));
                    enemyHitTarget.applyDamageTo(hitEnemy, damageThisHit);
                    // W1/W2 (balance-overhaul order 3): the close-range payoff — knockback at 1 tile,
                    // stagger at <= 2 tiles. Resolved before the ability callbacks so a resolver bonus
                    // reads the post-knockback position.
                    SpreadImpact.applyCloseRangeImpact(enemyHitTarget, hitEnemy, distanceTiles,
                            facingStepColumn, facingStepRow);
                    dispatchHitCallbacks(new FireResult(false, distanceTiles));
                    clearLastHit();
                    enemiesHit++;
                    lastHitDistance = distanceTiles;
                    if (!WeaponConstants.SHOTGUN_PENETRATION && enemiesHit > extraTargets) {
                        return new FireResult(false, distanceTiles);
                    }
                }
            }
        }
        return enemiesHit > 0 ? new FireResult(false, lastHitDistance) : FireResult.MISSED;
    }

    @Override public String getNormalTexturePath() { return WeaponConstants.SHOTGUN_NORMAL_TEXTURE_PATH; }
    @Override public String getFireTexturePath()   { return WeaponConstants.SHOTGUN_FIRE_TEXTURE_PATH;   }
    @Override public String getReloadTexturePath() { return WeaponConstants.SHOTGUN_RELOAD_TEXTURE_PATH; }
}
