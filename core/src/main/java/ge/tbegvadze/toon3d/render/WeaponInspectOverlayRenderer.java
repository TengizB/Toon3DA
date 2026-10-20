package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
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
import ge.tbegvadze.toon3d.util.ArtDirection;
import ge.tbegvadze.toon3d.util.CombatPalette;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.HudConstants;
import ge.tbegvadze.toon3d.util.EnemyConstants;
import ge.tbegvadze.toon3d.enemy.EnemyTrait;
import ge.tbegvadze.toon3d.entity.DamageClass;
import ge.tbegvadze.toon3d.entity.MatchupCatalog;
import ge.tbegvadze.toon3d.entity.MatchupTable;
import java.util.List;

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

    // ── Colours (visual-identity-order-5): ArtDirection / CombatPalette only ─────
    private static final Color PRIMARY_COLOR   = new Color(ArtDirection.UI_TEXT_PRIMARY_RED,
            ArtDirection.UI_TEXT_PRIMARY_GREEN, ArtDirection.UI_TEXT_PRIMARY_BLUE, 1f);
    private static final Color SECONDARY_COLOR = new Color(ArtDirection.UI_TEXT_SECONDARY_RED,
            ArtDirection.UI_TEXT_SECONDARY_GREEN, ArtDirection.UI_TEXT_SECONDARY_BLUE, 1f);
    /** A stat the found gun does better: the semantic "good" green; worse: CombatPalette's damage red. */
    private static final Color BETTER_COLOR    = new Color(ArtDirection.HEALTH_GREEN_RED,
            ArtDirection.HEALTH_GREEN_GREEN, ArtDirection.HEALTH_GREEN_BLUE, 1f);
    private static final Color WORSE_COLOR     = new Color(CombatPalette.DAMAGE_RED, CombatPalette.DAMAGE_GREEN,
            CombatPalette.DAMAGE_BLUE, 1f);

    // ── Font scales: TYPE_* steps (R6); the title is the R1 screen header ──────
    private static final float FS_TIER_TAG = ArtDirection.TYPE_BODY;
    private static final float FS_COL_HEAD = ArtDirection.TYPE_BODY;
    private static final float FS_STAT     = ArtDirection.TYPE_LABEL;
    /** TYPE_BODY: a "-n" delta is DAMAGE red, which reads only as large text (art-direction CONTRAST). */
    private static final float FS_DELTA    = ArtDirection.TYPE_BODY;
    private static final float FS_ABILITY  = ArtDirection.TYPE_BODY;
    private static final float FS_BUTTON   = ArtDirection.TYPE_HEADING;
    private static final float FS_SLOT_NAME= ArtDirection.TYPE_BODY;
    private static final float FS_SLOT_STAT= ArtDirection.TYPE_CAPTION;
    private static final float FS_SWAP     = ArtDirection.TYPE_BODY;
    private static final float FS_FOOTER   = ArtDirection.TYPE_LABEL;

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
    private static final int   STAT_ROW_COUNT  = 6;                            // 4 stats + STRONG VS + WEAK VS
    private static final int   STRONG_ROW_INDEX = 4;
    private static final int   WEAK_ROW_INDEX   = 5;
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

    // Title (R1): its left edge clears the tier icon; its top is the old header text line
    private static final float TITLE_X     = CARD_X + HudConstants.WEAPON_CARD_TITLE_INSET_X;
    private static final float TITLE_TOP_Y = HEADER_Y + HEADER_H / 2f;

    // ── Owned resources ───────────────────────────────────────────────────────
    private final ShapeRenderer shapeRenderer;
    private final SpriteBatch   spriteBatch;
    private final BitmapFont    font;
    /** The HEAVY face for the card's title (R1). */
    private final BitmapFont    headingFont;
    private final GlyphLayout   glyphLayout;
    private final MatchupGlyphs matchupGlyphs = new MatchupGlyphs();

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
    private float    cachedTierRed      = WeaponTier.COMMON.colorRed;
    private float    cachedTierGreen    = WeaponTier.COMMON.colorGreen;
    private float    cachedTierBlue     = WeaponTier.COMMON.colorBlue;
    private WeaponTier cachedTier       = WeaponTier.COMMON;

    // R14 c: both guns' level tags + EFFECTIVE damage on THIS floor (threat level = current depth).
    private int    currentThreatLevel   = 1;
    private String cachedFoundHead      = "FOUND";
    private String cachedActiveHead     = "ACTIVE";
    private int    cachedFoundStanding  = WeaponLevelTag.STANDING_NORMAL;
    private int    cachedActiveStanding = WeaponLevelTag.STANDING_NORMAL;
    private final Color levelHeadColor  = new Color();

    private int cachedGroundDamage  = 0;
    private int cachedGroundClip    = 0;
    private int cachedGroundReload  = 0;
    private int cachedGroundRange   = 0;

    private int cachedActiveDamage  = 0;
    private int cachedActiveClip    = 0;
    private int cachedActiveReload  = 0;
    private int cachedActiveRange   = 0;

    private String cachedAbilityStrip = "";

    // Damage class of each compared gun (null = no gun in that column) — drives the matchup rows (C5).
    private DamageClass cachedFoundClass  = null;
    private DamageClass cachedActiveClass = null;

    private String cachedConvertLabel = "CONVERT AMMO";

    // ── Callbacks ─────────────────────────────────────────────────────────────
    private Runnable    onEquipFreeSlot  = null;
    private IntConsumer onSwapSlot       = null;
    private Runnable    onConvertToAmmo  = null;
    private Runnable    onClose          = null;

    // ── Scratch — pre-allocated ───────────────────────────────────────────────
    private final StringBuilder textBuilder    = new StringBuilder(96);

    // ── Constructor ───────────────────────────────────────────────────────────

    public WeaponInspectOverlayRenderer() {
        shapeRenderer = new ShapeRenderer();
        spriteBatch   = new SpriteBatch();
        font          = GameFonts.newFont(FontFace.REGULAR);
        // Sub-pixel positioning keeps scaled text smooth (the atlas filter is GameFonts').
        font.setUseIntegerPositions(false);
        headingFont   = GameFonts.newFont(FontFace.HEAVY);
        headingFont.setUseIntegerPositions(false);
        glyphLayout   = new GlyphLayout();
    }

    // ── Callback setters ──────────────────────────────────────────────────────

    public void setOnEquipFreeSlot(Runnable callback)   { this.onEquipFreeSlot = callback; }
    public void setOnSwapSlot(IntConsumer callback)     { this.onSwapSlot = callback; }
    public void setOnConvertToAmmo(Runnable callback)   { this.onConvertToAmmo = callback; }
    public void setOnClose(Runnable callback)           { this.onClose = callback; }

    // Kept for backward compatibility — World wires these before fully migrating
    public void setOnTake(Runnable callback)            { this.onEquipFreeSlot = callback; }
    /** The floor's threat level (= current depth); the found weapon's damage is measured against it. */
    public void setCurrentThreatLevel(int threatLevel) { this.currentThreatLevel = Math.max(1, threatLevel); }

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
        cachedFoundClass  = arsenalWeapon != null ? arsenalWeapon.damageClass() : null;
        cachedActiveClass = activeWeapon  != null ? activeWeapon.damageClass()  : null;
        buildLevelHeads(level, arsenalWeapon, activeWeapon);
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
        renderText(camera);
    }

    // ── Pass A: filled shapes ─────────────────────────────────────────────────

    /**
     * One Filled pass (visual-identity-order-5): the scrim, the card itself as a UiKit CARD framed in the found
     * weapon's TIER colour (R2) with SUIT_AMBER_DIM brackets (it is the screen's outermost panel), the R1 rule
     * under the title, plain dividers between the zones, and plates for every tappable control.
     */
    private void renderFilledShapes(OrthographicCamera camera) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Full-screen scrim (R3)
        shapeRenderer.setColor(ArtDirection.UI_INK_BLACK_RED, ArtDirection.UI_INK_BLACK_GREEN,
                               ArtDirection.UI_INK_BLACK_BLUE, ArtDirection.UI_SCRIM_ALPHA);
        shapeRenderer.rect(0, 0, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);

        // The card: tier-inked frame, amber brackets on the outermost panel (R2, R4)
        UiKit.card(shapeRenderer, CARD_X, CARD_Y, CARD_W, CARD_H,
                   cachedTierRed, cachedTierGreen, cachedTierBlue, PlateState.IDLE);
        UiKit.brackets(shapeRenderer, CARD_X, CARD_Y, CARD_W, CARD_H,
                       ArtDirection.SUIT_AMBER_DIM_RED, ArtDirection.SUIT_AMBER_DIM_GREEN,
                       ArtDirection.SUIT_AMBER_DIM_BLUE, 1f);
        // The tier icon (an icon routine: its shape and colour name the tier)
        drawTierIconFilled(cachedTier, CARD_X + HudConstants.WEAPON_CARD_TIER_ICON_INSET,
                           HEADER_Y + HEADER_H / 2f, HudConstants.WEAPON_CARD_TIER_ICON_SIZE);

        // Header rule (R1: it is the header's bottom edge too) and the zone / column dividers
        UiKit.screenHeaderRule(shapeRenderer, TITLE_X, UiKit.screenHeaderRuleY(TITLE_TOP_Y),
                               CARD_RIGHT - INNER_PAD - TITLE_X, 1f);
        drawPlainRule(CARD_X + INNER_PAD, ABILITY_TOP, CARD_W - INNER_PAD * 2f);
        drawPlainRule(CARD_X + INNER_PAD, ACTION_TOP, CARD_W - INNER_PAD * 2f);
        drawPlainRule(CARD_X + INNER_PAD, FOOTER_TOP, CARD_W - INNER_PAD * 2f);
        shapeRenderer.setColor(ArtDirection.UI_LINE_RED, ArtDirection.UI_LINE_GREEN, ArtDirection.UI_LINE_BLUE, 1f);
        float columnRuleBottom = STATS_BOTTOM + HudConstants.WEAPON_CARD_COLUMN_RULE_INSET;
        float columnRuleHeight = STATS_TOP - COL_HEAD_BAND_H - STATS_BOTTOM;
        shapeRenderer.rect(CARD_X + HudConstants.WEAPON_CARD_COLUMN_RULE_1_X, columnRuleBottom,
                           ArtDirection.UI_DIVIDER_THICKNESS, columnRuleHeight);
        shapeRenderer.rect(CARD_X + HudConstants.WEAPON_CARD_COLUMN_RULE_2_X, columnRuleBottom,
                           ArtDirection.UI_DIVIDER_THICKNESS, columnRuleHeight);

        // STRONG VS / WEAK VS trait glyphs (C5) under each compared gun.
        drawMatchupGlyphsFilled(cachedFoundClass,  FOUND_COL_X);
        drawMatchupGlyphsFilled(cachedActiveClass, ACTIVE_COL_X);

        // Action content (R5): the single action is an IDLE plate; full-loadout rows are weapon cards
        if (singleButtonMode()) {
            drawPlate(EQUIP_BTN_X, EQUIP_BTN_Y, EQUIP_BTN_W, EQUIP_BTN_H);
        } else {
            renderSlotRowsFilled();
        }

        // Footer plates
        drawPlate(CLOSE_BTN_X, CLOSE_BTN_Y, CLOSE_BTN_W, CLOSE_BTN_H);
        if (hasConvert) drawPlate(CONV_BTN_X, CONV_BTN_Y, CONV_BTN_W, CONV_BTN_H);

        shapeRenderer.end();
    }

    private void drawPlainRule(float x, float y, float width) {
        UiKit.divider(shapeRenderer, x, y, width,
                      ArtDirection.UI_LINE_RED, ArtDirection.UI_LINE_GREEN, ArtDirection.UI_LINE_BLUE, 1f);
    }

    private void drawPlate(float x, float y, float width, float height) {
        UiKit.plate(shapeRenderer, x, y, width, height, PlateState.IDLE, ArtDirection.UI_PANEL_ALPHA_SCREEN,
                    ArtDirection.SUIT_AMBER_RED, ArtDirection.SUIT_AMBER_GREEN, ArtDirection.SUIT_AMBER_BLUE);
    }

    private float statBandCenter(int rowIndex) {
        float rowsTop  = STATS_TOP - COL_HEAD_BAND_H;
        float rowBandH = (rowsTop - STATS_BOTTOM) / STAT_ROW_COUNT;
        return rowsTop - rowIndex * rowBandH - rowBandH / 2f;
    }

    /** Draws one column's STRONG VS and WEAK VS glyph rows (an empty row is drawn as a dash in the text pass). */
    private void drawMatchupGlyphsFilled(DamageClass damageClass, float columnCenterX) {
        if (damageClass == null) return;
        MatchupTable table = MatchupCatalog.shared();
        drawTraitGlyphRow(table.strongAgainst(damageClass), columnCenterX, statBandCenter(STRONG_ROW_INDEX), table);
        drawTraitGlyphRow(table.weakAgainst(damageClass),   columnCenterX, statBandCenter(WEAK_ROW_INDEX),   table);
    }

    private void drawTraitGlyphRow(List<EnemyTrait> traits, float columnCenterX, float centerY, MatchupTable table) {
        int count = traits.size();
        if (count == 0) return;
        float size = HudConstants.WEAPON_CARD_TRAIT_GLYPH_SIZE;
        float step = size + HudConstants.WEAPON_CARD_TRAIT_GLYPH_GAP;
        float left = columnCenterX - (count * size + (count - 1) * HudConstants.WEAPON_CARD_TRAIT_GLYPH_GAP) / 2f;
        for (int traitIndex = 0; traitIndex < count; traitIndex++) {
            EnemyTrait trait = traits.get(traitIndex);
            DamageClass best = table.bestClassAgainst(trait);
            float red   = best != null ? best.colorRed()   : EnemyConstants.ENEMY_TRAIT_GLYPH_NEUTRAL_RED;
            float green = best != null ? best.colorGreen() : EnemyConstants.ENEMY_TRAIT_GLYPH_NEUTRAL_GREEN;
            float blue  = best != null ? best.colorBlue()  : EnemyConstants.ENEMY_TRAIT_GLYPH_NEUTRAL_BLUE;
            matchupGlyphs.drawTrait(shapeRenderer, trait, left + traitIndex * step, centerY - size / 2f, size,
                    red, green, blue, 1f);
        }
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

    /**
     * Full-loadout rows: each is a weapon CARD in its own tier ink (R2), the active slot SELECTED (its brackets
     * take the ink too); the SWAP label inside sits on an IDLE plate.
     */
    private void renderSlotRowsFilled() {
        if (loadout == null) return;
        for (int rowPosition = 0; rowPosition < displayedSlotCount; rowPosition++) {
            int   slotIndex = displayedSlotIndices[rowPosition];
            float rowTop    = slotRowTop(rowPosition);
            float rowBottom = rowTop - SLOT_ROW_H;
            Weapon slotWeapon = loadout.getSlot(slotIndex);
            WeaponTier slotTier = (slotWeapon != null && slotWeapon.getTier() != null)
                    ? slotWeapon.getTier() : WeaponTier.COMMON;
            UiKit.card(shapeRenderer, SLOT_ROW_X, rowBottom, SLOT_ROW_W, SLOT_ROW_H,
                       slotTier.colorRed, slotTier.colorGreen, slotTier.colorBlue,
                       slotIndex == activeSlotIndex ? PlateState.SELECTED : PlateState.IDLE);
            float swapBtnX = SLOT_ROW_X + SLOT_ROW_W - SWAP_BTN_PAD - SWAP_BTN_W;
            float swapBtnY = rowBottom + (SLOT_ROW_H - SWAP_BTN_H) / 2f;
            drawPlate(swapBtnX, swapBtnY, SWAP_BTN_W, SWAP_BTN_H);
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
        font.getData().setScale(ArtDirection.TYPE_CAPTION);
    }

    private void drawHeader() {
        // The weapon's name is the card's R1 title; its tier lives in the frame ink and the "LV n • TIER" words.
        UiKit.screenHeaderTitle(spriteBatch, headingFont, cachedWeaponName, TITLE_X, TITLE_TOP_Y);
        drawAligned(cachedLevelTier, CARD_RIGHT - INNER_PAD, TITLE_TOP_Y, FS_TIER_TAG, SECONDARY_COLOR, Align.right);
    }

    private void drawStatBlock() {
        // Column headers, vertically centred in their band.
        float colHeadCenter = STATS_TOP - COL_HEAD_BAND_H / 2f;
        WeaponLevelTag.colorFor(cachedFoundStanding, PRIMARY_COLOR, levelHeadColor);
        drawCentered(cachedFoundHead, FOUND_COL_X, colHeadCenter, FS_COL_HEAD, levelHeadColor);
        drawCentered("STAT",   CENTER_X,     colHeadCenter, FS_COL_HEAD, SECONDARY_COLOR);
        WeaponLevelTag.colorFor(cachedActiveStanding, PRIMARY_COLOR, levelHeadColor);
        drawCentered(cachedActiveHead, ACTIVE_COL_X, colHeadCenter, FS_COL_HEAD, levelHeadColor);

        // Four stat rows, each vertically centred in its own band (bands never overlap).
        float rowsTop    = STATS_TOP - COL_HEAD_BAND_H;
        float rowBandH   = (rowsTop - STATS_BOTTOM) / STAT_ROW_COUNT;
        drawStatRow(rowsTop, rowBandH, 0, "DAMAGE", cachedGroundDamage, cachedActiveDamage, false);
        drawStatRow(rowsTop, rowBandH, 1, "CLIP",   cachedGroundClip,   cachedActiveClip,   false);
        drawStatRow(rowsTop, rowBandH, 2, "RELOAD", cachedGroundReload, cachedActiveReload, true);
        drawStatRow(rowsTop, rowBandH, 3, "RANGE",  cachedGroundRange,  cachedActiveRange,  false);

        // Matchup rows (C5): words and glyphs only, never multipliers (AS5). Glyphs are drawn in the filled pass.
        drawCentered(HudConstants.WEAPON_CARD_STRONG_VS_LABEL, CENTER_X, statBandCenter(STRONG_ROW_INDEX), FS_STAT, SECONDARY_COLOR);
        drawCentered(HudConstants.WEAPON_CARD_WEAK_VS_LABEL,   CENTER_X, statBandCenter(WEAK_ROW_INDEX),   FS_STAT, SECONDARY_COLOR);
        drawMatchupEmptyDashes(cachedFoundClass,  FOUND_COL_X);
        drawMatchupEmptyDashes(cachedActiveClass, ACTIVE_COL_X);
    }

    /** A column with a gun but no trait in a row reads as a dash; a column with no gun stays blank. */
    private void drawMatchupEmptyDashes(DamageClass damageClass, float columnCenterX) {
        if (damageClass == null) return;
        MatchupTable table = MatchupCatalog.shared();
        if (table.strongAgainst(damageClass).isEmpty()) {
            drawCentered(HudConstants.WEAPON_CARD_NO_MATCHUP_LABEL, columnCenterX, statBandCenter(STRONG_ROW_INDEX), FS_STAT, SECONDARY_COLOR);
        }
        if (table.weakAgainst(damageClass).isEmpty()) {
            drawCentered(HudConstants.WEAPON_CARD_NO_MATCHUP_LABEL, columnCenterX, statBandCenter(WEAK_ROW_INDEX), FS_STAT, SECONDARY_COLOR);
        }
    }

    private void drawStatRow(float rowsTop, float rowBandH, int rowIndex,
                             String label, int groundValue, int activeValue, boolean lowerIsBetter) {
        float bandCenter = rowsTop - rowIndex * rowBandH - rowBandH / 2f;

        drawCentered(label, CENTER_X, bandCenter, FS_STAT, SECONDARY_COLOR);

        // FOUND value, color-coded against the active weapon.
        textBuilder.setLength(0);
        textBuilder.append(groundValue);
        drawCentered(textBuilder, FOUND_COL_X, bandCenter, FS_STAT, deltaColor(groundValue, activeValue, lowerIsBetter));

        // ACTIVE value: white, except the DAMAGE row, where the stronger gun on THIS floor reads green
        // (and the FOUND side already reads green/red), so the verdict shows on both columns.
        textBuilder.setLength(0);
        textBuilder.append(activeValue);
        Color activeColor = PRIMARY_COLOR;
        if (rowIndex == 0 && activeValue > 0 && groundValue > 0 && groundValue != activeValue) {
            activeColor = activeValue > groundValue ? BETTER_COLOR : WORSE_COLOR;
        }
        drawCentered(textBuilder, ACTIVE_COL_X, bandCenter, FS_STAT, activeColor);

        // Numeric delta tag beside the FOUND value (font-safe, no arrow glyphs).
        if (activeValue > 0 && groundValue > 0 && groundValue != activeValue) {
            boolean groundBetter = lowerIsBetter ? (groundValue < activeValue) : (groundValue > activeValue);
            int difference = Math.abs(groundValue - activeValue);
            textBuilder.setLength(0);
            textBuilder.append(groundBetter ? "+" : "-").append(difference);
            drawAligned(textBuilder, FOUND_COL_X + HudConstants.WEAPON_CARD_DELTA_OFFSET_X, bandCenter, FS_DELTA,
                        groundBetter ? BETTER_COLOR : WORSE_COLOR, Align.left);
        }
    }

    private Color deltaColor(int groundValue, int activeValue, boolean lowerIsBetter) {
        if (activeValue <= 0 || groundValue == activeValue) return PRIMARY_COLOR;
        boolean groundBetter = lowerIsBetter ? (groundValue < activeValue) : (groundValue > activeValue);
        return groundBetter ? BETTER_COLOR : WORSE_COLOR;
    }

    private void drawAbilityStrip() {
        float bandCenter = ABILITY_Y + ABILITY_H / 2f;
        if (cachedAbilityStrip.isEmpty()) {
            drawCentered("— NO SPECIAL ABILITIES —", CENTER_X, bandCenter, FS_ABILITY, SECONDARY_COLOR);
            return;
        }
        // Wrap the ability tags to the card width and centre the block vertically so a long
        // list flows onto a second line instead of overrunning the card edges.
        font.getData().setScale(FS_ABILITY);
        font.setColor(PRIMARY_COLOR);
        float wrapWidth = CARD_W - 2f * INNER_PAD;
        glyphLayout.setText(font, cachedAbilityStrip, PRIMARY_COLOR, wrapWidth, Align.center, true);
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
                       FS_BUTTON, PRIMARY_COLOR);
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
            float nameBaseline  = rowBottom + SLOT_ROW_H - HudConstants.WEAPON_SLOT_ROW_NAME_DROP;
            float statsBaseline = rowBottom + HudConstants.WEAPON_SLOT_ROW_STATS_RISE;

            // Slot label + weapon name (primary: the row's frame carries the tier ink, R2).
            textBuilder.setLength(0);
            if (slotIndex == activeSlotIndex) textBuilder.append("[ACTIVE] ");
            textBuilder.append("SLOT ").append(slotIndex + 1).append("   ");
            textBuilder.append(slotWeapon.getDisplayName().toUpperCase());
            textBuilder.append("  LV ").append(slotWeapon.getWeaponLevel());
            drawAligned(textBuilder, SLOT_ROW_X + HudConstants.WEAPON_SLOT_ROW_TEXT_INSET, nameBaseline, FS_SLOT_NAME,
                        PRIMARY_COLOR, Align.left);

            // Compact stat line.
            textBuilder.setLength(0);
            textBuilder.append("DMG ").append(slotWeapon.getEffectiveDamage())
                       .append("     RNG ").append(slotWeapon.getEffectiveRange())
                       .append("     CLIP ").append(slotWeapon.getEffectiveClipSize());
            drawAligned(textBuilder, SLOT_ROW_X + HudConstants.WEAPON_SLOT_ROW_TEXT_INSET, statsBaseline, FS_SLOT_STAT,
                        SECONDARY_COLOR, Align.left);

            // SWAP button label.
            float swapBtnX = SLOT_ROW_X + SLOT_ROW_W - SWAP_BTN_PAD - SWAP_BTN_W;
            float swapBtnY = rowBottom + (SLOT_ROW_H - SWAP_BTN_H) / 2f;
            drawInRect("SWAP", swapBtnX, swapBtnY, SWAP_BTN_W, SWAP_BTN_H, FS_SWAP, PRIMARY_COLOR);
        }
    }

    private void drawFooter() {
        drawInRect("CLOSE", CLOSE_BTN_X, CLOSE_BTN_Y, CLOSE_BTN_W, CLOSE_BTN_H, FS_FOOTER, PRIMARY_COLOR);
        if (hasConvert) {
            drawInRect(cachedConvertLabel, CONV_BTN_X, CONV_BTN_Y, CONV_BTN_W, CONV_BTN_H, FS_FOOTER, PRIMARY_COLOR);
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
        headingFont.dispose();
    }

    // ── Helpers (called from show(), no LibGDX render state) ──────────────────

    /** Column headings carrying each gun's "LV n" and its standing against this floor's threat level. */
    private void buildLevelHeads(int foundLevel, Weapon arsenalWeapon, Weapon activeWeapon) {
        boolean foundExempt = arsenalWeapon != null && arsenalWeapon.isLevelGapExempt();
        cachedFoundStanding = WeaponLevelTag.standingForGap(foundExempt ? 0 : foundLevel - currentThreatLevel);
        cachedFoundHead = "FOUND  " + WeaponLevelTag.label(foundLevel);
        if (activeWeapon != null) {
            cachedActiveStanding = WeaponLevelTag.standing(activeWeapon, currentThreatLevel);
            cachedActiveHead = "ACTIVE  " + WeaponLevelTag.label(activeWeapon.getWeaponLevel());
        } else {
            cachedActiveStanding = WeaponLevelTag.STANDING_NORMAL;
            cachedActiveHead = "ACTIVE";
        }
    }

    private void buildStatCache(WeaponRoll groundRoll, Weapon arsenalWeapon, Weapon activeWeapon) {
        if (arsenalWeapon != null && groundRoll != null) {
            int level = groundRoll.weaponLevel;
            cachedGroundDamage = Math.round(GameMath.weaponLadderDamage(arsenalWeapon.getBaseDamage(), level,
                    groundRoll.tier.ordinal(), currentThreatLevel, arsenalWeapon.isLevelGapExempt()));
            cachedGroundClip   = GameMath.weaponScaledClipSize(arsenalWeapon.getBaseClipSize(), level);
            cachedGroundReload = GameMath.weaponScaledReloadTicks(arsenalWeapon.getBaseReloadTicks(), level);
            cachedGroundRange  = GameMath.weaponScaledRange(arsenalWeapon.getBaseRange(), level, arsenalWeapon.isMelee());
        } else if (arsenalWeapon != null) {
            cachedGroundDamage = Math.round(GameMath.weaponLadderDamage(arsenalWeapon.getBaseDamage(), 1,
                    WeaponTier.COMMON.ordinal(), currentThreatLevel, arsenalWeapon.isLevelGapExempt()));
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
            case ARMOR_PIERCE:       return "PIERCE " + inst.magnitudePercent() + "% +" + inst.countValue;
            case EXECUTIONER:        return "EXECUTE " + inst.magnitudePercent() + "%";
            case REND:               return "BLEED " + Math.round(inst.magnitude) + "/T";
            case STAGGER_ROUNDS:     return "STUN " + inst.magnitudePercent() + "%";
            case KINETIC_SLAM:       return "KNOCKBACK " + inst.magnitudePercent() + "%";
            case CLEAVE:             return "CLEAVE " + inst.magnitudePercent() + "%";
            case INCENDIARY:         return "BURN " + Math.round(inst.magnitude) + "/T";
            case LIFESTEAL:          return "LIFESTEAL " + inst.magnitudePercent() + "%";
            case SCAVENGER_ROUNDS:   return "REFUND " + inst.magnitudePercent() + "%";
            case POINT_BLANK:        return "POINT BLANK " + inst.magnitudePercent() + "%";
            case OPENING_SALVO:      return "FIRST SHOT +" + inst.magnitudePercent() + "%";
            case RHYTHM:             return "RHYTHM " + inst.magnitudePercent() + "%";
            case STATIC_DISCHARGE:   return "STATIC " + Math.round(inst.magnitude);
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
