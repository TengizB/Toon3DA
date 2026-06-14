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
        loadout = new Loadout();
        for (int slotIndex = 0; slotIndex < Math.min(weapons.size(), WeaponConstants.WEAPON_SLOT_COUNT); slotIndex++) {
            loadout.tryEquip(weapons.get(slotIndex));
        }
    }

    public List<Weapon> getArsenal() {
        return Collections.unmodifiableList(arsenal);
    }

    /**
     * Returns the currently equipped weapon from the loadout, or null if the player is unarmed.
     * Delegates entirely to the loadout so all callers share the same selection state.
     */
    public Weapon getEquippedWeapon() {
        return loadout.active();
    }

    /** Sets the arsenal to a single weapon. Prefer setArsenal() for multi-weapon runs. */
    public void setEquippedWeapon(Weapon weapon) {
        setArsenal(java.util.List.of(weapon));
    }

    /**
     * Advances to the next filled loadout slot (wraps around).
     * Returns the newly equipped weapon, or the current weapon if only one slot is filled.
     */
    public Weapon switchToNextWeapon() {
        int nextSlotIndex = loadout.nextFilledSlot(loadout.getActiveSlotIndex());
        loadout.selectSlot(nextSlotIndex);
        return loadout.active();
    }

    /**
     * Clears all loadout slots without changing the arsenal or weapon configuration.
     * Used by the start room so the player begins completely unarmed.
     */
    public void clearLoadout() {
        loadout = new Loadout();
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
