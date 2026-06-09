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
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.world.PersistentStats;
import ge.tbegvadze.toon3d.world.RunStats;

/**
 * Full-screen death report overlay rendered when the player's HP reaches zero.
 *
 * <p>Call {@link #show(RunStats, PersistentStats)} once when entering the DEAD phase to
 * snapshot stats and pre-compute all glyph layout positions. Then call
 * {@link #render(OrthographicCamera, boolean)} every frame.
 *
 * <p>Zero per-frame allocations: all string widths are measured in {@code show()} via the
 * single pre-allocated {@code GlyphLayout}. {@code render()} performs no layout computation.
 */
public final class DeathOverlayRenderer implements Disposable {

    private static final float PANEL_TOP    = Constants.DEATH_OVERLAY_PANEL_Y + Constants.DEATH_OVERLAY_PANEL_HEIGHT;
    private static final float CENTER_X     = Constants.WORLD_WIDTH / 2f;

    // ---- Palette -----------------------------------------------------------
    private static final Color BACKGROUND   = new Color(0f, 0f, 0f, 0.92f);
    private static final Color PANEL_BG     = new Color(0.06f, 0.03f, 0.03f, 0.97f);
    private static final Color PANEL_BORDER = new Color(0.60f, 0.05f, 0.05f, 1f);
    private static final Color HEADER_COLOR = new Color(1f, 0.65f, 0f, 1f);
    private static final Color SUBHEAD      = new Color(0.80f, 0.15f, 0.15f, 1f);
    private static final Color LABEL_COLOR  = new Color(0.70f, 0.70f, 0.70f, 1f);
    private static final Color VALUE_COLOR  = new Color(0.95f, 0.95f, 0.95f, 1f);
    private static final Color NEW_BEST     = new Color(1f, 0.88f, 0.20f, 1f);
    private static final Color FLAVOR_COLOR = new Color(0.50f, 0.50f, 0.50f, 1f);
    private static final Color PROMPT_COLOR = new Color(0.90f, 0.90f, 0.90f, 1f);

    // ---- Display text strings ----------------------------------------------
    private static final String HEADER_TEXT   = "INCURSION TERMINATED";
    private static final String SUBHEAD_TEXT  = "MARINE KIA — FACILITY OVERRUN";
    private static final String PROMPT_TEXT   = "[ANY KEY] RETURN TO STAGING";
    private static final String LABEL_FLOOR   = "FLOOR REACHED";
    private static final String LABEL_KILLS   = "DEMONS PURGED";
    private static final String LABEL_DAMAGE  = "DAMAGE TAKEN";
    private static final String LABEL_TIME    = "TIME IN FACILITY";
    private static final String TAG_NEW_BEST  = "NEW BEST";

    private static final String[] FLAVOR_POOL = {
        "\"The facility keeps its dead.\"",
        "\"UAC will deny you ever existed.\"",
        "\"Another marine. Another statistic.\"",
        "\"Containment holds. You do not.\"",
        "\"The demons won. This time.\""
    };

    // ---- Rendering resources -----------------------------------------------
    private final ShapeRenderer shapes;
    private final SpriteBatch   batch;
    private final BitmapFont    font;
    private final GlyphLayout   layout;  // used only in show() — zero use in render()

    // ---- Pre-computed display state (set in show()) ------------------------
    private String  floorValue;
    private String  killsValue;
    private String  damageTakenValue;
    private String  timeValue;
    private String  flavorLine;
    private boolean newBestFloor;
    private boolean newBestKills;

    // Pre-computed X positions for centered / right-aligned text
    private float headerX;
    private float subheadX;
    private float flavorX;
    private float promptX;
    private float floorValueX;
    private float killsValueX;
    private float damageTakenValueX;
    private float timeValueX;

    public DeathOverlayRenderer() {
        shapes = new ShapeRenderer();
        batch  = new SpriteBatch();
        font   = new BitmapFont();
        layout = new GlyphLayout();
        font.getData().markupEnabled = false;
    }

    /**
     * Snapshots run data and pre-computes all text positions. Call once when the player dies.
     * All {@code GlyphLayout} computation happens here — render() performs none.
     */
    public void show(RunStats run, PersistentStats persistent) {
        newBestFloor = run.floorReached  > persistent.bestFloor  || persistent.totalRuns == 0;
        newBestKills = run.enemiesKilled > persistent.mostKills  && run.enemiesKilled > 0;

        floorValue       = String.valueOf(run.floorReached);
        killsValue       = String.valueOf(run.enemiesKilled);
        damageTakenValue = String.valueOf(run.totalDamageTaken);
        timeValue        = GameMath.formatPlayTime(run.realSecondsPlayed);

        int flavorIndex = (int)(run.ticksElapsed % FLAVOR_POOL.length);
        flavorLine = FLAVOR_POOL[flavorIndex];

        // Pre-compute centered X positions — must match the scale used in render()
        font.getData().setScale(Constants.DEATH_OVERLAY_HEADER_SCALE);
        layout.setText(font, HEADER_TEXT);
        headerX = CENTER_X - layout.width / 2f;

        font.getData().setScale(Constants.DEATH_OVERLAY_SUBHEAD_SCALE);
        layout.setText(font, SUBHEAD_TEXT);
        subheadX = CENTER_X - layout.width / 2f;

        font.getData().setScale(Constants.DEATH_OVERLAY_FLAVOR_SCALE);
        layout.setText(font, flavorLine);
        flavorX = CENTER_X - layout.width / 2f;

        font.getData().setScale(Constants.DEATH_OVERLAY_PROMPT_SCALE);
        layout.setText(font, PROMPT_TEXT);
        promptX = CENTER_X - layout.width / 2f;

        // Pre-compute right-aligned X positions for stat values
        font.getData().setScale(Constants.DEATH_OVERLAY_STAT_SCALE);
        layout.setText(font, floorValue);
        floorValueX = Constants.DEATH_OVERLAY_VALUE_X_MAX - layout.width;

        layout.setText(font, killsValue);
        killsValueX = Constants.DEATH_OVERLAY_VALUE_X_MAX - layout.width;

        layout.setText(font, damageTakenValue);
        damageTakenValueX = Constants.DEATH_OVERLAY_VALUE_X_MAX - layout.width;

        layout.setText(font, timeValue);
        timeValueX = Constants.DEATH_OVERLAY_VALUE_X_MAX - layout.width;
    }

    /**
     * Draws the death screen. No layout computation occurs here.
     *
     * @param showPrompt when true the "[ANY KEY] RETURN TO STAGING" footer is visible (blink)
     */
    public void render(OrthographicCamera camera, boolean showPrompt) {
        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(BACKGROUND);
        shapes.rect(0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);
        shapes.setColor(PANEL_BG);
        shapes.rect(Constants.DEATH_OVERLAY_PANEL_X, Constants.DEATH_OVERLAY_PANEL_Y,
                    Constants.DEATH_OVERLAY_PANEL_WIDTH, Constants.DEATH_OVERLAY_PANEL_HEIGHT);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        Gdx.gl.glLineWidth(3f);
        shapes.setColor(PANEL_BORDER);
        shapes.rect(Constants.DEATH_OVERLAY_PANEL_X, Constants.DEATH_OVERLAY_PANEL_Y,
                    Constants.DEATH_OVERLAY_PANEL_WIDTH, Constants.DEATH_OVERLAY_PANEL_HEIGHT);
        Gdx.gl.glLineWidth(1f);
        shapes.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.begin();

        font.getData().setScale(Constants.DEATH_OVERLAY_HEADER_SCALE);
        font.setColor(HEADER_COLOR);
        font.draw(batch, HEADER_TEXT, headerX, PANEL_TOP - Constants.DEATH_OVERLAY_HEADER_Y_BELOW_TOP);

        font.getData().setScale(Constants.DEATH_OVERLAY_SUBHEAD_SCALE);
        font.setColor(SUBHEAD);
        font.draw(batch, SUBHEAD_TEXT, subheadX, PANEL_TOP - Constants.DEATH_OVERLAY_SUBHEAD_Y_BELOW_TOP);

        font.getData().setScale(Constants.DEATH_OVERLAY_STAT_SCALE);
        float statY = PANEL_TOP - Constants.DEATH_OVERLAY_FIRST_STAT_Y_BELOW_TOP;
        drawStatLine(LABEL_FLOOR,  floorValue,       floorValueX,       newBestFloor, statY);
        statY -= Constants.DEATH_OVERLAY_STAT_LINE_STEP;
        drawStatLine(LABEL_KILLS,  killsValue,        killsValueX,       newBestKills, statY);
        statY -= Constants.DEATH_OVERLAY_STAT_LINE_STEP;
        drawStatLine(LABEL_DAMAGE, damageTakenValue,  damageTakenValueX, false,        statY);
        statY -= Constants.DEATH_OVERLAY_STAT_LINE_STEP;
        drawStatLine(LABEL_TIME,   timeValue,         timeValueX,        false,        statY);

        font.getData().setScale(Constants.DEATH_OVERLAY_FLAVOR_SCALE);
        font.setColor(FLAVOR_COLOR);
        font.draw(batch, flavorLine, flavorX,
                  Constants.DEATH_OVERLAY_PANEL_Y + Constants.DEATH_OVERLAY_FLAVOR_Y_ABOVE_BOTTOM);

        if (showPrompt) {
            font.getData().setScale(Constants.DEATH_OVERLAY_PROMPT_SCALE);
            font.setColor(PROMPT_COLOR);
            font.draw(batch, PROMPT_TEXT, promptX,
                      Constants.DEATH_OVERLAY_PANEL_Y + Constants.DEATH_OVERLAY_PROMPT_Y_ABOVE_BOTTOM);
        }

        batch.end();
    }

    private void drawStatLine(String label, String value, float valueX,
                              boolean isNewBest, float y) {
        font.setColor(LABEL_COLOR);
        font.draw(batch, label, Constants.DEATH_OVERLAY_LABEL_X, y);

        font.setColor(VALUE_COLOR);
        font.draw(batch, value, valueX, y);

        if (isNewBest) {
            font.getData().setScale(Constants.DEATH_OVERLAY_NEWBEST_SCALE);
            font.setColor(NEW_BEST);
            font.draw(batch, TAG_NEW_BEST,
                      Constants.DEATH_OVERLAY_VALUE_X_MAX + Constants.DEATH_OVERLAY_NEWBEST_GAP, y);
            font.getData().setScale(Constants.DEATH_OVERLAY_STAT_SCALE);
        }
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}
