package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.route.NodeSupplySpec;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One shape of enemy GROUP (balance-overhaul order 2, E2): a few role slots that spawn together as one
 * unit in one room. Registered as data in {@link EncounterGroupTemplateRegistry} by
 * {@link EncounterGroupTemplates#registerAll} — adding a group shape is one {@code register()} row, never
 * a switch. Immutable.
 */
public final class EncounterGroupTemplate {

    /** One slot: a role, how many members fill it, and how they are filled (pairs, singles, a brood). */
    public static final class Slot {
        public final EncounterGroupRole role;
        public final int                minimumCount;
        public final int                maximumCount;
        /** How the slot's members are drawn; counts are whole multiples of its step. */
        public final SlotFill           fill;

        public Slot(EncounterGroupRole role, int minimumCount, int maximumCount, SlotFill fill) {
            if (minimumCount < 1 || maximumCount < minimumCount) {
                throw new IllegalArgumentException("slot count range " + minimumCount + ".." + maximumCount);
            }
            this.fill         = Objects.requireNonNull(fill, "fill");
            int step = fill.membersPerArchetype();
            if (minimumCount % step != 0 || maximumCount % step != 0) {
                throw new IllegalArgumentException("slot count range " + minimumCount + ".." + maximumCount
                        + " is not a multiple of " + fill + "'s step " + step);
            }
            this.role         = Objects.requireNonNull(role, "role");
            this.minimumCount = minimumCount;
            this.maximumCount = maximumCount;
        }

        /** Fewest archetypes the slot draws. */
        public int minimumArchetypes() { return minimumCount / fill.membersPerArchetype(); }

        /** Most archetypes the slot draws. */
        public int maximumArchetypes() { return maximumCount / fill.membersPerArchetype(); }
    }

    private final String                            id;
    private final List<Slot>                        slots;
    private final int                               minimumDepth;
    private final Set<NodeSupplySpec.EncounterKind> kinds;
    private final boolean                           anchorCapable;
    private final boolean                           lone;
    private final float                             weight;
    private final int                               maximumPerFloor;

    public EncounterGroupTemplate(String id, List<Slot> slots, int minimumDepth,
                                  Set<NodeSupplySpec.EncounterKind> kinds, boolean anchorCapable, boolean lone,
                                  float weight) {
        this(id, slots, minimumDepth, kinds, anchorCapable, lone, weight, 0);
    }

    /** As above, with at most {@code maximumPerFloor} groups of this shape per floor (0 = unlimited). */
    public EncounterGroupTemplate(String id, List<Slot> slots, int minimumDepth,
                                  Set<NodeSupplySpec.EncounterKind> kinds, boolean anchorCapable, boolean lone,
                                  float weight, int maximumPerFloor) {
        this.maximumPerFloor = Math.max(0, maximumPerFloor);
        this.id            = Objects.requireNonNull(id, "id");
        this.slots         = Collections.unmodifiableList(new java.util.ArrayList<>(slots));
        this.minimumDepth  = Math.max(1, minimumDepth);
        this.kinds         = Collections.unmodifiableSet(EnumSet.copyOf(kinds));
        this.anchorCapable = anchorCapable;
        this.lone          = lone;
        this.weight        = weight;
        if (this.slots.isEmpty()) throw new IllegalArgumentException("template " + id + " has no slots");
    }

    public String     id()            { return id; }
    public List<Slot> slots()         { return slots; }
    /** First depth this shape may appear on. */
    public int        minimumDepth()  { return minimumDepth; }
    /** Whether the shape may be a floor's ANCHOR group (ESCORT, WARBAND). */
    public boolean    anchorCapable() { return anchorCapable; }
    /** A one-member HUNTER group (capped at GROUP_HUNTER_MAX_FRACTION of a floor's groups). */
    public boolean    lone()          { return lone; }
    /** Relative selection weight among eligible shapes. */
    public float      weight()        { return weight; }

    /** At most this many groups of this shape on one floor (0 = unlimited). */
    public int        maximumPerFloor() { return maximumPerFloor; }

    /** A BROOD shape (V4): rolled once per floor, never topped up, never first contact when avoidable. */
    public boolean brood() {
        for (Slot slot : slots) {
            if (slot.fill.isBrood()) return true;
        }
        return false;
    }

    /** Whether this shape may appear on a floor of this encounter kind at this depth. */
    public boolean isEligible(NodeSupplySpec.EncounterKind kind, int depth) {
        return kinds.contains(kind) && depth >= minimumDepth;
    }

    /** Fewest members the shape can field. */
    public int minimumSize() {
        int size = 0;
        for (Slot slot : slots) size += slot.minimumCount;
        return size;
    }

    /** Most members the shape can field. */
    public int maximumSize() {
        int size = 0;
        for (Slot slot : slots) size += slot.maximumCount;
        return size;
    }
}
