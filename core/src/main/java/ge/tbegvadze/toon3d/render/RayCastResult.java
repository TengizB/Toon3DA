package ge.tbegvadze.toon3d.render;

/**
 * Mutable result of a single DDA ray cast. Pre-allocated by RayCaster and reused every frame
 * to avoid per-frame heap allocation inside the render path.
 */
public final class RayCastResult {

    /** True when the ray stopped because it hit a wall tile; false when it reached max length. */
    public boolean hitWall;

    /** Fractional tile-space X coordinate of the ray endpoint. */
    public float endTileX;

    /** Fractional tile-space Y coordinate of the ray endpoint. */
    public float endTileY;
}
