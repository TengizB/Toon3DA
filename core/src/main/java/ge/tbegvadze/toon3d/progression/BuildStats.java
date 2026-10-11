package ge.tbegvadze.toon3d.progression;

import ge.tbegvadze.toon3d.entity.DamageClass;

/**
 * THE BUILD AGGREGATE (balance-overhaul order 5) — everything the picked {@link Perk} ranks and
 * {@link Keystone}s change, folded into typed primitives. {@link PlayerBuild} recomputes it on every pick;
 * the runtime seams (damage, guard, heal, shop, supply, reload — CP2/CP3) and the R-PERK / R-KEYSTONE /
 * R-CARRY valuation ({@code util/BuildValuationModel}) read it through the accessors below. No maps, no
 * strings: one array lookup or one field read per query, so a hot path never allocates.
 *
 * <p><b>Stacking rule (EDGE CASES of the idea file):</b> perk bonuses ADD to each other; keystone costs
 * (and the QUARTERMASTER price cut) MULTIPLY after. Every accessor that combines the two encodes that
 * order — e.g. {@link #healMultiplier()} = (1 + healBonus) x keystoneHealMultiplier — so IRONHIDE's -20%
 * heals and Field Medic's bonus compose the same way at every call site.
 *
 * <p>Mutated only by {@link PerkModifier#apply} (package-private fields) and {@link #reset()}. Pure data,
 * headless, no LibGDX imports.
 */
public final class BuildStats {

    private static final int DAMAGE_CLASS_COUNT = DamageClass.values().length;
    private static final int ATTRIBUTE_COUNT    = Attribute.values().length;

    /** Attribute points the picked perk ranks grant, by Attribute ordinal (Butcher's STRENGTH). */
    final int[] attributeGrants = new int[ATTRIBUTE_COUNT];

    // --- PERK BONUSES (additive) ------------------------------------------------------------
    /** Additive damage bonus by DamageClass ordinal (Buckshot Discipline, Shaped Charges, Capacitor, Accelerant, Butcher). */
    final float[] classDamageBonus = new float[DAMAGE_CLASS_COUNT];
    /** Point Man: additive SPREAD damage bonus at point-blank range (<= BUILD_POINT_BLANK_MAX_TILES). */
    float spreadPointBlankDamageBonus;
    /** Steady Aim: fraction of the BALLISTIC / RAIL falloff coefficient removed (0.30 per rank). */
    float ballisticRailFalloffReduction;
    /** First Blood: BALLISTIC / RAIL damage bonus on a hit against an enemy still at full HP. */
    float fullHealthTargetDamageBonus;
    /** Lane Discipline: extra reach for BALLISTIC / RAIL weapons. */
    int   rangeBonusTiles;
    /** Lane Discipline: BALLISTIC / RAIL damage bonus at BUILD_LONG_RANGE_FROM_TILES or farther. */
    float longRangeDamageBonus;
    /** Point Man r2: extra SPREAD knockback reach (tiles). */
    int   knockbackBonusTiles;
    /** Point Man r3: extra SPREAD stagger reach (tiles). */
    int   staggerBonusTiles;
    /** Breacher: chance a SPREAD kill refunds one shell. */
    float spreadKillShellRefundChance;
    /** Accelerant: extra max burn stacks (FIRE). */
    int   burnMaxStackBonus;
    /** Wildfire: a burning enemy that dies ignites its tile. */
    boolean wildfire;
    /** Fireproof (and PYROMANIAC): immune to fire tiles. */
    boolean fireTileImmune;
    /** Blast Shield: fraction of the player's own explosion damage removed (never above 1). */
    float ownExplosionDamageReduction;
    /** Sapper: additive barrel blast bonus. */
    float barrelDamageBonus;
    /** Sapper r1: barrels drawn on the mini-map. */
    boolean barrelsOnMinimap;
    /** Arc Jump: extra Arc Cannon chain jumps. */
    int   arcChainJumpBonus;
    /** Arc Jump: additive raise of the Arc Cannon's per-jump chain multiplier. */
    float arcChainMultiplierBonus;
    /** Overcharge: Plasma pierce damage bonus per enemy already pierced by the same shot. */
    float piercePerEnemyBonus;
    /** Shield Wall r1: the guard's front arc widens to +/-90 degrees. */
    boolean wideGuardArc;
    /** Shield Wall r2: guarded HEAVY multiplier override (NaN = unchanged, use GUARD_FRONT_HEAVY). */
    float heavyGuardMultiplier = Float.NaN;
    /** Shield Wall r3: the guard covers every lane (no heavy arrives outside it). */
    boolean allRoundGuard;
    /** Riposte Drill: extra turns of VULNERABLE a riposte applies. */
    int   riposteVulnerableBonusTurns;
    /** Riposte Drill: additive extra damage the riposted (VULNERABLE) enemy takes. */
    float riposteDamageBonus;
    /** Plate Carrier: additive max-armour bonus (fraction of max). */
    float maxArmorBonus;
    /** Hardy: additive max-HP bonus (fraction of max). */
    float maxHealthBonus;
    /** Bloodletting: a melee kill heals this fraction of max HP. */
    float meleeKillHealFraction;
    /** Pack Rat: additive ammo pickup bonus. */
    float ammoPickupBonus;
    /** Haggler: additive shop discount. */
    float shopDiscount;
    /** Salvager: chance a carrier drops one extra item. */
    float carrierExtraDropChance;
    /** Field Medic: additive heal bonus. */
    float healBonus;
    /** Second Wind: once per floor a lethal hit leaves 1 HP. */
    boolean secondWind;

    // --- KEYSTONE GAINS -----------------------------------------------------------------------
    /** SCATTERGUN DOCTRINE: a SPREAD reload costs no turn. */
    boolean spreadReloadFree;
    /** SCATTERGUN DOCTRINE: a point-blank SPREAD kill refunds a shell. */
    boolean pointBlankKillShellRefund;
    /** DEADEYE: the first hit on each enemy is a guaranteed critical. */
    boolean firstHitGuaranteedCritical;
    /** PYROMANIAC: immune to fire (tiles and fire attacks). */
    boolean fireImmune;
    /** PYROMANIAC: additive damage burning enemies take from every source. */
    float burningTargetDamageBonus;
    /** DEMOLITIONIST: immune to the player's own explosions. */
    boolean ownExplosionImmune;
    /** DEMOLITIONIST: multiplier on barrels the player detonates (1.0 = unchanged). */
    float detonatedBarrelMultiplier = 1f;
    /** STORM CONDUIT: an ENERGY hit arcs to one adjacent enemy at this fraction (0 = no arc). */
    float energyArcFraction;
    /** IRON BULWARK: a guarded hit reflects this fraction of the blocked damage to the attacker. */
    float guardReflectFraction;
    /** BERSERKER: a melee kill restores this fraction of max HP (adds to Bloodletting). */
    float berserkMeleeKillHealFraction;
    /** BERSERKER: additive melee damage bonus while below BUILD_BERSERK_LOW_HEALTH_FRACTION of max HP. */
    float berserkLowHealthMeleeBonus;
    /** QUARTERMASTER: extra weapon loadout slots. */
    int   extraLoadoutSlots;
    /** IRONHIDE: a STIM no longer cleanses statuses. */
    boolean stimsDoNotCleanse;

    // --- KEYSTONE MULTIPLIERS (applied AFTER the additive perk bonuses) ------------------------
    /** Keystone damage cost by DamageClass ordinal (SCATTERGUN, STORM CONDUIT, IRON BULWARK); 1.0 = unchanged. */
    final float[] classDamageCostMultiplier = new float[DAMAGE_CLASS_COUNT];
    /** DEADEYE: clip-size multiplier. */
    float clipSizeCostMultiplier = 1f;
    /** PYROMANIAC: max-armour multiplier. */
    float maxArmorCostMultiplier = 1f;
    /** DEMOLITIONIST / QUARTERMASTER / IRONHIDE: max-HP multiplier (IRONHIDE's gain is a multiplier above 1). */
    float maxHealthKeystoneMultiplier = 1f;
    /** BERSERKER / IRONHIDE: heal multiplier. */
    float healKeystoneMultiplier = 1f;
    /** QUARTERMASTER: shop price multiplier. */
    float shopPriceKeystoneMultiplier = 1f;

    public BuildStats() {
        reset();
    }

    /** Restores every field to the no-build default. */
    public void reset() {
        java.util.Arrays.fill(attributeGrants, 0);
        java.util.Arrays.fill(classDamageBonus, 0f);
        java.util.Arrays.fill(classDamageCostMultiplier, 1f);
        spreadPointBlankDamageBonus = 0f;
        ballisticRailFalloffReduction = 0f;
        fullHealthTargetDamageBonus = 0f;
        rangeBonusTiles = 0;
        longRangeDamageBonus = 0f;
        knockbackBonusTiles = 0;
        staggerBonusTiles = 0;
        spreadKillShellRefundChance = 0f;
        burnMaxStackBonus = 0;
        wildfire = false;
        fireTileImmune = false;
        ownExplosionDamageReduction = 0f;
        barrelDamageBonus = 0f;
        barrelsOnMinimap = false;
        arcChainJumpBonus = 0;
        arcChainMultiplierBonus = 0f;
        piercePerEnemyBonus = 0f;
        wideGuardArc = false;
        heavyGuardMultiplier = Float.NaN;
        allRoundGuard = false;
        riposteVulnerableBonusTurns = 0;
        riposteDamageBonus = 0f;
        maxArmorBonus = 0f;
        maxHealthBonus = 0f;
        meleeKillHealFraction = 0f;
        ammoPickupBonus = 0f;
        shopDiscount = 0f;
        carrierExtraDropChance = 0f;
        healBonus = 0f;
        secondWind = false;
        spreadReloadFree = false;
        pointBlankKillShellRefund = false;
        firstHitGuaranteedCritical = false;
        fireImmune = false;
        burningTargetDamageBonus = 0f;
        ownExplosionImmune = false;
        detonatedBarrelMultiplier = 1f;
        energyArcFraction = 0f;
        guardReflectFraction = 0f;
        berserkMeleeKillHealFraction = 0f;
        berserkLowHealthMeleeBonus = 0f;
        extraLoadoutSlots = 0;
        stimsDoNotCleanse = false;
        clipSizeCostMultiplier = 1f;
        maxArmorCostMultiplier = 1f;
        maxHealthKeystoneMultiplier = 1f;
        healKeystoneMultiplier = 1f;
        shopPriceKeystoneMultiplier = 1f;
    }

    /** Copies every field from another aggregate (the valuation's per-rank snapshots). */
    public void copyFrom(BuildStats other) {
        System.arraycopy(other.attributeGrants, 0, attributeGrants, 0, ATTRIBUTE_COUNT);
        System.arraycopy(other.classDamageBonus, 0, classDamageBonus, 0, DAMAGE_CLASS_COUNT);
        System.arraycopy(other.classDamageCostMultiplier, 0, classDamageCostMultiplier, 0, DAMAGE_CLASS_COUNT);
        spreadPointBlankDamageBonus = other.spreadPointBlankDamageBonus;
        ballisticRailFalloffReduction = other.ballisticRailFalloffReduction;
        fullHealthTargetDamageBonus = other.fullHealthTargetDamageBonus;
        rangeBonusTiles = other.rangeBonusTiles;
        longRangeDamageBonus = other.longRangeDamageBonus;
        knockbackBonusTiles = other.knockbackBonusTiles;
        staggerBonusTiles = other.staggerBonusTiles;
        spreadKillShellRefundChance = other.spreadKillShellRefundChance;
        burnMaxStackBonus = other.burnMaxStackBonus;
        wildfire = other.wildfire;
        fireTileImmune = other.fireTileImmune;
        ownExplosionDamageReduction = other.ownExplosionDamageReduction;
        barrelDamageBonus = other.barrelDamageBonus;
        barrelsOnMinimap = other.barrelsOnMinimap;
        arcChainJumpBonus = other.arcChainJumpBonus;
        arcChainMultiplierBonus = other.arcChainMultiplierBonus;
        piercePerEnemyBonus = other.piercePerEnemyBonus;
        wideGuardArc = other.wideGuardArc;
        heavyGuardMultiplier = other.heavyGuardMultiplier;
        allRoundGuard = other.allRoundGuard;
        riposteVulnerableBonusTurns = other.riposteVulnerableBonusTurns;
        riposteDamageBonus = other.riposteDamageBonus;
        maxArmorBonus = other.maxArmorBonus;
        maxHealthBonus = other.maxHealthBonus;
        meleeKillHealFraction = other.meleeKillHealFraction;
        ammoPickupBonus = other.ammoPickupBonus;
        shopDiscount = other.shopDiscount;
        carrierExtraDropChance = other.carrierExtraDropChance;
        healBonus = other.healBonus;
        secondWind = other.secondWind;
        spreadReloadFree = other.spreadReloadFree;
        pointBlankKillShellRefund = other.pointBlankKillShellRefund;
        firstHitGuaranteedCritical = other.firstHitGuaranteedCritical;
        fireImmune = other.fireImmune;
        burningTargetDamageBonus = other.burningTargetDamageBonus;
        ownExplosionImmune = other.ownExplosionImmune;
        detonatedBarrelMultiplier = other.detonatedBarrelMultiplier;
        energyArcFraction = other.energyArcFraction;
        guardReflectFraction = other.guardReflectFraction;
        berserkMeleeKillHealFraction = other.berserkMeleeKillHealFraction;
        berserkLowHealthMeleeBonus = other.berserkLowHealthMeleeBonus;
        extraLoadoutSlots = other.extraLoadoutSlots;
        stimsDoNotCleanse = other.stimsDoNotCleanse;
        clipSizeCostMultiplier = other.clipSizeCostMultiplier;
        maxArmorCostMultiplier = other.maxArmorCostMultiplier;
        maxHealthKeystoneMultiplier = other.maxHealthKeystoneMultiplier;
        healKeystoneMultiplier = other.healKeystoneMultiplier;
        shopPriceKeystoneMultiplier = other.shopPriceKeystoneMultiplier;
    }

    // =====================================================================================
    // COMBINED ACCESSORS — perk bonuses add, keystone multipliers apply after.
    // =====================================================================================

    /** Damage multiplier for a weapon of this class: (1 + perk bonus) x keystone cost. Null -> 1. */
    public float damageMultiplierFor(DamageClass damageClass) {
        if (damageClass == null) return 1f;
        return (1f + classDamageBonus[damageClass.ordinal()]) * classDamageCostMultiplier[damageClass.ordinal()];
    }

    /** Max-HP multiplier: (1 + Hardy) x keystone (DEMOLITIONIST / QUARTERMASTER cut, IRONHIDE raise). */
    public float maxHealthMultiplier() {
        return (1f + maxHealthBonus) * maxHealthKeystoneMultiplier;
    }

    /** Max-armour multiplier: (1 + Plate Carrier) x keystone (PYROMANIAC cut). */
    public float maxArmorMultiplier() {
        return (1f + maxArmorBonus) * maxArmorCostMultiplier;
    }

    /** Heal multiplier on every medkit / stim: (1 + Field Medic) x keystone (BERSERKER / IRONHIDE cut). */
    public float healMultiplier() {
        return (1f + healBonus) * healKeystoneMultiplier;
    }

    /** Shop price multiplier: (1 - Haggler discounts, floored at 0) x keystone (QUARTERMASTER). */
    public float shopPriceMultiplier() {
        return Math.max(0f, 1f - shopDiscount) * shopPriceKeystoneMultiplier;
    }

    /** Ammo pickup multiplier: 1 + Pack Rat. */
    public float ammoPickupMultiplier() {
        return 1f + ammoPickupBonus;
    }

    /** Clip-size multiplier (DEADEYE's cost); the caller keeps a clip of at least one. */
    public float clipSizeMultiplier() {
        return clipSizeCostMultiplier;
    }

    /**
     * Multiplier on the player's OWN explosion damage to the player: 0 when DEMOLITIONIST, else
     * 1 - Blast Shield (clamped to [0, 1]).
     */
    public float ownExplosionDamageMultiplier() {
        if (ownExplosionImmune) return 0f;
        return Math.max(0f, Math.min(1f, 1f - ownExplosionDamageReduction));
    }

    /** Multiplier on a barrel blast the player set off: (1 + Sapper) x DEMOLITIONIST. */
    public float barrelDamageMultiplier() {
        return (1f + barrelDamageBonus) * detonatedBarrelMultiplier;
    }

    /** Fraction of max HP a melee kill restores: Bloodletting + BERSERKER. */
    public float meleeKillHealTotalFraction() {
        return meleeKillHealFraction + berserkMeleeKillHealFraction;
    }

    /** True when fire tiles cannot hurt the player (Fireproof or PYROMANIAC). */
    public boolean isFireTileImmune() {
        return fireTileImmune || fireImmune;
    }

    /** Guarded HEAVY multiplier, or the given default when Shield Wall has not overridden it. */
    public float guardHeavyMultiplierOr(float defaultMultiplier) {
        return Float.isNaN(heavyGuardMultiplier) ? defaultMultiplier : heavyGuardMultiplier;
    }


    // =====================================================================================
    // RAW ACCESSORS
    // =====================================================================================

    /** Attribute points the picked perks grant to one attribute. */
    public int attributeGrant(Attribute attribute) {
        return attributeGrants[attribute.ordinal()];
    }

    public float spreadPointBlankDamageBonus()     { return spreadPointBlankDamageBonus; }
    public float ballisticRailFalloffReduction()   { return ballisticRailFalloffReduction; }
    public float fullHealthTargetDamageBonus()     { return fullHealthTargetDamageBonus; }
    public int   rangeBonusTiles()                 { return rangeBonusTiles; }
    public float longRangeDamageBonus()            { return longRangeDamageBonus; }
    public int   knockbackBonusTiles()             { return knockbackBonusTiles; }
    public int   staggerBonusTiles()               { return staggerBonusTiles; }
    public float spreadKillShellRefundChance()     { return spreadKillShellRefundChance; }
    public int   burnMaxStackBonus()               { return burnMaxStackBonus; }
    public boolean wildfire()                      { return wildfire; }
    public boolean barrelsOnMinimap()              { return barrelsOnMinimap; }
    public int   arcChainJumpBonus()               { return arcChainJumpBonus; }
    public float arcChainMultiplierBonus()         { return arcChainMultiplierBonus; }
    public float piercePerEnemyBonus()             { return piercePerEnemyBonus; }
    public boolean wideGuardArc()                  { return wideGuardArc; }
    public boolean allRoundGuard()                 { return allRoundGuard; }
    public int   riposteVulnerableBonusTurns()     { return riposteVulnerableBonusTurns; }
    public float riposteDamageBonus()              { return riposteDamageBonus; }
    public boolean secondWind()                    { return secondWind; }
    public boolean spreadReloadFree()              { return spreadReloadFree; }
    public boolean pointBlankKillShellRefund()     { return pointBlankKillShellRefund; }
    public boolean firstHitGuaranteedCritical()    { return firstHitGuaranteedCritical; }
    public boolean fireImmune()                    { return fireImmune; }
    public float burningTargetDamageBonus()        { return burningTargetDamageBonus; }
    public boolean ownExplosionImmune()            { return ownExplosionImmune; }
    public float energyArcFraction()               { return energyArcFraction; }
    public float guardReflectFraction()            { return guardReflectFraction; }
    public float berserkLowHealthMeleeBonus()      { return berserkLowHealthMeleeBonus; }
    public int   extraLoadoutSlots()               { return extraLoadoutSlots; }
    public boolean stimsDoNotCleanse()             { return stimsDoNotCleanse; }
    public float carrierExtraDropChance()          { return carrierExtraDropChance; }
}
