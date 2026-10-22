package ge.tbegvadze.toon3d.tileset;

import ge.tbegvadze.toon3d.util.RenderConstants;

/**
 * The one place the tileset registries are populated — the twin of {@code route/RouteRegistries}.
 * {@link #bootstrap()} registers every solid prop and floor decal as an {@link EnvironmentSpriteDefinition},
 * giving the game a complete, enumerable inventory of its environment art. Walls, doors and columns are NOT
 * in this catalog any more: every floor builds them from its region wall set (generated-wall-art order 2,
 * {@link RegionWallSets}; docs/environment-tileset-system.txt section 15). Nothing reads the registry yet (order-1 is pure data + registration); it exists so
 * later orders — the per-level allocator and the renderers — have a stable catalog to select from
 * instead of the scattered {@code WallRenderer} table + {@code PropRenderer} switch.
 *
 * <p>{@link #bootstrap()} is idempotent — the second call is a no-op — so a repeated
 * {@code World.create()} (e.g. after a death-screen restart) is safe. Adding a new prop / decal sprite is ONE {@link EnvironmentSpriteDefinition} + one {@code register()} line in the matching
 * helper below; nothing else in this order references a specific char.
 *
 * <p>The {@code "observatory"} theme tag marks room-contained STELLAR_OBSERVATORY art. Today those
 * symbols are "contained to that room type; do not reuse elsewhere" ({@code docs/tile-symbols.txt});
 * they are registered here so the catalog is complete, and the tag lets a later allocator pull them
 * only for their room — preserving the containment rule. order-1 changes no behaviour.
 */
public final class TilesetRegistries {

    /** Theme tag on the room-contained STELLAR_OBSERVATORY art (props, decals). */
    public static final String THEME_TAG_OBSERVATORY = "observatory";

    /**
     * Sprite ids registered here that no LEGACY symbol maps to (reached only through the allocator's per-level
     * variety). Empty since generated-wall-art order 2 moved every wall and column out of this catalog — they
     * come from the floor's region wall set — and kept so the catalog-size invariants stay explicit.
     */
    public static final java.util.Set<String> VARIETY_ONLY_SPRITE_IDS = java.util.Collections.emptySet();

    private static final EnvironmentSpriteRegistry SPRITES = new EnvironmentSpriteRegistry();
    private static boolean bootstrapped = false;

    private TilesetRegistries() {}

    /** The shared environment-sprite registry (the art catalog) the rest of the game reads. */
    public static EnvironmentSpriteRegistry sprites() {
        return SPRITES;
    }

    /** Whether {@link #bootstrap()} has already run. */
    public static boolean isBootstrapped() {
        return bootstrapped;
    }

    /**
     * Registers all v1 sprite definitions into the shared registry. Idempotent: safe to call
     * repeatedly. Invoked once from the {@code World.create()} path, next to
     * {@code RouteRegistries.bootstrap()}.
     */
    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        registerAll(SPRITES);
        bootstrapped = true;
    }

    /**
     * Registers the full v1 inventory into the given registry. Exposed (package-private) so tests can
     * populate a fresh registry without touching the shared singleton.
     */
    static void registerAll(EnvironmentSpriteRegistry registry) {
        registerSolidProps(registry);
        registerDecals(registry);
    }

    /**
     * Every solid-prop sprite ({@code Level.isPropSolid}). All occlude (builder default true). Heights
     * are copied from the SAME {@code RenderConstants} the renderer reads — no re-hardcoded literals.
     * The last four are the STELLAR_OBSERVATORY room's props, tagged {@link #THEME_TAG_OBSERVATORY}.
     */
    static void registerSolidProps(EnvironmentSpriteRegistry registry) {
        registry.register(EnvironmentSpriteDefinition.builder("prop_radioactive_barrel", TileCategory.SOLID_PROP, "Radioactive Barrel")
                .heightMultiplier(RenderConstants.PROP_HEIGHT_RADIOACTIVE_BARREL).build());
        registry.register(EnvironmentSpriteDefinition.builder("prop_explosive_barrel", TileCategory.SOLID_PROP, "Explosive Barrel")
                .heightMultiplier(RenderConstants.PROP_HEIGHT_EXPLOSIVE_BARREL).build());
        registry.register(EnvironmentSpriteDefinition.builder("prop_terminal", TileCategory.SOLID_PROP, "Computer Terminal")
                .heightMultiplier(RenderConstants.PROP_HEIGHT_TERMINAL).build());
        registry.register(EnvironmentSpriteDefinition.builder("prop_locker", TileCategory.SOLID_PROP, "Locker")
                .heightMultiplier(RenderConstants.PROP_HEIGHT_LOCKER).build());
        registry.register(EnvironmentSpriteDefinition.builder("prop_crate", TileCategory.SOLID_PROP, "Crate")
                .heightMultiplier(RenderConstants.PROP_HEIGHT_CRATE).build());
        registry.register(EnvironmentSpriteDefinition.builder("prop_camera", TileCategory.SOLID_PROP, "Security Camera")
                .heightMultiplier(RenderConstants.PROP_CAMERA_HEIGHT).build());
        registry.register(EnvironmentSpriteDefinition.builder("prop_generator", TileCategory.SOLID_PROP, "Generator")
                .heightMultiplier(RenderConstants.PROP_GENERATOR_HEIGHT).build());
        registry.register(EnvironmentSpriteDefinition.builder("prop_biopod", TileCategory.SOLID_PROP, "Bio-pod")
                .heightMultiplier(RenderConstants.PROP_BIOPOD_HEIGHT).build());
        registry.register(EnvironmentSpriteDefinition.builder("prop_weapon_rack", TileCategory.SOLID_PROP, "Weapon Rack")
                .heightMultiplier(RenderConstants.PROP_RACK_HEIGHT).build());
        registry.register(EnvironmentSpriteDefinition.builder("prop_vendor", TileCategory.SOLID_PROP, "Special Equipment")
                .heightMultiplier(RenderConstants.PROP_VENDOR_HEIGHT).build());
        registry.register(EnvironmentSpriteDefinition.builder("prop_specimen_tank", TileCategory.SOLID_PROP, "Specimen Tank")
                .heightMultiplier(RenderConstants.PROP_HEIGHT_SPECIMEN_TANK).build());
        registry.register(EnvironmentSpriteDefinition.builder("prop_ai_core", TileCategory.SOLID_PROP, "AI Core Node")
                .heightMultiplier(RenderConstants.PROP_HEIGHT_AICORE_NODE).build());
        registry.register(EnvironmentSpriteDefinition.builder("prop_holo_workstation", TileCategory.SOLID_PROP, "Holo-workstation")
                .heightMultiplier(RenderConstants.PROP_HEIGHT_HOLO_WORKSTATION).build());

        registry.register(EnvironmentSpriteDefinition.builder("prop_gravity_well_core", TileCategory.SOLID_PROP, "Gravity Well Core")
                .heightMultiplier(RenderConstants.PROP_HEIGHT_GRAVITY_WELL_CORE).themeTag(THEME_TAG_OBSERVATORY).build());
        registry.register(EnvironmentSpriteDefinition.builder("prop_floating_cargo_crate", TileCategory.SOLID_PROP, "Floating Cargo Crate")
                .heightMultiplier(RenderConstants.PROP_HEIGHT_FLOATING_CARGO_CRATE).themeTag(THEME_TAG_OBSERVATORY).build());
        registry.register(EnvironmentSpriteDefinition.builder("prop_docking_strut_pylon", TileCategory.SOLID_PROP, "Docking Strut Pylon")
                .heightMultiplier(RenderConstants.PROP_HEIGHT_DOCKING_STRUT_PYLON).themeTag(THEME_TAG_OBSERVATORY).build());
        registry.register(EnvironmentSpriteDefinition.builder("prop_astro_nav_table", TileCategory.SOLID_PROP, "Astro-nav Holo Table")
                .heightMultiplier(RenderConstants.PROP_HEIGHT_ASTRO_NAV_TABLE).themeTag(THEME_TAG_OBSERVATORY).build());
    }

    /**
     * Every floor-decal sprite ({@code Level.isPropDecal}, minus the FIXED pickup / hazard / stairs
     * symbols which keep their dedicated handling). Flat stains do NOT occlude (occluder false). The
     * last three are the STELLAR_OBSERVATORY room's decals, tagged {@link #THEME_TAG_OBSERVATORY}.
     */
    static void registerDecals(EnvironmentSpriteRegistry registry) {
        registry.register(EnvironmentSpriteDefinition.builder("decal_corpse", TileCategory.FLOOR_DECAL, "Corpse")
                .heightMultiplier(RenderConstants.PROP_HEIGHT_CORPSE).occluder(false).build());
        registry.register(EnvironmentSpriteDefinition.builder("decal_blood_alt", TileCategory.FLOOR_DECAL, "Blood Stain (alt)")
                .heightMultiplier(RenderConstants.PROP_HEIGHT_BLOOD_ALT).occluder(false).build());
        registry.register(EnvironmentSpriteDefinition.builder("decal_blood", TileCategory.FLOOR_DECAL, "Blood Stain")
                .heightMultiplier(RenderConstants.PROP_HEIGHT_BLOOD).occluder(false).build());
        registry.register(EnvironmentSpriteDefinition.builder("decal_oil", TileCategory.FLOOR_DECAL, "Oil/Fluid Pool")
                .heightMultiplier(RenderConstants.PROP_HEIGHT_OIL).occluder(false).build());
        registry.register(EnvironmentSpriteDefinition.builder("decal_energy_scorch", TileCategory.FLOOR_DECAL, "Energy Scorch")
                .heightMultiplier(RenderConstants.PROP_HEIGHT_ENERGY_SCORCH).occluder(false).build());

        registry.register(EnvironmentSpriteDefinition.builder("decal_magnetic_deck", TileCategory.FLOOR_DECAL, "Magnetic Deck Plating")
                .heightMultiplier(RenderConstants.PROP_HEIGHT_MAGNETIC_DECK).occluder(false).themeTag(THEME_TAG_OBSERVATORY).build());
        registry.register(EnvironmentSpriteDefinition.builder("decal_zerog_debris", TileCategory.FLOOR_DECAL, "Zero-g Debris Scatter")
                .heightMultiplier(RenderConstants.PROP_HEIGHT_ZEROG_DEBRIS).occluder(false).themeTag(THEME_TAG_OBSERVATORY).build());
        registry.register(EnvironmentSpriteDefinition.builder("decal_starlight_seam", TileCategory.FLOOR_DECAL, "Starlight Seam Strip")
                .heightMultiplier(RenderConstants.PROP_HEIGHT_STARLIGHT_SEAM).occluder(false).themeTag(THEME_TAG_OBSERVATORY).build());
    }
}
