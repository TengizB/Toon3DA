package ge.tbegvadze.toon3d.sim;

/**
 * What one simulated FLOOR cost and paid (new-game-balancr order 9).
 *
 * <p>Plain mutable record filled in by {@link SimWorld} as the floor plays out. Every field is a
 * quantity the balance model makes a claim about, so the behavioural bands can compare the PLAYED
 * number against the MODELLED one instead of trusting the model's own arithmetic.
 */
public final class FloorLedger {

    /** 1-based floor number. */
    public int depth;

    /** Turns (player actions) spent on this floor. */
    public int turnsSpent;

    /** Hit points lost to enemies, hazards and DoT on this floor (gross, before heals). */
    public int healthLost;

    /** Hit points restored on this floor (medkits, stims, heal stations). */
    public int healthRestored;

    /** Medkits/stims consumed on this floor. */
    public int healsUsed;

    /** Ammo units the floor supplied (pickups, caches, drops, shop purchases). */
    public int ammoGained;

    /** Ammo units fired on this floor. */
    public int ammoSpent;

    /** Enemy effective hit points the floor asked the player to chew through (the DEMAND term of S). */
    public float demandDamage;

    /** Enemies killed on this floor. */
    public int enemiesKilled;

    /** Enemies that were still alive when the player left (the floor does not have to be cleared). */
    public int enemiesLeftAlive;

    /** Player level on ARRIVAL at this floor — compared against the expected level for the depth. */
    public int playerLevelOnArrival;

    /** True when the never-softlock emergency ammo lifeline fired on this floor (order 3, part D). */
    public boolean emergencySupplyFired;

    /** True when this floor was a boss arena. */
    public boolean bossFloor;

    /** True when the floor's boss was killed. */
    public boolean bossKilled;

    /** True when the player left the floor through the exit (false when they died or stalled here). */
    public boolean exited;

    // ---- FLOOR REPORT (balance-overhaul order 2, CP7): what the shared FloorPopulator planned for this
    // floor, read from its FloorContentReport, beside what the play actually did with it. --------------

    /** The generator that built the floor ("hand" for a level without a content report). */
    public String generatorName = "hand";

    /** The node type the floor was planned for (its NodeSupplySpec type), or "NONE". */
    public String nodeType = "NONE";

    /** Enemies the floor spawned (the planned roster, bosses excluded). */
    public int enemiesSpawned;

    /** Encounter groups the floor fielded (E2). */
    public int groups;

    /** Heal + armour value the floor PLACED (ground and carried), as a fraction of expected max HP. */
    public float healValuePlaced;

    /** The S4 heal floor this floor owed (0 when exempt). */
    public float healFloorFraction;

    /** Placed heal value REACHABLE WITHOUT A KEYCARD, as a fraction of expected max HP. */
    public float reachableHealValue;

    /** Ammo the floor PLANNED, in on-curve damage (S2), and its share of the roster's eHP (planned S). */
    public float ammoPlannedDamage;
    public float plannedScarcityRatio;

    /** Ammo units the floor planned (boxes x box size) — the denominator of the experienced share. */
    public int ammoPlannedUnits;

    /** Player health as a fraction of max health on ARRIVAL and on LEAVING (or dying on) the floor. */
    public float healthFractionOnEntry;
    public float healthFractionOnExit;

    /** Turns until the first damage exchange (a shot fired or a hit taken), or -1 when none happened. */
    public int turnsToFirstDamageExchange = -1;

    // ---- MATCHUP REPORT (balance-overhaul order 3, CP7) ------------------------------------------------
    /** SWITCH taps taken while the C4 hint was up that equipped the hinted gun (the S-SWITCH numerator). */
    public int matchupSwitches;
    /** C4 hint EPISODES: turns on which a hint appeared or changed gun (the take-rate denominator). */
    public int hintEpisodes;
    /** Player hits that landed EFFECTIVE / RESISTED (EnemyManager's onEnemyMatchupHit seam). */
    public int effectiveHits;
    public int resistedHits;
    /** Damage the player's landed hits dealt, by DamageClass ordinal (status ticks excluded). */
    public final float[] damageByClass = new float[ge.tbegvadze.toon3d.entity.DamageClass.values().length];

    /** True when the player died on this floor holding no medkit at all. */
    public boolean diedWithNoHealsHeld;

    /** Whether the floor carried its S4 heal floor reachable without a keycard (exempt floors: true). */
    public boolean healFloorMet() {
        return healFloorFraction <= 0f || reachableHealValue + 1e-3f >= healFloorFraction;
    }

    /**
     * The share of the floor's PLANNED ammo the player actually picked up (S-ECONOMY, re-based by
     * balance-overhaul order 2): 1.0 = took everything the planner put down; carriers left alive and
     * pickups skipped read below it. NaN when the floor planned no ammo.
     */
    public float experiencedSupplyShare() {
        if (ammoPlannedUnits <= 0) return Float.NaN;
        return ammoGained / (float) ammoPlannedUnits;
    }

    /** Net HP drain as a fraction of the player's full vitality pool — the order-3 heal-drain metric. */
    public float netDrainFraction(int fullVitalityPool) {
        if (fullVitalityPool <= 0) return 0f;
        return (healthLost - healthRestored) / (float) fullVitalityPool;
    }
}
