package ge.tbegvadze.toon3d.route;

/**
 * Where on a generated floor a {@link GuaranteedContent} promise should land. Resolved against the
 * floor's spawn ('p') / exit ('&gt;') anchors and walkable-tile distances by
 * {@link GuaranteedContentStamper}, so the same guarantee (e.g. "3 ammo boxes") drops sensibly no
 * matter which generator built the floor.
 *
 * <p>Placement is an INTENT, not a coordinate: the stamper picks concrete legal tiles
 * deterministically from the floor seed, so results are reproducible.
 */
public enum Placement {
    /** Tiles closest to the player start ('p') — early, reachable pickups. */
    NEAR_SPAWN,
    /** Tiles closest to the exit ('&gt;') — a reward for reaching the end. */
    NEAR_EXIT,
    /** The walkable tile(s) farthest (path/Euclidean) from the spawn — the deep-room climax. */
    FARTHEST_ROOM,
    /** Tiles nearest the centroid of all walkable tiles — a central display. */
    CENTER_ROOM,
    /** Spread widely across the floor's walkable tiles (seeded, well-separated). */
    SCATTERED,
    /**
     * Behind a gate when one exists; approximated as {@link #FARTHEST_ROOM} until a floor exposes
     * explicit gated-room metadata (order-8+ may refine this).
     */
    GATED_ROOM
}
