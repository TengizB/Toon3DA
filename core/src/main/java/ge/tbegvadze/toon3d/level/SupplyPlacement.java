package ge.tbegvadze.toon3d.level;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a {@link SupplyPlan}'s pickups actually landed (balance-overhaul order 2): ground placements,
 * carrier assignments, and anything that found no home. The floor populator stamps it into the level;
 * the R-SUPPLY audit and the sim FLOOR REPORT read its measurements. Immutable.
 */
public final class SupplyPlacement {

    /** One pickup placed on a ground tile. */
    public static final class GroundPlacement {
        public final PlannedPickup pickup;
        public final int           tileColumn;
        public final int           tileRow;
        public final int           regionId;
        public final int           walkDistance;
        public final boolean       reachableWithoutKeycard;

        GroundPlacement(PlannedPickup pickup, SupplySlot slot) {
            this.pickup                  = pickup;
            this.tileColumn              = slot.tileColumn;
            this.tileRow                 = slot.tileRow;
            this.regionId                = slot.regionId;
            this.walkDistance            = slot.walkDistance;
            this.reachableWithoutKeycard = slot.reachableWithoutKeycard;
        }
    }

    /** One pickup handed to a planned enemy as its drop (S6). */
    public static final class CarrierAssignment {
        /** Index into the floor's enemy spawn list. */
        public final int           spawnIndex;
        public final PlannedPickup pickup;

        CarrierAssignment(int spawnIndex, PlannedPickup pickup) {
            this.spawnIndex = spawnIndex;
            this.pickup     = pickup;
        }
    }

    private final List<GroundPlacement>   ground;
    private final List<CarrierAssignment> carriers;
    private final List<PlannedPickup>     unplaced;
    private final int                     halfDistance;

    SupplyPlacement(List<GroundPlacement> ground, List<CarrierAssignment> carriers,
                    List<PlannedPickup> unplaced, int halfDistance) {
        this.ground       = Collections.unmodifiableList(new java.util.ArrayList<>(ground));
        this.carriers     = Collections.unmodifiableList(new java.util.ArrayList<>(carriers));
        this.unplaced     = Collections.unmodifiableList(new java.util.ArrayList<>(unplaced));
        this.halfDistance = halfDistance;
    }

    public List<GroundPlacement>   ground()   { return ground; }
    public List<CarrierAssignment> carriers() { return carriers; }
    /** Pickups that found neither a slot nor a carrier (should be empty; the audit fails otherwise). */
    public List<PlannedPickup>     unplaced() { return unplaced; }
    /** The S4 "first half of the floor" walk distance this placement used. */
    public int                     halfDistance() { return halfDistance; }

    /** Total placed value of a category (ground + carriers). */
    public float placedValue(SupplyCategory category) {
        float total = 0f;
        for (GroundPlacement placement : ground) {
            if (placement.pickup.category == category) total += placement.pickup.value;
        }
        for (CarrierAssignment assignment : carriers) {
            if (assignment.pickup.category == category) total += assignment.pickup.value;
        }
        return total;
    }

    /** Heal-floor value placed keycard-free on the ground (S4). */
    public float keycardFreeHealFloorValue() {
        float total = 0f;
        for (GroundPlacement placement : ground) {
            if (placement.pickup.healFloor && placement.reachableWithoutKeycard) total += placement.pickup.value;
        }
        return total;
    }

    /** Heal-floor value placed keycard-free within the first half of the floor by walk distance (S4). */
    public float earlyHealFloorValue() {
        float total = 0f;
        for (GroundPlacement placement : ground) {
            if (placement.pickup.healFloor && placement.reachableWithoutKeycard
                    && placement.walkDistance <= halfDistance) {
                total += placement.pickup.value;
            }
        }
        return total;
    }

    /**
     * The largest COUNT share any single room holds of a category's ground pickups (S7). The denominator
     * is EVERY ground pickup of the category — corridor (connector) pickups included, since they are
     * part of the category even though no room owns them — and the numerator the best room. A room that
     * holds exactly one pickup is never a violation — with two pickups on a floor, "35%" cannot be
     * met by anything but one each — so the caller pairs this with {@link #maximumRoomCount}.
     */
    public float maximumRoomShare(SupplyCategory category) {
        int total = 0;
        Map<Integer, Integer> byRegion = new HashMap<>();
        for (GroundPlacement placement : ground) {
            if (placement.pickup.category != category) continue;
            total++;
            if (placement.regionId < 0) continue;
            byRegion.merge(placement.regionId, 1, Integer::sum);
        }
        if (total == 0) return 0f;
        int largest = 0;
        for (int count : byRegion.values()) largest = Math.max(largest, count);
        return largest / (float) total;
    }

    /** The most ground pickups of a category any single room holds. */
    public int maximumRoomCount(SupplyCategory category) {
        Map<Integer, Integer> byRegion = new HashMap<>();
        int largest = 0;
        for (GroundPlacement placement : ground) {
            if (placement.pickup.category != category || placement.regionId < 0) continue;
            largest = Math.max(largest, byRegion.merge(placement.regionId, 1, Integer::sum));
        }
        return largest;
    }
}
