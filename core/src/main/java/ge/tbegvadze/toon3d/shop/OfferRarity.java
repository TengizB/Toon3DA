package ge.tbegvadze.toon3d.shop;

import ge.tbegvadze.toon3d.util.GameBalance;

/**
 * Shop offer rarity (shop_order_2). Drives the price multiplier and the UI card tint (part 5).
 * Colours follow the loot convention shared with the weapon tiers (grey → blue → gold).
 */
public enum OfferRarity {
    COMMON(GameBalance.SHOP_RARITY_PRICE_MULT_COMMON, 0.72f, 0.74f, 0.78f),
    RARE  (GameBalance.SHOP_RARITY_PRICE_MULT_RARE,   0.227f, 0.627f, 1.000f),
    EPIC  (GameBalance.SHOP_RARITY_PRICE_MULT_EPIC,   1.000f, 0.690f, 0.125f);

    public final float priceMultiplier;
    public final float colorRed;
    public final float colorGreen;
    public final float colorBlue;

    OfferRarity(float priceMultiplier, float colorRed, float colorGreen, float colorBlue) {
        this.priceMultiplier = priceMultiplier;
        this.colorRed        = colorRed;
        this.colorGreen      = colorGreen;
        this.colorBlue       = colorBlue;
    }
}
