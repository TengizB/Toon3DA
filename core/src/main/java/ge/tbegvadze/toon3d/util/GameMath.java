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
}
