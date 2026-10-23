package ge.tbegvadze.toon3d.level;

/**
 * Configuration for procedural level generation: floor lighting, the structural switches (columns, wide halls,
 * lock-and-key), the encounter / supply / footprint inputs, the wall set, and the floor's preferred regular-room kit.
 * Rooms are furnished by prop kits (level-generation-overhaul) — there is no per-symbol scatter flag or weight.
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
    // Structural elements
    // -------------------------------------------------------------------------

    /** Cylindrical columns ('P'): the architecture's pillar slots and the hall pass's spaced hall columns. */
    public boolean columns = true;

    /** Enable wide (3-tile) grand hallways with centre-line columns on 2-3 MST edges. */
    public boolean enableWideHallways = true;

    /**
     * Enable lock-and-key gating: promote one bridge door to a keycard-locked door,
     * scatter the matching keycard in a still-reachable room, and steer the level exit
     * behind the gate. Reuses the existing DoorManager keycard system.
     */
    public boolean enableLockAndKey = true;

    // Pickups are no longer configured per floor (balance-overhaul order 2): the SupplyPlanner places every
    // medkit, armour, ammo box, weapon drop and credit chip from the node's {@link #supplySpec}.

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

    // -------------------------------------------------------------------------
    // Supply & density (balance-overhaul order 2)
    // -------------------------------------------------------------------------

    /**
     * What this floor's node type CONTAINS (S10): threat, bodies and the supply the
     * {@link SupplyPlanner} derives from the roster. {@code null} = the COMBAT row (a floor built
     * without a route node). The node type's own threat lives HERE; {@link #enemyBudgetScale} carries
     * only the extra multipliers on top (an ELITE affix, an event's next-floor bonus, a mystery outcome).
     */
    public ge.tbegvadze.toon3d.route.NodeSupplySpec supplySpec = null;

    /**
     * Ammo types of the weapons the player carries at floor build (S2), set by World / the simulator.
     * {@code null} or empty = the expected player's types. Supply never reads the player's CURRENT ammo.
     */
    public java.util.Set<ge.tbegvadze.toon3d.item.AmmoType> carriedAmmoTypes = null;

    /** S9 cadence: the previous non-boss floor offered no weapon at level >= its depth. Set by World. */
    public boolean weaponCadenceDue = false;

    /**
     * E6 footprint: the walkable tiles a combat generator (rooms / corridor / cave) builds to. 0 = derive it
     * from the floor's region range (the normal case — see {@link FootprintPlanner}); a negative value =
     * build at natural size (no target).
     */
    public int targetWalkableTiles = 0;

    // -------------------------------------------------------------------------
    // Region wall set (generated-wall-art order 2)
    // -------------------------------------------------------------------------

    /**
     * The floor's region wall set id (tileset/RegionWallSets), set by World with its other per-floor
     * fields; the LevelFinisher dresses every wall from it. Null = the default set (outer_facility).
     */
    public String wallSetId = null;

    /**
     * The regular-room kit this floor prefers (level-generation-overhaul R23/R31): it counts double in every regular
     * room's uniform kit draw. Set by a route profile (CACHE -> "storeroom", ELITE -> "aftermath"); null = none.
     */
    public String preferredRegularKit = null;

    /** P7: how many floors into its region this floor is (0 = the region's first floor; RegionPlan). */
    public int floorIndexInRegion = 0;
}
