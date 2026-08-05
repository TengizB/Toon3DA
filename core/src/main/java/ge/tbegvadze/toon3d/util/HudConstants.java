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

    // The full-screen death report used to live here.  Story UI order-8 folded it into the
    // death -> reprint framing: the run's numbers are now a PAGE of the reprint card's screen
    // (render/InstanceReportRenderer), so its geometry lives with the other framing screens in
    // util/StoryUiConstants (STORY_REPORT_*) rather than in the HUD's constants.

    // Status-effect icon row — small procedural squares along the bottom of the left panel,
    // in the clear band beneath the weapon-slot strip (no overlap with the slots).
    public static final float HUD_STATUS_ICON_SIZE      = 16f;
    public static final float HUD_STATUS_ICON_GAP       = 5f;
    public static final float HUD_STATUS_ROW_LOCAL_X    = 16f;
    public static final float HUD_STATUS_ROW_LOCAL_Y    = 14f;

    // Always-on credits readout — top-right corner of the screen, clear of the mini-map
    // (top-left) and the bottom HUD chrome. World units, screen 1280x720, Y-up.
    public static final float HUD_CREDITS_RIGHT_INSET_X = 24f;    // gap from the right screen edge
    public static final float HUD_CREDITS_TOP_INSET_Y   = 28f;    // gap from the top screen edge to the text top
    public static final float HUD_CREDITS_SCALE         = 1.40f;

    // Debuff text list — stacked lines beneath the credits readout, top-right corner.
    // Each active status effect draws as "NAME (turnsRemaining)  <detail>", counting down every turn,
    // where <detail> is the effect's concrete per-turn/per-hit potency (e.g. "-6 HP/TURN", "-25% DMG DEALT").
    public static final float HUD_DEBUFF_LIST_TOP_GAP   = 48f;    // gap below the credits text top for the first line
    public static final float HUD_DEBUFF_LIST_LINE_STEP = 34f;    // vertical spacing between stacked debuff lines
    public static final float HUD_DEBUFF_LIST_SCALE     = 1.10f;

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

    // Weapon card — single-screen pickup / compare modal (redesigned: large card, high-res text)
    public static final float WEAPON_CARD_WIDTH          = 900f;
    public static final float WEAPON_CARD_HEIGHT         = 620f;
    public static final float WEAPON_CARD_ORIGIN_X       = 190f;   // (1280 - 900) / 2
    public static final float WEAPON_CARD_ORIGIN_Y       = 50f;    // (720 - 620) / 2
    public static final float WEAPON_CARD_PANEL_ALPHA    = 0.96f;
    // Base body font scale; individual text elements scale relative to this (see renderer).
    public static final float WEAPON_CARD_FONT_SCALE     = 1.5f;
    // Zone heights inside the card (from top): header, abilities, action, footer.
    // The stats table fills whatever remains between the header and the ability strip.
    public static final float WEAPON_CARD_HEADER_HEIGHT  = 78f;
    public static final float WEAPON_CARD_STAT_ROW_H     = 34f;    // nominal — renderer computes exact bands
    public static final float WEAPON_CARD_ABILITY_H      = 66f;    // ability strip below stats
    public static final float WEAPON_CARD_ACTION_H       = 176f;   // equip button OR slot rows
    public static final float WEAPON_CARD_FOOTER_H       = 92f;    // close + convert strip
    // Large equip button (free-slot fast lane)
    public static final float WEAPON_EQUIP_BUTTON_WIDTH  = 640f;
    public static final float WEAPON_EQUIP_BUTTON_HEIGHT = 104f;
    // Inline slot rows (full loadout)
    public static final float WEAPON_SLOT_ROW_HEIGHT     = 74f;
    public static final float WEAPON_SLOT_ROW_GAP        = 14f;
    // Swap button on the right edge of each slot row
    public static final float WEAPON_SWAP_BUTTON_WIDTH   = 176f;
    public static final float WEAPON_SWAP_BUTTON_HEIGHT  = 56f;
    // Footer buttons
    public static final float WEAPON_CLOSE_BUTTON_WIDTH  = 190f;
    public static final float WEAPON_CLOSE_BUTTON_HEIGHT = 64f;
    public static final float WEAPON_CONVERT_BUTTON_WIDTH  = 300f;
    public static final float WEAPON_CONVERT_BUTTON_HEIGHT = 64f;
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

    // State label (ORDER 8) — a small tag under the boss name on the HP bar reading the current beat
    // (STALKING / CHARGING / SUMMONING / CASTING / REPAIRING / ENRAGED). Unobtrusive; sits just below
    // the name baseline. Colour is a dim white, or the boss accent when hot (enraged).
    public static final float BOSS_STATE_LABEL_FONT_SCALE   = 0.62f;
    public static final float BOSS_STATE_LABEL_Y_GAP        = 6f;    // gap below the name baseline
    public static final float BOSS_STATE_LABEL_R            = 0.78f;
    public static final float BOSS_STATE_LABEL_G            = 0.82f;
    public static final float BOSS_STATE_LABEL_B            = 0.88f;

    // Heal tick-up flash (ORDER 8 + ORDER 5) — when the boss HP bar RISES on a repair turn, flash the
    // fill green so the player instantly reads "it's healing — go stop it". Driven by a decaying timer.
    public static final float BOSS_HEAL_FLASH_DURATION_SECONDS = 0.45f;
    public static final float BOSS_HEAL_FLASH_R = 0.25f, BOSS_HEAL_FLASH_G = 1.00f, BOSS_HEAL_FLASH_B = 0.35f;

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
