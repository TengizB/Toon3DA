package ge.tbegvadze.toon3d.shop;

import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.item.Inventory;
import ge.tbegvadze.toon3d.item.ItemStack;
import ge.tbegvadze.toon3d.item.ItemType;

/**
 * shop_order_4 bridge for the two SUPPLY (resource-relief) categories the UAC Fabricator sells —
 * {@code AMMO} and {@code MEDKIT}. It is the {@link ShopEffectApplier} for those categories and
 * contains NO new ammo/heal math: a purchase routes straight into the SAME inventory add paths a
 * ground pickup uses ({@link Inventory#addAmmo} / {@link Inventory#tryAdd}), so bought supplies
 * behave exactly like found ones — reserve caps, stack caps, and overflow rules all still bind.
 *
 * <p>Weapon and player-ability categories are not handled here — a different applier owns those;
 * {@link #canApply}/{@link #apply} return false / no-op for them.
 */
public final class ShopSupplyService implements ShopEffectApplier {

    private final Inventory inventory;

    public ShopSupplyService(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public boolean canApply(ShopEntry entry) {
        if (entry == null || inventory == null) return false;
        switch (entry.category) {
            case AMMO:
                // Reject when the credits would be 100% wasted: either the reserve is ALREADY full
                // for this type, OR the inventory has no room to hold a stack of it (all slots taken
                // and no existing stack of this ammo with headroom). Without the room check a paid
                // ammo box could be charged-for but silently dropped by tryAdd — the "bought ammo but
                // never received it" bug. A purchase that merely overflows the reserve cap is still
                // allowed (the excess is wasted, the player's choice, matching ground pickups).
                if (!(entry.payload instanceof AmmoType)) return false;
                AmmoType ammoType = (AmmoType) entry.payload;
                if (inventory.countOf(ammoType.getItemType()) >= ammoType.getReserveCap()) return false;
                return hasRoomFor(ammoType.getItemType());
            case MEDKIT:
                // Offer only if the inventory can hold at least one more of this medkit item.
                if (!(entry.payload instanceof ItemType)) return false;
                return hasRoomFor((ItemType) entry.payload);
            default:
                return false;
        }
    }

    @Override
    public void apply(ShopEntry entry) {
        if (entry == null || inventory == null) return;
        switch (entry.category) {
            case AMMO:
                if (entry.payload instanceof AmmoType && entry.quantity > 0) {
                    // addAmmo clamps to the reserve cap; overflow past the cap is wasted (as above).
                    inventory.addAmmo((AmmoType) entry.payload, entry.quantity);
                }
                break;
            case MEDKIT:
                if (entry.payload instanceof ItemType) {
                    inventory.tryAdd((ItemType) entry.payload, 1);
                }
                break;
            default:
                // Not a supply offer — handled by another applier.
                break;
        }
    }

    /**
     * True when one more unit of {@code itemType} would fit: either an existing stack has headroom
     * below its max, or a free slot is available. Pure read — mutates nothing (mirrors the room test
     * {@link Inventory#tryAdd} performs, without adding).
     */
    private boolean hasRoomFor(ItemType itemType) {
        if (itemType.isStackable()) {
            // Some room remains as long as at least one existing stack is below its max.
            for (int slotIndex = 0; slotIndex < inventory.getSlotCount(); slotIndex++) {
                ItemStack slot = inventory.getSlot(slotIndex);
                if (slot.getType() == itemType && slot.getQuantity() < itemType.getMaxStackSize()) {
                    return true;
                }
            }
        }
        return inventory.firstFreeSlot() != -1;
    }
}
