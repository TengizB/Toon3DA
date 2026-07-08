package ge.tbegvadze.toon3d.route;

/**
 * The KIND of a route-map node. This enum lists only the stable IDENTITY of each node kind — all
 * behaviour and display metadata live in a {@link NodeTypeDefinition} owned by the
 * {@link NodeTypeRegistry}, never in switch statements scattered across the codebase.
 *
 * <p>EXTENSIBILITY: adding a new node kind later is one new constant here plus one
 * {@code registry.register(...)} line in {@link RouteRegistries}. Nothing downstream (world,
 * generator, overlay) may switch on a hard-coded node list — it must ask the registry.
 *
 * <p>{@code BOSS} and {@code REGION_GATE} are FORCED convergence nodes: the generator injects them
 * deliberately, so they are excluded from the random pool ({@link NodeTypeRegistry#allWeightedForPool()}).
 */
public enum RouteNodeType {
    COMBAT,
    ELITE,
    CACHE,
    SHOP,
    REST,
    MYSTERY,
    EVENT,
    BOSS,
    REGION_GATE
}
