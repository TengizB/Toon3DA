package ge.tbegvadze.toon3d.util;

/** HUD geometry constants — panels, bars, weapon slots, death overlay. */
public final class HudConstants {

    private HudConstants() {}

    // =====================================================================
    // Left vitals panel — anchored to the bottom-left corner (x 0..WIDTH, y 0..HEIGHT).
    // A vertical stack of "vital rows": each row is [label] [gradient bar] [value] where the
    // label sits to the LEFT of the bar and the value to the RIGHT — text is never drawn on top
    // of a bar, so bars and numbers can never overlap. Bars are smooth per-vertex gradients
    // (no chunky segments) so they read crisply at any device resolution. Below the bars sit the
    // numbered weapon-slot strip and a thin status-effect icon row.
    // =====================================================================
    public static final float HUD_HEIGHT                      = 206f;
    public static final float HUD_LEFT_PANEL_WIDTH            = 420f;
    public static final float HUD_PANEL_INSET                 = 6f;
    public static final float HUD_RIVET_RADIUS                = 2.5f;
    public static final float HUD_LED_RADIUS                  = 3f;
    // Slightly more opaque than the old 0.82 so text reads cleanly over the busy 3D view.
    public static final float HUD_PANEL_ALPHA                 = 0.88f;

    // Vital row columns (panel-local X)
    public static final float HUD_ROW_LABEL_X                 = 14f;    // left-aligned 2-letter label
    public static final float HUD_BAR_X                       = 56f;    // bar left edge
    public static final float HUD_BAR_WIDTH                   = 232f;   // bar width (ends at x=288)
    public static final float HUD_BAR_HEIGHT                  = 20f;
    public static final float HUD_VALUE_RIGHT_X               = 406f;   // right edge for right-aligned value text
    public static final float HUD_BAR_LERP_RATE              = 3.5f;    // bar fill glide speed (fraction units/sec)
    // Bars are split into small rectangles (segments). HP/AR/XP use a fixed tick count; the ammo
    // bar draws one tick per round (falling back to the fixed count for oversized magazines).
    public static final int   HUD_BAR_SEGMENT_COUNT           = 20;
    public static final float HUD_BAR_SEGMENT_GAP             = 2f;

    // Vital row bar Y (bottom edge); bars span y: barY .. barY + HUD_BAR_HEIGHT. Row pitch = 28px.
    public static final float HUD_HP_BAR_Y                    = 172f;
    public static final float HUD_AR_BAR_Y                    = 144f;
    public static final float HUD_CLIP_BAR_Y                  = 116f;
    public static final float HUD_XP_BAR_Y                    = 88f;

    // Weapon-slot strip — numbered slots with active highlight, below the XP row
    public static final float HUD_SLOT_STRIP_Y                = 42f;
    public static final float HUD_SLOT_STRIP_HEIGHT           = 38f;
    public static final float HUD_SLOT_SIDE_PADDING           = 14f;
    public static final float HUD_SLOT_GAP                    = 6f;

    // Text scales — the default BitmapFont is 15px; we never shrink the hero numbers below ~0.9 so
    // glyphs stay legible, and every glyph gets a dark drop-shadow for contrast.
    public static final float HUD_LABEL_SCALE                 = 0.95f;
    public static final float HUD_VALUE_SCALE                 = 1.05f;
    public static final float HUD_HP_VALUE_SCALE              = 1.20f;
    public static final float HUD_CLIP_VALUE_SCALE            = 0.90f;
    public static final float HUD_SLOT_NUMBER_SCALE           = 0.80f;
    public static final float HUD_SLOT_NAME_SCALE             = 0.78f;
    public static final float HUD_TEXT_SHADOW_OFFSET          = 1.5f;

    // HUD animation
    public static final float HUD_PULSE_HZ                    = 4f;
    public static final float HUD_LOW_HP_THRESHOLD            = 0.25f;
    // Medkit reminder: when HP is at/below this fraction AND the player still holds a medical
    // charge, HudRenderer flashes a "USE MEDKIT" prompt centred in the HUD gap. Set slightly above
    // the low-HP pulse threshold so the warning appears a little before the situation is critical.
    public static final float HUD_MEDKIT_WARN_HP_THRESHOLD    = 0.40f;
    // Baseline Y (world units) for the centred medkit-reminder text, just above the HUD chrome.
    public static final float HUD_MEDKIT_WARN_Y               = HUD_HEIGHT + 70f;

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

    // Status-effect icon row — small procedural squares along the bottom of the left panel,
    // in the clear band beneath the weapon-slot strip (no overlap with the slots).
    public static final float HUD_STATUS_ICON_SIZE      = 16f;
    public static final float HUD_STATUS_ICON_GAP       = 5f;
    public static final float HUD_STATUS_ROW_LOCAL_X    = 16f;
    public static final float HUD_STATUS_ROW_LOCAL_Y    = 14f;

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

    // =====================================================================
    // Shop / UAC Fabricator overlay (shop_order_5 — full-screen touch card grid).
    // Drawn in world units (origin bottom-left, 1280×720) over a darkened, paused 3D view.
    // The stock is presented as up to six touchable cards; a tap opens an on-card CONFIRM
    // step before any credits are spent. Layout: header band on top, card grid in the
    // middle (reflowed so small shops stay centred), flavor + CLOSE button in the footer.
    // =====================================================================
    public static final float SHOP_OVERLAY_DIM_ALPHA         = 0.72f;   // darken quad over the paused 3D view
    public static final float SHOP_OVERLAY_OPEN_FADE_SECONDS = 0.18f;   // cosmetic fade-in on open (world stays paused)

    // Header band (top of screen): title (left) + live credits readout (right).
    public static final float SHOP_HEADER_HEIGHT             = 76f;
    public static final float SHOP_HEADER_TITLE_SCALE        = 1.35f;
    public static final float SHOP_HEADER_CREDITS_SCALE      = 1.25f;
    public static final float SHOP_HEADER_SIDE_INSET_X       = 44f;     // title left / credits right inset from screen edge
    public static final float SHOP_HEADER_TEXT_Y_BELOW_TOP   = 40f;     // baseline below screen top

    // Footer band (bottom of screen): rotating flavor line (left) + CLOSE button (right).
    public static final float SHOP_FOOTER_HEIGHT             = 96f;
    public static final float SHOP_FOOTER_FLAVOR_SCALE       = 0.85f;
    public static final float SHOP_FOOTER_FLAVOR_INSET_X     = 44f;
    public static final float SHOP_FOOTER_FLAVOR_Y_ABOVE_BOTTOM = 44f;

    // Card grid — up to nine cards, reflowed to at most three per row (a full 3×3 shelf).
    // Card size is tuned so nine cards fit between the header and footer while the on-card text is
    // rendered large enough to read comfortably on a phone (see the *_SCALE values below).
    public static final int   SHOP_CARD_MAX_COLUMNS          = 3;
    public static final float SHOP_CARD_WIDTH                = 386f;
    public static final float SHOP_CARD_HEIGHT               = 164f;
    public static final float SHOP_CARD_GAP                  = 16f;
    public static final float SHOP_CARD_BORDER_WIDTH         = 3f;
    public static final float SHOP_CARD_PAD                  = 18f;     // inner padding for card content
    public static final float SHOP_CARD_ICON_SIZE           = 32f;     // category glyph box (top-left)
    public static final float SHOP_CARD_NAME_SCALE          = 0.92f;   // larger, more readable item name
    public static final float SHOP_CARD_DESC_SCALE          = 0.80f;   // larger, more readable description
    public static final float SHOP_CARD_PRICE_SCALE         = 1.05f;
    public static final float SHOP_CARD_TAG_SCALE           = 0.92f;   // BUY affordance / SOLD stamp
    public static final float SHOP_CARD_NAME_Y_BELOW_TOP    = 28f;     // name baseline below card top
    public static final float SHOP_CARD_DESC_Y_BELOW_TOP    = 70f;     // description baseline below card top
    public static final float SHOP_CARD_PRICE_Y_ABOVE_BOTTOM = 20f;    // price baseline above card bottom

    // On-card CONFIRM step (bottom of the tapped card): "BUY N cr?" + CONFIRM / CANCEL.
    public static final float SHOP_CONFIRM_PROMPT_SCALE      = 0.84f;
    public static final float SHOP_CONFIRM_LABEL_SCALE       = 0.86f;
    public static final float SHOP_CONFIRM_BUTTON_HEIGHT     = 52f;
    public static final float SHOP_CONFIRM_BUTTON_GAP        = 12f;
    public static final float SHOP_CONFIRM_PROMPT_Y_BELOW_TOP = 40f;

    // CLOSE button — large thumb target, bottom-right of the footer.
    public static final float SHOP_OVERLAY_CLOSE_WIDTH       = 240f;
    public static final float SHOP_OVERLAY_CLOSE_HEIGHT      = 70f;
    public static final float SHOP_OVERLAY_CLOSE_MARGIN      = 26f;     // inset from screen bottom-right corner
    public static final float SHOP_OVERLAY_CLOSE_LABEL_SCALE = 1.05f;

    // Purchase / denial feedback timers (cosmetic, real-time while paused).
    public static final float SHOP_PURCHASE_FLASH_SECONDS    = 0.55f;
    public static final float SHOP_DENY_BLIP_SECONDS         = 0.32f;
    public static final float SHOP_BUY_PULSE_SPEED           = 6.0f;    // radians/sec for the BUY affordance pulse

    // Receipt toast — one centred line shown in the footer band after a buy tap ("ACQUIRED: …" / a
    // rejection reason), so the player has a clear textual confirmation the goods were delivered.
    // Placed in the footer (clear of the card grid and the right-hand CLOSE button).
    public static final float SHOP_RECEIPT_SECONDS           = 2.4f;    // how long the receipt stays up
    public static final float SHOP_RECEIPT_SCALE             = 1.0f;    // large, readable confirmation text
    public static final float SHOP_RECEIPT_Y_ABOVE_BOTTOM    = 72f;     // baseline above screen bottom (in footer)
}
