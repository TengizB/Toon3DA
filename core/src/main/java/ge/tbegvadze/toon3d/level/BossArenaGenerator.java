package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.util.Constants;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates a fixed boss-arena layout for any boss-floor depth.
 *
 * Layout (rows bottom to top, 80×45 grid, cell size 16):
 *
 *   Bottom section  (rows 0-14):  approach corridor — narrow 3-tile wide tunnel
 *                                  with the player start tile 'p' near the bottom.
 *   Row 15:                        door 'd' spanning the full corridor width.
 *   Top section     (rows 16-44): open arena room (walls on perimeter),
 *                                  'P' cover columns in a 2×2 cluster pattern,
 *                                  'n' boss spawn at arena center,
 *                                  '>' exit stairs above the boss at the top wall.
 *
 * The arena door locks when the boss awakens (handled by BossFloorController using
 * DoorManager.lockArenaDoor). The exit '>' is disabled by BossFloorController until
 * the boss dies — it converts the tile back to '>' only after death.
 *
 * Grid convention: (0,0) = bottom-left tile, Y-up.
 */
public final class BossArenaGenerator implements ILevelGenerator {

    // Arena geometry — all in tile coordinates
    private static final int GRID_WIDTH  = Constants.WORLD_WIDTH  / Constants.CELL_SIZE; // 80
    private static final int GRID_HEIGHT = Constants.WORLD_HEIGHT / Constants.CELL_SIZE; // 45

    // Corridor — 3 tiles wide, centred horizontally
    private static final int CORRIDOR_WIDTH    = 3;
    private static final int CORRIDOR_CENTER_X = GRID_WIDTH / 2;      // 40
    private static final int CORRIDOR_LEFT     = CORRIDOR_CENTER_X - CORRIDOR_WIDTH / 2; // 39
    private static final int CORRIDOR_RIGHT    = CORRIDOR_LEFT + CORRIDOR_WIDTH - 1;     // 41

    // Door row — separates corridor from arena
    private static final int DOOR_ROW = 14;

    // Arena room — rows above the door up to the top wall
    private static final int ARENA_BOTTOM = DOOR_ROW + 1; // 15
    private static final int ARENA_TOP    = GRID_HEIGHT - 2; // 43 (wall at row 44)

    // Player start — near the bottom of the approach corridor
    private static final int PLAYER_START_COLUMN = CORRIDOR_CENTER_X;
    private static final int PLAYER_START_ROW    = 2;

    // Boss spawn — centre of the arena
    private static final int BOSS_COLUMN = CORRIDOR_CENTER_X;
    private static final int BOSS_ROW    = (ARENA_BOTTOM + ARENA_TOP) / 2;

    // Exit stairs — one tile below the top wall inside the arena, centred
    private static final int EXIT_COLUMN = CORRIDOR_CENTER_X;
    private static final int EXIT_ROW    = ARENA_TOP - 1;

    // Cover columns — placed in symmetric groups inside the arena
    // Four pairs: left/right of centre at 1/3 and 2/3 arena depth
    private static final int ARENA_LEFT_WALL  = (GRID_WIDTH - (ARENA_TOP - ARENA_BOTTOM)) / 2;
    private static final int ARENA_RIGHT_WALL = GRID_WIDTH - ARENA_LEFT_WALL - 1;

    // Pre-computed door tile position (used by BossFloorController to lock/unlock).
    public static final int ARENA_DOOR_COLUMN = CORRIDOR_CENTER_X;
    public static final int ARENA_DOOR_ROW    = DOOR_ROW;

    @Override
    public Level generate() {
        int levelWidth  = GRID_WIDTH;
        int levelHeight = GRID_HEIGHT;
        char[][] matrix = new char[levelHeight][levelWidth];

        // Fill everything with outer wall
        for (int row = 0; row < levelHeight; row++) {
            for (int column = 0; column < levelWidth; column++) {
                matrix[row][column] = 'x';
            }
        }

        // Carve approach corridor (rows 0 to DOOR_ROW - 1)
        for (int row = 0; row < DOOR_ROW; row++) {
            for (int column = CORRIDOR_LEFT; column <= CORRIDOR_RIGHT; column++) {
                matrix[row][column] = ' ';
            }
        }

        // Place door at DOOR_ROW — only the corridor tiles get a door char
        for (int column = CORRIDOR_LEFT; column <= CORRIDOR_RIGHT; column++) {
            matrix[DOOR_ROW][column] = 'd';
        }

        // Determine arena side walls (square-ish room centred horizontally)
        int arenaHeight = ARENA_TOP - ARENA_BOTTOM + 1;
        int arenaWidth  = Math.min(arenaHeight + 10, levelWidth - 4); // generous width
        int arenaLeft   = (levelWidth - arenaWidth) / 2;
        int arenaRight  = arenaLeft + arenaWidth - 1;

        // Carve arena interior
        for (int row = ARENA_BOTTOM; row <= ARENA_TOP; row++) {
            for (int column = arenaLeft; column <= arenaRight; column++) {
                matrix[row][column] = ' ';
            }
        }

        // Arena perimeter walls — already 'x' from fill; just ensure the interior is open.
        // Place lit-floor lighting in the arena to set the mood
        for (int row = ARENA_BOTTOM + 1; row < ARENA_TOP; row++) {
            for (int column = arenaLeft + 1; column < arenaRight; column++) {
                if (matrix[row][column] == ' ') {
                    matrix[row][column] = 'l'; // lit floor
                }
            }
        }

        // Cover columns — symmetric pairs spaced through arena depth
        int arenaDepth = ARENA_TOP - ARENA_BOTTOM;
        int row1 = ARENA_BOTTOM + arenaDepth / 3;
        int row2 = ARENA_BOTTOM + 2 * arenaDepth / 3;
        int midColumn  = levelWidth / 2;
        int colSpacing = arenaWidth / 5;

        placeCoverColumn(matrix, midColumn - colSpacing, row1);
        placeCoverColumn(matrix, midColumn + colSpacing, row1);
        placeCoverColumn(matrix, midColumn - colSpacing, row2);
        placeCoverColumn(matrix, midColumn + colSpacing, row2);

        // Ensure boss and exit tiles are clear (cover columns might have landed on them)
        matrix[BOSS_ROW][BOSS_COLUMN]   = 'n'; // boss spawn marker
        matrix[EXIT_ROW][EXIT_COLUMN]   = '>'; // exit stairs

        // Player start
        matrix[PLAYER_START_ROW][PLAYER_START_COLUMN] = 'p';

        // Build enemy spawn list — extract 'n' for BossFloorController
        List<EnemySpawnPoint> spawnPoints = new ArrayList<>();
        for (int row = 0; row < levelHeight; row++) {
            for (int column = 0; column < levelWidth; column++) {
                if (matrix[row][column] == 'n') {
                    spawnPoints.add(new EnemySpawnPoint('n', column, row));
                    matrix[row][column] = 'l'; // replace with floor after recording position
                }
            }
        }

        return new Level(matrix, spawnPoints, new ArrayList<>());
    }

    private static void placeCoverColumn(char[][] matrix, int column, int row) {
        if (row >= 0 && row < matrix.length && column >= 0 && column < matrix[0].length) {
            matrix[row][column] = 'P';
        }
    }

    /** Returns the tile column where the boss spawns in the generated arena. */
    public static int getBossSpawnColumn() { return BOSS_COLUMN; }

    /** Returns the tile row where the boss spawns in the generated arena. */
    public static int getBossSpawnRow()    { return BOSS_ROW; }

    /** Returns the tile column of the exit stairs. */
    public static int getExitColumn()      { return EXIT_COLUMN; }

    /** Returns the tile row of the exit stairs. */
    public static int getExitRow()         { return EXIT_ROW; }
}
