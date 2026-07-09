package ge.tbegvadze.toon3d.render.routeicons;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * A larger heraldic "wing badge" drawn on the route-map title plate for the current region
 * (route-map order-5) — the visual signature that makes each act FEEL different. Same registry
 * pattern as {@link IconPainter}: one crest per region theme, selected by {@code RouteRegion.themeId}
 * through the {@link RegionCrestPainterRegistry}. A new region (order-10) registers its crest here.
 *
 * <p>Like node icons, crests are drawn from {@link ShapeRenderer} primitives only (no assets) and
 * direct into the overlay's active {@code ShapeRenderer}; switch fill/line with {@code shapes.set}.
 * Crests carry no {@code SpriteBatch} — they are pure geometry.
 */
public interface RegionCrestPainter {

    /**
     * Draws the region crest centred at ({@code centerX}, {@code centerY}) inside a {@code size×size}
     * box, tinted by {@code tint} (the region theme colour; {@code tint.a} is the master opacity).
     *
     * @param animationT free-running seconds for any subtle motion; deterministic.
     */
    void paint(ShapeRenderer shapes, float centerX, float centerY, float size, Color tint, float animationT);
}
