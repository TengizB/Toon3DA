package ge.tbegvadze.toon3d.util;

/**
 * THE on-curve player at one depth (balance-overhaul order 1, rule R11) — the single expected-player
 * model the balance audit, the simulator and the boss derivation all read. Built only by
 * {@link GameMath#expectedPlayerAtDepth} (on-curve) or {@link GameMath#expectedPlayer} (a deliberately
 * off-curve variant: a lagging weapon, a lower character level), so every consumer measures against
 * exactly the same arithmetic.
 *
 * <p>Immutable value type: pure data, no LibGDX state.
 */
public final class ExpectedPlayer {

    /** The floor this player is measured on (its threat level). */
    public final int   depth;
    /** Weapon level held (on-curve = depth). */
    public final int   weaponLevel;
    /** Rarity damage multiplier of the weapon held (on-curve = the region's expected rarity). */
    public final float rarityMultiplier;
    /** Level-gap multiplier the weapon takes on this floor (on-curve = 1.0). */
    public final float levelGapMultiplier;
    /** Character level (on-curve = 1 + EXPECTED_LEVELS_PER_DEPTH * (depth - 1)). */
    public final int   characterLevel;
    /**
     * Sustained damage per turn, anchored so the depth-1 on-curve player equals
     * {@link BalanceConfig#REFERENCE_PLAYER_DPT} — the yardstick boss HP and Threat Points are read against.
     */
    public final float damagePerTurn;
    /**
     * Per-hit damage of the R8 reference weapon (Assault Rifle, measured at
     * {@link BalanceConfig#LADDER_REFERENCE_RANGE_TILES}) carried by this player — the hits-to-kill yardstick.
     */
    public final float referenceHitDamage;
    /** Max HP (vitality growth only; card eHP is folded into {@link #effectiveHitPoints}). */
    public final float maxHealth;
    /** Max armour (vitality growth only). */
    public final float maxArmor;
    /** Effective HP: (max HP + max armour) plus the expected flat defence-card eHP (R9). */
    public final float effectiveHitPoints;

    ExpectedPlayer(int depth, int weaponLevel, float rarityMultiplier, float levelGapMultiplier,
                   int characterLevel, float damagePerTurn, float referenceHitDamage,
                   float maxHealth, float maxArmor, float effectiveHitPoints) {
        this.depth              = depth;
        this.weaponLevel        = weaponLevel;
        this.rarityMultiplier   = rarityMultiplier;
        this.levelGapMultiplier = levelGapMultiplier;
        this.characterLevel     = characterLevel;
        this.damagePerTurn      = damagePerTurn;
        this.referenceHitDamage = referenceHitDamage;
        this.maxHealth          = maxHealth;
        this.maxArmor           = maxArmor;
        this.effectiveHitPoints = effectiveHitPoints;
    }

    @Override public String toString() {
        return String.format("ExpectedPlayer[d=%d wLv=%d rarity=%.2f gap=%.2f cLv=%d dpt=%.1f hit=%.1f eHP=%.0f]",
                depth, weaponLevel, rarityMultiplier, levelGapMultiplier, characterLevel,
                damagePerTurn, referenceHitDamage, effectiveHitPoints);
    }
}
