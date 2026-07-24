package ge.tbegvadze.toon3d.shop;

/**
 * Shop offer rarity (shop_order_2). Drives the UI card tint (part 5) — colours follow the loot
 * convention shared with the weapon tiers (grey → blue → gold).
 *
 * <p>As of new-game-balancr order 3 rarity NO LONGER carries a price multiplier: every offer's
 * price is derived from its VALUE IN POWER POINTS via {@link ge.tbegvadze.toon3d.util.GameMath#shopPrice}
 * (a rarer offer is pricier because it is worth more PP, not because of a rarity surcharge). Rarity
 * is now purely cosmetic — the card tint that tells the player at a glance how premium an offer is.
 */
public enum OfferRarity {
    COMMON(0.72f, 0.74f, 0.78f),
    RARE  (0.227f, 0.627f, 1.000f),
    EPIC  (1.000f, 0.690f, 0.125f);

    public final float colorRed;
    public final float colorGreen;
    public final float colorBlue;

    OfferRarity(float colorRed, float colorGreen, float colorBlue) {
        this.colorRed   = colorRed;
        this.colorGreen = colorGreen;
        this.colorBlue  = colorBlue;
    }
}
