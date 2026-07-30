package ge.tbegvadze.toon3d.entity;

/**
 * Narrow interface onto the crystal-spire simulation (SpireManager, grown by Verdant Spiresowers), passed
 * into EnemyManager the same way {@link EnemyHitTarget}/{@link BarrelHitTarget} are — so the enemy package
 * never depends on the hazard package directly (SpireManager, in hazard, implements this entity-package
 * seam). Spires are SOLID terrain, not enemies: they have flat HP, no AI and no XP, so their queries live
 * here rather than on the enemy interfaces (.claude/agents/ideas/elemental-golem-verdant-spiresower.txt).
 */
public interface SpireHitTarget {

    /** True when a live spire occupies the tile. */
    boolean isSpireAt(int tileColumn, int tileRow);

    /** The id of the sower that planted the spire on this tile, or -1 if none is there. */
    int spireOwnerAt(int tileColumn, int tileRow);

    /** Applies flat {@code amount} damage to a spire on the tile; returns true if one was there (hit). */
    boolean damageSpireAt(int tileColumn, int tileRow, int amount);

    /** Living spires within {@code rangeTiles} (Chebyshev) of the tile — the sower's per-turn regen input. */
    int countLivingSpiresNear(int centerColumn, int centerRow, int rangeTiles);

    /** Live spires this owner currently holds — the per-sower cap check. */
    int liveSpireCountForOwner(int ownerId);

    /** Plants a spire on the tile for {@code ownerId}; returns false if the tile is blocked or at capacity. */
    boolean sowSpire(int tileColumn, int tileRow, int ownerId, int lifetimeTurns);

    /** Shatters every spire this owner planted (its death withering). */
    void shatterSpiresOf(int ownerId);

    /**
     * True when planting a spire on {@code candidate} would leave a level exit unreachable from the player
     * — i.e. it must NOT be sown there. {@code extraBlocked*} are already-chosen candidates this sow to
     * treat as solid (may be null; only the first {@code extraBlockedCount} entries are read).
     */
    boolean wouldSealExit(int playerColumn, int playerRow, int candidateColumn, int candidateRow,
                          int[] extraBlockedColumns, int[] extraBlockedRows, int extraBlockedCount);
}
