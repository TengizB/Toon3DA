package ge.tbegvadze.toon3d.world;

/**
 * How a freshly-built {@link World} opens (Story UI order-8) — the handshake between the world that
 * just ended and the one {@code Main} builds to replace it.
 *
 * <p>A run is one {@code World}: ending one means disposing it and constructing the next.  What the
 * player should SEE at that seam depends entirely on why the last one ended, and that is the whole
 * of this enum.
 */
public enum StartMode {

    /**
     * Open on the launch / title screen: the app just started, or a run was abandoned or finished.
     * Nothing is running yet; the descent begins when the player picks it.
     */
    TITLE,

    /**
     * Drop straight into a fresh run, fading up from black.  The reprint card the player has just
     * tapped through was printed by the world that DIED — that is what makes death → reprint one
     * calm presentation instead of two competing screens, and it is why this mode shows no card of
     * its own.
     */
    RESPAWN
}
