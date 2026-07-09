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

    public static final float NODE_WEIGHT_COMBAT  = 34f;
    public static final float NODE_WEIGHT_ELITE   = 14f;
    public static final float NODE_WEIGHT_CACHE   = 14f;
    public static final float NODE_WEIGHT_SHOP    = 11f;
    public static final float NODE_WEIGHT_REST    = 11f;
    public static final float NODE_WEIGHT_MYSTERY = 10f;
    public static final float NODE_WEIGHT_EVENT   = 6f;

    // -------------------------------------------------------------------------
    // Branch width — how many nodes a single layer may hold
    // -------------------------------------------------------------------------
    // Consumed by order-2's RouteMapGenerator. Declared here so the data model documents its
    // layout bounds in one place.

    public static final int BRANCH_WIDTH_MINIMUM = 2;
    public static final int BRANCH_WIDTH_MAXIMUM = 4;

    /**
     * Probability (0..1) that a source node grows an extra edge to one of its projected lane's
     * neighbours, on top of the guaranteed centre edge. Higher = more cross-connected, busier map.
     */
    public static final float BRANCH_SPREAD_CHANCE = 0.55f;

    // -------------------------------------------------------------------------
    // Region / act bands — the descent's narrated shape
    // -------------------------------------------------------------------------
    // A region spans REGION_BAND_SIZE depths ending in a boss floor (isBossFloor(lastDepth) holds
    // because REGION_BAND_SIZE == Constants.BOSS_FLOOR_INTERVAL). The first FIXED_REGION_COUNT regions
    // are hand-named acts; every region beyond that is an endless "THE BREACH" cycle, generated lazily.

    public static final int REGION_BAND_SIZE   = 5;
    public static final int FIXED_REGION_COUNT = 3;

    public static final String REGION_A_NAME = "OUTER FACILITY";
    public static final String REGION_B_NAME = "RESEARCH WING";
    public static final String REGION_C_NAME = "REACTOR DEPTHS";
    public static final String REGION_D_NAME = "THE BREACH";

    public static final String REGION_A_THEME = "outer_facility";
    public static final String REGION_B_THEME = "research_wing";
    public static final String REGION_C_THEME = "reactor_depths";
    public static final String REGION_D_THEME = "the_breach";

    // -------------------------------------------------------------------------
    // Per-region node-type weight multipliers (scale the base weights above)
    // -------------------------------------------------------------------------
    // 1.0 = unchanged. >1 weights a type UP in that region, <1 down. These give each act a distinct
    // pacing feel without hard-coding any node list — the roll still reads every registered type.

    // Region A "OUTER FACILITY" — leans safe: more caches, fewer elites (footing for the run).
    public static final float REGION_A_MULTIPLIER_COMBAT  = 1.15f;
    public static final float REGION_A_MULTIPLIER_ELITE   = 0.50f;
    public static final float REGION_A_MULTIPLIER_CACHE   = 1.35f;
    public static final float REGION_A_MULTIPLIER_MYSTERY = 0.80f;

    // Region B "RESEARCH WING" — leans exploratory: more mystery/event signals.
    public static final float REGION_B_MULTIPLIER_MYSTERY = 1.40f;
    public static final float REGION_B_MULTIPLIER_EVENT   = 1.50f;
    public static final float REGION_B_MULTIPLIER_ELITE   = 1.10f;

    // Region C "REACTOR DEPTHS" — leans lethal: more elites, fewer safe havens.
    public static final float REGION_C_MULTIPLIER_ELITE = 1.60f;
    public static final float REGION_C_MULTIPLIER_REST  = 0.75f;
    public static final float REGION_C_MULTIPLIER_SHOP  = 0.85f;

    // Region D "THE BREACH" (endless) — relentless: elites up, relief scarce but never absent.
    public static final float REGION_D_MULTIPLIER_ELITE = 1.80f;
    public static final float REGION_D_MULTIPLIER_REST  = 0.70f;
    public static final float REGION_D_MULTIPLIER_SHOP  = 0.70f;

    // -------------------------------------------------------------------------
    // Per-region combat-generator weight multipliers (scale the standard pool)
    // -------------------------------------------------------------------------
    // The combat-node generator pool is ALWAYS the registry's standard pool, so a newly registered
    // generator appears automatically. A region merely LEANS toward biomes by weighting existing ids
    // up; an unlisted generator keeps weight 1.0. Values keyed by GeneratorId.stableId() at build.

    public static final float REGION_B_GENERATOR_WEIGHT_ROOMS   = 1.75f; // research wing = built rooms
    public static final float REGION_C_GENERATOR_WEIGHT_CAVERN  = 1.90f; // reactor depths = raw caverns
    public static final float REGION_D_GENERATOR_WEIGHT_CAVERN  = 1.60f; // the breach = collapsed caverns

    // -------------------------------------------------------------------------
    // Anti-starvation / anti-clump guards (deterministic post-pass over the roll)
    // -------------------------------------------------------------------------

    /** Sliding window (in layers) over which no more than SHOP_MAX_PER_WINDOW shops may appear. */
    public static final int SHOP_WINDOW_LAYERS = 4;
    /** Max SHOP nodes permitted inside any SHOP_WINDOW_LAYERS-wide window (economy pacing). */
    public static final int SHOP_MAX_PER_WINDOW = 2;
    /**
     * The player must be OFFERED a CACHE or REST at least once every RESOURCE_RELIEF_WINDOW layers,
     * protecting the finite-ammo economy. "Offered" = present as a candidate, not forced to take.
     */
    public static final int RESOURCE_RELIEF_WINDOW = 4;
    /** Hard cap on repair iterations per guard so an over-constrained pool can never infinite-loop. */
    public static final int GUARD_REPAIR_ATTEMPT_CAP = 64;

    // -------------------------------------------------------------------------
    // Affix roll hook (ELITE / MYSTERY) — order-9 defines the catalog; order-2 reserves the roll
    // -------------------------------------------------------------------------

    /** Probability an ELITE node rolls an affix, IF an affix catalog has been supplied (else 0). */
    public static final float AFFIX_ROLL_CHANCE_ELITE   = 0.60f;
    /** Probability a MYSTERY node rolls an affix, IF an affix catalog has been supplied (else 0). */
    public static final float AFFIX_ROLL_CHANCE_MYSTERY = 0.25f;

    // -------------------------------------------------------------------------
    // Endless-mode lazy extension
    // -------------------------------------------------------------------------

    /**
     * When the player's current depth comes within this many layers of the map's final layer, the
     * generator appends the next region band so the descent never runs out of graph.
     */
    public static final int EXTENSION_TRIGGER_DISTANCE = 3;

    // -------------------------------------------------------------------------
    // Fog / reveal defaults
    // -------------------------------------------------------------------------

    /** Whether a freshly generated node starts revealed (type shown) or hidden behind a '?'. */
    public static final boolean NODE_REVEALED_BY_DEFAULT = false;

    // -------------------------------------------------------------------------
    // World integration (order-3)
    // -------------------------------------------------------------------------

    /**
     * Whether the ROUTE_SELECT overlay is still shown when the only next pick is a FORCED convergence
     * node (BOSS / REGION_GATE). {@code true} = present a one-card "converge to the boss" beat before
     * the descent (reads better); {@code false} = auto-skip straight to the transition since the pick
     * is not really a choice.
     */
    public static final boolean SHOW_FORCED_NODE_CARD = true;

    // =========================================================================
    // OVERLAY VISUAL SPEC (order-4) — the "FACILITY NAV" console.
    // =========================================================================
    // All coordinates are world units (1280x720, Y-up, origin bottom-left — project invariant).
    // Consumed by RouteMapOverlayRenderer. Per project rule, no visual value is hardcoded inline.

    /** Dim navy scrim alpha over the frozen 3D world so the map reads without fully hiding it. */
    public static final float OVERLAY_SCRIM_ALPHA = 0.72f;

    // ---- Screen regions (vertical bands) -----------------------------------
    public static final float TITLE_PLATE_BOTTOM_Y   = 648f;
    public static final float TITLE_PLATE_TOP_Y      = 712f;
    public static final float MAP_VIEWPORT_BOTTOM_Y  = 150f;
    public static final float MAP_VIEWPORT_TOP_Y     = 640f;
    public static final float LEGEND_STRIP_BOTTOM_Y  = 96f;
    public static final float LEGEND_STRIP_TOP_Y     = 150f;
    public static final float CONFIRM_BAR_BOTTOM_Y   = 12f;
    public static final float CONFIRM_BAR_TOP_Y      = 92f;

    // ---- Map layout --------------------------------------------------------
    /** Vertical spacing between successive layer rows (bottom->top flow: current low, candidates high). */
    public static final float MAP_LAYER_GAP        = 138f;
    /** Horizontal spacing between lanes within one layer row. */
    public static final float MAP_LANE_GAP         = 210f;
    /** How many layers above the current one are drawn (further = smaller + dimmer perspective fade). */
    public static final int   MAP_LOOKAHEAD_LAYERS = 4;
    /** Gap between the viewport's bottom edge and the bottom-most drawn row. */
    public static final float MAP_BOTTOM_PAD       = 46f;
    /** Hard cap on nodes drawn at once, sizing the renderer's pre-allocated layout arrays. */
    public static final int   MAP_MAX_DISPLAY_NODES = 40;

    // ---- Node card ---------------------------------------------------------
    public static final float NODE_CARD_WIDTH         = 150f;
    public static final float NODE_CARD_HEIGHT        = 120f;
    public static final float NODE_CARD_CORNER_RADIUS = 14f;
    public static final float NODE_CARD_BODY_ALPHA    = 0.22f;
    public static final float NODE_CARD_BORDER_WIDTH  = 2f;

    /** Per-layer shrink applied to distant (non-decision) rows: scale = 1 − (row−1)·step. */
    public static final float DISTANT_SCALE_STEP = 0.16f;
    public static final float DISTANT_MIN_SCALE  = 0.5f;
    /** Per-layer fade applied to distant rows: alpha = 1 − (row−1)·step. */
    public static final float DISTANT_ALPHA_STEP = 0.22f;
    public static final float DISTANT_MIN_ALPHA  = 0.28f;
    /** Alpha of the already-taken (VISITED) history row shown below the current node. */
    public static final float HISTORY_ALPHA      = 0.45f;

    // ---- Glow (additive ShapeRenderer discs — NOT a per-frame FrameBuffer) -
    // Project rule: FrameBuffer.end() resets the GL viewport and would corrupt the FitViewport
    // letterbox mid-frame (see WeaponHudRenderer), so the hologram bloom is faked with concentric
    // additive-blended discs instead of an offscreen pass. Crisp, viewport-safe, no allocation.
    public static final float GLOW_RADIUS      = 92f;
    public static final int   GLOW_RING_COUNT  = 5;
    public static final float GLOW_RING_ALPHA  = 0.13f;
    public static final float FOCUS_GLOW_BOOST = 1.6f;

    // ---- Node idle / focus animation ---------------------------------------
    public static final float NODE_BOB_AMPLITUDE = 5f;
    public static final float NODE_BOB_SPEED     = 2.4f;
    public static final float FOCUS_SCALE        = 1.06f;
    public static final float FOCUS_PULSE_SPEED  = 4f;
    public static final float SELECTION_RING_PAD = 12f;
    /** "YOU ARE HERE" reticle around the CURRENT card. */
    public static final float RETICLE_RADIUS     = 86f;
    public static final float RETICLE_SPIN_SPEED = 1.2f;

    // ---- Connector conduits + travelling data pulses -----------------------
    public static final float CONDUIT_WIDTH        = 2.5f;
    public static final int   CONDUIT_SEGMENTS     = 18;
    /** Sideways pull of the bezier control point, giving conduits a gentle S-curve. */
    public static final float CONDUIT_CURVE_OFFSET = 26f;
    public static final float CONDUIT_DIM_ALPHA    = 0.35f;
    /** Travelling pulse loops per second along a hot (leaving CURRENT/AVAILABLE) conduit. */
    public static final float PULSE_SPEED          = 0.55f;
    public static final float PULSE_DOT_RADIUS     = 5f;
    /** Extra-bright "locked route" spine drawn along the path already taken. */
    public static final float HISTORY_SPINE_ALPHA  = 0.9f;

    // ---- Risk pip meter (1-4 dots by dangerTier) ---------------------------
    public static final float RISK_PIP_RADIUS = 4.5f;
    public static final float RISK_PIP_GAP    = 5f;
    public static final int   RISK_PIP_MAX    = 4;
    public static final float RISK_PIP_INSET  = 12f;

    // ---- CRT treatment -----------------------------------------------------
    public static final float SCANLINE_SPACING      = 3f;
    public static final float SCANLINE_ALPHA        = 0.10f;
    public static final float SCANLINE_SCROLL_SPEED = 12f;
    public static final float VIGNETTE_ALPHA        = 0.34f;
    public static final float VIGNETTE_BAND         = 64f;
    public static final float FLICKER_AMPLITUDE     = 0.02f;
    public static final float FLICKER_SPEED         = 30f;
    public static final float SYNC_FLASH_INTERVAL   = 4.5f;
    public static final float SYNC_FLASH_ALPHA      = 0.06f;

    // ---- Bezel chrome / hazard trim ----------------------------------------
    public static final float FRAME_BORDER_WIDTH   = 3f;
    public static final float HAZARD_CHEVRON_WIDTH = 28f;
    public static final float HAZARD_TRIM_HEIGHT   = 12f;
    public static final float RIVET_RADIUS         = 4f;
    public static final float RIVET_INSET          = 18f;
    /** Red-alert bezel pulse rate (ties into the emergency-red-alert language for continuity). */
    public static final float RED_ALERT_PULSE_SPEED = 6f;

    // ---- Confirm bar (visual targets; touch handling is order-6) -----------
    public static final float CONFIRM_BUTTON_WIDTH  = 250f;
    public static final float CONFIRM_BUTTON_MARGIN = 42f;

    // ---- Animation timelines (kept snappy for mobile) ----------------------
    public static final float OPENING_SECONDS      = 0.35f;
    public static final float COMMIT_SECONDS       = 0.40f;
    /**
     * Order-4 has no touch selection yet (order-6), so the console auto-focuses the first candidate
     * and, after showing the map this long, auto-commits it — proving the OPENING/IDLE/COMMIT
     * timelines and the present/commit pipeline end-to-end. Order-6 replaces this with a real tap.
     */
    public static final float AUTO_SELECT_HOLD_SECONDS = 1.25f;

    // ---- Text scales -------------------------------------------------------
    public static final float TITLE_TEXT_SCALE   = 1.2f;
    public static final float DEPTH_TEXT_SCALE   = 0.95f;
    public static final float REGION_TEXT_SCALE  = 1.0f;
    public static final float NODE_LABEL_SCALE   = 0.9f;
    public static final float LEGEND_TEXT_SCALE  = 0.95f;
    public static final float CONFIRM_LABEL_SCALE = 1.1f;
}
