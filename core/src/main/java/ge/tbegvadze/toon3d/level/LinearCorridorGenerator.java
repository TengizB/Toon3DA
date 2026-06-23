package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.util.LevelGenConstants;
import ge.tbegvadze.toon3d.util.RenderConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Linear corridor dungeon generator — a single 3-tile-wide spine corridor runs most
 * of the grid with side rooms branching off both walls, like a vertebrate skeleton.
 * The spatial vocabulary is immediately distinct from {@link LevelGenerator}: instead of
 * scattered boxes, the player navigates one long artery and explores the ribs.
 *
 * Phase 1 — Spine:          3-tile-wide central corridor (horizontal 70 % or vertical 30 %).
 *                            Entrance room near the head; landmark room near the tail.
 * Phase 2 — Side Rooms:     rectangular rooms placed at regular intervals along both sides,
 *                            each connected to the spine via a single-tile doorway.
 * Phase 3 — Decoration:     floor lighting, context-aware wall variety, spine columns,
 *                            per-room-type props, pickups, and weapon spawns.
 * Phase 4 — Enemies:        spawn points in all non-entrance rooms.
 * Phase 5 — Connectivity:   BFS audit; emergency corridors for any isolated room.
 * Phase 6 — Stairs:         exactly one exit in the landmark or furthest room.
 *
 * Grid convention: (0,0) = bottom-left tile, Y-up. No LibGDX imports — pure Java.
 */
public class LinearCorridorGenerator implements ILevelGenerator {

    private enum RoomType {
        ENTRANCE, STANDARD, LARGE, SERVER_ROOM,
        MEDICAL_BAY, ARMORY, CRYO_CHAMBER,
        POWER_PLANT, COMMAND_CENTER, CONTAINMENT_BLOCK,
        RESEARCH_LAB, STORAGE_BAY, REACTOR
    }

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
            int margin = LevelGenConstants.LEVEL_GEN_ROOM_MARGIN;
            return leftColumn  < other.rightColumn  + margin
                && rightColumn > other.leftColumn   - margin
                && bottomRow   < other.topRow       + margin
                && topRow      > other.bottomRow    - margin;
        }
    }

    private final Random                 random;
    private final LevelGenConfig         config;
    private       List<int[]>            spineCenterTiles;
    private       List<WeaponSpawnPoint> weaponSpawnPoints;

    public LinearCorridorGenerator(long seed) {
        this(seed, new LevelGenConfig());
    }

    public LinearCorridorGenerator(long seed, LevelGenConfig config) {
        this.random = new Random(seed);
        this.config = config;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Override
    public Level generate() {
        char[][] grid = new char[LevelGenConstants.LEVEL_GEN_GRID_HEIGHT][LevelGenConstants.LEVEL_GEN_GRID_WIDTH];
        fillAll(grid, 'x');
        spineCenterTiles  = new ArrayList<>();
        weaponSpawnPoints = new ArrayList<>();

        boolean horizontal = random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_HORIZONTAL_CHANCE;
        List<Room> rooms   = horizontal ? buildHorizontalSpine(grid) : buildVerticalSpine(grid);

        if (rooms.size() < 2) return buildFallbackLevel();

        assignRoomTypes(rooms);

        // Phase 3 — Decoration
        assignFloorLighting(grid, rooms);
        assignWallVariety(grid);
        placeSpineColumns(grid);
        placePlayerSpawn(grid, rooms.get(0));

        // Doors placed before props so interior layouts can keep doorways and their swing
        // axes clear (the prop/column guards test for adjacent doors). All corridor and room
        // carving is already complete by this point.
        placeDoors(grid);

        placeProps(grid, rooms);
        placePickups(grid, rooms);
        placeWeaponSpawns(grid, rooms);

        // Phase 4 — Enemies (after props so spawns land on walkable tiles)
        List<EnemySpawnPoint> spawnPoints = new ArrayList<>();
        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            spawnEnemiesInRoom(grid, rooms.get(roomIndex), spawnPoints);
        }

        // Atmospheric post-passes
        placeRustWallsNearUnlit(grid);
        placeGoreWallsNearCorpses(grid);

        // Phase 5 — Connectivity audit
        verifyAndRepairConnectivity(grid, rooms);

        // Phase 6 — Stairs
        stampStairsDown(grid, rooms);

        return new Level(grid, spawnPoints, weaponSpawnPoints);
    }

    // -------------------------------------------------------------------------
    // Phase 1 — Spine generation
    // -------------------------------------------------------------------------

    private List<Room> buildHorizontalSpine(char[][] grid) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;

        // Spine center row: 40–60 % of grid height
        int spineRow = randomBetween((int)(gridHeight * 0.40f), (int)(gridHeight * 0.60f));

        // Spine length: 75–90 % of grid width, centred horizontally
        int spineLength      = (int)(gridWidth * randomFloat(
            LevelGenConstants.LEVEL_GEN_SPINE_LENGTH_MIN_FRAC,
            LevelGenConstants.LEVEL_GEN_SPINE_LENGTH_MAX_FRAC));
        int spineStartColumn = Math.max(1, (gridWidth - spineLength) / 2);
        int spineEndColumn   = Math.min(gridWidth - 2, spineStartColumn + spineLength - 1);

        // Carve 3-tile-wide spine: centre row is ' ', rib rows are 'l'
        int spineHalfWidth = LevelGenConstants.LEVEL_GEN_SPINE_WIDTH / 2;
        for (int tileColumn = spineStartColumn; tileColumn <= spineEndColumn; tileColumn++) {
            for (int deltaRow = -spineHalfWidth; deltaRow <= spineHalfWidth; deltaRow++) {
                int tileRow = spineRow + deltaRow;
                if (!isInBounds(tileColumn, tileRow)) continue;
                if (deltaRow == 0) {
                    grid[tileRow][tileColumn] = ' ';
                    spineCenterTiles.add(new int[]{ tileColumn, tileRow });
                } else {
                    grid[tileRow][tileColumn] = 'l';
                }
            }
        }

        // Optional widened crossroads node so the long artery is not a monotonous straight line.
        if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_PLAZA_CHANCE) {
            int plazaColumn = (spineStartColumn + spineEndColumn) / 2 + randomBetween(-4, 4);
            carveSpinePlaza(grid, plazaColumn, spineRow);
        }

        List<Room> rooms = new ArrayList<>();
        placeHorizontalSpineRooms(grid, rooms, spineRow, spineStartColumn, spineEndColumn);
        return rooms;
    }

    /**
     * Walks the spine placing side rooms. The entrance room is forced near the head
     * and a landmark room near the tail; all others are placed randomly.
     */
    private void placeHorizontalSpineRooms(char[][] grid, List<Room> rooms,
                                            int spineRow, int spineStartColumn, int spineEndColumn) {
        int spineHalfWidth = LevelGenConstants.LEVEL_GEN_SPINE_WIDTH / 2;

        // ENTRANCE: near the spine head (leftmost slot)
        Room entranceRoom = tryPlaceHorizontalSideRoom(
            grid, rooms, spineStartColumn + 1, spineRow, spineHalfWidth, random.nextBoolean());
        if (entranceRoom != null) entranceRoom.type = RoomType.ENTRANCE;

        // Middle rooms along the spine
        int slotColumn = spineStartColumn + randomBetween(
            LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX,
            LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX + 2);
        int landmarkCutoff = spineEndColumn - LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX;
        while (slotColumn <= landmarkCutoff) {
            if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_SIDE_ROOM_CHANCE) {
                tryPlaceHorizontalSideRoom(grid, rooms, slotColumn, spineRow, spineHalfWidth, true);
            }
            if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_SIDE_ROOM_CHANCE) {
                tryPlaceHorizontalSideRoom(grid, rooms, slotColumn, spineRow, spineHalfWidth, false);
            }
            slotColumn += randomBetween(
                LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MIN,
                LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX);
        }

        // LANDMARK: near the spine tail (rightmost slot)
        tryPlaceHorizontalSideRoom(
            grid, rooms, spineEndColumn - 1, spineRow, spineHalfWidth, random.nextBoolean());

        // Guarantee the first room in the list is ENTRANCE
        if (!rooms.isEmpty() && rooms.get(0).type != RoomType.ENTRANCE) {
            rooms.get(0).type = RoomType.ENTRANCE;
        }
    }

    /**
     * Tries to place one side room on the north (northSide=true) or south side of the
     * horizontal spine at {@code slotColumn}. Carves the room interior and a single-tile
     * connection into the spine. Returns the Room on success, null on failure.
     */
    private Room tryPlaceHorizontalSideRoom(char[][] grid, List<Room> rooms,
                                             int slotColumn, int spineRow, int spineHalfWidth,
                                             boolean northSide) {
        boolean bigRoom    = random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_BIG_ROOM_CHANCE;
        int interiorWidth  = bigRoom
            ? randomBetween(9, LevelGenConstants.LEVEL_GEN_SPINE_ROOM_MAX_WIDTH)
            : randomBetween(LevelGenConstants.LEVEL_GEN_SPINE_ROOM_MIN_WIDTH, 8);
        int interiorHeight = bigRoom
            ? randomBetween(9, LevelGenConstants.LEVEL_GEN_SPINE_ROOM_MAX_HEIGHT)
            : randomBetween(LevelGenConstants.LEVEL_GEN_SPINE_ROOM_MIN_HEIGHT, 8);
        int totalWidth     = interiorWidth  + 2;
        int totalHeight    = interiorHeight + 2;

        int leftColumn, bottomRow, rightColumn, topRow;
        int connectionRow;

        if (northSide) {
            bottomRow     = spineRow + spineHalfWidth + 1; // adjacent to spine top rib
            topRow        = bottomRow + totalHeight - 1;
            connectionRow = spineRow + spineHalfWidth;     // spine top rib
        } else {
            topRow        = spineRow - spineHalfWidth - 1; // adjacent to spine bottom rib
            bottomRow     = topRow   - totalHeight + 1;
            connectionRow = spineRow - spineHalfWidth;     // spine bottom rib
        }

        // Centre the room on the slot column
        leftColumn  = slotColumn - totalWidth / 2;
        rightColumn = leftColumn + totalWidth - 1;

        // Clamp to grid bounds (keep 1-tile solid border)
        if (leftColumn  < 1) leftColumn  = 1;
        if (rightColumn >= LevelGenConstants.LEVEL_GEN_GRID_WIDTH  - 1)
            rightColumn  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH  - 2;
        if (bottomRow   < 1) bottomRow   = 1;
        if (topRow      >= LevelGenConstants.LEVEL_GEN_GRID_HEIGHT - 1)
            topRow       = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT - 2;

        if (rightColumn - leftColumn  < LevelGenConstants.LEVEL_GEN_ROOM_MIN_WIDTH  + 1) return null;
        if (topRow      - bottomRow   < LevelGenConstants.LEVEL_GEN_ROOM_MIN_HEIGHT + 1) return null;

        // Recalculate connection column after clamping to ensure it is inside the room walls
        int connectionColumn = Math.min(Math.max(slotColumn, leftColumn + 1), rightColumn - 1);

        Room candidate = new Room(leftColumn, bottomRow, rightColumn, topRow);
        for (Room existing : rooms) {
            if (candidate.overlaps(existing)) return null;
        }

        // Carve room interior
        for (int tileRow = bottomRow + 1; tileRow < topRow; tileRow++) {
            for (int tileColumn = leftColumn + 1; tileColumn < rightColumn; tileColumn++) {
                grid[tileRow][tileColumn] = ' ';
            }
        }

        // Carve single-tile connection through the room wall into the spine rib
        if (northSide) {
            grid[bottomRow][connectionColumn]     = 'l'; // room bottom wall → corridor tile
            grid[connectionRow][connectionColumn] = 'l'; // spine top rib (reinforce)
        } else {
            grid[topRow][connectionColumn]        = 'l'; // room top wall → corridor tile
            grid[connectionRow][connectionColumn] = 'l'; // spine bottom rib
        }

        rooms.add(candidate);
        return candidate;
    }

    private List<Room> buildVerticalSpine(char[][] grid) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;

        // Spine center column: 40–60 % of grid width
        int spineColumn = randomBetween((int)(gridWidth * 0.40f), (int)(gridWidth * 0.60f));

        // Spine length: 75–90 % of grid height, centred vertically
        int spineLength   = (int)(gridHeight * randomFloat(
            LevelGenConstants.LEVEL_GEN_SPINE_LENGTH_MIN_FRAC,
            LevelGenConstants.LEVEL_GEN_SPINE_LENGTH_MAX_FRAC));
        int spineStartRow = Math.max(1, (gridHeight - spineLength) / 2);
        int spineEndRow   = Math.min(gridHeight - 2, spineStartRow + spineLength - 1);

        // Carve 3-tile-wide spine: centre column is ' ', rib columns are 'l'
        int spineHalfWidth = LevelGenConstants.LEVEL_GEN_SPINE_WIDTH / 2;
        for (int tileRow = spineStartRow; tileRow <= spineEndRow; tileRow++) {
            for (int deltaColumn = -spineHalfWidth; deltaColumn <= spineHalfWidth; deltaColumn++) {
                int tileColumn = spineColumn + deltaColumn;
                if (!isInBounds(tileColumn, tileRow)) continue;
                if (deltaColumn == 0) {
                    grid[tileRow][tileColumn] = ' ';
                    spineCenterTiles.add(new int[]{ tileColumn, tileRow });
                } else {
                    grid[tileRow][tileColumn] = 'l';
                }
            }
        }

        if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_PLAZA_CHANCE) {
            int plazaRow = (spineStartRow + spineEndRow) / 2 + randomBetween(-4, 4);
            carveSpinePlaza(grid, spineColumn, plazaRow);
        }

        List<Room> rooms = new ArrayList<>();
        placeVerticalSpineRooms(grid, rooms, spineColumn, spineStartRow, spineEndRow);
        return rooms;
    }

    /**
     * Carves a square open plaza centred on a spine tile and frames it with four corner
     * columns. Reads as a crossroads / staging node that breaks up the straight artery.
     *
     * Lane-safe by construction: the centre tile and all four cardinal mid-edges stay open,
     * so the corridor is never sealed. Plaza tiles are deliberately NOT fed to the spine-column
     * pass — the plaza carries its own corner framing and extra columns in the widened throat
     * could pinch the narrow spine where it meets the plaza.
     */
    private void carveSpinePlaza(char[][] grid, int centerColumn, int centerRow) {
        int radius = LevelGenConstants.LEVEL_GEN_SPINE_PLAZA_RADIUS;
        for (int deltaRow = -radius; deltaRow <= radius; deltaRow++) {
            for (int deltaColumn = -radius; deltaColumn <= radius; deltaColumn++) {
                int tileColumn = centerColumn + deltaColumn;
                int tileRow    = centerRow    + deltaRow;
                if (tileColumn <= 0 || tileColumn >= LevelGenConstants.LEVEL_GEN_GRID_WIDTH  - 1) continue;
                if (tileRow    <= 0 || tileRow    >= LevelGenConstants.LEVEL_GEN_GRID_HEIGHT - 1) continue;
                char cell = grid[tileRow][tileColumn];
                if (Level.isWall(cell) || cell == 'l') {
                    grid[tileRow][tileColumn] = ' ';
                }
            }
        }
        // Frame the opening with four free-standing corner columns (centre cross stays clear).
        int inset = Math.max(1, radius - 1);
        tryPlaceColumnAt(grid, centerColumn - inset, centerRow - inset);
        tryPlaceColumnAt(grid, centerColumn + inset, centerRow - inset);
        tryPlaceColumnAt(grid, centerColumn - inset, centerRow + inset);
        tryPlaceColumnAt(grid, centerColumn + inset, centerRow + inset);
    }

    private void placeVerticalSpineRooms(char[][] grid, List<Room> rooms,
                                          int spineColumn, int spineStartRow, int spineEndRow) {
        int spineHalfWidth = LevelGenConstants.LEVEL_GEN_SPINE_WIDTH / 2;

        Room entranceRoom = tryPlaceVerticalSideRoom(
            grid, rooms, spineColumn, spineHalfWidth, spineStartRow + 1, random.nextBoolean());
        if (entranceRoom != null) entranceRoom.type = RoomType.ENTRANCE;

        int slotRow = spineStartRow + randomBetween(
            LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX,
            LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX + 2);
        int landmarkCutoff = spineEndRow - LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX;
        while (slotRow <= landmarkCutoff) {
            if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_SIDE_ROOM_CHANCE) {
                tryPlaceVerticalSideRoom(grid, rooms, spineColumn, spineHalfWidth, slotRow, true);
            }
            if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_SIDE_ROOM_CHANCE) {
                tryPlaceVerticalSideRoom(grid, rooms, spineColumn, spineHalfWidth, slotRow, false);
            }
            slotRow += randomBetween(
                LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MIN,
                LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX);
        }

        tryPlaceVerticalSideRoom(
            grid, rooms, spineColumn, spineHalfWidth, spineEndRow - 1, random.nextBoolean());

        if (!rooms.isEmpty() && rooms.get(0).type != RoomType.ENTRANCE) {
            rooms.get(0).type = RoomType.ENTRANCE;
        }
    }

    /**
     * Tries to place one side room on the east (eastSide=true) or west side of the
     * vertical spine at {@code slotRow}. Returns the Room on success, null on failure.
     */
    private Room tryPlaceVerticalSideRoom(char[][] grid, List<Room> rooms,
                                           int spineColumn, int spineHalfWidth,
                                           int slotRow, boolean eastSide) {
        boolean bigRoom    = random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_BIG_ROOM_CHANCE;
        int interiorWidth  = bigRoom
            ? randomBetween(9, LevelGenConstants.LEVEL_GEN_SPINE_ROOM_MAX_WIDTH)
            : randomBetween(LevelGenConstants.LEVEL_GEN_SPINE_ROOM_MIN_WIDTH, 8);
        int interiorHeight = bigRoom
            ? randomBetween(9, LevelGenConstants.LEVEL_GEN_SPINE_ROOM_MAX_HEIGHT)
            : randomBetween(LevelGenConstants.LEVEL_GEN_SPINE_ROOM_MIN_HEIGHT, 8);
        int totalWidth     = interiorWidth  + 2;
        int totalHeight    = interiorHeight + 2;

        int leftColumn, bottomRow, rightColumn, topRow;
        int connectionColumn;

        if (eastSide) {
            leftColumn       = spineColumn + spineHalfWidth + 1;
            rightColumn      = leftColumn + totalWidth - 1;
            connectionColumn = spineColumn + spineHalfWidth;
        } else {
            rightColumn      = spineColumn - spineHalfWidth - 1;
            leftColumn       = rightColumn - totalWidth + 1;
            connectionColumn = spineColumn - spineHalfWidth;
        }

        // Centre room vertically on the slot row
        bottomRow = slotRow - totalHeight / 2;
        topRow    = bottomRow + totalHeight - 1;

        // Clamp to grid bounds
        if (leftColumn  < 1) leftColumn  = 1;
        if (rightColumn >= LevelGenConstants.LEVEL_GEN_GRID_WIDTH  - 1)
            rightColumn  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH  - 2;
        if (bottomRow   < 1) bottomRow   = 1;
        if (topRow      >= LevelGenConstants.LEVEL_GEN_GRID_HEIGHT - 1)
            topRow       = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT - 2;

        if (rightColumn - leftColumn  < LevelGenConstants.LEVEL_GEN_ROOM_MIN_WIDTH  + 1) return null;
        if (topRow      - bottomRow   < LevelGenConstants.LEVEL_GEN_ROOM_MIN_HEIGHT + 1) return null;

        int connectionRow = Math.min(Math.max(slotRow, bottomRow + 1), topRow - 1);

        Room candidate = new Room(leftColumn, bottomRow, rightColumn, topRow);
        for (Room existing : rooms) {
            if (candidate.overlaps(existing)) return null;
        }

        for (int tileRow = bottomRow + 1; tileRow < topRow; tileRow++) {
            for (int tileColumn = leftColumn + 1; tileColumn < rightColumn; tileColumn++) {
                grid[tileRow][tileColumn] = ' ';
            }
        }

        if (eastSide) {
            grid[connectionRow][leftColumn]       = 'l'; // room left wall → corridor tile
            grid[connectionRow][connectionColumn] = 'l'; // spine east rib
        } else {
            grid[connectionRow][rightColumn]      = 'l'; // room right wall → corridor tile
            grid[connectionRow][connectionColumn] = 'l'; // spine west rib
        }

        rooms.add(candidate);
        return candidate;
    }

    // -------------------------------------------------------------------------
    // Room type assignment
    // -------------------------------------------------------------------------

    /**
     * Assigns room types using the same priority logic as {@link LevelGenerator}:
     * guaranteed uniques first (ENTRANCE, MEDICAL_BAY, COMMAND_CENTER, ARMORY),
     * then POWER_PLANT for large-eligible rooms, then cumulative band rolls.
     */
    private void assignRoomTypes(List<Room> rooms) {
        rooms.get(0).type = RoomType.ENTRANCE;

        boolean commandCenterPlaced = false;
        boolean armoryPlaced        = false;
        boolean powerPlantPlaced    = false;
        int     cryoChamberCount    = 0;
        int     containmentCount    = 0;
        int     serverRoomCount     = 0;
        int     largeRoomCount      = 0;
        int     researchLabCount    = 0;
        int     storageBayCount     = 0;
        int     reactorCount        = 0;

        // Medical Bay — guaranteed; prefer the mid-list room with minimum size
        int midIndex = rooms.size() / 2;
        outer:
        for (int searchRadius = 0; searchRadius < rooms.size(); searchRadius++) {
            for (int sign : new int[]{-1, 1}) {
                int index = midIndex + sign * searchRadius;
                if (index <= 0 || index >= rooms.size()) continue;
                Room candidate = rooms.get(index);
                if (candidate.type == RoomType.STANDARD
                        && candidate.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_MEDICAL_BAY_MIN_WIDTH
                        && candidate.interiorHeight() >= LevelGenConstants.LEVEL_GEN_MEDICAL_BAY_MIN_HEIGHT) {
                    candidate.type = RoomType.MEDICAL_BAY;
                    break outer;
                }
            }
        }

        // Command Center — deepest eligible room at 50 % chance
        if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_COMMAND_CHANCE) {
            for (int roomIndex = rooms.size() - 1; roomIndex >= 1; roomIndex--) {
                Room room = rooms.get(roomIndex);
                if (room.type == RoomType.STANDARD
                        && room.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_COMMAND_MIN_WIDTH
                        && room.interiorHeight() >= LevelGenConstants.LEVEL_GEN_COMMAND_MIN_HEIGHT) {
                    room.type = RoomType.COMMAND_CENTER;
                    commandCenterPlaced = true;
                    break;
                }
            }
        }

        // Armory — random eligible room at 80 % chance
        if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_ARMORY_CHANCE) {
            List<Room> eligible = new ArrayList<>();
            for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
                Room room = rooms.get(roomIndex);
                if (room.type == RoomType.STANDARD
                        && room.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_ARMORY_MIN_WIDTH
                        && room.interiorHeight() >= LevelGenConstants.LEVEL_GEN_ARMORY_MIN_HEIGHT) {
                    eligible.add(room);
                }
            }
            if (!eligible.isEmpty()) {
                eligible.get(random.nextInt(eligible.size())).type = RoomType.ARMORY;
                armoryPlaced = true;
            }
        }

        // Power Plant — first large-eligible STANDARD room at 45 % chance
        for (int roomIndex = 1; roomIndex < rooms.size() && !powerPlantPlaced; roomIndex++) {
            Room room = rooms.get(roomIndex);
            if (room.type != RoomType.STANDARD) continue;
            if (room.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_LARGE_MIN_DIM
                    && room.interiorHeight() >= LevelGenConstants.LEVEL_GEN_LARGE_MIN_DIM
                    && random.nextFloat() < LevelGenConstants.LEVEL_GEN_POWERPLANT_CHANCE) {
                room.type = RoomType.POWER_PLANT;
                powerPlantPlaced = true;
            }
        }

        // Research Lab — first eligible STANDARD room becomes a sci-fi set-piece (capped at 1).
        for (int roomIndex = 1; roomIndex < rooms.size() && researchLabCount < LevelGenConstants.LEVEL_GEN_SPINE_RESEARCH_MAX; roomIndex++) {
            Room room = rooms.get(roomIndex);
            if (room.type != RoomType.STANDARD) continue;
            if (room.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_SPINE_RESEARCH_MIN_WIDTH
                    && room.interiorHeight() >= LevelGenConstants.LEVEL_GEN_SPINE_RESEARCH_MIN_HEIGHT
                    && random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_RESEARCH_CHANCE) {
                room.type = RoomType.RESEARCH_LAB;
                researchLabCount++;
            }
        }

        // Reactor — first large-ish STANDARD room becomes a volatile hazard field (capped at 1).
        for (int roomIndex = 1; roomIndex < rooms.size() && reactorCount < LevelGenConstants.LEVEL_GEN_SPINE_REACTOR_MAX; roomIndex++) {
            Room room = rooms.get(roomIndex);
            if (room.type != RoomType.STANDARD) continue;
            if (room.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_SPINE_REACTOR_MIN_WIDTH
                    && room.interiorHeight() >= LevelGenConstants.LEVEL_GEN_SPINE_REACTOR_MIN_HEIGHT
                    && random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_REACTOR_CHANCE) {
                room.type = RoomType.REACTOR;
                reactorCount++;
            }
        }

        // Remaining rooms — cumulative probability bands (LARGE / specialty / STANDARD).
        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            Room  room = rooms.get(roomIndex);
            if (room.type != RoomType.STANDARD) continue;

            // LARGE landmark — only oversized rooms qualify; rolled before the specialty bands.
            if (largeRoomCount < LevelGenConstants.LEVEL_GEN_SPINE_LARGE_MAX
                    && room.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_LARGE_MIN_DIM
                    && room.interiorHeight() >= LevelGenConstants.LEVEL_GEN_LARGE_MIN_DIM
                    && random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_LARGE_CHANCE) {
                room.type = RoomType.LARGE;
                largeRoomCount++;
                continue;
            }

            float roll = random.nextFloat();
            if (cryoChamberCount < LevelGenConstants.LEVEL_GEN_CRYO_MAX
                    && room.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_CRYO_MIN_WIDTH
                    && room.interiorHeight() >= LevelGenConstants.LEVEL_GEN_CRYO_MIN_HEIGHT
                    && roll < LevelGenConstants.LEVEL_GEN_CRYO_CHANCE) {
                room.type = RoomType.CRYO_CHAMBER;
                cryoChamberCount++;
            } else if (containmentCount < LevelGenConstants.LEVEL_GEN_CONTAINMENT_MAX
                    && room.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_CONTAINMENT_MIN_WIDTH
                    && room.interiorHeight() >= LevelGenConstants.LEVEL_GEN_CONTAINMENT_MIN_HEIGHT
                    && roll < LevelGenConstants.LEVEL_GEN_CRYO_CHANCE
                              + LevelGenConstants.LEVEL_GEN_CONTAINMENT_CHANCE) {
                room.type = RoomType.CONTAINMENT_BLOCK;
                containmentCount++;
            } else if (storageBayCount < LevelGenConstants.LEVEL_GEN_SPINE_STORAGE_MAX
                    && room.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_SPINE_STORAGE_MIN_WIDTH
                    && room.interiorHeight() >= LevelGenConstants.LEVEL_GEN_SPINE_STORAGE_MIN_HEIGHT
                    && roll < LevelGenConstants.LEVEL_GEN_CRYO_CHANCE
                              + LevelGenConstants.LEVEL_GEN_CONTAINMENT_CHANCE
                              + LevelGenConstants.LEVEL_GEN_SPINE_STORAGE_CHANCE) {
                room.type = RoomType.STORAGE_BAY;
                storageBayCount++;
            } else if (serverRoomCount < LevelGenConstants.LEVEL_GEN_SERVER_ROOM_MAX_PER_LEVEL
                    && roll < LevelGenConstants.LEVEL_GEN_CRYO_CHANCE
                              + LevelGenConstants.LEVEL_GEN_CONTAINMENT_CHANCE
                              + LevelGenConstants.LEVEL_GEN_SPINE_STORAGE_CHANCE
                              + LevelGenConstants.LEVEL_GEN_SERVER_ROOM_CHANCE) {
                room.type = RoomType.SERVER_ROOM;
                serverRoomCount++;
            }
        }

        // Suppress "assigned but never read" warnings for future-guard boolean flags.
        boolean unused = commandCenterPlaced || armoryPlaced || powerPlantPlaced;
    }

    // -------------------------------------------------------------------------
    // Phase 3 — Floor lighting (per room type)
    // -------------------------------------------------------------------------

    private void assignFloorLighting(char[][] grid, List<Room> rooms) {
        for (Room room : rooms) {
            if (room.type == RoomType.ENTRANCE) continue;
            switch (room.type) {
                case SERVER_ROOM:        assignServerRoomFloor(grid, room);        break;
                case MEDICAL_BAY:        break; // fully lit: clinical brightness
                case ARMORY:             assignArmoryFloor(grid, room);            break;
                case CRYO_CHAMBER:       assignCryoChamberFloor(grid, room);       break;
                case POWER_PLANT:        assignPowerPlantFloor(grid, room);        break;
                case COMMAND_CENTER:     assignCommandCenterFloor(grid, room);     break;
                case CONTAINMENT_BLOCK:  assignContainmentBlockFloor(grid, room);  break;
                case LARGE:              assignLargeRoomFloor(grid, room);         break;
                case RESEARCH_LAB:       assignResearchLabFloor(grid, room);       break;
                case STORAGE_BAY:        assignStorageBayFloor(grid, room);        break;
                case REACTOR:            assignReactorFloor(grid, room);           break;
                default:                 assignStandardRoomFloor(grid, room);      break;
            }
        }
    }

    private void assignServerRoomFloor(char[][] grid, Room room) {
        int flickerBudget = 2;
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                float roll = random.nextFloat();
                if (roll < LevelGenConstants.LEVEL_GEN_SERVER_FLICKER_CHANCE && flickerBudget > 0) {
                    grid[tileRow][tileColumn] = 'f';
                    flickerBudget--;
                } else if (roll < LevelGenConstants.LEVEL_GEN_ROOM_SERVER_UNLIT_THRESHOLD) {
                    grid[tileRow][tileColumn] = 'u';
                } else {
                    grid[tileRow][tileColumn] = 'l';
                }
            }
        }
    }

    private void assignArmoryFloor(char[][] grid, Room room) {
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                boolean isCorner = (tileColumn <= room.leftColumn  + 2 || tileColumn >= room.rightColumn - 2)
                                && (tileRow    <= room.bottomRow   + 2 || tileRow    >= room.topRow      - 2);
                if (isCorner && random.nextFloat() < LevelGenConstants.LEVEL_GEN_ROOM_ARMORY_CORNER_DARK_CHANCE) {
                    grid[tileRow][tileColumn] = 'u';
                } else if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_ROOM_ARMORY_DIM_CHANCE) {
                    grid[tileRow][tileColumn] = 'l';
                }
            }
        }
    }

    private void assignCryoChamberFloor(char[][] grid, Room room) {
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_ROOM_CRYO_UNLIT_CHANCE) {
                    grid[tileRow][tileColumn] = 'u';
                } else {
                    grid[tileRow][tileColumn] = 'l';
                }
            }
        }
    }

    private void assignPowerPlantFloor(char[][] grid, Room room) {
        int centerColumn  = room.centerColumn();
        int centerRow     = room.centerRow();
        int flickerBudget = 4;
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                boolean nearCenter = Math.abs(tileColumn - centerColumn) <= 2
                                  && Math.abs(tileRow    - centerRow)    <= 2;
                if (nearCenter && flickerBudget > 0
                        && random.nextFloat() < LevelGenConstants.LEVEL_GEN_ROOM_POWERPLANT_NEAR_FLICKER_CHANCE) {
                    grid[tileRow][tileColumn] = 'f';
                    flickerBudget--;
                } else {
                    grid[tileRow][tileColumn] = 'u';
                }
            }
        }
    }

    private void assignCommandCenterFloor(char[][] grid, Room room) {
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                boolean isEdgeTile = tileColumn == room.leftColumn  + 1
                                  || tileColumn == room.rightColumn - 1
                                  || tileRow    == room.bottomRow   + 1
                                  || tileRow    == room.topRow      - 1;
                if (isEdgeTile
                        && random.nextFloat() < LevelGenConstants.LEVEL_GEN_ROOM_COMMAND_EDGE_DIM_CHANCE) {
                    grid[tileRow][tileColumn] = 'l';
                }
            }
        }
    }

    private void assignContainmentBlockFloor(char[][] grid, Room room) {
        int flickerBudget = 3;
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_ROOM_CONTAINMENT_FLICKER_CHANCE
                        && flickerBudget > 0) {
                    grid[tileRow][tileColumn] = 'f';
                    flickerBudget--;
                } else {
                    grid[tileRow][tileColumn] = 'u';
                }
            }
        }
    }

    private void assignStandardRoomFloor(char[][] grid, Room room) {
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                float roll = random.nextFloat();
                if (roll < LevelGenConstants.LEVEL_GEN_ROOM_STANDARD_FLICKER_CUMULATIVE) {
                    grid[tileRow][tileColumn] = 'f';
                } else if (roll < LevelGenConstants.LEVEL_GEN_ROOM_STANDARD_UNLIT_CUMULATIVE) {
                    grid[tileRow][tileColumn] = 'u';
                } else if (roll < LevelGenConstants.LEVEL_GEN_ROOM_STANDARD_NORMAL_CUMULATIVE) {
                    grid[tileRow][tileColumn] = 'l';
                }
                // else: leave as ' ' (lit)
            }
        }
    }

    /** LARGE landmark — mostly lit open floor with a dimmer 'l' edge ring for depth. */
    private void assignLargeRoomFloor(char[][] grid, Room room) {
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                boolean isEdgeTile = tileColumn == room.leftColumn  + 1
                                  || tileColumn == room.rightColumn - 1
                                  || tileRow    == room.bottomRow   + 1
                                  || tileRow    == room.topRow      - 1;
                if (isEdgeTile && random.nextFloat() < 0.50f) {
                    grid[tileRow][tileColumn] = 'l';
                }
            }
        }
    }

    /** RESEARCH_LAB — dim, even 'l' lab lighting; cold and clinical without going dark. */
    private void assignResearchLabFloor(char[][] grid, Room room) {
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                grid[tileRow][tileColumn] = random.nextFloat() < 0.20f ? 'u' : 'l';
            }
        }
    }

    /** STORAGE_BAY — utilitarian normal 'l' floor; the cargo aisles read by their crates. */
    private void assignStorageBayFloor(char[][] grid, Room room) {
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                grid[tileRow][tileColumn] = 'l';
            }
        }
    }

    /** REACTOR — dark 'u' floor with a few 'f' flickering tiles near the core (reactor hum). */
    private void assignReactorFloor(char[][] grid, Room room) {
        int centerColumn  = room.centerColumn();
        int centerRow     = room.centerRow();
        int flickerBudget = 4;
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                boolean nearCenter = Math.abs(tileColumn - centerColumn) <= 2
                                  && Math.abs(tileRow    - centerRow)    <= 2;
                if (nearCenter && flickerBudget > 0
                        && random.nextFloat() < LevelGenConstants.LEVEL_GEN_ROOM_POWERPLANT_NEAR_FLICKER_CHANCE) {
                    grid[tileRow][tileColumn] = 'f';
                    flickerBudget--;
                } else {
                    grid[tileRow][tileColumn] = 'u';
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase 3 — Wall variety
    // -------------------------------------------------------------------------

    private void assignWallVariety(char[][] grid) {
        for (int tileRow = 0; tileRow < LevelGenConstants.LEVEL_GEN_GRID_HEIGHT; tileRow++) {
            for (int tileColumn = 0; tileColumn < LevelGenConstants.LEVEL_GEN_GRID_WIDTH; tileColumn++) {
                if (grid[tileRow][tileColumn] != 'x') continue;
                if (!isAdjacentToOpen(grid, tileColumn, tileRow)) continue;
                grid[tileRow][tileColumn] = randomWallChar();
            }
        }
    }

    private boolean isAdjacentToOpen(char[][] grid, int tileColumn, int tileRow) {
        int[] deltaColumns = { 0,  0,  1, -1 };
        int[] deltaRows    = { 1, -1,  0,  0 };
        for (int direction = 0; direction < 4; direction++) {
            int neighborColumn = tileColumn + deltaColumns[direction];
            int neighborRow    = tileRow    + deltaRows[direction];
            if (!isInBounds(neighborColumn, neighborRow)) continue;
            char neighbor = grid[neighborRow][neighborColumn];
            if (neighbor == ' ' || neighbor == 'l' || neighbor == 'u' || neighbor == 'f'
                    || Level.isDoor(neighbor)) {
                return true;
            }
        }
        return false;
    }

    private char randomWallChar() {
        // Cumulative thresholds — conduit 'c' heavy for a military corridor feel.
        float roll = random.nextFloat();
        if (roll < LevelGenConstants.LEVEL_GEN_SPINE_WALL_CONDUIT_CHANCE)     return 'c';
        if (roll < LevelGenConstants.LEVEL_GEN_SPINE_WALL_VENT_CUMULATIVE)    return 'v';
        if (roll < LevelGenConstants.LEVEL_GEN_SPINE_WALL_DAMAGED_CUMULATIVE) return 'w';
        if (roll < LevelGenConstants.LEVEL_GEN_SPINE_WALL_HAZARD_CUMULATIVE)  return 'h';
        if (roll < LevelGenConstants.LEVEL_GEN_SPINE_WALL_TECH_CUMULATIVE)    return 't';
        return 'x';
    }

    // -------------------------------------------------------------------------
    // Phase 3 — Spine column props
    // -------------------------------------------------------------------------

    /**
     * Places 'P' column props along the spine centre at regular intervals using the
     * recorded spine-centre tile list. A column is placed only when it cannot seal the
     * 3-wide spine: at least one perpendicular axis must keep an open lane on both sides,
     * and the tile must be clear of doors and other columns.
     */
    private void placeSpineColumns(char[][] grid) {
        int spacing = LevelGenConstants.LEVEL_GEN_WIDE_HALLWAY_COLUMN_SPACING;
        for (int spineTileIndex = 0; spineTileIndex < spineCenterTiles.size(); spineTileIndex++) {
            if (spineTileIndex % (spacing + 1) != 0) continue;
            int[] tile     = spineCenterTiles.get(spineTileIndex);
            int tileColumn = tile[0];
            int tileRow    = tile[1];
            if (!isWalkableFloor(grid, tileColumn, tileRow)) continue;
            if (isAdjacentToDoor(grid, tileColumn, tileRow))  continue;
            if (isAdjacentToColumn(grid, tileColumn, tileRow)) continue;
            boolean northClear = isWalkableFloor(grid, tileColumn, tileRow + 1);
            boolean southClear = isWalkableFloor(grid, tileColumn, tileRow - 1);
            boolean eastClear  = isWalkableFloor(grid, tileColumn + 1, tileRow);
            boolean westClear  = isWalkableFloor(grid, tileColumn - 1, tileRow);
            if ((northClear && southClear) || (eastClear && westClear)) {
                grid[tileRow][tileColumn] = 'P';
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase 3 — Player spawn
    // -------------------------------------------------------------------------

    private void placePlayerSpawn(char[][] grid, Room entranceRoom) {
        int centerColumn = entranceRoom.centerColumn();
        int centerRow    = entranceRoom.centerRow();
        if (isWalkableFloor(grid, centerColumn, centerRow)) {
            grid[centerRow][centerColumn] = 'p';
            return;
        }
        for (int tileRow = entranceRoom.bottomRow + 1; tileRow < entranceRoom.topRow; tileRow++) {
            for (int tileColumn = entranceRoom.leftColumn + 1; tileColumn < entranceRoom.rightColumn; tileColumn++) {
                if (isWalkableFloor(grid, tileColumn, tileRow)) {
                    grid[tileRow][tileColumn] = 'p';
                    return;
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase 3 — Props (per room type)
    // -------------------------------------------------------------------------

    private void placeProps(char[][] grid, List<Room> rooms) {
        for (Room room : rooms) {
            if (room.type == RoomType.ENTRANCE) continue;
            switch (room.type) {
                case SERVER_ROOM:        placeServerRoomProps(grid, room);        break;
                case MEDICAL_BAY:        placeMedicalBayProps(grid, room);        break;
                case ARMORY:             placeArmoryProps(grid, room);            break;
                case CRYO_CHAMBER:       placeCryoChamberProps(grid, room);       break;
                case POWER_PLANT:        placePowerPlantProps(grid, room);        break;
                case COMMAND_CENTER:     placeCommandCenterProps(grid, room);     break;
                case CONTAINMENT_BLOCK:  placeContainmentBlockProps(grid, room);  break;
                case LARGE:              placeLargeRoomProps(grid, room);         break;
                case RESEARCH_LAB:       placeResearchLabProps(grid, room);       break;
                case STORAGE_BAY:        placeStorageBayProps(grid, room);        break;
                case REACTOR:            placeReactorProps(grid, room);           break;
                default:                 placeStandardRoomProps(grid, room);      break;
            }
        }
    }

    /**
     * STANDARD room props. Qualifying rooms first attempt a structural interior layout
     * (columns, cover, an altar) so even ordinary rooms read as designed spaces; rooms
     * that do not roll a layout fall back to the legacy sparse hazard/crate scatter.
     */
    private void placeStandardRoomProps(char[][] grid, Room room) {
        if (decorateRoomInterior(grid, room)) return;
        char[] propChars = { 'g', 'E', 'C', '#' };
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (!isWalkableFloor(grid, tileColumn, tileRow)) continue;
                if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
                if (isAdjacentToDoorAxis(grid, tileColumn, tileRow)) continue;
                if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_PROP_CHANCE) {
                    grid[tileRow][tileColumn] = propChars[random.nextInt(propChars.length)];
                }
            }
        }
    }

    private void placeServerRoomProps(char[][] grid, Room room) {
        // Rack rows of T/L props at 2-tile vertical spacing
        for (int tileRow = room.bottomRow + 2; tileRow < room.topRow - 1; tileRow += 2) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (!isWalkableFloor(grid, tileColumn, tileRow)) continue;
                char rackChar = (random.nextFloat() < LevelGenConstants.LEVEL_GEN_SERVER_LOCKER_RATIO)
                                ? 'L' : 'T';
                grid[tileRow][tileColumn] = rackChar;
            }
        }
        // Terminal walls along the top perimeter
        for (int tileColumn = room.leftColumn; tileColumn <= room.rightColumn; tileColumn++) {
            if (Level.isWall(grid[room.topRow][tileColumn])
                    && random.nextFloat() < LevelGenConstants.LEVEL_GEN_SERVER_WALL_TERMINAL_CHANCE) {
                grid[room.topRow][tileColumn] = 't';
            }
        }
    }

    private void placeMedicalBayProps(char[][] grid, Room room) {
        // Medical 'M' walls on the perimeter
        for (int tileColumn = room.leftColumn; tileColumn <= room.rightColumn; tileColumn++) {
            for (int wallRow : new int[]{ room.bottomRow, room.topRow }) {
                if (Level.isWall(grid[wallRow][tileColumn])
                        && random.nextFloat() < LevelGenConstants.LEVEL_GEN_MEDICAL_WALL_CHANCE) {
                    grid[wallRow][tileColumn] = 'M';
                }
            }
        }
        for (int tileRow = room.bottomRow; tileRow <= room.topRow; tileRow++) {
            for (int wallColumn : new int[]{ room.leftColumn, room.rightColumn }) {
                if (Level.isWall(grid[tileRow][wallColumn])
                        && random.nextFloat() < LevelGenConstants.LEVEL_GEN_MEDICAL_WALL_CHANCE) {
                    grid[tileRow][wallColumn] = 'M';
                }
            }
        }
        // Medical cabinets ('C') scattered inside
        for (int attempt = 0; attempt < 5; attempt++) {
            if (room.interiorWidth() <= 0 || room.interiorHeight() <= 0) break;
            int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
            int tileRow    = room.bottomRow  + 1 + random.nextInt(room.interiorHeight());
            if (isWalkableFloor(grid, tileColumn, tileRow)
                    && random.nextFloat() < LevelGenConstants.LEVEL_GEN_MEDICAL_PROP_CHANCE) {
                grid[tileRow][tileColumn] = 'C';
            }
        }
    }

    private void placeArmoryProps(char[][] grid, Room room) {
        // Blast-scarred 'X' walls on the perimeter
        for (int tileColumn = room.leftColumn; tileColumn <= room.rightColumn; tileColumn++) {
            for (int wallRow : new int[]{ room.bottomRow, room.topRow }) {
                if (Level.isWall(grid[wallRow][tileColumn])
                        && random.nextFloat() < LevelGenConstants.LEVEL_GEN_ARMORY_BLAST_WALL_CHANCE) {
                    grid[wallRow][tileColumn] = 'X';
                }
            }
        }
        // Weapon racks '=' along the back (top) interior row
        for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
            int backRow = room.topRow - 1;
            if (isWalkableFloor(grid, tileColumn, backRow)) {
                grid[backRow][tileColumn] = '=';
            }
        }
        // Ammo crate '#' somewhere inside
        for (int attempt = 0; attempt < 10; attempt++) {
            if (room.interiorWidth() <= 0 || room.interiorHeight() <= 0) break;
            int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
            int tileRow    = room.bottomRow  + 1 + random.nextInt(room.interiorHeight());
            if (isWalkableFloor(grid, tileColumn, tileRow)) {
                grid[tileRow][tileColumn] = '#';
                break;
            }
        }
    }

    private void placeCryoChamberProps(char[][] grid, Room room) {
        // Cryo 'Z' walls on the top perimeter
        for (int tileColumn = room.leftColumn; tileColumn <= room.rightColumn; tileColumn++) {
            if (Level.isWall(grid[room.topRow][tileColumn])
                    && random.nextFloat() < LevelGenConstants.LEVEL_GEN_CRYO_WALL_CHANCE) {
                grid[room.topRow][tileColumn] = 'Z';
            }
        }
        // Bio-pod '&' rows at 2-tile spacing inside
        for (int tileRow = room.bottomRow + 2; tileRow < room.topRow - 1; tileRow += 2) {
            for (int tileColumn = room.leftColumn + 2; tileColumn < room.rightColumn - 1; tileColumn += 3) {
                if (isWalkableFloor(grid, tileColumn, tileRow)) {
                    grid[tileRow][tileColumn] = '&';
                }
            }
        }
    }

    private void placePowerPlantProps(char[][] grid, Room room) {
        // Radiation 'U' walls on the perimeter
        for (int tileColumn = room.leftColumn; tileColumn <= room.rightColumn; tileColumn++) {
            for (int wallRow : new int[]{ room.bottomRow, room.topRow }) {
                if (Level.isWall(grid[wallRow][tileColumn])
                        && random.nextFloat() < LevelGenConstants.LEVEL_GEN_POWERPLANT_RAD_WALL_CHANCE) {
                    grid[wallRow][tileColumn] = 'U';
                }
            }
        }
        // Generator '%' cluster near the centre
        int centerColumn     = room.centerColumn();
        int centerRow        = room.centerRow();
        int generatorCount   = randomBetween(
            LevelGenConstants.LEVEL_GEN_POWERPLANT_MIN_GENERATORS,
            LevelGenConstants.LEVEL_GEN_POWERPLANT_MAX_GENERATORS);
        int generatorsPlaced = 0;
        for (int attempt = 0; attempt < 40 && generatorsPlaced < generatorCount; attempt++) {
            int tileColumn = centerColumn + random.nextInt(3) - 1;
            int tileRow    = centerRow    + random.nextInt(3) - 1;
            if (isInBounds(tileColumn, tileRow) && isWalkableFloor(grid, tileColumn, tileRow)) {
                grid[tileRow][tileColumn] = '%';
                generatorsPlaced++;
            }
        }
    }

    private void placeCommandCenterProps(char[][] grid, Room room) {
        // Glass 'N' walls on the front (bottom) perimeter
        for (int tileColumn = room.leftColumn; tileColumn <= room.rightColumn; tileColumn++) {
            if (Level.isWall(grid[room.bottomRow][tileColumn])
                    && random.nextFloat() < LevelGenConstants.LEVEL_GEN_COMMAND_GLASS_WALL_CHANCE) {
                grid[room.bottomRow][tileColumn] = 'N';
            }
        }
        // Terminal 'T' row along the back
        int terminalsPlaced = 0;
        int terminalTarget  = randomBetween(
            LevelGenConstants.LEVEL_GEN_COMMAND_MIN_TERMINALS,
            LevelGenConstants.LEVEL_GEN_COMMAND_MAX_TERMINALS);
        int backRow = room.topRow - 1;
        for (int tileColumn = room.leftColumn + 1;
             tileColumn < room.rightColumn && terminalsPlaced < terminalTarget;
             tileColumn++) {
            if (isWalkableFloor(grid, tileColumn, backRow)) {
                grid[backRow][tileColumn] = 'T';
                terminalsPlaced++;
            }
        }
    }

    private void placeContainmentBlockProps(char[][] grid, Room room) {
        // Glass 'N' cell fronts alternating on the side walls
        for (int tileRow = room.bottomRow + 2; tileRow < room.topRow - 1; tileRow += 3) {
            for (int wallColumn : new int[]{ room.leftColumn, room.rightColumn }) {
                if (Level.isWall(grid[tileRow][wallColumn])
                        && random.nextFloat() < LevelGenConstants.LEVEL_GEN_CONTAINMENT_GLASS_CHANCE) {
                    grid[tileRow][wallColumn] = 'N';
                }
            }
        }
        // Quarantine 'Q' walls on top perimeter
        for (int tileColumn = room.leftColumn; tileColumn <= room.rightColumn; tileColumn++) {
            if (Level.isWall(grid[room.topRow][tileColumn])
                    && random.nextFloat() < LevelGenConstants.LEVEL_GEN_CONTAINMENT_BIO_CHANCE) {
                grid[room.topRow][tileColumn] = 'Q';
            }
        }
    }

    /**
     * LARGE landmark — a patterned column hall with deliberately sparse props so the open
     * floor stays navigable and the pillars carry the visual rhythm.
     */
    private void placeLargeRoomProps(char[][] grid, Room room) {
        switch (random.nextInt(3)) {
            case 0:  layoutFourPillars(grid, room);      break;
            case 1:  layoutCentreAvenue(grid, room);     break;
            default: layoutPerimeterColumns(grid, room); break;
        }
        char[] sparseProps = { 'C', 'E', '#' };
        scatterSparseProps(grid, room, LevelGenConstants.LEVEL_GEN_LARGE_PROP_CHANCE, sparseProps);
    }

    /**
     * RESEARCH_LAB — sci-fi set-piece: holo-data 'D' back wall, a row of specimen tanks 'I',
     * a central AI core 'J', a holo-workstation 'W' and special equipment '@', plus scattered
     * energy-scorch 'e' decals. Solids go down lane-safe so the lab never seals.
     */
    private void placeResearchLabProps(char[][] grid, Room room) {
        // Holo-data wall band along the back (top) perimeter.
        for (int tileColumn = room.leftColumn; tileColumn <= room.rightColumn; tileColumn++) {
            if (Level.isWall(grid[room.topRow][tileColumn])
                    && random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_RESEARCH_HOLO_WALL_CHANCE) {
                grid[room.topRow][tileColumn] = 'D';
            }
        }
        // Row of specimen tanks 'I' along the back interior row.
        int tankTarget = randomBetween(LevelGenConstants.LEVEL_GEN_SPINE_RESEARCH_MIN_TANKS,
                                       LevelGenConstants.LEVEL_GEN_SPINE_RESEARCH_MAX_TANKS);
        int tanksPlaced = 0;
        int backRow = room.topRow - 1;
        for (int tileColumn = room.leftColumn + 1;
             tileColumn < room.rightColumn && tanksPlaced < tankTarget; tileColumn += 2) {
            if (tryPlaceSolidProp(grid, tileColumn, backRow, 'I')) tanksPlaced++;
        }
        // AI core just off-centre, flanked by a holo-workstation and special equipment.
        // The exact room centre is left walkable so the connectivity audit never bulldozes here.
        int centerColumn = room.centerColumn();
        int centerRow    = room.centerRow();
        tryPlaceSolidProp(grid, centerColumn,     centerRow + 1, 'J');
        tryPlaceSolidProp(grid, centerColumn - 2, centerRow,     'W');
        tryPlaceSolidProp(grid, centerColumn + 2, centerRow,     '@');
        // Energy-scorch decals scattered on the floor.
        int scorchTarget = randomBetween(LevelGenConstants.LEVEL_GEN_SPINE_RESEARCH_SCORCH_MIN,
                                         LevelGenConstants.LEVEL_GEN_SPINE_RESEARCH_SCORCH_MAX);
        scatterDecals(grid, room, 'e', scorchTarget);
    }

    /**
     * STORAGE_BAY — cargo hold of stacked crate 'C' / locker 'L' aisles. A one-tile cross-aisle
     * is kept open through the centre so every lane stays reachable.
     */
    private void placeStorageBayProps(char[][] grid, Room room) {
        int crossRow = room.centerRow();
        for (int tileColumn = room.leftColumn + 2; tileColumn < room.rightColumn - 1; tileColumn += 2) {
            for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
                if (tileRow == crossRow) continue; // cross-aisle keeps the bay fully connected
                char crate = random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_STORAGE_LOCKER_RATIO
                             ? 'L' : 'C';
                tryPlaceSolidProp(grid, tileColumn, tileRow, crate);
            }
        }
    }

    /**
     * REACTOR — volatile hazard field: radiation 'U' walls, a generator '%' core cluster, and
     * scattered explosive 'E' / radioactive 'g' barrels. Barrels are placed lane-safe.
     */
    private void placeReactorProps(char[][] grid, Room room) {
        // Radiation walls on the perimeter.
        for (int tileColumn = room.leftColumn; tileColumn <= room.rightColumn; tileColumn++) {
            for (int wallRow : new int[]{ room.bottomRow, room.topRow }) {
                if (Level.isWall(grid[wallRow][tileColumn])
                        && random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_REACTOR_RAD_WALL_CHANCE) {
                    grid[wallRow][tileColumn] = 'U';
                }
            }
        }
        // Generator core cluster off-centre. The exact room centre is left walkable so the
        // connectivity audit never has to bulldoze a corridor through the core.
        int centerColumn = room.centerColumn();
        int centerRow    = room.centerRow();
        tryPlaceSolidProp(grid, centerColumn + 1, centerRow,     '%');
        tryPlaceSolidProp(grid, centerColumn + 2, centerRow,     '%');
        tryPlaceSolidProp(grid, centerColumn + 1, centerRow + 1, '%');
        // Scattered volatile barrels (centre tile kept clear).
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (tileColumn == centerColumn && tileRow == centerRow) continue;
                if (random.nextFloat() >= LevelGenConstants.LEVEL_GEN_SPINE_REACTOR_BARREL_CHANCE) continue;
                tryPlaceSolidProp(grid, tileColumn, tileRow, random.nextBoolean() ? 'E' : 'g');
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase 3 — Interior architecture (STANDARD / LARGE structural layouts)
    // -------------------------------------------------------------------------

    /**
     * Gives a qualifying STANDARD room an internal structure built from environment elements
     * so it reads as a designed space rather than a bare box. Returns true when a layout was
     * applied (caller then skips the legacy scatter). Every layout is lane-safe by
     * construction: it never seals the room centre, a doorway, the spawn, or the stairs.
     */
    private boolean decorateRoomInterior(char[][] grid, Room room) {
        if (room.interiorWidth()  < LevelGenConstants.LEVEL_GEN_INTERIOR_MIN_DIM) return false;
        if (room.interiorHeight() < LevelGenConstants.LEVEL_GEN_INTERIOR_MIN_DIM) return false;
        if (random.nextFloat() >= LevelGenConstants.LEVEL_GEN_INTERIOR_LAYOUT_CHANCE) return false;
        switch (random.nextInt(4)) {
            case 0:  layoutFourPillars(grid, room);  break;
            case 1:  layoutCentreAvenue(grid, room); break;
            case 2:  layoutCentralAltar(grid, room); break;
            default: layoutCrateCover(grid, room);   break;
        }
        return true;
    }

    /** Four free-standing columns at the interior quadrant centres (classic pillared hall). */
    private void layoutFourPillars(char[][] grid, Room room) {
        int quarterWidth  = room.interiorWidth()  / 4;
        int quarterHeight = room.interiorHeight() / 4;
        int[] columns = { room.leftColumn + 1 + quarterWidth, room.rightColumn - 1 - quarterWidth };
        int[] rows    = { room.bottomRow  + 1 + quarterHeight, room.topRow      - 1 - quarterHeight };
        for (int patternColumn : columns) {
            for (int patternRow : rows) {
                tryPlaceColumnAt(grid, patternColumn, patternRow);
            }
        }
    }

    /** A single row of columns down the room's long axis, splitting it into two naves. */
    private void layoutCentreAvenue(char[][] grid, Room room) {
        int spacing      = LevelGenConstants.LEVEL_GEN_INTERIOR_COLUMN_SPACING + 1;
        int centerColumn = room.centerColumn();
        int centerRow    = room.centerRow();
        if (room.interiorWidth() >= room.interiorHeight()) {
            for (int tileColumn = room.leftColumn + 2; tileColumn < room.rightColumn - 1; tileColumn += spacing) {
                if (tileColumn == centerColumn) continue; // keep the room centre clear
                tryPlaceColumnAt(grid, tileColumn, centerRow);
            }
        } else {
            for (int tileRow = room.bottomRow + 2; tileRow < room.topRow - 1; tileRow += spacing) {
                if (tileRow == centerRow) continue; // keep the room centre clear
                tryPlaceColumnAt(grid, centerColumn, tileRow);
            }
        }
    }

    /** Columns hugging the four interior mid-edges, leaving a wide open centre (mini-arena). */
    private void layoutPerimeterColumns(char[][] grid, Room room) {
        int midColumn = room.centerColumn();
        int midRow    = room.centerRow();
        tryPlaceColumnAt(grid, midColumn,            room.bottomRow + 2);
        tryPlaceColumnAt(grid, midColumn,            room.topRow    - 2);
        tryPlaceColumnAt(grid, room.leftColumn  + 2, midRow);
        tryPlaceColumnAt(grid, room.rightColumn - 2, midRow);
    }

    /**
     * Four columns framing a central reward pedestal — a small shrine. The pickup sits on the
     * open centre tile; the columns occupy the diagonals so all four cardinal lanes stay clear.
     */
    private void layoutCentralAltar(char[][] grid, Room room) {
        int centerColumn = room.centerColumn();
        int centerRow    = room.centerRow();
        tryPlaceColumnAt(grid, centerColumn - 1, centerRow - 1);
        tryPlaceColumnAt(grid, centerColumn + 1, centerRow - 1);
        tryPlaceColumnAt(grid, centerColumn - 1, centerRow + 1);
        tryPlaceColumnAt(grid, centerColumn + 1, centerRow + 1);
        if (isWalkableFloor(grid, centerColumn, centerRow)
                && !isAdjacentToDoor(grid, centerColumn, centerRow)) {
            grid[centerRow][centerColumn] = random.nextBoolean() ? 'A' : 'H';
        }
    }

    /** A few small crate/locker cover clusters offset from the centre, for tactical cover. */
    private void layoutCrateCover(char[][] grid, Room room) {
        int centerColumn = room.centerColumn();
        int centerRow    = room.centerRow();
        for (int cluster = 0; cluster < LevelGenConstants.LEVEL_GEN_INTERIOR_CRATE_CLUSTERS; cluster++) {
            int anchorColumn = room.leftColumn + 2 + random.nextInt(Math.max(1, room.interiorWidth()  - 2));
            int anchorRow    = room.bottomRow  + 2 + random.nextInt(Math.max(1, room.interiorHeight() - 2));
            if (anchorColumn == centerColumn && anchorRow == centerRow) continue; // keep centre clear
            int crateCount = 1 + random.nextInt(LevelGenConstants.LEVEL_GEN_INTERIOR_CRATE_PER_CLUSTER);
            for (int offset = 0; offset < crateCount; offset++) {
                char crate = random.nextFloat() < 0.30f ? 'L' : 'C';
                tryPlaceSolidProp(grid, anchorColumn + offset, anchorRow, crate);
            }
        }
    }

    /** Scatters {@code count} solid props of one type at random walkable, lane-safe interior tiles. */
    private void scatterSparseProps(char[][] grid, Room room, float chance, char[] propChars) {
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (random.nextFloat() >= chance) continue;
                tryPlaceSolidProp(grid, tileColumn, tileRow, propChars[random.nextInt(propChars.length)]);
            }
        }
    }

    /** Scatters up to {@code count} walkable decals on random interior floor tiles. */
    private void scatterDecals(char[][] grid, Room room, char decalChar, int count) {
        int placed = 0;
        for (int attempt = 0; attempt < count * 6 && placed < count; attempt++) {
            if (room.interiorWidth() <= 0 || room.interiorHeight() <= 0) break;
            int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
            int tileRow    = room.bottomRow  + 1 + random.nextInt(room.interiorHeight());
            if (isWalkableFloor(grid, tileColumn, tileRow)) {
                grid[tileRow][tileColumn] = decalChar;
                placed++;
            }
        }
    }

    /**
     * Places a 'P' column at a tile only when it is open floor and doing so cannot seal a
     * passage: never on or beside another column, never on or beside a door or its swing axis.
     */
    private void tryPlaceColumnAt(char[][] grid, int tileColumn, int tileRow) {
        if (!isInBounds(tileColumn, tileRow)) return;
        if (!isWalkableFloor(grid, tileColumn, tileRow)) return;
        if (isAdjacentToColumn(grid, tileColumn, tileRow)) return;
        if (isAdjacentToDoor(grid, tileColumn, tileRow)) return;
        if (isAdjacentToDoorAxis(grid, tileColumn, tileRow)) return;
        grid[tileRow][tileColumn] = 'P';
    }

    /** Places a solid prop only on lane-safe open floor that is clear of any doorway. */
    private boolean tryPlaceSolidProp(char[][] grid, int tileColumn, int tileRow, char propChar) {
        if (!isInBounds(tileColumn, tileRow)) return false;
        if (!isWalkableFloor(grid, tileColumn, tileRow)) return false;
        if (isAdjacentToDoor(grid, tileColumn, tileRow)) return false;
        if (isAdjacentToDoorAxis(grid, tileColumn, tileRow)) return false;
        grid[tileRow][tileColumn] = propChar;
        return true;
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

    /**
     * Returns true when placing a solid here would sit on the swing axis of an adjacent door
     * (the line a door opens along), which could block that door. Mirrors the same guard in
     * {@link LevelGenerator}.
     */
    private boolean isAdjacentToDoorAxis(char[][] grid, int tileColumn, int tileRow) {
        int[] deltaColumns = { 0, 0, 1, -1 };
        int[] deltaRows    = { 1, -1, 0, 0 };
        for (int direction = 0; direction < 4; direction++) {
            int neighborColumn = tileColumn + deltaColumns[direction];
            int neighborRow    = tileRow    + deltaRows[direction];
            if (!isInBounds(neighborColumn, neighborRow)) continue;
            if (!Level.isDoor(grid[neighborRow][neighborColumn])) continue;
            boolean doorHasWallNorth = isWallAt(grid, neighborColumn, neighborRow + 1);
            boolean doorHasWallSouth = isWallAt(grid, neighborColumn, neighborRow - 1);
            boolean doorHasWallEast  = isWallAt(grid, neighborColumn + 1, neighborRow);
            boolean doorHasWallWest  = isWallAt(grid, neighborColumn - 1, neighborRow);
            if (doorHasWallNorth && doorHasWallSouth && tileRow == neighborRow)    return true;
            if (doorHasWallEast  && doorHasWallWest  && tileColumn == neighborColumn) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Phase 3 — Pickups
    // -------------------------------------------------------------------------

    private void placePickups(char[][] grid, List<Room> rooms) {
        for (Room room : rooms) {
            if (room.type == RoomType.ENTRANCE) continue;

            float medkitChance;
            float armourChance;
            float ammoChance;

            switch (room.type) {
                case MEDICAL_BAY:
                    tryPlacePickup(grid, room, 'H');
                    tryPlacePickup(grid, room, 'H');
                    continue;
                case ARMORY:
                    if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_ARMORY_ARMOUR_CHANCE)
                        tryPlacePickup(grid, room, 'A');
                    if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_ARMORY_MEDKIT_CHANCE)
                        tryPlacePickup(grid, room, 'H');
                    tryPlacePickup(grid, room, randomAmmoChar());
                    if (random.nextBoolean()) tryPlacePickup(grid, room, randomAmmoChar());
                    continue;
                case SERVER_ROOM:
                    medkitChance = LevelGenConstants.LEVEL_GEN_SERVER_MEDKIT_CHANCE;
                    armourChance = LevelGenConstants.LEVEL_GEN_SERVER_ARMOUR_CHANCE;
                    ammoChance   = LevelGenConstants.LEVEL_GEN_HAZARD_ROOM_AMMO_CHANCE;
                    break;
                case COMMAND_CENTER:
                    medkitChance = LevelGenConstants.LEVEL_GEN_COMMAND_MEDKIT_CHANCE;
                    armourChance = LevelGenConstants.LEVEL_GEN_COMMAND_ARMOUR_CHANCE;
                    ammoChance   = LevelGenConstants.LEVEL_GEN_COMMAND_AMMO_CHANCE;
                    break;
                case STORAGE_BAY:
                    // Cargo hold — loot hub: guaranteed ammo, generous armour.
                    if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_COMMAND_ARMOUR_CHANCE)
                        tryPlacePickup(grid, room, 'A');
                    tryPlacePickup(grid, room, randomAmmoChar());
                    if (random.nextBoolean()) tryPlacePickup(grid, room, randomAmmoChar());
                    continue;
                case RESEARCH_LAB:
                    medkitChance = LevelGenConstants.LEVEL_GEN_COMMAND_MEDKIT_CHANCE;
                    armourChance = LevelGenConstants.LEVEL_GEN_HAZARD_ROOM_ARMOUR_CHANCE;
                    ammoChance   = LevelGenConstants.LEVEL_GEN_COMMAND_AMMO_CHANCE;
                    break;
                case LARGE:
                    medkitChance = LevelGenConstants.LEVEL_GEN_LARGE_MEDKIT_CHANCE;
                    armourChance = LevelGenConstants.LEVEL_GEN_LARGE_ARMOUR_CHANCE;
                    ammoChance   = LevelGenConstants.LEVEL_GEN_AMMO_CHANCE_PER_ROOM;
                    break;
                case POWER_PLANT:
                case REACTOR:
                case CRYO_CHAMBER:
                case CONTAINMENT_BLOCK:
                    medkitChance = LevelGenConstants.LEVEL_GEN_HAZARD_ROOM_MEDKIT_CHANCE;
                    armourChance = LevelGenConstants.LEVEL_GEN_HAZARD_ROOM_ARMOUR_CHANCE;
                    ammoChance   = LevelGenConstants.LEVEL_GEN_HAZARD_ROOM_AMMO_CHANCE;
                    break;
                default:
                    medkitChance = config.medkitChancePerRoom;
                    armourChance = config.armourChancePerRoom;
                    ammoChance   = LevelGenConstants.LEVEL_GEN_AMMO_CHANCE_PER_ROOM;
                    break;
            }

            if (config.medkits    && random.nextFloat() < medkitChance) tryPlacePickup(grid, room, 'H');
            if (config.armourKits && random.nextFloat() < armourChance)  tryPlacePickup(grid, room, 'A');
            if (random.nextFloat() < ammoChance) tryPlacePickup(grid, room, randomAmmoChar());
        }
    }

    // -------------------------------------------------------------------------
    // Phase 3 — Weapon spawns
    // -------------------------------------------------------------------------

    private void placeWeaponSpawns(char[][] grid, List<Room> rooms) {
        for (Room room : rooms) {
            if (room.type == RoomType.ARMORY) {
                tryPlaceWeaponSpawn(grid, room);
                break;
            }
        }
        for (Room room : rooms) {
            if (room.type == RoomType.ENTRANCE || room.type == RoomType.ARMORY) continue;
            if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_RANDOM_ROOM_WEAPON_CHANCE) {
                tryPlaceWeaponSpawn(grid, room);
            }
        }
    }

    private boolean tryPlaceWeaponSpawn(char[][] grid, Room room) {
        for (int attempt = 0; attempt < 20; attempt++) {
            if (room.interiorWidth() <= 0 || room.interiorHeight() <= 0) return false;
            int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
            int tileRow    = room.bottomRow  + 1 + random.nextInt(room.interiorHeight());
            char cell = grid[tileRow][tileColumn];
            if (cell != ' ' && cell != 'l') continue;
            if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
            weaponSpawnPoints.add(new WeaponSpawnPoint(tileColumn, tileRow, randomWeaponItemType()));
            return true;
        }
        return false;
    }

    private ItemType randomWeaponItemType() {
        switch (random.nextInt(5)) {
            case 0:  return ItemType.WEAPON_SHOTGUN;
            case 1:  return ItemType.WEAPON_CHAINGUN;
            case 2:  return ItemType.WEAPON_ASSAULT_RIFLE;
            case 3:  return ItemType.WEAPON_PLASMA;
            default: return ItemType.WEAPON_ROCKET;
        }
    }

    private void tryPlacePickup(char[][] grid, Room room, char pickupChar) {
        for (int attempt = 0; attempt < 20; attempt++) {
            if (room.interiorWidth() <= 0 || room.interiorHeight() <= 0) return;
            int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
            int tileRow    = room.bottomRow  + 1 + random.nextInt(room.interiorHeight());
            if (isWalkableFloor(grid, tileColumn, tileRow)) {
                grid[tileRow][tileColumn] = pickupChar;
                return;
            }
        }
    }

    private char randomAmmoChar() {
        switch (random.nextInt(5)) {
            case 0:  return '6';
            case 1:  return '7';
            case 2:  return '8';
            case 3:  return '9';
            default: return '0';
        }
    }

    // -------------------------------------------------------------------------
    // Door placement
    // -------------------------------------------------------------------------

    private void placeDoors(char[][] grid) {
        for (int tileRow = 1; tileRow < LevelGenConstants.LEVEL_GEN_GRID_HEIGHT - 1; tileRow++) {
            for (int tileColumn = 1; tileColumn < LevelGenConstants.LEVEL_GEN_GRID_WIDTH - 1; tileColumn++) {
                if (grid[tileRow][tileColumn] != 'l') continue;
                if (!isAdjacentToRoomFloor(grid, tileColumn, tileRow)) continue;
                if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
                if (!isDoorwayAligned(grid, tileColumn, tileRow)) continue;
                if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_DOOR_CHANCE) {
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
        return Level.isDoor(grid[tileRow][tileColumn]);
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
    // Phase 4 — Enemy placement
    // -------------------------------------------------------------------------

    private void spawnEnemiesInRoom(char[][] grid, Room room, List<EnemySpawnPoint> spawnPoints) {
        int area       = room.interiorWidth() * room.interiorHeight();
        int enemyCount = Math.min(LevelGenConstants.LEVEL_GEN_MAX_ENEMIES_PER_ROOM,
                                  1 + random.nextInt(Math.max(1, area / 6)));
        int[] placedColumns = new int[enemyCount];
        int[] placedRows    = new int[enemyCount];
        int   placed        = 0;
        int   attempts      = 0;

        while (placed < enemyCount && attempts < 50) {
            attempts++;
            if (room.interiorWidth() <= 0 || room.interiorHeight() <= 0) break;
            int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
            int tileRow    = room.bottomRow  + 1 + random.nextInt(room.interiorHeight());
            if (!isEnemySpawnEligible(grid, tileColumn, tileRow)) continue;
            if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
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
            spawnPoints.add(new EnemySpawnPoint(randomEnemySpawnChar(), tileColumn, tileRow));
            placed++;
        }
    }

    private char randomEnemySpawnChar() {
        float roll = random.nextFloat();
        if (roll < LevelGenConstants.LEVEL_GEN_CORRUPTOR_THRESHOLD)  return '1';
        if (roll < LevelGenConstants.LEVEL_GEN_VORTEX_EYE_THRESHOLD) return '2';
        if (roll < LevelGenConstants.LEVEL_GEN_GHOUL_THRESHOLD)       return '3';
        if (roll < LevelGenConstants.LEVEL_GEN_CRAWLER_THRESHOLD)     return '4';
        return '5';
    }

    // -------------------------------------------------------------------------
    // Atmospheric post-passes
    // -------------------------------------------------------------------------

    private void placeRustWallsNearUnlit(char[][] grid) {
        int[] deltaColumns = { 0,  0,  1, -1 };
        int[] deltaRows    = { 1, -1,  0,  0 };
        for (int tileRow = 0; tileRow < LevelGenConstants.LEVEL_GEN_GRID_HEIGHT; tileRow++) {
            for (int tileColumn = 0; tileColumn < LevelGenConstants.LEVEL_GEN_GRID_WIDTH; tileColumn++) {
                if (grid[tileRow][tileColumn] != 'x') continue;
                boolean nearUnlit = false;
                for (int direction = 0; direction < 4; direction++) {
                    int neighborColumn = tileColumn + deltaColumns[direction];
                    int neighborRow    = tileRow    + deltaRows[direction];
                    if (!isInBounds(neighborColumn, neighborRow)) continue;
                    if (grid[neighborRow][neighborColumn] == 'u') nearUnlit = true;
                }
                if (nearUnlit && random.nextFloat() < LevelGenConstants.LEVEL_GEN_RUST_WALL_CHANCE) {
                    grid[tileRow][tileColumn] = 'j';
                }
            }
        }
    }

    private void placeGoreWallsNearCorpses(char[][] grid) {
        int[] deltaColumns = { 0,  0,  1, -1 };
        int[] deltaRows    = { 1, -1,  0,  0 };
        for (int tileRow = 0; tileRow < LevelGenConstants.LEVEL_GEN_GRID_HEIGHT; tileRow++) {
            for (int tileColumn = 0; tileColumn < LevelGenConstants.LEVEL_GEN_GRID_WIDTH; tileColumn++) {
                if (grid[tileRow][tileColumn] != 'x') continue;
                boolean nearGore = false;
                for (int direction = 0; direction < 4; direction++) {
                    int neighborColumn = tileColumn + deltaColumns[direction];
                    int neighborRow    = tileRow    + deltaRows[direction];
                    if (!isInBounds(neighborColumn, neighborRow)) continue;
                    char neighbor = grid[neighborRow][neighborColumn];
                    if (neighbor == 'm' || neighbor == '.') nearGore = true;
                }
                if (nearGore && random.nextFloat() < LevelGenConstants.LEVEL_GEN_GORE_WALL_CHANCE) {
                    grid[tileRow][tileColumn] = 'G';
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase 5 — Connectivity audit
    // -------------------------------------------------------------------------

    private void verifyAndRepairConnectivity(char[][] grid, List<Room> rooms) {
        // Source the audit at the actual player spawn tile so reachability is measured from
        // where the player really starts (room 0's geometric centre may differ from 'p').
        int startColumn = rooms.get(0).centerColumn();
        int startRow    = rooms.get(0).centerRow();
        for (int tileRow = 0; tileRow < LevelGenConstants.LEVEL_GEN_GRID_HEIGHT; tileRow++) {
            for (int tileColumn = 0; tileColumn < LevelGenConstants.LEVEL_GEN_GRID_WIDTH; tileColumn++) {
                if (grid[tileRow][tileColumn] == 'p') {
                    startColumn = tileColumn;
                    startRow    = tileRow;
                }
            }
        }
        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            Room room = rooms.get(roomIndex);
            if (!isTileReachable(grid, startColumn, startRow,
                                 room.centerColumn(), room.centerRow())) {
                carveEmergencyCorridor(grid, startColumn, startRow,
                                       room.centerColumn(), room.centerRow());
            }
        }
        repairUnreachableFloorRegions(grid, startColumn, startRow);
    }

    /**
     * Definitive connectivity backstop. The per-room audit checks only room centres, which can
     * miss a region that is walled off yet whose centre tile lies elsewhere. This pass floods
     * from the spawn and, while any walkable floor tile remains unreachable, carves a
     * blocker-clearing corridor from the spawn to one such tile — connecting that whole region.
     * It repeats until every floor tile is reachable, so the player can always reach all loot,
     * enemies, and the exit.
     */
    private void repairUnreachableFloorRegions(char[][] grid, int spawnColumn, int spawnRow) {
        int safetyCap = LevelGenConstants.LEVEL_GEN_GRID_WIDTH + LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        for (int iteration = 0; iteration < safetyCap; iteration++) {
            boolean[][] reachable = computeReachableFromSpawn(grid);
            int targetColumn = -1;
            int targetRow    = -1;
            for (int tileRow = 0; tileRow < LevelGenConstants.LEVEL_GEN_GRID_HEIGHT && targetColumn < 0; tileRow++) {
                for (int tileColumn = 0; tileColumn < LevelGenConstants.LEVEL_GEN_GRID_WIDTH; tileColumn++) {
                    if (!reachable[tileRow][tileColumn] && isWalkableFloor(grid, tileColumn, tileRow)) {
                        targetColumn = tileColumn;
                        targetRow    = tileRow;
                        break;
                    }
                }
            }
            if (targetColumn < 0) return; // every floor tile is reachable
            carveEmergencyCorridor(grid, spawnColumn, spawnRow, targetColumn, targetRow);
        }
    }

    private boolean isTileReachable(char[][] grid,
                                     int startColumn, int startRow,
                                     int targetColumn, int targetRow) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        boolean[][] visited = new boolean[gridHeight][gridWidth];
        int capacity        = gridWidth * gridHeight;
        int[] stackColumns  = new int[capacity];
        int[] stackRows     = new int[capacity];
        int   stackTop      = 0;

        visited[startRow][startColumn] = true;
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
                if (!isPassableForBFS(grid[neighborRow][neighborColumn])) continue;
                visited[neighborRow][neighborColumn] = true;
                stackColumns[stackTop] = neighborColumn;
                stackRows[stackTop]    = neighborRow;
                stackTop++;
            }
        }
        return false;
    }

    /**
     * Connectivity passability — mirrors in-game movement, where only solid walls, solid
     * props, and cylindrical columns block the player. Floor, doors (openable), walkable
     * decals (blood, scorch, oil, corpses) and pickups are all walkable, so they must count
     * as passable here; treating a decal or pickup as a blocker would wrongly report a region
     * unreachable and make the repair pass loop trying to clear a tile that is not a blocker.
     */
    private boolean isPassableForBFS(char cell) {
        return !Level.isWall(cell) && !Level.isPropSolid(cell) && !Level.isColumn(cell);
    }

    private void carveEmergencyCorridor(char[][] grid,
                                         int fromColumn, int fromRow,
                                         int toColumn,   int toRow) {
        carveEmergencyHorizontal(grid, fromRow,   fromColumn, toColumn);
        carveEmergencyVertical(grid,   toColumn,  fromRow,    toRow);
    }

    private void carveEmergencyHorizontal(char[][] grid, int fixedRow, int column1, int column2) {
        int minColumn = Math.min(column1, column2);
        int maxColumn = Math.max(column1, column2);
        for (int tileColumn = minColumn; tileColumn <= maxColumn; tileColumn++) {
            if (!isInBounds(tileColumn, fixedRow)) continue;
            if (isEmergencyBlocker(grid[fixedRow][tileColumn])) {
                grid[fixedRow][tileColumn] = 'l';
            }
        }
    }

    private void carveEmergencyVertical(char[][] grid, int fixedColumn, int row1, int row2) {
        int minRow = Math.min(row1, row2);
        int maxRow = Math.max(row1, row2);
        for (int tileRow = minRow; tileRow <= maxRow; tileRow++) {
            if (!isInBounds(fixedColumn, tileRow)) continue;
            if (isEmergencyBlocker(grid[tileRow][fixedColumn])) {
                grid[tileRow][fixedColumn] = 'l';
            }
        }
    }

    /**
     * A tile an emergency corridor must clear to reach a stranded room: solid walls, solid
     * props, AND cylindrical columns 'P'. Columns are included because the audit otherwise
     * cannot punch through a column that happens to sit on the straight repair path, which
     * would leave the room sealed despite the repair pass running.
     */
    private boolean isEmergencyBlocker(char cell) {
        return Level.isWall(cell) || Level.isPropSolid(cell) || Level.isColumn(cell);
    }

    // -------------------------------------------------------------------------
    // Phase 6 — Stairs
    // -------------------------------------------------------------------------

    /**
     * Stamps exactly one exit. The tile is always chosen from the set of tiles reachable
     * from the player spawn, so the stairs can never strand in an isolated pocket — interior
     * structures, column avenues, and the connectivity audit's center-only guarantee
     * notwithstanding. Landmark rooms (Command Center, then Power Plant) are preferred, then
     * the deepest reachable room, with a whole-grid reachable-tile sweep as the final guard.
     */
    private void stampStairsDown(char[][] grid, List<Room> rooms) {
        boolean[][] reachable = computeReachableFromSpawn(grid);
        for (int roomIndex = rooms.size() - 1; roomIndex >= 1; roomIndex--) {
            Room room = rooms.get(roomIndex);
            if (room.type == RoomType.COMMAND_CENTER && tryStampInRoom(grid, room, reachable)) return;
        }
        for (int roomIndex = rooms.size() - 1; roomIndex >= 1; roomIndex--) {
            Room room = rooms.get(roomIndex);
            if (room.type == RoomType.POWER_PLANT && tryStampInRoom(grid, room, reachable)) return;
        }
        for (int roomIndex = rooms.size() - 1; roomIndex >= 1; roomIndex--) {
            if (tryStampInRoom(grid, rooms.get(roomIndex), reachable)) return;
        }
        if (!rooms.isEmpty() && tryStampInRoom(grid, rooms.get(0), reachable)) return;
        // Final guard: stamp on any reachable walkable tile anywhere on the grid.
        for (int tileRow = 0; tileRow < LevelGenConstants.LEVEL_GEN_GRID_HEIGHT; tileRow++) {
            for (int tileColumn = 0; tileColumn < LevelGenConstants.LEVEL_GEN_GRID_WIDTH; tileColumn++) {
                if (reachable[tileRow][tileColumn] && isWalkableFloor(grid, tileColumn, tileRow)) {
                    grid[tileRow][tileColumn] = RenderConstants.STAIRS_DOWN_CHAR;
                    return;
                }
            }
        }
    }

    private boolean tryStampInRoom(char[][] grid, Room room, boolean[][] reachable) {
        int centerColumn = room.centerColumn();
        int centerRow    = room.centerRow();
        if (isInBounds(centerColumn, centerRow) && reachable[centerRow][centerColumn]
                && isWalkableFloor(grid, centerColumn, centerRow)) {
            grid[centerRow][centerColumn] = RenderConstants.STAIRS_DOWN_CHAR;
            return true;
        }
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (reachable[tileRow][tileColumn] && isWalkableFloor(grid, tileColumn, tileRow)) {
                    grid[tileRow][tileColumn] = RenderConstants.STAIRS_DOWN_CHAR;
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Flood-fills the set of tiles reachable from the player spawn 'p' across passable tiles,
     * matching the same passability rule the connectivity audit uses.
     */
    private boolean[][] computeReachableFromSpawn(char[][] grid) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        boolean[][] visited = new boolean[gridHeight][gridWidth];
        int spawnColumn = -1;
        int spawnRow    = -1;
        for (int tileRow = 0; tileRow < gridHeight && spawnColumn < 0; tileRow++) {
            for (int tileColumn = 0; tileColumn < gridWidth; tileColumn++) {
                if (grid[tileRow][tileColumn] == 'p') {
                    spawnColumn = tileColumn;
                    spawnRow    = tileRow;
                    break;
                }
            }
        }
        if (spawnColumn < 0) return visited;

        int[] stackColumns = new int[gridWidth * gridHeight];
        int[] stackRows    = new int[gridWidth * gridHeight];
        int   stackTop     = 0;
        visited[spawnRow][spawnColumn] = true;
        stackColumns[stackTop] = spawnColumn;
        stackRows[stackTop]    = spawnRow;
        stackTop++;

        int[] deltaColumns = { 0,  0,  1, -1 };
        int[] deltaRows    = { 1, -1,  0,  0 };
        while (stackTop > 0) {
            stackTop--;
            int currentColumn = stackColumns[stackTop];
            int currentRow    = stackRows[stackTop];
            for (int direction = 0; direction < 4; direction++) {
                int neighborColumn = currentColumn + deltaColumns[direction];
                int neighborRow    = currentRow    + deltaRows[direction];
                if (!isInBounds(neighborColumn, neighborRow)) continue;
                if (visited[neighborRow][neighborColumn]) continue;
                if (!isPassableForBFS(grid[neighborRow][neighborColumn])) continue;
                visited[neighborRow][neighborColumn] = true;
                stackColumns[stackTop] = neighborColumn;
                stackRows[stackTop]    = neighborRow;
                stackTop++;
            }
        }
        return visited;
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    private boolean isWalkableFloor(char[][] grid, int tileColumn, int tileRow) {
        if (!isInBounds(tileColumn, tileRow)) return false;
        char cell = grid[tileRow][tileColumn];
        return cell == ' ' || cell == 'l' || cell == 'u' || cell == 'f';
    }

    private boolean isEnemySpawnEligible(char[][] grid, int tileColumn, int tileRow) {
        if (!isInBounds(tileColumn, tileRow)) return false;
        char cell = grid[tileRow][tileColumn];
        if (cell == ' ' || cell == 'l' || cell == 'u' || cell == 'f') return true;
        return cell == '.' || cell == 'O' || cell == 'm';
    }

    private boolean isInBounds(int tileColumn, int tileRow) {
        return tileColumn >= 0 && tileColumn < LevelGenConstants.LEVEL_GEN_GRID_WIDTH
            && tileRow    >= 0 && tileRow    < LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
    }

    private int randomBetween(int minInclusive, int maxInclusive) {
        if (maxInclusive <= minInclusive) return minInclusive;
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }

    private float randomFloat(float minInclusive, float maxInclusive) {
        return minInclusive + random.nextFloat() * (maxInclusive - minInclusive);
    }

    private void fillAll(char[][] grid, char fillChar) {
        for (int tileRow = 0; tileRow < LevelGenConstants.LEVEL_GEN_GRID_HEIGHT; tileRow++) {
            for (int tileColumn = 0; tileColumn < LevelGenConstants.LEVEL_GEN_GRID_WIDTH; tileColumn++) {
                grid[tileRow][tileColumn] = fillChar;
            }
        }
    }

    private Level buildFallbackLevel() {
        char[][] grid        = new char[LevelGenConstants.LEVEL_GEN_GRID_HEIGHT][LevelGenConstants.LEVEL_GEN_GRID_WIDTH];
        int centerColumn     = LevelGenConstants.LEVEL_GEN_GRID_WIDTH  / 2;
        int centerRow        = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT / 2;
        fillAll(grid, 'x');
        for (int tileRow = centerRow - 2; tileRow <= centerRow + 2; tileRow++) {
            for (int tileColumn = centerColumn - 4; tileColumn <= centerColumn + 4; tileColumn++) {
                grid[tileRow][tileColumn] = ' ';
            }
        }
        grid[centerRow][centerColumn] = 'p';
        return new Level(grid, new ArrayList<>(), new ArrayList<>());
    }
}
