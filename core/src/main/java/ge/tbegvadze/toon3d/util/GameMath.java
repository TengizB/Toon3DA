package ge.tbegvadze.toon3d.util;

import com.badlogic.gdx.math.MathUtils;
import ge.tbegvadze.toon3d.util.EnemyConstants;
import ge.tbegvadze.toon3d.util.RenderConstants;

/**
 * Central repository for every mathematical formula used in the game.
 *
 * Rules:
 * - ALL non-trivial math lives here as a static method. No formula is implemented inline elsewhere.
 * - Every method has a comment block that derives or explains the formula before the code.
 * - Coordinate system: (0, 0) = bottom-left corner of the world. Y increases upward.
 * - Angles are in RADIANS internally. Convert at call sites if a LibGDX API requires degrees.
 * - Methods are pure functions (no side effects, no LibGDX state touched).
 */
public final class GameMath {

    private GameMath() {}

    // =========================================================================
    // TEMPLATE — copy this block when adding a new formula
    // =========================================================================
    /*
     * Formula: <name>
     * Derivation / explanation:
     *   <step-by-step math or reference>
     * Edge cases:
     *   <division by zero, degenerate input, precision issues>
     */

    // =========================================================================
    // LINEAR INTERPOLATION
    // =========================================================================
    /*
     * Formula: Linear interpolation (lerp)
     * Derivation:
     *   lerp(start, end, t) = start + t * (end - start)
     *   At t=0 the result is start; at t=1 the result is end.
     *   t should be in [0, 1] for interpolation; values outside that range extrapolate.
     * Edge cases:
     *   interpolationFactor not clamped here — clamp at call site if overshoot is undesirable.
     */
    public static float lerp(float start, float end, float interpolationFactor) {
        return start + interpolationFactor * (end - start);
    }

    // =========================================================================
    // QUADRATIC BEZIER (single axis)
    // =========================================================================
    /*
     * Formula: Quadratic Bezier point on one axis
     * Derivation:
     *   B(t) = (1 − t)² · start + 2(1 − t)t · control + t² · end
     *   The classic three-point Bezier: at t=0 the curve sits on start, at t=1 on end, and
     *   the control point pulls the middle without ever being touched. Evaluated per axis
     *   (call twice — once with the X triple, once with the Y triple) to trace a smooth curved
     *   connector, e.g. the route-map hologram conduits (order-4).
     * Edge cases:
     *   interpolationFactor is expected in [0, 1]; outside that range the expression still
     *   evaluates but extrapolates off the curve. No division, so no singularity.
     */
    public static float quadraticBezier(float start, float control, float end, float interpolationFactor) {
        float inverse = 1f - interpolationFactor;
        return inverse * inverse * start
             + 2f * inverse * interpolationFactor * control
             + interpolationFactor * interpolationFactor * end;
    }

    // =========================================================================
    // SMOOTHSTEP EASING
    // =========================================================================
    /*
     * Formula: smoothstep01
     * Derivation:
     *   smoothstep(t) = t² × (3 − 2t)
     *   This is the Hermite cubic that passes through (0,0) and (1,1) with zero
     *   first-derivative at both endpoints, producing an ease-in/ease-out curve.
     *   Applied to the raw action timer (not the wall-clock duration) so the
     *   animation takes the same total time but accelerates from rest and decelerates
     *   into the final position — giving tile movement a sense of mass.
     * Edge cases:
     *   Input clamped to [0,1] — values outside that range would overshoot and are
     *   meaningless for animation interpolation. Caller should clamp before passing.
     *   At t=0: output 0. At t=0.5: output 0.5. At t=1: output 1.
     */
    public static float smoothstep01(float progress) {
        float clamped = Math.max(0f, Math.min(1f, progress));
        return clamped * clamped * (3f - 2f * clamped);
    }

    // =========================================================================
    // WEAPON PICKUP BOB OFFSET
    // =========================================================================
    /*
     * Formula: Pickup float bob screen offset
     * Derivation:
     *   offset = sin(timeSeconds * speed + phaseOffset) * amplitudeFraction * spriteHeight
     *   The sin wave oscillates between -1 and +1, producing a smooth hover.
     *   Multiplying by amplitudeFraction (0–1) and spriteHeight converts to screen pixels.
     *   The phaseOffset desyncs multiple pickups so they don't bob in lockstep.
     * Edge cases:
     *   amplitudeFraction must be < WEAPON_PICKUP_HEIGHT_FRACTION so the sprite never
     *   dips below floor level at the bottom of its bob cycle.
     *   Returns 0 when spriteHeight is non-positive.
     */
    public static float pickupBobOffset(float timeSeconds, float speed,
                                        float amplitudeFraction, float phaseOffset,
                                        float spriteHeight) {
        if (spriteHeight <= 0f) return 0f;
        return MathUtils.sin(timeSeconds * speed + phaseOffset) * amplitudeFraction * spriteHeight;
    }

    // =========================================================================
    // ANGLE BETWEEN TWO POINTS
    // =========================================================================
    /*
     * Formula: Angle from point A to point B in the world coordinate system
     * Derivation:
     *   differenceX = toX - fromX,  differenceY = toY - fromY
     *   angle = atan2(differenceY, differenceX)
     *   Result is in radians in (-π, π].
     *   atan2 handles the quadrant correctly and is safe when differenceX=0.
     * Coordinate system: (0,0) = bottom-left, Y-up, so positive angles go counter-clockwise.
     * Edge cases:
     *   If A == B the angle is 0 (atan2(0, 0) = 0 in Java).
     */
    public static float angleBetween(float fromX, float fromY, float toX, float toY) {
        float differenceX = toX - fromX;
        float differenceY = toY - fromY;
        return MathUtils.atan2(differenceY, differenceX);
    }

    // =========================================================================
    // DISTANCE BETWEEN TWO POINTS
    // =========================================================================
    /*
     * Formula: Euclidean distance
     * Derivation:
     *   d = sqrt((toX - fromX)^2 + (toY - fromY)^2)
     *   Derived from the Pythagorean theorem in 2D.
     * Edge cases:
     *   sqrt is expensive — use distanceSquared() when only relative comparison is needed.
     */
    public static float distance(float fromX, float fromY, float toX, float toY) {
        float differenceX = toX - fromX;
        float differenceY = toY - fromY;
        return (float) Math.sqrt(differenceX * differenceX + differenceY * differenceY);
    }

    /** Squared distance — cheaper than distance() when you only need to compare magnitudes. */
    public static float distanceSquared(float fromX, float fromY, float toX, float toY) {
        float differenceX = toX - fromX;
        float differenceY = toY - fromY;
        return differenceX * differenceX + differenceY * differenceY;
    }

    // =========================================================================
    // POINT ON CIRCLE EDGE
    // =========================================================================
    /*
     * Formula: World-space position of the point on a circle's perimeter in a given direction
     * Derivation:
     *   Given center (centerX, centerY), radius, and directionAngleRadians (Y-up, CCW positive):
     *     pointX = centerX + radius * cos(directionAngleRadians)
     *     pointY = centerY + radius * sin(directionAngleRadians)
     *   This is the standard parametric form of a circle.
     * Edge cases:
     *   radius = 0 returns the center point; directionAngleRadians has no effect.
     */
    public static float pointOnCircleX(float centerX, float directionAngleRadians, float radius) {
        return centerX + radius * MathUtils.cos(directionAngleRadians);
    }

    public static float pointOnCircleY(float centerY, float directionAngleRadians, float radius) {
        return centerY + radius * MathUtils.sin(directionAngleRadians);
    }

    // =========================================================================
    // 2D ROTATION MATRIX
    // =========================================================================
    /*
     * Formula: Rotate a 2D vector (vectorX, vectorY) by rotationAngleRadians around the origin
     * Derivation:
     *   [ cos r  -sin r ] [ vectorX ]   [ vectorX*cos(r) - vectorY*sin(r) ]
     *   [ sin r   cos r ] [ vectorY ] = [ vectorX*sin(r) + vectorY*cos(r) ]
     *   where r = rotationAngleRadians
     *   Positive r = counter-clockwise in Y-up world.
     *   The rotation matrix preserves vector length, so a unit vector stays unit.
     * Edge cases:
     *   rotationAngleRadians = 0 returns the original vector unchanged.
     */
    public static float rotateVectorX(float vectorX, float vectorY, float rotationAngleRadians) {
        return vectorX * MathUtils.cos(rotationAngleRadians) - vectorY * MathUtils.sin(rotationAngleRadians);
    }

    public static float rotateVectorY(float vectorX, float vectorY, float rotationAngleRadians) {
        return vectorX * MathUtils.sin(rotationAngleRadians) + vectorY * MathUtils.cos(rotationAngleRadians);
    }

    // =========================================================================
    // DDA — DELTA DISTANCES
    // =========================================================================
    /*
     * Formula: DDA delta distance X  (and its Y counterpart)
     * Derivation:
     *   The ray has unit direction (rayDirectionX, rayDirectionY).
     *   To travel exactly 1 tile in the X direction the ray covers a total length of:
     *     deltaDistX = sqrt(1 + (rayDirectionY / rayDirectionX)^2)
     *                = sqrt((rayDirectionX^2 + rayDirectionY^2) / rayDirectionX^2)
     *                = sqrt(rayDirectionX^2 + rayDirectionY^2) / |rayDirectionX|
     *                = 1 / |rayDirectionX|   (since the direction is a unit vector)
     * Edge cases:
     *   rayDirectionX = 0 → ray is perfectly vertical; it never crosses a vertical grid line.
     *   Return Float.MAX_VALUE so sideDistanceX is always larger than sideDistanceY in the DDA
     *   loop and X steps are never chosen.
     */
    public static float ddaDeltaDistanceX(float rayDirectionX) {
        return rayDirectionX == 0f ? Float.MAX_VALUE : Math.abs(1f / rayDirectionX);
    }

    public static float ddaDeltaDistanceY(float rayDirectionY) {
        return rayDirectionY == 0f ? Float.MAX_VALUE : Math.abs(1f / rayDirectionY);
    }

    // =========================================================================
    // DDA — INITIAL SIDE DISTANCES
    // =========================================================================
    /*
     * Formula: DDA initial side distance X  (and its Y counterpart)
     * Derivation:
     *   sideDistanceX is the ray length to the FIRST vertical grid line the ray will cross.
     *   The player sits at fractional tile position playerTileX inside tile playerMapX.
     *   Going left  (rayDirectionX < 0): the first vertical line is at x = playerMapX.
     *     Distance in tiles = playerTileX - playerMapX   (how far we are past the left edge)
     *     Ray length        = (playerTileX - playerMapX) * deltaDistX
     *   Going right (rayDirectionX > 0): the first vertical line is at x = playerMapX + 1.
     *     Distance in tiles = playerMapX + 1 - playerTileX
     *     Ray length        = (playerMapX + 1 - playerTileX) * deltaDistX
     * Edge cases:
     *   Player exactly on a grid line (fractional part = 0):
     *     going left  → sideDistX = 0 (already at a vertical line — step immediately)
     *     going right → sideDistX = 1 * deltaDistX (one full cell to the right)
     */
    public static float ddaInitialSideDistanceX(float playerTileX, int playerMapX,
                                                float rayDirectionX, float deltaDistX) {
        if (rayDirectionX < 0f) {
            return (playerTileX - playerMapX) * deltaDistX;
        }
        return (playerMapX + 1f - playerTileX) * deltaDistX;
    }

    public static float ddaInitialSideDistanceY(float playerTileY, int playerMapY,
                                                float rayDirectionY, float deltaDistY) {
        if (rayDirectionY < 0f) {
            return (playerTileY - playerMapY) * deltaDistY;
        }
        return (playerMapY + 1f - playerTileY) * deltaDistY;
    }

    // =========================================================================
    // CAMERA PLANE — Y-UP COORDINATE SYSTEM
    // =========================================================================
    /*
     * Formula: Camera plane vector for Lodev-style wall projection (Y-up world)
     *
     * Derivation:
     *   The camera plane is a segment centred on the player, perpendicular to the
     *   facing direction.  Its half-width in tile space equals:
     *     scale = tan(FOV / 2)
     *   For FOV = 90°: scale = tan(45°) = 1.0.
     *
     *   In a standard Y-up, CCW-positive coordinate system, rotating a vector
     *   +90° (CCW) is:
     *     rotate(+90°): (x, y) → (-y, +x)
     *
     *   So the camera plane vector, which points 90° CCW from the facing direction
     *   and has magnitude = scale, is:
     *     planeX = -directionY * scale
     *     planeY = +directionX * scale
     *
     *   Facing +X (east): direction = (1, 0)
     *     planeX = -0 * scale = 0
     *     planeY = +1 * scale = scale
     *   The plane points in the +Y direction (north).
     *
     *   Screen mapping — left screen edge = cameraX = -1, right = +1:
     *     leftRayDir  = direction + plane * (-1) = (1, 0) + (0, -scale) = (1, -scale)
     *     rightRayDir = direction + plane * (+1) = (1, 0) + (0, +scale) = (1, +scale)
     *
     *   In Y-up space when facing east (+X):
     *     Player's visual LEFT  = +Y direction (north)
     *     Player's visual RIGHT = -Y direction (south)
     *
     *   cameraX = -1 produces ray toward (1, -scale) → points toward -Y (south).
     *   This is the player's visual RIGHT, not left.
     *
     *   Conclusion: to make cameraX = -1 correspond to the LEFT screen column
     *   (visual left = +Y direction when facing +X), the mapping must be INVERTED:
     *     cameraX = 1 - 2 * screenColumn / screenWidth     (right-to-left as column increases)
     *
     *   This is the corrected formula for a Y-up world.  The plane vectors themselves
     *   (planeX, planeY) are correct as derived above.  Only the screen-column-to-
     *   cameraX mapping needs to flip sign relative to the Lodev (Y-down) convention.
     *
     *   Why the sign differs from Lodev (lodev.org/raycasting):
     *     Lodev uses Y-down screen space where left = smaller X and the screen column
     *     index increases left-to-right in world-space-left.  In Y-up space the world
     *     X axis is unchanged but the camera plane's Y component points the opposite
     *     way relative to screen columns, so the cameraX sign must flip.
     *
     * Edge cases:
     *   scale = 0 collapses the plane to a point; all rays become parallel (infinite
     *   zoom / zero FOV).  Never pass scale = 0.
     *   If the direction vector is not a unit vector the plane magnitude scales with
     *   it; callers must normalise the direction before calling.
     */
    public static float cameraPlaneX(float directionY, float scale) {
        return -directionY * scale;
    }

    public static float cameraPlaneY(float directionX, float scale) {
        return directionX * scale;
    }

    // =========================================================================
    // SCREEN COLUMN → CAMERA-PLANE PARAMETER (Y-UP CORRECTED)
    // =========================================================================
    /*
     * Formula: cameraX — normalised camera-plane coordinate for a screen column
     *
     * Derivation:
     *   In Y-down Lodev convention:
     *     cameraX = 2 * column / screenWidth - 1
     *     column = 0 → cameraX = -1  (left screen edge)
     *     column = screenWidth-1 → cameraX ≈ +1  (right screen edge)
     *
     *   In Y-up convention the camera plane's +Y side is the player's visual left,
     *   but column = 0 is still the LEFT screen column.  Adding the plane vector
     *   scaled by cameraX to the direction vector must therefore give:
     *     column = 0          → cameraX = +1  (add full +plane → leftward in world)
     *     column = screenWidth-1 → cameraX = -1  (subtract full plane → rightward)
     *
     *   The corrected mapping:
     *     cameraX = 1 - 2 * column / screenWidth
     *
     *   Verification (facing +X, dir=(1,0), plane=(0,scale)):
     *     column = 0             → cameraX = +1 → ray = (1, +scale) → toward +Y = visual LEFT  ✓
     *     column = screenWidth-1 → cameraX ≈ -1 → ray = (1, -scale) → toward -Y = visual RIGHT ✓
     *
     * Edge cases:
     *   screenColumn must be in [0, screenWidth-1].  At screenColumn = screenWidth
     *   cameraX = -1 (same as the last valid column), which is an off-by-one; guard
     *   at the call site.
     */
    public static float cameraPlaneParameter(int screenColumn, int screenWidth) {
        return 1f - 2f * screenColumn / (float) screenWidth;
    }

    // =========================================================================
    // RAY DIRECTION FOR ONE SCREEN COLUMN (CAMERA-PLANE METHOD)
    // =========================================================================
    /*
     * Formula: Ray direction vector for screen column (camera-plane method)
     *
     * Derivation:
     *   Each ray is the sum of the facing direction plus a fraction of the camera
     *   plane vector:
     *     rayDirectionX = directionX + planeX * cameraX
     *     rayDirectionY = directionY + planeY * cameraX
     *
     *   where cameraX = cameraPlaneParameter(column, screenWidth) ∈ [-1, +1].
     *
     *   The resulting ray is NOT a unit vector.  Its magnitude is:
     *     |ray| = sqrt((dirX + planeX*t)^2 + (dirY + planeY*t)^2)
     *   which varies across columns.  This is intentional — the DDA deltaDist
     *   formula (1 / |component|) absorbs the non-unit length correctly.
     *
     *   Importantly, the perpendicular wall distance produced by DDA with these
     *   rays is already the true perpendicular distance (see perpWallDist
     *   derivation below), so no fish-eye correction is needed.
     *
     * Edge cases:
     *   None beyond those of the DDA itself (handled in ddaDeltaDistanceX/Y).
     */
    public static float cameraPlaneRayDirectionX(float directionX, float planeX, float cameraPlaneX) {
        return directionX + planeX * cameraPlaneX;
    }

    public static float cameraPlaneRayDirectionY(float directionY, float planeY, float cameraPlaneY) {
        return directionY + planeY * cameraPlaneY;
    }

    // =========================================================================
    // PERPENDICULAR WALL DISTANCE (WHY DDA GIVES PERP, NOT EUCLIDEAN)
    // =========================================================================
    /*
     * Formula: Perpendicular wall distance from the camera-plane DDA result
     *
     * Derivation:
     *   Setup: player at tile position P = (px, py).
     *   Ray direction R = (rx, ry)  (NOT necessarily unit length).
     *   The ray hits a vertical grid line (X-face) after parameter t steps:
     *     hit point H = P + t * R
     *     t = (wallTileX - px) / rx       [from the x-component equation]
     *
     *   The DDA computes:
     *     perpWallDist = sideDistX - deltaDistX
     *   where sideDistX was advanced until we crossed the wall's grid line.
     *   That quantity equals exactly t above (in tile-length units of R).
     *
     *   EUCLIDEAN distance to H:
     *     euclidean = t * |R| = t * sqrt(rx^2 + ry^2)
     *
     *   PERPENDICULAR distance to the camera plane:
     *     The camera plane has normal direction = the facing direction D = (dx, dy).
     *     perpDist = (H - P) · D_hat
     *              = t * R · D_hat
     *              = t * (rx*dx + ry*dy)
     *
     *   Now observe how R was constructed:
     *     R = D + plane * cameraX
     *   where plane ⊥ D and |D| = 1.  Therefore:
     *     R · D = (D + plane*cameraX) · D
     *           = D·D + (plane·D)*cameraX
     *           = 1   + 0              (plane ⊥ D, |D|=1)
     *           = 1
     *
     *   Therefore:
     *     perpDist = t * 1 = t = perpWallDist  ✓
     *
     *   The DDA value is the PERPENDICULAR distance in tile units, regardless of
     *   ray length.  No fish-eye correction is needed because the camera-plane
     *   construction enforces R·D = 1 for every column.
     *
     *   For a Y-FACE hit the same argument holds with the Y component:
     *     t = (wallTileY - py) / ry = sideDistY - deltaDistY
     *
     *   Units: perpWallDist is in TILE units (1 unit = CELL_SIZE world units).
     *   The wall projection formula uses tile units directly; CELL_SIZE cancels.
     *
     * Edge cases:
     *   perpWallDist = 0 (player on a wall face): produces infinite lineHeight.
     *   Guard with a minimum distance (e.g. 0.01 tiles) at the call site.
     */
    public static float perpWallDistance(float sideDistance, float deltaDist) {
        return sideDistance - deltaDist;
    }

    // =========================================================================
    // WALL STRIPE HEIGHT AND VERTICAL DRAW RANGE (Y-UP SCREEN SPACE)
    // =========================================================================
    /*
     * Formula: Wall stripe screen height and draw bounds
     *
     * Derivation:
     *   The projection plane sits 1 unit in front of the player (by construction:
     *   the facing direction is a unit vector and the camera plane has R·D = 1).
     *
     *   A wall of height H_world at perpendicular distance d occupies a vertical
     *   angle of:
     *     verticalAngle = 2 * atan(H_world / (2 * d))
     *
     *   On a projection plane at distance 1 the projected height is:
     *     projectedHeight = H_world / d
     *
     *   Mapping projected height to pixels: define screenHeight pixels to cover
     *   the "standard" wall height at distance 1.  Then:
     *     lineHeight = screenHeight * projectedHeight / 1.0
     *               = screenHeight / d          (for a full-height unit wall)
     *
     *   This is the inverse-perspective formula.  Units: d is in TILE units;
     *   screenHeight is in pixels.  The ratio screenHeight/d gives pixels.
     *
     *   Vertical centering in Y-UP screen space:
     *     In a Y-up pixel buffer the horizon sits at screenHeight/2.
     *     The stripe is centred on the horizon:
     *       drawBottom = screenHeight/2 - lineHeight/2    (lower pixel, Y-up)
     *       drawTop    = screenHeight/2 + lineHeight/2    (upper pixel, Y-up)
     *
     *   Clamp to [0, screenHeight-1] before rasterising.
     *
     * Edge cases:
     *   perpWallDist ≤ 0: guard externally; passing 0 produces +Infinity.
     *   Very small perpWallDist produces lineHeight > screenHeight, which is
     *   correct — the stripe overflows the screen and gets clamped.
     */
    public static float wallStripeHeight(float screenHeight, float perpWallDistTiles) {
        return screenHeight / perpWallDistTiles;
    }

    public static float wallStripeDrawBottom(float screenHeight, float lineHeight) {
        return screenHeight / 2f - lineHeight / 2f;
    }

    public static float wallStripeDrawTop(float screenHeight, float lineHeight) {
        return screenHeight / 2f + lineHeight / 2f;
    }

    /*
     * Formula: Pickup sprite centered draw-bottom
     * Derivation:
     *   Center a sprite of height spriteHeight at the vertical midpoint of the screen:
     *     drawBottom = screenHeight / 2 - spriteHeight / 2
     *   The screen midpoint corresponds to eye level, which maps to the exact halfway point
     *   between floor and ceiling in the perspective projection — independent of depth.
     *   Applying a bob offset on top of this result makes the sprite hover at mid-air height.
     * Edge cases:
     *   If spriteHeight >= screenHeight the result is negative (clamp at draw time).
     */
    public static float spriteDrawBottomCentered(float screenHeight, float spriteHeight) {
        return screenHeight / 2f - spriteHeight / 2f;
    }

    // =========================================================================
    // WALL HIT POSITION (texture U coordinate, 0..1 along the wall face)
    // =========================================================================
    /*
     * Formula: wallHitPosition — fractional hit position along a wall face
     *
     * Derivation:
     *   After the DDA loop we know:
     *     t = perpWallDistance                (the DDA parameter, in tile-length units of the ray)
     *     Ray direction R = (rayDirectionX, rayDirectionY)   (camera-plane construction, not unit)
     *     Player tile position P = (playerTileX, playerTileY)
     *
     *   The ray equation gives the exact hit point in tile space:
     *     hitTileX = playerTileX + rayDirectionX * perpWallDistance
     *     hitTileY = playerTileY + rayDirectionY * perpWallDistance
     *
     *   A vertical wall face (crossedVerticalLine = true) is a grid line at integer X,
     *   running parallel to the Y axis.  The hit travels along that face in the Y
     *   direction, so the position along the face is the fractional Y coordinate:
     *     wallHitPosition = frac(hitTileY) = hitTileY - floor(hitTileY)
     *
     *   A horizontal wall face (crossedVerticalLine = false) is a grid line at
     *   integer Y, running parallel to the X axis.  The position along the face is
     *   the fractional X coordinate:
     *     wallHitPosition = frac(hitTileX) = hitTileX - floor(hitTileX)
     *
     *   Result is in [0, 1).  This is the raw texture U coordinate before mirroring.
     *
     * Y-up vs Y-down:
     *   The hit-point reconstruction formula is identical in both conventions because
     *   it operates purely in tile space.  The Y-up sign flip only affects the
     *   cameraPlaneParameter mapping (already corrected in that method); by the time
     *   we reach this method, rayDirectionX/Y already encode the correct Y-up ray.
     *   No additional sign change is required here.
     *
     * Edge cases:
     *   Floating-point jitter can put hitTileY fractionally outside [0, 1) — clamp
     *   after this call (done in wallHitPositionClamped).
     *   perpWallDistance = 0: hit point equals the player position; result is
     *   floor(player)'s fractional part — not physically meaningful, but safe.
     */
    public static float wallHitPosition(float playerTileX, float playerTileY,
                                        float rayDirectionX, float rayDirectionY,
                                        float perpWallDistance,
                                        boolean crossedVerticalLine) {
        if (crossedVerticalLine) {
            // Vertical face: hit travels in the Y direction along the wall.
            float hitTileY = playerTileY + rayDirectionY * perpWallDistance;
            return hitTileY - (float) Math.floor(hitTileY);
        } else {
            // Horizontal face: hit travels in the X direction along the wall.
            float hitTileX = playerTileX + rayDirectionX * perpWallDistance;
            return hitTileX - (float) Math.floor(hitTileX);
        }
    }

    // =========================================================================
    // WALL HIT POSITION — MIRROR CORRECTION
    // =========================================================================
    /*
     * Formula: wallHitPositionMirrored — flip wallHitPosition when the texture
     *          would appear mirrored on the far side of a wall
     *
     * Derivation:
     *   Consider a vertical wall face (crossedVerticalLine = true).
     *   The DDA stepped in the +X direction (stepColumn = +1) when it entered the
     *   wall tile from the LEFT — the player is west of the wall, looking east.
     *   The DDA stepped in the -X direction (stepColumn = -1) when it entered from
     *   the RIGHT — the player is east of the wall, looking west.
     *
     *   Both cases produce a hitTileY that increases as Y increases in world space.
     *   When the player is west (looking east), Y increases left-to-right as seen
     *   from OUTSIDE the wall face (because +Y is to the player's left when facing +X
     *   in a Y-up CCW system).  That means wallHitPosition = 0 is on the left of the
     *   texture as seen from outside — correct, no flip.
     *
     *   When the player is east (looking west, stepColumn = -1), Y still increases
     *   the same direction in world space, but from the OUTSIDE of this face the
     *   "left" of the wall is now the -Y direction.  So wallHitPosition = 0 (low Y)
     *   would appear on the RIGHT side of the texture — mirrored.  To fix it:
     *     wallHitPosition = 1 - wallHitPosition
     *
     *   Rule for vertical face: flip when stepColumn > 0 (ray entered from the west,
     *   i.e. facing east).  Wait — let's verify with the Y-up convention carefully:
     *
     *     Facing +X (east), standing west of wall:
     *       stepColumn = +1 (ray moves right = toward the wall).
     *       The camera plane's left edge corresponds to high Y (north).
     *       hitTileY increases northward.  As the ray sweeps from left screen edge
     *       (north) to right screen edge (south), hitTileY decreases.
     *       For a single column, the camera hits a specific Y on the wall face.
     *       The natural direction along the face seen from the west: Y increases
     *       going north = going to the screen's LEFT.  So position 0 is at the
     *       south end (screen right), position 1 at the north end (screen left).
     *       Whether this matches your texture atlas is a convention choice.
     *
     *   The standard Lodev convention (also used here) is:
     *     Vertical face   (X-face):  flip when rayDirectionX > 0
     *                                i.e. when the ray points in the +X direction
     *                                (player is on the west side of the wall, face is its east side).
     *     Horizontal face (Y-face):  flip when rayDirectionY < 0
     *                                i.e. when the ray points in the -Y direction
     *                                (player is on the north side in Y-up = looking south).
     *
     *   Physical reasoning (Y-up, CCW positive):
     *     For a vertical face seen from the east (rayDirectionX < 0):
     *       The "left" of the face as seen from the east is the +Y direction (north).
     *       hitTileY at fractional 0 is the south corner — rightmost when viewed from east.
     *       We want tex=0 at the left (north) corner, so FLIP.
     *       But rayDirectionX < 0 → do NOT flip by the rule above.  Contradiction?
     *
     *   Resolution: the mirroring rule is purely a convention that must be consistent
     *   with the texture authoring convention.  The standard Lodev rule is:
     *     Vertical face:   flip when rayDirectionX > 0
     *     Horizontal face: flip when rayDirectionY < 0
     *   This produces the same result as Lodev because the Y-up / Y-down difference
     *   only changed the cameraPlaneParameter sign, not the ray direction assignment
     *   to walls.  The same walls are hit by the same rays; only the screen column
     *   ordering reversed, which is already handled by cameraPlaneParameter.
     *
     *   Callers: pass the SAME rayDirectionX/Y that drove the DDA, and the stepColumn
     *   / stepRow as computed in the DDA loop (sign of the ray direction component).
     *
     * Edge cases:
     *   wallHitRaw must already be in [0, 1]; clamp before calling if precision
     *   drift is possible (see wallHitPositionClamped).
     *   At exactly 0.0 or 1.0 the flip maps one boundary to the other — acceptable
     *   since those are the same visual edge on a tiling texture.
     */
    public static float wallHitPositionMirrored(float wallHitRaw,
                                                float rayDirectionX, float rayDirectionY,
                                                boolean crossedVerticalLine) {
        if (crossedVerticalLine) {
            // Vertical (X) face: flip when ray points in +X direction.
            if (rayDirectionX > 0f) {
                return 1f - wallHitRaw;
            }
        } else {
            // Horizontal (Y) face: flip when ray points in -Y direction.
            if (rayDirectionY < 0f) {
                return 1f - wallHitRaw;
            }
        }
        return wallHitRaw;
    }

    // =========================================================================
    // TEXTURE COLUMN INDEX FROM WALL HIT POSITION
    // =========================================================================
    /*
     * Formula: textureColumn — integer texel column for a wall stripe
     *
     * Derivation:
     *   wallHitPosition is in [0, 1).  A texture has textureWidth columns indexed
     *   0 .. textureWidth-1.
     *
     *     textureColumn = (int)(wallHitPosition * textureWidth)
     *
     *   This maps:
     *     wallHitPosition = 0.0            → textureColumn = 0      (left texel)
     *     wallHitPosition = 0.999...       → textureColumn = textureWidth - 1  (right texel)
     *     wallHitPosition = 1.0 (boundary) → textureColumn = textureWidth  → OUT OF RANGE
     *
     *   Floating-point jitter can push wallHitPosition to exactly 1.0 or fractionally
     *   above it.  Guard with a clamp:
     *     textureColumn = clamp((int)(wallHitPosition * textureWidth), 0, textureWidth - 1)
     *
     *   Why (int) and not Math.floor()?
     *     For positive inputs (wallHitPosition ≥ 0) truncation and floor are
     *     equivalent.  wallHitPosition is always non-negative (frac of a position),
     *     so (int) is safe and avoids the overhead of a method call.
     *
     * Y-up note:
     *   The Y-up system has no bearing on horizontal texture coordinates.
     *   The U axis of the texture is horizontal — it maps along the wall face, which
     *   lies in the XY plane.  Vertical texture coordinates (V axis) depend on the
     *   screen Y range and are computed separately during per-pixel rendering.
     *
     * Edge cases:
     *   wallHitPosition < 0 due to float error: clamp catches it (result = 0).
     *   textureWidth = 0: would produce divide-by-zero; guard at call site.
     */
    public static int textureColumn(float wallHitPosition, int textureWidth) {
        int column = (int)(wallHitPosition * textureWidth);
        // Clamp in case floating-point drift pushed wallHitPosition to exactly 1.0
        // or infinitesimally beyond it.
        if (column < 0)            column = 0;
        if (column >= textureWidth) column = textureWidth - 1;
        return column;
    }

    // =========================================================================
    // WALL SHADING BY PERPENDICULAR DISTANCE
    // =========================================================================
    /*
     * Formula: Brightness multiplier based on perpendicular distance
     *
     * Derivation:
     *   A simple inverse-square soft-falloff that stays in [0, 1]:
     *     shade = 1 / (1 + d^2 * falloff)
     *
     *   At d = 0  tiles:  shade = 1.0  (fully lit — wall is touching the camera)
     *   At d = 10 tiles:  shade = 1 / (1 + 100 * 0.05) = 1/6 ≈ 0.167
     *   At d = 20 tiles:  shade = 1 / (1 + 400 * 0.05) = 1/21 ≈ 0.048
     *
     *   The falloff constant (RenderConstants.WALL_SHADING_FALLOFF = 0.05) is tuned
     *   for a maximum visible range of ~10 tiles (RAY_MAX_LENGTH_CELLS).  At the
     *   cut-off the shade is ≈0.167, giving a gradual fade rather than a hard
     *   clip.  Multiply this value by the wall's base colour components to obtain
     *   the final shaded colour.
     *
     *   Why inverse-square and not linear?
     *     Physical light falloff is inverse-square.  A linear ramp would make
     *     close walls look unnaturally dim and distant walls too bright relative
     *     to each other.
     *
     * Edge cases:
     *   perpWallDistTiles < 0: not physically meaningful; shade would be > 1.
     *   Guard with Math.max(0, dist) at the call site if needed.
     *   falloff = 0: shade is always 1.0 (no distance darkening).
     */
    public static float wallShade(float perpWallDistTiles, float falloff) {
        return 1f / (1f + perpWallDistTiles * perpWallDistTiles * falloff);
    }

    // =========================================================================
    // WALL TEXTURE CLIPPING — texel row range for a screen-clipped wall stripe
    // =========================================================================
    /*
     * Formula: wallTextureClipSrcY / wallTextureClipSrcHeight
     *
     * Derivation:
     *   The full (unclamped) wall stripe spans world-Y range:
     *     unclampedBottom = screenHeight/2 - lineHeight/2
     *     unclampedTop    = screenHeight/2 + lineHeight/2
     *
     *   When lineHeight > screenHeight the stripe overflows the screen and is clamped:
     *     drawBottom = max(0,            unclampedBottom)
     *     drawTop    = min(screenHeight, unclampedTop)
     *
     *   LibGDX SpriteBatch.draw() convention (confirmed by CLAUDE.md and source):
     *     srcY = 0 is the TOP row of the image in pixel space and maps to the
     *     VISUAL TOP of the sprite (the high-Y edge in Y-up world space = drawTop).
     *     srcY increases downward through the image, toward lower world Y (drawBottom).
     *
     *   The number of screen pixels hidden ABOVE the top of the screen:
     *     topClipPixels = max(0, unclampedTop - screenHeight)
     *
     *   Each screen pixel corresponds to (textureHeight / lineHeight) texture rows.
     *   Therefore the number of texture rows belonging to the hidden top clip is:
     *     texSrcY = topClipPixels * textureHeight / lineHeight
     *
     *   The visible stripe height in screen pixels is (drawTop - drawBottom).
     *   The corresponding number of texture rows is:
     *     texSrcHeight = (drawTop - drawBottom) * textureHeight / lineHeight
     *
     *   Mathematical consistency check — no overflow beyond textureHeight:
     *     texSrcY + texSrcHeight
     *       = (topClipPixels + drawTop - drawBottom) / lineHeight * textureHeight
     *     When the bottom is also clipped:
     *       topClipPixels  = unclampedTop - screenHeight
     *       drawTop        = screenHeight
     *       drawBottom     = 0
     *       bottomClipPixels = -unclampedBottom
     *       topClipPixels + drawTop - drawBottom
     *         = (unclampedTop - screenHeight) + screenHeight - 0
     *         = unclampedTop
     *         = unclampedBottom + lineHeight
     *         = -bottomClipPixels + lineHeight
     *     Symmetrically this equals lineHeight when no bottom clip occurs, so:
     *       texSrcY + texSrcHeight ≤ textureHeight (exact at the float limit).
     *     Float rounding may push the sum one texel over; the call site must clamp:
     *       texSrcHeight = min(texSrcHeight, textureHeight - texSrcY)
     *
     *   When lineHeight ≤ screenHeight (no clip):
     *     topClipPixels = 0  →  texSrcY      = 0
     *     drawTop - drawBottom = lineHeight   →  texSrcHeight = textureHeight
     *   Both degenerate correctly to the unclipped case.
     *
     *   Y-up note:
     *     This derivation relies on LibGDX SpriteBatch mapping srcY=0 to the visual
     *     top of the sprite in Y-up world space, which is confirmed by the renderer
     *     in WallRenderer.  The Y-up coordinate system does not require a sign change
     *     here — only the unclampedBottom/Top values already encode Y-up via the
     *     wallStripeDrawBottom/Top formulas.
     *
     * Edge cases:
     *   lineHeight = 0: caller must guard (perpWallDistance = infinity — never happens
     *     in practice because RAY_MAX_LENGTH_CELLS provides an upper bound on distance).
     *   lineHeight very large (player nearly touching a wall): texSrcY and texSrcHeight
     *     remain finite floats because lineHeight is a float and textureHeight fits in
     *     a float without overflow.
     *   texSrcHeight < 1 after cast: guarded by max(1, texSrcHeight) at the call site
     *     (a stripe narrower than one texel row should still sample one row).
     *   Integer overflow in intermediate product: all arithmetic is in float; safe.
     */

    /**
     * Returns the texel row at which to start sampling the wall texture column when
     * the rendered stripe is clipped at the top of the screen.
     *
     * @param unclampedTop  World-Y of the stripe top BEFORE clamping  (= screenHeight/2 + lineHeight/2)
     * @param screenHeight  Height of the render target in pixels
     * @param lineHeight    Full unclamped stripe height in screen pixels
     * @param textureHeight Height of the wall texture in texels
     * @return              First texel row to pass as srcY to SpriteBatch.draw()
     */
    public static int wallTextureClipSrcY(float unclampedTop,
                                          float screenHeight,
                                          float lineHeight,
                                          int   textureHeight) {
        float topClipPixels = Math.max(0f, unclampedTop - screenHeight);
        return (int)(topClipPixels * textureHeight / lineHeight);
    }

    /**
     * Returns the number of texel rows to sample from the wall texture column for
     * the portion of the stripe that is actually visible on screen.
     *
     * <p>The result must be clamped at the call site:
     * {@code texSrcHeight = Math.min(texSrcHeight, textureHeight - texSrcY)}
     * to guard against one-texel float rounding overshoot.</p>
     *
     * @param drawTop       World-Y of the stripe top AFTER clamping    (= min(screenHeight, unclampedTop))
     * @param drawBottom    World-Y of the stripe bottom AFTER clamping  (= max(0, unclampedBottom))
     * @param lineHeight    Full unclamped stripe height in screen pixels
     * @param textureHeight Height of the wall texture in texels
     * @return              Number of texel rows to pass as srcHeight to SpriteBatch.draw();
     *                      always at least 1
     */
    public static int wallTextureClipSrcHeight(float drawTop,
                                               float drawBottom,
                                               float lineHeight,
                                               int   textureHeight) {
        float visiblePixels = drawTop - drawBottom;
        return Math.max(1, (int)(visiblePixels * textureHeight / lineHeight));
    }

    // =========================================================================
    // FLOOR / CEILING CASTING — Lodev floor-casting adapted for Y-up
    // =========================================================================

    /*
     * Formula: floorPixelOffsetBelowHorizon
     * Derivation:
     *   In Y-up coordinates, the horizon sits at screen row screenHeight/2.
     *   Floor rows have drawY < screenHeight/2, so offset below horizon is:
     *       pixelOffset = screenHeight/2 − drawY
     *   This is the OPPOSITE of Lodev (Y-down): Lodev uses (drawY − screenHeight/2).
     *   A drawY of 0 (bottom of screen) gives the largest offset → greatest
     *   distance from horizon → closest floor strip to the player.
     *   Wait — actually drawY=0 means bottommost row, farthest from horizon in
     *   Y-up, which is the NEAREST floor strip visually. offset = H/2 - 0 = H/2 (large).
     *   Larger offset → smaller rowDistance → closer floor. Correct.
     * Edge cases:
     *   drawY = screenHeight/2 → offset = 0 → division by zero in rowDistance.
     *   Caller must iterate only over drawY < screenHeight/2 − 1.
     */
    public static int floorPixelOffsetBelowHorizon(int drawY, int screenHeight) {
        return screenHeight / 2 - drawY;
    }

    /*
     * Formula: floorRowDistance
     * Derivation:
     *   Virtual camera sits at z = FLOOR_CAMERA_Z (= 0.5 screen-heights above floor).
     *   The floor plane at z = 0 is at world distance d below the camera.
     *   By similar triangles:
     *       d / FLOOR_CAMERA_Z = screenHeight / (2 × pixelOffset)
     *       d = screenHeight × FLOOR_CAMERA_Z / pixelOffset
     *         = screenHeight × 0.5 / pixelOffset
     *   The result is in tile units (same unit as DDA perpWallDistance).
     * Edge cases:
     *   pixelOffset = 0 → returns +Infinity (guarded by caller; don't pass 0).
     */
    public static float floorRowDistance(int pixelOffset, int screenHeight) {
        return screenHeight * RenderConstants.FLOOR_CAMERA_Z / pixelOffset;
    }

    /*
     * Formula: floorStepTileComponent
     * Derivation:
     *   The leftmost ray at column 0 has direction rayDirLeft and the rightmost
     *   ray at column screenWidth-1 has direction rayDirRight.  In this Y-up
     *   project (cameraParameter = 1 − 2×col/W), column 0 → param = +1 →
     *   rayDirLeft  = direction + plane  (opposite to Lodev Y-down).
     *   column W-1  → param ≈ −1 →
     *   rayDirRight = direction − plane.
     *   The tile-space span of the floor row is:
     *       span = rowDistance × (rayDirRight − rayDirLeft)
     *   Divided by screenWidth gives the per-pixel step:
     *       step = rowDistance × (rayDirRight − rayDirLeft) / screenWidth
     * Edge cases:
     *   screenWidth = 0 → division by zero. Caller guarantees screenWidth > 0.
     */
    public static float floorStepTileComponent(float rowDistance,
                                                float rayDirRightComponent,
                                                float rayDirLeftComponent,
                                                int screenWidth) {
        return rowDistance * (rayDirRightComponent - rayDirLeftComponent) / screenWidth;
    }

    /*
     * Formula: floorOriginTileComponent
     * Derivation:
     *   The world tile coordinate of the leftmost pixel in the floor row is:
     *       origin = playerTile + rowDistance × rayDirLeft
     *   where rayDirLeft is the ray direction at column 0 (cameraParameter = +1
     *   in Y-up, so rayDirLeft = direction + plane).
     * Edge cases:
     *   No degenerate cases; floating-point precision degrades at very large
     *   playerTile values (> ~10,000 tiles) but this game operates on small levels.
     */
    public static float floorOriginTileComponent(float playerTile,
                                                   float rowDistance,
                                                   float rayDirLeftComponent) {
        return playerTile + rowDistance * rayDirLeftComponent;
    }

    /*
     * Formula: floorTexelIndex
     * Derivation:
     *   Take the fractional part of the tile coordinate to get UV in [0, 1):
     *       frac = tileFraction − floor(tileFraction)
     *   Scale to texture pixel space and clamp:
     *       texel = clamp((int)(frac × textureSize), 0, textureSize − 1)
     *   The clamp guards against floating-point giving exactly 1.0 at tile edges.
     * Edge cases:
     *   Negative tileFraction: Java's (int) cast truncates toward zero, not floor,
     *   so we use Math.floor() explicitly to get the correct fractional part.
     *   textureSize = 0 → returns 0 (division by zero avoided; caller guarantees > 0).
     */
    public static int floorTexelIndex(float tileFraction, int textureSize) {
        float frac = tileFraction - (float) Math.floor(tileFraction);
        return MathUtils.clamp((int) (frac * textureSize), 0, textureSize - 1);
    }

    /*
     * Formula: floorShade
     * Derivation:
     *   Reuses the same distance-shading curve as walls:
     *       shade = 1 / (1 + rowDistance² × falloff)
     *   Using the same falloff constant ensures floor and walls share the same
     *   visual darkening rate at equal distances.
     * Edge cases:
     *   Same as wallShade — see that method's comment block.
     */
    public static float floorShade(float rowDistance, float falloff) {
        return wallShade(rowDistance, falloff);
    }

    /*
     * Formula: applyShadeToPackedRGBA
     * Derivation:
     *   Unpack RGBA8888 int into four byte channels.  Apply shade + alert tint:
     *       outR = min(1, shade × (1 + alertPulse × alertRedBoost)) × inR
     *       outG = shade × (1 − alertPulse × alertGbDampen) × inG
     *       outB = shade × (1 − alertPulse × alertGbDampen) × inB
     *   These mirror the per-column wall-tinting formulas in WallRenderer so that
     *   the floor/ceiling alert effect is visually consistent with the walls.
     *   Repack as RGBA8888 with alpha = 0xFF.
     * Edge cases:
     *   shade = 0 → all channels clamp to 0 (black).
     *   alertPulse outside [0,1] is not guarded — caller must pass a valid pulse.
     *   Integer overflow: channels are ANDed with 0xFF before shifting so they
     *   always stay in [0, 255].
     */
    public static int applyShadeToPackedRGBA(int packedRGBA, float shade,
                                              float alertRedBoost, float alertGbDampen,
                                              float alertPulse) {
        int inR = (packedRGBA >>> 24) & 0xFF;
        int inG = (packedRGBA >>> 16) & 0xFF;
        int inB = (packedRGBA >>>  8) & 0xFF;

        float redFactor   = Math.min(1f, shade * (1f + alertPulse * alertRedBoost));
        float greenFactor = shade * (1f - alertPulse * alertGbDampen);
        float blueFactor  = shade * (1f - alertPulse * alertGbDampen);

        int outR = MathUtils.clamp((int) (redFactor   * inR), 0, 255);
        int outG = MathUtils.clamp((int) (greenFactor * inG), 0, 255);
        int outB = MathUtils.clamp((int) (blueFactor  * inB), 0, 255);

        return (outR << 24) | (outG << 16) | (outB << 8) | 0xFF;
    }

    // =========================================================================
    // ALERT PULSE — emergency facility lighting
    // =========================================================================
    /*
     * Formula: alertPulse
     * Derivation:
     *   Offset sine wave mapped to [0, 1]:
     *       pulse = 0.5 + 0.5 × sin(timeSeconds × pulseSpeedRadiansPerSecond)
     *   At sin = −1 (trough): pulse = 0   (normal, un-tinted lighting).
     *   At sin = +1 (peak):   pulse = 1   (full red-alert tint applied).
     *   Period = 2π / pulseSpeedRadiansPerSecond.
     *   With pulseSpeedRadiansPerSecond ≈ 1.57 (π/2): period ≈ 4 s.
     * Edge cases:
     *   timeSeconds may grow unbounded across a session — MathUtils.sin() handles
     *   large float arguments without meaningful precision loss for this use case.
     *   pulseSpeedRadiansPerSecond = 0 → sin(0) = 0 → constant 0.5 (stuck mid-pulse).
     */
    public static float alertPulse(float timeSeconds, float pulseSpeedRadiansPerSecond) {
        return 0.5f + 0.5f * MathUtils.sin(timeSeconds * pulseSpeedRadiansPerSecond);
    }

    // =========================================================================
    // DOOR PANEL — rising bottom edge as the door slides upward
    // =========================================================================
    /*
     * Formula: doorPanelBottom
     * Derivation:
     *   The door panel slides upward as it opens — like a garage door retracting
     *   into the ceiling.  The visible panel is always anchored at the TOP of the
     *   doorway stripe (drawTop); its BOTTOM edge rises from drawBottom toward
     *   drawTop as openFraction increases.
     *
     *   doorPanelBottom = lerp(drawBottom, drawTop, openFraction)
     *                   = drawBottom + (drawTop - drawBottom) × openFraction
     *
     *   Verification:
     *     openFraction = 0 (closed): doorPanelBottom = drawBottom → full panel visible ✓
     *     openFraction = 1 (open):   doorPanelBottom = drawTop   → panel fully retracted ✓
     *
     *   The rendered stripe spans [doorPanelBottom, drawTop].
     *   The texture samples from srcY = 0 (image top = visual top = drawTop) downward,
     *   so the TOP portion of the texture remains visible as the door opens — consistent
     *   with the panel hanging from the ceiling and the bottom rising away.
     *
     * Edge cases:
     *   openFraction = 1 returns drawTop; caller should skip drawing since
     *     the visible height is zero.
     *   drawTop <= drawBottom (degenerate stripe): the expression evaluates to
     *     drawBottom regardless of openFraction, which is safe.
     *   openFraction is NOT clamped here — caller must pass a value in [0, 1].
     */
    public static float doorPanelBottom(float drawBottom, float drawTop, float openFraction) {
        return drawBottom + (drawTop - drawBottom) * openFraction;
    }

    // =========================================================================
    // DOOR PANEL HEIGHT — unclamped screen height of the visible panel
    // =========================================================================
    /*
     * Formula: doorPanelHeight
     * Derivation:
     *   The visible panel occupies (1 - openFraction) of the full stripe:
     *     panelHeight = lineHeight × (1 - openFraction)
     *
     *   This is used as the denominator when mapping panel screen pixels to
     *   texture rows, analogous to how lineHeight is used for walls.
     *
     * Edge cases:
     *   openFraction = 1: panelHeight = 0; caller must guard against divide-by-zero.
     *   openFraction is NOT clamped here — caller must pass a value in [0, 1].
     */
    public static float doorPanelHeight(float lineHeight, float openFraction) {
        return lineHeight * (1f - openFraction);
    }

    // =========================================================================
    // SPRITE DEPTH — perpendicular camera-plane distance to a billboard sprite
    // =========================================================================
    /*
     * Formula: spriteDepth
     * Derivation:
     *   The perpendicular distance from the player's camera plane to a world point
     *   is the dot product of the player-to-point vector with the player's unit
     *   facing direction:
     *     depth = differenceX × directionX + differenceY × directionY
     *   Both inputs must be in the same units.  Passing tile-space coordinates
     *   (world divided by CELL_SIZE) gives a result in tile units, matching the
     *   perpWallDistance values stored in the z-buffer.
     *   A depth ≤ 0 means the point is at or behind the camera plane; callers
     *   must skip those cases.
     * Edge cases:
     *   depth ≤ PROP_BEHIND_PLAYER_EPSILON_TILES: skip — behind or on camera plane.
     *   direction must be a unit vector; non-unit vectors scale the result.
     */
    public static float spriteDepth(float differenceX, float differenceY,
                                    float directionX, float directionY) {
        return differenceX * directionX + differenceY * directionY;
    }

    // =========================================================================
    // SPRITE SCREEN COLUMN — horizontal screen position of a billboard sprite
    // =========================================================================
    /*
     * Formula: spriteScreenColumnCenter
     * Derivation:
     *   Map the sprite's tile-space offset from the player into camera space using
     *   the inverse of the camera matrix used by WallRenderer:
     *
     *   Camera matrix:  M = [ planeX  directionX ]
     *                       [ planeY  directionY ]
     *
     *   Inverse:  M^-1 = (1/det) × [  directionY  −directionX ]
     *                               [ −planeY       planeX     ]
     *   where det = planeX × directionY − directionX × planeY
     *
     *   Camera-space coordinates (inputs are tile-space offsets):
     *     cameraSpaceX     = (1/det) × (directionY × differenceX − directionX × differenceY)
     *     cameraSpaceDepth = (1/det) × (−planeY × differenceX + planeX × differenceY)
     *
     *   Proof that cameraSpaceDepth == spriteDepth():
     *     With planeX = −dirY×scale, planeY = dirX×scale, det = −scale:
     *       cameraSpaceDepth = (−1/scale) × (−dirX×scale×diffX + (−dirY×scale)×diffY)
     *                        = dirX×diffX + dirY×diffY = spriteDepth ✓
     *
     *   Screen column mapping (Y-up, matching cameraPlaneParameter sign convention):
     *     screenColumn = (screenWidth / 2) × (1 − cameraSpaceX / cameraSpaceDepth)
     *   This is the Y-up mirror of Lodev's Y-down formula (1 + transformX/transformY).
     *
     * Edge cases:
     *   cameraSpaceDepth near zero: caller must guard with PROP_BEHIND_PLAYER_EPSILON_TILES
     *   before calling; dividing by ~0 produces extreme off-screen column values.
     *   det near zero: impossible with a valid unit direction and perpendicular plane.
     */
    public static float spriteScreenColumnCenter(float differenceX, float differenceY,
                                                  float directionX, float directionY,
                                                  float planeX, float planeY,
                                                  int screenWidth) {
        float inverseDeterminant = 1f / (planeX * directionY - directionX * planeY);
        float cameraSpaceX     = inverseDeterminant * (directionY * differenceX - directionX * differenceY);
        float cameraSpaceDepth = inverseDeterminant * (-planeY * differenceX + planeX * differenceY);
        return (screenWidth / 2f) * (1f - cameraSpaceX / cameraSpaceDepth);
    }

    // =========================================================================
    // SPRITE SCREEN HEIGHT — projected screen height of a billboard sprite
    // =========================================================================
    /*
     * Formula: spriteScreenHeight
     * Derivation:
     *   Identical to wallStripeHeight: the projection plane is 1 unit in front of
     *   the player, so a unit-height sprite at perpendicular distance d projects to:
     *     screenHeight / d   pixels tall
     *   A per-sprite height multiplier is applied by the caller after this call
     *   to scale each prop type to its natural size.
     * Edge cases:
     *   spriteDepthTiles must be > 0; guard with PROP_BEHIND_PLAYER_EPSILON_TILES.
     *   Very small depths yield heights larger than screenHeight — clamp draw bounds
     *   to [0, screenHeight] at the call site.
     */
    public static float spriteScreenHeight(float screenHeight, float spriteDepthTiles) {
        return screenHeight / spriteDepthTiles;
    }

    // =========================================================================
    // RAY-CIRCLE INTERSECTION — front-face hit distance for a sub-cell column
    // =========================================================================
    /*
     * Formula: rayCircleIntersection
     * Derivation:
     *   Ray:    P(t) = (originX + t*dirX,  originY + t*dirY)
     *   Circle: |P(t) - center|² = radius²
     *   Let d = origin - center = (dx, dy).  Expand:
     *     t²*(dirX²+dirY²) + t*2*(dx*dirX+dy*dirY) + (dx²+dy²-r²) = 0
     *   Quadratic coefficients:
     *     a = dirX²+dirY²   (not necessarily 1 — camera-plane rays are not unit vectors)
     *     b = 2*(d · dir)
     *     c = |d|² - r²
     *   discriminant = b²-4ac
     *     < 0  → ray misses circle, return -1
     *    >= 0  → tNear = (-b - sqrt(discriminant)) / (2a)  [front-face intersection]
     *   Why tNear equals perpWallDistance:
     *     For camera-plane rays R = direction + plane*cameraParam,  R·direction = 1.
     *     perpDist = (P(t) - origin)·direction = t*(R·direction) = t.
     *     So the returned t IS the perpendicular distance, directly comparable to the
     *     z-buffer values stored by WallRenderer.
     * Edge cases:
     *   discriminant < 0: no intersection → return -1.
     *   tNear <= 0: circle is entirely behind the ray origin → return -1.
     *   The player cannot enter a column cell (isBlockedAt returns true), so the
     *   origin is always outside the circle; tNear is always the relevant root.
     */
    public static float rayCircleIntersection(float originX, float originY,
                                              float rayDirectionX, float rayDirectionY,
                                              float centerX, float centerY,
                                              float radiusTiles) {
        float differenceX   = originX - centerX;
        float differenceY   = originY - centerY;
        float quadraticA    = rayDirectionX * rayDirectionX + rayDirectionY * rayDirectionY;
        float quadraticB    = 2f * (differenceX * rayDirectionX + differenceY * rayDirectionY);
        float quadraticC    = differenceX * differenceX + differenceY * differenceY
                              - radiusTiles * radiusTiles;
        float discriminant  = quadraticB * quadraticB - 4f * quadraticA * quadraticC;
        if (discriminant < 0f) return -1f;
        float tNear = (-quadraticB - (float) Math.sqrt(discriminant)) / (2f * quadraticA);
        return tNear > 0f ? tNear : -1f;
    }

    // =========================================================================
    // COLUMN TEXTURE U — cylindrical texture coordinate at a circle hit point
    // =========================================================================
    /*
     * Formula: columnTextureU
     * Derivation:
     *   The outward surface normal at the hit point is:
     *     normalX = (hitTileX - centerX) / radius
     *     normalY = (hitTileY - centerY) / radius
     *   The angle of this normal in the tile plane:
     *     angle = atan2(normalY, normalX)  ∈ [-PI, PI]
     *   Map linearly to [0, 1]:
     *     u = (angle + PI) / (2*PI)
     *   This wraps the texture once around the full 360° circumference so every
     *   view angle shows a different texture column, matching the cylindrical geometry.
     * Edge cases:
     *   atan2 has a discontinuity at ±PI (the texture seam is at the back of the
     *   column, angle = ±PI → u = 0 or u = 1).  The seam is only visible when the
     *   player looks directly at the back face — acceptable for a round column.
     *   The division by radius is implicit; only the direction matters, not the
     *   magnitude, so we skip normalising by radius here.
     */
    public static float columnTextureU(float hitTileX, float hitTileY,
                                       float columnCenterTileX, float columnCenterTileY) {
        float normalX = hitTileX - columnCenterTileX;
        float normalY = hitTileY - columnCenterTileY;
        float angle   = MathUtils.atan2(normalY, normalX);
        return (angle + MathUtils.PI) / (2f * MathUtils.PI);
    }

    // =========================================================================
    // WEAPON DAMAGE DROP — linear multiplier with floor
    // =========================================================================
    /*
     * Formula: damageDropMultiplier (linear with floor)
     * Derivation:
     *   Each tile of distance removes a fixed fraction `coefficient` of base damage:
     *     multiplier = 1 - coefficient × distanceTiles
     *   Clamped to [minimumMultiplier, 1.0] so point-blank never exceeds base damage
     *   and far shots always deal at least a scratch — no dead zone at maximum range.
     *   With coefficient = 0.18 and range = 5:
     *     distance 1 → 0.82, distance 3 → 0.46, distance 5 → 0.10 → clamped to 0.15.
     *   The linear form is intentional: the player perceives "each tile shaves a chunk",
     *   which is more readable than an exponential curve.
     * Edge cases:
     *   distanceTiles = 0 → 1.0 (full damage at muzzle).
     *   Large distanceTiles → clamps at minimumMultiplier, never reaches 0.
     *   Negative distanceTiles: not expected (shot marches forward only); returns > 1.0.
     *   coefficient > 1: multiplier would go negative — caller must constrain to [0, 1].
     */
    public static float damageDropMultiplier(float coefficient, int distanceTiles,
                                              float minimumMultiplier) {
        float multiplier = 1f - coefficient * distanceTiles;
        return Math.max(minimumMultiplier, Math.min(1f, multiplier));
    }

    // =========================================================================
    // RAILGUN DAMAGE FALLOFF — near-flat linear falloff with a high floor
    // =========================================================================
    /*
     * Formula: railgunFalloff
     * Derivation:
     *   The railgun fires a coherent slug; damage barely drops over 16 tiles.
     *   multiplier = 1 - coefficient × (distanceTiles - 1)
     *   At distanceTiles = 1: multiplier = 1.0 (no drop at point-blank).
     *   With coefficient = 0.02 and distanceTiles = 16:
     *     multiplier = 1 - 0.02 × 15 = 0.70 (hits the floor exactly at max range).
     *   Result is clamped to [minMultiplier, 1.0].
     *   The (distanceTiles - 1) form ensures full damage at tile 1 (adjacent tile),
     *   distinguishing railgun feel from the standard damageDropMultiplier which begins
     *   dropping at tile 1.
     * Edge cases:
     *   distanceTiles < 1: formula gives > 1.0, clamped to 1.0 by the min().
     *   coefficient = 0: constant 1.0 (no falloff — infinite precision slug).
     *   Large coefficient: floor dominates; railgun uses tiny coefficient (0.02).
     *   minMultiplier > 1.0: degenerate; caller must pass a valid value in [0, 1].
     */
    public static float railgunFalloff(int distanceTiles, float coefficient, float minMultiplier) {
        float multiplier = 1f - coefficient * (distanceTiles - 1);
        return Math.max(minMultiplier, Math.min(1f, multiplier));
    }

    /*
     * Formula: damageDropMultiplierExponential (geometric decay — tuning alternative)
     * Derivation:
     *   multiplier = (1 - coefficient) ^ distanceTiles
     *   Provides a gentler drop near the muzzle and sharper falloff at range vs the
     *   linear variant. Not floored — natural asymptote toward 0. Ship the linear form
     *   first; swap here in one line if playtesting favours exponential feel.
     * Edge cases:
     *   distanceTiles = 0 → 1.0.
     *   coefficient = 0 → no falloff (multiplier stays 1.0 at every distance).
     *   coefficient = 1 → multiplier = 0 for all distances > 0 (instant zero range).
     *   coefficient > 1 → degenerate; caller must constrain coefficient to [0, 1).
     */
    public static float damageDropMultiplierExponential(float coefficient, int distanceTiles) {
        return (float) Math.pow(1f - coefficient, distanceTiles);
    }

    // =========================================================================
    // TILE POSITION HASH — deterministic per-tile pseudo-random seed
    // =========================================================================
    /*
     * Formula: combineHash
     * Derivation:
     *   Mixes two integers into a single long seed using XOR and multiply
     *   bit operations (Knuth multiplicative hash 2654435761 for 32-bit input,
     *   combined with finalisation steps from xxHash / MurmurHash3) so that
     *   nearby tile positions produce uncorrelated seeds.
     * Edge cases:
     *   Negative tile coordinates are handled correctly — Java integer overflow
     *   wraps arithmetically, which is the desired behaviour for hash functions.
     */
    public static long combineHash(int tileColumn, int tileRow) {
        long hashValue = (long) tileColumn * 2654435761L ^ (long) tileRow * 2246822519L;
        hashValue ^= hashValue >>> 33;
        hashValue *= 0xff51afd7ed558ccdL;
        hashValue ^= hashValue >>> 33;
        return hashValue;
    }

    // =========================================================================
    // ROUTE NODE SEED — deterministic per-node seed for the branching route map
    // =========================================================================
    /*
     * Formula: routeNodeSeed
     * Derivation / explanation:
     *   Every route-map node needs its own reproducible seed so that all of its
     *   rolls (which standard generator to use, an elite affix, a mystery outcome)
     *   depend ONLY on the run's master seed and the node's fixed layout position
     *   (depth, laneIndex) — never on the ORDER the player visits nodes. That makes
     *   the branch you could have taken identical to the branch you did take, and a
     *   given run seed always yields an identical map.
     *
     *   Uses the same splitmix-style mixer as World.floorSeed (multiply by the
     *   64-bit golden-ratio constant 0x9E3779B97F4A7C15, then add the next input):
     *
     *     mixed = (runSeed ^ ROUTE_NODE_SEED_SALT)
     *     mixed = mixed * 0x9E3779B97F4A7C15 + depth
     *     mixed = mixed * 0x9E3779B97F4A7C15 + laneIndex
     *
     *   The salt (RouteMapConstants.ROUTE_NODE_SEED_SALT) XORed in up front makes this
     *   stream independent of World.floorSeed(runSeed, depth), so a node's rolls never
     *   collide with that floor's LevelGenerator seed even at the same depth.
     * Edge cases:
     *   long overflow wraps arithmetically (desired for a hash); depth=0 and
     *   laneIndex=0 still produce a well-mixed seed; negative depth/lane wrap
     *   correctly like any other integer input.
     */
    public static long routeNodeSeed(long runSeed, int depth, int laneIndex) {
        long mixed = runSeed ^ RouteMapConstants.ROUTE_NODE_SEED_SALT;
        mixed = mixed * 0x9E3779B97F4A7C15L + depth;
        mixed = mixed * 0x9E3779B97F4A7C15L + laneIndex;
        return mixed;
    }

    // =========================================================================
    // FLICKER MULTIPLIER — deterministic per-tile brightness oscillator
    // =========================================================================
    /*
     * Formula: flickerMultiplier
     * Derivation:
     *   Two sine oscillators at different frequencies are combined to produce
     *   irregular flicker that reads as a failing fluorescent lamp:
     *
     *     seed              = combineHash(tileColumn, tileRow)
     *     phaseOffset       = |seed % 1000| × 0.006283185   (maps to [0, 2π))
     *     baseSin           = sin(time × FLICKER_NOISE_FREQUENCY × 2π + phaseOffset)
     *     smoothLevel       = (baseSin + 1) × 0.5            (maps [-1, 1] to [0, 1])
     *     jitterPhase       = |(seed >> 8) % 1000| × 0.006283185
     *     jitter            = sin(time × FLICKER_NOISE_FREQUENCY × 5.7 + jitterPhase)
     *     combined          = smoothLevel × 0.7 + (jitter + 1) × 0.15  (in [0, 1])
     *
     *   When combined < FLICKER_FAILURE_THRESHOLD the lamp is "off":
     *     return FLICKER_MIN_BRIGHTNESS
     *   Otherwise lerp from min to max across the live range:
     *     live = (combined - threshold) / (1 - threshold)
     *     return lerp(FLICKER_MIN, FLICKER_MAX, live)
     *
     *   Transition behaviour: the off→on and on→off transitions are INSTANTANEOUS.
     *   When combined crosses FLICKER_FAILURE_THRESHOLD the brightness snaps directly
     *   between FLICKER_MIN and FLICKER_MAX with no interpolation, so the lamp cuts
     *   rather than fades.  The timing of each transition is still governed by the
     *   same oscillator formula — only the output shape changes from smooth to binary.
     *
     *   The 5.7× jitter multiplier is incommensurate with the base frequency so
     *   the two oscillators never synchronise, producing aperiodic flicker.
     *   The tile-position hash gives each 'f' tile a unique phase so adjacent
     *   flickering tiles do not pulse in unison.
     *
     * Edge cases:
     *   timeSeconds grows unbounded — MathUtils.sin handles large float args.
     *   Negative tile coordinates are handled by combineHash and Math.abs.
     */
    public static float flickerMultiplier(int tileColumn, int tileRow, float timeSeconds) {
        long seed                      = combineHash(tileColumn, tileRow);
        float phaseOffsetRadians       = Math.abs(seed % 1000) * 0.006283185f;
        float baseSin                  = MathUtils.sin(timeSeconds * RenderConstants.FLICKER_NOISE_FREQUENCY
                                                        * MathUtils.PI2 + phaseOffsetRadians);
        float smoothLevel              = (baseSin + 1f) * 0.5f;
        float jitterPhaseOffsetRadians = Math.abs((seed >> 8) % 1000) * 0.006283185f;
        float jitter                   = MathUtils.sin(timeSeconds * RenderConstants.FLICKER_NOISE_FREQUENCY
                                                        * 5.7f + jitterPhaseOffsetRadians);
        float combined                 = smoothLevel * 0.7f + (jitter + 1f) * 0.15f;
        // Instantaneous snap: no lerp — the lamp is either fully on or fully off.
        return combined < RenderConstants.FLICKER_FAILURE_THRESHOLD
                ? RenderConstants.FLICKER_MIN_BRIGHTNESS
                : RenderConstants.FLICKER_MAX_BRIGHTNESS;
    }

    // =========================================================================
    // TILE DISTANCE METRICS
    // =========================================================================
    /*
     * Formula: chebyshevDistanceTiles
     * Derivation:
     *   Chebyshev (chessboard) distance = max(|dc|, |dr|).
     *   A king on a chessboard can reach (toColumn, toRow) from (fromColumn, fromRow)
     *   in exactly chebyshev steps, moving diagonally when both axes need closing.
     *   Used as a fast pre-check before running full LOS: if chebyshev > LOS_MAX_RANGE
     *   the LOS walk is skipped entirely.
     * Edge cases:
     *   Returns 0 when both coordinates are equal.
     */
    public static int chebyshevDistanceTiles(int fromColumn, int fromRow, int toColumn, int toRow) {
        return Math.max(Math.abs(toColumn - fromColumn), Math.abs(toRow - fromRow));
    }

    /*
     * Formula: manhattanDistanceTiles
     * Derivation:
     *   Manhattan (taxicab) distance = |dc| + |dr|.
     *   Used by the enemy movement AI to measure how far a candidate step is from
     *   the player: the greedy stepper picks the legal step with the smallest
     *   Manhattan distance to the target tile.
     * Edge cases:
     *   Returns 0 when both coordinates are equal.
     */
    public static int manhattanDistanceTiles(int fromColumn, int fromRow, int toColumn, int toRow) {
        return Math.abs(toColumn - fromColumn) + Math.abs(toRow - fromRow);
    }

    /*
     * Formula: Tile-space Manhattan combat distance
     * Derivation: |fromColumn - toColumn| + |fromRow - toRow|
     *             For cardinal-line combat (same row or column), this equals the
     *             number of tiles between player and target.
     *             Delegates to manhattanDistanceTiles() — named separately for clarity
     *             at call sites that compute shot distances in the ability pipeline.
     * Edge cases: distance=0 means same tile (should not happen in normal combat).
     */
    public static int tileDistance(int fromColumn, int fromRow, int toColumn, int toRow) {
        return manhattanDistanceTiles(fromColumn, fromRow, toColumn, toRow);
    }

    // =========================================================================
    // TILE LINE-OF-SIGHT — supercover walk in tile space
    // =========================================================================
    /*
     * Formula: tileLineOfSightClear
     * Derivation:
     *   Walk from (fromColumn, fromRow) to (toColumn, toRow) through the integer
     *   tile grid, testing each INTERMEDIATE tile (endpoints excluded) for solidity.
     *   Uses a parametric sampling approach: advance step/maxSteps along both axes
     *   simultaneously, where maxSteps = max(|diffColumn|, |diffRow|) (Chebyshev
     *   distance).  This visits exactly one tile per step — the one closest to the
     *   ideal line at that point — and matches the diagonal/cardinal stepping that
     *   enemies use for movement, keeping LOS and movement consistent.
     *
     *   Number of intermediate tiles sampled: maxSteps − 1.
     *   Time complexity: O(chebyshev(from, to)).
     *
     * TileSolidTest: caller-supplied lambda that returns true if (column, row) blocks
     *   sight.  Keeping it out of GameMath avoids Level/DoorManager imports.
     *
     * Edge cases:
     *   from == to: returns true (trivially clear — same tile).
     *   chebyshev > LOS_MAX_RANGE_TILES: returns false without walking (pre-check
     *   is the caller's responsibility; this method does not re-check range).
     *   Floating-point: uses double arithmetic for the division to avoid rounding
     *   pushing the intermediate tile one column/row off in long diagonal lines.
     */

    @FunctionalInterface
    public interface TileSolidTest {
        boolean isSolid(int column, int row);
    }

    public static boolean tileLineOfSightClear(int fromColumn, int fromRow,
                                                int toColumn, int toRow,
                                                TileSolidTest solidTest) {
        int diffColumn = toColumn - fromColumn;
        int diffRow    = toRow    - fromRow;
        int maxSteps   = Math.max(Math.abs(diffColumn), Math.abs(diffRow));
        if (maxSteps == 0) return true;
        for (int step = 1; step < maxSteps; step++) {
            int column = fromColumn + (int) Math.round((double) diffColumn * step / maxSteps);
            int row    = fromRow    + (int) Math.round((double) diffRow    * step / maxSteps);
            if (solidTest.isSolid(column, row)) return false;
        }
        return true;
    }

    // =========================================================================
    // HUD — SEGMENT FILL COUNT
    // =========================================================================
    /*
     * Formula: segmentFillCount
     * Derivation:
     *   fillCount = round(fraction × segmentCount), clamped to [0, segmentCount].
     *   round() rather than floor() so the bar visually tracks the real value
     *   without always lagging by half a segment.
     * Edge cases:
     *   fraction outside [0,1] is clamped before rounding so the result never
     *   underflows or overflows segmentCount.
     */
    public static int segmentFillCount(float fraction, int segmentCount) {
        float clamped = Math.max(0f, Math.min(1f, fraction));
        return Math.round(clamped * segmentCount);
    }

    // =========================================================================
    // HUD — PULSE MULTIPLIER
    // =========================================================================
    /*
     * Formula: pulseMultiplier
     * Derivation:
     *   result = minMul + (maxMul - minMul) × 0.5 × (1 + sin(2π × hertz × clockSeconds))
     *   At sin = -1 (trough): result = minMul.
     *   At sin = +1 (peak):   result = maxMul.
     *   Oscillates between minMul and maxMul at frequency hertz (cycles per second).
     * Edge cases:
     *   clockSeconds may grow unbounded — MathUtils.sin handles large floats.
     *   hertz = 0 → sin(0) = 0 → constant (minMul + maxMul) / 2 (mid-point, not a hazard).
     *   minMul > maxMul: returns values in [maxMul, minMul] — not guarded; caller must pass correct order.
     */
    public static float pulseMultiplier(float clockSeconds, float hertz, float minMul, float maxMul) {
        float sineValue = MathUtils.sin(MathUtils.PI2 * hertz * clockSeconds);
        return minMul + (maxMul - minMul) * 0.5f * (1f + sineValue);
    }

    // =========================================================================
    // HUD — ZERO-PADDED INTEGER APPEND (no boxing, no allocation)
    // =========================================================================
    /*
     * Formula: appendIntPadded
     * Derivation:
     *   Appends `value` to `builder` as a decimal string with at least `minDigits`
     *   digits, left-padded with '0' characters.  Uses repeated division by 10 to
     *   extract digits, then reverses them — standard LSD-first integer-to-chars
     *   algorithm without any Integer.toString() allocation.
     *   Negative values are clamped to 0 for HUD display (no negative readouts).
     * Edge cases:
     *   value = 0 with minDigits = 4: appends "0000".
     *   value larger than minDigits can represent: all digits are included (no truncation).
     *   minDigits <= 0: treated as 1.
     */
    public static void appendIntPadded(StringBuilder builder, int value, int minDigits) {
        if (value < 0) value = 0;
        int safeMinDigits = Math.max(1, minDigits);
        // Extract digits LSD-first into a small buffer.
        char[] digitBuffer = new char[10];
        int digitCount = 0;
        int remaining = value;
        do {
            digitBuffer[digitCount++] = (char) ('0' + remaining % 10);
            remaining /= 10;
        } while (remaining > 0);
        // Pad with leading zeros up to minDigits.
        for (int padIndex = digitCount; padIndex < safeMinDigits; padIndex++) {
            builder.append('0');
        }
        // Append digits in reverse (MSD-first).
        for (int digitIndex = digitCount - 1; digitIndex >= 0; digitIndex--) {
            builder.append(digitBuffer[digitIndex]);
        }
    }

    // =========================================================================
    // KEYCARD DOOR GLOW FALLOFF
    // =========================================================================
    /*
     * Formula: Linear falloff from keycard door proximity
     * Derivation:
     *   falloff(d) = 1 - d / glowRadius   clamped to [0, 1]
     *   At distance 0 (the door tile itself) → 1.0 (full glow).
     *   At distance == glowRadius           → 0.0 (no glow).
     *   Beyond glowRadius                   → 0.0.
     * Edge cases:
     *   glowRadius <= 0 returns 0 to avoid division by zero.
     *   distanceTiles < 0 clamped to 0 via max.
     */
    public static float keycardGlowFalloff(int distanceTiles, int glowRadius) {
        if (glowRadius <= 0) return 0f;
        return Math.max(0f, 1f - (float) distanceTiles / glowRadius);
    }

    // =========================================================================
    // ENEMY HIT FLASH — lerp a colour channel toward white
    // =========================================================================
    /*
     * Formula: lerpTowardWhite
     * Derivation:
     *   result = lerp(channel, 1, flashStrength)
     *          = channel + (1 - channel) × clamp(flashStrength, 0, 1)
     *   At flashStrength = 0: returns channel unchanged (no flash).
     *   At flashStrength = 1: returns 1.0 (full-white for this channel).
     *   Applying this to all three RGB channels of a sprite tint makes the
     *   sprite gradually blanch to white as the flash strength rises.
     * Edge cases:
     *   flashStrength < 0: clamped to 0, channel returned unchanged.
     *   flashStrength > 1: clamped to 1, returns 1.0.
     *   channel outside [0, 1]: not clamped here; caller supplies valid shade values.
     */
    public static float lerpTowardWhite(float channel, float flashStrength) {
        float clampedStrength = Math.max(0f, Math.min(1f, flashStrength));
        return channel + (1f - channel) * clampedStrength;
    }

    // =========================================================================
    // ENEMY HEALTH BAR — two-stop green → yellow → red gradient
    // =========================================================================
    /*
     * Formula: healthBarColor
     * Derivation:
     *   Two-stop piecewise linear interpolation through three palette anchors
     *   (from Constants): FULL (green), HALF (yellow), EMPTY (red).
     *
     *   For fillFraction in [0.5, 1.0] — blending green toward yellow:
     *     localT = (1 - fillFraction) / 0.5   maps [1.0 .. 0.5] → [0 .. 1]
     *     rgb = lerp(FULL, HALF, localT)
     *
     *   For fillFraction in [0.0, 0.5) — blending yellow toward red:
     *     localT = (0.5 - fillFraction) / 0.5  maps [0.5 .. 0.0] → [0 .. 1]
     *     rgb = lerp(HALF, EMPTY, localT)
     *
     *   At fillFraction = 0.5 both branches converge to HALF (continuous seam).
     *   The gradient is purely cosmetic and does not affect game logic.
     *
     * Edge cases:
     *   fillFraction clamped to [0, 1] before computation.
     *   outRgb must be a non-null float[3]; caller provides a reused instance
     *   (no allocation per call).
     */
    /*
     * Formula: Armour Damage Absorption
     * Derivation:
     *   A fraction of each incoming hit is absorbed by armour instead of reaching HP.
     *       idealAbsorb = floor(incomingDamage × absorptionFraction)
     *   Clamped by currentArmor so we can't absorb more than we hold:
     *       armorAbsorbed = min(currentArmor, idealAbsorb)
     *   The remaining (incomingDamage − armorAbsorbed) is passed to HP by the caller.
     * Edge cases:
     *   currentArmor = 0 → returns 0 (no shield, full damage to HP).
     *   incomingDamage = 0 → returns 0.
     *   absorptionFraction ≥ 1.0 → all damage goes to armour (never penetrates); caller
     *   should choose a fraction < 1 so some chip damage always gets through.
     */
    public static int armorAbsorb(int incomingDamage, int currentArmor, float absorptionFraction) {
        return Math.min(currentArmor, (int)(incomingDamage * absorptionFraction));
    }

    /*
     * Formula: radialFalloff
     * Derivation:
     *   Linear falloff from 1 at the centre of a blob to 0 at its edge.
     *   falloff = max(0, 1 − distance / radius)
     *   Used by procedural wall texture generators (rust blotches, gore flesh mass)
     *   to accumulate blob weights per pixel. Pure function, called at load time only.
     * Edge cases:
     *   radius <= 0 would divide by zero; returns 0 (degenerate zero-radius blob contributes nothing).
     */
    public static float radialFalloff(float distance, float radius) {
        if (radius <= 0f) return 0f;
        return Math.max(0f, 1f - distance / radius);
    }

    public static void healthBarColor(float fillFraction, float[] outRgb) {
        float clampedFraction = Math.max(0f, Math.min(1f, fillFraction));
        if (clampedFraction >= 0.5f) {
            float localInterpolationFactor = (1f - clampedFraction) / 0.5f;
            outRgb[0] = lerp(EnemyConstants.ENEMY_HEALTH_FULL_RED,   EnemyConstants.ENEMY_HEALTH_HALF_RED,   localInterpolationFactor);
            outRgb[1] = lerp(EnemyConstants.ENEMY_HEALTH_FULL_GREEN, EnemyConstants.ENEMY_HEALTH_HALF_GREEN, localInterpolationFactor);
            outRgb[2] = lerp(EnemyConstants.ENEMY_HEALTH_FULL_BLUE,  EnemyConstants.ENEMY_HEALTH_HALF_BLUE,  localInterpolationFactor);
        } else {
            float localInterpolationFactor = (0.5f - clampedFraction) / 0.5f;
            outRgb[0] = lerp(EnemyConstants.ENEMY_HEALTH_HALF_RED,   EnemyConstants.ENEMY_HEALTH_EMPTY_RED,   localInterpolationFactor);
            outRgb[1] = lerp(EnemyConstants.ENEMY_HEALTH_HALF_GREEN, EnemyConstants.ENEMY_HEALTH_EMPTY_GREEN, localInterpolationFactor);
            outRgb[2] = lerp(EnemyConstants.ENEMY_HEALTH_HALF_BLUE,  EnemyConstants.ENEMY_HEALTH_EMPTY_BLUE,  localInterpolationFactor);
        }
    }

    // =========================================================================
    // XP PROGRESSION CURVE
    // =========================================================================
    /*
     * Formula: xpRequiredForLevel
     * Derivation:
     *   xpRequired = base * level ^ exponent
     *   Polynomial curve: faster growth than linear, slower than exponential.
     *   Example (base=100, exponent=1.5):
     *     level 1 → 2:  100 * 1^1.5 =  100 XP
     *     level 2 → 3:  100 * 2^1.5 =  283 XP
     *     level 5 → 6:  100 * 5^1.5 = 1118 XP
     * Edge cases:
     *   currentLevel < 1 is clamped to 1 (no negative or zero XP requirements).
     */
    public static int xpRequiredForLevel(int base, float exponent, int currentLevel) {
        int safeLevel = Math.max(1, currentLevel);
        return (int)(base * Math.pow(safeLevel, exponent));
    }

    // =========================================================================
    // COMPOUND DEPTH SCALE
    // =========================================================================
    /*
     * Formula: compoundScaleForDepth
     * Derivation:
     *   result = scaleFactor ^ max(0, depth - 1)
     *   Compound multiplier: depth=1 → 1.0 exactly (no scaling on first floor).
     *   depth=2 → scaleFactor^1, depth=3 → scaleFactor^2, etc.
     * Edge cases:
     *   depth < 1 returns 1.0 (no negative scaling).
     *   scaleFactor < 1 causes shrinkage per floor — only use values >= 1.0 for growth.
     */
    public static float compoundScaleForDepth(float scaleFactor, int depth) {
        return (float) Math.pow(scaleFactor, Math.max(0, depth - 1));
    }

    // =========================================================================
    // STAT SYSTEM — derived multiplier formulas (one per attribute effect)
    // All formulas use a linear per-point model anchored at STAT_REFERENCE.
    // STAT_REFERENCE = 0 means a fresh marine with STR 2 already gets +10% melee
    // (chosen to match the user spec "STR 5 = +25% melee").
    // =========================================================================

    /*
     * Formula: meleeDamageMultiplier
     * Derivation:
     *   multiplier = 1.0 + (strengthEffective - reference) × perPoint
     *   STR 0, reference 0, perPoint 0.05 → 1.0  (no bonus at zero)
     *   STR 5, reference 0, perPoint 0.05 → 1.0 + 5 × 0.05 = 1.25  (+25% melee, per spec)
     *   STR 12 (cap)                       → 1.0 + 12 × 0.05 = 1.60  (+60% melee ceiling)
     * Edge cases:
     *   strengthEffective = 0, reference = 0 → multiplier = 1.0 (identity, no bonus).
     *   strengthEffective < reference (debuff) → multiplier < 1.0 (penalty).
     *   perPoint = 0 → multiplier always 1.0 (disabled, safe).
     */
    public static float meleeDamageMultiplier(int strengthEffective, int reference, float perPoint) {
        return 1.0f + (strengthEffective - reference) * perPoint;
    }

    /*
     * Formula: rangedDamageMultiplier
     * Derivation:
     *   multiplier = 1.0 + (marksmanshipEffective - reference) × perPoint
     *   MRK 0, reference 0, perPoint 0.04 → 1.0  (no bonus at zero)
     *   MRK 5                              → 1.0 + 5 × 0.04 = 1.20  (+20% ranged)
     *   Scales slower than melee (0.04 vs 0.05) — ranged is the safe-distance option;
     *   melee carries higher risk so its reward is proportionally higher.
     * Edge cases:
     *   Same as meleeDamageMultiplier.
     */
    public static float rangedDamageMultiplier(int marksmanshipEffective, int reference, float perPoint) {
        return 1.0f + (marksmanshipEffective - reference) * perPoint;
    }

    /*
     * Formula: accuracyMultiplier
     * Derivation:
     *   multiplier = 1.0 + (marksmanshipEffective - reference) × perPoint
     *   MRK 0, reference 0, perPoint 0.03 → 1.0  (no bonus at zero)
     *   MRK 5                              → 1.0 + 5 × 0.03 = 1.15  (+15% accuracy)
     *   Used to tighten shotgun pellet spread or reduce future hit-chance rolls.
     *   Dormant for hitscan weapons that always hit; getter exists for future use.
     * Edge cases:
     *   Same as meleeDamageMultiplier.
     */
    public static float accuracyMultiplier(int marksmanshipEffective, int reference, float perPoint) {
        return 1.0f + (marksmanshipEffective - reference) * perPoint;
    }

    /*
     * Formula: actionDurationMultiplier
     * Derivation:
     *   raw = 1.0 - (agilityEffective - reference) × perPoint
     *   result = clamp(raw, minDurationMultiplier, 1.0)
     *   AGI 0, reference 0, perPoint 0.03 → 1.0  (normal duration)
     *   AGI 5                              → 1.0 - 5 × 0.03 = 0.85  (15% faster animation)
     *   AGI 10 (cap)                       → max(0.55, 1.0 - 0.30) = 0.70 → clamped at 0.55
     *   minDurationMultiplier = 0.55 prevents animation becoming unreadably fast.
     *   IMPORTANT: this changes ANIMATION speed only, not turn order — one player action
     *   is still exactly one world turn.
     * Edge cases:
     *   agilityEffective < reference → raw > 1.0 → clamped to 1.0 (never slower than base).
     *   perPoint = 0 → always 1.0 (disabled).
     *   minDurationMultiplier ≥ 1.0 → degenerates to constant 1.0 (safe no-op).
     */
    public static float actionDurationMultiplier(int agilityEffective, int reference,
                                                  float perPoint, float minDurationMultiplier) {
        float raw = 1.0f - (agilityEffective - reference) * perPoint;
        return Math.max(minDurationMultiplier, Math.min(1.0f, raw));
    }

    /*
     * Formula: dodgeChance
     * Derivation:
     *   raw = (agilityEffective - reference) × perPoint
     *   result = clamp(raw, 0.0, dodgeCap)
     *   AGI 0, reference 0, perPoint 0.02, cap 0.35 → 0.0  (no dodge at zero)
     *   AGI 10 (cap)                                → min(0.35, 10 × 0.02) = 0.20  (20%)
     *   AGI would need to be 17.5 to naturally hit the 35% cap — cap provides hard ceiling.
     *   Rolled in Player.applyDamage BEFORE armour; on success damage = 0.
     *   dodgeCap = 0.35 keeps high-AGI builds slippery but never immune.
     * Edge cases:
     *   agilityEffective = 0, reference = 0 → 0.0 (no dodge).
     *   agilityEffective < reference → raw < 0 → clamped to 0.
     *   perPoint = 0 → always 0.0 (disabled).
     */
    public static float dodgeChance(int agilityEffective, int reference,
                                     float perPoint, float dodgeCap) {
        float raw = (agilityEffective - reference) * perPoint;
        return Math.max(0.0f, Math.min(dodgeCap, raw));
    }

    /*
     * Formula: maxHealthBonus
     * Derivation:
     *   bonus = (toughnessEffective - reference) × hpPerPoint
     *   TGH 0, reference 0, hpPerPoint 5 → 0   (no bonus at zero)
     *   TGH 5                             → 5 × 5 = 25  (+25 max HP)
     *   TGH 12 (cap)                      → 12 × 5 = 60  (+60 max HP ceiling)
     *   Added to Player.maxHealth on TOUGHNESS change; does NOT auto-heal.
     * Edge cases:
     *   toughnessEffective = 0, reference = 0 → 0 (no bonus).
     *   toughnessEffective < reference → negative bonus (debuff); caller may clamp to 0.
     *   hpPerPoint = 0 → always 0 (disabled).
     */
    public static int maxHealthBonus(int toughnessEffective, int reference, int hpPerPoint) {
        return (toughnessEffective - reference) * hpPerPoint;
    }

    /*
     * Formula: flatDamageReduction
     * Derivation:
     *   reduction = (toughnessEffective - reference) × reductionPerPoint
     *   TGH 0, reference 0, reductionPerPoint 1 → 0   (no reduction at zero)
     *   TGH 5                                    → 5 × 1 = 5  (shaves 5 off every HP-bound hit)
     *   TGH 12 (cap)                             → 12   (shaves 12 per hit)
     *   Applied AFTER armour absorption. Caller floors the remaining HP damage at
     *   TGH_MIN_DAMAGE (1) so chip damage (poison ticks etc.) still threatens turtles.
     * Edge cases:
     *   toughnessEffective = 0, reference = 0 → 0 (no reduction).
     *   toughnessEffective < reference → negative reduction (increases damage); caller guards.
     *   reductionPerPoint = 0 → always 0 (disabled).
     */
    public static int flatDamageReduction(int toughnessEffective, int reference, int reductionPerPoint) {
        return (toughnessEffective - reference) * reductionPerPoint;
    }

    // =========================================================================
    // ENEMY ATTACK ANIMATIONS — lunge curve and recoil offset
    // =========================================================================
    /*
     * Formula: attackLungeCurve
     * Derivation:
     *   strength decays from 1.0 (instant of attack) to 0.0 (animation complete).
     *   We want full extension IMMEDIATELY and a smooth ease-back to zero:
     *     phase = clamp(strength, 0, 1)   (strength IS the linear decay)
     *   So when strength = 1 (just triggered): phase = 1.0 (full lunge).
     *   As strength decays toward 0: the lunge eases back out.
     *   A simple easeOut applied to the strength itself:
     *     phase = 1 - (1 - strength)^2   gives a fast initial extension and slow ease-back.
     *   At strength = 1: phase = 1 - 0 = 1.0  (full extension immediately).
     *   At strength = 0: phase = 1 - 1 = 0.0  (back to neutral).
     *   At strength = 0.5: phase = 1 - 0.25 = 0.75  (mostly extended mid-way).
     * Edge cases:
     *   strength clamped to [0, 1]; output is always in [0, 1].
     */
    public static float attackLungeCurve(float strength) {
        float clampedStrength = Math.max(0f, Math.min(1f, strength));
        float inverseFraction = 1f - clampedStrength;
        return 1f - inverseFraction * inverseFraction;
    }

    /*
     * Formula: attackRecoilOffset
     * Derivation:
     *   The sprite nudges away from the player on fire with a quadratic ease-out:
     *     offset = strength^2 × maxPixels
     *   At strength = 1 (just fired): offset = maxPixels.
     *   As strength decays toward 0: offset reduces quadratically.
     *   Quadratic gives a strong initial kick that settles back quickly.
     *   Caller multiplies by a sign to push the sprite in the correct direction.
     * Edge cases:
     *   strength clamped to [0, 1]; output is always in [0, maxPixels].
     *   maxPixels should be positive; negative value reverses direction.
     */
    public static float attackRecoilOffset(float strength, float maxPixels) {
        float clampedStrength = Math.max(0f, Math.min(1f, strength));
        return clampedStrength * clampedStrength * maxPixels;
    }

    // =========================================================================
    // PLAY TIME FORMATTER
    // =========================================================================
    /*
     * Formula: formatPlayTime
     * Derivation:
     *   minutes = floor(totalSeconds / 60)
     *   seconds = floor(totalSeconds % 60)
     *   Formatted as "M:SS" — minutes are unbounded; seconds are always two digits.
     * Edge cases:
     *   Negative input clamped to 0 via Math.max before integer cast.
     *   totalSeconds > 5999 renders as e.g. "100:05" — no realistic run exceeds this.
     */
    public static String formatPlayTime(float totalSeconds) {
        int safeTotalSeconds = Math.max(0, (int) totalSeconds);
        int minutes = safeTotalSeconds / 60;
        int seconds = safeTotalSeconds % 60;
        return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }

    // =========================================================================
    // WORLD-TO-TILE CONVERSION
    // =========================================================================
    /*
     * Formula: worldToTile
     * Derivation:
     *   tileIndex = floor(worldCoord / CELL_SIZE)
     *   Integer-divides a world-space coordinate by the cell size to get the tile index.
     *   Uses floor() so negative coordinates give the correct negative tile index.
     * Edge cases:
     *   Negative worldCoord produces a negative tile index (typically out of bounds).
     *   Callers that need in-bounds tiles must clamp the result themselves.
     */
    public static int worldToTile(float worldCoord) {
        return (int) Math.floor(worldCoord / Constants.CELL_SIZE);
    }

    // =========================================================================
    // BOSS FLOOR CHECK
    // =========================================================================
    /*
     * Formula: isBossFloor
     * Derivation:
     *   depth > 0 AND (depth % BOSS_FLOOR_INTERVAL == 0)
     *   Depth 0 is the start room — never a boss floor.
     *   Every BOSS_FLOOR_INTERVAL-th positive floor triggers a boss encounter.
     * Edge cases:
     *   depth <= 0 always returns false (start room, pre-game).
     *   BOSS_FLOOR_INTERVAL must not be zero; Constants guarantees it is 5.
     */
    public static boolean isBossFloor(int depth) {
        return depth > 0 && (depth % Constants.BOSS_FLOOR_INTERVAL == 0);
    }

    // =========================================================================
    // BOSS DEPTH-SCALED STAT
    // =========================================================================
    /*
     * Formula: bossDepthScaledStat
     * Derivation:
     *   scaledStat = baseStat * (1 + scalePerStep * (depth - firstAppearanceDepth))
     *   At depth == firstAppearanceDepth the multiplier is 1.0 (base stats unchanged).
     *   Each floor beyond firstAppearanceDepth adds scalePerStep fraction of baseStat.
     * Edge cases:
     *   depth < firstAppearanceDepth returns baseStat unchanged (no negative scaling).
     *   Returns float — caller uses Math.round() for integer stats.
     */
    public static float bossDepthScaledStat(float baseStat, int depth,
                                            int firstAppearanceDepth, float scalePerStep) {
        if (depth <= firstAppearanceDepth) return baseStat;
        return baseStat * (1f + scalePerStep * (depth - firstAppearanceDepth));
    }

    // =========================================================================
    // WEAPON LEVEL SCALING — damage, accuracy, reload, clip, range
    // =========================================================================
    /*
     * Formula: Weapon level damage scaling
     * Derivation:
     *   scaledDamage = round(baseDamage * (1 + DAMAGE_PER_LEVEL * (level - 1)))
     *   Level 1 returns baseDamage unchanged (multiplier = 1.0).
     *   Each additional level adds WEAPON_LEVEL_DAMAGE_PER_LEVEL (10%) of baseDamage.
     *   Level is clamped to [1, MAX_WEAPON_LEVEL] before computation.
     * Edge cases:
     *   level = 1 → multiplier = 1.0, returns baseDamage unchanged.
     *   baseDamage = 0 → returns 0 at any level (melee with 0 base).
     */
    public static int weaponScaledDamage(int baseDamage, int weaponLevel) {
        int clampedLevel = Math.max(1, Math.min(weaponLevel, WeaponConstants.MAX_WEAPON_LEVEL));
        float multiplier = 1f + WeaponConstants.WEAPON_LEVEL_DAMAGE_PER_LEVEL * (clampedLevel - 1);
        return Math.round(baseDamage * multiplier);
    }

    /*
     * Formula: Weapon level accuracy scaling
     * Derivation:
     *   scaled = clamp(baseAccuracy + ACCURACY_PER_LEVEL * (level - 1), ACCURACY_MINIMUM, 1.0)
     *   Level 1 returns baseAccuracy unchanged.
     *   Each additional level adds WEAPON_LEVEL_ACCURACY_PER_LEVEL (2%) accuracy.
     *   Clamped so accuracy never drops below WEAPON_LEVEL_ACCURACY_MINIMUM or exceeds 1.0.
     * Edge cases:
     *   melee passes baseAccuracy = 1.0 and always returns 1.0 (never misses).
     *   If baseAccuracy < ACCURACY_MINIMUM even at level 1, the minimum floor is applied.
     */
    public static float weaponScaledAccuracy(float baseAccuracy, int weaponLevel) {
        int clampedLevel = Math.max(1, Math.min(weaponLevel, WeaponConstants.MAX_WEAPON_LEVEL));
        float scaled = baseAccuracy + WeaponConstants.WEAPON_LEVEL_ACCURACY_PER_LEVEL * (clampedLevel - 1);
        return Math.max(WeaponConstants.WEAPON_LEVEL_ACCURACY_MINIMUM, Math.min(1f, scaled));
    }

    /*
     * Formula: Weapon level reload scaling
     * Derivation:
     *   scaled = max(WEAPON_RELOAD_MIN_TICKS, round(baseReloadTicks - RELOAD_STEP * (level - 1)))
     *   Level 1 returns baseReloadTicks unchanged.
     *   Each additional level subtracts WEAPON_LEVEL_RELOAD_STEP (0.15) ticks.
     *   Floored at WEAPON_RELOAD_MIN_TICKS (1) to prevent instant reloads.
     * Edge cases:
     *   Weapons already at 1 tick (Shotgun, DBL Shotgun) remain at 1 at every level.
     *   baseReloadTicks = 0 (melee has no reload) returns 0 immediately via early return, bypassing the min() floor.
     */
    public static int weaponScaledReloadTicks(int baseReloadTicks, int weaponLevel) {
        if (baseReloadTicks == 0) return 0;
        int clampedLevel = Math.max(1, Math.min(weaponLevel, WeaponConstants.MAX_WEAPON_LEVEL));
        float scaled = baseReloadTicks - WeaponConstants.WEAPON_LEVEL_RELOAD_STEP * (clampedLevel - 1);
        return Math.max(WeaponConstants.WEAPON_RELOAD_MIN_TICKS, Math.round(scaled));
    }

    /*
     * Formula: Weapon level clip scaling
     * Derivation:
     *   scaled = baseClipSize + floor(CLIP_PER_LEVEL * (level - 1) * baseClipSize)
     *   Level 1 returns baseClipSize unchanged (floor(0) = 0 bonus).
     *   Each additional level adds WEAPON_LEVEL_CLIP_PER_LEVEL (8%) of baseClipSize.
     *   Accumulated bonus uses floor() so clip-1 weapons stay at 1 until bonus >= 1.
     * Edge cases:
     *   baseClipSize = 0 (melee) → always returns 0 (no ammo system).
     *   clip-1 weapons (Shotgun, Railgun) remain at 1 up to MAX_WEAPON_LEVEL (10),
     *   since 8% × 9 levels ≈ 72% bonus, which floors to 0 (< 100% needed for +1).
     */
    public static int weaponScaledClipSize(int baseClipSize, int weaponLevel) {
        if (baseClipSize == 0) return 0;
        int clampedLevel = Math.max(1, Math.min(weaponLevel, WeaponConstants.MAX_WEAPON_LEVEL));
        int bonus = (int)(WeaponConstants.WEAPON_LEVEL_CLIP_PER_LEVEL * (clampedLevel - 1) * baseClipSize);
        return baseClipSize + bonus;
    }

    /*
     * Formula: Weapon level range scaling
     * Derivation:
     *   bonus = min(floor((level - 1) * RANGE_PER_2_LEVELS / 2), RANGE_MAX_BONUS)
     *   Simplified: +1 tile for every 2 weapon levels gained, capped at +3 tiles.
     *   Level 1 → +0, level 3 → +1, level 5 → +2, level 7+ → +3 (capped).
     * Edge cases:
     *   Melee weapons always return 1 regardless of level (melee range is invariant).
     *   baseRange = 0 returns 0 (no range weapon with 0 base would exist in practice).
     */
    public static int weaponScaledRange(int baseRange, int weaponLevel, boolean isMelee) {
        if (isMelee) return 1;
        int clampedLevel = Math.max(1, Math.min(weaponLevel, WeaponConstants.MAX_WEAPON_LEVEL));
        int bonus = Math.min(
                (int)((clampedLevel - 1) * WeaponConstants.WEAPON_LEVEL_RANGE_PER_2_LEVELS / 2f),
                WeaponConstants.WEAPON_LEVEL_RANGE_MAX_BONUS);
        return baseRange + bonus;
    }

    /*
     * Formula: Hit chance resolution
     * Derivation: hitChance = weaponAccuracy * playerAccuracyMultiplier
     *             clamped to [0, 1].
     *             Returns true if the shot hits (random() <= hitChance).
     * Edge cases: accuracy >= 1.0 short-circuits to true (never allocates a random call).
     *             accuracy <= 0.0 always misses.
     */
    public static boolean resolveHitChance(float weaponAccuracy,
                                           float playerAccuracyMultiplier,
                                           java.util.Random rng) {
        float hitChance = MathUtils.clamp(weaponAccuracy * playerAccuracyMultiplier, 0f, 1f);
        if (hitChance >= 1.0f) return true;
        if (hitChance <= 0.0f) return false;
        return rng.nextFloat() <= hitChance;
    }

    /*
     * Formula: Ability magnitude linear scaling
     * Derivation:
     *   scaled = clamp(baseMagnitude + perLevel * (level - 1), 0, maxMagnitude)
     *   Level 1 returns baseMagnitude unchanged.
     *   Each additional level adds perLevel to the magnitude.
     * Edge cases:
     *   level = 1 → returns baseMagnitude.
     *   maxMagnitude = 0 → always returns 0 (ability disabled by caller).
     */
    public static float abilityMagnitudeScaled(float baseMagnitude, float perLevel,
                                               int weaponLevel, float maxMagnitude) {
        int clampedLevel = Math.max(1, Math.min(weaponLevel, WeaponConstants.MAX_WEAPON_LEVEL));
        float scaled = baseMagnitude + perLevel * (clampedLevel - 1);
        return Math.max(0f, Math.min(maxMagnitude, scaled));
    }

    /*
     * Formula: Ability count stepped scaling
     * Derivation:
     *   scaled = clamp(baseCount + floor((level - 1) / levelsPerStep), minCount, maxCount)
     *   Level 1 returns baseCount.
     *   Every levelsPerStep additional levels add +1 to the count.
     * Edge cases:
     *   level = 1 → floor(0 / levelsPerStep) = 0, returns baseCount.
     *   levelsPerStep = 0 → would divide by zero; treated as levelsPerStep = 1.
     *   maxCount < minCount: result is clamped to minCount (degenerate but safe).
     */
    public static int abilityCountScaled(int baseCount, int levelsPerStep,
                                         int weaponLevel, int minCount, int maxCount) {
        int clampedLevel = Math.max(1, Math.min(weaponLevel, WeaponConstants.MAX_WEAPON_LEVEL));
        int safeStep = Math.max(1, levelsPerStep);
        int scaled = baseCount + (clampedLevel - 1) / safeStep;
        return Math.max(minCount, Math.min(maxCount, scaled));
    }

    /*
     * Formula: Armor-pierce damage
     * Derivation:
     *   Normally all of the enemy's armor reduces damage:
     *     normalDamage = max(1, rawDamage - armor)
     *   With pierceFraction in [0, 1], a portion of the armor is bypassed.
     *   Only the un-pierced fraction of armor still applies:
     *     unpiercedReduction = armor * (1 - pierceFraction)
     *     effectiveDamage = max(1, rawDamage - unpiercedReduction)
     *   pierceFraction = 0.0 → full armor reduction (same as normal).
     *   pierceFraction = 1.0 → armor completely ignored; raw damage applied.
     *   pierceFraction = 0.5 → only half the armor reduces damage.
     * Edge cases:
     *   pierceFraction clamped to [0, 1] — negative pierce or over-1 pierce have no meaning.
     *   rawDamage <= 0: result clamped to 1 (always deal at least 1 damage).
     *   armor = 0: unpiercedReduction is 0; result equals max(1, rawDamage).
     */
    public static int armorPierceDamage(int rawDamage, int armor, float pierceFraction) {
        float clampedPierce      = Math.max(0f, Math.min(1f, pierceFraction));
        int   unpiercedReduction = Math.round(armor * (1f - clampedPierce));
        return Math.max(1, rawDamage - unpiercedReduction);
    }

    /*
     * Formula: Block absorption (strategy-combat-order-3)
     * Derivation:
     *   Block is a temporary HP-shield consumed before HP (and before flat armor). Step 3 of the
     *   shared 5-step mitigation pipeline documented in docs/balance-rule-system.txt:
     *     absorbed = min(block, incomingDamage)
     *   The caller then does: block -= absorbed; remainingDamage = incomingDamage - absorbed.
     *   A shot fully swallowed by fresh Block (incomingDamage <= block) is entirely absorbed and
     *   deals 0 HP damage — overkill into Block is lost, which is the intended counterplay ("don't
     *   dump your big shot into fresh Block").
     * Edge cases:
     *   block <= 0            → returns 0 (nothing absorbed; pipeline is a no-op, matching pre-Block behaviour).
     *   incomingDamage <= 0   → returns 0 (a non-positive hit consumes no Block).
     *   incomingDamage > block → returns block (Block shatters; the overflow carries through to armor/HP).
     */
    public static int blockAbsorbed(int block, int incomingDamage) {
        if (block <= 0 || incomingDamage <= 0) return 0;
        return Math.min(block, incomingDamage);
    }

    /*
     * Formula: Defend Block gain (strategy-combat-order-3)
     * Derivation:
     *   An enemy that commits DEFEND braces for a role-based base Block, scaled to the floor so a
     *   deep-floor bracing enemy stays proportionally as sturdy as it does on floor 1 (Block is
     *   transient eHP, so it tracks the same depth curve as HP — GameBalance.enemyHealthScaleForDepth):
     *     gain = round(baseBlock * depthHealthScale)
     *   Then clamped to blockMax so defend-spam can never stack Block into immortality:
     *     result = min(blockMax, gain)
     *   Priced so one DEFEND buys ~1 turn of survival (baseBlock ≈ reference player DPT); see the
     *   BALANCE CONTRACT in the idea doc and the BLOCK section of docs/balance-rule-system.txt.
     * Edge cases:
     *   baseBlock <= 0      → returns 0 (this role never braces; caller should not commit DEFEND).
     *   depthHealthScale <= 0 → treated as no scaling is impossible here; round of a non-positive base
     *                           still yields 0, and the min() keeps the result within [0, blockMax].
     *   blockMax < gain     → result clamped down to blockMax.
     */
    public static int defendBlockGain(int baseBlock, float depthHealthScale, int blockMax) {
        if (baseBlock <= 0) return 0;
        int gain = Math.round(baseBlock * depthHealthScale);
        return Math.max(0, Math.min(blockMax, gain));
    }

    /*
     * Formula: Vulnerable damage multiplier (strategy-combat-order-6)
     * Derivation:
     *   VULNERABLE makes the marked host take more damage. It is a Step-2 OUTGOING multiplier in the
     *   shared mitigation pipeline (applied to the incoming hit BEFORE Block/armor). Each stack adds a
     *   fixed percent, so N stacks at P% each give:
     *     multiplier = 1 + stacks * P/100
     *   The caller multiplies the raw hit by this before Block absorption, so a Vulnerable target both
     *   loses more Block AND takes more HP damage — the "land a cheap mark, then dump the big shot" play.
     * Edge cases:
     *   stacks <= 0 → returns 1.0 (unmarked target: pipeline no-op).
     *   percentPerStack <= 0 → returns 1.0 (no configured potency).
     *   Stacks are capped by the caller (StatusEffectController) at VULNERABLE_MAX_STACKS, so this
     *   cannot run away; this pure helper does no clamping of its own.
     */
    public static float vulnerableDamageMultiplier(int stacks, int percentPerStack) {
        if (stacks <= 0 || percentPerStack <= 0) return 1f;
        return 1f + stacks * (percentPerStack / 100f);
    }

    /*
     * Formula: Weak damage multiplier (strategy-combat-order-6)
     * Derivation:
     *   WEAK reduces the DEALT damage of whoever holds it (an enemy softened by the player, or the
     *   player debuffed by an enemy). It is a Step-2 multiplier applied to the attacker's OUTGOING
     *   damage:
     *     multiplier = 1 - P/100   (when active), else 1
     *   Floored at 0 so an over-100% configuration can never flip the sign and heal the target.
     * Edge cases:
     *   active == false → returns 1.0 (no debuff).
     *   percent >= 100  → clamped to 0.0 (a fully-neutralised hit deals nothing, never negative).
     *   percent <= 0    → returns 1.0.
     */
    public static float weakDamageMultiplier(boolean active, int percent) {
        if (!active || percent <= 0) return 1f;
        return Math.max(0f, 1f - percent / 100f);
    }

    /*
     * Formula: Backstab/flank damage multiplier (strategy-combat-order-6)
     * Derivation:
     *   A hit landed from BEHIND the target's facing deals a modest positional bonus:
     *     multiplier = 1 + P/100   (from behind), else 1
     *   Priced as a conditional bonus (like crit) because it requires setup/positioning; kept small
     *   (±30% default) so it is a nudge toward maneuvering, not a mandatory execution combo.
     * Edge cases:
     *   fromBehind == false → returns 1.0.
     *   percent <= 0        → returns 1.0.
     */
    public static float backstabDamageMultiplier(boolean fromBehind, int percent) {
        if (!fromBehind || percent <= 0) return 1f;
        return 1f + percent / 100f;
    }

    /*
     * Formula: attacker-behind-facing test (strategy-combat-order-6)
     * Derivation:
     *   An actor's implicit facing is the cardinal unit vector it last moved/attacked along. The
     *   attacker is "behind" the target when the vector from the target to the attacker points against
     *   that facing — i.e. their dot product is negative:
     *     toAttacker = (attackerColumn - targetColumn, attackerRow - targetRow)
     *     behind = dot(toAttacker, facing) < 0
     *   Mirrors EnemyManager.isBehindPlayerFacing (enemy-behind-player), reused here for
     *   player-behind-enemy so positioning matters symmetrically.
     * Edge cases:
     *   facing = (0,0) (never moved) → dot is 0, not < 0 → returns false (no backstab on a
     *     never-committed enemy, which is the safe/generous-to-the-enemy default).
     *   attacker on the target's own tile → toAttacker = (0,0) → dot 0 → false.
     *   Pure integer arithmetic — no precision issues.
     */
    public static boolean isAttackerBehindFacing(int facingColumn, int facingRow,
                                                 int targetColumn, int targetRow,
                                                 int attackerColumn, int attackerRow) {
        int toAttackerColumn = attackerColumn - targetColumn;
        int toAttackerRow    = attackerRow    - targetRow;
        return toAttackerColumn * facingColumn + toAttackerRow * facingRow < 0;
    }

    // =========================================================================
    // ABILITY EVENT FEEDBACK — BANNER ANIMATION AND RING PULSE
    // =========================================================================

    /*
     * Formula: bannerPopScale
     * Derivation:
     *   Returns a scale multiplier ≥ 1.0 that drives the pop-in / punch-in entrance
     *   animation for tiered ability banners.
     *   At normalizedAge = 0 (just spawned) the scale is (1 + overshoot), the largest.
     *   It decays linearly to 1.0 at normalizedAge = 1 (animation fully settled).
     *   scaleFactor = 1 + overshoot × (1 − normalizedAge)
     *   Callers pass normalizedAge = age / animationDuration; clamp to [0, 1] at call site or
     *   let the >= 1 guard here return the settled value.
     * Edge cases:
     *   normalizedAge < 0 → clamped to 0 → returns maximum scale (1 + overshoot).
     *   overshoot = 0 → returns 1.0 always (no animation).
     *   normalizedAge ≥ 1 → settled at exactly 1.0.
     */
    public static float bannerPopScale(float normalizedAge, float overshoot) {
        if (normalizedAge >= 1f) return 1f;
        float clamped = Math.max(0f, normalizedAge);
        return 1f + overshoot * (1f - clamped);
    }

    /*
     * Formula: ringPulseRadius
     * Derivation:
     *   Expands from 0 to maxRadius using an ease-out square-root curve.
     *   radius = maxRadius × √normalizedAge
     *   Square root gives rapid initial expansion that decelerates toward the rim,
     *   matching the physical feel of a shockwave or energy pulse.
     * Edge cases:
     *   normalizedAge ≤ 0 → radius = 0 (ring starts at the origin).
     *   normalizedAge ≥ 1 → radius = maxRadius (fully expanded).
     */
    public static float ringPulseRadius(float normalizedAge, float maxRadius) {
        float clamped = Math.max(0f, Math.min(1f, normalizedAge));
        return maxRadius * (float) Math.sqrt(clamped);
    }

    /*
     * Formula: ringPulseAlpha
     * Derivation:
     *   Fades from 1.0 (fully visible at spawn) to 0.0 (gone at expiry) linearly.
     *   alpha = 1 − normalizedAge
     *   Linear fade pairs with the sqrt expansion so the ring fades as it slows.
     * Edge cases:
     *   normalizedAge ≤ 0 → alpha = 1.0 (fully opaque).
     *   normalizedAge ≥ 1 → alpha = 0.0 (fully transparent, ring gone).
     */
    public static float ringPulseAlpha(float normalizedAge) {
        float clamped = Math.max(0f, Math.min(1f, normalizedAge));
        return 1f - clamped;
    }

    // =========================================================================
    // BALANCE RULE SYSTEM — the four primitives + derived contract math
    // -------------------------------------------------------------------------
    // The whole balance contract (docs/balance-rule-system.txt) rests on four
    // primitive quantities — eHP, DPT, TTK, TP — plus a handful of derived
    // scores. The game is strictly turn-based (one action = one world turn), so
    // TIME is measured in TURNS, never seconds: there is no real-time DPS, only
    // damage-per-turn. That makes every number below exact arithmetic.
    //
    // The reference anchors these formulas compare against (REFERENCE_PLAYER_DPT,
    // REFERENCE_PLAYER_EHP, REFERENCE_AMMO_EFFICIENCY) and the per-role power / TP
    // bands all live in BalanceConfig. These methods are pure and unit-free; the
    // caller supplies the numbers (BalanceReport tabulates the whole roster).
    // =========================================================================

    /*
     * Formula: effectiveHitPoints (eHP) — PRIMITIVE 1
     * Derivation:
     *   "How much raw incoming damage a thing really absorbs before dying."
     *   Start from the raw survivability pool (hit points plus the armour pool,
     *   which in this simplified contract model is treated as extra absorbed HP):
     *       pool = rawHitPoints + armorPool
     *   Dodge multiplies survivability by 1/(1 - dodgeChance): a dodged hit deals
     *   zero, so on average it takes 1/(1 - dodgeChance) incoming hits to land the
     *   same total damage.
     *   Flat reduction subtracts from each incoming hit of average size
     *   averageIncomingHit, so each landed hit of size H only removes (H - R) from
     *   the pool — multiplying survivability by H / max(1, H - R).
     *       eHP = pool * 1/(1 - dodgeChance) * averageIncomingHit / max(1, averageIncomingHit - flatReduction)
     *   Worked: MARINE start (130 HP, 75 armour, 0 dodge, 0 reduction) -> 205 eHP.
     *           Tough/Agile build (160 HP, 75 armour, 0.20 dodge, R=6, H=12)
     *           -> 235 * 1.25 * 2 = 587 eHP.
     *   Enemies currently have no dodge or reduction, so enemy eHP == raw HP.
     * Edge cases:
     *   dodgeChance >= 1 would divide by zero / go negative -> clamped to 0.99
     *     (a 100%-dodge entity is unkillable and not a valid contract input).
     *   averageIncomingHit - flatReduction is floored at 1 so reduction can never
     *     make a hit deal <= 0 (that would be infinite eHP).
     *   averageIncomingHit <= 0 (no incoming reference hit) -> the flat-reduction
     *     term is neutralised (factor 1.0), leaving just pool and dodge.
     */
    public static float effectiveHitPoints(float rawHitPoints, float armorPool,
                                           float dodgeChance, float flatReduction,
                                           float averageIncomingHit) {
        float survivabilityPool = rawHitPoints + armorPool;
        float clampedDodge = Math.max(0f, Math.min(0.99f, dodgeChance));
        float dodgeFactor = 1f / (1f - clampedDodge);
        float reductionFactor = 1f;
        if (averageIncomingHit > 0f) {
            float damageAfterReduction = Math.max(1f, averageIncomingHit - flatReduction);
            reductionFactor = averageIncomingHit / damageAfterReduction;
        }
        return survivabilityPool * dodgeFactor * reductionFactor;
    }

    /*
     * Formula: sustainedDamagePerTurn (sustained DPT) — PRIMITIVE 2
     * Derivation:
     *   Damage output averaged over a full fire-the-whole-clip-then-reload cycle.
     *   A clip delivers (clipSize * damagePerShot) damage and the cycle costs
     *   clipSize turns of firing plus reloadTicks turns of reloading:
     *       sustainedDPT = (clipSize * damagePerShot) / (clipSize + reloadTicks)
     *   Worked: Shotgun (1*50)/(1+1)=25.0 ; Chaingun (24*10)/(24+1)=9.6 ;
     *           Railgun full (1*90)/(1+2)=30.0.
     *   This deliberately PENALISES tiny clips with long reloads, which is the
     *   honest cost of burst weapons over a sustained fight.
     * Edge cases:
     *   clipSize + reloadTicks <= 0 -> returns 0 (degenerate weapon definition).
     *   reloadTicks = 0 collapses to raw per-shot damage (a no-reload weapon).
     */
    public static float sustainedDamagePerTurn(int clipSize, float damagePerShot, int reloadTicks) {
        int cycleTurns = clipSize + reloadTicks;
        if (cycleTurns <= 0) {
            return 0f;
        }
        return (clipSize * damagePerShot) / cycleTurns;
    }

    /*
     * Formula: ammoEfficiency (damage per ammo unit) — PRIMITIVE 2b
     * Derivation:
     *   How much damage one unit of ammunition buys:
     *       ammoEfficiency = damagePerShot / ammoPerShot
     *   Multiplied by the ammo the economy hands you over a run, this is a
     *   weapon's true "run budget" (the bridge to the scarcity model, idea 3).
     * Edge cases:
     *   ammoPerShot <= 0 -> floored at 1 (a shot must cost at least one unit).
     */
    public static float ammoEfficiency(float damagePerShot, int ammoPerShot) {
        int safeAmmoPerShot = Math.max(1, ammoPerShot);
        return damagePerShot / safeAmmoPerShot;
    }

    /*
     * Formula: turnsToKill (TTK) — PRIMITIVE 3
     * Derivation:
     *   Turns of fire needed to drop a target, rounded UP because a partial turn
     *   still costs a whole turn:
     *       turnsToKill = ceil(targetEffectiveHitPoints / attackerDamagePerTurn)
     *   Worked: Iron Stalker 95 eHP / 50 burst -> ceil(1.9) = 2 turns.
     *   Symmetric use: feeding the ENEMY's DPT and the PLAYER's eHP yields the
     *   player's Turns-To-Die (TTD) against that enemy.
     * Edge cases:
     *   attackerDamagePerTurn <= 0 -> returns Integer.MAX_VALUE (target is
     *     effectively unkillable by this attacker; avoids divide-by-zero).
     *   targetEffectiveHitPoints <= 0 -> returns 0 (already dead).
     */
    public static int turnsToKill(float targetEffectiveHitPoints, float attackerDamagePerTurn) {
        if (targetEffectiveHitPoints <= 0f) {
            return 0;
        }
        if (attackerDamagePerTurn <= 0f) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.ceil(targetEffectiveHitPoints / attackerDamagePerTurn);
    }

    /*
     * Formula: threatPoints (TP) — PRIMITIVE 4
     * Derivation:
     *   One comparable "danger number" per enemy. An enemy is dangerous in
     *   proportion to (a) how hard it hits per turn, (b) how many turns it
     *   survives your fire (= how long it keeps hitting you), and (c) whether it
     *   can hurt you without being adjacent.
     *       enemyDamagePerTurn = attackDamage / max(1, attackCadenceTurns)
     *       survivalTurns      = enemyEffectiveHitPoints / referencePlayerDamagePerTurn
     *       threatPoints       = enemyDamagePerTurn * survivalTurns * positionalMultiplier
     *   referencePlayerDamagePerTurn anchors survival on a fixed yardstick
     *   (BalanceConfig.REFERENCE_PLAYER_DPT = 25, the shotgun) so every enemy is
     *   measured against the same player. positionalMultiplier is a designer
     *   classification (1.0 melee, 1.30 ranged, 1.15 fast-melee, +0.25 if it
     *   applies a status effect), NOT auto-derived from move cadence.
     *   Worked: Iron Stalker (16/1) * (95/25) * 1.15 = 69.9 TP (mini-elite).
     * Edge cases:
     *   attackCadenceTurns < 1 -> floored at 1 (an enemy attacks at most once per
     *     turn, the game's atomic unit).
     *   referencePlayerDamagePerTurn <= 0 -> returns 0 (no yardstick, no
     *     comparable threat; avoids divide-by-zero).
     */
    public static float threatPoints(float attackDamage, int attackCadenceTurns,
                                     float enemyEffectiveHitPoints,
                                     float referencePlayerDamagePerTurn,
                                     float positionalMultiplier) {
        if (referencePlayerDamagePerTurn <= 0f) {
            return 0f;
        }
        int safeCadence = Math.max(1, attackCadenceTurns);
        float enemyDamagePerTurn = attackDamage / safeCadence;
        float survivalTurns = enemyEffectiveHitPoints / referencePlayerDamagePerTurn;
        return enemyDamagePerTurn * survivalTurns * positionalMultiplier;
    }

    /*
     * Formula: damagePerTurnPowerPoints — UPGRADE PRICING (idea 5)
     * Derivation:
     *   The level-up card system prices every upgrade in a common currency, "power
     *   points" (PP) = the %-gain it grants to the player's reference offence. An
     *   upgrade that raises the player's effective Damage-Per-Turn by D, against a
     *   fixed reference DPT, is worth:
     *       powerPoints = (D / referenceDamagePerTurn) * 100
     *   So an upgrade that adds 3 DPT on a reference DPT of 25 costs 12 PP. This is
     *   the offence half of the budget; survivability uses the eHP variant below.
     *   Pricing OFFENCE and DEFENCE in the same %-of-reference unit is what lets a
     *   single fixed budget make a damage card and an armour card cost the same.
     * Edge cases:
     *   referenceDamagePerTurn <= 0 -> returns 0 (no yardstick; avoids divide-by-zero).
     *   damagePerTurnGain may be negative (a trade-off card's downside) -> negative PP.
     */
    public static float damagePerTurnPowerPoints(float damagePerTurnGain, float referenceDamagePerTurn) {
        if (referenceDamagePerTurn <= 0f) {
            return 0f;
        }
        return (damagePerTurnGain / referenceDamagePerTurn) * 100f;
    }

    /*
     * Formula: effectiveHitPointPowerPoints — UPGRADE PRICING (idea 5)
     * Derivation:
     *   The survivability half of the upgrade-pricing currency. An upgrade that raises
     *   the player's effective HP by E, against a fixed reference eHP, is worth:
     *       powerPoints = (E / referenceEffectiveHitPoints) * 100
     *   so +25 eHP on a reference eHP of 205 costs ~12.2 PP — the same budget a +3-DPT
     *   damage card costs. The eHP GAIN itself is computed by the caller through
     *   GameMath.effectiveHitPoints (armour adds directly to the survivability pool;
     *   dodge multiplies it by 1/(1-dodge); flat reduction by H/(H-R)), so all the
     *   real survivability math stays in one place and only the %-conversion lives here.
     * Edge cases:
     *   referenceEffectiveHitPoints <= 0 -> returns 0 (no yardstick; avoids divide-by-zero).
     *   effectiveHitPointGain may be negative (a trade-off card's downside) -> negative PP.
     */
    public static float effectiveHitPointPowerPoints(float effectiveHitPointGain, float referenceEffectiveHitPoints) {
        if (referenceEffectiveHitPoints <= 0f) {
            return 0f;
        }
        return (effectiveHitPointGain / referenceEffectiveHitPoints) * 100f;
    }

    /*
     * Formula: weaponPowerScore — the weapon contract's single comparable number
     * Derivation:
     *   A weapon's power is its sustained output scaled by how ammo-cheap that
     *   output is, so a high-DPT but ammo-hungry weapon does not out-score a
     *   leaner one purely on muzzle damage:
     *       weaponPowerScore = sustainedEffectiveDamagePerTurn * sqrt(ammoEfficiencyNormalized)
     *   The square root DAMPENS the ammo-efficiency term: doubling efficiency
     *   only multiplies power by ~1.41, not 2, so per-shot damage cannot dominate
     *   the score. ammoEfficiencyNormalized is the weapon's raw ammoEfficiency
     *   divided by BalanceConfig.REFERENCE_AMMO_EFFICIENCY, so a reference-class
     *   weapon contributes a factor of 1.0.
     *   A new weapon picks a ROLE, then is tuned until its score lands in that
     *   role's band (BalanceConfig power-band constants). Higher rarity does NOT
     *   raise the band — it buys abilities, not raw damage.
     * Edge cases:
     *   ammoEfficiencyNormalized <= 0 -> the sqrt term is 0, so the score is 0
     *     (a weapon with no ammo value scores nothing on the run-budget axis).
     */
    public static float weaponPowerScore(float sustainedEffectiveDamagePerTurn,
                                         float ammoEfficiencyNormalized) {
        float safeEfficiency = Math.max(0f, ammoEfficiencyNormalized);
        return sustainedEffectiveDamagePerTurn * (float) Math.sqrt(safeEfficiency);
    }

    /*
     * Formula: goldenRatio — the "tense but winnable" knob (TTD / TTK)
     * Derivation:
     *   For a fair 1-on-1 the player should win, but not trivially. Compare how
     *   long the player survives (turnsToDie) against how long the player needs
     *   to kill (turnsToKill):
     *       goldenRatio = turnsToDie / turnsToKill
     *   Target band: [3, 8] for trash mobs (you could kill 3-8 of this enemy
     *   back-to-back if you took every hit — but you WON'T if you play well),
     *   [2, 4] for bruisers (they are supposed to be scary 1v1). Below the band =
     *   unfair/swingy; above it = harmless damage sponges.
     * Edge cases:
     *   turnsToKill <= 0 -> returns Float.POSITIVE_INFINITY (the player kills
     *     instantly and is in no danger; avoids divide-by-zero).
     */
    public static float goldenRatio(int turnsToDie, int turnsToKill) {
        if (turnsToKill <= 0) {
            return Float.POSITIVE_INFINITY;
        }
        return (float) turnsToDie / (float) turnsToKill;
    }

    /*
     * Formula: depthThreatScale — keep player and enemy curves coupled by depth
     * Derivation:
     *   Enemy threat compounds per floor through two independent multipliers,
     *   one on health and one on damage. Because threat points are roughly
     *   (damage * survival) and survival scales with health, the per-floor threat
     *   multiplier is the PRODUCT of both compound curves:
     *       depthThreatScale = healthScalePerDepth^(depth-1) * damageScalePerDepth^(depth-1)
     *   so enemyThreatAtDepth(d) = baseThreatPoints * depthThreatScale(d).
     *   With current values (1.08 health, 1.06 damage) a depth-5 enemy is
     *   1.08^4 * 1.06^4 = 1.360 * 1.262 = 1.717x its depth-1 threat. The player
     *   power curve must stay coupled: 0.9 <= playerPower/enemyThreat <= 1.2.
     * Edge cases:
     *   depth <= 1 -> exponent 0 -> returns 1.0 (floor 1 is the un-scaled base).
     */
    public static float depthThreatScale(float healthScalePerDepth, float damageScalePerDepth, int depth) {
        int floorsDescended = Math.max(0, depth - 1);
        float healthGrowth = (float) Math.pow(healthScalePerDepth, floorsDescended);
        float damageGrowth = (float) Math.pow(damageScalePerDepth, floorsDescended);
        return healthGrowth * damageGrowth;
    }

    /*
     * Formula: floorThreatPointBudget — difficulty as a DIAL (balance idea 4, Pillar 1)
     * Derivation:
     *   A floor's total danger is one number the generator SPENDS, instead of rolling
     *   enemies at random. The base budget is the depth-1 reference (BASE_TP), and it
     *   grows per floor by the SAME coupled curve that scales individual enemies:
     *       floorThreatPointBudget = baseThreatPointBudget * depthThreatScale(d)
     *   Because each enemy's effective Threat-Point cost is ALSO scaled by
     *   depthThreatScale(d) at spawn time (EnemyManager health/damage scaling, mirrored
     *   by enemyThreatAtDepth below), the budget and the per-enemy cost grow in lockstep.
     *   The result: the enemy COUNT stays roughly constant across depth (budget / unit
     *   cost), while each enemy gets stronger — difficulty rises through per-enemy power,
     *   not through an exploding horde, and never double-counts the depth curve.
     * Edge cases:
     *   depth <= 1 -> depthThreatScale returns 1.0 -> budget == baseThreatPointBudget.
     *   baseThreatPointBudget <= 0 -> returns 0 (no budget, no enemies).
     */
    public static float floorThreatPointBudget(float baseThreatPointBudget,
                                               float healthScalePerDepth,
                                               float damageScalePerDepth,
                                               int depth) {
        if (baseThreatPointBudget <= 0f) {
            return 0f;
        }
        return baseThreatPointBudget * depthThreatScale(healthScalePerDepth, damageScalePerDepth, depth);
    }

    /*
     * Formula: enemyThreatAtDepth — an archetype's Threat-Point cost on a given floor
     * Derivation:
     *   An enemy's depth-1 base Threat-Point value (EnemyType.baseThreatPoints) is scaled
     *   to the floor it spawns on by the same coupled depth curve that scales its HP and
     *   damage:
     *       enemyThreatAtDepth = baseThreatPoints * depthThreatScale(d)
     *   This is the per-unit cost the encounter budget (floorThreatPointBudget) is spent
     *   against, so count = budget / unitCost is depth-stable (see derivation above).
     * Edge cases:
     *   depth <= 1 -> returns baseThreatPoints unchanged.
     */
    public static float enemyThreatAtDepth(float baseThreatPoints,
                                           float healthScalePerDepth,
                                           float damageScalePerDepth,
                                           int depth) {
        return baseThreatPoints * depthThreatScale(healthScalePerDepth, damageScalePerDepth, depth);
    }

    /*
     * Formula: playerPowerAtDepth — the player's expected power multiplier on a given floor
     * Derivation:
     *   The depth-coupling invariant (docs/balance-rule-system.txt, DEPTH SCALING) compares the
     *   player's power curve against the enemy threat curve and demands they stay coupled. The
     *   enemy side is depthThreatScale (a COMPOUND curve). The player side is set by the level-up
     *   power budget: every level grants a fixed LEVEL_UP_BUDGET_PP power points (idea 5), and the
     *   total-PP invariant guarantees a player's total power at level L is L * budget regardless of
     *   WHICH cards were taken. Modelling the player as gaining levelsPerDepth levels per floor
     *   descended, the accumulated power points by depth d are:
     *       accumulatedPowerPoints = budgetPowerPointsPerLevel * levelsPerDepth * (depth - 1)
     *   Power points are a %-gain to the player's reference output (damagePerTurnPowerPoints is the
     *   forward direction), so the player's power MULTIPLIER relative to the depth-1 baseline is:
     *       playerPowerAtDepth = 1 + accumulatedPowerPoints / 100
     *   This is deliberately ADDITIVE (linear in depth): the contract prices every upgrade as a flat
     *   %-of-reference, so the player curve is linear while the enemy curve compounds. Keeping the
     *   ratio in band over the run's depth range is therefore a matter of choosing an enemy compound
     *   rate (ENEMY_*_SCALE_PER_DEPTH) gentle enough that the compound curve does not outrun the
     *   linear one before the deepest floor. The conservative model ignores found weapons/armour,
     *   which only HELP the player, so the real ratio is at least this favourable.
     *   Worked: budget 12, 1 level/floor, depth 5 -> 1 + 12*1*4/100 = 1.48.
     * Edge cases:
     *   depth <= 1 -> floorsDescended is 0 -> returns 1.0 (floor 1 is the un-scaled baseline).
     *   Negative inputs are nonsensical config; the result simply tracks them (BalanceReport would
     *     surface a sub-1.0 floor-1 power as a bad number rather than being silently clamped).
     */
    public static float playerPowerAtDepth(float budgetPowerPointsPerLevel,
                                           float levelsPerDepth, int depth) {
        int floorsDescended = Math.max(0, depth - 1);
        float accumulatedPowerPoints = budgetPowerPointsPerLevel * levelsPerDepth * floorsDescended;
        return 1f + accumulatedPowerPoints / 100f;
    }

    /*
     * Formula: depthCouplingRatio — the depth-coupling invariant (balance contract, DEPTH SCALING)
     * Derivation:
     *   The single number that says whether a floor is fair: how the player's expected power
     *   compares to the floor's scaled enemy threat —
     *       depthCouplingRatio = playerPowerAtDepth / enemyThreatScale
     *   The invariant requires DEPTH_COUPLING_RATIO_MIN <= ratio <= DEPTH_COUPLING_RATIO_MAX
     *   ([0.9, 1.2]): below the min the floor is unfair-hard (the player fell behind the curve),
     *   above the max it is trivial-easy (the player outscaled it). Both inputs are multipliers
     *   relative to the depth-1 baseline (playerPowerAtDepth and depthThreatScale), so at depth 1
     *   both are 1.0 and the ratio is exactly 1.0 by construction.
     *   Worked: player 1.48 / enemy 1.718 at depth 5 (old 1.08/1.06 scales) = 0.86 -> UNDER (hard).
     * Edge cases:
     *   enemyThreatScale <= 0 -> returns 0 (no threat to measure against; avoids divide-by-zero).
     */
    public static float depthCouplingRatio(float playerPowerAtDepth, float enemyThreatScale) {
        if (enemyThreatScale <= 0f) {
            return 0f;
        }
        return playerPowerAtDepth / enemyThreatScale;
    }

    /*
     * Formula: hazardTileThreatPoints — fold terrain danger into the TP contract (idea 4, Pillar 3)
     * Derivation:
     *   A hazard tile (fire / toxic) has no health, so the enemy Threat-Point primitive's
     *   "survivalTurns" term (eHP / refDPT) does not apply. Instead a static hazard threatens
     *   the player for as many turns as the player carelessly stands in it:
     *       hazardTileThreatPoints = hazardDamagePerTurn * turnsStood * positionalMultiplier
     *   This is the SAME shape as threatPoints (damagePerTurn * danger-window * positional) so a
     *   hazard tile is directly comparable to an enemy's TP. A room with N such tiles raises its
     *   effective floor TP by ~N * this value — that is how "a room full of fire raises its
     *   effective TP" (idea 4) is made quantitative. turnsStood is a CARELESS-player reference
     *   (BalanceConfig.HAZARD_THREAT_TURNS_STOOD), not the hazard's spread lifetime: a skilled
     *   player leaves sooner and pays less, which is exactly the counterplay the hazard sells.
     * Edge cases:
     *   turnsStood <= 0 -> returns 0 (a hazard nobody ever stands in threatens nothing).
     *   hazardDamagePerTurn <= 0 -> returns 0 (no damage, no threat).
     */
    public static float hazardTileThreatPoints(float hazardDamagePerTurn, int turnsStood,
                                               float positionalMultiplier) {
        if (turnsStood <= 0 || hazardDamagePerTurn <= 0f) {
            return 0f;
        }
        return hazardDamagePerTurn * turnsStood * positionalMultiplier;
    }

    // =========================================================================
    // BOSS BALANCE RULESET — bosses are tuned by formula, not by flat HP (idea 6)
    // -------------------------------------------------------------------------
    // Bosses break the trash-mob threat math: a single entity meant to survive
    // many turns and threaten a PREPARED player. The golden-ratio / TP bands above
    // do NOT apply. A boss tuned like a big trash mob is either a 2-shot anticlimax
    // or an un-counterable HP wall. So a boss is tuned to a fight-LENGTH target and a
    // phase-structured threat curve instead. These methods are the contract idea 6
    // defines; boss FIGHTS themselves are deferred (they need story/run structure),
    // so for now these formulas exist to re-derive the placeholder boss numbers when
    // boss work lands, NEVER to bless a literal HP constant. Anchors and bands live
    // in BalanceConfig SECTION 14; see docs/balance-rule-system.txt (Boss appendix).
    //
    // The six rules, mapped to the methods below:
    //   RULE 1  HP from fight length, never a flat number  -> bossEffectiveHitPoints
    //   RULE 2  cap the fight length from above (no sponges) -> bossFightTurnsForPlayerDamagePerTurn
    //                                                          + bossUpperFightTurnsCap
    //   RULE 3  lethal-but-counterable damage              -> bossDamagePerTurnForSurvivalCheck
    //                                                          + bossSurvivalCheckRatio
    //                                                          + bossSingleHitFractionOfEffectiveHitPoints
    //   RULE 4  phases structure the threat curve          -> bossPhaseHealthThreshold
    //   RULE 5  the boss is a build check                  -> (it IS the HP target tied to
    //                                                          expectedPlayerSustainedDamagePerTurn)
    //   RULE 6  reward scaled to cost                      -> bossReward
    // =========================================================================

    /*
     * Formula: expectedPlayerSustainedDamagePerTurn — the player's likely DPT by a given depth
     * Derivation:
     *   A boss's HP must be tuned to what the EXPECTED player can output at the boss's
     *   depth, NOT to the fixed depth-1 reference shotgun. A leveled player with found
     *   upgrades out-DPSes the reference, so anchoring a boss to the reference makes it
     *   trivialise as builds scale (idea 6, RULE 1; the worked Corruptor lesson).
     *   Power points (idea 5) are the bridge: PP is a %-gain to the reference DPT
     *   (damagePerTurnPowerPoints is the forward direction). The accumulated OFFENCE
     *   power points a player is expected to have invested by this depth therefore lift
     *   the reference DPT by that percentage — this is exactly the inverse of
     *   damagePerTurnPowerPoints:
     *       expectedDPT = referenceDamagePerTurn * (1 + expectedOffencePowerPoints / 100)
     *   The caller supplies expectedOffencePowerPoints (the offence fraction of the
     *   level-up budget accumulated over the floors descended — see BalanceConfig
     *   SECTION 14), so the depth-dependence stays a tunable input, not a hidden constant.
     *   Worked: reference DPT 25, +96 offence PP by depth 5 -> 25 * 1.96 = 49 DPT.
     * Edge cases:
     *   expectedOffencePowerPoints = 0 -> returns referenceDamagePerTurn unchanged (a
     *     player who skipped every offence upgrade still has the start weapon).
     *   Negative expectedOffencePowerPoints is floored at -100% so DPT can never go
     *     negative (a strictly de-powered player still deals >= 0).
     */
    public static float expectedPlayerSustainedDamagePerTurn(float referenceDamagePerTurn,
                                                             float expectedOffencePowerPoints) {
        float liftFraction = Math.max(-1f, expectedOffencePowerPoints / 100f);
        return referenceDamagePerTurn * (1f + liftFraction);
    }

    /*
     * Formula: bossEffectiveHitPoints — boss HP derived from fight length (idea 6, RULE 1)
     * Derivation:
     *   A boss's HP is NEVER a literal constant. It is derived from how LONG the fight
     *   should last against the EXPECTED player's sustained output at that depth, times
     *   one factor per phase the player effectively re-fights:
     *       bossEffectiveHitPoints = expectedPlayerSustainedDamagePerTurn
     *                              * targetFightTurns
     *                              * multiPhaseFactor
     *   targetFightTurns is the design target band (18-40 for an act boss, 40-60 for a
     *   future run-final boss; BalanceConfig SECTION 14). multiPhaseFactor is 1.0 per
     *   phase (RULE 4): a 3-phase boss the player must "kill three times" carries 3.0.
     *   Because expectedPlayerSustainedDamagePerTurn rises with depth, the same target
     *   fight-length re-derives a larger HP pool deeper down — that is the build check
     *   (RULE 5): an under-powered player physically cannot out-DPS the fight window.
     *   Worked: expected DPT 49 * 24 target turns * 1.0 phase = 1176 eHP.
     * Edge cases:
     *   Any factor <= 0 -> returns 0 (a degenerate target produces no boss; the caller's
     *     BalanceReport would surface that as a bad config rather than a silent 1-HP boss).
     */
    public static float bossEffectiveHitPoints(float expectedPlayerSustainedDamagePerTurn,
                                               float targetFightTurns, float multiPhaseFactor) {
        if (expectedPlayerSustainedDamagePerTurn <= 0f || targetFightTurns <= 0f || multiPhaseFactor <= 0f) {
            return 0f;
        }
        return expectedPlayerSustainedDamagePerTurn * targetFightTurns * multiPhaseFactor;
    }

    /*
     * Formula: bossFightTurnsForPlayerDamagePerTurn — how long the fight ACTUALLY lasts (idea 6, RULE 2)
     * Derivation:
     *   The inverse of bossEffectiveHitPoints: given a boss's eHP and a particular
     *   player's sustained DPT, how many turns the fight takes (un-rounded, so the
     *   upper-cap check below is continuous):
     *       fightTurns = bossEffectiveHitPoints / playerSustainedDamagePerTurn
     *   Feed an ON-CURVE player's DPT to confirm the fight lands in the target band;
     *   feed a WELL-PLAYING (slightly higher) DPT to confirm the worst case stays under
     *   the upper cap (RULE 2: a boring long fight is as bad as a 2-shot). If only an
     *   over-cap fight hits the HP target, the boss is over-HP'd — add a PHASE or a
     *   damage-window mechanic instead of more HP (HP is the worst difficulty lever).
     *   Worked: 1176 eHP / 49 DPT = 24.0 turns (in the 18-40 act band).
     * Edge cases:
     *   playerSustainedDamagePerTurn <= 0 -> returns Float.POSITIVE_INFINITY (a player
     *     who cannot damage the boss never finishes; avoids divide-by-zero).
     */
    public static float bossFightTurnsForPlayerDamagePerTurn(float bossEffectiveHitPoints,
                                                             float playerSustainedDamagePerTurn) {
        if (playerSustainedDamagePerTurn <= 0f) {
            return Float.POSITIVE_INFINITY;
        }
        return bossEffectiveHitPoints / playerSustainedDamagePerTurn;
    }

    /*
     * Formula: bossUpperFightTurnsCap — the no-sponge ceiling (idea 6, RULE 2)
     * Derivation:
     *   A long fight is fine; a BORING long fight is not. The worst-case fight length
     *   for a player who plays well must stay under a fixed multiple of the target:
     *       upperFightTurnsCap = targetFightTurns * upperFightTurnsMultiplier
     *   with upperFightTurnsMultiplier = 1.5 (BalanceConfig.BOSS_UPPER_FIGHT_TURNS_MULTIPLIER).
     *   If the only way to hit the HP target is a fight that drags past this, add a phase
     *   or a damage window — not more HP.
     *   Worked: 24 target * 1.5 = 36 turns hard ceiling.
     * Edge cases:
     *   None — pure product; a non-positive target simply yields a non-positive cap,
     *     which the caller treats as "no valid boss".
     */
    public static float bossUpperFightTurnsCap(float targetFightTurns, float upperFightTurnsMultiplier) {
        return targetFightTurns * upperFightTurnsMultiplier;
    }

    /*
     * Formula: bossDamagePerTurnForSurvivalCheck — lethal-but-counterable output (idea 6, RULE 3)
     * Derivation:
     *   A boss must KILL a careless player but never one-shot a careful one. Express the
     *   boss's output as a SURVIVAL CHECK: an un-healing player who eats most attacks
     *   should die BEFORE the fight ends (so they must dodge / use cover / heal), but a
     *   player who avoids ~half the damage and uses heals survives with margin. The knob:
     *       playerEffectiveHitPoints / bossDamagePerTurn  ~=  survivalCheckRatio * fightTurns
     *   solved for the boss DPT to AIM for:
     *       bossDamagePerTurn = playerEffectiveHitPoints / (survivalCheckRatio * fightTurns)
     *   survivalCheckRatio in [0.4, 0.7] (BalanceConfig SECTION 14): at 0.5 a no-heal,
     *   no-dodge player dies at half the fight length, leaving the other half to be bought
     *   back by skill and the heal economy (idea 3).
     *   Worked: 205 eHP / (0.5 * 24 turns) = 205 / 12 = ~17 boss DPT.
     * Edge cases:
     *   survivalCheckRatio * fightTurns <= 0 -> returns 0 (no meaningful survival window;
     *     avoids divide-by-zero).
     */
    public static float bossDamagePerTurnForSurvivalCheck(float playerEffectiveHitPoints,
                                                          float fightTurns, float survivalCheckRatio) {
        float survivalWindowTurns = survivalCheckRatio * fightTurns;
        if (survivalWindowTurns <= 0f) {
            return 0f;
        }
        return playerEffectiveHitPoints / survivalWindowTurns;
    }

    /*
     * Formula: bossSurvivalCheckRatio — verify a boss's DPT is in the fair window (idea 6, RULE 3)
     * Derivation:
     *   The inverse of bossDamagePerTurnForSurvivalCheck: given a boss's DPT, what
     *   fraction of the fight a no-heal player survives —
     *       survivalCheckRatio = (playerEffectiveHitPoints / bossDamagePerTurn) / fightTurns
     *   Must land in [0.4, 0.7]. Below 0.4 the boss is a coin-flip (you die before you
     *   can react); above 0.7 it cannot threaten a careless player and the fight is a
     *   stat-check instead of a tactics-check.
     *   Worked: (205 / 17) / 24 = 12.06 / 24 = 0.50 -> in band.
     * Edge cases:
     *   bossDamagePerTurn <= 0 or fightTurns <= 0 -> returns Float.POSITIVE_INFINITY
     *     (a boss that deals no damage or a zero-length fight never kills; avoids
     *     divide-by-zero).
     */
    public static float bossSurvivalCheckRatio(float playerEffectiveHitPoints,
                                               float bossDamagePerTurn, float fightTurns) {
        if (bossDamagePerTurn <= 0f || fightTurns <= 0f) {
            return Float.POSITIVE_INFINITY;
        }
        return (playerEffectiveHitPoints / bossDamagePerTurn) / fightTurns;
    }

    /*
     * Formula: bossSingleHitFractionOfEffectiveHitPoints — the hard fairness cap (idea 6, RULE 3)
     * Derivation:
     *   Inherits idea 4's telegraph pillar. No SINGLE boss attack may exceed 35% of the
     *   player's eHP, and ANY attack above 25% must be telegraphed one full turn ahead
     *   with a readable, avoidable cue. This method expresses one attack as that fraction
     *   so the caller can check it against both caps:
     *       fraction = singleHitDamage / playerEffectiveHitPoints
     *     fraction >  BOSS_HARD_SINGLE_HIT_FRACTION (0.35)        -> BANNED outright.
     *     fraction >  TELEGRAPH_MAX_UNTELEGRAPHED_HIT_FRACTION (0.25) -> MUST be telegraphed.
     *   Unavoidable burst above the cap is banned: a death must read as "I mispositioned",
     *   never as a dice roll.
     *   Worked: a 52-damage Hell Baron cleave / 205 eHP = 0.254 -> over 25%, so it MUST be
     *     telegraphed (it is, via the BossAttackPattern wind-up), and is under 35% -> legal.
     * Edge cases:
     *   playerEffectiveHitPoints <= 0 -> returns Float.POSITIVE_INFINITY (any hit is fatal
     *     against a zero-eHP reference; avoids divide-by-zero and reads as "always over cap").
     */
    public static float bossSingleHitFractionOfEffectiveHitPoints(float singleHitDamage,
                                                                  float playerEffectiveHitPoints) {
        if (playerEffectiveHitPoints <= 0f) {
            return Float.POSITIVE_INFINITY;
        }
        return singleHitDamage / playerEffectiveHitPoints;
    }

    /*
     * Formula: bossPhaseHealthThreshold — the HP fraction where one phase hands off to the next (idea 6, RULE 4)
     * Derivation:
     *   A boss's eHP is split into N equal phases, each of which escalates ONE mechanic at
     *   an HP threshold (RULE 4: phase 1 teaches the pattern, the last phase tests it). For
     *   equal phases the boundary AFTER completing phaseIndex (1-based) of phaseCount is:
     *       threshold = 1 - phaseIndex / phaseCount
     *   so a 3-phase boss transitions at 0.66 and 0.33 (the third phase ends at 0.0 = death):
     *       phaseIndex 1 of 3 -> 0.6667   phaseIndex 2 of 3 -> 0.3333   phaseIndex 3 of 3 -> 0.0
     *   Phases are an AI-state concern (BossAttackPattern), balanced by the per-phase threat
     *   staying within the fairness cap above; this method only fixes WHERE the seams are.
     * Edge cases:
     *   phaseCount <= 0 -> returns 0 (no phases; the whole bar is one undivided fight).
     *   phaseIndex clamped to [0, phaseCount] so the result stays in [0, 1] (index 0 -> 1.0
     *     = full health start; index = phaseCount -> 0.0 = death).
     */
    public static float bossPhaseHealthThreshold(int phaseIndex, int phaseCount) {
        if (phaseCount <= 0) {
            return 0f;
        }
        int clampedIndex = Math.max(0, Math.min(phaseCount, phaseIndex));
        return 1f - (float) clampedIndex / (float) phaseCount;
    }

    /*
     * Formula: bossReward — reward priced by what the fight CONSUMES (idea 6, RULE 6)
     * Derivation:
     *   A boss's XP/credit/loot must be priced by the resources the fight burns (ammo and
     *   heals spent over the fight), plus a premium for the risk, so the boss roughly
     *   REFUNDS the fight plus a profit — never a net resource LOSS (which would punish the
     *   player for progressing) and never a jackpot that breaks the economy (idea 3):
     *       bossReward = (ammoSpent * ammoValue + healsSpent * healValue) * riskPremium
     *   ammoValue and healValue are the same scarcity units the economy is priced in
     *   (damage-per-ammo-unit, HP-per-heal); riskPremium (> 1) is the profit margin
     *   (BalanceConfig.BOSS_REWARD_RISK_PREMIUM). This closes the economy loop opened in
     *   idea 3; the actual numbers are re-derived once the scarcity model is fully tuned.
     *   Worked: (18 shells * 44 + 2 medkits * 50) * 1.3 = (792 + 100) * 1.3 = ~1160 reward units.
     * Edge cases:
     *   Any negative input is nonsensical config; not clamped here so BalanceReport surfaces
     *     a negative reward as a bad number rather than silently flooring it.
     *   riskPremium < 1 would make the boss a net resource LOSS — allowed by the math but a
     *     contract violation the caller is expected to catch (the premium must exceed 1).
     */
    public static float bossReward(float ammoSpent, float ammoValue,
                                   float healsSpent, float healValue, float riskPremium) {
        return (ammoSpent * ammoValue + healsSpent * healValue) * riskPremium;
    }

    // =========================================================================
    // RESOURCE SCARCITY MODEL — supply vs demand per floor (idea 3)
    // -------------------------------------------------------------------------
    // Scarcity is what converts a shooter into a roguelike: when ammo lands just
    // BELOW comfort, every fight becomes the recurring question "is this worth the
    // ammo?". The whole model rests on one ratio:
    //
    //     DEMAND(floor) = total damage needed to clear it  = sum of enemy eHP
    //     SUPPLY(floor) = total ranged damage the floor hands you
    //                   = sum over ammo pickups of (boxSize * damagePerAmmoUnit)
    //     S = SUPPLY / DEMAND          (the scarcity ratio)
    //
    // TARGET: S in [0.75, 0.95] floor-wide on a "fight everything" basis, and
    // < 0.6 for any SINGLE weapon (so no one gun's ammo clears the floor — the
    // player must diversify). The remaining 5-25% comes from melee (free but
    // risky), avoidance, or carried reserves. See docs/balance-rule-system.txt
    // and .claude/agents/ideas/balance_order_3_resource_scarcity_economy.txt.
    //
    // All methods below are pure and unit-free; BalanceReport feeds them the model
    // floor's numbers from BalanceConfig and tabulates the whole economy.
    // =========================================================================

    /*
     * Formula: expectedAmmoBoxesPerFloor — how many ammo pickups a floor hands you
     * Derivation:
     *   Two independent sources supply ammo over a floor:
     *     roomBoxes = roomCount * ammoChancePerRoom        (generator room rolls)
     *     killBoxes = enemyCount * ammoChancePerKill        (per-kill drops)
     *     expectedBoxes = roomBoxes + killBoxes
     *   This is the expectation (mean) over the floor's random rolls, so it is a
     *   real number, not an integer. It feeds the per-type SUPPLY below.
     *   Worked: 8 rooms * 0.20 + 12 enemies * 0.10 = 1.6 + 1.2 = 2.8 boxes.
     * Edge cases:
     *   Negative counts/chances are nonsensical inputs; the caller passes
     *   non-negative tuning numbers, so no clamping is applied (the result would
     *   simply be negative, which BalanceReport would surface as a bad config).
     */
    public static float expectedAmmoBoxesPerFloor(int roomCount, float ammoChancePerRoom,
                                                  int enemyCount, float ammoChancePerKill) {
        return roomCount * ammoChancePerRoom + enemyCount * ammoChancePerKill;
    }

    /*
     * Formula: ammoSupplyDamage — damage potential handed to the player for one ammo type
     * Derivation:
     *   Each ammo box of a type grants boxSize units; each unit buys
     *   damagePerAmmoUnit damage (= the representative weapon's per-shot damage,
     *   since one shot costs one unit). Over expectedBoxesOfType boxes:
     *       supply = expectedBoxesOfType * boxSize * damagePerAmmoUnit
     *   damagePerAmmoUnit is exactly GameMath.ammoEfficiency(damagePerShot, 1) for
     *   the weapon that consumes this ammo, so this is the "run-budget" bridge the
     *   ammoEfficiency comment refers to.
     *   Worked: 0.56 cell-boxes * 4 cells * 28 dmg = 62.7 damage of plasma supply.
     * Edge cases:
     *   Any factor <= 0 -> supply 0 (a type with no boxes / empty boxes / no damage
     *   value contributes nothing).
     */
    public static float ammoSupplyDamage(float expectedBoxesOfType, int boxSize, float damagePerAmmoUnit) {
        if (expectedBoxesOfType <= 0f || boxSize <= 0 || damagePerAmmoUnit <= 0f) {
            return 0f;
        }
        return expectedBoxesOfType * boxSize * damagePerAmmoUnit;
    }

    /*
     * Formula: scarcityRatio (S) — the central equation of the scarcity model
     * Derivation:
     *       S = totalRangedSupplyDamage / totalDemandDamage
     *   S >= 1.0  -> ammo is effectively infinite, no decisions (the bug idea 3 fixes).
     *   S in [0.75, 0.95] -> ammo covers most of the floor; the margin forces the
     *     "shoot or save?" question every fight.
     *   S < 0.6  -> forced melee too often; feels grindy/unfair.
     *   demandDamage is the sum of every enemy's eHP on the floor (PRIMITIVE 1).
     *   supplyDamage EXCLUDES melee (which is free but slow and dangerous) by design.
     * Edge cases:
     *   demandDamage <= 0 -> returns 0 (an empty floor has no scarcity to measure;
     *     avoids divide-by-zero).
     */
    public static float scarcityRatio(float totalRangedSupplyDamage, float totalDemandDamage) {
        if (totalDemandDamage <= 0f) {
            return 0f;
        }
        return totalRangedSupplyDamage / totalDemandDamage;
    }

    /*
     * Formula: reserveBankingFloors — how many floors of fights a full reserve banks
     * Derivation:
     *   A reserve cap of reserveCap units of a weapon, at damagePerAmmoUnit each,
     *   banks (reserveCap * damagePerAmmoUnit) damage. Divided by one floor's DEMAND
     *   that is how many floors a hoarder could pre-pay:
     *       bankingFloors = reserveCap * damagePerAmmoUnit / floorDemandDamage
     *   The anti-hoard target is ~1.5 floors (BalanceConfig.RESERVE_BANKING_FLOORS_TARGET):
     *   enough to carry a small buffer, not enough to trivialise scarcity by stockpiling.
     *   Worked: 12 shells * 44 dmg / 288 demand = 1.83 floors of banked shotgun fire.
     * Edge cases:
     *   floorDemandDamage <= 0 -> returns 0 (no floor to measure against).
     */
    public static float reserveBankingFloors(int reserveCap, float damagePerAmmoUnit, float floorDemandDamage) {
        if (floorDemandDamage <= 0f) {
            return 0f;
        }
        return reserveCap * damagePerAmmoUnit / floorDemandDamage;
    }

    // =========================================================================
    // HEAL ECONOMY — HP is a resource too (idea 3)
    // -------------------------------------------------------------------------
    // Each floor should cost some HP you don't fully recover, so HP slowly becomes
    // precious and you can't face-tank — but the run total stays survivable for a
    // skilled player. The lever is the gap between incoming damage and heal supply.
    // =========================================================================

    /*
     * Formula: incomingDamagePerFloor — HP a floor takes off the player
     * Derivation:
     *   If the player took every possible hit, each enemy would deal its DPT for
     *   the turns it stays engaged. Skilled play (positioning, avoidance, not
     *   waking every enemy) cancels a fraction of that:
     *       incoming = totalEnemyDamagePerTurn * expectedTurnsEngaged * (1 - avoidanceFactor)
     *   totalEnemyDamagePerTurn is the sum over enemies of (attackDamage / cadence).
     *   Worked: 94 DPT * 2 turns * (1 - 0.50) = 94 HP incoming on the model floor.
     * Edge cases:
     *   avoidanceFactor is clamped to [0, 1): a player cannot avoid more than all
     *     incoming damage, and avoidance >= 1 would make every floor free.
     *   expectedTurnsEngaged <= 0 -> incoming 0 (enemies never get to act).
     */
    public static float incomingDamagePerFloor(float totalEnemyDamagePerTurn,
                                               float expectedTurnsEngaged,
                                               float avoidanceFactor) {
        float clampedAvoidance = Math.max(0f, Math.min(0.99f, avoidanceFactor));
        float safeTurns = Math.max(0f, expectedTurnsEngaged);
        return totalEnemyDamagePerTurn * safeTurns * (1f - clampedAvoidance);
    }

    /*
     * Formula: healSupplyPerFloor — HP/armour the floor hands back
     * Derivation:
     *   Medkits and armour pickups both restore the survivability pool, so the
     *   floor's heal supply is the expected count of each times its average value:
     *       healSupply = expectedMedkits * averageMedkitHeal
     *                  + expectedArmourPickups * averageArmourValue
     *   Armour is counted because eHP folds armour into the same pool (PRIMITIVE 1).
     *   Worked: 1.5 * 34 + 1.0 * 21.5 = 51 + 21.5 = 72.5 HP-equivalent restored.
     * Edge cases:
     *   Negative inputs are nonsensical config; not clamped (BalanceReport would
     *   surface a negative supply as a bad number).
     */
    public static float healSupplyPerFloor(float expectedMedkits, float averageMedkitHeal,
                                           float expectedArmourPickups, float averageArmourValue) {
        return expectedMedkits * averageMedkitHeal + expectedArmourPickups * averageArmourValue;
    }

    /*
     * Formula: netHpDrainPerFloor — the HP a floor costs after heals
     * Derivation:
     *       netHpDrain = incomingDamagePerFloor - healSupplyPerFloor
     *   TARGET: > 0 on most floors (a small net loss so HP stays precious), but the
     *   cumulative drain across a run must stay under the player's eHP buffer plus
     *   level-up HP. Expressed as a fraction of reference eHP, the per-floor loss
     *   should sit in [5%, 15%] (BalanceConfig.HEAL_NET_DRAIN_FRACTION_*). A
     *   non-positive value means the player out-heals the floor and never feels HP
     *   pressure (the anti-face-tank failure mode).
     *   Worked: 94 incoming - 72.5 heal = 21.5 HP net loss (10.5% of 205 eHP).
     * Edge cases:
     *   None — pure subtraction; a negative result is meaningful (net HP GAIN).
     */
    public static float netHpDrainPerFloor(float incomingDamagePerFloor, float healSupplyPerFloor) {
        return incomingDamagePerFloor - healSupplyPerFloor;
    }

    /*
     * Formula: survivalTurnsBought — the price of a heal, in turns of survival
     * Derivation:
     *   A heal is only meaningfully priced relative to how fast the floor hurts you:
     *       survivalTurnsBought = healAmount / averageIncomingDamagePerTurn
     *   A heal that buys > 10 turns on an early floor trivialises scarcity (balance
     *   contract C). This makes "spend the medkit now or bank it for the bad room?"
     *   a real decision rather than a flat number.
     *   Worked: a 50 HP full medkit / 11.75 avg incoming DPT = 4.3 turns bought.
     * Edge cases:
     *   averageIncomingDamagePerTurn <= 0 -> returns Float.POSITIVE_INFINITY (with no
     *     incoming damage a heal buys unlimited survival; avoids divide-by-zero).
     */
    public static float survivalTurnsBought(float healAmount, float averageIncomingDamagePerTurn) {
        if (averageIncomingDamagePerTurn <= 0f) {
            return Float.POSITIVE_INFINITY;
        }
        return healAmount / averageIncomingDamagePerTurn;
    }

    // =========================================================================
    // MINI-MAP — FACING WEDGE GEOMETRY (replaces the unreadable facing line)
    // =========================================================================
    /*
     * Formula: facingWedgeTip — the pointed vertex of the direction wedge
     * Derivation:
     *   The wedge is an isosceles triangle built from the player's unit facing
     *   vector (directionX, directionY). Its tip sits one wedge-length ahead of
     *   the player dot along the facing vector:
     *       tip = center + facing * wedgeLength
     * Edge cases:
     *   facingX/facingY is always a unit vector supplied by Player, so no
     *   normalization is needed here.
     */
    public static float facingWedgeTipX(float centerX, float facingX, float wedgeLength) {
        return centerX + facingX * wedgeLength;
    }

    public static float facingWedgeTipY(float centerY, float facingY, float wedgeLength) {
        return centerY + facingY * wedgeLength;
    }

    /*
     * Formula: facingWedgeBase — the two base vertices of the direction wedge
     * Derivation:
     *   The base sits behind the player dot (opposite the facing vector) and is
     *   offset sideways along the perpendicular of facing, (-facingY, facingX)
     *   (90 degrees CCW of facing, same rotation used for strafe-left):
     *       baseLeft  = center - facing * wedgeBack + perpendicular * wedgeHalfWidth
     *       baseRight = center - facing * wedgeBack - perpendicular * wedgeHalfWidth
     *   Together with the tip this forms a triangle pointing exactly where the
     *   player is looking, readable at a glance on a small procedural mini-map.
     * Edge cases:
     *   None — pure vector arithmetic on an always-unit facing vector.
     */
    public static float facingWedgeBaseLeftX(float centerX, float facingX, float facingY,
                                              float wedgeBack, float wedgeHalfWidth) {
        return centerX - facingX * wedgeBack + (-facingY) * wedgeHalfWidth;
    }

    public static float facingWedgeBaseLeftY(float centerY, float facingX, float facingY,
                                              float wedgeBack, float wedgeHalfWidth) {
        return centerY - facingY * wedgeBack + facingX * wedgeHalfWidth;
    }

    public static float facingWedgeBaseRightX(float centerX, float facingX, float facingY,
                                               float wedgeBack, float wedgeHalfWidth) {
        return centerX - facingX * wedgeBack - (-facingY) * wedgeHalfWidth;
    }

    public static float facingWedgeBaseRightY(float centerY, float facingX, float facingY,
                                               float wedgeBack, float wedgeHalfWidth) {
        return centerY - facingY * wedgeBack - facingX * wedgeHalfWidth;
    }

    /*
     * Formula: shopEntryPrice — credit price of one shop offer (shop_order_2)
     * Derivation:
     *   depthFactor  = 1 + depthScale * (depth - 1)   // linear +depthScale per floor beyond the first
     *   price        = round( basePrice * depthFactor * rarityMultiplier )
     *   Base price is the offer variant's value floor; depthFactor keeps prices meaningful as kill
     *   bounties grow with depth; rarityMultiplier bumps fancier (rarer) offers.
     * Edge cases:
     *   - depth < 1 is clamped to 1 so a bad depth never yields a below-base price.
     *   - basePrice / multipliers are assumed >= 0; a 0 base yields 0 (a free offer).
     *   - Math.round returns a long; cast to int — prices are far within int range.
     */
    public static int shopEntryPrice(int basePrice, int depth, float depthScale, float rarityMultiplier) {
        int clampedDepth  = Math.max(1, depth);
        float depthFactor = 1f + depthScale * (clampedDepth - 1);
        return (int) Math.round(basePrice * depthFactor * rarityMultiplier);
    }

    /*
     * Formula: heartbeatPulse — a two-thump (lub-dub) cardiac rhythm envelope in [0, 1]
     * Derivation / explanation:
     *   The low-HP breathing vignette pulses like a heartbeat: a strong "lub" followed a
     *   short gap later by a weaker "dub", then a rest until the next period.
     *   phase = elapsedSeconds mod periodSeconds        // position within one heartbeat cycle
     *   Each thump is a raised half-sine bump of width thumpWidthSeconds:
     *     bump(offset) = (0 <= offset < width) ? sin(pi * offset / width) : 0
     *   The strong thump is centred at phase 0; the echo ("dub") begins echoGapSeconds later
     *   and is scaled by echoStrength (0..1). We take the max of the two so the envelope
     *   never sums past 1 where the tails of lub and dub overlap.
     * Edge cases:
     *   - periodSeconds <= 0 → return 0 (no rhythm; avoids modulo-by-zero).
     *   - thumpWidthSeconds <= 0 → thumps have no width → return 0.
     *   - echoStrength is not clamped here; callers pass 0..1.
     *   - Result is clamped to [0, 1] for safety against float error at the seams.
     */
    public static float heartbeatPulse(float elapsedSeconds, float periodSeconds,
                                        float thumpWidthSeconds, float echoGapSeconds,
                                        float echoStrength) {
        if (periodSeconds <= 0f || thumpWidthSeconds <= 0f) return 0f;
        float phase = elapsedSeconds - periodSeconds * (float) Math.floor(elapsedSeconds / periodSeconds);
        float strong = halfSineBump(phase, thumpWidthSeconds);
        float echo   = echoStrength * halfSineBump(phase - echoGapSeconds, thumpWidthSeconds);
        float value  = Math.max(strong, echo);
        return MathUtils.clamp(value, 0f, 1f);
    }

    /*
     * Formula: halfSineBump — a single smooth 0→1→0 bump of the given width
     * Derivation / explanation:
     *   For an offset in [0, width) the bump is sin(pi * offset / width), which rises from 0
     *   at offset 0 to 1 at the midpoint and back to 0 at width. Outside [0, width) it is 0.
     *   Used as the per-thump shape of heartbeatPulse.
     * Edge cases:
     *   - width <= 0 → return 0 (degenerate, avoids divide-by-zero).
     *   - offset < 0 or offset >= width → return 0 (the bump is inactive).
     */
    public static float halfSineBump(float offsetSeconds, float widthSeconds) {
        if (widthSeconds <= 0f) return 0f;
        if (offsetSeconds < 0f || offsetSeconds >= widthSeconds) return 0f;
        return (float) Math.sin(Math.PI * offsetSeconds / widthSeconds);
    }

    /*
     * Formula: bumpNudgeEnvelope — a lurch-and-spring-back displacement factor in [0, 1]
     * Derivation / explanation:
     *   When a move is blocked (a wall bump), the view lurches toward the blocked direction
     *   and springs back. progress runs 0→1 over the bump duration; the displacement follows
     *   sin(pi * progress): 0 at the start, peaks at progress 0.5 (fully lurched into the wall),
     *   and returns to 0 at progress 1 (settled). Multiply by the peak magnitude at the call site.
     * Edge cases:
     *   - progress is clamped to [0, 1] so a caller passing a slightly-over value still settles at 0.
     */
    public static float bumpNudgeEnvelope(float progress) {
        float clamped = MathUtils.clamp(progress, 0f, 1f);
        return (float) Math.sin(Math.PI * clamped);
    }

    /*
     * Formula: intentIconSize — distance-scaled, clamped edge length of an enemy intent icon
     * Derivation / explanation:
     *   Billboard sprites shrink as 1/depth. To keep the intent icon readable we scale the same
     *   way but relative to a reference distance: size = baseSize * (referenceDistance / depth),
     *   so at depth == referenceDistance the icon is exactly baseSize, closer enemies get a bigger
     *   icon and farther ones a smaller one. The result is clamped to [minSize, maxSize] so a
     *   point-blank enemy's icon can't swallow the screen and a distant sniper's committed lane
     *   stays legible (order-2 readability-at-distance requirement).
     * Edge cases:
     *   - depth <= 0 (enemy at/behind the camera) → return maxSize; such enemies are culled before
     *     rendering, so this is only a divide-by-zero guard.
     *   - minSize > maxSize (misconfigured constants) → clamp still returns a value in the intended
     *     range because MathUtils.clamp treats its second arg as the low bound.
     */
    public static float intentIconSize(float baseSize, float depth,
                                       float referenceDistance, float minSize, float maxSize) {
        if (depth <= 0f) return maxSize;
        float scaled = baseSize * (referenceDistance / depth);
        return MathUtils.clamp(scaled, minSize, maxSize);
    }

    /*
     * Formula: intentIconBobOffset — gentle idle vertical bob for a floating intent icon
     * Derivation / explanation:
     *   A sine wave in wall-clock time gives a smooth up/down float: offset = amplitude *
     *   sin(2*pi*hz*clock + phase). The per-enemy phase seed (in radians) desynchronises a room
     *   full of icons so they don't all bob in lockstep. Purely cosmetic; wall-clock time is
     *   correct here (not turn-based).
     * Edge cases:
     *   - amplitude <= 0 → returns 0 (bob disabled).
     */
    public static float intentIconBobOffset(float clockSeconds, float phaseRadians,
                                            float amplitude, float hz) {
        if (amplitude <= 0f) return 0f;
        return amplitude * (float) Math.sin(MathUtils.PI2 * hz * clockSeconds + phaseRadians);
    }

    /*
     * Formula: intentIconScale — combined pop + wind-up pulse scale multiplier for an intent icon
     * Derivation / explanation:
     *   Two independent cosmetic scale sources are combined multiplicatively onto a base 1.0:
     *     - re-commit "pop": as popStrength eases 1→0 the icon springs from (1 + popBonus) back to 1.
     *     - WIND_UP pulse:   telegraphStrength (0..1, already animated for the sprite flash) adds up
     *                        to pulseBonus so a charging attack visibly swells.
     *   scale = (1 + popStrength*popBonus) * (1 + telegraphStrength*pulseBonus). Multiplying keeps
     *   both effects visible when they overlap (a freshly-committed wind-up both pops and pulses).
     * Edge cases:
     *   - both strengths 0 → returns exactly 1.0 (no scaling), the common idle case.
     *   - inputs are used as-is; callers pass clamped [0,1] strengths.
     */
    public static float intentIconScale(float popStrength, float popBonus,
                                        float telegraphStrength, float pulseBonus) {
        float popFactor   = 1f + popStrength * popBonus;
        float pulseFactor = 1f + telegraphStrength * pulseBonus;
        return popFactor * pulseFactor;
    }

    /*
     * Formula: guardFacingMultiplier — directional damage multiplier for a guarding player
     *          (strategy-combat-order-4 player guard stance).
     * Derivation:
     *   facing = (facingX, facingY) is the player's unit facing vector (always cardinal after a
     *   rotation snaps, but any unit vector resolves cleanly). toAttacker = normalize(attackerX -
     *   playerX, attackerY - playerY) is the unit vector from the player toward the attacker.
     *     alignment = dot(facing, toAttacker) in [-1, 1]
     *   is the cosine of the angle between "where I face" and "where the hit comes from".
     *   Classify by arc using cosine thresholds (cos is monotonically DECREASING on [0°,180°], so a
     *   larger alignment means a smaller angle = more in front):
     *     FRONT if alignment >= cos(frontHalfAngle)  -> return frontMultiplier
     *     BACK  if alignment <= -cos(backHalfAngle)   -> return backMultiplier
     *     SIDE  otherwise                             -> return sideMultiplier
     *   With cardinal facing + a cardinal attacker lane, alignment lands on {1, 0, -1}, so the
     *   default ±60° front arc (cos 60° = 0.5) catches ONLY the exactly-faced direction (align 1)
     *   and treats both perpendicular lanes (align 0) as SIDE — exactly the four-way intent.
     * Edge cases:
     *   - Attacker on the player's own tile (degenerate zero-length toAttacker): treated as FRONT
     *     (point-blank — the marine is bracing straight at it), so it gets the front reduction.
     *   - facing is assumed unit length (Player invariant); a non-unit facing only scales alignment
     *     and never flips its sign, so the arc classification stays robust.
     *   - Half-angles are passed through MathUtils.cosDeg, which handles any degree input.
     */
    public static float guardFacingMultiplier(float facingX, float facingY,
                                              float playerX, float playerY,
                                              float attackerX, float attackerY,
                                              float frontMultiplier, float sideMultiplier,
                                              float backMultiplier,
                                              float frontHalfAngleDegrees, float backHalfAngleDegrees) {
        float toAttackerX = attackerX - playerX;
        float toAttackerY = attackerY - playerY;
        float distance    = (float) Math.sqrt(toAttackerX * toAttackerX + toAttackerY * toAttackerY);
        if (distance <= 1e-5f) {
            return frontMultiplier; // point-blank / same tile — brace straight at it
        }
        float alignment      = (facingX * toAttackerX + facingY * toAttackerY) / distance;
        float frontThreshold = MathUtils.cosDeg(frontHalfAngleDegrees);
        float backThreshold  = MathUtils.cosDeg(backHalfAngleDegrees);
        if (alignment >= frontThreshold)  return frontMultiplier;
        if (alignment <= -backThreshold)  return backMultiplier;
        return sideMultiplier;
    }

    // =========================================================================
    // ROUTE MAP — SPLITMIX64 PSEUDO-RANDOM FINALIZER
    // =========================================================================
    /*
     * Formula: splitMix64
     * Derivation / explanation:
     *   The standard SplitMix64 finalizer (Steele, Lea & Flood 2014). Given any
     *   64-bit state it returns a well-mixed 64-bit output where every input bit
     *   influences (on average) half the output bits:
     *
     *     z = state
     *     z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9
     *     z = (z ^ (z >>> 27)) * 0x94D049BB133111EB
     *     z =  z ^ (z >>> 31)
     *
     *   The route-map generator advances a state by the 64-bit golden-ratio
     *   increment (0x9E3779B97F4A7C15) between draws and passes each new state
     *   through this finalizer, giving an equidistributed, reproducible stream of
     *   random longs from a single seed.
     * Edge cases:
     *   state = 0 still produces a non-zero, well-mixed output (the added golden
     *   increment upstream guarantees the state is stepped before finalizing).
     *   Java long overflow wraps arithmetically, which is exactly what a hash wants.
     */
    public static long splitMix64(long state) {
        long mixed = state;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        mixed =  mixed ^ (mixed >>> 31);
        return mixed;
    }

    // =========================================================================
    // ROUTE MAP — SEED MIX (independent sub-stream derivation)
    // =========================================================================
    /*
     * Formula: mixSeed
     * Derivation / explanation:
     *   Folds a salt into a base seed to spawn an INDEPENDENT deterministic
     *   sub-stream — used by endless-mode region extension, which re-seeds each
     *   new region band from mix(runSeed, regionIndex) so band N is reproducible
     *   without pre-allocating an infinite graph:
     *
     *     mixSeed(seed, salt) = splitMix64(seed + salt * 0x9E3779B97F4A7C15)
     *
     *   Multiplying the salt by the golden-ratio constant before adding decorrelates
     *   adjacent salts (regionIndex 3 vs 4) so their streams share no visible pattern.
     * Edge cases:
     *   seed = 0 and salt = 0 still yields a mixed value via splitMix64. Overflow
     *   wraps arithmetically. Negative salt wraps correctly like any integer input.
     */
    public static long mixSeed(long seed, long salt) {
        return splitMix64(seed + salt * 0x9E3779B97F4A7C15L);
    }

    // =========================================================================
    // ROUTE MAP — WEIGHTED CHOICE INDEX
    // =========================================================================
    /*
     * Formula: weightedChoiceIndex
     * Derivation / explanation:
     *   Picks an index into a weight array with probability proportional to each
     *   weight, given a uniform roll in [0, 1):
     *
     *     total      = sum(weights)
     *     target     = roll01 * total
     *     cumulative = running sum; return the first index whose running sum > target
     *
     *   This is the inverse-CDF (roulette-wheel) sample. Feeding it a reproducible
     *   roll (from splitMix64) makes every node-type / generator pick deterministic.
     * Edge cases:
     *   total <= 0 (all weights zero or empty array) → returns 0 as a safe default
     *   (the caller is expected to guarantee a non-empty pool; falling back to the
     *   first index never throws and never loops).
     *   Floating-point rounding could leave target == total exactly at roll01≈1; the
     *   final "return last index" guard covers that so the method always returns a
     *   valid index.
     */
    public static int weightedChoiceIndex(float[] weights, float roll01) {
        if (weights.length == 0) {
            return 0;
        }
        float total = 0f;
        for (float weight : weights) {
            if (weight > 0f) {
                total += weight;
            }
        }
        if (total <= 0f) {
            return 0;
        }
        float target     = roll01 * total;
        float cumulative = 0f;
        for (int index = 0; index < weights.length; index++) {
            if (weights[index] > 0f) {
                cumulative += weights[index];
                if (target < cumulative) {
                    return index;
                }
            }
        }
        return weights.length - 1;
    }

    // =========================================================================
    // ROUTE MAP — PROJECTED LANE CENTRE (layer-to-layer lane mapping)
    // =========================================================================
    /*
     * Formula: projectedLaneCentre
     * Derivation / explanation:
     *   The route graph stacks layers of differing widths; to keep connector lines
     *   readable, a node in one layer wires to next-layer nodes near its own lateral
     *   position. This maps a source lane onto the target layer proportionally:
     *
     *     centre = round( fromLane * (toWidth - 1) / (fromWidth - 1) )   clamped [0, toWidth-1]
     *
     *   so lane 0 maps to lane 0, the last source lane maps to the last target lane,
     *   and interior lanes spread evenly. The generator connects a source to its
     *   projected centre and (rolled) the centre's +/-1 neighbours — the Slay-the-Spire
     *   "no edge crosses more than one lane" readability rule.
     * Edge cases:
     *   toWidth <= 1 (a boss/gate convergence layer) → returns 0, the lone target lane.
     *   fromWidth <= 1 (a single source spreading outward) → returns the middle target
     *   lane (toWidth-1)/2 as a neutral centre; the generator treats single sources as
     *   fully spreading, so this value is only a fallback.
     */
    public static int projectedLaneCentre(int fromLane, int fromWidth, int toWidth) {
        if (toWidth <= 1) {
            return 0;
        }
        if (fromWidth <= 1) {
            return (toWidth - 1) / 2;
        }
        int centre = Math.round(fromLane * (float) (toWidth - 1) / (float) (fromWidth - 1));
        if (centre < 0) {
            return 0;
        }
        if (centre > toWidth - 1) {
            return toWidth - 1;
        }
        return centre;
    }

    // =========================================================================
    // ROUTE MAP — LANE CONNECTABILITY (the +/-1 lane-crossing test)
    // =========================================================================
    /*
     * Formula: lanesConnectable
     * Derivation / explanation:
     *   Whether a source lane may legally wire to a given target lane under the
     *   "edges do not cross more than one lane" rule: the target must lie within
     *   +/-1 of the source's projected centre.
     *
     *     |toLane - projectedLaneCentre(fromLane, fromWidth, toWidth)| <= 1
     *
     *   Used both to place spread edges and to assert overlay readability in tests.
     * Edge cases:
     *   Convergence/divergence layers (width 1 on either side) are handled by the
     *   generator as full fan-in / fan-out and are intentionally exempt from this
     *   test; callers only apply it to parallel sections (both widths > 1).
     */
    public static boolean lanesConnectable(int fromLane, int fromWidth, int toLane, int toWidth) {
        int centre = projectedLaneCentre(fromLane, fromWidth, toWidth);
        return Math.abs(toLane - centre) <= 1;
    }
}
