package ge.tbegvadze.toon3d.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.entity.Loadout;
import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.render.Renderable;
import ge.tbegvadze.toon3d.status.StatusEffect;
import ge.tbegvadze.toon3d.status.StatusType;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.HudConstants;
import ge.tbegvadze.toon3d.world.HudState;

/**
 * Left chrome HUD panel only (x 0..420, y 0..206 — bottom-left corner).
 *
 * Reworked vitals layout: four "vital rows" (HP, AR, AMMO, XP) stacked top-to-bottom, each one a
 * left-aligned label, a smooth per-vertex gradient bar, and a right-aligned numeric value. Labels
 * sit to the LEFT of every bar and values to the RIGHT, so text is never drawn over a bar and the
 * elements can never overlap. The HP bar is a true green→red gradient: a full-width red→green
 * "scale" track with a bright filled slice on top, so the player reads health by both length and
 * hue (green = healthy, red = critical). Below the bars sit a numbered weapon-slot strip and a thin
 * status-effect icon row in its own clear band.
 *
 * Every glyph is drawn with a dark drop-shadow for contrast against the busy 3D view behind it.
 *
 * Render order per frame:
 *   Pass A — ShapeRenderer.Filled  (panel chrome, gradient bars, slots — alpha-blended)
 *   Pass B — ShapeRenderer.Line    (bevels, bar/slot outlines)
 *   Pass C — SpriteBatch + BitmapFont (labels, values, slot text, status counts)
 */
public class HudRenderer implements Renderable, Disposable {

    // -------------------------------------------------------------------------
    // Palette
    // -------------------------------------------------------------------------
    private static final Color STEEL_DARK     = new Color(0.102f, 0.102f, 0.122f, 1f);
    private static final Color STEEL_BLACK    = new Color(0.055f, 0.055f, 0.071f, 1f);
    private static final Color BEVEL_LIGHT    = new Color(0.290f, 0.290f, 0.333f, 1f);
    private static final Color BEVEL_DARK     = new Color(0.031f, 0.031f, 0.039f, 1f);
    private static final Color BAR_BORDER     = new Color(0.000f, 0.000f, 0.000f, 0.55f);

    private static final Color TEXT_PRIMARY   = new Color(0.93f, 0.96f, 0.98f, 1f);
    private static final Color TEXT_DIM       = new Color(0.45f, 0.48f, 0.52f, 1f);
    private static final Color TEXT_SHADOW    = new Color(0.00f, 0.00f, 0.00f, 0.80f);

    // HP green→red ramp endpoints (sampled by healthRampColor)
    private static final Color HP_LABEL       = new Color(0.55f, 0.95f, 0.60f, 1f);
    private static final Color HP_TRACK_LOW   = new Color(0.18f, 0.03f, 0.03f, 1f);
    private static final Color HP_TRACK_HIGH  = new Color(0.04f, 0.16f, 0.05f, 1f);

    // Armor (blue), ammo (amber), XP (gold) — single-hue glossy gradients
    private static final Color AR_LABEL       = new Color(0.55f, 0.85f, 1.00f, 1f);
    private static final Color AR_BRIGHT      = new Color(0.20f, 0.78f, 0.95f, 1f);
    private static final Color AR_TRACK       = new Color(0.04f, 0.11f, 0.15f, 1f);

    private static final Color CLIP_LABEL     = new Color(1.00f, 0.90f, 0.35f, 1f);
    private static final Color CLIP_BRIGHT    = new Color(1.00f, 0.78f, 0.05f, 1f);
    private static final Color CLIP_TRACK     = new Color(0.16f, 0.12f, 0.02f, 1f);
    private static final Color MELEE_BRIGHT   = new Color(0.25f, 0.85f, 0.35f, 1f);
    private static final Color MELEE_TRACK    = new Color(0.04f, 0.14f, 0.05f, 1f);

    private static final Color XP_LABEL       = new Color(1.00f, 0.85f, 0.45f, 1f);
    private static final Color XP_BRIGHT      = new Color(1.00f, 0.80f, 0.10f, 1f);
    private static final Color XP_TRACK       = new Color(0.14f, 0.10f, 0.01f, 1f);

    private static final Color WARN_YELLOW    = new Color(1.000f, 0.878f, 0.000f, 1f);
    private static final Color ALERT_RED      = new Color(1.000f, 0.165f, 0.165f, 1f);
    private static final Color LED_GREEN      = new Color(0.188f, 1.000f, 0.376f, 1f);
    private static final Color HP_RED         = new Color(0.95f, 0.20f, 0.20f, 1f);

    // Weapon slot strip
    private static final Color SLOT_ACTIVE    = new Color(1.000f, 0.720f, 0.000f, 1f);
    private static final Color SLOT_FILLED    = new Color(0.230f, 0.190f, 0.060f, 0.92f);
    private static final Color SLOT_EMPTY     = new Color(0.060f, 0.060f, 0.080f, 0.85f);
    private static final Color SLOT_BORDER    = new Color(0.220f, 0.220f, 0.260f, 1f);

    // Status icon colors — indexed by StatusType ordinal
    // BURNING=0 POISONED=1 BLEED=2 STUNNED=3 BLINDED=4 SLOWED=5 EMPOWERED=6
    private static final float[] STATUS_ICON_RED   = { 1.00f, 0.00f, 0.85f, 1.00f, 0.30f, 0.25f, 0.85f };
    private static final float[] STATUS_ICON_GREEN = { 0.45f, 0.80f, 0.05f, 1.00f, 0.00f, 0.40f, 0.10f };
    private static final float[] STATUS_ICON_BLUE  = { 0.00f, 0.15f, 0.10f, 1.00f, 0.50f, 0.70f, 0.00f };
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

    // -------------------------------------------------------------------------
    // Inputs
    // -------------------------------------------------------------------------
    private final Player   player;
    private final HudState hudState;
    private       Loadout  loadout = null;

    // -------------------------------------------------------------------------
    // Animation state
    // -------------------------------------------------------------------------
    private float displayedHealthFraction = 1f;
    private float displayedArmorFraction  = 0f;
    private float displayedXpFraction     = 0f;
    private float animationClockSeconds   = 0f;

    private String groundWeaponLabel = null;

    // Reusable scratch — no allocations inside render()
    private final StringBuilder stringBuilder = new StringBuilder(64);
    private final GlyphLayout   glyphLayout   = new GlyphLayout();
    private final Color cornerBottomLeft  = new Color();
    private final Color cornerBottomRight = new Color();
    private final Color cornerTopRight    = new Color();
    private final Color cornerTopLeft     = new Color();
    private final Color rampColor         = new Color();
    private final Color temporaryColor    = new Color();

    public HudRenderer(Player player, HudState hudState) {
        this.player   = player;
        this.hudState = hudState;
        this.shapes   = new ShapeRenderer();
        this.batch    = new SpriteBatch();
        this.font     = new BitmapFont();
        this.font.getData().markupEnabled = false;
        // Linear filtering keeps the scaled bitmap font from looking jagged.
        this.font.getRegion().getTexture().setFilter(
                Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    }

    /** Provides the loadout whose slots are drawn in the left-panel strip. */
    public void setLoadout(Loadout newLoadout) {
        this.loadout = newLoadout;
    }

    /**
     * Sets the weapon name shown in the HUD centre gap when the player stands on a weapon tile.
     * Pass null to hide the label (player stepped off or weapon was taken).
     */
    public void setGroundWeaponLabel(String label) {
        this.groundWeaponLabel = label;
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
        if (loadout != null) drawSlotStripFilled(loadout, pulse);
        if (!isDead) drawStatusIconsFilled();
        shapes.end();

        // ---- Pass B: Line ----
        shapes.begin(ShapeRenderer.ShapeType.Line);
        drawPanelChromeLines(0f, 0f, LEFT_WIDTH, PANEL_HEIGHT);
        drawBarBorders();
        if (loadout != null) drawSlotStripLines(loadout);
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // ---- Pass C: Text ----
        batch.begin();
        drawVitalsText(lowHp, pulse, isDead);
        if (loadout != null) drawSlotStripText(loadout, isDead);
        if (!isDead) drawStatusIconsText();
        if (groundWeaponLabel != null && !isDead) drawGroundWeaponLabel(groundWeaponLabel);
        boolean medkitWarn = !isDead
                && hudState.medicalCharges > 0
                && player.getHealthFraction() <= HudConstants.HUD_MEDKIT_WARN_HP_THRESHOLD;
        if (medkitWarn) drawMedkitWarning(pulse);
        batch.end();
    }

    // =========================================================================
    // PASS A: Panel chrome
    // =========================================================================

    private void drawPanelChromeFilled(float panelX, float panelY, float panelW, float panelH,
                                        boolean alert, float pulse) {
        float inset = HudConstants.HUD_PANEL_INSET;
        float alpha = HudConstants.HUD_PANEL_ALPHA;

        shapes.setColor(STEEL_DARK.r, STEEL_DARK.g, STEEL_DARK.b, alpha);
        shapes.rect(panelX, panelY, panelW, panelH);
        shapes.setColor(STEEL_BLACK.r, STEEL_BLACK.g, STEEL_BLACK.b, alpha);
        shapes.rect(panelX + inset, panelY + inset, panelW - inset * 2f, panelH - inset * 2f);

        shapes.setColor(BEVEL_LIGHT);
        float rivetRadius = HudConstants.HUD_RIVET_RADIUS;
        float rivetOffset = 8f;
        shapes.circle(panelX + rivetOffset,          panelY + rivetOffset,          rivetRadius, 8);
        shapes.circle(panelX + panelW - rivetOffset, panelY + rivetOffset,          rivetRadius, 8);
        shapes.circle(panelX + rivetOffset,          panelY + panelH - rivetOffset, rivetRadius, 8);
        shapes.circle(panelX + panelW - rivetOffset, panelY + panelH - rivetOffset, rivetRadius, 8);

        float ledRadius = HudConstants.HUD_LED_RADIUS;
        float ledX      = panelX + panelW - 14f;
        float ledY      = panelY + panelH - 14f;
        if (alert) {
            temporaryColor.set(ALERT_RED).mul(pulse, pulse, pulse, 1f);
            shapes.setColor(temporaryColor);
        } else {
            shapes.setColor(LED_GREEN);
        }
        shapes.circle(ledX, ledY, ledRadius, 8);
    }

    private void drawPanelChromeLines(float panelX, float panelY, float panelW, float panelH) {
        float inset = HudConstants.HUD_PANEL_INSET;
        shapes.setColor(BEVEL_LIGHT);
        shapes.line(panelX,          panelY + panelH, panelX + panelW, panelY + panelH);
        shapes.line(panelX,          panelY,          panelX,          panelY + panelH);
        shapes.setColor(BEVEL_DARK);
        shapes.line(panelX,          panelY,          panelX + panelW, panelY);
        shapes.line(panelX + panelW, panelY,          panelX + panelW, panelY + panelH);
        float innerX      = panelX + inset;
        float innerY      = panelY + inset;
        float innerWidth  = panelW - inset * 2f;
        float innerHeight = panelH - inset * 2f;
        shapes.setColor(BEVEL_DARK);
        shapes.line(innerX,              innerY + innerHeight, innerX + innerWidth, innerY + innerHeight);
        shapes.line(innerX,              innerY,               innerX,              innerY + innerHeight);
        shapes.setColor(BEVEL_LIGHT);
        shapes.line(innerX,              innerY,               innerX + innerWidth, innerY);
        shapes.line(innerX + innerWidth, innerY,               innerX + innerWidth, innerY + innerHeight);
        float rivetRadius = HudConstants.HUD_RIVET_RADIUS;
        float rivetOffset = 8f;
        shapes.setColor(BEVEL_DARK);
        shapes.circle(panelX + rivetOffset,          panelY + rivetOffset,          rivetRadius, 8);
        shapes.circle(panelX + panelW - rivetOffset, panelY + rivetOffset,          rivetRadius, 8);
        shapes.circle(panelX + rivetOffset,          panelY + panelH - rivetOffset, rivetRadius, 8);
        shapes.circle(panelX + panelW - rivetOffset, panelY + panelH - rivetOffset, rivetRadius, 8);
    }

    // =========================================================================
    // PASS A: Gradient vital bars
    // =========================================================================

    /**
     * Draws one vital bar: a full-width dim "track" (the green→red scale, or a single dim hue),
     * then a bright filled slice from the left edge up to {@code fraction} of the width. Both layers
     * carry a top-to-bottom gloss (top lighter than bottom). All four corner colors come from the
     * pre-allocated scratch Colors so render() never allocates.
     */
    private void drawGradientBar(float barY, float fraction,
                                 Color trackLow, Color trackHigh,
                                 Color fillLeft, Color fillRight) {
        float barX      = HudConstants.HUD_BAR_X;
        float barWidth  = HudConstants.HUD_BAR_WIDTH;
        float barHeight = HudConstants.HUD_BAR_HEIGHT;
        float clamped   = MathUtils.clamp(fraction, 0f, 1f);

        // Track (full width). Bottom corners are darkened for a recessed look.
        setCorners(trackLow, trackHigh, 0.55f);
        shapes.rect(barX, barY, barWidth, barHeight,
                cornerBottomLeft, cornerBottomRight, cornerTopRight, cornerTopLeft);

        // Bright filled slice.
        float fillWidth = barWidth * clamped;
        if (fillWidth > 0.5f) {
            setCorners(fillLeft, fillRight, 0.62f);
            shapes.rect(barX, barY, fillWidth, barHeight,
                    cornerBottomLeft, cornerBottomRight, cornerTopRight, cornerTopLeft);
        }
    }

    /**
     * Loads the four corner scratch colors for a glossy left→right gradient: top corners use the
     * left/right hue at full brightness, bottom corners the same hue multiplied by {@code bottomMul}.
     * rect() corner order is (bottom-left, bottom-right, top-right, top-left).
     */
    private void setCorners(Color left, Color right, float bottomMul) {
        cornerTopLeft.set(left);
        cornerTopRight.set(right);
        cornerBottomLeft.set(left.r * bottomMul, left.g * bottomMul, left.b * bottomMul, left.a);
        cornerBottomRight.set(right.r * bottomMul, right.g * bottomMul, right.b * bottomMul, right.a);
    }

    private void drawHpBarFilled(float pulse, boolean lowHp, boolean isDead) {
        float fraction = isDead ? 0f : displayedHealthFraction;
        // Bright slice runs red (left edge) → the ramp color at the current fill edge (right), so the
        // player only sees green when near full health — a literal green→red gradient. temporaryColor
        // holds the left (red) end, rampColor the right end; both are reusable scratch Colors.
        healthRampColor(0f, temporaryColor);
        healthRampColor(fraction, rampColor);
        if (lowHp && !isDead) {
            temporaryColor.mul(pulse, pulse, pulse, 1f);
            rampColor.mul(pulse, pulse, pulse, 1f);
        }
        drawGradientBar(HudConstants.HUD_HP_BAR_Y, fraction,
                HP_TRACK_LOW, HP_TRACK_HIGH, temporaryColor, rampColor);
    }

    private void drawArmorBarFilled(boolean isDead) {
        float fraction = isDead ? 0f : displayedArmorFraction;
        drawGradientBar(HudConstants.HUD_AR_BAR_Y, fraction, AR_TRACK, AR_TRACK, AR_BRIGHT, AR_BRIGHT);
    }

    private void drawClipBarFilled(boolean isDead) {
        // clipSize <= 0 is the sentinel for "melee selected — no ammo concept": show a full green bar.
        if (hudState.clipSize <= 0) {
            float meleeFraction = isDead ? 0f : 1f;
            drawGradientBar(HudConstants.HUD_CLIP_BAR_Y, meleeFraction,
                    MELEE_TRACK, MELEE_TRACK, MELEE_BRIGHT, MELEE_BRIGHT);
            return;
        }
        int   clipSize = Math.max(1, hudState.clipSize);
        float fraction = isDead ? 0f : MathUtils.clamp(hudState.currentAmmo / (float) clipSize, 0f, 1f);
        drawGradientBar(HudConstants.HUD_CLIP_BAR_Y, fraction, CLIP_TRACK, CLIP_TRACK, CLIP_BRIGHT, CLIP_BRIGHT);
    }

    private void drawXpBarFilled(boolean isDead) {
        float fraction = isDead ? 0f : displayedXpFraction;
        drawGradientBar(HudConstants.HUD_XP_BAR_Y, fraction, XP_TRACK, XP_TRACK, XP_BRIGHT, XP_BRIGHT);
    }

    /** Thin dark outline around every vital bar so each reads as a distinct, crisp element. */
    private void drawBarBorders() {
        shapes.setColor(BAR_BORDER);
        barBorder(HudConstants.HUD_HP_BAR_Y);
        barBorder(HudConstants.HUD_AR_BAR_Y);
        barBorder(HudConstants.HUD_CLIP_BAR_Y);
        barBorder(HudConstants.HUD_XP_BAR_Y);
    }

    private void barBorder(float barY) {
        shapes.rect(HudConstants.HUD_BAR_X, barY, HudConstants.HUD_BAR_WIDTH, HudConstants.HUD_BAR_HEIGHT);
    }

    /**
     * Samples the health color ramp red(0) → yellow(0.5) → green(1) into {@code out}.
     * (Channel ramp kept local to the renderer to avoid allocating a Color in GameMath.)
     */
    private static void healthRampColor(float fraction, Color out) {
        float clamped = MathUtils.clamp(fraction, 0f, 1f);
        float red, green;
        if (clamped < 0.5f) {
            red   = 1f;
            green = clamped * 2f;          // 0 → 1 over [0, 0.5]
        } else {
            red   = (1f - clamped) * 2f;   // 1 → 0 over [0.5, 1]
            green = 1f;
        }
        out.set(red, green, 0.10f, 1f);
    }

    // =========================================================================
    // PASS C: Vital labels + values
    // =========================================================================

    private void drawVitalsText(boolean lowHp, float pulse, boolean isDead) {
        // HP
        drawLabel("HP", HudConstants.HUD_HP_BAR_Y, isDead ? TEXT_DIM : HP_LABEL);
        if (lowHp && !isDead) {
            temporaryColor.set(HP_RED).mul(pulse, pulse, pulse, 1f);
        } else if (isDead) {
            temporaryColor.set(TEXT_DIM);
        } else {
            healthRampColor(player.getHealthFraction(), temporaryColor);
        }
        stringBuilder.setLength(0);
        stringBuilder.append(player.getHealth());
        drawValueRight(stringBuilder, HudConstants.HUD_HP_BAR_Y, temporaryColor, HudConstants.HUD_HP_VALUE_SCALE);

        // AR
        drawLabel("AR", HudConstants.HUD_AR_BAR_Y, isDead ? TEXT_DIM : AR_LABEL);
        stringBuilder.setLength(0);
        stringBuilder.append(player.getArmor());
        drawValueRight(stringBuilder, HudConstants.HUD_AR_BAR_Y,
                isDead ? TEXT_DIM : TEXT_PRIMARY, HudConstants.HUD_VALUE_SCALE);

        // AMMO / MELEE
        if (hudState.clipSize <= 0) {
            drawLabel("ML", HudConstants.HUD_CLIP_BAR_Y, isDead ? TEXT_DIM : MELEE_BRIGHT);
            stringBuilder.setLength(0);
            stringBuilder.append("MELEE");
            drawValueRight(stringBuilder, HudConstants.HUD_CLIP_BAR_Y,
                    isDead ? TEXT_DIM : MELEE_BRIGHT, HudConstants.HUD_CLIP_VALUE_SCALE);
        } else {
            drawLabel("AM", HudConstants.HUD_CLIP_BAR_Y, isDead ? TEXT_DIM : CLIP_LABEL);
            stringBuilder.setLength(0);
            stringBuilder.append(isDead ? 0 : hudState.currentAmmo);
            stringBuilder.append('/');
            stringBuilder.append(hudState.clipSize);
            if (!isDead && hudState.reserveAmmo >= 0) {
                stringBuilder.append(" [");
                stringBuilder.append(hudState.reserveAmmo);
                stringBuilder.append(']');
            }
            drawValueRight(stringBuilder, HudConstants.HUD_CLIP_BAR_Y,
                    isDead ? TEXT_DIM : TEXT_PRIMARY, HudConstants.HUD_CLIP_VALUE_SCALE);
        }

        // XP
        drawLabel("XP", HudConstants.HUD_XP_BAR_Y, isDead ? TEXT_DIM : XP_LABEL);
        stringBuilder.setLength(0);
        stringBuilder.append("LV.");
        stringBuilder.append(hudState.playerLevel);
        drawValueRight(stringBuilder, HudConstants.HUD_XP_BAR_Y,
                isDead ? TEXT_DIM : XP_LABEL, HudConstants.HUD_VALUE_SCALE);
    }

    /** Left-aligned vital label, vertically centred on its bar, with a drop-shadow. */
    private void drawLabel(CharSequence text, float barY, Color color) {
        float baseline = barLabelBaseline(text, barY, HudConstants.HUD_LABEL_SCALE);
        drawTextWithShadow(text, HudConstants.HUD_ROW_LABEL_X, baseline, color, HudConstants.HUD_LABEL_SCALE);
    }

    /** Right-aligned vital value, vertically centred on its bar, with a drop-shadow. */
    private void drawValueRight(CharSequence text, float barY, Color color, float scale) {
        float baseline = barLabelBaseline(text, barY, scale);
        font.getData().setScale(scale);
        glyphLayout.setText(font, text);
        float x = HudConstants.HUD_VALUE_RIGHT_X - glyphLayout.width;
        drawTextWithShadow(text, x, baseline, color, scale);
    }

    /** Baseline that vertically centres {@code text} (at {@code scale}) on the bar at {@code barY}. */
    private float barLabelBaseline(CharSequence text, float barY, float scale) {
        font.getData().setScale(scale);
        glyphLayout.setText(font, text);
        return barY + (HudConstants.HUD_BAR_HEIGHT + glyphLayout.height) / 2f;
    }

    /** Draws text twice — a dark offset shadow then the foreground color — for legibility. */
    private void drawTextWithShadow(CharSequence text, float x, float baselineY, Color color, float scale) {
        float offset = HudConstants.HUD_TEXT_SHADOW_OFFSET;
        font.getData().setScale(scale);
        font.setColor(TEXT_SHADOW);
        font.draw(batch, text, x + offset, baselineY - offset);
        font.setColor(color);
        font.draw(batch, text, x, baselineY);
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

    private void drawSlotStripFilled(Loadout activeLoadout, float pulse) {
        float originY       = HudConstants.HUD_SLOT_STRIP_Y;
        float slotBoxHeight = HudConstants.HUD_SLOT_STRIP_HEIGHT;
        int   slotCount     = activeLoadout.getSlotCount();
        float slotBoxWidth  = slotWidth(slotCount);
        int   activeIndex   = activeLoadout.getActiveSlotIndex();

        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            float   slotPositionX = slotX(slotIndex, slotBoxWidth);
            boolean filled        = activeLoadout.getSlot(slotIndex) != null;
            boolean isActive      = slotIndex == activeIndex && filled;

            if (isActive) {
                float brightness = 0.35f + 0.20f * pulse;
                shapes.setColor(SLOT_ACTIVE.r * brightness, SLOT_ACTIVE.g * brightness,
                                SLOT_ACTIVE.b * brightness, 0.95f);
            } else if (filled) {
                shapes.setColor(SLOT_FILLED);
            } else {
                shapes.setColor(SLOT_EMPTY);
            }
            shapes.rect(slotPositionX, originY, slotBoxWidth, slotBoxHeight);
        }
    }

    private void drawSlotStripLines(Loadout activeLoadout) {
        float originY       = HudConstants.HUD_SLOT_STRIP_Y;
        float slotBoxHeight = HudConstants.HUD_SLOT_STRIP_HEIGHT;
        int   slotCount     = activeLoadout.getSlotCount();
        float slotBoxWidth  = slotWidth(slotCount);
        int   activeIndex   = activeLoadout.getActiveSlotIndex();

        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            float   slotPositionX = slotX(slotIndex, slotBoxWidth);
            boolean filled        = activeLoadout.getSlot(slotIndex) != null;
            boolean isActive      = slotIndex == activeIndex && filled;
            shapes.setColor(isActive ? SLOT_ACTIVE : SLOT_BORDER);
            shapes.rect(slotPositionX, originY, slotBoxWidth, slotBoxHeight);
        }
    }

    private void drawSlotStripText(Loadout activeLoadout, boolean isDead) {
        float originY       = HudConstants.HUD_SLOT_STRIP_Y;
        float slotBoxHeight = HudConstants.HUD_SLOT_STRIP_HEIGHT;
        int   slotCount     = activeLoadout.getSlotCount();
        float slotBoxWidth  = slotWidth(slotCount);
        int   activeIndex   = activeLoadout.getActiveSlotIndex();

        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            float   slotPositionX = slotX(slotIndex, slotBoxWidth);
            boolean filled        = activeLoadout.getSlot(slotIndex) != null;
            boolean isActive      = slotIndex == activeIndex && filled;

            // Slot number, top-left corner of the slot.
            stringBuilder.setLength(0);
            stringBuilder.append(slotIndex + 1);
            Color numberColor = isDead ? TEXT_DIM : (isActive ? SLOT_ACTIVE : TEXT_DIM);
            drawTextWithShadow(stringBuilder, slotPositionX + 4f, originY + slotBoxHeight - 3f,
                    numberColor, HudConstants.HUD_SLOT_NUMBER_SCALE);

            // Weapon name, truncated to fit the slot, vertically centred under the number.
            if (filled && !isDead) {
                appendTruncatedName(activeLoadout.getSlot(slotIndex).getDisplayName(),
                        slotBoxWidth - 8f, HudConstants.HUD_SLOT_NAME_SCALE);
                font.getData().setScale(HudConstants.HUD_SLOT_NAME_SCALE);
                glyphLayout.setText(font, stringBuilder);
                float nameX = slotPositionX + (slotBoxWidth - glyphLayout.width) / 2f;
                float nameY = originY + slotBoxHeight * 0.42f;
                drawTextWithShadow(stringBuilder, nameX, nameY,
                        isActive ? SLOT_ACTIVE : TEXT_PRIMARY, HudConstants.HUD_SLOT_NAME_SCALE);
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
        font.getData().setScale(0.6f);
        font.setColor(Color.WHITE);
        for (int typeIndex = 0; typeIndex < STATUS_TYPES.length; typeIndex++) {
            StatusEffect effect = player.getActiveEffects().get(STATUS_TYPES[typeIndex]);
            if (effect == null || !effect.isActive()) continue;
            float drawX = HudConstants.HUD_STATUS_ROW_LOCAL_X + activeCount * (size + gap);
            stringBuilder.setLength(0);
            stringBuilder.append(effect.getRemainingTurns());
            font.draw(batch, stringBuilder, drawX + 2f, iconY + size - 2f);
            activeCount++;
        }
        font.getData().setScale(HudConstants.HUD_LABEL_SCALE);
    }

    // =========================================================================
    // Centre-gap overlays (outside the panel)
    // =========================================================================

    private void drawGroundWeaponLabel(String label) {
        float centreX = 640f; // world centre — HUD gap spans X 420..860

        font.getData().setScale(1.0f);
        font.setColor(WARN_YELLOW);
        glyphLayout.setText(font, label);
        font.draw(batch, label, centreX - glyphLayout.width / 2f,
                  HudConstants.WEAPON_NAME_LABEL_Y + glyphLayout.height + font.getLineHeight() * 0.3f);

        float subPulse = 0.5f + 0.5f * MathUtils.sin(animationClockSeconds * MathUtils.PI2 * 1.5f);
        temporaryColor.set(WARN_YELLOW.r * subPulse, WARN_YELLOW.g * subPulse, WARN_YELLOW.b * subPulse, 1f);
        font.setColor(temporaryColor);
        font.getData().setScale(0.85f);
        String subLabel = "[ INSPECT ]";
        glyphLayout.setText(font, subLabel);
        font.draw(batch, subLabel, centreX - glyphLayout.width / 2f,
                  HudConstants.WEAPON_NAME_LABEL_Y + glyphLayout.height);

        font.getData().setScale(HudConstants.HUD_LABEL_SCALE);
    }

    private void drawMedkitWarning(float pulse) {
        float centreX = 640f;
        font.getData().setScale(1.1f);
        temporaryColor.set(HP_RED).lerp(WARN_YELLOW, pulse);
        font.setColor(temporaryColor);
        glyphLayout.setText(font, MEDKIT_WARN_TEXT);
        font.draw(batch, MEDKIT_WARN_TEXT, centreX - glyphLayout.width / 2f, HudConstants.HUD_MEDKIT_WARN_Y);
        font.getData().setScale(HudConstants.HUD_LABEL_SCALE);
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}
