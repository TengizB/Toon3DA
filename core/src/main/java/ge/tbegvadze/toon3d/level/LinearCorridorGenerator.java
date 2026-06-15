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
        ENTRANCE, STANDARD, SERVER_ROOM,
        MEDICAL_BAY, ARMORY, CRYO_CHAMBER,
        POWER_PLANT, COMMAND_CENTER, CONTAINMENT_BLOCK
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
        placeProps(grid, rooms);
        placePickups(grid, rooms);
        placeWeaponSpawns(grid, rooms);

        // Doors placed after all corridor carving and room carving are done
        placeDoors(grid);

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
        int interiorWidth  = randomBetween(4, 10);
        int interiorHeight = randomBetween(4, 8);
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

        List<Room> rooms = new ArrayList<>();
        placeVerticalSpineRooms(grid, rooms, spineColumn, spineStartRow, spineEndRow);
        return rooms;
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
        int interiorWidth  = randomBetween(4, 8);
        int interiorHeight = randomBetween(4, 10);
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

        // Remaining rooms — cumulative probability bands (same as LevelGenerator)
        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            Room  room = rooms.get(roomIndex);
            if (room.type != RoomType.STANDARD) continue;
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
            } else if (serverRoomCount < LevelGenConstants.LEVEL_GEN_SERVER_ROOM_MAX_PER_LEVEL
                    && roll < LevelGenConstants.LEVEL_GEN_CRYO_CHANCE
                              + LevelGenConstants.LEVEL_GEN_CONTAINMENT_CHANCE
                              + LevelGenConstants.LEVEL_GEN_SERVER_ROOM_CHANCE) {
                room.type = RoomType.SERVER_ROOM;
                serverRoomCount++;
            }
        }

        // Suppress "assigned but never read" warnings for future-guard boolean flags.
        boolean unused = commandCenterPlaced || armoryPlaced;
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
     * recorded spine-centre tile list. Only places where no door is immediately adjacent.
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
            grid[tileRow][tileColumn] = 'P';
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
                default:                 placeStandardRoomProps(grid, room);      break;
            }
        }
    }

    private void placeStandardRoomProps(char[][] grid, Room room) {
        char[] propChars = { 'g', 'E', 'C', '#' };
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (!isWalkableFloor(grid, tileColumn, tileRow)) continue;
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
                case POWER_PLANT:
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
        switch (random.nextInt(4)) {
            case 0:  return ItemType.WEAPON_SHOTGUN;
            case 1:  return ItemType.WEAPON_CHAINGUN;
            case 2:  return ItemType.WEAPON_PLASMA;
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
        int startColumn = rooms.get(0).centerColumn();
        int startRow    = rooms.get(0).centerRow();
        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            Room room = rooms.get(roomIndex);
            if (!isTileReachable(grid, startColumn, startRow,
                                 room.centerColumn(), room.centerRow())) {
                carveEmergencyCorridor(grid, startColumn, startRow,
                                       room.centerColumn(), room.centerRow());
            }
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

    private boolean isPassableForBFS(char cell) {
        return cell == ' ' || cell == 'l' || cell == 'u' || cell == 'f'
            || Level.isDoor(cell)
            || cell == 'p' || cell == RenderConstants.STAIRS_DOWN_CHAR;
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
    // Phase 6 — Stairs
    // -------------------------------------------------------------------------

    private void stampStairsDown(char[][] grid, List<Room> rooms) {
        for (int roomIndex = rooms.size() - 1; roomIndex >= 1; roomIndex--) {
            Room room = rooms.get(roomIndex);
            if (room.type == RoomType.COMMAND_CENTER && tryStampInRoom(grid, room)) return;
        }
        for (int roomIndex = rooms.size() - 1; roomIndex >= 1; roomIndex--) {
            Room room = rooms.get(roomIndex);
            if (room.type == RoomType.POWER_PLANT && tryStampInRoom(grid, room)) return;
        }
        for (int roomIndex = rooms.size() - 1; roomIndex >= 1; roomIndex--) {
            if (tryStampInRoom(grid, rooms.get(roomIndex))) return;
        }
        if (!rooms.isEmpty()) tryStampInRoom(grid, rooms.get(0));
    }

    private boolean tryStampInRoom(char[][] grid, Room room) {
        int centerColumn = room.centerColumn();
        int centerRow    = room.centerRow();
        if (isWalkableFloor(grid, centerColumn, centerRow)) {
            grid[centerRow][centerColumn] = RenderConstants.STAIRS_DOWN_CHAR;
            return true;
        }
        for (int attempt = 0; attempt < 12; attempt++) {
            if (room.interiorWidth() <= 0 || room.interiorHeight() <= 0) break;
            int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
            int tileRow    = room.bottomRow  + 1 + random.nextInt(room.interiorHeight());
            if (isWalkableFloor(grid, tileColumn, tileRow)) {
                grid[tileRow][tileColumn] = RenderConstants.STAIRS_DOWN_CHAR;
                return true;
            }
        }
        return false;
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
