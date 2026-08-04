package ge.tbegvadze.toon3d.narrative;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * The EXCHANGE LAYER (Story UI order-4) — the blocking, interactive half of the story channel, and
 * the headless brain behind it.  Where a bark is a line the player reads while they keep playing, an
 * exchange STOPS the world and asks them to answer: a speaker's prompt plus 2-3 big tappable
 * options, one of which they must pick before control returns.  That tap is the whole point — it
 * turns reading into speaking, which is what makes a player who does not want to read, read.
 *
 * <p>This class owns every narrative decision and NO rendering: which exchange opens for a moment,
 * what the options say, what picking one does to the hidden {@link Stance} model, which reply comes
 * back, and the fade clock.  {@code render/StoryExchangeRenderer} draws whatever it reports.
 * Headless (no LibGDX render state), so all of it is unit-testable.
 *
 * <h3>The rules it enforces</h3>
 * <ul>
 *   <li><b>Rare and deliberate.</b> Exchange rows are one-shot by default and gated to a region
 *       band, so the budget is a handful per region.  A moment with nothing eligible simply passes
 *       in silence — an exchange is never manufactured to fill a gap.</li>
 *   <li><b>Never a timer, ever.</b> Nothing in here counts down and nothing auto-selects.  The
 *       world is already frozen (this is a turn-based game with an action lock), so waiting costs
 *       the player nothing and no answer is ever taken out of their hands.</li>
 *   <li><b>No blank "next".</b> The only way past the prompt is to tap an actual option, so the
 *       player must at least glance at what they are choosing between.  The reply that follows has
 *       one acknowledge button — that is a receipt for a choice already made, not a skip.</li>
 *   <li><b>Region gating, same axis as everything else.</b> Rows are filtered by
 *       {@link StoryProgress#getDeepestRegion()} — the deepest region EVER reached — so death never
 *       rewinds an exchange's tone, and a one-shot answered two runs ago never re-asks.</li>
 *   <li><b>Stance flavours; it never gates.</b> When several rows are eligible, one written for the
 *       player's dominant leaning is preferred — but a neutral row is always a valid fallback, so no
 *       stance can ever silence a moment or lock content away.</li>
 *   <li><b>Answering is what persists.</b> A stance nudge, a codex unlock and a consequential
 *       outcome are all written through {@link StoryProgress} the instant the option is tapped.</li>
 * </ul>
 *
 * <p>ALLOCATION: prompt lines, option lines and reply lines live in pre-allocated arrays, and the
 * candidate scratch list is reused, so the per-frame path allocates nothing.  Resolving + wrapping
 * happens once, when an exchange opens and again when an option is answered — never in render().
 */
public final class ExchangeSystem {

    private final ExchangeRegistry registry;
    private final StoryStrings     strings;
    private final StoryProgress    progress;
    private final Random           random;

    /** What the panel is showing right now. */
    private enum Stage { NONE, PROMPT, REPLY, CLOSING }

    // ---- the exchange currently on screen -------------------------------------------------------
    private ExchangeDefinition activeDefinition;
    private Stage              stage = Stage.NONE;
    private Speaker            activeSpeaker;
    private String             activeSpeakerName;
    private final String[]     bodyLines = new String[StoryUiConstants.STORY_EXCHANGE_MAX_LINES];
    private int                bodyLineCount;
    private final String[]     optionLines = new String[StoryUiConstants.STORY_EXCHANGE_MAX_OPTIONS];
    private int                optionCount;
    private int                pressedOptionIndex = -1;
    private float              elapsedSeconds;
    private float              closingElapsedSeconds;
    private String             chosenOptionId;
    private boolean            finished;
    private Speaker            justAppearedSpeaker;

    /** An exchange asked for by a moment but not yet opened (the world was mid-transition). */
    private ExchangeDefinition pendingDefinition;

    // ---- effects the engine must apply (consumed once, never re-applied) ------------------------
    private String          pendingRewardItemTypeName;
    private int             pendingRewardQuantity;
    private BootCardVariant pendingEndingVariant;

    /** Reused selection scratch — cleared and refilled per request, never allocated per frame. */
    private final List<ExchangeDefinition> candidateScratch = new ArrayList<>();

    /**
     * Wrap width for the prompt / reply block.  Settable so the order-6 accessibility text-size
     * setting can narrow it as the glyphs grow — wrap, never shrink.
     */
    private int lineMaxChars = StoryUiConstants.STORY_LINE_MAX_CHARS;

    /**
     * @param registry      the exchange catalog (see {@link ExchangeCatalog#defaultRegistry})
     * @param strings       the resolved localisation table
     * @param progress      persistent narrative state — the region gate, one-shot flags and stance
     * @param selectionSeed seed for which eligible row opens; the same seed replays the same picks
     */
    public ExchangeSystem(ExchangeRegistry registry, StoryStrings strings, StoryProgress progress,
                          long selectionSeed) {
        if (registry == null) throw new IllegalArgumentException("registry must not be null");
        if (strings  == null) throw new IllegalArgumentException("strings must not be null");
        if (progress == null) throw new IllegalArgumentException("progress must not be null");
        this.registry = registry;
        this.strings  = strings;
        this.progress = progress;
        this.random   = new Random(selectionSeed);
    }

    /** The persistent narrative state this system gates on and writes answers into. */
    public StoryProgress getProgress() {
        return progress;
    }

    /**
     * Narrows the wrap width for blocks resolved from now on (order-6 Part D's text-size setting).
     * Values below 1 are ignored — a cap of zero would hard-break every character onto its own line.
     */
    public void setLineMaxChars(int value) {
        if (value < 1) return;
        this.lineMaxChars = value;
    }

    public int getLineMaxChars() {
        return lineMaxChars;
    }

    // -------------------------------------------------------------------------
    // Asking for an exchange
    // -------------------------------------------------------------------------

    /**
     * Asks whether this moment has an exchange to offer, and if so holds it as PENDING rather than
     * opening it on the spot.  Moments fire during level transitions and fades, and stopping the
     * world mid-transition would strand the player in a modal over a black screen; the caller opens
     * the held exchange with {@link #openPending()} once play has actually resumed.
     *
     * @return true when an exchange is now waiting to open
     */
    public boolean request(ExchangeTrigger trigger) {
        if (trigger == null) return false;
        if (activeDefinition != null || pendingDefinition != null) return false;
        ExchangeDefinition chosen = selectCandidate(trigger);
        if (chosen == null) return false;
        pendingDefinition = chosen;
        return true;
    }

    /** True when a moment offered an exchange that has not been put on screen yet. */
    public boolean hasPending() {
        return pendingDefinition != null;
    }

    /**
     * Opens the exchange a moment held back, if any.  Call only when the world is genuinely ready to
     * stop — in play, not mid-fade and not mid-step.
     *
     * @return true when an exchange is now on screen (the caller should enter its modal phase)
     */
    public boolean openPending() {
        if (pendingDefinition == null || activeDefinition != null) return false;
        ExchangeDefinition definition = pendingDefinition;
        pendingDefinition = null;
        return open(definition);
    }

    /**
     * Holds a specific exchange by id as PENDING — the "ask for this exact beat, but wait until the
     * player is standing still" path order-5's hand-placed moments take (the ending choice at the
     * Core).  Nothing opens here; {@link #openPending()} does, once the world is ready to stop, which
     * is how a hand-placed beat inherits exactly the same readiness bars as a triggered one.
     *
     * @return true when the exchange exists and is now waiting to open
     */
    public boolean requestById(String exchangeId) {
        if (activeDefinition != null || pendingDefinition != null) return false;
        ExchangeDefinition definition = registry.getById(exchangeId);
        if (definition == null) return false;
        pendingDefinition = definition;
        return true;
    }

    /**
     * Opens a specific exchange by id, bypassing triggers entirely — the path an option's
     * {@code nextExchangeId} chain takes.
     *
     * @return true when the exchange exists and is now on screen
     */
    public boolean present(String exchangeId) {
        ExchangeDefinition definition = registry.getById(exchangeId);
        if (definition == null) return false;
        return open(definition);
    }

    /**
     * Picks one eligible exchange for this moment.  Preference order is: a row written FOR the
     * player's dominant leaning, then a neutral row, then anything else eligible — so stance
     * flavours which conversation happens without ever being able to leave a moment empty.
     */
    private ExchangeDefinition selectCandidate(ExchangeTrigger trigger) {
        StoryRegion region = progress.getDeepestRegion();
        Stance dominant    = progress.getDominantStance();
        List<ExchangeDefinition> rows = registry.getForTrigger(trigger);

        if (dominant != null && collectEligible(rows, region, dominant, false)) {
            return pickFromScratch();
        }
        if (collectEligible(rows, region, null, true)) {
            return pickFromScratch();
        }
        if (collectEligible(rows, region, null, false)) {
            return pickFromScratch();
        }
        return null;
    }

    /**
     * Fills {@link #candidateScratch} with the eligible rows matching one preference tier.
     *
     * @param requiredAffinity only rows written for this leaning qualify, or null for "any affinity"
     * @param neutralOnly      only rows with no affinity at all qualify (the fallback tier)
     * @return true when the scratch list came back non-empty
     */
    private boolean collectEligible(List<ExchangeDefinition> rows, StoryRegion region,
                                    Stance requiredAffinity, boolean neutralOnly) {
        candidateScratch.clear();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            ExchangeDefinition row = rows.get(rowIndex);
            if (!row.matchesRegion(region))                          continue;
            if (row.isOneShot() && progress.hasSeen(row.getId()))    continue;
            if (requiredAffinity != null && row.getStanceAffinity() != requiredAffinity) continue;
            if (neutralOnly && row.getStanceAffinity() != null)      continue;
            candidateScratch.add(row);
        }
        return !candidateScratch.isEmpty();
    }

    private ExchangeDefinition pickFromScratch() {
        return candidateScratch.get(random.nextInt(candidateScratch.size()));
    }

    /** Puts an exchange on screen: resolve and wrap the prompt, resolve every option, start the clock. */
    private boolean open(ExchangeDefinition definition) {
        if (definition == null) return false;
        // Resets the PANEL only, never the pending reward — an option may both reveal a cache and
        // chain into a follow-up exchange, and the engine must still get to grant that cache.
        resetPanelState();
        activeDefinition = definition;
        stage            = Stage.PROMPT;
        activeSpeaker    = definition.getSpeaker();
        setBody(activeSpeaker, definition.getPromptStringId());

        optionCount = Math.min(definition.getOptionCount(), optionLines.length);
        for (int optionIndex = 0; optionIndex < optionCount; optionIndex++) {
            // The player's own answers are never upper-cased: the Organization's ALL-CAPS styling is
            // the Organization's, and the player does not speak in its voice even when obeying it.
            optionLines[optionIndex] =
                    strings.get(definition.getOption(optionIndex).getPlayerLineStringId());
        }

        if (definition.isOneShot()) {
            progress.markSeen(definition.getId());
        }
        justAppearedSpeaker = activeSpeaker;
        return true;
    }

    /** Resolves, styles and pre-wraps one body block (the prompt, or an option's reply). */
    private void setBody(Speaker speaker, String stringId) {
        String text = strings.get(stringId);
        if (speaker != null && speaker.getTypeStyle().isUpperCase()) {
            text = text.toUpperCase(Locale.ROOT);   // the Organization is drawn ALL-CAPS
        }
        List<String> wrapped = StoryText.wrapToMaxChars(text, lineMaxChars);
        bodyLineCount = Math.min(wrapped.size(), bodyLines.length);
        for (int lineIndex = 0; lineIndex < bodyLineCount; lineIndex++) {
            bodyLines[lineIndex] = wrapped.get(lineIndex);
        }
        activeSpeakerName = strings.get(speaker != null ? speaker.getNameStringId()
                                                       : Speaker.AI.getNameStringId());
    }

    // -------------------------------------------------------------------------
    // Answering
    // -------------------------------------------------------------------------

    /**
     * The player tapped an answer.  Applies EVERY consequence at once — the stance nudge, a codex
     * unlock, a consequential outcome, a revealed reward — then shows the reply, or ends the
     * exchange when the option has nothing to say back.
     *
     * @return true when the tap was accepted (ignored unless the prompt is actually up)
     */
    public boolean selectOption(int optionIndex) {
        if (stage != Stage.PROMPT || activeDefinition == null) return false;
        ExchangeOption option = activeDefinition.getOption(optionIndex);
        if (option == null) return false;

        chosenOptionId     = option.getId();
        pressedOptionIndex = -1;

        for (Stance stance : Stance.values()) {
            progress.nudgeStance(stance, option.getStanceNudge(stance));
        }
        if (option.getUnlocksCodexId() != null) {
            progress.unlockCodexEntry(option.getUnlocksCodexId());
        }
        if (option.getKind() == ExchangeOptionKind.CONSEQUENTIAL) {
            // The tracked outcome is keyed by the BEAT, not the option, so order-5 can ask "how did
            // they answer the log question?" without knowing which option ids exist — and a probe
            // that chains into the same yes/no still records under the one key.
            progress.recordOutcome(activeDefinition.getOutcomeId(), option.getId());
        }
        if (option.getRewardItemTypeName() != null) {
            pendingRewardItemTypeName = option.getRewardItemTypeName();
            pendingRewardQuantity     = option.getRewardQuantity();
        }
        if (option.getEndingVariant() != null) {
            // The run is over; it just does not know it yet.  The engine picks this up once the panel
            // has faded (order-5), so the player still reads the reply to their own last answer before
            // the boot card they have seen a hundred times comes back changed.
            pendingEndingVariant = option.getEndingVariant();
        }

        if (option.getReplyStringId() != null) {
            Speaker replySpeaker = option.getReplySpeaker() != null
                    ? option.getReplySpeaker() : activeDefinition.getSpeaker();
            activeSpeaker = replySpeaker;
            setBody(replySpeaker, option.getReplyStringId());
            optionCount         = 0;
            stage               = Stage.REPLY;
            justAppearedSpeaker = replySpeaker;
            return true;
        }
        advancePastReply(option);
        return true;
    }

    /**
     * The player acknowledged the reply.  Chains straight into a follow-up exchange when the answered
     * option named one; otherwise starts the fade-out and hands control back.
     *
     * @return true when the tap was accepted (ignored unless a reply is actually up)
     */
    public boolean requestContinue() {
        if (stage != Stage.REPLY || activeDefinition == null) return false;
        advancePastReply(findChosenOption());
        return true;
    }

    /** Either opens the chained follow-up exchange, or closes this one. */
    private void advancePastReply(ExchangeOption answeredOption) {
        String nextExchangeId = answeredOption != null ? answeredOption.getNextExchangeId() : null;
        if (nextExchangeId != null && present(nextExchangeId)) return;
        stage                 = Stage.CLOSING;
        closingElapsedSeconds = 0f;
        optionCount           = 0;
    }

    private ExchangeOption findChosenOption() {
        if (activeDefinition == null || chosenOptionId == null) return null;
        for (int optionIndex = 0; optionIndex < activeDefinition.getOptionCount(); optionIndex++) {
            ExchangeOption option = activeDefinition.getOption(optionIndex);
            if (chosenOptionId.equals(option.getId())) return option;
        }
        return null;
    }

    /**
     * Which option the thumb is currently held on, for the pressed plate the renderer draws.  A tap
     * that slides off its button before release passes -1 here and is cancelled, exactly like every
     * other button on the phone.
     */
    public void setPressedOptionIndex(int optionIndex) {
        this.pressedOptionIndex = (stage == Stage.PROMPT || stage == Stage.REPLY) ? optionIndex : -1;
    }

    public int getPressedOptionIndex() {
        return pressedOptionIndex;
    }

    // -------------------------------------------------------------------------
    // Per-frame update
    // -------------------------------------------------------------------------

    /** Advances the fade clocks.  The ONLY clock in the whole system — nothing here ever advances
     *  the conversation on its own. */
    public void update(float deltaTime) {
        if (activeDefinition == null) return;
        elapsedSeconds += deltaTime;
        if (stage != Stage.CLOSING) return;
        closingElapsedSeconds += deltaTime;
        if (closingElapsedSeconds >= StoryUiConstants.STORY_EXCHANGE_FADE_OUT_SECONDS) {
            activeDefinition = null;
            stage            = Stage.NONE;
            optionCount      = 0;
            bodyLineCount    = 0;
            finished         = true;
        }
    }

    /**
     * True once the exchange has closed and faded out — the caller's cue to return control to the
     * player.  Cleared by {@link #consumeFinished()}.
     */
    public boolean isFinished() {
        return finished;
    }

    /** Reads and clears the finished flag, so a stale one can never skip the next exchange. */
    public boolean consumeFinished() {
        boolean wasFinished = finished;
        finished = false;
        return wasFinished;
    }

    /**
     * Drops whatever is on screen, whatever was waiting to open, and every un-consumed effect.
     * Persisted answers (stance, outcomes, codex) are untouched — those are the player's, not the
     * panel's.  Used to tear the layer down, never as part of the normal flow.
     */
    public void clear() {
        resetPanelState();
        pendingDefinition         = null;
        finished                  = false;
        pendingRewardItemTypeName = null;
        pendingRewardQuantity     = 0;
        pendingEndingVariant      = null;
    }

    /** Clears the on-screen exchange only.  Shared by {@link #clear()} and by opening a new one. */
    private void resetPanelState() {
        activeDefinition      = null;
        stage                 = Stage.NONE;
        activeSpeaker         = null;
        activeSpeakerName     = null;
        bodyLineCount         = 0;
        optionCount           = 0;
        pressedOptionIndex    = -1;
        elapsedSeconds        = 0f;
        closingElapsedSeconds = 0f;
        chosenOptionId        = null;
    }

    // -------------------------------------------------------------------------
    // Render-side read model (all pre-computed; the renderer allocates nothing)
    // -------------------------------------------------------------------------

    /** True while an exchange owns the screen — the world must take no turns. */
    public boolean isActive() {
        return activeDefinition != null;
    }

    /** True while the player still has an answer to give (the prompt and its option buttons). */
    public boolean isAwaitingChoice() {
        return stage == Stage.PROMPT;
    }

    /** True while the answer's reply is up, waiting to be acknowledged. */
    public boolean isShowingReply() {
        return stage == Stage.REPLY;
    }

    public ExchangeDefinition getActiveDefinition() {
        return activeDefinition;
    }

    /** The id of the exchange on screen (tests / telemetry), or null. */
    public String getActiveExchangeId() {
        return activeDefinition != null ? activeDefinition.getId() : null;
    }

    /** The id of the option the player tapped, or null before they answer. */
    public String getChosenOptionId() {
        return chosenOptionId;
    }

    /** Who is speaking the block on screen — the prompt's speaker, then the reply's. */
    public Speaker getActiveSpeaker() {
        return activeSpeaker;
    }

    /** The resolved (localised) speaker name for the chip. */
    public String getActiveSpeakerName() {
        return activeSpeakerName;
    }

    /** Pre-wrapped prompt / reply lines.  Read only the first {@link #getBodyLineCount()} entries. */
    public String[] getBodyLines() {
        return bodyLines;
    }

    public int getBodyLineCount() {
        return bodyLineCount;
    }

    /** Resolved answer text, one short line each.  Read only the first {@link #getOptionCount()}. */
    public String[] getOptionLines() {
        return optionLines;
    }

    /** How many answer buttons are on screen — zero once the player has answered. */
    public int getOptionCount() {
        return optionCount;
    }

    /** The kind of an option on screen (tests / telemetry), or null. */
    public ExchangeOptionKind getOptionKind(int optionIndex) {
        if (activeDefinition == null) return null;
        ExchangeOption option = activeDefinition.getOption(optionIndex);
        return option != null ? option.getKind() : null;
    }

    /** Seconds since the exchange opened — drives the Planet's jitter phase. */
    public float getElapsedSeconds() {
        return elapsedSeconds;
    }

    /** 0..1 fade multiplier for the whole modal (fade in -> held until answered -> fade out). */
    public float getVisibleFraction() {
        if (activeDefinition == null) return 0f;
        return GameMath.storyPanelVisibleFraction(elapsedSeconds,
                StoryUiConstants.STORY_EXCHANGE_FADE_IN_SECONDS,
                stage == Stage.CLOSING, closingElapsedSeconds,
                StoryUiConstants.STORY_EXCHANGE_FADE_OUT_SECONDS);
    }

    /**
     * Returns the speaker of a block that just appeared (for its audio sting), then clears the flag.
     * Null when nothing new appeared.
     */
    public Speaker consumeJustAppearedSpeaker() {
        Speaker speaker = justAppearedSpeaker;
        justAppearedSpeaker = null;
        return speaker;
    }

    // -------------------------------------------------------------------------
    // Effects the engine applies (the narrative layer never touches the inventory itself)
    // -------------------------------------------------------------------------

    /**
     * The {@code ItemType} name of a reward a probe just revealed, or null.  Reading it CLEARS it, so
     * a reward can never be granted twice.  Pair with {@link #getPendingRewardQuantity()}, which must
     * be read first.
     */
    public String consumePendingRewardItemTypeName() {
        String itemTypeName = pendingRewardItemTypeName;
        pendingRewardItemTypeName = null;
        return itemTypeName;
    }

    /** How many of the pending reward item to grant.  Read this BEFORE consuming the name. */
    public int getPendingRewardQuantity() {
        return pendingRewardQuantity;
    }

    /**
     * True once the player has committed to an ENDING and the exchange that asked has closed
     * (order-5).  Deliberately gated on the panel being gone: the reply to their own last answer is
     * the final thing they read as themselves, and the ending card must not land on top of it.
     */
    public boolean hasPendingEnding() {
        return pendingEndingVariant != null && activeDefinition == null;
    }

    /**
     * The {@link BootCardVariant} the player's last answer committed the run to, or null.  Reading it
     * CLEARS it, so an ending can never fire twice.  {@code World} hands the result straight to
     * {@code presentEndingCard} — the narrative layer names the ending and nothing more.
     */
    public BootCardVariant consumePendingEndingVariant() {
        if (activeDefinition != null) return null;   // still on screen: the answer is not final yet
        BootCardVariant variant = pendingEndingVariant;
        pendingEndingVariant = null;
        return variant;
    }
}
