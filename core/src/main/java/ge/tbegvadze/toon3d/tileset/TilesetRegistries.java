package ge.tbegvadze.toon3d.tileset;

import ge.tbegvadze.toon3d.util.RenderConstants;

/**
 * The one place the tileset registries are populated — the twin of {@code route/RouteRegistries}.
 * {@link #bootstrap()} registers EVERY existing wall, column, solid prop, and floor decal as an
 * {@link EnvironmentSpriteDefinition}, giving the game a complete, enumerable inventory of today's
 * environment art. Nothing reads the registry yet (order-1 is pure data + registration); it exists so
 * later orders — the per-level allocator and the renderers — have a stable catalog to select from
 * instead of the scattered {@code WallRenderer} table + {@code PropRenderer} switch.
 *
 * <p>{@link #bootstrap()} is idempotent — the second call is a no-op — so a repeated
 * {@code World.create()} (e.g. after a death-screen restart) is safe. Adding a new wall/prop/column/
 * decal sprite is ONE {@link EnvironmentSpriteDefinition} + one {@code register()} line in the matching
 * helper below; nothing else in this order references a specific char.
 *
 * <p>The {@code "observatory"} theme tag marks room-contained STELLAR_OBSERVATORY art. Today those
 * symbols are "contained to that room type; do not reuse elsewhere" ({@code docs/tile-symbols.txt});
 * they are registered here so the catalog is complete, and the tag lets a later allocator pull them
 * only for their room — preserving the containment rule. order-1 changes no behaviour.
 */
public final class TilesetRegistries {

    /** Theme tag on the room-contained STELLAR_OBSERVATORY art (walls, props, decals). */
    public static final String THEME_TAG_OBSERVATORY = "observatory";

    /** Stable sprite id of the round column (the historic 1:1 COLUMN art the legacy palette binds 'P' to). */
    public static final String SPRITE_ID_COLUMN_ROUND  = "column_round";
    /** Stable sprite id of the order-8 square column (REQUIREMENT PROOF 2; variety-only, see below). */
    public static final String SPRITE_ID_COLUMN_SQUARE = "column_square";
    /**
     * Stable sprite id of the order-9 hex-plate accent wall — the acceptance-test wall sprite added by
     * RECIPE A alone (see docs/environment-tileset-system.txt §9). No legacy symbol maps to it, so it is
     * variety-only, reached only through the allocator's per-level wall variety.
     */
    public static final String SPRITE_ID_WALL_HEX_PLATE = "wall_hex_plate";
    /**
     * Stable sprite ids of the ALTERNATE BASE WALLS. The bulk wall symbol 'x' resolves per GENERATED
     * level to "wall_plain" OR one of these, seeded from the floor seed, so levels no longer share one
     * base wall. Each gives the floor its own FACILITY IDENTITY and is LIGHT, not gray: a warm
     * sandstone-block facility, a bright clinical-lab facility, and a sage/teal glazed ceramic-tile
     * facility. They carry no legacy symbol, so they are variety-only (see {@link #VARIETY_ONLY_SPRITE_IDS}).
     */
    public static final String SPRITE_ID_WALL_PLAIN_SANDSTONE = "wall_plain_sandstone";
    public static final String SPRITE_ID_WALL_PLAIN_CLEAN     = "wall_plain_clean";
    public static final String SPRITE_ID_WALL_PLAIN_TILED     = "wall_plain_tiled";

    /**
     * Sprite ids that exist in the catalog as PER-LEVEL VARIETY ALTERNATIVES which the historic 1:1
     * legacy palette never maps a symbol to. A sprite lands here whenever it is NEW art in an EXISTING
     * category with no free legacy symbol to bind it (the common case for content added via RECIPE A):
     * {@link #SPRITE_ID_COLUMN_SQUARE} (order-8, the second COLUMN sprite the flexible 'P' slot resolves
     * to), {@link #SPRITE_ID_WALL_HEX_PLATE} (order-9, an extra WALL accent), and the alternate base
     * walls {@link #SPRITE_ID_WALL_PLAIN_SANDSTONE} / {@link #SPRITE_ID_WALL_PLAIN_CLEAN} /
     * {@link #SPRITE_ID_WALL_PLAIN_TILED} (which the allocator picks for the plain 'x' slot per level).
     * They are fully registered (with texture generators) but only ever reached through the allocator, so
     * the legacy palette references the whole catalog MINUS this set. Unmodifiable.
     */
    public static final java.util.Set<String> VARIETY_ONLY_SPRITE_IDS =
            java.util.Collections.unmodifiableSet(
                    new java.util.LinkedHashSet<>(java.util.Arrays.asList(
                            SPRITE_ID_COLUMN_SQUARE, SPRITE_ID_WALL_HEX_PLATE,
                            SPRITE_ID_WALL_PLAIN_SANDSTONE, SPRITE_ID_WALL_PLAIN_CLEAN,
                            SPRITE_ID_WALL_PLAIN_TILED)));

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
        registerWalls(registry);
        registerColumns(registry);
        registerSolidProps(registry);
        registerDecals(registry);
    }

    /**
     * Every wall sprite ({@code Level.isWall}). WALL sprites are full-height stripes (heightMultiplier
     * 1.0, occluder true — both the builder defaults). The last three are the STELLAR_OBSERVATORY
     * room's walls, tagged {@link #THEME_TAG_OBSERVATORY}.
     */
    static void registerWalls(EnvironmentSpriteRegistry registry) {
        registry.register(EnvironmentSpriteDefinition.builder("wall_plain",       TileCategory.WALL, "Plain Wall").build());
        registry.register(EnvironmentSpriteDefinition.builder("wall_conduit",     TileCategory.WALL, "Conduit Wall").build());
        registry.register(EnvironmentSpriteDefinition.builder("wall_vent",        TileCategory.WALL, "Vent Wall").build());
        registry.register(EnvironmentSpriteDefinition.builder("wall_terminal",    TileCategory.WALL, "Terminal Wall").build());
        registry.register(EnvironmentSpriteDefinition.builder("wall_wires",       TileCategory.WALL, "Wires Wall").build());
        registry.register(EnvironmentSpriteDefinition.builder("wall_hazard",      TileCategory.WALL, "Hazard Wall").build());
        registry.register(EnvironmentSpriteDefinition.builder("wall_rust",        TileCategory.WALL, "Rust Wall").build());
        registry.register(EnvironmentSpriteDefinition.builder("wall_gore",        TileCategory.WALL, "Gore Wall").build());
        registry.register(EnvironmentSpriteDefinition.builder("wall_bulkhead",    TileCategory.WALL, "Bulkhead Wall").build());
        registry.register(EnvironmentSpriteDefinition.builder("wall_glass",       TileCategory.WALL, "Glass Wall").build());
        registry.register(EnvironmentSpriteDefinition.builder("wall_bio",         TileCategory.WALL, "Bio/Quarantine Wall").build());
        registry.register(EnvironmentSpriteDefinition.builder("wall_emergency",   TileCategory.WALL, "Emergency Strip Wall").build());
        registry.register(EnvironmentSpriteDefinition.builder("wall_medical",     TileCategory.WALL, "Medical Wall").build());
        registry.register(EnvironmentSpriteDefinition.builder("wall_cryo",        TileCategory.WALL, "Cryo Wall").build());
        registry.register(EnvironmentSpriteDefinition.builder("wall_radiation",   TileCategory.WALL, "Radiation Wall").build());
        registry.register(EnvironmentSpriteDefinition.builder("wall_blast",       TileCategory.WALL, "Blast Wall").build());
        registry.register(EnvironmentSpriteDefinition.builder("wall_holo_data",   TileCategory.WALL, "Holo-data Wall").build());
        registry.register(EnvironmentSpriteDefinition.builder("wall_force_field", TileCategory.WALL, "Force-field Wall").build());

        registry.register(EnvironmentSpriteDefinition.builder("wall_stellar_viewport",   TileCategory.WALL, "Viewport Dome Wall")
                .themeTag(THEME_TAG_OBSERVATORY).build());
        registry.register(EnvironmentSpriteDefinition.builder("wall_stellar_magrail",    TileCategory.WALL, "Magrail Conduit Wall")
                .themeTag(THEME_TAG_OBSERVATORY).build());
        registry.register(EnvironmentSpriteDefinition.builder("wall_stellar_hull_plate", TileCategory.WALL, "Hull Plate Wall")
                .themeTag(THEME_TAG_OBSERVATORY).build());

        // order-9 RECIPE A acceptance sprite — a hexagonal-plate accent wall. NEW art in the EXISTING
        // WALL category: no legacy symbol maps to it (VARIETY_ONLY_SPRITE_IDS), so it is registered LAST
        // — after the historic 1:1 walls the legacy palette zips against — and only ever reached through
        // the allocator's per-level wall variety. Added by REGISTRATION ALONE: this line + its texture
        // generator in WallRenderer.registerTextureGenerators, with ZERO edits to LevelGenerator,
        // WallRenderer draw logic, or Level. See docs/environment-tileset-system.txt §9 (RECIPE A).
        registry.register(EnvironmentSpriteDefinition.builder(SPRITE_ID_WALL_HEX_PLATE, TileCategory.WALL, "Hex Plate Wall").build());

        // Alternate BASE WALLS — extra plain "bulk wall" sprites the allocator can pick for the default
        // wall symbol 'x' per GENERATED level (the fix for "every level looks the same"). NEW art in the
        // EXISTING WALL category with no legacy symbol, so — like the hex-plate above — they are registered
        // LAST (after the historic 1:1 walls the legacy palette zips against) and only ever reached through
        // the allocator's base-wall roll. Each is one register() line here + its texture generator in
        // WallRenderer.registerTextureGenerators (startup id-set assertion enforces the pair). See
        // docs/environment-tileset-system.txt (BASE-WALL VARIETY).
        registry.register(EnvironmentSpriteDefinition.builder(SPRITE_ID_WALL_PLAIN_SANDSTONE, TileCategory.WALL, "Sandstone Block Wall").build());
        registry.register(EnvironmentSpriteDefinition.builder(SPRITE_ID_WALL_PLAIN_CLEAN,     TileCategory.WALL, "Clean Lab Panel Wall").build());
        registry.register(EnvironmentSpriteDefinition.builder(SPRITE_ID_WALL_PLAIN_TILED,     TileCategory.WALL, "Ceramic Tile Wall").build());
    }

    /**
     * Every column sprite ({@code Level.isColumn}). Today there is only the round column ('P'); the
     * square column arrives later as the extensibility proof.
     */
    static void registerColumns(EnvironmentSpriteRegistry registry) {
        registry.register(EnvironmentSpriteDefinition.builder(SPRITE_ID_COLUMN_ROUND, TileCategory.COLUMN, "Round Column").build());
        // order-8 REQUIREMENT PROOF 2 — the square column: a SECOND sprite in the EXISTING COLUMN
        // category (new ART, not a new symbol). The FIXED-meaning symbol 'P' now resolves per level to
        // EITHER "column_round" or "column_square" through the allocator (COLUMN is a flexible-sprite
        // slot; its MEANING stays "column"). Adding it here is the whole recipe on the catalog side —
        // its texture generator is registered in WallRenderer.registerTextureGenerators and the startup
        // id-set assertion enforces the pair.
        registry.register(EnvironmentSpriteDefinition.builder(SPRITE_ID_COLUMN_SQUARE, TileCategory.COLUMN, "Square Column").build());
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
