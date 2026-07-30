package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.EffectConstants;
import ge.tbegvadze.toon3d.util.WeaponConstants;

/**
 * Four-shot plasma rifle with full line penetration.
 *
 * Stats: damage 28, clipSize 4, reloadTime 1 tick, dropCoeff 0.10, range 8.
 * Each shot costs one clip charge; after four shots the weapon reloads over
 * one tile-step. Plasma bolts pierce every enemy in the facing line —
 * unlike the shotgun which stops at the first target.
 *
 * Damage table (coefficient 0.10, floor 0.15):
 *   distance 1: 28 × 0.90 = 25   distance 4: 28 × 0.60 = 17
 *   distance 6: 28 × 0.40 = 11   distance 8: 28 × 0.20 =  6
 */
public class PlasmaRifle extends Weapon {

    public PlasmaRifle() {
        super("PLASMA RIFLE",
              WeaponConstants.PLASMA_RIFLE_DAMAGE,
              WeaponConstants.PLASMA_RIFLE_CLIP_SIZE,
              WeaponConstants.PLASMA_RIFLE_RELOAD_TIME_TICKS,
              WeaponConstants.PLASMA_RIFLE_DAMAGE_DROP_COEFF,
              WeaponConstants.PLASMA_RIFLE_RANGE_TILES,
              AmmoType.CELLS);
        setBaseAccuracy(WeaponConstants.PLASMA_RIFLE_BASE_ACCURACY);
    }

    @Override public boolean isMelee()    { return false; }
    @Override public ItemType getItemType() { return ItemType.WEAPON_PLASMA; }

    @Override
    protected FireResult marchShot(int playerTileColumn, int playerTileRow,
                                   int facingStepColumn, int facingStepRow,
                                   Level level, EnemyHitTarget enemyHitTarget,
                                   BarrelHitTarget barrelHitTarget, DoorBlocksQuery doorBlocksQuery) {
        boolean hitEnemy = false;
        // OVERPENETRATION on an already-piercing weapon adds a stacking damage bonus per enemy
        // struck beyond the first (see overpenetrationPiercingDamageMultiplier); 1.0 without it.
        int enemiesHit = 0;
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
            if (isShotBlockingCover(targetCell)) {
                // Column / solid prop is physical cover — the bolt cannot pierce through it (a crystal
                // spire on the tile takes the hit).
                hitSpireCover(enemyHitTarget, targetColumn, targetRow, distanceTiles);
                return hitEnemy ? new FireResult(true, distanceTiles) : FireResult.HIT_WALL;
            }
            if (enemyHitTarget != null) {
                Object enemy = enemyHitTarget.enemyAt(targetColumn, targetRow);
                if (enemy != null) {
                    int damageThisHit = Math.round(damageAtDistance(distanceTiles)
                            * overpenetrationPiercingDamageMultiplier(enemiesHit));
                    setLastHitEnemy(enemy, damageThisHit, enemyHitTarget.isAtFullHp(enemy));
                    enemyHitTarget.applyDamageTo(enemy, damageThisHit);
                    // Shield-shredding bolt (strategy-combat-order-6): piercing plasma leaves the target
                    // EXPOSED, so the NEXT hit ignores its Block — the answer to a turtling enemy for a
                    // build without an armor-piercing weapon. Applied after this hit so it sets up the follow-up.
                    enemyHitTarget.applyExposedStatus(enemy, EffectConstants.EXPOSED_DURATION);
                    dispatchHitCallbacks(new FireResult(false, distanceTiles));
                    clearLastHit();
                    hitEnemy = true;
                    enemiesHit++;
                    // Penetration: continue the bolt through this enemy into the next tile.
                    if (!WeaponConstants.PLASMA_RIFLE_PENETRATION) {
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
        return "PLASMA " + shotsInClip + "/" + getEffectiveClipSize();
    }

    @Override public String getNormalTexturePath() { return WeaponConstants.PLASMA_RIFLE_NORMAL_TEXTURE_PATH; }
    @Override public String getFireTexturePath()   { return WeaponConstants.PLASMA_RIFLE_FIRE_TEXTURE_PATH;   }
    @Override public String getReloadTexturePath() { return WeaponConstants.PLASMA_RIFLE_RELOAD_TEXTURE_PATH; }
}
