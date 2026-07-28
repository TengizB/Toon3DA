package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.tileset.LevelPalettes;
import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.LevelGenConstants;
import ge.tbegvadze.toon3d.util.RenderConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Linear corridor dungeon generator — a single 3-tile-wide spine corridor runs most
 * of the grid with side rooms branching off both walls, like a vertebrate skeleton.
 * The spatial vocabulary is immediately distinct from {@link LevelGenerator}: instead of
 * scattered boxes, the player navigates one long artery and explores the ribs.
 *
 * Phase 1 — Spine:          3-tile-wide central corridor (horizontal 70 % or vertical 30 %).
 *                            Entrance room near the head. After the primary run, a single
 *                            perpendicular BEND is carved from its tail toward whichever side of
 *                            the grid has more open space (extendHorizontalSpineWithVerticalBend /
 *                            extendVerticalSpineWithHorizontalBend), carrying its own side rooms
 *                            and landmark — the 80x45 grid is far shorter tall than wide, so a
 *                            vertical-only spine would place roughly half as many rooms as a
 *                            horizontal one; the bend brings both orientations to comparable
 *                            room-placement budgets (see LEVEL_GEN_SPINE_BEND_* in LevelGenConstants).
 * Phase 2 — Side Rooms:     rectangular rooms placed at regular intervals along both sides, each
 *                            connected to the spine via a single-tile doorway. Each room
 *                            independently rolls a small chance to be significantly larger than
 *                            standard (LEVEL_GEN_LARGE_MODIFIER_CHANCE, Room.isLarge — shared with
 *                            LevelGenerator, not a room type of its own). Room type is then picked
 *                            via the same seeded weighted roll over RoomBlueprintRegistry that
 *                            LevelGenerator uses (assignRoomTypes), followed by a backstop that
 *                            guarantees at least LEVEL_GEN_MIN_SPECIAL_ROOMS. STORAGE_BAY/REACTOR
 *                            remain this generator's own local, unchanged room types.
 * Phase 3 — Decoration:     floor lighting, context-aware wall variety, spine columns,
 *                            per-room-type props, pickups, and weapon spawns.
 * Phase 4 — Enemies:        spawn points in all non-entrance rooms.
 * Phase 5 — Connectivity:   BFS audit; emergency corridors for any isolated room.
 * Phase 6 — Stairs:         exactly one exit in the landmark or furthest room.
 *
 * Grid convention: (0,0) = bottom-left tile, Y-up. No LibGDX imports — pure Java.
 */
public class LinearCorridorGenerator implements ILevelGenerator {

    private enum RoomType {
        ENTRANCE, STANDARD, SERVER_ROOM,
        MEDICAL_BAY, ARMORY, CRYO_CHAMBER,
        POWER_PLANT, COMMAND_CENTER, CONTAINMENT_BLOCK,
        RESEARCH_LAB, STORAGE_BAY, REACTOR
    }

    private static final class Room {
        final int leftColumn, bottomRow, rightColumn, topRow;
        RoomType type;
        // The registered blueprint selected for this room (RoomBlueprintRegistry). Set by
        // assignRoomTypes()/ensureMinimumSpecialRooms(); drives special-room counting (by id, not the
        // STANDARD-defaulting type tag) and the delegated-build bridge.
        RoomBlueprint blueprint;
        // True when this room's blueprint is a SPECIAL room this generator has NO native decoration for
        // (STELLAR_OBSERVATORY, GORE_NEST, ATMOSPHERIC_PLANT, SALVAGE_BAY, SUPPLY_CACHE): its architecture
        // is stamped canonically via the LevelGenerator build bridge (buildDelegatedRooms), so the native
        // per-type floor/prop/legacy passes MUST skip it.
        boolean delegated;
        // Size modifier, independent of type: rolled at placement time (mirrors LevelGenerator.Room).
        // A large room keeps whatever type-driven styling it would have received anyway — only its
        // footprint is bigger. Never true for the entrance room.
        boolean isLarge;

        Room(int leftColumn, int bottomRow, int rightColumn, int topRow) {
            this.leftColumn  = leftColumn;
            this.bottomRow   = bottomRow;
            this.rightColumn = rightColumn;
            this.topRow      = topRow;
            this.type        = RoomType.STANDARD;
        }

        int centerColumn()   { return (leftColumn + rightColumn) / 2; }
        int centerRow()      { return (bottomRow  + topRow)      / 2; }
        int interiorWidth()  { return rightColumn - leftColumn - 1; }
        int interiorHeight() { return topRow      - bottomRow  - 1; }

        boolean overlaps(Room other) {
            int margin = LevelGenConstants.LEVEL_GEN_ROOM_MARGIN;
            return leftColumn  < other.rightColumn  + margin
                && rightColumn > other.leftColumn   - margin
                && bottomRow   < other.topRow       + margin
                && topRow      > other.bottomRow    - margin;
        }
    }

    private final Random                 random;
    // Raw floor seed, kept for deterministic per-level BASE-WALL selection (independent of `random`, so it
    // never perturbs the generated grid). See LevelPalettes.generatedWithBaseWall.
    private final long                   seed;
    private final LevelGenConfig         config;
    private       List<int[]>            spineCenterTiles;
    private       List<WeaponSpawnPoint> weaponSpawnPoints;

    // Dungeon floor this generator is building for (1-based); drives the encounter Threat-Point
    // budget (balance idea 4, Pillar 1). Defaults to 1; set via generate(int dungeonDepth).
    private int dungeonDepth = 1;

    public LinearCorridorGenerator(long seed) {
        this(seed, new LevelGenConfig());
    }

    public LinearCorridorGenerator(long seed, LevelGenConfig config) {
        this.random = new Random(seed);
        this.seed   = seed;
        this.config = config;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Override
    public Level generate(int dungeonDepth) {
        this.dungeonDepth = Math.max(1, dungeonDepth);
        return generate();
    }

    @Override
    public Level generate() {
        char[][] grid = new char[LevelGenConstants.LEVEL_GEN_GRID_HEIGHT][LevelGenConstants.LEVEL_GEN_GRID_WIDTH];
        fillAll(grid, 'x');
        spineCenterTiles  = new ArrayList<>();
        weaponSpawnPoints = new ArrayList<>();

        boolean horizontal = random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_HORIZONTAL_CHANCE;
        List<Room> rooms   = horizontal ? buildHorizontalSpine(grid) : buildVerticalSpine(grid);

        if (rooms.size() < 2) return buildFallbackLevel();

        assignRoomTypes(rooms);

        // Phase 3 — Decoration
        assignFloorLighting(grid, rooms);
        assignWallVariety(grid);
        // Delegated specials (STELLAR_OBSERVATORY, GORE_NEST, ATMOSPHERIC_PLANT, SALVAGE_BAY, SUPPLY_CACHE)
        // are stamped canonically via the LevelGenerator build bridge AFTER wall variety — variety only
        // recolours 'x', so their themed/sealed walls survive it — and BEFORE doors/props so entrances and
        // the prop-skip guards see finished rooms. assignFloorLighting/placeProps already skip them.
        buildDelegatedRooms(grid, rooms);
        placeSpineColumns(grid);
        placePlayerSpawn(grid, rooms.get(0));

        // Doors placed before props so interior layouts can keep doorways and their swing
        // axes clear (the prop/column guards test for adjacent doors). All corridor and room
        // carving is already complete by this point.
        placeDoors(grid);

        placeProps(grid, rooms);
        placePickups(grid, rooms);
        placeWeaponSpawns(grid, rooms);

        // Phase 4 — Enemies: spend the floor's encounter Threat-Point budget (balance idea 4,
        // Pillar 1) across the side rooms instead of rolling each room independently.
        List<EnemySpawnPoint> spawnPoints = new ArrayList<>();
        placeBudgetedEncounter(grid, rooms, spawnPoints);

        // Atmospheric post-passes
        placeRustWallsNearUnlit(grid);
        placeGoreWallsNearCorpses(grid);

        // Phase 5 — Connectivity audit
        verifyAndRepairConnectivity(grid, rooms);

        // Phase 6 — Stairs
        stampStairsDown(grid, rooms);

        return new Level(grid, spawnPoints, weaponSpawnPoints,
                         LevelPalettes.generatedWithBaseWall(seed));
    }

    // -------------------------------------------------------------------------
    // Phase 1 — Spine generation
    // -------------------------------------------------------------------------

    private List<Room> buildHorizontalSpine(char[][] grid) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;

        // Spine center row: 40–60 % of grid height
        int spineRow = randomBetween((int)(gridHeight * 0.40f), (int)(gridHeight * 0.60f));

        // Spine length: 75–90 % of grid width, centred horizontally
        int spineLength      = (int)(gridWidth * randomFloat(
            LevelGenConstants.LEVEL_GEN_SPINE_LENGTH_MIN_FRAC,
            LevelGenConstants.LEVEL_GEN_SPINE_LENGTH_MAX_FRAC));
        int spineStartColumn = Math.max(1, (gridWidth - spineLength) / 2);
        int spineEndColumn   = Math.min(gridWidth - 2, spineStartColumn + spineLength - 1);

        // Carve 3-tile-wide spine: centre row is ' ', rib rows are 'l'
        int spineHalfWidth = LevelGenConstants.LEVEL_GEN_SPINE_WIDTH / 2;
        for (int tileColumn = spineStartColumn; tileColumn <= spineEndColumn; tileColumn++) {
            for (int deltaRow = -spineHalfWidth; deltaRow <= spineHalfWidth; deltaRow++) {
                int tileRow = spineRow + deltaRow;
                if (!isInBounds(tileColumn, tileRow)) continue;
                if (deltaRow == 0) {
                    grid[tileRow][tileColumn] = ' ';
                    spineCenterTiles.add(new int[]{ tileColumn, tileRow });
                } else {
                    grid[tileRow][tileColumn] = 'l';
                }
            }
        }

        // Optional widened crossroads node so the long artery is not a monotonous straight line.
        if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_PLAZA_CHANCE) {
            int plazaColumn = (spineStartColumn + spineEndColumn) / 2 + randomBetween(-4, 4);
            carveSpinePlaza(grid, plazaColumn, spineRow);
        }

        List<Room> rooms = new ArrayList<>();
        placeHorizontalSpineRooms(grid, rooms, spineRow, spineStartColumn, spineEndColumn);
        extendHorizontalSpineWithVerticalBend(grid, rooms, spineEndColumn, spineRow);
        return rooms;
    }

    /**
     * Walks the spine placing side rooms. The entrance room is forced near the head
     * and a landmark room near the tail; all others are placed randomly.
     */
    private void placeHorizontalSpineRooms(char[][] grid, List<Room> rooms,
                                            int spineRow, int spineStartColumn, int spineEndColumn) {
        int spineHalfWidth = LevelGenConstants.LEVEL_GEN_SPINE_WIDTH / 2;

        // ENTRANCE: near the spine head (leftmost slot)
        Room entranceRoom = tryPlaceHorizontalSideRoom(
            grid, rooms, spineStartColumn + 1, spineRow, spineHalfWidth, random.nextBoolean());
        if (entranceRoom != null) {
            entranceRoom.type    = RoomType.ENTRANCE;
            entranceRoom.isLarge = false; // never the large modifier, regardless of its placement roll
        }

        // Middle rooms along the spine
        int slotColumn = spineStartColumn + randomBetween(
            LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX,
            LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX + 2);
        int landmarkCutoff = spineEndColumn - LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX;
        while (slotColumn <= landmarkCutoff) {
            if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_SIDE_ROOM_CHANCE) {
                tryPlaceHorizontalSideRoom(grid, rooms, slotColumn, spineRow, spineHalfWidth, true);
            }
            if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_SIDE_ROOM_CHANCE) {
                tryPlaceHorizontalSideRoom(grid, rooms, slotColumn, spineRow, spineHalfWidth, false);
            }
            slotColumn += randomBetween(
                LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MIN,
                LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX);
        }

        // LANDMARK: near the spine tail (rightmost slot)
        tryPlaceHorizontalSideRoom(
            grid, rooms, spineEndColumn - 1, spineRow, spineHalfWidth, random.nextBoolean());

        // Guarantee the first room in the list is ENTRANCE
        if (!rooms.isEmpty() && rooms.get(0).type != RoomType.ENTRANCE) {
            rooms.get(0).type    = RoomType.ENTRANCE;
            rooms.get(0).isLarge = false;
        }
    }

    /**
     * Extends the horizontal spine with a single perpendicular VERTICAL bend at its tail
     * (spineEndColumn), toward whichever side of the grid has more open space. Carries its own
     * side rooms and landmark via {@link #tryPlaceVerticalSideRoom}, the same helper the vertical
     * spine's own primary run uses, so the added stretch reads identically. See
     * LEVEL_GEN_SPINE_BEND_* for why both orientations get a bend (room-budget parity).
     */
    private void extendHorizontalSpineWithVerticalBend(char[][] grid, List<Room> rooms,
                                                        int spineEndColumn, int spineRow) {
        int spineHalfWidth = LevelGenConstants.LEVEL_GEN_SPINE_WIDTH / 2;
        int gridHeight      = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;

        int belowSpace = spineRow - 1;
        int aboveSpace = gridHeight - 2 - spineRow;
        boolean extendAbove = aboveSpace >= belowSpace;
        int availableSpace  = extendAbove ? aboveSpace : belowSpace;
        if (availableSpace < LevelGenConstants.LEVEL_GEN_SPINE_BEND_MIN_LENGTH) return;

        int bendLength = Math.max(LevelGenConstants.LEVEL_GEN_SPINE_BEND_MIN_LENGTH,
                (int) (availableSpace * randomFloat(LevelGenConstants.LEVEL_GEN_SPINE_BEND_LENGTH_MIN_FRAC,
                                                    LevelGenConstants.LEVEL_GEN_SPINE_BEND_LENGTH_MAX_FRAC)));
        int bendEndRow = extendAbove ? spineRow + bendLength : spineRow - bendLength;

        int bendColumn  = spineEndColumn;
        int startRow    = Math.min(spineRow, bendEndRow);
        int endRow      = Math.max(spineRow, bendEndRow);
        for (int tileRow = startRow; tileRow <= endRow; tileRow++) {
            for (int deltaColumn = -spineHalfWidth; deltaColumn <= spineHalfWidth; deltaColumn++) {
                int tileColumn = bendColumn + deltaColumn;
                if (!isInBounds(tileColumn, tileRow)) continue;
                if (deltaColumn == 0) {
                    grid[tileRow][tileColumn] = ' ';
                    spineCenterTiles.add(new int[]{ tileColumn, tileRow });
                } else {
                    grid[tileRow][tileColumn] = 'l';
                }
            }
        }

        // Side rooms along the bend, same slot-stepping pattern as the primary vertical spine.
        int slotRow = extendAbove
            ? spineRow + randomBetween(LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX, LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX + 2)
            : spineRow - randomBetween(LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX, LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX + 2);
        int landmarkCutoff = extendAbove
            ? bendEndRow - LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX
            : bendEndRow + LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX;
        while (extendAbove ? slotRow <= landmarkCutoff : slotRow >= landmarkCutoff) {
            if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_SIDE_ROOM_CHANCE) {
                tryPlaceVerticalSideRoom(grid, rooms, bendColumn, spineHalfWidth, slotRow, true);
            }
            if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_SIDE_ROOM_CHANCE) {
                tryPlaceVerticalSideRoom(grid, rooms, bendColumn, spineHalfWidth, slotRow, false);
            }
            slotRow += extendAbove
                ? randomBetween(LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MIN, LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX)
                : -randomBetween(LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MIN, LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX);
        }

        // Landmark near the bend's far end.
        int landmarkRow = extendAbove ? bendEndRow - 1 : bendEndRow + 1;
        tryPlaceVerticalSideRoom(grid, rooms, bendColumn, spineHalfWidth, landmarkRow, random.nextBoolean());
    }

    /**
     * Tries to place one side room on the north (northSide=true) or south side of the
     * horizontal spine at {@code slotColumn}. Carves the room interior and a single-tile
     * connection into the spine. Returns the Room on success, null on failure.
     */
    private Room tryPlaceHorizontalSideRoom(char[][] grid, List<Room> rooms,
                                             int slotColumn, int spineRow, int spineHalfWidth,
                                             boolean northSide) {
        // Independent size tiers: a small chance of the LARGE modifier (significantly oversized,
        // shared with LevelGenerator — see LEVEL_GEN_LARGE_MODIFIER_CHANCE), else the existing
        // "bigRoom" oversized-band roll, else the normal small/medium range.
        boolean rollLarge  = config.enableLargeRooms
                && random.nextFloat() < LevelGenConstants.LEVEL_GEN_LARGE_MODIFIER_CHANCE;
        boolean bigRoom    = !rollLarge && random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_BIG_ROOM_CHANCE;
        int interiorWidth  = rollLarge
            ? randomBetween(LevelGenConstants.LEVEL_GEN_LARGE_MIN_DIM, LevelGenConstants.LEVEL_GEN_LARGE_MODIFIER_MAX_WIDTH)
            : bigRoom
                ? randomBetween(9, LevelGenConstants.LEVEL_GEN_SPINE_ROOM_MAX_WIDTH)
                : randomBetween(LevelGenConstants.LEVEL_GEN_SPINE_ROOM_MIN_WIDTH, 8);
        int interiorHeight = rollLarge
            ? randomBetween(LevelGenConstants.LEVEL_GEN_LARGE_MIN_DIM, LevelGenConstants.LEVEL_GEN_LARGE_MODIFIER_MAX_HEIGHT)
            : bigRoom
                ? randomBetween(9, LevelGenConstants.LEVEL_GEN_SPINE_ROOM_MAX_HEIGHT)
                : randomBetween(LevelGenConstants.LEVEL_GEN_SPINE_ROOM_MIN_HEIGHT, 8);
        int totalWidth     = interiorWidth  + 2;
        int totalHeight    = interiorHeight + 2;

        int leftColumn, bottomRow, rightColumn, topRow;
        int connectionRow;

        if (northSide) {
            bottomRow     = spineRow + spineHalfWidth + 1; // adjacent to spine top rib
            topRow        = bottomRow + totalHeight - 1;
            connectionRow = spineRow + spineHalfWidth;     // spine top rib
        } else {
            topRow        = spineRow - spineHalfWidth - 1; // adjacent to spine bottom rib
            bottomRow     = topRow   - totalHeight + 1;
            connectionRow = spineRow - spineHalfWidth;     // spine bottom rib
        }

        // Centre the room on the slot column
        leftColumn  = slotColumn - totalWidth / 2;
        rightColumn = leftColumn + totalWidth - 1;

        // Clamp to grid bounds (keep 1-tile solid border)
        if (leftColumn  < 1) leftColumn  = 1;
        if (rightColumn >= LevelGenConstants.LEVEL_GEN_GRID_WIDTH  - 1)
            rightColumn  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH  - 2;
        if (bottomRow   < 1) bottomRow   = 1;
        if (topRow      >= LevelGenConstants.LEVEL_GEN_GRID_HEIGHT - 1)
            topRow       = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT - 2;

        if (rightColumn - leftColumn  < LevelGenConstants.LEVEL_GEN_ROOM_MIN_WIDTH  + 1) return null;
        if (topRow      - bottomRow   < LevelGenConstants.LEVEL_GEN_ROOM_MIN_HEIGHT + 1) return null;

        // Recalculate connection column after clamping to ensure it is inside the room walls
        int connectionColumn = Math.min(Math.max(slotColumn, leftColumn + 1), rightColumn - 1);

        Room candidate = new Room(leftColumn, bottomRow, rightColumn, topRow);
        candidate.isLarge = rollLarge;
        for (Room existing : rooms) {
            if (candidate.overlaps(existing)) return null;
        }

        // Carve room interior
        for (int tileRow = bottomRow + 1; tileRow < topRow; tileRow++) {
            for (int tileColumn = leftColumn + 1; tileColumn < rightColumn; tileColumn++) {
                grid[tileRow][tileColumn] = ' ';
            }
        }

        // Carve single-tile connection through the room wall into the spine rib
        if (northSide) {
            grid[bottomRow][connectionColumn]     = 'l'; // room bottom wall → corridor tile
            grid[connectionRow][connectionColumn] = 'l'; // spine top rib (reinforce)
        } else {
            grid[topRow][connectionColumn]        = 'l'; // room top wall → corridor tile
            grid[connectionRow][connectionColumn] = 'l'; // spine bottom rib
        }

        rooms.add(candidate);
        return candidate;
    }

    private List<Room> buildVerticalSpine(char[][] grid) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;

        // Spine center column: 40–60 % of grid width
        int spineColumn = randomBetween((int)(gridWidth * 0.40f), (int)(gridWidth * 0.60f));

        // Spine length: 75–90 % of grid height, centred vertically
        int spineLength   = (int)(gridHeight * randomFloat(
            LevelGenConstants.LEVEL_GEN_SPINE_LENGTH_MIN_FRAC,
            LevelGenConstants.LEVEL_GEN_SPINE_LENGTH_MAX_FRAC));
        int spineStartRow = Math.max(1, (gridHeight - spineLength) / 2);
        int spineEndRow   = Math.min(gridHeight - 2, spineStartRow + spineLength - 1);

        // Carve 3-tile-wide spine: centre column is ' ', rib columns are 'l'
        int spineHalfWidth = LevelGenConstants.LEVEL_GEN_SPINE_WIDTH / 2;
        for (int tileRow = spineStartRow; tileRow <= spineEndRow; tileRow++) {
            for (int deltaColumn = -spineHalfWidth; deltaColumn <= spineHalfWidth; deltaColumn++) {
                int tileColumn = spineColumn + deltaColumn;
                if (!isInBounds(tileColumn, tileRow)) continue;
                if (deltaColumn == 0) {
                    grid[tileRow][tileColumn] = ' ';
                    spineCenterTiles.add(new int[]{ tileColumn, tileRow });
                } else {
                    grid[tileRow][tileColumn] = 'l';
                }
            }
        }

        if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_PLAZA_CHANCE) {
            int plazaRow = (spineStartRow + spineEndRow) / 2 + randomBetween(-4, 4);
            carveSpinePlaza(grid, spineColumn, plazaRow);
        }

        List<Room> rooms = new ArrayList<>();
        placeVerticalSpineRooms(grid, rooms, spineColumn, spineStartRow, spineEndRow);
        extendVerticalSpineWithHorizontalBend(grid, rooms, spineColumn, spineEndRow);
        return rooms;
    }

    /**
     * Carves a square open plaza centred on a spine tile and frames it with four corner
     * columns. Reads as a crossroads / staging node that breaks up the straight artery.
     *
     * Lane-safe by construction: the centre tile and all four cardinal mid-edges stay open,
     * so the corridor is never sealed. Plaza tiles are deliberately NOT fed to the spine-column
     * pass — the plaza carries its own corner framing and extra columns in the widened throat
     * could pinch the narrow spine where it meets the plaza.
     */
    private void carveSpinePlaza(char[][] grid, int centerColumn, int centerRow) {
        int radius = LevelGenConstants.LEVEL_GEN_SPINE_PLAZA_RADIUS;
        for (int deltaRow = -radius; deltaRow <= radius; deltaRow++) {
            for (int deltaColumn = -radius; deltaColumn <= radius; deltaColumn++) {
                int tileColumn = centerColumn + deltaColumn;
                int tileRow    = centerRow    + deltaRow;
                if (tileColumn <= 0 || tileColumn >= LevelGenConstants.LEVEL_GEN_GRID_WIDTH  - 1) continue;
                if (tileRow    <= 0 || tileRow    >= LevelGenConstants.LEVEL_GEN_GRID_HEIGHT - 1) continue;
                char cell = grid[tileRow][tileColumn];
                if (Level.isWall(cell) || cell == 'l') {
                    grid[tileRow][tileColumn] = ' ';
                }
            }
        }
        // Frame the opening with four free-standing corner columns (centre cross stays clear).
        int inset = Math.max(1, radius - 1);
        tryPlaceColumnAt(grid, centerColumn - inset, centerRow - inset);
        tryPlaceColumnAt(grid, centerColumn + inset, centerRow - inset);
        tryPlaceColumnAt(grid, centerColumn - inset, centerRow + inset);
        tryPlaceColumnAt(grid, centerColumn + inset, centerRow + inset);
    }

    private void placeVerticalSpineRooms(char[][] grid, List<Room> rooms,
                                          int spineColumn, int spineStartRow, int spineEndRow) {
        int spineHalfWidth = LevelGenConstants.LEVEL_GEN_SPINE_WIDTH / 2;

        Room entranceRoom = tryPlaceVerticalSideRoom(
            grid, rooms, spineColumn, spineHalfWidth, spineStartRow + 1, random.nextBoolean());
        if (entranceRoom != null) {
            entranceRoom.type    = RoomType.ENTRANCE;
            entranceRoom.isLarge = false; // never the large modifier, regardless of its placement roll
        }

        int slotRow = spineStartRow + randomBetween(
            LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX,
            LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX + 2);
        int landmarkCutoff = spineEndRow - LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX;
        while (slotRow <= landmarkCutoff) {
            if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_SIDE_ROOM_CHANCE) {
                tryPlaceVerticalSideRoom(grid, rooms, spineColumn, spineHalfWidth, slotRow, true);
            }
            if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_SIDE_ROOM_CHANCE) {
                tryPlaceVerticalSideRoom(grid, rooms, spineColumn, spineHalfWidth, slotRow, false);
            }
            slotRow += randomBetween(
                LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MIN,
                LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX);
        }

        tryPlaceVerticalSideRoom(
            grid, rooms, spineColumn, spineHalfWidth, spineEndRow - 1, random.nextBoolean());

        if (!rooms.isEmpty() && rooms.get(0).type != RoomType.ENTRANCE) {
            rooms.get(0).type    = RoomType.ENTRANCE;
            rooms.get(0).isLarge = false;
        }
    }

    /**
     * Extends the vertical spine with a single perpendicular HORIZONTAL bend at its tail
     * (spineEndRow), toward whichever side of the grid has more open space. Carries its own side
     * rooms and landmark via {@link #tryPlaceHorizontalSideRoom}, the same helper the horizontal
     * spine's own primary run uses, so the added stretch reads identically. See
     * LEVEL_GEN_SPINE_BEND_* for why both orientations get a bend (room-budget parity) — the 80x45
     * grid is far shorter tall than wide, so this is the orientation that needs it most.
     */
    private void extendVerticalSpineWithHorizontalBend(char[][] grid, List<Room> rooms,
                                                        int spineColumn, int spineEndRow) {
        int spineHalfWidth = LevelGenConstants.LEVEL_GEN_SPINE_WIDTH / 2;
        int gridWidth       = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;

        int rightSpace = gridWidth - 2 - spineColumn;
        int leftSpace  = spineColumn - 1;
        boolean extendRight = rightSpace >= leftSpace;
        int availableSpace  = extendRight ? rightSpace : leftSpace;
        if (availableSpace < LevelGenConstants.LEVEL_GEN_SPINE_BEND_MIN_LENGTH) return;

        int bendLength = Math.max(LevelGenConstants.LEVEL_GEN_SPINE_BEND_MIN_LENGTH,
                (int) (availableSpace * randomFloat(LevelGenConstants.LEVEL_GEN_SPINE_BEND_LENGTH_MIN_FRAC,
                                                    LevelGenConstants.LEVEL_GEN_SPINE_BEND_LENGTH_MAX_FRAC)));
        int bendEndColumn = extendRight ? spineColumn + bendLength : spineColumn - bendLength;

        int bendRow     = spineEndRow;
        int startColumn = Math.min(spineColumn, bendEndColumn);
        int endColumn   = Math.max(spineColumn, bendEndColumn);
        for (int tileColumn = startColumn; tileColumn <= endColumn; tileColumn++) {
            for (int deltaRow = -spineHalfWidth; deltaRow <= spineHalfWidth; deltaRow++) {
                int tileRow = bendRow + deltaRow;
                if (!isInBounds(tileColumn, tileRow)) continue;
                if (deltaRow == 0) {
                    grid[tileRow][tileColumn] = ' ';
                    spineCenterTiles.add(new int[]{ tileColumn, tileRow });
                } else {
                    grid[tileRow][tileColumn] = 'l';
                }
            }
        }

        // Side rooms along the bend, same slot-stepping pattern as the primary horizontal spine.
        int slotColumn = extendRight
            ? spineColumn + randomBetween(LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX, LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX + 2)
            : spineColumn - randomBetween(LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX, LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX + 2);
        int landmarkCutoff = extendRight
            ? bendEndColumn - LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX
            : bendEndColumn + LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX;
        while (extendRight ? slotColumn <= landmarkCutoff : slotColumn >= landmarkCutoff) {
            if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_SIDE_ROOM_CHANCE) {
                tryPlaceHorizontalSideRoom(grid, rooms, slotColumn, bendRow, spineHalfWidth, true);
            }
            if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_SIDE_ROOM_CHANCE) {
                tryPlaceHorizontalSideRoom(grid, rooms, slotColumn, bendRow, spineHalfWidth, false);
            }
            slotColumn += extendRight
                ? randomBetween(LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MIN, LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX)
                : -randomBetween(LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MIN, LevelGenConstants.LEVEL_GEN_SPINE_SIDE_STEP_MAX);
        }

        // Landmark near the bend's far end.
        int landmarkColumn = extendRight ? bendEndColumn - 1 : bendEndColumn + 1;
        tryPlaceHorizontalSideRoom(grid, rooms, landmarkColumn, bendRow, spineHalfWidth, random.nextBoolean());
    }

    /**
     * Tries to place one side room on the east (eastSide=true) or west side of the
     * vertical spine at {@code slotRow}. Returns the Room on success, null on failure.
     */
    private Room tryPlaceVerticalSideRoom(char[][] grid, List<Room> rooms,
                                           int spineColumn, int spineHalfWidth,
                                           int slotRow, boolean eastSide) {
        // Independent size tiers — mirrors tryPlaceHorizontalSideRoom.
        boolean rollLarge  = config.enableLargeRooms
                && random.nextFloat() < LevelGenConstants.LEVEL_GEN_LARGE_MODIFIER_CHANCE;
        boolean bigRoom    = !rollLarge && random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_BIG_ROOM_CHANCE;
        int interiorWidth  = rollLarge
            ? randomBetween(LevelGenConstants.LEVEL_GEN_LARGE_MIN_DIM, LevelGenConstants.LEVEL_GEN_LARGE_MODIFIER_MAX_WIDTH)
            : bigRoom
                ? randomBetween(9, LevelGenConstants.LEVEL_GEN_SPINE_ROOM_MAX_WIDTH)
                : randomBetween(LevelGenConstants.LEVEL_GEN_SPINE_ROOM_MIN_WIDTH, 8);
        int interiorHeight = rollLarge
            ? randomBetween(LevelGenConstants.LEVEL_GEN_LARGE_MIN_DIM, LevelGenConstants.LEVEL_GEN_LARGE_MODIFIER_MAX_HEIGHT)
            : bigRoom
                ? randomBetween(9, LevelGenConstants.LEVEL_GEN_SPINE_ROOM_MAX_HEIGHT)
                : randomBetween(LevelGenConstants.LEVEL_GEN_SPINE_ROOM_MIN_HEIGHT, 8);
        int totalWidth     = interiorWidth  + 2;
        int totalHeight    = interiorHeight + 2;

        int leftColumn, bottomRow, rightColumn, topRow;
        int connectionColumn;

        if (eastSide) {
            leftColumn       = spineColumn + spineHalfWidth + 1;
            rightColumn      = leftColumn + totalWidth - 1;
            connectionColumn = spineColumn + spineHalfWidth;
        } else {
            rightColumn      = spineColumn - spineHalfWidth - 1;
            leftColumn       = rightColumn - totalWidth + 1;
            connectionColumn = spineColumn - spineHalfWidth;
        }

        // Centre room vertically on the slot row
        bottomRow = slotRow - totalHeight / 2;
        topRow    = bottomRow + totalHeight - 1;

        // Clamp to grid bounds
        if (leftColumn  < 1) leftColumn  = 1;
        if (rightColumn >= LevelGenConstants.LEVEL_GEN_GRID_WIDTH  - 1)
            rightColumn  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH  - 2;
        if (bottomRow   < 1) bottomRow   = 1;
        if (topRow      >= LevelGenConstants.LEVEL_GEN_GRID_HEIGHT - 1)
            topRow       = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT - 2;

        if (rightColumn - leftColumn  < LevelGenConstants.LEVEL_GEN_ROOM_MIN_WIDTH  + 1) return null;
        if (topRow      - bottomRow   < LevelGenConstants.LEVEL_GEN_ROOM_MIN_HEIGHT + 1) return null;

        int connectionRow = Math.min(Math.max(slotRow, bottomRow + 1), topRow - 1);

        Room candidate = new Room(leftColumn, bottomRow, rightColumn, topRow);
        candidate.isLarge = rollLarge;
        for (Room existing : rooms) {
            if (candidate.overlaps(existing)) return null;
        }

        for (int tileRow = bottomRow + 1; tileRow < topRow; tileRow++) {
            for (int tileColumn = leftColumn + 1; tileColumn < rightColumn; tileColumn++) {
                grid[tileRow][tileColumn] = ' ';
            }
        }

        if (eastSide) {
            grid[connectionRow][leftColumn]       = 'l'; // room left wall → corridor tile
            grid[connectionRow][connectionColumn] = 'l'; // spine east rib
        } else {
            grid[connectionRow][rightColumn]      = 'l'; // room right wall → corridor tile
            grid[connectionRow][connectionColumn] = 'l'; // spine west rib
        }

        rooms.add(candidate);
        return candidate;
    }

    // -------------------------------------------------------------------------
    // Room type assignment
    // -------------------------------------------------------------------------

    // Pool filters for selectBlueprint() — mirrors LevelGenerator's NOT_ENTRANCE/SPECIAL_ONLY.
    private static final Predicate<RoomBlueprint> NOT_ENTRANCE =
            blueprint -> !blueprint.id().equals(RoomBlueprints.ID_ENTRANCE);
    private static final Predicate<RoomBlueprint> SPECIAL_ONLY =
            blueprint -> !blueprint.id().equals(RoomBlueprints.ID_ENTRANCE)
                      && !blueprint.id().equals(RoomBlueprints.ID_STANDARD);

    /**
     * Assigns room types via the same seeded weighted roll {@link LevelGenerator} uses
     * (RoomBlueprintRegistry) — MEDICAL_BAY, ARMORY, COMMAND_CENTER, POWER_PLANT, CRYO_CHAMBER,
     * CONTAINMENT_BLOCK, SERVER_ROOM, RESEARCH_LAB, and any GENERIC blueprint (e.g. SALVAGE_BAY/
     * SUPPLY_CACHE) all compete for each room slot with one weighted roll, replacing the previous
     * hardcoded finder-pass/cumulative-band logic — the two generators now share one room catalog
     * and selection algorithm. A backstop then tops the level up to LEVEL_GEN_MIN_SPECIAL_ROOMS if
     * the roll came up short (see LevelGenerator.ensureMinimumSpecialRooms for the same contract).
     *
     * STORAGE_BAY and REACTOR remain THIS generator's own local, unchanged top-up pass on whatever
     * STANDARD rooms are left — they are not registered in the shared RoomBlueprintRegistry (only
     * this generator has decoration for them), so they stay outside the shared roll on purpose.
     */
    private void assignRoomTypes(List<Room> rooms) {
        RoomBlueprints.bootstrap(); // idempotent — safe outside World (tests build generators directly)
        RoomBlueprintRegistry registry = RoomBlueprints.rooms();
        Map<String, Integer> placedCounts = new HashMap<>();

        Room entrance = rooms.get(0);
        entrance.blueprint = registry.get(RoomBlueprints.ID_ENTRANCE);
        entrance.type      = RoomType.ENTRANCE;
        entrance.delegated = false;
        entrance.isLarge   = false; // never the large modifier, regardless of what it rolled at placement
        placedCounts.merge(RoomBlueprints.ID_ENTRANCE, 1, Integer::sum);

        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            Room room = rooms.get(roomIndex);
            RoomBlueprint chosen = selectBlueprint(registry, room, placedCounts, NOT_ENTRANCE);
            if (chosen == null) chosen = registry.get(RoomBlueprints.ID_STANDARD); // defensive
            assignBlueprint(room, chosen);
            placedCounts.merge(chosen.id(), 1, Integer::sum);
        }

        ensureMinimumSpecialRooms(registry, rooms, placedCounts);
        assignLegacyStorageAndReactorTypes(rooms);
    }

    /**
     * Seeded weighted pick of one blueprint for a candidate room, drawn from every registered
     * blueprint {@code poolFilter} admits. Mirrors {@code LevelGenerator.selectBlueprint} exactly
     * (same {@link GameMath#weightedChoiceIndex} roulette) so both generators pick the same way.
     * Returns {@code null} when nothing in the filtered pool is eligible.
     */
    private RoomBlueprint selectBlueprint(RoomBlueprintRegistry registry, Room room,
                                          Map<String, Integer> placedCounts,
                                          Predicate<RoomBlueprint> poolFilter) {
        List<RoomBlueprint> candidates = new ArrayList<>();
        List<Float>         weights    = new ArrayList<>();
        for (RoomBlueprint blueprint : registry.all()) {
            if (!poolFilter.test(blueprint)) continue;
            RoomContext context = new RoomContext(room.interiorWidth(), room.interiorHeight(),
                    dungeonDepth, placedCounts.getOrDefault(blueprint.id(), 0));
            if (!blueprint.eligible(context)) continue;
            float weight = blueprint.selectionWeight(context);
            if (weight <= 0f) continue;
            candidates.add(blueprint);
            weights.add(weight);
        }
        if (candidates.isEmpty()) return null;
        float[] weightArray = new float[weights.size()];
        for (int index = 0; index < weightArray.length; index++) {
            weightArray[index] = weights.get(index);
        }
        int chosenIndex = GameMath.weightedChoiceIndex(weightArray, random.nextFloat());
        return candidates.get(chosenIndex);
    }

    /**
     * Backstop guaranteeing at least LEVEL_GEN_MIN_SPECIAL_ROOMS carry a SPECIAL blueprint (any id
     * other than "entrance"/"standard"). Mirrors {@code LevelGenerator.ensureMinimumSpecialRooms}:
     * only ever ADDS specials by upgrading random STANDARD rooms whose size/depth fit an eligible
     * special blueprint; best-effort, never forces an ineligible fit.
     */
    private void ensureMinimumSpecialRooms(RoomBlueprintRegistry registry, List<Room> rooms,
                                           Map<String, Integer> placedCounts) {
        int specialCount = 0;
        List<Room> standardRooms = new ArrayList<>();
        // Count SPECIAL rooms by BLUEPRINT id, not the RoomType tag: a delegated special (SALVAGE_BAY,
        // STELLAR_OBSERVATORY, GORE_NEST, …) is tagged RoomType.STANDARD but is genuinely special, so it
        // must count toward the minimum and must NOT be re-offered as an upgradeable standard room.
        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            Room room = rooms.get(roomIndex);
            if (isSpecialBlueprint(room.blueprint)) {
                specialCount++;
            } else if (room.type == RoomType.STANDARD) {
                standardRooms.add(room);
            }
        }
        if (specialCount >= LevelGenConstants.LEVEL_GEN_MIN_SPECIAL_ROOMS) return;

        Collections.shuffle(standardRooms, random);
        for (Room candidate : standardRooms) {
            if (specialCount >= LevelGenConstants.LEVEL_GEN_MIN_SPECIAL_ROOMS) break;
            RoomBlueprint upgrade = selectBlueprint(registry, candidate, placedCounts, SPECIAL_ONLY);
            if (upgrade == null) continue; // nothing special fits this room's size/depth — try the next

            placedCounts.merge(RoomBlueprints.ID_STANDARD, -1, Integer::sum);
            assignBlueprint(candidate, upgrade);
            placedCounts.merge(upgrade.id(), 1, Integer::sum);
            specialCount++;
        }
    }

    /**
     * Records the selected blueprint on a room and derives its native {@link RoomType} tag and its
     * {@link Room#delegated} flag. A room is DELEGATED when its blueprint is a SPECIAL room (not
     * ENTRANCE/STANDARD) that maps to no native RoomType — i.e. one this generator has no bespoke
     * decoration for (SALVAGE_BAY, SUPPLY_CACHE, STELLAR_OBSERVATORY, GORE_NEST, ATMOSPHERIC_PLANT).
     * Those are stamped canonically through the LevelGenerator build bridge instead of the native passes.
     */
    private void assignBlueprint(Room room, RoomBlueprint blueprint) {
        room.blueprint = blueprint;
        room.type      = roomTypeForBlueprint(blueprint);
        room.delegated = isSpecialBlueprint(blueprint) && room.type == RoomType.STANDARD;
    }

    /** A blueprint is SPECIAL when it is neither the ENTRANCE nor the STANDARD filler blueprint. */
    private static boolean isSpecialBlueprint(RoomBlueprint blueprint) {
        return blueprint != null
                && !blueprint.id().equals(RoomBlueprints.ID_ENTRANCE)
                && !blueprint.id().equals(RoomBlueprints.ID_STANDARD);
    }

    /**
     * Stamps every DELEGATED room (a special blueprint this generator has no native decoration for) with
     * its CANONICAL architecture by driving {@link RoomBlueprint#build} through a {@link LevelGenerator}
     * used purely as a room-stamping engine. This is what lets LINEAR_CORRIDOR spawn STELLAR_OBSERVATORY,
     * GORE_NEST, ATMOSPHERIC_PLANT, SALVAGE_BAY and SUPPLY_CACHE looking IDENTICAL to how ROOMS_MST builds
     * them — one implementation, not a divergent copy (the size/look/feel stay unchanged). A single bridge
     * is created lazily and seeded deterministically from THIS generator's RNG, so a given floor seed still
     * yields a byte-identical level; its build() draws render under the level's legacy palette, which maps
     * every symbol these rooms use.
     */
    private void buildDelegatedRooms(char[][] grid, List<Room> rooms) {
        LevelGenerator buildBridge = null;
        for (Room room : rooms) {
            if (!room.delegated || room.blueprint == null) continue;
            if (buildBridge == null) {
                buildBridge = new LevelGenerator(random.nextLong());
                buildBridge.setDungeonDepthForBridge(dungeonDepth);
            }
            buildBridge.buildRoomForBridge(grid, room.leftColumn, room.bottomRow,
                    room.rightColumn, room.topRow, room.blueprint);
        }
    }

    /**
     * Maps a blueprint id to this generator's own {@link RoomType} tag with no switch: an id that
     * matches an enum constant name gets that tag; anything else (SALVAGE_BAY, SUPPLY_CACHE,
     * STELLAR_OBSERVATORY — registered blueprints this generator has no bespoke decoration for)
     * falls back to STANDARD, so it renders as an ordinary side room. Mirrors
     * {@code LevelGenerator.roomTypeForBlueprint}.
     */
    private static RoomType roomTypeForBlueprint(RoomBlueprint blueprint) {
        String enumName = blueprint.id().toUpperCase(java.util.Locale.ROOT);
        for (RoomType candidate : RoomType.values()) {
            if (candidate.name().equals(enumName)) {
                return candidate;
            }
        }
        return RoomType.STANDARD;
    }

    /**
     * STORAGE_BAY and REACTOR are exclusive to this generator (not registered in the shared
     * RoomBlueprintRegistry — LevelGenerator has no decoration for them). Unchanged cumulative-band
     * top-up on whatever STANDARD rooms remain after the shared registry roll + special-room backstop.
     */
    private void assignLegacyStorageAndReactorTypes(List<Room> rooms) {
        int storageBayCount = 0;
        int reactorCount    = 0;
        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            Room room = rooms.get(roomIndex);
            if (room.type != RoomType.STANDARD || room.delegated) continue; // never re-tag a delegated special

            if (storageBayCount < LevelGenConstants.LEVEL_GEN_SPINE_STORAGE_MAX
                    && room.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_SPINE_STORAGE_MIN_WIDTH
                    && room.interiorHeight() >= LevelGenConstants.LEVEL_GEN_SPINE_STORAGE_MIN_HEIGHT
                    && random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_STORAGE_CHANCE) {
                room.type = RoomType.STORAGE_BAY;
                storageBayCount++;
                continue;
            }
            if (reactorCount < LevelGenConstants.LEVEL_GEN_SPINE_REACTOR_MAX
                    && room.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_SPINE_REACTOR_MIN_WIDTH
                    && room.interiorHeight() >= LevelGenConstants.LEVEL_GEN_SPINE_REACTOR_MIN_HEIGHT
                    && random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_REACTOR_CHANCE) {
                room.type = RoomType.REACTOR;
                reactorCount++;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase 3 — Floor lighting (per room type)
    // -------------------------------------------------------------------------

    private void assignFloorLighting(char[][] grid, List<Room> rooms) {
        for (Room room : rooms) {
            if (room.type == RoomType.ENTRANCE) continue;
            if (room.delegated) continue; // floor lit canonically by the build bridge (buildDelegatedRooms)
            switch (room.type) {
                case SERVER_ROOM:        assignServerRoomFloor(grid, room);        break;
                case MEDICAL_BAY:        break; // fully lit: clinical brightness
                case ARMORY:             assignArmoryFloor(grid, room);            break;
                case CRYO_CHAMBER:       assignCryoChamberFloor(grid, room);       break;
                case POWER_PLANT:        assignPowerPlantFloor(grid, room);        break;
                case COMMAND_CENTER:     assignCommandCenterFloor(grid, room);     break;
                case CONTAINMENT_BLOCK:  assignContainmentBlockFloor(grid, room);  break;
                case RESEARCH_LAB:       assignResearchLabFloor(grid, room);       break;
                case STORAGE_BAY:        assignStorageBayFloor(grid, room);        break;
                case REACTOR:            assignReactorFloor(grid, room);           break;
                default:                 assignStandardRoomFloor(grid, room);      break;
            }
        }
    }

    private void assignServerRoomFloor(char[][] grid, Room room) {
        int flickerBudget = 2;
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                float roll = random.nextFloat();
                if (roll < LevelGenConstants.LEVEL_GEN_SERVER_FLICKER_CHANCE && flickerBudget > 0) {
                    grid[tileRow][tileColumn] = 'f';
                    flickerBudget--;
                } else if (roll < LevelGenConstants.LEVEL_GEN_ROOM_SERVER_UNLIT_THRESHOLD) {
                    grid[tileRow][tileColumn] = 'u';
                } else {
                    grid[tileRow][tileColumn] = 'l';
                }
            }
        }
    }

    private void assignArmoryFloor(char[][] grid, Room room) {
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                boolean isCorner = (tileColumn <= room.leftColumn  + 2 || tileColumn >= room.rightColumn - 2)
                                && (tileRow    <= room.bottomRow   + 2 || tileRow    >= room.topRow      - 2);
                if (isCorner && random.nextFloat() < LevelGenConstants.LEVEL_GEN_ROOM_ARMORY_CORNER_DARK_CHANCE) {
                    grid[tileRow][tileColumn] = 'u';
                } else if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_ROOM_ARMORY_DIM_CHANCE) {
                    grid[tileRow][tileColumn] = 'l';
                }
            }
        }
    }

    private void assignCryoChamberFloor(char[][] grid, Room room) {
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_ROOM_CRYO_UNLIT_CHANCE) {
                    grid[tileRow][tileColumn] = 'u';
                } else {
                    grid[tileRow][tileColumn] = 'l';
                }
            }
        }
    }

    private void assignPowerPlantFloor(char[][] grid, Room room) {
        int centerColumn  = room.centerColumn();
        int centerRow     = room.centerRow();
        int flickerBudget = 4;
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                boolean nearCenter = Math.abs(tileColumn - centerColumn) <= 2
                                  && Math.abs(tileRow    - centerRow)    <= 2;
                if (nearCenter && flickerBudget > 0
                        && random.nextFloat() < LevelGenConstants.LEVEL_GEN_ROOM_POWERPLANT_NEAR_FLICKER_CHANCE) {
                    grid[tileRow][tileColumn] = 'f';
                    flickerBudget--;
                } else {
                    grid[tileRow][tileColumn] = 'u';
                }
            }
        }
    }

    private void assignCommandCenterFloor(char[][] grid, Room room) {
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                boolean isEdgeTile = tileColumn == room.leftColumn  + 1
                                  || tileColumn == room.rightColumn - 1
                                  || tileRow    == room.bottomRow   + 1
                                  || tileRow    == room.topRow      - 1;
                if (isEdgeTile
                        && random.nextFloat() < LevelGenConstants.LEVEL_GEN_ROOM_COMMAND_EDGE_DIM_CHANCE) {
                    grid[tileRow][tileColumn] = 'l';
                }
            }
        }
    }

    private void assignContainmentBlockFloor(char[][] grid, Room room) {
        int flickerBudget = 3;
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_ROOM_CONTAINMENT_FLICKER_CHANCE
                        && flickerBudget > 0) {
                    grid[tileRow][tileColumn] = 'f';
                    flickerBudget--;
                } else {
                    grid[tileRow][tileColumn] = 'u';
                }
            }
        }
    }

    private void assignStandardRoomFloor(char[][] grid, Room room) {
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                float roll = random.nextFloat();
                if (roll < LevelGenConstants.LEVEL_GEN_ROOM_STANDARD_FLICKER_CUMULATIVE) {
                    grid[tileRow][tileColumn] = 'f';
                } else if (roll < LevelGenConstants.LEVEL_GEN_ROOM_STANDARD_UNLIT_CUMULATIVE) {
                    grid[tileRow][tileColumn] = 'u';
                } else if (roll < LevelGenConstants.LEVEL_GEN_ROOM_STANDARD_NORMAL_CUMULATIVE) {
                    grid[tileRow][tileColumn] = 'l';
                }
                // else: leave as ' ' (lit)
            }
        }
    }

    /** RESEARCH_LAB — dim, even 'l' lab lighting; cold and clinical without going dark. */
    private void assignResearchLabFloor(char[][] grid, Room room) {
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                grid[tileRow][tileColumn] = random.nextFloat() < 0.20f ? 'u' : 'l';
            }
        }
    }

    /** STORAGE_BAY — utilitarian normal 'l' floor; the cargo aisles read by their crates. */
    private void assignStorageBayFloor(char[][] grid, Room room) {
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                grid[tileRow][tileColumn] = 'l';
            }
        }
    }

    /** REACTOR — dark 'u' floor with a few 'f' flickering tiles near the core (reactor hum). */
    private void assignReactorFloor(char[][] grid, Room room) {
        int centerColumn  = room.centerColumn();
        int centerRow     = room.centerRow();
        int flickerBudget = 4;
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (grid[tileRow][tileColumn] != ' ') continue;
                boolean nearCenter = Math.abs(tileColumn - centerColumn) <= 2
                                  && Math.abs(tileRow    - centerRow)    <= 2;
                if (nearCenter && flickerBudget > 0
                        && random.nextFloat() < LevelGenConstants.LEVEL_GEN_ROOM_POWERPLANT_NEAR_FLICKER_CHANCE) {
                    grid[tileRow][tileColumn] = 'f';
                    flickerBudget--;
                } else {
                    grid[tileRow][tileColumn] = 'u';
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase 3 — Wall variety
    // -------------------------------------------------------------------------

    private void assignWallVariety(char[][] grid) {
        for (int tileRow = 0; tileRow < LevelGenConstants.LEVEL_GEN_GRID_HEIGHT; tileRow++) {
            for (int tileColumn = 0; tileColumn < LevelGenConstants.LEVEL_GEN_GRID_WIDTH; tileColumn++) {
                if (grid[tileRow][tileColumn] != 'x') continue;
                if (!isAdjacentToOpen(grid, tileColumn, tileRow)) continue;
                grid[tileRow][tileColumn] = randomWallChar();
            }
        }
    }

    private boolean isAdjacentToOpen(char[][] grid, int tileColumn, int tileRow) {
        int[] deltaColumns = { 0,  0,  1, -1 };
        int[] deltaRows    = { 1, -1,  0,  0 };
        for (int direction = 0; direction < 4; direction++) {
            int neighborColumn = tileColumn + deltaColumns[direction];
            int neighborRow    = tileRow    + deltaRows[direction];
            if (!isInBounds(neighborColumn, neighborRow)) continue;
            char neighbor = grid[neighborRow][neighborColumn];
            if (neighbor == ' ' || neighbor == 'l' || neighbor == 'u' || neighbor == 'f'
                    || Level.isDoor(neighbor)) {
                return true;
            }
        }
        return false;
    }

    private char randomWallChar() {
        // Cumulative thresholds — conduit 'c' heavy for a military corridor feel.
        float roll = random.nextFloat();
        if (roll < LevelGenConstants.LEVEL_GEN_SPINE_WALL_CONDUIT_CHANCE)     return 'c';
        if (roll < LevelGenConstants.LEVEL_GEN_SPINE_WALL_VENT_CUMULATIVE)    return 'v';
        if (roll < LevelGenConstants.LEVEL_GEN_SPINE_WALL_DAMAGED_CUMULATIVE) return 'w';
        if (roll < LevelGenConstants.LEVEL_GEN_SPINE_WALL_HAZARD_CUMULATIVE)  return 'h';
        if (roll < LevelGenConstants.LEVEL_GEN_SPINE_WALL_TECH_CUMULATIVE)    return 't';
        return 'x';
    }

    // -------------------------------------------------------------------------
    // Phase 3 — Spine column props
    // -------------------------------------------------------------------------

    /**
     * Places 'P' column props along the spine centre at regular intervals using the
     * recorded spine-centre tile list. A column is placed only when it cannot seal the
     * 3-wide spine: at least one perpendicular axis must keep an open lane on both sides,
     * and the tile must be clear of doors and other columns.
     */
    private void placeSpineColumns(char[][] grid) {
        int spacing = LevelGenConstants.LEVEL_GEN_WIDE_HALLWAY_COLUMN_SPACING;
        for (int spineTileIndex = 0; spineTileIndex < spineCenterTiles.size(); spineTileIndex++) {
            if (spineTileIndex % (spacing + 1) != 0) continue;
            int[] tile     = spineCenterTiles.get(spineTileIndex);
            int tileColumn = tile[0];
            int tileRow    = tile[1];
            if (!isWalkableFloor(grid, tileColumn, tileRow)) continue;
            if (isAdjacentToDoor(grid, tileColumn, tileRow))  continue;
            if (isAdjacentToColumn(grid, tileColumn, tileRow)) continue;
            boolean northClear = isWalkableFloor(grid, tileColumn, tileRow + 1);
            boolean southClear = isWalkableFloor(grid, tileColumn, tileRow - 1);
            boolean eastClear  = isWalkableFloor(grid, tileColumn + 1, tileRow);
            boolean westClear  = isWalkableFloor(grid, tileColumn - 1, tileRow);
            if ((northClear && southClear) || (eastClear && westClear)) {
                grid[tileRow][tileColumn] = 'P';
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase 3 — Player spawn
    // -------------------------------------------------------------------------

    private void placePlayerSpawn(char[][] grid, Room entranceRoom) {
        int centerColumn = entranceRoom.centerColumn();
        int centerRow    = entranceRoom.centerRow();
        if (isWalkableFloor(grid, centerColumn, centerRow)) {
            grid[centerRow][centerColumn] = 'p';
            return;
        }
        for (int tileRow = entranceRoom.bottomRow + 1; tileRow < entranceRoom.topRow; tileRow++) {
            for (int tileColumn = entranceRoom.leftColumn + 1; tileColumn < entranceRoom.rightColumn; tileColumn++) {
                if (isWalkableFloor(grid, tileColumn, tileRow)) {
                    grid[tileRow][tileColumn] = 'p';
                    return;
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase 3 — Props (per room type)
    // -------------------------------------------------------------------------

    private void placeProps(char[][] grid, List<Room> rooms) {
        for (Room room : rooms) {
            if (room.type == RoomType.ENTRANCE) continue;
            if (room.delegated) continue; // props placed canonically by the build bridge (buildDelegatedRooms)
            switch (room.type) {
                case SERVER_ROOM:        placeServerRoomProps(grid, room);        break;
                case MEDICAL_BAY:        placeMedicalBayProps(grid, room);        break;
                case ARMORY:             placeArmoryProps(grid, room);            break;
                case CRYO_CHAMBER:       placeCryoChamberProps(grid, room);       break;
                case POWER_PLANT:        placePowerPlantProps(grid, room);        break;
                case COMMAND_CENTER:     placeCommandCenterProps(grid, room);     break;
                case CONTAINMENT_BLOCK:  placeContainmentBlockProps(grid, room);  break;
                case RESEARCH_LAB:       placeResearchLabProps(grid, room);       break;
                case STORAGE_BAY:        placeStorageBayProps(grid, room);        break;
                case REACTOR:            placeReactorProps(grid, room);           break;
                default:                 placeStandardRoomProps(grid, room);      break;
            }
        }
    }

    /**
     * STANDARD room props. Qualifying rooms first attempt a structural interior layout
     * (columns, cover, an altar) so even ordinary rooms read as designed spaces; rooms
     * that do not roll a layout fall back to the legacy sparse hazard/crate scatter.
     */
    private void placeStandardRoomProps(char[][] grid, Room room) {
        if (decorateRoomInterior(grid, room)) return;
        char[] propChars = { 'g', 'E', 'C', '#' };
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (!isWalkableFloor(grid, tileColumn, tileRow)) continue;
                if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
                if (isAdjacentToDoorAxis(grid, tileColumn, tileRow)) continue;
                if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_PROP_CHANCE) {
                    grid[tileRow][tileColumn] = propChars[random.nextInt(propChars.length)];
                }
            }
        }
    }

    private void placeServerRoomProps(char[][] grid, Room room) {
        // Rack rows of T/L props at 2-tile vertical spacing
        for (int tileRow = room.bottomRow + 2; tileRow < room.topRow - 1; tileRow += 2) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (!isWalkableFloor(grid, tileColumn, tileRow)) continue;
                char rackChar = (random.nextFloat() < LevelGenConstants.LEVEL_GEN_SERVER_LOCKER_RATIO)
                                ? 'L' : 'T';
                grid[tileRow][tileColumn] = rackChar;
            }
        }
        // Terminal walls along the top perimeter
        for (int tileColumn = room.leftColumn; tileColumn <= room.rightColumn; tileColumn++) {
            if (Level.isWall(grid[room.topRow][tileColumn])
                    && random.nextFloat() < LevelGenConstants.LEVEL_GEN_SERVER_WALL_TERMINAL_CHANCE) {
                grid[room.topRow][tileColumn] = 't';
            }
        }
    }

    private void placeMedicalBayProps(char[][] grid, Room room) {
        // Medical 'M' walls on the perimeter
        for (int tileColumn = room.leftColumn; tileColumn <= room.rightColumn; tileColumn++) {
            for (int wallRow : new int[]{ room.bottomRow, room.topRow }) {
                if (Level.isWall(grid[wallRow][tileColumn])
                        && random.nextFloat() < LevelGenConstants.LEVEL_GEN_MEDICAL_WALL_CHANCE) {
                    grid[wallRow][tileColumn] = 'M';
                }
            }
        }
        for (int tileRow = room.bottomRow; tileRow <= room.topRow; tileRow++) {
            for (int wallColumn : new int[]{ room.leftColumn, room.rightColumn }) {
                if (Level.isWall(grid[tileRow][wallColumn])
                        && random.nextFloat() < LevelGenConstants.LEVEL_GEN_MEDICAL_WALL_CHANCE) {
                    grid[tileRow][wallColumn] = 'M';
                }
            }
        }
        // Medical cabinets ('C') scattered inside
        for (int attempt = 0; attempt < 5; attempt++) {
            if (room.interiorWidth() <= 0 || room.interiorHeight() <= 0) break;
            int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
            int tileRow    = room.bottomRow  + 1 + random.nextInt(room.interiorHeight());
            if (isWalkableFloor(grid, tileColumn, tileRow)
                    && random.nextFloat() < LevelGenConstants.LEVEL_GEN_MEDICAL_PROP_CHANCE) {
                grid[tileRow][tileColumn] = 'C';
            }
        }
    }

    private void placeArmoryProps(char[][] grid, Room room) {
        // Blast-scarred 'X' walls on the perimeter
        for (int tileColumn = room.leftColumn; tileColumn <= room.rightColumn; tileColumn++) {
            for (int wallRow : new int[]{ room.bottomRow, room.topRow }) {
                if (Level.isWall(grid[wallRow][tileColumn])
                        && random.nextFloat() < LevelGenConstants.LEVEL_GEN_ARMORY_BLAST_WALL_CHANCE) {
                    grid[wallRow][tileColumn] = 'X';
                }
            }
        }
        // Weapon racks '=' along the back (top) interior row
        for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
            int backRow = room.topRow - 1;
            if (isWalkableFloor(grid, tileColumn, backRow)) {
                grid[backRow][tileColumn] = '=';
            }
        }
        // Ammo crate '#' somewhere inside
        for (int attempt = 0; attempt < 10; attempt++) {
            if (room.interiorWidth() <= 0 || room.interiorHeight() <= 0) break;
            int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
            int tileRow    = room.bottomRow  + 1 + random.nextInt(room.interiorHeight());
            if (isWalkableFloor(grid, tileColumn, tileRow)) {
                grid[tileRow][tileColumn] = '#';
                break;
            }
        }
    }

    private void placeCryoChamberProps(char[][] grid, Room room) {
        // Cryo 'Z' walls on the top perimeter
        for (int tileColumn = room.leftColumn; tileColumn <= room.rightColumn; tileColumn++) {
            if (Level.isWall(grid[room.topRow][tileColumn])
                    && random.nextFloat() < LevelGenConstants.LEVEL_GEN_CRYO_WALL_CHANCE) {
                grid[room.topRow][tileColumn] = 'Z';
            }
        }
        // Bio-pod '&' rows at 2-tile spacing inside
        for (int tileRow = room.bottomRow + 2; tileRow < room.topRow - 1; tileRow += 2) {
            for (int tileColumn = room.leftColumn + 2; tileColumn < room.rightColumn - 1; tileColumn += 3) {
                if (isWalkableFloor(grid, tileColumn, tileRow)) {
                    grid[tileRow][tileColumn] = '&';
                }
            }
        }
    }

    private void placePowerPlantProps(char[][] grid, Room room) {
        // Radiation 'U' walls on the perimeter
        for (int tileColumn = room.leftColumn; tileColumn <= room.rightColumn; tileColumn++) {
            for (int wallRow : new int[]{ room.bottomRow, room.topRow }) {
                if (Level.isWall(grid[wallRow][tileColumn])
                        && random.nextFloat() < LevelGenConstants.LEVEL_GEN_POWERPLANT_RAD_WALL_CHANCE) {
                    grid[wallRow][tileColumn] = 'U';
                }
            }
        }
        // Generator '%' cluster near the centre
        int centerColumn     = room.centerColumn();
        int centerRow        = room.centerRow();
        int generatorCount   = randomBetween(
            LevelGenConstants.LEVEL_GEN_POWERPLANT_MIN_GENERATORS,
            LevelGenConstants.LEVEL_GEN_POWERPLANT_MAX_GENERATORS);
        int generatorsPlaced = 0;
        for (int attempt = 0; attempt < 40 && generatorsPlaced < generatorCount; attempt++) {
            int tileColumn = centerColumn + random.nextInt(3) - 1;
            int tileRow    = centerRow    + random.nextInt(3) - 1;
            if (isInBounds(tileColumn, tileRow) && isWalkableFloor(grid, tileColumn, tileRow)) {
                grid[tileRow][tileColumn] = '%';
                generatorsPlaced++;
            }
        }
    }

    private void placeCommandCenterProps(char[][] grid, Room room) {
        // Glass 'N' walls on the front (bottom) perimeter
        for (int tileColumn = room.leftColumn; tileColumn <= room.rightColumn; tileColumn++) {
            if (Level.isWall(grid[room.bottomRow][tileColumn])
                    && random.nextFloat() < LevelGenConstants.LEVEL_GEN_COMMAND_GLASS_WALL_CHANCE) {
                grid[room.bottomRow][tileColumn] = 'N';
            }
        }
        // Terminal 'T' row along the back
        int terminalsPlaced = 0;
        int terminalTarget  = randomBetween(
            LevelGenConstants.LEVEL_GEN_COMMAND_MIN_TERMINALS,
            LevelGenConstants.LEVEL_GEN_COMMAND_MAX_TERMINALS);
        int backRow = room.topRow - 1;
        for (int tileColumn = room.leftColumn + 1;
             tileColumn < room.rightColumn && terminalsPlaced < terminalTarget;
             tileColumn++) {
            if (isWalkableFloor(grid, tileColumn, backRow)) {
                grid[backRow][tileColumn] = 'T';
                terminalsPlaced++;
            }
        }
    }

    private void placeContainmentBlockProps(char[][] grid, Room room) {
        // Glass 'N' cell fronts alternating on the side walls
        for (int tileRow = room.bottomRow + 2; tileRow < room.topRow - 1; tileRow += 3) {
            for (int wallColumn : new int[]{ room.leftColumn, room.rightColumn }) {
                if (Level.isWall(grid[tileRow][wallColumn])
                        && random.nextFloat() < LevelGenConstants.LEVEL_GEN_CONTAINMENT_GLASS_CHANCE) {
                    grid[tileRow][wallColumn] = 'N';
                }
            }
        }
        // Quarantine 'Q' walls on top perimeter
        for (int tileColumn = room.leftColumn; tileColumn <= room.rightColumn; tileColumn++) {
            if (Level.isWall(grid[room.topRow][tileColumn])
                    && random.nextFloat() < LevelGenConstants.LEVEL_GEN_CONTAINMENT_BIO_CHANCE) {
                grid[room.topRow][tileColumn] = 'Q';
            }
        }
    }

    /**
     * RESEARCH_LAB — sci-fi set-piece: holo-data 'D' back wall, a row of specimen tanks 'I',
     * a central AI core 'J', a holo-workstation 'W' and special equipment '@', plus scattered
     * energy-scorch 'e' decals. Solids go down lane-safe so the lab never seals.
     */
    private void placeResearchLabProps(char[][] grid, Room room) {
        // Holo-data wall band along the back (top) perimeter.
        for (int tileColumn = room.leftColumn; tileColumn <= room.rightColumn; tileColumn++) {
            if (Level.isWall(grid[room.topRow][tileColumn])
                    && random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_RESEARCH_HOLO_WALL_CHANCE) {
                grid[room.topRow][tileColumn] = 'D';
            }
        }
        // Row of specimen tanks 'I' along the back interior row.
        int tankTarget = randomBetween(LevelGenConstants.LEVEL_GEN_SPINE_RESEARCH_MIN_TANKS,
                                       LevelGenConstants.LEVEL_GEN_SPINE_RESEARCH_MAX_TANKS);
        int tanksPlaced = 0;
        int backRow = room.topRow - 1;
        for (int tileColumn = room.leftColumn + 1;
             tileColumn < room.rightColumn && tanksPlaced < tankTarget; tileColumn += 2) {
            if (tryPlaceSolidProp(grid, tileColumn, backRow, 'I')) tanksPlaced++;
        }
        // AI core just off-centre, flanked by a holo-workstation and special equipment.
        // The exact room centre is left walkable so the connectivity audit never bulldozes here.
        int centerColumn = room.centerColumn();
        int centerRow    = room.centerRow();
        tryPlaceSolidProp(grid, centerColumn,     centerRow + 1, 'J');
        tryPlaceSolidProp(grid, centerColumn - 2, centerRow,     'W');
        tryPlaceSolidProp(grid, centerColumn + 2, centerRow,     '@');
        // Energy-scorch decals scattered on the floor.
        int scorchTarget = randomBetween(LevelGenConstants.LEVEL_GEN_SPINE_RESEARCH_SCORCH_MIN,
                                         LevelGenConstants.LEVEL_GEN_SPINE_RESEARCH_SCORCH_MAX);
        scatterDecals(grid, room, 'e', scorchTarget);
    }

    /**
     * STORAGE_BAY — cargo hold of stacked crate 'C' / locker 'L' aisles. A one-tile cross-aisle
     * is kept open through the centre so every lane stays reachable.
     */
    private void placeStorageBayProps(char[][] grid, Room room) {
        int crossRow = room.centerRow();
        for (int tileColumn = room.leftColumn + 2; tileColumn < room.rightColumn - 1; tileColumn += 2) {
            for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
                if (tileRow == crossRow) continue; // cross-aisle keeps the bay fully connected
                char crate = random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_STORAGE_LOCKER_RATIO
                             ? 'L' : 'C';
                tryPlaceSolidProp(grid, tileColumn, tileRow, crate);
            }
        }
    }

    /**
     * REACTOR — volatile hazard field: radiation 'U' walls, a generator '%' core cluster, and
     * scattered explosive 'E' / radioactive 'g' barrels. Barrels are placed lane-safe.
     */
    private void placeReactorProps(char[][] grid, Room room) {
        // Radiation walls on the perimeter.
        for (int tileColumn = room.leftColumn; tileColumn <= room.rightColumn; tileColumn++) {
            for (int wallRow : new int[]{ room.bottomRow, room.topRow }) {
                if (Level.isWall(grid[wallRow][tileColumn])
                        && random.nextFloat() < LevelGenConstants.LEVEL_GEN_SPINE_REACTOR_RAD_WALL_CHANCE) {
                    grid[wallRow][tileColumn] = 'U';
                }
            }
        }
        // Generator core cluster off-centre. The exact room centre is left walkable so the
        // connectivity audit never has to bulldoze a corridor through the core.
        int centerColumn = room.centerColumn();
        int centerRow    = room.centerRow();
        tryPlaceSolidProp(grid, centerColumn + 1, centerRow,     '%');
        tryPlaceSolidProp(grid, centerColumn + 2, centerRow,     '%');
        tryPlaceSolidProp(grid, centerColumn + 1, centerRow + 1, '%');
        // Scattered volatile barrels (centre tile kept clear).
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (tileColumn == centerColumn && tileRow == centerRow) continue;
                if (random.nextFloat() >= LevelGenConstants.LEVEL_GEN_SPINE_REACTOR_BARREL_CHANCE) continue;
                tryPlaceSolidProp(grid, tileColumn, tileRow, random.nextBoolean() ? 'E' : 'g');
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase 3 — Interior architecture (STANDARD structural layouts; a large-modified STANDARD
    // room, Room.isLarge, goes through this same pass on a bigger footprint)
    // -------------------------------------------------------------------------

    /**
     * Gives a qualifying STANDARD room an internal structure built from environment elements
     * so it reads as a designed space rather than a bare box. Returns true when a layout was
     * applied (caller then skips the legacy scatter). Every layout is lane-safe by
     * construction: it never seals the room centre, a doorway, the spawn, or the stairs.
     */
    private boolean decorateRoomInterior(char[][] grid, Room room) {
        if (room.interiorWidth()  < LevelGenConstants.LEVEL_GEN_INTERIOR_MIN_DIM) return false;
        if (room.interiorHeight() < LevelGenConstants.LEVEL_GEN_INTERIOR_MIN_DIM) return false;
        if (random.nextFloat() >= LevelGenConstants.LEVEL_GEN_INTERIOR_LAYOUT_CHANCE) return false;
        switch (random.nextInt(4)) {
            case 0:  layoutFourPillars(grid, room);  break;
            case 1:  layoutCentreAvenue(grid, room); break;
            case 2:  layoutCentralAltar(grid, room); break;
            default: layoutCrateCover(grid, room);   break;
        }
        return true;
    }

    /** Four free-standing columns at the interior quadrant centres (classic pillared hall). */
    private void layoutFourPillars(char[][] grid, Room room) {
        int quarterWidth  = room.interiorWidth()  / 4;
        int quarterHeight = room.interiorHeight() / 4;
        int[] columns = { room.leftColumn + 1 + quarterWidth, room.rightColumn - 1 - quarterWidth };
        int[] rows    = { room.bottomRow  + 1 + quarterHeight, room.topRow      - 1 - quarterHeight };
        for (int patternColumn : columns) {
            for (int patternRow : rows) {
                tryPlaceColumnAt(grid, patternColumn, patternRow);
            }
        }
    }

    /** A single row of columns down the room's long axis, splitting it into two naves. */
    private void layoutCentreAvenue(char[][] grid, Room room) {
        int spacing      = LevelGenConstants.LEVEL_GEN_INTERIOR_COLUMN_SPACING + 1;
        int centerColumn = room.centerColumn();
        int centerRow    = room.centerRow();
        if (room.interiorWidth() >= room.interiorHeight()) {
            for (int tileColumn = room.leftColumn + 2; tileColumn < room.rightColumn - 1; tileColumn += spacing) {
                if (tileColumn == centerColumn) continue; // keep the room centre clear
                tryPlaceColumnAt(grid, tileColumn, centerRow);
            }
        } else {
            for (int tileRow = room.bottomRow + 2; tileRow < room.topRow - 1; tileRow += spacing) {
                if (tileRow == centerRow) continue; // keep the room centre clear
                tryPlaceColumnAt(grid, centerColumn, tileRow);
            }
        }
    }

    /**
     * Four columns framing a central reward pedestal — a small shrine. The pickup sits on the
     * open centre tile; the columns occupy the diagonals so all four cardinal lanes stay clear.
     */
    private void layoutCentralAltar(char[][] grid, Room room) {
        int centerColumn = room.centerColumn();
        int centerRow    = room.centerRow();
        tryPlaceColumnAt(grid, centerColumn - 1, centerRow - 1);
        tryPlaceColumnAt(grid, centerColumn + 1, centerRow - 1);
        tryPlaceColumnAt(grid, centerColumn - 1, centerRow + 1);
        tryPlaceColumnAt(grid, centerColumn + 1, centerRow + 1);
        if (isWalkableFloor(grid, centerColumn, centerRow)
                && !isAdjacentToDoor(grid, centerColumn, centerRow)) {
            grid[centerRow][centerColumn] = random.nextBoolean() ? 'A' : 'H';
        }
    }

    /** A few small crate/locker cover clusters offset from the centre, for tactical cover. */
    private void layoutCrateCover(char[][] grid, Room room) {
        int centerColumn = room.centerColumn();
        int centerRow    = room.centerRow();
        for (int cluster = 0; cluster < LevelGenConstants.LEVEL_GEN_INTERIOR_CRATE_CLUSTERS; cluster++) {
            int anchorColumn = room.leftColumn + 2 + random.nextInt(Math.max(1, room.interiorWidth()  - 2));
            int anchorRow    = room.bottomRow  + 2 + random.nextInt(Math.max(1, room.interiorHeight() - 2));
            if (anchorColumn == centerColumn && anchorRow == centerRow) continue; // keep centre clear
            int crateCount = 1 + random.nextInt(LevelGenConstants.LEVEL_GEN_INTERIOR_CRATE_PER_CLUSTER);
            for (int offset = 0; offset < crateCount; offset++) {
                char crate = random.nextFloat() < 0.30f ? 'L' : 'C';
                tryPlaceSolidProp(grid, anchorColumn + offset, anchorRow, crate);
            }
        }
    }

    /** Scatters up to {@code count} walkable decals on random interior floor tiles. */
    private void scatterDecals(char[][] grid, Room room, char decalChar, int count) {
        int placed = 0;
        for (int attempt = 0; attempt < count * 6 && placed < count; attempt++) {
            if (room.interiorWidth() <= 0 || room.interiorHeight() <= 0) break;
            int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
            int tileRow    = room.bottomRow  + 1 + random.nextInt(room.interiorHeight());
            if (isWalkableFloor(grid, tileColumn, tileRow)) {
                grid[tileRow][tileColumn] = decalChar;
                placed++;
            }
        }
    }

    /**
     * Places a 'P' column at a tile only when it is open floor and doing so cannot seal a
     * passage: never on or beside another column, never on or beside a door or its swing axis.
     */
    private void tryPlaceColumnAt(char[][] grid, int tileColumn, int tileRow) {
        if (!isInBounds(tileColumn, tileRow)) return;
        if (!isWalkableFloor(grid, tileColumn, tileRow)) return;
        if (isAdjacentToColumn(grid, tileColumn, tileRow)) return;
        if (isAdjacentToDoor(grid, tileColumn, tileRow)) return;
        if (isAdjacentToDoorAxis(grid, tileColumn, tileRow)) return;
        grid[tileRow][tileColumn] = 'P';
    }

    /** Places a solid prop only on lane-safe open floor that is clear of any doorway. */
    private boolean tryPlaceSolidProp(char[][] grid, int tileColumn, int tileRow, char propChar) {
        if (!isInBounds(tileColumn, tileRow)) return false;
        if (!isWalkableFloor(grid, tileColumn, tileRow)) return false;
        if (isAdjacentToDoor(grid, tileColumn, tileRow)) return false;
        if (isAdjacentToDoorAxis(grid, tileColumn, tileRow)) return false;
        grid[tileRow][tileColumn] = propChar;
        return true;
    }

    private boolean isAdjacentToColumn(char[][] grid, int tileColumn, int tileRow) {
        int[] deltaColumns = { 0, 0, 1, -1 };
        int[] deltaRows    = { 1, -1, 0, 0 };
        for (int direction = 0; direction < 4; direction++) {
            int neighborColumn = tileColumn + deltaColumns[direction];
            int neighborRow    = tileRow    + deltaRows[direction];
            if (!isInBounds(neighborColumn, neighborRow)) continue;
            if (grid[neighborRow][neighborColumn] == 'P') return true;
        }
        return false;
    }

    /**
     * Returns true when placing a solid here would sit on the swing axis of an adjacent door
     * (the line a door opens along), which could block that door. Mirrors the same guard in
     * {@link LevelGenerator}.
     */
    private boolean isAdjacentToDoorAxis(char[][] grid, int tileColumn, int tileRow) {
        int[] deltaColumns = { 0, 0, 1, -1 };
        int[] deltaRows    = { 1, -1, 0, 0 };
        for (int direction = 0; direction < 4; direction++) {
            int neighborColumn = tileColumn + deltaColumns[direction];
            int neighborRow    = tileRow    + deltaRows[direction];
            if (!isInBounds(neighborColumn, neighborRow)) continue;
            if (!Level.isDoor(grid[neighborRow][neighborColumn])) continue;
            boolean doorHasWallNorth = isWallAt(grid, neighborColumn, neighborRow + 1);
            boolean doorHasWallSouth = isWallAt(grid, neighborColumn, neighborRow - 1);
            boolean doorHasWallEast  = isWallAt(grid, neighborColumn + 1, neighborRow);
            boolean doorHasWallWest  = isWallAt(grid, neighborColumn - 1, neighborRow);
            if (doorHasWallNorth && doorHasWallSouth && tileRow == neighborRow)    return true;
            if (doorHasWallEast  && doorHasWallWest  && tileColumn == neighborColumn) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Phase 3 — Pickups
    // -------------------------------------------------------------------------

    private void placePickups(char[][] grid, List<Room> rooms) {
        for (Room room : rooms) {
            if (room.type == RoomType.ENTRANCE) continue;

            float medkitChance;
            float armourChance;
            float ammoChance;

            switch (room.type) {
                case MEDICAL_BAY:
                    tryPlacePickup(grid, room, 'H');
                    tryPlacePickup(grid, room, 'H');
                    continue;
                case ARMORY:
                    if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_ARMORY_ARMOUR_CHANCE)
                        tryPlacePickup(grid, room, 'A');
                    if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_ARMORY_MEDKIT_CHANCE)
                        tryPlacePickup(grid, room, 'H');
                    tryPlacePickup(grid, room, randomAmmoChar());
                    if (random.nextBoolean()) tryPlacePickup(grid, room, randomAmmoChar());
                    continue;
                case SERVER_ROOM:
                    medkitChance = LevelGenConstants.LEVEL_GEN_SERVER_MEDKIT_CHANCE;
                    armourChance = LevelGenConstants.LEVEL_GEN_SERVER_ARMOUR_CHANCE;
                    ammoChance   = LevelGenConstants.LEVEL_GEN_HAZARD_ROOM_AMMO_CHANCE;
                    break;
                case COMMAND_CENTER:
                    medkitChance = LevelGenConstants.LEVEL_GEN_COMMAND_MEDKIT_CHANCE;
                    armourChance = LevelGenConstants.LEVEL_GEN_COMMAND_ARMOUR_CHANCE;
                    ammoChance   = LevelGenConstants.LEVEL_GEN_COMMAND_AMMO_CHANCE;
                    break;
                case STORAGE_BAY:
                    // Cargo hold — loot hub: guaranteed ammo, generous armour.
                    if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_COMMAND_ARMOUR_CHANCE)
                        tryPlacePickup(grid, room, 'A');
                    tryPlacePickup(grid, room, randomAmmoChar());
                    if (random.nextBoolean()) tryPlacePickup(grid, room, randomAmmoChar());
                    continue;
                case RESEARCH_LAB:
                    medkitChance = LevelGenConstants.LEVEL_GEN_COMMAND_MEDKIT_CHANCE;
                    armourChance = LevelGenConstants.LEVEL_GEN_HAZARD_ROOM_ARMOUR_CHANCE;
                    ammoChance   = LevelGenConstants.LEVEL_GEN_COMMAND_AMMO_CHANCE;
                    break;
                case POWER_PLANT:
                case REACTOR:
                case CRYO_CHAMBER:
                case CONTAINMENT_BLOCK:
                    medkitChance = LevelGenConstants.LEVEL_GEN_HAZARD_ROOM_MEDKIT_CHANCE;
                    armourChance = LevelGenConstants.LEVEL_GEN_HAZARD_ROOM_ARMOUR_CHANCE;
                    ammoChance   = LevelGenConstants.LEVEL_GEN_HAZARD_ROOM_AMMO_CHANCE;
                    break;
                default:
                    medkitChance = config.medkitChancePerRoom;
                    armourChance = config.armourChancePerRoom;
                    ammoChance   = LevelGenConstants.LEVEL_GEN_AMMO_CHANCE_PER_ROOM;
                    break;
            }

            // A large-modified room (Room.isLarge) floors its chances at the same boosted levels a
            // LARGE landmark room used to guarantee — size alone earns richer loot regardless of type
            // (mirrors LevelGenerator.placePickups; skipped for MEDICAL_BAY/ARMORY/STORAGE_BAY, which
            // roll their own bespoke pickups above and never reach here).
            if (room.isLarge) {
                medkitChance = Math.max(medkitChance, LevelGenConstants.LEVEL_GEN_LARGE_MEDKIT_CHANCE);
                armourChance = Math.max(armourChance, LevelGenConstants.LEVEL_GEN_LARGE_ARMOUR_CHANCE);
                ammoChance   = Math.max(ammoChance, LevelGenConstants.LEVEL_GEN_AMMO_CHANCE_PER_ROOM);
            }

            if (config.medkits    && random.nextFloat() < medkitChance) tryPlacePickup(grid, room, 'H');
            if (config.armourKits && random.nextFloat() < armourChance)  tryPlacePickup(grid, room, 'A');
            if (random.nextFloat() < ammoChance) tryPlacePickup(grid, room, randomAmmoChar());
        }
    }

    // -------------------------------------------------------------------------
    // Phase 3 — Weapon spawns
    // -------------------------------------------------------------------------

    private void placeWeaponSpawns(char[][] grid, List<Room> rooms) {
        for (Room room : rooms) {
            if (room.type == RoomType.ARMORY) {
                tryPlaceWeaponSpawn(grid, room);
                break;
            }
        }
        for (Room room : rooms) {
            if (room.type == RoomType.ENTRANCE || room.type == RoomType.ARMORY) continue;
            if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_RANDOM_ROOM_WEAPON_CHANCE) {
                tryPlaceWeaponSpawn(grid, room);
            }
        }
    }

    private boolean tryPlaceWeaponSpawn(char[][] grid, Room room) {
        for (int attempt = 0; attempt < 20; attempt++) {
            if (room.interiorWidth() <= 0 || room.interiorHeight() <= 0) return false;
            int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
            int tileRow    = room.bottomRow  + 1 + random.nextInt(room.interiorHeight());
            char cell = grid[tileRow][tileColumn];
            if (cell != ' ' && cell != 'l') continue;
            if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
            weaponSpawnPoints.add(new WeaponSpawnPoint(tileColumn, tileRow, randomWeaponItemType()));
            return true;
        }
        return false;
    }

    private ItemType randomWeaponItemType() {
        switch (random.nextInt(5)) {
            case 0:  return ItemType.WEAPON_SHOTGUN;
            case 1:  return ItemType.WEAPON_CHAINGUN;
            case 2:  return ItemType.WEAPON_ASSAULT_RIFLE;
            case 3:  return ItemType.WEAPON_PLASMA;
            default: return ItemType.WEAPON_ROCKET;
        }
    }

    private void tryPlacePickup(char[][] grid, Room room, char pickupChar) {
        for (int attempt = 0; attempt < 20; attempt++) {
            if (room.interiorWidth() <= 0 || room.interiorHeight() <= 0) return;
            int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
            int tileRow    = room.bottomRow  + 1 + random.nextInt(room.interiorHeight());
            if (isWalkableFloor(grid, tileColumn, tileRow)) {
                grid[tileRow][tileColumn] = pickupChar;
                return;
            }
        }
    }

    private char randomAmmoChar() {
        switch (random.nextInt(5)) {
            case 0:  return '6';
            case 1:  return '7';
            case 2:  return '8';
            case 3:  return '9';
            default: return '0';
        }
    }

    // -------------------------------------------------------------------------
    // Door placement
    // -------------------------------------------------------------------------

    private void placeDoors(char[][] grid) {
        for (int tileRow = 1; tileRow < LevelGenConstants.LEVEL_GEN_GRID_HEIGHT - 1; tileRow++) {
            for (int tileColumn = 1; tileColumn < LevelGenConstants.LEVEL_GEN_GRID_WIDTH - 1; tileColumn++) {
                if (grid[tileRow][tileColumn] != 'l') continue;
                if (!isAdjacentToRoomFloor(grid, tileColumn, tileRow)) continue;
                if (isAdjacentToDoor(grid, tileColumn, tileRow)) continue;
                if (!isDoorwayAligned(grid, tileColumn, tileRow)) continue;
                if (random.nextFloat() < LevelGenConstants.LEVEL_GEN_DOOR_CHANCE) {
                    grid[tileRow][tileColumn] = 'd';
                }
            }
        }
    }

    private boolean isDoorwayAligned(char[][] grid, int tileColumn, int tileRow) {
        boolean wallNorth = isWallAt(grid, tileColumn, tileRow + 1);
        boolean wallSouth = isWallAt(grid, tileColumn, tileRow - 1);
        boolean wallEast  = isWallAt(grid, tileColumn + 1, tileRow);
        boolean wallWest  = isWallAt(grid, tileColumn - 1, tileRow);
        return (wallNorth && wallSouth) || (wallEast && wallWest);
    }

    private boolean isWallAt(char[][] grid, int tileColumn, int tileRow) {
        if (!isInBounds(tileColumn, tileRow)) return true;
        return Level.isWall(grid[tileRow][tileColumn]);
    }

    private boolean isAdjacentToDoor(char[][] grid, int tileColumn, int tileRow) {
        return isDoorCell(grid, tileColumn,     tileRow + 1)
            || isDoorCell(grid, tileColumn,     tileRow - 1)
            || isDoorCell(grid, tileColumn + 1, tileRow)
            || isDoorCell(grid, tileColumn - 1, tileRow);
    }

    private boolean isDoorCell(char[][] grid, int tileColumn, int tileRow) {
        if (!isInBounds(tileColumn, tileRow)) return false;
        return Level.isDoor(grid[tileRow][tileColumn]);
    }

    private boolean isAdjacentToRoomFloor(char[][] grid, int tileColumn, int tileRow) {
        return isRoomFloor(grid, tileColumn,     tileRow + 1)
            || isRoomFloor(grid, tileColumn,     tileRow - 1)
            || isRoomFloor(grid, tileColumn + 1, tileRow)
            || isRoomFloor(grid, tileColumn - 1, tileRow);
    }

    private boolean isRoomFloor(char[][] grid, int tileColumn, int tileRow) {
        if (!isInBounds(tileColumn, tileRow)) return false;
        char cell = grid[tileRow][tileColumn];
        return cell == ' ' || cell == 'u' || cell == 'f';
    }

    // -------------------------------------------------------------------------
    // Phase 4 — Enemy placement
    // -------------------------------------------------------------------------

    /**
     * Spends the floor's encounter Threat-Point budget across the side rooms (balance idea 4,
     * Pillar 1), reusing the shared EncounterBudgetPlanner. The anchor goes in the deepest room
     * (highest spine index, exempt from the per-room cap); the rest fill under the per-room cap.
     * Mirrors LevelGenerator.placeBudgetedEncounter, adapted to the spine's room ordering.
     */
    private void placeBudgetedEncounter(char[][] grid, List<Room> rooms, List<EnemySpawnPoint> spawnPoints) {
        if (rooms.size() < 2) return;

        EncounterBudgetPlanner.Plan plan =
                new EncounterBudgetPlanner(dungeonDepth, random, config.enemyBudgetScale).plan();
        List<EnemyType> roster = plan.enemies();
        if (roster.isEmpty()) return;

        // Non-entrance rooms (1..n-1), deepest-first. Along a spine, later index = farther from
        // the entrance, so descending index is a natural depth order.
        Integer[] roomOrder = new Integer[rooms.size() - 1];
        for (int index = 0; index < roomOrder.length; index++) {
            roomOrder[index] = index + 1;
        }
        java.util.Arrays.sort(roomOrder, (left, right) -> Integer.compare(right, left));

        float[]  roomSpentThreat = new float[rooms.size()];
        boolean[][] usedTiles = new boolean[LevelGenConstants.LEVEL_GEN_GRID_HEIGHT]
                                            [LevelGenConstants.LEVEL_GEN_GRID_WIDTH];
        float perRoomCap = plan.perRoomThreatPointCap();

        // Anchor first — deepest room, exempt from the per-room cap.
        EnemyType anchor              = plan.anchor();
        int       anchorIndexInRoster = -1;
        if (anchor != null) {
            for (int orderIndex = 0; orderIndex < roomOrder.length; orderIndex++) {
                int roomIndex = roomOrder[orderIndex];
                if (tryPlaceEnemyInRoom(grid, rooms.get(roomIndex), anchor, usedTiles, spawnPoints)) {
                    roomSpentThreat[roomIndex] += plan.threatOf(anchor);
                    anchorIndexInRoster = 0; // anchor is always roster element 0
                    break;
                }
            }
        }

        // Distribute the remaining roster by load-balancing across the side rooms instead of packing
        // the deepest few up to the per-room cap (balance fix: the old deepest-first fill left most
        // rooms empty while stuffing 2-3 rooms into a low-level death trap). Each enemy drops into the
        // eligible room with the lowest depth-weighted load; along a spine the room index doubles as
        // the depth, so deeper rooms (higher index) still trend denser.
        // PACK COHERENCE IN SPACE: consecutive identical roster entries are one pack and land in ONE
        // room together, instead of being handed to the load balancer individually and split apart.
        boolean[] roomTilesExhausted = new boolean[rooms.size()];
        for (int rosterIndex = 0; rosterIndex < roster.size(); rosterIndex++) {
            if (rosterIndex == anchorIndexInRoster) continue;
            EnemyType enemy    = roster.get(rosterIndex);
            int       packSize = packRunLengthAt(roster, rosterIndex, anchorIndexInRoster);
            float     cost     = plan.threatOf(enemy);
            placePackLoadBalanced(grid, rooms, roomOrder, enemy, packSize, cost, perRoomCap,
                    roomSpentThreat, roomTilesExhausted, usedTiles, spawnPoints);
            rosterIndex += packSize - 1;
        }
    }

    /**
     * Length of the run of consecutive identical entries starting at {@code startIndex} — the shape in
     * which {@link EncounterBudgetPlanner} emits a pack — capped at {@code CHAFF_PACK_MAX} so a long
     * remainder-pass tail of one type cannot pile into a single room.
     */
    private int packRunLengthAt(List<EnemyType> roster, int startIndex, int anchorIndexInRoster) {
        EnemyType type   = roster.get(startIndex);
        int       length = 1;
        while (startIndex + length < roster.size()
                && roster.get(startIndex + length) == type
                && (startIndex + length) != anchorIndexInRoster
                && length < BalanceConfig.CHAFF_PACK_MAX) {
            length++;
        }
        return length;
    }

    /**
     * Places one enemy into the least-loaded eligible room, spreading the roster across the spine
     * (balance fix: no empty rooms, no over-stuffed death-trap room). "Load" is a room's spent Threat
     * Points divided by its depth weight (1 + room index, since later spine rooms are deeper), so
     * deeper rooms absorb proportionally more while enemies still fan out. First pass honours the
     * per-room cap; a second pass falls back to any room with a free tile so the budget is spent.
     */
    private void placePackLoadBalanced(char[][] grid, List<Room> rooms, Integer[] roomOrder,
                                       EnemyType enemy, int packSize, float cost, float perRoomCap,
                                       float[] roomSpentThreat, boolean[] roomTilesExhausted,
                                       boolean[][] usedTiles, List<EnemySpawnPoint> spawnPoints) {
        float packCost = cost * packSize;
        for (int phase = 0; phase < 2; phase++) {
            boolean capPhase = (phase == 0);
            while (true) {
                int   bestRoom  = -1;
                float bestLoad  = Float.MAX_VALUE;
                int   bestDepth = -1;
                for (Integer roomIndex : roomOrder) {
                    if (roomTilesExhausted[roomIndex]) continue;
                    // The room is charged for the whole pack, so the cap is tested against the total.
                    if (capPhase && roomSpentThreat[roomIndex] + packCost > perRoomCap) continue;
                    float weight = 1f + roomIndex;
                    float load   = roomSpentThreat[roomIndex] / weight;
                    if (load < bestLoad || (load == bestLoad && roomIndex > bestDepth)) {
                        bestLoad  = load;
                        bestDepth = roomIndex;
                        bestRoom  = roomIndex;
                    }
                }
                if (bestRoom < 0) break;
                int placed = tryPlacePackInRoom(grid, rooms.get(bestRoom), enemy, packSize,
                        usedTiles, spawnPoints);
                if (placed > 0) {
                    roomSpentThreat[bestRoom] += cost * placed;
                    if (placed == packSize) return;
                    // Room held part of the pack — carry the rest onward instead of dropping it.
                    packSize -= placed;
                    packCost  = cost * packSize;
                    continue;
                }
                roomTilesExhausted[bestRoom] = true;
            }
        }
    }

    /**
     * Places up to {@code packSize} members of one archetype in a single room, clustered within
     * {@link LevelGenConstants#LEVEL_GEN_PACK_CLUSTER_RADIUS} of the first member so the group fights
     * as a group. Returns how many were placed (0 only when the room has no eligible tile at all).
     */
    private int tryPlacePackInRoom(char[][] grid, Room room, EnemyType enemy, int packSize,
                                   boolean[][] usedTiles, List<EnemySpawnPoint> spawnPoints) {
        if (!tryPlaceEnemyInRoom(grid, room, enemy, usedTiles, spawnPoints)) return 0;

        EnemySpawnPoint leader = spawnPoints.get(spawnPoints.size() - 1);
        int placed = 1;
        int radius = LevelGenConstants.LEVEL_GEN_PACK_CLUSTER_RADIUS;
        for (int ring = 1; ring <= radius && placed < packSize; ring++) {
            for (int rowOffset = -ring; rowOffset <= ring && placed < packSize; rowOffset++) {
                for (int columnOffset = -ring; columnOffset <= ring && placed < packSize; columnOffset++) {
                    if (Math.max(Math.abs(rowOffset), Math.abs(columnOffset)) != ring) continue;
                    int tileColumn = leader.tileColumn + columnOffset;
                    int tileRow    = leader.tileRow    + rowOffset;
                    if (!isInsideRoomInterior(room, tileColumn, tileRow))       continue;
                    if (!isSpawnableTile(grid, usedTiles, tileColumn, tileRow)) continue;
                    claimSpawnTile(usedTiles, spawnPoints, enemy, tileColumn, tileRow);
                    placed++;
                }
            }
        }
        while (placed < packSize
                && tryPlaceEnemyInRoom(grid, room, enemy, usedTiles, spawnPoints)) {
            placed++;
        }
        return placed;
    }

    /** Records an enemy spawn on a free, eligible, non-door-adjacent tile in the room. */
    private boolean tryPlaceEnemyInRoom(char[][] grid, Room room, EnemyType enemy,
                                        boolean[][] usedTiles, List<EnemySpawnPoint> spawnPoints) {
        if (room.interiorWidth() <= 0 || room.interiorHeight() <= 0) return false;
        for (int attempt = 0; attempt < LevelGenConstants.LEVEL_GEN_ENEMY_SPAWN_PROBE_ATTEMPTS; attempt++) {
            int tileColumn = room.leftColumn + 1 + random.nextInt(room.interiorWidth());
            int tileRow    = room.bottomRow  + 1 + random.nextInt(room.interiorHeight());
            if (!isSpawnableTile(grid, usedTiles, tileColumn, tileRow)) continue;
            claimSpawnTile(usedTiles, spawnPoints, enemy, tileColumn, tileRow);
            return true;
        }
        // Random probing missing is not evidence the room is full — settle it deterministically before
        // retiring the room, so an unlucky probe run stops silently dropping planned enemies.
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (!isSpawnableTile(grid, usedTiles, tileColumn, tileRow)) continue;
                claimSpawnTile(usedTiles, spawnPoints, enemy, tileColumn, tileRow);
                return true;
            }
        }
        return false;
    }

    private boolean isInsideRoomInterior(Room room, int tileColumn, int tileRow) {
        return tileColumn > room.leftColumn && tileColumn < room.rightColumn
            && tileRow    > room.bottomRow  && tileRow    < room.topRow;
    }

    /** A tile an enemy may spawn on: eligible terrain, not beside a door, not already claimed. */
    private boolean isSpawnableTile(char[][] grid, boolean[][] usedTiles, int tileColumn, int tileRow) {
        // isEnemySpawnEligible bounds-checks first, so the usedTiles access below is safe.
        return isEnemySpawnEligible(grid, tileColumn, tileRow)
            && !isAdjacentToDoor(grid, tileColumn, tileRow)
            && !usedTiles[tileRow][tileColumn];
    }

    private void claimSpawnTile(boolean[][] usedTiles, List<EnemySpawnPoint> spawnPoints,
                                EnemyType enemy, int tileColumn, int tileRow) {
        usedTiles[tileRow][tileColumn] = true;
        spawnPoints.add(new EnemySpawnPoint(enemy.spawnChar(), tileColumn, tileRow));
    }

    // -------------------------------------------------------------------------
    // Atmospheric post-passes
    // -------------------------------------------------------------------------

    private void placeRustWallsNearUnlit(char[][] grid) {
        int[] deltaColumns = { 0,  0,  1, -1 };
        int[] deltaRows    = { 1, -1,  0,  0 };
        for (int tileRow = 0; tileRow < LevelGenConstants.LEVEL_GEN_GRID_HEIGHT; tileRow++) {
            for (int tileColumn = 0; tileColumn < LevelGenConstants.LEVEL_GEN_GRID_WIDTH; tileColumn++) {
                if (grid[tileRow][tileColumn] != 'x') continue;
                boolean nearUnlit = false;
                for (int direction = 0; direction < 4; direction++) {
                    int neighborColumn = tileColumn + deltaColumns[direction];
                    int neighborRow    = tileRow    + deltaRows[direction];
                    if (!isInBounds(neighborColumn, neighborRow)) continue;
                    if (grid[neighborRow][neighborColumn] == 'u') nearUnlit = true;
                }
                if (nearUnlit && random.nextFloat() < LevelGenConstants.LEVEL_GEN_RUST_WALL_CHANCE) {
                    grid[tileRow][tileColumn] = 'j';
                }
            }
        }
    }

    private void placeGoreWallsNearCorpses(char[][] grid) {
        int[] deltaColumns = { 0,  0,  1, -1 };
        int[] deltaRows    = { 1, -1,  0,  0 };
        for (int tileRow = 0; tileRow < LevelGenConstants.LEVEL_GEN_GRID_HEIGHT; tileRow++) {
            for (int tileColumn = 0; tileColumn < LevelGenConstants.LEVEL_GEN_GRID_WIDTH; tileColumn++) {
                if (grid[tileRow][tileColumn] != 'x') continue;
                boolean nearGore = false;
                for (int direction = 0; direction < 4; direction++) {
                    int neighborColumn = tileColumn + deltaColumns[direction];
                    int neighborRow    = tileRow    + deltaRows[direction];
                    if (!isInBounds(neighborColumn, neighborRow)) continue;
                    char neighbor = grid[neighborRow][neighborColumn];
                    if (neighbor == 'm' || neighbor == '.') nearGore = true;
                }
                if (nearGore && random.nextFloat() < LevelGenConstants.LEVEL_GEN_GORE_WALL_CHANCE) {
                    grid[tileRow][tileColumn] = 'G';
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Phase 5 — Connectivity audit
    // -------------------------------------------------------------------------

    private void verifyAndRepairConnectivity(char[][] grid, List<Room> rooms) {
        // Source the audit at the actual player spawn tile so reachability is measured from
        // where the player really starts (room 0's geometric centre may differ from 'p').
        int startColumn = rooms.get(0).centerColumn();
        int startRow    = rooms.get(0).centerRow();
        for (int tileRow = 0; tileRow < LevelGenConstants.LEVEL_GEN_GRID_HEIGHT; tileRow++) {
            for (int tileColumn = 0; tileColumn < LevelGenConstants.LEVEL_GEN_GRID_WIDTH; tileColumn++) {
                if (grid[tileRow][tileColumn] == 'p') {
                    startColumn = tileColumn;
                    startRow    = tileRow;
                }
            }
        }
        for (int roomIndex = 1; roomIndex < rooms.size(); roomIndex++) {
            Room room = rooms.get(roomIndex);
            if (!isTileReachable(grid, startColumn, startRow,
                                 room.centerColumn(), room.centerRow())) {
                carveEmergencyCorridor(grid, startColumn, startRow,
                                       room.centerColumn(), room.centerRow());
            }
        }
        repairUnreachableFloorRegions(grid, startColumn, startRow);
    }

    /**
     * Definitive connectivity backstop. The per-room audit checks only room centres, which can
     * miss a region that is walled off yet whose centre tile lies elsewhere. This pass floods
     * from the spawn and, while any walkable floor tile remains unreachable, carves a
     * blocker-clearing corridor from the spawn to one such tile — connecting that whole region.
     * It repeats until every floor tile is reachable, so the player can always reach all loot,
     * enemies, and the exit.
     */
    private void repairUnreachableFloorRegions(char[][] grid, int spawnColumn, int spawnRow) {
        int safetyCap = LevelGenConstants.LEVEL_GEN_GRID_WIDTH + LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        for (int iteration = 0; iteration < safetyCap; iteration++) {
            boolean[][] reachable = computeReachableFromSpawn(grid);
            int targetColumn = -1;
            int targetRow    = -1;
            for (int tileRow = 0; tileRow < LevelGenConstants.LEVEL_GEN_GRID_HEIGHT && targetColumn < 0; tileRow++) {
                for (int tileColumn = 0; tileColumn < LevelGenConstants.LEVEL_GEN_GRID_WIDTH; tileColumn++) {
                    if (!reachable[tileRow][tileColumn] && isWalkableFloor(grid, tileColumn, tileRow)) {
                        targetColumn = tileColumn;
                        targetRow    = tileRow;
                        break;
                    }
                }
            }
            if (targetColumn < 0) return; // every floor tile is reachable
            carveEmergencyCorridor(grid, spawnColumn, spawnRow, targetColumn, targetRow);
        }
    }

    private boolean isTileReachable(char[][] grid,
                                     int startColumn, int startRow,
                                     int targetColumn, int targetRow) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        boolean[][] visited = new boolean[gridHeight][gridWidth];
        int capacity        = gridWidth * gridHeight;
        int[] stackColumns  = new int[capacity];
        int[] stackRows     = new int[capacity];
        int   stackTop      = 0;

        visited[startRow][startColumn] = true;
        stackColumns[stackTop] = startColumn;
        stackRows[stackTop]    = startRow;
        stackTop++;

        int[] deltaColumns = { 0,  0,  1, -1 };
        int[] deltaRows    = { 1, -1,  0,  0 };

        while (stackTop > 0) {
            stackTop--;
            int currentColumn = stackColumns[stackTop];
            int currentRow    = stackRows[stackTop];
            if (currentColumn == targetColumn && currentRow == targetRow) return true;
            for (int direction = 0; direction < 4; direction++) {
                int neighborColumn = currentColumn + deltaColumns[direction];
                int neighborRow    = currentRow    + deltaRows[direction];
                if (!isInBounds(neighborColumn, neighborRow)) continue;
                if (visited[neighborRow][neighborColumn]) continue;
                if (!isPassableForBFS(grid[neighborRow][neighborColumn])) continue;
                visited[neighborRow][neighborColumn] = true;
                stackColumns[stackTop] = neighborColumn;
                stackRows[stackTop]    = neighborRow;
                stackTop++;
            }
        }
        return false;
    }

    /**
     * Connectivity passability — mirrors in-game movement, where only solid walls, solid
     * props, and cylindrical columns block the player. Floor, doors (openable), walkable
     * decals (blood, scorch, oil, corpses) and pickups are all walkable, so they must count
     * as passable here; treating a decal or pickup as a blocker would wrongly report a region
     * unreachable and make the repair pass loop trying to clear a tile that is not a blocker.
     */
    private boolean isPassableForBFS(char cell) {
        return !Level.isWall(cell) && !Level.isPropSolid(cell) && !Level.isColumn(cell);
    }

    private void carveEmergencyCorridor(char[][] grid,
                                         int fromColumn, int fromRow,
                                         int toColumn,   int toRow) {
        carveEmergencyHorizontal(grid, fromRow,   fromColumn, toColumn);
        carveEmergencyVertical(grid,   toColumn,  fromRow,    toRow);
    }

    private void carveEmergencyHorizontal(char[][] grid, int fixedRow, int column1, int column2) {
        int minColumn = Math.min(column1, column2);
        int maxColumn = Math.max(column1, column2);
        for (int tileColumn = minColumn; tileColumn <= maxColumn; tileColumn++) {
            if (!isInBounds(tileColumn, fixedRow)) continue;
            if (isEmergencyBlocker(grid[fixedRow][tileColumn])) {
                grid[fixedRow][tileColumn] = 'l';
            }
        }
    }

    private void carveEmergencyVertical(char[][] grid, int fixedColumn, int row1, int row2) {
        int minRow = Math.min(row1, row2);
        int maxRow = Math.max(row1, row2);
        for (int tileRow = minRow; tileRow <= maxRow; tileRow++) {
            if (!isInBounds(fixedColumn, tileRow)) continue;
            if (isEmergencyBlocker(grid[tileRow][fixedColumn])) {
                grid[tileRow][fixedColumn] = 'l';
            }
        }
    }

    /**
     * A tile an emergency corridor must clear to reach a stranded room: solid walls, solid
     * props, AND cylindrical columns 'P'. Columns are included because the audit otherwise
     * cannot punch through a column that happens to sit on the straight repair path, which
     * would leave the room sealed despite the repair pass running.
     */
    private boolean isEmergencyBlocker(char cell) {
        return Level.isWall(cell) || Level.isPropSolid(cell) || Level.isColumn(cell);
    }

    // -------------------------------------------------------------------------
    // Phase 6 — Stairs
    // -------------------------------------------------------------------------

    /**
     * Stamps exactly one exit. The tile is always chosen from the set of tiles reachable
     * from the player spawn, so the stairs can never strand in an isolated pocket — interior
     * structures, column avenues, and the connectivity audit's center-only guarantee
     * notwithstanding. Landmark rooms (Command Center, then Power Plant) are preferred, then
     * the deepest reachable room, with a whole-grid reachable-tile sweep as the final guard.
     */
    private void stampStairsDown(char[][] grid, List<Room> rooms) {
        boolean[][] reachable = computeReachableFromSpawn(grid);
        for (int roomIndex = rooms.size() - 1; roomIndex >= 1; roomIndex--) {
            Room room = rooms.get(roomIndex);
            if (room.type == RoomType.COMMAND_CENTER && tryStampInRoom(grid, room, reachable)) return;
        }
        for (int roomIndex = rooms.size() - 1; roomIndex >= 1; roomIndex--) {
            Room room = rooms.get(roomIndex);
            if (room.type == RoomType.POWER_PLANT && tryStampInRoom(grid, room, reachable)) return;
        }
        for (int roomIndex = rooms.size() - 1; roomIndex >= 1; roomIndex--) {
            if (tryStampInRoom(grid, rooms.get(roomIndex), reachable)) return;
        }
        if (!rooms.isEmpty() && tryStampInRoom(grid, rooms.get(0), reachable)) return;
        // Final guard: stamp on any reachable walkable tile anywhere on the grid.
        for (int tileRow = 0; tileRow < LevelGenConstants.LEVEL_GEN_GRID_HEIGHT; tileRow++) {
            for (int tileColumn = 0; tileColumn < LevelGenConstants.LEVEL_GEN_GRID_WIDTH; tileColumn++) {
                if (reachable[tileRow][tileColumn] && isWalkableFloor(grid, tileColumn, tileRow)) {
                    grid[tileRow][tileColumn] = RenderConstants.STAIRS_DOWN_CHAR;
                    return;
                }
            }
        }
    }

    private boolean tryStampInRoom(char[][] grid, Room room, boolean[][] reachable) {
        int centerColumn = room.centerColumn();
        int centerRow    = room.centerRow();
        if (isInBounds(centerColumn, centerRow) && reachable[centerRow][centerColumn]
                && isWalkableFloor(grid, centerColumn, centerRow)) {
            grid[centerRow][centerColumn] = RenderConstants.STAIRS_DOWN_CHAR;
            return true;
        }
        for (int tileRow = room.bottomRow + 1; tileRow < room.topRow; tileRow++) {
            for (int tileColumn = room.leftColumn + 1; tileColumn < room.rightColumn; tileColumn++) {
                if (reachable[tileRow][tileColumn] && isWalkableFloor(grid, tileColumn, tileRow)) {
                    grid[tileRow][tileColumn] = RenderConstants.STAIRS_DOWN_CHAR;
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Flood-fills the set of tiles reachable from the player spawn 'p' across passable tiles,
     * matching the same passability rule the connectivity audit uses.
     */
    private boolean[][] computeReachableFromSpawn(char[][] grid) {
        int gridWidth  = LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
        int gridHeight = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
        boolean[][] visited = new boolean[gridHeight][gridWidth];
        int spawnColumn = -1;
        int spawnRow    = -1;
        for (int tileRow = 0; tileRow < gridHeight && spawnColumn < 0; tileRow++) {
            for (int tileColumn = 0; tileColumn < gridWidth; tileColumn++) {
                if (grid[tileRow][tileColumn] == 'p') {
                    spawnColumn = tileColumn;
                    spawnRow    = tileRow;
                    break;
                }
            }
        }
        if (spawnColumn < 0) return visited;

        int[] stackColumns = new int[gridWidth * gridHeight];
        int[] stackRows    = new int[gridWidth * gridHeight];
        int   stackTop     = 0;
        visited[spawnRow][spawnColumn] = true;
        stackColumns[stackTop] = spawnColumn;
        stackRows[stackTop]    = spawnRow;
        stackTop++;

        int[] deltaColumns = { 0,  0,  1, -1 };
        int[] deltaRows    = { 1, -1,  0,  0 };
        while (stackTop > 0) {
            stackTop--;
            int currentColumn = stackColumns[stackTop];
            int currentRow    = stackRows[stackTop];
            for (int direction = 0; direction < 4; direction++) {
                int neighborColumn = currentColumn + deltaColumns[direction];
                int neighborRow    = currentRow    + deltaRows[direction];
                if (!isInBounds(neighborColumn, neighborRow)) continue;
                if (visited[neighborRow][neighborColumn]) continue;
                if (!isPassableForBFS(grid[neighborRow][neighborColumn])) continue;
                visited[neighborRow][neighborColumn] = true;
                stackColumns[stackTop] = neighborColumn;
                stackRows[stackTop]    = neighborRow;
                stackTop++;
            }
        }
        return visited;
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    private boolean isWalkableFloor(char[][] grid, int tileColumn, int tileRow) {
        if (!isInBounds(tileColumn, tileRow)) return false;
        char cell = grid[tileRow][tileColumn];
        return cell == ' ' || cell == 'l' || cell == 'u' || cell == 'f';
    }

    private boolean isEnemySpawnEligible(char[][] grid, int tileColumn, int tileRow) {
        if (!isInBounds(tileColumn, tileRow)) return false;
        char cell = grid[tileRow][tileColumn];
        if (cell == ' ' || cell == 'l' || cell == 'u' || cell == 'f') return true;
        return cell == '.' || cell == 'O' || cell == 'm';
    }

    private boolean isInBounds(int tileColumn, int tileRow) {
        return tileColumn >= 0 && tileColumn < LevelGenConstants.LEVEL_GEN_GRID_WIDTH
            && tileRow    >= 0 && tileRow    < LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
    }

    private int randomBetween(int minInclusive, int maxInclusive) {
        if (maxInclusive <= minInclusive) return minInclusive;
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }

    private float randomFloat(float minInclusive, float maxInclusive) {
        return minInclusive + random.nextFloat() * (maxInclusive - minInclusive);
    }

    private void fillAll(char[][] grid, char fillChar) {
        for (int tileRow = 0; tileRow < LevelGenConstants.LEVEL_GEN_GRID_HEIGHT; tileRow++) {
            for (int tileColumn = 0; tileColumn < LevelGenConstants.LEVEL_GEN_GRID_WIDTH; tileColumn++) {
                grid[tileRow][tileColumn] = fillChar;
            }
        }
    }

    private Level buildFallbackLevel() {
        char[][] grid        = new char[LevelGenConstants.LEVEL_GEN_GRID_HEIGHT][LevelGenConstants.LEVEL_GEN_GRID_WIDTH];
        int centerColumn     = LevelGenConstants.LEVEL_GEN_GRID_WIDTH  / 2;
        int centerRow        = LevelGenConstants.LEVEL_GEN_GRID_HEIGHT / 2;
        fillAll(grid, 'x');
        for (int tileRow = centerRow - 2; tileRow <= centerRow + 2; tileRow++) {
            for (int tileColumn = centerColumn - 4; tileColumn <= centerColumn + 4; tileColumn++) {
                grid[tileRow][tileColumn] = ' ';
            }
        }
        grid[centerRow][centerColumn] = 'p';
        return new Level(grid, new ArrayList<>(), new ArrayList<>(),
                         LevelPalettes.generatedWithBaseWall(seed));
    }
}
