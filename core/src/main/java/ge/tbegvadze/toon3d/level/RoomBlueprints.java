package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.tileset.RoomSymbolDemand;
import ge.tbegvadze.toon3d.tileset.TileCategory;
import ge.tbegvadze.toon3d.tileset.WallGroup;
import ge.tbegvadze.toon3d.util.LevelGenConstants;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The one place the {@link RoomBlueprintRegistry} is populated (symbol/sprite-reuse order-5) — the ROOM
 * twin of {@code route/RouteRegistries.bootstrap()} and {@code tileset/TilesetRegistries.bootstrap()}.
 * {@link #bootstrap()} registers every v1 room: the GENERIC filler rooms (ENTRANCE / STANDARD /
 * SALVAGE_BAY / SUPPLY_CACHE) and the nine SIGNATURE rooms, each wrapping an existing
 * {@code LevelGenerator.RoomType}. After this order a room is a registered object, not a private enum
 * constant referenced by ~4 hardcoded switches; adding a room becomes ONE {@link RoomBlueprint} + one
 * {@code register()} line here. "LARGE" is a size MODIFIER applied independently to any room
 * (see {@code LevelGenerator.Room#isLarge}), not a registered blueprint.
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
    public static final String ID_SERVER_ROOM         = "server_room";
    public static final String ID_MEDICAL_BAY         = "medical_bay";
    public static final String ID_ARMORY              = "armory";
    public static final String ID_CRYO_CHAMBER        = "cryo_chamber";
    public static final String ID_POWER_PLANT         = "power_plant";
    public static final String ID_COMMAND_CENTER      = "command_center";
    public static final String ID_CONTAINMENT_BLOCK   = "containment_block";
    public static final String ID_RESEARCH_LAB        = "research_lab";
    public static final String ID_STELLAR_OBSERVATORY = "stellar_observatory";
    // A SIGNATURE "aftermath" room built from walls + environment decals (RECIPE B, bespoke prop pass).
    public static final String ID_GORE_NEST           = "gore_nest";
    // A SIGNATURE "industrial utility / hazardous-materials" room built from walls + environment decals +
    // reused props (RECIPE B, bespoke prop pass): the facility's volatile air/coolant scrubbing hub.
    public static final String ID_ATMOSPHERIC_PLANT   = "atmospheric_plant";
    // order-8 REQUIREMENT PROOF 1 — the brand-new GENERIC salvage bay.
    public static final String ID_SALVAGE_BAY         = "salvage_bay";
    // order-9 RECIPE B acceptance room — a GENERIC storeroom added by REGISTRATION ALONE (composes
    // existing build steps; no bespoke generator helper, no RoomType constant).
    public static final String ID_SUPPLY_CACHE        = "supply_cache";

    // FEATURE slots the room architectures export (R17 FEATURE anchors).
    public static final String FEATURE_LAB_ALCOVE              = "alcove";
    public static final String FEATURE_OBSERVATORY_CORE        = "core";
    public static final String FEATURE_OBSERVATORY_SPOKES      = "spokes";
    public static final String FEATURE_OBSERVATORY_ANNULUS     = "annulus";
    public static final String FEATURE_OBSERVATORY_SHELL_BAND  = "shell_band";

    private static final RoomBlueprintRegistry ROOMS = new RoomBlueprintRegistry();
    private static boolean bootstrapped = false;

    private RoomBlueprints() {}

    /**
     * Every symbol {@code blueprint.build()} writes into a fresh, generously sized room for one seed (headless; the
     * same isolation the build-demand drift guard uses). The balance rule C-ROOM sweeps seeds over it to prove a
     * SUPPLY_NEUTRAL room stamps no pickup and no enemy spawn.
     */
    public static java.util.Set<Character> symbolsStampedInIsolation(RoomBlueprint blueprint, long seed) {
        final int roomLeft = 2, roomBottom = 2, roomRight = 19, roomTop = 19;
        char[][] grid = new char[LevelGenConstants.LEVEL_GEN_GRID_HEIGHT][LevelGenConstants.LEVEL_GEN_GRID_WIDTH];
        for (char[] row : grid) java.util.Arrays.fill(row, 'x');
        for (int tileRow = roomBottom + 1; tileRow < roomTop; tileRow++) {
            for (int tileColumn = roomLeft + 1; tileColumn < roomRight; tileColumn++) {
                grid[tileRow][tileColumn] = ' ';
            }
        }
        LevelGenerator generator = new LevelGenerator(seed);
        LevelGenerator.Room room = new LevelGenerator.Room(roomLeft, roomBottom, roomRight, roomTop);
        room.type = LevelGenerator.roomTypeForBlueprint(blueprint);
        blueprint.build(new RoomBuildContext(generator, grid, room));
        java.util.Set<Character> stamped = new java.util.TreeSet<>();
        for (int tileRow = roomBottom; tileRow <= roomTop; tileRow++) {
            for (int tileColumn = roomLeft; tileColumn <= roomRight; tileColumn++) {
                stamped.add(grid[tileRow][tileColumn]);
            }
        }
        return stamped;
    }

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
        registry.register(new LambdaRoomBlueprint(ID_ENTRANCE, RoomKind.GENERIC, WallGroup.HALL,
                RoomSupplyStance.SUPPLY_NEUTRAL,
                RoomSymbolDemand.of(emptyDemand()),
                context -> true,
                context -> LevelGenConstants.LEVEL_GEN_ROOM_ENTRANCE_SELECTION_WEIGHT,
                context -> { /* no architecture */ }).layout(null, null, null));

        // STANDARD: baseline lab room; residual fallback for any candidate no specialty claims.
        registry.register(new LambdaRoomBlueprint(ID_STANDARD, RoomKind.GENERIC, WallGroup.HALL,
                RoomSupplyStance.SUPPLY_NEUTRAL,
                RoomSymbolDemand.of(emptyDemand()),
                context -> true,
                context -> LevelGenConstants.LEVEL_GEN_ROOM_STANDARD_SELECTION_WEIGHT,
                context -> {
                    context.assignFloorLighting();
                    context.placeStandardColumns();
                    context.placeStandardProps();
                }).layout(null, null, null).regularKits(regularKits()));

        // NOTE: "LARGE" is no longer a room blueprint/type — it is a size MODIFIER applied
        // independently to any room (LevelGenerator/LinearCorridorGenerator Room.isLarge, rolled at
        // placement time), which keeps whatever blueprint styling the room would have gotten anyway.

        // SALVAGE_BAY (order-8 REQUIREMENT PROOF 1): a brand-new GENERIC room with a DISTINCT central
        // scrap-heap architecture (not a reskin). It reserves NO specific sprite — its demand is
        // FLEXIBLE-category slot counts only, so it composes from whatever flexible symbols are free on
        // the level and its look is fully allocator-driven. Eligible on ordinary floors at a modest size.
        // EQUAL-CHANCE: shares the common special-room weight and carries no per-level cap (a floor may
        // roll it as many times as its rooms fit) — see LEVEL_GEN_SPECIAL_ROOM_SELECTION_WEIGHT.
        registry.register(new LambdaRoomBlueprint(ID_SALVAGE_BAY, RoomKind.GENERIC, WallGroup.STORAGE,
                RoomSupplyStance.SUPPLY_NEUTRAL,
                RoomSymbolDemand.of(emptyDemand(), salvageBayCategoryCounts()),
                context -> context.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_SALVAGE_BAY_MIN_WIDTH
                        && context.interiorHeight() >= LevelGenConstants.LEVEL_GEN_SALVAGE_BAY_MIN_HEIGHT,
                context -> LevelGenConstants.LEVEL_GEN_SPECIAL_ROOM_SELECTION_WEIGHT,
                context -> context.placeSalvageBay()).layout(salvageBayWindow(), salvageBayKit(), salvageBayRelaxedKit()));

        // SUPPLY_CACHE (order-9 RECIPE B acceptance room): a compact ancillary storeroom added by
        // REGISTRATION ALONE — this blueprint + this register() line, nothing else. It needs NO RoomType
        // enum constant (roomTypeForBlueprint tags an unmatched id STANDARD) and NO bespoke generator
        // helper: its build() COMPOSES the EXISTING per-room steps — standard floor lighting, standard
        // columns, and a DOUBLE standard-prop pass so the room reads as a cluttered, densely-stocked
        // store. Its accent walls are allocator-driven per level (tagged STANDARD, it receives the STEP-C
        // generic-variety accents), and its demand declares only FLEXIBLE-category slot counts, so it
        // composes from whatever symbols are free. It competes in the STEP-A roulette like any room.
        registry.register(new LambdaRoomBlueprint(ID_SUPPLY_CACHE, RoomKind.GENERIC, WallGroup.STORAGE,
                RoomSupplyStance.SUPPLY_NEUTRAL,
                RoomSymbolDemand.of(emptyDemand(), supplyCacheCategoryCounts()),
                context -> context.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_SUPPLY_CACHE_MIN_WIDTH
                        && context.interiorHeight() >= LevelGenConstants.LEVEL_GEN_SUPPLY_CACHE_MIN_HEIGHT,
                context -> LevelGenConstants.LEVEL_GEN_SPECIAL_ROOM_SELECTION_WEIGHT,
                context -> {
                    context.assignFloorLighting();
                    context.placeStandardColumns();
                    context.placeStandardProps();
                    context.placeStandardProps(); // second pass → denser, cluttered storeroom clutter
                }).layout(supplyCacheWindow(), supplyCacheKit(), null));

        // --- SIGNATURE rooms (fixed identity; declare the sprites they claim) ---

        // SERVER_ROOM: data vault — RESEARCH wall family, rack rows of terminals/lockers, oil + a barrel.
        registry.register(new LambdaRoomBlueprint(ID_SERVER_ROOM, RoomKind.SIGNATURE, WallGroup.RESEARCH,
                RoomSupplyStance.SUPPLY_NEUTRAL,
                RoomSymbolDemand.of(demand(
                        'T', "prop_terminal",
                        'L', "prop_locker",
                        'O', "decal_oil",
                        'g', "prop_radioactive_barrel")),
                context -> true,
                context -> LevelGenConstants.LEVEL_GEN_SPECIAL_ROOM_SELECTION_WEIGHT,
                context -> {
                    context.assignFloorLighting();
                    context.retiredServerRoomWallRolls();
                    context.placeServerRoomProps();
                }).layout(serverRoomWindow(), serverRoomKit(true), serverRoomKit(false)));

        // MEDICAL_BAY: MEDICAL wall family, clinical props. EQUAL-CHANCE: shares the
        // common special-room weight and carries no per-level cap (a floor may roll more than one).
        registry.register(new LambdaRoomBlueprint(ID_MEDICAL_BAY, RoomKind.SIGNATURE, WallGroup.MEDICAL,
                RoomSupplyStance.SUPPLY_NEUTRAL,
                RoomSymbolDemand.of(demand(
                        '#', "prop_camera",
                        'C', "prop_crate",
                        'T', "prop_terminal",
                        '.', "decal_blood",
                        'O', "decal_oil",
                        'm', "decal_corpse")),
                context -> context.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_MEDICAL_BAY_MIN_WIDTH
                        && context.interiorHeight() >= LevelGenConstants.LEVEL_GEN_MEDICAL_BAY_MIN_HEIGHT,
                context -> LevelGenConstants.LEVEL_GEN_SPECIAL_ROOM_SELECTION_WEIGHT,
                context -> {
                    context.assignFloorLighting();
                    context.retiredRoomWallRolls();
                    context.placeMedicalBayProps();
                }).layout(medicalBayWindow(), medicalBayKit(), null));

        // ARMORY: last-stand room; SECURITY wall family, weapon racks, cameras, aftermath decals.
        registry.register(new LambdaRoomBlueprint(ID_ARMORY, RoomKind.SIGNATURE, WallGroup.SECURITY,
                RoomSupplyStance.SUPPLY_NEUTRAL,
                RoomSymbolDemand.of(demand(
                        '=', "prop_weapon_rack",
                        '#', "prop_camera",
                        '.', "decal_blood",
                        'm', "decal_corpse")),
                context -> context.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_ARMORY_MIN_WIDTH
                        && context.interiorHeight() >= LevelGenConstants.LEVEL_GEN_ARMORY_MIN_HEIGHT,
                context -> LevelGenConstants.LEVEL_GEN_SPECIAL_ROOM_SELECTION_WEIGHT,
                context -> {
                    context.assignFloorLighting();
                    context.retiredRoomWallRolls();
                    context.placeArmoryProps();
                }).layout(armoryWindow(), armoryKit(), null));

        // CRYO_CHAMBER: cryogenic bay; CONTAINMENT wall family, bio-pod rows.
        registry.register(new LambdaRoomBlueprint(ID_CRYO_CHAMBER, RoomKind.SIGNATURE, WallGroup.CONTAINMENT,
                RoomSupplyStance.SUPPLY_NEUTRAL,
                RoomSymbolDemand.of(demand(
                        '&', "prop_biopod",
                        '#', "prop_camera",
                        'O', "decal_oil",
                        'C', "prop_crate")),
                context -> context.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_CRYO_MIN_WIDTH
                        && context.interiorHeight() >= LevelGenConstants.LEVEL_GEN_CRYO_MIN_HEIGHT,
                context -> LevelGenConstants.LEVEL_GEN_SPECIAL_ROOM_SELECTION_WEIGHT,
                context -> {
                    context.assignFloorLighting();
                    context.retiredRoomWallRolls();
                    context.placeCryoChamberProps();
                }).layout(cryoChamberWindow(), cryoChamberKit(), null));

        // POWER_PLANT: reactor core (LARGE-class); MACHINERY wall family, generators.
        registry.register(new LambdaRoomBlueprint(ID_POWER_PLANT, RoomKind.SIGNATURE, WallGroup.MACHINERY,
                RoomSupplyStance.SUPPLY_NEUTRAL,
                RoomSymbolDemand.of(demand(
                        '%', "prop_generator",
                        '#', "prop_camera",
                        'g', "prop_radioactive_barrel",
                        'O', "decal_oil")),
                context -> context.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_LARGE_MIN_DIM
                        && context.interiorHeight() >= LevelGenConstants.LEVEL_GEN_LARGE_MIN_DIM,
                context -> LevelGenConstants.LEVEL_GEN_SETPIECE_ROOM_SELECTION_WEIGHT,
                context -> {
                    context.assignFloorLighting();
                    context.retiredRoomWallRolls();
                    context.placePowerPlantProps();
                }).layout(powerPlantWindow(), powerPlantKit(), null));

        // COMMAND_CENTER: control room (LARGE-class); SECURITY wall family, terminals.
        registry.register(new LambdaRoomBlueprint(ID_COMMAND_CENTER, RoomKind.SIGNATURE, WallGroup.SECURITY,
                RoomSupplyStance.SUPPLY_NEUTRAL,
                RoomSymbolDemand.of(demand(
                        'T', "prop_terminal",
                        '#', "prop_camera",
                        '=', "prop_weapon_rack",
                        '.', "decal_blood",
                        'm', "decal_corpse")),
                context -> context.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_COMMAND_MIN_WIDTH
                        && context.interiorHeight() >= LevelGenConstants.LEVEL_GEN_COMMAND_MIN_HEIGHT,
                context -> LevelGenConstants.LEVEL_GEN_SETPIECE_ROOM_SELECTION_WEIGHT,
                context -> {
                    context.assignFloorLighting();
                    context.retiredRoomWallRolls();
                    context.placeCommandCenterProps();
                }).layout(commandCenterWindow(), commandCenterKit(), null));

        // CONTAINMENT_BLOCK: cell block; CONTAINMENT wall family, bio-pods, dread.
        registry.register(new LambdaRoomBlueprint(ID_CONTAINMENT_BLOCK, RoomKind.SIGNATURE, WallGroup.CONTAINMENT,
                RoomSupplyStance.SUPPLY_NEUTRAL,
                RoomSymbolDemand.of(demand(
                        '&', "prop_biopod",
                        'C', "prop_crate",
                        '#', "prop_camera",
                        '.', "decal_blood",
                        'O', "decal_oil",
                        'm', "decal_corpse")),
                context -> context.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_CONTAINMENT_MIN_WIDTH
                        && context.interiorHeight() >= LevelGenConstants.LEVEL_GEN_CONTAINMENT_MIN_HEIGHT,
                context -> LevelGenConstants.LEVEL_GEN_SPECIAL_ROOM_SELECTION_WEIGHT,
                context -> {
                    context.assignFloorLighting();
                    context.retiredRoomWallRolls();
                    context.placeContainmentBlockProps();
                }).layout(containmentBlockWindow(), containmentBlockKit(), null));

        // RESEARCH_LAB: sci-fi set-piece; RESEARCH wall family, specimen tanks, AI core.
        registry.register(new LambdaRoomBlueprint(ID_RESEARCH_LAB, RoomKind.SIGNATURE, WallGroup.RESEARCH,
                RoomSupplyStance.SUPPLY_NEUTRAL,
                RoomSymbolDemand.of(demand(
                        'I', "prop_specimen_tank",
                        'W', "prop_holo_workstation",
                        'J', "prop_ai_core",
                        'e', "decal_energy_scorch")),
                context -> context.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_RESEARCH_LAB_MIN_WIDTH
                        && context.interiorHeight() >= LevelGenConstants.LEVEL_GEN_RESEARCH_LAB_MIN_HEIGHT,
                context -> LevelGenConstants.LEVEL_GEN_SPECIAL_ROOM_SELECTION_WEIGHT,
                context -> {
                    context.assignFloorLighting();
                    context.retiredRoomWallRolls();
                    context.placeResearchLabProps();
                }).layout(researchLabWindow(), researchLabKit(), null));

        // STELLAR_OBSERVATORY: rare rotunda landmark; room-CONTAINED observatory art (hull/viewport/
        // magrail walls, gravity-well core, pylons, holo-table, cargo crates, deck/debris/seam decals).
        // Its demand is exactly the "observatory" theme-tagged sprites, so order-4 binds them ONLY when
        // this room is placed — satisfying the containment rule (order-4 R2).
        registry.register(new LambdaRoomBlueprint(ID_STELLAR_OBSERVATORY, RoomKind.SIGNATURE, WallGroup.SIGNATURE,
                RoomSupplyStance.SUPPLY_NEUTRAL,
                RoomSymbolDemand.of(demand(
                        ';',  "prop_gravity_well_core",
                        '|',  "prop_docking_strut_pylon",
                        '<',  "prop_astro_nav_table",
                        '\\', "prop_floating_cargo_crate",
                        '?',  "decal_magnetic_deck",
                        ']',  "decal_zerog_debris",
                        '-',  "decal_starlight_seam")),
                context -> context.alreadyPlacedOfThisKind() < LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_MAX
                        && context.dungeonDepth() >= LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_MIN_DEPTH
                        && context.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_MIN_INTERIOR
                        && context.interiorHeight() >= LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_MIN_INTERIOR
                        && Math.max(context.interiorWidth(), context.interiorHeight())
                                <= Math.min(context.interiorWidth(), context.interiorHeight())
                                        * LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_MAX_ASPECT,
                context -> LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_SELECTION_WEIGHT,
                context -> {
                    context.assignFloorLighting();
                    context.placeStellarObservatoryProps();
                }).layout(stellarObservatoryWindow(), stellarObservatoryKit(), null));

        // GORE_NEST: a demonic-breach "aftermath" set-piece, built from WALLS + ENVIRONMENT DECALS only,
        // all reused art (no new symbol, no new sprite). It PINS the currently-unclaimed gore wall 'G'
        // (flesh membrane) + rust wall 'j' accent so the allocator reserves its exact look; the ruptured
        // bio-pod '&', barrels 'g' and crates 'C', and the corpse/blood/bile decals ('m' '.' 's' 'O') all
        // claim the SAME legacy sprite the sibling rooms use (no clash — same-symbol/same-sprite is a
        // no-op for the allocator). DEPTH/SIZE-gated (>=2 deep, min interior) so it only appears past the
        // intro floor with room to breathe, and capped at LEVEL_GEN_GORE_NEST_MAX (1) per level so it stays a
        // landmark. Carries the above-baseline SET-PIECE selection weight (see section 14): at the old flat
        // 1.0 the depth gate made it invisible on the early floors most players see. Its build() composes
        // floor lighting + the shared wall theming (GORE_NEST case) + a bespoke nest-prop pass.
        registry.register(new LambdaRoomBlueprint(ID_GORE_NEST, RoomKind.SIGNATURE, WallGroup.SIGNATURE,
                RoomSupplyStance.SUPPLY_NEUTRAL,
                RoomSymbolDemand.of(demand(
                        '&', "prop_biopod",
                        'g', "prop_radioactive_barrel",
                        'C', "prop_crate",
                        'm', "decal_corpse",
                        '.', "decal_blood",
                        's', "decal_blood_alt",
                        'O', "decal_oil")),
                context -> context.alreadyPlacedOfThisKind() < LevelGenConstants.LEVEL_GEN_GORE_NEST_MAX
                        && context.dungeonDepth()   >= LevelGenConstants.LEVEL_GEN_GORE_NEST_MIN_DEPTH
                        && context.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_GORE_NEST_MIN_WIDTH
                        && context.interiorHeight() >= LevelGenConstants.LEVEL_GEN_GORE_NEST_MIN_HEIGHT,
                context -> LevelGenConstants.LEVEL_GEN_SETPIECE_ROOM_SELECTION_WEIGHT,
                context -> {
                    context.assignFloorLighting();
                    context.retiredRoomWallRolls();      // retiredRoomWallRollsForRoom handles the GORE_NEST case
                    context.placeGoreNestProps();
                }).layout(goreNestWindow(), goreNestKit(), null));

        // ATMOSPHERIC_PLANT: an industrial "hazardous-materials" set-piece — the facility's volatile
        // air/coolant scrubbing hub, built from ENVIRONMENT DECALS + reused props only (no new
        // symbol, no new sprite); its walls are the MACHINERY family, dressed by the LevelFinisher. The
        // scrubber pumps '%', live gas cylinders 'E', coolant drums 'g', coolant slick 'O' and thermal
        // scorch 'e' all claim the SAME legacy sprite the sibling rooms use (no clash — same-symbol/
        // same-sprite is a no-op for the allocator). DEPTH/SIZE-gated (>=2 deep, min interior) and capped at
        // LEVEL_GEN_ATMO_PLANT_MAX (1) per level so it stays a landmark. Carries the above-baseline SET-PIECE
        // selection weight (see section 14): at the old flat 1.0 the depth gate made it invisible on the early
        // floors most players see. Its build() composes floor lighting + the RETIRED
        // hazard/vent wall pass (its draws kept for R1, its writes gone) + a bespoke machinery-and-leak prop pass. The
        // 'E' cluster becomes live explosive barrels at load, making the room the game's chain-reaction arena.
        registry.register(new LambdaRoomBlueprint(ID_ATMOSPHERIC_PLANT, RoomKind.SIGNATURE, WallGroup.MACHINERY,
                RoomSupplyStance.SUPPLY_NEUTRAL,
                RoomSymbolDemand.of(demand(
                        '%', "prop_generator",
                        'E', "prop_explosive_barrel",
                        'g', "prop_radioactive_barrel",
                        'O', "decal_oil",
                        'e', "decal_energy_scorch")),
                context -> context.alreadyPlacedOfThisKind() < LevelGenConstants.LEVEL_GEN_ATMO_PLANT_MAX
                        && context.dungeonDepth()   >= LevelGenConstants.LEVEL_GEN_ATMO_PLANT_MIN_DEPTH
                        && context.interiorWidth()  >= LevelGenConstants.LEVEL_GEN_ATMO_PLANT_MIN_WIDTH
                        && context.interiorHeight() >= LevelGenConstants.LEVEL_GEN_ATMO_PLANT_MIN_HEIGHT,
                context -> LevelGenConstants.LEVEL_GEN_SETPIECE_ROOM_SELECTION_WEIGHT,
                context -> {
                    context.assignFloorLighting();
                    context.retiredAtmosphericPlantWallRolls(); // the old hazard/vent pass: draws kept (R1), no writes
                    context.placeAtmosphericPlantProps();
                }).layout(atmosphericPlantWindow(), atmosphericPlantKit(), null));
    }


    // -------------------------------------------------------------------------
    // SIZE WINDOWS and PROP KITS (level-generation-overhaul R4 / R16-R23). Row 1 of a special kit is its
    // SIGNATURE. Symbols are existing ones; each renders its legacy sprite (pinned by the kit, R29). Tuning a
    // row is short-lane work: one row, no balance file. Hazard 'g' stays owned by this file (HAZARD_DECLARATIONS).
    // -------------------------------------------------------------------------

    private static RoomSizeWindow window(int minWidth, int minHeight, int maxWidth, int maxHeight) {
        return RoomSizeWindow.of(minWidth, minHeight, maxWidth, maxHeight);
    }

    static RoomSizeWindow serverRoomWindow() {
        return window(LevelGenConstants.LEVEL_GEN_SIZE_SERVER_ROOM_MIN_WIDTH, LevelGenConstants.LEVEL_GEN_SIZE_SERVER_ROOM_MIN_HEIGHT,
                      LevelGenConstants.LEVEL_GEN_SIZE_SERVER_ROOM_MAX_WIDTH, LevelGenConstants.LEVEL_GEN_SIZE_SERVER_ROOM_MAX_HEIGHT);
    }

    static RoomSizeWindow medicalBayWindow() {
        return window(LevelGenConstants.LEVEL_GEN_SIZE_MEDICAL_BAY_MIN_WIDTH, LevelGenConstants.LEVEL_GEN_SIZE_MEDICAL_BAY_MIN_HEIGHT,
                      LevelGenConstants.LEVEL_GEN_SIZE_MEDICAL_BAY_MAX_WIDTH, LevelGenConstants.LEVEL_GEN_SIZE_MEDICAL_BAY_MAX_HEIGHT);
    }

    static RoomSizeWindow armoryWindow() {
        return window(LevelGenConstants.LEVEL_GEN_SIZE_ARMORY_MIN_WIDTH, LevelGenConstants.LEVEL_GEN_SIZE_ARMORY_MIN_HEIGHT,
                      LevelGenConstants.LEVEL_GEN_SIZE_ARMORY_MAX_WIDTH, LevelGenConstants.LEVEL_GEN_SIZE_ARMORY_MAX_HEIGHT);
    }

    static RoomSizeWindow cryoChamberWindow() {
        return window(LevelGenConstants.LEVEL_GEN_SIZE_CRYO_CHAMBER_MIN_WIDTH, LevelGenConstants.LEVEL_GEN_SIZE_CRYO_CHAMBER_MIN_HEIGHT,
                      LevelGenConstants.LEVEL_GEN_SIZE_CRYO_CHAMBER_MAX_WIDTH, LevelGenConstants.LEVEL_GEN_SIZE_CRYO_CHAMBER_MAX_HEIGHT);
    }

    static RoomSizeWindow powerPlantWindow() {
        return window(LevelGenConstants.LEVEL_GEN_SIZE_POWER_PLANT_MIN_WIDTH, LevelGenConstants.LEVEL_GEN_SIZE_POWER_PLANT_MIN_HEIGHT,
                      LevelGenConstants.LEVEL_GEN_SIZE_POWER_PLANT_MAX_WIDTH, LevelGenConstants.LEVEL_GEN_SIZE_POWER_PLANT_MAX_HEIGHT);
    }

    static RoomSizeWindow commandCenterWindow() {
        return window(LevelGenConstants.LEVEL_GEN_SIZE_COMMAND_CENTER_MIN_WIDTH, LevelGenConstants.LEVEL_GEN_SIZE_COMMAND_CENTER_MIN_HEIGHT,
                      LevelGenConstants.LEVEL_GEN_SIZE_COMMAND_CENTER_MAX_WIDTH, LevelGenConstants.LEVEL_GEN_SIZE_COMMAND_CENTER_MAX_HEIGHT);
    }

    static RoomSizeWindow containmentBlockWindow() {
        return window(LevelGenConstants.LEVEL_GEN_SIZE_CONTAINMENT_BLOCK_MIN_WIDTH, LevelGenConstants.LEVEL_GEN_SIZE_CONTAINMENT_BLOCK_MIN_HEIGHT,
                      LevelGenConstants.LEVEL_GEN_SIZE_CONTAINMENT_BLOCK_MAX_WIDTH, LevelGenConstants.LEVEL_GEN_SIZE_CONTAINMENT_BLOCK_MAX_HEIGHT);
    }

    static RoomSizeWindow researchLabWindow() {
        return window(LevelGenConstants.LEVEL_GEN_SIZE_RESEARCH_LAB_MIN_WIDTH, LevelGenConstants.LEVEL_GEN_SIZE_RESEARCH_LAB_MIN_HEIGHT,
                      LevelGenConstants.LEVEL_GEN_SIZE_RESEARCH_LAB_MAX_WIDTH, LevelGenConstants.LEVEL_GEN_SIZE_RESEARCH_LAB_MAX_HEIGHT);
    }

    static RoomSizeWindow stellarObservatoryWindow() {
        return RoomSizeWindow.square(LevelGenConstants.LEVEL_GEN_SIZE_STELLAR_OBSERVATORY_MIN_SIDE,
                                     LevelGenConstants.LEVEL_GEN_SIZE_STELLAR_OBSERVATORY_MAX_SIDE);
    }

    static RoomSizeWindow goreNestWindow() {
        return window(LevelGenConstants.LEVEL_GEN_SIZE_GORE_NEST_MIN_WIDTH, LevelGenConstants.LEVEL_GEN_SIZE_GORE_NEST_MIN_HEIGHT,
                      LevelGenConstants.LEVEL_GEN_SIZE_GORE_NEST_MAX_WIDTH, LevelGenConstants.LEVEL_GEN_SIZE_GORE_NEST_MAX_HEIGHT);
    }

    static RoomSizeWindow atmosphericPlantWindow() {
        return window(LevelGenConstants.LEVEL_GEN_SIZE_ATMOSPHERIC_PLANT_MIN_WIDTH, LevelGenConstants.LEVEL_GEN_SIZE_ATMOSPHERIC_PLANT_MIN_HEIGHT,
                      LevelGenConstants.LEVEL_GEN_SIZE_ATMOSPHERIC_PLANT_MAX_WIDTH, LevelGenConstants.LEVEL_GEN_SIZE_ATMOSPHERIC_PLANT_MAX_HEIGHT);
    }

    static RoomSizeWindow salvageBayWindow() {
        return window(LevelGenConstants.LEVEL_GEN_SIZE_SALVAGE_BAY_MIN_WIDTH, LevelGenConstants.LEVEL_GEN_SIZE_SALVAGE_BAY_MIN_HEIGHT,
                      LevelGenConstants.LEVEL_GEN_SIZE_SALVAGE_BAY_MAX_WIDTH, LevelGenConstants.LEVEL_GEN_SIZE_SALVAGE_BAY_MAX_HEIGHT);
    }

    static RoomSizeWindow supplyCacheWindow() {
        return window(LevelGenConstants.LEVEL_GEN_SIZE_SUPPLY_CACHE_MIN_WIDTH, LevelGenConstants.LEVEL_GEN_SIZE_SUPPLY_CACHE_MIN_HEIGHT,
                      LevelGenConstants.LEVEL_GEN_SIZE_SUPPLY_CACHE_MAX_WIDTH, LevelGenConstants.LEVEL_GEN_SIZE_SUPPLY_CACHE_MAX_HEIGHT);
    }

    /** The regular-room window (R7). */
    public static RoomSizeWindow regularWindow() {
        return window(LevelGenConstants.LEVEL_GEN_SIZE_REGULAR_MIN_WIDTH, LevelGenConstants.LEVEL_GEN_SIZE_REGULAR_MIN_HEIGHT,
                      LevelGenConstants.LEVEL_GEN_SIZE_REGULAR_MAX_WIDTH, LevelGenConstants.LEVEL_GEN_SIZE_REGULAR_MAX_HEIGHT);
    }

    /** Server room: rack aisles of terminals and lockers. DENSE; the relaxed variant keeps alternate lines. */
    static PropKit serverRoomKit(boolean dense) {
        float lockers = LevelGenConstants.LEVEL_GEN_SERVER_LOCKER_RATIO;
        PropKit.Builder kit = PropKit.signature(ID_SERVER_ROOM)
                .row(PropKitRow.place("TL", 1f - lockers, lockers).at(PropAnchor.ROWS).count(1, 99))
                .row(PropKitRow.place('#').at(PropAnchor.CORNER).count(1))
                .row(PropKitRow.place('O').at(PropAnchor.NEAR).near("TL").count(1, 2));
        return dense ? kit.dense().build() : kit.build();
    }

    static PropKit medicalBayKit() {
        return PropKit.signature(ID_MEDICAL_BAY)
                .row(PropKitRow.place('&').at(PropAnchor.BACK_WALL).count(2, 4).as(PropKitRow.Mode.SPACED))
                .row(PropKitRow.place('T').at(PropAnchor.SIDE_WALL).count(1))
                .row(PropKitRow.place('I').at(PropAnchor.CORNER).count(0, 1).chance(0.6f))
                .row(PropKitRow.place('C').at(PropAnchor.CORNER).count(1).chance(0.5f))
                .row(PropKitRow.place('.').at(PropAnchor.NEAR).near("&").count(1, 2))
                .row(PropKitRow.place('m').at(PropAnchor.ANY_WALL).count(1).chance(0.4f))
                .build();
    }

    static PropKit armoryKit() {
        return PropKit.signature(ID_ARMORY)
                .row(PropKitRow.place('=').at(PropAnchor.BACK_WALL).count(2, 4).as(PropKitRow.Mode.RUN))
                .row(PropKitRow.place('C').at(PropAnchor.CORNER).count(1, 3).as(PropKitRow.Mode.CLUSTER))
                .row(PropKitRow.place('#').at(PropAnchor.ENTRY_WALL).count(1))
                .row(PropKitRow.place('m').at(PropAnchor.NEAR).near("=").count(1, 2))
                .row(PropKitRow.place('.').at(PropAnchor.TRAIL).count(1, 3))
                .build();
    }

    /** Cryo chamber: pod rows on the back wall and, in a room at least six deep, the opposite wall too. */
    static PropKit cryoChamberKit() {
        return PropKit.signature(ID_CRYO_CHAMBER)
                .row(PropKitRow.place('&').at(PropAnchor.BACK_WALL).count(2, 3).as(PropKitRow.Mode.SPACED))
                .row(PropKitRow.place('&').at(PropAnchor.ENTRY_WALL).count(1, 3).as(PropKitRow.Mode.SPACED)
                        .whenShortSideAtLeast(6))
                .row(PropKitRow.place('#').at(PropAnchor.CORNER).count(1))
                .row(PropKitRow.place('O').at(PropAnchor.NEAR).near("&").count(1, 3))
                .row(PropKitRow.place('C').at(PropAnchor.CORNER).count(0, 1).chance(0.5f))
                .build();
    }

    /** Power plant: a generator block in the middle of the room (2x2 or 1x3), drums in the corners. */
    static PropKit powerPlantKit() {
        return PropKit.signature(ID_POWER_PLANT)
                .row(PropKitRow.place('%').at(PropAnchor.CENTRE).count(2, 4).as(PropKitRow.Mode.CLUSTER)
                        .footprints(new int[]{ 2, 2 }, new int[]{ 3, 1 }))
                .row(PropKitRow.place('g').at(PropAnchor.CORNER).count(1, 2))
                .row(PropKitRow.place('#').at(PropAnchor.ENTRY_WALL).count(1))
                .row(PropKitRow.place('O').at(PropAnchor.NEAR).near("%").count(1, 3))
                .row(PropKitRow.place('e').at(PropAnchor.NEAR).near("%").count(0, 2))
                .build();
    }

    static PropKit commandCenterKit() {
        return PropKit.signature(ID_COMMAND_CENTER)
                .row(PropKitRow.place('T').at(PropAnchor.BACK_WALL).count(3, 5).as(PropKitRow.Mode.RUN))
                .row(PropKitRow.place('W').at(PropAnchor.CENTRE).count(1).chance(0.6f))
                .row(PropKitRow.place('=').at(PropAnchor.SIDE_WALL).count(1))
                .row(PropKitRow.place('#').at(PropAnchor.CORNER).count(1, 2))
                .row(PropKitRow.place('.').at(PropAnchor.FIELD).count(0, 2))
                .row(PropKitRow.place('m').at(PropAnchor.ANY_WALL).count(1).chance(0.3f))
                .build();
    }

    static PropKit containmentBlockKit() {
        return PropKit.signature(ID_CONTAINMENT_BLOCK)
                .row(PropKitRow.place('&').at(PropAnchor.BACK_WALL).count(3, 5).as(PropKitRow.Mode.SPACED))
                .row(PropKitRow.place('T').at(PropAnchor.ENTRY_WALL).count(1))
                .row(PropKitRow.place('#').at(PropAnchor.CORNER).count(2))
                .row(PropKitRow.place('m').at(PropAnchor.ANY_WALL).count(1, 2))
                .row(PropKitRow.place('.').at(PropAnchor.TRAIL).count(2, 3))
                .row(PropKitRow.place('O').at(PropAnchor.FIELD).count(0, 1))
                .build();
    }

    /** Research lab: specimen tanks on the back wall, the AI core in the lab's alcove (else a corner). */
    static PropKit researchLabKit() {
        return PropKit.signature(ID_RESEARCH_LAB)
                .row(PropKitRow.place('I').at(PropAnchor.BACK_WALL).count(2, 4).as(PropKitRow.Mode.SPACED))
                .row(PropKitRow.place('J').at(PropAnchor.FEATURE).feature(FEATURE_LAB_ALCOVE).orAt(PropAnchor.CORNER).count(1))
                .row(PropKitRow.place('W').at(PropAnchor.SIDE_WALL).count(1, 2))
                .row(PropKitRow.place('T').at(PropAnchor.SIDE_WALL).count(1))
                .row(PropKitRow.place('e').at(PropAnchor.FIELD).count(1, LevelGenConstants.LEVEL_GEN_RESEARCH_LAB_SCORCH_MAX))
                .build();
    }

    /** Stellar observatory: every prop sits on a slot the rotunda architecture exported. */
    static PropKit stellarObservatoryKit() {
        return PropKit.signature(ID_STELLAR_OBSERVATORY)
                .row(PropKitRow.place(';').at(PropAnchor.FEATURE).feature(FEATURE_OBSERVATORY_CORE).count(1))
                .row(PropKitRow.place('|').at(PropAnchor.FEATURE).feature(FEATURE_OBSERVATORY_SPOKES)
                        .count(LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_PYLON_MIN,
                               LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_PYLON_MAX))
                .row(PropKitRow.place('<').at(PropAnchor.FEATURE).feature(FEATURE_OBSERVATORY_ANNULUS).count(1)
                        .chance(LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_HOLOTABLE_CHANCE))
                .row(PropKitRow.place('\\').at(PropAnchor.FEATURE).feature(FEATURE_OBSERVATORY_SHELL_BAND)
                        .count(LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_CRATE_MIN,
                               LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_CRATE_MAX))
                .row(PropKitRow.place(']').at(PropAnchor.FEATURE).feature(FEATURE_OBSERVATORY_ANNULUS)
                        .count(LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_DEBRIS_MIN,
                               LevelGenConstants.LEVEL_GEN_STELLAR_OBSERVATORY_DEBRIS_MAX))
                .build();
    }

    /** Gore nest: one ruptured pod at the centre over a carpet of aftermath decals. */
    static PropKit goreNestKit() {
        return PropKit.signature(ID_GORE_NEST)
                .row(PropKitRow.place('&').at(PropAnchor.CENTRE).count(1))
                .row(PropKitRow.place("m.sO", 1f, 1f, 1f, 1f).at(PropAnchor.FIELD)
                        .density(LevelGenConstants.LEVEL_GEN_GORE_NEST_DECAL_CHANCE))
                .row(PropKitRow.place("gC", 1f, 1f).at(PropAnchor.ANY_WALL).count(1, 2))
                .build();
    }

    /** Atmospheric plant: scrubbers on the back wall and the three-cylinder chain-reaction core on a side wall. */
    static PropKit atmosphericPlantKit() {
        return PropKit.signature(ID_ATMOSPHERIC_PLANT)
                .row(PropKitRow.place('%').at(PropAnchor.BACK_WALL).count(2).as(PropKitRow.Mode.SPACED))
                .row(PropKitRow.place('E').at(PropAnchor.SIDE_WALL).count(3).as(PropKitRow.Mode.CLUSTER))
                .row(PropKitRow.place('g').at(PropAnchor.CORNER).count(1))
                .row(PropKitRow.place('O').at(PropAnchor.FIELD).near("%").density(0.35f))
                .row(PropKitRow.place('e').at(PropAnchor.FIELD).density(0.20f))
                .build();
    }

    /** Salvage bay: a scrap heap in the middle, racks along the walls. DENSE. */
    static PropKit salvageBayKit() {
        return PropKit.signature(ID_SALVAGE_BAY).dense()
                .row(PropKitRow.place("Cg%", 1f, 1f, 1f).at(PropAnchor.CENTRE).count(3, 6).as(PropKitRow.Mode.CLUSTER)
                        .footprints(new int[]{ 3, 2 }))
                .row(PropKitRow.place("L=", 1f, 1f).at(PropAnchor.ANY_WALL).count(1, 3))
                .row(PropKitRow.place("Oe", 1f, 1f).at(PropAnchor.FIELD).count(1, 3))
                .build();
    }

    /** The salvage bay when the floor's tight room is spent: a heap of at most three. */
    static PropKit salvageBayRelaxedKit() {
        return PropKit.signature(ID_SALVAGE_BAY)
                .row(PropKitRow.place("Cg%", 1f, 1f, 1f).at(PropAnchor.CENTRE).count(2, 3).as(PropKitRow.Mode.CLUSTER)
                        .footprints(new int[]{ 2, 2 }))
                .row(PropKitRow.place("L=", 1f, 1f).at(PropAnchor.ANY_WALL).count(1, 3))
                .row(PropKitRow.place("Oe", 1f, 1f).at(PropAnchor.FIELD).count(1, 3))
                .build();
    }

    /** Supply cache: crate stacks in two corners and a locker run on the back wall. */
    static PropKit supplyCacheKit() {
        return PropKit.signature(ID_SUPPLY_CACHE)
                .row(PropKitRow.place('C').at(PropAnchor.CORNER).count(2, 3).as(PropKitRow.Mode.CLUSTER))
                .row(PropKitRow.place('C').at(PropAnchor.CORNER).count(2, 3).as(PropKitRow.Mode.CLUSTER))
                .row(PropKitRow.place('L').at(PropAnchor.BACK_WALL).count(2, 3).as(PropKitRow.Mode.RUN))
                .row(PropKitRow.place('O').at(PropAnchor.FIELD).count(0, 1))
                .build();
    }

    /** The five regular-room kits (R23), drawn uniformly (a route profile's preferred kit counts double). */
    static java.util.List<PropKit> regularKits() {
        java.util.List<PropKit> kits = new java.util.ArrayList<>();
        kits.add(officeKit());
        kits.add(PropKit.plain(PropKit.ID_STOREROOM)
                .row(PropKitRow.place('C').at(PropAnchor.CORNER).count(2, 3).as(PropKitRow.Mode.CLUSTER))
                .row(PropKitRow.place('L').at(PropAnchor.SIDE_WALL).count(1, 2).as(PropKitRow.Mode.RUN))
                .row(PropKitRow.place('O').at(PropAnchor.FIELD).count(0, 1).chance(0.3f))
                .build());
        kits.add(PropKit.plain(PropKit.ID_MAINTENANCE)
                .row(PropKitRow.place('%').at(PropAnchor.SIDE_WALL).orAt(PropAnchor.BACK_WALL).count(1))
                .row(PropKitRow.place('g').at(PropAnchor.NEAR).near("%").count(0, 1))
                .row(PropKitRow.place('O').at(PropAnchor.NEAR).near("%").count(1, 2))
                .build());
        kits.add(PropKit.plain(PropKit.ID_AFTERMATH)
                .row(PropKitRow.place('C').at(PropAnchor.ANY_WALL).count(0, 1))
                .row(PropKitRow.place('m').at(PropAnchor.ANY_WALL).count(1))
                .row(PropKitRow.place(".s", 1f, 1f).at(PropAnchor.TRAIL).count(2, 4))
                .build());
        kits.add(PropKit.plain(PropKit.ID_BARE).build());
        return java.util.Collections.unmodifiableList(kits);
    }

    /** The OFFICE kit: a desk of terminals on the back wall (also the R20 terminal fallback). */
    public static PropKit officeKit() {
        return PropKit.plain(PropKit.ID_OFFICE)
                .row(PropKitRow.place('T').at(PropAnchor.BACK_WALL).count(1, 2).as(PropKitRow.Mode.RUN))
                .row(PropKitRow.place('L').at(PropAnchor.CORNER).count(0, 1))
                .row(PropKitRow.place('#').at(PropAnchor.CORNER).count(0, 1).chance(0.3f))
                .row(PropKitRow.place('m').at(PropAnchor.ANY_WALL).count(1).chance(0.2f))
                .build();
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
        counts.put(TileCategory.SOLID_PROP, 2);
        counts.put(TileCategory.FLOOR_DECAL, 1);
        return counts;
    }

    // The supply cache's demand is FLEXIBLE-category slot counts only (no pinned sprite): a single accent
    // wall and a couple of stock props, all allocator-chosen from free symbols (order-9 RECIPE B).
    private static Map<TileCategory, Integer> supplyCacheCategoryCounts() {
        Map<TileCategory, Integer> counts = new EnumMap<>(TileCategory.class);
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
        private final WallGroup wallGroup;
        private final RoomSupplyStance supplyStance;
        private final RoomSymbolDemand demand;
        private final Eligibility eligibility;
        private final SelectionWeight weight;
        private final BuildStep buildStep;
        private RoomSizeWindow sizeWindow;
        private PropKit kit;
        private PropKit relaxedKit;
        private java.util.List<PropKit> regularKits = java.util.Collections.emptyList();

        LambdaRoomBlueprint(String id, RoomKind kind, WallGroup wallGroup, RoomSupplyStance supplyStance,
                            RoomSymbolDemand demand,
                            Eligibility eligibility, SelectionWeight weight, BuildStep buildStep) {
            this.id          = id;
            this.kind        = kind;
            this.wallGroup   = wallGroup;
            this.supplyStance = supplyStance;
            this.demand      = demand;
            this.eligibility = eligibility;
            this.weight      = weight;
            this.buildStep   = buildStep;
        }

        @Override public String id() { return id; }
        @Override public RoomKind kind() { return kind; }
        @Override public WallGroup wallGroup() { return wallGroup; }
        @Override public RoomSupplyStance supplyStance() { return supplyStance; }
        @Override public boolean eligible(RoomContext context) { return eligibility.test(context); }
        @Override public float selectionWeight(RoomContext context) { return weight.of(context); }
        @Override public RoomSymbolDemand symbolDemand() { return demand; }
        @Override public void build(RoomBuildContext context) { buildStep.build(context); }
        @Override public RoomSizeWindow sizeWindow() { return sizeWindow; }
        @Override public PropKit kit() { return kit; }
        @Override public PropKit relaxedKit() { return relaxedKit; }
        @Override public java.util.List<PropKit> regularKits() { return regularKits; }

        /** Gives the row its size window, kit and (for a DENSE kit) relaxed kit. */
        LambdaRoomBlueprint layout(RoomSizeWindow roomWindow, PropKit roomKit, PropKit roomRelaxedKit) {
            this.sizeWindow = roomWindow;
            this.kit        = roomKit;
            this.relaxedKit = roomRelaxedKit;
            return this;
        }

        /** Gives the STANDARD row the regular-room kits (R23). */
        LambdaRoomBlueprint regularKits(java.util.List<PropKit> kits) {
            this.regularKits = kits;
            return this;
        }
    }
}
