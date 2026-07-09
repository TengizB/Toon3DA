package ge.tbegvadze.toon3d.render.routeicons;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import ge.tbegvadze.toon3d.util.RouteMapConstants;

/**
 * BOSS — "horned command skull" (route-map order-5). A larger, meaner skull than ELITE: a broad
 * cranium with two horns curving out, deep eye sockets holding a pulsing red ember, a small spine
 * base, framed by a full hazard diamond. Reads "facility guardian". Direct-drawn, accent = danger
 * red; the overlay draws it slightly larger (a set-piece beat).
 *
 * <p>Reuses {@link EliteIconPainter#drawSkull} for the cranium/jaw/sockets so the family reads
 * consistently, then layers on the boss-only horns, brow, spine and ember pulse.
 */
final class BossIconPainter implements IconPainter {

    private final EliteIconPainter skullSource = new EliteIconPainter();
    private final Color detail = new Color();

    @Override
    public void paint(ShapeRenderer shapes, SpriteBatch batch, float centerX, float centerY,
                      float size, Color accent, float animationT) {
        float alpha = accent.a;
        float radius = size * 0.5f;
        float skullRadius = radius * 0.66f;

        // Full hazard-diamond frame.
        RouteIconSupport.stroke(shapes, accent, 0.9f * alpha);
        RouteIconSupport.diamondOutline(shapes, centerX, centerY, radius);

        // Spine base beneath the skull (a short stack of narrowing bars).
        RouteIconSupport.fill(shapes, RouteIconSupport.darken(detail, accent, 0.4f), 0.85f * alpha);
        float spineTop = centerY - skullRadius * 0.5f;
        for (int vertebra = 0; vertebra < 3; vertebra++) {
            float vertebraWidth = skullRadius * (0.5f - vertebra * 0.1f);
            float vertebraY = spineTop - vertebra * skullRadius * 0.22f;
            shapes.rect(centerX - vertebraWidth, vertebraY, vertebraWidth * 2f, skullRadius * 0.12f);
        }

        // Horns curving up-and-out from the temples (quadratic-bezier strokes, mirrored).
        Color horn = RouteIconSupport.brighten(detail, accent, 0.25f);
        RouteIconSupport.stroke(shapes, horn, 0.95f * alpha);
        float templeY = centerY + skullRadius * 0.28f;
        drawHorn(shapes, centerX, templeY, skullRadius, +1f);
        drawHorn(shapes, centerX, templeY, skullRadius, -1f);

        // The shared skull (ember sockets: pulse the socket brightness for BOSS).
        float emberPulse = RouteMapConstants.ICON_BOSS_EMBER_MIN
                + (RouteMapConstants.ICON_BOSS_EMBER_MAX - RouteMapConstants.ICON_BOSS_EMBER_MIN)
                * (0.5f + 0.5f * MathUtils.sin(animationT * RouteMapConstants.ICON_BOSS_EMBER_SPEED));
        skullSource.drawSkull(shapes, centerX, centerY, skullRadius, accent, alpha, emberPulse);

        // Pronounced brow ridge across the top of the eye sockets.
        RouteIconSupport.stroke(shapes, RouteIconSupport.darken(detail, accent, 0.5f), 0.9f * alpha);
        float browY = centerY + skullRadius * 0.32f;
        shapes.line(centerX - skullRadius * 0.62f, browY, centerX - skullRadius * 0.12f, browY + skullRadius * 0.14f);
        shapes.line(centerX + skullRadius * 0.62f, browY, centerX + skullRadius * 0.12f, browY + skullRadius * 0.14f);
    }

    /**
     * One horn, curving up and out from ({@code templeX}, {@code templeY}). {@code side} = +1 for the
     * right horn, -1 for the left. The bezier control point pulls outward and the end sweeps up-out.
     */
    private void drawHorn(ShapeRenderer shapes, float templeX, float templeY, float skullRadius, float side) {
        float rootX = templeX + side * skullRadius * 0.72f;
        float controlX = templeX + side * skullRadius * 1.5f;
        float controlY = templeY + skullRadius * 0.5f;
        float tipX = templeX + side * skullRadius * 1.15f;
        float tipY = templeY + skullRadius * 1.35f;
        RouteIconSupport.bezierStroke(shapes, rootX, templeY, controlX, controlY, tipX, tipY, 10);
    }
}
