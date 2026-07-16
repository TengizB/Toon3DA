package ge.tbegvadze.toon3d.tileset;

import ge.tbegvadze.toon3d.util.GameMath;

/**
 * A tiny, fully deterministic pseudo-random source for the {@link SymbolAllocator}. It is the tileset
 * twin of route/RouteRng: it wraps a 64-bit state that advances by the golden-ratio increment between
 * draws and finalises each state through {@link GameMath#splitMix64(long)}. Seeded from
 * {@link GameMath#paletteSymbolSeed(long, char)} (one stream per level-file symbol), it makes every
 * variety pick a pure function of the level seed and the symbol — never of loop order.
 *
 * <p>Package-private helper — not part of the public tileset API. All the mixing math lives in
 * {@link GameMath}; this class only owns the advancing state (a side-effecting cursor, which is why it
 * cannot live in the pure-function GameMath). It duplicates RouteRng's shape rather than reusing it
 * because RouteRng is package-private to {@code route} and this stream serves a different subsystem.
 */
final class PaletteRng {

    private static final long GOLDEN_INCREMENT = 0x9E3779B97F4A7C15L;

    private long state;

    PaletteRng(long seed) {
        this.state = seed;
    }

    /** The next well-mixed 64-bit value. */
    long nextLong() {
        state += GOLDEN_INCREMENT;
        return GameMath.splitMix64(state);
    }

    /** A uniform float in [0, 1). */
    float nextFloat01() {
        // Take the top 24 bits so the mantissa is filled exactly; divide by 2^24.
        long bits = nextLong() >>> 40;
        return bits / (float) (1 << 24);
    }

    /**
     * A uniform integer in [minimumInclusive, maximumInclusive].
     *
     * @throws IllegalArgumentException if the range is empty (min &gt; max)
     */
    int nextIntInclusive(int minimumInclusive, int maximumInclusive) {
        if (minimumInclusive > maximumInclusive) {
            throw new IllegalArgumentException(
                    "Empty range: " + minimumInclusive + " > " + maximumInclusive);
        }
        int span = maximumInclusive - minimumInclusive + 1;
        long unsigned = nextLong() >>> 1; // drop sign so the modulo stays non-negative
        return minimumInclusive + (int) (unsigned % span);
    }
}
