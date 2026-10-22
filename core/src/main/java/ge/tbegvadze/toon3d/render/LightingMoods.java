package ge.tbegvadze.toon3d.render;

import ge.tbegvadze.toon3d.route.RegionAmbience;
import ge.tbegvadze.toon3d.route.RegionPlan;
import ge.tbegvadze.toon3d.route.RegionSpec;
import ge.tbegvadze.toon3d.route.RouteRegistries;
import ge.tbegvadze.toon3d.util.ArtDirection;
import ge.tbegvadze.toon3d.util.TilesetConstants;

/**
 * The one place the five {@link LightingMood}s are registered (one {@code register()} row each, numbers
 * from {@link ArtDirection}) plus the R11 fail-loud cross-checks. Same pattern as
 * {@code TilesetGfxBootstrap}: bootstrap is idempotent and throws {@link IllegalStateException} naming the
 * offending id if a region names an unregistered mood or a mood names an unregistered material.
 */
public final class LightingMoods {

    private static final LightingMoodRegistry REGISTRY = new LightingMoodRegistry();
    private static boolean bootstrapped = false;

    private LightingMoods() {}

    /** The shared mood registry. */
    public static LightingMoodRegistry registry() {
        return REGISTRY;
    }

    /**
     * Registers the five moods and runs the cross-checks. Requires {@code RouteRegistries.bootstrap()} and
     * {@code SurfaceMaterials.bootstrap()} to have run first (both are invoked here, idempotently).
     */
    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        RouteRegistries.bootstrap();
        SurfaceMaterials.bootstrap();

        LightingMood coolSteel = LightingMood.builder(ArtDirection.MOOD_ID_COOL_STEEL)
                .nearTint(ArtDirection.MOOD_COOL_STEEL_NEAR_R, ArtDirection.MOOD_COOL_STEEL_NEAR_G, ArtDirection.MOOD_COOL_STEEL_NEAR_B)
                .farTint(ArtDirection.MOOD_COOL_STEEL_FAR_R, ArtDirection.MOOD_COOL_STEEL_FAR_G, ArtDirection.MOOD_COOL_STEEL_FAR_B)
                .fog(ArtDirection.MOOD_COOL_STEEL_FOG_START_TILES, ArtDirection.MOOD_COOL_STEEL_FOG_END_TILES)
                .horizon(ArtDirection.MOOD_COOL_STEEL_HORIZON_FLOOR_PACKED, ArtDirection.MOOD_COOL_STEEL_HORIZON_CEILING_PACKED)
                .vignetteAlpha(ArtDirection.MOOD_COOL_STEEL_VIGNETTE_ALPHA)
                .materials(SurfaceMaterials.floorMaterialId(TilesetConstants.WALL_SET_OUTER_FACILITY),
                           SurfaceMaterials.ceilingMaterialId(TilesetConstants.WALL_SET_OUTER_FACILITY))
                .build();
        REGISTRY.register(coolSteel);
        REGISTRY.register(LightingMood.builder(ArtDirection.MOOD_ID_CLINICAL_WHITE)
                .nearTint(ArtDirection.MOOD_CLINICAL_WHITE_NEAR_R, ArtDirection.MOOD_CLINICAL_WHITE_NEAR_G, ArtDirection.MOOD_CLINICAL_WHITE_NEAR_B)
                .farTint(ArtDirection.MOOD_CLINICAL_WHITE_FAR_R, ArtDirection.MOOD_CLINICAL_WHITE_FAR_G, ArtDirection.MOOD_CLINICAL_WHITE_FAR_B)
                .fog(ArtDirection.MOOD_CLINICAL_WHITE_FOG_START_TILES, ArtDirection.MOOD_CLINICAL_WHITE_FOG_END_TILES)
                .horizon(ArtDirection.MOOD_CLINICAL_WHITE_HORIZON_FLOOR_PACKED, ArtDirection.MOOD_CLINICAL_WHITE_HORIZON_CEILING_PACKED)
                .vignetteAlpha(ArtDirection.MOOD_CLINICAL_WHITE_VIGNETTE_ALPHA)
                .materials(SurfaceMaterials.floorMaterialId(TilesetConstants.WALL_SET_RESEARCH_WING),
                           SurfaceMaterials.ceilingMaterialId(TilesetConstants.WALL_SET_RESEARCH_WING))
                .build());
        REGISTRY.register(LightingMood.builder(ArtDirection.MOOD_ID_AMBER_HOT)
                .nearTint(ArtDirection.MOOD_AMBER_HOT_NEAR_R, ArtDirection.MOOD_AMBER_HOT_NEAR_G, ArtDirection.MOOD_AMBER_HOT_NEAR_B)
                .farTint(ArtDirection.MOOD_AMBER_HOT_FAR_R, ArtDirection.MOOD_AMBER_HOT_FAR_G, ArtDirection.MOOD_AMBER_HOT_FAR_B)
                .fog(ArtDirection.MOOD_AMBER_HOT_FOG_START_TILES, ArtDirection.MOOD_AMBER_HOT_FOG_END_TILES)
                .horizon(ArtDirection.MOOD_AMBER_HOT_HORIZON_FLOOR_PACKED, ArtDirection.MOOD_AMBER_HOT_HORIZON_CEILING_PACKED)
                .vignetteAlpha(ArtDirection.MOOD_AMBER_HOT_VIGNETTE_ALPHA)
                .materials(SurfaceMaterials.floorMaterialId(TilesetConstants.WALL_SET_REACTOR_DEPTHS),
                           SurfaceMaterials.ceilingMaterialId(TilesetConstants.WALL_SET_REACTOR_DEPTHS))
                .build());
        REGISTRY.register(LightingMood.builder(ArtDirection.MOOD_ID_CORRUPTED_VIOLET)
                .nearTint(ArtDirection.MOOD_CORRUPTED_VIOLET_NEAR_R, ArtDirection.MOOD_CORRUPTED_VIOLET_NEAR_G, ArtDirection.MOOD_CORRUPTED_VIOLET_NEAR_B)
                .farTint(ArtDirection.MOOD_CORRUPTED_VIOLET_FAR_R, ArtDirection.MOOD_CORRUPTED_VIOLET_FAR_G, ArtDirection.MOOD_CORRUPTED_VIOLET_FAR_B)
                .fog(ArtDirection.MOOD_CORRUPTED_VIOLET_FOG_START_TILES, ArtDirection.MOOD_CORRUPTED_VIOLET_FOG_END_TILES)
                .horizon(ArtDirection.MOOD_CORRUPTED_VIOLET_HORIZON_FLOOR_PACKED, ArtDirection.MOOD_CORRUPTED_VIOLET_HORIZON_CEILING_PACKED)
                .vignetteAlpha(ArtDirection.MOOD_CORRUPTED_VIOLET_VIGNETTE_ALPHA)
                .materials(SurfaceMaterials.floorMaterialId(TilesetConstants.WALL_SET_THE_BREACH),
                           SurfaceMaterials.ceilingMaterialId(TilesetConstants.WALL_SET_THE_BREACH))
                .build());
        REGISTRY.register(LightingMood.builder(ArtDirection.MOOD_ID_CORE_WHITE_VIOLET)
                .nearTint(ArtDirection.MOOD_CORE_WHITE_VIOLET_NEAR_R, ArtDirection.MOOD_CORE_WHITE_VIOLET_NEAR_G, ArtDirection.MOOD_CORE_WHITE_VIOLET_NEAR_B)
                .farTint(ArtDirection.MOOD_CORE_WHITE_VIOLET_FAR_R, ArtDirection.MOOD_CORE_WHITE_VIOLET_FAR_G, ArtDirection.MOOD_CORE_WHITE_VIOLET_FAR_B)
                .fog(ArtDirection.MOOD_CORE_WHITE_VIOLET_FOG_START_TILES, ArtDirection.MOOD_CORE_WHITE_VIOLET_FOG_END_TILES)
                .horizon(ArtDirection.MOOD_CORE_WHITE_VIOLET_HORIZON_FLOOR_PACKED, ArtDirection.MOOD_CORE_WHITE_VIOLET_HORIZON_CEILING_PACKED)
                .vignetteAlpha(ArtDirection.MOOD_CORE_WHITE_VIOLET_VIGNETTE_ALPHA)
                .materials(SurfaceMaterials.floorMaterialId(TilesetConstants.WALL_SET_THE_CORE),
                           SurfaceMaterials.ceilingMaterialId(TilesetConstants.WALL_SET_THE_CORE))
                .build());
        REGISTRY.setFallback(coolSteel);

        assertRegionsAndMaterialsResolve(REGISTRY, SurfaceMaterials.registry());
        bootstrapped = true;
    }

    /**
     * R11 fail-loud: every region's lightingMoodId must be a registered mood, and every mood's two material
     * ids must be registered materials.
     */
    static void assertRegionsAndMaterialsResolve(LightingMoodRegistry moods, SurfaceMaterialRegistry materials) {
        for (RegionSpec region : RegionPlan.defaultPlan().regions()) {
            RegionAmbience ambience = RouteRegistries.regionAmbience().get(region.themeId());
            if (ambience == null) {
                continue;
            }
            if (!moods.has(ambience.lightingMoodId())) {
                throw new IllegalStateException("Region '" + region.themeId() + "' names unregistered lighting mood '"
                        + ambience.lightingMoodId() + "'");
            }
        }
        for (String moodId : moods.ids()) {
            LightingMood mood = moods.get(moodId);
            if (!materials.has(mood.floorMaterialId())) {
                throw new IllegalStateException("Lighting mood '" + moodId + "' names unregistered floor material '"
                        + mood.floorMaterialId() + "'");
            }
            if (!materials.has(mood.ceilingMaterialId())) {
                throw new IllegalStateException("Lighting mood '" + moodId + "' names unregistered ceiling material '"
                        + mood.ceilingMaterialId() + "'");
            }
        }
    }
}
