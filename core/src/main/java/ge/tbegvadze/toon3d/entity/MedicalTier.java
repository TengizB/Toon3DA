package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.GameMath;

/** Two-tier medical pickup system: stim-packs for drip-feed healing, field medkits for panic recovery. */
public enum MedicalTier {
    STIM(BalanceConfig.MEDKIT_STIM_HEAL_FRACTION, ItemType.MEDKIT_SMALL),
    FIELD_MEDKIT(BalanceConfig.MEDKIT_FULL_HEAL_FRACTION, ItemType.MEDKIT_LARGE);

    private final float healFraction;
    private final ItemType itemType;

    MedicalTier(float healFraction, ItemType itemType) {
        this.healFraction = healFraction;
        this.itemType     = itemType;
    }

    /** Fraction of the player's maximum HP this tier restores. */
    public float getHealFraction() {
        return healFraction;
    }

    /** HP restored against the given current maximum HP (resolved at the moment of use). */
    public int healAmountFor(int maxHealth) {
        return GameMath.fractionOfMaximum(maxHealth, healFraction);
    }

    /** The slotted inventory item that backs this tier's carried stash. */
    public ItemType getItemType() {
        return itemType;
    }
}
