package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.util.RenderConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads a FINISHED tile grid (balance-overhaul order 2, S1) and measures what the shared supply and
 * encounter placement need, identically for every generator: walk distance from the start with and
 * without keycards, the shortest start-to-exit path, the walkable footprint, and the ground-eligible
 * pickup slots tagged with the generator's own room ids ({@link SupplySlotProvider}).
 *
 * <p>Pure: reads the grid, writes nothing. Start = the {@code 'p'} tile, exit = the stairs tile.
 */
public final class SupplySlotSurvey {

    /** Walk distance meaning "not reachable". */
    public static final int UNREACHABLE = -1;

    private static final int[] STEP_COLUMNS = { 0, 0, 1, -1 };
    private static final int[] STEP_ROWS    = { 1, -1, 0, 0 };

    public final int startColumn;
    public final int startRow;
    /** Exit column, or -1 when the floor has no stairs tile. */
    public final int exitColumn;
    /** Exit row, or -1 when the floor has no stairs tile. */
    public final int exitRow;

    private final char[][]          grid;
    private final SupplySlotProvider provider;
    private final int[][]           walkWithoutKeycard;
    private final int[][]           walkWithKeycards;
    private final boolean[][]       exitPath;
    private final List<SupplySlot>  slots;
    private final int               walkableTileCount;
    private final int               maximumKeycardFreeDistance;

    private SupplySlotSurvey(char[][] grid, SupplySlotProvider provider) {
        this.grid     = grid;
        this.provider = provider;
        int[] start = find(grid, 'p');
        int[] exit  = find(grid, RenderConstants.STAIRS_DOWN_CHAR);
        this.startColumn = start == null ? -1 : start[0];
        this.startRow    = start == null ? -1 : start[1];
        this.exitColumn  = exit == null ? -1 : exit[0];
        this.exitRow     = exit == null ? -1 : exit[1];

        int[][] parentWithKeycards = new int[grid.length][grid[0].length];
        this.walkWithoutKeycard = breadthFirst(grid, startColumn, startRow, false, null);
        this.walkWithKeycards   = breadthFirst(grid, startColumn, startRow, true, parentWithKeycards);
        this.exitPath           = tracePath(parentWithKeycards, walkWithKeycards, exitColumn, exitRow);

        int walkable = 0;
        int farthest = 0;
        List<SupplySlot> eligible = new ArrayList<>();
        for (int tileRow = 0; tileRow < grid.length; tileRow++) {
            for (int tileColumn = 0; tileColumn < grid[0].length; tileColumn++) {
                char cell = grid[tileRow][tileColumn];
                if (isPassable(cell, true)) walkable++;
                int keycardFree = walkWithoutKeycard[tileRow][tileColumn];
                if (keycardFree > farthest) farthest = keycardFree;
                if (!isPlainFloor(cell)) continue;
                if (walkWithKeycards[tileRow][tileColumn] == UNREACHABLE) continue;
                if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
                boolean free = keycardFree != UNREACHABLE;
                int distance = free ? keycardFree : walkWithKeycards[tileRow][tileColumn];
                eligible.add(new SupplySlot(tileColumn, tileRow,
                        provider == null ? SupplySlotProvider.CONNECTOR_REGION
                                         : provider.supplyRegionAt(tileColumn, tileRow),
                        distance, free, exitPath[tileRow][tileColumn]));
            }
        }
        this.slots                      = Collections.unmodifiableList(eligible);
        this.walkableTileCount          = walkable;
        this.maximumKeycardFreeDistance = farthest;
    }

    /** Surveys a finished grid ({@code grid[row][column]}, row 0 = bottom) with the generator's room model. */
    public static SupplySlotSurvey survey(char[][] grid, SupplySlotProvider provider) {
        return new SupplySlotSurvey(grid, provider);
    }

    /** Every ground-eligible pickup slot (plain floor, reachable, not beside a door), in grid order. */
    public List<SupplySlot> slots() {
        return slots;
    }

    /** Walkable tiles on the floor (not a wall, not a solid prop) — the E6 footprint measure. */
    public int walkableTileCount() {
        return walkableTileCount;
    }

    /** The farthest keycard-free walk distance on the floor (the S4 "first half" yardstick is half of it). */
    public int maximumKeycardFreeDistance() {
        return maximumKeycardFreeDistance;
    }

    /** Keycard-free walk distance to a tile, or {@link #UNREACHABLE}. */
    public int walkDistanceWithoutKeycard(int tileColumn, int tileRow) {
        if (!inBounds(tileColumn, tileRow)) return UNREACHABLE;
        return walkWithoutKeycard[tileRow][tileColumn];
    }

    /** Walk distance to a tile when every keycard door may be opened, or {@link #UNREACHABLE}. */
    public int walkDistanceWithKeycards(int tileColumn, int tileRow) {
        if (!inBounds(tileColumn, tileRow)) return UNREACHABLE;
        return walkWithKeycards[tileRow][tileColumn];
    }

    /** Whether a tile lies on the shortest start-to-exit walk. */
    public boolean isOnExitPath(int tileColumn, int tileRow) {
        return inBounds(tileColumn, tileRow) && exitPath[tileRow][tileColumn];
    }

    /** The generator's room id for a tile ({@link SupplySlotProvider#CONNECTOR_REGION} without a provider). */
    public int regionAt(int tileColumn, int tileRow) {
        if (provider == null || !inBounds(tileColumn, tileRow)) return SupplySlotProvider.CONNECTOR_REGION;
        return provider.supplyRegionAt(tileColumn, tileRow);
    }

    /** Whether the generator flags a region LARGE. */
    public boolean isLargeRegion(int regionId) {
        return provider != null && regionId >= 0 && provider.isLargeSupplyRegion(regionId);
    }

    /** The start tile's room id. */
    public int startRegion() {
        return regionAt(startColumn, startRow);
    }

    /** The surveyed grid's width in tiles. */
    public int gridWidth() {
        return grid[0].length;
    }

    /** The surveyed grid's height in tiles. */
    public int gridHeight() {
        return grid.length;
    }

    // -------------------------------------------------------------------------
    // Tile classes shared by the placement passes
    // -------------------------------------------------------------------------

    /** Plain floor a pickup may overwrite: unlit ' ', lit 'l', dark 'u', flickering 'f'. */
    public static boolean isPlainFloor(char cell) {
        return cell == ' ' || cell == 'l' || cell == 'u' || cell == 'f';
    }

    /** Whether the player can stand on / walk through a tile (doors pass; locked doors only with keys). */
    static boolean isPassable(char cell, boolean keycardsAllowed) {
        if (Level.isLockedDoor(cell)) return keycardsAllowed;
        return !Level.isWall(cell) && !Level.isPropSolid(cell);
    }

    static boolean isAdjacentToDoor(char[][] grid, int tileColumn, int tileRow) {
        for (int direction = 0; direction < 4; direction++) {
            int neighborColumn = tileColumn + STEP_COLUMNS[direction];
            int neighborRow    = tileRow    + STEP_ROWS[direction];
            if (neighborRow < 0 || neighborRow >= grid.length
                    || neighborColumn < 0 || neighborColumn >= grid[0].length) continue;
            if (Level.isDoor(grid[neighborRow][neighborColumn])) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private boolean inBounds(int tileColumn, int tileRow) {
        return tileRow >= 0 && tileRow < grid.length && tileColumn >= 0 && tileColumn < grid[0].length;
    }

    private static int[] find(char[][] grid, char symbol) {
        for (int tileRow = 0; tileRow < grid.length; tileRow++) {
            for (int tileColumn = 0; tileColumn < grid[0].length; tileColumn++) {
                if (grid[tileRow][tileColumn] == symbol) return new int[]{tileColumn, tileRow};
            }
        }
        return null;
    }

    /** Breadth-first walk distances from the start; records parents (row * width + column) when asked. */
    private static int[][] breadthFirst(char[][] grid, int startColumn, int startRow, boolean keycardsAllowed,
                                        int[][] parents) {
        int height = grid.length;
        int width  = grid[0].length;
        int[][] distance = new int[height][width];
        for (int[] row : distance) java.util.Arrays.fill(row, UNREACHABLE);
        if (parents != null) for (int[] row : parents) java.util.Arrays.fill(row, -1);
        if (startRow < 0 || startColumn < 0) return distance;

        int[] queue = new int[width * height];
        int head = 0;
        int tail = 0;
        distance[startRow][startColumn] = 0;
        queue[tail++] = startRow * width + startColumn;
        while (head < tail) {
            int packed        = queue[head++];
            int currentRow    = packed / width;
            int currentColumn = packed % width;
            for (int direction = 0; direction < 4; direction++) {
                int neighborColumn = currentColumn + STEP_COLUMNS[direction];
                int neighborRow    = currentRow    + STEP_ROWS[direction];
                if (neighborRow < 0 || neighborRow >= height || neighborColumn < 0 || neighborColumn >= width) continue;
                if (distance[neighborRow][neighborColumn] != UNREACHABLE) continue;
                if (!isPassable(grid[neighborRow][neighborColumn], keycardsAllowed)) continue;
                distance[neighborRow][neighborColumn] = distance[currentRow][currentColumn] + 1;
                if (parents != null) parents[neighborRow][neighborColumn] = packed;
                queue[tail++] = neighborRow * width + neighborColumn;
            }
        }
        return distance;
    }

    private static boolean[][] tracePath(int[][] parents, int[][] distance, int exitColumn, int exitRow) {
        int height = parents.length;
        int width  = parents[0].length;
        boolean[][] path = new boolean[height][width];
        if (exitRow < 0 || exitColumn < 0 || distance[exitRow][exitColumn] == UNREACHABLE) return path;
        int packed = exitRow * width + exitColumn;
        while (packed >= 0) {
            int tileRow    = packed / width;
            int tileColumn = packed % width;
            path[tileRow][tileColumn] = true;
            packed = parents[tileRow][tileColumn];
        }
        return path;
    }
}
