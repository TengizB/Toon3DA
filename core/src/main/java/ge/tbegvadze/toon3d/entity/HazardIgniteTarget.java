package ge.tbegvadze.toon3d.entity;

/**
 * Narrow, weapon-scoped interface that lets the Incinerator set a level tile on fire
 * without the entity package depending on the hazard package directly.
 *
 * Only the Incinerator uses this — it is NOT part of the shared Weapon base class, since
 * no other weapon ignites tiles. World.java wires the real implementation
 * ({@code hazardManager::igniteFire}) per floor; in unit tests or no-World construction
 * the target stays null and every ignition call is a safe no-op.
 */
public interface HazardIgniteTarget {

    /**
     * Attempts to ignite a spreading FIRE hazard at the given tile.
     * Implementations no-op safely on walls / non-spreadable tiles.
     */
    void igniteFire(int tileColumn, int tileRow);
}
