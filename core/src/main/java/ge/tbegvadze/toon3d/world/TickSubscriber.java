package ge.tbegvadze.toon3d.world;

/**
 * Receives exactly one notification per game tick.
 * Implementations must NOT allocate and must NOT block.
 * The world has already advanced the turn (player moved/fired) before this is called.
 */
public interface TickSubscriber {
    /**
     * @param context immutable per-tick context (player tile position, tick index, cause).
     *                Never null. Do not retain the reference beyond this call.
     */
    void onTick(TickContext context);
}
