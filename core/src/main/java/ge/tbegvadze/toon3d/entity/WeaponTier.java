package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.util.BalanceConfig;

/**
 * Weapon rarity. Since balance-overhaul order 5 (B5) a tier is a GUARANTEED ability SHAPE plus a priced
 * BAND, not a slot cap the roller may under-fill:
 * <ul>
 *   <li>{@link #abilityCount} — the standard + MAJOR abilities every roll of this tier carries, exactly
 *       (COMMON 0, UNCOMMON 1, RARE 2, EPIC 3, LEGENDARY 3 plus its signature);</li>
 *   <li>{@link #majorCount} — how many of those are drawn from the MAJOR list (EPIC 1, LEGENDARY 1);</li>
 *   <li>{@link #abilityPowerPointBudget} — the centre of the band the roll's priced total must land in
 *       ({@code budget × (1 ± TIER_ABILITY_PP_TOLERANCE)}).</li>
 * </ul>
 * All three are data in {@code BalanceConfig} SECTION 24; this enum only binds them to a tier.
 */
public enum WeaponTier {

    COMMON   (BalanceConfig.TIER_ABILITY_COUNT_COMMON,    BalanceConfig.TIER_MAJOR_COUNT_COMMON,
              BalanceConfig.TIER_ABILITY_PP_BUDGET_COMMON,    false,
              0.604f, 0.627f, 0.651f, "Common"),
    UNCOMMON (BalanceConfig.TIER_ABILITY_COUNT_UNCOMMON,  BalanceConfig.TIER_MAJOR_COUNT_UNCOMMON,
              BalanceConfig.TIER_ABILITY_PP_BUDGET_UNCOMMON,  false,
              0.298f, 0.780f, 0.298f, "Uncommon"),
    RARE     (BalanceConfig.TIER_ABILITY_COUNT_RARE,      BalanceConfig.TIER_MAJOR_COUNT_RARE,
              BalanceConfig.TIER_ABILITY_PP_BUDGET_RARE,      false,
              0.227f, 0.627f, 1.000f, "Rare"),
    EPIC     (BalanceConfig.TIER_ABILITY_COUNT_EPIC,      BalanceConfig.TIER_MAJOR_COUNT_EPIC,
              BalanceConfig.TIER_ABILITY_PP_BUDGET_EPIC,      false,
              0.694f, 0.298f, 1.000f, "Epic"),
    LEGENDARY(BalanceConfig.TIER_ABILITY_COUNT_LEGENDARY, BalanceConfig.TIER_MAJOR_COUNT_LEGENDARY,
              BalanceConfig.TIER_ABILITY_PP_BUDGET_LEGENDARY, true,
              1.000f, 0.690f, 0.125f, "Legendary");

    /** Standard + MAJOR abilities every roll of this tier carries (the signature is NOT counted). */
    public final int    abilityCount;
    /** How many of {@link #abilityCount} are drawn from the MAJOR list. */
    public final int    majorCount;
    /** Centre of the priced band the whole roll (signature included) must land in. */
    public final float  abilityPowerPointBudget;
    /** Whether this tier also rolls one legendary signature ability on top of its count. */
    public final boolean rollsSignature;
    public final float  colorRed;
    public final float  colorGreen;
    public final float  colorBlue;
    public final String displayName;

    WeaponTier(int abilityCount, int majorCount, float abilityPowerPointBudget, boolean rollsSignature,
               float colorRed, float colorGreen, float colorBlue, String displayName) {
        this.abilityCount            = abilityCount;
        this.majorCount              = majorCount;
        this.abilityPowerPointBudget = abilityPowerPointBudget;
        this.rollsSignature          = rollsSignature;
        this.colorRed                = colorRed;
        this.colorGreen              = colorGreen;
        this.colorBlue               = colorBlue;
        this.displayName             = displayName;
    }

    /** Standard (non-MAJOR, non-signature) abilities a roll of this tier carries. */
    public int standardCount() {
        return abilityCount - majorCount;
    }

    /** Every ability a roll of this tier carries, signature included (UI slot geometry). */
    public int totalAbilityCount() {
        return abilityCount + (rollsSignature ? 1 : 0);
    }

    /** Lowest priced total a roll of this tier may carry: {@code budget × (1 − tolerance)}. */
    public float abilityPowerPointFloor() {
        return abilityPowerPointBudget * (1f - BalanceConfig.TIER_ABILITY_PP_TOLERANCE);
    }

    /** Highest priced total a roll of this tier may carry: {@code budget × (1 + tolerance)}. */
    public float abilityPowerPointCeiling() {
        return abilityPowerPointBudget * (1f + BalanceConfig.TIER_ABILITY_PP_TOLERANCE);
    }

    public boolean isLegendary() {
        return this == LEGENDARY;
    }
}
