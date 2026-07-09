package ge.tbegvadze.toon3d.render.routeicons;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

/**
 * ELITE — "skull in a hazard diamond" (route-map order-5). A hazard-diamond outline framing a
 * minimal skull: a rounded-top cranium dome (arc fan), two dark eye sockets, and a short jaw notch.
 * Reads "heavy resistance". Direct-drawn, accent = danger red / hazard amber (the node's accent).
 */
final class EliteIconPainter implements IconPainter {

    private final Color core   = new Color();
    private final Color socket = new Color();

    @Override
    public void paint(ShapeRenderer shapes, SpriteBatch batch, float centerX, float centerY,
                      float size, Color accent, float animationT) {
        float alpha = accent.a;
        float radius = size * 0.5f;

        // Hazard-diamond frame.
        RouteIconSupport.stroke(shapes, accent, 0.9f * alpha);
        RouteIconSupport.diamondOutline(shapes, centerX, centerY, radius);

        drawSkull(shapes, centerX, centerY, radius * 0.62f, accent, alpha, 1f);
    }

    /**
     * Shared minimal skull used by ELITE (and, enlarged with horns, by BOSS). {@code emberPulse}
     * brightens the eye sockets toward an ember when &gt; 0 (BOSS only); at 0 the sockets stay dark.
     */
    void drawSkull(ShapeRenderer shapes, float centerX, float centerY, float skullRadius,
                   Color accent, float alpha, float socketBrightness) {
        float domeBottomY = centerY - skullRadius * 0.1f;
        float jawWidth = skullRadius * 0.7f;
        float jawBottomY = domeBottomY - skullRadius * 0.55f;

        // Cranium: bright dome (arc fan, upper half) sitting on a small jaw block.
        RouteIconSupport.fill(shapes, RouteIconSupport.brighten(core, accent, 0.35f), 0.92f * alpha);
        RouteIconSupport.arcFan(shapes, centerX, domeBottomY, skullRadius, 0f, MathUtils.PI, 12);
        // Jaw as a trapezoid (two triangles) narrowing downward.
        shapes.triangle(centerX - jawWidth, domeBottomY, centerX + jawWidth, domeBottomY,
                        centerX - jawWidth * 0.55f, jawBottomY);
        shapes.triangle(centerX + jawWidth, domeBottomY, centerX + jawWidth * 0.55f, jawBottomY,
                        centerX - jawWidth * 0.55f, jawBottomY);

        // Eye sockets (dark, or ember for BOSS).
        float socketRadius = skullRadius * 0.24f;
        float socketOffsetX = skullRadius * 0.42f;
        float socketY = domeBottomY + skullRadius * 0.34f;
        if (socketBrightness > 0f) {
            RouteIconSupport.mix(socket, accent, RouteIconSupport.WHITE_CORE, socketBrightness);
        } else {
            RouteIconSupport.darken(socket, accent, 0.85f);
        }
        RouteIconSupport.fill(shapes, socket, alpha);
        RouteIconSupport.disc(shapes, centerX - socketOffsetX, socketY, socketRadius);
        RouteIconSupport.disc(shapes, centerX + socketOffsetX, socketY, socketRadius);

        // Jaw notch (dark centre gap between the "teeth").
        RouteIconSupport.fill(shapes, RouteIconSupport.darken(socket, accent, 0.8f), 0.9f * alpha);
        shapes.rect(centerX - socketRadius * 0.4f, jawBottomY, socketRadius * 0.8f, skullRadius * 0.3f);
    }
}
