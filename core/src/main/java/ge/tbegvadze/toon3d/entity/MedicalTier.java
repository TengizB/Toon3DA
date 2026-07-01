package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.util.ItemConstants;

/** Two-tier medical pickup system: stim-packs for drip-feed healing, field medkits for panic recovery. */
public enum MedicalTier {
    STIM(ItemConstants.MEDKIT_STIM_HEAL, ItemType.MEDKIT_SMALL),
    FIELD_MEDKIT(ItemConstants.MEDKIT_FULL_HEAL, ItemType.MEDKIT_LARGE);

    private final int healAmount;
    private final ItemType itemType;

    MedicalTier(int healAmount, ItemType itemType) {
        this.healAmount = healAmount;
        this.itemType   = itemType;
    }

    public int getHealAmount() {
        return healAmount;
    }

    /** The slotted inventory item that backs this tier's carried stash. */
    public ItemType getItemType() {
        return itemType;
    }
}
