package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.EnemyConstants;
import ge.tbegvadze.toon3d.util.LevelGenConstants;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fairness + spaciousness contract for the procedural boss arena (boss ORDER 7). Generates the arena
 * across many seeds and asserts the invariants {@code BossFloorController} and the boss fight rely on:
 * the boss, exit, and every heal alcove are reachable from the player start; the arena has no 1-wide
 * interior pinch or dead-end; the centre keeps at least two escape routes; there is exactly ONE lockable
 * door; the open core fits the boss's full dash/charge range; and generation is deterministic per seed.
 */
class BossArenaGeneratorTest {

    private static final int GRID_WIDTH  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
    private static final int GRID_HEIGHT = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
    private static final int SEED_COUNT  = 300;

    /** Matches the generator's own movement-blocking predicate: walls, cover columns, barrels. */
    private static boolean isBlocked(Level level, int tileColumn, int tileRow) {
        if (tileColumn < 0 || tileColumn >= GRID_WIDTH || tileRow < 0 || tileRow >= GRID_HEIGHT) return true;
        char cell = level.getCell(tileColumn, tileRow);
        return Level.isWall(cell) || cell == 'P' || cell == 'E';
    }

    @Test
    void generatesReachableFairSpaciousArenaAcrossManySeeds() {
        for (long seed = 0; seed < SEED_COUNT; seed++) {
            BossArenaGenerator generator = new BossArenaGenerator(seed * 2654435761L + 12345L);
            Level level = generator.generate(5);
            BossArenaLayout layout = generator.getLayout();
            assertNotNull(level, "seed " + seed + " produced no level");
            assertNotNull(layout, "seed " + seed + " produced no layout");

            int playerStartColumn = -1;
            int playerStartRow    = -1;
            int doorTileCount     = 0;
            int coverColumnCount  = 0;
            for (int tileRow = 0; tileRow < GRID_HEIGHT; tileRow++) {
                for (int tileColumn = 0; tileColumn < GRID_WIDTH; tileColumn++) {
                    char cell = level.getCell(tileColumn, tileRow);
                    if (cell == 'p') { playerStartColumn = tileColumn; playerStartRow = tileRow; }
                    if (cell == 'd') doorTileCount++;
                    if (cell == 'P') coverColumnCount++;
                }
            }
            assertTrue(playerStartColumn >= 0, "seed " + seed + " has no player start");

            // Exactly one lockable door (the single sealable arena entry).
            assertEquals(1, doorTileCount, "seed " + seed + " must have exactly one door tile");
            assertTrue(Level.isDoor(level.getCell(layout.doorColumn, layout.doorRow)),
                    "seed " + seed + " door tile in layout is not a door");

            // Cover count stays inside the tuned band (never falls back to a bare room).
            assertTrue(coverColumnCount >= LevelGenConstants.BOSS_ARENA_MIN_COVER
                            && coverColumnCount <= LevelGenConstants.BOSS_ARENA_MAX_COVER,
                    "seed " + seed + " cover count " + coverColumnCount + " out of band");

            // Reachability: boss, exit, and every alcove are reachable from the player start.
            boolean[][] reachable = floodFill(level, playerStartColumn, playerStartRow);
            assertTrue(reachable[layout.bossRow][layout.bossColumn], "seed " + seed + " boss unreachable");
            assertTrue(reachable[layout.exitRow][layout.exitColumn], "seed " + seed + " exit unreachable");
            assertEquals('>', level.getCell(layout.exitColumn, layout.exitRow),
                    "seed " + seed + " exit tile is not the exit portal");
            assertTrue(!isBlocked(level, layout.bossColumn, layout.bossRow),
                    "seed " + seed + " boss stands on a blocked tile");

            List<int[]> alcoves = layout.getHealAlcoveTiles();
            assertTrue(!alcoves.isEmpty(), "seed " + seed + " has no heal alcoves");
            for (int[] tile : alcoves) {
                assertTrue(reachable[tile[1]][tile[0]], "seed " + seed + " alcove " + tile[0] + "," + tile[1] + " unreachable");
            }

            // No 1-wide pinch and no dead-end over every reachable arena tile (above the door row, so the
            // intentional single-wide door and the antechamber are excluded).
            for (int tileRow = 0; tileRow < GRID_HEIGHT; tileRow++) {
                for (int tileColumn = 0; tileColumn < GRID_WIDTH; tileColumn++) {
                    if (!reachable[tileRow][tileColumn]) continue;
                    if (tileRow <= layout.doorRow) continue;
                    boolean leftBlocked  = isBlocked(level, tileColumn - 1, tileRow);
                    boolean rightBlocked = isBlocked(level, tileColumn + 1, tileRow);
                    boolean upBlocked    = isBlocked(level, tileColumn, tileRow + 1);
                    boolean downBlocked  = isBlocked(level, tileColumn, tileRow - 1);
                    assertTrue(!((leftBlocked && rightBlocked) || (upBlocked && downBlocked)),
                            "seed " + seed + " 1-wide pinch at " + tileColumn + "," + tileRow);
                    int openNeighbours = (leftBlocked ? 0 : 1) + (rightBlocked ? 0 : 1)
                                       + (upBlocked ? 0 : 1) + (downBlocked ? 0 : 1);
                    assertTrue(openNeighbours >= LevelGenConstants.BOSS_ARENA_MIN_ESCAPE_ROUTES,
                            "seed " + seed + " dead-end at " + tileColumn + "," + tileRow);
                }
            }

            // The open core fits the boss's full dash and charge range along at least one axis through it.
            int requiredRun = Math.max(Constants.BOSS_MAX_DASH_TILES, EnemyConstants.OVERSEER_CHARGE_RANGE_TILES);
            assertTrue(longestOpenRunThroughBoss(level, layout) >= requiredRun,
                    "seed " + seed + " open core too tight for a full dash/charge");
        }
    }

    @Test
    void isDeterministicForAGivenSeed() {
        for (long seed = 0; seed < 25; seed++) {
            Level first  = new BossArenaGenerator(seed).generate(5);
            Level second = new BossArenaGenerator(seed).generate(5);
            for (int tileRow = 0; tileRow < GRID_HEIGHT; tileRow++) {
                for (int tileColumn = 0; tileColumn < GRID_WIDTH; tileColumn++) {
                    assertEquals(first.getCell(tileColumn, tileRow), second.getCell(tileColumn, tileRow),
                            "seed " + seed + " differs at " + tileColumn + "," + tileRow);
                }
            }
        }
    }

    private static boolean[][] floodFill(Level level, int startColumn, int startRow) {
        boolean[][] visited = new boolean[GRID_HEIGHT][GRID_WIDTH];
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startColumn, startRow});
        visited[startRow][startColumn] = true;
        int[] neighbourColumns = { -1, 1, 0, 0 };
        int[] neighbourRows    = {  0, 0, 1, -1 };
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            for (int direction = 0; direction < 4; direction++) {
                int nextColumn = current[0] + neighbourColumns[direction];
                int nextRow    = current[1] + neighbourRows[direction];
                if (nextColumn < 0 || nextColumn >= GRID_WIDTH || nextRow < 0 || nextRow >= GRID_HEIGHT) continue;
                if (visited[nextRow][nextColumn] || isBlocked(level, nextColumn, nextRow)) continue;
                visited[nextRow][nextColumn] = true;
                queue.add(new int[]{nextColumn, nextRow});
            }
        }
        return visited;
    }

    /** Length of the longest unblocked cardinal run passing through the boss tile (horizontal or vertical). */
    private static int longestOpenRunThroughBoss(Level level, BossArenaLayout layout) {
        int horizontal = 1;
        for (int tileColumn = layout.bossColumn - 1; !isBlocked(level, tileColumn, layout.bossRow); tileColumn--) horizontal++;
        for (int tileColumn = layout.bossColumn + 1; !isBlocked(level, tileColumn, layout.bossRow); tileColumn++) horizontal++;
        int vertical = 1;
        for (int tileRow = layout.bossRow - 1; !isBlocked(level, layout.bossColumn, tileRow); tileRow--) vertical++;
        for (int tileRow = layout.bossRow + 1; !isBlocked(level, layout.bossColumn, tileRow); tileRow++) vertical++;
        return Math.max(horizontal, vertical);
    }
}
