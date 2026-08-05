package ge.tbegvadze.toon3d.narrative;

import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * The story text-size setting (Story UI order-6 Part D) — the accessibility knob that scales every
 * story panel's body text.
 *
 * <p>The DEFAULT is already the comfortable end of the scale: order-1 sized story text to read at
 * arm's length on a phone, so {@link #NORMAL} is generous and the other two are for players who need
 * more.  Text is never shrunk below the default — a smaller option would be a readability
 * regression, and this setting only exists to make reading easier.
 *
 * <p>Growing the text also NARROWS the wrap width, so a larger size wraps sooner instead of running
 * off the panel.  That is the whole "wrap, never shrink" rule of order-1, applied to the setting.
 *
 * <p>Headless: reads only the multiplier table in {@code util/StoryUiConstants}.
 */
public enum StoryTextSize {

    /** The order-1 default — already large, and what most players will keep. */
    NORMAL,
    /** A step up for smaller screens or tired eyes. */
    LARGE,
    /** The largest offered; wraps to noticeably fewer characters per line. */
    LARGEST;

    /** Scale factor applied over the base story text size. */
    public float getMultiplier() {
        return StoryUiConstants.STORY_TEXT_SIZE_MULTIPLIER[ordinal()];
    }

    /**
     * The character wrap cap at this size, derived from a base cap.  Wider glyphs mean fewer of them
     * fit, so the cap scales DOWN by the same factor the text scales up.  Never returns less than 1.
     */
    public int scaleLineMaxChars(int baseMaxChars) {
        int scaled = Math.round(baseMaxChars / getMultiplier());
        return Math.max(1, scaled);
    }

    /** Localisation id of this size's player-facing label on the settings strip. */
    public String getLabelStringId() {
        return StoryUiConstants.STORY_CODEX_SETTING_TEXT_ID + "." + ordinal();
    }

    /** The next size, wrapping around — the settings strip cycles rather than opening a submenu. */
    public StoryTextSize next() {
        StoryTextSize[] sizes = values();
        return sizes[(ordinal() + 1) % sizes.length];
    }

    /** Restores a size from its persisted ordinal, clamping anything out of range. */
    public static StoryTextSize fromOrdinal(int sizeOrdinal) {
        StoryTextSize[] sizes = values();
        if (sizeOrdinal <= 0)             return sizes[0];
        if (sizeOrdinal >= sizes.length)  return sizes[sizes.length - 1];
        return sizes[sizeOrdinal];
    }
}
