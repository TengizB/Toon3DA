package ge.tbegvadze.toon3d.shop;

import ge.tbegvadze.toon3d.progression.PlayerStats;

/**
 * The generic buy transaction (shop_order_2): the single place a purchase is validated and settled.
 * Order of operations mirrors the idea doc — sold-out guard, credit guard, category re-check, spend,
 * apply, mark sold. Spending happens only after every guard passes, so a rejected purchase never
 * touches the credit balance. Buying spends NO turn (the shop is a menu pause, part 1).
 */
public final class ShopTransaction {

    public enum Result {
        PURCHASED,
        SOLD_OUT,
        INSUFFICIENT_CREDITS,
        CANNOT_APPLY
    }

    private ShopTransaction() {}

    /**
     * Attempts to buy one entry.
     *
     * @param entry   the shelf line being purchased
     * @param wallet  the credit balance (spent on success)
     * @param applier delivers the effect and re-checks validity; may be null (deducts + marks sold only)
     * @return the outcome; only {@link Result#PURCHASED} deducts credits and marks the entry sold
     */
    public static Result buy(ShopEntry entry, PlayerStats wallet, ShopEffectApplier applier) {
        if (entry == null || wallet == null) return Result.CANNOT_APPLY;
        if (entry.isSoldOut())                return Result.SOLD_OUT;
        if (wallet.getCredits() < entry.price) return Result.INSUFFICIENT_CREDITS;
        if (applier != null && !applier.canApply(entry)) return Result.CANNOT_APPLY;

        // Guards passed — settle. spendCredits re-checks affordability atomically.
        if (!wallet.spendCredits(entry.price)) return Result.INSUFFICIENT_CREDITS;
        if (applier != null) applier.apply(entry);
        entry.markSoldOut();
        return Result.PURCHASED;
    }
}
