package ge.tbegvadze.toon3d.render.routeicons;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.RouteMapConstants;

/**
 * MYSTERY — "unknown query" (route-map order-5). A bold "?" glyph (arc + stem + dot) floating inside
 * a slowly-rotating segmented ring. This painter is ALSO the fog icon: the overlay draws it for any
 * un-revealed node regardless of its hidden real type, so a "?" is what the player sees pre-entry.
 * Direct-drawn, accent = mystery violet.
 */
final class MysteryIconPainter implements IconPainter {

    private final Color core = new Color();

    @Override
    public void paint(ShapeRenderer shapes, SpriteBatch batch, float centerX, float centerY,
                      float size, Color accent, float animationT) {
        float alpha = accent.a;
        float ringRadius = size * 0.5f;
        float spin = animationT * RouteMapConstants.ICON_MYSTERY_SPIN_SPEED;

        // Segmented ring: dashes drawn as short arcs, slowly rotating.
        RouteIconSupport.stroke(shapes, accent, 0.85f * alpha);
        int dashes = RouteMapConstants.ICON_MYSTERY_RING_DASHES;
        float dashSpanRadians = MathUtils.PI2 / dashes * 0.55f;
        for (int dash = 0; dash < dashes; dash++) {
            float startAngle = spin + dash * MathUtils.PI2 / dashes;
            float previousX = GameMath.pointOnCircleX(centerX, startAngle, ringRadius);
            float previousY = GameMath.pointOnCircleY(centerY, startAngle, ringRadius);
            int steps = 3;
            for (int step = 1; step <= steps; step++) {
                float angle = startAngle + dashSpanRadians * step / steps;
                float pointX = GameMath.pointOnCircleX(centerX, angle, ringRadius);
                float pointY = GameMath.pointOnCircleY(centerY, angle, ringRadius);
                shapes.line(previousX, previousY, pointX, pointY);
                previousX = pointX;
                previousY = pointY;
            }
        }

        // "?" glyph — hook arc (top), short vertical stem, and a dot below.
        Color bright = RouteIconSupport.brighten(core, accent, 0.5f);
        float glyphRadius = ringRadius * 0.42f;
        float hookCenterY = centerY + glyphRadius * 0.5f;
        RouteIconSupport.stroke(shapes, bright, alpha);
        // hook: an open arc from the left, over the top, down the right (about 300 degrees).
        float hookFrom = MathUtils.PI * 1.15f;
        float hookTo = -MathUtils.PI * 0.35f;
        float previousX = GameMath.pointOnCircleX(centerX, hookFrom, glyphRadius);
        float previousY = GameMath.pointOnCircleY(hookCenterY, hookFrom, glyphRadius);
        int hookSteps = 10;
        for (int step = 1; step <= hookSteps; step++) {
            float angle = GameMath.lerp(hookFrom, hookTo, step / (float) hookSteps);
            float pointX = GameMath.pointOnCircleX(centerX, angle, glyphRadius);
            float pointY = GameMath.pointOnCircleY(hookCenterY, angle, glyphRadius);
            shapes.line(previousX, previousY, pointX, pointY);
            previousX = pointX;
            previousY = pointY;
        }
        // stem from the hook's lower-right end down to just above the dot.
        float stemTopY = hookCenterY - glyphRadius * 0.35f;
        float stemBottomY = centerY - glyphRadius * 0.55f;
        shapes.line(centerX, stemTopY, centerX, stemBottomY);

        // dot.
        RouteIconSupport.fill(shapes, bright, alpha);
        RouteIconSupport.disc(shapes, centerX, stemBottomY - glyphRadius * 0.35f, glyphRadius * 0.16f);
    }
}
