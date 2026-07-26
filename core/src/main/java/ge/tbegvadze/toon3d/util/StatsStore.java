package ge.tbegvadze.toon3d.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import ge.tbegvadze.toon3d.world.PersistentStats;
import ge.tbegvadze.toon3d.world.RunStats;
import ge.tbegvadze.toon3d.util.ProgressionConstants;

/** Persistence adapter for cross-run records. Uses LibGDX Preferences — no extra dependency. */
public final class StatsStore {

    private StatsStore() {}

    /**
     * Keys written by builds that still had a difficulty-mode system (new-game-balancr order 8
     * removed it). They are never read — {@link #load} asks only for the keys it knows, so a
     * legacy record loads fine whatever type they held — but they are dropped on the next load so
     * a stale mode choice cannot linger in a player's preferences file.
     */
    private static final String[] LEGACY_MODE_KEYS = { "difficulty", "difficultyMode" };

    public static PersistentStats load() {
        PersistentStats stats = new PersistentStats();
        Preferences prefs = Gdx.app.getPreferences(ProgressionConstants.STATS_PREFS_NAME);
        dropLegacyModeKeys(prefs);
        stats.bestFloor            = prefs.getInteger("bestFloor",            0);
        stats.mostKills            = prefs.getInteger("mostKills",            0);
        stats.lifetimeKills        = prefs.getInteger("lifetimeKills",        0);
        stats.totalRuns            = prefs.getInteger("totalRuns",            0);
        stats.totalDeaths          = prefs.getInteger("totalDeaths",          0);
        stats.longestSurvivalTicks = prefs.getLong("longestSurvivalTicks",    0L);
        stats.fastestClearTicks    = prefs.getLong("fastestClearTicks",       0L);
        return stats;
    }

    /**
     * Best-effort migration: removes any difficulty-mode key left by an older build.
     * Never throws — a record that has no such key (every record written since order 8) is
     * untouched and nothing is flushed.
     */
    private static void dropLegacyModeKeys(Preferences prefs) {
        boolean removedAny = false;
        for (String legacyKey : LEGACY_MODE_KEYS) {
            if (prefs.contains(legacyKey)) {
                prefs.remove(legacyKey);
                removedAny = true;
            }
        }
        if (removedAny) prefs.flush();
    }

    public static void save(PersistentStats stats) {
        Preferences prefs = Gdx.app.getPreferences(ProgressionConstants.STATS_PREFS_NAME);
        prefs.putInteger("bestFloor",            stats.bestFloor);
        prefs.putInteger("mostKills",            stats.mostKills);
        prefs.putInteger("lifetimeKills",        stats.lifetimeKills);
        prefs.putInteger("totalRuns",            stats.totalRuns);
        prefs.putInteger("totalDeaths",          stats.totalDeaths);
        prefs.putLong("longestSurvivalTicks",    stats.longestSurvivalTicks);
        prefs.putLong("fastestClearTicks",       stats.fastestClearTicks);
        prefs.flush();
    }

    /**
     * True once the given first-encounter tip has already fired (strategy-combat-order-6 onboarding).
     * Tips are gated to fire exactly once across all runs, so a returning player is never re-taught.
     * Backed by the same Preferences store under a "tip_" key prefix.
     */
    public static boolean isTipSeen(String tipKey) {
        Preferences prefs = Gdx.app.getPreferences(ProgressionConstants.STATS_PREFS_NAME);
        return prefs.getBoolean("tip_" + tipKey, false);
    }

    /** Records that a first-encounter tip has fired so it never fires again (order-6 onboarding). */
    public static void markTipSeen(String tipKey) {
        Preferences prefs = Gdx.app.getPreferences(ProgressionConstants.STATS_PREFS_NAME);
        prefs.putBoolean("tip_" + tipKey, true);
        prefs.flush();
    }

    /** Updates persistent records from the completed run and saves to disk. */
    public static void updateAndSave(RunStats run, PersistentStats persistent) {
        persistent.totalRuns++;
        persistent.totalDeaths++;
        persistent.lifetimeKills += run.enemiesKilled;
        if (run.floorReached > persistent.bestFloor)
            persistent.bestFloor = run.floorReached;
        if (run.enemiesKilled > persistent.mostKills)
            persistent.mostKills = run.enemiesKilled;
        if (run.ticksElapsed > persistent.longestSurvivalTicks)
            persistent.longestSurvivalTicks = run.ticksElapsed;
        save(persistent);
    }
}
