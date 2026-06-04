package ge.tbegvadze.toon3d.level;

/**
 * Records the tile position and type character of an enemy spawn found in a level .txt file.
 * Produced by LevelLoader when it encounters enemy spawn characters.
 * Consumed by EnemyManager to build the initial enemy list.
 * Kept in the level package so Level stays free of enemy-package imports.
 */
public final class EnemySpawnPoint {

    /** '1'=Corruptor, '2'=Vortex Eye, '3'=Ghoul, '4'=Crawler, '5'=Revenant. */
    public final char spawnChar;
    public final int  tileColumn;
    public final int  tileRow;

    public EnemySpawnPoint(char spawnChar, int tileColumn, int tileRow) {
        this.spawnChar  = spawnChar;
        this.tileColumn = tileColumn;
        this.tileRow    = tileRow;
    }
}
