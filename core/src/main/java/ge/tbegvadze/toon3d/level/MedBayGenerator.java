package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.util.LevelGenConstants;
import ge.tbegvadze.toon3d.util.RenderConstants;
import ge.tbegvadze.toon3d.util.RouteMapConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Bespoke MED-BAY / REST clinic generator (route-map order-8). Unlike the random room/cavern
 * generators, this one emits a CURATED, legible clinic so the sanctuary read is unmistakable within
 * two seconds of entering: a short approach corridor feeds a central ward that holds the auto-doc
 * heal station, flanked by 1-2 cryo-pod alcoves, with the exit set a few tiles PAST the auto-doc so
 * the free heal sits on the natural path.
 *
 * <p>Legibility is the whole point — the honest REST node must always read (and BE) safe, so the map's
 * icon language stays trustworthy. Accordingly this generator emits ZERO enemy spawn points and no
 * weapon spawns; it is a pure pacing exhale.
 *
 * <p>The layout is fixed-ish and DETERMINISTIC from the floor seed: only the alcove side and whether a
 * second (bottom) alcove appears vary, so the clinic feels hand-made yet never identical. Every tile
 * is from {@code docs/tile-symbols.txt} — 'M' medical walls dominate, 'N' reinforced glass for the
 * ward viewing window, 'Z' cryo walls frame the pod alcoves, 'S' emergency strips line the approach;
 * '&amp;' bio-pods, 'I' specimen tanks, 'T' terminals and a '%' generator dress the ward; the auto-doc
 * reuses the holo-workstation 'W' (no new symbol — the heal interactivity is layered on by World).
 *
 * <p>Grid convention: (0,0) = bottom-left tile, Y-up. No LibGDX imports — pure Java.
 */
public class MedBayGenerator implements ILevelGenerator {

    private final Random random;

    public MedBayGenerator(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public Level generate() {
        return generate(1);
    }

    @Override
    public Level generate(int dungeonDepth) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;

        // Everything starts as medical wall 'M'; the clinic is carved out of that mass.
        char[][] grid = new char[gridHeight][gridWidth];
        for (int tileRow = 0; tileRow < gridHeight; tileRow++) {
            for (int tileColumn = 0; tileColumn < gridWidth; tileColumn++) {
                grid[tileRow][tileColumn] = 'M';
            }
        }

        int centerColumn = gridWidth / 2;
        int centerRow    = gridHeight / 2;

        // --- Central ward (open floor rectangle centred on the grid) ---
        int wardLeft   = centerColumn - LevelGenConstants.MED_BAY_WARD_WIDTH / 2;
        int wardRight  = wardLeft + LevelGenConstants.MED_BAY_WARD_WIDTH - 1;
        int wardBottom = centerRow - LevelGenConstants.MED_BAY_WARD_HEIGHT / 2;
        int wardTop    = wardBottom + LevelGenConstants.MED_BAY_WARD_HEIGHT - 1;
        carveFloor(grid, wardLeft, wardBottom, wardRight, wardTop);

        // --- Approach corridor (3-tile-wide, running left into the ward's mid row) ---
        int approachHalf   = LevelGenConstants.MED_BAY_APPROACH_HALF_WIDTH;
        int approachRight  = wardLeft - 1;
        int approachLeft   = approachRight - LevelGenConstants.MED_BAY_APPROACH_LENGTH + 1;
        carveFloor(grid, approachLeft, centerRow - approachHalf, approachRight, centerRow + approachHalf);
        // Emergency strip walls 'S' flanking the approach — a clinical corridor accent.
        paintWallRun(grid, approachLeft, approachRight, centerRow - approachHalf - 1, 'S');
        paintWallRun(grid, approachLeft, approachRight, centerRow + approachHalf + 1, 'S');

        // --- Cryo-pod alcove(s) carved off the ward, framed in cryo walls 'Z' ---
        int alcoveSize = LevelGenConstants.MED_BAY_ALCOVE_SIZE;
        boolean alcoveOnLeft = random.nextBoolean();
        int alcoveLeft = alcoveOnLeft ? centerColumn - alcoveSize - 1 : centerColumn + 2;
        // Clamp the alcove within the ward's column span so its doorway always lands on ward floor.
        alcoveLeft = Math.max(wardLeft + 1, Math.min(alcoveLeft, wardRight - alcoveSize));
        int alcoveRight = alcoveLeft + alcoveSize - 1;

        // Top alcove (always present).
        int topAlcoveBottom = wardTop + 2;
        int topAlcoveTop    = topAlcoveBottom + alcoveSize - 1;
        carveAlcove(grid, alcoveLeft, topAlcoveBottom, alcoveRight, topAlcoveTop, wardTop + 1);
        lineAlcovePods(grid, alcoveLeft, alcoveRight, topAlcoveTop);

        // Optional bottom alcove (a second pod bay) — the deterministic pod-count variation.
        boolean secondAlcove = random.nextBoolean();
        if (secondAlcove) {
            int bottomAlcoveTop    = wardBottom - 2;
            int bottomAlcoveBottom = bottomAlcoveTop - alcoveSize + 1;
            carveAlcove(grid, alcoveLeft, bottomAlcoveBottom, alcoveRight, bottomAlcoveTop, wardBottom - 1);
            lineAlcovePods(grid, alcoveLeft, alcoveRight, bottomAlcoveBottom);
        }

        // --- Ward viewing window: a band of reinforced glass 'N' on the ward's top wall ---
        int windowLeft = wardLeft + 2;
        for (int tileColumn = windowLeft; tileColumn < windowLeft + LevelGenConstants.MED_BAY_WINDOW_WIDTH; tileColumn++) {
            setIfWall(grid, tileColumn, wardTop + 1, 'N');
        }

        // --- The auto-doc heal station at the ward centre (solid; player heals when adjacent) ---
        int stationColumn = centerColumn;
        int stationRow    = centerRow;
        grid[stationRow][stationColumn] = RouteMapConstants.HEAL_STATION_SYMBOL; // 'W' holo-workstation

        // --- Exit a few tiles PAST the auto-doc so the heal is on the natural path out ---
        int exitColumn = wardRight - 1;
        grid[centerRow][exitColumn] = RenderConstants.STAIRS_DOWN_CHAR; // '>'

        // --- Player start at the mouth of the approach corridor ---
        int startColumn = approachLeft;
        grid[centerRow][startColumn] = 'p';

        // --- Ward monitoring stations + bio ambience (solid props; never over p/>/W) ---
        placeProp(grid, wardLeft + 1, wardBottom + 1, '%');      // humming generator in a corner
        placeProp(grid, wardLeft + 1, wardTop - 1, 'T');         // monitoring terminal
        placeProp(grid, wardRight - 1, wardBottom + 1, 'T');     // monitoring terminal
        placeProp(grid, wardLeft + 3, wardBottom + 1, '&');      // bio-pod
        placeProp(grid, wardRight - 3, wardBottom + 1, 'I');     // specimen tank
        placeProp(grid, wardLeft + 3, wardTop - 1, 'I');         // specimen tank
        placeProp(grid, wardRight - 3, wardTop - 1, '&');        // bio-pod

        // --- Sparse decals: the staff didn't all make it (walkable, never on p/>/W) ---
        placeDecal(grid, wardLeft + 4, centerRow - 2, 'm');      // a lone corpse
        placeDecal(grid, approachLeft + 2, centerRow - approachHalf, 'O'); // oil at the entrance
        placeDecal(grid, approachLeft + 1, centerRow, 'f');      // one flickering light at the mouth

        List<EnemySpawnPoint>  enemySpawnPoints  = new ArrayList<>(); // ZERO enemies — the sanctuary.
        List<WeaponSpawnPoint> weaponSpawnPoints = new ArrayList<>();
        Level level = new Level(grid, enemySpawnPoints, weaponSpawnPoints);
        level.markHealStation(stationColumn, stationRow);
        return level;
    }

    // -------------------------------------------------------------------------
    // Carving + painting helpers (all bounds-guarded)
    // -------------------------------------------------------------------------

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

    /**
     * Carves an alcove rectangle, frames it in cryo walls 'Z', then punches a 2-tile doorway through
     * the {@code doorRow} wall that separates it from the ward so it stays connected.
     */
    private void carveAlcove(char[][] grid, int left, int bottom, int right, int top, int doorRow) {
        carveFloor(grid, left, bottom, right, top);
        // Frame the ring just outside the alcove with cryo walls (only where it is still 'M' mass).
        for (int tileColumn = left - 1; tileColumn <= right + 1; tileColumn++) {
            setIfWall(grid, tileColumn, bottom - 1, 'Z');
            setIfWall(grid, tileColumn, top + 1, 'Z');
        }
        for (int tileRow = bottom - 1; tileRow <= top + 1; tileRow++) {
            setIfWall(grid, left - 1, tileRow, 'Z');
            setIfWall(grid, right + 1, tileRow, 'Z');
        }
        // Doorway through the separating wall row (2 tiles wide, centred).
        int doorColumn = left + 1;
        carveFloor(grid, doorColumn, doorRow, doorColumn + 1, doorRow);
    }

    /** Lines an alcove's far wall row with alternating bio-pods '&' and specimen tanks 'I'. */
    private void lineAlcovePods(char[][] grid, int left, int right, int wallRow) {
        for (int tileColumn = left; tileColumn <= right; tileColumn++) {
            char prop = ((tileColumn - left) % 2 == 0) ? '&' : 'I';
            placeProp(grid, tileColumn, wallRow, prop);
        }
    }

    /** Paints a horizontal run of a wall accent char, only over existing wall mass 'M'. */
    private void paintWallRun(char[][] grid, int left, int right, int row, char wall) {
        for (int tileColumn = left; tileColumn <= right; tileColumn++) {
            setIfWall(grid, tileColumn, row, wall);
        }
    }

    /** Sets a tile to a wall accent only if it is currently plain medical wall 'M'. */
    private void setIfWall(char[][] grid, int tileColumn, int tileRow, char wall) {
        if (inBounds(grid, tileColumn, tileRow) && grid[tileRow][tileColumn] == 'M') {
            grid[tileRow][tileColumn] = wall;
        }
    }

    /** Places a solid prop only on open lit floor, so it never overwrites the start/exit/auto-doc. */
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
