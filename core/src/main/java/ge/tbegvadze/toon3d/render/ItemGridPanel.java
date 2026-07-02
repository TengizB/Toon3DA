package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.item.Inventory;
import ge.tbegvadze.toon3d.item.ItemCategory;
import ge.tbegvadze.toon3d.item.ItemStack;
import ge.tbegvadze.toon3d.util.ItemConstants;

import java.util.function.IntConsumer;

/**
 * Renders the right panel interior: a 4×3 item slot grid with a category
 * filter tab bar above it.
 *
 * Implemented per inventory-menu-order-3 spec:
 *   - 12 slots (was 10) in a 4-column × 3-row layout, 148×148px each.
 *   - 5 filter tabs: ALL / GUNS / AMMO / MEDS / MISC.
 *   - Slot visual states: EMPTY, FILLED, EQUIPPED (warm tint + amber border),
 *     SELECTED (pulsing amber border), DIMMED (40% alpha when filter active).
 *   - Category dot (4×4px) in top-left corner of each filled slot.
 *   - Slot usage counter ("N / 12") right-aligned inside the tab bar.
 *   - Touch: tab bar changes active filter; slots open ItemWindow on tap.
 *
 * Package-private — owned and driven by InventoryOverlayRenderer coordinator.
 */
final class ItemGridPanel implements Disposable {

    // -------------------------------------------------------------------------
    // Tab indices
    // -------------------------------------------------------------------------
    private static final int TAB_ALL     = 0;
    private static final int TAB_WEAPONS = 1;
    private static final int TAB_AMMO    = 2;
    private static final int TAB_MEDS    = 3;
    private static final int TAB_MISC    = 4;

    private static final String[] TAB_LABELS = { "ALL", "GUNS", "AMMO", "MEDS", "MISC" };

    // -------------------------------------------------------------------------
    // Derived geometry — computed once from constants
    // -------------------------------------------------------------------------
    private static final float SLOT_STRIDE_COL = ItemConstants.INV_GRID_SLOT_SIZE
                                                 + ItemConstants.INV_GRID_SLOT_GAP_COL;
    private static final float SLOT_STRIDE_ROW = ItemConstants.INV_GRID_SLOT_SIZE
                                                 + ItemConstants.INV_GRID_SLOT_GAP_ROW;

    // -------------------------------------------------------------------------
    // Palette
    // -------------------------------------------------------------------------
    private static final Color SLOT_BG_NORMAL    = new Color(0.10f, 0.10f, 0.12f, 1f);
    private static final Color SLOT_BG_EQUIPPED  = new Color(0.12f, 0.10f, 0.05f, 1f);
    private static final Color SLOT_BG_SELECTED  = new Color(0.14f, 0.14f, 0.18f, 1f);
    private static final Color SLOT_BORDER_DIM   = new Color(0.22f, 0.22f, 0.30f, 1f);
    private static final Color SLOT_BORDER_FILL  = new Color(0.28f, 0.28f, 0.35f, 1f);
    private static final Color SLOT_BORDER_EQUIP = new Color(0.50f, 0.36f, 0.00f, 1f);
    private static final Color AMBER             = new Color(1.00f, 0.72f, 0.00f, 1f);
    private static final Color AMBER_TEXT        = new Color(1.00f, 0.75f, 0.10f, 1f);
    private static final Color TEXT_SECONDARY    = new Color(0.50f, 0.50f, 0.55f, 1f);
    private static final Color GREEN_EQUIPPED    = new Color(0.20f, 0.90f, 0.30f, 1f);
    private static final Color TAB_ACTIVE_BG     = new Color(1.00f, 0.72f, 0.00f,
                                                              ItemConstants.INV_TAB_ACTIVE_BG_ALPHA);

    // -------------------------------------------------------------------------
    // Dependencies (shared, not owned)
    // -------------------------------------------------------------------------
    private final Inventory     inventory;
    private final ShapeRenderer     shapeRenderer;
    private final SpriteBatch       spriteBatch;
    private final BitmapFont        font;
    private final GlyphLayout       glyphLayout;
    private final IntConsumer       onSlotTapped;
    private final ItemIconTextures  iconTextures;

    // -------------------------------------------------------------------------
    // Pre-allocated scratch objects
    // -------------------------------------------------------------------------
    private final StringBuilder textBuilder;
    private final Color         temporaryColor;

    // -------------------------------------------------------------------------
    // Runtime state
    // -------------------------------------------------------------------------
    private int   activeFilterTab  = TAB_ALL;
    private int   selectedColumn   = -1;
    private int   selectedRow      = -1;
    private float animationClock   = 0f;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    ItemGridPanel(Inventory inventory, ShapeRenderer shapeRenderer, SpriteBatch spriteBatch,
                  BitmapFont font, GlyphLayout glyphLayout, IntConsumer onSlotTapped,
                  ItemIconTextures iconTextures) {
        this.inventory     = inventory;
        this.shapeRenderer = shapeRenderer;
        this.spriteBatch   = spriteBatch;
        this.font          = font;
        this.glyphLayout   = glyphLayout;
        this.onSlotTapped  = onSlotTapped;
        this.iconTextures  = iconTextures;
        this.textBuilder   = new StringBuilder(32);
        this.temporaryColor = new Color();
    }

    // -------------------------------------------------------------------------
    // Render entry point
    // -------------------------------------------------------------------------

    void render(OrthographicCamera camera, float animationClock) {
        this.animationClock = animationClock;
        renderPass_Filled(camera);
        renderPass_Lines(camera);
        renderPass_Text(camera);
    }

    // -------------------------------------------------------------------------
    // Touch input
    // -------------------------------------------------------------------------

    boolean handleTouch(float worldX, float worldY) {
        // Check tab bar first (higher priority)
        if (worldY >= ItemConstants.INV_GRID_TAB_BAR_Y
                && worldY <= ItemConstants.INV_GRID_TAB_BAR_Y + ItemConstants.INV_GRID_TAB_BAR_HEIGHT
                && worldX >= ItemConstants.INV_RIGHT_PANEL_X) {
            float relativeX = worldX - ItemConstants.INV_RIGHT_PANEL_X;
            int tabIndex = (int) (relativeX / ItemConstants.INV_TAB_WIDTH);
            if (tabIndex >= 0 && tabIndex < ItemConstants.INV_TAB_COUNT) {
                activeFilterTab = tabIndex;
                return true;
            }
        }

        // Check slot grid
        for (int gridRow = 0; gridRow < ItemConstants.INV_GRID_ROWS; gridRow++) {
            for (int gridColumn = 0; gridColumn < ItemConstants.INV_GRID_COLUMNS; gridColumn++) {
                float slotX = slotLeft(gridColumn);
                float slotY = slotBottom(gridRow);
                if (worldX >= slotX && worldX <= slotX + ItemConstants.INV_GRID_SLOT_SIZE
                        && worldY >= slotY && worldY <= slotY + ItemConstants.INV_GRID_SLOT_SIZE) {
                    int inventoryIndex = gridRowColumnToIndex(gridRow, gridColumn);
                    selectedColumn = gridColumn;
                    selectedRow    = gridRow;
                    if (!inventory.getSlot(inventoryIndex).isEmpty()) {
                        onSlotTapped.accept(inventoryIndex);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    void clearSelection() {
        selectedColumn = -1;
        selectedRow    = -1;
    }

    // -------------------------------------------------------------------------
    // Pass A — ShapeRenderer.Filled: backgrounds + tab highlights + category dots
    // -------------------------------------------------------------------------

    private void renderPass_Filled(OrthographicCamera camera) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Tab bar active highlight
        if (activeFilterTab >= 0 && activeFilterTab < ItemConstants.INV_TAB_COUNT) {
            shapeRenderer.setColor(TAB_ACTIVE_BG);
            float tabX = ItemConstants.INV_RIGHT_PANEL_X + activeFilterTab * ItemConstants.INV_TAB_WIDTH;
            shapeRenderer.rect(tabX, ItemConstants.INV_GRID_TAB_BAR_Y,
                               ItemConstants.INV_TAB_WIDTH, ItemConstants.INV_GRID_TAB_BAR_HEIGHT);
        }

        // Slot backgrounds
        for (int gridRow = 0; gridRow < ItemConstants.INV_GRID_ROWS; gridRow++) {
            for (int gridColumn = 0; gridColumn < ItemConstants.INV_GRID_COLUMNS; gridColumn++) {
                int inventoryIndex = gridRowColumnToIndex(gridRow, gridColumn);
                ItemStack slot     = inventory.getSlot(inventoryIndex);
                float slotX        = slotLeft(gridColumn);
                float slotY        = slotBottom(gridRow);
                float alphaScale   = slotAlphaScale(slot);

                Color background   = slotBackgroundColor(slot, inventoryIndex, gridColumn, gridRow);
                temporaryColor.set(background.r, background.g, background.b,
                                   background.a * alphaScale);
                shapeRenderer.setColor(temporaryColor);
                shapeRenderer.rect(slotX, slotY,
                                   ItemConstants.INV_GRID_SLOT_SIZE,
                                   ItemConstants.INV_GRID_SLOT_SIZE);

                // Category dot — top-left corner of filled slots
                // Y-up: bottom of dot = slotY + SLOT_SIZE - CAT_DOT_MARGIN_TOP → visually 15px from top
                if (!slot.isEmpty()) {
                    setCategoryDotColor(slot.getType().getCategory(), alphaScale);
                    float dotX = slotX + ItemConstants.INV_GRID_CAT_DOT_MARGIN_X;
                    float dotY = slotY + ItemConstants.INV_GRID_SLOT_SIZE
                                 - ItemConstants.INV_GRID_CAT_DOT_MARGIN_TOP;
                    shapeRenderer.rect(dotX, dotY,
                                       ItemConstants.INV_GRID_CAT_DOT_SIZE,
                                       ItemConstants.INV_GRID_CAT_DOT_SIZE);
                }
            }
        }

        shapeRenderer.end();
    }

    // -------------------------------------------------------------------------
    // Pass B — ShapeRenderer.Line: borders + tab underline + tab dividers
    // -------------------------------------------------------------------------

    private void renderPass_Lines(OrthographicCamera camera) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        // Active tab amber underline
        if (activeFilterTab >= 0 && activeFilterTab < ItemConstants.INV_TAB_COUNT) {
            shapeRenderer.setColor(AMBER);
            float tabX = ItemConstants.INV_RIGHT_PANEL_X + activeFilterTab * ItemConstants.INV_TAB_WIDTH;
            shapeRenderer.line(tabX, ItemConstants.INV_GRID_TAB_BAR_Y,
                               tabX + ItemConstants.INV_TAB_WIDTH, ItemConstants.INV_GRID_TAB_BAR_Y);
        }

        // Vertical tab dividers (very dim)
        temporaryColor.set(0.25f, 0.25f, 0.30f, 0.5f);
        shapeRenderer.setColor(temporaryColor);
        for (int tabIndex = 1; tabIndex < ItemConstants.INV_TAB_COUNT; tabIndex++) {
            float dividerX = ItemConstants.INV_RIGHT_PANEL_X + tabIndex * ItemConstants.INV_TAB_WIDTH;
            shapeRenderer.line(dividerX, ItemConstants.INV_GRID_TAB_BAR_Y,
                               dividerX, ItemConstants.INV_GRID_TAB_BAR_Y + ItemConstants.INV_GRID_TAB_BAR_HEIGHT);
        }

        // Slot borders
        for (int gridRow = 0; gridRow < ItemConstants.INV_GRID_ROWS; gridRow++) {
            for (int gridColumn = 0; gridColumn < ItemConstants.INV_GRID_COLUMNS; gridColumn++) {
                int inventoryIndex = gridRowColumnToIndex(gridRow, gridColumn);
                ItemStack slot     = inventory.getSlot(inventoryIndex);
                float slotX        = slotLeft(gridColumn);
                float slotY        = slotBottom(gridRow);
                float alphaScale   = slotAlphaScale(slot);
                boolean isSelected = (gridColumn == selectedColumn && gridRow == selectedRow);

                if (isSelected) {
                    float pulse = ItemConstants.INV_GRID_ACTIVE_PULSE_MIN
                            + (1f - ItemConstants.INV_GRID_ACTIVE_PULSE_MIN)
                            * (MathUtils.sin(animationClock * ItemConstants.INV_GRID_ACTIVE_PULSE_HZ
                                             * MathUtils.PI2) * 0.5f + 0.5f);
                    temporaryColor.set(AMBER.r, AMBER.g, AMBER.b, pulse * alphaScale);
                    shapeRenderer.setColor(temporaryColor);
                    // Draw two-pixel border by drawing inner and outer rects
                    shapeRenderer.rect(slotX, slotY,
                                       ItemConstants.INV_GRID_SLOT_SIZE,
                                       ItemConstants.INV_GRID_SLOT_SIZE);
                    shapeRenderer.rect(slotX + 1f, slotY + 1f,
                                       ItemConstants.INV_GRID_SLOT_SIZE - 2f,
                                       ItemConstants.INV_GRID_SLOT_SIZE - 2f);
                } else if (!slot.isEmpty()
                        && inventory.getEquippedWeaponSlot() == inventoryIndex) {
                    temporaryColor.set(SLOT_BORDER_EQUIP.r, SLOT_BORDER_EQUIP.g,
                                       SLOT_BORDER_EQUIP.b, SLOT_BORDER_EQUIP.a * alphaScale);
                    shapeRenderer.setColor(temporaryColor);
                    shapeRenderer.rect(slotX, slotY,
                                       ItemConstants.INV_GRID_SLOT_SIZE,
                                       ItemConstants.INV_GRID_SLOT_SIZE);
                } else if (!slot.isEmpty()) {
                    temporaryColor.set(SLOT_BORDER_FILL.r, SLOT_BORDER_FILL.g,
                                       SLOT_BORDER_FILL.b, SLOT_BORDER_FILL.a * alphaScale);
                    shapeRenderer.setColor(temporaryColor);
                    shapeRenderer.rect(slotX, slotY,
                                       ItemConstants.INV_GRID_SLOT_SIZE,
                                       ItemConstants.INV_GRID_SLOT_SIZE);
                } else {
                    temporaryColor.set(SLOT_BORDER_DIM.r, SLOT_BORDER_DIM.g,
                                       SLOT_BORDER_DIM.b, SLOT_BORDER_DIM.a * alphaScale);
                    shapeRenderer.setColor(temporaryColor);
                    shapeRenderer.rect(slotX, slotY,
                                       ItemConstants.INV_GRID_SLOT_SIZE,
                                       ItemConstants.INV_GRID_SLOT_SIZE);
                }
            }
        }

        shapeRenderer.end();
    }

    // -------------------------------------------------------------------------
    // Pass C — SpriteBatch + BitmapFont: tab labels, usage counter, glyphs, qty, star
    // -------------------------------------------------------------------------

    private void renderPass_Text(OrthographicCamera camera) {
        spriteBatch.setProjectionMatrix(camera.combined);
        spriteBatch.begin();

        // Tab labels
        for (int tabIndex = 0; tabIndex < ItemConstants.INV_TAB_COUNT; tabIndex++) {
            float tabX   = ItemConstants.INV_RIGHT_PANEL_X + tabIndex * ItemConstants.INV_TAB_WIDTH;
            float tabCenterX = tabX + ItemConstants.INV_TAB_WIDTH / 2f;
            float tabCenterY = ItemConstants.INV_GRID_TAB_BAR_Y
                               + ItemConstants.INV_GRID_TAB_BAR_HEIGHT / 2f;
            if (tabIndex == activeFilterTab) {
                font.setColor(AMBER_TEXT);
            } else {
                font.setColor(TEXT_SECONDARY);
            }
            glyphLayout.setText(font, TAB_LABELS[tabIndex]);
            font.draw(spriteBatch, TAB_LABELS[tabIndex],
                      tabCenterX - glyphLayout.width / 2f,
                      tabCenterY + glyphLayout.height / 2f);
        }

        // Slot usage counter — right-aligned inside the tab bar
        int usedCount = countUsedSlots();
        textBuilder.setLength(0);
        textBuilder.append(usedCount).append(" / ").append(ItemConstants.INVENTORY_SLOT_COUNT);
        glyphLayout.setText(font, textBuilder);
        font.setColor(AMBER_TEXT);
        float usageCounterRightEdge = ItemConstants.INV_RIGHT_PANEL_X + ItemConstants.INV_RIGHT_PANEL_WIDTH
                                      - ItemConstants.INV_PANEL_PADDING;
        font.draw(spriteBatch, textBuilder,
                  usageCounterRightEdge - glyphLayout.width,
                  ItemConstants.INV_GRID_TAB_BAR_Y + ItemConstants.INV_GRID_TAB_BAR_HEIGHT / 2f
                  + glyphLayout.height / 2f);

        // Slot contents (glyph, quantity, equipped star)
        for (int gridRow = 0; gridRow < ItemConstants.INV_GRID_ROWS; gridRow++) {
            for (int gridColumn = 0; gridColumn < ItemConstants.INV_GRID_COLUMNS; gridColumn++) {
                int inventoryIndex = gridRowColumnToIndex(gridRow, gridColumn);
                ItemStack slot     = inventory.getSlot(inventoryIndex);
                if (slot.isEmpty()) continue;

                float slotX      = slotLeft(gridColumn);
                float slotY      = slotBottom(gridRow);
                float alphaScale = slotAlphaScale(slot);

                // Large centered icon — reuses the ground-pickup sprite when one exists
                // (medkits, ammo); falls back to the ASCII glyph for everything else.
                Texture iconTexture = iconTextures.get(slot.getType());
                if (iconTexture != null) {
                    drawSlotIcon(iconTexture, slotX, slotY, alphaScale);
                } else {
                    font.setColor(slot.getType().getGlyphRed(),
                                  slot.getType().getGlyphGreen(),
                                  slot.getType().getGlyphBlue(),
                                  alphaScale);
                    textBuilder.setLength(0);
                    textBuilder.append(slot.getType().getGlyph());
                    glyphLayout.setText(font, textBuilder);
                    font.draw(spriteBatch, textBuilder,
                              slotX + (ItemConstants.INV_GRID_SLOT_SIZE - glyphLayout.width) / 2f,
                              slotY + (ItemConstants.INV_GRID_SLOT_SIZE + glyphLayout.height) / 2f);
                }

                // Quantity label — bottom-right corner
                if (slot.getQuantity() > 1) {
                    font.setColor(TEXT_SECONDARY.r, TEXT_SECONDARY.g, TEXT_SECONDARY.b, alphaScale);
                    textBuilder.setLength(0);
                    textBuilder.append('×').append(slot.getQuantity());
                    glyphLayout.setText(font, textBuilder);
                    font.draw(spriteBatch, textBuilder,
                              slotX + ItemConstants.INV_GRID_SLOT_SIZE
                                      - glyphLayout.width - ItemConstants.INV_GRID_QTY_LABEL_MARGIN,
                              slotY + glyphLayout.height + ItemConstants.INV_GRID_QTY_LABEL_MARGIN);
                }

                // Equipped star — top-right corner (green)
                if (slot.getType().getCategory() == ItemCategory.WEAPON
                        && inventory.getEquippedWeaponSlot() == inventoryIndex) {
                    font.setColor(GREEN_EQUIPPED.r, GREEN_EQUIPPED.g, GREEN_EQUIPPED.b, alphaScale);
                    font.draw(spriteBatch, "*",
                              slotX + ItemConstants.INV_GRID_SLOT_SIZE
                                      - ItemConstants.INV_GRID_STAR_MARGIN_RIGHT,
                              slotY + ItemConstants.INV_GRID_SLOT_SIZE
                                      - ItemConstants.INV_GRID_STAR_MARGIN_TOP);
                }
            }
        }

        spriteBatch.end();
    }

    // Draws a ground-pickup icon centered and aspect-fit inside a slot, leaving
    // a fixed margin so it never touches the slot border.
    private void drawSlotIcon(Texture iconTexture, float slotX, float slotY, float alphaScale) {
        float srcWidth  = iconTexture.getWidth();
        float srcHeight = iconTexture.getHeight();
        if (srcWidth <= 0f || srcHeight <= 0f) return;

        float availableSize = ItemConstants.INV_GRID_SLOT_SIZE - 2f * ItemConstants.INV_GRID_ICON_MARGIN;
        float scale          = Math.min(availableSize / srcWidth, availableSize / srcHeight);
        float drawWidth      = srcWidth  * scale;
        float drawHeight     = srcHeight * scale;
        float drawX          = slotX + (ItemConstants.INV_GRID_SLOT_SIZE - drawWidth)  / 2f;
        float drawY          = slotY + (ItemConstants.INV_GRID_SLOT_SIZE - drawHeight) / 2f;

        spriteBatch.setColor(1f, 1f, 1f, alphaScale);
        spriteBatch.draw(iconTexture, drawX, drawY, drawWidth, drawHeight);
        spriteBatch.setColor(Color.WHITE);
    }

    // -------------------------------------------------------------------------
    // Slot coordinate helpers
    // -------------------------------------------------------------------------

    /*
     * Formula: slotLeft
     * Derivation: origin_x + column × (slot_size + gap_col)
     * Edge cases: column must be in [0, INV_GRID_COLUMNS)
     */
    private static float slotLeft(int gridColumn) {
        return ItemConstants.INV_GRID_ORIGIN_X + gridColumn * SLOT_STRIDE_COL;
    }

    /*
     * Formula: slotBottom
     * Derivation: origin_y + row × (slot_size + gap_row)
     *   row 0 = visual bottom row = inventory slots 8–11
     *   row 2 = visual top row    = inventory slots 0–3
     * Edge cases: gridRow must be in [0, INV_GRID_ROWS)
     */
    private static float slotBottom(int gridRow) {
        return ItemConstants.INV_GRID_ORIGIN_Y + gridRow * SLOT_STRIDE_ROW;
    }

    /*
     * Formula: gridRowColumnToIndex
     * Derivation: index = (ROWS - 1 - gridRow) × COLUMNS + gridColumn
     *   Visual top row (gridRow == ROWS-1) → inventory index 0..3
     *   Visual bottom row (gridRow == 0)   → inventory index 8..11
     * Edge cases: gridRow and gridColumn must be in valid ranges
     */
    private static int gridRowColumnToIndex(int gridRow, int gridColumn) {
        return (ItemConstants.INV_GRID_ROWS - 1 - gridRow) * ItemConstants.INV_GRID_COLUMNS + gridColumn;
    }

    // -------------------------------------------------------------------------
    // Slot visual helpers
    // -------------------------------------------------------------------------

    private Color slotBackgroundColor(ItemStack slot, int inventoryIndex,
                                       int gridColumn, int gridRow) {
        boolean isSelected = (gridColumn == selectedColumn && gridRow == selectedRow);
        if (isSelected) return SLOT_BG_SELECTED;
        if (!slot.isEmpty() && inventory.getEquippedWeaponSlot() == inventoryIndex) {
            return SLOT_BG_EQUIPPED;
        }
        return SLOT_BG_NORMAL;
    }

    private float slotAlphaScale(ItemStack slot) {
        if (activeFilterTab == TAB_ALL || slot.isEmpty()) return 1f;
        if (slotMatchesFilter(slot)) return 1f;
        return ItemConstants.INV_TAB_DIM_ALPHA;
    }

    private boolean slotMatchesFilter(ItemStack slot) {
        ItemCategory category = slot.getType().getCategory();
        switch (activeFilterTab) {
            case TAB_WEAPONS: return category == ItemCategory.WEAPON;
            case TAB_AMMO:    return category == ItemCategory.AMMO;
            case TAB_MEDS:    return category == ItemCategory.CONSUMABLE;
            case TAB_MISC:    return category == ItemCategory.KEY_ITEM
                                     || category == ItemCategory.MISC
                                     || category == ItemCategory.MOD;
            default:          return true;
        }
    }

    private void setCategoryDotColor(ItemCategory category, float alphaScale) {
        switch (category) {
            case WEAPON:
                temporaryColor.set(ItemConstants.INV_CAT_DOT_WEAPON_R,
                                   ItemConstants.INV_CAT_DOT_WEAPON_G,
                                   ItemConstants.INV_CAT_DOT_WEAPON_B, alphaScale);
                break;
            case CONSUMABLE:
                temporaryColor.set(ItemConstants.INV_CAT_DOT_CONSUMABLE_R,
                                   ItemConstants.INV_CAT_DOT_CONSUMABLE_G,
                                   ItemConstants.INV_CAT_DOT_CONSUMABLE_B, alphaScale);
                break;
            case AMMO:
                temporaryColor.set(ItemConstants.INV_CAT_DOT_AMMO_R,
                                   ItemConstants.INV_CAT_DOT_AMMO_G,
                                   ItemConstants.INV_CAT_DOT_AMMO_B, alphaScale);
                break;
            case KEY_ITEM:
                temporaryColor.set(ItemConstants.INV_CAT_DOT_KEY_ITEM_R,
                                   ItemConstants.INV_CAT_DOT_KEY_ITEM_G,
                                   ItemConstants.INV_CAT_DOT_KEY_ITEM_B, alphaScale);
                break;
            case MOD:
                temporaryColor.set(ItemConstants.INV_CAT_DOT_MOD_R,
                                   ItemConstants.INV_CAT_DOT_MOD_G,
                                   ItemConstants.INV_CAT_DOT_MOD_B, alphaScale);
                break;
            default:
                temporaryColor.set(ItemConstants.INV_CAT_DOT_MISC_R,
                                   ItemConstants.INV_CAT_DOT_MISC_G,
                                   ItemConstants.INV_CAT_DOT_MISC_B, alphaScale);
                break;
        }
        shapeRenderer.setColor(temporaryColor);
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private int countUsedSlots() {
        int count = 0;
        for (int slotIndex = 0; slotIndex < ItemConstants.INVENTORY_SLOT_COUNT; slotIndex++) {
            if (!inventory.getSlot(slotIndex).isEmpty()) count++;
        }
        return count;
    }

    // -------------------------------------------------------------------------
    // Dispose
    // -------------------------------------------------------------------------

    @Override
    public void dispose() {
        // Shared resources are owned by InventoryOverlayRenderer coordinator
    }
}
