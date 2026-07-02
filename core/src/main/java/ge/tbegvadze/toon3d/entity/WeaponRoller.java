package ge.tbegvadze.toon3d.entity;

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
        WeaponTier        rolledTier      = rollTierClamped(floorDepth, null, null);
        AbilityInstance[] rolledAbilities = rollAbilities(weapon, rolledTier, rolledLevel);
        weapon.configureRoll(rolledLevel, rolledTier, rolledAbilities);
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
        WeaponTier        rolledTier      = rollTierClamped(floorDepth, null, null);
        AbilityInstance[] rolledAbilities = rollAbilities(weapon, rolledTier, rolledLevel);
        return new WeaponRoll(rolledLevel, rolledTier, rolledAbilities);
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

    private AbilityInstance[] rollAbilities(Weapon weapon, WeaponTier tier, int level) {
        if (tier.abilitySlots == 0) return new AbilityInstance[0];

        List<WeaponAbility> standardPool = buildStandardPool(weapon);

        int standardCount = tier.standardSlotCount();
        List<AbilityInstance> result = new ArrayList<>();

        Collections.shuffle(standardPool, rng);
        for (int slotIndex = 0; slotIndex < Math.min(standardCount, standardPool.size()); slotIndex++) {
            result.add(buildInstance(standardPool.get(slotIndex), level));
        }

        if (tier.isLegendary()) {
            List<WeaponAbility> legendaryPool = buildLegendaryPool(weapon);
            if (!legendaryPool.isEmpty()) {
                WeaponAbility signature = legendaryPool.get(rng.nextInt(legendaryPool.size()));
                result.add(buildInstance(signature, level));
            }
        }

        return result.toArray(new AbilityInstance[0]);
    }

    /** Eligible non-signature abilities for this weapon (shared by spawn roll + shop tier upgrade). */
    private List<WeaponAbility> buildStandardPool(Weapon weapon) {
        List<WeaponAbility> pool = new ArrayList<>();
        for (WeaponAbility ability : WeaponAbility.values()) {
            if (!ability.legendaryOnly && ability.eligibleFor(weapon.isMelee())) {
                pool.add(ability);
            }
        }
        return pool;
    }

    /** Eligible signature (legendary-only) abilities for this weapon. */
    private List<WeaponAbility> buildLegendaryPool(Weapon weapon) {
        List<WeaponAbility> pool = new ArrayList<>();
        for (WeaponAbility ability : WeaponAbility.values()) {
            if (ability.legendaryOnly && ability.eligibleFor(weapon.isMelee())) {
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
     * Reuses {@link #buildInstance} — the same magnitude math the spawn roll uses.
     *
     * @return false with no change if the weapon is already at max level.
     */
    public boolean applyLevelUp(Weapon weapon) {
        if (!weapon.canLevelUp()) return false;
        int newLevel = weapon.getWeaponLevel() + 1;
        int abilityCount = weapon.getAbilityCount();
        AbilityInstance[] rescaled = new AbilityInstance[abilityCount];
        for (int index = 0; index < abilityCount; index++) {
            rescaled[index] = buildInstance(weapon.getAbility(index).ability, newLevel);
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

        // Fill the standard slots the destination tier grants beyond what the weapon holds.
        int newStandardNeeded = Math.max(0, newTier.standardSlotCount() - currentStandardCount);
        if (newStandardNeeded > 0) {
            List<WeaponAbility> pool = buildStandardPool(weapon);
            pool.removeAll(alreadyOwned);
            Collections.shuffle(pool, rng);
            for (int slotIndex = 0; slotIndex < Math.min(newStandardNeeded, pool.size()); slotIndex++) {
                combined.add(buildInstance(pool.get(slotIndex), currentLevel));
            }
        }

        // Into Legendary: add the signature (legendary-only) ability if the weapon has none.
        if (newTier.isLegendary() && !hasSignature) {
            List<WeaponAbility> legendaryPool = buildLegendaryPool(weapon);
            if (!legendaryPool.isEmpty()) {
                WeaponAbility signature = legendaryPool.get(rng.nextInt(legendaryPool.size()));
                combined.add(buildInstance(signature, currentLevel));
            }
        }

        weapon.applyShopTierUpgrade(newTier, combined.toArray(new AbilityInstance[0]));
        return true;
    }

    private AbilityInstance buildInstance(WeaponAbility ability, int weaponLevel) {
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
