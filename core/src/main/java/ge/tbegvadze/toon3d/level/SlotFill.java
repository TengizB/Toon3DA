package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.util.BalanceConfig;

/**
 * How one slot of an {@link EncounterGroupTemplate} is filled (mixed-encounter-groups, V1/V2/V4): chaff
 * come in PAIRS of different archetypes, soldiers and heavies as SINGLES of different archetypes, and
 * the rare BROOD is the only slot that repeats one archetype past a pair. Per-constant data, never a
 * switch: the planner rolls, grows and shrinks a slot in whole {@link #membersPerArchetype()} units, so
 * a pair is never split.
 */
public enum SlotFill {

    /** Each archetype in the slot appears exactly GROUP_CHAFF_SLOT_MIN times; the archetypes differ. */
    PAIRS {
        @Override public int     membersPerArchetype() { return BalanceConfig.GROUP_CHAFF_SLOT_MIN; }
        @Override public boolean isBrood()             { return false; }
    },
    /** Every member is a different archetype. */
    SINGLES {
        @Override public int     membersPerArchetype() { return 1; }
        @Override public boolean isBrood()             { return false; }
    },
    /** The whole slot is ONE archetype, exactly GROUP_BROOD_SIZE of it — the owner's "by chance" nest. */
    BROOD {
        @Override public int     membersPerArchetype() { return BalanceConfig.GROUP_BROOD_SIZE; }
        @Override public boolean isBrood()             { return true; }
    };

    /** How many members each archetype drawn into the slot fields (the slot's step size). */
    public abstract int membersPerArchetype();

    /** Whether the slot is a BROOD (exempt from the per-group same-type cap and the behaviour mix). */
    public abstract boolean isBrood();
}
