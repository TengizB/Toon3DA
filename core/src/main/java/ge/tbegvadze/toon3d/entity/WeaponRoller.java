package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.GameBalance;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.WeaponConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Rolls weapon level, tier, and abilities at spawn time.
 * Constructed once per run from runSeed for reproducibility:
 * same seed → same sequence of rolls across all weapons in the run.
 *
 * <p><b>The gear curve (new-game-balancr order 2).</b> Two order-2 rules shape a rolled weapon:
 * <ul>
 *   <li><b>Depth-gated tier bands</b> — a dropped weapon's tier is clamped to its region's band
 *       ({@code BalanceConfig.WEAPON_DROP_TIER_*_BY_REGION}): region 1 rolls COMMON..UNCOMMON,
 *       region 2 UNCOMMON..RARE, and so on, so the arsenal the game supplies keeps pace with the
 *       expected gear curve.</li>
 *   <li><b>Priced abilities</b> — tier is a PP BUDGET, not a slot count. Abilities are rolled
 *       greedily and each is charged its {@link GameMath#abilityPowerPoints} price; the roll stops
 *       once the next ability would exceed the tier's ability-PP ceiling
 *       ({@code TIER_ABILITY_PP_BUDGET_* × (1 + TIER_ABILITY_PP_TOLERANCE)}). Rarity buys abilities,
 *       never raw power. BalanceSchema R-ABILITY asserts every roll fits its budget.</li>
 * </ul>
 */
public class WeaponRoller {

    private final Random rng;

    public WeaponRoller(long runSeed) {
        this.rng = new Random(runSeed);
    }

    /**
     * Configures a weapon with a rolled level, tier, and abilities appropriate
     * for the given floor depth.
     *
     * @param weapon     the weapon instance to configure
     * @param floorDepth current dungeon floor (1-based); drives level and tier weights
     */
    public void rollWeapon(Weapon weapon, int floorDepth) {
        int               rolledLevel     = rollLevel(floorDepth);
        WeaponTier        rolledTier      = rollTierClamped(floorDepth,
                                                regionMinTier(floorDepth), regionMaxTier(floorDepth));
        AbilityInstance[] rolledAbilities = rollAbilities(weapon, rolledTier, rolledLevel);
        weapon.configureRoll(rolledLevel, rolledTier, rolledAbilities);
    }

    /**
     * THE PITY RULE (new-game-balancr order 2): rolls a GUARANTEED in-band upgrade for a region — a
     * weapon whose tier is forced to at least this region's band minimum (and no higher than its max),
     * so a region can never end with the player starved of the gear its curve expects. The floor
     * generator calls this for the pity spawn it force-places when a region is about to end with zero
     * upgrades seen (RunStats tracks the count).
     *
     * @param weapon     the weapon instance to configure
     * @param floorDepth current dungeon floor (1-based); selects the region band
     */
    public void rollGuaranteedUpgrade(Weapon weapon, int floorDepth) {
        WeaponTier minTier            = regionMinTier(floorDepth);
        int               rolledLevel = rollLevel(floorDepth);
        WeaponTier        rolledTier  = rollTierClamped(floorDepth, minTier, regionMaxTier(floorDepth));
        // Never below the region's guaranteed floor: a pity drop must actually BE an upgrade.
        if (rolledTier.ordinal() < minTier.ordinal()) rolledTier = minTier;
        AbilityInstance[] rolledAbilities = rollAbilities(weapon, rolledTier, rolledLevel);
        weapon.configureRoll(rolledLevel, rolledTier, rolledAbilities);
    }

    /**
     * Snapshot form of {@link #rollGuaranteedUpgrade} for the pity force-spawn (a floor's weapons are
     * stored as {@link WeaponRoll} snapshots on ground items, not applied to a live weapon).
     */
    public WeaponRoll rollGuaranteedUpgradeToSnapshot(Weapon weapon, int floorDepth) {
        WeaponTier minTier            = regionMinTier(floorDepth);
        int               rolledLevel = rollLevel(floorDepth);
        WeaponTier        rolledTier  = rollTierClamped(floorDepth, minTier, regionMaxTier(floorDepth));
        if (rolledTier.ordinal() < minTier.ordinal()) rolledTier = minTier;
        AbilityInstance[] rolledAbilities = rollAbilities(weapon, rolledTier, rolledLevel);
        return new WeaponRoll(rolledLevel, rolledTier, rolledAbilities);
    }

    /** The lowest tier that counts as a real weapon UPGRADE (UNCOMMON or better) — the pity threshold. */
    public static WeaponTier upgradeTierThreshold() {
        return WeaponTier.UNCOMMON;
    }

    /**
     * Configures a weapon as a fixed Common Level-1 run-start weapon (no randomness).
     */
    public void configureRunStart(Weapon weapon) {
        weapon.configureRoll(GameBalance.RUN_START_WEAPON_LEVEL,
                             GameBalance.RUN_START_WEAPON_TIER,
                             new AbilityInstance[0]);
    }

    /**
     * Rolls a weapon with tier clamped to [minTier, maxTier].
     * Weights outside the range are zeroed before sampling (no bias).
     * Used for start-room weapon offers where a minimum quality floor is enforced.
     *
     * @param weapon     the weapon instance to configure
     * @param floorDepth current dungeon floor (1-based)
     * @param minTier    lowest tier allowed (inclusive)
     * @param maxTier    highest tier allowed (inclusive)
     */
    public void rollWeaponWithMinTier(Weapon weapon, int floorDepth,
                                      WeaponTier minTier, WeaponTier maxTier) {
        int               rolledLevel     = rollLevel(floorDepth);
        WeaponTier        rolledTier      = rollTierClamped(floorDepth, minTier, maxTier);
        AbilityInstance[] rolledAbilities = rollAbilities(weapon, rolledTier, rolledLevel);
        weapon.configureRoll(rolledLevel, rolledTier, rolledAbilities);
    }

    /**
     * Generates a WeaponRoll snapshot without modifying the weapon instance.
     * Advances the RNG state identically to rollWeapon() so the sequence stays consistent.
     *
     * @param weapon     weapon used for ability eligibility (isMelee(), etc.)
     * @param floorDepth current dungeon floor (1-based)
     * @return a new WeaponRoll ready to be stored on a GroundItem
     */
    public WeaponRoll rollToSnapshot(Weapon weapon, int floorDepth) {
        int               rolledLevel     = rollLevel(floorDepth);
        WeaponTier        rolledTier      = rollTierClamped(floorDepth,
                                                regionMinTier(floorDepth), regionMaxTier(floorDepth));
        AbilityInstance[] rolledAbilities = rollAbilities(weapon, rolledTier, rolledLevel);
        return new WeaponRoll(rolledLevel, rolledTier, rolledAbilities);
    }

    // ── Depth-gated loot tier bands (new-game-balancr order 2) ──────────────

    /**
     * Region index for a floor depth: {@code floor((depth-1) / REGION_BAND_SIZE)}, clamped to a valid
     * index into the per-region tier-band tables (deeper regions reuse the last, deepest band entry).
     */
    private static int regionBandIndex(int floorDepth, int tableLength) {
        int bandSize = Math.max(1, BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE);
        int region   = Math.max(0, floorDepth - 1) / bandSize;
        return Math.min(region, tableLength - 1);
    }

    /** Lowest tier a dropped weapon may roll at this depth (from WEAPON_DROP_TIER_MIN_BY_REGION). */
    private static WeaponTier regionMinTier(int floorDepth) {
        int[] table = BalanceConfig.WEAPON_DROP_TIER_MIN_BY_REGION;
        return tierByOrdinal(table[regionBandIndex(floorDepth, table.length)]);
    }

    /** Highest tier a dropped weapon may roll at this depth (from WEAPON_DROP_TIER_MAX_BY_REGION). */
    private static WeaponTier regionMaxTier(int floorDepth) {
        int[] table = BalanceConfig.WEAPON_DROP_TIER_MAX_BY_REGION;
        return tierByOrdinal(table[regionBandIndex(floorDepth, table.length)]);
    }

    /** Maps a band's tier-ordinal to the nearest existing WeaponTier (LEGENDARY has ordinal 4). */
    private static WeaponTier tierByOrdinal(int tierOrdinal) {
        WeaponTier[] tiers = WeaponTier.values();
        int clamped = Math.max(0, Math.min(tiers.length - 1, tierOrdinal));
        return tiers[clamped];
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private int rollLevel(int floorDepth) {
        int offset = rng.nextInt(3) - 1;  // -1, 0, or +1 uniformly
        int level  = floorDepth + offset;
        return Math.max(1, Math.min(WeaponConstants.MAX_WEAPON_LEVEL, level));
    }

    private WeaponTier rollTierClamped(int floorDepth, WeaponTier minTier, WeaponTier maxTier) {
        WeaponTier[] tiers = WeaponTier.values();
        float[] weights = new float[tiers.length];
        float totalWeight = 0f;

        for (int tierIndex = 0; tierIndex < tiers.length; tierIndex++) {
            WeaponTier tier = tiers[tierIndex];
            if (minTier != null && tier.ordinal() < minTier.ordinal()) {
                weights[tierIndex] = 0f;
                continue;
            }
            if (maxTier != null && tier.ordinal() > maxTier.ordinal()) {
                weights[tierIndex] = 0f;
                continue;
            }
            float baseWeight = getTierBaseWeight(tier);
            float drift      = getTierDrift(tier);
            float minFloor   = getTierMinFloor(tier);
            float cap        = getTierCap(tier);
            float computed   = baseWeight + drift * (floorDepth - 1);
            computed = Math.max(minFloor, computed);
            if (cap > 0f) computed = Math.min(cap, computed);
            weights[tierIndex] = Math.max(0f, computed);
            totalWeight += weights[tierIndex];
        }

        if (totalWeight <= 0f) {
            return minTier != null ? minTier : WeaponTier.COMMON;
        }

        float roll       = rng.nextFloat() * totalWeight;
        float cumulative = 0f;
        for (int tierIndex = 0; tierIndex < tiers.length; tierIndex++) {
            cumulative += weights[tierIndex];
            if (roll < cumulative) return tiers[tierIndex];
        }
        return minTier != null ? minTier : WeaponTier.COMMON;
    }

    private float getTierBaseWeight(WeaponTier tier) {
        switch (tier) {
            case COMMON:    return WeaponConstants.TIER_WEIGHT_COMMON_BASE;
            case UNCOMMON:  return WeaponConstants.TIER_WEIGHT_UNCOMMON_BASE;
            case RARE:      return WeaponConstants.TIER_WEIGHT_RARE_BASE;
            case EPIC:      return WeaponConstants.TIER_WEIGHT_EPIC_BASE;
            case LEGENDARY: return WeaponConstants.TIER_WEIGHT_LEGENDARY_BASE;
            default:        return 0f;
        }
    }

    private float getTierDrift(WeaponTier tier) {
        switch (tier) {
            case COMMON:    return WeaponConstants.TIER_WEIGHT_COMMON_DRIFT;
            case UNCOMMON:  return WeaponConstants.TIER_WEIGHT_UNCOMMON_DRIFT;
            case RARE:      return WeaponConstants.TIER_WEIGHT_RARE_DRIFT;
            case EPIC:      return WeaponConstants.TIER_WEIGHT_EPIC_DRIFT;
            case LEGENDARY: return WeaponConstants.TIER_WEIGHT_LEGENDARY_DRIFT;
            default:        return 0f;
        }
    }

    private float getTierMinFloor(WeaponTier tier) {
        if (tier == WeaponTier.COMMON) return WeaponConstants.TIER_WEIGHT_COMMON_MINIMUM;
        return 0f;
    }

    private float getTierCap(WeaponTier tier) {
        if (tier == WeaponTier.LEGENDARY) return WeaponConstants.TIER_WEIGHT_LEGENDARY_CAP;
        return 0f;
    }

    /**
     * Rolls a weapon's abilities against its tier's PP BUDGET (new-game-balancr order 2). Tier is a
     * spend limit, not a slot count: abilities are drawn greedily and each is charged its
     * {@link GameMath#abilityPowerPoints} price; an ability is added only while the running total stays
     * within the tier's ability-PP ceiling ({@code budget × (1 + tolerance)}), and the tier's slot
     * geometry ({@link WeaponTier#standardSlotCount()}) still caps the count from above. A LEGENDARY
     * reserves and places its signature FIRST (that is its identity), then fills standard slots with
     * the remaining budget. The result therefore always satisfies R-ABILITY by construction.
     */
    private AbilityInstance[] rollAbilities(Weapon weapon, WeaponTier tier, int level) {
        return rollAbilitySet(weapon.isMelee(), tier, level);
    }

    /**
     * Rolls a tier's abilities against its PP budget for a weapon of the given melee-ness, without a
     * Weapon instance. Public so BalanceReport and the audit can exercise the budgeted roll headlessly.
     * Uses this roller's RNG, so the sequence stays reproducible per run seed.
     */
    public AbilityInstance[] rollAbilitySet(boolean isMeleeWeapon, WeaponTier tier, int level) {
        float ceiling = tierAbilityPowerPointCeiling(tier);
        if (ceiling <= 0f) return new AbilityInstance[0];

        List<AbilityInstance> result = new ArrayList<>();
        float spentPowerPoints = 0f;

        // Legendary signature first: it defines the legendary, so it is placed before standard fill.
        if (tier.isLegendary()) {
            List<WeaponAbility> legendaryPool = buildLegendaryPool(isMeleeWeapon);
            if (!legendaryPool.isEmpty()) {
                WeaponAbility signature = legendaryPool.get(rng.nextInt(legendaryPool.size()));
                AbilityInstance signatureInstance = buildAbilityInstance(signature, level);
                float signatureCost = GameMath.abilityPowerPoints(signature,
                        signatureInstance.magnitude, signatureInstance.countValue);
                if (spentPowerPoints + signatureCost <= ceiling) {
                    result.add(signatureInstance);
                    spentPowerPoints += signatureCost;
                }
            }
        }

        // Standard fill: draw the shuffled pool and add each ability whose price still fits the budget.
        List<WeaponAbility> standardPool = buildStandardPool(isMeleeWeapon);
        Collections.shuffle(standardPool, rng);
        int standardSlotCap = tier.standardSlotCount();
        int standardAdded   = 0;
        for (WeaponAbility ability : standardPool) {
            if (standardAdded >= standardSlotCap) break;
            AbilityInstance instance = buildAbilityInstance(ability, level);
            float cost = GameMath.abilityPowerPoints(ability, instance.magnitude, instance.countValue);
            if (spentPowerPoints + cost <= ceiling) {
                result.add(instance);
                spentPowerPoints += cost;
                standardAdded++;
            }
            // else: too expensive for the remaining budget — skip it and try the next (cheaper) draw.
        }

        return result.toArray(new AbilityInstance[0]);
    }

    /** The ability-PP ceiling a tier may spend: its budget plus the allowed overspend tolerance. */
    private static float tierAbilityPowerPointCeiling(WeaponTier tier) {
        return tierAbilityPowerPointBudget(tier) * (1f + BalanceConfig.TIER_ABILITY_PP_TOLERANCE);
    }

    /** The nominal ability-PP budget for a tier (BalanceConfig SECTION 15). */
    public static float tierAbilityPowerPointBudget(WeaponTier tier) {
        switch (tier) {
            case COMMON:    return BalanceConfig.TIER_ABILITY_PP_BUDGET_COMMON;
            case UNCOMMON:  return BalanceConfig.TIER_ABILITY_PP_BUDGET_UNCOMMON;
            case RARE:      return BalanceConfig.TIER_ABILITY_PP_BUDGET_RARE;
            case EPIC:      return BalanceConfig.TIER_ABILITY_PP_BUDGET_EPIC;
            case LEGENDARY: return BalanceConfig.TIER_ABILITY_PP_BUDGET_LEGENDARY;
            default:        return 0f;
        }
    }

    /** Total ability PP carried by a set of rolled abilities (R-ABILITY / BalanceReport helper). */
    public static float totalAbilityPowerPoints(AbilityInstance[] abilities) {
        float total = 0f;
        if (abilities != null) {
            for (AbilityInstance instance : abilities) {
                total += GameMath.abilityPowerPoints(instance.ability, instance.magnitude, instance.countValue);
            }
        }
        return total;
    }

    /** Eligible non-signature abilities for this weapon (shared by spawn roll + shop tier upgrade). */
    private List<WeaponAbility> buildStandardPool(boolean isMeleeWeapon) {
        List<WeaponAbility> pool = new ArrayList<>();
        for (WeaponAbility ability : WeaponAbility.values()) {
            if (!ability.legendaryOnly && ability.eligibleFor(isMeleeWeapon)) {
                pool.add(ability);
            }
        }
        return pool;
    }

    /** Eligible signature (legendary-only) abilities for a weapon of the given melee-ness. */
    private List<WeaponAbility> buildLegendaryPool(boolean isMeleeWeapon) {
        List<WeaponAbility> pool = new ArrayList<>();
        for (WeaponAbility ability : WeaponAbility.values()) {
            if (ability.legendaryOnly && ability.eligibleFor(isMeleeWeapon)) {
                pool.add(ability);
            }
        }
        return pool;
    }

    // ── Shop upgrade mutators (shop_order_3) ─────────────────────────────────
    // The shop's WEAPON_LEVEL_UP / WEAPON_TIER_UPGRADE services delegate here so the
    // ability-roll + magnitude-scaling logic stays in one place (this class), shared with
    // the spawn roll. The shop package never reimplements any of this.

    /**
     * Shop level-up: raises the weapon's level by one and rescales its existing abilities'
     * magnitudes to the new level (ability magnitude scales with level), then installs them.
     * Reuses {@link #buildAbilityInstance} — the same magnitude math the spawn roll uses.
     *
     * @return false with no change if the weapon is already at max level.
     */
    public boolean applyLevelUp(Weapon weapon) {
        if (!weapon.canLevelUp()) return false;
        int newLevel = weapon.getWeaponLevel() + 1;
        int abilityCount = weapon.getAbilityCount();
        AbilityInstance[] rescaled = new AbilityInstance[abilityCount];
        for (int index = 0; index < abilityCount; index++) {
            rescaled[index] = buildAbilityInstance(weapon.getAbility(index).ability, newLevel);
        }
        weapon.applyShopLevelUp(rescaled);
        return true;
    }

    /**
     * Shop tier upgrade: raises the weapon's tier to {@code newTier} and grants the extra
     * ability slot(s) the destination tier adds — sampled without replacement from the eligible
     * pool, never duplicating an ability already on the weapon, magnitudes scaled to the weapon's
     * CURRENT level. Into LEGENDARY it also rolls the single signature ability when the weapon
     * lacks one. Existing abilities carry over unchanged (tier adds tricks; level scales numbers).
     *
     * @return false with no change if the weapon is already Legendary.
     */
    public boolean applyTierUpgrade(Weapon weapon, WeaponTier newTier) {
        if (!weapon.canUpgradeTier()) return false;
        int currentLevel = weapon.getWeaponLevel();

        List<AbilityInstance> combined     = new ArrayList<>();
        List<WeaponAbility>   alreadyOwned = new ArrayList<>();
        int currentStandardCount = 0;
        boolean hasSignature     = false;
        for (int index = 0; index < weapon.getAbilityCount(); index++) {
            AbilityInstance instance = weapon.getAbility(index);
            combined.add(instance);
            alreadyOwned.add(instance.ability);
            if (instance.ability.legendaryOnly) hasSignature = true;
            else                                currentStandardCount++;
        }

        // Existing abilities carry over; new ones must keep the weapon within the NEW tier's PP
        // ceiling (R-ABILITY holds for shop upgrades too, not just spawn rolls).
        float ceiling = tierAbilityPowerPointCeiling(newTier);
        float spentPowerPoints = totalAbilityPowerPoints(combined.toArray(new AbilityInstance[0]));

        // Into Legendary: add the signature (legendary-only) ability if the weapon has none.
        if (newTier.isLegendary() && !hasSignature) {
            List<WeaponAbility> legendaryPool = buildLegendaryPool(weapon.isMelee());
            if (!legendaryPool.isEmpty()) {
                WeaponAbility signature = legendaryPool.get(rng.nextInt(legendaryPool.size()));
                AbilityInstance signatureInstance = buildAbilityInstance(signature, currentLevel);
                float cost = GameMath.abilityPowerPoints(signature,
                        signatureInstance.magnitude, signatureInstance.countValue);
                if (spentPowerPoints + cost <= ceiling) {
                    combined.add(signatureInstance);
                    spentPowerPoints += cost;
                }
            }
        }

        // Fill the standard slots the destination tier grants beyond what the weapon holds, greedily,
        // adding only abilities whose price still fits the remaining budget.
        int newStandardNeeded = Math.max(0, newTier.standardSlotCount() - currentStandardCount);
        if (newStandardNeeded > 0) {
            List<WeaponAbility> pool = buildStandardPool(weapon.isMelee());
            pool.removeAll(alreadyOwned);
            Collections.shuffle(pool, rng);
            int added = 0;
            for (WeaponAbility ability : pool) {
                if (added >= newStandardNeeded) break;
                AbilityInstance instance = buildAbilityInstance(ability, currentLevel);
                float cost = GameMath.abilityPowerPoints(ability, instance.magnitude, instance.countValue);
                if (spentPowerPoints + cost <= ceiling) {
                    combined.add(instance);
                    spentPowerPoints += cost;
                    added++;
                }
            }
        }

        weapon.applyShopTierUpgrade(newTier, combined.toArray(new AbilityInstance[0]));
        return true;
    }

    /**
     * Builds a level-scaled {@link AbilityInstance} for an ability. Deterministic and rng-free — the
     * same (ability, level) always yields the same magnitude/count — so BalanceSchema, BalanceReport,
     * and the audit can price any ability without a WeaponRoller instance.
     */
    public static AbilityInstance buildAbilityInstance(WeaponAbility ability, int weaponLevel) {
        float magnitude = 0f;
        int   countVal  = 0;

        switch (ability) {
            case BURST_FIRE:
                countVal  = GameMath.abilityCountScaled(2, 2, weaponLevel, 2, 5);
                magnitude = countVal;
                break;
            case CRITICAL_STRIKE:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.CRIT_CHANCE_BASE,
                        GameBalance.CRIT_CHANCE_PER_LEVEL, weaponLevel, GameBalance.CRIT_CHANCE_CAP);
                break;
            case ARMOR_PIERCE:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.ARMOR_PIERCE_BASE,
                        GameBalance.ARMOR_PIERCE_PER_LEVEL, weaponLevel, GameBalance.ARMOR_PIERCE_CAP);
                break;
            case EXECUTIONER:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.EXECUTIONER_BONUS_BASE,
                        GameBalance.EXECUTIONER_BONUS_PER_LEVEL, weaponLevel,
                        GameBalance.EXECUTIONER_BONUS_CAP);
                break;
            case REND:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.REND_DAMAGE_PER_TURN_BASE,
                        GameBalance.REND_DAMAGE_PER_TURN_PER_LEVEL, weaponLevel,
                        GameBalance.REND_DAMAGE_PER_TURN_CAP);
                break;
            case OVERPENETRATION:
                countVal  = GameMath.abilityCountScaled(GameBalance.OVERPENETRATION_BASE_COUNT,
                        GameBalance.OVERPENETRATION_LEVELS_PER_STEP, weaponLevel,
                        GameBalance.OVERPENETRATION_BASE_COUNT, GameBalance.OVERPENETRATION_MAX_COUNT);
                magnitude = countVal;
                break;
            case STAGGER_ROUNDS:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.STAGGER_CHANCE_BASE,
                        GameBalance.STAGGER_CHANCE_PER_LEVEL, weaponLevel, GameBalance.STAGGER_CHANCE_CAP);
                break;
            case KINETIC_SLAM:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.KINETIC_SLAM_CHANCE_BASE,
                        GameBalance.KINETIC_SLAM_CHANCE_PER_LEVEL, weaponLevel,
                        GameBalance.KINETIC_SLAM_CHANCE_CAP);
                break;
            case CLEAVE:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.CLEAVE_FRACTION_BASE,
                        GameBalance.CLEAVE_FRACTION_PER_LEVEL, weaponLevel, GameBalance.CLEAVE_FRACTION_CAP);
                break;
            case INCENDIARY:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.INCENDIARY_BURN_PER_TURN_BASE,
                        GameBalance.INCENDIARY_BURN_PER_TURN_PER_LEVEL, weaponLevel,
                        GameBalance.INCENDIARY_BURN_PER_TURN_CAP);
                break;
            case LIFESTEAL:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.LIFESTEAL_BASE,
                        GameBalance.LIFESTEAL_PER_LEVEL, weaponLevel, GameBalance.LIFESTEAL_CAP);
                break;
            case HEMORRHAGE_HARVEST:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.HEMORRHAGE_HP_BASE,
                        GameBalance.HEMORRHAGE_HP_PER_LEVEL, weaponLevel, GameBalance.HEMORRHAGE_HP_CAP);
                break;
            case VAMPIRIC_CRIT:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.VAMPIRIC_CRIT_HP_BASE,
                        GameBalance.VAMPIRIC_CRIT_HP_PER_LEVEL, weaponLevel, GameBalance.VAMPIRIC_CRIT_HP_CAP);
                break;
            case ADRENAL_SURGE:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.ADRENAL_SURGE_CHANCE_BASE,
                        GameBalance.ADRENAL_SURGE_CHANCE_PER_LEVEL, weaponLevel,
                        GameBalance.ADRENAL_SURGE_CHANCE_CAP);
                break;
            case BULWARK_ROUNDS:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.BULWARK_ARMOR_BASE,
                        GameBalance.BULWARK_ARMOR_PER_LEVEL, weaponLevel, GameBalance.BULWARK_ARMOR_CAP);
                break;
            case SECOND_WIND:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.SECOND_WIND_BONUS_BASE,
                        GameBalance.SECOND_WIND_BONUS_PER_LEVEL, weaponLevel,
                        GameBalance.SECOND_WIND_BONUS_CAP);
                break;
            case SCAVENGER_ROUNDS:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.SCAVENGER_CHANCE_BASE,
                        GameBalance.SCAVENGER_CHANCE_PER_LEVEL, weaponLevel, GameBalance.SCAVENGER_CHANCE_CAP);
                break;
            case SALVAGE_STRIKE:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.SALVAGE_CHANCE_BASE,
                        GameBalance.SALVAGE_CHANCE_PER_LEVEL, weaponLevel, GameBalance.SALVAGE_CHANCE_CAP);
                break;
            case SCHOLARS_EDGE:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.SCHOLARS_XP_BONUS_BASE,
                        GameBalance.SCHOLARS_XP_BONUS_PER_LEVEL, weaponLevel,
                        GameBalance.SCHOLARS_XP_BONUS_CAP);
                break;
            case QUICK_HANDS:
                magnitude = 1f;
                break;
            case EXTENDED_MAG:
                countVal  = GameMath.abilityCountScaled(GameBalance.EXTENDED_MAG_BASE_COUNT,
                        GameBalance.EXTENDED_MAG_LEVELS_PER_STEP, weaponLevel,
                        GameBalance.EXTENDED_MAG_BASE_COUNT, GameBalance.EXTENDED_MAG_MAX_COUNT);
                magnitude = countVal;
                break;
            case FIELD_MEDIC_ROUNDS:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.FIELD_MEDIC_CHANCE_BASE,
                        GameBalance.FIELD_MEDIC_CHANCE_PER_LEVEL, weaponLevel,
                        GameBalance.FIELD_MEDIC_CHANCE_CAP);
                break;
            case CREDIT_FANG:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.CREDIT_FANG_BASE,
                        GameBalance.CREDIT_FANG_PER_LEVEL, weaponLevel, GameBalance.CREDIT_FANG_CAP);
                break;
            case POINT_BLANK:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.POINT_BLANK_BONUS_BASE,
                        GameBalance.POINT_BLANK_BONUS_PER_LEVEL, weaponLevel,
                        GameBalance.POINT_BLANK_BONUS_CAP);
                break;
            case MARKSMANS_PATIENCE:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.MARKSMAN_PER_TILE_BASE,
                        GameBalance.MARKSMAN_PER_TILE_PER_LEVEL, weaponLevel,
                        GameBalance.MARKSMAN_PER_TILE_CAP);
                break;
            case OPENING_SALVO:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.OPENING_SALVO_BONUS_BASE,
                        GameBalance.OPENING_SALVO_BONUS_PER_LEVEL, weaponLevel,
                        GameBalance.OPENING_SALVO_BONUS_CAP);
                break;
            case RHYTHM:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.RHYTHM_RAMP_PER_HIT_BASE,
                        GameBalance.RHYTHM_RAMP_PER_HIT_PER_LEVEL, weaponLevel,
                        GameBalance.RHYTHM_RAMP_PER_HIT_CAP);
                break;
            case STATIC_DISCHARGE:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.STATIC_SPLASH_BASE,
                        GameBalance.STATIC_SPLASH_PER_LEVEL, weaponLevel, GameBalance.STATIC_SPLASH_CAP);
                break;
            case RESONANT_ROUNDS:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.RESONANT_PCT_BASE,
                        GameBalance.RESONANT_PCT_PER_LEVEL, weaponLevel, GameBalance.RESONANT_PCT_CAP);
                break;
            case SOULFORGE:
            case JUDGMENT:
            case HELLFIRE_NOVA:
            case BERSERKERS_OATH:
                magnitude = 1f;
                break;
            default:
                magnitude = 1f;
                break;
        }

        return new AbilityInstance(ability, magnitude, countVal);
    }
}
