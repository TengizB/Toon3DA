package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.util.LevelGenConstants;
import ge.tbegvadze.toon3d.util.RenderConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Procedural dungeon generator — six-phase algorithm inspired by Shattered Pixel Dungeon.
 *
 * Phase 1 — Room Placement:     randomly sized rectangular rooms placed without overlap,
 *                                typed as one of: ENTRANCE, STANDARD, LARGE, SERVER_ROOM,
 *                                MEDICAL_BAY, ARMORY, CRYO_CHAMBER, POWER_PLANT,
 *                                COMMAND_CENTER, or CONTAINMENT_BLOCK.
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
 *   ENTRANCE          — player spawn room; fully lit, no hazards.
 *   STANDARD          — baseline UAC lab room; existing decoration logic.
 *   LARGE             — landmark arena (reactor floor, cargo bay); patterned columns, sparse props.
 *   SERVER_ROOM       — data vault; terminal walls 't', rack rows of T/L props, dark atmosphere.
 *   MEDICAL_BAY       — guaranteed once per level; medical-tile 'M' walls, medkit/stim pickups.
 *   ARMORY            — last-stand room; blast-scarred 'X' walls, weapon racks '=', corpses.
 *   CRYO_CHAMBER      — cryogenic bay; frost 'Z' walls, bio-pod '&' rows, dim lighting.
 *   POWER_PLANT       — reactor core (LARGE-class); radiation 'U' walls, generator '%' cluster.
 *   COMMAND_CENTER    — control room (LARGE-class); glass 'N' walls, terminal 'T' rows.
 *   CONTAINMENT_BLOCK — cell block; glass 'N' cell fronts, quarantine 'Q' walls, high enemy den.
 *   RESEARCH_LAB      — sci-fi set-piece; holo-data 'D' walls, specimen tanks 'I', AI core 'J',
 *                        force-field barrier 'F', energy scorch 'e' decals. At most 1 per level.
 *
 * Wide hallways: 2-3 MST edges widened to 3 tiles (centre lit, ribs normal) with evenly
 * spaced 'P' columns along the centre spine. The width-3 invariant guarantees at least one
 * 1-tile lane on each side of every column — passage is never sealed.
 *
 * Grid convention: (0,0) = bottom-left tile, Y-up (matches project-wide standard).
 * No LibGDX imports — pure Java logic, fully unit-testable without an OpenGL context.
 * Lives in the level package to access the package-private Level(char[][], List) constructor.
 */
public class LevelGenerator implements ILevelGenerator {

    private enum WallContext { CORRIDOR, ROOM, MIXED, INTERIOR }

    private enum RoomType {
        ENTRANCE, STANDARD, LARGE, SERVER_ROOM,
        MEDICAL_BAY, ARMORY, CRYO_CHAMBER,
        POWER_PLANT, COMMAND_CENTER, CONTAINMENT_BLOCK,
        RESEARCH_LAB
    }

    private final Random         random;
    private final LevelGenConfig config;

    // MST room-pair references captured during connectivity so widenSelectedCorridors()
    // can re-carve chosen edges at width 3 without re-running the MST selection.
    private List<Room[]> mstEdgeRooms;

    // Centre-spine tiles from each widened corridor, recorded during carving so
    // placeWideHallwayColumns() can walk them in order and space columns evenly.
    private List<int[]> wideHallwaySpineTiles;

    // Weapon spawn points collected during phase 3; consumed by World to create GroundItems.
    private List<WeaponSpawnPoint> weaponSpawnPoints;

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
        char[][] grid = new char[LevelGenConstants.LEVEL_GEN_GRID_HEIGHT][LevelGenConstants.LEVEL_GEN_GRID_WIDTH];
        fillAll(grid, 'x');
        mstEdgeRooms          = new ArrayList<>();
        wideHallwaySpineTiles = new ArrayList<>();
        weaponSpawnPoints     = new ArrayList<>();

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
        themeNewRoomWalls(grid, rooms);
        placePlayerSpawn(grid, rooms.get(0));
        placeColumns(grid, rooms);
        placeLargeRoomColumns(grid, rooms);
        placeWideHallwayColumns(grid);
        placeProps(grid, rooms);
        placeServerRoomProps(grid, rooms);
        placeLargeRoomProps(grid, rooms);
        placeMedicalBayProps(grid, rooms);
        placeArmoryProps(grid, rooms);
        placeCryoChamberProps(grid, rooms);
        placePowerPlantProps(grid, rooms);
        placeCommandCenterProps(grid, rooms);
        placeContainmentBlockProps(grid, rooms);
        placeResearchLabProps(grid, rooms);
        placePickups(grid, rooms);
        placeWeaponSpawns(grid, rooms);

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

        // Phase 6 — stamp exactly one stairs-down exit
        stampStairsDown(grid, rooms);

        return new Level(grid, spawnPoints, weaponSpawnPoints);
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
            int margin = LevelGenConstants.LEVEL_GEN_ROOM_MARGIN;
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

        while (rooms.size() < LevelGenConstants.LEVEL_GEN_TARGET_ROOMS
                && attempts < LevelGenConstants.LEVEL_GEN_PLACEMENT_TRIES) {
            attempts++;

            int interiorWidth  = randomBetween(LevelGenConstants.LEVEL_GEN_ROOM_MIN_WIDTH,
                                               LevelGenConstants.LEVEL_GEN_ROOM_MAX_WIDTH);
            int interiorHeight = randomBetween(LevelGenConstants.LEVEL_GEN_ROOM_MIN_HEIGHT,
                                               LevelGenConstants.LEVEL_GEN_ROOM_MAX_HEIGHT);
            int totalWidth     = interiorWidth  + 2;
            int totalHeight    = interiorHeight + 2;

            int maxLeftColumn = LevelGenConstants.LEVEL_GEN_GRID_WIDTH  - totalWidth  - 1;
            int maxBottomRow  = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT - totalHeight - 1;
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
     * Multi-step room-type assignment:
     *
     * Step A — Guaranteed/rare uniques (run first to ensure they always appear):
     *   ENTRANCE:       room 0, always.
     *   MEDICAL_BAY:    guaranteed one per level, mid-distance, ≥7×6, hard cap 1.
     *   COMMAND_CENTER: deepest eligible room ≥8×7, 50% chance, hard cap 1.
     *   ARMORY:         random eligible ≥6×6, 80% chance, hard cap 1.
     *
     * Step B — LARGE-class rooms (consume LARGE-eligible rooms after uniques are placed):
     *   POWER_PLANT:    from LARGE-eligible candidates, 45% chance, hard cap 1; rest → LARGE.
     *
     * Step C — Remaining rooms classified by sequential cumulative roll bands (one roll per room):
     *   CRYO_CHAMBER:       ≥7×7, roll in [0, CRYO_CHANCE)           → ~16 % of rolls, hard cap 2.
     *   CONTAINMENT_BLOCK:  ≥8×6, roll in [CRYO_CHANCE, +CONTAIN)    → next ~16 % band, hard cap 2.
     *   SERVER_ROOM:        any,   roll in [prev, +SERVER_CHANCE)     → next ~16 % band, hard cap per config.
     *   STANDARD:           fallback for all remaining rolls.
     *
     * Because these are else-if branches on a single roll, the bands are mutually exclusive per room.
     * A room that is cryo-ineligible falls through to the CONTAINMENT check at the same roll value,
     * so its effective CONTAINMENT threshold is the full [0, CRYO+CONTAIN) range (~32 %).
     */
    private void assignRoomTypes(List<Room> rooms) {
        rooms.get(0).type = RoomType.ENTRANCE;

        boolean medicalBayPlaced    = false;
        boolean armoryPlaced        = false;
        boolean commandCenterPlaced = false;
        boolean powerPlantPlaced    = false;
        boolean researchLabPlaced   = false;
        int     cryoChamberCount    = 0;
        int     containmentCount    = 0;
        int     serverRoomCount     = 0;

        // Step A — Medical Bay: guaranteed, mid-distance room with minimum size
        if (!medicalBayPlaced) {
            Room medicalCandidate = findMedicalBayCandidate(rooms);
            if (medicalCandidate != null) {
                medicalCandidate.type = RoomType.MEDICAL_BAY;
                medicalBayPlaced = true;
            }
        }

        // Step A — Command Center: deepest eligible LARGE-class room at 50% chance
        if (!commandCenterPlaced && random.nextFloat() < LevelGenConstants.LEVEL_GEN_COMMAND_CHANCE) {
            Room commandCandidate = findCommandCenterCandidate(rooms);
            if (commandCandidate != null) {
                commandCandidate.type = RoomType.COMMAND_CENTER;
                commandCenterPlaced = true;
            }
        }

        // Step A — Armory: random eligible room at 80% chance
        if (!armoryPlaced && random.nextFloat() < LevelGenConstants.LEVEL_GEN_ARMORY_CHANCE) {
            Room armoryCandidate = findArmoryCandidate(rooms);
            if (armoryCandidate != null) {
                armoryCandidate.type = RoomType.ARMORY;
                armoryPlaced = true;
            }
        }

        // Step B — LARGE-eligible rooms: Power Plant or plain LARGE
        int largeRoomCount = 0;
        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            Room room = rooms.get(roomIndex);
            if (room.type != RoomType.STANDARD) continue;
            boolean largeEligible = room.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_LARGE_MIN_DIM
                                 && room.interiorHeight() >= LevelGenConstants.LEVEL_GEN_LARGE_MIN_DIM;
            if (!largeEligible) continue;
            if (config.enableLargeRooms
                    && largeRoomCount < LevelGenConstants.LEVEL_GEN_LARGE_ROOM_MAX_PER_LEVEL) {
                if (!powerPlantPlaced
                        && random.nextFloat() < LevelGenConstants.LEVEL_GEN_POWERPLANT_CHANCE) {
                    room.type = RoomType.POWER_PLANT;
                    powerPlantPlaced = true;
                } else {
                    room.type = RoomType.LARGE;
                }
                largeRoomCount++;
            }
        }

        // Step C — remaining STANDARD rooms get specialty or stay STANDARD
        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            Room room = rooms.get(roomIndex);
            if (room.type != RoomType.STANDARD) continue;

            boolean cryoEligible        = room.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_CRYO_MIN_WIDTH
                                       && room.interiorHeight() >= LevelGenConstants.LEVEL_GEN_CRYO_MIN_HEIGHT;
            boolean containmentEligible = room.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_CONTAINMENT_MIN_WIDTH
                                       && room.interiorHeight() >= LevelGenConstants.LEVEL_GEN_CONTAINMENT_MIN_HEIGHT;

            float roll = random.nextFloat();
            if (cryoEligible
                    && cryoChamberCount < LevelGenConstants.LEVEL_GEN_CRYO_MAX
                    && roll < LevelGenConstants.LEVEL_GEN_CRYO_CHANCE) {
                room.type = RoomType.CRYO_CHAMBER;
                cryoChamberCount++;
            } else if (containmentEligible
                    && containmentCount < LevelGenConstants.LEVEL_GEN_CONTAINMENT_MAX
                    && roll < LevelGenConstants.LEVEL_GEN_CRYO_CHANCE + LevelGenConstants.LEVEL_GEN_CONTAINMENT_CHANCE) {
                room.type = RoomType.CONTAINMENT_BLOCK;
                containmentCount++;
            } else if (config.enableServerRooms
                    && serverRoomCount < LevelGenConstants.LEVEL_GEN_SERVER_ROOM_MAX_PER_LEVEL
                    && roll < LevelGenConstants.LEVEL_GEN_CRYO_CHANCE
                              + LevelGenConstants.LEVEL_GEN_CONTAINMENT_CHANCE
                              + LevelGenConstants.LEVEL_GEN_SERVER_ROOM_CHANCE) {
                room.type = RoomType.SERVER_ROOM;
                serverRoomCount++;
            } else if (!researchLabPlaced
                    && room.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_RESEARCH_LAB_MIN_WIDTH
                    && room.interiorHeight() >= LevelGenConstants.LEVEL_GEN_RESEARCH_LAB_MIN_HEIGHT
                    && roll < LevelGenConstants.LEVEL_GEN_CRYO_CHANCE
                              + LevelGenConstants.LEVEL_GEN_CONTAINMENT_CHANCE
                              + LevelGenConstants.LEVEL_GEN_SERVER_ROOM_CHANCE
                              + LevelGenConstants.LEVEL_GEN_RESEARCH_LAB_CHANCE) {
                room.type = RoomType.RESEARCH_LAB;
                researchLabPlaced = true;
            }
            // else: remains STANDARD
        }
    }

    /**
     * Finds the best candidate for a Medical Bay: a STANDARD room at roughly mid-depth
     * (not the nearest rooms, not the deepest) meeting minimum size requirements.
     */
    private Room findMedicalBayCandidate(List<Room> rooms) {
        int midIndex = rooms.size() / 2;
        // Search outward from mid-point to find first eligible room
        for (int radius = 0; radius <= rooms.size() / 2; radius++) {
            int forwardIndex  = midIndex + radius;
            int backwardIndex = midIndex - radius;
            if (forwardIndex < rooms.size()) {
                Room candidate = rooms.get(forwardIndex);
                if (candidate.type == RoomType.STANDARD
                        && candidate.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_MEDICAL_BAY_MIN_WIDTH
                        && candidate.interiorHeight() >= LevelGenConstants.LEVEL_GEN_MEDICAL_BAY_MIN_HEIGHT) {
                    return candidate;
                }
            }
            if (backwardIndex > 0 && backwardIndex != forwardIndex) {
                Room candidate = rooms.get(backwardIndex);
                if (candidate.type == RoomType.STANDARD
                        && candidate.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_MEDICAL_BAY_MIN_WIDTH
                        && candidate.interiorHeight() >= LevelGenConstants.LEVEL_GEN_MEDICAL_BAY_MIN_HEIGHT) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * Finds the best candidate for a Command Center: the deepest STANDARD room
     * meeting minimum size requirements (≥8×7).
     */
    private Room findCommandCenterCandidate(List<Room> rooms) {
        for (int roomIndex = rooms.size() - 1; roomIndex >= 1; roomIndex--) {
            Room candidate = rooms.get(roomIndex);
            if (candidate.type == RoomType.STANDARD
                    && candidate.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_COMMAND_MIN_WIDTH
                    && candidate.interiorHeight() >= LevelGenConstants.LEVEL_GEN_COMMAND_MIN_HEIGHT) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Finds the best candidate for an Armory: any STANDARD room meeting minimum size (≥6×6),
     * selected randomly from eligible candidates.
     */
    private Room findArmoryCandidate(List<Room> rooms) {
        List<Room> eligible = new ArrayList<>();
        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            Room candidate = rooms.get(roomIndex);
            if (candidate.type == RoomType.STANDARD
                    && candidate.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_ARMORY_MIN_WIDTH
                    && candidate.interiorHeight() >= LevelGenConstants.LEVEL_GEN_ARMORY_MIN_HEIGHT) {
                eligible.add(candidate);
            }
        }
        if (eligible.isEmpty()) return null;
        return eligible.get(random.nextInt(eligible.size()));
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
            if (manhattanDistance(roomA, roomB) <= LevelGenConstants.LEVEL_GEN_LOOP_MAX_DISTANCE) {
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
                                   || edge[0].type == RoomType.COMMAND_CENTER
                                   || edge[0].type == RoomType.POWER_PLANT
                                   || edge[1].type == RoomType.ENTRANCE
                                   || edge[1].type == RoomType.LARGE
                                   || edge[1].type == RoomType.COMMAND_CENTER
                                   || edge[1].type == RoomType.POWER_PLANT;
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
            if (hallwaysWidened >= LevelGenConstants.LEVEL_GEN_WIDE_HALLWAY_COUNT) break;
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
    // Phase 3 — Decoration
    // -------------------------------------------------------------------------

    /**
     * Assigns floor lighting for each room type:
     *   ENTRANCE          — fully lit ' ' (safe start zone, no conversion).
     *   SERVER_ROOM       — dark 'u' dominant, sprinkle of 'f' flickering, no bright ' '.
     *   LARGE             — mostly lit ' ', ring of 'l' at edges, one small 'u' dark alcove.
     *   MEDICAL_BAY       — fully lit ' ' (clinical brightness).
     *   ARMORY            — mostly lit ' ', dark 'u' corners for hiding stashes.
     *   CRYO_CHAMBER      — dim 'l' dominant with rare 'u' patches (cold, unwelcoming).
     *   POWER_PLANT       — dark 'u' floor, 'f' flickering near centre (reactor hum).
     *   COMMAND_CENTER    — lit ' ' dominant, thin 'l' ring at perimeter.
     *   CONTAINMENT_BLOCK — very dark 'u' dominant, 'f' flickering at cell fronts.
     *   STANDARD          — existing mixed logic (config-driven unlitFloors / normalFloors).
     */
    private void assignFloorLighting(char[][] grid, List<Room> rooms) {
        for (Room room : rooms) {
            if (room.type == RoomType.ENTRANCE) continue;
            switch (room.type) {
                case SERVER_ROOM:       assignServerRoomFloor(grid, room);       break;
                case LARGE:             assignLargeRoomFloor(grid, room);         break;
                case MEDICAL_BAY:       assignMedicalBayFloor(grid, room);        break;
                case ARMORY:            assignArmoryFloor(grid, room);             break;
                case CRYO_CHAMBER:      assignCryoChamberFloor(grid, room);        break;
                case POWER_PLANT:       assignPowerPlantFloor(grid, room);         break;
                case COMMAND_CENTER:    assignCommandCenterFloor(grid, room);      break;
                case CONTAINMENT_BLOCK: assignContainmentBlockFloor(grid, room);   break;
                default:                assignStandardRoomFloor(grid, room);       break;
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
                } else if (roll < 0.55f) {
                    grid[tileRow][tileColumn] = 'u';
                } else {
                    grid[tileRow][tileColumn] = 'l';
                }
            }
        }
    }

    private void assignLargeRoomFloor(char[][] grid, Room room) {
        boolean hasAlcove    = random.nextBoolean();
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

    private void assignMedicalBayFloor(char[][] grid, Room room) {
        // Medical bays are fully lit for clinical visibility — no conversion needed
        // (all tiles are already ' ' from carveRoomInteriors)
    }

    private void assignArmoryFloor(char[][] grid, Room room) {
        // Mostly lit, dark corners for tension and hidden pickups
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                boolean isCorner = (tileColumn <= room.leftColumn  + 2 || tileColumn >= room.rightColumn - 2)
                                && (tileRow    <= room.bottomRow   + 2 || tileRow    >= room.topRow      - 2);
                if (isCorner && random.nextFloat() < 0.60f) {
                    grid[tileRow][tileColumn] = 'u';
                } else if (random.nextFloat() < 0.12f) {
                    grid[tileRow][tileColumn] = 'l';
                }
            }
        }
    }

    private void assignCryoChamberFloor(char[][] grid, Room room) {
        // Dim 'l' dominant; rare 'u' patches give a cold, unwelcoming feel
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                float roll = random.nextFloat();
                if (roll < 0.15f) {
                    grid[tileRow][tileColumn] = 'u';
                } else {
                    grid[tileRow][tileColumn] = 'l';
                }
            }
        }
    }

    private void assignPowerPlantFloor(char[][] grid, Room room) {
        // Dark 'u' floor, 'f' flickering near the centre (reactor hum)
        int centreColumn = room.centerColumn();
        int centreRow    = room.centerRow();
        int flickerBudget = 4;
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                boolean nearCentre = Math.abs(tileColumn - centreColumn) <= 2
                                  && Math.abs(tileRow    - centreRow)    <= 2;
                if (nearCentre && flickerBudget > 0 && random.nextFloat() < 0.40f) {
                    grid[tileRow][tileColumn] = 'f';
                    flickerBudget--;
                } else {
                    grid[tileRow][tileColumn] = 'u';
                }
            }
        }
    }

    private void assignCommandCenterFloor(char[][] grid, Room room) {
        // Lit ' ' dominant, thin 'l' ring at perimeter
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                boolean isEdgeTile = tileColumn == room.leftColumn  + 1
                                  || tileColumn == room.rightColumn - 1
                                  || tileRow    == room.bottomRow   + 1
                                  || tileRow    == room.topRow      - 1;
                if (isEdgeTile && random.nextFloat() < 0.35f) {
                    grid[tileRow][tileColumn] = 'l';
                }
            }
        }
    }

    private void assignContainmentBlockFloor(char[][] grid, Room room) {
        // Very dark 'u' dominant, 'f' flickering at random spots for dread
        int flickerBudget = 3;
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                float roll = random.nextFloat();
                if (roll < 0.08f && flickerBudget > 0) {
                    grid[tileRow][tileColumn] = 'f';
                    flickerBudget--;
                } else {
                    grid[tileRow][tileColumn] = 'u';
                }
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
        for (int tileRow = 0; tileRow < LevelGenConstants.LEVEL_GEN_GRID_HEIGHT; tileRow++) {
            for (int tileColumn = 0; tileColumn < LevelGenConstants.LEVEL_GEN_GRID_WIDTH; tileColumn++) {
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
     * Overrides perimeter walls of SERVER_ROOM to terminal walls 't'.
     * Runs AFTER assignWallVariety() so it overrides the generic vent/conduit theming.
     */
    private void themeServerRoomWalls(char[][] grid, List<Room> rooms) {
        for (Room room : rooms) {
            if (room.type != RoomType.SERVER_ROOM) continue;
            for (int tileRow = room.bottomRow; tileRow <= room.topRow; tileRow++) {
                for (int tileColumn = room.leftColumn; tileColumn <= room.rightColumn; tileColumn++) {
                    if (!isInBounds(tileColumn, tileRow)) continue;
                    if (!Level.isWall(grid[tileRow][tileColumn])) continue;
                    if (!facesRoomInterior(grid, tileColumn, tileRow, room)) continue;
                    if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_SERVER_WALL_TERMINAL_CHANCE) {
                        grid[tileRow][tileColumn] = 't';
                    }
                }
            }
        }
    }

    /**
     * Applies room-type-specific wall theming for the 6 new room types.
     * Runs AFTER themeServerRoomWalls() so ordering is consistent.
     *
     * Per-type wall replacement rules (perimeter only, using facesRoomInterior):
     *   MEDICAL_BAY:       70% 'M' (medical wall), 10% 'Q' (bio accent), rest keep generic.
     *   ARMORY:            50% 'X' (blast wall), rest keep generic.
     *   CRYO_CHAMBER:      70% 'Z' (cryo wall), 15% 'N' (glass accent), rest keep generic.
     *   POWER_PLANT:       40% 'U' (radiation wall), 20% 'S' (emergency strip), rest keep generic.
     *   COMMAND_CENTER:    30% 'N' (glass observation), 15% 'S' (emergency strip), rest keep generic.
     *   CONTAINMENT_BLOCK: 40% 'N' (glass cell fronts), 15% 'Q' (quarantine), rest keep generic.
     */
    private void themeNewRoomWalls(char[][] grid, List<Room> rooms) {
        for (Room room : rooms) {
            for (int tileRow = room.bottomRow; tileRow <= room.topRow; tileRow++) {
                for (int tileColumn = room.leftColumn; tileColumn <= room.rightColumn; tileColumn++) {
                    if (!isInBounds(tileColumn, tileRow)) continue;
                    if (!Level.isWall(grid[tileRow][tileColumn])) continue;
                    if (!facesRoomInterior(grid, tileColumn, tileRow, room)) continue;
                    float roll = random.nextFloat();
                    switch (room.type) {
                        case MEDICAL_BAY:
                            if (roll < LevelGenConstants.LEVEL_GEN_MEDICAL_WALL_CHANCE) {
                                grid[tileRow][tileColumn] = 'M';
                            } else if (roll < LevelGenConstants.LEVEL_GEN_MEDICAL_WALL_CHANCE
                                             + LevelGenConstants.LEVEL_GEN_MEDICAL_BIO_WALL_CHANCE) {
                                grid[tileRow][tileColumn] = 'Q';
                            }
                            break;
                        case ARMORY:
                            if (roll < LevelGenConstants.LEVEL_GEN_ARMORY_BLAST_WALL_CHANCE) {
                                grid[tileRow][tileColumn] = 'X';
                            }
                            break;
                        case CRYO_CHAMBER:
                            if (roll < LevelGenConstants.LEVEL_GEN_CRYO_WALL_CHANCE) {
                                grid[tileRow][tileColumn] = 'Z';
                            } else if (roll < LevelGenConstants.LEVEL_GEN_CRYO_WALL_CHANCE
                                             + LevelGenConstants.LEVEL_GEN_CRYO_GLASS_WALL_CHANCE) {
                                grid[tileRow][tileColumn] = 'N';
                            }
                            break;
                        case POWER_PLANT:
                            if (roll < LevelGenConstants.LEVEL_GEN_POWERPLANT_RAD_WALL_CHANCE) {
                                grid[tileRow][tileColumn] = 'U';
                            } else if (roll < LevelGenConstants.LEVEL_GEN_POWERPLANT_RAD_WALL_CHANCE
                                             + LevelGenConstants.LEVEL_GEN_POWERPLANT_EMERG_WALL_CHANCE) {
                                grid[tileRow][tileColumn] = 'S';
                            }
                            break;
                        case COMMAND_CENTER:
                            if (roll < LevelGenConstants.LEVEL_GEN_COMMAND_GLASS_WALL_CHANCE) {
                                grid[tileRow][tileColumn] = 'N';
                            } else if (roll < LevelGenConstants.LEVEL_GEN_COMMAND_GLASS_WALL_CHANCE
                                             + LevelGenConstants.LEVEL_GEN_COMMAND_EMERG_WALL_CHANCE) {
                                grid[tileRow][tileColumn] = 'S';
                            }
                            break;
                        case CONTAINMENT_BLOCK:
                            if (roll < LevelGenConstants.LEVEL_GEN_CONTAINMENT_GLASS_CHANCE) {
                                grid[tileRow][tileColumn] = 'N';
                            } else if (roll < LevelGenConstants.LEVEL_GEN_CONTAINMENT_GLASS_CHANCE
                                             + LevelGenConstants.LEVEL_GEN_CONTAINMENT_BIO_CHANCE) {
                                grid[tileRow][tileColumn] = 'Q';
                            }
                            break;
                        case RESEARCH_LAB:
                            if (roll < LevelGenConstants.LEVEL_GEN_RESEARCH_LAB_HOLO_WALL_CHANCE) {
                                grid[tileRow][tileColumn] = 'D';
                            }
                            break;
                        default:
                            break;
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
     * Places columns in STANDARD rooms only — all special room types have dedicated
     * prop/column handlers (LARGE, SERVER_ROOM, MEDICAL_BAY, ARMORY, CRYO_CHAMBER,
     * POWER_PLANT, COMMAND_CENTER, CONTAINMENT_BLOCK).
     */
    private void placeColumns(char[][] grid, List<Room> rooms) {
        if (!config.columns) return;
        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            Room room = rooms.get(roomIndex);
            if (room.type != RoomType.STANDARD) continue;
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
        int spacing       = LevelGenConstants.LEVEL_GEN_WIDE_HALLWAY_COLUMN_SPACING;
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

    /**
     * Places props in STANDARD rooms only — all special room types have dedicated
     * prop handlers.
     */
    private void placeProps(char[][] grid, List<Room> rooms) {
        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            Room room = rooms.get(roomIndex);
            if (room.type != RoomType.STANDARD) continue;
            for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
                for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                    if (!isWalkableFloor(grid, tileColumn, tileRow)) continue;
                    if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
                    if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_PROP_CHANCE) {
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
            for (int tileRow = room.bottomRow + 2; tileRow < room.topRow - 1; tileRow++) {
                int rowOffset = tileRow - (room.bottomRow + 2);
                if (rowOffset % 2 != 0) continue;
                for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                    if (!isWalkableFloor(grid, tileColumn, tileRow)) continue;
                    if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
                    if (isAdjacentToDoorAxis(grid, tileColumn, tileRow)) continue;
                    char prop = random.nextFloat() < LevelGenConstants.LEVEL_GEN_SERVER_LOCKER_RATIO ? 'L' : 'T';
                    grid[tileRow][tileColumn] = prop;
                }
            }
        } else {
            for (int tileColumn = room.leftColumn + 2; tileColumn < room.rightColumn - 1; tileColumn++) {
                int columnOffset = tileColumn - (room.leftColumn + 2);
                if (columnOffset % 2 != 0) continue;
                for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
                    if (!isWalkableFloor(grid, tileColumn, tileRow)) continue;
                    if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
                    if (isAdjacentToDoorAxis(grid, tileColumn, tileRow)) continue;
                    char prop = random.nextFloat() < LevelGenConstants.LEVEL_GEN_SERVER_LOCKER_RATIO ? 'L' : 'T';
                    grid[tileRow][tileColumn] = prop;
                }
            }
        }
    }

    /**
     * Places sparse props in LARGE rooms: crates and barrels at low density,
     * one optional explosive barrel near existing columns.
     */
    private void placeLargeRoomProps(char[][] grid, List<Room> rooms) {
        for (Room room : rooms) {
            if (room.type != RoomType.LARGE) continue;
            for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
                for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                    if (!isWalkableFloor(grid, tileColumn, tileRow)) continue;
                    if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
                    if (isAdjacentToDoorAxis(grid, tileColumn, tileRow)) continue;
                    if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_LARGE_PROP_CHANCE) {
                        char prop = random.nextFloat() < 0.70f ? 'C' : 'g';
                        grid[tileRow][tileColumn] = prop;
                    }
                }
            }
        }
    }

    /**
     * Medical Bay props: weapon racks '=' as equipment storage, security cameras '#'
     * near walls, blood stains '.' and corpses 'm' for backstory. Sparse overall.
     */
    private void placeMedicalBayProps(char[][] grid, List<Room> rooms) {
        for (Room room : rooms) {
            if (room.type != RoomType.MEDICAL_BAY) continue;
            // Place security cameras '#' near walls
            tryPlaceAtmosphericPropNearWall(grid, room, '#');
            if (random.nextBoolean()) tryPlaceAtmosphericPropNearWall(grid, room, '#');
            // Scatter blood stains and a corpse for narrative
            if (config.bloodStains) tryPlaceAtmosphericProp(grid, room, '.');
            if (config.bloodStains) tryPlaceAtmosphericProp(grid, room, '.');
            if (config.corpses && random.nextBoolean()) tryPlaceAtmosphericPropNearWall(grid, room, 'm');
            // General sparse props
            for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
                for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                    if (!isWalkableFloor(grid, tileColumn, tileRow)) continue;
                    if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
                    if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_MEDICAL_PROP_CHANCE) {
                        char prop = randomMedicalPropChar();
                        if (prop != '\0' && !(Level.isPropSolid(prop)
                                && isAdjacentToDoorAxis(grid, tileColumn, tileRow))) {
                            grid[tileRow][tileColumn] = prop;
                        }
                    }
                }
            }
        }
    }

    private char randomMedicalPropChar() {
        float roll = random.nextFloat();
        if (roll < 0.35f) return 'C';   // crate (supply box)
        if (roll < 0.55f) return 'T';   // terminal (medical console)
        if (roll < 0.70f) return '.';   // blood stain
        if (roll < 0.85f) return 'O';   // oil/fluid pool
        return 'm';                      // corpse
    }

    /**
     * Armory props: weapon racks '=' in rows, crates 'C' (ammo), security camera '#',
     * scattered blood stains and corpses for the "last stand" narrative.
     */
    private void placeArmoryProps(char[][] grid, List<Room> rooms) {
        for (Room room : rooms) {
            if (room.type != RoomType.ARMORY) continue;
            // Weapon rack rows along the short axis
            placeArmoryWeaponRacks(grid, room);
            // Security camera at entrance wall
            tryPlaceAtmosphericPropNearWall(grid, room, '#');
            // Battlefield aftermath
            if (config.bloodStains) {
                tryPlaceAtmosphericProp(grid, room, '.');
                tryPlaceAtmosphericProp(grid, room, '.');
            }
            if (config.corpses) {
                tryPlaceAtmosphericPropNearWall(grid, room, 'm');
                if (random.nextBoolean()) tryPlaceAtmosphericPropNearWall(grid, room, 'm');
            }
        }
    }

    private void placeArmoryWeaponRacks(char[][] grid, Room room) {
        int racksPlaced = 0;
        int maxRacks    = LevelGenConstants.LEVEL_GEN_ARMORY_MIN_WEAPON_RACKS + random.nextInt(3);
        for (int attempt = 0; attempt < 40 && racksPlaced < maxRacks; attempt++) {
            int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
            int tileRow    = room.bottomRow  + 1 + random.nextInt(room.interiorHeight());
            if (!isWalkableFloor(grid, tileColumn, tileRow)) continue;
            if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
            if (isAdjacentToDoorAxis(grid, tileColumn, tileRow)) continue;
            if (!isAdjacentToAnyWall(grid, tileColumn, tileRow)) continue;
            grid[tileRow][tileColumn] = '=';
            racksPlaced++;
        }
    }

    /**
     * Cryo Chamber props: bio-pods '&' in rows (specimen containment), security cameras '#',
     * oil pools 'O' (coolant leaks), crates 'C' for cryo-supply.
     */
    private void placeCryoChamberProps(char[][] grid, List<Room> rooms) {
        for (Room room : rooms) {
            if (room.type != RoomType.CRYO_CHAMBER) continue;
            placeCryoPodRows(grid, room);
            tryPlaceAtmosphericPropNearWall(grid, room, '#');
            if (config.oilPools) {
                tryPlaceAtmosphericProp(grid, room, 'O');
                if (random.nextBoolean()) tryPlaceAtmosphericProp(grid, room, 'O');
            }
            if (config.crates && random.nextBoolean()) {
                tryPlaceAtmosphericPropNearWall(grid, room, 'C');
            }
        }
    }

    private void placeCryoPodRows(char[][] grid, Room room) {
        // Place bio-pods '&' along the longer wall in a row
        boolean horizontalRow = room.interiorWidth() >= room.interiorHeight();
        if (horizontalRow) {
            int podRow = room.bottomRow + 2;
            for (int tileColumn = room.leftColumn + 2; tileColumn < room.rightColumn - 1; tileColumn += 2) {
                if (!isWalkableFloor(grid, tileColumn, podRow)) continue;
                if (isAdjacentToDoor(grid, tileColumn, podRow)) continue;
                if (isAdjacentToDoorAxis(grid, tileColumn, podRow)) continue;
                grid[podRow][tileColumn] = '&';
            }
            // Second row on opposite wall if room is tall enough
            if (room.interiorHeight() >= 5) {
                int podRow2 = room.topRow - 2;
                for (int tileColumn = room.leftColumn + 2; tileColumn < room.rightColumn - 1; tileColumn += 2) {
                    if (!isWalkableFloor(grid, tileColumn, podRow2)) continue;
                    if (isAdjacentToDoor(grid, tileColumn, podRow2)) continue;
                    if (isAdjacentToDoorAxis(grid, tileColumn, podRow2)) continue;
                    grid[podRow2][tileColumn] = '&';
                }
            }
        } else {
            int podColumn = room.leftColumn + 2;
            for (int tileRow = room.bottomRow + 2; tileRow < room.topRow - 1; tileRow += 2) {
                if (!isWalkableFloor(grid, podColumn, tileRow)) continue;
                if (isAdjacentToDoor(grid, podColumn, tileRow)) continue;
                if (isAdjacentToDoorAxis(grid, podColumn, tileRow)) continue;
                grid[tileRow][podColumn] = '&';
            }
            if (room.interiorWidth() >= 5) {
                int podColumn2 = room.rightColumn - 2;
                for (int tileRow = room.bottomRow + 2; tileRow < room.topRow - 1; tileRow += 2) {
                    if (!isWalkableFloor(grid, podColumn2, tileRow)) continue;
                    if (isAdjacentToDoor(grid, podColumn2, tileRow)) continue;
                    if (isAdjacentToDoorAxis(grid, podColumn2, tileRow)) continue;
                    grid[tileRow][podColumn2] = '&';
                }
            }
        }
    }

    /**
     * Power Plant props: generators '%' clustered near centre, security cameras '#',
     * radioactive barrels 'g' scattered around (coolant drums), oil pools 'O'.
     */
    private void placePowerPlantProps(char[][] grid, List<Room> rooms) {
        for (Room room : rooms) {
            if (room.type != RoomType.POWER_PLANT) continue;
            // Cluster of generators near centre
            int generatorCount = LevelGenConstants.LEVEL_GEN_POWERPLANT_MIN_GENERATORS
                    + random.nextInt(LevelGenConstants.LEVEL_GEN_POWERPLANT_MAX_GENERATORS
                                     - LevelGenConstants.LEVEL_GEN_POWERPLANT_MIN_GENERATORS + 1);
            placeGeneratorCluster(grid, room, generatorCount);
            // Atmosphere props
            tryPlaceAtmosphericPropNearWall(grid, room, '#');
            if (config.radioactiveBarrels) {
                tryPlaceAtmosphericPropNearWall(grid, room, 'g');
                if (random.nextBoolean()) tryPlaceAtmosphericPropNearWall(grid, room, 'g');
            }
            if (config.oilPools) {
                tryPlaceAtmosphericProp(grid, room, 'O');
            }
        }
    }

    private void placeGeneratorCluster(char[][] grid, Room room, int generatorCount) {
        int centreColumn = room.centerColumn();
        int centreRow    = room.centerRow();
        int[] tryColumns = { centreColumn, centreColumn - 1, centreColumn + 1,
                             centreColumn - 2, centreColumn + 2,
                             centreColumn, centreColumn };
        int[] tryRows    = { centreRow, centreRow, centreRow,
                             centreRow, centreRow,
                             centreRow - 1, centreRow + 1 };
        int placed = 0;
        for (int attempt = 0; attempt < tryColumns.length && placed < generatorCount; attempt++) {
            int tileColumn = tryColumns[attempt];
            int tileRow    = tryRows[attempt];
            if (!isInBounds(tileColumn, tileRow)) continue;
            if (!isWalkableFloor(grid, tileColumn, tileRow)) continue;
            if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
            if (isAdjacentToDoorAxis(grid, tileColumn, tileRow)) continue;
            grid[tileRow][tileColumn] = '%';
            placed++;
        }
    }

    /**
     * Command Center props: terminals 'T' in a row (operator stations), security camera '#',
     * weapon rack '=' near the back, scattered blood stains for fallen crew.
     */
    private void placeCommandCenterProps(char[][] grid, List<Room> rooms) {
        for (Room room : rooms) {
            if (room.type != RoomType.COMMAND_CENTER) continue;
            placeCommandTerminalRow(grid, room);
            tryPlaceAtmosphericPropNearWall(grid, room, '#');
            if (random.nextBoolean()) tryPlaceAtmosphericPropNearWall(grid, room, '#');
            tryPlaceAtmosphericPropNearWall(grid, room, '=');
            if (config.bloodStains) tryPlaceAtmosphericProp(grid, room, '.');
            if (config.corpses && random.nextFloat() < 0.30f) {
                tryPlaceAtmosphericPropNearWall(grid, room, 'm');
            }
        }
    }

    private void placeCommandTerminalRow(char[][] grid, Room room) {
        int terminalCount  = LevelGenConstants.LEVEL_GEN_COMMAND_MIN_TERMINALS
                + random.nextInt(LevelGenConstants.LEVEL_GEN_COMMAND_MAX_TERMINALS
                                 - LevelGenConstants.LEVEL_GEN_COMMAND_MIN_TERMINALS + 1);
        // Place terminals along the back wall (top row of interior)
        int backRow   = room.topRow - 2;
        int placed    = 0;
        int attempts  = 0;
        for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn && placed < terminalCount; tileColumn++) {
            attempts++;
            if (!isWalkableFloor(grid, tileColumn, backRow)) continue;
            if (isAdjacentToDoor(grid, tileColumn, backRow)) continue;
            if (isAdjacentToDoorAxis(grid, tileColumn, backRow)) continue;
            grid[backRow][tileColumn] = 'T';
            placed++;
        }
        // Fill remainder randomly if back-row placement is short
        for (int extra = placed; extra < terminalCount && attempts < 40; extra++, attempts++) {
            tryPlaceAtmosphericPropNearWall(grid, room, 'T');
        }
    }

    /**
     * Containment Block props: bio-pods '&' (cell interiors), security cameras '#' at
     * cell fronts, blood stains and corpses throughout (harrowing atmosphere).
     */
    private void placeContainmentBlockProps(char[][] grid, List<Room> rooms) {
        for (Room room : rooms) {
            if (room.type != RoomType.CONTAINMENT_BLOCK) continue;
            // Bio-pods scattered as cell contents
            for (int attempt = 0; attempt < 20; attempt++) {
                int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
                int tileRow    = room.bottomRow  + 1 + random.nextInt(room.interiorHeight());
                if (!isWalkableFloor(grid, tileColumn, tileRow)) continue;
                if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
                if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_CONTAINMENT_PROP_CHANCE) {
                    char prop = randomContainmentPropChar();
                    if (prop != '\0' && !(Level.isPropSolid(prop)
                            && isAdjacentToDoorAxis(grid, tileColumn, tileRow))) {
                        grid[tileRow][tileColumn] = prop;
                    }
                }
            }
            // Security cameras at walls
            tryPlaceAtmosphericPropNearWall(grid, room, '#');
            tryPlaceAtmosphericPropNearWall(grid, room, '#');
            // Harrowing aftermath
            if (config.bloodStains) {
                tryPlaceAtmosphericProp(grid, room, '.');
                tryPlaceAtmosphericProp(grid, room, '.');
                if (random.nextBoolean()) tryPlaceAtmosphericProp(grid, room, '.');
            }
            if (config.corpses) {
                tryPlaceAtmosphericPropNearWall(grid, room, 'm');
                if (random.nextBoolean()) tryPlaceAtmosphericPropNearWall(grid, room, 'm');
            }
        }
    }

    /**
     * Places Research Lab props: specimen tanks ('I') along one interior wall,
     * a central holo-workstation ('W'), an AI core node ('J') near a wall,
     * force-field tiles ('F') as a partial barrier, a reward pickup behind it,
     * and energy scorch decals ('e') throughout.
     */
    private void placeResearchLabProps(char[][] grid, List<Room> rooms) {
        for (Room room : rooms) {
            if (room.type != RoomType.RESEARCH_LAB) continue;

            // --- Specimen tank row along one interior wall ---
            boolean tankRowHorizontal = room.interiorWidth() >= room.interiorHeight();
            int tankTarget = LevelGenConstants.LEVEL_GEN_RESEARCH_LAB_MIN_TANKS
                           + random.nextInt(LevelGenConstants.LEVEL_GEN_RESEARCH_LAB_MAX_TANKS
                                           - LevelGenConstants.LEVEL_GEN_RESEARCH_LAB_MIN_TANKS + 1);
            int tanksPlaced = 0;
            if (tankRowHorizontal) {
                for (int tileColumn = room.leftColumn + 2;
                     tileColumn < room.rightColumn - 1 && tanksPlaced < tankTarget;
                     tileColumn++) {
                    int tileRow = room.bottomRow + 1;
                    if (!isWalkableFloor(grid, tileColumn, tileRow)) continue;
                    if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
                    grid[tileRow][tileColumn] = 'I';
                    tanksPlaced++;
                    if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_RESEARCH_LAB_CRACKED_CHANCE) {
                        int scorchRow = tileRow + 1;
                        if (isWalkableFloor(grid, tileColumn, scorchRow)) {
                            grid[scorchRow][tileColumn] = 'e';
                        }
                    }
                }
            } else {
                for (int tileRow = room.bottomRow + 2;
                     tileRow < room.topRow - 1 && tanksPlaced < tankTarget;
                     tileRow++) {
                    int tileColumn = room.leftColumn + 1;
                    if (!isWalkableFloor(grid, tileColumn, tileRow)) continue;
                    if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
                    grid[tileRow][tileColumn] = 'I';
                    tanksPlaced++;
                    if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_RESEARCH_LAB_CRACKED_CHANCE) {
                        int scorchColumn = tileColumn + 1;
                        if (isWalkableFloor(grid, scorchColumn, tileRow)) {
                            grid[tileRow][scorchColumn] = 'e';
                        }
                    }
                }
            }

            // --- Holo-workstation near room centre ---
            tryPlaceAtmosphericProp(grid, room, 'W');

            // --- AI core node against a wall ---
            tryPlaceAtmosphericPropNearWall(grid, room, 'J');

            // --- Force-field partial barrier (phase 1: decorative, reward in open room) ---
            if (room.interiorHeight() >= 4) {
                int barrierRow      = room.bottomRow + room.interiorHeight() * 2 / 3;
                int barrierStartCol = room.leftColumn + 2;
                int barrierLength   = Math.min(3, room.interiorWidth() - 2);
                for (int fColumn = barrierStartCol;
                     fColumn < barrierStartCol + barrierLength;
                     fColumn++) {
                    if (!isWalkableFloor(grid, fColumn, barrierRow)) continue;
                    if (isAdjacentToDoor(grid, fColumn, barrierRow)) continue;
                    grid[barrierRow][fColumn] = 'F';
                }
                // Scorch approaching the barrier
                int approachRow = barrierRow - 1;
                if (approachRow > room.bottomRow
                        && isWalkableFloor(grid, barrierStartCol, approachRow)) {
                    grid[approachRow][barrierStartCol] = 'e';
                }
                // Reward in open room area
                int rewardRow    = barrierRow + 1;
                int rewardColumn = barrierStartCol + 1;
                if (rewardRow < room.topRow
                        && rewardColumn < room.rightColumn
                        && isWalkableFloor(grid, rewardColumn, rewardRow)) {
                    grid[rewardRow][rewardColumn] = pickResearchLabReward();
                }
            }

            // --- Additional energy scorch decals ---
            int scorchTarget = LevelGenConstants.LEVEL_GEN_RESEARCH_LAB_SCORCH_MIN
                             + random.nextInt(LevelGenConstants.LEVEL_GEN_RESEARCH_LAB_SCORCH_MAX
                                             - LevelGenConstants.LEVEL_GEN_RESEARCH_LAB_SCORCH_MIN + 1);
            int scorchPlaced = 0;
            for (int attempt = 0; attempt < 40 && scorchPlaced < scorchTarget; attempt++) {
                int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
                int tileRow    = room.bottomRow  + 1 + random.nextInt(room.interiorHeight());
                if (isWalkableFloor(grid, tileColumn, tileRow)
                        && !isAdjacentToDoor(grid, tileColumn, tileRow)) {
                    grid[tileRow][tileColumn] = 'e';
                    scorchPlaced++;
                }
            }
        }
    }

    private char pickResearchLabReward() {
        float roll = random.nextFloat();
        if (roll < 0.35f) return 'H';   // field medkit
        if (roll < 0.65f) return 'A';   // security vest
        if (roll < 0.80f) return 'r';   // red keycard
        if (roll < 0.90f) return 'y';   // yellow keycard
        return 'a';                      // armour shard fallback
    }

    private char randomContainmentPropChar() {
        float roll = random.nextFloat();
        if (roll < 0.30f) return '&';   // bio-pod
        if (roll < 0.50f) return 'm';   // corpse
        if (roll < 0.65f) return '.';   // blood stain
        if (roll < 0.75f) return 'O';   // fluid pool
        if (roll < 0.85f) return 'C';   // crate
        return '\0';
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
            if (!Level.isDoor(grid[neighborRow][neighborColumn])) continue;
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
     * Places medkit ('H'), stim-pack ('+'), and armour-kit ('A') pickups in non-entrance rooms.
     * Per-type boosts:
     *   MEDICAL_BAY:       high medkit + stim chance (loot hub).
     *   ARMORY:            high armour chance, low medkit (last-stand gear).
     *   COMMAND_CENTER:    moderate medkit + armour (VIP resupply).
     *   SERVER_ROOM/LARGE: existing boosted chances.
     */
    private void placePickups(char[][] grid, List<Room> rooms) {
        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            Room  room         = rooms.get(roomIndex);
            float medkitChance = config.medkitChancePerRoom;
            float armourChance = config.armourChancePerRoom;
            float ammoChance   = LevelGenConstants.LEVEL_GEN_AMMO_CHANCE_PER_ROOM;

            switch (room.type) {
                case SERVER_ROOM:
                    medkitChance = LevelGenConstants.LEVEL_GEN_SERVER_MEDKIT_CHANCE;
                    armourChance = LevelGenConstants.LEVEL_GEN_SERVER_ARMOUR_CHANCE;
                    ammoChance   = 0.45f;
                    break;
                case LARGE:
                    medkitChance = LevelGenConstants.LEVEL_GEN_LARGE_MEDKIT_CHANCE;
                    armourChance = LevelGenConstants.LEVEL_GEN_LARGE_ARMOUR_CHANCE;
                    ammoChance   = 0.55f;
                    break;
                case MEDICAL_BAY:
                    tryPlacePickup(grid, room, 'H');
                    if (random.nextFloat() < 0.70f) tryPlacePickup(grid, room, '+');
                    if (random.nextFloat() < 0.30f) tryPlacePickup(grid, room, 'A');
                    if (random.nextFloat() < 0.30f) tryPlacePickup(grid, room, randomAmmoChar());
                    continue;
                case ARMORY:
                    if (random.nextFloat() < 0.80f) tryPlacePickup(grid, room, 'A');
                    if (random.nextFloat() < 0.40f) tryPlacePickup(grid, room, 'H');
                    // Armory always has ammo; often two boxes
                    tryPlacePickup(grid, room, randomAmmoChar());
                    if (random.nextBoolean()) tryPlacePickup(grid, room, randomAmmoChar());
                    continue;
                case COMMAND_CENTER:
                    medkitChance = 0.50f;
                    armourChance = 0.50f;
                    ammoChance   = 0.60f;
                    break;
                case POWER_PLANT:
                case CRYO_CHAMBER:
                case CONTAINMENT_BLOCK:
                    medkitChance = 0.25f;
                    armourChance = 0.20f;
                    ammoChance   = 0.30f;
                    break;
                default:
                    break;
            }

            if (config.medkits    && random.nextFloat() < medkitChance) tryPlacePickup(grid, room, 'H');
            if (config.armourKits && random.nextFloat() < armourChance)  tryPlacePickup(grid, room, 'A');
            if (random.nextFloat() < ammoChance) tryPlacePickup(grid, room, randomAmmoChar());
        }
    }

    private char randomAmmoChar() {
        switch (random.nextInt(5)) {
            case 0:  return '6'; // bullets
            case 1:  return '7'; // shells
            case 2:  return '8'; // cells
            case 3:  return '9'; // rockets
            default: return '0'; // slugs
        }
    }

    /**
     * Places weapon pickups across the level as WeaponSpawnPoints (no grid tile written).
     *
     * Two independent passes:
     *   1. All special rooms (LARGE, SERVER_ROOM, MEDICAL_BAY, ARMORY, CRYO_CHAMBER,
     *      POWER_PLANT, COMMAND_CENTER, CONTAINMENT_BLOCK, RESEARCH_LAB) — guaranteed
     *      placement so every special room always contains a weapon.
     *   2. STANDARD rooms — each has a LEVEL_GEN_RANDOM_ROOM_WEAPON_CHANCE independent
     *      chance, giving varied weapon distribution across ordinary rooms.
     *
     * Spawns are recorded in weaponSpawnPoints; World instantiates a GroundItem from each.
     * The grid tile itself is NOT modified — weapon ground items are entity-side only.
     */
    private void placeWeaponSpawns(char[][] grid, List<Room> rooms) {
        // Pass 1: All special rooms (non-ENTRANCE, non-STANDARD) get a guaranteed weapon.
        for (Room room : rooms) {
            if (room.type == RoomType.ENTRANCE) continue;
            if (room.type == RoomType.STANDARD) continue;
            tryPlaceWeaponSpawn(grid, room);
        }

        // Pass 2: STANDARD rooms each get a random chance.
        for (Room room : rooms) {
            if (room.type != RoomType.STANDARD) continue;
            if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_RANDOM_ROOM_WEAPON_CHANCE) {
                tryPlaceWeaponSpawn(grid, room);
            }
        }
    }

    /**
     * Attempts up to 20 times to find a walkable floor tile in the room that holds no
     * prop or pickup, then records a WeaponSpawnPoint there.
     * Returns true when a spawn was successfully placed; false when no eligible tile was found.
     */
    private boolean tryPlaceWeaponSpawn(char[][] grid, Room room) {
        for (int attempt = 0; attempt < 20; attempt++) {
            int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
            int tileRow    = room.bottomRow  + 1 + random.nextInt(room.interiorHeight());
            char cell = grid[tileRow][tileColumn];
            // Only place on plain walkable floor — not on a prop, pickup, door axis, etc.
            if (cell != ' ' && cell != 'l') continue;
            if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
            weaponSpawnPoints.add(new WeaponSpawnPoint(tileColumn, tileRow, randomWeaponItemType()));
            return true;
        }
        return false;
    }

    /** Returns one of the implemented weapon ItemTypes at equal probability (ranged and melee). */
    private ItemType randomWeaponItemType() {
        switch (random.nextInt(11)) {
            case 0:  return ItemType.WEAPON_SHOTGUN;
            case 1:  return ItemType.WEAPON_DOUBLE_BARREL;
            case 2:  return ItemType.WEAPON_CHAINGUN;
            case 3:  return ItemType.WEAPON_ASSAULT_RIFLE;
            case 4:  return ItemType.WEAPON_PLASMA;
            case 5:  return ItemType.WEAPON_INCINERATOR;
            case 6:  return ItemType.WEAPON_RAILGUN;
            case 7:  return ItemType.WEAPON_ROCKET;
            case 8:  return ItemType.WEAPON_KNIFE;
            case 9:  return ItemType.WEAPON_HAMMER;
            default: return ItemType.WEAPON_CHAINSAW;
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

    private void placeRustWallsNearUnlit(char[][] grid) {
        int[] deltaColumns = { 0,  0,  1, -1 };
        int[] deltaRows    = { 1, -1,  0,  0 };
        for (int tileRow = 0; tileRow < LevelGenConstants.LEVEL_GEN_GRID_HEIGHT; tileRow++) {
            for (int tileColumn = 0; tileColumn < LevelGenConstants.LEVEL_GEN_GRID_WIDTH; tileColumn++) {
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
                if (nearUnlit && random.nextFloat() < LevelGenConstants.LEVEL_GEN_RUST_WALL_CHANCE) {
                    grid[tileRow][tileColumn] = 'j';
                } else if (nearDecal && random.nextFloat() < LevelGenConstants.LEVEL_GEN_RUST_OIL_CHANCE) {
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

    private void placeBulkheadWallsAtDeadEnds(char[][] grid) {
        int[] deltaColumns = { 0,  0,  1, -1 };
        int[] deltaRows    = { 1, -1,  0,  0 };
        for (int tileRow = 0; tileRow < LevelGenConstants.LEVEL_GEN_GRID_HEIGHT; tileRow++) {
            for (int tileColumn = 0; tileColumn < LevelGenConstants.LEVEL_GEN_GRID_WIDTH; tileColumn++) {
                char cell        = grid[tileRow][tileColumn];
                boolean isDeadEnd = false;
                boolean isStairs  = (cell == RenderConstants.STAIRS_DOWN_CHAR);
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
        int enemyCount = Math.min(LevelGenConstants.LEVEL_GEN_MAX_ENEMIES_PER_ROOM,
                                  1 + random.nextInt(Math.max(1, area / 6)));
        int[] placedColumns = new int[enemyCount];
        int[] placedRows    = new int[enemyCount];
        int   placed        = 0;
        int   attempts      = 0;
        while (placed < enemyCount && attempts < 50) {
            attempts++;
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
            char spawnChar = randomEnemySpawnChar();
            spawnPoints.add(new EnemySpawnPoint(spawnChar, tileColumn, tileRow));
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
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
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
     * Stamps exactly one stairs-down tile. Preference order (furthest first within each tier):
     *   1. COMMAND_CENTER — cinematic payoff for conquering the control room.
     *   2. POWER_PLANT    — reactor shutdown = level complete.
     *   3. LARGE          — landmark scale suits an exit landmark.
     *   4. Any non-entrance room.
     *   5. Entrance room  — absolute last resort.
     */
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
            Room room = rooms.get(roomIndex);
            if (room.type == RoomType.LARGE && tryStampInRoom(grid, room)) return;
        }
        for (int roomIndex = rooms.size() - 1; roomIndex >= 1; roomIndex--) {
            if (tryStampInRoom(grid, rooms.get(roomIndex))) return;
        }
        tryStampInRoom(grid, rooms.get(0));
    }

    private boolean tryStampInRoom(char[][] grid, Room room) {
        int centerColumn = room.centerColumn();
        int centerRow    = room.centerRow();
        if (isWalkableFloor(grid, centerColumn, centerRow)) {
            grid[centerRow][centerColumn] = RenderConstants.STAIRS_DOWN_CHAR;
            lightSurroundingFloor(grid, centerColumn, centerRow);
            return true;
        }
        for (int attempt = 0; attempt < 12; attempt++) {
            if (room.interiorWidth() <= 0 || room.interiorHeight() <= 0) break;
            int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
            int tileRow    = room.bottomRow  + 1 + random.nextInt(room.interiorHeight());
            if (isWalkableFloor(grid, tileColumn, tileRow)) {
                grid[tileRow][tileColumn] = RenderConstants.STAIRS_DOWN_CHAR;
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

    /**
     * Wider eligibility check for enemy spawn positions.
     * Accepts the four floor tile types plus walkable decal props (blood stains, oil pools,
     * and corpses) — all of which are passable for both player and enemy during movement.
     * Excludes keycards, medical/armour pickups, and stairs to avoid destroying them on
     * enemy death (killEnemy stamps 'm' at the enemy's last tile).
     */
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

    private int manhattanDistance(Room roomA, Room roomB) {
        return Math.abs(roomA.centerColumn() - roomB.centerColumn())
             + Math.abs(roomA.centerRow()    - roomB.centerRow());
    }

    private int randomBetween(int minInclusive, int maxInclusive) {
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }

    private void fillAll(char[][] grid, char fillChar) {
        for (int tileRow = 0; tileRow < LevelGenConstants.LEVEL_GEN_GRID_HEIGHT; tileRow++) {
            for (int tileColumn = 0; tileColumn < LevelGenConstants.LEVEL_GEN_GRID_WIDTH; tileColumn++) {
                grid[tileRow][tileColumn] = fillChar;
            }
        }
    }

    private Level buildFallbackLevel() {
        char[][] grid = new char[LevelGenConstants.LEVEL_GEN_GRID_HEIGHT][LevelGenConstants.LEVEL_GEN_GRID_WIDTH];
        fillAll(grid, 'x');
        for (int tileRow = 20; tileRow <= 24; tileRow++) {
            for (int tileColumn = 36; tileColumn <= 43; tileColumn++) {
                grid[tileRow][tileColumn] = ' ';
            }
        }
        grid[22][40] = 'p';
        return new Level(grid, new ArrayList<>(), new ArrayList<>());
    }
}
