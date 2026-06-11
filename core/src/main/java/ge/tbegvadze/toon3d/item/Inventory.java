package ge.tbegvadze.toon3d.item;

import ge.tbegvadze.toon3d.util.ItemConstants;

/**
 * Fixed-size slot-based inventory for the marine's carried items.
 *
 * Capacity: ItemConstants.INVENTORY_SLOT_COUNT slots (10).
 * The slot array is pre-allocated once; ItemStack objects are mutated in place —
 * zero new allocations occur on any add/remove/use/query path.
 *
 * Design decisions:
 *  - Ammo lives here as AMMO_* ItemType stacks (Order 6 redesign).
 *    Weapons spend ammo via spend(ItemType, int) on every reload.
 *  - HP and armour are pools, not items.
 *  - The equipped weapon slot index is tracked here so WeaponReloadSubscriber
 *    and other systems can call getEquippedWeaponSlot() without coupling to
 *    the old PlayerInventory weapon list.
 *  - use(slotIndex) is a stub; the actual effect routing (heal, equip, etc.)
 *    will be wired in the consuming systems (Order 3 health, weapon system).
 */
public final class Inventory {

    /** Pre-allocated fixed-size slot array. Never replaced; slots are mutated in place. */
    private final ItemStack[] slots;

    /**
     * Index into slots[] of the currently equipped weapon.
     * -1 means no weapon is equipped (unarmed or no weapon item in inventory).
     */
    private int equippedWeaponSlot = -1;

    /** Constructs an empty inventory with all slots pre-allocated. */
    public Inventory() {
        slots = new ItemStack[ItemConstants.INVENTORY_SLOT_COUNT];
        for (int slotIndex = 0; slotIndex < ItemConstants.INVENTORY_SLOT_COUNT; slotIndex++) {
            slots[slotIndex] = new ItemStack();
        }
    }

    // =========================================================================
    // Mutation — all paths are allocation-free
    // =========================================================================

    /**
     * Attempts to add the given quantity of itemType to this inventory.
     *
     * Algorithm:
     *  1. If itemType is stackable, scan all slots for an existing stack of that type
     *     with room below its maxStackSize. Fill as much as possible, spilling overflow
     *     into additional free slots if needed.
     *  2. If itemType is not stackable (or no existing stack was found / had no room),
     *     place into the first free slot.
     *  3. Return false if no free slot could be found to absorb the full or partial quantity.
     *     (A partial add is still performed when possible — the return value indicates
     *     whether ALL of the requested quantity was accepted.)
     *
     * @param itemType the type to add; must not be null
     * @param quantity how many to add; must be >= 1
     * @return true if the full quantity was added; false if at least one unit was rejected
     */
    public boolean tryAdd(ItemType itemType, int quantity) {
        if (itemType == null) throw new IllegalArgumentException("itemType must not be null");
        if (quantity < 1)     throw new IllegalArgumentException("quantity must be >= 1");

        int remaining = quantity;

        if (itemType.isStackable()) {
            // Pass 1: merge into existing stacks that have room.
            for (int slotIndex = 0; slotIndex < slots.length && remaining > 0; slotIndex++) {
                ItemStack slot = slots[slotIndex];
                if (slot.type == itemType) {
                    int room = itemType.getMaxStackSize() - slot.quantity;
                    if (room > 0) {
                        int absorbed = Math.min(room, remaining);
                        slot.quantity += absorbed;
                        remaining     -= absorbed;
                    }
                }
            }
        }

        // Pass 2: place remaining units into free slots (one slot per maxStackSize chunk).
        while (remaining > 0) {
            int freeSlotIndex = firstFreeSlot();
            if (freeSlotIndex == -1) {
                // No free slot — could not absorb everything.
                return false;
            }
            int placed = Math.min(itemType.getMaxStackSize(), remaining);
            slots[freeSlotIndex].occupy(itemType, placed);
            remaining -= placed;
        }

        return true;
    }

    /**
     * Reduces the quantity at slotIndex by the given amount.
     * Clears the slot if quantity reaches zero or below.
     * Handles the equipped weapon slot: if the cleared slot was the equipped weapon,
     * equippedWeaponSlot is reset to -1.
     *
     * @param slotIndex index of the slot to reduce; must be in [0, INVENTORY_SLOT_COUNT)
     * @param quantity  how many units to remove; must be >= 1
     */
    public void remove(int slotIndex, int quantity) {
        validateSlotIndex(slotIndex);
        if (quantity < 1) throw new IllegalArgumentException("quantity must be >= 1");

        ItemStack slot = slots[slotIndex];
        if (slot.isEmpty()) return;

        slot.quantity -= quantity;
        if (slot.quantity <= 0) {
            slot.clear();
            if (equippedWeaponSlot == slotIndex) {
                equippedWeaponSlot = -1;
            }
        }
    }

    /**
     * Uses the item at slotIndex.
     *
     * This is a stub for Order 3: the actual effect routing (heal path for
     * CONSUMABLE, equip path for WEAPON, etc.) will be wired by the consuming
     * systems once they exist. For now, calling use() on a CONSUMABLE decrements
     * its stack by 1 so the data model stays consistent even before the full
     * effect system is live.
     *
     * WEAPON items are not consumed — they are equipped (equippedWeaponSlot is
     * updated) and the slot is not decremented.
     *
     * @param slotIndex the slot whose item to use
     */
    public void use(int slotIndex) {
        validateSlotIndex(slotIndex);
        ItemStack slot = slots[slotIndex];
        if (slot.isEmpty()) return;

        if (slot.type.getCategory() == ItemCategory.WEAPON) {
            // Equipping a weapon is a free action (no turn cost); just update the pointer.
            equippedWeaponSlot = slotIndex;
            return;
        }

        if (slot.type.getCategory() == ItemCategory.CONSUMABLE) {
            // Consume one unit. Full effect routing is wired by Order 3 health system.
            remove(slotIndex, 1);
        }

        // KEY_ITEM, MOD, MISC: not usable from the menu in this order; no-op for now.
    }

    /**
     * Removes up to the requested quantity of itemType across all matching stacks,
     * starting from the lowest slot index. Returns the number of units actually removed
     * (may be less than quantity if the inventory holds fewer than requested).
     *
     * Used by the weapon system to spend ammo from reserve on every reload.
     *
     * @param itemType the type to deduct; must not be null
     * @param quantity how many units to remove; must be >= 1
     * @return units actually removed (0 if none found)
     */
    public int spend(ItemType itemType, int quantity) {
        if (itemType == null) throw new IllegalArgumentException("itemType must not be null");
        if (quantity < 1)     throw new IllegalArgumentException("quantity must be >= 1");

        int remaining = quantity;
        for (int slotIndex = 0; slotIndex < slots.length && remaining > 0; slotIndex++) {
            ItemStack slot = slots[slotIndex];
            if (slot.type == itemType && !slot.isEmpty()) {
                int taken = Math.min(slot.quantity, remaining);
                slot.quantity -= taken;
                remaining     -= taken;
                if (slot.quantity <= 0) {
                    slot.clear();
                }
            }
        }
        return quantity - remaining;
    }

    // =========================================================================
    // Queries — all allocation-free
    // =========================================================================

    /**
     * Returns the first ItemStack whose type matches itemType, or null if none exists.
     * Does not allocate. Callers must not mutate the returned stack directly.
     *
     * @param itemType the type to search for
     * @return the first matching stack, or null
     */
    public ItemStack findStack(ItemType itemType) {
        for (int slotIndex = 0; slotIndex < slots.length; slotIndex++) {
            if (slots[slotIndex].type == itemType) {
                return slots[slotIndex];
            }
        }
        return null;
    }

    /**
     * Returns true if every slot is occupied (non-empty).
     * Allocation-free: simple linear scan.
     */
    public boolean isFull() {
        for (int slotIndex = 0; slotIndex < slots.length; slotIndex++) {
            if (slots[slotIndex].isEmpty()) return false;
        }
        return true;
    }

    /**
     * Returns the index of the first empty slot, or -1 if all slots are occupied.
     * Allocation-free.
     */
    public int firstFreeSlot() {
        for (int slotIndex = 0; slotIndex < slots.length; slotIndex++) {
            if (slots[slotIndex].isEmpty()) return slotIndex;
        }
        return -1;
    }

    /**
     * Counts the total number of items of the given type across all stacks.
     * Allocation-free.
     *
     * @param itemType the type to count
     * @return total quantity across all matching stacks
     */
    public int countOf(ItemType itemType) {
        int total = 0;
        for (int slotIndex = 0; slotIndex < slots.length; slotIndex++) {
            if (slots[slotIndex].type == itemType) {
                total += slots[slotIndex].quantity;
            }
        }
        return total;
    }

    /**
     * Returns true if the inventory contains at least one item of the given type.
     * Equivalent to countOf(itemType) > 0 but short-circuits on first match.
     * Allocation-free.
     *
     * @param itemType the type to check for
     * @return true if at least one unit of itemType is present
     */
    public boolean hasItem(ItemType itemType) {
        for (int slotIndex = 0; slotIndex < slots.length; slotIndex++) {
            if (slots[slotIndex].type == itemType) return true;
        }
        return false;
    }

    /**
     * Returns the ItemStack at the given slot index for read-only inspection.
     * The returned reference is the live slot object; callers must not mutate it.
     *
     * @param slotIndex must be in [0, INVENTORY_SLOT_COUNT)
     * @return the slot at that index (may be empty)
     */
    public ItemStack getSlot(int slotIndex) {
        validateSlotIndex(slotIndex);
        return slots[slotIndex];
    }

    /** Returns the number of slots in this inventory. Always ItemConstants.INVENTORY_SLOT_COUNT. */
    public int getSlotCount() {
        return slots.length;
    }

    // =========================================================================
    // Equipped weapon tracking
    // =========================================================================

    /**
     * Returns the index of the currently equipped weapon slot, or -1 if unarmed.
     * WeaponReloadSubscriber and the weapon system use this to query the active weapon.
     */
    public int getEquippedWeaponSlot() {
        return equippedWeaponSlot;
    }

    /**
     * Sets the equipped weapon slot.
     * Pass -1 to unequip (unarmed). Callers are responsible for ensuring the
     * slot at the given index holds a WEAPON item before setting this.
     *
     * @param slotIndex index of the weapon slot to equip, or -1 to unequip
     */
    public void setEquippedWeaponSlot(int slotIndex) {
        if (slotIndex != -1) validateSlotIndex(slotIndex);
        this.equippedWeaponSlot = slotIndex;
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private void validateSlotIndex(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.length) {
            throw new IndexOutOfBoundsException(
                    "Slot index " + slotIndex + " out of range [0, " + slots.length + ")");
        }
    }
}
