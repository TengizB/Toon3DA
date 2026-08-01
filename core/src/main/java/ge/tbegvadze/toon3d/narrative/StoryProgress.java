package ge.tbegvadze.toon3d.narrative;

import java.util.HashSet;
import java.util.Set;

/**
 * The player's PERSISTENT narrative state — the reconciliation between a linear story and a
 * permadeath roguelike (Story UI order-2; model owned by order-7 Part A).
 *
 * <p>Two facts live here, and both only ever move FORWARD:
 * <ul>
 *   <li>{@link #getDeepestRegion()} — the deepest {@link StoryRegion} ever reached, across all runs.
 *       Every bark pool is gated on this, never on the current run, so dying and restarting at the
 *       surface does NOT rewind ORA's tone, the planet's voice stage or the Organization's
 *       escalation.  This is the fiction, not a concession to it: the clone remembers, ORA is never
 *       wiped, and the planet never forgets you.</li>
 *   <li>{@link #hasSeen(String)} — the ids of one-shot beats already fired.  Re-entering a cleared
 *       region on a later run never replays its intro lines.</li>
 * </ul>
 *
 * <p>State is cached in memory and written through to a {@link StoryProgressStore} on meaningful
 * change only (a region reached, a beat seen) — never per frame.  Headless: no LibGDX imports.
 */
public final class StoryProgress {

    private final StoryProgressStore store;
    private final Set<String>        seenBeatIds = new HashSet<>();
    private StoryRegion              deepestRegion;

    /** Uses an in-memory store — nothing survives the process (tests, showcases). */
    public StoryProgress() {
        this(new InMemoryStoryProgressStore());
    }

    /** Loads the persisted deepest region from {@code store}; seen-flags are read lazily. */
    public StoryProgress(StoryProgressStore store) {
        if (store == null) throw new IllegalArgumentException("store must not be null");
        this.store         = store;
        this.deepestRegion = StoryRegion.fromOrdinal(store.loadDeepestRegionOrdinal());
    }

    /** The deepest story region ever reached.  Never null; never moves backwards. */
    public StoryRegion getDeepestRegion() {
        return deepestRegion;
    }

    /**
     * Records that the descent reached {@code region}.  Keeps the deeper of the two, so an out-of-order
     * route (or a fresh run starting at the surface) can never rewind the story.
     *
     * @return true when this call actually deepened the story (the caller may fire a first-time beat)
     */
    public boolean reachRegion(StoryRegion region) {
        StoryRegion deeper = StoryRegion.deeperOf(deepestRegion, region);
        if (deeper == deepestRegion) return false;
        deepestRegion = deeper;
        store.saveDeepestRegionOrdinal(deeper.ordinal());
        return true;
    }

    /** True when this one-shot beat has already fired (this session or any earlier run). */
    public boolean hasSeen(String beatId) {
        if (beatId == null) return false;
        if (seenBeatIds.contains(beatId)) return true;
        if (store.isBeatSeen(beatId)) {
            seenBeatIds.add(beatId);   // cache the hit so a repeat check costs no store read
            return true;
        }
        return false;
    }

    /** Marks a one-shot beat as fired and persists it immediately. */
    public void markSeen(String beatId) {
        if (beatId == null) return;
        if (!seenBeatIds.add(beatId)) return;   // already known — no redundant write
        store.markBeatSeen(beatId);
    }
}
