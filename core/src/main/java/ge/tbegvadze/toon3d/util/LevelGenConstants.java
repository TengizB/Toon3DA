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
    public static final float LEVEL_GEN_PROP_CHANCE           = 0.13f;
    public static final int   LEVEL_GEN_MAX_ENEMIES_PER_ROOM  = 3;
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

    // --- Room type system ---
    // LARGE rooms: interior must meet LARGE_MIN_DIM in both axes to be eligible.
    public static final int   LEVEL_GEN_LARGE_MIN_DIM              = 9;
    // Probability that an eligible (large enough) room becomes a LARGE landmark room.
    public static final float LEVEL_GEN_LARGE_ROOM_CHANCE          = 0.55f;
    // Hard cap: at most this many LARGE rooms per level so they feel special.
    public static final int   LEVEL_GEN_LARGE_ROOM_MAX_PER_LEVEL   = 3;
    // Column count range for LARGE rooms (more than the standard 1-3).
    public static final int   LEVEL_GEN_LARGE_COLUMN_MIN           = 4;
    public static final int   LEVEL_GEN_LARGE_COLUMN_MAX           = 6;
    // Prop density for LARGE rooms — sparse so the open floor stays navigable.
    public static final float LEVEL_GEN_LARGE_PROP_CHANCE          = 0.06f;
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
    public static final float LEVEL_GEN_SERVER_MEDKIT_CHANCE       = 0.55f;
    public static final float LEVEL_GEN_SERVER_ARMOUR_CHANCE       = 0.35f;
    public static final float LEVEL_GEN_LARGE_MEDKIT_CHANCE        = 0.50f;
    public static final float LEVEL_GEN_LARGE_ARMOUR_CHANCE        = 0.30f;
    // Base probability that any non-entrance room contains at least one ammo box pickup.
    public static final float LEVEL_GEN_AMMO_CHANCE_PER_ROOM       = 0.35f;

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
    public static final float LEVEL_GEN_LARGE_WEAPON_CHANCE      = 0.30f;
    // Probability that any non-ENTRANCE room gets a random weapon spawn (independent of armory)
    public static final float LEVEL_GEN_RANDOM_ROOM_WEAPON_CHANCE = 0.20f;

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

    // Global accent wall chances (post-pass, any room type)
    public static final float LEVEL_GEN_EMERG_STRIP_CORRIDOR_CHANCE = 0.25f; // 'S' near keycard doors
    public static final float LEVEL_GEN_BLAST_NEAR_CORPSE_CHANCE    = 0.10f; // 'X' near corpse clusters
}
