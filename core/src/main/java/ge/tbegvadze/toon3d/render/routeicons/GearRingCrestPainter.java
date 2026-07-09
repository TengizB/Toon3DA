package ge.tbegvadze.toon3d.render.routeicons;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import ge.tbegvadze.toon3d.util.GameMath;

/**
 * OUTER FACILITY crest (route-map order-5): a gear ring — a hub ring encircled by radial teeth,
 * slowly rotating. The industrial "machine sector" signature. Direct-drawn, tint = region theme.
 */
final class GearRingCrestPainter implements RegionCrestPainter {

    private static final int TEETH = 10;

    private final Color core = new Color();

    @Override
    public void paint(ShapeRenderer shapes, float centerX, float centerY, float size, Color tint, float animationT) {
        float alpha = tint.a;
        float hubRadius = size * 0.34f;
        float toothInner = hubRadius * 1.02f;
        float toothOuter = size * 0.5f;
        float spin = animationT * 0.4f;

        // Radial teeth as short thick spokes (line mode).
        RouteIconSupport.stroke(shapes, tint, 0.92f * alpha);
        for (int tooth = 0; tooth < TEETH; tooth++) {
            float angle = spin + tooth * MathUtils.PI2 / TEETH;
            shapes.line(GameMath.pointOnCircleX(centerX, angle, toothInner),
                        GameMath.pointOnCircleY(centerY, angle, toothInner),
                        GameMath.pointOnCircleX(centerX, angle, toothOuter),
                        GameMath.pointOnCircleY(centerY, angle, toothOuter));
        }

        // Hub ring + inner bore.
        RouteIconSupport.ring(shapes, centerX, centerY, hubRadius);
        RouteIconSupport.ring(shapes, centerX, centerY, hubRadius * 0.45f);
        RouteIconSupport.fill(shapes, RouteIconSupport.brighten(core, tint, 0.4f), alpha);
        RouteIconSupport.disc(shapes, centerX, centerY, hubRadius * 0.18f);
    }
}
