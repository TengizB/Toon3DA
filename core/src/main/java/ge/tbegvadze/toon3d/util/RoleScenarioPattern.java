package ge.tbegvadze.toon3d.util;

/**
 * How one fire action of a weapon spreads over a GROUP in the R-ROLE scenario model (balance-overhaul
 * order 3). Pure data read by {@link GameMath#roleScenarioClear}; the geometry each pattern stands for
 * is stated on the constant. A single target makes every pattern hit just that target.
 */
public enum RoleScenarioPattern {
    /** The first enemy only (rifles, chaingun, shotguns). */
    SINGLE,
    /** Every enemy standing in the firing lane — {@code ROLE_GROUP_TARGETS_IN_LANE} of a group (Plasma, Railgun). */
    PIERCE,
    /** Every enemy of the clustered group inside the cone's reach (Incinerator). */
    CONE,
    /** The first enemy at the centre hit, every other group member at the neighbour hit (Grenade Launcher plus). */
    SPLASH,
    /** The first enemy at the full hit, then up to {@code chainJumps} others at a decaying, never-resisted hit (Arc Cannon). */
    CHAIN
}
