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
    public static final int KEY_STRAFE_RIGHT  = com.badlogic.gdx.Input.Keys.E;
    public static final int KEY_INTERACT      = com.badlogic.gdx.Input.Keys.F;
    public static final int KEY_FIRE          = com.badlogic.gdx.Input.Keys.SPACE;
    public static final int KEY_SKIP_TURN     = com.badlogic.gdx.Input.Keys.TAB;
    public static final int KEY_SWITCH_WEAPON = com.badlogic.gdx.Input.Keys.G;

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
    public static final int     SHOTGUN_DAMAGE             = 50;
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

    // Double-Barrel Shotgun stats — higher burst damage, shorter range, 2-shot clip, slower reload
    // Damage table (coefficient 0.22, floor 0.15):
    //   distance 1: 32 × 0.78 = 25   distance 2: 32 × 0.56 = 18
    //   distance 3: 32 × 0.34 = 11   distance 4: 32 × 0.15 =  5   (clamped by floor)
    public static final int     DBL_SHOTGUN_DAMAGE             = 32;
    public static final int     DBL_SHOTGUN_CLIP_SIZE          = 2;
    public static final int     DBL_SHOTGUN_RELOAD_TIME_TICKS  = 2;
    public static final float   DBL_SHOTGUN_DAMAGE_DROP_COEFF  = 0.22f;
    public static final int     DBL_SHOTGUN_RANGE_TILES        = 4;
    // DBL_SHOTGUN_PENETRATION: false = stops at first enemy (spread dissipates on first target)
    public static final boolean DBL_SHOTGUN_PENETRATION        = false;

    // Double-Barrel Shotgun HUD textures — always procedural (no asset files)
    public static final String DBL_SHOTGUN_NORMAL_TEXTURE_PATH = "textures/guns/dbl_shotgun/dbl_shotgun.png";
    public static final String DBL_SHOTGUN_FIRE_TEXTURE_PATH   = "textures/guns/dbl_shotgun/dbl_shotgun_fire.png";
    public static final String DBL_SHOTGUN_RELOAD_TEXTURE_PATH = "textures/guns/dbl_shotgun/dbl_shotgun_reload.png";
    // Double-Barrel Shotgun procedural canvas — ShapeRenderer renders into this offscreen FrameBuffer
    public static final int DBL_SHOTGUN_CANVAS_WIDTH  = 192;
    public static final int DBL_SHOTGUN_CANVAS_HEIGHT = 134;
    // Display name shown in the HUD ammo readout
    public static final String DBL_SHOTGUN_DISPLAY_NAME = "DBL SHOTGUN";

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

    // Chaingun — triple-barrel rotary weapon, sustained fire, medium range
    public static final String CHAINGUN_DISPLAY_NAME        = "CHAINGUN";
    // Chaingun stats — rapid-fire rotary weapon, 8-shot clip, 3-tick reload, medium range
    // Damage table (coefficient 0.10, floor 0.15):
    //   distance 1: 10 × 0.90 = 9    distance 2: 10 × 0.80 = 8
    //   distance 4: 10 × 0.60 = 6    distance 6: 10 × 0.40 = 4
    //   distance 8: 10 × 0.20 = 2    (minimum floor applies at extreme range)
    public static final int     CHAINGUN_DAMAGE             = 10;
    // 8 bursts × 3 shots = 24 total rounds per clip
    public static final int     CHAINGUN_CLIP_SIZE          = 24;
    // Each press of fire launches CHAINGUN_BURST_SIZE simultaneous bullets in one volley
    public static final int     CHAINGUN_BURST_SIZE         = 3;
    public static final int     CHAINGUN_RELOAD_TIME_TICKS  = 3;
    public static final float   CHAINGUN_DAMAGE_DROP_COEFF  = 0.10f;
    public static final int     CHAINGUN_RANGE_TILES        = 8;
    // CHAINGUN_PENETRATION: false = stops at first enemy (bullets are stopped by armour)
    public static final boolean CHAINGUN_PENETRATION        = false;

    // Chaingun HUD textures — always procedural (no asset files)
    public static final String CHAINGUN_NORMAL_TEXTURE_PATH = "textures/guns/chaingun/chaingun.png";
    public static final String CHAINGUN_FIRE_TEXTURE_PATH   = "textures/guns/chaingun/chaingun_fire.png";
    public static final String CHAINGUN_RELOAD_TEXTURE_PATH = "textures/guns/chaingun/chaingun_reload.png";
    // Chaingun procedural canvas — ShapeRenderer renders into this offscreen FrameBuffer
    public static final int CHAINGUN_CANVAS_WIDTH  = 192;
    public static final int CHAINGUN_CANVAS_HEIGHT = 134;

    // Plasma muzzle blast — blue-cyan sphere burst; replaces the shotgun orange flame
    public static final float PLASMA_BLAST_RADIUS = 85f;

    // Weapon HUD rendering — sprite anchored at screen bottom-centre
    // drawX = (WORLD_WIDTH - WEAPON_HUD_WIDTH) / 2f; drawY = WEAPON_HUD_BASE_Y
    // Canvas is 192×134; grip occupies Y=0..14 (transparent, cut off below screen edge).
    // Visible height = WEAPON_HUD_HEIGHT + WEAPON_HUD_BASE_Y = 268 − 28 = 240 world units
    //   = exactly 1/3 of WORLD_HEIGHT (720).  Width maintains the 192:134 canvas aspect ratio.
    public static final float WEAPON_HUD_WIDTH  = 384f;
    public static final float WEAPON_HUD_HEIGHT = 268f;
    // Transparent grip area at canvas bottom = (14/134) × 268 ≈ 28 world units.
    // Setting drawY to -28 hides the grip below the screen edge, putting the body flush.
    public static final float WEAPON_HUD_BASE_Y = -28f;
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
    public static final String  ENEMY_GHOUL_PATH                 = "textures/enemies/enemy_ghoul.png";
    public static final String  ENEMY_CRAWLER_PATH               = "textures/enemies/enemy_crawler.png";
    public static final String  ENEMY_REVENANT_PATH              = "textures/enemies/enemy_revenant.png";
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
    public static final int     VORTEX_EYE_RANGE_TILES           = 2;
    public static final int     VORTEX_EYE_KITE_MIN_TILES        = 2;
    public static final float   VORTEX_EYE_HEIGHT_MULTIPLIER     = 0.55f;
    public static final float   VORTEX_EYE_HOVER_OFFSET_FRACTION = 0.25f;
    public static final int     LIGHT_MELEE_MAX_HEALTH           = 18;
    public static final int     LIGHT_MELEE_ATTACK_DAMAGE        = 10;
    public static final int     LIGHT_MELEE_MOVE_EVERY_N_TURNS   = 1;
    public static final float   LIGHT_MELEE_HEIGHT_MULTIPLIER    = 0.85f;
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
    public static final int   LEVEL_GEN_ROOM_MAX_WIDTH        = 20;
    public static final int   LEVEL_GEN_ROOM_MAX_HEIGHT       = 14;
    // Minimum gap between room bounding boxes so rooms never share a wall tile.
    public static final int   LEVEL_GEN_ROOM_MARGIN           = 2;
    public static final int   LEVEL_GEN_TARGET_ROOMS          = 16;
    public static final int   LEVEL_GEN_PLACEMENT_TRIES       = 600;
    // Probability that any interior floor tile in a non-entrance room receives a prop.
    public static final float LEVEL_GEN_PROP_CHANCE           = 0.13f;
    public static final int   LEVEL_GEN_MAX_ENEMIES_PER_ROOM  = 3;
    // Probability that a spawn point produces a Corruptor ('1') vs Vortex Eye ('2').
    // Cumulative thresholds for enemy type selection (each range maps to one type):
    //   [0.00, 0.25) → CORRUPTOR   25 % heavy melee
    //   [0.25, 0.40) → VORTEX_EYE  15 % ranged
    //   [0.40, 0.60) → GHOUL        20 % light melee
    //   [0.60, 0.80) → CRAWLER      20 % light melee
    //   [0.80, 1.00) → REVENANT     20 % light melee
    public static final float LEVEL_GEN_CORRUPTOR_THRESHOLD   = 0.25f;
    public static final float LEVEL_GEN_VORTEX_EYE_THRESHOLD  = 0.40f;
    public static final float LEVEL_GEN_GHOUL_THRESHOLD        = 0.60f;
    public static final float LEVEL_GEN_CRAWLER_THRESHOLD      = 0.80f;
    // Probability that a corridor-room boundary 'l' tile becomes a door ('d').
    // 0.75 = roughly 3 out of 4 room entries get a door; some stay open for flow variety.
    public static final float LEVEL_GEN_DOOR_CHANCE           = 0.75f;
    // Probability that a wall tile adjacent to an explosive barrel ('E') becomes a hazard wall ('h').
    public static final float LEVEL_GEN_HAZARD_WALL_CHANCE    = 0.45f;
    // Maximum Manhattan distance (room-center to room-center) for optional loop corridors.
    // Keeps loop connections local so they add shortcuts rather than crossing the entire dungeon.
    public static final int   LEVEL_GEN_LOOP_MAX_DISTANCE     = 25;

    // --- Room type system ---
    // LARGE rooms: interior must meet LARGE_MIN_DIM in both axes to be eligible.
    public static final int   LEVEL_GEN_LARGE_MIN_DIM              = 9;
    // Probability that an eligible (large enough) room becomes a LARGE landmark room.
    public static final float LEVEL_GEN_LARGE_ROOM_CHANCE          = 0.55f;
    // Hard cap: at most this many LARGE rooms per level so they feel special.
    public static final int   LEVEL_GEN_LARGE_ROOM_MAX_PER_LEVEL   = 3;
    // Column count range for LARGE rooms (more than the standard 1-3).
    public static final int   LEVEL_GEN_LARGE_COLUMN_MIN           = 4;
    public static final int   LEVEL_GEN_LARGE_COLUMN_MAX           = 6;
    // Prop density for LARGE rooms — sparse so the open floor stays navigable.
    public static final float LEVEL_GEN_LARGE_PROP_CHANCE          = 0.06f;
    // Probability that a SERVER_ROOM perimeter wall tile is converted to terminal wall 't'.
    public static final float LEVEL_GEN_SERVER_WALL_TERMINAL_CHANCE = 0.80f;
    // Probability that any small/medium room becomes a SERVER_ROOM (data vault).
    public static final float LEVEL_GEN_SERVER_ROOM_CHANCE         = 0.18f;
    // Hard cap: at most this many SERVER_ROOMs per level so they feel special.
    public static final int   LEVEL_GEN_SERVER_ROOM_MAX_PER_LEVEL  = 2;
    // Probability a floor tile in a server room becomes a flickering tile.
    public static final float LEVEL_GEN_SERVER_FLICKER_CHANCE      = 0.12f;
    // Ratio of rack props that are lockers vs terminals in a server room.
    public static final float LEVEL_GEN_SERVER_LOCKER_RATIO        = 0.30f;
    // Boosted pickup chances for special rooms (loot hubs / set-piece arenas).
    public static final float LEVEL_GEN_SERVER_MEDKIT_CHANCE       = 0.55f;
    public static final float LEVEL_GEN_SERVER_ARMOUR_CHANCE       = 0.35f;
    public static final float LEVEL_GEN_LARGE_MEDKIT_CHANCE        = 0.50f;
    public static final float LEVEL_GEN_LARGE_ARMOUR_CHANCE        = 0.30f;

    // --- Wide hallway generation ---
    // Number of MST edges widened to 3-tile grand corridors per level.
    public static final int   LEVEL_GEN_WIDE_HALLWAY_COUNT          = 3;
    // Spine column spacing for wide-hall 'P' columns (place one every N eligible spine tiles).
    public static final int   LEVEL_GEN_WIDE_HALLWAY_COLUMN_SPACING = 3;

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

    // -------------------------------------------------------------------------
    // NEW WALL TYPES — procedural textures ('N','Q','S','M','Z','U','X')
    // Note: 'R' and 'Y' are reserved for red/yellow keycard doors — use 'S'/'U'.
    // -------------------------------------------------------------------------

    // 'N' — Reinforced Containment Glass (cracked observation window)
    public static final int   GLASS_WALL_TEXTURE_SIZE         = 128;
    public static final long  GLASS_WALL_SEED                 = 0x476C6173L; // "Glas"
    public static final int   GLASS_CRACK_BRANCH_COUNT        = 5;
    public static final int   GLASS_CRACK_STEPS               = 30;
    public static final int   GLASS_GHOST_BLOB_COUNT          = 3;
    public static final int   GLASS_SHEEN_STREAK_COUNT        = 2;

    // 'Q' — Bio-Containment / Quarantine Wall
    public static final int   BIO_WALL_TEXTURE_SIZE           = 128;
    public static final long  BIO_WALL_SEED                   = 0x42696F57L; // "BioW"
    public static final int   BIO_STRIPE_PITCH                = 10;

    // 'S' — Emergency Lighting Strip Wall (static red — maps visual alarm state)
    public static final int   EMERG_WALL_TEXTURE_SIZE         = 128;
    public static final long  EMERG_WALL_SEED                 = 0x456D6572L; // "Emer"
    public static final int   EMERG_CAGE_COUNT                = 4;

    // 'M' — Medical Tile Wall (white tile + green accent + cross)
    public static final int   MED_WALL_TEXTURE_SIZE           = 128;
    public static final long  MED_WALL_SEED                   = 0x4D656469L; // "Medi"
    public static final int   MED_TILE_SIZE                   = 16;
    public static final int   MED_BLOOD_FLECK_COUNT           = 12;

    // 'Z' — Cryo / Frost-Damaged Wall (ice rime, icicles, frost fractures)
    public static final int   CRYO_WALL_TEXTURE_SIZE          = 128;
    public static final long  CRYO_WALL_SEED                  = 0x4372796FL; // "Cryo"
    public static final int   CRYO_ICICLE_COUNT               = 4;
    public static final int   CRYO_FRACTURE_COUNT             = 3;
    public static final int   CRYO_GLINT_COUNT                = 15;
    public static final int   CRYO_FROST_CORNER_RADIUS        = 40;

    // 'U' — Radiation-Burned Wall (scorched black, glowing green-yellow cracks)
    public static final int   RAD_WALL_TEXTURE_SIZE           = 128;
    public static final long  RAD_WALL_SEED                   = 0x52616469L; // "Radi"
    public static final int   RAD_CRACK_COUNT                 = 5;
    public static final int   RAD_CRACK_STEPS                 = 35;
    public static final int   RAD_DUST_COUNT                  = 20;

    // 'X' — Blast-Scarred Wall (bullet pocks, blast craters, through-holes)
    public static final int   BLAST_WALL_TEXTURE_SIZE         = 128;
    public static final long  BLAST_WALL_SEED                 = 0x426C6173L; // "Blas"
    public static final int   BLAST_BULLET_HOLE_COUNT         = 18;
    public static final int   BLAST_CRATER_COUNT              = 3;
    public static final int   BLAST_THROUGH_HOLE_COUNT        = 2;
    public static final int   BLAST_SCORCH_COUNT              = 4;

    // -------------------------------------------------------------------------
    // NEW ROOM TYPES — level generator configuration
    // -------------------------------------------------------------------------

    // MEDICAL_BAY — guaranteed once per level (reliable heal stop)
    public static final int   LEVEL_GEN_MEDICAL_BAY_MIN_WIDTH    = 7;
    public static final int   LEVEL_GEN_MEDICAL_BAY_MIN_HEIGHT   = 6;
    public static final float LEVEL_GEN_MEDICAL_WALL_CHANCE      = 0.70f; // 'M' dominant
    public static final float LEVEL_GEN_MEDICAL_BIO_WALL_CHANCE  = 0.10f; // 'Q' accent
    public static final float LEVEL_GEN_MEDICAL_PROP_CHANCE      = 0.16f;
    public static final int   LEVEL_GEN_MEDICAL_BAY_MAX          = 1;

    // ARMORY — present in ~80% of levels, at most once
    public static final float LEVEL_GEN_ARMORY_CHANCE            = 0.80f;
    public static final int   LEVEL_GEN_ARMORY_MIN_WIDTH         = 6;
    public static final int   LEVEL_GEN_ARMORY_MIN_HEIGHT        = 6;
    public static final float LEVEL_GEN_ARMORY_BLAST_WALL_CHANCE = 0.50f; // 'X' dominant
    public static final float LEVEL_GEN_ARMORY_PROP_CHANCE       = 0.22f;
    public static final int   LEVEL_GEN_ARMORY_MIN_WEAPON_RACKS  = 2;
    public static final int   LEVEL_GEN_ARMORY_MAX               = 1;

    // CRYO_CHAMBER — ~16% of levels, at most 2
    public static final float LEVEL_GEN_CRYO_CHANCE              = 0.16f;
    public static final int   LEVEL_GEN_CRYO_MIN_WIDTH           = 7;
    public static final int   LEVEL_GEN_CRYO_MIN_HEIGHT          = 7;
    public static final float LEVEL_GEN_CRYO_WALL_CHANCE         = 0.70f; // 'Z' dominant
    public static final float LEVEL_GEN_CRYO_GLASS_WALL_CHANCE   = 0.15f; // 'N' accent
    public static final float LEVEL_GEN_CRYO_PROP_CHANCE         = 0.20f;
    public static final int   LEVEL_GEN_CRYO_MAX                 = 2;

    // POWER_PLANT — ~45% of LARGE-eligible rooms, at most 1
    public static final float LEVEL_GEN_POWERPLANT_CHANCE        = 0.45f;
    public static final float LEVEL_GEN_POWERPLANT_RAD_WALL_CHANCE = 0.40f; // 'U' near core
    public static final float LEVEL_GEN_POWERPLANT_EMERG_WALL_CHANCE = 0.20f; // 'S' approach
    public static final float LEVEL_GEN_POWERPLANT_PROP_CHANCE   = 0.18f;
    public static final int   LEVEL_GEN_POWERPLANT_MIN_GENERATORS = 2;
    public static final int   LEVEL_GEN_POWERPLANT_MAX_GENERATORS = 4;
    public static final int   LEVEL_GEN_POWERPLANT_MAX           = 1;

    // COMMAND_CENTER — ~50% of levels, deepest large room, at most 1
    public static final float LEVEL_GEN_COMMAND_CHANCE           = 0.50f;
    public static final int   LEVEL_GEN_COMMAND_MIN_WIDTH        = 8;
    public static final int   LEVEL_GEN_COMMAND_MIN_HEIGHT       = 7;
    public static final float LEVEL_GEN_COMMAND_GLASS_WALL_CHANCE = 0.30f; // 'N' one wall band
    public static final float LEVEL_GEN_COMMAND_EMERG_WALL_CHANCE = 0.15f; // 'S' accent
    public static final float LEVEL_GEN_COMMAND_PROP_CHANCE      = 0.15f;
    public static final int   LEVEL_GEN_COMMAND_MIN_TERMINALS    = 3;
    public static final int   LEVEL_GEN_COMMAND_MAX_TERMINALS    = 5;
    public static final int   LEVEL_GEN_COMMAND_MAX              = 1;

    // CONTAINMENT_BLOCK — ~16% of levels, at most 2
    public static final float LEVEL_GEN_CONTAINMENT_CHANCE       = 0.16f;
    public static final int   LEVEL_GEN_CONTAINMENT_MIN_WIDTH    = 8;
    public static final int   LEVEL_GEN_CONTAINMENT_MIN_HEIGHT   = 6;
    public static final float LEVEL_GEN_CONTAINMENT_GLASS_CHANCE = 0.40f; // 'N' cell fronts
    public static final float LEVEL_GEN_CONTAINMENT_BIO_CHANCE   = 0.15f; // 'Q' approach
    public static final float LEVEL_GEN_CONTAINMENT_PROP_CHANCE  = 0.18f;
    public static final int   LEVEL_GEN_CONTAINMENT_MAX          = 2;

    // Global accent wall chances (post-pass, any room type)
    public static final float LEVEL_GEN_EMERG_STRIP_CORRIDOR_CHANCE = 0.25f; // 'S' near keycard doors
    public static final float LEVEL_GEN_BLAST_NEAR_CORPSE_CHANCE    = 0.10f; // 'X' near corpse clusters

    // -------------------------------------------------------------------------
    // NEW PROP HEIGHT MULTIPLIERS (relative to full wall stripe)
    // -------------------------------------------------------------------------
    public static final float PROP_CAMERA_HEIGHT      = 0.80f; // '#' security camera
    public static final float PROP_GENERATOR_HEIGHT   = 0.85f; // '%' power generator / reactor
    public static final float PROP_BIOPOD_HEIGHT      = 0.90f; // '&' bio-pod / specimen tank
    public static final float PROP_RACK_HEIGHT        = 0.70f; // '=' weapon rack
    public static final float PROP_VENDOR_HEIGHT      = 0.88f; // '@' vending / supply dispenser

    // Touch Controller — 4-row grid, right side of screen, all buttons 96×96
    // Grid columns (center X): left=1004, center=1100, right=1196
    // Grid rows   (center Y): row1(BACK)=200, row2(FIRE/ROT)=296, row3(FWD/STR)=392, row4(RELOAD/SKIP)=488
    // Safe zone: x 956..1244, y 152..536; above HUD (y 0..130), avoids mini-map (top-left)
    public static final float TOUCH_BUTTON_SIZE          = 96f;
    public static final float TOUCH_BUTTON_CORNER_RADIUS = 16f;
    public static final float TOUCH_GRID_CENTER_X        = 1100f;  // center-column X
    public static final float TOUCH_GRID_BASE_Y          = 200f;   // center Y of bottom row (BACK)
    public static final float TOUCH_GRID_ARM_OFFSET      = 96f;    // row & column spacing (= button size → rows touch)
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

    // Event Text (F1) — screen-space rising text for game events
    public static final int   EVENT_TEXT_MAX             = 8;
    public static final float EVENT_TEXT_LIFE_SECONDS    = 1.5f;
    public static final float EVENT_TEXT_RISE_PIXELS     = 70f;
    public static final float EVENT_TEXT_ANCHOR_Y        = 470f;
    public static final float EVENT_TEXT_LINE_STEP       = 34f;

    // Hit Vignette (F2) — red screen-edge flash when player takes damage
    public static final float HIT_VIGNETTE_FADE_SECONDS  = 0.6f;
    public static final float HIT_VIGNETTE_MAX_ALPHA     = 0.55f;

    // Level transitions — stairs-down tile ('>' char)
    // STAIRS_DOWN_CHAR: classic roguelike glyph; exactly one per procedurally generated floor.
    public static final char  STAIRS_DOWN_CHAR                   = '>';
    // Decal-height sprite — drawn flat and low to the floor, like blood/oil.
    public static final float STAIRS_SPRITE_HEIGHT               = 0.20f;
    public static final float LEVEL_TRANSITION_FADE_OUT_SECONDS  = 0.30f;
    public static final float LEVEL_TRANSITION_FADE_IN_SECONDS   = 0.30f;
    public static final int   STARTING_DEPTH                     = 1;

    // Railgun — charge-up infinite-pierce hitscan sniper (SLUGS ammo)
    // Damage table (coefficient 0.02, floor 0.70):
    //   charge 1 (half), distance 1: 40 × 1.00 = 40
    //   charge 1 (half), distance 16: 40 × max(0.70, 1 - 0.02×15) = 40 × 0.70 = 28
    //   charge 2 (full), distance 1: 90 × 1.00 = 90
    //   charge 2 (full), distance 16: 90 × 0.70 = 63
    public static final int[]   RAILGUN_DAMAGE_BY_CHARGE          = {0, 40, 90};
    public static final int     RAILGUN_MAX_CHARGE                = 2;
    public static final float   RAILGUN_DROP_COEFF                = 0.02f;
    public static final float   RAILGUN_DAMAGE_MIN_MULTIPLIER     = 0.70f;
    public static final int     RAILGUN_RANGE_TILES               = 16;
    public static final int     RAILGUN_CLIP_SIZE                 = 1;
    public static final int     RAILGUN_RELOAD_TIME_TICKS         = 4;
    public static final int     RAILGUN_PICKUP_SLUGS              = 4;
    public static final int     RAILGUN_MAX_SLUGS                 = 12;
    public static final boolean RAILGUN_PENETRATION               = true;
    public static final float   RAILGUN_BEAM_DURATION             = 0.14f;
    public static final float   RAILGUN_SHAKE_INTENSITY           = 10f;
    public static final float   RAILGUN_SCREEN_FLASH_ALPHA        = 0.45f;
    public static final int     RAILGUN_CANVAS_WIDTH              = 192;
    public static final int     RAILGUN_CANVAS_HEIGHT             = 134;
    public static final String  RAILGUN_NORMAL_TEXTURE_PATH       = "textures/guns/railgun/railgun.png";
    public static final String  RAILGUN_FIRE_TEXTURE_PATH         = "textures/guns/railgun/railgun_fire.png";
    public static final String  RAILGUN_RELOAD_TEXTURE_PATH       = "textures/guns/railgun/railgun_reload.png";

    // Incinerator — short-range cone flamethrower (FUEL ammo)
    // Impact damage applied to every enemy in the cone on each spray.
    // Depth-3 (far-edge) tiles use FLAME_FALLOFF instead of FLAME_IMPACT_DAMAGE.
    // FLAME_DAMAGE_DROP_COEFF = 0.0: depth falloff is handled explicitly, not by the drop curve.
    // Burn DoT constants (FLAME_BURN_*) are reserved for the enemy system when implemented.
    public static final int     FLAME_IMPACT_DAMAGE        = 8;
    public static final int     FLAME_FALLOFF              = 5;
    public static final int     FLAME_BURN_DAMAGE_PER_TURN = 6;
    public static final int     FLAME_BURN_TURNS           = 4;
    public static final float   FLAME_DAMAGE_DROP_COEFF    = 0.0f;
    public static final int     FLAME_RANGE_TILES          = 3;
    public static final int     FLAME_CLIP_SIZE            = 30;
    public static final int     FUEL_PER_SHOT              = 3;
    public static final int     FLAME_RELOAD_TICKS         = 3;
    public static final int     FLAME_PICKUP_FUEL          = 60;
    public static final int     FLAME_MAX_FUEL             = 120;
    // Cone offset table: each entry is {forwardDepth, lateralOffset}.
    // Lateral=0 reaches depth 1-3; lateral=+-1 reaches depth 2-3 only (7-tile fan).
    public static final int[][] FLAME_CONE_OFFSETS         = {{1,0},{2,-1},{2,0},{2,1},{3,-1},{3,0},{3,1}};
    public static final float   FLAME_SHAKE_INTENSITY      = 4f;
    public static final float   FLAME_SCREEN_GLOW_ALPHA    = 0.30f;
    // Incinerator procedural canvas — ShapeRenderer renders into this offscreen FrameBuffer
    public static final int     FLAME_CANVAS_WIDTH         = 192;
    public static final int     FLAME_CANVAS_HEIGHT        = 134;
    // Incinerator HUD textures — always procedural (no asset files)
    public static final String  FLAME_NORMAL_TEXTURE_PATH  = "textures/guns/incinerator/incinerator.png";
    public static final String  FLAME_FIRE_TEXTURE_PATH    = "textures/guns/incinerator/incinerator_fire.png";
    public static final String  FLAME_RELOAD_TEXTURE_PATH  = "textures/guns/incinerator/incinerator_reload.png";

    // Grenade Launcher — bouncing indirect-fire AoE splash weapon (GRENADES ammo)
    // Damage table (coefficient 0.0 — no travel falloff; splash is splash):
    //   impact tile:           GRENADE_SPLASH_DAMAGE  = 30 (full blast)
    //   4 orthogonal neighbours: GRENADE_FALLOFF_DAMAGE = 16 (edge of plus)
    //   player self-damage:    GRENADE_SELF_DAMAGE    = 20 (if caught in blast)
    // Per-shot ceiling: 5 enemies in plus = 30 + 4×16 = 94 distributed damage.
    public static final int     GRENADE_SPLASH_DAMAGE      = 30;
    public static final int     GRENADE_FALLOFF_DAMAGE     = 16;
    public static final int     GRENADE_SELF_DAMAGE        = 20;
    public static final float   GRENADE_DAMAGE_DROP_COEFF  = 0.0f;
    public static final int     GRENADE_RANGE_TILES        = 6;
    // GRENADE_ARM_TILES: grenade must travel this many tiles before it can detonate.
    // Prevents point-blank abuse; unarmed grenade passes through enemies harmlessly.
    public static final int     GRENADE_ARM_TILES          = 2;
    public static final int     GRENADE_CLIP_SIZE          = 3;
    public static final int     GRENADE_RELOAD_TIME_TICKS  = 4;
    public static final int     GRENADE_PICKUP_AMMO        = 6;
    public static final int     GRENADE_MAX_AMMO           = 18;
    // GRENADE_PENETRATION is not used by the custom marchShot, kept for API completeness.
    public static final boolean GRENADE_PENETRATION        = false;
    // 5-tile plus splash offsets: {forwardDelta, lateralDelta} relative to impact tile.
    // Index 0 = center (full damage); indices 1-4 = orthogonal neighbours (falloff damage).
    public static final int[][] GRENADE_SPLASH_OFFSETS     = {{0,0},{1,0},{-1,0},{0,1},{0,-1}};
    // Visual effect timings (reserved for ImpactEffectSystem wiring)
    public static final float   GRENADE_BLAST_DURATION     = 0.30f;
    public static final float   GRENADE_SHAKE_INTENSITY    = 8f;
    public static final float   GRENADE_SCREEN_FLASH_ALPHA = 0.35f;
    // Procedural canvas — ShapeRenderer renders into this offscreen FrameBuffer
    public static final int     GRENADE_CANVAS_WIDTH       = 192;
    public static final int     GRENADE_CANVAS_HEIGHT      = 134;
    // Grenade Launcher HUD textures — always procedural (no asset files)
    public static final String  GRENADE_NORMAL_TEXTURE_PATH  = "textures/guns/grenade/grenade.png";
    public static final String  GRENADE_FIRE_TEXTURE_PATH    = "textures/guns/grenade/grenade_fire.png";
    public static final String  GRENADE_RELOAD_TEXTURE_PATH  = "textures/guns/grenade/grenade_reload.png";

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
