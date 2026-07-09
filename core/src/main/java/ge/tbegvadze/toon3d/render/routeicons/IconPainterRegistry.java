package ge.tbegvadze.toon3d.render.routeicons;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The registry mapping an {@code iconPainterId} (from {@code NodeTypeDefinition.iconPainterId()}) to
 * its {@link IconPainter} (route-map order-5). This is the render-layer twin of the headless
 * {@code NodeTypeRegistry}: because {@link IconPainter} touches LibGDX it cannot live in the
 * {@code …toon3d.route} package, so painter registration happens here, driven by
 * {@link RouteIconBootstrap}.
 *
 * <p>Adding a node icon is one class + one {@code register(...)} line — the overlay draws it with
 * zero edits via {@link #get(String)}. Unknown ids fall back to a shared placeholder painter so a
 * newly-registered node type that forgot its icon still renders (a diamond) rather than crashing.
 */
public final class IconPainterRegistry {

    private final Map<String, IconPainter> painters = new HashMap<>();
    private final IconPainter fallback;

    /** Builds an empty registry whose fallback is a plain accent diamond. */
    public IconPainterRegistry() {
        this.fallback = new PlaceholderIconPainter();
    }

    /** Registers {@code painter} under {@code iconPainterId}. Later registrations overwrite earlier. */
    public void register(String iconPainterId, IconPainter painter) {
        painters.put(Objects.requireNonNull(iconPainterId, "iconPainterId"),
                     Objects.requireNonNull(painter, "painter"));
    }

    /** The painter for {@code iconPainterId}, or the fallback diamond painter if none is registered. */
    public IconPainter get(String iconPainterId) {
        IconPainter painter = painters.get(iconPainterId);
        return painter != null ? painter : fallback;
    }

    /** Whether an explicit painter is registered for {@code iconPainterId}. */
    public boolean has(String iconPainterId) {
        return painters.containsKey(iconPainterId);
    }
}
