package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.tileset.LevelPalettes;
import ge.tbegvadze.toon3d.util.LevelGenConstants;

import java.util.Collections;

/**
 * Generates the one-time weapon-selection staging room shown at the start of every run.
 *
 * Layout (Y-up, 80×45 tile grid):
 *   - Centered 15×15 outer shell of 'x' walls surrounding a 13×13 lit-floor interior.
 *   - '>' portal one tile north of the room centre (player's visual focal point).
 *   - Three weapon-offer tiles in a row at the room centre (one tile south of the portal).
 *   - 'p' player spawn at the south-centre of the interior, facing north toward the offers.
 *   - Four optional 'P' decorative columns framing the portal area.
 *
 * After generate() returns, call getWeaponTileColumn(index) / getWeaponTileRow(index)
 * to retrieve the tile coordinates where World should spawn the three GroundItems.
 */
public class StartGameLevelGenerator implements ILevelGenerator {

    private static final int OFFER_COUNT        = LevelGenConstants.START_ROOM_WEAPON_OFFER_COUNT;
    private static final int MELEE_OFFER_COUNT  = LevelGenConstants.START_ROOM_MELEE_OFFER_COUNT;
    private static final int MELEE_ROW_OFFSET   = LevelGenConstants.START_ROOM_MELEE_ROW_OFFSET;
    private static final int INTERIOR_SIZE      = LevelGenConstants.START_ROOM_INTERIOR_SIZE;
    private static final int COLUMN_OFFSET      = LevelGenConstants.START_ROOM_COLUMN_OFFSET;

    private final int[] weaponTileColumns = new int[OFFER_COUNT];
    private final int[] weaponTileRows    = new int[OFFER_COUNT];
    private final int[] meleeTileColumns  = new int[MELEE_OFFER_COUNT];
    private final int[] meleeTileRows     = new int[MELEE_OFFER_COUNT];

    // The run seed, used only to pick this staging room's BASE WALL so every generator — this one
    // included, without exception — gets per-run base-wall variety. The layout is otherwise fixed.
    private final long seed;

    /** Uses the run seed to vary the staging room's base wall. */
    public StartGameLevelGenerator(long seed) {
        this.seed = seed;
    }

    @Override
    public Level generate() {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;   // 80
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;  // 45

        char[][] matrix = new char[gridHeight][gridWidth];

        // Fill every tile with solid wall.
        for (int tileRow = 0; tileRow < gridHeight; tileRow++) {
            for (int tileColumn = 0; tileColumn < gridWidth; tileColumn++) {
                matrix[tileRow][tileColumn] = 'x';
            }
        }

        // Room outer shell is (INTERIOR_SIZE + 2) wide/tall to include the 1-tile wall ring.
        int outerSize  = INTERIOR_SIZE + 2;
        int roomLeft   = (gridWidth  - outerSize) / 2;  // 32
        int roomBottom = (gridHeight - outerSize) / 2;  // 15

        // Carve the interior with bright lit-floor tiles.
        for (int tileRow = roomBottom + 1; tileRow <= roomBottom + INTERIOR_SIZE; tileRow++) {
            for (int tileColumn = roomLeft + 1; tileColumn <= roomLeft + INTERIOR_SIZE; tileColumn++) {
                matrix[tileRow][tileColumn] = ' ';
            }
        }

        // Centre of the interior in tile coordinates (Y-up).
        int centerColumn = roomLeft   + 1 + INTERIOR_SIZE / 2;  // 39
        int centerRow    = roomBottom + 1 + INTERIOR_SIZE / 2;  // 22

        // Portal sits one tile north of centre so the weapon row lands exactly at centre height,
        // giving the player a clear north-facing view of offers in front of the portal.
        int portalColumn = centerColumn;
        int portalRow    = centerRow + 1;  // 23
        matrix[portalRow][portalColumn] = '>';

        // Ranged weapon-offer row at the room centre (one tile south of the portal).
        // The three offers are arranged symmetrically: left, centre, right.
        int weaponRow = centerRow;  // 22
        int halfOffers = OFFER_COUNT / 2;
        for (int offerIndex = 0; offerIndex < OFFER_COUNT; offerIndex++) {
            weaponTileColumns[offerIndex] = centerColumn - halfOffers + offerIndex;
            weaponTileRows[offerIndex]    = weaponRow;
        }

        // Melee weapon-offer row, MELEE_ROW_OFFSET tiles south of the ranged row.
        // Same symmetric spread so the two rows mirror each other visually.
        int meleeRow = weaponRow - MELEE_ROW_OFFSET;  // 20
        int halfMeleeOffers = MELEE_OFFER_COUNT / 2;
        for (int offerIndex = 0; offerIndex < MELEE_OFFER_COUNT; offerIndex++) {
            meleeTileColumns[offerIndex] = centerColumn - halfMeleeOffers + offerIndex;
            meleeTileRows[offerIndex]    = meleeRow;
        }

        // Player spawn at south-centre of the interior, one tile from the south wall.
        int startColumn = centerColumn;
        int startRow    = roomBottom + 2;  // 17
        matrix[startRow][startColumn] = 'p';

        // Optional decorative 'P' columns framing the portal area.
        if (LevelGenConstants.START_ROOM_USE_COLUMNS) {
            placeColumnIfInterior(matrix, centerColumn - COLUMN_OFFSET, centerRow + COLUMN_OFFSET,
                                  roomLeft, roomBottom, outerSize);
            placeColumnIfInterior(matrix, centerColumn + COLUMN_OFFSET, centerRow + COLUMN_OFFSET,
                                  roomLeft, roomBottom, outerSize);
            placeColumnIfInterior(matrix, centerColumn - COLUMN_OFFSET, centerRow - COLUMN_OFFSET,
                                  roomLeft, roomBottom, outerSize);
            placeColumnIfInterior(matrix, centerColumn + COLUMN_OFFSET, centerRow - COLUMN_OFFSET,
                                  roomLeft, roomBottom, outerSize);
        }

        return new Level(matrix, Collections.emptyList(), Collections.emptyList(),
                         LevelPalettes.generatedWithBaseWall(seed));
    }

    /** Returns the tile column for the weapon offer at the given index (0 to OFFER_COUNT-1). */
    public int getWeaponTileColumn(int index) {
        if (index < 0 || index >= OFFER_COUNT) throw new IndexOutOfBoundsException("offer index " + index);
        return weaponTileColumns[index];
    }

    /** Returns the tile row for the weapon offer at the given index (0 to OFFER_COUNT-1). */
    public int getWeaponTileRow(int index) {
        if (index < 0 || index >= OFFER_COUNT) throw new IndexOutOfBoundsException("offer index " + index);
        return weaponTileRows[index];
    }

    /** Returns the tile column for the melee offer at the given index (0 to MELEE_OFFER_COUNT-1). */
    public int getMeleeTileColumn(int index) {
        if (index < 0 || index >= MELEE_OFFER_COUNT) throw new IndexOutOfBoundsException("melee offer index " + index);
        return meleeTileColumns[index];
    }

    /** Returns the tile row for the melee offer at the given index (0 to MELEE_OFFER_COUNT-1). */
    public int getMeleeTileRow(int index) {
        if (index < 0 || index >= MELEE_OFFER_COUNT) throw new IndexOutOfBoundsException("melee offer index " + index);
        return meleeTileRows[index];
    }

    private static void placeColumnIfInterior(char[][] matrix,
                                              int tileColumn, int tileRow,
                                              int roomLeft, int roomBottom, int outerSize) {
        boolean insideColumn = tileColumn > roomLeft && tileColumn < roomLeft + outerSize - 1;
        boolean insideRow    = tileRow    > roomBottom && tileRow    < roomBottom + outerSize - 1;
        if (insideColumn && insideRow) {
            matrix[tileRow][tileColumn] = 'P';
        }
    }
}
