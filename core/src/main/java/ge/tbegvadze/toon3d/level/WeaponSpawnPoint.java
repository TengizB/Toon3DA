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
    /**
     * Whether the SupplyPlanner planned this drop (balance-overhaul order 2, S9). A planned drop rolls
     * its level as {@code depth + levelOffset} and its tier from at least the region minimum plus
     * {@code tierBonus}; an unplanned one (none ship today) rolls the ordinary depth band.
     */
    public final boolean  planned;
    /** Weapon level offset from the floor depth (planned drops). */
    public final int      levelOffset;
    /** Tier floor above the region's minimum drop tier, or -1 for the plain band (planned drops). */
    public final int      tierBonus;

    public WeaponSpawnPoint(int tileColumn, int tileRow, ItemType weaponItemType) {
        this(tileColumn, tileRow, weaponItemType, false, 0, -1);
    }

    public WeaponSpawnPoint(int tileColumn, int tileRow, ItemType weaponItemType,
                            boolean planned, int levelOffset, int tierBonus) {
        if (weaponItemType == null) throw new IllegalArgumentException("weaponItemType must not be null");
        this.tileColumn     = tileColumn;
        this.tileRow        = tileRow;
        this.weaponItemType = weaponItemType;
        this.planned        = planned;
        this.levelOffset    = levelOffset;
        this.tierBonus      = tierBonus;
    }
}
