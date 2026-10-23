package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.tileset.RoomSymbolDemand;
import ge.tbegvadze.toon3d.tileset.WallGroup;
import ge.tbegvadze.toon3d.util.LevelGenConstants;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * THE ROOM CATALOG — the one place the {@link RoomBlueprintRegistry} is populated (the ROOM twin of
 * {@code route/RouteRegistries.bootstrap()}). Every row says what a room IS and how it is FURNISHED
 * (level-generation-overhaul): its id and tier, its wall family, its supply stance, its symbol demand (architecture
 * + kit, R29), its ARCHITECTURE build (floor lighting, the observatory rotunda, the lab's alcove, pillar slots), its
 * SIZE WINDOW and its PROP KIT (row 1 = SIGNATURE; a DENSE kit has a RELAXED variant). The STANDARD row carries the
 * five regular-room kits. Every row other than ENTRANCE and STANDARD is a SPECIAL room in the equal-chance pool
 * ({@link SpecialRoomPlanner}); adding one is ONE row here plus its window/kit constants, and dilutes every type's
 * share. Hazard 'g' stays owned by this file. {@link #bootstrap()} is idempotent. Headless — no LibGDX.
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
    public static final String FEATURE_PILLARS                 = "pillars";
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
        LevelLayoutReport.Role role = blueprint.id().equals(ID_ENTRANCE) ? LevelLayoutReport.Role.ENTRANCE
                : SpecialRoomPlanner.isSpecial(blueprint.id()) ? LevelLayoutReport.Role.SPECIAL : LevelLayoutReport.Role.REGULAR;
        LayoutRoom room = new LayoutRoom(roomLeft, roomBottom, roomRight, roomTop, role, blueprint.id());
        java.util.List<LayoutRoom> rooms = java.util.Collections.singletonList(room);
        LayoutSupport.carveInteriors(grid, rooms);
        // One synthetic socket in the middle of the south wall, with its corridor tile.
        DoorSocket socket = DoorwayRules.openSocket(grid, room, room.centerColumn(), roomBottom);
        grid[socket.outerRow()][socket.outerColumn()] = 'l';
        DoorwayRules.hangDoor(grid, socket);
        java.util.Random random = new java.util.Random(seed);
        blueprint.build(new RoomBuildContext(grid, room, random, new LevelGenConfig(), rooms));
        RoomDresser dresser = new RoomDresser(grid, rooms, seed);
        java.util.List<PropKit> regular = blueprint.regularKits();
        PropKit kit = !regular.isEmpty() ? regular.get((int) Math.floorMod(seed, (long) regular.size())) : blueprint.kit();
        if (role != LevelLayoutReport.Role.ENTRANCE) dresser.dress(room, kit, blueprint.relaxedKit());
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
        // Every row: id, tier, wall family, supply stance, demand (architecture + kit, R29), the ARCHITECTURE build,
        // then its size window + kit (+ relaxed kit). Props are never placed by build(): the RoomDresser places them.

        // ENTRANCE: the player's start room — no architecture, no props, no decals (R22).
        registry.register(new LambdaRoomBlueprint(ID_ENTRANCE, RoomKind.GENERIC, WallGroup.HALL,
                RoomSupplyStance.SUPPLY_NEUTRAL, RoomSymbolDemand.of(emptyDemand()),
                context -> { /* no architecture */ }).layout(null, null, null));

        // STANDARD: every REGULAR room. Standard lighting plus the BARE kit's pillar slots; the kit is one of the five
        // regular kits (R23), chosen per room, so the row's own demand is empty — the dresser pins what it placed.
        registry.register(new LambdaRoomBlueprint(ID_STANDARD, RoomKind.GENERIC, WallGroup.HALL,
                RoomSupplyStance.SUPPLY_NEUTRAL, RoomSymbolDemand.of(emptyDemand()),
                context -> {
                    context.standardFloor();
                    context.exportPillarSlots();
                }).layout(null, null, null).regularKits(regularKits()));

        // SALVAGE_BAY: a dingy staging room around a central scrap heap. DENSE kit; RELAXED when the tight room is spent.
        registry.register(special(ID_SALVAGE_BAY, RoomKind.GENERIC, WallGroup.STORAGE, salvageBayKit(),
                RoomBuildContext::salvageBayFloor).layout(salvageBayWindow(), salvageBayKit(), salvageBayRelaxedKit()));

        // SUPPLY_CACHE: a compact storeroom — crate stacks in two corners, a locker run.
        registry.register(special(ID_SUPPLY_CACHE, RoomKind.GENERIC, WallGroup.STORAGE, supplyCacheKit(),
                RoomBuildContext::standardFloor).layout(supplyCacheWindow(), supplyCacheKit(), null));

        // SERVER_ROOM: the data vault — rack aisles of terminals and lockers. DENSE kit; RELAXED keeps alternate lines.
        registry.register(special(ID_SERVER_ROOM, RoomKind.SIGNATURE, WallGroup.RESEARCH, serverRoomKit(true),
                RoomBuildContext::serverRoomFloor).layout(serverRoomWindow(), serverRoomKit(true), serverRoomKit(false)));

        // MEDICAL_BAY: clinical brightness (no floor pass), beds on the back wall.
        registry.register(special(ID_MEDICAL_BAY, RoomKind.SIGNATURE, WallGroup.MEDICAL, medicalBayKit(),
                context -> { /* fully lit */ }).layout(medicalBayWindow(), medicalBayKit(), null));

        // ARMORY: the last stand — weapon racks on the back wall, the dead in front of them.
        registry.register(special(ID_ARMORY, RoomKind.SIGNATURE, WallGroup.SECURITY, armoryKit(),
                RoomBuildContext::armoryFloor).layout(armoryWindow(), armoryKit(), null));

        // CRYO_CHAMBER: pod rows on the long walls, cold dim floor.
        registry.register(special(ID_CRYO_CHAMBER, RoomKind.SIGNATURE, WallGroup.CONTAINMENT, cryoChamberKit(),
                RoomBuildContext::cryoChamberFloor).layout(cryoChamberWindow(), cryoChamberKit(), null));

        // POWER_PLANT: a humming generator block in the middle of a big dark room.
        registry.register(special(ID_POWER_PLANT, RoomKind.SIGNATURE, WallGroup.MACHINERY, powerPlantKit(),
                RoomBuildContext::powerPlantFloor).layout(powerPlantWindow(), powerPlantKit(), null));

        // COMMAND_CENTER: a console bank on the back wall, a holo-table in the middle.
        registry.register(special(ID_COMMAND_CENTER, RoomKind.SIGNATURE, WallGroup.SECURITY, commandCenterKit(),
                RoomBuildContext::commandCenterFloor).layout(commandCenterWindow(), commandCenterKit(), null));

        // CONTAINMENT_BLOCK: cell pods on the back wall, an observation console by the door.
        registry.register(special(ID_CONTAINMENT_BLOCK, RoomKind.SIGNATURE, WallGroup.CONTAINMENT, containmentBlockKit(),
                RoomBuildContext::containmentBlockFloor).layout(containmentBlockWindow(), containmentBlockKit(), null));

        // RESEARCH_LAB: specimen tanks on the back wall; the force-field wall stub makes the AI core's alcove.
        registry.register(special(ID_RESEARCH_LAB, RoomKind.SIGNATURE, WallGroup.RESEARCH, researchLabKit(),
                context -> {
                    context.standardFloor();
                    context.researchLabAlcove();
                }).layout(researchLabWindow(), researchLabKit(), null));

        // STELLAR_OBSERVATORY: the rotunda landmark (stellar-observatory-gravity-well-room). Its architecture owns the
        // catwalk '?' and spokes '-' decals; every prop sits on an exported FEATURE slot.
        Map<Character, String> rotunda = demand('?', "decal_magnetic_deck", '-', "decal_starlight_seam");
        registry.register(new LambdaRoomBlueprint(ID_STELLAR_OBSERVATORY, RoomKind.SIGNATURE, WallGroup.SIGNATURE,
                RoomSupplyStance.SUPPLY_NEUTRAL, RoomSymbolDemand.of(union(rotunda, stellarObservatoryKit())),
                RoomBuildContext::stellarObservatoryRotunda)
                .layout(stellarObservatoryWindow(), stellarObservatoryKit(), null));

        // GORE_NEST: a demonic-breach aftermath — one ruptured pod over a carpet of decals.
        registry.register(special(ID_GORE_NEST, RoomKind.SIGNATURE, WallGroup.SIGNATURE, goreNestKit(),
                RoomBuildContext::goreNestFloor).layout(goreNestWindow(), goreNestKit(), null));

        // ATMOSPHERIC_PLANT: the hazardous-materials hub — scrubbers, and the three live cylinders that make it the
        // game's chain-reaction arena ('E' placement owner beside GroupBarrelPlacer).
        registry.register(special(ID_ATMOSPHERIC_PLANT, RoomKind.SIGNATURE, WallGroup.MACHINERY, atmosphericPlantKit(),
                RoomBuildContext::atmosphericPlantFloor).layout(atmosphericPlantWindow(), atmosphericPlantKit(), null));
    }

    /** A special room's row: its demand is exactly its kit's (R29), and build() runs its architecture. */
    private static LambdaRoomBlueprint special(String id, RoomKind kind, WallGroup wallGroup, PropKit kit,
                                               BuildStep architecture) {
        return new LambdaRoomBlueprint(id, kind, wallGroup, RoomSupplyStance.SUPPLY_NEUTRAL,
                RoomSymbolDemand.of(union(emptyDemand(), kit)), architecture);
    }

    /** Architecture demand plus a kit's demand (R29: a room claims exactly what it can stamp). */
    private static Map<Character, String> union(Map<Character, String> architecture, PropKit kit) {
        Map<Character, String> combined = new LinkedHashMap<>(architecture);
        combined.putAll(kit.spriteDemand());
        return combined;
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
        return window(LevelGenConstants.LEVEL_GEN_ROOM_MIN_WIDTH, LevelGenConstants.LEVEL_GEN_ROOM_MIN_HEIGHT,
                      LevelGenConstants.LEVEL_GEN_ROOM_MAX_WIDTH, LevelGenConstants.LEVEL_GEN_ROOM_MAX_HEIGHT);
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
        // BARE: no props. A ROOMS_MST room at least 7 x 7 gets two pillars on the architecture's symmetric slots; a
        // LINEAR room gets one of its interior-layout variants (four pillars / avenue / altar) as slots (R25, G2).
        kits.add(PropKit.plain(PropKit.ID_BARE)
                .row(PropKitRow.place('P').at(PropAnchor.FEATURE).feature(FEATURE_PILLARS).count(2, 4))
                .build());
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

    // --- Concrete blueprint backed by the four functional pieces (kept package-private) ---

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
        private final BuildStep buildStep;
        private RoomSizeWindow sizeWindow;
        private PropKit kit;
        private PropKit relaxedKit;
        private java.util.List<PropKit> regularKits = java.util.Collections.emptyList();

        LambdaRoomBlueprint(String id, RoomKind kind, WallGroup wallGroup, RoomSupplyStance supplyStance,
                            RoomSymbolDemand demand, BuildStep buildStep) {
            this.id          = id;
            this.kind        = kind;
            this.wallGroup   = wallGroup;
            this.supplyStance = supplyStance;
            this.demand      = demand;
            this.buildStep   = buildStep;
        }

        @Override public String id() { return id; }
        @Override public RoomKind kind() { return kind; }
        @Override public WallGroup wallGroup() { return wallGroup; }
        @Override public RoomSupplyStance supplyStance() { return supplyStance; }
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
