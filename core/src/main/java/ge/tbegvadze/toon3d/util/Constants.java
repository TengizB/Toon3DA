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
    public static final float PLAYER_MOVE_DURATION   = 0.12f;
    public static final float PLAYER_ROTATE_DURATION = 0.12f;
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
    public static final float MINI_MAP_PLAYER_RADIUS = MINI_MAP_CELL_SIZE / 4f;
    public static final float MINI_MAP_CENTER_X      = MINI_MAP_TILE_RADIUS * MINI_MAP_CELL_SIZE + MINI_MAP_CELL_SIZE / 2f;
    public static final float MINI_MAP_CENTER_Y      = MINI_MAP_TILE_RADIUS * MINI_MAP_CELL_SIZE + MINI_MAP_CELL_SIZE / 2f;
    // Mini-map relocated to top-left so it clears the HUD strip
    public static final float MINI_MAP_ORIGIN_X               = 8f;
    public static final float MINI_MAP_ORIGIN_Y               = WORLD_HEIGHT - 8f - MINI_MAP_WORLD_SIZE;

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
}
