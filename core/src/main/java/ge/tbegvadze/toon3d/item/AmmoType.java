package ge.tbegvadze.toon3d.item;

import ge.tbegvadze.toon3d.util.Constants;

/**
 * The four ammo types carried in the marine's reserve pool.
 *
 * Each constant stores the data needed by AmmoPool, Level, PropRenderer, and HudRenderer:
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
            Constants.AMMO_RESERVE_CAP_BULLETS,
            '6',
            Constants.AMMO_BOX_BULLETS,
            0.72f, 0.48f, 0.18f   // copper
    ),

    SHELLS(
            "Shells",
            Constants.AMMO_RESERVE_CAP_SHELLS,
            '7',
            Constants.AMMO_BOX_SHELLS,
            0.78f, 0.68f, 0.12f   // brass/yellow
    ),

    CELLS(
            "Plasma Cells",
            Constants.AMMO_RESERVE_CAP_CELLS,
            '8',
            Constants.AMMO_BOX_CELLS,
            0.10f, 0.80f, 0.90f   // cyan
    ),

    ROCKETS(
            "Rockets",
            Constants.AMMO_RESERVE_CAP_ROCKETS,
            '9',
            Constants.AMMO_BOX_ROCKETS,
            0.45f, 0.55f, 0.20f   // olive
    );

    private final String displayName;
    private final int    reserveCap;
    private final char   pickupTileChar;
    private final int    amountPerBox;
    private final float  hudRed;
    private final float  hudGreen;
    private final float  hudBlue;

    AmmoType(String displayName, int reserveCap, char pickupTileChar, int amountPerBox,
             float hudRed, float hudGreen, float hudBlue) {
        this.displayName    = displayName;
        this.reserveCap     = reserveCap;
        this.pickupTileChar = pickupTileChar;
        this.amountPerBox   = amountPerBox;
        this.hudRed         = hudRed;
        this.hudGreen       = hudGreen;
        this.hudBlue        = hudBlue;
    }

    public String getDisplayName()    { return displayName; }
    public int    getReserveCap()     { return reserveCap; }
    public char   getPickupTileChar() { return pickupTileChar; }
    public int    getAmountPerBox()   { return amountPerBox; }
    public float  getHudRed()         { return hudRed; }
    public float  getHudGreen()       { return hudGreen; }
    public float  getHudBlue()        { return hudBlue; }

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
}
