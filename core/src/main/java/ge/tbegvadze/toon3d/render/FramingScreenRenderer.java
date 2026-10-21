package ge.tbegvadze.toon3d.render;

import java.util.List;

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

import ge.tbegvadze.toon3d.narrative.FramingMenu;
import ge.tbegvadze.toon3d.narrative.StoryStrings;
import ge.tbegvadze.toon3d.narrative.StoryText;
import ge.tbegvadze.toon3d.narrative.TitleScreenSystem;
import ge.tbegvadze.toon3d.util.ArtDirection;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * Draws the FRAMING SCREENS (Story UI order-8) — the screens that book-end play:
 *
 * <ol>
 *   <li>the LAUNCH / TITLE screen, which opens on the game telling the player they are already
 *       dead, then resolves the title quietly beneath it and fades a cold menu in under that;</li>
 *   <li>the PAUSE and SETTINGS menus, drawn over the frozen floor;</li>
 *   <li>the CONFIRMATION either of them may raise, because wiping a save or abandoning a run is
 *       never one tap away;</li>
 *   <li>the way OFF an ending that authorises no reprint — the dim plate that stands where the
 *       CONTINUE the player has tapped a hundred times is deliberately missing.</li>
 * </ol>
 *
 * <p>Presentation ONLY: which rows exist, which are available, what a tap does and whether a
 * confirmation is open are decided headlessly ({@link TitleScreenSystem}, {@link FramingMenu}).  The
 * hit rects the engine unprojects into live here as statics, so drawing and input can never drift.
 *
 * <p>INK (visual-identity-order-6 R6), decided per screen by who is showing it to you: the launch screen
 * is the reprint machine's — FACILITY plates (an IDLE card in FACILITY_CYAN_DIM, the first row SELECTED in
 * FACILITY_CYAN) under a HEAVY title with a cyan rule; the pause and settings menus are the suit's — SUIT
 * plates under an R1 screen header, framed by SUIT_AMBER_DIM brackets; a confirmation takes the ink of the
 * screen that opened it; the death stroke is bone text alone on the black.  Every screen here sits on black
 * or a scrim, so no text is shadowed.
 *
 * <p>Everything is procedural — one {@link ShapeRenderer}, one {@link SpriteBatch}, two
 * {@link BitmapFont}s, no textures — and all are disposed in {@link #dispose()}.  The only
 * per-frame work is measuring already-resolved labels through the shared {@link GlyphLayout} (the
 * established idiom in {@code CodexOverlayRenderer}); the confirmation's prose is wrapped once, when
 * its question changes, never per frame.
 */
public final class FramingScreenRenderer implements Disposable {

    private final ShapeRenderer shapes;
    private final SpriteBatch   batch;
    private final BitmapFont    font;
    /** HEAVY face for the title and the death stroke (visual-identity-order-3 R7). */
    private final BitmapFont    displayFont;
    private final GlyphLayout   layout;

    private StoryStrings      strings;
    private TitleScreenSystem titleScreenSystem;

    // Resolved once in setStrings() — the launch screen's fixed chrome.
    private String gameTitleLabel    = "";
    private String gameTaglineLabel  = "";
    private String confirmYesLabel   = "";
    private String confirmNoLabel    = "";
    private String returnLabel       = "";
    private String deathStrokeLabel  = "";
    /** The title's width at TYPE_DISPLAY, measured once: the cyan rule under it spans it. */
    private float  gameTitleWidth;

    // The confirmation's question, wrapped once per DIFFERENT question rather than per frame.
    private final String[] confirmLines = new String[StoryUiConstants.STORY_FRAME_CONFIRM_MAX_LINES];
    private int            confirmLineCount;
    private String         wrappedConfirmStringId;

    public FramingScreenRenderer() {
        this.shapes = new ShapeRenderer();
        this.batch  = new SpriteBatch();
        this.font   = GameFonts.newFont(FontFace.REGULAR);
        this.displayFont = GameFonts.newFont(FontFace.HEAVY);
        this.layout = new GlyphLayout();
    }

    /** Resolves every fixed label ONCE (localisation rule: never a literal here). */
    public void setStrings(StoryStrings strings) {
        if (strings == null) return;
        this.strings     = strings;
        gameTitleLabel   = strings.get(StoryUiConstants.STORY_TITLE_GAME_ID);
        gameTaglineLabel = strings.get(StoryUiConstants.STORY_TITLE_TAGLINE_ID);
        confirmYesLabel  = strings.get(StoryUiConstants.STORY_FRAME_CONFIRM_YES_ID);
        confirmNoLabel   = strings.get(StoryUiConstants.STORY_FRAME_CONFIRM_NO_ID);
        returnLabel      = strings.get(StoryUiConstants.STORY_FRAME_RETURN_ID);
        deathStrokeLabel = strings.get(StoryUiConstants.STORY_DEATH_STROKE_ID);
        displayFont.getData().setScale(StoryUiConstants.STORY_TITLE_GAME_TEXT_SIZE);
        layout.setText(displayFont, gameTitleLabel);
        gameTitleWidth = layout.width;
        displayFont.getData().setScale(1f);
    }

    // -------------------------------------------------------------------------
    // 0. The death stroke — the first thing drawn after a death, and the whole of it
    // -------------------------------------------------------------------------

    /**
     * Three words on black, and nothing else (narrative-rework order-2).
     *
     * <p>This is the moment of death, and it is deliberately NOT the reprint card: the card reports
     * a birth — a machine somewhere else making another body — and printing both at once meant
     * neither landed.  So there is no counter here, no status block, no button, and no ORA.  Her
     * absence is the point: she does not get printed, she reloads, so for one second the only voice
     * in the game is missing.  She comes back on the card, and the first thing she says is that the
     * player is alright.
     *
     * <p>The caller has already faded the world to black; this draws over it.
     *
     * @param wordsFade 0..1 resolve of the words themselves, which arrive after the black does
     */
    public void renderDeathStroke(OrthographicCamera camera, float wordsFade) {
        if (strings == null || wordsFade <= 0f) return;
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        // R6: HEAVY at TYPE_DISPLAY, bone on the finished black — no brackets, no panel, no shadow, nothing else.
        displayFont.getData().setScale(StoryUiConstants.STORY_DEATH_STROKE_TEXT_SIZE);
        layout.setText(displayFont, deathStrokeLabel);
        drawText(displayFont, deathStrokeLabel,
                 Constants.WORLD_WIDTH / 2f - layout.width / 2f,
                 StoryUiConstants.STORY_DEATH_STROKE_TOP_Y,
                 StoryUiConstants.STORY_DEATH_STROKE_R, StoryUiConstants.STORY_DEATH_STROKE_G,
                 StoryUiConstants.STORY_DEATH_STROKE_B,
                 MathUtils.clamp(wordsFade, 0f, 1f));
        endBatch();
    }

    /** Binds the headless brain behind the launch screen.  Null draws no title screen at all. */
    public void setTitleScreenSystem(TitleScreenSystem titleScreenSystem) {
        this.titleScreenSystem = titleScreenSystem;
    }

    // -------------------------------------------------------------------------
    // 1. The launch / title screen
    // -------------------------------------------------------------------------

    /**
     * Draws the launch screen: black, then the machine's death notice typing out, then the title,
     * then the menu.  The order is the point — the first thing anybody reads is a death.
     */
    public void renderTitle(OrthographicCamera camera) {
        if (titleScreenSystem == null || strings == null) return;
        FramingMenu menu = titleScreenSystem.getMenu();
        float fade       = MathUtils.clamp(titleScreenSystem.getVisibleFraction(), 0f, 1f);
        float menuFade   = fade * MathUtils.clamp(titleScreenSystem.getMenuVisibleFraction(), 0f, 1f);
        boolean menuVisible  = titleScreenSystem.isMenuVisible() && !menu.isConfirmOpen();
        boolean titleVisible = titleScreenSystem.isTitleVisible();

        beginShapes(camera);
        drawBackdrop(StoryUiConstants.STORY_FRAME_BACKDROP_ALPHA * fade);
        if (titleVisible) {
            // R6: the title's FACILITY_CYAN rule, as wide as the title, between it and the tagline.
            UiKit.screenHeaderRule(shapes, (Constants.WORLD_WIDTH - gameTitleWidth) / 2f,
                                   titleRuleY(), gameTitleWidth,
                                   ArtDirection.FACILITY_CYAN_RED, ArtDirection.FACILITY_CYAN_GREEN,
                                   ArtDirection.FACILITY_CYAN_BLUE, fade);
        }
        if (menuVisible) {
            drawMenuPlates(menu, StoryUiConstants.STORY_TITLE_MENU_TOP_Y, menuFade, true);
        }
        endShapes();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawLaunchStatusLines(fade);
        if (titleVisible) {
            drawCentered(displayFont, gameTitleLabel, StoryUiConstants.STORY_TITLE_GAME_TOP_Y,
                         StoryUiConstants.STORY_TITLE_GAME_TEXT_SIZE,
                         StoryUiConstants.STORY_TITLE_GAME_R, StoryUiConstants.STORY_TITLE_GAME_G,
                         StoryUiConstants.STORY_TITLE_GAME_B, fade);
            drawCentered(gameTaglineLabel, StoryUiConstants.STORY_TITLE_TAGLINE_TOP_Y,
                         StoryUiConstants.STORY_TITLE_TAGLINE_TEXT_SIZE,
                         StoryUiConstants.STORY_TITLE_TAGLINE_R,
                         StoryUiConstants.STORY_TITLE_TAGLINE_G,
                         StoryUiConstants.STORY_TITLE_TAGLINE_B, fade);
        }
        if (menuVisible) {
            drawMenuLabels(menu, StoryUiConstants.STORY_TITLE_MENU_TOP_Y, menuFade);
        }
        endBatch();

        if (menu.isConfirmOpen()) drawConfirmation(camera, menu, fade, true);
    }

    /** The title rule sits one UI_SCREEN_HEADER_RULE_GAP under the title's glyph bottom. */
    private static float titleRuleY() {
        return StoryUiConstants.STORY_TITLE_GAME_TOP_Y
                - ArtDirection.PIXEL_FONT_TARGET_CAP_HEIGHT * StoryUiConstants.STORY_TITLE_GAME_TEXT_SIZE
                - ArtDirection.UI_SCREEN_HEADER_RULE_GAP;
    }

    /**
     * The status lines the launch screen borrows from the boot card, printing one by one.
     *
     * <p>The block is anchored from its BOTTOM: the FIRST-PRINT notice is four lines and every other
     * card's is two, and both must end just above the title rather than leaving a hole under the
     * short one.  The offset uses the card's TOTAL line count, not the revealed count, so the lines
     * print downwards into a fixed block instead of sliding as they arrive.
     */
    private void drawLaunchStatusLines(float fade) {
        String[] lines      = titleScreenSystem.getStatusLines();
        int      lineCount  = titleScreenSystem.getStatusLineCount();
        int      printCount = Math.min(titleScreenSystem.getRevealedStatusLineCount(), lineCount);
        float lineTopY = StoryUiConstants.STORY_TITLE_STATUS_TOP_Y
                - (StoryUiConstants.STORY_TITLE_STATUS_LINE_COUNT - lineCount)
                        * StoryUiConstants.STORY_TITLE_STATUS_LINE_PITCH;
        for (int lineIndex = 0; lineIndex < printCount; lineIndex++) {
            drawCentered(lines[lineIndex], lineTopY,
                         StoryUiConstants.STORY_TITLE_STATUS_TEXT_SIZE,
                         StoryUiConstants.STORY_BODY_R, StoryUiConstants.STORY_BODY_G,
                         StoryUiConstants.STORY_BODY_B, fade);
            lineTopY -= StoryUiConstants.STORY_TITLE_STATUS_LINE_PITCH;
        }
    }

    // -------------------------------------------------------------------------
    // 2. The pause / settings menus (drawn over a frozen floor)
    // -------------------------------------------------------------------------

    /**
     * Draws a menu screen with a heading over the frozen world: the pause panel and the settings
     * screen are the same object with different rows.
     *
     * @param headerStringId localisation id of the heading ("SUIT PAUSED", "SETTINGS")
     * @param menu           the rows to draw, and whether a confirmation is over them
     */
    public void renderMenu(OrthographicCamera camera, String headerStringId, FramingMenu menu) {
        renderMenu(camera, headerStringId, menu, false);
    }

    /**
     * As above; {@code overLaunchScreen} is true for the settings screen opened from the title, whose backdrop
     * is the launch screen's own blackout instead of the in-run scrim — the title's text must not show through
     * the rows and the header (on the black there is no room to see, so the lighter scrim only exposed it).
     */
    public void renderMenu(OrthographicCamera camera, String headerStringId, FramingMenu menu,
                           boolean overLaunchScreen) {
        if (menu == null || strings == null) return;
        boolean rowsVisible = !menu.isConfirmOpen();

        // R6: the suit's own menu — one scrim, an R1 header over the rows (left-aligned on the row column, its
        // amber-ticked rule the column's width), SUIT plates, and SUIT_AMBER_DIM brackets round header + rows
        // (there is no single outer panel, so the brackets frame the content's bounding rect, order 5).
        float menuX = StoryUiConstants.STORY_FRAME_MENU_X;
        float menuWidth = StoryUiConstants.STORY_FRAME_MENU_WIDTH;
        beginShapes(camera);
        drawBackdrop(overLaunchScreen ? StoryUiConstants.STORY_FRAME_BACKDROP_ALPHA
                                      : StoryUiConstants.STORY_FRAME_SCRIM_ALPHA);
        UiKit.screenHeaderRule(shapes, menuX, UiKit.screenHeaderRuleY(StoryUiConstants.STORY_FRAME_HEADER_TOP_Y),
                               menuWidth, 1f);
        if (rowsVisible) {
            drawMenuPlates(menu, StoryUiConstants.STORY_FRAME_MENU_TOP_Y, 1f, false);
            float margin = ArtDirection.UI_SCREEN_FRAME_MARGIN;
            float frameBottom = menuRowBottomY(menu.getRowCount() - 1, StoryUiConstants.STORY_FRAME_MENU_TOP_Y)
                    - margin;
            float frameTop = StoryUiConstants.STORY_FRAME_HEADER_TOP_Y + margin;
            UiKit.brackets(shapes, menuX - margin, frameBottom, menuWidth + margin * 2f, frameTop - frameBottom,
                           ArtDirection.SUIT_AMBER_DIM_RED, ArtDirection.SUIT_AMBER_DIM_GREEN,
                           ArtDirection.SUIT_AMBER_DIM_BLUE, 1f);
        }
        endShapes();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        UiKit.screenHeaderTitle(batch, displayFont, strings.get(headerStringId), menuX,
                                StoryUiConstants.STORY_FRAME_HEADER_TOP_Y);
        if (rowsVisible) drawMenuLabels(menu, StoryUiConstants.STORY_FRAME_MENU_TOP_Y, 1f);
        endBatch();

        if (menu.isConfirmOpen()) drawConfirmation(camera, menu, 1f, false);
    }

    // -------------------------------------------------------------------------
    // 3. The way off a terminal ending card
    // -------------------------------------------------------------------------

    /**
     * Draws the dim RETURN plate over an ending card that authorises no reprint (order-3 draws no
     * CONTINUE on those, and that absence is the ending).  It stands in the CONTINUE's rect so the
     * thumb finds it, and is deliberately dimmer: this is a way out of a finished game, not a way
     * back into a run.
     */
    public void renderTerminalReturn(OrthographicCamera camera, float fade) {
        if (strings == null) return;
        float clampedFade = MathUtils.clamp(fade, 0f, 1f);

        // R5: a DISABLED-styled FACILITY plate (the machine's, and deliberately quieter than a CONTINUE).
        beginShapes(camera);
        drawFacilityPlate(StoryUiConstants.STORY_BOOT_CONTINUE_X, StoryUiConstants.STORY_BOOT_CONTINUE_Y,
                          StoryUiConstants.STORY_BOOT_CONTINUE_WIDTH,
                          StoryUiConstants.STORY_BOOT_CONTINUE_HEIGHT, PlateState.DISABLED, clampedFade);
        endShapes();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.getData().setScale(StoryUiConstants.STORY_BOOT_CONTINUE_TEXT_SIZE);
        drawCenteredInPlate(returnLabel, StoryUiConstants.STORY_BOOT_CONTINUE_X,
                            StoryUiConstants.STORY_BOOT_CONTINUE_Y,
                            StoryUiConstants.STORY_BOOT_CONTINUE_WIDTH,
                            StoryUiConstants.STORY_BOOT_CONTINUE_HEIGHT,
                            ArtDirection.UI_TEXT_SECONDARY_RED, ArtDirection.UI_TEXT_SECONDARY_GREEN,
                            ArtDirection.UI_TEXT_SECONDARY_BLUE, clampedFade);
        endBatch();
    }

    // -------------------------------------------------------------------------
    // 4. The confirmation ("this wipes…", "abandon this instance?")
    // -------------------------------------------------------------------------

    /**
     * R6: the confirmation takes the ink of the screen that opened it — FACILITY from the launch screen, SUIT
     * from the pause menu.  A kit panel with brackets in that ink; PROCEED is SELECTED in it, CANCEL is IDLE.
     */
    private void drawConfirmation(OrthographicCamera camera, FramingMenu menu, float fade, boolean facilityInk) {
        wrapConfirmPrompt(menu.getConfirmPromptStringId());
        float inkRed   = facilityInk ? ArtDirection.FACILITY_CYAN_RED   : ArtDirection.SUIT_AMBER_RED;
        float inkGreen = facilityInk ? ArtDirection.FACILITY_CYAN_GREEN : ArtDirection.SUIT_AMBER_GREEN;
        float inkBlue  = facilityInk ? ArtDirection.FACILITY_CYAN_BLUE  : ArtDirection.SUIT_AMBER_BLUE;

        beginShapes(camera);
        float panelX = StoryUiConstants.STORY_FRAME_CONFIRM_X, panelY = StoryUiConstants.STORY_FRAME_CONFIRM_Y;
        float panelWidth = StoryUiConstants.STORY_FRAME_CONFIRM_WIDTH;
        float panelHeight = StoryUiConstants.STORY_FRAME_CONFIRM_HEIGHT;
        UiKit.panel(shapes, panelX, panelY, panelWidth, panelHeight, ArtDirection.UI_CHAMFER,
                    ArtDirection.UI_PANEL_RED, ArtDirection.UI_PANEL_GREEN, ArtDirection.UI_PANEL_BLUE,
                    ArtDirection.UI_PANEL_ALPHA_SCREEN * fade,
                    ArtDirection.UI_LINE_RED, ArtDirection.UI_LINE_GREEN, ArtDirection.UI_LINE_BLUE, fade,
                    ArtDirection.UI_BORDER_THICKNESS);
        UiKit.brackets(shapes, panelX, panelY, panelWidth, panelHeight, inkRed, inkGreen, inkBlue, fade);
        UiKit.plate(shapes, StoryUiConstants.STORY_FRAME_CONFIRM_YES_X, StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_Y,
                    StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_WIDTH,
                    StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_HEIGHT,
                    ArtDirection.UI_PLATE_CHAMFER, ArtDirection.UI_BRACKET_LENGTH, PlateState.SELECTED,
                    StoryUiConstants.STORY_FRAME_MENU_FILL_ALPHA, inkRed, inkGreen, inkBlue, fade);
        if (facilityInk) {
            drawFacilityPlate(StoryUiConstants.STORY_FRAME_CONFIRM_NO_X, StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_Y,
                              StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_WIDTH,
                              StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_HEIGHT, PlateState.IDLE, fade);
        } else {
            drawSuitPlate(StoryUiConstants.STORY_FRAME_CONFIRM_NO_X, StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_Y,
                          StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_WIDTH,
                          StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_HEIGHT, PlateState.IDLE, fade);
        }
        endShapes();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.getData().setScale(StoryUiConstants.STORY_FRAME_CONFIRM_TEXT_SIZE);
        float lineTopY = StoryUiConstants.STORY_FRAME_CONFIRM_TOP_Y
                - StoryUiConstants.STORY_PANEL_PADDING - StoryUiConstants.STORY_CHIP_TO_BODY_GAP;
        for (int lineIndex = 0; lineIndex < confirmLineCount; lineIndex++) {
            layout.setText(font, confirmLines[lineIndex]);
            drawText(font, confirmLines[lineIndex],
                     Constants.WORLD_WIDTH / 2f - layout.width / 2f, lineTopY,
                     StoryUiConstants.STORY_BODY_R, StoryUiConstants.STORY_BODY_G,
                     StoryUiConstants.STORY_BODY_B, fade);
            lineTopY -= StoryUiConstants.STORY_BODY_LINE_PITCH;
        }
        font.getData().setScale(StoryUiConstants.STORY_FRAME_MENU_TEXT_SIZE);
        drawCenteredInPlate(confirmYesLabel, StoryUiConstants.STORY_FRAME_CONFIRM_YES_X,
                            StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_Y,
                            StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_WIDTH,
                            StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_HEIGHT, fade);
        drawCenteredInPlate(confirmNoLabel, StoryUiConstants.STORY_FRAME_CONFIRM_NO_X,
                            StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_Y,
                            StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_WIDTH,
                            StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_HEIGHT, fade);
        endBatch();
    }

    /**
     * Wraps the question ONCE per different question.  A confirmation is opened by a tap and then
     * sits there, so this runs on the tap and never again while it is up.
     */
    private void wrapConfirmPrompt(String confirmStringId) {
        if (confirmStringId == null || confirmStringId.equals(wrappedConfirmStringId)) return;
        wrappedConfirmStringId = confirmStringId;
        List<String> wrapped = StoryText.wrapToMaxChars(strings.get(confirmStringId),
                                                        StoryUiConstants.STORY_LINE_MAX_CHARS);
        confirmLineCount = Math.min(wrapped.size(), confirmLines.length);
        for (int lineIndex = 0; lineIndex < confirmLineCount; lineIndex++) {
            confirmLines[lineIndex] = wrapped.get(lineIndex);
        }
    }

    // -------------------------------------------------------------------------
    // Menu drawing (shared by all three screens)
    // -------------------------------------------------------------------------

    /**
     * R6: one plate per row in the screen's ink — FACILITY on the launch screen, where the first row (BEGIN /
     * CONTINUE DESCENT) is SELECTED, SUIT on the pause and settings screens.  A pressed row is PRESSED, an
     * unavailable one DISABLED.
     */
    private void drawMenuPlates(FramingMenu menu, float menuTopY, float fade, boolean facilityInk) {
        for (int rowIndex = 0; rowIndex < menu.getRowCount(); rowIndex++) {
            PlateState state;
            if (!menu.isRowEnabled(rowIndex))               state = PlateState.DISABLED;
            else if (menu.getPressedIndex() == rowIndex)    state = PlateState.PRESSED;
            else if (facilityInk && rowIndex == 0)          state = PlateState.SELECTED;
            else                                            state = PlateState.IDLE;
            float rowBottomY = menuRowBottomY(rowIndex, menuTopY);
            if (facilityInk) {
                drawFacilityPlate(StoryUiConstants.STORY_FRAME_MENU_X, rowBottomY,
                                  StoryUiConstants.STORY_FRAME_MENU_WIDTH,
                                  StoryUiConstants.STORY_FRAME_MENU_ROW_HEIGHT, state, fade);
            } else {
                drawSuitPlate(StoryUiConstants.STORY_FRAME_MENU_X, rowBottomY,
                              StoryUiConstants.STORY_FRAME_MENU_WIDTH,
                              StoryUiConstants.STORY_FRAME_MENU_ROW_HEIGHT, state, fade);
            }
        }
    }

    private void drawMenuLabels(FramingMenu menu, float menuTopY, float fade) {
        for (int rowIndex = 0; rowIndex < menu.getRowCount(); rowIndex++) {
            float rowBottomY = menuRowBottomY(rowIndex, menuTopY);
            boolean enabled  = menu.isRowEnabled(rowIndex);
            // A disabled row's text is secondary (still readable), never the disabled grey.
            float textRed    = enabled ? StoryUiConstants.STORY_BODY_R : ArtDirection.UI_TEXT_SECONDARY_RED;
            float textGreen  = enabled ? StoryUiConstants.STORY_BODY_G : ArtDirection.UI_TEXT_SECONDARY_GREEN;
            float textBlue   = enabled ? StoryUiConstants.STORY_BODY_B : ArtDirection.UI_TEXT_SECONDARY_BLUE;
            String valueStringId = menu.getValueStringId(rowIndex);

            font.getData().setScale(StoryUiConstants.STORY_FRAME_MENU_TEXT_SIZE);
            if (valueStringId == null) {
                // A plain command row: one centred label, because that is what a button looks like.
                drawCenteredInPlate(strings.get(menu.getLabelStringId(rowIndex)),
                                    StoryUiConstants.STORY_FRAME_MENU_X, rowBottomY,
                                    StoryUiConstants.STORY_FRAME_MENU_WIDTH,
                                    StoryUiConstants.STORY_FRAME_MENU_ROW_HEIGHT,
                                    textRed, textGreen, textBlue, fade);
                continue;
            }
            // A settings row: NAME on the left, its current VALUE on the right, so the row reads as
            // a state rather than as a command.
            String nameText  = strings.get(menu.getLabelStringId(rowIndex));
            String valueText = strings.get(valueStringId);
            layout.setText(font, nameText);
            float textTopY = rowBottomY
                    + (StoryUiConstants.STORY_FRAME_MENU_ROW_HEIGHT + layout.height) / 2f;
            drawText(font, nameText,
                     StoryUiConstants.STORY_FRAME_MENU_X + StoryUiConstants.STORY_PANEL_PADDING,
                     textTopY, textRed, textGreen, textBlue, fade);

            // The value is the suit's current STATE, so it reads in SUIT_AMBER (the active-state role).
            font.getData().setScale(StoryUiConstants.STORY_FRAME_MENU_VALUE_TEXT_SIZE);
            layout.setText(font, valueText);
            drawText(font, valueText,
                     StoryUiConstants.STORY_FRAME_MENU_X + StoryUiConstants.STORY_FRAME_MENU_WIDTH
                             - StoryUiConstants.STORY_PANEL_PADDING - layout.width,
                     textTopY,
                     ArtDirection.SUIT_AMBER_RED, ArtDirection.SUIT_AMBER_GREEN, ArtDirection.SUIT_AMBER_BLUE, fade);
        }
    }

    // -------------------------------------------------------------------------
    // Hit rects — the engine unprojects a touch and asks these.  Statics, so drawing and input
    // read the SAME geometry and can never drift apart.
    // -------------------------------------------------------------------------

    /** Bottom edge of one menu row, given the screen's top anchor. */
    public static float menuRowBottomY(int rowIndex, float menuTopY) {
        return menuTopY - rowIndex * StoryUiConstants.STORY_FRAME_MENU_ROW_PITCH
                - StoryUiConstants.STORY_FRAME_MENU_ROW_HEIGHT;
    }

    /**
     * Which menu row contains this world point, or -1 for none (including the gaps between rows —
     * a tap that lands in a gap must do nothing rather than pick its nearest neighbour).
     */
    public static int menuRowIndexAt(float worldX, float worldY, int rowCount, float menuTopY) {
        if (worldX < StoryUiConstants.STORY_FRAME_MENU_X
                || worldX > StoryUiConstants.STORY_FRAME_MENU_X
                        + StoryUiConstants.STORY_FRAME_MENU_WIDTH) {
            return -1;
        }
        float distanceBelowTop = menuTopY - worldY;
        if (distanceBelowTop < 0f) return -1;
        int rowIndex = (int) (distanceBelowTop / StoryUiConstants.STORY_FRAME_MENU_ROW_PITCH);
        if (rowIndex < 0 || rowIndex >= rowCount) return -1;
        float offsetInRow = distanceBelowTop - rowIndex * StoryUiConstants.STORY_FRAME_MENU_ROW_PITCH;
        return offsetInRow <= StoryUiConstants.STORY_FRAME_MENU_ROW_HEIGHT ? rowIndex : -1;
    }

    /** True when the point is on the confirmation's PROCEED plate. */
    public static boolean isInsideConfirmYes(float worldX, float worldY) {
        return isInsideRect(worldX, worldY, StoryUiConstants.STORY_FRAME_CONFIRM_YES_X,
                            StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_Y,
                            StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_WIDTH,
                            StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_HEIGHT);
    }

    /** True when the point is on the confirmation's CANCEL plate — always present, always safe. */
    public static boolean isInsideConfirmNo(float worldX, float worldY) {
        return isInsideRect(worldX, worldY, StoryUiConstants.STORY_FRAME_CONFIRM_NO_X,
                            StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_Y,
                            StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_WIDTH,
                            StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_HEIGHT);
    }

    /** True when the point is on the terminal ending's RETURN plate (the CONTINUE's rect). */
    public static boolean isInsideReturnButton(float worldX, float worldY) {
        return isInsideRect(worldX, worldY, StoryUiConstants.STORY_BOOT_CONTINUE_X,
                            StoryUiConstants.STORY_BOOT_CONTINUE_Y,
                            StoryUiConstants.STORY_BOOT_CONTINUE_WIDTH,
                            StoryUiConstants.STORY_BOOT_CONTINUE_HEIGHT);
    }

    private static boolean isInsideRect(float worldX, float worldY,
                                        float rectX, float rectY, float rectWidth, float rectHeight) {
        return worldX >= rectX && worldX <= rectX + rectWidth
            && worldY >= rectY && worldY <= rectY + rectHeight;
    }

    // -------------------------------------------------------------------------
    // Drawing primitives (the boot card's plate language, so every framing screen matches)
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
        // Restore shared font state for every other renderer.
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        displayFont.getData().setScale(1f);
        displayFont.setColor(Color.WHITE);
        batch.end();
    }

    private void drawBackdrop(float alpha) {
        shapes.setColor(StoryUiConstants.STORY_FRAME_BACKDROP_R,
                        StoryUiConstants.STORY_FRAME_BACKDROP_G,
                        StoryUiConstants.STORY_FRAME_BACKDROP_B, alpha);
        shapes.rect(0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);
    }

    /**
     * A FACILITY plate: a card inked FACILITY_CYAN_DIM (IDLE / DISABLED), lit FACILITY_CYAN when PRESSED or
     * SELECTED, so the machine's controls read as the machine's even before they are touched.
     */
    private void drawFacilityPlate(float plateX, float plateY, float plateWidth, float plateHeight,
                                   PlateState state, float fade) {
        boolean lit = state == PlateState.PRESSED || state == PlateState.SELECTED;
        UiKit.card(shapes, plateX, plateY, plateWidth, plateHeight, ArtDirection.UI_PLATE_CHAMFER,
                   lit ? ArtDirection.FACILITY_CYAN_RED : ArtDirection.FACILITY_CYAN_DIM_RED,
                   lit ? ArtDirection.FACILITY_CYAN_GREEN : ArtDirection.FACILITY_CYAN_DIM_GREEN,
                   lit ? ArtDirection.FACILITY_CYAN_BLUE : ArtDirection.FACILITY_CYAN_DIM_BLUE, state, fade);
    }

    /** A SUIT plate: the kit plate in its state, PRESSED / SELECTED in SUIT_AMBER. */
    private void drawSuitPlate(float plateX, float plateY, float plateWidth, float plateHeight,
                               PlateState state, float fade) {
        UiKit.plate(shapes, plateX, plateY, plateWidth, plateHeight, ArtDirection.UI_PLATE_CHAMFER,
                    ArtDirection.UI_BRACKET_LENGTH, state, StoryUiConstants.STORY_FRAME_MENU_FILL_ALPHA,
                    ArtDirection.SUIT_AMBER_RED, ArtDirection.SUIT_AMBER_GREEN, ArtDirection.SUIT_AMBER_BLUE, fade);
    }

    /** Draws one line centred on the screen's vertical axis at the given text scale. */
    private void drawCentered(String text, float textTopY, float textScale,
                              float red, float green, float blue, float fade) {
        drawCentered(font, text, textTopY, textScale, red, green, blue, fade);
    }

    /** {@link #drawCentered(String, float, float, float, float, float, float)} in a given face. */
    private void drawCentered(BitmapFont textFont, String text, float textTopY, float textScale,
                              float red, float green, float blue, float fade) {
        textFont.getData().setScale(textScale);
        layout.setText(textFont, text);
        drawText(textFont, text, Constants.WORLD_WIDTH / 2f - layout.width / 2f, textTopY,
                 red, green, blue, fade);
    }

    /**
     * Centres a label inside a plate.  {@code BitmapFont.draw} anchors a line by its TOP, so a glyph
     * box of height h centred in a plate spanning [Y, Y+H] has its top at Y + (H + h) / 2 — equal
     * margins above and below.  The caller sets the font scale first.
     */
    private void drawCenteredInPlate(String text, float plateX, float plateY,
                                     float plateWidth, float plateHeight, float fade) {
        drawCenteredInPlate(text, plateX, plateY, plateWidth, plateHeight,
                            StoryUiConstants.STORY_BODY_R, StoryUiConstants.STORY_BODY_G,
                            StoryUiConstants.STORY_BODY_B, fade);
    }

    /** As above, in a given colour (a disabled row, the terminal RETURN). */
    private void drawCenteredInPlate(String text, float plateX, float plateY, float plateWidth, float plateHeight,
                                     float red, float green, float blue, float fade) {
        layout.setText(font, text);
        drawText(font, text, plateX + (plateWidth - layout.width) / 2f,
                 plateY + (plateHeight + layout.height) / 2f, red, green, blue, fade);
    }

    /** Draws one string, unshadowed: every framing screen sits on black or a scrim. */
    private void drawText(BitmapFont textFont, String text, float textX, float textTopY,
                          float red, float green, float blue, float fade) {
        textFont.setColor(red, green, blue, fade);
        textFont.draw(batch, text, textX, textTopY);
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
        displayFont.dispose();
    }
}
