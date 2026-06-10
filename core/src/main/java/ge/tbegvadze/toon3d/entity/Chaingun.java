package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.Constants;

/**
 * Triple-barrel rotary chaingun — sequential burst-fire weapon for medium-range sustained damage.
 *
 * Firing mechanic:
 *   Each press of fire launches CHAINGUN_BURST_SIZE (3) bullets, one per flash cycle.
 *   Bullet 1 fires immediately when fire() is called.  Bullets 2 and 3 are queued and
 *   dispatched by update() once the previous flash animation expires — each resets the flash
 *   timer so the muzzle-flash plays a separate cycle for every shot.
 *
 *   Per-bullet effects (each cycle):
 *     • shotsInClip decrements by 1  → HUD large-digit ticks down once per bullet
 *     • fireSingleBullet() applies damage  → enemy health bar updates after each hit
 *     • spawnEventText("BURST FIRE")  → event text entry for each round fired
 *
 *   canFire() requires shotsInClip >= BURST_SIZE so no partial burst is ever started.
 *   The clip holds 24 rounds (8 full bursts).
 *
 * Stats: damage 10, clipSize 24, burstSize 3, reloadTime 3 ticks, dropCoeff 0.10, range 8 tiles.
 * No penetration: the first enemy or obstacle stops the bullet.
 *
 * Damage per bullet (coefficient 0.10, floor 0.15):
 *   distance 1: 10 × 0.90 = 9    distance 2: 10 × 0.80 = 8
 *   distance 4: 10 × 0.60 = 6    distance 6: 10 × 0.40 = 4
 *   distance 8: 10 × 0.20 = 2    (floor applies at extreme range)
 * Total burst damage = 3× per-bullet damage at each distance (3 separate hits).
 */
public class Chaingun extends Weapon {

    // Number of burst bullets still waiting to fire after the current flash cycle ends.
    private int pendingBurstBullets = 0;

    // Cached fire parameters forwarded to pending bullets in update().
    private int            burstPlayerColumn;
    private int            burstPlayerRow;
    private int            burstFacingColumn;
    private int            burstFacingRow;
    private Level          burstLevel;
    private EnemyHitTarget   burstEnemyHitTarget;
    private BarrelHitTarget  burstBarrelHitTarget;
    private DoorBlocksQuery  burstDoorBlocksQuery;

    public Chaingun() {
        super(Constants.CHAINGUN_DISPLAY_NAME,
              Constants.CHAINGUN_DAMAGE,
              Constants.CHAINGUN_CLIP_SIZE,
              Constants.CHAINGUN_RELOAD_TIME_TICKS,
              Constants.CHAINGUN_DAMAGE_DROP_COEFF,
              Constants.CHAINGUN_RANGE_TILES,
              AmmoType.BULLETS);
    }

    /** Requires a full burst worth of ammo so the weapon never fires a partial volley. */
    @Override
    public boolean canFire() {
        return visualState == WeaponVisualState.NORMAL && shotsInClip >= Constants.CHAINGUN_BURST_SIZE;
    }

    /**
     * Fires bullet 1 immediately and queues the remaining BURST_SIZE-1 bullets.
     * Queued bullets are dispatched one-per-flash-cycle by update().
     * shotsInClip for bullet 1 was already decremented by fire() before this runs.
     */
    @Override
    protected FireResult marchShot(int playerTileColumn, int playerTileRow,
                                   int facingStepColumn, int facingStepRow,
                                   Level level, EnemyHitTarget enemyHitTarget,
                                   BarrelHitTarget barrelHitTarget, DoorBlocksQuery doorBlocksQuery) {
        // shotsInClip was already decremented by fire() for bullet 1; guard so pending
        // bullets never exceed what remains in the clip (defensive; canFire() normally
        // ensures shotsInClip >= BURST_SIZE before we get here).
        pendingBurstBullets  = Math.min(Constants.CHAINGUN_BURST_SIZE - 1, shotsInClip);
        burstPlayerColumn    = playerTileColumn;
        burstPlayerRow       = playerTileRow;
        burstFacingColumn    = facingStepColumn;
        burstFacingRow       = facingStepRow;
        burstLevel           = level;
        burstEnemyHitTarget  = enemyHitTarget;
        burstBarrelHitTarget = barrelHitTarget;
        burstDoorBlocksQuery = doorBlocksQuery;

        FireResult result = fireSingleBullet(playerTileColumn, playerTileRow,
                facingStepColumn, facingStepRow, level, enemyHitTarget, barrelHitTarget, doorBlocksQuery);
        spawnEventText("BURST FIRE");
        return result;
    }

    /**
     * Extends the base update to dispatch queued burst bullets one per flash cycle.
     *
     * While pendingBurstBullets > 0 and the weapon is FIRING:
     *   • Manages the flash timer directly (does NOT call super) so the base class
     *     cannot transition FIRING → NORMAL before all burst shots are complete.
     *   • When the flash timer expires: fire next bullet, decrement shotsInClip,
     *     spawn event text, reset flash timer for the next cycle.
     *
     * Once pendingBurstBullets reaches 0 the next frame delegates to super.update(),
     * which handles the final FIRING → NORMAL transition normally.
     */
    @Override
    public void update(float deltaTime) {
        if (visualState == WeaponVisualState.FIRING && pendingBurstBullets > 0) {
            fireFlashTimerSeconds -= deltaTime;
            if (fireFlashTimerSeconds <= 0f) {
                pendingBurstBullets--;
                shotsInClip--;
                flashCycleCount++;
                fireSingleBullet(burstPlayerColumn, burstPlayerRow,
                        burstFacingColumn, burstFacingRow,
                        burstLevel, burstEnemyHitTarget, burstBarrelHitTarget, burstDoorBlocksQuery);
                spawnEventText("BURST FIRE");
                fireFlashTimerSeconds = Constants.FIRE_FLASH_DURATION;
            }
            // Do not call super — the base class must not transition FIRING→NORMAL yet.
        } else {
            super.update(deltaTime);
        }
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

    /**
     * Shows individual round count so the HUD text also reflects each per-bullet decrement
     * during the burst animation (24 → 23 → 22 → 21 per burst).
     */
    @Override
    public String hudAmmoString() {
        if (visualState == WeaponVisualState.RELOADING) return "RELOAD";
        return "ROUNDS " + shotsInClip + "/" + clipSize;
    }

    @Override public String getNormalTexturePath() { return Constants.CHAINGUN_NORMAL_TEXTURE_PATH; }
    @Override public String getFireTexturePath()   { return Constants.CHAINGUN_FIRE_TEXTURE_PATH;   }
    @Override public String getReloadTexturePath() { return Constants.CHAINGUN_RELOAD_TEXTURE_PATH; }
}
