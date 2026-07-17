package ge.tbegvadze.toon3d.level;

import java.util.Collections;
import java.util.List;

/**
 * The build-time facade a {@link RoomBlueprint#build(RoomBuildContext)} writes through (symbol/sprite-
 * reuse order-5). It wraps a {@link LevelGenerator} instance plus the target grid and the one
 * {@link LevelGenerator.Room} being stamped, and exposes that room type's architecture steps as thin
 * package-private delegators to the generator's EXISTING per-room helpers — so a blueprint stamps its
 * room with the identical code (and identical RNG draw sequence) the generator's own passes use, rather
 * than a copy that could drift.
 *
 * <p>Most delegators invoke a generator per-type pass scoped to a single-room list; the two index-1
 * passes (STANDARD props/columns) delegate to dedicated {@code *ForRoom} helpers the generator extracted
 * for exactly this purpose. Nothing here writes chars directly — layout lives in the generator.
 *
 * <p>order-5 does NOT wire {@code LevelGenerator.generate()} to call {@code build()}; this context is
 * driven by the order-5 blueprint tests today and becomes the generator's room-stamping seam in order-8.
 * Headless — no LibGDX.
 */
public final class RoomBuildContext {

    private final LevelGenerator generator;
    private final char[][] grid;
    private final LevelGenerator.Room room;
    private final List<LevelGenerator.Room> singletonRoom;

    RoomBuildContext(LevelGenerator generator, char[][] grid, LevelGenerator.Room room) {
        this.generator     = generator;
        this.grid          = grid;
        this.room          = room;
        this.singletonRoom = Collections.singletonList(room);
    }

    // --- Floor lighting (dispatches by the room's type tag, exactly like the generate() pass) ---

    void assignFloorLighting() {
        generator.assignFloorLighting(grid, singletonRoom);
    }

    // --- Wall theming ---

    void themeServerRoomWalls() {
        generator.themeServerRoomWallsForRoom(grid, room);
    }

    void themeNewRoomWalls() {
        generator.themeNewRoomWallsForRoom(grid, room);
    }

    // --- Columns ---

    void placeStandardColumns() {
        generator.placeStandardColumnsForRoom(grid, room);
    }

    // --- Props (per room type) ---

    void placeStandardProps() {
        generator.placeStandardPropsForRoom(grid, room);
    }

    void placeServerRoomProps() {
        generator.placeServerRoomProps(grid, singletonRoom);
    }

    void placeMedicalBayProps() {
        generator.placeMedicalBayProps(grid, singletonRoom);
    }

    void placeArmoryProps() {
        generator.placeArmoryProps(grid, singletonRoom);
    }

    void placeCryoChamberProps() {
        generator.placeCryoChamberProps(grid, singletonRoom);
    }

    void placePowerPlantProps() {
        generator.placePowerPlantProps(grid, singletonRoom);
    }

    void placeCommandCenterProps() {
        generator.placeCommandCenterProps(grid, singletonRoom);
    }

    void placeContainmentBlockProps() {
        generator.placeContainmentBlockProps(grid, singletonRoom);
    }

    void placeResearchLabProps() {
        generator.placeResearchLabProps(grid, singletonRoom);
    }

    void placeStellarObservatoryProps() {
        generator.placeStellarObservatoryProps(grid, singletonRoom);
    }

    void placeGoreNestProps() {
        generator.placeGoreNestProps(grid, singletonRoom);
    }

    // order-8 REQUIREMENT PROOF 1: the salvage bay stamps its own floor + walls + heap + racks + decals
    // + columns in one self-contained pass, so it needs no shared floor-lighting/wall-theming switch case.
    void placeSalvageBay() {
        generator.placeSalvageBayForRoom(grid, room);
    }
}
