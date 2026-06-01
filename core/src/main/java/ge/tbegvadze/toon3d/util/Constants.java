package ge.tbegvadze.toon3d.util;

/** Single source of truth for all game-wide constants. Never hardcode these values elsewhere. */
public final class Constants {

    private Constants() {}

    // Viewport / World — virtual canvas always 1280×720; (0,0) = bottom-left
    public static final int WORLD_WIDTH  = 1280;
    public static final int WORLD_HEIGHT = 720;

    // Tile / Grid — each cell is CELL_SIZE × CELL_SIZE world units; 80×45 cells fills 1280×720
    public static final int CELL_SIZE = 16;

    // Player
    public static final float PLAYER_RADIUS = CELL_SIZE / 4f;
    // Seconds per cell step or 90° rotation. Lower = snappier animation.
    public static final float PLAYER_MOVE_DURATION   = 0.12f;
    public static final float PLAYER_ROTATE_DURATION = 0.12f;
    // FOV stored as degrees for readability; radians derived from it.
    public static final float PLAYER_FIELD_OF_VIEW_DEGREES = 90f;
    public static final float PLAYER_FIELD_OF_VIEW_RADIANS = PLAYER_FIELD_OF_VIEW_DEGREES * ((float) Math.PI / 180f);

    // Mini-map — renders (2*RADIUS+1)² tile window centred on player at world (0,0)
    public static final int   MINI_MAP_TILE_RADIUS  = 12;
    public static final int   MINI_MAP_TILE_COUNT   = MINI_MAP_TILE_RADIUS * 2 + 1;
    public static final float MINI_MAP_WORLD_SIZE   = 300f;
    public static final float MINI_MAP_CELL_SIZE     = MINI_MAP_WORLD_SIZE / MINI_MAP_TILE_COUNT;
    public static final float MINI_MAP_PLAYER_RADIUS = MINI_MAP_CELL_SIZE / 4f;
    public static final float MINI_MAP_CENTER_X      = MINI_MAP_TILE_RADIUS * MINI_MAP_CELL_SIZE + MINI_MAP_CELL_SIZE / 2f;
    public static final float MINI_MAP_CENTER_Y      = MINI_MAP_TILE_RADIUS * MINI_MAP_CELL_SIZE + MINI_MAP_CELL_SIZE / 2f;

    // Raycasting (DDA)
    public static final int   RAY_COUNT             = 60;
    public static final float RAY_MAX_LENGTH_CELLS  = 20f;

    // Wall textures — one path per wall symbol (see Level.isWall)
    public static final String LAB_WALL_PLAIN_PATH    = "textures/lab/lab_wall_generic.jpg";
    public static final String LAB_WALL_CONDUIT_PATH  = "textures/lab/lab_wall_conduit.jpg";
    public static final String LAB_WALL_VENT_PATH     = "textures/lab/lab_wall_vent.jpg";
    public static final String LAB_WALL_TERMINAL_PATH = "textures/lab/lab_wall_terminal.jpg";
    public static final String LAB_WALL_WIRES_PATH    = "textures/lab/lab_wall_wires.jpg";
    // Hazard wall — always procedural (no asset file); yellow/black diagonal safety stripes
    public static final String LAB_WALL_HAZARD_PATH   = "textures/lab/lab_wall_hazard.png";

    // Camera-plane raycasting — see docs/dda-raycasting-math.txt for formula derivations
    // CAMERA_PLANE_SCALE = tan(FOV/2); = 1.0 for 90° FOV
    public static final float CAMERA_PLANE_SCALE              = (float) Math.tan(PLAYER_FIELD_OF_VIEW_RADIANS / 2.0);
    // Set WALL_PROJECTION_SCREEN_WIDTH to 320 for retro pixelated look; WORLD_WIDTH for crisp
    public static final int   WALL_PROJECTION_SCREEN_WIDTH    = WORLD_WIDTH;
    public static final int   WALL_PROJECTION_SCREEN_HEIGHT   = WORLD_HEIGHT;
    public static final float WALL_COLUMN_WIDTH               = (float) WORLD_WIDTH / WALL_PROJECTION_SCREEN_WIDTH;
    // shade = 1 / (1 + d² × FALLOFF). At d=10, falloff=0.05 → shade≈0.167
    public static final float WALL_SHADING_FALLOFF            = 0.05f;
    // N/S faces darkened by this factor vs E/W faces to simulate directional lighting
    public static final float HORIZONTAL_FACE_SHADE_MULTIPLIER = 0.7f;

    // Floor & Ceiling texture rendering — see docs/dda-raycasting-math.txt
    // SCALE_DIVISOR=4 → 320×180 backdrop (~16× cheaper than full-res); stretched by SpriteBatch
    public static final int    FLOOR_BACKDROP_SCALE_DIVISOR    = 4;
    public static final int    FLOOR_BACKDROP_WIDTH             = WORLD_WIDTH  / FLOOR_BACKDROP_SCALE_DIVISOR;
    public static final int    FLOOR_BACKDROP_HEIGHT            = WORLD_HEIGHT / FLOOR_BACKDROP_SCALE_DIVISOR;
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

    // Input key bindings — single source of truth for remappable keys
    public static final int KEY_STRAFE_RIGHT = com.badlogic.gdx.Input.Keys.E;
    public static final int KEY_INTERACT     = com.badlogic.gdx.Input.Keys.F;
    public static final int KEY_FIRE         = com.badlogic.gdx.Input.Keys.SPACE;
    public static final int KEY_SKIP_TURN    = com.badlogic.gdx.Input.Keys.TAB;

    // Weapon system — timing
    // PLAYER_FIRE_DURATION: how long the fire action locks input (slightly heavier than a step)
    public static final float PLAYER_FIRE_DURATION  = 0.14f;
    // FIRE_FLASH_DURATION: real-time muzzle-flash pose duration; cosmetic only
    public static final float FIRE_FLASH_DURATION              = 0.22f;
    // NORMAL_TO_RELOAD_DELAY: how long the normal pose is held after the fire flash
    // before the reload pose begins; lets the player see the weapon lower to idle first
    public static final float NORMAL_TO_RELOAD_DELAY_SECONDS   = 0.18f;
    // DAMAGE_MIN_MULTIPLIER: damage floor at extreme range; prevents a dead zone
    public static final float DAMAGE_MIN_MULTIPLIER = 0.15f;

    // Shotgun stats
    public static final int     SHOTGUN_DAMAGE             = 24;
    public static final int     SHOTGUN_CLIP_SIZE          = 1;
    public static final int     SHOTGUN_RELOAD_TIME_TICKS  = 1;
    public static final float   SHOTGUN_DAMAGE_DROP_COEFF  = 0.18f;
    public static final int     SHOTGUN_RANGE_TILES        = 5;
    // SHOTGUN_PENETRATION: false = stops at first enemy (v1); true = pierces (future)
    public static final boolean SHOTGUN_PENETRATION        = false;

    // Shotgun HUD textures
    public static final String SHOTGUN_NORMAL_TEXTURE_PATH = "textures/guns/shotgun/shotgun.png";
    public static final String SHOTGUN_FIRE_TEXTURE_PATH   = "textures/guns/shotgun/shotgun_fire.png";
    public static final String SHOTGUN_RELOAD_TEXTURE_PATH = "textures/guns/shotgun/shotgun_reload.png";
    // Shotgun procedural canvas — ShapeRenderer renders into this offscreen FrameBuffer
    public static final int SHOTGUN_CANVAS_WIDTH  = 192;
    public static final int SHOTGUN_CANVAS_HEIGHT = 134;

    // Plasma Rifle stats — high clip, long range, lower per-shot damage, moderate drop
    // Damage table (coefficient 0.10, floor 0.15):
    //   distance 1: 18 × 0.90 = 16   distance 4: 18 × 0.60 = 11
    //   distance 6: 18 × 0.40 =  7   distance 8: 18 × 0.20 =  4
    public static final int   PLASMA_RIFLE_DAMAGE             = 18;
    public static final int   PLASMA_RIFLE_CLIP_SIZE          = 4;
    public static final int   PLASMA_RIFLE_RELOAD_TIME_TICKS  = 3;
    public static final float PLASMA_RIFLE_DAMAGE_DROP_COEFF  = 0.10f;
    public static final int   PLASMA_RIFLE_RANGE_TILES        = 8;
    // PLASMA_RIFLE_PENETRATION: true = shot pierces all enemies in a line
    public static final boolean PLASMA_RIFLE_PENETRATION      = true;

    // Plasma Rifle HUD textures — always procedural (no asset files)
    public static final String PLASMA_RIFLE_NORMAL_TEXTURE_PATH = "textures/guns/plasma/plasma_normal.png";
    public static final String PLASMA_RIFLE_FIRE_TEXTURE_PATH   = "textures/guns/plasma/plasma_fire.png";
    public static final String PLASMA_RIFLE_RELOAD_TEXTURE_PATH = "textures/guns/plasma/plasma_reload.png";

    // Plasma rifle procedural canvas — ShapeRenderer renders into this offscreen FrameBuffer
    public static final int PLASMA_RIFLE_CANVAS_WIDTH  = 192;
    public static final int PLASMA_RIFLE_CANVAS_HEIGHT = 134;

    // Plasma muzzle blast — blue-cyan sphere burst; replaces the shotgun orange flame
    public static final float PLASMA_BLAST_RADIUS = 85f;

    // Weapon HUD rendering — sprite anchored at screen bottom-centre
    // drawX = (WORLD_WIDTH - WEAPON_HUD_WIDTH) / 2f; drawY = 0
    public static final float WEAPON_HUD_WIDTH  = 380f;
    public static final float WEAPON_HUD_HEIGHT = 263f;
    // Recoil: weapon drops instantly by RECOIL_OFFSET_Y on fire, eases back over FIRE_FLASH_DURATION
    public static final float WEAPON_RECOIL_OFFSET_Y         = 55f;
    // Reload slide: weapon lerps this many world-units downward (mostly off screen) while reloading
    public static final float WEAPON_RELOAD_SLIDE_Y          = 200f;
    // Lerp speed for slide/return animations (higher = snappier)
    public static final float WEAPON_OFFSET_LERP_SPEED       = 14f;
    // Fraction of HUD height where the barrel tip sits in the sprite.
    // 0.92 places the plasma effect origin at the muzzle emitter center of the redesigned sprite.
    public static final float WEAPON_BARREL_TIP_Y_FRACTION = 0.92f;
    // Flame cone dimensions in world units
    public static final float WEAPON_FLAME_HEIGHT     = 80f;
    public static final float WEAPON_FLAME_BASE_WIDTH =  160f;

    // Doors — 'd' cells; runtime state in DoorManager
    // OPEN_DURATION matches PlayerController INTERACTING animation for first W press
    public static final float DOOR_OPEN_DURATION  = 0.30f;
    public static final float DOOR_CLOSE_DURATION = 0.40f;
    // DDA ray may pass through a door only when open fraction exceeds this threshold
    public static final float DOOR_OPEN_THROUGH_THRESHOLD = 0.99f;
    public static final String LAB_DOOR_CLOSED_PATH    = "textures/lab/lab_door_1.jpg";
    public static final String LAB_DOOR_RED_PATH       = "textures/lab/lab_door_red.jpg";
    public static final String LAB_DOOR_YELLOW_PATH    = "textures/lab/lab_door_yellow.jpg";
    public static final String LAB_DOOR_BLUE_PATH      = "textures/lab/lab_door_blue.jpg";
    public static final float DOOR_MINIMAP_CLOSED_R = 0.60f;
    public static final float DOOR_MINIMAP_CLOSED_G = 0.60f;
    public static final float DOOR_MINIMAP_CLOSED_B = 0.70f;
    public static final float DOOR_MINIMAP_OPEN_R   = 0.20f;
    public static final float DOOR_MINIMAP_OPEN_G   = 0.80f;
    public static final float DOOR_MINIMAP_OPEN_B   = 0.85f;

    // Keycard door glow — ambient colour tint for walls near locked doors
    public static final int   KEYCARD_DOOR_GLOW_RADIUS_TILES = 3;
    public static final float KEYCARD_DOOR_GLOW_INTENSITY    = 0.25f;

    // Tick event bus — fixed subscriber capacity; generous ceiling for future systems
    public static final int MAX_TICK_SUBSCRIBERS = 16;

    // Enemy system
    public static final String  ENEMY_CORRUPTOR_PATH             = "textures/enemies/enemy_corruptor.png";
    public static final String  ENEMY_VORTEX_EYE_PATH            = "textures/enemies/enemy_vortex_eye.png";
    public static final int     ALERT_RADIUS_TILES               = 4;
    public static final int     CHAIN_ALERT_RADIUS_TILES         = 5;
    public static final int     LOS_MAX_RANGE_TILES              = 16;
    public static final int     PLAYER_MAX_HEALTH                = 100;
    public static final int     CORRUPTOR_MAX_HEALTH             = 60;
    public static final int     CORRUPTOR_ATTACK_DAMAGE          = 14;
    public static final int     CORRUPTOR_MOVE_EVERY_N_TURNS     = 2;
    public static final float   CORRUPTOR_HEIGHT_MULTIPLIER      = 0.95f;
    public static final int     VORTEX_EYE_MAX_HEALTH            = 18;
    public static final int     VORTEX_EYE_ATTACK_DAMAGE         = 8;
    public static final int     VORTEX_EYE_RANGE_TILES           = 5;
    public static final int     VORTEX_EYE_KITE_MIN_TILES        = 2;
    public static final float   VORTEX_EYE_HEIGHT_MULTIPLIER     = 0.55f;
    public static final float   VORTEX_EYE_HOVER_OFFSET_FRACTION = 0.25f;
    public static final float   DORMANT_SHADE_DAMPEN             = 0.7f;
    public static final int     STUCK_TURNS_BEFORE_WIGGLE        = 2;
    public static final boolean ENEMY_GREEDY_WIGGLE_ENABLED      = true;
    public static final float   MAX_ENEMY_DRAW_DISTANCE_TILES    = 14f;

    // Enemy hit flash — white blanch on damage contact (purely cosmetic, wall-clock timed)
    public static final float   ENEMY_HIT_FLASH_DURATION_SECONDS    = 0.18f;

    // Enemy health bar — floating billboard above each alerted enemy sprite
    public static final float   ENEMY_HEALTH_BAR_WIDTH_FRACTION     = 0.9f;
    public static final float   ENEMY_HEALTH_BAR_HEIGHT_FRACTION    = 0.06f;
    public static final float   ENEMY_HEALTH_BAR_GAP_FRACTION       = 0.04f;
    public static final float   ENEMY_HEALTH_BAR_MIN_PIXELS         = 3f;
    public static final float   ENEMY_HEALTH_BAR_BORDER_PIXELS      = 1f;
    public static final float   ENEMY_HEALTH_BAR_MAX_DISTANCE_TILES = 12f;
    // Border / backdrop tint (semi-transparent near-black frame)
    public static final float   ENEMY_HEALTH_BAR_BORDER_RED         = 0.05f;
    public static final float   ENEMY_HEALTH_BAR_BORDER_GREEN       = 0.05f;
    public static final float   ENEMY_HEALTH_BAR_BORDER_BLUE        = 0.05f;
    public static final float   ENEMY_HEALTH_BAR_BORDER_ALPHA       = 0.85f;
    // Empty-track tint (missing health, dark red-gray)
    public static final float   ENEMY_HEALTH_BAR_TRACK_RED          = 0.18f;
    public static final float   ENEMY_HEALTH_BAR_TRACK_GREEN        = 0.05f;
    public static final float   ENEMY_HEALTH_BAR_TRACK_BLUE         = 0.05f;
    // Health gradient colour stops: full=green, half=yellow, empty=red
    public static final float   ENEMY_HEALTH_FULL_RED               = 0.10f;
    public static final float   ENEMY_HEALTH_FULL_GREEN             = 0.85f;
    public static final float   ENEMY_HEALTH_FULL_BLUE              = 0.10f;
    public static final float   ENEMY_HEALTH_HALF_RED               = 1.00f;
    public static final float   ENEMY_HEALTH_HALF_GREEN             = 0.85f;
    public static final float   ENEMY_HEALTH_HALF_BLUE              = 0.10f;
    public static final float   ENEMY_HEALTH_EMPTY_RED              = 1.00f;
    public static final float   ENEMY_HEALTH_EMPTY_GREEN            = 0.10f;
    public static final float   ENEMY_HEALTH_EMPTY_BLUE             = 0.10f;

    // HUD geometry — left panel anchored to bottom-left, y 0..HUD_HEIGHT.
    public static final float HUD_HEIGHT                      = 130f;
    public static final float HUD_LEFT_PANEL_WIDTH            = 420f;
    public static final float HUD_PANEL_GUTTER                = 4f;
    public static final float HUD_PANEL_INSET                 = 6f;
    public static final float HUD_RIVET_RADIUS                = 2.5f;
    public static final float HUD_LED_RADIUS                  = 3f;
    public static final float HUD_PANEL_ALPHA                 = 0.82f;
    // Full-width bar layout: [label x=10] [bar x=36..374] [number x=380]
    public static final float HUD_BAR_LABEL_X                 = 10f;
    public static final float HUD_BAR_START_X                 = 36f;
    public static final float HUD_BAR_FULL_WIDTH              = 338f;
    public static final float HUD_BAR_NUMBER_X                = 380f;
    public static final float HUD_BAR_HEIGHT                  = 18f;
    public static final int   HUD_BAR_SEGMENT_COUNT           = 20;
    public static final float HUD_BAR_SEGMENT_GAP             = 2f;
    public static final float HUD_BAR_LERP_RATE               = 3.5f;
    // Bar Y positions (bottom edge); bars span y: barY .. barY+HUD_BAR_HEIGHT
    public static final float HUD_HP_BAR_Y                    = 90f;
    public static final float HUD_AR_BAR_Y                    = 52f;
    public static final float HUD_CLIP_BAR_Y                  = 14f;
    // HUD animation
    public static final float HUD_PULSE_HZ                    = 4f;
    public static final float HUD_LOW_HP_THRESHOLD            = 0.25f;
    // Player armor — pool capped at 50; armour pickups feed directly into this pool
    public static final int   PLAYER_MAX_ARMOR                = 50;

    // Medical pickup system — stim-packs ('+') and field medkits ('H')
    public static final int   MEDKIT_STIM_HEAL                = 8;
    public static final int   MEDKIT_FULL_HEAL                = 25;
    public static final int   MEDKIT_TOTAL_CARRY_CAP          = 4;
    public static final int   MEDKIT_FULL_CARRY_CAP           = 2;
    public static final float PLAYER_HEAL_DURATION            = 0.18f;
    public static final int   KEY_HEAL                        = com.badlogic.gdx.Input.Keys.R;
    public static final float MEDKIT_STIM_SPRITE_HEIGHT       = 0.20f;
    public static final float MEDKIT_FULL_SPRITE_HEIGHT       = 0.30f;

    // Armour pickup system — shards ('a') and security vests ('A')
    public static final int   ARMOUR_SHARD_VALUE              = 5;
    public static final int   ARMOUR_VEST_VALUE               = 25;
    public static final float ARMOUR_SHARD_SPRITE_HEIGHT      = 0.25f;
    public static final float ARMOUR_VEST_SPRITE_HEIGHT       = 0.35f;
    // Fraction of each incoming hit that is absorbed by armour (depleting it instead of HP).
    public static final float ARMOUR_ABSORB_FRACTION          = 0.50f;
    // Mini-map relocated to top-left so it clears the HUD strip
    public static final float MINI_MAP_ORIGIN_X               = 8f;
    public static final float MINI_MAP_ORIGIN_Y               = WORLD_HEIGHT - 8f - MINI_MAP_WORLD_SIZE;

    // Impact effects — screen shake, hit particles, kill flash, floating damage numbers
    // Hit shake: small jolt confirming contact; duration matches the weapon fire lock
    public static final float HIT_SHAKE_MAGNITUDE           = 4f;
    public static final float HIT_SHAKE_DURATION_SECONDS    = 0.12f;
    // Kill shake: twice as strong and lasting — rewards the kill decisively
    public static final float KILL_SHAKE_MAGNITUDE          = 10f;
    public static final float KILL_SHAKE_DURATION_SECONDS   = 0.25f;
    // Kill flash: bright warm-white rects along all four screen edges; sin-curve fade
    public static final float KILL_FLASH_DURATION_SECONDS   = 0.35f;
    public static final float KILL_FLASH_MAX_ALPHA          = 0.70f;
    public static final float KILL_FLASH_EDGE_THICKNESS     = 110f;  // rect depth from each edge
    // Hit particles: square fragments ejecting from the enemy's screen-space position
    public static final int   HIT_PARTICLE_COUNT            = 8;
    public static final float HIT_PARTICLE_DURATION_SECONDS = 0.40f;
    public static final float HIT_PARTICLE_SPEED_MIN        = 60f;   // world units/s
    public static final float HIT_PARTICLE_SPEED_MAX        = 140f;
    public static final float HIT_PARTICLE_SIZE             = 4f;    // square side in world units
    public static final float HIT_PARTICLE_GRAVITY          = 80f;   // world units/s² downward arc
    public static final int   IMPACT_MAX_SIMULTANEOUS_HITS  = 12;    // pre-allocated pool ceiling
    // Death burst: expanding ring of dots + white-hot core at the kill position
    public static final float DEATH_BURST_LIFE_SECONDS      = 0.45f;
    public static final float DEATH_BURST_BASE_RADIUS       = 30f;   // minimum ring radius
    public static final float DEATH_BURST_SCALE_PER_HEIGHT  = 50f;   // radius += heightMultiplier×this
    // Floating damage numbers: "-N" text rising above the enemy on contact
    public static final float DAMAGE_NUMBER_DURATION_SECONDS = 0.80f;
    public static final float DAMAGE_NUMBER_RISE_SPEED       = 55f;  // world units/s upward
    public static final float DAMAGE_NUMBER_FONT_SCALE       = 1.4f;

    // Explosive barrel hazard — detonation damage and chain limit
    public static final int   EXPLOSION_DAMAGE    = 12;
    public static final int   EXPLOSION_CHAIN_MAX = 32;

    // Procedural Level Generation — LevelGenerator configuration (see docs/procedural-level-generation.txt)
    // Grid dimensions match the 80×45 tile layout that fills the 1280×720 world exactly.
    public static final int   LEVEL_GEN_GRID_WIDTH            = WORLD_WIDTH  / CELL_SIZE; // 80
    public static final int   LEVEL_GEN_GRID_HEIGHT           = WORLD_HEIGHT / CELL_SIZE; // 45
    // Interior tile count, excluding the 1-tile perimeter wall on each side.
    public static final int   LEVEL_GEN_ROOM_MIN_WIDTH        = 3;
    public static final int   LEVEL_GEN_ROOM_MIN_HEIGHT       = 3;
    public static final int   LEVEL_GEN_ROOM_MAX_WIDTH        = 12;
    public static final int   LEVEL_GEN_ROOM_MAX_HEIGHT       = 7;
    // Minimum gap between room bounding boxes so rooms never share a wall tile.
    public static final int   LEVEL_GEN_ROOM_MARGIN           = 2;
    public static final int   LEVEL_GEN_TARGET_ROOMS          = 10;
    public static final int   LEVEL_GEN_PLACEMENT_TRIES       = 300;
    // Probability that any interior floor tile in a non-entrance room receives a prop.
    public static final float LEVEL_GEN_PROP_CHANCE           = 0.13f;
    public static final int   LEVEL_GEN_MAX_ENEMIES_PER_ROOM  = 3;
    // Probability that a spawn point produces a Corruptor ('1') vs Vortex Eye ('2').
    public static final float LEVEL_GEN_CORRUPTOR_RATIO       = 0.65f;
    // Probability that a corridor-room boundary 'l' tile becomes a door ('d').
    // 0.75 = roughly 3 out of 4 room entries get a door; some stay open for flow variety.
    public static final float LEVEL_GEN_DOOR_CHANCE           = 0.75f;
    // Probability that a wall tile adjacent to an explosive barrel ('E') becomes a hazard wall ('h').
    public static final float LEVEL_GEN_HAZARD_WALL_CHANCE    = 0.45f;
    // Maximum Manhattan distance (room-center to room-center) for optional loop corridors.
    // Keeps loop connections local so they add shortcuts rather than crossing the entire dungeon.
    public static final int   LEVEL_GEN_LOOP_MAX_DISTANCE     = 25;

    // Procedural wall placement chances — post-pass reskin probability for new atmospheric walls.
    public static final float LEVEL_GEN_RUST_WALL_CHANCE      = 0.60f; // 'x' near unlit tiles
    public static final float LEVEL_GEN_RUST_OIL_CHANCE       = 0.40f; // 'x' near oil/blood decals
    public static final float LEVEL_GEN_GORE_WALL_CHANCE      = 0.35f; // 'x' near enemy dens / corpses
    // Rust procedural wall texture generation
    public static final int   RUST_WALL_TEXTURE_SIZE          = 128;
    public static final long  RUST_WALL_SEED                  = 0x52757374L; // "Rust"
    public static final int   RUST_BLOB_COUNT                 = 14;
    public static final int   RUST_PIT_COUNT                  = 10;
    // Gore procedural wall texture generation
    public static final int   GORE_WALL_TEXTURE_SIZE          = 128;
    public static final long  GORE_WALL_SEED                  = 0x476F7265L; // "Gore"
    public static final int   GORE_BLOB_COUNT                 = 18;
    public static final int   GORE_VEIN_COUNT                 = 6;
    public static final int   GORE_GLINT_COUNT                = 25;
    public static final int   GORE_BONE_COUNT                 = 4;
    public static final float GORE_FLESH_THRESHOLD            = 0.35f;
    // Bulkhead procedural wall texture generation
    public static final int   BULKHEAD_WALL_TEXTURE_SIZE      = 128;
    public static final int   BULKHEAD_FRAME_WIDTH            = 14;
    public static final int   BULKHEAD_BOLT_SPACING           = 21;

    // Touch Controller — transparent on-screen button cluster for Android landscape
    // Safe zone: x 904..1264, y 160..440; spatially disjoint from HUD (y 0..130) and mini-map (top-left)
    public static final float TOUCH_BUTTON_SIZE          = 96f;
    public static final float TOUCH_BUTTON_CORNER_RADIUS = 16f;
    public static final float TOUCH_DIAMOND_CENTER_X     = 1124f;
    public static final float TOUCH_DIAMOND_CENTER_Y     = 300f;
    public static final float TOUCH_DIAMOND_ARM_OFFSET   = 92f;
    public static final float TOUCH_STRAFE_WIDTH         = 64f;
    public static final float TOUCH_STRAFE_HEIGHT        = 132f;
    public static final float TOUCH_STRAFE_COLUMN_X      = 904f;
    public static final float TOUCH_STRAFE_UPPER_Y       = 312f;
    public static final float TOUCH_STRAFE_LOWER_Y       = 164f;
    public static final float TOUCH_FILL_ALPHA_IDLE      = 0.22f;
    public static final float TOUCH_FILL_ALPHA_PRESSED   = 0.40f;
    public static final float TOUCH_RIM_ALPHA            = 0.55f;
    public static final float TOUCH_ICON_ALPHA_IDLE      = 0.70f;
    public static final float TOUCH_ICON_ALPHA_PRESSED   = 1.00f;
    public static final float TOUCH_ICON_ALPHA_LOCKED    = 0.35f;
    public static final float TOUCH_PRESS_GLOW_DURATION  = 0.12f;
    public static final float TOUCH_ICON_EXTENT          = 22f;
    public static final float TOUCH_RIM_THICKNESS        = 3f;
    public static final int   TOUCH_ARC_SEGMENTS         = 8;
    // Action button cluster — left side of screen, left-thumb reach (above left HUD panel, y > 130)
    public static final float TOUCH_FIRE_SIZE            = 110f;
    public static final float TOUCH_FIRE_CENTER_X        = 200f;
    public static final float TOUCH_FIRE_CENTER_Y        = 310f;
    public static final float TOUCH_ACTION_SIZE          = 82f;
    public static final float TOUCH_RELOAD_CENTER_X      = 90f;
    public static final float TOUCH_RELOAD_CENTER_Y      = 208f;
    public static final float TOUCH_SKIP_CENTER_X        = 330f;
    public static final float TOUCH_SKIP_CENTER_Y        = 208f;

    // Level transitions — stairs-down tile ('>' char)
    // STAIRS_DOWN_CHAR: classic roguelike glyph; exactly one per procedurally generated floor.
    public static final char  STAIRS_DOWN_CHAR                   = '>';
    // Decal-height sprite — drawn flat and low to the floor, like blood/oil.
    public static final float STAIRS_SPRITE_HEIGHT               = 0.20f;
    public static final float LEVEL_TRANSITION_FADE_OUT_SECONDS  = 0.30f;
    public static final float LEVEL_TRANSITION_FADE_IN_SECONDS   = 0.30f;
    public static final int   STARTING_DEPTH                     = 1;

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
}
