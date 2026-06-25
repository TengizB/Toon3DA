package ge.tbegvadze.toon3d.level;

/**
 * Common interface for all procedural level generators.
 * Every generator implements a single {@link #generate()} method that returns a
 * fully-playable {@link Level} — tile grid, enemy spawn points, and weapon spawn points.
 *
 * Current implementations:
 *   {@link LevelGenerator}           — scattered rooms connected by MST corridors (generator 1)
 *   {@link LinearCorridorGenerator}  — linear spine corridor with side rooms (generator 2)
 *   {@link CavernGenerator}          — cellular automata organic caves (generator 3)
 */
public interface ILevelGenerator {
    Level generate();

    /**
     * Generates a level for a specific dungeon depth (1-based floor number). Depth-aware
     * generators (e.g. {@link LevelGenerator}, which spends a per-depth encounter Threat-Point
     * budget — balance idea 4) override this; the default ignores depth and falls back to
     * {@link #generate()} so depth-agnostic generators need no change.
     */
    default Level generate(int dungeonDepth) {
        return generate();
    }
}
