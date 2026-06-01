package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.util.Constants;

/** Two-tier medical pickup system: stim-packs for drip-feed healing, field medkits for panic recovery. */
public enum MedicalTier {
    STIM(Constants.MEDKIT_STIM_HEAL),
    FIELD_MEDKIT(Constants.MEDKIT_FULL_HEAL);

    private final int healAmount;

    MedicalTier(int healAmount) {
        this.healAmount = healAmount;
    }

    public int getHealAmount() {
        return healAmount;
    }
}
