package ge.tbegvadze.toon3d.level;

/**
 * Implemented by EVERY generator (balance-overhaul order 2, S1): exposes the generator's own room /
 * chamber / corridor-pocket model to the shared supply and encounter placement, which otherwise read
 * only the finished tile grid. Walk distances, keycard reachability and the exit path are computed
 * from the grid itself ({@link SupplySlotSurvey}); the provider adds what the grid cannot say — which
 * tiles belong to the same ROOM.
 */
public interface SupplySlotProvider {

    /** Region id meaning "a connector tile (corridor) that belongs to no room". */
    int CONNECTOR_REGION = -1;

    /**
     * The id of the room / chamber / pocket that owns this tile, or {@link #CONNECTOR_REGION} for a
     * corridor tile. Ids are small non-negative integers, stable for one generated floor.
     */
    int supplyRegionAt(int tileColumn, int tileRow);

    /** Whether that region is LARGE (it may host two encounter groups, E3). */
    boolean isLargeSupplyRegion(int regionId);
}
