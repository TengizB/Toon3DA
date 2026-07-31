package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.door.DoorManager;
import ge.tbegvadze.toon3d.hazard.SpireManager;
import ge.tbegvadze.toon3d.util.EnemyConstants;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for the SpireManager (Verdant Spiresower's crystal spires,
 * .claude/agents/ideas/elemental-golem-verdant-spiresower.txt).
 *
 * <p>The design's NON-OPTIONAL rule is pinned here: a sow may never seal the player off from the room's
 * exit. {@link SpireManager#wouldSealExit} is the flood-fill guard the sow selector consults, and this test
 * is the one the design explicitly asked for. Also pins the tile bookkeeping that must never leak a solid
 * tile: sow stamps, damage/decay/withering restore.
 *
 * <p>Lives in the {@code level} package because it builds a headless {@link Level} through the
 * package-private char-grid constructor (the same seam {@code LevelPaletteWiringTest} uses) — no LibGDX.
 */
class SpireReachabilityTest {

    private static final char SPIRE = EnemyConstants.SPIRE_TILE_CHAR;

    /**
     * Builds a headless level from rows given TOP-first (row 0 of the argument is the visual top). The
     * internal matrix is row 0 = BOTTOM (Y-up), so the rows are reversed on the way in.
     */
    private static Level level(String... topFirstRows) {
        int height = topFirstRows.length;
        int width  = topFirstRows[0].length();
        char[][] matrix = new char[height][width];
        for (int inputRow = 0; inputRow < height; inputRow++) {
            String rowText = topFirstRows[inputRow];
            int matrixRow  = height - 1 - inputRow; // flip: top input row -> highest matrix index
            for (int column = 0; column < width; column++) {
                matrix[matrixRow][column] = rowText.charAt(column);
            }
        }
        return new Level(matrix, new ArrayList<>(), new ArrayList<>());
    }

    // -------------------------------------------------------------------------
    // The non-optional reachability guard
    // -------------------------------------------------------------------------

    /** A spire plugging the ONLY corridor tile between the player and the exit MUST be rejected. */
    @Test
    void sealingTheSoleCorridorTileIsRejected() {
        // Player at (1,1), exit '>' at (3,1); the only route runs through (2,1).
        Level level = level(
                "xxxxx",
                "x..>x",
                "xxxxx");
        SpireManager spires = new SpireManager(level, new DoorManager(level));
        assertTrue(spires.wouldSealExit(1, 1, 2, 1, null, null, 0),
                "a spire in the sole corridor tile seals the player off from the exit — reject it");
    }

    /** With an alternate route around it, the same candidate does NOT seal the exit and is allowed. */
    @Test
    void aCandidateWithAnAlternateRouteIsAllowed() {
        // A second open row (row 2) gives a way around (2,1).
        Level level = level(
                "xxxxx",
                "x...x",
                "x..>x",
                "xxxxx");
        SpireManager spires = new SpireManager(level, new DoorManager(level));
        assertFalse(spires.wouldSealExit(1, 1, 2, 1, null, null, 0),
                "an alternate route keeps the exit reachable — the candidate is fine");
    }

    /** Two spires that INDIVIDUALLY leave a route can jointly seal it; the guard treats already-chosen ones as solid. */
    @Test
    void jointlySealingCandidatesAreRejectedViaTheExtraBlockedSet() {
        Level level = level(
                "xxxxx",
                "x...x",
                "x..>x",
                "xxxxx");
        SpireManager spires = new SpireManager(level, new DoorManager(level));
        // With (2,1) already chosen (extra-blocked), the only remaining route to the exit runs through
        // (2,2); plugging that too traps the player. Coordinates are (column,row) with row 0 at the bottom.
        int[] extraColumns = { 2 };
        int[] extraRows    = { 1 };
        assertTrue(spires.wouldSealExit(1, 1, 2, 2, extraColumns, extraRows, 1),
                "two spires that combine to block every route must be rejected");
    }

    /** A floor with no exit tile can never be 'sealed', so the guard never blocks a sow there. */
    @Test
    void aFloorWithNoExitIsNeverSealed() {
        Level level = level(
                "xxxxx",
                "x...x",
                "xxxxx");
        SpireManager spires = new SpireManager(level, new DoorManager(level));
        assertFalse(spires.wouldSealExit(1, 1, 2, 1, null, null, 0),
                "no exit means nothing to seal the player away from");
    }

    // -------------------------------------------------------------------------
    // Tile bookkeeping — never leak a solid tile
    // -------------------------------------------------------------------------

    @Test
    void sowStampsAndDamageRestores() {
        Level level = level(
                "xxxxx",
                "x...x",
                "x..>x",
                "xxxxx");
        SpireManager spires = new SpireManager(level, new DoorManager(level));

        assertTrue(spires.sowSpire(2, 2, 7, EnemyConstants.SPIRESOWER_SPIRE_LIFETIME_TURNS));
        assertTrue(spires.isSpireAt(2, 2));
        assertEquals(SPIRE, level.getCell(2, 2), "sowing stamps the spire char");
        assertEquals(1, spires.liveSpireCount());
        assertEquals(1, spires.countLivingSpiresNear(2, 2, EnemyConstants.SPIRESOWER_LEY_RANGE_TILES));

        // A sub-lethal hit leaves it standing; a lethal one restores the floor tile.
        boolean hitNonLethal = spires.damageSpireAt(2, 2, EnemyConstants.SPIRESOWER_SPIRE_HIT_POINTS - 1);
        assertTrue(hitNonLethal && spires.isSpireAt(2, 2), "the spire survives a sub-lethal hit");
        spires.damageSpireAt(2, 2, EnemyConstants.SPIRESOWER_SPIRE_HIT_POINTS);
        assertFalse(spires.isSpireAt(2, 2), "the spire dies once its HP is spent");
        assertEquals('.', level.getCell(2, 2), "a destroyed spire restores the floor tile it overlaid");
        assertEquals(0, spires.liveSpireCount());
    }

    @Test
    void spiresDecayAfterTheirLifetimeAndRestoreTheirTiles() {
        Level level = level(
                "xxxxx",
                "x...x",
                "xxxxx");
        SpireManager spires = new SpireManager(level, new DoorManager(level));
        spires.sowSpire(2, 1, 3, 2); // lifetime 2 turns
        assertTrue(spires.isSpireAt(2, 1));
        spires.tickLifetimes();
        assertTrue(spires.isSpireAt(2, 1), "still standing after one turn");
        spires.tickLifetimes();
        assertFalse(spires.isSpireAt(2, 1), "gone after its lifetime elapses");
        assertEquals('.', level.getCell(2, 1), "decay restores the floor tile — no leaked solid tile");
    }

    @Test
    void witheringAndClearRestoreEveryTileOfAnOwner() {
        Level level = level(
                "xxxxx",
                "x...x",
                "x...x",
                "xxxxx");
        SpireManager spires = new SpireManager(level, new DoorManager(level));
        spires.sowSpire(1, 1, 42, 8);
        spires.sowSpire(2, 2, 42, 8);
        spires.sowSpire(3, 1, 99, 8); // a different owner
        assertEquals(3, spires.liveSpireCount());

        spires.shatterSpiresOf(42);
        assertFalse(spires.isSpireAt(1, 1));
        assertFalse(spires.isSpireAt(2, 2));
        assertTrue(spires.isSpireAt(3, 1), "another owner's spire is untouched by the withering");
        assertEquals(1, spires.liveSpireCount());

        spires.clear();
        assertEquals(0, spires.liveSpireCount());
        assertEquals('.', level.getCell(3, 1), "clear() restores every remaining stamped tile");
    }
}
