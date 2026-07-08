package ge.tbegvadze.toon3d.util;

/**
 * Constants for the branching route-map subsystem (see {@code …toon3d.route}).
 *
 * <p>This file is introduced by route-map order-1 (architecture &amp; registries). It holds the
 * pool-weight and branch-width entries the route-map DATA MODEL references, plus the deterministic
 * seed salt. Order-2 (route-graph generation) fills in the remaining numbers (region band sizes,
 * anti-starvation windows, etc.) as generation is implemented. Per project rule, no route-map
 * value is ever hardcoded inline — it lives here first, then is referenced.
 *
 * <p>All values are pure data; this class contains no LibGDX imports so the route package stays
 * headless-testable.
 */
public final class RouteMapConstants {

    private RouteMapConstants() {}

    // -------------------------------------------------------------------------
    // Determinism
    // -------------------------------------------------------------------------

    /**
     * Salt mixed into every per-node seed so a node's rolls never collide with the floor's
     * {@code LevelGenerator} seed (which is derived by {@code World.floorSeed(runSeed, depth)}).
     * A distinct salt keeps the two seed streams independent even at the same depth.
     */
    public static final long ROUTE_NODE_SEED_SALT = 0x526F757465L; // "Route" in ASCII hex

    // -------------------------------------------------------------------------
    // Node pool base weights (relative probability a rollable type joins the pool)
    // -------------------------------------------------------------------------
    // BOSS and REGION_GATE are FORCED convergence nodes the generator injects deliberately, so
    // they carry no pool weight. Order-2 consumes these weights when it builds the random pool.

    public static final float NODE_WEIGHT_COMBAT  = 1.00f;
    public static final float NODE_WEIGHT_ELITE   = 0.35f;
    public static final float NODE_WEIGHT_CACHE   = 0.45f;
    public static final float NODE_WEIGHT_SHOP    = 0.30f;
    public static final float NODE_WEIGHT_REST    = 0.30f;
    public static final float NODE_WEIGHT_MYSTERY = 0.40f;
    public static final float NODE_WEIGHT_EVENT   = 0.35f;

    // -------------------------------------------------------------------------
    // Branch width — how many nodes a single layer may hold
    // -------------------------------------------------------------------------
    // Consumed by order-2's RouteMapGenerator. Declared here so the data model documents its
    // layout bounds in one place.

    public static final int BRANCH_WIDTH_MINIMUM = 2;
    public static final int BRANCH_WIDTH_MAXIMUM = 4;

    // -------------------------------------------------------------------------
    // Fog / reveal defaults
    // -------------------------------------------------------------------------

    /** Whether a freshly generated node starts revealed (type shown) or hidden behind a '?'. */
    public static final boolean NODE_REVEALED_BY_DEFAULT = false;
}
