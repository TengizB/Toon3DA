package ge.tbegvadze.toon3d.world;

/**
 * Holds mutable global flags that control active game effects.
 * Toggle these fields at runtime to switch effects on or off without
 * touching renderer code. All fields are public so any system can read
 * or write them; keep writes in one place per flag to avoid race conditions.
 */
public class GameState {

    /** When true the facility emergency lighting pulses red across ceiling, floor, and walls. */
    public boolean redAlert = false;
}
