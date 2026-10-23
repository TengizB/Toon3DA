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
 * floor in its region set's MEDICAL family (generated-wall-art order 2, rule P3). Since
 * level-generation-overhaul G5 the ward and its pod bays are real rooms: the approach ends at the clinic's
 * entrance DOOR socket, each bay opens through one open socket, and the fixed CLINIC kits ({@link #clinicKit},
 * {@link #clinicPodsKit}) are placed flush by the {@link RoomDresser}; the auto-doc reuses the holo-workstation
 * 'W' as the ward's claimed centre (no new symbol — the heal interactivity is layered on by World).
 *
 * <p>Grid convention: (0,0) = bottom-left tile, Y-up. No LibGDX imports — pure Java.
 */
public class MedBayGenerator implements ILevelGenerator {

    /** The kit ids (the audit's terminal rooms include the clinic) and the layout report's room ids. */
    static final String CLINIC_KIT_ID      = "clinic";
    static final String CLINIC_PODS_KIT_ID = "clinic_pods";
    private static final String WARD_ROOM_ID = "clinic_ward";
    private static final String PODS_ROOM_ID = "clinic_pods";

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

        // --- Approach corridor (3 tiles wide), ending one tile short of the ward: its left wall stays shell and
        //     the corridor's mid row meets the ward through the clinic's entrance DOOR socket (G5, R14). ---
        int approachHalf   = LevelGenConstants.MED_BAY_APPROACH_HALF_WIDTH;
        int approachRight  = wardLeft - 2;
        int approachLeft   = approachRight - LevelGenConstants.MED_BAY_APPROACH_LENGTH + 1;
        carveFloor(grid, approachLeft, centerRow - approachHalf, approachRight, centerRow + approachHalf);
        LayoutRoom ward = new LayoutRoom(wardLeft - 1, wardBottom - 1, wardRight + 1, wardTop + 1,
                LevelLayoutReport.Role.REGULAR, WARD_ROOM_ID);
        List<LayoutRoom> rooms = new ArrayList<>();
        rooms.add(ward);
        DoorwayRules.hangDoor(grid, DoorwayRules.openSocket(grid, ward, wardLeft - 1, centerRow));

        // --- Cryo-pod alcove(s) off the ward, each opening through ONE open socket in the shared wall ---
        int alcoveSize = LevelGenConstants.MED_BAY_ALCOVE_SIZE;
        boolean alcoveOnLeft = random.nextBoolean();
        int alcoveLeft = alcoveOnLeft ? centerColumn - alcoveSize - 1 : centerColumn + 2;
        // Clamp the alcove within the ward's column span so its socket always lands on ward floor.
        alcoveLeft = Math.max(wardLeft + 1, Math.min(alcoveLeft, wardRight - alcoveSize));
        int alcoveRight = alcoveLeft + alcoveSize - 1;

        // Top alcove (always present).
        int topAlcoveBottom = wardTop + 2;
        int topAlcoveTop    = topAlcoveBottom + alcoveSize - 1;
        rooms.add(carveAlcove(grid, ward, alcoveLeft, topAlcoveBottom, alcoveRight, topAlcoveTop, wardTop + 1));

        // Optional bottom alcove (a second pod bay) — the deterministic pod-count variation.
        boolean secondAlcove = random.nextBoolean();
        if (secondAlcove) {
            int bottomAlcoveTop    = wardBottom - 2;
            int bottomAlcoveBottom = bottomAlcoveTop - alcoveSize + 1;
            rooms.add(carveAlcove(grid, ward, alcoveLeft, bottomAlcoveBottom, alcoveRight, bottomAlcoveTop, wardBottom - 1));
        }

        // --- The auto-doc heal station at the ward centre (solid; player heals when adjacent): the ward's CENTRE
        //     feature, so the dresser routes every lane to the ring around it ---
        int stationColumn = centerColumn;
        int stationRow    = centerRow;
        grid[stationRow][stationColumn] = RouteMapConstants.HEAL_STATION_SYMBOL; // 'W' holo-workstation
        List<int[]> station = new ArrayList<>();
        station.add(new int[]{ stationColumn, stationRow });
        ward.claimCentre(station);

        // --- Exit a few tiles PAST the auto-doc so the heal is on the natural path out ---
        int exitColumn = wardRight - 1;
        grid[centerRow][exitColumn] = RenderConstants.STAIRS_DOWN_CHAR; // '>'

        // --- Player start at the mouth of the approach corridor ---
        int startColumn = approachLeft;
        grid[centerRow][startColumn] = 'p';

        // --- The fixed CLINIC dressing, every prop flush (G5): the ward's kit and the pod bays' beds ---
        RoomDresser dresser = new RoomDresser(grid, rooms, seed);
        dresser.dress(ward, clinicKit(), null);
        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) dresser.dress(rooms.get(roomIndex), clinicPodsKit(), null);

        // --- The approach's one decal (walkable) and one flickering light at the mouth ---
        placeDecal(grid, approachLeft + 2, centerRow - approachHalf, 'O'); // oil at the entrance
        placeDecal(grid, approachLeft + 1, centerRow, 'f');               // one flickering light at the mouth
        DoorwayRules.Validation validation = DoorwayRules.validate(grid, rooms);

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
        level.attachLayoutReport(LayoutSupport.report(rooms, null, 0, validation));
        return level;
    }

    /**
     * The ward's fixed CLINIC kit (G5): two 'T' consoles flush on the side walls (the ward's top and bottom walls —
     * its entry is the left wall), two 'I' tanks in ward corners, one '%' generator flush in another corner, and the
     * one staff member who did not make it. The auto-doc is the room's claimed centre, not a kit row.
     */
    static PropKit clinicKit() {
        return PropKit.plain(CLINIC_KIT_ID)
                .row(PropKitRow.place('T').at(PropAnchor.SIDE_WALL).count(2))
                .row(PropKitRow.place('I').at(PropAnchor.CORNER).count(2))
                .row(PropKitRow.place('%').at(PropAnchor.CORNER).count(1))
                .row(PropKitRow.place('m').at(PropAnchor.ANY_WALL).count(1))
                .build();
    }

    /** A pod bay's beds (G5): '&' SPACED along the far wall, opposite its socket. */
    static PropKit clinicPodsKit() {
        return PropKit.plain(CLINIC_PODS_KIT_ID)
                .row(PropKitRow.place('&').at(PropAnchor.BACK_WALL).count(2).as(PropKitRow.Mode.SPACED))
                .build();
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
     * Carves a pod bay and returns it as a room whose ONE open socket sits in the wall row it shares with the ward
     * ({@code socketRow}), centred on the bay; the ward holds the same socket. Both shells keep the flanks (R14).
     */
    private LayoutRoom carveAlcove(char[][] grid, LayoutRoom ward, int left, int bottom, int right, int top, int socketRow) {
        carveFloor(grid, left, bottom, right, top);
        LayoutRoom alcove = new LayoutRoom(left - 1, bottom - 1, right + 1, top + 1,
                LevelLayoutReport.Role.SPECIAL, PODS_ROOM_ID);
        int socketColumn = left + LevelGenConstants.MED_BAY_ALCOVE_SIZE / 2;
        DoorSocket socket = DoorwayRules.openSocket(grid, alcove, socketColumn, socketRow);
        ward.addSocket(new DoorSocket(socket.tileColumn, socket.tileRow, ward.sideOf(socket.tileColumn, socket.tileRow)));
        return alcove;
    }

    /** Places a walkable decal only on open lit floor, so it never overwrites the start. */
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
