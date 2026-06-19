package ge.tbegvadze.toon3d.item;

import ge.tbegvadze.toon3d.entity.WeaponAbility;
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
 *   displayName        — human-readable label shown in the slot grid and detail panel.
 *   category           — determines use() routing and slot-grid sorting.
 *   isStackable        — if true, items of this type merge into a single slot up to maxStackSize.
 *   maxStackSize       — maximum quantity in one stack; always 1 for non-stackable items.
 *   glyph              — ASCII char displayed in the slot cell by order-6 (roguelike convention).
 *   glyphRed/Green/Blue — glyph colour components (0–1).
 *   description        — short lore/usage text shown in the ItemWindow detail panel.
 *   signatureAbilities — abilities characteristic of this weapon type (shown in ItemWindow
 *                        right column). Non-weapon entries use the 9-param constructor which
 *                        defaults to an empty array; static NO_ABILITIES alias declared below.
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
            1.00f, 0.75f, 0.10f,
            "Standard UAC sidearm. Reliable and accurate with limited stopping power.",
            new WeaponAbility[]{WeaponAbility.CRITICAL_STRIKE, WeaponAbility.MARKSMANS_PATIENCE}
    ),

    WEAPON_SHOTGUN(
            "Shotgun",
            ItemCategory.WEAPON,
            false,
            ItemConstants.ITEM_STACK_MAX_WEAPON,
            'S',
            1.00f, 0.75f, 0.10f,
            "Close-range devastation. Spreads buckshot in a wide cone.",
            new WeaponAbility[]{WeaponAbility.POINT_BLANK, WeaponAbility.STAGGER_ROUNDS}
    ),

    WEAPON_PLASMA(
            "Plasma Rifle",
            ItemCategory.WEAPON,
            false,
            ItemConstants.ITEM_STACK_MAX_WEAPON,
            'L',
            1.00f, 0.75f, 0.10f,
            "UAC Plasma Rifle. Rapid-fire plasma bolts at high energy cost.",
            new WeaponAbility[]{WeaponAbility.INCENDIARY, WeaponAbility.RHYTHM}
    ),

    WEAPON_ROCKET(
            "Rocket Launcher",
            ItemCategory.WEAPON,
            false,
            ItemConstants.ITEM_STACK_MAX_WEAPON,
            'R',
            1.00f, 0.75f, 0.10f,
            "Devastating area-of-effect blast. Every shot counts.",
            new WeaponAbility[]{WeaponAbility.STATIC_DISCHARGE, WeaponAbility.OPENING_SALVO}
    ),

    WEAPON_DOUBLE_BARREL(
            "Double-Barrel Shotgun",
            ItemCategory.WEAPON,
            false,
            ItemConstants.ITEM_STACK_MAX_WEAPON,
            'D',
            1.00f, 0.75f, 0.10f,
            "Both barrels at once — double the carnage, double the reload.",
            new WeaponAbility[]{WeaponAbility.BURST_FIRE, WeaponAbility.POINT_BLANK}
    ),

    WEAPON_CHAINGUN(
            "Chaingun",
            ItemCategory.WEAPON,
            false,
            ItemConstants.ITEM_STACK_MAX_WEAPON,
            'G',
            1.00f, 0.75f, 0.10f,
            "UAC Chaingun. Sustained suppressing fire at high ammo consumption.",
            new WeaponAbility[]{WeaponAbility.BURST_FIRE, WeaponAbility.EXTENDED_MAG}
    ),

    WEAPON_RAILGUN(
            "Railgun",
            ItemCategory.WEAPON,
            false,
            ItemConstants.ITEM_STACK_MAX_WEAPON,
            'N',
            1.00f, 0.75f, 0.10f,
            "Accelerates a ferromagnetic slug at hypersonic velocity. Punches through walls.",
            new WeaponAbility[]{WeaponAbility.OVERPENETRATION, WeaponAbility.MARKSMANS_PATIENCE}
    ),

    WEAPON_INCINERATOR(
            "Incinerator",
            ItemCategory.WEAPON,
            false,
            ItemConstants.ITEM_STACK_MAX_WEAPON,
            'F',
            1.00f, 0.75f, 0.10f,
            "Sprays burning plasma at close range. High damage, limited reach.",
            new WeaponAbility[]{WeaponAbility.INCENDIARY, WeaponAbility.POINT_BLANK}
    ),

    WEAPON_FIST(
            "Fist",
            ItemCategory.WEAPON,
            false,
            ItemConstants.ITEM_STACK_MAX_WEAPON,
            'X',
            1.00f, 0.75f, 0.10f,
            "Your bare hands. No ammo needed. Brutal at point-blank range.",
            new WeaponAbility[]{WeaponAbility.SECOND_WIND, WeaponAbility.CLEAVE}
    ),

    WEAPON_KNIFE(
            "Combat Knife",
            ItemCategory.WEAPON,
            false,
            ItemConstants.ITEM_STACK_MAX_WEAPON,
            'K',
            1.00f, 0.75f, 0.10f,
            "Silent and efficient. Never runs dry.",
            new WeaponAbility[]{WeaponAbility.REND, WeaponAbility.EXECUTIONER}
    ),

    WEAPON_HAMMER(
            "Hammer",
            ItemCategory.WEAPON,
            false,
            ItemConstants.ITEM_STACK_MAX_WEAPON,
            '/',
            1.00f, 0.75f, 0.10f,
            "Heavy industrial hammer. Slow swing, but hits like a freight train.",
            new WeaponAbility[]{WeaponAbility.KINETIC_SLAM, WeaponAbility.ARMOR_PIERCE}
    ),

    WEAPON_CHAINSAW(
            "Chainsaw",
            ItemCategory.WEAPON,
            false,
            ItemConstants.ITEM_STACK_MAX_WEAPON,
            'Z',
            1.00f, 0.75f, 0.10f,
            "Continuous melee damage. Needs fuel cells to keep running.",
            new WeaponAbility[]{WeaponAbility.REND, WeaponAbility.CLEAVE}
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
            0.20f, 0.90f, 0.20f,
            "UAC field first-aid kit. Restores a moderate amount of health."
    ),

    MEDKIT_LARGE(
            "Medkit (Large)",
            ItemCategory.CONSUMABLE,
            true,
            ItemConstants.ITEM_STACK_MAX_MEDKIT_LARGE,
            'H',
            0.20f, 0.90f, 0.20f,
            "Full field surgery kit. Restores a large amount of health."
    ),

    STIMPACK(
            "Stimpack",
            ItemCategory.CONSUMABLE,
            true,
            ItemConstants.ITEM_STACK_MAX_STIMPACK,
            's',
            1.00f, 0.55f, 0.10f,
            "Combat stimulant. Grants a temporary boost to speed and reflexes."
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
            1.00f, 0.10f, 0.10f,
            "UAC red-level access card. Unlocks red security doors."
    ),

    KEYCARD_YELLOW(
            "Yellow Keycard",
            ItemCategory.KEY_ITEM,
            false,
            1,
            'k',
            1.00f, 0.90f, 0.10f,
            "UAC yellow-level access card. Unlocks yellow security doors."
    ),

    KEYCARD_BLUE(
            "Blue Keycard",
            ItemCategory.KEY_ITEM,
            false,
            1,
            'k',
            0.20f, 0.55f, 1.00f,
            "UAC blue-level access card. Unlocks blue security doors."
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
            1.00f, 0.85f, 0.10f,
            "UAC facility credits. Used at dispensers and trading terminals."
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
            0.72f, 0.48f, 0.18f,
            "9mm pistol rounds. Standard sidearm ammunition."
    ),

    AMMO_SHELLS(
            "Shells",
            ItemCategory.AMMO,
            true,
            ItemConstants.AMMO_RESERVE_CAP_SHELLS,
            'h',
            0.78f, 0.68f, 0.12f,
            "12-gauge buckshot shells. Used by both shotgun variants."
    ),

    AMMO_CELLS(
            "Plasma Cells",
            ItemCategory.AMMO,
            true,
            ItemConstants.AMMO_RESERVE_CAP_CELLS,
            'c',
            0.10f, 0.80f, 0.90f,
            "High-energy plasma cells. Powers the Plasma Rifle and Incinerator."
    ),

    AMMO_ROCKETS(
            "Rockets",
            ItemCategory.AMMO,
            true,
            ItemConstants.AMMO_RESERVE_CAP_ROCKETS,
            'r',
            0.45f, 0.55f, 0.20f,
            "High-explosive warheads. Fired by the Rocket Launcher."
    ),

    AMMO_SLUGS(
            "Slugs",
            ItemCategory.AMMO,
            true,
            WeaponConstants.RAILGUN_MAX_SLUGS,
            'g',
            0.85f, 0.90f, 0.95f,
            "Ferromagnetic slugs. Accelerated by the Railgun to hypersonic velocity."
    );

    // -------------------------------------------------------------------------
    // Fields — all final; set once in the constructor
    // -------------------------------------------------------------------------

    private final String          displayName;
    private final ItemCategory    category;
    private final boolean         isStackable;
    private final int             maxStackSize;
    private final char            glyph;
    private final float           glyphRed;
    private final float           glyphGreen;
    private final float           glyphBlue;
    private final String          description;
    /**
     * Null for non-weapon types. The getter returns NO_ABILITIES instead of null
     * so callers never receive a null reference. Stored as null rather than an
     * allocated empty array because Java enum static fields (NO_ABILITIES) are
     * not yet initialised when enum constructors run — the convenience constructor
     * cannot reference NO_ABILITIES at construction time.
     */
    private final WeaponAbility[] signatureAbilities;

    // -------------------------------------------------------------------------
    // Shared empty array — reused by getSignatureAbilities() for all non-weapons
    // -------------------------------------------------------------------------

    public static final WeaponAbility[] NO_ABILITIES = new WeaponAbility[0];

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Full constructor — used by weapon item types that carry signature abilities. */
    ItemType(String displayName, ItemCategory category,
             boolean isStackable, int maxStackSize,
             char glyph, float glyphRed, float glyphGreen, float glyphBlue,
             String description, WeaponAbility[] signatureAbilities) {
        this.displayName        = displayName;
        this.category           = category;
        this.isStackable        = isStackable;
        this.maxStackSize       = maxStackSize;
        this.glyph              = glyph;
        this.glyphRed           = glyphRed;
        this.glyphGreen         = glyphGreen;
        this.glyphBlue          = glyphBlue;
        this.description        = description;
        this.signatureAbilities = signatureAbilities;
    }

    /** Convenience constructor for non-weapon types — stores null; getter returns NO_ABILITIES. */
    ItemType(String displayName, ItemCategory category,
             boolean isStackable, int maxStackSize,
             char glyph, float glyphRed, float glyphGreen, float glyphBlue,
             String description) {
        this(displayName, category, isStackable, maxStackSize,
             glyph, glyphRed, glyphGreen, glyphBlue, description, null);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String       getDisplayName()  { return displayName; }
    public ItemCategory getCategory()     { return category; }
    public boolean      isStackable()     { return isStackable; }
    public int          getMaxStackSize() { return maxStackSize; }
    public char         getGlyph()        { return glyph; }
    public float        getGlyphRed()     { return glyphRed; }
    public float        getGlyphGreen()   { return glyphGreen; }
    public float        getGlyphBlue()    { return glyphBlue; }
    public String       getDescription()  { return description; }

    /**
     * Returns the signature abilities for this item type.
     * Non-weapon types return the shared NO_ABILITIES constant (no allocation).
     * Weapon types return a defensive copy so callers cannot corrupt the stored array.
     * Cache the result at call-site if repeated access is needed (e.g. in open()).
     */
    public WeaponAbility[] getSignatureAbilities() {
        if (signatureAbilities == null) return NO_ABILITIES;
        return signatureAbilities.clone();
    }
}
