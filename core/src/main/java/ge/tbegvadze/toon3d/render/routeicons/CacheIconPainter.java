package ge.tbegvadze.toon3d.render.routeicons;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * CACHE — "supply crate" (route-map order-5). An isometric box: a front face, a slanted top
 * parallelogram and a slanted side, with an X strap across the front and a small "+" ammo tick.
 * Reads "loot &amp; refuel". Direct-drawn, accent = supply green/steel (the node's accent) with a
 * brighter top.
 */
final class CacheIconPainter implements IconPainter {

    private final Color top   = new Color();
    private final Color strap = new Color();

    @Override
    public void paint(ShapeRenderer shapes, SpriteBatch batch, float centerX, float centerY,
                      float size, Color accent, float animationT) {
        float alpha = accent.a;
        float half = size * 0.36f;
        float depth = half * 0.5f;

        // Front face box, offset slightly down-left so the iso top/side read above and to the right.
        float frontLeft   = centerX - half;
        float frontBottom = centerY - half;
        float frontRight  = centerX + half;
        float frontTop    = centerY + half - depth * 0.4f;

        RouteIconSupport.fill(shapes, accent, 0.85f * alpha);
        shapes.rect(frontLeft, frontBottom, frontRight - frontLeft, frontTop - frontBottom);

        // Slanted top parallelogram (brighter) — front-top edge sheared back-right by depth.
        RouteIconSupport.fill(shapes, RouteIconSupport.brighten(top, accent, 0.32f), 0.9f * alpha);
        shapes.triangle(frontLeft, frontTop, frontRight, frontTop, frontRight + depth, frontTop + depth);
        shapes.triangle(frontLeft, frontTop, frontRight + depth, frontTop + depth, frontLeft + depth, frontTop + depth);

        // Slanted right side (darker) — front-right edge sheared back by depth.
        RouteIconSupport.fill(shapes, RouteIconSupport.darken(top, accent, 0.35f), 0.9f * alpha);
        shapes.triangle(frontRight, frontBottom, frontRight, frontTop, frontRight + depth, frontTop + depth);
        shapes.triangle(frontRight, frontBottom, frontRight + depth, frontTop + depth, frontRight + depth, frontBottom + depth);

        // X strap across the front face (line).
        RouteIconSupport.stroke(shapes, RouteIconSupport.brighten(strap, accent, 0.5f), 0.9f * alpha);
        shapes.line(frontLeft, frontBottom, frontRight, frontTop);
        shapes.line(frontLeft, frontTop, frontRight, frontBottom);

        // Small "+" ammo tick centred on the front face (line, thicker via short crossbars).
        float tick = half * 0.28f;
        shapes.line(centerX - tick, frontBottom + (frontTop - frontBottom) * 0.5f,
                    centerX + tick, frontBottom + (frontTop - frontBottom) * 0.5f);
        shapes.line(centerX, frontBottom + (frontTop - frontBottom) * 0.5f - tick,
                    centerX, frontBottom + (frontTop - frontBottom) * 0.5f + tick);
    }
}
