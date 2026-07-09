package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.util.RouteMapConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The data-driven blueprint of the descent's act structure: an ordered list of {@link RegionSpec}
 * bands the {@link RouteMapGenerator} consumes. Keeping region count and shape as DATA (not code)
 * means new acts are added as rows in {@link #defaultPlan()}, never as new branches in the generator.
 *
 * <p>The plan grows: {@link #defaultPlan()} seeds the fixed acts A/B/C plus the first endless
 * "THE BREACH" band, and {@link #withAppendedBreachBand()} returns a longer plan for endless-mode
 * lazy extension. Instances are immutable — appending yields a new plan.
 *
 * <p>Pure data — no LibGDX, headless-testable.
 */
public final class RegionPlan {

    private final List<RegionSpec> regions;

    public RegionPlan(List<RegionSpec> regions) {
        if (regions.isEmpty()) {
            throw new IllegalArgumentException("A RegionPlan needs at least one region");
        }
        this.regions = Collections.unmodifiableList(new ArrayList<>(regions));
    }

    /** The region bands, in descent order. Unmodifiable. */
    public List<RegionSpec> regions() {
        return regions;
    }

    /** Number of region bands currently in the plan. */
    public int regionCount() {
        return regions.size();
    }

    /** The deepest floor any region in this plan reaches (the last region's last depth). */
    public int finalDepth() {
        return regions.get(regions.size() - 1).lastDepth();
    }

    /**
     * The region band that owns a given depth.
     *
     * @throws IllegalArgumentException if no band in this plan covers that depth
     */
    public RegionSpec regionForDepth(int depth) {
        for (RegionSpec region : regions) {
            if (region.containsDepth(depth)) {
                return region;
            }
        }
        throw new IllegalArgumentException("No region in the plan covers depth " + depth);
    }

    /**
     * Returns a new plan with one more endless "THE BREACH" band appended after the current final
     * region. Used by {@link RouteMapGenerator} endless extension; the plan itself stays immutable.
     */
    public RegionPlan withAppendedBreachBand() {
        List<RegionSpec> extended = new ArrayList<>(regions);
        int nextIndex      = regions.size();
        int nextFirstDepth = finalDepth() + 1;
        extended.add(breachBand(nextIndex, nextFirstDepth));
        return new RegionPlan(extended);
    }

    /**
     * The default descent: three named acts (OUTER FACILITY, RESEARCH WING, REACTOR DEPTHS) followed
     * by the first endless "THE BREACH" band. Every band is {@code REGION_BAND_SIZE} deep and ends on
     * a boss floor. Bias tables come from {@link RouteMapConstants} so no distribution number is
     * hard-coded here.
     */
    public static RegionPlan defaultPlan() {
        int band = RouteMapConstants.REGION_BAND_SIZE;

        RegionSpec outerFacility = RegionSpec.builder(0)
                .displayName(RouteMapConstants.REGION_A_NAME)
                .themeId(RouteMapConstants.REGION_A_THEME)
                .depthSpan(1, band)
                .nodeTypeMultiplier(RouteNodeType.COMBAT,  RouteMapConstants.REGION_A_MULTIPLIER_COMBAT)
                .nodeTypeMultiplier(RouteNodeType.ELITE,   RouteMapConstants.REGION_A_MULTIPLIER_ELITE)
                .nodeTypeMultiplier(RouteNodeType.CACHE,   RouteMapConstants.REGION_A_MULTIPLIER_CACHE)
                .nodeTypeMultiplier(RouteNodeType.MYSTERY, RouteMapConstants.REGION_A_MULTIPLIER_MYSTERY)
                .build();

        RegionSpec researchWing = RegionSpec.builder(1)
                .displayName(RouteMapConstants.REGION_B_NAME)
                .themeId(RouteMapConstants.REGION_B_THEME)
                .depthSpan(band + 1, 2 * band)
                .nodeTypeMultiplier(RouteNodeType.MYSTERY, RouteMapConstants.REGION_B_MULTIPLIER_MYSTERY)
                .nodeTypeMultiplier(RouteNodeType.EVENT,   RouteMapConstants.REGION_B_MULTIPLIER_EVENT)
                .nodeTypeMultiplier(RouteNodeType.ELITE,   RouteMapConstants.REGION_B_MULTIPLIER_ELITE)
                .generatorWeight(GeneratorId.ROOMS_MST,    RouteMapConstants.REGION_B_GENERATOR_WEIGHT_ROOMS)
                .build();

        RegionSpec reactorDepths = RegionSpec.builder(2)
                .displayName(RouteMapConstants.REGION_C_NAME)
                .themeId(RouteMapConstants.REGION_C_THEME)
                .depthSpan(2 * band + 1, 3 * band)
                .nodeTypeMultiplier(RouteNodeType.ELITE, RouteMapConstants.REGION_C_MULTIPLIER_ELITE)
                .nodeTypeMultiplier(RouteNodeType.REST,  RouteMapConstants.REGION_C_MULTIPLIER_REST)
                .nodeTypeMultiplier(RouteNodeType.SHOP,  RouteMapConstants.REGION_C_MULTIPLIER_SHOP)
                .generatorWeight(GeneratorId.CAVERN,     RouteMapConstants.REGION_C_GENERATOR_WEIGHT_CAVERN)
                .build();

        List<RegionSpec> plan = new ArrayList<>();
        plan.add(outerFacility);
        plan.add(researchWing);
        plan.add(reactorDepths);
        plan.add(breachBand(RouteMapConstants.FIXED_REGION_COUNT, 3 * band + 1));
        return new RegionPlan(plan);
    }

    /**
     * Builds one endless "THE BREACH" band. All breach bands share a name/theme and lethal biases;
     * only their index and depth span advance. Kept package-private-friendly (static) so both
     * {@link #defaultPlan()} and {@link #withAppendedBreachBand()} produce identical bands.
     */
    private static RegionSpec breachBand(int regionIndex, int firstDepth) {
        int lastDepth = firstDepth + RouteMapConstants.REGION_BAND_SIZE - 1;
        return RegionSpec.builder(regionIndex)
                .displayName(RouteMapConstants.REGION_D_NAME)
                .themeId(RouteMapConstants.REGION_D_THEME)
                .depthSpan(firstDepth, lastDepth)
                .endless(true)
                .nodeTypeMultiplier(RouteNodeType.ELITE, RouteMapConstants.REGION_D_MULTIPLIER_ELITE)
                .nodeTypeMultiplier(RouteNodeType.REST,  RouteMapConstants.REGION_D_MULTIPLIER_REST)
                .nodeTypeMultiplier(RouteNodeType.SHOP,  RouteMapConstants.REGION_D_MULTIPLIER_SHOP)
                .generatorWeight(GeneratorId.CAVERN,     RouteMapConstants.REGION_D_GENERATOR_WEIGHT_CAVERN)
                .build();
    }
}
