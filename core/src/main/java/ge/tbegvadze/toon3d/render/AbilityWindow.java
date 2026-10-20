package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.entity.AbilityInstance;
import ge.tbegvadze.toon3d.entity.WeaponAbility;
import ge.tbegvadze.toon3d.util.ArtDirection;
import ge.tbegvadze.toon3d.util.ItemConstants;

/**
 * Layer-3 popup showing the full description of a single weapon ability.
 *
 * Opens when the player taps an ability row in ItemWindow (Part 4).
 * Uses a blue-white accent to visually distinguish itself from the amber
 * ItemWindow at Layer 2 — signals "deeper level of detail."
 *
 * Two render passes (visual-identity-order-5): one Filled pass (a UiKit panel with SUIT_AMBER_DIM brackets, the
 * R1 amber-ticked rule under the title, an outline type badge, the X plate) → SpriteBatch text.
 * No allocations inside render() — all scratch objects pre-allocated.
 * Dynamic description strings are built once in open() and cached.
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
    private static final float BADGE_X    = EXIT_X - BADGE_W - ItemConstants.INV_ABILITY_WIN_BADGE_GAP;
    private static final float BADGE_Y    = HEADER_Y + (HEADER_H - BADGE_H) / 2f;
    private static final float DIVIDER_Y  = ItemConstants.INV_ABILITY_WIN_DIVIDER_Y;

    /** The title's top edge, placed so the R1 rule (UiKit.screenHeaderRuleY) lands on the header line. */
    private static final float TITLE_TOP_Y = HEADER_Y + ArtDirection.PIXEL_FONT_TARGET_CAP_HEIGHT * ArtDirection.TYPE_HEADING
            + ArtDirection.UI_SCREEN_HEADER_RULE_GAP;

    // -------------------------------------------------------------------------
    // Shared rendering resources (owned by coordinator, not disposed here)
    // -------------------------------------------------------------------------

    private final ShapeRenderer shapeRenderer;
    private final SpriteBatch   spriteBatch;
    private final BitmapFont    font;
    private final BitmapFont    headingFont;
    private final GlyphLayout   glyphLayout;
    private final StringBuilder textBuilder;

    // -------------------------------------------------------------------------
    // State — set in open(), cleared in close(), read-only during render()
    // -------------------------------------------------------------------------

    private AbilityInstance displayedInstance  = null;
    private String          cachedDescription  = null;
    private String[]        cachedAffects      = null;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    AbilityWindow(ShapeRenderer shapeRenderer, SpriteBatch spriteBatch,
                  BitmapFont font, BitmapFont headingFont, GlyphLayout glyphLayout) {
        this.shapeRenderer = shapeRenderer;
        this.spriteBatch   = spriteBatch;
        this.font          = font;
        this.headingFont   = headingFont;
        this.glyphLayout   = glyphLayout;
        this.textBuilder   = new StringBuilder(16);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    void open(AbilityInstance instance) {
        this.displayedInstance = instance;
        this.cachedDescription = instance.ability.buildDescription(instance);
        this.cachedAffects     = instance.ability.buildAffectsLines(instance);
    }

    boolean isOpen() {
        return displayedInstance != null;
    }

    void close() {
        displayedInstance = null;
        cachedDescription = null;
        cachedAffects     = null;
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
        if (displayedInstance == null) return;
        renderFilled(camera);
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

        UiKit.panel(shapeRenderer, WIN_X, WIN_Y, WIN_W, WIN_H,
                    ArtDirection.UI_PANEL_RED, ArtDirection.UI_PANEL_GREEN, ArtDirection.UI_PANEL_BLUE,
                    ItemConstants.INV_PANEL_ALPHA,
                    ArtDirection.UI_LINE_RED, ArtDirection.UI_LINE_GREEN, ArtDirection.UI_LINE_BLUE);
        UiKit.brackets(shapeRenderer, WIN_X, WIN_Y, WIN_W, WIN_H,
                       ArtDirection.SUIT_AMBER_DIM_RED, ArtDirection.SUIT_AMBER_DIM_GREEN,
                       ArtDirection.SUIT_AMBER_DIM_BLUE, 1f);

        // The header line is the title's R1 rule (amber tick), running up to the type badge
        UiKit.screenHeaderRule(shapeRenderer, WIN_X + PAD, HEADER_Y, BADGE_X - ItemConstants.INV_ABILITY_WIN_BADGE_GAP
                               - WIN_X - PAD, 1f);
        if (cachedAffects != null && cachedAffects.length > 0) {
            UiKit.divider(shapeRenderer, WIN_X + PAD, DIVIDER_Y, WIN_W - PAD * 2f,
                          ArtDirection.UI_LINE_RED, ArtDirection.UI_LINE_GREEN, ArtDirection.UI_LINE_BLUE, 1f);
        }

        // Type badge: an outline chip in the badge's ink (PASSIVE secondary, ACTIVE amber)
        boolean isPassive = displayedInstance.ability.trigger == WeaponAbility.Trigger.PASSIVE;
        UiKit.panel(shapeRenderer, BADGE_X, BADGE_Y, BADGE_W, BADGE_H, ArtDirection.UI_CHIP_CHAMFER,
                    ArtDirection.UI_PANEL_RAISED_RED, ArtDirection.UI_PANEL_RAISED_GREEN,
                    ArtDirection.UI_PANEL_RAISED_BLUE, 1f,
                    isPassive ? ArtDirection.UI_TEXT_SECONDARY_RED : ArtDirection.SUIT_AMBER_RED,
                    isPassive ? ArtDirection.UI_TEXT_SECONDARY_GREEN : ArtDirection.SUIT_AMBER_GREEN,
                    isPassive ? ArtDirection.UI_TEXT_SECONDARY_BLUE : ArtDirection.SUIT_AMBER_BLUE,
                    1f, ArtDirection.UI_CHIP_BORDER_THICKNESS);

        UiKit.plate(shapeRenderer, EXIT_X, EXIT_Y, EXIT_SIZE, EXIT_SIZE, ArtDirection.UI_CHAMFER, PlateState.IDLE,
                    ItemConstants.INV_PANEL_ALPHA,
                    ArtDirection.SUIT_AMBER_RED, ArtDirection.SUIT_AMBER_GREEN, ArtDirection.SUIT_AMBER_BLUE);

        shapeRenderer.end();
    }

    private void renderText(OrthographicCamera camera) {
        spriteBatch.setProjectionMatrix(camera.combined);
        spriteBatch.begin();

        // Ability name — the window's R1 screen header (HEAVY, TYPE_HEADING, primary)
        UiKit.screenHeaderTitle(spriteBatch, headingFont, displayedInstance.ability.displayName,
                                WIN_X + PAD, TITLE_TOP_Y);

        // Type badge text (PASSIVE secondary / ACTIVE amber)
        boolean isPassive = displayedInstance.ability.trigger == WeaponAbility.Trigger.PASSIVE;
        font.getData().setScale(ArtDirection.TYPE_CAPTION);
        if (isPassive) {
            setColor(ArtDirection.UI_TEXT_SECONDARY_RED, ArtDirection.UI_TEXT_SECONDARY_GREEN,
                     ArtDirection.UI_TEXT_SECONDARY_BLUE);
        } else {
            setColor(ArtDirection.SUIT_AMBER_RED, ArtDirection.SUIT_AMBER_GREEN, ArtDirection.SUIT_AMBER_BLUE);
        }
        String typeLabel = displayedInstance.ability.getTypeLabel();
        glyphLayout.setText(font, typeLabel);
        font.draw(spriteBatch, typeLabel,
                  BADGE_X + (BADGE_W - glyphLayout.width)  / 2f,
                  BADGE_Y + (BADGE_H + glyphLayout.height) / 2f);

        // Exit button "X"
        font.getData().setScale(ArtDirection.TYPE_BODY);
        setColor(ArtDirection.UI_TEXT_PRIMARY_RED, ArtDirection.UI_TEXT_PRIMARY_GREEN, ArtDirection.UI_TEXT_PRIMARY_BLUE);
        glyphLayout.setText(font, "X");
        font.draw(spriteBatch, "X",
                  EXIT_X + (EXIT_SIZE - glyphLayout.width)  / 2f,
                  EXIT_Y + (EXIT_SIZE + glyphLayout.height) / 2f);

        // Full description text (body) — pre-built with exact numbers in open()
        float descX = WIN_X + PAD;
        float descY = HEADER_Y - ItemConstants.INV_ABILITY_WIN_DESC_DROP;
        float descW = WIN_W - 2f * PAD;
        font.getData().setScale(ArtDirection.TYPE_BODY);
        setColor(ArtDirection.UI_TEXT_PRIMARY_RED, ArtDirection.UI_TEXT_PRIMARY_GREEN, ArtDirection.UI_TEXT_PRIMARY_BLUE);
        font.draw(spriteBatch, cachedDescription, descX, descY, descW, Align.left, true);

        // AFFECTS block — pre-built with exact numbers in open(). The fixed 16-unit bullet pitch under the
        // divider holds TYPE_CAPTION lines, so the label and bullets stay captions.
        if (cachedAffects != null && cachedAffects.length > 0) {
            font.getData().setScale(ArtDirection.TYPE_CAPTION);
            setColor(ArtDirection.UI_TEXT_SECONDARY_RED, ArtDirection.UI_TEXT_SECONDARY_GREEN,
                     ArtDirection.UI_TEXT_SECONDARY_BLUE);
            font.draw(spriteBatch, "AFFECTS:", WIN_X + PAD, DIVIDER_Y - ItemConstants.INV_ABILITY_WIN_AFFECTS_DROP);

            setColor(ArtDirection.UI_TEXT_PRIMARY_RED, ArtDirection.UI_TEXT_PRIMARY_GREEN, ArtDirection.UI_TEXT_PRIMARY_BLUE);
            float bulletY = DIVIDER_Y - ItemConstants.INV_ABILITY_WIN_BULLET_DROP;
            for (String line : cachedAffects) {
                textBuilder.setLength(0);
                textBuilder.append('●').append(' ').append(line);
                font.draw(spriteBatch, textBuilder, WIN_X + PAD + ItemConstants.INV_ABILITY_WIN_BULLET_INDENT, bulletY);
                bulletY -= ItemConstants.INV_ABILITY_WIN_BULLET_PITCH;
            }
        }
        font.getData().setScale(ArtDirection.TYPE_CAPTION);

        spriteBatch.end();
    }

    private void setColor(float red, float green, float blue) {
        font.setColor(red, green, blue, 1f);
    }
}
