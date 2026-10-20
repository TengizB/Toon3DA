package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.entity.AbilityInstance;
import ge.tbegvadze.toon3d.entity.Weapon;
import ge.tbegvadze.toon3d.entity.WeaponAbility;
import ge.tbegvadze.toon3d.item.Inventory;
import ge.tbegvadze.toon3d.item.ItemCategory;
import ge.tbegvadze.toon3d.item.ItemStack;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.util.ArtDirection;
import ge.tbegvadze.toon3d.util.CombatPalette;
import ge.tbegvadze.toon3d.util.ItemConstants;
import ge.tbegvadze.toon3d.util.WeaponConstants;
import java.util.function.Consumer;

/**
 * Part 4 ItemWindow — modal popup shown when a weapon or inventory slot is tapped.
 *
 * Three-zone layout:
 *   Header (top 68px)  — glyph box, item name, category badge, amber exit button
 *   Body   (middle)    — left column: description + stat block;
 *                        right column: abilities (weapons) or category-specific info
 *   Footer (bottom 56px) — two action buttons (use/equip + drop)
 *
 * Part 5 addition: weapon right column renders tappable ability rows. Each row
 * shows the ability's hudGlyph, displayName, shortHint, and a PASSIVE/ACTIVE badge.
 * Tapping a row invokes the onAbilityTap callback, which opens AbilityWindow.
 *
 * Package-private — owned and driven by InventoryOverlayRenderer coordinator.
 * No allocations inside render() — all scratch objects pre-allocated.
 */
final class ItemWindow implements Disposable {

    // -------------------------------------------------------------------------
    // Derived geometry constants (computed once from ItemConstants)
    // -------------------------------------------------------------------------

    private static final float WINDOW_X      = ItemConstants.INV_ITEM_WIN_X;
    private static final float WINDOW_Y      = ItemConstants.INV_ITEM_WIN_Y;
    private static final float WINDOW_W      = ItemConstants.INV_ITEM_WIN_WIDTH;
    private static final float WINDOW_H      = ItemConstants.INV_ITEM_WIN_HEIGHT;

    private static final float HEADER_Y      = ItemConstants.INV_ITEM_WIN_HEADER_Y;
    private static final float HEADER_H      = ItemConstants.INV_ITEM_WIN_HEADER_H;
    private static final float FOOTER_RULE_Y = ItemConstants.INV_ITEM_WIN_FOOTER_RULE_Y;
    private static final float BODY_Y        = ItemConstants.INV_ITEM_WIN_BODY_Y;
    private static final float BODY_H        = ItemConstants.INV_ITEM_WIN_BODY_H;

    private static final float GLYPH_X       = ItemConstants.INV_ITEM_WIN_GLYPH_X;
    private static final float GLYPH_Y       = ItemConstants.INV_ITEM_WIN_GLYPH_Y;
    private static final float GLYPH_SIZE    = ItemConstants.INV_ITEM_WIN_GLYPH_SIZE;
    private static final float NAME_X        = ItemConstants.INV_ITEM_WIN_NAME_X;

    private static final float EXIT_X        = ItemConstants.INV_ITEM_WIN_EXIT_BTN_X;
    private static final float EXIT_Y        = ItemConstants.INV_ITEM_WIN_EXIT_BTN_Y;
    private static final float EXIT_SIZE     = ItemConstants.INV_ITEM_WIN_EXIT_BTN_SIZE;

    private static final float BODY_L_X      = ItemConstants.INV_ITEM_WIN_BODY_LEFT_X;
    private static final float BODY_L_W      = ItemConstants.INV_ITEM_WIN_BODY_LEFT_WIDTH;
    private static final float DIVIDER_X     = ItemConstants.INV_ITEM_WIN_DIVIDER_X;
    private static final float BODY_R_X      = ItemConstants.INV_ITEM_WIN_BODY_RIGHT_X;
    private static final float BODY_R_W      = ItemConstants.INV_ITEM_WIN_BODY_RIGHT_WIDTH;
    private static final float STAT_ROW_H    = ItemConstants.INV_ITEM_WIN_STAT_ROW_H;

    private static final float BTN1_X        = ItemConstants.INV_ACTION_BTN_1_X;
    private static final float BTN2_X        = ItemConstants.INV_ACTION_BTN_2_X;
    private static final float BTN_Y         = ItemConstants.INV_ACTION_BTN_Y;
    private static final float BTN_W         = ItemConstants.INV_ACTION_BTN_WIDTH;
    private static final float BTN_H         = ItemConstants.INV_ACTION_BTN_HEIGHT;

    // Header: the title's top edge (the R1 rule sits under it)
    private static final float TITLE_TOP_Y   = HEADER_Y + HEADER_H - ItemConstants.INV_ITEM_WIN_TITLE_TOP_INSET;

    // Body: fixed Y at which the description block begins (just below header rule)
    private static final float DESC_Y        = HEADER_Y - 10f;
    // Body: fixed Y at which the stat block begins (leaves ~72px for 4 description lines)
    private static final float STAT_Y        = DESC_Y - 80f;

    // Ability rows in the right column — Part 5
    private static final float ABILITY_ROW_H   = ItemConstants.INV_ITEM_WIN_ABILITY_ROW_H;
    private static final float ABILITY_ROW_GAP = ItemConstants.INV_ITEM_WIN_ABILITY_ROW_GAP;
    private static final float ABILITY_HDR_H   = ItemConstants.INV_ITEM_WIN_ABILITY_HDR_H;
    private static final float BADGE_W         = ItemConstants.INV_ABILITY_BADGE_W;
    private static final float BADGE_H         = ItemConstants.INV_ABILITY_BADGE_H;

    // -------------------------------------------------------------------------
    // Type roles (visual-identity-order-5 R6; the order-3 R8 sub-caption scales are gone)
    // -------------------------------------------------------------------------

    /** The window title is the R1 screen header; these are the other roles. */
    private static final float SCALE_BODY     = ArtDirection.TYPE_BODY;
    private static final float SCALE_CAPTION  = ArtDirection.TYPE_CAPTION;
    private static final float SCALE_SECTION  = ArtDirection.TYPE_LABEL;
    /** The big "quantity in bag" number and the ASCII fallback glyph are icon-sized. */
    private static final float SCALE_QUANTITY = ArtDirection.TYPE_HEADING;
    private static final float SCALE_GLYPH    = ArtDirection.TYPE_DISPLAY;

    // -------------------------------------------------------------------------
    // Shared rendering resources (owned by coordinator, not disposed here)
    // -------------------------------------------------------------------------

    private static final AbilityInstance[] NO_ABILITY_INSTANCES = new AbilityInstance[0];
    private static final String[]         NO_SHORT_HINTS        = new String[0];

    private final ShapeRenderer            shapeRenderer;
    private final SpriteBatch              spriteBatch;
    private final BitmapFont               font;
    private final BitmapFont               headingFont;
    private final GlyphLayout              glyphLayout;
    private final Consumer<AbilityInstance> onAbilityTap;
    private final ItemIconTextures         iconTextures;

    // Pre-allocated scratch buffer for dynamic stat value strings
    private final StringBuilder valueBuilder;
    // Scratch colour for the glyph box's category border
    private final Color glyphBoxBorder = new Color();

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private Inventory         inventory;
    private int               slotIndex          = -1;
    private String            flashMessage       = null;
    private float             flashTimerSeconds  = 0f;
    private AbilityInstance[] cachedAbilities    = NO_ABILITY_INSTANCES;
    private String[]          cachedShortHints   = NO_SHORT_HINTS;
    private ItemType          lastConsumedType   = null;

    // Weapon-mode state: set by openWeapon(), cleared by close(). Mutually exclusive with slotIndex >= 0.
    private Weapon  currentWeapon   = null;
    private boolean weaponIsActive  = false;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    ItemWindow(ShapeRenderer shapeRenderer, SpriteBatch spriteBatch,
               BitmapFont font, BitmapFont headingFont, GlyphLayout glyphLayout,
               Consumer<AbilityInstance> onAbilityTap,
               ItemIconTextures iconTextures) {
        this.shapeRenderer = shapeRenderer;
        this.spriteBatch   = spriteBatch;
        this.font          = font;
        this.headingFont   = headingFont;
        this.glyphLayout   = glyphLayout;
        this.onAbilityTap  = onAbilityTap;
        this.iconTextures  = iconTextures;
        this.valueBuilder  = new StringBuilder(32);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    void open(Inventory inventory, int slotIndex) {
        this.inventory         = inventory;
        this.slotIndex         = slotIndex;
        this.flashMessage      = null;
        this.flashTimerSeconds = 0f;
        this.lastConsumedType  = null;
        ItemStack slot = inventory.getSlot(slotIndex);
        if (!slot.isEmpty() && slot.getType().getCategory() == ItemCategory.WEAPON) {
            WeaponAbility[] signatureAbilities = slot.getType().getSignatureAbilities();
            cachedAbilities  = new AbilityInstance[signatureAbilities.length];
            cachedShortHints = new String[signatureAbilities.length];
            for (int abilityIndex = 0; abilityIndex < signatureAbilities.length; abilityIndex++) {
                AbilityInstance levelOneInst    = signatureAbilities[abilityIndex].levelOneInstance();
                cachedAbilities[abilityIndex]  = levelOneInst;
                cachedShortHints[abilityIndex] = levelOneInst.ability.buildShortHint(levelOneInst);
            }
        } else {
            cachedAbilities  = NO_ABILITY_INSTANCES;
            cachedShortHints = NO_SHORT_HINTS;
        }
    }

    void openWeapon(Weapon weapon, boolean isActive) {
        this.currentWeapon    = weapon;
        this.weaponIsActive   = isActive;
        this.slotIndex        = -1;
        this.inventory        = null;
        this.flashMessage     = null;
        this.flashTimerSeconds = 0f;
        this.lastConsumedType = null;
        if (weapon != null) {
            int abilityCount = weapon.getAbilityCount();
            cachedAbilities  = new AbilityInstance[abilityCount];
            cachedShortHints = new String[abilityCount];
            for (int abilityIndex = 0; abilityIndex < abilityCount; abilityIndex++) {
                AbilityInstance instance        = weapon.getAbility(abilityIndex);
                cachedAbilities[abilityIndex]  = instance;
                cachedShortHints[abilityIndex] = instance.ability.buildShortHint(instance);
            }
        } else {
            cachedAbilities  = NO_ABILITY_INSTANCES;
            cachedShortHints = NO_SHORT_HINTS;
        }
    }

    boolean isOpen() {
        return slotIndex >= 0 || currentWeapon != null;
    }

    void close() {
        slotIndex         = -1;
        inventory         = null;
        cachedAbilities   = NO_ABILITY_INSTANCES;
        cachedShortHints  = NO_SHORT_HINTS;
        currentWeapon     = null;
        weaponIsActive    = false;
        lastConsumedType  = null;
    }

    ItemType getLastConsumedType() {
        return lastConsumedType;
    }

    boolean containsPoint(float worldX, float worldY) {
        return worldX >= WINDOW_X && worldX <= WINDOW_X + WINDOW_W
                && worldY >= WINDOW_Y && worldY <= WINDOW_Y + WINDOW_H;
    }

    void updateFlash(float deltaTime) {
        if (flashTimerSeconds > 0f) flashTimerSeconds -= deltaTime;
    }

    // -------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------

    InventoryOverlayRenderer.CloseAction handleTouch(float worldX, float worldY) {
        // Header exit button
        if (worldX >= EXIT_X && worldX <= EXIT_X + EXIT_SIZE
                && worldY >= EXIT_Y && worldY <= EXIT_Y + EXIT_SIZE) {
            return InventoryOverlayRenderer.CloseAction.CLOSE_WINDOW;
        }
        // Ability rows in right column (weapon only) — open AbilityWindow on tap
        if (cachedAbilities.length > 0
                && worldX >= BODY_R_X && worldX <= BODY_R_X + BODY_R_W) {
            float abilityRowTop = DESC_Y - ABILITY_HDR_H;
            for (int abilityIndex = 0; abilityIndex < cachedAbilities.length; abilityIndex++) {
                float rowBottom = abilityRowTop - ABILITY_ROW_H;
                if (worldY >= rowBottom && worldY <= abilityRowTop) {
                    onAbilityTap.accept(cachedAbilities[abilityIndex]);
                    return InventoryOverlayRenderer.CloseAction.NONE;
                }
                abilityRowTop -= (ABILITY_ROW_H + ABILITY_ROW_GAP);
            }
        }
        // Footer button 1 (USE / EQUIP / disabled)
        if (worldX >= BTN1_X && worldX <= BTN1_X + BTN_W
                && worldY >= BTN_Y && worldY <= BTN_Y + BTN_H) {
            return handleActionOne();
        }
        // Footer button 2 (DROP / disabled)
        if (worldX >= BTN2_X && worldX <= BTN2_X + BTN_W
                && worldY >= BTN_Y && worldY <= BTN_Y + BTN_H) {
            return handleDrop();
        }
        return InventoryOverlayRenderer.CloseAction.NONE;
    }

    // -------------------------------------------------------------------------
    // Render
    // -------------------------------------------------------------------------

    void render(OrthographicCamera camera, float animationClock) {
        renderFilled(camera);
        renderText(camera);
    }

    // -------------------------------------------------------------------------
    // Dispose — resources owned by coordinator
    // -------------------------------------------------------------------------

    @Override
    public void dispose() {}

    // =========================================================================
    // Private — action handlers
    // =========================================================================

    private InventoryOverlayRenderer.CloseAction handleActionOne() {
        if (inventory == null || slotIndex < 0) return InventoryOverlayRenderer.CloseAction.NONE;
        ItemStack slot = inventory.getSlot(slotIndex);
        if (slot.isEmpty()) return InventoryOverlayRenderer.CloseAction.CLOSE_WINDOW;
        ItemCategory category = slot.getType().getCategory();

        if (category == ItemCategory.AMMO || category == ItemCategory.KEY_ITEM
                || category == ItemCategory.MOD || category == ItemCategory.MISC) {
            showFlash("Cannot use here");
            return InventoryOverlayRenderer.CloseAction.NONE;
        }
        if (category == ItemCategory.WEAPON) {
            boolean isEquipped = inventory.getEquippedWeaponSlot() == slotIndex;
            if (isEquipped) {
                inventory.setEquippedWeaponSlot(-1);
                return InventoryOverlayRenderer.CloseAction.NONE;
            }
            inventory.use(slotIndex);
            return InventoryOverlayRenderer.CloseAction.CLOSE_WINDOW;
        }
        if (category == ItemCategory.CONSUMABLE) {
            lastConsumedType = slot.getType();
            inventory.use(slotIndex);
            return InventoryOverlayRenderer.CloseAction.CLOSE_WITH_TURN;
        }
        return InventoryOverlayRenderer.CloseAction.NONE;
    }

    private InventoryOverlayRenderer.CloseAction handleDrop() {
        if (inventory == null || slotIndex < 0) return InventoryOverlayRenderer.CloseAction.NONE;
        ItemStack slot = inventory.getSlot(slotIndex);
        if (slot.isEmpty()) return InventoryOverlayRenderer.CloseAction.CLOSE_WINDOW;
        if (slot.getType().getCategory() == ItemCategory.KEY_ITEM) {
            showFlash("Cannot drop quest items");
            return InventoryOverlayRenderer.CloseAction.NONE;
        }
        inventory.remove(slotIndex, slot.getQuantity());
        return InventoryOverlayRenderer.CloseAction.CLOSE_WITH_TURN;
    }

    private void showFlash(String message) {
        flashMessage      = message;
        flashTimerSeconds = ItemConstants.INV_FLASH_SECONDS;
    }

    // =========================================================================
    // Private — render passes
    // =========================================================================

    /**
     * One Filled pass (visual-identity-order-5): the window is a UiKit panel with SUIT_AMBER_DIM brackets (the
     * outermost panel of its layer), the header gets the R1 amber-ticked rule under the title, the header and
     * footer rules are plain dividers, and every tappable thing (X, ability rows, footer actions) is a plate.
     */
    private void renderFilled(OrthographicCamera camera) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        UiKit.panel(shapeRenderer, WINDOW_X, WINDOW_Y, WINDOW_W, WINDOW_H,
                    ArtDirection.UI_PANEL_RED, ArtDirection.UI_PANEL_GREEN, ArtDirection.UI_PANEL_BLUE,
                    ItemConstants.INV_PANEL_ALPHA,
                    ArtDirection.UI_LINE_RED, ArtDirection.UI_LINE_GREEN, ArtDirection.UI_LINE_BLUE);
        UiKit.brackets(shapeRenderer, WINDOW_X, WINDOW_Y, WINDOW_W, WINDOW_H,
                       ArtDirection.SUIT_AMBER_DIM_RED, ArtDirection.SUIT_AMBER_DIM_GREEN,
                       ArtDirection.SUIT_AMBER_DIM_BLUE, 1f);

        // Header: the title's amber-ticked rule, then the plain header and footer rules and the column divider
        UiKit.screenHeaderRule(shapeRenderer, NAME_X, UiKit.screenHeaderRuleY(TITLE_TOP_Y),
                               EXIT_X - ItemConstants.INV_ITEM_WIN_PADDING - NAME_X, 1f);
        float ruleInset = ItemConstants.INV_ITEM_WIN_RULE_INSET;
        drawPlainRule(WINDOW_X + ruleInset, HEADER_Y, WINDOW_W - ruleInset * 2f);
        drawPlainRule(WINDOW_X + ruleInset, FOOTER_RULE_Y, WINDOW_W - ruleInset * 2f);
        shapeRenderer.setColor(ArtDirection.UI_LINE_RED, ArtDirection.UI_LINE_GREEN, ArtDirection.UI_LINE_BLUE, 1f);
        shapeRenderer.rect(DIVIDER_X, BODY_Y + ruleInset, ArtDirection.UI_DIVIDER_THICKNESS,
                           HEADER_Y - BODY_Y - ruleInset * 2f);

        // Glyph box: a small raised panel framed in the item's (semantic) category colour
        drawGlyphBox();

        // Exit X plate
        UiKit.plate(shapeRenderer, EXIT_X, EXIT_Y, EXIT_SIZE, EXIT_SIZE, ArtDirection.UI_CHAMFER, PlateState.IDLE,
                    ItemConstants.INV_PANEL_ALPHA,
                    ArtDirection.SUIT_AMBER_RED, ArtDirection.SUIT_AMBER_GREEN, ArtDirection.SUIT_AMBER_BLUE);

        // Alternating stat row backgrounds (even rows = transparent = skip, odd = raised)
        ItemType statType = null;
        if (inventory != null && slotIndex >= 0) {
            ItemStack slot = inventory.getSlot(slotIndex);
            if (!slot.isEmpty()) statType = slot.getType();
        } else if (currentWeapon != null) {
            statType = currentWeapon.getItemType();
        }
        if (statType != null) {
            int rowCount = statRowCount(statType);
            shapeRenderer.setColor(ArtDirection.UI_PANEL_RAISED_RED, ArtDirection.UI_PANEL_RAISED_GREEN,
                                   ArtDirection.UI_PANEL_RAISED_BLUE, 1f);
            for (int rowIndex = 1; rowIndex < rowCount; rowIndex += 2) {
                float rowY = STAT_Y - rowIndex * STAT_ROW_H - STAT_ROW_H;
                shapeRenderer.rect(BODY_L_X, rowY, BODY_L_W, STAT_ROW_H);
            }
            // Ability row plates + type badges (weapons)
            if (statType.getCategory() == ItemCategory.WEAPON && cachedAbilities.length > 0) {
                renderAbilityRowBackgrounds();
            }
        }

        // Footer action plates: IDLE when the action applies, DISABLED otherwise
        drawActionPlate(BTN1_X, isButton1Enabled());
        drawActionPlate(BTN2_X, isButton2Enabled());

        shapeRenderer.end();
    }

    private void drawPlainRule(float x, float y, float width) {
        UiKit.divider(shapeRenderer, x, y, width,
                      ArtDirection.UI_LINE_RED, ArtDirection.UI_LINE_GREEN, ArtDirection.UI_LINE_BLUE, 1f);
    }

    private void drawActionPlate(float plateX, boolean enabled) {
        UiKit.plate(shapeRenderer, plateX, BTN_Y, BTN_W, BTN_H, ArtDirection.UI_CHAMFER,
                    enabled ? PlateState.IDLE : PlateState.DISABLED, ItemConstants.INV_PANEL_ALPHA,
                    ArtDirection.SUIT_AMBER_RED, ArtDirection.SUIT_AMBER_GREEN, ArtDirection.SUIT_AMBER_BLUE);
    }

    /** The header glyph box: a small raised panel framed in the item's (semantic) category colour. */
    private void drawGlyphBox() {
        glyphBoxBorder.set(ArtDirection.UI_LINE_RED, ArtDirection.UI_LINE_GREEN, ArtDirection.UI_LINE_BLUE, 1f);
        if (inventory != null && slotIndex >= 0) {
            ItemStack slot = inventory.getSlot(slotIndex);
            if (!slot.isEmpty()) ItemGridPanel.writeCategoryColor(slot.getType().getCategory(), glyphBoxBorder);
        } else if (currentWeapon != null) {
            ItemGridPanel.writeCategoryColor(ItemCategory.WEAPON, glyphBoxBorder);
        }
        UiKit.panel(shapeRenderer, GLYPH_X, GLYPH_Y, GLYPH_SIZE, GLYPH_SIZE, ArtDirection.UI_CHAMFER,
                    ArtDirection.UI_PANEL_RAISED_RED, ArtDirection.UI_PANEL_RAISED_GREEN,
                    ArtDirection.UI_PANEL_RAISED_BLUE, 1f,
                    glyphBoxBorder.r, glyphBoxBorder.g, glyphBoxBorder.b, 1f, ArtDirection.UI_BORDER_THICKNESS);
    }

    private void renderAbilityRowBackgrounds() {
        float abilityRowTop = DESC_Y - ABILITY_HDR_H;
        for (AbilityInstance instance : cachedAbilities) {
            float rowBottom = abilityRowTop - ABILITY_ROW_H;
            UiKit.plate(shapeRenderer, BODY_R_X, rowBottom, BODY_R_W, ABILITY_ROW_H, ArtDirection.UI_CHAMFER,
                        PlateState.IDLE, ItemConstants.INV_PANEL_ALPHA,
                        ArtDirection.SUIT_AMBER_RED, ArtDirection.SUIT_AMBER_GREEN, ArtDirection.SUIT_AMBER_BLUE);

            // Type badge: an outline chip in the badge's ink (PASSIVE secondary, ACTIVE amber)
            boolean isPassive = instance.ability.trigger == WeaponAbility.Trigger.PASSIVE;
            float badgeX = BODY_R_X + BODY_R_W - BADGE_W - ItemConstants.INV_ITEM_WIN_BADGE_MARGIN;
            float badgeY = rowBottom + (ABILITY_ROW_H - BADGE_H) / 2f;
            UiKit.panel(shapeRenderer, badgeX, badgeY, BADGE_W, BADGE_H, ArtDirection.UI_CHIP_CHAMFER,
                        ArtDirection.UI_PANEL_RAISED_RED, ArtDirection.UI_PANEL_RAISED_GREEN,
                        ArtDirection.UI_PANEL_RAISED_BLUE, 1f,
                        isPassive ? ArtDirection.UI_TEXT_SECONDARY_RED : ArtDirection.SUIT_AMBER_RED,
                        isPassive ? ArtDirection.UI_TEXT_SECONDARY_GREEN : ArtDirection.SUIT_AMBER_GREEN,
                        isPassive ? ArtDirection.UI_TEXT_SECONDARY_BLUE : ArtDirection.SUIT_AMBER_BLUE,
                        1f, ArtDirection.UI_CHIP_BORDER_THICKNESS);

            abilityRowTop -= (ABILITY_ROW_H + ABILITY_ROW_GAP);
        }
    }

    private void renderText(OrthographicCamera camera) {
        if (currentWeapon == null && (inventory == null || slotIndex < 0)) return;
        spriteBatch.setProjectionMatrix(camera.combined);
        spriteBatch.begin();

        if (currentWeapon != null) {
            renderTextForWeapon();
            spriteBatch.end();
            return;
        }

        ItemStack slot = inventory.getSlot(slotIndex);

        if (slot.isEmpty()) {
            font.getData().setScale(SCALE_BODY);
            setSecondary();
            glyphLayout.setText(font, "— EMPTY SLOT —");
            font.draw(spriteBatch, "— EMPTY SLOT —",
                      WINDOW_X + (WINDOW_W - glyphLayout.width) / 2f,
                      WINDOW_Y + WINDOW_H / 2f);
            font.getData().setScale(SCALE_CAPTION);
            renderFooterButtons(null, false);
            spriteBatch.end();
            return;
        }

        ItemType itemType  = slot.getType();
        boolean isEquipped = itemType.getCategory() == ItemCategory.WEAPON
                && inventory.getEquippedWeaponSlot() == slotIndex;

        renderHeader(itemType, isEquipped);
        renderBodyLeft(itemType, slot, isEquipped);
        renderBodyRight(itemType, slot);
        renderFooterButtons(itemType, isEquipped);
        renderFlashMessage();

        spriteBatch.end();
    }

    private void renderTextForWeapon() {
        ItemType itemType = currentWeapon.getItemType();
        if (itemType == null) {
            font.getData().setScale(SCALE_BODY);
            setSecondary();
            glyphLayout.setText(font, "— UNKNOWN WEAPON —");
            font.draw(spriteBatch, "— UNKNOWN WEAPON —",
                      WINDOW_X + (WINDOW_W - glyphLayout.width) / 2f, WINDOW_Y + WINDOW_H / 2f);
            font.getData().setScale(SCALE_CAPTION);
            return;
        }
        renderHeader(itemType, weaponIsActive);
        renderBodyLeftForWeapon(itemType);
        renderBodyRight(itemType, null);
        renderFooterButtons(itemType, weaponIsActive);
        renderFlashMessage();
    }

    private void renderBodyLeftForWeapon(ItemType itemType) {
        drawDescription(itemType);

        font.getData().setScale(SCALE_CAPTION);
        renderStatRowsForWeapon();

        if (weaponIsActive) drawInUseNotice("● CURRENTLY ACTIVE");
    }

    /**
     * The description, primary, at TYPE_CAPTION: the fixed 80-unit band above the stat block holds three
     * caption lines of the longest description (81 chars), where TYPE_BODY would run into the stats.
     */
    private void drawDescription(ItemType itemType) {
        font.getData().setScale(SCALE_CAPTION);
        setPrimary();
        font.draw(spriteBatch, itemType.getDescription(), BODY_L_X, DESC_Y, BODY_L_W, Align.left, true);
    }

    /** "Currently equipped / active" — the suit's amber marks the thing in use. */
    private void drawInUseNotice(String notice) {
        font.getData().setScale(SCALE_CAPTION);
        setAmber();
        font.draw(spriteBatch, notice, BODY_L_X, BODY_Y + ItemConstants.INV_ITEM_WIN_NOTICE_RISE);
    }

    private void setPrimary() {
        font.setColor(ArtDirection.UI_TEXT_PRIMARY_RED, ArtDirection.UI_TEXT_PRIMARY_GREEN,
                      ArtDirection.UI_TEXT_PRIMARY_BLUE, 1f);
    }

    private void setSecondary() {
        font.setColor(ArtDirection.UI_TEXT_SECONDARY_RED, ArtDirection.UI_TEXT_SECONDARY_GREEN,
                      ArtDirection.UI_TEXT_SECONDARY_BLUE, 1f);
    }

    private void setDisabled() {
        font.setColor(ArtDirection.UI_TEXT_DISABLED_RED, ArtDirection.UI_TEXT_DISABLED_GREEN,
                      ArtDirection.UI_TEXT_DISABLED_BLUE, 1f);
    }

    private void setAmber() {
        font.setColor(ArtDirection.SUIT_AMBER_RED, ArtDirection.SUIT_AMBER_GREEN, ArtDirection.SUIT_AMBER_BLUE, 1f);
    }

    private void renderStatRowsForWeapon() {
        float rowY = STAT_Y;
        valueBuilder.setLength(0);
        // EFFECTIVE per-hit damage on this floor (ladder applied) beside the weapon's level (R14 c).
        valueBuilder.append(currentWeapon.getEffectiveDamage())
                    .append("   ").append(WeaponLevelTag.label(currentWeapon.getWeaponLevel()));
        rowY = drawStatRow(rowY, "Damage",    valueBuilder);
        rowY = drawStatRow(rowY, "Range",     weaponRange(currentWeapon.getItemType()));
        rowY = drawStatRow(rowY, "Ammo",      weaponAmmoType(currentWeapon.getItemType()));
        valueBuilder.setLength(0);
        valueBuilder.append(currentWeapon.getShotsInClip())
                    .append(" / ")
                    .append(currentWeapon.getEffectiveClipSize());
        rowY = drawStatRow(rowY, "Clip",      valueBuilder);
        drawStatRow(rowY, "Fire Mode", weaponFireMode(currentWeapon.getItemType()));
    }

    // =========================================================================
    // Private — text sub-renders
    // =========================================================================

    private void renderHeader(ItemType itemType, boolean isEquipped) {
        // Icon (reused ground-pickup sprite) when available; otherwise the ASCII glyph.
        Texture iconTexture = iconTextures.get(itemType);
        if (iconTexture != null) {
            drawGlyphBoxIcon(iconTexture);
        } else {
            font.getData().setScale(SCALE_GLYPH);
            // Item art: the ASCII glyph in the item's own colour (an icon routine, not chrome).
            font.setColor(itemType.getGlyphRed(), itemType.getGlyphGreen(), itemType.getGlyphBlue(), 1f);
            valueBuilder.setLength(0);
            valueBuilder.append(itemType.getGlyph());
            glyphLayout.setText(font, valueBuilder);
            font.draw(spriteBatch, valueBuilder,
                      GLYPH_X + (GLYPH_SIZE - glyphLayout.width)  / 2f,
                      GLYPH_Y + (GLYPH_SIZE + glyphLayout.height) / 2f);
        }

        // Item name — the window's R1 screen header (HEAVY, TYPE_HEADING, primary)
        UiKit.screenHeaderTitle(spriteBatch, headingFont, itemType.getDisplayName(), NAME_X, TITLE_TOP_Y);

        // Category badge — a caption under the rule
        font.getData().setScale(SCALE_CAPTION);
        setSecondary();
        font.draw(spriteBatch, categoryBadge(itemType), NAME_X,
                  TITLE_TOP_Y - ItemConstants.INV_ITEM_WIN_HEADER_NAME_GAP);

        // Exit button "X"
        font.getData().setScale(SCALE_BODY);
        setPrimary();
        glyphLayout.setText(font, "X");
        font.draw(spriteBatch, "X",
                  EXIT_X + (EXIT_SIZE - glyphLayout.width)  / 2f,
                  EXIT_Y + (EXIT_SIZE + glyphLayout.height) / 2f);
        font.getData().setScale(SCALE_CAPTION);
    }

    // Draws a ground-pickup icon centered and aspect-fit inside the header glyph box.
    private void drawGlyphBoxIcon(Texture iconTexture) {
        float srcWidth  = iconTexture.getWidth();
        float srcHeight = iconTexture.getHeight();
        if (srcWidth <= 0f || srcHeight <= 0f) return;

        float availableSize = GLYPH_SIZE - 2f * ItemConstants.INV_ITEM_WIN_ICON_MARGIN;
        float scale          = Math.min(availableSize / srcWidth, availableSize / srcHeight);
        float drawWidth      = srcWidth  * scale;
        float drawHeight     = srcHeight * scale;
        float drawX          = GLYPH_X + (GLYPH_SIZE - drawWidth)  / 2f;
        float drawY          = GLYPH_Y + (GLYPH_SIZE - drawHeight) / 2f;

        spriteBatch.draw(iconTexture, drawX, drawY, drawWidth, drawHeight);
    }

    private void renderBodyLeft(ItemType itemType, ItemStack slot, boolean isEquipped) {
        drawDescription(itemType);

        // Stat block (dense label / value rows: TYPE_CAPTION)
        font.getData().setScale(SCALE_CAPTION);
        renderStatRows(itemType, slot);

        // Equipped notice (weapons only)
        if (isEquipped) drawInUseNotice("● CURRENTLY EQUIPPED");
    }

    private void renderStatRows(ItemType itemType, ItemStack slot) {
        ItemCategory category = itemType.getCategory();
        float rowY = STAT_Y;

        if (category == ItemCategory.WEAPON) {
            rowY = drawStatRow(rowY, "Damage",    weaponDamage(itemType));
            rowY = drawStatRow(rowY, "Range",     weaponRange(itemType));
            rowY = drawStatRow(rowY, "Ammo",      weaponAmmoType(itemType));
            rowY = drawStatRow(rowY, "Clip",      weaponClip(itemType));
                   drawStatRow(rowY, "Fire Mode", weaponFireMode(itemType));
        } else if (category == ItemCategory.CONSUMABLE) {
            rowY = drawStatRow(rowY, "Heals",    consumableHeal(itemType));
            valueBuilder.setLength(0);
            valueBuilder.append(slot.getQuantity()).append(" in bag");
            rowY = drawStatRow(rowY, "Quantity", valueBuilder);
            String effect = consumableEffect(itemType);
            if (effect != null) drawStatRow(rowY, "Effect", effect);
        } else if (category == ItemCategory.AMMO) {
            rowY = drawStatRow(rowY, "Type",    itemType.getDisplayName());
            valueBuilder.setLength(0);
            valueBuilder.append(slot.getQuantity()).append(" / ").append(itemType.getMaxStackSize());
            rowY = drawStatRow(rowY, "Reserve", valueBuilder);
                   drawStatRow(rowY, "Per Box", ammoPerBox(itemType));
        } else if (category == ItemCategory.KEY_ITEM) {
            rowY = drawStatRow(rowY, "Type",   "ACCESS KEYCARD");
            rowY = drawStatRow(rowY, "Color",  keycardColor(itemType));
                   drawStatRow(rowY, "Status", "Carried");
        } else {
            valueBuilder.setLength(0);
            valueBuilder.append(slot.getQuantity());
            rowY = drawStatRow(rowY, "Quantity", valueBuilder);
            if (itemType == ItemType.CREDITS) {
                valueBuilder.setLength(0);
                valueBuilder.append(slot.getQuantity()).append(" cr");
                drawStatRow(rowY, "Value", valueBuilder);
            }
        }
    }

    private float drawStatRow(float rowY, CharSequence label, CharSequence value) {
        float inset = ItemConstants.INV_ITEM_WIN_STAT_TEXT_INSET;
        setSecondary();
        font.draw(spriteBatch, label, BODY_L_X + inset, rowY);
        setPrimary();
        glyphLayout.setText(font, value);
        font.draw(spriteBatch, value, BODY_L_X + BODY_L_W - glyphLayout.width - inset, rowY);
        return rowY - STAT_ROW_H;
    }

    private void renderBodyRight(ItemType itemType, ItemStack slot) {
        float headerY = DESC_Y;
        ItemCategory category = itemType.getCategory();

        // Section label (R6: TYPE_LABEL, secondary)
        font.getData().setScale(SCALE_SECTION);
        setSecondary();
        font.draw(spriteBatch, rightColumnTitle(category), BODY_R_X, headerY);

        float contentY = headerY - ABILITY_HDR_H;
        font.getData().setScale(SCALE_BODY);

        if (category == ItemCategory.WEAPON) {
            if (cachedAbilities.length == 0) {
                setSecondary();
                font.draw(spriteBatch, "No special abilities.", BODY_R_X, contentY);
            } else {
                float abilityRowTop = contentY;
                for (int abilityIndex = 0; abilityIndex < cachedAbilities.length; abilityIndex++) {
                    AbilityInstance instance = cachedAbilities[abilityIndex];
                    float rowBottom = abilityRowTop - ABILITY_ROW_H;

                    // The ability's HUD glyph (an icon) in the suit's amber, left of the row
                    font.getData().setScale(SCALE_SECTION);
                    setAmber();
                    valueBuilder.setLength(0);
                    valueBuilder.append(instance.ability.hudGlyph);
                    font.draw(spriteBatch, valueBuilder,
                              BODY_R_X + ItemConstants.INV_ITEM_WIN_STAT_TEXT_INSET,
                              rowBottom + ABILITY_ROW_H - ItemConstants.INV_ITEM_WIN_ABILITY_NAME_DROP);

                    // Ability display name (primary)
                    font.getData().setScale(SCALE_BODY);
                    float nameTextX = BODY_R_X + ItemConstants.INV_ITEM_WIN_ABILITY_TEXT_X;
                    setPrimary();
                    font.draw(spriteBatch, instance.ability.displayName,
                              nameTextX,
                              rowBottom + ABILITY_ROW_H - ItemConstants.INV_ITEM_WIN_ABILITY_NAME_DROP);

                    // Short hint — pre-built with exact numbers in open() / openWeapon()
                    font.getData().setScale(SCALE_CAPTION);
                    setSecondary();
                    float hintWidth = BODY_R_W - ItemConstants.INV_ITEM_WIN_ABILITY_TEXT_X - BADGE_W
                            - ItemConstants.INV_ITEM_WIN_BADGE_MARGIN * 2f;
                    font.draw(spriteBatch, cachedShortHints[abilityIndex],
                              nameTextX,
                              rowBottom + ABILITY_ROW_H - ItemConstants.INV_ITEM_WIN_ABILITY_HINT_DROP,
                              hintWidth, Align.left, true);

                    // Type badge text (PASSIVE secondary / ACTIVE amber), centred in its outline chip
                    boolean isPassive = instance.ability.trigger == WeaponAbility.Trigger.PASSIVE;
                    if (isPassive) setSecondary(); else setAmber();
                    String badgeLabel = instance.ability.getTypeLabel();
                    glyphLayout.setText(font, badgeLabel);
                    float badgeX = BODY_R_X + BODY_R_W - BADGE_W - ItemConstants.INV_ITEM_WIN_BADGE_MARGIN;
                    float badgeTextY = rowBottom + (ABILITY_ROW_H + glyphLayout.height) / 2f;
                    font.draw(spriteBatch, badgeLabel,
                              badgeX + (BADGE_W - glyphLayout.width) / 2f,
                              badgeTextY);

                    abilityRowTop -= (ABILITY_ROW_H + ABILITY_ROW_GAP);
                }
            }

        } else if (category == ItemCategory.CONSUMABLE) {
            setPrimary();
            font.draw(spriteBatch, consumableGuidance(itemType), BODY_R_X, contentY, BODY_R_W, Align.left, true);
            font.getData().setScale(SCALE_CAPTION);
            setSecondary();
            font.draw(spriteBatch, "QUANTITY IN BAG", BODY_R_X, BODY_Y + ItemConstants.INV_ITEM_WIN_QUANTITY_LABEL_RISE);
            font.getData().setScale(SCALE_QUANTITY);
            setPrimary();
            valueBuilder.setLength(0);
            valueBuilder.append(slot.getQuantity());
            glyphLayout.setText(font, valueBuilder);
            font.draw(spriteBatch, valueBuilder,
                      BODY_R_X + (BODY_R_W - glyphLayout.width) / 2f,
                      BODY_Y + ItemConstants.INV_ITEM_WIN_QUANTITY_VALUE_RISE);

        } else if (category == ItemCategory.AMMO) {
            setPrimary();
            font.draw(spriteBatch, ammoCompatible(itemType), BODY_R_X, contentY, BODY_R_W, Align.left, true);

        } else if (category == ItemCategory.KEY_ITEM) {
            setPrimary();
            font.draw(spriteBatch, "Opens doors marked with this keycard color.",
                      BODY_R_X, contentY, BODY_R_W, Align.left, true);
            font.getData().setScale(SCALE_CAPTION);
            setSecondary();
            font.draw(spriteBatch, "Persistent: keycards stay through level transitions.",
                      BODY_R_X, contentY - ItemConstants.INV_ITEM_WIN_KEY_NOTE_DROP, BODY_R_W, Align.left, true);

        } else {
            setPrimary();
            font.draw(spriteBatch, "UAC standard currency. Accepted at facility terminals.",
                      BODY_R_X, contentY, BODY_R_W, Align.left, true);
        }

        font.getData().setScale(SCALE_CAPTION);
    }

    private void renderFooterButtons(ItemType itemType, boolean isEquipped) {
        font.getData().setScale(SCALE_BODY);
        boolean btn1On = isButton1Enabled();
        String  btn1Label = itemType != null ? button1Label(itemType.getCategory(), isEquipped) : "—";

        if (btn1On) setPrimary(); else setDisabled();
        glyphLayout.setText(font, btn1Label);
        font.draw(spriteBatch, btn1Label,
                  BTN1_X + (BTN_W - glyphLayout.width)  / 2f,
                  BTN_Y  + (BTN_H + glyphLayout.height) / 2f);

        boolean btn2On = isButton2Enabled();
        if (btn2On) setPrimary(); else setDisabled();
        glyphLayout.setText(font, "DROP");
        font.draw(spriteBatch, "DROP",
                  BTN2_X + (BTN_W - glyphLayout.width)  / 2f,
                  BTN_Y  + (BTN_H + glyphLayout.height) / 2f);
        font.getData().setScale(SCALE_CAPTION);
    }

    private void renderFlashMessage() {
        if (flashTimerSeconds <= 0f || flashMessage == null) return;
        font.getData().setScale(SCALE_BODY);
        font.setColor(CombatPalette.DAMAGE_RED, CombatPalette.DAMAGE_GREEN, CombatPalette.DAMAGE_BLUE, 1f);
        glyphLayout.setText(font, flashMessage);
        font.draw(spriteBatch, flashMessage,
                  WINDOW_X + (WINDOW_W - glyphLayout.width) / 2f,
                  BTN_Y + BTN_H + ItemConstants.INV_ITEM_WIN_FLASH_RISE);
        font.getData().setScale(SCALE_CAPTION);
    }

    // =========================================================================
    // Private — button state
    // =========================================================================

    private boolean isButton1Enabled() {
        // Weapon-mode: equipping is handled via the main touch controls, not here.
        if (currentWeapon != null) return false;
        if (inventory == null || slotIndex < 0) return false;
        ItemStack slot = inventory.getSlot(slotIndex);
        if (slot.isEmpty()) return false;
        ItemCategory cat = slot.getType().getCategory();
        return cat == ItemCategory.WEAPON || cat == ItemCategory.CONSUMABLE;
    }

    private boolean isButton2Enabled() {
        // Weapon-mode: loadout weapons cannot be dropped from this window.
        if (currentWeapon != null) return false;
        if (inventory == null || slotIndex < 0) return false;
        ItemStack slot = inventory.getSlot(slotIndex);
        return !slot.isEmpty() && slot.getType().getCategory() != ItemCategory.KEY_ITEM;
    }

    private static String button1Label(ItemCategory category, boolean isEquipped) {
        switch (category) {
            case WEAPON:     return isEquipped ? "UNEQUIP" : "EQUIP";
            case CONSUMABLE: return "USE";
            case AMMO:       return "Auto-Loaded";
            case KEY_ITEM:   return "Quest Item";
            default:         return "No Use";
        }
    }

    // =========================================================================
    // Private — static label helpers (no allocation)
    // =========================================================================

    private static String rightColumnTitle(ItemCategory category) {
        switch (category) {
            case WEAPON:     return "ABILITIES";
            case CONSUMABLE: return "HOW TO USE";
            case AMMO:       return "COMPATIBLE WEAPONS";
            case KEY_ITEM:   return "SECURITY TIER";
            default:         return "INFO";
        }
    }

    private static String categoryBadge(ItemType itemType) {
        switch (itemType.getCategory()) {
            case WEAPON:     return "WEAPON · " + weaponSubclass(itemType);
            case CONSUMABLE: return "CONSUMABLE · MEDKIT";
            case AMMO:       return "AMMO · " + itemType.getDisplayName().toUpperCase();
            case KEY_ITEM:   return "KEY ITEM · KEYCARD";
            case MISC:       return "MISC · CURRENCY";
            default:         return itemType.getCategory().name();
        }
    }

    private static String weaponSubclass(ItemType itemType) {
        switch (itemType) {
            case WEAPON_SHOTGUN:
            case WEAPON_DOUBLE_BARREL: return "SHOTGUN CLASS";
            case WEAPON_CHAINGUN:
            case WEAPON_ASSAULT_RIFLE: return "AUTOMATIC CLASS";
            case WEAPON_PLASMA:
            case WEAPON_INCINERATOR:
            case WEAPON_ARC_CANNON:    return "ENERGY CLASS";
            case WEAPON_RAILGUN:       return "PRECISION CLASS";
            case WEAPON_ROCKET:        return "EXPLOSIVE CLASS";
            case WEAPON_FIST:
            case WEAPON_KNIFE:
            case WEAPON_HAMMER:
            case WEAPON_CHAINSAW:      return "MELEE CLASS";
            default:                   return "RANGED CLASS";
        }
    }

    /** Railgun charge-1 .. full-charge damage, derived from the charge table so it cannot drift from balance. */
    private static final String RAILGUN_DAMAGE_RANGE_TEXT = WeaponConstants.RAILGUN_DAMAGE_BY_CHARGE[1] + "-"
            + WeaponConstants.RAILGUN_DAMAGE_BY_CHARGE[WeaponConstants.RAILGUN_DAMAGE_BY_CHARGE.length - 1];

    private static String weaponDamage(ItemType itemType) {
        switch (itemType) {
            case WEAPON_SHOTGUN:       return String.valueOf(WeaponConstants.SHOTGUN_DAMAGE);
            case WEAPON_DOUBLE_BARREL: return WeaponConstants.DBL_SHOTGUN_DAMAGE + " x2";
            case WEAPON_PLASMA:        return String.valueOf(WeaponConstants.PLASMA_RIFLE_DAMAGE);
            case WEAPON_CHAINGUN:      return WeaponConstants.CHAINGUN_DAMAGE + "/bolt";
            case WEAPON_ASSAULT_RIFLE: return WeaponConstants.ASSAULT_RIFLE_DAMAGE + "/round";
            case WEAPON_RAILGUN:       return RAILGUN_DAMAGE_RANGE_TEXT;
            case WEAPON_INCINERATOR:   return WeaponConstants.FLAME_IMPACT_DAMAGE + "+" + WeaponConstants.FLAME_BURN_DAMAGE_PER_STACK + "/turn";
            case WEAPON_ARC_CANNON:    return WeaponConstants.ARC_CANNON_DAMAGE + "+chain";
            case WEAPON_ROCKET:        return String.valueOf(WeaponConstants.GRENADE_SPLASH_DAMAGE);
            case WEAPON_FIST:          return "4";
            case WEAPON_KNIFE:         return "8";
            case WEAPON_HAMMER:        return "16";
            case WEAPON_CHAINSAW:      return "12/turn";
            default:                   return "—";
        }
    }

    private static String weaponRange(ItemType itemType) {
        switch (itemType) {
            case WEAPON_FIST:
            case WEAPON_KNIFE:
            case WEAPON_HAMMER:
            case WEAPON_CHAINSAW:
            case WEAPON_SHOTGUN:
            case WEAPON_DOUBLE_BARREL: return "Short";
            case WEAPON_RAILGUN:       return "Long";
            default:                   return "Medium";
        }
    }

    private static String weaponAmmoType(ItemType itemType) {
        switch (itemType) {
            case WEAPON_SHOTGUN:
            case WEAPON_DOUBLE_BARREL: return "Shells";
            case WEAPON_CHAINGUN:
            case WEAPON_ASSAULT_RIFLE: return "Bullets";
            case WEAPON_PLASMA:
            case WEAPON_INCINERATOR:
            case WEAPON_ARC_CANNON:    return "Cells";
            case WEAPON_ROCKET:        return "Rockets";
            case WEAPON_RAILGUN:       return "Slugs";
            default:                   return "None (melee)";
        }
    }

    private static String weaponClip(ItemType itemType) {
        switch (itemType) {
            case WEAPON_SHOTGUN:       return "— / " + WeaponConstants.SHOTGUN_CLIP_SIZE;
            case WEAPON_DOUBLE_BARREL: return "— / " + WeaponConstants.DBL_SHOTGUN_CLIP_SIZE;
            case WEAPON_PLASMA:        return "— / " + WeaponConstants.PLASMA_RIFLE_CLIP_SIZE;
            case WEAPON_CHAINGUN:      return "— / " + WeaponConstants.CHAINGUN_CLIP_SIZE;
            case WEAPON_ASSAULT_RIFLE: return "— / " + WeaponConstants.ASSAULT_RIFLE_CLIP_SIZE;
            case WEAPON_RAILGUN:       return "— / " + WeaponConstants.RAILGUN_CLIP_SIZE;
            case WEAPON_INCINERATOR:   return "— / " + WeaponConstants.FLAME_CLIP_SIZE;
            case WEAPON_ARC_CANNON:    return "— / " + WeaponConstants.ARC_CANNON_CLIP_SIZE;
            case WEAPON_ROCKET:        return "— / " + WeaponConstants.GRENADE_CLIP_SIZE;
            default:                   return "—";
        }
    }

    private static String weaponFireMode(ItemType itemType) {
        switch (itemType) {
            case WEAPON_CHAINGUN:    return "Burst";
            case WEAPON_ASSAULT_RIFLE: return "Semi";
            case WEAPON_INCINERATOR: return "Full-Auto";
            case WEAPON_FIST:
            case WEAPON_KNIFE:
            case WEAPON_HAMMER:
            case WEAPON_CHAINSAW:    return "Melee";
            default:                 return "Semi";
        }
    }

    private static String consumableHeal(ItemType itemType) {
        switch (itemType) {
            case MEDKIT_SMALL: return "+" + Math.round(ItemConstants.MEDKIT_STIM_HEAL_FRACTION * 100f) + "% HP";
            case MEDKIT_LARGE: return "+" + Math.round(ItemConstants.MEDKIT_FULL_HEAL_FRACTION * 100f) + "% HP";
            case STIMPACK:     return "+15 HP";
            default:           return "—";
        }
    }

    private static String consumableEffect(ItemType itemType) {
        if (itemType == ItemType.STIMPACK) return "Combat boost";
        return null;
    }

    private static String consumableGuidance(ItemType itemType) {
        switch (itemType) {
            case MEDKIT_SMALL: return "Use when health is low. Restores a small amount instantly.";
            case MEDKIT_LARGE: return "Use when critically injured. Restores significant health.";
            case STIMPACK:     return "Quick combat boost. Use under fire for immediate recovery.";
            default:           return "Use from the inventory.";
        }
    }

    private static String ammoPerBox(ItemType itemType) {
        switch (itemType) {
            case AMMO_BULLETS: return String.valueOf(ItemConstants.AMMO_BOX_BULLETS);
            case AMMO_SHELLS:  return String.valueOf(ItemConstants.AMMO_BOX_SHELLS);
            case AMMO_CELLS:   return String.valueOf(ItemConstants.AMMO_BOX_CELLS);
            case AMMO_ROCKETS: return String.valueOf(ItemConstants.AMMO_BOX_ROCKETS);
            default:           return "—";
        }
    }

    private static String ammoCompatible(ItemType itemType) {
        switch (itemType) {
            case AMMO_BULLETS: return "Used by: Chaingun, Assault Rifle";
            case AMMO_SHELLS:  return "Used by: Shotgun, Double-Barrel Shotgun";
            case AMMO_CELLS:   return "Used by: Plasma Rifle, Incinerator";
            case AMMO_ROCKETS: return "Used by: Rocket Launcher";
            case AMMO_SLUGS:   return "Used by: Railgun";
            default:           return "—";
        }
    }

    private static String keycardColor(ItemType itemType) {
        switch (itemType) {
            case KEYCARD_RED:    return "Red";
            case KEYCARD_YELLOW: return "Yellow";
            case KEYCARD_BLUE:   return "Blue";
            default:             return "—";
        }
    }

    private static int statRowCount(ItemType itemType) {
        switch (itemType.getCategory()) {
            case WEAPON:     return 5;
            case CONSUMABLE: return consumableEffect(itemType) != null ? 3 : 2;
            case AMMO:       return 3;
            case KEY_ITEM:   return 3;
            default:         return itemType == ItemType.CREDITS ? 2 : 1;
        }
    }
}
