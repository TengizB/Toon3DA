package ge.tbegvadze.toon3d.narrative;

/**
 * The persistence PORT for narrative state (Story UI order-2; the full schema is order-7 Part B).
 * Keeps {@link StoryProgress} headless: the narrative layer talks to this interface, and the engine
 * plugs in a LibGDX {@code Preferences}-backed implementation ({@code util/StoryStore}).  Tests use
 * {@link InMemoryStoryProgressStore}.
 *
 * <p>What order-2 needs to survive a death, an app restart and a whole new run:
 * <ul>
 *   <li>the DEEPEST story region ever reached — the gate every pool is selected through, so the
 *       story never rewinds on death;</li>
 *   <li>the set of one-shot beat ids already fired — so a cleared region never replays its
 *       intro lines on a later run.</li>
 * </ul>
 *
 * <p>Order-3 adds one more, for the reprint card's instance counter:
 * <ul>
 *   <li>the REPRINT COUNT — how many times the player has been printed, ever.  It only climbs, it
 *       is never reset by a death or a new run, and it is never celebrated; the number quietly
 *       growing over a long session is the whole point (story/dialog/system-cards.md).</li>
 * </ul>
 *
 * <p>Order-4 (interactive exchanges) adds three, all written only when the player answers something:
 * <ul>
 *   <li>the three hidden {@link Stance} leanings — who the player has been across every run.  A
 *       death must not wipe that, for the same reason it does not wipe the deepest region;</li>
 *   <li>the OUTCOME of each consequential exchange (which option id was chosen);</li>
 *   <li>the set of unlocked codex ids, so a probe answered two runs ago is still readable.</li>
 * </ul>
 *
 * <p>Implementations write on meaningful change (a beat seen, a region reached), never per frame.
 */
public interface StoryProgressStore {

    /** The persisted deepest-region ordinal, or 0 when nothing has been recorded yet. */
    int loadDeepestRegionOrdinal();

    /** Persists a new deepest-region ordinal.  Callers only ever pass a deeper value. */
    void saveDeepestRegionOrdinal(int regionOrdinal);

    /** True when this one-shot beat id has already fired (in any earlier run). */
    boolean isBeatSeen(String beatId);

    /** Records that a one-shot beat fired, so it never fires again. */
    void markBeatSeen(String beatId);

    /** The persisted reprint count, or 0 when the player has never been printed (order-3). */
    int loadReprintCount();

    /** Persists a new reprint count.  Callers only ever pass a higher value. */
    void saveReprintCount(int reprintCount);

    /**
     * The persisted value of one hidden {@link Stance} leaning, or 0 when the player has never
     * expressed it (order-4).  Signed: a leaning can be pushed either way.
     */
    int loadStance(String stanceName);

    /** Persists one leaning's new value.  Written when a choice is made, never per frame. */
    void saveStance(String stanceName, int value);

    /** The option id recorded for a CONSEQUENTIAL exchange, or null when it was never answered. */
    String loadOutcome(String exchangeId);

    /** Records which way a CONSEQUENTIAL exchange was answered.  Order-5 and order-8 read it back. */
    void saveOutcome(String exchangeId, String optionId);

    /** True when a codex entry has been unlocked (by a PROBE option, or later by order-6's pickups). */
    boolean isCodexUnlocked(String codexId);

    /** Records a codex entry as unlocked, permanently. */
    void markCodexUnlocked(String codexId);
}
