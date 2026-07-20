package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.tileset.LevelPalettes;
import ge.tbegvadze.toon3d.util.LevelGenConstants;
import ge.tbegvadze.toon3d.util.RenderConstants;
import ge.tbegvadze.toon3d.util.RouteMapConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Bespoke small curated EVENT room generator (route-map order-10). Unlike the random room/cavern
 * generators, this emits a TIGHT, legible chamber built around a single central INTERACTABLE so the
 * "this is a story beat, not a fight" read is immediate: a short approach corridor feeds a small room
 * holding one holo-workstation ('W', the event station), with the exit set a couple of tiles PAST it
 * so reaching the interactable is on the natural path out.
 *
 * <p>An EVENT is a non-combat DECISION (the map's ? room). Accordingly this generator emits ZERO
 * enemy spawn points and no weapon spawns — {@code EventProfile} still applies a near-zero budget, but
 * the room itself promises calm. The interactable tile is registered via {@link Level#markEventStation}
 * so {@code World} can open the choice overlay when the player steps adjacent (the 'W' tile is solid,
 * exactly like the MED-BAY auto-doc it mirrors).
 *
 * <p>Deterministic from the floor seed (only small decal placement varies). Every tile is from
 * {@code docs/tile-symbols.txt}. Grid convention: (0,0) = bottom-left tile, Y-up. No LibGDX imports.
 */
public class EventRoomGenerator implements ILevelGenerator {

    private final Random random;
    // Raw floor seed, kept for deterministic per-level BASE-WALL selection (independent of `random`,
    // so it never perturbs the generated grid). See LevelPalettes.generatedWithBaseWall.
    private final long   seed;

    public EventRoomGenerator(long seed) {
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

        // Everything starts as standard wall 'x'; the chamber is carved out of that mass.
        char[][] grid = new char[gridHeight][gridWidth];
        for (int tileRow = 0; tileRow < gridHeight; tileRow++) {
            for (int tileColumn = 0; tileColumn < gridWidth; tileColumn++) {
                grid[tileRow][tileColumn] = 'x';
            }
        }

        int centerColumn = gridWidth / 2;
        int centerRow    = gridHeight / 2;

        // --- Central room (open lit floor rectangle centred on the grid) ---
        int roomLeft   = centerColumn - LevelGenConstants.EVENT_ROOM_WIDTH / 2;
        int roomRight  = roomLeft + LevelGenConstants.EVENT_ROOM_WIDTH - 1;
        int roomBottom = centerRow - LevelGenConstants.EVENT_ROOM_HEIGHT / 2;
        int roomTop    = roomBottom + LevelGenConstants.EVENT_ROOM_HEIGHT - 1;
        carveFloor(grid, roomLeft, roomBottom, roomRight, roomTop);

        // --- Approach corridor (3-tile-wide, running left into the room's mid row) ---
        int approachHalf  = LevelGenConstants.EVENT_ROOM_APPROACH_HALF_WIDTH;
        int approachRight = roomLeft - 1;
        int approachLeft  = approachRight - LevelGenConstants.EVENT_ROOM_APPROACH_LENGTH + 1;
        carveFloor(grid, approachLeft, centerRow - approachHalf, approachRight, centerRow + approachHalf);

        // --- The central interactable ('W' holo-workstation) — the event station, solid ---
        int stationColumn = centerColumn - 1;
        int stationRow    = centerRow;
        grid[stationRow][stationColumn] = RouteMapConstants.EVENT_STATION_SYMBOL; // 'W'

        // --- Exit a couple of tiles PAST the interactable so the beat is on the path out ---
        int exitColumn = roomRight - 1;
        grid[centerRow][exitColumn] = RenderConstants.STAIRS_DOWN_CHAR; // '>'

        // --- Player start at the mouth of the approach corridor ---
        int startColumn = approachLeft;
        grid[centerRow][startColumn] = 'p';

        // --- Set dressing (solid props / decals, never over p / > / W) ---
        placeProp(grid, roomLeft + 1, roomBottom + 1, 'T');      // a monitoring terminal
        placeProp(grid, roomRight - 1, roomTop - 1, '%');        // a humming generator
        placeDecal(grid, stationColumn - 1, centerRow + 1, 'f'); // one flickering light by the console
        // A little deterministic-from-seed clutter so two event rooms are never quite identical.
        if (random.nextBoolean()) {
            placeDecal(grid, roomLeft + 2, roomTop - 1, 'm');    // a lone corpse
        }
        if (random.nextBoolean()) {
            placeDecal(grid, approachLeft + 2, centerRow, 'O');  // oil at the entrance
        }

        List<EnemySpawnPoint>  enemySpawnPoints  = new ArrayList<>(); // ZERO enemies — a story beat.
        List<WeaponSpawnPoint> weaponSpawnPoints = new ArrayList<>();
        Level level = new Level(grid, enemySpawnPoints, weaponSpawnPoints,
                                LevelPalettes.generatedWithBaseWall(seed));
        level.markEventStation(stationColumn, stationRow);
        return level;
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

    /** Places a solid prop only on open lit floor, so it never overwrites the start/exit/station. */
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
