package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.route.GeneratorId;
import ge.tbegvadze.toon3d.route.RouteRegistries;
import ge.tbegvadze.toon3d.tileset.LevelPalette;
import ge.tbegvadze.toon3d.tileset.RoomSymbolDemand;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * LEVEL QUALITY AUDIT (level-generation-overhaul) — a DEV-ONLY MEASURING INSTRUMENT, like
 * {@code sim/LadderReport} and {@code render/RenderPerformanceProbe}. It is NOT a test: it asserts nothing,
 * is wired to no Gradle task and no CI job, and never fails a build. It sweeps seeds x depths x generators,
 * builds every floor exactly as World does (through {@link RouteRegistries#generators()}, plus the start
 * room), and reports the layout invariants I1-I16 of the idea file, per generator and pooled, with optional
 * ASCII dumps. Output: {@code core/build/reports/level-audit/summary.txt} (+ {@code dumps/}), echoed to stdout.
 *
 * <p>Args: {@code --seeds N} (default 200), {@code --depths 1,5,10,15,20,25}, {@code --generators
 * ROOMS_MST,CAVERN,START_ROOM} (default all), {@code --dump K} (K grids per generator), {@code --out DIR},
 * {@code --explain K} (print the first K violation locations per generator).
 * Headless — no LibGDX.
 */
public final class LevelQualityAudit {

    private static final String START_ROOM = "START_ROOM";

    // Rooms dressed with racks or heaps (the DENSE kits), when the generator reports no kit of its own.
    private static final Set<String> DENSE_BLUEPRINTS = new HashSet<>(Arrays.asList(
            RoomBlueprints.ID_SERVER_ROOM, RoomBlueprints.ID_SALVAGE_BAY));
    // Rooms (blueprint or kit ids) in which a terminal belongs (R20).
    private static final Set<String> TERMINAL_ROOMS = new HashSet<>(Arrays.asList(
            "office", RoomBlueprints.ID_SERVER_ROOM, RoomBlueprints.ID_RESEARCH_LAB,
            RoomBlueprints.ID_COMMAND_CENTER, RoomBlueprints.ID_MEDICAL_BAY, RoomBlueprints.ID_CONTAINMENT_BLOCK,
            "clinic", "event", "airlock"));
    // Solids R24 allows outside every room, per generator.
    private static final Map<String, Set<Character>> HALL_SOLIDS = new HashMap<>();
    static {
        HALL_SOLIDS.put(GeneratorId.ROOMS_MST.name(),       chars("PE"));
        HALL_SOLIDS.put(GeneratorId.LINEAR_CORRIDOR.name(), chars("PE"));
        HALL_SOLIDS.put(GeneratorId.CAVERN.name(),          chars("PE&LgCI%J"));
    }
    private static final Set<String> COMBAT = new HashSet<>(Arrays.asList(
            GeneratorId.ROOMS_MST.name(), GeneratorId.LINEAR_CORRIDOR.name(), GeneratorId.CAVERN.name()));

    private static final float FILL_CAP_REGULAR = 0.12f;
    private static final float FILL_CAP_SPECIAL = 0.22f;
    private static final float FILL_CAP_DENSE   = 0.40f;

    private LevelQualityAudit() {}

    /** Everything counted for one generator. */
    private static final class Tally {
        final String name;
        long floors, flankFailures, demotions, illegalOpenings, clearanceSolids, laneSolids, splitRooms;
        long unreachableTiles, floorsWithUnreachable, pluggedCleared, straySolids, misplacedTerminals;
        long floorsWithoutTerminal, plannedMismatchFloors, floorsBelowTwoSpecials, floorsWithDuplicates;
        long placedSpecials, specialsWithoutSignature, repairCarves, doors, offSpriteSymbols, offSpriteTerminalFloors;
        long rooms, tightRooms, floorsOverOneTight, roomsOverCap, entrancesDressed, reportedFloors;
        final Map<String, double[]> fillByRole = new TreeMap<>(); // role -> {sum, count, max}
        final Map<String, Integer> specialCounts = new TreeMap<>();
        final Map<String, Integer> specialCountsDepthOne = new TreeMap<>();
        int dumped;
        final List<String> examples = new ArrayList<>();

        Tally(String name) {
            this.name = name;
        }

        void add(Tally other) {
            floors += other.floors; flankFailures += other.flankFailures; demotions += other.demotions;
            illegalOpenings += other.illegalOpenings; clearanceSolids += other.clearanceSolids;
            laneSolids += other.laneSolids; splitRooms += other.splitRooms; unreachableTiles += other.unreachableTiles;
            floorsWithUnreachable += other.floorsWithUnreachable; pluggedCleared += other.pluggedCleared;
            straySolids += other.straySolids; misplacedTerminals += other.misplacedTerminals;
            floorsWithoutTerminal += other.floorsWithoutTerminal; plannedMismatchFloors += other.plannedMismatchFloors;
            floorsBelowTwoSpecials += other.floorsBelowTwoSpecials; floorsWithDuplicates += other.floorsWithDuplicates;
            placedSpecials += other.placedSpecials; specialsWithoutSignature += other.specialsWithoutSignature;
            repairCarves += other.repairCarves; doors += other.doors; offSpriteSymbols += other.offSpriteSymbols;
            offSpriteTerminalFloors += other.offSpriteTerminalFloors; rooms += other.rooms;
            tightRooms += other.tightRooms; floorsOverOneTight += other.floorsOverOneTight;
            roomsOverCap += other.roomsOverCap; entrancesDressed += other.entrancesDressed;
            reportedFloors += other.reportedFloors;
            for (Map.Entry<String, double[]> entry : other.fillByRole.entrySet()) {
                double[] mine = fillByRole.computeIfAbsent(entry.getKey(), key -> new double[3]);
                mine[0] += entry.getValue()[0];
                mine[1] += entry.getValue()[1];
                mine[2] = Math.max(mine[2], entry.getValue()[2]);
            }
            for (Map.Entry<String, Integer> entry : other.specialCounts.entrySet()) {
                specialCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
            for (Map.Entry<String, Integer> entry : other.specialCountsDepthOne.entrySet()) {
                specialCountsDepthOne.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }

        void recordFill(String role, double fill) {
            double[] slot = fillByRole.computeIfAbsent(role, key -> new double[3]);
            slot[0] += fill;
            slot[1] += 1;
            slot[2] = Math.max(slot[2], fill);
        }
    }

    /** How many violation locations to print per generator (--explain K). */
    private static int explainLimit;
    private static String currentFloor = "";
    private static Level  currentLevel;
    private static File   explainDumps;
    private static final Set<String> explainedFloors = new HashSet<>();

    // Records a violation location (and, once per floor, an ASCII dump of that floor under dumps/explain/).
    private static void explain(Tally tally, String what, int tileColumn, int tileRow) {
        if (tally.examples.size() >= explainLimit) return;
        tally.examples.add(currentFloor + " " + what + " at (" + tileColumn + "," + tileRow + ")");
        if (explainDumps == null || !explainedFloors.add(currentFloor)) return;
        try {
            writeDump(new File(explainDumps, currentFloor.replace(' ', '_').replace('=', '-') + ".txt"), currentLevel);
        } catch (IOException ignored) {
            // A dump is a convenience; the summary still lists the location.
        }
    }

    public static void main(String[] args) throws IOException {
        int seeds = 200;
        int dumpsPerGenerator = 0;
        int[] depths = { 1, 5, 10, 15, 20, 25 };
        List<String> generators = new ArrayList<>();
        for (GeneratorId id : GeneratorId.values()) generators.add(id.name());
        generators.add(START_ROOM);
        String outDirectory = "core/build/reports/level-audit";
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if (argument.equals("--seeds")) seeds = Integer.parseInt(args[++index]);
            else if (argument.equals("--dump")) dumpsPerGenerator = Integer.parseInt(args[++index]);
            else if (argument.equals("--out")) outDirectory = args[++index];
            else if (argument.equals("--explain")) explainLimit = Integer.parseInt(args[++index]);
            else if (argument.equals("--depths")) {
                String[] parts = args[++index].split(",");
                depths = new int[parts.length];
                for (int part = 0; part < parts.length; part++) depths[part] = Integer.parseInt(parts[part].trim());
            } else if (argument.equals("--generators")) {
                generators.clear();
                for (String part : args[++index].split(",")) generators.add(part.trim().toUpperCase(Locale.ROOT));
            }
        }
        RoomBlueprints.bootstrap();
        File dumpDirectory = new File(outDirectory, "dumps");
        if (explainLimit > 0) {
            explainDumps = new File(dumpDirectory, "explain");
            if (!explainDumps.isDirectory() && !explainDumps.mkdirs()) explainDumps = null;
        }
        if (dumpsPerGenerator > 0 && !dumpDirectory.isDirectory() && !dumpDirectory.mkdirs()) {
            throw new IOException("cannot create " + dumpDirectory);
        }

        List<Tally> tallies = new ArrayList<>();
        Tally pooled = new Tally("POOLED");
        for (String generator : generators) {
            Tally tally = new Tally(generator);
            for (int depth : depths) {
                for (long seed = 1; seed <= seeds; seed++) {
                    currentFloor = generator + " seed=" + seed + " depth=" + depth;
                    Level level = build(generator, seed, depth);
                    currentLevel = level;
                    analyse(level, depth, tally);
                    if (tally.dumped < dumpsPerGenerator) {
                        tally.dumped++;
                        writeDump(new File(dumpDirectory, generator.toLowerCase(Locale.ROOT) + "_s" + seed
                                + "_d" + depth + ".txt"), level);
                    }
                }
            }
            tallies.add(tally);
            pooled.add(tally);
        }

        StringWriter text = new StringWriter();
        PrintWriter out = new PrintWriter(text);
        out.printf(Locale.ROOT, "LEVEL QUALITY AUDIT  seeds=%d depths=%s%n", seeds, Arrays.toString(depths));
        for (Tally tally : tallies) print(out, tally);
        print(out, pooled);
        out.flush();
        File summary = new File(outDirectory, "summary.txt");
        if (!summary.getParentFile().isDirectory() && !summary.getParentFile().mkdirs()) {
            throw new IOException("cannot create " + summary.getParentFile());
        }
        try (FileWriter writer = new FileWriter(summary)) {
            writer.write(text.toString());
        }
        System.out.print(text);
        System.out.println("written: " + summary.getPath());
    }

    /** Builds one floor exactly as World does: the run seed's floor seed for that depth (GameMath.floorSeed). */
    private static Level build(String generator, long seed, int depth) {
        long floorSeed = ge.tbegvadze.toon3d.util.GameMath.floorSeed(seed, depth);
        if (generator.equals(START_ROOM)) return new StartGameLevelGenerator(floorSeed).generate();
        GeneratorId id = GeneratorId.valueOf(generator);
        return RouteRegistries.generators().create(id, floorSeed, new LevelGenConfig()).generate(depth);
    }

    // -------------------------------------------------------------------------
    // One floor
    // -------------------------------------------------------------------------

    private static void analyse(Level level, int depth, Tally tally) {
        tally.floors++;
        int width  = level.getWidth();
        int height = level.getHeight();
        char[][] grid = new char[height][width];
        for (int tileRow = 0; tileRow < height; tileRow++) {
            for (int tileColumn = 0; tileColumn < width; tileColumn++) {
                grid[tileRow][tileColumn] = level.getCell(tileColumn, tileRow);
            }
        }
        LevelLayoutReport layout = level.getLayoutReport();
        List<LevelLayoutReport.RoomRecord> rooms = layout != null ? layout.rooms()
                                                                  : new ArrayList<>();
        if (layout != null) {
            tally.reportedFloors++;
            tally.demotions    += layout.doorwayDemotions();
            tally.repairCarves += layout.repairCarves();
        }
        FloorContentReport content = level.getFloorContentReport();
        if (content != null) tally.pluggedCleared += content.pluggedPassagesCleared();
        if (content != null && content.pluggedPassagesCleared() > 0) explain(tally, "I7 passage sweep removed "
                + content.pluggedPassagesCleared(), -1, -1);
        if (layout != null && layout.repairCarves() > 0) explain(tally, "I14 repair carves " + layout.repairCarves(), -1, -1);

        // --- Doors (I1, I15) and their clearance zones (I3).
        boolean[][] clearance = new boolean[height][width];
        for (int tileRow = 0; tileRow < height; tileRow++) {
            for (int tileColumn = 0; tileColumn < width; tileColumn++) {
                if (!Level.isDoor(grid[tileRow][tileColumn])) continue;
                tally.doors++;
                if (!doorPassesFlankRule(grid, tileColumn, tileRow)) {
                    tally.flankFailures++;
                    explain(tally, "I1 door", tileColumn, tileRow);
                }
                boolean verticalPassage = isWall(grid, tileColumn - 1, tileRow) && isWall(grid, tileColumn + 1, tileRow);
                boolean horizontalPassage = isWall(grid, tileColumn, tileRow - 1) && isWall(grid, tileColumn, tileRow + 1);
                markClearance(clearance, tileColumn, tileRow, verticalPassage, horizontalPassage);
            }
        }
        for (LevelLayoutReport.RoomRecord room : rooms) {
            for (int[] opening : shellOpenings(grid, room)) {
                boolean onHorizontalSide = opening[1] == room.bottomRow || opening[1] == room.topRow;
                markClearance(clearance, opening[0], opening[1], onHorizontalSide, !onHorizontalSide);
            }
        }
        for (int tileRow = 0; tileRow < height; tileRow++) {
            for (int tileColumn = 0; tileColumn < width; tileColumn++) {
                if (clearance[tileRow][tileColumn] && isSolid(grid[tileRow][tileColumn])) {
                    tally.clearanceSolids++;
                    explain(tally, "I3 '" + grid[tileRow][tileColumn] + "'", tileColumn, tileRow);
                }
            }
        }

        // --- Reachability (I6).
        int unreachable = countUnreachable(grid);
        tally.unreachableTiles += unreachable;
        if (unreachable > 0) tally.floorsWithUnreachable++;

        // --- Rooms (I2, I4, I5, I12, I13, I16) and the special plan (I10, I11).
        Map<String, Integer> specialsThisFloor = new HashMap<>();
        int tightThisFloor = 0;
        RoomBlueprintRegistry registry = RoomBlueprints.rooms();
        LevelPalette palette = level.getPalette();
        for (LevelLayoutReport.RoomRecord room : rooms) {
            tally.rooms++;
            tally.illegalOpenings += countIllegalOpenings(grid, room, tally);
            boolean dense = room.kitId != null ? room.dense : DENSE_BLUEPRINTS.contains(room.blueprintId);
            List<int[]> band = room.laneBand != null ? room.laneBand
                    : computeLaneBand(grid, room, dense ? 0 : 1);
            for (int[] tile : band) {
                if (isSolid(grid[tile[1]][tile[0]])) tally.laneSolids++;
            }
            if (openInteriorComponents(grid, room) > 1) tally.splitRooms++;

            int solids = 0, open = 0, decals = 0;
            for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
                for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                    char cell = grid[tileRow][tileColumn];
                    if (Level.isWall(cell)) continue;
                    open++;
                    // Group barrels (GroupBarrelPlacer, balance-owned, R26) are not furniture: only the atmospheric
                    // plant's own cylinders count toward its fill.
                    boolean groupBarrel = cell == 'E' && !RoomBlueprints.ID_ATMOSPHERIC_PLANT.equals(room.blueprintId);
                    if (isSolid(cell) && !groupBarrel) solids++;
                    // R22 is about furniture: environment decals only (a supply pickup may land in the entrance).
                    if (ge.tbegvadze.toon3d.tileset.SymbolCategories.categoryOf(cell)
                            == ge.tbegvadze.toon3d.tileset.TileCategory.FLOOR_DECAL) decals++;
                }
            }
            double fill = open > 0 ? (double) solids / open : 0d;
            tally.recordFill(room.role.name() + (dense ? "_DENSE" : ""), fill);
            if (room.role == LevelLayoutReport.Role.ENTRANCE && (solids > 0 || decals > 0)) tally.entrancesDressed++;
            if (dense) {
                tally.tightRooms++;
                tightThisFloor++;
            }
            float cap = room.role == LevelLayoutReport.Role.REGULAR ? FILL_CAP_REGULAR
                      : dense ? FILL_CAP_DENSE : FILL_CAP_SPECIAL;
            if (room.role != LevelLayoutReport.Role.ENTRANCE && fill > cap + 1e-6) {
                tally.roomsOverCap++;
                explain(tally, "I12 over cap " + room.blueprintId + "/" + room.kitId, room.leftColumn, room.bottomRow);
            }

            if (room.role == LevelLayoutReport.Role.SPECIAL) {
                tally.placedSpecials++;
                specialsThisFloor.merge(room.blueprintId, 1, Integer::sum);
                tally.specialCounts.merge(room.blueprintId, 1, Integer::sum);
                if (depth == 1) tally.specialCountsDepthOne.merge(room.blueprintId, 1, Integer::sum);
                char[] signature = signatureSymbolsOf(room.blueprintId);
                boolean signed = signature == null;
                if (signature != null) for (char symbol : signature) signed |= interiorContains(grid, room, symbol);
                if (!signed) {
                    tally.specialsWithoutSignature++;
                    explain(tally, "I13 " + room.blueprintId + " without '" + new String(signature) + "'",
                            room.leftColumn, room.bottomRow);
                }
            }
            // I16: a symbol the room claims must render the sprite it claims.
            if (registry.contains(room.blueprintId)) {
                RoomSymbolDemand demand = registry.get(room.blueprintId).symbolDemand();
                for (Map.Entry<Character, String> claim : demand.requiredSprites().entrySet()) {
                    if (!interiorContains(grid, room, claim.getKey())) continue;
                    if (!claim.getValue().equals(palette.spriteIdOf(claim.getKey()))) tally.offSpriteSymbols++;
                }
            }
        }
        if (tightThisFloor > 1) tally.floorsOverOneTight++;
        if (layout != null && COMBAT.contains(tally.name)) {
            int placed = 0;
            boolean duplicate = false;
            for (int count : specialsThisFloor.values()) {
                placed += count;
                if (count > 1) duplicate = true;
            }
            if (duplicate) tally.floorsWithDuplicates++;
            if (placed < 2) tally.floorsBelowTwoSpecials++;
            List<String> planned = layout.plannedSpecials();
            if (planned != null && planned.size() != placed) tally.plannedMismatchFloors++;
        }

        // --- Stray solids outside rooms (I8).
        Set<Character> hallSolids = HALL_SOLIDS.get(tally.name);
        if (layout != null && hallSolids != null) {
            for (int tileRow = 0; tileRow < height; tileRow++) {
                for (int tileColumn = 0; tileColumn < width; tileColumn++) {
                    char cell = grid[tileRow][tileColumn];
                    if (!isSolid(cell) || hallSolids.contains(cell)) continue;
                    if (roomContaining(rooms, tileColumn, tileRow) == null) tally.straySolids++;
                }
            }
        }

        // --- Terminals (I9, I16 for 'T').
        boolean usableTerminal = false;
        boolean anyTerminal = false;
        for (int tileRow = 0; tileRow < height; tileRow++) {
            for (int tileColumn = 0; tileColumn < width; tileColumn++) {
                if (grid[tileRow][tileColumn] != 'T') continue;
                anyTerminal = true;
                LevelLayoutReport.RoomRecord room = roomContaining(rooms, tileColumn, tileRow);
                boolean misplaced = clearance[tileRow][tileColumn];
                boolean racks = room != null && RoomBlueprints.ID_SERVER_ROOM.equals(room.blueprintId);
                if (!racks && !hasWallNeighbour(grid, tileColumn, tileRow)) misplaced = true;
                if (layout != null && !rooms.isEmpty()) {
                    if (room == null || room.role == LevelLayoutReport.Role.ENTRANCE) misplaced = true;
                    else {
                        String owner = room.kitId != null ? room.kitId : room.blueprintId;
                        if (!TERMINAL_ROOMS.contains(owner) && !TERMINAL_ROOMS.contains(room.blueprintId)) misplaced = true;
                    }
                }
                if (misplaced) {
                    tally.misplacedTerminals++;
                    explain(tally, "I9 'T'", tileColumn, tileRow);
                }
                if (hasPassableNeighbour(grid, tileColumn, tileRow)) usableTerminal = true;
            }
        }
        if (COMBAT.contains(tally.name) && !usableTerminal) {
            tally.floorsWithoutTerminal++;
            explain(tally, "I9 no usable 'T' (any 'T'=" + anyTerminal + ")", -1, -1);
        }
        if (anyTerminal && !"prop_terminal".equals(palette.spriteIdOf('T'))) tally.offSpriteTerminalFloors++;
    }

    /** The SIGNATURE row's symbols of a registered blueprint (row 1 of its kit), or null when it declares none. */
    private static char[] signatureSymbolsOf(String blueprintId) {
        RoomBlueprintRegistry registry = RoomBlueprints.rooms();
        if (!registry.contains(blueprintId)) return null;
        PropKit kit = registry.get(blueprintId).kit();
        return kit == null || kit.signatureRow() == null ? null : kit.signatureRow().symbols();
    }

    // -------------------------------------------------------------------------
    // Geometry helpers
    // -------------------------------------------------------------------------

    private static boolean doorPassesFlankRule(char[][] grid, int tileColumn, int tileRow) {
        boolean wallsEastWest   = isWall(grid, tileColumn - 1, tileRow) && isWall(grid, tileColumn + 1, tileRow);
        boolean wallsNorthSouth = isWall(grid, tileColumn, tileRow - 1) && isWall(grid, tileColumn, tileRow + 1);
        boolean openEastWest    = isPassable(grid, tileColumn - 1, tileRow) && isPassable(grid, tileColumn + 1, tileRow);
        boolean openNorthSouth  = isPassable(grid, tileColumn, tileRow - 1) && isPassable(grid, tileColumn, tileRow + 1);
        boolean flanked = (wallsEastWest && openNorthSouth) || (wallsNorthSouth && openEastWest);
        boolean touchesDoor = isDoorAt(grid, tileColumn - 1, tileRow) || isDoorAt(grid, tileColumn + 1, tileRow)
                || isDoorAt(grid, tileColumn, tileRow - 1) || isDoorAt(grid, tileColumn, tileRow + 1);
        return flanked && !touchesDoor;
    }

    /** Chebyshev 1 around the socket plus two tiles straight out along its passage axis on both sides. */
    private static void markClearance(boolean[][] clearance, int tileColumn, int tileRow,
                                      boolean verticalPassage, boolean horizontalPassage) {
        for (int differenceRow = -1; differenceRow <= 1; differenceRow++) {
            for (int differenceColumn = -1; differenceColumn <= 1; differenceColumn++) {
                mark(clearance, tileColumn + differenceColumn, tileRow + differenceRow);
            }
        }
        if (verticalPassage) {
            mark(clearance, tileColumn, tileRow + 2);
            mark(clearance, tileColumn, tileRow - 2);
        }
        if (horizontalPassage) {
            mark(clearance, tileColumn + 2, tileRow);
            mark(clearance, tileColumn - 2, tileRow);
        }
    }

    private static void mark(boolean[][] mask, int tileColumn, int tileRow) {
        if (tileRow >= 0 && tileRow < mask.length && tileColumn >= 0 && tileColumn < mask[0].length) {
            mask[tileRow][tileColumn] = true;
        }
    }

    /** Every non-wall tile of the room's shell ring (a door or an opening). */
    private static List<int[]> shellOpenings(char[][] grid, LevelLayoutReport.RoomRecord room) {
        List<int[]> openings = new ArrayList<>();
        for (int tileColumn = room.leftColumn; tileColumn <= room.rightColumn; tileColumn++) {
            addOpening(grid, openings, tileColumn, room.bottomRow);
            addOpening(grid, openings, tileColumn, room.topRow);
        }
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            addOpening(grid, openings, room.leftColumn, tileRow);
            addOpening(grid, openings, room.rightColumn, tileRow);
        }
        return openings;
    }

    private static void addOpening(char[][] grid, List<int[]> openings, int tileColumn, int tileRow) {
        if (!inBounds(grid, tileColumn, tileRow)) return;
        if (!Level.isWall(grid[tileRow][tileColumn])) openings.add(new int[]{ tileColumn, tileRow });
    }

    /** I2: shell openings that are not legal sockets (R10 / R11). */
    private static int countIllegalOpenings(char[][] grid, LevelLayoutReport.RoomRecord room, Tally tally) {
        List<int[]> openings = shellOpenings(grid, room);
        int illegal = 0;
        for (int[] opening : openings) {
            int column = opening[0];
            int row    = opening[1];
            boolean onHorizontalSide = row == room.bottomRow || row == room.topRow;
            int distanceToCorner = onHorizontalSide
                    ? Math.min(column - room.leftColumn, room.rightColumn - column)
                    : Math.min(row - room.bottomRow, room.topRow - row);
            boolean legal = distanceToCorner >= 2;
            for (int[] other : openings) {
                if (other == opening) continue;
                boolean sameSide = onHorizontalSide ? other[1] == row : other[0] == column;
                int spacing = onHorizontalSide ? Math.abs(other[0] - column) : Math.abs(other[1] - row);
                if (sameSide && spacing < 3) legal = false;
            }
            int outerColumn = column, outerRow = row;
            if (row == room.bottomRow) outerRow--;
            else if (row == room.topRow) outerRow++;
            else if (column == room.leftColumn) outerColumn--;
            else outerColumn++;
            if (!isPassable(grid, outerColumn, outerRow)) legal = false;
            if (!legal) {
                illegal++;
                explain(tally, "I2 opening of " + room.blueprintId, column, row);
            }
        }
        return illegal;
    }

    /**
     * The audit's own lane band for a room whose generator reported none: for each opening, the shortest
     * 4-connected interior path from its inner tile to the interior centre (ignoring props, fixed direction
     * order), widened by {@code halfWidth} to orthogonal interior neighbours.
     */
    private static List<int[]> computeLaneBand(char[][] grid, LevelLayoutReport.RoomRecord room, int halfWidth) {
        int hubColumn = (room.leftColumn + room.rightColumn) / 2;
        int hubRow    = (room.bottomRow + room.topRow) / 2;
        Set<Long> band = new HashSet<>();
        for (int[] opening : shellOpenings(grid, room)) {
            int innerColumn = opening[0], innerRow = opening[1];
            if (opening[1] == room.bottomRow) innerRow++;
            else if (opening[1] == room.topRow) innerRow--;
            else if (opening[0] == room.leftColumn) innerColumn++;
            else innerColumn--;
            if (!room.containsInterior(innerColumn, innerRow)) continue;
            for (int[] tile : interiorPath(grid, room, innerColumn, innerRow, hubColumn, hubRow)) {
                band.add(key(tile[0], tile[1]));
                if (halfWidth <= 0) continue;
                int[][] neighbours = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
                for (int[] step : neighbours) {
                    int column = tile[0] + step[0], row = tile[1] + step[1];
                    if (room.containsInterior(column, row)) band.add(key(column, row));
                }
            }
        }
        List<int[]> tiles = new ArrayList<>();
        for (long packed : band) {
            int row = (int) (packed >> 32);
            int column = (int) packed;
            if (!Level.isWall(grid[row][column])) tiles.add(new int[]{ column, row });
        }
        return tiles;
    }

    private static List<int[]> interiorPath(char[][] grid, LevelLayoutReport.RoomRecord room,
                                            int fromColumn, int fromRow, int toColumn, int toRow) {
        Map<Long, Long> parent = new HashMap<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        long start = key(fromColumn, fromRow);
        parent.put(start, start);
        queue.add(new int[]{ fromColumn, fromRow });
        int[][] steps = { { 0, 1 }, { 0, -1 }, { 1, 0 }, { -1, 0 } };
        boolean found = false;
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            if (current[0] == toColumn && current[1] == toRow) {
                found = true;
                break;
            }
            for (int[] step : steps) {
                int column = current[0] + step[0], row = current[1] + step[1];
                if (!room.containsInterior(column, row) || Level.isWall(grid[row][column])) continue;
                long packed = key(column, row);
                if (parent.containsKey(packed)) continue;
                parent.put(packed, key(current[0], current[1]));
                queue.add(new int[]{ column, row });
            }
        }
        List<int[]> path = new ArrayList<>();
        if (!found) return path;
        long cursor = key(toColumn, toRow);
        while (true) {
            path.add(new int[]{ (int) cursor, (int) (cursor >> 32) });
            long previous = parent.get(cursor);
            if (previous == cursor) break;
            cursor = previous;
        }
        return path;
    }

    /** I5: 4-connected regions of the room's open (standable) interior tiles. */
    private static int openInteriorComponents(char[][] grid, LevelLayoutReport.RoomRecord room) {
        Set<Long> seen = new HashSet<>();
        int components = 0;
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (!isPassable(grid, tileColumn, tileRow) || seen.contains(key(tileColumn, tileRow))) continue;
                components++;
                ArrayDeque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{ tileColumn, tileRow });
                seen.add(key(tileColumn, tileRow));
                while (!queue.isEmpty()) {
                    int[] current = queue.poll();
                    int[][] steps = { { 0, 1 }, { 0, -1 }, { 1, 0 }, { -1, 0 } };
                    for (int[] step : steps) {
                        int column = current[0] + step[0], row = current[1] + step[1];
                        if (!room.containsInterior(column, row) || !isPassable(grid, column, row)) continue;
                        if (seen.add(key(column, row))) queue.add(new int[]{ column, row });
                    }
                }
            }
        }
        return components;
    }

    /** I6: standable tiles not reachable from the player start. */
    private static int countUnreachable(char[][] grid) {
        int height = grid.length, width = grid[0].length;
        int startColumn = -1, startRow = -1;
        for (int tileRow = 0; tileRow < height && startColumn < 0; tileRow++) {
            for (int tileColumn = 0; tileColumn < width; tileColumn++) {
                if (grid[tileRow][tileColumn] == 'p') {
                    startColumn = tileColumn;
                    startRow = tileRow;
                    break;
                }
            }
        }
        if (startColumn < 0) return 0;
        boolean[][] reached = new boolean[height][width];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        reached[startRow][startColumn] = true;
        queue.add(new int[]{ startColumn, startRow });
        int[][] steps = { { 0, 1 }, { 0, -1 }, { 1, 0 }, { -1, 0 } };
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            for (int[] step : steps) {
                int column = current[0] + step[0], row = current[1] + step[1];
                if (!isPassable(grid, column, row) || reached[row][column]) continue;
                reached[row][column] = true;
                queue.add(new int[]{ column, row });
            }
        }
        int unreachable = 0;
        for (int tileRow = 0; tileRow < height; tileRow++) {
            for (int tileColumn = 0; tileColumn < width; tileColumn++) {
                if (isPassable(grid, tileColumn, tileRow) && !reached[tileRow][tileColumn]) unreachable++;
            }
        }
        return unreachable;
    }

    private static LevelLayoutReport.RoomRecord roomContaining(List<LevelLayoutReport.RoomRecord> rooms,
                                                              int tileColumn, int tileRow) {
        for (LevelLayoutReport.RoomRecord room : rooms) {
            if (room.containsInterior(tileColumn, tileRow)) return room;
        }
        return null;
    }

    private static boolean interiorContains(char[][] grid, LevelLayoutReport.RoomRecord room, char symbol) {
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] == symbol) return true;
            }
        }
        return false;
    }

    private static boolean hasWallNeighbour(char[][] grid, int tileColumn, int tileRow) {
        return isWall(grid, tileColumn + 1, tileRow) || isWall(grid, tileColumn - 1, tileRow)
            || isWall(grid, tileColumn, tileRow + 1) || isWall(grid, tileColumn, tileRow - 1);
    }

    private static boolean hasPassableNeighbour(char[][] grid, int tileColumn, int tileRow) {
        return isPassable(grid, tileColumn + 1, tileRow) || isPassable(grid, tileColumn - 1, tileRow)
            || isPassable(grid, tileColumn, tileRow + 1) || isPassable(grid, tileColumn, tileRow - 1);
    }

    private static boolean isSolid(char cell) {
        return Level.isPropSolid(cell) || Level.isColumn(cell);
    }

    private static boolean isWall(char[][] grid, int tileColumn, int tileRow) {
        return !inBounds(grid, tileColumn, tileRow) || Level.isWall(grid[tileRow][tileColumn]);
    }

    private static boolean isDoorAt(char[][] grid, int tileColumn, int tileRow) {
        return inBounds(grid, tileColumn, tileRow) && Level.isDoor(grid[tileRow][tileColumn]);
    }

    /** Standable the way the player moves: walls, solid props and columns block. */
    private static boolean isPassable(char[][] grid, int tileColumn, int tileRow) {
        if (!inBounds(grid, tileColumn, tileRow)) return false;
        char cell = grid[tileRow][tileColumn];
        return !Level.isWall(cell) && !isSolid(cell);
    }

    private static boolean inBounds(char[][] grid, int tileColumn, int tileRow) {
        return tileRow >= 0 && tileRow < grid.length && tileColumn >= 0 && tileColumn < grid[0].length;
    }

    private static long key(int tileColumn, int tileRow) {
        return ((long) tileRow << 32) | (tileColumn & 0xFFFFFFFFL);
    }

    private static Set<Character> chars(String symbols) {
        Set<Character> set = new HashSet<>();
        for (char symbol : symbols.toCharArray()) set.add(symbol);
        return set;
    }

    // -------------------------------------------------------------------------
    // Output
    // -------------------------------------------------------------------------

    private static void print(PrintWriter out, Tally tally) {
        double floors = Math.max(1, tally.floors);
        out.printf(Locale.ROOT, "%n== %s  floors=%d (layout reported on %d)%n", tally.name, tally.floors,
                tally.reportedFloors);
        out.printf(Locale.ROOT, "  I1  doors failing flank rule=%d  validator demotions=%d%n",
                tally.flankFailures, tally.demotions);
        out.printf(Locale.ROOT, "  I2  illegal shell openings=%d%n", tally.illegalOpenings);
        out.printf(Locale.ROOT, "  I3  solids in a door clearance zone=%d%n", tally.clearanceSolids);
        out.printf(Locale.ROOT, "  I4  solids in a lane band=%d%n", tally.laneSolids);
        out.printf(Locale.ROOT, "  I5  split rooms=%d%n", tally.splitRooms);
        out.printf(Locale.ROOT, "  I6  unreachable standable tiles=%d (floors=%d)%n", tally.unreachableTiles,
                tally.floorsWithUnreachable);
        out.printf(Locale.ROOT, "  I7  plugging solids (cleared by the passage sweep)=%d%n", tally.pluggedCleared);
        out.printf(Locale.ROOT, "  I8  stray solids outside rooms=%d%n", tally.straySolids);
        out.printf(Locale.ROOT, "  I9  misplaced 'T'=%d  combat floors without a usable 'T'=%d%n",
                tally.misplacedTerminals, tally.floorsWithoutTerminal);
        out.printf(Locale.ROOT, "  I10 specials placed=%d (%.2f/floor)  planned!=placed floors=%d  floors<2=%d"
                + "  floors with duplicates=%d%n", tally.placedSpecials, tally.placedSpecials / floors,
                tally.plannedMismatchFloors, tally.floorsBelowTwoSpecials, tally.floorsWithDuplicates);
        out.printf(Locale.ROOT, "  I11 per type: %s%n", spread(tally.specialCounts));
        out.printf(Locale.ROOT, "      depth 1:  %s%n", spread(tally.specialCountsDepthOne));
        StringBuilder fill = new StringBuilder();
        for (Map.Entry<String, double[]> entry : tally.fillByRole.entrySet()) {
            double[] value = entry.getValue();
            fill.append(String.format(Locale.ROOT, "%s mean=%.3f max=%.3f  ", entry.getKey(),
                    value[1] > 0 ? value[0] / value[1] : 0d, value[2]));
        }
        out.printf(Locale.ROOT, "  I12 fill %s%n", fill);
        out.printf(Locale.ROOT, "      tight rooms=%d (%.1f%% of %d rooms)  floors with >1 tight=%d  rooms over cap=%d"
                + "  dressed entrances=%d%n", tally.tightRooms, 100d * tally.tightRooms / Math.max(1, tally.rooms),
                tally.rooms, tally.floorsOverOneTight, tally.roomsOverCap, tally.entrancesDressed);
        out.printf(Locale.ROOT, "  I13 placed specials without their signature=%d%n", tally.specialsWithoutSignature);
        out.printf(Locale.ROOT, "  I14 repair carves=%d  passage-sweep removals=%d%n", tally.repairCarves,
                tally.pluggedCleared);
        out.printf(Locale.ROOT, "  I15 doors per floor=%.2f%n", tally.doors / floors);
        out.printf(Locale.ROOT, "  I16 claimed symbols off-sprite=%d  floors whose 'T' is not a terminal=%d%n",
                tally.offSpriteSymbols, tally.offSpriteTerminalFloors);
        for (String example : tally.examples) out.println("    " + example);
    }

    /** "type=count ..." plus the largest deviation from the mean, over every registered special type. */
    private static String spread(Map<String, Integer> counts) {
        Map<String, Integer> all = new LinkedHashMap<>();
        for (RoomBlueprint blueprint : RoomBlueprints.rooms().all()) {
            String id = blueprint.id();
            if (id.equals(RoomBlueprints.ID_ENTRANCE) || id.equals(RoomBlueprints.ID_STANDARD)) continue;
            all.put(id, 0);
        }
        for (Map.Entry<String, Integer> entry : counts.entrySet()) all.merge(entry.getKey(), entry.getValue(), Integer::sum);
        int registered = 0;
        long total = 0;
        for (Map.Entry<String, Integer> entry : all.entrySet()) {
            if (!RoomBlueprints.rooms().contains(entry.getKey())) continue;
            registered++;
            total += entry.getValue();
        }
        double mean = registered > 0 ? (double) total / registered : 0d;
        double worst = 0d;
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Integer> entry : all.entrySet()) {
            text.append(entry.getKey()).append('=').append(entry.getValue()).append(' ');
            if (mean > 0 && RoomBlueprints.rooms().contains(entry.getKey())) {
                worst = Math.max(worst, Math.abs(entry.getValue() - mean) / mean);
            }
        }
        text.append(String.format(Locale.ROOT, "| mean=%.1f max deviation=%.0f%%", mean, worst * 100d));
        return text.toString();
    }

    private static void writeDump(File file, Level level) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            for (int tileRow = level.getHeight() - 1; tileRow >= 0; tileRow--) {
                StringBuilder line = new StringBuilder();
                for (int tileColumn = 0; tileColumn < level.getWidth(); tileColumn++) {
                    line.append(level.getCell(tileColumn, tileRow));
                }
                writer.write(line.append('\n').toString());
            }
        }
    }
}
