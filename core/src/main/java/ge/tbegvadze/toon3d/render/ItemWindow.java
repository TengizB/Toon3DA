package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.item.Inventory;
import ge.tbegvadze.toon3d.item.ItemCategory;
import ge.tbegvadze.toon3d.item.ItemStack;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.util.ItemConstants;
import ge.tbegvadze.toon3d.util.WeaponConstants;

/**
 * Part 4 ItemWindow — modal popup shown when a weapon or inventory slot is tapped.
 *
 * Three-zone layout:
 *   Header (top 52px)  — glyph box, item name, category badge, amber exit button
 *   Body   (middle)    — left column: description + stat block;
 *                        right column: abilities (weapons) or category-specific info
 *   Footer (bottom 56px) — two action buttons (use/equip + drop)
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

    // Body: fixed Y at which the description block begins (just below header rule)
    private static final float DESC_Y        = HEADER_Y - 10f;
    // Body: fixed Y at which the stat block begins (leaves ~72px for 4 description lines)
    private static final float STAT_Y        = DESC_Y - 80f;

    // -------------------------------------------------------------------------
    // Palette — UAC military CRT amber-on-steel
    // -------------------------------------------------------------------------

    private static final Color WINDOW_BG        = new Color(0.07f,  0.07f,  0.09f,  0.97f);
    private static final Color HEADER_BG        = new Color(0.09f,  0.09f,  0.11f,  1.00f);
    private static final Color SLOT_BG_NORMAL   = new Color(0.10f,  0.10f,  0.12f,  1.00f);
    private static final Color ALT_ROW_BG       = new Color(0.105f, 0.105f, 0.13f,  1.00f);
    private static final Color PANEL_BORDER     = new Color(0.22f,  0.22f,  0.30f,  1.00f);
    private static final Color AMBER_BORDER     = new Color(1.00f,  0.72f,  0.00f,  0.85f);
    private static final Color AMBER            = new Color(1.00f,  0.75f,  0.10f,  1.00f);
    private static final Color TEXT_DIM         = new Color(0.50f,  0.50f,  0.55f,  1.00f);
    private static final Color TEXT_DISABLED    = new Color(0.30f,  0.30f,  0.32f,  1.00f);
    private static final Color OFF_WHITE        = new Color(0.80f,  0.80f,  0.85f,  1.00f);
    private static final Color GREEN_EQUIPPED   = new Color(0.20f,  0.90f,  0.30f,  1.00f);
    private static final Color EXIT_BTN_BG      = new Color(0.70f,  0.20f,  0.15f,  1.00f);
    private static final Color EXIT_BTN_TEXT    = new Color(1.00f,  0.85f,  0.85f,  1.00f);
    private static final Color BTN_BG_ON        = new Color(0.10f,  0.10f,  0.12f,  1.00f);
    private static final Color BTN_BG_OFF       = new Color(0.07f,  0.07f,  0.09f,  1.00f);
    private static final Color BTN_BORDER_ON    = new Color(0.50f,  0.50f,  0.55f,  1.00f);
    private static final Color BTN_BORDER_OFF   = new Color(0.30f,  0.30f,  0.32f,  1.00f);
    private static final Color RED_FLASH        = new Color(0.95f,  0.15f,  0.15f,  1.00f);

    // Category glyph-box border colors
    private static final Color CAT_WEAPON       = new Color(1.00f, 0.72f, 0.00f, 1.00f);
    private static final Color CAT_CONSUMABLE   = new Color(0.20f, 0.90f, 0.20f, 1.00f);
    private static final Color CAT_AMMO         = new Color(0.75f, 0.45f, 0.10f, 1.00f);
    private static final Color CAT_KEY_ITEM     = new Color(1.00f, 0.10f, 0.10f, 1.00f);
    private static final Color CAT_MOD          = new Color(0.60f, 0.30f, 0.90f, 1.00f);
    private static final Color CAT_MISC         = new Color(0.75f, 0.75f, 0.75f, 1.00f);

    // -------------------------------------------------------------------------
    // Shared rendering resources (owned by coordinator, not disposed here)
    // -------------------------------------------------------------------------

    private final ShapeRenderer shapeRenderer;
    private final SpriteBatch   spriteBatch;
    private final BitmapFont    font;
    private final GlyphLayout   glyphLayout;

    // Pre-allocated scratch buffer for dynamic stat value strings
    private final StringBuilder valueBuilder;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private Inventory inventory;
    private int       slotIndex         = -1;
    private String    flashMessage      = null;
    private float     flashTimerSeconds = 0f;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    ItemWindow(ShapeRenderer shapeRenderer, SpriteBatch spriteBatch,
               BitmapFont font, GlyphLayout glyphLayout) {
        this.shapeRenderer = shapeRenderer;
        this.spriteBatch   = spriteBatch;
        this.font          = font;
        this.glyphLayout   = glyphLayout;
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
    }

    boolean isOpen() {
        return slotIndex >= 0;
    }

    void close() {
        slotIndex = -1;
        inventory = null;
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
        renderLines(camera);
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

    private void renderFilled(OrthographicCamera camera) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Window background
        shapeRenderer.setColor(WINDOW_BG);
        shapeRenderer.rect(WINDOW_X, WINDOW_Y, WINDOW_W, WINDOW_H);

        // Header background (slightly lighter than window)
        shapeRenderer.setColor(HEADER_BG);
        shapeRenderer.rect(WINDOW_X, HEADER_Y, WINDOW_W, HEADER_H);

        // Glyph box background
        shapeRenderer.setColor(SLOT_BG_NORMAL);
        shapeRenderer.rect(GLYPH_X, GLYPH_Y, GLYPH_SIZE, GLYPH_SIZE);

        // Exit button background
        shapeRenderer.setColor(EXIT_BTN_BG);
        shapeRenderer.rect(EXIT_X, EXIT_Y, EXIT_SIZE, EXIT_SIZE);

        // Alternating stat row backgrounds (even rows = transparent = skip, odd = dim highlight)
        if (inventory != null && slotIndex >= 0) {
            ItemStack slot = inventory.getSlot(slotIndex);
            if (!slot.isEmpty()) {
                int rowCount = statRowCount(slot.getType());
                for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
                    if (rowIndex % 2 == 1) {
                        float rowY = STAT_Y - rowIndex * STAT_ROW_H - STAT_ROW_H;
                        shapeRenderer.setColor(ALT_ROW_BG);
                        shapeRenderer.rect(BODY_L_X, rowY, BODY_L_W, STAT_ROW_H);
                    }
                }
            }
        }

        // Footer action button backgrounds
        boolean btn1On = isButton1Enabled();
        shapeRenderer.setColor(btn1On ? BTN_BG_ON : BTN_BG_OFF);
        shapeRenderer.rect(BTN1_X, BTN_Y, BTN_W, BTN_H);

        boolean btn2On = isButton2Enabled();
        shapeRenderer.setColor(btn2On ? BTN_BG_ON : BTN_BG_OFF);
        shapeRenderer.rect(BTN2_X, BTN_Y, BTN_W, BTN_H);

        shapeRenderer.end();
    }

    private void renderLines(OrthographicCamera camera) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        // Window outer border (amber)
        shapeRenderer.setColor(AMBER_BORDER);
        shapeRenderer.rect(WINDOW_X, WINDOW_Y, WINDOW_W, WINDOW_H);

        // Header bottom rule (amber)
        shapeRenderer.line(WINDOW_X + 10f, HEADER_Y, WINDOW_X + WINDOW_W - 10f, HEADER_Y);

        // Footer top rule (amber)
        shapeRenderer.line(WINDOW_X + 10f, FOOTER_RULE_Y, WINDOW_X + WINDOW_W - 10f, FOOTER_RULE_Y);

        // Body column divider
        shapeRenderer.setColor(PANEL_BORDER);
        shapeRenderer.line(DIVIDER_X, BODY_Y + 8f, DIVIDER_X, HEADER_Y - 8f);

        // Glyph box border (category color or dim if empty)
        if (inventory != null && slotIndex >= 0) {
            ItemStack slot = inventory.getSlot(slotIndex);
            shapeRenderer.setColor(slot.isEmpty() ? PANEL_BORDER : categoryColor(slot.getType().getCategory()));
        } else {
            shapeRenderer.setColor(PANEL_BORDER);
        }
        shapeRenderer.rect(GLYPH_X, GLYPH_Y, GLYPH_SIZE, GLYPH_SIZE);

        // Exit button border
        shapeRenderer.setColor(EXIT_BTN_TEXT);
        shapeRenderer.rect(EXIT_X, EXIT_Y, EXIT_SIZE, EXIT_SIZE);

        // Action button borders
        shapeRenderer.setColor(isButton1Enabled() ? BTN_BORDER_ON : BTN_BORDER_OFF);
        shapeRenderer.rect(BTN1_X, BTN_Y, BTN_W, BTN_H);
        shapeRenderer.setColor(isButton2Enabled() ? BTN_BORDER_ON : BTN_BORDER_OFF);
        shapeRenderer.rect(BTN2_X, BTN_Y, BTN_W, BTN_H);

        shapeRenderer.end();
    }

    private void renderText(OrthographicCamera camera) {
        if (inventory == null || slotIndex < 0) return;
        spriteBatch.setProjectionMatrix(camera.combined);
        spriteBatch.begin();

        ItemStack slot = inventory.getSlot(slotIndex);

        if (slot.isEmpty()) {
            font.setColor(TEXT_DIM);
            font.draw(spriteBatch, "— EMPTY SLOT —",
                      WINDOW_X + WINDOW_W / 2f - 48f,
                      WINDOW_Y + WINDOW_H / 2f);
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

    // =========================================================================
    // Private — text sub-renders
    // =========================================================================

    private void renderHeader(ItemType itemType, boolean isEquipped) {
        // Glyph (large, item color)
        font.getData().setScale(2.5f);
        font.setColor(itemType.getGlyphRed(), itemType.getGlyphGreen(), itemType.getGlyphBlue(), 1f);
        valueBuilder.setLength(0);
        valueBuilder.append(itemType.getGlyph());
        glyphLayout.setText(font, valueBuilder);
        font.draw(spriteBatch, valueBuilder,
                  GLYPH_X + (GLYPH_SIZE - glyphLayout.width)  / 2f,
                  GLYPH_Y + (GLYPH_SIZE + glyphLayout.height) / 2f);
        font.getData().setScale(1f);

        // Item name (amber, slightly scaled up)
        float nameY = HEADER_Y + HEADER_H - 10f;
        font.getData().setScale(1.3f);
        font.setColor(AMBER);
        font.draw(spriteBatch, itemType.getDisplayName(), NAME_X, nameY);
        font.getData().setScale(1f);

        // Category badge (dim, small)
        font.getData().setScale(0.85f);
        font.setColor(TEXT_DIM);
        font.draw(spriteBatch, categoryBadge(itemType), NAME_X, nameY - 22f);
        font.getData().setScale(1f);

        // Exit button "X"
        font.setColor(EXIT_BTN_TEXT);
        glyphLayout.setText(font, "X");
        font.draw(spriteBatch, "X",
                  EXIT_X + (EXIT_SIZE - glyphLayout.width)  / 2f,
                  EXIT_Y + (EXIT_SIZE + glyphLayout.height) / 2f);
    }

    private void renderBodyLeft(ItemType itemType, ItemStack slot, boolean isEquipped) {
        // Description text (small, off-white, word-wrapped)
        font.getData().setScale(0.85f);
        font.setColor(OFF_WHITE);
        font.draw(spriteBatch, itemType.getDescription(), BODY_L_X, DESC_Y, BODY_L_W, Align.left, true);
        font.getData().setScale(1f);

        // Stat block
        font.getData().setScale(0.80f);
        renderStatRows(itemType, slot);
        font.getData().setScale(1f);

        // Equipped notice (weapons only)
        if (isEquipped) {
            font.getData().setScale(0.85f);
            font.setColor(GREEN_EQUIPPED);
            font.draw(spriteBatch, "● CURRENTLY EQUIPPED", BODY_L_X, BODY_Y + 18f);
            font.getData().setScale(1f);
        }
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
        font.setColor(TEXT_DIM);
        font.draw(spriteBatch, label, BODY_L_X + 4f, rowY);
        font.setColor(AMBER);
        glyphLayout.setText(font, value);
        font.draw(spriteBatch, value, BODY_L_X + BODY_L_W - glyphLayout.width - 4f, rowY);
        return rowY - STAT_ROW_H;
    }

    private void renderBodyRight(ItemType itemType, ItemStack slot) {
        float headerY = DESC_Y;
        ItemCategory category = itemType.getCategory();

        font.getData().setScale(0.85f);
        font.setColor(AMBER);
        font.draw(spriteBatch, rightColumnTitle(category), BODY_R_X, headerY);
        font.getData().setScale(1f);

        float contentY = headerY - 26f;
        font.getData().setScale(0.80f);

        if (category == ItemCategory.WEAPON) {
            font.setColor(TEXT_DIM);
            font.draw(spriteBatch, "No special abilities.", BODY_R_X, contentY);

        } else if (category == ItemCategory.CONSUMABLE) {
            font.setColor(OFF_WHITE);
            font.draw(spriteBatch, consumableGuidance(itemType), BODY_R_X, contentY, BODY_R_W, Align.left, true);
            font.getData().setScale(0.85f);
            font.setColor(TEXT_DIM);
            font.draw(spriteBatch, "QUANTITY IN BAG", BODY_R_X, BODY_Y + 70f);
            font.getData().setScale(1.8f);
            font.setColor(AMBER);
            valueBuilder.setLength(0);
            valueBuilder.append(slot.getQuantity());
            glyphLayout.setText(font, valueBuilder);
            font.draw(spriteBatch, valueBuilder,
                      BODY_R_X + (BODY_R_W - glyphLayout.width) / 2f,
                      BODY_Y + 50f);

        } else if (category == ItemCategory.AMMO) {
            font.setColor(OFF_WHITE);
            font.draw(spriteBatch, ammoCompatible(itemType), BODY_R_X, contentY, BODY_R_W, Align.left, true);

        } else if (category == ItemCategory.KEY_ITEM) {
            font.setColor(OFF_WHITE);
            font.draw(spriteBatch, "Opens doors marked with this keycard color.",
                      BODY_R_X, contentY, BODY_R_W, Align.left, true);
            font.setColor(TEXT_DIM);
            font.draw(spriteBatch, "Persistent: keycards stay through level transitions.",
                      BODY_R_X, contentY - 32f, BODY_R_W, Align.left, true);

        } else {
            font.setColor(OFF_WHITE);
            font.draw(spriteBatch, "UAC standard currency. Accepted at facility terminals.",
                      BODY_R_X, contentY, BODY_R_W, Align.left, true);
        }

        font.getData().setScale(1f);
    }

    private void renderFooterButtons(ItemType itemType, boolean isEquipped) {
        boolean btn1On = isButton1Enabled();
        String  btn1Label = itemType != null ? button1Label(itemType.getCategory(), isEquipped) : "—";

        font.setColor(btn1On ? AMBER : TEXT_DISABLED);
        glyphLayout.setText(font, btn1Label);
        font.draw(spriteBatch, btn1Label,
                  BTN1_X + (BTN_W - glyphLayout.width)  / 2f,
                  BTN_Y  + (BTN_H + glyphLayout.height) / 2f);

        boolean btn2On = isButton2Enabled();
        font.setColor(btn2On ? AMBER : TEXT_DISABLED);
        glyphLayout.setText(font, "DROP");
        font.draw(spriteBatch, "DROP",
                  BTN2_X + (BTN_W - glyphLayout.width)  / 2f,
                  BTN_Y  + (BTN_H + glyphLayout.height) / 2f);
    }

    private void renderFlashMessage() {
        if (flashTimerSeconds <= 0f || flashMessage == null) return;
        font.setColor(RED_FLASH);
        glyphLayout.setText(font, flashMessage);
        font.draw(spriteBatch, flashMessage,
                  WINDOW_X + (WINDOW_W - glyphLayout.width) / 2f,
                  BTN_Y + BTN_H + 12f);
    }

    // =========================================================================
    // Private — button state
    // =========================================================================

    private boolean isButton1Enabled() {
        if (inventory == null || slotIndex < 0) return false;
        ItemStack slot = inventory.getSlot(slotIndex);
        if (slot.isEmpty()) return false;
        ItemCategory cat = slot.getType().getCategory();
        return cat == ItemCategory.WEAPON || cat == ItemCategory.CONSUMABLE;
    }

    private boolean isButton2Enabled() {
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
            case WEAPON_CHAINGUN:      return "AUTOMATIC CLASS";
            case WEAPON_PLASMA:
            case WEAPON_INCINERATOR:   return "ENERGY CLASS";
            case WEAPON_RAILGUN:       return "PRECISION CLASS";
            case WEAPON_ROCKET:        return "EXPLOSIVE CLASS";
            case WEAPON_FIST:
            case WEAPON_KNIFE:
            case WEAPON_HAMMER:
            case WEAPON_CHAINSAW:      return "MELEE CLASS";
            default:                   return "RANGED CLASS";
        }
    }

    private static String weaponDamage(ItemType itemType) {
        switch (itemType) {
            case WEAPON_SHOTGUN:       return String.valueOf(WeaponConstants.SHOTGUN_DAMAGE);
            case WEAPON_DOUBLE_BARREL: return WeaponConstants.DBL_SHOTGUN_DAMAGE + " x2";
            case WEAPON_PLASMA:        return String.valueOf(WeaponConstants.PLASMA_RIFLE_DAMAGE);
            case WEAPON_CHAINGUN:      return WeaponConstants.CHAINGUN_DAMAGE + "/bolt";
            case WEAPON_RAILGUN:       return "40-90";
            case WEAPON_INCINERATOR:   return WeaponConstants.FLAME_IMPACT_DAMAGE + "+" + WeaponConstants.FLAME_BURN_DAMAGE_PER_TURN + "/turn";
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
            case WEAPON_CHAINGUN:      return "Bullets";
            case WEAPON_PLASMA:
            case WEAPON_INCINERATOR:   return "Cells";
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
            case WEAPON_RAILGUN:       return "— / " + WeaponConstants.RAILGUN_CLIP_SIZE;
            case WEAPON_INCINERATOR:   return "— / " + WeaponConstants.FLAME_CLIP_SIZE;
            case WEAPON_ROCKET:        return "— / " + WeaponConstants.GRENADE_CLIP_SIZE;
            default:                   return "—";
        }
    }

    private static String weaponFireMode(ItemType itemType) {
        switch (itemType) {
            case WEAPON_CHAINGUN:  return "Burst";
            case WEAPON_INCINERATOR: return "Full-Auto";
            case WEAPON_FIST:
            case WEAPON_KNIFE:
            case WEAPON_HAMMER:
            case WEAPON_CHAINSAW:  return "Melee";
            default:               return "Semi";
        }
    }

    private static String consumableHeal(ItemType itemType) {
        switch (itemType) {
            case MEDKIT_SMALL: return "+" + ItemConstants.MEDKIT_STIM_HEAL + " HP";
            case MEDKIT_LARGE: return "+" + ItemConstants.MEDKIT_FULL_HEAL + " HP";
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
            case AMMO_BULLETS: return "Used by: Chaingun";
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

    private static Color categoryColor(ItemCategory category) {
        switch (category) {
            case WEAPON:     return CAT_WEAPON;
            case CONSUMABLE: return CAT_CONSUMABLE;
            case AMMO:       return CAT_AMMO;
            case KEY_ITEM:   return CAT_KEY_ITEM;
            case MOD:        return CAT_MOD;
            default:         return CAT_MISC;
        }
    }
}
