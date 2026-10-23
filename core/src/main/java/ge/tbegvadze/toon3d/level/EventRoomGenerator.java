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
 * <p>Since level-generation-overhaul G6 the chamber is a real room: the approach enters through a DOOR socket,
 * the station is the room's claimed centre, and the fixed EVENT kit ({@link #eventKit}) is placed flush by the
 * {@link RoomDresser}.
 *
 * <p>Deterministic from the floor seed (only small decal placement varies). Every tile is from
 * {@code docs/tile-symbols.txt}. Grid convention: (0,0) = bottom-left tile, Y-up. No LibGDX imports.
 */
public class EventRoomGenerator implements ILevelGenerator {

    /** The kit id (the audit's terminal rooms include it) and the layout report's room id. */
    static final String EVENT_KIT_ID  = "event";
    private static final String EVENT_ROOM_ID = "event_room";

    private final Random random;
    // Raw floor seed: the LevelFinisher's own wall-dressing stream derives from it (independent of
    // `random`, so it never perturbs the generated grid). See LevelFinisher.
    private final long   seed;

    // The floor's config (balance-overhaul order 2): the node's supply spec, the carried ammo types and
    // the weapon cadence flag the shared supply pipeline reads. Null = this generator's node-type defaults.
    private final LevelGenConfig config;

    public EventRoomGenerator(long seed) {
        this(seed, null);
    }

    public EventRoomGenerator(long seed, LevelGenConfig config) {
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

        // --- Approach corridor (3 tiles wide), ending one tile short of the room: the room's left wall stays shell
        //     and the corridor's mid row enters through a DOOR socket (G6, R14) ---
        int approachHalf  = LevelGenConstants.EVENT_ROOM_APPROACH_HALF_WIDTH;
        int approachRight = roomLeft - 2;
        int approachLeft  = approachRight - LevelGenConstants.EVENT_ROOM_APPROACH_LENGTH + 1;
        carveFloor(grid, approachLeft, centerRow - approachHalf, approachRight, centerRow + approachHalf);
        LayoutRoom room = new LayoutRoom(roomLeft - 1, roomBottom - 1, roomRight + 1, roomTop + 1,
                LevelLayoutReport.Role.REGULAR, EVENT_ROOM_ID);
        List<LayoutRoom> rooms = new ArrayList<>();
        rooms.add(room);
        DoorwayRules.hangDoor(grid, DoorwayRules.openSocket(grid, room, roomLeft - 1, centerRow));

        // --- The central interactable ('W' holo-workstation) — the event station, solid, the room's CENTRE
        //     feature so the lanes end on the ring around it ---
        int stationColumn = centerColumn - 1;
        int stationRow    = centerRow;
        grid[stationRow][stationColumn] = RouteMapConstants.EVENT_STATION_SYMBOL; // 'W'
        List<int[]> station = new ArrayList<>();
        station.add(new int[]{ stationColumn, stationRow });
        room.claimCentre(station);

        // --- Exit a couple of tiles PAST the interactable so the beat is on the path out ---
        int exitColumn = roomRight - 1;
        grid[centerRow][exitColumn] = RenderConstants.STAIRS_DOWN_CHAR; // '>'

        // --- Player start at the mouth of the approach corridor ---
        int startColumn = approachLeft;
        grid[centerRow][startColumn] = 'p';

        // --- Set dressing: the fixed EVENT kit, flush (G6); one flickering light by the console; optional oil ---
        new RoomDresser(grid, rooms, seed).dress(room, eventKit(), null);
        placeDecal(grid, stationColumn - 1, centerRow + 1, 'f'); // one flickering light by the console
        // A little deterministic-from-seed clutter so two event rooms are never quite identical.
        if (random.nextBoolean()) {
            placeDecal(grid, approachLeft + 2, centerRow, 'O');  // oil at the entrance
        }
        DoorwayRules.Validation validation = DoorwayRules.validate(grid, rooms);

        List<EnemySpawnPoint>  enemySpawnPoints  = new ArrayList<>(); // ZERO enemies — a story beat.
        List<WeaponSpawnPoint> weaponSpawnPoints = new ArrayList<>();
        // SUPPLY (balance-overhaul order 2, S1): even a bespoke chamber goes through the shared pipeline —
        // its node type's spec decides what it holds (today's contents; order 6 retunes REST / EVENT).
        FloorPopulator.Result populated = FloorPopulator.populate(ge.tbegvadze.toon3d.route.GeneratorId.EVENT_ROOM.stableId(), grid,
                new PocketSupplyRegions(grid[0].length, LevelGenConstants.SPECIAL_ROOM_SUPPLY_POCKET_SIZE,
                        LevelGenConstants.SPECIAL_ROOM_SUPPLY_POCKET_SIZE),
                enemySpawnPoints, new FloorPopulator.EncounterFacts(-1, 0f, 0f, 0, 0), config,
                FloorPopulator.specOf(config, ge.tbegvadze.toon3d.route.RouteNodeType.EVENT), dungeonDepth, seed);
        Level level = populated.attachTo(new Level(grid, populated.spawnPoints, populated.weaponSpawnPoints,
                                LevelFinisher.request(grid, seed).config(config)
                                        .floorGroup(ge.tbegvadze.toon3d.tileset.WallGroup.SIGNATURE)
                                        .basePalette(LevelPalettes.legacy()).finish()));
        level.markEventStation(stationColumn, stationRow);
        level.attachLayoutReport(LayoutSupport.report(rooms, null, 0, validation));
        return level;
    }

    /** The room's fixed EVENT kit (G6): a 'T' terminal and a '%' generator flush to walls, an optional corpse. */
    static PropKit eventKit() {
        return PropKit.plain(EVENT_KIT_ID)
                .row(PropKitRow.place('T').at(PropAnchor.SIDE_WALL).count(1))
                .row(PropKitRow.place('%').at(PropAnchor.CORNER).count(1))
                .row(PropKitRow.place('m').at(PropAnchor.ANY_WALL).count(1).chance(0.5f))
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

    /** Places a walkable decal (or floor light) only on open lit floor, so it never overwrites p / > / W. */
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
