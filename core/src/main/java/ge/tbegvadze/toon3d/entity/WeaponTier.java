package ge.tbegvadze.toon3d.entity;

public enum WeaponTier {

    COMMON   (0, 0.604f, 0.627f, 0.651f, "Common"),
    UNCOMMON (1, 0.298f, 0.780f, 0.298f, "Uncommon"),
    RARE     (2, 0.227f, 0.627f, 1.000f, "Rare"),
    EPIC     (3, 0.694f, 0.298f, 1.000f, "Epic"),
    LEGENDARY(5, 1.000f, 0.690f, 0.125f, "Legendary");

    public final int    abilitySlots;
    public final float  colorRed;
    public final float  colorGreen;
    public final float  colorBlue;
    public final String displayName;

    WeaponTier(int abilitySlots, float colorRed, float colorGreen,
               float colorBlue, String displayName) {
        this.abilitySlots = abilitySlots;
        this.colorRed     = colorRed;
        this.colorGreen   = colorGreen;
        this.colorBlue    = colorBlue;
        this.displayName  = displayName;
    }

    /** Standard (non-signature) slot count. Legendary uses 4 standard + 1 signature. */
    public int standardSlotCount() {
        return this == LEGENDARY ? 4 : abilitySlots;
    }

    public boolean isLegendary() {
        return this == LEGENDARY;
    }
}
