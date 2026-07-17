package ge.tbegvadze.toon3d.util;

/** Procedural level generation constants — grid, room types, corridors, wall distribution. */
public final class LevelGenConstants {

    private LevelGenConstants() {}

    // Grid dimensions match the 80×45 tile layout that fills the 1280×720 world exactly.
    public static final int   LEVEL_GEN_GRID_WIDTH            = Constants.WORLD_WIDTH  / Constants.CELL_SIZE; // 80
    public static final int   LEVEL_GEN_GRID_HEIGHT           = Constants.WORLD_HEIGHT / Constants.CELL_SIZE; // 45
    // Interior tile count, excluding the 1-tile perimeter wall on each side.
    public static final int   LEVEL_GEN_ROOM_MIN_WIDTH        = 3;
    public static final int   LEVEL_GEN_ROOM_MIN_HEIGHT       = 3;
    public static final int   LEVEL_GEN_ROOM_MAX_WIDTH        = 20;
    public static final int   LEVEL_GEN_ROOM_MAX_HEIGHT       = 14;
    // Minimum gap between room bounding boxes so rooms never share a wall tile.
    public static final int   LEVEL_GEN_ROOM_MARGIN           = 2;
    public static final int   LEVEL_GEN_TARGET_ROOMS          = 16;
    public static final int   LEVEL_GEN_PLACEMENT_TRIES       = 600;
    // Probability that any interior floor tile in a non-entrance room receives a prop.
    // Balance values (loot/enemy density) live in BalanceConfig (SINGLE SOURCE OF TRUTH).
    public static final float LEVEL_GEN_PROP_CHANCE           = BalanceConfig.LEVEL_GEN_PROP_CHANCE;
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
    // Probability that a wall tile adjacent to an explosive barrel ('E') becomes a hazard wall ('h').
    public static final float LEVEL_GEN_HAZARD_WALL_CHANCE    = 0.45f;
    // Maximum Manhattan distance (room-center to room-center) for optional loop corridors.
    // Keeps loop connections local so they add shortcuts rather than crossing the entire dungeon.
    public static final int   LEVEL_GEN_LOOP_MAX_DISTANCE     = 25;
    // Guaranteed floor: at least this many non-entrance rooms roll a SPECIAL blueprint (any id
    // other than "entrance"/"standard" — themed SIGNATURE rooms plus GENERIC flavour rooms like
    // SALVAGE_BAY/SUPPLY_CACHE). The normal seeded weighted roll can naturally exceed this count;
    // a backstop pass only ever ADDS specials when the roll comes up short, never removes any.
    public static final int   LEVEL_GEN_MIN_SPECIAL_ROOMS     = 3;

    // --- Room type system ---
    // LARGE is now a size MODIFIER (not a room type/blueprint): every non-entrance room
    // independently rolls a small chance to be stamped significantly larger than a standard
    // room, while keeping whatever blueprint styling it would have received anyway (Room.isLarge,
    // rolled in LevelGenerator.placeRooms() / LinearCorridorGenerator's side-room placement).
    // LEVEL_GEN_LARGE_MIN_DIM is reused as: (a) the floor for a large-modified room's interior
    // width AND height, and (b) the size gate for LARGE-class SIGNATURE rooms (e.g. POWER_PLANT).
    public static final int   LEVEL_GEN_LARGE_MIN_DIM              = 9;
    // Probability any non-entrance room independently rolls the large size modifier.
    public static final float LEVEL_GEN_LARGE_MODIFIER_CHANCE      = 0.05f;
    // Ceiling for a large-modified room's interior size — deliberately well above the standard
    // room max (LEVEL_GEN_ROOM_MAX_WIDTH/HEIGHT) so the modifier reads as genuinely oversized.
    public static final int   LEVEL_GEN_LARGE_MODIFIER_MAX_WIDTH   = 28;
    public static final int   LEVEL_GEN_LARGE_MODIFIER_MAX_HEIGHT  = 20;
    // Probability that a SERVER_ROOM perimeter wall tile is converted to terminal wall 't'.
    public static final float LEVEL_GEN_SERVER_WALL_TERMINAL_CHANCE = 0.80f;
    // Probability that any small/medium room becomes a SERVER_ROOM (data vault).
    public static final float LEVEL_GEN_SERVER_ROOM_CHANCE         = 0.18f;
    // Hard cap: at most this many SERVER_ROOMs per level so they feel special.
    public static final int   LEVEL_GEN_SERVER_ROOM_MAX_PER_LEVEL  = 2;
    // Probability a floor tile in a server room becomes a flickering tile.
    public static final float LEVEL_GEN_SERVER_FLICKER_CHANCE      = 0.12f;
    // Ratio of rack props that are lockers vs terminals in a server room.
    public static final float LEVEL_GEN_SERVER_LOCKER_RATIO        = 0.30f;
    // Boosted pickup chances for special rooms (loot hubs / set-piece arenas).
    // Loot room budgets are balance values — see BalanceConfig (SINGLE SOURCE OF TRUTH).
    public static final float LEVEL_GEN_SERVER_MEDKIT_CHANCE       = BalanceConfig.LEVEL_GEN_SERVER_MEDKIT_CHANCE;
    public static final float LEVEL_GEN_SERVER_ARMOUR_CHANCE       = BalanceConfig.LEVEL_GEN_SERVER_ARMOUR_CHANCE;
    public static final float LEVEL_GEN_LARGE_MEDKIT_CHANCE        = BalanceConfig.LEVEL_GEN_LARGE_MEDKIT_CHANCE;
    public static final float LEVEL_GEN_LARGE_ARMOUR_CHANCE        = BalanceConfig.LEVEL_GEN_LARGE_ARMOUR_CHANCE;
    // Base probability that any non-entrance room contains at least one ammo box pickup.
    // Balance value — see BalanceConfig (SINGLE SOURCE OF TRUTH).
    public static final float LEVEL_GEN_AMMO_CHANCE_PER_ROOM       = BalanceConfig.LEVEL_GEN_AMMO_CHANCE_PER_ROOM;

    // --- Wide hallway generation ---
    // Number of MST edges widened to 3-tile grand corridors per level.
    public static final int   LEVEL_GEN_WIDE_HALLWAY_COUNT          = 3;
    // Spine column spacing for wide-hall 'P' columns (place one every N eligible spine tiles).
    public static final int   LEVEL_GEN_WIDE_HALLWAY_COLUMN_SPACING = 3;

    // Procedural wall placement chances — post-pass reskin probability for new atmospheric walls.
    public static final float LEVEL_GEN_RUST_WALL_CHANCE      = 0.60f; // 'x' near unlit tiles
    public static final float LEVEL_GEN_RUST_OIL_CHANCE       = 0.40f; // 'x' near oil/blood decals
    public static final float LEVEL_GEN_GORE_WALL_CHANCE      = 0.35f; // 'x' near enemy dens / corpses

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
    public static final float LEVEL_GEN_DEPTH_MEDKIT_BONUS           = BalanceConfig.LEVEL_GEN_DEPTH_MEDKIT_BONUS;
    public static final float LEVEL_GEN_DEPTH_AMMO_BONUS             = BalanceConfig.LEVEL_GEN_DEPTH_AMMO_BONUS;
    // At full depth, the chance a room receives a bonus second ammo box.
    public static final float LEVEL_GEN_DEPTH_EXTRA_AMMO_CHANCE      = BalanceConfig.LEVEL_GEN_DEPTH_EXTRA_AMMO_CHANCE;

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

    // MEDICAL_BAY — guaranteed once per level (reliable heal stop)
    public static final int   LEVEL_GEN_MEDICAL_BAY_MIN_WIDTH    = 7;
    public static final int   LEVEL_GEN_MEDICAL_BAY_MIN_HEIGHT   = 6;
    public static final float LEVEL_GEN_MEDICAL_WALL_CHANCE      = 0.70f; // 'M' dominant
    public static final float LEVEL_GEN_MEDICAL_BIO_WALL_CHANCE  = 0.10f; // 'Q' accent
    public static final float LEVEL_GEN_MEDICAL_PROP_CHANCE      = 0.16f;
    public static final int   LEVEL_GEN_MEDICAL_BAY_MAX          = 1;

    // ARMORY — present in ~80% of levels, at most once
    public static final float LEVEL_GEN_ARMORY_CHANCE            = 0.80f;
    public static final int   LEVEL_GEN_ARMORY_MIN_WIDTH         = 6;
    public static final int   LEVEL_GEN_ARMORY_MIN_HEIGHT        = 6;
    public static final float LEVEL_GEN_ARMORY_BLAST_WALL_CHANCE = 0.50f; // 'X' dominant
    public static final float LEVEL_GEN_ARMORY_PROP_CHANCE       = 0.22f;
    public static final int   LEVEL_GEN_ARMORY_MIN_WEAPON_RACKS  = 2;
    public static final int   LEVEL_GEN_ARMORY_MAX               = 1;
    // Probability that a LARGE room (when no ARMORY weapon was placed) gets a weapon pickup
    public static final float LEVEL_GEN_LARGE_WEAPON_CHANCE      = BalanceConfig.LEVEL_GEN_LARGE_WEAPON_CHANCE;
    // Probability that a STANDARD room gets a random weapon spawn.
    public static final float LEVEL_GEN_RANDOM_ROOM_WEAPON_CHANCE = BalanceConfig.LEVEL_GEN_RANDOM_ROOM_WEAPON_CHANCE;

    // CRYO_CHAMBER — ~25% of levels, at most 2
    public static final float LEVEL_GEN_CRYO_CHANCE              = 0.25f;
    public static final int   LEVEL_GEN_CRYO_MIN_WIDTH           = 7;
    public static final int   LEVEL_GEN_CRYO_MIN_HEIGHT          = 7;
    public static final float LEVEL_GEN_CRYO_WALL_CHANCE         = 0.70f; // 'Z' dominant
    public static final float LEVEL_GEN_CRYO_GLASS_WALL_CHANCE   = 0.15f; // 'N' accent
    public static final float LEVEL_GEN_CRYO_PROP_CHANCE         = 0.20f;
    public static final int   LEVEL_GEN_CRYO_MAX                 = 2;

    // POWER_PLANT — ~45% of LARGE-eligible rooms, at most 1
    public static final float LEVEL_GEN_POWERPLANT_CHANCE        = 0.45f;
    public static final float LEVEL_GEN_POWERPLANT_RAD_WALL_CHANCE = 0.40f; // 'U' near core
    public static final float LEVEL_GEN_POWERPLANT_EMERG_WALL_CHANCE = 0.20f; // 'S' approach
    public static final float LEVEL_GEN_POWERPLANT_PROP_CHANCE   = 0.18f;
    public static final int   LEVEL_GEN_POWERPLANT_MIN_GENERATORS = 2;
    public static final int   LEVEL_GEN_POWERPLANT_MAX_GENERATORS = 4;
    public static final int   LEVEL_GEN_POWERPLANT_MAX           = 1;

    // COMMAND_CENTER — ~50% of levels, deepest large room, at most 1
    public static final float LEVEL_GEN_COMMAND_CHANCE           = 0.50f;
    public static final int   LEVEL_GEN_COMMAND_MIN_WIDTH        = 8;
    public static final int   LEVEL_GEN_COMMAND_MIN_HEIGHT       = 7;
    public static final float LEVEL_GEN_COMMAND_GLASS_WALL_CHANCE = 0.30f; // 'N' one wall band
    public static final float LEVEL_GEN_COMMAND_EMERG_WALL_CHANCE = 0.15f; // 'S' accent
    public static final float LEVEL_GEN_COMMAND_PROP_CHANCE      = 0.15f;
    public static final int   LEVEL_GEN_COMMAND_MIN_TERMINALS    = 3;
    public static final int   LEVEL_GEN_COMMAND_MAX_TERMINALS    = 5;
    public static final int   LEVEL_GEN_COMMAND_MAX              = 1;

    // CONTAINMENT_BLOCK — ~16% of levels, at most 2
    public static final float LEVEL_GEN_CONTAINMENT_CHANCE       = 0.16f;
    public static final int   LEVEL_GEN_CONTAINMENT_MIN_WIDTH    = 8;
    public static final int   LEVEL_GEN_CONTAINMENT_MIN_HEIGHT   = 6;
    public static final float LEVEL_GEN_CONTAINMENT_GLASS_CHANCE = 0.40f; // 'N' cell fronts
    public static final float LEVEL_GEN_CONTAINMENT_BIO_CHANCE   = 0.15f; // 'Q' approach
    public static final float LEVEL_GEN_CONTAINMENT_PROP_CHANCE  = 0.18f;
    public static final int   LEVEL_GEN_CONTAINMENT_MAX          = 2;

    // RESEARCH_LAB — uncommon sci-fi set-piece; at most 1 per level
    public static final float LEVEL_GEN_RESEARCH_LAB_CHANCE         = 0.40f;
    public static final int   LEVEL_GEN_RESEARCH_LAB_MIN_WIDTH      = 6;
    public static final int   LEVEL_GEN_RESEARCH_LAB_MIN_HEIGHT     = 5;
    public static final float LEVEL_GEN_RESEARCH_LAB_HOLO_WALL_CHANCE = 0.55f; // 'D' far-wall band
    public static final float LEVEL_GEN_RESEARCH_LAB_FIELD_WALL_CHANCE = 0.80f; // 'F' alcove barrier (placed directly)
    public static final int   LEVEL_GEN_RESEARCH_LAB_MIN_TANKS      = 2;
    public static final int   LEVEL_GEN_RESEARCH_LAB_MAX_TANKS      = 4;
    public static final float LEVEL_GEN_RESEARCH_LAB_CRACKED_CHANCE = 0.30f; // per 'I' tank: cracked variant
    public static final int   LEVEL_GEN_RESEARCH_LAB_SCORCH_MIN     = 2;
    public static final int   LEVEL_GEN_RESEARCH_LAB_SCORCH_MAX     = 5;
    public static final int   LEVEL_GEN_RESEARCH_LAB_MAX            = 1;

    // -------------------------------------------------------------------------
    // STELLAR_OBSERVATORY — true-circle rotunda landmark room. RARE set-piece; at most 1 per
    // level (assignRoomTypes() only ever attempts this once per generate() call). See the
    // design doc for the full spec: .claude/agents/ideas/stellar-observatory-gravity-well-room.txt
    // -------------------------------------------------------------------------

    // Rarity / depth gate (design doc PLACEMENT RULES: LOW weight, floors 3+).
    public static final float LEVEL_GEN_STELLAR_OBSERVATORY_CHANCE          = 0.14f;
    public static final int   LEVEL_GEN_STELLAR_OBSERVATORY_MIN_DEPTH       = 3;

    // Size gate: near-square interior, minimum 13x13 (design doc GENERATOR ALGORITHM step 1).
    // NOTE: the design doc's target radius (R = 6..8, from 15x15/17x17 interiors) assumes rooms
    // up to 17 tiles tall, but LEVEL_GEN_ROOM_MAX_HEIGHT caps every room's interior height at 14
    // — so a 15x15+ interior is never produced by placeRooms(). Rather than raising that shared
    // cap (which would perturb every other room type's size balance), the observatory uses the
    // biggest near-square footprint the existing cap allows; see MIN_RADIUS/MAX_RADIUS below.
    public static final int   LEVEL_GEN_STELLAR_OBSERVATORY_MIN_INTERIOR   = 13;
    public static final float LEVEL_GEN_STELLAR_OBSERVATORY_MAX_ASPECT    = 1.25f;
    // Minimum Manhattan room-centre distance from the Research Lab landmark (tonal-opposite
    // spacing rule from PLACEMENT RULES). The doc's other spacing partners — boss arena,
    // shop/safe room, EXCAVATION_SITE — are route-level or sibling-doc concepts that don't
    // exist as a RoomType in this generator, so they have no analogous check here.
    public static final int   LEVEL_GEN_STELLAR_OBSERVATORY_LANDMARK_SPACING = 20;

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

    // Shell variety (step 3): angular half-widths (radians) of the viewport-dome hero arc
    // (opposite the entrance) and the two magrail-conduit side arcs; everything else in the
    // SHELL ring stays the default hull-plate wall. DOOR_KEEPOUT_RADIANS excludes ring tiles
    // near any entrance bearing from hero/magrail reskinning, so a door's immediate neighbours
    // are always a clean opening (never converted away from it).
    public static final float LEVEL_GEN_STELLAR_OBSERVATORY_HERO_ARC_HALF_WIDTH_RADIANS    = 0.35f;
    public static final float LEVEL_GEN_STELLAR_OBSERVATORY_MAGRAIL_ARC_HALF_WIDTH_RADIANS  = 0.20f;
    public static final float LEVEL_GEN_STELLAR_OBSERVATORY_DOOR_KEEPOUT_RADIANS            = 0.18f;

    // Radial spokes (step 6): always 4 (cardinal directions).
    public static final int   LEVEL_GEN_STELLAR_OBSERVATORY_SPOKE_COUNT = 4;

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

    // Global accent wall chances (post-pass, any room type)
    public static final float LEVEL_GEN_EMERG_STRIP_CORRIDOR_CHANCE = 0.25f; // 'S' near keycard doors
    public static final float LEVEL_GEN_BLAST_NEAR_CORPSE_CHANCE    = 0.10f; // 'X' near corpse clusters

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

    // --- Spine wall character cumulative distribution (checked in order; remainder → 'x') ---
    public static final float LEVEL_GEN_SPINE_WALL_CONDUIT_CHANCE          = 0.30f; // 'c'
    public static final float LEVEL_GEN_SPINE_WALL_VENT_CUMULATIVE         = 0.50f; // 'v'
    public static final float LEVEL_GEN_SPINE_WALL_DAMAGED_CUMULATIVE      = 0.62f; // 'w'
    public static final float LEVEL_GEN_SPINE_WALL_HAZARD_CUMULATIVE       = 0.70f; // 'h'
    public static final float LEVEL_GEN_SPINE_WALL_TECH_CUMULATIVE         = 0.75f; // 't'

    // -------------------------------------------------------------------------
    // LINEAR CORRIDOR generator — side-room size & special-room enrichment
    // -------------------------------------------------------------------------
    // Side rooms branching off the spine. The upper bounds give an occasional room a bigger
    // footprint independent of the (now shared, registry-driven) room-type roll.
    public static final int   LEVEL_GEN_SPINE_ROOM_MIN_WIDTH        = 4;
    public static final int   LEVEL_GEN_SPINE_ROOM_MAX_WIDTH        = 12;
    public static final int   LEVEL_GEN_SPINE_ROOM_MIN_HEIGHT       = 4;
    public static final int   LEVEL_GEN_SPINE_ROOM_MAX_HEIGHT       = 11;
    // Probability a side-room slot rolls for the oversized size band (independent of, and rolled
    // before, the LEVEL_GEN_LARGE_MODIFIER_CHANCE size modifier — see LevelGenConstants room type
    // system section above).
    public static final float LEVEL_GEN_SPINE_BIG_ROOM_CHANCE       = 0.28f;

    // RESEARCH_LAB, CRYO_CHAMBER, CONTAINMENT_BLOCK, SERVER_ROOM, MEDICAL_BAY, ARMORY, POWER_PLANT,
    // COMMAND_CENTER selection now goes through the shared RoomBlueprintRegistry (RoomBlueprints.java)
    // — the same seeded weighted roll LevelGenerator uses — so this generator no longer carries its
    // own duplicate selection chance/size-gate constants for those types.
    // RESEARCH_LAB decoration (holo-data wall band, specimen tank count, scorch decal count):
    public static final float LEVEL_GEN_SPINE_RESEARCH_HOLO_WALL_CHANCE = 0.55f; // 'D' back wall band
    public static final int   LEVEL_GEN_SPINE_RESEARCH_MIN_TANKS    = 2;
    public static final int   LEVEL_GEN_SPINE_RESEARCH_MAX_TANKS    = 4;
    public static final int   LEVEL_GEN_SPINE_RESEARCH_SCORCH_MIN   = 2;
    public static final int   LEVEL_GEN_SPINE_RESEARCH_SCORCH_MAX   = 5;

    // STORAGE_BAY — cargo hold: stacked crate 'C' aisles + locker 'L' loot racks, ammo-dense.
    public static final float LEVEL_GEN_SPINE_STORAGE_CHANCE        = 0.24f;
    public static final int   LEVEL_GEN_SPINE_STORAGE_MAX           = 2;
    public static final int   LEVEL_GEN_SPINE_STORAGE_MIN_WIDTH     = 5;
    public static final int   LEVEL_GEN_SPINE_STORAGE_MIN_HEIGHT    = 4;
    public static final float LEVEL_GEN_SPINE_STORAGE_LOCKER_RATIO  = 0.35f; // locker vs crate per aisle tile

    // REACTOR — hazard field: explosive 'E' + radioactive 'g' barrels, generator '%' core,
    // radiation 'U' walls, dark flickering floor. Volatile risk/reward room.
    public static final float LEVEL_GEN_SPINE_REACTOR_CHANCE        = 0.18f;
    public static final int   LEVEL_GEN_SPINE_REACTOR_MAX           = 1;
    public static final int   LEVEL_GEN_SPINE_REACTOR_MIN_WIDTH     = 6;
    public static final int   LEVEL_GEN_SPINE_REACTOR_MIN_HEIGHT    = 6;
    public static final float LEVEL_GEN_SPINE_REACTOR_RAD_WALL_CHANCE = 0.45f; // 'U'
    public static final float LEVEL_GEN_SPINE_REACTOR_BARREL_CHANCE   = 0.16f; // per interior tile

    // -------------------------------------------------------------------------
    // LINEAR CORRIDOR generator — interior architecture
    // Structural layouts that turn bare boxes into legible spaces using columns and
    // props. Applied to STANDARD and LARGE rooms. Every layout is built lane-safe:
    // it never seals the room centre, a doorway, the spawn, or the stairs.
    // -------------------------------------------------------------------------
    // Probability a qualifying STANDARD room receives a structural interior layout
    // (otherwise it falls back to the legacy sparse prop scatter).
    public static final float LEVEL_GEN_INTERIOR_LAYOUT_CHANCE      = 0.62f;
    // Minimum interior dimension (both axes) for any structural interior layout.
    public static final int   LEVEL_GEN_INTERIOR_MIN_DIM            = 5;
    // Column spacing for centre-avenue / cross-spar layouts.
    public static final int   LEVEL_GEN_INTERIOR_COLUMN_SPACING     = 2;
    // Crate-cover layout: number of cover clusters and crates per cluster.
    public static final int   LEVEL_GEN_INTERIOR_CRATE_CLUSTERS     = 3;
    public static final int   LEVEL_GEN_INTERIOR_CRATE_PER_CLUSTER  = 3;

    // -------------------------------------------------------------------------
    // LINEAR CORRIDOR generator — spine junction plaza
    // The spine is broken up by one widened open node so a long straight artery is
    // not monotonous: it reads as a crossroads / staging area framed by columns.
    // -------------------------------------------------------------------------
    public static final float LEVEL_GEN_SPINE_PLAZA_CHANCE          = 0.65f;
    // Half-extent (tiles) of the square opening carved around the plaza centre.
    public static final int   LEVEL_GEN_SPINE_PLAZA_RADIUS          = 2;

    // --- Shared per-room-type floor lighting probabilities ---
    // Standard room lighting — cumulative thresholds (checked in order; remainder = ' ').
    // Probabilities: flicker 5 %, unlit 10 %, dim 25 %, lit 60 %.
    public static final float LEVEL_GEN_ROOM_STANDARD_FLICKER_CUMULATIVE   = 0.05f;
    public static final float LEVEL_GEN_ROOM_STANDARD_UNLIT_CUMULATIVE     = 0.15f;
    public static final float LEVEL_GEN_ROOM_STANDARD_NORMAL_CUMULATIVE    = 0.40f;
    // SERVER_ROOM lighting — after flicker budget, remainder split unlit / dim
    public static final float LEVEL_GEN_ROOM_SERVER_UNLIT_THRESHOLD        = 0.55f;
    // ARMORY lighting
    public static final float LEVEL_GEN_ROOM_ARMORY_CORNER_DARK_CHANCE     = 0.60f;
    public static final float LEVEL_GEN_ROOM_ARMORY_DIM_CHANCE             = 0.12f;
    // CRYO_CHAMBER lighting
    public static final float LEVEL_GEN_ROOM_CRYO_UNLIT_CHANCE             = 0.15f;
    // POWER_PLANT lighting
    public static final float LEVEL_GEN_ROOM_POWERPLANT_NEAR_FLICKER_CHANCE = 0.40f;
    // COMMAND_CENTER lighting
    public static final float LEVEL_GEN_ROOM_COMMAND_EDGE_DIM_CHANCE       = 0.35f;
    // CONTAINMENT_BLOCK lighting
    public static final float LEVEL_GEN_ROOM_CONTAINMENT_FLICKER_CHANCE    = 0.08f;

    // --- Shared per-room-type pickup chances (LOOT ROOM BUDGETS — see BalanceConfig) ---
    public static final float LEVEL_GEN_ARMORY_MEDKIT_CHANCE               = BalanceConfig.LEVEL_GEN_ARMORY_MEDKIT_CHANCE;
    public static final float LEVEL_GEN_ARMORY_ARMOUR_CHANCE               = BalanceConfig.LEVEL_GEN_ARMORY_ARMOUR_CHANCE;
    public static final float LEVEL_GEN_COMMAND_MEDKIT_CHANCE              = BalanceConfig.LEVEL_GEN_COMMAND_MEDKIT_CHANCE;
    public static final float LEVEL_GEN_COMMAND_ARMOUR_CHANCE              = BalanceConfig.LEVEL_GEN_COMMAND_ARMOUR_CHANCE;
    public static final float LEVEL_GEN_COMMAND_AMMO_CHANCE                = BalanceConfig.LEVEL_GEN_COMMAND_AMMO_CHANCE;
    // POWER_PLANT / CRYO_CHAMBER / CONTAINMENT_BLOCK — hazardous/dark rooms (reduced loot)
    public static final float LEVEL_GEN_HAZARD_ROOM_MEDKIT_CHANCE          = BalanceConfig.LEVEL_GEN_HAZARD_ROOM_MEDKIT_CHANCE;
    public static final float LEVEL_GEN_HAZARD_ROOM_ARMOUR_CHANCE          = BalanceConfig.LEVEL_GEN_HAZARD_ROOM_ARMOUR_CHANCE;
    public static final float LEVEL_GEN_HAZARD_ROOM_AMMO_CHANCE            = BalanceConfig.LEVEL_GEN_HAZARD_ROOM_AMMO_CHANCE;

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

    // Rectangular chambers stamped into the cave (the Room-type bridge).
    public static final int   LEVEL_GEN_CAVE_CHAMBER_MIN          = 2;
    public static final int   LEVEL_GEN_CAVE_CHAMBER_MAX          = 4;
    public static final int   LEVEL_GEN_CAVE_CHAMBER_TRIES        = 40;
    public static final int   LEVEL_GEN_CAVE_CHAMBER_MIN_WIDTH    = 4;
    public static final int   LEVEL_GEN_CAVE_CHAMBER_MAX_WIDTH    = 7;
    public static final int   LEVEL_GEN_CAVE_CHAMBER_MIN_HEIGHT   = 4;
    public static final int   LEVEL_GEN_CAVE_CHAMBER_MAX_HEIGHT   = 6;
    // Minimum fraction of the chamber footprint that must already be cave floor.
    public static final float LEVEL_GEN_CAVE_CHAMBER_FLOOR_OVERLAP  = 0.65f;
    // Probability a chamber opening is ragged floor instead of a door.
    public static final float LEVEL_GEN_CAVE_CHAMBER_BREACH_CHANCE  = 0.40f;

    // Cave body wall distribution (applied to walls bordering cave floor, evaluated in order;
    // the remaining ~0.54 fraction stays plain 'x' rock).
    public static final float LEVEL_GEN_CAVE_GORE_WALL_CHANCE       = 0.18f; // 'G' flesh-wall
    public static final float LEVEL_GEN_CAVE_RUST_WALL_CHANCE       = 0.22f; // 'j' corroded
    public static final float LEVEL_GEN_CAVE_BULKHEAD_WALL_CHANCE   = 0.06f; // 'k' armour plate

    // Cave floor lighting — individual proportions (sum to 0.84; remainder ~0.16 stays lit ' ').
    // Implementation computes running cumulative thresholds: unlit → unlit+normal → +flicker.
    public static final float LEVEL_GEN_CAVE_FLOOR_UNLIT_CHANCE     = 0.45f; // 'u' deep dark
    public static final float LEVEL_GEN_CAVE_FLOOR_NORMAL_CHANCE    = 0.35f; // 'l' dim
    public static final float LEVEL_GEN_CAVE_FLOOR_FLICKER_CHANCE   = 0.04f; // 'f' failing light
    // Total budget of flickering floor tiles in the entire cave.
    public static final int   LEVEL_GEN_CAVE_FLICKER_BUDGET         = 6;

    // Probability per cave floor tile of receiving a decal (blood, oil, scorch, corpse).
    public static final float LEVEL_GEN_CAVE_DECAL_CHANCE           = 0.05f;
    // Probability per eligible open-cave tile of receiving a stalagmite column 'P'.
    public static final float LEVEL_GEN_CAVE_STALAGMITE_CHANCE      = 0.03f;
    // Probability per level of placing a small radioactive barrel cluster.
    public static final float LEVEL_GEN_CAVE_BARREL_CLUSTER_CHANCE  = 0.50f;
    // Minimum Chebyshev distance from player spawn at which cave-body enemies may appear.
    public static final int   LEVEL_GEN_CAVE_SPAWN_SAFE_RADIUS      = 6;

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
    // Probability a cave-body wall bordering floor is reskinned with a biome wall.
    public static final float LEVEL_GEN_CAVE_BIOME_WALL_CHANCE      = 0.55f;
    // Probability per eligible open cave tile of receiving a scattered biome prop.
    public static final float LEVEL_GEN_CAVE_BIOME_PROP_CHANCE      = 0.035f;
    // Minimum walkable cardinal neighbours a tile needs before a solid biome prop
    // may be stamped there (pinch-guard so winding tunnels never seal shut).
    public static final int   LEVEL_GEN_CAVE_BIOME_PROP_MIN_OPEN    = 3;
    // Feature clusters (pod nests, barrel piles, console banks) stamped per biome.
    public static final int   LEVEL_GEN_CAVE_BIOME_FEATURE_MIN      = 1;
    public static final int   LEVEL_GEN_CAVE_BIOME_FEATURE_MAX      = 2;
    // Maximum props stamped per feature cluster.
    public static final int   LEVEL_GEN_CAVE_BIOME_CLUSTER_SIZE     = 4;
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
    // Width (columns) of the 'N' reinforced-glass viewing window band on the ward's top wall.
    public static final int   MED_BAY_WINDOW_WIDTH      = 4;

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

    // --- Symbol/sprite-reuse order-5: RoomBlueprint selection weights ---
    // RoomBlueprint.selectionWeight() reuses the existing *_CHANCE constants for the chance-rolled
    // rooms; these cover the rooms that lack a natural chance gate. They are DECLARED in order-5 and
    // CONSUMED by the registry-driven selection order-8 introduces (which defines how weights combine).
    // ENTRANCE is assigned to room 0 by position, never rolled → zero weight.
    public static final float LEVEL_GEN_ROOM_ENTRANCE_SELECTION_WEIGHT = 0f;
    // STANDARD is the residual fallback for any room no specialty claims. order-8 makes this the roulette
    // weight for plain rooms; kept well above the summed specialty weights so most rooms stay plain and
    // corridors/rooms read legibly (PLAIN-WALL discipline at the room level).
    public static final float LEVEL_GEN_ROOM_STANDARD_SELECTION_WEIGHT = 5.0f;
    // MEDICAL_BAY roulette weight (order-8): the guaranteed-once finder is gone, so eligibility caps it
    // to one and this above-baseline weight keeps a heal-stop room common across a run.
    public static final float LEVEL_GEN_ROOM_MEDICAL_BAY_SELECTION_WEIGHT = 2.5f;

    // --- Symbol/sprite-reuse order-8: registry-driven selection + variety tuning ---
    // order-8 replaces the hardcoded probability-band switch in assignRoomTypes() with a per-candidate
    // seeded weighted pick over the RoomBlueprintRegistry (RoomBlueprint.selectionWeight). The roulette
    // weighs each eligible specialty room (its existing *_CHANCE) against STANDARD's baseline weight, so
    // a higher STANDARD weight keeps plain rooms dominant and specialties occasional — the mix the old
    // bands produced. The STANDARD and MEDICAL_BAY roulette weights live with the order-5 selection
    // weights above; the salvage-bay and STEP-C accent tunables follow.

    // SALVAGE_BAY — order-8 REQUIREMENT PROOF 1: a brand-new GENERIC room with a distinct central
    // scrap-heap architecture, composed entirely from FREE (unreserved) flexible symbols so its look is
    // allocator-driven. Eligible on ordinary floors at a modest size; capped so it stays a flavour room.
    public static final int   LEVEL_GEN_SALVAGE_BAY_MIN_WIDTH  = 6;
    public static final int   LEVEL_GEN_SALVAGE_BAY_MIN_HEIGHT = 6;
    public static final int   LEVEL_GEN_SALVAGE_BAY_MAX        = 2;
    public static final float LEVEL_GEN_ROOM_SALVAGE_BAY_SELECTION_WEIGHT = 0.30f;
    // Salvage-bay interior tuning (all rates seeded through the generator's Random).
    public static final float LEVEL_GEN_SALVAGE_HEAP_DENSITY        = 0.55f; // solid-prop fill of the central heap
    public static final float LEVEL_GEN_SALVAGE_PERIMETER_RACK_CHANCE = 0.30f; // lockers/racks along interior walls
    public static final float LEVEL_GEN_SALVAGE_WALL_ACCENT_CHANCE  = 0.22f; // perimeter wall → salvage accent symbol
    public static final float LEVEL_GEN_SALVAGE_DECAL_CHANCE        = 0.14f; // oil/scrap decals on open floor
    public static final float LEVEL_GEN_SALVAGE_DARK_FLOOR_CHANCE   = 0.30f; // dingy 'u' patches over the 'l' floor

    // SUPPLY_CACHE — order-9 RECIPE B acceptance room: a compact ancillary storeroom, added by
    // REGISTRATION ALONE. Its build() COMPOSES the existing per-room steps (standard floor + standard
    // columns + a double standard-prop pass for cluttered density) — no bespoke generator helper — and
    // its accent walls are allocator-driven per level via the generic-variety pass. Modest size window,
    // capped so it stays occasional. See docs/environment-tileset-system.txt §9 (RECIPE B).
    public static final int   LEVEL_GEN_SUPPLY_CACHE_MIN_WIDTH  = 5;
    public static final int   LEVEL_GEN_SUPPLY_CACHE_MIN_HEIGHT = 5;
    public static final int   LEVEL_GEN_SUPPLY_CACHE_MAX        = 2;
    public static final float LEVEL_GEN_ROOM_SUPPLY_CACHE_SELECTION_WEIGHT = 0.30f;

    // STEP C — generic rooms & halls get per-level variety. A sparse pass stamps FREED flexible accent
    // symbols (those NO placed signature room reserved this level) into GENERIC room perimeters and
    // corridor walls, so the allocator's per-level sprite for those symbols makes two seeds visibly
    // differ AND the freed symbols concretely appear. 'x' stays dominant (accents are sparse).
    public static final float LEVEL_GEN_GENERIC_ACCENT_ROOM_CHANCE  = 0.60f; // chance a generic room gets an accent theme
    public static final float LEVEL_GEN_GENERIC_ACCENT_WALL_CHANCE  = 0.14f; // per perimeter wall tile, if themed
    public static final float LEVEL_GEN_CORRIDOR_ACCENT_WALL_CHANCE = 0.05f; // per corridor-context wall tile
}
