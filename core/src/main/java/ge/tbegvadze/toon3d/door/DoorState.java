package ge.tbegvadze.toon3d.door;

/**
 * Lifecycle states for a single door tile.
 *
 * State transitions:
 *   CLOSED   → OPENING  (player interacts / steps toward the door)
 *   OPENING  → OPEN     (animation completes)
 *   OPEN     → CLOSING  (player leaves the tile)
 *   CLOSING  → CLOSED   (animation completes)
 *   CLOSING  → OPENING  (player re-approaches mid-close — animation reverses seamlessly)
 */
public enum DoorState {
    CLOSED,
    OPENING,
    OPEN,
    CLOSING
}
