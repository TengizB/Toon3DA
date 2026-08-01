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
import ge.tbegvadze.toon3d.narrative.SpeakerIcon;
import ge.tbegvadze.toon3d.narrative.TypeStyle;
import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * Story UI order-1 — the shared renderer for the four speakers' visual language.  Draws one
 * story panel: a rounded dark panel (so text reads over ANY 3D backdrop) carrying a speaker
 * chip (colored accent bar + procedural icon + name) and light, high-contrast, pre-wrapped
 * body text.  Every later story-UI order (bark toast, exchange panel, boot card, codex)
 * reuses this primitive with its own rect from {@link StoryUiConstants}.
 *
 * Identity, never colour alone: each speaker is told apart by accent colour AND icon AND
 * name — so it stays legible for colour-blind players (accessibility baseline).
 *
 * Owns its {@link SpriteBatch}, {@link ShapeRenderer} and {@link BitmapFont}; all disposed
 * in {@link #dispose()}.  No allocation in the draw path: the caller pre-wraps body text
 * (narrative/StoryText) and passes the line array; {@link GlyphLayout} and the scratch
 * {@link Color} are pre-allocated (measuring reuses the shared layout, matching
 * EventTextRenderer's established pattern).
 */
public final class StoryPanelRenderer implements Disposable {

    private final SpriteBatch   batch;
    private final ShapeRenderer shapes;
    private final BitmapFont    font;
    private final GlyphLayout   layout;
    private final Color         scratchColor = new Color();

    // Number of quarter-circle segments per rounded corner (cheap, smooth enough at UI scale).
    private static final int CORNER_SEGMENTS = 10;

    public StoryPanelRenderer() {
        this.batch  = new SpriteBatch();
        this.shapes = new ShapeRenderer();
        this.font   = new BitmapFont();
        this.font.getData().markupEnabled = false;
        this.layout = new GlyphLayout();
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
        float accentRed   = StoryUiConstants.SPEAKER_ACCENT_R[speakerIndex];
        float accentGreen = StoryUiConstants.SPEAKER_ACCENT_G[speakerIndex];
        float accentBlue  = StoryUiConstants.SPEAKER_ACCENT_B[speakerIndex];
        float fade        = MathUtils.clamp(visibleFraction, 0f, 1f);

        drawPanelShapes(camera, speaker, panelX, panelY, panelWidth, panelHeight,
                        accentRed, accentGreen, accentBlue, fade, drawCloseGlyph);
        drawPanelText(camera, speaker, speakerName, bodyLines, bodyLineCount,
                      panelX, panelY, panelHeight,
                      accentRed, accentGreen, accentBlue, fade, elapsedSeconds);
    }

    // -------------------------------------------------------------------------
    // Pass 1 — ShapeRenderer: border, panel fill, accent bar, icon (all Filled).
    // -------------------------------------------------------------------------

    private void drawPanelShapes(OrthographicCamera camera, Speaker speaker,
                                 float panelX, float panelY, float panelWidth, float panelHeight,
                                 float accentRed, float accentGreen, float accentBlue, float fade,
                                 boolean drawCloseGlyph) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        float radius    = StoryUiConstants.STORY_PANEL_CORNER_RADIUS;
        float border    = StoryUiConstants.STORY_PANEL_BORDER_THICKNESS;

        // Accent-tinted border: draw a slightly larger accent rect, then inset the dark fill.
        shapes.setColor(accentRed, accentGreen, accentBlue, StoryUiConstants.STORY_PANEL_BORDER_ALPHA * fade);
        drawRoundedRect(panelX, panelY, panelWidth, panelHeight, radius);

        shapes.setColor(StoryUiConstants.STORY_PANEL_BG_R, StoryUiConstants.STORY_PANEL_BG_G,
                        StoryUiConstants.STORY_PANEL_BG_B, StoryUiConstants.STORY_PANEL_ALPHA * fade);
        drawRoundedRect(panelX + border, panelY + border,
                        panelWidth - border * 2f, panelHeight - border * 2f, radius);

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

        // CLOSE affordance (order-2 bark): an X in the top-right corner.  Drawn in the body's light
        // colour, not the accent, so it reads as a control rather than part of the speaker chip.
        if (drawCloseGlyph) {
            drawCloseGlyph(StoryUiConstants.STORY_BARK_CLOSE_CENTER_X,
                           StoryUiConstants.STORY_BARK_CLOSE_CENTER_Y,
                           StoryUiConstants.STORY_BARK_CLOSE_GLYPH_SIZE,
                           StoryUiConstants.STORY_BARK_CLOSE_STROKE, fade);
        }

        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * Draws an X from two crossed bars (Filled ShapeRenderer only, so it shares the single shape
     * pass).  {@code size} is the glyph's full width/height; {@code stroke} its bar thickness.
     */
    private void drawCloseGlyph(float centerX, float centerY, float size, float stroke, float fade) {
        shapes.setColor(StoryUiConstants.STORY_BODY_R, StoryUiConstants.STORY_BODY_G,
                        StoryUiConstants.STORY_BODY_B, fade);
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
                               float fade, float elapsedSeconds) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        // CONTENT GUTTER: text starts one element-gap PAST the accent bar's right edge, using the
        // same geometry the bar and icon are drawn from (drawPanelShapes).  Deriving it any other
        // way is what made the bar sit under the first glyph of every body line.
        float contentLeft = panelX + StoryUiConstants.STORY_PANEL_PADDING
                + StoryUiConstants.STORY_CHIP_ACCENT_BAR_WIDTH
                + StoryUiConstants.STORY_CHIP_ELEMENT_GAP;
        float chipTopY    = panelY + panelHeight - StoryUiConstants.STORY_PANEL_PADDING;
        float shadow      = StoryUiConstants.STORY_TEXT_SHADOW_OFFSET;

        // Speaker name — sits after the icon, coloured with the accent (part of the chip).  The icon
        // occupies contentLeft .. contentLeft + iconSize, so the name clears it by one gap.
        float nameX = contentLeft + StoryUiConstants.STORY_CHIP_ICON_SIZE
                + StoryUiConstants.STORY_CHIP_ELEMENT_GAP;
        float nameTopY = chipTopY - 4f;
        font.getData().setScale(StoryUiConstants.STORY_NAME_TEXT_SIZE);
        drawWithShadow(speakerName, nameX, nameTopY, accentRed, accentGreen, accentBlue, fade, shadow);

        // Body — light text, pre-wrapped.  Planet gets a tiny bounded vertical jitter.
        float jitter = 0f;
        if (speaker.getTypeStyle().hasJitter()) {
            jitter = MathUtils.sin(elapsedSeconds * MathUtils.PI2 * StoryUiConstants.STORY_PLANET_JITTER_HZ)
                    * StoryUiConstants.STORY_PLANET_JITTER_PIXELS;
        }
        font.getData().setScale(StoryUiConstants.STORY_TEXT_SIZE);
        float lineTopY = chipTopY - StoryUiConstants.STORY_CHIP_HEIGHT - StoryUiConstants.STORY_CHIP_TO_BODY_GAP;
        for (int lineIndex = 0; lineIndex < bodyLineCount; lineIndex++) {
            drawWithShadow(bodyLines[lineIndex], contentLeft, lineTopY + jitter,
                           StoryUiConstants.STORY_BODY_R, StoryUiConstants.STORY_BODY_G,
                           StoryUiConstants.STORY_BODY_B, fade, shadow);
            lineTopY -= StoryUiConstants.STORY_BODY_LINE_PITCH;
        }

        // Restore shared font state for other renderers.
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    /** Draws one string with a dark drop-shadow beneath the coloured glyphs. */
    private void drawWithShadow(String text, float textX, float textTopY,
                                float red, float green, float blue, float fade, float shadowOffset) {
        scratchColor.set(0f, 0f, 0f, StoryUiConstants.STORY_TEXT_SHADOW_ALPHA * fade);
        font.setColor(scratchColor);
        font.draw(batch, text, textX + shadowOffset, textTopY - shadowOffset);

        scratchColor.set(red, green, blue, fade);
        font.setColor(scratchColor);
        font.draw(batch, text, textX, textTopY);
    }

    // -------------------------------------------------------------------------
    // Rounded-rect primitive (Filled): centre + two side rects + four corner discs.
    // -------------------------------------------------------------------------

    private void drawRoundedRect(float left, float bottom, float width, float height, float radius) {
        float clampedRadius = Math.min(radius, Math.min(width, height) / 2f);
        shapes.rect(left + clampedRadius, bottom, width - clampedRadius * 2f, height);
        shapes.rect(left, bottom + clampedRadius, clampedRadius, height - clampedRadius * 2f);
        shapes.rect(left + width - clampedRadius, bottom + clampedRadius, clampedRadius, height - clampedRadius * 2f);

        float leftCenterX  = left + clampedRadius;
        float rightCenterX = left + width - clampedRadius;
        float bottomCenterY = bottom + clampedRadius;
        float topCenterY    = bottom + height - clampedRadius;
        shapes.arc(leftCenterX,  bottomCenterY, clampedRadius, 180f, 90f, CORNER_SEGMENTS);
        shapes.arc(rightCenterX, bottomCenterY, clampedRadius, 270f, 90f, CORNER_SEGMENTS);
        shapes.arc(rightCenterX, topCenterY,    clampedRadius, 0f,   90f, CORNER_SEGMENTS);
        shapes.arc(leftCenterX,  topCenterY,    clampedRadius, 90f,  90f, CORNER_SEGMENTS);
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        font.dispose();
    }
}
