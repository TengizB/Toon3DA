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
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.util.ItemConstants;

/**
 * Popup window shown when a weapon slot or item slot is tapped.
 * Displays item detail and offers USE/EQUIP, DROP, and EXIT actions.
 *
 * All window and button geometry is defined in ItemConstants (INV_ITEM_WINDOW_*
 * and INV_ITEM_WIN_BTN_* constants). Part 4 of the inventory redesign will
 * refine those dimensions without touching this class.
 *
 * Package-private — owned and driven by InventoryOverlayRenderer coordinator.
 */
final class ItemWindow implements Disposable {

    // Derived constant — header accent line Y, computed once from ItemConstants.
    private static final float HEADER_LINE_Y = ItemConstants.INV_ITEM_WINDOW_Y
            + ItemConstants.INV_ITEM_WINDOW_HEIGHT - ItemConstants.INV_ITEM_WINDOW_HEADER_LINE_OFFSET;

    private static final Color WINDOW_BG      = new Color(0.071f, 0.071f, 0.086f, 0.97f);
    private static final Color WINDOW_BORDER  = new Color(0.22f,  0.22f,  0.30f,  1f);
    private static final Color HEADER_ACCENT  = new Color(1.00f,  0.72f,  0.00f,  1f);
    private static final Color AMBER          = new Color(1.00f,  0.75f,  0.10f,  1f);
    private static final Color TEXT_DIM       = new Color(0.50f,  0.50f,  0.55f,  1f);
    private static final Color WHITE          = new Color(1f,     1f,     1f,     1f);
    private static final Color GREEN_EQUIPPED = new Color(0.20f,  0.90f,  0.30f,  1f);
    private static final Color BTN_BG         = new Color(0.14f,  0.14f,  0.17f,  1f);
    private static final Color BTN_BORDER     = new Color(0.35f,  0.35f,  0.40f,  1f);
    private static final Color EXIT_BTN_BG    = new Color(0.70f,  0.20f,  0.15f,  1f);
    private static final Color EXIT_BTN_TEXT  = new Color(1.00f,  0.85f,  0.85f,  1f);
    private static final Color RED_FLASH      = new Color(0.95f,  0.15f,  0.15f,  1f);

    private final ShapeRenderer shapeRenderer;
    private final SpriteBatch   spriteBatch;
    private final BitmapFont    font;
    private final GlyphLayout   glyphLayout;
    private final StringBuilder textBuilder;

    private Inventory inventory;
    private int       slotIndex         = -1;
    private String    flashMessage      = null;
    private float     flashTimerSeconds = 0f;

    ItemWindow(ShapeRenderer shapeRenderer, SpriteBatch spriteBatch,
               BitmapFont font, GlyphLayout glyphLayout) {
        this.shapeRenderer = shapeRenderer;
        this.spriteBatch   = spriteBatch;
        this.font          = font;
        this.glyphLayout   = glyphLayout;
        this.textBuilder   = new StringBuilder(64);
    }

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
        return worldX >= ItemConstants.INV_ITEM_WINDOW_X
                && worldX <= ItemConstants.INV_ITEM_WINDOW_X + ItemConstants.INV_ITEM_WINDOW_WIDTH
                && worldY >= ItemConstants.INV_ITEM_WINDOW_Y
                && worldY <= ItemConstants.INV_ITEM_WINDOW_Y + ItemConstants.INV_ITEM_WINDOW_HEIGHT;
    }

    void updateFlash(float deltaTime) {
        if (flashTimerSeconds > 0f) flashTimerSeconds -= deltaTime;
    }

    InventoryOverlayRenderer.CloseAction handleTouch(float worldX, float worldY) {
        if (worldY >= ItemConstants.INV_ITEM_WIN_BTN_Y
                && worldY <= ItemConstants.INV_ITEM_WIN_BTN_Y + ItemConstants.INV_ITEM_WIN_BTN_HEIGHT) {
            if (worldX >= ItemConstants.INV_ITEM_WIN_BTN_EXIT_X
                    && worldX <= ItemConstants.INV_ITEM_WIN_BTN_EXIT_X + ItemConstants.INV_ITEM_WIN_BTN_WIDTH) {
                return InventoryOverlayRenderer.CloseAction.CLOSE_WINDOW;
            }
            if (worldX >= ItemConstants.INV_ITEM_WIN_BTN_USE_X
                    && worldX <= ItemConstants.INV_ITEM_WIN_BTN_USE_X + ItemConstants.INV_ITEM_WIN_BTN_WIDTH) {
                return handleUse();
            }
            if (worldX >= ItemConstants.INV_ITEM_WIN_BTN_DROP_X
                    && worldX <= ItemConstants.INV_ITEM_WIN_BTN_DROP_X + ItemConstants.INV_ITEM_WIN_BTN_WIDTH) {
                return handleDrop();
            }
        }
        return InventoryOverlayRenderer.CloseAction.NONE;
    }

    void render(OrthographicCamera camera, float animationClock) {
        renderBackground(camera);
        renderContent(camera);
    }

    // -------------------------------------------------------------------------
    // Private — action handlers
    // -------------------------------------------------------------------------

    private InventoryOverlayRenderer.CloseAction handleUse() {
        if (inventory == null || slotIndex < 0) return InventoryOverlayRenderer.CloseAction.NONE;
        ItemStack slot = inventory.getSlot(slotIndex);
        if (slot.isEmpty()) return InventoryOverlayRenderer.CloseAction.CLOSE_WINDOW;

        ItemCategory category = slot.getType().getCategory();
        if (category == ItemCategory.AMMO) {
            showFlash("Loaded by weapons");
            return InventoryOverlayRenderer.CloseAction.NONE;
        }
        if (category == ItemCategory.KEY_ITEM || category == ItemCategory.MOD
                || category == ItemCategory.MISC) {
            showFlash("Cannot use here");
            return InventoryOverlayRenderer.CloseAction.NONE;
        }
        if (category == ItemCategory.WEAPON) {
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
        inventory.remove(slotIndex, slot.getQuantity());
        return InventoryOverlayRenderer.CloseAction.CLOSE_WITH_TURN;
    }

    private void showFlash(String message) {
        flashMessage      = message;
        flashTimerSeconds = ItemConstants.INV_FLASH_SECONDS;
    }

    // -------------------------------------------------------------------------
    // Private — render passes
    // -------------------------------------------------------------------------

    private void renderBackground(OrthographicCamera camera) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(WINDOW_BG);
        shapeRenderer.rect(ItemConstants.INV_ITEM_WINDOW_X, ItemConstants.INV_ITEM_WINDOW_Y,
                           ItemConstants.INV_ITEM_WINDOW_WIDTH, ItemConstants.INV_ITEM_WINDOW_HEIGHT);
        shapeRenderer.setColor(BTN_BG);
        shapeRenderer.rect(ItemConstants.INV_ITEM_WIN_BTN_USE_X,  ItemConstants.INV_ITEM_WIN_BTN_Y,
                           ItemConstants.INV_ITEM_WIN_BTN_WIDTH,  ItemConstants.INV_ITEM_WIN_BTN_HEIGHT);
        shapeRenderer.rect(ItemConstants.INV_ITEM_WIN_BTN_DROP_X, ItemConstants.INV_ITEM_WIN_BTN_Y,
                           ItemConstants.INV_ITEM_WIN_BTN_WIDTH,  ItemConstants.INV_ITEM_WIN_BTN_HEIGHT);
        shapeRenderer.setColor(EXIT_BTN_BG);
        shapeRenderer.rect(ItemConstants.INV_ITEM_WIN_BTN_EXIT_X, ItemConstants.INV_ITEM_WIN_BTN_Y,
                           ItemConstants.INV_ITEM_WIN_BTN_WIDTH,  ItemConstants.INV_ITEM_WIN_BTN_HEIGHT);
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(WINDOW_BORDER);
        shapeRenderer.rect(ItemConstants.INV_ITEM_WINDOW_X, ItemConstants.INV_ITEM_WINDOW_Y,
                           ItemConstants.INV_ITEM_WINDOW_WIDTH, ItemConstants.INV_ITEM_WINDOW_HEIGHT);
        shapeRenderer.setColor(HEADER_ACCENT);
        shapeRenderer.line(ItemConstants.INV_ITEM_WINDOW_X + 10f, HEADER_LINE_Y,
                           ItemConstants.INV_ITEM_WINDOW_X + ItemConstants.INV_ITEM_WINDOW_WIDTH - 10f,
                           HEADER_LINE_Y);
        shapeRenderer.setColor(BTN_BORDER);
        shapeRenderer.rect(ItemConstants.INV_ITEM_WIN_BTN_USE_X,  ItemConstants.INV_ITEM_WIN_BTN_Y,
                           ItemConstants.INV_ITEM_WIN_BTN_WIDTH,  ItemConstants.INV_ITEM_WIN_BTN_HEIGHT);
        shapeRenderer.rect(ItemConstants.INV_ITEM_WIN_BTN_DROP_X, ItemConstants.INV_ITEM_WIN_BTN_Y,
                           ItemConstants.INV_ITEM_WIN_BTN_WIDTH,  ItemConstants.INV_ITEM_WIN_BTN_HEIGHT);
        shapeRenderer.rect(ItemConstants.INV_ITEM_WIN_BTN_EXIT_X, ItemConstants.INV_ITEM_WIN_BTN_Y,
                           ItemConstants.INV_ITEM_WIN_BTN_WIDTH,  ItemConstants.INV_ITEM_WIN_BTN_HEIGHT);
        shapeRenderer.end();
    }

    private void renderContent(OrthographicCamera camera) {
        if (inventory == null || slotIndex < 0) return;

        spriteBatch.setProjectionMatrix(camera.combined);
        spriteBatch.begin();

        float contentX = ItemConstants.INV_ITEM_WINDOW_X + 16f;
        float contentY = ItemConstants.INV_ITEM_WINDOW_Y + ItemConstants.INV_ITEM_WINDOW_HEIGHT - 16f;
        float lineStep = 22f;
        ItemStack slot = inventory.getSlot(slotIndex);

        if (slot.isEmpty()) {
            font.setColor(TEXT_DIM);
            font.draw(spriteBatch, "— EMPTY SLOT —", contentX, contentY);
        } else {
            ItemType itemType = slot.getType();

            font.setColor(AMBER);
            font.draw(spriteBatch, itemType.getDisplayName(), contentX, contentY);
            contentY -= lineStep + 4f;

            font.setColor(TEXT_DIM);
            font.draw(spriteBatch, itemType.getCategory().name(), contentX, contentY);
            contentY -= lineStep + 6f;

            font.setColor(WHITE);
            font.draw(spriteBatch, effectDescriptionFor(itemType), contentX, contentY);
            contentY -= lineStep;

            if (itemType.isStackable() || slot.getQuantity() > 1) {
                font.setColor(TEXT_DIM);
                textBuilder.setLength(0);
                textBuilder.append("Qty: ").append(slot.getQuantity())
                           .append(" / ").append(itemType.getMaxStackSize());
                font.draw(spriteBatch, textBuilder.toString(), contentX, contentY);
                contentY -= lineStep;
            }

            if (itemType.getCategory() == ItemCategory.WEAPON) {
                if (inventory.getEquippedWeaponSlot() == slotIndex) {
                    font.setColor(GREEN_EQUIPPED);
                    font.draw(spriteBatch, "[EQUIPPED]", contentX, contentY);
                } else {
                    font.setColor(TEXT_DIM);
                    font.draw(spriteBatch, "Tap USE to equip", contentX, contentY);
                }
            }
        }

        drawButtonLabel("USE / EQUIP", ItemConstants.INV_ITEM_WIN_BTN_USE_X,  AMBER);
        drawButtonLabel("DROP",        ItemConstants.INV_ITEM_WIN_BTN_DROP_X, AMBER);
        drawButtonLabel("EXIT",        ItemConstants.INV_ITEM_WIN_BTN_EXIT_X, EXIT_BTN_TEXT);

        if (flashTimerSeconds > 0f && flashMessage != null) {
            font.setColor(RED_FLASH);
            glyphLayout.setText(font, flashMessage);
            font.draw(spriteBatch, flashMessage,
                      ItemConstants.INV_ITEM_WINDOW_X
                              + (ItemConstants.INV_ITEM_WINDOW_WIDTH - glyphLayout.width) / 2f,
                      ItemConstants.INV_ITEM_WIN_BTN_Y + ItemConstants.INV_ITEM_WIN_BTN_AREA_HEIGHT + 10f);
        }

        spriteBatch.end();
    }

    private void drawButtonLabel(String label, float buttonX, Color color) {
        glyphLayout.setText(font, label);
        font.setColor(color);
        font.draw(spriteBatch, label,
                  buttonX + (ItemConstants.INV_ITEM_WIN_BTN_WIDTH  - glyphLayout.width)  / 2f,
                  ItemConstants.INV_ITEM_WIN_BTN_Y
                          + (ItemConstants.INV_ITEM_WIN_BTN_HEIGHT + glyphLayout.height) / 2f);
    }

    private static String effectDescriptionFor(ItemType itemType) {
        switch (itemType) {
            case MEDKIT_SMALL:         return "+25 HP";
            case MEDKIT_LARGE:         return "+75 HP";
            case STIMPACK:             return "+15 HP";
            case WEAPON_PISTOL:        return "Sidearm. Reliable.";
            case WEAPON_SHOTGUN:       return "Close-range spread.";
            case WEAPON_DOUBLE_BARREL: return "Double burst.";
            case WEAPON_CHAINGUN:      return "Sustained fire.";
            case WEAPON_PLASMA:        return "Energy weapon.";
            case WEAPON_RAILGUN:       return "Armour-piercing.";
            case WEAPON_INCINERATOR:   return "Burns everything.";
            case WEAPON_ROCKET:        return "High explosive.";
            case WEAPON_FIST:          return "Bare-handed. Always available.";
            case WEAPON_KNIFE:         return "Fast light melee.";
            case WEAPON_HAMMER:        return "Knockback on hit.";
            case WEAPON_CHAINSAW:      return "High sustained damage.";
            case KEYCARD_RED:          return "Unlocks red security doors.";
            case KEYCARD_YELLOW:       return "Unlocks yellow security doors.";
            case KEYCARD_BLUE:         return "Unlocks blue security doors.";
            case CREDITS:              return "UAC trade currency.";
            default:                   return "—";
        }
    }

    @Override
    public void dispose() {
        // Shared resources owned by InventoryOverlayRenderer coordinator
    }
}
