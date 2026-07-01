package ge.tbegvadze.toon3d.progression;

import ge.tbegvadze.toon3d.util.GameBalance;
import ge.tbegvadze.toon3d.util.GameMath;

/**
 * Player attribute data object.  Stores four core stat tracks and derives all
 * combat multipliers from them through {@link GameMath} formulas.
 *
 * <h2>Storage layout</h2>
 * Three {@code int[4]} arrays, each indexed by {@link Attribute#ordinal()}:
 * <ul>
 *   <li>{@code baseValue}        — set once from the chosen difficulty at run start; immutable.</li>
 *   <li>{@code permanentBonus}   — cumulative perk bumps; grows via {@link #addPermanent}.</li>
 *   <li>{@code equipmentBonus}   — recomputed on equip/unequip via {@link #setEquipmentBonus};
 *                                  never touched per frame.</li>
 * </ul>
 *
 * <h2>No allocation after construction</h2>
 * All arrays are pre-sized in the constructor.  {@link #getEffective} and the derived
 * getter methods are pure reads that allocate nothing — safe to call at fire time.
 *
 * <h2>Difficulty</h2>
 * The inner {@link Difficulty} enum maps to the per-difficulty base tables in
 * {@link GameBalance}.  MARINE is the balance anchor: its base values set the reference
 * point from which RECRUIT gets padding and NIGHTMARE/ULTRA get reduced starts.
 *
 * <h2>Stat caps</h2>
 * {@link #getEffective} clamps to [{@code GameBalance.STAT_MIN},
 * {@code GameBalance.STAT_CAP_<x>}].  Perks and equipment that try to push past the cap
 * are silently saturated — surplus never corrupts the stored value.
 */
public final class PlayerStats {

    // =========================================================================
    // Difficulty enum — chosen once at run start
    // =========================================================================

    /**
     * Difficulty tier chosen at run start.  Determines the initial base values for
     * each attribute (see GameBalance.STAT_BASE_<DIFFICULTY>_<ATTRIBUTE>).
     */
    public enum Difficulty {
        /** Easiest — generous all-round starting stats. */
        RECRUIT,
        /** Normal — the tuning reference loadout. */
        MARINE,
        /** Hard — lean starting stats; growth depends on perk choices. */
        NIGHTMARE,
        /** Brutal — minimal starting values; mastery required. */
        ULTRA
    }

    // =========================================================================
    // Attribute count — must match Attribute.values().length
    // =========================================================================

    private static final int ATTRIBUTE_COUNT = Attribute.values().length;

    // =========================================================================
    // Temp armor pool — Bulwark Rounds (ON_RELOAD) grants turn-limited armor
    // Fixed-size slots; no allocation after construction.
    // =========================================================================

    /** Remaining armor points in each temp-armor slot. */
    private final int[] tempArmorAmounts   = new int[GameBalance.BULWARK_TEMP_ARMOR_SLOTS];
    /** Turns left before each temp-armor slot expires; 0 = slot inactive. */
    private final int[] tempArmorTurnsLeft = new int[GameBalance.BULWARK_TEMP_ARMOR_SLOTS];

    // =========================================================================
    // Adrenal Surge pending buff — consumed on the very next fire activation
    // Not turn-counted; "next attack only" semantics via consume-on-read.
    // =========================================================================

    /** True when an Adrenal Surge bonus is waiting to be applied. */
    private boolean adrenalSurgePending    = false;
    /** The multiplier bonus stored by the most recent Adrenal Surge proc (e.g. 0.30). */
    private float   adrenalSurgeBonus      = 0f;

    // =========================================================================
    // Storage — three layers, indexed by Attribute.ordinal()
    // =========================================================================

    /** Difficulty-seeded base value; set once in the constructor, never changed. */
    private final int[] baseValue;

    /** Accumulated permanent perk bonuses; grown via addPermanent(). */
    private final int[] permanentBonus;

    /** Equipment-slot totals; fully recomputed on equip/unequip via setEquipmentBonus(). */
    private final int[] equipmentBonus;

    /** Per-attribute effective caps, indexed by Attribute.ordinal(). */
    private final int[] effectiveCap;

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * Creates a fresh PlayerStats seeded from the given difficulty.
     * All permanent and equipment bonuses start at zero.
     *
     * @param difficulty the chosen difficulty tier for this run
     */
    public PlayerStats(Difficulty difficulty) {
        baseValue      = new int[ATTRIBUTE_COUNT];
        permanentBonus = new int[ATTRIBUTE_COUNT];
        equipmentBonus = new int[ATTRIBUTE_COUNT];
        effectiveCap   = new int[ATTRIBUTE_COUNT];

        // Caps — indexed by Attribute ordinal; never change after construction.
        effectiveCap[Attribute.STRENGTH.ordinal()]     = GameBalance.STAT_CAP_STRENGTH;
        effectiveCap[Attribute.AGILITY.ordinal()]      = GameBalance.STAT_CAP_AGILITY;
        effectiveCap[Attribute.TOUGHNESS.ordinal()]    = GameBalance.STAT_CAP_TOUGHNESS;
        effectiveCap[Attribute.MARKSMANSHIP.ordinal()] = GameBalance.STAT_CAP_MARKSMANSHIP;

        // Seed base values from difficulty tables.
        switch (difficulty) {
            case RECRUIT:
                baseValue[Attribute.STRENGTH.ordinal()]     = GameBalance.STAT_BASE_RECRUIT_STR;
                baseValue[Attribute.AGILITY.ordinal()]      = GameBalance.STAT_BASE_RECRUIT_AGI;
                baseValue[Attribute.TOUGHNESS.ordinal()]    = GameBalance.STAT_BASE_RECRUIT_TGH;
                baseValue[Attribute.MARKSMANSHIP.ordinal()] = GameBalance.STAT_BASE_RECRUIT_MRK;
                break;
            case MARINE:
                baseValue[Attribute.STRENGTH.ordinal()]     = GameBalance.STAT_BASE_MARINE_STR;
                baseValue[Attribute.AGILITY.ordinal()]      = GameBalance.STAT_BASE_MARINE_AGI;
                baseValue[Attribute.TOUGHNESS.ordinal()]    = GameBalance.STAT_BASE_MARINE_TGH;
                baseValue[Attribute.MARKSMANSHIP.ordinal()] = GameBalance.STAT_BASE_MARINE_MRK;
                break;
            case NIGHTMARE:
                baseValue[Attribute.STRENGTH.ordinal()]     = GameBalance.STAT_BASE_NIGHTMARE_STR;
                baseValue[Attribute.AGILITY.ordinal()]      = GameBalance.STAT_BASE_NIGHTMARE_AGI;
                baseValue[Attribute.TOUGHNESS.ordinal()]    = GameBalance.STAT_BASE_NIGHTMARE_TGH;
                baseValue[Attribute.MARKSMANSHIP.ordinal()] = GameBalance.STAT_BASE_NIGHTMARE_MRK;
                break;
            case ULTRA:
                baseValue[Attribute.STRENGTH.ordinal()]     = GameBalance.STAT_BASE_ULTRA_STR;
                baseValue[Attribute.AGILITY.ordinal()]      = GameBalance.STAT_BASE_ULTRA_AGI;
                baseValue[Attribute.TOUGHNESS.ordinal()]    = GameBalance.STAT_BASE_ULTRA_TGH;
                baseValue[Attribute.MARKSMANSHIP.ordinal()] = GameBalance.STAT_BASE_ULTRA_MRK;
                break;
        }
    }

    // =========================================================================
    // Core read accessors
    // =========================================================================

    /**
     * Returns the immutable base value for the given attribute (difficulty-seeded).
     */
    public int getBase(Attribute attribute) {
        return baseValue[attribute.ordinal()];
    }

    /**
     * Returns the effective (clamped) value for the given attribute.
     * effective = clamp(base + permanent + equipment, STAT_MIN, STAT_CAP_<x>)
     * This is the value every formula in GameMath must read.
     */
    public int getEffective(Attribute attribute) {
        int ordinal = attribute.ordinal();
        int total   = baseValue[ordinal] + permanentBonus[ordinal] + equipmentBonus[ordinal];
        return Math.max(GameBalance.STAT_MIN, Math.min(effectiveCap[ordinal], total));
    }

    // =========================================================================
    // Mutation — perk hook and equipment hook
    // =========================================================================

    /**
     * Applies a permanent perk bonus to the given attribute.
     * The delta is accumulated into {@code permanentBonus}; the result is clamped
     * to the stat cap by {@link #getEffective} at read time — no overflow stored.
     * Negative deltas are allowed (e.g. a curse or debuff perk).
     *
     * @param attribute the attribute to modify
     * @param delta     the signed bonus to add (typically +1 per perk pick)
     */
    public void addPermanent(Attribute attribute, int delta) {
        permanentBonus[attribute.ordinal()] += delta;
    }

    /**
     * Replaces the equipment bonus for the given attribute with the new total.
     * Call this whenever the player's equipped items change (equip or unequip).
     * The caller is responsible for summing all worn-item modifiers before calling.
     * Clamping happens at {@link #getEffective} read time.
     *
     * @param attribute the attribute whose equipment total has changed
     * @param total     the new total equipment bonus (sum of all worn items)
     */
    public void setEquipmentBonus(Attribute attribute, int total) {
        equipmentBonus[attribute.ordinal()] = total;
    }

    // =========================================================================
    // Derived multiplier getters — delegate all math to GameMath
    // =========================================================================

    /**
     * Melee damage multiplier derived from STRENGTH.
     * STRENGTH 0 (STAT_REFERENCE = 0) → 1.0× (no bonus).
     * Each point above 0 adds STR_MELEE_PER_POINT (5%) to the multiplier.
     */
    public float getMeleeDamageMultiplier() {
        return GameMath.meleeDamageMultiplier(
                getEffective(Attribute.STRENGTH),
                GameBalance.STAT_REFERENCE,
                GameBalance.STR_MELEE_PER_POINT);
    }

    /**
     * Ranged damage multiplier derived from MARKSMANSHIP.
     * Each point adds MRK_DAMAGE_PER_POINT (4%) to the multiplier.
     */
    public float getRangedDamageMultiplier() {
        return GameMath.rangedDamageMultiplier(
                getEffective(Attribute.MARKSMANSHIP),
                GameBalance.STAT_REFERENCE,
                GameBalance.MRK_DAMAGE_PER_POINT);
    }

    /**
     * Accuracy multiplier derived from MARKSMANSHIP.
     * Used to tighten shotgun spread / reduce hit-chance rolls for relevant weapons.
     * Each point adds MRK_ACCURACY_PER_POINT (3%) to the multiplier.
     */
    public float getAccuracyMultiplier() {
        return GameMath.accuracyMultiplier(
                getEffective(Attribute.MARKSMANSHIP),
                GameBalance.STAT_REFERENCE,
                GameBalance.MRK_ACCURACY_PER_POINT);
    }

    /**
     * Action duration multiplier derived from AGILITY.
     * Values below 1.0 shorten animation seconds (snappier feel).
     * Clamped to [AGI_MIN_DURATION_MULT, 1.0] so animation never becomes unreadable.
     * This changes animation speed ONLY — one action is still exactly one world turn.
     */
    public float getActionDurationMultiplier() {
        return GameMath.actionDurationMultiplier(
                getEffective(Attribute.AGILITY),
                GameBalance.STAT_REFERENCE,
                GameBalance.AGI_SPEED_PER_POINT,
                GameBalance.AGI_MIN_DURATION_MULT);
    }

    /**
     * Dodge chance derived from AGILITY.
     * Rolled in Player.applyDamage before armour absorption — success skips all damage.
     * Clamped to [0, DODGE_CAP] (max 35%) so high-AGI builds never become immune.
     */
    public float getDodgeChance() {
        return GameMath.dodgeChance(
                getEffective(Attribute.AGILITY),
                GameBalance.STAT_REFERENCE,
                GameBalance.AGI_DODGE_PER_POINT,
                GameBalance.DODGE_CAP);
    }

    /**
     * Flat max-HP bonus derived from TOUGHNESS.
     * Added to Player.maxHealth whenever TOUGHNESS changes (perk or equipment).
     * Raising maxHealth does NOT auto-heal; current HP is unchanged.
     */
    public int getMaxHealthBonus() {
        return GameMath.maxHealthBonus(
                getEffective(Attribute.TOUGHNESS),
                GameBalance.STAT_REFERENCE,
                GameBalance.TGH_HP_PER_POINT);
    }

    /**
     * Flat damage reduction derived from TOUGHNESS.
     * Applied after armour absorption on every HP-bound incoming hit.
     * Floored at TGH_MIN_DAMAGE (1) by the caller so chip damage still threatens.
     */
    public int getFlatDamageReduction() {
        return GameMath.flatDamageReduction(
                getEffective(Attribute.TOUGHNESS),
                GameBalance.STAT_REFERENCE,
                GameBalance.TGH_REDUCTION_PER_POINT);
    }

    // =========================================================================
    // Temp armor — Bulwark Rounds (ON_RELOAD)
    // Consumed before regular armor when the player takes damage.
    // =========================================================================

    /**
     * Adds a block of temporary armor that expires after {@code turns} player-action ticks.
     * If all slots are occupied, overwrites the slot with the fewest turns remaining.
     * No allocation — operates on the pre-sized arrays from construction.
     *
     * @param amount amount of temporary armor points to add
     * @param turns  number of player-action ticks before this block expires
     */
    public void addTempArmor(int amount, int turns) {
        for (int slotIndex = 0; slotIndex < GameBalance.BULWARK_TEMP_ARMOR_SLOTS; slotIndex++) {
            if (tempArmorTurnsLeft[slotIndex] <= 0) {
                tempArmorAmounts[slotIndex]   = amount;
                tempArmorTurnsLeft[slotIndex] = turns;
                return;
            }
        }
        // All slots occupied — overwrite the one expiring soonest.
        int shortestSlot = 0;
        for (int slotIndex = 1; slotIndex < GameBalance.BULWARK_TEMP_ARMOR_SLOTS; slotIndex++) {
            if (tempArmorTurnsLeft[slotIndex] < tempArmorTurnsLeft[shortestSlot]) {
                shortestSlot = slotIndex;
            }
        }
        tempArmorAmounts[shortestSlot]   = amount;
        tempArmorTurnsLeft[shortestSlot] = turns;
    }

    /**
     * Consumes temp armor to absorb incoming damage, slot by slot until the damage is
     * fully absorbed or all slots are exhausted. Returns the total amount absorbed.
     * Partial slot absorption is tracked — a slot is only zeroed once its pool hits 0.
     * Called from {@code Player.applyDamage()} before regular armor absorption.
     *
     * @param incomingDamage the damage about to be applied
     * @return               the amount absorbed by temp armor (≤ incomingDamage)
     */
    public int consumeTempArmor(int incomingDamage) {
        int totalAbsorbed   = 0;
        int remainingDamage = incomingDamage;
        for (int slotIndex = 0; slotIndex < GameBalance.BULWARK_TEMP_ARMOR_SLOTS; slotIndex++) {
            if (tempArmorTurnsLeft[slotIndex] > 0 && tempArmorAmounts[slotIndex] > 0 && remainingDamage > 0) {
                int absorbed = Math.min(remainingDamage, tempArmorAmounts[slotIndex]);
                tempArmorAmounts[slotIndex] -= absorbed;
                remainingDamage             -= absorbed;
                totalAbsorbed               += absorbed;
            }
        }
        return totalAbsorbed;
    }

    /**
     * Decrements the turn counter for all active temp-armor slots.
     * Slots that reach 0 turns are marked inactive (their remaining amount is zeroed).
     * Call once per player-action tick from a TickSubscriber registered in World.
     */
    public void tickTempArmor() {
        for (int slotIndex = 0; slotIndex < GameBalance.BULWARK_TEMP_ARMOR_SLOTS; slotIndex++) {
            if (tempArmorTurnsLeft[slotIndex] > 0) {
                tempArmorTurnsLeft[slotIndex]--;
                if (tempArmorTurnsLeft[slotIndex] == 0) {
                    tempArmorAmounts[slotIndex] = 0;
                }
            }
        }
    }

    // =========================================================================
    // Credits — accumulated by Credit Fang ability; spendable in the shop (order-11)
    // =========================================================================

    private int credits = 0;

    /**
     * Adds the given amount to the player's credit balance.
     * Called by AbilityResolver when Credit Fang procs on a kill.
     * Credits are persistent across floors and spendable in the shop (order-11).
     *
     * @param amount credits to add; must be >= 0
     */
    public void addCredits(int amount) {
        if (amount > 0) credits += amount;
    }

    /** Returns the player's current credit balance. */
    public int getCredits() {
        return credits;
    }

    /**
     * Spends credits at the shop (shop_order_2). Deducts the amount only when the balance can
     * cover it, so callers never drive the balance negative.
     *
     * @param amount credits to spend; must be >= 0
     * @return true and deducts when affordable; false and leaves the balance unchanged otherwise.
     *         A zero amount is a no-op success; a negative amount is rejected (returns false).
     */
    public boolean spendCredits(int amount) {
        if (amount < 0) return false;   // programming error — never mutate the balance
        if (amount == 0) return true;   // nothing to deduct
        if (credits < amount) return false;
        credits -= amount;
        return true;
    }

    // =========================================================================
    // Adrenal Surge pending buff — consumed on the very next weapon fire
    // "Next attack only" semantics: stored on kill, read-and-clear on fire.
    // =========================================================================

    /**
     * Stores an Adrenal Surge damage bonus to apply on the player's next attack.
     * Overwrites any existing pending surge (only one surge is active at a time).
     *
     * @param bonusMultiplier the fractional bonus, e.g. 0.30 for +30% damage
     */
    public void applyAdrenalSurge(float bonusMultiplier) {
        adrenalSurgePending = true;
        adrenalSurgeBonus   = bonusMultiplier;
    }

    /**
     * Returns the pending Adrenal Surge multiplier (1.0 + bonus) and clears the buff.
     * Returns 1.0 if no surge is pending.
     * Must be called exactly once per fire activation (from AbilityResolver.onFire).
     *
     * @return multiplier to apply to the next attack's damage (e.g. 1.30); 1.0 if inactive
     */
    public float pollAdrenalSurgeMultiplier() {
        if (adrenalSurgePending) {
            adrenalSurgePending = false;
            float multiplier    = 1.0f + adrenalSurgeBonus;
            adrenalSurgeBonus   = 0f;
            return multiplier;
        }
        return 1.0f;
    }
}
