package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.Constants;

/**
 * Single-barrel break-action grenade launcher with indirect-fire and AoE splash.
 *
 * Stats: center damage 30, falloff damage 16, clipSize 3, reloadTime 2 ticks,
 * dropCoeff 0.0 (no travel falloff — splash damage is constant), range 6 tiles.
 *
 * The grenade launcher does NOT use the standard linear pierce loop. It walks a path
 * one tile at a time, handles ONE 90-degree CW bounce off the first eligible wall
 * (requires traveledTiles >= GRENADE_ARM_TILES), then detonates in a plus-shaped
 * splash covering the impact tile and its 4 orthogonal neighbours.
 *
 * Bounce rule: on hitting a wall after arming, the direction turns 90 degrees CW:
 *   (directionColumn, directionRow) → (directionRow, -directionColumn)
 * If the bounced tile is also a wall, the grenade detonates at the current position.
 * A second wall contact after bouncing always detonates.
 *
 * Arming distance: GRENADE_ARM_TILES (2). An unarmed grenade passes through enemies
 * harmlessly and detonates against a wall without bouncing (the turn is consumed and
 * the grenade expended — it punishes the misplay but no self-damage applies).
 *
 * Plus splash: impact tile gets GRENADE_SPLASH_DAMAGE (30);
 * the 4 orthogonal neighbours each get GRENADE_FALLOFF_DAMAGE (16);
 * wall tiles are skipped (blasts don't penetrate walls).
 *
 * Damage table (coefficient 0.0 — travel distance has no effect):
 *   impact tile:              30 (GRENADE_SPLASH_DAMAGE)
 *   orthogonal neighbours:   16 each (GRENADE_FALLOFF_DAMAGE)
 *   per-shot AoE ceiling:    30 + 4 × 16 = 94 distributed across 5 enemies
 */
public class GrenadeLauncher extends Weapon {

    /** Pre-allocated buffer for hudAmmoString() — avoids per-call allocation. */
    private final StringBuilder hudStringBuilder = new StringBuilder(20);

    public GrenadeLauncher() {
        super("GRENADE LAUNCHER",
              Constants.GRENADE_SPLASH_DAMAGE,
              Constants.GRENADE_CLIP_SIZE,
              Constants.GRENADE_RELOAD_TIME_TICKS,
              Constants.GRENADE_DAMAGE_DROP_COEFF,
              Constants.GRENADE_RANGE_TILES,
              AmmoType.ROCKETS);
    }

    /**
     * Marches the grenade forward, handles a single 90-degree CW bounce at the
     * first eligible wall (armed only), then applies a plus-shaped splash at the
     * impact tile.
     *
     * Algorithm:
     *   Start at (playerTileColumn, playerTileRow); advance step-by-step.
     *   At each step, peek the next tile before moving:
     *     - If next tile is blocked (wall or closed door):
     *         If armed (traveledTiles >= GRENADE_ARM_TILES) and not yet bounced:
     *           Try a 90-degree CW turn. If the turned next tile is open, redirect and continue.
     *           Otherwise detonate at current position.
     *         Else detonate at current position (unarmed hit or second wall after bounce).
     *     - If next tile is open: advance to it.
     *         If a barrel is there: detonate barrel and exit.
     *         If an armed enemy is there: detonate and exit.
     *   After the loop (air-burst at max range): apply splash at the last reached tile.
     */
    @Override
    protected FireResult marchShot(int playerTileColumn, int playerTileRow,
                                   int facingStepColumn, int facingStepRow,
                                   Level level, EnemyHitTarget enemyHitTarget,
                                   BarrelHitTarget barrelHitTarget,
                                   DoorBlocksQuery doorBlocksQuery) {
        int currentColumn   = playerTileColumn;
        int currentRow      = playerTileRow;
        int directionColumn = facingStepColumn;
        int directionRow    = facingStepRow;
        boolean hasBounced  = false;

        for (int traveledTiles = 1; traveledTiles <= range; traveledTiles++) {
            int  nextColumn = currentColumn + directionColumn;
            int  nextRow    = currentRow    + directionRow;
            char nextCell   = level.getCell(nextColumn, nextRow);

            boolean nextBlocked = Level.isWall(nextCell)
                    || (Level.isDoor(nextCell)
                        && doorBlocksQuery != null
                        && doorBlocksQuery.blocksShotAt(nextColumn, nextRow));

            if (nextBlocked) {
                if (!hasBounced && traveledTiles >= Constants.GRENADE_ARM_TILES) {
                    // Attempt a 90-degree CW bounce: (dc, dr) → (dr, -dc)
                    int bouncedDirectionColumn = directionRow;
                    int bouncedDirectionRow    = -directionColumn;
                    int bouncedNextColumn      = currentColumn + bouncedDirectionColumn;
                    int bouncedNextRow         = currentRow    + bouncedDirectionRow;
                    char bouncedCell = level.getCell(bouncedNextColumn, bouncedNextRow);
                    if (!Level.isWall(bouncedCell)
                            && !(Level.isDoor(bouncedCell)
                                 && doorBlocksQuery != null
                                 && doorBlocksQuery.blocksShotAt(bouncedNextColumn, bouncedNextRow))) {
                        // Bounce succeeded — redirect and do NOT advance the tile counter.
                        directionColumn = bouncedDirectionColumn;
                        directionRow    = bouncedDirectionRow;
                        hasBounced      = true;
                        // Re-evaluate step in the new direction at the same traveledTiles count.
                        continue;
                    }
                }
                // Cannot bounce (unarmed, already bounced, or bounce also blocked): detonate here.
                applyPlusSplash(currentColumn, currentRow, level, enemyHitTarget);
                return new FireResult(true, traveledTiles);
            }

            // Next tile is open — advance to it.
            currentColumn = nextColumn;
            currentRow    = nextRow;

            // Check for an explosive barrel at the new tile.
            if (barrelHitTarget != null
                    && barrelHitTarget.isExplosiveBarrel(currentColumn, currentRow)) {
                barrelHitTarget.onExplosiveBarrelHit(currentColumn, currentRow);
                applyPlusSplash(currentColumn, currentRow, level, enemyHitTarget);
                return new FireResult(true, traveledTiles);
            }

            // Check for an armed enemy at the new tile (only when grenade is armed).
            if (traveledTiles >= Constants.GRENADE_ARM_TILES && enemyHitTarget != null) {
                Object hitEnemy = enemyHitTarget.enemyAt(currentColumn, currentRow);
                if (hitEnemy != null) {
                    applyPlusSplash(currentColumn, currentRow, level, enemyHitTarget);
                    return new FireResult(true, traveledTiles);
                }
            }
        }

        // Air-burst: grenade reached maximum range without hitting anything.
        applyPlusSplash(currentColumn, currentRow, level, enemyHitTarget);
        return new FireResult(true, range);
    }

    /**
     * Applies a plus-shaped splash at (impactColumn, impactRow).
     *
     * Iterates the 5-tile plus using Constants.GRENADE_SPLASH_OFFSETS:
     *   index 0: {0,0}  — impact tile    → GRENADE_SPLASH_DAMAGE
     *   index 1–4: {±1,0},{0,±1} — neighbours → GRENADE_FALLOFF_DAMAGE
     * Wall tiles in the splash are skipped (blasts do not penetrate walls).
     * No new int[] allocations — offsets are read directly from the constant table.
     */
    private void applyPlusSplash(int impactColumn, int impactRow,
                                  Level level, EnemyHitTarget enemyHitTarget) {
        for (int offsetIndex = 0; offsetIndex < Constants.GRENADE_SPLASH_OFFSETS.length; offsetIndex++) {
            int splashColumn = impactColumn + Constants.GRENADE_SPLASH_OFFSETS[offsetIndex][0];
            int splashRow    = impactRow    + Constants.GRENADE_SPLASH_OFFSETS[offsetIndex][1];
            char splashCell  = level.getCell(splashColumn, splashRow);
            if (Level.isWall(splashCell)) {
                continue; // walls absorb the blast; no damage beyond them
            }
            if (enemyHitTarget != null) {
                Object splashEnemy = enemyHitTarget.enemyAt(splashColumn, splashRow);
                if (splashEnemy != null) {
                    int splashDamage = (offsetIndex == 0)
                            ? Constants.GRENADE_SPLASH_DAMAGE
                            : Constants.GRENADE_FALLOFF_DAMAGE;
                    enemyHitTarget.applyDamageTo(splashEnemy, splashDamage);
                }
            }
        }
    }

    /**
     * Ammo display: "GRENADES N/3" while ready; "RELOAD" while reloading.
     * Uses a pre-allocated StringBuilder to avoid per-call allocation.
     */
    @Override
    public String hudAmmoString() {
        if (visualState == WeaponVisualState.RELOADING) return "RELOAD";
        hudStringBuilder.setLength(0);
        hudStringBuilder.append("GRENADES ");
        hudStringBuilder.append(shotsInClip);
        hudStringBuilder.append('/');
        hudStringBuilder.append(clipSize);
        return hudStringBuilder.toString();
    }

    @Override public String getNormalTexturePath() { return Constants.GRENADE_NORMAL_TEXTURE_PATH;  }
    @Override public String getFireTexturePath()   { return Constants.GRENADE_FIRE_TEXTURE_PATH;    }
    @Override public String getReloadTexturePath() { return Constants.GRENADE_RELOAD_TEXTURE_PATH;  }
}
