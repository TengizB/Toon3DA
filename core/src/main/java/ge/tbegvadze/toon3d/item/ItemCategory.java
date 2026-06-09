package ge.tbegvadze.toon3d.item;

/**
 * Broad category of an item in the inventory.
 *
 * Used for sorting, branching in use() logic, and filtering the slot grid in
 * the inventory UI (Order 6). Every ItemType belongs to exactly one category.
 *
 * WEAPON     — carried guns; equipping one sets Inventory.equippedWeaponSlot.
 * CONSUMABLE — single-use items that route into health, armour, or effect pools.
 * KEY_ITEM   — non-consumable quest/progress items such as keycards.
 * MOD        — weapon modification chips (reserved for a future order).
 * MISC       — trade goods, credits, and any other non-combat items.
 */
public enum ItemCategory {
    WEAPON,
    CONSUMABLE,
    KEY_ITEM,
    MOD,
    MISC
}
