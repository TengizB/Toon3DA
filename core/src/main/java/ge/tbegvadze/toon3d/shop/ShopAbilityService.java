package ge.tbegvadze.toon3d.shop;

import ge.tbegvadze.toon3d.progression.UpgradeCard;

/**
 * shop_order_4 bridge for the {@code PLAYER_ABILITY} category — the paid, alternate acquisition of
 * the SAME budget-equal boons the level-up screen hands out for free on XP thresholds. It is the
 * {@link ShopEffectApplier} for that category and contains NO stat-mutation logic: it resolves the
 * entry's {@link UpgradeCard} payload and delegates to a {@link ShopAbilityGrantor} that reuses
 * World's level-up effect path, so a bought Vitality boon heals-to-full exactly as the level-up
 * Vitality pick does.
 *
 * <p>Every {@link UpgradeCard} is a repeatable stat boon, so any drawn boon is always applicable
 * (there is no "already at max stacks" rejection). Weapon and supply categories are not handled here;
 * {@link #canApply}/{@link #apply} return false / no-op for them.
 */
public final class ShopAbilityService implements ShopEffectApplier {

    private final ShopAbilityGrantor grantor;

    public ShopAbilityService(ShopAbilityGrantor grantor) {
        this.grantor = grantor;
    }

    @Override
    public boolean canApply(ShopEntry entry) {
        return grantor != null && resolve(entry) != null;
    }

    @Override
    public void apply(ShopEntry entry) {
        UpgradeCard boon = resolve(entry);
        if (boon == null || grantor == null) return;
        grantor.grantAbility(boon);
    }

    /** Resolves an entry's payload to the boon it grants, or null if it is not a player-ability offer. */
    private static UpgradeCard resolve(ShopEntry entry) {
        if (entry != null && entry.category == OfferCategory.PLAYER_ABILITY
                && entry.payload instanceof UpgradeCard) {
            return (UpgradeCard) entry.payload;
        }
        return null;
    }
}
