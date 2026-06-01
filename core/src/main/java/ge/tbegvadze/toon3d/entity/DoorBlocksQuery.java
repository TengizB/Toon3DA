package ge.tbegvadze.toon3d.entity;

/**
 * Narrow interface passed into Weapon.fire() so a shot can test whether a door tile
 * blocks its path without creating a direct dependency from entity → door.
 * DoorManager implements this via DoorManager::blocksSight.
 */
@FunctionalInterface
public interface DoorBlocksQuery {
    /** Returns true when the door at the given tile should stop a projectile. */
    boolean blocksShotAt(int tileColumn, int tileRow);
}
