package ge.tbegvadze.toon3d.relic;

/** The four relic tiers. Per-constant data only: nothing switches on a rarity. */
public enum RelicRarity {
    COMMON("COMMON", false, false),
    RARE("RARE", false, false),
    LEGENDARY("LEGENDARY", false, true),
    /** A devil's bargain: a real gain paid for with a real cost, only ever met behind a seal the player breaks. */
    CURSED("CURSED", true, false);

    private final String  label;
    private final boolean cursed;
    private final boolean glowPulses;

    RelicRarity(String label, boolean cursed, boolean glowPulses) {
        this.label  = label;
        this.cursed = cursed;
        this.glowPulses = glowPulses;
    }

    /** The card's rarity label. */
    public String label() { return label; }

    public boolean isCursed() { return cursed; }

    /** Presentation only: the offer card's glow breathes (LEGENDARY). Per-constant data, never switched on. */
    public boolean glowPulses() { return glowPulses; }
}
