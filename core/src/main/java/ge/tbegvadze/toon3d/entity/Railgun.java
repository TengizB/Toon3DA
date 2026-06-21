package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.WeaponConstants;

/**
 * Charge-up infinite-pierce hitscan sniper — the longest-range weapon in the game.
 *
 * Stats: nominal damage 90 (full charge), clipSize 1, reloadTime 2 ticks,
 *        dropCoeff 0.02, range 16 tiles. Full pierce — slug passes through every
 *        enemy in the line and stops only at a wall or closed door.
 *
 * Charge model (turn-based, zero real-time):
 *   chargeLevel 0 → idle; press attack to begin charging (chargeLevel 1, turn consumed).
 *   chargeLevel 1 → half-charge; pressing attack again fires the slug this same turn.
 *   chargeLevel 2 → full charge (auto-fired same turn the charge reached max).
 *   Firing always resets chargeLevel to 0.
 *
 * Damage table (coefficient 0.02, floor 0.70):
 *   charge 1, distance  1: 40 × 1.00 = 40
 *   charge 1, distance 16: 40 × 0.70 = 28
 *   charge 2, distance  1: 90 × 1.00 = 90
 *   charge 2, distance 16: 90 × 0.70 = 63
 */
public class Railgun extends Weapon {

    /** Current charge level: 0 = idle, 1 = half charge, 2 = full charge. */
    private int chargeLevel = 0;

    /** Pre-allocated buffer so hudAmmoString() never allocates during gameplay. */
    private final StringBuilder hudStringBuilder = new StringBuilder(16);

    public Railgun() {
        super("RAILGUN",
              WeaponConstants.RAILGUN_DAMAGE_BY_CHARGE[WeaponConstants.RAILGUN_MAX_CHARGE],
              WeaponConstants.RAILGUN_CLIP_SIZE,
              WeaponConstants.RAILGUN_RELOAD_TIME_TICKS,
              WeaponConstants.RAILGUN_DROP_COEFF,
              WeaponConstants.RAILGUN_RANGE_TILES,
              AmmoType.SLUGS);
        setBaseAccuracy(WeaponConstants.RAILGUN_BASE_ACCURACY);
    }

    @Override public boolean isMelee()    { return false; }
    @Override public ItemType getItemType() { return ItemType.WEAPON_RAILGUN; }

    // -------------------------------------------------------------------------
    // Charge-state accessors
    // -------------------------------------------------------------------------

    /** Returns current charge level (0 = idle, 1 = half-charge, 2 = full-charge). */
    public int getChargeLevel() {
        return chargeLevel;
    }

    /** True when any charge has been built up (chargeLevel >= 1). */
    public boolean isCharging() {
        return chargeLevel >= 1;
    }

    /** True when the capacitor has reached maximum charge (ready to auto-fire). */
    public boolean isFullyCharged() {
        return chargeLevel >= WeaponConstants.RAILGUN_MAX_CHARGE;
    }

    /**
     * Advances the charge by one level, capped at RAILGUN_MAX_CHARGE.
     * Call this when the player presses attack and the weapon is not yet ready to fire
     * (chargeLevel == 0 and the player wants to begin spinning up the capacitor).
     */
    public void advanceCharge() {
        if (chargeLevel < WeaponConstants.RAILGUN_MAX_CHARGE) {
            chargeLevel++;
        }
    }

    /**
     * Resets the charge to zero without firing — used when the player swaps away
     * from the railgun while it is charged.
     */
    public void resetCharge() {
        chargeLevel = 0;
    }

    // -------------------------------------------------------------------------
    // Weapon contract
    // -------------------------------------------------------------------------

    /**
     * Marches the slug through the level: infinite pierce through enemies, stops at
     * walls and closed doors. Damage is scaled by the current chargeLevel at the
     * moment fire() was called. The chargeLevel is captured at the start and cleared
     * immediately so subsequent queries correctly read 0 (discharged).
     *
     * Conforms to the marchShot contract:
     *   - Called by fire() after state is already FIRING; does not set state itself.
     *   - All three nullable parameters (enemyHitTarget, barrelHitTarget, doorBlocksQuery)
     *     are guarded before use.
     *   - Loops distanceTiles from 1 to range, checking wall then door then barrel
     *     then enemy at each step.
     */
    @Override
    protected FireResult marchShot(int playerTileColumn, int playerTileRow,
                                   int facingStepColumn, int facingStepRow,
                                   Level level, EnemyHitTarget enemyHitTarget,
                                   BarrelHitTarget barrelHitTarget, DoorBlocksQuery doorBlocksQuery) {
        // Capture the charge level that was active when fire() was called, then
        // immediately discharge the capacitor so the weapon reads as idle after firing.
        int firedChargeLevel = chargeLevel;
        chargeLevel = 0;

        boolean hitAnyEnemy = false;

        for (int distanceTiles = 1; distanceTiles <= range; distanceTiles++) {
            int  targetColumn = playerTileColumn + facingStepColumn * distanceTiles;
            int  targetRow    = playerTileRow    + facingStepRow    * distanceTiles;
            char targetCell   = level.getCell(targetColumn, targetRow);

            if (Level.isWall(targetCell)) {
                return hitAnyEnemy ? new FireResult(true, distanceTiles) : FireResult.HIT_WALL;
            }
            if (Level.isDoor(targetCell)
                    && doorBlocksQuery != null && doorBlocksQuery.blocksShotAt(targetColumn, targetRow)) {
                return hitAnyEnemy ? new FireResult(true, distanceTiles) : FireResult.HIT_WALL;
            }
            if (barrelHitTarget != null && barrelHitTarget.isExplosiveBarrel(targetColumn, targetRow)) {
                barrelHitTarget.onExplosiveBarrelHit(targetColumn, targetRow);
                return hitAnyEnemy ? new FireResult(true, distanceTiles) : FireResult.HIT_WALL;
            }
            if (enemyHitTarget != null) {
                Object hitEnemy = enemyHitTarget.enemyAt(targetColumn, targetRow);
                if (hitEnemy != null) {
                    float baseForCharge = WeaponConstants.RAILGUN_DAMAGE_BY_CHARGE[firedChargeLevel];
                    int computedDamage = Math.round(baseForCharge
                            * GameMath.railgunFalloff(distanceTiles,
                                    WeaponConstants.RAILGUN_DROP_COEFF,
                                    WeaponConstants.RAILGUN_DAMAGE_MIN_MULTIPLIER));
                    boolean targetWasFullHp = enemyHitTarget.isAtFullHp(hitEnemy);
                    setLastHitEnemy(hitEnemy, computedDamage, targetWasFullHp);
                    enemyHitTarget.applyDamageTo(hitEnemy, computedDamage);
                    dispatchHitCallbacks(new FireResult(false, distanceTiles));
                    clearLastHit();
                    hitAnyEnemy = true;
                    // Infinite pierce: do NOT return — continue marching through this enemy.
                }
            }
        }
        return hitAnyEnemy ? new FireResult(false, range) : FireResult.MISSED;
    }

    // -------------------------------------------------------------------------
    // HUD string
    // -------------------------------------------------------------------------

    /**
     * Short ammo string for the right HUD panel.
     *   RELOADING  →  "RELOAD"
     *   chargeLevel 2  →  "RAIL >>"
     *   chargeLevel 1  →  "RAIL >"
     *   idle           →  "SLUGS <n>/1"
     *
     * Uses a pre-allocated StringBuilder — no allocation during gameplay.
     */
    @Override
    public String hudAmmoString() {
        if (visualState == WeaponVisualState.RELOADING) return "RELOAD";
        if (chargeLevel == WeaponConstants.RAILGUN_MAX_CHARGE) return "RAIL >>";
        if (chargeLevel == 1) return "RAIL >";
        hudStringBuilder.setLength(0);
        hudStringBuilder.append("SLUGS ");
        hudStringBuilder.append(shotsInClip);
        hudStringBuilder.append('/');
        hudStringBuilder.append(getEffectiveClipSize());
        return hudStringBuilder.toString();
    }

    // -------------------------------------------------------------------------
    // Texture path stubs (procedural sprite generated in WeaponHudRenderer)
    // -------------------------------------------------------------------------

    @Override public String getNormalTexturePath() { return WeaponConstants.RAILGUN_NORMAL_TEXTURE_PATH; }
    @Override public String getFireTexturePath()   { return WeaponConstants.RAILGUN_FIRE_TEXTURE_PATH;   }
    @Override public String getReloadTexturePath() { return WeaponConstants.RAILGUN_RELOAD_TEXTURE_PATH; }
}
