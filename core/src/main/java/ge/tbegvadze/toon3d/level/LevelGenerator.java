package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.tileset.FloorSymbolPins;
import ge.tbegvadze.toon3d.tileset.LevelPalette;
import ge.tbegvadze.toon3d.tileset.LevelPalettes;
import ge.tbegvadze.toon3d.tileset.RoomSymbolDemand;
import ge.tbegvadze.toon3d.tileset.SymbolAllocationRequest;
import ge.tbegvadze.toon3d.tileset.SymbolAllocator;
import ge.tbegvadze.toon3d.tileset.SymbolBudget;
import ge.tbegvadze.toon3d.tileset.TilesetRegistries;
import ge.tbegvadze.toon3d.util.LevelGenConstants;
import ge.tbegvadze.toon3d.util.RenderConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * ROOMS_MST — scattered rooms joined by an MST corridor network, rebuilt SPECIAL ROOMS FIRST
 * (level-generation-overhaul, G1 / pipeline A):
 * <pre>
 *  1 SPECIAL PLAN   SpecialRoomPlanner: N special types, uniform without replacement, from the floor seed (R1-R3)
 *  2 FOOTPRINT      FootprintPlanner attempts, each:
 *    a/b specials   sized from their windows by the attempt's scale, placed FIRST, spread apart (R4/R5)
 *    c   entrance   5..7 per axis, placed a short corridor hop from a special (R6); regular rooms fill the rest (R7)
 *    d   corridors  greedy MST + loops, socket-to-socket through the CorridorRouter; some MST edges become 3-wide
 *                   halls tapering to the socket (R9-R13); room shells are never cut except at sockets
 *    e   architecture  each blueprint's build(): floor lighting, rotunda, lab alcove, pillar slots
 *    f   dressing   RoomDresser: every room from its kit (R15-R22), the R20 terminal guarantee
 *    g   halls      spaced columns on 3-wide halls, sparse walkable decals (R28)
 *    h   validate   DoorwayRules.validate (R14) — a guard; construction keeps it at zero
 *    i   repair (a no-op backstop, R30), keycard gate, stairs in the deepest room
 *    j   encounter + supply + barrels through the shared FloorPopulator (unchanged contracts)
 *    k   LevelFinisher walls; the palette pins every placed kit's symbols and 'T' / 'E' (R29)
 * </pre>
 * Every draw comes from the layout's seeded stream (or the dresser's own); same seed + config -> the same floor.
 * Grid convention: (0,0) = bottom-left tile, Y-up. Headless — no LibGDX.
 */
public class LevelGenerator implements ILevelGenerator, SupplySlotProvider {

    private       Random         random;
    private final LevelGenConfig config;
    // The floor's master seed: the special plan, the dresser stream and the palette derive from it.
    private final long           seed;
    private int dungeonDepth = 1;

    // E6 footprint (balance-overhaul order 2): rooms are placed inside a centred window of sqrt(scale) of the grid.
    private float footprintScale = 1f;
    private int   footprintTarget;

    // The floor's planned specials (fixed before any footprint attempt) and the last-resort flag (R5).
    private List<RoomBlueprint> plannedSpecials = Collections.emptyList();
    private boolean             lastResort;

    // Supply regions and the keep-clear model, from the finished layout (SupplySlotProvider).
    private int[][]          supplyRegionMap;
    private List<LayoutRoom> supplyRooms;
    private RoomDresser      dresser;

    /** One MST edge: the two rooms and the corridor between their sockets' outer tiles. */
    private static final class Edge {
        final LayoutRoom  from;
        final LayoutRoom  to;
        final List<int[]> path;

        Edge(LayoutRoom from, LayoutRoom to, List<int[]> path) {
            this.from = from;
            this.to   = to;
            this.path = path;
        }
    }

    public LevelGenerator(long seed) {
        this(seed, new LevelGenConfig());
    }

    public LevelGenerator(long seed, LevelGenConfig config) {
        this.random = new Random(seed);
        this.config = config;
        this.seed   = seed;
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
     * Plans the floor's specials once, then builds to its E6 footprint target: {@link FootprintPlanner} rebuilds
     * at a corrected window until the walkable footprint lands within tolerance. An attempt that cannot place
     * every planned special is discarded; when every attempt was, the floor is built at window minimums and the
     * full window (R5).
     */
    @Override
    public Level generate() {
        RoomBlueprints.bootstrap();
        int target = FootprintPlanner.targetWalkableTiles(config, dungeonDepth,
                LevelGenConstants.FOOTPRINT_NATURAL_WALKABLE_ROOMS);
        plannedSpecials = SpecialRoomPlanner.plan(seed,
                target > 0 ? target : Math.round(LevelGenConstants.FOOTPRINT_NATURAL_WALKABLE_ROOMS));
        Level level = FootprintPlanner.buildToTarget(config, dungeonDepth,
                LevelGenConstants.FOOTPRINT_NATURAL_WALKABLE_ROOMS, seed, (scale, attemptSeed, attemptTarget) -> {
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

        int[] window = layoutWindow();
        List<LayoutRoom> specials = placeSpecials(window);
        if (specials == null) return null;
        LayoutRoom entrance = placeEntrance(window, specials);
        if (entrance == null) return null;
        List<LayoutRoom> rooms = new ArrayList<>();
        rooms.add(entrance);
        rooms.addAll(specials);
        fillRegularRooms(window, rooms);

        // d — corridors, socket to socket.
        LayoutSupport.RouteMasks masks = new LayoutSupport.RouteMasks(rooms, gridWidth, gridHeight);
        List<Edge> mstEdges = connectMinimumSpanningTree(grid, rooms, masks);
        if (mstEdges == null) return null;
        addLoopCorridors(grid, rooms, masks);
        List<List<int[]>> wideHallSpines = config.enableWideHallways
                ? widenHalls(grid, mstEdges, masks) : new ArrayList<>();
        LayoutSupport.carveInteriors(grid, rooms);
        assignPrimarySockets(mstEdges);
        LayoutSupport.hangDoors(grid, rooms, random);

        // e — architecture; then the player start.
        RoomBlueprintRegistry registry = RoomBlueprints.rooms();
        for (LayoutRoom room : rooms) {
            registry.get(room.blueprintId).build(new RoomBuildContext(grid, room, random, config, rooms));
        }
        grid[entrance.centerRow()][entrance.centerColumn()] = 'p';

        // f — dressing (the entrance stays bare, R22); g — halls; h — the doorway guard.
        dresser = new RoomDresser(grid, rooms, seed);
        dressRooms(rooms, registry);
        LayoutSupport.dressHalls(grid, rooms, wideHallSpines, dresser, random);
        DoorwayRules.Validation validation = DoorwayRules.validate(grid, rooms);

        // i — connectivity backstop, keycard gate, stairs.
        int repairCarves = LayoutSupport.repairConnectivity(grid, rooms, entrance.centerColumn(), entrance.centerRow());
        int[] roomDepths = computeRoomDepths(rooms, mstEdges);
        boolean[] gatedRooms = config.enableLockAndKey ? placeLockAndKeyGate(grid, rooms, roomDepths) : null;
        stampStairsDown(grid, rooms, roomDepths, gatedRooms);

        // j — ENCOUNTER (balance-overhaul order 2, E1-E5) and SUPPLY (S1) on the finished layout.
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
                ge.tbegvadze.toon3d.route.GeneratorId.ROOMS_MST.stableId(), grid, this, placement.spawnPoints,
                new FloorPopulator.EncounterFacts(placement.anchorSpawnIndex, encounter.spentThreatPoints(),
                        encounter.floorBudget(), encounter.bodyTarget(), footprintTarget),
                config, dungeonDepth, seed);

        // k — walls and the pinned palette.
        Level level = populated.attachTo(new Level(grid, populated.spawnPoints, populated.weaponSpawnPoints,
                finishWalls(grid, rooms, allocatePalette(rooms))));
        level.attachLayoutReport(LayoutSupport.report(rooms, SpecialRoomPlanner.ids(plannedSpecials), repairCarves,
                validation));
        return level;
    }

    /** {windowLeft, windowBottom, windowWidth, windowHeight}: a centred window of sqrt(scale) per axis (E6). */
    private int[] layoutWindow() {
        float axisScale  = (float) Math.sqrt(footprintScale);
        int windowWidth  = Math.min(LevelGenConstants.LEVEL_GEN_GRID_WIDTH, Math.max(
                LevelGenConstants.FOOTPRINT_MIN_WINDOW_WIDTH, Math.round(LevelGenConstants.LEVEL_GEN_GRID_WIDTH * axisScale)));
        int windowHeight = Math.min(LevelGenConstants.LEVEL_GEN_GRID_HEIGHT, Math.max(
                LevelGenConstants.FOOTPRINT_MIN_WINDOW_HEIGHT, Math.round(LevelGenConstants.LEVEL_GEN_GRID_HEIGHT * axisScale)));
        return new int[]{ (LevelGenConstants.LEVEL_GEN_GRID_WIDTH - windowWidth) / 2,
                          (LevelGenConstants.LEVEL_GEN_GRID_HEIGHT - windowHeight) / 2, windowWidth, windowHeight };
    }

    // -------------------------------------------------------------------------
    // Room placement (R4-R7)
    // -------------------------------------------------------------------------

    /**
     * R5: every planned special, in plan order, by best-candidate sampling — up to
     * LEVEL_GEN_SPECIAL_PLACEMENT_CANDIDATES legal positions, keeping the one farthest from the specials already
     * placed. A special that will not fit is retried at its window minimum; still nothing -> null (the attempt is
     * discarded; a special is never swapped or dropped).
     */
    private List<LayoutRoom> placeSpecials(int[] window) {
        List<LayoutRoom> placed = new ArrayList<>();
        for (RoomBlueprint blueprint : plannedSpecials) {
            int[] size = SpecialRoomPlanner.size(blueprint, random, footprintScale, lastResort);
            LayoutRoom room = bestPosition(window, size, placed, blueprint.id());
            if (room == null && !lastResort) {
                room = bestPosition(window, SpecialRoomPlanner.size(blueprint, random, footprintScale, true), placed,
                        blueprint.id());
            }
            if (room == null) return null;
            placed.add(room);
        }
        return placed;
    }

    private LayoutRoom bestPosition(int[] window, int[] size, List<LayoutRoom> placed, String blueprintId) {
        LayoutRoom best = null;
        int bestScore = -1;
        int found = 0;
        for (int attempt = 0; attempt < LevelGenConstants.LEVEL_GEN_PLACEMENT_TRIES
                && found < LevelGenConstants.LEVEL_GEN_SPECIAL_PLACEMENT_CANDIDATES; attempt++) {
            LayoutRoom candidate = randomPosition(window, size[0], size[1], blueprintId,
                    LevelLayoutReport.Role.SPECIAL, placed);
            if (candidate == null) continue;
            found++;
            int score = Integer.MAX_VALUE;
            for (LayoutRoom other : placed) score = Math.min(score, centreDistance(candidate, other));
            if (best == null || score > bestScore) {
                best      = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    /**
     * R6: the entrance, 5..7 per axis, at the best of LEVEL_GEN_SPECIAL_PLACEMENT_CANDIDATES legal positions —
     * the one closest to a special's shell, so the start is a short corridor hop from a landmark and the floor's
     * first contact stays near.
     */
    private LayoutRoom placeEntrance(int[] window, List<LayoutRoom> specials) {
        int width  = randomBetween(LevelGenConstants.LEVEL_GEN_ENTRANCE_MIN_DIM, LevelGenConstants.LEVEL_GEN_ENTRANCE_MAX_DIM);
        int height = randomBetween(LevelGenConstants.LEVEL_GEN_ENTRANCE_MIN_DIM, LevelGenConstants.LEVEL_GEN_ENTRANCE_MAX_DIM);
        LayoutRoom best = null;
        int bestGap = Integer.MAX_VALUE;
        int found = 0;
        for (int attempt = 0; attempt < LevelGenConstants.LEVEL_GEN_PLACEMENT_TRIES
                && found < LevelGenConstants.LEVEL_GEN_SPECIAL_PLACEMENT_CANDIDATES; attempt++) {
            LayoutRoom candidate = randomPosition(window, width, height, RoomBlueprints.ID_ENTRANCE,
                    LevelLayoutReport.Role.ENTRANCE, specials);
            if (candidate == null) continue;
            found++;
            int gap = Integer.MAX_VALUE;
            for (LayoutRoom special : specials) gap = Math.min(gap, shellGap(candidate, special));
            if (best == null || gap < bestGap) {
                best    = candidate;
                bestGap = gap;
            }
        }
        return best;
    }

    /** R7: regular rooms (4..12 x 4..10) fill the window up to the footprint's room target. */
    private void fillRegularRooms(int[] window, List<LayoutRoom> rooms) {
        int targetRooms = Math.max(LevelGenConstants.FOOTPRINT_MIN_ROOMS,
                Math.round(LevelGenConstants.LEVEL_GEN_TARGET_ROOMS * footprintScale));
        for (int attempt = 0; attempt < LevelGenConstants.LEVEL_GEN_PLACEMENT_TRIES && rooms.size() < targetRooms;
             attempt++) {
            int width  = randomBetween(LevelGenConstants.LEVEL_GEN_ROOM_MIN_WIDTH, LevelGenConstants.LEVEL_GEN_ROOM_MAX_WIDTH);
            int height = randomBetween(LevelGenConstants.LEVEL_GEN_ROOM_MIN_HEIGHT, LevelGenConstants.LEVEL_GEN_ROOM_MAX_HEIGHT);
            LayoutRoom room = randomPosition(window, width, height, RoomBlueprints.ID_STANDARD,
                    LevelLayoutReport.Role.REGULAR, rooms);
            if (room != null) rooms.add(room);
        }
    }

    /** A random position for an interior of the given size inside the window, or null when it collides. */
    private LayoutRoom randomPosition(int[] window, int interiorWidth, int interiorHeight, String blueprintId,
                                      LevelLayoutReport.Role role, List<LayoutRoom> existing) {
        int totalWidth  = interiorWidth + 2;
        int totalHeight = interiorHeight + 2;
        int maxLeft   = window[2] - totalWidth - 1;
        int maxBottom = window[3] - totalHeight - 1;
        if (maxLeft < 1 || maxBottom < 1) return null;
        int left   = window[0] + 1 + random.nextInt(maxLeft);
        int bottom = window[1] + 1 + random.nextInt(maxBottom);
        LayoutRoom candidate = new LayoutRoom(left, bottom, left + totalWidth - 1, bottom + totalHeight - 1,
                role, blueprintId);
        for (LayoutRoom other : existing) if (overlaps(candidate, other)) return null;
        return candidate;
    }

    private static boolean overlaps(LayoutRoom first, LayoutRoom second) {
        int margin = LevelGenConstants.LEVEL_GEN_ROOM_MARGIN;
        return first.leftColumn < second.rightColumn + margin && first.rightColumn > second.leftColumn - margin
            && first.bottomRow < second.topRow + margin && first.topRow > second.bottomRow - margin;
    }

    private static int centreDistance(LayoutRoom first, LayoutRoom second) {
        return Math.abs(first.centerColumn() - second.centerColumn()) + Math.abs(first.centerRow() - second.centerRow());
    }

    /** Tiles of rock between two shells (Chebyshev gap between their rectangles). */
    private static int shellGap(LayoutRoom first, LayoutRoom second) {
        int columnGap = Math.max(0, Math.max(second.leftColumn - first.rightColumn, first.leftColumn - second.rightColumn) - 1);
        int rowGap    = Math.max(0, Math.max(second.bottomRow - first.topRow, first.bottomRow - second.topRow) - 1);
        return Math.max(columnGap, rowGap);
    }

    // -------------------------------------------------------------------------
    // Corridors (R11-R13)
    // -------------------------------------------------------------------------

    /**
     * Greedy MST from the entrance: each step joins the nearest unconnected room to the connected set, trying the
     * next-nearest pair whenever no legal socket or route exists. A REGULAR room nothing can reach is dropped; a
     * special left unconnected discards the attempt (null).
     */
    private List<Edge> connectMinimumSpanningTree(char[][] grid, List<LayoutRoom> rooms, LayoutSupport.RouteMasks masks) {
        List<LayoutRoom> connected   = new ArrayList<>();
        List<LayoutRoom> unconnected = new ArrayList<>(rooms);
        connected.add(unconnected.remove(0));
        List<Edge> edges = new ArrayList<>();
        while (!unconnected.isEmpty()) {
            List<LayoutRoom[]> pairs = new ArrayList<>();
            for (LayoutRoom from : connected) for (LayoutRoom to : unconnected) pairs.add(new LayoutRoom[]{ from, to });
            pairs.sort((first, second) -> centreDistance(first[0], first[1]) - centreDistance(second[0], second[1]));
            Edge joined = null;
            for (LayoutRoom[] pair : pairs) {
                List<int[]> path = LayoutSupport.connect(grid, pair[0], pair[1], masks, random.nextLong());
                if (path == null) continue;
                joined = new Edge(pair[0], pair[1], path);
                break;
            }
            if (joined == null) break;
            edges.add(joined);
            connected.add(joined.to);
            unconnected.remove(joined.to);
        }
        for (LayoutRoom stranded : unconnected) {
            if (stranded.role != LevelLayoutReport.Role.REGULAR) return null;
            rooms.remove(stranded);
        }
        return edges;
    }

    /** rooms/3 extra loop corridors between rooms within LEVEL_GEN_LOOP_MAX_DISTANCE (failures are skipped). */
    private void addLoopCorridors(char[][] grid, List<LayoutRoom> rooms, LayoutSupport.RouteMasks masks) {
        int loopTarget = Math.max(1, rooms.size() / 3);
        int loopsAdded = 0;
        for (int attempt = 0; attempt < loopTarget * 5 && loopsAdded < loopTarget; attempt++) {
            LayoutRoom first  = rooms.get(random.nextInt(rooms.size()));
            LayoutRoom second = rooms.get(random.nextInt(rooms.size()));
            if (first == second || centreDistance(first, second) > LevelGenConstants.LEVEL_GEN_LOOP_MAX_DISTANCE) continue;
            if (LayoutSupport.connect(grid, first, second, masks, random.nextLong()) != null) loopsAdded++;
        }
    }

    /**
     * R13: LEVEL_GEN_WIDE_HALLWAY_COUNT MST edges become 3-wide halls (edges touching the entrance or a special
     * first), narrowing to one tile before each socket. Returns each hall's spine for its columns.
     */
    private List<List<int[]>> widenHalls(char[][] grid, List<Edge> edges, LayoutSupport.RouteMasks masks) {
        List<Edge> ordered = new ArrayList<>();
        List<Edge> others  = new ArrayList<>();
        for (Edge edge : edges) {
            boolean landmark = edge.from.role != LevelLayoutReport.Role.REGULAR || edge.to.role != LevelLayoutReport.Role.REGULAR;
            if (landmark) ordered.add(edge);
            else others.add(edge);
        }
        ordered.addAll(others);
        List<List<int[]>> spines = new ArrayList<>();
        for (int index = 0; index < ordered.size() && spines.size() < LevelGenConstants.LEVEL_GEN_WIDE_HALLWAY_COUNT; index++) {
            List<int[]> spine = CorridorRouter.widen(grid, ordered.get(index).path, masks.nearRooms,
                    LevelGenConstants.LEVEL_GEN_WIDE_HALL_TAPER_TILES);
            if (!spine.isEmpty()) spines.add(spine);
        }
        return spines;
    }

    /** Each room's PRIMARY socket is the one on its MST corridor toward the entrance (the BACK_WALL faces it). */
    private static void assignPrimarySockets(List<Edge> edges) {
        for (Edge edge : edges) {
            int[] end = edge.path.get(edge.path.size() - 1);
            for (DoorSocket socket : edge.to.sockets()) {
                if (socket.outerColumn() == end[0] && socket.outerRow() == end[1]) edge.to.setPrimarySocket(socket);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Dressing (R16-R23)
    // -------------------------------------------------------------------------

    private void dressRooms(List<LayoutRoom> rooms, RoomBlueprintRegistry registry) {
        RoomBlueprint standard = registry.get(RoomBlueprints.ID_STANDARD);
        for (LayoutRoom room : rooms) {
            if (room.role == LevelLayoutReport.Role.ENTRANCE) continue;
            if (room.role == LevelLayoutReport.Role.REGULAR) {
                dresser.dress(room, dresser.pickRegularKit(room, standard.regularKits(), config.preferredRegularKit), null);
            } else {
                RoomBlueprint blueprint = registry.get(room.blueprintId);
                dresser.dress(room, blueprint.kit(), blueprint.relaxedKit());
            }
        }
        guaranteeTerminal(rooms, registry);
    }

    /**
     * R20: every combat floor carries a usable terminal (the story's LOG_FOUND beats fire off them). If no kit
     * produced one, a regular room is re-dressed as an OFFICE; with none that takes, the OFFICE terminal row is
     * added to the first terminal-allowed special.
     */
    private void guaranteeTerminal(List<LayoutRoom> rooms, RoomBlueprintRegistry registry) {
        if (dresser.hasUsableTerminal()) return;
        for (LayoutRoom room : rooms) {
            if (room.role != LevelLayoutReport.Role.REGULAR) continue;
            dresser.redress(room, RoomBlueprints.officeKit());
            if (dresser.hasUsableTerminal()) return;
        }
        for (LayoutRoom room : rooms) {
            if (room.role != LevelLayoutReport.Role.SPECIAL) continue;
            PropKit kit = registry.get(room.blueprintId).kit();
            if (kit == null || !kit.placesTerminals()) continue;
            dresser.dressExtraRow(room, RoomBlueprints.officeKit().rows().get(0));
            if (dresser.hasUsableTerminal()) return;
        }
    }

    // -------------------------------------------------------------------------
    // Room depth, keycard gate, stairs (algorithms unchanged; rooms are LayoutRooms now)
    // -------------------------------------------------------------------------

    /** Each room's hop distance from the entrance over the MST (room 0). */
    private static int[] computeRoomDepths(List<LayoutRoom> rooms, List<Edge> edges) {
        int roomCount = rooms.size();
        int[] depth = new int[roomCount];
        java.util.Arrays.fill(depth, -1);
        List<List<Integer>> adjacency = new ArrayList<>();
        for (int roomIndex = 0; roomIndex < roomCount; roomIndex++) adjacency.add(new ArrayList<>());
        for (Edge edge : edges) {
            int first = rooms.indexOf(edge.from), second = rooms.indexOf(edge.to);
            if (first < 0 || second < 0) continue;
            adjacency.get(first).add(second);
            adjacency.get(second).add(first);
        }
        int[] queue = new int[roomCount];
        int head = 0, tail = 0;
        depth[0] = 0;
        queue[tail++] = 0;
        while (head < tail) {
            int current = queue[head++];
            for (int neighbour : adjacency.get(current)) {
                if (depth[neighbour] != -1) continue;
                depth[neighbour] = depth[current] + 1;
                queue[tail++] = neighbour;
            }
        }
        for (int roomIndex = 0; roomIndex < roomCount; roomIndex++) if (depth[roomIndex] == -1) depth[roomIndex] = 0;
        return depth;
    }

    /**
     * Promotes one plain door that is a true single-door cut into a keycard-locked door and places the key in a
     * room still reachable without it (the deepest such region preferred, then the tightest vault). Returns the
     * per-room gated mask, or null when no gate was placed. Every gate is a real doorway now (a flanked socket).
     */
    private boolean[] placeLockAndKeyGate(char[][] grid, List<LayoutRoom> rooms, int[] roomDepths) {
        LayoutRoom entrance = rooms.get(0);
        int roomCount = rooms.size();
        int maxGated  = (int) Math.floor((roomCount - 1) * LevelGenConstants.LEVEL_GEN_GATE_MAX_GATED_FRACTION);
        int bestGateColumn = -1, bestGateRow = -1, bestScore = -1, bestGatedCount = Integer.MAX_VALUE;
        boolean[][] bestReachable = null;
        for (int tileRow = 1; tileRow < grid.length - 1; tileRow++) {
            for (int tileColumn = 1; tileColumn < grid[0].length - 1; tileColumn++) {
                if (grid[tileRow][tileColumn] != 'd') continue;
                grid[tileRow][tileColumn] = 'x';
                boolean[][] reachable = LayoutSupport.reachableFrom(grid, entrance.centerColumn(), entrance.centerRow());
                grid[tileRow][tileColumn] = 'd';
                int gatedCount = 0, reachableRooms = 0, deepestGated = -1;
                for (int roomIndex = 1; roomIndex < roomCount; roomIndex++) {
                    if (LayoutSupport.isRoomReachable(reachable, grid, rooms.get(roomIndex))) {
                        reachableRooms++;
                    } else {
                        gatedCount++;
                        deepestGated = Math.max(deepestGated, roomDepths[roomIndex]);
                    }
                }
                if (gatedCount == 0 || reachableRooms == 0) continue;
                if (maxGated >= 1 && gatedCount > maxGated) continue;
                if (deepestGated > bestScore || (deepestGated == bestScore && gatedCount < bestGatedCount)) {
                    bestScore      = deepestGated;
                    bestGatedCount = gatedCount;
                    bestGateColumn = tileColumn;
                    bestGateRow    = tileRow;
                    bestReachable  = reachable;
                }
            }
        }
        if (bestReachable == null) return null;
        KeycardColor color = KeycardColor.values()[random.nextInt(KeycardColor.values().length)];
        grid[bestGateRow][bestGateColumn] = lockedDoorChar(color);
        boolean[] gatedRooms = new boolean[roomCount];
        int keyRoomIndex = -1, keyRoomDepth = -1;
        for (int roomIndex = 1; roomIndex < roomCount; roomIndex++) {
            if (LayoutSupport.isRoomReachable(bestReachable, grid, rooms.get(roomIndex))) {
                if (roomDepths[roomIndex] > keyRoomDepth) {
                    keyRoomDepth = roomDepths[roomIndex];
                    keyRoomIndex = roomIndex;
                }
            } else {
                gatedRooms[roomIndex] = true;
            }
        }
        if (keyRoomIndex < 0 || !placeKeycard(grid, rooms.get(keyRoomIndex), keycardChar(color), bestReachable)) {
            grid[bestGateRow][bestGateColumn] = 'd';
            return null;
        }
        return gatedRooms;
    }

    private boolean placeKeycard(char[][] grid, LayoutRoom room, char keycard, boolean[][] reachable) {
        for (int attempt = 0; attempt < 30; attempt++) {
            int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
            int tileRow    = room.bottomRow + 1 + random.nextInt(room.interiorHeight());
            if (!reachable[tileRow][tileColumn] || !LayoutSupport.isPlainFloor(grid[tileRow][tileColumn])) continue;
            if (SupplySlotSurvey.isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
            grid[tileRow][tileColumn] = keycard;
            return true;
        }
        return false;
    }

    private static char lockedDoorChar(KeycardColor color) {
        return color == KeycardColor.RED ? 'R' : color == KeycardColor.YELLOW ? 'Y' : 'B';
    }

    private static char keycardChar(KeycardColor color) {
        return color == KeycardColor.RED ? 'r' : color == KeycardColor.YELLOW ? 'y' : 'b';
    }

    /**
     * Exactly one exit, in the spatially deepest room (inside the gated region when a gate exists); ties go to
     * the biggest room. Fallbacks: any non-entrance room, then the entrance.
     */
    private void stampStairsDown(char[][] grid, List<LayoutRoom> rooms, int[] roomDepths, boolean[] gatedRooms) {
        if (gatedRooms != null && stampInDeepestRoom(grid, rooms, roomDepths, gatedRooms)) return;
        if (stampInDeepestRoom(grid, rooms, roomDepths, null)) return;
        for (int roomIndex = rooms.size() - 1; roomIndex >= 1; roomIndex--) {
            if (tryStampInRoom(grid, rooms.get(roomIndex))) return;
        }
        tryStampInRoom(grid, rooms.get(0));
    }

    private boolean stampInDeepestRoom(char[][] grid, List<LayoutRoom> rooms, int[] roomDepths, boolean[] mask) {
        List<Integer> candidates = new ArrayList<>();
        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            if (mask == null || mask[roomIndex]) candidates.add(roomIndex);
        }
        candidates.sort((first, second) -> roomDepths[second] != roomDepths[first] ? roomDepths[second] - roomDepths[first]
                : rooms.get(second).interiorArea() - rooms.get(first).interiorArea());
        for (int roomIndex : candidates) if (tryStampInRoom(grid, rooms.get(roomIndex))) return true;
        return false;
    }

    private boolean tryStampInRoom(char[][] grid, LayoutRoom room) {
        if (stampStairsAt(grid, room.centerColumn(), room.centerRow())) return true;
        for (int attempt = 0; attempt < 12; attempt++) {
            if (room.interiorWidth() <= 0 || room.interiorHeight() <= 0) break;
            int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
            int tileRow    = room.bottomRow + 1 + random.nextInt(room.interiorHeight());
            if (stampStairsAt(grid, tileColumn, tileRow)) return true;
        }
        return false;
    }

    private static boolean stampStairsAt(char[][] grid, int tileColumn, int tileRow) {
        if (!LayoutSupport.isPlainFloor(grid[tileRow][tileColumn])) return false;
        grid[tileRow][tileColumn] = RenderConstants.STAIRS_DOWN_CHAR;
        int[][] steps = { { 0, 1 }, { 0, -1 }, { 1, 0 }, { -1, 0 } };
        for (int[] step : steps) {
            int column = tileColumn + step[0], row = tileRow + step[1];
            if (LayoutSupport.isPlainFloor(grid[row][column])) grid[row][column] = ' ';
        }
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
    // Palette, walls, fallback
    // -------------------------------------------------------------------------

    /**
     * The floor's palette (R29): every placed room's blueprint demand, every placed kit's demand, and the 'T' / 'E'
     * pins every generated floor carries. The allocator is a pure function of (seed, demands).
     */
    private LevelPalette allocatePalette(List<LayoutRoom> rooms) {
        TilesetRegistries.bootstrap();
        SymbolAllocationRequest.Builder request = SymbolAllocationRequest
                .builder(seed, SymbolBudget.standard(), TilesetRegistries.sprites()).depth(dungeonDepth);
        RoomBlueprintRegistry registry = RoomBlueprints.rooms();
        for (LayoutRoom room : rooms) {
            RoomSymbolDemand demand = registry.get(room.blueprintId).symbolDemand();
            if (!demand.requiredSprites().isEmpty()) request.placedRoom(demand);
        }
        FloorSymbolPins.pin(request, dresser.kitDemand());
        return SymbolAllocator.allocate(request.build());
    }

    /** Region wall sets (generated-wall-art order 2): each room's interior and its blueprint's wall group. */
    private LevelPalette finishWalls(char[][] grid, List<LayoutRoom> rooms, LevelPalette propsAndDecals) {
        LevelFinisher.Request request = LevelFinisher.request(grid, seed).config(config).basePalette(propsAndDecals);
        RoomBlueprintRegistry registry = RoomBlueprints.rooms();
        for (LayoutRoom room : rooms) {
            request.room(room.leftColumn + 1, room.bottomRow + 1, room.rightColumn - 1, room.topRow - 1,
                         registry.get(room.blueprintId).wallGroup());
        }
        return request.finish();
    }

    private Level buildFallbackLevel() {
        char[][] grid = new char[LevelGenConstants.LEVEL_GEN_GRID_HEIGHT][LevelGenConstants.LEVEL_GEN_GRID_WIDTH];
        for (char[] row : grid) java.util.Arrays.fill(row, 'x');
        for (int tileRow = 20; tileRow <= 24; tileRow++) {
            for (int tileColumn = 36; tileColumn <= 43; tileColumn++) grid[tileRow][tileColumn] = ' ';
        }
        grid[22][40] = 'p';
        supplyRooms     = null;
        supplyRegionMap = null;
        dresser         = null;
        FloorPopulator.Result populated = FloorPopulator.populate(
                ge.tbegvadze.toon3d.route.GeneratorId.ROOMS_MST.stableId(), grid, this, new ArrayList<>(),
                new FloorPopulator.EncounterFacts(-1, 0f, 0f, 0, 0), config, dungeonDepth, seed);
        return populated.attachTo(new Level(grid, populated.spawnPoints, populated.weaponSpawnPoints,
                LevelFinisher.request(grid, seed).config(config).basePalette(LevelPalettes.legacy()).finish()));
    }

    // -------------------------------------------------------------------------
    // The build bridge (LINEAR_CORRIDOR's delegated rooms until its own rebuild)
    // -------------------------------------------------------------------------

    /**
     * Builds one room of ANOTHER generator through the shared engine: architecture from the blueprint, props
     * from its kit. The room's existing shell openings become its sockets. Bounds are shell bounds; the interior
     * must already be carved.
     */
    static void buildRoomForBridge(char[][] grid, int leftColumn, int bottomRow, int rightColumn, int topRow,
                                   RoomBlueprint blueprint, Random random, LevelGenConfig config, long floorSeed) {
        LayoutRoom room = new LayoutRoom(leftColumn, bottomRow, rightColumn, topRow,
                LevelLayoutReport.Role.SPECIAL, blueprint.id());
        for (int tileRow = bottomRow; tileRow <= topRow; tileRow++) {
            for (int tileColumn = leftColumn; tileColumn <= rightColumn; tileColumn++) {
                if (!room.isShellTile(tileColumn, tileRow) || Level.isWall(grid[tileRow][tileColumn])) continue;
                DoorSocket.Side side = room.sideOf(tileColumn, tileRow);
                if (side != null) room.addSocket(new DoorSocket(tileColumn, tileRow, side));
            }
        }
        List<LayoutRoom> rooms = Collections.singletonList(room);
        blueprint.build(new RoomBuildContext(grid, room, random, config, rooms));
        new RoomDresser(grid, rooms, floorSeed).dress(room, blueprint.kit(), blueprint.relaxedKit());
    }

    // -------------------------------------------------------------------------

    /**
     * Enemy spawn tiles: the four floors plus walkable decals (incl. the observatory's ring, spokes and debris);
     * never a pickup, keycard or the stairs (killEnemy stamps 'm' on the death tile).
     */
    private static boolean isEnemySpawnEligible(char[][] grid, int tileColumn, int tileRow) {
        char cell = grid[tileRow][tileColumn];
        if (LayoutSupport.isPlainFloor(cell)) return true;
        return cell == '?' || cell == '-' || cell == ']' || cell == '.' || cell == 'O' || cell == 'm';
    }

    private int randomBetween(int minimum, int maximum) {
        return minimum + random.nextInt(maximum - minimum + 1);
    }
}
