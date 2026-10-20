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
    private final Map<String, int[][]> cache = new HashMap<>();

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

    /** Level 0 (the full {@code SURFACE_MATERIAL_SIZE} texels) of the material. Read-only. */
    public int[] getPixels(String id) {
        return getPixels(id, 0);
    }

    /**
     * The material's packed RGBA pixels at a mip level: level 0 is {@code SURFACE_MATERIAL_SIZE}-squared,
     * each further level halves the edge (64, 32, 16, 8) via a 2x2 box filter of the previous one. Generated
     * and cached on first request, shared across floors. Read-only: do not mutate the returned array.
     *
     * @throws IllegalArgumentException if the id is not registered or the level is out of range
     */
    public synchronized int[] getPixels(String id, int level) {
        if (level < 0 || level >= RenderConstants.SURFACE_MATERIAL_MIP_LEVELS) {
            throw new IllegalArgumentException("Surface material mip level out of range: " + level);
        }
        int[][] chain = cache.get(id);
        if (chain == null) {
            SurfaceMaterialGenerator generator = id == null ? null : generators.get(id);
            if (generator == null) {
                throw new IllegalArgumentException("Unknown surface material: " + id);
            }
            chain = new int[RenderConstants.SURFACE_MATERIAL_MIP_LEVELS][];
            chain[0] = generator.generate(RenderConstants.SURFACE_MATERIAL_SIZE);
            int sourceSize = RenderConstants.SURFACE_MATERIAL_SIZE;
            for (int mipLevel = 1; mipLevel < chain.length; mipLevel++) {
                chain[mipLevel] = boxFilterHalf(chain[mipLevel - 1], sourceSize);
                sourceSize >>= 1;
            }
            cache.put(id, chain);
        }
        return chain[level];
    }

    /*
     * 2x2 box filter: each output texel is the per-channel (R,G,B,A) average of the 2x2 source block, with
     * the same row-major layout and orientation (row 0 = south edge). +2 rounds to nearest; a sum of four
     * 8-bit channels (max 1020) cannot overflow int. sourceSize must be even (powers of two down to 8).
     */
    private static int[] boxFilterHalf(int[] source, int sourceSize) {
        int targetSize = sourceSize / 2;
        int[] target = new int[targetSize * targetSize];
        for (int row = 0; row < targetSize; row++) {
            for (int column = 0; column < targetSize; column++) {
                int sourceIndex = (row * 2) * sourceSize + column * 2;
                int a = source[sourceIndex];
                int b = source[sourceIndex + 1];
                int c = source[sourceIndex + sourceSize];
                int d = source[sourceIndex + sourceSize + 1];
                int packed = 0;
                for (int shift = 0; shift <= 24; shift += 8) {
                    int sum = ((a >>> shift) & 0xFF) + ((b >>> shift) & 0xFF)
                            + ((c >>> shift) & 0xFF) + ((d >>> shift) & 0xFF);
                    packed |= ((sum + 2) >> 2) << shift;
                }
                target[row * targetSize + column] = packed;
            }
        }
        return target;
    }
}
