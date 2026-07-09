package ge.tbegvadze.toon3d.render.routeicons;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * REST — "med cross / bio-bed" (route-map order-5). A filled medical cross inside a soft ring, with a
 * tiny heartbeat polyline beneath. Reads "recover health". Direct-drawn, accent = med green (matches
 * the HUD phosphor green).
 */
final class RestIconPainter implements IconPainter {

    private final Color core = new Color();

    @Override
    public void paint(ShapeRenderer shapes, SpriteBatch batch, float centerX, float centerY,
                      float size, Color accent, float animationT) {
        float alpha = accent.a;
        float ringRadius = size * 0.46f;
        float crossCenterY = centerY + size * 0.06f;

        // Soft ring around the cross.
        RouteIconSupport.stroke(shapes, accent, 0.75f * alpha);
        RouteIconSupport.ring(shapes, centerX, crossCenterY, ringRadius);

        // Filled cross: two overlapping rounded bars (approximated by rectangles).
        float armHalf = ringRadius * 0.62f;
        float armThick = ringRadius * 0.28f;
        RouteIconSupport.fill(shapes, RouteIconSupport.brighten(core, accent, 0.3f), 0.95f * alpha);
        shapes.rect(centerX - armThick, crossCenterY - armHalf, armThick * 2f, armHalf * 2f);
        shapes.rect(centerX - armHalf, crossCenterY - armThick, armHalf * 2f, armThick * 2f);

        // Tiny heartbeat trace beneath the cross.
        float traceY = crossCenterY - ringRadius - size * 0.05f;
        float traceHalf = size * 0.4f;
        RouteIconSupport.stroke(shapes, RouteIconSupport.brighten(core, accent, 0.4f), 0.9f * alpha);
        float step = traceHalf / 3f;
        float left = centerX - traceHalf;
        shapes.line(left, traceY, left + step, traceY);
        shapes.line(left + step, traceY, left + step * 1.4f, traceY + size * 0.14f);
        shapes.line(left + step * 1.4f, traceY + size * 0.14f, left + step * 1.8f, traceY - size * 0.1f);
        shapes.line(left + step * 1.8f, traceY - size * 0.1f, left + step * 2.2f, traceY);
        shapes.line(left + step * 2.2f, traceY, centerX + traceHalf, traceY);
    }
}
