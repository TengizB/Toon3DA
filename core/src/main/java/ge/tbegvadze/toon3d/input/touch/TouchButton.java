package ge.tbegvadze.toon3d.input.touch;

public final class TouchButton {

    public enum Shape { ROUNDED_SQUARE, CAPSULE }

    final float       rectX, rectY, rectWidth, rectHeight;
    final TouchAction action;
    final Shape       shape;
    /** Tap-only buttons fire once on press and do not repeat while held. */
    final boolean     tapOnly;

    boolean pressed;
    int     pointerId;       // -1 = no finger bound to this button
    float   pressGlowTimer;  // counts down from TOUCH_PRESS_GLOW_DURATION → 0

    TouchButton(float rectX, float rectY, float rectWidth, float rectHeight,
                TouchAction action, Shape shape) {
        this(rectX, rectY, rectWidth, rectHeight, action, shape, false);
    }

    TouchButton(float rectX, float rectY, float rectWidth, float rectHeight,
                TouchAction action, Shape shape, boolean tapOnly) {
        this.rectX      = rectX;
        this.rectY      = rectY;
        this.rectWidth  = rectWidth;
        this.rectHeight = rectHeight;
        this.action     = action;
        this.shape      = shape;
        this.tapOnly    = tapOnly;
        this.pointerId  = -1;
    }

    boolean contains(float worldX, float worldY) {
        return worldX >= rectX && worldX <= rectX + rectWidth
            && worldY >= rectY && worldY <= rectY + rectHeight;
    }
}
