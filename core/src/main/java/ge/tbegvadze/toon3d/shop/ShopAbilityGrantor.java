package ge.tbegvadze.toon3d.shop;

import ge.tbegvadze.toon3d.progression.UpgradeCard;

/**
 * The seam (shop_order_4) between {@link ShopAbilityService} and the live progression systems that
 * actually apply a boon's stat effects. The shop package must not reach into {@code World} internals
 * (Player, PlayerStats, EnemyManager), so the owner of those systems supplies this grantor and the
 * service calls it on a purchase.
 *
 * <p>Implementations must reuse the SAME effect path the level-up screen uses (World applies each
 * {@link UpgradeCard}'s attribute / HP / armour / damage deltas), so a shop-bought boon is identical
 * to the free level-up pick — WITHOUT advancing the player's XP level (a purchase is not a level-up).
 */
@FunctionalInterface
public interface ShopAbilityGrantor {

    /** Applies the boon's permanent-for-run effects to the player. Called once, on purchase. */
    void grantAbility(UpgradeCard boon);
}
