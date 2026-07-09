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

    /**
     * Compact, shareable summary of the route the player actually walked — one token per committed
     * node in descent order (e.g. "C-C-!-$-R-BOSS"). Built by {@code World} on each route commit
     * (route-map order-3); the "readable story of the incursion" the route-map vision promises.
     */
    private final StringBuilder routeString = new StringBuilder();

    public void recordKill()                  { enemiesKilled++; }

    /**
     * Appends one committed node's token to the run's route string, separating tokens with '-'.
     *
     * @param nodeToken a short symbol for the node type (e.g. its registry id or icon glyph)
     */
    public void recordRouteNode(String nodeToken) {
        if (routeString.length() > 0) {
            routeString.append('-');
        }
        routeString.append(nodeToken);
    }

    /** The route walked so far, as a single dash-separated string (empty before the first commit). */
    public String getRouteString() {
        return routeString.toString();
    }
    public void recordDamageDealt(int amount) { totalDamageDealt += amount; }
    public void recordDamageTaken(int amount) { totalDamageTaken += amount; }
    public void recordShotFired()             { shotsFired++; }
    public void recordItemCollected()         { itemsCollected++; }
    public void recordTick()                  { ticksElapsed++; }

    public void recordFloor(int floor) {
        if (floor > floorReached) floorReached = floor;
    }
}
