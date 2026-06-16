package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.level.KeycardColor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import ge.tbegvadze.toon3d.util.WeaponConstants;
import ge.tbegvadze.toon3d.util.ItemConstants;

/**
 * Tracks items the player is carrying during a level.
 * Keycards reset on level change (per classic Doom convention).
 */
public class PlayerInventory {

    private final EnumSet<KeycardColor> keycards = EnumSet.noneOf(KeycardColor.class);

    // Arsenal is the legacy backing list; the Loadout is the authoritative source for the
    // equipped weapon. Both are kept in sync via setArsenal().
    private final List<Weapon> arsenal = new ArrayList<>();
    private int equippedWeaponIndex = 0;
    private Loadout loadout = new Loadout();

    // Melee slot — separate from the ranged loadout; always holds at least a Fist.
    // meleeSelected == true means the active weapon is meleeWeapon, not loadout.active().
    private MeleeWeapon meleeWeapon  = null;
    private boolean     meleeSelected = false;

    // Medical stash — combined cap across both tiers.
    private int stimCharges   = 0;
    private int medkitCharges = 0;

    public boolean hasKeycard(KeycardColor color) {
        return keycards.contains(color);
    }

    public void addKeycard(KeycardColor color) {
        keycards.add(color);
    }

    public void resetKeycards() {
        keycards.clear();
    }

    /**
     * Replaces the entire weapon arsenal and re-seeds the loadout from the new list.
     * The first up-to-WEAPON_SLOT_COUNT weapons are placed into loadout slots in order.
     * The loadout becomes the authoritative source for the equipped weapon after this call.
     */
    public void setArsenal(List<Weapon> weapons) {
        arsenal.clear();
        arsenal.addAll(weapons);
        equippedWeaponIndex = 0;
        meleeSelected = false;
        loadout = new Loadout();
        for (int slotIndex = 0; slotIndex < Math.min(weapons.size(), WeaponConstants.WEAPON_SLOT_COUNT); slotIndex++) {
            loadout.tryEquip(weapons.get(slotIndex));
        }
    }

    public List<Weapon> getArsenal() {
        return Collections.unmodifiableList(arsenal);
    }

    // -------------------------------------------------------------------------
    // Melee slot — always present, never occupies a ranged loadout slot
    // -------------------------------------------------------------------------

    /** Sets the melee weapon. The Fist (default) should be set at run start and never removed. */
    public void setMeleeWeapon(MeleeWeapon weapon) {
        if (weapon != null) {
            this.meleeWeapon = weapon;
        }
    }

    /** Returns the melee weapon (always non-null after run start once Fist is set). */
    public MeleeWeapon getMeleeWeapon() {
        return meleeWeapon;
    }

    /** True when the player has the melee slot selected instead of a ranged slot. */
    public boolean isMeleeSelected() {
        return meleeSelected;
    }

    // -------------------------------------------------------------------------
    // Equipped weapon — returns melee or ranged depending on current selection
    // -------------------------------------------------------------------------

    /**
     * Returns the currently equipped weapon: either the melee weapon (when melee is selected
     * and a melee weapon is present) or the active ranged loadout slot.
     */
    public Weapon getEquippedWeapon() {
        if (meleeSelected && meleeWeapon != null) return meleeWeapon;
        return loadout.active();
    }

    /** Sets the arsenal to a single weapon. Prefer setArsenal() for multi-weapon runs. */
    public void setEquippedWeapon(Weapon weapon) {
        setArsenal(java.util.List.of(weapon));
    }

    /**
     * Cycles weapon selection: ranged slot 0 → ranged slot 1 → … → melee → ranged slot 0.
     * Visits each filled ranged slot in order (no wrap), then melee, then back to slot 0.
     * This ensures melee is always reachable regardless of how many ranged slots are full.
     * If the melee slot is currently selected, wraps back to the first filled ranged slot.
     * Returns the newly active weapon.
     */
    public Weapon switchToNextWeapon() {
        if (meleeSelected) {
            // Melee → first filled ranged slot (search forward from the last slot so index 0 is found first)
            int firstFilled = loadout.nextFilledSlot(loadout.getSlotCount() - 1);
            if (loadout.getSlot(firstFilled) != null) {
                meleeSelected = false;
                loadout.selectSlot(firstFilled);
                return loadout.active();
            }
            // No ranged weapon available — stay on melee
            return meleeWeapon;
        }

        // Ranged → find the first filled slot strictly after the current index (no wrap).
        // Not wrapping ensures that once we exhaust all later slots we fall through to melee,
        // rather than cycling back to slot 0 and skipping melee entirely.
        int currentIndex = loadout.getActiveSlotIndex();
        for (int slotIndex = currentIndex + 1; slotIndex < loadout.getSlotCount(); slotIndex++) {
            if (loadout.getSlot(slotIndex) != null) {
                loadout.selectSlot(slotIndex);
                return loadout.active();
            }
        }

        // No filled slots after current — switch to melee if available
        if (meleeWeapon != null) {
            meleeSelected = true;
            return meleeWeapon;
        }

        // No melee — wrap back to the first filled ranged slot
        int firstFilled = loadout.nextFilledSlot(loadout.getSlotCount() - 1);
        if (loadout.getSlot(firstFilled) != null) {
            loadout.selectSlot(firstFilled);
            return loadout.active();
        }

        // Completely empty (no ranged, no melee) — return melee as last resort.
        // May be null if the melee slot was never initialised (edge-case / test scenario);
        // callers must guard against null.
        return meleeWeapon;
    }

    /**
     * Clears all loadout slots without changing the arsenal or weapon configuration.
     * Used by the start room so the player begins completely unarmed.
     * Melee selection is cleared; the player starts without an active weapon.
     */
    public void clearLoadout() {
        loadout        = new Loadout();
        meleeSelected  = false;
    }

    /**
     * Switches away from melee selection so the active ranged loadout slot becomes the
     * equipped weapon. Called after the player takes or swaps in a new ranged weapon so
     * the HUD and fire action reflect the newly equipped gun immediately.
     */
    public void selectRangedActive() {
        meleeSelected = false;
    }

    /** Provides direct access to the loadout for slot-selection UI and input handling. */
    public Loadout getLoadout() { return loadout; }

    public int getStimCharges()    { return stimCharges; }
    public int getMedkitCharges()  { return medkitCharges; }
    public int getTotalMedicalCharges() { return stimCharges + medkitCharges; }

    public boolean hasAnyMedical() {
        return stimCharges > 0 || medkitCharges > 0;
    }

    public boolean canAcceptMedical(MedicalTier tier) {
        if (getTotalMedicalCharges() >= ItemConstants.MEDKIT_TOTAL_CARRY_CAP) return false;
        if (tier == MedicalTier.FIELD_MEDKIT && medkitCharges >= ItemConstants.MEDKIT_FULL_CARRY_CAP) return false;
        return true;
    }

    /** Attempts to add one charge. Returns false (leaving the pickup on the floor) when the stash is full. */
    public boolean addMedical(MedicalTier tier) {
        if (!canAcceptMedical(tier)) return false;
        if (tier == MedicalTier.STIM) stimCharges++;
        else                          medkitCharges++;
        return true;
    }

    /**
     * Selects the tier to spend, preferring the smallest charge that brings the marine to or
     * above (maxHealth - slack). Spends a stim if it alone tops the player off; otherwise
     * spends a medkit. Always returns non-null when hasAnyMedical() is true.
     */
    public MedicalTier chooseHealTier(int currentHealth, int maxHealth) {
        if (stimCharges > 0) {
            int healthAfterStim = currentHealth + ItemConstants.MEDKIT_STIM_HEAL;
            if (healthAfterStim >= maxHealth || medkitCharges == 0) return MedicalTier.STIM;
        }
        return MedicalTier.FIELD_MEDKIT;
    }

    /** Decrements the stash and returns the HP value to restore. */
    public int spendHeal(MedicalTier tier) {
        if (tier == MedicalTier.STIM && stimCharges > 0) {
            stimCharges--;
            return ItemConstants.MEDKIT_STIM_HEAL;
        }
        if (tier == MedicalTier.FIELD_MEDKIT && medkitCharges > 0) {
            medkitCharges--;
            return ItemConstants.MEDKIT_FULL_HEAL;
        }
        return 0;
    }
}
