package ge.tbegvadze.toon3d.world;

/** Per-run accumulator. Reset by constructing a new instance at the start of every run. */
public class RunStats {

    public int   floorReached        = 1;
    public int   enemiesKilled;
    public int   itemsCollected;
    public int   totalDamageDealt;
    public int   totalDamageTaken;
    public int   shotsFired;
    public long  ticksElapsed;
    public float realSecondsPlayed;

    public void recordKill()                  { enemiesKilled++; }
    public void recordDamageDealt(int amount) { totalDamageDealt += amount; }
    public void recordDamageTaken(int amount) { totalDamageTaken += amount; }
    public void recordShotFired()             { shotsFired++; }
    public void recordItemCollected()         { itemsCollected++; }
    public void recordTick()                  { ticksElapsed++; }

    public void recordFloor(int floor) {
        if (floor > floorReached) floorReached = floor;
    }
}
