package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.util.LevelGenConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * THE CORRIDOR ROUTER (level-generation-overhaul R12 / R13): routes a corridor from one socket's OUTER tile to
 * another's with a seeded A* on the tile grid. Room footprints (shell and interior) are impassable except the
 * two endpoints, so a corridor can never cut a shell (R9); a tile beside another room costs
 * {@code LEVEL_GEN_ROUTE_SHELL_HUG_COST}, an existing corridor tile {@code LEVEL_GEN_ROUTE_MERGE_COST} (so
 * corridors merge), every other tile {@code LEVEL_GEN_ROUTE_BASE_COST}; every turn adds {@code LEVEL_GEN_ROUTE_TURN_COST}
 * (straight legs, never a staircase). Ties are broken by a per-tile jitter
 * hashed from the route seed, so the route is deterministic without drawing from the layout's Random. It
 * returns the tiles; the caller carves them. {@link #widen} then makes an MST edge a 3-wide hall wherever
 * the band keeps a tile from every room, narrowing to one tile before each socket. Headless — no LibGDX.
 */
public final class CorridorRouter {

    private CorridorRouter() {}

    private static final int[] STEP_COLUMNS = { 0, 0, 1, -1 };
    private static final int[] STEP_ROWS    = { 1, -1, 0, 0 };

    /**
     * The cheapest route between two tiles, both endpoints included, or an empty list when none exists.
     *
     * @param grid        the grid ({@code grid[row][column]}); walkable tiles outside rooms are existing corridor
     * @param roomMask    [row][column] true on every room footprint (impassable, except the endpoints)
     * @param hugMask     [row][column] true on tiles orthogonally beside a room footprint
     * @param routeSeed   the seed hashed into the per-tile tie-break jitter
     */
    public static List<int[]> route(char[][] grid, boolean[][] roomMask, boolean[][] hugMask,
                                    int fromColumn, int fromRow, int toColumn, int toRow, long routeSeed) {
        int height = grid.length;
        int width  = grid[0].length;
        // The search state is (tile, arrival direction) so a turn can be charged; direction 4 = the start.
        int states = width * height * 5;
        float[] bestCost = new float[states];
        int[]   parent   = new int[states];
        java.util.Arrays.fill(bestCost, Float.MAX_VALUE);
        java.util.Arrays.fill(parent, -1);
        int start = (fromRow * width + fromColumn) * 5 + 4;
        int targetTile = toRow * width + toColumn;
        bestCost[start] = 0f;
        float heuristicScale = LevelGenConstants.LEVEL_GEN_ROUTE_MERGE_COST;
        PriorityQueue<float[]> open = new PriorityQueue<>((first, second) -> {
            int byPriority = Float.compare(first[0], second[0]);
            return byPriority != 0 ? byPriority : Float.compare(first[1], second[1]);
        });
        open.add(new float[]{ heuristicScale * (Math.abs(fromColumn - toColumn) + Math.abs(fromRow - toRow)), start });
        boolean[] closed = new boolean[states];
        int reached = -1;
        while (!open.isEmpty()) {
            float[] entry = open.poll();
            int current = (int) entry[1];
            if (closed[current]) continue;
            closed[current] = true;
            int currentTile = current / 5;
            int arrival     = current % 5;
            if (currentTile == targetTile) {
                reached = current;
                break;
            }
            int currentColumn = currentTile % width;
            int currentRow    = currentTile / width;
            for (int direction = 0; direction < 4; direction++) {
                int nextColumn = currentColumn + STEP_COLUMNS[direction];
                int nextRow    = currentRow + STEP_ROWS[direction];
                // The outer border stays solid rock.
                if (nextColumn <= 0 || nextRow <= 0 || nextColumn >= width - 1 || nextRow >= height - 1) continue;
                int nextTile = nextRow * width + nextColumn;
                if (roomMask[nextRow][nextColumn] && nextTile != targetTile) continue;
                int next = nextTile * 5 + direction;
                if (closed[next]) continue;
                float step = stepCost(grid, hugMask, nextColumn, nextRow) + jitter(nextColumn, nextRow, routeSeed);
                if (arrival != 4 && arrival != direction) step += LevelGenConstants.LEVEL_GEN_ROUTE_TURN_COST;
                float cost = bestCost[current] + step;
                if (cost >= bestCost[next]) continue;
                bestCost[next] = cost;
                parent[next]   = current;
                float priority = cost + heuristicScale * (Math.abs(nextColumn - toColumn) + Math.abs(nextRow - toRow));
                open.add(new float[]{ priority, next });
            }
        }
        List<int[]> path = new ArrayList<>();
        if (reached < 0) return path;
        for (int cursor = reached; cursor >= 0; cursor = parent[cursor]) {
            int tile = cursor / 5;
            path.add(new int[]{ tile % width, tile / width });
        }
        Collections.reverse(path);
        return path;
    }

    private static float stepCost(char[][] grid, boolean[][] hugMask, int tileColumn, int tileRow) {
        char cell = grid[tileRow][tileColumn];
        boolean existingCorridor = !Level.isWall(cell) && !Level.isPropSolid(cell) && !Level.isColumn(cell);
        if (existingCorridor) return LevelGenConstants.LEVEL_GEN_ROUTE_MERGE_COST;
        if (hugMask[tileRow][tileColumn]) return LevelGenConstants.LEVEL_GEN_ROUTE_SHELL_HUG_COST;
        return LevelGenConstants.LEVEL_GEN_ROUTE_BASE_COST;
    }

    /** A deterministic tie-break in [0, LEVEL_GEN_ROUTE_TIE_JITTER) per tile and route (SplitMix64 hash). */
    private static float jitter(int tileColumn, int tileRow, long routeSeed) {
        long mixed = routeSeed + 0x9E3779B97F4A7C15L * (tileRow * 131L + tileColumn + 1);
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        mixed = mixed ^ (mixed >>> 31);
        return ((mixed >>> 40) / (float) (1L << 24)) * LevelGenConstants.LEVEL_GEN_ROUTE_TIE_JITTER;
    }

    /**
     * Widens a routed path to a 3-wide hall (R13): each path tile further than {@code taperTiles} from both
     * ends carves its perpendicular neighbours to 'l', but only where the neighbour is rock and lies outside
     * {@code nearRoomMask} (within Chebyshev 1 of a room), so the band never touches a shell. Returns the
     * spine tiles that got at least one widened neighbour, in path order (for the hall's spaced columns).
     */
    public static List<int[]> widen(char[][] grid, List<int[]> path, boolean[][] nearRoomMask, int taperTiles) {
        List<int[]> spine = new ArrayList<>();
        for (int index = taperTiles; index < path.size() - taperTiles; index++) {
            int[] tile = path.get(index);
            boolean widened = false;
            for (int direction = 0; direction < 4; direction++) {
                int neighborColumn = tile[0] + STEP_COLUMNS[direction];
                int neighborRow    = tile[1] + STEP_ROWS[direction];
                if (isOnPath(path, index, neighborColumn, neighborRow)) continue;
                if (neighborColumn <= 0 || neighborRow <= 0 || neighborRow >= grid.length - 1
                        || neighborColumn >= grid[0].length - 1) continue;
                if (nearRoomMask[neighborRow][neighborColumn]) continue;
                if (!Level.isWall(grid[neighborRow][neighborColumn])) {
                    widened = true;
                    continue;
                }
                grid[neighborRow][neighborColumn] = 'l';
                widened = true;
            }
            if (widened) spine.add(tile);
        }
        return spine;
    }

    // A neighbour that is the previous or next path tile is the corridor itself, not its shoulder.
    private static boolean isOnPath(List<int[]> path, int index, int tileColumn, int tileRow) {
        for (int offset = -1; offset <= 1; offset += 2) {
            int other = index + offset;
            if (other < 0 || other >= path.size()) continue;
            if (path.get(other)[0] == tileColumn && path.get(other)[1] == tileRow) return true;
        }
        return false;
    }
}
