package ge.tbegvadze.toon3d.item;

/**
 * An item stack that has been placed or dropped on a specific floor tile.
 *
 * Ground items are owned by a future GroundItemManager and rendered as billboards
 * by PropRenderer. The player picks them up via the F/LOOT verb (one turn) or, for
 * auto-collect types (ammo, armour), automatically on the step.
 *
 * This is a plain data holder — no LibGDX state, no Disposable.
 */
public final class GroundItem {

    /** Tile column of the world cell this item rests on. */
    public final int tileColumn;

    /** Tile row of the world cell this item rests on. */
    public final int tileRow;

    /** The item stack sitting on this tile. The stack object is owned by this GroundItem. */
    public final ItemStack stack;

    // Tile-bounds validation is the caller's responsibility (requires a Level reference).
    public GroundItem(int tileColumn, int tileRow, ItemType itemType, int quantity) {
        if (itemType == null) throw new IllegalArgumentException("itemType must not be null");
        if (quantity < 1)    throw new IllegalArgumentException("quantity must be >= 1");
        this.tileColumn = tileColumn;
        this.tileRow    = tileRow;
        this.stack      = new ItemStack();
        this.stack.occupy(itemType, quantity);
    }
}
