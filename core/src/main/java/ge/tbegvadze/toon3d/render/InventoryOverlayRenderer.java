package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.entity.PlayerInventory;
import ge.tbegvadze.toon3d.item.Inventory;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.ItemConstants;

/**
 * Full-screen inventory overlay drawn on top of the frozen world frame.
 *
 * Coordinator for a layered window system:
 *   Layer 0 — full-screen scrim (60% black)
 *   Layer 1 — base inventory screen: header + left weapon panel + right item grid
 *   Layer 2 — ItemWindow popup (optional); dims layer 1 by INV_BASE_DIM_FACTOR
 *   Layer 3 — AbilityWindow popup (optional, stub); dims layer 2 by INV_ITEM_WIN_DIM_FACTOR
 *
 * Public API (World.java calls setPlayerInventory and setWeaponHudRenderer after construction):
 *   new InventoryOverlayRenderer(Inventory)
 *   onOpen()
 *   setTime(float)
 *   setCurrentDepth(int)
 *   handleInput(float deltaTime)  → CloseAction
 *   handleTouchAt(float, float)   → CloseAction
 *   render(OrthographicCamera)
 *   dispose()
 *
 * No allocations inside render() or handleInput() — all scratch objects pre-allocated.
 */
public final class InventoryOverlayRenderer implements Renderable, Disposable {

    /**
     * Returned by handleInput() / handleTouchAt() to instruct World on whether
     * and how to close the overlay.
     *
     * CLOSE_WINDOW is new (Part 1) but backward-compatible: World.java only
     * checks CLOSE_FREE and CLOSE_WITH_TURN; any other value keeps the overlay open.
     */
    public enum CloseAction {
        /** Overlay stays open. */
        NONE,
        /** Close the topmost popup window; base inventory remains open. */
        CLOSE_WINDOW,
        /** Close the entire inventory overlay without spending a player turn. */
        CLOSE_FREE,
        /** Close the entire inventory overlay and spend one world tick. */
        CLOSE_WITH_TURN
    }

    // -------------------------------------------------------------------------
    // Palette — UAC military CRT amber on steel
    // -------------------------------------------------------------------------
    private static final Color SCRIM_COLOR   = new Color(0f, 0f, 0f, ItemConstants.INV_SCRIM_ALPHA);
    private static final Color PANEL_BG      = new Color(0.071f, 0.071f, 0.086f, 0.95f);
    private static final Color PANEL_BORDER  = new Color(0.22f,  0.22f,  0.30f,  1f);
    private static final Color HEADER_BG     = new Color(0.06f,  0.06f,  0.08f,  1f);
    private static final Color HEADER_ACCENT = new Color(1.00f,  0.72f,  0.00f,  1f);
    private static final Color AMBER         = new Color(1.00f,  0.75f,  0.10f,  1f);
    private static final Color EXIT_BTN_BG   = new Color(0.70f,  0.20f,  0.15f,  1f);
    private static final Color EXIT_BTN_TEXT = new Color(1.00f,  0.85f,  0.85f,  1f);
    private static final Color TEXT_DIM      = new Color(0.50f,  0.50f,  0.55f,  1f);

    // -------------------------------------------------------------------------
    // Header EXIT button position — derived from ItemConstants, computed once
    // -------------------------------------------------------------------------
    private static final float HEADER_EXIT_X = Constants.WORLD_WIDTH
            - ItemConstants.INV_HEADER_EXIT_MARGIN - ItemConstants.INV_HEADER_EXIT_BUTTON_SIZE;
    private static final float HEADER_EXIT_Y = ItemConstants.INV_HEADER_Y
            + (ItemConstants.INV_HEADER_HEIGHT - ItemConstants.INV_HEADER_EXIT_BUTTON_SIZE) / 2f;

    // -------------------------------------------------------------------------
    // Owned resources — disposed in dispose()
    // -------------------------------------------------------------------------
    private final ShapeRenderer shapeRenderer;
    private final SpriteBatch   spriteBatch;
    private final BitmapFont    font;
    private final GlyphLayout   glyphLayout;
    private final StringBuilder textBuilder;

    // -------------------------------------------------------------------------
    // Sub-panels and popup windows
    // -------------------------------------------------------------------------
    private final WeaponSlotsPanel weaponSlotsPanel;
    private final ItemGridPanel    itemGridPanel;
    private final ItemWindow       itemWindow;
    private final AbilityWindow    abilityWindow;

    // -------------------------------------------------------------------------
    // Runtime state
    // -------------------------------------------------------------------------
    private final Inventory inventory;
    private float           animationClock      = 0f;
    private float           facilityTimeSeconds = 0f;
    private int             currentDepth        = 1;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public InventoryOverlayRenderer(Inventory inventory) {
        this.inventory    = inventory;
        shapeRenderer     = new ShapeRenderer();
        spriteBatch       = new SpriteBatch();
        font              = new BitmapFont();
        glyphLayout       = new GlyphLayout();
        textBuilder       = new StringBuilder(32);

        weaponSlotsPanel  = new WeaponSlotsPanel(inventory, shapeRenderer, spriteBatch,
                                                  font, glyphLayout, this::openItemWindowForSlot);
        itemGridPanel     = new ItemGridPanel(inventory, shapeRenderer, spriteBatch,
                                              font, glyphLayout, this::openItemWindowForSlot);
        itemWindow        = new ItemWindow(shapeRenderer, spriteBatch, font, glyphLayout);
        abilityWindow     = new AbilityWindow();
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Reset cursor and popup state when the overlay opens. */
    public void onOpen() {
        weaponSlotsPanel.clearSelection();
        itemGridPanel.clearSelection();
        if (itemWindow.isOpen())    itemWindow.close();
        if (abilityWindow.isOpen()) abilityWindow.close();
    }

    /** Provides the player's loadout and melee slot so WeaponSlotsPanel can read them. */
    public void setPlayerInventory(PlayerInventory playerInventory) {
        weaponSlotsPanel.setPlayerInventory(playerInventory);
    }

    /** Provides weapon textures so WeaponSlotsPanel can render thumbnails inside slots. */
    public void setWeaponHudRenderer(WeaponHudRenderer weaponHudRenderer) {
        weaponSlotsPanel.setWeaponHudRenderer(weaponHudRenderer);
    }

    /** Pass the facility clock so selection borders can pulse. */
    public void setTime(float time) {
        this.facilityTimeSeconds = time;
        this.animationClock      = time;
    }

    /** Pass the current floor depth for the "SUBLEVEL N" header label. */
    public void setCurrentDepth(int depth) {
        this.currentDepth = depth;
    }

    // -------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------

    /** Advances the flash timer. All actual input on mobile goes through handleTouchAt(). */
    public CloseAction handleInput(float deltaTime) {
        itemWindow.updateFlash(deltaTime);
        return CloseAction.NONE;
    }

    /**
     * Routes a touch tap through the layer stack, topmost layer first.
     *
     * Layer 3 (AbilityWindow) → Layer 2 (ItemWindow) → Layer 1 (header EXIT, sub-panels).
     * Tapping outside an open popup closes it without closing the inventory.
     */
    public CloseAction handleTouchAt(float worldX, float worldY) {
        // Layer 3 — AbilityWindow (topmost)
        if (abilityWindow.isOpen()) {
            if (abilityWindow.containsPoint(worldX, worldY)) {
                abilityWindow.handleTouch(worldX, worldY);
            } else {
                abilityWindow.close();
            }
            return CloseAction.NONE;
        }

        // Layer 2 — ItemWindow
        if (itemWindow.isOpen()) {
            if (itemWindow.containsPoint(worldX, worldY)) {
                CloseAction windowAction = itemWindow.handleTouch(worldX, worldY);
                if (windowAction == CloseAction.CLOSE_WINDOW) {
                    itemWindow.close();
                    return CloseAction.NONE;
                }
                if (windowAction == CloseAction.CLOSE_FREE
                        || windowAction == CloseAction.CLOSE_WITH_TURN) {
                    itemWindow.close();
                    return windowAction;
                }
            } else {
                itemWindow.close();
            }
            return CloseAction.NONE;
        }

        // Layer 1 — Header EXIT button
        if (worldX >= HEADER_EXIT_X
                && worldX <= HEADER_EXIT_X + ItemConstants.INV_HEADER_EXIT_BUTTON_SIZE
                && worldY >= HEADER_EXIT_Y
                && worldY <= HEADER_EXIT_Y + ItemConstants.INV_HEADER_EXIT_BUTTON_SIZE) {
            return CloseAction.CLOSE_FREE;
        }

        // Layer 1 — Sub-panels
        if (weaponSlotsPanel.handleTouch(worldX, worldY)) return CloseAction.NONE;
        if (itemGridPanel.handleTouch(worldX, worldY))    return CloseAction.NONE;

        return CloseAction.NONE;
    }

    // -------------------------------------------------------------------------
    // Render
    // -------------------------------------------------------------------------

    @Override
    public void render(OrthographicCamera camera) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        animationClock = facilityTimeSeconds;

        // Layer 0 + Layer 1 base
        renderBaseLayer(camera);
        weaponSlotsPanel.render(camera, animationClock);
        itemGridPanel.render(camera, animationClock);
        renderHeader(camera);

        // Layer 2 — dim base then draw ItemWindow
        if (itemWindow.isOpen()) {
            renderDimOverlay(camera, ItemConstants.INV_BASE_DIM_FACTOR);
            itemWindow.render(camera, animationClock);
        }

        // Layer 3 — dim ItemWindow then draw AbilityWindow
        if (abilityWindow.isOpen()) {
            renderDimOverlay(camera, ItemConstants.INV_ITEM_WIN_DIM_FACTOR);
            abilityWindow.render(camera, animationClock);
        }
    }

    // -------------------------------------------------------------------------
    // Dispose
    // -------------------------------------------------------------------------

    @Override
    public void dispose() {
        shapeRenderer.dispose();
        spriteBatch.dispose();
        font.dispose();
        // Sub-panels and windows share resources from coordinator; dispose() is a no-op for them
        weaponSlotsPanel.dispose();
        itemGridPanel.dispose();
        itemWindow.dispose();
        abilityWindow.dispose();
    }

    // -------------------------------------------------------------------------
    // Private — render helpers
    // -------------------------------------------------------------------------

    private void renderBaseLayer(OrthographicCamera camera) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Full-screen scrim
        shapeRenderer.setColor(SCRIM_COLOR);
        shapeRenderer.rect(0, 0, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);

        // Left and right content panels
        shapeRenderer.setColor(PANEL_BG);
        shapeRenderer.rect(ItemConstants.INV_LEFT_PANEL_X,  ItemConstants.INV_PANEL_Y_BOTTOM,
                           ItemConstants.INV_LEFT_PANEL_WIDTH,  ItemConstants.INV_PANEL_HEIGHT);
        shapeRenderer.rect(ItemConstants.INV_RIGHT_PANEL_X, ItemConstants.INV_PANEL_Y_BOTTOM,
                           ItemConstants.INV_RIGHT_PANEL_WIDTH, ItemConstants.INV_PANEL_HEIGHT);

        // Header bar
        shapeRenderer.setColor(HEADER_BG);
        shapeRenderer.rect(0, ItemConstants.INV_HEADER_Y,
                           Constants.WORLD_WIDTH, ItemConstants.INV_HEADER_HEIGHT);

        // Header EXIT button background
        shapeRenderer.setColor(EXIT_BTN_BG);
        shapeRenderer.rect(HEADER_EXIT_X, HEADER_EXIT_Y,
                           ItemConstants.INV_HEADER_EXIT_BUTTON_SIZE,
                           ItemConstants.INV_HEADER_EXIT_BUTTON_SIZE);

        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        // Panel borders
        shapeRenderer.setColor(PANEL_BORDER);
        shapeRenderer.rect(ItemConstants.INV_LEFT_PANEL_X,  ItemConstants.INV_PANEL_Y_BOTTOM,
                           ItemConstants.INV_LEFT_PANEL_WIDTH,  ItemConstants.INV_PANEL_HEIGHT);
        shapeRenderer.rect(ItemConstants.INV_RIGHT_PANEL_X, ItemConstants.INV_PANEL_Y_BOTTOM,
                           ItemConstants.INV_RIGHT_PANEL_WIDTH, ItemConstants.INV_PANEL_HEIGHT);

        // Amber accent line at bottom of header
        shapeRenderer.setColor(HEADER_ACCENT);
        shapeRenderer.line(0, ItemConstants.INV_HEADER_Y,
                           Constants.WORLD_WIDTH, ItemConstants.INV_HEADER_Y);

        shapeRenderer.end();
    }

    private void renderHeader(OrthographicCamera camera) {
        spriteBatch.setProjectionMatrix(camera.combined);
        spriteBatch.begin();

        // "INVENTORY" title — left-aligned in header
        font.setColor(AMBER);
        font.draw(spriteBatch, "INVENTORY",
                  ItemConstants.INV_HEADER_TITLE_X,
                  ItemConstants.INV_HEADER_Y + ItemConstants.INV_HEADER_HEIGHT - 18f);

        // "SUBLEVEL N" label — just left of the EXIT button
        textBuilder.setLength(0);
        textBuilder.append("SUBLEVEL ").append(currentDepth);
        glyphLayout.setText(font, textBuilder);
        font.setColor(TEXT_DIM);
        font.draw(spriteBatch, textBuilder,
                  HEADER_EXIT_X - glyphLayout.width - 16f,
                  ItemConstants.INV_HEADER_Y + ItemConstants.INV_HEADER_HEIGHT - 18f);

        // EXIT button label — centered in the button rect
        font.setColor(EXIT_BTN_TEXT);
        glyphLayout.setText(font, "EXIT");
        font.draw(spriteBatch, "EXIT",
                  HEADER_EXIT_X + (ItemConstants.INV_HEADER_EXIT_BUTTON_SIZE - glyphLayout.width)  / 2f,
                  HEADER_EXIT_Y + (ItemConstants.INV_HEADER_EXIT_BUTTON_SIZE + glyphLayout.height) / 2f);

        spriteBatch.end();
    }

    /**
     * Draws a semi-transparent black rectangle over the entire screen to dim
     * lower layers. Used instead of modifying SpriteBatch colors mid-frame.
     */
    private void renderDimOverlay(OrthographicCamera camera, float dimAlpha) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0f, 0f, 0f, dimAlpha);
        shapeRenderer.rect(0, 0, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);
        shapeRenderer.end();
    }

    // -------------------------------------------------------------------------
    // Private — slot tap callback wired into sub-panels
    // -------------------------------------------------------------------------

    private void openItemWindowForSlot(int slotIndex) {
        if (abilityWindow.isOpen()) abilityWindow.close();
        itemWindow.open(inventory, slotIndex);
    }
}
