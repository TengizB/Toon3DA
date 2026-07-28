package ge.tbegvadze.toon3d.input.touch;

/**
 * The two questions {@link ge.tbegvadze.toon3d.input.PlayerController} asks its input device every
 * frame: "is a movement button HELD?" and "was an action button TAPPED?".
 *
 * <p>On a real device the answer comes from {@link TouchInputState} (the on-screen thumb cluster —
 * still the ONLY input source a player ever has; there is no keyboard on this platform). This
 * interface exists so the headless balance simulator (new-game-balancr order 9,
 * {@code sim/BalanceSimulator}) can answer the same two questions from a scripted
 * {@code PlayerPolicy} and drive the REAL controller through the REAL action entry points, instead
 * of duplicating the movement/fire/heal rules in a parallel test-only pipeline.
 *
 * <p>Deliberately narrow: everything else on {@link TouchInputState} (button geometry, visibility,
 * the tap buffer) belongs to the touch renderer and is not part of this seam.
 */
public interface ActionSource {

    /** The highest-priority currently-HELD action (movement), or {@link TouchAction#NONE}. */
    TouchAction getHeldAction();

    /** Returns and clears the pending TAPPED action (FIRE, HEAL, …), or {@link TouchAction#NONE}. */
    TouchAction consumeTapAction();
}
