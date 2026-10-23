package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.tileset.LevelPalettes;
import ge.tbegvadze.toon3d.util.LevelGenConstants;
import ge.tbegvadze.toon3d.util.RenderConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * LINEAR_CORRIDOR — one long 3-tile-wide spine (horizontal 70 % / vertical 30 %, with one perpendicular bend at its
 * tail) and side rooms off both walls, like a vertebrate skeleton. Rebuilt SPECIAL ROOMS FIRST
 * (level-generation-overhaul G2 / pipeline A):
 * <pre>
 *  1 SPECIAL PLAN   SpecialRoomPlanner, once per floor (R1-R3)
 *  2 SPINE          the artery and its bend; side-room SLOTS stepped along both
 *  3 ROOMS          the entrance at the head slot; the planned specials take slots FIRST at evenly spaced fractions of
 *                   the spine (alternating sides), each sized from its window, moving on to the next slot when one
 *                   will not fit; regular rooms then fill the remaining slots at LEVEL_GEN_SPINE_SIDE_ROOM_CHANCE
 *  4 SOCKETS        each side room's one socket is on its spine-facing wall; its outer tile is spine floor (R10/R11)
 *  5 PLAZA          the optional crossroads, carved only >= 1 tile from every shell (R9)
 *  6 architecture, dressing (regular rooms' BARE kit takes the interior-layout variants), the hall pass (spaced spine
 *    columns, hall decals), the doorway guard, the repair backstop, stairs, encounter + supply (shared pipeline)
 * </pre>
 * Every draw comes from the layout's seeded stream (or the dresser's own). Grid convention: (0,0) = bottom-left
 * tile, Y-up. Headless — no LibGDX.
 */
public class LinearCorridorGenerator implements ILevelGenerator, SupplySlotProvider {

    /** One straight leg of the spine: horizontal runs along a row, vertical along a column. */
    private static final class SpineRun {
        final boolean horizontal;
        final int     line;
        final int     start;
        final int     end;
        // The run's head (its junction with the run before it, or the spine's head) and its tail.
        final int     head;
        final int     tail;

        SpineRun(boolean horizontal, int line, int from, int to) {
            this.horizontal = horizontal;
            this.line       = line;
            this.start      = Math.min(from, to);
            this.end        = Math.max(from, to);
            this.head       = from;
            this.tail       = to;
        }

        int length() { return end - start + 1; }
    }

    /** A side-room position along a run; {@code spinePosition} is its distance from the spine's head. */
    private static final class Slot {
        final SpineRun run;
        final int      along;
        final int      spinePosition;
        final boolean[] sideTaken = new boolean[2];

        Slot(SpineRun run, int along, int spinePosition) {
            this.run           = run;
            this.along         = along;
            this.spinePosition = spinePosition;
        }
    }

    private       Random         random;
    private final long           seed;
    private final LevelGenConfig config;
    private int dungeonDepth = 1;

    // E6 footprint (balance-overhaul order 2): the spine (and its bend) is scaled to this fraction of its length.
    private float footprintScale = 1f;
    private int   footprintTarget;

    private List<RoomBlueprint> plannedSpecials = Collections.emptyList();
    private boolean             lastResort;

    private int[][]          supplyRegionMap;
    private List<LayoutRoom> supplyRooms;
    private RoomDresser      dresser;

    public LinearCorridorGenerator(long seed) {
        this(seed, new LevelGenConfig());
    }

    public LinearCorridorGenerator(long seed, LevelGenConfig config) {
        this.random = new Random(seed);
        this.seed   = seed;
        this.config = config;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Override
    public Level generate(int dungeonDepth) {
        this.dungeonDepth = Math.max(1, dungeonDepth);
        return generate();
    }

    /**
     * Plans the floor's specials once, then builds the corridor to its E6 footprint target ({@link FootprintPlanner}
     * rebuilds at a corrected spine length). An attempt that cannot seat every planned special is discarded; when
     * every one was, the floor is built at full length with the specials at their window minimums (R5).
     */
    @Override
    public Level generate() {
        RoomBlueprints.bootstrap();
        int target = FootprintPlanner.targetWalkableTiles(config, dungeonDepth,
                LevelGenConstants.FOOTPRINT_NATURAL_WALKABLE_LINEAR);
        plannedSpecials = SpecialRoomPlanner.plan(seed,
                target > 0 ? target : Math.round(LevelGenConstants.FOOTPRINT_NATURAL_WALKABLE_LINEAR));
        Level level = FootprintPlanner.buildToTarget(config, dungeonDepth,
                LevelGenConstants.FOOTPRINT_NATURAL_WALKABLE_LINEAR, seed, (scale, attemptSeed, attemptTarget) -> {
                    footprintScale  = scale;
                    footprintTarget = attemptTarget;
                    random          = new Random(attemptSeed);
                    return generateOnce();
                });
        if (level == null) {
            lastResort     = true;
            footprintScale = 1f;
            random         = new Random(seed);
            level          = generateOnce();
            lastResort     = false;
        }
        return level != null ? level : buildFallbackLevel();
    }

    // -------------------------------------------------------------------------
    // One footprint attempt
    // -------------------------------------------------------------------------

    private Level generateOnce() {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        char[][] grid = new char[gridHeight][gridWidth];
        for (char[] row : grid) java.util.Arrays.fill(row, 'x');

        // 2 — the spine, its bend, the plaza roll, the slots.
        List<SpineRun> runs = buildSpine();
        List<List<int[]>> spineLines = new ArrayList<>();
        for (SpineRun run : runs) spineLines.add(carveRun(grid, run));
        int[] plaza = null;
        if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_PLAZA_CHANCE) {
            SpineRun first = runs.get(0);
            int along = (first.start + first.end) / 2 + randomBetween(-4, 4);
            plaza = first.horizontal ? new int[]{ along, first.line } : new int[]{ first.line, along };
        }
        List<Slot> slots = buildSlots(runs);
        if (slots.isEmpty()) return null;

        // 3/4 — the entrance, the specials first, then the regular rooms; one spine-facing socket each.
        List<LayoutRoom> rooms = new ArrayList<>();
        Map<LayoutRoom, Integer> spinePosition = new IdentityHashMap<>();
        LayoutRoom entrance = placeEntrance(grid, slots.get(0), rooms, spinePosition);
        if (entrance == null) return null;
        if (!placeSpecials(grid, slots, rooms, spinePosition)) return null;
        placeRegularRooms(grid, slots, rooms, spinePosition);

        // 5 — the plaza, never within a tile of a shell (R9).
        List<int[]> plazaColumns = plaza == null ? Collections.emptyList() : carvePlaza(grid, plaza, rooms);
        LayoutSupport.carveInteriors(grid, rooms);
        LayoutSupport.hangDoors(grid, rooms, random);

        // 6 — architecture (regular rooms take the interior-layout variants), the start, dressing, halls, guards.
        RoomBlueprintRegistry registry = RoomBlueprints.rooms();
        for (LayoutRoom room : rooms) {
            registry.get(room.blueprintId)
                    .build(new RoomBuildContext(grid, room, random, config, rooms).withLinearInteriorVariants());
        }
        grid[entrance.centerRow()][entrance.centerColumn()] = 'p';
        dresser = new RoomDresser(grid, rooms, seed);
        LayoutSupport.dressRooms(dresser, rooms, config);
        List<List<int[]>> hallSpines = new ArrayList<>(spineLines);
        LayoutSupport.dressHalls(grid, rooms, hallSpines, dresser, random);
        for (int[] column : plazaColumns) LayoutSupport.tryHallColumn(grid, column[0], column[1], dresser);
        // The repair backstop runs before the doorway guard, so a socket it opens is validated too.
        int repairCarves = LayoutSupport.repairConnectivity(grid, rooms, entrance.centerColumn(), entrance.centerRow());
        DoorwayRules.Validation validation = DoorwayRules.validate(grid, rooms);
        stampStairsDown(grid, rooms, spinePosition);

        // Encounter (E1-E5) and supply (S1) through the shared pipeline, on the finished layout.
        supplyRooms     = rooms;
        supplyRegionMap = LayoutSupport.supplyRegionMap(rooms, gridWidth, gridHeight);
        ge.tbegvadze.toon3d.route.NodeSupplySpec spec = FloorPopulator.specOf(config);
        EncounterBudgetPlanner.Plan encounter =
                new EncounterBudgetPlanner(dungeonDepth, random, config.enemyBudgetScale, spec).plan();
        EncounterPlacer.Placement placement = EncounterPlacer.place(grid, SupplySlotSurvey.survey(grid, this),
                encounter, (cells, tileColumn, tileRow) -> isEnemySpawnEligible(cells, tileColumn, tileRow)
                        && !SupplySlotSurvey.isAdjacentToDoor(cells, tileColumn, tileRow),
                random, dungeonDepth, spec.shapeRulesApply());
        FloorPopulator.Result populated = FloorPopulator.populate(
                ge.tbegvadze.toon3d.route.GeneratorId.LINEAR_CORRIDOR.stableId(), grid, this, placement.spawnPoints,
                new FloorPopulator.EncounterFacts(placement.anchorSpawnIndex, encounter.spentThreatPoints(),
                        encounter.floorBudget(), encounter.bodyTarget(), footprintTarget),
                config, dungeonDepth, seed);
        Level level = populated.attachTo(new Level(grid, populated.spawnPoints, populated.weaponSpawnPoints,
                finishWalls(grid, rooms)));
        level.attachLayoutReport(LayoutSupport.report(rooms, SpecialRoomPlanner.ids(plannedSpecials), repairCarves,
                validation));
        return level;
    }

    /**
     * Region wall sets (generated-wall-art order 2): the finished grid goes through the {@link LevelFinisher} with
     * every room's interior and its blueprint's wall group. The legacy palette already binds every kit symbol to its
     * own sprite and 'T' / 'E' to the terminal and the barrel (R29).
     */
    private ge.tbegvadze.toon3d.tileset.LevelPalette finishWalls(char[][] grid, List<LayoutRoom> rooms) {
        LevelFinisher.Request request = LevelFinisher.request(grid, seed).config(config)
                .basePalette(LevelPalettes.legacy());
        RoomBlueprintRegistry registry = RoomBlueprints.rooms();
        for (LayoutRoom room : rooms) {
            request.room(room.leftColumn + 1, room.bottomRow + 1, room.rightColumn - 1, room.topRow - 1,
                         registry.get(room.blueprintId).wallGroup());
        }
        return request.finish();
    }

    // -------------------------------------------------------------------------
    // The spine and its slots
    // -------------------------------------------------------------------------

    /**
     * The primary run (75-90 % of the grid's long axis x the footprint scale, centred) and, when there is room, one
     * perpendicular bend from its tail toward the more open side (both shrink with the footprint scale).
     */
    private List<SpineRun> buildSpine() {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        boolean horizontal = random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_HORIZONTAL_CHANCE;
        int axisLength  = horizontal ? gridWidth : gridHeight;
        int crossLength = horizontal ? gridHeight : gridWidth;
        int line = randomBetween((int) (crossLength * 0.40f), (int) (crossLength * 0.60f));
        int length = (int) (axisLength * randomFloat(LevelGenConstants.LEVEL_GEN_SPINE_LENGTH_MIN_FRAC,
                LevelGenConstants.LEVEL_GEN_SPINE_LENGTH_MAX_FRAC) * footprintScale);
        int start = Math.max(1, (axisLength - length) / 2);
        int end   = Math.min(axisLength - 2, start + length - 1);
        List<SpineRun> runs = new ArrayList<>();
        runs.add(new SpineRun(horizontal, line, start, end));

        // The bend: from the tail, toward the side with more room.
        int below = line - 1;
        int above = crossLength - 2 - line;
        boolean towardHigh = above >= below;
        int available = (int) ((towardHigh ? above : below) * footprintScale);
        if (available >= LevelGenConstants.LEVEL_GEN_SPINE_BEND_MIN_LENGTH) {
            int bendLength = Math.max(LevelGenConstants.LEVEL_GEN_SPINE_BEND_MIN_LENGTH, (int) (available * randomFloat(
                    LevelGenConstants.LEVEL_GEN_SPINE_BEND_LENGTH_MIN_FRAC, LevelGenConstants.LEVEL_GEN_SPINE_BEND_LENGTH_MAX_FRAC)));
            int bendEnd = towardHigh ? line + bendLength : line - bendLength;
            runs.add(new SpineRun(!horizontal, end, line, bendEnd));
        }
        return runs;
    }

    /** Carves a run 3 wide (centre ' ', ribs 'l') and returns its centre tiles, head to tail. */
    private List<int[]> carveRun(char[][] grid, SpineRun run) {
        int halfWidth = LevelGenConstants.LEVEL_GEN_SPINE_WIDTH / 2;
        List<int[]> centre = new ArrayList<>();
        for (int along = run.start; along <= run.end; along++) {
            for (int across = -halfWidth; across <= halfWidth; across++) {
                int tileColumn = run.horizontal ? along : run.line + across;
                int tileRow    = run.horizontal ? run.line + across : along;
                if (tileColumn <= 0 || tileRow <= 0 || tileColumn >= grid[0].length - 1 || tileRow >= grid.length - 1) continue;
                if (across == 0) {
                    grid[tileRow][tileColumn] = ' ';
                    centre.add(new int[]{ tileColumn, tileRow });
                } else if (Level.isWall(grid[tileRow][tileColumn])) {
                    grid[tileRow][tileColumn] = 'l';
                }
            }
        }
        return centre;
    }

    /**
     * The side-room slots, head to tail: the head of the primary run, positions stepped every
     * LEVEL_GEN_SPINE_SIDE_STEP_MIN..MAX along each run, and each run's tail. Each slot has two sides.
     */
    private List<Slot> buildSlots(List<SpineRun> runs) {
        List<Slot> slots = new ArrayList<>();
        int offset = 0;
        for (int runIndex = 0; runIndex < runs.size(); runIndex++) {
            SpineRun run = runs.get(runIndex);
            int direction = run.tail >= run.head ? 1 : -1;
            if (runIndex == 0) slots.add(new Slot(run, run.head + direction, offset + 1));
            int distance = randomBetween(LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX,
                    LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX + 2);
            while (distance <= run.length() - 1 - LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX) {
                slots.add(new Slot(run, run.head + direction * distance, offset + distance));
                distance += randomBetween(LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MIN,
                        LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX);
            }
            slots.add(new Slot(run, run.tail - direction, offset + run.length() - 2));
            offset += run.length();
        }
        return slots;
    }

    // -------------------------------------------------------------------------
    // Rooms (R5-R7, G2)
    // -------------------------------------------------------------------------

    /** R6: the entrance at the head slot, a small room (so the floor's first fight is near), either side. */
    private LayoutRoom placeEntrance(char[][] grid, Slot head, List<LayoutRoom> rooms, Map<LayoutRoom, Integer> spinePosition) {
        int maximum = LevelGenConstants.LEVEL_GEN_SPINE_ENTRANCE_MAX_DIM;
        int width  = randomBetween(LevelGenConstants.LEVEL_GEN_ENTRANCE_MIN_DIM, maximum);
        int height = randomBetween(LevelGenConstants.LEVEL_GEN_ENTRANCE_MIN_DIM, maximum);
        int firstSide = random.nextInt(2);
        for (int attempt = 0; attempt < 2; attempt++) {
            int side = (firstSide + attempt) % 2;
            LayoutRoom room = placeSideRoom(grid, head, side, width, height, LevelLayoutReport.Role.ENTRANCE,
                    RoomBlueprints.ID_ENTRANCE, rooms);
            if (room == null) continue;
            head.sideTaken[side] = true;
            spinePosition.put(room, head.spinePosition);
            return room;
        }
        return null;
    }

    /**
     * G2: the planned specials take slots FIRST, at evenly spaced fractions of the spine (alternating sides). A
     * special that will not fit its slot moves to the next slot (and the next…); with no slot left it is retried at
     * its window minimum; still nothing -> false (the attempt is discarded; a special is never swapped or dropped).
     */
    private boolean placeSpecials(char[][] grid, List<Slot> slots, List<LayoutRoom> rooms,
                                  Map<LayoutRoom, Integer> spinePosition) {
        int spineLength = slots.get(slots.size() - 1).spinePosition + 1;
        for (int specialIndex = 0; specialIndex < plannedSpecials.size(); specialIndex++) {
            RoomBlueprint blueprint = plannedSpecials.get(specialIndex);
            int targetPosition = spineLength * (specialIndex + 1) / (plannedSpecials.size() + 1);
            int preferredSide  = specialIndex % 2;
            boolean placed = false;
            for (int pass = 0; pass < 2 && !placed; pass++) {
                boolean atMinimum = lastResort || pass == 1;
                int[] size = SpecialRoomPlanner.size(blueprint, random, footprintScale, atMinimum);
                for (Slot slot : slotsNearest(slots, targetPosition)) {
                    for (int sideAttempt = 0; sideAttempt < 2 && !placed; sideAttempt++) {
                        int side = (preferredSide + sideAttempt) % 2;
                        if (slot.sideTaken[side]) continue;
                        LayoutRoom room = placeSideRoom(grid, slot, side, size[0], size[1],
                                LevelLayoutReport.Role.SPECIAL, blueprint.id(), rooms);
                        if (room == null) continue;
                        slot.sideTaken[side] = true;
                        spinePosition.put(room, slot.spinePosition);
                        placed = true;
                    }
                    if (placed) break;
                }
            }
            if (!placed) return false;
        }
        return true;
    }

    /** The slots ordered by distance from a spine position (the head slot excluded: it is the entrance's). */
    private static List<Slot> slotsNearest(List<Slot> slots, int targetPosition) {
        List<Slot> ordered = new ArrayList<>(slots.subList(1, slots.size()));
        ordered.sort((first, second) -> {
            int byDistance = Math.abs(first.spinePosition - targetPosition) - Math.abs(second.spinePosition - targetPosition);
            return byDistance != 0 ? byDistance : first.spinePosition - second.spinePosition;
        });
        return ordered;
    }

    /** Regular rooms (4..12 x 4..10) in every remaining slot side, each at LEVEL_GEN_SPINE_SIDE_ROOM_CHANCE. */
    private void placeRegularRooms(char[][] grid, List<Slot> slots, List<LayoutRoom> rooms,
                                   Map<LayoutRoom, Integer> spinePosition) {
        for (Slot slot : slots) {
            for (int side = 0; side < 2; side++) {
                if (slot.sideTaken[side]) continue;
                if (random.nextFloat() >= LevelGenConstants.LEVEL_GEN_SPINE_SIDE_ROOM_CHANCE) continue;
                int width  = randomBetween(LevelGenConstants.LEVEL_GEN_ROOM_MIN_WIDTH, LevelGenConstants.LEVEL_GEN_ROOM_MAX_WIDTH);
                int height = randomBetween(LevelGenConstants.LEVEL_GEN_ROOM_MIN_HEIGHT, LevelGenConstants.LEVEL_GEN_ROOM_MAX_HEIGHT);
                LayoutRoom room = placeSideRoom(grid, slot, side, width, height, LevelLayoutReport.Role.REGULAR,
                        RoomBlueprints.ID_STANDARD, rooms);
                if (room == null) continue;
                slot.sideTaken[side] = true;
                spinePosition.put(room, slot.spinePosition);
            }
        }
    }

    /**
     * Seats a side room against the spine at a slot: its shell row/column touches the spine's rib, it is centred on
     * the slot, it stays inside the grid border, it overlaps neither another room (LEVEL_GEN_ROOM_MARGIN) nor any
     * carved tile, and its one socket — on the spine-facing wall, >= 2 from either corner — opens onto spine floor.
     * Returns the room (registered, socket open) or null.
     */
    private LayoutRoom placeSideRoom(char[][] grid, Slot slot, int side, int interiorWidth, int interiorHeight,
                                     LevelLayoutReport.Role role, String blueprintId, List<LayoutRoom> rooms) {
        int halfWidth = LevelGenConstants.LEVEL_GEN_SPINE_WIDTH / 2;
        SpineRun run = slot.run;
        int left, bottom, right, top;
        DoorSocket.Side facing;
        if (run.horizontal) {
            left  = slot.along - (interiorWidth + 2) / 2;
            right = left + interiorWidth + 1;
            if (side == 0) {
                bottom = run.line + halfWidth + 1;
                top    = bottom + interiorHeight + 1;
                facing = DoorSocket.Side.SOUTH;
            } else {
                top    = run.line - halfWidth - 1;
                bottom = top - interiorHeight - 1;
                facing = DoorSocket.Side.NORTH;
            }
        } else {
            bottom = slot.along - (interiorHeight + 2) / 2;
            top    = bottom + interiorHeight + 1;
            if (side == 0) {
                left   = run.line + halfWidth + 1;
                right  = left + interiorWidth + 1;
                facing = DoorSocket.Side.WEST;
            } else {
                right  = run.line - halfWidth - 1;
                left   = right - interiorWidth - 1;
                facing = DoorSocket.Side.EAST;
            }
        }
        if (left < 1 || bottom < 1 || right > grid[0].length - 2 || top > grid.length - 2) return null;
        LayoutRoom room = new LayoutRoom(left, bottom, right, top, role, blueprintId);
        int margin = LevelGenConstants.LEVEL_GEN_ROOM_MARGIN;
        for (LayoutRoom other : rooms) {
            if (left < other.rightColumn + margin && right > other.leftColumn - margin
                    && bottom < other.topRow + margin && top > other.bottomRow - margin) return null;
        }
        for (int tileRow = bottom; tileRow <= top; tileRow++) {
            for (int tileColumn = left; tileColumn <= right; tileColumn++) {
                if (!Level.isWall(grid[tileRow][tileColumn])) return null;
            }
        }
        // The socket: on the spine-facing wall, nearest the slot, whose outer tile is spine floor.
        List<int[]> candidates = new ArrayList<>();
        if (facing.isHorizontalWall()) {
            int socketRow = facing == DoorSocket.Side.SOUTH ? bottom : top;
            for (int tileColumn = left + 2; tileColumn <= right - 2; tileColumn++) candidates.add(new int[]{ tileColumn, socketRow });
        } else {
            int socketColumn = facing == DoorSocket.Side.WEST ? left : right;
            for (int tileRow = bottom + 2; tileRow <= top - 2; tileRow++) candidates.add(new int[]{ socketColumn, tileRow });
        }
        candidates.sort((first, second) -> {
            int firstAlong  = run.horizontal ? first[0] : first[1];
            int secondAlong = run.horizontal ? second[0] : second[1];
            int byDistance = Math.abs(firstAlong - slot.along) - Math.abs(secondAlong - slot.along);
            return byDistance != 0 ? byDistance : firstAlong - secondAlong;
        });
        for (int[] candidate : candidates) {
            int outerColumn = candidate[0] + facing.stepColumn;
            int outerRow    = candidate[1] + facing.stepRow;
            if (!LayoutSupport.isOpen(grid, outerColumn, outerRow)) continue;
            if (!DoorwayRules.canPlaceSocket(room, candidate[0], candidate[1], grid[0].length, grid.length)) continue;
            DoorwayRules.openSocket(grid, room, candidate[0], candidate[1]);
            rooms.add(room);
            return room;
        }
        return null;
    }

    /**
     * The crossroads plaza: a square around a spine tile, carved only where it stays >= 1 tile from every room (R9),
     * framed by four corner columns (placed later by the hall pass rules). Returns the column slots.
     */
    private List<int[]> carvePlaza(char[][] grid, int[] centre, List<LayoutRoom> rooms) {
        boolean[][] nearRooms = DoorwayRules.nearRoomMask(rooms, grid[0].length, grid.length, 1);
        int radius = LevelGenConstants.LEVEL_GEN_SPINE_PLAZA_RADIUS;
        for (int differenceRow = -radius; differenceRow <= radius; differenceRow++) {
            for (int differenceColumn = -radius; differenceColumn <= radius; differenceColumn++) {
                int tileColumn = centre[0] + differenceColumn;
                int tileRow    = centre[1] + differenceRow;
                if (tileColumn <= 0 || tileRow <= 0 || tileColumn >= grid[0].length - 1 || tileRow >= grid.length - 1) continue;
                if (nearRooms[tileRow][tileColumn]) continue;
                char cell = grid[tileRow][tileColumn];
                if (Level.isWall(cell) || cell == 'l') grid[tileRow][tileColumn] = ' ';
            }
        }
        int inset = Math.max(1, radius - 1);
        List<int[]> columns = new ArrayList<>();
        for (int[] corner : new int[][]{ { -inset, -inset }, { inset, -inset }, { -inset, inset }, { inset, inset } }) {
            columns.add(new int[]{ centre[0] + corner[0], centre[1] + corner[1] });
        }
        return columns;
    }

    // -------------------------------------------------------------------------
    // Stairs
    // -------------------------------------------------------------------------

    /** Exactly one exit, in the room farthest down the spine that can take it (the entrance last of all). */
    private void stampStairsDown(char[][] grid, List<LayoutRoom> rooms, Map<LayoutRoom, Integer> spinePosition) {
        List<LayoutRoom> ordered = new ArrayList<>(rooms);
        ordered.sort((first, second) -> {
            if (first.role == LevelLayoutReport.Role.ENTRANCE) return 1;
            if (second.role == LevelLayoutReport.Role.ENTRANCE) return -1;
            return spinePosition.getOrDefault(second, 0) - spinePosition.getOrDefault(first, 0);
        });
        LayoutRoom entrance = rooms.get(0);
        boolean[][] reachable = LayoutSupport.reachableFrom(grid, entrance.centerColumn(), entrance.centerRow());
        for (LayoutRoom room : ordered) {
            if (stampInRoom(grid, room, reachable)) return;
        }
    }

    private boolean stampInRoom(char[][] grid, LayoutRoom room, boolean[][] reachable) {
        if (stampAt(grid, room.centerColumn(), room.centerRow(), reachable)) return true;
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (stampAt(grid, tileColumn, tileRow, reachable)) return true;
            }
        }
        return false;
    }

    private static boolean stampAt(char[][] grid, int tileColumn, int tileRow, boolean[][] reachable) {
        if (!reachable[tileRow][tileColumn] || !LayoutSupport.isPlainFloor(grid[tileRow][tileColumn])) return false;
        grid[tileRow][tileColumn] = RenderConstants.STAIRS_DOWN_CHAR;
        return true;
    }

    // -------------------------------------------------------------------------
    // SupplySlotProvider
    // -------------------------------------------------------------------------

    @Override
    public int supplyRegionAt(int tileColumn, int tileRow) {
        if (supplyRegionMap == null || tileRow < 0 || tileRow >= supplyRegionMap.length
                || tileColumn < 0 || tileColumn >= supplyRegionMap[0].length) return CONNECTOR_REGION;
        return supplyRegionMap[tileRow][tileColumn];
    }

    @Override
    public boolean isLargeSupplyRegion(int regionId) {
        return supplyRooms != null && regionId >= 0 && regionId < supplyRooms.size()
                && supplyRooms.get(regionId).interiorArea() >= LevelGenConstants.LEVEL_GEN_LARGE_ROOM_INTERIOR_AREA;
    }

    @Override
    public boolean isKeepClearTile(int tileColumn, int tileRow) {
        return dresser != null && dresser.isKeepClear(tileColumn, tileRow);
    }

    // -------------------------------------------------------------------------

    private Level buildFallbackLevel() {
        char[][] grid    = new char[LevelGenConstants.LEVEL_GEN_GRID_HEIGHT][LevelGenConstants.LEVEL_GEN_GRID_WIDTH];
        int centerColumn = LevelGenConstants.LEVEL_GEN_GRID_WIDTH / 2;
        int centerRow    = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT / 2;
        for (char[] row : grid) java.util.Arrays.fill(row, 'x');
        for (int tileRow = centerRow - 2; tileRow <= centerRow + 2; tileRow++) {
            for (int tileColumn = centerColumn - 4; tileColumn <= centerColumn + 4; tileColumn++) grid[tileRow][tileColumn] = ' ';
        }
        grid[centerRow][centerColumn] = 'p';
        supplyRooms     = null;
        supplyRegionMap = null;
        dresser         = null;
        FloorPopulator.Result populated = FloorPopulator.populate(
                ge.tbegvadze.toon3d.route.GeneratorId.LINEAR_CORRIDOR.stableId(), grid, this, new ArrayList<>(),
                new FloorPopulator.EncounterFacts(-1, 0f, 0f, 0, 0), config, dungeonDepth, seed);
        return populated.attachTo(new Level(grid, populated.spawnPoints, populated.weaponSpawnPoints,
                LevelFinisher.request(grid, seed).config(config).basePalette(LevelPalettes.legacy()).finish()));
    }

    private static boolean isEnemySpawnEligible(char[][] grid, int tileColumn, int tileRow) {
        char cell = grid[tileRow][tileColumn];
        if (LayoutSupport.isPlainFloor(cell)) return true;
        return cell == '?' || cell == '-' || cell == ']' || cell == '.' || cell == 'O' || cell == 'm';
    }

    private int randomBetween(int minimum, int maximum) {
        if (maximum <= minimum) return minimum;
        return minimum + random.nextInt(maximum - minimum + 1);
    }

    private float randomFloat(float minimum, float maximum) {
        return minimum + random.nextFloat() * (maximum - minimum);
    }
}
