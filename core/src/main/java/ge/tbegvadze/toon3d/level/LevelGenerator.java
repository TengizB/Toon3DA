package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Procedural dungeon generator — six-phase algorithm inspired by Shattered Pixel Dungeon.
 *
 * Phase 1 — Room Placement:     randomly sized rectangular rooms placed without overlap,
 *                                typed as ENTRANCE / STANDARD / LARGE / SERVER_ROOM.
 * Phase 2 — Connectivity:       greedy MST + optional loop corridors; 2-3 MST edges
 *                                widened to 3-tile grand hallways with centre-line columns.
 *                                Single door pass after all corridor carving.
 * Phase 3 — Decoration:         context-aware wall variety, room-type-specific floor
 *                                lighting, patterned columns, room-type prop layout,
 *                                pickups, and hazard walls.
 * Phase 4 — Enemy Placement:    enemy spawns in non-entrance rooms, after props.
 * Phase 5 — Connectivity Audit: BFS flood-fill from player spawn; emergency corridors for
 *                                any unreachable room.
 * Phase 6 — Stairs:             exactly one exit in the furthest / largest landmark room.
 *
 * Room types:
 *   ENTRANCE    — player spawn room; fully lit, no hazards.
 *   STANDARD    — baseline UAC lab room; existing decoration logic.
 *   LARGE       — landmark arena (reactor floor, cargo bay); patterned columns, sparse props.
 *   SERVER_ROOM — data vault; terminal walls 't', rack rows of T/L props, dark atmosphere.
 *
 * Wide hallways: 2-3 MST edges widened to 3 tiles (centre lit, ribs normal) with evenly
 * spaced 'P' columns along the centre spine. The width-3 invariant guarantees at least one
 * 1-tile lane on each side of every column — passage is never sealed.
 *
 * Grid convention: (0,0) = bottom-left tile, Y-up (matches project-wide standard).
 * No LibGDX imports — pure Java logic, fully unit-testable without an OpenGL context.
 * Lives in the level package to access the package-private Level(char[][], List) constructor.
 */
public class LevelGenerator {

    private enum WallContext { CORRIDOR, ROOM, MIXED, INTERIOR }

    private enum RoomType { ENTRANCE, STANDARD, LARGE, SERVER_ROOM }

    private final Random         random;
    private final LevelGenConfig config;

    // MST room-pair references captured during connectivity so widenSelectedCorridors()
    // can re-carve chosen edges at width 3 without re-running the MST selection.
    private List<Room[]> mstEdgeRooms;

    // Centre-spine tiles from each widened corridor, recorded during carving so
    // placeWideHallwayColumns() can walk them in order and space columns evenly.
    private List<int[]> wideHallwaySpineTiles;

    public LevelGenerator(long seed) {
        this(seed, new LevelGenConfig());
    }

    public LevelGenerator(long seed, LevelGenConfig config) {
        this.random = new Random(seed);
        this.config = config;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public Level generate() {
        char[][] grid = new char[Constants.LEVEL_GEN_GRID_HEIGHT][Constants.LEVEL_GEN_GRID_WIDTH];
        fillAll(grid, 'x');
        mstEdgeRooms       = new ArrayList<>();
        wideHallwaySpineTiles = new ArrayList<>();

        List<Room> rooms = placeRooms();
        if (rooms.size() < 2) return buildFallbackLevel();

        assignRoomTypes(rooms);

        // Phase 1 — carve rooms and corridors
        carveRoomInteriors(grid, rooms);
        connectRoomsWithCorridors(grid, rooms);
        addLoopCorridors(grid, rooms);

        // Phase 1b — widen 2-3 MST edges to 3-tile grand hallways
        if (config.enableWideHallways) {
            widenSelectedCorridors(grid, rooms);
        }

        // Phase 2 — doors (single pass after ALL corridor carving is complete)
        placeDoors(grid);

        // Phase 3 — decoration
        assignFloorLighting(grid, rooms);
        assignWallVariety(grid);
        themeServerRoomWalls(grid, rooms);
        placePlayerSpawn(grid, rooms.get(0));
        placeColumns(grid, rooms);
        placeLargeRoomColumns(grid, rooms);
        placeWideHallwayColumns(grid);
        placeProps(grid, rooms);
        placeServerRoomProps(grid, rooms);
        placeLargeRoomProps(grid, rooms);
        placePickups(grid, rooms);
        placeHazardWallsNearBarrels(grid);

        // Phase 4 — enemies (after props so spawns land on walkable tiles only)
        List<EnemySpawnPoint> spawnPoints = new ArrayList<>();
        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            spawnEnemiesInRoom(grid, rooms.get(roomIndex), spawnPoints);
        }

        // Phase 4b — atmospheric wall theming (post-pass after enemies so corpse/den density is final)
        placeRustWallsNearUnlit(grid);
        placeGoreWallsNearCorpses(grid);
        placeBulkheadWallsAtDeadEnds(grid);

        // Phase 5 — connectivity audit
        verifyAndRepairConnectivity(grid, rooms);

        // Phase 6 — stamp exactly one stairs-down exit; prefer a LARGE room
        stampStairsDown(grid, rooms);

        return new Level(grid, spawnPoints);
    }

    // -------------------------------------------------------------------------
    // Room data structure
    // -------------------------------------------------------------------------

    private static final class Room {
        final int leftColumn, bottomRow, rightColumn, topRow;
        RoomType type;

        Room(int leftColumn, int bottomRow, int rightColumn, int topRow) {
            this.leftColumn  = leftColumn;
            this.bottomRow   = bottomRow;
            this.rightColumn = rightColumn;
            this.topRow      = topRow;
            this.type        = RoomType.STANDARD;
        }

        int centerColumn()   { return (leftColumn + rightColumn) / 2; }
        int centerRow()      { return (bottomRow  + topRow)      / 2; }
        int interiorWidth()  { return rightColumn - leftColumn - 1; }
        int interiorHeight() { return topRow      - bottomRow  - 1; }

        boolean overlaps(Room other) {
            int margin = Constants.LEVEL_GEN_ROOM_MARGIN;
            return leftColumn   < other.rightColumn  + margin
                && rightColumn  > other.leftColumn   - margin
                && bottomRow    < other.topRow       + margin
                && topRow       > other.bottomRow    - margin;
        }
    }

    // -------------------------------------------------------------------------
    // Phase 1 — Room placement
    // -------------------------------------------------------------------------

    private List<Room> placeRooms() {
        List<Room> rooms    = new ArrayList<>();
        int        attempts = 0;

        while (rooms.size() < Constants.LEVEL_GEN_TARGET_ROOMS
                && attempts < Constants.LEVEL_GEN_PLACEMENT_TRIES) {
            attempts++;

            int interiorWidth  = randomBetween(Constants.LEVEL_GEN_ROOM_MIN_WIDTH,
                                               Constants.LEVEL_GEN_ROOM_MAX_WIDTH);
            int interiorHeight = randomBetween(Constants.LEVEL_GEN_ROOM_MIN_HEIGHT,
                                               Constants.LEVEL_GEN_ROOM_MAX_HEIGHT);
            int totalWidth     = interiorWidth  + 2;
            int totalHeight    = interiorHeight + 2;

            int maxLeftColumn = Constants.LEVEL_GEN_GRID_WIDTH  - totalWidth  - 1;
            int maxBottomRow  = Constants.LEVEL_GEN_GRID_HEIGHT - totalHeight - 1;
            if (maxLeftColumn < 1 || maxBottomRow < 1) continue;

            int leftColumn = 1 + random.nextInt(maxLeftColumn);
            int bottomRow  = 1 + random.nextInt(maxBottomRow);
            Room candidate = new Room(leftColumn, bottomRow,
                                      leftColumn + totalWidth  - 1,
                                      bottomRow  + totalHeight - 1);

            boolean overlapsExisting = false;
            for (Room existing : rooms) {
                if (candidate.overlaps(existing)) {
                    overlapsExisting = true;
                    break;
                }
            }
            if (!overlapsExisting) rooms.add(candidate);
        }
        return rooms;
    }

    /**
     * Assigns RoomType to every placed room.
     *
     * Room 0 is always ENTRANCE (player spawn). Remaining rooms are classified by size first,
     * then weighted roll, with hard caps so special rooms stay rare:
     *   - LARGE:       interiorWidth >= LARGE_MIN_DIM AND interiorHeight >= LARGE_MIN_DIM,
     *                  roll < LARGE_ROOM_CHANCE; capped at LARGE_ROOM_MAX_PER_LEVEL.
     *   - SERVER_ROOM: any remaining room (large-ineligible or failed LARGE roll),
     *                  roll < SERVER_ROOM_CHANCE; capped at SERVER_ROOM_MAX_PER_LEVEL.
     *   - STANDARD:    everything else (guaranteed majority ~65-75 %).
     */
    private void assignRoomTypes(List<Room> rooms) {
        rooms.get(0).type = RoomType.ENTRANCE;
        int largeRoomCount  = 0;
        int serverRoomCount = 0;
        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            Room    room          = rooms.get(roomIndex);
            boolean largeEligible = room.interiorWidth()  >= Constants.LEVEL_GEN_LARGE_MIN_DIM
                                 && room.interiorHeight() >= Constants.LEVEL_GEN_LARGE_MIN_DIM;
            if (config.enableLargeRooms
                    && largeEligible
                    && largeRoomCount < Constants.LEVEL_GEN_LARGE_ROOM_MAX_PER_LEVEL
                    && random.nextFloat() < Constants.LEVEL_GEN_LARGE_ROOM_CHANCE) {
                room.type = RoomType.LARGE;
                largeRoomCount++;
            } else if (config.enableServerRooms
                    && serverRoomCount < Constants.LEVEL_GEN_SERVER_ROOM_MAX_PER_LEVEL
                    && random.nextFloat() < Constants.LEVEL_GEN_SERVER_ROOM_CHANCE) {
                room.type = RoomType.SERVER_ROOM;
                serverRoomCount++;
            }
            // else: remains STANDARD
        }
    }

    // -------------------------------------------------------------------------
    // Phase 2 — Connectivity
    // -------------------------------------------------------------------------

    private void carveRoomInteriors(char[][] grid, List<Room> rooms) {
        for (Room room : rooms) {
            for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
                for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                    grid[tileRow][tileColumn] = ' ';
                }
            }
        }
    }

    /**
     * Greedy MST: always connects the nearest unconnected room to the already-connected set.
     * Each connected pair is stored in mstEdgeRooms so widenSelectedCorridors() can
     * promote chosen edges to width 3 after the full 1-tile network is in place.
     */
    private void connectRoomsWithCorridors(char[][] grid, List<Room> rooms) {
        List<Room> connected   = new ArrayList<>();
        List<Room> unconnected = new ArrayList<>(rooms);
        connected.add(unconnected.remove(0));

        while (!unconnected.isEmpty()) {
            Room nearestCandidate  = null;
            Room connectionSource  = null;
            int  minimumDistance   = Integer.MAX_VALUE;

            for (Room connectedRoom : connected) {
                for (Room candidate : unconnected) {
                    int distance = manhattanDistance(connectedRoom, candidate);
                    if (distance < minimumDistance) {
                        minimumDistance  = distance;
                        nearestCandidate = candidate;
                        connectionSource = connectedRoom;
                    }
                }
            }

            if (nearestCandidate == null) break;
            carveLShapedCorridor(grid, connectionSource, nearestCandidate);
            mstEdgeRooms.add(new Room[]{ connectionSource, nearestCandidate });
            connected.add(nearestCandidate);
            unconnected.remove(nearestCandidate);
        }
    }

    /**
     * Adds loop corridors beyond the MST so the dungeon has multiple paths between areas.
     */
    private void addLoopCorridors(char[][] grid, List<Room> rooms) {
        int loopTarget  = Math.max(1, rooms.size() / 3);
        int loopsAdded  = 0;
        int maxAttempts = loopTarget * 5;

        for (int attempt = 0; attempt < maxAttempts && loopsAdded < loopTarget; attempt++) {
            int indexA = random.nextInt(rooms.size());
            int indexB = random.nextInt(rooms.size());
            if (indexA == indexB) continue;
            Room roomA = rooms.get(indexA);
            Room roomB = rooms.get(indexB);
            if (manhattanDistance(roomA, roomB) <= Constants.LEVEL_GEN_LOOP_MAX_DISTANCE) {
                carveLShapedCorridor(grid, roomA, roomB);
                loopsAdded++;
            }
        }
    }

    /**
     * Promotes LEVEL_GEN_WIDE_HALLWAY_COUNT MST edges from 1-tile to 3-tile grand corridors.
     * Prefers edges that touch an ENTRANCE or LARGE room (main arteries feel most natural
     * leading to/from landmark rooms). Falls back to any MST edge when priority ones run out.
     *
     * Each widened corridor records its centre-spine tiles in wideHallwaySpineTiles so
     * placeWideHallwayColumns() can space 'P' columns along it evenly.
     */
    private void widenSelectedCorridors(char[][] grid, List<Room> rooms) {
        if (mstEdgeRooms.isEmpty()) return;

        List<Room[]> priorityEdges = new ArrayList<>();
        List<Room[]> otherEdges    = new ArrayList<>();
        for (Room[] edge : mstEdgeRooms) {
            boolean touchesLandmark = edge[0].type == RoomType.ENTRANCE
                                   || edge[0].type == RoomType.LARGE
                                   || edge[1].type == RoomType.ENTRANCE
                                   || edge[1].type == RoomType.LARGE;
            if (touchesLandmark) {
                priorityEdges.add(edge);
            } else {
                otherEdges.add(edge);
            }
        }

        List<Room[]> candidates = new ArrayList<>(priorityEdges);
        candidates.addAll(otherEdges);

        int hallwaysWidened = 0;
        for (Room[] edge : candidates) {
            if (hallwaysWidened >= Constants.LEVEL_GEN_WIDE_HALLWAY_COUNT) break;
            carveWideLShapedCorridor(grid, edge[0], edge[1]);
            hallwaysWidened++;
        }
    }

    private void carveLShapedCorridor(char[][] grid, Room fromRoom, Room toRoom) {
        int fromColumn = fromRoom.centerColumn();
        int fromRow    = fromRoom.centerRow();
        int toColumn   = toRoom.centerColumn();
        int toRow      = toRoom.centerRow();

        if (random.nextBoolean()) {
            carveHorizontalSegment(grid, fromRow,    fromColumn, toColumn);
            carveVerticalSegment(grid,   toColumn,   fromRow,    toRow);
        } else {
            carveVerticalSegment(grid,   fromColumn, fromRow,    toRow);
            carveHorizontalSegment(grid, toRow,      fromColumn, toColumn);
        }
    }

    /**
     * Carves a 3-tile-wide L-shaped corridor. The centre spine is lit (' ') for a bright
     * runway; the two rib tiles perpendicular to travel are normal ('l') for dimmer shoulders.
     * The first segment records its spine tiles so columns can be placed later.
     */
    private void carveWideLShapedCorridor(char[][] grid, Room fromRoom, Room toRoom) {
        int fromColumn = fromRoom.centerColumn();
        int fromRow    = fromRoom.centerRow();
        int toColumn   = toRoom.centerColumn();
        int toRow      = toRoom.centerRow();

        if (random.nextBoolean()) {
            carveWideHorizontalSegment(grid, fromRow,    fromColumn, toColumn, true);
            carveWideVerticalSegment(grid,   toColumn,   fromRow,    toRow,    true);
        } else {
            carveWideVerticalSegment(grid,   fromColumn, fromRow,    toRow,    true);
            carveWideHorizontalSegment(grid, toRow,      fromColumn, toColumn, true);
        }
    }

    /**
     * Carves a 3-tile-wide horizontal corridor segment.
     * Centre row → lit (' '); top/bottom ribs → normal ('l') when currently solid 'x'.
     * recordSpine=true appends each centre tile to wideHallwaySpineTiles.
     */
    private void carveWideHorizontalSegment(char[][] grid, int centerRow,
                                            int column1, int column2, boolean recordSpine) {
        int minColumn = Math.min(column1, column2);
        int maxColumn = Math.max(column1, column2);
        for (int tileColumn = minColumn; tileColumn <= maxColumn; tileColumn++) {
            if (isInBounds(tileColumn, centerRow) && isCarveableForWide(grid[centerRow][tileColumn])) {
                grid[centerRow][tileColumn] = ' ';
                if (recordSpine) wideHallwaySpineTiles.add(new int[]{ tileColumn, centerRow });
            }
            if (isInBounds(tileColumn, centerRow + 1) && grid[centerRow + 1][tileColumn] == 'x') {
                grid[centerRow + 1][tileColumn] = 'l';
            }
            if (isInBounds(tileColumn, centerRow - 1) && grid[centerRow - 1][tileColumn] == 'x') {
                grid[centerRow - 1][tileColumn] = 'l';
            }
        }
    }

    /**
     * Carves a 3-tile-wide vertical corridor segment.
     * Centre column → lit (' '); left/right ribs → normal ('l') when currently solid 'x'.
     * recordSpine=true appends each centre tile to wideHallwaySpineTiles.
     */
    private void carveWideVerticalSegment(char[][] grid, int centerColumn,
                                          int row1, int row2, boolean recordSpine) {
        int minRow = Math.min(row1, row2);
        int maxRow = Math.max(row1, row2);
        for (int tileRow = minRow; tileRow <= maxRow; tileRow++) {
            if (isInBounds(centerColumn, tileRow) && isCarveableForWide(grid[tileRow][centerColumn])) {
                grid[tileRow][centerColumn] = ' ';
                if (recordSpine) wideHallwaySpineTiles.add(new int[]{ centerColumn, tileRow });
            }
            if (isInBounds(centerColumn + 1, tileRow) && grid[tileRow][centerColumn + 1] == 'x') {
                grid[tileRow][centerColumn + 1] = 'l';
            }
            if (isInBounds(centerColumn - 1, tileRow) && grid[tileRow][centerColumn - 1] == 'x') {
                grid[tileRow][centerColumn - 1] = 'l';
            }
        }
    }

    private boolean isCarveableForWide(char cell) {
        return cell == 'x' || cell == 'l';
    }

    private void carveHorizontalSegment(char[][] grid, int fixedRow, int column1, int column2) {
        int minColumn = Math.min(column1, column2);
        int maxColumn = Math.max(column1, column2);
        for (int tileColumn = minColumn; tileColumn <= maxColumn; tileColumn++) {
            if (isInBounds(tileColumn, fixedRow) && grid[fixedRow][tileColumn] == 'x') {
                grid[fixedRow][tileColumn] = 'l';
            }
        }
    }

    private void carveVerticalSegment(char[][] grid, int fixedColumn, int row1, int row2) {
        int minRow = Math.min(row1, row2);
        int maxRow = Math.max(row1, row2);
        for (int tileRow = minRow; tileRow <= maxRow; tileRow++) {
            if (isInBounds(fixedColumn, tileRow) && grid[tileRow][fixedColumn] == 'x') {
                grid[tileRow][fixedColumn] = 'l';
            }
        }
    }

    /**
     * Single door pass after ALL corridors (MST + loops + widening) are carved.
     * Any 'l' corridor tile directly adjacent to a room-floor tile (' ', 'u', 'f') becomes
     * a door 'd' with LEVEL_GEN_DOOR_CHANCE probability — natural placement at every room entry.
     *
     * Wide-corridor entry tiles typically lack the wall-on-two-opposing-sides geometry
     * required by isDoorwayAligned(), so doors are naturally suppressed there — wide halls
     * feel more open and unobstructed.
     */
    private void placeDoors(char[][] grid) {
        for (int tileRow = 1; tileRow < Constants.LEVEL_GEN_GRID_HEIGHT - 1; tileRow++) {
            for (int tileColumn = 1; tileColumn < Constants.LEVEL_GEN_GRID_WIDTH - 1; tileColumn++) {
                if (grid[tileRow][tileColumn] != 'l') continue;
                if (!isAdjacentToRoomFloor(grid, tileColumn, tileRow)) continue;
                if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
                if (!isDoorwayAligned(grid, tileColumn, tileRow)) continue;
                if (random.nextFloat() < Constants.LEVEL_GEN_DOOR_CHANCE) {
                    grid[tileRow][tileColumn] = 'd';
                }
            }
        }
    }

    private boolean isDoorwayAligned(char[][] grid, int tileColumn, int tileRow) {
        boolean wallNorth = isWallAt(grid, tileColumn, tileRow + 1);
        boolean wallSouth = isWallAt(grid, tileColumn, tileRow - 1);
        boolean wallEast  = isWallAt(grid, tileColumn + 1, tileRow);
        boolean wallWest  = isWallAt(grid, tileColumn - 1, tileRow);
        return (wallNorth && wallSouth) || (wallEast && wallWest);
    }

    private boolean isWallAt(char[][] grid, int tileColumn, int tileRow) {
        if (!isInBounds(tileColumn, tileRow)) return true;
        return Level.isWall(grid[tileRow][tileColumn]);
    }

    private boolean isAdjacentToDoor(char[][] grid, int tileColumn, int tileRow) {
        return isDoorCell(grid, tileColumn,     tileRow + 1)
            || isDoorCell(grid, tileColumn,     tileRow - 1)
            || isDoorCell(grid, tileColumn + 1, tileRow)
            || isDoorCell(grid, tileColumn - 1, tileRow);
    }

    private boolean isDoorCell(char[][] grid, int tileColumn, int tileRow) {
        if (!isInBounds(tileColumn, tileRow)) return false;
        return grid[tileRow][tileColumn] == 'd';
    }

    private boolean isAdjacentToRoomFloor(char[][] grid, int tileColumn, int tileRow) {
        return isRoomFloor(grid, tileColumn,     tileRow + 1)
            || isRoomFloor(grid, tileColumn,     tileRow - 1)
            || isRoomFloor(grid, tileColumn + 1, tileRow)
            || isRoomFloor(grid, tileColumn - 1, tileRow);
    }

    private boolean isRoomFloor(char[][] grid, int tileColumn, int tileRow) {
        if (!isInBounds(tileColumn, tileRow)) return false;
        char cell = grid[tileRow][tileColumn];
        return cell == ' ' || cell == 'u' || cell == 'f';
    }

    // -------------------------------------------------------------------------
    // Phase 3 — Decoration
    // -------------------------------------------------------------------------

    /**
     * Assigns floor lighting for each room type:
     *   ENTRANCE    — fully lit ' ' (safe start zone, no conversion).
     *   LARGE       — mostly lit ' ', ring of 'l' at edges, one small 'u' dark alcove.
     *   SERVER_ROOM — dark 'u' dominant, sprinkle of 'f' flickering, no bright ' '.
     *   STANDARD    — existing mixed logic (config-driven unlitFloors / normalFloors).
     */
    private void assignFloorLighting(char[][] grid, List<Room> rooms) {
        for (Room room : rooms) {
            if (room.type == RoomType.ENTRANCE) continue;
            if (room.type == RoomType.SERVER_ROOM) {
                assignServerRoomFloor(grid, room);
            } else if (room.type == RoomType.LARGE) {
                assignLargeRoomFloor(grid, room);
            } else {
                assignStandardRoomFloor(grid, room);
            }
        }
    }

    private void assignServerRoomFloor(char[][] grid, Room room) {
        int flickerBudget = 2;
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                float roll = random.nextFloat();
                if (roll < Constants.LEVEL_GEN_SERVER_FLICKER_CHANCE && flickerBudget > 0) {
                    grid[tileRow][tileColumn] = 'f';
                    flickerBudget--;
                } else if (roll < 0.55f) {
                    grid[tileRow][tileColumn] = 'u';
                } else {
                    grid[tileRow][tileColumn] = 'l';
                }
            }
        }
    }

    private void assignLargeRoomFloor(char[][] grid, Room room) {
        // One dark alcove in a random corner for hidden-loot tension
        boolean hasAlcove   = random.nextBoolean();
        int     alcoveColumn = random.nextBoolean() ? room.leftColumn + 1  : room.rightColumn - 1;
        int     alcoveRow    = random.nextBoolean() ? room.bottomRow  + 1  : room.topRow      - 1;
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                if (hasAlcove
                        && Math.abs(tileColumn - alcoveColumn) <= 1
                        && Math.abs(tileRow    - alcoveRow)    <= 1) {
                    grid[tileRow][tileColumn] = 'u';
                    continue;
                }
                // Edge ring slightly dimmer for depth
                boolean isEdgeTile = tileColumn == room.leftColumn  + 1
                                  || tileColumn == room.rightColumn - 1
                                  || tileRow    == room.bottomRow   + 1
                                  || tileRow    == room.topRow      - 1;
                if (isEdgeTile && random.nextFloat() < 0.50f) {
                    grid[tileRow][tileColumn] = 'l';
                }
                // Centre stays ' ' (lit) — the grand-hall bright-runway feeling
            }
        }
    }

    private void assignStandardRoomFloor(char[][] grid, Room room) {
        int flickerBudget = config.flickeringFloors ? 2 + random.nextInt(2) : 0;
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                float roll = random.nextFloat();
                if (config.flickeringFloors && roll < 0.05f && flickerBudget > 0) {
                    grid[tileRow][tileColumn] = 'f';
                    flickerBudget--;
                } else if (config.unlitFloors && roll < 0.10f) {
                    grid[tileRow][tileColumn] = 'u';
                } else if (config.normalFloors && roll < 0.35f) {
                    grid[tileRow][tileColumn] = 'l';
                }
            }
        }
    }

    /**
     * Context-aware wall variety — distributions tuned to architectural context:
     *   CORRIDOR  walls: 'c' conduit heavy (pipes run along utility passages)
     *   ROOM      walls: 'v' vent heavy (ventilation along room sides); rare 't', 'h'
     *   MIXED     walls: balanced blend at corridor-room junctions
     *   INTERIOR  walls: left as 'x' (deep solid mass, never visible)
     */
    private void assignWallVariety(char[][] grid) {
        for (int tileRow = 0; tileRow < Constants.LEVEL_GEN_GRID_HEIGHT; tileRow++) {
            for (int tileColumn = 0; tileColumn < Constants.LEVEL_GEN_GRID_WIDTH; tileColumn++) {
                if (grid[tileRow][tileColumn] != 'x') continue;
                WallContext context = classifyWallContext(grid, tileColumn, tileRow);
                if (context == WallContext.INTERIOR) continue;
                grid[tileRow][tileColumn] = randomWallCharForContext(context);
            }
        }
    }

    private WallContext classifyWallContext(char[][] grid, int tileColumn, int tileRow) {
        boolean adjacentToRoom     = false;
        boolean adjacentToCorridor = false;
        int[] deltaColumns = { 0,  0,  1, -1 };
        int[] deltaRows    = { 1, -1,  0,  0 };
        for (int direction = 0; direction < 4; direction++) {
            int neighborColumn = tileColumn + deltaColumns[direction];
            int neighborRow    = tileRow    + deltaRows[direction];
            if (!isInBounds(neighborColumn, neighborRow)) continue;
            char neighbor = grid[neighborRow][neighborColumn];
            if (neighbor == ' ' || neighbor == 'u' || neighbor == 'f') adjacentToRoom     = true;
            if (neighbor == 'l' || neighbor == 'd')                    adjacentToCorridor = true;
        }
        if (!adjacentToRoom && !adjacentToCorridor) return WallContext.INTERIOR;
        if (adjacentToRoom && adjacentToCorridor)   return WallContext.MIXED;
        if (adjacentToCorridor)                     return WallContext.CORRIDOR;
        return WallContext.ROOM;
    }

    private char randomWallCharForContext(WallContext context) {
        float roll = random.nextFloat();
        switch (context) {
            case CORRIDOR:
                if (roll < 0.30f) return 'c';
                if (roll < 0.38f) return 'w';
                return 'x';
            case ROOM:
                if (roll < 0.005f) return 'h';
                if (roll < 0.015f) return 't';
                if (roll < 0.025f) return 'w';
                if (roll < 0.105f) return 'v';
                if (roll < 0.185f) return 'c';
                return 'x';
            case MIXED:
                if (roll < 0.01f)  return 't';
                if (roll < 0.03f)  return 'w';
                if (roll < 0.08f)  return 'v';
                if (roll < 0.25f)  return 'c';
                return 'x';
            default:
                return 'x';
        }
    }

    /**
     * Overrides perimeter walls of every SERVER_ROOM to terminal walls 't' with
     * SERVER_WALL_TERMINAL_CHANCE probability. The remaining ~20% keep whatever
     * generic variety was assigned, giving a 80/20 blend.
     *
     * Runs AFTER assignWallVariety() so it overrides the generic vent/conduit theming
     * for server rooms only — non-server rooms are unaffected.
     */
    private void themeServerRoomWalls(char[][] grid, List<Room> rooms) {
        for (Room room : rooms) {
            if (room.type != RoomType.SERVER_ROOM) continue;
            for (int tileRow = room.bottomRow; tileRow <= room.topRow; tileRow++) {
                for (int tileColumn = room.leftColumn; tileColumn <= room.rightColumn; tileColumn++) {
                    if (!isInBounds(tileColumn, tileRow)) continue;
                    if (!Level.isWall(grid[tileRow][tileColumn])) continue;
                    if (!facesRoomInterior(grid, tileColumn, tileRow, room)) continue;
                    if (random.nextFloat() < Constants.LEVEL_GEN_SERVER_WALL_TERMINAL_CHANCE) {
                        grid[tileRow][tileColumn] = 't';
                    }
                }
            }
        }
    }

    /**
     * Returns true when a wall tile at (tileColumn, tileRow) has at least one cardinal
     * neighbour that is non-wall floor inside the given room's interior bounds.
     */
    private boolean facesRoomInterior(char[][] grid, int tileColumn, int tileRow, Room room) {
        int[] deltaColumns = { 0, 0, 1, -1 };
        int[] deltaRows    = { 1, -1, 0, 0 };
        for (int direction = 0; direction < 4; direction++) {
            int neighborColumn = tileColumn + deltaColumns[direction];
            int neighborRow    = tileRow    + deltaRows[direction];
            if (!isInBounds(neighborColumn, neighborRow)) continue;
            boolean insideInterior = neighborColumn > room.leftColumn
                                  && neighborColumn < room.rightColumn
                                  && neighborRow    > room.bottomRow
                                  && neighborRow    < room.topRow;
            if (insideInterior && !Level.isWall(grid[neighborRow][neighborColumn])) {
                return true;
            }
        }
        return false;
    }

    private void placePlayerSpawn(char[][] grid, Room startRoom) {
        grid[startRoom.centerRow()][startRoom.centerColumn()] = 'p';
    }

    /**
     * Places columns in STANDARD rooms only (LARGE and SERVER_ROOM have custom handlers).
     * Columns are only placed in rooms large enough to avoid blocking passage, and never
     * adjacent to another column. Controlled by LevelGenConfig column fields.
     */
    private void placeColumns(char[][] grid, List<Room> rooms) {
        if (!config.columns) return;
        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            Room room = rooms.get(roomIndex);
            if (room.type == RoomType.LARGE || room.type == RoomType.SERVER_ROOM) continue;
            if (room.interiorWidth()  < config.columnMinRoomSize) continue;
            if (room.interiorHeight() < config.columnMinRoomSize) continue;
            if (random.nextFloat() > config.columnChancePerRoom)  continue;

            int columnCount = config.columnMinCount
                    + random.nextInt(config.columnMaxCount - config.columnMinCount + 1);
            int placed   = 0;
            int attempts = 0;
            while (placed < columnCount && attempts < 30) {
                attempts++;
                int tileColumn = room.leftColumn + 2 + random.nextInt(Math.max(1, room.interiorWidth()  - 2));
                int tileRow    = room.bottomRow  + 2 + random.nextInt(Math.max(1, room.interiorHeight() - 2));
                if (isWalkableFloor(grid, tileColumn, tileRow)
                        && !isAdjacentToColumn(grid, tileColumn, tileRow)
                        && !isAdjacentToDoor(grid, tileColumn, tileRow)
                        && !isAdjacentToDoorAxis(grid, tileColumn, tileRow)) {
                    grid[tileRow][tileColumn] = 'P';
                    placed++;
                }
            }
        }
    }

    /**
     * Places columns in LARGE rooms using one of three deliberate architectural patterns:
     *   SYMMETRIC_FOUR  — columns at the four interior quadrant centres (classic pillared hall).
     *   CENTRE_AVENUE   — row of columns down the long axis, spaced 3 tiles.
     *   PERIMETER_RING  — columns at mid-edges just inside the walls (open arena centre).
     */
    private void placeLargeRoomColumns(char[][] grid, List<Room> rooms) {
        if (!config.columns) return;
        for (Room room : rooms) {
            if (room.type != RoomType.LARGE) continue;
            int pattern = random.nextInt(3);
            switch (pattern) {
                case 0: placeLargeColumnsSymmetricFour(grid, room);  break;
                case 1: placeLargeColumnsCentreAvenue(grid, room);   break;
                default: placeLargeColumnsPerimeterRing(grid, room); break;
            }
        }
    }

    private void placeLargeColumnsSymmetricFour(char[][] grid, Room room) {
        int quarterWidth  = room.interiorWidth()  / 4;
        int quarterHeight = room.interiorHeight() / 4;
        int[] quadrantColumns = {
            room.leftColumn  + 1 + quarterWidth,
            room.rightColumn - 1 - quarterWidth
        };
        int[] quadrantRows = {
            room.bottomRow   + 1 + quarterHeight,
            room.topRow      - 1 - quarterHeight
        };
        for (int quadrantColumn : quadrantColumns) {
            for (int quadrantRow : quadrantRows) {
                tryPlaceColumnAt(grid, quadrantColumn, quadrantRow);
            }
        }
    }

    private void placeLargeColumnsCentreAvenue(char[][] grid, Room room) {
        int spacing = 3;
        if (room.interiorWidth() >= room.interiorHeight()) {
            int fixedRow = room.centerRow();
            for (int tileColumn = room.leftColumn + 2; tileColumn < room.rightColumn - 1; tileColumn += spacing) {
                tryPlaceColumnAt(grid, tileColumn, fixedRow);
            }
        } else {
            int fixedColumn = room.centerColumn();
            for (int tileRow = room.bottomRow + 2; tileRow < room.topRow - 1; tileRow += spacing) {
                tryPlaceColumnAt(grid, fixedColumn, tileRow);
            }
        }
    }

    private void placeLargeColumnsPerimeterRing(char[][] grid, Room room) {
        int midColumn = room.centerColumn();
        int midRow    = room.centerRow();
        tryPlaceColumnAt(grid, midColumn,            room.bottomRow + 2);
        tryPlaceColumnAt(grid, midColumn,            room.topRow    - 2);
        tryPlaceColumnAt(grid, room.leftColumn  + 2, midRow);
        tryPlaceColumnAt(grid, room.rightColumn - 2, midRow);
    }

    private void tryPlaceColumnAt(char[][] grid, int tileColumn, int tileRow) {
        if (!isInBounds(tileColumn, tileRow)) return;
        if (!isWalkableFloor(grid, tileColumn, tileRow)) return;
        if (isAdjacentToColumn(grid, tileColumn, tileRow)) return;
        if (isAdjacentToDoor(grid, tileColumn, tileRow)) return;
        if (isAdjacentToDoorAxis(grid, tileColumn, tileRow)) return;
        grid[tileRow][tileColumn] = 'P';
    }

    /**
     * Places 'P' columns along the centre-spine tiles of widened corridors.
     * Columns are placed every LEVEL_GEN_WIDE_HALLWAY_COLUMN_SPACING eligible spine tiles.
     * A tile is eligible when it is walkable floor and not adjacent to a door.
     *
     * Perpendicular clearance is verified before each placement: at least one of the two
     * axes must have walkable tiles on both sides so the column never seals the corridor.
     */
    private void placeWideHallwayColumns(char[][] grid) {
        int spacing       = Constants.LEVEL_GEN_WIDE_HALLWAY_COLUMN_SPACING;
        int eligibleCount = 0;
        for (int[] spinePoint : wideHallwaySpineTiles) {
            int tileColumn = spinePoint[0];
            int tileRow    = spinePoint[1];
            if (!isWalkableFloor(grid, tileColumn, tileRow)) continue;
            if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
            eligibleCount++;
            if (eligibleCount % spacing != 0) continue;
            if (isAdjacentToColumn(grid, tileColumn, tileRow)) continue;
            if (isAdjacentToDoorAxis(grid, tileColumn, tileRow)) continue;
            boolean northClear = isWalkableFloor(grid, tileColumn, tileRow + 1);
            boolean southClear = isWalkableFloor(grid, tileColumn, tileRow - 1);
            boolean eastClear  = isWalkableFloor(grid, tileColumn + 1, tileRow);
            boolean westClear  = isWalkableFloor(grid, tileColumn - 1, tileRow);
            if ((northClear && southClear) || (eastClear && westClear)) {
                grid[tileRow][tileColumn] = 'P';
            }
        }
    }

    private boolean isAdjacentToColumn(char[][] grid, int tileColumn, int tileRow) {
        int[] deltaColumns = { 0, 0, 1, -1 };
        int[] deltaRows    = { 1, -1, 0, 0 };
        for (int direction = 0; direction < 4; direction++) {
            int neighborColumn = tileColumn + deltaColumns[direction];
            int neighborRow    = tileRow    + deltaRows[direction];
            if (!isInBounds(neighborColumn, neighborRow)) continue;
            if (grid[neighborRow][neighborColumn] == 'P') return true;
        }
        return false;
    }

    private void placeProps(char[][] grid, List<Room> rooms) {
        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            Room room = rooms.get(roomIndex);
            // SERVER_ROOM and LARGE rooms have dedicated prop handlers
            if (room.type == RoomType.SERVER_ROOM || room.type == RoomType.LARGE) continue;
            for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
                for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                    if (!isWalkableFloor(grid, tileColumn, tileRow)) continue;
                    if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
                    if (random.nextFloat() < Constants.LEVEL_GEN_PROP_CHANCE) {
                        char propChar = randomPropChar();
                        if (Level.isPropSolid(propChar) && isAdjacentToDoorAxis(grid, tileColumn, tileRow)) continue;
                        if (propChar != '\0') grid[tileRow][tileColumn] = propChar;
                    }
                }
            }
        }
    }

    /**
     * Places props in SERVER_ROOM rooms as rack rows of terminals ('T') and lockers ('L'),
     * reading as a UAC data vault — dense, claustrophobic, tactically dangerous.
     *
     * Racks run along the short axis (perpendicular to the longer axis) so they fill the
     * room most naturally. Rack rows start at the second interior row (leaving a clear entry
     * buffer at the walls) and alternate: rack row / walking lane / rack row / walking lane.
     *
     * Atmosphere props (oil pools, radioactive barrel) are scattered afterward to trigger
     * the existing rust-wall post-pass and sell the "this room is leaking" decay feel.
     */
    private void placeServerRoomProps(char[][] grid, List<Room> rooms) {
        for (Room room : rooms) {
            if (room.type != RoomType.SERVER_ROOM) continue;
            placeServerRoomRacks(grid, room);
            tryPlaceAtmosphericProp(grid, room, 'O');
            if (random.nextBoolean()) tryPlaceAtmosphericProp(grid, room, 'O');
            tryPlaceAtmosphericPropNearWall(grid, room, 'g');
        }
    }

    private void placeServerRoomRacks(char[][] grid, Room room) {
        boolean horizontalRacks = room.interiorWidth() >= room.interiorHeight();
        if (horizontalRacks) {
            // Racks are horizontal rows; skip first and last interior row as entry buffers
            for (int tileRow = room.bottomRow + 2; tileRow < room.topRow - 1; tileRow++) {
                int rowOffset = tileRow - (room.bottomRow + 2);
                if (rowOffset % 2 != 0) continue; // odd offsets are walking lanes
                for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                    if (!isWalkableFloor(grid, tileColumn, tileRow)) continue;
                    if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
                    if (isAdjacentToDoorAxis(grid, tileColumn, tileRow)) continue;
                    char prop = random.nextFloat() < Constants.LEVEL_GEN_SERVER_LOCKER_RATIO ? 'L' : 'T';
                    grid[tileRow][tileColumn] = prop;
                }
            }
        } else {
            // Racks are vertical columns; skip first and last interior column as entry buffers
            for (int tileColumn = room.leftColumn + 2; tileColumn < room.rightColumn - 1; tileColumn++) {
                int columnOffset = tileColumn - (room.leftColumn + 2);
                if (columnOffset % 2 != 0) continue; // odd offsets are walking lanes
                for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
                    if (!isWalkableFloor(grid, tileColumn, tileRow)) continue;
                    if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
                    if (isAdjacentToDoorAxis(grid, tileColumn, tileRow)) continue;
                    char prop = random.nextFloat() < Constants.LEVEL_GEN_SERVER_LOCKER_RATIO ? 'L' : 'T';
                    grid[tileRow][tileColumn] = prop;
                }
            }
        }
    }

    /**
     * Places sparse props in LARGE rooms at LEVEL_GEN_LARGE_PROP_CHANCE density (~half the
     * global rate). Crates ('C') and radioactive barrels ('g') only — no blood/corpse clutter
     * so the open floor reads as a combat arena rather than a slaughter zone.
     * One optional explosive barrel ('E') as a tactical hazard near existing columns.
     */
    private void placeLargeRoomProps(char[][] grid, List<Room> rooms) {
        for (Room room : rooms) {
            if (room.type != RoomType.LARGE) continue;
            for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
                for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                    if (!isWalkableFloor(grid, tileColumn, tileRow)) continue;
                    if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
                    if (isAdjacentToDoorAxis(grid, tileColumn, tileRow)) continue;
                    if (random.nextFloat() < Constants.LEVEL_GEN_LARGE_PROP_CHANCE) {
                        char prop = random.nextFloat() < 0.70f ? 'C' : 'g';
                        grid[tileRow][tileColumn] = prop;
                    }
                }
            }
            // One explosive barrel for tactical interest (30 % chance per large room)
            if (config.explosiveBarrels && random.nextFloat() < 0.30f) {
                tryPlaceAtmosphericPropNearWall(grid, room, 'E');
            }
        }
    }

    private void tryPlaceAtmosphericProp(char[][] grid, Room room, char propChar) {
        for (int attempt = 0; attempt < 15; attempt++) {
            int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
            int tileRow    = room.bottomRow  + 1 + random.nextInt(room.interiorHeight());
            if (isWalkableFloor(grid, tileColumn, tileRow)
                    && !isAdjacentToDoor(grid, tileColumn, tileRow)) {
                grid[tileRow][tileColumn] = propChar;
                return;
            }
        }
    }

    private void tryPlaceAtmosphericPropNearWall(char[][] grid, Room room, char propChar) {
        for (int attempt = 0; attempt < 20; attempt++) {
            int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
            int tileRow    = room.bottomRow  + 1 + random.nextInt(room.interiorHeight());
            if (!isWalkableFloor(grid, tileColumn, tileRow)) continue;
            if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
            if (isAdjacentToDoorAxis(grid, tileColumn, tileRow)) continue;
            if (isAdjacentToAnyWall(grid, tileColumn, tileRow)) {
                grid[tileRow][tileColumn] = propChar;
                return;
            }
        }
    }

    private boolean isAdjacentToAnyWall(char[][] grid, int tileColumn, int tileRow) {
        int[] deltaColumns = { 0, 0, 1, -1 };
        int[] deltaRows    = { 1, -1, 0, 0 };
        for (int direction = 0; direction < 4; direction++) {
            int neighborColumn = tileColumn + deltaColumns[direction];
            int neighborRow    = tileRow    + deltaRows[direction];
            if (!isInBounds(neighborColumn, neighborRow)) continue;
            if (Level.isWall(grid[neighborRow][neighborColumn])) return true;
        }
        return false;
    }

    private boolean isAdjacentToDoorAxis(char[][] grid, int tileColumn, int tileRow) {
        int[] deltaColumns = { 0, 0, 1, -1 };
        int[] deltaRows    = { 1, -1, 0, 0 };
        for (int direction = 0; direction < 4; direction++) {
            int neighborColumn = tileColumn + deltaColumns[direction];
            int neighborRow    = tileRow    + deltaRows[direction];
            if (!isInBounds(neighborColumn, neighborRow)) continue;
            if (grid[neighborRow][neighborColumn] != 'd') continue;
            boolean doorHasWallNorth = isWallAt(grid, neighborColumn, neighborRow + 1);
            boolean doorHasWallSouth = isWallAt(grid, neighborColumn, neighborRow - 1);
            boolean doorHasWallEast  = isWallAt(grid, neighborColumn + 1, neighborRow);
            boolean doorHasWallWest  = isWallAt(grid, neighborColumn - 1, neighborRow);
            if (doorHasWallNorth && doorHasWallSouth) {
                if (tileRow == neighborRow) return true;
            }
            if (doorHasWallEast && doorHasWallWest) {
                if (tileColumn == neighborColumn) return true;
            }
        }
        return false;
    }

    private char randomPropChar() {
        char[]  chars   = new char[8];
        float[] weights = new float[8];
        int     count   = 0;
        float   total   = 0f;

        if (config.radioactiveBarrels) { chars[count] = 'g'; weights[count++] = config.radioactiveBarrelWeight; total += config.radioactiveBarrelWeight; }
        if (config.explosiveBarrels)   { chars[count] = 'E'; weights[count++] = config.explosiveBarrelWeight;   total += config.explosiveBarrelWeight;   }
        if (config.crates)             { chars[count] = 'C'; weights[count++] = config.crateWeight;             total += config.crateWeight;             }
        if (config.computerTerminals)  { chars[count] = 'T'; weights[count++] = config.terminalWeight;          total += config.terminalWeight;          }
        if (config.lockers)            { chars[count] = 'L'; weights[count++] = config.lockerWeight;            total += config.lockerWeight;            }
        if (config.bloodStains)        { chars[count] = '.'; weights[count++] = config.bloodStainWeight;        total += config.bloodStainWeight;        }
        if (config.corpses)            { chars[count] = 'm'; weights[count++] = config.corpseWeight;            total += config.corpseWeight;            }
        if (config.oilPools)           { chars[count] = 'O'; weights[count++] = config.oilPoolWeight;           total += config.oilPoolWeight;           }

        if (count == 0 || total <= 0f) return '\0';

        float roll       = random.nextFloat() * total;
        float cumulative = 0f;
        for (int propIndex = 0; propIndex < count; propIndex++) {
            cumulative += weights[propIndex];
            if (roll < cumulative) return chars[propIndex];
        }
        return chars[count - 1];
    }

    /**
     * Places medkit ('H') and armour-kit ('A') pickups in non-entrance rooms.
     * SERVER_ROOM and LARGE rooms use boosted pickup chances — they are the loot hubs.
     */
    private void placePickups(char[][] grid, List<Room> rooms) {
        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            Room  room         = rooms.get(roomIndex);
            float medkitChance = config.medkitChancePerRoom;
            float armourChance = config.armourChancePerRoom;
            if (room.type == RoomType.SERVER_ROOM) {
                medkitChance = Constants.LEVEL_GEN_SERVER_MEDKIT_CHANCE;
                armourChance = Constants.LEVEL_GEN_SERVER_ARMOUR_CHANCE;
            } else if (room.type == RoomType.LARGE) {
                medkitChance = Constants.LEVEL_GEN_LARGE_MEDKIT_CHANCE;
                armourChance = Constants.LEVEL_GEN_LARGE_ARMOUR_CHANCE;
            }
            if (config.medkits    && random.nextFloat() < medkitChance) tryPlacePickup(grid, room, 'H');
            if (config.armourKits && random.nextFloat() < armourChance)  tryPlacePickup(grid, room, 'A');
        }
    }

    private void tryPlacePickup(char[][] grid, Room room, char pickupChar) {
        for (int attempt = 0; attempt < 20; attempt++) {
            int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
            int tileRow    = room.bottomRow  + 1 + random.nextInt(room.interiorHeight());
            if (isWalkableFloor(grid, tileColumn, tileRow)) {
                grid[tileRow][tileColumn] = pickupChar;
                return;
            }
        }
    }

    private void placeHazardWallsNearBarrels(char[][] grid) {
        int[] deltaColumns = { 0,  0,  1, -1 };
        int[] deltaRows    = { 1, -1,  0,  0 };
        for (int tileRow = 0; tileRow < Constants.LEVEL_GEN_GRID_HEIGHT; tileRow++) {
            for (int tileColumn = 0; tileColumn < Constants.LEVEL_GEN_GRID_WIDTH; tileColumn++) {
                if (grid[tileRow][tileColumn] != 'E') continue;
                for (int direction = 0; direction < 4; direction++) {
                    int neighborColumn = tileColumn + deltaColumns[direction];
                    int neighborRow    = tileRow    + deltaRows[direction];
                    if (!isInBounds(neighborColumn, neighborRow)) continue;
                    if (Level.isWall(grid[neighborRow][neighborColumn])
                            && grid[neighborRow][neighborColumn] == 'x'
                            && random.nextFloat() < Constants.LEVEL_GEN_HAZARD_WALL_CHANCE) {
                        grid[neighborRow][neighborColumn] = 'h';
                    }
                }
            }
        }
    }

    private void placeRustWallsNearUnlit(char[][] grid) {
        int[] deltaColumns = { 0,  0,  1, -1 };
        int[] deltaRows    = { 1, -1,  0,  0 };
        for (int tileRow = 0; tileRow < Constants.LEVEL_GEN_GRID_HEIGHT; tileRow++) {
            for (int tileColumn = 0; tileColumn < Constants.LEVEL_GEN_GRID_WIDTH; tileColumn++) {
                if (grid[tileRow][tileColumn] != 'x') continue;
                boolean nearUnlit = false;
                boolean nearDecal = false;
                for (int direction = 0; direction < 4; direction++) {
                    int neighborColumn = tileColumn + deltaColumns[direction];
                    int neighborRow    = tileRow    + deltaRows[direction];
                    if (!isInBounds(neighborColumn, neighborRow)) continue;
                    char neighbor = grid[neighborRow][neighborColumn];
                    if (neighbor == 'u')                    nearUnlit = true;
                    if (neighbor == 'O' || neighbor == '.') nearDecal = true;
                }
                if (nearUnlit && random.nextFloat() < Constants.LEVEL_GEN_RUST_WALL_CHANCE) {
                    grid[tileRow][tileColumn] = 'r';
                } else if (nearDecal && random.nextFloat() < Constants.LEVEL_GEN_RUST_OIL_CHANCE) {
                    grid[tileRow][tileColumn] = 'r';
                }
            }
        }
    }

    private void placeGoreWallsNearCorpses(char[][] grid) {
        int[] deltaColumns = { 0,  0,  1, -1 };
        int[] deltaRows    = { 1, -1,  0,  0 };
        for (int tileRow = 0; tileRow < Constants.LEVEL_GEN_GRID_HEIGHT; tileRow++) {
            for (int tileColumn = 0; tileColumn < Constants.LEVEL_GEN_GRID_WIDTH; tileColumn++) {
                if (grid[tileRow][tileColumn] != 'x') continue;
                boolean nearGore = false;
                for (int direction = 0; direction < 4; direction++) {
                    int neighborColumn = tileColumn + deltaColumns[direction];
                    int neighborRow    = tileRow    + deltaRows[direction];
                    if (!isInBounds(neighborColumn, neighborRow)) continue;
                    char neighbor = grid[neighborRow][neighborColumn];
                    if (neighbor == 'm' || neighbor == '.') nearGore = true;
                }
                if (nearGore && random.nextFloat() < Constants.LEVEL_GEN_GORE_WALL_CHANCE) {
                    grid[tileRow][tileColumn] = 'G';
                }
            }
        }
    }

    private void placeBulkheadWallsAtDeadEnds(char[][] grid) {
        int[] deltaColumns = { 0,  0,  1, -1 };
        int[] deltaRows    = { 1, -1,  0,  0 };
        for (int tileRow = 0; tileRow < Constants.LEVEL_GEN_GRID_HEIGHT; tileRow++) {
            for (int tileColumn = 0; tileColumn < Constants.LEVEL_GEN_GRID_WIDTH; tileColumn++) {
                char cell          = grid[tileRow][tileColumn];
                boolean isDeadEnd  = false;
                boolean isStairs   = (cell == Constants.STAIRS_DOWN_CHAR);
                if (cell == 'l' || cell == ' ' || cell == 'u') {
                    int walkableCount = 0;
                    int wallCount     = 0;
                    for (int direction = 0; direction < 4; direction++) {
                        int neighborColumn = tileColumn + deltaColumns[direction];
                        int neighborRow    = tileRow    + deltaRows[direction];
                        if (!isInBounds(neighborColumn, neighborRow)) continue;
                        char neighbor = grid[neighborRow][neighborColumn];
                        if (isWalkableFloor(grid, neighborColumn, neighborRow)) walkableCount++;
                        if (Level.isWall(neighbor))                             wallCount++;
                    }
                    isDeadEnd = (walkableCount == 1 && wallCount >= 3);
                }
                if (!isDeadEnd && !isStairs) continue;
                for (int direction = 0; direction < 4; direction++) {
                    int neighborColumn = tileColumn + deltaColumns[direction];
                    int neighborRow    = tileRow    + deltaRows[direction];
                    if (!isInBounds(neighborColumn, neighborRow)) continue;
                    if (grid[neighborRow][neighborColumn] == 'x') {
                        grid[neighborRow][neighborColumn] = 'k';
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase 4 — Enemy placement
    // -------------------------------------------------------------------------

    private void spawnEnemiesInRoom(char[][] grid, Room room, List<EnemySpawnPoint> spawnPoints) {
        int area       = room.interiorWidth() * room.interiorHeight();
        int enemyCount = Math.min(Constants.LEVEL_GEN_MAX_ENEMIES_PER_ROOM,
                                  1 + random.nextInt(Math.max(1, area / 6)));
        int[] placedColumns = new int[enemyCount];
        int[] placedRows    = new int[enemyCount];
        int   placed        = 0;
        int   attempts      = 0;
        while (placed < enemyCount && attempts < 50) {
            attempts++;
            int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
            int tileRow    = room.bottomRow  + 1 + random.nextInt(room.interiorHeight());
            if (!isWalkableFloor(grid, tileColumn, tileRow)) continue;
            boolean alreadyUsed = false;
            for (int placedIndex = 0; placedIndex < placed; placedIndex++) {
                if (placedColumns[placedIndex] == tileColumn && placedRows[placedIndex] == tileRow) {
                    alreadyUsed = true;
                    break;
                }
            }
            if (alreadyUsed) continue;
            placedColumns[placed] = tileColumn;
            placedRows[placed]    = tileRow;
            char spawnChar = randomEnemySpawnChar();
            spawnPoints.add(new EnemySpawnPoint(spawnChar, tileColumn, tileRow));
            placed++;
        }
    }

    private char randomEnemySpawnChar() {
        float roll = random.nextFloat();
        if (roll < Constants.LEVEL_GEN_CORRUPTOR_THRESHOLD)  return '1';
        if (roll < Constants.LEVEL_GEN_VORTEX_EYE_THRESHOLD) return '2';
        if (roll < Constants.LEVEL_GEN_GHOUL_THRESHOLD)       return '3';
        if (roll < Constants.LEVEL_GEN_CRAWLER_THRESHOLD)     return '4';
        return '5';
    }

    // -------------------------------------------------------------------------
    // Phase 5 — Connectivity audit (BFS + emergency corridors)
    // -------------------------------------------------------------------------

    private void verifyAndRepairConnectivity(char[][] grid, List<Room> rooms) {
        int startColumn = rooms.get(0).centerColumn();
        int startRow    = rooms.get(0).centerRow();

        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            Room room = rooms.get(roomIndex);
            if (!isTileReachable(grid, startColumn, startRow, room.centerColumn(), room.centerRow())) {
                carveEmergencyCorridor(grid, rooms.get(0), room);
            }
        }
    }

    private boolean isTileReachable(char[][] grid, int startColumn, int startRow,
                                    int targetColumn, int targetRow) {
        int gridWidth  = Constants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = Constants.LEVEL_GEN_GRID_HEIGHT;
        boolean[][] visited = new boolean[gridHeight][gridWidth];

        int capacity       = gridWidth * gridHeight;
        int[] stackColumns = new int[capacity];
        int[] stackRows    = new int[capacity];
        int   stackTop     = 0;

        visited[startRow][startColumn]    = true;
        stackColumns[stackTop] = startColumn;
        stackRows[stackTop]    = startRow;
        stackTop++;

        int[] deltaColumns = { 0,  0,  1, -1 };
        int[] deltaRows    = { 1, -1,  0,  0 };

        while (stackTop > 0) {
            stackTop--;
            int currentColumn = stackColumns[stackTop];
            int currentRow    = stackRows[stackTop];

            if (currentColumn == targetColumn && currentRow == targetRow) return true;

            for (int direction = 0; direction < 4; direction++) {
                int neighborColumn = currentColumn + deltaColumns[direction];
                int neighborRow    = currentRow    + deltaRows[direction];
                if (!isInBounds(neighborColumn, neighborRow)) continue;
                if (visited[neighborRow][neighborColumn]) continue;
                if (isBfsPassable(grid[neighborRow][neighborColumn])) {
                    visited[neighborRow][neighborColumn] = true;
                    stackColumns[stackTop] = neighborColumn;
                    stackRows[stackTop]    = neighborRow;
                    stackTop++;
                }
            }
        }
        return false;
    }

    private boolean isBfsPassable(char cell) {
        if (cell == '\0') return false;
        return !Level.isWall(cell) && !Level.isPropSolid(cell) && !Level.isColumn(cell);
    }

    private void carveEmergencyCorridor(char[][] grid, Room fromRoom, Room toRoom) {
        int fromColumn = fromRoom.centerColumn();
        int fromRow    = fromRoom.centerRow();
        int toColumn   = toRoom.centerColumn();
        int toRow      = toRoom.centerRow();
        carveEmergencyHorizontal(grid, fromRow, fromColumn, toColumn);
        carveEmergencyVertical(grid, toColumn, fromRow, toRow);
    }

    private void carveEmergencyHorizontal(char[][] grid, int fixedRow, int column1, int column2) {
        int minColumn = Math.min(column1, column2);
        int maxColumn = Math.max(column1, column2);
        for (int tileColumn = minColumn; tileColumn <= maxColumn; tileColumn++) {
            if (!isInBounds(tileColumn, fixedRow)) continue;
            char cell = grid[fixedRow][tileColumn];
            if (Level.isWall(cell) || Level.isPropSolid(cell)) {
                grid[fixedRow][tileColumn] = 'l';
            }
        }
    }

    private void carveEmergencyVertical(char[][] grid, int fixedColumn, int row1, int row2) {
        int minRow = Math.min(row1, row2);
        int maxRow = Math.max(row1, row2);
        for (int tileRow = minRow; tileRow <= maxRow; tileRow++) {
            if (!isInBounds(fixedColumn, tileRow)) continue;
            char cell = grid[tileRow][fixedColumn];
            if (Level.isWall(cell) || Level.isPropSolid(cell)) {
                grid[tileRow][fixedColumn] = 'l';
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase 6 — Stairs placement
    // -------------------------------------------------------------------------

    /**
     * Stamps exactly one stairs-down tile, preferring a LARGE room so the exit landmark
     * matches the scale of the grand hall (cinematic payoff). Falls back to any non-entrance
     * room (furthest first), then the entrance room as absolute last resort.
     */
    private void stampStairsDown(char[][] grid, List<Room> rooms) {
        // First pass: try LARGE rooms (furthest from start first)
        for (int roomIndex = rooms.size() - 1; roomIndex >= 1; roomIndex--) {
            Room room = rooms.get(roomIndex);
            if (room.type == RoomType.LARGE && tryStampInRoom(grid, room)) return;
        }
        // Second pass: any non-entrance room
        for (int roomIndex = rooms.size() - 1; roomIndex >= 1; roomIndex--) {
            if (tryStampInRoom(grid, rooms.get(roomIndex))) return;
        }
        tryStampInRoom(grid, rooms.get(0));
    }

    private boolean tryStampInRoom(char[][] grid, Room room) {
        int centerColumn = room.centerColumn();
        int centerRow    = room.centerRow();
        if (isWalkableFloor(grid, centerColumn, centerRow)) {
            grid[centerRow][centerColumn] = Constants.STAIRS_DOWN_CHAR;
            lightSurroundingFloor(grid, centerColumn, centerRow);
            return true;
        }
        for (int attempt = 0; attempt < 12; attempt++) {
            if (room.interiorWidth() <= 0 || room.interiorHeight() <= 0) break;
            int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
            int tileRow    = room.bottomRow  + 1 + random.nextInt(room.interiorHeight());
            if (isWalkableFloor(grid, tileColumn, tileRow)) {
                grid[tileRow][tileColumn] = Constants.STAIRS_DOWN_CHAR;
                lightSurroundingFloor(grid, tileColumn, tileRow);
                return true;
            }
        }
        return false;
    }

    private void lightSurroundingFloor(char[][] grid, int tileColumn, int tileRow) {
        int[] deltaColumns = { 0,  0,  1, -1 };
        int[] deltaRows    = { 1, -1,  0,  0 };
        for (int direction = 0; direction < 4; direction++) {
            int neighborColumn = tileColumn + deltaColumns[direction];
            int neighborRow    = tileRow    + deltaRows[direction];
            if (isInBounds(neighborColumn, neighborRow) && isWalkableFloor(grid, neighborColumn, neighborRow)) {
                grid[neighborRow][neighborColumn] = ' ';
            }
        }
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    private boolean isWalkableFloor(char[][] grid, int tileColumn, int tileRow) {
        if (!isInBounds(tileColumn, tileRow)) return false;
        char cell = grid[tileRow][tileColumn];
        return cell == ' ' || cell == 'l' || cell == 'u' || cell == 'f';
    }

    private boolean isInBounds(int tileColumn, int tileRow) {
        return tileColumn >= 0 && tileColumn < Constants.LEVEL_GEN_GRID_WIDTH
            && tileRow    >= 0 && tileRow    < Constants.LEVEL_GEN_GRID_HEIGHT;
    }

    private int manhattanDistance(Room roomA, Room roomB) {
        return Math.abs(roomA.centerColumn() - roomB.centerColumn())
             + Math.abs(roomA.centerRow()    - roomB.centerRow());
    }

    private int randomBetween(int minInclusive, int maxInclusive) {
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }

    private void fillAll(char[][] grid, char fillChar) {
        for (int tileRow = 0; tileRow < Constants.LEVEL_GEN_GRID_HEIGHT; tileRow++) {
            for (int tileColumn = 0; tileColumn < Constants.LEVEL_GEN_GRID_WIDTH; tileColumn++) {
                grid[tileRow][tileColumn] = fillChar;
            }
        }
    }

    private Level buildFallbackLevel() {
        char[][] grid = new char[Constants.LEVEL_GEN_GRID_HEIGHT][Constants.LEVEL_GEN_GRID_WIDTH];
        fillAll(grid, 'x');
        for (int tileRow = 20; tileRow <= 24; tileRow++) {
            for (int tileColumn = 36; tileColumn <= 43; tileColumn++) {
                grid[tileRow][tileColumn] = ' ';
            }
        }
        grid[22][40] = 'p';
        return new Level(grid, new ArrayList<>());
    }
}
