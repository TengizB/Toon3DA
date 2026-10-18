package ge.tbegvadze.toon3d.sim;

import ge.tbegvadze.toon3d.relic.RelicRarity;

/**
 * The S-RELIC probe rows (relics-run-changing-artifacts, CP8): a TACTICAL variant that LEAVES every relic offer
 * and is instead GRANTED one relic of a fixed rarity at depth-1 arrival — or none at all, the baseline the other
 * rows are measured against. {@link #NONE} is plain TACTICAL, which takes offers normally.
 *
 * <p>Per-constant data only: nothing switches on a probe.
 */
public enum RelicProbe {
    /** Plain TACTICAL: takes offers through {@link TacticalPolicy#chooseRelic}. Not a probe row. */
    NONE("", false, null),
    /** The baseline: declines every offer and is granted nothing. */
    NO_RELIC("-NO-RELIC", true, null),
    COMMON("-RELIC-COMMON", true, RelicRarity.COMMON),
    RARE("-RELIC-RARE", true, RelicRarity.RARE),
    LEGENDARY("-RELIC-LEGENDARY", true, RelicRarity.LEGENDARY),
    CURSED("-RELIC-CURSED", true, RelicRarity.CURSED);

    /** Appended to the policy id ({@code "TACTICAL-RELIC-RARE"}). */
    public final String      policyIdSuffix;
    /** True for every probe row: offers are seen and left. */
    public final boolean     declinesOffers;
    /** The rarity granted at depth-1 arrival, or null. */
    public final RelicRarity grantedRarity;

    RelicProbe(String policyIdSuffix, boolean declinesOffers, RelicRarity grantedRarity) {
        this.policyIdSuffix = policyIdSuffix;
        this.declinesOffers = declinesOffers;
        this.grantedRarity  = grantedRarity;
    }

    /** True for the five probe rows (everything but {@link #NONE}). */
    public boolean isProbe() {
        return this != NONE;
    }
}
