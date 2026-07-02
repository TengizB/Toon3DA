package ge.tbegvadze.toon3d.shop;

/**
 * Composes the per-category {@link ShopEffectApplier}s into the single applier
 * {@link ShopTransaction#buy} expects (shop_order_4). Each concrete service owns exactly one group
 * of categories and no-ops for the rest — the weapon service (tier / level, shop_order_3), the
 * supply service (ammo / medkit, shop_order_4), and the ability service (player boons, shop_order_4).
 * The router dispatches a purchase to the one service that owns the entry's {@link OfferCategory}, so
 * a single {@code ShopEffectApplier} can deliver every category the UAC Fabricator sells.
 *
 * <p>Holds no state of its own; all validity and mutation live in the delegate services.
 */
public final class ShopEffectRouter implements ShopEffectApplier {

    private final ShopEffectApplier weaponApplier;
    private final ShopEffectApplier supplyApplier;
    private final ShopEffectApplier abilityApplier;

    public ShopEffectRouter(ShopEffectApplier weaponApplier, ShopEffectApplier supplyApplier,
                            ShopEffectApplier abilityApplier) {
        this.weaponApplier  = weaponApplier;
        this.supplyApplier  = supplyApplier;
        this.abilityApplier = abilityApplier;
    }

    @Override
    public boolean canApply(ShopEntry entry) {
        ShopEffectApplier applier = applierFor(entry);
        return applier != null && applier.canApply(entry);
    }

    @Override
    public void apply(ShopEntry entry) {
        ShopEffectApplier applier = applierFor(entry);
        if (applier != null) applier.apply(entry);
    }

    /** Selects the delegate that owns the entry's category, or null for an unknown/absent entry. */
    private ShopEffectApplier applierFor(ShopEntry entry) {
        if (entry == null) return null;
        switch (entry.category) {
            case WEAPON_TIER_UPGRADE:
            case WEAPON_LEVEL_UP:
                return weaponApplier;
            case AMMO:
            case MEDKIT:
                return supplyApplier;
            case PLAYER_ABILITY:
                return abilityApplier;
            default:
                return null;
        }
    }
}
