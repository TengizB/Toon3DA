package ge.tbegvadze.toon3d.item;

/**
 * Value-style holder for a quantity of a single item type occupying one inventory slot.
 *
 * Instances are pre-allocated by Inventory and REUSED in place — the type and quantity
 * fields are mutated directly. This means no allocation occurs in hot paths (tryAdd,
 * remove, use). An empty (cleared) slot is represented by type == null and quantity == 0.
 *
 * This class is intentionally package-private in construction: Inventory creates the fixed
 * pool; external code reads stacks via Inventory's query methods.
 */
public final class ItemStack {

    /** The item type stored in this slot, or null if the slot is empty. */
    ItemType type;

    /** How many items are in this stack. Always 0 when type is null. */
    int quantity;

    /** Creates an empty slot. Called once per slot during Inventory construction. */
    ItemStack() {
        this.type     = null;
        this.quantity = 0;
    }

    /**
     * Fills this slot with the given type and quantity.
     * Called by Inventory to occupy a previously empty slot.
     */
    void occupy(ItemType itemType, int stackQuantity) {
        this.type     = itemType;
        this.quantity = stackQuantity;
    }

    /**
     * Clears this slot back to the empty state.
     * Called by Inventory when a stack's quantity reaches zero.
     */
    void clear() {
        this.type     = null;
        this.quantity = 0;
    }

    // -------------------------------------------------------------------------
    // Public read-only accessors — callers must not mutate via these
    // -------------------------------------------------------------------------

    /** Returns the item type of this stack, or null if the slot is empty. */
    public ItemType getType() {
        return type;
    }

    /** Returns the current quantity in this stack (0 if the slot is empty). */
    public int getQuantity() {
        return quantity;
    }

    /** True when this slot holds no item. */
    public boolean isEmpty() {
        return type == null;
    }

    @Override
    public String toString() {
        if (type == null) return "ItemStack[empty]";
        return "ItemStack[" + type.getDisplayName() + " x" + quantity + "]";
    }
}
