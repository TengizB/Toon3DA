package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import ge.tbegvadze.toon3d.util.ArtDirection;
import ge.tbegvadze.toon3d.util.GameMath;

/**
 * The game's interface vocabulary (visual-identity-order-4): a chamfered panel, corner brackets, a plate
 * with four states, a segmented meter, a divider and shadowed text; order 5 adds the inked card, the suit
 * screen header (title + amber-ticked rule) and the NEW marker. Static, stateless, allocation-free.
 *
 * <p>Every ShapeRenderer primitive here expects the renderer to be inside {@code begin(ShapeType.Filled)}
 * (outlines are filled quads, so a frame never churns begin/end between fill and line) with GL blending
 * enabled by the caller when any alpha is below 1. Pieces never overlap, so a translucent panel shows no
 * darker seams. {@link #text} expects the batch to be between begin/end. See docs/art-direction.txt
 * UI KIT.
 */
public final class UiKit {

    private UiKit() {}

    // ------------------------------------------------------------------ R1 panel

    /** R1: chamfered panel ({@code UI_CHAMFER}), filled, with a full-alpha UI_BORDER_THICKNESS border. */
    public static void panel(ShapeRenderer shapes, float x, float y, float width, float height,
                             float fillRed, float fillGreen, float fillBlue, float fillAlpha,
                             float borderRed, float borderGreen, float borderBlue) {
        panel(shapes, x, y, width, height, ArtDirection.UI_CHAMFER,
                fillRed, fillGreen, fillBlue, fillAlpha,
                borderRed, borderGreen, borderBlue, 1f, ArtDirection.UI_BORDER_THICKNESS);
    }

    /**
     * General chamfered panel: the border is the band between the outer octagon and the octagon inset by
     * {@code borderThickness}; the fill covers only the inner octagon. A zero alpha or thickness skips that
     * part.
     */
    public static void panel(ShapeRenderer shapes, float x, float y, float width, float height, float chamfer,
                             float fillRed, float fillGreen, float fillBlue, float fillAlpha,
                             float borderRed, float borderGreen, float borderBlue, float borderAlpha,
                             float borderThickness) {
        float clampedChamfer = Math.min(chamfer, Math.min(width, height) * 0.5f);
        float thickness = Math.min(borderThickness, Math.min(width, height) * 0.5f);
        if (thickness <= 0f || borderAlpha <= 0f) thickness = 0f;
        float innerX = x + thickness;
        float innerY = y + thickness;
        float innerWidth = width - 2f * thickness;
        float innerHeight = height - 2f * thickness;
        float innerChamfer = GameMath.insetChamfer(clampedChamfer, thickness);
        if (fillAlpha > 0f) {
            shapes.setColor(fillRed, fillGreen, fillBlue, fillAlpha);
            fillOctagon(shapes, innerX, innerY, innerWidth, innerHeight, innerChamfer);
        }
        if (thickness > 0f) {
            shapes.setColor(borderRed, borderGreen, borderBlue, borderAlpha);
            octagonBand(shapes, x, y, width, height, clampedChamfer,
                    innerX, innerY, innerWidth, innerHeight, innerChamfer);
        }
    }

    /** Fills a chamfered rectangle with the renderer's current colour: three rects and four triangles. */
    public static void fillOctagon(ShapeRenderer shapes, float x, float y, float width, float height, float chamfer) {
        if (width <= 0f || height <= 0f) return;
        float cut = Math.min(chamfer, Math.min(width, height) * 0.5f);
        float right = x + width;
        float top = y + height;
        shapes.rect(x, y + cut, width, height - 2f * cut);
        if (cut <= 0f) return;
        shapes.rect(x + cut, y, width - 2f * cut, cut);
        shapes.rect(x + cut, top - cut, width - 2f * cut, cut);
        shapes.triangle(x, y + cut, x + cut, y, x + cut, y + cut);
        shapes.triangle(right - cut, y, right, y + cut, right - cut, y + cut);
        shapes.triangle(right, top - cut, right - cut, top, right - cut, top - cut);
        shapes.triangle(x + cut, top, x, top - cut, x + cut, top - cut);
    }

    /** Fills the band between two concentric chamfered rectangles: eight edge quads, none overlapping. */
    private static void octagonBand(ShapeRenderer shapes,
                                    float outerX, float outerY, float outerWidth, float outerHeight, float outerCut,
                                    float innerX, float innerY, float innerWidth, float innerHeight, float innerCut) {
        float outerRight = outerX + outerWidth, outerTop = outerY + outerHeight;
        float innerRight = innerX + innerWidth, innerTop = innerY + innerHeight;
        // bottom edge
        quad(shapes, outerX + outerCut, outerY, outerRight - outerCut, outerY,
                innerRight - innerCut, innerY, innerX + innerCut, innerY);
        // bottom-right diagonal
        quad(shapes, outerRight - outerCut, outerY, outerRight, outerY + outerCut,
                innerRight, innerY + innerCut, innerRight - innerCut, innerY);
        // right edge
        quad(shapes, outerRight, outerY + outerCut, outerRight, outerTop - outerCut,
                innerRight, innerTop - innerCut, innerRight, innerY + innerCut);
        // top-right diagonal
        quad(shapes, outerRight, outerTop - outerCut, outerRight - outerCut, outerTop,
                innerRight - innerCut, innerTop, innerRight, innerTop - innerCut);
        // top edge
        quad(shapes, outerRight - outerCut, outerTop, outerX + outerCut, outerTop,
                innerX + innerCut, innerTop, innerRight - innerCut, innerTop);
        // top-left diagonal
        quad(shapes, outerX + outerCut, outerTop, outerX, outerTop - outerCut,
                innerX, innerTop - innerCut, innerX + innerCut, innerTop);
        // left edge
        quad(shapes, outerX, outerTop - outerCut, outerX, outerY + outerCut,
                innerX, innerY + innerCut, innerX, innerTop - innerCut);
        // bottom-left diagonal
        quad(shapes, outerX, outerY + outerCut, outerX + outerCut, outerY,
                innerX + innerCut, innerY, innerX, innerY + innerCut);
    }

    private static void quad(ShapeRenderer shapes, float firstX, float firstY, float secondX, float secondY,
                             float thirdX, float thirdY, float fourthX, float fourthY) {
        shapes.triangle(firstX, firstY, secondX, secondY, thirdX, thirdY);
        shapes.triangle(firstX, firstY, thirdX, thirdY, fourthX, fourthY);
    }

    // ------------------------------------------------------------------ R2 brackets

    /** R2: four L-shaped corner marks, arms UI_BRACKET_LENGTH, UI_BRACKET_THICKNESS thick, inset UI_BRACKET_INSET. */
    public static void brackets(ShapeRenderer shapes, float x, float y, float width, float height,
                                float inkRed, float inkGreen, float inkBlue, float alpha) {
        brackets(shapes, x, y, width, height, ArtDirection.UI_BRACKET_LENGTH, inkRed, inkGreen, inkBlue, alpha);
    }

    /** R2 with a caller-chosen arm length, for plates too small for UI_BRACKET_LENGTH (UI_BRACKET_LENGTH_COMPACT). */
    public static void brackets(ShapeRenderer shapes, float x, float y, float width, float height, float armLength,
                                float inkRed, float inkGreen, float inkBlue, float alpha) {
        float inset = ArtDirection.UI_BRACKET_INSET;
        float arm = Math.min(armLength, Math.min(width, height) * 0.5f - inset);
        float thickness = ArtDirection.UI_BRACKET_THICKNESS;
        if (arm <= thickness) return;
        float left = x + inset, bottom = y + inset;
        float right = x + width - inset, top = y + height - inset;
        shapes.setColor(inkRed, inkGreen, inkBlue, alpha);
        // bottom-left
        shapes.rect(left, bottom, arm, thickness);
        shapes.rect(left, bottom + thickness, thickness, arm - thickness);
        // bottom-right
        shapes.rect(right - arm, bottom, arm, thickness);
        shapes.rect(right - thickness, bottom + thickness, thickness, arm - thickness);
        // top-left
        shapes.rect(left, top - thickness, arm, thickness);
        shapes.rect(left, top - arm, thickness, arm - thickness);
        // top-right
        shapes.rect(right - arm, top - thickness, arm, thickness);
        shapes.rect(right - thickness, top - arm, thickness, arm - thickness);
    }

    // ------------------------------------------------------------------ R3 plate

    /**
     * R3: a tappable plate, chamfered by UI_PLATE_CHAMFER, styled by {@code state}. {@code fillAlpha} is the
     * caller's (idle or pressed) alpha; the ink colours the PRESSED/SELECTED border and SELECTED brackets.
     */
    public static void plate(ShapeRenderer shapes, float x, float y, float width, float height,
                             PlateState state, float fillAlpha, float inkRed, float inkGreen, float inkBlue) {
        plate(shapes, x, y, width, height, ArtDirection.UI_PLATE_CHAMFER, state, fillAlpha, inkRed, inkGreen, inkBlue);
    }

    /** R3 with a caller-chosen chamfer, for plates too small for UI_PLATE_CHAMFER (e.g. the HUD's weapon slots). */
    public static void plate(ShapeRenderer shapes, float x, float y, float width, float height, float chamfer,
                             PlateState state, float fillAlpha, float inkRed, float inkGreen, float inkBlue) {
        plate(shapes, x, y, width, height, chamfer, ArtDirection.UI_BRACKET_LENGTH, state, fillAlpha,
                inkRed, inkGreen, inkBlue);
    }

    /**
     * R3 with a caller-chosen chamfer and bracket arm, for plates too small for UI_PLATE_CHAMFER and
     * UI_BRACKET_LENGTH (the HUD's 38-unit weapon slots).
     */
    public static void plate(ShapeRenderer shapes, float x, float y, float width, float height, float chamfer,
                             float bracketLength, PlateState state, float fillAlpha,
                             float inkRed, float inkGreen, float inkBlue) {
        boolean ink = state.usesInkBorder();
        panel(shapes, x, y, width, height, chamfer,
                state.fillRed(), state.fillGreen(), state.fillBlue(), fillAlpha * state.fillAlphaScale(),
                ink ? inkRed : ArtDirection.UI_LINE_RED,
                ink ? inkGreen : ArtDirection.UI_LINE_GREEN,
                ink ? inkBlue : ArtDirection.UI_LINE_BLUE,
                state.borderAlpha(), ArtDirection.UI_BORDER_THICKNESS);
        if (state.drawsInkBrackets()) {
            brackets(shapes, x, y, width, height, bracketLength, inkRed, inkGreen, inkBlue, 1f);
        }
    }

    // ------------------------------------------------------------------ order 5: card, screen header, marker

    /**
     * Order 5 R2: a CARD - a plate whose border is always the given INK (a relic's rarity, a perk's tag, a
     * weapon's tier) and whose brackets, in the SELECTED state, take that ink too. The fill is the state's
     * neutral fill at UI_PANEL_ALPHA_SCREEN, so text contrast is the same on every card. The only place a
     * rarity or tier colour appears in chrome.
     */
    public static void card(ShapeRenderer shapes, float x, float y, float width, float height,
                            float inkRed, float inkGreen, float inkBlue, PlateState state) {
        card(shapes, x, y, width, height, ArtDirection.UI_PLATE_CHAMFER, inkRed, inkGreen, inkBlue, state, 1f);
    }

    /** Order 5 R2 with a caller-chosen chamfer and an overall alpha multiplier (a dimmed card). */
    public static void card(ShapeRenderer shapes, float x, float y, float width, float height, float chamfer,
                            float inkRed, float inkGreen, float inkBlue, PlateState state, float alpha) {
        panel(shapes, x, y, width, height, chamfer,
                state.fillRed(), state.fillGreen(), state.fillBlue(),
                ArtDirection.UI_PANEL_ALPHA_SCREEN * state.fillAlphaScale() * alpha,
                inkRed, inkGreen, inkBlue, state.borderAlpha() * alpha, ArtDirection.UI_BORDER_THICKNESS);
        if (state.drawsInkBrackets()) {
            brackets(shapes, x, y, width, height, inkRed, inkGreen, inkBlue, alpha);
        }
    }

    /**
     * Order 5 R1, glyph half: a suit screen's title in the HEAVY face at TYPE_HEADING, UI_TEXT_PRIMARY,
     * left-aligned with its top at {@code topY}. Unshadowed (screen text never sits on the 3D view). The
     * font must be a HEAVY font; its scale is restored. The divider half is {@link #screenHeaderRule}, drawn
     * in the ShapeRenderer pass (a ShapeRenderer and a SpriteBatch cannot be active at once).
     */
    public static void screenHeaderTitle(Batch batch, BitmapFont heavyFont, CharSequence title, float x, float topY) {
        if (title == null || title.length() == 0) return;
        // getData().scaleX includes GameFonts' base scale, and setScale multiplies it in again: read the
        // logical scale back as raw / raw-at-1 so the restore below does not compound.
        float previousRawX = heavyFont.getData().scaleX, previousRawY = heavyFont.getData().scaleY;
        heavyFont.getData().setScale(1f);
        float previousScaleX = previousRawX / heavyFont.getData().scaleX;
        float previousScaleY = previousRawY / heavyFont.getData().scaleY;
        Color fontColor = heavyFont.getColor();
        float previousRed = fontColor.r, previousGreen = fontColor.g;
        float previousBlue = fontColor.b, previousAlpha = fontColor.a;
        heavyFont.getData().setScale(ArtDirection.TYPE_HEADING);
        heavyFont.setColor(ArtDirection.UI_TEXT_PRIMARY_RED, ArtDirection.UI_TEXT_PRIMARY_GREEN,
                ArtDirection.UI_TEXT_PRIMARY_BLUE, previousAlpha);
        heavyFont.draw(batch, title, x, topY);
        heavyFont.setColor(previousRed, previousGreen, previousBlue, previousAlpha);
        heavyFont.getData().setScale(previousScaleX, previousScaleY);
    }

    /** Order 5 R1, divider half: a {@link #divider} the header's width with its tick in SUIT_AMBER. */
    public static void screenHeaderRule(ShapeRenderer shapes, float x, float y, float width, float alpha) {
        divider(shapes, x, y, width,
                ArtDirection.SUIT_AMBER_RED, ArtDirection.SUIT_AMBER_GREEN, ArtDirection.SUIT_AMBER_BLUE, alpha);
    }

    /**
     * Order 5 R1: where the divider goes under a title whose top is {@code titleTopY} - one TYPE_HEADING
     * cap height, then UI_SCREEN_HEADER_RULE_GAP.
     */
    public static float screenHeaderRuleY(float titleTopY) {
        return titleTopY - ArtDirection.PIXEL_FONT_TARGET_CAP_HEIGHT * ArtDirection.TYPE_HEADING
                - ArtDirection.UI_SCREEN_HEADER_RULE_GAP;
    }

    /**
     * Order 5 R7: the NEW marker - a SUIT_AMBER square of UI_NEW_MARKER_SIZE centred on (centerX, centerY),
     * framed outside by a UI_INK_BLACK edge of UI_NEW_MARKER_EDGE. Five non-overlapping rects.
     */
    public static void newMarker(ShapeRenderer shapes, float centerX, float centerY, float alpha) {
        float half = ArtDirection.UI_NEW_MARKER_SIZE * 0.5f;
        float edge = ArtDirection.UI_NEW_MARKER_EDGE;
        float left = centerX - half, bottom = centerY - half, size = ArtDirection.UI_NEW_MARKER_SIZE;
        shapes.setColor(ArtDirection.UI_INK_BLACK_RED, ArtDirection.UI_INK_BLACK_GREEN, ArtDirection.UI_INK_BLACK_BLUE,
                alpha);
        shapes.rect(left - edge, bottom - edge, size + 2f * edge, edge);
        shapes.rect(left - edge, bottom + size, size + 2f * edge, edge);
        shapes.rect(left - edge, bottom, edge, size);
        shapes.rect(left + size, bottom, edge, size);
        shapes.setColor(ArtDirection.SUIT_AMBER_RED, ArtDirection.SUIT_AMBER_GREEN, ArtDirection.SUIT_AMBER_BLUE, alpha);
        shapes.rect(left, bottom, size, size);
    }

    // ------------------------------------------------------------------ R4 meter

    /** R4: a full-alpha segmented meter. */
    public static void meter(ShapeRenderer shapes, float x, float y, float width, float height,
                             float fraction, int segments,
                             float onRed, float onGreen, float onBlue,
                             float trackRed, float trackGreen, float trackBlue) {
        meter(shapes, x, y, width, height, fraction, segments, onRed, onGreen, onBlue, 1f,
                trackRed, trackGreen, trackBlue, 1f);
    }

    /**
     * R4: {@code segments} cells separated by UI_METER_GAP; every cell draws its track, the lit cells their
     * on-colour, and the partial last cell is lit proportionally from its left edge. Fraction 0 draws only
     * the track.
     */
    public static void meter(ShapeRenderer shapes, float x, float y, float width, float height,
                             float fraction, int segments,
                             float onRed, float onGreen, float onBlue, float onAlpha,
                             float trackRed, float trackGreen, float trackBlue, float trackAlpha) {
        int cellCount = Math.max(1, segments);
        float gap = ArtDirection.UI_METER_GAP;
        float cellWidth = (width - gap * (cellCount - 1)) / cellCount;
        if (cellWidth <= 0f) {
            gap = 0f;
            cellWidth = width / cellCount;
        }
        float clampedFraction = Math.max(0f, Math.min(1f, fraction));
        float litCells = clampedFraction * cellCount;
        if (trackAlpha > 0f) {
            shapes.setColor(trackRed, trackGreen, trackBlue, trackAlpha);
            for (int cellIndex = 0; cellIndex < cellCount; cellIndex++) {
                float cellX = x + cellIndex * (cellWidth + gap);
                float litPart = Math.max(0f, Math.min(1f, litCells - cellIndex));
                // The track shows only where the cell is not lit, so a translucent meter never double-blends.
                float trackStart = cellX + cellWidth * litPart;
                float trackWidth = cellWidth * (1f - litPart);
                if (trackWidth > 0f) shapes.rect(trackStart, y, trackWidth, height);
            }
        }
        if (litCells <= 0f) return;
        shapes.setColor(onRed, onGreen, onBlue, onAlpha);
        for (int cellIndex = 0; cellIndex < cellCount; cellIndex++) {
            float litPart = Math.min(1f, litCells - cellIndex);
            if (litPart <= 0f) break;
            shapes.rect(x + cellIndex * (cellWidth + gap), y, cellWidth * litPart, height);
        }
    }

    // ------------------------------------------------------------------ R5 divider

    /** R5: a UI_LINE rule of UI_DIVIDER_THICKNESS with a UI_DIVIDER_TICK_LENGTH ink tick at its left end. */
    public static void divider(ShapeRenderer shapes, float x, float y, float width,
                               float inkRed, float inkGreen, float inkBlue, float alpha) {
        float tick = Math.min(ArtDirection.UI_DIVIDER_TICK_LENGTH, width);
        float tickThickness = ArtDirection.UI_DIVIDER_TICK_THICKNESS;
        shapes.setColor(ArtDirection.UI_LINE_RED, ArtDirection.UI_LINE_GREEN, ArtDirection.UI_LINE_BLUE, alpha);
        if (width > tick) shapes.rect(x + tick, y, width - tick, ArtDirection.UI_DIVIDER_THICKNESS);
        shapes.setColor(inkRed, inkGreen, inkBlue, alpha);
        shapes.rect(x, y + (ArtDirection.UI_DIVIDER_THICKNESS - tickThickness) * 0.5f, tick, tickThickness);
    }

    // ------------------------------------------------------------------ R6 shadowed text

    /** R6: text with a UI_INK_BLACK drop shadow down-right, then the text. Restores the font's colour. */
    public static void text(Batch batch, BitmapFont font, CharSequence text, float x, float y,
                            float red, float green, float blue, float alpha) {
        if (text == null || text.length() == 0) return;
        Color fontColor = font.getColor();
        float previousRed = fontColor.r, previousGreen = fontColor.g;
        float previousBlue = fontColor.b, previousAlpha = fontColor.a;
        float offset = shadowOffset(font);
        font.setColor(ArtDirection.UI_INK_BLACK_RED, ArtDirection.UI_INK_BLACK_GREEN, ArtDirection.UI_INK_BLACK_BLUE,
                ArtDirection.UI_TEXT_SHADOW_ALPHA * alpha);
        font.draw(batch, text, x + offset, y - offset);
        font.setColor(red, green, blue, alpha);
        font.draw(batch, text, x, y);
        font.setColor(previousRed, previousGreen, previousBlue, previousAlpha);
    }

    /** R6 with alignment: as {@link #text}, laid out in {@code targetWidth} with a LibGDX {@code Align} value. */
    public static void text(Batch batch, BitmapFont font, CharSequence text, float x, float y,
                            float targetWidth, int horizontalAlign, boolean wrap,
                            float red, float green, float blue, float alpha) {
        if (text == null || text.length() == 0) return;
        Color fontColor = font.getColor();
        float previousRed = fontColor.r, previousGreen = fontColor.g;
        float previousBlue = fontColor.b, previousAlpha = fontColor.a;
        float offset = shadowOffset(font);
        font.setColor(ArtDirection.UI_INK_BLACK_RED, ArtDirection.UI_INK_BLACK_GREEN, ArtDirection.UI_INK_BLACK_BLUE,
                ArtDirection.UI_TEXT_SHADOW_ALPHA * alpha);
        font.draw(batch, text, x + offset, y - offset, targetWidth, horizontalAlign, wrap);
        font.setColor(red, green, blue, alpha);
        font.draw(batch, text, x, y, targetWidth, horizontalAlign, wrap);
        font.setColor(previousRed, previousGreen, previousBlue, previousAlpha);
    }

    /**
     * The shadow offset for the font's current scale: UI_TEXT_SHADOW_OFFSET at TYPE_CAPTION. The scale is
     * read from the cap height (getScaleX includes GameFonts' base scale; cap height at setScale(1) is
     * PIXEL_FONT_TARGET_CAP_HEIGHT).
     */
    public static float shadowOffset(BitmapFont font) {
        return ArtDirection.UI_TEXT_SHADOW_OFFSET * font.getCapHeight() / ArtDirection.PIXEL_FONT_TARGET_CAP_HEIGHT;
    }
}
