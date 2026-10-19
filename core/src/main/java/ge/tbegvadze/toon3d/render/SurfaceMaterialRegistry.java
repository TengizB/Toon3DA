package ge.tbegvadze.toon3d.render;

import ge.tbegvadze.toon3d.util.RenderConstants;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Catalog of floor/ceiling surface materials (id to {@link SurfaceMaterialGenerator}) with a pixel cache.
 * Materials are deterministic, so {@link #getPixels(String)} generates once and reuses the array across
 * floors. The returned arrays are shared and MUST be treated as read-only. Headless, no LibGDX imports.
 */
public final class SurfaceMaterialRegistry {

    private final Map<String, SurfaceMaterialGenerator> generators = new LinkedHashMap<>();
    private final Map<String, int[]> cache = new HashMap<>();

    /** @throws IllegalStateException if the id is already registered */
    public synchronized void register(String id, SurfaceMaterialGenerator generator) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(generator, "generator");
        if (generators.containsKey(id)) {
            throw new IllegalStateException("Surface material already registered: " + id);
        }
        generators.put(id, generator);
    }

    public synchronized boolean has(String id) {
        return id != null && generators.containsKey(id);
    }

    public synchronized Set<String> ids() {
        return Collections.unmodifiableSet(new java.util.LinkedHashSet<>(generators.keySet()));
    }

    /**
     * The material's {@code SURFACE_MATERIAL_SIZE}-squared packed RGBA pixels, generated on first request
     * and cached. Read-only: do not mutate the returned array.
     *
     * @throws IllegalArgumentException if the id is not registered
     */
    public synchronized int[] getPixels(String id) {
        int[] pixels = cache.get(id);
        if (pixels == null) {
            SurfaceMaterialGenerator generator = id == null ? null : generators.get(id);
            if (generator == null) {
                throw new IllegalArgumentException("Unknown surface material: " + id);
            }
            pixels = generator.generate(RenderConstants.SURFACE_MATERIAL_SIZE);
            cache.put(id, pixels);
        }
        return pixels;
    }
}
