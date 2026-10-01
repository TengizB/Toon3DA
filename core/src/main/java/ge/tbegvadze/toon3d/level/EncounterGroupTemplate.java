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

    /** One slot: a role, how many members fill it, and whether they all share one archetype. */
    public static final class Slot {
        public final EncounterGroupRole role;
        public final int                minimumCount;
        public final int                maximumCount;
        /** Every member of the slot is the same archetype (a pack, a chaff screen). */
        public final boolean            sameType;

        public Slot(EncounterGroupRole role, int minimumCount, int maximumCount, boolean sameType) {
            if (minimumCount < 1 || maximumCount < minimumCount) {
                throw new IllegalArgumentException("slot count range " + minimumCount + ".." + maximumCount);
            }
            this.role         = Objects.requireNonNull(role, "role");
            this.minimumCount = minimumCount;
            this.maximumCount = maximumCount;
            this.sameType     = sameType;
        }
    }

    private final String                            id;
    private final List<Slot>                        slots;
    private final int                               minimumDepth;
    private final Set<NodeSupplySpec.EncounterKind> kinds;
    private final boolean                           anchorCapable;
    private final boolean                           lone;
    private final float                             weight;

    public EncounterGroupTemplate(String id, List<Slot> slots, int minimumDepth,
                                  Set<NodeSupplySpec.EncounterKind> kinds, boolean anchorCapable, boolean lone,
                                  float weight) {
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
