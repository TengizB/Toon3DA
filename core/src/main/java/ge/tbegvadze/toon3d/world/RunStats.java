package ge.tbegvadze.toon3d.world;

import ge.tbegvadze.toon3d.util.BalanceConfig;

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

    /**
     * Annotates the most-recently committed route node with a resolved detail, joined by ':'
     * (e.g. a MYSTERY node "mystery" becomes "mystery:VAULT"). Used by route-map order-9 to record a
     * MYSTERY node's HIDDEN-but-resolved outcome so the post-run route story is complete. No-op before
     * any node has been committed.
     */
    public void annotateLastRouteNode(String detailToken) {
        if (detailToken == null || detailToken.isEmpty() || routeString.length() == 0) {
            return;
        }
        routeString.append(':').append(detailToken);
    }

    /** The route walked so far, as a single dash-separated string (empty before the first commit). */
    public String getRouteString() {
        return routeString.toString();
    }

    /**
     * The regions the descent has entered, in order, as "index:NAME" tokens (route-map order-10).
     * Recorded once per region the first time the player crosses into it, so the post-run story reads
     * the acts traversed (e.g. "0:OUTER FACILITY | 1:RESEARCH WING"). Empty before the first floor.
     */
    private final StringBuilder regionsEntered = new StringBuilder();

    /**
     * Records that the descent has entered a region (route-map order-10). Called once per region as the
     * player first crosses into it; tokens are '|'-separated.
     */
    public void recordRegionEntry(int regionIndex, String regionName) {
        if (regionsEntered.length() > 0) {
            regionsEntered.append(" | ");
        }
        regionsEntered.append(regionIndex).append(':').append(regionName == null ? "?" : regionName);
    }

    /** The regions entered so far, as a single '|'-separated string (empty before the first floor). */
    public String getRegionsEntered() {
        return regionsEntered.toString();
    }

    public void recordDamageDealt(int amount) { totalDamageDealt += amount; }
    public void recordDamageTaken(int amount) { totalDamageTaken += amount; }
    public void recordShotFired()             { shotsFired++; }
    public void recordItemCollected()         { itemsCollected++; }
    public void recordTick()                  { ticksElapsed++; }

    public void recordFloor(int floor) {
        if (floor > floorReached) floorReached = floor;
    }

    // =====================================================================================
    // THE PITY RULE (new-game-balancr order 2): track weapon UPGRADES the player is offered per
    // region so the floor generator can force-spawn one when a region is about to end with zero,
    // guaranteeing no run is starved of the gear curve by RNG. "Upgrade" = an UNCOMMON-or-better
    // weapon PLACED on a floor of the region (ground drop, cache, armory, or shop stock).
    // =====================================================================================

    private int currentUpgradeRegionIndex     = -1;
    private int weaponUpgradesSeenThisRegion   = 0;

    /**
     * Announces the region the player is now generating floors for. Crossing into a NEW region resets
     * the per-region upgrade counter (each region owns its own pity quota); re-announcing the same
     * region is a no-op, so this is safe to call once per floor build.
     *
     * @param regionIndex 0-based region index, {@code floor((depth-1) / GEAR_CURVE_REGION_BAND_SIZE)}
     */
    public void enterUpgradeRegion(int regionIndex) {
        if (regionIndex != currentUpgradeRegionIndex) {
            currentUpgradeRegionIndex   = regionIndex;
            weaponUpgradesSeenThisRegion = 0;
        }
    }

    /** Records that an in-band weapon upgrade was placed on the current region's floors. */
    public void recordWeaponUpgradeSeen() {
        weaponUpgradesSeenThisRegion++;
    }

    /** Weapon upgrades placed so far in the current region. */
    public int getWeaponUpgradesSeenThisRegion() {
        return weaponUpgradesSeenThisRegion;
    }

    /**
     * Whether the current region still owes the player its guaranteed upgrade(s). The floor generator
     * consults this on a region's LAST floor and force-spawns a pity weapon if it returns true.
     */
    public boolean regionUpgradeQuotaUnmet() {
        return weaponUpgradesSeenThisRegion < BalanceConfig.GUARANTEED_UPGRADE_PER_REGION;
    }

    // =====================================================================================
    // RESOURCE-ECONOMY TELEMETRY (new-game-balancr order 3). Counters the order-9 balance simulator
    // reads back to regression-test the live economy against the audited model (ammo scarcity, the
    // credit sink, the heal drain, and the never-softlock lifeline). Purely observational — nothing
    // here feeds gameplay logic; it only records what the run actually spent and earned.
    // =====================================================================================

    public int ammoPickedUp;              // total ammo UNITS collected across the run
    public int ammoSpent;                 // total ammo UNITS consumed firing weapons
    public int creditsEarned;             // total credits awarded (kills + chips + abilities)
    public int creditsSpent;              // total credits spent at the shop
    public int healsUsed;                 // medkits/stims consumed
    public int emergencySupplyTriggers;   // times the never-softlock ammo lifeline fired

    /** Records ammo UNITS the player collected (positive amounts only). */
    public void recordAmmoPickedUp(int units) {
        if (units > 0) ammoPickedUp += units;
    }

    /** Records ammo UNITS the player consumed firing (positive amounts only). */
    public void recordAmmoSpent(int units) {
        if (units > 0) ammoSpent += units;
    }

    /** Records credits awarded to the player (positive amounts only). */
    public void recordCreditsEarned(int amount) {
        if (amount > 0) creditsEarned += amount;
    }

    /** Records credits the player spent at the shop (positive amounts only). */
    public void recordCreditsSpent(int amount) {
        if (amount > 0) creditsSpent += amount;
    }

    /** Records one heal (medkit/stim) consumed. */
    public void recordHealUsed() {
        healsUsed++;
    }

    /** Records that the never-softlock emergency ammo lifeline fired (order 3, part D). */
    public void recordEmergencySupplyTrigger() {
        emergencySupplyTriggers++;
    }
}
