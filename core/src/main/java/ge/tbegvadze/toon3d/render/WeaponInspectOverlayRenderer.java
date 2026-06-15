package ge.tbegvadze.toon3d.render;

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
import ge.tbegvadze.toon3d.entity.Weapon;
import ge.tbegvadze.toon3d.item.GroundItem;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.HudConstants;

import java.util.function.IntConsumer;

/**
 * Modal overlay shown when the player taps INSPECT while standing on a weapon GroundItem.
 *
 * Two phases:
 *   STATS_CARD  — side-by-side stat comparison (ground weapon vs active weapon).
 *                 Footer buttons: primary action (TAKE / SWAP SLOT / CONVERT AMMO) + CLOSE.
 *   SLOT_SELECT — tap a loadout slot to evict it and place the ground weapon there.
 *                 Footer buttons: CANCEL (returns to STATS_CARD).
 *
 * No allocations in render() — all scratch objects are pre-allocated in the constructor.
 * Callers must set the four callbacks via setOn*() before calling show().
 */
public final class WeaponInspectOverlayRenderer implements Renderable, Disposable {

    private enum Phase { STATS_CARD, SLOT_SELECT }
    private enum StatKind { DAMAGE, CLIP, RELOAD, RANGE }

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final Color SCRIM_BG     = new Color(0f,     0f,     0f,     0.68f);
    private static final Color PANEL_BG     = new Color(0.071f, 0.071f, 0.086f,
                                                        HudConstants.WEAPON_INSPECT_PANEL_ALPHA);
    private static final Color HEADER_BG    = new Color(0.10f,  0.10f,  0.12f,  1f);
    private static final Color BTN_NORMAL   = new Color(0.14f,  0.14f,  0.17f,  1f);
    private static final Color BTN_POSITIVE = new Color(0.08f,  0.22f,  0.08f,  1f);
    private static final Color BTN_BORDER   = new Color(0.35f,  0.35f,  0.40f,  1f);
    private static final Color DIVIDER      = new Color(0.25f,  0.25f,  0.28f,  1f);
    private static final Color AMBER        = new Color(0.902f, 0.667f, 0.157f, 1f);
    private static final Color WHITE_COLOR  = new Color(1f,     1f,     1f,     1f);
    private static final Color DIM_COLOR    = new Color(0.50f,  0.50f,  0.50f,  1f);
    private static final Color GREEN_BETTER = new Color(0.20f,  0.90f,  0.30f,  1f);
    private static final Color RED_WORSE    = new Color(0.95f,  0.20f,  0.20f,  1f);
    private static final Color SLOT_ROW_BG  = new Color(0.12f,  0.18f,  0.12f,  1f);

    // ── Card geometry (derived once from HudConstants) ────────────────────────
    private static final float CARD_X     = HudConstants.WEAPON_INSPECT_CARD_ORIGIN_X;  // 320
    private static final float CARD_Y     = HudConstants.WEAPON_INSPECT_CARD_ORIGIN_Y;  // 130
    private static final float CARD_W     = HudConstants.WEAPON_INSPECT_CARD_WIDTH;     // 640
    private static final float CARD_H     = HudConstants.WEAPON_INSPECT_CARD_HEIGHT;    // 460
    private static final float CARD_RIGHT = CARD_X + CARD_W;                            // 960
    private static final float CARD_TOP   = CARD_Y + CARD_H;                            // 590

    private static final float HEADER_H   = 38f;
    private static final float HEADER_Y   = CARD_TOP - HEADER_H;   // 552

    private static final float FOOTER_PAD   = 8f;
    private static final float BTN_H        = HudConstants.WEAPON_INSPECT_BUTTON_HEIGHT; // 54
    private static final float BTN_W        = HudConstants.WEAPON_INSPECT_BUTTON_WIDTH;  // 200
    private static final float FOOTER_H     = BTN_H + 2f * FOOTER_PAD;                  // 70
    private static final float BTN_Y_BOTTOM = CARD_Y + FOOTER_PAD;                      // 138

    private static final float BTN_PRIMARY_X = CARD_X + 20f;              // 340
    private static final float BTN_CLOSE_X   = CARD_RIGHT - 20f - BTN_W;  // 740

    private static final float CONTENT_Y   = CARD_Y + FOOTER_H + 4f;   // 204
    private static final float CONTENT_TOP = HEADER_Y - 4f;             // 548

    // Three-column layout: ground values | stat labels | active values
    private static final float GROUND_COL_X = CARD_X + 16f;        // 336
    private static final float CENTER_X     = CARD_X + CARD_W / 2f; // 640
    private static final float ACTIVE_COL_X = CARD_RIGHT - 16f;     // 944

    // 6 stat rows inside the content area (CONTENT_Y..CONTENT_TOP = 344px)
    private static final int   STAT_ROW_COUNT = 6;
    private static final float COL_HEADER_Y   = CONTENT_TOP - 8f;   // 540 — column header text
    private static final float STAT_START_Y   = CONTENT_TOP - 32f;  // 516 — first stat row
    private static final float STAT_STEP      = (STAT_START_Y - CONTENT_Y) / STAT_ROW_COUNT; // ~52

    // Slot-select rows
    private static final float SLOT_ROW_H   = 64f;
    private static final float SLOT_ROW_PAD = 8f;

    // ── Owned resources ───────────────────────────────────────────────────────
    private final ShapeRenderer shapeRenderer;
    private final SpriteBatch   spriteBatch;
    private final BitmapFont    font;
    private final GlyphLayout   glyphLayout;

    // ── State (written by show()) ─────────────────────────────────────────────
    private boolean    visible       = false;
    private Phase      phase         = Phase.STATS_CARD;
    private GroundItem groundItem    = null;
    private Weapon     groundWeapon  = null;  // null when not found in arsenal
    private Weapon     activeWeapon  = null;
    private Loadout    loadout       = null;
    private boolean    alreadyOwned  = false; // ground weapon is already in a loadout slot
    private boolean    loadoutFull   = false; // loadout full AND ground weapon not owned
    private float      facilityTime  = 0f;

    // ── Callbacks (wired by World before show()) ──────────────────────────────
    private Runnable    onTake          = null; // free-slot equip
    private Runnable    onConvertToAmmo = null; // weapon already owned → convert to ammo
    private IntConsumer onEvictSlot     = null; // full loadout → evict slot N, equip ground weapon
    private Runnable    onClose         = null; // close without any action

    // ── Scratch — pre-allocated to avoid allocs in render() ───────────────────
    private final StringBuilder textBuilder    = new StringBuilder(32);
    private final Color         temporaryColor = new Color();

    // ── Constructor ───────────────────────────────────────────────────────────

    public WeaponInspectOverlayRenderer() {
        shapeRenderer = new ShapeRenderer();
        spriteBatch   = new SpriteBatch();
        font          = new BitmapFont();
        glyphLayout   = new GlyphLayout();
    }

    // ── Callback setters ──────────────────────────────────────────────────────

    public void setOnTake(Runnable callback)          { this.onTake = callback; }
    public void setOnConvertToAmmo(Runnable callback) { this.onConvertToAmmo = callback; }
    public void setOnEvictSlot(IntConsumer callback)  { this.onEvictSlot = callback; }
    public void setOnClose(Runnable callback)         { this.onClose = callback; }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Opens the overlay showing the ground weapon vs the currently active weapon.
     * groundWeaponParam may be null when the weapon type is not in the arsenal.
     */
    public void show(GroundItem groundItemParam, Weapon groundWeaponParam,
                     Weapon activeWeaponParam, Loadout loadoutParam) {
        this.groundItem   = groundItemParam;
        this.groundWeapon = groundWeaponParam;
        this.activeWeapon = activeWeaponParam;
        this.loadout      = loadoutParam;

        this.alreadyOwned = false;
        if (groundWeaponParam != null && loadoutParam != null) {
            for (int slotIndex = 0; slotIndex < loadoutParam.getSlotCount(); slotIndex++) {
                if (loadoutParam.getSlot(slotIndex) == groundWeaponParam) {
                    this.alreadyOwned = true;
                    break;
                }
            }
        }
        // loadoutFull is only meaningful when the weapon is NOT already owned
        this.loadoutFull = (loadoutParam != null) && loadoutParam.isFull() && !alreadyOwned;
        this.phase       = Phase.STATS_CARD;
        this.visible     = true;
    }

    public void hide() {
        visible      = false;
        groundItem   = null;
        groundWeapon = null;
        activeWeapon = null;
        loadout      = null;
    }

    public boolean isVisible() { return visible; }

    /** Pass the facility clock each frame so slot rows can pulse. */
    public void setFacilityTime(float time) { this.facilityTime = time; }

    // ── Touch handling ────────────────────────────────────────────────────────

    /** Routes a touch tap at world-space coordinates to the appropriate action. */
    public void handleTap(float worldX, float worldY) {
        if (!visible) return;
        if (phase == Phase.STATS_CARD) {
            handleStatsCardTap(worldX, worldY);
        } else {
            handleSlotSelectTap(worldX, worldY);
        }
    }

    private void handleStatsCardTap(float worldX, float worldY) {
        if (hitButton(worldX, worldY, BTN_CLOSE_X, BTN_Y_BOTTOM, BTN_W, BTN_H)) {
            if (onClose != null) onClose.run();
            return;
        }
        if (hitButton(worldX, worldY, BTN_PRIMARY_X, BTN_Y_BOTTOM, BTN_W, BTN_H)) {
            if (alreadyOwned) {
                if (onConvertToAmmo != null) onConvertToAmmo.run();
            } else if (!loadoutFull) {
                if (onTake != null) onTake.run();
            } else {
                phase = Phase.SLOT_SELECT;
            }
        }
    }

    private void handleSlotSelectTap(float worldX, float worldY) {
        if (hitButton(worldX, worldY, BTN_CLOSE_X, BTN_Y_BOTTOM, BTN_W, BTN_H)) {
            phase = Phase.STATS_CARD;
            return;
        }
        if (loadout == null) return;
        float rowTop = CONTENT_TOP - SLOT_ROW_PAD;
        for (int slotIndex = 0; slotIndex < loadout.getSlotCount(); slotIndex++) {
            float rowBottom = rowTop - SLOT_ROW_H;
            if (loadout.getSlot(slotIndex) != null
                    && worldX >= CARD_X + 12f && worldX <= CARD_RIGHT - 12f
                    && worldY >= rowBottom    && worldY <= rowTop) {
                if (onEvictSlot != null) onEvictSlot.accept(slotIndex);
                return;
            }
            rowTop = rowBottom - SLOT_ROW_PAD;
        }
    }

    private static boolean hitButton(float worldX, float worldY,
                                     float bx, float by, float bw, float bh) {
        return worldX >= bx && worldX <= bx + bw && worldY >= by && worldY <= by + bh;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(OrthographicCamera camera) {
        if (!visible) return;
        renderFilledShapes(camera);
        renderLineShapes(camera);
        renderText(camera);
    }

    // ── Pass A: filled shapes ─────────────────────────────────────────────────

    private void renderFilledShapes(OrthographicCamera camera) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Full-screen scrim
        shapeRenderer.setColor(SCRIM_BG);
        shapeRenderer.rect(0, 0, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);

        // Card background and header
        shapeRenderer.setColor(PANEL_BG);
        shapeRenderer.rect(CARD_X, CARD_Y, CARD_W, CARD_H);
        shapeRenderer.setColor(HEADER_BG);
        shapeRenderer.rect(CARD_X, HEADER_Y, CARD_W, HEADER_H);

        // Primary action button (STATS_CARD only)
        if (phase == Phase.STATS_CARD) {
            shapeRenderer.setColor((!alreadyOwned && !loadoutFull) ? BTN_POSITIVE : BTN_NORMAL);
            shapeRenderer.rect(BTN_PRIMARY_X, BTN_Y_BOTTOM, BTN_W, BTN_H);
        }
        // Close / Cancel button (always shown)
        shapeRenderer.setColor(BTN_NORMAL);
        shapeRenderer.rect(BTN_CLOSE_X, BTN_Y_BOTTOM, BTN_W, BTN_H);

        // Pulsing slot rows (SLOT_SELECT only)
        if (phase == Phase.SLOT_SELECT && loadout != null) {
            float rowTop = CONTENT_TOP - SLOT_ROW_PAD;
            for (int slotIndex = 0; slotIndex < loadout.getSlotCount(); slotIndex++) {
                float rowBottom = rowTop - SLOT_ROW_H;
                if (loadout.getSlot(slotIndex) != null) {
                    float pulse = 0.6f + 0.4f * MathUtils.sin(facilityTime * 2.5f + slotIndex * 1.3f);
                    temporaryColor.set(SLOT_ROW_BG.r * pulse, SLOT_ROW_BG.g * pulse,
                                      SLOT_ROW_BG.b * pulse, SLOT_ROW_BG.a);
                    shapeRenderer.setColor(temporaryColor);
                    shapeRenderer.rect(CARD_X + 12f, rowBottom, CARD_W - 24f, SLOT_ROW_H);
                }
                rowTop = rowBottom - SLOT_ROW_PAD;
            }
        }

        shapeRenderer.end();
    }

    // ── Pass B: line shapes ───────────────────────────────────────────────────

    private void renderLineShapes(OrthographicCamera camera) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        // Card border and button borders
        shapeRenderer.setColor(BTN_BORDER);
        shapeRenderer.rect(CARD_X, CARD_Y, CARD_W, CARD_H);
        if (phase == Phase.STATS_CARD) {
            shapeRenderer.rect(BTN_PRIMARY_X, BTN_Y_BOTTOM, BTN_W, BTN_H);
        }
        shapeRenderer.rect(BTN_CLOSE_X, BTN_Y_BOTTOM, BTN_W, BTN_H);

        // Header underline
        shapeRenderer.setColor(AMBER);
        shapeRenderer.line(CARD_X, HEADER_Y, CARD_RIGHT, HEADER_Y);

        if (phase == Phase.STATS_CARD) {
            // Vertical divider between ground and active columns
            shapeRenderer.setColor(DIVIDER);
            shapeRenderer.line(CENTER_X, CONTENT_Y + 4f, CENTER_X, COL_HEADER_Y - 4f);
        } else if (loadout != null) {
            // Slot row outlines
            shapeRenderer.setColor(AMBER);
            float rowTop = CONTENT_TOP - SLOT_ROW_PAD;
            for (int slotIndex = 0; slotIndex < loadout.getSlotCount(); slotIndex++) {
                float rowBottom = rowTop - SLOT_ROW_H;
                if (loadout.getSlot(slotIndex) != null) {
                    shapeRenderer.rect(CARD_X + 12f, rowBottom, CARD_W - 24f, SLOT_ROW_H);
                }
                rowTop = rowBottom - SLOT_ROW_PAD;
            }
        }

        shapeRenderer.end();
    }

    // ── Pass C: text ──────────────────────────────────────────────────────────

    private void renderText(OrthographicCamera camera) {
        spriteBatch.setProjectionMatrix(camera.combined);
        spriteBatch.begin();

        drawHeader();
        if (phase == Phase.STATS_CARD) {
            drawStatsContent();
        } else {
            drawSlotSelectContent();
        }
        drawFooterButtons();

        spriteBatch.end();
    }

    private void drawHeader() {
        String title;
        if (phase == Phase.STATS_CARD) {
            title = (groundItem != null) ? groundItem.stack.getType().getDisplayName() : "WEAPON";
        } else {
            title = "SWAP OUT WHICH?";
        }
        glyphLayout.setText(font, title);
        font.setColor(AMBER);
        font.draw(spriteBatch, title,
                  CARD_X + (CARD_W - glyphLayout.width) / 2f,
                  HEADER_Y + HEADER_H - 10f);
    }

    private void drawStatsContent() {
        // Column headers
        font.setColor(groundWeapon != null ? WHITE_COLOR : DIM_COLOR);
        font.draw(spriteBatch, "GROUND", GROUND_COL_X, COL_HEADER_Y);

        glyphLayout.setText(font, "STAT");
        font.setColor(DIM_COLOR);
        font.draw(spriteBatch, "STAT", CENTER_X - glyphLayout.width / 2f, COL_HEADER_Y);

        glyphLayout.setText(font, "ACTIVE");
        font.setColor(activeWeapon != null ? WHITE_COLOR : DIM_COLOR);
        font.draw(spriteBatch, "ACTIVE", ACTIVE_COL_X - glyphLayout.width, COL_HEADER_Y);

        // Six stat rows
        float rowY = STAT_START_Y;
        drawIntRow(rowY, "DMG",    groundWeapon, activeWeapon, StatKind.DAMAGE, false);
        rowY -= STAT_STEP;
        drawIntRow(rowY, "CLIP",   groundWeapon, activeWeapon, StatKind.CLIP,   false);
        rowY -= STAT_STEP;
        drawIntRow(rowY, "RELOAD", groundWeapon, activeWeapon, StatKind.RELOAD, true);
        rowY -= STAT_STEP;
        drawIntRow(rowY, "RANGE",  groundWeapon, activeWeapon, StatKind.RANGE,  false);
        rowY -= STAT_STEP;
        drawDropRow(rowY);
        rowY -= STAT_STEP;
        drawAmmoRow(rowY);
    }

    private int statValue(Weapon weapon, StatKind kind) {
        if (weapon == null) return -1;
        switch (kind) {
            case DAMAGE: return weapon.getDamage();
            case CLIP:   return weapon.getClipSize();
            case RELOAD: return weapon.getReloadTime();
            case RANGE:  return weapon.getRange();
            default:     return -1;
        }
    }

    /**
     * Draws one stat row with a center label and ground/active integer values.
     * Ground value is color-coded green (better) or red (worse) relative to the active weapon.
     */
    private void drawIntRow(float rowY, String label, Weapon ground, Weapon active,
                            StatKind kind, boolean lowerIsBetter) {
        // Center label
        glyphLayout.setText(font, label);
        font.setColor(DIM_COLOR);
        font.draw(spriteBatch, label, CENTER_X - glyphLayout.width / 2f, rowY);

        int groundVal = statValue(ground, kind);
        int activeVal = statValue(active, kind);

        if (groundVal >= 0) {
            textBuilder.setLength(0);
            textBuilder.append(groundVal);
            font.setColor(deltaColor(groundVal, activeVal, lowerIsBetter));
            font.draw(spriteBatch, textBuilder, GROUND_COL_X, rowY);
        } else {
            font.setColor(DIM_COLOR);
            font.draw(spriteBatch, "-", GROUND_COL_X, rowY);
        }

        if (activeVal >= 0) {
            textBuilder.setLength(0);
            textBuilder.append(activeVal);
            glyphLayout.setText(font, textBuilder);
            font.setColor(WHITE_COLOR);
            font.draw(spriteBatch, textBuilder, ACTIVE_COL_X - glyphLayout.width, rowY);
        } else {
            font.setColor(DIM_COLOR);
            glyphLayout.setText(font, "-");
            font.draw(spriteBatch, "-", ACTIVE_COL_X - glyphLayout.width, rowY);
        }
    }

    private Color deltaColor(int groundVal, int activeVal, boolean lowerIsBetter) {
        if (activeVal < 0 || groundVal == activeVal) return WHITE_COLOR;
        boolean groundBetter = lowerIsBetter ? (groundVal < activeVal) : (groundVal > activeVal);
        return groundBetter ? GREEN_BETTER : RED_WORSE;
    }

    private void drawDropRow(float rowY) {
        glyphLayout.setText(font, "DROP");
        font.setColor(DIM_COLOR);
        font.draw(spriteBatch, "DROP", CENTER_X - glyphLayout.width / 2f, rowY);

        int activePct = (activeWeapon != null)
                ? Math.round(activeWeapon.getDamageDropCoefficient() * 100f) : -1;

        if (groundWeapon != null) {
            int groundPct = Math.round(groundWeapon.getDamageDropCoefficient() * 100f);
            textBuilder.setLength(0);
            textBuilder.append(groundPct).append('%');
            font.setColor(deltaColor(groundPct, activePct, true));
            font.draw(spriteBatch, textBuilder, GROUND_COL_X, rowY);
        } else {
            font.setColor(DIM_COLOR);
            font.draw(spriteBatch, "-", GROUND_COL_X, rowY);
        }

        if (activeWeapon != null) {
            textBuilder.setLength(0);
            textBuilder.append(activePct).append('%');
            glyphLayout.setText(font, textBuilder);
            font.setColor(WHITE_COLOR);
            font.draw(spriteBatch, textBuilder, ACTIVE_COL_X - glyphLayout.width, rowY);
        } else {
            font.setColor(DIM_COLOR);
            glyphLayout.setText(font, "-");
            font.draw(spriteBatch, "-", ACTIVE_COL_X - glyphLayout.width, rowY);
        }
    }

    private void drawAmmoRow(float rowY) {
        glyphLayout.setText(font, "AMMO");
        font.setColor(DIM_COLOR);
        font.draw(spriteBatch, "AMMO", CENTER_X - glyphLayout.width / 2f, rowY);

        if (groundWeapon != null) {
            String groundAmmo = (groundWeapon.getAmmoType() != null)
                    ? groundWeapon.getAmmoType().getDisplayName() : "Melee";
            font.setColor(WHITE_COLOR);
            font.draw(spriteBatch, groundAmmo, GROUND_COL_X, rowY);
        } else {
            font.setColor(DIM_COLOR);
            font.draw(spriteBatch, "-", GROUND_COL_X, rowY);
        }

        if (activeWeapon != null) {
            String activeAmmo = (activeWeapon.getAmmoType() != null)
                    ? activeWeapon.getAmmoType().getDisplayName() : "Melee";
            glyphLayout.setText(font, activeAmmo);
            font.setColor(WHITE_COLOR);
            font.draw(spriteBatch, activeAmmo, ACTIVE_COL_X - glyphLayout.width, rowY);
        } else {
            font.setColor(DIM_COLOR);
            glyphLayout.setText(font, "-");
            font.draw(spriteBatch, "-", ACTIVE_COL_X - glyphLayout.width, rowY);
        }
    }

    private void drawSlotSelectContent() {
        if (loadout == null) return;
        float rowTop = CONTENT_TOP - SLOT_ROW_PAD;
        for (int slotIndex = 0; slotIndex < loadout.getSlotCount(); slotIndex++) {
            float rowBottom = rowTop - SLOT_ROW_H;
            Weapon slotWeapon = loadout.getSlot(slotIndex);
            if (slotWeapon != null) {
                float nameY  = rowBottom + SLOT_ROW_H - 16f;
                float statsY = nameY - 22f;

                textBuilder.setLength(0);
                textBuilder.append("SLOT ").append(slotIndex + 1)
                           .append("  ").append(slotWeapon.getDisplayName());
                font.setColor(AMBER);
                font.draw(spriteBatch, textBuilder, CARD_X + 24f, nameY);

                textBuilder.setLength(0);
                textBuilder.append("DMG ").append(slotWeapon.getDamage())
                           .append("   RNG ").append(slotWeapon.getRange())
                           .append("   CLIP ").append(slotWeapon.getClipSize());
                font.setColor(DIM_COLOR);
                font.draw(spriteBatch, textBuilder, CARD_X + 24f, statsY);
            }
            rowTop = rowBottom - SLOT_ROW_PAD;
        }
    }

    private void drawFooterButtons() {
        // Primary action button label (STATS_CARD only)
        if (phase == Phase.STATS_CARD) {
            String primaryLabel;
            if (alreadyOwned) {
                primaryLabel = "CONVERT AMMO";
            } else if (loadoutFull) {
                primaryLabel = "SWAP SLOT";
            } else {
                primaryLabel = "TAKE";
            }
            glyphLayout.setText(font, primaryLabel);
            font.setColor(AMBER);
            font.draw(spriteBatch, primaryLabel,
                      BTN_PRIMARY_X + (BTN_W - glyphLayout.width) / 2f,
                      BTN_Y_BOTTOM + (BTN_H + glyphLayout.height) / 2f);
        }

        // Close / Cancel button label
        String closeLabel = (phase == Phase.SLOT_SELECT) ? "CANCEL" : "CLOSE";
        glyphLayout.setText(font, closeLabel);
        font.setColor(AMBER);
        font.draw(spriteBatch, closeLabel,
                  BTN_CLOSE_X + (BTN_W - glyphLayout.width) / 2f,
                  BTN_Y_BOTTOM + (BTN_H + glyphLayout.height) / 2f);
    }

    // ── Disposable ────────────────────────────────────────────────────────────

    @Override
    public void dispose() {
        shapeRenderer.dispose();
        spriteBatch.dispose();
        font.dispose();
    }
}
