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
import ge.tbegvadze.toon3d.narrative.Speaker;
import ge.tbegvadze.toon3d.narrative.StoryStrings;
import ge.tbegvadze.toon3d.narrative.StoryText;
import ge.tbegvadze.toon3d.narrative.TitleScreenSystem;
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
 * <p>Everything is procedural — one {@link ShapeRenderer}, one {@link SpriteBatch}, one
 * {@link BitmapFont}, no textures — and all three are disposed in {@link #dispose()}.  The only
 * per-frame work is measuring already-resolved labels through the shared {@link GlyphLayout} (the
 * established idiom in {@code CodexOverlayRenderer}); the confirmation's prose is wrapped once, when
 * its question changes, never per frame.
 */
public final class FramingScreenRenderer implements Disposable {

    private final ShapeRenderer shapes;
    private final SpriteBatch   batch;
    private final BitmapFont    font;
    private final GlyphLayout   layout;
    private final Color         scratchColor = new Color();

    private StoryStrings      strings;
    private TitleScreenSystem titleScreenSystem;

    // Resolved once in setStrings() — the launch screen's fixed chrome.
    private String gameTitleLabel    = "";
    private String gameTaglineLabel  = "";
    private String confirmYesLabel   = "";
    private String confirmNoLabel    = "";
    private String returnLabel       = "";
    private String deathStrokeLabel  = "";

    // The confirmation's question, wrapped once per DIFFERENT question rather than per frame.
    private final String[] confirmLines = new String[StoryUiConstants.STORY_FRAME_CONFIRM_MAX_LINES];
    private int            confirmLineCount;
    private String         wrappedConfirmStringId;

    public FramingScreenRenderer() {
        this.shapes = new ShapeRenderer();
        this.batch  = new SpriteBatch();
        this.font   = new BitmapFont();
        this.font.getData().markupEnabled = false;
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
        font.getData().setScale(StoryUiConstants.STORY_DEATH_STROKE_TEXT_SIZE);
        layout.setText(font, deathStrokeLabel);
        drawWithShadow(deathStrokeLabel,
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
        if (menuVisible) {
            drawMenuPlates(menu, StoryUiConstants.STORY_TITLE_MENU_TOP_Y, menuFade);
        }
        endShapes();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawLaunchStatusLines(fade);
        if (titleVisible) {
            drawCentered(gameTitleLabel, StoryUiConstants.STORY_TITLE_GAME_TOP_Y,
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

        if (menu.isConfirmOpen()) drawConfirmation(camera, menu, fade);
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
        if (menu == null || strings == null) return;
        boolean rowsVisible = !menu.isConfirmOpen();

        beginShapes(camera);
        drawBackdrop(StoryUiConstants.STORY_FRAME_SCRIM_ALPHA);
        if (rowsVisible) drawMenuPlates(menu, StoryUiConstants.STORY_FRAME_MENU_TOP_Y, 1f);
        endShapes();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawCentered(strings.get(headerStringId), StoryUiConstants.STORY_FRAME_HEADER_TOP_Y,
                     StoryUiConstants.STORY_FRAME_HEADER_TEXT_SIZE,
                     StoryUiConstants.STORY_BODY_R, StoryUiConstants.STORY_BODY_G,
                     StoryUiConstants.STORY_BODY_B, 1f);
        if (rowsVisible) drawMenuLabels(menu, StoryUiConstants.STORY_FRAME_MENU_TOP_Y, 1f);
        endBatch();

        if (menu.isConfirmOpen()) drawConfirmation(camera, menu, 1f);
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

        beginShapes(camera);
        drawPlate(StoryUiConstants.STORY_BOOT_CONTINUE_X, StoryUiConstants.STORY_BOOT_CONTINUE_Y,
                  StoryUiConstants.STORY_BOOT_CONTINUE_WIDTH,
                  StoryUiConstants.STORY_BOOT_CONTINUE_HEIGHT,
                  StoryUiConstants.STORY_FRAME_RETURN_FILL_ALPHA, clampedFade);
        endShapes();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.getData().setScale(StoryUiConstants.STORY_BOOT_CONTINUE_TEXT_SIZE);
        drawCenteredInPlate(returnLabel, StoryUiConstants.STORY_BOOT_CONTINUE_X,
                            StoryUiConstants.STORY_BOOT_CONTINUE_Y,
                            StoryUiConstants.STORY_BOOT_CONTINUE_WIDTH,
                            StoryUiConstants.STORY_BOOT_CONTINUE_HEIGHT, clampedFade);
        endBatch();
    }

    // -------------------------------------------------------------------------
    // 4. The confirmation ("this wipes…", "abandon this instance?")
    // -------------------------------------------------------------------------

    private void drawConfirmation(OrthographicCamera camera, FramingMenu menu, float fade) {
        wrapConfirmPrompt(menu.getConfirmPromptStringId());

        beginShapes(camera);
        drawPanel(StoryUiConstants.STORY_FRAME_CONFIRM_X, StoryUiConstants.STORY_FRAME_CONFIRM_Y,
                  StoryUiConstants.STORY_FRAME_CONFIRM_WIDTH,
                  StoryUiConstants.STORY_FRAME_CONFIRM_HEIGHT, fade);
        drawPlate(StoryUiConstants.STORY_FRAME_CONFIRM_YES_X,
                  StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_Y,
                  StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_WIDTH,
                  StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_HEIGHT,
                  StoryUiConstants.STORY_FRAME_MENU_FILL_ALPHA, fade);
        drawPlate(StoryUiConstants.STORY_FRAME_CONFIRM_NO_X,
                  StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_Y,
                  StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_WIDTH,
                  StoryUiConstants.STORY_FRAME_CONFIRM_BUTTON_HEIGHT,
                  StoryUiConstants.STORY_FRAME_MENU_FILL_ALPHA, fade);
        endShapes();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.getData().setScale(StoryUiConstants.STORY_FRAME_MENU_TEXT_SIZE);
        float lineTopY = StoryUiConstants.STORY_FRAME_CONFIRM_TOP_Y
                - StoryUiConstants.STORY_PANEL_PADDING - StoryUiConstants.STORY_CHIP_TO_BODY_GAP;
        for (int lineIndex = 0; lineIndex < confirmLineCount; lineIndex++) {
            layout.setText(font, confirmLines[lineIndex]);
            drawWithShadow(confirmLines[lineIndex],
                           Constants.WORLD_WIDTH / 2f - layout.width / 2f, lineTopY,
                           StoryUiConstants.STORY_BODY_R, StoryUiConstants.STORY_BODY_G,
                           StoryUiConstants.STORY_BODY_B, fade);
            lineTopY -= StoryUiConstants.STORY_BODY_LINE_PITCH;
        }
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

    private void drawMenuPlates(FramingMenu menu, float menuTopY, float fade) {
        for (int rowIndex = 0; rowIndex < menu.getRowCount(); rowIndex++) {
            float fillAlpha = menu.getPressedIndex() == rowIndex
                    ? StoryUiConstants.STORY_FRAME_MENU_PRESSED_ALPHA
                    : StoryUiConstants.STORY_FRAME_MENU_FILL_ALPHA;
            float rowFade = menu.isRowEnabled(rowIndex)
                    ? fade
                    : fade * StoryUiConstants.STORY_FRAME_MENU_DISABLED_ALPHA;
            drawPlate(StoryUiConstants.STORY_FRAME_MENU_X, menuRowBottomY(rowIndex, menuTopY),
                      StoryUiConstants.STORY_FRAME_MENU_WIDTH,
                      StoryUiConstants.STORY_FRAME_MENU_ROW_HEIGHT, fillAlpha, rowFade);
        }
    }

    private void drawMenuLabels(FramingMenu menu, float menuTopY, float fade) {
        for (int rowIndex = 0; rowIndex < menu.getRowCount(); rowIndex++) {
            float rowBottomY = menuRowBottomY(rowIndex, menuTopY);
            float rowFade    = menu.isRowEnabled(rowIndex)
                    ? fade
                    : fade * StoryUiConstants.STORY_FRAME_MENU_DISABLED_ALPHA;
            String valueStringId = menu.getValueStringId(rowIndex);

            font.getData().setScale(StoryUiConstants.STORY_FRAME_MENU_TEXT_SIZE);
            if (valueStringId == null) {
                // A plain command row: one centred label, because that is what a button looks like.
                drawCenteredInPlate(strings.get(menu.getLabelStringId(rowIndex)),
                                    StoryUiConstants.STORY_FRAME_MENU_X, rowBottomY,
                                    StoryUiConstants.STORY_FRAME_MENU_WIDTH,
                                    StoryUiConstants.STORY_FRAME_MENU_ROW_HEIGHT, rowFade);
                continue;
            }
            // A settings row: NAME on the left, its current VALUE on the right, so the row reads as
            // a state rather than as a command.
            String nameText  = strings.get(menu.getLabelStringId(rowIndex));
            String valueText = strings.get(valueStringId);
            layout.setText(font, nameText);
            float textTopY = rowBottomY
                    + (StoryUiConstants.STORY_FRAME_MENU_ROW_HEIGHT + layout.height) / 2f;
            drawWithShadow(nameText,
                           StoryUiConstants.STORY_FRAME_MENU_X + StoryUiConstants.STORY_PANEL_PADDING,
                           textTopY, StoryUiConstants.STORY_BODY_R, StoryUiConstants.STORY_BODY_G,
                           StoryUiConstants.STORY_BODY_B, rowFade);

            font.getData().setScale(StoryUiConstants.STORY_FRAME_MENU_VALUE_TEXT_SIZE);
            layout.setText(font, valueText);
            int speakerIndex = Speaker.AI.ordinal();
            drawWithShadow(valueText,
                           StoryUiConstants.STORY_FRAME_MENU_X + StoryUiConstants.STORY_FRAME_MENU_WIDTH
                                   - StoryUiConstants.STORY_PANEL_PADDING - layout.width,
                           textTopY,
                           StoryUiConstants.SPEAKER_ACCENT_R[speakerIndex],
                           StoryUiConstants.SPEAKER_ACCENT_G[speakerIndex],
                           StoryUiConstants.SPEAKER_ACCENT_B[speakerIndex], rowFade);
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
        batch.end();
    }

    private void drawBackdrop(float alpha) {
        shapes.setColor(StoryUiConstants.STORY_FRAME_BACKDROP_R,
                        StoryUiConstants.STORY_FRAME_BACKDROP_G,
                        StoryUiConstants.STORY_FRAME_BACKDROP_B, alpha);
        shapes.rect(0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);
    }

    /** A dark panel with an accent border — the confirmation's body. */
    private void drawPanel(float panelX, float panelY, float panelWidth, float panelHeight, float fade) {
        int speakerIndex = Speaker.AI.ordinal();
        float border     = StoryUiConstants.STORY_PANEL_BORDER_THICKNESS;
        shapes.setColor(StoryUiConstants.SPEAKER_ACCENT_R[speakerIndex],
                        StoryUiConstants.SPEAKER_ACCENT_G[speakerIndex],
                        StoryUiConstants.SPEAKER_ACCENT_B[speakerIndex],
                        StoryUiConstants.STORY_PANEL_BORDER_ALPHA * fade);
        shapes.rect(panelX, panelY, panelWidth, panelHeight);
        shapes.setColor(StoryUiConstants.STORY_PANEL_BG_R, StoryUiConstants.STORY_PANEL_BG_G,
                        StoryUiConstants.STORY_PANEL_BG_B,
                        StoryUiConstants.STORY_PANEL_ALPHA * fade);
        shapes.rect(panelX + border, panelY + border,
                    panelWidth - border * 2f, panelHeight - border * 2f);
    }

    /** An accent border with a dim fill inset — the plate language of every story button. */
    private void drawPlate(float plateX, float plateY, float plateWidth, float plateHeight,
                           float fillAlpha, float fade) {
        int speakerIndex = Speaker.AI.ordinal();
        float accentRed   = StoryUiConstants.SPEAKER_ACCENT_R[speakerIndex];
        float accentGreen = StoryUiConstants.SPEAKER_ACCENT_G[speakerIndex];
        float accentBlue  = StoryUiConstants.SPEAKER_ACCENT_B[speakerIndex];
        float border      = StoryUiConstants.STORY_FRAME_MENU_BORDER;

        shapes.setColor(accentRed, accentGreen, accentBlue, fade);
        shapes.rect(plateX, plateY, plateWidth, plateHeight);
        shapes.setColor(accentRed * fillAlpha, accentGreen * fillAlpha, accentBlue * fillAlpha, fade);
        shapes.rect(plateX + border, plateY + border,
                    plateWidth - border * 2f, plateHeight - border * 2f);
    }

    /** Draws one line centred on the screen's vertical axis at the given text scale. */
    private void drawCentered(String text, float textTopY, float textScale,
                              float red, float green, float blue, float fade) {
        font.getData().setScale(textScale);
        layout.setText(font, text);
        drawWithShadow(text, Constants.WORLD_WIDTH / 2f - layout.width / 2f, textTopY,
                       red, green, blue, fade);
    }

    /**
     * Centres a label inside a plate.  {@code BitmapFont.draw} anchors a line by its TOP, so a glyph
     * box of height h centred in a plate spanning [Y, Y+H] has its top at Y + (H + h) / 2 — equal
     * margins above and below.  The caller sets the font scale first.
     */
    private void drawCenteredInPlate(String text, float plateX, float plateY,
                                     float plateWidth, float plateHeight, float fade) {
        layout.setText(font, text);
        drawWithShadow(text, plateX + (plateWidth - layout.width) / 2f,
                       plateY + (plateHeight + layout.height) / 2f,
                       StoryUiConstants.STORY_BODY_R, StoryUiConstants.STORY_BODY_G,
                       StoryUiConstants.STORY_BODY_B, fade);
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
