package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
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
    private static final Color SCRIM_COLOR     = new Color(0f, 0f, 0f, Constants.INV_SCRIM_ALPHA);
    private static final Color PANEL_BG        = new Color(0.071f, 0.071f, 0.086f, Constants.INV_PANEL_ALPHA);
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
            (double) Constants.INVENTORY_SLOT_COUNT / Constants.INVENTORY_GRID_COLUMNS);
    private static final float SLOT_STRIDE        = Constants.INV_SLOT_SIZE + Constants.INV_SLOT_GAP;
    private static final float GRID_PIXEL_WIDTH   = Constants.INVENTORY_GRID_COLUMNS * SLOT_STRIDE - Constants.INV_SLOT_GAP;
    private static final float GRID_PIXEL_HEIGHT  = GRID_ROW_COUNT * SLOT_STRIDE - Constants.INV_SLOT_GAP;

    // Left panel chrome — 20 px padding around the slot grid
    private static final float LEFT_PANEL_X       = Constants.INV_GRID_ORIGIN_X - 20f;
    private static final float LEFT_PANEL_Y       = Constants.INV_GRID_ORIGIN_Y - GRID_PIXEL_HEIGHT - 20f;
    private static final float LEFT_PANEL_WIDTH   = GRID_PIXEL_WIDTH + 40f;
    private static final float LEFT_PANEL_HEIGHT  = GRID_PIXEL_HEIGHT + 40f;

    // Header bar — sits directly on top of the two content panels
    private static final float HEADER_Y           = LEFT_PANEL_Y + LEFT_PANEL_HEIGHT;
    private static final float HEADER_HEIGHT      = 30f;
    private static final float HEADER_X           = LEFT_PANEL_X;
    private static final float HEADER_WIDTH       = Constants.INV_DETAIL_PANEL_X
                                                    + Constants.INV_DETAIL_PANEL_WIDTH - LEFT_PANEL_X;

    // Footer bar — sits just below the two content panels
    private static final float FOOTER_HEIGHT      = 28f;
    private static final float FOOTER_Y           = LEFT_PANEL_Y - Constants.INV_SLOT_GAP - FOOTER_HEIGHT;
    private static final float FOOTER_X           = LEFT_PANEL_X;
    private static final float FOOTER_WIDTH       = HEADER_WIDTH;

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

    /**
     * Process one frame of keyboard input.
     * Returns NONE while the overlay stays open, CLOSE_FREE for a free close,
     * or CLOSE_WITH_TURN when the action costs a game turn (use/drop).
     */
    public CloseAction handleInput(float deltaTime) {
        if (flashTimerSeconds > 0f) {
            flashTimerSeconds -= deltaTime;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT)) {
            moveSelection(-1, 0);
            return CloseAction.NONE;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) {
            moveSelection(1, 0);
            return CloseAction.NONE;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) {
            moveSelection(0, -1);
            return CloseAction.NONE;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) {
            moveSelection(0, 1);
            return CloseAction.NONE;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            return handleUse();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.D)) {
            return handleDrop();
        }

        if (Gdx.input.isKeyJustPressed(Constants.KEY_OPEN_INVENTORY)
                || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
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

    private void moveSelection(int columnDelta, int rowDelta) {
        int newColumn    = MathUtils.clamp(selectedSlotColumn + columnDelta, 0, Constants.INVENTORY_GRID_COLUMNS - 1);
        int newRow       = MathUtils.clamp(selectedSlotRow    + rowDelta,    0, GRID_ROW_COUNT - 1);
        int newSlotIndex = newRow * Constants.INVENTORY_GRID_COLUMNS + newColumn;
        if (newSlotIndex < Constants.INVENTORY_SLOT_COUNT) {
            selectedSlotColumn = newColumn;
            selectedSlotRow    = newRow;
        }
    }

    private CloseAction handleUse() {
        ItemStack slot = inventory.getSlot(selectedSlotIndex());
        if (slot.isEmpty()) return CloseAction.NONE;

        ItemCategory category = slot.getType().getCategory();

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
        flashTimerSeconds = Constants.INV_FLASH_SECONDS;
    }

    private int selectedSlotIndex() {
        return selectedSlotRow * Constants.INVENTORY_GRID_COLUMNS + selectedSlotColumn;
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
        shapeRenderer.rect(Constants.INV_DETAIL_PANEL_X, LEFT_PANEL_Y,
                           Constants.INV_DETAIL_PANEL_WIDTH, LEFT_PANEL_HEIGHT);

        // Footer bar
        shapeRenderer.rect(FOOTER_X, FOOTER_Y, FOOTER_WIDTH, FOOTER_HEIGHT);

        // Individual slot backgrounds
        for (int slotIndex = 0; slotIndex < Constants.INVENTORY_SLOT_COUNT; slotIndex++) {
            int   slotRow    = slotIndex / Constants.INVENTORY_GRID_COLUMNS;
            int   slotColumn = slotIndex % Constants.INVENTORY_GRID_COLUMNS;
            float slotX      = slotLeft(slotColumn);
            float slotY      = slotBottom(slotRow);
            shapeRenderer.setColor(SLOT_BG);
            shapeRenderer.rect(slotX, slotY, Constants.INV_SLOT_SIZE, Constants.INV_SLOT_SIZE);
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
        for (int slotIndex = 0; slotIndex < Constants.INVENTORY_SLOT_COUNT; slotIndex++) {
            int   slotRow    = slotIndex / Constants.INVENTORY_GRID_COLUMNS;
            int   slotColumn = slotIndex % Constants.INVENTORY_GRID_COLUMNS;
            float slotX      = slotLeft(slotColumn);
            float slotY      = slotBottom(slotRow);

            boolean isSelected = (slotColumn == selectedSlotColumn && slotRow == selectedSlotRow);
            if (isSelected) {
                float pulse = 0.7f + 0.3f * MathUtils.sin(facilityTimeSeconds * 4f);
                temporaryColor.set(AMBER.r * pulse, AMBER.g * pulse, AMBER.b * pulse, 1f);
                shapeRenderer.setColor(temporaryColor);
                // Concentric rects produce a thick pulsing border (INV_SELECT_BORDER_THICKNESS pixels)
                int borderThickness = (int) Constants.INV_SELECT_BORDER_THICKNESS;
                for (int borderStep = 0; borderStep < borderThickness; borderStep++) {
                    shapeRenderer.rect(
                            slotX + borderStep,
                            slotY + borderStep,
                            Constants.INV_SLOT_SIZE - borderStep * 2,
                            Constants.INV_SLOT_SIZE - borderStep * 2);
                }
            } else {
                shapeRenderer.setColor(SLOT_BORDER);
                shapeRenderer.rect(slotX, slotY, Constants.INV_SLOT_SIZE, Constants.INV_SLOT_SIZE);
            }
        }

        // Dim top-border on detail panel to separate it visually
        shapeRenderer.setColor(AMBER_DIM);
        shapeRenderer.line(Constants.INV_DETAIL_PANEL_X,
                           LEFT_PANEL_Y + LEFT_PANEL_HEIGHT,
                           Constants.INV_DETAIL_PANEL_X + Constants.INV_DETAIL_PANEL_WIDTH,
                           LEFT_PANEL_Y + LEFT_PANEL_HEIGHT);

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
        for (int slotIndex = 0; slotIndex < Constants.INVENTORY_SLOT_COUNT; slotIndex++) {
            int   slotRow    = slotIndex / Constants.INVENTORY_GRID_COLUMNS;
            int   slotColumn = slotIndex % Constants.INVENTORY_GRID_COLUMNS;
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
                      slotX + (Constants.INV_SLOT_SIZE - glyphLayout.width) / 2f,
                      slotY + (Constants.INV_SLOT_SIZE + glyphLayout.height) / 2f);

            // Stack count bottom-right if quantity > 1
            if (slot.getQuantity() > 1) {
                font.setColor(DIM_COLOR);
                textBuilder.setLength(0);
                textBuilder.append('x').append(slot.getQuantity());
                String quantityLabel = textBuilder.toString();
                glyphLayout.setText(font, quantityLabel);
                font.draw(spriteBatch, quantityLabel,
                          slotX + Constants.INV_SLOT_SIZE - glyphLayout.width - 4f,
                          slotY + glyphLayout.height + 4f);
            }

            // Small star marker for the equipped weapon slot
            if (itemType.getCategory() == ItemCategory.WEAPON
                    && inventory.getEquippedWeaponSlot() == slotIndex) {
                font.setColor(GREEN_EQUIPPED);
                font.draw(spriteBatch, "*",
                          slotX + Constants.INV_SLOT_SIZE - 12f,
                          slotY + Constants.INV_SLOT_SIZE - 4f);
            }
        }
    }

    private void drawDetailPanel() {
        ItemStack slot     = inventory.getSlot(selectedSlotIndex());
        float     textX    = Constants.INV_DETAIL_PANEL_X + 16f;
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
        font.setColor(DIM_COLOR);
        font.draw(spriteBatch,
                  "[ARROWS] Move  [ENTER] Use/Equip  [D] Drop  [I/ESC] Close",
                  FOOTER_X + 8f,
                  FOOTER_Y + FOOTER_HEIGHT - 6f);

        int usedSlotCount = 0;
        for (int slotIndex = 0; slotIndex < Constants.INVENTORY_SLOT_COUNT; slotIndex++) {
            if (!inventory.getSlot(slotIndex).isEmpty()) usedSlotCount++;
        }
        textBuilder.setLength(0);
        textBuilder.append("SLOTS ").append(usedSlotCount).append('/').append(Constants.INVENTORY_SLOT_COUNT);
        String slotsLabel = textBuilder.toString();
        glyphLayout.setText(font, slotsLabel);
        font.setColor(DIM_COLOR);
        font.draw(spriteBatch, slotsLabel,
                  FOOTER_X + FOOTER_WIDTH - glyphLayout.width - 8f,
                  FOOTER_Y + FOOTER_HEIGHT - 6f);
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
        return Constants.INV_GRID_ORIGIN_X + slotColumn * SLOT_STRIDE;
    }

    private static float slotBottom(int slotRow) {
        return Constants.INV_GRID_ORIGIN_Y - slotRow * SLOT_STRIDE - Constants.INV_SLOT_SIZE;
    }

    // -------------------------------------------------------------------------
    // Item descriptions — returns a short effect string per ItemType
    // -------------------------------------------------------------------------

    private static String effectDescriptionFor(ItemType itemType) {
        switch (itemType) {
            case MEDKIT_SMALL:     return "+25 HP";
            case MEDKIT_LARGE:     return "+75 HP";
            case STIMPACK:         return "+15 HP";
            case WEAPON_PISTOL:    return "Sidearm. Reliable.";
            case WEAPON_SHOTGUN:   return "Close-range spread.";
            case WEAPON_PLASMA:    return "Energy weapon.";
            case WEAPON_ROCKET:    return "High explosive.";
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
