package ge.tbegvadze.toon3d.render.routeicons;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import ge.tbegvadze.toon3d.util.RouteMapConstants;

/**
 * Tiny shared glyph painters reused by the order-4 node cards (route-map order-5): the risk-pip row
 * and the per-state decorations (lock, check, "you are here" reticle, affix tag). Centralising these
 * keeps card chrome consistent so a new node type opts into the same visual language for free — the
 * overlay delegates here instead of inlining each glyph.
 *
 * <p>All methods draw through the caller's active {@link ShapeRenderer} using {@code shapes.set(...)}
 * to pick fill vs line (auto-flushing), so they compose inside the overlay's node passes. Colours are
 * passed in; nothing is allocated.
 */
public final class RouteGlyphs {

    private RouteGlyphs() {}

    /**
     * A row of up to {@link RouteMapConstants#RISK_PIP_MAX} filled dots growing leftward from
     * ({@code rightX}, {@code pipY}); the first {@code litCount} are drawn in {@code litColor}, the
     * rest dim in {@code dimColor}. Matches the order-4 risk meter (green → amber → red by tier).
     */
    public static void riskPips(ShapeRenderer shapes, float rightX, float pipY, float radius,
                                float step, int litCount, Color litColor, Color dimColor,
                                float litAlpha, float dimAlpha) {
        shapes.set(ShapeRenderer.ShapeType.Filled);
        for (int pip = 0; pip < RouteMapConstants.RISK_PIP_MAX; pip++) {
            float pipX = rightX - pip * step;
            if (pip < litCount) {
                shapes.setColor(litColor.r, litColor.g, litColor.b, MathUtils.clamp(litAlpha, 0f, 1f));
            } else {
                shapes.setColor(dimColor.r, dimColor.g, dimColor.b, MathUtils.clamp(dimAlpha, 0f, 1f));
            }
            shapes.circle(pipX, pipY, radius);
        }
    }

    /**
     * A padlock glyph (line): a body rectangle with a shackle arc above — drawn on LOCKED cards. Call
     * with a desaturated steel colour.
     */
    public static void lockGlyph(ShapeRenderer shapes, float centerX, float centerY, float size,
                                 Color color, float alpha) {
        shapes.set(ShapeRenderer.ShapeType.Line);
        shapes.setColor(color.r, color.g, color.b, MathUtils.clamp(alpha, 0f, 1f));
        float bodyHalfWidth = size * 0.34f;
        float bodyHalfHeight = size * 0.26f;
        float bodyBottom = centerY - bodyHalfHeight;
        shapes.rect(centerX - bodyHalfWidth, bodyBottom, bodyHalfWidth * 2f, bodyHalfHeight * 2f);
        float shackleRadius = bodyHalfWidth * 0.62f;
        shapes.arc(centerX, centerY + bodyHalfHeight, shackleRadius, 0f, 180f);
    }

    /** A check mark (line): a short down-stroke into a long up-stroke — drawn on VISITED cards. */
    public static void checkGlyph(ShapeRenderer shapes, float centerX, float centerY, float size,
                                  Color color, float alpha, float lineWidth) {
        shapes.set(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(color.r, color.g, color.b, MathUtils.clamp(alpha, 0f, 1f));
        shapes.rectLine(centerX - size * 0.4f, centerY, centerX - size * 0.1f, centerY - size * 0.28f, lineWidth);
        shapes.rectLine(centerX - size * 0.1f, centerY - size * 0.28f, centerX + size * 0.45f, centerY + size * 0.4f, lineWidth);
    }

    /**
     * The rotating "you are here" diamond reticle (line): a square rotated by {@code angleRadians}
     * — drawn around the CURRENT card. Same look the order-4 overlay used for CURRENT nodes.
     */
    public static void hereReticle(ShapeRenderer shapes, float centerX, float centerY, float radius,
                                   float angleRadians, Color color, float alpha) {
        shapes.set(ShapeRenderer.ShapeType.Line);
        shapes.setColor(color.r, color.g, color.b, MathUtils.clamp(alpha, 0f, 1f));
        float previousX = 0f;
        float previousY = 0f;
        for (int corner = 0; corner <= 4; corner++) {
            float cornerAngle = angleRadians + corner * MathUtils.HALF_PI;
            float pointX = centerX + radius * MathUtils.cos(cornerAngle);
            float pointY = centerY + radius * MathUtils.sin(cornerAngle);
            if (corner > 0) {
                shapes.line(previousX, previousY, pointX, pointY);
            }
            previousX = pointX;
            previousY = pointY;
        }
    }

    /**
     * A small filled corner tag (a rotated square) marking that a node carries a {@code NodeAffix}
     * (order-9). Drawn in a card corner in the affix's accent colour.
     */
    public static void affixTag(ShapeRenderer shapes, float centerX, float centerY, float size,
                                Color color, float alpha) {
        shapes.set(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(color.r, color.g, color.b, MathUtils.clamp(alpha, 0f, 1f));
        RouteIconSupport.filledDiamond(shapes, centerX, centerY, size * 0.5f);
    }
}
