package ge.tbegvadze.toon3d.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.entity.Loadout;
import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.entity.PlayerInventory;
import ge.tbegvadze.toon3d.entity.DamageClass;
import ge.tbegvadze.toon3d.entity.Weapon;
import ge.tbegvadze.toon3d.render.FontFace;
import ge.tbegvadze.toon3d.render.GameFonts;
import ge.tbegvadze.toon3d.render.MatchupGlyphs;
import ge.tbegvadze.toon3d.render.PlateState;
import ge.tbegvadze.toon3d.render.Renderable;
import ge.tbegvadze.toon3d.render.UiKit;
import ge.tbegvadze.toon3d.render.WeaponLevelTag;
import ge.tbegvadze.toon3d.status.StatusEffect;
import ge.tbegvadze.toon3d.status.StatusType;
import ge.tbegvadze.toon3d.util.ArtDirection;
import ge.tbegvadze.toon3d.util.CombatPalette;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.EffectConstants;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.HudConstants;
import ge.tbegvadze.toon3d.world.HudState;

/**
 * Left chrome HUD panel only (x 0..420, y 0..206 — bottom-left corner).
 *
 * Reworked vitals layout: four "vital rows" (HP, AR, AMMO, XP) stacked top-to-bottom, each one a
 * left-aligned label, a segmented bar (split into small rectangles), and a right-aligned numeric
 * value. Labels sit to the LEFT of every bar and values to the RIGHT, so text is never drawn over a
 * bar and the elements can never overlap. Each filled HP tick is coloured by its position on a
 * red→green ramp, so the row of rectangles reads as a green→red gradient (green ticks appear only
 * near full health). The ammo bar draws one tick per round in the clip. Below the bars sit a
 * numbered weapon-slot strip and a thin status-effect icon row in its own clear band.
 *
 * Every glyph is drawn with a dark drop-shadow for contrast against the busy 3D view behind it.
 *
 * Render order per frame:
 *   Pass A — ShapeRenderer.Filled  (panel chrome, segmented bars, slots — alpha-blended)
 *   Pass B — ShapeRenderer.Line    (bevels, bar/slot outlines)
 *   Pass C — SpriteBatch + BitmapFont (labels, values, slot text, status counts)
 */
public class HudRenderer implements Renderable, Disposable {

    // -------------------------------------------------------------------------
    // Palette (visual-identity-order-4 R9): SUIT ink, read from ArtDirection. Pre-built once at class load
    // for the text paths that take a Color; shapes read the ArtDirection floats directly.
    // -------------------------------------------------------------------------
    private static final Color TEXT_PRIMARY   = new Color(ArtDirection.UI_TEXT_PRIMARY_RED,
            ArtDirection.UI_TEXT_PRIMARY_GREEN, ArtDirection.UI_TEXT_PRIMARY_BLUE, 1f);
    private static final Color TEXT_SECONDARY = new Color(ArtDirection.UI_TEXT_SECONDARY_RED,
            ArtDirection.UI_TEXT_SECONDARY_GREEN, ArtDirection.UI_TEXT_SECONDARY_BLUE, 1f);
    private static final Color TEXT_DIM       = new Color(ArtDirection.UI_TEXT_DISABLED_RED,
            ArtDirection.UI_TEXT_DISABLED_GREEN, ArtDirection.UI_TEXT_DISABLED_BLUE, 1f);
    private static final Color SUIT_AMBER     = new Color(ArtDirection.SUIT_AMBER_RED,
            ArtDirection.SUIT_AMBER_GREEN, ArtDirection.SUIT_AMBER_BLUE, 1f);
    private static final Color DAMAGE         = new Color(CombatPalette.DAMAGE_RED,
            CombatPalette.DAMAGE_GREEN, CombatPalette.DAMAGE_BLUE, 1f);
    private static final Color HEALTH_GREEN   = new Color(ArtDirection.HEALTH_GREEN_RED,
            ArtDirection.HEALTH_GREEN_GREEN, ArtDirection.HEALTH_GREEN_BLUE, 1f);
    /** Midpoint of the HP ramp: the brighter channel of each endpoint, so the ramp passes through a warm yellow. */
    private static final Color HEALTH_RAMP_MIDDLE = new Color(
            Math.max(CombatPalette.DAMAGE_RED, ArtDirection.HEALTH_GREEN_RED),
            Math.max(CombatPalette.DAMAGE_GREEN, ArtDirection.HEALTH_GREEN_GREEN),
            (CombatPalette.DAMAGE_BLUE + ArtDirection.HEALTH_GREEN_BLUE) * 0.5f, 1f);

    // Status icon colors — indexed by StatusType ordinal
    // BURNING=0 POISONED=1 BLEED=2 STUNNED=3 BLINDED=4 SLOWED=5 EMPOWERED=6 VULNERABLE=7 WEAK=8 EXPOSED=9
    // Order-6 powers (7-9) follow the combat colour language: VULNERABLE red, WEAK grey-purple, EXPOSED purple.
    private static final float[] STATUS_ICON_RED   = { 1.00f, 0.00f, 0.85f, 1.00f, 0.30f, 0.25f, 0.85f,
            CombatPalette.VULNERABLE_RED,   CombatPalette.WEAK_RED,   CombatPalette.EXPOSED_RED };
    private static final float[] STATUS_ICON_GREEN = { 0.45f, 0.80f, 0.05f, 1.00f, 0.00f, 0.40f, 0.10f,
            CombatPalette.VULNERABLE_GREEN, CombatPalette.WEAK_GREEN, CombatPalette.EXPOSED_GREEN };
    private static final float[] STATUS_ICON_BLUE  = { 0.00f, 0.15f, 0.10f, 1.00f, 0.50f, 0.70f, 0.00f,
            CombatPalette.VULNERABLE_BLUE,  CombatPalette.WEAK_BLUE,  CombatPalette.EXPOSED_BLUE };
    private static final StatusType[] STATUS_TYPES = StatusType.values();
    static {
        // Fail fast if a new StatusType was added without updating the icon color arrays above.
        if (STATUS_ICON_RED.length != STATUS_TYPES.length) {
            throw new IllegalStateException("STATUS_ICON color arrays must match StatusType count");
        }
    }

    private static final String MEDKIT_WARN_TEXT = "LOW HP! TAP HEAL TO USE MEDKIT";

    // -------------------------------------------------------------------------
    // Layout — derived from HudConstants
    // -------------------------------------------------------------------------
    private static final float PANEL_HEIGHT = HudConstants.HUD_HEIGHT;
    private static final float LEFT_WIDTH   = HudConstants.HUD_LEFT_PANEL_WIDTH;

    // -------------------------------------------------------------------------
    // Resources owned by this renderer
    // -------------------------------------------------------------------------
    private final ShapeRenderer shapes;
    private final SpriteBatch   batch;
    private final BitmapFont    font;
    /** HEAVY face for the ammo readout's digits (visual-identity-order-3 R7); everything else uses {@link #font}. */
    private final BitmapFont    ammoDigitsFont;

    // -------------------------------------------------------------------------
    // Inputs
    // -------------------------------------------------------------------------
    private final Player          player;
    private final HudState        hudState;
    private       PlayerInventory playerInventory = null;

    // -------------------------------------------------------------------------
    // Animation state
    // -------------------------------------------------------------------------
    private float displayedHealthFraction = 1f;
    private float displayedArmorFraction  = 0f;
    private float displayedXpFraction     = 0f;
    private float animationClockSeconds   = 0f;

    private String groundWeaponLabel = null;
    private final MatchupGlyphs matchupGlyphs = new MatchupGlyphs();

    // Reusable scratch — no allocations inside render()
    private final StringBuilder stringBuilder = new StringBuilder(64);
    private final GlyphLayout   glyphLayout   = new GlyphLayout();
    private final Color temporaryColor    = new Color();

    public HudRenderer(Player player, HudState hudState) {
        this.player   = player;
        this.hudState = hudState;
        this.shapes   = new ShapeRenderer();
        this.batch    = new SpriteBatch();
        this.font     = GameFonts.newFont(FontFace.REGULAR);
        this.ammoDigitsFont = GameFonts.newFont(FontFace.HEAVY);
    }

    /**
     * Provides the player inventory whose weapons are drawn in the left-panel strip.
     * The strip shows the melee weapon in the first slot, followed by the ranged loadout slots.
     */
    public void setPlayerInventory(PlayerInventory newPlayerInventory) {
        this.playerInventory = newPlayerInventory;
    }

    /**
     * Sets the weapon name shown in the HUD centre gap when the player stands on a weapon tile.
     * Pass null to hide the label (player stepped off or weapon was taken).
     */
    public void setGroundWeaponLabel(String label) {
        this.groundWeaponLabel = label;
    }

    /** Floor-twists R7a: true on a BLACKOUT floor — the mini-map rectangle shows the NAV OFFLINE plate. */
    private boolean navOffline = false;

    /** Shows (true) or hides the NAV OFFLINE plate in the mini-map's rectangle. World resets it every floor. */
    public void setNavOffline(boolean navOffline) {
        this.navOffline = navOffline;
    }

    public void update(float deltaTime) {
        animationClockSeconds += deltaTime;
        if (animationClockSeconds > 1000f) animationClockSeconds -= 1000f;

        float lerpRate = HudConstants.HUD_BAR_LERP_RATE * deltaTime;
        displayedHealthFraction = approach(displayedHealthFraction, player.getHealthFraction(), lerpRate);
        displayedArmorFraction  = approach(displayedArmorFraction,  player.getArmorFraction(),  lerpRate);
        displayedXpFraction     = approach(displayedXpFraction,     hudState.xpFraction,        lerpRate);
    }

    private static float approach(float current, float target, float maxStep) {
        if (current < target) return Math.min(current + maxStep, target);
        if (current > target) return Math.max(current - maxStep, target);
        return target;
    }

    @Override
    public void render(OrthographicCamera camera) {
        float   pulse  = GameMath.pulseMultiplier(animationClockSeconds, HudConstants.HUD_PULSE_HZ, 0.6f, 1.0f);
        boolean lowHp  = player.getHealthFraction() <= HudConstants.HUD_LOW_HP_THRESHOLD;
        boolean isDead = player.isDead();
        boolean alert  = hudState.alertActive;

        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        // ---- Pass A: Filled (alpha-blended) ----
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawPanelChromeFilled(0f, 0f, LEFT_WIDTH, PANEL_HEIGHT, alert, pulse);
        drawHpBarFilled(pulse, lowHp, isDead);
        drawArmorBarFilled(isDead);
        drawClipBarFilled(isDead);
        drawXpBarFilled(isDead);
        if (playerInventory != null) drawSlotStripFilled(playerInventory);
        if (!isDead) drawStatusIconsFilled();
        if (navOffline) drawNavOfflinePlateFilled();
        shapes.end();

        // ---- Pass B: Line (only the slot strip's level arrows are strokes; the chrome is filled UiKit) ----
        if (playerInventory != null) {
            shapes.begin(ShapeRenderer.ShapeType.Line);
            drawSlotStripLines(playerInventory);
            shapes.end();
        }
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // ---- Pass C: Text ----
        batch.begin();
        drawVitalsText(lowHp, pulse, isDead);
        if (playerInventory != null) drawSlotStripText(playerInventory, isDead);
        if (!isDead) drawStatusIconsText();
        if (groundWeaponLabel != null && !isDead) drawGroundWeaponLabel(groundWeaponLabel);
        boolean medkitWarn = !isDead
                && hudState.medicalCharges > 0
                && player.getHealthFraction() <= HudConstants.HUD_MEDKIT_WARN_HP_THRESHOLD;
        if (medkitWarn) drawMedkitWarning(pulse);
        drawCreditsReadout();
        if (!isDead) drawDebuffTextList();
        if (navOffline) drawNavOfflinePlateText();
        batch.end();
    }

    // =========================================================================
    // NAV OFFLINE plate (floor-twists R7a) — drawn in the mini-map's rectangle on a BLACKOUT floor,
    // where World skips the mini-map and the player dot. Static: no animation, no allocation.
    // =========================================================================

    private void drawNavOfflinePlateFilled() {
        float x = Constants.MINI_MAP_ORIGIN_X, y = Constants.MINI_MAP_ORIGIN_Y, size = Constants.MINI_MAP_WORLD_SIZE;
        UiKit.panel(shapes, x, y, size, size,
                ArtDirection.UI_PANEL_RED, ArtDirection.UI_PANEL_GREEN, ArtDirection.UI_PANEL_BLUE,
                HudConstants.TWIST_NAV_OFFLINE_PLATE_ALPHA,
                ArtDirection.UI_LINE_RED, ArtDirection.UI_LINE_GREEN, ArtDirection.UI_LINE_BLUE);
        UiKit.brackets(shapes, x, y, size, size,
                ArtDirection.SUIT_AMBER_DIM_RED, ArtDirection.SUIT_AMBER_DIM_GREEN, ArtDirection.SUIT_AMBER_DIM_BLUE, 1f);
    }

    /**
     * The facility is speaking (R8): its NAV OFFLINE notice is in FACILITY_CYAN. (R9 asked for FACILITY_CYAN_DIM,
     * which is 3.3:1 on UI_PANEL — below the 4.5:1 text floor — so the full ink is used; see the idea's
     * IMPLEMENTATION NOTE.)
     */
    private void drawNavOfflinePlateText() {
        font.getData().setScale(HudConstants.TWIST_NAV_OFFLINE_TEXT_SCALE);
        glyphLayout.setText(font, HudConstants.TWIST_NAV_OFFLINE_TEXT);
        float drawX = Constants.MINI_MAP_ORIGIN_X + (Constants.MINI_MAP_WORLD_SIZE - glyphLayout.width) / 2f;
        float drawY = Constants.MINI_MAP_ORIGIN_Y + (Constants.MINI_MAP_WORLD_SIZE + glyphLayout.height) / 2f;
        UiKit.text(batch, font, HudConstants.TWIST_NAV_OFFLINE_TEXT, drawX, drawY,
                ArtDirection.FACILITY_CYAN_RED, ArtDirection.FACILITY_CYAN_GREEN,
                ArtDirection.FACILITY_CYAN_BLUE, 1f);
        font.getData().setScale(HudConstants.HUD_LABEL_SCALE);
    }

    // =========================================================================
    // Always-on top-right readouts — credits balance and active debuff list
    // =========================================================================

    private void drawCreditsReadout() {
        font.getData().setScale(HudConstants.HUD_CREDITS_SCALE);
        stringBuilder.setLength(0);
        stringBuilder.append("CREDITS ").append(hudState.credits);
        glyphLayout.setText(font, stringBuilder);
        float drawX = Constants.WORLD_WIDTH - HudConstants.HUD_CREDITS_RIGHT_INSET_X - glyphLayout.width;
        float drawY = Constants.WORLD_HEIGHT - HudConstants.HUD_CREDITS_TOP_INSET_Y;
        UiKit.text(batch, font, stringBuilder, drawX, drawY,
                ArtDirection.CREDITS_GOLD_RED, ArtDirection.CREDITS_GOLD_GREEN, ArtDirection.CREDITS_GOLD_BLUE, 1f);
        font.getData().setScale(HudConstants.HUD_LABEL_SCALE);
    }

    private void drawDebuffTextList() {
        font.getData().setScale(HudConstants.HUD_DEBUFF_LIST_SCALE);
        float drawY = Constants.WORLD_HEIGHT - HudConstants.HUD_CREDITS_TOP_INSET_Y
                - HudConstants.HUD_DEBUFF_LIST_TOP_GAP;
        int lineIndex = 0;
        for (int typeIndex = 0; typeIndex < STATUS_TYPES.length; typeIndex++) {
            StatusEffect effect = player.getActiveEffects().get(STATUS_TYPES[typeIndex]);
            if (effect == null || !effect.isActive()) continue;
            stringBuilder.setLength(0);
            stringBuilder.append(STATUS_TYPES[typeIndex].name()).append(" (")
                          .append(effect.getRemainingTurns()).append(")  ")
                          .append(debuffDetailText(STATUS_TYPES[typeIndex], effect));
            glyphLayout.setText(font, stringBuilder);
            float drawX = Constants.WORLD_WIDTH - HudConstants.HUD_CREDITS_RIGHT_INSET_X - glyphLayout.width;
            UiKit.text(batch, font, stringBuilder, drawX, drawY - lineIndex * HudConstants.HUD_DEBUFF_LIST_LINE_STEP,
                    STATUS_ICON_RED[typeIndex], STATUS_ICON_GREEN[typeIndex], STATUS_ICON_BLUE[typeIndex], 1f);
            lineIndex++;
        }
        font.getData().setScale(HudConstants.HUD_LABEL_SCALE);
    }

    /**
     * The concrete per-turn/per-hit potency of an active status effect, appended after the
     * "(turnsRemaining)" countdown so the player sees what the debuff is actually doing, not just its
     * name — e.g. "BURNING (2)  -6 HP/TURN" or "WEAK (3)  -25% DMG DEALT". DoT types read their stored
     * per-instance magnitude (POISONED multiplies by stacks, matching StatusEffectController's tick
     * math); WEAK/SLOWED read the flat EffectConstants values since the player's multiplier helpers
     * (Player#getWeakDamageMultiplier, Player#getSlowMultiplier) don't use the per-instance magnitude.
     */
    private static String debuffDetailText(StatusType type, StatusEffect effect) {
        switch (type) {
            case BURNING:
            case BLEED:
                return "-" + effect.getMagnitude() + " HP/TURN";
            case POISONED:
                return "-" + (effect.getMagnitude() * effect.getStacks()) + " HP/TURN";
            case STUNNED:
                return "CANNOT ACT";
            case BLINDED:
                return "VISION IMPAIRED";
            case SLOWED:
                return "ACTIONS " + (int) EffectConstants.SLOW_FACTOR + "X SLOWER";
            case WEAK:
                return "-" + EffectConstants.WEAK_DAMAGE_PERCENT + "% DMG DEALT";
            case VULNERABLE:
                return "+" + (effect.getStacks() * EffectConstants.VULNERABLE_DAMAGE_PERCENT) + "% DMG TAKEN";
            case EXPOSED:
                return "BLOCK IGNORED";
            case EMPOWERED:
                return "+" + effect.getMagnitude() + "% DMG DEALT";
            default:
                return "";
        }
    }

    // =========================================================================
    // PASS A: Panel chrome
    // =========================================================================

    /**
     * R9: the panel is a UiKit chamfered slab with SUIT_AMBER_DIM brackets (replacing the old bevel lines and
     * rivets). The status LED stays: SUIT_AMBER at rest, pulsing CombatPalette.DAMAGE on alert.
     */
    private void drawPanelChromeFilled(float panelX, float panelY, float panelW, float panelH,
                                        boolean alert, float pulse) {
        UiKit.panel(shapes, panelX, panelY, panelW, panelH,
                ArtDirection.UI_PANEL_RED, ArtDirection.UI_PANEL_GREEN, ArtDirection.UI_PANEL_BLUE,
                HudConstants.HUD_PANEL_ALPHA,
                ArtDirection.UI_LINE_RED, ArtDirection.UI_LINE_GREEN, ArtDirection.UI_LINE_BLUE);
        UiKit.brackets(shapes, panelX, panelY, panelW, panelH,
                ArtDirection.SUIT_AMBER_DIM_RED, ArtDirection.SUIT_AMBER_DIM_GREEN, ArtDirection.SUIT_AMBER_DIM_BLUE, 1f);

        float ledX = panelX + panelW - HudConstants.HUD_LED_INSET;
        float ledY = panelY + panelH - HudConstants.HUD_LED_INSET;
        if (alert) {
            shapes.setColor(CombatPalette.DAMAGE_RED * pulse, CombatPalette.DAMAGE_GREEN * pulse,
                    CombatPalette.DAMAGE_BLUE * pulse, 1f);
        } else {
            shapes.setColor(ArtDirection.SUIT_AMBER_RED, ArtDirection.SUIT_AMBER_GREEN, ArtDirection.SUIT_AMBER_BLUE, 1f);
        }
        shapes.circle(ledX, ledY, HudConstants.HUD_LED_RADIUS, 8);
    }

    // =========================================================================
    // PASS A: Segmented vital bars — each bar is split into small rectangles
    // =========================================================================

    /** The HP / AR / XP bars keep their fixed tick count (AS5); a meter fraction lights whole ticks only. */
    private static float wholeTickFraction(float fraction, int segmentCount) {
        return GameMath.segmentFillCount(fraction, segmentCount) / (float) segmentCount;
    }

    private void drawHpBarFilled(float pulse, boolean lowHp, boolean isDead) {
        float fraction     = isDead ? 0f : displayedHealthFraction;
        int   segmentCount = HudConstants.HUD_BAR_SEGMENT_COUNT;
        float gap          = ArtDirection.UI_METER_GAP;
        int   fillCount    = GameMath.segmentFillCount(fraction, segmentCount);
        float segmentWidth = (HudConstants.HUD_BAR_WIDTH - (segmentCount - 1) * gap) / segmentCount;

        for (int index = 0; index < segmentCount; index++) {
            if (index < fillCount) {
                // Colour each filled segment by its position along the DAMAGE -> HEALTH_GREEN ramp, so the row
                // reads as a green -> red gradient (green ticks appear only near full health).
                healthRampColor(index / (float) (segmentCount - 1), temporaryColor);
                if (lowHp && !isDead) temporaryColor.mul(pulse, pulse, pulse, 1f);
                shapes.setColor(temporaryColor);
            } else {
                shapes.setColor(ArtDirection.UI_INK_BLACK_RED, ArtDirection.UI_INK_BLACK_GREEN,
                        ArtDirection.UI_INK_BLACK_BLUE, 1f);
            }
            shapes.rect(HudConstants.HUD_BAR_X + index * (segmentWidth + gap), HudConstants.HUD_HP_BAR_Y,
                    segmentWidth, HudConstants.HUD_BAR_HEIGHT);
        }
    }

    private void drawArmorBarFilled(boolean isDead) {
        float fraction = isDead ? 0f : displayedArmorFraction;
        int   segmentCount = HudConstants.HUD_BAR_SEGMENT_COUNT;
        UiKit.meter(shapes, HudConstants.HUD_BAR_X, HudConstants.HUD_AR_BAR_Y,
                HudConstants.HUD_BAR_WIDTH, HudConstants.HUD_BAR_HEIGHT,
                wholeTickFraction(fraction, segmentCount), segmentCount,
                ArtDirection.ARMOUR_BLUE_RED, ArtDirection.ARMOUR_BLUE_GREEN, ArtDirection.ARMOUR_BLUE_BLUE,
                ArtDirection.UI_INK_BLACK_RED, ArtDirection.UI_INK_BLACK_GREEN, ArtDirection.UI_INK_BLACK_BLUE);
    }

    private void drawClipBarFilled(boolean isDead) {
        int maxSegments = HudConstants.HUD_BAR_SEGMENT_COUNT;

        // clipSize <= 0 is the sentinel for "melee selected — no ammo concept": every tick lit in SUIT_AMBER
        // (the player's own resource; green is reserved for health and buffs).
        if (hudState.clipSize <= 0) {
            UiKit.meter(shapes, HudConstants.HUD_BAR_X, HudConstants.HUD_CLIP_BAR_Y,
                    HudConstants.HUD_BAR_WIDTH, HudConstants.HUD_BAR_HEIGHT,
                    isDead ? 0f : 1f, maxSegments,
                    ArtDirection.SUIT_AMBER_RED, ArtDirection.SUIT_AMBER_GREEN, ArtDirection.SUIT_AMBER_BLUE,
                    ArtDirection.UI_INK_BLACK_RED, ArtDirection.UI_INK_BLACK_GREEN, ArtDirection.UI_INK_BLACK_BLUE);
            return;
        }

        int clipSize = Math.max(1, hudState.clipSize);
        int segmentCount;
        int fillCount;
        if (clipSize <= maxSegments) {
            // One tick (pip) per round in the clip.
            segmentCount = clipSize;
            fillCount    = isDead ? 0 : Math.min(hudState.currentAmmo, clipSize);
        } else {
            // Oversized magazine: fall back to the fixed tick count so segments stay legible.
            segmentCount   = maxSegments;
            float fraction = isDead ? 0f : hudState.currentAmmo / (float) clipSize;
            fillCount      = GameMath.segmentFillCount(fraction, segmentCount);
        }
        UiKit.meter(shapes, HudConstants.HUD_BAR_X, HudConstants.HUD_CLIP_BAR_Y,
                HudConstants.HUD_BAR_WIDTH, HudConstants.HUD_BAR_HEIGHT,
                fillCount / (float) segmentCount, segmentCount,
                ArtDirection.SUIT_AMBER_RED, ArtDirection.SUIT_AMBER_GREEN, ArtDirection.SUIT_AMBER_BLUE,
                ArtDirection.UI_INK_BLACK_RED, ArtDirection.UI_INK_BLACK_GREEN, ArtDirection.UI_INK_BLACK_BLUE);
    }

    /** XP is earned, so it is CREDITS_GOLD (AS4). */
    private void drawXpBarFilled(boolean isDead) {
        float fraction = isDead ? 0f : displayedXpFraction;
        int   segmentCount = HudConstants.HUD_BAR_SEGMENT_COUNT;
        UiKit.meter(shapes, HudConstants.HUD_BAR_X, HudConstants.HUD_XP_BAR_Y,
                HudConstants.HUD_BAR_WIDTH, HudConstants.HUD_BAR_HEIGHT,
                wholeTickFraction(fraction, segmentCount), segmentCount,
                ArtDirection.CREDITS_GOLD_RED, ArtDirection.CREDITS_GOLD_GREEN, ArtDirection.CREDITS_GOLD_BLUE,
                ArtDirection.UI_INK_BLACK_RED, ArtDirection.UI_INK_BLACK_GREEN, ArtDirection.UI_INK_BLACK_BLUE);
    }

    /**
     * Samples the health ramp CombatPalette.DAMAGE (0) -> HEALTH_RAMP_MIDDLE (0.5) -> ArtDirection.HEALTH_GREEN (1)
     * into {@code out}. Same two-segment shape as before; only the endpoints moved to the palette.
     */
    private static void healthRampColor(float fraction, Color out) {
        float clamped = MathUtils.clamp(fraction, 0f, 1f);
        if (clamped < 0.5f) {
            out.set(DAMAGE).lerp(HEALTH_RAMP_MIDDLE, clamped * 2f);
        } else {
            out.set(HEALTH_RAMP_MIDDLE).lerp(HEALTH_GREEN, (clamped - 0.5f) * 2f);
        }
    }

    // =========================================================================
    // PASS C: Vital labels + values
    // =========================================================================

    /** R9: labels UI_TEXT_SECONDARY, values UI_TEXT_PRIMARY; harm (low HP, an empty clip) in DAMAGE. */
    private void drawVitalsText(boolean lowHp, float pulse, boolean isDead) {
        Color labelColor = isDead ? TEXT_DIM : TEXT_SECONDARY;
        // HP
        drawLabel("HP", HudConstants.HUD_HP_BAR_Y, labelColor);
        if (lowHp && !isDead) {
            temporaryColor.set(DAMAGE).mul(pulse, pulse, pulse, 1f);
        } else if (isDead) {
            temporaryColor.set(TEXT_DIM);
        } else {
            temporaryColor.set(TEXT_PRIMARY);
        }
        stringBuilder.setLength(0);
        stringBuilder.append(player.getHealth());
        drawValueRight(stringBuilder, HudConstants.HUD_HP_BAR_Y, temporaryColor, HudConstants.HUD_HP_VALUE_SCALE);

        // AR
        drawLabel("AR", HudConstants.HUD_AR_BAR_Y, labelColor);
        stringBuilder.setLength(0);
        stringBuilder.append(player.getArmor());
        drawValueRight(stringBuilder, HudConstants.HUD_AR_BAR_Y,
                isDead ? TEXT_DIM : TEXT_PRIMARY, HudConstants.HUD_VALUE_SCALE);

        // AMMO / MELEE
        if (hudState.clipSize <= 0) {
            drawLabel("ML", HudConstants.HUD_CLIP_BAR_Y, labelColor);
            stringBuilder.setLength(0);
            stringBuilder.append("MELEE");
            drawValueRight(stringBuilder, HudConstants.HUD_CLIP_BAR_Y,
                    isDead ? TEXT_DIM : SUIT_AMBER, HudConstants.HUD_CLIP_VALUE_SCALE);
        } else {
            drawLabel("AM", HudConstants.HUD_CLIP_BAR_Y, labelColor);
            stringBuilder.setLength(0);
            stringBuilder.append(isDead ? 0 : hudState.currentAmmo);
            stringBuilder.append('/');
            stringBuilder.append(hudState.clipSize);
            if (!isDead && hudState.reserveAmmo >= 0) {
                stringBuilder.append(" [");
                stringBuilder.append(hudState.reserveAmmo);
                stringBuilder.append(']');
            }
            // No low-ammo threshold exists in the game, so the digits are primary until the clip is empty.
            Color ammoColor = isDead ? TEXT_DIM : (hudState.currentAmmo <= 0 ? DAMAGE : TEXT_PRIMARY);
            drawValueRight(ammoDigitsFont, stringBuilder, HudConstants.HUD_CLIP_BAR_Y,
                    ammoColor, HudConstants.HUD_CLIP_VALUE_SCALE);
        }

        // XP
        drawLabel("XP", HudConstants.HUD_XP_BAR_Y, labelColor);
        stringBuilder.setLength(0);
        stringBuilder.append("LV.");
        stringBuilder.append(hudState.playerLevel);
        drawValueRight(stringBuilder, HudConstants.HUD_XP_BAR_Y,
                isDead ? TEXT_DIM : TEXT_PRIMARY, HudConstants.HUD_VALUE_SCALE);
    }

    /** Left-aligned vital label, vertically centred on its bar, with a drop-shadow. */
    private void drawLabel(CharSequence text, float barY, Color color) {
        float baseline = barLabelBaseline(text, barY, HudConstants.HUD_LABEL_SCALE);
        drawTextWithShadow(text, HudConstants.HUD_ROW_LABEL_X, baseline, color, HudConstants.HUD_LABEL_SCALE);
    }

    /** Right-aligned vital value, vertically centred on its bar, with a drop-shadow. */
    private void drawValueRight(CharSequence text, float barY, Color color, float scale) {
        drawValueRight(font, text, barY, color, scale);
    }

    /** {@link #drawValueRight(CharSequence, float, Color, float)} in a given face. */
    private void drawValueRight(BitmapFont valueFont, CharSequence text, float barY, Color color, float scale) {
        float baseline = barLabelBaseline(text, barY, scale);
        valueFont.getData().setScale(scale);
        glyphLayout.setText(valueFont, text);
        float x = HudConstants.HUD_VALUE_RIGHT_X - glyphLayout.width;
        drawTextWithShadow(valueFont, text, x, baseline, color, scale);
    }

    /** Baseline that vertically centres {@code text} (at {@code scale}) on the bar at {@code barY}. */
    private float barLabelBaseline(CharSequence text, float barY, float scale) {
        font.getData().setScale(scale);
        glyphLayout.setText(font, text);
        return barY + (HudConstants.HUD_BAR_HEIGHT + glyphLayout.height) / 2f;
    }

    /** Shadowed text (UiKit R6) — a dark offset shadow then the foreground color — for legibility. */
    private void drawTextWithShadow(CharSequence text, float x, float baselineY, Color color, float scale) {
        drawTextWithShadow(font, text, x, baselineY, color, scale);
    }

    /** {@link #drawTextWithShadow(CharSequence, float, float, Color, float)} in a given face. */
    private void drawTextWithShadow(BitmapFont textFont, CharSequence text, float x, float baselineY,
                                    Color color, float scale) {
        textFont.getData().setScale(scale);
        UiKit.text(batch, textFont, text, x, baselineY, color.r, color.g, color.b, color.a);
    }

    // =========================================================================
    // Weapon slot strip — Pass A, B, C
    // =========================================================================

    private float slotWidth(int slotCount) {
        float padding = HudConstants.HUD_SLOT_SIDE_PADDING;
        float gap     = HudConstants.HUD_SLOT_GAP;
        return (LEFT_WIDTH - 2f * padding - (slotCount - 1) * gap) / slotCount;
    }

    private float slotX(int slotIndex, float slotBoxWidth) {
        return HudConstants.HUD_SLOT_SIDE_PADDING + slotIndex * (slotBoxWidth + HudConstants.HUD_SLOT_GAP);
    }

    // -------------------------------------------------------------------------
    // Display-slot model: slot 0 always shows the melee weapon; the remaining
    // display slots map, in order, onto the non-locked ranged loadout slots.
    // -------------------------------------------------------------------------

    /** Total slots shown in the strip: one melee slot plus every non-locked ranged loadout slot. */
    private int displaySlotCount(Loadout activeLoadout) {
        int count = 1; // melee slot
        for (int rangedSlot = 0; rangedSlot < activeLoadout.getSlotCount(); rangedSlot++) {
            if (!activeLoadout.isSlotLocked(rangedSlot)) count++;
        }
        return count;
    }

    /**
     * Maps a display index (>= 1) to the underlying ranged loadout slot index, skipping locked slots.
     * Returns -1 when the display index has no matching ranged slot.
     */
    private int rangedSlotForDisplay(Loadout activeLoadout, int displayIndex) {
        int rangedOrdinal = 0;
        for (int rangedSlot = 0; rangedSlot < activeLoadout.getSlotCount(); rangedSlot++) {
            if (activeLoadout.isSlotLocked(rangedSlot)) continue;
            rangedOrdinal++;
            if (rangedOrdinal == displayIndex) return rangedSlot;
        }
        return -1;
    }

    /** Weapon shown in the given display slot: melee for slot 0, otherwise the mapped ranged weapon. */
    private Weapon displaySlotWeapon(PlayerInventory inventory, int displayIndex) {
        if (displayIndex == 0) return inventory.getMeleeWeapon();
        int rangedSlot = rangedSlotForDisplay(inventory.getLoadout(), displayIndex);
        return rangedSlot < 0 ? null : inventory.getLoadout().getSlot(rangedSlot);
    }

    /** True when the given display slot holds the currently equipped weapon. */
    private boolean displaySlotActive(PlayerInventory inventory, int displayIndex) {
        if (displayIndex == 0) return inventory.isMeleeSelected();
        if (inventory.isMeleeSelected()) return false;
        int rangedSlot = rangedSlotForDisplay(inventory.getLoadout(), displayIndex);
        return rangedSlot >= 0 && inventory.getLoadout().getActiveSlotIndex() == rangedSlot;
    }

    private void drawSlotStripFilled(PlayerInventory inventory) {
        float originY       = HudConstants.HUD_SLOT_STRIP_Y;
        float slotBoxHeight = HudConstants.HUD_SLOT_STRIP_HEIGHT;
        int   slotCount     = displaySlotCount(inventory.getLoadout());
        float slotBoxWidth  = slotWidth(slotCount);

        for (int displayIndex = 0; displayIndex < slotCount; displayIndex++) {
            float   slotPositionX = slotX(displayIndex, slotBoxWidth);
            boolean filled        = displaySlotWeapon(inventory, displayIndex) != null;
            boolean isActive      = displaySlotActive(inventory, displayIndex) && filled;

            // R9: active = SELECTED plate in SUIT_AMBER, filled = IDLE, empty = DISABLED.
            PlateState state = isActive ? PlateState.SELECTED : (filled ? PlateState.IDLE : PlateState.DISABLED);
            UiKit.plate(shapes, slotPositionX, originY, slotBoxWidth, slotBoxHeight, ArtDirection.UI_CHAMFER,
                    ArtDirection.UI_BRACKET_LENGTH_COMPACT, state, HudConstants.HUD_SLOT_PLATE_ALPHA,
                    ArtDirection.SUIT_AMBER_RED, ArtDirection.SUIT_AMBER_GREEN, ArtDirection.SUIT_AMBER_BLUE);

            // Damage-class glyph (balance-overhaul order 3, C3): top row, right of the slot number, in the
            // class colour — the equipped slot at full strength, the carried ones dimmed.
            Weapon glyphWeapon = displaySlotWeapon(inventory, displayIndex);
            if (glyphWeapon != null && !player.isDead()) {
                DamageClass damageClass = glyphWeapon.damageClass();
                matchupGlyphs.drawClass(shapes, damageClass,
                        slotPositionX + HudConstants.HUD_CLASS_GLYPH_LEFT_INSET,
                        originY + slotBoxHeight - HudConstants.HUD_CLASS_GLYPH_TOP_INSET
                                - HudConstants.HUD_CLASS_GLYPH_SIZE,
                        HudConstants.HUD_CLASS_GLYPH_SIZE,
                        damageClass.colorRed(), damageClass.colorGreen(), damageClass.colorBlue(),
                        isActive ? 1f : HudConstants.HUD_CLASS_GLYPH_INACTIVE_ALPHA);
            }
        }
    }

    private void drawSlotStripLines(PlayerInventory inventory) {
        float originY       = HudConstants.HUD_SLOT_STRIP_Y;
        float slotBoxHeight = HudConstants.HUD_SLOT_STRIP_HEIGHT;
        int   slotCount     = displaySlotCount(inventory.getLoadout());
        float slotBoxWidth  = slotWidth(slotCount);

        for (int displayIndex = 0; displayIndex < slotCount; displayIndex++) {
            float   slotPositionX = slotX(displayIndex, slotBoxWidth);

            // LV tag arrow glyph (R14 a): right-hand end of the slot's top row, beside the tag text.
            Weapon weapon = displaySlotWeapon(inventory, displayIndex);
            if (weapon != null && !player.isDead()) {
                int standing = WeaponLevelTag.standing(weapon, hudState.threatLevel);
                if (WeaponLevelTag.hasGlyph(standing)) {
                    WeaponLevelTag.colorFor(standing, TEXT_PRIMARY, temporaryColor);
                    shapes.setColor(temporaryColor);
                    float glyphX = slotPositionX + slotBoxWidth - HudConstants.HUD_LEVEL_TAG_SLOT_INSET
                            - HudConstants.HUD_LEVEL_CHEVRON_WIDTH;
                    float glyphY = originY + slotBoxHeight - HudConstants.HUD_LEVEL_TAG_TOP_INSET
                            - HudConstants.HUD_LEVEL_CHEVRON_HEIGHT;
                    WeaponLevelTag.drawGlyph(shapes, glyphX, glyphY, standing);
                }
            }
        }
    }

    private void drawSlotStripText(PlayerInventory inventory, boolean isDead) {
        float originY       = HudConstants.HUD_SLOT_STRIP_Y;
        float slotBoxHeight = HudConstants.HUD_SLOT_STRIP_HEIGHT;
        int   slotCount     = displaySlotCount(inventory.getLoadout());
        float slotBoxWidth  = slotWidth(slotCount);

        for (int displayIndex = 0; displayIndex < slotCount; displayIndex++) {
            float   slotPositionX = slotX(displayIndex, slotBoxWidth);
            Weapon  weapon        = displaySlotWeapon(inventory, displayIndex);
            boolean filled        = weapon != null;
            boolean isActive      = displaySlotActive(inventory, displayIndex) && filled;

            // Slot number, top-left corner of the slot.
            stringBuilder.setLength(0);
            stringBuilder.append(displayIndex + 1);
            Color numberColor = isDead ? TEXT_DIM : (isActive ? SUIT_AMBER : TEXT_SECONDARY);
            drawTextWithShadow(stringBuilder, slotPositionX + HudConstants.HUD_SLOT_NUMBER_INSET_X,
                    originY + slotBoxHeight - HudConstants.HUD_SLOT_NUMBER_TOP_INSET,
                    numberColor, HudConstants.HUD_SLOT_NUMBER_SCALE);

            // Weapon name, truncated to fit the slot, vertically centred under the number.
            if (filled && !isDead) {
                appendTruncatedName(weapon.getDisplayName(),
                        slotBoxWidth - 2f * HudConstants.HUD_SLOT_NAME_SIDE_INSET, HudConstants.HUD_SLOT_NAME_SCALE);
                font.getData().setScale(HudConstants.HUD_SLOT_NAME_SCALE);
                glyphLayout.setText(font, stringBuilder);
                float nameX = slotPositionX + (slotBoxWidth - glyphLayout.width) / 2f;
                float nameY = originY + HudConstants.HUD_SLOT_NAME_TOP_Y;
                drawTextWithShadow(stringBuilder, nameX, nameY,
                        isActive ? SUIT_AMBER : TEXT_PRIMARY, HudConstants.HUD_SLOT_NAME_SCALE);
            }

            // "LV n" tag, top-right of the slot, coloured by the gap to the floor threat level.
            if (filled && !isDead) {
                int standing = WeaponLevelTag.standing(weapon, hudState.threatLevel);
                WeaponLevelTag.colorFor(standing, TEXT_PRIMARY, temporaryColor);
                String tagLabel = WeaponLevelTag.label(weapon.getWeaponLevel());
                font.getData().setScale(HudConstants.HUD_LEVEL_TAG_SCALE);
                glyphLayout.setText(font, tagLabel);
                float rightEdge = slotPositionX + slotBoxWidth - HudConstants.HUD_LEVEL_TAG_SLOT_INSET;
                if (WeaponLevelTag.hasGlyph(standing)) {
                    rightEdge -= HudConstants.HUD_LEVEL_CHEVRON_WIDTH + HudConstants.HUD_LEVEL_TAG_CHEVRON_GAP;
                }
                drawTextWithShadow(tagLabel, rightEdge - glyphLayout.width,
                        originY + slotBoxHeight - HudConstants.HUD_LEVEL_TAG_TOP_INSET,
                        temporaryColor, HudConstants.HUD_LEVEL_TAG_SCALE);
            }
        }
        font.getData().setScale(HudConstants.HUD_LABEL_SCALE);
    }

    /**
     * Writes {@code name} into stringBuilder, dropping trailing characters (and adding a '.') until
     * it measures no wider than {@code maxWidth} at {@code scale}. Guarantees the slot name never
     * overflows its box, so names can never overlap neighbouring slots or the bars above.
     */
    private void appendTruncatedName(String name, float maxWidth, float scale) {
        font.getData().setScale(scale);
        stringBuilder.setLength(0);
        stringBuilder.append(name);
        glyphLayout.setText(font, stringBuilder);
        if (glyphLayout.width <= maxWidth) return;
        while (stringBuilder.length() > 1) {
            stringBuilder.setLength(stringBuilder.length() - 1);
            glyphLayout.setText(font, stringBuilder);
            if (glyphLayout.width <= maxWidth) break;
        }
        if (stringBuilder.length() > 1) {
            stringBuilder.setLength(stringBuilder.length() - 1);
            stringBuilder.append('.');
        }
    }

    // =========================================================================
    // Status icon row
    // =========================================================================

    private void drawStatusIconsFilled() {
        float iconY = HudConstants.HUD_STATUS_ROW_LOCAL_Y;
        float size  = HudConstants.HUD_STATUS_ICON_SIZE;
        float gap   = HudConstants.HUD_STATUS_ICON_GAP;
        int activeCount = 0;
        for (int typeIndex = 0; typeIndex < STATUS_TYPES.length; typeIndex++) {
            StatusEffect effect = player.getActiveEffects().get(STATUS_TYPES[typeIndex]);
            if (effect == null || !effect.isActive()) continue;
            float drawX = HudConstants.HUD_STATUS_ROW_LOCAL_X + activeCount * (size + gap);
            shapes.setColor(STATUS_ICON_RED[typeIndex], STATUS_ICON_GREEN[typeIndex],
                            STATUS_ICON_BLUE[typeIndex], 1f);
            shapes.rect(drawX, iconY, size, size);
            activeCount++;
        }
    }

    private void drawStatusIconsText() {
        float iconY = HudConstants.HUD_STATUS_ROW_LOCAL_Y;
        float size  = HudConstants.HUD_STATUS_ICON_SIZE;
        float gap   = HudConstants.HUD_STATUS_ICON_GAP;
        int activeCount = 0;
        font.getData().setScale(HudConstants.HUD_STATUS_COUNT_SCALE);
        for (int typeIndex = 0; typeIndex < STATUS_TYPES.length; typeIndex++) {
            StatusEffect effect = player.getActiveEffects().get(STATUS_TYPES[typeIndex]);
            if (effect == null || !effect.isActive()) continue;
            float drawX = HudConstants.HUD_STATUS_ROW_LOCAL_X + activeCount * (size + gap);
            stringBuilder.setLength(0);
            if (STATUS_TYPES[typeIndex] == StatusType.POISONED) {
                // Poison decays one STACK per turn (order 4, H10), so its turns remaining always equal its
                // stack count: the icon shows the stacks ("x3"), which is also the turns left.
                stringBuilder.append('x').append(effect.getStacks());
            } else {
                stringBuilder.append(effect.getRemainingTurns());
            }
            UiKit.text(batch, font, stringBuilder, drawX + HudConstants.HUD_STATUS_COUNT_INSET,
                    iconY + size - HudConstants.HUD_STATUS_COUNT_INSET,
                    ArtDirection.UI_TEXT_PRIMARY_RED, ArtDirection.UI_TEXT_PRIMARY_GREEN, ArtDirection.UI_TEXT_PRIMARY_BLUE, 1f);
            activeCount++;
        }
        font.getData().setScale(HudConstants.HUD_LABEL_SCALE);
    }

    // =========================================================================
    // Centre-gap overlays (outside the panel)
    // =========================================================================

    private void drawGroundWeaponLabel(String label) {
        float centreX = Constants.WORLD_WIDTH / 2f; // HUD gap spans X 420..860

        font.getData().setScale(HudConstants.HUD_GROUND_WEAPON_LABEL_SCALE);
        glyphLayout.setText(font, label);
        UiKit.text(batch, font, label, centreX - glyphLayout.width / 2f,
                HudConstants.WEAPON_NAME_LABEL_Y + glyphLayout.height + font.getLineHeight() * 0.3f,
                SUIT_AMBER.r, SUIT_AMBER.g, SUIT_AMBER.b, 1f);

        float subPulse = 0.5f + 0.5f * MathUtils.sin(animationClockSeconds * MathUtils.PI2 * 1.5f);
        font.getData().setScale(HudConstants.HUD_GROUND_WEAPON_HINT_SCALE);
        String subLabel = "[ INSPECT ]";
        glyphLayout.setText(font, subLabel);
        UiKit.text(batch, font, subLabel, centreX - glyphLayout.width / 2f,
                HudConstants.WEAPON_NAME_LABEL_Y + glyphLayout.height,
                SUIT_AMBER.r * subPulse, SUIT_AMBER.g * subPulse, SUIT_AMBER.b * subPulse, 1f);

        font.getData().setScale(HudConstants.HUD_LABEL_SCALE);
    }

    private void drawMedkitWarning(float pulse) {
        float centreX = Constants.WORLD_WIDTH / 2f;
        font.getData().setScale(HudConstants.HUD_MEDKIT_WARN_SCALE);
        temporaryColor.set(DAMAGE).lerp(SUIT_AMBER, pulse);
        glyphLayout.setText(font, MEDKIT_WARN_TEXT);
        UiKit.text(batch, font, MEDKIT_WARN_TEXT, centreX - glyphLayout.width / 2f, HudConstants.HUD_MEDKIT_WARN_Y,
                temporaryColor.r, temporaryColor.g, temporaryColor.b, 1f);
        font.getData().setScale(HudConstants.HUD_LABEL_SCALE);
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
        ammoDigitsFont.dispose();
    }
}
