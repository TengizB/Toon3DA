package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.level.KeycardColor;
import ge.tbegvadze.toon3d.util.Constants;

import java.util.EnumSet;

/**
 * Tracks items the player is carrying during a level.
 * Keycards reset on level change (per classic Doom convention).
 */
public class PlayerInventory {

    private final EnumSet<KeycardColor> keycards = EnumSet.noneOf(KeycardColor.class);
    private Weapon equippedWeapon;

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

    /** Returns the currently equipped weapon, or null if the player is unarmed. */
    public Weapon getEquippedWeapon() {
        return equippedWeapon;
    }

    public void setEquippedWeapon(Weapon weapon) {
        equippedWeapon = weapon;
    }

    public int getStimCharges()    { return stimCharges; }
    public int getMedkitCharges()  { return medkitCharges; }
    public int getTotalMedicalCharges() { return stimCharges + medkitCharges; }

    public boolean hasAnyMedical() {
        return stimCharges > 0 || medkitCharges > 0;
    }

    public boolean canAcceptMedical(MedicalTier tier) {
        if (getTotalMedicalCharges() >= Constants.MEDKIT_TOTAL_CARRY_CAP) return false;
        if (tier == MedicalTier.FIELD_MEDKIT && medkitCharges >= Constants.MEDKIT_FULL_CARRY_CAP) return false;
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
            int healthAfterStim = currentHealth + Constants.MEDKIT_STIM_HEAL;
            if (healthAfterStim >= maxHealth || medkitCharges == 0) return MedicalTier.STIM;
        }
        return MedicalTier.FIELD_MEDKIT;
    }

    /** Decrements the stash and returns the HP value to restore. */
    public int spendHeal(MedicalTier tier) {
        if (tier == MedicalTier.STIM && stimCharges > 0) {
            stimCharges--;
            return Constants.MEDKIT_STIM_HEAL;
        }
        if (tier == MedicalTier.FIELD_MEDKIT && medkitCharges > 0) {
            medkitCharges--;
            return Constants.MEDKIT_FULL_HEAL;
        }
        return 0;
    }
}
