package ge.tbegvadze.toon3d.level;

import com.badlogic.gdx.Gdx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LevelLoader {

    /**
     * Reads a .txt file from assets line by line and builds a Level.
     * Matrix width  = length of the longest line.
     * Matrix height = number of lines.
     * Lines shorter than the widest are padded with spaces on the right.
     * Rows are stored bottom-up so that matrix[0] is the last line in the file,
     * matching world Y-up semantics: row 0 = bottom-left tile.
     */
    public Level load(String fileName) {
        String text = Gdx.files.internal(fileName).readString();
        String[] rawLines = text.split("\n", -1);

        // Strip trailing \r so Windows-style line endings don't pollute cell chars
        List<String> lines = new ArrayList<>(rawLines.length);
        for (String rawLine : rawLines) {
            lines.add(rawLine.endsWith("\r") ? rawLine.substring(0, rawLine.length() - 1) : rawLine);
        }

        // Most editors append a final newline, producing a spurious empty last element — drop it
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }

        int levelWidth = 0;
        for (String line : lines) {
            if (line.length() > levelWidth) levelWidth = line.length();
        }

        int levelHeight = lines.size();
        char[][] matrix = new char[levelHeight][levelWidth];

        // File is top-to-bottom; store bottom-up so row 0 = last line (Y-up)
        for (int row = 0; row < levelHeight; row++) {
            String line = lines.get(levelHeight - 1 - row);
            char[] rowChars = Arrays.copyOf(line.toCharArray(), levelWidth);
            // copyOf pads with '\0'; replace those with spaces
            for (int column = line.length(); column < levelWidth; column++) {
                rowChars[column] = ' ';
            }
            matrix[row] = rowChars;
        }

        // Scan for enemy spawn characters:
        //   '1'=Plague Hulk  '2'=Eye Tyrant   '3'=Gore Biter  '4'=Shell Brute  '5'=Mire Wraith
        //   '!'=Iron Stalker '$'=Acid Drone    '^'=Void Shroud
        //   '~'=Ghoul        'z'=Crawler       'K'=Revenant   'V'=Vortex Eye   '*'=Blight Corruptor
        //   '('=Auric Sentinel (elemental golem)
        // Record each spawn, then replace the cell with lit-floor so the grid
        // stays traversable and existing renderers need no changes.
        List<EnemySpawnPoint> spawnPoints = new ArrayList<>();
        for (int tileRow = 0; tileRow < levelHeight; tileRow++) {
            for (int tileColumn = 0; tileColumn < levelWidth; tileColumn++) {
                char cell = matrix[tileRow][tileColumn];
                if (cell == '1' || cell == '2' || cell == '3' || cell == '4' || cell == '5'
                        || cell == '!' || cell == '$' || cell == '^'
                        || cell == '~' || cell == 'z' || cell == 'K' || cell == 'V' || cell == '*'
                        || cell == '(' || cell == 'n') {
                    spawnPoints.add(new EnemySpawnPoint(cell, tileColumn, tileRow));
                    matrix[tileRow][tileColumn] = ' ';
                }
            }
        }

        return new Level(matrix, spawnPoints, new ArrayList<>());
    }
}
