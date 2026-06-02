package ge.tbegvadze.toon3d.input.touch;

import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import ge.tbegvadze.toon3d.util.Constants;

public final class TouchInputState extends InputAdapter {

    // Ordered by action priority: index 0 = highest priority
    private static final int INDEX_FORWARD      = 0;
    private static final int INDEX_BACK         = 1;
    private static final int INDEX_ROTATE_LEFT  = 2;
    private static final int INDEX_ROTATE_RIGHT = 3;
    private static final int INDEX_STRAFE_LEFT  = 4;
    private static final int INDEX_STRAFE_RIGHT = 5;
    private static final int INDEX_FIRE         = 6;
    private static final int INDEX_RELOAD       = 7;
    private static final int INDEX_SKIP_TURN    = 8;

    private final TouchButton[]  buttons;
    private final Viewport       viewport;
    private final Vector2        touchWorldCoords; // pre-allocated; never recreated in event handlers
    private       TouchAction    pendingTapAction = TouchAction.NONE;

    public TouchInputState(Viewport viewport) {
        this.viewport         = viewport;
        this.touchWorldCoords = new Vector2();

        float size    = Constants.TOUCH_BUTTON_SIZE;
        float half    = size / 2f;
        float arm     = Constants.TOUCH_GRID_ARM_OFFSET;
        float centerX = Constants.TOUCH_GRID_CENTER_X;
        float baseY   = Constants.TOUCH_GRID_BASE_Y;    // center Y of row 1 (BACK)

        buttons = new TouchButton[9];

        // Row 1 (bottom) — center column only
        buttons[INDEX_BACK] = new TouchButton(
            centerX - half,       baseY - half,
            size, size, TouchAction.BACK, TouchButton.Shape.ROUNDED_SQUARE);

        // Row 2 — ROT_L | FIRE | ROT_R
        float row2Y = baseY + arm;
        buttons[INDEX_ROTATE_LEFT] = new TouchButton(
            centerX - arm - half, row2Y - half,
            size, size, TouchAction.ROTATE_LEFT, TouchButton.Shape.ROUNDED_SQUARE);
        buttons[INDEX_FIRE] = new TouchButton(
            centerX - half,       row2Y - half,
            size, size, TouchAction.FIRE, TouchButton.Shape.ROUNDED_SQUARE, true);
        buttons[INDEX_ROTATE_RIGHT] = new TouchButton(
            centerX + arm - half, row2Y - half,
            size, size, TouchAction.ROTATE_RIGHT, TouchButton.Shape.ROUNDED_SQUARE);

        // Row 3 — STR_L | FORWARD | STR_R
        float row3Y = baseY + 2 * arm;
        buttons[INDEX_STRAFE_LEFT] = new TouchButton(
            centerX - arm - half, row3Y - half,
            size, size, TouchAction.STRAFE_LEFT, TouchButton.Shape.ROUNDED_SQUARE);
        buttons[INDEX_FORWARD] = new TouchButton(
            centerX - half,       row3Y - half,
            size, size, TouchAction.FORWARD, TouchButton.Shape.ROUNDED_SQUARE);
        buttons[INDEX_STRAFE_RIGHT] = new TouchButton(
            centerX + arm - half, row3Y - half,
            size, size, TouchAction.STRAFE_RIGHT, TouchButton.Shape.ROUNDED_SQUARE);

        // Row 4 (top) — [blank] | RELOAD | SKIP
        float row4Y = baseY + 3 * arm;
        buttons[INDEX_RELOAD] = new TouchButton(
            centerX - half,       row4Y - half,
            size, size, TouchAction.RELOAD, TouchButton.Shape.ROUNDED_SQUARE, true);
        buttons[INDEX_SKIP_TURN] = new TouchButton(
            centerX + arm - half, row4Y - half,
            size, size, TouchAction.SKIP_TURN, TouchButton.Shape.ROUNDED_SQUARE, true);
    }

    /** Returns the highest-priority currently-pressed held action (movement), or NONE. */
    public TouchAction getHeldAction() {
        for (TouchButton button : buttons) {
            if (button.pressed && !button.tapOnly) return button.action;
        }
        return TouchAction.NONE;
    }

    /**
     * Returns and clears the pending tap action (FIRE, RELOAD, SKIP_TURN).
     * Returns NONE if no tap is pending.
     */
    public TouchAction consumeTapAction() {
        TouchAction tap   = pendingTapAction;
        pendingTapAction  = TouchAction.NONE;
        return tap;
    }

    public TouchButton[] getButtons() { return buttons; }

    public void update(float deltaTime) {
        for (TouchButton button : buttons) {
            if (button.pressGlowTimer > 0f) {
                button.pressGlowTimer = Math.max(0f, button.pressGlowTimer - deltaTime);
            }
        }
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        toWorldCoords(screenX, screenY);
        for (TouchButton touchButton : buttons) {
            if (touchButton.pointerId == -1 && touchButton.contains(touchWorldCoords.x, touchWorldCoords.y)) {
                touchButton.pressed        = true;
                touchButton.pointerId      = pointer;
                touchButton.pressGlowTimer = Constants.TOUCH_PRESS_GLOW_DURATION;
                if (touchButton.tapOnly) {
                    pendingTapAction = touchButton.action;
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        for (TouchButton touchButton : buttons) {
            if (touchButton.pointerId == pointer) {
                touchButton.pressed   = false;
                touchButton.pointerId = -1;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        toWorldCoords(screenX, screenY);
        for (TouchButton touchButton : buttons) {
            if (touchButton.pointerId == pointer && !touchButton.contains(touchWorldCoords.x, touchWorldCoords.y)) {
                touchButton.pressed   = false;
                touchButton.pointerId = -1;
            }
        }
        return false;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        return touchUp(screenX, screenY, pointer, button);
    }

    private void toWorldCoords(int screenX, int screenY) {
        touchWorldCoords.set(screenX, screenY);
        viewport.unproject(touchWorldCoords);
    }
}
