package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.LevelGenConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * THE SHARED GROUP PLACEMENT (balance-overhaul order 2, E3 / E5): puts each planned
 * {@link EncounterGroup} into ONE room (or cavern chamber / pocket) of a finished layout, clustered
 * within {@link LevelGenConstants#LEVEL_GEN_PACK_CLUSTER_RADIUS} of its leader. Every combat generator
 * uses it, so groups mean the same thing in a rooms floor, a corridor floor and a cave.
 * <ul>
 *   <li>FIRST CONTACT (E5, COMBAT / ELITE): the lightest group of two or more goes into the nearest room
 *       that is not the start room, on a tile within {@link BalanceConfig#FIRST_CONTACT_MAX_WALK_TILES}
 *       of the start ({@link BalanceConfig#FIRST_CONTACT_FIRST_FLOOR_MAX_WALK_TILES} on depth 1).</li>
 *   <li>The ANCHOR group takes the deepest room by walk distance (it guards the floor's far end, and an
 *       ELITE's reward weapon is placed in or past its room).</li>
 *   <li>Every other group takes a room no group holds yet (a LARGE room may hold two), spreading the
 *       fights across the floor; only when rooms run out do groups share.</li>
 * </ul>
 * Rooms come from the generator's {@link SupplySlotProvider} via the {@link SupplySlotSurvey}; which tiles
 * an enemy may stand on comes from the generator's own {@link SpawnTileRule}.
 */
public final class EncounterPlacer {

    /** A generator's own "may an enemy spawn here" rule (terrain, door clearance, safe radius). */
    public interface SpawnTileRule {
        boolean isSpawnable(char[][] grid, int tileColumn, int tileRow);
    }

    /** The placed encounter: spawn points tagged with group ids, and the anchor's spawn index. */
    public static final class Placement {
        public final List<EnemySpawnPoint> spawnPoints;
        public final int                   anchorSpawnIndex;

        Placement(List<EnemySpawnPoint> spawnPoints, int anchorSpawnIndex) {
            this.spawnPoints      = spawnPoints;
            this.anchorSpawnIndex = anchorSpawnIndex;
        }
    }

    /** One candidate room: its spawnable tiles, how far in it sits, and how many groups it holds. */
    private static final class Region {
        final int        id;
        final boolean    large;
        final List<int[]> tiles = new ArrayList<>();   // {column, row, walk}
        int minimumWalk = Integer.MAX_VALUE;
        int maximumWalk = -1;
        int groups;
        int used;

        Region(int id, boolean large) {
            this.id    = id;
            this.large = large;
        }

        int freeTiles() { return tiles.size() - used; }
    }

    private EncounterPlacer() {}

    /**
     * Places a plan's groups.
     *
     * @param grid              the finished grid ({@code grid[row][column]}, row 0 = bottom)
     * @param survey            the grid's survey (rooms, walk distances, start)
     * @param plan              the encounter plan
     * @param rule              the generator's spawn-tile rule
     * @param random            the generator's seeded stream
     * @param depth             floor depth (1-based) — depth 1 tightens first contact
     * @param firstContactRule  whether E5 applies (COMBAT / ELITE)
     */
    public static Placement place(char[][] grid, SupplySlotSurvey survey, EncounterBudgetPlanner.Plan plan,
                                  SpawnTileRule rule, Random random, int depth, boolean firstContactRule) {
        List<EnemySpawnPoint> spawns = new ArrayList<>();
        List<EncounterGroup> groups = plan.groups();
        if (groups.isEmpty()) return new Placement(spawns, -1);

        Map<Integer, Region> regions = collectRegions(grid, survey, rule);
        boolean[][] used = new boolean[grid.length][grid[0].length];
        boolean[] placedGroup = new boolean[groups.size()];
        int contactLimit = depth <= 1 ? BalanceConfig.FIRST_CONTACT_FIRST_FLOOR_MAX_WALK_TILES
                                      : BalanceConfig.FIRST_CONTACT_MAX_WALK_TILES;
        int anchorSpawnIndex = -1;

        // --- E5 first contact: the lightest group of two or more, in the nearest room, near the start.
        if (firstContactRule) {
            int contactGroup = -1;
            for (int index = 0; index < groups.size(); index++) {
                EncounterGroup group = groups.get(index);
                if (group.size() < 2 || group.anchor) continue;
                if (contactGroup < 0 || group.threat < groups.get(contactGroup).threat) contactGroup = index;
            }
            if (contactGroup >= 0) {
                Region nearest = null;
                for (Region region : regions.values()) {
                    if (region.freeTiles() < groups.get(contactGroup).size()) continue;
                    if (nearest == null || region.minimumWalk < nearest.minimumWalk) nearest = region;
                }
                // No room reaches within the limit (a long corridor spine, a sprawling cave): the first
                // fight stands in the corridor pocket off the start instead ("corridor pockets", E3).
                if (nearest == null || nearest.minimumWalk > contactLimit) {
                    Region pocket = collectConnectorPocket(grid, survey, rule, contactLimit);
                    if (pocket.freeTiles() >= groups.get(contactGroup).size()) nearest = pocket;
                }
                if (nearest != null) {
                    placeGroup(groups.get(contactGroup), contactGroup, nearest, contactLimit, used, random, spawns, regions);
                    placedGroup[contactGroup] = true;
                }
            }
        }

        // --- The anchor group: the deepest room that can hold it.
        for (int index = 0; index < groups.size(); index++) {
            EncounterGroup group = groups.get(index);
            if (!group.anchor || placedGroup[index]) continue;
            Region deepest = null;
            for (Region region : regions.values()) {
                if (region.groups > 0 || region.freeTiles() < group.size()) continue;
                if (deepest == null || region.maximumWalk > deepest.maximumWalk) deepest = region;
            }
            if (deepest == null) deepest = roomiest(regions);
            if (deepest != null) {
                int first = spawns.size();
                placeGroup(group, index, deepest, Integer.MAX_VALUE, used, random, spawns, regions);
                anchorSpawnIndex = heaviestSpawn(spawns, first, plan);
            }
            placedGroup[index] = true;
        }

        // --- Everyone else: largest first, each into a room no group holds yet (a LARGE room takes two).
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < groups.size(); index++) if (!placedGroup[index]) order.add(index);
        order.sort((left, right) -> Integer.compare(groups.get(right).size(), groups.get(left).size()));
        for (int index : order) {
            EncounterGroup group = groups.get(index);
            List<Region> free = new ArrayList<>();
            for (Region region : regions.values()) {
                if (region.freeTiles() < group.size()) continue;
                if (region.groups == 0 || (region.large && region.groups < 2)) free.add(region);
            }
            Region target = free.isEmpty() ? roomiest(regions) : free.get(random.nextInt(free.size()));
            if (target != null) placeGroup(group, index, target, Integer.MAX_VALUE, used, random, spawns, regions);
        }
        return new Placement(spawns, anchorSpawnIndex);
    }

    /** Every non-start room's spawnable, reachable tiles, keyed by room id (ascending — deterministic). */
    private static Map<Integer, Region> collectRegions(char[][] grid, SupplySlotSurvey survey, SpawnTileRule rule) {
        Map<Integer, Region> regions = new TreeMap<>();
        int startRegion = survey.startRegion();
        for (int tileRow = 0; tileRow < grid.length; tileRow++) {
            for (int tileColumn = 0; tileColumn < grid[0].length; tileColumn++) {
                int region = survey.regionAt(tileColumn, tileRow);
                if (region < 0 || region == startRegion) continue;
                int walk = survey.walkDistanceWithKeycards(tileColumn, tileRow);
                if (walk == SupplySlotSurvey.UNREACHABLE) continue;
                if (!rule.isSpawnable(grid, tileColumn, tileRow)) continue;
                Region entry = regions.get(region);
                if (entry == null) {
                    entry = new Region(region, survey.isLargeRegion(region));
                    regions.put(region, entry);
                }
                entry.tiles.add(new int[]{tileColumn, tileRow, walk});
                entry.minimumWalk = Math.min(entry.minimumWalk, walk);
                entry.maximumWalk = Math.max(entry.maximumWalk, walk);
            }
        }
        return regions;
    }

    /**
     * The corridor (connector) tiles within {@code walkLimit} of the start that an enemy may stand on —
     * the first-contact fallback when no room is close enough. Never the start tile's neighbourhood: the
     * generator's spawn rule (door clearance, safe radius) still applies.
     */
    private static Region collectConnectorPocket(char[][] grid, SupplySlotSurvey survey, SpawnTileRule rule,
                                                 int walkLimit) {
        Region pocket = new Region(SupplySlotProvider.CONNECTOR_REGION, false);
        for (int tileRow = 0; tileRow < grid.length; tileRow++) {
            for (int tileColumn = 0; tileColumn < grid[0].length; tileColumn++) {
                if (survey.regionAt(tileColumn, tileRow) != SupplySlotProvider.CONNECTOR_REGION) continue;
                int walk = survey.walkDistanceWithKeycards(tileColumn, tileRow);
                if (walk == SupplySlotSurvey.UNREACHABLE || walk > walkLimit) continue;
                if (walk < BalanceConfig.FIRST_CONTACT_MIN_WALK_TILES) continue;
                if (!rule.isSpawnable(grid, tileColumn, tileRow)) continue;
                pocket.tiles.add(new int[]{tileColumn, tileRow, walk});
                pocket.minimumWalk = Math.min(pocket.minimumWalk, walk);
                pocket.maximumWalk = Math.max(pocket.maximumWalk, walk);
            }
        }
        return pocket;
    }

    /** The room with the most free tiles (the overflow home), or null when every room is full. */
    private static Region roomiest(Map<Integer, Region> regions) {
        Region best = null;
        for (Region region : regions.values()) {
            if (region.freeTiles() <= 0) continue;
            if (best == null || region.freeTiles() > best.freeTiles()) best = region;
        }
        return best;
    }

    /**
     * Places one group in one room: a leader on a free tile (within {@code leaderWalkLimit} walk tiles of
     * the start when it can be), the rest ring-outward from the leader inside the same room, any
     * stragglers anywhere in the room, and only then — the room full — in the roomiest other room.
     */
    private static void placeGroup(EncounterGroup group, int groupId, Region region, int leaderWalkLimit,
                                   boolean[][] used, Random random, List<EnemySpawnPoint> spawns,
                                   Map<Integer, Region> regions) {
        List<int[]> leaders = new ArrayList<>();
        for (int[] tile : region.tiles) {
            if (!used[tile[1]][tile[0]] && tile[2] <= leaderWalkLimit) leaders.add(tile);
        }
        if (leaders.isEmpty()) {
            int[] nearest = null;
            for (int[] tile : region.tiles) {
                if (used[tile[1]][tile[0]]) continue;
                if (nearest == null || tile[2] < nearest[2]) nearest = tile;
            }
            if (nearest == null) return;
            leaders.add(nearest);
        }
        int[] leader = leaders.get(random.nextInt(leaders.size()));
        List<EnemyType> members = group.members;
        claim(region, leader, members.get(0), groupId, used, spawns);
        int placed = 1;
        int radius = LevelGenConstants.LEVEL_GEN_PACK_CLUSTER_RADIUS;
        for (int ring = 1; ring <= radius && placed < members.size(); ring++) {
            for (int[] tile : region.tiles) {
                if (placed >= members.size()) break;
                if (used[tile[1]][tile[0]]) continue;
                if (Math.max(Math.abs(tile[0] - leader[0]), Math.abs(tile[1] - leader[1])) != ring) continue;
                claim(region, tile, members.get(placed), groupId, used, spawns);
                placed++;
            }
        }
        for (int[] tile : region.tiles) {
            if (placed >= members.size()) break;
            if (used[tile[1]][tile[0]]) continue;
            claim(region, tile, members.get(placed), groupId, used, spawns);
            placed++;
        }
        while (placed < members.size()) {
            Region overflow = roomiest(regions);
            if (overflow == null) return;
            for (int[] tile : overflow.tiles) {
                if (placed >= members.size()) break;
                if (used[tile[1]][tile[0]]) continue;
                claim(overflow, tile, members.get(placed), groupId, used, spawns);
                placed++;
            }
        }
        region.groups++;
    }

    private static void claim(Region region, int[] tile, EnemyType type, int groupId, boolean[][] used,
                              List<EnemySpawnPoint> spawns) {
        used[tile[1]][tile[0]] = true;
        region.used++;
        spawns.add(new EnemySpawnPoint(type.spawnChar(), tile[0], tile[1], (char) 0, groupId));
    }

    /** The spawn index of the heaviest member placed since {@code firstIndex} (the anchor itself). */
    private static int heaviestSpawn(List<EnemySpawnPoint> spawns, int firstIndex, EncounterBudgetPlanner.Plan plan) {
        int best = -1;
        float bestThreat = -1f;
        for (int index = firstIndex; index < spawns.size(); index++) {
            EnemyType type = EnemyType.fromSpawnChar(spawns.get(index).spawnChar);
            float threat = type == null ? 0f : plan.threatOf(type);
            if (threat > bestThreat) {
                bestThreat = threat;
                best       = index;
            }
        }
        return best;
    }
}
