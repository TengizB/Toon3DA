package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.LevelGenConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * THE SPECIAL ROOM PLANNER (level-generation-overhaul R1-R4): which special rooms a combat floor carries, decided
 * FIRST, before any footprint attempt. The pool is every registered blueprint other than the entrance and the
 * standard room; the floor draws N of them uniformly WITHOUT replacement (no duplicate type, no weight, no depth
 * input, no cap), from the floor seed salted with {@code SPECIAL_ROOM_SEED_SALT} — so a footprint retry never
 * changes WHICH specials appear. N comes from the footprint target ({@link GameMath#specialRoomCountForFootprint}).
 * Each attempt then sizes every planned special from its window, scaled by the attempt's footprint scale.
 * Headless — no LibGDX.
 */
public final class SpecialRoomPlanner {

    private SpecialRoomPlanner() {}

    /** Every SPECIAL blueprint (R1), in registration order. */
    public static List<RoomBlueprint> specialPool(RoomBlueprintRegistry registry) {
        List<RoomBlueprint> pool = new ArrayList<>();
        for (RoomBlueprint blueprint : registry.all()) {
            if (isSpecial(blueprint.id())) pool.add(blueprint);
        }
        return Collections.unmodifiableList(pool);
    }

    /** Whether a blueprint id is a special room (anything but the entrance and the standard room). */
    public static boolean isSpecial(String blueprintId) {
        return !RoomBlueprints.ID_ENTRANCE.equals(blueprintId) && !RoomBlueprints.ID_STANDARD.equals(blueprintId);
    }

    /**
     * The floor's planned specials (R2/R3): N = {@link GameMath#specialRoomCountForFootprint}(target) distinct
     * types, drawn uniformly without replacement (a partial Fisher-Yates over the pool) from the floor seed ^
     * {@code SPECIAL_ROOM_SEED_SALT}. Same seed and target -> the same list, in draw order.
     */
    public static List<RoomBlueprint> plan(long floorSeed, int targetWalkableTiles) {
        RoomBlueprints.bootstrap();
        List<RoomBlueprint> pool = new ArrayList<>(specialPool(RoomBlueprints.rooms()));
        int count = Math.min(pool.size(), GameMath.specialRoomCountForFootprint(targetWalkableTiles));
        Random random = new Random(GameMath.mixSeed(floorSeed, LevelGenConstants.SPECIAL_ROOM_SEED_SALT));
        List<RoomBlueprint> planned = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            int pick = index + random.nextInt(pool.size() - index);
            Collections.swap(pool, index, pick);
            planned.add(pool.get(index));
        }
        return Collections.unmodifiableList(planned);
    }

    /** The planned ids, for the layout report. */
    public static List<String> ids(List<RoomBlueprint> planned) {
        List<String> ids = new ArrayList<>();
        for (RoomBlueprint blueprint : planned) ids.add(blueprint.id());
        return ids;
    }

    /**
     * One special's interior for a footprint attempt (R4): sized from its window by the attempt's scale, or the
     * window minimum when {@code atMinimum} (the R5 shrink retry). Returns {@code {interiorWidth, interiorHeight}}.
     */
    public static int[] size(RoomBlueprint blueprint, Random attemptRandom, float footprintScale, boolean atMinimum) {
        RoomSizeWindow window = blueprint.sizeWindow() != null ? blueprint.sizeWindow() : RoomBlueprints.regularWindow();
        return atMinimum ? window.minimum() : window.size(attemptRandom, footprintScale);
    }
}
