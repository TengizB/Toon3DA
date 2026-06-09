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
}
