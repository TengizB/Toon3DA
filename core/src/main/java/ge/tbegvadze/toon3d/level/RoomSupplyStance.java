package ge.tbegvadze.toon3d.level;

/**
 * A room blueprint's declared relationship to the floor's SUPPLY (balance-overhaul order 8, K12 / C-ROOM).
 * Supply — pickups and enemy spawns — is planned per FLOOR by {@code SupplyPlanner} and the encounter planner
 * and placed into slots; a room's own {@link RoomBlueprint#build} is architecture and props. The stance makes
 * that split a declaration the audit can check instead of an accident of what a build step happens to write.
 */
public enum RoomSupplyStance {

    /** The room's build() stamps no pickup and no enemy-spawn symbol; every pickup in it comes from the planner. */
    SUPPLY_NEUTRAL,

    /**
     * The room offers tagged supply / group slots that the planners may fill (K12); the room itself never stamps
     * pickups or spawns. No shipped room uses it yet.
     */
    SLOT_PROVIDER
}
