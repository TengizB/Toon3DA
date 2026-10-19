package ge.tbegvadze.toon3d.render;

/**
 * Produces one floor/ceiling surface material on the CPU (no GL).
 *
 * <p>LAYOUT CONTRACT: the returned array has {@code size * size} packed RGBA8888 ints
 * ({@code (red << 24) | (green << 16) | (blue << 8) | 0xFF}), row-major: index = {@code row * size + column}.
 * It is sampled exactly as FloorCeilingRenderer samples its texel arrays today: the tile-local fraction
 * {@code fracX} (west to east) selects the column and {@code fracY} (south to north, world Y-up) selects
 * the row, so {@code row 0} is the tile's SOUTH edge and {@code column 0} its WEST edge. The material must
 * tile seamlessly (all patterns wrap at {@code size}) and be deterministic (integer hashing, never
 * {@code java.util.Random}).
 */
@FunctionalInterface
public interface SurfaceMaterialGenerator {
    int[] generate(int size);
}
