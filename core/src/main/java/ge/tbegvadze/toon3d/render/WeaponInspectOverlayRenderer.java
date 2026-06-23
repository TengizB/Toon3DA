package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.entity.AbilityInstance;
import ge.tbegvadze.toon3d.entity.Loadout;
import ge.tbegvadze.toon3d.entity.Weapon;
import ge.tbegvadze.toon3d.entity.WeaponRoll;
import ge.tbegvadze.toon3d.entity.WeaponTier;
import ge.tbegvadze.toon3d.item.GroundItem;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.HudConstants;

import java.util.function.IntConsumer;

/**
 * Single-screen weapon pickup / compare card.
 *
 * Auto-opens the instant the player settles on a weapon tile. Three context-driven layouts:
 *   FREE SLOT  — one large "EQUIP" button (or "CHOOSE THIS WEAPON" in start-room mode).
 *   FULL LOADOUT — inline "SWAP ->" rows for each held weapon, no second modal phase.
 * CLOSE and CONVERT AMMO live in a fixed footer strip below the action area.
 *
 * No allocations in render() — all strings and hit-test rects are pre-built in show().
 */
public final class WeaponInspectOverlayRenderer implements Renderable, Disposable {

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final Color SCRIM_COLOR      = new Color(0f,     0f,     0f,     0.68f);
    private static final Color PANEL_COLOR      = new Color(0.06f,  0.06f,  0.08f,  HudConstants.WEAPON_CARD_PANEL_ALPHA);
    private static final Color HEADER_COLOR     = new Color(0.09f,  0.09f,  0.11f,  1f);
    private static final Color BTN_EQUIP_COLOR  = new Color(0.06f,  0.26f,  0.06f,  1f);
    private static final Color BTN_SWAP_COLOR   = new Color(0.18f,  0.14f,  0.06f,  1f);
    private static final Color BTN_NEUTRAL_COLOR= new Color(0.14f,  0.14f,  0.17f,  1f);
    private static final Color BTN_BORDER_COLOR = new Color(0.35f,  0.35f,  0.40f,  1f);
    private static final Color DIVIDER_COLOR    = new Color(0.22f,  0.22f,  0.26f,  1f);
    private static final Color AMBER_COLOR      = new Color(0.902f, 0.667f, 0.157f, 1f);
    private static final Color WHITE_COLOR      = new Color(1f,     1f,     1f,     1f);
    private static final Color DIM_COLOR        = new Color(0.50f,  0.50f,  0.50f,  1f);
    private static final Color GREEN_COLOR      = new Color(0.20f,  0.90f,  0.30f,  1f);
    private static final Color RED_COLOR        = new Color(0.95f,  0.20f,  0.20f,  1f);
    private static final Color ROW_HIGHLIGHT    = new Color(0.10f,  0.16f,  0.10f,  1f);
    private static final Color ACTIVE_SLOT_EDGE = new Color(0.902f, 0.667f, 0.157f, 0.6f);

    // ── Card geometry (derived from HudConstants) ─────────────────────────────
    private static final float CARD_X     = HudConstants.WEAPON_CARD_ORIGIN_X;    // 260
    private static final float CARD_Y     = HudConstants.WEAPON_CARD_ORIGIN_Y;    // 95
    private static final float CARD_W     = HudConstants.WEAPON_CARD_WIDTH;       // 760
    private static final float CARD_H     = HudConstants.WEAPON_CARD_HEIGHT;      // 530
    private static final float CARD_RIGHT = CARD_X + CARD_W;                      // 1020
    private static final float CARD_TOP   = CARD_Y + CARD_H;                      // 625

    // Header zone (top of card)
    private static final float HEADER_H   = HudConstants.WEAPON_CARD_HEADER_HEIGHT; // 50
    private static final float HEADER_Y   = CARD_TOP - HEADER_H;                  // 575

    // Footer zone (bottom of card, above CARD_Y padding)
    private static final float FOOTER_PAD = 10f;
    private static final float FOOTER_H   = HudConstants.WEAPON_CARD_FOOTER_H;    // 68
    private static final float FOOTER_Y   = CARD_Y + FOOTER_PAD;                  // 105 (bottom of footer btn)
    private static final float FOOTER_TOP = FOOTER_Y + FOOTER_H;                  // 173

    // Action zone sits between ability strip and footer
    private static final float ACTION_H   = HudConstants.WEAPON_CARD_ACTION_H;    // 185
    private static final float ACTION_Y   = FOOTER_TOP + 4f;                      // 177
    private static final float ACTION_TOP = ACTION_Y + ACTION_H;                   // 362

    // Ability strip
    private static final float ABILITY_H   = HudConstants.WEAPON_CARD_ABILITY_H;  // 55
    private static final float ABILITY_Y   = ACTION_TOP;                           // 362
    private static final float ABILITY_TOP = ABILITY_Y + ABILITY_H;               // 417

    // Stats zone: 4 rows × STAT_ROW_H occupying the space between the ability strip and the header.
    private static final float STAT_ROW_H     = HudConstants.WEAPON_CARD_STAT_ROW_H; // 36
    private static final float STATS_TOP      = HEADER_Y;                             // 575 — rows grow downward from here into the stats zone
    private static final int   STAT_ROW_COUNT = 4;
    // Stat column X positions
    private static final float GROUND_COL_X  = CARD_X + 16f;                     // 276  (ground value, left-align)
    private static final float CENTER_X       = CARD_X + CARD_W / 2f;            // 640  (stat labels, centered)
    private static final float ACTIVE_COL_X  = CARD_RIGHT - 16f;                 // 1004 (active value, right-align)

    // Equip button (free slot fast lane)
    private static final float EQUIP_BTN_W   = HudConstants.WEAPON_EQUIP_BUTTON_WIDTH;  // 520
    private static final float EQUIP_BTN_H   = HudConstants.WEAPON_EQUIP_BUTTON_HEIGHT; // 84
    private static final float EQUIP_BTN_X   = CARD_X + (CARD_W - EQUIP_BTN_W) / 2f;  // 380
    private static final float EQUIP_BTN_Y   = ACTION_Y + (ACTION_H - EQUIP_BTN_H) / 2f; // 227

    // Slot rows (full loadout)
    private static final float SLOT_ROW_H   = HudConstants.WEAPON_SLOT_ROW_HEIGHT; // 52
    private static final float SLOT_ROW_GAP = HudConstants.WEAPON_SLOT_ROW_GAP;   // 8
    private static final float SLOT_ROW_X   = CARD_X + 12f;
    private static final float SLOT_ROW_W   = CARD_W - 24f;
    private static final float SLOT_ROW_TOP = ACTION_TOP - 8f;                    // 354 (first row top)
    // Swap button within each row
    private static final float SWAP_BTN_W   = HudConstants.WEAPON_SWAP_BUTTON_WIDTH;  // 150
    private static final float SWAP_BTN_H   = HudConstants.WEAPON_SWAP_BUTTON_HEIGHT; // 46

    // Footer buttons
    private static final float CLOSE_BTN_W = HudConstants.WEAPON_CLOSE_BUTTON_WIDTH;   // 160
    private static final float CLOSE_BTN_H = HudConstants.WEAPON_CLOSE_BUTTON_HEIGHT;  // 52
    private static final float CLOSE_BTN_X = CARD_X + 16f;
    private static final float CLOSE_BTN_Y = FOOTER_Y + (FOOTER_H - CLOSE_BTN_H) / 2f;

    private static final float CONV_BTN_W  = HudConstants.WEAPON_CONVERT_BUTTON_WIDTH;  // 220
    private static final float CONV_BTN_H  = HudConstants.WEAPON_CONVERT_BUTTON_HEIGHT; // 52
    private static final float CONV_BTN_X  = CARD_RIGHT - 16f - CONV_BTN_W;
    private static final float CONV_BTN_Y  = FOOTER_Y + (FOOTER_H - CONV_BTN_H) / 2f;

    private static final int MAX_ABILITY_LINES = 5;

    // ── Owned resources ───────────────────────────────────────────────────────
    private final ShapeRenderer shapeRenderer;
    private final SpriteBatch   spriteBatch;
    private final BitmapFont    font;
    private final GlyphLayout   glyphLayout;

    // ── State (written by show()) ─────────────────────────────────────────────
    private boolean  visible        = false;
    private boolean  startRoomMode  = false;
    private boolean  loadoutFull    = false;
    // True when the player already carries this weapon TYPE. Because the arsenal keeps one
    // instance per type, the only valid action is a VARIANT SWAP (replace the held roll), never
    // a second equip — so the card shows a single REPLACE button instead of slot rows / EQUIP.
    private boolean  alreadyHeld    = false;
    private boolean  hasConvert     = false;
    private int      freeSlotIndex  = -1;
    private Loadout  loadout        = null;
    private int      activeSlotIndex = 0;

    // ── Cached strings and stats (built in show(), read in render()) ──────────
    private String   cachedWeaponName   = "";
    private String   cachedLevelTier    = "";
    private float    cachedTierRed      = AMBER_COLOR.r;
    private float    cachedTierGreen    = AMBER_COLOR.g;
    private float    cachedTierBlue     = AMBER_COLOR.b;
    private WeaponTier cachedTier       = WeaponTier.COMMON;

    // Ground weapon effective stats (computed from WeaponRoll + base weapon)
    private int cachedGroundDamage  = 0;
    private int cachedGroundClip    = 0;
    private int cachedGroundReload  = 0;
    private int cachedGroundRange   = 0;

    // Active weapon effective stats
    private int cachedActiveDamage  = 0;
    private int cachedActiveClip    = 0;
    private int cachedActiveReload  = 0;
    private int cachedActiveRange   = 0;

    // Ability lines
    private final String[]  cachedAbilityLines    = new String[MAX_ABILITY_LINES];
    private final boolean[] cachedAbilityLegendary = new boolean[MAX_ABILITY_LINES];
    private int             cachedAbilityCount     = 0;

    // Convert button label
    private String cachedConvertLabel = "CONVERT AMMO";

    // ── Callbacks ─────────────────────────────────────────────────────────────
    private Runnable    onEquipFreeSlot  = null;
    private IntConsumer onSwapSlot       = null;
    private Runnable    onConvertToAmmo  = null;
    private Runnable    onClose          = null;

    // ── Scratch — pre-allocated ───────────────────────────────────────────────
    private final StringBuilder textBuilder    = new StringBuilder(64);
    private final Color         temporaryColor = new Color();

    // ── Constructor ───────────────────────────────────────────────────────────

    public WeaponInspectOverlayRenderer() {
        shapeRenderer = new ShapeRenderer();
        spriteBatch   = new SpriteBatch();
        font          = new BitmapFont();
        font.getData().setScale(HudConstants.WEAPON_CARD_FONT_SCALE);
        glyphLayout   = new GlyphLayout();
    }

    // ── Callback setters ──────────────────────────────────────────────────────

    public void setOnEquipFreeSlot(Runnable callback)   { this.onEquipFreeSlot = callback; }
    public void setOnSwapSlot(IntConsumer callback)     { this.onSwapSlot = callback; }
    public void setOnConvertToAmmo(Runnable callback)   { this.onConvertToAmmo = callback; }
    public void setOnClose(Runnable callback)           { this.onClose = callback; }

    // Kept for backward compatibility — World wires these before fully migrating
    public void setOnTake(Runnable callback)            { this.onEquipFreeSlot = callback; }
    public void setOnEvictSlot(IntConsumer callback)    { this.onSwapSlot = callback; }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Opens the card for a ground weapon.
     *
     * @param groundItem    the ground item the player is standing on
     * @param groundRoll    rolled stats for that weapon (null = Common Lv1 fallback)
     * @param arsenalWeapon matching weapon in arsenal, used for base stats (null = convert-only mode)
     * @param activeWeapon  currently active weapon in loadout (null = empty loadout)
     * @param loadoutRef    the player's current loadout
     * @param startRoom     true in the starting weapon selection room
     * @param convertAmount how many ammo units a CONVERT yields (0 = hide CONVERT)
     */
    public void show(GroundItem groundItem, WeaponRoll groundRoll,
                     Weapon arsenalWeapon, Weapon activeWeapon,
                     Loadout loadoutRef, boolean startRoom, int convertAmount) {
        this.loadout       = loadoutRef;
        this.startRoomMode = startRoom;
        this.visible       = true;

        WeaponTier tier = (groundRoll != null) ? groundRoll.tier : WeaponTier.COMMON;
        int        level = (groundRoll != null) ? groundRoll.weaponLevel : 1;

        cachedTier       = tier;
        cachedTierRed    = tier.colorRed;
        cachedTierGreen  = tier.colorGreen;
        cachedTierBlue   = tier.colorBlue;
        cachedWeaponName = (arsenalWeapon != null)
                           ? arsenalWeapon.getDisplayName().toUpperCase()
                           : (groundItem != null ? groundItem.stack.getType().getDisplayName().toUpperCase() : "WEAPON");
        cachedLevelTier  = "Lv " + level + "  " + tier.displayName.toUpperCase();

        buildStatCache(groundRoll, arsenalWeapon, activeWeapon);
        buildAbilityCache(groundRoll);

        hasConvert         = convertAmount > 0;
        cachedConvertLabel = hasConvert ? ("CONVERT  +" + convertAmount + " AMMO") : "CONVERT AMMO";

        // Determine loadout state for action zone layout
        if (loadoutRef == null || !loadoutRef.isFull()) {
            loadoutFull   = false;
            freeSlotIndex = findFreeSlot(loadoutRef);
        } else {
            loadoutFull   = true;
            freeSlotIndex = -1;
        }
        alreadyHeld = (loadoutRef != null && arsenalWeapon != null
                       && loadoutRef.slotIndexOf(arsenalWeapon) >= 0);
        activeSlotIndex = (loadoutRef != null) ? loadoutRef.getActiveSlotIndex() : 0;
    }

    /** True when the action zone shows a single full-width button rather than the SWAP slot rows. */
    private boolean singleButtonMode() {
        return !loadoutFull || alreadyHeld;
    }

    /** Closes the card and clears all held references. */
    public void hide() {
        visible      = false;
        alreadyHeld  = false;
        loadout      = null;
        for (int lineIndex = 0; lineIndex < MAX_ABILITY_LINES; lineIndex++) {
            cachedAbilityLines[lineIndex]    = null;
            cachedAbilityLegendary[lineIndex] = false;
        }
        cachedAbilityCount = 0;
    }

    public boolean isVisible() { return visible; }

    /** Updates the facility clock for any pulsing effects. Currently a no-op placeholder. */
    public void setFacilityTime(float time) { /* pulsing slots can use this if added later */ }

    // ── Touch handling ────────────────────────────────────────────────────────

    public void handleTap(float worldX, float worldY) {
        if (!visible) return;

        // CLOSE button (always available)
        if (hitTest(worldX, worldY, CLOSE_BTN_X, CLOSE_BTN_Y, CLOSE_BTN_W, CLOSE_BTN_H)) {
            if (onClose != null) onClose.run();
            return;
        }

        // CONVERT button (secondary, footer)
        if (hasConvert && hitTest(worldX, worldY, CONV_BTN_X, CONV_BTN_Y, CONV_BTN_W, CONV_BTN_H)) {
            if (onConvertToAmmo != null) onConvertToAmmo.run();
            return;
        }

        if (singleButtonMode()) {
            // EQUIP (free slot) or REPLACE (already-held variant swap) — single full-width button.
            // Both route through onEquipFreeSlot; World decides equip vs variant swap by checking
            // whether the weapon type is already in the loadout.
            if (hitTest(worldX, worldY, EQUIP_BTN_X, EQUIP_BTN_Y, EQUIP_BTN_W, EQUIP_BTN_H)) {
                if (onEquipFreeSlot != null) onEquipFreeSlot.run();
            }
        } else {
            // Inline slot rows — entire row is the tap target
            hitTestSlotRows(worldX, worldY);
        }
    }

    private void hitTestSlotRows(float worldX, float worldY) {
        if (loadout == null) return;
        float rowTop = SLOT_ROW_TOP;
        for (int slotIndex = 0; slotIndex < loadout.getSlotCount(); slotIndex++) {
            float rowBottom = rowTop - SLOT_ROW_H;
            if (loadout.getSlot(slotIndex) != null
                    && worldX >= SLOT_ROW_X && worldX <= SLOT_ROW_X + SLOT_ROW_W
                    && worldY >= rowBottom && worldY <= rowTop) {
                if (onSwapSlot != null) onSwapSlot.accept(slotIndex);
                return;
            }
            rowTop = rowBottom - SLOT_ROW_GAP;
        }
    }

    private static boolean hitTest(float worldX, float worldY,
                                   float rectX, float rectY, float rectW, float rectH) {
        return worldX >= rectX && worldX <= rectX + rectW
            && worldY >= rectY && worldY <= rectY + rectH;
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
        shapeRenderer.setColor(SCRIM_COLOR);
        shapeRenderer.rect(0, 0, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);

        // Card background
        shapeRenderer.setColor(PANEL_COLOR);
        shapeRenderer.rect(CARD_X, CARD_Y, CARD_W, CARD_H);

        // Header background (tinted panel)
        shapeRenderer.setColor(HEADER_COLOR);
        shapeRenderer.rect(CARD_X, HEADER_Y, CARD_W, HEADER_H);

        // Tier icon shape left of weapon name
        drawTierIconFilled(cachedTier, CARD_X + 30f, HEADER_Y + HEADER_H / 2f, 10f);

        // Action zone background (subtle)
        shapeRenderer.setColor(0.08f, 0.08f, 0.10f, 0.5f);
        shapeRenderer.rect(CARD_X, ACTION_Y, CARD_W, ACTION_H);

        // Action zone content
        if (singleButtonMode()) {
            shapeRenderer.setColor(alreadyHeld ? BTN_SWAP_COLOR : BTN_EQUIP_COLOR);
            shapeRenderer.rect(EQUIP_BTN_X, EQUIP_BTN_Y, EQUIP_BTN_W, EQUIP_BTN_H);
        } else {
            renderSlotRowsFilled();
        }

        // Footer strip
        shapeRenderer.setColor(BTN_NEUTRAL_COLOR);
        shapeRenderer.rect(CLOSE_BTN_X, CLOSE_BTN_Y, CLOSE_BTN_W, CLOSE_BTN_H);
        if (hasConvert) {
            shapeRenderer.setColor(0.12f, 0.08f, 0.04f, 1f);
            shapeRenderer.rect(CONV_BTN_X, CONV_BTN_Y, CONV_BTN_W, CONV_BTN_H);
        }

        shapeRenderer.end();
    }

    private void drawTierIconFilled(WeaponTier tier, float centerX, float centerY, float size) {
        shapeRenderer.setColor(cachedTierRed, cachedTierGreen, cachedTierBlue, 1f);
        switch (tier) {
            case COMMON:
                shapeRenderer.circle(centerX, centerY, size, 20);
                break;
            case UNCOMMON:
                shapeRenderer.rect(centerX - size, centerY - size, size * 2f, size * 2f);
                break;
            case RARE:
                shapeRenderer.triangle(
                    centerX,         centerY + size,
                    centerX - size,  centerY - size,
                    centerX + size,  centerY - size);
                break;
            case EPIC:
                shapeRenderer.triangle(
                    centerX,        centerY + size,
                    centerX - size, centerY,
                    centerX,        centerY - size);
                shapeRenderer.triangle(
                    centerX,        centerY + size,
                    centerX + size, centerY,
                    centerX,        centerY - size);
                break;
            case LEGENDARY:
            default:
                shapeRenderer.circle(centerX, centerY, size, 5);
                break;
        }
    }

    private void renderSlotRowsFilled() {
        if (loadout == null) return;
        float rowTop = SLOT_ROW_TOP;
        for (int slotIndex = 0; slotIndex < loadout.getSlotCount(); slotIndex++) {
            float rowBottom = rowTop - SLOT_ROW_H;
            if (loadout.getSlot(slotIndex) != null) {
                shapeRenderer.setColor(ROW_HIGHLIGHT);
                shapeRenderer.rect(SLOT_ROW_X, rowBottom, SLOT_ROW_W, SLOT_ROW_H);
                // SWAP button background
                float swapBtnX = SLOT_ROW_X + SLOT_ROW_W - SWAP_BTN_W;
                float swapBtnY = rowBottom + (SLOT_ROW_H - SWAP_BTN_H) / 2f;
                shapeRenderer.setColor(BTN_SWAP_COLOR);
                shapeRenderer.rect(swapBtnX, swapBtnY, SWAP_BTN_W, SWAP_BTN_H);
            }
            rowTop = rowBottom - SLOT_ROW_GAP;
        }
    }

    // ── Pass B: line shapes ───────────────────────────────────────────────────

    private void renderLineShapes(OrthographicCamera camera) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        // Card border in tier color (3px effect via drawing two lines)
        shapeRenderer.setColor(cachedTierRed, cachedTierGreen, cachedTierBlue, 1f);
        shapeRenderer.rect(CARD_X, CARD_Y, CARD_W, CARD_H);
        shapeRenderer.rect(CARD_X + 1f, CARD_Y + 1f, CARD_W - 2f, CARD_H - 2f);

        // Header divider in tier color
        shapeRenderer.line(CARD_X, HEADER_Y, CARD_RIGHT, HEADER_Y);

        // Stat zone divider lines
        shapeRenderer.setColor(DIVIDER_COLOR);
        shapeRenderer.line(CENTER_X, ABILITY_TOP - 4f, CENTER_X, HEADER_Y - 4f);
        shapeRenderer.line(CARD_X, ABILITY_TOP, CARD_RIGHT, ABILITY_TOP);
        shapeRenderer.line(CARD_X, ACTION_TOP, CARD_RIGHT, ACTION_TOP);
        shapeRenderer.line(CARD_X, FOOTER_TOP, CARD_RIGHT, FOOTER_TOP);

        // Action zone: EQUIP / REPLACE border or slot row borders
        if (singleButtonMode()) {
            if (alreadyHeld) {
                shapeRenderer.setColor(AMBER_COLOR);
            } else {
                shapeRenderer.setColor(GREEN_COLOR);
            }
            shapeRenderer.rect(EQUIP_BTN_X, EQUIP_BTN_Y, EQUIP_BTN_W, EQUIP_BTN_H);
        } else {
            renderSlotRowsLines();
        }

        // Footer button borders
        shapeRenderer.setColor(BTN_BORDER_COLOR);
        shapeRenderer.rect(CLOSE_BTN_X, CLOSE_BTN_Y, CLOSE_BTN_W, CLOSE_BTN_H);
        if (hasConvert) {
            shapeRenderer.setColor(AMBER_COLOR.r, AMBER_COLOR.g, AMBER_COLOR.b, 0.6f);
            shapeRenderer.rect(CONV_BTN_X, CONV_BTN_Y, CONV_BTN_W, CONV_BTN_H);
        }

        shapeRenderer.end();
    }

    private void renderSlotRowsLines() {
        if (loadout == null) return;
        float rowTop = SLOT_ROW_TOP;
        for (int slotIndex = 0; slotIndex < loadout.getSlotCount(); slotIndex++) {
            float rowBottom = rowTop - SLOT_ROW_H;
            if (loadout.getSlot(slotIndex) != null) {
                // Active slot gets amber edge highlight
                if (slotIndex == activeSlotIndex) {
                    shapeRenderer.setColor(ACTIVE_SLOT_EDGE);
                    shapeRenderer.line(SLOT_ROW_X, rowBottom, SLOT_ROW_X, rowTop);
                    shapeRenderer.line(SLOT_ROW_X + 2f, rowBottom, SLOT_ROW_X + 2f, rowTop);
                }
                shapeRenderer.setColor(BTN_BORDER_COLOR);
                shapeRenderer.rect(SLOT_ROW_X, rowBottom, SLOT_ROW_W, SLOT_ROW_H);
                float swapBtnX = SLOT_ROW_X + SLOT_ROW_W - SWAP_BTN_W;
                float swapBtnY = rowBottom + (SLOT_ROW_H - SWAP_BTN_H) / 2f;
                shapeRenderer.setColor(AMBER_COLOR.r, AMBER_COLOR.g, AMBER_COLOR.b, 0.8f);
                shapeRenderer.rect(swapBtnX, swapBtnY, SWAP_BTN_W, SWAP_BTN_H);
            }
            rowTop = rowBottom - SLOT_ROW_GAP;
        }
    }

    // ── Pass C: text ──────────────────────────────────────────────────────────

    private void renderText(OrthographicCamera camera) {
        spriteBatch.setProjectionMatrix(camera.combined);
        spriteBatch.begin();

        drawHeader();
        drawStatBlock();
        drawAbilityStrip();
        drawActionZone();
        drawFooter();

        spriteBatch.end();
    }

    private void drawHeader() {
        // Weapon name (tier color, left-aligned after tier icon)
        float nameX = CARD_X + 52f;
        font.setColor(cachedTierRed, cachedTierGreen, cachedTierBlue, 1f);
        font.draw(spriteBatch, cachedWeaponName, nameX, HEADER_Y + HEADER_H - 12f);

        // Level/tier tag (right-aligned in header)
        glyphLayout.setText(font, cachedLevelTier);
        font.setColor(cachedTierRed, cachedTierGreen, cachedTierBlue, 0.85f);
        font.draw(spriteBatch, cachedLevelTier,
                  CARD_RIGHT - 16f - glyphLayout.width, HEADER_Y + HEADER_H - 12f);
    }

    private void drawStatBlock() {
        // Column headers
        float headerY = STATS_TOP - 10f;

        glyphLayout.setText(font, "FOUND");
        font.setColor(WHITE_COLOR);
        font.draw(spriteBatch, "FOUND", GROUND_COL_X, headerY);

        glyphLayout.setText(font, "STAT");
        font.setColor(DIM_COLOR);
        font.draw(spriteBatch, "STAT", CENTER_X - glyphLayout.width / 2f, headerY);

        glyphLayout.setText(font, "ACTIVE");
        font.setColor(WHITE_COLOR);
        font.draw(spriteBatch, "ACTIVE", ACTIVE_COL_X - glyphLayout.width, headerY);

        // Four stat rows
        float rowY = STATS_TOP - STAT_ROW_H;
        drawStatRow(rowY, "DAMAGE", cachedGroundDamage, cachedActiveDamage, false);
        rowY -= STAT_ROW_H;
        drawStatRow(rowY, "CLIP",   cachedGroundClip,   cachedActiveClip,   false);
        rowY -= STAT_ROW_H;
        drawStatRow(rowY, "RELOAD", cachedGroundReload, cachedActiveReload, true);
        rowY -= STAT_ROW_H;
        drawStatRow(rowY, "RANGE",  cachedGroundRange,  cachedActiveRange,  false);
    }

    private void drawStatRow(float rowY, String label,
                             int groundValue, int activeValue, boolean lowerIsBetter) {
        // Center label
        glyphLayout.setText(font, label);
        font.setColor(DIM_COLOR);
        font.draw(spriteBatch, label, CENTER_X - glyphLayout.width / 2f, rowY);

        // Ground value (color-coded vs active)
        textBuilder.setLength(0);
        textBuilder.append(groundValue);
        font.setColor(deltaColor(groundValue, activeValue, lowerIsBetter));
        font.draw(spriteBatch, textBuilder, GROUND_COL_X, rowY);

        // Active value
        textBuilder.setLength(0);
        textBuilder.append(activeValue);
        glyphLayout.setText(font, textBuilder);
        font.setColor(WHITE_COLOR);
        font.draw(spriteBatch, textBuilder, ACTIVE_COL_X - glyphLayout.width, rowY);

        // Delta indicator between ground value and label
        if (activeValue > 0 && groundValue > 0 && groundValue != activeValue) {
            boolean groundBetter = lowerIsBetter ? (groundValue < activeValue) : (groundValue > activeValue);
            String arrow = groundBetter ? " ▲" : " ▼";
            font.setColor(groundBetter ? GREEN_COLOR : RED_COLOR);
            glyphLayout.setText(font, textBuilder);
            font.draw(spriteBatch, arrow, GROUND_COL_X + glyphLayout.width + 4f, rowY);
        }
    }

    private Color deltaColor(int groundValue, int activeValue, boolean lowerIsBetter) {
        if (activeValue <= 0 || groundValue == activeValue) return WHITE_COLOR;
        boolean groundBetter = lowerIsBetter ? (groundValue < activeValue) : (groundValue > activeValue);
        return groundBetter ? GREEN_COLOR : RED_COLOR;
    }

    private void drawAbilityStrip() {
        float abilityY = ABILITY_Y + ABILITY_H - 16f;
        if (cachedAbilityCount == 0) {
            glyphLayout.setText(font, "— no abilities —");
            font.setColor(DIM_COLOR);
            font.draw(spriteBatch, "— no abilities —",
                      CENTER_X - glyphLayout.width / 2f, abilityY);
            return;
        }

        // Draw abilities as a horizontal strip of short tags
        StringBuilder abilityLine = new StringBuilder(64);
        for (int abilityIndex = 0; abilityIndex < cachedAbilityCount; abilityIndex++) {
            if (abilityIndex > 0) abilityLine.append("   ");
            if (cachedAbilityLegendary[abilityIndex]) abilityLine.append("★ ");
            abilityLine.append(cachedAbilityLines[abilityIndex]);
        }
        String abilityText = abilityLine.toString();
        glyphLayout.setText(font, abilityText);
        font.setColor(cachedTierRed, cachedTierGreen, cachedTierBlue, 1f);
        font.draw(spriteBatch, abilityText,
                  CENTER_X - glyphLayout.width / 2f, abilityY);
    }

    private void drawActionZone() {
        if (singleButtonMode()) {
            // Single full-width button: REPLACE when the type is already held, otherwise EQUIP.
            String actionLabel;
            if (alreadyHeld) {
                actionLabel = "SWAP  →  REPLACE HELD";
            } else if (startRoomMode) {
                actionLabel = "CHOOSE THIS WEAPON";
            } else {
                actionLabel = "EQUIP  →  SLOT " + (freeSlotIndex + 1);
            }
            glyphLayout.setText(font, actionLabel);
            font.setColor(alreadyHeld ? AMBER_COLOR : GREEN_COLOR);
            font.draw(spriteBatch, actionLabel,
                      EQUIP_BTN_X + (EQUIP_BTN_W - glyphLayout.width) / 2f,
                      EQUIP_BTN_Y + (EQUIP_BTN_H + glyphLayout.height) / 2f);
        } else {
            drawSlotRowsText();
        }
    }

    private void drawSlotRowsText() {
        if (loadout == null) return;
        float rowTop = SLOT_ROW_TOP;
        for (int slotIndex = 0; slotIndex < loadout.getSlotCount(); slotIndex++) {
            float rowBottom = rowTop - SLOT_ROW_H;
            Weapon slotWeapon = loadout.getSlot(slotIndex);
            if (slotWeapon != null) {
                float nameY  = rowBottom + SLOT_ROW_H - 14f;
                float statsY = nameY - 20f;

                // Slot index + weapon name
                textBuilder.setLength(0);
                textBuilder.append(slotIndex == activeSlotIndex ? "* " : "  ");
                textBuilder.append("SLOT ").append(slotIndex + 1).append("  ");
                textBuilder.append(slotWeapon.getDisplayName().toUpperCase());
                textBuilder.append("  Lv ").append(slotWeapon.getWeaponLevel());
                WeaponTier slotTier = slotWeapon.getTier();
                if (slotTier != null) {
                    temporaryColor.set(slotTier.colorRed, slotTier.colorGreen, slotTier.colorBlue, 1f);
                    font.setColor(temporaryColor);
                } else {
                    font.setColor(AMBER_COLOR);
                }
                font.draw(spriteBatch, textBuilder, SLOT_ROW_X + 12f, nameY);

                // Compact stats with comparison tinting
                int slotDmg   = slotWeapon.getEffectiveDamage();
                int slotRange = slotWeapon.getEffectiveRange();
                int slotClip  = slotWeapon.getEffectiveClipSize();
                textBuilder.setLength(0);
                textBuilder.append("DMG ").append(slotDmg)
                           .append("   RNG ").append(slotRange)
                           .append("   CLIP ").append(slotClip);
                font.setColor(DIM_COLOR);
                font.draw(spriteBatch, textBuilder, SLOT_ROW_X + 12f, statsY);

                // SWAP button label
                float swapBtnX = SLOT_ROW_X + SLOT_ROW_W - SWAP_BTN_W;
                float swapBtnY = rowBottom + (SLOT_ROW_H - SWAP_BTN_H) / 2f;
                String swapLabel = "SWAP →";
                glyphLayout.setText(font, swapLabel);
                font.setColor(AMBER_COLOR);
                font.draw(spriteBatch, swapLabel,
                          swapBtnX + (SWAP_BTN_W - glyphLayout.width) / 2f,
                          swapBtnY + (SWAP_BTN_H + glyphLayout.height) / 2f);
            }
            rowTop = rowBottom - SLOT_ROW_GAP;
        }
    }

    private void drawFooter() {
        // CLOSE
        String closeLabel = "CLOSE";
        glyphLayout.setText(font, closeLabel);
        font.setColor(DIM_COLOR);
        font.draw(spriteBatch, closeLabel,
                  CLOSE_BTN_X + (CLOSE_BTN_W - glyphLayout.width) / 2f,
                  CLOSE_BTN_Y + (CLOSE_BTN_H + glyphLayout.height) / 2f);

        // CONVERT AMMO
        if (hasConvert) {
            glyphLayout.setText(font, cachedConvertLabel);
            font.setColor(AMBER_COLOR.r, AMBER_COLOR.g, AMBER_COLOR.b, 0.8f);
            font.draw(spriteBatch, cachedConvertLabel,
                      CONV_BTN_X + (CONV_BTN_W - glyphLayout.width) / 2f,
                      CONV_BTN_Y + (CONV_BTN_H + glyphLayout.height) / 2f);
        }
    }

    // ── Disposable ────────────────────────────────────────────────────────────

    @Override
    public void dispose() {
        shapeRenderer.dispose();
        spriteBatch.dispose();
        font.dispose();
    }

    // ── Helpers (called from show(), no LibGDX render state) ──────────────────

    private void buildStatCache(WeaponRoll groundRoll, Weapon arsenalWeapon, Weapon activeWeapon) {
        if (arsenalWeapon != null && groundRoll != null) {
            int level = groundRoll.weaponLevel;
            cachedGroundDamage = GameMath.weaponScaledDamage(arsenalWeapon.getBaseDamage(), level);
            cachedGroundClip   = GameMath.weaponScaledClipSize(arsenalWeapon.getBaseClipSize(), level);
            cachedGroundReload = GameMath.weaponScaledReloadTicks(arsenalWeapon.getBaseReloadTicks(), level);
            cachedGroundRange  = GameMath.weaponScaledRange(arsenalWeapon.getBaseRange(), level, arsenalWeapon.isMelee());
        } else if (arsenalWeapon != null) {
            cachedGroundDamage = arsenalWeapon.getBaseDamage();
            cachedGroundClip   = arsenalWeapon.getBaseClipSize();
            cachedGroundReload = arsenalWeapon.getBaseReloadTicks();
            cachedGroundRange  = arsenalWeapon.getBaseRange();
        } else {
            cachedGroundDamage = 0;
            cachedGroundClip   = 0;
            cachedGroundReload = 0;
            cachedGroundRange  = 0;
        }

        if (activeWeapon != null) {
            cachedActiveDamage = activeWeapon.getEffectiveDamage();
            cachedActiveClip   = activeWeapon.getEffectiveClipSize();
            cachedActiveReload = activeWeapon.getEffectiveReloadTicks();
            cachedActiveRange  = activeWeapon.getEffectiveRange();
        } else {
            cachedActiveDamage = 0;
            cachedActiveClip   = 0;
            cachedActiveReload = 0;
            cachedActiveRange  = 0;
        }
    }

    private void buildAbilityCache(WeaponRoll groundRoll) {
        cachedAbilityCount = 0;
        if (groundRoll == null || groundRoll.abilities == null) return;
        for (int abilityIndex = 0;
             abilityIndex < groundRoll.abilities.length && cachedAbilityCount < MAX_ABILITY_LINES;
             abilityIndex++) {
            AbilityInstance inst = groundRoll.abilities[abilityIndex];
            if (inst == null) continue;
            cachedAbilityLines[cachedAbilityCount]     = formatAbilityShort(inst);
            cachedAbilityLegendary[cachedAbilityCount] = inst.ability.legendaryOnly;
            cachedAbilityCount++;
        }
    }

    private static String formatAbilityShort(AbilityInstance inst) {
        switch (inst.ability) {
            case BURST_FIRE:         return "BURST x" + inst.countValue;
            case CRITICAL_STRIKE:    return "CRIT " + inst.magnitudePercent() + "%";
            case ARMOR_PIERCE:       return "PIERCE " + inst.magnitudePercent() + "%";
            case EXECUTIONER:        return "EXECUTE " + inst.magnitudePercent() + "%";
            case REND:               return "BLEED " + inst.countValue + "/T";
            case OVERPENETRATION:    return "OVERPENETRATE";
            case STAGGER_ROUNDS:     return "STUN " + inst.magnitudePercent() + "%";
            case KINETIC_SLAM:       return "KNOCKBACK " + inst.magnitudePercent() + "%";
            case CLEAVE:             return "CLEAVE " + inst.magnitudePercent() + "%";
            case INCENDIARY:         return "BURN " + inst.countValue + "/T";
            case LIFESTEAL:          return "LIFESTEAL " + inst.magnitudePercent() + "%";
            case HEMORRHAGE_HARVEST: return "HP/KILL +" + inst.countValue;
            case VAMPIRIC_CRIT:      return "CRIT HEAL +" + inst.countValue;
            case ADRENAL_SURGE:      return "SURGE ON KILL";
            case BULWARK_ROUNDS:     return "SHIELD +" + inst.countValue;
            case SECOND_WIND:        return "SECOND WIND " + inst.magnitudePercent() + "%";
            case SCAVENGER_ROUNDS:   return "AMMO DROP " + inst.magnitudePercent() + "%";
            case SALVAGE_STRIKE:     return "AMMO DROP " + inst.magnitudePercent() + "%";
            case SCHOLARS_EDGE:      return "XP/KILL +" + inst.magnitudePercent() + "%";
            case QUICK_HANDS:        return "QUICK RELOAD";
            case EXTENDED_MAG:       return "MAG +" + inst.countValue;
            case FIELD_MEDIC_ROUNDS: return "HEAL/KILL +" + inst.countValue;
            case CREDIT_FANG:        return "CREDITS/KILL";
            case POINT_BLANK:        return "POINT BLANK " + inst.magnitudePercent() + "%";
            case MARKSMANS_PATIENCE: return "PATIENCE " + inst.magnitudePercent() + "%";
            case OPENING_SALVO:      return "FIRST SHOT +" + inst.magnitudePercent() + "%";
            case RHYTHM:             return "RHYTHM " + inst.magnitudePercent() + "%";
            case STATIC_DISCHARGE:   return "STATIC " + inst.countValue;
            case RESONANT_ROUNDS:    return "RESONANCE " + inst.magnitudePercent() + "%";
            case SOULFORGE:          return "SOULFORGE";
            case JUDGMENT:           return "LANCE/" + inst.countValue;
            case HELLFIRE_NOVA:      return "CRIT NOVA " + inst.magnitudePercent() + "%";
            case BERSERKERS_OATH:    return "BERSERK";
            default:                 return inst.ability.displayName.toUpperCase();
        }
    }

    private static int findFreeSlot(Loadout loadoutRef) {
        if (loadoutRef == null) return 0;
        for (int slotIndex = 0; slotIndex < loadoutRef.getSlotCount(); slotIndex++) {
            // Skip locked slots — tryEquip() never assigns there, so offering one as the
            // "free" target would produce an EQUIP button that silently fails.
            if (loadoutRef.isSlotLocked(slotIndex)) continue;
            if (loadoutRef.getSlot(slotIndex) == null) return slotIndex;
        }
        return 0;
    }
}
