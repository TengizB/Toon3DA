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
}
