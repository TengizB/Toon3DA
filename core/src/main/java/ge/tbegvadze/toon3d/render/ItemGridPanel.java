package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
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
 * Renders the right panel interior: a 4×3 item slot grid.
 * Migrated from the old InventoryOverlayRenderer.drawSlotContents().
 *
 * Package-private — owned and driven by InventoryOverlayRenderer coordinator.
 */
final class ItemGridPanel implements Disposable {

    private static final float SLOT_STRIDE    = ItemConstants.INV_SLOT_SIZE + ItemConstants.INV_SLOT_GAP;
    private static final int   GRID_ROW_COUNT = (int) Math.ceil(
            (double) ItemConstants.INVENTORY_SLOT_COUNT / ItemConstants.INVENTORY_GRID_COLUMNS);
    private static final float GRID_PIXEL_WIDTH = ItemConstants.INVENTORY_GRID_COLUMNS * SLOT_STRIDE
                                                  - ItemConstants.INV_SLOT_GAP;
    private static final float GRID_ORIGIN_X  = ItemConstants.INV_RIGHT_PANEL_X
            + (ItemConstants.INV_RIGHT_PANEL_WIDTH - GRID_PIXEL_WIDTH) / 2f;
    private static final float GRID_ORIGIN_Y  = ItemConstants.INV_PANEL_Y_TOP - ItemConstants.INV_PANEL_PADDING;

    private static final Color SLOT_BG         = new Color(0.10f, 0.10f, 0.12f, 1f);
    private static final Color SLOT_BORDER     = new Color(0.22f, 0.22f, 0.30f, 1f);
    private static final Color SELECTED_BORDER = new Color(1.00f, 0.72f, 0.00f, 1f);
    private static final Color DIM             = new Color(0.50f, 0.50f, 0.55f, 1f);
    private static final Color GREEN_EQUIPPED  = new Color(0.20f, 0.90f, 0.30f, 1f);

    private final Inventory     inventory;
    private final ShapeRenderer shapeRenderer;
    private final SpriteBatch   spriteBatch;
    private final BitmapFont    font;
    private final GlyphLayout   glyphLayout;
    private final IntConsumer   onSlotTapped;
    private final StringBuilder textBuilder;
    private final Color         temporaryColor;

    private int   selectedColumn = 0;
    private int   selectedRow    = 0;
    private float animationClock = 0f;

    ItemGridPanel(Inventory inventory, ShapeRenderer shapeRenderer, SpriteBatch spriteBatch,
                  BitmapFont font, GlyphLayout glyphLayout, IntConsumer onSlotTapped) {
        this.inventory      = inventory;
        this.shapeRenderer  = shapeRenderer;
        this.spriteBatch    = spriteBatch;
        this.font           = font;
        this.glyphLayout    = glyphLayout;
        this.onSlotTapped   = onSlotTapped;
        this.textBuilder    = new StringBuilder(32);
        this.temporaryColor = new Color();
    }

    void render(OrthographicCamera camera, float animationClock) {
        this.animationClock = animationClock;
        renderSlotBackgrounds(camera);
        renderSlotBorders(camera);
        renderSlotContents(camera);
    }

    boolean handleTouch(float worldX, float worldY) {
        for (int slotIndex = 0; slotIndex < ItemConstants.INVENTORY_SLOT_COUNT; slotIndex++) {
            int   slotRow    = slotIndex / ItemConstants.INVENTORY_GRID_COLUMNS;
            int   slotColumn = slotIndex % ItemConstants.INVENTORY_GRID_COLUMNS;
            float slotX      = slotLeft(slotColumn);
            float slotY      = slotBottom(slotRow);
            if (worldX >= slotX && worldX <= slotX + ItemConstants.INV_SLOT_SIZE
                    && worldY >= slotY && worldY <= slotY + ItemConstants.INV_SLOT_SIZE) {
                selectedColumn = slotColumn;
                selectedRow    = slotRow;
                if (!inventory.getSlot(slotIndex).isEmpty()) {
                    onSlotTapped.accept(slotIndex);
                }
                return true;
            }
        }
        return false;
    }

    void clearSelection() {
        selectedColumn = 0;
        selectedRow    = 0;
    }

    private void renderSlotBackgrounds(OrthographicCamera camera) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(SLOT_BG);
        for (int slotIndex = 0; slotIndex < ItemConstants.INVENTORY_SLOT_COUNT; slotIndex++) {
            int slotRow    = slotIndex / ItemConstants.INVENTORY_GRID_COLUMNS;
            int slotColumn = slotIndex % ItemConstants.INVENTORY_GRID_COLUMNS;
            shapeRenderer.rect(slotLeft(slotColumn), slotBottom(slotRow),
                               ItemConstants.INV_SLOT_SIZE, ItemConstants.INV_SLOT_SIZE);
        }
        shapeRenderer.end();
    }

    private void renderSlotBorders(OrthographicCamera camera) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        for (int slotIndex = 0; slotIndex < ItemConstants.INVENTORY_SLOT_COUNT; slotIndex++) {
            int   slotRow    = slotIndex / ItemConstants.INVENTORY_GRID_COLUMNS;
            int   slotColumn = slotIndex % ItemConstants.INVENTORY_GRID_COLUMNS;
            float slotX      = slotLeft(slotColumn);
            float slotY      = slotBottom(slotRow);
            boolean selected = (slotColumn == selectedColumn && slotRow == selectedRow);
            if (selected) {
                float pulse = 0.7f + 0.3f * MathUtils.sin(animationClock * 4f);
                temporaryColor.set(SELECTED_BORDER.r * pulse,
                                   SELECTED_BORDER.g * pulse,
                                   SELECTED_BORDER.b * pulse, 1f);
                shapeRenderer.setColor(temporaryColor);
                int borderThickness = (int) ItemConstants.INV_SELECT_BORDER_THICKNESS;
                for (int step = 0; step < borderThickness; step++) {
                    shapeRenderer.rect(slotX + step, slotY + step,
                                       ItemConstants.INV_SLOT_SIZE - step * 2f,
                                       ItemConstants.INV_SLOT_SIZE - step * 2f);
                }
            } else {
                shapeRenderer.setColor(SLOT_BORDER);
                shapeRenderer.rect(slotX, slotY,
                                   ItemConstants.INV_SLOT_SIZE, ItemConstants.INV_SLOT_SIZE);
            }
        }
        shapeRenderer.end();
    }

    private void renderSlotContents(OrthographicCamera camera) {
        spriteBatch.setProjectionMatrix(camera.combined);
        spriteBatch.begin();
        for (int slotIndex = 0; slotIndex < ItemConstants.INVENTORY_SLOT_COUNT; slotIndex++) {
            ItemStack slot = inventory.getSlot(slotIndex);
            if (slot.isEmpty()) continue;

            int   slotRow    = slotIndex / ItemConstants.INVENTORY_GRID_COLUMNS;
            int   slotColumn = slotIndex % ItemConstants.INVENTORY_GRID_COLUMNS;
            float slotX      = slotLeft(slotColumn);
            float slotY      = slotBottom(slotRow);

            font.setColor(slot.getType().getGlyphRed(),
                          slot.getType().getGlyphGreen(),
                          slot.getType().getGlyphBlue(), 1f);
            textBuilder.setLength(0);
            textBuilder.append(slot.getType().getGlyph());
            glyphLayout.setText(font, textBuilder);
            font.draw(spriteBatch, textBuilder,
                      slotX + (ItemConstants.INV_SLOT_SIZE - glyphLayout.width) / 2f,
                      slotY + (ItemConstants.INV_SLOT_SIZE + glyphLayout.height) / 2f);

            if (slot.getQuantity() > 1) {
                font.setColor(DIM);
                textBuilder.setLength(0);
                textBuilder.append('x').append(slot.getQuantity());
                glyphLayout.setText(font, textBuilder);
                font.draw(spriteBatch, textBuilder,
                          slotX + ItemConstants.INV_SLOT_SIZE - glyphLayout.width - 4f,
                          slotY + glyphLayout.height + 4f);
            }

            if (slot.getType().getCategory() == ItemCategory.WEAPON
                    && inventory.getEquippedWeaponSlot() == slotIndex) {
                font.setColor(GREEN_EQUIPPED);
                font.draw(spriteBatch, "*",
                          slotX + ItemConstants.INV_SLOT_SIZE - 12f,
                          slotY + ItemConstants.INV_SLOT_SIZE - 4f);
            }
        }
        spriteBatch.end();
    }

    private static float slotLeft(int slotColumn) {
        return GRID_ORIGIN_X + slotColumn * SLOT_STRIDE;
    }

    private static float slotBottom(int slotRow) {
        return GRID_ORIGIN_Y - slotRow * SLOT_STRIDE - ItemConstants.INV_SLOT_SIZE;
    }

    @Override
    public void dispose() {
        // Shared resources owned by InventoryOverlayRenderer coordinator
    }
}
