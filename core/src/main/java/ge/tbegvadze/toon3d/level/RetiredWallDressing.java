package ge.tbegvadze.toon3d.level;

/**
 * R1 DRAW LEDGER (generated-wall-art order 2). The generators no longer choose wall pictures — the
 * {@link LevelFinisher} dresses every wall from the floor's region set — but the wall-dressing passes they
 * used to run drew from the generator's {@link java.util.Random}, interleaved with layout, props, spawns and
 * supply. Rule R1 keeps the 2D game byte-identical for every seed, so those passes keep EVERY draw and only
 * stop writing the symbol. A few of their draws were conditioned on whether a wall was still the plain
 * {@code 'x'} (the rust, gore and bulkhead passes); this ledger remembers which walls a retired pass WOULD
 * have repainted, so those conditions see exactly what they saw before. It never reaches the {@link Level}
 * and nothing is drawn from it: every wall it touches stays a plain {@code 'x'} in the grid.
 */
final class RetiredWallDressing {

    private final boolean[][] dressed;

    RetiredWallDressing(int levelHeight, int levelWidth) {
        this.dressed = new boolean[levelHeight][levelWidth];
    }

    /**
     * Where a retired pass would have written {@code retiredSymbol}: the tile becomes (stays) the plain
     * wall {@code 'x'} — a geometry write (floor -&gt; wall) still makes a wall — and the ledger records
     * whether the old symbol was a dressing (anything but {@code 'x'}).
     */
    void paint(char[][] grid, int tileColumn, int tileRow, char retiredSymbol) {
        grid[tileRow][tileColumn]    = 'x';
        dressed[tileRow][tileColumn] = retiredSymbol != 'x';
    }

    /** Records that a retired pass would have repainted the wall at the tile (the grid is not touched). */
    void markDressed(int tileColumn, int tileRow) {
        dressed[tileRow][tileColumn] = true;
    }

    /** What a retired pass's {@code cell == 'x'} test saw: a plain wall no retired pass had repainted. */
    boolean isPlainWall(char[][] grid, int tileColumn, int tileRow) {
        return grid[tileRow][tileColumn] == 'x' && !dressed[tileRow][tileColumn];
    }
}
