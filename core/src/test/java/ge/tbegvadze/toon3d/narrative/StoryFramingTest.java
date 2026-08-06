package ge.tbegvadze.toon3d.narrative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.HudConstants;
import ge.tbegvadze.toon3d.util.StoryUiConstants;
import ge.tbegvadze.toon3d.util.TouchConstants;

/**
 * THE FRAMING SCREENS (Story UI order-8) — the screens that book-end play.
 *
 * <p>What is asserted here is what the design would quietly lose if it ever broke:
 *
 * <ol>
 *   <li><b>The first line the player ever reads is a death notice</b>, and it is the SAME string the
 *       reprint card prints — one machine, one voice, one id.  A first-timer reads it as flavour;
 *       hours later they learn it was announcing the original self's death all along.</li>
 *   <li><b>The endings subvert the launch screen.</b>  Once a story has ended, the framing every run
 *       has worn prints that ending's card instead — a depleted reserve where the sufficient one
 *       used to be.</li>
 *   <li><b>Nothing advances itself.</b>  No screen in the layer commits anything on a timer, and a
 *       row that carries consequences opens a confirmation instead of acting (order-6 Part C).</li>
 *   <li><b>The geometry is thumb-first and on screen.</b>  A menu that runs off the bottom of a
 *       phone, or a row too small to hit, is a menu nobody can use.</li>
 * </ol>
 *
 * <p>Headless — no LibGDX context anywhere, like every other narrative test.
 */
class StoryFramingTest {

    private static final float FRAME_SECONDS = 1f / 60f;

    private static TitleScreenSystem newTitleScreen(StoryProgress progress) {
        return new TitleScreenSystem(BootCardCatalog.defaultRegistry(), StoryStrings.defaults(),
                                     progress);
    }

    private static void advance(TitleScreenSystem title, float seconds) {
        int frames = Math.max(1, Math.round(seconds / FRAME_SECONDS));
        for (int frameIndex = 0; frameIndex < frames; frameIndex++) {
            title.update(FRAME_SECONDS);
        }
    }

    /** The row a menu item sits on, or -1 when the screen is not offering it at all. */
    private static int rowOf(TitleScreenSystem title, TitleMenuItem wanted) {
        for (int rowIndex = 0; rowIndex < title.getMenu().getRowCount(); rowIndex++) {
            if (title.getItem(rowIndex) == wanted) return rowIndex;
        }
        return -1;
    }

    // -------------------------------------------------------------------------
    // 1. The launch screen opens on the death phrase — and shares it with the card
    // -------------------------------------------------------------------------

    /**
     * The very first launch prints the FIRST-PRINT block — the four plain lines written for somebody
     * who has never heard of any of this (narrative-rework order-2 A) — and they are the BOOT CARD's
     * own ids, because the launch screen and the card are one machine talking.
     */
    @Test
    void theFirstLaunchEverPrintsTheFirstPrintBlock() {
        TitleScreenSystem title = newTitleScreen(new StoryProgress());
        BootCardDefinition firstPrintCard =
                BootCardCatalog.defaultRegistry().getCard(BootCardVariant.FIRST_PRINT);
        StoryStrings strings = StoryStrings.defaults();

        assertEquals(firstPrintCard.getLaunchStatusLineCount(), title.getStatusLineCount(),
                "the first launch does not print the whole first-print notice");
        for (int lineIndex = 0; lineIndex < title.getStatusLineCount(); lineIndex++) {
            assertEquals(strings.get(firstPrintCard.getSystemLineStringIds()[lineIndex]),
                         title.getStatusLines()[lineIndex],
                         "launch line " + lineIndex + " is not the boot card's own string");
        }
    }

    /** Once the player has been printed, the launch screen prints the ORDINARY reprint notice. */
    @Test
    void aSaveWithPrintsBehindItGetsTheReprintBlock() {
        StoryProgress progress = new StoryProgress();
        progress.recordReprint();
        TitleScreenSystem title = newTitleScreen(progress);
        BootCardDefinition reprintCard =
                BootCardCatalog.defaultRegistry().getCard(BootCardVariant.REPRINT);
        StoryStrings strings = StoryStrings.defaults();

        assertEquals(reprintCard.getLaunchStatusLineCount(), title.getStatusLineCount(),
                "the launch screen borrows the wrong number of the card's lines");
        for (int lineIndex = 0; lineIndex < title.getStatusLineCount(); lineIndex++) {
            assertEquals(strings.get(reprintCard.getSystemLineStringIds()[lineIndex]),
                         title.getStatusLines()[lineIndex],
                         "launch line " + lineIndex + " is not the boot card's own string");
        }
        // The title screen stops before the card's closing line: that is a promise about a run that
        // has not started, and this screen makes no promises.
        assertTrue(title.getStatusLineCount() < reprintCard.getSystemLineCount(),
                "the launch screen promised a reprint it has not started");
    }

    /**
     * The first line anybody ever reads says, in a word a stranger can parse cold, that somebody
     * died.  It used to say "PREVIOUS INSTANCE - TERMINATED": two undefined nouns about an event
     * that happened off screen to somebody they had not met.
     */
    @Test
    void theVeryFirstThingPrintedIsADeathNotice() {
        TitleScreenSystem title = newTitleScreen(new StoryProgress());
        String firstLine = title.getStatusLines()[0].toUpperCase(java.util.Locale.ROOT);
        assertTrue(firstLine.contains("DECEASED"),
                "the first line the player ever reads must be a death notice, was: " + firstLine);
        assertFalse(firstLine.contains("INSTANCE"),
                "the first line the player ever reads leans on a word nothing has defined yet");
    }

    /**
     * "SOUL RESERVE" is a Region-3 reveal, and the launch screen is bound by the same ladder the
     * card is: a save that has not been to the Harvesting Galleries never sees the phrase.
     */
    @Test
    void theLaunchScreenNeverSpendsTheReserveRevealEarly() {
        StoryProgress shallow = new StoryProgress();
        shallow.recordReprint();
        TitleScreenSystem shallowTitle = newTitleScreen(shallow);
        for (int lineIndex = 0; lineIndex < shallowTitle.getStatusLineCount(); lineIndex++) {
            assertFalse(shallowTitle.getStatusLines()[lineIndex].contains("SOUL"),
                    "the launch screen spent the Region-3 reveal on a player who cannot read it");
        }

        StoryProgress deep = new StoryProgress();
        deep.recordReprint();
        deep.reachRegion(StoryRegion.HARVESTING_GALLERIES);
        TitleScreenSystem deepTitle = newTitleScreen(deep);
        boolean printsTheReserve = false;
        for (int lineIndex = 0; lineIndex < deepTitle.getStatusLineCount(); lineIndex++) {
            if (deepTitle.getStatusLines()[lineIndex].contains("SOUL RESERVE")) printsTheReserve = true;
        }
        assertTrue(printsTheReserve,
                "a player who has earned the reserve line is not being shown it");
    }

    @Test
    void anEndingSubvertsTheLaunchScreenForGood() {
        StoryProgress progress = new StoryProgress();
        TitleScreenSystem beforeEnding = newTitleScreen(progress);
        String reserveLineBefore = beforeEnding.getStatusLines()[1];

        progress.recordEndingReached(BootCardVariant.ENDING_FREE.name());
        TitleScreenSystem afterEnding = newTitleScreen(progress);

        BootCardDefinition freeCard =
                BootCardCatalog.defaultRegistry().getCard(BootCardVariant.ENDING_FREE);
        assertEquals(StoryStrings.defaults().get(freeCard.getSystemLineStringIds()[1]),
                     afterEnding.getStatusLines()[1],
                     "after FREE the launch screen must print the ending's card, not the reprint's");
        assertFalse(reserveLineBefore.equals(afterEnding.getStatusLines()[1]),
                "the ending changed nothing about the framing — the payload is the subversion");
    }

    @Test
    void mergeLeavesTheLaunchScreenOnItsOrdinaryNotice() {
        // MERGE registers no card at all (the interface stopped being the player's), so the launch
        // screen has nothing of its own to print and must fall back rather than blank out.
        StoryProgress progress = new StoryProgress();
        progress.recordEndingReached(BootCardVariant.ENDING_MERGE.name());
        TitleScreenSystem title = newTitleScreen(progress);
        assertTrue(title.getStatusLineCount() > 0, "the launch screen blanked out");
        assertTrue(title.getStatusLineCount() <= StoryUiConstants.STORY_TITLE_STATUS_LINE_COUNT);
        for (int lineIndex = 0; lineIndex < title.getStatusLineCount(); lineIndex++) {
            assertNotNull(title.getStatusLines()[lineIndex], "a launch line resolved to nothing");
        }
    }

    @Test
    void anUnknownEndingNameNeverTakesTheLaunchScreenDown() {
        StoryProgress progress = new StoryProgress();
        progress.recordEndingReached("A_VARIANT_FROM_A_NEWER_BUILD");
        TitleScreenSystem title = newTitleScreen(progress);
        assertTrue(title.getStatusLineCount() > 0, "the launch screen blanked out");
        assertTrue(title.getStatusLineCount() <= StoryUiConstants.STORY_TITLE_STATUS_LINE_COUNT);
    }

    // -------------------------------------------------------------------------
    // 1b. The death stroke — the moment of death is three words and nothing else
    // -------------------------------------------------------------------------

    /**
     * The whole of what the game says at the moment of death.  It is a beat of its OWN, ahead of the
     * reprint card, because the two screens do two different jobs — this one reports a death, the
     * card reports a birth — and printed together neither lands.
     */
    @Test
    void theDeathStrokeIsThreeWordsAndNothingElse() {
        String stroke = StoryStrings.defaults().get(StoryUiConstants.STORY_DEATH_STROKE_ID);
        assertFalse(stroke.trim().isEmpty(), "the death screen says nothing at all");
        assertTrue(stroke.split("\\s+").length <= 3,
                "the moment of death is not the place for a sentence: " + stroke);
        assertTrue(stroke.toUpperCase(java.util.Locale.ROOT).contains("DIED"),
                "the death screen does not say that anybody died: " + stroke);
        // Whatever it says, it must not need a word the player has not been given.
        assertFalse(stroke.contains("INSTANCE"), "the death screen leans on undefined jargon");
        assertFalse(stroke.contains("SOUL"),     "the death screen spends a later reveal");
    }

    /**
     * It holds long enough to read and then hands over by itself, and it can be skipped.  Neither
     * end of that is optional: a player who just died is never held, and never rushed.
     */
    @Test
    void theDeathStrokeHoldsAndThenHandsOverWithoutTrappingAnyone() {
        assertTrue(StoryUiConstants.STORY_DEATH_STROKE_HOLD_SECONDS >= 1.2f,
                "the death stroke is gone before three words can be read");
        assertTrue(StoryUiConstants.STORY_DEATH_STROKE_HOLD_SECONDS <= 4f,
                "the death stroke holds a dead screen longer than anyone will sit through");
        assertTrue(StoryUiConstants.STORY_DEATH_STROKE_FADE_SECONDS
                        < StoryUiConstants.STORY_DEATH_STROKE_HOLD_SECONDS,
                "the words are still arriving when the screen is taken away");
        // It is drawn over a screen that is already black, never over the frozen corridor.
        assertTrue(StoryUiConstants.STORY_DEATH_STROKE_TOP_Y > 0f
                        && StoryUiConstants.STORY_DEATH_STROKE_TOP_Y <= Constants.WORLD_HEIGHT,
                "the death stroke is drawn off the screen");
    }

    // -------------------------------------------------------------------------
    // 2. The reveal is mood, never a gate
    // -------------------------------------------------------------------------

    @Test
    void theStatusLinesPrintOneByOneAndTheMenuArrivesLast() {
        TitleScreenSystem title = newTitleScreen(new StoryProgress());
        // The death notice is the FIRST thing on screen and the rest follows it, one line at a time.
        assertEquals(1, title.getRevealedStatusLineCount(),
                "the launch screen must open ON the death notice, not on an empty panel");
        assertFalse(title.isRevealComplete(), "the whole notice appeared at once");
        assertFalse(title.isMenuVisible(), "the menu must not be there before the machine has spoken");

        advance(title, StoryUiConstants.STORY_TITLE_LINE_REVEAL_SECONDS * 1.1f);
        assertEquals(2, title.getRevealedStatusLineCount(), "the second line never printed");

        advance(title, 10f);
        assertTrue(title.isRevealComplete(), "the notice never finished printing");
        assertTrue(title.isTitleVisible(), "the title never resolved");
        assertTrue(title.isMenuVisible(), "the menu never arrived");
    }

    @Test
    void aTapPrintsTheWholeScreenAtOnce() {
        TitleScreenSystem title = newTitleScreen(new StoryProgress());
        title.skipReveal();
        assertTrue(title.isRevealComplete(), "a tap must print the notice immediately");
        assertTrue(title.isMenuVisible(), "a tap must bring the menu with it — the reveal is not a gate");
        assertEquals(1f, title.getMenuVisibleFraction(), 0.0001f);
    }

    // -------------------------------------------------------------------------
    // 3. The menu: what is offered, and what has to be confirmed
    // -------------------------------------------------------------------------

    /**
     * The way down is always the first row; what changes is what it is CALLED (narrative-rework
     * order-2 A).  With nothing behind them, "CONTINUE DESCENT" is a lie about a thing that never
     * happened — and hiding the row entirely used to leave NEW OPERATOR, the save wipe, as the way a
     * first-timer started the game.
     */
    @Test
    void theWayDownIsAlwaysTheFirstRowAndReadsBeginBeforeAnyoneHasBeenPrinted() {
        StoryProgress freshSave = new StoryProgress();
        TitleScreenSystem beforeAPrint = newTitleScreen(freshSave);
        assertEquals(0, rowOf(beforeAPrint, TitleMenuItem.CONTINUE_DESCENT),
                "the way down must be the first row a first-timer sees");
        assertEquals(TitleMenuItem.CONTINUE_DESCENT.getFirstRunLabelStringId(),
                     beforeAPrint.getMenu().getLabelStringId(0),
                     "the first row offers to CONTINUE something that never happened");

        freshSave.recordReprint();
        TitleScreenSystem afterAPrint = newTitleScreen(freshSave);
        assertEquals(0, rowOf(afterAPrint, TitleMenuItem.CONTINUE_DESCENT),
                "once the player has been printed, continuing is the first row");
        assertEquals(TitleMenuItem.CONTINUE_DESCENT.getLabelStringId(),
                     afterAPrint.getMenu().getLabelStringId(0),
                     "a player with a descent behind them is still being told to BEGIN one");
    }

    @Test
    void everyTitleRowIsReachableAndOffersTheArchiveAndSettings() {
        StoryProgress progress = new StoryProgress();
        progress.recordReprint();
        TitleScreenSystem title = newTitleScreen(progress);
        assertEquals(TitleMenuItem.values().length, title.getMenu().getRowCount(),
                "a save with progress offers every row");
        for (TitleMenuItem item : TitleMenuItem.values()) {
            assertTrue(rowOf(title, item) >= 0, item + " is not reachable from the title screen");
        }
    }

    @Test
    void wipingTheSaveAlwaysAsksFirst() {
        StoryProgress progress = new StoryProgress();
        progress.recordReprint();   // there is now something to lose
        TitleScreenSystem title = newTitleScreen(progress);
        int newOperatorRow = rowOf(title, TitleMenuItem.NEW_OPERATOR);

        assertNull(title.activateRow(newOperatorRow),
                "NEW OPERATOR must not act on its own tap — it wipes the save");
        assertTrue(title.getMenu().isConfirmOpen(), "no confirmation was raised");
        assertEquals(TitleMenuItem.NEW_OPERATOR.getConfirmStringId(),
                     title.getMenu().getConfirmPromptStringId());
        assertNull(title.consumeConfirmedItem(), "an open confirmation must commit nothing");
    }

    @Test
    void aConfirmationCanAlwaysBeBackedOutOf() {
        StoryProgress progress = new StoryProgress();
        progress.recordReprint();
        TitleScreenSystem title = newTitleScreen(progress);
        title.activateRow(rowOf(title, TitleMenuItem.NEW_OPERATOR));

        title.getMenu().cancelConfirm();
        assertFalse(title.getMenu().isConfirmOpen(), "CANCEL left the confirmation open");
        assertNull(title.consumeConfirmedItem(), "cancelling committed the row anyway");
    }

    @Test
    void onlyAConfirmationCanCommitTheWipe() {
        StoryProgress progress = new StoryProgress();
        progress.recordReprint();
        TitleScreenSystem title = newTitleScreen(progress);
        title.activateRow(rowOf(title, TitleMenuItem.NEW_OPERATOR));

        title.acceptConfirm();
        assertEquals(TitleMenuItem.NEW_OPERATOR, title.consumeConfirmedItem(),
                "PROCEED did not commit the row it was raised for");
        assertNull(title.consumeConfirmedItem(), "a committed row must be readable exactly once");
        assertFalse(title.getMenu().isConfirmOpen(), "the confirmation stayed on screen");
    }

    @Test
    void aBrandNewSaveIsNeverAskedAboutWipingNothing() {
        TitleScreenSystem title = newTitleScreen(new StoryProgress());
        int newOperatorRow = rowOf(title, TitleMenuItem.NEW_OPERATOR);
        assertEquals(TitleMenuItem.NEW_OPERATOR, title.activateRow(newOperatorRow),
                "with nothing to wipe, NEW OPERATOR must act at once rather than ask");
        assertFalse(title.getMenu().isConfirmOpen(),
                "a scary question about no consequence at all was asked anyway");
    }

    @Test
    void openingTheArchiveOrTheSettingsNeverAsks() {
        StoryProgress progress = new StoryProgress();
        progress.recordReprint();
        TitleScreenSystem title = newTitleScreen(progress);
        assertEquals(TitleMenuItem.CODEX, title.activateRow(rowOf(title, TitleMenuItem.CODEX)));
        assertEquals(TitleMenuItem.SETTINGS, title.activateRow(rowOf(title, TitleMenuItem.SETTINGS)));
    }

    // -------------------------------------------------------------------------
    // 4. Nothing in the layer advances itself (order-6 Part C, restated for order-8)
    // -------------------------------------------------------------------------

    @Test
    void theLaunchScreenNeverCommitsAnythingOnItsOwn() {
        StoryProgress progress = new StoryProgress();
        progress.recordReprint();
        TitleScreenSystem title = newTitleScreen(progress);
        advance(title, 120f);   // two minutes of a player who put the phone down
        assertNull(title.consumeConfirmedItem(), "the title screen picked something by itself");
        assertFalse(title.getMenu().isConfirmOpen(), "a confirmation opened with no tap behind it");
        assertEquals(-1, title.getMenu().getPressedIndex(), "a row lit up on its own");
    }

    @Test
    void anOpenConfirmationRefusesEveryOtherRow() {
        StoryProgress progress = new StoryProgress();
        progress.recordReprint();
        TitleScreenSystem title = newTitleScreen(progress);
        title.activateRow(rowOf(title, TitleMenuItem.NEW_OPERATOR));

        assertNull(title.activateRow(rowOf(title, TitleMenuItem.QUIT)),
                "a row behind an open confirmation must be unreachable");
    }

    // -------------------------------------------------------------------------
    // 5. The menu model itself
    // -------------------------------------------------------------------------

    @Test
    void aDisabledRowRefusesTapsAndNeverLightsUp() {
        FramingMenu menu = new FramingMenu();
        menu.addRow("story.title.continue", null, false);
        menu.setPressedIndex(0);
        assertEquals(-1, menu.getPressedIndex(), "a disabled row lit up under the thumb");
        assertFalse(menu.activateRow(0, null), "a disabled row acted on a tap");
    }

    @Test
    void anOutOfRangeRowIsSimplyRefused() {
        FramingMenu menu = new FramingMenu();
        menu.addRow("story.pause.resume", null, true);
        assertFalse(menu.activateRow(7, "story.pause.confirm.abandon"),
                "a row that does not exist was activated");
        assertFalse(menu.isConfirmOpen(), "a confirmation opened for a row that does not exist");
        assertFalse(menu.activateRow(-1, null));
    }

    @Test
    void aMenuNeverGrowsPastItsCap() {
        FramingMenu menu = new FramingMenu();
        for (int rowIndex = 0; rowIndex < StoryUiConstants.STORY_FRAME_MENU_MAX_ROWS + 4; rowIndex++) {
            menu.addRow("story.pause.resume", null, true);
        }
        assertEquals(StoryUiConstants.STORY_FRAME_MENU_MAX_ROWS, menu.getRowCount());
    }

    @Test
    void thePauseMenuOffersResumeArchiveSettingsAndAbandonAndOnlyAsksAboutTheLastOne() {
        assertEquals(PauseMenuItem.RESUME, PauseMenuItem.values()[0],
                "RESUME must be the first row — the common case is getting back to the floor");
        for (PauseMenuItem item : PauseMenuItem.values()) {
            boolean shouldAsk = item == PauseMenuItem.ABANDON_RUN;
            assertEquals(shouldAsk, item.requiresConfirmation(),
                    item + " asks the wrong question count before acting");
        }
        assertTrue(PauseMenuItem.values().length <= StoryUiConstants.STORY_FRAME_MENU_MAX_ROWS);
    }

    @Test
    void theSettingsScreenShowsEveryKnobWithItsCurrentValueAndABackRow() {
        StorySettings settings = new StorySettings();
        FramingMenu   menu     = new FramingMenu();
        menu.rebuildAsSettings(settings);

        assertEquals(StoryUiConstants.STORY_CODEX_SETTING_COUNT + 1, menu.getRowCount(),
                "the settings screen is the four knobs plus BACK");
        for (int settingIndex = 0; settingIndex < StoryUiConstants.STORY_CODEX_SETTING_COUNT;
                settingIndex++) {
            assertEquals(StorySettings.getSettingNameStringId(settingIndex),
                         menu.getLabelStringId(settingIndex));
            assertEquals(settings.getSettingValueStringId(settingIndex),
                         menu.getValueStringId(settingIndex),
                         "a knob row does not show what it is currently set to");
        }
        assertTrue(menu.isSettingsBackRow(StoryUiConstants.STORY_CODEX_SETTING_COUNT));
        assertNull(menu.getValueStringId(StoryUiConstants.STORY_CODEX_SETTING_COUNT),
                "BACK is a command, not a state");
    }

    @Test
    void cyclingAKnobIsVisibleOnTheRowImmediately() {
        StorySettings settings = new StorySettings();
        FramingMenu   menu     = new FramingMenu();
        menu.rebuildAsSettings(settings);
        String valueBefore = menu.getValueStringId(0);

        settings.cycleSetting(0);
        menu.rebuildAsSettings(settings);
        assertFalse(valueBefore.equals(menu.getValueStringId(0)),
                "cycling a knob did not change what its row reads");
    }

    // -------------------------------------------------------------------------
    // 6. The ending record — persistent, and cleared only by a new game
    // -------------------------------------------------------------------------

    @Test
    void theEndingSurvivesAsPersistentStateAndAWipeClearsIt() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();
        StoryProgress progress = new StoryProgress(store);
        assertNull(progress.getEndingReached(), "a fresh save has no ending");
        assertFalse(progress.hasReachedEnding());

        progress.recordEndingReached(BootCardVariant.ENDING_OBEY.name());
        assertEquals(BootCardVariant.ENDING_OBEY.name(), new StoryProgress(store).getEndingReached(),
                "the ending did not survive being read back from the store");

        progress.wipeAllNarrativeState();
        assertNull(new StoryProgress(store).getEndingReached(),
                "a new game must forget how the last story ended");
    }

    @Test
    void aDeathNeverTouchesTheEndingRecord() {
        InMemoryStoryProgressStore store = new InMemoryStoryProgressStore();
        StoryProgress progress = new StoryProgress(store);
        progress.recordEndingReached(BootCardVariant.ENDING_KILL.name());
        for (int deathIndex = 0; deathIndex < 20; deathIndex++) {
            progress.recordReprint();   // a death is what makes the counter climb
        }
        assertEquals(BootCardVariant.ENDING_KILL.name(), progress.getEndingReached());
    }

    // -------------------------------------------------------------------------
    // 7. Geometry: thumb-first, and on the screen
    // -------------------------------------------------------------------------

    @Test
    void everyMenuFitsOnScreenWithItsLongestRowCount() {
        float titleMenuBottom = StoryUiConstants.STORY_TITLE_MENU_TOP_Y
                - TitleMenuItem.values().length * StoryUiConstants.STORY_FRAME_MENU_ROW_PITCH;
        assertTrue(titleMenuBottom > 0f,
                "the title menu runs off the bottom of the screen: " + titleMenuBottom);

        float longestMenuBottom = StoryUiConstants.STORY_FRAME_MENU_TOP_Y
                - StoryUiConstants.STORY_FRAME_MENU_MAX_ROWS
                        * StoryUiConstants.STORY_FRAME_MENU_ROW_PITCH;
        assertTrue(longestMenuBottom > 0f,
                "a full-length menu runs off the bottom of the screen: " + longestMenuBottom);
        assertTrue(StoryUiConstants.STORY_FRAME_MENU_TOP_Y <= Constants.WORLD_HEIGHT);
    }

    @Test
    void everyTapTargetIsThumbSized() {
        // The exchange layer's answer plates are the established floor for "a comfortable tap".
        float minimumHeight = StoryUiConstants.STORY_CHOICE_BUTTON_MIN_HEIGHT * 0.9f;
        assertTrue(StoryUiConstants.STORY_FRAME_MENU_ROW_HEIGHT >= minimumHeight,
                "menu rows are too small to hit with a thumb");
        assertTrue(StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_HEIGHT >= minimumHeight);
        assertTrue(StoryUiConstants.STORY_REPORT_OPEN_HEIGHT >= minimumHeight);
        assertTrue(StoryUiConstants.STORY_REPORT_BACK_HEIGHT >= minimumHeight);
        assertTrue(StoryUiConstants.STORY_FRAME_MENU_ROW_GAP > 0f,
                "rows with no gap between them make a mis-tap indistinguishable from a tap");
    }

    @Test
    void theConfirmationsButtonsAreSeparatedAndOnScreen() {
        float yesRight = StoryUiConstants.STORY_FRAME_CONFIRM_YES_X
                + StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_WIDTH;
        assertTrue(yesRight < StoryUiConstants.STORY_FRAME_CONFIRM_NO_X,
                "PROCEED and CANCEL overlap — the one tap that must never slip");
        assertTrue(StoryUiConstants.STORY_FRAME_CONFIRM_YES_X > 0f);
        assertTrue(StoryUiConstants.STORY_FRAME_CONFIRM_NO_X
                + StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_WIDTH <= Constants.WORLD_WIDTH);
        assertTrue(StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_Y > 0f,
                "the confirmation's buttons hang off the bottom of the screen");
        assertTrue(StoryUiConstants.STORY_FRAME_CONFIRM_TOP_Y <= Constants.WORLD_HEIGHT);
    }

    @Test
    void theLaunchStackReadsTopDownWithoutColliding() {
        float lastStatusLineY = StoryUiConstants.STORY_TITLE_STATUS_TOP_Y
                - (StoryUiConstants.STORY_TITLE_STATUS_LINE_COUNT - 1)
                        * StoryUiConstants.STORY_TITLE_STATUS_LINE_PITCH;
        assertTrue(StoryUiConstants.STORY_TITLE_STATUS_TOP_Y <= Constants.WORLD_HEIGHT,
                "the death notice starts above the top of the screen");
        assertTrue(lastStatusLineY > StoryUiConstants.STORY_TITLE_GAME_TOP_Y,
                "the title resolves on top of the notice");
        assertTrue(StoryUiConstants.STORY_TITLE_GAME_TOP_Y > StoryUiConstants.STORY_TITLE_TAGLINE_TOP_Y);
        assertTrue(StoryUiConstants.STORY_TITLE_TAGLINE_TOP_Y > StoryUiConstants.STORY_TITLE_MENU_TOP_Y,
                "the menu overlaps the title above it");
    }

    @Test
    void theReportPageFitsOnScreenAndClearsItsOwnBackButton() {
        assertTrue(StoryUiConstants.STORY_REPORT_PANEL_TOP_Y <= Constants.WORLD_HEIGHT);
        assertTrue(StoryUiConstants.STORY_REPORT_PANEL_Y
                > StoryUiConstants.STORY_REPORT_BACK_Y + StoryUiConstants.STORY_REPORT_BACK_HEIGHT,
                "the report panel sits on top of its own BACK plate");
        // The lines have to fit inside the panel, or the autopsy prints past the bottom edge.
        float lastLineY = StoryUiConstants.STORY_REPORT_PANEL_TOP_Y
                - StoryUiConstants.STORY_REPORT_FIRST_LINE_INSET_Y
                - (StoryUiConstants.STORY_REPORT_MAX_LINES - 1)
                        * StoryUiConstants.STORY_REPORT_LINE_PITCH;
        assertTrue(lastLineY > StoryUiConstants.STORY_REPORT_PANEL_Y,
                "a full report overflows its panel: " + lastLineY);
    }

    @Test
    void theReportOpenPlateStaysClearOfTheCardsContinueButton() {
        // The REPORT footnote must never crowd the button the player taps a hundred times.
        float openRight = StoryUiConstants.STORY_REPORT_OPEN_X + StoryUiConstants.STORY_REPORT_OPEN_WIDTH;
        assertTrue(openRight < StoryUiConstants.STORY_BOOT_CONTINUE_X,
                "the REPORT plate overlaps CONTINUE");
        assertTrue(StoryUiConstants.STORY_REPORT_OPEN_X > 0f);
        assertTrue(StoryUiConstants.STORY_REPORT_OPEN_Y > 0f);
    }

    @Test
    void thePauseButtonClearsEverythingElseOnScreen() {
        float pauseLeft   = TouchConstants.PAUSE_BUTTON_CENTER_X - TouchConstants.PAUSE_BUTTON_SIZE / 2f;
        float pauseRight  = TouchConstants.PAUSE_BUTTON_CENTER_X + TouchConstants.PAUSE_BUTTON_SIZE / 2f;
        float pauseBottom = TouchConstants.PAUSE_BUTTON_CENTER_Y - TouchConstants.PAUSE_BUTTON_SIZE / 2f;
        float pauseTop    = TouchConstants.PAUSE_BUTTON_CENTER_Y + TouchConstants.PAUSE_BUTTON_SIZE / 2f;
        float leftClusterLeftEdge =
                TouchConstants.TOUCH_GRID_LEFT_CENTER_X - TouchConstants.TOUCH_BUTTON_SIZE / 2f;

        assertTrue(pauseLeft >= 0f, "the pause button hangs off the left edge of the screen");
        assertTrue(pauseBottom > HudConstants.HUD_HEIGHT, "the pause button sits on the HUD chrome");
        assertTrue(pauseTop < Constants.MINI_MAP_ORIGIN_Y, "the pause button sits on the mini-map");
        assertTrue(pauseRight < leftClusterLeftEdge,
                "the pause button crowds the secondary thumb cluster — pausing must never be a mis-tap");
        assertTrue(TouchConstants.PAUSE_BUTTON_SIZE >= StoryUiConstants.STORY_CHOICE_BUTTON_MIN_HEIGHT,
                "the pause button is smaller than a comfortable tap target");
    }

    @Test
    void mergeHoldsLongEnoughToReadAsAnEndingAndThenStandsDown() {
        assertTrue(StoryUiConstants.STORY_ENDING_MERGE_HOLD_SECONDS >= 3f,
                "MERGE's silent hold is too short to read as anything but a bug");
        assertTrue(StoryUiConstants.STORY_ENDING_MERGE_HOLD_SECONDS <= 15f,
                "MERGE holds an unresponsive screen for longer than anyone will believe is deliberate");
    }
}
