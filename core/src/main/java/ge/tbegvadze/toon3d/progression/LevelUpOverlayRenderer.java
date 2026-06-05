package ge.tbegvadze.toon3d.progression;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameBalance;

/**
 * Renders the full-screen level-up choice overlay.  Drawn on top of the frozen game view
 * when {@link PlayerProgress#hasPendingLevelUp()} is true.
 *
 * <p>Layout (centred in world space 1280×720):</p>
 * <pre>
 *   ┌─────────────────────────────────────────┐
 *   │         *** LEVEL UP! ***               │
 *   │           Now Level N                   │
 *   │                                         │
 *   │  [1]  HP BOOST    +15 Max HP            │
 *   │  [2]  ARMOR BOOST +10 Max Armor         │
 *   │  [3]  DMG BOOST   +5 Flat Damage        │
 *   └─────────────────────────────────────────┘
 * </pre>
 * Input is handled in World — this renderer only draws.
 */
public final class LevelUpOverlayRenderer implements Disposable {

    // Panel geometry in world units
    private static final float PANEL_WIDTH    = 560f;
    private static final float PANEL_HEIGHT   = 260f;
    private static final float PANEL_X        = (Constants.WORLD_WIDTH  - PANEL_WIDTH)  / 2f;
    private static final float PANEL_Y        = (Constants.WORLD_HEIGHT - PANEL_HEIGHT) / 2f;
    private static final float CORNER_INSET   = 6f;

    // Row heights inside the panel
    private static final float TITLE_ROW_Y    = PANEL_Y + PANEL_HEIGHT - 52f;
    private static final float SUBTITLE_ROW_Y = PANEL_Y + PANEL_HEIGHT - 80f;
    private static final float CHOICE_1_ROW_Y = PANEL_Y + PANEL_HEIGHT - 120f;
    private static final float CHOICE_2_ROW_Y = PANEL_Y + PANEL_HEIGHT - 158f;
    private static final float CHOICE_3_ROW_Y = PANEL_Y + PANEL_HEIGHT - 196f;
    private static final float TEXT_MARGIN    = 36f;

    // Palette
    private static final Color DIM_OVERLAY  = new Color(0.00f, 0.00f, 0.00f, 0.72f);
    private static final Color PANEL_BG     = new Color(0.06f, 0.06f, 0.10f, 0.96f);
    private static final Color PANEL_BORDER = new Color(0.85f, 0.72f, 0.10f, 1.00f);
    private static final Color GOLD         = new Color(1.00f, 0.88f, 0.20f, 1.00f);
    private static final Color WHITE        = new Color(0.92f, 0.92f, 0.92f, 1.00f);
    private static final Color CYAN         = new Color(0.10f, 0.85f, 0.95f, 1.00f);
    private static final Color GREEN        = new Color(0.20f, 1.00f, 0.45f, 1.00f);
    private static final Color RED_WARM     = new Color(1.00f, 0.50f, 0.20f, 1.00f);
    private static final Color DIM_HINT     = new Color(0.55f, 0.55f, 0.55f, 1.00f);

    private final ShapeRenderer shapes;
    private final SpriteBatch   batch;
    private final BitmapFont    font;
    private final PlayerProgress progress;

    private final StringBuilder stringBuilder = new StringBuilder(64);

    public LevelUpOverlayRenderer(PlayerProgress progress) {
        this.progress = progress;
        this.shapes   = new ShapeRenderer();
        this.batch    = new SpriteBatch();
        this.font     = new BitmapFont();
        this.font.getData().markupEnabled = false;
    }

    public void render(OrthographicCamera camera) {
        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        // ---- Pass A: dim the game behind the panel ----
        com.badlogic.gdx.Gdx.gl.glEnable(GL20.GL_BLEND);
        com.badlogic.gdx.Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        shapes.setColor(DIM_OVERLAY);
        shapes.rect(0, 0, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);

        shapes.setColor(PANEL_BG);
        shapes.rect(PANEL_X, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT);
        shapes.end();

        // ---- Pass B: border lines ----
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(PANEL_BORDER);
        float innerX = PANEL_X + CORNER_INSET;
        float innerY = PANEL_Y + CORNER_INSET;
        float innerW = PANEL_WIDTH  - CORNER_INSET * 2f;
        float innerH = PANEL_HEIGHT - CORNER_INSET * 2f;
        shapes.rect(PANEL_X, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT);
        shapes.rect(innerX,  innerY,  innerW,       innerH);
        shapes.end();
        com.badlogic.gdx.Gdx.gl.glDisable(GL20.GL_BLEND);

        // ---- Pass C: text ----
        batch.begin();

        // Title
        font.getData().setScale(1.6f);
        font.setColor(GOLD);
        String title = "*** LEVEL UP! ***";
        font.draw(batch, title, PANEL_X + TEXT_MARGIN, TITLE_ROW_Y);

        // Subtitle
        font.getData().setScale(1.0f);
        font.setColor(WHITE);
        stringBuilder.setLength(0);
        stringBuilder.append("Now Level ").append(progress.getPlayerLevel() + 1);
        font.draw(batch, stringBuilder, PANEL_X + TEXT_MARGIN, SUBTITLE_ROW_Y);

        // Choice rows
        drawChoiceRow(CHOICE_1_ROW_Y, "1", "HP BOOST",
                "+" + GameBalance.LEVEL_UP_HP_BONUS + " Max HP", GREEN);
        drawChoiceRow(CHOICE_2_ROW_Y, "2", "ARMOR BOOST",
                "+" + GameBalance.LEVEL_UP_ARMOR_BONUS + " Max Armor", CYAN);
        drawChoiceRow(CHOICE_3_ROW_Y, "3", "DMG BOOST",
                "+" + GameBalance.LEVEL_UP_DAMAGE_BONUS + " Flat Damage per Shot", RED_WARM);

        // Hint
        font.getData().setScale(0.75f);
        font.setColor(DIM_HINT);
        font.draw(batch, "Press 1, 2 or 3 to choose", PANEL_X + TEXT_MARGIN,
                PANEL_Y + 28f);

        batch.end();
    }

    private void drawChoiceRow(float rowY, String key, String name, String description, Color nameColor) {
        float keyX    = PANEL_X + TEXT_MARGIN;
        float nameX   = keyX + 60f;
        float descX   = keyX + 210f;

        font.getData().setScale(1.1f);
        font.setColor(GOLD);
        font.draw(batch, "[" + key + "]", keyX, rowY);

        font.setColor(nameColor);
        font.draw(batch, name, nameX, rowY);

        font.getData().setScale(0.85f);
        font.setColor(WHITE);
        font.draw(batch, description, descX, rowY);
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}
