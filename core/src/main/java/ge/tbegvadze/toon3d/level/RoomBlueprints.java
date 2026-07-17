package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.tileset.RoomSymbolDemand;
import ge.tbegvadze.toon3d.tileset.TileCategory;
import ge.tbegvadze.toon3d.util.LevelGenConstants;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The one place the {@link RoomBlueprintRegistry} is populated (symbol/sprite-reuse order-5) — the ROOM
 * twin of {@code route/RouteRegistries.bootstrap()} and {@code tileset/TilesetRegistries.bootstrap()}.
 * {@link #bootstrap()} registers every v1 room: the three GENERIC filler rooms (ENTRANCE / STANDARD /
 * LARGE) and the nine SIGNATURE rooms, each wrapping an existing {@code LevelGenerator.RoomType}. After
 * this order a room is a registered object, not a private enum constant referenced by ~4 hardcoded
 * switches; adding a room becomes ONE {@link RoomBlueprint} + one {@code register()} line here.
 *
 * <p>{@link #bootstrap()} is idempotent (safe across a death→new-run rebuild). It is invoked once from
 * {@code World.create()} next to {@code TilesetRegistries.bootstrap()}. Lives in {@code …toon3d.level}
 * (option (a) in the order-5 design): {@code build()} writes into the grid through the generator's
 * package-private per-room helpers, so the blueprints sit next to {@code LevelGenerator}; the
 * {@code tileset} package stays purely about categories/sprites/budget/allocator.
 *
 * <p>ORDER-5 SCOPE: registration + self-description only. {@code LevelGenerator.generate()} does NOT
 * yet iterate this registry (it still selects with its internal {@code RoomType} tag); order-8 flips
 * the generator to pick from {@link RoomBlueprintRegistry#selectable} and call {@link RoomBlueprint#build}.
 * Headless — no LibGDX.
 */
public final class RoomBlueprints {

    // Stable blueprint ids (registry keys + reproducibility contract — never rename once shipped).
    public static final String ID_ENTRANCE            = "entrance";
    public static final String ID_STANDARD            = "standard";
    public static final String ID_LARGE               = "large";
    public static final String ID_SERVER_ROOM         = "server_room";
    public static final String ID_MEDICAL_BAY         = "medical_bay";
    public static final String ID_ARMORY              = "armory";
    public static final String ID_CRYO_CHAMBER        = "cryo_chamber";
    public static final String ID_POWER_PLANT         = "power_plant";
    public static final String ID_COMMAND_CENTER      = "command_center";
    public static final String ID_CONTAINMENT_BLOCK   = "containment_block";
    public static final String ID_RESEARCH_LAB        = "research_lab";
    public static final String ID_STELLAR_OBSERVATORY = "stellar_observatory";
    // order-8 REQUIREMENT PROOF 1 — the brand-new GENERIC salvage bay.
    public static final String ID_SALVAGE_BAY         = "salvage_bay";

    private static final RoomBlueprintRegistry ROOMS = new RoomBlueprintRegistry();
    private static boolean bootstrapped = false;

    private RoomBlueprints() {}

    /** The shared room-blueprint registry the generator selects from. */
    public static RoomBlueprintRegistry rooms() {
        return ROOMS;
    }

    /** Whether {@link #bootstrap()} has already run. */
    public static boolean isBootstrapped() {
        return bootstrapped;
    }

    /** Registers all v1 blueprints into the shared registry. Idempotent. */
    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        registerAll(ROOMS);
        bootstrapped = true;
    }

    /**
     * Registers the full v1 room catalog into the given registry. Package-private so tests can populate
     * a fresh registry without touching the shared singleton.
     */
    static void registerAll(RoomBlueprintRegistry registry) {
        // --- GENERIC filler rooms (allocator-driven look; empty demand) ---

        // ENTRANCE: player spawn room; room 0 by position (never rolled). No themed architecture — its
        // interior stays fully lit floor and the generator stamps the player start separately.
        registry.register(new LambdaRoomBlueprint(ID_ENTRANCE, RoomKind.GENERIC,
                RoomSymbolDemand.of(emptyDemand()),
                context -> true,
                context -> LevelGenConstants.LEVEL_GEN_ROOM_ENTRANCE_SELECTION_WEIGHT,
                context -> { /* no architecture */ }));

        // STANDARD: baseline lab room; residual fallback for any candidate no specialty claims.
        registry.register(new LambdaRoomBlueprint(ID_STANDARD, RoomKind.GENERIC,
                RoomSymbolDemand.of(emptyDemand()),
                context -> true,
                context -> LevelGenConstants.LEVEL_GEN_ROOM_STANDARD_SELECTION_WEIGHT,
                context -> {
                    context.assignFloorLighting();
                    context.placeStandardColumns();
                    context.placeStandardProps();
                }));

        // LARGE: landmark arena; needs LARGE_MIN_DIM in both axes, hard-capped per level.
        registry.register(new LambdaRoomBlueprint(ID_LARGE, RoomKind.GENERIC,
                RoomSymbolDemand.of(emptyDemand()),
                context -> context.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_LARGE_MIN_DIM
                        && context.interiorHeight() >= LevelGenConstants.LEVEL_GEN_LARGE_MIN_DIM
                        && context.alreadyPlacedOfThisKind() < LevelGenConstants.LEVEL_GEN_LARGE_ROOM_MAX_PER_LEVEL,
                context -> LevelGenConstants.LEVEL_GEN_LARGE_ROOM_CHANCE,
                context -> {
                    context.assignFloorLighting();
                    context.placeLargeRoomColumns();
                    context.placeLargeRoomProps();
                }));

        // SALVAGE_BAY (order-8 REQUIREMENT PROOF 1): a brand-new GENERIC room with a DISTINCT central
        // scrap-heap architecture (not a reskin). It reserves NO specific sprite — its demand is
        // FLEXIBLE-category slot counts only, so it composes from whatever flexible symbols are free on
        // the level and its look is fully allocator-driven. Eligible on ordinary floors at a modest size,
        // capped so it stays a flavour room.
        registry.register(new LambdaRoomBlueprint(ID_SALVAGE_BAY, RoomKind.GENERIC,
                RoomSymbolDemand.of(emptyDemand(), salvageBayCategoryCounts()),
                context -> context.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_SALVAGE_BAY_MIN_WIDTH
                        && context.interiorHeight() >= LevelGenConstants.LEVEL_GEN_SALVAGE_BAY_MIN_HEIGHT
                        && context.alreadyPlacedOfThisKind() < LevelGenConstants.LEVEL_GEN_SALVAGE_BAY_MAX,
                context -> LevelGenConstants.LEVEL_GEN_ROOM_SALVAGE_BAY_SELECTION_WEIGHT,
                context -> context.placeSalvageBay()));

        // --- SIGNATURE rooms (fixed identity; declare the sprites they claim) ---

        // SERVER_ROOM: data vault — terminal walls 't', rack rows of terminals/lockers, oil + a barrel.
        registry.register(new LambdaRoomBlueprint(ID_SERVER_ROOM, RoomKind.SIGNATURE,
                RoomSymbolDemand.of(demand(
                        't', "wall_terminal",
                        'T', "prop_terminal",
                        'L', "prop_locker",
                        'O', "decal_oil",
                        'g', "prop_radioactive_barrel")),
                context -> context.alreadyPlacedOfThisKind() < LevelGenConstants.LEVEL_GEN_SERVER_ROOM_MAX_PER_LEVEL,
                context -> LevelGenConstants.LEVEL_GEN_SERVER_ROOM_CHANCE,
                context -> {
                    context.assignFloorLighting();
                    context.themeServerRoomWalls();
                    context.placeServerRoomProps();
                }));

        // MEDICAL_BAY: guaranteed once per level; medical 'M' walls + bio 'Q' accents, clinical props.
        registry.register(new LambdaRoomBlueprint(ID_MEDICAL_BAY, RoomKind.SIGNATURE,
                RoomSymbolDemand.of(demand(
                        'M', "wall_medical",
                        'Q', "wall_bio",
                        '#', "prop_camera",
                        'C', "prop_crate",
                        'T', "prop_terminal",
                        '.', "decal_blood",
                        'O', "decal_oil",
                        'm', "decal_corpse")),
                context -> context.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_MEDICAL_BAY_MIN_WIDTH
                        && context.interiorHeight() >= LevelGenConstants.LEVEL_GEN_MEDICAL_BAY_MIN_HEIGHT
                        && context.alreadyPlacedOfThisKind() < 1,
                context -> LevelGenConstants.LEVEL_GEN_ROOM_MEDICAL_BAY_SELECTION_WEIGHT,
                context -> {
                    context.assignFloorLighting();
                    context.themeNewRoomWalls();
                    context.placeMedicalBayProps();
                }));

        // ARMORY: last-stand room; blast-scarred 'X' walls, weapon racks, cameras, aftermath decals.
        registry.register(new LambdaRoomBlueprint(ID_ARMORY, RoomKind.SIGNATURE,
                RoomSymbolDemand.of(demand(
                        'X', "wall_blast",
                        '=', "prop_weapon_rack",
                        '#', "prop_camera",
                        '.', "decal_blood",
                        'm', "decal_corpse")),
                context -> context.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_ARMORY_MIN_WIDTH
                        && context.interiorHeight() >= LevelGenConstants.LEVEL_GEN_ARMORY_MIN_HEIGHT
                        && context.alreadyPlacedOfThisKind() < 1,
                context -> LevelGenConstants.LEVEL_GEN_ARMORY_CHANCE,
                context -> {
                    context.assignFloorLighting();
                    context.themeNewRoomWalls();
                    context.placeArmoryProps();
                }));

        // CRYO_CHAMBER: cryogenic bay; frost 'Z' walls + glass 'N' accents, bio-pod rows.
        registry.register(new LambdaRoomBlueprint(ID_CRYO_CHAMBER, RoomKind.SIGNATURE,
                RoomSymbolDemand.of(demand(
                        'Z', "wall_cryo",
                        'N', "wall_glass",
                        '&', "prop_biopod",
                        '#', "prop_camera",
                        'O', "decal_oil",
                        'C', "prop_crate")),
                context -> context.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_CRYO_MIN_WIDTH
                        && context.interiorHeight() >= LevelGenConstants.LEVEL_GEN_CRYO_MIN_HEIGHT
                        && context.alreadyPlacedOfThisKind() < LevelGenConstants.LEVEL_GEN_CRYO_MAX,
                context -> LevelGenConstants.LEVEL_GEN_CRYO_CHANCE,
                context -> {
                    context.assignFloorLighting();
                    context.themeNewRoomWalls();
                    context.placeCryoChamberProps();
                }));

        // POWER_PLANT: reactor core (LARGE-class); radiation 'U' walls + emergency 'S' strips, generators.
        registry.register(new LambdaRoomBlueprint(ID_POWER_PLANT, RoomKind.SIGNATURE,
                RoomSymbolDemand.of(demand(
                        'U', "wall_radiation",
                        'S', "wall_emergency",
                        '%', "prop_generator",
                        '#', "prop_camera",
                        'g', "prop_radioactive_barrel",
                        'O', "decal_oil")),
                context -> context.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_LARGE_MIN_DIM
                        && context.interiorHeight() >= LevelGenConstants.LEVEL_GEN_LARGE_MIN_DIM
                        && context.alreadyPlacedOfThisKind() < 1,
                context -> LevelGenConstants.LEVEL_GEN_POWERPLANT_CHANCE,
                context -> {
                    context.assignFloorLighting();
                    context.themeNewRoomWalls();
                    context.placePowerPlantProps();
                }));

        // COMMAND_CENTER: control room (LARGE-class); glass 'N' walls + emergency 'S' strips, terminals.
        registry.register(new LambdaRoomBlueprint(ID_COMMAND_CENTER, RoomKind.SIGNATURE,
                RoomSymbolDemand.of(demand(
                        'N', "wall_glass",
                        'S', "wall_emergency",
                        'T', "prop_terminal",
                        '#', "prop_camera",
                        '=', "prop_weapon_rack",
                        '.', "decal_blood",
                        'm', "decal_corpse")),
                context -> context.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_COMMAND_MIN_WIDTH
                        && context.interiorHeight() >= LevelGenConstants.LEVEL_GEN_COMMAND_MIN_HEIGHT
                        && context.alreadyPlacedOfThisKind() < 1,
                context -> LevelGenConstants.LEVEL_GEN_COMMAND_CHANCE,
                context -> {
                    context.assignFloorLighting();
                    context.themeNewRoomWalls();
                    context.placeCommandCenterProps();
                }));

        // CONTAINMENT_BLOCK: cell block; glass 'N' cell fronts + quarantine 'Q' walls, bio-pods, dread.
        registry.register(new LambdaRoomBlueprint(ID_CONTAINMENT_BLOCK, RoomKind.SIGNATURE,
                RoomSymbolDemand.of(demand(
                        'N', "wall_glass",
                        'Q', "wall_bio",
                        '&', "prop_biopod",
                        'C', "prop_crate",
                        '#', "prop_camera",
                        '.', "decal_blood",
                        'O', "decal_oil",
                        'm', "decal_corpse")),
                context -> context.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_CONTAINMENT_MIN_WIDTH
                        && context.interiorHeight() >= LevelGenConstants.LEVEL_GEN_CONTAINMENT_MIN_HEIGHT
                        && context.alreadyPlacedOfThisKind() < LevelGenConstants.LEVEL_GEN_CONTAINMENT_MAX,
                context -> LevelGenConstants.LEVEL_GEN_CONTAINMENT_CHANCE,
                context -> {
                    context.assignFloorLighting();
                    context.themeNewRoomWalls();
                    context.placeContainmentBlockProps();
                }));

        // RESEARCH_LAB: sci-fi set-piece; holo-data 'D' + force-field 'F' walls, specimen tanks, AI core.
        registry.register(new LambdaRoomBlueprint(ID_RESEARCH_LAB, RoomKind.SIGNATURE,
                RoomSymbolDemand.of(demand(
                        'D', "wall_holo_data",
                        'F', "wall_force_field",
                        'I', "prop_specimen_tank",
                        'W', "prop_holo_workstation",
                        'J', "prop_ai_core",
                        'e', "decal_energy_scorch")),
                context -> context.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_RESEARCH_LAB_MIN_WIDTH
                        && context.interiorHeight() >= LevelGenConstants.LEVEL_GEN_RESEARCH_LAB_MIN_HEIGHT
                        && context.alreadyPlacedOfThisKind() < 1,
                context -> LevelGenConstants.LEVEL_GEN_RESEARCH_LAB_CHANCE,
                context -> {
                    context.assignFloorLighting();
                    context.themeNewRoomWalls();
                    context.placeResearchLabProps();
                }));

        // STELLAR_OBSERVATORY: rare rotunda landmark; room-CONTAINED observatory art (hull/viewport/
        // magrail walls, gravity-well core, pylons, holo-table, cargo crates, deck/debris/seam decals).
        // Its demand is exactly the "observatory" theme-tagged sprites, so order-4 binds them ONLY when
        // this room is placed — satisfying the containment rule (order-4 R2).
        registry.register(new LambdaRoomBlueprint(ID_STELLAR_OBSERVATORY, RoomKind.SIGNATURE,
                RoomSymbolDemand.of(demand(
                        '`',  "wall_stellar_hull_plate",
                        '"',  "wall_stellar_viewport",
                        '\'', "wall_stellar_magrail",
                        ';',  "prop_gravity_well_core",
                        '|',  "prop_docking_strut_pylon",
                        '<',  "prop_astro_nav_table",
                        '\\', "prop_floating_cargo_crate",
                        '?',  "decal_magnetic_deck",
                        ']',  "decal_zerog_debris",
                        '-',  "decal_starlight_seam")),
                context -> context.dungeonDepth() >= LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_MIN_DEPTH
                        && context.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_MIN_INTERIOR
                        && context.interiorHeight() >= LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_MIN_INTERIOR
                        && Math.max(context.interiorWidth(), context.interiorHeight())
                                <= Math.min(context.interiorWidth(), context.interiorHeight())
                                        * LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_MAX_ASPECT
                        && context.alreadyPlacedOfThisKind() < 1,
                context -> LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_CHANCE,
                context -> {
                    context.assignFloorLighting();
                    context.placeStellarObservatoryProps();
                }));
    }

    // Builds an insertion-ordered symbol → sprite-id map from alternating (char, id) pairs.
    private static Map<Character, String> demand(Object... symbolThenSpriteId) {
        Map<Character, String> required = new LinkedHashMap<>();
        for (int index = 0; index < symbolThenSpriteId.length; index += 2) {
            required.put((Character) symbolThenSpriteId[index], (String) symbolThenSpriteId[index + 1]);
        }
        return required;
    }

    private static Map<Character, String> emptyDemand() {
        return new LinkedHashMap<>();
    }

    // The salvage bay's demand is FLEXIBLE-category slot counts only (no pinned sprite): it wants a
    // couple of accent walls, a couple of scrap props, and a decal, all allocator-chosen from free
    // symbols. order-4's allocator carries these counts; order-8's generic-variety pass consumes them.
    private static Map<TileCategory, Integer> salvageBayCategoryCounts() {
        Map<TileCategory, Integer> counts = new EnumMap<>(TileCategory.class);
        counts.put(TileCategory.WALL, 2);
        counts.put(TileCategory.SOLID_PROP, 2);
        counts.put(TileCategory.FLOOR_DECAL, 1);
        return counts;
    }

    // --- Concrete blueprint backed by the four functional pieces (kept package-private) ---

    @FunctionalInterface
    interface Eligibility {
        boolean test(RoomContext context);
    }

    @FunctionalInterface
    interface SelectionWeight {
        float of(RoomContext context);
    }

    @FunctionalInterface
    interface BuildStep {
        void build(RoomBuildContext context);
    }

    private static final class LambdaRoomBlueprint implements RoomBlueprint {
        private final String id;
        private final RoomKind kind;
        private final RoomSymbolDemand demand;
        private final Eligibility eligibility;
        private final SelectionWeight weight;
        private final BuildStep buildStep;

        LambdaRoomBlueprint(String id, RoomKind kind, RoomSymbolDemand demand,
                            Eligibility eligibility, SelectionWeight weight, BuildStep buildStep) {
            this.id          = id;
            this.kind        = kind;
            this.demand      = demand;
            this.eligibility = eligibility;
            this.weight      = weight;
            this.buildStep   = buildStep;
        }

        @Override public String id() { return id; }
        @Override public RoomKind kind() { return kind; }
        @Override public boolean eligible(RoomContext context) { return eligibility.test(context); }
        @Override public float selectionWeight(RoomContext context) { return weight.of(context); }
        @Override public RoomSymbolDemand symbolDemand() { return demand; }
        @Override public void build(RoomBuildContext context) { buildStep.build(context); }
    }
}
