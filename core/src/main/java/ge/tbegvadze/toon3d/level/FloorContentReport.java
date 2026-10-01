package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.route.NodeSupplySpec;

/**
 * What the shared {@link FloorPopulator} planned and placed on one generated floor (balance-overhaul
 * order 2): the footprint, the encounter (bodies, groups, first contact, threat) and the supply (plan
 * and placement). Attached to the {@link Level}; read by the R-SUPPLY / R-DENSITY audit sweep, the
 * BalanceReport SUPPLY/DENSITY table and the sim FLOOR REPORT. Pure data, immutable.
 */
public final class FloorContentReport {

    /** Which generator built the floor (its stable id, e.g. "rooms_mst"). */
    public final String          generatorName;
    public final NodeSupplySpec  spec;
    public final int             depth;
    /** Walkable tiles (not wall, not solid prop) — the E6 footprint measure. */
    public final int             walkableTiles;
    /** The E6 target the generator built toward, or 0 when it had none. */
    public final int             targetWalkableTiles;
    public final int             enemyCount;
    /** Threat Points the roster spent, and the cap it was planned against (E1). */
    public final float           threatSpent;
    public final float           threatCap;
    /** The E1 body target the encounter planner filled toward (0 when not planned by body target). */
    public final int             bodyTarget;
    /** Member count of every encounter group (E2), in group-id order; empty when groups are not tracked. */
    public final int[]           groupSizes;
    /** Enemies standing alone (a group of one, or untagged). */
    public final int             loneEnemies;
    /** Walk tiles from the start to the nearest member of a group of >= 2, or -1 when there is none (E5). */
    public final int             firstContactWalkTiles;
    /** Whether any member of a group of >= 2 stands in the start room (E5 forbids it). */
    public final boolean         groupInStartRegion;
    /** The anchor group's room id, or -1 (C2). */
    public final int             anchorRegionId;
    /** The shortest walk into the anchor group's room, or -1 (C2: "in or past its room"). */
    public final int             anchorWalkDistance;
    public final SupplyPlan      plan;
    public final SupplyPlacement placement;

    FloorContentReport(String generatorName, NodeSupplySpec spec, int depth, int walkableTiles,
                       int targetWalkableTiles, int enemyCount, float threatSpent, float threatCap,
                       int bodyTarget, int[] groupSizes, int loneEnemies, int firstContactWalkTiles,
                       boolean groupInStartRegion, int anchorRegionId, int anchorWalkDistance,
                       SupplyPlan plan, SupplyPlacement placement) {
        this.generatorName         = generatorName;
        this.spec                  = spec;
        this.depth                 = depth;
        this.walkableTiles         = walkableTiles;
        this.targetWalkableTiles   = targetWalkableTiles;
        this.enemyCount            = enemyCount;
        this.threatSpent           = threatSpent;
        this.threatCap             = threatCap;
        this.bodyTarget            = bodyTarget;
        this.groupSizes            = groupSizes.clone();
        this.loneEnemies           = loneEnemies;
        this.firstContactWalkTiles = firstContactWalkTiles;
        this.groupInStartRegion    = groupInStartRegion;
        this.anchorRegionId        = anchorRegionId;
        this.anchorWalkDistance    = anchorWalkDistance;
        this.plan                  = plan;
        this.placement             = placement;
    }

    /** Enemies per 100 walkable tiles (E7). */
    public float density() {
        return ge.tbegvadze.toon3d.util.GameMath.densityPerHundredTiles(enemyCount, walkableTiles);
    }

    /** Number of encounter groups. */
    public int groupCount() {
        return groupSizes.length;
    }

    /** Groups with at least {@code minimumSize} members. */
    public int groupsOfAtLeast(int minimumSize) {
        int count = 0;
        for (int size : groupSizes) if (size >= minimumSize) count++;
        return count;
    }

    /** Enemies standing in groups of two or more. */
    public int groupedEnemies() {
        return Math.max(0, enemyCount - loneEnemies);
    }
}
