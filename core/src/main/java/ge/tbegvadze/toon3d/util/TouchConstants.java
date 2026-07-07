package ge.tbegvadze.toon3d.util;

/** Touch controller layout and visual constants — button size, grid, alpha, glow, icons. */
public final class TouchConstants {

    private TouchConstants() {}

    // Touch Controller — 4-row grid, right side of screen, all buttons 96×96
    // Grid columns (center X): left=1004, center=1100, right=1196
    // Grid rows   (center Y): row1(BACK)=200, row2(FIRE/ROT)=296, row3(FWD/STR)=392, row4(RELOAD/SKIP)=488
    // Safe zone: x 956..1244, y 152..536; above HUD (y 0..130), avoids mini-map (top-left)
    public static final float TOUCH_BUTTON_SIZE          = 96f;
    public static final float TOUCH_BUTTON_CORNER_RADIUS = 16f;
    public static final float TOUCH_GRID_CENTER_X        = 1100f;  // center-column X of right combat cluster
    public static final float TOUCH_GRID_LEFT_CENTER_X  = 180f;   // center X of left secondary cluster (heal, inventory)
    public static final float TOUCH_GRID_BASE_Y          = 200f;   // center Y of bottom row (BACK)
    public static final float TOUCH_GRID_ARM_OFFSET      = 96f;    // row & column spacing (= button size → rows touch)
    public static final float TOUCH_FILL_ALPHA_IDLE      = 0.22f;
    public static final float TOUCH_FILL_ALPHA_PRESSED   = 0.40f;
    public static final float TOUCH_RIM_ALPHA            = 0.55f;
    public static final float TOUCH_ICON_ALPHA_IDLE      = 0.70f;
    public static final float TOUCH_ICON_ALPHA_PRESSED   = 1.00f;
    public static final float TOUCH_ICON_ALPHA_LOCKED    = 0.35f;
    public static final float TOUCH_PRESS_GLOW_DURATION  = 0.12f;
    // Tap-input buffer window: a tap registered while the player is mid-action is kept
    // alive for this many seconds so it is consumed on the first IDLE frame after completion.
    public static final float TOUCH_TAP_BUFFER_SECONDS   = 0.15f;
    public static final float TOUCH_ICON_EXTENT          = 22f;
    public static final float TOUCH_RIM_THICKNESS        = 3f;
    public static final int   TOUCH_ARC_SEGMENTS         = 8;

    // Contextual INSPECT button — left secondary cluster, row 1 (base Y), starts hidden
    public static final float INSPECT_BUTTON_CENTER_X   = 180f;
    public static final float INSPECT_BUTTON_CENTER_Y   = 200f;
    public static final float INSPECT_BUTTON_SIZE       = 96f;

    // Contextual USE button — shown only when the player faces a UAC Fabricator vending machine
    // (shop_order_1). Centred on the screen so it's unmistakable and easy to reach with either
    // thumb; the 3D view and every other HUD element (bottom chrome, mini-map, weapon sprite)
    // stays clear of screen centre, so it never overlaps anything else while visible.
    public static final float USE_BUTTON_CENTER_X       = Constants.WORLD_WIDTH  / 2f;
    public static final float USE_BUTTON_CENTER_Y       = Constants.WORLD_HEIGHT / 2f;
    public static final float USE_BUTTON_SIZE           = 96f;
}
