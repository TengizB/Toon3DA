package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.util.GameMath;

/**
 * A tiny, fully deterministic pseudo-random source for route-map generation. Wraps a 64-bit state
 * that advances by the golden-ratio increment between draws and passes each state through
 * {@link GameMath#splitMix64(long)}. Given the same seed it always yields the same sequence, which is
 * what makes a whole map reproducible from the run seed.
 *
 * <p>Package-private helper — not part of the public route API. All the actual mixing math lives in
 * {@link GameMath}; this class only owns the advancing state (a side-effecting cursor, which is why
 * it cannot itself live in the pure-function GameMath).
 */
final class RouteRng {

    private static final long GOLDEN_INCREMENT = 0x9E3779B97F4A7C15L;

    private long state;

    RouteRng(long seed) {
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
     * @throws IllegalArgumentException if the range is empty (min > max)
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

    /** True with the given probability (0..1). */
    boolean nextChance(float probability) {
        return nextFloat01() < probability;
    }
}
