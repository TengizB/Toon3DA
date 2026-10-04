package ge.tbegvadze.toon3d.util;

/**
 * One ranged weapon as the R-ROLE scenario model sees it (balance-overhaul order 3): its fire cadence,
 * its per-tile hit, how an action spreads over a group, and its role extras (burn stacks, stagger,
 * knockback, charge, self-damage). Built by {@code BalanceSchema}'s role registry from the weapon's own
 * declared stats plus the balance constants; read by {@link GameMath#roleScenarioClear} and
 * {@link GameMath#roleScenarioBruiserHitsTaken}. Immutable; all damage numbers are DEPTH-1 BASE values —
 * the caller scales them with the ladder.
 */
public final class RoleScenarioWeapon {

    public final String  displayName;
    /** Fire actions per clip (clip / ammo per action). */
    public final int     clipActions;
    /** Turns a reload costs. */
    public final int     reloadTurns;
    /** Ammo units one fire action spends. */
    public final int     ammoPerAction;
    /** Hits one action lands on its primary target (chaingun 3, double-barrel 2, others 1). */
    public final int     hitsPerAction;
    /** Expected-value accuracy multiplier applied to every hit. */
    public final float   accuracy;
    /** Base hit by tile distance; index = distance - 1, beyond the array = out of reach (0). */
    public final float[] baseHitByTile;
    public final RoleScenarioPattern pattern;
    /** SPLASH: the base hit every non-centre group member takes. */
    public final float   splashNeighbourHit;
    /** CHAIN: leaps after the primary, each at chainMultiplier^jump of the primary hit, never resisted. */
    public final int     chainJumps;
    public final float   chainMultiplier;
    /** Turns of charging before every shot (Railgun 1: charge, then the full-charge shot). */
    public final int     chargeTurns;
    /** Burn per stack as a fraction of the base impact hit (0 = no burn). */
    public final float   burnFraction;
    public final float   burnImpactHit;
    public final int     burnTurns;
    public final int     burnMaxStacks;
    /** SPREAD close-range payoff (S8): knockback at <= knockbackMaxTiles, stagger at <= staggerMaxTiles. */
    public final int     knockbackMaxTiles;
    public final int     staggerMaxTiles;
    /** A hit on an ADJACENT target also hurts the shooter (Grenade Launcher contact fuse). */
    public final boolean selfDamageWhenAdjacent;

    public RoleScenarioWeapon(String displayName, int clipActions, int reloadTurns, int ammoPerAction,
                              int hitsPerAction, float accuracy, float[] baseHitByTile,
                              RoleScenarioPattern pattern, float splashNeighbourHit,
                              int chainJumps, float chainMultiplier, int chargeTurns,
                              float burnFraction, float burnImpactHit, int burnTurns, int burnMaxStacks,
                              int knockbackMaxTiles, int staggerMaxTiles, boolean selfDamageWhenAdjacent) {
        this.displayName            = displayName;
        this.clipActions            = Math.max(1, clipActions);
        this.reloadTurns            = Math.max(0, reloadTurns);
        this.ammoPerAction          = Math.max(1, ammoPerAction);
        this.hitsPerAction          = Math.max(1, hitsPerAction);
        this.accuracy               = accuracy;
        this.baseHitByTile          = baseHitByTile.clone();
        this.pattern                = pattern;
        this.splashNeighbourHit     = splashNeighbourHit;
        this.chainJumps             = chainJumps;
        this.chainMultiplier        = chainMultiplier;
        this.chargeTurns            = Math.max(0, chargeTurns);
        this.burnFraction           = burnFraction;
        this.burnImpactHit          = burnImpactHit;
        this.burnTurns              = burnTurns;
        this.burnMaxStacks          = burnMaxStacks;
        this.knockbackMaxTiles      = knockbackMaxTiles;
        this.staggerMaxTiles        = staggerMaxTiles;
        this.selfDamageWhenAdjacent = selfDamageWhenAdjacent;
    }

    /** Base hit at a tile distance (0 outside the weapon's reach). */
    public float baseHitAt(int distanceTiles) {
        if (distanceTiles < 1 || distanceTiles > baseHitByTile.length) return 0f;
        return baseHitByTile[distanceTiles - 1];
    }

    /** True when the weapon applies Incinerator-style burn stacks. */
    public boolean burns() {
        return burnFraction > 0f && burnTurns > 0 && burnMaxStacks > 0;
    }
}
