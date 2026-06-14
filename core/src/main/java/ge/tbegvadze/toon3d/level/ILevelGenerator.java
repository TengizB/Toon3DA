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
}
