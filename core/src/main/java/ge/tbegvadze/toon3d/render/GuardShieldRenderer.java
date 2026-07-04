package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;

import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.util.EffectConstants;
import ge.tbegvadze.toon3d.util.GameMath;

/**
 * Screen-space overlay for the player GUARD stance (strategy-combat-order-4): a translucent blue
 * shield-arc rim drawn across the bottom-centre of the 3D view whenever the player is braced, so the
 * marine constantly SEES they are protected on the faced side. Rendered as a thin annulus segment
 * (see {@link EffectConstants} GUARD_SHIELD_* for the geometry) with a gentle breathing alpha pulse.
 *
 * <p>Owns a single {@link ShapeRenderer}, disposed in {@link #dispose()}. No per-frame allocation.
 */
public final class GuardShieldRenderer implements Renderable, Disposable {

    private final Player        player;
    private final ShapeRenderer shapeRenderer;
    private       float         elapsedSeconds = 0f;

    public GuardShieldRenderer(Player player) {
        this.player        = player;
        this.shapeRenderer = new ShapeRenderer();
    }

    /** Advances the breathing pulse clock. Safe to call every frame regardless of guard state. */
    public void update(float deltaTime) {
        elapsedSeconds += deltaTime;
    }

    @Override
    public void render(OrthographicCamera camera) {
        if (player == null || !player.isGuarding()) return;

        // Breathing alpha: ping-pong between MIN and MAX at the configured rate.
        float pulse = 0.5f + 0.5f * MathUtils.sin(MathUtils.PI2 * EffectConstants.GUARD_SHIELD_PULSE_HZ * elapsedSeconds);
        float alpha = GameMath.lerp(EffectConstants.GUARD_SHIELD_ALPHA_MIN,
                                    EffectConstants.GUARD_SHIELD_ALPHA_MAX, pulse);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(EffectConstants.GUARD_SHIELD_RED, EffectConstants.GUARD_SHIELD_GREEN,
                               EffectConstants.GUARD_SHIELD_BLUE, alpha);

        float centerX     = EffectConstants.GUARD_SHIELD_CENTER_X;
        float centerY     = EffectConstants.GUARD_SHIELD_CENTER_Y;
        float innerRadius = EffectConstants.GUARD_SHIELD_INNER_RADIUS;
        float outerRadius = EffectConstants.GUARD_SHIELD_OUTER_RADIUS;
        float startDeg    = EffectConstants.GUARD_SHIELD_ARC_START_DEGREES;
        float endDeg      = EffectConstants.GUARD_SHIELD_ARC_END_DEGREES;
        int   segments    = EffectConstants.GUARD_SHIELD_SEGMENTS;
        float stepDeg     = (endDeg - startDeg) / segments;

        // Emit the annulus band as quads (two triangles each) between the inner and outer radius.
        for (int segmentIndex = 0; segmentIndex < segments; segmentIndex++) {
            float angle1 = startDeg + stepDeg * segmentIndex;
            float angle2 = angle1 + stepDeg;
            float cos1 = MathUtils.cosDeg(angle1), sin1 = MathUtils.sinDeg(angle1);
            float cos2 = MathUtils.cosDeg(angle2), sin2 = MathUtils.sinDeg(angle2);

            float innerX1 = centerX + innerRadius * cos1, innerY1 = centerY + innerRadius * sin1;
            float outerX1 = centerX + outerRadius * cos1, outerY1 = centerY + outerRadius * sin1;
            float innerX2 = centerX + innerRadius * cos2, innerY2 = centerY + innerRadius * sin2;
            float outerX2 = centerX + outerRadius * cos2, outerY2 = centerY + outerRadius * sin2;

            shapeRenderer.triangle(innerX1, innerY1, outerX1, outerY1, outerX2, outerY2);
            shapeRenderer.triangle(innerX1, innerY1, outerX2, outerY2, innerX2, innerY2);
        }

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
    }
}
