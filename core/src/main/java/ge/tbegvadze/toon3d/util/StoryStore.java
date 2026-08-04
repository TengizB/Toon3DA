package ge.tbegvadze.toon3d.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import ge.tbegvadze.toon3d.narrative.StoryProgressStore;

/**
 * Persistence adapter for NARRATIVE state — the engine half of
 * {@link StoryProgressStore} (Story UI order-2; the full schema is order-7 Part B).
 *
 * <p>Uses LibGDX {@link Preferences}, exactly like {@link StatsStore}, and shares the same
 * preferences file so a "new game" wipe can clear meta-progression and story progress together.
 * All keys carry a {@code story.} prefix so they can never collide with the run-record keys.
 *
 * <p>What it holds today: the deepest STORY region ever reached (the gate every bark pool is
 * selected through), one boolean per one-shot beat id, and the REPRINT COUNT behind the order-3
 * boot card's instance counter.  All are written on meaningful change only — a region deepening, a
 * beat firing, a reprint — never per frame.  A schema version is stamped on
 * first write so order-7 can migrate cleanly when it extends this with stance, consequential
 * outcomes, codex unlocks and settings.
 *
 * <p>Reads/writes are best-effort: if no LibGDX application is available (headless tests, tools),
 * every method degrades to an in-memory no-op rather than throwing, so narrative logic never
 * depends on a live Preferences backend.
 */
public final class StoryStore implements StoryProgressStore {

    /**
     * Bumped by order-7 when the persisted narrative schema changes.  Version 2 added the order-4
     * keys (the three hidden stance leanings, consequential outcomes, codex unlocks); version 3 the
     * order-6 keys (which codex entries have been READ, and the accessibility settings).  Every one
     * of them reads a default when absent, so an older save loads cleanly with no migration.
     */
    public static final int STORY_SCHEMA_VERSION = 3;

    private static final String KEY_SCHEMA_VERSION = "story.schemaVersion";
    private static final String KEY_DEEPEST_REGION = "story.deepestRegion";
    private static final String KEY_BEAT_PREFIX    = "story.beat.";
    private static final String KEY_REPRINT_COUNT  = "story.reprintCount";
    private static final String KEY_STANCE_PREFIX  = "story.stance.";
    private static final String KEY_OUTCOME_PREFIX = "story.outcome.";
    private static final String KEY_CODEX_PREFIX   = "story.codex.";
    private static final String KEY_CODEX_READ_PREFIX = "story.codexRead.";
    private static final String KEY_SETTING_PREFIX = "story.setting.";

    /** Prefix shared by every narrative key — used by a "new game" wipe. */
    public static final String STORY_KEY_PREFIX = "story.";

    private Preferences preferences() {
        if (Gdx.app == null) return null;
        return Gdx.app.getPreferences(ProgressionConstants.STATS_PREFS_NAME);
    }

    @Override
    public int loadDeepestRegionOrdinal() {
        Preferences prefs = preferences();
        if (prefs == null) return 0;
        return prefs.getInteger(KEY_DEEPEST_REGION, 0);
    }

    @Override
    public void saveDeepestRegionOrdinal(int regionOrdinal) {
        Preferences prefs = preferences();
        if (prefs == null) return;
        prefs.putInteger(KEY_SCHEMA_VERSION, STORY_SCHEMA_VERSION);
        prefs.putInteger(KEY_DEEPEST_REGION, regionOrdinal);
        prefs.flush();
    }

    @Override
    public boolean isBeatSeen(String beatId) {
        Preferences prefs = preferences();
        if (prefs == null || beatId == null) return false;
        return prefs.getBoolean(KEY_BEAT_PREFIX + beatId, false);
    }

    @Override
    public void markBeatSeen(String beatId) {
        Preferences prefs = preferences();
        if (prefs == null || beatId == null) return;
        prefs.putInteger(KEY_SCHEMA_VERSION, STORY_SCHEMA_VERSION);
        prefs.putBoolean(KEY_BEAT_PREFIX + beatId, true);
        prefs.flush();
    }

    @Override
    public int loadReprintCount() {
        Preferences prefs = preferences();
        if (prefs == null) return 0;
        return prefs.getInteger(KEY_REPRINT_COUNT, 0);
    }

    /**
     * Persists the reprint count behind the boot card's instance counter (order-3).  Written once
     * per reprint — that is, once per death — never per frame.
     */
    @Override
    public void saveReprintCount(int reprintCount) {
        Preferences prefs = preferences();
        if (prefs == null) return;
        prefs.putInteger(KEY_SCHEMA_VERSION, STORY_SCHEMA_VERSION);
        prefs.putInteger(KEY_REPRINT_COUNT, reprintCount);
        prefs.flush();
    }

    // -------------------------------------------------------------------------
    // Order-4: the hidden stance model, consequential outcomes, codex unlocks.
    // All three are written only when the player ANSWERS something — a handful of writes per run.
    // -------------------------------------------------------------------------

    @Override
    public int loadStance(String stanceName) {
        Preferences prefs = preferences();
        if (prefs == null || stanceName == null) return 0;
        return prefs.getInteger(KEY_STANCE_PREFIX + stanceName, 0);
    }

    @Override
    public void saveStance(String stanceName, int value) {
        Preferences prefs = preferences();
        if (prefs == null || stanceName == null) return;
        prefs.putInteger(KEY_SCHEMA_VERSION, STORY_SCHEMA_VERSION);
        prefs.putInteger(KEY_STANCE_PREFIX + stanceName, value);
        prefs.flush();
    }

    @Override
    public String loadOutcome(String exchangeId) {
        Preferences prefs = preferences();
        if (prefs == null || exchangeId == null) return null;
        String optionId = prefs.getString(KEY_OUTCOME_PREFIX + exchangeId, "");
        return optionId.isEmpty() ? null : optionId;
    }

    @Override
    public void saveOutcome(String exchangeId, String optionId) {
        Preferences prefs = preferences();
        if (prefs == null || exchangeId == null || optionId == null) return;
        prefs.putInteger(KEY_SCHEMA_VERSION, STORY_SCHEMA_VERSION);
        prefs.putString(KEY_OUTCOME_PREFIX + exchangeId, optionId);
        prefs.flush();
    }

    @Override
    public boolean isCodexUnlocked(String codexId) {
        Preferences prefs = preferences();
        if (prefs == null || codexId == null) return false;
        return prefs.getBoolean(KEY_CODEX_PREFIX + codexId, false);
    }

    @Override
    public void markCodexUnlocked(String codexId) {
        Preferences prefs = preferences();
        if (prefs == null || codexId == null) return;
        prefs.putInteger(KEY_SCHEMA_VERSION, STORY_SCHEMA_VERSION);
        prefs.putBoolean(KEY_CODEX_PREFIX + codexId, true);
        prefs.flush();
    }

    // -------------------------------------------------------------------------
    // Order-6: which codex entries have actually been READ, and the accessibility settings.
    // Both are written only when the player does something — opens an entry, changes a setting.
    // -------------------------------------------------------------------------

    @Override
    public boolean isCodexEntryRead(String codexId) {
        Preferences prefs = preferences();
        if (prefs == null || codexId == null) return false;
        return prefs.getBoolean(KEY_CODEX_READ_PREFIX + codexId, false);
    }

    @Override
    public void markCodexEntryRead(String codexId) {
        Preferences prefs = preferences();
        if (prefs == null || codexId == null) return;
        prefs.putInteger(KEY_SCHEMA_VERSION, STORY_SCHEMA_VERSION);
        prefs.putBoolean(KEY_CODEX_READ_PREFIX + codexId, true);
        prefs.flush();
    }

    @Override
    public int loadSettingInt(String settingKey, int defaultValue) {
        Preferences prefs = preferences();
        if (prefs == null || settingKey == null) return defaultValue;
        return prefs.getInteger(KEY_SETTING_PREFIX + settingKey, defaultValue);
    }

    @Override
    public void saveSettingInt(String settingKey, int value) {
        Preferences prefs = preferences();
        if (prefs == null || settingKey == null) return;
        prefs.putInteger(KEY_SCHEMA_VERSION, STORY_SCHEMA_VERSION);
        prefs.putInteger(KEY_SETTING_PREFIX + settingKey, value);
        prefs.flush();
    }
}
