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

    /** Explosive barrels ('E'). Placement also triggers hazard-wall post-pass. */
    public boolean explosiveBarrels = true;

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

    /** Corpse decals ('m'). Also trigger gore-wall post-pass. */
    public boolean corpses = true;

    /** Oil pool decals ('O'). Also trigger rust-wall post-pass. */
    public boolean oilPools = true;

    // -------------------------------------------------------------------------
    // Structural elements
    // -------------------------------------------------------------------------

    /** Cylindrical columns ('P'). Adds visual depth and cover in larger rooms. */
    public boolean columns = true;

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
    public float explosiveBarrelWeight   = 0.08f;
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
}
