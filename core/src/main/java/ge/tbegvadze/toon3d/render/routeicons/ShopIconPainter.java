package ge.tbegvadze.toon3d.render.routeicons;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

/**
 * SHOP — "credit sigil / vend" (route-map order-5). A hexagon coin outline holding a stylised
 * facility-credit rune (two vertical bars through a circle), with a small down-arrow "dispense" tick
 * beneath. Reads "spend credits". Direct-drawn, accent = credit gold (the node's accent).
 */
final class ShopIconPainter implements IconPainter {

    private final Color core = new Color();

    @Override
    public void paint(ShapeRenderer shapes, SpriteBatch batch, float centerX, float centerY,
                      float size, Color accent, float animationT) {
        float alpha = accent.a;
        float coinRadius = size * 0.44f;
        float coinCenterY = centerY + size * 0.06f;

        // Hexagon coin (flat-top hexagon: first vertex at 30 degrees).
        RouteIconSupport.stroke(shapes, accent, 0.95f * alpha);
        RouteIconSupport.polygonOutline(shapes, centerX, coinCenterY, coinRadius, 6, MathUtils.PI / 6f);

        // Credit rune: a small ring with two vertical bars crossing it.
        float runeRadius = coinRadius * 0.42f;
        Color bright = RouteIconSupport.brighten(core, accent, 0.5f);
        RouteIconSupport.stroke(shapes, bright, alpha);
        RouteIconSupport.ring(shapes, centerX, coinCenterY, runeRadius);
        float barHalf = runeRadius * 1.35f;
        float barOffset = runeRadius * 0.4f;
        shapes.line(centerX - barOffset, coinCenterY - barHalf, centerX - barOffset, coinCenterY + barHalf);
        shapes.line(centerX + barOffset, coinCenterY - barHalf, centerX + barOffset, coinCenterY + barHalf);

        // Down-arrow "dispense" tick beneath the coin.
        float arrowY = coinCenterY - coinRadius - size * 0.06f;
        float arrowHalf = size * 0.14f;
        shapes.line(centerX, arrowY + arrowHalf, centerX, arrowY - arrowHalf);
        shapes.line(centerX - arrowHalf * 0.6f, arrowY - arrowHalf * 0.35f, centerX, arrowY - arrowHalf);
        shapes.line(centerX + arrowHalf * 0.6f, arrowY - arrowHalf * 0.35f, centerX, arrowY - arrowHalf);
    }
}
