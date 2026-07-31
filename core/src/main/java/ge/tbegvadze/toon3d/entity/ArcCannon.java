package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.WeaponConstants;

/**
 * Chain-lightning energy weapon — the anti-swarm specialist.
 *
 * The primary bolt is a standard hitscan that marches the facing line and stops at the FIRST
 * enemy it strikes (no line penetration). What makes the Arc Cannon unique is the LATERAL CHAIN:
 * once the primary target is hit, the electric arc leaps to an adjacent enemy, then to an enemy
 * adjacent to THAT one, and so on — creeping sideways through a cluster. No other weapon in the
 * arsenal chains between separated targets, so this is the crowd-control niche.
 *
 * Stats: damage 28, clipSize 7, reloadTime 2 ticks, dropCoeff 0.08, range 7 tiles, CELLS ammo.
 * No penetration — the primary bolt stops at the first enemy; the chain handles multi-target.
 *
 * Chain model (turn-based, deterministic):
 *   From the primary target's tile, up to ARC_CANNON_CHAIN_JUMPS leaps are resolved. Each leap
 *   inspects the four cardinal neighbours of the current arc tile in a fixed order (left, right,
 *   forward, back, relative to the player's facing) and jumps to the first neighbour that holds a
 *   not-yet-struck enemy and is not blocked by a wall / closed door / cover. Each jump deals
 *   ARC_CANNON_CHAIN_DAMAGE_MULTIPLIER^jumpIndex of the primary hit's damage; the chain dies as
 *   soon as no eligible neighbour is found.
 *
 * Damage table (coefficient 0.08, floor 0.15) — PRIMARY hit only (chain is bonus, uncredited):
 *   distance 1: 28 × 0.92 = 26   distance 4: 28 × 0.68 = 19
 *   distance 7: 28 × 0.44 = 12
 * Chain leaps (from a 28 primary): 28 × 0.6 = 17, × 0.36 = 10, × 0.216 = 6.
 */
public class ArcCannon extends Weapon {

    // Pre-allocated scratch buffers so marchShot() performs no per-call allocation.
    // chainDirectionColumns/Rows hold the four cardinal leap directions (rebuilt each fire from
    // the current facing); visitedColumns/Rows record every tile the arc has already struck this
    // activation so a single enemy is never zapped twice by the same chain.
    private final int[] chainDirectionColumns = new int[4];
    private final int[] chainDirectionRows    = new int[4];
    private final int[] visitedColumns        = new int[WeaponConstants.ARC_CANNON_CHAIN_JUMPS + 1];
    private final int[] visitedRows           = new int[WeaponConstants.ARC_CANNON_CHAIN_JUMPS + 1];

    public ArcCannon() {
        super("ARC CANNON",
              WeaponConstants.ARC_CANNON_DAMAGE,
              WeaponConstants.ARC_CANNON_CLIP_SIZE,
              WeaponConstants.ARC_CANNON_RELOAD_TIME_TICKS,
              WeaponConstants.ARC_CANNON_DAMAGE_DROP_COEFF,
              WeaponConstants.ARC_CANNON_RANGE_TILES,
              AmmoType.CELLS);
        setBaseAccuracy(WeaponConstants.ARC_CANNON_BASE_ACCURACY);
    }

    @Override public boolean isMelee()      { return false; }
    @Override public ItemType getItemType() { return ItemType.WEAPON_ARC_CANNON; }

    /**
     * Marches the primary bolt to the first enemy in the facing line, applies its damage, then
     * discharges the lateral chain from that enemy's tile.
     *
     * Conforms to the marchShot contract:
     *   - Called by fire() after state is already FIRING; does not set state itself.
     *   - All three nullable parameters (enemyHitTarget, barrelHitTarget, doorBlocksQuery) are
     *     guarded before use.
     *   - Loops distanceTiles from 1 to range, checking wall, then closed door, then explosive
     *     barrel, then cover, then enemy at each step.
     */
    @Override
    protected FireResult marchShot(int playerTileColumn, int playerTileRow,
                                   int facingStepColumn, int facingStepRow,
                                   Level level, EnemyHitTarget enemyHitTarget,
                                   BarrelHitTarget barrelHitTarget, DoorBlocksQuery doorBlocksQuery) {
        for (int distanceTiles = 1; distanceTiles <= range; distanceTiles++) {
            int  targetColumn = playerTileColumn + facingStepColumn * distanceTiles;
            int  targetRow    = playerTileRow    + facingStepRow    * distanceTiles;
            char targetCell   = level.getCell(targetColumn, targetRow);

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
                // Column / solid prop is physical cover — the bolt cannot pass it (a crystal spire on the
                // tile takes the hit).
                hitSpireCover(enemyHitTarget, targetColumn, targetRow, distanceTiles);
                return FireResult.HIT_WALL;
            }
            if (enemyHitTarget != null) {
                Object primaryEnemy = enemyHitTarget.enemyAt(targetColumn, targetRow);
                if (primaryEnemy != null) {
                    int primaryDamage = damageAtDistance(distanceTiles);
                    // Dispatch the primary hit's callbacks INLINE (mirroring the Railgun): the chain
                    // below calls clearLastHit(), so relying on fire()'s post-return dispatch would
                    // drop the primary target's on-hit abilities. clearLastHit() after this makes
                    // fire()'s own dispatchHitCallbacks a safe no-op.
                    setLastHitEnemy(primaryEnemy, primaryDamage, enemyHitTarget.isAtFullHp(primaryEnemy));
                    enemyHitTarget.applyDamageTo(primaryEnemy, primaryDamage);
                    dispatchHitCallbacks(new FireResult(false, distanceTiles));
                    clearLastHit();
                    // Discharge the lateral chain from this first enemy's tile (its own inline dispatch).
                    dischargeChain(targetColumn, targetRow, primaryDamage,
                                   facingStepColumn, facingStepRow,
                                   level, enemyHitTarget, doorBlocksQuery, barrelHitTarget);
                    // No penetration: the primary bolt stops at this first enemy.
                    return new FireResult(false, distanceTiles);
                }
            }
        }
        return FireResult.MISSED;
    }

    /**
     * Resolves the lateral chain from the primary target's tile. Rebuilds the four cardinal leap
     * directions (left, right, forward, back relative to the player's facing) into the pre-allocated
     * scratch buffers, then leaps up to ARC_CANNON_CHAIN_JUMPS times, each jump dealing a decaying
     * fraction of the primary damage. Enemies already struck this activation are never re-hit.
     *
     * @param primaryDamage the damage the primary bolt dealt; each chain leap decays from this.
     */
    private void dischargeChain(int primaryColumn, int primaryRow, int primaryDamage,
                                int facingStepColumn, int facingStepRow,
                                Level level, EnemyHitTarget enemyHitTarget,
                                DoorBlocksQuery doorBlocksQuery, BarrelHitTarget barrelHitTarget) {
        // Perpendicular axis (90-degree CCW rotation of the facing cardinal direction), matching
        // the Incinerator's convention: (1,0)->(0,1), (0,1)->(-1,0), etc.
        int perpendicularColumn = -facingStepRow;
        int perpendicularRow    =  facingStepColumn;
        // Leap-direction preference: left, right, then forward, then back — the arc spreads sideways
        // through a cluster before continuing away from the player.
        chainDirectionColumns[0] =  perpendicularColumn; chainDirectionRows[0] =  perpendicularRow; // left
        chainDirectionColumns[1] = -perpendicularColumn; chainDirectionRows[1] = -perpendicularRow; // right
        chainDirectionColumns[2] =  facingStepColumn;    chainDirectionRows[2] =  facingStepRow;     // forward
        chainDirectionColumns[3] = -facingStepColumn;    chainDirectionRows[3] = -facingStepRow;     // back

        int visitedCount = 0;
        visitedColumns[visitedCount] = primaryColumn;
        visitedRows[visitedCount]    = primaryRow;
        visitedCount++;

        int currentColumn = primaryColumn;
        int currentRow    = primaryRow;
        float chainDamage = primaryDamage;

        for (int jumpIndex = 1; jumpIndex <= WeaponConstants.ARC_CANNON_CHAIN_JUMPS; jumpIndex++) {
            int nextColumn = 0;
            int nextRow    = 0;
            Object nextEnemy = null;

            for (int directionIndex = 0; directionIndex < 4 && nextEnemy == null; directionIndex++) {
                int candidateColumn = currentColumn + chainDirectionColumns[directionIndex];
                int candidateRow    = currentRow    + chainDirectionRows[directionIndex];
                if (isVisited(candidateColumn, candidateRow, visitedCount)) continue;

                char candidateCell = level.getCell(candidateColumn, candidateRow);
                if (Level.isWall(candidateCell)) continue;
                if (Level.isDoor(candidateCell)
                        && doorBlocksQuery != null && doorBlocksQuery.blocksShotAt(candidateColumn, candidateRow)) continue;
                if (isShotBlockingCover(candidateCell)) continue;

                Object candidateEnemy = enemyHitTarget.enemyAt(candidateColumn, candidateRow);
                if (candidateEnemy != null) {
                    nextColumn = candidateColumn;
                    nextRow    = candidateRow;
                    nextEnemy  = candidateEnemy;
                }
            }

            if (nextEnemy == null) return; // no eligible neighbour — the chain dies here.

            chainDamage *= WeaponConstants.ARC_CANNON_CHAIN_DAMAGE_MULTIPLIER;
            int leapDamage = Math.max(1, Math.round(chainDamage));
            // Dispatch each leap's hit through the standard callback path so abilities (Rhythm,
            // Static Discharge, Lifesteal, etc.) apply to every chained target, mirroring the
            // Railgun's inline per-enemy dispatch.
            setLastHitEnemy(nextEnemy, leapDamage, enemyHitTarget.isAtFullHp(nextEnemy));
            enemyHitTarget.applyDamageTo(nextEnemy, leapDamage);
            dispatchHitCallbacks(new FireResult(false, jumpIndex));
            clearLastHit();

            visitedColumns[visitedCount] = nextColumn;
            visitedRows[visitedCount]    = nextRow;
            visitedCount++;
            currentColumn = nextColumn;
            currentRow    = nextRow;
        }
    }

    /** True when (column, row) is among the first {@code count} recorded visited tiles. */
    private boolean isVisited(int column, int row, int count) {
        for (int index = 0; index < count; index++) {
            if (visitedColumns[index] == column && visitedRows[index] == row) return true;
        }
        return false;
    }

    @Override
    public String hudAmmoString() {
        if (visualState == WeaponVisualState.RELOADING) return "RELOAD";
        return "CELLS " + shotsInClip + "/" + getEffectiveClipSize();
    }

    @Override public String getNormalTexturePath() { return WeaponConstants.ARC_CANNON_NORMAL_TEXTURE_PATH; }
    @Override public String getFireTexturePath()   { return WeaponConstants.ARC_CANNON_FIRE_TEXTURE_PATH;   }
    @Override public String getReloadTexturePath() { return WeaponConstants.ARC_CANNON_RELOAD_TEXTURE_PATH; }
}
