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

        float half      = Constants.TOUCH_BUTTON_SIZE / 2f;
        float armOffset = Constants.TOUCH_DIAMOND_ARM_OFFSET;
        float centerX   = Constants.TOUCH_DIAMOND_CENTER_X;
        float centerY   = Constants.TOUCH_DIAMOND_CENTER_Y;

        buttons = new TouchButton[9];
        buttons[INDEX_FORWARD] = new TouchButton(
            centerX - half,          centerY + armOffset - half,
            Constants.TOUCH_BUTTON_SIZE, Constants.TOUCH_BUTTON_SIZE,
            TouchAction.FORWARD, TouchButton.Shape.ROUNDED_SQUARE);

        buttons[INDEX_BACK] = new TouchButton(
            centerX - half,          centerY - armOffset - half,
            Constants.TOUCH_BUTTON_SIZE, Constants.TOUCH_BUTTON_SIZE,
            TouchAction.BACK, TouchButton.Shape.ROUNDED_SQUARE);

        buttons[INDEX_ROTATE_LEFT] = new TouchButton(
            centerX - armOffset - half, centerY - half,
            Constants.TOUCH_BUTTON_SIZE, Constants.TOUCH_BUTTON_SIZE,
            TouchAction.ROTATE_LEFT, TouchButton.Shape.ROUNDED_SQUARE);

        buttons[INDEX_ROTATE_RIGHT] = new TouchButton(
            centerX + armOffset - half, centerY - half,
            Constants.TOUCH_BUTTON_SIZE, Constants.TOUCH_BUTTON_SIZE,
            TouchAction.ROTATE_RIGHT, TouchButton.Shape.ROUNDED_SQUARE);

        // Strafe pads: tall capsules inboard-left of the diamond
        buttons[INDEX_STRAFE_LEFT] = new TouchButton(
            Constants.TOUCH_STRAFE_COLUMN_X, Constants.TOUCH_STRAFE_UPPER_Y,
            Constants.TOUCH_STRAFE_WIDTH,    Constants.TOUCH_STRAFE_HEIGHT,
            TouchAction.STRAFE_LEFT, TouchButton.Shape.CAPSULE);

        buttons[INDEX_STRAFE_RIGHT] = new TouchButton(
            Constants.TOUCH_STRAFE_COLUMN_X, Constants.TOUCH_STRAFE_LOWER_Y,
            Constants.TOUCH_STRAFE_WIDTH,    Constants.TOUCH_STRAFE_HEIGHT,
            TouchAction.STRAFE_RIGHT, TouchButton.Shape.CAPSULE);

        // Action buttons — tap-only, left side of screen
        float fireHalf   = Constants.TOUCH_FIRE_SIZE   / 2f;
        float actionHalf = Constants.TOUCH_ACTION_SIZE / 2f;

        buttons[INDEX_FIRE] = new TouchButton(
            Constants.TOUCH_FIRE_CENTER_X   - fireHalf,   Constants.TOUCH_FIRE_CENTER_Y   - fireHalf,
            Constants.TOUCH_FIRE_SIZE,                    Constants.TOUCH_FIRE_SIZE,
            TouchAction.FIRE, TouchButton.Shape.ROUNDED_SQUARE, true);

        buttons[INDEX_RELOAD] = new TouchButton(
            Constants.TOUCH_RELOAD_CENTER_X - actionHalf, Constants.TOUCH_RELOAD_CENTER_Y - actionHalf,
            Constants.TOUCH_ACTION_SIZE,                  Constants.TOUCH_ACTION_SIZE,
            TouchAction.RELOAD, TouchButton.Shape.ROUNDED_SQUARE, true);

        buttons[INDEX_SKIP_TURN] = new TouchButton(
            Constants.TOUCH_SKIP_CENTER_X   - actionHalf, Constants.TOUCH_SKIP_CENTER_Y   - actionHalf,
            Constants.TOUCH_ACTION_SIZE,                  Constants.TOUCH_ACTION_SIZE,
            TouchAction.SKIP_TURN, TouchButton.Shape.ROUNDED_SQUARE, true);
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
