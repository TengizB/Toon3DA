package ge.tbegvadze.toon3d.render.routeicons;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

import ge.tbegvadze.toon3d.util.GameMath;

/**
 * Stateless drawing helpers shared by every {@link IconPainter} and {@link RegionCrestPainter}
 * (route-map order-5). These centralise the handful of primitive shapes the icon vocabulary is built
 * from — filled discs, rings, diamonds, colour brighten/darken — so nine painters do not each
 * re-derive them. Every method draws through the caller's active {@link ShapeRenderer} (already
 * inside a {@code begin()}/{@code end()}); type switches use {@code shapes.set(...)} which
 * auto-flushes, so a painter can interleave fills and lines freely.
 *
 * <p>All colour helpers write into a caller-supplied out-parameter {@link Color} (the painter's own
 * pre-allocated scratch field) and return it — no allocation, honouring the no-allocation-in-render
 * rule. Colour maths that is more than a trivial channel lerp lives in {@link GameMath}; the shared
 * palette anchors ({@link #WHITE_CORE}, {@link #DARK_SOCKET}) are the only fixed tints painters mix
 * toward, keeping each icon "accent-tinted with a brighter core and darker detail".
 */
final class RouteIconSupport {

    private RouteIconSupport() {}

    /** The near-white the icon core is mixed toward for the "brighter core" read. */
    static final Color WHITE_CORE  = new Color(0.96f, 0.99f, 1f, 1f);
    /** The deep navy that eye sockets / seams / recesses are mixed toward for shadow detail. */
    static final Color DARK_SOCKET = new Color(0.04f, 0.06f, 0.10f, 1f);

    // ---- Colour mixing (out-param, no allocation) ---------------------------

    /**
     * Writes {@code base} blended toward {@code target} by {@code amount} into {@code out}, preserving
     * {@code base}'s alpha (the master opacity), and returns {@code out}.
     */
    static Color mix(Color out, Color base, Color target, float amount) {
        out.r = GameMath.lerp(base.r, target.r, amount);
        out.g = GameMath.lerp(base.g, target.g, amount);
        out.b = GameMath.lerp(base.b, target.b, amount);
        out.a = base.a;
        return out;
    }

    /** {@code base} pushed toward white by {@code amount} (the brighter icon core). */
    static Color brighten(Color out, Color base, float amount) {
        return mix(out, base, WHITE_CORE, amount);
    }

    /** {@code base} pushed toward deep navy by {@code amount} (recessed detail / sockets). */
    static Color darken(Color out, Color base, float amount) {
        return mix(out, base, DARK_SOCKET, amount);
    }

    // ---- Mode + colour ------------------------------------------------------

    /** Switches the renderer to filled mode and sets {@code color} scaled to {@code alpha}. */
    static void fill(ShapeRenderer shapes, Color color, float alpha) {
        shapes.set(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(color.r, color.g, color.b, MathUtils.clamp(alpha, 0f, 1f));
    }

    /** Switches the renderer to line mode and sets {@code color} scaled to {@code alpha}. */
    static void stroke(ShapeRenderer shapes, Color color, float alpha) {
        shapes.set(ShapeRenderer.ShapeType.Line);
        shapes.setColor(color.r, color.g, color.b, MathUtils.clamp(alpha, 0f, 1f));
    }

    // ---- Primitive shapes (assume the correct mode has already been set) ----

    /** Filled disc — call {@link #fill} first. */
    static void disc(ShapeRenderer shapes, float centerX, float centerY, float radius) {
        shapes.circle(centerX, centerY, radius);
    }

    /** Ring outline — call {@link #stroke} first. */
    static void ring(ShapeRenderer shapes, float centerX, float centerY, float radius) {
        shapes.circle(centerX, centerY, radius);
    }

    /** Filled diamond (rotated square), point-up — call {@link #fill} first. */
    static void filledDiamond(ShapeRenderer shapes, float centerX, float centerY, float radius) {
        shapes.triangle(centerX, centerY + radius, centerX - radius, centerY, centerX + radius, centerY);
        shapes.triangle(centerX, centerY - radius, centerX - radius, centerY, centerX + radius, centerY);
    }

    /** Diamond outline, point-up — call {@link #stroke} first. */
    static void diamondOutline(ShapeRenderer shapes, float centerX, float centerY, float radius) {
        shapes.line(centerX, centerY + radius, centerX + radius, centerY);
        shapes.line(centerX + radius, centerY, centerX, centerY - radius);
        shapes.line(centerX, centerY - radius, centerX - radius, centerY);
        shapes.line(centerX - radius, centerY, centerX, centerY + radius);
    }

    /**
     * A convex-polygon outline whose {@code cornerCount} vertices lie on a circle of {@code radius},
     * the first vertex at {@code startAngleRadians} — used for hexagons/coins. Call {@link #stroke}
     * first. Uses {@link GameMath#pointOnCircleX}/{@code Y} so the vertex maths is not inlined.
     */
    static void polygonOutline(ShapeRenderer shapes, float centerX, float centerY, float radius,
                               int cornerCount, float startAngleRadians) {
        float previousX = 0f;
        float previousY = 0f;
        for (int corner = 0; corner <= cornerCount; corner++) {
            float cornerAngle = startAngleRadians + corner * MathUtils.PI2 / cornerCount;
            float pointX = GameMath.pointOnCircleX(centerX, cornerAngle, radius);
            float pointY = GameMath.pointOnCircleY(centerY, cornerAngle, radius);
            if (corner > 0) {
                shapes.line(previousX, previousY, pointX, pointY);
            }
            previousX = pointX;
            previousY = pointY;
        }
    }

    /**
     * A "fan" of filled triangles approximating a half-disc (a dome) spanning {@code fromAngleRadians}
     * to {@code toAngleRadians}, apexing at the centre — used for skull cranium domes. Call
     * {@link #fill} first. Vertices come from {@link GameMath#pointOnCircleX}/{@code Y}.
     */
    static void arcFan(ShapeRenderer shapes, float centerX, float centerY, float radius,
                       float fromAngleRadians, float toAngleRadians, int segments) {
        float previousX = GameMath.pointOnCircleX(centerX, fromAngleRadians, radius);
        float previousY = GameMath.pointOnCircleY(centerY, fromAngleRadians, radius);
        for (int step = 1; step <= segments; step++) {
            float parameter = step / (float) segments;
            float angle = GameMath.lerp(fromAngleRadians, toAngleRadians, parameter);
            float pointX = GameMath.pointOnCircleX(centerX, angle, radius);
            float pointY = GameMath.pointOnCircleY(centerY, angle, radius);
            shapes.triangle(centerX, centerY, previousX, previousY, pointX, pointY);
            previousX = pointX;
            previousY = pointY;
        }
    }

    /**
     * A curved stroke sampled along a quadratic bezier (start -&gt; control -&gt; end) as short line
     * segments — used for horns, heartbeat traces, broadcast tails. Call {@link #stroke} first. Reuses
     * {@link GameMath#quadraticBezier} per axis (no inline curve maths).
     */
    static void bezierStroke(ShapeRenderer shapes, float startX, float startY,
                             float controlX, float controlY, float endX, float endY, int segments) {
        float previousX = startX;
        float previousY = startY;
        for (int step = 1; step <= segments; step++) {
            float parameter = step / (float) segments;
            float pointX = GameMath.quadraticBezier(startX, controlX, endX, parameter);
            float pointY = GameMath.quadraticBezier(startY, controlY, endY, parameter);
            shapes.line(previousX, previousY, pointX, pointY);
            previousX = pointX;
            previousY = pointY;
        }
    }
}
