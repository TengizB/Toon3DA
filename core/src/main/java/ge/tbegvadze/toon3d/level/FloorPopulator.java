package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.route.NodeSupplySpec;
import ge.tbegvadze.toon3d.route.RouteRegistries;
import ge.tbegvadze.toon3d.util.BossBalance;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * THE SHARED FLOOR PIPELINE's supply half (balance-overhaul order 2, S1). Every generator finishes its
 * layout (rooms, decoration, lock-and-key, stairs) and its encounter, then hands the grid here:
 * <pre>
 *   finished grid --SupplySlotSurvey--> slots --SupplyPlanner.plan--> plan --SupplyPlanner.place--> placement
 *        --stamp--> grid pickups, carriers on spawn points, planned weapon spawns, credit chips, report
 * </pre>
 * No generator places a pickup by its own rule; hand-made levels never come through here.
 *
 * <p>Pure: no LibGDX imports; every random draw is seeded from the floor seed.
 */
public final class FloorPopulator {

    /** Seed salt for the carrier order (which enemy carries which drop). */
    private static final long CARRIER_SEED_SALT = 0xCA221E25L;

    private FloorPopulator() {}

    /** The populated content a generator builds its {@link Level} from. */
    public static final class Result {
        public final List<EnemySpawnPoint>  spawnPoints;
        public final List<WeaponSpawnPoint> weaponSpawnPoints;
        public final List<CreditSpawnPoint> creditSpawnPoints;
        public final FloorContentReport     report;
        /** Explosive barrels placed beside groups (order 4, H14); the generated palette must bind their symbol. */
        public final int                    barrelCount;

        Result(List<EnemySpawnPoint> spawnPoints, List<WeaponSpawnPoint> weaponSpawnPoints,
               List<CreditSpawnPoint> creditSpawnPoints, FloorContentReport report, int barrelCount) {
            this.barrelCount       = barrelCount;
            this.spawnPoints       = spawnPoints;
            this.weaponSpawnPoints = weaponSpawnPoints;
            this.creditSpawnPoints = creditSpawnPoints;
            this.report            = report;
        }

        /** Builds the level and attaches the planned credits + report (the generator's last step). */
        public Level attachTo(Level level) {
            level.attachPlannedContent(creditSpawnPoints, report);
            return level;
        }
    }

    /** What the encounter step decided, carried into the report. */
    public static final class EncounterFacts {
        public final int   anchorSpawnIndex;
        public final float threatSpent;
        public final float threatCap;
        public final int   bodyTarget;
        public final int   targetWalkableTiles;

        public EncounterFacts(int anchorSpawnIndex, float threatSpent, float threatCap, int bodyTarget,
                              int targetWalkableTiles) {
            this.anchorSpawnIndex    = anchorSpawnIndex;
            this.threatSpent         = threatSpent;
            this.threatCap           = threatCap;
            this.bodyTarget          = bodyTarget;
            this.targetWalkableTiles = targetWalkableTiles;
        }
    }

    /** The spec a config asks for: its own, else the registered COMBAT row. */
    public static NodeSupplySpec specOf(LevelGenConfig config) {
        return specOf(config, ge.tbegvadze.toon3d.route.RouteNodeType.COMBAT);
    }

    /**
     * The spec a config asks for: its own, else the registered row for {@code defaultType} — a bespoke
     * generator (the boss arena, the clinic, the event room, the airlock) defaults to its node's row.
     */
    public static NodeSupplySpec specOf(LevelGenConfig config, ge.tbegvadze.toon3d.route.RouteNodeType defaultType) {
        if (config != null && config.supplySpec != null) return config.supplySpec;
        return RouteRegistries.nodeSupplySpecs().getOrCombat(defaultType);
    }

    /**
     * Plans and places this floor's supply and writes it into {@code grid} (pickup symbols).
     *
     * @param generatorName the generator's stable id, for the report
     * @param grid          the finished grid ({@code grid[row][column]}, row 0 = bottom); pickups are stamped in
     * @param provider      the generator's room model
     * @param spawnPoints   the encounter's spawn points (group ids set when the encounter tracks groups)
     * @param facts         the encounter's anchor / threat / body-target facts
     * @param config        the floor's config (spec, carried ammo types, cadence)
     * @param depth         floor depth (1-based)
     * @param seed          the floor seed
     */
    public static Result populate(String generatorName, char[][] grid, SupplySlotProvider provider,
                                  List<EnemySpawnPoint> spawnPoints, EncounterFacts facts,
                                  LevelGenConfig config, int depth, long seed) {
        return populate(generatorName, grid, provider, spawnPoints, facts, config, specOf(config), depth, seed);
    }

    /** {@link #populate} with an explicit spec (a bespoke generator passes its node type's default). */
    public static Result populate(String generatorName, char[][] grid, SupplySlotProvider provider,
                                  List<EnemySpawnPoint> spawnPoints, EncounterFacts facts,
                                  LevelGenConfig config, NodeSupplySpec spec, int depth, long seed) {
        SupplySlotSurvey survey = SupplySlotSurvey.survey(grid, provider);

        // --- The roster the floor actually fields (its demand), and which rooms hold a fight.
        List<EnemyType> roster   = new ArrayList<>();
        List<Integer>   carriers = new ArrayList<>();
        Set<Long>       occupied = new HashSet<>();
        Set<Integer>    groupRooms = new HashSet<>();
        for (int index = 0; index < spawnPoints.size(); index++) {
            EnemySpawnPoint spawn = spawnPoints.get(index);
            occupied.add(tileKey(spawn.tileColumn, spawn.tileRow));
            EnemyType type = EnemyType.fromSpawnChar(spawn.spawnChar);
            if (type == null) continue;            // the boss marker: seeded by World, never a carrier
            roster.add(type);
            carriers.add(index);
            int region = survey.regionAt(spawn.tileColumn, spawn.tileRow);
            if (region >= 0) groupRooms.add(region);
        }
        Collections.shuffle(carriers, new Random(seed ^ CARRIER_SEED_SALT));

        List<SupplySlot> slots = new ArrayList<>();
        for (SupplySlot slot : survey.slots()) {
            if (occupied.contains(tileKey(slot.tileColumn, slot.tileRow))) continue;
            slot.groupRegion = groupRooms.contains(slot.regionId);
            slots.add(slot);
        }

        int anchorRegion = -1;
        int anchorWalk   = -1;
        if (facts.anchorSpawnIndex >= 0 && facts.anchorSpawnIndex < spawnPoints.size()) {
            EnemySpawnPoint anchor = spawnPoints.get(facts.anchorSpawnIndex);
            anchorRegion = survey.regionAt(anchor.tileColumn, anchor.tileRow);
            anchorWalk   = regionWalkDistance(slots, survey, anchorRegion, anchor);
            // C2: when the anchor group fills its room (no free slot in it), offer the anchor's OWN tile —
            // the reward WEAPON (never a grid-stamped pickup) then lies under the guardian, claimed once it is down.
            if (!hasSlotInRegion(slots, anchorRegion)) {
                int walk = survey.walkDistanceWithKeycards(anchor.tileColumn, anchor.tileRow);
                if (walk >= 0) {
                    int noKeycardWalk = survey.walkDistanceWithoutKeycard(anchor.tileColumn, anchor.tileRow);
                    SupplySlot anchorSlot = new SupplySlot(anchor.tileColumn, anchor.tileRow, anchorRegion, walk,
                            noKeycardWalk >= 0, survey.isOnExitPath(anchor.tileColumn, anchor.tileRow));
                    anchorSlot.groupRegion = true;
                    anchorSlot.weaponOnly  = true;
                    slots.add(anchorSlot);
                }
            }
        }

        // --- Plan + place.
        int bossEffectiveHitPoints = spec.bossArenaAmmo() ? BossBalance.statsForDepth(depth).effectiveHitPoints : 0;
        SupplyPlan plan = SupplyPlanner.plan(new SupplyRequest(depth, spec, roster,
                GameMath.expectedPlayerAtDepth(depth),
                config == null ? null : config.carriedAmmoTypes, seed,
                config != null && config.weaponCadenceDue, bossEffectiveHitPoints));
        SupplyPlacement placement = SupplyPlanner.place(plan, slots, carriers, anchorRegion, anchorWalk,
                survey.maximumKeycardFreeDistance() / 2, seed);

        // --- Stamp.
        List<EnemySpawnPoint>  stampedSpawns = new ArrayList<>(spawnPoints);
        List<WeaponSpawnPoint> weapons       = new ArrayList<>();
        List<CreditSpawnPoint> credits       = new ArrayList<>();
        for (SupplyPlacement.GroundPlacement ground : placement.ground()) {
            PlannedPickup pickup = ground.pickup;
            switch (pickup.category) {
                case WEAPON:
                    weapons.add(new WeaponSpawnPoint(ground.tileColumn, ground.tileRow, pickup.weaponType,
                            true, pickup.weaponLevelOffset, pickup.weaponTierBonus));
                    break;
                case CREDITS:
                    credits.add(new CreditSpawnPoint(ground.tileColumn, ground.tileRow, pickup.creditAmount));
                    break;
                default:
                    grid[ground.tileRow][ground.tileColumn] = pickup.symbol;
                    break;
            }
        }
        for (SupplyPlacement.CarrierAssignment assignment : placement.carriers()) {
            EnemySpawnPoint spawn = stampedSpawns.get(assignment.spawnIndex);
            stampedSpawns.set(assignment.spawnIndex, spawn.withCarriedDrop(assignment.pickup.symbol));
        }

        // --- HAZARDS (balance-overhaul order 4, H14): explosive barrels beside groups, on COMBAT / ELITE only,
        // on tiles no spawn, pickup, weapon or credit owns.
        Set<Long> reservedTiles = new HashSet<>(occupied);
        for (WeaponSpawnPoint weapon : weapons) reservedTiles.add(tileKey(weapon.tileColumn, weapon.tileRow));
        for (CreditSpawnPoint credit : credits) reservedTiles.add(tileKey(credit.tileColumn, credit.tileRow));
        GroupBarrelPlacer.Outcome barrels =
                GroupBarrelPlacer.place(grid, survey, stampedSpawns, reservedTiles, spec, seed);
        int barrelCount = barrels.barrels;

        // --- PASSAGES: the last write to the grid. No solid prop or column may plug a one-tile-wide passage.
        int pluggedPassagesCleared = clearPluggedPassages(grid);

        FloorContentReport report = buildReport(generatorName, spec, depth, survey, stampedSpawns, facts,
                anchorRegion, anchorWalk, plan, placement);
        report.attachBarrels(barrels.barrels, barrels.eligibleRooms, barrels.roomsServed);
        report.attachPluggedPassagesCleared(pluggedPassagesCleared);
        return new Result(stampedSpawns, weapons, credits, report, barrelCount);
    }

    private static FloorContentReport buildReport(String generatorName, NodeSupplySpec spec, int depth,
                                                  SupplySlotSurvey survey, List<EnemySpawnPoint> spawns,
                                                  EncounterFacts facts, int anchorRegion, int anchorWalk,
                                                  SupplyPlan plan, SupplyPlacement placement) {
        Map<Integer, Integer> sizes = new HashMap<>();
        int enemies = 0;
        for (EnemySpawnPoint spawn : spawns) {
            if (EnemyType.fromSpawnChar(spawn.spawnChar) == null) continue;
            enemies++;
            if (spawn.groupId >= 0) sizes.merge(spawn.groupId, 1, Integer::sum);
        }
        List<Integer> groupIds = new ArrayList<>(sizes.keySet());
        Collections.sort(groupIds);
        int[] groupSizes = new int[groupIds.size()];
        int lone = 0;
        for (int index = 0; index < groupIds.size(); index++) {
            groupSizes[index] = sizes.get(groupIds.get(index));
            if (groupSizes[index] == 1) lone++;
        }
        int startRegion = survey.startRegion();
        int firstContact = -1;
        boolean inStart = false;
        for (EnemySpawnPoint spawn : spawns) {
            if (EnemyType.fromSpawnChar(spawn.spawnChar) == null) continue;
            if (spawn.groupId < 0) {
                lone++;
                continue;
            }
            if (sizes.get(spawn.groupId) < 2) continue;
            int walk = survey.walkDistanceWithKeycards(spawn.tileColumn, spawn.tileRow);
            if (walk >= 0 && (firstContact < 0 || walk < firstContact)) firstContact = walk;
            if (startRegion >= 0 && survey.regionAt(spawn.tileColumn, spawn.tileRow) == startRegion) inStart = true;
        }
        // V7 (mixed-encounter-groups): the most enemies of ONE archetype standing in any one room (corridor
        // tiles are not a room and are not counted).
        Map<Integer, Map<Character, Integer>> archetypesByRoom = new HashMap<>();
        int roomMaximumSameArchetype = 0;
        for (EnemySpawnPoint spawn : spawns) {
            if (EnemyType.fromSpawnChar(spawn.spawnChar) == null) continue;
            int room = survey.regionAt(spawn.tileColumn, spawn.tileRow);
            if (room < 0) continue;
            int count = archetypesByRoom.computeIfAbsent(room, key -> new HashMap<>())
                    .merge(spawn.spawnChar, 1, Integer::sum);
            roomMaximumSameArchetype = Math.max(roomMaximumSameArchetype, count);
        }
        return new FloorContentReport(generatorName, spec, depth, survey.walkableTileCount(),
                facts.targetWalkableTiles, enemies, facts.threatSpent, facts.threatCap, facts.bodyTarget,
                groupSizes, lone, firstContact, inStart, anchorRegion, anchorWalk, roomMaximumSameArchetype,
                plan, placement);
    }

    private static boolean hasSlotInRegion(List<SupplySlot> slots, int region) {
        if (region < 0) return true;
        for (SupplySlot slot : slots) if (slot.regionId == region) return true;
        return false;
    }

    /** The shortest walk into a room (from its slots), falling back to the anchor's own tile. */
    private static int regionWalkDistance(List<SupplySlot> slots, SupplySlotSurvey survey, int region,
                                          EnemySpawnPoint anchor) {
        int shortest = Integer.MAX_VALUE;
        if (region >= 0) {
            for (SupplySlot slot : slots) {
                if (slot.regionId == region) shortest = Math.min(shortest, slot.walkDistance);
            }
        }
        if (shortest == Integer.MAX_VALUE) {
            shortest = survey.walkDistanceWithKeycards(anchor.tileColumn, anchor.tileRow);
        }
        return shortest;
    }

    private static long tileKey(int tileColumn, int tileRow) {
        return ((long) tileRow << 32) | (tileColumn & 0xFFFFFFFFL);
    }

    /**
     * Final passage sweep, shared by every generator (they all finish through {@link #populate}). The
     * reachability repairs only guarantee that every tile can be reached SOMEHOW; a crate, barrel or
     * terminal dropped into a one-tile-wide corridor or a doorway-sized gap in a partition still plugs that
     * passage whenever a detour happens to exist. Every such prop/column is turned back into plain floor.
     * Removing a blocker never strands anything, so this cannot undo any earlier connectivity guarantee.
     * Returns how many solids it removed (the layout audit's backstop counter; target 0).
     */
    static int clearPluggedPassages(char[][] grid) {
        int removed = 0;
        for (int tileRow = 0; tileRow < grid.length; tileRow++) {
            for (int tileColumn = 0; tileColumn < grid[tileRow].length; tileColumn++) {
                char cell = grid[tileRow][tileColumn];
                if (!Level.isPropSolid(cell) && !Level.isColumn(cell)) continue;
                if (wouldPlugPassage(grid, tileColumn, tileRow)) {
                    grid[tileRow][tileColumn] = ' ';
                    removed++;
                }
            }
        }
        return removed;
    }

    /**
     * True when a solid tile at (tileColumn, tileRow) would sit INSIDE a one-tile-wide passage: blocked on
     * both sides along one axis and open on both sides along the other, so it is the only way through.
     * Blocked = wall, solid prop, column or off-grid; open = anything the player can step onto (doors too).
     */
    static boolean wouldPlugPassage(char[][] grid, int tileColumn, int tileRow) {
        boolean northOpen = isOpenForPassage(grid, tileColumn, tileRow + 1);
        boolean southOpen = isOpenForPassage(grid, tileColumn, tileRow - 1);
        boolean eastOpen  = isOpenForPassage(grid, tileColumn + 1, tileRow);
        boolean westOpen  = isOpenForPassage(grid, tileColumn - 1, tileRow);
        return (northOpen && southOpen && !eastOpen && !westOpen)
            || (eastOpen && westOpen && !northOpen && !southOpen);
    }

    private static boolean isOpenForPassage(char[][] grid, int tileColumn, int tileRow) {
        if (tileRow < 0 || tileRow >= grid.length || tileColumn < 0 || tileColumn >= grid[tileRow].length) {
            return false;
        }
        char cell = grid[tileRow][tileColumn];
        return !Level.isWall(cell) && !Level.isPropSolid(cell) && !Level.isColumn(cell);
    }
}
