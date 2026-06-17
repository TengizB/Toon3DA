package ge.tbegvadze.toon3d.level;

import java.util.ArrayDeque;

/**
 * Tracks which tiles the player has visited. Fog is permanent within a run and
 * resets when descending to a new floor.
 */
public class FogOfWarMap {

    private final boolean[][] revealed;
    private final int         columnCount;
    private final int         rowCount;

    public FogOfWarMap(int columnCount, int rowCount) {
        this.columnCount = columnCount;
        this.rowCount    = rowCount;
        this.revealed    = new boolean[rowCount][columnCount];
    }

    public boolean isRevealed(int tileColumn, int tileRow) {
        if (tileColumn < 0 || tileColumn >= columnCount || tileRow < 0 || tileRow >= rowCount) return false;
        return revealed[tileRow][tileColumn];
    }

    /**
     * BFS flood-fill from the player's settled tile to reveal the entire contiguous area.
     * Expansion stops only at solid wall tiles; doors, props, and decorations are treated
     * as passable so corridors and rooms with objects are fully uncovered.
     * Boundary wall tiles are still marked revealed so room outlines appear on the mini-map.
     */
    public void revealRoom(int startColumn, int startRow, Level level) {
        if (startColumn < 0 || startColumn >= columnCount || startRow < 0 || startRow >= rowCount) return;

        boolean[][] visited = new boolean[rowCount][columnCount];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(encodeTile(startColumn, startRow));
        visited[startRow][startColumn] = true;

        while (!queue.isEmpty()) {
            int encoded   = queue.poll();
            int tileColumn = decodeTileColumn(encoded);
            int tileRow    = decodeTileRow(encoded);

            revealed[tileRow][tileColumn] = true;

            char cell = level.getCell(tileColumn, tileRow);
            if (Level.isWall(cell)) continue;

            expandIfUnvisited(tileColumn + 1, tileRow,    visited, queue);
            expandIfUnvisited(tileColumn - 1, tileRow,    visited, queue);
            expandIfUnvisited(tileColumn,     tileRow + 1, visited, queue);
            expandIfUnvisited(tileColumn,     tileRow - 1, visited, queue);
        }
    }

    private void expandIfUnvisited(int tileColumn, int tileRow, boolean[][] visited,
                                    ArrayDeque<Integer> queue) {
        if (tileColumn < 0 || tileColumn >= columnCount || tileRow < 0 || tileRow >= rowCount) return;
        if (visited[tileRow][tileColumn]) return;
        visited[tileRow][tileColumn] = true;
        queue.add(encodeTile(tileColumn, tileRow));
    }

    private int encodeTile(int tileColumn, int tileRow) {
        return tileRow * columnCount + tileColumn;
    }

    private int decodeTileColumn(int encoded) {
        return encoded % columnCount;
    }

    private int decodeTileRow(int encoded) {
        return encoded / columnCount;
    }

    public void reset() {
        for (int tileRow = 0; tileRow < rowCount; tileRow++) {
            for (int tileColumn = 0; tileColumn < columnCount; tileColumn++) {
                revealed[tileRow][tileColumn] = false;
            }
        }
    }
}
