package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.tileset.LevelPalettes;
import ge.tbegvadze.toon3d.util.LevelGenConstants;
import ge.tbegvadze.toon3d.util.RenderConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Bespoke ceremonial REGION_GATE airlock generator (route-map order-10). A region boundary that is
 * not a boss depth still deserves a visible chapter break, so this emits a SHORT reverent floor rather
 * than a fight: a 3-wide approach corridor, a keycard-gated 'B' blue-door bulkhead (a blue keycard 'b'
 * waits on the approach so the door always opens — the ceremony reuses {@code DoorManager}, never a
 * soft-lock), then a threshold room where {@code World} announces the new region ("ENTERING: …"), with
 * the exit just past it.
 *
 * <p>It is a PACING BREATH — almost no combat. Accordingly it emits ZERO enemy spawn points and no
 * weapon spawns ({@code GateProfile} also applies a near-zero budget). Deterministic from the floor
 * seed. Every tile is from {@code docs/tile-symbols.txt}. Grid convention: (0,0) = bottom-left, Y-up.
 * No LibGDX imports.
 */
public class GateAirlockGenerator implements ILevelGenerator {

    private final Random random;
    // Raw floor seed, kept for deterministic per-level BASE-WALL selection (independent of `random`,
    // so it never perturbs the generated grid). See LevelPalettes.generatedWithBaseWall.
    private final long   seed;

    public GateAirlockGenerator(long seed) {
        this.random = new Random(seed);
        this.seed   = seed;
    }

    @Override
    public Level generate() {
        return generate(1);
    }

    @Override
    public Level generate(int dungeonDepth) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;

        // Everything starts as standard wall 'x'; the airlock is carved out of that mass.
        char[][] grid = new char[gridHeight][gridWidth];
        for (int tileRow = 0; tileRow < gridHeight; tileRow++) {
            for (int tileColumn = 0; tileColumn < gridWidth; tileColumn++) {
                grid[tileRow][tileColumn] = 'x';
            }
        }

        int centerRow    = gridHeight / 2;
        int centerColumn = gridWidth / 2;

        // --- Threshold room (open lit floor rectangle, right of centre) ---
        int roomWidth  = LevelGenConstants.GATE_THRESHOLD_WIDTH;
        int roomHeight = LevelGenConstants.GATE_THRESHOLD_HEIGHT;
        int roomLeft   = centerColumn;
        int roomRight  = roomLeft + roomWidth - 1;
        int roomBottom = centerRow - roomHeight / 2;
        int roomTop    = roomBottom + roomHeight - 1;
        carveFloor(grid, roomLeft, roomBottom, roomRight, roomTop);

        // --- Approach corridor (3-tile-wide, running left into the threshold room) ---
        int approachHalf  = LevelGenConstants.GATE_APPROACH_HALF_WIDTH;
        int approachRight = roomLeft - 1;
        int approachLeft  = approachRight - LevelGenConstants.GATE_APPROACH_LENGTH + 1;
        carveFloor(grid, approachLeft, centerRow - approachHalf, approachRight, centerRow + approachHalf);

        // --- The bulkhead: a 1-tile chokepoint holding the 'B' blue-door, mid-approach ---
        int doorColumn = approachRight - 1;
        // Wall off the flanking rows at the door column so the door is a genuine 1-wide airlock seam.
        grid[centerRow - approachHalf][doorColumn] = 'x';
        grid[centerRow + approachHalf][doorColumn] = 'x';
        grid[centerRow][doorColumn] = 'B'; // blue keycard door

        // --- The blue keycard 'b' on the approach, BEFORE the door, so it always opens ---
        int keycardColumn = approachLeft + 2;
        grid[centerRow][keycardColumn] = 'b';

        // --- Exit a few tiles past the threshold-room centre (the descent onward) ---
        int exitColumn = roomRight - 1;
        grid[centerRow][exitColumn] = RenderConstants.STAIRS_DOWN_CHAR; // '>'

        // --- Player start at the mouth of the approach corridor ---
        int startColumn = approachLeft;
        grid[centerRow][startColumn] = 'p';

        // --- Sparse ceremonial dressing (solid props / decals, never over p / > / b / B) ---
        placeProp(grid, roomRight - 2, roomBottom + 1, 'T');    // a threshold monitoring terminal
        placeProp(grid, roomLeft + 1, roomTop - 1, '%');        // a humming generator by the seam
        if (random.nextBoolean()) {
            placeDecal(grid, roomLeft + 2, centerRow - 2, 'f'); // a flickering strip light
        }

        List<EnemySpawnPoint>  enemySpawnPoints  = new ArrayList<>(); // ZERO enemies — a pacing breath.
        List<WeaponSpawnPoint> weaponSpawnPoints = new ArrayList<>();
        return new Level(grid, enemySpawnPoints, weaponSpawnPoints,
                         LevelPalettes.generatedWithBaseWall(seed));
    }

    /** Carves an inclusive rectangle of lit floor 'l'. */
    private void carveFloor(char[][] grid, int left, int bottom, int right, int top) {
        for (int tileRow = bottom; tileRow <= top; tileRow++) {
            for (int tileColumn = left; tileColumn <= right; tileColumn++) {
                if (inBounds(grid, tileColumn, tileRow)) {
                    grid[tileRow][tileColumn] = 'l';
                }
            }
        }
    }

    /** Places a solid prop only on open lit floor, so it never overwrites the start/exit/door/keycard. */
    private void placeProp(char[][] grid, int tileColumn, int tileRow, char prop) {
        if (inBounds(grid, tileColumn, tileRow) && grid[tileRow][tileColumn] == 'l') {
            grid[tileRow][tileColumn] = prop;
        }
    }

    /** Places a walkable decal only on open lit floor (same guard as {@link #placeProp}). */
    private void placeDecal(char[][] grid, int tileColumn, int tileRow, char decal) {
        placeProp(grid, tileColumn, tileRow, decal);
    }

    private boolean inBounds(char[][] grid, int tileColumn, int tileRow) {
        return tileRow >= 0 && tileRow < grid.length
            && tileColumn >= 0 && tileColumn < grid[0].length;
    }
}
