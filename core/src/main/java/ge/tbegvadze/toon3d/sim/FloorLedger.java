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

    /** Damage the player's available ranged ammo could deliver on this floor (the SUPPLY term of S). */
    public float rangedSupplyDamage;

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

    /** Net HP drain as a fraction of the player's full vitality pool — the order-3 heal-drain metric. */
    public float netDrainFraction(int fullVitalityPool) {
        if (fullVitalityPool <= 0) return 0f;
        return (healthLost - healthRestored) / (float) fullVitalityPool;
    }

    /** The scarcity ratio S the player actually EXPERIENCED on this floor (supply / demand). */
    public float experiencedScarcityRatio() {
        return ge.tbegvadze.toon3d.util.GameMath.scarcityRatio(rangedSupplyDamage, demandDamage);
    }
}
