package ge.tbegvadze.toon3d.util;

/**
 * Enemy intent-telegraph constants (strategy-combat-order-2).
 *
 * <p>Geometry, per-verb frame colours, and animation timings for the floating "what happens next"
 * icon drawn above every alerted enemy that has committed a {@code PlannedAction}. Rendering lives in
 * {@code EnemyRenderer}; this file is the single source of truth for the numbers so nothing is
 * hardcoded (project split-constants rule).
 *
 * <p>Colour language is doubled on hue AND glyph shape so danger survives colourblindness:
 * red/orange + spiky glyph = "damage incoming"; blue + shield = "turtling"; yellow + footsteps =
 * "repositioning"; purple + rune = "special"; grey + dots/X = "no threat / disabled".
 */
public final class IntentConstants {

    private IntentConstants() {}

    // --- Geometry (screen pixels within the 1280x720 wall-projection space) ---
    /** Base edge length of the square intent frame at 1-tile distance, before distance scaling. */
    public static final float INTENT_ICON_SIZE_WORLD = 34f;
    /** Lower clamp so a far-off sniper's committed lane stays legible. */
    public static final float INTENT_ICON_MIN_SIZE   = 18f;
    /** Upper clamp so a point-blank enemy's icon does not swallow the screen. */
    public static final float INTENT_ICON_MAX_SIZE   = 46f;
    /** Reference distance (tiles) at which the icon renders at INTENT_ICON_SIZE_WORLD (1/depth scaling). */
    public static final float INTENT_ICON_REFERENCE_DISTANCE_TILES = 3f;
    /** Beyond this distance no icon is drawn (mirrors the health-bar cutoff band, slightly longer). */
    public static final float INTENT_ICON_MAX_DISTANCE_TILES = 14f;
    /** Vertical gap between the top of the name-tag/health-bar cluster and the icon's bottom edge. */
    public static final float INTENT_ICON_Y_OFFSET_ABOVE_HEALTHBAR = 6f;
    /** Extra vertical band reserved above the health bar for the (existing) name tag, so the icon clears it. */
    public static final float INTENT_ICON_NAMETAG_CLEARANCE = 16f;

    // --- Frame / border ---
    public static final float INTENT_FRAME_ALPHA        = 0.88f;
    public static final float INTENT_FRAME_BORDER_PIXELS = 2f;
    /** Border colour (near-black) framing every icon for contrast against bright rooms. */
    public static final float INTENT_FRAME_BORDER_RED   = 0.04f;
    public static final float INTENT_FRAME_BORDER_GREEN = 0.04f;
    public static final float INTENT_FRAME_BORDER_BLUE  = 0.04f;
    /** Glyph ink colour (bright, drawn on top of the tinted frame). */
    public static final float INTENT_GLYPH_RED   = 0.98f;
    public static final float INTENT_GLYPH_GREEN = 0.98f;
    public static final float INTENT_GLYPH_BLUE  = 0.98f;

    /** Fraction of the icon size reserved above the health-bar cluster for the magnitude number band. */
    public static final float INTENT_NUMBER_BAND_FRACTION = 0.55f;

    // --- Intent text label (plain-word description under the icon: "Attacking", "Moving", ...) ---
    // Icons alone can be cryptic to new players, so a short verb label is stacked just above the icon.
    /**
     * Font scale for the intent word label (relative to the shared 1x name-tag font). Matched to
     * {@code EnemyConstants.ENEMY_NAME_TAG_FONT_SCALE} so the intent text reads at the same size as the
     * enemy name tag above it.
     */
    public static final float INTENT_LABEL_FONT_SCALE          = 1.15f;
    /** Vertical gap (screen pixels) between the top of the icon frame and the label baseline band. */
    public static final float INTENT_LABEL_GAP_ABOVE_ICON      = 6f;
    /** Beyond this distance the label is suppressed to de-clutter distant rooms (icon still shows). */
    public static final float INTENT_LABEL_MAX_DISTANCE_TILES  = 9f;
    /** How far the label text is pushed toward white (0 = raw frame hue, 1 = white) so tinted hues stay readable. */
    public static final float INTENT_LABEL_WHITEN              = 0.55f;

    // Dark rounded backing plate drawn behind the label so the words stay readable over bright/noisy
    // walls regardless of the frame hue. Padding is added around the measured glyph box.
    public static final float INTENT_LABEL_PLATE_ALPHA  = 0.62f;
    public static final float INTENT_LABEL_PLATE_PAD_X  = 5f;
    public static final float INTENT_LABEL_PLATE_PAD_Y  = 3f;
    public static final float INTENT_LABEL_PLATE_RED    = 0.02f;
    public static final float INTENT_LABEL_PLATE_GREEN  = 0.02f;
    public static final float INTENT_LABEL_PLATE_BLUE   = 0.02f;

    // --- Damage number ---
    public static final float INTENT_DAMAGE_NUMBER_SCALE      = 0.62f;
    public static final float INTENT_DAMAGE_MAX_DISTANCE_TILES = 9f;
    public static final float INTENT_DAMAGE_NUMBER_RED   = 1.00f;
    public static final float INTENT_DAMAGE_NUMBER_GREEN = 0.92f;
    public static final float INTENT_DAMAGE_NUMBER_BLUE  = 0.55f;
    /** DEFEND shows a Block value instead of damage — rendered in a calm blue, not alarm yellow. */
    public static final float INTENT_BLOCK_NUMBER_RED   = 0.55f;
    public static final float INTENT_BLOCK_NUMBER_GREEN = 0.80f;
    public static final float INTENT_BLOCK_NUMBER_BLUE  = 1.00f;

    // --- Animation ---
    /** Idle bob amplitude (pixels) so a room of icons breathes gently. */
    public static final float INTENT_ICON_BOB_AMPLITUDE = 2.5f;
    /** Idle bob frequency (Hz). */
    public static final float INTENT_ICON_BOB_HZ        = 0.6f;
    /** Duration of the scale-in "pop" played when an enemy re-commits to a different verb. */
    public static final float INTENT_POP_DURATION_SECONDS = 0.15f;
    /** Peak extra scale at the start of the pop (1.0 = +100% then eases back to 1x). */
    public static final float INTENT_POP_SCALE_BONUS      = 0.55f;
    /** Peak extra scale added by the WIND_UP pulse at full telegraph strength. */
    public static final float INTENT_WINDUP_PULSE_SCALE_BONUS = 0.30f;

    // --- Per-verb frame colours (RGB) ---
    // ATTACK_MELEE — red.
    public static final float INTENT_COLOR_ATTACK_RED   = 0.86f;
    public static final float INTENT_COLOR_ATTACK_GREEN = 0.14f;
    public static final float INTENT_COLOR_ATTACK_BLUE  = 0.12f;
    // ATTACK_RANGED — orange.
    public static final float INTENT_COLOR_RANGED_RED   = 0.95f;
    public static final float INTENT_COLOR_RANGED_GREEN = 0.50f;
    public static final float INTENT_COLOR_RANGED_BLUE  = 0.08f;
    // WIND_UP — deep red (pulses).
    public static final float INTENT_COLOR_WINDUP_RED   = 0.72f;
    public static final float INTENT_COLOR_WINDUP_GREEN = 0.05f;
    public static final float INTENT_COLOR_WINDUP_BLUE  = 0.05f;
    // MOVE — yellow.
    public static final float INTENT_COLOR_MOVE_RED     = 0.90f;
    public static final float INTENT_COLOR_MOVE_GREEN   = 0.78f;
    public static final float INTENT_COLOR_MOVE_BLUE    = 0.16f;
    // DEFEND — blue.
    public static final float INTENT_COLOR_DEFEND_RED   = 0.18f;
    public static final float INTENT_COLOR_DEFEND_GREEN = 0.45f;
    public static final float INTENT_COLOR_DEFEND_BLUE  = 0.92f;
    // SPECIAL — purple.
    public static final float INTENT_COLOR_SPECIAL_RED  = 0.62f;
    public static final float INTENT_COLOR_SPECIAL_GREEN = 0.24f;
    public static final float INTENT_COLOR_SPECIAL_BLUE = 0.86f;
    // SELF_DESTRUCT (Plague Hulk finisher) — sickly green, overrides the purple SPECIAL frame so it
    // is never mistaken for the blue DEFEND shield or the deep-red WIND_UP charger telegraph.
    public static final float INTENT_COLOR_SELF_DESTRUCT_RED   = 0.35f;
    public static final float INTENT_COLOR_SELF_DESTRUCT_GREEN = 0.95f;
    public static final float INTENT_COLOR_SELF_DESTRUCT_BLUE  = 0.30f;
    // WAIT — grey, low alpha applied at draw time.
    public static final float INTENT_COLOR_WAIT_RED     = 0.45f;
    public static final float INTENT_COLOR_WAIT_GREEN   = 0.45f;
    public static final float INTENT_COLOR_WAIT_BLUE    = 0.48f;
    /** WAIT frames are dimmed so the player can deprioritise them at a glance. */
    public static final float INTENT_WAIT_ALPHA_SCALE   = 0.55f;
    // STUNNED — grey (wasted turn; positive feedback for status builds).
    public static final float INTENT_COLOR_STUNNED_RED  = 0.40f;
    public static final float INTENT_COLOR_STUNNED_GREEN = 0.40f;
    public static final float INTENT_COLOR_STUNNED_BLUE = 0.42f;

    // --- Priority read (strategy-combat-order-6) — teaching, never auto-play ---
    /**
     * How far the intent frame of the PRIORITY target is brightened toward white (0 = no change,
     * 1 = full white). A subtle emphasis on the highest-threat committed hit and on support casters
     * (BUFF_SELF) so a new player's eye is guided to "the big number / the buffer" — the classic
     * "who to kill first" read. Kept small so experts still read the raw numbers; it never shows an
     * optimal move, it only makes the salient info salient.
     */
    public static final float INTENT_PRIORITY_FRAME_BRIGHTEN = 0.40f;

    // --- Ranged "locked" outline (enemy is on the player's line — the deadly one) ---
    public static final float INTENT_LOCKED_OUTLINE_RED   = 1.00f;
    public static final float INTENT_LOCKED_OUTLINE_GREEN = 0.95f;
    public static final float INTENT_LOCKED_OUTLINE_BLUE  = 0.35f;
    public static final float INTENT_LOCKED_OUTLINE_PIXELS = 2.5f;
}
