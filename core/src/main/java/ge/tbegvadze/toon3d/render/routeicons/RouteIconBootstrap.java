package ge.tbegvadze.toon3d.render.routeicons;

import ge.tbegvadze.toon3d.util.RouteMapConstants;

/**
 * The one place the route-map icon + crest registries are populated (route-map order-5) — the
 * render-layer twin of {@code RouteRegistries.bootstrap()}. It registers every v1 node
 * {@link IconPainter} (keyed by the {@code iconPainterId} the node types declare) and every v1
 * {@link RegionCrestPainter} (keyed by {@code RouteRegion.themeId}).
 *
 * <p><b>How to add a new node icon.</b> Write a {@code class MyNodeIconPainter implements IconPainter}
 * in this package, register it here with the same id the node type's {@code iconPainterId} declares,
 * and the overlay draws it with zero edits. A new region crest is the same recipe against
 * {@link RegionCrestPainterRegistry}. This mirrors the "one class + one line" extensibility promise.
 */
public final class RouteIconBootstrap {

    private RouteIconBootstrap() {}

    /** Registers the nine v1 node icon painters into {@code registry}. */
    public static void registerIconPainters(IconPainterRegistry registry) {
        registry.register("icon_combat",  new CombatIconPainter());
        registry.register("icon_elite",   new EliteIconPainter());
        registry.register("icon_cache",   new CacheIconPainter());
        registry.register("icon_shop",    new ShopIconPainter());
        registry.register("icon_rest",    new RestIconPainter());
        registry.register("icon_mystery", new MysteryIconPainter());
        registry.register("icon_event",   new EventIconPainter());
        registry.register("icon_gate",    new RegionGateIconPainter());
        registry.register("icon_boss",    new BossIconPainter());
    }

    /** Registers the four v1 region crests into {@code registry}, keyed by region theme id. */
    public static void registerRegionCrests(RegionCrestPainterRegistry registry) {
        registry.register(RouteMapConstants.REGION_A_THEME, new GearRingCrestPainter());
        registry.register(RouteMapConstants.REGION_B_THEME, new MoleculeCrestPainter());
        registry.register(RouteMapConstants.REGION_C_THEME, new RadiationTrefoilCrestPainter());
        registry.register(RouteMapConstants.REGION_D_THEME, new CorruptedSigilCrestPainter());
    }
}
