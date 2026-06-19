package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.entity.WeaponAbility;
import ge.tbegvadze.toon3d.util.ItemConstants;

/**
 * Layer-3 popup showing the full description of a single weapon ability.
 *
 * Opens when the player taps an ability row in ItemWindow (Part 4).
 * Uses a blue-white accent to visually distinguish itself from the amber
 * ItemWindow at Layer 2 — signals "deeper level of detail."
 *
 * Three render passes: Filled shapes → Line shapes → SpriteBatch text.
 * No allocations inside render() — all scratch objects pre-allocated.
 *
 * Package-private — owned and driven by InventoryOverlayRenderer coordinator.
 */
final class AbilityWindow implements Disposable {

    // -------------------------------------------------------------------------
    // Geometry — derived from ItemConstants
    // -------------------------------------------------------------------------

    private static final float WIN_X      = ItemConstants.INV_ABILITY_WIN_X;
    private static final float WIN_Y      = ItemConstants.INV_ABILITY_WIN_Y;
    private static final float WIN_W      = ItemConstants.INV_ABILITY_WIN_WIDTH;
    private static final float WIN_H      = ItemConstants.INV_ABILITY_WIN_HEIGHT;
    private static final float WIN_TOP    = WIN_Y + WIN_H;
    private static final float HEADER_H   = ItemConstants.INV_ABILITY_WIN_HEADER_H;
    private static final float HEADER_Y   = WIN_TOP - HEADER_H;
    private static final float PAD        = ItemConstants.INV_ABILITY_WIN_PADDING;
    private static final float EXIT_SIZE  = ItemConstants.INV_ABILITY_WIN_EXIT_BTN_SIZE;
    private static final float EXIT_X     = WIN_X + WIN_W - PAD - EXIT_SIZE;
    private static final float EXIT_Y     = HEADER_Y + (HEADER_H - EXIT_SIZE) / 2f;
    private static final float BADGE_W    = ItemConstants.INV_ABILITY_BADGE_W;
    private static final float BADGE_H    = ItemConstants.INV_ABILITY_BADGE_H;
    private static final float BADGE_X    = EXIT_X - BADGE_W - 8f;
    private static final float BADGE_Y    = HEADER_Y + (HEADER_H - BADGE_H) / 2f;
    private static final float DIVIDER_Y  = ItemConstants.INV_ABILITY_WIN_DIVIDER_Y;

    // -------------------------------------------------------------------------
    // Palette — blue-white accent distinguishes from amber ItemWindow
    // -------------------------------------------------------------------------

    private static final Color WIN_BG            = new Color(0.06f, 0.07f, 0.10f, 0.98f);
    private static final Color BORDER_BLUE       = new Color(0.60f, 0.72f, 1.00f, 0.85f);
    private static final Color AMBER             = new Color(1.00f, 0.75f, 0.10f, 1.00f);
    private static final Color BADGE_PASSIVE_BG  = new Color(0.10f, 0.30f, 0.10f, 0.85f);
    private static final Color BADGE_PASSIVE_FG  = new Color(0.20f, 0.90f, 0.30f, 1.00f);
    private static final Color BADGE_ACTIVE_BG   = new Color(0.30f, 0.20f, 0.05f, 0.85f);
    private static final Color BADGE_ACTIVE_FG   = new Color(1.00f, 0.72f, 0.00f, 1.00f);
    private static final Color EXIT_BG           = new Color(0.70f, 0.20f, 0.15f, 1.00f);
    private static final Color EXIT_TEXT         = new Color(1.00f, 0.85f, 0.85f, 1.00f);
    private static final Color OFF_WHITE         = new Color(0.82f, 0.82f, 0.87f, 1.00f);
    private static final Color TEXT_DIM_AMBER    = new Color(0.75f, 0.55f, 0.10f, 1.00f);
    private static final Color DIVIDER_DIM       = new Color(0.30f, 0.32f, 0.40f, 1.00f);

    // -------------------------------------------------------------------------
    // Shared rendering resources (owned by coordinator, not disposed here)
    // -------------------------------------------------------------------------

    private final ShapeRenderer shapeRenderer;
    private final SpriteBatch   spriteBatch;
    private final BitmapFont    font;
    private final GlyphLayout   glyphLayout;
    private final StringBuilder textBuilder;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private WeaponAbility displayedAbility = null;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    AbilityWindow(ShapeRenderer shapeRenderer, SpriteBatch spriteBatch,
                  BitmapFont font, GlyphLayout glyphLayout) {
        this.shapeRenderer = shapeRenderer;
        this.spriteBatch   = spriteBatch;
        this.font          = font;
        this.glyphLayout   = glyphLayout;
        this.textBuilder   = new StringBuilder(16);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    void open(WeaponAbility ability) {
        this.displayedAbility = ability;
    }

    boolean isOpen() {
        return displayedAbility != null;
    }

    void close() {
        displayedAbility = null;
    }

    // -------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------

    boolean containsPoint(float worldX, float worldY) {
        return worldX >= WIN_X && worldX <= WIN_X + WIN_W
                && worldY >= WIN_Y && worldY <= WIN_Y + WIN_H;
    }

    /** Returns true if the touch was consumed. Closes itself when exit button is tapped. */
    boolean handleTouch(float worldX, float worldY) {
        if (worldX >= EXIT_X && worldX <= EXIT_X + EXIT_SIZE
                && worldY >= EXIT_Y && worldY <= EXIT_Y + EXIT_SIZE) {
            close();
            return true;
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Render
    // -------------------------------------------------------------------------

    void render(OrthographicCamera camera, float animationClock) {
        if (displayedAbility == null) return;
        renderFilled(camera);
        renderLines(camera);
        renderText(camera);
    }

    // -------------------------------------------------------------------------
    // Dispose — rendering resources owned by coordinator
    // -------------------------------------------------------------------------

    @Override
    public void dispose() {}

    // =========================================================================
    // Private — render passes
    // =========================================================================

    private void renderFilled(OrthographicCamera camera) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        shapeRenderer.setColor(WIN_BG);
        shapeRenderer.rect(WIN_X, WIN_Y, WIN_W, WIN_H);

        boolean isPassive = displayedAbility.trigger == WeaponAbility.Trigger.PASSIVE;
        shapeRenderer.setColor(isPassive ? BADGE_PASSIVE_BG : BADGE_ACTIVE_BG);
        shapeRenderer.rect(BADGE_X, BADGE_Y, BADGE_W, BADGE_H);

        shapeRenderer.setColor(EXIT_BG);
        shapeRenderer.rect(EXIT_X, EXIT_Y, EXIT_SIZE, EXIT_SIZE);

        shapeRenderer.end();
    }

    private void renderLines(OrthographicCamera camera) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        shapeRenderer.setColor(BORDER_BLUE);
        shapeRenderer.rect(WIN_X, WIN_Y, WIN_W, WIN_H);
        shapeRenderer.line(WIN_X + PAD, HEADER_Y, WIN_X + WIN_W - PAD, HEADER_Y);

        String[] affects = displayedAbility.affectsLines;
        if (affects != null && affects.length > 0) {
            shapeRenderer.setColor(DIVIDER_DIM);
            shapeRenderer.line(WIN_X + PAD, DIVIDER_Y, WIN_X + WIN_W - PAD, DIVIDER_Y);
        }

        shapeRenderer.end();
    }

    private void renderText(OrthographicCamera camera) {
        spriteBatch.setProjectionMatrix(camera.combined);
        spriteBatch.begin();

        // Ability name (amber, left-aligned in header)
        font.setColor(AMBER);
        font.draw(spriteBatch, displayedAbility.displayName,
                  WIN_X + PAD,
                  HEADER_Y + (HEADER_H + font.getLineHeight()) / 2f);

        // Type badge text (PASSIVE / ACTIVE)
        boolean isPassive = displayedAbility.trigger == WeaponAbility.Trigger.PASSIVE;
        font.getData().setScale(0.65f);
        font.setColor(isPassive ? BADGE_PASSIVE_FG : BADGE_ACTIVE_FG);
        glyphLayout.setText(font, displayedAbility.getTypeLabel());
        font.draw(spriteBatch, displayedAbility.getTypeLabel(),
                  BADGE_X + (BADGE_W - glyphLayout.width)  / 2f,
                  BADGE_Y + (BADGE_H + glyphLayout.height) / 2f);
        font.getData().setScale(1f);

        // Exit button "X"
        font.setColor(EXIT_TEXT);
        glyphLayout.setText(font, "X");
        font.draw(spriteBatch, "X",
                  EXIT_X + (EXIT_SIZE - glyphLayout.width)  / 2f,
                  EXIT_Y + (EXIT_SIZE + glyphLayout.height) / 2f);

        // Full description text (word-wrapped body)
        float descX = WIN_X + PAD;
        float descY = HEADER_Y - 10f;
        float descW = WIN_W - 2f * PAD;
        font.getData().setScale(0.80f);
        font.setColor(OFF_WHITE);
        font.draw(spriteBatch, displayedAbility.fullDescription,
                  descX, descY, descW, Align.left, true);
        font.getData().setScale(1f);

        // AFFECTS block
        String[] affects = displayedAbility.affectsLines;
        if (affects != null && affects.length > 0) {
            font.getData().setScale(0.75f);
            font.setColor(TEXT_DIM_AMBER);
            font.draw(spriteBatch, "AFFECTS:", WIN_X + PAD, DIVIDER_Y - 4f);

            font.setColor(OFF_WHITE);
            float bulletY = DIVIDER_Y - 20f;
            for (String line : affects) {
                textBuilder.setLength(0);
                textBuilder.append('●').append(' ').append(line);
                font.draw(spriteBatch, textBuilder, WIN_X + PAD + 8f, bulletY);
                bulletY -= 16f;
            }
            font.getData().setScale(1f);
        }

        spriteBatch.end();
    }
}
