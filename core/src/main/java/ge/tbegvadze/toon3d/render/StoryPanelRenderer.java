package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.narrative.Speaker;
import ge.tbegvadze.toon3d.narrative.SpeakerIcon;
import ge.tbegvadze.toon3d.util.ArtDirection;
import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * Story UI order-1 — the shared renderer for the four speakers' visual language.  Draws one
 * story panel: a dark UiKit panel (so text reads over ANY 3D backdrop) framed by corner brackets in
 * the speaker's ink (visual-identity-order-6 R3), carrying a speaker chip (colored accent bar +
 * procedural icon + name) and light, high-contrast, pre-wrapped body text.  Every later story-UI order (bark toast, exchange panel, boot card, codex)
 * reuses this primitive with its own rect from {@link StoryUiConstants}.
 *
 * Identity, never colour alone: each speaker is told apart by accent colour AND icon AND
 * name — so it stays legible for colour-blind players (accessibility baseline).
 *
 * Owns its {@link SpriteBatch}, {@link ShapeRenderer} and two {@link BitmapFont}s (REGULAR + HEAVY);
 * all disposed in {@link #dispose()}.  No allocation in the draw path: the caller pre-wraps body
 * text (narrative/StoryText) and passes the line array.
 */
public final class StoryPanelRenderer implements Disposable {

    private final SpriteBatch   batch;
    private final ShapeRenderer shapes;
    private final BitmapFont    font;
    /** The HEAVY face, for a panel whose speaker name is a machine header (the reprint card's status block). */
    private final BitmapFont    heavyFont;

    // Circle segments for the speaker icons' discs (cheap, smooth enough at UI scale).
    private static final int CORNER_SEGMENTS = 10;

    // ACCESSIBILITY (order-6 Part D) — the two settings that change how a panel DRAWS.  Both default
    // to the order-1 baseline, so a renderer that never sets them behaves exactly as before.
    private float   bodyTextScale = StoryUiConstants.STORY_TEXT_SIZE;
    private boolean reduceMotion;

    public StoryPanelRenderer() {
        this.batch  = new SpriteBatch();
        this.shapes = new ShapeRenderer();
        this.font   = GameFonts.newFont(FontFace.REGULAR);
        this.heavyFont = GameFonts.newFont(FontFace.HEAVY);
    }

    /**
     * Applies the order-6 accessibility settings to every panel this renderer draws from now on.
     * Call from the update path when the player changes a setting — never per frame.
     *
     * <p>The caller passes the ALREADY-SCALED text size (see {@code StorySettings.getStoryTextScale})
     * and must narrow the wrap width to match on the headless side, or a larger size would run past
     * the panel instead of wrapping sooner.
     */
    public void applyAccessibilitySettings(float bodyTextScale, boolean reduceMotion) {
        if (bodyTextScale > 0f) this.bodyTextScale = bodyTextScale;
        this.reduceMotion = reduceMotion;
    }

    /**
     * Draws one story panel.  Body lines must already be wrapped (see
     * narrative/StoryText.wrapToMaxChars) and, for the Organization, already upper-cased —
     * so the draw path allocates nothing.
     *
     * @param visibleFraction 0..1 fade multiplier applied to every element (fade in/out)
     * @param elapsedSeconds  running time used only for the Planet's subtle jitter
     */
    public void drawPanel(OrthographicCamera camera, Speaker speaker, String speakerName,
                          String[] bodyLines, int bodyLineCount,
                          float panelX, float panelY, float panelWidth, float panelHeight,
                          float visibleFraction, float elapsedSeconds) {
        drawPanel(camera, speaker, speakerName, bodyLines, bodyLineCount,
                  panelX, panelY, panelWidth, panelHeight, visibleFraction, elapsedSeconds, false);
    }

    /**
     * As above, plus an optional CLOSE glyph (an X) in the panel's top-right corner — drawn by any
     * panel the player must dismiss themselves, such as the order-2 bark.  Its hit rect lives in
     * {@link StoryUiConstants} so input and drawing cannot drift apart.
     */
    public void drawPanel(OrthographicCamera camera, Speaker speaker, String speakerName,
                          String[] bodyLines, int bodyLineCount,
                          float panelX, float panelY, float panelWidth, float panelHeight,
                          float visibleFraction, float elapsedSeconds, boolean drawCloseGlyph) {
        int speakerIndex = speaker.ordinal();
        drawPanel(camera, speaker, speakerName, bodyLines, bodyLineCount,
                  panelX, panelY, panelWidth, panelHeight, visibleFraction, elapsedSeconds, drawCloseGlyph,
                  StoryUiConstants.SPEAKER_ACCENT_R[speakerIndex], StoryUiConstants.SPEAKER_ACCENT_G[speakerIndex],
                  StoryUiConstants.SPEAKER_ACCENT_B[speakerIndex], false);
    }

    /**
     * The general form (visual-identity-order-6 R3/R5): the corner brackets take {@code bracketRed/Green/Blue}
     * instead of the speaker accent — the reprint card's SYSTEM block is framed in the machine's FACILITY_CYAN,
     * not in its speaker's bone — and {@code heavySpeakerName} draws the chip's name in the HEAVY face (the card's
     * status header). The chip, accent bar and icon always keep the speaker's own accent.
     */
    public void drawPanel(OrthographicCamera camera, Speaker speaker, String speakerName,
                          String[] bodyLines, int bodyLineCount,
                          float panelX, float panelY, float panelWidth, float panelHeight,
                          float visibleFraction, float elapsedSeconds, boolean drawCloseGlyph,
                          float bracketRed, float bracketGreen, float bracketBlue, boolean heavySpeakerName) {
        int speakerIndex = speaker.ordinal();
        float accentRed   = StoryUiConstants.SPEAKER_ACCENT_R[speakerIndex];
        float accentGreen = StoryUiConstants.SPEAKER_ACCENT_G[speakerIndex];
        float accentBlue  = StoryUiConstants.SPEAKER_ACCENT_B[speakerIndex];
        float fade        = MathUtils.clamp(visibleFraction, 0f, 1f);

        drawPanelShapes(camera, speaker, panelX, panelY, panelWidth, panelHeight,
                        accentRed, accentGreen, accentBlue, bracketRed, bracketGreen, bracketBlue,
                        fade, drawCloseGlyph);
        drawPanelText(camera, speaker, speakerName, bodyLines, bodyLineCount,
                      panelX, panelY, panelHeight,
                      accentRed, accentGreen, accentBlue, fade, elapsedSeconds, heavySpeakerName);
    }

    // -------------------------------------------------------------------------
    // Pass 1 — ShapeRenderer: panel, brackets, accent bar, icon, close plate (all Filled).
    // -------------------------------------------------------------------------

    private void drawPanelShapes(OrthographicCamera camera, Speaker speaker,
                                 float panelX, float panelY, float panelWidth, float panelHeight,
                                 float accentRed, float accentGreen, float accentBlue,
                                 float bracketRed, float bracketGreen, float bracketBlue, float fade,
                                 boolean drawCloseGlyph) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // R3: the kit panel (UI_PANEL at the in-play chrome alpha, UI_LINE border), its brackets in the ink.
        UiKit.panel(shapes, panelX, panelY, panelWidth, panelHeight, ArtDirection.UI_CHAMFER,
                    StoryUiConstants.STORY_PANEL_BG_R, StoryUiConstants.STORY_PANEL_BG_G,
                    StoryUiConstants.STORY_PANEL_BG_B, StoryUiConstants.STORY_PANEL_ALPHA * fade,
                    ArtDirection.UI_LINE_RED, ArtDirection.UI_LINE_GREEN, ArtDirection.UI_LINE_BLUE, fade,
                    ArtDirection.UI_BORDER_THICKNESS);
        UiKit.brackets(shapes, panelX, panelY, panelWidth, panelHeight, bracketRed, bracketGreen, bracketBlue, fade);

        // Vertical accent bar at the panel's left edge — the primary colour cue.
        float barX = panelX + StoryUiConstants.STORY_PANEL_PADDING;
        float barW = StoryUiConstants.STORY_CHIP_ACCENT_BAR_WIDTH;
        float barTop    = panelY + panelHeight - StoryUiConstants.STORY_PANEL_PADDING;
        float barBottom = panelY + StoryUiConstants.STORY_PANEL_PADDING;
        shapes.setColor(accentRed, accentGreen, accentBlue, fade);
        shapes.rect(barX, barBottom, barW, barTop - barBottom);

        // Speaker icon, in the chip row to the right of the accent bar.
        float iconSize = StoryUiConstants.STORY_CHIP_ICON_SIZE;
        float iconX    = barX + barW + StoryUiConstants.STORY_CHIP_ELEMENT_GAP;
        float chipTopY = panelY + panelHeight - StoryUiConstants.STORY_PANEL_PADDING;
        float iconY    = chipTopY - (StoryUiConstants.STORY_CHIP_HEIGHT + iconSize) / 2f;
        drawSpeakerIcon(speaker.getIcon(), iconX, iconY, iconSize,
                        accentRed, accentGreen, accentBlue, fade);

        // CLOSE affordance (order-2 bark): an X on a small IDLE plate in the top-right corner (R3).  The
        // glyph is UI_TEXT_SECONDARY, not the accent, so it reads as a control rather than part of the chip.
        if (drawCloseGlyph) {
            drawCloseGlyph(StoryUiConstants.STORY_BARK_CLOSE_CENTER_X,
                           StoryUiConstants.STORY_BARK_CLOSE_CENTER_Y,
                           StoryUiConstants.STORY_BARK_CLOSE_GLYPH_SIZE,
                           StoryUiConstants.STORY_BARK_CLOSE_STROKE, accentRed, accentGreen, accentBlue, fade);
        }

        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * Draws the close plate (IDLE, STORY_BARK_CLOSE_PLATE_SIZE — visual only; the touch rect is the glyph
     * plus STORY_BARK_CLOSE_TOUCH_PADDING) and an X from two crossed bars on it (Filled ShapeRenderer only, so
     * it shares the single shape pass).  {@code size} is the glyph's full width/height; {@code stroke} its bar
     * thickness.
     */
    private void drawCloseGlyph(float centerX, float centerY, float size, float stroke,
                                float accentRed, float accentGreen, float accentBlue, float fade) {
        float plateSize = StoryUiConstants.STORY_BARK_CLOSE_PLATE_SIZE;
        UiKit.plate(shapes, centerX - plateSize / 2f, centerY - plateSize / 2f, plateSize, plateSize,
                    ArtDirection.UI_CHAMFER, ArtDirection.UI_BRACKET_LENGTH_COMPACT, PlateState.IDLE,
                    StoryUiConstants.STORY_PANEL_ALPHA, accentRed, accentGreen, accentBlue, fade);
        shapes.setColor(ArtDirection.UI_TEXT_SECONDARY_RED, ArtDirection.UI_TEXT_SECONDARY_GREEN,
                        ArtDirection.UI_TEXT_SECONDARY_BLUE, fade);
        float half = size / 2f;
        // rectLine draws a thick line between two points — the two diagonals of the glyph box.
        shapes.rectLine(centerX - half, centerY - half, centerX + half, centerY + half, stroke);
        shapes.rectLine(centerX - half, centerY + half, centerX + half, centerY - half, stroke);
    }

    /** Draws the tiny procedural chip glyph for a speaker (Filled ShapeRenderer only). */
    private void drawSpeakerIcon(SpeakerIcon icon, float iconX, float iconY, float size,
                                 float accentRed, float accentGreen, float accentBlue, float fade) {
        float centerX = iconX + size / 2f;
        float centerY = iconY + size / 2f;
        shapes.setColor(accentRed, accentGreen, accentBlue, fade);

        switch (icon) {
            case FRIENDLY_RING: {
                // Accent ring: outer accent disc with the panel-bg disc punched out of the centre.
                shapes.circle(centerX, centerY, size / 2f, CORNER_SEGMENTS * 2);
                shapes.setColor(StoryUiConstants.STORY_PANEL_BG_R, StoryUiConstants.STORY_PANEL_BG_G,
                                StoryUiConstants.STORY_PANEL_BG_B, StoryUiConstants.STORY_PANEL_ALPHA * fade);
                shapes.circle(centerX, centerY, size / 2f - 3f, CORNER_SEGMENTS * 2);
                break;
            }
            case CRACKED_EYE: {
                // Accent disc with a dark vertical "crack" through it and a small pupil dot.
                shapes.circle(centerX, centerY, size / 2f, CORNER_SEGMENTS * 2);
                shapes.setColor(StoryUiConstants.STORY_PANEL_BG_R, StoryUiConstants.STORY_PANEL_BG_G,
                                StoryUiConstants.STORY_PANEL_BG_B, StoryUiConstants.STORY_PANEL_ALPHA * fade);
                shapes.rect(centerX - 1.2f, iconY, 2.4f, size);
                shapes.setColor(accentRed, accentGreen, accentBlue, fade);
                shapes.circle(centerX, centerY, size / 6f, CORNER_SEGMENTS);
                break;
            }
            case CORPORATE_BRACKET: {
                // Two hard brackets: [ ]  made of thin accent rectangles.
                float thickness = 3f;
                float armLength = size / 3f;
                // Left bracket
                shapes.rect(iconX, iconY, thickness, size);
                shapes.rect(iconX, iconY + size - thickness, armLength, thickness);
                shapes.rect(iconX, iconY, armLength, thickness);
                // Right bracket
                float rightX = iconX + size - thickness;
                shapes.rect(rightX, iconY, thickness, size);
                shapes.rect(iconX + size - armLength, iconY + size - thickness, armLength, thickness);
                shapes.rect(iconX + size - armLength, iconY, armLength, thickness);
                break;
            }
            case TERMINAL_CARET:
            default: {
                // A right-pointing caret ">" plus a short underscore — a terminal prompt.
                shapes.triangle(iconX, iconY + size, iconX, iconY + size * 0.55f,
                                centerX, centerY + size * 0.02f);
                shapes.triangle(iconX, iconY + size * 0.45f, iconX, iconY,
                                centerX, centerY - size * 0.02f);
                shapes.rect(centerX + 2f, iconY, size / 2.5f, 3f);
                break;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Pass 2 — SpriteBatch: speaker name (accent) + body lines (light) with shadow.
    // -------------------------------------------------------------------------

    private void drawPanelText(OrthographicCamera camera, Speaker speaker, String speakerName,
                               String[] bodyLines, int bodyLineCount,
                               float panelX, float panelY, float panelHeight,
                               float accentRed, float accentGreen, float accentBlue,
                               float fade, float elapsedSeconds, boolean heavySpeakerName) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        // CONTENT GUTTER: text starts one element-gap PAST the accent bar's right edge, using the
        // same geometry the bar and icon are drawn from (drawPanelShapes).  Deriving it any other
        // way is what made the bar sit under the first glyph of every body line.
        float contentLeft = panelX + StoryUiConstants.STORY_PANEL_PADDING
                + StoryUiConstants.STORY_CHIP_ACCENT_BAR_WIDTH
                + StoryUiConstants.STORY_CHIP_ELEMENT_GAP;
        float chipTopY    = panelY + panelHeight - StoryUiConstants.STORY_PANEL_PADDING;

        // Speaker name — sits after the icon, coloured with the accent (part of the chip).  The icon
        // occupies contentLeft .. contentLeft + iconSize, so the name clears it by one gap.
        float nameX = contentLeft + StoryUiConstants.STORY_CHIP_ICON_SIZE
                + StoryUiConstants.STORY_CHIP_ELEMENT_GAP;
        float nameTopY = chipTopY - 4f;
        BitmapFont nameFont = heavySpeakerName ? heavyFont : font;
        nameFont.getData().setScale(StoryUiConstants.STORY_NAME_TEXT_SIZE);
        UiKit.text(batch, nameFont, speakerName, nameX, nameTopY, accentRed, accentGreen, accentBlue, fade);

        // Body — light text, pre-wrapped.  Planet gets a tiny bounded vertical jitter, unless the
        // "reduce motion" accessibility setting is on: movement under text is exactly the kind of
        // decoration order-6 Part D lets a player switch off, and the Planet is still told apart by
        // its accent colour, its icon and its name without it.
        float jitter = 0f;
        if (speaker.getTypeStyle().hasJitter() && !reduceMotion) {
            jitter = MathUtils.sin(elapsedSeconds * MathUtils.PI2 * StoryUiConstants.STORY_PLANET_JITTER_HZ)
                    * StoryUiConstants.STORY_PLANET_JITTER_PIXELS;
        }
        font.getData().setScale(bodyTextScale);
        float lineTopY = chipTopY - StoryUiConstants.STORY_CHIP_HEIGHT - StoryUiConstants.STORY_CHIP_TO_BODY_GAP;
        for (int lineIndex = 0; lineIndex < bodyLineCount; lineIndex++) {
            UiKit.text(batch, font, bodyLines[lineIndex], contentLeft, lineTopY + jitter,
                       StoryUiConstants.STORY_BODY_R, StoryUiConstants.STORY_BODY_G,
                       StoryUiConstants.STORY_BODY_B, fade);
            lineTopY -= StoryUiConstants.STORY_BODY_LINE_PITCH;
        }

        // Restore shared font state for other renderers.
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        heavyFont.getData().setScale(1f);
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        font.dispose();
        heavyFont.dispose();
    }
}
