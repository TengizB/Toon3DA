package ge.tbegvadze.toon3d.util;

/** Rendering constants — wall textures, camera-plane raycasting, floor/ceiling, alert pulse, props, columns, procedural wall types. */
public final class RenderConstants {

    private RenderConstants() {}

    // Wall textures — one path per wall symbol (see Level.isWall)
    public static final String LAB_WALL_PLAIN_PATH    = "textures/lab/lab_wall_generic.jpg";
    public static final String LAB_WALL_CONDUIT_PATH  = "textures/lab/lab_wall_conduit.jpg";
    public static final String LAB_WALL_VENT_PATH     = "textures/lab/lab_wall_vent.jpg";
    public static final String LAB_WALL_TERMINAL_PATH = "textures/lab/lab_wall_terminal.jpg";
    public static final String LAB_WALL_WIRES_PATH    = "textures/lab/lab_wall_wires.jpg";
    // Hazard wall — always procedural (no asset file); yellow/black diagonal safety stripes
    public static final String LAB_WALL_HAZARD_PATH   = "textures/lab/lab_wall_hazard.png";

    // Door textures
    public static final String LAB_DOOR_CLOSED_PATH    = "textures/lab/lab_door_1.jpg";
    public static final String LAB_DOOR_RED_PATH       = "textures/lab/lab_door_red.jpg";
    public static final String LAB_DOOR_YELLOW_PATH    = "textures/lab/lab_door_yellow.jpg";
    public static final String LAB_DOOR_BLUE_PATH      = "textures/lab/lab_door_blue.jpg";

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
    public static final float MINIMAP_EXIT_R           = 0.10f;
    public static final float MINIMAP_EXIT_G           = 1.00f;
    public static final float MINIMAP_EXIT_B           = 0.55f;
    public static final float MINIMAP_PROP_R           = 0.42f;
    public static final float MINIMAP_PROP_G           = 0.42f;
    public static final float MINIMAP_PROP_B           = 0.46f;
    public static final float MINIMAP_BARREL_R         = 1.00f;
    public static final float MINIMAP_BARREL_G         = 0.48f;
    public static final float MINIMAP_BARREL_B         = 0.09f;
    public static final float MINIMAP_HEALTH_PICKUP_R  = 0.20f;
    public static final float MINIMAP_HEALTH_PICKUP_G  = 0.90f;
    public static final float MINIMAP_HEALTH_PICKUP_B  = 0.20f;
    public static final float MINIMAP_ARMOR_PICKUP_R   = 0.09f;
    public static final float MINIMAP_ARMOR_PICKUP_G   = 0.75f;
    public static final float MINIMAP_ARMOR_PICKUP_B   = 0.88f;
    public static final float MINIMAP_AMMO_PICKUP_R    = 0.75f;
    public static final float MINIMAP_AMMO_PICKUP_G    = 0.45f;
    public static final float MINIMAP_AMMO_PICKUP_B    = 0.10f;
    public static final float MINIMAP_ENEMY_THREAT_R   = 1.00f;
    public static final float MINIMAP_ENEMY_THREAT_G   = 0.16f;
    public static final float MINIMAP_ENEMY_THREAT_B   = 0.16f;
    // Shop (vending machine, '@') marker — bright glowing magenta, distinct from generic props
    // so it reads at a glance on the mini-map instead of blending in with grey obstacles.
    public static final float MINIMAP_SHOP_R           = 0.85f;
    public static final float MINIMAP_SHOP_G           = 0.25f;
    public static final float MINIMAP_SHOP_B           = 1.00f;
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
    // Set WALL_PROJECTION_SCREEN_WIDTH to 320 for retro pixelated look; WORLD_WIDTH for crisp
    public static final int   WALL_PROJECTION_SCREEN_WIDTH    = Constants.WORLD_WIDTH;
    public static final int   WALL_PROJECTION_SCREEN_HEIGHT   = Constants.WORLD_HEIGHT;
    public static final float WALL_COLUMN_WIDTH               = (float) Constants.WORLD_WIDTH / WALL_PROJECTION_SCREEN_WIDTH;
    // shade = max(MIN, 1 / (1 + d² × FALLOFF)). Steeper falloff increases near/far contrast.
    // At d=5: shade≈0.29  At d=10: shade≈0.091  MIN_BRIGHTNESS prevents full-black silhouettes.
    public static final float WALL_SHADING_FALLOFF            = 0.10f;
    public static final float WALL_SHADE_MIN_BRIGHTNESS       = 0.12f;
    // Enemy sprites use a higher min so distant enemies never read as wall-coloured blobs.
    public static final float ENEMY_SPRITE_SHADE_MIN_BRIGHTNESS = 0.35f;
    // N/S faces darkened by this factor vs E/W faces to simulate directional lighting
    public static final float HORIZONTAL_FACE_SHADE_MULTIPLIER = 0.7f;

    // Floor & Ceiling texture rendering — see docs/dda-raycasting-math.txt
    // SCALE_DIVISOR=4 → 320×180 backdrop (~16× cheaper than full-res); stretched by SpriteBatch
    public static final int    FLOOR_BACKDROP_SCALE_DIVISOR    = 4;
    public static final int    FLOOR_BACKDROP_WIDTH             = Constants.WORLD_WIDTH  / FLOOR_BACKDROP_SCALE_DIVISOR;
    public static final int    FLOOR_BACKDROP_HEIGHT            = Constants.WORLD_HEIGHT / FLOOR_BACKDROP_SCALE_DIVISOR;
    // FLOOR_CAMERA_Z = 0.5 → camera exactly centred (horizon at screen centre)
    public static final float  FLOOR_CAMERA_Z                   = 0.5f;
    public static final float  FLOOR_SHADING_FALLOFF            = 0.05f;
    public static final float  FLOOR_MAX_VISIBLE_DISTANCE_CELLS = 15.0f;
    // Packed RGBA8888 ambient colours for max-distance / unshaded pixels
    public static final int    FLOOR_AMBIENT_COLOUR_PACKED      = 0x141414FF;
    public static final int    CEILING_AMBIENT_COLOUR_PACKED    = 0x10101CFF;
    // Placeholder procedural textures generated at runtime when these assets are absent
    public static final String LAB_FLOOR_TEXTURE_PATH   = "textures/lab/lab_floor_concrete.png";
    public static final String LAB_CEILING_TEXTURE_PATH = "textures/lab/lab_ceiling_panel.png";

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

    // -------------------------------------------------------------------------
    // NEW WALL TYPES — procedural textures ('N','Q','S','M','Z','U','X')
    // Note: 'R' and 'Y' are reserved for red/yellow keycard doors — use 'S'/'U'.
    // -------------------------------------------------------------------------

    // All procedural wall textures are authored at this resolution. Raised from the
    // original 128 to 256 for crisper detail when a wall fills the screen up close.
    // Each generator below derives its geometry from its own *_WALL_TEXTURE_SIZE so the
    // designs stay proportional; point-feature counts are tuned for the 256² area.

    // 'N' — Reinforced Containment Glass (cracked observation window)
    public static final int   GLASS_WALL_TEXTURE_SIZE         = 256;
    public static final long  GLASS_WALL_SEED                 = 0x476C6173L; // "Glas"
    public static final int   GLASS_CRACK_BRANCH_COUNT        = 5;
    public static final int   GLASS_CRACK_STEPS               = 60;
    public static final int   GLASS_GHOST_BLOB_COUNT          = 3;
    public static final int   GLASS_SHEEN_STREAK_COUNT        = 2;

    // 'Q' — Bio-Containment / Quarantine Wall
    public static final int   BIO_WALL_TEXTURE_SIZE           = 256;
    public static final long  BIO_WALL_SEED                   = 0x42696F57L; // "BioW"
    public static final int   BIO_STRIPE_PITCH                = 20;

    // 'S' — Emergency Lighting Strip Wall (static red — maps visual alarm state)
    public static final int   EMERG_WALL_TEXTURE_SIZE         = 256;
    public static final long  EMERG_WALL_SEED                 = 0x456D6572L; // "Emer"
    public static final int   EMERG_CAGE_COUNT                = 4;

    // 'M' — Medical Tile Wall (white tile + green accent + cross)
    public static final int   MED_WALL_TEXTURE_SIZE           = 256;
    public static final long  MED_WALL_SEED                   = 0x4D656469L; // "Medi"
    public static final int   MED_TILE_SIZE                   = 32;
    public static final int   MED_BLOOD_FLECK_COUNT           = 40;

    // 'Z' — Cryo / Frost-Damaged Wall (ice rime, icicles, frost fractures)
    public static final int   CRYO_WALL_TEXTURE_SIZE          = 256;
    public static final long  CRYO_WALL_SEED                  = 0x4372796FL; // "Cryo"
    public static final int   CRYO_ICICLE_COUNT               = 8;
    public static final int   CRYO_FRACTURE_COUNT             = 6;
    public static final int   CRYO_GLINT_COUNT                = 48;
    public static final int   CRYO_FROST_CORNER_RADIUS        = 80;

    // 'U' — Radiation-Burned Wall (scorched black, glowing green-yellow cracks)
    public static final int   RAD_WALL_TEXTURE_SIZE           = 256;
    public static final long  RAD_WALL_SEED                   = 0x52616469L; // "Radi"
    public static final int   RAD_CRACK_COUNT                 = 5;
    public static final int   RAD_CRACK_STEPS                 = 70;
    public static final int   RAD_DUST_COUNT                  = 64;

    // 'X' — Blast-Scarred Wall (bullet pocks, blast craters, through-holes)
    public static final int   BLAST_WALL_TEXTURE_SIZE         = 256;
    public static final long  BLAST_WALL_SEED                 = 0x426C6173L; // "Blas"
    public static final int   BLAST_BULLET_HOLE_COUNT         = 48;
    public static final int   BLAST_CRATER_COUNT              = 6;
    public static final int   BLAST_THROUGH_HOLE_COUNT        = 6;
    public static final int   BLAST_SCORCH_COUNT              = 8;

    // 'D' — Holo-Data Display Wall (glowing cyan UI panels, data bars, readout grid)
    public static final int   HOLO_DATA_WALL_TEXTURE_SIZE     = 256;
    public static final long  HOLO_DATA_WALL_SEED             = 0x44617461L; // "Data"
    public static final int   HOLO_DATA_BAR_COUNT             = 4;
    public static final int   HOLO_DATA_READOUT_COLUMNS       = 8;
    public static final int   HOLO_DATA_READOUT_ROWS          = 6;

    // 'F' — Force-Field Arc Wall (electric-blue lattice with emitter posts)
    public static final int   FORCE_FIELD_WALL_TEXTURE_SIZE   = 256;
    public static final long  FORCE_FIELD_WALL_SEED           = 0x466F7263L; // "Forc"
    public static final int   FORCE_FIELD_ARC_COUNT           = 4;
    public static final int   FORCE_FIELD_BAND_COUNT          = 2;

    // 'h' — Hazard caution-stripe wall (procedural fallback when no asset present)
    public static final int   HAZARD_WALL_TEXTURE_SIZE        = 256;

    // Rust procedural wall texture generation
    public static final int   RUST_WALL_TEXTURE_SIZE          = 256;
    public static final long  RUST_WALL_SEED                  = 0x52757374L; // "Rust"
    public static final int   RUST_BLOB_COUNT                 = 14;
    public static final int   RUST_PIT_COUNT                  = 32;

    // 'G' — Gore wall: wet demonic flesh membrane. Full procedural redesign:
    // a tiling metaball flesh field with bump-relief shading, wet specular sheen,
    // organic sinew vessels, infected pustules, open gashes with blood drips,
    // bone nodules, pores and glistening glints.
    public static final int   GORE_WALL_TEXTURE_SIZE          = 256;
    public static final long  GORE_WALL_SEED                  = 0x476F7265L; // "Gore"
    public static final int   GORE_BLOB_COUNT                 = 30;   // membrane masses
    public static final int   GORE_BLOB_RADIUS_MIN            = 22;
    public static final int   GORE_BLOB_RADIUS_SPAN           = 46;
    public static final float GORE_FLESH_THRESHOLD            = 0.30f;
    public static final float GORE_EMBOSS_STRENGTH            = 1.7f;  // bump-relief intensity
    public static final int   GORE_SHEEN_COUNT                = 18;   // wet specular blooms
    public static final int   GORE_SUBDERMAL_VEIN_COUNT       = 10;
    public static final int   GORE_VEIN_COUNT                 = 26;   // sinew vessels
    public static final int   GORE_VEIN_STEPS                 = 110;
    public static final int   GORE_PUSTULE_COUNT              = 26;   // boils
    public static final int   GORE_GASH_COUNT                 = 5;    // open wounds
    public static final int   GORE_BONE_COUNT                 = 9;
    public static final int   GORE_PORE_COUNT                 = 70;
    public static final int   GORE_GLINT_COUNT                = 150;

    // Bulkhead procedural wall texture generation
    public static final int   BULKHEAD_WALL_TEXTURE_SIZE      = 256;
    public static final int   BULKHEAD_FRAME_WIDTH            = 28;
    public static final int   BULKHEAD_BOLT_SPACING           = 42;

    // 'P' — Cylindrical column texture (taller than wide; mortar course lines)
    public static final int   COLUMN_TEXTURE_WIDTH            = 128;
    public static final int   COLUMN_TEXTURE_HEIGHT           = 256;
    public static final int   COLUMN_MORTAR_SPACING           = 40;

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
    // gravity-well-room.txt). Shared cool-toned palette reused by both WallRenderer (walls '"'
    // viewport dome, ''' magrail conduit, '`' hull plate) and PropRenderer (props ';' \\ | < and
    // decals ? ] -) so the biome reads as one consistent material system. Palette discipline:
    // titanium/gunmetal metal + deep-space indigo/black + emissive cyan/violet/ice-blue, with a
    // single warm exception (the docking-pylon service lamp, LED_AMBER) — no hazard orange, no
    // rust, no gore, no red glow anywhere else in this room.
    // =========================================================================================
    public static final float STELLAR_HULL_TITANIUM_R    = 0.58f, STELLAR_HULL_TITANIUM_G    = 0.60f, STELLAR_HULL_TITANIUM_B    = 0.64f;
    public static final float STELLAR_HULL_STEEL_DARK_R  = 0.20f, STELLAR_HULL_STEEL_DARK_G  = 0.22f, STELLAR_HULL_STEEL_DARK_B  = 0.26f;
    public static final float STELLAR_HULL_STEEL_LIGHT_R = 0.74f, STELLAR_HULL_STEEL_LIGHT_G = 0.77f, STELLAR_HULL_STEEL_LIGHT_B = 0.80f;
    public static final float STELLAR_VOID_INDIGO_R      = 0.06f, STELLAR_VOID_INDIGO_G      = 0.07f, STELLAR_VOID_INDIGO_B      = 0.14f;
    public static final float STELLAR_VOID_BLACK_R       = 0.02f, STELLAR_VOID_BLACK_G       = 0.02f, STELLAR_VOID_BLACK_B       = 0.05f;
    public static final float STELLAR_NEBULA_MAGENTA_R   = 0.55f, STELLAR_NEBULA_MAGENTA_G   = 0.22f, STELLAR_NEBULA_MAGENTA_B   = 0.55f;
    public static final float STELLAR_NEBULA_TEAL_R      = 0.16f, STELLAR_NEBULA_TEAL_G      = 0.42f, STELLAR_NEBULA_TEAL_B      = 0.48f;
    public static final float STELLAR_STAR_WHITE_R       = 0.96f, STELLAR_STAR_WHITE_G       = 0.98f, STELLAR_STAR_WHITE_B       = 1.00f;
    public static final float STELLAR_STAR_BLUE_R        = 0.62f, STELLAR_STAR_BLUE_G        = 0.78f, STELLAR_STAR_BLUE_B        = 1.00f;
    public static final float STELLAR_CYAN_GLOW_R        = 0.30f, STELLAR_CYAN_GLOW_G        = 0.90f, STELLAR_CYAN_GLOW_B        = 1.00f;
    public static final float STELLAR_MAGRAIL_VIOLET_R   = 0.55f, STELLAR_MAGRAIL_VIOLET_G   = 0.30f, STELLAR_MAGRAIL_VIOLET_B   = 0.95f;
    public static final float STELLAR_MAGRAIL_CYAN_R     = 0.25f, STELLAR_MAGRAIL_CYAN_G     = 0.85f, STELLAR_MAGRAIL_CYAN_B     = 1.00f;
    public static final float STELLAR_ICE_BLUE_R         = 0.70f, STELLAR_ICE_BLUE_G         = 0.92f, STELLAR_ICE_BLUE_B         = 1.00f;
    public static final float STELLAR_FROST_WHITE_R      = 0.85f, STELLAR_FROST_WHITE_G      = 0.92f, STELLAR_FROST_WHITE_B     = 0.98f;
    public static final float STELLAR_LED_GREEN_R        = 0.30f, STELLAR_LED_GREEN_G        = 0.90f, STELLAR_LED_GREEN_B        = 0.45f;
    public static final float STELLAR_LED_AMBER_R        = 0.95f, STELLAR_LED_AMBER_G        = 0.72f, STELLAR_LED_AMBER_B        = 0.28f;
    public static final float STELLAR_CORE_BLUE_R        = 0.35f, STELLAR_CORE_BLUE_G        = 0.72f, STELLAR_CORE_BLUE_B        = 1.00f;
    public static final float STELLAR_SEAM_WHITE_R       = 0.90f, STELLAR_SEAM_WHITE_G       = 0.95f, STELLAR_SEAM_WHITE_B       = 1.00f;

    // Wall texture sizes/seeds (WallRenderer generateXWallTexture pattern — see 'N'/'Q'/etc above).
    public static final int  STELLAR_VIEWPORT_WALL_TEXTURE_SIZE  = 256;
    public static final long STELLAR_VIEWPORT_WALL_SEED          = 0x5669657AL; // "ViewpZ" trunc
    public static final int  STELLAR_MAGRAIL_WALL_TEXTURE_SIZE   = 256;
    public static final long STELLAR_MAGRAIL_WALL_SEED           = 0x4D616772L; // "Magr"
    public static final int  STELLAR_HULLPLATE_WALL_TEXTURE_SIZE = 256;
    public static final long STELLAR_HULLPLATE_WALL_SEED         = 0x48756C6CL; // "Hull"

    // Wall generator feature counts (non-colour implementation constants — see the wall texture
    // builders in WallRenderer.java: generateStellarViewportWallTexture, generateStellarMagrailWallTexture,
    // generateStellarHullPlateWallTexture). Baked once at load time like every other procedural wall
    // in this file, so these describe the single baked look rather than a per-tile-instance variation.
    public static final int   STELLAR_VIEWPORT_STAR_COUNT        = 55;  // 40-70 scattered star points
    public static final int   STELLAR_VIEWPORT_STAR_GLINT_COUNT  = 6;   // brightest stars get a cross-glint
    public static final int   STELLAR_VIEWPORT_NEBULA_BAND_COUNT = 3;   // soft magenta/teal cloud bands
    public static final int   STELLAR_VIEWPORT_SILL_LED_COUNT    = 3;   // status LEDs along the sill strip
    public static final int   STELLAR_VIEWPORT_CORNER_RADIUS     = 30;  // rounded-top window corner radius (px)

    public static final int   STELLAR_MAGRAIL_CHANNEL_COUNT      = 4;   // vertical recessed rail housings
    public static final int   STELLAR_MAGRAIL_NODE_SPACING       = 12;  // px between diamond junction LEDs

    public static final int   STELLAR_HULLPLATE_GRID_COLUMNS     = 3;   // plate grid columns
    public static final int   STELLAR_HULLPLATE_GRID_ROWS        = 2;   // plate grid rows
    public static final int   STELLAR_HULLPLATE_RIVET_SPACING    = 22;  // px between border rivets

    // Prop/decal height multipliers (relative to a full wall stripe) — see propHeightMultiplier()
    // in PropRenderer.java.
    public static final float PROP_HEIGHT_GRAVITY_WELL_CORE    = 0.98f; // ';' centerpiece
    public static final float PROP_HEIGHT_FLOATING_CARGO_CRATE = 0.55f; // '\'
    public static final float PROP_HEIGHT_DOCKING_STRUT_PYLON  = 0.92f; // '|'
    public static final float PROP_HEIGHT_ASTRO_NAV_TABLE      = 0.60f; // '<'
    public static final float PROP_HEIGHT_MAGNETIC_DECK        = 0.18f; // '?'
    public static final float PROP_HEIGHT_ZEROG_DEBRIS         = 0.16f; // ']'
    public static final float PROP_HEIGHT_STARLIGHT_SEAM       = 0.14f; // '-'
}
