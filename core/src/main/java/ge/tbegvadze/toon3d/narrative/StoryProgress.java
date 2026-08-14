package ge.tbegvadze.toon3d.narrative;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.StoryUiConstants;

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

    // ---- narrative-rework order-9: what a returning player is owed --------------------------------
    /** Whole hours since the last recorded session, computed ONCE at load (order-9 A). */
    private long                     hoursSinceLastSession;
    /** Codex ids most recently unlocked, newest first — the recap's "WHAT WE KNOW" (order-9 B). */
    private final List<String>       recentCodexUnlocks = new ArrayList<>();
    /** Critical beats closed before they could be read — the archive's shelf (order-9 C). */
    private final List<String>       missedBeatIds      = new ArrayList<>();

    /** Uses an in-memory store — nothing survives the process (tests, showcases). */
    public StoryProgress() {
        this(new InMemoryStoryProgressStore());
    }

    /** Loads the persisted deepest region from {@code store}; seen-flags are read lazily. */
    public StoryProgress(StoryProgressStore store) {
        if (store == null) throw new IllegalArgumentException("store must not be null");
        this.store = store;
        loadCachesFromStore();
    }

    /**
     * Reads every cached fact out of the store.  Shared by construction and {@link #reloadFromStore()}
     * so the two can never drift — a field added to one and forgotten in the other is a save that
     * reports one story on launch and a different one after a wipe.
     */
    private void loadCachesFromStore() {
        seenBeatIds.clear();
        deepestRegion = StoryRegion.fromOrdinal(store.loadDeepestRegionOrdinal());
        reprintCount  = Math.max(0, store.loadReprintCount());
        for (Stance stance : Stance.values()) {
            stanceValues[stance.ordinal()] = store.loadStance(stance.name());
        }
        // THE THREE-DAY PROBLEM (order-9 A). Measured once, HERE, and never again for the life of
        // this object: the gap is a property of the moment the save was opened, and re-measuring it
        // later (after the timestamp has been refreshed) would always report zero.
        hoursSinceLastSession = GameMath.hoursBetweenEpochMillis(store.loadLastSessionEpochMillis(),
                                                                 System.currentTimeMillis());
        readIdList(StoryUiConstants.STORY_RECENT_UNLOCK_RECORD_ID, recentCodexUnlocks);
        readIdList(StoryUiConstants.STORY_MISSED_BEAT_RECORD_ID,   missedBeatIds);
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

    // -------------------------------------------------------------------------
    // The ENDING the player committed to (Story UI order-8)
    // -------------------------------------------------------------------------

    /**
     * The {@code BootCardVariant} name of the ending this save reached, or null while the story is
     * unfinished.  The launch screen reads it back to print that ending's status lines instead of the
     * ordinary death notice — the framing every run has worn, subverted for good.
     *
     * <p>Filed through the consequential-outcome mechanism above, because an ending IS the ultimate
     * consequential outcome: no new persisted key, no schema bump, and a "new game" clears it with
     * everything else, which is exactly right — a wiped save has no ending to remember.
     */
    public String getEndingReached() {
        return getOutcome(StoryUiConstants.STORY_ENDING_OUTCOME_ID);
    }

    /** True once any ending has been committed to.  Never gates anything — the descent stays open. */
    public boolean hasReachedEnding() {
        return getEndingReached() != null;
    }

    /**
     * Records the ending the player just chose, permanently.  Written once, at the moment the choice
     * commits; a later run may overwrite it with a different ending, which is the honest record — the
     * save remembers how the story ended LAST, not every way it ever could have.
     */
    public void recordEndingReached(String endingVariantName) {
        if (endingVariantName == null) return;
        recordOutcome(StoryUiConstants.STORY_ENDING_OUTCOME_ID, endingVariantName);
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
        // Both unlock paths funnel through here, which is why the recap's "what we know" is recorded
        // here too: the alternative is a second table that a probe reward could quietly skip.
        pushOntoRing(recentCodexUnlocks, codexId, StoryUiConstants.STORY_RECAP_RECENT_ENTRY_COUNT,
                     StoryUiConstants.STORY_RECENT_UNLOCK_RECORD_ID);
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
    // Re-entry, the recap's inputs, and the missed-beat shelf (narrative-rework order-9)
    // -------------------------------------------------------------------------

    /**
     * Whole hours since the player was last here, measured when this object loaded (order-9 A).
     * Zero on a save that has never recorded a session, so a first-ever launch can never be read as
     * an absence.
     *
     * <p>It is a snapshot on purpose.  A run refreshes the stored timestamp as it goes, so asking
     * the store again later would always answer "no time at all" — the gap has to be captured at the
     * moment the save is opened or it is not captured at all.
     */
    public long getHoursSinceLastSession() {
        return hoursSinceLastSession;
    }

    /** True when the player has been away long enough to have genuinely lost the thread. */
    public boolean isReturningAfterALongGap() {
        return hoursSinceLastSession >= StoryUiConstants.STORY_REENTRY_GAP_HOURS;
    }

    /**
     * Records that the player is here NOW.  Called once per reprint card — often enough that a long
     * session cannot later be mistaken for an absence, rare enough to stay off any hot path.
     *
     * <p>Deliberately does NOT touch {@link #getHoursSinceLastSession()}: the gap this object
     * reports is the one it was loaded with, for its whole life.
     */
    public void noteSessionActivity() {
        store.saveLastSessionEpochMillis(System.currentTimeMillis());
    }

    /**
     * The archive entries unlocked most recently, newest first — the recap's WHAT WE KNOW block
     * (order-9 B).  Read-only, and never longer than
     * {@code STORY_RECAP_RECENT_ENTRY_COUNT}.
     */
    public List<String> getRecentCodexUnlocks() {
        return Collections.unmodifiableList(recentCodexUnlocks);
    }

    /**
     * The critical lines the player closed before they could have read them, newest first
     * (order-9 C).  Read-only, capped at {@code STORY_MISSED_SHELF_CAPACITY}.
     */
    public List<String> getMissedBeatIds() {
        return Collections.unmodifiableList(missedBeatIds);
    }

    /**
     * Files a critical beat the player dismissed unread, so the archive can offer it back.  This is
     * the ONLY concession the layer makes to a line that was already spent: it is recovered on an
     * OPT-IN screen, never re-delivered in play, because a story beat that comes back is a story
     * layer the player starts trying to escape.
     *
     * @return true when this beat was newly filed (it was not already on the shelf)
     */
    public boolean recordMissedBeat(String beatId) {
        if (beatId == null || beatId.isEmpty()) return false;
        if (missedBeatIds.contains(beatId))     return false;
        pushOntoRing(missedBeatIds, beatId, StoryUiConstants.STORY_MISSED_SHELF_CAPACITY,
                     StoryUiConstants.STORY_MISSED_BEAT_RECORD_ID);
        return true;
    }

    /**
     * Pushes an id onto the front of a capped, newest-first ring and writes the whole ring back.
     * A re-added id MOVES to the front rather than appearing twice, which is what keeps the recap
     * showing three distinct entries rather than the same one three times.
     */
    private void pushOntoRing(List<String> ring, String id, int capacity, String recordId) {
        ring.remove(id);
        ring.add(0, id);
        while (ring.size() > Math.max(1, capacity)) ring.remove(ring.size() - 1);
        writeIdList(recordId, ring);
    }

    /** Reads one packed ring out of the permanent string record into {@code target}. */
    private void readIdList(String recordId, List<String> target) {
        target.clear();
        String packed = store.loadOutcome(recordId);
        if (packed == null || packed.isEmpty()) return;
        int fieldStart = 0;
        while (fieldStart <= packed.length()) {
            int separatorIndex = packed.indexOf(StoryUiConstants.STORY_RECORD_LIST_SEPARATOR,
                                                fieldStart);
            int fieldEnd = separatorIndex < 0 ? packed.length() : separatorIndex;
            String id = packed.substring(fieldStart, fieldEnd);
            if (!id.isEmpty()) target.add(id);
            if (separatorIndex < 0) break;
            fieldStart = separatorIndex
                    + StoryUiConstants.STORY_RECORD_LIST_SEPARATOR.length();
        }
    }

    /** Packs one ring into a single permanent record.  Called on a change only, never per frame. */
    private void writeIdList(String recordId, List<String> ring) {
        StringBuilder packed = new StringBuilder();
        for (int idIndex = 0; idIndex < ring.size(); idIndex++) {
            if (idIndex > 0) packed.append(StoryUiConstants.STORY_RECORD_LIST_SEPARATOR);
            packed.append(ring.get(idIndex));
        }
        store.saveOutcome(recordId, packed.toString());
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
        // Re-read rather than zero field by field: the store is now empty, so this loads exactly the
        // state a brand-new save has — including the order-9 rings and the (absent) session stamp —
        // and cannot forget a field the way a hand-written reset eventually does.
        loadCachesFromStore();
    }

    /**
     * Re-reads everything cached in memory from the store.  Needed after the store is cleared behind
     * this object's back — a whole-file wipe ({@code StatsStore.wipeAllPersistentProgress()}) does
     * exactly that — so the in-memory copy cannot go on reporting a story the save no longer holds.
     */
    public void reloadFromStore() {
        loadCachesFromStore();
    }
}
