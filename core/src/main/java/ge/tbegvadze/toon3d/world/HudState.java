package ge.tbegvadze.toon3d.world;

/**
 * Mutable holder for HUD-visible game state.  World seeds placeholder values;
 * the weapon system writes currentAmmo and clipSize each turn.
 */
public class HudState {

    /** Shots currently loaded in the clip (0..clipSize). */
    public int     currentAmmo   = 4;

    /** Maximum shots per clip for the active weapon. */
    public int     clipSize      = 4;

    /** True while the red-alert state is active (pulsing LED). */
    public boolean alertActive   = false;
}
