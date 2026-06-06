package ge.tbegvadze.toon3d.progression;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameBalance;

/**
 * Renders the full-screen level-up card overlay for mobile.
 *
 * <p>Three large tappable cards laid side-by-side in landscape orientation.
 * Tap detection is via {@link #getTappedReward(float, float)} which tests world-space
 * coordinates — call it from World.update() when {@code Gdx.input.justTouched()}.
 * Keyboard shortcuts 1/2/3 remain available for desktop testing.
 *
 * <pre>
 *              *** LEVEL UP! ***         Reached Level N
 *   +-----------+   +-----------+   +-----------+
 *   |  +15      |   |  +10      |   |  +5       |
 *   |  MAX HP   |   |  ARMOR    |   |  DAMAGE   |
 *   |           |   |           |   |           |
 *   | HP BOOST  |   |ARMOR BOOST|   | DMG BOOST |
 *   |   [TAP]   |   |   [TAP]   |   |   [TAP]   |
 *   +-----------+   +-----------+   +-----------+
 * </pre>
 */
public final class LevelUpOverlayRenderer implements Disposable {

    // ---- Card geometry (world units) ----------------------------------------
    private static final float CARD_WIDTH   = 360f;
    private static final float CARD_HEIGHT  = 300f;
    private static final float CARD_GAP     = 20f;
    private static final float CARD_Y       = (Constants.WORLD_HEIGHT - CARD_HEIGHT) / 2f - 30f;

    private static final float TOTAL_CARDS_WIDTH = 3f * CARD_WIDTH + 2f * CARD_GAP;
    private static final float CARDS_LEFT_X      = (Constants.WORLD_WIDTH - TOTAL_CARDS_WIDTH) / 2f;

    private static final float CARD_1_X = CARDS_LEFT_X;
    private static final float CARD_2_X = CARDS_LEFT_X + CARD_WIDTH + CARD_GAP;
    private static final float CARD_3_X = CARDS_LEFT_X + 2f * (CARD_WIDTH + CARD_GAP);

    // ---- Internal card layout proportions (offsets from card bottom) ---------
    private static final float CARD_CORNER_RADIUS  = 8f;   // visual only — drawn as filled rect + border
    private static final float CARD_BORDER_THICK   = 4f;
    private static final float ACCENT_STRIP_HEIGHT = 60f;  // colored top accent bar

    // Text Y offsets from card bottom
    private static final float TEXT_VALUE_Y    = CARD_HEIGHT - ACCENT_STRIP_HEIGHT - 30f;  // big value
    private static final float TEXT_NAME_Y     = CARD_HEIGHT - ACCENT_STRIP_HEIGHT - 90f;  // type name
    private static final float TEXT_DESC_Y     = CARD_HEIGHT - ACCENT_STRIP_HEIGHT - 140f; // description
    private static final float TEXT_TAP_Y      = 42f;  // "TAP TO CHOOSE" hint near bottom

    // Title / subtitle above the cards
    private static final float TITLE_Y    = CARD_Y + CARD_HEIGHT + 70f;
    private static final float SUBTITLE_Y = CARD_Y + CARD_HEIGHT + 32f;

    // ---- Palette -----------------------------------------------------------
    private static final Color DIM_OVERLAY      = new Color(0.00f, 0.00f, 0.00f, 0.78f);

    // HP card — green
    private static final Color HP_ACCENT        = new Color(0.20f, 1.00f, 0.45f, 1.00f);
    private static final Color HP_BG            = new Color(0.04f, 0.12f, 0.05f, 0.97f);
    private static final Color HP_ACCENT_STRIP  = new Color(0.15f, 0.80f, 0.35f, 1.00f);

    // Armor card — cyan
    private static final Color AR_ACCENT        = new Color(0.10f, 0.85f, 0.95f, 1.00f);
    private static final Color AR_BG            = new Color(0.04f, 0.10f, 0.14f, 0.97f);
    private static final Color AR_ACCENT_STRIP  = new Color(0.08f, 0.70f, 0.85f, 1.00f);

    // Damage card — red-orange
    private static final Color DMG_ACCENT       = new Color(1.00f, 0.50f, 0.20f, 1.00f);
    private static final Color DMG_BG           = new Color(0.14f, 0.05f, 0.03f, 0.97f);
    private static final Color DMG_ACCENT_STRIP = new Color(0.85f, 0.38f, 0.10f, 1.00f);

    private static final Color WHITE            = new Color(0.95f, 0.95f, 0.95f, 1.00f);
    private static final Color GOLD             = new Color(1.00f, 0.88f, 0.20f, 1.00f);
    private static final Color DIM_HINT         = new Color(0.55f, 0.55f, 0.55f, 1.00f);
    private static final Color CARD_SHADOW      = new Color(0.00f, 0.00f, 0.00f, 0.45f);

    private final ShapeRenderer shapes;
    private final SpriteBatch   batch;
    private final BitmapFont    font;
    private final GlyphLayout   glyphLayout;
    private final PlayerProgress progress;

    private final StringBuilder stringBuilder = new StringBuilder(64);

    public LevelUpOverlayRenderer(PlayerProgress progress) {
        this.progress    = progress;
        this.shapes      = new ShapeRenderer();
        this.batch       = new SpriteBatch();
        this.font        = new BitmapFont();
        this.glyphLayout = new GlyphLayout();
        this.font.getData().markupEnabled = false;
    }

    // -------------------------------------------------------------------------
    // Touch detection — called from World.update()
    // -------------------------------------------------------------------------

    /**
     * Returns the reward matching the tapped card, or {@code null} if no card was hit.
     * Coordinates must be in world space (viewport-unprojected).
     */
    public LevelUpReward getTappedReward(float worldX, float worldY) {
        if (worldY < CARD_Y || worldY > CARD_Y + CARD_HEIGHT) return null;
        if (worldX >= CARD_1_X && worldX < CARD_1_X + CARD_WIDTH) return LevelUpReward.HP_BOOST;
        if (worldX >= CARD_2_X && worldX < CARD_2_X + CARD_WIDTH) return LevelUpReward.ARMOR_BOOST;
        if (worldX >= CARD_3_X && worldX < CARD_3_X + CARD_WIDTH) return LevelUpReward.DAMAGE_BOOST;
        return null;
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    public void render(OrthographicCamera camera) {
        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        com.badlogic.gdx.Gdx.gl.glEnable(GL20.GL_BLEND);
        com.badlogic.gdx.Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // ---- Pass A: backgrounds ----
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // Full-screen dim
        shapes.setColor(DIM_OVERLAY);
        shapes.rect(0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);

        // Card drop shadows (slight offset)
        shapes.setColor(CARD_SHADOW);
        shapes.rect(CARD_1_X + 6f, CARD_Y - 6f, CARD_WIDTH, CARD_HEIGHT);
        shapes.rect(CARD_2_X + 6f, CARD_Y - 6f, CARD_WIDTH, CARD_HEIGHT);
        shapes.rect(CARD_3_X + 6f, CARD_Y - 6f, CARD_WIDTH, CARD_HEIGHT);

        // Card backgrounds
        drawCardBackground(HP_BG, HP_ACCENT_STRIP, CARD_1_X);
        drawCardBackground(AR_BG, AR_ACCENT_STRIP, CARD_2_X);
        drawCardBackground(DMG_BG, DMG_ACCENT_STRIP, CARD_3_X);

        shapes.end();

        // ---- Pass B: borders ----
        shapes.begin(ShapeRenderer.ShapeType.Line);
        com.badlogic.gdx.Gdx.gl.glLineWidth(CARD_BORDER_THICK);

        shapes.setColor(HP_ACCENT);
        shapes.rect(CARD_1_X, CARD_Y, CARD_WIDTH, CARD_HEIGHT);

        shapes.setColor(AR_ACCENT);
        shapes.rect(CARD_2_X, CARD_Y, CARD_WIDTH, CARD_HEIGHT);

        shapes.setColor(DMG_ACCENT);
        shapes.rect(CARD_3_X, CARD_Y, CARD_WIDTH, CARD_HEIGHT);

        com.badlogic.gdx.Gdx.gl.glLineWidth(1f);
        shapes.end();

        com.badlogic.gdx.Gdx.gl.glDisable(GL20.GL_BLEND);

        // ---- Pass C: text ----
        batch.begin();

        // Title
        font.getData().setScale(2.2f);
        font.setColor(GOLD);
        drawCenteredText("*** LEVEL UP! ***", Constants.WORLD_WIDTH / 2f, TITLE_Y);

        // Subtitle
        font.getData().setScale(1.3f);
        font.setColor(WHITE);
        stringBuilder.setLength(0);
        stringBuilder.append("Reached Level ").append(progress.getPlayerLevel() + 1);
        drawCenteredText(stringBuilder.toString(), Constants.WORLD_WIDTH / 2f, SUBTITLE_Y);

        // HP card text
        drawCardText(CARD_1_X, "+" + GameBalance.LEVEL_UP_HP_BONUS, "MAX HP",
                     "HP BOOST", "Gain " + GameBalance.LEVEL_UP_HP_BONUS + " Max HP", HP_ACCENT);

        // Armor card text
        drawCardText(CARD_2_X, "+" + GameBalance.LEVEL_UP_ARMOR_BONUS, "ARMOR",
                     "ARMOR BOOST", "Gain " + GameBalance.LEVEL_UP_ARMOR_BONUS + " Max Armor", AR_ACCENT);

        // Damage card text
        drawCardText(CARD_3_X, "+" + GameBalance.LEVEL_UP_DAMAGE_BONUS, "DAMAGE",
                     "DMG BOOST", "+" + GameBalance.LEVEL_UP_DAMAGE_BONUS + " flat per shot", DMG_ACCENT);

        batch.end();
    }

    private void drawCardBackground(Color bgColor, Color accentColor, float cardX) {
        // Dark body
        shapes.setColor(bgColor);
        shapes.rect(cardX, CARD_Y, CARD_WIDTH, CARD_HEIGHT);
        // Color accent strip at top
        shapes.setColor(accentColor);
        shapes.rect(cardX, CARD_Y + CARD_HEIGHT - ACCENT_STRIP_HEIGHT, CARD_WIDTH, ACCENT_STRIP_HEIGHT);
    }

    private void drawCardText(float cardX, String valueTop, String valueBottom,
                              String name, String description, Color accentColor) {
        float cardCenterX = cardX + CARD_WIDTH / 2f;

        // Large value in accent color — top number (e.g. "+15")
        font.getData().setScale(3.2f);
        font.setColor(accentColor);
        drawCenteredText(valueTop, cardCenterX, CARD_Y + TEXT_VALUE_Y);

        // Value label below (e.g. "MAX HP") — slightly smaller, white
        font.getData().setScale(1.6f);
        font.setColor(WHITE);
        drawCenteredText(valueBottom, cardCenterX, CARD_Y + TEXT_VALUE_Y - 54f);

        // Divider visual line gap handled by spacing

        // Card type name (bold feel via slightly larger scale)
        font.getData().setScale(1.8f);
        font.setColor(accentColor);
        drawCenteredText(name, cardCenterX, CARD_Y + TEXT_NAME_Y);

        // Description — smaller, white
        font.getData().setScale(1.1f);
        font.setColor(WHITE);
        drawCenteredText(description, cardCenterX, CARD_Y + TEXT_DESC_Y);

        // Tap hint — small, dimmed
        font.getData().setScale(1.0f);
        font.setColor(DIM_HINT);
        drawCenteredText("TAP TO CHOOSE", cardCenterX, CARD_Y + TEXT_TAP_Y);
    }

    private void drawCenteredText(String text, float centerX, float baselineY) {
        glyphLayout.setText(font, text);
        float textX = centerX - glyphLayout.width / 2f;
        font.draw(batch, text, textX, baselineY);
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}
