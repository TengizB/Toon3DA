package ge.tbegvadze.toon3d.render.routeicons;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Registry mapping a {@code RouteRegion.themeId} to its {@link RegionCrestPainter} (route-map
 * order-5). The render-layer twin of the region-theme ids the headless route package speaks. Adding
 * a region crest is one class + one {@code register(...)} line; unknown themes fall back to a plain
 * ring badge so a new region always renders something.
 */
public final class RegionCrestPainterRegistry {

    private final Map<String, RegionCrestPainter> crests = new HashMap<>();
    private final RegionCrestPainter fallback;

    public RegionCrestPainterRegistry() {
        this.fallback = new RingBadgeCrestPainter();
    }

    /** Registers {@code crest} under {@code themeId}. Later registrations overwrite earlier. */
    public void register(String themeId, RegionCrestPainter crest) {
        crests.put(Objects.requireNonNull(themeId, "themeId"),
                   Objects.requireNonNull(crest, "crest"));
    }

    /** The crest for {@code themeId}, or a fallback ring badge if none is registered. */
    public RegionCrestPainter get(String themeId) {
        RegionCrestPainter crest = crests.get(themeId);
        return crest != null ? crest : fallback;
    }
}
