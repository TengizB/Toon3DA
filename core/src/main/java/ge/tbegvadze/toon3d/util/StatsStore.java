package ge.tbegvadze.toon3d.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import ge.tbegvadze.toon3d.world.PersistentStats;
import ge.tbegvadze.toon3d.world.RunStats;

/** Persistence adapter for cross-run records. Uses LibGDX Preferences — no extra dependency. */
public final class StatsStore {

    private StatsStore() {}

    public static PersistentStats load() {
        PersistentStats stats = new PersistentStats();
        Preferences prefs = Gdx.app.getPreferences(Constants.STATS_PREFS_NAME);
        stats.bestFloor            = prefs.getInteger("bestFloor",            0);
        stats.mostKills            = prefs.getInteger("mostKills",            0);
        stats.lifetimeKills        = prefs.getInteger("lifetimeKills",        0);
        stats.totalRuns            = prefs.getInteger("totalRuns",            0);
        stats.totalDeaths          = prefs.getInteger("totalDeaths",          0);
        stats.longestSurvivalTicks = prefs.getLong("longestSurvivalTicks",    0L);
        stats.fastestClearTicks    = prefs.getLong("fastestClearTicks",       0L);
        return stats;
    }

    public static void save(PersistentStats stats) {
        Preferences prefs = Gdx.app.getPreferences(Constants.STATS_PREFS_NAME);
        prefs.putInteger("bestFloor",            stats.bestFloor);
        prefs.putInteger("mostKills",            stats.mostKills);
        prefs.putInteger("lifetimeKills",        stats.lifetimeKills);
        prefs.putInteger("totalRuns",            stats.totalRuns);
        prefs.putInteger("totalDeaths",          stats.totalDeaths);
        prefs.putLong("longestSurvivalTicks",    stats.longestSurvivalTicks);
        prefs.putLong("fastestClearTicks",       stats.fastestClearTicks);
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
