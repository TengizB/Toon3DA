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
import ge.tbegvadze.toon3d.narrative.CodexCategory;
import ge.tbegvadze.toon3d.narrative.CodexSystem;
import ge.tbegvadze.toon3d.narrative.Speaker;
import ge.tbegvadze.toon3d.narrative.StorySettings;
import ge.tbegvadze.toon3d.narrative.StoryStrings;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * Draws the CODEX (Story UI order-6 Part A) — the archive, and the one screen in the game where long
 * text is allowed.  A near-full-screen dark panel: category tabs down the left, the open tab's
 * entries on the right, and an entry's full text in place of that list once one is opened.
 *
 * <p>This class is presentation ONLY.  Which entries exist, which are unlocked, which tab and entry
 * are open, where the body is scrolled and which entries still wear a NEW dot are all decided
 * headlessly by {@link CodexSystem}; the renderer asks it and draws the answer.
 *
 * <h3>Readability, which is the whole point of this screen</h3>
 * Big tap targets (tabs and rows are thumb-sized plates), generous line pitch, light text on a dark
 * panel, and NO horizontal scrolling ever — long text is wrapped to fit, never clipped sideways.
 * The body scrolls vertically under a thumb drag and is CLAMPED to real content, so it can never be
 * flung into empty space.  The accessibility strip along the bottom changes text size, the boot
 * card's reveal pacing, and decorative motion, and every change applies immediately.
 *
 * <h3>How the body is clipped</h3>
 * Scrolled content is drawn first and may overflow its band; the header and footer bands are then
 * painted over it in the panel's own colour.  That is a mask rather than a scissor rectangle, which
 * keeps the whole screen inside the world's {@code FitViewport} coordinate system — a GL scissor
 * would need screen-space rectangles and would break the moment the viewport letterboxes.
 *
 * <p>Nothing is allocated on the draw path: chrome labels and tab names are resolved once in
 * {@link #setStrings}, entry titles and detail lines arrive pre-resolved and pre-wrapped from the
 * system, and the scratch {@link Color} / {@link GlyphLayout} are pre-allocated.  Owns a
 * {@link ShapeRenderer}, a {@link SpriteBatch} and a {@link BitmapFont} — all disposed in
 * {@link #dispose()}.
 */
public final class CodexOverlayRenderer implements Renderable, Disposable {

    private final ShapeRenderer shapes;
    private final SpriteBatch   batch;
    private final BitmapFont    font;
    private final GlyphLayout   layout;
    private final Color         scratchColor = new Color();

    private CodexSystem codexSystem;

    // Chrome labels, resolved ONCE (localisation rule: never a literal in a renderer).
    private String titleLabel    = "";
    private String backLabel     = "";
    private String emptyLabel    = "";
    private String newLabel      = "";
    private String completeLabel = "";
    private final String[] categoryLabels = new String[CodexCategory.values().length];
    private final String[] settingNames   = new String[StoryUiConstants.STORY_CODEX_SETTING_COUNT];

    // The strings table is kept so the VARIABLE labels — the settings strip's values and each tab's
    // recovered count — can be re-resolved when they change.  Both are rebuilt on a tap, never on
    // the draw path, which is what keeps render() free of string building.
    private StoryStrings   strings;
    private final String[] settingValues = new String[StoryUiConstants.STORY_CODEX_SETTING_COUNT];
    private final String[] tabProgressLabels = new String[CodexCategory.values().length];
    private final StringBuilder progressBuilder = new StringBuilder(8);

    public CodexOverlayRenderer() {
        this.shapes = new ShapeRenderer();
        this.batch  = new SpriteBatch();
        this.font   = new BitmapFont();
        this.font.getData().markupEnabled = false;
        this.layout = new GlyphLayout();
        for (int labelIndex = 0; labelIndex < settingValues.length; labelIndex++) {
            settingValues[labelIndex] = "";
        }
        for (int labelIndex = 0; labelIndex < tabProgressLabels.length; labelIndex++) {
            tabProgressLabels[labelIndex] = "";
        }
    }

    /** Binds the headless brain this renderer reads from.  Null hides the screen entirely. */
    public void setCodexSystem(CodexSystem codexSystem) {
        this.codexSystem = codexSystem;
        refreshLabels();
    }

    /** Resolves every fixed label ONCE.  Call at construction time, not per frame. */
    public void setStrings(StoryStrings strings) {
        if (strings == null) return;
        this.strings  = strings;
        titleLabel    = strings.get(StoryUiConstants.STORY_CODEX_TITLE_ID);
        backLabel     = strings.get(StoryUiConstants.STORY_CODEX_BACK_LABEL_ID);
        emptyLabel    = strings.get(StoryUiConstants.STORY_CODEX_EMPTY_ID);
        newLabel      = strings.get(StoryUiConstants.STORY_CODEX_NEW_LABEL_ID);
        completeLabel = strings.get(StoryUiConstants.STORY_CODEX_COMPLETE_LABEL_ID);
        for (CodexCategory category : CodexCategory.values()) {
            categoryLabels[category.ordinal()] = strings.get(category.getTitleStringId());
        }
        for (int settingIndex = 0; settingIndex < settingNames.length; settingIndex++) {
            settingNames[settingIndex] =
                    strings.get(StorySettings.getSettingNameStringId(settingIndex));
        }
        refreshLabels();
    }

    /**
     * Rebuilds every label that can CHANGE while the screen is open: the settings strip's values and
     * each tab's recovered count.  Call when the codex opens and after any tap that could move one —
     * a handful of lookups on a tap, so the draw path never resolves or builds a string.
     */
    public void refreshLabels() {
        if (strings == null || codexSystem == null) return;
        StorySettings settings = codexSystem.getSettings();
        for (int settingIndex = 0; settingIndex < settingValues.length; settingIndex++) {
            settingValues[settingIndex] =
                    strings.get(settings.getSettingValueStringId(settingIndex));
        }
        for (CodexCategory category : CodexCategory.values()) {
            tabProgressLabels[category.ordinal()] = buildTabProgressLabel(category);
        }
    }

    /** "3/5", or the completion label once a tab is filled.  Built on a tap, never per frame. */
    private String buildTabProgressLabel(CodexCategory category) {
        if (codexSystem.isCategoryComplete(category)) return completeLabel;
        progressBuilder.setLength(0);
        progressBuilder.append(codexSystem.getUnlockedCount(category))
                       .append('/')
                       .append(codexSystem.getEntryCount(category));
        return progressBuilder.toString();
    }

    @Override
    public void render(OrthographicCamera camera) {
        if (codexSystem == null || !codexSystem.isOpen()) return;
        float fade = MathUtils.clamp(codexSystem.getVisibleFraction(), 0f, 1f);

        drawBodyShapes(camera, fade);
        drawBodyText(camera, fade);
        drawChromeShapes(camera, fade);
        drawChromeText(camera, fade);
    }

    // -------------------------------------------------------------------------
    // Pass 1 — the scrim, the panel, and the scrolled body's plates.
    // -------------------------------------------------------------------------

    private void drawBodyShapes(OrthographicCamera camera, float fade) {
        beginShapes(camera);

        shapes.setColor(StoryUiConstants.STORY_CODEX_SCRIM_R, StoryUiConstants.STORY_CODEX_SCRIM_G,
                        StoryUiConstants.STORY_CODEX_SCRIM_B,
                        StoryUiConstants.STORY_CODEX_SCRIM_ALPHA * fade);
        shapes.rect(0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);

        // The panel: an accent-tinted border with the dark story-panel fill inset, exactly like the
        // bark and boot card, so the archive is visibly part of the same interface.
        float border = StoryUiConstants.STORY_PANEL_BORDER_THICKNESS;
        setAccentColor(StoryUiConstants.STORY_PANEL_BORDER_ALPHA * fade);
        shapes.rect(StoryUiConstants.STORY_CODEX_X, StoryUiConstants.STORY_CODEX_Y,
                    StoryUiConstants.STORY_CODEX_WIDTH, StoryUiConstants.STORY_CODEX_HEIGHT);
        setPanelFillColor(fade);
        shapes.rect(StoryUiConstants.STORY_CODEX_X + border, StoryUiConstants.STORY_CODEX_Y + border,
                    StoryUiConstants.STORY_CODEX_WIDTH  - border * 2f,
                    StoryUiConstants.STORY_CODEX_HEIGHT - border * 2f);

        if (!codexSystem.isShowingEntry()) drawEntryRowPlates(fade);

        endShapes();
    }

    /** One plate per listed entry, scrolled.  Overflow is masked by the chrome pass. */
    private void drawEntryRowPlates(float fade) {
        int rowCount = codexSystem.getVisibleEntryCount();
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            float rowBottom = entryRowBottomY(rowIndex, codexSystem.getScrollOffset());
            if (isFarOutsideBody(rowBottom, StoryUiConstants.STORY_CODEX_ROW_HEIGHT)) continue;
            drawPlate(StoryUiConstants.STORY_CODEX_BODY_X, rowBottom,
                      StoryUiConstants.STORY_CODEX_BODY_WIDTH,
                      StoryUiConstants.STORY_CODEX_ROW_HEIGHT,
                      StoryUiConstants.STORY_CODEX_PLATE_FILL_ALPHA, fade);
            if (codexSystem.isVisibleEntryNew(rowIndex)) {
                // The NEW dot: an invitation, in the same amber as ORA's voice.  It is drawn as a
                // dot AND labelled in the row's text, so it never depends on colour alone.
                setAccentColor(fade);
                shapes.circle(StoryUiConstants.STORY_CODEX_BODY_X
                                      + StoryUiConstants.STORY_CODEX_BODY_WIDTH
                                      - StoryUiConstants.STORY_CODEX_NEW_DOT_INSET,
                              rowBottom + StoryUiConstants.STORY_CODEX_ROW_HEIGHT / 2f,
                              StoryUiConstants.STORY_CODEX_NEW_DOT_RADIUS, 12);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Pass 2 — the scrolled body's text (entry rows, or one entry's full text).
    // -------------------------------------------------------------------------

    private void drawBodyText(OrthographicCamera camera, float fade) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        if (codexSystem.isShowingEntry()) {
            drawEntryDetailText(fade);
        } else if (codexSystem.getVisibleEntryCount() == 0) {
            drawEmptyTabText(fade);
        } else {
            drawEntryRowText(fade);
        }
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    private void drawEmptyTabText(float fade) {
        font.getData().setScale(StoryUiConstants.STORY_CODEX_ROW_TEXT_SIZE);
        drawDimText(emptyLabel,
                    StoryUiConstants.STORY_CODEX_BODY_X,
                    StoryUiConstants.STORY_CODEX_BODY_TOP_Y - StoryUiConstants.STORY_CODEX_ROW_HEIGHT / 2f,
                    fade);
    }

    private void drawEntryRowText(float fade) {
        int rowCount = codexSystem.getVisibleEntryCount();
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            float rowBottom = entryRowBottomY(rowIndex, codexSystem.getScrollOffset());
            if (isFarOutsideBody(rowBottom, StoryUiConstants.STORY_CODEX_ROW_HEIGHT)) continue;
            float textLeft = StoryUiConstants.STORY_CODEX_BODY_X + StoryUiConstants.STORY_PANEL_PADDING;

            font.getData().setScale(StoryUiConstants.STORY_CODEX_ROW_TEXT_SIZE);
            drawBodyColouredText(codexSystem.getVisibleEntryTitle(rowIndex), textLeft,
                                 rowBottom + StoryUiConstants.STORY_CODEX_ROW_HEIGHT - 12f, fade);

            font.getData().setScale(StoryUiConstants.STORY_CODEX_ROW_SOURCE_TEXT_SIZE);
            drawDimText(codexSystem.isVisibleEntryNew(rowIndex)
                                ? newLabel + "  -  " + codexSystem.getVisibleEntrySource(rowIndex)
                                : codexSystem.getVisibleEntrySource(rowIndex),
                        textLeft, rowBottom + 22f, fade);
        }
    }

    /**
     * The open entry's full text, scrolled.  Each display line carries its own style from the system
     * (title, source, ORA's take, body), so the renderer never has to parse the text it is drawing.
     */
    private void drawEntryDetailText(float fade) {
        float textLeft   = StoryUiConstants.STORY_CODEX_BODY_X + StoryUiConstants.STORY_PANEL_PADDING;
        float codexScale = codexSystem.getSettings().getCodexTextScale();
        int   lineCount  = codexSystem.getDetailLineCount();

        for (int lineIndex = 0; lineIndex < lineCount; lineIndex++) {
            float lineTopY = detailLineTopY(lineIndex, codexSystem.getScrollOffset());
            // The cull takes a band's BOTTOM edge; a text line is anchored by its top, so hand it
            // the top minus one pitch — the row its glyphs actually occupy.
            if (isFarOutsideBody(lineTopY - StoryUiConstants.STORY_CODEX_DETAIL_LINE_PITCH,
                                 StoryUiConstants.STORY_CODEX_DETAIL_LINE_PITCH)) continue;
            String line = codexSystem.getDetailLine(lineIndex);
            if (line.isEmpty()) continue;

            switch (codexSystem.getDetailLineStyle(lineIndex)) {
                case TITLE:
                    font.getData().setScale(StoryUiConstants.STORY_CODEX_TITLE_TEXT_SIZE);
                    drawAccentText(line, textLeft, lineTopY, fade);
                    break;
                case SOURCE:
                case TAKE_LABEL:
                    font.getData().setScale(StoryUiConstants.STORY_CODEX_ROW_SOURCE_TEXT_SIZE);
                    drawDimText(line, textLeft, lineTopY, fade);
                    break;
                case TAKE:
                    font.getData().setScale(codexScale);
                    drawAccentText(line, textLeft, lineTopY, fade);
                    break;
                default:
                    font.getData().setScale(codexScale);
                    drawBodyColouredText(line, textLeft, lineTopY, fade);
                    break;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Pass 3 — the mask bands, then the chrome: tabs, back/close, settings plates.
    // -------------------------------------------------------------------------

    private void drawChromeShapes(OrthographicCamera camera, float fade) {
        beginShapes(camera);

        // MASK: repaint the header and footer bands over anything the scrolled body spilled into.
        setPanelFillColor(1f);   // opaque against the body text, then the panel's own fade applies
        float maskLeft  = StoryUiConstants.STORY_CODEX_BODY_X - StoryUiConstants.STORY_CODEX_BODY_GAP / 2f;
        float maskWidth = StoryUiConstants.STORY_CODEX_RIGHT_X - maskLeft
                - StoryUiConstants.STORY_PANEL_BORDER_THICKNESS;
        float topMaskBottom = StoryUiConstants.STORY_CODEX_BODY_TOP_Y;
        shapes.rect(maskLeft, topMaskBottom, maskWidth,
                    StoryUiConstants.STORY_CODEX_TOP_Y - StoryUiConstants.STORY_PANEL_BORDER_THICKNESS
                            - topMaskBottom);
        shapes.rect(maskLeft, StoryUiConstants.STORY_CODEX_Y + StoryUiConstants.STORY_PANEL_BORDER_THICKNESS,
                    maskWidth,
                    StoryUiConstants.STORY_CODEX_BODY_BOTTOM_Y - StoryUiConstants.STORY_CODEX_Y
                            - StoryUiConstants.STORY_PANEL_BORDER_THICKNESS);

        drawCategoryTabPlates(fade);
        drawSettingPlates(fade);
        if (codexSystem.isShowingEntry()) {
            drawPlate(StoryUiConstants.STORY_CODEX_BACK_X, StoryUiConstants.STORY_CODEX_BACK_Y,
                      StoryUiConstants.STORY_CODEX_BACK_WIDTH, StoryUiConstants.STORY_CODEX_BACK_HEIGHT,
                      StoryUiConstants.STORY_CODEX_PLATE_FILL_ALPHA, fade);
        }
        drawCloseGlyph(fade);

        endShapes();
    }

    private void drawCategoryTabPlates(float fade) {
        for (CodexCategory category : CodexCategory.values()) {
            boolean selected = category == codexSystem.getSelectedCategory();
            drawPlate(StoryUiConstants.STORY_CODEX_TAB_X, categoryTabBottomY(category.ordinal()),
                      StoryUiConstants.STORY_CODEX_TAB_WIDTH, StoryUiConstants.STORY_CODEX_TAB_HEIGHT,
                      selected ? StoryUiConstants.STORY_CODEX_PLATE_SELECTED_ALPHA
                               : StoryUiConstants.STORY_CODEX_PLATE_FILL_ALPHA,
                      fade);
        }
    }

    private void drawSettingPlates(float fade) {
        for (int settingIndex = 0; settingIndex < StoryUiConstants.STORY_CODEX_SETTING_COUNT; settingIndex++) {
            drawPlate(settingButtonX(settingIndex), StoryUiConstants.STORY_CODEX_FOOTER_Y,
                      StoryUiConstants.STORY_CODEX_SETTING_WIDTH,
                      StoryUiConstants.STORY_CODEX_FOOTER_HEIGHT,
                      StoryUiConstants.STORY_CODEX_PLATE_FILL_ALPHA, fade);
        }
    }

    /** The close X, in the body's light colour so it reads as a control rather than as decoration. */
    private void drawCloseGlyph(float fade) {
        shapes.setColor(StoryUiConstants.STORY_BODY_R, StoryUiConstants.STORY_BODY_G,
                        StoryUiConstants.STORY_BODY_B, fade);
        float half   = StoryUiConstants.STORY_BARK_CLOSE_GLYPH_SIZE / 2f;
        float stroke = StoryUiConstants.STORY_BARK_CLOSE_STROKE;
        float centerX = StoryUiConstants.STORY_CODEX_CLOSE_CENTER_X;
        float centerY = StoryUiConstants.STORY_CODEX_CLOSE_CENTER_Y;
        shapes.rectLine(centerX - half, centerY - half, centerX + half, centerY + half, stroke);
        shapes.rectLine(centerX - half, centerY + half, centerX + half, centerY - half, stroke);
    }

    private void drawChromeText(OrthographicCamera camera, float fade) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        // Header: the archive's name, or BACK once an entry is open.
        font.getData().setScale(StoryUiConstants.STORY_CODEX_TITLE_TEXT_SIZE);
        if (codexSystem.isShowingEntry()) {
            drawCenteredInPlate(backLabel, StoryUiConstants.STORY_CODEX_BACK_X,
                                StoryUiConstants.STORY_CODEX_BACK_Y,
                                StoryUiConstants.STORY_CODEX_BACK_WIDTH,
                                StoryUiConstants.STORY_CODEX_BACK_HEIGHT, fade, true);
        } else {
            drawAccentText(titleLabel, StoryUiConstants.STORY_CODEX_X + StoryUiConstants.STORY_PANEL_PADDING,
                           StoryUiConstants.STORY_CODEX_TOP_Y - StoryUiConstants.STORY_CODEX_PADDING, fade);
        }

        drawCategoryTabText(fade);
        drawSettingText(fade);

        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    /**
     * A tab's name plus its recovered count.  The count is dim and never a score; a filled tab wears
     * the completion mark instead, which is the whole of order-6's "completion perk" on this screen.
     */
    private void drawCategoryTabText(float fade) {
        for (CodexCategory category : CodexCategory.values()) {
            float tabBottom = categoryTabBottomY(category.ordinal());
            float textLeft  = StoryUiConstants.STORY_CODEX_TAB_X + StoryUiConstants.STORY_PANEL_PADDING;

            font.getData().setScale(StoryUiConstants.STORY_CODEX_TAB_TEXT_SIZE);
            drawBodyColouredText(categoryLabels[category.ordinal()], textLeft,
                                 tabBottom + StoryUiConstants.STORY_CODEX_TAB_HEIGHT - 14f, fade);

            // A filled tab wears its completion mark in ORA's accent; a partial one stays dim, so
            // progress reads as information rather than as a score being kept on the player.
            font.getData().setScale(StoryUiConstants.STORY_CODEX_TAB_COUNT_TEXT_SIZE);
            String progressLabel = tabProgressLabels[category.ordinal()];
            if (codexSystem.isCategoryComplete(category)) {
                drawAccentText(progressLabel, textLeft, tabBottom + 24f, fade);
            } else {
                drawDimText(progressLabel, textLeft, tabBottom + 24f, fade);
            }
        }
    }

    private void drawSettingText(float fade) {
        font.getData().setScale(StoryUiConstants.STORY_CODEX_FOOTER_TEXT_SIZE);
        for (int settingIndex = 0; settingIndex < StoryUiConstants.STORY_CODEX_SETTING_COUNT; settingIndex++) {
            float buttonX = settingButtonX(settingIndex);
            drawDimText(settingNames[settingIndex], buttonX + 14f,
                        StoryUiConstants.STORY_CODEX_FOOTER_Y
                                + StoryUiConstants.STORY_CODEX_FOOTER_HEIGHT - 10f, fade);
            drawBodyColouredText(settingValues[settingIndex] != null ? settingValues[settingIndex] : "",
                                 buttonX + 14f, StoryUiConstants.STORY_CODEX_FOOTER_Y + 24f, fade);
        }
    }

    // -------------------------------------------------------------------------
    // Layout + hit testing (shared by drawing and input, so they cannot drift apart)
    // -------------------------------------------------------------------------

    /** Bottom edge of the category tab in slot {@code tabIndex} — the stack runs DOWNWARD (Y-up). */
    public static float categoryTabBottomY(int tabIndex) {
        return GameMath.stackedButtonBottomY(StoryUiConstants.STORY_CODEX_TABS_TOP_Y,
                                             StoryUiConstants.STORY_CODEX_TAB_HEIGHT,
                                             StoryUiConstants.STORY_CODEX_TAB_GAP,
                                             tabIndex);
    }

    /** Bottom edge of entry row {@code rowIndex} at the given scroll offset. */
    public static float entryRowBottomY(int rowIndex, float scrollOffset) {
        return GameMath.stackedButtonBottomY(
                StoryUiConstants.STORY_CODEX_BODY_TOP_Y + scrollOffset,
                StoryUiConstants.STORY_CODEX_ROW_HEIGHT,
                StoryUiConstants.STORY_CODEX_ROW_GAP,
                rowIndex);
    }

    /** Top edge (the font's draw anchor) of detail line {@code lineIndex} at the given offset. */
    public static float detailLineTopY(int lineIndex, float scrollOffset) {
        return StoryUiConstants.STORY_CODEX_BODY_TOP_Y + scrollOffset
                - lineIndex * StoryUiConstants.STORY_CODEX_DETAIL_LINE_PITCH;
    }

    /** Left edge of the settings button in slot {@code settingIndex}. */
    public static float settingButtonX(int settingIndex) {
        return StoryUiConstants.STORY_CODEX_BODY_X
                + settingIndex * (StoryUiConstants.STORY_CODEX_SETTING_WIDTH
                                  + StoryUiConstants.STORY_CODEX_SETTING_GAP);
    }

    /** Which category tab a world point falls in, or -1 for none. */
    public static int categoryTabIndexAt(float worldX, float worldY) {
        if (worldX < StoryUiConstants.STORY_CODEX_TAB_X
                || worldX > StoryUiConstants.STORY_CODEX_TAB_X + StoryUiConstants.STORY_CODEX_TAB_WIDTH) {
            return -1;
        }
        for (int tabIndex = 0; tabIndex < CodexCategory.values().length; tabIndex++) {
            float tabBottom = categoryTabBottomY(tabIndex);
            if (worldY >= tabBottom && worldY <= tabBottom + StoryUiConstants.STORY_CODEX_TAB_HEIGHT) {
                return tabIndex;
            }
        }
        return -1;
    }

    /**
     * Which listed entry a world point falls in, or -1 for none.  A point outside the body band never
     * hits a row, even when a plate's geometry technically extends into the masked area.
     */
    public static int entryRowIndexAt(float worldX, float worldY, int rowCount, float scrollOffset) {
        if (!isInsideBody(worldX, worldY)) return -1;
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            float rowBottom = entryRowBottomY(rowIndex, scrollOffset);
            if (worldY >= rowBottom && worldY <= rowBottom + StoryUiConstants.STORY_CODEX_ROW_HEIGHT) {
                return rowIndex;
            }
        }
        return -1;
    }

    /** Which settings button a world point falls in, or -1 for none. */
    public static int settingIndexAt(float worldX, float worldY) {
        if (worldY < StoryUiConstants.STORY_CODEX_FOOTER_Y
                || worldY > StoryUiConstants.STORY_CODEX_FOOTER_Y
                          + StoryUiConstants.STORY_CODEX_FOOTER_HEIGHT) {
            return -1;
        }
        for (int settingIndex = 0; settingIndex < StoryUiConstants.STORY_CODEX_SETTING_COUNT; settingIndex++) {
            float buttonX = settingButtonX(settingIndex);
            if (worldX >= buttonX && worldX <= buttonX + StoryUiConstants.STORY_CODEX_SETTING_WIDTH) {
                return settingIndex;
            }
        }
        return -1;
    }

    /** True when a world point is inside the scrollable body band. */
    public static boolean isInsideBody(float worldX, float worldY) {
        return worldX >= StoryUiConstants.STORY_CODEX_BODY_X
            && worldX <= StoryUiConstants.STORY_CODEX_BODY_X + StoryUiConstants.STORY_CODEX_BODY_WIDTH
            && worldY >= StoryUiConstants.STORY_CODEX_BODY_BOTTOM_Y
            && worldY <= StoryUiConstants.STORY_CODEX_BODY_TOP_Y;
    }

    /** True when a world point is inside the BACK plate (only meaningful while an entry is open). */
    public static boolean isInsideBackButton(float worldX, float worldY) {
        return worldX >= StoryUiConstants.STORY_CODEX_BACK_X
            && worldX <= StoryUiConstants.STORY_CODEX_BACK_X + StoryUiConstants.STORY_CODEX_BACK_WIDTH
            && worldY >= StoryUiConstants.STORY_CODEX_BACK_Y
            && worldY <= StoryUiConstants.STORY_CODEX_BACK_Y + StoryUiConstants.STORY_CODEX_BACK_HEIGHT;
    }

    /** True when a world point is inside the close X's (generously padded) touch rect. */
    public static boolean isInsideCloseButton(float worldX, float worldY) {
        float half = StoryUiConstants.STORY_BARK_CLOSE_GLYPH_SIZE / 2f
                + StoryUiConstants.STORY_BARK_CLOSE_TOUCH_PADDING;
        return Math.abs(worldX - StoryUiConstants.STORY_CODEX_CLOSE_CENTER_X) <= half
            && Math.abs(worldY - StoryUiConstants.STORY_CODEX_CLOSE_CENTER_Y) <= half;
    }

    /** True when a world point is outside the codex panel entirely — a tap there closes the screen. */
    public static boolean isOutsidePanel(float worldX, float worldY) {
        return worldX < StoryUiConstants.STORY_CODEX_X
            || worldX > StoryUiConstants.STORY_CODEX_RIGHT_X
            || worldY < StoryUiConstants.STORY_CODEX_Y
            || worldY > StoryUiConstants.STORY_CODEX_TOP_Y;
    }

    // -------------------------------------------------------------------------
    // Small drawing helpers
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

    /** An accent border with a dim fill inset — the same plate language as the exchange answers. */
    private void drawPlate(float plateX, float plateY, float plateWidth, float plateHeight,
                           float fillAlpha, float fade) {
        float border = StoryUiConstants.STORY_CODEX_PLATE_BORDER;
        setAccentColor(fade);
        shapes.rect(plateX, plateY, plateWidth, plateHeight);
        setAccentColor(fade, fillAlpha);
        shapes.rect(plateX + border, plateY + border,
                    plateWidth - border * 2f, plateHeight - border * 2f);
    }

    /**
     * True when a band of content whose BOTTOM edge is {@code bandBottomY} lies far enough outside
     * the body to skip drawing it.  One band of slack on each side, because anything within that
     * margin may still be partly visible and is covered by the mask if it spills.
     */
    private static boolean isFarOutsideBody(float bandBottomY, float bandHeight) {
        return bandBottomY + bandHeight < StoryUiConstants.STORY_CODEX_BODY_BOTTOM_Y - bandHeight
            || bandBottomY > StoryUiConstants.STORY_CODEX_BODY_TOP_Y + bandHeight;
    }

    private void setAccentColor(float fade) {
        setAccentColor(fade, 1f);
    }

    /** ORA's amber is the archive's accent: this is her record of the descent, not the facility's. */
    private void setAccentColor(float fade, float brightness) {
        int speakerIndex = Speaker.AI.ordinal();
        shapes.setColor(StoryUiConstants.SPEAKER_ACCENT_R[speakerIndex] * brightness,
                        StoryUiConstants.SPEAKER_ACCENT_G[speakerIndex] * brightness,
                        StoryUiConstants.SPEAKER_ACCENT_B[speakerIndex] * brightness,
                        fade);
    }

    private void setPanelFillColor(float fade) {
        shapes.setColor(StoryUiConstants.STORY_PANEL_BG_R, StoryUiConstants.STORY_PANEL_BG_G,
                        StoryUiConstants.STORY_PANEL_BG_B,
                        StoryUiConstants.STORY_PANEL_ALPHA * fade);
    }

    private void drawBodyColouredText(String text, float textX, float textTopY, float fade) {
        drawWithShadow(text, textX, textTopY,
                       StoryUiConstants.STORY_BODY_R, StoryUiConstants.STORY_BODY_G,
                       StoryUiConstants.STORY_BODY_B, fade);
    }

    private void drawAccentText(String text, float textX, float textTopY, float fade) {
        int speakerIndex = Speaker.AI.ordinal();
        drawWithShadow(text, textX, textTopY,
                       StoryUiConstants.SPEAKER_ACCENT_R[speakerIndex],
                       StoryUiConstants.SPEAKER_ACCENT_G[speakerIndex],
                       StoryUiConstants.SPEAKER_ACCENT_B[speakerIndex], fade);
    }

    private void drawDimText(String text, float textX, float textTopY, float fade) {
        drawWithShadow(text, textX, textTopY,
                       StoryUiConstants.STORY_BOOT_COUNTER_R, StoryUiConstants.STORY_BOOT_COUNTER_G,
                       StoryUiConstants.STORY_BOOT_COUNTER_B, fade);
    }

    /** Centres a label in a plate, vertically and (optionally) horizontally. */
    private void drawCenteredInPlate(String text, float plateX, float plateY,
                                     float plateWidth, float plateHeight, float fade,
                                     boolean centerHorizontally) {
        layout.setText(font, text);
        float textX = centerHorizontally ? plateX + (plateWidth - layout.width) / 2f : plateX + 14f;
        // BitmapFont.draw anchors a line by its TOP, so a glyph box of height h centred in a plate
        // spanning [Y, Y+H] has its top at Y + (H + h) / 2 — equal margins above and below.
        float textTopY = plateY + (plateHeight + layout.height) / 2f;
        drawBodyColouredText(text, textX, textTopY, fade);
    }

    /** Draws one string with a dark drop-shadow beneath the coloured glyphs. */
    private void drawWithShadow(String text, float textX, float textTopY,
                                float red, float green, float blue, float fade) {
        float shadow = StoryUiConstants.STORY_TEXT_SHADOW_OFFSET;
        scratchColor.set(0f, 0f, 0f, StoryUiConstants.STORY_TEXT_SHADOW_ALPHA * fade);
        font.setColor(scratchColor);
        font.draw(batch, text, textX + shadow, textTopY - shadow);

        scratchColor.set(red, green, blue, fade);
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
