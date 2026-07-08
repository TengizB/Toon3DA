package ge.tbegvadze.toon3d.route;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Stable-id round-trip tests. The stable id strings are persisted in save files and run stats, so a
 * regression that changes one would silently break seed reproducibility.
 */
class GeneratorIdTest {

    @Test
    void stableIdsRoundTrip() {
        for (GeneratorId id : GeneratorId.values()) {
            assertEquals(id, GeneratorId.fromStableId(id.stableId()));
        }
    }

    @Test
    void stableIdStringsAreTheDocumentedValues() {
        assertEquals("rooms_mst", GeneratorId.ROOMS_MST.stableId());
        assertEquals("linear_corridor", GeneratorId.LINEAR_CORRIDOR.stableId());
        assertEquals("cavern", GeneratorId.CAVERN.stableId());
        assertEquals("boss_arena", GeneratorId.BOSS_ARENA.stableId());
    }

    @Test
    void unknownStableIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> GeneratorId.fromStableId("does_not_exist"));
    }
}
