package ge.tbegvadze.toon3d.level;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Determinism guard for {@code LevelGenerator}: the same seed must always produce the identical level
 * grid. This test digests the full generated grid across a spread of seeds and depths (so every room
 * type — including the depth-gated STELLAR_OBSERVATORY and the order-8 SALVAGE_BAY — is exercised) into
 * one stable SHA-256 fingerprint.
 *
 * <p>Through order-5 this was a byte-for-byte "pure structural refactor" guard. order-8 is an explicit
 * BEHAVIOUR CHANGE (registry-driven room selection + rule-driven per-level variety), so the digest was
 * re-baselined once, deliberately, from the post-order-8 generator; order-9 registers ONE MORE room
 * (SUPPLY_CACHE, RECIPE B) that competes in the same seeded roulette, shifting the RNG draw sequence, so
 * the digest is re-baselined a second time, deliberately, from the post-order-9 generator. order-10 is a
 * third deliberate re-baseline: the LARGE blueprint is removed (one fewer roulette candidate), placeRooms()
 * now rolls a per-room large-modifier (an extra RNG draw per room), and a minimum-special-rooms backstop
 * upgrades some STANDARD rooms — all of which shift the RNG draw sequence and stamped tiles. It remains a
 * strict determinism contract afterwards: same seed ⇒ same rooms ⇒ same palette ⇒ same grid (permadeath /
 * seed-sharing fairness). If any later change perturbs the RNG draw sequence or a stamped tile, the
 * fingerprint changes and this test fails.
 */
class LevelGeneratorSnapshotTest {

    /**
     * SHA-256 over {@link #fingerprint()}, re-baselined for the GORE_NEST room addition (RECIPE B): one
     * more blueprint competes in the STEP-A weighted roulette and, when selected on a depth&gt;=3 room, it
     * stamps its own gore/rust wall theming + bespoke nest-prop pass — both of which shift the RNG draw
     * sequence and the stamped tiles. This sits on top of the order-10 generator (LARGE demoted to a
     * per-room size modifier + guaranteed-minimum-special-rooms backstop, order-8/9 registry selection +
     * variety accents + salvage bay + supply cache). Do NOT hand-edit; regenerate only via a deliberate,
     * reviewed behaviour change.
     */
    private static final String EXPECTED_DIGEST =
            "4b7a2c7918612d49782cba9ecfad9c2f4c516540a5edfad9784a58596de5b527";

    @Test
    void generatedGridsAreByteForByteStableAcrossSeedsAndDepths() {
        assertEquals(EXPECTED_DIGEST, digest(fingerprint()),
                "LevelGenerator output changed — same seed must produce the identical grid (determinism)");
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
