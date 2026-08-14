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

    // CLOSE AFFORDANCE — a bark NEVER auto-dismisses (a player must never lose a line to a timer
    // they could not read in time).  It stays until the player closes it: tap the X, or swipe the
    // panel in any direction.  The X sits in the panel's top-right corner; its TOUCH rect is grown
    // past the drawn glyph so it stays a comfortable thumb target without a big visual box.
    public static final float STORY_BARK_CLOSE_GLYPH_SIZE     = 22f;
    public static final float STORY_BARK_CLOSE_STROKE         = 3f;
    public static final float STORY_BARK_CLOSE_MARGIN         = 14f;
    public static final float STORY_BARK_CLOSE_TOUCH_PADDING  = 18f;
    /** Drag this far (world units) starting inside the panel to swipe a bark away. */
    public static final float STORY_BARK_SWIPE_DISMISS_DISTANCE = 60f;
    /** Centre of the close glyph, derived from the panel rect (top-right corner, inside padding). */
    public static final float STORY_BARK_CLOSE_CENTER_X =
            STORY_BARK_X + STORY_BARK_WIDTH - STORY_BARK_CLOSE_MARGIN - STORY_BARK_CLOSE_GLYPH_SIZE / 2f;
    public static final float STORY_BARK_CLOSE_CENTER_Y =
            STORY_BARK_TOP_Y - STORY_BARK_CLOSE_MARGIN - STORY_BARK_CLOSE_GLYPH_SIZE / 2f;

    // Exchange panel (order-4): a prompt panel with 2-3 big tappable answers stacked under it.
    // Unlike the bark, an exchange is a BLOCKING modal: the world is dimmed and frozen behind it and
    // the thumb clusters are not drawn at all, so — exactly like the order-3 boot card's CONTINUE —
    // the stack is free to use the lower screen the touch buttons normally own.  It stays clear of
    // the HUD band so health and ammo remain readable while the player decides.
    public static final float STORY_EXCHANGE_WIDTH  = 640f;
    public static final float STORY_EXCHANGE_X      = (Constants.WORLD_WIDTH - STORY_EXCHANGE_WIDTH) / 2f;
    // The prompt panel is the TOP of the stack and the anchor everything else hangs off, so the whole
    // exchange can be re-spaced by moving this one number.  It is set as low as the stack allows:
    // prompt(160) + gap(24) + 3 plates(228) + 2 gaps(28) = 440 tall, which lands the last plate a
    // comfortable margin above the HUD band while keeping the panel clear of the screen's top edge.
    public static final float STORY_EXCHANGE_PROMPT_TOP_Y = 664f;
    // Panel height for N body lines, by the same rule the bark and boot card are sized with:
    //   30 * lines + 70.  N = STORY_EXCHANGE_MAX_LINES (3).
    public static final float STORY_EXCHANGE_PROMPT_HEIGHT = 160f;
    public static final float STORY_EXCHANGE_PROMPT_Y      =
            STORY_EXCHANGE_PROMPT_TOP_Y - STORY_EXCHANGE_PROMPT_HEIGHT;

    // Choice button (order-4): min height must be a comfortable thumb target.
    public static final float STORY_CHOICE_BUTTON_MIN_HEIGHT = 72f;
    public static final float STORY_CHOICE_BUTTON_WIDTH      = STORY_EXCHANGE_WIDTH - STORY_PANEL_PADDING * 2f;
    public static final float STORY_CHOICE_BUTTON_GAP        = 14f;
    public static final float STORY_CHOICE_BUTTON_CORNER     = 12f;

    // How many answers an exchange may carry.  Two is the floor because an exchange with one answer
    // is a wall of text with a dismiss button — the exact thing order-4 exists to avoid.  Three is
    // the ceiling the button stack is sized for, and more than three is a menu, not a conversation.
    public static final int   STORY_EXCHANGE_MIN_OPTIONS = 2;
    public static final int   STORY_EXCHANGE_MAX_OPTIONS = 3;

    // The button stack: TOP-anchored under the prompt panel, so a two-answer exchange and a
    // three-answer one put their first button in the same place and the thumb learns one spot.
    public static final float STORY_EXCHANGE_BUTTON_STACK_GAP = 24f;
    public static final float STORY_EXCHANGE_BUTTON_HEIGHT    = 76f;
    public static final float STORY_EXCHANGE_BUTTONS_TOP_Y    =
            STORY_EXCHANGE_PROMPT_Y - STORY_EXCHANGE_BUTTON_STACK_GAP;
    public static final float STORY_EXCHANGE_BUTTON_X         = STORY_EXCHANGE_X + STORY_PANEL_PADDING;
    /** Bottom edge of the LAST button when all three slots are used — the stack's floor. */
    public static final float STORY_EXCHANGE_BUTTONS_BOTTOM_Y =
            STORY_EXCHANGE_BUTTONS_TOP_Y
                    - STORY_EXCHANGE_MAX_OPTIONS * STORY_EXCHANGE_BUTTON_HEIGHT
                    - (STORY_EXCHANGE_MAX_OPTIONS - 1) * STORY_CHOICE_BUTTON_GAP;
    // Overall footprint of the exchange (the rect the whole modal occupies), derived from the stack.
    public static final float STORY_EXCHANGE_Y      = STORY_EXCHANGE_BUTTONS_BOTTOM_Y;
    public static final float STORY_EXCHANGE_HEIGHT = STORY_EXCHANGE_PROMPT_TOP_Y - STORY_EXCHANGE_Y;

    // Button plate styling — an accent border with a dim fill, matching the boot card's CONTINUE.
    // The PRESSED fill is much brighter: a tap must be visibly acknowledged before the panel changes,
    // or a player who has just committed to an answer cannot tell whether the game heard them.
    public static final float STORY_EXCHANGE_BUTTON_BORDER        = 2f;
    public static final float STORY_EXCHANGE_BUTTON_FILL_ALPHA    = 0.22f;
    public static final float STORY_EXCHANGE_BUTTON_PRESSED_ALPHA = 0.55f;
    /** Answer text scale — a touch under the body size so one short line always fits the plate. */
    public static final float STORY_EXCHANGE_OPTION_TEXT_SIZE     = 1.4f;
    /** Hard cap on an answer's length.  ONE short line per button; a test enforces it. */
    public static final int   STORY_EXCHANGE_OPTION_MAX_CHARS     = 34;

    // The world DIMS behind the panel — "we've paused for this".  Lighter than the boot card's
    // near-blackout on purpose: the player must still see the room they are standing in.
    public static final float STORY_EXCHANGE_DIM_ALPHA = 0.62f;
    public static final float STORY_EXCHANGE_DIM_R = 0.02f, STORY_EXCHANGE_DIM_G = 0.02f,
                              STORY_EXCHANGE_DIM_B = 0.04f;

    // MOTION — a shade slower than a bark (the world just stopped for this), and NO timers of any
    // kind anywhere in the exchange: it waits for a tap, forever, exactly like the boot card.
    public static final float STORY_EXCHANGE_FADE_IN_SECONDS  = 0.25f;
    public static final float STORY_EXCHANGE_FADE_OUT_SECONDS = 0.25f;
    /** Localisation id of the label on the reply's acknowledge button (never a literal in render). */
    public static final String STORY_EXCHANGE_CONTINUE_LABEL_ID = "story.exchange.continue";
    /** Seed salt for which of several eligible exchanges opens (joins the reproducible run stream). */
    public static final long  STORY_EXCHANGE_SELECTION_SEED_SALT = 0xE8C4A9E5A17L;

    // Boot / respawn card (order-3, System voice): a centred modal shown on every run start.
    // The rect below is the card's overall footprint; the order-3 BOOT CARD section further down
    // splits it into the three stacked elements (system lines, instance counter, ORA wake line).
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
    // NO AUTO-DISMISS: a delivered bark holds until the player closes it (see the CLOSE AFFORDANCE
    // block above).  Only the fade IN and the fade OUT are timed; there is no hold timer at all.
    // HARD rate limit: at most one bark per this many seconds of gameplay, whatever asks.  Measured
    // from the moment the previous bark was DISMISSED, so reading slowly never causes a pile-up.
    public static final float STORY_BARK_MIN_INTERVAL_SECONDS = 12f;
    // Pending barks waiting for the screen.  This is NOT a pacing knob — the rate limit and the
    // per-trigger cooldowns own pacing, and a queued line is only ever delivered one at a time, on
    // the player's own taps.  It is a FLOOR under the mandatory burst: a STORY_CRITICAL beat that
    // finds the queue full of other criticals is dropped outright, and a dropped control hint is a
    // stuck player.  Floor one of run one is the worst case the game ever asks for, and it asks for
    // ten in two frames — the region-entry beat, the Organization's gate order, ORA's aside on it
    // (narrative-rework order-7), the two vocabulary namings behind them (narrative-rework order-3),
    // the facility's own naming, ORA's three introduction lines and the MOVE hint — plus the reactive
    // floor-arrival line.  Twelve is that burst with margin.
    public static final int   STORY_BARK_QUEUE_CAPACITY       = 12;
    // A queued non-critical bark this old is stale: its moment has passed, so it is dropped
    // rather than shown late.  Generous, because the player now controls when the screen frees up.
    public static final float STORY_BARK_QUEUE_STALE_SECONDS  = 25f;
    // ANTI-REPETITION: never re-use a line said within the last N barks.  If every candidate for a
    // moment is still in that memory, the moment says NOTHING — silence beats a repeat.
    public static final int   STORY_BARK_RECENT_MEMORY        = 10;
    // Chance an individual kill even asks for a line (kills are frequent; barks must not be).
    public static final float STORY_BARK_KILL_CHANCE          = 0.07f;
    // Chance arriving on a floor asks for a line — most floors ORA simply says nothing.
    public static final float STORY_BARK_FLOOR_ARRIVAL_CHANCE = 0.40f;
    // Health fraction whose downward crossing fires the low-health bark.
    public static final float STORY_BARK_LOW_HEALTH_FRACTION  = 0.30f;
    // No player action for this long => an idle flavour quip may fire.
    public static final float STORY_BARK_IDLE_SECONDS         = 45f;
    // Re-stepping this many already-cleared tiles in a row reads as backtracking.
    public static final int   STORY_BARK_BACKTRACK_STEPS      = 10;
    // Every Nth floor is a deep-strata milestone (the planet's growing whispers).
    public static final int   STORY_BARK_DEEP_STRATA_INTERVAL = 3;

    // THE PER-FLOOR CEILING (narrative-rework order-8 B) — the strongest anti-annoyance lever in the
    // layer, and the one thing the cooldowns, the probability rolls and the combat-spike hold could
    // not express between them: how much the game may say on ONE floor, whatever asks.
    //
    // An ordinary floor delivers at most one terminal take and two flavour lines. This number is the
    // second half of that sentence: after this many NON-CRITICAL barks have reached the screen,
    // BarkSystem stays quiet until the next floor arrival resets the count (BarkSystem.beginFloor).
    // A held line simply goes stale and is dropped — silence is the correct result, not a backlog.
    //
    // STORY_CRITICAL beats are exempt and always will be: a teaching line, a region entry, a gate
    // order or a reveal log costs less than a stuck or lost player. Raising this number is how "the
    // game keeps talking" comes back; lowering it is always safe.
    public static final int   STORY_BARK_FLOOR_BUDGET         = 2;
    // THE PLANET'S HARD CAP (order-8 E). It is the easiest voice in the game to overuse and the only
    // one whose entire power is scarcity, so it gets a ceiling of its own on top of the budget above:
    // at most this many PLANET lines reach the screen per floor, in any region, on any trigger.
    public static final int   STORY_PLANET_LINES_PER_FLOOR    = 1;
    // DELIBERATE SILENCE (order-8 F). From the Wound down, a floor arrival has this chance of not
    // even asking — ORA saying nothing when the room is bad is the cheapest horror effect in the
    // game, and it is unavailable while something always fires. Rolled on top of the arrival chance
    // above, so deep floors speak on arrival about a quarter of the time.
    public static final float STORY_BARK_DEEP_FLOOR_SILENCE_CHANCE = 0.40f;

    // TONE BALANCE — every ORA pool mixes lines that MOVE THE STORY with lines that are just her
    // being dry.  Lore outweighs levity by these selection weights, so she is funny sometimes, not
    // constantly (narrative/BarkTone).  Tune the mix here; never by editing selection code.
    public static final float STORY_BARK_WEIGHT_LORE   = 1.0f;
    public static final float STORY_BARK_WEIGHT_LEVITY = 0.40f;

    // Seed salts, mixed with the run seed so the bark layer's rolls join the reproducible run
    // (a shared seed replays the same lines) without colliding with any other seeded stream.
    public static final long STORY_BARK_SELECTION_SEED_SALT = 0xB44B5EED5A17L;   // which line in a pool
    public static final long STORY_MOMENT_SEED_SALT         = 0x570125A17BA4L;   // does a kill even ask

    // PER-TRIGGER COOLDOWNS (seconds) — indexed by narrative/BarkTrigger.ordinal(), in that enum's
    // declaration order: 0 FLOOR_ARRIVAL, 1 REGION_ENTERED, 2 REGION_GATE_ORDER,
    // 3 ENEMY_FAMILY_FIRST_SEEN, 4 ENEMY_ABILITY_RESOLVED, 5 BOSS_ARENA_ENTERED, 6 BOSS_DEFEATED,
    // 7 KILL, 8 LOW_HEALTH, 9 DEEP_STRATA, 10 IDLE, 11 BACKTRACK,
    // 12 RUN_START, 13 CONTROL_HINT, 14 LOG_FOUND, 15 CODEX_COMPLETE, 16 MAP_OPENED,
    // 17 DEPTH_REACTION.
    // A test guards this array against the enum drifting.  Zero = the moment is naturally rare
    // (a floor arrival, a one-shot beat) and needs no cooldown beyond the global rate limit.
    public static final float[] STORY_BARK_TRIGGER_COOLDOWN_SECONDS = {
            75f,    // FLOOR_ARRIVAL            — a short floor must not always earn a line
            0f,     // REGION_ENTERED           — one-shot mandatory beat
            0f,     // REGION_GATE_ORDER        — one-shot mandatory beat
            0f,     // ENEMY_FAMILY_FIRST_SEEN  — one-shot per family AND per archetype
            0f,     // ENEMY_ABILITY_RESOLVED   — one-shot per ability; a dropped rule kills a player
            0f,     // BOSS_ARENA_ENTERED       — one-shot, once in a lifetime
            0f,     // BOSS_DEFEATED            — one-shot, the reveal riding the first boss's death
            180f,   // KILL                     — reactive flavour; stays fresh only if RARE
            120f,   // LOW_HEALTH
            90f,    // DEEP_STRATA
            300f,   // IDLE                     — a quip every five minutes at most
            300f,   // BACKTRACK
            0f,     // RUN_START                — one-shot cold open, twice in a lifetime
            0f,     // CONTROL_HINT             — one-shot tutorial; a dropped hint is a stuck player
            120f,   // LOG_FOUND                — a room full of terminals must yield ONE take
            0f,     // CODEX_COMPLETE           — one-shot per category, six times in a lifetime
            0f,     // MAP_OPENED               — one-shot per region, five times in a lifetime
            75f,    // DEPTH_REACTION           — the grind-floor pool; paced exactly like an arrival
    };

    // =====================================================================
    // MOMENT SCHEDULE (order-5) — the gameplay side of "when does a story beat fire".  The catalogs
    // own WHAT is said; these numbers own how often the world is even allowed to ask.
    // =====================================================================
    // The solid prop that counts as a readable facility log.  Walking orthogonally adjacent to one
    // is the LOG_FOUND moment — the same auto-trigger idiom as the heal station and the EVENT
    // console, so reading never costs the player a button they had to be taught.  'T' is the
    // computer terminal (docs/tile-symbols.txt); it is common enough that every region band contains
    // several, and never guaranteed, which is why no MANDATORY spine beat rides this channel alone.
    public static final char  STORY_LOG_TERMINAL_SYMBOL = 'T';
    // At most one log take per floor: a server room holds a dozen terminals and ORA has one opinion.
    public static final int   STORY_LOG_TAKES_PER_FLOOR = 1;
    // A floor is "quiet" for the teaching exchange once the player has walked this many fresh tiles
    // on it with nothing awake.  Long enough that it never fires in the first corridor of a fight,
    // short enough that a new player meets the choice UI early in their first run.
    public static final int   STORY_QUIET_MOMENT_MIN_STEPS = 14;
    // Floors after a region's gate before the Organization's demand exchange may open.  NOT zero:
    // the region-entry exchange fires at the gate, and two exchanges with no gameplay between them
    // is the pacing failure order-6 Part B names by name.
    public static final int   STORY_ORGANIZATION_ORDER_FLOOR_DELAY = 1;

    // =====================================================================
    // BOOT / REPRINT CARD (order-3) — the full-screen card shown every time the player is
    // reprinted after death, and on the very first run too (in-fiction the original self has just
    // died).  It is the game's most reliable story channel, so it doubles as a guaranteed
    // bite-sized beat: three stacked elements over a dark full-bleed background —
    //   1. the SYSTEM status lines (the machine voice, which never editorialises),
    //   2. the quiet instance counter (never celebrated),
    //   3. one region-appropriate ORA wake-up line (the story beat).
    // Layout is a TOP-DOWN stack: each element's anchor is derived from the one above it, so the
    // card can be re-spaced by moving STORY_BOOT_SYSTEM_TOP_Y alone and nothing drifts.
    // =====================================================================
    // Dark full-bleed background behind the card.  Calm and near-opaque: this screen appears on a
    // player who has just died, so it must never flash or strobe.
    public static final float STORY_BOOT_BACKDROP_ALPHA = 0.94f;
    public static final float STORY_BOOT_BACKDROP_R = 0.02f, STORY_BOOT_BACKDROP_G = 0.02f,
                              STORY_BOOT_BACKDROP_B = 0.03f;

    // Body-line caps per block.  The ordinary reprint card is three status lines; the FIRST-PRINT
    // card (narrative-rework order-2) needs a fourth, because a stranger has to be told four things
    // before any of it means anything: a person died, that person is you, there is a copy, and the
    // copy is being made.  ORA gets a bark's two, because the boot line is a bark in every way
    // except that it stops the world.
    public static final int   STORY_BOOT_SYSTEM_MAX_LINES = 4;
    public static final int   STORY_BOOT_WAKE_MAX_LINES   = 2;
    // Panel height for N body lines, from the geometry StoryPanelRenderer actually draws with:
    //   padding(18) + chip(34) + chip-to-body(8) + (N-1) * line pitch(30) + a glyph box(~24)
    //   + bottom room(16)  =  30N + 70.   N = 2 reproduces the bark's 130 exactly.
    public static final float STORY_BOOT_SYSTEM_PANEL_HEIGHT = 190f;   // N = 4
    public static final float STORY_BOOT_WAKE_PANEL_HEIGHT   = 130f;   // N = 2
    // Both blocks share the boot card's horizontal footprint, so the stack reads as one card.
    public static final float STORY_BOOT_PANEL_X     = STORY_BOOT_CARD_X;
    public static final float STORY_BOOT_PANEL_WIDTH = STORY_BOOT_CARD_WIDTH;

    // 1. SYSTEM status lines — the top of the stack; every other anchor hangs off it.
    public static final float STORY_BOOT_SYSTEM_TOP_Y = 596f;
    public static final float STORY_BOOT_SYSTEM_Y     =
            STORY_BOOT_SYSTEM_TOP_Y - STORY_BOOT_SYSTEM_PANEL_HEIGHT;

    // 2. Instance counter — a single quiet line under the system panel.  Deliberately dim and
    // small: the number climbing over a long session is its own horror and is never annotated.
    public static final float STORY_BOOT_COUNTER_GAP       = 34f;
    public static final float STORY_BOOT_COUNTER_TOP_Y     = STORY_BOOT_SYSTEM_Y - STORY_BOOT_COUNTER_GAP;
    public static final float STORY_BOOT_COUNTER_TEXT_SIZE = 1.1f;
    public static final float STORY_BOOT_COUNTER_R = 0.46f, STORY_BOOT_COUNTER_G = 0.48f,
                              STORY_BOOT_COUNTER_B = 0.52f;
    /** Machine label, not story prose — the counter is chrome, so it is not a localised line. */
    public static final String STORY_BOOT_COUNTER_PREFIX = "INSTANCE #";
    /** Zero-padded width of the printed instance number ("INSTANCE #0048"). */
    public static final int    STORY_BOOT_COUNTER_DIGITS = 4;

    // 3. ORA's wake-up line — the story beat, in the AI speaker's amber.
    public static final float STORY_BOOT_WAKE_GAP   = 44f;
    public static final float STORY_BOOT_WAKE_TOP_Y = STORY_BOOT_COUNTER_TOP_Y - STORY_BOOT_WAKE_GAP;
    public static final float STORY_BOOT_WAKE_Y     = STORY_BOOT_WAKE_TOP_Y - STORY_BOOT_WAKE_PANEL_HEIGHT;

    // CONTINUE — one large, thumb-friendly tap target at the bottom.  A tap ALWAYS proceeds: the
    // card is never a trap, and it is never on a timer either, so a slow reader can take the beat
    // in.  The endgame FREE/KILL variant draws no button at all — that absence is the ending.
    public static final float STORY_BOOT_CONTINUE_WIDTH  = 380f;
    public static final float STORY_BOOT_CONTINUE_HEIGHT = 96f;
    public static final float STORY_BOOT_CONTINUE_X      =
            (Constants.WORLD_WIDTH - STORY_BOOT_CONTINUE_WIDTH) / 2f;
    public static final float STORY_BOOT_CONTINUE_Y      = 96f;
    public static final float STORY_BOOT_CONTINUE_CORNER = 12f;
    public static final float STORY_BOOT_CONTINUE_BORDER = 2f;
    public static final float STORY_BOOT_CONTINUE_TEXT_SIZE = 1.5f;
    public static final float STORY_BOOT_CONTINUE_FILL_ALPHA = 0.28f;
    /** Localisation id of the button label (localisation rule: never a literal in the renderer). */
    public static final String STORY_BOOT_CONTINUE_LABEL_ID = "story.boot.continue";

    // MOTION — the card fades in a touch slower than a bark (it is a beat, not a notification) and
    // its status lines print one after another, like a terminal waking up.  Nothing flashes.
    public static final float STORY_BOOT_FADE_IN_SECONDS   = 0.50f;
    public static final float STORY_BOOT_FADE_OUT_SECONDS  = 0.35f;
    /** Seconds between successive SYSTEM status lines printing in. */
    public static final float STORY_BOOT_LINE_REVEAL_SECONDS = 0.45f;
    /**
     * ACCESSIBILITY — the "reduce text hold" setting (order-1's accessibility baseline).  When on,
     * every boot-card reveal delay is multiplied by this, so the whole card is legible sooner for a
     * player who does not want a paced reveal.  A tap still skips straight to the full card either
     * way — the reveal is mood, never a gate.
     */
    public static final float STORY_TEXT_HOLD_SCALE_REDUCED = 0.35f;
    /** Seed salt for which ORA wake line a reprint draws (joins the reproducible run stream). */
    public static final long  STORY_BOOT_WAKE_SEED_SALT = 0xB007CA2D5A17L;

    // =====================================================================
    // SPEAKER STINGS (audio) — one short non-verbal sting per speaker, pre-attentively
    // signalling WHO is talking.  Fully understandable with audio OFF.  Arrays index by
    // Speaker.ordinal().  Generated procedurally on the render side (render/StoryAudio).
    // =====================================================================
    public static final int   STORY_STING_SAMPLE_RATE_HZ = 44100;
    public static final float[] STORY_STING_FREQUENCY_HZ = { 660f, 110f, 440f, 900f };
    public static final float[] STORY_STING_DURATION_SEC = { 0.12f, 0.22f, 0.10f, 0.06f };
    public static final float[] STORY_STING_VOLUME       = { 0.50f, 0.55f, 0.60f, 0.40f };
    // The Organization gets a hard square-wave blip; the others are soft sine tones.
    public static final boolean[] STORY_STING_IS_SQUARE  = { false, false, true, false };
    // Short attack/release envelope (fraction of duration) to avoid clicks — soft edges.
    public static final float STORY_STING_ENVELOPE_FRACTION = 0.15f;

    // =====================================================================
    // INTERFACE CUES (audio, order-7 Part D) — the non-speaker half of the story layer's sound.
    // A sting says WHO is talking; a cue says WHAT the interface just did: a panel took the screen,
    // an answer was committed, another status line printed.  Same procedural WAV pipeline as the
    // stings (render/StoryAudio), same rule: every one of them is redundant polish.  The game is
    // fully understandable with audio off, and the whole layer can be switched off in the codex's
    // accessibility strip.  Arrays index by StoryCue.ordinal().
    // =====================================================================
    /** How many interface cues exist — PANEL_OPEN, CHOICE_PICKED, REVEAL_TICK (see StoryCue). */
    public static final int   STORY_CUE_COUNT        = 3;
    /** A gentle rise for the panel, a firm low thunk for a committed answer, a dry tick for a line. */
    public static final float[] STORY_CUE_FREQUENCY_HZ = { 520f, 196f, 1400f };
    public static final float[] STORY_CUE_DURATION_SEC = { 0.09f, 0.13f, 0.02f };
    /** The reveal tick fires once per printed line, so it is the quietest thing in the game. */
    public static final float[] STORY_CUE_VOLUME       = { 0.35f, 0.45f, 0.14f };
    /** Only the committed-answer cue is a square wave — it should feel like a switch being thrown. */
    public static final boolean[] STORY_CUE_IS_SQUARE  = { false, true, false };

    // =====================================================================
    // CODEX (order-6 Part A) — the ARCHIVE, and the ONE place in the whole game where long text is
    // allowed.  Everything the story channels already said is filed here, plus the full text of the
    // logs the player walked up to.  Nothing in it is forced: the ~20% who love lore can read for
    // hours, the ~80% never open it and still get the whole story off the barks and exchanges.
    //
    // LAYOUT is a fixed three-band frame inside the near-full-screen panel above:
    //   header  (title / BACK, close X)            — the top band
    //   tabs    (one per CodexCategory, left)      + body (entry list -> entry detail, right)
    //   footer  (the accessibility strip, under the body)
    // Every anchor below is derived from the panel rect, so the whole screen re-spaces by moving
    // STORY_CODEX_MARGIN alone.
    // =====================================================================
    public static final float STORY_CODEX_TOP_Y   = STORY_CODEX_Y + STORY_CODEX_HEIGHT;
    public static final float STORY_CODEX_RIGHT_X = STORY_CODEX_X + STORY_CODEX_WIDTH;
    public static final float STORY_CODEX_PADDING = 18f;
    // Backdrop behind the panel: dark, but NOT the boot card's blackout — the codex is a menu the
    // player chose to open, not a beat that happened to them.
    public static final float STORY_CODEX_SCRIM_ALPHA = 0.86f;
    public static final float STORY_CODEX_SCRIM_R = 0.02f, STORY_CODEX_SCRIM_G = 0.02f,
                              STORY_CODEX_SCRIM_B = 0.03f;

    // 1. HEADER — the archive title (or BACK, in an entry) and the close X.
    public static final float STORY_CODEX_HEADER_HEIGHT   = 62f;
    public static final float STORY_CODEX_HEADER_Y        = STORY_CODEX_TOP_Y - STORY_CODEX_HEADER_HEIGHT;
    public static final float STORY_CODEX_TITLE_TEXT_SIZE = 1.5f;
    /** Everything under the header — the tab column and the body share this top edge. */
    public static final float STORY_CODEX_CONTENT_TOP_Y   = STORY_CODEX_HEADER_Y - STORY_CODEX_PADDING;
    // The close X reuses the bark's glyph geometry so every dismissable panel closes the same way.
    public static final float STORY_CODEX_CLOSE_CENTER_X =
            STORY_CODEX_RIGHT_X - STORY_BARK_CLOSE_MARGIN - STORY_BARK_CLOSE_GLYPH_SIZE / 2f;
    public static final float STORY_CODEX_CLOSE_CENTER_Y =
            STORY_CODEX_TOP_Y - STORY_BARK_CLOSE_MARGIN - STORY_BARK_CLOSE_GLYPH_SIZE / 2f;
    /** BACK sits where the title does, so returning from an entry is always the same thumb spot. */
    public static final float STORY_CODEX_BACK_X      = STORY_CODEX_X + STORY_CODEX_PADDING;
    public static final float STORY_CODEX_BACK_WIDTH  = 190f;
    public static final float STORY_CODEX_BACK_HEIGHT = 46f;
    public static final float STORY_CODEX_BACK_Y      =
            STORY_CODEX_HEADER_Y + (STORY_CODEX_HEADER_HEIGHT - STORY_CODEX_BACK_HEIGHT) / 2f;

    // 2. CATEGORY TABS — one per CodexCategory, stacked down the left.  Big plates, because this is
    // a phone and a tab that needs aiming is a tab nobody presses.
    public static final float STORY_CODEX_TAB_WIDTH     = 300f;
    public static final float STORY_CODEX_TAB_HEIGHT    = 74f;
    public static final float STORY_CODEX_TAB_GAP       = 8f;
    public static final float STORY_CODEX_TAB_X         = STORY_CODEX_X + STORY_CODEX_PADDING;
    public static final float STORY_CODEX_TABS_TOP_Y    = STORY_CODEX_CONTENT_TOP_Y;
    public static final float STORY_CODEX_TAB_TEXT_SIZE = 1.15f;
    /** Progress readout under a tab's name ("3/5") — dim, never a score. */
    public static final float STORY_CODEX_TAB_COUNT_TEXT_SIZE = 0.95f;

    // 3. BODY — the entry list, and the entry detail that replaces it.  Both scroll inside it.
    public static final float STORY_CODEX_BODY_GAP    = 20f;
    public static final float STORY_CODEX_BODY_X      =
            STORY_CODEX_TAB_X + STORY_CODEX_TAB_WIDTH + STORY_CODEX_BODY_GAP;
    public static final float STORY_CODEX_BODY_WIDTH  =
            STORY_CODEX_RIGHT_X - STORY_CODEX_PADDING - STORY_CODEX_BODY_X;
    public static final float STORY_CODEX_BODY_TOP_Y  = STORY_CODEX_CONTENT_TOP_Y;

    // 4. FOOTER — the accessibility strip (Part D), directly under the body and only as wide as it.
    public static final float STORY_CODEX_FOOTER_HEIGHT     = 56f;
    public static final float STORY_CODEX_FOOTER_GAP        = 14f;
    public static final float STORY_CODEX_FOOTER_Y          = STORY_CODEX_Y + STORY_CODEX_PADDING;
    public static final float STORY_CODEX_FOOTER_TEXT_SIZE  = 1.0f;
    /** How many settings buttons the strip holds — text size, reveal pacing, motion, story audio. */
    public static final int   STORY_CODEX_SETTING_COUNT     = 4;
    public static final float STORY_CODEX_SETTING_GAP       = 12f;
    public static final float STORY_CODEX_SETTING_WIDTH     =
            (STORY_CODEX_BODY_WIDTH - (STORY_CODEX_SETTING_COUNT - 1) * STORY_CODEX_SETTING_GAP)
                    / STORY_CODEX_SETTING_COUNT;
    public static final float STORY_CODEX_BODY_BOTTOM_Y     =
            STORY_CODEX_FOOTER_Y + STORY_CODEX_FOOTER_HEIGHT + STORY_CODEX_FOOTER_GAP;
    public static final float STORY_CODEX_BODY_HEIGHT       =
            STORY_CODEX_BODY_TOP_Y - STORY_CODEX_BODY_BOTTOM_Y;

    // ENTRY LIST rows — one tappable plate per unlocked entry.  A locked entry is not listed at all:
    // a wall of "???" is a chore list, and the codex must never feel like homework.
    public static final float STORY_CODEX_ROW_HEIGHT     = 66f;
    public static final float STORY_CODEX_ROW_GAP        = 8f;
    public static final float STORY_CODEX_ROW_TEXT_SIZE  = 1.2f;
    /** Source line under a row's title (where the entry came from) — dim and small. */
    public static final float STORY_CODEX_ROW_SOURCE_TEXT_SIZE = 0.9f;
    /** The "NEW" dot: an invitation to look, never a badge that nags.  Cleared once read. */
    public static final float STORY_CODEX_NEW_DOT_RADIUS = 7f;
    public static final float STORY_CODEX_NEW_DOT_INSET  = 22f;

    // ENTRY DETAIL — the long text.  Generous line pitch; the whole point of this screen is that it
    // is comfortable to read for a long time on a phone.
    public static final float STORY_CODEX_DETAIL_LINE_PITCH   = 32f;
    public static final float STORY_CODEX_DETAIL_TEXT_SIZE    = 1.3f;
    /** Wrap width in characters at the default text size; scaled down as the size setting grows. */
    public static final int   STORY_CODEX_LINE_MAX_CHARS      = 62;
    /** Blank-line pitch between the detail's blocks (title / source / ORA's take / body). */
    public static final float STORY_CODEX_DETAIL_BLOCK_GAP    = 16f;

    // SCROLLING — drag the body with a thumb.  Vertical only: nothing in the codex ever scrolls
    // sideways, and content is wrapped to fit rather than clipped.
    public static final float STORY_CODEX_SCROLL_DRAG_SCALE = 1.0f;
    /** A drag shorter than this is a TAP, not a scroll — so a slightly shaky finger still opens an entry. */
    public static final float STORY_CODEX_TAP_SLOP          = 12f;

    // Plate styling, shared by tabs, rows and setting buttons (the exchange plates' language).
    public static final float STORY_CODEX_PLATE_BORDER          = 2f;
    public static final float STORY_CODEX_PLATE_FILL_ALPHA      = 0.16f;
    public static final float STORY_CODEX_PLATE_SELECTED_ALPHA  = 0.46f;
    public static final float STORY_CODEX_PLATE_CORNER          = 10f;

    /** Seconds the codex panel takes to fade in / out — a menu, so brisk. */
    public static final float STORY_CODEX_FADE_SECONDS = 0.18f;

    // Localisation ids for the codex's own chrome (localisation rule: never a literal in render).
    public static final String STORY_CODEX_TITLE_ID            = "story.codex.title";
    public static final String STORY_CODEX_BACK_LABEL_ID       = "story.codex.back";
    public static final String STORY_CODEX_EMPTY_ID            = "story.codex.empty";
    public static final String STORY_CODEX_NEW_LABEL_ID        = "story.codex.new";
    public static final String STORY_CODEX_COMPLETE_LABEL_ID   = "story.codex.complete";
    public static final String STORY_CODEX_TAKE_LABEL_ID       = "story.codex.take";
    public static final String STORY_CODEX_SETTING_TEXT_ID     = "story.codex.setting.text";
    public static final String STORY_CODEX_SETTING_REVEAL_ID   = "story.codex.setting.reveal";
    public static final String STORY_CODEX_SETTING_MOTION_ID   = "story.codex.setting.motion";
    public static final String STORY_CODEX_SETTING_AUDIO_ID    = "story.codex.setting.audio";

    // =====================================================================
    // ACCESSIBILITY SETTINGS (order-6 Part D) — the player-facing knobs, and their persisted keys.
    // Defaults are the COMFORTABLE end of every scale: big text, paced reveal, motion on.  Nothing
    // here is ever required to understand the game — a setting may only make reading easier.
    // =====================================================================
    /** Multipliers over {@link #STORY_TEXT_SIZE}, indexed by {@code StoryTextSize.ordinal()}. */
    public static final float[] STORY_TEXT_SIZE_MULTIPLIER = { 1.0f, 1.2f, 1.45f };
    /** Persisted setting keys.  Stable once shipped — they are baked into saves. */
    public static final String STORY_SETTING_TEXT_SIZE      = "textSize";
    public static final String STORY_SETTING_REDUCE_HOLD    = "reduceTextHold";
    public static final String STORY_SETTING_REDUCE_MOTION  = "reduceMotion";
    /**
     * Story audio on/off (order-7 Part D).  Defaults to ON, because the cues only ever reinforce
     * what the panel already says in text — but every one of them is optional, so the whole layer
     * has a single switch rather than a per-cue submenu.
     */
    public static final String STORY_SETTING_STORY_AUDIO    = "storyAudio";

    // =====================================================================
    // PACING BUDGET (order-6 Part B) — the anti-overwhelm rules that are NOT already a cooldown.
    // The rate limits live in the bark section above; this is the one rule they cannot express:
    // no story UI lands during a combat spike.  It is HELD, not dropped — the line arrives in the
    // lull right after, which is when the player can actually read it.
    // =====================================================================
    /** This many awake enemies (or more) is a spike: nothing non-critical is delivered until it ends. */
    public static final int STORY_COMBAT_SPIKE_AWAKE_ENEMIES = 2;

    // =====================================================================
    // TELEMETRY (order-6 Part E) — in-memory tuning counters only.  No PII, no identifiers, no I/O:
    // the numbers exist so a designer can ask "which lines are being skipped?" and prune them.
    // =====================================================================
    /** A bark dismissed faster than this was skipped, not read — the signal Part E exists to collect. */
    public static final float STORY_TELEMETRY_FAST_DISMISS_SECONDS = 1.5f;

    // =====================================================================
    // FRAMING SCREENS (order-8) — the screens that BOOK-END play: the launch / title screen, the
    // pause menu, the settings screen, the reprint report folded into the death->reprint beat, and
    // the way out of an ending that authorises no reprint.
    //
    // They all speak the boot card's visual language on purpose (the same near-black full-bleed
    // backdrop, the same plate styling), because the launch screen's death notice and the reprint
    // card are the SAME machine talking — that continuity is the whole trick order-8 plays.
    // =====================================================================
    /**
     * Where the ENDING the player committed to is filed (order-8 Part D).  Stored through the
     * order-4 consequential-outcome mechanism — the ending IS a consequential outcome — so it needs
     * no new persisted key and no schema bump, and a "new game" wipe clears it with everything else.
     * The value is a {@code BootCardVariant} name; the launch screen reads it back to decide which
     * card's status lines it prints.
     */
    public static final String STORY_ENDING_OUTCOME_ID = "ending";

    // Full-bleed backdrop shared by every framing screen — the boot card's blackout, reused.
    public static final float STORY_FRAME_BACKDROP_ALPHA = STORY_BOOT_BACKDROP_ALPHA;
    public static final float STORY_FRAME_BACKDROP_R = STORY_BOOT_BACKDROP_R,
                              STORY_FRAME_BACKDROP_G = STORY_BOOT_BACKDROP_G,
                              STORY_FRAME_BACKDROP_B = STORY_BOOT_BACKDROP_B;
    /** Seconds any framing element takes to fade in.  Gentle: nothing on these screens flashes. */
    public static final float STORY_FRAME_FADE_SECONDS = 0.30f;
    /**
     * Backdrop of a framing screen drawn OVER a live floor (the pause and settings menus).  Darker
     * than the exchange's dim, lighter than the launch screen's blackout: the player is still in a
     * room, and a paused suit should not pretend otherwise.
     */
    public static final float STORY_FRAME_SCRIM_ALPHA = 0.86f;

    // --- MENU PLATES (the title menu, the pause menu, the settings rows) ---------------------
    // Big, high-contrast, thumb-first: a menu row that needs aiming is a row nobody presses.
    /** Hard cap on a framing menu's rows — five is the longest menu the game has (title, settings). */
    public static final int   STORY_FRAME_MENU_MAX_ROWS   = 6;
    public static final float STORY_FRAME_MENU_WIDTH      = 460f;
    public static final float STORY_FRAME_MENU_X          = (Constants.WORLD_WIDTH - STORY_FRAME_MENU_WIDTH) / 2f;
    public static final float STORY_FRAME_MENU_ROW_HEIGHT = 68f;
    public static final float STORY_FRAME_MENU_ROW_GAP    = 12f;
    /** Vertical pitch from one row's top edge to the next — the geometry hit-testing inverts. */
    public static final float STORY_FRAME_MENU_ROW_PITCH  =
            STORY_FRAME_MENU_ROW_HEIGHT + STORY_FRAME_MENU_ROW_GAP;
    public static final float STORY_FRAME_MENU_CORNER     = 12f;
    public static final float STORY_FRAME_MENU_BORDER     = 2f;
    public static final float STORY_FRAME_MENU_FILL_ALPHA         = 0.20f;
    public static final float STORY_FRAME_MENU_PRESSED_ALPHA      = 0.55f;
    /** A row that is offered but not available (nothing to continue yet) reads as dimmed, not absent. */
    public static final float STORY_FRAME_MENU_DISABLED_ALPHA     = 0.35f;
    public static final float STORY_FRAME_MENU_TEXT_SIZE          = 1.3f;
    /** A settings row shows its current value on the right, a shade smaller than its name. */
    public static final float STORY_FRAME_MENU_VALUE_TEXT_SIZE    = 1.1f;
    /** Top edge of the first row on the pause / settings screens (the title screen sits lower). */
    public static final float STORY_FRAME_MENU_TOP_Y   = 540f;
    /** The screen's own heading ("SUIT PAUSED", "SETTINGS"), centred above the rows. */
    public static final float STORY_FRAME_HEADER_TOP_Y   = 624f;
    public static final float STORY_FRAME_HEADER_TEXT_SIZE = 1.7f;

    // --- LAUNCH SEQUENCE (the title screen's top-down stack) ----------------------------------
    // 1. the death notice types out, unhurried;  2. the title resolves quietly beneath it;
    // 3. the menu fades in under that.  A tap prints all of it at once — the reveal is mood, and
    // mood is never allowed to be a gate (order-6 Part C).
    /**
     * HARD CAP on how many of the boot card's status lines the launch screen borrows.  HOW MANY it
     * actually prints is per card ({@code BootCardDefinition.getLaunchStatusLineCount()}): the
     * FIRST-PRINT card prints its whole four-line block, because on a save with nothing in it those
     * four lines are the only explanation of anything the player is about to read; every other card
     * stops before its closing "PRINTING NEW BODY" line, because that is a promise about a run that
     * has not started yet and the title screen makes no promises (narrative-rework order-2 A).
     */
    public static final int   STORY_TITLE_STATUS_LINE_COUNT   = 4;
    /**
     * Top edge of the FIRST status line when the whole four-line block prints.  A card that prints
     * fewer lines is anchored from the BOTTOM of the block instead, so the notice always ends just
     * above the title rather than leaving a hole under a short one.
     */
    public static final float STORY_TITLE_STATUS_TOP_Y        = 704f;
    public static final float STORY_TITLE_STATUS_TEXT_SIZE    = 1.35f;
    public static final float STORY_TITLE_STATUS_LINE_PITCH   = 40f;
    /** Seconds between status lines printing.  Slower than the boot card's: nothing is happening yet. */
    public static final float STORY_TITLE_LINE_REVEAL_SECONDS = 0.90f;
    /** The beat between the machine finishing and the title resolving. */
    public static final float STORY_TITLE_TITLE_DELAY_SECONDS = 0.60f;
    /** The beat between the title resolving and the menu arriving under it. */
    public static final float STORY_TITLE_MENU_DELAY_SECONDS  = 0.50f;
    /** The whole screen rises out of black this slowly — a low, quiet start, never a splash. */
    public static final float STORY_TITLE_FADE_IN_SECONDS     = 0.80f;
    public static final float STORY_TITLE_GAME_TOP_Y          = 560f;
    public static final float STORY_TITLE_GAME_TEXT_SIZE      = 3.2f;
    public static final float STORY_TITLE_TAGLINE_TOP_Y       = 486f;
    public static final float STORY_TITLE_TAGLINE_TEXT_SIZE   = 1.1f;
    /** Top edge of the title menu's first row: five rows clear the screen's bottom edge. */
    public static final float STORY_TITLE_MENU_TOP_Y          = 424f;
    /** The title itself is dim white, not accented: this screen is cold, not branded. */
    public static final float STORY_TITLE_GAME_R = 0.88f, STORY_TITLE_GAME_G = 0.90f,
                              STORY_TITLE_GAME_B = 0.93f;
    public static final float STORY_TITLE_TAGLINE_R = 0.46f, STORY_TITLE_TAGLINE_G = 0.48f,
                              STORY_TITLE_TAGLINE_B = 0.52f;

    // --- CONFIRMATION (the wipe, the abandon) --------------------------------------------------
    // A consequence is never one tap away, and every confirmation carries a way back.
    public static final float STORY_FRAME_CONFIRM_WIDTH  = 680f;
    public static final float STORY_FRAME_CONFIRM_X      =
            (Constants.WORLD_WIDTH - STORY_FRAME_CONFIRM_WIDTH) / 2f;
    /** Panel height for 3 body lines by the same rule the other panels use: 30 * lines + 70. */
    public static final float STORY_FRAME_CONFIRM_HEIGHT = 160f;
    public static final float STORY_FRAME_CONFIRM_TOP_Y  = 470f;
    public static final float STORY_FRAME_CONFIRM_Y      =
            STORY_FRAME_CONFIRM_TOP_Y - STORY_FRAME_CONFIRM_HEIGHT;
    /** Max wrapped lines of the question — it must be ONE plain sentence, so three is plenty. */
    public static final int   STORY_FRAME_CONFIRM_MAX_LINES = 3;
    public static final float STORY_FRAME_CONFIRM_BUTTON_WIDTH  = 300f;
    public static final float STORY_FRAME_CONFIRM_BUTTON_HEIGHT = 84f;
    public static final float STORY_FRAME_CONFIRM_BUTTON_GAP    = 40f;
    public static final float STORY_FRAME_CONFIRM_BUTTON_Y      =
            STORY_FRAME_CONFIRM_Y - 26f - STORY_FRAME_CONFIRM_BUTTON_HEIGHT;
    /** PROCEED sits left of centre, CANCEL right of it — both a full thumb apart. */
    public static final float STORY_FRAME_CONFIRM_YES_X =
            Constants.WORLD_WIDTH / 2f - STORY_FRAME_CONFIRM_BUTTON_GAP / 2f
                    - STORY_FRAME_CONFIRM_BUTTON_WIDTH;
    public static final float STORY_FRAME_CONFIRM_NO_X  =
            Constants.WORLD_WIDTH / 2f + STORY_FRAME_CONFIRM_BUTTON_GAP / 2f;

    // --- THE DEATH STROKE (narrative-rework order-2) --------------------------------------------
    // The moment of death is THREE WORDS on black and nothing else — no counter, no status block,
    // no button, and no ORA.  It is a separate beat from the reprint card on purpose: the card
    // reports a BIRTH (a machine somewhere else is making another body), and merging the two meant
    // neither landed.  ORA's absence here is the point and costs nothing: she does not get printed,
    // she reloads, so for one second the only voice in the game is missing.
    //
    // The words never change, at any depth, in any region — that constancy IS the design.  What
    // changes across a save is her line on the card that follows, which is where the escalation
    // lives (BootCardCatalog's reserved rows, then the region pools).
    /** Seconds the words hold once the screen is fully black.  A tap skips straight to the card. */
    public static final float STORY_DEATH_STROKE_HOLD_SECONDS = 1.9f;
    /** Seconds the words take to resolve out of the black.  They arrive; they never flash. */
    public static final float STORY_DEATH_STROKE_FADE_SECONDS = 0.55f;
    /** Centred, high on the screen's middle band — the only thing drawn. */
    public static final float STORY_DEATH_STROKE_TOP_Y        = 404f;
    public static final float STORY_DEATH_STROKE_TEXT_SIZE    = 2.6f;
    /** Bone white, slightly cold.  Not red: this game does not shout at a player who just died. */
    public static final float STORY_DEATH_STROKE_R = 0.86f, STORY_DEATH_STROKE_G = 0.87f,
                              STORY_DEATH_STROKE_B = 0.90f;
    /** Localisation id of the words (localisation rule: never a literal in the renderer). */
    public static final String STORY_DEATH_STROKE_ID = "story.death.stroke";

    // --- THE ENDING'S WAY OUT ------------------------------------------------------------------
    // A terminal card (FREE / KILL) draws no CONTINUE — that absence IS the ending (order-3).  What
    // order-8 adds is the way OFF that final screen, once the card has finished printing: a dim
    // plate in the CONTINUE's place that returns to a title screen the ending has changed.
    public static final float STORY_FRAME_RETURN_FILL_ALPHA = 0.14f;
    /** MERGE presents no card at all; the frozen view holds this long, fading, then stands down. */
    public static final float STORY_ENDING_MERGE_HOLD_SECONDS = 6.0f;

    // --- THE REPRINT REPORT (the folded death overlay) -----------------------------------------
    // The run that just ended is not a second screen competing with the reprint card: it is a page
    // OF that screen, reached by a small plate and dismissed by one tap.  The default path through a
    // death is untouched — fade, card, CONTINUE — and the numbers are there for whoever wants them.
    public static final float STORY_REPORT_PANEL_WIDTH  = 860f;
    public static final float STORY_REPORT_PANEL_X      =
            (Constants.WORLD_WIDTH - STORY_REPORT_PANEL_WIDTH) / 2f;
    public static final float STORY_REPORT_PANEL_HEIGHT = 452f;
    public static final float STORY_REPORT_PANEL_TOP_Y  = 656f;
    public static final float STORY_REPORT_PANEL_Y      =
            STORY_REPORT_PANEL_TOP_Y - STORY_REPORT_PANEL_HEIGHT;
    public static final float STORY_REPORT_TITLE_TEXT_SIZE = 1.5f;
    public static final float STORY_REPORT_TEXT_SIZE       = 1.0f;
    public static final float STORY_REPORT_LINE_PITCH      = 30f;
    /** Distance from the panel's top edge to the heading, and from the heading to the first line. */
    public static final float STORY_REPORT_TITLE_INSET_Y   = 28f;
    public static final float STORY_REPORT_FIRST_LINE_INSET_Y = 86f;
    public static final float STORY_REPORT_TEXT_INSET_X    = 40f;
    /** Four stat lines, a blank, and the six-line RUN AUTOPSY (new-game-balancr order 9, part C). */
    public static final int   STORY_REPORT_MAX_LINES       = 12;
    public static final float STORY_REPORT_R = 0.72f, STORY_REPORT_G = 0.75f, STORY_REPORT_B = 0.80f;
    /** The record marker beside a stat that beat the save's best — dim gold, never a fanfare. */
    public static final float STORY_REPORT_RECORD_R = 1.0f, STORY_REPORT_RECORD_G = 0.85f,
                              STORY_REPORT_RECORD_B = 0.35f;
    /** Column the stat VALUES start at, so the four readouts line up like a machine printout. */
    public static final float STORY_REPORT_VALUE_INSET_X = 380f;
    /** The plate on the reprint card that opens the report, tucked bottom-left out of the way. */
    public static final float STORY_REPORT_OPEN_WIDTH  = 220f;
    public static final float STORY_REPORT_OPEN_HEIGHT = 76f;
    public static final float STORY_REPORT_OPEN_X      = 60f;
    public static final float STORY_REPORT_OPEN_Y      = 110f;
    /** BACK out of the report page, in the CONTINUE button's spot so the thumb knows where to go. */
    public static final float STORY_REPORT_BACK_WIDTH  = 300f;
    public static final float STORY_REPORT_BACK_HEIGHT = 84f;
    public static final float STORY_REPORT_BACK_X      =
            (Constants.WORLD_WIDTH - STORY_REPORT_BACK_WIDTH) / 2f;
    public static final float STORY_REPORT_BACK_Y      = 96f;

    // --- Localisation ids for the framing chrome (never a literal in a renderer) ----------------
    public static final String STORY_TITLE_GAME_ID        = "story.title.game";
    public static final String STORY_TITLE_TAGLINE_ID     = "story.title.tagline";
    public static final String STORY_PAUSE_TITLE_ID       = "story.pause.title";
    public static final String STORY_SETTINGS_TITLE_ID    = "story.settings.title";
    public static final String STORY_SETTINGS_BACK_ID     = "story.settings.back";
    public static final String STORY_FRAME_CONFIRM_YES_ID = "story.frame.confirm.yes";
    public static final String STORY_FRAME_CONFIRM_NO_ID  = "story.frame.confirm.no";
    public static final String STORY_FRAME_RETURN_ID      = "story.frame.return";
    public static final String STORY_REPORT_TITLE_ID      = "story.report.title";
    public static final String STORY_REPORT_OPEN_ID       = "story.report.open";
    public static final String STORY_REPORT_BACK_ID       = "story.report.back";
    public static final String STORY_REPORT_FLOOR_ID      = "story.report.floor";
    public static final String STORY_REPORT_KILLS_ID      = "story.report.kills";
    public static final String STORY_REPORT_DAMAGE_ID     = "story.report.damage";
    public static final String STORY_REPORT_TIME_ID       = "story.report.time";
    public static final String STORY_REPORT_BEST_ID       = "story.report.best";

    // =====================================================================
    // RE-ENTRY, RECAP & MISSED-BEAT RECOVERY (narrative-rework order-9)
    //
    // This game is played in phone-sized pieces across weeks. Everything above assumes a player who
    // remembers where they were and read the line they were shown; this block is what the layer owes
    // the two people that assumption fails for — the one who has been away, and the one who tapped a
    // one-shot line away before reading it.  No new screen and no new renderer: every number here
    // hangs off a channel the player was already going to look at.
    // =====================================================================

    /**
     * THE THREE-DAY PROBLEM (order-9 A).  A gap at least this many hours wide means the player has
     * genuinely been away, and the reprint card's one ORA slot is spent on a RE-ENTRY line — where
     * they are and what they were last told — instead of an ordinary greeting.
     *
     * <p>A day is deliberately generous.  Below it, somebody who put the phone down after lunch gets
     * told what they already know, which is the exact tone of the helper voices this layer exists
     * not to be.
     */
    public static final int   STORY_REENTRY_GAP_HOURS = 24;

    /**
     * How many archive entries the recap's WHAT WE KNOW block lists (order-9 B).  Three: enough to
     * place the player in the story, few enough that the page stays a summary rather than becoming a
     * second index of a screen that is already an index.
     */
    public static final int   STORY_RECAP_RECENT_ENTRY_COUNT = 3;

    /**
     * How many missed critical lines the "you may have missed this" shelf keeps (order-9 C).  A
     * small ring, newest first: this is a safety net for the line somebody skipped last week, not a
     * transcript of the run, and an unbounded list would grow into exactly the wall of text the
     * archive is written to avoid.
     */
    public static final int   STORY_MISSED_SHELF_CAPACITY = 6;

    /**
     * Where the two order-9 rings are filed, and the separator that packs each into one value.
     *
     * <p>Both ride the order-4 consequential-outcome mechanism, exactly as {@link
     * #STORY_ENDING_OUTCOME_ID} does: it is already the schema's general "remember this string,
     * permanently" store, so a ring needs no new persisted key, no new port method, and a "new game"
     * wipe clears both with everything else — which is right, because a wiped save has neither an
     * archive to summarise nor a beat it could have missed.
     */
    public static final String STORY_RECENT_UNLOCK_RECORD_ID = "recap.recent";
    public static final String STORY_MISSED_BEAT_RECORD_ID   = "recap.missed";
    public static final String STORY_RECORD_LIST_SEPARATOR   = "|";

    /**
     * The two composed archive pages, by entry id.  Named here rather than only in the catalog
     * because the engine has to unlock the missed-beat shelf at a moment no catalog row can
     * describe — the first time a mandatory line is closed unread — and a shelf whose id was
     * spelled out twice would eventually be spelled differently in one of the two places.
     */
    public static final String STORY_RECAP_CODEX_ID        = "codex.recap";
    public static final String STORY_MISSED_SHELF_CODEX_ID = "codex.missed";

    // --- The recap page's own chrome: the four things ORA summarises, in order ------------------
    public static final String STORY_RECAP_WHERE_HEADING_ID   = "story.recap.heading.where";
    public static final String STORY_RECAP_JOB_HEADING_ID     = "story.recap.heading.job";
    public static final String STORY_RECAP_KNOW_HEADING_ID    = "story.recap.heading.know";
    public static final String STORY_RECAP_DECIDED_HEADING_ID = "story.recap.heading.decided";
    /** Stands in for a block with nothing in it yet — never a blank space where a heading promised text. */
    public static final String STORY_RECAP_NOTHING_YET_ID     = "story.recap.nothing";
}
