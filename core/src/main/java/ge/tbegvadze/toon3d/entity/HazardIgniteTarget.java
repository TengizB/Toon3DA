package ge.tbegvadze.toon3d.entity;

/**
 * Narrow interface that lets a fire-STARTING system set a level tile alight without depending on the
 * hazard package directly.
 *
 * Two callers use it, and neither is the hazard system itself: the Incinerator (a weapon whose cone
 * bathes the floor in flame) and the enemy package's Cinderforge Colossus, whose MOLTEN TRAIL ignites
 * every tile it vacates. It is NOT part of the shared Weapon base class — no other weapon ignites tiles.
 * World.java wires the real implementation ({@code HazardManager}) per floor, since the manager is
 * rebuilt on every floor; in unit tests or no-World construction the target stays null and every
 * ignition call is a safe no-op.
 */
public interface HazardIgniteTarget {

    /**
     * Attempts to ignite a spreading FIRE hazard at the given tile, using the hazard system's own
     * default lifetime. Implementations no-op safely on walls / non-spreadable tiles.
     */
    void igniteFire(int tileColumn, int tileRow);

    /**
     * Attempts to ignite a spreading FIRE hazard whose lifetime is the CALLER's tunable rather than the
     * global hazard default — for a source that owns its own burn duration as a balance dial (the
     * Colossus's molten trail). Defaults to the global-lifetime call so an existing implementation, or a
     * test stub, keeps compiling and behaving exactly as before.
     */
    default void igniteFire(int tileColumn, int tileRow, int lifetimeTurns) {
        igniteFire(tileColumn, tileRow);
    }
}
