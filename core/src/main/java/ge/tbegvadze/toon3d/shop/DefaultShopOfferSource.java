package ge.tbegvadze.toon3d.shop;

import ge.tbegvadze.toon3d.entity.WeaponProfile;
import ge.tbegvadze.toon3d.entity.WeaponRoller;
import ge.tbegvadze.toon3d.entity.WeaponTier;
import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.progression.UpgradeCard;
import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.GameBalance;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.WeaponConstants;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

/**
 * shop_order_2's built-in {@link ShopOfferSource}. Builds concrete offers (including price) from the
 * systems available today: ammo (from the player's usable ammo types), medkits, weapon
 * level-up / tier-upgrade offers, and player-ability boons.
 *
 * <p><b>Pricing (new-game-balancr order 3).</b> Every price is derived from the offer's VALUE IN
 * POWER POINTS through {@link GameMath#shopPrice} — there are ZERO hand-set per-offer base prices.
 * A weapon-class upgrade prices on its ability/level PP; a consumable prices on its supply value
 * converted to PP (damage supplied / {@code SHOP_AMMO_DAMAGE_PER_POWER_POINT}, or HP restored /
 * {@code SHOP_HEAL_HP_PER_POWER_POINT}). The single knob {@code SHOP_CREDITS_PER_POWER_POINT} sets
 * the whole economy's price level, coupled to the credit BAND (R-CREDITS).
 */
public final class DefaultShopOfferSource implements ShopOfferSource {

    /** Credit price of an offer worth {@code valuePowerPoints}, at the context's depth. */
    private static int priceOf(float valuePowerPoints, ShopContext context) {
        return GameMath.shopPrice(valuePowerPoints, GameBalance.SHOP_CREDITS_PER_POWER_POINT,
                context.depth, GameBalance.SHOP_DEPTH_PRICE_SCALE);
    }

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
        // Priced on the ABILITY-PP budget the destination tier unlocks — the marquee value is the
        // ability slot the tier buys (SECTION 15, TIER_ABILITY_PP_BUDGET_*).
        float valuePowerPoints = WeaponRoller.tierAbilityPowerPointBudget(destination);
        int price = priceOf(valuePowerPoints, context);
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
        // A weapon level is ~+10% damage — priced at the fixed weapon-level PP value.
        int price = priceOf(BalanceConfig.SHOP_WEAPON_LEVEL_UP_POWER_POINTS, context);
        String name = "Level Up: " + target.getDisplayName()
                + " (Lv" + target.getWeaponLevel() + " -> Lv" + (target.getWeaponLevel() + 1) + ")";
        return new ShopEntry(OfferCategory.WEAPON_LEVEL_UP, OfferRarity.COMMON, name,
                "Raise weapon stats by one level.", price, 0, target,
                "LEVELUP:" + target.getDisplayName());
    }

    @Override
    public ShopEntry rollAbilityOffer(ShopContext context, Random random) {
        // shop_order_4: a PLAYER_ABILITY is a paid, alternate acquisition of the SAME boons the
        // level-up screen hands out — the budget-equal UpgradeCards. Priced on the card's OWN power
        // points (each card ~= LEVEL_UP_BUDGET_PP), so a boon and a level-up card cost the same value.
        UpgradeCard[] boons = UpgradeCard.values();
        if (boons.length == 0) return null;
        UpgradeCard boon = boons[random.nextInt(boons.length)];

        // Trade-off boons read as EPIC, plain budget boons as RARE — purely a UI/card tint now.
        OfferRarity rarity = boon.isTradeOff() ? OfferRarity.EPIC : OfferRarity.RARE;
        int price = priceOf(boon.estimatedPowerPoints(), context);
        return new ShopEntry(OfferCategory.PLAYER_ABILITY, rarity, "Boon: " + boon.displayName,
                boon.description, price, 0, boon, "ABILITY:" + boon.name());
    }

    @Override
    public ShopEntry rollAmmoOffer(ShopContext context, Random random) {
        // Usable ammo = the types the player's owned weapons actually consume (deterministic order),
        // paired with the per-shot damage of the weapon that eats them (for supply-value pricing).
        LinkedHashSet<AmmoType> usable = new LinkedHashSet<>();
        EnumMap<AmmoType, Integer> damagePerUnit = new EnumMap<>(AmmoType.class);
        for (WeaponProfile weapon : context.ownedWeapons) {
            if (weapon == null) continue;
            AmmoType ammoType = weapon.getAmmoType();
            if (ammoType == null) continue;
            usable.add(ammoType);
            // Keep the highest per-shot damage seen for the type (its most valuable consumer).
            damagePerUnit.merge(ammoType, weapon.getEffectiveDamage(), Math::max);
        }
        if (usable.isEmpty()) return null;

        List<AmmoType> usableList = new ArrayList<>(usable);
        AmmoType ammoType = usableList.get(random.nextInt(usableList.size()));

        // Roughly 60% small box, 40% large box.
        boolean large  = random.nextInt(100) >= 60;
        int amount     = large
                ? ammoType.getAmountPerBox() * GameBalance.SHOP_AMMO_LARGE_BOX_MULTIPLIER
                : ammoType.getAmountPerBox();
        // Priced on damage SUPPLIED = amount * damage-per-unit, converted to PP.
        int perUnit = Math.max(1, damagePerUnit.getOrDefault(ammoType, 1));
        float valuePowerPoints = (amount * (float) perUnit) / BalanceConfig.SHOP_AMMO_DAMAGE_PER_POWER_POINT;
        int price = priceOf(valuePowerPoints, context);
        String name = "Ammo: " + ammoType.getDisplayName() + " x" + amount;
        return new ShopEntry(OfferCategory.AMMO, OfferRarity.COMMON, name,
                "Reserve ammo for your guns.", price, amount, ammoType,
                "AMMO:" + ammoType.name() + ":" + amount);
    }

    @Override
    public ShopEntry rollMedkitOffer(ShopContext context, Random random) {
        boolean field = random.nextBoolean();
        ItemType itemType = field ? ItemType.MEDKIT_LARGE : ItemType.MEDKIT_SMALL;
        // Priced on HP restored, converted to PP.
        int healAmount = field ? BalanceConfig.MEDKIT_FULL_HEAL : BalanceConfig.MEDKIT_STIM_HEAL;
        float valuePowerPoints = healAmount / BalanceConfig.SHOP_HEAL_HP_PER_POWER_POINT;
        int price = priceOf(valuePowerPoints, context);
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
