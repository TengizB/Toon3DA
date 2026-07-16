package ge.tbegvadze.toon3d.level;

/**
 * The small, immutable query struct a {@link RoomBlueprint} reads to answer
 * {@link RoomBlueprint#eligible(RoomContext)} and {@link RoomBlueprint#selectionWeight(RoomContext)}
 * (symbol/sprite-reuse order-5). It carries exactly the facts the current
 * {@code LevelGenerator.assignRoomTypes()} gates read: the candidate room's interior dimensions, the
 * floor's dungeon depth (for depth-gated rooms like STELLAR_OBSERVATORY), and how many of THIS
 * blueprint have already been placed on the level (for per-level hard caps).
 *
 * <p>order-5 defines and unit-tests this contract; the generator does NOT consult it yet — selection
 * still runs through the enum in {@code assignRoomTypes()}. order-8 flips {@code placeRooms()} to
 * build a {@code RoomContext} per candidate and pick from {@link RoomBlueprintRegistry#selectable}.
 * Headless — no LibGDX.
 */
public final class RoomContext {

    private final int interiorWidth;
    private final int interiorHeight;
    private final int dungeonDepth;
    private final int alreadyPlacedOfThisKind;

    public RoomContext(int interiorWidth, int interiorHeight, int dungeonDepth,
                       int alreadyPlacedOfThisKind) {
        this.interiorWidth           = interiorWidth;
        this.interiorHeight          = interiorHeight;
        this.dungeonDepth            = dungeonDepth;
        this.alreadyPlacedOfThisKind = alreadyPlacedOfThisKind;
    }

    /** Candidate room interior width in tiles (excludes the perimeter walls). */
    public int interiorWidth() {
        return interiorWidth;
    }

    /** Candidate room interior height in tiles (excludes the perimeter walls). */
    public int interiorHeight() {
        return interiorHeight;
    }

    /** The floor's 1-based dungeon depth, used by depth-gated rooms. */
    public int dungeonDepth() {
        return dungeonDepth;
    }

    /** How many instances of the querying blueprint are already placed on this level (for hard caps). */
    public int alreadyPlacedOfThisKind() {
        return alreadyPlacedOfThisKind;
    }
}
