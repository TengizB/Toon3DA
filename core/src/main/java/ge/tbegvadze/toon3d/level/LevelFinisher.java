package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.tileset.LevelPalette;
import ge.tbegvadze.toon3d.tileset.RegionPaletteBuilder;
import ge.tbegvadze.toon3d.tileset.RegionWallSet;
import ge.tbegvadze.toon3d.tileset.RegionWallSets;
import ge.tbegvadze.toon3d.tileset.WallGroup;
import ge.tbegvadze.toon3d.tileset.WallSlot;
import ge.tbegvadze.toon3d.tileset.WallSlotTable;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.LevelGenConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * THE LEVEL FINISHER (generated-wall-art order 2): every generator's final grid goes through here before
 * it becomes a {@link Level} — rooms, corridor, cavern, boss arena, med bay, event room, gate airlock and
 * the start room. It is the ONLY place a wall picture is chosen. A wall symbol names a SLOT
 * ({@link WallSlotTable}); the floor's region set ({@link RegionWallSets}) names the picture.
 *
 * <p>What it does, in order (the placement rules of docs/wall-art-creation-guide.txt section 7):
 * <ol>
 *   <li>P8 — every wall symbol becomes the plain HALL BASE {@code 'x'}; only exposed walls (a non-wall
 *       4-neighbour) are dressed afterwards, and only a wall symbol ever replaces a wall symbol, so the
 *       walkable mask and every FIXED symbol are untouched (R1).</li>
 *   <li>P2 / P3 — a room's perimeter (the exposed walls whose open neighbour lies inside its interior)
 *       becomes its family's BASE, with the family ACCENT on 1-3 tiles, the wall facing the entrance first.
 *       A special floor names one group for every wall that no room claims (MED_BAY MEDICAL, gate
 *       SECURITY, event + boss SIGNATURE).</li>
 *   <li>P4 — along a straight exposed hall run of 5+ tiles every 4th tile is STRUCTURE (phase seeded per
 *       run); the walls flanking a door, in line with it, are STRUCTURE (door frames).</li>
 *   <li>P5 — no exposed hall run of 7 tiles without a LIGHT; never two LIGHTs adjacent.</li>
 *   <li>P6 — 1-3 MARKERs per floor, near the arrival point and the exit; SERVICE / VENT on 6-10% of the
 *       remaining hall base in 1-3 tile clusters.</li>
 *   <li>P7 — BREACH on breachBase + floorIndexInRegion x breachStep of the hall base (more in a cavern), in
 *       seeded clusters, never on a door frame.</li>
 *   <li>P9 — focal walls (LIGHT, MARKER, ACCENT) are never adjacent and keep 2+ tiles apart, 4+ for the same
 *       focal wall.</li>
 *   <li>P1 — the palette binds every wall symbol to the region set's picture (RegionPaletteBuilder).</li>
 * </ol>
 * It draws from its OWN random stream ({@link GameMath#wallSetSeed}), never the generator's, so the
 * generator's sequence — and with it layout, spawns and supply — is untouched. Same seed, same walls (R2).
 */
public final class LevelFinisher {

    private static final char PLAIN_WALL = 'x';
    private static final char ARRIVAL_SYMBOL = 'p';
    private static final int[] NEIGHBOUR_COLUMN = { 0, 0, 1, -1 };
    private static final int[] NEIGHBOUR_ROW    = { 1, -1, 0, 0 };

    /** A room the finisher dresses: its interior rectangle (inclusive) and the wall group it names. */
    public static final class FinishedRoom {
        final int interiorLeft;
        final int interiorBottom;
        final int interiorRight;
        final int interiorTop;
        final WallGroup group;

        public FinishedRoom(int interiorLeft, int interiorBottom, int interiorRight, int interiorTop, WallGroup group) {
            this.interiorLeft   = interiorLeft;
            this.interiorBottom = interiorBottom;
            this.interiorRight  = interiorRight;
            this.interiorTop    = interiorTop;
            this.group          = group;
        }

        boolean containsInterior(int tileColumn, int tileRow) {
            return tileColumn >= interiorLeft && tileColumn <= interiorRight
                && tileRow >= interiorBottom && tileRow <= interiorTop;
        }

        boolean isTiny() {
            return interiorRight - interiorLeft + 1 < LevelGenConstants.WALL_SET_TINY_ROOM_INTERIOR
                || interiorTop - interiorBottom + 1 < LevelGenConstants.WALL_SET_TINY_ROOM_INTERIOR;
        }
    }

    private LevelFinisher() {}

    /** Starts a finish of {@code grid} (indexed [row][column]) for a floor built from {@code levelSeed}. */
    public static Request request(char[][] grid, long levelSeed) {
        return new Request(grid, levelSeed);
    }

    /** The finisher's inputs; {@link #finish()} dresses the grid in place and returns the floor's palette. */
    public static final class Request {
        private final char[][] grid;
        private final long levelSeed;
        private final List<FinishedRoom> rooms = new ArrayList<>();
        private WallGroup floorGroup = WallGroup.HALL;
        private boolean cavern;
        private LevelGenConfig config;
        private LevelPalette basePalette;

        private Request(char[][] grid, long levelSeed) {
            this.grid      = grid;
            this.levelSeed = levelSeed;
        }

        /** A room with its interior rectangle (inclusive) and its family (a HALL room is not stamped). */
        public Request room(int interiorLeft, int interiorBottom, int interiorRight, int interiorTop, WallGroup group) {
            rooms.add(new FinishedRoom(interiorLeft, interiorBottom, interiorRight, interiorTop, group));
            return this;
        }

        /** P3: the group of every wall no room claims (HALL by default). */
        public Request floorGroup(WallGroup group) {
            this.floorGroup = group;
            return this;
        }

        /** P3: a cavern floor (HALL with BREACH weighted up). */
        public Request cavern() {
            this.cavern = true;
            return this;
        }

        /** The generator's config (carries the floor's wall set id and its floor index in the region). */
        public Request config(LevelGenConfig value) {
            this.config = value;
            return this;
        }

        /** The palette whose non-wall bindings (props, decals) the floor keeps. */
        public Request basePalette(LevelPalette palette) {
            this.basePalette = palette;
            return this;
        }

        public LevelPalette finish() {
            return LevelFinisher.finish(this);
        }
    }

    private static LevelPalette finish(Request request) {
        char[][] grid = request.grid;
        String setId = request.config != null ? request.config.wallSetId : null;
        RegionWallSet set = RegionWallSets.resolveOrDefault(setId);
        Random random = new Random(GameMath.splitMix64(GameMath.wallSetSeed(request.levelSeed)));

        int levelHeight = grid.length;
        int levelWidth  = levelHeight > 0 ? grid[0].length : 0;

        // P8: walls are plain until dressed; record which are exposed.
        boolean[][] exposed = new boolean[levelHeight][levelWidth];
        for (int tileRow = 0; tileRow < levelHeight; tileRow++) {
            for (int tileColumn = 0; tileColumn < levelWidth; tileColumn++) {
                if (!WallSlotTable.isWallSymbol(grid[tileRow][tileColumn])) continue;
                grid[tileRow][tileColumn] = PLAIN_WALL;
                exposed[tileRow][tileColumn] = hasOpenNeighbour(grid, tileColumn, tileRow);
            }
        }

        // P2 / P3: who owns each exposed wall. The first room whose interior the wall faces claims it; the
        // rest belong to the floor's group.
        WallGroup[][] owner = new WallGroup[levelHeight][levelWidth];
        int[][] ownerRoom   = new int[levelHeight][levelWidth];
        for (int tileRow = 0; tileRow < levelHeight; tileRow++) {
            for (int tileColumn = 0; tileColumn < levelWidth; tileColumn++) {
                ownerRoom[tileRow][tileColumn] = -1;
                if (!exposed[tileRow][tileColumn]) continue;
                owner[tileRow][tileColumn] = request.floorGroup;
                for (int roomIndex = 0; roomIndex < request.rooms.size(); roomIndex++) {
                    FinishedRoom room = request.rooms.get(roomIndex);
                    if (room.group == WallGroup.HALL || !facesInterior(grid, tileColumn, tileRow, room)) continue;
                    owner[tileRow][tileColumn]     = room.group;
                    ownerRoom[tileRow][tileColumn] = roomIndex;
                    break;
                }
            }
        }

        FocalSpacing focals = new FocalSpacing();

        // P2 / P3: family BASE on every wall a family owns.
        for (int tileRow = 0; tileRow < levelHeight; tileRow++) {
            for (int tileColumn = 0; tileColumn < levelWidth; tileColumn++) {
                WallGroup group = owner[tileRow][tileColumn];
                if (group == null || group == WallGroup.HALL) continue;
                grid[tileRow][tileColumn] = WallSlotTable.symbolFor(group, WallSlot.BASE);
            }
        }

        // P2: each family room's ACCENT; a special floor's walls count as one room of the floor group.
        for (int roomIndex = 0; roomIndex < request.rooms.size(); roomIndex++) {
            FinishedRoom room = request.rooms.get(roomIndex);
            if (room.group == WallGroup.HALL) continue;
            placeRoomAccents(grid, ownerRoom, roomIndex, room, random, focals);
        }
        if (request.floorGroup != WallGroup.HALL) {
            placeFloorAccents(grid, owner, ownerRoom, request.floorGroup, random, focals);
        }

        // P4-P7: the hall placement. Rhythm walls are the HALL's and, on a special floor, the walls of the
        // floor's own group that no room claims (a boss arena's SIGNATURE base still gets its frames and lights).
        boolean[][] rhythm = new boolean[levelHeight][levelWidth];
        for (int tileRow = 0; tileRow < levelHeight; tileRow++) {
            for (int tileColumn = 0; tileColumn < levelWidth; tileColumn++) {
                WallGroup group = owner[tileRow][tileColumn];
                rhythm[tileRow][tileColumn] = group == WallGroup.HALL
                        || group == request.floorGroup && ownerRoom[tileRow][tileColumn] == -1;
            }
        }
        char rhythmBase = WallSlotTable.symbolFor(request.floorGroup, WallSlot.BASE);
        List<List<int[]>> runs = findRuns(grid, rhythm);
        frameDoors(grid, exposed);
        placeRhythm(grid, runs, rhythmBase, random);
        placeLights(grid, runs, rhythmBase, random, focals);
        placeMarkers(grid, owner, random, focals);
        float breachDensity = set.breachBase()
                + (request.config != null ? request.config.floorIndexInRegion : 0) * set.breachStep()
                + (request.cavern ? LevelGenConstants.WALL_SET_CAVERN_BREACH_BONUS : 0f);
        placeHallClusters(grid, owner, breachDensity, new char[]{ symbol(WallSlot.BREACH) }, random);
        float varietyFraction = LevelGenConstants.WALL_SET_VARIETY_MIN_FRACTION + random.nextFloat()
                * (LevelGenConstants.WALL_SET_VARIETY_MAX_FRACTION - LevelGenConstants.WALL_SET_VARIETY_MIN_FRACTION);
        placeHallClusters(grid, owner, varietyFraction,
                new char[]{ symbol(WallSlot.SERVICE), symbol(WallSlot.VENT) }, random);

        LevelPalette base = request.basePalette != null
                ? request.basePalette : ge.tbegvadze.toon3d.tileset.LevelPalettes.legacy();
        return RegionPaletteBuilder.build(set, base);
    }

    // ---------------------------------------------------------------------------------------------
    // P2 — room accents
    // ---------------------------------------------------------------------------------------------

    private static void placeRoomAccents(char[][] grid, int[][] ownerRoom, int roomIndex, FinishedRoom room,
                                         Random random, FocalSpacing focals) {
        char accent = WallSlotTable.symbolFor(room.group, WallSlot.ACCENT);
        List<int[]> candidates = new ArrayList<>();
        for (int tileRow = room.interiorBottom - 1; tileRow <= room.interiorTop + 1; tileRow++) {
            for (int tileColumn = room.interiorLeft - 1; tileColumn <= room.interiorRight + 1; tileColumn++) {
                if (!inBounds(grid, tileColumn, tileRow) || ownerRoom[tileRow][tileColumn] != roomIndex) continue;
                if (isDoorFrame(grid, tileColumn, tileRow)) continue;
                candidates.add(new int[]{ tileColumn, tileRow });
            }
        }
        if (candidates.isEmpty()) return;
        int wanted = accentCount(room.isTiny(), random);
        int[] facing = wallFacingEntrance(grid, room, candidates);
        int placed = 0;
        if (facing != null && focals.allows(facing[0], facing[1], accent)) {
            place(grid, facing, accent, focals);
            placed++;
        }
        placed += scatterFocal(grid, candidates, accent, wanted - placed, random, focals);
    }

    private static void placeFloorAccents(char[][] grid, WallGroup[][] owner, int[][] ownerRoom, WallGroup floorGroup,
                                          Random random, FocalSpacing focals) {
        char accent = WallSlotTable.symbolFor(floorGroup, WallSlot.ACCENT);
        List<int[]> candidates = new ArrayList<>();
        for (int tileRow = 0; tileRow < grid.length; tileRow++) {
            for (int tileColumn = 0; tileColumn < grid[tileRow].length; tileColumn++) {
                if (owner[tileRow][tileColumn] != floorGroup || ownerRoom[tileRow][tileColumn] != -1) continue;
                if (isDoorFrame(grid, tileColumn, tileRow)) continue;
                candidates.add(new int[]{ tileColumn, tileRow });
            }
        }
        scatterFocal(grid, candidates, accent, accentCount(false, random), random, focals);
    }

    private static int accentCount(boolean tiny, Random random) {
        if (tiny) return 1;
        int span = LevelGenConstants.WALL_SET_ROOM_ACCENT_MAX - LevelGenConstants.WALL_SET_ROOM_ACCENT_MIN + 1;
        return LevelGenConstants.WALL_SET_ROOM_ACCENT_MIN + random.nextInt(span);
    }

    /**
     * The perimeter wall across the room from its first entrance (an open tile on the room's boundary
     * ring, scanned bottom-to-top, left-to-right): the nearest candidate on the opposite side, or null
     * when the room has no entrance or no wall there.
     */
    private static int[] wallFacingEntrance(char[][] grid, FinishedRoom room, List<int[]> candidates) {
        int ringLeft = room.interiorLeft - 1, ringRight = room.interiorRight + 1;
        int ringBottom = room.interiorBottom - 1, ringTop = room.interiorTop + 1;
        for (int tileRow = ringBottom; tileRow <= ringTop; tileRow++) {
            for (int tileColumn = ringLeft; tileColumn <= ringRight; tileColumn++) {
                boolean onRing = tileRow == ringBottom || tileRow == ringTop || tileColumn == ringLeft || tileColumn == ringRight;
                if (!onRing || !inBounds(grid, tileColumn, tileRow) || Level.isWall(grid[tileRow][tileColumn])) continue;
                int targetColumn = tileColumn, targetRow = tileRow;
                if (tileRow == ringBottom)       targetRow    = ringTop;
                else if (tileRow == ringTop)     targetRow    = ringBottom;
                else if (tileColumn == ringLeft) targetColumn = ringRight;
                else                             targetColumn = ringLeft;
                int[] best = null;
                int bestDistance = Integer.MAX_VALUE;
                for (int[] candidate : candidates) {
                    boolean sameSide = (targetRow == ringTop || targetRow == ringBottom) && candidate[1] == targetRow
                            || (targetColumn == ringLeft || targetColumn == ringRight) && candidate[0] == targetColumn;
                    if (!sameSide) continue;
                    int distance = Math.abs(candidate[0] - targetColumn) + Math.abs(candidate[1] - targetRow);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate;
                    }
                }
                return best;
            }
        }
        return null;
    }

    /** Places up to {@code wanted} copies of {@code focal} on random candidates that P9 allows. */
    private static int scatterFocal(char[][] grid, List<int[]> candidates, char focal, int wanted, Random random,
                                    FocalSpacing focals) {
        int placed = 0;
        if (wanted <= 0 || candidates.isEmpty()) return 0;
        int attempts = candidates.size() * 2;
        for (int attempt = 0; attempt < attempts && placed < wanted; attempt++) {
            int[] candidate = candidates.get(random.nextInt(candidates.size()));
            if (!focals.allows(candidate[0], candidate[1], focal)) continue;
            place(grid, candidate, focal, focals);
            placed++;
        }
        return placed;
    }

    private static void place(char[][] grid, int[] tile, char symbol, FocalSpacing focals) {
        grid[tile[1]][tile[0]] = symbol;
        focals.add(tile[0], tile[1], symbol);
    }

    // ---------------------------------------------------------------------------------------------
    // P4-P7 — the hall placement
    // ---------------------------------------------------------------------------------------------

    private static char symbol(WallSlot hallSlot) {
        return WallSlotTable.symbolFor(WallGroup.HALL, hallSlot);
    }

    /**
     * Every straight run of rhythm walls that share the same open side: for each of the four sides, the
     * maximal row (north / south) or column (east / west) sequences of rhythm walls open on that side.
     * A wall open on two sides belongs to two runs.
     */
    private static List<List<int[]>> findRuns(char[][] grid, boolean[][] rhythm) {
        List<List<int[]>> runs = new ArrayList<>();
        int levelHeight = grid.length;
        int levelWidth  = levelHeight > 0 ? grid[0].length : 0;
        for (int direction = 0; direction < 4; direction++) {
            int openColumn = NEIGHBOUR_COLUMN[direction];
            int openRow    = NEIGHBOUR_ROW[direction];
            boolean alongRow = openRow != 0;          // north / south faces run along a row
            int lineCount   = alongRow ? levelHeight : levelWidth;
            int lineLength  = alongRow ? levelWidth : levelHeight;
            for (int line = 0; line < lineCount; line++) {
                List<int[]> run = new ArrayList<>();
                for (int step = 0; step <= lineLength; step++) {
                    int tileColumn = alongRow ? step : line;
                    int tileRow    = alongRow ? line : step;
                    boolean member = step < lineLength && rhythm[tileRow][tileColumn]
                            && isOpen(grid, tileColumn + openColumn, tileRow + openRow);
                    if (member) {
                        run.add(new int[]{ tileColumn, tileRow });
                    } else if (!run.isEmpty()) {
                        runs.add(run);
                        run = new ArrayList<>();
                    }
                }
            }
        }
        return runs;
    }

    /** P4: the exposed walls flanking a door, in line with it (a door on a corner frames only those). */
    private static void frameDoors(char[][] grid, boolean[][] exposed) {
        char structure = symbol(WallSlot.STRUCTURE);
        for (int tileRow = 0; tileRow < grid.length; tileRow++) {
            for (int tileColumn = 0; tileColumn < grid[tileRow].length; tileColumn++) {
                if (!Level.isDoor(grid[tileRow][tileColumn])) continue;
                frameIfInLine(grid, exposed, tileColumn - 1, tileRow, tileColumn + 1, tileRow, structure);
                frameIfInLine(grid, exposed, tileColumn, tileRow - 1, tileColumn, tileRow + 1, structure);
            }
        }
    }

    private static void frameIfInLine(char[][] grid, boolean[][] exposed, int firstColumn, int firstRow,
                                      int secondColumn, int secondRow, char structure) {
        if (!isWallTile(grid, firstColumn, firstRow) || !isWallTile(grid, secondColumn, secondRow)) return;
        if (exposed[firstRow][firstColumn])   grid[firstRow][firstColumn]   = structure;
        if (exposed[secondRow][secondColumn]) grid[secondRow][secondColumn] = structure;
    }

    /** P4: every WALL_SET_RHYTHM_PERIOD-th tile of a run of WALL_SET_RHYTHM_MIN_RUN+ is STRUCTURE. */
    private static void placeRhythm(char[][] grid, List<List<int[]>> runs, char rhythmBase, Random random) {
        char structure = symbol(WallSlot.STRUCTURE);
        int period = LevelGenConstants.WALL_SET_RHYTHM_PERIOD;
        for (List<int[]> run : runs) {
            if (run.size() < LevelGenConstants.WALL_SET_RHYTHM_MIN_RUN) continue;
            int phase = random.nextInt(period);
            for (int index = 0; index < run.size(); index++) {
                if ((index + phase) % period != 0) continue;
                int[] tile = run.get(index);
                if (grid[tile[1]][tile[0]] == rhythmBase) grid[tile[1]][tile[0]] = structure;
            }
        }
    }

    /**
     * P5: walks each run and, before a stretch of WALL_SET_LIGHT_MAX_GAP tiles closes without a LIGHT,
     * places one on the latest base tile P9 allows inside that stretch (a LIGHT another run already put in
     * this run counts). The first LIGHT comes 3-5 tiles in (seeded). P9 wins over P5 when no tile is allowed.
     */
    private static void placeLights(char[][] grid, List<List<int[]>> runs, char rhythmBase, Random random,
                                    FocalSpacing focals) {
        char light = symbol(WallSlot.LIGHT);
        int window = LevelGenConstants.WALL_SET_LIGHT_MAX_GAP - 1;
        for (List<int[]> run : runs) {
            if (run.size() < LevelGenConstants.WALL_SET_LIGHT_MAX_GAP) continue;
            int lastLight = -1 - random.nextInt(3);
            for (int index = 0; index < run.size(); index++) {
                int[] tile = run.get(index);
                if (grid[tile[1]][tile[0]] == light) {
                    lastLight = index;
                    continue;
                }
                if (index - lastLight < window) continue;
                // A lamp reads best between two plain panels, so a tile flanked by base on both sides wins;
                // any allowed base tile in the stretch is the fallback.
                int chosen = latestLightTile(grid, run, index, lastLight, rhythmBase, light, focals, true);
                if (chosen < 0) chosen = latestLightTile(grid, run, index, lastLight, rhythmBase, light, focals, false);
                if (chosen >= 0) {
                    place(grid, run.get(chosen), light, focals);
                    lastLight = chosen;
                } else {
                    lastLight = index; // no allowed tile: P9 wins, move on
                }
            }
        }
    }

    /** The latest run index in (lastLight + focal gap, index] that can take a LIGHT; -1 when none can. */
    private static int latestLightTile(char[][] grid, List<int[]> run, int index, int lastLight, char rhythmBase,
                                       char light, FocalSpacing focals, boolean flankedByBase) {
        for (int candidate = index; candidate > lastLight + LevelGenConstants.WALL_SET_FOCAL_MIN_GAP
                && candidate >= 0; candidate--) {
            int[] option = run.get(candidate);
            if (grid[option[1]][option[0]] != rhythmBase || !focals.allows(option[0], option[1], light)) continue;
            if (flankedByBase && !(isRunBase(grid, run, candidate - 1, rhythmBase)
                                && isRunBase(grid, run, candidate + 1, rhythmBase))) continue;
            return candidate;
        }
        return -1;
    }

    private static boolean isRunBase(char[][] grid, List<int[]> run, int index, char rhythmBase) {
        if (index < 0 || index >= run.size()) return false;
        int[] tile = run.get(index);
        return grid[tile[1]][tile[0]] == rhythmBase;
    }

    /** P6: 1-3 MARKERs per floor — one near the arrival point, one near the exit, then anywhere. */
    private static void placeMarkers(char[][] grid, WallGroup[][] owner, Random random, FocalSpacing focals) {
        char marker = symbol(WallSlot.MARKER);
        char hallBase = symbol(WallSlot.BASE);
        int[] arrival = findSymbol(grid, ARRIVAL_SYMBOL);
        int[] exit    = findSymbol(grid, ge.tbegvadze.toon3d.util.RenderConstants.STAIRS_DOWN_CHAR);
        int span = LevelGenConstants.WALL_SET_MARKER_MAX - LevelGenConstants.WALL_SET_MARKER_MIN + 1;
        int wanted = LevelGenConstants.WALL_SET_MARKER_MIN + random.nextInt(span);
        int[][] anchors = { arrival, exit, null };
        for (int markerIndex = 0; markerIndex < wanted; markerIndex++) {
            int[] anchor = anchors[Math.min(markerIndex, anchors.length - 1)];
            if (markerIndex < 2 && anchor == null) continue;
            List<int[]> candidates = new ArrayList<>();
            for (int tileRow = 0; tileRow < grid.length; tileRow++) {
                for (int tileColumn = 0; tileColumn < grid[tileRow].length; tileColumn++) {
                    if (owner[tileRow][tileColumn] != WallGroup.HALL || grid[tileRow][tileColumn] != hallBase) continue;
                    if (anchor != null && Math.max(Math.abs(tileColumn - anchor[0]), Math.abs(tileRow - anchor[1]))
                            > LevelGenConstants.WALL_SET_MARKER_NEAR_TILES) continue;
                    candidates.add(new int[]{ tileColumn, tileRow });
                }
            }
            scatterFocal(grid, candidates, marker, 1, random, focals);
        }
    }

    /**
     * P6 / P7: dresses {@code fraction} of the remaining exposed hall base with {@code kinds} (one kind per
     * cluster, picked at random), in clusters of 1..WALL_SET_CLUSTER_MAX tiles along a straight line of hall
     * base. Door frames are already STRUCTURE, so a cluster never lands on one.
     */
    private static void placeHallClusters(char[][] grid, WallGroup[][] owner, float fraction, char[] kinds,
                                          Random random) {
        char hallBase = symbol(WallSlot.BASE);
        List<int[]> candidates = new ArrayList<>();
        for (int tileRow = 0; tileRow < grid.length; tileRow++) {
            for (int tileColumn = 0; tileColumn < grid[tileRow].length; tileColumn++) {
                if (owner[tileRow][tileColumn] == WallGroup.HALL && grid[tileRow][tileColumn] == hallBase) {
                    candidates.add(new int[]{ tileColumn, tileRow });
                }
            }
        }
        int target = Math.round(candidates.size() * Math.min(1f, Math.max(0f, fraction)));
        int placed = 0;
        for (int attempt = 0; attempt < candidates.size() * 2 && placed < target; attempt++) {
            int[] seedTile = candidates.get(random.nextInt(candidates.size()));
            if (grid[seedTile[1]][seedTile[0]] != hallBase) continue;
            char kind = kinds[random.nextInt(kinds.length)];
            int length = 1 + random.nextInt(LevelGenConstants.WALL_SET_CLUSTER_MAX);
            boolean alongRow = random.nextBoolean();
            int stepColumn = alongRow ? 1 : 0;
            int stepRow    = alongRow ? 0 : 1;
            for (int offset = 0; offset < length && placed < target; offset++) {
                int tileColumn = seedTile[0] + offset * stepColumn;
                int tileRow    = seedTile[1] + offset * stepRow;
                if (!inBounds(grid, tileColumn, tileRow) || owner[tileRow][tileColumn] != WallGroup.HALL
                        || grid[tileRow][tileColumn] != hallBase) break;
                grid[tileRow][tileColumn] = kind;
                placed++;
            }
        }
    }

    private static int[] findSymbol(char[][] grid, char symbol) {
        for (int tileRow = 0; tileRow < grid.length; tileRow++) {
            for (int tileColumn = 0; tileColumn < grid[tileRow].length; tileColumn++) {
                if (grid[tileRow][tileColumn] == symbol) return new int[]{ tileColumn, tileRow };
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Shared predicates
    // ---------------------------------------------------------------------------------------------

    private static boolean inBounds(char[][] grid, int tileColumn, int tileRow) {
        return tileRow >= 0 && tileRow < grid.length && tileColumn >= 0 && tileColumn < grid[tileRow].length;
    }

    private static boolean isWallTile(char[][] grid, int tileColumn, int tileRow) {
        return inBounds(grid, tileColumn, tileRow) && WallSlotTable.isWallSymbol(grid[tileRow][tileColumn]);
    }

    /** Open = in bounds and not a wall: a floor, a door, a prop, a pickup, a column — anything a face is seen from. */
    private static boolean isOpen(char[][] grid, int tileColumn, int tileRow) {
        return inBounds(grid, tileColumn, tileRow) && !WallSlotTable.isWallSymbol(grid[tileRow][tileColumn]);
    }

    private static boolean hasOpenNeighbour(char[][] grid, int tileColumn, int tileRow) {
        for (int direction = 0; direction < 4; direction++) {
            if (isOpen(grid, tileColumn + NEIGHBOUR_COLUMN[direction], tileRow + NEIGHBOUR_ROW[direction])) return true;
        }
        return false;
    }

    private static boolean facesInterior(char[][] grid, int tileColumn, int tileRow, FinishedRoom room) {
        for (int direction = 0; direction < 4; direction++) {
            int neighbourColumn = tileColumn + NEIGHBOUR_COLUMN[direction];
            int neighbourRow    = tileRow + NEIGHBOUR_ROW[direction];
            if (room.containsInterior(neighbourColumn, neighbourRow) && isOpen(grid, neighbourColumn, neighbourRow)) {
                return true;
            }
        }
        return false;
    }

    /** A wall with a door as a 4-neighbour: a door frame (P4 makes it STRUCTURE; nothing focal goes there). */
    static boolean isDoorFrame(char[][] grid, int tileColumn, int tileRow) {
        for (int direction = 0; direction < 4; direction++) {
            int neighbourColumn = tileColumn + NEIGHBOUR_COLUMN[direction];
            int neighbourRow    = tileRow + NEIGHBOUR_ROW[direction];
            if (inBounds(grid, neighbourColumn, neighbourRow) && Level.isDoor(grid[neighbourRow][neighbourColumn])) {
                return true;
            }
        }
        return false;
    }

    /**
     * P9 — the focal walls placed so far on the floor. Two focal walls keep more than
     * WALL_SET_FOCAL_MIN_GAP tiles between them (never adjacent); two copies of the SAME focal wall more
     * than WALL_SET_FOCAL_SAME_MIN_GAP. Distance is Chebyshev, which also spaces walls around a corner.
     */
    static final class FocalSpacing {
        private final List<int[]> placed = new ArrayList<>();

        boolean allows(int tileColumn, int tileRow, char symbol) {
            for (int[] other : placed) {
                int distance = Math.max(Math.abs(other[0] - tileColumn), Math.abs(other[1] - tileRow));
                int gap = other[2] == symbol ? LevelGenConstants.WALL_SET_FOCAL_SAME_MIN_GAP
                                             : LevelGenConstants.WALL_SET_FOCAL_MIN_GAP;
                if (distance <= gap) return false;
            }
            return true;
        }

        void add(int tileColumn, int tileRow, char symbol) {
            placed.add(new int[]{ tileColumn, tileRow, symbol });
        }
    }
}
