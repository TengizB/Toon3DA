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

    // Inventory UI overlay — all values in world units; placeholders
    public static final int   INVENTORY_GRID_COLUMNS        = 4;
    public static final float INV_SLOT_SIZE                  = 96f;
    public static final float INV_SLOT_GAP                   = 12f;
    public static final float INV_GRID_ORIGIN_X              = 80f;
    public static final float INV_GRID_ORIGIN_Y              = 620f;
    public static final float INV_DETAIL_PANEL_X             = 740f;
    public static final float INV_DETAIL_PANEL_WIDTH         = 460f;
    public static final float INV_SCRIM_ALPHA                = 0.60f;
    public static final float INV_PANEL_ALPHA                = 0.92f;
    public static final float INV_SELECT_BORDER_THICKNESS    = 3f;
    public static final float INV_FLASH_SECONDS              = 0.45f;
}
