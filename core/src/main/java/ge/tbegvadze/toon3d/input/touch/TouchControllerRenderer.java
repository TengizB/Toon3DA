package ge.tbegvadze.toon3d.input.touch;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.render.Renderable;
import ge.tbegvadze.toon3d.util.TouchConstants;

public final class TouchControllerRenderer implements Renderable, Disposable {

    // Palette — datapad chrome + phosphor palette (matches HUD aesthetic)
    private static final float STEEL_DARK_R    = 0x1A / 255f;
    private static final float STEEL_DARK_G    = 0x1A / 255f;
    private static final float STEEL_DARK_B    = 0x1F / 255f;
    private static final float BEVEL_LIGHT_R   = 0x4A / 255f;
    private static final float BEVEL_LIGHT_G   = 0x4A / 255f;
    private static final float BEVEL_LIGHT_B   = 0x55 / 255f;
    private static final float BEVEL_DARK_R    = 0x08 / 255f;
    private static final float BEVEL_DARK_G    = 0x08 / 255f;
    private static final float BEVEL_DARK_B    = 0x0A / 255f;
    private static final float PHOSPHOR_R      = 0x00 / 255f;
    private static final float PHOSPHOR_G      = 0xFF / 255f;
    private static final float PHOSPHOR_B      = 0x88 / 255f;
    private static final float PHOSPHOR_DIM_R  = 0x00 / 255f;
    private static final float PHOSPHOR_DIM_G  = 0x55 / 255f;
    private static final float PHOSPHOR_DIM_B  = 0x3A / 255f;
    private static final float WARN_R          = 1.00f;
    private static final float WARN_G          = 0.87f;
    private static final float WARN_B          = 0.00f;

    private final ShapeRenderer shapeRenderer;
    private final TouchButton[] buttons;
    private boolean actionLocked;

    public TouchControllerRenderer(TouchInputState touchInputState) {
        this.buttons       = touchInputState.getButtons();
        this.shapeRenderer = new ShapeRenderer();
    }

    /** Dims icon alphas when the player controller is animating an action. */
    public void setActionLocked(boolean locked) { this.actionLocked = locked; }

    @Override
    public void render(OrthographicCamera camera) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (TouchButton button : buttons) {
            if (!button.visible) continue;
            drawBody(button);
            drawBevel(button);
            drawIcon(button);
        }

        shapeRenderer.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    // -------------------------------------------------------------------------
    // Body fill
    // -------------------------------------------------------------------------

    private void drawBody(TouchButton button) {
        float alpha = button.pressed ? TouchConstants.TOUCH_FILL_ALPHA_PRESSED : TouchConstants.TOUCH_FILL_ALPHA_IDLE;
        shapeRenderer.setColor(STEEL_DARK_R, STEEL_DARK_G, STEEL_DARK_B, alpha);
        if (button.shape == TouchButton.Shape.CAPSULE) {
            drawCapsule(button.rectX, button.rectY, button.rectWidth, button.rectHeight);
        } else {
            drawRoundedRect(button.rectX, button.rectY, button.rectWidth, button.rectHeight,
                            TouchConstants.TOUCH_BUTTON_CORNER_RADIUS);
        }
    }

    private void drawRoundedRect(float x, float y, float width, float height, float radius) {
        // Cross-shaped fill so the body covers the full bounding rect
        shapeRenderer.rect(x + radius, y,          width - 2 * radius, height);
        shapeRenderer.rect(x,          y + radius,  width,              height - 2 * radius);
        // Four corner quarter-pie sectors (filled arc = sector in Filled mode)
        shapeRenderer.arc(x + radius,         y + radius,          radius, 180, 90);
        shapeRenderer.arc(x + width - radius, y + radius,          radius, 270, 90);
        shapeRenderer.arc(x + width - radius, y + height - radius, radius,   0, 90);
        shapeRenderer.arc(x + radius,         y + height - radius, radius,  90, 90);
    }

    private void drawCapsule(float x, float y, float width, float height) {
        float radius = width / 2f;
        shapeRenderer.rect(x, y + radius, width, height - 2 * radius);
        shapeRenderer.arc(x + radius, y + radius,          radius, 180, 180); // bottom semicircle
        shapeRenderer.arc(x + radius, y + height - radius, radius,   0, 180); // top semicircle
    }

    // -------------------------------------------------------------------------
    // Bevel rim
    // -------------------------------------------------------------------------

    private void drawBevel(TouchButton button) {
        boolean pressed   = button.pressed;
        float   rimAlpha  = TouchConstants.TOUCH_RIM_ALPHA;
        float   thickness = TouchConstants.TOUCH_RIM_THICKNESS;
        float   x = button.rectX, y = button.rectY;
        float   w = button.rectWidth, h = button.rectHeight;

        // Yellow glow pulse on first press, fades over TOUCH_PRESS_GLOW_DURATION
        float glowFraction = TouchConstants.TOUCH_PRESS_GLOW_DURATION > 0f
            ? button.pressGlowTimer / TouchConstants.TOUCH_PRESS_GLOW_DURATION
            : 0f;

        float rimLightR = MathUtils.lerp(BEVEL_LIGHT_R, WARN_R, glowFraction);
        float rimLightG = MathUtils.lerp(BEVEL_LIGHT_G, WARN_G, glowFraction);
        float rimLightB = MathUtils.lerp(BEVEL_LIGHT_B, WARN_B, glowFraction);

        // Pressed state inverts the bevel (button looks physically depressed)
        float topLeftR, topLeftG, topLeftB, botRightR, botRightG, botRightB;
        if (pressed) {
            topLeftR = BEVEL_DARK_R;  topLeftG = BEVEL_DARK_G;  topLeftB = BEVEL_DARK_B;
            botRightR = rimLightR;    botRightG = rimLightG;     botRightB = rimLightB;
        } else {
            topLeftR = rimLightR;     topLeftG = rimLightG;      topLeftB = rimLightB;
            botRightR = BEVEL_DARK_R; botRightG = BEVEL_DARK_G;  botRightB = BEVEL_DARK_B;
        }

        // Top + left edges (raised side when idle)
        shapeRenderer.setColor(topLeftR, topLeftG, topLeftB, rimAlpha);
        shapeRenderer.rectLine(x, y + h, x + w, y + h, thickness); // top
        shapeRenderer.rectLine(x, y,     x,      y + h, thickness); // left

        // Bottom + right edges (sunken side when idle)
        shapeRenderer.setColor(botRightR, botRightG, botRightB, rimAlpha);
        shapeRenderer.rectLine(x,     y, x + w, y,     thickness); // bottom
        shapeRenderer.rectLine(x + w, y, x + w, y + h, thickness); // right
    }

    // -------------------------------------------------------------------------
    // Icons
    // -------------------------------------------------------------------------

    private void drawIcon(TouchButton button) {
        float cx = button.rectX + button.rectWidth  / 2f;
        float cy = button.rectY + button.rectHeight / 2f;

        float iconAlpha = actionLocked
            ? TouchConstants.TOUCH_ICON_ALPHA_LOCKED
            : (button.pressed ? TouchConstants.TOUCH_ICON_ALPHA_PRESSED : TouchConstants.TOUCH_ICON_ALPHA_IDLE);

        float extent = TouchConstants.TOUCH_ICON_EXTENT;

        // Soft halo: redraw slightly larger in dim phosphor to keep glyphs readable over bright walls
        shapeRenderer.setColor(PHOSPHOR_DIM_R, PHOSPHOR_DIM_G, PHOSPHOR_DIM_B, iconAlpha * 0.55f);
        drawGlyph(button.action, cx, cy, extent + 3f);

        // Crisp foreground glyph in phosphor green
        shapeRenderer.setColor(PHOSPHOR_R, PHOSPHOR_G, PHOSPHOR_B, iconAlpha);
        drawGlyph(button.action, cx, cy, extent);
    }

    private void drawGlyph(TouchAction action, float cx, float cy, float extent) {
        switch (action) {
            case FORWARD:         drawTriangleUp(cx, cy, extent);              break;
            case BACK:            drawTriangleDown(cx, cy, extent);            break;
            case ROTATE_LEFT:     drawRotateArrow(cx, cy, extent, true);       break;
            case ROTATE_RIGHT:    drawRotateArrow(cx, cy, extent, false);      break;
            case STRAFE_LEFT:     drawDoubleChevron(cx, cy, extent, true);     break;
            case STRAFE_RIGHT:    drawDoubleChevron(cx, cy, extent, false);    break;
            case FIRE:            drawCrosshair(cx, cy, extent);               break;
            case SKIP_TURN:       drawSkipIcon(cx, cy, extent);                break;
            case RELOAD:          drawReloadIcon(cx, cy, extent);              break;
            case SWITCH_WEAPON:   drawSwitchWeaponIcon(cx, cy, extent);        break;
            case HEAL:            drawHealIcon(cx, cy, extent);                break;
            case GUARD:           drawGuardIcon(cx, cy, extent);               break;
            case OPEN_INVENTORY:  drawInventoryIcon(cx, cy, extent);           break;
            case INSPECT_WEAPON:  drawInspectIcon(cx, cy, extent);             break;
            case USE_MACHINE:     drawUseMachineIcon(cx, cy, extent);          break;
            default: break;
        }
    }

    /**
     * Vending-machine icon: a tall cabinet outline with a product window near the top and a
     * dispenser-tray slot near the bottom — reads as "use the fabricator".
     */
    private void drawUseMachineIcon(float cx, float cy, float extent) {
        float lineWidth   = Math.max(2f, extent * 0.12f);
        float halfWidth   = extent * 0.48f;
        float halfHeight  = extent * 0.72f;
        float left        = cx - halfWidth;
        float right       = cx + halfWidth;
        float bottom      = cy - halfHeight;
        float top         = cy + halfHeight;

        // Cabinet outline
        shapeRenderer.rectLine(left,  bottom, right, bottom, lineWidth);
        shapeRenderer.rectLine(right, bottom, right, top,    lineWidth);
        shapeRenderer.rectLine(right, top,    left,  top,    lineWidth);
        shapeRenderer.rectLine(left,  top,    left,  bottom, lineWidth);

        // Product window (upper inset rectangle)
        float windowInset  = extent * 0.18f;
        float windowTop    = top - windowInset;
        float windowBottom = cy + extent * 0.06f;
        shapeRenderer.rectLine(left + windowInset,  windowTop,    right - windowInset, windowTop,    lineWidth);
        shapeRenderer.rectLine(right - windowInset, windowTop,    right - windowInset, windowBottom, lineWidth);
        shapeRenderer.rectLine(right - windowInset, windowBottom, left + windowInset,  windowBottom, lineWidth);
        shapeRenderer.rectLine(left + windowInset,  windowBottom, left + windowInset,  windowTop,    lineWidth);

        // Dispenser-tray slot (lower horizontal bar)
        float trayY = bottom + extent * 0.24f;
        shapeRenderer.rectLine(left + windowInset, trayY, right - windowInset, trayY, lineWidth * 1.3f);
    }

    /** Magnifying-glass icon: circle outline + handle line at bottom-right. */
    private void drawInspectIcon(float cx, float cy, float extent) {
        float radius    = extent * 0.45f;
        float lineWidth = Math.max(2f, extent * 0.13f);
        float handleLen = extent * 0.40f;
        int   segments  = 10;
        // Circle
        for (int segmentIndex = 0; segmentIndex < segments; segmentIndex++) {
            float angle1 = 360f / segments * segmentIndex;
            float angle2 = 360f / segments * (segmentIndex + 1);
            float x1 = cx - extent * 0.10f + radius * MathUtils.cosDeg(angle1);
            float y1 = cy + extent * 0.10f + radius * MathUtils.sinDeg(angle1);
            float x2 = cx - extent * 0.10f + radius * MathUtils.cosDeg(angle2);
            float y2 = cy + extent * 0.10f + radius * MathUtils.sinDeg(angle2);
            shapeRenderer.rectLine(x1, y1, x2, y2, lineWidth);
        }
        // Handle
        float handleStartX = cx - extent * 0.10f + radius * MathUtils.cosDeg(315f);
        float handleStartY = cy + extent * 0.10f + radius * MathUtils.sinDeg(315f);
        shapeRenderer.rectLine(handleStartX, handleStartY,
                               handleStartX + handleLen * MathUtils.cosDeg(315f),
                               handleStartY + handleLen * MathUtils.sinDeg(315f),
                               lineWidth * 1.5f);
    }

    /**
     * Switch-weapon icon: two stacked horizontal bars (representing different weapons)
     * with a right-pointing arrow indicating cycling forward.
     */
    private void drawSwitchWeaponIcon(float cx, float cy, float extent) {
        float lineWidth  = Math.max(2f, extent * 0.13f);
        float barHalf    = extent * 0.55f;
        float spacing    = extent * 0.28f;
        float arrowSize  = extent * 0.28f;

        // Two horizontal bars representing weapon silhouettes
        shapeRenderer.rectLine(cx - barHalf, cy + spacing, cx + barHalf * 0.45f, cy + spacing, lineWidth);
        shapeRenderer.rectLine(cx - barHalf, cy - spacing, cx + barHalf * 0.45f, cy - spacing, lineWidth);

        // Right-pointing arrowhead at the right end of the top bar — indicates "next"
        float tipX = cx + barHalf * 0.45f;
        float tipY = cy + spacing;
        shapeRenderer.triangle(
            tipX,              tipY,
            tipX - arrowSize,  tipY + arrowSize * 0.55f,
            tipX - arrowSize,  tipY - arrowSize * 0.55f);
    }

    /** Solid up-pointing triangle (▲). */
    private void drawTriangleUp(float cx, float cy, float extent) {
        float halfBase = extent * 0.75f;
        float rise     = extent * 0.90f;
        shapeRenderer.triangle(
            cx,            cy + rise * 0.55f,
            cx - halfBase, cy - rise * 0.45f,
            cx + halfBase, cy - rise * 0.45f);
    }

    /** Solid down-pointing triangle (▽). */
    private void drawTriangleDown(float cx, float cy, float extent) {
        float halfBase = extent * 0.75f;
        float rise     = extent * 0.90f;
        shapeRenderer.triangle(
            cx,            cy - rise * 0.55f,
            cx - halfBase, cy + rise * 0.45f,
            cx + halfBase, cy + rise * 0.45f);
    }

    /**
     * Bent "turn left/right" arrow — a vertical arm rising from center with a
     * horizontal bar at the top and an arrowhead pointing left (CCW) or right (CW).
     * Clearly distinct from the straight double-chevron strafe icons.
     */
    private void drawRotateArrow(float cx, float cy, float extent, boolean counterClockwise) {
        float lineWidth = Math.max(2f, extent * 0.13f);
        float armLength = extent * 0.75f;
        float barLength = extent * 0.80f;
        float arrowSize = extent * 0.30f;

        // Vertical arm: rises from below-center to above-center
        float armX       = counterClockwise ? cx + armLength * 0.25f : cx - armLength * 0.25f;
        float armBottomY = cy - armLength * 0.45f;
        float armTopY    = cy + armLength * 0.45f;
        shapeRenderer.rectLine(armX, armBottomY, armX, armTopY, lineWidth);

        // Horizontal bar at top, extending toward the arrowhead
        float barStartX  = armX;
        float barEndX    = counterClockwise ? armX - barLength : armX + barLength;
        float barY       = armTopY;
        shapeRenderer.rectLine(barStartX, barY, barEndX, barY, lineWidth);

        // Arrowhead at the tip of the horizontal bar
        float tipX      = barEndX;
        float tipY      = barY;
        float arrowDeg  = counterClockwise ? 180f : 0f;
        float fin1X     = tipX + MathUtils.cosDeg(arrowDeg + 140f) * arrowSize;
        float fin1Y     = tipY + MathUtils.sinDeg(arrowDeg + 140f) * arrowSize;
        float fin2X     = tipX + MathUtils.cosDeg(arrowDeg - 140f) * arrowSize;
        float fin2Y     = tipY + MathUtils.sinDeg(arrowDeg - 140f) * arrowSize;
        shapeRenderer.triangle(tipX, tipY, fin1X, fin1Y, fin2X, fin2Y);
    }

    /**
     * Double chevron (<<  or  >>) for strafe actions.
     * Clearly distinct from the bent-arrow rotate icons.
     */
    private void drawDoubleChevron(float cx, float cy, float extent, boolean pointingLeft) {
        float halfArm   = extent * 0.22f;
        float halfVert  = extent * 0.52f;
        float spacing   = extent * 0.44f;
        float lineWidth = Math.max(2f, extent * 0.13f);

        // Two chevrons, each offset horizontally from center
        float[] offsets = { -spacing / 2f, spacing / 2f };
        for (float offsetX : offsets) {
            float apexX;
            float openX;
            if (pointingLeft) {
                apexX = cx + offsetX - halfArm;
                openX = cx + offsetX + halfArm;
            } else {
                apexX = cx + offsetX + halfArm;
                openX = cx + offsetX - halfArm;
            }
            shapeRenderer.rectLine(openX, cy + halfVert, apexX, cy, lineWidth);
            shapeRenderer.rectLine(apexX, cy, openX, cy - halfVert, lineWidth);
        }
    }

    /** Crosshair: four short arms with a filled dot at centre. */
    private void drawCrosshair(float cx, float cy, float extent) {
        float lineWidth  = Math.max(2f, extent * 0.15f);
        float arm        = extent * 0.80f;
        float gapRadius  = extent * 0.24f;
        shapeRenderer.rectLine(cx - arm, cy, cx - gapRadius, cy, lineWidth);
        shapeRenderer.rectLine(cx + gapRadius, cy, cx + arm, cy, lineWidth);
        shapeRenderer.rectLine(cx, cy - arm, cx, cy - gapRadius, lineWidth);
        shapeRenderer.rectLine(cx, cy + gapRadius, cx, cy + arm, lineWidth);
        shapeRenderer.circle(cx, cy, gapRadius * 0.55f, 8);
    }

    /** Skip icon: vertical bar followed by a right-pointing filled triangle. */
    private void drawSkipIcon(float cx, float cy, float extent) {
        float lineWidth = Math.max(2f, extent * 0.16f);
        float halfH     = extent * 0.70f;
        float barX      = cx - extent * 0.55f;
        float triLeft   = cx - extent * 0.08f;
        float triRight  = cx + extent * 0.62f;
        shapeRenderer.rectLine(barX, cy - halfH, barX, cy + halfH, lineWidth * 1.4f);
        shapeRenderer.triangle(triRight, cy, triLeft, cy + halfH, triLeft, cy - halfH);
    }

    /** Reload icon: circular arc (300°) with an arrowhead at the open end. */
    private void drawReloadIcon(float cx, float cy, float extent) {
        float radius    = extent * 0.65f;
        float lineWidth = Math.max(2f, extent * 0.14f);
        float arrowSize = extent * 0.30f;
        float startDeg  = 110f;
        float arcSpan   = 300f;
        int   segments  = 12;
        for (int segmentIndex = 0; segmentIndex < segments; segmentIndex++) {
            float angle1 = startDeg + arcSpan / segments * segmentIndex;
            float angle2 = startDeg + arcSpan / segments * (segmentIndex + 1);
            float x1 = cx + radius * MathUtils.cosDeg(angle1);
            float y1 = cy + radius * MathUtils.sinDeg(angle1);
            float x2 = cx + radius * MathUtils.cosDeg(angle2);
            float y2 = cy + radius * MathUtils.sinDeg(angle2);
            shapeRenderer.rectLine(x1, y1, x2, y2, lineWidth);
        }
        float endDeg     = startDeg + arcSpan;
        float tipX       = cx + radius * MathUtils.cosDeg(endDeg);
        float tipY       = cy + radius * MathUtils.sinDeg(endDeg);
        float tangentDeg = endDeg + 90f;
        float fin1X = tipX + MathUtils.cosDeg(tangentDeg + 140f) * arrowSize;
        float fin1Y = tipY + MathUtils.sinDeg(tangentDeg + 140f) * arrowSize;
        float fin2X = tipX + MathUtils.cosDeg(tangentDeg - 140f) * arrowSize;
        float fin2Y = tipY + MathUtils.sinDeg(tangentDeg - 140f) * arrowSize;
        shapeRenderer.triangle(tipX, tipY, fin1X, fin1Y, fin2X, fin2Y);
    }

    /**
     * Shield icon: a heater-shield outline — flat top edge, straight upper sides, tapering to a
     * point at the bottom. Reads as "brace / guard" (strategy-combat-order-4).
     */
    private void drawGuardIcon(float cx, float cy, float extent) {
        float lineWidth = Math.max(2f, extent * 0.13f);
        float halfWidth = extent * 0.55f;
        float top       = cy + extent * 0.68f;
        float shoulder  = cy + extent * 0.10f; // where the straight sides start curving inward
        float tipY      = cy - extent * 0.78f;
        float leftX     = cx - halfWidth;
        float rightX    = cx + halfWidth;
        // Top edge
        shapeRenderer.rectLine(leftX, top, rightX, top, lineWidth);
        // Upper sides (vertical) down to the shoulders
        shapeRenderer.rectLine(leftX,  top, leftX,  shoulder, lineWidth);
        shapeRenderer.rectLine(rightX, top, rightX, shoulder, lineWidth);
        // Lower sides taper to the point
        shapeRenderer.rectLine(leftX,  shoulder, cx, tipY, lineWidth);
        shapeRenderer.rectLine(rightX, shoulder, cx, tipY, lineWidth);
    }

    /** Medical cross (+): two overlapping thick bars forming a plus sign. */
    private void drawHealIcon(float cx, float cy, float extent) {
        float barHalf  = extent * 0.60f;
        float armWidth = extent * 0.28f;
        shapeRenderer.rectLine(cx - barHalf, cy, cx + barHalf, cy, armWidth);
        shapeRenderer.rectLine(cx, cy - barHalf, cx, cy + barHalf, armWidth);
    }

    /** Inventory grid icon: four small squares arranged in a 2×2 grid. */
    private void drawInventoryIcon(float cx, float cy, float extent) {
        float squareHalf = extent * 0.22f;
        float spacing    = extent * 0.28f;
        float lineWidth  = Math.max(2f, extent * 0.13f);
        // Top-left, top-right, bottom-left, bottom-right squares (outline only)
        float[] offsetsX = { -spacing, spacing, -spacing, spacing };
        float[] offsetsY = {  spacing, spacing, -spacing, -spacing };
        for (int squareIndex = 0; squareIndex < 4; squareIndex++) {
            float left   = cx + offsetsX[squareIndex] - squareHalf;
            float bottom = cy + offsetsY[squareIndex] - squareHalf;
            float right  = left   + squareHalf * 2f;
            float top    = bottom + squareHalf * 2f;
            shapeRenderer.rectLine(left,  bottom, right, bottom, lineWidth);
            shapeRenderer.rectLine(right, bottom, right, top,    lineWidth);
            shapeRenderer.rectLine(right, top,    left,  top,    lineWidth);
            shapeRenderer.rectLine(left,  top,    left,  bottom, lineWidth);
        }
    }

    // -------------------------------------------------------------------------
    // Dispose
    // -------------------------------------------------------------------------

    @Override
    public void dispose() {
        shapeRenderer.dispose();
    }
}
