package ge.tbegvadze.toon3d.render.routeicons;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import ge.tbegvadze.toon3d.util.GameMath;

/**
 * THE BREACH crest (route-map order-5): a cracked / corrupted sigil — a broken ring split by jagged
 * fracture lines with a dark void core, jittering subtly with {@code animationT}. The "corruption"
 * signature of the endless region. Direct-drawn, tint = region theme (corrupted violet-red).
 */
final class CorruptedSigilCrestPainter implements RegionCrestPainter {

    private static final int FRACTURES = 5;

    private final Color core = new Color();

    @Override
    public void paint(ShapeRenderer shapes, float centerX, float centerY, float size, Color tint, float animationT) {
        float alpha = tint.a;
        float radius = size * 0.5f;
        float jitter = MathUtils.sin(animationT * 9f) * size * 0.02f;

        // Broken outer ring: arcs with gaps.
        RouteIconSupport.stroke(shapes, tint, 0.9f * alpha);
        int arcs = 4;
        for (int arc = 0; arc < arcs; arc++) {
            float from = arc * MathUtils.PI2 / arcs + 0.25f;
            float to = from + MathUtils.PI2 / arcs - 0.5f;
            float previousX = GameMath.pointOnCircleX(centerX, from, radius);
            float previousY = GameMath.pointOnCircleY(centerY, from, radius);
            int steps = 8;
            for (int step = 1; step <= steps; step++) {
                float angle = GameMath.lerp(from, to, step / (float) steps);
                float pointX = GameMath.pointOnCircleX(centerX, angle, radius);
                float pointY = GameMath.pointOnCircleY(centerY, angle, radius);
                shapes.line(previousX, previousY, pointX, pointY);
                previousX = pointX;
                previousY = pointY;
            }
        }

        // Jagged fracture lines radiating from the void core (alternating length, jittering).
        RouteIconSupport.stroke(shapes, RouteIconSupport.brighten(core, tint, 0.35f), 0.85f * alpha);
        for (int fracture = 0; fracture < FRACTURES; fracture++) {
            float angle = fracture * MathUtils.PI2 / FRACTURES + jitter;
            float innerRadius = radius * 0.16f;
            float midRadius = radius * 0.55f;
            float outerRadius = radius * (0.85f + 0.1f * (fracture % 2));
            // dog-leg crack: core -> mid (kinked) -> outer.
            float kinkAngle = angle + 0.28f;
            float innerX = GameMath.pointOnCircleX(centerX, angle, innerRadius);
            float innerY = GameMath.pointOnCircleY(centerY, angle, innerRadius);
            float midX = GameMath.pointOnCircleX(centerX, kinkAngle, midRadius);
            float midY = GameMath.pointOnCircleY(centerY, kinkAngle, midRadius);
            float outerX = GameMath.pointOnCircleX(centerX, angle, outerRadius);
            float outerY = GameMath.pointOnCircleY(centerY, angle, outerRadius);
            shapes.line(innerX, innerY, midX, midY);
            shapes.line(midX, midY, outerX, outerY);
        }

        // Dark void core.
        RouteIconSupport.fill(shapes, RouteIconSupport.darken(core, tint, 0.75f), alpha);
        RouteIconSupport.disc(shapes, centerX, centerY, radius * 0.2f);
    }
}
