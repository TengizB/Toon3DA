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
import ge.tbegvadze.toon3d.item.Inventory;
import ge.tbegvadze.toon3d.item.ItemCategory;
import ge.tbegvadze.toon3d.item.ItemStack;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.ItemConstants;

/**
 * Full-screen inventory overlay drawn on top of the frozen world frame.
 *
 * Displays a slot grid (left panel) and an item detail panel (right panel).
 * The frozen 3D world is visible through a 60%-black scrim.
 *
 * Lifecycle:
 *   - Call onOpen() when transitioning to INVENTORY_OPEN to reset cursor state.
 *   - Call setTime(facilityTimeSeconds) and setCurrentDepth(depth) each render frame.
 *   - Call handleInput(deltaTime) each update frame — returns CloseAction.
 *   - Call render(camera) each render frame when phase is INVENTORY_OPEN.
 *
 * No allocations inside render() or handleInput() — all scratch objects pre-allocated.
 */
public final class InventoryOverlayRenderer implements Renderable, Disposable {

    /** Returned by handleInput() to instruct World on whether and how to close the overlay. */
    public enum CloseAction {
        /** Overlay stays open. */
        NONE,
        /** Close without spending a player turn (navigation, equip, ESC/I). */
        CLOSE_FREE,
        /** Close and spend one world tick (consumable use, item drop). */
        CLOSE_WITH_TURN
    }

    // -------------------------------------------------------------------------
    // Palette — UAC field-terminal CRT amber on charcoal
    // -------------------------------------------------------------------------
    private static final Color SCRIM_COLOR     = new Color(0f, 0f, 0f, ItemConstants.INV_SCRIM_ALPHA);
    private static final Color PANEL_BG        = new Color(0.071f, 0.071f, 0.086f, ItemConstants.INV_PANEL_ALPHA);
    private static final Color SLOT_BG         = new Color(0.102f, 0.102f, 0.122f, 1f);
    private static final Color SLOT_BORDER     = new Color(0.220f, 0.220f, 0.255f, 1f);
    private static final Color AMBER           = new Color(0.902f, 0.667f, 0.157f, 1f);
    private static final Color AMBER_DIM       = new Color(0.400f, 0.290f, 0.050f, 1f);
    private static final Color WHITE_COLOR     = new Color(1f, 1f, 1f, 1f);
    private static final Color DIM_COLOR       = new Color(0.500f, 0.500f, 0.500f, 1f);
    private static final Color RED_FLASH       = new Color(0.950f, 0.150f, 0.150f, 1f);
    private static final Color GREEN_EQUIPPED  = new Color(0.200f, 0.900f, 0.300f, 1f);

    // -------------------------------------------------------------------------
    // Derived layout constants — computed once from Constants at class load time
    // -------------------------------------------------------------------------
    private static final int   GRID_ROW_COUNT     = (int) Math.ceil(
            (double) ItemConstants.INVENTORY_SLOT_COUNT / ItemConstants.INVENTORY_GRID_COLUMNS);
    private static final float SLOT_STRIDE        = ItemConstants.INV_SLOT_SIZE + ItemConstants.INV_SLOT_GAP;
    private static final float GRID_PIXEL_WIDTH   = ItemConstants.INVENTORY_GRID_COLUMNS * SLOT_STRIDE - ItemConstants.INV_SLOT_GAP;
    private static final float GRID_PIXEL_HEIGHT  = GRID_ROW_COUNT * SLOT_STRIDE - ItemConstants.INV_SLOT_GAP;

    // Left panel chrome — 20 px padding around the slot grid
    private static final float LEFT_PANEL_X       = ItemConstants.INV_GRID_ORIGIN_X - 20f;
    private static final float LEFT_PANEL_Y       = ItemConstants.INV_GRID_ORIGIN_Y - GRID_PIXEL_HEIGHT - 20f;
    private static final float LEFT_PANEL_WIDTH   = GRID_PIXEL_WIDTH + 40f;
    private static final float LEFT_PANEL_HEIGHT  = GRID_PIXEL_HEIGHT + 40f;

    // Header bar — sits directly on top of the two content panels
    private static final float HEADER_Y           = LEFT_PANEL_Y + LEFT_PANEL_HEIGHT;
    private static final float HEADER_HEIGHT      = 30f;
    private static final float HEADER_X           = LEFT_PANEL_X;
    private static final float HEADER_WIDTH       = ItemConstants.INV_DETAIL_PANEL_X
                                                    + ItemConstants.INV_DETAIL_PANEL_WIDTH - LEFT_PANEL_X;

    // Footer bar — sits just below the two content panels; tall enough for touch buttons
    private static final float FOOTER_HEIGHT      = 44f;
    private static final float FOOTER_Y           = LEFT_PANEL_Y - ItemConstants.INV_SLOT_GAP - FOOTER_HEIGHT;
    private static final float FOOTER_X           = LEFT_PANEL_X;
    private static final float FOOTER_WIDTH       = HEADER_WIDTH;

    // Action buttons inside the footer bar: [USE/EQUIP] [DROP] [CLOSE]
    private static final float BTN_PADDING  = 6f;
    private static final float BTN_GAP      = 8f;
    private static final float BTN_HEIGHT   = FOOTER_HEIGHT - 2 * BTN_PADDING;
    private static final float BTN_WIDTH    = (FOOTER_WIDTH - 2 * BTN_PADDING - 2 * BTN_GAP) / 3f;
    private static final float BTN_Y        = FOOTER_Y + BTN_PADDING;
    private static final float BTN_USE_X    = FOOTER_X + BTN_PADDING;
    private static final float BTN_DROP_X   = BTN_USE_X  + BTN_WIDTH + BTN_GAP;
    private static final float BTN_CLOSE_X  = BTN_DROP_X + BTN_WIDTH + BTN_GAP;

    private static final Color  BTN_BG           = new Color(0.14f, 0.14f, 0.17f, 1f);
    private static final Color  BTN_BORDER       = new Color(0.35f, 0.35f, 0.40f, 1f);
    private static final String[] BTN_LABELS     = { "USE / EQUIP", "DROP", "CLOSE" };
    private static final float[]  BTN_LEFT_EDGES = { BTN_USE_X, BTN_DROP_X, BTN_CLOSE_X };

    // -------------------------------------------------------------------------
    // Owned resources — disposed in dispose()
    // -------------------------------------------------------------------------
    private final ShapeRenderer shapeRenderer;
    private final SpriteBatch   spriteBatch;
    private final BitmapFont    font;
    private final GlyphLayout   glyphLayout;

    // -------------------------------------------------------------------------
    // Runtime state — mutated by handleInput() and setter calls
    // -------------------------------------------------------------------------
    private final Inventory inventory;
    private       int       selectedSlotColumn  = 0;
    private       int       selectedSlotRow     = 0;
    private       float     flashTimerSeconds   = 0f;
    private       String    flashMessage        = null;
    private       float     facilityTimeSeconds = 0f;
    private       int       currentDepth        = 1;

    // Scratch — pre-allocated to avoid render() allocations
    private final Color         temporaryColor = new Color();
    private final StringBuilder textBuilder    = new StringBuilder(64);

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public InventoryOverlayRenderer(Inventory inventory) {
        this.inventory = inventory;
        shapeRenderer  = new ShapeRenderer();
        spriteBatch    = new SpriteBatch();
        font           = new BitmapFont();
        glyphLayout    = new GlyphLayout();
    }

    // -------------------------------------------------------------------------
    // Lifecycle — called by World on phase transition and each frame
    // -------------------------------------------------------------------------

    /** Reset cursor and flash state when the overlay opens. */
    public void onOpen() {
        selectedSlotColumn = 0;
        selectedSlotRow    = 0;
        flashTimerSeconds  = 0f;
        flashMessage       = null;
    }

    /** Pass the facility clock so the selection border can pulse. */
    public void setTime(float time) {
        this.facilityTimeSeconds = time;
    }

    /** Pass the current floor depth for the "SUBLEVEL N" header label. */
    public void setCurrentDepth(int depth) {
        this.currentDepth = depth;
    }

    // -------------------------------------------------------------------------
    // Input — called by World each update frame while INVENTORY_OPEN
    // -------------------------------------------------------------------------

    /** Updates the flash timer each frame. All input on mobile goes through handleTouchAt(). */
    public CloseAction handleInput(float deltaTime) {
        if (flashTimerSeconds > 0f) {
            flashTimerSeconds -= deltaTime;
        }
        return CloseAction.NONE;
    }

    /**
     * Handles a touch tap at the given world coordinates while the overlay is open.
     * Slot tap: selects on first tap; use/equip on second tap of the same slot.
     * Footer buttons: USE/EQUIP, DROP, CLOSE.
     * Header tap: closes freely.
     */
    public CloseAction handleTouchAt(float worldX, float worldY) {
        // Footer action buttons — checked first so they take priority over anything below
        if (worldY >= BTN_Y && worldY <= BTN_Y + BTN_HEIGHT) {
            if (worldX >= BTN_USE_X && worldX <= BTN_USE_X + BTN_WIDTH) {
                return handleUse();
            }
            if (worldX >= BTN_DROP_X && worldX <= BTN_DROP_X + BTN_WIDTH) {
                return handleDrop();
            }
            if (worldX >= BTN_CLOSE_X && worldX <= BTN_CLOSE_X + BTN_WIDTH) {
                return CloseAction.CLOSE_FREE;
            }
        }

        // Slot grid
        for (int slotIndex = 0; slotIndex < ItemConstants.INVENTORY_SLOT_COUNT; slotIndex++) {
            int   slotRow    = slotIndex / ItemConstants.INVENTORY_GRID_COLUMNS;
            int   slotColumn = slotIndex % ItemConstants.INVENTORY_GRID_COLUMNS;
            float slotX      = slotLeft(slotColumn);
            float slotY      = slotBottom(slotRow);
            if (worldX >= slotX && worldX <= slotX + ItemConstants.INV_SLOT_SIZE
                    && worldY >= slotY && worldY <= slotY + ItemConstants.INV_SLOT_SIZE) {
                if (slotColumn == selectedSlotColumn && slotRow == selectedSlotRow) {
                    return handleUse();
                }
                selectedSlotColumn = slotColumn;
                selectedSlotRow    = slotRow;
                return CloseAction.NONE;
            }
        }

        // Header tap closes the overlay
        if (worldX >= HEADER_X && worldX <= HEADER_X + HEADER_WIDTH
                && worldY >= HEADER_Y && worldY <= HEADER_Y + HEADER_HEIGHT) {
            return CloseAction.CLOSE_FREE;
        }
        return CloseAction.NONE;
    }

    // -------------------------------------------------------------------------
    // Render
    // -------------------------------------------------------------------------

    @Override
    public void render(OrthographicCamera camera) {
        renderFilledShapes(camera);
        renderLineShapes(camera);
        renderText(camera);
    }

    // -------------------------------------------------------------------------
    // Input helpers
    // -------------------------------------------------------------------------

    private CloseAction handleUse() {
        ItemStack slot = inventory.getSlot(selectedSlotIndex());
        if (slot.isEmpty()) return CloseAction.NONE;

        ItemCategory category = slot.getType().getCategory();

        if (category == ItemCategory.AMMO) {
            showFlash("Loaded by weapons");
            return CloseAction.NONE;
        }

        if (category == ItemCategory.KEY_ITEM
                || category == ItemCategory.MOD
                || category == ItemCategory.MISC) {
            showFlash("Cannot use here");
            return CloseAction.NONE;
        }

        if (category == ItemCategory.WEAPON) {
            // Equipping is free — overlay stays open, slot shows [EQUIPPED] next frame
            inventory.use(selectedSlotIndex());
            return CloseAction.NONE;
        }

        if (category == ItemCategory.CONSUMABLE) {
            // Consuming costs one turn; effect routing is handled by Order 7 when wired
            inventory.use(selectedSlotIndex());
            return CloseAction.CLOSE_WITH_TURN;
        }

        return CloseAction.NONE;
    }

    private CloseAction handleDrop() {
        int       slotIndex = selectedSlotIndex();
        ItemStack slot      = inventory.getSlot(slotIndex);
        if (slot.isEmpty()) return CloseAction.NONE;

        // Remove the whole stack. Ground spawning wired once GroundItemManager exists.
        inventory.remove(slotIndex, slot.getQuantity());
        return CloseAction.CLOSE_WITH_TURN;
    }

    private void showFlash(String message) {
        flashMessage      = message;
        flashTimerSeconds = ItemConstants.INV_FLASH_SECONDS;
    }

    private int selectedSlotIndex() {
        return selectedSlotRow * ItemConstants.INVENTORY_GRID_COLUMNS + selectedSlotColumn;
    }

    // -------------------------------------------------------------------------
    // Render pass A — filled shapes (backgrounds, slot fills)
    // -------------------------------------------------------------------------

    private void renderFilledShapes(OrthographicCamera camera) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Full-screen dimming scrim
        shapeRenderer.setColor(SCRIM_COLOR);
        shapeRenderer.rect(0, 0, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);

        // Header bar
        shapeRenderer.setColor(PANEL_BG);
        shapeRenderer.rect(HEADER_X, HEADER_Y, HEADER_WIDTH, HEADER_HEIGHT);

        // Left panel (slot grid area)
        shapeRenderer.rect(LEFT_PANEL_X, LEFT_PANEL_Y, LEFT_PANEL_WIDTH, LEFT_PANEL_HEIGHT);

        // Detail panel
        shapeRenderer.rect(ItemConstants.INV_DETAIL_PANEL_X, LEFT_PANEL_Y,
                           ItemConstants.INV_DETAIL_PANEL_WIDTH, LEFT_PANEL_HEIGHT);

        // Footer bar
        shapeRenderer.rect(FOOTER_X, FOOTER_Y, FOOTER_WIDTH, FOOTER_HEIGHT);

        // Action button backgrounds
        shapeRenderer.setColor(BTN_BG);
        shapeRenderer.rect(BTN_USE_X,   BTN_Y, BTN_WIDTH, BTN_HEIGHT);
        shapeRenderer.rect(BTN_DROP_X,  BTN_Y, BTN_WIDTH, BTN_HEIGHT);
        shapeRenderer.rect(BTN_CLOSE_X, BTN_Y, BTN_WIDTH, BTN_HEIGHT);

        // Individual slot backgrounds
        for (int slotIndex = 0; slotIndex < ItemConstants.INVENTORY_SLOT_COUNT; slotIndex++) {
            int   slotRow    = slotIndex / ItemConstants.INVENTORY_GRID_COLUMNS;
            int   slotColumn = slotIndex % ItemConstants.INVENTORY_GRID_COLUMNS;
            float slotX      = slotLeft(slotColumn);
            float slotY      = slotBottom(slotRow);
            shapeRenderer.setColor(SLOT_BG);
            shapeRenderer.rect(slotX, slotY, ItemConstants.INV_SLOT_SIZE, ItemConstants.INV_SLOT_SIZE);
        }

        shapeRenderer.end();
    }

    // -------------------------------------------------------------------------
    // Render pass B — line shapes (borders, underlines)
    // -------------------------------------------------------------------------

    private void renderLineShapes(OrthographicCamera camera) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        // Amber underline separating header from content
        shapeRenderer.setColor(AMBER);
        shapeRenderer.line(HEADER_X, HEADER_Y, HEADER_X + HEADER_WIDTH, HEADER_Y);

        // Slot borders
        for (int slotIndex = 0; slotIndex < ItemConstants.INVENTORY_SLOT_COUNT; slotIndex++) {
            int   slotRow    = slotIndex / ItemConstants.INVENTORY_GRID_COLUMNS;
            int   slotColumn = slotIndex % ItemConstants.INVENTORY_GRID_COLUMNS;
            float slotX      = slotLeft(slotColumn);
            float slotY      = slotBottom(slotRow);

            boolean isSelected = (slotColumn == selectedSlotColumn && slotRow == selectedSlotRow);
            if (isSelected) {
                float pulse = 0.7f + 0.3f * MathUtils.sin(facilityTimeSeconds * 4f);
                temporaryColor.set(AMBER.r * pulse, AMBER.g * pulse, AMBER.b * pulse, 1f);
                shapeRenderer.setColor(temporaryColor);
                // Concentric rects produce a thick pulsing border (INV_SELECT_BORDER_THICKNESS pixels)
                int borderThickness = (int) ItemConstants.INV_SELECT_BORDER_THICKNESS;
                for (int borderStep = 0; borderStep < borderThickness; borderStep++) {
                    shapeRenderer.rect(
                            slotX + borderStep,
                            slotY + borderStep,
                            ItemConstants.INV_SLOT_SIZE - borderStep * 2,
                            ItemConstants.INV_SLOT_SIZE - borderStep * 2);
                }
            } else {
                shapeRenderer.setColor(SLOT_BORDER);
                shapeRenderer.rect(slotX, slotY, ItemConstants.INV_SLOT_SIZE, ItemConstants.INV_SLOT_SIZE);
            }
        }

        // Dim top-border on detail panel to separate it visually
        shapeRenderer.setColor(AMBER_DIM);
        shapeRenderer.line(ItemConstants.INV_DETAIL_PANEL_X,
                           LEFT_PANEL_Y + LEFT_PANEL_HEIGHT,
                           ItemConstants.INV_DETAIL_PANEL_X + ItemConstants.INV_DETAIL_PANEL_WIDTH,
                           LEFT_PANEL_Y + LEFT_PANEL_HEIGHT);

        // Action button borders
        shapeRenderer.setColor(BTN_BORDER);
        shapeRenderer.rect(BTN_USE_X,   BTN_Y, BTN_WIDTH, BTN_HEIGHT);
        shapeRenderer.rect(BTN_DROP_X,  BTN_Y, BTN_WIDTH, BTN_HEIGHT);
        shapeRenderer.rect(BTN_CLOSE_X, BTN_Y, BTN_WIDTH, BTN_HEIGHT);

        shapeRenderer.end();
    }

    // -------------------------------------------------------------------------
    // Render pass C — SpriteBatch text (glyphs, labels, detail, footer)
    // -------------------------------------------------------------------------

    private void renderText(OrthographicCamera camera) {
        spriteBatch.setProjectionMatrix(camera.combined);
        spriteBatch.begin();

        drawHeader();
        drawSlotContents();
        drawDetailPanel();
        drawFooter();
        drawFlashMessage();

        spriteBatch.end();
    }

    private void drawHeader() {
        font.setColor(AMBER);
        font.draw(spriteBatch, "FIELD INVENTORY",
                  HEADER_X + 8f,
                  HEADER_Y + HEADER_HEIGHT - 6f);

        textBuilder.setLength(0);
        textBuilder.append("SUBLEVEL ").append(currentDepth);
        String sublevelLabel = textBuilder.toString();
        glyphLayout.setText(font, sublevelLabel);
        font.draw(spriteBatch, sublevelLabel,
                  HEADER_X + HEADER_WIDTH - glyphLayout.width - 8f,
                  HEADER_Y + HEADER_HEIGHT - 6f);
    }

    private void drawSlotContents() {
        for (int slotIndex = 0; slotIndex < ItemConstants.INVENTORY_SLOT_COUNT; slotIndex++) {
            int   slotRow    = slotIndex / ItemConstants.INVENTORY_GRID_COLUMNS;
            int   slotColumn = slotIndex % ItemConstants.INVENTORY_GRID_COLUMNS;
            float slotX      = slotLeft(slotColumn);
            float slotY      = slotBottom(slotRow);

            ItemStack slot = inventory.getSlot(slotIndex);
            if (slot.isEmpty()) continue;

            ItemType itemType = slot.getType();

            // Glyph — centered in slot
            font.setColor(itemType.getGlyphRed(), itemType.getGlyphGreen(), itemType.getGlyphBlue(), 1f);
            textBuilder.setLength(0);
            textBuilder.append(itemType.getGlyph());
            String glyphText = textBuilder.toString();
            glyphLayout.setText(font, glyphText);
            font.draw(spriteBatch, glyphText,
                      slotX + (ItemConstants.INV_SLOT_SIZE - glyphLayout.width) / 2f,
                      slotY + (ItemConstants.INV_SLOT_SIZE + glyphLayout.height) / 2f);

            // Stack count bottom-right if quantity > 1
            if (slot.getQuantity() > 1) {
                font.setColor(DIM_COLOR);
                textBuilder.setLength(0);
                textBuilder.append('x').append(slot.getQuantity());
                String quantityLabel = textBuilder.toString();
                glyphLayout.setText(font, quantityLabel);
                font.draw(spriteBatch, quantityLabel,
                          slotX + ItemConstants.INV_SLOT_SIZE - glyphLayout.width - 4f,
                          slotY + glyphLayout.height + 4f);
            }

            // Small star marker for the equipped weapon slot
            if (itemType.getCategory() == ItemCategory.WEAPON
                    && inventory.getEquippedWeaponSlot() == slotIndex) {
                font.setColor(GREEN_EQUIPPED);
                font.draw(spriteBatch, "*",
                          slotX + ItemConstants.INV_SLOT_SIZE - 12f,
                          slotY + ItemConstants.INV_SLOT_SIZE - 4f);
            }
        }
    }

    private void drawDetailPanel() {
        ItemStack slot     = inventory.getSlot(selectedSlotIndex());
        float     textX    = ItemConstants.INV_DETAIL_PANEL_X + 16f;
        float     textY    = LEFT_PANEL_Y + LEFT_PANEL_HEIGHT - 20f;
        float     lineStep = 20f;

        if (slot.isEmpty()) {
            font.setColor(DIM_COLOR);
            font.draw(spriteBatch, "— EMPTY SLOT —", textX, textY);
            return;
        }

        ItemType itemType = slot.getType();

        // Item name — amber, prominent
        font.setColor(AMBER);
        font.draw(spriteBatch, itemType.getDisplayName(), textX, textY);
        textY -= lineStep + 4f;

        // Category label
        font.setColor(DIM_COLOR);
        font.draw(spriteBatch, itemType.getCategory().name(), textX, textY);
        textY -= lineStep + 6f;

        // Effect description
        font.setColor(WHITE_COLOR);
        font.draw(spriteBatch, effectDescriptionFor(itemType), textX, textY);
        textY -= lineStep;

        // Stack quantity (shown when stackable or quantity > 1)
        if (itemType.isStackable() || slot.getQuantity() > 1) {
            font.setColor(DIM_COLOR);
            textBuilder.setLength(0);
            textBuilder.append("Qty: ").append(slot.getQuantity())
                       .append(" / ").append(itemType.getMaxStackSize());
            font.draw(spriteBatch, textBuilder.toString(), textX, textY);
            textY -= lineStep;
        }

        // Weapon equip status
        if (itemType.getCategory() == ItemCategory.WEAPON) {
            if (inventory.getEquippedWeaponSlot() == selectedSlotIndex()) {
                font.setColor(GREEN_EQUIPPED);
                font.draw(spriteBatch, "[EQUIPPED]", textX, textY);
            } else {
                font.setColor(DIM_COLOR);
                font.draw(spriteBatch, "ENTER to equip", textX, textY);
            }
        }
    }

    private void drawFooter() {
        // Draw action button labels centred in each button
        for (int buttonIndex = 0; buttonIndex < BTN_LABELS.length; buttonIndex++) {
            glyphLayout.setText(font, BTN_LABELS[buttonIndex]);
            // In LibGDX Y-up, font.draw() Y = top of text; centre = bottom + (height + textHeight) / 2
            float labelX = BTN_LEFT_EDGES[buttonIndex] + (BTN_WIDTH  - glyphLayout.width)  / 2f;
            float labelY = BTN_Y                       + (BTN_HEIGHT + glyphLayout.height)  / 2f;
            font.setColor(AMBER);
            font.draw(spriteBatch, BTN_LABELS[buttonIndex], labelX, labelY);
        }

        // Slot usage counter at bottom-right of footer bar
        int usedSlotCount = 0;
        for (int slotIndex = 0; slotIndex < ItemConstants.INVENTORY_SLOT_COUNT; slotIndex++) {
            if (!inventory.getSlot(slotIndex).isEmpty()) usedSlotCount++;
        }
        textBuilder.setLength(0);
        textBuilder.append("SLOTS ").append(usedSlotCount).append('/').append(ItemConstants.INVENTORY_SLOT_COUNT);
        String slotsLabel = textBuilder.toString();
        glyphLayout.setText(font, slotsLabel);
        font.setColor(DIM_COLOR);
        font.draw(spriteBatch, slotsLabel,
                  FOOTER_X + FOOTER_WIDTH - glyphLayout.width - 8f,
                  FOOTER_Y + FOOTER_HEIGHT - 4f);
    }

    private void drawFlashMessage() {
        if (flashTimerSeconds <= 0f || flashMessage == null) return;
        font.setColor(RED_FLASH);
        glyphLayout.setText(font, flashMessage);
        font.draw(spriteBatch, flashMessage,
                  LEFT_PANEL_X + (LEFT_PANEL_WIDTH - glyphLayout.width) / 2f,
                  LEFT_PANEL_Y - 8f);
    }

    // -------------------------------------------------------------------------
    // Slot layout helpers — pure math, no allocations
    // -------------------------------------------------------------------------

    private static float slotLeft(int slotColumn) {
        return ItemConstants.INV_GRID_ORIGIN_X + slotColumn * SLOT_STRIDE;
    }

    private static float slotBottom(int slotRow) {
        return ItemConstants.INV_GRID_ORIGIN_Y - slotRow * SLOT_STRIDE - ItemConstants.INV_SLOT_SIZE;
    }

    // -------------------------------------------------------------------------
    // Item descriptions — returns a short effect string per ItemType
    // -------------------------------------------------------------------------

    private static String effectDescriptionFor(ItemType itemType) {
        switch (itemType) {
            case MEDKIT_SMALL:     return "+25 HP";
            case MEDKIT_LARGE:     return "+75 HP";
            case STIMPACK:         return "+15 HP";
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
            case WEAPON_PIPE:          return "Knockback on hit.";
            case WEAPON_CHAINSAW:      return "High sustained damage.";
            case KEYCARD_RED:      return "Unlocks red security doors.";
            case KEYCARD_YELLOW:   return "Unlocks yellow security doors.";
            case KEYCARD_BLUE:     return "Unlocks blue security doors.";
            case CREDITS:          return "UAC trade currency.";
            default:               return "—";
        }
    }

    // -------------------------------------------------------------------------
    // Disposable
    // -------------------------------------------------------------------------

    @Override
    public void dispose() {
        shapeRenderer.dispose();
        spriteBatch.dispose();
        font.dispose();
    }
}
