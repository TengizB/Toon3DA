package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.util.LevelGenConstants;

import java.util.List;

/**
 * THE DOORWAY RULES (level-generation-overhaul R9-R14): where a socket may go in a room's shell, the DOOR
 * CLEARANCE ZONE around every doorway (queried by the prop engine, {@link GroupBarrelPlacer} and World's
 * shop-machine placement), and the final validator every generator runs on its finished grid. Pure and
 * static; reads only the grid and the rooms it is handed. Headless — no LibGDX.
 */
public final class DoorwayRules {

    private DoorwayRules() {}

    /** What the validator found on a finished grid. */
    public static final class Validation {
        /** Doors that failed the flank rule before demotion. */
        public final int flankFailures;
        /** Doors demoted to open floor ('l'). */
        public final int demotions;
        /** Shell openings that are not a legal socket of their room. */
        public final int shellGaps;

        Validation(int flankFailures, int demotions, int shellGaps) {
            this.flankFailures = flankFailures;
            this.demotions     = demotions;
            this.shellGaps     = shellGaps;
        }
    }

    /** Reads one tile; off the grid reads as a wall. */
    private interface CellReader {
        char at(int tileColumn, int tileRow);
    }

    // -------------------------------------------------------------------------
    // Socket legality (R10 / R11)
    // -------------------------------------------------------------------------

    /**
     * Whether a socket may be opened at a shell tile: it is on a straight side (never a corner), at least
     * {@code LEVEL_GEN_SOCKET_CORNER_MIN_DISTANCE} from either corner of that side, at least
     * {@code LEVEL_GEN_SOCKET_MIN_SPACING} from every socket already on that side, the room still has a
     * socket to spare, and the outer tile straight out is on the grid's interior (never the border).
     */
    public static boolean canPlaceSocket(LayoutRoom room, int tileColumn, int tileRow, int gridWidth, int gridHeight) {
        DoorSocket.Side side = room.sideOf(tileColumn, tileRow);
        if (side == null) return false;
        int maxSockets = room.role == LevelLayoutReport.Role.ENTRANCE
                ? LevelGenConstants.LEVEL_GEN_ENTRANCE_MAX_SOCKETS : LevelGenConstants.LEVEL_GEN_MAX_SOCKETS_PER_ROOM;
        if (room.sockets().size() >= maxSockets) return false;
        int distanceToCorner = side.isHorizontalWall()
                ? Math.min(tileColumn - room.leftColumn, room.rightColumn - tileColumn)
                : Math.min(tileRow - room.bottomRow, room.topRow - tileRow);
        if (distanceToCorner < LevelGenConstants.LEVEL_GEN_SOCKET_CORNER_MIN_DISTANCE) return false;
        for (DoorSocket existing : room.sockets()) {
            if (existing.side != side) continue;
            int spacing = side.isHorizontalWall() ? Math.abs(existing.tileColumn - tileColumn)
                                                  : Math.abs(existing.tileRow - tileRow);
            if (spacing < LevelGenConstants.LEVEL_GEN_SOCKET_MIN_SPACING) return false;
        }
        int outerColumn = tileColumn + side.stepColumn;
        int outerRow    = tileRow + side.stepRow;
        return outerColumn > 0 && outerColumn < gridWidth - 1 && outerRow > 0 && outerRow < gridHeight - 1;
    }

    /**
     * Opens a socket at a shell tile the caller has checked with {@link #canPlaceSocket}: the socket tile
     * becomes open floor 'l' (a door leaf is set later by {@link #hangDoor}). The outer tile is left to the
     * corridor that reaches it. Returns the socket.
     */
    public static DoorSocket openSocket(char[][] grid, LayoutRoom room, int tileColumn, int tileRow) {
        DoorSocket socket = new DoorSocket(tileColumn, tileRow, room.sideOf(tileColumn, tileRow));
        room.addSocket(socket);
        grid[tileRow][tileColumn] = 'l';
        return socket;
    }

    /** Hangs a door leaf on a socket ('d'). */
    public static void hangDoor(char[][] grid, DoorSocket socket) {
        grid[socket.tileRow][socket.tileColumn] = 'd';
        socket.setLeaf(DoorSocket.Leaf.DOOR);
    }

    // -------------------------------------------------------------------------
    // The door rule (R14)
    // -------------------------------------------------------------------------

    /**
     * R14: a door has walls on both tiles of one axis and walkable tiles on both tiles of the other, and no
     * door is orthogonally next to it.
     */
    public static boolean passesFlankRule(char[][] grid, int tileColumn, int tileRow) {
        CellReader cells = reader(grid);
        boolean wallsEastWest   = isWall(cells, tileColumn - 1, tileRow) && isWall(cells, tileColumn + 1, tileRow);
        boolean wallsNorthSouth = isWall(cells, tileColumn, tileRow - 1) && isWall(cells, tileColumn, tileRow + 1);
        boolean openEastWest    = isWalkable(cells, tileColumn - 1, tileRow) && isWalkable(cells, tileColumn + 1, tileRow);
        boolean openNorthSouth  = isWalkable(cells, tileColumn, tileRow - 1) && isWalkable(cells, tileColumn, tileRow + 1);
        if (!((wallsEastWest && openNorthSouth) || (wallsNorthSouth && openEastWest))) return false;
        return !Level.isDoor(cells.at(tileColumn - 1, tileRow)) && !Level.isDoor(cells.at(tileColumn + 1, tileRow))
            && !Level.isDoor(cells.at(tileColumn, tileRow - 1)) && !Level.isDoor(cells.at(tileColumn, tileRow + 1));
    }

    /**
     * The final check on a finished grid (R14), run by every generator. A door failing the flank rule is
     * DEMOTED to open floor 'l' and counted; every shell opening of every room that is not a registered socket
     * is counted as a gap. Construction must keep both counts at zero; this is the last-resort guard.
     */
    public static Validation validate(char[][] grid, List<LayoutRoom> rooms) {
        int flankFailures = 0;
        int demotions     = 0;
        for (int tileRow = 0; tileRow < grid.length; tileRow++) {
            for (int tileColumn = 0; tileColumn < grid[tileRow].length; tileColumn++) {
                if (!Level.isDoor(grid[tileRow][tileColumn])) continue;
                if (passesFlankRule(grid, tileColumn, tileRow)) continue;
                flankFailures++;
                grid[tileRow][tileColumn] = 'l';
                demotions++;
                for (LayoutRoom room : rooms) {
                    DoorSocket socket = room.socketAt(tileColumn, tileRow);
                    if (socket != null) socket.setLeaf(DoorSocket.Leaf.OPEN);
                }
            }
        }
        int shellGaps = 0;
        for (LayoutRoom room : rooms) {
            for (int tileRow = room.bottomRow; tileRow <= room.topRow; tileRow++) {
                for (int tileColumn = room.leftColumn; tileColumn <= room.rightColumn; tileColumn++) {
                    if (!room.isShellTile(tileColumn, tileRow)) continue;
                    if (tileRow < 0 || tileRow >= grid.length || tileColumn < 0 || tileColumn >= grid[0].length) continue;
                    if (Level.isWall(grid[tileRow][tileColumn])) continue;
                    if (room.socketAt(tileColumn, tileRow) == null) shellGaps++;
                }
            }
        }
        return new Validation(flankFailures, demotions, shellGaps);
    }

    // -------------------------------------------------------------------------
    // The door clearance zone (R18a, R26, R27)
    // -------------------------------------------------------------------------

    /** Whether a tile is inside the clearance zone of any DOOR leaf on the grid. */
    public static boolean isInDoorClearance(char[][] grid, int tileColumn, int tileRow) {
        return isNearDoor(reader(grid), tileColumn, tileRow);
    }

    /** Whether a tile is inside the clearance zone of any door leaf OR any socket of the given rooms. */
    public static boolean isInDoorClearance(char[][] grid, List<LayoutRoom> rooms, int tileColumn, int tileRow) {
        if (isNearDoor(reader(grid), tileColumn, tileRow)) return true;
        for (LayoutRoom room : rooms) {
            for (DoorSocket socket : room.sockets()) {
                if (inZone(socket.tileColumn, socket.tileRow, socket.side.isHorizontalWall(),
                        !socket.side.isHorizontalWall(), tileColumn, tileRow)) return true;
            }
        }
        return false;
    }

    /**
     * Whether a tile of a built level is inside the clearance zone of any door leaf, or of any socket its
     * generator reported (World's shop machine, R27).
     */
    public static boolean isInDoorClearance(Level level, int tileColumn, int tileRow) {
        CellReader cells = (column, row) -> column < 0 || row < 0 || column >= level.getWidth()
                || row >= level.getHeight() ? 'x' : level.getCell(column, row);
        if (isNearDoor(cells, tileColumn, tileRow)) return true;
        LevelLayoutReport layout = level.getLayoutReport();
        if (layout == null) return false;
        for (LevelLayoutReport.RoomRecord room : layout.rooms()) {
            for (int[] socket : room.sockets) {
                boolean horizontalWall = socket[1] == room.bottomRow || socket[1] == room.topRow;
                if (inZone(socket[0], socket[1], horizontalWall, !horizontalWall, tileColumn, tileRow)) return true;
            }
        }
        return false;
    }

    /** Marks a socket's clearance zone into a mask ([row][column]). */
    public static void markClearance(boolean[][] mask, DoorSocket socket) {
        int radius = LevelGenConstants.LEVEL_GEN_DOOR_CLEARANCE_RADIUS;
        int axis   = LevelGenConstants.LEVEL_GEN_DOOR_AXIS_CLEARANCE;
        for (int differenceRow = -radius; differenceRow <= radius; differenceRow++) {
            for (int differenceColumn = -radius; differenceColumn <= radius; differenceColumn++) {
                mark(mask, socket.tileColumn + differenceColumn, socket.tileRow + differenceRow);
            }
        }
        for (int distance = radius + 1; distance <= axis; distance++) {
            mark(mask, socket.tileColumn + socket.side.stepColumn * distance, socket.tileRow + socket.side.stepRow * distance);
            mark(mask, socket.tileColumn - socket.side.stepColumn * distance, socket.tileRow - socket.side.stepRow * distance);
        }
    }

    private static void mark(boolean[][] mask, int tileColumn, int tileRow) {
        if (tileRow >= 0 && tileRow < mask.length && tileColumn >= 0 && tileColumn < mask[0].length) {
            mask[tileRow][tileColumn] = true;
        }
    }

    private static boolean isNearDoor(CellReader cells, int tileColumn, int tileRow) {
        int reach = LevelGenConstants.LEVEL_GEN_DOOR_AXIS_CLEARANCE;
        for (int doorRow = tileRow - reach; doorRow <= tileRow + reach; doorRow++) {
            for (int doorColumn = tileColumn - reach; doorColumn <= tileColumn + reach; doorColumn++) {
                if (!Level.isDoor(cells.at(doorColumn, doorRow))) continue;
                boolean wallsEastWest   = isWall(cells, doorColumn - 1, doorRow) && isWall(cells, doorColumn + 1, doorRow);
                boolean wallsNorthSouth = isWall(cells, doorColumn, doorRow - 1) && isWall(cells, doorColumn, doorRow + 1);
                // An unflanked door (none should exist) keeps both axes clear.
                boolean verticalPassage   = wallsEastWest || !wallsNorthSouth;
                boolean horizontalPassage = wallsNorthSouth || !wallsEastWest;
                if (inZone(doorColumn, doorRow, verticalPassage, horizontalPassage, tileColumn, tileRow)) return true;
            }
        }
        return false;
    }

    private static boolean inZone(int socketColumn, int socketRow, boolean verticalPassage, boolean horizontalPassage,
                                  int tileColumn, int tileRow) {
        int differenceColumn = Math.abs(tileColumn - socketColumn);
        int differenceRow    = Math.abs(tileRow - socketRow);
        if (Math.max(differenceColumn, differenceRow) <= LevelGenConstants.LEVEL_GEN_DOOR_CLEARANCE_RADIUS) return true;
        int axis = LevelGenConstants.LEVEL_GEN_DOOR_AXIS_CLEARANCE;
        if (verticalPassage && differenceColumn == 0 && differenceRow <= axis) return true;
        return horizontalPassage && differenceRow == 0 && differenceColumn <= axis;
    }

    // -------------------------------------------------------------------------
    // Masks the corridor router reads (R9 / R12 / R13)
    // -------------------------------------------------------------------------

    /** [row][column] true on every room's shell and interior: corridors may never enter them (R9). */
    public static boolean[][] roomFootprintMask(List<LayoutRoom> rooms, int gridWidth, int gridHeight) {
        boolean[][] mask = new boolean[gridHeight][gridWidth];
        for (LayoutRoom room : rooms) {
            for (int tileRow = Math.max(0, room.bottomRow); tileRow <= Math.min(gridHeight - 1, room.topRow); tileRow++) {
                for (int tileColumn = Math.max(0, room.leftColumn);
                     tileColumn <= Math.min(gridWidth - 1, room.rightColumn); tileColumn++) {
                    mask[tileRow][tileColumn] = true;
                }
            }
        }
        return mask;
    }

    /** [row][column] true on every tile within Chebyshev {@code distance} of a room footprint (shell or interior). */
    public static boolean[][] nearRoomMask(List<LayoutRoom> rooms, int gridWidth, int gridHeight, int distance) {
        boolean[][] mask = new boolean[gridHeight][gridWidth];
        for (LayoutRoom room : rooms) {
            for (int tileRow = Math.max(0, room.bottomRow - distance);
                 tileRow <= Math.min(gridHeight - 1, room.topRow + distance); tileRow++) {
                for (int tileColumn = Math.max(0, room.leftColumn - distance);
                     tileColumn <= Math.min(gridWidth - 1, room.rightColumn + distance); tileColumn++) {
                    mask[tileRow][tileColumn] = true;
                }
            }
        }
        return mask;
    }

    // -------------------------------------------------------------------------

    private static CellReader reader(char[][] grid) {
        return (column, row) -> row < 0 || row >= grid.length || column < 0 || column >= grid[row].length
                ? 'x' : grid[row][column];
    }

    private static boolean isWall(CellReader cells, int tileColumn, int tileRow) {
        return Level.isWall(cells.at(tileColumn, tileRow));
    }

    /** Walkable for the door rule: not a wall, not a solid prop, not a column (doors themselves pass). */
    private static boolean isWalkable(CellReader cells, int tileColumn, int tileRow) {
        char cell = cells.at(tileColumn, tileRow);
        return !Level.isWall(cell) && !Level.isPropSolid(cell) && !Level.isColumn(cell);
    }
}
