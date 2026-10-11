package ge.tbegvadze.toon3d.progression;

import ge.tbegvadze.toon3d.entity.DamageClass;

/**
 * THE MODIFIER KINDS (balance-overhaul order 5, rule B2) — what a {@link Perk} rank or a {@link Keystone}
 * does to the {@link BuildStats} aggregate. Each constant owns its {@link #apply} body (the {@code EnemyType}
 * idiom), so the perk catalog is pure data and nothing anywhere switches on a modifier kind or a perk.
 *
 * <p>Two families, by the EDGE-CASES stacking rule: the perk bonuses ADD their magnitude to a field; the
 * keystone costs (and the keystone gains that are multipliers) MULTIPLY a field, so they apply after the
 * additive bonuses no matter the pick order. Boolean kinds ignore the magnitude.
 */
public enum PerkModifier {

    // ---- PERK BONUSES (additive) --------------------------------------------------------------
    SPREAD_DAMAGE         { @Override void apply(BuildStats stats, float magnitude) { addClassDamage(stats, DamageClass.SPREAD, magnitude); } },
    EXPLOSIVE_DAMAGE      { @Override void apply(BuildStats stats, float magnitude) { addClassDamage(stats, DamageClass.EXPLOSIVE, magnitude); } },
    ENERGY_DAMAGE         { @Override void apply(BuildStats stats, float magnitude) { addClassDamage(stats, DamageClass.ENERGY, magnitude); } },
    FIRE_DAMAGE           { @Override void apply(BuildStats stats, float magnitude) { addClassDamage(stats, DamageClass.FIRE, magnitude); } },
    /** Melee: both melee classes (BLADE and BLUNT). */
    MELEE_DAMAGE          { @Override void apply(BuildStats stats, float magnitude) {
                                addClassDamage(stats, DamageClass.BLADE, magnitude);
                                addClassDamage(stats, DamageClass.BLUNT, magnitude); } },
    SPREAD_POINT_BLANK_DAMAGE { @Override void apply(BuildStats stats, float magnitude) { stats.spreadPointBlankDamageBonus += magnitude; } },
    BALLISTIC_RAIL_FALLOFF_REDUCTION { @Override void apply(BuildStats stats, float magnitude) {
                                stats.ballisticRailFalloffReduction = Math.min(1f, stats.ballisticRailFalloffReduction + magnitude); } },
    FULL_HEALTH_TARGET_DAMAGE { @Override void apply(BuildStats stats, float magnitude) { stats.fullHealthTargetDamageBonus += magnitude; } },
    RANGE_BONUS           { @Override void apply(BuildStats stats, float magnitude) { stats.rangeBonusTiles += Math.round(magnitude); } },
    LONG_RANGE_DAMAGE     { @Override void apply(BuildStats stats, float magnitude) { stats.longRangeDamageBonus += magnitude; } },
    KNOCKBACK_BONUS       { @Override void apply(BuildStats stats, float magnitude) { stats.knockbackBonusTiles += Math.round(magnitude); } },
    STAGGER_BONUS         { @Override void apply(BuildStats stats, float magnitude) { stats.staggerBonusTiles += Math.round(magnitude); } },
    SPREAD_KILL_SHELL_REFUND { @Override void apply(BuildStats stats, float magnitude) {
                                stats.spreadKillShellRefundChance = Math.min(1f, stats.spreadKillShellRefundChance + magnitude); } },
    BURN_MAX_STACK        { @Override void apply(BuildStats stats, float magnitude) { stats.burnMaxStackBonus += Math.round(magnitude); } },
    WILDFIRE              { @Override void apply(BuildStats stats, float magnitude) { stats.wildfire = true; } },
    FIRE_TILE_IMMUNE      { @Override void apply(BuildStats stats, float magnitude) { stats.fireTileImmune = true; } },
    OWN_EXPLOSION_REDUCTION { @Override void apply(BuildStats stats, float magnitude) {
                                stats.ownExplosionDamageReduction = Math.min(1f, stats.ownExplosionDamageReduction + magnitude); } },
    BARREL_DAMAGE         { @Override void apply(BuildStats stats, float magnitude) { stats.barrelDamageBonus += magnitude; } },
    BARRELS_ON_MINIMAP    { @Override void apply(BuildStats stats, float magnitude) { stats.barrelsOnMinimap = true; } },
    ARC_CHAIN_JUMP        { @Override void apply(BuildStats stats, float magnitude) { stats.arcChainJumpBonus += Math.round(magnitude); } },
    ARC_CHAIN_MULTIPLIER  { @Override void apply(BuildStats stats, float magnitude) { stats.arcChainMultiplierBonus += magnitude; } },
    PIERCE_PER_ENEMY_DAMAGE { @Override void apply(BuildStats stats, float magnitude) { stats.piercePerEnemyBonus += magnitude; } },
    WIDE_GUARD_ARC        { @Override void apply(BuildStats stats, float magnitude) { stats.wideGuardArc = true; } },
    /** Shield Wall r2: the guarded heavy multiplier becomes the magnitude (the lowest override wins). */
    HEAVY_GUARD_MULTIPLIER { @Override void apply(BuildStats stats, float magnitude) {
                                stats.heavyGuardMultiplier = Float.isNaN(stats.heavyGuardMultiplier)
                                        ? magnitude : Math.min(stats.heavyGuardMultiplier, magnitude); } },
    /** Shield Wall r3: the guard covers every lane. */
    ALL_ROUND_GUARD       { @Override void apply(BuildStats stats, float magnitude) { stats.allRoundGuard = true; } },
    RIPOSTE_VULNERABLE_TURNS { @Override void apply(BuildStats stats, float magnitude) { stats.riposteVulnerableBonusTurns += Math.round(magnitude); } },
    RIPOSTE_DAMAGE        { @Override void apply(BuildStats stats, float magnitude) { stats.riposteDamageBonus += magnitude; } },
    MAX_ARMOR_BONUS       { @Override void apply(BuildStats stats, float magnitude) { stats.maxArmorBonus += magnitude; } },
    MAX_HEALTH_BONUS      { @Override void apply(BuildStats stats, float magnitude) { stats.maxHealthBonus += magnitude; } },
    MELEE_KILL_HEAL       { @Override void apply(BuildStats stats, float magnitude) { stats.meleeKillHealFraction += magnitude; } },
    AMMO_PICKUP_BONUS     { @Override void apply(BuildStats stats, float magnitude) { stats.ammoPickupBonus += magnitude; } },
    SHOP_DISCOUNT         { @Override void apply(BuildStats stats, float magnitude) { stats.shopDiscount += magnitude; } },
    CARRIER_EXTRA_DROP    { @Override void apply(BuildStats stats, float magnitude) {
                                stats.carrierExtraDropChance = Math.min(1f, stats.carrierExtraDropChance + magnitude); } },
    HEAL_BONUS            { @Override void apply(BuildStats stats, float magnitude) { stats.healBonus += magnitude; } },
    SECOND_WIND           { @Override void apply(BuildStats stats, float magnitude) { stats.secondWind = true; } },

    // ---- KEYSTONE GAINS -----------------------------------------------------------------------
    SPREAD_RELOAD_FREE    { @Override void apply(BuildStats stats, float magnitude) { stats.spreadReloadFree = true; } },
    POINT_BLANK_KILL_SHELL_REFUND { @Override void apply(BuildStats stats, float magnitude) { stats.pointBlankKillShellRefund = true; } },
    FIRST_HIT_CRITICAL    { @Override void apply(BuildStats stats, float magnitude) { stats.firstHitGuaranteedCritical = true; } },
    /** PYROMANIAC: fire immunity covers fire tiles too. */
    FIRE_IMMUNE           { @Override void apply(BuildStats stats, float magnitude) {
                                stats.fireImmune = true;
                                stats.fireTileImmune = true; } },
    BURNING_TARGET_DAMAGE { @Override void apply(BuildStats stats, float magnitude) { stats.burningTargetDamageBonus += magnitude; } },
    OWN_EXPLOSION_IMMUNE  { @Override void apply(BuildStats stats, float magnitude) { stats.ownExplosionImmune = true; } },
    DETONATED_BARREL_MULTIPLIER { @Override void apply(BuildStats stats, float magnitude) { stats.detonatedBarrelMultiplier *= magnitude; } },
    ENERGY_ARC            { @Override void apply(BuildStats stats, float magnitude) {
                                stats.energyArcFraction = Math.max(stats.energyArcFraction, magnitude); } },
    GUARD_REFLECT         { @Override void apply(BuildStats stats, float magnitude) { stats.guardReflectFraction += magnitude; } },
    BERSERK_MELEE_KILL_HEAL { @Override void apply(BuildStats stats, float magnitude) { stats.berserkMeleeKillHealFraction += magnitude; } },
    BERSERK_LOW_HEALTH_MELEE { @Override void apply(BuildStats stats, float magnitude) { stats.berserkLowHealthMeleeBonus += magnitude; } },
    EXTRA_LOADOUT_SLOT    { @Override void apply(BuildStats stats, float magnitude) { stats.extraLoadoutSlots += Math.round(magnitude); } },
    STIMS_DO_NOT_CLEANSE  { @Override void apply(BuildStats stats, float magnitude) { stats.stimsDoNotCleanse = true; } },
    /** IRONHIDE's gain: a max-HP MULTIPLIER (above 1), so it stacks after Hardy like every keystone term. */
    MAX_HEALTH_KEYSTONE_MULTIPLIER { @Override void apply(BuildStats stats, float magnitude) { stats.maxHealthKeystoneMultiplier *= magnitude; } },
    /** QUARTERMASTER's gain: a shop price MULTIPLIER (below 1). */
    SHOP_PRICE_MULTIPLIER { @Override void apply(BuildStats stats, float magnitude) { stats.shopPriceKeystoneMultiplier *= magnitude; } },

    // ---- KEYSTONE COSTS (multiplicative; magnitude = the multiplier, e.g. 0.85) ----------------
    /** SCATTERGUN DOCTRINE: every class but SPREAD. */
    NON_SPREAD_DAMAGE_COST { @Override void apply(BuildStats stats, float magnitude) {
                                for (DamageClass damageClass : DAMAGE_CLASSES) {
                                    if (damageClass != DamageClass.SPREAD) multiplyClassDamage(stats, damageClass, magnitude);
                                } } },
    BALLISTIC_DAMAGE_COST { @Override void apply(BuildStats stats, float magnitude) { multiplyClassDamage(stats, DamageClass.BALLISTIC, magnitude); } },
    ALL_WEAPON_DAMAGE_COST { @Override void apply(BuildStats stats, float magnitude) {
                                for (DamageClass damageClass : DAMAGE_CLASSES) multiplyClassDamage(stats, damageClass, magnitude); } },
    CLIP_SIZE_COST        { @Override void apply(BuildStats stats, float magnitude) { stats.clipSizeCostMultiplier *= magnitude; } },
    MAX_ARMOR_COST        { @Override void apply(BuildStats stats, float magnitude) { stats.maxArmorCostMultiplier *= magnitude; } },
    MAX_HEALTH_COST       { @Override void apply(BuildStats stats, float magnitude) { stats.maxHealthKeystoneMultiplier *= magnitude; } },
    HEAL_COST             { @Override void apply(BuildStats stats, float magnitude) { stats.healKeystoneMultiplier *= magnitude; } };

    private static final DamageClass[] DAMAGE_CLASSES = DamageClass.values();

    /** Folds one effect of this kind, at the given (total) magnitude, into the aggregate. */
    abstract void apply(BuildStats stats, float magnitude);

    private static void addClassDamage(BuildStats stats, DamageClass damageClass, float magnitude) {
        stats.classDamageBonus[damageClass.ordinal()] += magnitude;
    }

    private static void multiplyClassDamage(BuildStats stats, DamageClass damageClass, float multiplier) {
        stats.classDamageCostMultiplier[damageClass.ordinal()] *= multiplier;
    }
}
