package ge.tbegvadze.toon3d.util;

/** Rendering constants — camera-plane raycasting, floor/ceiling, alert pulse, props, columns, the region wall atlas runtime. */
public final class RenderConstants {

    private RenderConstants() {}

    // Door minimap colours
    public static final float DOOR_MINIMAP_CLOSED_R = 0.60f;
    public static final float DOOR_MINIMAP_CLOSED_G = 0.60f;
    public static final float DOOR_MINIMAP_CLOSED_B = 0.70f;
    public static final float DOOR_MINIMAP_OPEN_R   = 0.20f;
    public static final float DOOR_MINIMAP_OPEN_G   = 0.80f;
    public static final float DOOR_MINIMAP_OPEN_B   = 0.85f;
    // Locked-door minimap tint strength — how far the base door colour lerps toward
    // the required keycard colour (0 = no tint, 1 = pure keycard colour).
    public static final float DOOR_MINIMAP_LOCK_TINT_FRACTION = 0.55f;

    // Mini-map overlay marker palette — props, hazards, pickups, exit, and enemy threat chevrons.
    // Keycard pickups and locked-door tinting reuse KEYCARD_AURA_RED/YELLOW/BLUE below.
    // Mini-map frame and layers (visual-identity-order-4 R11): every other marker colour now comes from
    // ArtDirection / CombatPalette; the barrel prop square and ammo pickup keep their hazard / ammo colours.
    public static final float MINIMAP_FRAME_FILL_ALPHA = 0.70f;
    public static final float MINIMAP_WALL_ALPHA       = 0.85f;
    public static final float MINIMAP_GRID_ALPHA       = 0.35f;
    public static final float MINIMAP_GRID_LINE_THICKNESS = 1f;
    public static final float MINIMAP_BARREL_R         = 1.00f;
    public static final float MINIMAP_BARREL_G         = 0.48f;
    public static final float MINIMAP_BARREL_B         = 0.09f;
    public static final float MINIMAP_AMMO_PICKUP_R    = 0.75f;
    public static final float MINIMAP_AMMO_PICKUP_G    = 0.45f;
    public static final float MINIMAP_AMMO_PICKUP_B    = 0.10f;
    // Shop (vending machine, '@') marker — bright glowing CREDITS_GOLD (LevelRenderer), distinct from
    // generic props so it reads at a glance on the mini-map instead of blending in with grey obstacles.
    // Outer glow square drawn under the pulsing shop marker core, sized as a multiple of
    // MINI_MAP_PROP_SIZE and rendered dimmer (opaque) to read as a soft halo without GL blending.
    // Kept below CELL_SIZE / PROP_SIZE (~1.67) so the glow never exceeds the tile's own bounds —
    // markers here aren't clip-rectangled, so anything larger than the tile could bleed past the
    // mini-map border on edge tiles.
    public static final float MINIMAP_SHOP_GLOW_SCALE      = 1.5f;
    public static final float MINIMAP_SHOP_GLOW_BRIGHTNESS = 0.45f;
    // Pulse brightness range for the exit marker and enemy threat chevrons (see GameMath.pulseMultiplier)
    public static final float MINIMAP_PULSE_MIN_BRIGHTNESS = 0.65f;
    public static final float MINIMAP_PULSE_MAX_BRIGHTNESS = 1.00f;

    // Keycard door glow — ambient colour tint for walls near locked doors
    public static final int   KEYCARD_DOOR_GLOW_RADIUS_TILES = 3;
    public static final float KEYCARD_DOOR_GLOW_INTENSITY    = 0.25f;

    // Camera-plane raycasting — see docs/dda-raycasting-math.txt for formula derivations
    // CAMERA_PLANE_SCALE = tan(FOV/2); = 1.0 for 90° FOV
    public static final float CAMERA_PLANE_SCALE              = (float) Math.tan(
            Constants.PLAYER_FIELD_OF_VIEW_RADIANS / 2.0);
    // The 3D view's internal ray-column count is now runtime-switchable — see util.RenderResolution and
    // WallRenderer.setRenderResolution()/getProjectionColumnCount()/getColumnWidth(), the SINGLE runtime
    // authority every scene renderer (Prop, Enemy, ShopMachine, EnemyAttackEffectSystem) reads each frame.
    // Vertical extent stays fixed at WORLD_HEIGHT regardless of the setting (only column COUNT and the
    // floor backdrop vary — see RenderResolution's derivation comments).
    public static final int   WALL_PROJECTION_SCREEN_HEIGHT   = Constants.WORLD_HEIGHT;

    // Render resolution setting (util.RenderResolution) — how many ray columns the 3D view casts,
    // independent of the fixed 1280x720 world/UI space it is composited into. HD reproduces the
    // historic fixed 1280-column behaviour byte-identically; FULL_HD raises the column count.
    // RENDER_RESOLUTION_MAX_COLUMNS sizes any array/SpriteBatch that must cover the largest tier.
    public static final int    RENDER_RESOLUTION_HD_COLUMNS      = Constants.WORLD_WIDTH;
    public static final int    RENDER_RESOLUTION_FULL_HD_COLUMNS = 1920;
    public static final int    RENDER_RESOLUTION_MAX_COLUMNS     = RENDER_RESOLUTION_FULL_HD_COLUMNS;
    // Persisted setting key. Stable once shipped — it is baked into saves (additive key, no
    // SCHEMA_VERSION bump needed; see the SFX-volume-setting precedent).
    public static final String RENDER_RESOLUTION_SETTING_KEY     = "render.resolution";
    // shade = max(MIN, 1 / (1 + d² × FALLOFF)). Steeper falloff increases near/far contrast.
    // At d=5: shade≈0.29  At d=10: shade≈0.091  MIN_BRIGHTNESS prevents full-black silhouettes.
    public static final float WALL_SHADING_FALLOFF            = 0.10f;
    public static final float WALL_SHADE_MIN_BRIGHTNESS       = 0.12f;
    // Enemy sprites use a higher min so distant enemies never read as wall-coloured blobs.
    public static final float ENEMY_SPRITE_SHADE_MIN_BRIGHTNESS = 0.35f;
    // N/S faces darkened by this factor vs E/W faces to simulate directional lighting
    public static final float HORIZONTAL_FACE_SHADE_MULTIPLIER = 0.7f;

    // Floor & Ceiling texture rendering — see docs/dda-raycasting-math.txt and docs/art-direction.txt.
    // GPU floor/ceiling (render-performance-full-hd): perspective tile quads to the 15-tile clamp plus one span
    // band beyond it, sampled at the device's native resolution. The row count below is the tier's row model
    // (floor half + mirrored ceiling half, the historic backdrop heights): it fixes every per-row table (shade,
    // tint, material level) and the band's rows. Read through RenderResolution.getFloorCeilingRowCount().
    public static final int    RENDER_RESOLUTION_HD_FLOOR_CEILING_ROWS      = 180;
    public static final int    RENDER_RESOLUTION_FULL_HD_FLOOR_CEILING_ROWS = 540;
    /** R3: a span longer than this many tiles is split so every UV stays small (mediump varyings). */
    public static final float  FLOOR_SPAN_MAX_TILES             = 8f;
    /** R8: the span batch — the SpriteBatch maximum; it auto-flushes beyond that, never overflows. */
    public static final int    FLOOR_SPAN_BATCH_SIZE            = 8191;
    /** Hard cap on grid crossings walked per row (a row crosses at most ~45 at the 15-tile clamp, FOV 90). */
    public static final int    FLOOR_SPAN_MAX_CROSSINGS_PER_ROW = 512;
    /** R6: additive layers for channel factors above 1 (tint excess + muzzle light); layer L adds min(1, f-1-L). */
    public static final int    FLOOR_ADDITIVE_MAX_LAYERS        = 2;
    /** Perspective tile plane: the near clip depth in tiles (well short of the nearest visible floor, 1 tile). */
    public static final float  FLOOR_PLANE_NEAR_TILES           = 0.05f;
    /** Largest tile lattice per frame, per axis (the 90-degree wedge to the 15-tile clamp spans 31 tiles). */
    public static final int    FLOOR_TILE_LATTICE_MAX_EXTENT    = 48;
    /** Depth samples (0..clamp) used once per floor to find each channel's largest tint for the texture bake. */
    public static final int    FLOOR_BAKE_TINT_SAMPLES          = 60;
    /** The far span band starts this many rows below the clamp row; the tiles cover the overlap (no seam). */
    public static final int    FLOOR_BAND_OVERLAP_ROWS          = 1;
    // FLOOR_CAMERA_Z = 0.5 → camera exactly centred (horizon at screen centre)
    public static final float  FLOOR_CAMERA_Z                   = 0.5f;
    public static final float  FLOOR_SHADING_FALLOFF            = 0.05f;
    public static final float  FLOOR_MAX_VISIBLE_DISTANCE_CELLS = 15.0f;
    /** Edge length in texels of every floor/ceiling surface material (power of two, tileable). */
    public static final int    SURFACE_MATERIAL_SIZE            = 64;
    /** Mip chain length per material: level 0 = 64, then 32, 16, 8 (2x2 box filtered). */
    public static final int    SURFACE_MATERIAL_MIP_LEVELS      = 4;
    /** Multiplies the texel footprint before the level is chosen; above 1 blurs earlier, below 1 stays crisp longer. */
    public static final float  SURFACE_MATERIAL_MIP_BIAS        = 1.0f;
    // Placeholder procedural textures generated at runtime when these assets are absent

    // Emergency Red Alert Pulse — spinning UAC beacon tint (period ≈ 4s = 2π / SPEED)
    public static final float ALERT_PULSE_SPEED_RADIANS_PER_SECOND = 1.57f;
    public static final float ALERT_RED_R = 0.9f;
    public static final float ALERT_RED_G = 0.05f;
    public static final float ALERT_RED_B = 0.05f;
    public static final float ALERT_CEILING_TINT_STRENGTH = 0.55f;
    public static final float ALERT_FLOOR_TINT_STRENGTH   = 0.25f;
    public static final float ALERT_WALL_RED_BOOST         = 0.35f;
    public static final float ALERT_WALL_GB_DAMPEN         = 0.30f;

    // Prop sprites — billboard sprites on floor tiles
    public static final float  MAX_PROP_DRAW_DISTANCE_TILES      = 12f;
    // Minimum positive depth before a prop is considered "in front" of the player
    public static final float  PROP_BEHIND_PLAYER_EPSILON_TILES  = 0.1f;
    public static final String PROP_BARREL_RADIOACTIVE_PATH      = "textures/lab/barrel_radioactive.png";

    // Sub-cell columns ('P' tiles) — see docs/dda-raycasting-math.txt for ray-circle math
    // COLUMN_RADIUS_TILES = 0.25 → column 50% as wide as cell, leaving 0.25-tile gaps each side
    public static final float COLUMN_RADIUS_TILES       = 0.25f;
    // COLUMN_SHADE_MIN = darkest Lambert multiplier on column sides (front face always reaches 1.0)
    public static final float COLUMN_SHADE_MIN          = 0.50f;
    // Light direction at 45° NE: (0.707, 0.707) — even lighting across all four cardinal walls
    public static final float COLUMN_LIGHT_DIRECTION_X  = 0.70710678f;
    public static final float COLUMN_LIGHT_DIRECTION_Y  = 0.70710678f;

    // Prop/decal height multipliers previously inlined in PropRenderer.propHeightMultiplier() —
    // hoisted here (project rule: no inline values) so the tileset sprite registry and the renderer
    // reference the SAME literal. Values unchanged.
    public static final float PROP_HEIGHT_RADIOACTIVE_BARREL   = 0.65f; // 'g' radioactive barrel
    public static final float PROP_HEIGHT_EXPLOSIVE_BARREL     = 0.65f; // 'E' explosive barrel
    public static final float PROP_HEIGHT_TERMINAL             = 0.75f; // 'T' computer terminal
    public static final float PROP_HEIGHT_LOCKER               = 0.90f; // 'L' locker
    public static final float PROP_HEIGHT_CRATE                = 0.50f; // 'C' crate
    public static final float PROP_HEIGHT_CORPSE               = 0.35f; // 'm' corpse decal
    // The corpse decal symbol (docs/tile-symbols.txt): where a generated creature's REMAINS stand on it, the
    // decal is not drawn (generated-enemy-art order 3).
    public static final char  CORPSE_DECAL_SYMBOL              = 'm';
    public static final float PROP_HEIGHT_BLOOD_ALT            = 0.22f; // 's' blood stain (alt) decal
    public static final float PROP_HEIGHT_BLOOD                = 0.18f; // '.' blood stain decal
    public static final float PROP_HEIGHT_OIL                  = 0.18f; // 'O' oil/fluid pool decal

    // New prop height multipliers (relative to full wall stripe)
    public static final float PROP_CAMERA_HEIGHT               = 0.80f; // '#' security camera
    public static final float PROP_GENERATOR_HEIGHT            = 0.85f; // '%' power generator / reactor
    public static final float PROP_BIOPOD_HEIGHT               = 0.90f; // '&' bio-pod / specimen tank
    public static final float PROP_RACK_HEIGHT                 = 0.70f; // '=' weapon rack
    public static final float PROP_VENDOR_HEIGHT               = 0.88f; // '@' vending / supply dispenser
    public static final float PROP_HEIGHT_SPECIMEN_TANK        = 0.85f; // 'I' specimen tank
    public static final float PROP_HEIGHT_HOLO_WORKSTATION     = 0.70f; // 'W' holo-workstation
    public static final float PROP_HEIGHT_AICORE_NODE          = 0.95f; // 'J' AI core node
    public static final float PROP_HEIGHT_ENERGY_SCORCH        = 0.18f; // 'e' energy scorch decal
    // Terrain hazards (idea 4, Pillar 3) — animated volumetric effects, NOT flat decals. Both
    // fill the full tile footprint (see HAZARD_CELL_WIDTH_FILL) instead of a small centred patch.
    // Fire stands tall so spiky flames lick up toward the ceiling; the toxic cloud billows to a
    // medium height so it reads as a hanging gas cloud rather than a puddle.
    public static final float PROP_HEIGHT_HAZARD_FIRE          = 0.92f; // 'i' fire hazard tile
    public static final float PROP_HEIGHT_HAZARD_TOXIC         = 0.60f; // 'q' toxic hazard pool

    // Hazards are billboarded at this fraction of a full wall stripe's on-screen width so the
    // effect spans the whole cell footprint (1.0 = exactly one tile wide), decoupled from the
    // source texture's aspect ratio.
    public static final float HAZARD_CELL_WIDTH_FILL           = 1.0f;

    // Animated hazard sprites: each hazard cycles through this many procedurally-generated frames
    // at this rate. A per-tile phase offset (derived from tile coordinates) keeps neighbouring
    // tiles from flickering in lockstep.
    public static final int   HAZARD_ANIMATION_FRAME_COUNT     = 8;
    public static final float HAZARD_ANIMATION_FPS             = 12f;

    // Tile-Based Ambient Lighting — floor tiles ' ', 'l', 'u', 'f' carry brightness multipliers
    // finalShade = clamp(distanceShade * directionalMultiplier * tileBrightness, 0, MAX_LIGHTING_SHADE)
    // ' ' (space) = lit bright floor (1.55×); 'l' = normal floor (1.0×);
    // 'u' = unlit dark floor (0.55×); 'f' flickering uses two incommensurate oscillators (1.0× and 5.7× FLICKER_NOISE_FREQUENCY)
    public static final char  LIT_TILE_CHAR              = ' ';
    public static final char  NORMAL_TILE_CHAR           = 'l';
    public static final char  UNLIT_TILE_CHAR            = 'u';
    public static final char  FLICKERING_TILE_CHAR       = 'f';
    public static final float BASE_TILE_BRIGHTNESS       = 1.00f;
    public static final float LIT_TILE_BRIGHTNESS        = 1.55f;
    public static final float UNLIT_TILE_BRIGHTNESS      = 0.55f;
    public static final float FLICKER_MIN_BRIGHTNESS     = 0.20f;
    public static final float FLICKER_MAX_BRIGHTNESS     = 1.70f;
    public static final float FLICKER_NOISE_FREQUENCY    = 0.7f;
    public static final float FLICKER_FAILURE_THRESHOLD  = 0.35f;
    public static final float MAX_LIGHTING_SHADE         = 1.00f;

    // Credit chip ground pickups — bob animation and per-tier aura glow
    public static final float CREDIT_PICKUP_HEIGHT_FRACTION        = 0.28f;
    public static final float CREDIT_PICKUP_BOB_SPEED              = 2.4f;
    public static final float CREDIT_PICKUP_BOB_AMPLITUDE_FRACTION = 0.12f;
    public static final float CREDIT_PICKUP_PHASE_STEP             = 0.45f;
    public static final float CREDIT_PICKUP_CENTER_HEIGHT_FRACTION = 0.38f;
    // Aura — SMALL tier (dollar green — single banknote); RGB matches ItemType.CREDIT_SMALL glyph color
    public static final float CREDIT_AURA_SMALL_R           = 0.25f;
    public static final float CREDIT_AURA_SMALL_G           = 0.72f;
    public static final float CREDIT_AURA_SMALL_B           = 0.25f;
    public static final float CREDIT_AURA_SMALL_RADIUS      = 1.4f;
    public static final float CREDIT_AURA_SMALL_BASE_ALPHA  = 0.35f;
    public static final float CREDIT_AURA_SMALL_PULSE_AMP   = 0.12f;
    public static final float CREDIT_AURA_SMALL_PULSE_SPEED = 2.0f;
    // Aura — MEDIUM tier (bright green — bill stack); RGB matches ItemType.CREDIT_MEDIUM glyph color
    public static final float CREDIT_AURA_MEDIUM_R           = 0.20f;
    public static final float CREDIT_AURA_MEDIUM_G           = 0.85f;
    public static final float CREDIT_AURA_MEDIUM_B           = 0.20f;
    public static final float CREDIT_AURA_MEDIUM_RADIUS      = 1.8f;
    public static final float CREDIT_AURA_MEDIUM_BASE_ALPHA  = 0.45f;
    public static final float CREDIT_AURA_MEDIUM_PULSE_AMP   = 0.16f;
    public static final float CREDIT_AURA_MEDIUM_PULSE_SPEED = 2.4f;
    // Aura — LARGE tier (neon lime — three-stack vault); RGB matches ItemType.CREDIT_LARGE glyph color
    public static final float CREDIT_AURA_LARGE_R           = 0.35f;
    public static final float CREDIT_AURA_LARGE_G           = 1.00f;
    public static final float CREDIT_AURA_LARGE_B           = 0.20f;
    public static final float CREDIT_AURA_LARGE_RADIUS      = 2.4f;
    public static final float CREDIT_AURA_LARGE_BASE_ALPHA  = 0.55f;
    public static final float CREDIT_AURA_LARGE_PULSE_AMP   = 0.22f;
    public static final float CREDIT_AURA_LARGE_PULSE_SPEED = 2.8f;

    // Keycard pickup aura ('r'/'y'/'b') — additive halo drawn behind the floating card so each
    // security tier reads at a glance. Mirrors the credit-chip aura pipeline (radial glow texture,
    // additive blend, sin-wave pulse). Tint matches the keycard colour and the door it unlocks.
    public static final float KEYCARD_AURA_SIZE_MULTIPLIER  = 2.6f;   // aura diameter relative to card height
    public static final float KEYCARD_AURA_BASE_ALPHA       = 0.42f;  // additive halo base opacity
    public static final float KEYCARD_AURA_PULSE_AMPLITUDE  = 0.18f;  // sin-wave opacity swing
    public static final float KEYCARD_AURA_PULSE_SPEED      = 3.0f;   // radians/sec pulse rate
    public static final float KEYCARD_AURA_RED_R    = 1.00f;
    public static final float KEYCARD_AURA_RED_G    = 0.18f;
    public static final float KEYCARD_AURA_RED_B    = 0.16f;

    public static final float KEYCARD_AURA_YELLOW_R = 1.00f;
    public static final float KEYCARD_AURA_YELLOW_G = 0.86f;
    public static final float KEYCARD_AURA_YELLOW_B = 0.18f;

    public static final float KEYCARD_AURA_BLUE_R   = 0.22f;
    public static final float KEYCARD_AURA_BLUE_G   = 0.58f;
    public static final float KEYCARD_AURA_BLUE_B   = 1.00f;

    // Level transitions — exit portal tile ('>' char)
    // STAIRS_DOWN_CHAR: classic roguelike glyph; exactly one per procedurally generated floor.
    public static final char  STAIRS_DOWN_CHAR                   = '>';
    // Full-height billboard — stands exactly as tall as walls, a landmark visible down any corridor.
    public static final float PORTAL_SPRITE_HEIGHT               = 1.0f;
    public static final float LEVEL_TRANSITION_FADE_OUT_SECONDS  = 0.30f;
    public static final float LEVEL_TRANSITION_FADE_IN_SECONDS   = 0.30f;
    public static final int   STARTING_DEPTH                     = 1;

    // -------------------------------------------------------------------------
    // Shop — UAC Fabricator vending machine animated billboard (shop_order_6)
    // -------------------------------------------------------------------------
    // Tier-A high-resolution body baked ONCE into a Pixmap→Texture (portrait cabinet, width = 0.5×height).
    public static final int   SHOP_SPRITE_TEXTURE_HEIGHT         = 512;
    public static final int   SHOP_SPRITE_TEXTURE_WIDTH          = 256;
    // Billboard body height as a fraction of a full wall stripe (ground-anchored, no float lift).
    public static final float SHOP_SPRITE_HEIGHT_FRACTION        = 0.92f;

    // Tier-B animated overlay pulse rates (radians/second).
    public static final float SHOP_STATUS_PULSE_SPEED            = 2.0f;
    public static final float SHOP_WINDOW_GLOW_PULSE_SPEED       = 1.4f;
    public static final float SHOP_HOLO_FLICKER_SPEED            = 8.0f;
    public static final float SHOP_AURA_PULSE_SPEED              = 1.2f;

    // Ambient aura behind the whole unit (additive, tile-brightness aware).
    public static final float SHOP_AURA_RADIUS_MULTIPLIER        = 1.6f;   // diameter vs sprite width
    public static final float SHOP_AURA_BASE_ALPHA               = 0.24f;
    public static final float SHOP_AURA_PULSE_AMPLITUDE          = 0.06f;

    // Product-window breathing glow (cyan fabrication energy behind the glass).
    public static final float SHOP_WINDOW_GLOW_SIZE_FRACTION     = 0.44f;  // diameter vs sprite height
    public static final float SHOP_WINDOW_GLOW_BASE_ALPHA        = 0.34f;
    public static final float SHOP_WINDOW_GLOW_PULSE_AMPLITUDE   = 0.16f;

    // Status light halo — colour set by machine state (in-stock / low / depleted).
    public static final float SHOP_STATUS_SIZE_FRACTION          = 0.12f;  // diameter vs sprite height
    public static final float SHOP_STATUS_BASE_ALPHA             = 0.55f;
    public static final float SHOP_STATUS_PULSE_AMPLITUDE        = 0.40f;

    // Flickering holographic sign floating above the unit (marks it interactable across a room).
    public static final float SHOP_HOLO_HEIGHT_FRACTION          = 0.16f;  // sign height vs sprite height
    public static final float SHOP_HOLO_LIFT_FRACTION            = 0.12f;  // gap above sprite top
    public static final float SHOP_HOLO_BASE_ALPHA               = 0.42f;
    public static final float SHOP_HOLO_PULSE_AMPLITUDE          = 0.30f;

    // One-shot DISPENSE animation on a successful purchase (shop_order_5 trigger).
    public static final float SHOP_DISPENSE_DURATION_SECONDS     = 0.5f;
    public static final float SHOP_DISPENSE_FLASH_ALPHA          = 0.85f;  // peak window flash opacity

    // Pre-generated Tier-B texture sizes.
    public static final int   SHOP_GLOW_TEXTURE_SIZE             = 64;     // radial glow (aura/window/status)
    public static final int   SHOP_HOLO_TEXTURE_WIDTH           = 128;
    public static final int   SHOP_HOLO_TEXTURE_HEIGHT          = 64;

    // Sprite layout — overlay anchor points expressed as fractions of the baked body so Tier-B
    // layers register onto the Tier-A detail. FROM_TOP because the bake uses Pixmap y-down coords.
    public static final float SHOP_WINDOW_CENTER_FRACTION_FROM_TOP  = 0.30f;
    public static final float SHOP_STATUS_CENTER_FRACTION_FROM_TOP  = 0.47f;
    public static final float SHOP_STATUS_CENTER_FRACTION_FROM_LEFT = 0.74f;

    // Status light colours by machine state.
    public static final float SHOP_STATUS_INSTOCK_R  = 0.30f, SHOP_STATUS_INSTOCK_G  = 1.00f, SHOP_STATUS_INSTOCK_B  = 0.42f;
    public static final float SHOP_STATUS_LOW_R      = 1.00f, SHOP_STATUS_LOW_G      = 0.72f, SHOP_STATUS_LOW_B      = 0.14f;
    public static final float SHOP_STATUS_DEPLETED_R = 0.55f, SHOP_STATUS_DEPLETED_G = 0.10f, SHOP_STATUS_DEPLETED_B = 0.10f;

    // Cyan window glow + holographic sign tint.
    public static final float SHOP_WINDOW_GLOW_R = 0.35f, SHOP_WINDOW_GLOW_G = 0.85f, SHOP_WINDOW_GLOW_B = 1.00f;
    public static final float SHOP_HOLO_R        = 0.45f, SHOP_HOLO_G        = 0.95f, SHOP_HOLO_B        = 1.00f;
    // Ambient aura tint (warm UAC amber so the fabricator reads apart from cold walls / green chips).
    public static final float SHOP_AURA_R = 1.00f, SHOP_AURA_G = 0.66f, SHOP_AURA_B = 0.28f;

    // =========================================================================================
    // Boss telegraph floor overlay (boss-fight-mobile-overseer ORDER 8) — one COLOUR + SHAPE per
    // threat (Fairness F6). Flat quads drawn on the danger tiles' floor by EnemyRenderer, world-
    // space, occluded by nearer walls via the wall Z-buffer. Colour-blind safety: each threat pairs
    // its colour with a distinct floor shape (patch / line bar / ring / runes), never colour alone.
    // =========================================================================================
    /** Max draw distance (tiles) for a boss telegraph floor quad; beyond this it is culled for clarity. */
    public static final float BOSS_TELEGRAPH_MAX_DISTANCE_TILES = 20f;
    /** Marker footprint as a fraction of the tile's full wall-stripe height at its depth. */
    public static final float BOSS_TELEGRAPH_SIZE_FRACTION      = 0.72f;
    /** Vertical lift of the quad off the floor line, as a fraction of its own size (0 = flat on floor). */
    public static final float BOSS_TELEGRAPH_FLOOR_LIFT_FRACTION = 0.14f;
    /** Pulse speed (Hz) of the telegraph's breathing glow so it reads as "armed, about to resolve". */
    public static final float BOSS_TELEGRAPH_PULSE_HZ           = 2.2f;
    /** Peak alpha of a telegraph quad at the brightest point of its pulse. */
    public static final float BOSS_TELEGRAPH_MAX_ALPHA         = 0.55f;
    /** Fraction of the tile a filled patch/bar core spans (leaves a small gutter so tiles stay distinct). */
    public static final float BOSS_TELEGRAPH_FILL_FRACTION      = 0.82f;
    /** Thickness of the CHARGE line bar as a fraction of the marker size (a slim bar = "line" shape). */
    public static final float BOSS_TELEGRAPH_LINE_THICKNESS_FRACTION = 0.34f;
    /** Number of orbiting motes drawn for ring/rune shapes (melee ring, summon runes). */
    public static final int   BOSS_TELEGRAPH_MOTE_COUNT         = 8;
    /** Mote size as a fraction of the marker size for ring/rune shapes. */
    public static final float BOSS_TELEGRAPH_MOTE_SIZE_FRACTION = 0.20f;

    // Per-threat telegraph colours (the learned vocabulary).
    public static final float BOSS_TELEGRAPH_CHARGE_R = 1.00f, BOSS_TELEGRAPH_CHARGE_G = 0.12f, BOSS_TELEGRAPH_CHARGE_B = 0.10f; // RED line
    public static final float BOSS_TELEGRAPH_MELEE_R  = 1.00f, BOSS_TELEGRAPH_MELEE_G  = 0.55f, BOSS_TELEGRAPH_MELEE_B  = 0.08f; // ORANGE ring
    public static final float BOSS_TELEGRAPH_FIRE_R   = 1.00f, BOSS_TELEGRAPH_FIRE_G   = 0.62f, BOSS_TELEGRAPH_FIRE_B   = 0.12f; // AMBER patch
    public static final float BOSS_TELEGRAPH_TOXIC_R  = 0.40f, BOSS_TELEGRAPH_TOXIC_G  = 0.90f, BOSS_TELEGRAPH_TOXIC_B  = 0.18f; // SICKLY GREEN patch
    public static final float BOSS_TELEGRAPH_SUMMON_R = 0.66f, BOSS_TELEGRAPH_SUMMON_G = 0.30f, BOSS_TELEGRAPH_SUMMON_B = 0.95f; // PURPLE runes
    public static final float BOSS_TELEGRAPH_GENERIC_R = 0.85f, BOSS_TELEGRAPH_GENERIC_G = 0.85f, BOSS_TELEGRAPH_GENERIC_B = 0.90f; // neutral

    // =========================================================================================
    // Boss sprite feedback (ORDER 8) — the enraged (phase 2) and repairing (ORDER 5) tints blended
    // over the Overseer billboard in EnemyRenderer so both states are unmistakable at a glance.
    // =========================================================================================
    /** ENRAGED accent — a hot red the phase-2 boss sprite shifts toward (distinct from the cyan idle accent). */
    public static final float BOSS_ENRAGE_TINT_R = 1.00f, BOSS_ENRAGE_TINT_G = 0.20f, BOSS_ENRAGE_TINT_B = 0.12f;
    /** Base blend strength of the enrage tint, plus the amplitude and speed of its emissive pulse. */
    public static final float BOSS_ENRAGE_TINT_STRENGTH = 0.34f;
    public static final float BOSS_ENRAGE_TINT_PULSE_AMOUNT = 0.16f;
    public static final float BOSS_ENRAGE_TINT_PULSE_HZ = 1.4f;
    /** REPAIRING welding glow — a green the boss sprite pulses toward while its one-time heal chain runs. */
    public static final float BOSS_REPAIR_TINT_R = 0.20f, BOSS_REPAIR_TINT_G = 1.00f, BOSS_REPAIR_TINT_B = 0.35f;
    public static final float BOSS_REPAIR_TINT_STRENGTH = 0.45f;
    public static final float BOSS_REPAIR_TINT_PULSE_AMOUNT = 0.30f;
    public static final float BOSS_REPAIR_TINT_PULSE_HZ = 3.0f;

    // =========================================================================================
    // Boss dash afterimage / motion trail (ORDER 8) — 2-3 fading ghost billboards drawn behind the
    // boss along its slide path while isSliding(), so a multi-tile dash reads as a deliberate sprint
    // and never a teleport (Fairness F3). Pre-allocated; drawn only during a slide.
    // =========================================================================================
    /** Number of trailing ghost billboards behind a dashing boss. */
    public static final int   BOSS_AFTERIMAGE_COUNT           = 3;
    /** Progress spacing between consecutive ghosts, sampled backward from the live slide progress. */
    public static final float BOSS_AFTERIMAGE_PROGRESS_STEP   = 0.12f;
    /** Alpha of the nearest ghost; each further ghost fades by BOSS_AFTERIMAGE_ALPHA_FALLOFF. */
    public static final float BOSS_AFTERIMAGE_START_ALPHA     = 0.40f;
    public static final float BOSS_AFTERIMAGE_ALPHA_FALLOFF   = 0.55f;

    // =========================================================================================
    // STELLAR OBSERVATORY — Gravity Well Rotunda (.claude/agents/ideas/stellar-observatory-
    // gravity-well-room.txt). Cool-toned palette of its PropRenderer props and decals (its walls come from
    // the region set's SIGNATURE family since generated-wall-art order 2). Palette discipline:
    // titanium/gunmetal metal + deep-space indigo/black + emissive cyan/violet/ice-blue, with a
    // single warm exception (the docking-pylon service lamp, LED_AMBER) — no hazard orange, no
    // rust, no gore, no red glow anywhere else in this room.
    // =========================================================================================
    public static final float STELLAR_HULL_TITANIUM_R    = 0.58f, STELLAR_HULL_TITANIUM_G    = 0.60f, STELLAR_HULL_TITANIUM_B    = 0.64f;
    public static final float STELLAR_HULL_STEEL_DARK_R  = 0.20f, STELLAR_HULL_STEEL_DARK_G  = 0.22f, STELLAR_HULL_STEEL_DARK_B  = 0.26f;
    public static final float STELLAR_HULL_STEEL_LIGHT_R = 0.74f, STELLAR_HULL_STEEL_LIGHT_G = 0.77f, STELLAR_HULL_STEEL_LIGHT_B = 0.80f;
    public static final float STELLAR_VOID_BLACK_R       = 0.02f, STELLAR_VOID_BLACK_G       = 0.02f, STELLAR_VOID_BLACK_B       = 0.05f;
    public static final float STELLAR_STAR_BLUE_R        = 0.62f, STELLAR_STAR_BLUE_G        = 0.78f, STELLAR_STAR_BLUE_B        = 1.00f;
    public static final float STELLAR_CYAN_GLOW_R        = 0.30f, STELLAR_CYAN_GLOW_G        = 0.90f, STELLAR_CYAN_GLOW_B        = 1.00f;
    public static final float STELLAR_ICE_BLUE_R         = 0.70f, STELLAR_ICE_BLUE_G         = 0.92f, STELLAR_ICE_BLUE_B         = 1.00f;
    public static final float STELLAR_LED_GREEN_R        = 0.30f, STELLAR_LED_GREEN_G        = 0.90f, STELLAR_LED_GREEN_B        = 0.45f;
    public static final float STELLAR_LED_AMBER_R        = 0.95f, STELLAR_LED_AMBER_G        = 0.72f, STELLAR_LED_AMBER_B        = 0.28f;
    public static final float STELLAR_CORE_BLUE_R        = 0.35f, STELLAR_CORE_BLUE_G        = 0.72f, STELLAR_CORE_BLUE_B        = 1.00f;
    public static final float STELLAR_SEAM_WHITE_R       = 0.90f, STELLAR_SEAM_WHITE_G       = 0.95f, STELLAR_SEAM_WHITE_B       = 1.00f;

    // Prop/decal height multipliers (relative to a full wall stripe) — see propHeightMultiplier()
    // in PropRenderer.java.
    public static final float PROP_HEIGHT_GRAVITY_WELL_CORE    = 0.98f; // ';' centerpiece
    public static final float PROP_HEIGHT_FLOATING_CARGO_CRATE = 0.55f; // '\'
    public static final float PROP_HEIGHT_DOCKING_STRUT_PYLON  = 0.92f; // '|'
    public static final float PROP_HEIGHT_ASTRO_NAV_TABLE      = 0.60f; // '<'
    public static final float PROP_HEIGHT_MAGNETIC_DECK        = 0.18f; // '?'
    public static final float PROP_HEIGHT_ZEROG_DEBRIS         = 0.16f; // ']'
    public static final float PROP_HEIGHT_STARLIGHT_SEAM       = 0.14f; // '-'

    // ---- FLOOR TWISTS (floor-twists R7b/R7c) — BLACKOUT is cosmetic here (AS8); its perception half is BalanceConfig SECTION 27 ----
    /** R7b: every tile brightness on a BLACKOUT floor is multiplied by this — except the exit, which stays LIT. */
    public static final float TWIST_BLACKOUT_LIGHT_MULTIPLIER = 0.45f;
    /** R7c: enemy billboards on a BLACKOUT floor never shade below this, so a body in the dark is a silhouette. */
    public static final float TWIST_BLACKOUT_ENEMY_MIN_SHADE  = 0.35f;

    // -------------------------------------------------------------------------
    // Contact shadows (visual-identity-order-2 R5/R6) — soft ellipse under every upright billboard
    // -------------------------------------------------------------------------
    public static final int   CONTACT_SHADOW_TEXTURE_WIDTH        = 64;
    public static final int   CONTACT_SHADOW_TEXTURE_HEIGHT       = 32;
    /** Shadow width as a fraction of the projected billboard width. */
    public static final float CONTACT_SHADOW_WIDTH_FRACTION_ENEMY = 0.70f;
    public static final float CONTACT_SHADOW_WIDTH_FRACTION_PROP   = 0.85f;
    public static final float CONTACT_SHADOW_WIDTH_FRACTION_PICKUP = 0.60f;
    public static final float CONTACT_SHADOW_WIDTH_FRACTION_SHOP   = 0.90f;
    /** Shadow height = shadow width x this ratio. */
    public static final float CONTACT_SHADOW_HEIGHT_RATIO         = 0.22f;
    /** Peak alpha before the fog fade. */
    public static final float CONTACT_SHADOW_ALPHA                = 0.50f;
    /** Fraction of the alpha removed at full fog: alpha x (1 - fade x fog). */
    public static final float CONTACT_SHADOW_FOG_FADE             = 0.80f;
    /** Extra alpha scale for a hovering enemy so the gap to the body reads as height. */
    public static final float CONTACT_SHADOW_HOVER_ALPHA_SCALE    = 0.55f;

    // =========================================================================
    // PERFORMANCE PROBE (render-performance-full-hd R10-R12) — a DEV tool, never shipped on.
    // render/RenderPerformanceProbe times each World.render pass. Off unless the system property
    // PERF_PROBE_PROPERTY_KEY is "cpu" (timing only) or "sync" (glFinish after every pass, for GPU
    // attribution — inflates the total), or PERF_PROBE_FORCE_ON is flipped for a phone measurement build.
    // Readout strings are dev-only and deliberately not localised.
    // =========================================================================
    public static final boolean PERF_PROBE_FORCE_ON              = false;
    public static final String  PERF_PROBE_PROPERTY_KEY          = "toon3d.perfProbe";
    public static final String  PERF_PROBE_MODE_CPU              = "cpu";
    public static final String  PERF_PROBE_MODE_SYNC             = "sync";
    /** Rolling window for the averages and the worst frame. */
    public static final int     PERF_PROBE_WINDOW_FRAMES         = 120;
    public static final float   PERF_PROBE_LOG_INTERVAL_SECONDS  = 5f;
    /** How often the on-screen readout text is rebuilt (a steadier read than every frame). */
    public static final float   PERF_PROBE_READOUT_REFRESH_SECONDS = 0.25f;
    /** Probe readout batch (plate + glyphs) and the reused text builders' initial capacities. */
    public static final int     PERF_PROBE_BATCH_SIZE            = 64;
    public static final int     PERF_PROBE_READOUT_CAPACITY      = 160;
    public static final int     PERF_PROBE_LOG_CAPACITY          = 640;
    public static final String  PERF_PROBE_LOG_TAG               = "perf";
    /** Readout plate, top centre: clear of the mini-map (top-left), the HUD panels and every touch button. */
    public static final float   PERF_PROBE_PLATE_WIDTH           = 420f;
    public static final float   PERF_PROBE_PLATE_HEIGHT          = 44f;
    public static final float   PERF_PROBE_PLATE_TOP_MARGIN      = 4f;
    public static final float   PERF_PROBE_PLATE_ALPHA           = 0.6f;
    public static final float   PERF_PROBE_TEXT_PADDING          = 6f;
    public static final float   PERF_PROBE_TEXT_SCALE            = 1f;
    public static final String  PERF_PROBE_LABEL_AVERAGE         = "AVG ";
    public static final String  PERF_PROBE_LABEL_WORST           = " WORST ";
    public static final String  PERF_PROBE_LABEL_FPS             = " FPS ";
    public static final String  PERF_PROBE_LABEL_FLOOR           = "FLOOR ";
    public static final String  PERF_PROBE_LABEL_WALLS           = " WALL ";
    public static final String  PERF_PROBE_LABEL_DRAW_CALLS      = " DC ";
    public static final String  PERF_PROBE_UNIT_MILLISECONDS     = "MS";
    /** Fixed decimals for readout and log numbers. */
    public static final int     PERF_PROBE_DECIMALS              = 2;

    // ── REGION WALL ATLAS RUNTIME (generated-wall-art order 2; docs/wall-art-creation-guide.txt section 13) ──
    /** Texels per wall face side at mip 0 (the manifest's "size"). */
    public static final int   WALL_FACE_SIZE                = 256;
    /** Highest distance-LOD level the manifests ship (mips 0, 1, 2). */
    public static final int   WALL_MAX_MIP                  = 2;
    /**
     * Distance LOD bias: GameMath.wallMipLevel switches to mip m once (WALL_FACE_SIZE / stripe height) x bias
     * reaches 2^m. 1.0 = the honest texel-per-unit switch on the 720-unit projection; larger = earlier.
     */
    public static final float WALL_MIP_BIAS                 = 1.0f;
    /** Wall glow stays fully lit up to this many tiles ... */
    public static final float WALL_GLOW_FADE_START_TILES    = 10f;
    /** ... and fades linearly to WALL_GLOW_MIN_ALPHA here (and holds). */
    public static final float WALL_GLOW_FADE_END_TILES      = 20f;
    public static final float WALL_GLOW_MIN_ALPHA           = 0.6f;
    /** An "animate": "some" glow runs on about one face in this many (a failing tube); the rest hold frame 0. */
    public static final int   WALL_GLOW_SOME_ANIMATE_ONE_IN = 6;
    /** Hash "side" used to pick the floor's column picture from the set (not a real face side, which are 0-3). */
    public static final int   WALL_COLUMN_PICK_SIDE         = 4;
    /** Hash "side" offset for the per-face glow animation phase (keeps it independent of the variant hash). */
    public static final int   WALL_GLOW_PHASE_SIDE_OFFSET   = 8;
    /** Buckets the per-face glow phase is drawn from (phase = bucket / buckets x cycle length). */
    public static final int   WALL_GLOW_PHASE_BUCKETS       = 64;
}
