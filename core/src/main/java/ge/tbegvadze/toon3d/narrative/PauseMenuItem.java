package ge.tbegvadze.toon3d.narrative;

/**
 * The four rows of the in-run PAUSE menu (Story UI order-8 Part C) — the "suit paused" panel.
 *
 * <p>Minimal and readable, and it deliberately SPOILS NOTHING: no plot summary, no objective recap,
 * no "previously on".  Everything the player has been told already lives in the archive, which is one
 * row down; a pause screen that re-tells the story would be a second script nobody asked for.
 *
 * <p>Only {@link #ABANDON_RUN} carries a confirmation, for the obvious reason: it throws away a run.
 *
 * <p>Headless: no LibGDX imports.  Labels are string ids, never text (localisation rule).
 */
public enum PauseMenuItem {

    /** Back to the floor, exactly where it was — the game is turn-based, so nothing moved. */
    RESUME("story.pause.resume", null),
    /** The archive (order-6), opened from a MENU as it always is, never from play. */
    CODEX("story.pause.codex", null),
    /** The accessibility knobs (order-6 Part D). */
    SETTINGS("story.pause.settings", null),
    /** End this run and return to standby.  The records are filed exactly as a death files them. */
    ABANDON_RUN("story.pause.abandon", "story.pause.confirm.abandon");

    private final String labelStringId;
    private final String confirmStringId;

    PauseMenuItem(String labelStringId, String confirmStringId) {
        this.labelStringId   = labelStringId;
        this.confirmStringId = confirmStringId;
    }

    /** Localisation id of the row's label. */
    public String getLabelStringId() {
        return labelStringId;
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
