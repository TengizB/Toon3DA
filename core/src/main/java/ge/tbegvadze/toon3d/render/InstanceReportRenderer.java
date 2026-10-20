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

import ge.tbegvadze.toon3d.narrative.StoryStrings;
import ge.tbegvadze.toon3d.util.ArtDirection;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.HudConstants;
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
 * {@link SpriteBatch} and two {@link BitmapFont}s; all disposed in {@link #dispose()}.
 */
public final class InstanceReportRenderer implements Disposable {

    private final ShapeRenderer shapes;
    private final SpriteBatch   batch;
    private final BitmapFont    font;
    /** HEAVY face: the R1 title and the number column (visual-identity-order-6 R7). */
    private final BitmapFont    heavyFont;
    private final GlyphLayout   layout;

    // Resolved once in setStrings() — localisation rule: never a literal in a renderer.
    private String titleLabel  = "";
    private String victoryTitleLabel = "";
    /** The title this snapshot prints: the instance report, or DESCENT COMPLETE for a won run. */
    private String shownTitleLabel   = "";
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
        this.font   = GameFonts.newFont(FontFace.REGULAR);
        this.heavyFont = GameFonts.newFont(FontFace.HEAVY);
        this.layout = new GlyphLayout();
    }

    /** Resolves every fixed label ONCE.  Call at construction time, not per frame. */
    public void setStrings(StoryStrings strings) {
        if (strings == null) return;
        titleLabel  = strings.get(StoryUiConstants.STORY_REPORT_TITLE_ID);
        victoryTitleLabel = strings.get(StoryUiConstants.STORY_REPORT_VICTORY_TITLE_ID);
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
        shownTitleLabel = run.isVictory() ? victoryTitleLabel : titleLabel;
        boolean firstRunEver = persistent == null || persistent.totalRuns == 0;
        boolean bestFloor    = firstRunEver || run.floorReached  > persistent.bestFloor;
        boolean bestKills    = run.enemiesKilled > 0
                && (firstRunEver || run.enemiesKilled > persistent.mostKills);

        addLine(floorLabel,  String.valueOf(run.floorReached),     bestFloor);
        addLine(killsLabel,  String.valueOf(run.enemiesKilled),    bestKills);
        addLine(damageLabel, String.valueOf(run.totalDamageTaken), false);
        addLine(timeLabel,   GameMath.formatPlayTime(run.realSecondsPlayed), false);
        addLine(relicsLine(run.getRelicsBonded()), null, false);
        addLine("", null, false);   // a blank row: the autopsy is a second block, not a fifth stat

        // RUN AUTOPSY (new-game-balancr order 9, part C) — the six lines a playtester reports a run
        // with.  Already formatted machine readout, so it prints verbatim.
        for (String autopsyLine : run.autopsyLines()) {
            addLine(autopsyLine, null, false);
        }
    }

    /** "RELICS: A, B" or "RELICS: none"; trimmed with an ellipsis if the list would overrun the panel. Built once per death. */
    private String relicsLine(String relicNames) {
        if (relicNames == null || relicNames.isEmpty()) return HudConstants.RELIC_REPORT_NONE;
        String line = HudConstants.RELIC_REPORT_PREFIX + relicNames;
        float maxWidth = StoryUiConstants.STORY_REPORT_PANEL_WIDTH - StoryUiConstants.STORY_REPORT_TEXT_INSET_X * 2f;
        font.getData().setScale(StoryUiConstants.STORY_REPORT_TEXT_SIZE);
        layout.setText(font, line);
        while (layout.width > maxWidth && line.length() > HudConstants.RELIC_REPORT_PREFIX.length() + 1) {
            line = line.substring(0, line.length() - 3) + "..";
            layout.setText(font, line);
        }
        font.getData().setScale(1f);
        return line;
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
        drawFacilityPlate(StoryUiConstants.STORY_REPORT_OPEN_X, StoryUiConstants.STORY_REPORT_OPEN_Y,
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
        drawFacilityPlate(StoryUiConstants.STORY_REPORT_BACK_X, StoryUiConstants.STORY_REPORT_BACK_Y,
                          StoryUiConstants.STORY_REPORT_BACK_WIDTH, StoryUiConstants.STORY_REPORT_BACK_HEIGHT, 1f);
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

        // R7: the machine's record has a screen header - HEAVY title, the rule under it in the panel pass.
        UiKit.screenHeaderTitle(batch, heavyFont, shownTitleLabel, textLeft, titleTopY());

        font.getData().setScale(StoryUiConstants.STORY_REPORT_TEXT_SIZE);
        float lineTopY = StoryUiConstants.STORY_REPORT_PANEL_TOP_Y
                - StoryUiConstants.STORY_REPORT_FIRST_LINE_INSET_Y;
        for (int lineIndex = 0; lineIndex < lineCount; lineIndex++) {
            drawText(lineLabels[lineIndex], textLeft, lineTopY,
                     StoryUiConstants.STORY_REPORT_R, StoryUiConstants.STORY_REPORT_G,
                     StoryUiConstants.STORY_REPORT_B);
            if (lineValues[lineIndex] != null) {
                // R7: the number column is HEAVY (the font's digits are tabular, so a column stays a column).
                heavyFont.getData().setScale(StoryUiConstants.STORY_REPORT_TEXT_SIZE);
                heavyFont.setColor(StoryUiConstants.STORY_BODY_R, StoryUiConstants.STORY_BODY_G,
                                   StoryUiConstants.STORY_BODY_B, 1f);
                heavyFont.draw(batch, lineValues[lineIndex], valueLeft, lineTopY);
                if (lineRecords[lineIndex]) {
                    layout.setText(heavyFont, lineValues[lineIndex]);
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
        heavyFont.getData().setScale(1f);
        heavyFont.setColor(Color.WHITE);
        batch.end();
    }

    /** Top of the R1 title, at the panel's existing title inset. */
    private static float titleTopY() {
        return StoryUiConstants.STORY_REPORT_PANEL_TOP_Y - StoryUiConstants.STORY_REPORT_TITLE_INSET_Y;
    }

    /**
     * R7: the record is the FACILITY's — a kit panel at the screen alpha with FACILITY_CYAN brackets, the R1
     * header rule with a cyan tick, and a FACILITY_CYAN_DIM rule on the blank row that separates the stats from
     * the RUN AUTOPSY.
     */
    private void drawPanel() {
        float panelX = StoryUiConstants.STORY_REPORT_PANEL_X;
        float panelY = StoryUiConstants.STORY_REPORT_PANEL_Y;
        float panelWidth = StoryUiConstants.STORY_REPORT_PANEL_WIDTH;
        float panelHeight = StoryUiConstants.STORY_REPORT_PANEL_HEIGHT;
        UiKit.panel(shapes, panelX, panelY, panelWidth, panelHeight,
                    ArtDirection.UI_PANEL_RED, ArtDirection.UI_PANEL_GREEN, ArtDirection.UI_PANEL_BLUE,
                    ArtDirection.UI_PANEL_ALPHA_SCREEN,
                    ArtDirection.UI_LINE_RED, ArtDirection.UI_LINE_GREEN, ArtDirection.UI_LINE_BLUE);
        UiKit.brackets(shapes, panelX, panelY, panelWidth, panelHeight,
                       ArtDirection.FACILITY_CYAN_RED, ArtDirection.FACILITY_CYAN_GREEN,
                       ArtDirection.FACILITY_CYAN_BLUE, 1f);

        float textLeft = panelX + StoryUiConstants.STORY_REPORT_TEXT_INSET_X;
        float ruleWidth = panelWidth - StoryUiConstants.STORY_REPORT_TEXT_INSET_X * 2f;
        UiKit.screenHeaderRule(shapes, textLeft, UiKit.screenHeaderRuleY(titleTopY()), ruleWidth,
                               ArtDirection.FACILITY_CYAN_RED, ArtDirection.FACILITY_CYAN_GREEN,
                               ArtDirection.FACILITY_CYAN_BLUE, 1f);

        // Section divider: centred in each blank row (a label-less, value-less line).
        float lineTopY = StoryUiConstants.STORY_REPORT_PANEL_TOP_Y - StoryUiConstants.STORY_REPORT_FIRST_LINE_INSET_Y;
        float capHeight = ArtDirection.PIXEL_FONT_TARGET_CAP_HEIGHT * StoryUiConstants.STORY_REPORT_TEXT_SIZE;
        shapes.setColor(ArtDirection.FACILITY_CYAN_DIM_RED, ArtDirection.FACILITY_CYAN_DIM_GREEN,
                        ArtDirection.FACILITY_CYAN_DIM_BLUE, 1f);
        for (int lineIndex = 0; lineIndex < lineCount; lineIndex++) {
            if (lineLabels[lineIndex].isEmpty() && lineValues[lineIndex] == null) {
                shapes.rect(textLeft, lineTopY - capHeight * 0.5f, ruleWidth, ArtDirection.UI_DIVIDER_THICKNESS);
            }
            lineTopY -= StoryUiConstants.STORY_REPORT_LINE_PITCH;
        }
    }

    /**
     * A FACILITY plate (visual-identity-order-6 R5): an IDLE card inked FACILITY_CYAN_DIM, so the machine's
     * controls read as the machine's even before they are touched.  Nothing here has a pressed state (a tap
     * commits on touch).
     */
    private void drawFacilityPlate(float plateX, float plateY, float plateWidth, float plateHeight, float fade) {
        UiKit.card(shapes, plateX, plateY, plateWidth, plateHeight, ArtDirection.UI_PLATE_CHAMFER,
                   ArtDirection.FACILITY_CYAN_DIM_RED, ArtDirection.FACILITY_CYAN_DIM_GREEN,
                   ArtDirection.FACILITY_CYAN_DIM_BLUE, PlateState.IDLE, fade);
    }

    private void drawCenteredInPlate(String text, float plateX, float plateY,
                                     float plateWidth, float plateHeight, float fade) {
        layout.setText(font, text);
        font.setColor(StoryUiConstants.STORY_BODY_R, StoryUiConstants.STORY_BODY_G,
                      StoryUiConstants.STORY_BODY_B, fade);
        // BitmapFont.draw anchors a line by its TOP: a glyph box of height h centred in a plate
        // spanning [Y, Y+H] has its top at Y + (H + h) / 2 — equal margins above and below.
        font.draw(batch, text, plateX + (plateWidth - layout.width) / 2f,
                  plateY + (plateHeight + layout.height) / 2f);
    }

    private void drawText(String text, float textX, float textTopY,
                          float red, float green, float blue) {
        font.setColor(red, green, blue, 1f);
        font.draw(batch, text, textX, textTopY);
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
        heavyFont.dispose();
    }
}
