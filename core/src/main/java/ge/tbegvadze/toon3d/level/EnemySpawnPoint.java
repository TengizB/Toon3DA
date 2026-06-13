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

    public EnemySpawnPoint(char spawnChar, int tileColumn, int tileRow) {
        this.spawnChar  = spawnChar;
        this.tileColumn = tileColumn;
        this.tileRow    = tileRow;
    }
}
