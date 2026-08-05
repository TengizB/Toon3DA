package ge.tbegvadze.toon3d.narrative;

import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * The story-UI ACCESSIBILITY SETTINGS (Story UI order-6 Part D) — three knobs, persisted, applied
 * everywhere story text is drawn.
 *
 * <p>The design rule behind all of them: <b>a setting may only make the game easier to read, never
 * harder, and no setting is ever required to understand anything.</b>  Every default is the
 * comfortable end of its scale, so a player who never opens this strip already has the accessible
 * configuration.
 *
 * <ul>
 *   <li>{@link #getTextSize()} — scales every story panel's body text, and narrows the wrap width to
 *       match (wrap, never shrink).</li>
 *   <li>{@link #isReduceTextHold()} — shortens the boot card's paced line reveal.  Note what is NOT
 *       here: there is no "skip story" toggle and no timer to disable, because <em>nothing in the
 *       story UI is on a timer in the first place</em>.  Barks wait for the player to close them,
 *       exchanges wait for an answer, the boot card waits for a tap.  Part D asks that timed moments
 *       have a disable toggle; the layer's answer is that it has no timed moments.</li>
 *   <li>{@link #isReduceMotion()} — stills the Planet's jitter and any other decorative movement
 *       behind story text, for players who find motion under text hard to read.</li>
 * </ul>
 *
 * <p>State is cached in memory and written through to a {@link StoryProgressStore} the instant a
 * setting changes — a handful of writes in the life of a save, never a per-frame path.
 *
 * <p>Headless: no LibGDX imports.
 */
public final class StorySettings {

    private final StoryProgressStore store;
    private StoryTextSize            textSize;
    private boolean                  reduceTextHold;
    private boolean                  reduceMotion;

    /** In-memory settings at their defaults — tests and showcases. */
    public StorySettings() {
        this(new InMemoryStoryProgressStore());
    }

    /** Loads the persisted settings from {@code store}, falling back to the comfortable defaults. */
    public StorySettings(StoryProgressStore store) {
        if (store == null) throw new IllegalArgumentException("store must not be null");
        this.store          = store;
        this.textSize       = StoryTextSize.fromOrdinal(
                store.loadSettingInt(StoryUiConstants.STORY_SETTING_TEXT_SIZE,
                                     StoryTextSize.NORMAL.ordinal()));
        this.reduceTextHold = store.loadSettingInt(StoryUiConstants.STORY_SETTING_REDUCE_HOLD, 0) != 0;
        this.reduceMotion   = store.loadSettingInt(StoryUiConstants.STORY_SETTING_REDUCE_MOTION, 0) != 0;
    }

    public StoryTextSize getTextSize() {
        return textSize;
    }

    /** Sets the text size and persists it immediately.  A no-op change writes nothing. */
    public void setTextSize(StoryTextSize value) {
        if (value == null || value == textSize) return;
        textSize = value;
        store.saveSettingInt(StoryUiConstants.STORY_SETTING_TEXT_SIZE, value.ordinal());
    }

    /** Cycles to the next text size — the settings strip is a tap-to-cycle button, not a submenu. */
    public void cycleTextSize() {
        setTextSize(textSize.next());
    }

    /** True when the boot card's paced line reveal is shortened. */
    public boolean isReduceTextHold() {
        return reduceTextHold;
    }

    public void setReduceTextHold(boolean value) {
        if (value == reduceTextHold) return;
        reduceTextHold = value;
        store.saveSettingInt(StoryUiConstants.STORY_SETTING_REDUCE_HOLD, value ? 1 : 0);
    }

    public void toggleReduceTextHold() {
        setReduceTextHold(!reduceTextHold);
    }

    /** True when decorative motion behind story text is stilled. */
    public boolean isReduceMotion() {
        return reduceMotion;
    }

    public void setReduceMotion(boolean value) {
        if (value == reduceMotion) return;
        reduceMotion = value;
        store.saveSettingInt(StoryUiConstants.STORY_SETTING_REDUCE_MOTION, value ? 1 : 0);
    }

    public void toggleReduceMotion() {
        setReduceMotion(!reduceMotion);
    }

    // -------------------------------------------------------------------------
    // Derived values the systems and renderers actually consume
    // -------------------------------------------------------------------------

    /** Body text scale for every story panel — the order-1 size, scaled by the setting. */
    public float getStoryTextScale() {
        return StoryUiConstants.STORY_TEXT_SIZE * textSize.getMultiplier();
    }

    /** Wrap cap for bark / exchange / boot-card panels at the current size. */
    public int getPanelLineMaxChars() {
        return textSize.scaleLineMaxChars(StoryUiConstants.STORY_LINE_MAX_CHARS);
    }

    /** Body text scale for the codex's long-form text. */
    public float getCodexTextScale() {
        return StoryUiConstants.STORY_CODEX_DETAIL_TEXT_SIZE * textSize.getMultiplier();
    }

    /** Wrap cap for the codex's long-form text at the current size. */
    public int getCodexLineMaxChars() {
        return textSize.scaleLineMaxChars(StoryUiConstants.STORY_CODEX_LINE_MAX_CHARS);
    }

    /** Localisation id of the current value of each settings button, for the strip's label. */
    public String getSettingValueStringId(int settingIndex) {
        switch (settingIndex) {
            case 0:  return textSize.getLabelStringId();
            case 1:  return StoryUiConstants.STORY_CODEX_SETTING_REVEAL_ID + "." + (reduceTextHold ? 1 : 0);
            default: return StoryUiConstants.STORY_CODEX_SETTING_MOTION_ID + "." + (reduceMotion ? 1 : 0);
        }
    }

    /** Localisation id of each settings button's NAME (always shown alongside the value). */
    public static String getSettingNameStringId(int settingIndex) {
        switch (settingIndex) {
            case 0:  return StoryUiConstants.STORY_CODEX_SETTING_TEXT_ID;
            case 1:  return StoryUiConstants.STORY_CODEX_SETTING_REVEAL_ID;
            default: return StoryUiConstants.STORY_CODEX_SETTING_MOTION_ID;
        }
    }

    /** Cycles the setting in slot {@code settingIndex} of the codex's accessibility strip. */
    public void cycleSetting(int settingIndex) {
        switch (settingIndex) {
            case 0:  cycleTextSize();       break;
            case 1:  toggleReduceTextHold(); break;
            default: toggleReduceMotion();   break;
        }
    }
}
