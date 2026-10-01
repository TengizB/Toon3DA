package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.LevelGenConstants;

/**
 * FOOTPRINT TARGETS (balance-overhaul order 2, E6 — owner override 2026-10-01): the three combat layouts
 * build to a CUT from their ORIGINAL walkable size instead of always filling the 80x45 grid — half on
 * floor 1, then 40% / 30% / 25%, and a fifth from floor 5 on ({@link BalanceConfig#FOOTPRINT_REDUCTION_BY_DEPTH}
 * x the layout's {@code FOOTPRINT_ORIGINAL_WALKABLE_*}). Each generator exposes one size knob (a 0..1
 * footprint SCALE: a centred layout window for rooms and caves, the spine length for corridors); this class
 * builds, measures the walkable footprint, and rebuilds at a scale corrected by the mean tiles-per-scale
 * measured so far until it lands within {@link BalanceConfig#FOOTPRINT_TOLERANCE}, keeping the closest build
 * that fielded its body target. Deterministic: attempt 0 uses
 * the floor seed itself, later attempts a derived seed.
 */
public final class FootprintPlanner {

    /** Seed step between rebuild attempts. */
    private static final long ATTEMPT_SEED_STEP = 0x9E3779B97F4A7C15L;

    /** Each footprinted layout's ORIGINAL walkable size, by generator stable id (the E6 baseline). */
    private static final java.util.Map<String, Integer> ORIGINAL_WALKABLE_TILES = buildOriginals();

    private FootprintPlanner() {}

    private static java.util.Map<String, Integer> buildOriginals() {
        java.util.Map<String, Integer> originals = new java.util.HashMap<>();
        originals.put(ge.tbegvadze.toon3d.route.GeneratorId.ROOMS_MST.stableId(),
                BalanceConfig.FOOTPRINT_ORIGINAL_WALKABLE_ROOMS);
        originals.put(ge.tbegvadze.toon3d.route.GeneratorId.LINEAR_CORRIDOR.stableId(),
                BalanceConfig.FOOTPRINT_ORIGINAL_WALKABLE_LINEAR);
        originals.put(ge.tbegvadze.toon3d.route.GeneratorId.CAVERN.stableId(),
                BalanceConfig.FOOTPRINT_ORIGINAL_WALKABLE_CAVERN);
        return java.util.Collections.unmodifiableMap(originals);
    }

    /** Whether a generator (by stable id) builds to an E6 footprint target. */
    public static boolean isFootprinted(String generatorName) {
        return ORIGINAL_WALKABLE_TILES.containsKey(generatorName);
    }

    /** The E6 target for a footprinted generator (by stable id) at a depth, or 0 when it has none. */
    public static int targetWalkableTiles(String generatorName, int depth) {
        Integer original = ORIGINAL_WALKABLE_TILES.get(generatorName);
        return original == null ? 0 : GameMath.footprintTargetWalkableTiles(original,
                BalanceConfig.FOOTPRINT_REDUCTION_BY_DEPTH, depth);
    }

    /**
     * The walkable-tile target a floor builds to: the config's explicit target when positive, none when
     * negative (natural size), else the cut from the layout's original size at this depth.
     */
    public static int targetWalkableTiles(LevelGenConfig config, int depth, float originalWalkableTiles) {
        if (config != null && config.targetWalkableTiles > 0) return config.targetWalkableTiles;
        if (config != null && config.targetWalkableTiles < 0) return 0;
        return GameMath.footprintTargetWalkableTiles(Math.round(originalWalkableTiles),
                BalanceConfig.FOOTPRINT_REDUCTION_BY_DEPTH, depth);
    }

    /**
     * Builds toward the floor's footprint. With no target (explicitly natural size) builds once at full
     * scale. Otherwise each build is measured and the scale corrected by target / (mean tiles per unit scale so
     * far); the loop stops at the first build that fields its bodies within {@code FOOTPRINT_AIM_FRACTION} of
     * the tolerance, else keeps the closest such build.
     *
     * @param config                the floor's config (explicit target)
     * @param depth                 floor depth (1-based)
     * @param originalWalkableTiles the generator's ORIGINAL walkable tiles at scale 1 (the cut's baseline)
     * @param seed                  the floor seed (attempt 0 builds with it unchanged)
     * @param builder               one scaled build; it is told the target for its report
     */
    public static Level buildToTarget(LevelGenConfig config, int depth, float originalWalkableTiles, long seed,
                                      TargetedBuild builder) {
        int target = targetWalkableTiles(config, depth, originalWalkableTiles);
        if (target <= 0) return builder.build(1f, seed, 0);

        float scale = clampScale(target / Math.max(1f, originalWalkableTiles));
        Level best = null;
        float bestError = Float.MAX_VALUE;
        float tilesPerScaleSum = 0f;
        for (int attempt = 0; attempt < LevelGenConstants.FOOTPRINT_MAX_ATTEMPTS; attempt++) {
            Level built = builder.build(scale, seed + attempt * ATTEMPT_SEED_STEP, target);
            FloorContentReport report = built.getFloorContentReport();
            int walkable = report != null ? report.walkableTiles : 0;
            float error = Math.abs(walkable - target) / (float) Math.max(1, target);
            // A build whose encounter fell short of its body target (E1) ranks behind every build that
            // fielded it: the footprint must never be bought with an empty floor.
            boolean fielded = report == null || report.enemyCount >= report.bodyTarget;
            float rank = error + (fielded ? 0f : 1f);
            if (best == null || rank < bestError) {
                best      = built;
                bestError = rank;
            }
            if (fielded && error <= BalanceConfig.FOOTPRINT_TOLERANCE * LevelGenConstants.FOOTPRINT_AIM_FRACTION) break;
            if (walkable <= 0) break;
            // A layout's size at a given scale varies a lot from seed to seed (a corridor spine at scale 0.6
            // measures anywhere from ~250 to ~600 tiles), so the correction uses the MEAN tiles-per-scale over
            // every attempt so far rather than the last attempt alone, which would chase that noise.
            tilesPerScaleSum += walkable / scale;
            scale = clampScale(target / (tilesPerScaleSum / (attempt + 1)));
        }
        return best;
    }

    /** One build of a generator at a footprint scale with a given RNG seed, told its target. */
    public interface TargetedBuild {
        Level build(float footprintScale, long attemptSeed, int targetWalkableTiles);
    }

    private static float clampScale(float scale) {
        return Math.max(LevelGenConstants.FOOTPRINT_MIN_SCALE, Math.min(1f, scale));
    }
}
