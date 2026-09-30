package ge.tbegvadze.toon3d.shop;

import java.util.Random;

/**
 * The delegation seam (shop_order_2) between the {@link ShopRoller} and the systems that own each
 * offer category's validity, payload, and display data. Each method returns a fully-built
 * {@link ShopEntry} (including price) for the given player context, or {@code null} when no valid
 * offer of that category exists for this player (the roller then falls back / re-rolls).
 *
 * <p>shop_order_2 ships {@link DefaultShopOfferSource}, which builds ammo/medkit/weapon offers from
 * currently-available systems. shop_order_3 and shop_order_4 refine the weapon and ability/supply
 * offers respectively (and their purchase effects). The roller never depends on which source is used.
 */
public interface ShopOfferSource {

    ShopEntry rollTierUpgradeOffer(ShopContext context, Random random);

    ShopEntry rollLevelUpOffer(ShopContext context, Random random);

    /**
     * The guaranteed LEVEL UP rung (R13): the level-up offer for the EQUIPPED weapon, or null when
     * there is no equipped weapon, it is level-gap-exempt (the Fist), or it already stands at
     * min(MAX_WEAPON_LEVEL, depth + 1) — at the cap the offer disappears.
     */
    ShopEntry rollLadderRungOffer(ShopContext context);

    ShopEntry rollAbilityOffer(ShopContext context, Random random);

    ShopEntry rollAmmoOffer(ShopContext context, Random random);

    ShopEntry rollMedkitOffer(ShopContext context, Random random);
}
