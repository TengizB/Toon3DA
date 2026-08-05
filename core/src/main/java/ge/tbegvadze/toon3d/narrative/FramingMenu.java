package ge.tbegvadze.toon3d.narrative;

import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * The FRAMING SCREENS' menu model (Story UI order-8) — one small headless state machine behind every
 * menu that book-ends play: the title screen's five rows, the pause menu's four, and the settings
 * screen's knobs.  {@code render/FramingScreenRenderer} draws whatever this reports and decides
 * nothing itself, exactly as every other story channel is split.
 *
 * <p>A row is a LABEL string id, an optional VALUE string id (the settings screen's "TEXT / LARGE"
 * pairs — a menu row that shows what it is currently set to), and an enabled flag.  Rows are stored
 * in pre-allocated arrays and rebuilt in place, so opening a menu allocates nothing.
 *
 * <p>The one rule with teeth is the CONFIRMATION.  A row that carries consequences — wiping the save,
 * abandoning a run — never acts on its own tap: it opens a confirm, and only the confirm can commit.
 * Every confirm carries a way back, and nothing here is on a timer or auto-selects, which is the same
 * anti-skip promise the rest of the story layer makes (order-6 Part C).
 *
 * <p>Headless: no LibGDX imports.  Labels are string ids, never text (localisation rule).
 */
public final class FramingMenu {

    private final String[]  labelStringIds = new String[StoryUiConstants.STORY_FRAME_MENU_MAX_ROWS];
    private final String[]  valueStringIds = new String[StoryUiConstants.STORY_FRAME_MENU_MAX_ROWS];
    private final boolean[] rowEnabled     = new boolean[StoryUiConstants.STORY_FRAME_MENU_MAX_ROWS];
    private int             rowCount;

    /** Which row the thumb is currently down on, so a tap that slides off is visibly cancelled. */
    private int pressedIndex = -1;

    // ---- the pending confirmation, if any ------------------------------------------------------
    private int    confirmRowIndex = -1;
    private String confirmPromptStringId;
    private int    confirmedRowIndex = -1;

    /** Empties the menu.  Callers rebuild it row by row, in place. */
    public void clear() {
        rowCount     = 0;
        pressedIndex = -1;
        cancelConfirm();
        confirmedRowIndex = -1;
    }

    /**
     * Appends one row.  Rows past {@code STORY_FRAME_MENU_MAX_ROWS} are dropped rather than throwing:
     * a menu that grew too long is a design problem to see on screen, not a crash to ship.
     *
     * @param labelStringId localisation id of the row's label (never text)
     * @param valueStringId localisation id of the row's current value, or null for a plain row
     * @param enabled       false draws the row dimmed and refuses its taps
     */
    public void addRow(String labelStringId, String valueStringId, boolean enabled) {
        if (rowCount >= labelStringIds.length || labelStringId == null) return;
        labelStringIds[rowCount] = labelStringId;
        valueStringIds[rowCount] = valueStringId;
        rowEnabled[rowCount]     = enabled;
        rowCount++;
    }

    public int getRowCount() {
        return rowCount;
    }

    public String getLabelStringId(int rowIndex) {
        return isValidRow(rowIndex) ? labelStringIds[rowIndex] : null;
    }

    /** The row's current value id (settings rows), or null when the row is a plain command. */
    public String getValueStringId(int rowIndex) {
        return isValidRow(rowIndex) ? valueStringIds[rowIndex] : null;
    }

    public boolean isRowEnabled(int rowIndex) {
        return isValidRow(rowIndex) && rowEnabled[rowIndex];
    }

    private boolean isValidRow(int rowIndex) {
        return rowIndex >= 0 && rowIndex < rowCount;
    }

    // -------------------------------------------------------------------------
    // Press feedback
    // -------------------------------------------------------------------------

    /** Marks which row the finger is down on (-1 for none).  A disabled row never lights up. */
    public void setPressedIndex(int rowIndex) {
        pressedIndex = isRowEnabled(rowIndex) ? rowIndex : -1;
    }

    public int getPressedIndex() {
        return pressedIndex;
    }

    // -------------------------------------------------------------------------
    // Activation + confirmation
    // -------------------------------------------------------------------------

    /**
     * The player released on {@code rowIndex}.  A row with a confirm prompt opens the confirmation
     * and commits NOTHING; every other row is activated immediately.
     *
     * @param confirmPromptStringId the question to ask first, or null to act at once
     * @return true when the row acted (the caller may do the thing); false when a confirm opened,
     *         when the row is disabled, or when a confirmation is already on screen
     */
    public boolean activateRow(int rowIndex, String confirmPromptStringId) {
        pressedIndex = -1;
        if (isConfirmOpen() || !isRowEnabled(rowIndex)) return false;
        if (confirmPromptStringId != null) {
            confirmRowIndex       = rowIndex;
            this.confirmPromptStringId = confirmPromptStringId;
            return false;
        }
        return true;
    }

    /** True while a confirmation owns the screen — the menu behind it must refuse every tap. */
    public boolean isConfirmOpen() {
        return confirmRowIndex >= 0;
    }

    /** Localisation id of the question on screen, or null when no confirmation is open. */
    public String getConfirmPromptStringId() {
        return confirmPromptStringId;
    }

    /** Which row the open confirmation belongs to, or -1. */
    public int getConfirmRowIndex() {
        return confirmRowIndex;
    }

    /** The player backed out.  Nothing happens — every confirmation carries a way back. */
    public void cancelConfirm() {
        confirmRowIndex       = -1;
        confirmPromptStringId = null;
    }

    /** The player said yes.  The row is latched for the caller to read once, then the confirm closes. */
    public void acceptConfirm() {
        if (!isConfirmOpen()) return;
        confirmedRowIndex = confirmRowIndex;
        cancelConfirm();
    }

    /**
     * Reads and clears the row a confirmation just committed, or -1 when nothing was confirmed.
     * Latched rather than acted on inside the model, so the engine half stays the only thing that
     * wipes a save or ends a run.
     */
    public int consumeConfirmedRow() {
        int confirmed    = confirmedRowIndex;
        confirmedRowIndex = -1;
        return confirmed;
    }

    // -------------------------------------------------------------------------
    // The settings screen (order-6 Part D's knobs, on a screen of their own)
    // -------------------------------------------------------------------------

    /**
     * Rebuilds this menu as the SETTINGS screen: one row per accessibility knob showing its current
     * value, then BACK.  Called when the screen opens and again after every knob is cycled, so the
     * row always reads what the save now holds.
     *
     * <p>The knobs are exactly the ones the codex's footer strip carries — one model, two places to
     * reach it, no second copy of the truth.
     */
    public void rebuildAsSettings(StorySettings settings) {
        clear();
        if (settings == null) return;
        for (int settingIndex = 0; settingIndex < StoryUiConstants.STORY_CODEX_SETTING_COUNT;
                settingIndex++) {
            addRow(StorySettings.getSettingNameStringId(settingIndex),
                   settings.getSettingValueStringId(settingIndex), true);
        }
        addRow(StoryUiConstants.STORY_SETTINGS_BACK_ID, null, true);
    }

    /** True when {@code rowIndex} is the BACK row of a settings menu built by {@link #rebuildAsSettings}. */
    public boolean isSettingsBackRow(int rowIndex) {
        return rowIndex == StoryUiConstants.STORY_CODEX_SETTING_COUNT;
    }
}
