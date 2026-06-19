package ge.tbegvadze.toon3d.util;

/** Item system constants — medical pickups, armour pickups, inventory, ammo, inventory UI. */
public final class ItemConstants {

    private ItemConstants() {}

    // Medical pickup system — stim-packs ('+') and field medkits ('H')
    public static final int   MEDKIT_STIM_HEAL                = 8;
    public static final int   MEDKIT_FULL_HEAL                = 25;
    public static final int   MEDKIT_TOTAL_CARRY_CAP          = 4;
    public static final int   MEDKIT_FULL_CARRY_CAP           = 2;
    public static final float PLAYER_HEAL_DURATION            = 0.18f;
    public static final float MEDKIT_STIM_SPRITE_HEIGHT       = 0.20f;
    public static final float MEDKIT_FULL_SPRITE_HEIGHT       = 0.30f;

    // Player stats
    public static final int   PLAYER_MAX_HEALTH               = 100;
    // Player armor — pool capped at 50; armour pickups feed directly into this pool
    public static final int   PLAYER_MAX_ARMOR                = 50;

    // Armour pickup system — shards ('a') and security vests ('A')
    public static final int   ARMOUR_SHARD_VALUE              = 5;
    public static final int   ARMOUR_VEST_VALUE               = 25;
    public static final float ARMOUR_SHARD_SPRITE_HEIGHT      = 0.25f;
    public static final float ARMOUR_VEST_SPRITE_HEIGHT       = 0.35f;
    // Fraction of each incoming hit that is absorbed by armour (depleting it instead of HP).
    public static final float ARMOUR_ABSORB_FRACTION          = 0.50f;

    // Inventory system — slot count and per-type stack caps
    // INVENTORY_SLOT_COUNT: 10 slots (5×2 grid) gives tight but meaningful choice.
    // Ammo and armour bypass slots entirely; only consumables, weapons, key items,
    // mods, and credits occupy the slotted grid.
    public static final int INVENTORY_SLOT_COUNT          = 10;
    // ITEM_STACK_MAX_DEFAULT: generic fallback cap for any stackable not listed below.
    public static final int ITEM_STACK_MAX_DEFAULT        = 99;
    // Per-type stack caps — these are the definitive balance numbers; tune via constants only.
    public static final int ITEM_STACK_MAX_MEDKIT_SMALL   = 5;
    public static final int ITEM_STACK_MAX_MEDKIT_LARGE   = 2;
    public static final int ITEM_STACK_MAX_STIMPACK       = 5;
    // Weapons never stack — each weapon occupies exactly one slot.
    public static final int ITEM_STACK_MAX_WEAPON         = 1;
    public static final int ITEM_STACK_MAX_CREDITS        = 999;

    // Ammo system — reserve caps, box grants, starting reserves
    // All values are PLACEHOLDERS pending playtest.
    public static final int   AMMO_RESERVE_CAP_BULLETS    = 200;
    public static final int   AMMO_RESERVE_CAP_SHELLS     = 60;
    public static final int   AMMO_RESERVE_CAP_CELLS      = 120;
    public static final int   AMMO_RESERVE_CAP_ROCKETS    = 20;

    public static final int   AMMO_BOX_BULLETS            = 20;
    public static final int   AMMO_BOX_SHELLS             = 8;
    public static final int   AMMO_BOX_CELLS              = 20;
    public static final int   AMMO_BOX_ROCKETS            = 2;

    public static final int   AMMO_START_BULLETS          = 50;
    public static final int   AMMO_START_SHELLS           = 16;
    public static final int   AMMO_START_CELLS            = 0;
    public static final int   AMMO_START_ROCKETS          = 0;

    public static final float AMMO_PICKUP_HEIGHT_FRACTION = 0.20f;

    // Floor pickup bob animation — ammo, medkits, and armour hover with the same sin-wave
    // as weapon ground items so all collectibles feel consistently animated.
    public static final float PICKUP_ITEM_BOB_SPEED              = 2.2f;   // radians/sec
    public static final float PICKUP_ITEM_BOB_AMPLITUDE_FRACTION = 0.10f;  // 10% of sprite height
    public static final float PICKUP_ITEM_BOB_PHASE_STEP         = 0.5f;   // per-char phase offset (rad)

    // Start room — starter ammo granted on weapon selection (sufficient for first dungeon floor)
    public static final int START_ROOM_AMMO_BULLETS  = 120;
    public static final int START_ROOM_AMMO_SHELLS   = 16;
    public static final int START_ROOM_AMMO_CELLS    = 60;
    public static final int START_ROOM_AMMO_SLUGS    = 6;
    public static final int START_ROOM_AMMO_ROCKETS  = 4;

    // Inventory UI — slot grid constants (used by ItemGridPanel)
    public static final int   INVENTORY_GRID_COLUMNS        = 4;
    public static final float INV_SLOT_SIZE                  = 96f;
    public static final float INV_SLOT_GAP                   = 12f;
    public static final float INV_SCRIM_ALPHA                = 0.60f;
    public static final float INV_PANEL_ALPHA                = 0.95f;
    public static final float INV_SELECT_BORDER_THICKNESS    = 3f;
    public static final float INV_FLASH_SECONDS              = 0.45f;

    // Inventory Menu — new layout (Part 1: Layout & Architecture)
    public static final float INV_HEADER_HEIGHT           = 70f;
    public static final float INV_HEADER_Y                = 650f;
    public static final float INV_PANEL_Y_BOTTOM          = 110f;
    public static final float INV_PANEL_Y_TOP             = 645f;
    public static final float INV_PANEL_HEIGHT            = 535f;
    public static final float INV_LEFT_PANEL_X            = 20f;
    public static final float INV_LEFT_PANEL_WIDTH        = 500f;
    public static final float INV_RIGHT_PANEL_X           = 540f;
    public static final float INV_RIGHT_PANEL_WIDTH       = 720f;
    public static final float INV_PANEL_GAP               = 20f;
    public static final float INV_PANEL_PADDING           = 15f;
    public static final float INV_HEADER_TITLE_X          = 40f;
    public static final float INV_HEADER_EXIT_BUTTON_SIZE = 50f;
    public static final float INV_HEADER_EXIT_MARGIN      = 10f;
    public static final float INV_BASE_DIM_FACTOR         = 0.40f;
    public static final float INV_ITEM_WIN_DIM_FACTOR     = 0.50f;

    // ItemWindow popup geometry — centered at (640, 380) in world coordinates
    public static final float INV_ITEM_WINDOW_WIDTH       = 460f;
    public static final float INV_ITEM_WINDOW_HEIGHT      = 340f;
    public static final float INV_ITEM_WINDOW_CENTER_X    = 640f;
    public static final float INV_ITEM_WINDOW_CENTER_Y    = 380f;
    public static final float INV_ITEM_WINDOW_X           = INV_ITEM_WINDOW_CENTER_X - INV_ITEM_WINDOW_WIDTH  / 2f;
    public static final float INV_ITEM_WINDOW_Y           = INV_ITEM_WINDOW_CENTER_Y - INV_ITEM_WINDOW_HEIGHT / 2f;
    public static final float INV_ITEM_WINDOW_HEADER_LINE_OFFSET = 50f;  // header accent line offset from top

    // Inventory Menu — Part 2: Weapon Slots Panel geometry
    public static final float INV_WEAPON_SLOT_X                 = 35f;
    public static final float INV_WEAPON_SLOT_WIDTH             = 470f;
    public static final float INV_WEAPON_SLOT_HEIGHT            = 100f;
    public static final float INV_WEAPON_SLOT_GAP               = 6f;
    public static final float INV_WEAPON_SECTION_LABEL_H        = 20f;
    public static final float INV_WEAPON_SECTION_DIVIDER_Y      = 280f;
    public static final float INV_WEAPON_GUN_SLOT_1_Y           = 507f;
    public static final float INV_WEAPON_GUN_SLOT_2_Y           = 401f;
    public static final float INV_WEAPON_GUN_SLOT_3_Y           = 295f;
    public static final float INV_WEAPON_MELEE_SLOT_Y           = 145f;
    public static final float INV_WEAPON_RANGED_LABEL_Y         = 613f;
    public static final float INV_WEAPON_MELEE_LABEL_Y          = 253f;
    public static final float INV_WEAPON_SPRITE_ZONE_WIDTH      = 280f;
    public static final float INV_WEAPON_INFO_ZONE_X_OFFSET     = 288f;
    public static final float INV_WEAPON_ACTIVE_BADGE_WIDTH     = 60f;
    public static final float INV_WEAPON_ACTIVE_BADGE_HEIGHT    = 18f;
    public static final float INV_WEAPON_ACTIVE_BADGE_MARGIN    = 5f;
    public static final float INV_WEAPON_LOCK_BODY_WIDTH        = 30f;
    public static final float INV_WEAPON_LOCK_BODY_HEIGHT       = 22f;
    public static final float INV_WEAPON_LOCK_SHACKLE_RADIUS    = 11f;
    public static final float INV_WEAPON_ACTIVE_PULSE_HZ        = 4f;
    public static final float INV_WEAPON_ACTIVE_PULSE_MIN       = 0.7f;
    public static final int   INV_WEAPON_LOCKED_UNLOCK_LEVEL    = 10;
    // Lock icon geometry — center offsets relative to slot origin
    public static final float INV_WEAPON_LOCK_CENTER_X_OFFSET  = 140f;
    public static final float INV_WEAPON_LOCK_CENTER_Y_OFFSET  = 50f;
    // Section accent line X offsets (where the line starts after the label)
    public static final float INV_WEAPON_RANGED_ACCENT_X_OFFSET = 68f;
    public static final float INV_WEAPON_MELEE_ACCENT_X_OFFSET  = 54f;
    // Label Y offset from the section accent line center
    public static final float INV_WEAPON_ACCENT_LINE_Y_OFFSET   = 7f;
    // Locked slot text Y positions (relative to slot bottom)
    public static final float INV_WEAPON_LOCK_LABEL_Y_OFFSET    = 38f;
    public static final float INV_WEAPON_LOCK_HINT_Y_OFFSET     = 22f;
    // Info zone text layout
    public static final float INV_WEAPON_INFO_TOP_MARGIN        = 12f;
    public static final float INV_WEAPON_INFO_LINE_STEP         = 16f;
    // Dashed line dash/gap lengths
    public static final float INV_WEAPON_DIVIDER_DASH_LENGTH    = 12f;
    public static final float INV_WEAPON_DIVIDER_GAP_LENGTH     = 6f;
    public static final float INV_WEAPON_LOCKED_DASH_LENGTH     = 8f;
    public static final float INV_WEAPON_LOCKED_GAP_LENGTH      = 4f;

    // ItemWindow button row geometry
    public static final float INV_ITEM_WIN_BTN_AREA_HEIGHT = 50f;
    public static final float INV_ITEM_WIN_BTN_PADDING     = 10f;
    public static final float INV_ITEM_WIN_BTN_GAP         = 8f;
    public static final float INV_ITEM_WIN_BTN_HEIGHT      = INV_ITEM_WIN_BTN_AREA_HEIGHT - 2f * INV_ITEM_WIN_BTN_PADDING;
    public static final float INV_ITEM_WIN_BTN_WIDTH       = (INV_ITEM_WINDOW_WIDTH - 2f * INV_ITEM_WIN_BTN_PADDING - 2f * INV_ITEM_WIN_BTN_GAP) / 3f;
    public static final float INV_ITEM_WIN_BTN_Y           = INV_ITEM_WINDOW_Y + INV_ITEM_WIN_BTN_PADDING;
    public static final float INV_ITEM_WIN_BTN_USE_X       = INV_ITEM_WINDOW_X + INV_ITEM_WIN_BTN_PADDING;
    public static final float INV_ITEM_WIN_BTN_DROP_X      = INV_ITEM_WIN_BTN_USE_X  + INV_ITEM_WIN_BTN_WIDTH + INV_ITEM_WIN_BTN_GAP;
    public static final float INV_ITEM_WIN_BTN_EXIT_X      = INV_ITEM_WIN_BTN_DROP_X + INV_ITEM_WIN_BTN_WIDTH + INV_ITEM_WIN_BTN_GAP;
}
