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
 *   <li><b>Real rarity (balance-overhaul order 5, B5)</b> — a tier is a GUARANTEED ability shape: exactly
 *       {@link WeaponTier#abilityCount} abilities, {@link WeaponTier#majorCount} of them from the MAJOR list,
 *       plus the signature on a LEGENDARY. Nothing is dropped for not fitting: every surviving ability is
 *       tuned so any draw of the tier's shape prices inside the tier's band
 *       ({@code TIER_ABILITY_PP_BUDGET_* × (1 ± TIER_ABILITY_PP_TOLERANCE)}). A roll outside the band is a
 *       magnitude error that BalanceSchema R-ABILITY's roll sweep catches — never a silent short roll.</li>
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
     * A PLANNED weapon drop (balance-overhaul order 2, S9): the SupplyPlanner already decided this drop's
     * level offset from the floor depth and, for an ELITE reward, a tier floor above the region minimum,
     * so neither is rolled here. Only the tier inside the allowed band and the abilities are rolled.
     *
     * @param weapon      the weapon used for ability eligibility (never modified)
     * @param floorDepth  current dungeon floor (1-based)
     * @param levelOffset the planned offset: level = floorDepth + levelOffset, clamped to [1, MAX]
     * @param tierBonus   steps above the region's minimum drop tier the roll may not go below, or a
     *                    negative value for the plain region band
     */
    public WeaponRoll rollPlannedToSnapshot(Weapon weapon, int floorDepth, int levelOffset, int tierBonus) {
        int        plannedLevel = Math.max(1, Math.min(WeaponConstants.MAX_WEAPON_LEVEL, floorDepth + levelOffset));
        WeaponTier minimumTier  = regionMinTier(floorDepth);
        WeaponTier maximumTier  = regionMaxTier(floorDepth);
        if (tierBonus > 0) {
            minimumTier = tierByOrdinal(minimumTier.ordinal() + tierBonus);
            if (maximumTier.ordinal() < minimumTier.ordinal()) maximumTier = minimumTier;
        }
        WeaponTier        rolledTier      = rollTierClamped(floorDepth, minimumTier, maximumTier);
        if (rolledTier.ordinal() < minimumTier.ordinal()) rolledTier = minimumTier;
        AbilityInstance[] rolledAbilities = rollAbilities(weapon, rolledTier, plannedLevel);
        return new WeaponRoll(plannedLevel, rolledTier, rolledAbilities);
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

    /** R6: floor depth plus a weighted offset (WEAPON_LEVEL_ROLL_OFFSETS / _WEIGHTS), clamped to [1, MAX]. */
    private int rollLevel(int floorDepth) {
        int[] offsets = BalanceConfig.WEAPON_LEVEL_ROLL_OFFSETS;
        int[] weights = BalanceConfig.WEAPON_LEVEL_ROLL_WEIGHTS;
        int totalWeight = 0;
        for (int weightIndex = 0; weightIndex < weights.length; weightIndex++) {
            totalWeight += weights[weightIndex];
        }
        int pick = rng.nextInt(Math.max(1, totalWeight));
        int offset = 0;
        for (int weightIndex = 0; weightIndex < weights.length; weightIndex++) {
            pick -= weights[weightIndex];
            if (pick < 0) {
                offset = offsets[weightIndex];
                break;
            }
        }
        int level = floorDepth + offset;
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

    /** Rolls a weapon's abilities in its tier's guaranteed shape (see {@link #rollAbilitySet}). */
    private AbilityInstance[] rollAbilities(Weapon weapon, WeaponTier tier, int level) {
        return rollAbilitySet(weapon.isMelee(), tier, level);
    }

    /**
     * Rolls a tier's abilities in its GUARANTEED shape (B5) for a weapon of the given melee-ness, without a
     * Weapon instance: the signature first on a LEGENDARY (it defines the weapon), then
     * {@link WeaponTier#majorCount} distinct MAJORs, then {@link WeaponTier#standardCount()} distinct
     * standards — exactly, every time. Public so BalanceSchema, BalanceReport and the audit can exercise the
     * real roll headlessly. Uses this roller's RNG, so the sequence stays reproducible per run seed.
     */
    public AbilityInstance[] rollAbilitySet(boolean isMeleeWeapon, WeaponTier tier, int level) {
        List<AbilityInstance> result = new ArrayList<>(tier.totalAbilityCount());
        if (tier.rollsSignature) {
            drawDistinct(WeaponAbility.Rank.SIGNATURE, isMeleeWeapon, 1, level, result);
        }
        drawDistinct(WeaponAbility.Rank.MAJOR,    isMeleeWeapon, tier.majorCount,      level, result);
        drawDistinct(WeaponAbility.Rank.STANDARD, isMeleeWeapon, tier.standardCount(), level, result);
        return result.toArray(new AbilityInstance[0]);
    }

    /**
     * Appends {@code count} abilities of {@code rank}, eligible for the weapon and not already in
     * {@code into}, drawn without replacement from a seeded shuffle. A pool smaller than the count is a
     * catalogue error (R-ABILITY's sweep reports the short roll); the draw never pads with another rank.
     */
    private void drawDistinct(WeaponAbility.Rank rank, boolean isMeleeWeapon, int count, int level,
                              List<AbilityInstance> into) {
        if (count <= 0) return;
        List<WeaponAbility> pool = buildPool(rank, isMeleeWeapon);
        for (AbilityInstance owned : into) pool.remove(owned.ability);
        Collections.shuffle(pool, rng);
        int drawn = Math.min(count, pool.size());
        for (int drawIndex = 0; drawIndex < drawn; drawIndex++) {
            into.add(buildAbilityInstance(pool.get(drawIndex), level));
        }
    }

    /** Whether a roll's priced total lies inside its tier's band (R-ABILITY / BalanceReport helper). */
    public static boolean isWithinTierBand(WeaponTier tier, float totalPowerPoints) {
        float tolerance = 1e-3f;
        return totalPowerPoints >= tier.abilityPowerPointFloor() - tolerance
                && totalPowerPoints <= tier.abilityPowerPointCeiling() + tolerance;
    }

    /** The nominal ability-PP budget for a tier (BalanceConfig SECTION 24, via {@link WeaponTier}). */
    public static float tierAbilityPowerPointBudget(WeaponTier tier) {
        return tier.abilityPowerPointBudget;
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

    /** Eligible abilities of one rank for a weapon of the given melee-ness (spawn roll + shop tier upgrade). */
    private static List<WeaponAbility> buildPool(WeaponAbility.Rank rank, boolean isMeleeWeapon) {
        List<WeaponAbility> pool = new ArrayList<>();
        for (WeaponAbility ability : WeaponAbility.values()) {
            if (ability.rank == rank && ability.eligibleFor(isMeleeWeapon)) {
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
     * Shop tier upgrade: raises the weapon's tier to {@code newTier} and completes the destination tier's
     * guaranteed SHAPE (B5) — the signature if it is a LEGENDARY without one, then the MAJORs and standards
     * the weapon is still short of — sampled without replacement, never duplicating an ability already on the
     * weapon, magnitudes scaled to the weapon's CURRENT level. Existing abilities carry over unchanged (tier
     * adds tricks; level scales numbers), so an upgraded weapon carries exactly what a fresh roll of the new
     * tier would, and prices inside the same band.
     *
     * @return false with no change if the weapon is already Legendary.
     */
    public boolean applyTierUpgrade(Weapon weapon, WeaponTier newTier) {
        if (!weapon.canUpgradeTier()) return false;
        int currentLevel = weapon.getWeaponLevel();

        List<AbilityInstance> combined = new ArrayList<>(newTier.totalAbilityCount());
        int signatureCount = 0;
        int majorCount     = 0;
        int standardCount  = 0;
        for (int index = 0; index < weapon.getAbilityCount(); index++) {
            AbilityInstance instance = weapon.getAbility(index);
            combined.add(instance);
            if (instance.ability.legendaryOnly)  signatureCount++;
            else if (instance.ability.isMajor()) majorCount++;
            else                                 standardCount++;
        }

        if (newTier.rollsSignature) {
            drawDistinct(WeaponAbility.Rank.SIGNATURE, weapon.isMelee(), 1 - signatureCount, currentLevel, combined);
        }
        drawDistinct(WeaponAbility.Rank.MAJOR,    weapon.isMelee(), newTier.majorCount - majorCount,
                     currentLevel, combined);
        drawDistinct(WeaponAbility.Rank.STANDARD, weapon.isMelee(), newTier.standardCount() - standardCount,
                     currentLevel, combined);

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
                // Fixed round count (balance-overhaul order 5): the burst no longer grows 2 -> 5 with level.
                countVal  = GameBalance.BURST_FIRE_ROUND_COUNT;
                magnitude = countVal;
                break;
            case CRITICAL_STRIKE:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.CRIT_CHANCE_BASE,
                        GameBalance.CRIT_CHANCE_PER_LEVEL, weaponLevel, GameBalance.CRIT_CHANCE_CAP);
                break;
            case ARMOR_PIERCE:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.ARMOR_PIERCE_BASE,
                        GameBalance.ARMOR_PIERCE_PER_LEVEL, weaponLevel, GameBalance.ARMOR_PIERCE_CAP);
                // The merged OVERPENETRATION: extra enemies each shot pierces (flat, not level-scaled).
                countVal  = GameBalance.ARMOR_PIERCE_EXTRA_TARGETS;
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
            case SCAVENGER_ROUNDS:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.SCAVENGER_CHANCE_BASE,
                        GameBalance.SCAVENGER_CHANCE_PER_LEVEL, weaponLevel, GameBalance.SCAVENGER_CHANCE_CAP);
                // Rounds refunded per proc — the price reads it (chance × rounds).
                countVal  = GameBalance.SCAVENGER_REFUND_ROUNDS;
                break;
            case POINT_BLANK:
                magnitude = GameMath.abilityMagnitudeScaled(GameBalance.POINT_BLANK_BONUS_BASE,
                        GameBalance.POINT_BLANK_BONUS_PER_LEVEL, weaponLevel,
                        GameBalance.POINT_BLANK_BONUS_CAP);
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
