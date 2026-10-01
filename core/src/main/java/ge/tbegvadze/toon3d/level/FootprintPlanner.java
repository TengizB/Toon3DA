package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.LevelGenConstants;

import java.util.Random;

/**
 * FOOTPRINT TARGETS (balance-overhaul order 2, E6): the three combat generators build to a target number of
 * WALKABLE tiles from their region's range (region A 350-550, B 450-650, C/D/E 500-750; an ELITE floor the
 * lower half) instead of always filling the 80x45 grid — "levels are big but empty". Each generator exposes
 * one size knob (a 0..1 footprint SCALE: a centred layout window for rooms and caves, the spine length for
 * corridors); this class picks the target, then builds, measures the walkable footprint, and rebuilds with a
 * proportionally corrected scale until it lands within {@link BalanceConfig#FOOTPRINT_TOLERANCE}, keeping
 * the closest build. Deterministic: attempt 0 uses the floor seed itself, later attempts a derived seed.
 */
public final class FootprintPlanner {

    /** Seed salt for the target roll (independent of the layout stream). */
    private static final long TARGET_SEED_SALT  = 0xF007_9417L;
    /** Seed step between rebuild attempts. */
    private static final long ATTEMPT_SEED_STEP = 0x9E3779B97F4A7C15L;


    private FootprintPlanner() {}

    /**
     * The walkable-tile RANGE a floor's region allows (E6): region A 350-550, B 450-650, C/D/E 500-750
     * (deeper regions reuse the last row); an ELITE floor the lower half.
     */
    public static int[] footprintRange(ge.tbegvadze.toon3d.route.NodeSupplySpec spec, int depth) {
        int region = regionIndex(depth);
        int low  = GameMath.footprintTargetWalkableTiles(BalanceConfig.FOOTPRINT_MIN_BY_REGION,
                BalanceConfig.FOOTPRINT_MAX_BY_REGION, region, 0f, spec.footprintLowerHalf());
        int high = GameMath.footprintTargetWalkableTiles(BalanceConfig.FOOTPRINT_MIN_BY_REGION,
                BalanceConfig.FOOTPRINT_MAX_BY_REGION, region, 1f, spec.footprintLowerHalf());
        return new int[]{low, high};
    }

    /**
     * The walkable-tile target a floor first aims at: the config's explicit target when positive, none
     * when negative (natural size), else the region's range at the floor's seeded roll.
     */
    public static int targetWalkableTiles(LevelGenConfig config, int depth, long seed) {
        if (config != null && config.targetWalkableTiles > 0) return config.targetWalkableTiles;
        if (config != null && config.targetWalkableTiles < 0) return 0;
        float roll = new Random(seed ^ TARGET_SEED_SALT).nextFloat();
        return GameMath.footprintTargetWalkableTiles(BalanceConfig.FOOTPRINT_MIN_BY_REGION,
                BalanceConfig.FOOTPRINT_MAX_BY_REGION, regionIndex(depth), roll,
                FloorPopulator.specOf(config).footprintLowerHalf());
    }

    /**
     * Builds toward the floor's footprint. With no target (explicitly natural size) builds once at full
     * scale. Otherwise each build is measured: when the node's spec has a density band (E7), the target
     * FOLLOWS the bodies that build actually fielded — bodies / the band's midpoint density, clamped to
     * the region range (E6) — so the footprint and the density hold together; the loop stops at the first
     * build within tolerance of that target AND inside the density band, else keeps the closest.
     *
     * @param config          the floor's config (spec, explicit target)
     * @param depth           floor depth (1-based)
     * @param naturalWalkable the generator's typical walkable tiles at scale 1 (the first guess's base)
     * @param seed            the floor seed (attempt 0 builds with it unchanged)
     * @param builder         one scaled build; it is told the current target for its report
     */
    public static Level buildToTarget(LevelGenConfig config, int depth, float naturalWalkable, long seed,
                                      TargetedBuild builder) {
        int target = targetWalkableTiles(config, depth, seed);
        if (target <= 0) return builder.build(1f, seed, 0);
        ge.tbegvadze.toon3d.route.NodeSupplySpec spec = FloorPopulator.specOf(config);
        boolean explicit = config != null && config.targetWalkableTiles > 0;
        int[] range = footprintRange(spec, depth);
        boolean densityAim = spec.hasDensityBand() && !explicit;
        float aim = densityAim ? (spec.densityMin() + spec.densityMax()) / 2f : 0f;

        float scale = clampScale(target / Math.max(1f, naturalWalkable));
        Level best = null;
        float bestError = Float.MAX_VALUE;
        for (int attempt = 0; attempt < LevelGenConstants.FOOTPRINT_MAX_ATTEMPTS; attempt++) {
            Level built = builder.build(scale, seed + attempt * ATTEMPT_SEED_STEP, target);
            FloorContentReport report = built.getFloorContentReport();
            int walkable = report != null ? report.walkableTiles : 0;
            if (densityAim && report != null && report.enemyCount > 0) {
                target = Math.max(range[0], Math.min(range[1], GameMath.walkableTilesForDensity(report.enemyCount, aim)));
            }
            float error = Math.abs(walkable - target) / (float) Math.max(1, target);
            boolean densityOk = !densityAim || report == null
                    || (report.density() >= spec.densityMin() && report.density() <= spec.densityMax());
            boolean inRange = explicit || (walkable >= range[0] && walkable <= range[1]);
            // A build that breaks the density band or leaves the region range ranks behind every one that holds.
            float rank = error + (densityOk ? 0f : 1f) + (inRange ? 0f : 1f);
            if (best == null || rank < bestError) {
                best      = built;
                bestError = rank;
            }
            if (densityOk && inRange && error <= BalanceConfig.FOOTPRINT_TOLERANCE * LevelGenConstants.FOOTPRINT_AIM_FRACTION) {
                break;
            }
            if (walkable <= 0) break;
            scale = clampScale(scale * target / walkable);
        }
        return best;
    }

    /** One build of a generator at a footprint scale with a given RNG seed, told its current target. */
    public interface TargetedBuild {
        Level build(float footprintScale, long attemptSeed, int targetWalkableTiles);
    }

    private static int regionIndex(int depth) {
        return Math.max(0, Math.max(1, depth) - 1) / Math.max(1, BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE);
    }

    private static float clampScale(float scale) {
        return Math.max(LevelGenConstants.FOOTPRINT_MIN_SCALE, Math.min(1f, scale));
    }
}
