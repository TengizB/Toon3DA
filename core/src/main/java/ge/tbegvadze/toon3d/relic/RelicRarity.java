package ge.tbegvadze.toon3d.relic;

/** The four relic tiers. Per-constant data only: nothing switches on a rarity. */
public enum RelicRarity {
    COMMON("COMMON", false),
    RARE("RARE", false),
    LEGENDARY("LEGENDARY", false),
    /** A devil's bargain: a real gain paid for with a real cost, only ever met behind a seal the player breaks. */
    CURSED("CURSED", true);

    private final String  label;
    private final boolean cursed;

    RelicRarity(String label, boolean cursed) {
        this.label  = label;
        this.cursed = cursed;
    }

    /** The card's rarity label. */
    public String label() { return label; }

    public boolean isCursed() { return cursed; }
}
