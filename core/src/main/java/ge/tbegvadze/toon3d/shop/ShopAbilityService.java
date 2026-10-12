package ge.tbegvadze.toon3d.shop;

import ge.tbegvadze.toon3d.progression.Perk;

import java.util.function.Predicate;

/**
 * shop_order_4 bridge for the {@code PLAYER_ABILITY} category — since balance-overhaul order 5, the paid,
 * alternate acquisition of ONE RANK of the same tagged perks the level-up screen offers. It is the
 * {@link ShopEffectApplier} for that category and contains NO build logic: it resolves the entry's
 * {@link Perk} payload and delegates to a {@link ShopAbilityGrantor} that reuses World's level-up effect
 * path, so a bought rank is exactly a level-up pick.
 *
 * <p>The offer source only stocks a perk the player has not maxed; a rank that maxed out between the roll
 * and the purchase is refused by the build itself (the grantor's add is a no-op). Weapon and supply
 * categories are not handled here; {@link #canApply}/{@link #apply} return false / no-op for them.
 */
public final class ShopAbilityService implements ShopEffectApplier {

    private final ShopAbilityGrantor grantor;
    private final Predicate<Perk> perkMaxed;

    /** @param perkMaxed true when the build can take no further rank of the perk (the purchase is then refused) */
    public ShopAbilityService(ShopAbilityGrantor grantor, Predicate<Perk> perkMaxed) {
        this.grantor = grantor;
        this.perkMaxed = perkMaxed;
    }

    @Override
    public boolean canApply(ShopEntry entry) {
        Perk perk = resolve(entry);
        // A rank that maxed out since the machine was stocked is refused, so credits are never charged for a no-op.
        return grantor != null && perk != null && (perkMaxed == null || !perkMaxed.test(perk));
    }

    @Override
    public void apply(ShopEntry entry) {
        Perk perk = resolve(entry);
        if (perk == null || grantor == null) return;
        grantor.grantAbility(perk);
    }

    /** Resolves an entry's payload to the perk it grants a rank of, or null if it is not a player-ability offer. */
    private static Perk resolve(ShopEntry entry) {
        if (entry != null && entry.category == OfferCategory.PLAYER_ABILITY
                && entry.payload instanceof Perk) {
            return (Perk) entry.payload;
        }
        return null;
    }
}
