package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.WeaponConstants;

/**
 * Short-range cone flamethrower that sprays a widening fan of fire directly ahead.
 *
 * Stats: impact damage 8 (5 at depth 3), clipSize 30 fuel, reloadTime 1 tick,
 * dropCoeff 0.0 (depth falloff handled explicitly), range 3 tiles.
 *
 * Each spray consumes FUEL_PER_SHOT (3) fuel units. canFire() therefore requires
 * at least FUEL_PER_SHOT fuel and NORMAL state — not just > 0 as with standard weapons.
 *
 * marchShot() fires a 7-tile widening cone defined by FLAME_CONE_OFFSETS:
 *   Depth 1 (center only):              tile directly ahead.
 *   Depth 2 (three wide):  center + left and right perpendicular neighbours.
 *   Depth 3 (three wide):  center + left and right perpendicular neighbours.
 * Each lateral ray is blocked independently by the first wall it encounters.
 * Fire passes through enemies (full-cone damage; no early termination on hit).
 * Explosive barrels in the cone are detonated and their ray is terminated.
 *
 * Fuel consumption: Weapon.fire() (final) decrements shotsInClip by 1. marchShot()
 * deducts the remaining (FUEL_PER_SHOT - 1) units immediately so the total consumed
 * per spray is exactly FUEL_PER_SHOT.
 *
 * Damage table (explicit depth falloff, no drop coefficient):
 *   depth 1: FLAME_IMPACT_DAMAGE = 8
 *   depth 2: FLAME_IMPACT_DAMAGE = 8
 *   depth 3: FLAME_FALLOFF       = 5   (edge of range, reduced)
 *
 * Burn DoT (FLAME_BURN_DAMAGE_PER_TURN, FLAME_BURN_TURNS) is reserved for
 * the enemy system; wire in applyBurn when enemies carry a burnTurnsRemaining field.
 */
public class Incinerator extends Weapon {

    /** Pre-allocated buffer for hudAmmoString() — avoids per-call allocation. */
    private final StringBuilder hudStringBuilder = new StringBuilder(20);

    public Incinerator() {
        super("INCINERATOR",
              WeaponConstants.FLAME_IMPACT_DAMAGE,
              WeaponConstants.FLAME_CLIP_SIZE,
              WeaponConstants.FLAME_RELOAD_TICKS,
              WeaponConstants.FLAME_DAMAGE_DROP_COEFF,
              WeaponConstants.FLAME_RANGE_TILES,
              AmmoType.CELLS);
        setBaseAccuracy(WeaponConstants.INCINERATOR_BASE_ACCURACY);
    }

    @Override public boolean isMelee()    { return false; }
    @Override public ItemType getItemType() { return ItemType.WEAPON_INCINERATOR; }

    /**
     * The incinerator requires at least FUEL_PER_SHOT fuel to fire (not just > 0).
     * Standard Weapon.canFire() only checks shotsInClip > 0; we need >= FUEL_PER_SHOT.
     */
    @Override
    public boolean canFire() {
        return visualState == WeaponVisualState.NORMAL
               && shotsInClip >= WeaponConstants.FUEL_PER_SHOT;
    }

    /**
     * Marches a widening cone fan (up to 7 tiles) defined by FLAME_CONE_OFFSETS.
     *
     * Fuel deduction: Weapon.fire() already decremented shotsInClip by 1 before calling
     * here. We deduct the remaining (FUEL_PER_SHOT - 1) now so the total is FUEL_PER_SHOT.
     *
     * Algorithm:
     *   For each lateral offset in {-1, 0, +1}:
     *     Walk distanceTiles from 1..range along the facing + perpendicular axes.
     *     Skip tiles not in the cone shape (lateral +-1 are blocked at distanceTiles < 2).
     *     Stop the ray at the first wall or closed door on this lateral ray.
     *     Apply FLAME_IMPACT_DAMAGE (or FLAME_FALLOFF at max depth) to each enemy hit.
     *     Detonate explosive barrels and stop the ray.
     *     Fire passes through enemies (no early return on enemy hit).
     *   Return FireResult.MISSED — cone has no single stop tile.
     *
     * The perpendicular axis is computed as a 90-degree CCW rotation of the facing vector:
     *   perpColumn = -facingStepRow;  perpRow = facingStepColumn
     * This maps (1,0)→(0,1), (0,1)→(-1,0), (-1,0)→(0,-1), (0,-1)→(1,0).
     */
    @Override
    protected FireResult marchShot(int playerTileColumn, int playerTileRow,
                                   int facingStepColumn, int facingStepRow,
                                   Level level, EnemyHitTarget enemyHitTarget,
                                   BarrelHitTarget barrelHitTarget, DoorBlocksQuery doorBlocksQuery) {
        // Deduct the extra fuel units here; Weapon.fire() already consumed 1.
        shotsInClip -= (WeaponConstants.FUEL_PER_SHOT - 1);

        // Perpendicular axis (90-degree CCW rotation of the facing cardinal direction).
        int perpColumn = -facingStepRow;
        int perpRow    =  facingStepColumn;

        for (int lateralOffset = -1; lateralOffset <= 1; lateralOffset++) {
            for (int distanceTiles = 1; distanceTiles <= range; distanceTiles++) {
                // Gate: lateral +-1 rays only exist at depth 2 and beyond.
                if (lateralOffset != 0 && distanceTiles < 2) {
                    continue;
                }

                int targetColumn = playerTileColumn
                        + facingStepColumn * distanceTiles
                        + perpColumn       * lateralOffset;
                int targetRow    = playerTileRow
                        + facingStepRow    * distanceTiles
                        + perpRow          * lateralOffset;

                char targetCell = level.getCell(targetColumn, targetRow);

                if (Level.isWall(targetCell)) {
                    break; // wall blocks this lateral ray; continue to next lateralOffset
                }

                if (Level.isDoor(targetCell)
                        && doorBlocksQuery != null
                        && doorBlocksQuery.blocksShotAt(targetColumn, targetRow)) {
                    break; // closed door blocks this lateral ray
                }

                if (barrelHitTarget != null
                        && barrelHitTarget.isExplosiveBarrel(targetColumn, targetRow)) {
                    barrelHitTarget.onExplosiveBarrelHit(targetColumn, targetRow);
                    break; // barrel detonated; stop this lateral ray
                }

                if (enemyHitTarget != null) {
                    Object hitEnemy = enemyHitTarget.enemyAt(targetColumn, targetRow);
                    if (hitEnemy != null) {
                        int impactDamage = (distanceTiles >= range)
                                ? WeaponConstants.FLAME_FALLOFF
                                : WeaponConstants.FLAME_IMPACT_DAMAGE;
                        enemyHitTarget.applyDamageTo(hitEnemy, impactDamage);
                        // Fire passes through enemies — do NOT break or return here.
                    }
                }
            }
        }

        // Cone has no single stop tile; always report MISSED so callers handle it correctly.
        return FireResult.MISSED;
    }

    /**
     * Ammo display: "FUEL 21/30" while ready; "RELOAD" while reloading.
     * Uses a pre-allocated StringBuilder to avoid per-call allocation.
     */
    @Override
    public String hudAmmoString() {
        if (visualState == WeaponVisualState.RELOADING) return "RELOAD";
        hudStringBuilder.setLength(0);
        hudStringBuilder.append("FUEL ");
        hudStringBuilder.append(shotsInClip);
        hudStringBuilder.append('/');
        hudStringBuilder.append(getEffectiveClipSize());
        return hudStringBuilder.toString();
    }

    @Override public String getNormalTexturePath() { return WeaponConstants.FLAME_NORMAL_TEXTURE_PATH;  }
    @Override public String getFireTexturePath()   { return WeaponConstants.FLAME_FIRE_TEXTURE_PATH;    }
    @Override public String getReloadTexturePath() { return WeaponConstants.FLAME_RELOAD_TEXTURE_PATH;  }
}
