package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.util.LevelGenConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The layout steps every combat generator shares (level-generation-overhaul pipeline A, steps d / g / i): joining
 * two rooms socket-to-socket through the {@link CorridorRouter} (reusing a socket on the facing side, else opening
 * one that passes R10), hanging door leaves (R14), the hall pass (R28: spaced columns on 3-wide halls, sparse
 * walkable decals), the connectivity backstop that opens a socket instead of cutting a shell (R30), and the
 * per-room supply region map. Package-private and static; every draw comes from the Random it is handed.
 * Headless — no LibGDX.
 */
final class LayoutSupport {

    private static final int[] STEP_COLUMNS = { 0, 0, 1, -1 };
    private static final int[] STEP_ROWS    = { 1, -1, 0, 0 };
    /** The walkable decals a hall may carry (R28). */
    private static final String HALL_DECALS = ".Os";

    private LayoutSupport() {}

    /** The masks the router reads, built once all rooms are placed. */
    static final class RouteMasks {
        final boolean[][] rooms;
        final boolean[][] hug;
        final boolean[][] nearRooms;

        RouteMasks(List<LayoutRoom> layoutRooms, int gridWidth, int gridHeight) {
            this.rooms     = DoorwayRules.roomFootprintMask(layoutRooms, gridWidth, gridHeight);
            this.nearRooms = DoorwayRules.nearRoomMask(layoutRooms, gridWidth, gridHeight, 1);
            this.hug       = new boolean[gridHeight][gridWidth];
            for (int tileRow = 0; tileRow < gridHeight; tileRow++) {
                for (int tileColumn = 0; tileColumn < gridWidth; tileColumn++) {
                    hug[tileRow][tileColumn] = nearRooms[tileRow][tileColumn] && !rooms[tileRow][tileColumn];
                }
            }
        }
    }

    /**
     * Joins two rooms (R11 / R12): a socket on each room's side facing the other (an existing one is reused),
     * then the router's cheapest path between the two OUTER tiles, carved to 'l'. Returns the path, or null
     * when no legal socket or route exists (any socket opened for the attempt is closed again).
     */
    static List<int[]> connect(char[][] grid, LayoutRoom from, LayoutRoom to, RouteMasks masks, long routeSeed) {
        List<DoorSocket> opened = new ArrayList<>();
        DoorSocket fromSocket = socketToward(grid, from, to, masks, opened);
        DoorSocket toSocket   = fromSocket == null ? null : socketToward(grid, to, from, masks, opened);
        List<int[]> path = toSocket == null ? new ArrayList<>()
                : CorridorRouter.route(grid, masks.rooms, masks.hug, fromSocket.outerColumn(), fromSocket.outerRow(),
                                       toSocket.outerColumn(), toSocket.outerRow(), routeSeed);
        if (path.isEmpty()) {
            for (DoorSocket socket : opened) closeSocket(grid, from, to, socket);
            return null;
        }
        for (int[] tile : path) {
            if (Level.isWall(grid[tile[1]][tile[0]])) grid[tile[1]][tile[0]] = 'l';
        }
        return path;
    }

    private static void closeSocket(char[][] grid, LayoutRoom first, LayoutRoom second, DoorSocket socket) {
        LayoutRoom owner = first.sockets().contains(socket) ? first : second;
        owner.removeSocket(socket);
        grid[socket.tileRow][socket.tileColumn] = 'x';
    }

    /**
     * The socket a room uses toward another: the facing side first (the axis of the larger centre offset), then
     * the other axis, then the two remaining sides. On each side an existing socket is reused; otherwise the
     * legal position (R10) closest to the other room's centre is opened.
     */
    private static DoorSocket socketToward(char[][] grid, LayoutRoom room, LayoutRoom other, RouteMasks masks,
                                           List<DoorSocket> opened) {
        int differenceColumn = other.centerColumn() - room.centerColumn();
        int differenceRow    = other.centerRow() - room.centerRow();
        DoorSocket.Side horizontal = differenceColumn >= 0 ? DoorSocket.Side.EAST : DoorSocket.Side.WEST;
        DoorSocket.Side vertical   = differenceRow >= 0 ? DoorSocket.Side.NORTH : DoorSocket.Side.SOUTH;
        List<DoorSocket.Side> order = new ArrayList<>();
        if (Math.abs(differenceColumn) >= Math.abs(differenceRow)) {
            order.add(horizontal);
            order.add(vertical);
        } else {
            order.add(vertical);
            order.add(horizontal);
        }
        for (DoorSocket.Side side : DoorSocket.Side.values()) if (!order.contains(side)) order.add(side);
        for (DoorSocket.Side side : order) {
            for (DoorSocket existing : room.sockets()) {
                if (existing.side == side) return existing;
            }
            DoorSocket fresh = openOnSide(grid, room, side, other.centerColumn(), other.centerRow(), masks);
            if (fresh != null) {
                opened.add(fresh);
                return fresh;
            }
        }
        return null;
    }

    private static DoorSocket openOnSide(char[][] grid, LayoutRoom room, DoorSocket.Side side, int towardColumn,
                                         int towardRow, RouteMasks masks) {
        List<int[]> candidates = new ArrayList<>();
        if (side.isHorizontalWall()) {
            int tileRow = side.stepRow > 0 ? room.topRow : room.bottomRow;
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                candidates.add(new int[]{ tileColumn, tileRow });
            }
        } else {
            int tileColumn = side.stepColumn > 0 ? room.rightColumn : room.leftColumn;
            for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
                candidates.add(new int[]{ tileColumn, tileRow });
            }
        }
        candidates.sort((first, second) -> {
            int firstDistance  = side.isHorizontalWall() ? Math.abs(first[0] - towardColumn) : Math.abs(first[1] - towardRow);
            int secondDistance = side.isHorizontalWall() ? Math.abs(second[0] - towardColumn) : Math.abs(second[1] - towardRow);
            return firstDistance != secondDistance ? firstDistance - secondDistance
                    : side.isHorizontalWall() ? first[0] - second[0] : first[1] - second[1];
        });
        int height = grid.length;
        int width  = grid[0].length;
        for (int[] candidate : candidates) {
            if (!DoorwayRules.canPlaceSocket(room, candidate[0], candidate[1], width, height)) continue;
            int outerColumn = candidate[0] + side.stepColumn;
            int outerRow    = candidate[1] + side.stepRow;
            if (masks.rooms[outerRow][outerColumn]) continue;
            return DoorwayRules.openSocket(grid, room, candidate[0], candidate[1]);
        }
        return null;
    }

    /**
     * Dresses every room but the entrance (R22): a special from its kit (a DENSE kit RELAXED once the floor's tight
     * room is spent), a regular room from one of the regular kits (R23). Then the R20 terminal guarantee.
     */
    static void dressRooms(RoomDresser dresser, List<LayoutRoom> rooms, LevelGenConfig config) {
        RoomBlueprintRegistry registry = RoomBlueprints.rooms();
        RoomBlueprint standard = registry.get(RoomBlueprints.ID_STANDARD);
        for (LayoutRoom room : rooms) {
            if (room.role == LevelLayoutReport.Role.ENTRANCE) continue;
            if (room.role == LevelLayoutReport.Role.REGULAR) {
                dresser.dress(room, dresser.pickRegularKit(room, standard.regularKits(),
                        config != null ? config.preferredRegularKit : null), null);
            } else {
                RoomBlueprint blueprint = registry.get(room.blueprintId);
                dresser.dress(room, blueprint.kit(), blueprint.relaxedKit());
            }
        }
        guaranteeTerminal(dresser, rooms, registry);
    }

    /**
     * R20: every combat floor carries a usable terminal (the story's LOG_FOUND beats fire off them). If no kit
     * produced one, a regular room is re-dressed as an OFFICE; with none that takes, the OFFICE terminal row is
     * added to the first terminal-allowed special.
     */
    private static void guaranteeTerminal(RoomDresser dresser, List<LayoutRoom> rooms, RoomBlueprintRegistry registry) {
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

    /** Carves every room's interior to lit floor ' '. */
    static void carveInteriors(char[][] grid, List<LayoutRoom> rooms) {
        for (LayoutRoom room : rooms) {
            for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
                for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                    grid[tileRow][tileColumn] = ' ';
                }
            }
        }
    }

    /**
     * Hangs the door leaves (R14): a SPECIAL room's sockets always get a door (LEVEL_GEN_SPECIAL_DOOR_CHANCE), every
     * other socket rolls LEVEL_GEN_DOOR_CHANCE, otherwise it stays an open one-tile doorway.
     */
    static void hangDoors(char[][] grid, List<LayoutRoom> rooms, Random random) {
        for (LayoutRoom room : rooms) {
            float chance = room.role == LevelLayoutReport.Role.SPECIAL
                    ? LevelGenConstants.LEVEL_GEN_SPECIAL_DOOR_CHANCE : LevelGenConstants.LEVEL_GEN_DOOR_CHANCE;
            for (DoorSocket socket : room.sockets()) {
                if (random.nextFloat() < chance) DoorwayRules.hangDoor(grid, socket);
            }
        }
    }

    /**
     * The hall pass (R28): spaced 'P' columns along each 3-wide hall's spine (outside every clearance zone, never
     * sealing or narrowing the hall to a plug), then walkable decals on hall tiles at LEVEL_GEN_HALL_DECAL_CHANCE,
     * never on a socket's outer tile. A hall tile is a walkable tile outside every room footprint.
     */
    static void dressHalls(char[][] grid, List<LayoutRoom> rooms, List<List<int[]>> wideHallSpines,
                           RoomDresser dresser, Random random) {
        boolean[][] footprint = DoorwayRules.roomFootprintMask(rooms, grid[0].length, grid.length);
        int spacing = LevelGenConstants.LEVEL_GEN_WIDE_HALLWAY_COLUMN_SPACING;
        for (List<int[]> spine : wideHallSpines) {
            int eligible = 0;
            for (int[] tile : spine) {
                if (!isPlainFloor(grid[tile[1]][tile[0]]) || dresser.isInClearance(tile[0], tile[1])) continue;
                eligible++;
                if (eligible % spacing == 0) tryHallColumn(grid, tile[0], tile[1], dresser);
            }
        }
        java.util.Set<Long> socketOuterTiles = new java.util.HashSet<>();
        for (LayoutRoom room : rooms) {
            for (DoorSocket socket : room.sockets()) socketOuterTiles.add(key(socket.outerColumn(), socket.outerRow()));
        }
        for (int tileRow = 1; tileRow < grid.length - 1; tileRow++) {
            for (int tileColumn = 1; tileColumn < grid[0].length - 1; tileColumn++) {
                if (footprint[tileRow][tileColumn] || grid[tileRow][tileColumn] != 'l') continue;
                if (socketOuterTiles.contains(key(tileColumn, tileRow))) continue;
                if (random.nextFloat() >= LevelGenConstants.LEVEL_GEN_HALL_DECAL_CHANCE) continue;
                grid[tileRow][tileColumn] = HALL_DECALS.charAt(random.nextInt(HALL_DECALS.length()));
            }
        }
    }

    /**
     * One hall column (R28): only on plain floor outside every clearance zone, never touching another column, only
     * where the hall is open on all four sides, never plugging a passage and never stranding a tile. Returns
     * whether it was placed.
     */
    static boolean tryHallColumn(char[][] grid, int tileColumn, int tileRow, RoomDresser dresser) {
        if (tileRow <= 0 || tileColumn <= 0 || tileRow >= grid.length - 1 || tileColumn >= grid[0].length - 1) return false;
        if (!isPlainFloor(grid[tileRow][tileColumn]) || dresser.isInClearance(tileColumn, tileRow)) return false;
        if (touchesColumn(grid, tileColumn, tileRow)) return false;
        boolean openAllRound = isOpen(grid, tileColumn, tileRow + 1) && isOpen(grid, tileColumn, tileRow - 1)
                && isOpen(grid, tileColumn + 1, tileRow) && isOpen(grid, tileColumn - 1, tileRow);
        if (!openAllRound) return false;
        int[] neighbour = { tileColumn + 1, tileRow };
        int openBefore = countReachable(grid, neighbour);
        char previous = grid[tileRow][tileColumn];
        grid[tileRow][tileColumn] = 'P';
        if (FloorPopulator.wouldPlugPassage(grid, tileColumn, tileRow) || countReachable(grid, neighbour) != openBefore - 1) {
            grid[tileRow][tileColumn] = previous;
            return false;
        }
        return true;
    }

    /**
     * The connectivity backstop (R30): while any standable tile is unreachable from the spawn, carve an L from the
     * spawn to it. A carve that would cut a room's shell opens a legal socket there instead when it can. Returns
     * how many repair carves fired (target: zero — construction should never need it).
     */
    static int repairConnectivity(char[][] grid, List<LayoutRoom> rooms, int spawnColumn, int spawnRow) {
        int carves = 0;
        int safetyCap = grid.length + grid[0].length;
        for (int iteration = 0; iteration < safetyCap; iteration++) {
            boolean[][] reachable = reachableFrom(grid, spawnColumn, spawnRow);
            int targetColumn = -1, targetRow = -1;
            for (int tileRow = 0; tileRow < grid.length && targetColumn < 0; tileRow++) {
                for (int tileColumn = 0; tileColumn < grid[0].length; tileColumn++) {
                    if (!reachable[tileRow][tileColumn] && isOpen(grid, tileColumn, tileRow)) {
                        targetColumn = tileColumn;
                        targetRow    = tileRow;
                        break;
                    }
                }
            }
            if (targetColumn < 0) return carves;
            carves++;
            for (int tileColumn = spawnColumn; ; tileColumn += Integer.signum(targetColumn - tileColumn)) {
                carveRepairTile(grid, rooms, tileColumn, spawnRow);
                if (tileColumn == targetColumn) break;
            }
            for (int tileRow = spawnRow; ; tileRow += Integer.signum(targetRow - tileRow)) {
                carveRepairTile(grid, rooms, targetColumn, tileRow);
                if (tileRow == targetRow) break;
            }
        }
        return carves;
    }

    private static void carveRepairTile(char[][] grid, List<LayoutRoom> rooms, int tileColumn, int tileRow) {
        char cell = grid[tileRow][tileColumn];
        if (!Level.isWall(cell) && !Level.isPropSolid(cell) && !Level.isColumn(cell)) return;
        for (LayoutRoom room : rooms) {
            if (!room.isShellTile(tileColumn, tileRow)) continue;
            if (DoorwayRules.canPlaceSocket(room, tileColumn, tileRow, grid[0].length, grid.length)) {
                DoorwayRules.openSocket(grid, room, tileColumn, tileRow);
                return;
            }
        }
        grid[tileRow][tileColumn] = 'l';
    }

    /** Standable tiles reachable from a start (walls, solid props and columns block). */
    static boolean[][] reachableFrom(char[][] grid, int startColumn, int startRow) {
        boolean[][] reached = new boolean[grid.length][grid[0].length];
        if (!isOpen(grid, startColumn, startRow)) return reached;
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        reached[startRow][startColumn] = true;
        queue.add(new int[]{ startColumn, startRow });
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            for (int direction = 0; direction < 4; direction++) {
                int column = current[0] + STEP_COLUMNS[direction], row = current[1] + STEP_ROWS[direction];
                if (!isOpen(grid, column, row) || reached[row][column]) continue;
                reached[row][column] = true;
                queue.add(new int[]{ column, row });
            }
        }
        return reached;
    }

    private static int countReachable(char[][] grid, int[] from) {
        int count = 0;
        for (boolean[] row : reachableFrom(grid, from[0], from[1])) for (boolean cell : row) if (cell) count++;
        return count;
    }

    /** Whether any standable interior tile of a room is marked reachable. */
    static boolean isRoomReachable(boolean[][] reachable, char[][] grid, LayoutRoom room) {
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (reachable[tileRow][tileColumn]) return true;
            }
        }
        return false;
    }

    /** room index per interior tile, {@link SupplySlotProvider#CONNECTOR_REGION} elsewhere. */
    static int[][] supplyRegionMap(List<LayoutRoom> rooms, int gridWidth, int gridHeight) {
        int[][] map = new int[gridHeight][gridWidth];
        for (int[] row : map) java.util.Arrays.fill(row, SupplySlotProvider.CONNECTOR_REGION);
        for (int roomIndex = 0; roomIndex < rooms.size(); roomIndex++) {
            LayoutRoom room = rooms.get(roomIndex);
            for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
                for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                    if (tileRow >= 0 && tileRow < gridHeight && tileColumn >= 0 && tileColumn < gridWidth) {
                        map[tileRow][tileColumn] = roomIndex;
                    }
                }
            }
        }
        return map;
    }

    /** The layout report every rebuilt generator attaches (the audit's only window into its rooms). */
    static LevelLayoutReport report(List<LayoutRoom> rooms, List<String> plannedSpecials, int repairCarves,
                                    DoorwayRules.Validation validation) {
        LevelLayoutReport report = new LevelLayoutReport().plannedSpecials(plannedSpecials).repairCarves(repairCarves)
                .doorwayValidation(validation.flankFailures, validation.demotions);
        for (LayoutRoom room : rooms) report.room(room.toRecord());
        return report;
    }

    private static boolean touchesColumn(char[][] grid, int tileColumn, int tileRow) {
        for (int direction = 0; direction < 4; direction++) {
            int column = tileColumn + STEP_COLUMNS[direction], row = tileRow + STEP_ROWS[direction];
            if (row >= 0 && row < grid.length && column >= 0 && column < grid[0].length && grid[row][column] == 'P') return true;
        }
        return false;
    }

    static boolean isOpen(char[][] grid, int tileColumn, int tileRow) {
        if (tileRow < 0 || tileRow >= grid.length || tileColumn < 0 || tileColumn >= grid[0].length) return false;
        char cell = grid[tileRow][tileColumn];
        return !Level.isWall(cell) && !Level.isPropSolid(cell) && !Level.isColumn(cell);
    }

    static boolean isPlainFloor(char cell) {
        return cell == ' ' || cell == 'l' || cell == 'u' || cell == 'f';
    }

    private static long key(int tileColumn, int tileRow) {
        return ((long) tileRow << 32) | (tileColumn & 0xFFFFFFFFL);
    }
}
