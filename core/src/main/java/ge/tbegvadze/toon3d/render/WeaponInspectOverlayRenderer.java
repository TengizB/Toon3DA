package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
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
 * Single-screen weapon pickup / compare card — redesigned for a high-resolution,
 * large-text, non-overlapping layout.
 *
 * Vertical zone stack (top → bottom), each a fixed non-overlapping band:
 *   HEADER   — tier badge + weapon name + level/tier tag.
 *   STATS    — FOUND / STAT / ACTIVE comparison table, 4 rows, numeric deltas.
 *   ABILITY  — wrapped strip of the found weapon's ability tags.
 *   ACTION   — one large EQUIP/REPLACE/CHOOSE button, or inline SWAP rows when the loadout is full.
 *   FOOTER   — CLOSE (left) and optional CONVERT AMMO (right).
 *
 * Text quality: the built-in {@link BitmapFont} glyph atlas is switched to linear
 * filtering so it stays crisp when scaled up (the default Nearest filter is what made
 * the old card look blocky). Every text element is drawn through {@link #drawCentered}
 * / {@link #drawAligned} which measure the glyphs and place them inside their band, so
 * elements can never overlap regardless of string length.
 *
 * No allocations in render() — all strings and hit-test rects are pre-built in show().
 */
public final class WeaponInspectOverlayRenderer implements Renderable, Disposable {

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final Color SCRIM_COLOR      = new Color(0f,     0f,     0f,     0.72f);
    private static final Color PANEL_COLOR       = new Color(0.055f, 0.058f, 0.072f, HudConstants.WEAPON_CARD_PANEL_ALPHA);
    private static final Color HEADER_COLOR      = new Color(0.10f,  0.10f,  0.13f,  1f);
    private static final Color ZONE_BG_COLOR     = new Color(0.075f, 0.078f, 0.095f, 1f);
    private static final Color ACTION_BG_COLOR   = new Color(0.09f,  0.09f,  0.11f,  1f);
    private static final Color BTN_EQUIP_COLOR   = new Color(0.07f,  0.30f,  0.09f,  1f);
    private static final Color BTN_SWAP_COLOR    = new Color(0.24f,  0.17f,  0.05f,  1f);
    private static final Color BTN_NEUTRAL_COLOR = new Color(0.16f,  0.16f,  0.19f,  1f);
    private static final Color BTN_CONVERT_COLOR = new Color(0.16f,  0.11f,  0.04f,  1f);
    private static final Color BTN_BORDER_COLOR  = new Color(0.42f,  0.42f,  0.48f,  1f);
    private static final Color DIVIDER_COLOR     = new Color(0.24f,  0.24f,  0.29f,  1f);
    private static final Color ROW_BG_COLOR      = new Color(0.11f,  0.115f, 0.135f, 1f);
    private static final Color AMBER_COLOR       = new Color(0.95f,  0.72f,  0.20f,  1f);
    private static final Color WHITE_COLOR       = new Color(0.96f,  0.96f,  0.98f,  1f);
    private static final Color DIM_COLOR         = new Color(0.62f,  0.62f,  0.68f,  1f);
    private static final Color GREEN_COLOR       = new Color(0.34f,  0.86f,  0.38f,  1f);
    private static final Color RED_COLOR         = new Color(0.95f,  0.34f,  0.34f,  1f);
    private static final Color ACTIVE_SLOT_EDGE  = new Color(0.95f,  0.72f,  0.20f,  0.85f);

    // ── Font scales (relative sizes for each text element) ────────────────────
    private static final float FS_TITLE    = 2.0f;
    private static final float FS_TIER_TAG = 1.25f;
    private static final float FS_COL_HEAD = 1.15f;
    private static final float FS_STAT     = 1.55f;
    private static final float FS_DELTA    = 1.0f;
    private static final float FS_ABILITY  = 1.2f;
    private static final float FS_ZONE_TAG = 0.95f;
    private static final float FS_BUTTON   = 1.8f;
    private static final float FS_SLOT_NAME= 1.35f;
    private static final float FS_SLOT_STAT= 1.05f;
    private static final float FS_SWAP     = 1.3f;
    private static final float FS_FOOTER   = 1.45f;

    // ── Card box (from HudConstants) ──────────────────────────────────────────
    private static final float CARD_X     = HudConstants.WEAPON_CARD_ORIGIN_X;   // 190
    private static final float CARD_Y     = HudConstants.WEAPON_CARD_ORIGIN_Y;   // 50
    private static final float CARD_W     = HudConstants.WEAPON_CARD_WIDTH;      // 900
    private static final float CARD_H     = HudConstants.WEAPON_CARD_HEIGHT;     // 620
    private static final float CARD_RIGHT = CARD_X + CARD_W;                     // 1090
    private static final float CARD_TOP   = CARD_Y + CARD_H;                     // 670
    private static final float CENTER_X   = CARD_X + CARD_W / 2f;               // 640
    private static final float INNER_PAD  = 26f;

    // ── Zone bands (Y-up; each band is [bottom, top]) ─────────────────────────
    // Header
    private static final float HEADER_H = HudConstants.WEAPON_CARD_HEADER_HEIGHT; // 78
    private static final float HEADER_Y = CARD_TOP - HEADER_H;                    // 592

    // Footer (bottom of card)
    private static final float FOOTER_MARGIN = 8f;
    private static final float FOOTER_H   = HudConstants.WEAPON_CARD_FOOTER_H;    // 92
    private static final float FOOTER_Y   = CARD_Y + FOOTER_MARGIN;              // 58
    private static final float FOOTER_TOP = FOOTER_Y + FOOTER_H;                 // 150

    // Action zone
    private static final float ACTION_H   = HudConstants.WEAPON_CARD_ACTION_H;    // 176
    private static final float ACTION_Y   = FOOTER_TOP + 8f;                     // 158
    private static final float ACTION_TOP = ACTION_Y + ACTION_H;                 // 334

    // Ability strip
    private static final float ABILITY_H   = HudConstants.WEAPON_CARD_ABILITY_H; // 66
    private static final float ABILITY_Y   = ACTION_TOP + 4f;                    // 338
    private static final float ABILITY_TOP = ABILITY_Y + ABILITY_H;             // 404

    // Stats table fills the gap between the ability strip top and the header.
    private static final float STATS_TOP    = HEADER_Y;                          // 592
    private static final float STATS_BOTTOM = ABILITY_TOP;                       // 404
    private static final float COL_HEAD_BAND_H = 44f;                            // column-header row
    private static final int   STAT_ROW_COUNT  = 4;
    // Stat column centres
    private static final float FOUND_COL_X  = CARD_X + 150f;                     // 340
    private static final float ACTIVE_COL_X = CARD_RIGHT - 150f;                 // 940

    // Equip button (free-slot fast lane)
    private static final float EQUIP_BTN_W = HudConstants.WEAPON_EQUIP_BUTTON_WIDTH;  // 640
    private static final float EQUIP_BTN_H = HudConstants.WEAPON_EQUIP_BUTTON_HEIGHT; // 104
    private static final float EQUIP_BTN_X = CARD_X + (CARD_W - EQUIP_BTN_W) / 2f;    // 320
    private static final float EQUIP_BTN_Y = ACTION_Y + (ACTION_H - EQUIP_BTN_H) / 2f;// 194

    // Slot rows (full loadout)
    private static final float SLOT_ROW_H   = HudConstants.WEAPON_SLOT_ROW_HEIGHT; // 74
    private static final float SLOT_ROW_GAP = HudConstants.WEAPON_SLOT_ROW_GAP;   // 14
    private static final float SLOT_ROW_X   = CARD_X + 16f;                       // 206
    private static final float SLOT_ROW_W   = CARD_W - 32f;                       // 868
    private static final float SLOT_ROW_TOP = ACTION_TOP - 8f;                    // 326 (top of first row)
    private static final float SWAP_BTN_W   = HudConstants.WEAPON_SWAP_BUTTON_WIDTH;  // 176
    private static final float SWAP_BTN_H   = HudConstants.WEAPON_SWAP_BUTTON_HEIGHT; // 56
    private static final float SWAP_BTN_PAD = 14f; // inset of the SWAP button from the slot row's right edge

    // Footer buttons
    private static final float CLOSE_BTN_W = HudConstants.WEAPON_CLOSE_BUTTON_WIDTH;   // 190
    private static final float CLOSE_BTN_H = HudConstants.WEAPON_CLOSE_BUTTON_HEIGHT;  // 64
    private static final float CLOSE_BTN_X = CARD_X + INNER_PAD;                       // 216
    private static final float CLOSE_BTN_Y = FOOTER_Y + (FOOTER_H - CLOSE_BTN_H) / 2f; // 72

    private static final float CONV_BTN_W = HudConstants.WEAPON_CONVERT_BUTTON_WIDTH;  // 300
    private static final float CONV_BTN_H = HudConstants.WEAPON_CONVERT_BUTTON_HEIGHT; // 64
    private static final float CONV_BTN_X = CARD_RIGHT - INNER_PAD - CONV_BTN_W;       // 764
    private static final float CONV_BTN_Y = FOOTER_Y + (FOOTER_H - CONV_BTN_H) / 2f;   // 72

    private static final int MAX_ABILITY_LINES = 5;

    // ── Owned resources ───────────────────────────────────────────────────────
    private final ShapeRenderer shapeRenderer;
    private final SpriteBatch   spriteBatch;
    private final BitmapFont    font;
    private final GlyphLayout   glyphLayout;

    // ── State (written by show()) ─────────────────────────────────────────────
    private boolean  visible        = false;
    private boolean  startRoomMode  = false;
    private boolean  meleeMode      = false;
    private boolean  loadoutFull    = false;
    private boolean  alreadyHeld    = false;
    private boolean  hasConvert     = false;
    private int      freeSlotIndex  = -1;
    private Loadout  loadout        = null;
    private int      activeSlotIndex = 0;

    // Displayed (non-null) slot indices, packed so render + hit-test agree exactly.
    private final int[] displayedSlotIndices = new int[8];
    private int         displayedSlotCount   = 0;

    // ── Cached strings and stats (built in show(), read in render()) ──────────
    private String   cachedWeaponName   = "";
    private String   cachedLevelTier    = "";
    private float    cachedTierRed      = AMBER_COLOR.r;
    private float    cachedTierGreen    = AMBER_COLOR.g;
    private float    cachedTierBlue     = AMBER_COLOR.b;
    private WeaponTier cachedTier       = WeaponTier.COMMON;

    private int cachedGroundDamage  = 0;
    private int cachedGroundClip    = 0;
    private int cachedGroundReload  = 0;
    private int cachedGroundRange   = 0;

    private int cachedActiveDamage  = 0;
    private int cachedActiveClip    = 0;
    private int cachedActiveReload  = 0;
    private int cachedActiveRange   = 0;

    private String cachedAbilityStrip = "";

    private String cachedConvertLabel = "CONVERT AMMO";

    // ── Callbacks ─────────────────────────────────────────────────────────────
    private Runnable    onEquipFreeSlot  = null;
    private IntConsumer onSwapSlot       = null;
    private Runnable    onConvertToAmmo  = null;
    private Runnable    onClose          = null;

    // ── Scratch — pre-allocated ───────────────────────────────────────────────
    private final StringBuilder textBuilder    = new StringBuilder(96);
    private final Color         temporaryColor = new Color();

    // ── Constructor ───────────────────────────────────────────────────────────

    public WeaponInspectOverlayRenderer() {
        shapeRenderer = new ShapeRenderer();
        spriteBatch   = new SpriteBatch();
        font          = new BitmapFont();
        // Linear filtering keeps the bitmap glyphs smooth when scaled up — this is the
        // single biggest fix for the old "low resolution" look.
        font.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        font.setUseIntegerPositions(false);
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
     * @param melee         true when the ground weapon is melee (forces the single EQUIP→MELEE button)
     */
    public void show(GroundItem groundItem, WeaponRoll groundRoll,
                     Weapon arsenalWeapon, Weapon activeWeapon,
                     Loadout loadoutRef, boolean startRoom, int convertAmount, boolean melee) {
        this.loadout       = loadoutRef;
        this.startRoomMode = startRoom;
        this.meleeMode     = melee;
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
        cachedLevelTier  = "LV " + level + "  •  " + tier.displayName.toUpperCase();

        buildStatCache(groundRoll, arsenalWeapon, activeWeapon);
        buildAbilityStrip(groundRoll);

        hasConvert         = convertAmount > 0;
        cachedConvertLabel = hasConvert ? ("CONVERT  +" + convertAmount + " AMMO") : "CONVERT AMMO";

        // Determine loadout state for action-zone layout.
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

        // Pack the non-null slot indices so rows are drawn contiguously (no gap for locked slots).
        displayedSlotCount = 0;
        if (loadoutRef != null && !singleButtonMode()) {
            for (int slotIndex = 0;
                 slotIndex < loadoutRef.getSlotCount() && displayedSlotCount < displayedSlotIndices.length;
                 slotIndex++) {
                if (loadoutRef.getSlot(slotIndex) != null) {
                    displayedSlotIndices[displayedSlotCount++] = slotIndex;
                }
            }
        }
    }

    /** True when the action zone shows a single full-width button rather than the SWAP slot rows. */
    private boolean singleButtonMode() {
        return !loadoutFull || alreadyHeld || meleeMode;
    }

    /** Closes the card and clears all held references. */
    public void hide() {
        visible      = false;
        alreadyHeld  = false;
        meleeMode    = false;
        loadout      = null;
        displayedSlotCount = 0;
        cachedAbilityStrip = "";
    }

    public boolean isVisible() { return visible; }

    /** Updates the facility clock for any pulsing effects. Currently a no-op placeholder. */
    public void setFacilityTime(float time) { /* reserved for future pulsing effects */ }

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
            if (hitTest(worldX, worldY, EQUIP_BTN_X, EQUIP_BTN_Y, EQUIP_BTN_W, EQUIP_BTN_H)) {
                if (onEquipFreeSlot != null) onEquipFreeSlot.run();
            }
        } else {
            hitTestSlotRows(worldX, worldY);
        }
    }

    private void hitTestSlotRows(float worldX, float worldY) {
        for (int rowPosition = 0; rowPosition < displayedSlotCount; rowPosition++) {
            float rowTop    = slotRowTop(rowPosition);
            float rowBottom = rowTop - SLOT_ROW_H;
            if (worldX >= SLOT_ROW_X && worldX <= SLOT_ROW_X + SLOT_ROW_W
                    && worldY >= rowBottom && worldY <= rowTop) {
                if (onSwapSlot != null) onSwapSlot.accept(displayedSlotIndices[rowPosition]);
                return;
            }
        }
    }

    private static float slotRowTop(int rowPosition) {
        return SLOT_ROW_TOP - rowPosition * (SLOT_ROW_H + SLOT_ROW_GAP);
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

        // Header background + tier accent band
        shapeRenderer.setColor(HEADER_COLOR);
        shapeRenderer.rect(CARD_X, HEADER_Y, CARD_W, HEADER_H);
        shapeRenderer.setColor(cachedTierRed, cachedTierGreen, cachedTierBlue, 0.9f);
        shapeRenderer.rect(CARD_X, CARD_TOP - 5f, CARD_W, 5f); // slim tier stripe along the very top
        drawTierIconFilled(cachedTier, CARD_X + 40f, HEADER_Y + HEADER_H / 2f, 15f);

        // Ability strip background
        shapeRenderer.setColor(ZONE_BG_COLOR);
        shapeRenderer.rect(CARD_X, ABILITY_Y, CARD_W, ABILITY_H);

        // Action zone background
        shapeRenderer.setColor(ACTION_BG_COLOR);
        shapeRenderer.rect(CARD_X, ACTION_Y, CARD_W, ACTION_H);

        // Action content
        if (singleButtonMode()) {
            shapeRenderer.setColor(alreadyHeld ? BTN_SWAP_COLOR : BTN_EQUIP_COLOR);
            shapeRenderer.rect(EQUIP_BTN_X, EQUIP_BTN_Y, EQUIP_BTN_W, EQUIP_BTN_H);
        } else {
            renderSlotRowsFilled();
        }

        // Footer strip background + buttons
        shapeRenderer.setColor(ZONE_BG_COLOR);
        shapeRenderer.rect(CARD_X, FOOTER_Y - FOOTER_MARGIN, CARD_W, FOOTER_H + FOOTER_MARGIN);
        shapeRenderer.setColor(BTN_NEUTRAL_COLOR);
        shapeRenderer.rect(CLOSE_BTN_X, CLOSE_BTN_Y, CLOSE_BTN_W, CLOSE_BTN_H);
        if (hasConvert) {
            shapeRenderer.setColor(BTN_CONVERT_COLOR);
            shapeRenderer.rect(CONV_BTN_X, CONV_BTN_Y, CONV_BTN_W, CONV_BTN_H);
        }

        shapeRenderer.end();
    }

    private void drawTierIconFilled(WeaponTier tier, float centerX, float centerY, float size) {
        shapeRenderer.setColor(cachedTierRed, cachedTierGreen, cachedTierBlue, 1f);
        switch (tier) {
            case COMMON:
                shapeRenderer.circle(centerX, centerY, size, 24);
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
                shapeRenderer.circle(centerX, centerY, size, 6);
                break;
        }
    }

    private void renderSlotRowsFilled() {
        for (int rowPosition = 0; rowPosition < displayedSlotCount; rowPosition++) {
            float rowTop    = slotRowTop(rowPosition);
            float rowBottom = rowTop - SLOT_ROW_H;
            shapeRenderer.setColor(ROW_BG_COLOR);
            shapeRenderer.rect(SLOT_ROW_X, rowBottom, SLOT_ROW_W, SLOT_ROW_H);
            float swapBtnX = SLOT_ROW_X + SLOT_ROW_W - SWAP_BTN_PAD - SWAP_BTN_W;
            float swapBtnY = rowBottom + (SLOT_ROW_H - SWAP_BTN_H) / 2f;
            shapeRenderer.setColor(BTN_SWAP_COLOR);
            shapeRenderer.rect(swapBtnX, swapBtnY, SWAP_BTN_W, SWAP_BTN_H);
        }
    }

    // ── Pass B: line shapes ───────────────────────────────────────────────────

    private void renderLineShapes(OrthographicCamera camera) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        // Card border in tier color (double line for a 2px effect)
        shapeRenderer.setColor(cachedTierRed, cachedTierGreen, cachedTierBlue, 1f);
        shapeRenderer.rect(CARD_X, CARD_Y, CARD_W, CARD_H);
        shapeRenderer.rect(CARD_X + 1f, CARD_Y + 1f, CARD_W - 2f, CARD_H - 2f);

        // Zone dividers
        shapeRenderer.setColor(DIVIDER_COLOR);
        shapeRenderer.line(CARD_X, HEADER_Y,    CARD_RIGHT, HEADER_Y);
        shapeRenderer.line(CARD_X, ABILITY_TOP, CARD_RIGHT, ABILITY_TOP);
        shapeRenderer.line(CARD_X, ACTION_TOP,  CARD_RIGHT, ACTION_TOP);
        shapeRenderer.line(CARD_X, FOOTER_TOP,  CARD_RIGHT, FOOTER_TOP);
        // Vertical rules separating the three stat columns
        shapeRenderer.line(CARD_X + 300f, STATS_BOTTOM + 6f, CARD_X + 300f, STATS_TOP - COL_HEAD_BAND_H + 6f);
        shapeRenderer.line(CARD_X + 600f, STATS_BOTTOM + 6f, CARD_X + 600f, STATS_TOP - COL_HEAD_BAND_H + 6f);

        // Action zone borders
        if (singleButtonMode()) {
            shapeRenderer.setColor(alreadyHeld ? AMBER_COLOR : GREEN_COLOR);
            shapeRenderer.rect(EQUIP_BTN_X, EQUIP_BTN_Y, EQUIP_BTN_W, EQUIP_BTN_H);
            shapeRenderer.rect(EQUIP_BTN_X + 1f, EQUIP_BTN_Y + 1f, EQUIP_BTN_W - 2f, EQUIP_BTN_H - 2f);
        } else {
            renderSlotRowsLines();
        }

        // Footer button borders
        shapeRenderer.setColor(BTN_BORDER_COLOR);
        shapeRenderer.rect(CLOSE_BTN_X, CLOSE_BTN_Y, CLOSE_BTN_W, CLOSE_BTN_H);
        if (hasConvert) {
            shapeRenderer.setColor(AMBER_COLOR.r, AMBER_COLOR.g, AMBER_COLOR.b, 0.7f);
            shapeRenderer.rect(CONV_BTN_X, CONV_BTN_Y, CONV_BTN_W, CONV_BTN_H);
        }

        shapeRenderer.end();
    }

    private void renderSlotRowsLines() {
        for (int rowPosition = 0; rowPosition < displayedSlotCount; rowPosition++) {
            int   slotIndex = displayedSlotIndices[rowPosition];
            float rowTop    = slotRowTop(rowPosition);
            float rowBottom = rowTop - SLOT_ROW_H;

            if (slotIndex == activeSlotIndex) {
                shapeRenderer.setColor(ACTIVE_SLOT_EDGE);
                shapeRenderer.line(SLOT_ROW_X, rowBottom, SLOT_ROW_X, rowTop);
                shapeRenderer.line(SLOT_ROW_X + 2f, rowBottom, SLOT_ROW_X + 2f, rowTop);
                shapeRenderer.line(SLOT_ROW_X + 4f, rowBottom, SLOT_ROW_X + 4f, rowTop);
            }
            shapeRenderer.setColor(BTN_BORDER_COLOR);
            shapeRenderer.rect(SLOT_ROW_X, rowBottom, SLOT_ROW_W, SLOT_ROW_H);
            float swapBtnX = SLOT_ROW_X + SLOT_ROW_W - SWAP_BTN_PAD - SWAP_BTN_W;
            float swapBtnY = rowBottom + (SLOT_ROW_H - SWAP_BTN_H) / 2f;
            shapeRenderer.setColor(AMBER_COLOR);
            shapeRenderer.rect(swapBtnX, swapBtnY, SWAP_BTN_W, SWAP_BTN_H);
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
        font.getData().setScale(1f);
    }

    private void drawHeader() {
        float baseline = HEADER_Y + HEADER_H / 2f;
        temporaryColor.set(cachedTierRed, cachedTierGreen, cachedTierBlue, 1f);
        drawAligned(cachedWeaponName, CARD_X + 68f, baseline, FS_TITLE, temporaryColor, Align.left);
        temporaryColor.set(cachedTierRed, cachedTierGreen, cachedTierBlue, 0.9f);
        drawAligned(cachedLevelTier, CARD_RIGHT - INNER_PAD, baseline, FS_TIER_TAG, temporaryColor, Align.right);
    }

    private void drawStatBlock() {
        // Column headers, vertically centred in their band.
        float colHeadCenter = STATS_TOP - COL_HEAD_BAND_H / 2f;
        drawCentered("FOUND",  FOUND_COL_X,  colHeadCenter, FS_COL_HEAD, WHITE_COLOR);
        drawCentered("STAT",   CENTER_X,     colHeadCenter, FS_COL_HEAD, DIM_COLOR);
        drawCentered("ACTIVE", ACTIVE_COL_X, colHeadCenter, FS_COL_HEAD, WHITE_COLOR);

        // Four stat rows, each vertically centred in its own band (bands never overlap).
        float rowsTop    = STATS_TOP - COL_HEAD_BAND_H;
        float rowBandH   = (rowsTop - STATS_BOTTOM) / STAT_ROW_COUNT;
        drawStatRow(rowsTop, rowBandH, 0, "DAMAGE", cachedGroundDamage, cachedActiveDamage, false);
        drawStatRow(rowsTop, rowBandH, 1, "CLIP",   cachedGroundClip,   cachedActiveClip,   false);
        drawStatRow(rowsTop, rowBandH, 2, "RELOAD", cachedGroundReload, cachedActiveReload, true);
        drawStatRow(rowsTop, rowBandH, 3, "RANGE",  cachedGroundRange,  cachedActiveRange,  false);
    }

    private void drawStatRow(float rowsTop, float rowBandH, int rowIndex,
                             String label, int groundValue, int activeValue, boolean lowerIsBetter) {
        float bandCenter = rowsTop - rowIndex * rowBandH - rowBandH / 2f;

        drawCentered(label, CENTER_X, bandCenter, FS_STAT, DIM_COLOR);

        // FOUND value, color-coded against the active weapon.
        textBuilder.setLength(0);
        textBuilder.append(groundValue);
        drawCentered(textBuilder, FOUND_COL_X, bandCenter, FS_STAT, deltaColor(groundValue, activeValue, lowerIsBetter));

        // ACTIVE value, always neutral white.
        textBuilder.setLength(0);
        textBuilder.append(activeValue);
        drawCentered(textBuilder, ACTIVE_COL_X, bandCenter, FS_STAT, WHITE_COLOR);

        // Numeric delta tag beside the FOUND value (font-safe, no arrow glyphs).
        if (activeValue > 0 && groundValue > 0 && groundValue != activeValue) {
            boolean groundBetter = lowerIsBetter ? (groundValue < activeValue) : (groundValue > activeValue);
            int difference = Math.abs(groundValue - activeValue);
            textBuilder.setLength(0);
            textBuilder.append(groundBetter ? "+" : "-").append(difference);
            drawAligned(textBuilder, FOUND_COL_X + 58f, bandCenter, FS_DELTA,
                        groundBetter ? GREEN_COLOR : RED_COLOR, Align.left);
        }
    }

    private Color deltaColor(int groundValue, int activeValue, boolean lowerIsBetter) {
        if (activeValue <= 0 || groundValue == activeValue) return WHITE_COLOR;
        boolean groundBetter = lowerIsBetter ? (groundValue < activeValue) : (groundValue > activeValue);
        return groundBetter ? GREEN_COLOR : RED_COLOR;
    }

    private void drawAbilityStrip() {
        float bandCenter = ABILITY_Y + ABILITY_H / 2f;
        if (cachedAbilityStrip.isEmpty()) {
            drawCentered("— NO SPECIAL ABILITIES —", CENTER_X, bandCenter, FS_ABILITY, DIM_COLOR);
            return;
        }
        // Wrap the ability tags to the card width and centre the block vertically so a long
        // list flows onto a second line instead of overrunning the card edges.
        font.getData().setScale(FS_ABILITY);
        temporaryColor.set(cachedTierRed, cachedTierGreen, cachedTierBlue, 1f);
        font.setColor(temporaryColor);
        float wrapWidth = CARD_W - 2f * INNER_PAD;
        glyphLayout.setText(font, cachedAbilityStrip, temporaryColor, wrapWidth, Align.center, true);
        float baseline = bandCenter + glyphLayout.height / 2f;
        font.draw(spriteBatch, glyphLayout, CARD_X + INNER_PAD, baseline);
    }

    private void drawActionZone() {
        if (singleButtonMode()) {
            String actionLabel;
            if (startRoomMode) {
                actionLabel = "CHOOSE THIS WEAPON";
            } else if (meleeMode) {
                actionLabel = "EQUIP  →  MELEE";
            } else if (alreadyHeld) {
                actionLabel = "REPLACE HELD WEAPON";
            } else {
                actionLabel = "EQUIP  →  SLOT " + (freeSlotIndex + 1);
            }
            drawInRect(actionLabel, EQUIP_BTN_X, EQUIP_BTN_Y, EQUIP_BTN_W, EQUIP_BTN_H,
                       FS_BUTTON, alreadyHeld ? AMBER_COLOR : WHITE_COLOR);
        } else {
            drawSlotRowsText();
        }
    }

    private void drawSlotRowsText() {
        if (loadout == null) return;
        for (int rowPosition = 0; rowPosition < displayedSlotCount; rowPosition++) {
            int    slotIndex  = displayedSlotIndices[rowPosition];
            Weapon slotWeapon = loadout.getSlot(slotIndex);
            if (slotWeapon == null) continue;

            float rowTop    = slotRowTop(rowPosition);
            float rowBottom = rowTop - SLOT_ROW_H;
            float nameBaseline  = rowBottom + SLOT_ROW_H - 24f;
            float statsBaseline = rowBottom + 22f;

            // Slot label + weapon name (tier-colored).
            textBuilder.setLength(0);
            if (slotIndex == activeSlotIndex) textBuilder.append("[ACTIVE] ");
            textBuilder.append("SLOT ").append(slotIndex + 1).append("   ");
            textBuilder.append(slotWeapon.getDisplayName().toUpperCase());
            textBuilder.append("  LV ").append(slotWeapon.getWeaponLevel());
            WeaponTier slotTier = slotWeapon.getTier();
            if (slotTier != null) {
                temporaryColor.set(slotTier.colorRed, slotTier.colorGreen, slotTier.colorBlue, 1f);
            } else {
                temporaryColor.set(AMBER_COLOR);
            }
            drawAligned(textBuilder, SLOT_ROW_X + 18f, nameBaseline, FS_SLOT_NAME, temporaryColor, Align.left);

            // Compact stat line.
            textBuilder.setLength(0);
            textBuilder.append("DMG ").append(slotWeapon.getEffectiveDamage())
                       .append("     RNG ").append(slotWeapon.getEffectiveRange())
                       .append("     CLIP ").append(slotWeapon.getEffectiveClipSize());
            drawAligned(textBuilder, SLOT_ROW_X + 18f, statsBaseline, FS_SLOT_STAT, DIM_COLOR, Align.left);

            // SWAP button label.
            float swapBtnX = SLOT_ROW_X + SLOT_ROW_W - SWAP_BTN_PAD - SWAP_BTN_W;
            float swapBtnY = rowBottom + (SLOT_ROW_H - SWAP_BTN_H) / 2f;
            drawInRect("SWAP", swapBtnX, swapBtnY, SWAP_BTN_W, SWAP_BTN_H, FS_SWAP, AMBER_COLOR);
        }
    }

    private void drawFooter() {
        drawInRect("CLOSE", CLOSE_BTN_X, CLOSE_BTN_Y, CLOSE_BTN_W, CLOSE_BTN_H, FS_FOOTER, WHITE_COLOR);
        if (hasConvert) {
            temporaryColor.set(AMBER_COLOR.r, AMBER_COLOR.g, AMBER_COLOR.b, 0.95f);
            drawInRect(cachedConvertLabel, CONV_BTN_X, CONV_BTN_Y, CONV_BTN_W, CONV_BTN_H, FS_FOOTER, temporaryColor);
        }
    }

    // ── Text helpers — measure then place so elements never overlap ────────────

    /** Draws text with its baseline at {@code baselineY}, aligned left/center/right against {@code anchorX}. */
    private void drawAligned(CharSequence text, float anchorX, float baselineY,
                             float scale, Color color, int align) {
        font.getData().setScale(scale);
        font.setColor(color);
        glyphLayout.setText(font, text);
        float drawX = anchorX;
        if (align == Align.center) drawX = anchorX - glyphLayout.width / 2f;
        else if (align == Align.right) drawX = anchorX - glyphLayout.width;
        font.draw(spriteBatch, glyphLayout, drawX, baselineY);
    }

    /** Draws text horizontally centred on {@code centerX} and vertically centred on {@code centerY}. */
    private void drawCentered(CharSequence text, float centerX, float centerY, float scale, Color color) {
        font.getData().setScale(scale);
        font.setColor(color);
        glyphLayout.setText(font, text);
        font.draw(spriteBatch, glyphLayout,
                  centerX - glyphLayout.width / 2f,
                  centerY + glyphLayout.height / 2f);
    }

    /** Draws text centred inside the given rectangle (used for buttons). */
    private void drawInRect(CharSequence text, float rectX, float rectY, float rectW, float rectH,
                            float scale, Color color) {
        drawCentered(text, rectX + rectW / 2f, rectY + rectH / 2f, scale, color);
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

    private void buildAbilityStrip(WeaponRoll groundRoll) {
        textBuilder.setLength(0);
        if (groundRoll == null || groundRoll.abilities == null) {
            cachedAbilityStrip = "";
            return;
        }
        int shown = 0;
        for (int abilityIndex = 0;
             abilityIndex < groundRoll.abilities.length && shown < MAX_ABILITY_LINES;
             abilityIndex++) {
            AbilityInstance inst = groundRoll.abilities[abilityIndex];
            if (inst == null) continue;
            if (shown > 0) textBuilder.append("      ");
            if (inst.ability.legendaryOnly) textBuilder.append("* ");
            textBuilder.append(formatAbilityShort(inst));
            shown++;
        }
        cachedAbilityStrip = textBuilder.toString();
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
            if (loadoutRef.isSlotLocked(slotIndex)) continue;
            if (loadoutRef.getSlot(slotIndex) == null) return slotIndex;
        }
        return 0;
    }
}
