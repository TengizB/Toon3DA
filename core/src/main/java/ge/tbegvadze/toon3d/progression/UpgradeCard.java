package ge.tbegvadze.toon3d.progression;

import ge.tbegvadze.toon3d.util.GameBalance;
import ge.tbegvadze.toon3d.util.GameMath;

/**
 * Every level-up upgrade the player can be offered (idea 5: build diversity & synergy).
 *
 * <h2>The power-budget contract</h2>
 * Each card is a pure-data bundle of stat deltas, drawn from one of four {@link UpgradeCardPool}s.
 * The defining rule is that EVERY card costs the same power budget
 * ({@link GameBalance#LEVEL_UP_BUDGET_PP}), measured in "power points" (PP) — the %-gain a card
 * grants to the player's reference offence (DPT) or survivability (eHP). Because each pick is
 * budget-equal, the player's TOTAL power at level L is build-independent (≈ L × budget); only its
 * SHAPE differs. That is how the system delivers run-to-run diversity without breaking the
 * depth-coupling balance band. See {@link #estimatedPowerPoints()} for the live pricing and
 * {@code BalanceReport} for the living table that flags any card outside the budget band.
 *
 * <h2>Trade-off cards</h2>
 * Cards with a negative delta ({@link #isTradeOff()}) grant MORE than budget on one axis and pay
 * it back on another, netting to ≈budget but with high variance — the risk/reward picks roguelikes
 * thrive on (design pillar 4). A trade-off card's downside is priced as negative PP, so its net PP
 * still lands in the budget band.
 *
 * <h2>Anti-redundancy</h2>
 * Each card declares a {@link Lever} — the single stat it primarily moves. The deck never offers
 * two cards sharing a lever, so a player is never asked to choose between two cards that do the
 * same thing; the choice is always between distinct build directions.
 */
public enum UpgradeCard {

    // ── OFFENSE ──────────────────────────────────────────────────────────────
    HOLLOW_POINTS(
        UpgradeCardPool.OFFENSE, Lever.DAMAGE,
        "HOLLOW POINTS", "+" + GameBalance.LEVEL_UP_DAMAGE_BONUS, "DAMAGE",
        "+" + GameBalance.LEVEL_UP_DAMAGE_BONUS + " flat damage every shot",
        /*str*/0, /*agi*/0, /*tgh*/0, /*mrk*/0, /*hp*/0, /*armor*/0, /*flatDmg*/GameBalance.LEVEL_UP_DAMAGE_BONUS),

    MARKSMAN_TRAINING(
        UpgradeCardPool.OFFENSE, Lever.MARKSMANSHIP,
        "MARKSMAN TRAINING", "+" + GameBalance.CARD_MARKSMANSHIP_STEP, "MARKSMANSHIP",
        "+12% ranged damage, +9% accuracy",
        0, 0, 0, GameBalance.CARD_MARKSMANSHIP_STEP, 0, 0, 0),

    BRUTAL_STRENGTH(
        UpgradeCardPool.OFFENSE, Lever.STRENGTH,
        "BRUTAL STRENGTH", "+" + GameBalance.CARD_STRENGTH_STEP, "STRENGTH",
        "+15% melee damage",
        GameBalance.CARD_STRENGTH_STEP, 0, 0, 0, 0, 0, 0),

    GLASS_CANNON(
        UpgradeCardPool.OFFENSE, Lever.DAMAGE,
        "GLASS CANNON", "+" + GameBalance.CARD_GLASS_CANNON_DAMAGE + " / -" + GameBalance.CARD_GLASS_CANNON_HP_COST, "DMG / HP",
        "+" + GameBalance.CARD_GLASS_CANNON_DAMAGE + " damage per shot, -" + GameBalance.CARD_GLASS_CANNON_HP_COST + " Max HP",
        0, 0, 0, 0, -GameBalance.CARD_GLASS_CANNON_HP_COST, 0, GameBalance.CARD_GLASS_CANNON_DAMAGE),

    // ── DEFENSE ──────────────────────────────────────────────────────────────
    COMBAT_ARMOR(
        UpgradeCardPool.DEFENSE, Lever.ARMOR,
        "COMBAT ARMOR", "+" + GameBalance.LEVEL_UP_ARMOR_BONUS, "MAX ARMOR",
        "+" + GameBalance.LEVEL_UP_ARMOR_BONUS + " Max Armor",
        0, 0, 0, 0, 0, GameBalance.LEVEL_UP_ARMOR_BONUS, 0),

    TOUGHENED_HIDE(
        UpgradeCardPool.DEFENSE, Lever.TOUGHNESS,
        "TOUGHENED HIDE", "+" + GameBalance.CARD_TOUGHNESS_STEP, "TOUGHNESS",
        "+5 Max HP, +1 damage reduction",
        0, 0, GameBalance.CARD_TOUGHNESS_STEP, 0, 0, 0, 0),

    // ── SUSTAIN ──────────────────────────────────────────────────────────────
    VITALITY(
        UpgradeCardPool.SUSTAIN, Lever.HEALTH,
        "VITALITY", "+" + GameBalance.LEVEL_UP_HP_BONUS, "MAX HP",
        "+" + GameBalance.LEVEL_UP_HP_BONUS + " Max HP",
        0, 0, 0, 0, GameBalance.LEVEL_UP_HP_BONUS, 0, 0),

    IRON_CONSTITUTION(
        UpgradeCardPool.SUSTAIN, Lever.HEALTH,
        "IRON CONSTITUTION", "+" + GameBalance.CARD_IRON_CONSTITUTION_HP + " / -" + GameBalance.CARD_IRON_CONSTITUTION_ARMOR, "HP / AR",
        "+" + GameBalance.CARD_IRON_CONSTITUTION_HP + " Max HP, -" + GameBalance.CARD_IRON_CONSTITUTION_ARMOR + " Max Armor",
        0, 0, 0, 0, GameBalance.CARD_IRON_CONSTITUTION_HP, -GameBalance.CARD_IRON_CONSTITUTION_ARMOR, 0),

    // ── UTILITY ──────────────────────────────────────────────────────────────
    EVASION_TRAINING(
        UpgradeCardPool.UTILITY, Lever.AGILITY,
        "EVASION TRAINING", "+" + GameBalance.CARD_AGILITY_STEP, "AGILITY",
        "+10% dodge, +15% faster actions",
        0, GameBalance.CARD_AGILITY_STEP, 0, 0, 0, 0, 0),

    RECKLESS_CHARGE(
        UpgradeCardPool.UTILITY, Lever.AGILITY,
        "RECKLESS CHARGE", "+" + GameBalance.CARD_RECKLESS_CHARGE_AGILITY + " / -" + GameBalance.CARD_RECKLESS_CHARGE_ARMOR, "AGI / AR",
        "+" + GameBalance.CARD_RECKLESS_CHARGE_AGILITY + " Agility, -" + GameBalance.CARD_RECKLESS_CHARGE_ARMOR + " Max Armor",
        0, GameBalance.CARD_RECKLESS_CHARGE_AGILITY, 0, 0, 0, -GameBalance.CARD_RECKLESS_CHARGE_ARMOR, 0);

    /**
     * The single stat a card primarily moves. The deck enforces that no two simultaneously-offered
     * cards share a lever, guaranteeing the three choices are always distinct build directions.
     */
    public enum Lever { STRENGTH, AGILITY, TOUGHNESS, MARKSMANSHIP, HEALTH, ARMOR, DAMAGE }

    public final UpgradeCardPool pool;
    public final Lever  lever;
    public final String displayName;
    /** Big headline value drawn on the card (e.g. "+3", "+10 / -18"). */
    public final String valueText;
    /** Short label under the value (e.g. "MARKSMANSHIP", "MAX HP"). */
    public final String effectLabel;
    /** One-line plain-language description of the card's effect. */
    public final String description;

    // Stat deltas applied when the card is chosen. Negative values are trade-off costs.
    public final int strengthDelta;
    public final int agilityDelta;
    public final int toughnessDelta;
    public final int marksmanshipDelta;
    public final int maxHealthDelta;
    public final int maxArmorDelta;
    public final int flatDamageDelta;

    UpgradeCard(UpgradeCardPool pool, Lever lever, String displayName, String valueText,
                String effectLabel, String description,
                int strengthDelta, int agilityDelta, int toughnessDelta, int marksmanshipDelta,
                int maxHealthDelta, int maxArmorDelta, int flatDamageDelta) {
        this.pool              = pool;
        this.lever             = lever;
        this.displayName       = displayName;
        this.valueText         = valueText;
        this.effectLabel       = effectLabel;
        this.description       = description;
        this.strengthDelta     = strengthDelta;
        this.agilityDelta      = agilityDelta;
        this.toughnessDelta    = toughnessDelta;
        this.marksmanshipDelta = marksmanshipDelta;
        this.maxHealthDelta    = maxHealthDelta;
        this.maxArmorDelta     = maxArmorDelta;
        this.flatDamageDelta   = flatDamageDelta;
    }

    /** True when the card pays for an above-budget gain with an explicit downside (a negative delta). */
    public boolean isTradeOff() {
        return strengthDelta < 0 || agilityDelta < 0 || toughnessDelta < 0 || marksmanshipDelta < 0
                || maxHealthDelta < 0 || maxArmorDelta < 0 || flatDamageDelta < 0;
    }

    /**
     * Computes this card's net power-point (PP) cost at the reference build, summing its offence
     * contribution (flat damage + STRENGTH/MARKSMANSHIP multipliers, priced in reference DPT) and
     * its survivability contribution (HP + armour + TOUGHNESS reduction + AGILITY dodge, priced in
     * reference eHP through {@link GameMath#effectiveHitPoints}). A trade-off card's downside enters
     * as negative PP, so its net still lands inside the budget band. Pure read — no side effects,
     * no LibGDX state — safe to call from the dev-only balance report.
     *
     * <p>This is the single authority on what a card "costs"; the deck never needs it at runtime
     * (every card is budget-equal by construction), but the balance report uses it to PROVE that.
     */
    public float estimatedPowerPoints() {
        // --- OFFENCE: convert flat damage and attribute multipliers into a reference-DPT gain.
        float meleeFraction  = strengthDelta     * GameBalance.STR_MELEE_PER_POINT * GameBalance.CARD_MELEE_UTILIZATION;
        float rangedFraction = marksmanshipDelta * GameBalance.MRK_DAMAGE_PER_POINT;
        float damagePerTurnGain = GameBalance.REFERENCE_PLAYER_DPT * (meleeFraction + rangedFraction)
                + flatDamageDelta * GameBalance.CARD_FLAT_DAMAGE_DPT_FRACTION;
        float offencePowerPoints = GameMath.damagePerTurnPowerPoints(
                damagePerTurnGain, GameBalance.REFERENCE_PLAYER_DPT);

        // --- SURVIVABILITY: fold HP, armour, toughness reduction and agility dodge into one eHP delta.
        float referenceAverageHit = GameBalance.CARD_PRICING_AVERAGE_HIT;
        float flatReduction = toughnessDelta * GameBalance.TGH_REDUCTION_PER_POINT;
        float dodgeChance   = Math.min(GameBalance.DODGE_CAP,
                Math.max(0f, agilityDelta * GameBalance.AGI_DODGE_PER_POINT));
        float newHitPoints  = GameBalance.PLAYER_MAX_HEALTH + maxHealthDelta
                + toughnessDelta * GameBalance.TGH_HP_PER_POINT;
        float newArmor      = GameBalance.PLAYER_MAX_ARMOR + maxArmorDelta;

        float baseEffectiveHitPoints = GameMath.effectiveHitPoints(
                GameBalance.PLAYER_MAX_HEALTH, GameBalance.PLAYER_MAX_ARMOR, 0f, 0f, referenceAverageHit);
        float newEffectiveHitPoints = GameMath.effectiveHitPoints(
                newHitPoints, newArmor, dodgeChance, flatReduction, referenceAverageHit);
        float survivabilityPowerPoints = GameMath.effectiveHitPointPowerPoints(
                newEffectiveHitPoints - baseEffectiveHitPoints, GameBalance.REFERENCE_PLAYER_EHP);

        return offencePowerPoints + survivabilityPowerPoints;
    }

    /**
     * Whether this card's net PP sits inside the budget band
     * [budget·(1-tolerance), budget·(1+tolerance)] — the contract every offerable card must pass.
     */
    public boolean isWithinBudgetBand() {
        float powerPoints = estimatedPowerPoints();
        float minimum = GameBalance.LEVEL_UP_BUDGET_PP * (1f - GameBalance.LEVEL_UP_BUDGET_TOLERANCE);
        float maximum = GameBalance.LEVEL_UP_BUDGET_PP * (1f + GameBalance.LEVEL_UP_BUDGET_TOLERANCE);
        return powerPoints >= minimum && powerPoints <= maximum;
    }
}
