package ge.tbegvadze.toon3d.entity;

/**
 * Narrow interface passed into Weapon.fire() so shots can trigger barrel detonations
 * without creating a direct dependency from entity → hazard.
 * ExplosiveBarrelManager implements this interface.
 */
public interface BarrelHitTarget {
    boolean isExplosiveBarrel(int tileColumn, int tileRow);
    void onExplosiveBarrelHit(int tileColumn, int tileRow);
}
