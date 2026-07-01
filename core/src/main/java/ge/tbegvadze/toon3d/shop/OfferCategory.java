package ge.tbegvadze.toon3d.shop;

/**
 * The five things a UAC Fabricator can sell (shop_order_2).
 *
 * <p>GOLDEN CONSTRAINT: there is deliberately NO {@code WEAPON} category — the shop only improves
 * weapons the player already owns and resupplies them. Never add a category that sells a weapon.
 */
public enum OfferCategory {
    WEAPON_TIER_UPGRADE,
    WEAPON_LEVEL_UP,
    PLAYER_ABILITY,
    AMMO,
    MEDKIT;

    /** Upgrade offers make gear better (the marquee slot); supply offers restock. */
    public boolean isUpgrade() {
        return this == WEAPON_TIER_UPGRADE || this == WEAPON_LEVEL_UP || this == PLAYER_ABILITY;
    }

    /** Supply offers (ammo, medkits) are the always-available resupply slot. */
    public boolean isSupply() {
        return this == AMMO || this == MEDKIT;
    }
}
