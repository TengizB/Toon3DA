package ge.tbegvadze.toon3d.relic;

/** Card-text helpers. Card text is built from BalanceConfig so it never drifts from the numbers (the Perk.percent idiom). */
public final class RelicText {

    private RelicText() { }

    /** "15%" from 0.15. */
    public static String percent(float fraction) {
        return Math.round(fraction * 100f) + "%";
    }
}
