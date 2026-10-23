package ge.tbegvadze.toon3d.util;

/** Procedural level generation constants — grid, room types, corridors, wall distribution. */
public final class LevelGenConstants {

    private LevelGenConstants() {}

    // Grid dimensions match the 80×45 tile layout that fills the 1280×720 world exactly.
    public static final int   LEVEL_GEN_GRID_WIDTH            = Constants.WORLD_WIDTH  / Constants.CELL_SIZE; // 80
    public static final int   LEVEL_GEN_GRID_HEIGHT           = Constants.WORLD_HEIGHT / Constants.CELL_SIZE; // 45
    // REGULAR room interior window (level-generation-overhaul R7), excluding the 1-tile shell on each side. Big
    // spaces come from the special rooms' windows and 3-wide halls, not from regular rooms.
    public static final int   LEVEL_GEN_ROOM_MIN_WIDTH        = 4;
    public static final int   LEVEL_GEN_ROOM_MIN_HEIGHT       = 4;
    public static final int   LEVEL_GEN_ROOM_MAX_WIDTH        = 12;
    public static final int   LEVEL_GEN_ROOM_MAX_HEIGHT       = 10;
    // Minimum gap between room bounding boxes so rooms never share a wall tile.
    public static final int   LEVEL_GEN_ROOM_MARGIN           = 2;
    public static final int   LEVEL_GEN_TARGET_ROOMS          = 16;
    public static final int   LEVEL_GEN_PLACEMENT_TRIES       = 600;
    public static final int   LEVEL_GEN_MAX_ENEMIES_PER_ROOM  = BalanceConfig.LEVEL_GEN_MAX_ENEMIES_PER_ROOM;
    // Cumulative thresholds for enemy type selection (each range maps to a spawn digit):
    //   [0.00, 0.25) → '1' PLAGUE_HULK   25 % slow tank melee
    //   [0.25, 0.40) → '2' EYE_TYRANT    15 % fast ranged kiter
    //   [0.40, 0.60) → '3' GORE_BITER    20 % fast light melee
    //   [0.60, 0.80) → '4' SHELL_BRUTE   20 % heavy charger melee
    //   [0.80, 1.00) → '5' MIRE_WRAITH   20 % slow hovering ranged
    public static final float LEVEL_GEN_CORRUPTOR_THRESHOLD   = 0.25f;
    public static final float LEVEL_GEN_VORTEX_EYE_THRESHOLD  = 0.40f;
    public static final float LEVEL_GEN_GHOUL_THRESHOLD        = 0.60f;
    public static final float LEVEL_GEN_CRAWLER_THRESHOLD      = 0.80f;
    // Probability that a corridor-room boundary 'l' tile becomes a door ('d').
    // 0.75 = roughly 3 out of 4 room entries get a door; some stay open for flow variety.
    public static final float LEVEL_GEN_DOOR_CHANCE           = 0.75f;

    // --- DOORWAYS AND CORRIDORS (level-generation-overhaul R9-R14) ---
    // Door clearance zone of a socket: Chebyshev radius around it, plus this many tiles straight out along its
    // passage axis on both sides. No solid prop or column may stand inside it (R18a, R26, R27).
    public static final int   LEVEL_GEN_DOOR_CLEARANCE_RADIUS      = 1;
    public static final int   LEVEL_GEN_DOOR_AXIS_CLEARANCE        = 2;
    // A socket is >= this many tiles from either corner of its side, so both flank tiles are straight shell (R10).
    public static final int   LEVEL_GEN_SOCKET_CORNER_MIN_DISTANCE = 2;
    // Sockets on the same side of a room are at least this many tiles apart (R10).
    public static final int   LEVEL_GEN_SOCKET_MIN_SPACING         = 3;
    public static final int   LEVEL_GEN_MAX_SOCKETS_PER_ROOM       = 3;
    public static final int   LEVEL_GEN_ENTRANCE_MAX_SOCKETS       = 2;
    // A SPECIAL room's sockets always get a door leaf; every other socket rolls LEVEL_GEN_DOOR_CHANCE (R14).
    public static final float LEVEL_GEN_SPECIAL_DOOR_CHANCE        = 1.0f;
    // CorridorRouter step costs (R12): a tile beside another room's shell, an existing corridor tile (merging),
    // and the per-tile tie-break jitter that makes the route seeded without biasing it.
    public static final float LEVEL_GEN_ROUTE_SHELL_HUG_COST       = 4f;
    public static final float LEVEL_GEN_ROUTE_MERGE_COST           = 0.5f;
    public static final float LEVEL_GEN_ROUTE_BASE_COST            = 1f;
    public static final float LEVEL_GEN_ROUTE_TIE_JITTER           = 0.001f;
    // Added on every change of direction, so corridors run as straight L / Z legs instead of staircases (a
    // staircase reads badly in first person, and its widened shoulders would touch only diagonally).
    public static final float LEVEL_GEN_ROUTE_TURN_COST            = 3f;
    // A 3-wide hall narrows to 1 wide for this many tiles before a socket (R13).
    public static final int   LEVEL_GEN_WIDE_HALL_TAPER_TILES      = 2;

    // --- THE SPECIAL ROOM PLANNER (level-generation-overhaul R1-R5) ---
    // The floor's special types come from the floor seed ^ this salt (NOT a footprint attempt seed, R3).
    public static final long  SPECIAL_ROOM_SEED_SALT                 = 0x5BEC1A15L;
    // One special per this many walkable footprint tiles, clamped to [MIN, MAX] (R2, GameMath).
    public static final int   LEVEL_GEN_WALKABLE_TILES_PER_SPECIAL_ROOM = 260;
    public static final int   LEVEL_GEN_SPECIAL_ROOMS_MIN            = 2;
    public static final int   LEVEL_GEN_SPECIAL_ROOMS_MAX            = 4;
    // Best-candidate sampling: random positions tried per special, keeping the one farthest from those placed (R5).
    public static final int   LEVEL_GEN_SPECIAL_PLACEMENT_CANDIDATES = 12;

    // --- THE PROP ENGINE (level-generation-overhaul R15-R22) ---
    // The dresser's own stream: floor seed ^ this salt ^ the room's index mix, so dressing never shifts layout draws.
    public static final long  ROOM_DRESSING_SEED_SALT              = 0x5EEDD8E55L;
    // Lane band half-width around each socket->HUB lane (1 = a 3-wide clear lane); a DENSE room keeps 0.
    public static final int   LEVEL_GEN_LANE_BAND_HALF_WIDTH       = 1;
    // Solid fill caps per room (solids / open interior tiles, R21).
    public static final float LEVEL_GEN_ROOM_FILL_CAP_REGULAR      = 0.12f;
    public static final float LEVEL_GEN_ROOM_FILL_CAP_SPECIAL      = 0.22f;
    public static final float LEVEL_GEN_ROOM_FILL_CAP_DENSE        = 0.40f;
    // At most this many rooms per floor are dressed DENSE (TIGHT); a further dense special goes RELAXED.
    public static final int   LEVEL_GEN_MAX_TIGHT_ROOMS_PER_FLOOR  = 1;
    // A non-signature CENTRE row needs an interior at least this big on both axes; a signature one falls back
    // to BACK_WALL below it (R17).
    public static final int   LEVEL_GEN_CENTRE_ROW_MIN_INTERIOR    = 7;
    // TRAIL decals: each lane tile from the HUB outward takes a decal at this chance until the count is met.
    public static final float LEVEL_GEN_TRAIL_STEP_CHANCE          = 0.6f;
    // Walkable decals in halls (corridor tiles outside every room), per hall tile (R28).
    public static final float LEVEL_GEN_HALL_DECAL_CHANCE          = 0.02f;

    // --- ROOM SIZE WINDOWS (level-generation-overhaul R4): interior W x H, min -> max; rotatable unless square.
    // Every minimum fits the room's signature row with three sockets present.
    public static final int LEVEL_GEN_SIZE_SERVER_ROOM_MIN_WIDTH          = 6;
    public static final int LEVEL_GEN_SIZE_SERVER_ROOM_MIN_HEIGHT         = 5;
    public static final int LEVEL_GEN_SIZE_SERVER_ROOM_MAX_WIDTH          = 10;
    public static final int LEVEL_GEN_SIZE_SERVER_ROOM_MAX_HEIGHT         = 8;
    public static final int LEVEL_GEN_SIZE_MEDICAL_BAY_MIN_WIDTH          = 7;
    public static final int LEVEL_GEN_SIZE_MEDICAL_BAY_MIN_HEIGHT         = 5;
    public static final int LEVEL_GEN_SIZE_MEDICAL_BAY_MAX_WIDTH          = 10;
    public static final int LEVEL_GEN_SIZE_MEDICAL_BAY_MAX_HEIGHT         = 8;
    public static final int LEVEL_GEN_SIZE_ARMORY_MIN_WIDTH               = 6;
    public static final int LEVEL_GEN_SIZE_ARMORY_MIN_HEIGHT              = 5;
    public static final int LEVEL_GEN_SIZE_ARMORY_MAX_WIDTH               = 9;
    public static final int LEVEL_GEN_SIZE_ARMORY_MAX_HEIGHT              = 7;
    public static final int LEVEL_GEN_SIZE_CRYO_CHAMBER_MIN_WIDTH         = 7;
    public static final int LEVEL_GEN_SIZE_CRYO_CHAMBER_MIN_HEIGHT        = 6;
    public static final int LEVEL_GEN_SIZE_CRYO_CHAMBER_MAX_WIDTH         = 11;
    public static final int LEVEL_GEN_SIZE_CRYO_CHAMBER_MAX_HEIGHT        = 8;
    public static final int LEVEL_GEN_SIZE_POWER_PLANT_MIN_WIDTH          = 8;
    public static final int LEVEL_GEN_SIZE_POWER_PLANT_MIN_HEIGHT         = 8;
    public static final int LEVEL_GEN_SIZE_POWER_PLANT_MAX_WIDTH          = 12;
    public static final int LEVEL_GEN_SIZE_POWER_PLANT_MAX_HEIGHT         = 10;
    public static final int LEVEL_GEN_SIZE_COMMAND_CENTER_MIN_WIDTH       = 8;
    public static final int LEVEL_GEN_SIZE_COMMAND_CENTER_MIN_HEIGHT      = 6;
    public static final int LEVEL_GEN_SIZE_COMMAND_CENTER_MAX_WIDTH       = 12;
    public static final int LEVEL_GEN_SIZE_COMMAND_CENTER_MAX_HEIGHT      = 9;
    public static final int LEVEL_GEN_SIZE_CONTAINMENT_BLOCK_MIN_WIDTH    = 8;
    public static final int LEVEL_GEN_SIZE_CONTAINMENT_BLOCK_MIN_HEIGHT   = 6;
    public static final int LEVEL_GEN_SIZE_CONTAINMENT_BLOCK_MAX_WIDTH    = 12;
    public static final int LEVEL_GEN_SIZE_CONTAINMENT_BLOCK_MAX_HEIGHT   = 8;
    public static final int LEVEL_GEN_SIZE_RESEARCH_LAB_MIN_WIDTH         = 7;
    public static final int LEVEL_GEN_SIZE_RESEARCH_LAB_MIN_HEIGHT        = 6;
    public static final int LEVEL_GEN_SIZE_RESEARCH_LAB_MAX_WIDTH         = 10;
    public static final int LEVEL_GEN_SIZE_RESEARCH_LAB_MAX_HEIGHT        = 8;
    public static final int LEVEL_GEN_SIZE_STELLAR_OBSERVATORY_MIN_SIDE   = 13;
    public static final int LEVEL_GEN_SIZE_STELLAR_OBSERVATORY_MAX_SIDE   = 14;
    public static final int LEVEL_GEN_SIZE_GORE_NEST_MIN_WIDTH            = 6;
    public static final int LEVEL_GEN_SIZE_GORE_NEST_MIN_HEIGHT           = 6;
    public static final int LEVEL_GEN_SIZE_GORE_NEST_MAX_WIDTH            = 10;
    public static final int LEVEL_GEN_SIZE_GORE_NEST_MAX_HEIGHT           = 8;
    public static final int LEVEL_GEN_SIZE_ATMOSPHERIC_PLANT_MIN_WIDTH    = 7;
    public static final int LEVEL_GEN_SIZE_ATMOSPHERIC_PLANT_MIN_HEIGHT   = 6;
    public static final int LEVEL_GEN_SIZE_ATMOSPHERIC_PLANT_MAX_WIDTH    = 10;
    public static final int LEVEL_GEN_SIZE_ATMOSPHERIC_PLANT_MAX_HEIGHT   = 8;
    public static final int LEVEL_GEN_SIZE_SALVAGE_BAY_MIN_WIDTH          = 7;
    public static final int LEVEL_GEN_SIZE_SALVAGE_BAY_MIN_HEIGHT         = 7;
    public static final int LEVEL_GEN_SIZE_SALVAGE_BAY_MAX_WIDTH          = 10;
    public static final int LEVEL_GEN_SIZE_SALVAGE_BAY_MAX_HEIGHT         = 9;
    public static final int LEVEL_GEN_SIZE_SUPPLY_CACHE_MIN_WIDTH         = 5;
    public static final int LEVEL_GEN_SIZE_SUPPLY_CACHE_MIN_HEIGHT        = 5;
    public static final int LEVEL_GEN_SIZE_SUPPLY_CACHE_MAX_WIDTH         = 8;
    public static final int LEVEL_GEN_SIZE_SUPPLY_CACHE_MAX_HEIGHT        = 6;
    // The entrance room's interior, each axis (R6).
    public static final int LEVEL_GEN_ENTRANCE_MIN_DIM                    = 5;
    public static final int LEVEL_GEN_ENTRANCE_MAX_DIM                    = 7;
    // A room whose interior holds at least this many tiles is a LARGE supply region (may host two groups, E3).
    public static final int LEVEL_GEN_LARGE_ROOM_INTERIOR_AREA            = 81;
    // Maximum Manhattan distance (room-center to room-center) for optional loop corridors.
    // Keeps loop connections local so they add shortcuts rather than crossing the entire dungeon.
    public static final int   LEVEL_GEN_LOOP_MAX_DISTANCE     = 25;

    // --- Room type system ---
    // LARGE is now a size MODIFIER (not a room type/blueprint): every non-entrance room
    // independently rolls a small chance to be stamped significantly larger than a standard
    // room, while keeping whatever blueprint styling it would have received anyway (Room.isLarge,
    // rolled in LevelGenerator.placeRooms() / LinearCorridorGenerator's side-room placement).
    // LEVEL_GEN_LARGE_MIN_DIM is reused as: (a) the floor for a large-modified room's interior
    // width AND height, and (b) the size gate for LARGE-class SIGNATURE rooms (e.g. POWER_PLANT).
    public static final int   LEVEL_GEN_LARGE_MIN_DIM              = 9;
    // Probability any non-entrance room independently rolls the large size modifier.
    // This is the intended vehicle for the big set-piece rooms (STELLAR_OBSERVATORY, POWER_PLANT,
    // COMMAND_CENTER) — see the STELLAR_OBSERVATORY size-gate note: the shared standard-room height
    // cap (LEVEL_GEN_ROOM_MAX_HEIGHT) never produces a 13x13+ near-square footprint, so those rooms
    // can only be hosted by a large-modified room. At the old 0.05 an oversized room was so rare that
    // the observatory appeared in ~1% of standard levels and 0% of linear levels; raised so a couple
    // of oversized rooms occur per level and the set-pieces have somewhere to live.
    public static final float LEVEL_GEN_LARGE_MODIFIER_CHANCE      = 0.16f;
    // The linear generator's ENTRANCE room is always a small room of at most this interior side
    // (balance-overhaul order 2, E5): from its centre the spine — and the floor's first fight — must be
    // within FIRST_CONTACT_FIRST_FLOOR_MAX_WALK_TILES walk tiles.
    public static final int   LEVEL_GEN_SPINE_ENTRANCE_MAX_DIM     = 7;
    // FOOTPRINT TARGETS (balance-overhaul order 2, E6): the combat generators build to a walkable-tile
    // target (BalanceConfig FOOTPRINT_*) by rebuilding at a corrected footprint SCALE. A build within
    // FOOTPRINT_AIM_FRACTION of the +/-15% tolerance stops the loop early; otherwise the closest of
    // FOOTPRINT_MAX_ATTEMPTS builds is kept. The NATURAL figures are each generator's typical walkable
    // tiles at scale 1 (measured) — the ORIGINAL sizes the E6 cut is taken from (BalanceConfig owns them).
    public static final int   FOOTPRINT_MAX_ATTEMPTS               = 24;
    public static final float FOOTPRINT_MIN_SCALE                  = 0.12f;
    public static final float FOOTPRINT_AIM_FRACTION               = 0.7f;
    public static final float FOOTPRINT_NATURAL_WALKABLE_ROOMS     = BalanceConfig.FOOTPRINT_ORIGINAL_WALKABLE_ROOMS;
    public static final float FOOTPRINT_NATURAL_WALKABLE_LINEAR    = BalanceConfig.FOOTPRINT_ORIGINAL_WALKABLE_LINEAR;
    public static final float FOOTPRINT_NATURAL_WALKABLE_CAVE      = BalanceConfig.FOOTPRINT_ORIGINAL_WALKABLE_CAVERN;
    // ROOMS_MST at a footprint scale: rooms are placed inside a centred window of sqrt(scale) of the grid on
    // each axis (never narrower than these), and the room count target scales with the area.
    public static final int   FOOTPRINT_MIN_WINDOW_WIDTH           = 24;
    public static final int   FOOTPRINT_MIN_WINDOW_HEIGHT          = 18;
    public static final int   FOOTPRINT_MIN_ROOMS                  = 5;
    // (balance-overhaul order 2) The tactical room-cap classifier and the random spawn-tile probes are
    // gone with the per-room budget: groups are placed one per room by level/EncounterPlacer, which reads
    // every room's spawnable tiles from the finished layout.

    // PACK COHERENCE IN SPACE (encounter-density-and-corpse-semantics). The planner forms chaff packs
    // because the golden-band chaff exemption assumes them, then the load balancer used to scatter each
    // member into a different room — measured mean distance to the nearest other enemy was ~14 tiles,
    // i.e. ~14 player turns, so every enemy was fought alone and the pack the pricing model assumes
    // never existed in play. A pack now lands in ONE room, clustered within this radius of its first
    // member, so the player meets groups.
    /** Max Chebyshev radius from a pack's first member that the rest of the pack may spawn within. Range: 2–5. */
    public static final int   LEVEL_GEN_PACK_CLUSTER_RADIUS = 3;

    // Probability a floor tile in a server room becomes a flickering tile.
    public static final float LEVEL_GEN_SERVER_FLICKER_CHANCE      = 0.12f;
    // Ratio of rack props that are lockers vs terminals in a server room.
    public static final float LEVEL_GEN_SERVER_LOCKER_RATIO        = 0.35f;
    // Boosted pickup chances for special rooms (loot hubs / set-piece arenas).
    // Base probability that any non-entrance room contains at least one ammo box pickup.

    // --- Wide hallway generation ---
    // Number of MST edges widened to 3-tile grand corridors per level.
    public static final int   LEVEL_GEN_WIDE_HALLWAY_COUNT          = 3;
    // Spine column spacing for wide-hall 'P' columns (place one every N eligible spine tiles).
    public static final int   LEVEL_GEN_WIDE_HALLWAY_COLUMN_SPACING = 3;

    // -------------------------------------------------------------------------
    // DEPTH-AWARE DIFFICULTY & LOOT GRADIENT
    // Each room's "depth" is its hop distance from the entrance over the MST room
    // tree, normalised to [0,1]. Rooms further from the spawn ramp up in enemy
    // count, enemy toughness, and loot richness, so exploration has a deliberate
    // difficulty/reward curve instead of a flat random spread.
    // -------------------------------------------------------------------------
    // Depth-aware difficulty/loot gradient — balance values live in BalanceConfig.
    // Maximum extra enemies a deepest-depth room may add on top of the base count.
    public static final int   LEVEL_GEN_DEPTH_ENEMY_BONUS_MAX        = BalanceConfig.LEVEL_GEN_DEPTH_ENEMY_BONUS_MAX;
    // At full depth, the chance a light/ranged spawn ('3'/'2') is upgraded to a
    // heavy archetype ('1' hulk / '4' brute / '5' wraith). Scales linearly with depth.
    public static final float LEVEL_GEN_DEPTH_ENEMY_UPGRADE_CHANCE   = BalanceConfig.LEVEL_GEN_DEPTH_ENEMY_UPGRADE_CHANCE;
    // Additive bonus to a room's medkit / ammo pickup chance at full depth.
    // At full depth, the chance a room receives a bonus second ammo box.

    // -------------------------------------------------------------------------
    // LOCK-AND-KEY GATING
    // After connectivity is verified, one plain door that is a true single-door cut
    // (a "bridge") is promoted to a keycard-locked door ('R'/'Y'/'B'). The matching
    // keycard pickup ('r'/'y'/'b') is placed in a room still reachable WITHOUT the
    // gate, and the level exit (stairs) is steered into the gated region — creating
    // the classic find-key → backtrack → unlock → progress loop. Reuses the existing
    // DoorManager keycard system; no new tile symbols are introduced.
    // -------------------------------------------------------------------------
    // Never gate more than this fraction of the non-entrance rooms, so the player
    // can always explore a meaningful portion of the level before needing the key.
    public static final float LEVEL_GEN_GATE_MAX_GATED_FRACTION      = 0.60f;

    // -------------------------------------------------------------------------
    // NEW ROOM TYPES — level generator configuration
    // -------------------------------------------------------------------------

    // RESEARCH_LAB — uncommon sci-fi set-piece; at most 1 per level
    public static final int   LEVEL_GEN_RESEARCH_LAB_SCORCH_MAX     = 5;

    // -------------------------------------------------------------------------
    // STELLAR_OBSERVATORY — true-circle rotunda landmark room. RARE set-piece; at most 1 per
    // level (assignRoomTypes() only ever attempts this once per generate() call). See the
    // design doc for the full spec: .claude/agents/ideas/stellar-observatory-gravity-well-room.txt
    // -------------------------------------------------------------------------

    // Circle carve geometry (GENERATOR ALGORITHM step 2). RADIUS_MARGIN keeps a 1-tile gap
    // between the rasterized circle and the interior bounds; MIN/MAX_RADIUS clamp the radius
    // to what the achievable room sizes above actually produce (R = 5..6 given the 13x13..14x14
    // size gate — see the size-gate note above).
    public static final int   LEVEL_GEN_STELLAR_OBSERVATORY_RADIUS_MARGIN  = 1;
    public static final int   LEVEL_GEN_STELLAR_OBSERVATORY_MIN_RADIUS     = 5;
    public static final int   LEVEL_GEN_STELLAR_OBSERVATORY_MAX_RADIUS     = 6;

    // Catwalk ring (step 4): Rc = round(R * ratio); a 1-tile-wide annulus (bandHalfWidth 0.5 —
    // do not widen; BALANCE NOTES: "the exposure is the point").
    public static final float LEVEL_GEN_STELLAR_OBSERVATORY_CATWALK_RATIO            = 0.5f;
    public static final float LEVEL_GEN_STELLAR_OBSERVATORY_CATWALK_BAND_HALF_WIDTH  = 0.5f;

    // Prop scatter (step 7) — solid-prop budget stays <= 6 total (pylons + table + crates +
    // the single core) per BALANCE NOTES, so the ring and inner disc are never sealed off.
    // PROP_CLEARANCE keeps placements off the ring/shell edges without eating the whole (very
    // narrow, R=5..6) outer annulus band.
    public static final int   LEVEL_GEN_STELLAR_OBSERVATORY_PYLON_MIN               = 2;
    public static final int   LEVEL_GEN_STELLAR_OBSERVATORY_PYLON_MAX               = 4;
    public static final float LEVEL_GEN_STELLAR_OBSERVATORY_HOLOTABLE_CHANCE        = 0.65f; // 0-1 tables
    public static final int   LEVEL_GEN_STELLAR_OBSERVATORY_CRATE_MIN               = 1;
    public static final int   LEVEL_GEN_STELLAR_OBSERVATORY_CRATE_MAX               = 3;
    public static final float LEVEL_GEN_STELLAR_OBSERVATORY_PROP_CLEARANCE          = 0.5f;
    public static final int   LEVEL_GEN_STELLAR_OBSERVATORY_PROP_PLACEMENT_ATTEMPTS = 30;

    // Decal pass (step 8): zero-g debris count.
    public static final int   LEVEL_GEN_STELLAR_OBSERVATORY_DEBRIS_MIN = 3;
    public static final int   LEVEL_GEN_STELLAR_OBSERVATORY_DEBRIS_MAX = 6;

    // Lighting (step 9): sparse unlit tiles in the far quadrant only; no 'f' flicker (the
    // room's signature is that it still works). FAR_QUADRANT_RADIANS is the minimum angular
    // distance from the (primary) entrance bearing before a tile counts as "far".
    public static final int   LEVEL_GEN_STELLAR_OBSERVATORY_UNLIT_MAX             = 2;
    public static final float LEVEL_GEN_STELLAR_OBSERVATORY_FAR_QUADRANT_RADIANS  = 2.0f;

    // -------------------------------------------------------------------------
    // LINEAR CORRIDOR generator (generator 2) — spine constants
    // -------------------------------------------------------------------------

    // Probability that the spine runs horizontally (east-west) vs vertically.
    public static final float LEVEL_GEN_SPINE_HORIZONTAL_CHANCE  = 0.70f;
    // Spine width in tiles (grand corridor — always odd so there is a clear centre spine).
    public static final int   LEVEL_GEN_SPINE_WIDTH               = 3;
    // Fraction of the grid's long dimension that the spine occupies.
    public static final float LEVEL_GEN_SPINE_LENGTH_MIN_FRAC     = 0.75f;
    public static final float LEVEL_GEN_SPINE_LENGTH_MAX_FRAC     = 0.90f;
    // Tile spacing between successive side-room slot positions along the spine.
    public static final int   LEVEL_GEN_SPINE_SIDE_STEP_MIN       = 6;
    public static final int   LEVEL_GEN_SPINE_SIDE_STEP_MAX       = 10;
    // Probability per slot of attempting to place a room on one side of the spine.
    public static final float LEVEL_GEN_SPINE_SIDE_ROOM_CHANCE    = 0.70f;

    // Spine BEND: a single perpendicular continuation carved from the primary spine's tail,
    // toward whichever side of the grid has more open space, carrying its own side rooms and
    // landmark via the same helpers the primary spine uses. Exists because the 80x45 grid is far
    // shorter tall than wide, so a vertical-only spine places roughly half as many rooms as a
    // horizontal one (~3.1 vs ~6.4 average, measured) — too few for the 3-special-room minimum to
    // reliably reach. Applied to both orientations for consistency. Skipped entirely when the
    // available space in the extend direction is below the minimum (rare edge case).
    public static final int   LEVEL_GEN_SPINE_BEND_MIN_LENGTH      = 15;
    public static final float LEVEL_GEN_SPINE_BEND_LENGTH_MIN_FRAC = 0.70f;
    public static final float LEVEL_GEN_SPINE_BEND_LENGTH_MAX_FRAC = 0.92f;

    // -------------------------------------------------------------------------
    // LINEAR CORRIDOR generator — side-room size & special-room enrichment
    // -------------------------------------------------------------------------
    // Side rooms branching off the spine. The upper bounds give an occasional room a bigger
    // footprint independent of the (now shared, registry-driven) room-type roll.

    // -------------------------------------------------------------------------
    // LINEAR CORRIDOR generator — interior architecture
    // Structural layouts that turn bare boxes into legible spaces using columns and
    // props. Applied to STANDARD and LARGE rooms. Every layout is built lane-safe:
    // it never seals the room centre, a doorway, the spawn, or the stairs.
    // -------------------------------------------------------------------------
    // Probability a qualifying STANDARD room receives a structural interior layout
    // (otherwise it falls back to the legacy sparse prop scatter).
    // Minimum interior dimension (both axes) for any structural interior layout.
    public static final int   LEVEL_GEN_INTERIOR_MIN_DIM            = 5;
    // Column spacing for centre-avenue / cross-spar layouts.
    public static final int   LEVEL_GEN_INTERIOR_COLUMN_SPACING     = 2;

    // -------------------------------------------------------------------------
    // LINEAR CORRIDOR generator — spine junction plaza
    // The spine is broken up by one widened open node so a long straight artery is
    // not monotonous: it reads as a crossroads / staging area framed by columns.
    // -------------------------------------------------------------------------
    public static final float LEVEL_GEN_SPINE_PLAZA_CHANCE          = 0.65f;
    // Half-extent (tiles) of the square opening carved around the plaza centre.
    public static final int   LEVEL_GEN_SPINE_PLAZA_RADIUS          = 2;

    // -------------------------------------------------------------------------
    // START ROOM generator — staging room for weapon selection at run start
    // -------------------------------------------------------------------------

    // Walkable interior tile count in each dimension (odd so there is a clear centre tile).
    public static final int     START_ROOM_INTERIOR_SIZE        = 13;
    // Number of ranged weapon offers presented to the player side-by-side near the portal.
    public static final int     START_ROOM_WEAPON_OFFER_COUNT   = 3;
    // Number of melee weapon offers shown south of the ranged row.
    public static final int     START_ROOM_MELEE_OFFER_COUNT    = 3;
    // Rows south of the ranged weapon row where the melee offer row is placed.
    public static final int     START_ROOM_MELEE_ROW_OFFSET     = 2;
    // When true, four decorative 'P' columns frame the portal like an armory altar.
    public static final boolean START_ROOM_USE_COLUMNS          = true;
    // Tile distance from the room centre at which the four framing columns are placed.
    public static final int     START_ROOM_COLUMN_OFFSET        = 4;
    // STAGING_ARMORY (level-generation-overhaul G8): the two north-wall weapon racks sit this many columns either side
    // of the portal (G8 asks for >= 2).
    public static final int     START_ROOM_RACK_PORTAL_OFFSET   = 3;

    // -------------------------------------------------------------------------
    // CAVERN generator (generator 3) — cellular automata constants
    // -------------------------------------------------------------------------

    // Initial probability that any interior cell is solid (wall) before smoothing.
    public static final float LEVEL_GEN_CAVE_FILL_PROBABILITY     = 0.45f;
    // Number of 4-5-rule CA smoothing passes.
    public static final int   LEVEL_GEN_CAVE_SMOOTH_PASSES        = 5;
    // Extra birth-only rounding passes after main smoothing to remove jagged spurs.
    public static final int   LEVEL_GEN_CAVE_FINAL_FILL_PASSES    = 1;
    // A cell with >= this many solid Moore neighbours becomes solid.
    public static final int   LEVEL_GEN_CAVE_BIRTH_LIMIT          = 5;
    // A cell with < this many solid Moore neighbours becomes floor.
    public static final int   LEVEL_GEN_CAVE_DEATH_LIMIT          = 4;
    // Floor regions smaller than this tile count are erased to wall mass.
    public static final int   LEVEL_GEN_CAVE_MIN_REGION_SIZE      = 14;
    // Safety cap on region-merge stitch attempts.
    public static final int   LEVEL_GEN_CAVE_MAX_STITCH_ATTEMPTS  = 12;
    // Probability per drunkard-walk step that the step moves toward the target.
    public static final float LEVEL_GEN_CAVE_TUNNEL_STRAIGHTNESS  = 0.70f;

    // Walled chambers stamped into the cave (level-generation-overhaul G3): the planned specials first, then the
    // entrance, then 0..REGULAR_CHAMBERS_MAX regular chambers sized from the CHAMBER_* window below. Each chamber
    // samples up to CHAMBER_TRIES positions.
    public static final int   LEVEL_GEN_CAVE_REGULAR_CHAMBERS_MAX = 2;
    public static final int   LEVEL_GEN_CAVE_CHAMBER_TRIES        = 40;
    // Each chamber gets SOCKETS_MIN..MAX sockets on the sides nearest cave floor, each joined to it by a straight
    // 1-wide tunnel of at most SOCKET_TUNNEL_MAX carved tiles.
    public static final int   LEVEL_GEN_CAVE_SOCKETS_MIN          = 1;
    public static final int   LEVEL_GEN_CAVE_SOCKETS_MAX          = 3;
    public static final int   LEVEL_GEN_CAVE_SOCKET_TUNNEL_MAX    = 6;
    // The regular chamber a cave must hold when no planned special's kit places a terminal (R20): at least this
    // interior on each side, one socket, so the OFFICE kit always finds a wall for its terminal.
    public static final int   LEVEL_GEN_CAVE_TERMINAL_CHAMBER_MIN_DIM = 5;
    // Seed salt for the bypass tunnels that rejoin cave the forced shells cut off (R9).
    public static final long  LEVEL_GEN_CAVE_BYPASS_SEED_SALT     = 0x5C0FFEE1L;
    public static final int   LEVEL_GEN_CAVE_CHAMBER_MIN_WIDTH    = 4;
    public static final int   LEVEL_GEN_CAVE_CHAMBER_MAX_WIDTH    = 7;
    public static final int   LEVEL_GEN_CAVE_CHAMBER_MIN_HEIGHT   = 4;
    public static final int   LEVEL_GEN_CAVE_CHAMBER_MAX_HEIGHT   = 6;
    // Minimum fraction of the chamber footprint that must already be cave floor.
    public static final float LEVEL_GEN_CAVE_CHAMBER_FLOOR_OVERLAP  = 0.65f;

    // Total budget of flickering floor tiles in the entire cave.
    public static final int   LEVEL_GEN_CAVE_FLICKER_BUDGET         = 6;

    // Probability per cave floor tile of receiving a decal (blood, oil, scorch, corpse).
    public static final float LEVEL_GEN_CAVE_DECAL_CHANCE           = 0.05f;
    // Cave body solids (G3) come from two rules only. FORMATIONS: 'P' stalagmite clusters of 1..CLUSTER_MAX, only
    // where the 5x5 window holds >= MIN_OPEN open tiles, never within SOCKET_DISTANCE of a socket, at most
    // PER_BIOME_MAX clusters per biome (x GROTTO_MULTIPLIER in a crystal grotto). DEBRIS: the biome feature
    // clusters below, 1..DEBRIS_CLUSTER_MAX props flush against cave wall. Neither may sit where the open width
    // across it, on either axis, is under MIN_PASSAGE_WIDTH.
    public static final int   LEVEL_GEN_CAVE_FORMATION_MIN_OPEN       = 20;
    public static final int   LEVEL_GEN_CAVE_FORMATIONS_PER_BIOME_MAX = 3;
    public static final int   LEVEL_GEN_CAVE_FORMATION_GROTTO_MULTIPLIER = 2;
    public static final int   LEVEL_GEN_CAVE_FORMATION_CLUSTER_MAX    = 3;
    public static final int   LEVEL_GEN_CAVE_FORMATION_SOCKET_DISTANCE = 3;
    public static final int   LEVEL_GEN_CAVE_DEBRIS_CLUSTER_MAX       = 3;
    public static final int   LEVEL_GEN_CAVE_SOLID_MIN_PASSAGE_WIDTH  = 3;
    // Placement attempts per formation / debris cluster before it is skipped.
    public static final int   LEVEL_GEN_CAVE_SOLID_CLUSTER_TRIES      = 60;
    // Minimum Chebyshev distance from player spawn at which cave-body enemies may appear.
    public static final int   LEVEL_GEN_CAVE_SPAWN_SAFE_RADIUS      = 6;
    // Supply regions in a cave (balance-overhaul order 2): the open cave body is cut into fixed pockets
    // of this size so the shared supply placement spreads pickups across the cave (S7) rather than
    // treating the whole cave as one room.
    public static final int   LEVEL_GEN_CAVE_SUPPLY_POCKET_WIDTH    = 12;
    public static final int   LEVEL_GEN_CAVE_SUPPLY_POCKET_HEIGHT   = 9;
    // A stamped chamber with at least this interior area counts as LARGE (it may host two groups, E3).
    public static final int   LEVEL_GEN_CAVE_LARGE_CHAMBER_AREA     = 48;
    // Supply pockets for the bespoke single-chamber generators (boss arena, clinic, event room, airlock):
    // square pockets of this side, each its own supply region (balance-overhaul order 2, S7).
    public static final int   SPECIAL_ROOM_SUPPLY_POCKET_SIZE       = 6;

    // -------------------------------------------------------------------------
    // CAVERN generator — sci-fi biome regions
    // -------------------------------------------------------------------------
    // The open cave body is partitioned into contiguous biome regions by a
    // multi-source flood fill from farthest-point seed tiles. Each region reskins
    // its own walls, floor lighting, decals, scattered props and enemy mix, so a
    // single cavern reads as several distinct sci-fi environments fused together
    // (fungal hollow, frost vault, reactor sump, derelict data nest, gore warren,
    // mineral grotto).

    // Number of distinct biome regions seeded into one cave level.
    public static final int   LEVEL_GEN_CAVE_BIOME_MIN              = 3;
    public static final int   LEVEL_GEN_CAVE_BIOME_MAX              = 5;
    // Debris clusters (pod nests, barrel piles, console banks) stamped per biome, flush against cave wall (G3).
    public static final int   LEVEL_GEN_CAVE_BIOME_FEATURE_MIN      = 1;
    public static final int   LEVEL_GEN_CAVE_BIOME_FEATURE_MAX      = 2;
    // Probability a feature cluster upgrades its anchor to a rare set-piece prop.
    public static final float LEVEL_GEN_CAVE_BIOME_LANDMARK_CHANCE  = 0.45f;
    // Minimum Chebyshev distance from player spawn before any solid biome prop appears.
    public static final int   LEVEL_GEN_CAVE_BIOME_PROP_SAFE_RADIUS = 4;

    // -------------------------------------------------------------------------
    // MED-BAY generator (route-map order-8) — the curated REST-node clinic
    // -------------------------------------------------------------------------
    // A tiny hand-feeling clinic: one approach corridor -> a central ward holding the
    // auto-doc heal station -> 1-2 cryo-pod alcoves -> an exit past the auto-doc. The
    // layout is fixed-ish and deterministic from the floor seed (only the alcove side and
    // pod count vary), so the sanctuary reads the same calm way every time. All tiles come
    // from docs/tile-symbols.txt; no new symbols. Emits ZERO enemy spawn points (the honest
    // REST node is always safe — the map's icon language must stay trustworthy).

    // Central ward footprint (walkable floor rectangle), centred on the grid.
    public static final int   MED_BAY_WARD_WIDTH        = 16;
    public static final int   MED_BAY_WARD_HEIGHT       = 10;
    // 3-tile-wide approach corridor length, running left into the ward's mid row.
    public static final int   MED_BAY_APPROACH_LENGTH   = 14;
    public static final int   MED_BAY_APPROACH_HALF_WIDTH = 1; // 2*half+1 = 3 tiles wide
    // Square cryo-pod alcove carved off the ward (top and/or bottom), floor side length.
    public static final int   MED_BAY_ALCOVE_SIZE       = 4;

    // -------------------------------------------------------------------------
    // EVENT ROOM (route-map order-10) — the bespoke small curated narrative room
    // -------------------------------------------------------------------------
    // A tight chamber: a short approach corridor feeds a small room with a single central
    // interactable ('W' holo-workstation), and the exit sits a couple of tiles PAST it so the beat is
    // on the natural path out. Near-empty of enemies — a story beat, not a fight.
    public static final int   EVENT_ROOM_WIDTH          = 12;
    public static final int   EVENT_ROOM_HEIGHT         = 9;
    public static final int   EVENT_ROOM_APPROACH_LENGTH = 10;
    public static final int   EVENT_ROOM_APPROACH_HALF_WIDTH = 1; // 2*half+1 = 3 tiles wide

    // -------------------------------------------------------------------------
    // GATE AIRLOCK (route-map order-10) — the ceremonial region-boundary bulkhead floor
    // -------------------------------------------------------------------------
    // A short reverent walk: an approach corridor, a keycard-gated 'B' blue-door airlock, then a
    // threshold room where the overlay announces the new region, and the exit just past it.
    public static final int   GATE_APPROACH_LENGTH      = 12;
    public static final int   GATE_APPROACH_HALF_WIDTH  = 1; // 2*half+1 = 3 tiles wide
    public static final int   GATE_THRESHOLD_WIDTH      = 11;
    public static final int   GATE_THRESHOLD_HEIGHT     = 9;

    // -------------------------------------------------------------------------
    // BOSS ARENA (boss ORDER 7) — the procedural, spacious duel arena
    // -------------------------------------------------------------------------
    // Replaces the old fixed 3-wide corridor + rigid 2x2-column box. A large open core (so the boss
    // can DASH/CHARGE its full range and the player can circle-strafe), asymmetric scattered cover,
    // recessed heal alcoves, a wide antechamber with ONE lockable door, and mood lighting. Seeded from
    // the floor seed so a run's arena is reproducible. A reachability + fairness pass rejects any cover
    // roll that strands a key tile, creates a 1-wide interior chokepoint, or opens a dead-end; it
    // re-rolls up to BOSS_ARENA_MAX_LAYOUT_ATTEMPTS times, then falls back to the open room. Every tile
    // is from docs/tile-symbols.txt; no new symbol.

    // Open-core footprint bounds (walkable interior; randomised per run within these inclusive ranges).
    // Minimums keep the interior well over 16x16 so the dash (4) and charge (5) ranges fit with room to
    // kite; maximums keep the room inside the 80x45 grid with wall margins.
    public static final int   BOSS_ARENA_MIN_WIDTH        = 22;
    public static final int   BOSS_ARENA_MAX_WIDTH        = 30;
    public static final int   BOSS_ARENA_MIN_HEIGHT       = 22;
    public static final int   BOSS_ARENA_MAX_HEIGHT       = 28;
    // The open core's top interior row (one ring of perimeter wall sits above it).
    public static final int   BOSS_ARENA_TOP_MARGIN       = 2;
    // Wide entrance antechamber below the arena (player starts here; ONE door joins it to the arena).
    public static final int   BOSS_ARENA_ANTECHAMBER_WIDTH  = 7;
    public static final int   BOSS_ARENA_ANTECHAMBER_HEIGHT = 5;
    // Boss spawns this many rows below the arena's top interior row (near the far side from the door).
    public static final int   BOSS_ARENA_BOSS_TOP_OFFSET  = 3;
    // Scattered asymmetric cover columns ('P'): count randomised in this inclusive range.
    public static final int   BOSS_ARENA_MIN_COVER        = 6;
    public static final int   BOSS_ARENA_MAX_COVER        = 10;
    // A cover column keeps this Chebyshev clearance from walls, key tiles, and other columns so no
    // 1-wide pinch forms and the entry/boss/exit lanes stay open (fairness F5).
    public static final int   BOSS_ARENA_COVER_CLEARANCE  = 2;
    // Radius (Chebyshev) of the protected zones around the door mouth, boss, and exit.
    public static final int   BOSS_ARENA_KEEP_CLEAR_RADIUS = 3;
    // Recessed heal alcoves ('the boss retreats to repair'): pockets carved into the side walls.
    public static final int   BOSS_ARENA_ALCOVE_DEPTH     = 2;
    public static final int   BOSS_ARENA_ALCOVE_HEIGHT    = 2;
    // Explosive barrels ('E') dropped near the arena walls as a player tool (max; may place fewer).
    public static final int   BOSS_ARENA_MAX_BARRELS      = 2;
    // Bounded re-rolls of the cover/barrel scatter before falling back to a bare open room.
    public static final int   BOSS_ARENA_MAX_LAYOUT_ATTEMPTS = 48;
    // Minimum distinct open cardinal escape routes required from the arena centre (fairness F5).
    public static final int   BOSS_ARENA_MIN_ESCAPE_ROUTES = 2;

    // Salvage-bay interior tuning (all rates seeded through the generator's Random).
    public static final float LEVEL_GEN_SALVAGE_DARK_FLOOR_CHANCE   = 0.30f; // dingy 'u' patches over the 'l' floor

    // Gore-nest interior tuning (all rates seeded through the generator's Random).
    public static final float LEVEL_GEN_GORE_NEST_DECAL_CHANCE      = 0.35f; // per-tile corpse/blood/bile carpet density
    public static final float LEVEL_GEN_GORE_NEST_DARK_FLOOR_CHANCE = 0.55f; // unlit 'u' bias over the dim 'l' floor (dread)
    public static final float LEVEL_GEN_GORE_NEST_FLICKER_CHANCE    = 0.10f; // flickering 'f' at the flesh seams

    // Atmospheric-plant interior tuning (all scatter rates seeded through the generator's Random; the
    // signature look is guaranteed by deterministic minimums so every seed stamps the full demand set).
    public static final float LEVEL_GEN_ATMO_PLANT_SCORCH_CHANCE    = 0.20f; // 'e' scorch scatter near the machinery

    // ── REGION WALL SETS: THE PLACEMENT RULES P1-P9 (generated-wall-art order 2; guide section 7) ──
    // The LevelFinisher dresses every floor's walls by rule on its OWN seeded stream (GameMath.wallSetSeed),
    // never the generator's, so layout, spawns and supply are untouched (R1). Only exposed walls (a walkable
    // 4-neighbour) are dressed and only a wall symbol ever replaces a wall symbol (P8).
    /** P2: a room's ACCENT appears on this many tiles at least (the wall facing the entrance first). */
    public static final int   WALL_SET_ROOM_ACCENT_MIN          = 1;
    /** P2: ... and at most this many. */
    public static final int   WALL_SET_ROOM_ACCENT_MAX          = 3;
    /** Edge case: a room whose interior is narrower than this gets at most one ACCENT and no P4 rhythm. */
    public static final int   WALL_SET_TINY_ROOM_INTERIOR       = 3;
    /** P4: a straight exposed hall run this long or longer gets the STRUCTURE rhythm. */
    public static final int   WALL_SET_RHYTHM_MIN_RUN           = 5;
    /** P4: every Nth tile of such a run is STRUCTURE (phase seeded per run). */
    public static final int   WALL_SET_RHYTHM_PERIOD            = 4;
    /** P5: no exposed hall run of this many tiles without a LIGHT. */
    public static final int   WALL_SET_LIGHT_MAX_GAP            = 7;
    /** P6: SERVICE / VENT cover this fraction of the remaining hall base, at least ... */
    public static final float WALL_SET_VARIETY_MIN_FRACTION     = 0.06f;
    /** P6: ... and at most. */
    public static final float WALL_SET_VARIETY_MAX_FRACTION     = 0.10f;
    /** P6 / P7: a variety or breach cluster is 1..this many tiles along a run. */
    public static final int   WALL_SET_CLUSTER_MAX              = 3;
    /** P6: MARKER walls per floor, at least ... */
    public static final int   WALL_SET_MARKER_MIN               = 1;
    /** P6: ... and at most (one near the arrival point, one near the exit, then anywhere). */
    public static final int   WALL_SET_MARKER_MAX               = 3;
    /** P6: "near" the arrival point / the exit, in tiles (Chebyshev). */
    public static final int   WALL_SET_MARKER_NEAR_TILES        = 6;
    /** P3: caverns weight BREACH up by this much hall density on top of P7. */
    public static final float WALL_SET_CAVERN_BREACH_BONUS      = 0.04f;
    /** P9: two FOCAL walls keep at least this many tiles between them along a run (never adjacent). */
    public static final int   WALL_SET_FOCAL_MIN_GAP            = 2;
    /** P9: the SAME focal wall keeps at least this many tiles between copies. */
    public static final int   WALL_SET_FOCAL_SAME_MIN_GAP       = 4;
    /** P7 region A (outer_facility): hall breach density on the region's first floor (manifest "breach.base"). */
    public static final float WALL_SET_A_BREACH_BASE            = 0.0f;
    /** P7 region A: density added per floor into the region (manifest "breach.step"). */
    public static final float WALL_SET_A_BREACH_STEP            = 0.03f;
}
