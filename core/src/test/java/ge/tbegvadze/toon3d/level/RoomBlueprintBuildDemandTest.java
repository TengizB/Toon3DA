package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.tileset.SymbolBudget;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Drift guard between what a SIGNATURE {@link RoomBlueprint} DECLARES ({@link RoomBlueprint#symbolDemand()})
 * and what its {@link RoomBlueprint#build(RoomBuildContext)} actually STAMPS. For each signature room this
 * runs {@code build()} on a fresh, generously sized room across many seeds and collects every FLEXIBLE
 * environment symbol written into the room. Over enough seeds that set must equal the blueprint's declared
 * demand exactly — a missing symbol means the demand over-declares, an extra symbol means it under-declares.
 * (Runs {@code build()} directly, off the {@code generate()} path, exactly as order-8 eventually will.)
 */
class RoomBlueprintBuildDemandTest {

    private static final int SEED_COUNT = 400;
    // A large near-square room satisfies every signature room's minimum-size gate (incl. the observatory).
    private static final int ROOM_LEFT   = 2;
    private static final int ROOM_BOTTOM = 2;
    private static final int ROOM_RIGHT  = 19; // interior width  = 19 - 2 - 1 = 16
    private static final int ROOM_TOP    = 19; // interior height = 16

    private static final Map<String, LevelGenerator.RoomType> ID_TO_TYPE = buildIdToType();

    @Test
    void everySignatureBlueprintStampsExactlyItsDeclaredDemand() {
        SymbolBudget budget = SymbolBudget.standard();
        RoomBlueprintRegistry registry = new RoomBlueprintRegistry();
        RoomBlueprints.registerAll(registry);

        for (RoomBlueprint blueprint : registry.allSignature()) {
            LevelGenerator.RoomType type = ID_TO_TYPE.get(blueprint.id());
            TreeSet<Character> stampedFlexibleSymbols = new TreeSet<>();

            for (long seed = 1; seed <= SEED_COUNT; seed++) {
                LevelGenerator generator = new LevelGenerator(seed);
                char[][] grid = freshRoomGrid();
                LevelGenerator.Room room =
                        new LevelGenerator.Room(ROOM_LEFT, ROOM_BOTTOM, ROOM_RIGHT, ROOM_TOP);
                room.type = type;

                blueprint.build(new RoomBuildContext(generator, grid, room));
                collectFlexibleSymbols(grid, budget, stampedFlexibleSymbols);
            }

            TreeSet<Character> declared = new TreeSet<>(blueprint.symbolDemand().requiredSprites().keySet());
            assertEquals(declared, stampedFlexibleSymbols,
                    blueprint.id() + ": build() stamped flexible symbols must equal its declared demand");
        }
    }

    /** A grid of solid wall with the test room's interior carved to floor (mirrors carveRoomInteriors). */
    private static char[][] freshRoomGrid() {
        char[][] grid = new char[GameGridHeight()][GameGridWidth()];
        for (char[] row : grid) {
            java.util.Arrays.fill(row, 'x');
        }
        for (int tileRow = ROOM_BOTTOM + 1; tileRow < ROOM_TOP; tileRow++) {
            for (int tileColumn = ROOM_LEFT + 1; tileColumn < ROOM_RIGHT; tileColumn++) {
                grid[tileRow][tileColumn] = ' ';
            }
        }
        return grid;
    }

    private static void collectFlexibleSymbols(char[][] grid, SymbolBudget budget,
                                               TreeSet<Character> into) {
        for (int tileRow = ROOM_BOTTOM; tileRow <= ROOM_TOP; tileRow++) {
            for (int tileColumn = ROOM_LEFT; tileColumn <= ROOM_RIGHT; tileColumn++) {
                char cell = grid[tileRow][tileColumn];
                if (budget.isFlexible(cell)) {
                    into.add(cell);
                }
            }
        }
    }

    private static int GameGridWidth() {
        return ge.tbegvadze.toon3d.util.LevelGenConstants.LEVEL_GEN_GRID_WIDTH;
    }

    private static int GameGridHeight() {
        return ge.tbegvadze.toon3d.util.LevelGenConstants.LEVEL_GEN_GRID_HEIGHT;
    }

    private static Map<String, LevelGenerator.RoomType> buildIdToType() {
        Map<String, LevelGenerator.RoomType> map = new HashMap<>();
        map.put(RoomBlueprints.ID_SERVER_ROOM,         LevelGenerator.RoomType.SERVER_ROOM);
        map.put(RoomBlueprints.ID_MEDICAL_BAY,         LevelGenerator.RoomType.MEDICAL_BAY);
        map.put(RoomBlueprints.ID_ARMORY,              LevelGenerator.RoomType.ARMORY);
        map.put(RoomBlueprints.ID_CRYO_CHAMBER,        LevelGenerator.RoomType.CRYO_CHAMBER);
        map.put(RoomBlueprints.ID_POWER_PLANT,         LevelGenerator.RoomType.POWER_PLANT);
        map.put(RoomBlueprints.ID_COMMAND_CENTER,      LevelGenerator.RoomType.COMMAND_CENTER);
        map.put(RoomBlueprints.ID_CONTAINMENT_BLOCK,   LevelGenerator.RoomType.CONTAINMENT_BLOCK);
        map.put(RoomBlueprints.ID_RESEARCH_LAB,        LevelGenerator.RoomType.RESEARCH_LAB);
        map.put(RoomBlueprints.ID_STELLAR_OBSERVATORY, LevelGenerator.RoomType.STELLAR_OBSERVATORY);
        return map;
    }
}
