package ge.tbegvadze.toon3d.narrative;

import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * The LAUNCH / TITLE screen (Story UI order-8 Part A) — the headless brain behind the first thing
 * the player ever sees, and the screen every abandoned or finished run returns to.
 *
 * <p><b>There is no cheerful title screen.</b>  The app opens on black, and the first line anyone
 * reads is the game telling them they are already dead:
 *
 * <pre>
 *     PREVIOUS INSTANCE - TERMINATED
 *     SOUL RESERVE .......... SUFFICIENT
 * </pre>
 *
 * ...then, after a beat, the title resolves quietly beneath it, and the menu fades in under that.
 * Those two lines are not written here: they are the SAME localisation ids the reprint card prints
 * ({@link BootCardRegistry}), so the launch screen and the boot card are provably one machine
 * speaking, and a first-timer's "flavour text" turns out — hours later — to have been a literal
 * death notice all along (story/dialog/system-cards.md).
 *
 * <h3>The endings subvert it for free</h3>
 * Once a run has ENDED, {@link StoryProgress#getEndingReached()} names the variant, and this screen
 * simply prints THAT card's status lines instead — a depleted reserve where the sufficient one used
 * to be.  The framing the player has read on every launch is the instrument, exactly as order-3
 * spends it on the card itself.  No conditional in a renderer; a different id, same screen.
 *
 * <h3>What it decides</h3>
 * <ul>
 *   <li>which two status lines print, and how much of them has printed so far (a paced reveal, and
 *       a tap always skips it — the reveal is mood, never a gate);</li>
 *   <li>which menu rows exist: CONTINUE DESCENT is offered only once the player has actually been
 *       printed at least once, because "continue" with nothing to continue is a lie;</li>
 *   <li>when a row acts and when it must ask first — wiping the save is the one thing in the game
 *       that moves the story backwards, so it is never one tap away (order-7 Part B).</li>
 * </ul>
 *
 * <p>Nothing here is on a timer that ADVANCES anything: the screen waits for the player forever.
 *
 * <p>Headless: no LibGDX imports; unit-tested without a render context.
 */
public final class TitleScreenSystem {

    private final BootCardRegistry cards;
    private final StoryStrings     strings;
    private final StoryProgress    progress;
    private final FramingMenu      menu = new FramingMenu();

    /** The status lines on screen, resolved once in {@link #present()}. */
    private final String[] statusLines =
            new String[StoryUiConstants.STORY_TITLE_STATUS_LINE_COUNT];
    private int statusLineCount;

    /** Which {@link TitleMenuItem} each menu row carries (rows may omit CONTINUE DESCENT). */
    private final TitleMenuItem[] rowItems =
            new TitleMenuItem[StoryUiConstants.STORY_FRAME_MENU_MAX_ROWS];

    private float   elapsedSeconds;
    private boolean revealComplete;
    /** The row a confirmation committed, latched for the engine to read once. */
    private TitleMenuItem confirmedItem;

    /**
     * @param cards    the boot-card catalog — the launch screen borrows its status-line ids
     * @param strings  the resolved localisation table
     * @param progress persistent narrative state: has the player ever been printed, and how did
     *                 their story end
     */
    public TitleScreenSystem(BootCardRegistry cards, StoryStrings strings, StoryProgress progress) {
        if (cards    == null) throw new IllegalArgumentException("cards must not be null");
        if (strings  == null) throw new IllegalArgumentException("strings must not be null");
        if (progress == null) throw new IllegalArgumentException("progress must not be null");
        this.cards    = cards;
        this.strings  = strings;
        this.progress = progress;
        present();
    }

    // -------------------------------------------------------------------------
    // Presenting the screen
    // -------------------------------------------------------------------------

    /**
     * Puts the screen up from the top: resolves the status lines for whatever the save now says, and
     * rebuilds the menu.  Called at construction and again whenever the save changes underneath it —
     * after a "new game" wipe, and after a run ends — so the screen never shows a state the save no
     * longer holds.
     */
    public void present() {
        elapsedSeconds  = 0f;
        revealComplete  = false;
        confirmedItem   = null;
        resolveStatusLines();
        rebuildMenu();
    }

    /**
     * Reads the two status lines off the card this save has earned: the ordinary reprint card, or —
     * once the story has been finished — the ending's subversion of it.  Only the first two lines are
     * used: the third ("REPRINTING FROM LAST CHECKPOINT") is a promise about a run that has not
     * started yet, and the title screen makes no promises.
     */
    private void resolveStatusLines() {
        statusLineCount = 0;
        BootCardDefinition definition = cards.getCard(endingVariantOrNull());
        if (definition == null) definition = cards.getCard(BootCardVariant.REPRINT);
        if (definition == null) return;   // an empty catalog prints nothing rather than crashing

        String[] stringIds = definition.getSystemLineStringIds();
        if (stringIds == null) return;
        int printedCount = Math.min(stringIds.length, statusLines.length);
        for (int lineIndex = 0; lineIndex < printedCount; lineIndex++) {
            // StoryStrings.get() never returns null — a missing id shows as a visible !id! marker.
            statusLines[lineIndex] = strings.get(stringIds[lineIndex]);
        }
        statusLineCount = printedCount;
    }

    /**
     * The ending this save reached, or null when the story is unfinished (and for MERGE, which
     * registers no card at all — the interface that stopped being the player's has nothing to print,
     * so the launch screen goes back to its ordinary death notice).
     */
    private BootCardVariant endingVariantOrNull() {
        String endingName = progress.getEndingReached();
        if (endingName == null) return null;
        for (BootCardVariant variant : BootCardVariant.values()) {
            if (variant.name().equals(endingName)) return variant;
        }
        return null;   // an unknown name from a newer save must never take the launch screen down
    }

    /**
     * Rebuilds the menu rows.  CONTINUE DESCENT appears only when the player has meta-progress to
     * continue INTO; on a brand-new save the first row is NEW OPERATOR, which needs no confirmation
     * because there is nothing yet to wipe.
     */
    private void rebuildMenu() {
        menu.clear();
        int rowIndex = 0;
        for (TitleMenuItem item : TitleMenuItem.values()) {
            if (item == TitleMenuItem.CONTINUE_DESCENT && !hasMetaProgress()) continue;
            rowItems[rowIndex++] = item;
            menu.addRow(item.getLabelStringId(), null, true);
        }
        while (rowIndex < rowItems.length) rowItems[rowIndex++] = null;
    }

    /**
     * True when this save holds a story to continue: the player has been printed at least once, which
     * is what every other persistent fact (deepest region, one-shot beats, the archive) hangs off.
     */
    public boolean hasMetaProgress() {
        return progress.getReprintCount() > 0;
    }

    // -------------------------------------------------------------------------
    // The paced reveal (mood only — a tap always skips it)
    // -------------------------------------------------------------------------

    public void update(float deltaTime) {
        elapsedSeconds += deltaTime;
    }

    /** The player tapped somewhere that is not a row: print the whole screen at once. */
    public void skipReveal() {
        revealComplete = true;
    }

    /** How many status lines have typed out so far.  Unhurried — this screen is not in a rush. */
    public int getRevealedStatusLineCount() {
        if (revealComplete) return statusLineCount;
        return GameMath.bootCardRevealedLineCount(elapsedSeconds,
                StoryUiConstants.STORY_TITLE_LINE_REVEAL_SECONDS, statusLineCount);
    }

    /** True once every status line is on screen. */
    public boolean isRevealComplete() {
        return revealComplete || getRevealedStatusLineCount() >= statusLineCount;
    }

    /** The title resolves quietly a beat AFTER the machine has finished talking. */
    public boolean isTitleVisible() {
        return revealComplete || elapsedSeconds >= titleAppearsAtSeconds();
    }

    /** The menu fades in under the title — the last thing to arrive, and it never leaves. */
    public boolean isMenuVisible() {
        return revealComplete || elapsedSeconds >= menuAppearsAtSeconds();
    }

    private float titleAppearsAtSeconds() {
        return statusLineCount * StoryUiConstants.STORY_TITLE_LINE_REVEAL_SECONDS
                + StoryUiConstants.STORY_TITLE_TITLE_DELAY_SECONDS;
    }

    private float menuAppearsAtSeconds() {
        return titleAppearsAtSeconds() + StoryUiConstants.STORY_TITLE_MENU_DELAY_SECONDS;
    }

    /** 0..1 fade of the whole screen — it rises once and then holds.  Nothing flashes. */
    public float getVisibleFraction() {
        return GameMath.storyPanelVisibleFraction(elapsedSeconds,
                StoryUiConstants.STORY_TITLE_FADE_IN_SECONDS, false, 0f,
                StoryUiConstants.STORY_FRAME_FADE_SECONDS);
    }

    /** 0..1 fade of the title + menu, so they arrive gently rather than popping in. */
    public float getMenuVisibleFraction() {
        if (revealComplete) return 1f;
        float sinceMenuAppeared = elapsedSeconds - menuAppearsAtSeconds();
        if (sinceMenuAppeared <= 0f) return 0f;
        float fraction = sinceMenuAppeared / StoryUiConstants.STORY_FRAME_FADE_SECONDS;
        return fraction >= 1f ? 1f : fraction;
    }

    // -------------------------------------------------------------------------
    // Render-side read model + input
    // -------------------------------------------------------------------------

    /** The machine-voice status lines.  Read only the first {@link #getStatusLineCount()} entries. */
    public String[] getStatusLines() {
        return statusLines;
    }

    public int getStatusLineCount() {
        return statusLineCount;
    }

    /** The menu model the renderer draws and the engine drives. */
    public FramingMenu getMenu() {
        return menu;
    }

    /** Which item row {@code rowIndex} carries, or null when there is no such row. */
    public TitleMenuItem getItem(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= menu.getRowCount()) return null;
        return rowItems[rowIndex];
    }

    /**
     * The player released on a row.  A row that carries consequences opens its confirmation instead
     * of acting, and only {@link #acceptConfirm()} can commit it.
     *
     * @return the item to act on now, or null when a confirmation opened (or the tap was refused)
     */
    public TitleMenuItem activateRow(int rowIndex) {
        TitleMenuItem item = getItem(rowIndex);
        if (item == null) return null;
        return menu.activateRow(rowIndex, confirmStringIdFor(item)) ? item : null;
    }

    /**
     * The question this row must ask first, or null when it may act at once.  NEW OPERATOR is the
     * exception worth spelling out: on a brand-new save it wipes nothing, so asking "this wipes
     * everything, proceed?" would be a scary question about no consequence at all.
     */
    private String confirmStringIdFor(TitleMenuItem item) {
        if (item == TitleMenuItem.NEW_OPERATOR && !hasMetaProgress()) return null;
        return item.getConfirmStringId();
    }

    /** The player said yes to the open confirmation. */
    public void acceptConfirm() {
        int confirmedRow = menu.getConfirmRowIndex();
        menu.acceptConfirm();
        if (menu.consumeConfirmedRow() >= 0) confirmedItem = getItem(confirmedRow);
    }

    /** Reads and clears the item a confirmation just committed, or null when nothing was confirmed. */
    public TitleMenuItem consumeConfirmedItem() {
        TitleMenuItem confirmed = confirmedItem;
        confirmedItem = null;
        return confirmed;
    }
}
