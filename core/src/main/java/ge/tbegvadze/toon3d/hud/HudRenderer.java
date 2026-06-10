package ge.tbegvadze.toon3d.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.entity.Loadout;
import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.render.Renderable;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.world.HudState;

/**
 * Left-panel HUD: one semi-transparent chrome panel anchored to the bottom-left.
 * Four full-width segmented bars (top to bottom): HP, AR, CL, XP.
 * Label on the left, numeric value on the right, bar spanning between them.
 *
 * Render order per frame:
 *   Pass A — ShapeRenderer.Filled  (background, bars — alpha-blended)
 *   Pass B — ShapeRenderer.Line    (bevels, outlines)
 *   Pass C — SpriteBatch + BitmapFont (labels and numbers)
 */
public class HudRenderer implements Renderable, Disposable {

    // -------------------------------------------------------------------------
    // Palette
    // -------------------------------------------------------------------------
    private static final Color STEEL_DARK       = new Color(0.102f, 0.102f, 0.122f, 1f);
    private static final Color STEEL_BLACK      = new Color(0.055f, 0.055f, 0.071f, 1f);
    private static final Color BEVEL_LIGHT      = new Color(0.290f, 0.290f, 0.333f, 1f);
    private static final Color BEVEL_DARK       = new Color(0.031f, 0.031f, 0.039f, 1f);
    private static final Color PHOSPHOR_GREEN   = new Color(0.000f, 1.000f, 0.533f, 1f);
    private static final Color PHOSPHOR_DIM     = new Color(0.000f, 0.333f, 0.227f, 1f);
    private static final Color HP_DARKRED       = new Color(0.353f, 0.055f, 0.055f, 1f);
    private static final Color HP_RED           = new Color(0.878f, 0.094f, 0.094f, 1f);
    private static final Color HP_ORANGE        = new Color(1.000f, 0.690f, 0.125f, 1f);
    private static final Color HP_EMPTY         = new Color(0.165f, 0.039f, 0.039f, 1f);
    private static final Color ARM_DEEPCYAN     = new Color(0.039f, 0.290f, 0.400f, 1f);
    private static final Color ARM_CYAN         = new Color(0.094f, 0.753f, 0.878f, 1f);
    private static final Color ARM_WHITE        = new Color(0.847f, 0.973f, 1.000f, 1f);
    private static final Color ARM_EMPTY        = new Color(0.039f, 0.102f, 0.133f, 1f);
    private static final Color WARN_YELLOW      = new Color(1.000f, 0.878f, 0.000f, 1f);
    private static final Color CLIP_DARK_YELLOW = new Color(0.200f, 0.150f, 0.000f, 1f);
    private static final Color ALERT_RED        = new Color(1.000f, 0.165f, 0.165f, 1f);
    private static final Color LED_GREEN        = new Color(0.188f, 1.000f, 0.376f, 1f);
    private static final Color XP_DARK_GOLD    = new Color(0.220f, 0.160f, 0.010f, 1f);
    private static final Color XP_GOLD         = new Color(1.000f, 0.780f, 0.050f, 1f);
    private static final Color XP_BRIGHT_GOLD  = new Color(1.000f, 0.960f, 0.400f, 1f);

    // Weapon slot strip — four icons at bottom-left of left panel
    private static final Color SLOT_ACTIVE_AMBER = new Color(1.000f, 0.720f, 0.000f, 1f);
    private static final Color SLOT_FILLED_DIM   = new Color(0.350f, 0.250f, 0.010f, 1f);
    private static final Color SLOT_EMPTY_DARK   = new Color(0.060f, 0.060f, 0.080f, 0.85f);
    private static final Color SLOT_EMPTY_BORDER = new Color(0.180f, 0.180f, 0.220f, 1f);
    private static final Color SLOT_NUMBER_DIM   = new Color(0.280f, 0.260f, 0.180f, 1f);

    // Reusable mutable color for pulse calculations — never allocated inside render()
    private final Color temporaryColor = new Color();

    // -------------------------------------------------------------------------
    // Layout — derived from Constants
    // -------------------------------------------------------------------------
    private static final float PANEL_HEIGHT = Constants.HUD_HEIGHT;
    private static final float LEFT_WIDTH   = Constants.HUD_LEFT_PANEL_WIDTH;

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

    // Reusable — no String allocations inside render()
    private final StringBuilder stringBuilder = new StringBuilder(16);

    public HudRenderer(Player player, HudState hudState) {
        this.player   = player;
        this.hudState = hudState;
        this.shapes   = new ShapeRenderer();
        this.batch    = new SpriteBatch();
        this.font     = new BitmapFont();
        this.font.getData().markupEnabled = false;
        // Linear filtering reduces pixelation when font is scaled.
        this.font.getRegion().getTexture().setFilter(
                Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    }

    /** Provides the loadout whose slots are drawn in the left-panel strip. */
    public void setLoadout(Loadout newLoadout) {
        this.loadout = newLoadout;
    }

    public void update(float deltaTime) {
        animationClockSeconds += deltaTime;
        if (animationClockSeconds > 1000f) animationClockSeconds -= 1000f;

        float lerpRate = Constants.HUD_BAR_LERP_RATE * deltaTime;
        displayedHealthFraction = approach(displayedHealthFraction, player.getHealthFraction(), lerpRate);
        displayedArmorFraction  = approach(displayedArmorFraction,  player.getArmorFraction(),  lerpRate);
        displayedXpFraction     = approach(displayedXpFraction,     hudState.xpFraction,         lerpRate);
    }

    private static float approach(float current, float target, float maxStep) {
        if (current < target) return Math.min(current + maxStep, target);
        if (current > target) return Math.max(current - maxStep, target);
        return target;
    }

    @Override
    public void render(OrthographicCamera camera) {
        float   pulse  = GameMath.pulseMultiplier(animationClockSeconds, Constants.HUD_PULSE_HZ, 0.6f, 1.0f);
        boolean lowHp  = player.getHealthFraction() <= Constants.HUD_LOW_HP_THRESHOLD;
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
        shapes.end();

        // ---- Pass B: Line ----
        shapes.begin(ShapeRenderer.ShapeType.Line);
        drawPanelChromeLines(0f, 0f, LEFT_WIDTH, PANEL_HEIGHT);
        if (loadout != null) drawSlotStripLines(loadout);
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // ---- Pass C: Text ----
        batch.begin();
        drawHpLabel(lowHp, pulse, isDead);
        drawArmorLabel(isDead);
        drawClipLabel(isDead);
        drawXpLabel(isDead);
        if (loadout != null) drawSlotStripText(loadout, isDead);
        batch.end();
    }

    // =========================================================================
    // PASS A: Filled helpers
    // =========================================================================

    private void drawPanelChromeFilled(float panelX, float panelY, float panelW, float panelH,
                                        boolean alert, float pulse) {
        float inset = Constants.HUD_PANEL_INSET;
        float alpha = Constants.HUD_PANEL_ALPHA;

        shapes.setColor(STEEL_DARK.r, STEEL_DARK.g, STEEL_DARK.b, alpha);
        shapes.rect(panelX, panelY, panelW, panelH);
        shapes.setColor(STEEL_BLACK.r, STEEL_BLACK.g, STEEL_BLACK.b, alpha);
        shapes.rect(panelX + inset, panelY + inset, panelW - inset * 2f, panelH - inset * 2f);

        shapes.setColor(BEVEL_LIGHT);
        float rivetRadius = Constants.HUD_RIVET_RADIUS;
        float rivetOffset = 8f;
        shapes.circle(panelX + rivetOffset,           panelY + rivetOffset,           rivetRadius, 8);
        shapes.circle(panelX + panelW - rivetOffset,  panelY + rivetOffset,           rivetRadius, 8);
        shapes.circle(panelX + rivetOffset,           panelY + panelH - rivetOffset,  rivetRadius, 8);
        shapes.circle(panelX + panelW - rivetOffset,  panelY + panelH - rivetOffset,  rivetRadius, 8);

        float ledRadius = Constants.HUD_LED_RADIUS;
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

    private void drawHpBarFilled(float pulse, boolean lowHp, boolean isDead) {
        float barX      = Constants.HUD_BAR_START_X;
        float barY      = Constants.HUD_HP_BAR_Y;
        float barWidth  = Constants.HUD_BAR_FULL_WIDTH;
        float barHeight = Constants.HUD_BAR_HEIGHT;
        int   segments  = Constants.HUD_BAR_SEGMENT_COUNT;
        float gap       = Constants.HUD_BAR_SEGMENT_GAP;
        float segWidth  = (barWidth - (segments - 1) * gap) / segments;
        int   fillCount = isDead ? 0 : GameMath.segmentFillCount(displayedHealthFraction, segments);

        for (int segmentIndex = 0; segmentIndex < segments; segmentIndex++) {
            float segX = barX + segmentIndex * (segWidth + gap);
            if (segmentIndex < fillCount) {
                Color baseColor  = hpSegmentColor(segmentIndex);
                float brightness = (lowHp && !isDead) ? pulse : 1f;
                shapes.setColor(baseColor.r * brightness, baseColor.g * brightness, baseColor.b * brightness, 1f);
            } else {
                shapes.setColor(HP_EMPTY);
            }
            shapes.rect(segX, barY, segWidth, barHeight);
        }
    }

    private void drawArmorBarFilled(boolean isDead) {
        float barX      = Constants.HUD_BAR_START_X;
        float barY      = Constants.HUD_AR_BAR_Y;
        float barWidth  = Constants.HUD_BAR_FULL_WIDTH;
        float barHeight = Constants.HUD_BAR_HEIGHT;
        int   segments  = Constants.HUD_BAR_SEGMENT_COUNT;
        float gap       = Constants.HUD_BAR_SEGMENT_GAP;
        float segWidth  = (barWidth - (segments - 1) * gap) / segments;
        int   fillCount = isDead ? 0 : GameMath.segmentFillCount(displayedArmorFraction, segments);

        for (int segmentIndex = 0; segmentIndex < segments; segmentIndex++) {
            float segX = barX + segmentIndex * (segWidth + gap);
            shapes.setColor(segmentIndex < fillCount ? armorSegmentColor(segmentIndex) : ARM_EMPTY);
            shapes.rect(segX, barY, segWidth, barHeight);
        }
    }

    private void drawClipBarFilled(boolean isDead) {
        float barX      = Constants.HUD_BAR_START_X;
        float barY      = Constants.HUD_CLIP_BAR_Y;
        float barWidth  = Constants.HUD_BAR_FULL_WIDTH;
        float barHeight = Constants.HUD_BAR_HEIGHT;
        int   clipSize  = Math.max(1, hudState.clipSize);
        float gap       = Constants.HUD_BAR_SEGMENT_GAP;
        float segWidth  = (barWidth - (clipSize - 1) * gap) / clipSize;
        int   filled    = isDead ? 0 : Math.min(hudState.currentAmmo, clipSize);

        for (int segmentIndex = 0; segmentIndex < clipSize; segmentIndex++) {
            float segX = barX + segmentIndex * (segWidth + gap);
            shapes.setColor(segmentIndex < filled ? WARN_YELLOW : CLIP_DARK_YELLOW);
            shapes.rect(segX, barY, segWidth, barHeight);
        }
    }

    private void drawXpBarFilled(boolean isDead) {
        float barX      = Constants.HUD_BAR_START_X;
        float barY      = Constants.HUD_XP_BAR_Y;
        float barWidth  = Constants.HUD_BAR_FULL_WIDTH;
        float barHeight = Constants.HUD_BAR_HEIGHT;
        int   segments  = Constants.HUD_BAR_SEGMENT_COUNT;
        float gap       = Constants.HUD_BAR_SEGMENT_GAP;
        float segWidth  = (barWidth - (segments - 1) * gap) / segments;
        int   fillCount = isDead ? 0 : GameMath.segmentFillCount(displayedXpFraction, segments);

        for (int segmentIndex = 0; segmentIndex < segments; segmentIndex++) {
            float segX = barX + segmentIndex * (segWidth + gap);
            shapes.setColor(segmentIndex < fillCount ? xpSegmentColor(segmentIndex) : XP_DARK_GOLD);
            shapes.rect(segX, barY, segWidth, barHeight);
        }
    }

    // =========================================================================
    // PASS B: Line helpers
    // =========================================================================

    private void drawPanelChromeLines(float panelX, float panelY, float panelW, float panelH) {
        float inset = Constants.HUD_PANEL_INSET;
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
        float rivetRadius = Constants.HUD_RIVET_RADIUS;
        float rivetOffset = 8f;
        shapes.setColor(BEVEL_DARK);
        shapes.circle(panelX + rivetOffset,          panelY + rivetOffset,          rivetRadius, 8);
        shapes.circle(panelX + panelW - rivetOffset, panelY + rivetOffset,          rivetRadius, 8);
        shapes.circle(panelX + rivetOffset,          panelY + panelH - rivetOffset, rivetRadius, 8);
        shapes.circle(panelX + panelW - rivetOffset, panelY + panelH - rivetOffset, rivetRadius, 8);
    }

    // =========================================================================
    // PASS C: Text helpers
    // =========================================================================

    private void drawHpLabel(boolean lowHp, float pulse, boolean isDead) {
        float labelX = Constants.HUD_BAR_LABEL_X;
        float labelY = Constants.HUD_HP_BAR_Y + Constants.HUD_BAR_HEIGHT;

        font.getData().setScale(0.9f);
        font.setColor(PHOSPHOR_GREEN);
        font.draw(batch, "HP", labelX, labelY);

        if (lowHp && !isDead) {
            temporaryColor.set(HP_RED).mul(pulse, pulse, pulse, 1f);
            font.setColor(temporaryColor);
        } else {
            font.setColor(isDead ? PHOSPHOR_DIM : PHOSPHOR_GREEN);
        }
        stringBuilder.setLength(0);
        stringBuilder.append(player.getHealth());
        font.draw(batch, stringBuilder, Constants.HUD_BAR_NUMBER_X, labelY);
    }

    private void drawArmorLabel(boolean isDead) {
        float labelX = Constants.HUD_BAR_LABEL_X;
        float labelY = Constants.HUD_AR_BAR_Y + Constants.HUD_BAR_HEIGHT;

        font.getData().setScale(0.9f);
        font.setColor(isDead ? PHOSPHOR_DIM : ARM_CYAN);
        font.draw(batch, "AR", labelX, labelY);

        stringBuilder.setLength(0);
        stringBuilder.append(player.getArmor());
        font.draw(batch, stringBuilder, Constants.HUD_BAR_NUMBER_X, labelY);
    }

    private void drawClipLabel(boolean isDead) {
        float labelX = Constants.HUD_BAR_LABEL_X;
        float labelY = Constants.HUD_CLIP_BAR_Y + Constants.HUD_BAR_HEIGHT;

        font.getData().setScale(0.9f);
        font.setColor(isDead ? PHOSPHOR_DIM : WARN_YELLOW);
        font.draw(batch, "CL", labelX, labelY);

        stringBuilder.setLength(0);
        stringBuilder.append(isDead ? 0 : hudState.currentAmmo);
        stringBuilder.append('/');
        stringBuilder.append(hudState.clipSize);
        font.draw(batch, stringBuilder, Constants.HUD_BAR_NUMBER_X, labelY);
    }

    private void drawXpLabel(boolean isDead) {
        float labelX = Constants.HUD_BAR_LABEL_X;
        float labelY = Constants.HUD_XP_BAR_Y + Constants.HUD_BAR_HEIGHT;

        font.getData().setScale(0.9f);
        font.setColor(isDead ? PHOSPHOR_DIM : XP_GOLD);
        font.draw(batch, "XP", labelX, labelY);

        stringBuilder.setLength(0);
        stringBuilder.append("LV.");
        stringBuilder.append(hudState.playerLevel);
        font.draw(batch, stringBuilder, Constants.HUD_BAR_NUMBER_X, labelY);
    }

    // =========================================================================
    // Weapon slot strip — Pass A, B, C helpers
    // =========================================================================

    private void drawSlotStripFilled(Loadout activeLoadout, float pulse) {
        float originX  = Constants.WEAPON_SLOT_STRIP_ORIGIN_X;
        float originY  = Constants.WEAPON_SLOT_STRIP_ORIGIN_Y;
        float iconSize = Constants.WEAPON_SLOT_ICON_SIZE;
        float iconGap  = Constants.WEAPON_SLOT_ICON_GAP;
        int   active   = activeLoadout.getActiveSlotIndex();

        for (int slotIndex = 0; slotIndex < activeLoadout.getSlotCount(); slotIndex++) {
            float   slotX  = originX + slotIndex * (iconSize + iconGap);
            boolean filled = activeLoadout.getSlot(slotIndex) != null;
            boolean isActive = slotIndex == active && filled;

            if (filled) {
                float brightness = isActive ? pulse : 0.6f;
                shapes.setColor(SLOT_FILLED_DIM.r * brightness, SLOT_FILLED_DIM.g * brightness,
                                SLOT_FILLED_DIM.b * brightness, 0.90f);
            } else {
                shapes.setColor(SLOT_EMPTY_DARK);
            }
            shapes.rect(slotX, originY, iconSize, iconSize);

            if (filled) {
                float indicatorW = iconSize * 0.65f;
                float indicatorH = iconSize * 0.18f;
                float indicatorX = slotX + (iconSize - indicatorW) / 2f;
                float indicatorY = originY + iconSize * 0.55f;
                float bright = isActive ? pulse : 0.85f;
                shapes.setColor(SLOT_ACTIVE_AMBER.r * bright, SLOT_ACTIVE_AMBER.g * bright, 0f, 1f);
                shapes.rect(indicatorX, indicatorY, indicatorW, indicatorH);
            }
        }
    }

    private void drawSlotStripLines(Loadout activeLoadout) {
        float originX  = Constants.WEAPON_SLOT_STRIP_ORIGIN_X;
        float originY  = Constants.WEAPON_SLOT_STRIP_ORIGIN_Y;
        float iconSize = Constants.WEAPON_SLOT_ICON_SIZE;
        float iconGap  = Constants.WEAPON_SLOT_ICON_GAP;
        int   active   = activeLoadout.getActiveSlotIndex();

        for (int slotIndex = 0; slotIndex < activeLoadout.getSlotCount(); slotIndex++) {
            float   slotX  = originX + slotIndex * (iconSize + iconGap);
            boolean filled = activeLoadout.getSlot(slotIndex) != null;
            boolean isActive = slotIndex == active && filled;

            if (isActive) {
                shapes.setColor(SLOT_ACTIVE_AMBER);
            } else if (filled) {
                shapes.setColor(SLOT_FILLED_DIM.r * 2f, SLOT_FILLED_DIM.g * 2f, SLOT_FILLED_DIM.b * 2f, 1f);
            } else {
                shapes.setColor(SLOT_EMPTY_BORDER);
            }
            shapes.rect(slotX, originY, iconSize, iconSize);
        }
    }

    private void drawSlotStripText(Loadout activeLoadout, boolean isDead) {
        float originX  = Constants.WEAPON_SLOT_STRIP_ORIGIN_X;
        float originY  = Constants.WEAPON_SLOT_STRIP_ORIGIN_Y;
        float iconSize = Constants.WEAPON_SLOT_ICON_SIZE;
        float iconGap  = Constants.WEAPON_SLOT_ICON_GAP;
        int   active   = activeLoadout.getActiveSlotIndex();

        for (int slotIndex = 0; slotIndex < activeLoadout.getSlotCount(); slotIndex++) {
            float   slotX    = originX + slotIndex * (iconSize + iconGap);
            boolean filled   = activeLoadout.getSlot(slotIndex) != null;
            boolean isActive = slotIndex == active && filled;

            font.getData().setScale(0.75f);
            if (isDead) {
                font.setColor(PHOSPHOR_DIM);
            } else {
                font.setColor(isActive ? SLOT_ACTIVE_AMBER : SLOT_NUMBER_DIM);
            }
            stringBuilder.setLength(0);
            stringBuilder.append(slotIndex + 1);
            font.draw(batch, stringBuilder, slotX + 3f, originY + iconSize - 2f);

            if (filled && !isDead) {
                String weaponName = activeLoadout.getSlot(slotIndex).getDisplayName();
                String abbreviation = weaponName.length() > 4 ? weaponName.substring(0, 4) : weaponName;
                font.getData().setScale(0.55f);
                font.setColor(isActive ? SLOT_ACTIVE_AMBER : SLOT_NUMBER_DIM);
                font.draw(batch, abbreviation, slotX + 3f, originY + 16f);
                font.getData().setScale(0.75f);
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static Color hpSegmentColor(int segmentIndex) {
        if (segmentIndex <= 6)  return HP_DARKRED;
        if (segmentIndex <= 13) return HP_RED;
        return HP_ORANGE;
    }

    private static Color armorSegmentColor(int segmentIndex) {
        if (segmentIndex <= 6)  return ARM_DEEPCYAN;
        if (segmentIndex <= 13) return ARM_CYAN;
        return ARM_WHITE;
    }

    private static Color xpSegmentColor(int segmentIndex) {
        if (segmentIndex <= 9)  return XP_GOLD;
        return XP_BRIGHT_GOLD;
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}
