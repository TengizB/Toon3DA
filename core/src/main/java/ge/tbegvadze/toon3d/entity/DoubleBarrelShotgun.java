package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.WeaponConstants;

/**
 * Break-open double-barrel shotgun — the SPREAD role's heavier, shorter cousin (balance-overhaul
 * order 3, W2). Both barrels go off in ONE action (the built-in BURST_FIRE added in configureRoll), so
 * the 2-shell clip is one action; the break-open reload then costs 1 tick. No penetration — the blast
 * dissipates on the first enemy contacted.
 *
 * Falloff is the Shotgun's shape one tile shorter, BalanceConfig.DOUBLE_BARREL_FALLOFF_BY_TILE, applied
 * per barrel to the ladder-scaled per-barrel damage (~0.9x the Shotgun's, so the action is ~1.8x).
 * Each barrel's hit carries the same knockback / stagger as the Shotgun (SpreadImpact); a second
 * stagger in the same action is a no-op. The numbers are SECTION 22 data — never restate them here.
 */
public class DoubleBarrelShotgun extends Weapon {

    public DoubleBarrelShotgun() {
        super(WeaponConstants.DBL_SHOTGUN_DISPLAY_NAME,
              WeaponConstants.DBL_SHOTGUN_DAMAGE,
              WeaponConstants.DBL_SHOTGUN_CLIP_SIZE,
              WeaponConstants.DBL_SHOTGUN_RELOAD_TIME_TICKS,
              WeaponConstants.TABLE_FALLOFF_DROP_COEFFICIENT,
              WeaponConstants.DBL_SHOTGUN_RANGE_TILES,
              AmmoType.SHELLS);
        setBaseAccuracy(WeaponConstants.DOUBLE_BARREL_SHOTGUN_BASE_ACCURACY);
    }

    @Override public boolean isMelee()    { return false; }
    @Override public ItemType getItemType() { return ItemType.WEAPON_DOUBLE_BARREL; }
    @Override public DamageClass damageClass() { return DamageClass.SPREAD; }

    @Override
    public void configureRoll(int level, WeaponTier weaponTier, AbilityInstance[] weaponAbilities) {
        boolean hasBurstFire = false;
        for (AbilityInstance instance : weaponAbilities) {
            if (instance.ability == WeaponAbility.BURST_FIRE) {
                hasBurstFire = true;
                break;
            }
        }
        if (!hasBurstFire) {
            AbilityInstance[] extended = new AbilityInstance[weaponAbilities.length + 1];
            System.arraycopy(weaponAbilities, 0, extended, 0, weaponAbilities.length);
            extended[weaponAbilities.length] = new AbilityInstance(WeaponAbility.BURST_FIRE, 2f, 2);
            weaponAbilities = extended;
        }
        super.configureRoll(level, weaponTier, weaponAbilities);
    }

    /**
     * Per-tile falloff TABLE (balance-overhaul order 3, W2) instead of the base drop coefficient; the
     * ladder and fire-cycle terms are the shared {@link #damageWithFalloff} composition.
     */
    @Override
    public int damageAtDistance(int distanceTiles) {
        return damageWithFalloff(GameMath.shotgunFalloffAtTile(WeaponConstants.DBL_SHOTGUN_FALLOFF_BY_TILE, distanceTiles));
    }

    @Override
    protected FireResult marchShot(int playerTileColumn, int playerTileRow,
                                   int facingStepColumn, int facingStepRow,
                                   Level level, EnemyHitTarget enemyHitTarget,
                                   BarrelHitTarget barrelHitTarget, DoorBlocksQuery doorBlocksQuery) {
        // OVERPENETRATION: the blast pierces up to overpenetrationExtraTargets() enemies beyond the
        // first before it dissipates. Without the ability extraTargets is 0 and the blast stops on
        // the first enemy exactly as before.
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
                // column / solid prop blocks the shot (a crystal spire on the tile takes the hit — a
                // blast weapon clears spires and golem together)
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
                    if (!WeaponConstants.DBL_SHOTGUN_PENETRATION && enemiesHit > extraTargets) {
                        return new FireResult(false, distanceTiles);
                    }
                }
            }
        }
        return enemiesHit > 0 ? new FireResult(false, lastHitDistance) : FireResult.MISSED;
    }

    @Override public String getNormalTexturePath() { return WeaponConstants.DBL_SHOTGUN_NORMAL_TEXTURE_PATH; }
    @Override public String getFireTexturePath()   { return WeaponConstants.DBL_SHOTGUN_FIRE_TEXTURE_PATH;   }
    @Override public String getReloadTexturePath() { return WeaponConstants.DBL_SHOTGUN_RELOAD_TEXTURE_PATH; }
}
