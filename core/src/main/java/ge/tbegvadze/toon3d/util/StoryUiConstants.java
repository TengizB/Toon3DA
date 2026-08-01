package ge.tbegvadze.toon3d.util;

/**
 * Story UI order-1 — the visual-language foundation every other story-UI order builds on.
 * Defines HOW story text looks and reads so the four voices are instantly recognisable and
 * never hard to read on a phone.  Follows the "never hardcode a value" rule: all story-UI
 * geometry, timing, colour and typography live here.
 *
 * HEADLESS: no LibGDX imports, so narrative logic/tests can read geometry without a render
 * context.  Colours are stored as float R/G/B triples (the render layer builds Color from
 * them).  The 3D view is 1280x720, origin bottom-left, Y-up (Constants.WORLD_*).
 *
 * SAFE ZONES (mandatory): story UI must never overlap the bottom HUD (Constants /
 * HudConstants.HUD_HEIGHT) or the lower-left / lower-right thumb touch clusters
 * (TouchConstants).  The bark / exchange anchors below sit in the clear upper-centre band;
 * {@link #STORY_TOUCH_CLUSTER_TOP_Y} records where the touch clusters end so tests and
 * layout code can assert clearance.
 *
 * PER-SPEAKER arrays are indexed by {@code Speaker.ordinal()} in the enum's declaration
 * order: 0 = AI, 1 = PLANET, 2 = ORGANIZATION, 3 = SYSTEM.  A test guards this against the
 * enum drifting.
 */
public final class StoryUiConstants {

    private StoryUiConstants() {}

    // Index constants mirroring Speaker.ordinal() — for readable array initialisers below.
    public static final int SPEAKER_INDEX_AI           = 0;
    public static final int SPEAKER_INDEX_PLANET       = 1;
    public static final int SPEAKER_INDEX_ORGANIZATION = 2;
    public static final int SPEAKER_INDEX_SYSTEM       = 3;
    public static final int SPEAKER_COUNT              = 4;

    // The externalised string-table asset (localisation rule).  Loaded by render/StoryStringsLoader,
    // which falls back to narrative/StoryStrings.defaults() when the asset is missing.
    public static final String STORY_STRINGS_ASSET_PATH = "story/story-strings.properties";

    // =====================================================================
    // TYPOGRAPHY & READABILITY (hard requirements)
    // =====================================================================
    // Body text scale of the default 15px BitmapFont.  Err BIG — must read at arm's length
    // on a phone.  ~24px effective.  Never shrink text to fit; wrap instead.
    public static final float STORY_TEXT_SIZE          = 1.6f;
    // Speaker-name text scale inside the chip (a touch smaller than the body).
    public static final float STORY_NAME_TEXT_SIZE     = 1.0f;
    // Max characters per wrapped line (narrative/StoryText.wrapToMaxChars).  ~40, wrap only.
    public static final int   STORY_LINE_MAX_CHARS     = 40;
    // Delivery caps: a bark is at most 2 lines; an exchange prompt at most 3 short lines.
    public static final int   STORY_BARK_MAX_LINES     = 2;
    public static final int   STORY_EXCHANGE_MAX_LINES = 3;
    // Body text colour — LIGHT on a dark panel, identical for every speaker (colour lives in
    // the chip/accent only; coloured body text hurts readability).
    public static final float STORY_BODY_R = 0.93f, STORY_BODY_G = 0.94f, STORY_BODY_B = 0.96f;
    // Drop-shadow behind body/name glyphs for extra contrast over any backdrop.
    public static final float STORY_TEXT_SHADOW_OFFSET = 1.5f;
    public static final float STORY_TEXT_SHADOW_ALPHA  = 0.75f;

    // =====================================================================
    // PANEL STYLING (reused by every story panel — bark, exchange, boot card, codex)
    // =====================================================================
    // Rounded dark panel behind ALL story text so it reads over any backdrop.  Mirrors the
    // HUD panel styling for consistency (HudConstants.HUD_PANEL_ALPHA).
    public static final float STORY_PANEL_ALPHA        = 0.82f;
    public static final float STORY_PANEL_BG_R = 0.05f, STORY_PANEL_BG_G = 0.06f, STORY_PANEL_BG_B = 0.08f;
    public static final float STORY_PANEL_CORNER_RADIUS = 14f;
    // Inner padding from panel edge to content (text / chip).
    public static final float STORY_PANEL_PADDING       = 18f;
    // Thin accent-tinted border drawn around the panel (uses the speaker accent colour).
    public static final float STORY_PANEL_BORDER_THICKNESS = 2f;
    public static final float STORY_PANEL_BORDER_ALPHA     = 0.55f;

    // =====================================================================
    // SPEAKER CHIP GEOMETRY (colored accent bar + tiny icon + name)
    // =====================================================================
    public static final float STORY_CHIP_HEIGHT           = 34f;
    // The vertical colored accent bar at the panel's left edge (the primary colour cue).
    public static final float STORY_CHIP_ACCENT_BAR_WIDTH = 7f;
    public static final float STORY_CHIP_ICON_SIZE        = 24f;
    // Gap between accent bar → icon → name.
    public static final float STORY_CHIP_ELEMENT_GAP      = 9f;
    // Vertical gap between the chip row and the body text below it.
    public static final float STORY_CHIP_TO_BODY_GAP      = 8f;
    // Line pitch between wrapped body lines (world units at STORY_TEXT_SIZE).
    public static final float STORY_BODY_LINE_PITCH       = 30f;

    // =====================================================================
    // PER-SPEAKER ACCENT COLOURS  (index by Speaker.ordinal(); see class header)
    // Colour appears ONLY in the chip accent bar, icon and name — never in body text.
    // Chosen to read on both dark and bright wall backdrops (always over a dark panel).
    // =====================================================================
    public static final float[] SPEAKER_ACCENT_R = new float[SPEAKER_COUNT];
    public static final float[] SPEAKER_ACCENT_G = new float[SPEAKER_COUNT];
    public static final float[] SPEAKER_ACCENT_B = new float[SPEAKER_COUNT];
    static {
        // AI assistant — warm amber-green: friendly, the voice you live in.
        SPEAKER_ACCENT_R[SPEAKER_INDEX_AI] = 0.98f; SPEAKER_ACCENT_G[SPEAKER_INDEX_AI] = 0.78f; SPEAKER_ACCENT_B[SPEAKER_INDEX_AI] = 0.30f;
        // The Planet — deep organic red: grief and accusation.
        SPEAKER_ACCENT_R[SPEAKER_INDEX_PLANET] = 0.80f; SPEAKER_ACCENT_G[SPEAKER_INDEX_PLANET] = 0.17f; SPEAKER_ACCENT_B[SPEAKER_INDEX_PLANET] = 0.15f;
        // The Organization — cold steel blue: corporate, faceless.
        SPEAKER_ACCENT_R[SPEAKER_INDEX_ORGANIZATION] = 0.42f; SPEAKER_ACCENT_G[SPEAKER_INDEX_ORGANIZATION] = 0.62f; SPEAKER_ACCENT_B[SPEAKER_INDEX_ORGANIZATION] = 0.88f;
        // The System — neutral grey/white: machine status text.
        SPEAKER_ACCENT_R[SPEAKER_INDEX_SYSTEM] = 0.82f; SPEAKER_ACCENT_G[SPEAKER_INDEX_SYSTEM] = 0.85f; SPEAKER_ACCENT_B[SPEAKER_INDEX_SYSTEM] = 0.88f;
    }

    // =====================================================================
    // MOTION / TIMING (gentle, never jarring — no hard pop, no flashing)
    // =====================================================================
    public static final float STORY_FADE_IN_SECONDS   = 0.15f;
    public static final float STORY_FADE_OUT_SECONDS   = 0.20f;
    // The Planet's subtle "unstable" jitter — tiny amplitude, low frequency, stays readable.
    public static final float STORY_PLANET_JITTER_PIXELS = 1.2f;
    public static final float STORY_PLANET_JITTER_HZ     = 1.6f;

    // =====================================================================
    // ACCESSIBILITY BASELINE
    // Text hold time (how long a delivered line stays before fading).  SLOW is the default.
    // Nothing critical is conveyed by colour alone — the name chip + icon always accompany.
    // =====================================================================
    public static final float STORY_HOLD_SECONDS_SLOW    = 4.0f;
    public static final float STORY_HOLD_SECONDS_NORMAL  = 3.0f;
    public static final float STORY_HOLD_SECONDS_FAST     = 2.0f;
    public static final float STORY_HOLD_SECONDS_DEFAULT  = STORY_HOLD_SECONDS_SLOW;
    // Nice-to-have "dyslexia-friendly spacing" toggle — extra glyph spacing when enabled.
    public static final float STORY_DYSLEXIA_EXTRA_SPACING = 2.0f;

    // =====================================================================
    // SAFE ZONES — where story UI may NOT go (bottom HUD + thumb touch clusters).
    // Derived from HudConstants / TouchConstants so a layout drifting into them is caught.
    // Touch clusters are centred at (TOUCH_GRID_LEFT_CENTER_X / TOUCH_GRID_CENTER_X,
    // TOUCH_GRID_BASE_Y) and extend one arm-offset + half a button in each direction.
    // =====================================================================
    public static final float STORY_TOUCH_CLUSTER_HALF_EXTENT =
            TouchConstants.TOUCH_GRID_ARM_OFFSET + TouchConstants.TOUCH_BUTTON_SIZE / 2f;
    public static final float STORY_TOUCH_CLUSTER_TOP_Y =
            TouchConstants.TOUCH_GRID_BASE_Y + STORY_TOUCH_CLUSTER_HALF_EXTENT;
    public static final float STORY_LEFT_CLUSTER_RIGHT_X =
            TouchConstants.TOUCH_GRID_LEFT_CENTER_X + STORY_TOUCH_CLUSTER_HALF_EXTENT;
    public static final float STORY_RIGHT_CLUSTER_LEFT_X =
            TouchConstants.TOUCH_GRID_CENTER_X - STORY_TOUCH_CLUSTER_HALF_EXTENT;
    // The clear upper-centre band's lower edge: above both the HUD and the touch clusters.
    public static final float STORY_SAFE_BAND_BOTTOM_Y =
            Math.max(HudConstants.HUD_HEIGHT, STORY_TOUCH_CLUSTER_TOP_Y) + 20f;

    // =====================================================================
    // PANEL RECTS / ANCHORS  (x = left, y = bottom, in world units)
    // =====================================================================
    // Bark toast (order-2): a single short reaction, top-centre, clear of everything.
    // Height fits the chip row + two body lines at STORY_TEXT_SIZE with the padding intact:
    //   padding 18 + chip 34 + chip-to-body 8 + 2 * line pitch 30 + descender room + padding 18.
    // Order-2 raised it from 118 after the DoD screen showed a two-line bark's descenders touching
    // the bottom border.  The TOP edge is the anchor, so the panel grows downward and stays clear
    // of the HUD, the thumb clusters, the weapon sprite and the mini-map.
    public static final float STORY_BARK_WIDTH   = 600f;
    public static final float STORY_BARK_HEIGHT  = 130f;
    public static final float STORY_BARK_X       = (Constants.WORLD_WIDTH - STORY_BARK_WIDTH) / 2f;
    public static final float STORY_BARK_TOP_Y   = 692f;                              // top edge
    public static final float STORY_BARK_Y       = STORY_BARK_TOP_Y - STORY_BARK_HEIGHT; // bottom edge

    // Exchange panel (order-4): a prompt + a couple of tappable choices, upper-centre.
    public static final float STORY_EXCHANGE_WIDTH  = 640f;
    public static final float STORY_EXCHANGE_HEIGHT = 300f;
    public static final float STORY_EXCHANGE_X      = (Constants.WORLD_WIDTH - STORY_EXCHANGE_WIDTH) / 2f;
    public static final float STORY_EXCHANGE_Y      = 372f;   // bottom edge (spans 372..672, clear of clusters)

    // Choice button (order-4): min height must be a comfortable thumb target.
    public static final float STORY_CHOICE_BUTTON_MIN_HEIGHT = 72f;
    public static final float STORY_CHOICE_BUTTON_WIDTH      = STORY_EXCHANGE_WIDTH - STORY_PANEL_PADDING * 2f;
    public static final float STORY_CHOICE_BUTTON_GAP        = 14f;
    public static final float STORY_CHOICE_BUTTON_CORNER     = 12f;

    // Boot / respawn card (order-5, System voice): a centred modal shown on every run start.
    public static final float STORY_BOOT_CARD_WIDTH  = 720f;
    public static final float STORY_BOOT_CARD_HEIGHT = 300f;
    public static final float STORY_BOOT_CARD_X      = (Constants.WORLD_WIDTH - STORY_BOOT_CARD_WIDTH) / 2f;
    public static final float STORY_BOOT_CARD_Y      = (Constants.WORLD_HEIGHT - STORY_BOOT_CARD_HEIGHT) / 2f;

    // Codex (order-6): the one place long text is allowed — a near-full-screen panel.
    public static final float STORY_CODEX_MARGIN = 60f;
    public static final float STORY_CODEX_X      = STORY_CODEX_MARGIN;
    public static final float STORY_CODEX_Y      = STORY_CODEX_MARGIN;
    public static final float STORY_CODEX_WIDTH  = Constants.WORLD_WIDTH  - STORY_CODEX_MARGIN * 2f;
    public static final float STORY_CODEX_HEIGHT = Constants.WORLD_HEIGHT - STORY_CODEX_MARGIN * 2f;

    // =====================================================================
    // BARK LAYER (order-2) — pacing of the always-on, non-blocking one-liner channel.
    // A bark is: fade in -> hold -> fade out, one on screen at a time, everything else queued.
    // These numbers are the anti-overwhelm budget (order-6 Part B): if in doubt, raise them.
    // =====================================================================
    // How long a delivered bark holds before it fades on its own (slow, accessible default).
    public static final float STORY_BARK_HOLD_SECONDS         = STORY_HOLD_SECONDS_DEFAULT;
    // HARD rate limit: at most one bark per this many seconds of gameplay, whatever asks.
    public static final float STORY_BARK_MIN_INTERVAL_SECONDS = 7f;
    // Pending barks waiting for the screen.  Small on purpose — a long queue IS chatter.
    public static final int   STORY_BARK_QUEUE_CAPACITY       = 4;
    // A queued non-critical bark this old is stale: its moment has passed, so it is dropped.
    public static final float STORY_BARK_QUEUE_STALE_SECONDS  = 12f;
    // Chance an individual kill even asks for a line (kills are frequent; barks must not be).
    public static final float STORY_BARK_KILL_CHANCE          = 0.22f;
    // Health fraction whose downward crossing fires the low-health bark.
    public static final float STORY_BARK_LOW_HEALTH_FRACTION  = 0.30f;
    // No player action for this long => an idle flavour quip may fire.
    public static final float STORY_BARK_IDLE_SECONDS         = 25f;
    // Re-stepping this many already-cleared tiles in a row reads as backtracking.
    public static final int   STORY_BARK_BACKTRACK_STEPS      = 6;
    // Every Nth floor is a deep-strata milestone (the planet's growing whispers).
    public static final int   STORY_BARK_DEEP_STRATA_INTERVAL = 3;

    // Seed salts, mixed with the run seed so the bark layer's rolls join the reproducible run
    // (a shared seed replays the same lines) without colliding with any other seeded stream.
    public static final long STORY_BARK_SELECTION_SEED_SALT = 0xB44B5EED5A17L;   // which line in a pool
    public static final long STORY_MOMENT_SEED_SALT         = 0x570125A17BA4L;   // does a kill even ask

    // PER-TRIGGER COOLDOWNS (seconds) — indexed by narrative/BarkTrigger.ordinal(), in that enum's
    // declaration order: 0 FLOOR_ARRIVAL, 1 REGION_ENTERED, 2 REGION_GATE_ORDER,
    // 3 ENEMY_FAMILY_FIRST_SEEN, 4 KILL, 5 LOW_HEALTH, 6 DEEP_STRATA, 7 IDLE, 8 BACKTRACK.
    // A test guards this array against the enum drifting.  Zero = the moment is naturally rare
    // (a floor arrival, a one-shot beat) and needs no cooldown beyond the global rate limit.
    public static final float[] STORY_BARK_TRIGGER_COOLDOWN_SECONDS = {
            0f,     // FLOOR_ARRIVAL           — once per floor by construction
            0f,     // REGION_ENTERED           — one-shot mandatory beat
            0f,     // REGION_GATE_ORDER        — one-shot mandatory beat
            0f,     // ENEMY_FAMILY_FIRST_SEEN  — one-shot per family
            45f,    // KILL                     — reactive flavour; stays fresh only if rare
            40f,    // LOW_HEALTH
            20f,    // DEEP_STRATA
            60f,    // IDLE
            75f,    // BACKTRACK
    };

    // =====================================================================
    // SPEAKER STINGS (audio) — one short non-verbal sting per speaker, pre-attentively
    // signalling WHO is talking.  Fully understandable with audio OFF.  Arrays index by
    // Speaker.ordinal().  Generated procedurally on the render side (render/StorySpeakerStings).
    // =====================================================================
    public static final int   STORY_STING_SAMPLE_RATE_HZ = 44100;
    public static final float[] STORY_STING_FREQUENCY_HZ = { 660f, 110f, 440f, 900f };
    public static final float[] STORY_STING_DURATION_SEC = { 0.12f, 0.22f, 0.10f, 0.06f };
    public static final float[] STORY_STING_VOLUME       = { 0.50f, 0.55f, 0.60f, 0.40f };
    // The Organization gets a hard square-wave blip; the others are soft sine tones.
    public static final boolean[] STORY_STING_IS_SQUARE  = { false, false, true, false };
    // Short attack/release envelope (fraction of duration) to avoid clicks — soft edges.
    public static final float STORY_STING_ENVELOPE_FRACTION = 0.15f;
}
