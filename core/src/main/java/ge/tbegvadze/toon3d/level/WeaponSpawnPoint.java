package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.item.ItemType;

/**
 * Records a location where the level generator placed a weapon pickup.
 * World reads these after generation to spawn GroundItem instances.
 * Kept in the level package alongside EnemySpawnPoint for symmetry.
 */
public final class WeaponSpawnPoint {

    public final int      tileColumn;
    public final int      tileRow;
    public final ItemType weaponItemType;

    public WeaponSpawnPoint(int tileColumn, int tileRow, ItemType weaponItemType) {
        if (weaponItemType == null) throw new IllegalArgumentException("weaponItemType must not be null");
        this.tileColumn     = tileColumn;
        this.tileRow        = tileRow;
        this.weaponItemType = weaponItemType;
    }
}
