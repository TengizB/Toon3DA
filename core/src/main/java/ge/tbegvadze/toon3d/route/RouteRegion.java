package ge.tbegvadze.toon3d.route;

import java.util.Objects;

/**
 * An "act" band of the route map — a contiguous span of depths sharing a theme. Order-1 defines the
 * minimal shape (index, name, depth span, theme id); order-2/order-10 populate the regions and give
 * them distribution rules and biome theming. Kept here so {@link RouteMap#revealRegion(int)} has a
 * concrete unit to reveal.
 */
public final class RouteRegion {

    private final int regionIndex;
    private final String displayName;
    private final int firstDepth;
    private final int lastDepth;
    private final String themeId;

    public RouteRegion(int regionIndex, String displayName, int firstDepth, int lastDepth, String themeId) {
        if (firstDepth > lastDepth) {
            throw new IllegalArgumentException(
                    "Region depth span inverted: firstDepth=" + firstDepth + " > lastDepth=" + lastDepth);
        }
        this.regionIndex = regionIndex;
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.firstDepth  = firstDepth;
        this.lastDepth   = lastDepth;
        this.themeId     = Objects.requireNonNull(themeId, "themeId");
    }

    public int regionIndex() {
        return regionIndex;
    }

    public String displayName() {
        return displayName;
    }

    /** First (inclusive) depth in this region. */
    public int firstDepth() {
        return firstDepth;
    }

    /** Last (inclusive) depth in this region. */
    public int lastDepth() {
        return lastDepth;
    }

    /** Theme key resolved to visuals in the render layer (order-4/order-10). */
    public String themeId() {
        return themeId;
    }

    /** Whether the given floor depth falls within this region's band. */
    public boolean containsDepth(int depth) {
        return depth >= firstDepth && depth <= lastDepth;
    }
}
