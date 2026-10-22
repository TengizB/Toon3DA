package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.tileset.LevelPalettes;
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
 * is from {@code docs/tile-symbols.txt}. Its walls are plain 'x' mass; the LevelFinisher dresses the whole
 * floor in its region set's MEDICAL family (generated-wall-art order 2, rule P3);
 * '&amp;' bio-pods, 'I' specimen tanks, 'T' terminals and a '%' generator dress the ward; the auto-doc
 * reuses the holo-workstation 'W' (no new symbol — the heal interactivity is layered on by World).
 *
 * <p>Grid convention: (0,0) = bottom-left tile, Y-up. No LibGDX imports — pure Java.
 */
public class MedBayGenerator implements ILevelGenerator {

    private final Random random;
    // Raw floor seed: the LevelFinisher's own wall-dressing stream derives from it (independent of
    // `random`, so it never perturbs the generated grid). See LevelFinisher.
    private final long   seed;

    // The floor's config (balance-overhaul order 2): the node's supply spec, the carried ammo types and
    // the weapon cadence flag the shared supply pipeline reads. Null = this generator's node-type defaults.
    private final LevelGenConfig config;

    public MedBayGenerator(long seed) {
        this(seed, null);
    }

    public MedBayGenerator(long seed, LevelGenConfig config) {
        this.random = new Random(seed);
        this.seed   = seed;
        this.config = config;
    }

    @Override
    public Level generate() {
        return generate(1);
    }

    @Override
    public Level generate(int dungeonDepth) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;

        // Everything starts as wall 'x'; the clinic is carved out of that mass.
        char[][] grid = new char[gridHeight][gridWidth];
        for (int tileRow = 0; tileRow < gridHeight; tileRow++) {
            for (int tileColumn = 0; tileColumn < gridWidth; tileColumn++) {
                grid[tileRow][tileColumn] = 'x';
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

        // --- Cryo-pod alcove(s) carved off the ward ---
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
        // SUPPLY (balance-overhaul order 2, S1): even a bespoke chamber goes through the shared pipeline —
        // its node type's spec decides what it holds (today's contents; order 6 retunes REST / EVENT).
        FloorPopulator.Result populated = FloorPopulator.populate(ge.tbegvadze.toon3d.route.GeneratorId.MED_BAY.stableId(), grid,
                new PocketSupplyRegions(grid[0].length, LevelGenConstants.SPECIAL_ROOM_SUPPLY_POCKET_SIZE,
                        LevelGenConstants.SPECIAL_ROOM_SUPPLY_POCKET_SIZE),
                enemySpawnPoints, new FloorPopulator.EncounterFacts(-1, 0f, 0f, 0, 0), config,
                FloorPopulator.specOf(config, ge.tbegvadze.toon3d.route.RouteNodeType.REST), dungeonDepth, seed);
        Level level = populated.attachTo(new Level(grid, populated.spawnPoints, populated.weaponSpawnPoints,
                                LevelFinisher.request(grid, seed).config(config)
                                        .floorGroup(ge.tbegvadze.toon3d.tileset.WallGroup.MEDICAL)
                                        .basePalette(LevelPalettes.legacy()).finish()));
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
     * Carves an alcove rectangle, then punches a 2-tile doorway through
     * the {@code doorRow} wall that separates it from the ward so it stays connected.
     */
    private void carveAlcove(char[][] grid, int left, int bottom, int right, int top, int doorRow) {
        carveFloor(grid, left, bottom, right, top);
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
