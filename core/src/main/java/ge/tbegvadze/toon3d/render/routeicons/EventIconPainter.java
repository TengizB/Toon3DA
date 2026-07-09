package ge.tbegvadze.toon3d.render.routeicons;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.RouteMapConstants;

/**
 * EVENT — "comms / lore beacon" (route-map order-5). Concentric broadcast arcs (a signal fan) rising
 * from a small base node, gently pulsing outward with {@code animationT}. Reads "something to
 * interact with, not necessarily a fight". Direct-drawn, accent = holo cyan with a white core.
 */
final class EventIconPainter implements IconPainter {

    private final Color core = new Color();

    @Override
    public void paint(ShapeRenderer shapes, SpriteBatch batch, float centerX, float centerY,
                      float size, Color accent, float animationT) {
        float alpha = accent.a;
        float baseY = centerY - size * 0.34f;
        float maxRadius = size * 0.5f;

        // Broadcast arcs fanning upward from the base node, each fading as it grows.
        int arcs = RouteMapConstants.ICON_EVENT_ARC_COUNT;
        float pulse = (animationT * RouteMapConstants.ICON_EVENT_PULSE_SPEED) % 1f;
        float fanFrom = MathUtils.PI * 0.18f;   // right of vertical
        float fanTo = MathUtils.PI * 0.82f;     // left of vertical (upper fan)
        for (int arc = 0; arc < arcs; arc++) {
            // Radii march outward and the whole set breathes with the pulse.
            float ringFraction = (arc + pulse) / arcs;
            float radius = maxRadius * ringFraction;
            float arcAlpha = (1f - ringFraction) * 0.9f * alpha;
            RouteIconSupport.stroke(shapes, accent, arcAlpha);
            float previousX = GameMath.pointOnCircleX(centerX, fanFrom, radius);
            float previousY = GameMath.pointOnCircleY(baseY, fanFrom, radius);
            int steps = 8;
            for (int step = 1; step <= steps; step++) {
                float angle = GameMath.lerp(fanFrom, fanTo, step / (float) steps);
                float pointX = GameMath.pointOnCircleX(centerX, angle, radius);
                float pointY = GameMath.pointOnCircleY(baseY, angle, radius);
                shapes.line(previousX, previousY, pointX, pointY);
                previousX = pointX;
                previousY = pointY;
            }
        }

        // Bright base node the arcs emit from.
        RouteIconSupport.fill(shapes, RouteIconSupport.brighten(core, accent, 0.6f), alpha);
        RouteIconSupport.disc(shapes, centerX, baseY, size * 0.1f);
    }
}
