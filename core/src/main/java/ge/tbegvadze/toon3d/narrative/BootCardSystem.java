package ge.tbegvadze.toon3d.narrative;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * The REPRINT / BOOT CARD (Story UI order-3) — the full-screen card shown every time the player is
 * printed after a death, and on the very first run too, because in-fiction the original self has
 * just died (story/01-timeline.md).  It is the game's most reliable story channel: the player sees
 * it every single run, so it doubles as a guaranteed bite-sized beat.
 *
 * <p>This class is the headless BRAIN and does no rendering.  It owns which card is being shown,
 * which ORA line goes on it, what the instance counter reads, the reveal/fade clock, and whether
 * continuing is even allowed.  {@code render/BootCardRenderer} draws whatever it reports.
 *
 * <h3>The rules it enforces</h3>
 * <ul>
 *   <li><b>Every reprint counts.</b> {@link #present} increments the PERSISTENT reprint count
 *       exactly once per card, so the number climbs across deaths and app restarts alike.  It is
 *       never celebrated and never annotated — the climbing is the horror.</li>
 *   <li><b>Region gating.</b> ORA's wake line is chosen from the pool banded to
 *       {@link StoryProgress#getDeepestRegion()} — the deepest region ever reached, which is
 *       persistent.  Death never rewinds her: she is cheerful up top and protective down deep, with
 *       no conditional in game code.</li>
 *   <li><b>No repeats.</b> A line said within the last few reprints is not eligible; if that empties
 *       the pool the memory is ignored rather than the card going silent, because this beat is
 *       guaranteed — a boot card with no ORA line would read as a bug, not as silence.</li>
 *   <li><b>Never a trap.</b> The card has NO auto-advance and NO timer.  It waits for the player,
 *       and one tap on CONTINUE always proceeds ({@link #requestContinue()}), even mid-reveal.</li>
 *   <li><b>The endings subvert it.</b> A variant that {@link BootCardVariant#isTerminal() cannot
 *       reprint} (FREE / KILL) reports {@link #isContinueAllowed()} false: the renderer draws no
 *       CONTINUE and the run does not reload.  That absence is the ending.  MERGE presents no card
 *       at all.</li>
 * </ul>
 *
 * <p>ALLOCATION: the resolved lines live in pre-allocated arrays and the candidate scratch list is
 * reused, so the per-frame path allocates nothing.  Resolving + wrapping happens once, in
 * {@link #present}, off the render thread.
 *
 * <p>Headless: no LibGDX render state, so all of it is unit-testable.
 */
public final class BootCardSystem {

    /**
     * Placeholder a wake line may carry for the reprint number ("Reprint {instance}.").  Substituted
     * at present time with the formatted counter, so the catalog never has to know the number.
     */
    public static final String INSTANCE_TOKEN = "{instance}";

    /** How many recent wake lines are remembered so a reprint never repeats the last few. */
    private static final int RECENT_WAKE_MEMORY = 3;

    private final BootCardRegistry registry;
    private final StoryStrings     strings;
    private final StoryProgress    progress;
    private final Random           random;

    // ---- the card currently on screen ----------------------------------------------------------
    private BootCardVariant activeVariant;
    private BootCardDefinition activeDefinition;
    private final String[] systemLines =
            new String[StoryUiConstants.STORY_BOOT_SYSTEM_MAX_LINES];
    private int      systemLineCount;
    private String   instanceCounterText;
    private final String[] wakeLines =
            new String[StoryUiConstants.STORY_BOOT_WAKE_MAX_LINES];
    private int      wakeLineCount;
    private String   wakeSpeakerName;
    private String   activeWakeLineId;
    private float    elapsedSeconds;
    private boolean  continueRequested;
    private boolean  revealComplete;

    // ---- no-repeat memory (a small ring; ORA must not greet you the same way twice running) -----
    private final String[] recentWakeLineIds = new String[RECENT_WAKE_MEMORY];
    private int            recentWakeNextIndex;

    /** Accessibility: the "reduce text hold" setting shortens the staggered line reveal. */
    private boolean reducedTextHold;

    /** Reused selection scratch — cleared and refilled per present(), never allocated per frame. */
    private final List<BootWakeLine> candidateScratch = new ArrayList<>();

    /**
     * @param registry      the boot-card catalog (see {@link BootCardCatalog#defaultRegistry})
     * @param strings       the resolved localisation table
     * @param progress      persistent narrative state — the region gate and the reprint counter
     * @param selectionSeed seed for wake-line variety; the same seed replays the same picks
     */
    public BootCardSystem(BootCardRegistry registry, StoryStrings strings, StoryProgress progress,
                          long selectionSeed) {
        if (registry == null) throw new IllegalArgumentException("registry must not be null");
        if (strings  == null) throw new IllegalArgumentException("strings must not be null");
        if (progress == null) throw new IllegalArgumentException("progress must not be null");
        this.registry = registry;
        this.strings  = strings;
        this.progress = progress;
        this.random   = new Random(selectionSeed);
    }

    /** The persistent narrative state this system reads (region depth + the reprint counter). */
    public StoryProgress getProgress() {
        return progress;
    }

    /**
     * ACCESSIBILITY — "reduce text hold".  When on, the staggered reveal of the SYSTEM status lines
     * is shortened by {@code STORY_TEXT_HOLD_SCALE_REDUCED} so the whole card is legible sooner.
     * It never changes what is said, and a tap skips the reveal either way.
     */
    public void setReducedTextHold(boolean value) {
        this.reducedTextHold = value;
    }

    public boolean isReducedTextHold() {
        return reducedTextHold;
    }

    // -------------------------------------------------------------------------
    // Presenting a card
    // -------------------------------------------------------------------------

    /**
     * Puts a card on screen: bumps the persistent reprint count, resolves the machine-voice status
     * lines, picks ORA's region-appropriate wake line and pre-wraps it, and starts the reveal clock.
     * Everything the renderer needs is computed here, once.
     *
     * @param variant which card (the default reprint, or an ending's subversion of it)
     * @return true when a card is now on screen; false when this variant shows none (MERGE), in
     *         which case the caller must not enter a card phase at all
     */
    public boolean present(BootCardVariant variant) {
        clear();
        if (variant == null || !variant.showsCard()) return false;
        BootCardDefinition definition = registry.getCard(variant);
        if (definition == null) return false;   // an unregistered variant shows nothing, never crashes

        activeVariant    = variant;
        activeDefinition = definition;

        // The counter climbs on EVERY card, including the very first run — the original self has
        // just died, so run 1 is already reprint 1.  Terminal cards still count the instance that
        // reached them; they simply do not print the number.
        int reprintCount = progress.recordReprint();
        instanceCounterText = definition.showsInstanceCounter()
                ? StoryUiConstants.STORY_BOOT_COUNTER_PREFIX
                        + GameMath.formatInstanceCounter(reprintCount,
                                StoryUiConstants.STORY_BOOT_COUNTER_DIGITS)
                : null;

        resolveSystemLines(definition);
        if (definition.showsWakeLine()) {
            resolveWakeLine(reprintCount);
        }
        return true;
    }

    /**
     * The machine voice, resolved verbatim — status lines are NOT wrapped.  A wrapped readout looks
     * like a layout bug rather than like a cold terminal, so the catalog keeps every line inside
     * {@code STORY_LINE_MAX_CHARS} (a test enforces it) and each one occupies exactly one row.
     */
    private void resolveSystemLines(BootCardDefinition definition) {
        String[] stringIds = definition.getSystemLineStringIds();
        // The builder guarantees a non-empty array of non-empty ids; the guard keeps a card that
        // was somehow built another way from taking the screen down with it.
        if (stringIds == null) return;
        systemLineCount = Math.min(stringIds.length, systemLines.length);
        for (int lineIndex = 0; lineIndex < systemLineCount; lineIndex++) {
            // StoryStrings.get() never returns null — a missing id resolves to a visible !id!
            // marker, so a broken translation shows up in playtest instead of blanking the card.
            systemLines[lineIndex] = strings.get(stringIds[lineIndex]);
        }
    }

    /** Picks ORA's line for this reprint, substitutes the instance number, and pre-wraps it. */
    private void resolveWakeLine(int reprintCount) {
        BootWakeLine chosen = selectWakeLine();
        if (chosen == null) return;   // no pool for this region: the machine card stands alone

        String text = strings.get(chosen.getTextStringId());
        if (text != null && text.contains(INSTANCE_TOKEN)) {
            text = text.replace(INSTANCE_TOKEN,
                    GameMath.formatInstanceCounter(reprintCount,
                            StoryUiConstants.STORY_BOOT_COUNTER_DIGITS));
        }
        List<String> wrapped = StoryText.wrapToMaxChars(text, StoryUiConstants.STORY_LINE_MAX_CHARS);
        wakeLineCount = Math.min(wrapped.size(), wakeLines.length);
        for (int lineIndex = 0; lineIndex < wakeLineCount; lineIndex++) {
            wakeLines[lineIndex] = wrapped.get(lineIndex);
        }
        wakeSpeakerName  = strings.get(Speaker.AI.getNameStringId());
        activeWakeLineId = chosen.getId();
        rememberWakeLine(chosen.getId());
    }

    /**
     * Picks one wake line for the deepest region reached, preferring lines not said in the last few
     * reprints.  Unlike a bark, this beat is GUARANTEED: if the no-repeat memory would leave nothing
     * eligible, the memory is ignored and the region's pool is used anyway — a boot card with no ORA
     * line reads as a bug, not as deliberate silence.
     */
    private BootWakeLine selectWakeLine() {
        StoryRegion region = progress.getDeepestRegion();
        List<BootWakeLine> rows = registry.getWakeLines();

        candidateScratch.clear();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            BootWakeLine row = rows.get(rowIndex);
            if (!row.matchesRegion(region))          continue;
            if (wasRecentlySaid(row.getId()))        continue;
            candidateScratch.add(row);
        }
        if (candidateScratch.isEmpty()) {
            // Fall back to the whole region pool, repeats included, so the beat always lands.
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                BootWakeLine row = rows.get(rowIndex);
                if (row.matchesRegion(region)) candidateScratch.add(row);
            }
        }
        if (candidateScratch.isEmpty()) return null;
        return candidateScratch.get(random.nextInt(candidateScratch.size()));
    }

    private void rememberWakeLine(String wakeLineId) {
        recentWakeLineIds[recentWakeNextIndex] = wakeLineId;
        recentWakeNextIndex = (recentWakeNextIndex + 1) % recentWakeLineIds.length;
    }

    private boolean wasRecentlySaid(String wakeLineId) {
        for (int memoryIndex = 0; memoryIndex < recentWakeLineIds.length; memoryIndex++) {
            if (wakeLineId.equals(recentWakeLineIds[memoryIndex])) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Per-frame update
    // -------------------------------------------------------------------------

    /** Advances the fade-in and the staggered status-line reveal.  No-op when no card is up. */
    public void update(float deltaTime) {
        if (activeDefinition == null) return;
        elapsedSeconds += deltaTime;
    }

    /**
     * The player tapped CONTINUE.  Always proceeds when the variant allows a reprint — the card is
     * never a trap and never gates the tap behind the reveal.  A terminal ending card ignores this
     * entirely: there is nothing to continue to.
     *
     * @return true when the request was accepted (the caller may leave the card phase)
     */
    public boolean requestContinue() {
        if (activeDefinition == null || !isContinueAllowed()) return false;
        continueRequested = true;
        return true;
    }

    /**
     * The player tapped somewhere other than CONTINUE.  Completes the staggered reveal immediately
     * so nobody has to wait out a mood effect they did not ask for.
     */
    public void skipReveal() {
        if (activeDefinition == null) return;
        revealComplete = true;
    }

    /** Clears the card once the caller has acted on {@link #isContinueRequested()}. */
    public void clear() {
        activeVariant       = null;
        activeDefinition    = null;
        systemLineCount     = 0;
        wakeLineCount       = 0;
        wakeSpeakerName     = null;
        activeWakeLineId    = null;
        instanceCounterText = null;
        elapsedSeconds      = 0f;
        continueRequested   = false;
        revealComplete      = false;
    }

    // -------------------------------------------------------------------------
    // Render-side read model (all pre-computed; the renderer allocates nothing)
    // -------------------------------------------------------------------------

    public boolean hasActiveCard() {
        return activeDefinition != null;
    }

    public BootCardVariant getActiveVariant() {
        return activeVariant;
    }

    /** The machine-voice status lines.  Read only the first {@link #getSystemLineCount()} entries. */
    public String[] getSystemLines() {
        return systemLines;
    }

    public int getSystemLineCount() {
        return systemLineCount;
    }

    /**
     * How many status lines have printed in so far — the card wakes up like a terminal rather than
     * appearing as a block.  Jumps straight to all of them once the player taps past the reveal.
     */
    public int getRevealedSystemLineCount() {
        if (activeDefinition == null) return 0;
        if (revealComplete) return systemLineCount;
        float perLineSeconds = StoryUiConstants.STORY_BOOT_LINE_REVEAL_SECONDS;
        if (reducedTextHold) perLineSeconds *= StoryUiConstants.STORY_TEXT_HOLD_SCALE_REDUCED;
        return GameMath.bootCardRevealedLineCount(elapsedSeconds, perLineSeconds, systemLineCount);
    }

    /** True once every status line is on screen — the counter and ORA follow it in. */
    public boolean isRevealComplete() {
        return activeDefinition != null && getRevealedSystemLineCount() >= systemLineCount;
    }

    /** "INSTANCE #0048", or null when this card does not print the counter. */
    public String getInstanceCounterText() {
        return instanceCounterText;
    }

    /** Pre-wrapped ORA line.  Read only the first {@link #getWakeLineCount()} entries. */
    public String[] getWakeLines() {
        return wakeLines;
    }

    public int getWakeLineCount() {
        return wakeLineCount;
    }

    /** The resolved (localised) speaker name for ORA's chip, or null when she does not speak. */
    public String getWakeSpeakerName() {
        return wakeSpeakerName;
    }

    /** The id of the ORA line on screen (tests / telemetry), or null. */
    public String getActiveWakeLineId() {
        return activeWakeLineId;
    }

    /** Seconds since the card was presented — drives the fade-in and the reveal. */
    public float getElapsedSeconds() {
        return elapsedSeconds;
    }

    /** 0..1 fade multiplier for the whole card.  Rises once and then holds; nothing flashes. */
    public float getVisibleFraction() {
        if (activeDefinition == null) return 0f;
        return GameMath.storyPanelVisibleFraction(elapsedSeconds,
                StoryUiConstants.STORY_BOOT_FADE_IN_SECONDS,
                false, 0f, StoryUiConstants.STORY_BOOT_FADE_OUT_SECONDS);
    }

    /**
     * True when this card offers a way onward.  False on the FREE and KILL endings: they print
     * "NO CHECKPOINT AUTHORIZED FOR REPRINT" and then nothing reloads, so the renderer draws no
     * CONTINUE and {@link #requestContinue()} is refused.
     */
    public boolean isContinueAllowed() {
        return activeVariant != null && activeVariant.allowsReprint();
    }

    /** True when this card ENDS the run — the "no reprint" state the endings are built on. */
    public boolean isTerminal() {
        return activeVariant != null && activeVariant.isTerminal();
    }

    /** True once the player has tapped CONTINUE; the caller then leaves the card phase. */
    public boolean isContinueRequested() {
        return continueRequested;
    }
}
