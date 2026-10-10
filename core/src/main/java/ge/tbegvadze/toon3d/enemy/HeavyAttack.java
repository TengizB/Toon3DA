package ge.tbegvadze.toon3d.enemy;

import ge.tbegvadze.toon3d.util.BalanceConfig;

/**
 * The HEAVY flag as data (balance-overhaul order 4, H1): the existing commit-then-resolve attacks that
 * are sized as a fraction of the ON-CURVE player's max HP (so they must be avoided, not tanked).
 * Each constant carries its own numbers — nothing switches on it. An archetype or special ability that
 * resolves one reports it through {@link EnemyType#heavyWindUp()} / {@link SpecialAbility#heavyAttack()}.
 */
public enum HeavyAttack {

    /** A WIND_UP rush / lunge down a locked cardinal lane (Shell Brute today). */
    CHARGE("Charge", BalanceConfig.HEAVY_DAMAGE_FRACTION, 1),

    /** The Rimeshell Lancer's piercing lance down a locked lane. */
    LANCE("Lance", BalanceConfig.HEAVY_DAMAGE_FRACTION, 1),

    /** A slam on every tile within {@code EnemyConstants.AREA_STRIKE_RADIUS_TILES} (square) of the caster. */
    AREA_STRIKE("Area strike", BalanceConfig.HEAVY_AREA_STRIKE_DAMAGE_FRACTION, 1),

    /** The Plague Hulk's wall-stopped cross blast after its brace countdown. */
    SELF_DESTRUCT("Self-destruct", BalanceConfig.HEAVY_SELF_DESTRUCT_DAMAGE_FRACTION,
            BalanceConfig.PLAGUE_HULK_SELF_DESTRUCT_BRACE_TURNS);

    private final String displayName;
    private final float  damageFraction;
    private final int    minimumCommitTurns;

    HeavyAttack(String displayName, float damageFraction, int minimumCommitTurns) {
        this.displayName        = displayName;
        this.damageFraction     = damageFraction;
        this.minimumCommitTurns = minimumCommitTurns;
    }

    /** Fraction of the on-curve player's max HP an unguarded, unarmoured hit deals (before HEAVY_DAMAGE_SCALE). */
    public float damageFraction() { return damageFraction; }

    /**
     * Full player turns between the commit (intent shown) and the resolution — what the code actually
     * guarantees: 1 for a rush / lance / slam, the brace countdown for the self-destruct. Always >= 1 (H3).
     */
    public int minimumCommitTurns() { return minimumCommitTurns; }

    /** Short name for reports and tables. */
    public String displayName() { return displayName; }
}
