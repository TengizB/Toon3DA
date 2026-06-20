package ge.tbegvadze.toon3d.util;

/** HUD geometry constants — panels, bars, weapon slots, death overlay. */
public final class HudConstants {

    private HudConstants() {}

    // HUD geometry — left panel anchored to bottom-left, y 0..HUD_HEIGHT.
    // Panel height increased from 168 to 206 to fit the weapon slot strip below the XP bar.
    public static final float HUD_HEIGHT                      = 206f;
    public static final float HUD_LEFT_PANEL_WIDTH            = 420f;
    public static final float HUD_PANEL_GUTTER                = 4f;
    public static final float HUD_PANEL_INSET                 = 6f;
    public static final float HUD_RIVET_RADIUS                = 2.5f;
    public static final float HUD_LED_RADIUS                  = 3f;
    public static final float HUD_PANEL_ALPHA                 = 0.82f;
    // Full-width bar layout: [label x=10] [bar x=36..374] [number x=380]
    public static final float HUD_BAR_LABEL_X                 = 10f;
    public static final float HUD_BAR_START_X                 = 36f;
    public static final float HUD_BAR_FULL_WIDTH              = 338f;
    public static final float HUD_BAR_NUMBER_X                = 380f;
    public static final float HUD_BAR_HEIGHT                  = 18f;
    public static final int   HUD_BAR_SEGMENT_COUNT           = 20;
    public static final float HUD_BAR_SEGMENT_GAP             = 2f;
    public static final float HUD_BAR_LERP_RATE               = 3.5f;
    // Bar Y positions (bottom edge); bars span y: barY .. barY+HUD_BAR_HEIGHT
    // All bars shifted up by 38 vs the previous layout to make room for the weapon slot strip.
    public static final float HUD_HP_BAR_Y                    = 166f;
    public static final float HUD_AR_BAR_Y                    = 128f;
    public static final float HUD_CLIP_BAR_Y                  = 90f;
    // XP bar — gold/amber segmented bar showing progress toward next player level
    public static final float HUD_XP_BAR_Y                    = 52f;
    // HUD animation
    public static final float HUD_PULSE_HZ                    = 4f;
    public static final float HUD_LOW_HP_THRESHOLD            = 0.25f;

    // Death overlay — full-screen incursion-terminated report
    public static final float DEATH_OVERLAY_PANEL_X        = 280f;
    public static final float DEATH_OVERLAY_PANEL_Y        = 80f;
    public static final float DEATH_OVERLAY_PANEL_WIDTH    = 720f;
    public static final float DEATH_OVERLAY_PANEL_HEIGHT   = 560f;
    public static final float DEATH_OVERLAY_LABEL_X        = DEATH_OVERLAY_PANEL_X + 60f;
    public static final float DEATH_OVERLAY_VALUE_X_MAX    = DEATH_OVERLAY_PANEL_X + DEATH_OVERLAY_PANEL_WIDTH - 60f;
    public static final float DEATH_OVERLAY_HEADER_SCALE   = 2.5f;
    public static final float DEATH_OVERLAY_SUBHEAD_SCALE  = 1.3f;
    public static final float DEATH_OVERLAY_STAT_SCALE     = 1.2f;
    public static final float DEATH_OVERLAY_NEWBEST_SCALE  = 0.9f;
    public static final float DEATH_OVERLAY_FLAVOR_SCALE   = 1.0f;
    public static final float DEATH_OVERLAY_PROMPT_SCALE   = 1.3f;
    // Y positions relative to panel top (PANEL_Y + PANEL_HEIGHT)
    public static final float DEATH_OVERLAY_HEADER_Y_BELOW_TOP   = 55f;
    public static final float DEATH_OVERLAY_SUBHEAD_Y_BELOW_TOP  = 100f;
    public static final float DEATH_OVERLAY_FIRST_STAT_Y_BELOW_TOP = 175f;
    public static final float DEATH_OVERLAY_STAT_LINE_STEP        = 48f;
    // Y positions relative to panel bottom (PANEL_Y)
    public static final float DEATH_OVERLAY_FLAVOR_Y_ABOVE_BOTTOM = 130f;
    public static final float DEATH_OVERLAY_PROMPT_Y_ABOVE_BOTTOM = 50f;
    // Horizontal gap between right-edge of value text and "NEW BEST" tag
    public static final float DEATH_OVERLAY_NEWBEST_GAP            = 18f;

    // Status icon row — small procedural icons at the bottom-left of the left panel
    public static final float HUD_STATUS_ICON_SIZE      = 14f;
    public static final float HUD_STATUS_ICON_GAP       = 4f;
    public static final float HUD_STATUS_ROW_LOCAL_X    = 24f;
    public static final float HUD_STATUS_ROW_LOCAL_Y    = 4f;

    // Legacy weapon inspect constants — kept for compilation; renderer will be replaced
    public static final float WEAPON_INSPECT_CARD_WIDTH      = 640f;
    public static final float WEAPON_INSPECT_CARD_HEIGHT     = 460f;
    public static final float WEAPON_INSPECT_CARD_ORIGIN_X   = 320f;
    public static final float WEAPON_INSPECT_CARD_ORIGIN_Y   = 130f;
    public static final float WEAPON_INSPECT_PANEL_ALPHA     = 0.92f;
    public static final float WEAPON_INSPECT_BUTTON_WIDTH    = 200f;
    public static final float WEAPON_INSPECT_BUTTON_HEIGHT   = 54f;
    public static final float WEAPON_INSPECT_FONT_SCALE      = 1.8f;
    public static final float WEAPON_INSPECT_ABILITY_ROW_Y_ABOVE_CONTENT = 24f;

    // Weapon card — single-screen pickup / compare modal (replaces old two-phase inspect)
    public static final float WEAPON_CARD_WIDTH          = 760f;
    public static final float WEAPON_CARD_HEIGHT         = 530f;
    public static final float WEAPON_CARD_ORIGIN_X       = 260f;   // (1280 - 760) / 2
    public static final float WEAPON_CARD_ORIGIN_Y       = 95f;    // (720 - 530) / 2
    public static final float WEAPON_CARD_PANEL_ALPHA    = 0.94f;
    public static final float WEAPON_CARD_FONT_SCALE     = 1.8f;
    // Zone heights inside the card (from top): header, stats, abilities, action, footer
    public static final float WEAPON_CARD_HEADER_HEIGHT  = 50f;
    public static final float WEAPON_CARD_STAT_ROW_H     = 36f;    // 4 rows × 36 = 144px — fits stats zone between header and ability strip
    public static final float WEAPON_CARD_ABILITY_H      = 55f;    // ability strip below stats
    public static final float WEAPON_CARD_ACTION_H       = 185f;   // equip button OR slot rows
    public static final float WEAPON_CARD_FOOTER_H       = 68f;    // close + convert strip
    // Large equip button (free-slot fast lane)
    public static final float WEAPON_EQUIP_BUTTON_WIDTH  = 520f;
    public static final float WEAPON_EQUIP_BUTTON_HEIGHT = 84f;
    // Inline slot rows (full loadout)
    public static final float WEAPON_SLOT_ROW_HEIGHT     = 52f;
    public static final float WEAPON_SLOT_ROW_GAP        = 8f;
    // Swap button on the right edge of each slot row
    public static final float WEAPON_SWAP_BUTTON_WIDTH   = 150f;
    public static final float WEAPON_SWAP_BUTTON_HEIGHT  = 46f;
    // Footer buttons
    public static final float WEAPON_CLOSE_BUTTON_WIDTH  = 160f;
    public static final float WEAPON_CLOSE_BUTTON_HEIGHT = 52f;
    public static final float WEAPON_CONVERT_BUTTON_WIDTH  = 220f;
    public static final float WEAPON_CONVERT_BUTTON_HEIGHT = 52f;
    // Ground-weapon name label shown in HUD when card is closed but player is on weapon tile
    public static final float WEAPON_NAME_LABEL_Y        = 224f;   // HUD_HEIGHT + 18
    // Stat bar normalisers — only affect visual fill, not game logic
    public static final int   WEAPON_STAT_BAR_MAX_DAMAGE = 60;
    public static final int   WEAPON_STAT_BAR_MAX_RANGE  = 20;

    // Boss HP bar — rendered across the top of the screen during boss encounters
    public static final float BOSS_HP_BAR_MARGIN            = 80f;
    public static final float BOSS_HP_BAR_HEIGHT            = 24f;
    public static final float BOSS_HP_BAR_ABOVE_GAP         = 4f;   // gap between letterbox base and HP bar top
    public static final float BOSS_HP_BAR_NAME_GAP          = 4f;   // gap between HP bar top and name baseline
    public static final float BOSS_LETTERBOX_HEIGHT         = 120f;
    public static final float BOSS_BANNER_DURATION_SECONDS  = 1.2f;
    public static final float BOSS_BANNER_FADE_IN_FRACTION  = 0.20f; // first 20% of banner duration fades in
    public static final float BOSS_BANNER_FADE_OUT_START    = 0.70f; // last 30% fades out
    // Font scales used by BossHudRenderer
    public static final float BOSS_INTRO_NAME_FONT_SCALE    = 2.0f;
    public static final float BOSS_INTRO_EPITHET_FONT_SCALE = 1.2f;
    public static final float BOSS_HP_NAME_FONT_SCALE       = 0.80f;
    public static final float BOSS_BANNER_FONT_SCALE        = 1.6f;
    // Intro letterbox slide-in occupies the first 40% of the total intro duration
    public static final float BOSS_INTRO_SLIDE_FRACTION     = 0.40f;
    // Alpha ramp for name text: fully visible after this fraction of the slide duration
    public static final float BOSS_INTRO_TEXT_RAMP_FRACTION = 0.50f;
    // Vertical centering offsets inside the letterbox for name/epithet text
    public static final float BOSS_INTRO_NAME_Y_OFFSET      = 10f;
    public static final float BOSS_INTRO_EPITHET_Y_GAP      = 6f;
}
