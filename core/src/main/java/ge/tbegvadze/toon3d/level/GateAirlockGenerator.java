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
 * <p>Since level-generation-overhaul G7 the bulkhead IS the threshold room's single entrance socket, set in its
 * left wall where the approach ends, and the fixed AIRLOCK kit ({@link #airlockKit}) is placed flush by the
 * {@link RoomDresser}.
 *
 * <p>It is a PACING BREATH — almost no combat. Accordingly it emits ZERO enemy spawn points and no
 * weapon spawns ({@code GateProfile} also applies a near-zero budget). Deterministic from the floor
 * seed. Every tile is from {@code docs/tile-symbols.txt}. Grid convention: (0,0) = bottom-left, Y-up.
 * No LibGDX imports.
 */
public class GateAirlockGenerator implements ILevelGenerator {

    /** The blue keycard bulkhead (docs/tile-symbols.txt). */
    private static final char BULKHEAD_DOOR_TILE = 'B';
    /** The kit id (the audit's terminal rooms include it) and the layout report's room id. */
    static final String AIRLOCK_KIT_ID    = "airlock";
    private static final String THRESHOLD_ROOM_ID = "airlock_threshold";

    private final Random random;
    // Raw floor seed: the LevelFinisher's own wall-dressing stream derives from it (independent of
    // `random`, so it never perturbs the generated grid). See LevelFinisher.
    private final long   seed;

    // The floor's config (balance-overhaul order 2): the node's supply spec, the carried ammo types and
    // the weapon cadence flag the shared supply pipeline reads. Null = this generator's node-type defaults.
    private final LevelGenConfig config;

    public GateAirlockGenerator(long seed) {
        this(seed, null);
    }

    public GateAirlockGenerator(long seed, LevelGenConfig config) {
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

        // --- Approach corridor (3 tiles wide), running up to the threshold room's left wall (G7) ---
        int approachHalf  = LevelGenConstants.GATE_APPROACH_HALF_WIDTH;
        int approachRight = roomLeft - 2;
        int approachLeft  = approachRight - LevelGenConstants.GATE_APPROACH_LENGTH + 1;
        carveFloor(grid, approachLeft, centerRow - approachHalf, approachRight, centerRow + approachHalf);

        // --- The bulkhead: the 'B' blue-door IS the threshold room's single entrance socket, sitting in its left
        //     wall and flanked by shell walls (R14) ---
        LayoutRoom room = new LayoutRoom(roomLeft - 1, roomBottom - 1, roomRight + 1, roomTop + 1,
                LevelLayoutReport.Role.REGULAR, THRESHOLD_ROOM_ID);
        List<LayoutRoom> rooms = new ArrayList<>();
        rooms.add(room);
        DoorSocket bulkhead = DoorwayRules.openSocket(grid, room, roomLeft - 1, centerRow);
        bulkhead.setLeaf(DoorSocket.Leaf.DOOR);
        grid[centerRow][roomLeft - 1] = BULKHEAD_DOOR_TILE; // blue keycard door

        // --- The blue keycard 'b' on the approach, BEFORE the door, so it always opens ---
        int keycardColumn = approachLeft + 2;
        grid[centerRow][keycardColumn] = 'b';

        // --- Exit a few tiles past the threshold-room centre (the descent onward) ---
        int exitColumn = roomRight - 1;
        grid[centerRow][exitColumn] = RenderConstants.STAIRS_DOWN_CHAR; // '>'

        // --- Player start at the mouth of the approach corridor ---
        int startColumn = approachLeft;
        grid[centerRow][startColumn] = 'p';

        // --- Sparse ceremonial dressing: the fixed AIRLOCK kit, flush (G7); an optional flickering strip light ---
        new RoomDresser(grid, rooms, seed).dress(room, airlockKit(), null);
        if (random.nextBoolean()) {
            placeDecal(grid, roomLeft + 2, centerRow - 2, 'f'); // a flickering strip light
        }
        DoorwayRules.Validation validation = DoorwayRules.validate(grid, rooms);

        List<EnemySpawnPoint>  enemySpawnPoints  = new ArrayList<>(); // ZERO enemies — a pacing breath.
        List<WeaponSpawnPoint> weaponSpawnPoints = new ArrayList<>();
        // SUPPLY (balance-overhaul order 2, S1): even a bespoke chamber goes through the shared pipeline —
        // its node type's spec decides what it holds (today's contents; order 6 retunes REST / EVENT).
        FloorPopulator.Result populated = FloorPopulator.populate(ge.tbegvadze.toon3d.route.GeneratorId.GATE_AIRLOCK.stableId(), grid,
                new PocketSupplyRegions(grid[0].length, LevelGenConstants.SPECIAL_ROOM_SUPPLY_POCKET_SIZE,
                        LevelGenConstants.SPECIAL_ROOM_SUPPLY_POCKET_SIZE),
                enemySpawnPoints, new FloorPopulator.EncounterFacts(-1, 0f, 0f, 0, 0), config,
                FloorPopulator.specOf(config, ge.tbegvadze.toon3d.route.RouteNodeType.REGION_GATE), dungeonDepth, seed);
        Level level = populated.attachTo(new Level(grid, populated.spawnPoints, populated.weaponSpawnPoints,
                         LevelFinisher.request(grid, seed).config(config)
                                 .floorGroup(ge.tbegvadze.toon3d.tileset.WallGroup.SECURITY)
                                 .basePalette(LevelPalettes.legacy()).finish()));
        level.attachLayoutReport(LayoutSupport.report(rooms, null, 0, validation));
        return level;
    }

    /** The threshold room's fixed AIRLOCK kit (G7): a monitoring 'T' and a '%' generator, both flush. */
    static PropKit airlockKit() {
        return PropKit.plain(AIRLOCK_KIT_ID)
                .row(PropKitRow.place('T').at(PropAnchor.SIDE_WALL).count(1))
                .row(PropKitRow.place('%').at(PropAnchor.CORNER).count(1))
                .build();
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

    /** Places a floor light only on open lit floor, so it never overwrites p / > / b / B or a prop. */
    private void placeDecal(char[][] grid, int tileColumn, int tileRow, char decal) {
        if (inBounds(grid, tileColumn, tileRow) && grid[tileRow][tileColumn] == 'l') {
            grid[tileRow][tileColumn] = decal;
        }
    }

    private boolean inBounds(char[][] grid, int tileColumn, int tileRow) {
        return tileRow >= 0 && tileRow < grid.length
            && tileColumn >= 0 && tileColumn < grid[0].length;
    }
}
