package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.RouteMapConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The data-driven blueprint of the descent's act structure: an ordered list of {@link RegionSpec}
 * bands the {@link RouteMapGenerator} consumes. Keeping region count and shape as DATA (not code)
 * means new acts are added as rows in {@link #defaultPlan()}, never as new branches in the generator.
 *
 * <p>The plan is FINITE (balance-overhaul order 6, F1): {@link #defaultPlan()} is the five fixed acts
 * A..E covering depths 1..{@code BalanceConfig.RUN_FINAL_DEPTH}, and the constructor refuses any plan
 * that reaches deeper — there is no code path that appends a band after the run-final boss.
 *
 * <p>Pure data — no LibGDX, headless-testable.
 */
public final class RegionPlan {

    private final List<RegionSpec> regions;

    public RegionPlan(List<RegionSpec> regions) {
        if (regions.isEmpty()) {
            throw new IllegalArgumentException("A RegionPlan needs at least one region");
        }
        int finalDepth = regions.get(regions.size() - 1).lastDepth();
        if (finalDepth > BalanceConfig.RUN_FINAL_DEPTH) {
            throw new IllegalArgumentException("A RegionPlan may not reach past the run-final depth "
                    + BalanceConfig.RUN_FINAL_DEPTH + " (got " + finalDepth + ")");
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
     * The default (and only) descent: five named acts — OUTER FACILITY, RESEARCH WING, REACTOR DEPTHS,
     * THE BREACH, THE CORE. Every band is {@code REGION_BAND_SIZE} deep and ends on a boss floor; the
     * last one ends on the run-final boss at {@code BalanceConfig.RUN_FINAL_DEPTH}. Bias tables come from {@link RouteMapConstants} so no distribution number is
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

        RegionSpec theBreach = RegionSpec.builder(3)
                .displayName(RouteMapConstants.REGION_D_NAME)
                .themeId(RouteMapConstants.REGION_D_THEME)
                .depthSpan(3 * band + 1, 4 * band)
                .nodeTypeMultiplier(RouteNodeType.ELITE, RouteMapConstants.REGION_D_MULTIPLIER_ELITE)
                .nodeTypeMultiplier(RouteNodeType.REST,  RouteMapConstants.REGION_D_MULTIPLIER_REST)
                .nodeTypeMultiplier(RouteNodeType.SHOP,  RouteMapConstants.REGION_D_MULTIPLIER_SHOP)
                .generatorWeight(GeneratorId.CAVERN,     RouteMapConstants.REGION_D_GENERATOR_WEIGHT_CAVERN)
                .build();

        RegionSpec theCore = RegionSpec.builder(4)
                .displayName(RouteMapConstants.REGION_E_NAME)
                .themeId(RouteMapConstants.REGION_E_THEME)
                .depthSpan(4 * band + 1, 5 * band)
                .nodeTypeMultiplier(RouteNodeType.ELITE, RouteMapConstants.REGION_E_MULTIPLIER_ELITE)
                .nodeTypeMultiplier(RouteNodeType.REST,  RouteMapConstants.REGION_E_MULTIPLIER_REST)
                .nodeTypeMultiplier(RouteNodeType.SHOP,  RouteMapConstants.REGION_E_MULTIPLIER_SHOP)
                .generatorWeight(GeneratorId.ROOMS_MST,  RouteMapConstants.REGION_E_GENERATOR_WEIGHT_ROOMS)
                .build();

        List<RegionSpec> plan = new ArrayList<>();
        plan.add(outerFacility);
        plan.add(researchWing);
        plan.add(reactorDepths);
        plan.add(theBreach);
        plan.add(theCore);
        return new RegionPlan(plan);
    }
}
