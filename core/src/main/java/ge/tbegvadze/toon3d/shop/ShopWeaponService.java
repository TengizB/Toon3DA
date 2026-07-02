package ge.tbegvadze.toon3d.shop;

import ge.tbegvadze.toon3d.entity.Weapon;
import ge.tbegvadze.toon3d.entity.WeaponRoller;
import ge.tbegvadze.toon3d.entity.WeaponTier;

/**
 * shop_order_3 bridge between the shop and the weapon system. Delivers the two weapon-improvement
 * services the UAC Fabricator sells — {@code WEAPON_TIER_UPGRADE} and {@code WEAPON_LEVEL_UP} —
 * for a weapon the player ALREADY OWNS (golden constraint: the shop never sells a weapon).
 *
 * <p>This is the {@link ShopEffectApplier} implementation for the weapon categories (parts 3-4 fill
 * in the seam). It contains NO tier/level/ability math: it resolves the entry's payload to the live
 * {@link Weapon} and calls the reusable mutators on {@link WeaponRoller}, which owns the ability
 * roll and magnitude scaling shared with the spawn roll. The supplied roller provides the seeded
 * RNG for the tier-upgrade ability roll (revealed on purchase).
 *
 * <p>Ammo, medkit, and player-ability categories are not handled here — a different applier owns
 * those; {@link #canApply}/{@link #apply} return false / no-op for them.
 */
public final class ShopWeaponService implements ShopEffectApplier {

    private final WeaponRoller roller;

    public ShopWeaponService(WeaponRoller roller) {
        this.roller = roller;
    }

    /** Next rarity tier above {@code current}, clamped at Legendary. Shared with the offer source. */
    public static WeaponTier nextTier(WeaponTier current) {
        WeaponTier[] tiers = WeaponTier.values();
        int nextIndex = Math.min(current.ordinal() + 1, tiers.length - 1);
        return tiers[nextIndex];
    }

    @Override
    public boolean canApply(ShopEntry entry) {
        if (entry == null) return false;
        Weapon weapon = resolve(entry);
        if (weapon == null) return false;
        switch (entry.category) {
            case WEAPON_TIER_UPGRADE: return weapon.canUpgradeTier();
            case WEAPON_LEVEL_UP:     return weapon.canLevelUp();
            default:                  return false;
        }
    }

    @Override
    public void apply(ShopEntry entry) {
        Weapon weapon = resolve(entry);
        if (weapon == null || roller == null) return;
        switch (entry.category) {
            case WEAPON_TIER_UPGRADE:
                roller.applyTierUpgrade(weapon, nextTier(weapon.getTier()));
                break;
            case WEAPON_LEVEL_UP:
                roller.applyLevelUp(weapon);
                break;
            default:
                // Not a weapon offer — handled by another applier.
                break;
        }
    }

    /** Resolves an entry's payload to the concrete mutable weapon, or null if it isn't one. */
    private static Weapon resolve(ShopEntry entry) {
        if (entry != null && entry.payload instanceof Weapon) {
            return (Weapon) entry.payload;
        }
        return null;
    }
}
