package ge.tbegvadze.toon3d.shop;

import ge.tbegvadze.toon3d.entity.WeaponProfile;
import ge.tbegvadze.toon3d.entity.WeaponTier;
import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.util.GameBalance;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.WeaponConstants;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

/**
 * shop_order_2's built-in {@link ShopOfferSource}. Builds concrete offers (including price) from the
 * systems available today: ammo (from the player's usable ammo types), medkits, and weapon
 * level-up / tier-upgrade offers (read-only, targeting an owned weapon — the EFFECT is applied by
 * shop_order_3). Player-ability offers require the boon system that shop_order_4 owns, so
 * {@link #rollAbilityOffer} returns null until then (the roller falls back to a supply offer).
 */
public final class DefaultShopOfferSource implements ShopOfferSource {

    @Override
    public ShopEntry rollTierUpgradeOffer(ShopContext context, Random random) {
        List<WeaponProfile> upgradeable = new ArrayList<>();
        for (WeaponProfile weapon : context.ownedWeapons) {
            if (weapon != null && weapon.getTier() != WeaponTier.LEGENDARY) upgradeable.add(weapon);
        }
        if (upgradeable.isEmpty()) return null;

        WeaponProfile target      = upgradeable.get(random.nextInt(upgradeable.size()));
        WeaponTier    destination = ShopWeaponService.nextTier(target.getTier());
        OfferRarity   rarity      = tierUpgradeRarity(destination);
        int price = GameMath.shopEntryPrice(GameBalance.SHOP_BASE_PRICE_TIER_UPGRADE,
                context.depth, GameBalance.SHOP_DEPTH_PRICE_SCALE, rarity.priceMultiplier);
        String name = "Tier Up: " + target.getDisplayName()
                + " (" + target.getTier().displayName + " -> " + destination.displayName + ")";
        return new ShopEntry(OfferCategory.WEAPON_TIER_UPGRADE, rarity, name,
                "Raise rarity tier — adds an ability slot.", price, 0, target,
                "TIER:" + target.getDisplayName());
    }

    @Override
    public ShopEntry rollLevelUpOffer(ShopContext context, Random random) {
        List<WeaponProfile> levelable = new ArrayList<>();
        for (WeaponProfile weapon : context.ownedWeapons) {
            if (weapon != null && weapon.getWeaponLevel() < WeaponConstants.MAX_WEAPON_LEVEL) {
                levelable.add(weapon);
            }
        }
        if (levelable.isEmpty()) return null;

        WeaponProfile target = levelable.get(random.nextInt(levelable.size()));
        int price = GameMath.shopEntryPrice(GameBalance.SHOP_BASE_PRICE_WEAPON_LEVELUP,
                context.depth, GameBalance.SHOP_DEPTH_PRICE_SCALE, OfferRarity.COMMON.priceMultiplier);
        String name = "Level Up: " + target.getDisplayName()
                + " (Lv" + target.getWeaponLevel() + " -> Lv" + (target.getWeaponLevel() + 1) + ")";
        return new ShopEntry(OfferCategory.WEAPON_LEVEL_UP, OfferRarity.COMMON, name,
                "Raise weapon stats by one level.", price, 0, target,
                "LEVELUP:" + target.getDisplayName());
    }

    @Override
    public ShopEntry rollAbilityOffer(ShopContext context, Random random) {
        // Player abilities are drawn from the boon system that shop_order_4 owns; none exists yet.
        return null;
    }

    @Override
    public ShopEntry rollAmmoOffer(ShopContext context, Random random) {
        // Usable ammo = the types the player's owned weapons actually consume (deterministic order).
        LinkedHashSet<AmmoType> usable = new LinkedHashSet<>();
        for (WeaponProfile weapon : context.ownedWeapons) {
            if (weapon == null) continue;
            AmmoType ammoType = weapon.getAmmoType();
            if (ammoType != null) usable.add(ammoType);
        }
        if (usable.isEmpty()) return null;

        List<AmmoType> usableList = new ArrayList<>(usable);
        AmmoType ammoType = usableList.get(random.nextInt(usableList.size()));

        // Roughly 60% small box, 40% large box.
        boolean large  = random.nextInt(100) >= 60;
        int amount     = large
                ? ammoType.getAmountPerBox() * GameBalance.SHOP_AMMO_LARGE_BOX_MULTIPLIER
                : ammoType.getAmountPerBox();
        int basePrice  = large ? GameBalance.SHOP_BASE_PRICE_AMMO_LARGE : GameBalance.SHOP_BASE_PRICE_AMMO_SMALL;
        int price = GameMath.shopEntryPrice(basePrice, context.depth,
                GameBalance.SHOP_DEPTH_PRICE_SCALE, OfferRarity.COMMON.priceMultiplier);
        String name = "Ammo: " + ammoType.getDisplayName() + " x" + amount;
        return new ShopEntry(OfferCategory.AMMO, OfferRarity.COMMON, name,
                "Reserve ammo for your guns.", price, amount, ammoType,
                "AMMO:" + ammoType.name() + ":" + amount);
    }

    @Override
    public ShopEntry rollMedkitOffer(ShopContext context, Random random) {
        boolean field = random.nextBoolean();
        ItemType itemType = field ? ItemType.MEDKIT_LARGE : ItemType.MEDKIT_SMALL;
        int basePrice     = field ? GameBalance.SHOP_BASE_PRICE_MEDKIT_FIELD
                                   : GameBalance.SHOP_BASE_PRICE_MEDKIT_STIM;
        int price = GameMath.shopEntryPrice(basePrice, context.depth,
                GameBalance.SHOP_DEPTH_PRICE_SCALE, OfferRarity.COMMON.priceMultiplier);
        return new ShopEntry(OfferCategory.MEDKIT, OfferRarity.COMMON, itemType.getDisplayName(),
                "Healing — stashed in your inventory.", price, 1, itemType,
                "MEDKIT:" + itemType.name());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static OfferRarity tierUpgradeRarity(WeaponTier destination) {
        switch (destination) {
            case LEGENDARY: return OfferRarity.EPIC;
            case EPIC:      return OfferRarity.RARE;
            default:        return OfferRarity.COMMON;
        }
    }
}
