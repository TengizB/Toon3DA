package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Procedural dungeon generator — five-phase algorithm inspired by Shattered Pixel Dungeon.
 *
 * Phase 1 — Room Placement:     randomly sized rectangular rooms placed without overlap.
 * Phase 2 — Connectivity:       greedy MST + optional loop corridors; single door pass.
 * Phase 3 — Decoration:         context-aware wall variety, floor lighting, props, hazard walls.
 * Phase 4 — Enemy Placement:    enemy spawns in non-entrance rooms, after props to avoid overlap.
 * Phase 5 — Connectivity Audit: BFS flood-fill from player spawn; emergency corridors for any
 *                                unreachable room (guaranteed passage, no doors, clears obstacles).
 *
 * Grid convention: (0,0) = bottom-left tile, Y-up (matches project-wide standard).
 * No LibGDX imports — pure Java logic, fully unit-testable without an OpenGL context.
 * Lives in the level package to access the package-private Level(char[][], List) constructor.
 *
 * Wall types used and their generation context:
 *   'x' plain      — default (majority of all walls)
 *   'c' conduit    — corridor-adjacent walls (pipes/utility feel)
 *   'v' vent       — room-adjacent walls (ventilation along room sides)
 *   't' terminal   — very rare room corners (1-2 per level)
 *   'w' wires      — sparse, near equipment props
 *   'h' hazard     — walls adjacent to explosive barrels 'E'; guaranteed rare minimum
 *   'r' rust       — walls bordering unlit ('u') floors or oil/blood decals; decay feel
 *   'G' gore       — walls adjacent to corpse ('m') and blood ('.') decals; infestation feel
 *   'k' bulkhead   — caps at corridor dead-ends and around exit stairs; sealed/heavy feel
 *
 * See docs/procedural-level-generation.txt for full algorithm documentation.
 */
public class LevelGenerator {

    private enum WallContext { CORRIDOR, ROOM, MIXED, INTERIOR }

    private final Random random;

    public LevelGenerator(long seed) {
        this.random = new Random(seed);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public Level generate() {
        char[][] grid = new char[Constants.LEVEL_GEN_GRID_HEIGHT][Constants.LEVEL_GEN_GRID_WIDTH];
        fillAll(grid, 'x');

        List<Room> rooms = placeRooms();
        if (rooms.size() < 2) return buildFallbackLevel();

        // Phase 1 — carve rooms and corridors
        carveRoomInteriors(grid, rooms);
        connectRoomsWithCorridors(grid, rooms);
        addLoopCorridors(grid, rooms);

        // Phase 2 — doors (single pass after ALL corridor carving is complete)
        placeDoors(grid);

        // Phase 3 — decoration
        assignFloorLighting(grid, rooms);
        assignWallVariety(grid);
        placePlayerSpawn(grid, rooms.get(0));
        placeProps(grid, rooms);
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

        // Phase 5 — connectivity audit (safety net; MST already guarantees connectivity
        //           in theory, but emergency corridors catch any edge-case failures)
        verifyAndRepairConnectivity(grid, rooms);

        // Phase 6 — stamp exactly one stairs-down exit; runs after the audit so the
        // chosen tile is provably reachable.
        stampStairsDown(grid, rooms);

        return new Level(grid, spawnPoints);
    }

    // -------------------------------------------------------------------------
    // Room data structure
    // -------------------------------------------------------------------------

    private static final class Room {
        final int leftColumn, bottomRow, rightColumn, topRow;

        Room(int leftColumn, int bottomRow, int rightColumn, int topRow) {
            this.leftColumn  = leftColumn;
            this.bottomRow   = bottomRow;
            this.rightColumn = rightColumn;
            this.topRow      = topRow;
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
     * Guarantees every room is reachable. Loop corridors are added separately afterwards.
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
            connected.add(nearestCandidate);
            unconnected.remove(nearestCandidate);
        }
    }

    /**
     * Adds loop corridors beyond the MST so the dungeon has multiple paths between areas.
     * Each attempt picks two random rooms and connects them if they are within the distance
     * threshold — creating shortcuts and navigation variety without blowing up corridor density.
     */
    private void addLoopCorridors(char[][] grid, List<Room> rooms) {
        int loopTarget   = Math.max(1, rooms.size() / 3);
        int loopsAdded   = 0;
        int maxAttempts  = loopTarget * 5;

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
     * Single door pass after ALL corridors (MST + loops) are carved.
     * Any 'l' corridor tile directly adjacent to a room-floor tile (' ', 'u', 'f') becomes
     * a door 'd' with LEVEL_GEN_DOOR_CHANCE probability — natural placement at every room entry.
     *
     * Must run before assignFloorLighting() since lighting converts some ' ' to 'l',
     * which the adjacency heuristic uses to distinguish room floor from corridor floor.
     */
    private void placeDoors(char[][] grid) {
        for (int tileRow = 1; tileRow < Constants.LEVEL_GEN_GRID_HEIGHT - 1; tileRow++) {
            for (int tileColumn = 1; tileColumn < Constants.LEVEL_GEN_GRID_WIDTH - 1; tileColumn++) {
                if (grid[tileRow][tileColumn] != 'l') continue;
                if (!isAdjacentToRoomFloor(grid, tileColumn, tileRow)) continue;
                if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
                if (random.nextFloat() < Constants.LEVEL_GEN_DOOR_CHANCE) {
                    grid[tileRow][tileColumn] = 'd';
                }
            }
        }
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

    // Only ' ', 'u', 'f' qualify as room-floor at door-detection time; 'l' is corridor floor.
    private boolean isRoomFloor(char[][] grid, int tileColumn, int tileRow) {
        if (!isInBounds(tileColumn, tileRow)) return false;
        char cell = grid[tileRow][tileColumn];
        return cell == ' ' || cell == 'u' || cell == 'f';
    }

    // -------------------------------------------------------------------------
    // Phase 3 — Decoration
    // -------------------------------------------------------------------------

    private void assignFloorLighting(char[][] grid, List<Room> rooms) {
        for (Room room : rooms) {
            int flickerBudget = 2 + random.nextInt(2);
            for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
                for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                    if (grid[tileRow][tileColumn] != ' ') continue;
                    float roll = random.nextFloat();
                    if (roll < 0.05f && flickerBudget > 0) {
                        grid[tileRow][tileColumn] = 'f';
                        flickerBudget--;
                    } else if (roll < 0.10f) {
                        grid[tileRow][tileColumn] = 'u';
                    } else if (roll < 0.35f) {
                        grid[tileRow][tileColumn] = 'l';
                    }
                    // else: keep as ' ' (lit floor, default)
                }
            }
        }
    }

    /**
     * Context-aware wall variety — each of the six wall types is used, with distributions
     * tuned to the architectural context of that wall:
     *
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
            // ' ', 'u', 'f' are purely room-interior; 'l' could be room or corridor, treat as corridor
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
                // Utility corridor: conduit pipes dominant, sparse wires
                if (roll < 0.30f) return 'c';
                if (roll < 0.38f) return 'w';
                return 'x';

            case ROOM:
                // Room walls: vents, very rare terminal alcoves and hazard zones
                if (roll < 0.005f) return 'h'; // hazard — rare old danger marking
                if (roll < 0.015f) return 't'; // terminal — corner alcove
                if (roll < 0.025f) return 'w'; // wires — near equipment
                if (roll < 0.105f) return 'v'; // vent — 1–2 per room side
                if (roll < 0.185f) return 'c'; // conduit
                return 'x';

            case MIXED:
                // Junction walls: moderate conduit + vent blend
                if (roll < 0.01f)  return 't';
                if (roll < 0.03f)  return 'w';
                if (roll < 0.08f)  return 'v';
                if (roll < 0.25f)  return 'c';
                return 'x';

            default:
                return 'x';
        }
    }

    private void placePlayerSpawn(char[][] grid, Room startRoom) {
        grid[startRoom.centerRow()][startRoom.centerColumn()] = 'p';
    }

    private void placeProps(char[][] grid, List<Room> rooms) {
        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            Room room = rooms.get(roomIndex);
            for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
                for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                    if (isWalkableFloor(grid, tileColumn, tileRow)
                            && random.nextFloat() < Constants.LEVEL_GEN_PROP_CHANCE) {
                        grid[tileRow][tileColumn] = randomPropChar();
                    }
                }
            }
        }
    }

    private char randomPropChar() {
        float roll = random.nextFloat();
        if (roll < 0.22f) return 'g';
        if (roll < 0.30f) return 'E';
        if (roll < 0.40f) return 'C';
        if (roll < 0.50f) return 'T';
        if (roll < 0.58f) return 'L';
        if (roll < 0.74f) return '.';
        if (roll < 0.88f) return 'm';
        return 'O';
    }

    /**
     * Stamps hazard walls ('h') on wall tiles adjacent to explosive barrels ('E').
     * Runs after placeProps so barrel positions are finalised.
     * This ensures contextually appropriate 'h' walls appear near danger areas.
     */
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
                            && random.nextFloat() < Constants.LEVEL_GEN_HAZARD_WALL_CHANCE) {
                        grid[neighborRow][neighborColumn] = 'h';
                    }
                }
            }
        }
    }

    /**
     * Post-pass: reskins 'x' walls adjacent to unlit ('u') floor tiles to rust ('r') walls,
     * and also reskins 'x' walls adjacent to oil ('O') or blood ('.') decal tiles.
     * Rust spreads contextually — not every eligible wall converts, keeping visual variety.
     */
    private void placeRustWallsNearUnlit(char[][] grid) {
        int[] deltaColumns = { 0,  0,  1, -1 };
        int[] deltaRows    = { 1, -1,  0,  0 };
        for (int tileRow = 0; tileRow < Constants.LEVEL_GEN_GRID_HEIGHT; tileRow++) {
            for (int tileColumn = 0; tileColumn < Constants.LEVEL_GEN_GRID_WIDTH; tileColumn++) {
                if (grid[tileRow][tileColumn] != 'x') continue;
                boolean nearUnlit    = false;
                boolean nearDecal    = false;
                for (int direction = 0; direction < 4; direction++) {
                    int neighborColumn = tileColumn + deltaColumns[direction];
                    int neighborRow    = tileRow    + deltaRows[direction];
                    if (!isInBounds(neighborColumn, neighborRow)) continue;
                    char neighbor = grid[neighborRow][neighborColumn];
                    if (neighbor == 'u')                nearUnlit = true;
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

    /**
     * Post-pass: reskins 'x' walls adjacent to corpse ('m') or blood ('.') decal tiles to
     * gore ('G') walls. These are placed after enemies and props so all decal positions are final.
     */
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

    /**
     * Post-pass: places bulkhead ('k') walls as structural caps at dead-end corridor termini
     * and adjacent to the exit stairs tile.
     * A dead-end is a walkable floor tile with exactly one walkable cardinal neighbour.
     * The wall directly opposite the corridor entry (facing away from the only open direction)
     * receives the bulkhead stamp. This makes dead-ends read as deliberate sealed passages
     * rather than generator cut-offs.
     */
    private void placeBulkheadWallsAtDeadEnds(char[][] grid) {
        int[] deltaColumns = { 0,  0,  1, -1 };
        int[] deltaRows    = { 1, -1,  0,  0 };
        for (int tileRow = 0; tileRow < Constants.LEVEL_GEN_GRID_HEIGHT; tileRow++) {
            for (int tileColumn = 0; tileColumn < Constants.LEVEL_GEN_GRID_WIDTH; tileColumn++) {
                char cell = grid[tileRow][tileColumn];
                // Bulkhead caps for dead-end corridor floors and the stairs exit
                boolean isDeadEndFloor = false;
                boolean isStairs       = (cell == Constants.STAIRS_DOWN_CHAR);
                if (cell == 'l' || cell == ' ' || cell == 'u') {
                    int walkableNeighborCount  = 0;
                    int wallNeighborCount      = 0;
                    for (int direction = 0; direction < 4; direction++) {
                        int neighborColumn = tileColumn + deltaColumns[direction];
                        int neighborRow    = tileRow    + deltaRows[direction];
                        if (!isInBounds(neighborColumn, neighborRow)) continue;
                        char neighbor = grid[neighborRow][neighborColumn];
                        if (isWalkableFloor(grid, neighborColumn, neighborRow)) walkableNeighborCount++;
                        if (Level.isWall(neighbor))                             wallNeighborCount++;
                    }
                    isDeadEndFloor = (walkableNeighborCount == 1 && wallNeighborCount >= 3);
                }
                if (!isDeadEndFloor && !isStairs) continue;

                for (int direction = 0; direction < 4; direction++) {
                    int neighborColumn = tileColumn + deltaColumns[direction];
                    int neighborRow    = tileRow    + deltaRows[direction];
                    if (!isInBounds(neighborColumn, neighborRow)) continue;
                    char neighbor = grid[neighborRow][neighborColumn];
                    // Stamp plain walls (preserve any already-themed wall types)
                    if (neighbor == 'x') {
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
        int placed     = 0;
        int attempts   = 0;
        while (placed < enemyCount && attempts < 50) {
            attempts++;
            int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
            int tileRow    = room.bottomRow  + 1 + random.nextInt(room.interiorHeight());
            if (isWalkableFloor(grid, tileColumn, tileRow)) {
                char spawnChar = random.nextFloat() < Constants.LEVEL_GEN_CORRUPTOR_RATIO ? '1' : '2';
                spawnPoints.add(new EnemySpawnPoint(spawnChar, tileColumn, tileRow));
                placed++;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase 5 — Connectivity audit (BFS + emergency corridors)
    // -------------------------------------------------------------------------

    /**
     * Flood-fills the dungeon from the player-spawn room's center. Any room whose center
     * tile is not reachable receives an emergency corridor: a door-free L-shaped path
     * that clears walls and solid props to guarantee passage.
     *
     * Doors count as passable for this check — the player can open all doors.
     * The BFS uses an explicit stack (pre-allocated, no java.util.Queue) to avoid
     * any heap allocation overhead inside the connectivity phase.
     */
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

        // Pre-allocated stack — size = total tile count, worst-case upper bound.
        int capacity    = gridWidth * gridHeight;
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
        // Doors are passable — the player can open them.
        return !Level.isWall(cell) && !Level.isPropSolid(cell) && !Level.isColumn(cell);
    }

    /**
     * Carves a door-free L-shaped emergency corridor from entrance to a disconnected room.
     * Replaces walls (any variant) and solid props with open corridor 'l'. Existing floor
     * tiles and doors are preserved. No doors are added — the path must stay permanently open.
     */
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
     * Places exactly one stairs-down tile in the room furthest from spawn (last-placed room,
     * since MST tends to push later rooms away from the start). Ensures the surrounding
     * floor tiles are lit so the grate is never hidden in a dark zone.
     *
     * Falls back to earlier rooms if the last room has no walkable interior floor,
     * and ultimately to the start room as a last resort.
     */
    private void stampStairsDown(char[][] grid, List<Room> rooms) {
        for (int roomIndex = rooms.size() - 1; roomIndex >= 1; roomIndex--) {
            Room exitRoom = rooms.get(roomIndex);
            if (tryStampInRoom(grid, exitRoom)) return;
        }
        // Fallback: start room (very rare — only if all other rooms are prop-filled)
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
