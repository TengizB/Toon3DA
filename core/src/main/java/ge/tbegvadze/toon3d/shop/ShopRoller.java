package ge.tbegvadze.toon3d.shop;

import ge.tbegvadze.toon3d.util.GameBalance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Rolls one machine's {@link ShopStock} at floor generation (shop_order_2).
 *
 * <p>Every stock GUARANTEES a useful spread — at least one SUPPLY offer (ammo/medkit) and one
 * UPGRADE offer (weapon tier/level, or ability) — then fills the remaining slots from a weighted
 * category pool. No concrete offer is stocked twice (de-dup by {@link ShopEntry#dedupKey}). All
 * selection uses a seeded {@link Random} so a given run seed reproduces the same shops; each machine
 * is rolled with its own seed so two machines on a floor differ.
 */
public final class ShopRoller {

    /** Nudges a machine's weighted remainder toward upgrades or supplies (two-shop floors). */
    public enum RollBias { NONE, UPGRADE, SUPPLY }

    private static final int WEIGHTED_SLOT_ATTEMPTS = 12;
    private static final int SUPPLY_RETRIES         = 8;
    private static final int UPGRADE_RETRIES        = 8;

    private final ShopOfferSource source;

    public ShopRoller(ShopOfferSource source) {
        this.source = source;
    }

    public ShopStock roll(ShopContext context, long seed, RollBias bias) {
        Random random = new Random(seed);
        int span       = GameBalance.SHOP_ENTRY_MAX - GameBalance.SHOP_ENTRY_MIN + 1;
        int entryCount = GameBalance.SHOP_ENTRY_MIN + random.nextInt(span);

        List<ShopEntry> entries  = new ArrayList<>(entryCount);
        Set<String>     usedKeys = new HashSet<>();

        // Guaranteed SUPPLY slot (ammo or medkit) — always achievable.
        ShopEntry supply = rollSupply(context, random, usedKeys);
        if (supply != null) addEntry(entries, usedKeys, supply);

        // Guaranteed UPGRADE slot — falls back to supply if the player owns no upgradeable weapon.
        ShopEntry upgrade = rollUpgrade(context, random, usedKeys);
        if (upgrade == null) upgrade = rollSupply(context, random, usedKeys);
        if (upgrade != null) addEntry(entries, usedKeys, upgrade);

        // Remaining slots from the weighted category pool; stop early if no distinct offer remains.
        while (entries.size() < entryCount) {
            ShopEntry entry = rollWeightedSlot(context, random, usedKeys, bias);
            if (entry == null) break;
            addEntry(entries, usedKeys, entry);
        }

        return new ShopStock(entries.toArray(new ShopEntry[0]));
    }

    // ── slot rollers ───────────────────────────────────────────────────────────

    private ShopEntry rollWeightedSlot(ShopContext context, Random random,
                                       Set<String> usedKeys, RollBias bias) {
        for (int attempt = 0; attempt < WEIGHTED_SLOT_ATTEMPTS; attempt++) {
            OfferCategory category = pickCategory(random, bias);
            ShopEntry entry = rollConcrete(category, context, random);
            if (entry != null && !usedKeys.contains(entry.dedupKey)) return entry;
        }
        // Fallback so a slot is never wasted: supplies can (almost) always produce something new.
        return rollSupply(context, random, usedKeys);
    }

    private ShopEntry rollSupply(ShopContext context, Random random, Set<String> usedKeys) {
        for (int retry = 0; retry < SUPPLY_RETRIES; retry++) {
            ShopEntry entry = random.nextBoolean()
                    ? source.rollAmmoOffer(context, random)
                    : source.rollMedkitOffer(context, random);
            if (entry != null && !usedKeys.contains(entry.dedupKey)) return entry;
        }
        return null;
    }

    private ShopEntry rollUpgrade(ShopContext context, Random random, Set<String> usedKeys) {
        for (int retry = 0; retry < UPGRADE_RETRIES; retry++) {
            ShopEntry entry = random.nextBoolean()
                    ? source.rollTierUpgradeOffer(context, random)
                    : source.rollLevelUpOffer(context, random);
            if (entry != null && !usedKeys.contains(entry.dedupKey)) return entry;
        }
        return null;
    }

    private ShopEntry rollConcrete(OfferCategory category, ShopContext context, Random random) {
        switch (category) {
            case WEAPON_TIER_UPGRADE: return source.rollTierUpgradeOffer(context, random);
            case WEAPON_LEVEL_UP:     return source.rollLevelUpOffer(context, random);
            case PLAYER_ABILITY:      return source.rollAbilityOffer(context, random);
            case AMMO:                return source.rollAmmoOffer(context, random);
            case MEDKIT:              return source.rollMedkitOffer(context, random);
            default:                  return null;
        }
    }

    /** Weighted category pick, with an optional bias multiplier on the upgrade or supply group. */
    private OfferCategory pickCategory(Random random, RollBias bias) {
        float multiplier = GameBalance.SHOP_BIAS_WEIGHT_MULTIPLIER;
        float weightTier    = GameBalance.SHOP_CAT_WEIGHT_TIER_UPGRADE;
        float weightLevelUp = GameBalance.SHOP_CAT_WEIGHT_WEAPON_LEVELUP;
        float weightAbility = GameBalance.SHOP_CAT_WEIGHT_ABILITY;
        float weightAmmo    = GameBalance.SHOP_CAT_WEIGHT_AMMO;
        float weightMedkit  = GameBalance.SHOP_CAT_WEIGHT_MEDKIT;
        if (bias == RollBias.UPGRADE) {
            weightTier *= multiplier; weightLevelUp *= multiplier; weightAbility *= multiplier;
        } else if (bias == RollBias.SUPPLY) {
            weightAmmo *= multiplier; weightMedkit *= multiplier;
        }
        float total = weightTier + weightLevelUp + weightAbility + weightAmmo + weightMedkit;
        float pick  = random.nextFloat() * total;
        if ((pick -= weightTier)    < 0) return OfferCategory.WEAPON_TIER_UPGRADE;
        if ((pick -= weightLevelUp) < 0) return OfferCategory.WEAPON_LEVEL_UP;
        if ((pick -= weightAbility) < 0) return OfferCategory.PLAYER_ABILITY;
        if ((pick -= weightAmmo)    < 0) return OfferCategory.AMMO;
        return OfferCategory.MEDKIT;
    }

    private static void addEntry(List<ShopEntry> entries, Set<String> usedKeys, ShopEntry entry) {
        entries.add(entry);
        usedKeys.add(entry.dedupKey);
    }
}
