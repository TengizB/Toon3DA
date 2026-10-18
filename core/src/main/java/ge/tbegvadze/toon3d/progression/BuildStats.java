package ge.tbegvadze.toon3d.progression;

import ge.tbegvadze.toon3d.entity.DamageClass;
import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.GameMath;

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

    /**
     * The no-build aggregate every runtime seam starts from before World / SimWorld hands it the run's
     * {@link PlayerBuild#stats()} (a headless test, a weapon built before the build is wired). Never passed to
     * {@link PerkModifier#apply} or {@link #reset()}-then-mutated: it must stay the identity build.
     */
    public static final BuildStats NONE = new BuildStats();

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
    /** Wildfire (D5): a burning enemy that dies spreads its full burn stacks to its neighbours. */
    boolean wildfire;
    /** Wildfire (D5): extra turns every burn the player applies lasts. */
    int   burnTurnBonus;
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

    // --- RELIC KINDS (relics-run-changing-artifacts CP1): identity defaults, so a relic-free build reads as before ---
    /** Relic KILL_HEAL: fraction of max HP every kill restores. */
    float killHealFraction;
    /** Relic KILL_ARMOR: fraction of max armour every kill restores. */
    float killArmorFraction;
    /** Relic KILL_CREDIT_BONUS: additive bonus on the credits a kill pays. */
    float killCreditBonus;
    /** Relic KILL_AMMO_REFUND: chance a kill refunds one round (capped at 1). */
    float killAmmoRefundChance;
    /** Relic XP_MULTIPLIER: additive bonus on the experience a kill awards. */
    float experienceBonus;
    /** Relic DOOR_BLAST: blast fraction of a door the player opens. */
    float doorBlastFraction;
    /** Relic DOOR_OPEN_HEAL: fraction of max HP a door the player opens heals. */
    float doorOpenHealFraction;
    /** Relic CORPSE_BLAST: blast fraction of a kill's corpse burst. */
    float corpseBlastFraction;
    /** Relic FLOOR_ARRIVAL_HEAL: fraction of max HP restored on arrival. */
    float floorArrivalHealFraction;
    /** Relic FLOOR_ARRIVAL_ARMOR: fraction of max armour restored on arrival. */
    float floorArrivalArmorFraction;
    /** Relic FLOOR_ARRIVAL_AMMO: fraction of each carried ammo type's cap refilled on arrival. */
    float floorArrivalAmmoFraction;
    /** Relic FIRST_HIT_SHIELD: the first hit each floor deals nothing. */
    boolean firstHitShield;
    /** Relic LOW_HEALTH_DAMAGE: additive damage bonus for every weapon class below the low-health fraction. */
    float lowHealthDamageBonus;
    /** Relic DAMAGE_TAKEN_MULTIPLIER: product of the multipliers (1.0 = unchanged); read clamped. */
    float damageTakenFactor = 1f;
    /** Relic ENEMY_HEALTH_MULTIPLIER: product of the multipliers (1.0 = unchanged); read clamped. */
    float enemyHealthFactor = 1f;

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
        burnTurnBonus = 0;
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
        killHealFraction = 0f;
        killArmorFraction = 0f;
        killCreditBonus = 0f;
        killAmmoRefundChance = 0f;
        experienceBonus = 0f;
        doorBlastFraction = 0f;
        doorOpenHealFraction = 0f;
        corpseBlastFraction = 0f;
        floorArrivalHealFraction = 0f;
        floorArrivalArmorFraction = 0f;
        floorArrivalAmmoFraction = 0f;
        firstHitShield = false;
        lowHealthDamageBonus = 0f;
        damageTakenFactor = 1f;
        enemyHealthFactor = 1f;
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
        burnTurnBonus = other.burnTurnBonus;
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
        killHealFraction = other.killHealFraction;
        killArmorFraction = other.killArmorFraction;
        killCreditBonus = other.killCreditBonus;
        killAmmoRefundChance = other.killAmmoRefundChance;
        experienceBonus = other.experienceBonus;
        doorBlastFraction = other.doorBlastFraction;
        doorOpenHealFraction = other.doorOpenHealFraction;
        corpseBlastFraction = other.corpseBlastFraction;
        floorArrivalHealFraction = other.floorArrivalHealFraction;
        floorArrivalArmorFraction = other.floorArrivalArmorFraction;
        floorArrivalAmmoFraction = other.floorArrivalAmmoFraction;
        firstHitShield = other.firstHitShield;
        lowHealthDamageBonus = other.lowHealthDamageBonus;
        damageTakenFactor = other.damageTakenFactor;
        enemyHealthFactor = other.enemyHealthFactor;
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


    /**
     * Every perk / keystone term on ONE player hit, composed by {@link GameMath#buildHitDamageMultiplier}
     * (balance-overhaul order 5, CP2) — the single typed query {@code EnemyManager.applyDamageTo} makes:
     * the class multiplier ({@link #damageMultiplierFor}: Buckshot / Shaped Charges / Capacitor / Accelerant /
     * Butcher, then the keystone costs), the range band (Point Man at point-blank on SPREAD, Lane Discipline
     * at long range on BALLISTIC / RAIL — mutually exclusive by class), the first hit on this enemy (First
     * Blood on a full-HP target for BALLISTIC / RAIL, DEADEYE's guaranteed critical on any class), a burning
     * target (PYROMANIAC), a low-HP melee swing (BERSERKER) and a target inside a riposte window (Riposte
     * Drill). Mirrors util/BuildValuationModel.buildWeapon term for term.
     *
     * @param damageClass          the activation's class (null -> 1, no build term applies)
     * @param distanceTiles        tiles from the shooter to the target along the shot (1 = adjacent)
     * @param firstHitOnTarget     this is the first player hit this enemy has taken
     * @param targetAtFullHealth   the target was at full HP before the hit
     * @param targetBurning        the target is BURNING
     * @param meleeSwing           the hit is a melee swing
     * @param wielderLowHealth     the player is below BUILD_BERSERK_LOW_HEALTH_FRACTION of max HP
     * @param targetInRiposteWindow the target is inside a riposte's VULNERABLE window
     */
    public float hitDamageMultiplier(DamageClass damageClass, int distanceTiles, boolean firstHitOnTarget,
                                     boolean targetAtFullHealth, boolean targetBurning, boolean meleeSwing,
                                     boolean wielderLowHealth, boolean targetInRiposteWindow) {
        if (damageClass == null) return 1f;
        boolean marksman = BuildTag.MARKSMAN.ownsDamageClass(damageClass);
        boolean spread   = BuildTag.CLOSE_QUARTERS.ownsDamageClass(damageClass);
        float rangeBandBonus = 0f;
        if (marksman && distanceTiles >= BalanceConfig.BUILD_LONG_RANGE_MIN_TILES) rangeBandBonus = longRangeDamageBonus;
        if (spread && distanceTiles <= BalanceConfig.BUILD_POINT_BLANK_MAX_TILES) rangeBandBonus = spreadPointBlankDamageBonus;
        float firstHitBonus = firstHitOnTarget
                ? GameMath.combinedFirstHitBonus(marksman && targetAtFullHealth ? fullHealthTargetDamageBonus : 0f,
                        firstHitGuaranteedCritical, BalanceConfig.CRIT_DAMAGE_MULTIPLIER)
                : 0f;
        return GameMath.buildHitDamageMultiplier(damageMultiplierFor(damageClass), rangeBandBonus, firstHitBonus,
                targetBurning ? burningTargetDamageBonus : 0f,
                meleeSwing && wielderLowHealth ? berserkLowHealthMeleeBonus : 0f,
                targetInRiposteWindow ? riposteDamageBonus : 0f);
    }

    /**
     * PYROMANIAC's "+X% from every source" alone: the multiplier a BURNING target takes from a source that
     * carries no other build term (a barrel blast, an IRON BULWARK reflect). 1 when not burning.
     */
    public float burningTargetMultiplier(boolean targetBurning) {
        return targetBurning ? 1f + burningTargetDamageBonus : 1f;
    }

    /** Steady Aim's falloff reduction for a weapon of this class (BALLISTIC / RAIL only; 0 otherwise). */
    public float falloffReductionFor(DamageClass damageClass) {
        return BuildTag.MARKSMAN.ownsDamageClass(damageClass) ? ballisticRailFalloffReduction : 0f;
    }

    /** Lane Discipline's extra reach for a weapon of this class (BALLISTIC / RAIL only; 0 otherwise). */
    public int rangeBonusTilesFor(DamageClass damageClass) {
        return BuildTag.MARKSMAN.ownsDamageClass(damageClass) ? rangeBonusTiles : 0;
    }

    /**
     * Multiplier on a barrel blast: Sapper on every barrel, DEMOLITIONIST's x2 only on a barrel the PLAYER
     * set off (a shot, a grenade, or a chain from one). {@link #barrelDamageMultiplier()} is the player-set-off case.
     */
    public float barrelDamageMultiplier(boolean detonatedByPlayer) {
        return (1f + barrelDamageBonus) * (detonatedByPlayer ? detonatedBarrelMultiplier : 1f);
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
    public int   burnTurnBonus()                   { return burnTurnBonus; }
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

    // --- RELIC ACCESSORS (relics-run-changing-artifacts CP1) ------------------------------------
    public float killHealFraction()                { return killHealFraction; }
    public float killArmorFraction()               { return killArmorFraction; }
    /** Credit multiplier on a kill: 1 + the summed bonuses. */
    public float killCreditMultiplier()            { return 1f + killCreditBonus; }
    public float killAmmoRefundChance()            { return Math.min(1f, killAmmoRefundChance); }
    /** Experience multiplier on a kill: 1 + the summed bonuses. */
    public float experienceMultiplier()            { return 1f + experienceBonus; }
    public float doorBlastFraction()               { return doorBlastFraction; }
    public float doorOpenHealFraction()            { return doorOpenHealFraction; }
    public float corpseBlastFraction()             { return corpseBlastFraction; }
    public float floorArrivalHealFraction()        { return floorArrivalHealFraction; }
    public float floorArrivalArmorFraction()       { return floorArrivalArmorFraction; }
    public float floorArrivalAmmoFraction()        { return floorArrivalAmmoFraction; }
    public boolean firstHitShield()                { return firstHitShield; }
    public float lowHealthDamageBonus()            { return lowHealthDamageBonus; }
    /** Damage-taken multiplier, clamped to at least RELIC_DAMAGE_TAKEN_MULTIPLIER_MIN (R12). */
    public float damageTakenMultiplier()           { return Math.max(BalanceConfig.RELIC_DAMAGE_TAKEN_MULTIPLIER_MIN, damageTakenFactor); }
    /** Enemy-health multiplier, clamped to at least RELIC_ENEMY_HEALTH_MULTIPLIER_MIN (R12). */
    public float enemyHealthMultiplier()           { return Math.max(BalanceConfig.RELIC_ENEMY_HEALTH_MULTIPLIER_MIN, enemyHealthFactor); }

    /**
     * A fresh aggregate with each effect applied at rank 1 (the relic valuation's input). Perk and
     * keystone state is not included: this is the relic's effects alone.
     */
    public static BuildStats ofEffects(PerkEffect... effects) {
        BuildStats stats = new BuildStats();
        for (PerkEffect effect : effects) effect.applyAt(stats, 1);
        return stats;
    }
}
