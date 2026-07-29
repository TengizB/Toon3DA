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
     * SHA-256 over {@link #fingerprint()}, re-baselined for the ENCOUNTER DENSITY change
     * (.claude/agents/ideas/encounter-density-and-corpse-semantics.txt).
     *
     * <p>NOTE ON THE PREVIOUS BASELINE: it was already STALE before this change — the committed digest
     * did not match the generator on master, so `./gradlew test` was failing. Generation itself was
     * never broken (the digest is byte-stable across separate JVM runs, verified three times); the
     * constant had simply not been re-based after an earlier behaviour change. This re-baseline fixes
     * that too, so the gate is green and meaningful again.
     *
     * <p>Four deliberate behaviour changes perturb the RNG draw sequence and the stamped tiles:
     * (1) the anchor is bounded by a real budget CEILING instead of a fallback that could eat 76% of a
     * small floor, so low-budget floors now roll a different roster; (2) a REMAINDER PASS converts
     * leftover Threat Points into bodies rather than discarding them; (3) chaff packs are placed as one
     * unit into one room, changing which tiles are claimed and in what order; and (4) ambient corpse
     * 'm' decals are no longer generated (the symbol is the runtime death marker), which removes draws
     * from the prop-weight table and re-normalises the remaining decal weights. Authored set-piece
     * corpses are unchanged.
     *
     * <p>It remains a strict determinism contract afterwards: same seed ⇒ same grid. Do NOT hand-edit;
     * regenerate only via a deliberate, reviewed behaviour change.
     *
     * <p>PRIOR RE-BASELINE (retained for history) — the SET-PIECE VISIBILITY change: the big and
     * depth-gated signature rooms that had become effectively unspawnable under the flat equal-chance weight
     * are made reliably reachable again. Specifically: STELLAR_OBSERVATORY / GORE_NEST / ATMOSPHERIC_PLANT
     * regain a per-level cap of 1 (RoomContext.alreadyPlacedOfThisKind gate); GORE_NEST and STELLAR_OBSERVATORY
     * depth gates drop to 2; STELLAR_OBSERVATORY gets a relaxed aspect window (1.4) and a high dedicated
     * selection weight, while GORE_NEST / ATMOSPHERIC_PLANT / POWER_PLANT / COMMAND_CENTER share an above-
     * baseline set-piece weight; and the large-room modifier chance rises so the oversized footprints these
     * rooms need actually occur. Each alters which blueprint the STEP-A weighted roulette picks and the room
     * sizes rolled, shifting the RNG draw sequence and the stamped tiles. This sits on top of the earlier
     * EQUAL-CHANCE, ATMOSPHERIC_PLANT / GORE_NEST, and order-8/9/10 re-baselines. It remains a strict
     * determinism contract: same seed ⇒ same grid. Do NOT hand-edit; regenerate only via a deliberate,
     * reviewed behaviour change.
     */
    private static final String EXPECTED_DIGEST =
            "07068d2178444e072a643fc2de67ae5da387bc711dd30854bfcca81ee7f457c5";

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
