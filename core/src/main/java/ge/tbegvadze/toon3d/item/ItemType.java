package ge.tbegvadze.toon3d.item;

import ge.tbegvadze.toon3d.util.WeaponConstants;
import ge.tbegvadze.toon3d.util.ItemConstants;

/**
 * Catalog of every item the marine can carry in the slotted inventory.
 *
 * Ammo types (AMMO_BULLETS, AMMO_SHELLS, AMMO_CELLS, AMMO_ROCKETS) live here as
 * stackable inventory items in the AMMO category. Weapons spend them from the
 * inventory on reload via Inventory.spend(). They are not manually usable.
 *
 * Each constant stores all data needed by the inventory layer and the order-6 UI:
 *   displayName   — human-readable label shown in the slot grid and detail panel.
 *   category      — determines use() routing and slot-grid sorting.
 *   isStackable   — if true, items of this type merge into a single slot up to maxStackSize.
 *   maxStackSize  — maximum quantity in one stack; always 1 for non-stackable items.
 *   glyph         — ASCII char displayed in the slot cell by order-6 (roguelike convention).
 *   glyphRed      — red   component of the glyph colour (0–1).
 *   glyphGreen    — green component of the glyph colour (0–1).
 *   glyphBlue     — blue  component of the glyph colour (0–1).
 *
 * Glyph colour palette intent:
 *   Weapons      — amber  (1.0, 0.75, 0.1)
 *   Consumables  — green  (0.2, 0.9,  0.2) for medkits; orange (1.0, 0.55, 0.1) for stims
 *   Key items    — colour matches the keycard tier: red/yellow/blue
 *   Credits      — gold   (1.0, 0.85, 0.1)
 *
 * All stack-size values reference Constants so the single source of truth is respected.
 */
public enum ItemType {

    // -------------------------------------------------------------------------
    // WEAPONS — each occupies exactly one slot; never stackable
    // -------------------------------------------------------------------------

    WEAPON_PISTOL(
            "Pistol",
            ItemCategory.WEAPON,
            false,
            ItemConstants.ITEM_STACK_MAX_WEAPON,
            'P',
            1.00f, 0.75f, 0.10f
    ),

    WEAPON_SHOTGUN(
            "Shotgun",
            ItemCategory.WEAPON,
            false,
            ItemConstants.ITEM_STACK_MAX_WEAPON,
            'S',
            1.00f, 0.75f, 0.10f
    ),

    WEAPON_PLASMA(
            "Plasma Rifle",
            ItemCategory.WEAPON,
            false,
            ItemConstants.ITEM_STACK_MAX_WEAPON,
            'L',
            1.00f, 0.75f, 0.10f
    ),

    WEAPON_ROCKET(
            "Rocket Launcher",
            ItemCategory.WEAPON,
            false,
            ItemConstants.ITEM_STACK_MAX_WEAPON,
            'R',
            1.00f, 0.75f, 0.10f
    ),

    WEAPON_DOUBLE_BARREL(
            "Double-Barrel Shotgun",
            ItemCategory.WEAPON,
            false,
            ItemConstants.ITEM_STACK_MAX_WEAPON,
            'D',
            1.00f, 0.75f, 0.10f
    ),

    WEAPON_CHAINGUN(
            "Chaingun",
            ItemCategory.WEAPON,
            false,
            ItemConstants.ITEM_STACK_MAX_WEAPON,
            'G',
            1.00f, 0.75f, 0.10f
    ),

    WEAPON_RAILGUN(
            "Railgun",
            ItemCategory.WEAPON,
            false,
            ItemConstants.ITEM_STACK_MAX_WEAPON,
            'N',
            1.00f, 0.75f, 0.10f
    ),

    WEAPON_INCINERATOR(
            "Incinerator",
            ItemCategory.WEAPON,
            false,
            ItemConstants.ITEM_STACK_MAX_WEAPON,
            'F',
            1.00f, 0.75f, 0.10f
    ),

    // -------------------------------------------------------------------------
    // CONSUMABLES — stackable; quantities bounded by per-type caps in Constants
    // -------------------------------------------------------------------------

    MEDKIT_SMALL(
            "Medkit (Small)",
            ItemCategory.CONSUMABLE,
            true,
            ItemConstants.ITEM_STACK_MAX_MEDKIT_SMALL,
            '+',
            0.20f, 0.90f, 0.20f
    ),

    MEDKIT_LARGE(
            "Medkit (Large)",
            ItemCategory.CONSUMABLE,
            true,
            ItemConstants.ITEM_STACK_MAX_MEDKIT_LARGE,
            'H',
            0.20f, 0.90f, 0.20f
    ),

    STIMPACK(
            "Stimpack",
            ItemCategory.CONSUMABLE,
            true,
            ItemConstants.ITEM_STACK_MAX_STIMPACK,
            's',
            1.00f, 0.55f, 0.10f
    ),

    // -------------------------------------------------------------------------
    // KEY ITEMS — non-consumable; door checks read Inventory rather than a flag.
    // Glyph colours match the classic UAC keycard tier palette.
    // -------------------------------------------------------------------------

    KEYCARD_RED(
            "Red Keycard",
            ItemCategory.KEY_ITEM,
            false,
            1,
            'k',
            1.00f, 0.10f, 0.10f
    ),

    KEYCARD_YELLOW(
            "Yellow Keycard",
            ItemCategory.KEY_ITEM,
            false,
            1,
            'k',
            1.00f, 0.90f, 0.10f
    ),

    KEYCARD_BLUE(
            "Blue Keycard",
            ItemCategory.KEY_ITEM,
            false,
            1,
            'k',
            0.20f, 0.55f, 1.00f
    ),

    // -------------------------------------------------------------------------
    // MISC — credits and other non-combat items
    // -------------------------------------------------------------------------

    CREDITS(
            "Credits",
            ItemCategory.MISC,
            true,
            ItemConstants.ITEM_STACK_MAX_CREDITS,
            '$',
            1.00f, 0.85f, 0.10f
    ),

    // -------------------------------------------------------------------------
    // AMMO — reserve ammunition stacks; spent by weapons on reload.
    // Stack caps match the AMMO_RESERVE_CAP_* constants so one slot holds a full
    // reserve. Not manually usable from the inventory menu.
    // -------------------------------------------------------------------------

    AMMO_BULLETS(
            "Bullets",
            ItemCategory.AMMO,
            true,
            ItemConstants.AMMO_RESERVE_CAP_BULLETS,
            'b',
            0.72f, 0.48f, 0.18f
    ),

    AMMO_SHELLS(
            "Shells",
            ItemCategory.AMMO,
            true,
            ItemConstants.AMMO_RESERVE_CAP_SHELLS,
            'h',
            0.78f, 0.68f, 0.12f
    ),

    AMMO_CELLS(
            "Plasma Cells",
            ItemCategory.AMMO,
            true,
            ItemConstants.AMMO_RESERVE_CAP_CELLS,
            'c',
            0.10f, 0.80f, 0.90f
    ),

    AMMO_ROCKETS(
            "Rockets",
            ItemCategory.AMMO,
            true,
            ItemConstants.AMMO_RESERVE_CAP_ROCKETS,
            'r',
            0.45f, 0.55f, 0.20f
    ),

    AMMO_SLUGS(
            "Slugs",
            ItemCategory.AMMO,
            true,
            WeaponConstants.RAILGUN_MAX_SLUGS,
            'g',
            0.85f, 0.90f, 0.95f
    );

    // -------------------------------------------------------------------------
    // Fields — all final; set once in the constructor
    // -------------------------------------------------------------------------

    private final String       displayName;
    private final ItemCategory category;
    private final boolean      isStackable;
    private final int          maxStackSize;
    private final char         glyph;
    private final float        glyphRed;
    private final float        glyphGreen;
    private final float        glyphBlue;

    ItemType(String displayName, ItemCategory category,
             boolean isStackable, int maxStackSize,
             char glyph, float glyphRed, float glyphGreen, float glyphBlue) {
        this.displayName  = displayName;
        this.category     = category;
        this.isStackable  = isStackable;
        this.maxStackSize = maxStackSize;
        this.glyph        = glyph;
        this.glyphRed     = glyphRed;
        this.glyphGreen   = glyphGreen;
        this.glyphBlue    = glyphBlue;
    }

    public String       getDisplayName()  { return displayName; }
    public ItemCategory getCategory()     { return category; }
    public boolean      isStackable()     { return isStackable; }
    public int          getMaxStackSize() { return maxStackSize; }
    public char         getGlyph()        { return glyph; }
    public float        getGlyphRed()     { return glyphRed; }
    public float        getGlyphGreen()   { return glyphGreen; }
    public float        getGlyphBlue()    { return glyphBlue; }
}
