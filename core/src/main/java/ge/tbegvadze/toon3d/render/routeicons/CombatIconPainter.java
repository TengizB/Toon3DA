package ge.tbegvadze.toon3d.render.routeicons;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * COMBAT — "crosshair / engagement reticle" (route-map order-5). A thin ring with four N/E/S/W tick
 * marks crossing it and a bright centre dot. Reads as "standard fight". Direct-drawn, accent = holo
 * cyan (the node's accent).
 */
final class CombatIconPainter implements IconPainter {

    private final Color core = new Color();

    @Override
    public void paint(ShapeRenderer shapes, SpriteBatch batch, float centerX, float centerY,
                      float size, Color accent, float animationT) {
        float alpha = accent.a;
        float radius = size * 0.42f;
        float tickInner = radius * 0.72f;
        float tickOuter = radius * 1.24f;

        // Reticle ring + the four crossing ticks (line mode).
        RouteIconSupport.stroke(shapes, accent, 0.95f * alpha);
        RouteIconSupport.ring(shapes, centerX, centerY, radius);
        shapes.line(centerX, centerY + tickInner, centerX, centerY + tickOuter); // N
        shapes.line(centerX, centerY - tickInner, centerX, centerY - tickOuter); // S
        shapes.line(centerX + tickInner, centerY, centerX + tickOuter, centerY); // E
        shapes.line(centerX - tickInner, centerY, centerX - tickOuter, centerY); // W

        // Bright centre dot (filled).
        RouteIconSupport.fill(shapes, RouteIconSupport.brighten(core, accent, 0.55f), alpha);
        RouteIconSupport.disc(shapes, centerX, centerY, radius * 0.2f);
    }
}
