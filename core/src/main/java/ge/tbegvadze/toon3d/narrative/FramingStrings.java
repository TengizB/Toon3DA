package ge.tbegvadze.toon3d.narrative;

import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * The FRAMING SCREENS' string table (Story UI order-8) — the Java fallback half of the localisation
 * rule for every screen that BOOK-ENDS play: the launch / title screen, the pause menu, the settings
 * menu, the folded reprint report and the ending's way out.
 *
 * <p>Mirrors the {@code story.title.* / story.pause.* / story.settings.* / story.report.* /
 * story.frame.*} block of {@code assets/story/story-strings.properties}, which is the table the game
 * actually ships and a translator actually receives; this one is the safety net when that asset is
 * missing or unreadable ({@link StoryStrings#defaults()}).  {@code StoryLocalizationTest} asserts the
 * two agree, id for id.
 *
 * <p>What is deliberately NOT here: the launch screen's two status lines.  Those are the SAME ids the
 * reprint card prints ({@code story.boot.system.*}), because the death notice the player reads before
 * they have done anything is literally the card that frames every run — one machine, one voice, one
 * string.  The endings subvert the launch screen for free by pointing it at their own card's ids.
 *
 * <p>Headless: no LibGDX imports.
 */
public final class FramingStrings {

    private FramingStrings() {}

    /** Adds every framing-screen line to {@code strings}.  Called by {@link StoryStrings#defaults()}. */
    public static void registerDefaults(StoryStrings strings) {
        // --- The title itself: the being, then the wound bored into it (story/00-premise.md) ---
        strings.put(StoryUiConstants.STORY_TITLE_GAME_ID,    "EREBUS");
        strings.put(StoryUiConstants.STORY_TITLE_TAGLINE_ID, "THE DEEPWORKS");

        // --- Title menu: cold, terminal-styled, no feature list ---
        strings.put(TitleMenuItem.CONTINUE_DESCENT.getLabelStringId(), "CONTINUE DESCENT");
        // What that row reads before the player has ever been printed (narrative-rework order-2 A).
        strings.put(TitleMenuItem.CONTINUE_DESCENT.getFirstRunLabelStringId(), "BEGIN DESCENT");
        strings.put(TitleMenuItem.NEW_OPERATOR.getLabelStringId(),     "NEW OPERATOR");
        strings.put(TitleMenuItem.CODEX.getLabelStringId(),            "ARCHIVE");
        strings.put(TitleMenuItem.SETTINGS.getLabelStringId(),         "SETTINGS");
        strings.put(TitleMenuItem.QUIT.getLabelStringId(),             "QUIT");
        strings.put(TitleMenuItem.NEW_OPERATOR.getConfirmStringId(),
                "This wipes the current operator line and all recovered memory. Proceed?");

        // --- Pause: the suit, held. Never a plot summary; the archive holds the lore ---
        strings.put(StoryUiConstants.STORY_PAUSE_TITLE_ID,             "SUIT PAUSED");
        strings.put(PauseMenuItem.RESUME.getLabelStringId(),           "RESUME");
        strings.put(PauseMenuItem.CODEX.getLabelStringId(),            "ARCHIVE");
        strings.put(PauseMenuItem.SETTINGS.getLabelStringId(),         "SETTINGS");
        strings.put(PauseMenuItem.ABANDON_RUN.getLabelStringId(),      "ABANDON RUN");
        strings.put(PauseMenuItem.ABANDON_RUN.getConfirmStringId(),
                "Abandon this instance and return to standby?");

        // --- Settings screen (the codex strip's four knobs, on a screen of their own) ---
        strings.put(StoryUiConstants.STORY_SETTINGS_TITLE_ID, "SETTINGS");
        strings.put(StoryUiConstants.STORY_SETTINGS_BACK_ID,  "BACK");

        // --- Shared framing chrome ---
        strings.put(StoryUiConstants.STORY_FRAME_CONFIRM_YES_ID, "PROCEED");
        strings.put(StoryUiConstants.STORY_FRAME_CONFIRM_NO_ID,  "CANCEL");
        // The way out of an ending that authorises no reprint.  Not "continue": nothing continues.
        strings.put(StoryUiConstants.STORY_FRAME_RETURN_ID,      "RETURN TO STANDBY");

        // --- The folded reprint report: what the terminated instance managed, in machine voice ---
        strings.put(StoryUiConstants.STORY_REPORT_TITLE_ID,  "PREVIOUS INSTANCE");
        strings.put(StoryUiConstants.STORY_REPORT_OPEN_ID,   "REPORT");
        strings.put(StoryUiConstants.STORY_REPORT_BACK_ID,   "BACK");
        strings.put(StoryUiConstants.STORY_REPORT_FLOOR_ID,  "DEPTH REACHED");
        strings.put(StoryUiConstants.STORY_REPORT_KILLS_ID,  "HOSTILES PURGED");
        strings.put(StoryUiConstants.STORY_REPORT_DAMAGE_ID, "DAMAGE SUSTAINED");
        strings.put(StoryUiConstants.STORY_REPORT_TIME_ID,   "TIME IN FACILITY");
        strings.put(StoryUiConstants.STORY_REPORT_BEST_ID,   "RECORD");
    }
}
