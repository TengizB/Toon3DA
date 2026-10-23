package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.route.NodeSupplySpec;
import ge.tbegvadze.toon3d.route.RouteNodeType;
import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map;

/**
 * Hazard placement (balance-overhaul order 4, H14): on COMBAT and ELITE floors, a share of the rooms that hold
 * a group of {@link BalanceConfig#HAZARD_GROUP_MIN_SIZE}+ enemies get one or two explosive barrels within a
 * couple of tiles of the group's centroid, so the player looks at the room before charging in.
 *
 * <p>Runs inside {@link FloorPopulator#populate} on the FINISHED, supply-stamped grid, so it only ever claims
 * a plain floor tile that no spawn, pickup, weapon or credit already owns. Pure and deterministic: every draw
 * comes from the floor seed. Hand-made levels never come through here.
 *
 * <p>Exclusions: the start room, any tile closer to the start than
 * {@link BalanceConfig#FIRST_CONTACT_MIN_WALK_TILES} walking steps, a tile beside a door, a tile in a door
 * clearance zone or a room's lane band (level-generation-overhaul R26; a flush tile is preferred), and any tile whose
 * (solid) barrel would cut a reachable tile off — checked by re-surveying the grid, with and without keycards.
 */
final class GroupBarrelPlacer {

    /** The existing explosive-barrel symbol (docs/tile-symbols.txt). No new symbol. */
    static final char BARREL_SYMBOL = 'E';
    /** The sprite the palette must bind {@link #BARREL_SYMBOL} to on a generated level that carries barrels. */
    static final String BARREL_SPRITE_ID = "prop_explosive_barrel";

    /** Seed salt for the barrel room / tile draws. */
    private static final long BARREL_SEED_SALT = 0xBA22E10DL;

    private GroupBarrelPlacer() {}

    /** One eligible room: its id and the centroid tile of its largest group. */
    private static final class GroupRoom {
        final int regionId;
        final int centroidColumn;
        final int centroidRow;
        int       bestGroupSize;
        /** Tiles a barrel may stand on (each safe to place on its own); filled by {@link #hostableCandidates}. */
        List<int[]> candidates = new ArrayList<>();

        GroupRoom(int regionId, int centroidColumn, int centroidRow, int groupSize) {
            this.regionId       = regionId;
            this.centroidColumn = centroidColumn;
            this.centroidRow    = centroidRow;
            this.bestGroupSize  = groupSize;
        }
    }

    /** What a placement pass did: barrels stamped, rooms that qualified, rooms that actually received barrels. */
    static final class Outcome {
        final int barrels;
        final int eligibleRooms;
        final int roomsServed;
        Outcome(int barrels, int eligibleRooms, int roomsServed) {
            this.barrels       = barrels;
            this.eligibleRooms = eligibleRooms;
            this.roomsServed   = roomsServed;
        }
    }

    /** Whether this floor's node type gets barrels (COMBAT and ELITE only). */
    static boolean appliesTo(NodeSupplySpec spec) {
        return spec != null && (spec.type() == RouteNodeType.COMBAT || spec.type() == RouteNodeType.ELITE);
    }

    /**
     * Places the floor's barrels into {@code grid} (symbol {@link #BARREL_SYMBOL}).
     *
     * @param survey        the survey of the grid taken BEFORE supply was stamped (walk distances, rooms)
     * @param reservedTiles packed (row, column) keys already owned by a spawn, weapon or credit
     * @return the outcome (all zero when the floor is not COMBAT / ELITE or no room holds a group)
     */
    static Outcome place(char[][] grid, SupplySlotSurvey survey,
                     List<EnemySpawnPoint> spawns, Set<Long> reservedTiles, NodeSupplySpec spec, long seed) {
        if (!appliesTo(spec)) return new Outcome(0, 0, 0);

        List<GroupRoom> eligibleRooms = eligibleRooms(survey, spawns);
        // A room "holding a group" only counts when a barrel can safely stand in it: a tight room where every
        // tile within reach of the centroid would strand a floor tile cannot host one (never a blocked path).
        for (GroupRoom room : eligibleRooms) room.candidates = hostableCandidates(grid, survey, reservedTiles, room);
        eligibleRooms.removeIf(room -> room.candidates.isEmpty());
        int roomsNeeded = GameMath.groupBarrelRoomCount(eligibleRooms.size(), BalanceConfig.HAZARD_GROUP_ROOM_SHARE);
        if (roomsNeeded <= 0) return new Outcome(0, eligibleRooms.size(), 0);

        Random random = new Random(seed ^ BARREL_SEED_SALT);
        Collections.shuffle(eligibleRooms, random);

        int barrelsPlaced = 0;
        int roomsServed   = 0;
        for (GroupRoom room : eligibleRooms) {
            if (roomsServed >= roomsNeeded) break;
            int wanted = BalanceConfig.HAZARD_GROUP_BARRELS_MIN
                    + random.nextInt(BalanceConfig.HAZARD_GROUP_BARRELS_MAX - BalanceConfig.HAZARD_GROUP_BARRELS_MIN + 1);
            int placedHere = placeInRoom(grid, survey, room, wanted, random);
            barrelsPlaced += placedHere;
            if (placedHere >= BalanceConfig.HAZARD_GROUP_BARRELS_MIN) roomsServed++;
        }
        return new Outcome(barrelsPlaced, eligibleRooms.size(), roomsServed);
    }

    /** Rooms (ascending id) that hold a group of HAZARD_GROUP_MIN_SIZE+, minus the start room. */
    private static List<GroupRoom> eligibleRooms(SupplySlotSurvey survey, List<EnemySpawnPoint> spawns) {
        // Members per group id, in spawn order (deterministic).
        Map<Integer, List<EnemySpawnPoint>> groups = new TreeMap<>();
        for (EnemySpawnPoint spawn : spawns) {
            if (spawn.groupId < 0 || EnemyType.fromSpawnChar(spawn.spawnChar) == null) continue;
            groups.computeIfAbsent(spawn.groupId, key -> new ArrayList<>()).add(spawn);
        }
        int startRegion = survey.startRegion();
        Map<Integer, GroupRoom> byRoom = new TreeMap<>();
        for (List<EnemySpawnPoint> members : groups.values()) {
            if (members.size() < BalanceConfig.HAZARD_GROUP_MIN_SIZE) continue;
            float columnSum = 0f;
            float rowSum    = 0f;
            for (EnemySpawnPoint member : members) {
                columnSum += member.tileColumn;
                rowSum    += member.tileRow;
            }
            int centroidColumn = Math.round(columnSum / members.size());
            int centroidRow    = Math.round(rowSum / members.size());
            // The group's room is the room of the member nearest the centroid (ties: first in spawn order).
            EnemySpawnPoint nearest = members.get(0);
            int nearestDistance = Integer.MAX_VALUE;
            for (EnemySpawnPoint member : members) {
                int distance = Math.max(Math.abs(member.tileColumn - centroidColumn),
                        Math.abs(member.tileRow - centroidRow));
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = member;
                }
            }
            int region = survey.regionAt(nearest.tileColumn, nearest.tileRow);
            if (region < 0 || region == startRegion) continue;
            GroupRoom existing = byRoom.get(region);
            if (existing == null || members.size() > existing.bestGroupSize) {
                byRoom.put(region, new GroupRoom(region, centroidColumn, centroidRow, members.size()));
            }
        }
        return new ArrayList<>(byRoom.values());
    }

    /** The tiles within the radius of the group's centroid where a barrel may legally stand, each safe alone. */
    private static List<int[]> hostableCandidates(char[][] grid, SupplySlotSurvey survey, Set<Long> reservedTiles,
                                                  GroupRoom room) {
        int radius = BalanceConfig.HAZARD_GROUP_BARREL_RADIUS_TILES;
        List<int[]> hostable = new ArrayList<>();
        for (int tileRow = room.centroidRow - radius; tileRow <= room.centroidRow + radius; tileRow++) {
            for (int tileColumn = room.centroidColumn - radius; tileColumn <= room.centroidColumn + radius;
                 tileColumn++) {
                if (tileRow < 0 || tileRow >= grid.length || tileColumn < 0 || tileColumn >= grid[0].length) continue;
                if (!SupplySlotSurvey.isPlainFloor(grid[tileRow][tileColumn])) continue;
                if (reservedTiles.contains(tileKey(tileColumn, tileRow))) continue;
                if (survey.regionAt(tileColumn, tileRow) != room.regionId) continue;
                if (SupplySlotSurvey.isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
                // R26 (level-generation-overhaul): never in a door clearance zone or a room's lane band.
                if (DoorwayRules.isInDoorClearance(grid, tileColumn, tileRow)) continue;
                if (survey.isKeepClear(tileColumn, tileRow)) continue;
                int walk = survey.walkDistanceWithKeycards(tileColumn, tileRow);
                if (walk == SupplySlotSurvey.UNREACHABLE || walk < BalanceConfig.FIRST_CONTACT_MIN_WALK_TILES) continue;
                // The same two guards placeInRoom applies, so a room whose every tile would plug a passage is
                // never counted as able to host a barrel it can never receive.
                if (plugsAPassage(grid, tileColumn, tileRow)) continue;
                if (!keepsEverythingReachable(grid, survey, tileColumn, tileRow)) continue;
                hostable.add(new int[]{tileColumn, tileRow});
            }
        }
        return hostable;
    }

    private static int placeInRoom(char[][] grid, SupplySlotSurvey survey, GroupRoom room, int wanted, Random random) {
        List<int[]> candidates = new ArrayList<>(room.candidates);
        Collections.shuffle(candidates, random);
        // R26: a barrel prefers to stand against a wall when a flush tile exists in its radius (stable, so the
        // shuffled order — and every draw — is kept within each group).
        List<int[]> ordered = new ArrayList<>();
        for (int[] candidate : candidates) if (isFlush(grid, candidate[0], candidate[1])) ordered.add(candidate);
        for (int[] candidate : candidates) if (!isFlush(grid, candidate[0], candidate[1])) ordered.add(candidate);
        candidates = ordered;
        int placed = 0;
        for (int[] candidate : candidates) {
            if (placed >= wanted) break;
            int tileColumn = candidate[0];
            int tileRow    = candidate[1];
            // Earlier barrels (this room's, or another's) may have changed what is safe: re-check, then stamp.
            // A barrel may never plug a one-tile-wide passage, even where a detour keeps everything reachable.
            if (plugsAPassage(grid, tileColumn, tileRow)) continue;
            if (!keepsEverythingReachable(grid, survey, tileColumn, tileRow)) continue;
            grid[tileRow][tileColumn] = BARREL_SYMBOL;
            placed++;
        }
        return placed;
    }

    /**
     * Passage guard: true when a barrel here would sit in a one-tile-wide passage, OR would squeeze a solid
     * neighbour into one. Checked before stamping so FloorPopulator's final passage sweep never has to remove
     * a counted barrel. The grid is left exactly as it was.
     */
    private static boolean plugsAPassage(char[][] grid, int tileColumn, int tileRow) {
        if (FloorPopulator.wouldPlugPassage(grid, tileColumn, tileRow)) return true;
        char previous = grid[tileRow][tileColumn];
        grid[tileRow][tileColumn] = BARREL_SYMBOL;
        boolean plugs = false;
        for (int direction = 0; direction < 4 && !plugs; direction++) {
            int neighborColumn = tileColumn + STEP_COLUMNS[direction];
            int neighborRow    = tileRow    + STEP_ROWS[direction];
            if (neighborRow < 0 || neighborRow >= grid.length
                    || neighborColumn < 0 || neighborColumn >= grid[0].length) continue;
            char neighbor = grid[neighborRow][neighborColumn];
            if (!Level.isPropSolid(neighbor) && !Level.isColumn(neighbor)) continue;
            plugs = FloorPopulator.wouldPlugPassage(grid, neighborColumn, neighborRow);
        }
        grid[tileRow][tileColumn] = previous;
        return plugs;
    }

    /**
     * Connectivity guard: the barrel is SOLID, so every tile that is reachable now (with and without keycards,
     * columns blocking as they do in play) must still be afterwards, and so must the exit. The grid is left
     * exactly as it was.
     */
    private static boolean keepsEverythingReachable(char[][] grid, SupplySlotSurvey survey,
                                                    int tileColumn, int tileRow) {
        boolean[][] beforeWithKeycards    = flood(grid, survey, true);
        boolean[][] beforeWithoutKeycards = flood(grid, survey, false);
        if (!beforeWithKeycards[tileRow][tileColumn]) return false;
        boolean wasReachableFree = beforeWithoutKeycards[tileRow][tileColumn];
        char previous = grid[tileRow][tileColumn];
        grid[tileRow][tileColumn] = BARREL_SYMBOL;
        boolean[][] afterWithKeycards = flood(grid, survey, true);
        boolean keeps = count(afterWithKeycards) == count(beforeWithKeycards) - 1
                && count(flood(grid, survey, false)) == count(beforeWithoutKeycards) - (wasReachableFree ? 1 : 0)
                && (survey.exitColumn < 0 || afterWithKeycards[survey.exitRow][survey.exitColumn]);
        grid[tileRow][tileColumn] = previous;
        return keeps;
    }

    /** A tile with a wall as an orthogonal neighbour. */
    private static boolean isFlush(char[][] grid, int tileColumn, int tileRow) {
        for (int direction = 0; direction < 4; direction++) {
            int neighborColumn = tileColumn + STEP_COLUMNS[direction];
            int neighborRow    = tileRow    + STEP_ROWS[direction];
            if (neighborRow < 0 || neighborRow >= grid.length
                    || neighborColumn < 0 || neighborColumn >= grid[0].length) return true;
            if (Level.isWall(grid[neighborRow][neighborColumn])) return true;
        }
        return false;
    }

    private static final int[] STEP_COLUMNS = { 0, 0, 1, -1 };
    private static final int[] STEP_ROWS    = { 1, -1, 0, 0 };

    /** Whether the player can walk onto a tile: no wall, no solid prop (barrels included), no column. */
    private static boolean isWalkable(char cell, boolean keycardsAllowed) {
        return SupplySlotSurvey.isPassable(cell, keycardsAllowed) && !Level.isColumn(cell);
    }

    private static int count(boolean[][] reached) {
        int count = 0;
        for (boolean[] row : reached) for (boolean cell : row) if (cell) count++;
        return count;
    }

    private static boolean[][] flood(char[][] grid, SupplySlotSurvey survey, boolean withKeycards) {
        int height = grid.length;
        int width  = grid[0].length;
        boolean[][] reached = new boolean[height][width];
        if (survey.startRow < 0 || survey.startColumn < 0) return reached;
        int[] queue = new int[width * height];
        int head = 0;
        int tail = 0;
        reached[survey.startRow][survey.startColumn] = true;
        queue[tail++] = survey.startRow * width + survey.startColumn;
        while (head < tail) {
            int packed        = queue[head++];
            int currentRow    = packed / width;
            int currentColumn = packed % width;
            for (int direction = 0; direction < 4; direction++) {
                int neighborColumn = currentColumn + STEP_COLUMNS[direction];
                int neighborRow    = currentRow + STEP_ROWS[direction];
                if (neighborRow < 0 || neighborRow >= height || neighborColumn < 0 || neighborColumn >= width) continue;
                if (reached[neighborRow][neighborColumn]) continue;
                if (!isWalkable(grid[neighborRow][neighborColumn], withKeycards)) continue;
                reached[neighborRow][neighborColumn] = true;
                queue[tail++] = neighborRow * width + neighborColumn;
            }
        }
        return reached;
    }

    private static long tileKey(int tileColumn, int tileRow) {
        return ((long) tileRow << 32) | (tileColumn & 0xFFFFFFFFL);
    }
}
