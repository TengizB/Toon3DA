package ge.tbegvadze.toon3d.route;

/**
 * How threatening a route node is. Drives the overlay colour language (order-4) and the
 * anti-starvation guards in the route-graph generator (order-2). Ordered from safest to most
 * dangerous so callers may compare ordinals when tuning distribution.
 */
public enum DangerTier {
    /** Safe havens — med-bay / rest, shops. No combat expected. */
    CALM,
    /** Ordinary encounters — the backbone combat node. */
    STANDARD,
    /** Elevated threat — elite hotzones. */
    DANGER,
    /** Unknown risk/reward — mystery nodes the player gambles on. */
    GAMBLE,
    /** Forced convergence set-pieces — bosses, region gates. */
    SET_PIECE
}
