package ge.tbegvadze.toon3d.hazard;

import ge.tbegvadze.toon3d.door.DoorManager;
import ge.tbegvadze.toon3d.entity.ImpactEventListener;
import ge.tbegvadze.toon3d.entity.SpireHitTarget;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.EnemyConstants;
import ge.tbegvadze.toon3d.util.GameMath;

/**
 * Owns the runtime CRYSTAL SPIRE tiles grown by Verdant Spiresowers
 * (.claude/agents/ideas/elemental-golem-verdant-spiresower.txt), mirroring the way
 * {@link HazardManager}/{@link ExplosiveBarrelManager} own runtime tiles that are only STAMPED onto the
 * level grid for rendering.
 *
 * <p>A spire is SOLID, sight-blocking terrain — not an enemy: no health bar, no AI, no XP, no entry in the
 * enemy list. It has flat HP, decays after a lifetime, and heals its sower while it stands. This class is
 * the single authority for spire HP, lifetime and the grid stamp/unstamp; the grid cell only holds the
 * {@link EnemyConstants#SPIRE_TILE_CHAR} decal so the PropRenderer can draw it (auto-culled when restored),
 * exactly like a hazard tile. {@code Level.isSpire} makes that char block movement, LOS and shots for free.
 *
 * <p>A LEAKED solid tile is a permanently broken level, so every stamp is paired with a restore: expiry,
 * destruction, the owner's death and {@link #clear()} on level teardown all unstamp. Holds no GPU resources,
 * so it needs no dispose(); World calls {@link #clear()} when the floor is torn down.
 *
 * <p>Zero allocation per turn: the spire records and the reachability flood-fill buffers are pre-allocated.
 */
public final class SpireManager implements SpireHitTarget {

    /** Notified when a spire tile is placed so a renderer can register its billboard. */
    public interface SpireVisualListener {
        void onSpirePlaced(int tileColumn, int tileRow, char spireChar);
    }

    private final Level       level;
    private final DoorManager doorManager;
    private final int         gridWidth;
    private final int         gridHeight;

    // Fixed-capacity parallel spire records — never reallocated after construction. A floor rarely holds
    // more than one sower; SPIRE_CAPACITY covers several at the per-sower live cap with headroom.
    private static final int SPIRE_CAPACITY = 24;
    private final int[]  spireColumns   = new int[SPIRE_CAPACITY];
    private final int[]  spireRows      = new int[SPIRE_CAPACITY];
    private final int[]  spireHealth    = new int[SPIRE_CAPACITY];
    private final int[]  spireLifetime  = new int[SPIRE_CAPACITY];
    private final int[]  spireOwnerId   = new int[SPIRE_CAPACITY];
    private final char[] spireUnderChar = new char[SPIRE_CAPACITY];
    private int spireCount = 0;

    // Reusable flood-fill scratch for the reachability guard — reset each call, never reallocated.
    private final boolean[][] reachVisited;
    private final int[]       reachQueueColumns;
    private final int[]       reachQueueRows;

    private SpireVisualListener  visualListener;
    private ImpactEventListener  impactEventListener;

    public SpireManager(Level level, DoorManager doorManager) {
        this.level             = level;
        this.doorManager       = doorManager;
        this.gridWidth         = level.getWidth();
        this.gridHeight        = level.getHeight();
        this.reachVisited      = new boolean[gridWidth][gridHeight];
        int capacity           = Math.max(1, gridWidth * gridHeight);
        this.reachQueueColumns = new int[capacity];
        this.reachQueueRows    = new int[capacity];
    }

    /** Wires the renderer hook so each new spire tile registers its billboard. */
    public void setVisualListener(SpireVisualListener listener) {
        this.visualListener = listener;
    }

    /** Wires the effect hook so spire birth/shatter play their cosmetic bursts. */
    public void setImpactEventListener(ImpactEventListener listener) {
        this.impactEventListener = listener;
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    /** True when a live spire occupies the given tile. */
    public boolean isSpireAt(int tileColumn, int tileRow) {
        return indexAt(tileColumn, tileRow) >= 0;
    }

    /** Live spires this owner currently holds — the per-sower cap check. */
    public int liveSpireCountForOwner(int ownerId) {
        int count = 0;
        for (int index = 0; index < spireCount; index++) {
            if (spireOwnerId[index] == ownerId) count++;
        }
        return count;
    }

    /**
     * Living spires within {@code rangeTiles} (Chebyshev) of the given tile — the sower's per-turn regen
     * input. Counts EVERY spire in range, whatever its owner, so two sowers whose spires overlap can
     * cross-heal (a deliberate deep-floor spike, per the design doc).
     */
    public int countLivingSpiresNear(int centerColumn, int centerRow, int rangeTiles) {
        int count = 0;
        for (int index = 0; index < spireCount; index++) {
            if (GameMath.chebyshevDistanceTiles(spireColumns[index], spireRows[index],
                    centerColumn, centerRow) <= rangeTiles) {
                count++;
            }
        }
        return count;
    }

    /** Total live spires on the floor. Exposed for tests and telemetry. */
    public int liveSpireCount() {
        return spireCount;
    }

    // -------------------------------------------------------------------------
    // Mutation
    // -------------------------------------------------------------------------

    /**
     * Plants a spire on the given tile for {@code ownerId}. No-op (returns false) if the tile is out of
     * bounds, already blocked (wall/prop/door/existing spire), or the capacity is full. Stamps the spire
     * char onto the grid, saving the floor tile it overlaid so it can be restored on removal.
     */
    public boolean sowSpire(int tileColumn, int tileRow, int ownerId, int lifetimeTurns) {
        if (spireCount >= SPIRE_CAPACITY)          return false;
        if (!inBounds(tileColumn, tileRow))        return false;
        if (level.isBlockedAt(tileColumn, tileRow, doorManager)) return false;

        int index = spireCount++;
        spireColumns[index]   = tileColumn;
        spireRows[index]      = tileRow;
        spireHealth[index]    = EnemyConstants.SPIRESOWER_SPIRE_HIT_POINTS;
        spireLifetime[index]  = lifetimeTurns;
        spireOwnerId[index]   = ownerId;
        spireUnderChar[index] = level.getCell(tileColumn, tileRow);
        level.setCell(tileColumn, tileRow, EnemyConstants.SPIRE_TILE_CHAR);
        if (visualListener != null) {
            visualListener.onSpirePlaced(tileColumn, tileRow, EnemyConstants.SPIRE_TILE_CHAR);
        }
        if (impactEventListener != null) {
            impactEventListener.onSpireBorn(tileColumn, tileRow);
        }
        return true;
    }

    /** The id of the sower that planted the spire on this tile, or -1 if none is there. */
    @Override
    public int spireOwnerAt(int tileColumn, int tileRow) {
        int index = indexAt(tileColumn, tileRow);
        return index >= 0 ? spireOwnerId[index] : -1;
    }

    /**
     * Applies {@code amount} damage to a spire on the tile (no-op if none is there). A spire has flat HP
     * and no mitigation. When it dies the tile is restored and its shatter beat plays; the caller flinches
     * the sower (it has the enemy list). Returns true if a spire was on the tile (so the caller can fire
     * the hit feedback), whether or not it died.
     */
    @Override
    public boolean damageSpireAt(int tileColumn, int tileRow, int amount) {
        int index = indexAt(tileColumn, tileRow);
        if (index < 0) return false;
        spireHealth[index] -= Math.max(0, amount);
        if (spireHealth[index] > 0) return true;
        destroyAt(index);
        if (impactEventListener != null) {
            impactEventListener.onSpireShattered(tileColumn, tileRow);
        }
        return true;
    }

    /**
     * Advances every spire's decay clock by one world turn and removes the expired ones (restoring their
     * tiles). Compacts the record arrays in place — no allocation. Call once per world turn.
     */
    public void tickLifetimes() {
        for (int index = spireCount - 1; index >= 0; index--) {
            spireLifetime[index]--;
            if (spireLifetime[index] <= 0) {
                int column = spireColumns[index];
                int row    = spireRows[index];
                destroyAt(index);
                if (impactEventListener != null) {
                    impactEventListener.onSpireShattered(column, row);
                }
            }
        }
    }

    /**
     * Shatters EVERY spire this owner planted (the golem's death withering), restoring each tile. The room
     * visibly returns to normal, which is its own reward. Safe to call for an owner with no spires.
     */
    @Override
    public void shatterSpiresOf(int ownerId) {
        for (int index = spireCount - 1; index >= 0; index--) {
            if (spireOwnerId[index] != ownerId) continue;
            int column = spireColumns[index];
            int row    = spireRows[index];
            destroyAt(index);
            if (impactEventListener != null) {
                impactEventListener.onSpireShattered(column, row);
            }
        }
    }

    /** Restores every stamped tile and drops all records. Call on level teardown — no leaked solid tiles. */
    public void clear() {
        for (int index = 0; index < spireCount; index++) {
            restoreTile(index);
        }
        spireCount = 0;
    }

    // -------------------------------------------------------------------------
    // Reachability guard — the non-optional rule that a sow never seals the player off
    // -------------------------------------------------------------------------

    /**
     * True when planting a spire on {@code candidate} would leave at least one level EXIT (stairs-down)
     * unreachable from the player — i.e. it must NOT be sown there. Implemented exactly as the design doc
     * specifies: a flood fill from the player's tile with the candidate tile (and any already-chosen
     * candidates this sow) treated as solid; reject if any exit becomes unreachable. A floor with no exit
     * tile can never be sealed, so this returns false there.
     *
     * <p>Uses {@link Level#isBlockedAt} as the passability predicate (walls, solid props, existing spires
     * and closed doors — NOT transient enemies), so the guard reflects what the player can actually path
     * through. Reuses the pre-allocated flood-fill buffers, so it allocates nothing inside the turn loop.
     *
     * @param extraBlockedColumns extra tiles to treat as solid (already-chosen candidates this sow); may be
     *                            null. Only the first {@code extraBlockedCount} entries are read.
     */
    public boolean wouldSealExit(int playerColumn, int playerRow, int candidateColumn, int candidateRow,
                                 int[] extraBlockedColumns, int[] extraBlockedRows, int extraBlockedCount) {
        int exitCount = countExits();
        if (exitCount == 0) return false; // nothing to seal the player away from

        for (int column = 0; column < gridWidth; column++) {
            java.util.Arrays.fill(reachVisited[column], false);
        }

        int queueHead = 0;
        int queueTail = 0;
        if (inBounds(playerColumn, playerRow) && !reachVisited[playerColumn][playerRow]) {
            reachVisited[playerColumn][playerRow] = true;
            reachQueueColumns[queueTail] = playerColumn;
            reachQueueRows[queueTail]    = playerRow;
            queueTail++;
        }

        while (queueHead < queueTail) {
            int column = reachQueueColumns[queueHead];
            int row    = reachQueueRows[queueHead];
            queueHead++;
            for (int direction = 0; direction < 4; direction++) {
                int nextColumn = column + STEP_COLUMNS[direction];
                int nextRow    = row    + STEP_ROWS[direction];
                if (!inBounds(nextColumn, nextRow))            continue;
                if (reachVisited[nextColumn][nextRow])         continue;
                if (nextColumn == candidateColumn && nextRow == candidateRow) continue;
                if (isExtraBlocked(nextColumn, nextRow, extraBlockedColumns, extraBlockedRows, extraBlockedCount)) continue;
                // The exit tile itself is walkable even though isBlockedAt is false for it; a wall/prop/
                // spire/closed-door blocks the flood. The candidate above is the tile under test.
                if (level.isBlockedAt(nextColumn, nextRow, doorManager)) continue;
                reachVisited[nextColumn][nextRow] = true;
                reachQueueColumns[queueTail] = nextColumn;
                reachQueueRows[queueTail]    = nextRow;
                queueTail++;
            }
        }

        // Any exit the flood did not reach means the candidate would seal the player off.
        for (int row = 0; row < gridHeight; row++) {
            for (int column = 0; column < gridWidth; column++) {
                if (Level.isStairsDown(level.getCell(column, row)) && !reachVisited[column][row]) {
                    return true;
                }
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private static final int[] STEP_COLUMNS = {  1, -1,  0,  0 };
    private static final int[] STEP_ROWS    = {  0,  0,  1, -1 };

    private int indexAt(int tileColumn, int tileRow) {
        for (int index = 0; index < spireCount; index++) {
            if (spireColumns[index] == tileColumn && spireRows[index] == tileRow) return index;
        }
        return -1;
    }

    /** Removes the spire record at {@code index} (restoring its tile) and compacts the arrays in place. */
    private void destroyAt(int index) {
        restoreTile(index);
        int last = spireCount - 1;
        spireColumns[index]   = spireColumns[last];
        spireRows[index]      = spireRows[last];
        spireHealth[index]    = spireHealth[last];
        spireLifetime[index]  = spireLifetime[last];
        spireOwnerId[index]   = spireOwnerId[last];
        spireUnderChar[index] = spireUnderChar[last];
        spireCount--;
    }

    /** Restores the floor tile a spire overlaid, but only if the cell still shows our spire decal. */
    private void restoreTile(int index) {
        int column = spireColumns[index];
        int row    = spireRows[index];
        if (level.getCell(column, row) == EnemyConstants.SPIRE_TILE_CHAR) {
            level.setCell(column, row, spireUnderChar[index]);
        }
    }

    private int countExits() {
        int count = 0;
        for (int row = 0; row < gridHeight; row++) {
            for (int column = 0; column < gridWidth; column++) {
                if (Level.isStairsDown(level.getCell(column, row))) count++;
            }
        }
        return count;
    }

    private static boolean isExtraBlocked(int tileColumn, int tileRow,
                                          int[] extraColumns, int[] extraRows, int extraCount) {
        if (extraColumns == null) return false;
        for (int index = 0; index < extraCount; index++) {
            if (extraColumns[index] == tileColumn && extraRows[index] == tileRow) return true;
        }
        return false;
    }

    private boolean inBounds(int tileColumn, int tileRow) {
        return tileColumn >= 0 && tileColumn < gridWidth && tileRow >= 0 && tileRow < gridHeight;
    }
}
