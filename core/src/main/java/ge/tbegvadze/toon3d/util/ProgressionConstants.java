package ge.tbegvadze.toon3d.util;

/** Progression and permadeath constants — debug flag, death beat, persistent stats key. */
public final class ProgressionConstants {

    private ProgressionConstants() {}

    // Permadeath — death beat and persistent stats
    public static boolean debug                         = false;  // immortal mode; flip to true for testing
    public static final float  DEATH_BEAT_DURATION_SECONDS = 1.0f;
    public static final String STATS_PREFS_NAME             = "toon3d_records";

    // ---- Build tag chip colours (balance-overhaul order 5, CP5) ------------------------------------------
    // The six weapon tags reuse the order-3 damage-class palette (WeaponConstants.DAMAGE_CLASS_COLOR_*) through
    // render/BuildTagPalette; only the three tags that own no weapon class need a colour of their own.
    public static final float BUILD_TAG_BULWARK_RED     = 0.30f;  // blue
    public static final float BUILD_TAG_BULWARK_GREEN   = 0.55f;
    public static final float BUILD_TAG_BULWARK_BLUE    = 1.00f;
    public static final float BUILD_TAG_VITALITY_RED    = 0.30f;  // green
    public static final float BUILD_TAG_VITALITY_GREEN  = 0.90f;
    public static final float BUILD_TAG_VITALITY_BLUE   = 0.40f;
    public static final float BUILD_TAG_SCAVENGER_RED   = 0.80f;  // brass
    public static final float BUILD_TAG_SCAVENGER_GREEN = 0.62f;
    public static final float BUILD_TAG_SCAVENGER_BLUE  = 0.25f;

    /** SYNERGY ribbon / label colour on a level-up plate (amber). */
    public static final float BUILD_SYNERGY_RED   = ArtDirection.SUIT_AMBER_RED;
    public static final float BUILD_SYNERGY_GREEN = ArtDirection.SUIT_AMBER_GREEN;
    public static final float BUILD_SYNERGY_BLUE  = ArtDirection.SUIT_AMBER_BLUE;

    /** A keystone's cost line (the order-5 spec's cost colour, red). */
    public static final float BUILD_COST_RED   = CombatPalette.DAMAGE_RED;
    public static final float BUILD_COST_GREEN = CombatPalette.DAMAGE_GREEN;
    public static final float BUILD_COST_BLUE  = CombatPalette.DAMAGE_BLUE;

    /** Keystone gold frame. */
    public static final float BUILD_KEYSTONE_GOLD_RED   = 1.00f;
    public static final float BUILD_KEYSTONE_GOLD_GREEN = 0.84f;
    public static final float BUILD_KEYSTONE_GOLD_BLUE  = 0.20f;

    // ---- Shared build-UI chip / pip styling (balance-overhaul order 5; BuildPagePanel + LevelUpOverlayRenderer) ----
    public static final float BUILD_CHIP_FONT_SCALE = 1.0f;
    public static final float BUILD_CHIP_HEIGHT     = 26f;
    public static final float BUILD_CHIP_TEXT_PAD   = 14f;   // chip side padding around its text
    public static final float BUILD_PAGE_PIP_RADIUS = 7f;
    public static final float BUILD_PAGE_PIP_SPACING = 26f;
    /** Dark text drawn on a tag-coloured chip. */
    public static final float BUILD_CHIP_TEXT_RED = ArtDirection.UI_INK_BLACK_RED,
            BUILD_CHIP_TEXT_GREEN = ArtDirection.UI_INK_BLACK_GREEN, BUILD_CHIP_TEXT_BLUE = ArtDirection.UI_INK_BLACK_BLUE;
    /** Empty rank pip fill. */
    public static final float BUILD_PIP_EMPTY_RED = ArtDirection.UI_INK_BLACK_RED,
            BUILD_PIP_EMPTY_GREEN = ArtDirection.UI_INK_BLACK_GREEN, BUILD_PIP_EMPTY_BLUE = ArtDirection.UI_INK_BLACK_BLUE;
    /** Empty rank pip ring. */
    public static final float BUILD_PIP_RING_RED = ArtDirection.UI_LINE_RED,
            BUILD_PIP_RING_GREEN = ArtDirection.UI_LINE_GREEN, BUILD_PIP_RING_BLUE = ArtDirection.UI_LINE_BLUE;

    // ---- Inventory BUILD page (render/BuildPagePanel), world units ----
    public static final float BUILD_PAGE_TOP_PAD          = 10f;
    public static final float BUILD_PAGE_FOOTER_HEIGHT    = 62f;
    public static final float BUILD_PAGE_ROW_HEIGHT       = 38f;
    public static final float BUILD_PAGE_ROW_TEXT_RISE    = 12f;   // baseline above the row bottom
    public static final float BUILD_PAGE_ROW_BASELINE_EXTRA = 6f;  // extra lift on the text baseline
    public static final float BUILD_PAGE_ROW_INSET        = 10f;   // stripe / keystone frame side inset
    public static final float BUILD_PAGE_KEYSTONE_FRAME_INSET = 3f; // keystone frame vertical shrink per side
    public static final float BUILD_PAGE_NAME_OFFSET_X    = 40f;
    public static final float BUILD_PAGE_PIPS_OFFSET_X    = 330f;
    public static final float BUILD_PAGE_EFFECT_OFFSET_X  = 440f;
    public static final float BUILD_PAGE_EFFECT_RIGHT_MARGIN = 20f;
    public static final float BUILD_PAGE_CHIP_OFFSET_X    = 20f;
    public static final float BUILD_PAGE_CHIP_TEXT_DROP   = 2f;    // chip text baseline drop below row baseline
    public static final float BUILD_PAGE_SCALE_NAME       = 1.15f;
    public static final float BUILD_PAGE_SCALE_EFFECT     = 0.9f;
    public static final float BUILD_PAGE_SCALE_EMPTY      = 1.5f;
    public static final float BUILD_PAGE_SCALE_PLATE      = 1.2f;
    public static final float BUILD_PAGE_PLATE_WIDTH      = 90f;
    public static final float BUILD_PAGE_PLATE_HEIGHT     = 46f;
    public static final float BUILD_PAGE_PLATE_GAP        = 12f;
    public static final float BUILD_PAGE_PLATE_BOTTOM_MARGIN = 8f;
    public static final float BUILD_PAGE_PLATE_RIGHT_MARGIN  = 12f;
    public static final float BUILD_PAGE_COUNTER_OFFSET_X = 180f;  // "1-9 / 30" label left of the UP plate
    public static final float BUILD_PAGE_BG_RED = ArtDirection.UI_PANEL_RED,
            BUILD_PAGE_BG_GREEN = ArtDirection.UI_PANEL_GREEN, BUILD_PAGE_BG_BLUE = ArtDirection.UI_PANEL_BLUE,
            BUILD_PAGE_BG_ALPHA = ArtDirection.UI_PANEL_ALPHA_SCREEN;
    public static final float BUILD_PAGE_BORDER_RED = ArtDirection.UI_LINE_RED,
            BUILD_PAGE_BORDER_GREEN = ArtDirection.UI_LINE_GREEN, BUILD_PAGE_BORDER_BLUE = ArtDirection.UI_LINE_BLUE, BUILD_PAGE_BORDER_ALPHA = 1f;
    public static final float BUILD_PAGE_STRIPE_RED = 1f, BUILD_PAGE_STRIPE_GREEN = 1f, BUILD_PAGE_STRIPE_BLUE = 1f, BUILD_PAGE_STRIPE_ALPHA = 0.04f;
    public static final float BUILD_PAGE_TEXT_MAIN_RED = ArtDirection.UI_TEXT_PRIMARY_RED,
            BUILD_PAGE_TEXT_MAIN_GREEN = ArtDirection.UI_TEXT_PRIMARY_GREEN, BUILD_PAGE_TEXT_MAIN_BLUE = ArtDirection.UI_TEXT_PRIMARY_BLUE;
    public static final float BUILD_PAGE_TEXT_DIM_RED = ArtDirection.UI_TEXT_SECONDARY_RED,
            BUILD_PAGE_TEXT_DIM_GREEN = ArtDirection.UI_TEXT_SECONDARY_GREEN, BUILD_PAGE_TEXT_DIM_BLUE = ArtDirection.UI_TEXT_SECONDARY_BLUE;
    public static final float BUILD_PAGE_PLATE_BG_RED = ArtDirection.UI_PANEL_RED,
            BUILD_PAGE_PLATE_BG_GREEN = ArtDirection.UI_PANEL_GREEN, BUILD_PAGE_PLATE_BG_BLUE = ArtDirection.UI_PANEL_BLUE;
    public static final float BUILD_PAGE_PLATE_TEXT_RED = ArtDirection.UI_TEXT_PRIMARY_RED,
            BUILD_PAGE_PLATE_TEXT_GREEN = ArtDirection.UI_TEXT_PRIMARY_GREEN, BUILD_PAGE_PLATE_TEXT_BLUE = ArtDirection.UI_TEXT_PRIMARY_BLUE;

    // ---- Level-up perk plates (progression/LevelUpOverlayRenderer), order-5 additions ----
    public static final float LEVEL_UP_CHIP_TOP_OFFSET    = 72f;   // tag chip top edge below the card top
    public static final float LEVEL_UP_CHIP_TEXT_RISE    = 19f;   // chip text baseline above the chip bottom
    public static final float LEVEL_UP_PIP_CENTER_Y      = 160f;  // rank pip row, below the card top
    public static final float LEVEL_UP_PIP_RADIUS        = 8f;
    public static final float LEVEL_UP_PIP_SPACING       = 30f;
    public static final float LEVEL_UP_PIP_NEXT_RING     = 3f;    // extra ring radius around the highlighted next pip
    public static final float LEVEL_UP_TAP_BOTTOM_OFFSET = 22f;   // "TAP TO CHOOSE" baseline above the card bottom
    public static final float LEVEL_UP_SYNERGY_RIBBON_SIZE = 46f; // right-angle corner triangle legs
    public static final float LEVEL_UP_SYNERGY_LABEL_EXTRA_WIDTH = 12f;
    public static final float LEVEL_UP_KEYSTONE_INNER_FRAME_INSET = 8f;
    public static final float LEVEL_UP_KEYSTONE_GLOW_ALPHA = 0.30f;
    public static final float LEVEL_UP_TAG_BODY_BRIGHTNESS = 0.20f;
    public static final float LEVEL_UP_TAG_BODY_ALPHA      = 0.98f;
    public static final float LEVEL_UP_TAG_STRIP_BRIGHTNESS = 0.92f;
}
