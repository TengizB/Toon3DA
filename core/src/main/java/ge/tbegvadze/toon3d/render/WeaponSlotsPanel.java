package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.item.Inventory;
import ge.tbegvadze.toon3d.item.ItemCategory;
import ge.tbegvadze.toon3d.item.ItemStack;
import ge.tbegvadze.toon3d.util.ItemConstants;

import java.util.function.IntConsumer;

/**
 * Renders the left panel interior: up to 4 weapon slot rectangles.
 * Weapon items are found by scanning Inventory for the WEAPON category.
 *
 * Package-private — owned and driven by InventoryOverlayRenderer.
 */
final class WeaponSlotsPanel implements Disposable {

    private static final int   WEAPON_SLOT_COUNT  = 4;
    private static final float WEAPON_SLOT_HEIGHT = 100f;
    private static final float WEAPON_SLOT_WIDTH  =
            ItemConstants.INV_LEFT_PANEL_WIDTH - 2f * ItemConstants.INV_PANEL_PADDING;
    private static final float WEAPON_SLOT_GAP    = 12f;
    private static final float SLOT_ORIGIN_X      =
            ItemConstants.INV_LEFT_PANEL_X + ItemConstants.INV_PANEL_PADDING;
    private static final float SLOT_ORIGIN_Y      =
            ItemConstants.INV_PANEL_Y_TOP - ItemConstants.INV_PANEL_PADDING - WEAPON_SLOT_HEIGHT;

    private static final Color SLOT_BG         = new Color(0.10f, 0.10f, 0.12f, 1f);
    private static final Color SLOT_BORDER     = new Color(0.22f, 0.22f, 0.30f, 1f);
    private static final Color BORDER_SELECTED = new Color(1.00f, 0.72f, 0.00f, 1f);
    private static final Color AMBER           = new Color(1.00f, 0.75f, 0.10f, 1f);
    private static final Color TEXT_DIM        = new Color(0.50f, 0.50f, 0.55f, 1f);
    private static final Color GREEN_EQUIPPED  = new Color(0.20f, 0.90f, 0.30f, 1f);

    private final Inventory     inventory;
    private final ShapeRenderer shapeRenderer;
    private final SpriteBatch   spriteBatch;
    private final BitmapFont    font;
    private final GlyphLayout   glyphLayout;
    private final IntConsumer   onSlotTapped;

    private int selectedDisplayIndex = -1;

    WeaponSlotsPanel(Inventory inventory, ShapeRenderer shapeRenderer, SpriteBatch spriteBatch,
                     BitmapFont font, GlyphLayout glyphLayout, IntConsumer onSlotTapped) {
        this.inventory     = inventory;
        this.shapeRenderer = shapeRenderer;
        this.spriteBatch   = spriteBatch;
        this.font          = font;
        this.glyphLayout   = glyphLayout;
        this.onSlotTapped  = onSlotTapped;
    }

    void render(OrthographicCamera camera, float animationClock) {
        renderSlotBackgrounds(camera);
        renderSlotBorders(camera);
        renderSlotLabels(camera);
    }

    boolean handleTouch(float worldX, float worldY) {
        for (int displayIndex = 0; displayIndex < WEAPON_SLOT_COUNT; displayIndex++) {
            float slotY = slotBottom(displayIndex);
            if (worldX >= SLOT_ORIGIN_X && worldX <= SLOT_ORIGIN_X + WEAPON_SLOT_WIDTH
                    && worldY >= slotY && worldY <= slotY + WEAPON_SLOT_HEIGHT) {
                selectedDisplayIndex = displayIndex;
                int inventorySlot = findInventorySlotByDisplayIndex(displayIndex);
                if (inventorySlot >= 0) {
                    onSlotTapped.accept(inventorySlot);
                }
                return true;
            }
        }
        return false;
    }

    void clearSelection() {
        selectedDisplayIndex = -1;
    }

    private void renderSlotBackgrounds(OrthographicCamera camera) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(SLOT_BG);
        for (int displayIndex = 0; displayIndex < WEAPON_SLOT_COUNT; displayIndex++) {
            shapeRenderer.rect(SLOT_ORIGIN_X, slotBottom(displayIndex),
                               WEAPON_SLOT_WIDTH, WEAPON_SLOT_HEIGHT);
        }
        shapeRenderer.end();
    }

    private void renderSlotBorders(OrthographicCamera camera) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        for (int displayIndex = 0; displayIndex < WEAPON_SLOT_COUNT; displayIndex++) {
            shapeRenderer.setColor(displayIndex == selectedDisplayIndex ? BORDER_SELECTED : SLOT_BORDER);
            shapeRenderer.rect(SLOT_ORIGIN_X, slotBottom(displayIndex),
                               WEAPON_SLOT_WIDTH, WEAPON_SLOT_HEIGHT);
        }
        shapeRenderer.end();
    }

    private void renderSlotLabels(OrthographicCamera camera) {
        spriteBatch.setProjectionMatrix(camera.combined);
        spriteBatch.begin();
        int displayIndex = 0;
        for (int slotIndex = 0;
             slotIndex < ItemConstants.INVENTORY_SLOT_COUNT && displayIndex < WEAPON_SLOT_COUNT;
             slotIndex++) {
            ItemStack slot = inventory.getSlot(slotIndex);
            if (!slot.isEmpty() && slot.getType().getCategory() == ItemCategory.WEAPON) {
                float slotY   = slotBottom(displayIndex);
                float centerY = slotY + WEAPON_SLOT_HEIGHT;
                boolean equipped = inventory.getEquippedWeaponSlot() == slotIndex;
                font.setColor(equipped ? GREEN_EQUIPPED : AMBER);
                font.draw(spriteBatch, slot.getType().getDisplayName(),
                          SLOT_ORIGIN_X + 12f, centerY - 20f);
                if (equipped) {
                    font.setColor(GREEN_EQUIPPED);
                    font.draw(spriteBatch, "[EQUIPPED]",
                              SLOT_ORIGIN_X + 12f, centerY - 42f);
                }
                displayIndex++;
            }
        }
        for (; displayIndex < WEAPON_SLOT_COUNT; displayIndex++) {
            font.setColor(TEXT_DIM);
            font.draw(spriteBatch, "— empty slot —",
                      SLOT_ORIGIN_X + 12f, slotBottom(displayIndex) + WEAPON_SLOT_HEIGHT - 20f);
        }
        spriteBatch.end();
    }

    private int findInventorySlotByDisplayIndex(int displayIndex) {
        int found = 0;
        for (int slotIndex = 0; slotIndex < ItemConstants.INVENTORY_SLOT_COUNT; slotIndex++) {
            ItemStack slot = inventory.getSlot(slotIndex);
            if (!slot.isEmpty() && slot.getType().getCategory() == ItemCategory.WEAPON) {
                if (found == displayIndex) return slotIndex;
                found++;
            }
        }
        return -1;
    }

    private static float slotBottom(int displayIndex) {
        return SLOT_ORIGIN_Y - displayIndex * (WEAPON_SLOT_HEIGHT + WEAPON_SLOT_GAP);
    }

    @Override
    public void dispose() {
        // Shared resources owned by InventoryOverlayRenderer coordinator
    }
}
