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

    private final TouchButton[] buttons;
    private final Viewport      viewport;
    private final Vector2       touchWorldCoords; // pre-allocated; never recreated in event handlers

    public TouchInputState(Viewport viewport) {
        this.viewport         = viewport;
        this.touchWorldCoords = new Vector2();

        float half      = Constants.TOUCH_BUTTON_SIZE / 2f;
        float armOffset = Constants.TOUCH_DIAMOND_ARM_OFFSET;
        float centerX   = Constants.TOUCH_DIAMOND_CENTER_X;
        float centerY   = Constants.TOUCH_DIAMOND_CENTER_Y;

        buttons = new TouchButton[6];
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
    }

    /** Returns the highest-priority currently-pressed action, or NONE. */
    public TouchAction getHeldAction() {
        for (TouchButton button : buttons) {
            if (button.pressed) return button.action;
        }
        return TouchAction.NONE;
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
