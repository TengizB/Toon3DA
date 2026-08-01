package ge.tbegvadze.toon3d.narrative;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * The BARK LAYER (Story UI order-2) — the always-on, NON-BLOCKING storytelling channel and the
 * headless brain behind it.  The game keeps running while a bark is on screen; the player keeps
 * moving and tapping; the line auto-dismisses.  A bark never has choices (those are order-4
 * exchanges) — it is read-and-forget.
 *
 * <p>This class owns every narrative decision and NO rendering: which line fires for a moment,
 * whether it is allowed to fire at all, the queue, and the fade/hold clock.  {@code
 * render/StoryBarkRenderer} just draws whatever is active.  Headless (no LibGDX render state), so
 * all of it is unit-testable.
 *
 * <h3>The rules it enforces</h3>
 * <ul>
 *   <li><b>Region gating.</b> Candidates are filtered by {@link StoryProgress#getDeepestRegion()} —
 *       the deepest region ever reached, which is persistent.  That is what shifts ORA from
 *       corporate-cheerful to frightened ally, and grows the planet from silence to address, with no
 *       conditional in game code.  Death never rewinds it.</li>
 *   <li><b>One-shot beats.</b> A row marked one-shot is marked seen when DELIVERED and never fires
 *       again, in this run or any later one.</li>
 *   <li><b>One at a time, and NEVER on a timer.</b> Exactly one bark on screen, ever, and it stays
 *       there until the PLAYER dismisses it ({@link #dismissActiveBark()} — the panel's X or a
 *       swipe).  Nothing, not even a mandatory beat, cuts a line short: a player must never lose a
 *       line they were still reading.  Everything else waits in the queue.</li>
 *   <li><b>No repeats.</b> A line said within the last {@code STORY_BARK_RECENT_MEMORY} barks is
 *       not eligible; if that empties a moment's pool, the moment stays silent.</li>
 *   <li><b>Rate limit.</b> At most one delivery per {@code STORY_BARK_MIN_INTERVAL_SECONDS} of
 *       FREE screen (the clock runs only while no bark is up, so reading slowly never causes a
 *       pile-up), plus a per-trigger cooldown, so kills and low health never chatter.</li>
 *   <li><b>Tone balance.</b> Rows carry a {@link BarkTone}; lore outweighs levity by weight, so ORA
 *       is dry occasionally rather than constantly.</li>
 *   <li><b>Priority.</b> STORY_CRITICAL is never dropped; REACTIVE is dropped when the queue is
 *       full; FLAVOR is dropped the moment there is any pressure at all.</li>
 *   <li><b>Suppression.</b> While a hard-pause overlay owns the screen (order-7 Part E) the system
 *       is suppressed: requests still queue, nothing is delivered or aged, and the queue resumes
 *       intact afterwards.</li>
 * </ul>
 *
 * <p>ALLOCATION: the queue is fixed-capacity parallel arrays and the wrapped-line buffer is
 * pre-allocated, so the per-frame path allocates nothing.  Resolving + wrapping a delivered line
 * allocates its strings once, on the update thread, at delivery — never in render().
 */
public final class BarkSystem {

    private final BarkRegistry  registry;
    private final StoryStrings  strings;
    private final StoryProgress progress;
    private final Random        random;

    // ---- pending queue (fixed capacity; index 0 is the oldest entry) --------------------------
    private final BarkDefinition[] queuedDefinitions;
    private final float[]          queuedAgeSeconds;
    private int                    queuedCount;

    // ---- the one bark currently on screen ------------------------------------------------------
    private BarkDefinition activeDefinition;
    private String         activeSpeakerName;
    private final String[] activeLines = new String[StoryUiConstants.STORY_BARK_MAX_LINES];
    private int            activeLineCount;
    private float          activeElapsedSeconds;
    private boolean        activeDismissed;
    private float          activeDismissElapsedSeconds;
    private Speaker        justAppearedSpeaker;

    // ---- pacing -------------------------------------------------------------------------------
    /** Time since the screen was last FREED (a bark finished fading out), not since it appeared. */
    private float         secondsSinceScreenFreed  = Float.MAX_VALUE;
    private final float[] triggerCooldownRemaining = new float[BarkTrigger.values().length];
    private boolean       suppressed;

    /**
     * Ring of the last {@code STORY_BARK_RECENT_MEMORY} lines said.  A candidate still in here is
     * not eligible — which is what stops ORA repeating herself when a moment fires often.
     */
    private final String[] recentlySpokenIds =
            new String[Math.max(1, StoryUiConstants.STORY_BARK_RECENT_MEMORY)];
    private int recentlySpokenNextIndex;

    /** Reused selection scratch — cleared and refilled per request, never allocated per frame. */
    private final List<BarkDefinition> candidateScratch = new ArrayList<>();

    /**
     * @param registry the bark catalog (see {@link BarkCatalog#bootstrap})
     * @param strings  the resolved localisation table
     * @param progress persistent narrative state — the region gate and the one-shot flags
     * @param selectionSeed seed for pool variety; the same seed replays the same picks
     */
    public BarkSystem(BarkRegistry registry, StoryStrings strings, StoryProgress progress,
                      long selectionSeed) {
        if (registry == null) throw new IllegalArgumentException("registry must not be null");
        if (strings  == null) throw new IllegalArgumentException("strings must not be null");
        if (progress == null) throw new IllegalArgumentException("progress must not be null");
        this.registry = registry;
        this.strings  = strings;
        this.progress = progress;
        this.random   = new Random(selectionSeed);
        this.queuedDefinitions = new BarkDefinition[StoryUiConstants.STORY_BARK_QUEUE_CAPACITY];
        this.queuedAgeSeconds  = new float[StoryUiConstants.STORY_BARK_QUEUE_CAPACITY];
    }

    /** The persistent narrative state this system gates on (region depth + one-shot flags). */
    public StoryProgress getProgress() {
        return progress;
    }

    /**
     * While suppressed, nothing is delivered and nothing ages — requests still queue and resume
     * intact once the overlay that owns the screen closes (order-7 Part E, overlay precedence).
     */
    public void setSuppressed(boolean value) {
        this.suppressed = value;
    }

    public boolean isSuppressed() {
        return suppressed;
    }

    // -------------------------------------------------------------------------
    // Requesting a moment
    // -------------------------------------------------------------------------

    /** Convenience for moments that carry no subject. */
    public boolean request(BarkTrigger trigger) {
        return request(trigger, null);
    }

    /**
     * Asks for a line for one gameplay MOMENT.  Picks the region-appropriate row, applies the drop
     * rules, and queues it.  Safe to call on every kill / every tick — dropping is the normal case.
     *
     * @param trigger    the moment that just happened
     * @param subjectKey what the moment was about (e.g. an enemy family name), or null
     * @return true when a line was actually queued
     */
    public boolean request(BarkTrigger trigger, String subjectKey) {
        if (trigger == null) return false;
        BarkDefinition chosen = selectCandidate(trigger, subjectKey);
        if (chosen == null) return false;

        BarkPriority priority = chosen.getPriority();
        boolean critical = priority == BarkPriority.STORY_CRITICAL;

        // Per-trigger cooldown — a mandatory beat ignores it, everything else waits its turn.
        if (!critical && triggerCooldownRemaining[trigger.ordinal()] > 0f) return false;

        // Never queue the same line twice (a moment can fire repeatedly while one is pending).
        if (isPendingOrActive(chosen)) return false;

        if (!critical && !hasRoomFor(priority)) return false;

        if (queuedCount >= queuedDefinitions.length) {
            // Critical only: evict the lowest-priority, oldest queued entry to make room.
            if (!evictLowestPriorityQueued()) return false;
        }
        queuedDefinitions[queuedCount] = chosen;
        queuedAgeSeconds[queuedCount]  = 0f;
        queuedCount++;
        // NOTE: even a mandatory beat waits its turn behind the line already on screen. Nothing
        // ever cuts a bark short — the player, and only the player, decides when they have read it.
        return true;
    }

    /**
     * Picks one row for this moment: region-gated, subject-matched, un-seen (for one-shots), and
     * weighted among the highest-priority survivors — so a mandatory beat always beats a flavour
     * line sharing the same trigger.
     */
    private BarkDefinition selectCandidate(BarkTrigger trigger, String subjectKey) {
        StoryRegion region = progress.getDeepestRegion();
        List<BarkDefinition> rows = registry.getForTrigger(trigger);
        candidateScratch.clear();
        BarkPriority bestPriority = null;
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            BarkDefinition row = rows.get(rowIndex);
            if (!row.matchesRegion(region))          continue;
            if (!row.matchesSubject(subjectKey))     continue;
            if (row.isOneShot() && progress.hasSeen(row.getId())) continue;
            // ANTI-REPETITION: a line said in the last few barks is not eligible. If that empties
            // the pool, the moment says nothing at all — silence always beats hearing it twice.
            if (wasRecentlySpoken(row.getId()))      continue;
            if (bestPriority == null || row.getPriority().isHigherThan(bestPriority)) {
                bestPriority = row.getPriority();
            }
            candidateScratch.add(row);
        }
        if (candidateScratch.isEmpty()) return null;

        float totalWeight = 0f;
        for (int candidateIndex = 0; candidateIndex < candidateScratch.size(); candidateIndex++) {
            BarkDefinition candidate = candidateScratch.get(candidateIndex);
            if (candidate.getPriority() == bestPriority) totalWeight += candidate.getWeight();
        }
        float roll = random.nextFloat() * totalWeight;
        BarkDefinition fallback = null;
        for (int candidateIndex = 0; candidateIndex < candidateScratch.size(); candidateIndex++) {
            BarkDefinition candidate = candidateScratch.get(candidateIndex);
            if (candidate.getPriority() != bestPriority) continue;
            fallback = candidate;
            roll -= candidate.getWeight();
            if (roll <= 0f) return candidate;
        }
        return fallback;   // float drift only; the last eligible row
    }

    /** Drop rules for a non-critical arrival. */
    private boolean hasRoomFor(BarkPriority priority) {
        if (priority == BarkPriority.FLAVOR) {
            // Flavour never waits: any pressure at all and it is simply not said.
            return activeDefinition == null
                    && queuedCount == 0
                    && secondsSinceScreenFreed >= StoryUiConstants.STORY_BARK_MIN_INTERVAL_SECONDS;
        }
        return queuedCount < queuedDefinitions.length;
    }

    private boolean isPendingOrActive(BarkDefinition definition) {
        if (activeDefinition == definition) return true;
        for (int queueIndex = 0; queueIndex < queuedCount; queueIndex++) {
            if (queuedDefinitions[queueIndex] == definition) return true;
        }
        return false;
    }

    /** Removes the lowest-priority queued entry (oldest among equals).  False if none may be evicted. */
    private boolean evictLowestPriorityQueued() {
        int          victimIndex    = -1;
        BarkPriority victimPriority = null;
        for (int queueIndex = 0; queueIndex < queuedCount; queueIndex++) {
            BarkPriority priority = queuedDefinitions[queueIndex].getPriority();
            if (priority == BarkPriority.STORY_CRITICAL) continue;
            if (victimPriority == null || victimPriority.isHigherThan(priority)) {
                victimPriority = priority;
                victimIndex    = queueIndex;
            }
        }
        if (victimIndex < 0) return false;
        removeQueuedAt(victimIndex);
        return true;
    }

    private void removeQueuedAt(int queueIndex) {
        for (int shiftIndex = queueIndex; shiftIndex < queuedCount - 1; shiftIndex++) {
            queuedDefinitions[shiftIndex] = queuedDefinitions[shiftIndex + 1];
            queuedAgeSeconds[shiftIndex]  = queuedAgeSeconds[shiftIndex + 1];
        }
        queuedCount--;
        queuedDefinitions[queuedCount] = null;
        queuedAgeSeconds[queuedCount]  = 0f;
    }

    // -------------------------------------------------------------------------
    // Per-frame update
    // -------------------------------------------------------------------------

    /**
     * Advances the fade/hold clock, expires stale queue entries and delivers the next bark once the
     * screen is free and the rate limit allows.  No-op while suppressed, so a queued line waits out
     * an overlay without ageing.
     */
    public void update(float deltaTime) {
        if (suppressed) return;

        for (int triggerIndex = 0; triggerIndex < triggerCooldownRemaining.length; triggerIndex++) {
            if (triggerCooldownRemaining[triggerIndex] > 0f) {
                triggerCooldownRemaining[triggerIndex] -= deltaTime;
            }
        }

        if (activeDefinition != null) {
            // A bark on screen has NO hold timer: it waits for the player. Only the fade-out, once
            // they dismiss it, is on a clock. The rate-limit clock does not run while they read.
            activeElapsedSeconds += deltaTime;
            if (activeDismissed) {
                activeDismissElapsedSeconds += deltaTime;
                if (activeDismissElapsedSeconds >= StoryUiConstants.STORY_FADE_OUT_SECONDS) {
                    retireActiveBark();
                }
            }
        } else {
            secondsSinceScreenFreed += deltaTime;
        }

        for (int queueIndex = queuedCount - 1; queueIndex >= 0; queueIndex--) {
            queuedAgeSeconds[queueIndex] += deltaTime;
            boolean stale = queuedAgeSeconds[queueIndex] >= StoryUiConstants.STORY_BARK_QUEUE_STALE_SECONDS;
            if (stale && queuedDefinitions[queueIndex].getPriority() != BarkPriority.STORY_CRITICAL) {
                removeQueuedAt(queueIndex);   // the moment has passed — say nothing rather than say it late
            }
        }

        if (activeDefinition == null && queuedCount > 0
                && secondsSinceScreenFreed >= StoryUiConstants.STORY_BARK_MIN_INTERVAL_SECONDS) {
            deliver(takeNextQueued());
        }
    }

    /**
     * Dismisses the bark on screen — the player tapped its X or swiped it away.  Starts the
     * fade-out; the next queued line follows once the rate limit allows.  No-op when nothing is on
     * screen or it is already fading.
     *
     * @return true when a bark was actually dismissed by this call
     */
    public boolean dismissActiveBark() {
        if (activeDefinition == null || activeDismissed) return false;
        activeDismissed             = true;
        activeDismissElapsedSeconds = 0f;
        return true;
    }

    /** Clears the finished bark and starts the rate-limit clock from the moment the screen freed. */
    private void retireActiveBark() {
        activeDefinition            = null;
        activeSpeakerName           = null;
        activeLineCount             = 0;
        activeDismissed             = false;
        activeDismissElapsedSeconds = 0f;
        secondsSinceScreenFreed     = 0f;
    }

    /**
     * Pops the highest-priority queued entry, OLDEST among equals.  The queue is kept in arrival
     * order (index 0 = oldest), and the scan below advances only on a STRICTLY higher priority
     * ({@link BarkPriority#isHigherThan}), so an equal-priority later arrival never displaces an
     * earlier one — same-priority barks stay first-in, first-out.
     */
    private BarkDefinition takeNextQueued() {
        int bestIndex = 0;
        for (int queueIndex = 1; queueIndex < queuedCount; queueIndex++) {
            if (queuedDefinitions[queueIndex].getPriority()
                    .isHigherThan(queuedDefinitions[bestIndex].getPriority())) {
                bestIndex = queueIndex;
            }
        }
        BarkDefinition next = queuedDefinitions[bestIndex];
        removeQueuedAt(bestIndex);
        return next;
    }

    /** Puts a line on screen: resolve its text, pre-wrap it, start its clock, stamp its cooldowns. */
    private void deliver(BarkDefinition definition) {
        String text = strings.get(definition.getTextStringId());
        if (definition.getSpeaker().getTypeStyle().isUpperCase()) {
            text = text.toUpperCase(Locale.ROOT);   // the Organization is drawn ALL-CAPS
        }
        List<String> wrapped = StoryText.wrapToMaxChars(text, StoryUiConstants.STORY_LINE_MAX_CHARS);
        activeLineCount = Math.min(wrapped.size(), activeLines.length);
        for (int lineIndex = 0; lineIndex < activeLineCount; lineIndex++) {
            activeLines[lineIndex] = wrapped.get(lineIndex);
        }
        activeSpeakerName           = strings.get(definition.getSpeaker().getNameStringId());
        activeDefinition            = definition;
        activeElapsedSeconds        = 0f;
        activeDismissed             = false;
        activeDismissElapsedSeconds = 0f;
        justAppearedSpeaker         = definition.getSpeaker();

        triggerCooldownRemaining[definition.getTrigger().ordinal()] =
                StoryUiConstants.STORY_BARK_TRIGGER_COOLDOWN_SECONDS[definition.getTrigger().ordinal()];
        if (definition.isOneShot()) {
            progress.markSeen(definition.getId());
        }
        rememberSpoken(definition.getId());
    }

    /** Records a line in the no-repeat ring so it cannot be picked again for a while. */
    private void rememberSpoken(String barkId) {
        recentlySpokenIds[recentlySpokenNextIndex] = barkId;
        recentlySpokenNextIndex = (recentlySpokenNextIndex + 1) % recentlySpokenIds.length;
    }

    /** True when this line is still inside the no-repeat memory. */
    private boolean wasRecentlySpoken(String barkId) {
        for (int memoryIndex = 0; memoryIndex < recentlySpokenIds.length; memoryIndex++) {
            if (barkId.equals(recentlySpokenIds[memoryIndex])) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Render-side read model (all pre-computed; the renderer allocates nothing)
    // -------------------------------------------------------------------------

    public boolean hasActiveBark() {
        return activeDefinition != null;
    }

    public Speaker getActiveSpeaker() {
        return activeDefinition != null ? activeDefinition.getSpeaker() : null;
    }

    /** The resolved (localised) speaker name for the chip, or null when nothing is on screen. */
    public String getActiveSpeakerName() {
        return activeSpeakerName;
    }

    /** Pre-wrapped body lines.  Read only the first {@link #getActiveLineCount()} entries. */
    public String[] getActiveLines() {
        return activeLines;
    }

    public int getActiveLineCount() {
        return activeLineCount;
    }

    /** Seconds since the active bark was delivered — drives the Planet's jitter phase. */
    public float getActiveElapsedSeconds() {
        return activeElapsedSeconds;
    }

    /** 0..1 fade multiplier for the whole panel (fade in -> held until dismissed -> fade out). */
    public float getVisibleFraction() {
        if (activeDefinition == null) return 0f;
        return GameMath.storyPanelVisibleFraction(activeElapsedSeconds,
                StoryUiConstants.STORY_FADE_IN_SECONDS,
                activeDismissed, activeDismissElapsedSeconds,
                StoryUiConstants.STORY_FADE_OUT_SECONDS);
    }

    /** True once the player dismissed the bark on screen and it is fading out. */
    public boolean isActiveBarkDismissed() {
        return activeDismissed;
    }

    /**
     * Returns the speaker of a bark that appeared since the last call (for its audio sting), then
     * clears the flag.  Null when nothing new appeared.
     */
    public Speaker consumeJustAppearedSpeaker() {
        Speaker speaker = justAppearedSpeaker;
        justAppearedSpeaker = null;
        return speaker;
    }

    /** The id of the line on screen (tests / telemetry), or null. */
    public String getActiveBarkId() {
        return activeDefinition != null ? activeDefinition.getId() : null;
    }

    public int getQueuedCount() {
        return queuedCount;
    }
}
