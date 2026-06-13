package ge.tbegvadze.toon3d.status;

/**
 * A live status effect instance stored inside a host's EnumMap.
 * One instance per StatusType per host — pre-allocated, never replaced, only mutated.
 * Call isActive() before reading any other field.
 */
public final class StatusEffect {

    final StatusType type;

    /** Turns remaining; 0 or negative = expired (inactive). Decrements once per world turn. */
    int remainingTurns;

    /** Damage per turn for DoT effects; damage bonus percent for Empowered; unused for control effects. */
    int magnitude;

    /** Active poison stack count (STACK_MAGNITUDE effects only). Capped at POISON_MAX_STACKS. */
    int stacks;

    /** Who or what applied the effect — used for kill attribution / XP routing on DoT kills. */
    Object source;

    StatusEffect(StatusType type) {
        this.type = type;
    }

    public boolean isActive()          { return remainingTurns > 0; }
    public int getRemainingTurns()     { return remainingTurns; }
    public int getMagnitude()          { return magnitude; }
    public int getStacks()             { return stacks; }
    public StatusType getType()        { return type; }

    void reset() {
        remainingTurns = 0;
        stacks         = 0;
        magnitude      = 0;
        source         = null;
    }
}
