package ge.tbegvadze.toon3d.shop;

/**
 * The purchase-effect seam (shop_order_2) that parts 3-4 implement. The generic buy transaction
 * ({@link ShopTransaction}) handles the credit guard, deduction, and sold-out marking; the concrete
 * delivery of the bought thing (raise a weapon's tier/level, grant a boon, add ammo/medkit) lives
 * behind this interface so part 2 stays free of category effects.
 */
public interface ShopEffectApplier {

    /** Re-checks at purchase time that the offer can still be delivered (e.g. inventory has room). */
    boolean canApply(ShopEntry entry);

    /** Delivers the offer's effect. Only called after the credit guard passed and canApply() was true. */
    void apply(ShopEntry entry);
}
