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
    // TEMPORARY TESTING TOGGLE — remove before shipping
    // -------------------------------------------------------------------------

    /**
     * TEMPORARY DEBUG FLAG (testing only — must be {@code false} for real builds).
     *
     * <p>When {@code true}, any BOSS node drawn on the FACILITY NAV route console becomes tappable
     * regardless of whether it is a legal AVAILABLE next pick, so a tester can jump straight into the
     * boss arena from any route-select screen instead of clearing a whole region first. Selecting such
     * a boss bypasses the normal cursor advance in {@code World.commitRouteNode} and drops directly
     * into the boss floor.
     *
     * <p>This is a developer convenience for exercising the boss fight in isolation; it is NOT a
     * gameplay feature. Set back to {@code false} (or delete the flag and its call sites) once boss
     * testing is done.
     */
    public static final boolean BOSS_TEST_ALWAYS_CLICKABLE = true;

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

    /**
     * Alpha of the FULLY-OPAQUE dark backdrop panel filling the map viewport band, drawn after the
     * world scrim and before the additive glow/conduit/card passes. Raised to 1.0 (was 0.90): at 0.90
     * the frozen 3D world (weapon/HUD text, walls) still ghosted ~10% through the schematic in the map
     * band and washed out the cards/labels — the "screen-like effect" the player asked to have removed.
     * A solid backdrop gives every card and label maximum contrast; the additive glow still reads on top
     * of it. The world OUTSIDE this band still ghosts through the bezel chrome (diegetic).
     */
    public static final float MAP_SCREEN_FILL_ALPHA = 1.0f;

    // ---- Screen regions (vertical bands) -----------------------------------
    // The map viewport now runs from just above the confirm bar (104) to just below the title plate
    // (640). The old dedicated LEGEND_STRIP band (96..150) was retired: it wasted ~46px of vertical
    // space that showed only faint HUD bleed, so the map band was extended DOWN over it and the focused
    // node's hint line now renders in the confirm bar's centre gap (between CANCEL and ENGAGE).
    public static final float TITLE_PLATE_BOTTOM_Y   = 648f;
    public static final float TITLE_PLATE_TOP_Y      = 712f;
    public static final float MAP_VIEWPORT_BOTTOM_Y  = 104f;
    public static final float MAP_VIEWPORT_TOP_Y     = 640f;
    public static final float CONFIRM_BAR_BOTTOM_Y   = 12f;
    public static final float CONFIRM_BAR_TOP_Y      = 92f;

    // ---- Map layout --------------------------------------------------------
    /** Vertical spacing between successive layer rows (bottom->top flow: current low, candidates high). */
    public static final float MAP_LAYER_GAP        = 138f;
    /** Horizontal spacing between lanes within one layer row. */
    public static final float MAP_LANE_GAP         = 210f;
    /** How many layers above the current one are drawn (further = smaller + dimmer perspective fade). */
    public static final int   MAP_LOOKAHEAD_LAYERS = 4;
    /** Gap between the viewport's bottom edge and the bottom-most drawn row. Raised with the lowered
     *  MAP_VIEWPORT_BOTTOM_Y so the CURRENT node card clears the bottom chrome (card is 120 tall). */
    public static final float MAP_BOTTOM_PAD       = 60f;
    /** Hard cap on nodes drawn at once, sizing the renderer's pre-allocated layout arrays. */
    public static final int   MAP_MAX_DISPLAY_NODES = 40;

    // ---- Node card ---------------------------------------------------------
    public static final float NODE_CARD_WIDTH         = 150f;
    public static final float NODE_CARD_HEIGHT        = 120f;
    public static final float NODE_CARD_CORNER_RADIUS = 14f;
    public static final float NODE_CARD_BODY_ALPHA    = 0.34f;
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
    public static final float GLOW_RADIUS      = 70f;
    public static final int   GLOW_RING_COUNT  = 5;
    public static final float GLOW_RING_ALPHA  = 0.10f;
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
    // REMOVED (player: "there is a 'screen like' effect on the main part of the roadmap, I want it
    // removed completely because it makes text unreadable and nodes not seen well"). Every CRT layer —
    // scanlines, vignette, whole-hologram flicker, and the periodic sync flash — is zeroed so the map
    // reads as a clean, high-contrast schematic on the opaque backdrop. drawCrtPass() skips each pass
    // whose alpha is not positive, so at these values it is a no-op. Constants kept (documented) so the
    // treatment can be dialled back in without re-plumbing if a later design wants a subtle version.
    public static final float SCANLINE_SPACING      = 4f;
    public static final float SCANLINE_ALPHA        = 0.0f;
    public static final float SCANLINE_SCROLL_SPEED = 12f;
    /**
     * Vignette edge-band darkening. Zeroed (Issue 1 option B): the four opaque bands hugged the map
     * viewport edges and painted over the top/bottom node rows and outer lanes — the "black border
     * cutting the cards" the player reported. The dedicated screen panel ({@link #MAP_SCREEN_FILL_ALPHA})
     * now supplies the "inside a display" feel, so the vignette pass no-ops at alpha 0. Constants kept
     * documented; drawCrtPass() skips the pass entirely when the alpha is not positive.
     */
    public static final float VIGNETTE_ALPHA        = 0.0f;
    public static final float VIGNETTE_BAND         = 64f;
    public static final float FLICKER_AMPLITUDE     = 0.0f;
    public static final float FLICKER_SPEED         = 30f;
    public static final float SYNC_FLASH_INTERVAL   = 4.5f;
    public static final float SYNC_FLASH_ALPHA      = 0.0f;

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

    // =========================================================================
    // NODE ICON + REGION CREST SPEC (order-5) — the procedural IconPainter system.
    // =========================================================================
    // Every node icon and region crest is drawn from ShapeRenderer primitives (no assets), direct
    // into the overlay's ShapeRenderer (no per-frame FrameBuffer — viewport-safe, see order-4 note).
    // Consumed by RouteMapOverlayRenderer and the …render.routeicons painters.

    /** Side length (world units) of a node icon's drawing box, before the card's perspective scale. */
    public static final float ICON_BOX_SIZE = 46f;
    /** Line width used across icon strokes (rings, ticks, glyphs). */
    public static final float ICON_LINE_WIDTH = 2.5f;
    /** Fraction of the card height above centre where the icon sits (leaves room for the label). */
    public static final float ICON_CENTER_Y_FRACTION = 0.18f;
    /** REGION_GATE and BOSS are convergence set-pieces — their icons draw a touch larger. */
    public static final float ICON_GATE_SIZE_SCALE = 1.18f;
    public static final float ICON_BOSS_SIZE_SCALE = 1.14f;

    // ---- MYSTERY icon (segmented rotating ring around a "?") ----------------
    public static final float ICON_MYSTERY_SPIN_SPEED  = 0.8f;
    public static final int   ICON_MYSTERY_RING_DASHES = 8;

    // ---- EVENT icon (pulsing broadcast arc fan) ----------------------------
    public static final int   ICON_EVENT_ARC_COUNT  = 3;
    public static final float ICON_EVENT_PULSE_SPEED = 0.9f;

    // ---- REGION_GATE icon (radial locking bolts) ---------------------------
    public static final int   ICON_GATE_BOLT_COUNT = 6;

    // ---- BOSS icon (pulsing eye-socket ember) ------------------------------
    public static final float ICON_BOSS_EMBER_MIN   = 0.25f;
    public static final float ICON_BOSS_EMBER_MAX   = 0.85f;
    public static final float ICON_BOSS_EMBER_SPEED = 3.2f;

    // ---- Region crest on the title plate -----------------------------------
    public static final float CREST_CENTER_X = 60f;
    public static final float CREST_SIZE     = 42f;
    /** Left edge the region banner text starts at, clearing the crest badge. */
    public static final float REGION_TEXT_LEFT_X = 96f;

    // ---- Affix corner tag (order-9 affixes; slot reserved here) -------------
    public static final float AFFIX_TAG_SIZE  = 13f;
    public static final float AFFIX_TAG_INSET = 16f;

    // =========================================================================
    // TOUCH NAVIGATION & INTERACTION (order-6) — the two-thumb nav console UX.
    // =========================================================================
    // Consumed by RouteMapOverlayRenderer (gesture/hit-testing/feedback state) and World's
    // ROUTE_SELECT input branch. Platform law: touch only, no keyboard. Every descent is guarded
    // behind a deliberate tap-to-focus -> tap-ENGAGE two-step so a fat-finger can never dive.

    // ---- Selection model ---------------------------------------------------
    /**
     * When true, a second tap on an ALREADY-focused card engages it (shortcut). Default OFF for
     * safety so first-timers cannot dive by accident — ENGAGE is always the deliberate path.
     */
    public static final boolean DOUBLE_TAP_ENGAGE = false;
    /** Window within which a second tap on the focused card counts as the double-tap engage. */
    public static final float   DOUBLE_TAP_WINDOW_SECONDS = 0.35f;
    /**
     * Policy for the CANCEL button. Default false: the portal opened the map and leaving IS
     * committing, so the map must be resolved to descend — CANCEL is a no-op that keeps browsing.
     * If a future design lets the player step OFF the portal, flip this so CANCEL closes the overlay.
     */
    public static final boolean ALLOW_PORTAL_BACKOUT = false;

    // ---- Hit-testing (padded thumb targets) --------------------------------
    /** Extra world-unit padding added around a card's visual rect for a forgiving hit-rect. */
    public static final float NODE_HIT_PADDING = 16f;
    /** Minimum tappable size a card hit-rect is inflated to (mirrors TouchConstants thumb-min). */
    public static final float MIN_NODE_HIT_SIZE = 96f;

    // ---- Invalid-tap feedback ----------------------------------------------
    /** Duration of the red border flash shown when the player taps a locked/distant/disabled target. */
    public static final float INVALID_FLASH_SECONDS = 0.32f;
    /** Peak alpha of the invalid-tap red flash. */
    public static final float INVALID_FLASH_ALPHA   = 0.85f;

    // ---- Haptics (Android vibration; desktop no-ops) -----------------------
    /** Master gate for {@code Gdx.input.vibrate} feedback so the desktop dev build stays silent. */
    public static final boolean HAPTICS_ENABLED   = true;
    public static final int     HAPTIC_FOCUS_MS   = 12;   // light tick on focus
    public static final int     HAPTIC_ENGAGE_MS  = 34;   // firmer buzz on engage
    public static final int     HAPTIC_INVALID_MS = 22;   // dull buzz on invalid tap

    // ---- Console-thunk shake hook (reuses ImpactEffectSystem's screen shake) -
    public static final float ENGAGE_THUNK_MAGNITUDE = 5f;
    public static final float ENGAGE_THUNK_SECONDS   = 0.22f;

    // ---- Pan / framing (planning ahead on a small screen) ------------------
    /** Master gate for vertical drag-to-pan of the map viewport. */
    public static final boolean MAP_PAN_ENABLED = true;
    /**
     * Prefer the MAP/DETAIL framing toggle over pinch for v1 simplicity; pinch stays behind this flag
     * until it proves worth the fiddliness on a phone.
     */
    public static final boolean PINCH_ZOOM_ENABLED = false;
    /** World-unit finger travel before a touch is treated as a pan drag rather than a tap. */
    public static final float DRAG_START_THRESHOLD = 12f;
    /**
     * When a released pan sits within this of the home (decision-framing) offset, it snaps back so the
     * two decision layers are always reachable without scrolling.
     */
    public static final float PAN_SNAP_BACK_THRESHOLD = 70f;
    /** Ease rate (per second) the pan springs back toward home when released near it. */
    public static final float PAN_SNAP_BACK_SPEED = 9f;
    /** Momentum velocity decay (per second) after a pan flick — decelerates like a shoved schematic. */
    public static final float PAN_MOMENTUM_DECAY = 7f;
    /** Below this pan speed (world units/sec) momentum is considered spent and zeroed. */
    public static final float PAN_MOMENTUM_MIN_SPEED = 8f;
    /** Slack the pan may overscroll past its clamp before the spring pulls it back. */
    public static final float PAN_MAX_OVERSCROLL = 46f;
    /** Extra headroom kept above the topmost row so a panned-to-limit row is never edge-clipped. */
    public static final float PAN_TOP_MARGIN = 40f;
    /** How far past the map viewport band (bottom / top) a node centre may sit and still be drawn. */
    public static final float MAP_CULL_MARGIN_BOTTOM = 30f;
    /**
     * Trimmed from 96f (Issue 1): the old margin let a distant top row's centre climb to y≈736 — ABOVE
     * the title plate (bottom 648) — so a card could draw partly behind the plate/hazard trim. Capped
     * near the plate so no card centre can hide under the chrome. Distant rows are already shrunk by the
     * perspective fade, so the top lookahead row still fully fits.
     */
    public static final float MAP_CULL_MARGIN_TOP    = 24f;

    // ---- MAP / DETAIL framing toggle ---------------------------------------
    /** Vertical compression applied to layer spacing + node scale in "fit region" (MAP) framing. */
    public static final float MAP_FRAMING_COMPRESSION = 0.62f;
    public static final float FRAMING_TOGGLE_WIDTH  = 168f;
    public static final float FRAMING_TOGGLE_HEIGHT = 46f;
    /** Inset of the toggle button from the map viewport's top-right corner. */
    public static final float FRAMING_TOGGLE_MARGIN = 20f;
    public static final float FRAMING_TOGGLE_LABEL_SCALE = 0.8f;

    // ---- Fog / reveal policy -----------------------------------------------
    /**
     * v1 reveal policy: reveal every node whose depth falls in the CURRENT region on present, so the
     * act is a real plan while the NEXT region stays ghosted '?' until its REGION_GATE is crossed.
     */
    public static final boolean REVEAL_CURRENT_REGION = true;

    // ---- Long-range scanner (relic hook — order-14 defines the item) -------
    /** Whether the scanner reveal path is compiled in (the relic that triggers it lands in meta/order-14). */
    public static final boolean SCANNER_REVEAL_ENABLED = true;
    /** Duration of the scan-sweep wipe that un-ghosts scanner-revealed nodes. */
    public static final float   SCAN_SWEEP_SECONDS = 0.85f;

    // ---- ENGAGE gating visuals ---------------------------------------------
    /** Alpha of the ENGAGE button fill when no node is focused (disabled/dimmed). */
    public static final float ENGAGE_DISABLED_ALPHA = 0.14f;
    /** Base alpha of the ENGAGE button fill once a node is focused (before the pulse). */
    public static final float ENGAGE_ENABLED_ALPHA  = 0.42f;
    /** ENGAGE ready-pulse rate once a node is focused. */
    public static final float ENGAGE_PULSE_SPEED    = 5f;

    // ---- Forced-node ENGAGE copy -------------------------------------------
    public static final String ENGAGE_TEXT_DEFAULT   = "ENGAGE ▶";
    public static final String ENGAGE_TEXT_BOSS       = "DESCEND TO BOSS ▶";
    public static final String ENGAGE_TEXT_REGION_GATE = "BREACH BULKHEAD ▶";

    // =========================================================================
    // CALM NODE CONTENTS (order-8) — SUPPLY CACHE + MED-BAY / REST payoffs.
    // =========================================================================
    // These two nodes are the finite-ammo economy's release valves (roguelike_order_2): the
    // player routed HERE by choice, paying with a skipped ELITE reward, so both deliver an
    // unambiguous, deterministic-from-floorSeed payoff. Numbers tuned against
    // docs/balance-rule-system.txt — enough to justify skipping a reward node, never so much
    // it removes scarcity. Both nodes stay honestly SAFE so the map's icon language is
    // trustworthy (a MYSTERY "med-bay ambush" is a DIFFERENT node, order-9).

    // ---- SUPPLY CACHE (RouteNodeType.CACHE, profile "supply_cache") --------
    /** Ammo boxes stamped in the cargo bay, spread across the player's OWNED ammo types. */
    public static final int   CACHE_AMMO_BOXES      = 4;
    /** Guaranteed field medkits ('H') in a cache. */
    public static final int   CACHE_MEDKITS         = 1;
    /** Guaranteed stim-packs ('+') in a cache (a small top-up alongside the medkit). */
    public static final int   CACHE_STIMS           = 1;
    /** Guaranteed armour pickups in a cache (shard 'a' shallow, vest 'A' deep). */
    public static final int   CACHE_ARMOUR          = 1;
    /** Depth at/after which the cache armour drop upgrades from a shard 'a' to a vest 'A'. */
    public static final int   CACHE_ARMOUR_VEST_DEPTH = 6;
    /** Crates ('C') stamped as depot set-dressing. */
    public static final int   CACHE_CRATES          = 3;
    /** Lockers ('L') stamped as depot set-dressing. */
    public static final int   CACHE_LOCKERS         = 2;
    /** Per-room medkit chance the cache config bumps (a depot is medkit-rich). */
    public static final float CACHE_MEDKIT_CHANCE_PER_ROOM = 0.6f;
    /** Per-room armour chance the cache config bumps. */
    public static final float CACHE_ARMOUR_CHANCE_PER_ROOM = 0.4f;

    // ---- MED-BAY / REST (RouteNodeType.REST, profile "rest_medbay") --------
    /** The auto-doc heal-station prop tile. Reuses the holo-workstation 'W' (no new symbol); the
     *  one-shot heal interactivity is logic layered on that tile by World, not a new tile. */
    public static final char  HEAL_STATION_SYMBOL   = 'W';
    /** One-time auto-doc heal, as a fraction of the player's max HP. */
    public static final float REST_HEAL_FRACTION    = 0.35f;
    /**
     * Whether the auto-doc presents a 2-option choice (PATCH UP vs FIELD MOD) instead of a plain
     * heal. Default OFF: the permanent-boon system it would tie into does not exist yet, so REST is
     * an honest plain heal. Flip on once a boon system ships (levelup-stim-boon-choice-progression).
     */
    public static final boolean REST_OFFERS_CHOICE  = false;

    // =========================================================================
    // DANGER / GAMBLE NODE CONTENTS (order-9) — ELITE HOTZONE + MYSTERY.
    // =========================================================================
    // The "press your luck" side of the map that makes CACHE / REST (order-8) a real trade-off.
    // ELITE is a knowable, deliberate risk for a bigger reward; MYSTERY is an unknown box whose
    // expected value is deliberately NEUTRAL. Both are NodeLevelProfiles (order-7). Numbers tuned
    // against docs/balance-rule-system.txt: ELITE must be beatable with the resources a careful
    // player arrives with, punishing if routed here low — that IS the gamble the map sells.

    // ---- ELITE HOTZONE (RouteNodeType.ELITE, profile "elite_hotzone") ------
    /**
     * Multiplier on the raw depth-scaled encounter budget for an ELITE floor. Above 1 so the fight is
     * a mini-setpiece; the "fewer bodies, meaner enemies" fantasy is approximated by the planner
     * spending a larger budget on higher-Threat enemies. Never bypasses the depth ramp (order-3
     * invariant) — it scales the depth budget, it does not replace it. Clamped by EnemyBudgetOverride.
     */
    public static final float ELITE_BUDGET_SCALE       = 1.5f;
    /** Ammo boxes stamped in the gated vault (~1.5x a cache's {@link #CACHE_AMMO_BOXES}). */
    public static final int   ELITE_AMMO_BOXES         = 6;
    /** Guaranteed field medkits ('H') behind the vault. */
    public static final int   ELITE_MEDKITS            = 2;
    /** Guaranteed stim-packs ('+') behind the vault. */
    public static final int   ELITE_STIMS              = 1;
    /** Guaranteed armour pickups behind the vault (shard 'a' shallow, vest 'A' deep). */
    public static final int   ELITE_ARMOUR             = 1;
    /** Depth at/after which the ELITE armour drop upgrades from a shard 'a' to a vest 'A'. */
    public static final int   ELITE_ARMOUR_VEST_DEPTH  = 4;
    /** Weapon-rack ('=') props stamped in the vault as the visual "worth guarding" promise. */
    public static final int   ELITE_WEAPON_RACKS       = 1;
    /** Column ('P') cover props scattered so the arena reads as a defensible set-piece. */
    public static final int   ELITE_COVER_COLUMNS      = 2;
    /** Radioactive-barrel weight the ELITE config bumps to (hazard-forward vs the default 0.22). */
    public static final float ELITE_RADIOACTIVE_BARREL_WEIGHT = 0.34f;
    /** Whether an ELITE floor arrives with the emergency red-alert pulse already lit. */
    public static final boolean ELITE_RED_ALERT        = true;
    /** First-frame sting shown on entering an ELITE floor. */
    public static final String  ELITE_STING_TEXT       = "CONTAINMENT BREACH";

    // ---- ELITE node affixes (rolled at map-gen via NodeAffixRegistry) -------
    // Each affix is a bundle of TYPED modifiers routed through existing enemy / hazard / balance
    // systems — no bespoke per-affix code path beyond applying the modifiers. Where a referenced
    // system is not yet implemented the affix degrades to the nearest available effect.
    public static final String AFFIX_IRRADIATED_ID    = "irradiated";
    public static final String AFFIX_OVERCLOCKED_ID   = "overclocked";
    public static final String AFFIX_SWARM_ID         = "swarm";
    public static final String AFFIX_FORTIFIED_ID     = "fortified";
    public static final String AFFIX_VOLATILE_ID      = "volatile";

    /** Uniform pool weight each v1 affix carries (kept equal until a design reason splits them). */
    public static final float AFFIX_WEIGHT_DEFAULT    = 1f;
    /** Separator joining a node's base map hint to its rolled affix label (e.g. "… — OVERCLOCKED"). */
    public static final String AFFIX_HINT_SEPARATOR   = " — ";
    /** IRRADIATED: extra radioactive-barrel weight on top of the ELITE base. */
    public static final float AFFIX_IRRADIATED_BARREL_WEIGHT_BONUS = 0.12f;
    /** IRRADIATED: extra 'g' radioactive barrels stamped as hazard pools. */
    public static final int   AFFIX_IRRADIATED_EXTRA_BARRELS = 3;
    /** OVERCLOCKED: budget multiplier (enemies "hit harder" — approximated as more Threat spent). */
    public static final float AFFIX_OVERCLOCKED_BUDGET_MULT = 1.2f;
    /** SWARM: budget multiplier (the one exception to fewer-bodies — a crowd-control test). */
    public static final float AFFIX_SWARM_BUDGET_MULT = 1.5f;
    /** FORTIFIED: extra cover columns 'P' (a slugfest of angles). */
    public static final int   AFFIX_FORTIFIED_EXTRA_COLUMNS = 3;
    /** FORTIFIED: extra armour pickups (an eHP bump the player can also claim). */
    public static final int   AFFIX_FORTIFIED_EXTRA_ARMOUR = 1;
    /** VOLATILE: extra explosive barrels 'E' — the arena itself is a weapon for both sides. */
    public static final int   AFFIX_VOLATILE_EXTRA_BARRELS = 4;

    // ---- MYSTERY (RouteNodeType.MYSTERY, profile "mystery") ----------------
    // The outcome is rolled at ENTRY from the node's fixed nodeSeed (deterministic per run/seed), so
    // a scanner reveals a category TONE but never the exact result. Weights sum to 100 and are tuned
    // so expected value ≈ a normal combat floor — a genuine coin-flip, not a strictly-good pick.
    public static final int MYSTERY_WEIGHT_VAULT        = 18;
    public static final int MYSTERY_WEIGHT_SECRET_WARREN = 18;
    public static final int MYSTERY_WEIGHT_TRAP_GAUNTLET = 18;
    public static final int MYSTERY_WEIGHT_AMBUSH        = 18;
    public static final int MYSTERY_WEIGHT_LORE_SIGNAL   = 14;
    public static final int MYSTERY_WEIGHT_MALFUNCTION   = 14;

    /** First-frame reveal sting stamped by the MYSTERY meta-profile on entry. */
    public static final String MYSTERY_STING_TEXT = "SECTOR SCAN FAILED — UNKNOWN CONTACT";
    /** Loot boxes in a MYSTERY vault jackpot (the dream pull — richer than an ELITE vault). */
    public static final int   MYSTERY_VAULT_AMMO_BOXES = 7;
    /** Explosive/radioactive barrels the TRAP GAUNTLET degrade stamps (traps -> barrels fallback). */
    public static final int   MYSTERY_TRAP_BARRELS     = 6;
    /** Loot boxes waiting at the end of a TRAP GAUNTLET (modest reward for patience). */
    public static final int   MYSTERY_TRAP_AMMO_BOXES  = 3;
    /** Medkits waiting at the exit of a TRAP GAUNTLET. */
    public static final int   MYSTERY_TRAP_MEDKITS     = 1;
    /** Extra keycard-gated loot the SECRET WARREN promises (rewards those who search). */
    public static final int   MYSTERY_WARREN_AMMO_BOXES = 4;
    public static final int   MYSTERY_WARREN_MEDKITS    = 1;

    // =========================================================================
    // NARRATIVE / EVENT NODES + REGION THEMING (order-10)
    // =========================================================================
    // The two "texture the run" additions that turn the map from a difficulty selector into a
    // JOURNEY: EVENT nodes (small curated non-combat rooms with a choice) and REGION THEMING (the
    // act-band identity + the ceremonial REGION_GATE bulkhead). Both extend by registration — a new
    // event / region is data + a registry line, never a core edit.

    // ---- EVENT node (RouteNodeType.EVENT, profile "event") -----------------
    /**
     * The event interactable prop tile. Reuses the holo-workstation 'W' (no new tile symbol); the
     * "step adjacent to open the choice overlay" interactivity is logic layered on that tile by
     * World, exactly as the MED-BAY auto-doc reuses the same glyph for its heal station.
     */
    public static final char   EVENT_STATION_SYMBOL     = 'W';
    /** Budget scale for an EVENT floor — near-empty; a beat of story, not a fight. */
    public static final float  EVENT_BUDGET_SCALE       = 0.15f;
    /** First-frame sting shown on entering an EVENT floor (before the interactable is reached). */
    public static final String EVENT_STING_TEXT         = "SIGNAL DETECTED";
    /** XP awarded by an event choice tagged {@code EventEffect.grantXp} at its small tier. */
    public static final int    EVENT_XP_SMALL           = 25;
    /** XP awarded by an event choice tagged {@code EventEffect.grantXp} at its large tier. */
    public static final int    EVENT_XP_LARGE           = 60;
    /** Ammo boxes an event choice hands out (spread across owned ammo types, cache-style). */
    public static final int    EVENT_AMMO_BOXES         = 3;
    /** Next-floor encounter-budget nudge an event choice may apply (e.g. escort +10%). */
    public static final float  EVENT_NEXT_FLOOR_BUDGET_BONUS = 0.10f;
    /** Fraction of a mimic-ambush roll's [0,1) space that resolves as loot (else an ambush sting). */
    public static final float  EVENT_STASH_LOOT_CHANCE  = 0.60f;

    // ---- REGION GATE (RouteNodeType.REGION_GATE, profile "region_gate") ----
    /** Budget scale for the ceremonial airlock floor — a pacing breath, almost no combat. */
    public static final float  GATE_BUDGET_SCALE        = 0.10f;
    /** Prefix the gate announce prepends to the entered region's display name ("ENTERING: …"). */
    public static final String GATE_ANNOUNCE_PREFIX     = "ENTERING: ";

    // ---- Region ambience / theming registry (RegionAmbienceRegistry) -------
    // Per-region IDENTITY data future systems read: crest + overlay tint (order-4/5), the wall
    // palette its generators lean on, the enemy faction that dominates, ambience hooks (red-alert
    // frequency, lighting mood, music id — no audio assets added here), the closing boss, and the
    // REGION_GATE announce flavour. Keyed by RouteRegion.themeId. NOTHING hard-codes the region list.

    // Wall palettes (chars from docs/tile-symbols.txt) each region's generators lean toward.
    public static final String REGION_A_WALL_PALETTE = "xck";   // clean industrial steel
    public static final String REGION_B_WALL_PALETTE = "MNDQ";  // clinical lab white-green
    public static final String REGION_C_WALL_PALETTE = "UhXj";  // amber-hot reactor
    public static final String REGION_D_WALL_PALETTE = "GF";    // gore + force-field corruption

    // Enemy faction ids that dominate each region's spawns (descriptive hook for EnemyManager).
    public static final String REGION_A_FACTION = "baseline_demons";
    public static final String REGION_B_FACTION = "lab_horrors";
    public static final String REGION_C_FACTION = "reactor_brutes";
    public static final String REGION_D_FACTION = "necrotic";

    // Lighting mood ids (hook the lighting/red-alert systems can read; no assets added here).
    public static final String REGION_A_LIGHTING = "cool_steel";
    public static final String REGION_B_LIGHTING = "clinical_white";
    public static final String REGION_C_LIGHTING = "amber_hot";
    public static final String REGION_D_LIGHTING = "corrupted_violet";

    // Music ids (placeholders — no audio assets added here; the audio system reads these later).
    public static final String REGION_A_MUSIC = "music_outer_facility";
    public static final String REGION_B_MUSIC = "music_research_wing";
    public static final String REGION_C_MUSIC = "music_reactor_depths";
    public static final String REGION_D_MUSIC = "music_the_breach";

    // Ambient red-alert frequency per region (0..1): how often the emergency pulse flares. Escalates
    // with depth so the descent FEELS more hostile the deeper the player goes.
    public static final float  REGION_A_RED_ALERT_FREQUENCY = 0.00f;
    public static final float  REGION_B_RED_ALERT_FREQUENCY = 0.10f;
    public static final float  REGION_C_RED_ALERT_FREQUENCY = 0.35f;
    public static final float  REGION_D_RED_ALERT_FREQUENCY = 0.55f;

    // Boss ids that close each region (placeholders wired to the boss system as it grows).
    public static final String REGION_A_BOSS = "boss_gatekeeper";
    public static final String REGION_B_BOSS = "boss_prime_specimen";
    public static final String REGION_C_BOSS = "boss_reactor_core";
    public static final String REGION_D_BOSS = "boss_breach_avatar";

    // REGION_GATE announce flavour lines (the ceremonial "chapter break" copy per region).
    public static final String REGION_A_GATE_FLAVOUR = "Bulkhead sealed. The facility proper lies ahead.";
    public static final String REGION_B_GATE_FLAVOUR = "Quarantine breached. Research containment ahead.";
    public static final String REGION_C_GATE_FLAVOUR = "Coolant failing. Reactor levels ahead — mind the heat.";
    public static final String REGION_D_GATE_FLAVOUR = "Reality thins. You have gone too deep.";
}
