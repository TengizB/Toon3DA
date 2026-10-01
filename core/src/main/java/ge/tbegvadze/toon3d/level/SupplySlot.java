package ge.tbegvadze.toon3d.level;

/**
 * One candidate ground tile for a planned pickup (balance-overhaul order 2): where it is, which room
 * owns it, how far the player walks to reach it, whether that walk needs a keycard, and whether it
 * lies on the start-to-exit path. Produced by {@link SupplySlotSurvey}; consumed by
 * {@link SupplyPlanner#place}. Immutable except for {@link #groupRegion}, which the floor populator
 * sets once the encounter groups are placed.
 */
public final class SupplySlot {

    public final int     tileColumn;
    public final int     tileRow;
    /** Owning room / chamber id, or {@link SupplySlotProvider#CONNECTOR_REGION} for a corridor tile. */
    public final int     regionId;
    /** Walk tiles from the start (keycard-free when {@link #reachableWithoutKeycard}, else with keys). */
    public final int     walkDistance;
    /** Whether the player reaches this tile without unlocking any keycard door (S4). */
    public final boolean reachableWithoutKeycard;
    /** Whether the tile lies on the shortest start-to-exit walk (S7 preference). */
    public final boolean onExitPath;
    /** Whether the owning room hosts an encounter group (S7 preference: reward the fight). */
    boolean groupRegion;

    public SupplySlot(int tileColumn, int tileRow, int regionId, int walkDistance,
               boolean reachableWithoutKeycard, boolean onExitPath) {
        this.tileColumn              = tileColumn;
        this.tileRow                 = tileRow;
        this.regionId                = regionId;
        this.walkDistance            = walkDistance;
        this.reachableWithoutKeycard = reachableWithoutKeycard;
        this.onExitPath              = onExitPath;
    }

    /** Whether the tile is a corridor connector rather than a room tile. */
    public boolean isConnector() {
        return regionId == SupplySlotProvider.CONNECTOR_REGION;
    }

    /** Whether the owning room hosts an encounter group. */
    public boolean isGroupRegion() {
        return groupRegion;
    }
}
