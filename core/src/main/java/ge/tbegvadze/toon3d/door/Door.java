package ge.tbegvadze.toon3d.door;

/**
 * Mutable runtime state for a single door tile.
 *
 * {@code tileColumn} and {@code tileRow} are immutable tile-grid coordinates
 * (Y-up, (0,0) = bottom-left tile). All mutable fields are mutated exclusively
 * by {@link DoorManager}.
 */
public class Door {

    /** Tile-grid column of this door (X axis, 0 = left edge). */
    public final int tileColumn;

    /** Tile-grid row of this door (Y axis, 0 = bottom edge, Y-up). */
    public final int tileRow;

    /** Current lifecycle state of this door. */
    public DoorState state;

    /**
     * Progress within the current animating state, in the range [0, 1].
     * 0 = animation just started; 1 = animation fully complete.
     * Reset to 0f whenever a new animation phase begins.
     */
    public float animationProgress;

    /**
     * Creates a new door at the given tile position, initially fully closed
     * with no animation in progress.
     *
     * @param tileColumn tile-grid column (X), 0 = left edge
     * @param tileRow    tile-grid row    (Y), 0 = bottom edge (Y-up)
     */
    public Door(int tileColumn, int tileRow) {
        this.tileColumn        = tileColumn;
        this.tileRow           = tileRow;
        this.state             = DoorState.CLOSED;
        this.animationProgress = 0f;
    }
}
