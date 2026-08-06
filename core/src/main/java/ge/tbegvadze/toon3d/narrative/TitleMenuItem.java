package ge.tbegvadze.toon3d.narrative;

/**
 * The five rows of the TITLE screen's menu (Story UI order-8 Part A).
 *
 * <p>Deliberately short and cold.  The title screen is quiet dread, not a feature list: it offers a
 * way back into the descent, a way to start the operator line over, the archive, the settings, and a
 * way out.  Nothing else belongs here — anything that would need explaining belongs to ORA, in play.
 *
 * <p>Two of the five carry consequences and therefore a CONFIRM ({@link #getConfirmStringId()}):
 * wiping the save is the ONE thing in the game that moves the story backwards (order-7 Part B), so it
 * is never one tap away.  A row may also be UNAVAILABLE — {@link #CONTINUE_DESCENT} is hidden until
 * the player has actually been printed at least once, because "continue" with nothing to continue is
 * a lie the first launch must not tell.
 *
 * <p>Headless: no LibGDX imports.  Labels are string ids, never text (localisation rule).
 */
public enum TitleMenuItem {

    /**
     * Go down: a fresh run, with every scrap of meta-progress and story intact.  It reads BEGIN
     * DESCENT until the player has actually been printed once (narrative-rework order-2 A) — with
     * nothing behind them, "continue" is a lie about a thing that never happened, and the first
     * thing this game says to anybody must not be one.
     */
    CONTINUE_DESCENT("story.title.continue", "story.title.begin", null),
    /** Start the operator line over — wipes records, tips and narrative state TOGETHER. */
    NEW_OPERATOR("story.title.new", null, "story.title.confirm.new"),
    /** The archive (order-6).  Readable from the menu, before a run exists. */
    CODEX("story.title.codex", null, null),
    /** The accessibility knobs (order-6 Part D), on a screen of their own. */
    SETTINGS("story.title.settings", null, null),
    /** Leave. */
    QUIT("story.title.quit", null, null);

    private final String labelStringId;
    private final String firstRunLabelStringId;
    private final String confirmStringId;

    TitleMenuItem(String labelStringId, String firstRunLabelStringId, String confirmStringId) {
        this.labelStringId         = labelStringId;
        this.firstRunLabelStringId = firstRunLabelStringId;
        this.confirmStringId       = confirmStringId;
    }

    /** Localisation id of the row's label, for a save that has printed at least one body. */
    public String getLabelStringId() {
        return labelStringId;
    }

    /**
     * Localisation id this row wears on a save that has never printed anybody, or null when the row
     * reads the same either way.  One extra string, no extra row, no extra confirmation.
     */
    public String getFirstRunLabelStringId() {
        return firstRunLabelStringId;
    }

    /**
     * The label to draw for this row: its first-run wording on an empty save, its ordinary wording
     * once the player has something to come back to.
     */
    public String getLabelStringId(boolean hasBeenPrinted) {
        return !hasBeenPrinted && firstRunLabelStringId != null
                ? firstRunLabelStringId
                : labelStringId;
    }

    /** Localisation id of the question asked before this row acts, or null when it acts at once. */
    public String getConfirmStringId() {
        return confirmStringId;
    }

    /** True when tapping this row opens a confirmation instead of acting. */
    public boolean requiresConfirmation() {
        return confirmStringId != null;
    }
}
