package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.LevelGenConstants;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * THE ROOM DRESSER (level-generation-overhaul R15-R22): places every object in a room from that room's
 * {@link PropKit} — signature row first, then the supporting rows, then the decals — and never by a per-tile
 * dice roll. Before a room is dressed it computes the room's HUB (the interior centre, or the ring around a
 * CENTRE feature), one LANE from every socket's inner tile to the HUB, and the LANE BAND around them. Every
 * SOLID placement must then pass all of R18: outside every door clearance zone, outside the lane band, never
 * plugging a passage (nor turning a neighbour into a plug), the room's open interior still ONE 4-connected
 * region, and the room's fill under its cap (0.12 regular / 0.22 special / 0.40 dense). At most
 * {@code LEVEL_GEN_MAX_TIGHT_ROOMS_PER_FLOOR} rooms per floor dress DENSE; a further dense kit dresses its
 * RELAXED variant. Every random draw comes from the dresser's own stream (floor seed ^
 * {@code ROOM_DRESSING_SEED_SALT} ^ the room's index), so dressing never shifts the layout's sequence.
 * Headless — no LibGDX.
 */
public final class RoomDresser {

    private static final int[] STEP_COLUMNS = { 0, 0, 1, -1 };
    private static final int[] STEP_ROWS    = { 1, -1, 0, 0 };

    private final char[][]          grid;
    private final List<LayoutRoom>  rooms;
    private final long              floorSeed;
    private final boolean[][]       clearance;
    private final boolean[][]       laneBands;
    private final Map<Character, String> demand = new LinkedHashMap<>();
    private final Map<LayoutRoom, char[][]> snapshots = new IdentityHashMap<>();
    private int tightRooms;

    /**
     * @param grid      the floor grid ({@code grid[row][column]}); every room's sockets and door leaves are final
     * @param rooms     every room on the floor, in the generator's stable order (the stream index)
     * @param floorSeed the floor seed
     */
    public RoomDresser(char[][] grid, List<LayoutRoom> rooms, long floorSeed) {
        this.grid      = grid;
        this.rooms     = rooms;
        this.floorSeed = floorSeed;
        this.clearance = new boolean[grid.length][grid[0].length];
        this.laneBands = new boolean[grid.length][grid[0].length];
        for (int tileRow = 0; tileRow < grid.length; tileRow++) {
            for (int tileColumn = 0; tileColumn < grid[0].length; tileColumn++) {
                clearance[tileRow][tileColumn] = DoorwayRules.isInDoorClearance(grid, rooms, tileColumn, tileRow);
            }
        }
    }

    /** Whether a tile is inside any socket's (or door's) clearance zone. */
    public boolean isInClearance(int tileColumn, int tileRow) {
        return inBounds(tileColumn, tileRow) && clearance[tileRow][tileColumn];
    }

    /** Whether a tile is reserved: a clearance zone or a dressed room's lane band (R26 keep-clear). */
    public boolean isKeepClear(int tileColumn, int tileRow) {
        return inBounds(tileColumn, tileRow) && (clearance[tileRow][tileColumn] || laneBands[tileRow][tileColumn]);
    }

    /** Every symbol a placed kit claims, mapped to the sprite it must render (R29). */
    public Map<Character, String> kitDemand() {
        return Collections.unmodifiableMap(demand);
    }

    /**
     * Dresses one room (R16-R21). A DENSE kit dresses dense while the floor's tight budget lasts, otherwise its
     * {@code relaxedKit} (or, without one, itself at the non-dense cap). A null kit leaves the room bare.
     */
    public void dress(LayoutRoom room, PropKit kit, PropKit relaxedKit) {
        if (kit == null) return;
        boolean dense = kit.dense() && tightRooms < LevelGenConstants.LEVEL_GEN_MAX_TIGHT_ROOMS_PER_FLOOR;
        PropKit applied = kit.dense() && !dense && relaxedKit != null ? relaxedKit : kit;
        if (dense) tightRooms++;
        snapshot(room);
        Dressing dressing = new Dressing(room, applied, dense, streamFor(room));
        dressing.run();
        room.recordDressing(applied.id(), dense, dressing.bandTiles());
        for (int[] tile : dressing.bandTiles()) laneBands[tile[1]][tile[0]] = true;
        demand.putAll(applied.spriteDemand());
    }

    /**
     * The kit a REGULAR room draws (R23): uniform over the regular kits, the floor's preferred kit (set by a route
     * profile, R31) counting double. Drawn from the room's own dresser stream (a salt apart from its dressing).
     */
    public PropKit pickRegularKit(LayoutRoom room, List<PropKit> kits, String preferredKitId) {
        if (kits.isEmpty()) return null;
        List<PropKit> weighted = new ArrayList<>(kits);
        for (PropKit kit : kits) if (kit.id().equals(preferredKitId)) weighted.add(kit);
        Random random = new Random(GameMath.mixSeed(floorSeed ^ LevelGenConstants.ROOM_DRESSING_SEED_SALT,
                -(rooms.indexOf(room) + 1L)));
        return weighted.get(random.nextInt(weighted.size()));
    }

    /**
     * Places ONE extra row into an already dressed room (the R20 terminal fallback): the room keeps its lanes,
     * band and kit; the row's symbols join the floor's demand. Returns how many tiles it placed.
     */
    public int dressExtraRow(LayoutRoom room, PropKitRow row) {
        PropKit single = PropKit.plain(room.kitId() != null ? room.kitId() : "extra").row(row).build();
        Dressing dressing = new Dressing(room, single, room.dense(), streamFor(room));
        dressing.useBand(room.laneBand());
        int placed = dressing.placeRow(row, Math.max(1, row.minCount), false);
        demand.putAll(single.spriteDemand());
        return placed;
    }

    /** Restores a dressed room's interior to how it was before {@link #dress}, then dresses it with another kit. */
    public void redress(LayoutRoom room, PropKit kit) {
        char[][] before = snapshots.get(room);
        if (before != null) {
            for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
                for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                    grid[tileRow][tileColumn] = before[tileRow - room.bottomRow - 1][tileColumn - room.leftColumn - 1];
                }
            }
        }
        if (room.laneBand() != null) {
            for (int[] tile : room.laneBand()) laneBands[tile[1]][tile[0]] = false;
        }
        if (room.dense()) tightRooms--;
        snapshots.remove(room);
        dress(room, kit, null);
    }

    /** Whether the grid holds a terminal 'T' with a standable orthogonal neighbour (R20, story LOG_FOUND). */
    public boolean hasUsableTerminal() {
        for (int tileRow = 0; tileRow < grid.length; tileRow++) {
            for (int tileColumn = 0; tileColumn < grid[0].length; tileColumn++) {
                if (grid[tileRow][tileColumn] != 'T') continue;
                for (int direction = 0; direction < 4; direction++) {
                    if (isStandable(tileColumn + STEP_COLUMNS[direction], tileRow + STEP_ROWS[direction])) return true;
                }
            }
        }
        return false;
    }

    private void snapshot(LayoutRoom room) {
        char[][] copy = new char[Math.max(0, room.interiorHeight())][Math.max(0, room.interiorWidth())];
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                copy[tileRow - room.bottomRow - 1][tileColumn - room.leftColumn - 1] = grid[tileRow][tileColumn];
            }
        }
        snapshots.put(room, copy);
    }

    private Random streamFor(LayoutRoom room) {
        int index = rooms.indexOf(room);
        return new Random(GameMath.mixSeed(floorSeed ^ LevelGenConstants.ROOM_DRESSING_SEED_SALT, index + 1L));
    }

    private boolean inBounds(int tileColumn, int tileRow) {
        return tileRow >= 0 && tileRow < grid.length && tileColumn >= 0 && tileColumn < grid[0].length;
    }

    private boolean isStandable(int tileColumn, int tileRow) {
        if (!inBounds(tileColumn, tileRow)) return false;
        char cell = grid[tileRow][tileColumn];
        return !Level.isWall(cell) && !Level.isPropSolid(cell) && !Level.isColumn(cell);
    }

    private static boolean isPlainFloor(char cell) {
        return cell == ' ' || cell == 'l' || cell == 'u' || cell == 'f';
    }

    private static long key(int tileColumn, int tileRow) {
        return ((long) tileRow << 32) | (tileColumn & 0xFFFFFFFFL);
    }

    // -------------------------------------------------------------------------
    // One room's dressing
    // -------------------------------------------------------------------------

    /** The state of dressing one room: what the anchors read and the R18 checks enforce. */
    final class Dressing {
        final LayoutRoom room;
        final PropKit    kit;
        final boolean    dense;
        final Random     random;
        private final float cap;
        private final Set<Long> band      = new HashSet<>();
        private final Set<Long> footprint = new HashSet<>();
        private final List<int[]> footprintTiles = new ArrayList<>();
        private final List<int[]> hubTiles       = new ArrayList<>();
        private final List<List<int[]>> lanes    = new ArrayList<>();
        private final List<int[]> placed         = new ArrayList<>(); // {column, row, symbol}
        private final int[] directionOrder;
        private int solids;
        private int open;

        Dressing(LayoutRoom room, PropKit kit, boolean dense, Random random) {
            this.room   = room;
            this.kit    = kit;
            this.dense  = dense;
            this.random = random;
            this.cap    = dense ? LevelGenConstants.LEVEL_GEN_ROOM_FILL_CAP_DENSE
                        : room.role == LevelLayoutReport.Role.SPECIAL ? LevelGenConstants.LEVEL_GEN_ROOM_FILL_CAP_SPECIAL
                        : LevelGenConstants.LEVEL_GEN_ROOM_FILL_CAP_REGULAR;
            List<Integer> order = new ArrayList<>(java.util.Arrays.asList(0, 1, 2, 3));
            Collections.shuffle(order, random);
            this.directionOrder = new int[]{ order.get(0), order.get(1), order.get(2), order.get(3) };
            for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
                for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                    char cell = grid[tileRow][tileColumn];
                    if (Level.isWall(cell)) continue;
                    open++;
                    if (Level.isPropSolid(cell) || Level.isColumn(cell)) solids++;
                }
            }
        }

        void run() {
            List<PropKitRow> rows = kit.rows();
            boolean[] active = new boolean[rows.size()];
            int[] counts     = new int[rows.size()];
            PropAnchor[] anchors = new PropAnchor[rows.size()];
            int shortSide = Math.min(room.interiorWidth(), room.interiorHeight());
            boolean bigEnoughForCentre = room.interiorWidth() >= LevelGenConstants.LEVEL_GEN_CENTRE_ROW_MIN_INTERIOR
                    && room.interiorHeight() >= LevelGenConstants.LEVEL_GEN_CENTRE_ROW_MIN_INTERIOR;
            // Every row's chance and count is drawn up front, in row order, so the stream's use is fixed.
            for (int index = 0; index < rows.size(); index++) {
                PropKitRow row = rows.get(index);
                boolean signature = index == 0 && kit.signatureRow() != null;
                float roll = random.nextFloat();
                counts[index]  = row.minCount + random.nextInt(row.maxCount - row.minCount + 1);
                active[index]  = signature || (roll < row.chance && shortSide >= row.minimumShortSide);
                anchors[index] = row.anchor;
                if (row.anchor == PropAnchor.CENTRE && !bigEnoughForCentre) {
                    if (signature) anchors[index] = PropAnchor.BACK_WALL;
                    else active[index] = false;
                }
            }
            reserveCentre(rows, active, anchors, counts);
            computeLanes();
            for (int index = 0; index < rows.size(); index++) {
                if (!active[index]) continue;
                boolean signature = index == 0 && kit.signatureRow() != null;
                PropKitRow row = rows.get(index);
                if (anchors[index] != row.anchor) {
                    placeWithAnchors(row, counts[index], signature, anchors[index]);
                } else {
                    placeRow(row, counts[index], signature);
                }
            }
        }

        /** Applies an already computed band (an extra row into a dressed room). */
        void useBand(List<int[]> tiles) {
            if (tiles == null) return;
            for (int[] tile : tiles) band.add(key(tile[0], tile[1]));
        }

        List<int[]> bandTiles() {
            List<int[]> tiles = new ArrayList<>();
            for (long packed : band) tiles.add(new int[]{ (int) packed, (int) (packed >> 32) });
            tiles.sort((first, second) -> first[1] != second[1] ? first[1] - second[1] : first[0] - second[0]);
            return tiles;
        }

        // --- HUB, lanes and band ---

        private void reserveCentre(List<PropKitRow> rows, boolean[] active, PropAnchor[] anchors, int[] counts) {
            List<int[]> claimed = room.centreFeature();
            if (claimed != null) {
                for (int[] tile : claimed) addFootprint(tile[0], tile[1]);
                return;
            }
            for (int index = 0; index < rows.size(); index++) {
                if (!active[index] || anchors[index] != PropAnchor.CENTRE) continue;
                int[][] options = rows.get(index).centreFootprints;
                int[] chosen = options[random.nextInt(options.length)];
                boolean horizontal = room.interiorWidth() >= room.interiorHeight();
                int footprintWidth  = horizontal ? chosen[0] : chosen[1];
                int footprintHeight = horizontal ? chosen[1] : chosen[0];
                int left   = room.centerColumn() - (footprintWidth - 1) / 2;
                int bottom = room.centerRow() - (footprintHeight - 1) / 2;
                for (int tileRow = bottom; tileRow < bottom + footprintHeight; tileRow++) {
                    for (int tileColumn = left; tileColumn < left + footprintWidth; tileColumn++) {
                        if (room.containsInterior(tileColumn, tileRow)) addFootprint(tileColumn, tileRow);
                    }
                }
                return;
            }
        }

        private void addFootprint(int tileColumn, int tileRow) {
            if (footprint.add(key(tileColumn, tileRow))) footprintTiles.add(new int[]{ tileColumn, tileRow });
        }

        private void computeLanes() {
            if (footprint.isEmpty()) {
                hubTiles.add(new int[]{ room.centerColumn(), room.centerRow() });
            } else {
                for (int[] tile : footprintTiles) {
                    for (int differenceRow = -1; differenceRow <= 1; differenceRow++) {
                        for (int differenceColumn = -1; differenceColumn <= 1; differenceColumn++) {
                            int column = tile[0] + differenceColumn, row = tile[1] + differenceRow;
                            if (!room.containsInterior(column, row) || footprint.contains(key(column, row))) continue;
                            if (!isLaneTile(column, row)) continue;
                            boolean known = false;
                            for (int[] hub : hubTiles) if (hub[0] == column && hub[1] == row) known = true;
                            if (!known) hubTiles.add(new int[]{ column, row });
                        }
                    }
                }
            }
            for (DoorSocket socket : room.sockets()) {
                List<int[]> lane = lanePath(socket.innerColumn(), socket.innerRow());
                if (lane.isEmpty()) continue;
                lanes.add(lane);
                int halfWidth = dense ? 0 : LevelGenConstants.LEVEL_GEN_LANE_BAND_HALF_WIDTH;
                for (int[] tile : lane) {
                    band.add(key(tile[0], tile[1]));
                    if (halfWidth <= 0) continue;
                    for (int direction = 0; direction < 4; direction++) {
                        int column = tile[0] + STEP_COLUMNS[direction], row = tile[1] + STEP_ROWS[direction];
                        // The centre footprint is the feature the lanes lead to, never part of their band.
                        if (room.containsInterior(column, row) && !Level.isWall(grid[row][column])
                                && !footprint.contains(key(column, row))) {
                            band.add(key(column, row));
                        }
                    }
                }
            }
            for (int[] hub : hubTiles) if (footprint.isEmpty()) band.add(key(hub[0], hub[1]));
        }

        private boolean isLaneTile(int tileColumn, int tileRow) {
            char cell = grid[tileRow][tileColumn];
            return !Level.isWall(cell) && !Level.isPropSolid(cell) && !Level.isColumn(cell);
        }

        /** The shortest interior path from a socket's inner tile to the nearest HUB tile (seeded direction order). */
        private List<int[]> lanePath(int fromColumn, int fromRow) {
            if (!room.containsInterior(fromColumn, fromRow) || !isLaneTile(fromColumn, fromRow)) {
                return Collections.emptyList();
            }
            Set<Long> hubs = new HashSet<>();
            for (int[] hub : hubTiles) hubs.add(key(hub[0], hub[1]));
            Map<Long, Long> parent = new LinkedHashMap<>();
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            long start = key(fromColumn, fromRow);
            parent.put(start, start);
            queue.add(new int[]{ fromColumn, fromRow });
            long reached = -1;
            while (!queue.isEmpty()) {
                int[] current = queue.poll();
                long currentKey = key(current[0], current[1]);
                if (hubs.contains(currentKey)) {
                    reached = currentKey;
                    break;
                }
                for (int step = 0; step < 4; step++) {
                    int direction = directionOrder[step];
                    int column = current[0] + STEP_COLUMNS[direction], row = current[1] + STEP_ROWS[direction];
                    if (!room.containsInterior(column, row) || !isLaneTile(column, row)) continue;
                    long next = key(column, row);
                    if (footprint.contains(next) || parent.containsKey(next)) continue;
                    parent.put(next, currentKey);
                    queue.add(new int[]{ column, row });
                }
            }
            if (reached < 0) return Collections.emptyList();
            List<int[]> path = new ArrayList<>();
            for (long cursor = reached; ; cursor = parent.get(cursor)) {
                path.add(new int[]{ (int) cursor, (int) (cursor >> 32) });
                if (parent.get(cursor) == cursor) break;
            }
            Collections.reverse(path);
            return path;
        }

        // --- Rows ---

        int placeRow(PropKitRow row, int count, boolean signature) {
            return placeWithAnchors(row, count, signature, row.anchor);
        }

        private int placeWithAnchors(PropKitRow row, int count, boolean signature, PropAnchor first) {
            List<PropAnchor> chain = new ArrayList<>();
            chain.add(first);
            if (row.fallback != null && !chain.contains(row.fallback)) chain.add(row.fallback);
            if (signature) {
                for (PropAnchor anchor : new PropAnchor[]{ PropAnchor.BACK_WALL, PropAnchor.SIDE_WALL,
                                                           PropAnchor.ANY_WALL }) {
                    if (!chain.contains(anchor)) chain.add(anchor);
                }
            }
            int placedCount = 0;
            for (PropAnchor anchor : chain) {
                placedCount += placeMode(row, anchor, anchor.lines(this, row), count - placedCount);
                // Escalate down the chain only while the row is short of its minimum.
                if (placedCount >= count || placedCount >= row.minCount) break;
            }
            return placedCount;
        }

        private int placeMode(PropKitRow row, PropAnchor anchor, List<List<int[]>> lines, int wanted) {
            if (wanted <= 0) return 0;
            if (row.placesDecals()) return placeDecals(row, anchor, lines, wanted);
            // ROWS racks fill every legal tile of their lines; the plug check breaks them into pairs.
            boolean intoFootprint = anchor == PropAnchor.CENTRE || anchor == PropAnchor.FEATURE;
            if (anchor == PropAnchor.ROWS)           return placeSingles(row, lines, Integer.MAX_VALUE, true, false);
            if (row.mode == PropKitRow.Mode.RUN)     return placeRuns(row, lines, wanted, 1);
            if (row.mode == PropKitRow.Mode.SPACED)  return placeRuns(row, lines, wanted, 2);
            if (row.mode == PropKitRow.Mode.CLUSTER) return placeCluster(row, anchor, lines, wanted);
            return placeSingles(row, lines, wanted, false, intoFootprint);
        }

        private int placeSingles(PropKitRow row, List<List<int[]>> lines, int wanted, boolean inLineOrder,
                                 boolean intoFootprint) {
            List<int[]> candidates = new ArrayList<>();
            for (List<int[]> line : lines) candidates.addAll(line);
            if (!inLineOrder) Collections.shuffle(candidates, random);
            int count = 0;
            for (int[] tile : candidates) {
                if (count >= wanted) break;
                if (tryPlaceSolid(tile[0], tile[1], row.drawSymbol(random), intoFootprint)) count++;
            }
            return count;
        }

        /**
         * RUN (step 1) or SPACED (step 2) along one line: the longest run reachable, up to {@code wanted}. Every start
         * is tried on the same clean room and undone; the winner is then placed again. (Keeping a run that was checked
         * beside another run's tiles, then removing those, could leave its end tile plugging a passage.)
         */
        private int placeRuns(PropKitRow row, List<List<int[]>> lines, int wanted, int step) {
            List<List<int[]>> order = new ArrayList<>(lines);
            Collections.shuffle(order, random);
            char symbol = row.drawSymbol(random);
            List<int[]> bestLine = null;
            int bestStart = -1, bestLength = 0;
            for (List<int[]> line : order) {
                List<Integer> starts = new ArrayList<>();
                for (int index = 0; index < line.size(); index++) starts.add(index);
                Collections.shuffle(starts, random);
                for (int start : starts) {
                    List<int[]> run = runFrom(line, start, step, wanted, symbol);
                    if (run.size() >= wanted) return run.size();
                    undo(run);
                    if (run.size() > bestLength) {
                        bestLength = run.size();
                        bestLine   = line;
                        bestStart  = start;
                    }
                }
            }
            return bestLine == null ? 0 : runFrom(bestLine, bestStart, step, wanted, symbol).size();
        }

        private List<int[]> runFrom(List<int[]> line, int start, int step, int wanted, char symbol) {
            List<int[]> run = new ArrayList<>();
            for (int index = start; index < line.size() && run.size() < wanted; index += step) {
                int[] tile = line.get(index);
                if (!tryPlaceSolid(tile[0], tile[1], symbol, false)) break;
                run.add(tile);
            }
            return run;
        }

        /** CLUSTER: one contiguous blob grown from a seed (inside the centre footprint for a CENTRE row). */
        private int placeCluster(PropKitRow row, PropAnchor anchor, List<List<int[]>> lines, int wanted) {
            List<int[]> seeds = new ArrayList<>();
            for (List<int[]> line : lines) seeds.addAll(line);
            Collections.shuffle(seeds, random);
            boolean centre = anchor == PropAnchor.CENTRE;
            for (int[] seed : seeds) {
                if (!tryPlaceSolid(seed[0], seed[1], row.drawSymbol(random), centre)) continue;
                List<int[]> cluster = new ArrayList<>();
                cluster.add(seed);
                while (cluster.size() < wanted) {
                    List<int[]> frontier = new ArrayList<>();
                    for (int[] member : cluster) {
                        for (int direction = 0; direction < 4; direction++) {
                            int column = member[0] + STEP_COLUMNS[direction], neighborRow = member[1] + STEP_ROWS[direction];
                            if (centre && !footprint.contains(key(column, neighborRow))) continue;
                            if (!room.containsInterior(column, neighborRow) || !isPlainFloor(grid[neighborRow][column])) continue;
                            frontier.add(new int[]{ column, neighborRow });
                        }
                    }
                    if (frontier.isEmpty()) break;
                    Collections.shuffle(frontier, random);
                    frontier.sort((first, second) -> Boolean.compare(!isFlush(first[0], first[1]),
                                                                       !isFlush(second[0], second[1])));
                    boolean grew = false;
                    for (int[] tile : frontier) {
                        if (tryPlaceSolid(tile[0], tile[1], row.drawSymbol(random), centre)) {
                            cluster.add(tile);
                            grew = true;
                            break;
                        }
                    }
                    if (!grew) break;
                }
                return cluster.size();
            }
            return 0;
        }

        private int placeDecals(PropKitRow row, PropAnchor anchor, List<List<int[]>> lines, int wanted) {
            List<int[]> candidates = new ArrayList<>();
            for (List<int[]> line : lines) candidates.addAll(line);
            int count = 0;
            if (anchor == PropAnchor.FIELD && row.density > 0f) {
                for (int[] tile : candidates) {
                    if (random.nextFloat() < row.density && tryPlaceDecal(tile[0], tile[1], row.drawSymbol(random))) count++;
                }
                return count;
            }
            if (anchor == PropAnchor.TRAIL) {
                for (int[] tile : candidates) {
                    if (count >= wanted) break;
                    if (random.nextFloat() >= LevelGenConstants.LEVEL_GEN_TRAIL_STEP_CHANCE) continue;
                    if (tryPlaceDecal(tile[0], tile[1], row.drawSymbol(random))) count++;
                }
                return count;
            }
            Collections.shuffle(candidates, random);
            for (int[] tile : candidates) {
                if (count >= wanted) break;
                if (tryPlaceDecal(tile[0], tile[1], row.drawSymbol(random))) count++;
            }
            return count;
        }

        // --- The R18 checks ---

        private boolean tryPlaceSolid(int tileColumn, int tileRow, char symbol, boolean intoFootprint) {
            if (!room.containsInterior(tileColumn, tileRow)) return false;
            char previous = grid[tileRow][tileColumn];
            if (!isPlainFloor(previous)) return false;
            long tile = key(tileColumn, tileRow);
            if (clearance[tileRow][tileColumn]) return false;                       // (a)
            if (band.contains(tile)) return false;                                   // (b)
            if (footprint.contains(tile) && !intoFootprint) return false;            //     the centre is reserved
            if ((solids + 1) > cap * open + 1e-4f) return false;                     // (e)
            grid[tileRow][tileColumn] = symbol;
            boolean legal = !plugs(tileColumn, tileRow) && roomStaysConnected();      // (c) + (d)
            if (!legal) {
                grid[tileRow][tileColumn] = previous;
                return false;
            }
            solids++;
            placed.add(new int[]{ tileColumn, tileRow, symbol, previous });
            return true;
        }

        private boolean tryPlaceDecal(int tileColumn, int tileRow, char symbol) {
            if (!room.containsInterior(tileColumn, tileRow)) return false;
            char previous = grid[tileRow][tileColumn];
            if (!isPlainFloor(previous)) return false;
            grid[tileRow][tileColumn] = symbol;
            placed.add(new int[]{ tileColumn, tileRow, symbol, previous });
            return true;
        }

        private void undo(List<int[]> tiles) {
            for (int[] tile : tiles) {
                for (int index = placed.size() - 1; index >= 0; index--) {
                    int[] entry = placed.get(index);
                    if (entry[0] != tile[0] || entry[1] != tile[1]) continue;
                    grid[tile[1]][tile[0]] = (char) entry[3];
                    placed.remove(index);
                    solids--;
                    break;
                }
            }
        }

        /** (c): the new solid must not plug a passage, nor turn a solid neighbour into a plug. */
        private boolean plugs(int tileColumn, int tileRow) {
            if (FloorPopulator.wouldPlugPassage(grid, tileColumn, tileRow)) return true;
            for (int direction = 0; direction < 4; direction++) {
                int column = tileColumn + STEP_COLUMNS[direction], row = tileRow + STEP_ROWS[direction];
                if (!inBounds(column, row)) continue;
                char neighbour = grid[row][column];
                if (!Level.isPropSolid(neighbour) && !Level.isColumn(neighbour)) continue;
                if (FloorPopulator.wouldPlugPassage(grid, column, row)) return true;
            }
            return false;
        }

        /** (d): the room's open interior tiles stay one 4-connected region. */
        private boolean roomStaysConnected() {
            int[] start = null;
            int openTiles = 0;
            for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
                for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                    if (!isLaneTile(tileColumn, tileRow)) continue;
                    openTiles++;
                    if (start == null) start = new int[]{ tileColumn, tileRow };
                }
            }
            if (start == null) return true;
            Set<Long> seen = new HashSet<>();
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            seen.add(key(start[0], start[1]));
            queue.add(start);
            while (!queue.isEmpty()) {
                int[] current = queue.poll();
                for (int direction = 0; direction < 4; direction++) {
                    int column = current[0] + STEP_COLUMNS[direction], row = current[1] + STEP_ROWS[direction];
                    if (!room.containsInterior(column, row) || !isLaneTile(column, row)) continue;
                    if (seen.add(key(column, row))) queue.add(new int[]{ column, row });
                }
            }
            return seen.size() == openTiles;
        }

        private boolean isFlush(int tileColumn, int tileRow) {
            for (int direction = 0; direction < 4; direction++) {
                int column = tileColumn + STEP_COLUMNS[direction], row = tileRow + STEP_ROWS[direction];
                if (!inBounds(column, row) || Level.isWall(grid[row][column])) return true;
            }
            return false;
        }

        // --- What the anchors read ---

        /** The side opposite the primary socket (NORTH when the room has none). */
        DoorSocket.Side backSide() {
            DoorSocket primary = room.primarySocket();
            if (primary == null) return DoorSocket.Side.NORTH;
            for (DoorSocket.Side side : DoorSocket.Side.values()) {
                if (side.stepColumn == -primary.side.stepColumn && side.stepRow == -primary.side.stepRow) return side;
            }
            return DoorSocket.Side.NORTH;
        }

        /** The primary socket's side (SOUTH when the room has none). */
        DoorSocket.Side entrySide() {
            DoorSocket primary = room.primarySocket();
            return primary == null ? DoorSocket.Side.SOUTH : primary.side;
        }

        /** The two sides perpendicular to the primary socket's side. */
        List<DoorSocket.Side> perpendicularSides() {
            List<DoorSocket.Side> sides = new ArrayList<>();
            boolean entryHorizontal = entrySide().isHorizontalWall();
            for (DoorSocket.Side side : DoorSocket.Side.values()) {
                if (side.isHorizontalWall() != entryHorizontal) sides.add(side);
            }
            return sides;
        }

        /** The interior tiles flush against one wall, in order along it. */
        List<int[]> flushLine(DoorSocket.Side side) {
            List<int[]> line = new ArrayList<>();
            if (side.isHorizontalWall()) {
                int tileRow = side.stepRow > 0 ? room.topRow - 1 : room.bottomRow + 1;
                for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                    if (Level.isWall(grid[tileRow + side.stepRow][tileColumn])) line.add(new int[]{ tileColumn, tileRow });
                }
            } else {
                int tileColumn = side.stepColumn > 0 ? room.rightColumn - 1 : room.leftColumn + 1;
                for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
                    if (Level.isWall(grid[tileRow][tileColumn + side.stepColumn])) line.add(new int[]{ tileColumn, tileRow });
                }
            }
            return line;
        }

        /** Interior tiles with two shell neighbours. */
        List<int[]> cornerTiles() {
            List<int[]> corners = new ArrayList<>();
            int[][] candidates = {
                    { room.leftColumn + 1, room.bottomRow + 1 }, { room.rightColumn - 1, room.bottomRow + 1 },
                    { room.leftColumn + 1, room.topRow - 1 },    { room.rightColumn - 1, room.topRow - 1 } };
            for (int[] corner : candidates) {
                int walls = 0;
                for (int direction = 0; direction < 4; direction++) {
                    if (room.isShellTile(corner[0] + STEP_COLUMNS[direction], corner[1] + STEP_ROWS[direction])) walls++;
                }
                if (walls >= 2) corners.add(corner);
            }
            return corners;
        }

        List<int[]> centreFootprint() {
            return new ArrayList<>(footprintTiles);
        }

        /** ROWS: rack lines along the long axis, one in from the walls, cut by the cross aisle through the HUB. */
        List<List<int[]>> rackLines() {
            boolean horizontal = room.interiorWidth() >= room.interiorHeight();
            int longSide  = horizontal ? room.interiorWidth() : room.interiorHeight();
            int shortSide = horizontal ? room.interiorHeight() : room.interiorWidth();
            int crossAisle = horizontal ? room.centerColumn() - room.leftColumn - 1 : room.centerRow() - room.bottomRow - 1;
            List<List<int[]>> lines = new ArrayList<>();
            int lineNumber = 0;
            for (int across = 1; across <= shortSide - 2; across += 2, lineNumber++) {
                if (!dense && (lineNumber % 2 != 0 || lines.size() >= 2)) continue;
                List<int[]> line = new ArrayList<>();
                for (int along = 1; along <= longSide - 2; along++) {
                    if (along == crossAisle) continue;
                    int tileColumn = horizontal ? room.leftColumn + 1 + along : room.leftColumn + 1 + across;
                    int tileRow    = horizontal ? room.bottomRow + 1 + across : room.bottomRow + 1 + along;
                    line.add(new int[]{ tileColumn, tileRow });
                }
                lines.add(line);
            }
            return lines;
        }

        List<int[]> feature(String name) {
            return name == null ? Collections.emptyList() : new ArrayList<>(room.feature(name));
        }

        /** Open interior tiles within Chebyshev 1 of a prop this kit placed with one of {@code symbols}. */
        List<int[]> nearTiles(String symbols) {
            List<int[]> tiles = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            if (symbols == null) return tiles;
            for (int[] entry : placed) {
                if (symbols.indexOf((char) entry[2]) < 0) continue;
                for (int differenceRow = -1; differenceRow <= 1; differenceRow++) {
                    for (int differenceColumn = -1; differenceColumn <= 1; differenceColumn++) {
                        int column = entry[0] + differenceColumn, row = entry[1] + differenceRow;
                        if (!room.containsInterior(column, row) || !isPlainFloor(grid[row][column])) continue;
                        if (seen.add(key(column, row))) tiles.add(new int[]{ column, row });
                    }
                }
            }
            return tiles;
        }

        /** Every open (plain floor) interior tile, row by row. */
        List<int[]> openTiles() {
            List<int[]> tiles = new ArrayList<>();
            for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
                for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                    if (isPlainFloor(grid[tileRow][tileColumn])) tiles.add(new int[]{ tileColumn, tileRow });
                }
            }
            return tiles;
        }

        /** One lane, from the HUB side toward its socket (a random lane when the room has several). */
        List<int[]> trail() {
            if (lanes.isEmpty()) return Collections.emptyList();
            List<int[]> lane = new ArrayList<>(lanes.get(random.nextInt(lanes.size())));
            Collections.reverse(lane);
            return lane;
        }
    }
}
