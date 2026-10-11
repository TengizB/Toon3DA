package ge.tbegvadze.toon3d.shop;

import ge.tbegvadze.toon3d.progression.Perk;

/**
 * The seam (shop_order_4; re-pointed by balance-overhaul order 5) between {@link ShopAbilityService} and the
 * live progression systems that actually apply a bought perk rank. The shop package must not reach into
 * {@code World} internals (Player, PlayerBuild, EnemyManager), so the owner of those systems supplies this
 * grantor and the service calls it on a purchase.
 *
 * <p>Implementations must reuse the SAME effect path the level-up screen uses (add the rank to the run's
 * {@code PlayerBuild}, then re-derive every build-dependent value), so a bought rank is identical to a
 * level-up pick — WITHOUT advancing the player's XP level (a purchase is not a level-up).
 */
@FunctionalInterface
public interface ShopAbilityGrantor {

    /** Adds one rank of the perk to the player's build. Called once, on purchase. */
    void grantAbility(Perk perk);
}
