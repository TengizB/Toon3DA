package ge.tbegvadze.toon3d.level;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Byte-for-byte regression guard for the {@code LevelGenerator} tileset order-5 refactor.
 *
 * <p>order-5 relocates the generator's room logic behind a registry WITHOUT changing behaviour: the
 * same seed must always produce the identical level grid. This test digests the full generated grid
 * across a spread of seeds and depths (so every room type — including the depth-gated
 * STELLAR_OBSERVATORY — is exercised) into one stable SHA-256 fingerprint. The expected digest was
 * captured from the pre-refactor generator; if any refactor perturbs the RNG draw sequence or a
 * stamped tile, the fingerprint changes and this test fails.
 */
class LevelGeneratorSnapshotTest {

    /**
     * SHA-256 over {@link #fingerprint()} captured from the pre-order-5 generator. Do NOT hand-edit;
     * regenerate only via a deliberate, reviewed behaviour change.
     */
    private static final String EXPECTED_DIGEST =
            "558072ab571d0a6aa9cbc47e4435590d8693d4498512ef50150f2aaa8d12923f";

    @Test
    void generatedGridsAreByteForByteStableAcrossSeedsAndDepths() {
        assertEquals(EXPECTED_DIGEST, digest(fingerprint()),
                "LevelGenerator output changed — order-5 must be a pure structural refactor");
    }

    /** Concatenates every generated grid (seeds 1..40 × depths 1,3,5,7) into one string. */
    private static String fingerprint() {
        StringBuilder builder = new StringBuilder();
        int[] depths = { 1, 3, 5, 7 };
        for (long seed = 1; seed <= 40; seed++) {
            for (int depth : depths) {
                Level level = new LevelGenerator(seed).generate(depth);
                builder.append("seed=").append(seed).append(" depth=").append(depth).append('\n');
                for (int tileRow = level.getHeight() - 1; tileRow >= 0; tileRow--) {
                    for (int tileColumn = 0; tileColumn < level.getWidth(); tileColumn++) {
                        builder.append(level.getCell(tileColumn, tileRow));
                    }
                    builder.append('\n');
                }
            }
        }
        return builder.toString();
    }

    private static String digest(String content) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(Character.forDigit((value >> 4) & 0xF, 16));
                hex.append(Character.forDigit(value & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }
}
