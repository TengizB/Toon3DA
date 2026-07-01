package ge.tbegvadze.toon3d.util;

/**
 * Core game-wide constants: world/viewport, grid, player movement, mini-map,
 * raycasting geometry, and door timing.
 *
 * Domain-specific constants live in dedicated files in this package:
 *   WeaponConstants, EnemyConstants, HudConstants, RenderConstants,
 *   LevelGenConstants, ItemConstants, EffectConstants, ProgressionConstants,
 *   TouchConstants.
 */
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
    // These affect turn economy (how many enemy turns you eat per action) and are
    // therefore balance values — see BalanceConfig (SINGLE SOURCE OF TRUTH).
    public static final float PLAYER_MOVE_DURATION   = BalanceConfig.PLAYER_MOVE_DURATION;
    public static final float PLAYER_ROTATE_DURATION = BalanceConfig.PLAYER_ROTATE_DURATION;
    // FOV stored as degrees for readability; radians derived from it.
    public static final float PLAYER_FIELD_OF_VIEW_DEGREES = 90f;
    public static final float PLAYER_FIELD_OF_VIEW_RADIANS = PLAYER_FIELD_OF_VIEW_DEGREES * ((float) Math.PI / 180f);
    // 90-degree turn angles used by PlayerController rotation state machine.
    public static final float PLAYER_ROTATE_CCW_RADIANS = (float) (Math.PI / 2.0);
    public static final float PLAYER_ROTATE_CW_RADIANS  = -(float) (Math.PI / 2.0);

    // Mini-map — renders (2*RADIUS+1)² tile window centred on player at world (0,0)
    public static final int   MINI_MAP_TILE_RADIUS  = 12;
    public static final int   MINI_MAP_TILE_COUNT   = MINI_MAP_TILE_RADIUS * 2 + 1;
    public static final float MINI_MAP_WORLD_SIZE   = 300f;
    public static final float MINI_MAP_CELL_SIZE     = MINI_MAP_WORLD_SIZE / MINI_MAP_TILE_COUNT;
    public static final float MINI_MAP_CENTER_X      = MINI_MAP_TILE_RADIUS * MINI_MAP_CELL_SIZE + MINI_MAP_CELL_SIZE / 2f;
    public static final float MINI_MAP_CENTER_Y      = MINI_MAP_TILE_RADIUS * MINI_MAP_CELL_SIZE + MINI_MAP_CELL_SIZE / 2f;
    // Mini-map relocated to top-left so it clears the HUD strip
    public static final float MINI_MAP_ORIGIN_X               = 8f;
    public static final float MINI_MAP_ORIGIN_Y               = WORLD_HEIGHT - 8f - MINI_MAP_WORLD_SIZE;

    // Mini-map player marker — enlarged dot + facing wedge (replaces the old unreadable facing line)
    public static final float MINI_MAP_PLAYER_DOT_RADIUS        = MINI_MAP_CELL_SIZE * 0.42f;
    public static final float MINI_MAP_FACING_WEDGE_LENGTH      = MINI_MAP_CELL_SIZE * 1.40f;
    public static final float MINI_MAP_FACING_WEDGE_HALF_WIDTH  = MINI_MAP_CELL_SIZE * 0.55f;
    public static final float MINI_MAP_FACING_WEDGE_BACK        = MINI_MAP_CELL_SIZE * 0.35f;
    // Mini-map overlay markers — props, pickups, exit, and alerted-enemy threat chevrons
    public static final float MINI_MAP_MARKER_SIZE              = MINI_MAP_CELL_SIZE * 0.55f;
    public static final float MINI_MAP_PROP_SIZE                = MINI_MAP_CELL_SIZE * 0.60f;
    public static final float MINI_MAP_PULSE_HZ                 = 2.5f;

    // Raycasting (DDA)
    public static final int   RAY_COUNT             = 60;
    public static final float RAY_MAX_LENGTH_CELLS  = 20f;

    // Doors — 'd' cells; runtime state in DoorManager
    // OPEN_DURATION matches PlayerController INTERACTING animation for first W press
    public static final float DOOR_OPEN_DURATION  = 0.30f;
    public static final float DOOR_CLOSE_DURATION = 0.40f;
    // DDA ray may pass through a door only when open fraction exceeds this threshold
    public static final float DOOR_OPEN_THROUGH_THRESHOLD = 0.99f;

    // Tick event bus — fixed subscriber capacity; generous ceiling for future systems
    public static final int MAX_TICK_SUBSCRIBERS = 16;

    // Boss floor system — every BOSS_FLOOR_INTERVAL-th floor is a boss arena
    public static final int   BOSS_FLOOR_INTERVAL         = 5;
    public static final float BOSS_PHASE2_HP_THRESHOLD    = 0.50f;
    public static final int   BOSS_PHASE_TRANSITION_TURNS = 1;
    public static final float BOSS_INTRO_DURATION_SECONDS = 2.5f;
    public static final float BOSS_DEPTH_HP_SCALE         = 0.20f;
    public static final float BOSS_DEPTH_DAMAGE_SCALE     = 0.12f;
    // Proximity distance (Chebyshev) at which the player triggers the boss to awaken
    public static final int   BOSS_AWAKEN_RADIUS_TILES    = 12;
}
