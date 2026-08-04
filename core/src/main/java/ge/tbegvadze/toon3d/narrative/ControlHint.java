package ge.tbegvadze.toon3d.narrative;

/**
 * The controls ORA teaches, one line each, the first time each one is actually needed (Story UI
 * order-5, "TUTORIAL THROUGH ORA").
 *
 * <p>There is no tutorial screen in this game and there is never going to be one.  A player who is
 * handed a diagram of twelve touch buttons has already stopped reading; a player who is told "tap
 * forward to step — everything good is down" by a voice that keeps talking to them for the rest of
 * the descent has been taught the control AND met the character in the same line.  Every entry here
 * is therefore a one-shot {@link BarkTrigger#CONTROL_HINT} bark keyed by {@link #name()}, exactly
 * like the first-sight-of-an-enemy-family lines are keyed by {@code EnemyFamily.name()}.
 *
 * <p>The trigger is the moment the control BECOMES USEFUL, never the moment the run starts: the
 * reload line waits for an empty magazine, the heal line waits for real damage with a medkit in the
 * bag, the switch-weapon line waits for a second gun.  A hint for a control the player cannot use
 * yet is noise, and noise is what makes people stop reading.
 *
 * <p>Headless: no LibGDX imports.
 */
public enum ControlHint {

    /** The very first thing: stepping, and the direction the whole game points in. */
    MOVE,
    /** An enemy has woken up and is coming — the first moment the fire button matters. */
    FIRE,
    /** The magazine just ran dry mid-floor. */
    RELOAD,
    /** The player is hurt and is carrying something that would fix it. */
    HEAL,
    /** A second gun is in the loadout, so cycling between them is now a decision. */
    SWITCH_WEAPON,
    /** There is something stashed worth opening the bag for. */
    INVENTORY;

    /**
     * The subject key this hint is registered and requested under.  Kept as an explicit accessor
     * (rather than callers reaching for {@link #name()}) so the catalog and the firing sites can
     * never drift apart.
     */
    public String getSubjectKey() {
        return name();
    }

    /** The short, stable token this hint uses inside catalog and localisation ids. */
    public String getCatalogKey() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
