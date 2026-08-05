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
    private int                      reprintCount;
    /** The three hidden leanings (order-4), indexed by {@link Stance#ordinal()}.  Cached on load. */
    private final int[]              stanceValues = new int[Stance.values().length];

    /** Uses an in-memory store — nothing survives the process (tests, showcases). */
    public StoryProgress() {
        this(new InMemoryStoryProgressStore());
    }

    /** Loads the persisted deepest region from {@code store}; seen-flags are read lazily. */
    public StoryProgress(StoryProgressStore store) {
        if (store == null) throw new IllegalArgumentException("store must not be null");
        this.store         = store;
        this.deepestRegion = StoryRegion.fromOrdinal(store.loadDeepestRegionOrdinal());
        this.reprintCount  = Math.max(0, store.loadReprintCount());
        for (Stance stance : Stance.values()) {
            stanceValues[stance.ordinal()] = store.loadStance(stance.name());
        }
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

    /**
     * How many times the player has been printed, ever (Story UI order-3).  Zero before the first
     * reprint; the reprint card's "INSTANCE #0048" counter reads this.  Like the deepest region, it
     * only ever climbs — a death is what makes it climb, so nothing resets it.
     */
    public int getReprintCount() {
        return reprintCount;
    }

    /**
     * Records one more reprint and persists it immediately.  Called once per boot card presented,
     * so the number the player reads is the instance they are currently wearing.
     *
     * @return the new reprint count (1 on the very first run — in-fiction the original self has
     *         just died, which is why the first boot shows the same death notice as the hundredth)
     */
    public int recordReprint() {
        reprintCount++;
        store.saveReprintCount(reprintCount);
        return reprintCount;
    }

    // -------------------------------------------------------------------------
    // The hidden STANCE model (Story UI order-4)
    // -------------------------------------------------------------------------

    /**
     * How far the player leans one way, from every exchange they have ever answered.  Signed and
     * unbounded in principle; in practice it drifts by ones and twos over a whole campaign.
     *
     * <p>NEVER show this number, and never gate content on it.  It exists to flavour which lines
     * ORA offers and which ending the run resonates with — see {@link Stance}.
     */
    public int getStance(Stance stance) {
        if (stance == null) return 0;
        return stanceValues[stance.ordinal()];
    }

    /**
     * Nudges one leaning and persists it immediately.  Called once per answered option, so this is
     * a handful of writes across a whole run — never a per-frame path.  A zero nudge writes nothing.
     */
    public void nudgeStance(Stance stance, int amount) {
        if (stance == null || amount == 0) return;
        int updated = stanceValues[stance.ordinal()] + amount;
        stanceValues[stance.ordinal()] = updated;
        store.saveStance(stance.name(), updated);
    }

    /**
     * The leaning the player has expressed most, or null when nothing has been expressed yet or two
     * leanings are exactly tied.  A null dominant is a perfectly normal state — the caller must
     * always have a neutral fallback, because a stance may never make a moment go silent.
     */
    public Stance getDominantStance() {
        Stance dominant     = null;
        int    bestValue    = 0;
        boolean tiedAtBest  = false;
        for (Stance stance : Stance.values()) {
            int value = stanceValues[stance.ordinal()];
            if (value <= 0) continue;                 // a negative or unexpressed leaning never leads
            if (dominant == null || value > bestValue) {
                dominant   = stance;
                bestValue  = value;
                tiedAtBest = false;
            } else if (value == bestValue) {
                tiedAtBest = true;
            }
        }
        return tiedAtBest ? null : dominant;
    }

    // -------------------------------------------------------------------------
    // Consequential outcomes and codex unlocks (Story UI order-4)
    // -------------------------------------------------------------------------

    /** Which option the player chose for a consequential exchange, or null if never answered. */
    public String getOutcome(String exchangeId) {
        if (exchangeId == null) return null;
        return store.loadOutcome(exchangeId);
    }

    /** True when this consequential exchange has been answered, either way. */
    public boolean hasOutcome(String exchangeId) {
        return getOutcome(exchangeId) != null;
    }

    /**
     * Records how a consequential exchange was answered, permanently.  This is the "changes a
     * tracked outcome" half of order-4: order-5 (which ending fires) and order-8 (what surrounds it)
     * read it back, and a death never erases it.
     */
    public void recordOutcome(String exchangeId, String optionId) {
        if (exchangeId == null || optionId == null) return;
        store.saveOutcome(exchangeId, optionId);
    }

    /** True when a codex entry is already unlocked (order-6 reads this to list its entries). */
    public boolean isCodexUnlocked(String codexId) {
        if (codexId == null) return false;
        return store.isCodexUnlocked(codexId);
    }

    /** Unlocks a codex entry, permanently — the reward a PROBE option pays for curiosity. */
    public void unlockCodexEntry(String codexId) {
        if (codexId == null) return;
        if (store.isCodexUnlocked(codexId)) return;   // already known — no redundant write
        store.markCodexUnlocked(codexId);
    }

    /**
     * True once the player has actually OPENED a codex entry (order-6).  Unlocked-but-unopened is
     * what wears the archive's NEW dot, so this is deliberately a second flag rather than the unlock
     * itself: an entry earned by a bark the player barely glanced at should still invite a look.
     */
    public boolean isCodexEntryRead(String codexId) {
        if (codexId == null) return false;
        return store.isCodexEntryRead(codexId);
    }

    /** Marks a codex entry as read, permanently — clearing its NEW dot for good. */
    public void markCodexEntryRead(String codexId) {
        if (codexId == null) return;
        if (store.isCodexEntryRead(codexId)) return;  // already read — no redundant write
        store.markCodexEntryRead(codexId);
    }

    // -------------------------------------------------------------------------
    // "New game" (Story UI order-7 Part B)
    // -------------------------------------------------------------------------

    /**
     * Erases every scrap of narrative progress — in the store AND in this object's caches — so the
     * next line the player hears is the one a brand-new player hears.
     *
     * <p>This is the ONE operation allowed to move the story backwards, and it exists precisely
     * because everything else cannot: the deepest region, the one-shot flags, the reprint counter,
     * the hidden stance.  They all clear together, because a save with the region reset but the
     * one-shot beats still spent would start a story that can never be told.
     *
     * <p>Death does not call this.  Nothing in a run calls this.  Only an explicit "new game" does.
     */
    public void wipeAllNarrativeState() {
        store.wipeNarrativeState();
        seenBeatIds.clear();
        deepestRegion = StoryRegion.fromOrdinal(0);
        reprintCount  = 0;
        for (int stanceIndex = 0; stanceIndex < stanceValues.length; stanceIndex++) {
            stanceValues[stanceIndex] = 0;
        }
    }

    /**
     * Re-reads everything cached in memory from the store.  Needed after the store is cleared behind
     * this object's back — a whole-file wipe ({@code StatsStore.wipeAllPersistentProgress()}) does
     * exactly that — so the in-memory copy cannot go on reporting a story the save no longer holds.
     */
    public void reloadFromStore() {
        seenBeatIds.clear();
        deepestRegion = StoryRegion.fromOrdinal(store.loadDeepestRegionOrdinal());
        reprintCount  = Math.max(0, store.loadReprintCount());
        for (Stance stance : Stance.values()) {
            stanceValues[stance.ordinal()] = store.loadStance(stance.name());
        }
    }
}
