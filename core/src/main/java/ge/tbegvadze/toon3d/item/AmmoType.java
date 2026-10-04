package ge.tbegvadze.toon3d.item;

import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.WeaponConstants;
import ge.tbegvadze.toon3d.util.ItemConstants;

/**
 * The four ammo types carried in the marine's reserve pool.
 *
 * Each constant stores the data needed by Level, PropRenderer, HudRenderer, and Inventory:
 *   displayName    — label shown in the HUD right panel and pickup toasts.
 *   reserveCap     — maximum units storable in the reserve pool.
 *   pickupTileChar — the level-tile character that grants this ammo on player step.
 *   amountPerBox   — units granted when the pickup tile is collected.
 *   hudRed/Green/Blue — HUD/billboard tint colour (0–1 floats).
 *
 * Weapon mappings: Pistol→BULLETS, Shotgun→SHELLS, Plasma→CELLS, Rocket→ROCKETS.
 */
public enum AmmoType {

    BULLETS(
            "Bullets",
            ItemConstants.AMMO_RESERVE_CAP_BULLETS,
            '6',
            ItemConstants.AMMO_BOX_BULLETS,
            0.72f, 0.48f, 0.18f,  // copper
            BalanceConfig.AMMO_SUPPLY_GENEROSITY_BULLETS,
            BalanceConfig.AMMO_BANKING_FLOORS_BULLETS
    ),

    SHELLS(
            "Shells",
            ItemConstants.AMMO_RESERVE_CAP_SHELLS,
            '7',
            ItemConstants.AMMO_BOX_SHELLS,
            0.78f, 0.68f, 0.12f,  // brass/yellow
            BalanceConfig.AMMO_SUPPLY_GENEROSITY_SHELLS,
            BalanceConfig.AMMO_BANKING_FLOORS_SHELLS
    ),

    CELLS(
            "Plasma Cells",
            ItemConstants.AMMO_RESERVE_CAP_CELLS,
            '8',
            ItemConstants.AMMO_BOX_CELLS,
            0.10f, 0.80f, 0.90f,  // cyan
            BalanceConfig.AMMO_SUPPLY_GENEROSITY_CELLS,
            BalanceConfig.AMMO_BANKING_FLOORS_CELLS
    ),

    ROCKETS(
            "Rockets",
            ItemConstants.AMMO_RESERVE_CAP_ROCKETS,
            '9',
            ItemConstants.AMMO_BOX_ROCKETS,
            0.45f, 0.55f, 0.20f,  // olive
            BalanceConfig.AMMO_SUPPLY_GENEROSITY_ROCKETS,
            BalanceConfig.AMMO_BANKING_FLOORS_ROCKETS
    ),

    SLUGS(
            "Slugs",
            WeaponConstants.RAILGUN_MAX_SLUGS,
            '0',
            WeaponConstants.RAILGUN_PICKUP_SLUGS,
            0.85f, 0.90f, 0.95f,  // silver/white
            BalanceConfig.AMMO_SUPPLY_GENEROSITY_SLUGS,
            BalanceConfig.AMMO_BANKING_FLOORS_SLUGS
    );

    private final String displayName;
    private final int    reserveCap;
    private final char   pickupTileChar;
    private final int    amountPerBox;
    private final float  hudRed;
    private final float  hudGreen;
    private final float  hudBlue;
    private final float  supplyGenerosity;
    private final float  bankingFloorsTarget;

    AmmoType(String displayName, int reserveCap, char pickupTileChar, int amountPerBox,
             float hudRed, float hudGreen, float hudBlue, float supplyGenerosity, float bankingFloorsTarget) {
        this.displayName    = displayName;
        this.reserveCap     = reserveCap;
        this.pickupTileChar = pickupTileChar;
        this.amountPerBox   = amountPerBox;
        this.hudRed         = hudRed;
        this.hudGreen       = hudGreen;
        this.hudBlue        = hudBlue;
        this.supplyGenerosity    = supplyGenerosity;
        this.bankingFloorsTarget = bankingFloorsTarget;
    }

    public String getDisplayName()    { return displayName; }
    public int    getReserveCap()     { return reserveCap; }
    public char   getPickupTileChar() { return pickupTileChar; }
    public int    getAmountPerBox()   { return amountPerBox; }
    public float  getHudRed()         { return hudRed; }
    public float  getHudGreen()       { return hudGreen; }
    public float  getHudBlue()        { return hudBlue; }
    /**
     * A-1 (balance-overhaul order 3): this type's weight on the SupplyPlanner's carried / off-type split
     * (BalanceConfig SECTION 22); the planner re-normalises so total planned ammo is unchanged.
     */
    public float  getSupplyGenerosity()    { return supplyGenerosity; }
    /** A-1: floors of model-floor demand a full reserve of this type is fitted to bank (R-SUPPLY reserve banks). */
    public float  getBankingFloorsTarget() { return bankingFloorsTarget; }

    /**
     * Returns the AmmoType whose pickupTileChar matches the given cell,
     * or null if the cell is not an ammo pickup.
     */
    public static AmmoType fromPickupChar(char cell) {
        for (AmmoType type : values()) {
            if (type.pickupTileChar == cell) return type;
        }
        return null;
    }

    /** Returns the inventory ItemType that holds reserve ammo of this type. */
    public ItemType getItemType() {
        switch (this) {
            case BULLETS: return ItemType.AMMO_BULLETS;
            case SHELLS:  return ItemType.AMMO_SHELLS;
            case CELLS:   return ItemType.AMMO_CELLS;
            case ROCKETS: return ItemType.AMMO_ROCKETS;
            case SLUGS:   return ItemType.AMMO_SLUGS;
            default: throw new IllegalStateException("Unhandled AmmoType: " + this);
        }
    }
}
