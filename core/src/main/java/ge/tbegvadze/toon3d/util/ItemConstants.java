package ge.tbegvadze.toon3d.util;

/** Item system constants — medical pickups, armour pickups, inventory, ammo, inventory UI. */
public final class ItemConstants {

    private ItemConstants() {}

    // Medical pickup system — stim-packs ('+') and field medkits ('H')
    public static final int   MEDKIT_STIM_HEAL                = 18;
    public static final int   MEDKIT_FULL_HEAL                = 50;
    public static final int   MEDKIT_TOTAL_CARRY_CAP          = 6;
    public static final int   MEDKIT_FULL_CARRY_CAP           = 3;
    public static final float PLAYER_HEAL_DURATION            = 0.18f;
    public static final float MEDKIT_STIM_SPRITE_HEIGHT       = 0.20f;
    public static final float MEDKIT_FULL_SPRITE_HEIGHT       = 0.30f;

    // Player stats
    public static final int   PLAYER_MAX_HEALTH               = 130;
    // Player armor — pool capped at 75; armour pickups feed directly into this pool
    public static final int   PLAYER_MAX_ARMOR                = 75;

    // Armour pickup system — shards ('a') and security vests ('A')
    public static final int   ARMOUR_SHARD_VALUE              = 8;
    public static final int   ARMOUR_VEST_VALUE               = 35;
    public static final float ARMOUR_SHARD_SPRITE_HEIGHT      = 0.25f;
    public static final float ARMOUR_VEST_SPRITE_HEIGHT       = 0.35f;
    // Fraction of each incoming hit that is absorbed by armour (depleting it instead of HP).
    public static final float ARMOUR_ABSORB_FRACTION          = 0.50f;

    // Inventory system — slot count and per-type stack caps
    // INVENTORY_SLOT_COUNT: 12 slots (4×3 grid) as redesigned in inventory-menu-order-3.
    // Ammo and armour bypass slots entirely; only consumables, weapons, key items,
    // mods, and credits occupy the slotted grid.
    public static final int INVENTORY_SLOT_COUNT          = 12;
    // ITEM_STACK_MAX_DEFAULT: generic fallback cap for any stackable not listed below.
    public static final int ITEM_STACK_MAX_DEFAULT        = 99;
    // Per-type stack caps — these are the definitive balance numbers; tune via constants only.
    public static final int ITEM_STACK_MAX_MEDKIT_SMALL   = 5;
    public static final int ITEM_STACK_MAX_MEDKIT_LARGE   = 3;
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

    public static final int   AMMO_BOX_BULLETS            = 30;
    public static final int   AMMO_BOX_SHELLS             = 12;
    public static final int   AMMO_BOX_CELLS              = 25;
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
    // Downward shift applied to floating pickups so they hover slightly closer to the floor.
    public static final float PICKUP_ITEM_GROUND_OFFSET_FRACTION = 0.10f;  // shift down 10% of sprite height

    // Start room — starter ammo granted on weapon selection (sufficient for first dungeon floor)
    public static final int START_ROOM_AMMO_BULLETS  = 150;
    public static final int START_ROOM_AMMO_SHELLS   = 24;
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

    // ItemWindow popup geometry — 960×580 centered at (640, 370)
    public static final float INV_ITEM_WIN_WIDTH              = 960f;
    public static final float INV_ITEM_WIN_HEIGHT             = 580f;
    public static final float INV_ITEM_WIN_CENTER_X           = 640f;
    public static final float INV_ITEM_WIN_CENTER_Y           = 370f;
    // Font scale multiplier — all ItemWindow text is scaled by this factor for readability on mobile.
    public static final float INV_ITEM_WIN_FONT_SCALE         = 1.3f;
    public static final float INV_ITEM_WIN_X                  = INV_ITEM_WIN_CENTER_X - INV_ITEM_WIN_WIDTH  / 2f;
    public static final float INV_ITEM_WIN_Y                  = INV_ITEM_WIN_CENTER_Y - INV_ITEM_WIN_HEIGHT / 2f;
    public static final float INV_ITEM_WIN_PADDING            = 18f;
    public static final float INV_ITEM_WIN_BORDER_THICK       = 2f;
    // Header tall enough to contain item name (1.3×FONT_SCALE ~34px) + gap + category badge (~16px).
    public static final float INV_ITEM_WIN_HEADER_H           = 68f;
    // Vertical distance from name draw-Y to category badge draw-Y in the header.
    // Default font line height × 1.3 × FONT_SCALE ≈ 20 × 1.69 ≈ 34px; 36px gives 2px clearance.
    public static final float INV_ITEM_WIN_HEADER_NAME_GAP    = 36f;
    public static final float INV_ITEM_WIN_HEADER_Y           = INV_ITEM_WIN_Y + INV_ITEM_WIN_HEIGHT - INV_ITEM_WIN_HEADER_H;
    public static final float INV_ITEM_WIN_FOOTER_H           = 56f;
    public static final float INV_ITEM_WIN_FOOTER_RULE_Y      = INV_ITEM_WIN_Y + INV_ITEM_WIN_FOOTER_H;
    public static final float INV_ITEM_WIN_BODY_Y             = INV_ITEM_WIN_FOOTER_RULE_Y;
    public static final float INV_ITEM_WIN_BODY_H             = INV_ITEM_WIN_HEADER_Y - INV_ITEM_WIN_BODY_Y;
    public static final float INV_ITEM_WIN_EXIT_BTN_SIZE      = 40f;
    public static final float INV_ITEM_WIN_EXIT_BTN_X         = INV_ITEM_WIN_X + INV_ITEM_WIN_WIDTH - INV_ITEM_WIN_PADDING - INV_ITEM_WIN_EXIT_BTN_SIZE;
    public static final float INV_ITEM_WIN_EXIT_BTN_Y         = INV_ITEM_WIN_HEADER_Y + (INV_ITEM_WIN_HEADER_H - INV_ITEM_WIN_EXIT_BTN_SIZE) / 2f;
    public static final float INV_ITEM_WIN_GLYPH_SIZE         = 60f;
    public static final float INV_ITEM_WIN_GLYPH_X            = INV_ITEM_WIN_X + INV_ITEM_WIN_PADDING;
    public static final float INV_ITEM_WIN_GLYPH_Y            = INV_ITEM_WIN_HEADER_Y + (INV_ITEM_WIN_HEADER_H - INV_ITEM_WIN_GLYPH_SIZE) / 2f;
    public static final float INV_ITEM_WIN_NAME_X             = INV_ITEM_WIN_GLYPH_X + INV_ITEM_WIN_GLYPH_SIZE + 12f;
    public static final float INV_ITEM_WIN_BODY_LEFT_X        = INV_ITEM_WIN_X + INV_ITEM_WIN_PADDING;
    public static final float INV_ITEM_WIN_BODY_LEFT_WIDTH    = 320f;
    public static final float INV_ITEM_WIN_DIVIDER_X          = INV_ITEM_WIN_BODY_LEFT_X + INV_ITEM_WIN_BODY_LEFT_WIDTH + 4f;
    public static final float INV_ITEM_WIN_BODY_RIGHT_X       = INV_ITEM_WIN_DIVIDER_X + 8f;
    public static final float INV_ITEM_WIN_BODY_RIGHT_WIDTH   = INV_ITEM_WIN_X + INV_ITEM_WIN_WIDTH - INV_ITEM_WIN_PADDING - INV_ITEM_WIN_BODY_RIGHT_X;
    public static final float INV_ITEM_WIN_STAT_ROW_H         = 22f;

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

    // Inventory Menu — Part 3: Item Grid Panel geometry (4×3 grid, 148px slots)
    public static final float INV_GRID_SLOT_SIZE        = 148f;
    public static final float INV_GRID_SLOT_GAP_COL     = 10f;
    public static final float INV_GRID_SLOT_GAP_ROW     = 10f;
    public static final int   INV_GRID_COLUMNS          = 4;
    public static final int   INV_GRID_ROWS             = 3;
    public static final float INV_GRID_ORIGIN_X         = 589f;
    public static final float INV_GRID_ORIGIN_Y         = 130f;
    public static final float INV_GRID_TAB_BAR_HEIGHT   = 36f;
    public static final float INV_GRID_TAB_BAR_Y        = 609f;
    public static final int   INV_TAB_COUNT             = 5;
    public static final float INV_TAB_WIDTH             = 138f;
    public static final float INV_TAB_ACTIVE_BG_ALPHA   = 0.20f;
    public static final float INV_TAB_DIM_ALPHA         = 0.40f;
    public static final float INV_GRID_CAT_DOT_SIZE       = 4f;
    public static final float INV_GRID_CAT_DOT_MARGIN_X   = 8f;
    // Top margin: dot bottom-left Y = slotY + SLOT_SIZE - CAT_DOT_MARGIN_TOP (Y-up = 148-15=133)
    public static final float INV_GRID_CAT_DOT_MARGIN_TOP = 15f;
    public static final float INV_GRID_QTY_LABEL_MARGIN   = 8f;
    public static final float INV_GRID_STAR_MARGIN_RIGHT  = 14f;
    public static final float INV_GRID_STAR_MARGIN_TOP    = 4f;
    public static final float INV_GRID_ACTIVE_PULSE_HZ    = 4f;
    public static final float INV_GRID_ACTIVE_PULSE_MIN   = 0.7f;
    // Category dot colors
    public static final float INV_CAT_DOT_WEAPON_R      = 1.00f;
    public static final float INV_CAT_DOT_WEAPON_G      = 0.72f;
    public static final float INV_CAT_DOT_WEAPON_B      = 0.00f;
    public static final float INV_CAT_DOT_CONSUMABLE_R  = 0.20f;
    public static final float INV_CAT_DOT_CONSUMABLE_G  = 0.90f;
    public static final float INV_CAT_DOT_CONSUMABLE_B  = 0.20f;
    public static final float INV_CAT_DOT_AMMO_R        = 0.75f;
    public static final float INV_CAT_DOT_AMMO_G        = 0.45f;
    public static final float INV_CAT_DOT_AMMO_B        = 0.10f;
    public static final float INV_CAT_DOT_KEY_ITEM_R    = 1.00f;
    public static final float INV_CAT_DOT_KEY_ITEM_G    = 0.10f;
    public static final float INV_CAT_DOT_KEY_ITEM_B    = 0.10f;
    public static final float INV_CAT_DOT_MOD_R         = 0.60f;
    public static final float INV_CAT_DOT_MOD_G         = 0.30f;
    public static final float INV_CAT_DOT_MOD_B         = 0.90f;
    public static final float INV_CAT_DOT_MISC_R        = 0.75f;
    public static final float INV_CAT_DOT_MISC_G        = 0.75f;
    public static final float INV_CAT_DOT_MISC_B        = 0.75f;

    // Credit chip floor pickups — 3 tiers with weighted random tier selection
    public static final int   CREDIT_SMALL_BASE            = 8;
    public static final int   CREDIT_SMALL_JITTER          = 2;
    public static final int   CREDIT_MEDIUM_BASE           = 25;
    public static final int   CREDIT_MEDIUM_JITTER         = 5;
    public static final int   CREDIT_LARGE_BASE            = 70;
    public static final int   CREDIT_LARGE_JITTER          = 15;
    // Spawn weights — proportional; SMALL+MEDIUM+LARGE need not sum to any specific value
    public static final int   CREDIT_SPAWN_WEIGHT_SMALL    = 70;
    public static final int   CREDIT_SPAWN_WEIGHT_MEDIUM   = 24;
    public static final int   CREDIT_SPAWN_WEIGHT_LARGE    = 6;
    public static final int   CREDIT_PICKUP_TEXTURE_SIZE   = 64;
    // Inventory header — credits readout position and colour
    public static final float INVENTORY_CREDITS_LABEL_X    = 40f;
    public static final float INVENTORY_CREDITS_GLYPH_SIZE = 16f;
    public static final float INVENTORY_CREDITS_COLOR_R    = 1.00f;
    public static final float INVENTORY_CREDITS_COLOR_G    = 0.85f;
    public static final float INVENTORY_CREDITS_COLOR_B    = 0.10f;

    // ItemWindow footer action buttons (Part 4 redesign — two buttons in footer zone)
    public static final float INV_ACTION_BTN_WIDTH  = 180f;
    public static final float INV_ACTION_BTN_HEIGHT = 38f;
    public static final float INV_ACTION_BTN_GAP    = 20f;
    // Centered vertically within the footer zone (window bottom to footer rule).
    public static final float INV_ACTION_BTN_Y      = INV_ITEM_WIN_Y + (INV_ITEM_WIN_FOOTER_H - INV_ACTION_BTN_HEIGHT) / 2f;
    // Centered horizontally in the window: (960 - 2*180 - 20) / 2 = 190 → X=160+190=350, but kept
    // symmetric around window centre (640): two 180px buttons with 20px gap → total 380px → start at 450.
    public static final float INV_ACTION_BTN_1_X    = INV_ITEM_WIN_X + (INV_ITEM_WIN_WIDTH - 2f * INV_ACTION_BTN_WIDTH - INV_ACTION_BTN_GAP) / 2f;
    public static final float INV_ACTION_BTN_2_X    = INV_ACTION_BTN_1_X + INV_ACTION_BTN_WIDTH + INV_ACTION_BTN_GAP;

    // Ability badge pill — shared by ItemWindow ability rows and AbilityWindow header — Part 5
    public static final float INV_ABILITY_BADGE_W = 58f;
    public static final float INV_ABILITY_BADGE_H = 18f;

    // ItemWindow ability rows in right column — Part 5
    public static final float INV_ITEM_WIN_ABILITY_ROW_H   = 62f;
    public static final float INV_ITEM_WIN_ABILITY_ROW_GAP = 8f;
    public static final float INV_ITEM_WIN_ABILITY_HDR_H   = 26f;

    // AbilityWindow popup geometry — Part 5
    public static final float INV_ABILITY_WIN_WIDTH         = 720f;
    public static final float INV_ABILITY_WIN_HEIGHT        = 440f;
    public static final float INV_ABILITY_WIN_X             = 280f;  // (1280 - 720) / 2
    public static final float INV_ABILITY_WIN_Y             = 140f;  // (720 - 440) / 2
    public static final float INV_ABILITY_WIN_PADDING       = 20f;
    public static final float INV_ABILITY_WIN_HEADER_H      = 40f;
    public static final float INV_ABILITY_WIN_EXIT_BTN_SIZE = 36f;
    public static final float INV_ABILITY_WIN_DIVIDER_Y     = INV_ABILITY_WIN_Y + 80f;
    // Font scale multiplier — all AbilityWindow text scaled for mobile readability.
    // At 2.2 the badge pill (18px tall) overflowed and bullet-line spacing (16px) was
    // smaller than the scaled line height (~24px), causing text overlap. 1.4 is the
    // maximum where badge text (~11px) and bullet lines (~13px) both fit their containers.
    public static final float INV_ABILITY_WIN_FONT_SCALE    = 1.4f;
}
