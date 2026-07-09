package ge.tbegvadze.toon3d.render.routeicons;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * Fallback region crest (route-map order-5): two concentric rings with a centre dot. Drawn for any
 * region theme with no dedicated crest, so a newly-added region always shows a badge.
 */
final class RingBadgeCrestPainter implements RegionCrestPainter {

    private final Color core = new Color();

    @Override
    public void paint(ShapeRenderer shapes, float centerX, float centerY, float size, Color tint, float animationT) {
        float alpha = tint.a;
        float radius = size * 0.5f;
        RouteIconSupport.stroke(shapes, tint, 0.9f * alpha);
        RouteIconSupport.ring(shapes, centerX, centerY, radius);
        RouteIconSupport.ring(shapes, centerX, centerY, radius * 0.62f);
        RouteIconSupport.fill(shapes, RouteIconSupport.brighten(core, tint, 0.5f), alpha);
        RouteIconSupport.disc(shapes, centerX, centerY, radius * 0.18f);
    }
}
