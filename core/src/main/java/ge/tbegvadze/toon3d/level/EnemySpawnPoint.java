package ge.tbegvadze.toon3d.level;

/**
 * Records the tile position and type character of an enemy spawn found in a level .txt file.
 * Produced by LevelLoader when it encounters enemy spawn characters.
 * Consumed by EnemyManager to build the initial enemy list.
 * Kept in the level package so Level stays free of enemy-package imports.
 */
public final class EnemySpawnPoint {

    /** '1'=Plague Hulk, '2'=Eye Tyrant, '3'=Gore Biter, '4'=Shell Brute, '5'=Mire Wraith,
     *  '!'=Iron Stalker, '$'=Acid Drone, '^'=Void Shroud. */
    public final char spawnChar;
    public final int  tileColumn;
    public final int  tileRow;
    /**
     * The pickup symbol this enemy CARRIES and drops on death (balance-overhaul order 2, S6 — a planned
     * share of the floor's supply), or {@code 0} for none. Hand-made levels never set it.
     */
    public final char carriedDrop;
    /** The encounter group this enemy belongs to (order 2, E2-E5), or -1 for none / a hand-made level. */
    public final int  groupId;

    public EnemySpawnPoint(char spawnChar, int tileColumn, int tileRow) {
        this(spawnChar, tileColumn, tileRow, (char) 0, -1);
    }

    public EnemySpawnPoint(char spawnChar, int tileColumn, int tileRow, char carriedDrop, int groupId) {
        this.spawnChar   = spawnChar;
        this.tileColumn  = tileColumn;
        this.tileRow     = tileRow;
        this.carriedDrop = carriedDrop;
        this.groupId     = groupId;
    }

    /** A copy of this spawn carrying {@code drop} (S6). */
    public EnemySpawnPoint withCarriedDrop(char drop) {
        return new EnemySpawnPoint(spawnChar, tileColumn, tileRow, drop, groupId);
    }

    /** A copy of this spawn tagged with an encounter group id. */
    public EnemySpawnPoint withGroupId(int group) {
        return new EnemySpawnPoint(spawnChar, tileColumn, tileRow, carriedDrop, group);
    }
}
