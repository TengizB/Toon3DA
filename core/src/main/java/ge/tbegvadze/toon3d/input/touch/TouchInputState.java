package ge.tbegvadze.toon3d.input.touch;

import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import ge.tbegvadze.toon3d.util.TouchConstants;

public final class TouchInputState extends InputAdapter {

    // Ordered by action priority: index 0 = highest priority
    private static final int INDEX_FORWARD         = 0;
    private static final int INDEX_BACK            = 1;
    private static final int INDEX_ROTATE_LEFT     = 2;
    private static final int INDEX_ROTATE_RIGHT    = 3;
    private static final int INDEX_STRAFE_LEFT     = 4;
    private static final int INDEX_STRAFE_RIGHT    = 5;
    private static final int INDEX_FIRE            = 6;
    private static final int INDEX_RELOAD          = 7;
    private static final int INDEX_SKIP_TURN       = 8;
    private static final int INDEX_SWITCH_WEAPON   = 9;
    private static final int INDEX_HEAL            = 10;
    private static final int INDEX_OPEN_INVENTORY  = 11;
    private static final int INDEX_INSPECT_WEAPON  = 12;
    private static final int INDEX_USE_MACHINE     = 13;
    private static final int INDEX_GUARD           = 14;

    private final TouchButton[]  buttons;
    private final Viewport       viewport;
    private final Vector2        touchWorldCoords; // pre-allocated; never recreated in event handlers
    private       TouchAction    pendingTapAction  = TouchAction.NONE;
    // Tap buffer: latch the most recent tap action for TOUCH_TAP_BUFFER_SECONDS so a tap
    // during a mid-action animation is not silently dropped. Newest tap always wins.
    private       TouchAction    bufferedTapAction = TouchAction.NONE;
    private       float          bufferedTapTimer  = 0f;

    public TouchInputState(Viewport viewport) {
        this.viewport         = viewport;
        this.touchWorldCoords = new Vector2();

        float size    = TouchConstants.TOUCH_BUTTON_SIZE;
        float half    = size / 2f;
        float arm     = TouchConstants.TOUCH_GRID_ARM_OFFSET;
        float centerX = TouchConstants.TOUCH_GRID_CENTER_X;
        float baseY   = TouchConstants.TOUCH_GRID_BASE_Y;    // center Y of row 1 (BACK)

        buttons = new TouchButton[15];

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

        // Row 4 (top) — SWITCH_WEAPON | RELOAD | SKIP
        float row4Y = baseY + 3 * arm;
        buttons[INDEX_SWITCH_WEAPON] = new TouchButton(
            centerX - arm - half, row4Y - half,
            size, size, TouchAction.SWITCH_WEAPON, TouchButton.Shape.ROUNDED_SQUARE, true);
        buttons[INDEX_RELOAD] = new TouchButton(
            centerX - half,       row4Y - half,
            size, size, TouchAction.RELOAD, TouchButton.Shape.ROUNDED_SQUARE, true);
        buttons[INDEX_SKIP_TURN] = new TouchButton(
            centerX + arm - half, row4Y - half,
            size, size, TouchAction.SKIP_TURN, TouchButton.Shape.ROUNDED_SQUARE, true);

        // GUARD — bottom-right corner of the combat cluster (row 1 right column, the empty slot beside
        // BACK). The defensive sibling of SKIP: a tap-only, turn-ending brace within thumb reach of the
        // movement/rotate keys so the player can rotate-to-face then guard (strategy-combat-order-4).
        buttons[INDEX_GUARD] = new TouchButton(
            centerX + arm - half, baseY - half,
            size, size, TouchAction.GUARD, TouchButton.Shape.ROUNDED_SQUARE, true);

        // Left secondary cluster — HEAL (row 2 height) | OPEN_INVENTORY (row 3 height)
        float leftCenterX = TouchConstants.TOUCH_GRID_LEFT_CENTER_X;
        buttons[INDEX_HEAL] = new TouchButton(
            leftCenterX - half, (baseY + arm) - half,
            size, size, TouchAction.HEAL, TouchButton.Shape.ROUNDED_SQUARE, true);
        buttons[INDEX_OPEN_INVENTORY] = new TouchButton(
            leftCenterX - half, (baseY + 2 * arm) - half,
            size, size, TouchAction.OPEN_INVENTORY, TouchButton.Shape.ROUNDED_SQUARE, true);

        // Contextual INSPECT button — left cluster row 1; hidden until player stands on a weapon
        float inspectHalf = TouchConstants.INSPECT_BUTTON_SIZE / 2f;
        TouchButton inspectButton = new TouchButton(
            TouchConstants.INSPECT_BUTTON_CENTER_X - inspectHalf,
            TouchConstants.INSPECT_BUTTON_CENTER_Y - inspectHalf,
            TouchConstants.INSPECT_BUTTON_SIZE,
            TouchConstants.INSPECT_BUTTON_SIZE,
            TouchAction.INSPECT_WEAPON,
            TouchButton.Shape.ROUNDED_SQUARE,
            true);
        inspectButton.visible = false;
        buttons[INDEX_INSPECT_WEAPON] = inspectButton;

        // Contextual USE button — left cluster, top row; hidden until player faces a vending machine
        float useHalf = TouchConstants.USE_BUTTON_SIZE / 2f;
        TouchButton useButton = new TouchButton(
            TouchConstants.USE_BUTTON_CENTER_X - useHalf,
            TouchConstants.USE_BUTTON_CENTER_Y - useHalf,
            TouchConstants.USE_BUTTON_SIZE,
            TouchConstants.USE_BUTTON_SIZE,
            TouchAction.USE_MACHINE,
            TouchButton.Shape.ROUNDED_SQUARE,
            true);
        useButton.visible = false;
        buttons[INDEX_USE_MACHINE] = useButton;
    }

    /** Returns the highest-priority currently-pressed held action (movement), or NONE. */
    public TouchAction getHeldAction() {
        for (TouchButton button : buttons) {
            if (button.pressed && !button.tapOnly) return button.action;
        }
        return TouchAction.NONE;
    }

    /**
     * Returns and clears the pending tap action (FIRE, RELOAD, etc.).
     * First checks the immediate pending slot (same-frame tap); falls through to the
     * cross-frame buffer if valid. Returns NONE when nothing is waiting.
     */
    public TouchAction consumeTapAction() {
        if (pendingTapAction != TouchAction.NONE) {
            TouchAction tap   = pendingTapAction;
            pendingTapAction  = TouchAction.NONE;
            bufferedTapAction = TouchAction.NONE;
            bufferedTapTimer  = 0f;
            return tap;
        }
        if (bufferedTapTimer > 0f) {
            TouchAction tap   = bufferedTapAction;
            bufferedTapAction = TouchAction.NONE;
            bufferedTapTimer  = 0f;
            return tap;
        }
        return TouchAction.NONE;
    }

    public TouchButton[] getButtons() { return buttons; }

    /**
     * Shows or hides the contextual INSPECT_WEAPON button.
     * Hiding also clears any pressed / buffered state on that button so a tap
     * queued while the button was visible is not replayed after it disappears.
     */
    public void setInspectButtonVisible(boolean visible) {
        TouchButton button = buttons[INDEX_INSPECT_WEAPON];
        if (button.visible == visible) return;
        button.visible = visible;
        if (!visible) {
            button.pressed        = false;
            button.pointerId      = -1;
            button.pressGlowTimer = 0f;
            if (pendingTapAction  == TouchAction.INSPECT_WEAPON) pendingTapAction  = TouchAction.NONE;
            if (bufferedTapAction == TouchAction.INSPECT_WEAPON) {
                bufferedTapAction = TouchAction.NONE;
                bufferedTapTimer  = 0f;
            }
        }
    }

    /**
     * Shows or hides the contextual USE_MACHINE button.
     * Hiding also clears any pressed / buffered state so a tap queued while the button was
     * visible is not replayed after it disappears (same contract as the INSPECT button).
     */
    public void setUseMachineButtonVisible(boolean visible) {
        TouchButton button = buttons[INDEX_USE_MACHINE];
        if (button.visible == visible) return;
        button.visible = visible;
        if (!visible) {
            button.pressed        = false;
            button.pointerId      = -1;
            button.pressGlowTimer = 0f;
            if (pendingTapAction  == TouchAction.USE_MACHINE) pendingTapAction  = TouchAction.NONE;
            if (bufferedTapAction == TouchAction.USE_MACHINE) {
                bufferedTapAction = TouchAction.NONE;
                bufferedTapTimer  = 0f;
            }
        }
    }

    /** Releases all held buttons and clears any pending or buffered tap action. */
    public void resetAllButtonStates() {
        for (TouchButton button : buttons) {
            button.pressed        = false;
            button.pointerId      = -1;
            button.pressGlowTimer = 0f;
        }
        pendingTapAction  = TouchAction.NONE;
        bufferedTapAction = TouchAction.NONE;
        bufferedTapTimer  = 0f;
    }

    public void update(float deltaTime) {
        for (TouchButton button : buttons) {
            if (button.pressGlowTimer > 0f) {
                button.pressGlowTimer = Math.max(0f, button.pressGlowTimer - deltaTime);
            }
        }
        if (bufferedTapTimer > 0f) {
            bufferedTapTimer -= deltaTime;
            if (bufferedTapTimer <= 0f) {
                bufferedTapTimer  = 0f;
                bufferedTapAction = TouchAction.NONE;
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
                touchButton.pressGlowTimer = TouchConstants.TOUCH_PRESS_GLOW_DURATION;
                if (touchButton.tapOnly) {
                    // Newest tap always wins for both the immediate slot and the cross-frame buffer.
                    pendingTapAction  = touchButton.action;
                    bufferedTapAction = touchButton.action;
                    bufferedTapTimer  = TouchConstants.TOUCH_TAP_BUFFER_SECONDS;
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
