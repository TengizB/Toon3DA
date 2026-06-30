package ge.tbegvadze.toon3d.world;

/**
 * Mutable holder for HUD-visible game state.  World seeds placeholder values;
 * the weapon system writes currentAmmo and clipSize each turn, and World.render()
 * writes the progression fields from PlayerProgress each frame.
 */
public class HudState {

    /** Shots currently loaded in the clip (0..clipSize). */
    public int     currentAmmo   = 4;

    /** Maximum shots per clip for the active weapon. */
    public int     clipSize      = 4;

    /** True while the red-alert state is active (pulsing LED). */
    public boolean alertActive   = false;

    /** Current player level (starts at 1). Drives the "LV.N" label on the XP bar. */
    public int     playerLevel    = 1;

    /** Fraction of XP collected toward the next level-up, in [0, 1]. Drives the XP bar fill. */
    public float   xpFraction     = 0f;

    /** XP required for the next level-up. Used for the numeric readout. */
    public int     xpForNextLevel = 100;

    /** Reserve ammo in inventory for the equipped weapon's ammo type. -1 when not tracked. */
    public int     reserveAmmo    = -1;

    /**
     * Total stashed medical charges (stims + medkits) the player can still use. Drives the
     * low-HP "use a medkit" reminder in HudRenderer: the warning only fires when this is > 0,
     * so the player is never nagged to heal with an empty stash.
     */
    public int     medicalCharges = 0;
}
