package ge.tbegvadze.toon3d.render.routeicons;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import ge.tbegvadze.toon3d.util.GameMath;

/**
 * REACTOR DEPTHS crest (route-map order-5): the radiation trefoil — three filled blades at 120°
 * around a central hub, inside a warning ring. The "hot core" signature. Direct-drawn, tint = region
 * theme (amber-hot).
 */
final class RadiationTrefoilCrestPainter implements RegionCrestPainter {

    private static final int BLADES = 3;

    private final Color core = new Color();

    @Override
    public void paint(ShapeRenderer shapes, float centerX, float centerY, float size, Color tint, float animationT) {
        float alpha = tint.a;
        float outerRadius = size * 0.5f;
        float bladeInner = outerRadius * 0.24f;
        float bladeOuter = outerRadius * 0.9f;
        float bladeHalfSpanRadians = MathUtils.PI / 6f; // 30 degrees -> 60 degree wedge per blade

        // Warning ring.
        RouteIconSupport.stroke(shapes, tint, 0.85f * alpha);
        RouteIconSupport.ring(shapes, centerX, centerY, outerRadius);

        // Three filled wedge blades.
        RouteIconSupport.fill(shapes, RouteIconSupport.brighten(core, tint, 0.2f), 0.95f * alpha);
        for (int blade = 0; blade < BLADES; blade++) {
            float centreAngle = MathUtils.PI / 2f + blade * MathUtils.PI2 / BLADES;
            float leftAngle = centreAngle - bladeHalfSpanRadians;
            float rightAngle = centreAngle + bladeHalfSpanRadians;
            // Wedge as a fan of triangles from the inner radius arc to the outer radius arc.
            int steps = 6;
            for (int step = 0; step < steps; step++) {
                float angleA = GameMath.lerp(leftAngle, rightAngle, step / (float) steps);
                float angleB = GameMath.lerp(leftAngle, rightAngle, (step + 1) / (float) steps);
                float innerAX = GameMath.pointOnCircleX(centerX, angleA, bladeInner);
                float innerAY = GameMath.pointOnCircleY(centerY, angleA, bladeInner);
                float outerAX = GameMath.pointOnCircleX(centerX, angleA, bladeOuter);
                float outerAY = GameMath.pointOnCircleY(centerY, angleA, bladeOuter);
                float outerBX = GameMath.pointOnCircleX(centerX, angleB, bladeOuter);
                float outerBY = GameMath.pointOnCircleY(centerY, angleB, bladeOuter);
                float innerBX = GameMath.pointOnCircleX(centerX, angleB, bladeInner);
                float innerBY = GameMath.pointOnCircleY(centerY, angleB, bladeInner);
                shapes.triangle(innerAX, innerAY, outerAX, outerAY, outerBX, outerBY);
                shapes.triangle(innerAX, innerAY, outerBX, outerBY, innerBX, innerBY);
            }
        }

        // Central hub disc.
        RouteIconSupport.fill(shapes, RouteIconSupport.darken(core, tint, 0.4f), alpha);
        RouteIconSupport.disc(shapes, centerX, centerY, bladeInner * 0.9f);
    }
}
