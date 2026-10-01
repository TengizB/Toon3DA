package ge.tbegvadze.toon3d.level;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The {@link SupplyPlanner}'s decision for one floor (balance-overhaul order 2): the CONTINUOUS plan
 * per {@link SupplyCategory} (what the floor's demand and node spec ask for) and the discrete
 * {@link PlannedPickup}s that realise it (rounded to whole pickups, some handed to carriers). The
 * R-SUPPLY audit compares the two, and the placement against both. Immutable.
 */
public final class SupplyPlan {

    public final int   depth;
    /** Sum of the roster's effective HP at depth — the S2 demand. */
    public final float rosterEffectiveHitPoints;
    /** Modelled incoming damage as a fraction of the expected player's max HP (S3). */
    public final float incomingFraction;
    /** The S4 heal floor this floor carries (0 when exempt), in fractions of max HP. */
    public final float healFloorValue;

    private final Map<SupplyCategory, Float> plannedValue;
    private final List<PlannedPickup>        pickups;

    SupplyPlan(int depth, float rosterEffectiveHitPoints, float incomingFraction, float healFloorValue,
               Map<SupplyCategory, Float> plannedValue, List<PlannedPickup> pickups) {
        this.depth                    = depth;
        this.rosterEffectiveHitPoints = rosterEffectiveHitPoints;
        this.incomingFraction         = incomingFraction;
        this.healFloorValue           = healFloorValue;
        this.plannedValue             = Collections.unmodifiableMap(new EnumMap<>(plannedValue));
        this.pickups                  = Collections.unmodifiableList(new java.util.ArrayList<>(pickups));
    }

    /** The continuous plan for a category, before rounding to whole pickups. */
    public float plannedValue(SupplyCategory category) {
        Float value = plannedValue.get(category);
        return value == null ? 0f : value;
    }

    /** Every discrete pickup this floor carries (ground and carried). */
    public List<PlannedPickup> pickups() {
        return pickups;
    }

    /** Total value of the discrete pickups of a category (ground + carried). */
    public float roundedValue(SupplyCategory category) {
        float total = 0f;
        for (PlannedPickup pickup : pickups) {
            if (pickup.category == category) total += pickup.value;
        }
        return total;
    }

    /** Number of discrete pickups of a category (ground + carried). */
    public int count(SupplyCategory category) {
        int count = 0;
        for (PlannedPickup pickup : pickups) {
            if (pickup.category == category) count++;
        }
        return count;
    }

    /** The largest single pickup value of a category (0 when none) — the S5 rounding grain. */
    public float largestPickupValue(SupplyCategory category) {
        float largest = 0f;
        for (PlannedPickup pickup : pickups) {
            if (pickup.category == category) largest = Math.max(largest, pickup.value);
        }
        return largest;
    }
}
