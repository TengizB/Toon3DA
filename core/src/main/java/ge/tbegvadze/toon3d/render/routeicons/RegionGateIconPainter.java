package ge.tbegvadze.toon3d.render.routeicons;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.RouteMapConstants;

/**
 * REGION_GATE — "airlock / bulkhead" (route-map order-5). A heavy circular hatch: an outer ring, an
 * inner ring, a ring of radial locking bolts and a central seam. It is a convergence beat, so the
 * overlay draws it a touch larger than a normal icon. Direct-drawn, accent = the region theme colour.
 */
final class RegionGateIconPainter implements IconPainter {

    private final Color bolt = new Color();

    @Override
    public void paint(ShapeRenderer shapes, SpriteBatch batch, float centerX, float centerY,
                      float size, Color accent, float animationT) {
        float alpha = accent.a;
        float outerRadius = size * 0.5f;
        float innerRadius = outerRadius * 0.6f;

        // Outer + inner hatch rings.
        RouteIconSupport.stroke(shapes, accent, 0.95f * alpha);
        RouteIconSupport.ring(shapes, centerX, centerY, outerRadius);
        RouteIconSupport.stroke(shapes, RouteIconSupport.brighten(bolt, accent, 0.25f), 0.9f * alpha);
        RouteIconSupport.ring(shapes, centerX, centerY, innerRadius);

        // Radial locking bolts between the two rings.
        int bolts = RouteMapConstants.ICON_GATE_BOLT_COUNT;
        float boltRadius = size * 0.06f;
        float boltRing = (outerRadius + innerRadius) * 0.5f;
        RouteIconSupport.fill(shapes, RouteIconSupport.brighten(bolt, accent, 0.4f), alpha);
        for (int index = 0; index < bolts; index++) {
            float angle = index * com.badlogic.gdx.math.MathUtils.PI2 / bolts;
            float boltX = GameMath.pointOnCircleX(centerX, angle, boltRing);
            float boltY = GameMath.pointOnCircleY(centerY, angle, boltRing);
            RouteIconSupport.disc(shapes, boltX, boltY, boltRadius);
        }

        // Central vertical seam splitting the hatch.
        RouteIconSupport.stroke(shapes, RouteIconSupport.brighten(bolt, accent, 0.25f), 0.9f * alpha);
        shapes.line(centerX, centerY - innerRadius, centerX, centerY + innerRadius);
    }
}
