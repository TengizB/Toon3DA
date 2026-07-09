package ge.tbegvadze.toon3d.render.routeicons;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import ge.tbegvadze.toon3d.util.GameMath;

/**
 * RESEARCH WING crest (route-map order-5): a molecule / atom — a nucleus ringed by three slanted
 * orbit rings, each carrying a bright electron that revolves with {@code animationT}. The "science
 * sector" signature. Direct-drawn, tint = region theme.
 */
final class MoleculeCrestPainter implements RegionCrestPainter {

    private static final int ORBITS = 3;

    private final Color core = new Color();

    @Override
    public void paint(ShapeRenderer shapes, float centerX, float centerY, float size, Color tint, float animationT) {
        float alpha = tint.a;
        float orbitRadius = size * 0.5f;

        // Three orbit rings, each rotated a third of a turn, drawn as tilted ellipses (sampled).
        RouteIconSupport.stroke(shapes, tint, 0.85f * alpha);
        for (int orbit = 0; orbit < ORBITS; orbit++) {
            float tilt = orbit * MathUtils.PI / ORBITS;
            drawTiltedOrbit(shapes, centerX, centerY, orbitRadius, orbitRadius * 0.4f, tilt);
        }

        // Electrons revolving on each orbit.
        Color bright = RouteIconSupport.brighten(core, tint, 0.55f);
        RouteIconSupport.fill(shapes, bright, alpha);
        for (int orbit = 0; orbit < ORBITS; orbit++) {
            float tilt = orbit * MathUtils.PI / ORBITS;
            float phase = animationT * 1.4f + orbit * MathUtils.PI2 / ORBITS;
            // point on the un-tilted ellipse, then rotate by tilt.
            float ellipseX = orbitRadius * MathUtils.cos(phase);
            float ellipseY = orbitRadius * 0.4f * MathUtils.sin(phase);
            float electronX = centerX + GameMath.rotateVectorX(ellipseX, ellipseY, tilt);
            float electronY = centerY + GameMath.rotateVectorY(ellipseX, ellipseY, tilt);
            RouteIconSupport.disc(shapes, electronX, electronY, size * 0.05f);
        }

        // Nucleus.
        RouteIconSupport.disc(shapes, centerX, centerY, size * 0.12f);
    }

    /** A tilted ellipse orbit sampled as a closed line loop (semiMajor across, semiMinor up, rotated). */
    private void drawTiltedOrbit(ShapeRenderer shapes, float centerX, float centerY,
                                 float semiMajor, float semiMinor, float tiltRadians) {
        int segments = 40;
        float previousX = 0f;
        float previousY = 0f;
        for (int step = 0; step <= segments; step++) {
            float angle = step * MathUtils.PI2 / segments;
            float ellipseX = semiMajor * MathUtils.cos(angle);
            float ellipseY = semiMinor * MathUtils.sin(angle);
            float pointX = centerX + GameMath.rotateVectorX(ellipseX, ellipseY, tiltRadians);
            float pointY = centerY + GameMath.rotateVectorY(ellipseX, ellipseY, tiltRadians);
            if (step > 0) {
                shapes.line(previousX, previousY, pointX, pointY);
            }
            previousX = pointX;
            previousY = pointY;
        }
    }
}
