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
import ge.tbegvadze.toon3d.narrative.ExchangeSystem;
import ge.tbegvadze.toon3d.narrative.Speaker;
import ge.tbegvadze.toon3d.narrative.StoryStrings;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * Draws the EXCHANGE (Story UI order-4) — the blocking conversation panel: the speaker's prompt in
 * the order-1 story panel, and under it 2-3 full-width answer plates the player taps.  While it is
 * up the world is dimmed and frozen behind it, which is the whole signal: <em>we have paused for
 * this</em>.
 *
 * <p>This class is presentation ONLY.  Which exchange is open, what the answers say, which one the
 * thumb is currently on, and the fade fraction are all decided headlessly by {@link ExchangeSystem};
 * the renderer just asks it.  The panel itself is the shared order-1 {@link StoryPanelRenderer}, so
 * an exchange speaks exactly the same visual language as a bark or the boot card.
 *
 * <p>THUMB TARGETS: plates are {@code STORY_EXCHANGE_BUTTON_HEIGHT} tall and span the panel's full
 * width, stacked from a fixed top edge so the first answer is always in the same place.  A pressed
 * plate is drawn much brighter — a tap on an answer must be visibly acknowledged before the panel
 * changes, or a player who has just committed cannot tell whether the game heard them.
 *
 * <p>Nothing is allocated on the draw path: body and answer text arrive pre-resolved and pre-wrapped
 * from the system, answer labels are re-measured only when the text actually changes, and the
 * scratch {@link Color} / {@link GlyphLayout} are pre-allocated.  Owns a {@link StoryPanelRenderer},
 * a {@link ShapeRenderer}, a {@link SpriteBatch} and a {@link BitmapFont} — all disposed in
 * {@link #dispose()}.
 */
public final class StoryExchangeRenderer implements Renderable, Disposable {

    private final StoryPanelRenderer panelRenderer;
    private final ShapeRenderer      shapes;
    private final SpriteBatch        batch;
    private final BitmapFont         font;
    private final GlyphLayout        layout;
    private final Color              scratchColor = new Color();

    private ExchangeSystem exchangeSystem;
    private StoryAudio     storyAudio;

    /** Resolved label for the reply's acknowledge button (localisation rule: never a literal here). */
    private String acknowledgeLabel = "";

    // Cached label measurements — recomputed only when the text on a plate actually changes, so
    // render() never measures.  Parallel arrays indexed by button slot.
    private final String[] measuredLabels =
            new String[StoryUiConstants.STORY_EXCHANGE_MAX_OPTIONS];
    private final float[]  measuredLabelX =
            new float[StoryUiConstants.STORY_EXCHANGE_MAX_OPTIONS];
    private final float[]  measuredLabelHeight =
            new float[StoryUiConstants.STORY_EXCHANGE_MAX_OPTIONS];

    public StoryExchangeRenderer() {
        this.panelRenderer = new StoryPanelRenderer();
        this.shapes        = new ShapeRenderer();
        this.batch         = new SpriteBatch();
        this.font          = new BitmapFont();
        this.font.getData().markupEnabled = false;
        this.layout        = new GlyphLayout();
    }

    /**
     * Applies the order-6 accessibility settings (text size, reduced motion) to the shared story
     * panel this renderer draws the prompt with.  The answer plates keep their own fixed size: a
     * plate is a fixed-height thumb target, and its label is capped to one short line by the catalog.
     */
    public void applyAccessibilitySettings(float bodyTextScale, boolean reduceMotion) {
        panelRenderer.applyAccessibilitySettings(bodyTextScale, reduceMotion);
    }

    /** Binds the headless brain this renderer reads from.  Null hides the layer entirely. */
    public void setExchangeSystem(ExchangeSystem exchangeSystem) {
        this.exchangeSystem = exchangeSystem;
    }

    /**
     * Binds the world's shared story audio (order-7 Part D).  Null means silence — every cue here
     * is redundant with something the panel already shows.
     */
    public void setStoryAudio(StoryAudio storyAudio) {
        this.storyAudio = storyAudio;
    }

    /** Resolves the acknowledge-button label ONCE.  Call at construction time, not per frame. */
    public void setStrings(StoryStrings strings) {
        if (strings == null) return;
        acknowledgeLabel = strings.get(StoryUiConstants.STORY_EXCHANGE_CONTINUE_LABEL_ID);
    }

    /**
     * Plays the speaker sting for a block that just appeared — the prompt when the exchange opens,
     * the reply when the player answers.  Call from the update path (never from render) so audio is
     * a simulation side effect, not a draw-time one.
     */
    public void playPendingSpeakerSting() {
        if (exchangeSystem == null) return;
        Speaker appeared = exchangeSystem.consumeJustAppearedSpeaker();
        if (appeared != null && storyAudio != null) {
            storyAudio.playSpeakerSting(appeared);
        }
    }

    @Override
    public void render(OrthographicCamera camera) {
        if (exchangeSystem == null || !exchangeSystem.isActive()) return;
        float fade = MathUtils.clamp(exchangeSystem.getVisibleFraction(), 0f, 1f);
        int   buttonCount = visibleButtonCount();

        drawDimAndPlates(camera, fade, buttonCount);

        panelRenderer.drawPanel(camera,
                exchangeSystem.getActiveSpeaker(),
                exchangeSystem.getActiveSpeakerName(),
                exchangeSystem.getBodyLines(), exchangeSystem.getBodyLineCount(),
                StoryUiConstants.STORY_EXCHANGE_X, StoryUiConstants.STORY_EXCHANGE_PROMPT_Y,
                StoryUiConstants.STORY_EXCHANGE_WIDTH, StoryUiConstants.STORY_EXCHANGE_PROMPT_HEIGHT,
                fade, exchangeSystem.getElapsedSeconds());

        drawButtonLabels(camera, fade, buttonCount);
    }

    /**
     * How many plates are on screen: one per answer while the prompt is up, and a single acknowledge
     * plate while a reply is up.  Zero during the closing fade, when there is nothing left to tap.
     */
    private int visibleButtonCount() {
        if (exchangeSystem.isAwaitingChoice()) return exchangeSystem.getOptionCount();
        if (exchangeSystem.isShowingReply())   return 1;
        return 0;
    }

    /** The text on one plate: an answer while choosing, the acknowledge label while replying. */
    private String buttonLabel(int buttonIndex) {
        if (exchangeSystem.isAwaitingChoice()) return exchangeSystem.getOptionLines()[buttonIndex];
        return acknowledgeLabel;
    }

    /**
     * Pass 1 — the full-screen dim and every plate, all Filled shapes in one ShapeRenderer batch.
     * The dim is lighter than the boot card's near-blackout on purpose: the player must still see
     * the room they are standing in while they decide.
     */
    private void drawDimAndPlates(OrthographicCamera camera, float fade, int buttonCount) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        shapes.setColor(StoryUiConstants.STORY_EXCHANGE_DIM_R, StoryUiConstants.STORY_EXCHANGE_DIM_G,
                        StoryUiConstants.STORY_EXCHANGE_DIM_B,
                        StoryUiConstants.STORY_EXCHANGE_DIM_ALPHA * fade);
        shapes.rect(0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT);

        int pressedIndex = exchangeSystem.getPressedOptionIndex();
        for (int buttonIndex = 0; buttonIndex < buttonCount; buttonIndex++) {
            drawPlate(buttonIndex, buttonIndex == pressedIndex, fade);
        }

        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /** One answer plate: an accent border with a dim fill inset, brightened while pressed. */
    private void drawPlate(int buttonIndex, boolean pressed, float fade) {
        int   speakerIndex = Speaker.AI.ordinal();   // the player's own voice borrows ORA's amber
        float accentRed    = StoryUiConstants.SPEAKER_ACCENT_R[speakerIndex];
        float accentGreen  = StoryUiConstants.SPEAKER_ACCENT_G[speakerIndex];
        float accentBlue   = StoryUiConstants.SPEAKER_ACCENT_B[speakerIndex];
        float border       = StoryUiConstants.STORY_EXCHANGE_BUTTON_BORDER;
        float fill         = pressed ? StoryUiConstants.STORY_EXCHANGE_BUTTON_PRESSED_ALPHA
                                     : StoryUiConstants.STORY_EXCHANGE_BUTTON_FILL_ALPHA;
        float buttonBottom = buttonBottomY(buttonIndex);

        shapes.setColor(accentRed, accentGreen, accentBlue, fade);
        shapes.rect(StoryUiConstants.STORY_EXCHANGE_BUTTON_X, buttonBottom,
                    StoryUiConstants.STORY_CHOICE_BUTTON_WIDTH,
                    StoryUiConstants.STORY_EXCHANGE_BUTTON_HEIGHT);
        shapes.setColor(accentRed * fill, accentGreen * fill, accentBlue * fill, fade);
        shapes.rect(StoryUiConstants.STORY_EXCHANGE_BUTTON_X + border, buttonBottom + border,
                    StoryUiConstants.STORY_CHOICE_BUTTON_WIDTH - border * 2f,
                    StoryUiConstants.STORY_EXCHANGE_BUTTON_HEIGHT - border * 2f);
    }

    /** Pass 2 — the answer text, centred in each plate, in one SpriteBatch. */
    private void drawButtonLabels(OrthographicCamera camera, float fade, int buttonCount) {
        if (buttonCount <= 0) return;
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.getData().setScale(StoryUiConstants.STORY_EXCHANGE_OPTION_TEXT_SIZE);

        for (int buttonIndex = 0; buttonIndex < buttonCount; buttonIndex++) {
            String label = buttonLabel(buttonIndex);
            if (label == null || label.isEmpty()) continue;
            measureLabelIfChanged(buttonIndex, label);

            // Vertically centre the glyph box inside the plate.  BitmapFont.draw anchors a line by
            // its TOP, so for a box of height h inside a plate spanning [Y, Y+H]:
            //     top = Y + (H + h) / 2   -> equal margins above and below.
            float labelTopY = buttonBottomY(buttonIndex)
                    + (StoryUiConstants.STORY_EXCHANGE_BUTTON_HEIGHT + measuredLabelHeight[buttonIndex]) / 2f;
            drawWithShadow(label, measuredLabelX[buttonIndex], labelTopY, fade);
        }

        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    /**
     * Re-measures a plate's label only when its text actually changed, so the steady-state draw path
     * does no layout work.  The font scale is already set by the caller.
     */
    private void measureLabelIfChanged(int buttonIndex, String label) {
        if (label.equals(measuredLabels[buttonIndex])) return;
        layout.setText(font, label);
        measuredLabels[buttonIndex]      = label;
        measuredLabelX[buttonIndex]      = StoryUiConstants.STORY_EXCHANGE_BUTTON_X
                + (StoryUiConstants.STORY_CHOICE_BUTTON_WIDTH - layout.width) / 2f;
        measuredLabelHeight[buttonIndex] = layout.height;
    }

    /** Draws one label with a dark drop-shadow beneath it, matching the story panel's text. */
    private void drawWithShadow(String text, float textX, float textTopY, float fade) {
        float shadow = StoryUiConstants.STORY_TEXT_SHADOW_OFFSET;
        scratchColor.set(0f, 0f, 0f, StoryUiConstants.STORY_TEXT_SHADOW_ALPHA * fade);
        font.setColor(scratchColor);
        font.draw(batch, text, textX + shadow, textTopY - shadow);

        scratchColor.set(StoryUiConstants.STORY_BODY_R, StoryUiConstants.STORY_BODY_G,
                         StoryUiConstants.STORY_BODY_B, fade);
        font.setColor(scratchColor);
        font.draw(batch, text, textX, textTopY);
    }

    // -------------------------------------------------------------------------
    // Layout + hit testing (shared by drawing and input, so they cannot drift apart)
    // -------------------------------------------------------------------------

    /** Bottom edge of the plate in slot {@code buttonIndex} — the stack runs DOWNWARD (Y-up world). */
    public static float buttonBottomY(int buttonIndex) {
        return GameMath.stackedButtonBottomY(StoryUiConstants.STORY_EXCHANGE_BUTTONS_TOP_Y,
                                             StoryUiConstants.STORY_EXCHANGE_BUTTON_HEIGHT,
                                             StoryUiConstants.STORY_CHOICE_BUTTON_GAP,
                                             buttonIndex);
    }

    /** True when the given world point is inside the plate in slot {@code buttonIndex}. */
    public static boolean isInsideButton(float worldX, float worldY, int buttonIndex) {
        if (buttonIndex < 0 || buttonIndex >= StoryUiConstants.STORY_EXCHANGE_MAX_OPTIONS) return false;
        float buttonBottom = buttonBottomY(buttonIndex);
        return worldX >= StoryUiConstants.STORY_EXCHANGE_BUTTON_X
            && worldX <= StoryUiConstants.STORY_EXCHANGE_BUTTON_X
                       + StoryUiConstants.STORY_CHOICE_BUTTON_WIDTH
            && worldY >= buttonBottom
            && worldY <= buttonBottom + StoryUiConstants.STORY_EXCHANGE_BUTTON_HEIGHT;
    }

    /**
     * Which plate a world point falls in, or -1 for none.  {@code buttonCount} is how many plates are
     * actually on screen, so the empty third slot of a two-answer exchange can never be tapped.
     */
    public static int buttonIndexAt(float worldX, float worldY, int buttonCount) {
        int slots = Math.min(buttonCount, StoryUiConstants.STORY_EXCHANGE_MAX_OPTIONS);
        for (int buttonIndex = 0; buttonIndex < slots; buttonIndex++) {
            if (isInsideButton(worldX, worldY, buttonIndex)) return buttonIndex;
        }
        return -1;
    }

    @Override
    public void dispose() {
        panelRenderer.dispose();   // storyAudio is world-owned and disposed there
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}
