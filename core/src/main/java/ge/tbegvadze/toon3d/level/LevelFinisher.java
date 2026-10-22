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
 *   <li>P1 — the palette binds every wall symbol to the region set's picture (RegionPaletteBuilder).</li>
 * </ol>
 * It draws from its OWN random stream ({@link GameMath#wallSetSeed}), never the generator's, so the
 * generator's sequence — and with it layout, spawns and supply — is untouched. Same seed, same walls (R2).
 */
public final class LevelFinisher {

    private static final char PLAIN_WALL = 'x';
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
    // Shared predicates
    // ---------------------------------------------------------------------------------------------

    private static boolean inBounds(char[][] grid, int tileColumn, int tileRow) {
        return tileRow >= 0 && tileRow < grid.length && tileColumn >= 0 && tileColumn < grid[tileRow].length;
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
