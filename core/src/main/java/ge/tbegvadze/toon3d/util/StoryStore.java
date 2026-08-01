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
 * selected through) and one boolean per one-shot beat id.  Both are written on meaningful change
 * only — a region deepening or a beat firing — never per frame.  A schema version is stamped on
 * first write so order-7 can migrate cleanly when it extends this with stance, consequential
 * outcomes, codex unlocks and settings.
 *
 * <p>Reads/writes are best-effort: if no LibGDX application is available (headless tests, tools),
 * every method degrades to an in-memory no-op rather than throwing, so narrative logic never
 * depends on a live Preferences backend.
 */
public final class StoryStore implements StoryProgressStore {

    /** Bumped by order-7 when the persisted narrative schema changes. */
    public static final int STORY_SCHEMA_VERSION = 1;

    private static final String KEY_SCHEMA_VERSION = "story.schemaVersion";
    private static final String KEY_DEEPEST_REGION = "story.deepestRegion";
    private static final String KEY_BEAT_PREFIX    = "story.beat.";

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
}
