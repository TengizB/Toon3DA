package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.WeaponConstants;

/**
 * Six-round drum grenade launcher with contact detonation, one wall bank and a plus-shaped splash.
 *
 * Stats: center damage 42, falloff damage 22, clipSize 3, reloadTime 2 ticks,
 * dropCoeff 0.0 (no travel falloff — splash damage is constant), range 6 tiles.
 *
 * The grenade launcher does NOT use the standard linear pierce loop. It walks a path
 * one tile at a time and detonates on the FIRST enemy it reaches — at any distance, the
 * adjacent tile included, so the weapon is never useless up close. It banks ONE 90-degree
 * CW bounce off the first eligible wall (requires traveledTiles >= GRENADE_BOUNCE_MIN_TILES),
 * then detonates in a plus-shaped splash covering the impact tile and its 4 orthogonal
 * neighbours.
 *
 * Bounce rule: on hitting a wall after the minimum travel, the direction turns 90 degrees CW:
 *   (directionColumn, directionRow) → (directionRow, -directionColumn)
 * If the bounced tile is also a wall, the grenade detonates at the current position.
 * A second wall contact after bouncing always detonates.
 *
 * SELF-DAMAGE — the point-blank price. The player is hurt by their own grenade ONLY when it
 * detonates on an enemy standing on the very next cell (distance 1): the player's own tile is
 * then an orthogonal neighbour of the impact tile. Any other detonation — farther enemies, walls,
 * air-bursts, barrels, the banked shot — never hurts the player. The amount
 * (GRENADE_SELF_DAMAGE) is parked in {@link #consumePendingSelfDamage()} for PlayerController to
 * apply through the normal Player damage pipeline, which keeps this class free of Player state.
 *
 * Plus splash: impact tile gets GRENADE_SPLASH_DAMAGE (42);
 * the 4 orthogonal neighbours each get GRENADE_FALLOFF_DAMAGE (22);
 * wall tiles are skipped (blasts don't penetrate walls).
 *
 * Damage table (coefficient 0.0 — travel distance has no effect):
 *   impact tile:              42 (GRENADE_SPLASH_DAMAGE)
 *   orthogonal neighbours:   22 each (GRENADE_FALLOFF_DAMAGE)
 *   player (adjacent target): 24 (GRENADE_SELF_DAMAGE)
 *   per-shot AoE ceiling:    42 + 4 × 22 = 130 distributed across 5 enemies
 */
public class GrenadeLauncher extends Weapon {

    /** Pre-allocated buffer for hudAmmoString() — avoids per-call allocation. */
    private final StringBuilder hudStringBuilder = new StringBuilder(20);

    /** Self-damage owed by the last shot (0 when the player was not caught). Drained by PlayerController. */
    private int pendingSelfDamage = 0;

    public GrenadeLauncher() {
        super("GRENADE LAUNCHER",
              WeaponConstants.GRENADE_SPLASH_DAMAGE,
              WeaponConstants.GRENADE_CLIP_SIZE,
              WeaponConstants.GRENADE_RELOAD_TIME_TICKS,
              WeaponConstants.GRENADE_DAMAGE_DROP_COEFF,
              WeaponConstants.GRENADE_RANGE_TILES,
              AmmoType.ROCKETS);
        setBaseAccuracy(WeaponConstants.GRENADE_LAUNCHER_BASE_ACCURACY);
    }

    @Override public boolean isMelee()    { return false; }
    @Override public ItemType getItemType() { return ItemType.WEAPON_ROCKET; }
    @Override public DamageClass damageClass() { return DamageClass.EXPLOSIVE; }

    /**
     * Marches the grenade forward, detonates on the first enemy it reaches (any distance),
     * handles a single 90-degree CW bounce at the first eligible wall, then applies a
     * plus-shaped splash at the impact tile.
     *
     * Algorithm:
     *   Start at (playerTileColumn, playerTileRow); advance step-by-step.
     *   At each step, peek the next tile before moving:
     *     - If next tile is blocked (wall, closed door, column, or a non-barrel solid prop):
     *         If traveledTiles >= GRENADE_BOUNCE_MIN_TILES and not yet bounced:
     *           Try a 90-degree CW turn. If the turned next tile is open, redirect and continue.
     *           Otherwise detonate at current position.
     *         Else detonate at current position (point-blank wall or second wall after bounce).
     *     - If next tile is open: advance to it.
     *         If a barrel is there: detonate barrel and exit.
     *         If an enemy is there: detonate and exit. At distance 1 (before any bounce) the
     *         player is caught in the plus and owes GRENADE_SELF_DAMAGE.
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

            // Explosive barrels stay passable so the grenade lands on them and chain-detonates;
            // every other column / solid prop is physical cover that stops the grenade.
            boolean nextIsBarrel = barrelHitTarget != null
                    && barrelHitTarget.isExplosiveBarrel(nextColumn, nextRow);
            boolean nextBlocked = Level.isWall(nextCell)
                    || Level.isColumn(nextCell)
                    || (Level.isPropSolid(nextCell) && !nextIsBarrel)
                    || (Level.isDoor(nextCell)
                        && doorBlocksQuery != null
                        && doorBlocksQuery.blocksShotAt(nextColumn, nextRow));

            if (nextBlocked) {
                if (!hasBounced && traveledTiles >= WeaponConstants.GRENADE_BOUNCE_MIN_TILES) {
                    // Attempt a 90-degree CW bounce: (dc, dr) → (dr, -dc)
                    int bouncedDirectionColumn = directionRow;
                    int bouncedDirectionRow    = -directionColumn;
                    int bouncedNextColumn      = currentColumn + bouncedDirectionColumn;
                    int bouncedNextRow         = currentRow    + bouncedDirectionRow;
                    char bouncedCell = level.getCell(bouncedNextColumn, bouncedNextRow);
                    boolean bouncedIsBarrel = barrelHitTarget != null
                            && barrelHitTarget.isExplosiveBarrel(bouncedNextColumn, bouncedNextRow);
                    if (!Level.isWall(bouncedCell)
                            && !Level.isColumn(bouncedCell)
                            && !(Level.isPropSolid(bouncedCell) && !bouncedIsBarrel)
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
                // Cannot bounce (point-blank wall, already bounced, or bounce also blocked): detonate here.
                applyPlusSplash(currentColumn, currentRow, level, enemyHitTarget, barrelHitTarget);
                return new FireResult(true, traveledTiles);
            }

            // Next tile is open — advance to it.
            currentColumn = nextColumn;
            currentRow    = nextRow;

            // Check for an explosive barrel at the new tile.
            if (barrelHitTarget != null
                    && barrelHitTarget.isExplosiveBarrel(currentColumn, currentRow)) {
                barrelHitTarget.onExplosiveBarrelHit(currentColumn, currentRow);
                applyPlusSplash(currentColumn, currentRow, level, enemyHitTarget, barrelHitTarget);
                return new FireResult(true, traveledTiles);
            }

            // Contact fuse: the first enemy the grenade reaches sets it off, at ANY distance —
            // including the adjacent tile, so the launcher still works when something is in your face.
            if (enemyHitTarget != null) {
                Object hitEnemy = enemyHitTarget.enemyAt(currentColumn, currentRow);
                if (hitEnemy != null) {
                    applyPlusSplash(currentColumn, currentRow, level, enemyHitTarget, barrelHitTarget);
                    // The enemy is on the very next cell: the player's own tile is a neighbour of the
                    // impact, so the shooter eats part of the blast. Only this case ever self-damages.
                    if (!hasBounced && traveledTiles == 1) {
                        // Accumulates so a BURST_FIRE extra round that also lands point-blank is counted too.
                        pendingSelfDamage += WeaponConstants.GRENADE_SELF_DAMAGE;
                    }
                    return new FireResult(true, traveledTiles);
                }
            }
        }

        // Air-burst: grenade reached maximum range without hitting anything.
        applyPlusSplash(currentColumn, currentRow, level, enemyHitTarget, barrelHitTarget);
        return new FireResult(true, range);
    }

    /**
     * Applies a plus-shaped splash at (impactColumn, impactRow).
     *
     * Iterates the 5-tile plus using WeaponConstants.GRENADE_SPLASH_OFFSETS:
     *   index 0: {0,0}  — impact tile    → GRENADE_SPLASH_DAMAGE
     *   index 1–4: {±1,0},{0,±1} — neighbours → GRENADE_FALLOFF_DAMAGE
     * Wall, column and (non-barrel) solid-prop tiles in the splash are skipped — solid cover
     * absorbs the blast. Explosive barrels are NOT treated as cover here: the tile that holds the
     * grenade impact is detonated by the caller, and a barrel merely caught in the plus carries no
     * enemy, so letting it fall through is a no-op that keeps this consistent with the march loop.
     * No new int[] allocations — offsets are read directly from the constant table.
     */
    private void applyPlusSplash(int impactColumn, int impactRow,
                                  Level level, EnemyHitTarget enemyHitTarget,
                                  BarrelHitTarget barrelHitTarget) {
        for (int offsetIndex = 0; offsetIndex < WeaponConstants.GRENADE_SPLASH_OFFSETS.length; offsetIndex++) {
            int splashColumn = impactColumn + WeaponConstants.GRENADE_SPLASH_OFFSETS[offsetIndex][0];
            int splashRow    = impactRow    + WeaponConstants.GRENADE_SPLASH_OFFSETS[offsetIndex][1];
            char splashCell  = level.getCell(splashColumn, splashRow);
            boolean splashIsBarrel = barrelHitTarget != null
                    && barrelHitTarget.isExplosiveBarrel(splashColumn, splashRow);
            int splashDamage = Math.round(((offsetIndex == 0)
                    ? WeaponConstants.GRENADE_SPLASH_DAMAGE
                    : WeaponConstants.GRENADE_FALLOFF_DAMAGE) * getLadderDamageMultiplier());
            // A crystal spire caught in the blast takes the splash damage before it absorbs the rest — a
            // blast weapon clears spires and the golem together, which is the whole point of bringing one
            // to a Verdant Spiresower (.claude/agents/ideas/elemental-golem-verdant-spiresower.txt).
            if (enemyHitTarget != null && enemyHitTarget.isSpireAt(splashColumn, splashRow)) {
                enemyHitTarget.damageSpireAt(splashColumn, splashRow, splashDamage);
            }
            if (Level.isWall(splashCell) || Level.isColumn(splashCell)
                    || (Level.isPropSolid(splashCell) && !splashIsBarrel)) {
                continue; // walls, columns and (non-barrel) solid props absorb the blast
            }
            if (enemyHitTarget != null) {
                Object splashEnemy = enemyHitTarget.enemyAt(splashColumn, splashRow);
                if (splashEnemy != null) {
                    enemyHitTarget.applyDamageTo(splashEnemy, splashDamage);
                }
            }
        }
    }

    /**
     * Returns the self-damage the last shot owes the player and clears it, so it is applied
     * exactly once. Non-zero only when the grenade detonated on an enemy on the adjacent cell.
     */
    public int consumePendingSelfDamage() {
        int owed = pendingSelfDamage;
        pendingSelfDamage = 0;
        return owed;
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
        hudStringBuilder.append(getEffectiveClipSize());
        return hudStringBuilder.toString();
    }

    @Override public String getNormalTexturePath() { return WeaponConstants.GRENADE_NORMAL_TEXTURE_PATH;  }
    @Override public String getFireTexturePath()   { return WeaponConstants.GRENADE_FIRE_TEXTURE_PATH;    }
    @Override public String getReloadTexturePath() { return WeaponConstants.GRENADE_RELOAD_TEXTURE_PATH;  }
}
