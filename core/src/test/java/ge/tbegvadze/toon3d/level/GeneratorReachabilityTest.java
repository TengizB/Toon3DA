package ge.tbegvadze.toon3d.level;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongFunction;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Walkway-blocking regression guard for every procedural generator.
 *
 * <p>The bug this pins: an unwalkable environment element (a solid prop, a wall accent, or a
 * stalagmite/pillar column 'P') dropped into a walkway during decoration could seal off a floor
 * pocket, leaving part of the level unreachable. Each generator's per-room / per-chamber connectivity
 * audit only checked room/chamber CENTRES, so a pocket whose centre lay elsewhere slipped through.
 *
 * <p>The invariant asserted here is the fix's contract, measured on the real generated grid the same
 * way the player moves: <b>every walkable floor tile must be reachable from the player start</b>, where
 * a tile blocks movement exactly when {@link Level#isWall}, {@link Level#isPropSolid}, or
 * {@link Level#isColumn} is true (floor, doors, decals, and pickups are all passable). Run across a
 * wide seed spread and several depths so the prop-heavy passes (salvage heaps, cave crystal forests,
 * room blueprints) are exercised.
 *
 * <p>Headless — the generators are pure Java (no LibGDX), so this runs without an OpenGL context.
 */
class GeneratorReachabilityTest {

    @Test
    void mstRoomsGeneratorLeavesNoFloorTileStranded() {
        assertEveryFloorTileReachable("LevelGenerator", LevelGenerator::new);
    }

    @Test
    void linearCorridorGeneratorLeavesNoFloorTileStranded() {
        assertEveryFloorTileReachable("LinearCorridorGenerator", LinearCorridorGenerator::new);
    }

    @Test
    void cavernGeneratorLeavesNoFloorTileStranded() {
        assertEveryFloorTileReachable("CavernGenerator", CavernGenerator::new);
    }

    // -------------------------------------------------------------------------

    private void assertEveryFloorTileReachable(String generatorName, LongFunction<ILevelGenerator> factory) {
        int[] depths = { 1, 3, 5, 7 };
        for (long seed = 1; seed <= 60; seed++) {
            for (int depth : depths) {
                Level level = factory.apply(seed).generate(depth);
                int[] stranded = firstStrandedFloorTile(level);
                assertTrue(stranded == null,
                        generatorName + " left a walkable floor tile unreachable from the player start"
                        + " (seed=" + seed + ", depth=" + depth + ")"
                        + (stranded == null ? "" : " at (" + stranded[0] + "," + stranded[1] + ")")
                        + " — a prop/column blocked the only walkway");
            }
        }
    }

    /**
     * Returns {@code {column, row}} of the first walkable floor tile that is NOT reachable from the
     * player start, or {@code null} when every floor tile is reachable.
     */
    private int[] firstStrandedFloorTile(Level level) {
        int width  = level.getWidth();
        int height = level.getHeight();

        int startColumn = -1;
        int startRow    = -1;
        for (int tileRow = 0; tileRow < height && startColumn < 0; tileRow++) {
            for (int tileColumn = 0; tileColumn < width; tileColumn++) {
                if (level.getCell(tileColumn, tileRow) == 'p') {
                    startColumn = tileColumn;
                    startRow    = tileRow;
                    break;
                }
            }
        }
        if (startColumn < 0) return null; // no start tile — nothing to strand against

        boolean[][] reachable = new boolean[height][width];
        Deque<int[]> frontier = new ArrayDeque<>();
        reachable[startRow][startColumn] = true;
        frontier.add(new int[]{ startColumn, startRow });
        int[] deltaColumns = { 0, 0, 1, -1 };
        int[] deltaRows    = { 1, -1, 0, 0 };
        while (!frontier.isEmpty()) {
            int[] current = frontier.poll();
            for (int direction = 0; direction < 4; direction++) {
                int neighborColumn = current[0] + deltaColumns[direction];
                int neighborRow    = current[1] + deltaRows[direction];
                if (neighborColumn < 0 || neighborColumn >= width
                        || neighborRow < 0 || neighborRow >= height) continue;
                if (reachable[neighborRow][neighborColumn]) continue;
                if (!isPlayerPassable(level.getCell(neighborColumn, neighborRow))) continue;
                reachable[neighborRow][neighborColumn] = true;
                frontier.add(new int[]{ neighborColumn, neighborRow });
            }
        }

        for (int tileRow = 0; tileRow < height; tileRow++) {
            for (int tileColumn = 0; tileColumn < width; tileColumn++) {
                if (isWalkableFloor(level.getCell(tileColumn, tileRow))
                        && !reachable[tileRow][tileColumn]) {
                    return new int[]{ tileColumn, tileRow };
                }
            }
        }
        return null;
    }

    // Mirrors in-game movement: only solid walls, solid props, and columns block the player.
    private boolean isPlayerPassable(char cell) {
        return !Level.isWall(cell) && !Level.isPropSolid(cell) && !Level.isColumn(cell);
    }

    // The plain floor tiles the player must always be able to reach.
    private boolean isWalkableFloor(char cell) {
        return cell == ' ' || cell == 'l' || cell == 'u' || cell == 'f';
    }
}
