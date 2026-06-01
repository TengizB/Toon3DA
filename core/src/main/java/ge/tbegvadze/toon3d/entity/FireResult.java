package ge.tbegvadze.toon3d.entity;

/**
 * Outcome of a weapon's fire() call in tile space.
 *
 * v1: no enemies — records only whether the shot hit a wall before travelling
 * past maximum range. Extended (enemy reference, damage dealt) when EnemyManager
 * lands.
 */
public final class FireResult {

    /** Shot stopped at a wall tile before reaching maximum range. */
    public static final FireResult HIT_WALL = new FireResult(true,  -1);
    /** Shot flew the full range without hitting any wall or enemy. */
    public static final FireResult MISSED   = new FireResult(false, -1);

    /** True when a wall tile absorbed the shot. */
    public final boolean stoppedByWall;
    /**
     * Tile distance at which a hit occurred (1 = adjacent tile).
     * -1 for the HIT_WALL and MISSED singletons; populated for future
     * enemy-hit results constructed inline in Weapon.marchShot().
     */
    public final int distanceTiles;

    FireResult(boolean stoppedByWall, int distanceTiles) {
        this.stoppedByWall = stoppedByWall;
        this.distanceTiles = distanceTiles;
    }
}
