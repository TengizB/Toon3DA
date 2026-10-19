package ge.tbegvadze.toon3d.progression;

import ge.tbegvadze.toon3d.util.BalanceConfig;

/**
 * THE KEYSTONES (balance-overhaul order 5, rule B4) — the rule-changers offered at character levels
 * {@code KEYSTONE_CHARACTER_LEVELS} (5 / 10 / 15 / 20) instead of that level's perk offer. Each row is
 * data: the {@link BuildTag} it matches (the B4 "at least one matching the highest-rank tag" test), its
 * GAIN effects and its COST effects (both {@link PerkEffect}s of {@link PerkModifier} kinds — costs are
 * multipliers, so they stack after every perk bonus), and the two UI lines the keystone screen prints
 * (the cost line in red). A keystone is taken at most once per run; R-KEYSTONE requires its niche to gain
 * at least {@code KEYSTONE_NICHE_MIN_GAIN} and some other scenario or metric to lose at least
 * {@code KEYSTONE_COST_MIN_LOSS}.
 *
 * <p>DEADEYE's critical applies to the first hit on each enemy from ANY weapon (the spec names no class).
 * Headless: no LibGDX imports.
 */
public enum Keystone {

    SCATTERGUN_DOCTRINE(BuildTag.CLOSE_QUARTERS, "SCATTERGUN DOCTRINE",
            "SPREAD reloads cost no turn; point-blank kills refund a shell",
            "Non-SPREAD damage -" + Perk.percent(1f - BalanceConfig.KEYSTONE_SCATTERGUN_NON_SPREAD_MULTIPLIER),
            new PerkEffect[]{
                    PerkEffect.of(PerkModifier.SPREAD_RELOAD_FREE, 1f),
                    PerkEffect.of(PerkModifier.POINT_BLANK_KILL_SHELL_REFUND, 1f)},
            new PerkEffect[]{
                    PerkEffect.of(PerkModifier.NON_SPREAD_DAMAGE_COST, BalanceConfig.KEYSTONE_SCATTERGUN_NON_SPREAD_MULTIPLIER)}),
    DEADEYE(BuildTag.MARKSMAN, "DEADEYE",
            "The first hit on each enemy is a guaranteed critical",
            "Clip sizes -" + Perk.percent(1f - BalanceConfig.KEYSTONE_DEADEYE_CLIP_MULTIPLIER),
            new PerkEffect[]{PerkEffect.of(PerkModifier.FIRST_HIT_CRITICAL, 1f)},
            new PerkEffect[]{PerkEffect.of(PerkModifier.CLIP_SIZE_COST, BalanceConfig.KEYSTONE_DEADEYE_CLIP_MULTIPLIER)}),
    PYROMANIAC(BuildTag.PYRO, "PYROMANIAC",
            "Immune to fire; burning enemies take +" + Perk.percent(BalanceConfig.KEYSTONE_PYROMANIAC_BURNING_DAMAGE)
                    + " from every source",
            "Max armour -" + Perk.percent(1f - BalanceConfig.KEYSTONE_PYROMANIAC_ARMOR_MULTIPLIER),
            new PerkEffect[]{
                    PerkEffect.of(PerkModifier.FIRE_IMMUNE, 1f),
                    PerkEffect.of(PerkModifier.BURNING_TARGET_DAMAGE, BalanceConfig.KEYSTONE_PYROMANIAC_BURNING_DAMAGE)},
            new PerkEffect[]{PerkEffect.of(PerkModifier.MAX_ARMOR_COST, BalanceConfig.KEYSTONE_PYROMANIAC_ARMOR_MULTIPLIER)}),
    DEMOLITIONIST(BuildTag.DEMOLITION, "DEMOLITIONIST",
            "Immune to your own explosions; barrels you detonate deal x"
                    + Math.round(BalanceConfig.KEYSTONE_DEMOLITIONIST_BARREL_MULTIPLIER),
            "Max HP -" + Perk.percent(1f - BalanceConfig.KEYSTONE_DEMOLITIONIST_HEALTH_MULTIPLIER),
            new PerkEffect[]{
                    PerkEffect.of(PerkModifier.OWN_EXPLOSION_IMMUNE, 1f),
                    PerkEffect.of(PerkModifier.DETONATED_BARREL_MULTIPLIER, BalanceConfig.KEYSTONE_DEMOLITIONIST_BARREL_MULTIPLIER)},
            new PerkEffect[]{PerkEffect.of(PerkModifier.MAX_HEALTH_COST, BalanceConfig.KEYSTONE_DEMOLITIONIST_HEALTH_MULTIPLIER)}),
    STORM_CONDUIT(BuildTag.ENERGY, "STORM CONDUIT",
            "ENERGY hits arc to one adjacent enemy at " + Perk.percent(BalanceConfig.KEYSTONE_STORM_CONDUIT_ARC_FRACTION),
            "BALLISTIC damage -" + Perk.percent(1f - BalanceConfig.KEYSTONE_STORM_CONDUIT_BALLISTIC_MULTIPLIER),
            new PerkEffect[]{PerkEffect.of(PerkModifier.ENERGY_ARC, BalanceConfig.KEYSTONE_STORM_CONDUIT_ARC_FRACTION)},
            new PerkEffect[]{PerkEffect.of(PerkModifier.BALLISTIC_DAMAGE_COST, BalanceConfig.KEYSTONE_STORM_CONDUIT_BALLISTIC_MULTIPLIER)}),
    IRON_BULWARK(BuildTag.BULWARK, "IRON BULWARK",
            "A guarded hit reflects " + Perk.percent(BalanceConfig.KEYSTONE_IRON_BULWARK_REFLECT_FRACTION)
                    + " of the blocked damage to the attacker",
            "All weapon damage -" + Perk.percent(1f - BalanceConfig.KEYSTONE_IRON_BULWARK_DAMAGE_MULTIPLIER),
            new PerkEffect[]{PerkEffect.of(PerkModifier.GUARD_REFLECT, BalanceConfig.KEYSTONE_IRON_BULWARK_REFLECT_FRACTION)},
            new PerkEffect[]{PerkEffect.of(PerkModifier.ALL_WEAPON_DAMAGE_COST, BalanceConfig.KEYSTONE_IRON_BULWARK_DAMAGE_MULTIPLIER)}),
    BERSERKER(BuildTag.BLADE, "BERSERKER",
            "Melee kills restore " + Perk.percent(BalanceConfig.KEYSTONE_BERSERKER_KILL_HEAL) + " max HP; below "
                    + Perk.percent(BalanceConfig.BUILD_BERSERK_LOW_HEALTH_FRACTION) + " HP melee +"
                    + Perk.percent(BalanceConfig.KEYSTONE_BERSERKER_LOW_HEALTH_MELEE),
            "Heals -" + Perk.percent(1f - BalanceConfig.KEYSTONE_BERSERKER_HEAL_MULTIPLIER),
            new PerkEffect[]{
                    PerkEffect.of(PerkModifier.BERSERK_MELEE_KILL_HEAL, BalanceConfig.KEYSTONE_BERSERKER_KILL_HEAL),
                    PerkEffect.of(PerkModifier.BERSERK_LOW_HEALTH_MELEE, BalanceConfig.KEYSTONE_BERSERKER_LOW_HEALTH_MELEE)},
            new PerkEffect[]{PerkEffect.of(PerkModifier.HEAL_COST, BalanceConfig.KEYSTONE_BERSERKER_HEAL_MULTIPLIER)}),
    QUARTERMASTER(BuildTag.SCAVENGER, "QUARTERMASTER",
            "+" + BalanceConfig.KEYSTONE_QUARTERMASTER_EXTRA_SLOTS + " weapon loadout slot; shop prices -"
                    + Perk.percent(1f - BalanceConfig.KEYSTONE_QUARTERMASTER_PRICE_MULTIPLIER),
            "Max HP -" + Perk.percent(1f - BalanceConfig.KEYSTONE_QUARTERMASTER_HEALTH_MULTIPLIER),
            new PerkEffect[]{
                    PerkEffect.of(PerkModifier.EXTRA_LOADOUT_SLOT, BalanceConfig.KEYSTONE_QUARTERMASTER_EXTRA_SLOTS),
                    PerkEffect.of(PerkModifier.SHOP_PRICE_MULTIPLIER, BalanceConfig.KEYSTONE_QUARTERMASTER_PRICE_MULTIPLIER)},
            new PerkEffect[]{PerkEffect.of(PerkModifier.MAX_HEALTH_COST, BalanceConfig.KEYSTONE_QUARTERMASTER_HEALTH_MULTIPLIER)}),
    IRONHIDE(BuildTag.VITALITY, "IRONHIDE",
            "Max HP +" + Perk.percent(BalanceConfig.KEYSTONE_IRONHIDE_HEALTH_MULTIPLIER - 1f),
            "Heals -" + Perk.percent(1f - BalanceConfig.KEYSTONE_IRONHIDE_HEAL_MULTIPLIER) + "; STIMs no longer cleanse",
            new PerkEffect[]{PerkEffect.of(PerkModifier.MAX_HEALTH_KEYSTONE_MULTIPLIER, BalanceConfig.KEYSTONE_IRONHIDE_HEALTH_MULTIPLIER)},
            new PerkEffect[]{
                    PerkEffect.of(PerkModifier.HEAL_COST, BalanceConfig.KEYSTONE_IRONHIDE_HEAL_MULTIPLIER),
                    PerkEffect.of(PerkModifier.STIMS_DO_NOT_CLEANSE, 1f)});

    public final BuildTag tag;
    public final String   displayName;
    /** The gain, one plain line. */
    public final String   gainLine;
    /** The cost, one plain line (drawn in red). */
    public final String   costLine;
    private final PerkEffect[] gains;
    private final PerkEffect[] costs;

    Keystone(BuildTag tag, String displayName, String gainLine, String costLine,
             PerkEffect[] gains, PerkEffect[] costs) {
        this.tag         = java.util.Objects.requireNonNull(tag, "Keystone tag");
        this.displayName = displayName;
        this.gainLine    = gainLine;
        this.costLine    = costLine;
        this.gains       = gains;
        this.costs       = costs;
    }

    /** How many gain effect rows this keystone declares (read-only; C-PERK reads it). */
    public int gainEffectCount() {
        return gains.length;
    }

    /** How many cost effect rows this keystone declares (read-only; C-PERK reads it). */
    public int costEffectCount() {
        return costs.length;
    }

    /** Folds the gains and the costs into the aggregate. */
    public void apply(BuildStats stats) {
        applyGains(stats);
        applyCosts(stats);
    }

    /** Folds only the gains (the valuation reads the gain and the cost separately). */
    public void applyGains(BuildStats stats) {
        for (PerkEffect effect : gains) effect.applyAt(stats, 1);
    }

    /** Folds only the costs. */
    public void applyCosts(BuildStats stats) {
        for (PerkEffect effect : costs) effect.applyAt(stats, 1);
    }
}
