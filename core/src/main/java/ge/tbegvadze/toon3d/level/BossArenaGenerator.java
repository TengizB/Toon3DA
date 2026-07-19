package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.tileset.LevelPalettes;
import ge.tbegvadze.toon3d.util.LevelGenConstants;
import ge.tbegvadze.toon3d.util.RenderConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Procedural, spacious boss-arena generator (boss ORDER 7).
 *
 * <p>Replaces the old fixed 3-wide corridor + rigid 2x2-column box with a run-seeded duel arena built
 * for the mobile hit-and-run boss:
 * <ul>
 *   <li><b>Open core</b> — one large lit room (interior well over 16x16) so the boss can DASH
 *       ({@code BOSS_MAX_DASH_TILES}) and CHARGE ({@code OVERSEER_CHARGE_RANGE_TILES}) at full range and
 *       the player can circle-strafe and kite adds.</li>
 *   <li><b>Asymmetric cover</b> — a handful of 'P' columns scattered by seeded jitter (never a grid),
 *       breaking line-of-sight for both sides.</li>
 *   <li><b>Heal alcoves</b> — 1-2 recessed pockets carved into the side walls where the boss can retreat
 *       to repair (ORDER 5's flee target); each opens on its mouth side, never a turtle-proof dead-end.</li>
 *   <li><b>Wide entrance</b> — a roomy antechamber below the arena with exactly ONE lockable door, so the
 *       player never fights standing in a doorway.</li>
 *   <li><b>Mood</b> — a lit centre with dim/flickering edges reading as a failing security core, plus a
 *       couple of explosive barrels near the walls as a player tool.</li>
 * </ul>
 *
 * <p><b>Fairness pass (F5).</b> After each cover/barrel scatter the generator runs a reachability +
 * geometry check: flood-fill from the player start must reach the boss, the exit, and every alcove; no
 * open interior tile may be a 1-wide pinch (blocked on two opposite sides) or a dead-end (fewer than two
 * open neighbours); and the arena centre must keep at least {@code BOSS_ARENA_MIN_ESCAPE_ROUTES} open
 * cardinal exits. A failing roll is re-rolled up to {@code BOSS_ARENA_MAX_LAYOUT_ATTEMPTS} times, then the
 * generator falls back to the bare open room (no cover / barrels), which passes trivially.
 *
 * <p>The chosen door / exit / boss-spawn / alcove tiles vary per run, so they are returned in a
 * {@link BossArenaLayout} (via {@link #getLayout()} after {@link #generate(int)}) instead of the old
 * compile-time constants. {@code World} injects that layout into the boss-creation path and
 * {@code BossFloorController}.
 *
 * <p>Every tile symbol is already in {@code docs/tile-symbols.txt} — this generator introduces none.
 * Grid convention: (0,0) = bottom-left tile, Y-up.
 */
public final class BossArenaGenerator implements ILevelGenerator {

    // Tile symbols (all from docs/tile-symbols.txt).
    private static final char WALL_TILE           = 'x';
    private static final char LIT_FLOOR_TILE      = ' '; // 1.55x — the bright open core
    private static final char NORMAL_FLOOR_TILE   = 'l'; // 1.0x  — antechamber + recessed alcoves
    private static final char UNLIT_FLOOR_TILE     = 'u'; // 0.55x — dim arena edges
    private static final char FLICKER_FLOOR_TILE  = 'f'; // failing fluorescents at the edges
    private static final char COVER_COLUMN_TILE   = 'P';
    private static final char DOOR_TILE           = 'd';
    private static final char BARREL_TILE         = 'E'; // explosive barrel (player tool)
    private static final char BOSS_SPAWN_TILE     = 'n';
    private static final char PLAYER_START_TILE   = 'p';
    private static final char EXIT_TILE           = RenderConstants.STAIRS_DOWN_CHAR; // '>'

    // Every Nth perimeter-adjacent (edge) floor tile flickers; the rest are unlit — deterministic so the
    // base room is identical across re-roll attempts (only cover/barrels vary).
    private static final int EDGE_FLICKER_PERIOD = 5;
    // How many random tiles to try when seeking a spot for one cover column / barrel before giving up.
    private static final int PLACEMENT_TRIES_PER_ITEM = 40;

    private final int    gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;  // 80
    private final int    gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT; // 45
    private final Random random;
    // Raw floor seed, kept for deterministic per-level BASE-WALL selection (independent of `random`,
    // so it never perturbs the generated grid). See LevelPalettes.generatedWithBaseWall.
    private final long   seed;

    private BossArenaLayout layout;

    public BossArenaGenerator(long seed) {
        this.random = new Random(seed);
        this.seed   = seed;
    }

    /** The layout chosen by the most recent {@link #generate(int)} call. Null before the first call. */
    public BossArenaLayout getLayout() {
        return layout;
    }

    @Override
    public Level generate() {
        return generate(1);
    }

    @Override
    public Level generate(int dungeonDepth) {
        int centerColumn = gridWidth / 2;

        // --- Room dimensions + alcove rows: drawn ONCE so the base room is stable across re-rolls. ---
        int arenaWidth  = randomInRange(LevelGenConstants.BOSS_ARENA_MIN_WIDTH,  LevelGenConstants.BOSS_ARENA_MAX_WIDTH);
        int arenaHeight = randomInRange(LevelGenConstants.BOSS_ARENA_MIN_HEIGHT, LevelGenConstants.BOSS_ARENA_MAX_HEIGHT);

        int arenaInteriorTop    = gridHeight - 1 - LevelGenConstants.BOSS_ARENA_TOP_MARGIN;
        int arenaInteriorBottom = arenaInteriorTop - arenaHeight + 1;
        int arenaInteriorLeft   = centerColumn - arenaWidth / 2;
        int arenaInteriorRight  = arenaInteriorLeft + arenaWidth - 1;

        // Door on the arena's bottom wall row, at the centre column; the antechamber sits below it.
        int doorRow = arenaInteriorBottom - 1;

        // Boss spawns near the far (top) side so the player sees it across the room on entry; the exit
        // sits at the top wall just beyond the boss (gated until death).
        int bossColumn = centerColumn;
        int bossRow    = arenaInteriorTop - LevelGenConstants.BOSS_ARENA_BOSS_TOP_OFFSET;
        int exitColumn = centerColumn;
        int exitRow    = arenaInteriorTop;

        // Antechamber (wide entrance) — interior rows below the door row.
        int antechamberTopRow    = doorRow - 1;
        int antechamberBottomRow = antechamberTopRow - LevelGenConstants.BOSS_ARENA_ANTECHAMBER_HEIGHT + 1;
        int antechamberLeft      = centerColumn - LevelGenConstants.BOSS_ARENA_ANTECHAMBER_WIDTH / 2;
        int antechamberRight     = antechamberLeft + LevelGenConstants.BOSS_ARENA_ANTECHAMBER_WIDTH - 1;
        int playerStartColumn    = centerColumn;
        int playerStartRow       = antechamberBottomRow;

        // Two recessed heal alcoves carved into the side walls at differing (asymmetric) mid-band rows.
        int alcoveBandBottom = arenaInteriorBottom + LevelGenConstants.BOSS_ARENA_KEEP_CLEAR_RADIUS;
        int alcoveBandTop    = arenaInteriorTop - LevelGenConstants.BOSS_ARENA_KEEP_CLEAR_RADIUS
                             - LevelGenConstants.BOSS_ARENA_ALCOVE_HEIGHT;
        int leftAlcoveRow  = randomInRange(alcoveBandBottom, alcoveBandTop);
        int rightAlcoveRow = randomInRange(alcoveBandBottom, alcoveBandTop);

        // Best-effort attempts to find a cover/barrel scatter that clears the fairness pass; on the last
        // attempt we fall back to a bare open room (no cover/barrels), which always passes.
        char[][] matrix = null;
        List<int[]> alcoveTiles = null;
        for (int attempt = 0; attempt < LevelGenConstants.BOSS_ARENA_MAX_LAYOUT_ATTEMPTS; attempt++) {
            boolean fallbackBareRoom = attempt == LevelGenConstants.BOSS_ARENA_MAX_LAYOUT_ATTEMPTS - 1;

            matrix = new char[gridHeight][gridWidth];
            fillWalls(matrix);
            carveArena(matrix, arenaInteriorLeft, arenaInteriorBottom, arenaInteriorRight, arenaInteriorTop);
            carveAntechamber(matrix, antechamberLeft, antechamberBottomRow, antechamberRight, antechamberTopRow);
            matrix[doorRow][centerColumn] = DOOR_TILE;
            alcoveTiles = carveAlcoves(matrix, arenaInteriorLeft, arenaInteriorRight, leftAlcoveRow, rightAlcoveRow);

            matrix[exitRow][exitColumn] = EXIT_TILE;
            matrix[playerStartRow][playerStartColumn] = PLAYER_START_TILE;

            if (!fallbackBareRoom) {
                scatterCover(matrix, arenaInteriorLeft, arenaInteriorBottom, arenaInteriorRight, arenaInteriorTop,
                        bossColumn, bossRow, exitColumn, exitRow, centerColumn, doorRow, alcoveTiles);
                scatterBarrels(matrix, arenaInteriorLeft, arenaInteriorBottom, arenaInteriorRight, centerColumn);
            }

            if (validateArena(matrix, arenaInteriorLeft, arenaInteriorBottom, arenaInteriorRight, arenaInteriorTop,
                    playerStartColumn, playerStartRow, bossColumn, bossRow, exitColumn, exitRow, doorRow, alcoveTiles)) {
                break;
            }
        }

        // Stamp the boss spawn marker, then extract it (mirrors LevelLoader: 'n' -> recorded + floor).
        matrix[bossRow][bossColumn] = BOSS_SPAWN_TILE;
        List<EnemySpawnPoint> spawnPoints = new ArrayList<>();
        for (int tileRow = 0; tileRow < gridHeight; tileRow++) {
            for (int tileColumn = 0; tileColumn < gridWidth; tileColumn++) {
                if (matrix[tileRow][tileColumn] == BOSS_SPAWN_TILE) {
                    spawnPoints.add(new EnemySpawnPoint(BOSS_SPAWN_TILE, tileColumn, tileRow));
                    matrix[tileRow][tileColumn] = LIT_FLOOR_TILE;
                }
            }
        }

        this.layout = new BossArenaLayout(centerColumn, doorRow, exitColumn, exitRow,
                bossColumn, bossRow, alcoveTiles);

        return new Level(matrix, spawnPoints, new ArrayList<>(),
                         LevelPalettes.generatedWithBaseWall(seed));
    }

    // -------------------------------------------------------------------------
    // Base room construction (deterministic given the room dimensions)
    // -------------------------------------------------------------------------

    private void fillWalls(char[][] matrix) {
        for (int tileRow = 0; tileRow < gridHeight; tileRow++) {
            for (int tileColumn = 0; tileColumn < gridWidth; tileColumn++) {
                matrix[tileRow][tileColumn] = WALL_TILE;
            }
        }
    }

    /**
     * Carves the open core: a lit interior with a one-tile-deep dim border ring (unlit, with every
     * {@code EDGE_FLICKER_PERIOD}-th edge tile flickering) so the centre reads bright and the edges read
     * as a failing security core. Deterministic — no random draws, so re-roll attempts share this base.
     */
    private void carveArena(char[][] matrix, int interiorLeft, int interiorBottom, int interiorRight, int interiorTop) {
        int edgeCounter = 0;
        for (int tileRow = interiorBottom; tileRow <= interiorTop; tileRow++) {
            for (int tileColumn = interiorLeft; tileColumn <= interiorRight; tileColumn++) {
                boolean onEdge = tileColumn == interiorLeft || tileColumn == interiorRight
                              || tileRow == interiorBottom || tileRow == interiorTop;
                if (onEdge) {
                    matrix[tileRow][tileColumn] = (edgeCounter++ % EDGE_FLICKER_PERIOD == 0)
                            ? FLICKER_FLOOR_TILE : UNLIT_FLOOR_TILE;
                } else {
                    matrix[tileRow][tileColumn] = LIT_FLOOR_TILE;
                }
            }
        }
    }

    private void carveAntechamber(char[][] matrix, int left, int bottom, int right, int top) {
        for (int tileRow = bottom; tileRow <= top; tileRow++) {
            for (int tileColumn = left; tileColumn <= right; tileColumn++) {
                matrix[tileRow][tileColumn] = NORMAL_FLOOR_TILE;
            }
        }
    }

    /**
     * Carves one recessed pocket into each side wall (left + right) and returns their floor tiles. Each
     * pocket is {@code BOSS_ARENA_ALCOVE_DEPTH} deep into the wall and {@code BOSS_ARENA_ALCOVE_HEIGHT}
     * tall; its mouth opens into the arena, so it is always reachable and open on the mouth side (never a
     * dead-end the boss can turtle in — its tiles keep two open neighbours).
     */
    private List<int[]> carveAlcoves(char[][] matrix, int interiorLeft, int interiorRight,
                                     int leftAlcoveRow, int rightAlcoveRow) {
        List<int[]> tiles = new ArrayList<>();
        int depth  = LevelGenConstants.BOSS_ARENA_ALCOVE_DEPTH;
        int height = LevelGenConstants.BOSS_ARENA_ALCOVE_HEIGHT;

        // Left pocket: columns just outside the left wall, extending leftward.
        for (int step = 1; step <= depth; step++) {
            int tileColumn = interiorLeft - step;
            if (tileColumn <= 0) break; // keep the grid's outer wall ring intact
            for (int rowOffset = 0; rowOffset < height; rowOffset++) {
                int tileRow = leftAlcoveRow + rowOffset;
                matrix[tileRow][tileColumn] = NORMAL_FLOOR_TILE;
                tiles.add(new int[]{tileColumn, tileRow});
            }
        }

        // Right pocket: columns just outside the right wall, extending rightward.
        for (int step = 1; step <= depth; step++) {
            int tileColumn = interiorRight + step;
            if (tileColumn >= gridWidth - 1) break;
            for (int rowOffset = 0; rowOffset < height; rowOffset++) {
                int tileRow = rightAlcoveRow + rowOffset;
                matrix[tileRow][tileColumn] = NORMAL_FLOOR_TILE;
                tiles.add(new int[]{tileColumn, tileRow});
            }
        }
        return tiles;
    }

    // -------------------------------------------------------------------------
    // Seeded scatter (varies per re-roll attempt)
    // -------------------------------------------------------------------------

    /**
     * Scatters asymmetric 'P' cover columns across the open core. Each column keeps a Chebyshev clearance
     * from the walls, the door/boss/exit keep-clear zones, the alcove mouths, and every other column, so
     * the placement is irregular yet never forms a 1-wide pinch or seals a lane (fairness F5).
     */
    private void scatterCover(char[][] matrix, int interiorLeft, int interiorBottom, int interiorRight, int interiorTop,
                              int bossColumn, int bossRow, int exitColumn, int exitRow,
                              int doorColumn, int doorRow, List<int[]> alcoveTiles) {
        int clearance   = LevelGenConstants.BOSS_ARENA_COVER_CLEARANCE;
        int keepClear   = LevelGenConstants.BOSS_ARENA_KEEP_CLEAR_RADIUS;
        int desiredCount = randomInRange(LevelGenConstants.BOSS_ARENA_MIN_COVER, LevelGenConstants.BOSS_ARENA_MAX_COVER);
        int minColumn = interiorLeft + clearance;
        int maxColumn = interiorRight - clearance;
        int minRow    = interiorBottom + clearance;
        int maxRow    = interiorTop - clearance;
        if (minColumn > maxColumn || minRow > maxRow) return;

        List<int[]> placed = new ArrayList<>();
        for (int index = 0; index < desiredCount; index++) {
            for (int attempt = 0; attempt < PLACEMENT_TRIES_PER_ITEM; attempt++) {
                int tileColumn = randomInRange(minColumn, maxColumn);
                int tileRow    = randomInRange(minRow, maxRow);
                // The door mouth is one tile above the door, inside the arena.
                if (chebyshev(tileColumn, tileRow, doorColumn, doorRow + 1) < keepClear) continue;
                if (chebyshev(tileColumn, tileRow, bossColumn, bossRow) < keepClear)     continue;
                if (chebyshev(tileColumn, tileRow, exitColumn, exitRow) < keepClear)     continue;
                if (tooCloseToAny(tileColumn, tileRow, placed, clearance))               continue;
                if (tooCloseToAny(tileColumn, tileRow, alcoveTiles, clearance))          continue;
                matrix[tileRow][tileColumn] = COVER_COLUMN_TILE;
                placed.add(new int[]{tileColumn, tileRow});
                break;
            }
        }
    }

    /**
     * Drops up to {@code BOSS_ARENA_MAX_BARRELS} explosive barrels FLUSH against the arena's bottom wall
     * (on the edge row itself, so no 1-wide gap opens behind them), clear of the corners and the central
     * dash lane, and spaced apart, as a player tool (shoot to chip the boss via the existing hazard
     * chain). Best-effort — fewer may be placed if no slot fits.
     */
    private void scatterBarrels(char[][] matrix, int interiorLeft, int interiorBottom, int interiorRight,
                                int centerColumn) {
        int keepClear = LevelGenConstants.BOSS_ARENA_KEEP_CLEAR_RADIUS;
        int barrelRow = interiorBottom;     // flush on the bottom edge, touching the wall (no 1-wide slot)
        int minColumn = interiorLeft + 2;   // keep off the corners
        int maxColumn = interiorRight - 2;
        if (minColumn > maxColumn) return;

        List<int[]> placedBarrels = new ArrayList<>();
        for (int attempt = 0; attempt < PLACEMENT_TRIES_PER_ITEM && placedBarrels.size() < LevelGenConstants.BOSS_ARENA_MAX_BARRELS; attempt++) {
            int tileColumn = randomInRange(minColumn, maxColumn);
            if (Math.abs(tileColumn - centerColumn) <= keepClear) continue; // keep the central lane open
            if (isBlocked(matrix, tileColumn, barrelRow)) continue;         // a cover column already sits here
            if (tooCloseToAny(tileColumn, barrelRow, placedBarrels, 1)) continue;
            matrix[barrelRow][tileColumn] = BARREL_TILE;
            placedBarrels.add(new int[]{tileColumn, barrelRow});
        }
    }

    // -------------------------------------------------------------------------
    // Fairness pass
    // -------------------------------------------------------------------------

    /**
     * Runs the fairness contract (F5) on a candidate layout: the boss, exit, and every alcove tile must be
     * reachable from the player start; no open arena/alcove tile may be a 1-wide pinch (blocked on two
     * opposite sides) or a dead-end (fewer than two open neighbours); and the arena centre must keep at
     * least {@code BOSS_ARENA_MIN_ESCAPE_ROUTES} open cardinal exits. The lockable door (an intentional
     * width-1 chokepoint) is excluded from the pinch/dead-end checks.
     */
    private boolean validateArena(char[][] matrix, int interiorLeft, int interiorBottom, int interiorRight, int interiorTop,
                                  int playerStartColumn, int playerStartRow, int bossColumn, int bossRow,
                                  int exitColumn, int exitRow, int doorRow, List<int[]> alcoveTiles) {
        boolean[][] reachable = floodFill(matrix, playerStartColumn, playerStartRow);
        if (!reachable[bossRow][bossColumn]) return false;
        if (!reachable[exitRow][exitColumn]) return false;
        for (int[] tile : alcoveTiles) {
            if (!reachable[tile[1]][tile[0]]) return false;
        }

        // Pinch / dead-end check over the arena interior + alcove pockets (never the door).
        for (int tileRow = interiorBottom; tileRow <= interiorTop; tileRow++) {
            for (int tileColumn = interiorLeft; tileColumn <= interiorRight; tileColumn++) {
                if (!checkOpenTileHealthy(matrix, tileColumn, tileRow)) return false;
            }
        }
        for (int[] tile : alcoveTiles) {
            if (!checkOpenTileHealthy(matrix, tile[0], tile[1])) return false;
        }

        // Central-core escape routes.
        int centerColumn = (interiorLeft + interiorRight) / 2;
        int centerRow    = (interiorBottom + interiorTop) / 2;
        if (countOpenNeighbours(matrix, centerColumn, centerRow) < LevelGenConstants.BOSS_ARENA_MIN_ESCAPE_ROUTES) {
            return false;
        }
        return true;
    }

    /** A single open tile is healthy when it is neither a 1-wide pinch nor a dead-end. Blocked tiles pass. */
    private boolean checkOpenTileHealthy(char[][] matrix, int tileColumn, int tileRow) {
        if (isBlocked(matrix, tileColumn, tileRow)) return true;
        boolean leftBlocked  = isBlocked(matrix, tileColumn - 1, tileRow);
        boolean rightBlocked = isBlocked(matrix, tileColumn + 1, tileRow);
        boolean upBlocked    = isBlocked(matrix, tileColumn, tileRow + 1);
        boolean downBlocked  = isBlocked(matrix, tileColumn, tileRow - 1);
        if ((leftBlocked && rightBlocked) || (upBlocked && downBlocked)) return false; // 1-wide pinch
        int openNeighbours = (leftBlocked ? 0 : 1) + (rightBlocked ? 0 : 1)
                           + (upBlocked ? 0 : 1) + (downBlocked ? 0 : 1);
        return openNeighbours >= 2; // no dead-end
    }

    private int countOpenNeighbours(char[][] matrix, int tileColumn, int tileRow) {
        int count = 0;
        if (!isBlocked(matrix, tileColumn - 1, tileRow)) count++;
        if (!isBlocked(matrix, tileColumn + 1, tileRow)) count++;
        if (!isBlocked(matrix, tileColumn, tileRow + 1)) count++;
        if (!isBlocked(matrix, tileColumn, tileRow - 1)) count++;
        return count;
    }

    /** 4-connected flood fill over walkable tiles, marking every tile reachable from the start. */
    private boolean[][] floodFill(char[][] matrix, int startColumn, int startRow) {
        boolean[][] visited = new boolean[gridHeight][gridWidth];
        int[] stackColumns = new int[gridWidth * gridHeight];
        int[] stackRows    = new int[gridWidth * gridHeight];
        int stackSize = 0;
        stackColumns[stackSize] = startColumn;
        stackRows[stackSize]    = startRow;
        stackSize++;
        visited[startRow][startColumn] = true;

        int[] neighbourColumns = { -1, 1, 0, 0 };
        int[] neighbourRows    = {  0, 0, 1, -1 };
        while (stackSize > 0) {
            stackSize--;
            int currentColumn = stackColumns[stackSize];
            int currentRow    = stackRows[stackSize];
            for (int direction = 0; direction < 4; direction++) {
                int nextColumn = currentColumn + neighbourColumns[direction];
                int nextRow    = currentRow    + neighbourRows[direction];
                if (nextColumn < 0 || nextColumn >= gridWidth || nextRow < 0 || nextRow >= gridHeight) continue;
                if (visited[nextRow][nextColumn]) continue;
                if (isBlocked(matrix, nextColumn, nextRow)) continue;
                visited[nextRow][nextColumn] = true;
                stackColumns[stackSize] = nextColumn;
                stackRows[stackSize]    = nextRow;
                stackSize++;
            }
        }
        return visited;
    }

    /** True when a tile blocks movement: any wall, a cover column, or an explosive barrel. */
    private boolean isBlocked(char[][] matrix, int tileColumn, int tileRow) {
        if (tileColumn < 0 || tileColumn >= gridWidth || tileRow < 0 || tileRow >= gridHeight) return true;
        char cell = matrix[tileRow][tileColumn];
        return Level.isWall(cell) || cell == COVER_COLUMN_TILE || cell == BARREL_TILE;
    }

    // -------------------------------------------------------------------------
    // Small helpers
    // -------------------------------------------------------------------------

    /** Uniform random int in [minValue, maxValue] (inclusive). Degenerate ranges return minValue. */
    private int randomInRange(int minValue, int maxValue) {
        if (maxValue <= minValue) return minValue;
        return minValue + random.nextInt(maxValue - minValue + 1);
    }

    private static int chebyshev(int fromColumn, int fromRow, int toColumn, int toRow) {
        return Math.max(Math.abs(fromColumn - toColumn), Math.abs(fromRow - toRow));
    }

    /** True when (column,row) is within {@code clearance} Chebyshev of any {column,row} pair in the list. */
    private static boolean tooCloseToAny(int tileColumn, int tileRow, List<int[]> occupied, int clearance) {
        for (int[] other : occupied) {
            if (chebyshev(tileColumn, tileRow, other[0], other[1]) <= clearance) return true;
        }
        return false;
    }
}
