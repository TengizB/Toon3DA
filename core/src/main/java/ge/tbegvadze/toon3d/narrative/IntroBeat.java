package ge.tbegvadze.toon3d.narrative;

/**
 * ORA'S INTRODUCTION, as a schedule (narrative-rework order-2 D).
 *
 * <p>The game used to introduce the most important character in it with a name and nothing else —
 * "You're up. I'm ORA. I do the talking down here." — which is not an introduction, and left a
 * player who had just been told that an unexplained "instance" was "terminated" with a stranger
 * talking at them for the rest of the descent.  This enum is the fix: a fixed, ordered set of beats
 * that between them answer <em>who is talking, what happened to you, why your head is empty, and
 * what she is for</em>.
 *
 * <h3>Three doses at the start, the rest distributed</h3>
 * The COLD OPEN beats fire at {@link BarkTrigger#RUN_START}, three on the player's first run and
 * four more on their second, in the order declared here.  Everything after that hangs off a moment that
 * already happens on floor one — a door, a terminal, a pickup, the first quiet stretch — so the
 * player meets her by watching her be useful rather than by reading a biography.  A line about doors
 * arrives when a door does.
 *
 * <h3>Why these are subjects rather than a new trigger</h3>
 * Each beat is one row in {@link BarkCatalog}, keyed by {@link #getSubjectKey()} on an EXISTING
 * trigger — the same idiom the control hints ({@link ControlHint}) and the first-sight-of-a-family
 * lines use.  Every row is one-shot for the life of the save and {@link BarkPriority#STORY_CRITICAL},
 * because an introduction that loses a race with a flavour quip is an introduction that never
 * happened.  Adding one is still one {@code register()} call; nothing switches on this enum.
 *
 * <p>Headless: no LibGDX imports.
 */
public enum IntroBeat {

    /** Run 1, first panel: who is talking, and WHAT she is.  A name alone is not an introduction. */
    GREETING(BarkTrigger.RUN_START, 1),
    /** Run 1: what happened to the player, in plain words and no proper nouns. */
    WHAT_HAPPENED(BarkTrigger.RUN_START, 1),
    /**
     * Run 1: THE GAP.  This print came back short, and both of them put it down to printing noise.
     * It is the single sentence that licenses every explanation ORA gives for the rest of the game —
     * and, regions later, the thing that turns out to have been done on purpose (doctrine D3).
     */
    THE_GAP(BarkTrigger.RUN_START, 1),
    /**
     * Run 2: she does not get printed.  The second thing a player learns about her is the thing that
     * makes her matter, and it only lands once they have died and come back themselves.
     */
    CONTINUITY(BarkTrigger.RUN_START, 2),
    /** Run 2, straight after: which makes her the only one who remembers the last body. */
    MEMORY(BarkTrigger.RUN_START, 2),
    /**
     * Run 2: WHY THE DESCENT REPEATS (narrative-rework order-8, HOLE 1).  A roguelike player accepts
     * a reset without a reason; a story player asks, and until this line the game had no answer
     * anywhere on any channel.  They come back from a stored copy rather than from where they fell,
     * and that copy starts at the top — which is the premise as well as the loop.
     *
     * <p>Deliberately says WHERE they restart rather than naming the mechanism: the word for the
     * stored copy is {@link StoryTerm#CHECKPOINT} and it is not free until run 3, so this line has
     * to do its job in plain language (doctrine D2).  Run 2 is the earliest it can land at all,
     * because it only answers a question a player has actually asked once they have died.
     */
    LOOP_RESTART(BarkTrigger.RUN_START, 2),
    /**
     * Run 2, straight after: and neither does the facility stay cleared.  The other half of the
     * reset — the one that explains why the floors the player already fought through are full
     * again — which is otherwise the most visible unexplained thing in the game.
     */
    LOOP_REFILL(BarkTrigger.RUN_START, 2),

    /** The first door the player opens: what she does, and what she does not do. */
    DOORS(BarkTrigger.CONTROL_HINT, 1),
    /** The first thing the player picks up: what carrying anything is worth in a permadeath run. */
    CARRIED_ITEMS(BarkTrigger.CONTROL_HINT, 1),
    /**
     * The first quiet stretch of floor: what the number on the wake card is.  Deliberately not on
     * the card itself — a counter explains itself badly on the screen it appears on, and this is the
     * first moment the player has nothing else to do.
     */
    REPRINT_COUNTER(BarkTrigger.CONTROL_HINT, 1),
    /**
     * The first terminal read: she reads the facility's paperwork for you, and the first thing it
     * says is that forty-one people went down here and none came back.  Rides the ordinary LOG_FOUND
     * pool with no subject of its own, so it simply wins the first log of the game and then never
     * appears again.
     */
    PAPERWORK(BarkTrigger.LOG_FOUND, 1);

    private final BarkTrigger trigger;
    private final int         minimumReprintCount;
    private final String      subjectKey;

    IntroBeat(BarkTrigger trigger, int minimumReprintCount) {
        this.trigger             = trigger;
        this.minimumReprintCount = minimumReprintCount;
        // A beat that rides an existing POOL (the log take) carries no subject, so it competes in
        // that pool like any other row; every other beat is keyed by its own name.
        this.subjectKey = trigger == BarkTrigger.LOG_FOUND ? null : name();
    }

    /** The existing moment this beat hangs off.  No new trigger is introduced for any of them. */
    public BarkTrigger getTrigger() {
        return trigger;
    }

    /**
     * The subject key this beat is registered and requested under, or null when it competes in its
     * trigger's ordinary pool instead.  Kept as an accessor (rather than callers reaching for
     * {@link #name()}) so the catalog and the firing sites can never drift apart.
     */
    public String getSubjectKey() {
        return subjectKey;
    }

    /** The short, stable token this beat uses inside catalog and localisation ids. */
    public String getCatalogKey() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * The earliest instance number this beat may be spoken to.  Run 1 is print 1, so a beat that only
     * makes sense to somebody who has already died and come back declares 2.
     */
    public int getMinimumReprintCount() {
        return minimumReprintCount;
    }

    /** True when this beat is part of the run-start cold open rather than a distributed moment. */
    public boolean isColdOpen() {
        return trigger == BarkTrigger.RUN_START;
    }

    /** True when a save on its {@code reprintCount}-th print is far enough along to hear this. */
    public boolean isAvailableAt(int reprintCount) {
        return reprintCount >= minimumReprintCount;
    }
}
