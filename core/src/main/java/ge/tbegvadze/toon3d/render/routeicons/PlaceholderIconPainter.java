package ge.tbegvadze.toon3d.render.routeicons;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * The fallback icon for any {@code iconPainterId} with no registered painter (route-map order-5): a
 * plain accent diamond with a brighter core, matching the order-4 placeholder look. It exists so a
 * newly-registered node type that forgot its icon still renders something readable instead of
 * throwing — the extensibility safety net.
 */
final class PlaceholderIconPainter implements IconPainter {

    private final Color core = new Color();

    @Override
    public void paint(ShapeRenderer shapes, SpriteBatch batch, float centerX, float centerY,
                      float size, Color accent, float animationT) {
        float alpha = accent.a;
        float radius = size * 0.5f;
        RouteIconSupport.fill(shapes, accent, 0.85f * alpha);
        RouteIconSupport.filledDiamond(shapes, centerX, centerY, radius);
        RouteIconSupport.fill(shapes, RouteIconSupport.brighten(core, accent, 0.5f), 0.9f * alpha);
        RouteIconSupport.filledDiamond(shapes, centerX, centerY, radius * 0.42f);
    }
}
