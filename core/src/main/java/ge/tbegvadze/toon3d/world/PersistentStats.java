package ge.tbegvadze.toon3d.world;

/** Cross-run records that survive death and persist to disk via StatsStore. */
public class PersistentStats {

    public int  bestFloor;
    public int  mostKills;
    public long fastestClearTicks;
    public long longestSurvivalTicks;
    public int  lifetimeKills;
    public int  totalRuns;
    public int  totalDeaths;
    /** Runs that killed the run-final boss (balance-overhaul order 6, F4). */
    public int  runsWon;
    /** The fewest turns (ticks) any won run took; 0 = no win yet. */
    public long bestWinTurns;

    /**
     * Zeroes every record in place — the "new game" wipe (Story UI order-7 Part B).  In place rather
     * than by replacement so every holder of this object (the HUD, the death overlay) keeps reading
     * the same instance instead of a stale copy of the old records.
     */
    public void reset() {
        bestFloor            = 0;
        mostKills            = 0;
        fastestClearTicks    = 0L;
        longestSurvivalTicks = 0L;
        lifetimeKills        = 0;
        totalRuns            = 0;
        totalDeaths          = 0;
        runsWon              = 0;
        bestWinTurns         = 0L;
    }
}
