package ge.tbegvadze.toon3d.render.routeicons;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import ge.tbegvadze.toon3d.util.GameMath;

/**
 * THE CORE crest (balance-overhaul order 6): the bottom of the shaft — two nested hexagonal shells
 * counter-turned against each other, six spokes tying them to a bright, slowly breathing heart. Where
 * THE BREACH's sigil is broken, this one is whole and machined: the finale reads as the facility's
 * engine, not its wound. Direct-drawn, tint = region theme (deep violet-white).
 */
final class CoreLatticeCrestPainter implements RegionCrestPainter {

    private static final int SHELL_CORNERS = 6;

    private final Color heart = new Color();

    @Override
    public void paint(ShapeRenderer shapes, float centerX, float centerY, float size, Color tint, float animationT) {
        float alpha = tint.a;
        float radius = size * 0.5f;
        float outerTurnRadians = animationT * 0.15f;
        float innerTurnRadians = -animationT * 0.25f;

        // Outer and inner machined shells, slowly counter-rotating.
        RouteIconSupport.stroke(shapes, tint, 0.9f * alpha);
        RouteIconSupport.polygonOutline(shapes, centerX, centerY, radius, SHELL_CORNERS, outerTurnRadians);
        RouteIconSupport.polygonOutline(shapes, centerX, centerY, radius * 0.6f, SHELL_CORNERS,
                innerTurnRadians + MathUtils.PI / SHELL_CORNERS);

        // Spokes from the inner shell's corners to the outer shell's — the lattice that holds the heart.
        RouteIconSupport.stroke(shapes, RouteIconSupport.brighten(heart, tint, 0.25f), 0.7f * alpha);
        for (int corner = 0; corner < SHELL_CORNERS; corner++) {
            float outerAngle = outerTurnRadians + corner * MathUtils.PI2 / SHELL_CORNERS;
            float innerAngle = innerTurnRadians + MathUtils.PI / SHELL_CORNERS + corner * MathUtils.PI2 / SHELL_CORNERS;
            shapes.line(GameMath.pointOnCircleX(centerX, innerAngle, radius * 0.6f),
                        GameMath.pointOnCircleY(centerY, innerAngle, radius * 0.6f),
                        GameMath.pointOnCircleX(centerX, outerAngle, radius),
                        GameMath.pointOnCircleY(centerY, outerAngle, radius));
        }

        // The heart: a white-hot core that breathes, inside a thin containment ring.
        float breath = 0.85f + 0.15f * MathUtils.sin(animationT * 2.2f);
        RouteIconSupport.stroke(shapes, RouteIconSupport.WHITE_CORE, 0.8f * alpha);
        RouteIconSupport.ring(shapes, centerX, centerY, radius * 0.3f);
        RouteIconSupport.fill(shapes, RouteIconSupport.mix(heart, tint, RouteIconSupport.WHITE_CORE, 0.6f), alpha);
        RouteIconSupport.disc(shapes, centerX, centerY, radius * 0.18f * breath);
    }
}
