package ge.tbegvadze.toon3d.sim;

import ge.tbegvadze.toon3d.input.touch.ActionSource;
import ge.tbegvadze.toon3d.input.touch.TouchAction;

/**
 * The simulator's stand-in for the on-screen thumb cluster (new-game-balancr order 9).
 *
 * <p>{@link SimWorld} writes the policy's chosen action here, and the REAL
 * {@code PlayerController} reads it through the {@link ActionSource} seam — so a simulated turn goes
 * through the same {@code pollInput} switch, the same {@code tryMove}/{@code tryFire}/{@code tryHeal}
 * entry points, and the same tick as a tapped button. Held-style actions (movement) are reported as
 * held; tap-style actions (fire, heal, guard, …) are reported once and then cleared, mirroring
 * {@code TouchInputState}'s two channels.
 */
final class ScriptedActionSource implements ActionSource {

    private TouchAction heldAction = TouchAction.NONE;
    private TouchAction tapAction  = TouchAction.NONE;

    /** Presents one action to the controller, routed to the channel that action really uses. */
    void present(TouchAction action) {
        heldAction = TouchAction.NONE;
        tapAction  = TouchAction.NONE;
        if (action == null || action == TouchAction.NONE) return;
        if (isHeldStyle(action)) {
            heldAction = action;
        } else {
            tapAction = action;
        }
    }

    /** Clears both channels — called once an action has been consumed so it can never repeat. */
    void clear() {
        heldAction = TouchAction.NONE;
        tapAction  = TouchAction.NONE;
    }

    @Override
    public TouchAction getHeldAction() {
        return heldAction;
    }

    @Override
    public TouchAction consumeTapAction() {
        TouchAction action = tapAction;
        tapAction = TouchAction.NONE;
        return action;
    }

    /** The movement/rotation buttons are the HELD ones; every other button is tap-only. */
    private static boolean isHeldStyle(TouchAction action) {
        switch (action) {
            case FORWARD:
            case BACK:
            case ROTATE_LEFT:
            case ROTATE_RIGHT:
            case STRAFE_LEFT:
            case STRAFE_RIGHT:
                return true;
            default:
                return false;
        }
    }
}
