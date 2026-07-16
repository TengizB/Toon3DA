package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.LevelGenConstants;
import ge.tbegvadze.toon3d.util.RenderConstants;

import java.util.ArrayDeque;
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
 * Phase 4 — Enemy Placement:    enemy spawns in non-entrance rooms, after props; count and
 *                                archetype toughness scale with the room's depth from spawn.
 * Phase 5 — Connectivity Audit: BFS flood-fill from player spawn; emergency corridors for
 *                                any unreachable room.
 * Phase 5b— Lock-and-Key Gate:  one bridge door is promoted to a keycard-locked door, the
 *                                matching keycard is placed in a still-reachable room, and a
 *                                bonus reward is dropped behind the gate (reuses the existing
 *                                DoorManager keycard system; no new tile symbols).
 * Phase 6 — Stairs:             exactly one exit in the spatially deepest room — inside the
 *                                gated region when a gate exists — so the exit is the payoff
 *                                at the far end of the player's journey.
 *
 * Depth gradient: every room's depth is its hop distance from the entrance over the MST room
 * tree. Position now carries meaning — deeper rooms are harder and richer, and the exit sits
 * furthest in.
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

    // TILESET MIGRATION (order-5): every RoomType now also exists as a registered RoomBlueprint
    // (see RoomBlueprints + RoomBlueprintRegistry). The enum survives as an INTERNAL TAG during the
    // refactor: placeRooms()/assignRoomTypes() still select with it, and the per-room architecture
    // helpers are keyed off it. order-8 flips generate() to iterate the registry and calls
    // RoomBlueprint.build() instead of these passes; the enum is removed then.
    // Package-private so the level-package RoomBuildContext + blueprint tests can tag a Room's type.
    // See docs/environment-tileset-system.txt (ORDER STATUS TABLE, section 5).
    enum RoomType {
        ENTRANCE, STANDARD, LARGE, SERVER_ROOM,
        MEDICAL_BAY, ARMORY, CRYO_CHAMBER,
        POWER_PLANT, COMMAND_CENTER, CONTAINMENT_BLOCK,
        RESEARCH_LAB, STELLAR_OBSERVATORY
    }

    private final Random         random;
    private final LevelGenConfig config;

    // Dungeon floor this generator is building for (1-based). Drives the encounter Threat-Point
    // budget (balance idea 4). Defaults to 1; set via generate(int dungeonDepth).
    private int dungeonDepth = 1;

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

    @Override
    public Level generate(int dungeonDepth) {
        this.dungeonDepth = Math.max(1, dungeonDepth);
        return generate();
    }

    @Override
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

        // Spatial depth gradient: hop distance from the entrance over the MST room tree.
        // Drives depth-aware enemy/loot scaling (phase 3-4) and deepest-room stairs (phase 6).
        int[] roomDepths   = computeRoomDepths(rooms);
        int   maxRoomDepth = maxValue(roomDepths);

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
        placeStellarObservatoryProps(grid, rooms);
        placePickups(grid, rooms, roomDepths, maxRoomDepth);
        placeWeaponSpawns(grid, rooms);

        // Phase 4 — enemies: spend the floor's encounter Threat-Point budget (balance idea 4,
        // Pillar 1) instead of rolling enemies room-by-room at random.
        List<EnemySpawnPoint> spawnPoints = new ArrayList<>();
        placeBudgetedEncounter(grid, rooms, roomDepths, spawnPoints);

        // Phase 4b — atmospheric wall theming (post-pass after enemies so corpse/den density is final)
        placeRustWallsNearUnlit(grid);
        placeGoreWallsNearCorpses(grid);
        placeBulkheadWallsAtDeadEnds(grid);

        // Phase 5 — connectivity audit
        verifyAndRepairConnectivity(grid, rooms);

        // Phase 5b — lock-and-key gating (after connectivity so the gate is a true cut)
        boolean[] gatedRooms = null;
        if (config.enableLockAndKey) {
            gatedRooms = placeLockAndKeyGate(grid, rooms, roomDepths, spawnPoints);
        }

        // Phase 6 — stamp exactly one stairs-down exit in the deepest room (behind the gate if one exists)
        stampStairsDown(grid, rooms, roomDepths, gatedRooms);

        return new Level(grid, spawnPoints, weaponSpawnPoints);
    }

    // -------------------------------------------------------------------------
    // Room data structure
    // -------------------------------------------------------------------------

    // Package-private (order-5): the level-package RoomBuildContext + blueprint tests construct and tag
    // a Room when driving RoomBlueprint.build() outside the generator's own passes.
    static final class Room {
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

        // Step D — Stellar Observatory: RARE landmark rotunda (true-circle carve; decorated
        // separately by decorateStellarObservatory(), called from placeStellarObservatoryProps()
        // in phase 3). Depth-gated, LOW roll weight, and this block only ever runs once per
        // generate() call, so it is a hard cap of 1 per level. See the design doc:
        // .claude/agents/ideas/stellar-observatory-gravity-well-room.txt ("PLACEMENT RULES").
        if (dungeonDepth >= LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_MIN_DEPTH
                && random.nextFloat() < LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_CHANCE) {
            Room stellarObservatoryCandidate = findStellarObservatoryCandidate(rooms);
            if (stellarObservatoryCandidate != null) {
                stellarObservatoryCandidate.type = RoomType.STELLAR_OBSERVATORY;
            }
        }
    }

    /**
     * Finds the best candidate for a Stellar Observatory: a STANDARD room whose interior is
     * large enough and near-square enough to inscribe the rotunda circle, and spaced away from
     * the Research Lab landmark (tonal-opposite spacing rule from the design doc's PLACEMENT
     * RULES — the doc's other spacing partners, boss arena / shop-safe-room / EXCAVATION_SITE,
     * are route-level or sibling-doc concepts that don't exist as a RoomType in this generator).
     * Returns null if no candidate qualifies — the caller must never force the room type onto
     * an ineligible slot; STELLAR_OBSERVATORY simply doesn't appear this level.
     *
     * Size-gate note: the design doc's declared target radius is R = 6..8 (from 15x15/17x17
     * interiors), but LEVEL_GEN_ROOM_MAX_HEIGHT caps every room's interior height at 14 tiles,
     * so a 15x15+ interior can never be produced by placeRooms(). Rather than raising that
     * shared cap (which would perturb every other room type's size balance), this gate accepts
     * the biggest near-square footprint the existing cap allows (13x13..14x14); combined with
     * the carve formula in decorateStellarObservatory() that works out to R = 5..6 — still a
     * genuine multi-tile rotunda, just more compact than the doc's standalone target.
     */
    private Room findStellarObservatoryCandidate(List<Room> rooms) {
        Room researchLabRoom = null;
        for (Room existing : rooms) {
            if (existing.type == RoomType.RESEARCH_LAB) {
                researchLabRoom = existing;
                break;
            }
        }

        List<Room> eligible = new ArrayList<>();
        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            Room candidate = rooms.get(roomIndex);
            if (candidate.type != RoomType.STANDARD) continue;

            int interiorWidth  = candidate.interiorWidth();
            int interiorHeight = candidate.interiorHeight();
            if (interiorWidth  < LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_MIN_INTERIOR
                    || interiorHeight < LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_MIN_INTERIOR) continue;

            int largerDimension  = Math.max(interiorWidth, interiorHeight);
            int smallerDimension = Math.min(interiorWidth, interiorHeight);
            if (largerDimension > smallerDimension * LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_MAX_ASPECT) continue;

            if (researchLabRoom != null
                    && manhattanDistance(candidate, researchLabRoom)
                        < LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_LANDMARK_SPACING) continue;

            eligible.add(candidate);
        }
        if (eligible.isEmpty()) return null;
        return eligible.get(random.nextInt(eligible.size()));
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
    void assignFloorLighting(char[][] grid, List<Room> rooms) {
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
            themeServerRoomWallsForRoom(grid, room);
        }
    }

    /**
     * SERVER_ROOM perimeter wall theming for ONE room (terminal walls 't'). Extracted verbatim from
     * {@link #themeServerRoomWalls} so both the generate() pass and {@code RoomBuildContext} (the
     * order-5 blueprint build path) stamp identical walls with the identical RNG draw sequence.
     */
    void themeServerRoomWallsForRoom(char[][] grid, Room room) {
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
            themeNewRoomWallsForRoom(grid, room);
        }
    }

    /**
     * Room-type-specific perimeter wall theming for ONE room. Extracted verbatim from
     * {@link #themeNewRoomWalls} so both the generate() pass and {@code RoomBuildContext} (the order-5
     * blueprint build path) stamp identical walls with the identical RNG draw sequence. The internal
     * {@code switch} on {@link RoomType} is the order-5 "enum as internal tag" — order-8 replaces it
     * with per-blueprint wall logic. Every qualifying perimeter tile consumes exactly one
     * {@code random.nextFloat()} for EVERY room type (the {@code default} branch rolls but no-ops),
     * which is load-bearing for determinism; do not skip the roll for unthemed types.
     */
    void themeNewRoomWallsForRoom(char[][] grid, Room room) {
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
            placeStandardColumnsForRoom(grid, room);
        }
    }

    /**
     * STANDARD room column placement for ONE room. Extracted verbatim from {@link #placeColumns} so
     * both the generate() pass and {@code RoomBuildContext} (order-5 blueprint build path) place
     * identical columns with the identical RNG draw sequence. Honours {@code config.columns} via the
     * caller; the size/chance gates below are unchanged.
     */
    void placeStandardColumnsForRoom(char[][] grid, Room room) {
        if (!config.columns) return;
        if (room.interiorWidth()  < config.columnMinRoomSize) return;
        if (room.interiorHeight() < config.columnMinRoomSize) return;
        if (random.nextFloat() > config.columnChancePerRoom)  return;

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

    /**
     * Places columns in LARGE rooms using one of three deliberate architectural patterns:
     *   SYMMETRIC_FOUR  — columns at the four interior quadrant centres (classic pillared hall).
     *   CENTRE_AVENUE   — row of columns down the long axis, spaced 3 tiles.
     *   PERIMETER_RING  — columns at mid-edges just inside the walls (open arena centre).
     */
    void placeLargeRoomColumns(char[][] grid, List<Room> rooms) {
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
            placeStandardPropsForRoom(grid, room);
        }
    }

    /**
     * STANDARD room generic prop scatter for ONE room. Extracted verbatim from {@link #placeProps} so
     * both the generate() pass and {@code RoomBuildContext} (order-5 blueprint build path) stamp
     * identical props with the identical RNG draw sequence.
     */
    void placeStandardPropsForRoom(char[][] grid, Room room) {
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

    /**
     * Places props in SERVER_ROOM rooms as rack rows of terminals ('T') and lockers ('L'),
     * reading as a UAC data vault — dense, claustrophobic, tactically dangerous.
     */
    void placeServerRoomProps(char[][] grid, List<Room> rooms) {
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
    void placeLargeRoomProps(char[][] grid, List<Room> rooms) {
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
    void placeMedicalBayProps(char[][] grid, List<Room> rooms) {
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
    void placeArmoryProps(char[][] grid, List<Room> rooms) {
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
    void placeCryoChamberProps(char[][] grid, List<Room> rooms) {
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
    void placePowerPlantProps(char[][] grid, List<Room> rooms) {
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
    void placeCommandCenterProps(char[][] grid, List<Room> rooms) {
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
    void placeContainmentBlockProps(char[][] grid, List<Room> rooms) {
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
    void placeResearchLabProps(char[][] grid, List<Room> rooms) {
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

    // -------------------------------------------------------------------------
    // STELLAR_OBSERVATORY — true-circle rotunda landmark room.
    // See the design doc for the full spec and step numbering referenced below:
    // .claude/agents/ideas/stellar-observatory-gravity-well-room.txt
    // -------------------------------------------------------------------------

    void placeStellarObservatoryProps(char[][] grid, List<Room> rooms) {
        for (Room room : rooms) {
            if (room.type != RoomType.STELLAR_OBSERVATORY) continue;
            decorateStellarObservatory(grid, room);
        }
    }

    /**
     * Carves and decorates a STELLAR_OBSERVATORY room: a true-circle rotunda rasterized with
     * GameMath.classifyRotundaTile(), a raised catwalk ring, a centred gravity-well core, four
     * radial spokes, boundary shell variety, scattered props/decals, sparse dread lighting, and
     * boundary door placement via GameMath.angularDifferenceRadians() argmin bearing selection.
     *
     * Architectural note (adaptation from the design doc's literal step 2): Phase 1's
     * carveRoomInteriors() already flooded this room's whole rectangular interior to floor
     * before this method runs — every room type is carved the same generic way, then decorated
     * in place (see the class-level phase doc-comment). That means GameMath.RotundaTileClass
     * .OUTSIDE tiles are NOT "untouched wall" here the way the enum's javadoc frames it; they
     * are already floor. Leaving them as floor would carve an unintended walkable dead-space
     * ring/pocket outside the intended circle (the design doc's own ASCII diagram shows the
     * corners solid, not open), so this method explicitly seals every OUTSIDE tile to a wall
     * too — both SHELL and OUTSIDE become solid, differing only in which gets shell-variety
     * texturing. That, in turn, makes the design doc's separate "leak-seal pass" unnecessary:
     * since the entire non-FLOOR region is solid, there is no thin ring for a diagonal (or, at
     * these small radii, even orthogonal — the classify band's "+0.75" margin does not always
     * cover the single-step jump at an exact cardinal offset) rasterization gap to slip through.
     */
    private void decorateStellarObservatory(char[][] grid, Room room) {
        int interiorLeft   = room.leftColumn + 1;
        int interiorRight  = room.rightColumn - 1;
        int interiorBottom = room.bottomRow + 1;
        int interiorTop    = room.topRow - 1;

        // Use the same centroid the rest of the generator already targets this room by
        // (corridor connection, connectivity audit) so every subsystem agrees on one tile,
        // regardless of interior-dimension parity.
        int centerColumn = room.centerColumn();
        int centerRow    = room.centerRow();

        int rawRadius = Math.min(room.interiorWidth(), room.interiorHeight()) / 2
                      - LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_RADIUS_MARGIN;
        float radius = Math.max(LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_MIN_RADIUS,
                        Math.min(LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_MAX_RADIUS, rawRadius));
        int radiusInt = Math.round(radius);

        // --- Step 2 (+ sealed OUTSIDE, see method doc): carve the circle ---
        List<int[]> shellRingTiles = new ArrayList<>();
        for (int tileRow = interiorBottom; tileRow <= interiorTop; tileRow++) {
            for (int tileColumn = interiorLeft; tileColumn <= interiorRight; tileColumn++) {
                int differenceColumn = tileColumn - centerColumn;
                int differenceRow    = tileRow    - centerRow;
                GameMath.RotundaTileClass tileClass =
                        GameMath.classifyRotundaTile(differenceColumn, differenceRow, radius);
                if (tileClass == GameMath.RotundaTileClass.FLOOR) {
                    grid[tileRow][tileColumn] = ' ';
                } else {
                    grid[tileRow][tileColumn] = '`'; // hull plate — default shell/seal wall
                    if (tileClass == GameMath.RotundaTileClass.SHELL) {
                        shellRingTiles.add(new int[]{ tileColumn, tileRow });
                    }
                }
            }
        }

        // --- Corridor breach detection (feeds step 3 hero-arc bearing + step 10 doors) ---
        // A breach is any non-wall tile Phase 2 already carved into this room's TRUE rectangle
        // perimeter (assignWallVariety() only recolors 'x' tiles, so a corridor entry stays
        // identifiable as 'l'/'d' among the perimeter's themed wall chars).
        List<int[]> breachPoints = findPerimeterBreaches(grid, room);
        if (shellRingTiles.isEmpty()) return; // degenerate slot; nothing further to decorate

        int[] primaryBreach = breachPoints.isEmpty()
                ? new int[]{ centerColumn + radiusInt, centerRow }
                : breachPoints.get(0);
        float primaryBearing = (float) Math.atan2(primaryBreach[1] - centerRow, primaryBreach[0] - centerColumn);

        // --- Step 3: shell variety (viewport dome opposite the entrance, magrail side arcs) ---
        assignStellarShellVariety(grid, centerColumn, centerRow, shellRingTiles, breachPoints, primaryBearing);

        // --- Step 10: boundary door placement (argmin bearing) + connector lane through the
        // sealed margin between the true rectangle wall and the shell ring ---
        for (int[] breach : breachPoints) {
            carveStellarObservatoryEntrance(grid, breach, centerColumn, centerRow, shellRingTiles);
        }

        // --- Step 4: catwalk ring ---
        int catwalkRadiusInt = Math.round(radius * LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_CATWALK_RATIO);
        float catwalkRadius = catwalkRadiusInt;
        List<int[]> catwalkTiles = new ArrayList<>();
        for (int tileRow = interiorBottom; tileRow <= interiorTop; tileRow++) {
            for (int tileColumn = interiorLeft; tileColumn <= interiorRight; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                int differenceColumn = tileColumn - centerColumn;
                int differenceRow    = tileRow    - centerRow;
                if (GameMath.isOnCatwalkAnnulus(differenceColumn, differenceRow, catwalkRadius,
                        LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_CATWALK_BAND_HALF_WIDTH)) {
                    grid[tileRow][tileColumn] = '?';
                    catwalkTiles.add(new int[]{ tileColumn, tileRow });
                }
            }
        }

        // --- Step 5: core placement (always wins any conflict — placed after the ring) ---
        grid[centerRow][centerColumn] = ';';

        // --- Step 6: radial spokes (cardinal directions, ring to one tile shy of the shell) ---
        int[][] spokeDirections = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        assert spokeDirections.length == LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_SPOKE_COUNT;
        for (int[] direction : spokeDirections) {
            for (int distance = catwalkRadiusInt + 1; distance <= radiusInt - 1; distance++) {
                int tileColumn = centerColumn + direction[0] * distance;
                int tileRow    = centerRow    + direction[1] * distance;
                if (!isInBounds(tileColumn, tileRow)) continue;
                if (grid[tileRow][tileColumn] != ' ') continue;
                grid[tileRow][tileColumn] = '-';
            }
        }

        // --- Step 7: prop scatter (outer annulus only; solid-prop budget <= 6 incl. the core) ---
        int pylonTarget = randomBetween(LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_PYLON_MIN,
                                        LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_PYLON_MAX);
        boolean[] quadrantHasPylon = new boolean[4];
        for (int placed = 0; placed < pylonTarget; placed++) {
            placeStellarOuterAnnulusProp(grid, centerColumn, centerRow, radius, catwalkRadius, radiusInt,
                    '|', quadrantHasPylon);
        }
        if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_HOLOTABLE_CHANCE) {
            placeStellarOuterAnnulusProp(grid, centerColumn, centerRow, radius, catwalkRadius, radiusInt,
                    '<', null);
        }
        int crateTarget = randomBetween(LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_CRATE_MIN,
                                        LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_CRATE_MAX);
        for (int placed = 0; placed < crateTarget; placed++) {
            placeStellarOuterAnnulusProp(grid, centerColumn, centerRow, radius, catwalkRadius, radiusInt,
                    '\\', null);
        }

        // --- Step 8: decal pass (zero-g debris; magnetic deck plating is already the ring) ---
        int debrisTarget = randomBetween(LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_DEBRIS_MIN,
                                         LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_DEBRIS_MAX);
        for (int placed = 0; placed < debrisTarget; placed++) {
            placeStellarOuterAnnulusProp(grid, centerColumn, centerRow, radius, catwalkRadius, radiusInt,
                    ']', null);
        }

        // --- Step 9: sparse dread lighting (0-2 unlit tiles, far quadrant only, no flicker) ---
        int unlitTarget = random.nextInt(LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_UNLIT_MAX + 1);
        for (int placed = 0; placed < unlitTarget; placed++) {
            placeStellarFarQuadrantUnlitTile(grid, centerColumn, centerRow, radius, catwalkRadius, radiusInt,
                    primaryBearing);
        }

        // --- Step 11: navigability check (bounded local flood-fill) ---
        if (!isStellarObservatoryFullyNavigable(grid, room, centerColumn, centerRow, catwalkTiles)) {
            // Simplification of the design doc's "remove the offending prop and retry" loop:
            // clear every non-core solid prop this method placed and accept the now-guaranteed
            // open result. These props are single, sparse tiles in an already very open outer
            // annulus, so a hard block is a rare edge case; a full strip is a small, always-
            // correct fix rather than a much larger surgical targeted-removal retry loop.
            clearStellarObservatorySolidProps(grid, interiorLeft, interiorRight, interiorBottom, interiorTop);
        }

        // Step 12 (enemy spawns) is intentionally not implemented here: this generator's only
        // enemy placement is the generic budgeted-encounter system (placeBudgetedEncounter(),
        // phase 4 — see EncounterBudgetPlanner), which runs after ALL room decoration and picks
        // uniformly-random eligible tiles per room; no existing RoomType decorate method places
        // bespoke archetypes/positions either. isEnemySpawnEligible() was widened to accept
        // '?'/'-'/']' so the ring and spokes are viable spawn tiles, and the ranged "spoke
        // sniper" gauntlet this room is built around still emerges naturally: any ranged enemy
        // the budgeted system happens to drop on a spoke or the ring gets a clean shot at a
        // player crossing the open centre, via the existing isSameCardinalLine() rule.
    }

    /**
     * Scans a room's TRUE rectangle perimeter for tiles Phase 2's corridor carving already
     * turned into floor/door ('l'/'d') — each one is a corridor connection point into this room.
     */
    private List<int[]> findPerimeterBreaches(char[][] grid, Room room) {
        List<int[]> breaches = new ArrayList<>();
        for (int tileColumn = room.leftColumn; tileColumn <= room.rightColumn; tileColumn++) {
            addStellarBreachIfPresent(grid, breaches, tileColumn, room.bottomRow);
            addStellarBreachIfPresent(grid, breaches, tileColumn, room.topRow);
        }
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            addStellarBreachIfPresent(grid, breaches, room.leftColumn,  tileRow);
            addStellarBreachIfPresent(grid, breaches, room.rightColumn, tileRow);
        }
        return breaches;
    }

    private void addStellarBreachIfPresent(char[][] grid, List<int[]> breaches, int tileColumn, int tileRow) {
        if (!isInBounds(tileColumn, tileRow)) return;
        if (!Level.isWall(grid[tileRow][tileColumn])) {
            breaches.add(new int[]{ tileColumn, tileRow });
        }
    }

    /**
     * Step 3: reskins the SHELL ring by arc position — a viewport-dome hero arc opposite the
     * primary entrance, two magrail-conduit side arcs, hull plate everywhere else. Ring tiles
     * within DOOR_KEEPOUT_RADIANS of ANY entrance bearing are left as hull plate so every
     * door's immediate neighbours stay a clean opening (never converted away from it).
     */
    private void assignStellarShellVariety(char[][] grid, int centerColumn, int centerRow,
                                           List<int[]> shellRingTiles, List<int[]> breachPoints,
                                           float primaryBearing) {
        float heroBearing     = primaryBearing + (float) Math.PI;
        float magrailBearingA = primaryBearing + (float) Math.PI / 2f;
        float magrailBearingB = primaryBearing - (float) Math.PI / 2f;

        for (int[] shellTile : shellRingTiles) {
            int tileColumn = shellTile[0];
            int tileRow    = shellTile[1];
            float bearing = (float) Math.atan2(tileRow - centerRow, tileColumn - centerColumn);

            boolean nearAnyBreach = false;
            for (int[] breach : breachPoints) {
                float breachBearing = (float) Math.atan2(breach[1] - centerRow, breach[0] - centerColumn);
                if (GameMath.angularDifferenceRadians(bearing, breachBearing)
                        < LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_DOOR_KEEPOUT_RADIANS) {
                    nearAnyBreach = true;
                    break;
                }
            }
            if (nearAnyBreach) continue;

            if (GameMath.angularDifferenceRadians(bearing, heroBearing)
                    <= LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_HERO_ARC_HALF_WIDTH_RADIANS) {
                grid[tileRow][tileColumn] = '"'; // viewport dome
            } else if (GameMath.angularDifferenceRadians(bearing, magrailBearingA)
                        <= LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_MAGRAIL_ARC_HALF_WIDTH_RADIANS
                    || GameMath.angularDifferenceRadians(bearing, magrailBearingB)
                        <= LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_MAGRAIL_ARC_HALF_WIDTH_RADIANS) {
                grid[tileRow][tileColumn] = '\''; // magrail conduit
            }
            // else: stays hull plate '`' (already the default from the carve pass)
        }
    }

    /**
     * Step 10: picks the SHELL ring tile whose bearing-from-centre is nearest the given
     * corridor breach's bearing (argmin over GameMath.angularDifferenceRadians()), converts it
     * to a door, then carves a straight L-shaped connector lane from the breach (on the room's
     * TRUE rectangle wall) to that door tile — reopening the sealed OUTSIDE margin this method's
     * circle carve walled off, so the corridor Phase 2 already dug actually reaches the rotunda.
     */
    private void carveStellarObservatoryEntrance(char[][] grid, int[] breach, int centerColumn, int centerRow,
                                                  List<int[]> shellRingTiles) {
        float breachBearing = (float) Math.atan2(breach[1] - centerRow, breach[0] - centerColumn);

        int[] doorTile = null;
        float bestDifference = Float.MAX_VALUE;
        for (int[] shellTile : shellRingTiles) {
            float bearing = (float) Math.atan2(shellTile[1] - centerRow, shellTile[0] - centerColumn);
            float difference = GameMath.angularDifferenceRadians(bearing, breachBearing);
            if (difference < bestDifference) {
                bestDifference = difference;
                doorTile = shellTile;
            }
        }
        if (doorTile == null) return;

        grid[doorTile[1]][doorTile[0]] = 'd';

        int laneColumn = breach[0];
        int laneRow    = breach[1];
        while (laneColumn != doorTile[0]) {
            laneColumn += Integer.signum(doorTile[0] - laneColumn);
            if (grid[laneRow][laneColumn] != 'd') grid[laneRow][laneColumn] = ' ';
        }
        while (laneRow != doorTile[1]) {
            laneRow += Integer.signum(doorTile[1] - laneRow);
            if (grid[laneRow][laneColumn] != 'd') grid[laneRow][laneColumn] = ' ';
        }
    }

    /**
     * Random-samples a tile in the outer annulus (strictly between the catwalk ring and the
     * shell, with PROP_CLEARANCE tiles of breathing room on each side) that is still plain open
     * floor (' ') — i.e. not the ring, a spoke, a door/lane, or another prop — and stamps
     * propChar there. When quadrantOccupied is non-null, enforces at most one placement per
     * quadrant (used for docking-strut pylons per the design doc's step 7). Best-effort: silently
     * places nothing if no valid tile is found within the attempt budget (the outer annulus is
     * narrow at this room's achievable radius — see the LevelGenConstants size-gate note).
     */
    private void placeStellarOuterAnnulusProp(char[][] grid, int centerColumn, int centerRow,
                                              float radius, float catwalkRadius, int radiusInt,
                                              char propChar, boolean[] quadrantOccupied) {
        float innerBound = catwalkRadius + LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_PROP_CLEARANCE;
        float outerBound = radius - LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_PROP_CLEARANCE;
        for (int attempt = 0; attempt < LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_PROP_PLACEMENT_ATTEMPTS; attempt++) {
            int offsetColumn = randomBetween(-radiusInt, radiusInt);
            int offsetRow    = randomBetween(-radiusInt, radiusInt);
            int tileColumn = centerColumn + offsetColumn;
            int tileRow    = centerRow    + offsetRow;
            if (!isInBounds(tileColumn, tileRow)) continue;
            if (grid[tileRow][tileColumn] != ' ') continue;

            double distance = Math.sqrt((double) (offsetColumn * offsetColumn + offsetRow * offsetRow));
            if (distance <= innerBound || distance >= outerBound) continue;

            if (quadrantOccupied != null) {
                int quadrant = (offsetColumn >= 0 ? 0 : 1) | (offsetRow >= 0 ? 0 : 2);
                if (quadrantOccupied[quadrant]) continue;
                quadrantOccupied[quadrant] = true;
            }

            grid[tileRow][tileColumn] = propChar;
            return;
        }
    }

    /**
     * Step 9: places a single unlit ('u') tile in the outer annulus, biased to the quadrant
     * angularly farthest from the entrance bearing (design doc: "far quadrant only").
     */
    private void placeStellarFarQuadrantUnlitTile(char[][] grid, int centerColumn, int centerRow,
                                                   float radius, float catwalkRadius, int radiusInt,
                                                   float entranceBearing) {
        float innerBound = catwalkRadius + LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_PROP_CLEARANCE;
        float outerBound = radius - LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_PROP_CLEARANCE;
        for (int attempt = 0; attempt < LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_PROP_PLACEMENT_ATTEMPTS; attempt++) {
            int offsetColumn = randomBetween(-radiusInt, radiusInt);
            int offsetRow    = randomBetween(-radiusInt, radiusInt);
            int tileColumn = centerColumn + offsetColumn;
            int tileRow    = centerRow    + offsetRow;
            if (!isInBounds(tileColumn, tileRow)) continue;
            if (grid[tileRow][tileColumn] != ' ') continue;

            double distance = Math.sqrt((double) (offsetColumn * offsetColumn + offsetRow * offsetRow));
            if (distance <= innerBound || distance >= outerBound) continue;

            float bearing = (float) Math.atan2(offsetRow, offsetColumn);
            if (GameMath.angularDifferenceRadians(bearing, entranceBearing)
                    < LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_FAR_QUADRANT_RADIANS) continue;

            grid[tileRow][tileColumn] = 'u';
            return;
        }
    }

    /**
     * Step 11: bounded local flood-fill from a door tile over walkable cells within the room's
     * bounding box, confirming every catwalk-ring tile and at least one core-adjacent inner-disc
     * tile are reachable. Mirrors verifyAndRepairConnectivity()'s BFS-passability rules.
     */
    private boolean isStellarObservatoryFullyNavigable(char[][] grid, Room room, int centerColumn, int centerRow,
                                                        List<int[]> catwalkTiles) {
        int startColumn = -1;
        int startRow    = -1;
        outer:
        for (int tileRow = room.bottomRow; tileRow <= room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn; tileColumn <= room.rightColumn; tileColumn++) {
                if (Level.isDoor(grid[tileRow][tileColumn])) {
                    startColumn = tileColumn;
                    startRow    = tileRow;
                    break outer;
                }
            }
        }
        if (startColumn < 0) return true; // no door found; defensively treat as nothing to validate

        boolean[][] visited = new boolean[LevelGenConstants.LEVEL_GEN_GRID_HEIGHT][LevelGenConstants.LEVEL_GEN_GRID_WIDTH];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        visited[startRow][startColumn] = true;
        queue.add(new int[]{ startColumn, startRow });
        int[] deltaColumns = { 0, 0, 1, -1 };
        int[] deltaRows    = { 1, -1, 0, 0 };
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            for (int direction = 0; direction < 4; direction++) {
                int neighborColumn = current[0] + deltaColumns[direction];
                int neighborRow    = current[1] + deltaRows[direction];
                if (!isInBounds(neighborColumn, neighborRow)) continue;
                if (visited[neighborRow][neighborColumn]) continue;
                char cell = grid[neighborRow][neighborColumn];
                boolean passable = cell == ' ' || cell == 'u' || cell == 'l' || cell == 'f'
                                 || Level.isPropDecal(cell) || Level.isDoor(cell);
                if (!passable) continue;
                visited[neighborRow][neighborColumn] = true;
                queue.add(new int[]{ neighborColumn, neighborRow });
            }
        }

        for (int[] catwalkTile : catwalkTiles) {
            if (!visited[catwalkTile[1]][catwalkTile[0]]) return false;
        }
        int[] deltaColumnsCore = { 0, 0, 1, -1 };
        int[] deltaRowsCore    = { 1, -1, 0, 0 };
        for (int direction = 0; direction < 4; direction++) {
            int neighborColumn = centerColumn + deltaColumnsCore[direction];
            int neighborRow    = centerRow    + deltaRowsCore[direction];
            if (isInBounds(neighborColumn, neighborRow) && visited[neighborRow][neighborColumn]) return true;
        }
        return false;
    }

    private void clearStellarObservatorySolidProps(char[][] grid, int interiorLeft, int interiorRight,
                                                    int interiorBottom, int interiorTop) {
        for (int tileRow = interiorBottom; tileRow <= interiorTop; tileRow++) {
            for (int tileColumn = interiorLeft; tileColumn <= interiorRight; tileColumn++) {
                char cell = grid[tileRow][tileColumn];
                if (cell == '|' || cell == '\\' || cell == '<') {
                    grid[tileRow][tileColumn] = ' ';
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
    private void placePickups(char[][] grid, List<Room> rooms, int[] roomDepths, int maxRoomDepth) {
        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            Room  room          = rooms.get(roomIndex);
            float depthFraction = maxRoomDepth > 0 ? roomDepths[roomIndex] / (float) maxRoomDepth : 0f;
            float medkitChance  = config.medkitChancePerRoom;
            float armourChance  = config.armourChancePerRoom;
            float ammoChance    = LevelGenConstants.LEVEL_GEN_AMMO_CHANCE_PER_ROOM;

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

            // Depth gradient: deeper rooms are richer to sustain the harder fights there.
            medkitChance += depthFraction * LevelGenConstants.LEVEL_GEN_DEPTH_MEDKIT_BONUS;
            ammoChance   += depthFraction * LevelGenConstants.LEVEL_GEN_DEPTH_AMMO_BONUS;

            if (config.medkits    && random.nextFloat() < medkitChance) tryPlacePickup(grid, room, 'H');
            if (config.armourKits && random.nextFloat() < armourChance)  tryPlacePickup(grid, room, 'A');
            if (random.nextFloat() < ammoChance) tryPlacePickup(grid, room, randomAmmoChar());
            if (random.nextFloat() < depthFraction * LevelGenConstants.LEVEL_GEN_DEPTH_EXTRA_AMMO_CHANCE) {
                tryPlacePickup(grid, room, randomAmmoChar());
            }
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
            if (isOccupiedByWeaponSpawn(tileColumn, tileRow)) continue;
            weaponSpawnPoints.add(new WeaponSpawnPoint(tileColumn, tileRow, randomWeaponItemType()));
            return true;
        }
        return false;
    }

    /** True if a weapon ground item was already recorded at this tile (weapon spawns are entity-side, not grid-encoded). */
    private boolean isOccupiedByWeaponSpawn(int tileColumn, int tileRow) {
        for (WeaponSpawnPoint spawnPoint : weaponSpawnPoints) {
            if (spawnPoint.tileColumn == tileColumn && spawnPoint.tileRow == tileRow) return true;
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

    /**
     * Spends the floor's encounter Threat-Point budget (balance idea 4, Pillar 1) on a roster
     * of enemies, then distributes that roster across the non-entrance rooms.
     *
     * The roster is planned by {@link EncounterBudgetPlanner} (anchor reserve, per-type cap,
     * ranged/melee mix). Distribution rules:
     *   - Rooms are visited DEEPEST-FIRST so the anchor lands in the floor's farthest room,
     *     making the hardest fight the climax of the descent.
     *   - The anchor is placed first and is EXEMPT from the per-room TP cap (it may, on an
     *     elite-gauntlet floor, exceed it alone — the sanctioned "gauntlet climax" exception).
     *   - Each remaining enemy goes into the first room (deepest-first) where it fits under the
     *     per-room TP cap and an eligible tile is free; if no room fits the cap, it is placed in
     *     any room with a free tile so budget is not wasted, otherwise dropped.
     */
    private void placeBudgetedEncounter(char[][] grid, List<Room> rooms, int[] roomDepths,
                                        List<EnemySpawnPoint> spawnPoints) {
        if (rooms.size() < 2) return;

        EncounterBudgetPlanner.Plan plan =
                new EncounterBudgetPlanner(dungeonDepth, random, config.enemyBudgetScale).plan();
        List<EnemyType> roster = plan.enemies();
        if (roster.isEmpty()) return;

        // Non-entrance room indices (1..n-1) sorted deepest-first over the MST depth gradient.
        Integer[] roomOrder = new Integer[rooms.size() - 1];
        for (int index = 0; index < roomOrder.length; index++) {
            roomOrder[index] = index + 1;
        }
        java.util.Arrays.sort(roomOrder, (left, right) -> Integer.compare(roomDepths[right], roomDepths[left]));

        float[] roomSpentThreat = new float[rooms.size()];
        boolean[][] usedTiles = new boolean[LevelGenConstants.LEVEL_GEN_GRID_HEIGHT]
                                            [LevelGenConstants.LEVEL_GEN_GRID_WIDTH];
        float perRoomCap = plan.perRoomThreatPointCap();

        // --- Place the anchor first, deepest room, exempt from the per-room cap.
        EnemyType anchor    = plan.anchor();
        int       anchorIndexInRoster = -1;
        if (anchor != null) {
            for (int roomOrderIndex = 0; roomOrderIndex < roomOrder.length; roomOrderIndex++) {
                int roomIndex = roomOrder[roomOrderIndex];
                if (tryPlaceEnemyInRoom(grid, rooms.get(roomIndex), anchor, usedTiles, spawnPoints)) {
                    roomSpentThreat[roomIndex] += plan.threatOf(anchor);
                    anchorIndexInRoster = 0; // anchor is always roster element 0 (added first)
                    break;
                }
            }
        }

        // --- Distribute the remaining roster, load-balancing across rooms instead of packing the
        // deepest few. The old algorithm filled deepest-first up to the per-room cap, so on a typical
        // floor only the 2-3 deepest rooms held every enemy (a death-trap for a low-level player)
        // while the rest stayed empty (the player struggled to find a fight). We now drop each enemy
        // into the eligible room with the LOWEST depth-weighted load, so enemies fan out across the
        // whole floor; deeper rooms still end up denser because their weight lets them absorb more.
        boolean[] roomTilesExhausted = new boolean[rooms.size()];
        for (int rosterIndex = 0; rosterIndex < roster.size(); rosterIndex++) {
            if (rosterIndex == anchorIndexInRoster) continue; // already placed
            EnemyType enemy = roster.get(rosterIndex);
            float cost = plan.threatOf(enemy);
            placeEnemyLoadBalanced(grid, rooms, roomOrder, roomDepths, enemy, cost, perRoomCap,
                    roomSpentThreat, roomTilesExhausted, usedTiles, spawnPoints);
        }
    }

    /**
     * Places one enemy into the least-loaded eligible room, spreading the roster across the whole
     * floor (balance fix: no empty rooms, no over-stuffed death-trap room). "Load" is the room's
     * spent Threat Points divided by its depth weight (1 + room depth), so deeper rooms tolerate
     * proportionally more before they look full — preserving the deeper-is-harder gradient while
     * still fanning enemies out. Two passes: first only rooms under the per-room cap, then (if the
     * cap blocked every room) any room with a free tile so the budget is still spent.
     */
    private void placeEnemyLoadBalanced(char[][] grid, List<Room> rooms, Integer[] roomOrder,
                                        int[] roomDepths, EnemyType enemy, float cost, float perRoomCap,
                                        float[] roomSpentThreat, boolean[] roomTilesExhausted,
                                        boolean[][] usedTiles, List<EnemySpawnPoint> spawnPoints) {
        for (int phase = 0; phase < 2; phase++) {
            boolean capPhase = (phase == 0);
            while (true) {
                int   bestRoom  = -1;
                float bestLoad  = Float.MAX_VALUE;
                int   bestDepth = -1;
                for (Integer roomIndex : roomOrder) {
                    if (roomTilesExhausted[roomIndex]) continue;
                    if (capPhase && roomSpentThreat[roomIndex] + cost > perRoomCap) continue;
                    float weight = 1f + roomDepths[roomIndex];
                    float load   = roomSpentThreat[roomIndex] / weight;
                    if (load < bestLoad
                            || (load == bestLoad && roomDepths[roomIndex] > bestDepth)) {
                        bestLoad  = load;
                        bestDepth = roomDepths[roomIndex];
                        bestRoom  = roomIndex;
                    }
                }
                if (bestRoom < 0) break; // no eligible room this phase
                if (tryPlaceEnemyInRoom(grid, rooms.get(bestRoom), enemy, usedTiles, spawnPoints)) {
                    roomSpentThreat[bestRoom] += cost;
                    return;
                }
                // Chosen room had no free tile after a bounded search — exclude it and retry.
                roomTilesExhausted[bestRoom] = true;
            }
        }
    }

    /**
     * Finds a free, spawn-eligible tile in the room and records an enemy spawn point there.
     * Returns true on success; false if no eligible tile was found after a bounded search.
     * Marks the chosen tile in usedTiles so two enemies never share a tile.
     */
    private boolean tryPlaceEnemyInRoom(char[][] grid, Room room, EnemyType enemy,
                                        boolean[][] usedTiles, List<EnemySpawnPoint> spawnPoints) {
        for (int attempt = 0; attempt < 40; attempt++) {
            int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
            int tileRow    = room.bottomRow  + 1 + random.nextInt(room.interiorHeight());
            // isEnemySpawnEligible bounds-checks first, so the usedTiles access below is safe.
            if (!isEnemySpawnEligible(grid, tileColumn, tileRow)) continue;
            if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
            if (usedTiles[tileRow][tileColumn]) continue;
            usedTiles[tileRow][tileColumn] = true;
            spawnPoints.add(new EnemySpawnPoint(enemy.spawnChar(), tileColumn, tileRow));
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Phase 5 — Connectivity audit (BFS + emergency corridors)
    // -------------------------------------------------------------------------

    private void verifyAndRepairConnectivity(char[][] grid, List<Room> rooms) {
        int startColumn = rooms.get(0).centerColumn();
        int startRow    = rooms.get(0).centerRow();

        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            Room room = rooms.get(roomIndex);
            int targetColumn = room.centerColumn();
            int targetRow    = room.centerRow();
            if (room.type == RoomType.STELLAR_OBSERVATORY) {
                // The room centre is the gravity-well core (';'), a solid prop by design
                // (decorateStellarObservatory() step 5) — it can never itself be a BFS target
                // (isBfsPassable() rejects solid props). Check the tile just north of it
                // instead: always open inner-disc floor by construction, so this generic
                // connectivity audit doesn't mistake a correctly-built rotunda for an
                // unreachable room and punch an emergency corridor straight through it.
                targetRow += 1;
            }
            if (!isTileReachable(grid, startColumn, startRow, targetColumn, targetRow)) {
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
     * Stamps exactly one stairs-down tile in the spatially deepest room, so the exit always
     * sits at the far end of the player's journey rather than at an arbitrary list index.
     *
     * Selection order:
     *   1. If a lock-and-key gate exists, the deepest room WITHIN the gated region — the exit
     *      becomes the payoff for finding the key and unlocking the vault.
     *   2. Otherwise the deepest room overall (by MST hop distance from the entrance).
     *   In both cases ties are broken toward landmark room types (command center > power plant
     *   > large) for a more cinematic finale.
     *   3. Legacy fallback by index, then the entrance room as an absolute last resort.
     */
    private void stampStairsDown(char[][] grid, List<Room> rooms, int[] roomDepths, boolean[] gatedRooms) {
        if (gatedRooms != null && stampInDeepestRoom(grid, rooms, roomDepths, gatedRooms)) return;
        if (stampInDeepestRoom(grid, rooms, roomDepths, null)) return;
        for (int roomIndex = rooms.size() - 1; roomIndex >= 1; roomIndex--) {
            if (tryStampInRoom(grid, rooms.get(roomIndex))) return;
        }
        tryStampInRoom(grid, rooms.get(0));
    }

    /**
     * Attempts to stamp the stairs in the deepest eligible room. When restrictMask is non-null
     * only rooms flagged true are considered; otherwise all non-entrance rooms are eligible.
     * Candidates are tried in descending (depth, landmark rank) order until one accepts the stamp.
     */
    private boolean stampInDeepestRoom(char[][] grid, List<Room> rooms, int[] roomDepths,
                                       boolean[] restrictMask) {
        List<Integer> candidates = new ArrayList<>();
        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            if (restrictMask != null && !restrictMask[roomIndex]) continue;
            candidates.add(roomIndex);
        }
        candidates.sort((indexA, indexB) -> {
            if (roomDepths[indexB] != roomDepths[indexA]) return roomDepths[indexB] - roomDepths[indexA];
            return landmarkRank(rooms.get(indexB).type) - landmarkRank(rooms.get(indexA).type);
        });
        for (int roomIndex : candidates) {
            if (tryStampInRoom(grid, rooms.get(roomIndex))) return true;
        }
        return false;
    }

    private int landmarkRank(RoomType type) {
        switch (type) {
            case COMMAND_CENTER: return 3;
            case POWER_PLANT:    return 2;
            case LARGE:          return 1;
            default:             return 0;
        }
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
    // Room depth metric (spatial difficulty/loot gradient)
    // -------------------------------------------------------------------------

    /**
     * Computes each room's depth as its hop distance from the entrance (room 0) over the MST
     * room tree captured in mstEdgeRooms. The MST spans every room, so every depth is defined.
     * Returned array is parallel to the rooms list. Used to drive depth-aware enemy/loot scaling
     * and deepest-room stairs placement — position in the level now carries meaning.
     */
    private int[] computeRoomDepths(List<Room> rooms) {
        int roomCount = rooms.size();
        int[] depth = new int[roomCount];
        for (int roomIndex = 0; roomIndex < roomCount; roomIndex++) depth[roomIndex] = -1;

        List<List<Integer>> adjacency = new ArrayList<>();
        for (int roomIndex = 0; roomIndex < roomCount; roomIndex++) adjacency.add(new ArrayList<>());
        for (Room[] edge : mstEdgeRooms) {
            int indexA = indexOfRoom(rooms, edge[0]);
            int indexB = indexOfRoom(rooms, edge[1]);
            if (indexA < 0 || indexB < 0) continue;
            adjacency.get(indexA).add(indexB);
            adjacency.get(indexB).add(indexA);
        }

        int[] queue = new int[roomCount];
        int head = 0;
        int tail = 0;
        depth[0]      = 0;
        queue[tail++] = 0;
        while (head < tail) {
            int current = queue[head++];
            for (int neighbor : adjacency.get(current)) {
                if (depth[neighbor] != -1) continue;
                depth[neighbor] = depth[current] + 1;
                queue[tail++]   = neighbor;
            }
        }
        // Any room not reached over the MST (degenerate edge data) is treated as adjacent.
        for (int roomIndex = 0; roomIndex < roomCount; roomIndex++) {
            if (depth[roomIndex] == -1) depth[roomIndex] = 0;
        }
        return depth;
    }

    /** Returns the index of a room in the list by reference identity, or -1 if absent. */
    private int indexOfRoom(List<Room> rooms, Room target) {
        for (int roomIndex = 0; roomIndex < rooms.size(); roomIndex++) {
            if (rooms.get(roomIndex) == target) return roomIndex;
        }
        return -1;
    }

    private int maxValue(int[] values) {
        int max = 0;
        for (int value : values) if (value > max) max = value;
        return max;
    }

    // -------------------------------------------------------------------------
    // Phase 5b — Lock-and-key gating
    // -------------------------------------------------------------------------

    /**
     * Promotes one plain door ('d') that is a true single-door cut (a bridge) into a
     * keycard-locked door, places the matching keycard in a room still reachable WITHOUT the
     * gate, and drops a bonus reward in the gated region. Returns a per-room mask of the
     * rooms sealed behind the gate (true = gated), or null when no valid gate was placed.
     *
     * Reachability is computed with a flood fill that treats the candidate door as solid, so a
     * door only qualifies as a gate when locking it genuinely severs part of the level (loops
     * are respected — a door paralleled by a loop corridor cuts nothing and is rejected). The
     * deepest such region is preferred so the gate guards the most rewarding part of the map.
     */
    private boolean[] placeLockAndKeyGate(char[][] grid, List<Room> rooms, int[] roomDepths,
                                          List<EnemySpawnPoint> spawnPoints) {
        int spawnColumn = rooms.get(0).centerColumn();
        int spawnRow    = rooms.get(0).centerRow();
        int roomCount   = rooms.size();
        int maxGated    = (int) Math.floor((roomCount - 1) * LevelGenConstants.LEVEL_GEN_GATE_MAX_GATED_FRACTION);

        int       bestGateColumn = -1;
        int       bestGateRow    = -1;
        int       bestScore      = -1;
        int       bestGatedCount = Integer.MAX_VALUE;
        boolean[][] bestReachable = null;

        for (int tileRow = 1; tileRow < LevelGenConstants.LEVEL_GEN_GRID_HEIGHT - 1; tileRow++) {
            for (int tileColumn = 1; tileColumn < LevelGenConstants.LEVEL_GEN_GRID_WIDTH - 1; tileColumn++) {
                if (grid[tileRow][tileColumn] != 'd') continue;
                boolean[][] reachable = floodFillBlocking(grid, spawnColumn, spawnRow, tileColumn, tileRow);

                int gatedCount            = 0;
                int reachableNonEntrance  = 0;
                int deepestGatedDepth     = -1;
                for (int roomIndex = 1; roomIndex < roomCount; roomIndex++) {
                    Room room = rooms.get(roomIndex);
                    if (reachable[room.centerRow()][room.centerColumn()]) {
                        reachableNonEntrance++;
                    } else {
                        gatedCount++;
                        if (roomDepths[roomIndex] > deepestGatedDepth) deepestGatedDepth = roomDepths[roomIndex];
                    }
                }

                // Need something behind the gate AND somewhere reachable to host the key,
                // and the gate must not seal off too much of the level.
                if (gatedCount == 0 || reachableNonEntrance == 0) continue;
                if (maxGated >= 1 && gatedCount > maxGated) continue;

                // Prefer gating the deepest region; on ties prefer the TIGHTEST vault (fewest
                // gated rooms) so the lock guards a small set-piece near the end rather than
                // sealing off a large slice of the level near the entrance.
                boolean better = deepestGatedDepth > bestScore
                              || (deepestGatedDepth == bestScore && gatedCount < bestGatedCount);
                if (better) {
                    bestScore      = deepestGatedDepth;
                    bestGatedCount = gatedCount;
                    bestGateColumn = tileColumn;
                    bestGateRow    = tileRow;
                    bestReachable  = reachable;
                }
            }
        }

        if (bestGateColumn < 0 || bestReachable == null) return null; // no valid gate this level

        KeycardColor color    = pickGateColor();
        grid[bestGateRow][bestGateColumn] = lockedDoorChar(color);

        // Split rooms into gated vs reachable; choose the deepest reachable room to host the key.
        boolean[] gatedRooms  = new boolean[roomCount];
        int keyRoomIndex      = -1;
        int keyRoomDepth      = -1;
        for (int roomIndex = 1; roomIndex < roomCount; roomIndex++) {
            Room room = rooms.get(roomIndex);
            if (bestReachable[room.centerRow()][room.centerColumn()]) {
                if (roomDepths[roomIndex] > keyRoomDepth) {
                    keyRoomDepth = roomDepths[roomIndex];
                    keyRoomIndex = roomIndex;
                }
            } else {
                gatedRooms[roomIndex] = true;
            }
        }

        boolean keyPlaced = keyRoomIndex >= 0
                && placeKeycardInReachableRoom(grid, rooms.get(keyRoomIndex), keycardChar(color), bestReachable);
        if (!keyPlaced) {
            // Could not give the player a way to reach the key — never strand the gate.
            grid[bestGateRow][bestGateColumn] = 'd';
            return null;
        }

        // Reward behind the gate so unlocking pays off beyond just reaching the exit.
        int rewardRoomIndex = deepestGatedRoomIndex(rooms, roomDepths, gatedRooms);
        if (rewardRoomIndex >= 0) {
            Room rewardRoom = rooms.get(rewardRoomIndex);
            placeRewardPickup(grid, rewardRoom, 'H', spawnPoints);
            placeRewardPickup(grid, rewardRoom, randomAmmoChar(), spawnPoints);
        }
        return gatedRooms;
    }

    /** Returns the gated room with the greatest depth, or -1 when none are gated. */
    private int deepestGatedRoomIndex(List<Room> rooms, int[] roomDepths, boolean[] gatedRooms) {
        int bestIndex = -1;
        int bestDepth = -1;
        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            if (!gatedRooms[roomIndex]) continue;
            if (roomDepths[roomIndex] > bestDepth) {
                bestDepth = roomDepths[roomIndex];
                bestIndex = roomIndex;
            }
        }
        return bestIndex;
    }

    /**
     * Flood fill over passable floor tiles from (startColumn,startRow), treating the tile at
     * (blockColumn,blockRow) as solid. Returns a visited mask indexed [row][column]. Uses the
     * same passability rule as the connectivity audit (walls/props/columns block; doors pass).
     */
    private boolean[][] floodFillBlocking(char[][] grid, int startColumn, int startRow,
                                          int blockColumn, int blockRow) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        boolean[][] visited = new boolean[gridHeight][gridWidth];

        int[] stackColumns = new int[gridWidth * gridHeight];
        int[] stackRows    = new int[gridWidth * gridHeight];
        int   stackTop     = 0;
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
            for (int direction = 0; direction < 4; direction++) {
                int neighborColumn = currentColumn + deltaColumns[direction];
                int neighborRow    = currentRow    + deltaRows[direction];
                if (!isInBounds(neighborColumn, neighborRow)) continue;
                if (visited[neighborRow][neighborColumn]) continue;
                if (neighborColumn == blockColumn && neighborRow == blockRow) continue;
                if (isBfsPassable(grid[neighborRow][neighborColumn])) {
                    visited[neighborRow][neighborColumn] = true;
                    stackColumns[stackTop] = neighborColumn;
                    stackRows[stackTop]    = neighborRow;
                    stackTop++;
                }
            }
        }
        return visited;
    }

    /**
     * Places the keycard pickup on a plain walkable floor tile inside the given room that is
     * confirmed reachable (in the supplied mask) and not adjacent to a door. Returns true on
     * success. Only plain floor is overwritten so existing props/pickups are preserved.
     */
    private boolean placeKeycardInReachableRoom(char[][] grid, Room room, char keycardPickupChar,
                                                boolean[][] reachable) {
        for (int attempt = 0; attempt < 30; attempt++) {
            int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
            int tileRow    = room.bottomRow  + 1 + random.nextInt(room.interiorHeight());
            if (!reachable[tileRow][tileColumn]) continue;
            char cell = grid[tileRow][tileColumn];
            if (cell != ' ' && cell != 'l' && cell != 'u' && cell != 'f') continue;
            if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
            grid[tileRow][tileColumn] = keycardPickupChar;
            return true;
        }
        return false;
    }

    /**
     * Places a reward pickup on a plain walkable floor tile that is not occupied by an enemy
     * spawn point and not adjacent to a door. No-op if no eligible tile is found.
     */
    private void placeRewardPickup(char[][] grid, Room room, char pickupChar,
                                   List<EnemySpawnPoint> spawnPoints) {
        for (int attempt = 0; attempt < 25; attempt++) {
            int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
            int tileRow    = room.bottomRow  + 1 + random.nextInt(room.interiorHeight());
            char cell = grid[tileRow][tileColumn];
            if (cell != ' ' && cell != 'l' && cell != 'u' && cell != 'f') continue;
            if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
            if (isOccupiedBySpawn(spawnPoints, tileColumn, tileRow)) continue;
            grid[tileRow][tileColumn] = pickupChar;
            return;
        }
    }

    private boolean isOccupiedBySpawn(List<EnemySpawnPoint> spawnPoints, int tileColumn, int tileRow) {
        for (EnemySpawnPoint spawn : spawnPoints) {
            if (spawn.tileColumn == tileColumn && spawn.tileRow == tileRow) return true;
        }
        return false;
    }

    private KeycardColor pickGateColor() {
        switch (random.nextInt(3)) {
            case 0:  return KeycardColor.RED;
            case 1:  return KeycardColor.YELLOW;
            default: return KeycardColor.BLUE;
        }
    }

    private char lockedDoorChar(KeycardColor color) {
        switch (color) {
            case RED:    return 'R';
            case YELLOW: return 'Y';
            default:     return 'B';
        }
    }

    private char keycardChar(KeycardColor color) {
        switch (color) {
            case RED:    return 'r';
            case YELLOW: return 'y';
            default:     return 'b';
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
     * Also accepts the STELLAR_OBSERVATORY room's walkable decals ('?' catwalk ring, '-'
     * starlight spoke, ']' zero-g debris) — without this, the generic budgeted-encounter
     * system (the only enemy placement this generator has; decorateStellarObservatory() does
     * not stamp spawns directly) could never roll the "spoke sniper on the ring" encounter
     * the design doc calls for, since every ring/spoke tile would otherwise be spawn-ineligible.
     * Excludes keycards, medical/armour pickups, and stairs to avoid destroying them on
     * enemy death (killEnemy stamps 'm' at the enemy's last tile).
     */
    private boolean isEnemySpawnEligible(char[][] grid, int tileColumn, int tileRow) {
        if (!isInBounds(tileColumn, tileRow)) return false;
        char cell = grid[tileRow][tileColumn];
        if (cell == ' ' || cell == 'l' || cell == 'u' || cell == 'f') return true;
        if (cell == '?' || cell == '-' || cell == ']') return true;
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
