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

import ge.tbegvadze.toon3d.narrative.Speaker;
import ge.tbegvadze.toon3d.narrative.StoryStrings;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.StoryUiConstants;
import ge.tbegvadze.toon3d.world.PersistentStats;
import ge.tbegvadze.toon3d.world.RunStats;

/**
 * The PREVIOUS INSTANCE report (Story UI order-8 Part B) — what the run that just ended managed,
 * printed in the machine's voice.
 *
 * <p>This is the old death overlay, FOLDED into the death→reprint framing.  There used to be two
 * screens for one moment: a red "INCURSION TERMINATED" report in one visual language, and then the
 * reprint card in another.  Now there is one presentation — fade, card, CONTINUE — and this report
 * is a PAGE of that card's screen, opened by a small plate and closed by one tap.  The default path
 * through a death never sees it, and the numbers (including the RUN AUTOPSY that new-game-balancr
 * order 9 put there for playtesters) are one tap away for whoever wants them.
 *
 * <p>Presentation only, and deliberately quiet: no gore, no verdict, no flavour quip.  The system
 * voice reports machine state and never editorialises (story/dialog/system-cards.md).
 *
 * <p>All layout is computed in {@link #show}, once per death — {@link #render} measures only what it
 * centres, through the shared {@link GlyphLayout}.  Owns a {@link ShapeRenderer}, a
 * {@link SpriteBatch} and a {@link BitmapFont}; all three disposed in {@link #dispose()}.
 */
public final class InstanceReportRenderer implements Disposable {

    private final ShapeRenderer shapes;
    private final SpriteBatch   batch;
    private final BitmapFont    font;
    private final GlyphLayout   layout;
    private final Color         scratchColor = new Color();

    // Resolved once in setStrings() — localisation rule: never a literal in a renderer.
    private String titleLabel  = "";
    private String openLabel   = "";
    private String backLabel   = "";
    private String floorLabel  = "";
    private String killsLabel  = "";
    private String damageLabel = "";
    private String timeLabel   = "";
    private String recordLabel = "";

    // The report itself, built in show(): a label, an optional right-hand value, and whether that
    // value beat the save's best.  An autopsy line is a label with no value.
    private final String[]  lineLabels  = new String[StoryUiConstants.STORY_REPORT_MAX_LINES];
    private final String[]  lineValues  = new String[StoryUiConstants.STORY_REPORT_MAX_LINES];
    private final boolean[] lineRecords = new boolean[StoryUiConstants.STORY_REPORT_MAX_LINES];
    private int             lineCount;

    public InstanceReportRenderer() {
        this.shapes = new ShapeRenderer();
        this.batch  = new SpriteBatch();
        this.font   = new BitmapFont();
        this.font.getData().markupEnabled = false;
        this.layout = new GlyphLayout();
    }

    /** Resolves every fixed label ONCE.  Call at construction time, not per frame. */
    public void setStrings(StoryStrings strings) {
        if (strings == null) return;
        titleLabel  = strings.get(StoryUiConstants.STORY_REPORT_TITLE_ID);
        openLabel   = strings.get(StoryUiConstants.STORY_REPORT_OPEN_ID);
        backLabel   = strings.get(StoryUiConstants.STORY_REPORT_BACK_ID);
        floorLabel  = strings.get(StoryUiConstants.STORY_REPORT_FLOOR_ID);
        killsLabel  = strings.get(StoryUiConstants.STORY_REPORT_KILLS_ID);
        damageLabel = strings.get(StoryUiConstants.STORY_REPORT_DAMAGE_ID);
        timeLabel   = strings.get(StoryUiConstants.STORY_REPORT_TIME_ID);
        recordLabel = strings.get(StoryUiConstants.STORY_REPORT_BEST_ID);
    }

    /**
     * Snapshots the finished run.  Call ONCE, the moment the player dies (or abandons) — before the
     * records are saved, so the run can still be compared against the best the save held.
     */
    public void show(RunStats run, PersistentStats persistent) {
        lineCount = 0;
        if (run == null) return;
        boolean firstRunEver = persistent == null || persistent.totalRuns == 0;
        boolean bestFloor    = firstRunEver || run.floorReached  > persistent.bestFloor;
        boolean bestKills    = run.enemiesKilled > 0
                && (firstRunEver || run.enemiesKilled > persistent.mostKills);

        addLine(floorLabel,  String.valueOf(run.floorReached),     bestFloor);
        addLine(killsLabel,  String.valueOf(run.enemiesKilled),    bestKills);
        addLine(damageLabel, String.valueOf(run.totalDamageTaken), false);
        addLine(timeLabel,   GameMath.formatPlayTime(run.realSecondsPlayed), false);
        addLine("", null, false);   // a blank row: the autopsy is a second block, not a fifth stat

        // RUN AUTOPSY (new-game-balancr order 9, part C) — the six lines a playtester reports a run
        // with.  Already formatted machine readout, so it prints verbatim.
        for (String autopsyLine : run.autopsyLines()) {
            addLine(autopsyLine, null, false);
        }
    }

    private void addLine(String label, String value, boolean isRecord) {
        if (lineCount >= lineLabels.length || label == null) return;
        lineLabels[lineCount]  = label;
        lineValues[lineCount]  = value;
        lineRecords[lineCount] = isRecord;
        lineCount++;
    }

    /** True once a run has been snapshotted — the reprint card only offers the page when it has one. */
    public boolean hasReport() {
        return lineCount > 0;
    }

    // -------------------------------------------------------------------------
    // Drawing
    // -------------------------------------------------------------------------

    /**
     * Draws the small REPORT plate on the reprint card, bottom-left and out of the way of CONTINUE.
     * The card is the moment; this is a footnote on it.
     */
    public void renderOpenButton(OrthographicCamera camera, float fade) {
        if (!hasReport()) return;
        float clampedFade = MathUtils.clamp(fade, 0f, 1f);

        beginShapes(camera);
        drawPlate(StoryUiConstants.STORY_REPORT_OPEN_X, StoryUiConstants.STORY_REPORT_OPEN_Y,
                  StoryUiConstants.STORY_REPORT_OPEN_WIDTH, StoryUiConstants.STORY_REPORT_OPEN_HEIGHT,
                  clampedFade);
        endShapes();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.getData().setScale(StoryUiConstants.STORY_FRAME_MENU_VALUE_TEXT_SIZE);
        drawCenteredInPlate(openLabel, StoryUiConstants.STORY_REPORT_OPEN_X,
                            StoryUiConstants.STORY_REPORT_OPEN_Y,
                            StoryUiConstants.STORY_REPORT_OPEN_WIDTH,
                            StoryUiConstants.STORY_REPORT_OPEN_HEIGHT, clampedFade);
        endBatch();
    }

    /** Draws the report page itself: the panel, the readout, and one BACK plate. */
    public void render(OrthographicCamera camera) {
        if (!hasReport()) return;

        beginShapes(camera);
        // Its own backdrop, so the card behind it never shows through and competes for the eye.
        shapes.setColor(StoryUiConstants.STORY_FRAME_BACKDROP_R,
                        StoryUiConstants.STORY_FRAME_BACKDROP_G,
                        StoryUiConstants.STORY_FRAME_BACKDROP_B,
                        StoryUiConstants.STORY_FRAME_BACKDROP_ALPHA);
        shapes.rect(0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);
        drawPanel();
        drawPlate(StoryUiConstants.STORY_REPORT_BACK_X, StoryUiConstants.STORY_REPORT_BACK_Y,
                  StoryUiConstants.STORY_REPORT_BACK_WIDTH, StoryUiConstants.STORY_REPORT_BACK_HEIGHT,
                  1f);
        endShapes();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawReportText();
        font.getData().setScale(StoryUiConstants.STORY_FRAME_MENU_TEXT_SIZE);
        drawCenteredInPlate(backLabel, StoryUiConstants.STORY_REPORT_BACK_X,
                            StoryUiConstants.STORY_REPORT_BACK_Y,
                            StoryUiConstants.STORY_REPORT_BACK_WIDTH,
                            StoryUiConstants.STORY_REPORT_BACK_HEIGHT, 1f);
        endBatch();
    }

    private void drawReportText() {
        float textLeft = StoryUiConstants.STORY_REPORT_PANEL_X
                + StoryUiConstants.STORY_REPORT_TEXT_INSET_X;
        float valueLeft = StoryUiConstants.STORY_REPORT_PANEL_X
                + StoryUiConstants.STORY_REPORT_VALUE_INSET_X;

        font.getData().setScale(StoryUiConstants.STORY_REPORT_TITLE_TEXT_SIZE);
        drawText(titleLabel, textLeft,
                 StoryUiConstants.STORY_REPORT_PANEL_TOP_Y - StoryUiConstants.STORY_REPORT_TITLE_INSET_Y,
                 StoryUiConstants.STORY_BODY_R, StoryUiConstants.STORY_BODY_G,
                 StoryUiConstants.STORY_BODY_B);

        font.getData().setScale(StoryUiConstants.STORY_REPORT_TEXT_SIZE);
        float lineTopY = StoryUiConstants.STORY_REPORT_PANEL_TOP_Y
                - StoryUiConstants.STORY_REPORT_FIRST_LINE_INSET_Y;
        for (int lineIndex = 0; lineIndex < lineCount; lineIndex++) {
            drawText(lineLabels[lineIndex], textLeft, lineTopY,
                     StoryUiConstants.STORY_REPORT_R, StoryUiConstants.STORY_REPORT_G,
                     StoryUiConstants.STORY_REPORT_B);
            if (lineValues[lineIndex] != null) {
                drawText(lineValues[lineIndex], valueLeft, lineTopY,
                         StoryUiConstants.STORY_BODY_R, StoryUiConstants.STORY_BODY_G,
                         StoryUiConstants.STORY_BODY_B);
                if (lineRecords[lineIndex]) {
                    layout.setText(font, lineValues[lineIndex]);
                    drawText(recordLabel,
                             valueLeft + layout.width + StoryUiConstants.STORY_CHIP_ELEMENT_GAP * 2f,
                             lineTopY,
                             StoryUiConstants.STORY_REPORT_RECORD_R,
                             StoryUiConstants.STORY_REPORT_RECORD_G,
                             StoryUiConstants.STORY_REPORT_RECORD_B);
                }
            }
            lineTopY -= StoryUiConstants.STORY_REPORT_LINE_PITCH;
        }
    }

    // -------------------------------------------------------------------------
    // Hit rects — statics, so the plates the engine tests against are the ones drawn above.
    // -------------------------------------------------------------------------

    /** True when the point is on the REPORT plate that opens this page from the reprint card. */
    public static boolean isInsideOpenButton(float worldX, float worldY) {
        return isInsideRect(worldX, worldY, StoryUiConstants.STORY_REPORT_OPEN_X,
                            StoryUiConstants.STORY_REPORT_OPEN_Y,
                            StoryUiConstants.STORY_REPORT_OPEN_WIDTH,
                            StoryUiConstants.STORY_REPORT_OPEN_HEIGHT);
    }

    /** True when the point is on the page's BACK plate.  A tap anywhere else also closes it. */
    public static boolean isInsideBackButton(float worldX, float worldY) {
        return isInsideRect(worldX, worldY, StoryUiConstants.STORY_REPORT_BACK_X,
                            StoryUiConstants.STORY_REPORT_BACK_Y,
                            StoryUiConstants.STORY_REPORT_BACK_WIDTH,
                            StoryUiConstants.STORY_REPORT_BACK_HEIGHT);
    }

    private static boolean isInsideRect(float worldX, float worldY,
                                        float rectX, float rectY, float rectWidth, float rectHeight) {
        return worldX >= rectX && worldX <= rectX + rectWidth
            && worldY >= rectY && worldY <= rectY + rectHeight;
    }

    // -------------------------------------------------------------------------
    // Primitives (the framing screens' plate language)
    // -------------------------------------------------------------------------

    private void beginShapes(OrthographicCamera camera) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
    }

    private void endShapes() {
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void endBatch() {
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    private void drawPanel() {
        int speakerIndex = Speaker.SYSTEM.ordinal();
        float border     = StoryUiConstants.STORY_PANEL_BORDER_THICKNESS;
        shapes.setColor(StoryUiConstants.SPEAKER_ACCENT_R[speakerIndex],
                        StoryUiConstants.SPEAKER_ACCENT_G[speakerIndex],
                        StoryUiConstants.SPEAKER_ACCENT_B[speakerIndex],
                        StoryUiConstants.STORY_PANEL_BORDER_ALPHA);
        shapes.rect(StoryUiConstants.STORY_REPORT_PANEL_X, StoryUiConstants.STORY_REPORT_PANEL_Y,
                    StoryUiConstants.STORY_REPORT_PANEL_WIDTH,
                    StoryUiConstants.STORY_REPORT_PANEL_HEIGHT);
        shapes.setColor(StoryUiConstants.STORY_PANEL_BG_R, StoryUiConstants.STORY_PANEL_BG_G,
                        StoryUiConstants.STORY_PANEL_BG_B, StoryUiConstants.STORY_PANEL_ALPHA);
        shapes.rect(StoryUiConstants.STORY_REPORT_PANEL_X + border,
                    StoryUiConstants.STORY_REPORT_PANEL_Y + border,
                    StoryUiConstants.STORY_REPORT_PANEL_WIDTH  - border * 2f,
                    StoryUiConstants.STORY_REPORT_PANEL_HEIGHT - border * 2f);
    }

    private void drawPlate(float plateX, float plateY, float plateWidth, float plateHeight, float fade) {
        int speakerIndex = Speaker.AI.ordinal();
        float accentRed   = StoryUiConstants.SPEAKER_ACCENT_R[speakerIndex];
        float accentGreen = StoryUiConstants.SPEAKER_ACCENT_G[speakerIndex];
        float accentBlue  = StoryUiConstants.SPEAKER_ACCENT_B[speakerIndex];
        float border      = StoryUiConstants.STORY_FRAME_MENU_BORDER;
        float fillAlpha   = StoryUiConstants.STORY_FRAME_MENU_FILL_ALPHA;

        shapes.setColor(accentRed, accentGreen, accentBlue, fade);
        shapes.rect(plateX, plateY, plateWidth, plateHeight);
        shapes.setColor(accentRed * fillAlpha, accentGreen * fillAlpha, accentBlue * fillAlpha, fade);
        shapes.rect(plateX + border, plateY + border,
                    plateWidth - border * 2f, plateHeight - border * 2f);
    }

    private void drawCenteredInPlate(String text, float plateX, float plateY,
                                     float plateWidth, float plateHeight, float fade) {
        layout.setText(font, text);
        scratchColor.set(StoryUiConstants.STORY_BODY_R, StoryUiConstants.STORY_BODY_G,
                         StoryUiConstants.STORY_BODY_B, fade);
        font.setColor(scratchColor);
        // BitmapFont.draw anchors a line by its TOP: a glyph box of height h centred in a plate
        // spanning [Y, Y+H] has its top at Y + (H + h) / 2 — equal margins above and below.
        font.draw(batch, text, plateX + (plateWidth - layout.width) / 2f,
                  plateY + (plateHeight + layout.height) / 2f);
    }

    private void drawText(String text, float textX, float textTopY,
                          float red, float green, float blue) {
        scratchColor.set(red, green, blue, 1f);
        font.setColor(scratchColor);
        font.draw(batch, text, textX, textTopY);
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}
