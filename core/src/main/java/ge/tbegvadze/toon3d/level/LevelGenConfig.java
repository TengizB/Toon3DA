package ge.tbegvadze.toon3d.level;

/**
 * Configuration for procedural level generation — single source of truth for which tile
 * symbols the generator may place and at what relative frequencies.
 *
 * All public fields are intentionally mutable so callers can build a custom config by
 * mutating a default instance rather than going through a verbose builder chain.
 *
 * Usage:
 *   LevelGenConfig config = new LevelGenConfig();   // sensible defaults
 *   config.flickeringFloors = true;                  // opt-in to flickering floors
 *   new LevelGenerator(seed, config).generate();
 */
public final class LevelGenConfig {

    // -------------------------------------------------------------------------
    // Floor lighting variants
    // -------------------------------------------------------------------------

    /** Allow flickering floor tiles ('f'). Off by default — use for special themed levels. */
    public boolean flickeringFloors = false;

    /** Allow unlit floor tiles ('u'). Creates dark dread zones; also triggers rust-wall post-pass. */
    public boolean unlitFloors = true;

    /** Allow normal-brightness floor tiles ('l') in the per-room lighting pass. */
    public boolean normalFloors = true;

    // -------------------------------------------------------------------------
    // Solid props (block player movement)
    // -------------------------------------------------------------------------

    /** Radioactive barrels ('g'). */
    public boolean radioactiveBarrels = true;

    /** Crates ('C'). */
    public boolean crates = true;

    /** Computer terminals ('T'). */
    public boolean computerTerminals = true;

    /** Lockers ('L'). Off by default. */
    public boolean lockers = false;

    // -------------------------------------------------------------------------
    // Decal props (walkable; no collision)
    // -------------------------------------------------------------------------

    /** Blood stain decals ('.'). Also trigger rust and gore wall post-passes. */
    public boolean bloodStains = true;

    /**
     * Corpse decals ('m'). OFF by default: 'm' is the game's DEATH MARKER — EnemyManager.killEnemy
     * stamps it on the tile where an enemy dies — so generating it as scenery made a freshly entered
     * room indistinguishable from one the player had already fought through. Measured at ~16.4
     * pre-placed corpses against ~11.7 live enemies per floor (1.4 corpses per living enemy), which
     * reads unmistakably as "the enemies here died on their own" and was reported as exactly that.
     *
     * <p>'m' now means one thing only: SOMETHING DIED HERE DURING THIS RUN. Aftermath atmosphere is
     * carried by the blood ('.'), alt-blood ('s'), oil ('O') and scorch ('e') decals, which imply a
     * fight without implying a body the player did not make. Turn this on only for a hand-authored
     * level where pre-existing corpses are the intended story.
     */
    public boolean corpses = false;

    /** Oil pool decals ('O'). Also trigger rust-wall post-pass. */
    public boolean oilPools = true;

    // -------------------------------------------------------------------------
    // Structural elements
    // -------------------------------------------------------------------------

    /** Cylindrical columns ('P'). Adds visual depth and cover in larger rooms. */
    public boolean columns = true;

    /**
     * Enable the LARGE size modifier: gates whether any non-entrance room may independently roll
     * LEVEL_GEN_LARGE_MODIFIER_CHANCE to be stamped significantly larger than standard, keeping
     * whatever room-type styling it would have received anyway. Not a room type of its own.
     */
    public boolean enableLargeRooms = true;

    /** Enable SERVER_ROOM data vaults (terminal walls, rack rows, dark atmosphere). */
    public boolean enableServerRooms = true;

    /** Enable wide (3-tile) grand hallways with centre-line columns on 2-3 MST edges. */
    public boolean enableWideHallways = true;

    /**
     * Enable lock-and-key gating: promote one bridge door to a keycard-locked door,
     * scatter the matching keycard in a still-reachable room, and steer the level exit
     * behind the gate. Reuses the existing DoorManager keycard system.
     */
    public boolean enableLockAndKey = true;

    // -------------------------------------------------------------------------
    // Pickups (walkable; collected on contact)
    // -------------------------------------------------------------------------

    /** Field medkits ('H'). Placed in non-entrance rooms. */
    public boolean medkits = true;

    /** Security vests ('A'). Placed in non-entrance rooms. */
    public boolean armourKits = true;

    // -------------------------------------------------------------------------
    // Prop relative weights
    // Disabled categories are silently skipped; remaining weights are normalised
    // at runtime so the total always sums to 1.0 regardless of which are enabled.
    // -------------------------------------------------------------------------

    public float radioactiveBarrelWeight = 0.22f;
    public float crateWeight             = 0.10f;
    public float terminalWeight          = 0.10f;
    public float lockerWeight            = 0.08f;
    public float bloodStainWeight        = 0.16f;
    public float corpseWeight            = 0.14f;
    public float oilPoolWeight           = 0.12f;

    // -------------------------------------------------------------------------
    // Column placement parameters
    // -------------------------------------------------------------------------

    /** Probability (0–1) that an eligible non-entrance room receives columns at all. */
    public float columnChancePerRoom = 0.65f;

    /** Minimum number of columns placed when a room is selected for columns. */
    public int columnMinCount = 1;

    /** Maximum number of columns placed when a room is selected for columns. */
    public int columnMaxCount = 3;

    /**
     * Minimum interior dimension (width AND height) a room must have to be eligible
     * for column placement. Prevents columns from blocking tiny rooms.
     */
    public int columnMinRoomSize = 4;

    // -------------------------------------------------------------------------
    // Pickup placement parameters
    // -------------------------------------------------------------------------

    /** Probability (0–1) that any non-entrance room contains a medkit. */
    public float medkitChancePerRoom = 0.35f;

    /** Probability (0–1) that any non-entrance room contains an armour kit. */
    public float armourChancePerRoom = 0.20f;

    // -------------------------------------------------------------------------
    // Encounter budget (route-map order-7)
    // -------------------------------------------------------------------------

    /**
     * Multiplier on the depth-scaled encounter Threat-Point budget spent by
     * {@link EncounterBudgetPlanner}. 1.0 = normal; a route-map node lowers it for CALM floors
     * (CACHE / REST / SHOP) or raises it for DANGER floors (ELITE). NEVER a substitute for the depth
     * ramp — the budget is still computed from raw depth first, then scaled.
     */
    public float enemyBudgetScale = 1f;
}
