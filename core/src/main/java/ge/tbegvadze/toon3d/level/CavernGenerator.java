package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.tileset.LevelPalettes;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.LevelGenConstants;
import ge.tbegvadze.toon3d.util.RenderConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

/**
 * CAVERN — organic cellular-automata caves with walled chambers set into them, rebuilt SPECIAL ROOMS FIRST
 * (level-generation-overhaul G3 / pipeline A):
 * <pre>
 *  0-2 CAVE        noise, 5 x 4-5-rule CA passes + a birth-only pass, region audit (tiny pockets erased, isolated
 *                  regions stitched) — unchanged
 *  3   CHAMBERS    the planned specials FIRST (sized from their windows; they may carve rock), then the entrance,
 *                  then 0..LEVEL_GEN_CAVE_REGULAR_CHAMBERS_MAX regular chambers (>= LEVEL_GEN_CAVE_CHAMBER_FLOOR_OVERLAP
 *                  already cave floor). Every chamber is a walled room: its shell ring is forced to wall
 *  4   SOCKETS     1..3 per chamber on the sides nearest cave floor, each joined to it by a straight 1-wide tunnel
 *                  of at most LEVEL_GEN_CAVE_SOCKET_TUNNEL_MAX tiles; cave a forced shell cut off is rejoined by a
 *                  routed bypass tunnel that never touches a shell (R9)
 *  5   architecture, doors, biomes (floor moods + walkable decals), kit dressing, then the cave body's only two
 *      solid rules — FORMATIONS and wall-anchored DEBRIS — the doorway guard, the repair backstop, stairs,
 *      encounter + supply (shared pipeline)
 * </pre>
 * Grid convention: (0,0) = bottom-left tile, Y-up. Headless — no LibGDX.
 */
public class CavernGenerator implements ILevelGenerator, SupplySlotProvider {

    private static final int[] STEP_COLUMNS = { 0, 0, 1, -1 };
    private static final int[] STEP_ROWS    = { 1, -1, 0, 0 };

    /** A legal socket on a chamber's shell whose straight tunnel reaches cave floor. */
    private static final class SocketPlan {
        final int            tileColumn;
        final int            tileRow;
        final DoorSocket.Side side;
        final int            tunnelLength;

        SocketPlan(int tileColumn, int tileRow, DoorSocket.Side side, int tunnelLength) {
            this.tileColumn   = tileColumn;
            this.tileRow      = tileRow;
            this.side         = side;
            this.tunnelLength = tunnelLength;
        }
    }

    private       Random random;
    // Raw floor seed: the special plan, the LevelFinisher's wall stream and the dresser's streams derive from it.
    private final long   seed;
    private       int    spawnColumn;
    private       int    spawnRow;

    // Supply regions (balance-overhaul order 2, SupplySlotProvider): each chamber is one region; the open cave body
    // is cut into fixed-size POCKETS so supply spreads across the cave instead of piling into one huge region.
    private       int[][]          supplyRegionMap;
    private       List<LayoutRoom> supplyRooms;
    private       RoomDresser      dresser;

    // Per-tile biome id for the open cave body; -1 = wall, chamber, or unseeded.
    private       int[][] biomeMap;
    // Tunnel tiles carved from sockets this attempt (a later chamber may not stamp over one).
    private       boolean[][] tunnelMask;

    private int dungeonDepth = 1;

    // Route-map order-7 encounter-budget multiplier (1.0 = normal).
    private final float enemyBudgetScale;

    // E6 footprint scale (balance-overhaul order 2): the cave grows only inside a centred window of sqrt(scale) of
    // the grid on each axis; everything outside it stays rock. 1 = the whole grid. Chambers stay inside it too.
    private float footprintScale = 1f;
    private int   footprintTarget;
    private int   windowLeft, windowBottom, windowRight, windowTop;

    private List<RoomBlueprint> plannedSpecials = Collections.emptyList();
    private boolean             lastResort;

    // The floor's config (balance-overhaul order 2). Never null.
    private final LevelGenConfig config;

    public CavernGenerator(long seed) {
        this(seed, new LevelGenConfig());
    }

    /**
     * @param config the floor's generation config — this generator honours its encounter-budget scale and
     *               the order-2 supply fields; every other generation parameter defaults internally.
     */
    public CavernGenerator(long seed, LevelGenConfig config) {
        this.random           = new Random(seed);
        this.seed             = seed;
        this.config           = config != null ? config : new LevelGenConfig();
        // Exactly 0 is a legal "empty floor" request (EnemyBudgetOverride.empty); only a negative scale
        // is meaningless and falls back to the normal budget.
        this.enemyBudgetScale = this.config.enemyBudgetScale >= 0f ? this.config.enemyBudgetScale : 1f;
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
     * Plans the floor's specials once, then builds the cave to its E6 footprint target ({@link FootprintPlanner}
     * rebuilds at a corrected cave window). An attempt that cannot seat every planned special is discarded; when
     * every one was, the floor is built on the whole grid with the specials at their window minimums (R5).
     */
    @Override
    public Level generate() {
        RoomBlueprints.bootstrap();
        int target = FootprintPlanner.targetWalkableTiles(config, dungeonDepth,
                LevelGenConstants.FOOTPRINT_NATURAL_WALKABLE_CAVE);
        plannedSpecials = SpecialRoomPlanner.plan(seed,
                target > 0 ? target : Math.round(LevelGenConstants.FOOTPRINT_NATURAL_WALKABLE_CAVE));
        Level level = FootprintPlanner.buildToTarget(config, dungeonDepth,
                LevelGenConstants.FOOTPRINT_NATURAL_WALKABLE_CAVE, seed, (scale, attemptSeed, attemptTarget) -> {
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
        spawnColumn = gridWidth  / 2;
        spawnRow    = gridHeight / 2;

        // 0-2 — the cave.
        boolean[][] solid = new boolean[gridHeight][gridWidth];
        initNoise(solid);
        for (int pass = 0; pass < LevelGenConstants.LEVEL_GEN_CAVE_SMOOTH_PASSES; pass++) {
            solid = smoothPass(solid, false);
        }
        for (int pass = 0; pass < LevelGenConstants.LEVEL_GEN_CAVE_FINAL_FILL_PASSES; pass++) {
            solid = smoothPass(solid, true);
        }
        auditRegions(solid);
        char[][] grid = buildGrid(solid);
        tunnelMask = new boolean[gridHeight][gridWidth];

        // 3/4 — the specials first, then the entrance, then the regular chambers; sockets + tunnels each.
        List<LayoutRoom> rooms = new ArrayList<>();
        for (RoomBlueprint blueprint : plannedSpecials) {
            boolean placed = false;
            for (int pass = 0; pass < 2 && !placed; pass++) {
                int[] size = SpecialRoomPlanner.size(blueprint, random, footprintScale, lastResort || pass == 1);
                placed = placeChamber(grid, rooms, size[0], size[1], LevelLayoutReport.Role.SPECIAL,
                        blueprint.id(), false) != null;
            }
            if (!placed) return null;
        }
        LayoutRoom entrance = null;
        for (int pass = 0; pass < 2 && entrance == null; pass++) {
            int width  = randomBetween(LevelGenConstants.LEVEL_GEN_ENTRANCE_MIN_DIM, LevelGenConstants.LEVEL_GEN_ENTRANCE_MAX_DIM);
            int height = randomBetween(LevelGenConstants.LEVEL_GEN_ENTRANCE_MIN_DIM, LevelGenConstants.LEVEL_GEN_ENTRANCE_MAX_DIM);
            entrance = placeChamber(grid, rooms, width, height, LevelLayoutReport.Role.ENTRANCE,
                    RoomBlueprints.ID_ENTRANCE, pass == 0);
        }
        if (entrance == null) return null;
        // The entrance is room 0 (supply region 0, the first record of the report).
        rooms.remove(entrance);
        rooms.add(0, entrance);
        // R20: one regular chamber is always required — a special's kit may place a terminal but cannot promise one,
        // and the terminal guarantee re-dresses a regular chamber as an OFFICE. It is at least
        // LEVEL_GEN_CAVE_TERMINAL_CHAMBER_MIN_DIM square with ONE socket, so a door's clearance and lane never take
        // every wall, and it may carve rock when no cave-embedded spot takes it.
        int requiredRegulars = 1;
        int regularCount = Math.max(requiredRegulars, randomBetween(0, LevelGenConstants.LEVEL_GEN_CAVE_REGULAR_CHAMBERS_MAX));
        for (int regular = 0; regular < regularCount; regular++) {
            boolean required = regular < requiredRegulars;
            LayoutRoom placed = null;
            int minimum = required ? LevelGenConstants.LEVEL_GEN_CAVE_TERMINAL_CHAMBER_MIN_DIM : 0;
            for (int pass = 0; pass < (required ? 2 : 1) && placed == null; pass++) {
                int width  = Math.max(minimum, randomBetween(LevelGenConstants.LEVEL_GEN_CAVE_CHAMBER_MIN_WIDTH,
                        LevelGenConstants.LEVEL_GEN_CAVE_CHAMBER_MAX_WIDTH));
                int height = Math.max(minimum, randomBetween(LevelGenConstants.LEVEL_GEN_CAVE_CHAMBER_MIN_HEIGHT,
                        LevelGenConstants.LEVEL_GEN_CAVE_CHAMBER_MAX_HEIGHT));
                placed = placeChamber(grid, rooms, width, height, LevelLayoutReport.Role.REGULAR,
                        RoomBlueprints.ID_STANDARD, pass == 0,
                        required ? 1 : LevelGenConstants.LEVEL_GEN_CAVE_SOCKETS_MAX);
            }
            if (required && placed == null) return null;
        }
        rejoinSeveredCave(grid, rooms, entrance);
        LayoutSupport.hangDoors(grid, rooms, random);

        // 5 — architecture, the start, biomes, dressing, the cave body's two solid rules, guards.
        RoomBlueprintRegistry registry = RoomBlueprints.rooms();
        for (LayoutRoom room : rooms) {
            registry.get(room.blueprintId).build(new RoomBuildContext(grid, room, random, config, rooms));
        }
        spawnColumn = entrance.centerColumn();
        spawnRow    = entrance.centerRow();
        grid[spawnRow][spawnColumn] = 'p';
        boolean[][] footprint = DoorwayRules.roomFootprintMask(rooms, gridWidth, gridHeight);
        CaveBiome[] biomes = assignBiomes(grid, footprint);
        if (biomes.length > 0) {
            themeCaveFloor(grid, footprint, biomes);
            placeCaveDecals(grid, footprint, biomes, rooms);
        }
        dresser = new RoomDresser(grid, rooms, seed);
        LayoutSupport.dressRooms(dresser, rooms, config);
        if (biomes.length > 0) {
            placeFormations(grid, footprint, biomes, rooms);
            placeDebris(grid, footprint, biomes, rooms);
        }
        DoorwayRules.Validation validation = DoorwayRules.validate(grid, rooms);
        int repairCarves = LayoutSupport.repairConnectivity(grid, rooms, spawnColumn, spawnRow);
        stampStairsDown(grid, rooms);

        // Encounter (E1-E5) and supply (S1) through the shared pipeline, on the finished cave.
        buildSupplyRegionMap(rooms);
        ge.tbegvadze.toon3d.route.NodeSupplySpec spec = FloorPopulator.specOf(config);
        EncounterBudgetPlanner.Plan encounter =
                new EncounterBudgetPlanner(dungeonDepth, random, enemyBudgetScale, spec).plan();
        EncounterPlacer.Placement placement = EncounterPlacer.place(grid, SupplySlotSurvey.survey(grid, this),
                encounter, this::isCaveSpawnCell, random, dungeonDepth, spec.shapeRulesApply());
        FloorPopulator.Result populated = FloorPopulator.populate(
                ge.tbegvadze.toon3d.route.GeneratorId.CAVERN.stableId(), grid, this, placement.spawnPoints,
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
     * Region wall sets (generated-wall-art order 2): the cave goes through the {@link LevelFinisher} as a HALL floor
     * with BREACH weighted up (P3); each chamber names its blueprint's wall family (P2).
     */
    private ge.tbegvadze.toon3d.tileset.LevelPalette finishWalls(char[][] grid, List<LayoutRoom> rooms) {
        LevelFinisher.Request request = LevelFinisher.request(grid, seed).config(config).cavern()
                .basePalette(LevelPalettes.legacy());
        RoomBlueprintRegistry registry = RoomBlueprints.rooms();
        for (LayoutRoom room : rooms) {
            request.room(room.leftColumn + 1, room.bottomRow + 1, room.rightColumn - 1, room.topRow - 1,
                         registry.get(room.blueprintId).wallGroup());
        }
        return request.finish();
    }

    // -------------------------------------------------------------------------
    // Phase 0 — noise initialisation
    // -------------------------------------------------------------------------

    private void initNoise(boolean[][] solid) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        // E6 footprint window (balance-overhaul order 2): only a centred sub-rectangle of the grid may open.
        float axisScale  = (float) Math.sqrt(footprintScale);
        int windowWidth  = Math.min(gridWidth,  Math.round(gridWidth  * axisScale));
        int windowHeight = Math.min(gridHeight, Math.round(gridHeight * axisScale));
        int windowLeft   = (gridWidth  - windowWidth)  / 2;
        int windowBottom = (gridHeight - windowHeight) / 2;
        // Chambers stay inside the window, a tile in from the solid border.
        this.windowLeft   = Math.max(2, windowLeft);
        this.windowBottom = Math.max(2, windowBottom);
        this.windowRight  = Math.min(gridWidth  - 3, windowLeft + windowWidth - 1);
        this.windowTop    = Math.min(gridHeight - 3, windowBottom + windowHeight - 1);
        for (int tileRow = 0; tileRow < gridHeight; tileRow++) {
            for (int tileColumn = 0; tileColumn < gridWidth; tileColumn++) {
                boolean outsideWindow = tileColumn < windowLeft || tileColumn >= windowLeft + windowWidth
                        || tileRow < windowBottom || tileRow >= windowBottom + windowHeight;
                // Force a 1-tile solid border around the entire grid (and rock outside the window)
                if (outsideWindow || tileRow == 0 || tileRow == gridHeight - 1
                        || tileColumn == 0 || tileColumn == gridWidth - 1) {
                    solid[tileRow][tileColumn] = true;
                } else {
                    solid[tileRow][tileColumn] =
                        random.nextFloat() < LevelGenConstants.LEVEL_GEN_CAVE_FILL_PROBABILITY;
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase 1 — cellular automata
    // -------------------------------------------------------------------------

    private boolean[][] smoothPass(boolean[][] solid, boolean birthOnly) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        boolean[][] next = new boolean[gridHeight][gridWidth];
        for (int tileRow = 0; tileRow < gridHeight; tileRow++) {
            for (int tileColumn = 0; tileColumn < gridWidth; tileColumn++) {
                // Perimeter is always solid
                if (tileRow == 0 || tileRow == gridHeight - 1
                        || tileColumn == 0 || tileColumn == gridWidth - 1) {
                    next[tileRow][tileColumn] = true;
                    continue;
                }
                int solidNeighbors = countSolidNeighbors(solid, tileColumn, tileRow);
                if (solid[tileRow][tileColumn]) {
                    // Death rule: open up if fewer than DEATH_LIMIT solid neighbours
                    next[tileRow][tileColumn] =
                        solidNeighbors >= LevelGenConstants.LEVEL_GEN_CAVE_DEATH_LIMIT;
                } else {
                    // Birth rule: fill if enough solid neighbours; birth-only pass skips opening
                    if (solidNeighbors >= LevelGenConstants.LEVEL_GEN_CAVE_BIRTH_LIMIT) {
                        next[tileRow][tileColumn] = true;
                    } else {
                        next[tileRow][tileColumn] = birthOnly && solid[tileRow][tileColumn];
                    }
                }
            }
        }
        return next;
    }

    private int countSolidNeighbors(boolean[][] solid, int tileColumn, int tileRow) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        int count = 0;
        for (int deltaRow = -1; deltaRow <= 1; deltaRow++) {
            for (int deltaColumn = -1; deltaColumn <= 1; deltaColumn++) {
                if (deltaRow == 0 && deltaColumn == 0) continue;
                int neighborRow    = tileRow    + deltaRow;
                int neighborColumn = tileColumn + deltaColumn;
                if (neighborRow < 0 || neighborRow >= gridHeight
                        || neighborColumn < 0 || neighborColumn >= gridWidth) {
                    count++; // out-of-bounds counts as solid
                } else if (solid[neighborRow][neighborColumn]) {
                    count++;
                }
            }
        }
        return count;
    }

    // -------------------------------------------------------------------------
    // Phase 2 — region audit
    // -------------------------------------------------------------------------

    private void auditRegions(boolean[][] solid) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        boolean[][] visited    = new boolean[gridHeight][gridWidth];
        List<List<int[]>> regions = new ArrayList<>();

        for (int tileRow = 1; tileRow < gridHeight - 1; tileRow++) {
            for (int tileColumn = 1; tileColumn < gridWidth - 1; tileColumn++) {
                if (!solid[tileRow][tileColumn] && !visited[tileRow][tileColumn]) {
                    List<int[]> region = floodFillRegion(solid, visited, tileColumn, tileRow);
                    regions.add(region);
                }
            }
        }

        if (regions.isEmpty()) return;

        // Sort regions by size descending; largest = main cave
        regions.sort((regionA, regionB) -> regionB.size() - regionA.size());
        List<int[]> mainRegion = regions.get(0);

        // Erase regions that are too small
        for (int regionIndex = 1; regionIndex < regions.size(); regionIndex++) {
            List<int[]> region = regions.get(regionIndex);
            if (region.size() < LevelGenConstants.LEVEL_GEN_CAVE_MIN_REGION_SIZE) {
                for (int[] tile : region) {
                    solid[tile[1]][tile[0]] = true;
                }
            } else {
                // Large isolated region — stitch to main with drunkard walk
                int[] fromTile = mainRegion.get(random.nextInt(mainRegion.size()));
                int[] toTile   = region.get(random.nextInt(region.size()));
                drunkardWalk(solid, fromTile[0], fromTile[1], toTile[0], toTile[1]);
            }
        }
    }

    private List<int[]> floodFillRegion(boolean[][] solid, boolean[][] visited,
                                         int startColumn, int startRow) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        List<int[]> region = new ArrayList<>();
        Queue<int[]> queue = new LinkedList<>();
        visited[startRow][startColumn] = true;
        queue.add(new int[]{startColumn, startRow});

        int[] deltaColumns = {0, 0, 1, -1};
        int[] deltaRows    = {1, -1, 0, 0};

        while (!queue.isEmpty()) {
            int[] current        = queue.poll();
            int   currentColumn  = current[0];
            int   currentRow     = current[1];
            region.add(current);
            for (int direction = 0; direction < 4; direction++) {
                int neighborColumn = currentColumn + deltaColumns[direction];
                int neighborRow    = currentRow    + deltaRows[direction];
                if (neighborColumn < 0 || neighborColumn >= gridWidth
                        || neighborRow < 0 || neighborRow >= gridHeight) continue;
                if (visited[neighborRow][neighborColumn]) continue;
                if (solid[neighborRow][neighborColumn]) continue;
                visited[neighborRow][neighborColumn] = true;
                queue.add(new int[]{neighborColumn, neighborRow});
            }
        }
        return region;
    }

    private void drunkardWalk(boolean[][] solid,
                               int fromColumn, int fromRow,
                               int toColumn,   int toRow) {
        int currentColumn = fromColumn;
        int currentRow    = fromRow;
        int attempts      = 0;

        while ((currentColumn != toColumn || currentRow != toRow)
                && attempts < LevelGenConstants.LEVEL_GEN_CAVE_MAX_STITCH_ATTEMPTS * 200) {
            attempts++;
            solid[currentRow][currentColumn] = false;

            if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_CAVE_TUNNEL_STRAIGHTNESS) {
                // Move toward target
                int differenceColumn = toColumn - currentColumn;
                int differenceRow    = toRow    - currentRow;
                if (Math.abs(differenceColumn) > Math.abs(differenceRow)) {
                    currentColumn += Integer.signum(differenceColumn);
                } else if (differenceRow != 0) {
                    currentRow += Integer.signum(differenceRow);
                } else {
                    currentColumn += Integer.signum(differenceColumn);
                }
            } else {
                // Random step
                int direction      = random.nextInt(4);
                int[] deltaColumns = {0, 0, 1, -1};
                int[] deltaRows    = {1, -1, 0, 0};
                currentColumn += deltaColumns[direction];
                currentRow    += deltaRows[direction];
            }

            // Clamp to interior
            currentColumn = Math.max(1, Math.min(LevelGenConstants.LEVEL_GEN_GRID_WIDTH  - 2, currentColumn));
            currentRow    = Math.max(1, Math.min(LevelGenConstants.LEVEL_GEN_GRID_HEIGHT - 2, currentRow));
        }
        // Carve the arrival tile
        solid[currentRow][currentColumn] = false;
    }

    // -------------------------------------------------------------------------
    // The solid map as a char grid
    // -------------------------------------------------------------------------

    private char[][] buildGrid(boolean[][] solid) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        char[][] grid  = new char[gridHeight][gridWidth];
        for (int tileRow = 0; tileRow < gridHeight; tileRow++) {
            for (int tileColumn = 0; tileColumn < gridWidth; tileColumn++) {
                grid[tileRow][tileColumn] = solid[tileRow][tileColumn] ? 'x' : ' ';
            }
        }
        return grid;
    }

    // -------------------------------------------------------------------------
    // Phase 3/4 — walled chambers, sockets and tunnels (G3)
    // -------------------------------------------------------------------------

    /**
     * Seats one walled chamber: up to LEVEL_GEN_CAVE_CHAMBER_TRIES positions inside the cave window, clear of every
     * other chamber (LEVEL_GEN_ROOM_MARGIN) and every tunnel, with at least one legal socket whose straight tunnel
     * reaches cave floor; the first LEVEL_GEN_SPECIAL_PLACEMENT_CANDIDATES such positions compete and the one with
     * the most cave floor inside it wins (it sits IN the cave rather than beside it). A regular or entrance chamber
     * that asks for it must already be LEVEL_GEN_CAVE_CHAMBER_FLOOR_OVERLAP cave floor; a special may carve rock.
     * Returns the stamped room (registered, sockets open, tunnels carved) or null.
     */
    private LayoutRoom placeChamber(char[][] grid, List<LayoutRoom> rooms, int interiorWidth, int interiorHeight,
                                    LevelLayoutReport.Role role, String blueprintId, boolean requireOverlap) {
        return placeChamber(grid, rooms, interiorWidth, interiorHeight, role, blueprintId, requireOverlap,
                LevelGenConstants.LEVEL_GEN_CAVE_SOCKETS_MAX);
    }

    private LayoutRoom placeChamber(char[][] grid, List<LayoutRoom> rooms, int interiorWidth, int interiorHeight,
                                    LevelLayoutReport.Role role, String blueprintId, boolean requireOverlap,
                                    int maximumSockets) {
        int totalWidth  = interiorWidth + 2;
        int totalHeight = interiorHeight + 2;
        int maximumLeft   = windowRight - totalWidth + 1;
        int maximumBottom = windowTop - totalHeight + 1;
        if (maximumLeft < windowLeft || maximumBottom < windowBottom) return null;
        boolean[][] nearRooms = DoorwayRules.nearRoomMask(rooms, grid[0].length, grid.length,
                LevelGenConstants.LEVEL_GEN_ROOM_MARGIN);
        LayoutRoom       best        = null;
        List<SocketPlan> bestPlans   = null;
        int              bestOverlap = -1;
        int              competing   = 0;
        for (int attempt = 0; attempt < LevelGenConstants.LEVEL_GEN_CAVE_CHAMBER_TRIES
                && competing < LevelGenConstants.LEVEL_GEN_SPECIAL_PLACEMENT_CANDIDATES; attempt++) {
            int left   = windowLeft + random.nextInt(maximumLeft - windowLeft + 1);
            int bottom = windowBottom + random.nextInt(maximumBottom - windowBottom + 1);
            int right  = left + totalWidth - 1;
            int top    = bottom + totalHeight - 1;
            if (!isClearForChamber(nearRooms, left, bottom, right, top)) continue;
            int overlap = 0;
            for (int tileRow = bottom + 1; tileRow < top; tileRow++) {
                for (int tileColumn = left + 1; tileColumn < right; tileColumn++) {
                    if (LayoutSupport.isOpen(grid, tileColumn, tileRow)) overlap++;
                }
            }
            if (requireOverlap && overlap < LevelGenConstants.LEVEL_GEN_CAVE_CHAMBER_FLOOR_OVERLAP
                    * interiorWidth * interiorHeight) continue;
            LayoutRoom candidate = new LayoutRoom(left, bottom, right, top, role, blueprintId);
            List<SocketPlan> plans = socketPlans(grid, candidate, rooms);
            if (plans.isEmpty()) continue;
            competing++;
            if (overlap > bestOverlap) {
                best        = candidate;
                bestPlans   = plans;
                bestOverlap = overlap;
            }
        }
        if (best == null) return null;
        commitChamber(grid, best, bestPlans, maximumSockets);
        rooms.add(best);
        return best;
    }

    /** The footprint keeps LEVEL_GEN_ROOM_MARGIN from every chamber and a tile from every tunnel. */
    private boolean isClearForChamber(boolean[][] nearRooms, int left, int bottom, int right, int top) {
        for (int tileRow = bottom - 1; tileRow <= top + 1; tileRow++) {
            for (int tileColumn = left - 1; tileColumn <= right + 1; tileColumn++) {
                if (!isInBounds(tileColumn, tileRow)) continue;
                if (tunnelMask[tileRow][tileColumn]) return false;
                boolean inFootprint = tileRow >= bottom && tileRow <= top && tileColumn >= left && tileColumn <= right;
                if (inFootprint && nearRooms[tileRow][tileColumn]) return false;
            }
        }
        return true;
    }

    /**
     * Every legal socket position (R10) on a chamber's shell whose straight outward tunnel reaches cave floor within
     * LEVEL_GEN_CAVE_SOCKET_TUNNEL_MAX carved tiles without passing within a tile of another chamber — shortest
     * tunnel first (the sides nearest cave floor), then side, then position along the wall.
     */
    private List<SocketPlan> socketPlans(char[][] grid, LayoutRoom room, List<LayoutRoom> rooms) {
        boolean[][] nearRooms = DoorwayRules.nearRoomMask(rooms, grid[0].length, grid.length, 1);
        int corner = LevelGenConstants.LEVEL_GEN_SOCKET_CORNER_MIN_DISTANCE;
        List<SocketPlan> plans = new ArrayList<>();
        for (DoorSocket.Side side : DoorSocket.Side.values()) {
            if (side.isHorizontalWall()) {
                int tileRow = side.stepRow > 0 ? room.topRow : room.bottomRow;
                for (int tileColumn = room.leftColumn + corner; tileColumn <= room.rightColumn - corner; tileColumn++) {
                    addSocketPlan(grid, room, nearRooms, side, tileColumn, tileRow, plans);
                }
            } else {
                int tileColumn = side.stepColumn > 0 ? room.rightColumn : room.leftColumn;
                for (int tileRow = room.bottomRow + corner; tileRow <= room.topRow - corner; tileRow++) {
                    addSocketPlan(grid, room, nearRooms, side, tileColumn, tileRow, plans);
                }
            }
        }
        plans.sort((first, second) -> {
            if (first.tunnelLength != second.tunnelLength) return first.tunnelLength - second.tunnelLength;
            if (first.side != second.side) return first.side.ordinal() - second.side.ordinal();
            return first.side.isHorizontalWall() ? first.tileColumn - second.tileColumn : first.tileRow - second.tileRow;
        });
        return plans;
    }

    private void addSocketPlan(char[][] grid, LayoutRoom room, boolean[][] nearRooms, DoorSocket.Side side,
                               int tileColumn, int tileRow, List<SocketPlan> plans) {
        if (!DoorwayRules.canPlaceSocket(room, tileColumn, tileRow, grid[0].length, grid.length)) return;
        for (int step = 1; step <= LevelGenConstants.LEVEL_GEN_CAVE_SOCKET_TUNNEL_MAX + 1; step++) {
            int column = tileColumn + side.stepColumn * step;
            int row    = tileRow + side.stepRow * step;
            if (column <= 0 || row <= 0 || column >= grid[0].length - 1 || row >= grid.length - 1) return;
            if (nearRooms[row][column]) return;
            if (LayoutSupport.isOpen(grid, column, row)) {
                plans.add(new SocketPlan(tileColumn, tileRow, side, step - 1));
                return;
            }
        }
    }

    /**
     * Stamps the chamber — the shell ring forced to wall, the interior carved — then opens
     * LEVEL_GEN_CAVE_SOCKETS_MIN..MAX sockets (at most {@code maximumSockets}) from its plans (one per side first,
     * then any) and carves each one's tunnel to 'l'.
     */
    private void commitChamber(char[][] grid, LayoutRoom room, List<SocketPlan> plans, int maximumSockets) {
        for (int tileRow = room.bottomRow; tileRow <= room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn; tileColumn <= room.rightColumn; tileColumn++) {
                grid[tileRow][tileColumn] = room.isShellTile(tileColumn, tileRow) ? 'x' : ' ';
            }
        }
        int wanted = Math.min(maximumSockets,
                randomBetween(LevelGenConstants.LEVEL_GEN_CAVE_SOCKETS_MIN, LevelGenConstants.LEVEL_GEN_CAVE_SOCKETS_MAX));
        boolean[] sideUsed = new boolean[DoorSocket.Side.values().length];
        int opened = 0;
        for (int pass = 0; pass < 2 && opened < wanted; pass++) {
            for (SocketPlan plan : plans) {
                if (opened >= wanted) break;
                if (pass == 0 && sideUsed[plan.side.ordinal()]) continue;
                if (!DoorwayRules.canPlaceSocket(room, plan.tileColumn, plan.tileRow, grid[0].length, grid.length)) continue;
                DoorwayRules.openSocket(grid, room, plan.tileColumn, plan.tileRow);
                for (int step = 1; step <= plan.tunnelLength; step++) {
                    int column = plan.tileColumn + plan.side.stepColumn * step;
                    int row    = plan.tileRow + plan.side.stepRow * step;
                    grid[row][column]       = 'l';
                    tunnelMask[row][column] = true;
                }
                sideUsed[plan.side.ordinal()] = true;
                opened++;
            }
        }
    }

    /**
     * R9: a forced shell may cut cave off from the rest. While any open tile is unreachable from the entrance, its
     * region is either erased (a pocket under LEVEL_GEN_CAVE_MIN_REGION_SIZE with no chamber in it) or rejoined by a
     * bypass tunnel the {@link CorridorRouter} routes around every chamber footprint, so no shell is ever cut.
     */
    private void rejoinSeveredCave(char[][] grid, List<LayoutRoom> rooms, LayoutRoom entrance) {
        int gridWidth  = grid[0].length;
        int gridHeight = grid.length;
        LayoutSupport.RouteMasks masks = new LayoutSupport.RouteMasks(rooms, gridWidth, gridHeight);
        for (int iteration = 0; iteration < gridWidth + gridHeight; iteration++) {
            boolean[][] reachable = LayoutSupport.reachableFrom(grid, entrance.centerColumn(), entrance.centerRow());
            int[] stranded = null;
            for (int tileRow = 1; tileRow < gridHeight - 1 && stranded == null; tileRow++) {
                for (int tileColumn = 1; tileColumn < gridWidth - 1; tileColumn++) {
                    if (!reachable[tileRow][tileColumn] && LayoutSupport.isOpen(grid, tileColumn, tileRow)) {
                        stranded = new int[]{ tileColumn, tileRow };
                        break;
                    }
                }
            }
            if (stranded == null) return;
            List<int[]> region = LayoutSupport.openRegion(grid, stranded[0], stranded[1]);
            boolean holdsChamber = false;
            for (int[] tile : region) holdsChamber |= masks.rooms[tile[1]][tile[0]];
            if (!holdsChamber && region.size() < LevelGenConstants.LEVEL_GEN_CAVE_MIN_REGION_SIZE) {
                for (int[] tile : region) grid[tile[1]][tile[0]] = 'x';
                continue;
            }
            int[][] ends = nearestBypassEnds(region, reachable, masks.rooms, gridWidth, gridHeight);
            List<int[]> path = ends == null ? Collections.<int[]>emptyList()
                    : CorridorRouter.route(grid, masks.rooms, masks.hug, ends[0][0], ends[0][1], ends[1][0], ends[1][1],
                            GameMath.mixSeed(seed ^ LevelGenConstants.LEVEL_GEN_CAVE_BYPASS_SEED_SALT, iteration + 1));
            if (path.isEmpty()) {
                if (holdsChamber) return; // the repair backstop is the last word
                for (int[] tile : region) grid[tile[1]][tile[0]] = 'x';
                continue;
            }
            for (int[] tile : path) {
                if (Level.isWall(grid[tile[1]][tile[0]])) grid[tile[1]][tile[0]] = 'l';
            }
        }
    }

    /**
     * The bypass ends: a multi-source BFS from every reachable open tile outside the chambers, over every tile
     * outside the chambers, stops at the first tile of the stranded region; returns {region tile, its source}.
     */
    private static int[][] nearestBypassEnds(List<int[]> region, boolean[][] reachable, boolean[][] footprint,
                                             int gridWidth, int gridHeight) {
        boolean[][] inRegion = new boolean[gridHeight][gridWidth];
        for (int[] tile : region) inRegion[tile[1]][tile[0]] = true;
        int[][] source = new int[gridHeight * gridWidth][];
        boolean[][] seen = new boolean[gridHeight][gridWidth];
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        for (int tileRow = 1; tileRow < gridHeight - 1; tileRow++) {
            for (int tileColumn = 1; tileColumn < gridWidth - 1; tileColumn++) {
                if (!reachable[tileRow][tileColumn] || footprint[tileRow][tileColumn]) continue;
                seen[tileRow][tileColumn] = true;
                source[tileRow * gridWidth + tileColumn] = new int[]{ tileColumn, tileRow };
                queue.add(new int[]{ tileColumn, tileRow });
            }
        }
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int[] origin  = source[current[1] * gridWidth + current[0]];
            for (int direction = 0; direction < 4; direction++) {
                int column = current[0] + STEP_COLUMNS[direction];
                int row    = current[1] + STEP_ROWS[direction];
                if (column <= 0 || row <= 0 || column >= gridWidth - 1 || row >= gridHeight - 1) continue;
                if (seen[row][column] || footprint[row][column]) continue;
                seen[row][column] = true;
                if (inRegion[row][column]) return new int[][]{ { column, row }, origin };
                source[row * gridWidth + column] = origin;
                queue.add(new int[]{ column, row });
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Phase 5 — the cave body: biomes, floor moods, decals
    // -------------------------------------------------------------------------

    /**
     * One contiguous region of the open cave body. Each biome sets its own floor lighting, walkable decals and the
     * props its wall-anchored DEBRIS is made of, so a single organic cavern reads as several distinct sci-fi
     * environments fused together. All symbols are existing tiles (see docs/tile-symbols.txt).
     */
    private enum CaveBiome {
        // Overgrown bioluminescent spore garden: glowing pools, bio-pods.
        FUNGAL_HOLLOW (new char[]{'O', '.', 's'}, new char[]{'&'},      'I', FloorMood.GLOW),
        // Frozen cryo-vault: dim blue light, cryo pods.
        FROST_VAULT   (new char[]{'s', 'O'},      new char[]{'&', 'L'}, '&', FloorMood.DIM),
        // Radioactive reactor sump: leaking drums, flickering light.
        REACTOR_SUMP  (new char[]{'e', 'O', '.'}, new char[]{'g', 'E'}, '%', FloorMood.FLICKER),
        // Derelict data wreck: dead lockers and crates, an AI tomb. No terminal in a cave body (R20).
        DERELICT_NEST (new char[]{'.', 'm', 'e'}, new char[]{'L', 'C'}, 'J', FloorMood.DIM),
        // Infested gore warren: corpse drifts, deep darkness, deathtrap drums.
        GORE_WARREN   (new char[]{'m', '.', 's'}, new char[]{'E'},      'E', FloorMood.DARK),
        // Bare mineral grotto: mining rubble, dense stalagmite formations.
        CRYSTAL_GROTTO(new char[]{'O', '.'},      new char[]{'C'},      'C', FloorMood.ROCKY);

        final char[]    decalChars;    // walkable decals scattered on the floor
        final char[]    scatterProps;  // the solid props its debris clusters are made of
        final char      landmarkProp;  // the set-piece prop a debris cluster may anchor on
        final FloorMood mood;          // floor-lighting personality

        CaveBiome(char[] decalChars, char[] scatterProps, char landmarkProp, FloorMood mood) {
            this.decalChars   = decalChars;
            this.scatterProps = scatterProps;
            this.landmarkProp = landmarkProp;
            this.mood         = mood;
        }
    }

    /** Floor-lighting profile of a biome (drives the unlit/dim/lit/flicker mix). */
    private enum FloorMood { GLOW, DIM, DARK, FLICKER, ROCKY }

    /**
     * Partitions the open cave body into LEVEL_GEN_CAVE_BIOME_MIN..MAX contiguous regions. Seeds are chosen by greedy
     * farthest-point sampling so they spread across the cave, then a multi-source flood fill grows every region
     * outward simultaneously. Returns the biome assigned to each id; biomeMap holds the per-tile id.
     */
    private CaveBiome[] assignBiomes(char[][] grid, boolean[][] footprint) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        biomeMap = new int[gridHeight][gridWidth];
        for (int[] row : biomeMap) java.util.Arrays.fill(row, -1);

        // Every open cave-body floor tile (chambers are excluded so the man-made boxes keep their own theming).
        List<int[]> caveFloor = new ArrayList<>();
        for (int tileRow = 1; tileRow < gridHeight - 1; tileRow++) {
            for (int tileColumn = 1; tileColumn < gridWidth - 1; tileColumn++) {
                if (footprint[tileRow][tileColumn]) continue;
                if (grid[tileRow][tileColumn] == ' ') caveFloor.add(new int[]{tileColumn, tileRow});
            }
        }
        if (caveFloor.isEmpty()) return new CaveBiome[0];

        CaveBiome[] allBiomes = CaveBiome.values();
        int biomeCount = randomBetween(LevelGenConstants.LEVEL_GEN_CAVE_BIOME_MIN, LevelGenConstants.LEVEL_GEN_CAVE_BIOME_MAX);
        biomeCount = Math.min(biomeCount, allBiomes.length);
        biomeCount = Math.min(biomeCount, caveFloor.size());
        List<CaveBiome> pool = new ArrayList<>();
        Collections.addAll(pool, allBiomes);
        shuffle(pool);
        CaveBiome[] biomes = new CaveBiome[biomeCount];
        for (int biomeIndex = 0; biomeIndex < biomeCount; biomeIndex++) biomes[biomeIndex] = pool.get(biomeIndex);

        List<int[]> seeds = pickDistinctTiles(caveFloor, biomeCount);
        Queue<int[]> frontier = new LinkedList<>();
        for (int biomeIndex = 0; biomeIndex < seeds.size(); biomeIndex++) {
            int[] seedTile = seeds.get(biomeIndex);
            biomeMap[seedTile[1]][seedTile[0]] = biomeIndex;
            frontier.add(seedTile);
        }
        while (!frontier.isEmpty()) {
            int[] current      = frontier.poll();
            int   currentBiome = biomeMap[current[1]][current[0]];
            for (int direction = 0; direction < 4; direction++) {
                int neighborColumn = current[0] + STEP_COLUMNS[direction];
                int neighborRow    = current[1] + STEP_ROWS[direction];
                if (!isInBounds(neighborColumn, neighborRow)) continue;
                if (footprint[neighborRow][neighborColumn]) continue;
                if (biomeMap[neighborRow][neighborColumn] != -1) continue;
                if (grid[neighborRow][neighborColumn] != ' ') continue;
                biomeMap[neighborRow][neighborColumn] = currentBiome;
                frontier.add(new int[]{neighborColumn, neighborRow});
            }
        }
        // A cave tile the flood could not reach adopts the nearest seed's biome so no floor is left untyped.
        for (int[] tile : caveFloor) {
            if (biomeMap[tile[1]][tile[0]] != -1) continue;
            int nearestBiome    = 0;
            int nearestDistance = Integer.MAX_VALUE;
            for (int biomeIndex = 0; biomeIndex < seeds.size(); biomeIndex++) {
                int[] seedTile = seeds.get(biomeIndex);
                int distance = Math.abs(seedTile[0] - tile[0]) + Math.abs(seedTile[1] - tile[1]);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestBiome    = biomeIndex;
                }
            }
            biomeMap[tile[1]][tile[0]] = nearestBiome;
        }
        return biomes;
    }

    /** Fisher-Yates shuffle using the generator's seeded Random. */
    private <T> void shuffle(List<T> list) {
        for (int index = list.size() - 1; index > 0; index--) {
            int swapIndex = random.nextInt(index + 1);
            T temporary = list.get(index);
            list.set(index, list.get(swapIndex));
            list.set(swapIndex, temporary);
        }
    }

    /**
     * Greedy farthest-point sampling: picks {@code count} tiles that are spread
     * across the source set so biome seeds do not clump in one corner of the cave.
     */
    private List<int[]> pickDistinctTiles(List<int[]> source, int count) {
        List<int[]> picked = new ArrayList<>();
        if (source.isEmpty()) return picked;
        picked.add(source.get(random.nextInt(source.size())));
        while (picked.size() < count) {
            int[] best         = null;
            int   bestDistance = -1;
            for (int sample = 0; sample < 64; sample++) {
                int[] candidate = source.get(random.nextInt(source.size()));
                int   nearest   = Integer.MAX_VALUE;
                for (int[] chosen : picked) {
                    int distance = Math.abs(chosen[0] - candidate[0])
                                 + Math.abs(chosen[1] - candidate[1]);
                    nearest = Math.min(nearest, distance);
                }
                if (nearest > bestDistance) {
                    bestDistance = nearest;
                    best         = candidate;
                }
            }
            if (best == null) break;
            picked.add(best);
        }
        return picked;
    }

    /** Reskins each cave-floor tile per its biome's lighting mood. */
    private void themeCaveFloor(char[][] grid, boolean[][] footprint, CaveBiome[] biomes) {
        int flickerRemaining = LevelGenConstants.LEVEL_GEN_CAVE_FLICKER_BUDGET;
        for (int tileRow = 1; tileRow < grid.length - 1; tileRow++) {
            for (int tileColumn = 1; tileColumn < grid[0].length - 1; tileColumn++) {
                if (footprint[tileRow][tileColumn] || grid[tileRow][tileColumn] != ' ') continue;
                int biomeId = biomeMap[tileRow][tileColumn];
                if (biomeId < 0) continue;
                char floorChar = floorCharForMood(biomes[biomeId].mood, flickerRemaining > 0);
                if (floorChar == 'f') flickerRemaining--;
                grid[tileRow][tileColumn] = floorChar;
            }
        }
    }

    private char floorCharForMood(FloorMood mood, boolean flickerAllowed) {
        float roll = random.nextFloat();
        switch (mood) {
            case GLOW:  return roll < 0.10f ? 'u' : (roll < 0.45f ? 'l' : ' ');
            case DIM:   return roll < 0.22f ? 'u' : (roll < 0.78f ? 'l' : ' ');
            case DARK:  return roll < 0.62f ? 'u' : (roll < 0.88f ? 'l' : ' ');
            case ROCKY: return roll < 0.38f ? 'u' : (roll < 0.82f ? 'l' : ' ');
            case FLICKER:
                if (flickerAllowed && roll < 0.05f) return 'f';
                return roll < 0.55f ? 'u' : (roll < 0.82f ? 'l' : ' ');
            default:    return ' ';
        }
    }

    /** Biome-flavoured walkable decals on the cave floor (R19: never on a socket's outer tile). */
    private void placeCaveDecals(char[][] grid, boolean[][] footprint, CaveBiome[] biomes, List<LayoutRoom> rooms) {
        boolean[][] socketOuter = socketOuterTiles(rooms, grid[0].length, grid.length);
        for (int tileRow = 1; tileRow < grid.length - 1; tileRow++) {
            for (int tileColumn = 1; tileColumn < grid[0].length - 1; tileColumn++) {
                if (footprint[tileRow][tileColumn] || socketOuter[tileRow][tileColumn]) continue;
                int biomeId = biomeMap[tileRow][tileColumn];
                if (biomeId < 0) continue;
                char cell = grid[tileRow][tileColumn];
                if (cell != ' ' && cell != 'u' && cell != 'l') continue;
                if (random.nextFloat() >= LevelGenConstants.LEVEL_GEN_CAVE_DECAL_CHANCE) continue;
                char[] decals = biomes[biomeId].decalChars;
                grid[tileRow][tileColumn] = decals[random.nextInt(decals.length)];
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase 5 — the cave body's two solid rules (G3): FORMATIONS and DEBRIS
    // -------------------------------------------------------------------------

    /**
     * FORMATIONS: 'P' stalagmite clusters of 1..LEVEL_GEN_CAVE_FORMATION_CLUSTER_MAX, 0..PER_BIOME_MAX clusters per
     * biome (x GROTTO_MULTIPLIER in a crystal grotto), only on open ground (the 5x5 window holds >= FORMATION_MIN_OPEN
     * open tiles) and never within FORMATION_SOCKET_DISTANCE of a socket.
     */
    private void placeFormations(char[][] grid, boolean[][] footprint, CaveBiome[] biomes, List<LayoutRoom> rooms) {
        List<List<int[]>> tilesByBiome = biomeFloorTiles(biomes.length);
        for (int biomeId = 0; biomeId < biomes.length; biomeId++) {
            List<int[]> tiles = tilesByBiome.get(biomeId);
            int cap = LevelGenConstants.LEVEL_GEN_CAVE_FORMATIONS_PER_BIOME_MAX
                    * (biomes[biomeId] == CaveBiome.CRYSTAL_GROTTO ? LevelGenConstants.LEVEL_GEN_CAVE_FORMATION_GROTTO_MULTIPLIER : 1);
            int clusters = randomBetween(0, cap);
            for (int cluster = 0; cluster < clusters && !tiles.isEmpty(); cluster++) {
                for (int attempt = 0; attempt < LevelGenConstants.LEVEL_GEN_CAVE_SOLID_CLUSTER_TRIES; attempt++) {
                    int[] anchor = tiles.get(random.nextInt(tiles.size()));
                    if (!isFormationTile(grid, footprint, rooms, biomeId, anchor[0], anchor[1])) continue;
                    if (!placeSolid(grid, anchor[0], anchor[1], 'P')) continue;
                    int formationBiome = biomeId;
                    growCluster(grid, anchor, randomBetween(1, LevelGenConstants.LEVEL_GEN_CAVE_FORMATION_CLUSTER_MAX),
                            (column, row) -> isFormationTile(grid, footprint, rooms, formationBiome, column, row), () -> 'P');
                    break;
                }
            }
        }
    }

    /**
     * DEBRIS: LEVEL_GEN_CAVE_BIOME_FEATURE_MIN..MAX clusters per biome of 1..DEBRIS_CLUSTER_MAX of the biome's props,
     * every one flush against CAVE wall (never a chamber shell); the first may be the biome's landmark at
     * LEVEL_GEN_CAVE_BIOME_LANDMARK_CHANCE.
     */
    private void placeDebris(char[][] grid, boolean[][] footprint, CaveBiome[] biomes, List<LayoutRoom> rooms) {
        List<List<int[]>> tilesByBiome = biomeFloorTiles(biomes.length);
        for (int biomeId = 0; biomeId < biomes.length; biomeId++) {
            List<int[]> tiles = tilesByBiome.get(biomeId);
            CaveBiome biome = biomes[biomeId];
            int clusters = randomBetween(LevelGenConstants.LEVEL_GEN_CAVE_BIOME_FEATURE_MIN,
                                         LevelGenConstants.LEVEL_GEN_CAVE_BIOME_FEATURE_MAX);
            for (int cluster = 0; cluster < clusters && !tiles.isEmpty(); cluster++) {
                for (int attempt = 0; attempt < LevelGenConstants.LEVEL_GEN_CAVE_SOLID_CLUSTER_TRIES; attempt++) {
                    int[] anchor = tiles.get(random.nextInt(tiles.size()));
                    if (!isDebrisTile(grid, footprint, rooms, anchor[0], anchor[1])) continue;
                    boolean landmark = random.nextFloat() < LevelGenConstants.LEVEL_GEN_CAVE_BIOME_LANDMARK_CHANCE;
                    char first = landmark ? biome.landmarkProp : biome.scatterProps[random.nextInt(biome.scatterProps.length)];
                    if (!placeSolid(grid, anchor[0], anchor[1], first)) continue;
                    growCluster(grid, anchor, randomBetween(1, LevelGenConstants.LEVEL_GEN_CAVE_DEBRIS_CLUSTER_MAX),
                            (column, row) -> isDebrisTile(grid, footprint, rooms, column, row),
                            () -> biome.scatterProps[random.nextInt(biome.scatterProps.length)]);
                    break;
                }
            }
        }
    }

    @FunctionalInterface
    private interface TileRule { boolean accepts(int tileColumn, int tileRow); }

    @FunctionalInterface
    private interface SymbolSource { char next(); }

    /** Grows a placed anchor to {@code size} solids, one orthogonal step at a time from the last placed tile. */
    private void growCluster(char[][] grid, int[] anchor, int size, TileRule rule, SymbolSource symbols) {
        int placed = 1;
        int[] last = anchor;
        for (int step = 0; step < size * 4 && placed < size; step++) {
            int direction = random.nextInt(4);
            int column = last[0] + STEP_COLUMNS[direction];
            int row    = last[1] + STEP_ROWS[direction];
            if (!rule.accepts(column, row)) continue;
            if (!placeSolid(grid, column, row, symbols.next())) continue;
            last = new int[]{ column, row };
            placed++;
        }
    }

    /** Every biome's still-plain cave floor tiles, in scan order (the candidate pools both rules draw from). */
    private List<List<int[]>> biomeFloorTiles(int biomeCount) {
        List<List<int[]>> tiles = new ArrayList<>();
        for (int biomeId = 0; biomeId < biomeCount; biomeId++) tiles.add(new ArrayList<>());
        for (int tileRow = 1; tileRow < biomeMap.length - 1; tileRow++) {
            for (int tileColumn = 1; tileColumn < biomeMap[0].length - 1; tileColumn++) {
                int biomeId = biomeMap[tileRow][tileColumn];
                if (biomeId >= 0 && biomeId < biomeCount) tiles.get(biomeId).add(new int[]{ tileColumn, tileRow });
            }
        }
        return tiles;
    }

    private boolean isFormationTile(char[][] grid, boolean[][] footprint, List<LayoutRoom> rooms, int biomeId,
                                    int tileColumn, int tileRow) {
        if (!isCaveSolidTile(grid, footprint, rooms, tileColumn, tileRow)) return false;
        if (biomeMap[tileRow][tileColumn] != biomeId) return false;
        if (openTilesAround(grid, tileColumn, tileRow, 2) < LevelGenConstants.LEVEL_GEN_CAVE_FORMATION_MIN_OPEN) return false;
        int keepAway = LevelGenConstants.LEVEL_GEN_CAVE_FORMATION_SOCKET_DISTANCE;
        for (LayoutRoom room : rooms) {
            for (DoorSocket socket : room.sockets()) {
                if (Math.max(Math.abs(socket.tileColumn - tileColumn), Math.abs(socket.tileRow - tileRow)) <= keepAway) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Debris stands flush against CAVE wall: an orthogonal wall neighbour outside every chamber footprint. */
    private boolean isDebrisTile(char[][] grid, boolean[][] footprint, List<LayoutRoom> rooms,
                                 int tileColumn, int tileRow) {
        if (!isCaveSolidTile(grid, footprint, rooms, tileColumn, tileRow)) return false;
        for (int direction = 0; direction < 4; direction++) {
            int column = tileColumn + STEP_COLUMNS[direction];
            int row    = tileRow + STEP_ROWS[direction];
            if (isInBounds(column, row) && Level.isWall(grid[row][column]) && !footprint[row][column]) return true;
        }
        return false;
    }

    /**
     * What both rules share: plain cave floor outside every chamber and tunnel, outside the start's safe radius and
     * every door clearance zone (R18 a), and where the open width across it is >= LEVEL_GEN_CAVE_SOLID_MIN_PASSAGE_WIDTH
     * on both axes (never in a narrow passage).
     */
    private boolean isCaveSolidTile(char[][] grid, boolean[][] footprint, List<LayoutRoom> rooms,
                                    int tileColumn, int tileRow) {
        if (tileColumn <= 1 || tileRow <= 1 || tileColumn >= grid[0].length - 2 || tileRow >= grid.length - 2) return false;
        if (footprint[tileRow][tileColumn] || tunnelMask[tileRow][tileColumn]) return false;
        if (!LayoutSupport.isPlainFloor(grid[tileRow][tileColumn])) return false;
        if (Math.max(Math.abs(tileColumn - spawnColumn), Math.abs(tileRow - spawnRow))
                < LevelGenConstants.LEVEL_GEN_CAVE_BIOME_PROP_SAFE_RADIUS) return false;
        if (DoorwayRules.isInDoorClearance(grid, rooms, tileColumn, tileRow)) return false;
        int minimumWidth = LevelGenConstants.LEVEL_GEN_CAVE_SOLID_MIN_PASSAGE_WIDTH;
        return openRun(grid, tileColumn, tileRow, 1, 0) >= minimumWidth
            && openRun(grid, tileColumn, tileRow, 0, 1) >= minimumWidth;
    }

    /** The open tiles in a straight line through a tile along one axis, the tile itself included. */
    private static int openRun(char[][] grid, int tileColumn, int tileRow, int stepColumn, int stepRow) {
        int run = 1;
        for (int sign = -1; sign <= 1; sign += 2) {
            int column = tileColumn + sign * stepColumn;
            int row    = tileRow + sign * stepRow;
            while (LayoutSupport.isOpen(grid, column, row)) {
                run++;
                column += sign * stepColumn;
                row    += sign * stepRow;
            }
        }
        return run;
    }

    private static int openTilesAround(char[][] grid, int tileColumn, int tileRow, int radius) {
        int open = 0;
        for (int row = tileRow - radius; row <= tileRow + radius; row++) {
            for (int column = tileColumn - radius; column <= tileColumn + radius; column++) {
                if (LayoutSupport.isOpen(grid, column, row)) open++;
            }
        }
        return open;
    }

    /**
     * Stamps one cave-body solid (R18 c): it may not plug a one-tile passage, turn a solid neighbour into a plug, or
     * strand any tile. Returns whether it stayed.
     */
    private boolean placeSolid(char[][] grid, int tileColumn, int tileRow, char symbol) {
        int before = countReachable(grid);
        char previous = grid[tileRow][tileColumn];
        grid[tileRow][tileColumn] = symbol;
        boolean plugs = FloorPopulator.wouldPlugPassage(grid, tileColumn, tileRow);
        for (int direction = 0; direction < 4 && !plugs; direction++) {
            int column = tileColumn + STEP_COLUMNS[direction];
            int row    = tileRow + STEP_ROWS[direction];
            if (!isInBounds(column, row)) continue;
            char neighbour = grid[row][column];
            if (Level.isPropSolid(neighbour) || Level.isColumn(neighbour)) {
                plugs = FloorPopulator.wouldPlugPassage(grid, column, row);
            }
        }
        if (plugs || countReachable(grid) != before - 1) {
            grid[tileRow][tileColumn] = previous;
            return false;
        }
        return true;
    }

    private int countReachable(char[][] grid) {
        int count = 0;
        for (boolean[] row : LayoutSupport.reachableFrom(grid, spawnColumn, spawnRow)) {
            for (boolean cell : row) if (cell) count++;
        }
        return count;
    }

    private static boolean[][] socketOuterTiles(List<LayoutRoom> rooms, int gridWidth, int gridHeight) {
        boolean[][] outer = new boolean[gridHeight][gridWidth];
        for (LayoutRoom room : rooms) {
            for (DoorSocket socket : room.sockets()) {
                int column = socket.outerColumn(), row = socket.outerRow();
                if (column >= 0 && row >= 0 && column < gridWidth && row < gridHeight) outer[row][column] = true;
            }
        }
        return outer;
    }

    // -------------------------------------------------------------------------
    // Encounter spawn rule, stairs
    // -------------------------------------------------------------------------

    /** A walkable cave tile (floor or decal, not the start, not beside a door) outside the start's safe radius. */
    private boolean isCaveSpawnCell(char[][] grid, int tileColumn, int tileRow) {
        if (tileColumn < 1 || tileColumn >= LevelGenConstants.LEVEL_GEN_GRID_WIDTH  - 1) return false;
        if (tileRow    < 1 || tileRow    >= LevelGenConstants.LEVEL_GEN_GRID_HEIGHT - 1) return false;
        char cell = grid[tileRow][tileColumn];
        if (!LayoutSupport.isPlainFloor(cell) && !isDecal(cell)) return false;
        if (SupplySlotSurvey.isAdjacentToDoor(grid, tileColumn, tileRow)) return false;
        int chebyshevDistance = Math.max(Math.abs(tileColumn - spawnColumn), Math.abs(tileRow - spawnRow));
        return chebyshevDistance >= LevelGenConstants.LEVEL_GEN_CAVE_SPAWN_SAFE_RADIUS;
    }

    /**
     * Exactly one exit: the plain floor tile farthest (walking) from the start, never a socket, a tunnel tile or a
     * door clearance tile.
     */
    private void stampStairsDown(char[][] grid, List<LayoutRoom> rooms) {
        int gridWidth  = grid[0].length;
        int gridHeight = grid.length;
        int[][] distance = new int[gridHeight][gridWidth];
        for (int[] row : distance) java.util.Arrays.fill(row, -1);
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        distance[spawnRow][spawnColumn] = 0;
        queue.add(new int[]{ spawnColumn, spawnRow });
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            for (int direction = 0; direction < 4; direction++) {
                int column = current[0] + STEP_COLUMNS[direction];
                int row    = current[1] + STEP_ROWS[direction];
                if (!LayoutSupport.isOpen(grid, column, row) || distance[row][column] >= 0) continue;
                distance[row][column] = distance[current[1]][current[0]] + 1;
                queue.add(new int[]{ column, row });
            }
        }
        int farthestColumn = -1, farthestRow = -1, farthestDistance = -1;
        for (int tileRow = 1; tileRow < gridHeight - 1; tileRow++) {
            for (int tileColumn = 1; tileColumn < gridWidth - 1; tileColumn++) {
                if (distance[tileRow][tileColumn] <= farthestDistance) continue;
                if (!LayoutSupport.isPlainFloor(grid[tileRow][tileColumn]) || tunnelMask[tileRow][tileColumn]) continue;
                if (isSocketTile(rooms, tileColumn, tileRow)) continue;
                if (DoorwayRules.isInDoorClearance(grid, rooms, tileColumn, tileRow)) continue;
                farthestDistance = distance[tileRow][tileColumn];
                farthestColumn   = tileColumn;
                farthestRow      = tileRow;
            }
        }
        if (farthestColumn >= 0) grid[farthestRow][farthestColumn] = RenderConstants.STAIRS_DOWN_CHAR;
    }

    private static boolean isSocketTile(List<LayoutRoom> rooms, int tileColumn, int tileRow) {
        for (LayoutRoom room : rooms) if (room.socketAt(tileColumn, tileRow) != null) return true;
        return false;
    }

    // -------------------------------------------------------------------------
    // Fallback
    // -------------------------------------------------------------------------

    private Level buildFallbackLevel() {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        char[][] grid  = new char[gridHeight][gridWidth];
        int centerColumn = gridWidth  / 2;
        int centerRow    = gridHeight / 2;
        for (char[] row : grid) java.util.Arrays.fill(row, 'x');
        for (int tileRow = centerRow - 3; tileRow <= centerRow + 3; tileRow++) {
            for (int tileColumn = centerColumn - 5; tileColumn <= centerColumn + 5; tileColumn++) {
                grid[tileRow][tileColumn] = ' ';
            }
        }
        grid[centerRow][centerColumn] = 'p';
        dresser = null;
        // Even the fallback goes through the shared supply pipeline (S1).
        buildSupplyRegionMap(new ArrayList<>());
        FloorPopulator.Result populated = FloorPopulator.populate(
                ge.tbegvadze.toon3d.route.GeneratorId.CAVERN.stableId(), grid, this, new ArrayList<>(),
                new FloorPopulator.EncounterFacts(-1, 0f, 0f, 0, 0), config, dungeonDepth, seed);
        return populated.attachTo(new Level(grid, populated.spawnPoints, populated.weaponSpawnPoints,
                         LevelFinisher.request(grid, seed).config(config).cavern()
                                 .basePalette(LevelPalettes.legacy()).finish()));
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    private boolean isDecal(char cell) {
        return cell == 'm' || cell == '.' || cell == 'O' || cell == 's' || cell == 'e';
    }

    private boolean isInBounds(int tileColumn, int tileRow) {
        return tileColumn >= 0 && tileColumn < LevelGenConstants.LEVEL_GEN_GRID_WIDTH
            && tileRow    >= 0 && tileRow    < LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
    }

    private int randomBetween(int minInclusive, int maxInclusive) {
        if (maxInclusive <= minInclusive) return minInclusive;
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }

    // -------------------------------------------------------------------------
    // Supply regions (balance-overhaul order 2: SupplySlotProvider)
    // -------------------------------------------------------------------------

    /** Chambers are regions 0..n-1; the cave body is cut into fixed pockets numbered after them. */
    private void buildSupplyRegionMap(List<LayoutRoom> rooms) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        int pocketWidth  = LevelGenConstants.LEVEL_GEN_CAVE_SUPPLY_POCKET_WIDTH;
        int pocketHeight = LevelGenConstants.LEVEL_GEN_CAVE_SUPPLY_POCKET_HEIGHT;
        int pocketColumns = (gridWidth + pocketWidth - 1) / pocketWidth;
        supplyRooms     = rooms;
        supplyRegionMap = new int[gridHeight][gridWidth];
        for (int tileRow = 0; tileRow < gridHeight; tileRow++) {
            for (int tileColumn = 0; tileColumn < gridWidth; tileColumn++) {
                int pocket = (tileRow / pocketHeight) * pocketColumns + (tileColumn / pocketWidth);
                supplyRegionMap[tileRow][tileColumn] = rooms.size() + pocket;
            }
        }
        for (int roomIndex = 0; roomIndex < rooms.size(); roomIndex++) {
            LayoutRoom room = rooms.get(roomIndex);
            for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
                for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                    if (isInBounds(tileColumn, tileRow)) supplyRegionMap[tileRow][tileColumn] = roomIndex;
                }
            }
        }
    }

    @Override
    public int supplyRegionAt(int tileColumn, int tileRow) {
        if (supplyRegionMap == null || !isInBounds(tileColumn, tileRow)) return CONNECTOR_REGION;
        return supplyRegionMap[tileRow][tileColumn];
    }

    @Override
    public boolean isLargeSupplyRegion(int regionId) {
        // Only a chamber can count as large; a cave pocket is a fixed-size cut of the open cave.
        return supplyRooms != null && regionId >= 0 && regionId < supplyRooms.size()
                && supplyRooms.get(regionId).interiorArea() >= LevelGenConstants.LEVEL_GEN_CAVE_LARGE_CHAMBER_AREA;
    }

    @Override
    public boolean isKeepClearTile(int tileColumn, int tileRow) {
        return dresser != null && dresser.isKeepClear(tileColumn, tileRow);
    }
}
