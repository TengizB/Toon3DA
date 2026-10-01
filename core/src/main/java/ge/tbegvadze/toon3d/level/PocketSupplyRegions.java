package ge.tbegvadze.toon3d.level;

/**
 * The simplest {@link SupplySlotProvider} (balance-overhaul order 2): the grid cut into fixed
 * rectangular POCKETS, each its own region. Used by the bespoke single-chamber generators (the boss
 * arena, the clinic, the event room, the airlock), which have no room model of their own but still go
 * through the shared supply pipeline (S1) — the pockets let its per-room spread rule (S7) mean "not
 * all in one corner".
 */
public final class PocketSupplyRegions implements SupplySlotProvider {

    private final int pocketWidth;
    private final int pocketHeight;
    private final int pocketColumns;

    /**
     * @param gridWidth    the grid's width in tiles
     * @param pocketWidth  pocket width in tiles (&gt;= 1)
     * @param pocketHeight pocket height in tiles (&gt;= 1)
     */
    public PocketSupplyRegions(int gridWidth, int pocketWidth, int pocketHeight) {
        this.pocketWidth   = Math.max(1, pocketWidth);
        this.pocketHeight  = Math.max(1, pocketHeight);
        this.pocketColumns = (Math.max(1, gridWidth) + this.pocketWidth - 1) / this.pocketWidth;
    }

    @Override
    public int supplyRegionAt(int tileColumn, int tileRow) {
        if (tileColumn < 0 || tileRow < 0) return CONNECTOR_REGION;
        return (tileRow / pocketHeight) * pocketColumns + (tileColumn / pocketWidth);
    }

    @Override
    public boolean isLargeSupplyRegion(int regionId) {
        return false;
    }
}
