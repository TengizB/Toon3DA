package ge.tbegvadze.toon3d.render.routeicons;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * A procedural drawer for one route-map node icon (route-map order-5). One painter per node type,
 * selected by {@code NodeTypeDefinition.iconPainterId()} through the {@link IconPainterRegistry}.
 *
 * <p><b>Why a painter, not a switch.</b> The overlay (order-4) must never contain
 * {@code switch(nodeType) { case COMBAT: drawCrosshair()... }}. Instead each node type registers a
 * painter, and the overlay just calls {@code registry.get(id).paint(...)}. Adding a new node icon is
 * therefore one new class + one registration line — the extensibility promise applied to visuals.
 *
 * <p><b>Rendering contract.</b> The icon is drawn from {@link ShapeRenderer} primitives only — NO
 * image assets (a hard product requirement). It is drawn <em>direct</em> into the overlay's shared
 * {@code ShapeRenderer} each frame; the overlay has already called {@code shapes.begin(...)} before
 * invoking {@link #paint}. A painter may freely switch between {@link ShapeRenderer.ShapeType#Filled}
 * and {@link ShapeRenderer.ShapeType#Line} with {@code shapes.set(...)} (it auto-flushes) but MUST
 * NOT call {@code begin()}/{@code end()} itself. Use {@link RouteIconSupport} for the common draws.
 *
 * <p>Direct-drawing (rather than baking to a FrameBuffer texture) is a deliberate project decision:
 * {@code FrameBuffer.end()} resets the GL viewport and would corrupt the {@code FitViewport}
 * letterbox mid-frame (see the note in {@code RouteMapOverlayRenderer} / {@code WeaponHudRenderer}),
 * so — exactly like the overlay's additive-disc glow — every icon is drawn straight through the one
 * ShapeRenderer. This keeps the pipeline viewport-safe and allocation-free with no FBO to dispose.
 * The {@code batch} parameter is reserved for a future baked-icon variant and is not begun here.
 *
 * <p><b>Purity.</b> A painter holds no timers: the animation phase is passed in as {@code animationT}
 * (the overlay's free-running {@code overlayTimeSeconds}) so icons stay deterministic. Any
 * pre-allocated scratch {@link Color}s a painter needs are its own fields, reused each call — no
 * allocation inside {@link #paint}.
 */
public interface IconPainter {

    /**
     * Draws this node's icon centred at ({@code centerX}, {@code centerY}) inside a {@code size×size}
     * box, tinted by {@code accent}. The accent's alpha channel ({@code accent.a}) is the master
     * opacity every stroke/fill must respect (the overlay folds the node's perspective fade + flicker
     * into it), so read {@code accent.a} and multiply your local alphas by it rather than assuming 1.
     *
     * @param shapes     the overlay's ShapeRenderer, already inside a begin()/end(); switch types with
     *                   {@code shapes.set(...)}, never begin()/end().
     * @param batch      reserved for future baked icons; NOT begun during the icon pass — do not use.
     * @param centerX    icon box centre X in world units (Y-up, origin bottom-left — project invariant).
     * @param centerY    icon box centre Y in world units.
     * @param size       the icon box side length in world units (already scaled for perspective).
     * @param accent     the node's accent colour; {@code accent.a} carries the master opacity.
     * @param animationT free-running seconds for cheap idle motion (rotation/pulse); deterministic.
     */
    void paint(ShapeRenderer shapes, SpriteBatch batch, float centerX, float centerY,
               float size, Color accent, float animationT);
}
