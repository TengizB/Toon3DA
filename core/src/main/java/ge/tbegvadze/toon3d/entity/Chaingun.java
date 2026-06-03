package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.Constants;

/**
 * Triple-barrel rotary chaingun — burst fire sustained weapon filling the medium-range damage niche.
 *
 * Each fire press launches CHAINGUN_BURST_SIZE (3) bullets simultaneously, consuming 3 shots from
 * the clip. The clip holds 24 rounds (8 bursts). canFire() requires at least BURST_SIZE shots so
 * the weapon never fires a partial burst.
 *
 * Stats: damage 10, clipSize 24, burstSize 3, reloadTime 3 ticks, dropCoeff 0.10, range 8 tiles.
 * Each bullet in a burst marches the facing direction independently — all three share the same
 * target line, so the burst hits as if it were one powerful shot rather than a spread pattern.
 * No penetration: the first enemy or obstacle stops all bullets that reach it.
 *
 * Damage per bullet (coefficient 0.10, floor 0.15):
 *   distance 1: 10 × 0.90 = 9    distance 2: 10 × 0.80 = 8
 *   distance 4: 10 × 0.60 = 6    distance 6: 10 × 0.40 = 4
 *   distance 8: 10 × 0.20 = 2    (floor applies at extreme range)
 * Total burst damage = 3× per-bullet damage at each distance.
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

    /** Requires a full burst worth of ammo so the weapon never fires a partial volley. */
    @Override
    public boolean canFire() {
        return visualState == WeaponVisualState.NORMAL && shotsInClip >= Constants.CHAINGUN_BURST_SIZE;
    }

    /**
     * Fires CHAINGUN_BURST_SIZE bullets along the facing direction.
     * The first bullet's shotsInClip decrement is handled by fire() before this method runs.
     * Each additional bullet in the burst decrements shotsInClip here directly.
     */
    @Override
    protected FireResult marchShot(int playerTileColumn, int playerTileRow,
                                   int facingStepColumn, int facingStepRow,
                                   Level level, EnemyHitTarget enemyHitTarget,
                                   BarrelHitTarget barrelHitTarget, DoorBlocksQuery doorBlocksQuery) {
        FireResult lastResult = fireSingleBullet(playerTileColumn, playerTileRow,
                facingStepColumn, facingStepRow, level, enemyHitTarget, barrelHitTarget, doorBlocksQuery);
        int remainingBullets = Math.min(Constants.CHAINGUN_BURST_SIZE - 1, shotsInClip);
        for (int bulletIndex = 0; bulletIndex < remainingBullets; bulletIndex++) {
            shotsInClip--;
            lastResult = fireSingleBullet(playerTileColumn, playerTileRow,
                    facingStepColumn, facingStepRow, level, enemyHitTarget, barrelHitTarget, doorBlocksQuery);
        }
        return lastResult;
    }

    private FireResult fireSingleBullet(int playerTileColumn, int playerTileRow,
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

    /** Shows burst count rather than individual rounds, since ammo is spent in groups of 3. */
    @Override
    public String hudAmmoString() {
        if (visualState == WeaponVisualState.RELOADING) return "RELOAD";
        int burstsRemaining = shotsInClip / Constants.CHAINGUN_BURST_SIZE;
        int burstsTotal     = clipSize    / Constants.CHAINGUN_BURST_SIZE;
        return "BURSTS " + burstsRemaining + "/" + burstsTotal;
    }

    @Override public String getNormalTexturePath() { return Constants.CHAINGUN_NORMAL_TEXTURE_PATH; }
    @Override public String getFireTexturePath()   { return Constants.CHAINGUN_FIRE_TEXTURE_PATH;   }
    @Override public String getReloadTexturePath() { return Constants.CHAINGUN_RELOAD_TEXTURE_PATH; }
}
