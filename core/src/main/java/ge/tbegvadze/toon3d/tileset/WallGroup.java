package ge.tbegvadze.toon3d.tileset;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * A family of walls inside a region wall set (generated-wall-art order 2; guide section 6.3): the HALL
 * (halls, generic rooms, corridors, caverns), the six room families and the region's SIGNATURE look.
 * Room families are DATA: every RoomBlueprint (and every special generator) names its group in its
 * registration row — a new room is one row naming an existing group; a new family is one enum row here
 * plus a room set in every region's kit. Never a switch.
 *
 * <p>Per-constant data: the group's part of the stable sprite id, how many BASE variants every region
 * ships for it (guide 6.1: HALL 3, rooms 2), and which slots it carries. Headless — no LibGDX.
 */
public enum WallGroup {
    HALL       ("hall",        3, EnumSet.of(WallSlot.BASE, WallSlot.STRUCTURE, WallSlot.LIGHT, WallSlot.SERVICE,
                                             WallSlot.VENT, WallSlot.MARKER, WallSlot.BREACH)),
    MEDICAL    ("medical",     2, EnumSet.of(WallSlot.BASE, WallSlot.ACCENT)),
    SECURITY   ("security",    2, EnumSet.of(WallSlot.BASE, WallSlot.ACCENT)),
    CONTAINMENT("containment", 2, EnumSet.of(WallSlot.BASE, WallSlot.ACCENT)),
    MACHINERY  ("machinery",   2, EnumSet.of(WallSlot.BASE, WallSlot.ACCENT)),
    RESEARCH   ("research",    2, EnumSet.of(WallSlot.BASE, WallSlot.ACCENT)),
    STORAGE    ("storage",     2, EnumSet.of(WallSlot.BASE, WallSlot.ACCENT)),
    SIGNATURE  ("signature",   2, EnumSet.of(WallSlot.BASE, WallSlot.ACCENT));

    private final String        idToken;
    private final int           baseVariantCount;
    private final Set<WallSlot> slots;

    WallGroup(String idToken, int baseVariantCount, EnumSet<WallSlot> slots) {
        this.idToken          = idToken;
        this.baseVariantCount = baseVariantCount;
        this.slots            = Collections.unmodifiableSet(slots);
    }

    public String idToken() {
        return idToken;
    }

    /** BASE variants every region ships for this group (the renderer picks one per face). */
    public int baseVariantCount() {
        return baseVariantCount;
    }

    /** The slots a full region set carries for this group. */
    public Set<WallSlot> slots() {
        return slots;
    }

    /** True for a room family (anything but the HALL): rule P2 stamps its BASE on a room perimeter. */
    public boolean isRoomFamily() {
        return this != HALL;
    }
}
