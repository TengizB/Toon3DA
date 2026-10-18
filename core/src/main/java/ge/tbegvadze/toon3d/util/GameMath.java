package ge.tbegvadze.toon3d.util;

import com.badlogic.gdx.math.MathUtils;
import ge.tbegvadze.toon3d.entity.WeaponAbility;
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

    /*
     * Formula: easeTowardLinear — frame-rate-independent constant-rate approach to a target
     * Derivation:
     *   A cosmetic value that must take a FIXED wall-clock time to travel the full [0, 1] range,
     *   whatever the frame rate, moves at a constant rate of 1 / durationSeconds per second:
     *       step  = deltaTime / durationSeconds
     *       delta = end - current
     *       next  = current + clamp(step, 0, |delta|) * sign(delta)
     *   Clamping the step to the remaining distance is what guarantees the value never OVERSHOOTS on a
     *   long frame — the reason this is a constant-rate approach rather than the usual exponential
     *   lerp(current, end, deltaTime * k), which is frame-rate dependent and never actually arrives.
     *   Used for the Cinderforge Colossus's crust tint, where "0.4s to go from cooled to hot" is a
     *   stated design value and arriving exactly matters (the tint must settle, not asymptote).
     * Edge cases:
     *   durationSeconds <= 0 -> snaps straight to end (an instant ease is a snap, not a divide by zero).
     *   deltaTime <= 0 -> returns current unchanged (a paused frame moves nothing).
     *   current == end -> returns end exactly, so the value settles with no float residue.
     */
    public static float easeTowardLinear(float current, float end, float deltaTime,
                                         float durationSeconds) {
        if (durationSeconds <= 0f) return end;
        if (deltaTime <= 0f)       return current;
        float difference = end - current;
        if (difference == 0f)      return end;
        float step = deltaTime / durationSeconds;
        if (step >= Math.abs(difference)) return end;
        return current + (difference > 0f ? step : -step);
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
    // ORBITING SHARD RING (elemental-golem-auric-sentinel) — cosmetic billboard decoration.
    // The ring shows the enemy's live shard count, which is simultaneously its remaining armour and
    // its remaining ammunition, so these three formulas ARE the archetype's readability contract.
    // =========================================================================
    /*
     * Formula: Orbiting shard angle, with a smooth re-space after the ring loses a shard
     * Derivation:
     *   Shards sit at even angles around a common ring and sweep it at a constant rate:
     *       spacing = 2*PI / shardCount
     *       angle(i) = wallClockSeconds * orbitSpeed + i * spacing
     *   When a shard is destroyed the survivors must close the gap. Snapping straight to the new
     *   spacing pops; instead the SPACING itself is interpolated over the absorb-flash window:
     *       spacing = lerp(2*PI / previousCount, 2*PI / liveCount, respaceProgress)
     *   Interpolating the spacing (not the angle) keeps the ring evenly divided at every instant of
     *   the transition, so it reads as the survivors sliding apart rather than as a rotation glitch.
     *   Worked: 3 -> 2 shards sweeps the spacing 120deg -> 180deg across ~0.25 s.
     * Edge cases:
     *   liveCount <= 0 -> no shard is ever drawn; returns the base sweep angle (spacing term 0).
     *   previousCount <= 0 (or <= liveCount, i.e. a REGROWTH rather than a loss) falls back to the
     *     live spacing, so a new shard simply appears at its final slot.
     *   respaceProgress is clamped to [0, 1]; the caller normally passes 1 - flashStrength.
     */
    public static float shardOrbitAngleRadians(float wallClockSeconds, float orbitSpeedRadiansPerSecond,
                                               int shardIndex, int liveShardCount,
                                               int previousShardCount, float respaceProgress) {
        float sweepAngle = wallClockSeconds * orbitSpeedRadiansPerSecond;
        if (liveShardCount <= 0) return sweepAngle;
        float liveSpacing = MathUtils.PI2 / liveShardCount;
        float spacing     = liveSpacing;
        if (previousShardCount > liveShardCount) {
            float clampedProgress  = respaceProgress < 0f ? 0f : (respaceProgress > 1f ? 1f : respaceProgress);
            float previousSpacing  = MathUtils.PI2 / previousShardCount;
            spacing = lerp(previousSpacing, liveSpacing, clampedProgress);
        }
        return sweepAngle + shardIndex * spacing;
    }

    /*
     * Formula: Orbiting shard size scale (near/far perspective on a billboard ring)
     * Derivation:
     *   The ring is drawn on a flat billboard, so the only cue that it encircles the body rather than
     *   being a flat halo is that shards on the FAR side draw smaller. Treat the orbit as a circle seen
     *   edge-on: cos(angle) is the horizontal offset the renderer draws, so the orthogonal component
     *   sin(angle) is the toward/away axis — +1 nearest the viewer, -1 furthest.
     *   Map it to a scale whose maximum is exactly 1 (the nominal size) at the near point:
     *       nearness = (1 + sin(angle)) / 2        in [0, 1]
     *       scale    = 1 - farSideFalloff * (1 - nearness)
     *   so the far point draws (1 - farSideFalloff) x the near point and nothing is ever enlarged.
     *   Worked: falloff 0.35 -> near 1.00, side 0.825, far 0.65.
     * Edge cases:
     *   farSideFalloff is clamped to [0, 1), so the scale stays strictly positive; 0 disables the cue
     *   entirely (a flat halo) rather than producing a degenerate zero-size quad.
     */
    public static float shardOrbitSizeScale(float angleRadians, float farSideFalloff) {
        float clampedFalloff = farSideFalloff < 0f ? 0f : (farSideFalloff > 0.99f ? 0.99f : farSideFalloff);
        float nearness = (1f + MathUtils.sin(angleRadians)) / 2f;
        return 1f - clampedFalloff * (1f - nearness);
    }

    /*
     * Formula: Orbiting shard horizontal screen offset
     * Derivation:
     *   The visible (screen-X) component of a circular orbit seen edge-on is the cosine of the orbit
     *   angle scaled by the ring radius:
     *       offsetX = cos(angle) * orbitRadius
     *   The perpendicular component is NOT drawn as a Y offset — it is the depth axis, and it is what
     *   {@link #shardOrbitSizeScale} turns into a size cue. (The small vertical bob a shard also gets is
     *   a separate, independent oscillation via {@link #pickupBobOffset}.)
     * Edge cases:
     *   A non-positive radius returns 0, collapsing the ring onto the sprite's centre line.
     */
    public static float shardOrbitOffsetX(float angleRadians, float orbitRadius) {
        if (orbitRadius <= 0f) return 0f;
        return MathUtils.cos(angleRadians) * orbitRadius;
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
     * Formula: floorPixelOffsetBelowHorizon (float overload — runtime floor-backdrop resolution)
     * Derivation: identical to the int overload above, but drawY is a fractional full-screen pixel
     *   row. This exists because the render-resolution setting (util.RenderResolution) lets the floor
     *   backdrop's row count be a non-divisor of WORLD_HEIGHT (e.g. FULL_HD: 720 / 270 = 2.667), so the
     *   backdrop-row -> full-screen-row mapping (WORLD_HEIGHT / backdropHeight) is no longer always an
     *   integer; at HD (720 / 180 = 4.0 exactly) this returns the same value as the int overload.
     * Edge cases: drawY = screenHeight/2 -> offset = 0 -> division by zero in floorRowDistance; caller
     *   guarantees drawY stays strictly below the horizon.
     */
    public static float floorPixelOffsetBelowHorizon(float drawY, int screenHeight) {
        return screenHeight / 2f - drawY;
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
     * Formula: floorRowDistance (float overload — runtime floor-backdrop resolution)
     * Derivation: identical to the int overload above, taking the float pixelOffset produced by the
     *   float floorPixelOffsetBelowHorizon() overload (see its comment for why pixelOffset is a float
     *   at a non-HD render resolution).
     * Edge cases: pixelOffset = 0 -> +Infinity (guarded by caller; don't pass 0).
     */
    public static float floorRowDistance(float pixelOffset, int screenHeight) {
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

    /** Bit set by {@link #offscreenSideMask} when the source lies off the LEFT screen edge. */
    public static final int OFFSCREEN_SIDE_LEFT  = 1;
    /** Bit set by {@link #offscreenSideMask} when the source lies off the RIGHT screen edge. */
    public static final int OFFSCREEN_SIDE_RIGHT = 2;

    /*
     * Formula: offscreenSideMask
     * Derivation: project the source with spriteScreenColumnCenter on a unit-width screen, so the
     *   visible span is column in [0, 1). column < 0 means the source is past the LEFT edge
     *   (Y-up convention: small column = left); column >= 1 means past the RIGHT edge. A source at or
     *   behind the player (cameraSpaceDepth <= epsilon) has no meaningful column — the projection
     *   divides by ~0 or flips sign — so it is reported on BOTH sides: the player must turn, and
     *   either way round is a valid turn.
     *   cameraSpaceDepth is read through spriteDepth (same as the sprite renderers).
     * Edge cases: returns 0 when the source is in view. Result is a bit mask of OFFSCREEN_SIDE_*.
     */
    public static int offscreenSideMask(float differenceX, float differenceY,
                                        float directionX, float directionY,
                                        float planeX, float planeY, float behindEpsilon) {
        float depth = spriteDepth(differenceX, differenceY, directionX, directionY);
        if (depth <= behindEpsilon) {
            return OFFSCREEN_SIDE_LEFT | OFFSCREEN_SIDE_RIGHT;
        }
        float column = spriteScreenColumnCenter(differenceX, differenceY,
                directionX, directionY, planeX, planeY, 1);
        if (column < 0f) return OFFSCREEN_SIDE_LEFT;
        if (column >= 1f) return OFFSCREEN_SIDE_RIGHT;
        return 0;
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
    // PALETTE SYMBOL SEED — deterministic per-symbol seed for the tileset allocator
    // =========================================================================
    /*
     * Formula: paletteSymbolSeed
     * Derivation / explanation:
     *   The per-level SymbolAllocator (tileset order-4) rolls a sprite for each FLEXIBLE
     *   level-file symbol. Every symbol needs its OWN reproducible seed so its roll depends
     *   only on the floor's master seed and the symbol's char value — never on the ORDER the
     *   allocator happens to iterate symbols. Keying by the symbol (not by loop position) is
     *   what makes a shared seed look identical for everyone (permadeath fairness / seed
     *   sharing): the same levelSeed and the same placed rooms always yield a byte-identical
     *   LevelPalette.
     *
     *   Uses the SAME multiply-by-golden-ratio mixing style as routeNodeSeed / World.floorSeed
     *   (multiply by the 64-bit golden-ratio constant 0x9E3779B97F4A7C15, then fold in the next
     *   input). A salt (TilesetConstants.PALETTE_SEED_SALT) scaled by (symbol + 1) is XORed in up
     *   front so palette rolls form an INDEPENDENT stream from floor-content rolls
     *   (World.floorSeed) and route-node rolls (routeNodeSeed) at the same seed:
     *
     *     mixed = levelSeed ^ (PALETTE_SEED_SALT * (symbol + 1))
     *     mixed = mixed * 0x9E3779B97F4A7C15 + symbol
     *
     *   The result seeds a small advancing RNG (tileset PaletteRng), which finalises each draw
     *   through splitMix64 — the same "seed a cursor, splitMix64 each step" shape RouteRng uses.
     * Edge cases:
     *   symbol == 0 (the NUL sentinel the allocator uses for its per-level theme roll): the
     *     (symbol + 1) factor keeps the salt term non-zero, so a 0 char still produces a
     *     well-mixed, salt-dependent seed rather than collapsing to levelSeed.
     *   'char' in Java is UNSIGNED 16-bit (0..65535), so there are no "negative" chars; the
     *     (symbol + 1L) promotion to long also prevents the multiply from overflowing an int at
     *     the top of the char range.
     *   long overflow wraps arithmetically, exactly what a hash mixer wants.
     */
    public static long paletteSymbolSeed(long levelSeed, char symbol) {
        long mixed = levelSeed ^ (TilesetConstants.PALETTE_SEED_SALT * (symbol + 1L));
        mixed = mixed * 0x9E3779B97F4A7C15L + symbol;
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

    // =========================================================================
    // HEAVY DANGER TILES (balance-overhaul order 4, H3 / H4) — tile space, cardinal only
    // =========================================================================

    /** Headless "is this tile solid for the shape being built" query (no LibGDX, no level dependency). */
    public interface TileBlockedQuery {
        boolean isBlocked(int tileColumn, int tileRow);
    }

    /*
     * Formula: dangerTilesForLane
     * Derivation:
     *   For distance k = 1..maxTiles the lane tile is (originColumn + k*stepColumn, originRow + k*stepRow).
     *   Walking outward, the first blocked tile ends the lane BEFORE being listed (a marker never sits in a
     *   wall, and nothing is hit past a blocker). The origin (the caster) is never listed.
     * Edge cases:
     *   A zero step, maxTiles <= 0 or outCapacity <= 0 returns 0. A lane longer than outCapacity is
     *   truncated at outCapacity. Does not allocate.
     */
    public static int dangerTilesForLane(int originColumn, int originRow, int stepColumn, int stepRow,
                                         int maxTiles, TileBlockedQuery blocked,
                                         int[] outColumns, int[] outRows, int outCapacity) {
        if (stepColumn == 0 && stepRow == 0) return 0;
        int count = 0;
        for (int distance = 1; distance <= maxTiles && count < outCapacity; distance++) {
            int tileColumn = originColumn + stepColumn * distance;
            int tileRow    = originRow    + stepRow    * distance;
            if (blocked.isBlocked(tileColumn, tileRow)) break;
            outColumns[count] = tileColumn;
            outRows[count]    = tileRow;
            count++;
        }
        return count;
    }

    /*
     * Formula: dangerTilesForSquare
     * Derivation:
     *   A tile (c, r) is struck when chebyshevDistanceTiles(center, (c, r)) <= radius, i.e.
     *   |c - centerColumn| <= radius and |r - centerRow| <= radius. The centre is the caster and is
     *   excluded; blocked tiles are skipped (the player cannot stand there).
     * Edge cases:
     *   radius <= 0 returns 0. Truncates at outCapacity. Does not allocate.
     */
    public static int dangerTilesForSquare(int centerColumn, int centerRow, int radius,
                                           TileBlockedQuery blocked,
                                           int[] outColumns, int[] outRows, int outCapacity) {
        int count = 0;
        for (int tileRow = centerRow - radius; tileRow <= centerRow + radius; tileRow++) {
            for (int tileColumn = centerColumn - radius; tileColumn <= centerColumn + radius; tileColumn++) {
                if (tileColumn == centerColumn && tileRow == centerRow) continue;
                if (count >= outCapacity) return count;
                if (blocked.isBlocked(tileColumn, tileRow)) continue;
                outColumns[count] = tileColumn;
                outRows[count]    = tileRow;
                count++;
            }
        }
        return count;
    }

    /*
     * Formula: dangerTilesForCross
     * Derivation:
     *   The blast covers the centre tile plus, for each of the four cardinal steps, tiles 1..radius
     *   along that arm; an arm stops BEFORE its first blocked tile (the blast does not wrap corners).
     * Edge cases:
     *   The centre is always listed (count >= 1 when outCapacity >= 1) even if the query calls it blocked.
     *   radius <= 0 lists only the centre. Truncates at outCapacity. Does not allocate.
     */
    public static int dangerTilesForCross(int centerColumn, int centerRow, int radius,
                                          TileBlockedQuery blocked,
                                          int[] outColumns, int[] outRows, int outCapacity) {
        if (outCapacity <= 0) return 0;
        outColumns[0] = centerColumn;
        outRows[0]    = centerRow;
        int count = 1;
        for (int arm = 0; arm < 4; arm++) {
            int stepColumn = (arm == 0) ? 1 : (arm == 1) ? -1 : 0;
            int stepRow    = (arm == 2) ? 1 : (arm == 3) ? -1 : 0;
            for (int distance = 1; distance <= radius && count < outCapacity; distance++) {
                int tileColumn = centerColumn + stepColumn * distance;
                int tileRow    = centerRow    + stepRow    * distance;
                if (blocked.isBlocked(tileColumn, tileRow)) break;
                outColumns[count] = tileColumn;
                outRows[count]    = tileRow;
                count++;
            }
        }
        return count;
    }

    /*
     * Formula: everyDangerTileHasSafeNeighbour
     * Derivation:
     *   H4 (avoidable): from each listed tile one single step must reach a tile that is walkable
     *   (not blocked) and not itself dangerous. For each listed tile test its four cardinal neighbours
     *   against the blocked query and the list (linear scan, lists are <= a few dozen tiles).
     * Edge cases:
     *   An empty list is vacuously true. A tile whose four neighbours are all blocked or dangerous makes
     *   the whole result false (that player is cornered; the guard cap covers that case). Does not allocate.
     */
    public static boolean everyDangerTileHasSafeNeighbour(int[] columns, int[] rows, int count,
                                                          TileBlockedQuery blocked) {
        return everyDangerTileHasSafeNeighbour(columns, rows, count, 0, blocked);
    }

    /**
     * Same check, but only entries from {@code firstCheckedIndex} on need a sidestep; earlier entries
     * still count as dangerous for the neighbour test. Used for a CROSS, whose entry 0 is the caster's
     * own tile (the player can never stand there).
     */
    public static boolean everyDangerTileHasSafeNeighbour(int[] columns, int[] rows, int count,
                                                          int firstCheckedIndex, TileBlockedQuery blocked) {
        for (int index = firstCheckedIndex; index < count; index++) {
            boolean hasSafe = false;
            for (int arm = 0; arm < 4 && !hasSafe; arm++) {
                int neighbourColumn = columns[index] + ((arm == 0) ? 1 : (arm == 1) ? -1 : 0);
                int neighbourRow    = rows[index]    + ((arm == 2) ? 1 : (arm == 3) ? -1 : 0);
                if (blocked.isBlocked(neighbourColumn, neighbourRow)) continue;
                boolean dangerous = false;
                for (int other = 0; other < count && !dangerous; other++) {
                    dangerous = columns[other] == neighbourColumn && rows[other] == neighbourRow;
                }
                hasSafe = !dangerous;
            }
            if (!hasSafe) return false;
        }
        return true;
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
     * Formula: Armour Shred Cost (balance-overhaul order 4, H8)
     * Derivation:
     *   Armour soaks a share of every hit (see armorAbsorb). A HEAVY hit also SHREDS the plate: the pool
     *   loses more than the share it absorbed, while the HP the player takes is unchanged.
     *       shredCost = ceil(armorAbsorbed × shredMultiplier)
     *   capped by what is left:
     *       cost = min(currentArmor, shredCost)
     *   An ordinary hit passes shredMultiplier = 1.0, which gives cost = armorAbsorbed (the old rule).
     *   Example (armour 75, heavy of 60, fraction 0.67, shred 1.5): absorbed = 40, cost = 60.
     * Edge cases:
     *   armorAbsorbed <= 0 or currentArmor <= 0 -> 0.
     *   shredMultiplier < 1 is clamped to 1 (a hit never repairs, or under-charges, the plate).
     *   The result never exceeds currentArmor, so the pool never goes negative.
     */
    public static int armorShredCost(int armorAbsorbed, int currentArmor, float shredMultiplier) {
        if (armorAbsorbed <= 0 || currentArmor <= 0) return 0;
        float clampedMultiplier = Math.max(1f, shredMultiplier);
        int shredCost = (int) Math.ceil(armorAbsorbed * clampedMultiplier);
        return Math.min(currentArmor, shredCost);
    }

    /*
     * Formula: Armour Status-Shield Cost (balance-overhaul order 4, H9)
     * Derivation:
     *   While armour holds, an enemy status application (WEAK / SLOWED / BLINDED / POISONED) is blocked
     *   and costs a fraction of the MAXIMUM pool, so the price scales with the player's armour build:
     *       cost = max(1, round(maxArmor × costFraction))
     *   capped by what is left (the pool never goes below 0):
     *       cost = min(currentArmor, cost)
     *   Example (max 75, fraction 0.10): round(7.5) = 8 per blocked application.
     * Edge cases:
     *   currentArmor <= 0 -> 0 (and the caller applies the status — a shield with nothing left is down).
     *   maxArmor <= 0 -> the minimum of 1 (still capped by currentArmor).
     */
    public static int armorStatusShieldCost(int currentArmor, int maxArmor, float costFraction) {
        if (currentArmor <= 0) return 0;
        int cost = Math.max(1, Math.round(maxArmor * costFraction));
        return Math.min(currentArmor, cost);
    }

    /*
     * Formula: Status Tick Damage (balance-overhaul order 4, H10)
     * Derivation:
     *   Every status DoT is a FRACTION of the host's max HP so it never goes stale with depth:
     *       perStack = max(1, round(fraction x hostMaxHealth))
     *       damage   = perStack x stacks
     *   The per-stack value is what the StatusEffect stores at application time; the tick multiplies by the
     *   live stack count. Example: fraction 0.015, max HP 100 -> perStack round(1.5) = 2; 3 stacks -> 6.
     *   Burn passes stacks = 1 (fraction 0.03).
     * Edge cases:
     *   hostMaxHealth <= 0 or fraction <= 0 -> perStack floors at 1 (a status always hurts a little).
     *   stacks <= 0 -> 0 (nothing applied). Math.round is half-up, so 1.5 -> 2 (equal fractions can differ by
     *   up to 0.5 HP of rounding between depths, which is the "up to rounding" of acceptance A5).
     */
    public static int statusTickDamage(float fraction, int hostMaxHealth, int stacks) {
        if (stacks <= 0) return 0;
        int perStack = Math.max(1, Math.round(fraction * Math.max(0, hostMaxHealth)));
        return perStack * stacks;
    }

    /*
     * Formula: Toxic Average Stacks (balance-overhaul order 4, H10 — hazard pricing read-out)
     * Derivation:
     *   A host standing in a toxic tile gains stacksPerTurn each turn (capped at maxStacks); the status
     *   tick then deals damage on the CURRENT stack count and afterwards the stacks decay by 1:
     *       stacksBeforeTick(turn n) = min(maxStacks, carried(n) + stacksPerTurn)
     *       carried(n + 1)           = stacksBeforeTick(n) - 1
     *   The average damage-bearing stack count over turnsStood turns is the mean of stacksBeforeTick.
     *   Worked (2 per turn, cap 6, 2 turns): turn 1 -> 2, carry 1; turn 2 -> 3. Mean = 2.5.
     * Edge cases:
     *   turnsStood <= 0 or stacksPerTurn <= 0 -> 0. maxStacks < 1 is treated as 1.
     */
    public static float toxicAverageStacks(int turnsStood, int stacksPerTurn, int maxStacks) {
        if (turnsStood <= 0 || stacksPerTurn <= 0) return 0f;
        int cap = Math.max(1, maxStacks);
        int carried = 0;
        float total = 0f;
        for (int turn = 0; turn < turnsStood; turn++) {
            int stacksBeforeTick = Math.min(cap, carried + stacksPerTurn);
            total += stacksBeforeTick;
            carried = stacksBeforeTick - 1;
        }
        return total / turnsStood;
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

    /*
     * Formula: xpRequiredForLevelGeometric — the honest, depth-coupled XP curve (order 4)
     * Derivation:
     *   The polynomial curve (base * level^exponent) grows POLYNOMIALLY while enemy threat — and
     *   therefore the XP a floor's roster is worth — grows GEOMETRICALLY (depthThreatScale). Those two
     *   shapes cannot stay coupled, so the per-floor XP yield drifts and leveling stops being paced
     *   (new-game-balancr order 4, problem 2). A GEOMETRIC requirement fixes this:
     *       xpRequired(level) = base * growthPerLevel ^ (level - 1)
     *   Choose growthPerLevel to track the enemy threat compound (ENEMY_HEALTH_SCALE * ENEMY_DAMAGE_
     *   SCALE per floor). Then, with per-enemy XP DERIVED from the enemy's depth-scaled Threat Points
     *   (xpRewardAtDepth below), a floor's total roster XP and the level requirement grow at the SAME
     *   rate, so the yield (available XP / required XP) is depth-STABLE — R-XP-PACE holds at every depth.
     *   Worked: base 150, growth 1.118 -> level 1->2 needs 150, level 10->11 needs 150*1.118^9 = 409.
     * Edge cases:
     *   currentLevel < 1 is clamped to 1 (no negative/zero requirements).
     *   growthPerLevel <= 0 is nonsensical; not clamped so a bad config surfaces as a bad number.
     */
    public static int xpRequiredForLevelGeometric(int base, float growthPerLevel, int currentLevel) {
        int safeLevel = Math.max(1, currentLevel);
        return (int) (base * Math.pow(growthPerLevel, safeLevel - 1));
    }

    /*
     * Formula: expectedLevelAtDepth — the player level the pacing model expects at a floor (order 4)
     * Derivation:
     *   The player starts at level 1 on depth 1 and is expected to gain levelsPerDepth levels per floor
     *   (BalanceConfig.EXPECTED_LEVELS_PER_DEPTH, the depth-coupling input). So:
     *       expectedLevel(d) = round(1 + levelsPerDepth * (depth - 1))
     *   At the default 1.0 level/floor this is simply depth. It is the reference the XP-pacing rule
     *   prices against (xpRequired at THIS level) and the anchor the catch-up rubber band compares the
     *   real player level to.
     * Edge cases:
     *   depth <= 1 -> returns 1 (the run begins at level 1). Rounded so the expectation is a whole level.
     */
    public static int expectedLevelAtDepth(float levelsPerDepth, int depth) {
        int floorsDescended = Math.max(0, depth - 1);
        return Math.max(1, Math.round(1f + levelsPerDepth * floorsDescended));
    }

    /*
     * Formula: catchUpScaledXp — the forward-only XP rubber band (order 4)
     * Derivation:
     *   A player who falls behind the expected level curve would spiral (weaker -> slower kills ->
     *   even less XP). The catch-up band multiplies incoming XP while the player is more than one level
     *   BELOW the expected level for their depth, and does nothing otherwise:
     *       underLevelled = playerLevel < expectedLevel - 1
     *       scaledXp      = underLevelled ? round(baseXp * catchUpMultiplier) : baseXp
     *   It is FORWARD-ONLY: it never slows an at-or-ahead player (being ahead is earned), it only helps
     *   a behind one recover. The "-1" band gives one level of slack so normal variance never trips it.
     * Edge cases:
     *   baseXp <= 0 -> returned unchanged (no XP to scale).
     *   catchUpMultiplier < 1 would PUNISH being behind; callers pass >= 1 (not clamped so a bad config
     *     surfaces rather than being silently corrected).
     */
    public static int catchUpScaledXp(int baseXp, int playerLevel, int expectedLevel, float catchUpMultiplier) {
        if (baseXp <= 0) {
            return baseXp;
        }
        boolean underLevelled = playerLevel < expectedLevel - 1;
        return underLevelled ? Math.round(baseXp * catchUpMultiplier) : baseXp;
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
     * Formula: respannedLegacyWeaponLevel — the old 1..10 scaling curves stretched over 1..MAX_WEAPON_LEVEL (R6)
     * Derivation:
     *   The accuracy / reload / clip / range / ability curves were authored for levels 1..10. To keep
     *   their start value (level 1) and end value (old level 10) while the ladder now runs to
     *   MAX_WEAPON_LEVEL, level L maps to the old curve position
     *       position = 1 + (L - 1) * (LEGACY_MAX - 1) / (MAX_WEAPON_LEVEL - 1)
     *   with LEGACY_MAX = 10. L = 1 -> 1.0, L = MAX -> 10.0, linear between (continuous, unrounded).
     *   Worked: MAX = 27, L = 14 -> 1 + 13 * 9 / 26 = 5.5.
     * Edge cases:
     *   level is clamped to [1, MAX_WEAPON_LEVEL]; MAX_WEAPON_LEVEL <= 1 returns 1.0 (no span to map).
     */
    public static float respannedLegacyWeaponLevel(int weaponLevel) {
        int maximumLevel = WeaponConstants.MAX_WEAPON_LEVEL;
        if (maximumLevel <= 1) {
            return 1f;
        }
        int clampedLevel = Math.max(1, Math.min(weaponLevel, maximumLevel));
        return 1f + (clampedLevel - 1) * (WeaponConstants.LEGACY_WEAPON_LEVEL_SPAN - 1f) / (maximumLevel - 1f);
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
        float legacyPosition = respannedLegacyWeaponLevel(weaponLevel);
        float scaled = baseAccuracy + WeaponConstants.WEAPON_LEVEL_ACCURACY_PER_LEVEL * (legacyPosition - 1f);
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
        float legacyPosition = respannedLegacyWeaponLevel(weaponLevel);
        float scaled = baseReloadTicks - WeaponConstants.WEAPON_LEVEL_RELOAD_STEP * (legacyPosition - 1f);
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
     *   clip-1 weapons (Shotgun, Railgun) remain at 1 up to MAX_WEAPON_LEVEL,
     *   since 8% × 9 levels ≈ 72% bonus, which floors to 0 (< 100% needed for +1).
     */
    public static int weaponScaledClipSize(int baseClipSize, int weaponLevel) {
        if (baseClipSize == 0) return 0;
        float legacyPosition = respannedLegacyWeaponLevel(weaponLevel);
        int bonus = (int)(WeaponConstants.WEAPON_LEVEL_CLIP_PER_LEVEL * (legacyPosition - 1f) * baseClipSize);
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
        float legacyPosition = respannedLegacyWeaponLevel(weaponLevel);
        int bonus = Math.min(
                (int)((legacyPosition - 1f) * WeaponConstants.WEAPON_LEVEL_RANGE_PER_2_LEVELS / 2f),
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
        float legacyPosition = respannedLegacyWeaponLevel(weaponLevel);
        float scaled = baseMagnitude + perLevel * (legacyPosition - 1f);
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
        float legacyPosition = respannedLegacyWeaponLevel(weaponLevel);
        int safeStep = Math.max(1, levelsPerStep);
        int scaled = baseCount + (int) ((legacyPosition - 1f) / safeStep);
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
     *   shared 5-step mitigation pipeline documented in docs/game-balance-authority.txt:
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
     *   BALANCE CONTRACT in the idea doc and the BLOCK section of docs/game-balance-authority.txt.
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
    // The whole balance contract (docs/game-balance-authority.txt) rests on four
    // primitive quantities — eHP, DPT, TTK, TP — plus a handful of derived
    // scores. The game is strictly turn-based (one action = one world turn), so
    // TIME is measured in TURNS, never seconds: there is no real-time DPS, only
    // damage-per-turn. That makes every number below exact arithmetic.
    //
    // The reference anchors these formulas compare against (REFERENCE_PLAYER_DPT,
    // REFERENCE_PLAYER_EHP) and the per-role TP
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
     *   Worked: canonical start (130 HP, 75 armour, 0 dodge, 0 reduction) -> 205 eHP.
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
     * Formula: enemyEffectiveHitPoints (enemy eHP) — the SAME survivability primitive, both sides (order 5)
     * Derivation:
     *   Before order 5 enemy eHP was hard-coded as raw HP everywhere (TP, TTK, golden-ratio call sites),
     *   which silently ASSUMED no enemy ever mitigates. The moment a single armoured/dodging archetype
     *   ships that assumption breaks. The mitigation PIPELINE is already shared player<->enemy (Block, flat
     *   armour), so the survivability MATH is unified here too: an enemy's eHP runs through the exact same
     *   GameMath.effectiveHitPoints the player uses —
     *       enemyEffectiveHitPoints = effectiveHitPoints(rawHitPoints, armorPool, dodgeChance,
     *                                                     flatReduction, averageIncomingHit)
     *   No non-boss archetype supplies non-zero armour/dodge/reduction YET (Iron Stalker is the designated
     *   first user in a later content pass — flat reduction to punish chip damage and reward heavy weapons),
     *   so today this returns rawHitPoints unchanged (armorPool=dodge=flatReduction=0 -> pool*1*1). The point
     *   is that the model STOPS ASSUMING: the first mitigating enemy re-prices its own TP/TTK for free.
     *   averageIncomingHit is the reference hit the flat-reduction term is priced against — the player's
     *   sustained reference DPT (BalanceConfig.REFERENCE_PLAYER_DPT) is the natural yardstick, matching the
     *   TP formula's survivalTurns normaliser.
     * Edge cases:
     *   Inherits effectiveHitPoints' clamps (dodge < 1, hit - reduction floored at 1, hit <= 0 neutralises
     *   the reduction term). With all-zero mitigation the result is exactly rawHitPoints + armorPool.
     */
    public static float enemyEffectiveHitPoints(float rawHitPoints, float armorPool,
                                                float dodgeChance, float flatReduction,
                                                float averageIncomingHit) {
        return effectiveHitPoints(rawHitPoints, armorPool, dodgeChance, flatReduction, averageIncomingHit);
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
     * Formula: turnsToKillBreakpointGain — how many whole turns a damage increase SHAVES off a kill (order 4)
     * Derivation:
     *   Turn-based combat is felt in INTEGER turns: a card that takes a fight from 4 shots to 3 changes
     *   the next fight, while a raw +8% DPT that leaves it at 4 shots does not. The breakpoint gain is
     *   the drop in turnsToKill against a fixed target when the player's damage-per-turn rises:
     *       gain = turnsToKill(target, dptBefore) - turnsToKill(target, dptAfter)
     *   A gain >= 1 means the upgrade CROSSES a kill breakpoint versus that target. Order 4 prices the
     *   level-up card pool so most cards cross at least one breakpoint against the region's soldier at
     *   the depth they are likely picked — that is what makes a level-up FEEL different in the next
     *   fight, not just on the character sheet. The symmetric survivability breakpoint (a defensive
     *   card taking the player from dying in 3 enemy hits to 4) is the same primitive with the player's
     *   eHP as the target and the enemy's DPT held fixed, evaluated at the before/after eHP.
     *   Worked: soldier 118 eHP, dpt 25 -> 40 : ceil(118/25)=5 - ceil(118/40)=3 = 2 breakpoints.
     * Edge cases:
     *   dptAfter <= dptBefore -> gain <= 0 (no breakpoint from a non-increase; a negative value is a
     *     meaningful regression that the caller can surface).
     *   Either dpt <= 0 -> turnsToKill returns MAX_VALUE for that side; the caller compares finite
     *     realistic DPTs, so this only guards degenerate input.
     */
    public static int turnsToKillBreakpointGain(float targetEffectiveHitPoints,
                                                float damagePerTurnBefore, float damagePerTurnAfter) {
        int turnsBefore = turnsToKill(targetEffectiveHitPoints, damagePerTurnBefore);
        int turnsAfter  = turnsToKill(targetEffectiveHitPoints, damagePerTurnAfter);
        return turnsBefore - turnsAfter;
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
     * Formula: cycleAveragedDamagePerTurn — TRUE effective DPT over a special-cadence cycle (order 5)
     * Derivation:
     *   The plain threatPoints DPT is attackDamage/cadence — BASIC ATTACKS ONLY. Casters, buffers and
     *   summoners raise their EFFECTIVE output far beyond their stat block (an EMPOWERED buff, an area
     *   slam, a debuff that steals the player's turns, whole extra summoned bodies), and none of it was
     *   priced. Cycle-averaging fixes that: over one special-cadence cycle of {@code cycleTurns} enemy
     *   turns the archetype spends ONE turn on a special (which DISPLACES a basic attack that turn) and
     *   the remaining {@code cycleTurns - 1} turns attacking normally, so —
     *       basicTurns          = cycleTurns - 1
     *       cycleAveragedDpt    = (basicDamagePerTurn * basicTurns + perCastSpecialDamage) / cycleTurns
     *                             + summonDamagePerTurn
     *   {@code perCastSpecialDamage} is the equivalent DAMAGE of the special that fires this cycle
     *   (AREA_STRIKE / BUFF_SELF / DEBUFF, averaged over a rotating move-set — one fires per cycle),
     *   priced by the specialEquivalent* verbs below. {@code summonDamagePerTurn} is added SEPARATELY (it
     *   is already a per-turn figure, see specialEquivalentSummon) because a summon adds whole extra
     *   BODIES, not caster output — folding it through the cycle average would double-amortise it.
     *   An archetype with no move-set (cycleTurns <= 0) returns basicDamagePerTurn + summonDamagePerTurn
     *   unchanged — the plain stat-block DPT, so non-scripted enemies are untouched.
     *   Worked: Iron Stalker basic 24, per-cast mean 30.6 (buff 28.8 / area 32.4), cycleTurns 2, no summon
     *           -> (24*1 + 30.6)/2 = 27.3 effective DPT (vs 24 raw).
     * Edge cases:
     *   cycleTurns <= 0 -> no special cadence -> returns basicDamagePerTurn + summonDamagePerTurn.
     *   cycleTurns == 1 (a special every turn) -> basicTurns 0 -> pure specials, no displaced basic.
     *   Negative per-cast damage is impossible by construction (every verb returns a non-negative number).
     */
    public static float cycleAveragedDamagePerTurn(float basicDamagePerTurn, float perCastSpecialDamage,
                                                   float summonDamagePerTurn, int cycleTurns) {
        if (cycleTurns <= 0) {
            return basicDamagePerTurn + summonDamagePerTurn;
        }
        int basicTurns = Math.max(0, cycleTurns - 1);
        return (basicDamagePerTurn * basicTurns + perCastSpecialDamage) / cycleTurns + summonDamagePerTurn;
    }

    /*
     * Formula: heavyDamageAtDepth — the damage of a HEAVY (committed-then-resolved) attack (balance-overhaul order 4, H2)
     * Derivation:
     *   A heavy is sized against the player who is SUPPOSED to be there, not against a stat block:
     *       damage = max(1, round(damageFraction * expectedMaxHealth * scale))
     *   where expectedMaxHealth is GameMath.expectedPlayerAtDepth(d).maxHealth (the on-curve player at the
     *   enemy's effective depth) and scale is the ordinary HEAVY_DAMAGE_SCALE feel knob. At scale 1 the
     *   unguarded, unarmoured hit is exactly damageFraction of an on-curve player's health at every depth,
     *   so the heavy never goes stale as the ladder climbs.
     *   Worked: fraction 0.50, expected max HP 130, scale 1 -> round(65) = 65.
     * Edge cases:
     *   Non-positive fraction, health or scale floors at 1 (a heavy always does something).
     */
    public static int heavyDamageAtDepth(float damageFraction, float expectedMaxHealth, float scale) {
        return Math.max(1, Math.round(damageFraction * expectedMaxHealth * scale));
    }

    /*
     * Formula: heavyExpectedDamageForPricing — what a heavy is worth in Threat Points (balance-overhaul order 4)
     * Derivation:
     *   A heavy is telegraphed and avoidable, so a competent player eats only a share of it. The price is
     *       priced = heavyDamage * (1 - clamp01(expectedAvoidance))
     *   (HEAVY_EXPECTED_AVOIDANCE 0.6 -> 40% of the hit), which stops heavy-bearing archetypes flooding the
     *   encounter budget. Replaces the old specialEquivalentAreaStrike (full predicted hit).
     *   Worked: 58 damage, avoidance 0.6 -> 23.2.
     * Edge cases:
     *   Avoidance is clamped to [0, 1]: 0 prices the full hit, 1 prices nothing. Negative damage is not expected.
     */
    public static float heavyExpectedDamageForPricing(float heavyDamage, float expectedAvoidance) {
        float clampedAvoidance = Math.max(0f, Math.min(1f, expectedAvoidance));
        return heavyDamage * (1f - clampedAvoidance);
    }

    /*
     * Formula: specialEquivalentBuffSelf — extra damage an EMPOWERED self-buff adds over its life (order 5)
     * Derivation:
     *   BUFF_SELF grants the caster EMPOWERED: +empoweredPercent% outgoing damage for durationTurns turns.
     *   Its equivalent value is the EXTRA damage that buff produces over its duration, on top of the
     *   caster's normal output —
     *       equivalent = basicDamagePerTurn * (empoweredPercent / 100) * durationTurns
     *   i.e. a fraction of the caster's own per-turn damage, sustained across the buff. This prices the
     *   "kill the buffer before its empowered swing lands" priority-target threat the stat block ignores.
     *   Worked: basic 24, +40% for 3 turns -> 24 * 0.40 * 3 = 28.8 equivalent damage per cast.
     * Edge cases:
     *   durationTurns <= 0 or empoweredPercent <= 0 -> 0 (a no-op buff is worth nothing).
     */
    public static float specialEquivalentBuffSelf(float basicDamagePerTurn, float empoweredPercent,
                                                  int durationTurns) {
        return basicDamagePerTurn * (empoweredPercent / 100f) * Math.max(0, durationTurns);
    }

    /*
     * Formula: specialEquivalentDebuffWeak — a WEAK debuff priced as offence (order 5)
     * Derivation:
     *   WEAK cuts the PLAYER's outgoing damage by weakPercent% for durationTurns turns. A defensive verb,
     *   but the encounter budget spends offence, so we price the player OUTPUT it steals —
     *       playerDamageLost = referencePlayerDamagePerTurn * (weakPercent / 100) * durationTurns
     *       equivalent       = playerDamageLost * debuffEquivalence
     *   {@code debuffEquivalence} (< 1) discounts it because a turn of your LOST output is worth less than
     *   a turn of the enemy's DEALT damage: you can rotate, retreat or heal around a debuff, so it does not
     *   convert one-for-one into the enemy's threat.
     *   Worked: refDPT 25, -25% for 2 turns, equivalence 0.6 -> 25 * 0.25 * 2 * 0.6 = 7.5 equivalent damage.
     * Edge cases:
     *   durationTurns <= 0 or weakPercent <= 0 -> 0. referencePlayerDamagePerTurn <= 0 -> 0 (no yardstick).
     */
    public static float specialEquivalentDebuffWeak(float referencePlayerDamagePerTurn, float weakPercent,
                                                    int durationTurns, float debuffEquivalence) {
        if (referencePlayerDamagePerTurn <= 0f) {
            return 0f;
        }
        float playerDamageLost = referencePlayerDamagePerTurn * (weakPercent / 100f) * Math.max(0, durationTurns);
        return playerDamageLost * debuffEquivalence;
    }

    /*
     * Formula: specialEquivalentDebuffControl — SLOW / BLIND priced as bonus enemy turns (order 5)
     * Derivation:
     *   SLOW and BLIND deny the player turns rather than cutting a percentage of damage: a slow makes the
     *   player's actions take longer (the pack gets extra turns to act), a blind wastes the player's aim.
     *   Both are priced as BONUS ENEMY TURNS gained, valued at the caster's own per-turn output and
     *   discounted by the same defensive-verb equivalence as WEAK —
     *       equivalent = bonusEnemyTurns * basicDamagePerTurn * debuffEquivalence
     *   The caller supplies bonusEnemyTurns per verb (priced conservatively, band-checked):
     *       SLOW  -> (slowFactor - 1) * slowDurationTurns   (a 2x slow over 3 turns ~= 3 bonus turns)
     *       BLIND -> blindDurationTurns                     (each blinded turn ~= one wasted player turn)
     *   Using the CASTER's DPT as the "pack average DPT share" is the conservative single-enemy stand-in
     *   the design doc calls for (no whole-pack lookup inside the TP primitive).
     * Edge cases:
     *   bonusEnemyTurns <= 0 -> 0. Negative is impossible for the supported verbs.
     */
    public static float specialEquivalentDebuffControl(float basicDamagePerTurn, float bonusEnemyTurns,
                                                       float debuffEquivalence) {
        return basicDamagePerTurn * Math.max(0f, bonusEnemyTurns) * debuffEquivalence;
    }

    /*
     * Formula: specialEquivalentSummon — amortised per-turn threat of summoned bodies (order 5)
     * Derivation:
     *   A summon adds whole EXTRA enemies, each worth summonedThreatPoints of danger the encounter budget
     *   never paid for. Amortising that added threat across the caster's own life turns it into a per-turn
     *   figure that folds into the caster's cycle-averaged DPT (so a single archetype-level TP number still
     *   captures the flood) —
     *       summonDamagePerTurn = summonedThreatPoints * expectedSummonsPerFight / survivalTurns
     *   because TP = cycleAveragedDpt * survivalTurns * positional, adding this per-turn term contributes
     *   summonedThreatPoints * expectedSummonsPerFight back into the caster's TP (the survivalTurns cancel),
     *   i.e. the summoner's TP rises by the FULL threat of the bodies it adds. expectedSummonsPerFight is
     *   priced conservatively below the max batch (one cast per summoner lifetime, blocked tiles and the
     *   per-room live cap eat part of the batch) — the "bounded by the existing hard caps" clause.
     *   Worked: summoned chaff TP 17.5, expected 1.5 bodies, caster survives 3.6 turns
     *           -> 17.5 * 1.5 / 3.6 = 7.29 added effective DPT.
     * Edge cases:
     *   survivalTurns <= 0 -> 0 (a caster that dies instantly summons nothing worth pricing; avoids
     *     divide-by-zero). expectedSummonsPerFight <= 0 or summonedThreatPoints <= 0 -> 0.
     */
    public static float specialEquivalentSummon(float summonedThreatPoints, float expectedSummonsPerFight,
                                                float survivalTurns) {
        if (survivalTurns <= 0f) {
            return 0f;
        }
        return summonedThreatPoints * expectedSummonsPerFight / survivalTurns;
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
     * Formula: abilityPowerPoints — price a weapon ABILITY in the build currency (order 2; re-cut by balance-overhaul order 5)
     * Derivation:
     *   Rarity buys ABILITIES, not raw damage (weapon POWER bands are role properties, SECTION 9).
     *   To make tier an ENFORCEABLE band, every ability is priced in the SAME power points (PP =
     *   %-of-reference DPT/eHP) the perks use (damagePerTurnPowerPoints / effectiveHitPointPowerPoints
     *   are the primitives). An ability's value is its live magnitude converted to a %-of-reference,
     *   DISCOUNTED by a utilisation weight for how often that value actually applies. The weights are
     *   designer DATA in BalanceConfig SECTION 15, so a tuned magnitude re-prices for free:
     *       damage multiplier   -> magnitude * 100 * util              (fraction of reference DPT gained)
     *       damage-over-time    -> (magnitude / refDPT) * 100 * util   (sustained added DPT as %)
     *       on-kill AoE burst   -> (magnitude / refDPT) * 100 * util
     *       armour pierce       -> magnitude * 100 * util + extraTargets * PP_PER_COUNT  (merged pierce)
     *       burst fire          -> (rounds - 1) * PP_PER_EXTRA_BURST_ROUND
     *       ammo refund         -> chance * rounds * PP_PER_EXPECTED_REFUND_ROUND
     *       legendary signature -> a fixed marquee PP
     *   balance-overhaul order 5 (B5/B6): the tier BAND (TIER_ABILITY_PP_BUDGET_* +/- tolerance, SECTION 24)
     *   bounds the SUM of a roll from BELOW and ABOVE; ABILITY_MIN_FELT_PP bounds each ability from below at
     *   its expected weapon level. The sub-threshold economy/sustain abilities that used the old flat
     *   "utility nominal" price were CUT, so every surviving ability is priced from its own magnitude.
     * Edge cases:
     *   Every WeaponAbility is classified — an unclassified constant returns NaN rather than a silent
     *   nominal price, and BalanceSchema R-ABILITY reports it UNPRICED (and asserts every one is felt).
     *   Magnitudes and counts are >= 0 by construction (count clamped), so PP >= 0.
     */
    public static float abilityPowerPoints(WeaponAbility ability, float magnitude, int countValue) {
        switch (ability) {
            // Always-on / on-every-hit damage multipliers (fraction of reference DPT gained).
            case CRITICAL_STRIKE:
            case RHYTHM:
                return magnitude * 100f * BalanceConfig.ABILITY_UTIL_ALWAYS_ON;
            // Conditional damage bonuses (low-HP target, point-blank range, first shot, splash).
            case EXECUTIONER:
            case POINT_BLANK:
            case OPENING_SALVO:
            case CLEAVE:
                return magnitude * 100f * BalanceConfig.ABILITY_UTIL_CONDITIONAL;
            // Chance-gated crowd control (defensive value).
            case STAGGER_ROUNDS:
            case KINETIC_SLAM:
                return magnitude * 100f * BalanceConfig.ABILITY_UTIL_CROWD_CONTROL;
            // Block-bypass plus the merged over-penetration (extra enemies pierced per shot).
            case ARMOR_PIERCE:
                return magnitude * 100f * BalanceConfig.ABILITY_UTIL_PENETRATION
                        + Math.max(0, countValue) * BalanceConfig.ABILITY_PP_PER_COUNT;
            // Damage-over-time: sustained added DPT as a fraction of the reference DPT.
            case REND:
            case INCENDIARY:
                return (magnitude / BalanceConfig.REFERENCE_PLAYER_DPT) * 100f
                        * BalanceConfig.ABILITY_UTIL_DAMAGE_OVER_TIME;
            // Lifesteal sustain, expressed as a fraction of damage dealt returned as HP.
            case LIFESTEAL:
                return magnitude * 100f * BalanceConfig.ABILITY_UTIL_SUSTAIN_FRACTION;
            // Flat AoE burst on kill — value as a fraction of reference DPT.
            case STATIC_DISCHARGE:
                return (magnitude / BalanceConfig.REFERENCE_PLAYER_DPT) * 100f
                        * BalanceConfig.ABILITY_UTIL_FLAT_EVENT;
            // Extra rounds per attack action — each one is a tempo gain (fewer enemy turns per kill).
            case BURST_FIRE:
                return Math.max(0, countValue - 1) * BalanceConfig.ABILITY_PP_PER_EXTRA_BURST_ROUND;
            // On-kill ammo refund — expected rounds returned per kill.
            case SCAVENGER_ROUNDS:
                return magnitude * Math.max(0, countValue) * BalanceConfig.ABILITY_PP_PER_EXPECTED_REFUND_ROUND;
            // Legendary signatures — fixed marquee price.
            case SOULFORGE:
            case JUDGMENT:
            case HELLFIRE_NOVA:
            case BERSERKERS_OATH:
                return BalanceConfig.ABILITY_PP_LEGENDARY_SIGNATURE;
            default:
                // Unclassified: NaN, which R-ABILITY reports as UNPRICED (a red audit row, not a crash).
                return Float.NaN;
        }
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
     * Formula: regionScaledFloorThreatPointBudget — the REGION DANGER DIAL (order 5, Pillar C.3)
     * Derivation:
     *   Macro pacing (route regions) and micro difficulty (the TP budget) used to be unrelated: a "lethal"
     *   region was lethal only by how OFTEN it rolled combat nodes — danger by vibe, not a budgeted number.
     *   The region danger dial unifies them by scaling the depth-ramped floor budget by a per-region
     *   multiplier applied ON TOP of the depth curve —
     *       regionBudget = floorThreatPointBudget(base, hpScale, dmgScale, depth)
     *                      * perRegionMultiplierAtDepth(regionTpMultipliers, depth, regionBandSize)
     *   so a deeper, more dangerous region spends MORE Threat Points at the same depth (more / stronger
     *   bodies), while the per-ENEMY fairness (golden ratio, depth-coupling) is untouched — region danger
     *   is an attrition tax of extra bodies, not an unfair per-duel spike. This is the number the
     *   depth-coupling audit reads per region lane, so a lethal region is EXPLICITLY lethal and budgeted.
     *   Node weight multipliers still own FLAVOUR (what kind of rooms); this dial owns HOW HARD.
     * Edge cases:
     *   Inherits floorThreatPointBudget / perRegionMultiplierAtDepth edge cases. A regions array of all
     *   1.0 (or depths inside region A at multiplier 1.0) reproduces the plain floorThreatPointBudget.
     *   Depths past the last region entry clamp to it (endless "The Breach" keeps its multiplier forever).
     */
    public static float regionScaledFloorThreatPointBudget(float baseThreatPointBudget,
                                                           float healthScalePerDepth,
                                                           float damageScalePerDepth,
                                                           int depth, float[] regionTpMultipliers,
                                                           int regionBandSize) {
        float depthBudget = floorThreatPointBudget(baseThreatPointBudget, healthScalePerDepth,
                damageScalePerDepth, depth);
        return depthBudget * perRegionMultiplierAtDepth(regionTpMultipliers, depth, regionBandSize);
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
     * Formula: xpRewardAtDepth — the DERIVED per-kill XP, scaled to the enemy's depth (order 4)
     * Derivation:
     *   Per-enemy XP is no longer hand-set (thirteen forgotten constants); it is DERIVED from the one
     *   thing that already measures how dangerous an enemy is — its Threat Points — times a single knob:
     *       xpReward(depth) = round( xpPerThreatPoint * enemyThreatAtDepth(baseThreatPoints, d) )
     *   Because enemyThreatAtDepth scales the base TP by depthThreatScale, a deeper (stronger) instance
     *   of the same archetype automatically pays more XP, and the whole floor's roster XP grows at the
     *   SAME geometric rate as the level requirement (xpRequiredForLevelGeometric) — the coupling that
     *   makes R-XP-PACE hold at every depth. Dangerous enemies pay more; new archetypes can never ship
     *   with a forgotten XP value.
     * Edge cases:
     *   depth <= 1 -> depthThreatScale 1.0 -> the depth-1 base reward.
     *   xpPerThreatPoint or baseThreatPoints <= 0 -> a 0 reward (surfaces bad config rather than clamping).
     */
    public static int xpRewardAtDepth(float xpPerThreatPoint, float baseThreatPoints,
                                      float healthScalePerDepth, float damageScalePerDepth, int depth) {
        float scaledThreat = enemyThreatAtDepth(baseThreatPoints, healthScalePerDepth, damageScalePerDepth, depth);
        return Math.round(xpPerThreatPoint * scaledThreat);
    }

    /*
     * Formula: compoundDepthMultiplier — one compound per-depth growth factor (order 2 helper)
     * Derivation:
     *   Enemy HP and enemy damage each compound per floor by their own scale (SECTION 3). The growth
     *   factor from depth 1 to depth d for a single axis is:
     *       compoundDepthMultiplier = perDepthScale ^ (depth - 1)
     *   depthThreatScale is the PRODUCT of two of these (health axis * damage axis); this exposes one
     *   axis on its own so the gear-gate audit can scale a reference enemy's eHP and DPT independently
     *   to a region-entry depth (its eHP grows on the health axis, its hit grows on the damage axis).
     * Edge cases:
     *   depth <= 1 -> exponent 0 -> returns 1.0 (depth 1 is the un-scaled base).
     */
    public static float compoundDepthMultiplier(float perDepthScale, int depth) {
        int floorsDescended = Math.max(0, depth - 1);
        return (float) Math.pow(perDepthScale, floorsDescended);
    }

    /*
     * Formula: distanceAttenuatedShake — how hard a distant impact shakes the screen
     * Derivation:
     *   A heavy footfall should be FELT near the player and merely sensed far away, so its magnitude
     *   falls off linearly with tile distance and reaches exactly zero at a cutoff:
     *       attenuation = 1 - distanceTiles / maxDistanceTiles     (clamped to [0, 1])
     *       shake       = baseMagnitude * attenuation
     *   Linear rather than inverse-square on purpose: an inverse-square falloff collapses to almost
     *   nothing within two tiles, which would erase the "something huge is walking around out of view"
     *   read this exists to deliver. The hard cutoff at maxDistanceTiles keeps a far-off enemy from
     *   contributing a permanent low-grade jitter.
     * Edge cases:
     *   maxDistanceTiles <= 0 -> returns 0 (a cutoff of zero means nothing is ever felt).
     *   distanceTiles <= 0 -> full baseMagnitude (an impact on the player's own tile).
     *   distanceTiles >= maxDistanceTiles -> exactly 0, never negative.
     */
    public static float distanceAttenuatedShake(float baseMagnitude, int distanceTiles,
                                                int maxDistanceTiles) {
        if (maxDistanceTiles <= 0)               return 0f;
        if (distanceTiles >= maxDistanceTiles)   return 0f;
        if (distanceTiles <= 0)                  return baseMagnitude;
        return baseMagnitude * (1f - (float) distanceTiles / maxDistanceTiles);
    }

    /*
     * Formula: hazardTileThreatPoints — fold terrain danger into the TP contract (idea 4, Pillar 3; order 4 H13)
     * Derivation:
     *   A hazard tile (fire / toxic) has no health, so the enemy Threat-Point primitive's
     *   "survivalTurns" term (eHP / refDPT) does not apply. Instead a static hazard threatens
     *   the player for as many turns as a careless player stays hurt by it:
     *       hazardDamagePerTurn   = max(1, round(damageFraction x referenceMaxHealth))   (statusTickDamage)
     *       hazardTileThreatPoints = hazardDamagePerTurn * carelessTurns * positionalMultiplier
     *   This is the SAME shape as threatPoints (damagePerTurn * danger-window * positional), so a hazard
     *   tile is directly comparable to an enemy's TP. Since order 4 the damage is a FRACTION of the
     *   reference max HP (the on-curve player at depth 1 for the player side), and the careless window is the
     *   hazard's own burn window (BalanceConfig.HAZARD_FIRE_BURN_TURNS: how long standing in it once keeps
     *   hurting), replacing the flat HAZARD_THREAT_TURNS_STOOD constant.
     * Edge cases:
     *   carelessTurns <= 0 -> returns 0 (a hazard nobody ever stands in threatens nothing).
     *   hazardDamagePerTurn <= 0 -> returns 0 (no damage, no threat).
     */
    public static float hazardTileThreatPoints(float hazardDamagePerTurn, int carelessTurns,
                                               float positionalMultiplier) {
        if (carelessTurns <= 0 || hazardDamagePerTurn <= 0f) {
            return 0f;
        }
        return hazardDamagePerTurn * carelessTurns * positionalMultiplier;
    }

    /*
     * Formula: barrelBlastDamage — a barrel's blast as a FRACTION of a reference max HP (balance-overhaul order 4, H13)
     * Derivation:
     *   damage = max(1, round(fraction x referenceMaxHealth))
     *   Enemy side: referenceMaxHealth = the reference SOLDIER's max HP at the floor's depth
     *   (enemyHealthAtDepth(HAZARD_BARREL_REFERENCE_SOLDIER_BASE_HEALTH, depth)), fraction = centre 1.2 / ring 0.6.
     *   Player side: referenceMaxHealth = the player's own max HP, fraction = centre 0.30 / ring 0.15.
     *   Example (depth 10, soldier 161 HP): ring 0.6 -> round(96.6) = 97; centre 1.2 -> round(193.2) = 193.
     *   Because the reference grows with the enemy HP ladder, a barrel kills the same chaff at every depth.
     * Edge cases:
     *   referenceMaxHealth <= 0 or fraction <= 0 -> 1 (an explosion always hurts a little).
     *   Math.round is half-up; the caller passes the already-rounded integer HP the enemy actually has.
     */
    public static int barrelBlastDamage(float fraction, float referenceMaxHealth) {
        return Math.max(1, Math.round(fraction * referenceMaxHealth));
    }

    /*
     * Formula: relicBlastDamage — a relic blast tile's damage as a FRACTION of the reference soldier's HP
     * Derivation:
     *   damage = max(1, round(fraction x referenceHealth))
     *   referenceHealth = the reference SOLDIER's max HP at the floor's depth (enemyHealthAtDepth), so 1.0 unit
     *   is the same yardstick a barrel uses. The door blast's centre takes fraction x 1.0 and each of the four
     *   neighbours fraction x RELIC_BLAST_RING_SHARE; the corpse burst puts fraction x 1.0 on each neighbour.
     *   Example (depth 10, soldier 161 HP): fraction 0.30 -> round(48.3) = 48.
     * Edge cases:
     *   referenceHealth <= 0 or fraction <= 0 -> 1; callers skip a tile whose fraction is 0 ("nothing there")
     *   before asking. Math.round is half-up.
     */
    public static int relicBlastDamage(float fraction, float referenceHealth) {
        return Math.max(1, Math.round(fraction * referenceHealth));
    }

    /*
     * Formula: groupBarrelRoomCount — how many group rooms must hold barrels (balance-overhaul order 4, H14)
     * Derivation:
     *   needed = ceil(share x eligibleRooms)   (share = HAZARD_GROUP_ROOM_SHARE, 0.40)
     *   "At least 40% of the rooms holding a group" rounds UP, so a floor with one eligible room still gets
     *   a barrel room (ceil(0.4) = 1) and five eligible rooms need two (ceil(2.0) = 2). A float product such
     *   as 0.4f x 5 = 2.0000000298 would round to 3, so a tiny epsilon is subtracted before the ceiling:
     *       needed = ceil(share x eligibleRooms - 1e-4)
     * Edge cases:
     *   eligibleRooms <= 0 or share <= 0 -> 0. The result never exceeds eligibleRooms (share > 1 is clamped).
     */
    public static int groupBarrelRoomCount(int eligibleRooms, float share) {
        if (eligibleRooms <= 0 || share <= 0f) {
            return 0;
        }
        int needed = (int) Math.ceil((double) share * eligibleRooms - GROUP_BARREL_ROOM_COUNT_EPSILON);
        return Math.max(0, Math.min(eligibleRooms, needed));
    }

    /** Guards {@link #groupBarrelRoomCount} against float products that land a hair above a whole number. */
    private static final double GROUP_BARREL_ROOM_COUNT_EPSILON = 1e-4;

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
    // in BalanceConfig SECTION 14; see docs/game-balance-authority.txt (Boss appendix).
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
    // risky), avoidance, or carried reserves. See docs/game-balance-authority.txt
    // and .claude/agents/ideas/balance_order_3_resource_scarcity_economy.txt.
    //
    // All methods below are pure and unit-free; BalanceReport feeds them the model
    // floor's numbers from BalanceConfig and tabulates the whole economy.
    // =========================================================================

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
     * Formula: normalisedAmmoShare — A-1 per-type generosity on the planned ammo split
     * Derivation:
     *   The SupplyPlanner splits a floor's planned ammo DAMAGE over the ammo types with base shares s_t
     *   (SUPPLY_CARRIED_SHARE over the carried types, SUPPLY_OFF_TYPE_SHARE over the rest; sum s_t = 1).
     *   A-1 weights each by its generosity g_t, then re-normalises so the shares still sum to 1:
     *       W = sum_t s_t x g_t
     *       share_t = s_t x g_t / W
     *   so sum_t share_t = 1 and the floor's TOTAL planned ammo damage is unchanged — only the mix moves
     *   (equal weights reproduce the base split exactly).
     *   Worked (carried = BULLETS only): bullets 0.70 x 0.8 = 0.56, four off types 0.075 x {1.3, 1.0,
     *   1.0, 0.9} = 0.315, W = 0.875 -> bullets 0.64, shells 0.111, cells 0.086, rockets 0.086, slugs 0.077.
     * Edge cases:
     *   W <= 0 (every weight or share zero) -> the base share is returned unchanged; a negative weight
     *   is treated as 0 (that type gets no ammo).
     */
    public static float normalisedAmmoShare(float baseShare, float generosity, float weightedShareSum) {
        if (weightedShareSum <= 0f) {
            return baseShare;
        }
        return baseShare * Math.max(0f, generosity) / weightedShareSum;
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
    // RESOURCE ECONOMY ACROSS DEPTH (new-game-balancr order 3)
    // -------------------------------------------------------------------------
    // Order 3 audits the ammo economy, the heal economy, and the credit sink at
    // EVERY depth 1..15, not just one depth-1 model floor. The depth-1 model floor
    // (SECTION 10) stays the box-size calibration anchor; these methods carry that
    // anchor down the run using the two curves the run actually rides:
    //   - DEMAND rides the enemy eHP curve   (compoundDepthMultiplier(healthScale, d))
    //   - SUPPLY rides the EXPECTED-ARSENAL curve (gearCurveAtDepth) — ammo must be
    //     priced against the guns the player is EXPECTED to hold at d (order 2), or
    //     upgrades would silently break the economy.
    // See docs/game-balance-authority.txt and
    // .claude/agents/ideas/new-game-balancr-order-3.txt.
    // =========================================================================

    /*
     * Formula: regionIndexAtDepth — which 5-floor region a depth belongs to
     * Derivation:
     *   The gear curve, the loot-tier bands and the per-region economy multipliers all step once per
     *   region of regionBandSize depths (the SAME band the route map uses). The 0-based region index is:
     *       regionIndexAtDepth = floor((depth - 1) / regionBandSize)
     *   depths 1..5 -> region 0, 6..10 -> region 1, and so on. This is the shared index every
     *   per-region lever (REGION_TP_BUDGET_MULTIPLIER, tier bands)
     *   reads, so no two of them can drift apart.
     * Edge cases:
     *   regionBandSize <= 0 -> treated as 1 (every depth is its own region) to avoid divide-by-zero.
     *   depth <= 1 -> region 0 (the run begins in the first region).
     */
    public static int regionIndexAtDepth(int depth, int regionBandSize) {
        int safeBandSize = Math.max(1, regionBandSize);
        return Math.max(0, depth - 1) / safeBandSize;
    }

    /*
     * Formula: perRegionMultiplierAtDepth — read a per-region lever, clamping past the last band
     * Derivation:
     *   The economy levers are small arrays indexed by region (one entry per designed region). Endless
     *   mode descends past the last designed region, so a depth beyond the table clamps to its LAST
     *   entry (the deepest designed tuning holds for every deeper region):
     *       index = min(regionIndexAtDepth(depth), multipliers.length - 1)
     *       result = multipliers[index]
     * Edge cases:
     *   Empty/null array -> returns 1.0 (a no-op multiplier, so a missing table never zeroes supply).
     */
    public static float perRegionMultiplierAtDepth(float[] multipliers, int depth, int regionBandSize) {
        if (multipliers == null || multipliers.length == 0) {
            return 1f;
        }
        int index = Math.min(regionIndexAtDepth(depth, regionBandSize), multipliers.length - 1);
        return multipliers[index];
    }

    /*
     * Formula: floorDemandAtDepth — the damage needed to clear a floor at depth d
     * Derivation:
     *   The depth-1 model floor has a fixed DEMAND (sum of its enemies' eHP). Deeper floors keep a
     *   scale-invariant enemy COUNT (the encounter budget and each enemy's TP cost scale by the same
     *   curve — SECTION 11), so the whole floor's eHP grows by the single enemy-eHP axis:
     *       floorDemandAtDepth = modelFloorDemand * compoundDepthMultiplier(healthScale, d)
     *   This is the deterministic "one source" DEMAND the order-3 audit uses instead of a hand-listed
     *   per-depth roster (the encounter planner spends this same budget in play).
     * Edge cases:
     *   depth <= 1 -> multiplier 1.0 -> returns modelFloorDemand unchanged.
     */
    public static float floorDemandAtDepth(float modelFloorDemand, float healthScalePerDepth, int depth) {
        return modelFloorDemand * compoundDepthMultiplier(healthScalePerDepth, depth);
    }

    /*
     * Formula: scarcityRatioAtDepth — the scarcity ratio S at an arbitrary depth (order 3)
     * Derivation:
     *   The central scarcity equation carried down the run:
     *       S(d) = ammoSupplyAtDepth(d) / floorDemandAtDepth(d)
     *   The order-3 rule R-SCARCITY-DEPTH requires S(d) in [0.75, 0.95] for every d in 1..15, so ammo
     *   covers most-but-not-all of every floor at every depth — the "shoot or save?" question never
     *   evaporates as the arsenal and the enemies co-escalate.
     * Edge cases:
     *   floorDemand <= 0 -> returns 0 (delegated to scarcityRatio; an empty floor has no scarcity).
     */
    public static float scarcityRatioAtDepth(float ammoSupplyAtDepth, float floorDemandAtDepth) {
        return scarcityRatio(ammoSupplyAtDepth, floorDemandAtDepth);
    }

    /*
     * Formula: netHpDrainFractionAtDepth — per-floor net HP loss as a fraction of eHP at depth d
     * Derivation:
     *   Each floor should cost a small slice of HP you never recover (order 3, part B). On the power
     *   ladder (balance-overhaul order 1) the three terms ride three FITTED curves:
     *       incoming(d)   = incomingBase   * ENEMY_DAMAGE_GROWTH^(d-1)
     *       eHP(d)        = expectedPlayerAtDepth(d).effectiveHitPoints
     *       healSupply(d) = healSupplyBase * eHP(d) / eHP(1) * healRegionMultiplier(d)
     *                       (heals are FRACTIONS of max HP/armour — R10 — and max includes the flat
     *                        card bonuses, so the supply rides the player's whole pool)
     *       netDrainFraction(d) = (incoming(d) - healSupply(d)) / eHP(d)
     *                           = incomingBase/eHP(1) * r(d) - healSupplyBase/eHP(1) * healRegionMultiplier(d)
     *   where r(d) = ENEMY_DAMAGE_GROWTH^(d-1) * eHP(1) / eHP(d) is the R-LADDER L1 turns-to-die drift
     *   (fitted to within +/-7%). The drain is a small DIFFERENCE, so it amplifies that drift several
     *   times; the per-region heal lever absorbs the regional part of it.
     *   R-HEALDRAIN-DEPTH requires the result in [0.05, 0.15] for every d in 1..RUN_FINAL_DEPTH.
     * Edge cases:
     *   depth < 1 is clamped to 1; a zero expected eHP returns 0 (no eHP to measure a fraction of).
     *   A negative result is meaningful (net HP GAIN — the anti-face-tank failure mode the band bans).
     */
    public static float netHpDrainFractionAtDepth(float incomingBase, float healSupplyBase,
                                                  float healRegionMultiplier, int depth) {
        int clampedDepth = Math.max(1, depth);
        ExpectedPlayer player = expectedPlayerAtDepth(clampedDepth);
        if (player.effectiveHitPoints <= 0f) {
            return 0f;
        }
        float incoming = incomingBase * compoundDepthMultiplier(BalanceConfig.ENEMY_DAMAGE_GROWTH, clampedDepth);
        float healSupply = healSupplyBase * player.effectiveHitPoints / expectedPlayerAtDepth(1).effectiveHitPoints
                * healRegionMultiplier;
        return (incoming - healSupply) / player.effectiveHitPoints;
    }

    /*
     * Formula: shopPrice — a shop offer's credit price derived from its priced VALUE (order 3)
     * Derivation:
     *   Every offer prices through ONE formula from its value in power points (PP — the same %-of-
     *   reference currency the level-up cards and the ability budget use), never a hand-set base price:
     *       depthFactor = 1 + depthPriceScale * (depth - 1)     // deeper credits are worth less
     *       price       = round( valuePowerPoints * creditsPerPowerPoint * depthFactor )
     *   A weapon-class upgrade prices on its ability/level PP; a consumable prices on its supply value
     *   converted to PP (damage supplied / DAMAGE_PER_PP, or HP restored / HP_PER_PP). One knob
     *   (creditsPerPowerPoint) sets the whole economy's price level, so the credit BAND (R-CREDITS) is
     *   tuned by moving a single number rather than eight hand-set prices.
     * Edge cases:
     *   depth < 1 clamped to 1 (a bad depth never underprices below the base).
     *   Negative value/knob is nonsensical config; not clamped so a bad number surfaces as a bad price
     *     rather than being silently floored. Math.round(double) -> long, cast to int (prices are small).
     */
    public static int shopPrice(float valuePowerPoints, float creditsPerPowerPoint,
                                int depth, float depthPriceScale) {
        return (int) Math.round(valuePowerPoints * creditsPerPowerPoint
                * shopDepthPriceFactor(depth, depthPriceScale));
    }

    /*
     * Formula: shopDepthPriceFactor — the shop's price level at a depth
     * Derivation:
     *   depthFactor = 1 + depthPriceScale * (depth - 1): every offer's price (shopPrice) rides it, so it is
     *   also what one credit is worth at that depth. The route model and the boss reward read credits at
     *   this price level (balance-overhaul order 1) so a depth-scaled bounty never reads as free power.
     * Edge cases: depth < 1 is clamped to 1 (-> 1.0).
     */
    public static float shopDepthPriceFactor(int depth, float depthPriceScale) {
        return 1f + depthPriceScale * (Math.max(1, depth) - 1);
    }

    /*
     * Formula: gambleExpectedValue — the expected worth of a press-your-luck choice (F15)
     * Derivation:
     *   A gamble pays its reward with probability p and costs its bite otherwise:
     *       EV = p * successValue - (1 - p) * failCost
     *   A deterministic choice is p = 1 (the bite never lands).
     * Edge cases:
     *   p is clamped to [0, 1]; a negative failCost is nonsensical config and is not clamped.
     */
    public static float gambleExpectedValue(float successChance, float successValue, float failCost) {
        float chance = Math.max(0f, Math.min(1f, successChance));
        return chance * successValue - (1f - chance) * failCost;
    }

    /*
     * Formula: neededBundlePricePerFloor — the credits an on-curve player NEEDS to spend per floor (R-CREDITS v2)
     * Derivation:
     *   Balance-overhaul order 6, F12. What a floor's income must cover is not a "significant purchase per
     *   region" but the run's actual recurring needs, each at the floor's shop price:
     *       bundle(d) = rungPrice(d) + consumablesPerFloor * consumablePrice(d) + tierUpgradePrice(region) / floorsPerRegion
     *   one LEVEL UP rung (the order-1 ladder step), the typical consumable need, and one tier upgrade per
     *   region amortised evenly over the region's floors. R-CREDITS v2 bands income(d) / bundle(d).
     * Edge cases:
     *   floorsPerRegion <= 0 -> the tier term is dropped (no floors to amortise over). Negative inputs are
     *   nonsensical config and are not clamped, so a bad number surfaces as a bad ratio.
     */
    public static float neededBundlePricePerFloor(int rungPrice, float consumablesPerFloor, int consumablePrice,
                                                  int tierUpgradePrice, int floorsPerRegion) {
        float tierShare = floorsPerRegion > 0 ? tierUpgradePrice / (float) floorsPerRegion : 0f;
        return rungPrice + consumablesPerFloor * consumablePrice + tierShare;
    }

    /*
     * Formula: creditIncomePerRegion — expected credits earned across one region (order 3, part C)
     * Derivation:
     *   The credit HALF of the economy is only a real resource if income is COUPLED to the sink. Income
     *   over a region is the kill bounties (which scale linearly with depth) plus the flat credit chips,
     *   summed across the region's floors:
     *       killIncome(d) = floorKillCreditBase * (1 + creditDepthScale * (d - 1))
     *       income(region) = sum over the region's floors of ( killIncome(d) + chipIncomePerFloor )
     *   floorKillCreditBase is the model floor's total per-kill credit reward; chipIncomePerFloor is the
     *   expected chip count * average chip value. R-CREDITS then checks income(region) against the price
     *   of the expected purchase bundle (one weapon-class buy + a couple of supplies) so a region affords
     *   roughly one significant purchase plus resupply, banking ~one region's worth.
     * Edge cases:
     *   firstDepth < 1 clamped to 1; floorsPerRegion <= 0 -> returns 0 (no floors, no income).
     */
    public static float creditIncomePerRegion(float floorKillCreditBase, float creditDepthScale,
                                              float chipIncomePerFloor, int firstDepth, int floorsPerRegion) {
        if (floorsPerRegion <= 0) {
            return 0f;
        }
        int startDepth = Math.max(1, firstDepth);
        float income = 0f;
        for (int floorOffset = 0; floorOffset < floorsPerRegion; floorOffset++) {
            int depth = startDepth + floorOffset;
            float killIncome = floorKillCreditBase * (1f + creditDepthScale * (depth - 1));
            income += killIncome + chipIncomePerFloor;
        }
        return income;
    }

    /*
     * Formula: emergencySupplyDemandThreshold — the never-softlock floor under catastrophe (order 3, D)
     * Derivation:
     *   A player who wastes ammo must never reach a state where the floor cannot supply enough to
     *   continue — dying to an empty inventory screen instead of to gameplay. The guard: if the player's
     *   TOTAL remaining potential damage (all reserve ammo * efficiency, plus melee) falls below the
     *   remaining floor demand times a small fraction, force the next enemy drop to be ammo:
     *       threshold = remainingFloorDemand * emergencySupplyFraction
     *   emergencySupplyFraction (0.25) sits FAR below the scarcity band, so a hoarder never trips it —
     *   it only converts "standing empty-handed" deaths into fighting-retreat deaths. Melee (fist, no
     *   ammo) is the true always-available backstop; this only keeps the gap crossable.
     * Edge cases:
     *   Negative demand/fraction is nonsensical config; returned verbatim so a bad number is visible.
     */
    public static float emergencySupplyDemandThreshold(float remainingFloorDemand, float emergencySupplyFraction) {
        return remainingFloorDemand * emergencySupplyFraction;
    }

    /*
     * Formula: emergencySupplyTriggers — whether the never-softlock ammo drop must fire (order 3, D)
     * Derivation:
     *   True exactly when the player's total remaining potential damage has fallen below the emergency
     *   threshold AND the once-per-floor guarantee has not already fired:
     *       triggers = !alreadyGrantedThisFloor
     *                  && totalPotentialDamage < emergencySupplyDemandThreshold(remainingFloorDemand, f)
     *   Kept a pure predicate so a unit test can exercise it with synthetic inventories (order-3
     *   acceptance) and the live EnemyManager can call the identical logic on each kill.
     * Edge cases:
     *   remainingFloorDemand <= 0 -> false (nothing left to fight; no lifeline needed).
     */
    public static boolean emergencySupplyTriggers(float totalPotentialDamage, float remainingFloorDemand,
                                                  float emergencySupplyFraction, boolean alreadyGrantedThisFloor) {
        if (alreadyGrantedThisFloor || remainingFloorDemand <= 0f) {
            return false;
        }
        return totalPotentialDamage < emergencySupplyDemandThreshold(remainingFloorDemand, emergencySupplyFraction);
    }

    // =========================================================================
    // ROUTE ECONOMICS (new-game-balancr order 7) — PRICING THE BRANCHING MAP
    // Orders 1-6 price FLOORS; the player plays a JOURNEY through the route map's DAG. These are
    // the formulas that turn a route node's ledger row (route/NodeEconomics) into numbers the
    // balance contract can band: what it COSTS in threat, what it is WORTH, whether its reward
    // pays for its danger, what risk tier it must therefore display, and how a whole journey's
    // resources accumulate. All pure; the model that feeds them lives in util/RouteEconomicsModel.
    // =========================================================================

    /*
     * Formula: nodeThreatCost — the Threat Points a route node actually puts in front of the player
     * Derivation:
     *   A node never changes the DEPTH of the floor it builds (route-map DEPTH RAMP INVARIANT) — it
     *   scales the Threat-Point budget the encounter planner spends on that depth. Two multipliers
     *   compose, both applied on TOP of the already depth- and region-scaled budget:
     *       threatCost = regionScaledFloorBudget(depth) * nodeBudgetScale * affixBudgetMultiplier
     *   nodeBudgetScale is the node's own dial (CACHE 0.28, COMBAT 1.0, ELITE 1.5); the affix
     *   multiplier is the ELITE modifier folded on (SWARM 1.5, OVERCLOCKED 1.2, others 1.0). Because
     *   both are multipliers on the SAME depth-ramped budget, a node's threat ratio against the
     *   standard combat node is exactly (scale * affix) at every depth — which is what makes the
     *   risk-premium and derived-pip rules depth-independent.
     * Edge cases:
     *   A zero or negative budget/scale yields 0 (a floor with no roster — the honest read of the
     *   MED-BAY / EVENT / GATE generators, which emit no enemy spawn points at all).
     */
    public static float nodeThreatCost(float regionScaledFloorThreatPointBudget,
                                       float nodeBudgetScale, float affixBudgetMultiplier) {
        float threatCost = regionScaledFloorThreatPointBudget * nodeBudgetScale * affixBudgetMultiplier;
        return threatCost > 0f ? threatCost : 0f;
    }

    /*
     * Formula: expectedOnLevelWeaponOffers — on-level weapons a floor's supply spec offers (S9)
     * Derivation:
     *   A spec drops `drops` weapons, each with probability `chance`, at an integer level offset drawn
     *   uniformly from [offsetMin, offsetMax]. A weapon at offset >= 0 (level >= the floor's depth) is
     *   the one that can be an UPGRADE (the cadence rule's "level >= d"). So
     *       onLevelShare = |{o in [offsetMin, offsetMax] : o >= 0}| / (offsetMax - offsetMin + 1)
     *       offers       = drops * chance * onLevelShare
     *   COMBAT 1 @ -1..0 -> 0.5; ELITE 1 @ +1..+2 -> 1.0; CACHE 1 @ 0 at 50% -> 0.5.
     * Edge cases: drops <= 0 or chance <= 0 -> 0; offsetMax < offsetMin is treated as the single
     *   offset offsetMin; chance is clamped to [0, 1].
     */
    public static float expectedOnLevelWeaponOffers(int drops, float chance, int offsetMin, int offsetMax) {
        if (drops <= 0 || chance <= 0f) {
            return 0f;
        }
        int high = Math.max(offsetMin, offsetMax);
        int width = high - offsetMin + 1;
        int onLevel = Math.max(0, high - Math.max(0, offsetMin) + 1);
        if (high < 0) onLevel = 0;
        float share = (float) Math.min(onLevel, width) / width;
        return drops * Math.min(1f, chance) * share;
    }

    /*
     * Formula: nodeExpectedValue — one comparable EV number for any route node
     * Derivation:
     *   A node hands the player RESOURCES (ammo damage, healing, credits — net of what the fight
     *   consumes) and PROGRESS (XP levels, weapon-class upgrade opportunities), and charges DANGER.
     *   Resources and progress are already in the same currency (POWER POINTS: the unit orders 2/3
     *   use for cards, abilities and shop prices), but they are not interchangeable one-for-one: a
     *   level or a gun applies to EVERY remaining floor, while an ammo box is spent once. So progress
     *   is weighted by a durability factor, and the danger is charged as a multiple of what ONE
     *   standard floor's danger is worth:
     *       EV = resourceDeltaPP + permanentWeight * progressPP - riskPerStandardFloor * threatRatio
     *   permanentWeight < 1 would say "consumables beat permanent power"; the honest read is the
     *   opposite, which is exactly why an un-priced map let a calm-chaining route out-earn a fighting
     *   one. The risk term is charged on the threat RATIO (this node's Threat Points over a standard
     *   combat floor's at the same depth), not on raw Threat Points: TP grow geometrically with depth
     *   while the resources and progress a floor hands over do not, so a raw-TP charge would make
     *   every deep floor read as a catastrophic loss. A ratio keeps the danger charge depth-stable —
     *   one standard floor always costs one standard floor's worth of risk.
     * Edge cases:
     *   All terms may be negative (a MALFUNCTION sector nets resources DOWN), so no clamping — a
     *   node that is a net loss must be visible as one.
     */
    public static float nodeExpectedValue(float resourceDeltaPowerPoints, float progressPowerPoints,
                                          float threatRatioVsStandardFloor, float permanentPowerWeight,
                                          float riskPowerPointsPerStandardFloor) {
        return resourceDeltaPowerPoints
                + permanentPowerWeight * progressPowerPoints
                - riskPowerPointsPerStandardFloor * threatRatioVsStandardFloor;
    }

    /*
     * Formula: rewardPremiumRatio — does a node's reward pay for its danger? (R-RISK-PREMIUM)
     * Derivation:
     *   A route choice is only a real trade-off if reward scales with priced risk. Measure both as
     *   PREMIUMS over the standard combat node and divide:
     *       rewardPremium = nodeReward / standardReward
     *       threatPremium = nodeThreat / standardThreat
     *       ratio         = rewardPremium / threatPremium
     *                     = (nodeReward * standardThreat) / (standardReward * nodeThreat)
     *   ratio == 1 is a FAIR bet (reward rises exactly in step with danger); the contract band
     *   [1.0, 1.2] means danger pays slightly better than fair — never free, never a sucker bet.
     *   Reward here is GROSS (what the node hands over), because the consumption side is already
     *   proportional to the threat premium it is being divided by.
     * Edge cases:
     *   standardReward or nodeThreat <= 0 -> POSITIVE_INFINITY (an un-priced denominator must fail
     *   loudly in the audit rather than silently read as fair).
     */
    public static float rewardPremiumRatio(float nodeRewardPowerPoints, float standardRewardPowerPoints,
                                           float nodeThreatCost, float standardThreatCost) {
        if (standardRewardPowerPoints <= 0f || nodeThreatCost <= 0f || standardThreatCost <= 0f) {
            return Float.POSITIVE_INFINITY;
        }
        float rewardPremium = nodeRewardPowerPoints / standardRewardPowerPoints;
        float threatPremium = nodeThreatCost / standardThreatCost;
        return rewardPremium / threatPremium;
    }

    /*
     * Formula: derivedDangerTierIndex — risk pips DERIVED from price, never hand-assigned (R-PIPS-DERIVED)
     * Derivation:
     *   The overlay's risk pips are the player's ONLY information when choosing a route, so they must
     *   track the priced threat or the map lies and informed choice collapses. The tier is read off
     *   the node's threat RATIO against the standard combat node at the same depth (a ratio, so it is
     *   depth-independent — see nodeThreatCost), with two structural overrides first:
     *       forced convergence node        -> SET_PIECE (4)   (a boss/gate is a set-piece, not a dial)
     *       hidden outcome table (MYSTERY)  -> GAMBLE   (3)   (its VARIANCE is the danger, not its mean)
     *       ratio <= calmMaxRatio           -> CALM     (0)
     *       ratio <= standardMaxRatio       -> STANDARD (1)
     *       otherwise                        -> DANGER   (2)
     *   The returned index is the ordinal of route/DangerTier, kept as an int so this formula stays
     *   in the headless math layer instead of importing the route enum.
     * Edge cases:
     *   A negative ratio cannot occur (threat costs are clamped at 0); ratio 0 reads CALM, which is
     *   the correct read for a floor that spawns nothing.
     */
    public static int derivedDangerTierIndex(float nodeThreatRatioVsStandardCombat,
                                             boolean forcedConvergenceNode, boolean hiddenOutcomeTable,
                                             float calmMaxRatio, float standardMaxRatio) {
        if (forcedConvergenceNode) {
            return 4; // DangerTier.SET_PIECE
        }
        if (hiddenOutcomeTable) {
            return 3; // DangerTier.GAMBLE
        }
        if (nodeThreatRatioVsStandardCombat <= calmMaxRatio) {
            return 0; // DangerTier.CALM
        }
        if (nodeThreatRatioVsStandardCombat <= standardMaxRatio) {
            return 1; // DangerTier.STANDARD
        }
        return 2; // DangerTier.DANGER
    }

    /*
     * Formula: trajectoryResourceLedger — the JOURNEY's cumulative scarcity (R-TRAJECTORY)
     * Derivation:
     *   Order 3 audits scarcity S = SUPPLY / DEMAND on ONE floor. A branching map means the player
     *   never plays "a floor" — they play a PATH, and a path that chains calm nodes accumulates a
     *   very different ledger from one that chains elites. The journey-level ledger is the same
     *   ratio taken over the nodes ACTUALLY VISITED:
     *       S(journey) = sum(supplyDamage of visited nodes) / sum(demandDamage of visited nodes)
     *   Summing first and dividing once (rather than averaging per-floor ratios) is the correct
     *   aggregation: ammo banks across floors, so a rich floor genuinely offsets a lean one.
     * Edge cases:
     *   cumulativeDemand <= 0 (a journey of nothing but zero-spawn sanctuary floors) -> returns
     *   POSITIVE_INFINITY, which the audit reads as "no demand to measure", not as a violation.
     */
    public static float trajectoryResourceLedger(float cumulativeSupplyDamage, float cumulativeDemandDamage) {
        if (cumulativeDemandDamage <= 0f) {
            return Float.POSITIVE_INFINITY;
        }
        return cumulativeSupplyDamage / cumulativeDemandDamage;
    }

    /*
     * Formula: depthScaledPickupCount — a stocked payoff that keeps its VALUE as depth scales (order 7)
     * Derivation:
     *   A medkit is a flat number of hit points, but the damage a floor deals compounds with depth, so a
     *   fixed stock of pickups is worth a shrinking share of the fight the deeper the run goes. Where a
     *   node's ENTIRE payoff is healing (the MED-BAY clinic), that decay turns it into a dead choice deep
     *   in a run. Scaling the COUNT by the same enemy-damage compound the incoming damage rides restores
     *   the node's relative worth:
     *       count(d) = max(1, round( baseCount * perDepthScale^(d-1) ))
     *   i.e. "the deeper the facility, the better stocked its medical bay" — one medkit early, three at
     *   depth 15. Integer rounding makes it step rather than ramp, which is what a stack of pickups is.
     * Edge cases:
     *   depth <= 1 -> the base count. baseCount <= 0 -> 0 (a node that stocks nothing stays empty).
     */
    public static int depthScaledPickupCount(int baseCount, float perDepthScale, int depth) {
        if (baseCount <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(baseCount * compoundDepthMultiplier(perDepthScale, depth)));
    }

    /*
     * Formula: depthScaledRewardXp — a hand-set XP reward, carried honestly down the run (order 7)
     * Derivation:
     *   Order 4 made per-KILL XP derived (XP_PER_THREAT_POINT * depth-scaled Threat), but a FLAT XP
     *   grant written into content — an EVENT choice's "you learn something" payout — silently decays
     *   to nothing as the geometric level requirement grows: 60 XP is 40% of a level at depth 1 and 9%
     *   of one at depth 15, so a deep event node quietly becomes worthless. The fix is to read a
     *   hand-set grant as "this fraction of a level", and re-express it at the depth actually played:
     *       reward(d) = baseXpAtDepthOne * xpRequired(expectedLevel(d)) / xpBaseRequirement
     *   At depth 1 this is exactly the authored number (xpRequired(1) == base), and deeper it keeps the
     *   SAME share of a level — the reward is depth-honest without a second table of constants.
     * Edge cases:
     *   xpBaseRequirement <= 0 -> the base value is returned unscaled (bad config stays visible).
     *   depth <= 1 -> the scale factor is exactly 1.0.
     */
    public static int depthScaledRewardXp(int baseXpAtDepthOne, int xpBaseRequirement,
                                          float growthPerLevel, float levelsPerDepth, int depth) {
        if (xpBaseRequirement <= 0) {
            return baseXpAtDepthOne;
        }
        int expectedLevel = expectedLevelAtDepth(levelsPerDepth, depth);
        int levelCost = xpRequiredForLevelGeometric(xpBaseRequirement, growthPerLevel, expectedLevel);
        return Math.round(baseXpAtDepthOne * (levelCost / (float) xpBaseRequirement));
    }

    /*
     * Formula: cumulativeXpRequiredToReachLevel — total XP a run must bank to stand at a level
     * Derivation:
     *   xpRequiredForLevelGeometric(level) is the XP for ONE step (level -> level+1). Reaching level L
     *   from level 1 costs the sum of the first L-1 steps, a geometric series:
     *       total(L) = sum over l = 1..L-1 of base * growth^(l-1)
     *                = base * (growth^(L-1) - 1) / (growth - 1)      for growth != 1
     *                = base * (L - 1)                                 for growth == 1
     *   The closed form is used so a journey audit can price "how far behind is this route?" in one
     *   call rather than looping per level. Integer-truncated per step matches the live curve's
     *   per-level int cast closely enough for a pacing ratio (the audit bands are percentages).
     * Edge cases:
     *   level <= 1 -> 0 (standing at level 1 costs nothing). growth <= 0 is nonsensical config and is
     *   not clamped, so a bad number surfaces in the report rather than silently reading as sane.
     */
    public static float cumulativeXpRequiredToReachLevel(int base, float growthPerLevel, int level) {
        int steps = Math.max(0, level - 1);
        if (steps == 0) {
            return 0f;
        }
        if (Math.abs(growthPerLevel - 1f) < 1e-6f) {
            return (float) base * steps;
        }
        return (float) (base * (Math.pow(growthPerLevel, steps) - 1.0) / (growthPerLevel - 1.0));
    }

    /*
     * Formula: levelForCumulativeXp — the level a journey's banked XP actually buys
     * Derivation:
     *   The inverse of cumulativeXpRequiredToReachLevel: the largest L whose total requirement the
     *   banked XP covers. Solving base * (growth^(L-1) - 1)/(growth - 1) <= xp for L gives
     *       L = 1 + floor( log( 1 + xp * (growth - 1) / base ) / log(growth) )
     *   This is what makes the trajectory audit honest: a SAFEST route that skips fights banks less
     *   XP, so it stands at a LOWER level than the depth expects, and its depth-coupling is measured
     *   at that real level instead of the expected one.
     * Edge cases:
     *   xp <= 0 or base <= 0 -> level 1 (the run's starting level). growth == 1 degrades to the
     *   linear inverse 1 + floor(xp / base).
     */
    public static int levelForCumulativeXp(int base, float growthPerLevel, float cumulativeXp) {
        if (cumulativeXp <= 0f || base <= 0) {
            return 1;
        }
        if (Math.abs(growthPerLevel - 1f) < 1e-6f) {
            return 1 + (int) Math.floor(cumulativeXp / base);
        }
        double stepsCovered = Math.log(1.0 + cumulativeXp * (growthPerLevel - 1.0) / base)
                / Math.log(growthPerLevel);
        return 1 + (int) Math.floor(stepsCovered);
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
     *   It is also the SEED FINALISER for java.util.Random streams whose seeds can be
     *   adjacent (PerkDeck, EnemyManager's build stream — balance-overhaul order 5):
     *   Random scrambles a seed with one XOR and one LCG step, so its first nextFloat()
     *   for seeds s, s+1, s+2 ... lands in the same narrow band; splitMix64(seed) first
     *   spreads adjacent seeds across the whole 64-bit space.
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

    // =========================================================================
    // STELLAR OBSERVATORY — TRUE-CIRCLE ROTUNDA CARVE CLASSIFICATION
    // =========================================================================

    /**
     * Classification of a tile's relationship to a rasterized circle carved into
     * the level grid, as used by the STELLAR_OBSERVATORY room generator.
     * See {@code .claude/agents/ideas/stellar-observatory-gravity-well-room.txt}
     * ("ROOM SHAPE" / "GENERATOR ALGORITHM" step 2).
     */
    public enum RotundaTileClass {
        /** Interior of the rotunda — carve to open/lit floor. */
        FLOOR,
        /** The one-tile-thick boundary wall ring — carve to a shell wall variant. */
        SHELL,
        /** Outside the rotunda entirely — leave the tile untouched. */
        OUTSIDE
    }

    /*
     * Formula: classifyRotundaTile
     * Derivation:
     *   Given a tile's integer offset from the room centroid:
     *     differenceColumn = tileColumn - centerColumn
     *     differenceRow    = tileRow    - centerRow
     *   compute the squared Euclidean distance from the centroid (no sqrt/trig
     *   needed — this runs once per tile in the room's bounding square during
     *   generation):
     *     distanceSquared = differenceColumn^2 + differenceRow^2
     *
     *   Compare against two squared thresholds:
     *     distanceSquared <= radius^2                      -> FLOOR   (interior)
     *     radius^2 < distanceSquared <= (radius + 0.75)^2   -> SHELL   (boundary ring)
     *     distanceSquared > (radius + 0.75)^2               -> OUTSIDE (untouched)
     *
     *   Why "+0.75" and not "+1.0" or an exact "== radius" test:
     *     A pure "on the circle" test (distanceSquared == radius^2, or a thin band
     *     like [radius, radius+epsilon]) can leave single-tile gaps where the
     *     rasterized circle passes diagonally between two grid crossings without
     *     ever landing exactly on an integer-offset tile at that arc — a "diagonal
     *     leak" that would expose the room interior to the untouched exterior.
     *     Widening the shell band to a full extra 0.75 tiles of squared-radius
     *     margin guarantees every FLOOR tile has an orthogonally-adjacent (not just
     *     diagonally-adjacent) SHELL tile all the way around the ring, closing those
     *     gaps and producing a watertight one-tile-thick wall. (The generator's
     *     LEAK-SEAL PASS is a belt-and-suspenders follow-up for the rare residual
     *     case; this band is the primary defense.)
     * Edge cases:
     *   radius <= 0: every tile with distanceSquared > 0 is SHELL or OUTSIDE and the
     *     centroid tile itself (distanceSquared = 0) is FLOOR — degenerate but not
     *     divide-by-zero (no division in this method at all).
     *   No sqrt or trig is used, so there is no precision loss beyond standard float
     *     squaring; safe for the integer offsets used here (room radii are single-
     *     digit tile counts, far below float precision limits).
     */
    public static RotundaTileClass classifyRotundaTile(int differenceColumn, int differenceRow, float radius) {
        float distanceSquared = (float) (differenceColumn * differenceColumn + differenceRow * differenceRow);
        float radiusSquared = radius * radius;
        if (distanceSquared <= radiusSquared) {
            return RotundaTileClass.FLOOR;
        }
        float shellOuterRadius = radius + 0.75f;
        float shellOuterRadiusSquared = shellOuterRadius * shellOuterRadius;
        if (distanceSquared <= shellOuterRadiusSquared) {
            return RotundaTileClass.SHELL;
        }
        return RotundaTileClass.OUTSIDE;
    }

    // =========================================================================
    // STELLAR OBSERVATORY — CATWALK ANNULUS TEST
    // =========================================================================
    /*
     * Formula: isOnCatwalkAnnulus
     * Derivation:
     *   The catwalk ring is a concentric raised walkway at radius catwalkRadius
     *   (Rc). A tile's offset from the centroid (differenceColumn, differenceRow)
     *   lies "on" the ring when its true Euclidean distance from the centroid is
     *   within bandHalfWidth of catwalkRadius:
     *     distance = sqrt(differenceColumn^2 + differenceRow^2)
     *     onAnnulus = abs(distance - catwalkRadius) <= bandHalfWidth
     *
     *   Why this needs an actual sqrt rather than a squared-distance shortcut:
     *     A tempting cheaper test is a squared-distance band:
     *       abs(distanceSquared - catwalkRadius^2) <= bandWidthSquaredTerm
     *     but this is NOT geometrically equivalent to a constant-width annulus.
     *     distanceSquared - catwalkRadius^2 = (distance - catwalkRadius) * (distance + catwalkRadius),
     *     so a fixed threshold on the squared-difference corresponds to a
     *     (distance - catwalkRadius) tolerance that SHRINKS as (distance + catwalkRadius)
     *     grows — i.e. the effective band width varies with distance from the
     *     center instead of staying a constant bandHalfWidth. For a small
     *     centroid-relative radius like the catwalk ring (Rc ~ 3..4 tiles) this
     *     would produce a visibly non-uniform ring thickness. Since this test runs
     *     ONLY at level-generation time (never per-frame in render()), the exact
     *     sqrt-based test is affordable and preferred over the inexact shortcut.
     * Edge cases:
     *   catwalkRadius = 0: annulus test degenerates to "within bandHalfWidth of the
     *     centroid" (a small disc around the core) — not physically meaningful for
     *     this room but not a divide-by-zero (no division in this method).
     *   bandHalfWidth < 0: no tile ever satisfies the test (abs(...) is always >= 0);
     *     treat as "ring disabled" rather than an error.
     *   differenceColumn = differenceRow = 0: distance = 0, handled by sqrt(0) = 0
     *     without issue.
     */
    public static boolean isOnCatwalkAnnulus(int differenceColumn, int differenceRow,
                                              float catwalkRadius, float bandHalfWidth) {
        float distanceSquared = (float) (differenceColumn * differenceColumn + differenceRow * differenceRow);
        float distance = (float) Math.sqrt(distanceSquared);
        return Math.abs(distance - catwalkRadius) <= bandHalfWidth;
    }

    // =========================================================================
    // STELLAR OBSERVATORY — WRAPPED ANGULAR DIFFERENCE (BOUNDARY DOOR SELECTION)
    // =========================================================================
    /*
     * Formula: angularDifferenceRadians
     * Derivation:
     *   Used by the generator to pick, via argmin over boundary-ring tiles, which
     *   ring tile's bearing-from-center is closest to the corridor connector's
     *   bearing (both bearings computed with atan2, range (-PI, PI]).
     *
     *   The naive difference (angleRadiansA - angleRadiansB) is wrong near the
     *   +/-PI wraparound seam: e.g. angleRadiansA = -3.10, angleRadiansB = 3.10
     *   are only ~0.08 radians apart on the circle, but the naive subtraction
     *   gives -6.20, whose absolute value (~6.20) is nearly a full turn (2*PI)
     *   away from the true answer.
     *
     *   Standard fix — normalize the raw difference into (-PI, PI] before taking
     *   the magnitude:
     *     rawDifference = angleRadiansA - angleRadiansB
     *     wrapped = rawDifference - 2*PI * floor((rawDifference + PI) / (2*PI))
     *   This is the textbook "wrap to (-PI, PI]" formula: adding PI before the
     *   floor-divide by the full turn (2*PI) and subtracting it back out shifts
     *   the wraparound seam from 0 to +/-PI, matching atan2's own range, so any
     *   input difference (however many full turns off) is folded back into
     *   exactly one representative in (-PI, PI].
     *     result = abs(wrapped)   ∈ [0, PI]
     *
     *   Verification with the example above:
     *     rawDifference = -3.10 - 3.10 = -6.20
     *     (rawDifference + PI) / (2*PI) = (-6.20 + 3.14159) / 6.28319 ≈ -0.4867
     *     floor(-0.4867) = -1
     *     wrapped = -6.20 - 2*PI*(-1) = -6.20 + 6.28319 ≈ 0.0832
     *     result = abs(0.0832) ≈ 0.083  radians  (matches the true ~0.08 answer)
     * Edge cases:
     *   angleRadiansA == angleRadiansB: rawDifference = 0, wrapped = 0, result = 0.
     *   Difference of exactly PI (opposite bearings): wraps to exactly PI (or -PI,
     *     both fold to the same magnitude), result = PI — the maximum possible
     *     angular difference, as expected for antipodal bearings.
     *   Inputs far outside (-PI, PI] (e.g. accumulated multi-turn angles): still
     *     handled correctly because the floor-divide removes any integer number of
     *     full turns before the final subtraction.
     *   No division-by-zero: 2*PI is a nonzero compile-time constant.
     */
    public static float angularDifferenceRadians(float angleRadiansA, float angleRadiansB) {
        float rawDifference = angleRadiansA - angleRadiansB;
        float wrapped = rawDifference - MathUtils.PI2 * MathUtils.floor((rawDifference + MathUtils.PI) / MathUtils.PI2);
        return Math.abs(wrapped);
    }

    /*
     * Formula: vitalityFraction
     * Derivation:
     *   The marine's survivability pool is health PLUS armour: armour absorbs a fixed
     *   fraction of every incoming hit, so a full armour bar is real staying power, not
     *   decoration. The fraction is therefore the combined current pool over the combined
     *   maximum pool:
     *     vitality = (health + armor) / (maxHealth + maxArmor)
     *   This is the single number a "how close am I to dying" check should read; health
     *   alone under-reports danger at full armour and over-reports it at zero armour.
     * Edge cases:
     *   maxHealth + maxArmor == 0 (a degenerate/unconfigured player) returns 0 instead of
     *     dividing by zero.
     *   Values above the maxima (over-heal) can return > 1; callers that need a probability
     *     should clamp. Negative current values are impossible (both pools floor at zero).
     */
    public static float vitalityFraction(int health, int armor, int maxHealth, int maxArmor) {
        int maximumPool = maxHealth + maxArmor;
        if (maximumPool <= 0) return 0f;
        return (health + armor) / (float) maximumPool;
    }

    /*
     * Formula: floorSeed
     * Derivation:
     *   splitmix-style mixing — multiplying the run seed by a large odd prime (the golden-ratio
     *   64-bit constant 0x9E3779B97F4A7C15) distributes its bits across the whole word, then
     *   adding the depth guarantees a distinct seed per floor even when two run seeds differ
     *   only in their low bits:
     *       floorSeed(runSeed, depth) = runSeed * 0x9E3779B97F4A7C15 + depth
     *   Every per-floor stream (level generation, enemy spawn/drop/effect rolls, hazards, the
     *   shop roll) derives from this one value, which is what makes a run reproducible from its
     *   run seed alone (the order-9 determinism contract).
     * Edge cases:
     *   long overflow wraps, which is intended (the multiply is modulo 2^64).
     *   depth = 0 is valid and yields the pre-descent (staging) seed.
     */
    public static long floorSeed(long runSeed, int depth) {
        return runSeed * 0x9E3779B97F4A7C15L + depth;
    }

    /*
     * Formula: story speaker sting sample (16-bit PCM)
     * Derivation:
     *   A single mono audio sample for a short non-verbal speaker "sting" (Story UI order-1).
     *   time            t          = sampleIndex / sampleRateHz
     *   oscillator      wave(t)     = sine  : sin(2*pi*frequencyHz*t)
     *                               = square: sign(sin(2*pi*frequencyHz*t))  (hard, for the Organization)
     *   envelope        env(t)      = linear attack/release over `envelopeFraction` of the tone so
     *                                 the start/end fade to zero (no click).  With total length T:
     *                                   attack/release window w = envelopeFraction * T
     *                                   env = min(1, t/w, (T - t)/w), clamped to [0,1]
     *   amplitude scale s           = volume * 32767 (full-scale 16-bit)
     *   sample          = round(s * env(t) * wave(t)), clamped to the signed-16-bit range.
     * Edge cases:
     *   sampleRateHz <= 0 or totalSamples <= 0 -> returns 0 (silence) rather than dividing by zero.
     *   envelopeFraction <= 0 -> no fade (env = 1) so a click is possible; callers pass > 0.
     *   envelopeFraction >= 0.5 -> attack and release meet; env peaks below 1 but never negative.
     *   clamp guards against float rounding pushing the value just past +/-32767.
     */
    public static short storyStingSample(float frequencyHz, boolean square, float volume,
                                         int sampleIndex, int totalSamples, int sampleRateHz,
                                         float envelopeFraction) {
        if (sampleRateHz <= 0 || totalSamples <= 0) return 0;
        float time  = sampleIndex / (float) sampleRateHz;
        float phase = MathUtils.PI2 * frequencyHz * time;
        float wave  = square ? (MathUtils.sin(phase) >= 0f ? 1f : -1f) : MathUtils.sin(phase);

        float envelope = 1f;
        if (envelopeFraction > 0f) {
            float windowSamples = envelopeFraction * totalSamples;
            float attack  = sampleIndex / windowSamples;
            float release = (totalSamples - sampleIndex) / windowSamples;
            envelope = Math.min(1f, Math.min(attack, release));
            if (envelope < 0f) envelope = 0f;
        }

        int value = Math.round(volume * 32767f * envelope * wave);
        if (value >  32767) value =  32767;
        if (value < -32768) value = -32768;
        return (short) value;
    }

    /*
     * Formula: dismissible story panel visible fraction (fade in -> hold indefinitely -> fade out)
     * Derivation:
     *   A story bark has NO hold timer: it fades in, then stays at full opacity for as long as the
     *   player needs, and only fades out once THEY dismiss it (Story UI order-2).  So the alpha is
     *   driven by two independent clocks rather than one:
     *       deliveredElapsed  — seconds since the panel appeared
     *       dismissElapsed    — seconds since the player dismissed it (only meaningful once dismissed)
     *   Rising  (not dismissed, deliveredElapsed < fadeIn):  f = deliveredElapsed / fadeIn
     *   Held    (not dismissed, otherwise):                   f = 1
     *   Falling (dismissed):                                  f = fadeInReached * (1 - dismissElapsed/fadeOut)
     *   where fadeInReached is the alpha the panel had when it was dismissed — dismissing during the
     *   fade-in must not pop the panel to full opacity before fading it out, so the falling ramp is
     *   scaled by min(1, deliveredElapsed/fadeIn) and the curve stays continuous at the moment of
     *   dismissal.  The result is clamped to [0, 1] and multiplies alpha only; the panel never moves,
     *   so combat screen shake cannot disturb story text.
     * Edge cases:
     *   deliveredElapsed <= 0 -> 0 (nothing drawn before the panel is delivered).
     *   fadeIn  <= 0          -> the rising span is skipped; the panel starts fully opaque.
     *   fadeOut <= 0          -> the panel vanishes the instant it is dismissed (no divide by zero).
     *   dismissElapsed >= fadeOut -> 0; the caller then retires the panel.
     *   float drift at a span boundary can only move the result by an epsilon; the clamp absorbs it.
     */
    public static float storyPanelVisibleFraction(float deliveredElapsedSeconds, float fadeInSeconds,
                                                  boolean dismissed, float dismissElapsedSeconds,
                                                  float fadeOutSeconds) {
        if (deliveredElapsedSeconds <= 0f) return 0f;
        float risen = fadeInSeconds > 0f
                ? MathUtils.clamp(deliveredElapsedSeconds / fadeInSeconds, 0f, 1f)
                : 1f;
        if (!dismissed) return risen;
        if (fadeOutSeconds <= 0f) return 0f;
        float falling = 1f - MathUtils.clamp(dismissElapsedSeconds / fadeOutSeconds, 0f, 1f);
        return MathUtils.clamp(risen * falling, 0f, 1f);
    }

    /*
     * Formula: boot-card revealed line count (the terminal "printing in" reveal, Story UI order-3)
     * Derivation:
     *   The reprint card's SYSTEM status lines print one after another, like a terminal waking up,
     *   rather than appearing as a block.  Line i (0-based) is on screen once
     *       elapsed >= i * perLineSeconds
     *   so the number of lines revealed at time t is
     *       revealed = floor(t / perLineSeconds) + 1
     *   clamped into [0, totalLines].  The +1 makes the FIRST line visible at t = 0, which is what
     *   keeps the card from starting as an empty panel.
     * Edge cases:
     *   elapsedSeconds < 0    -> 0 (nothing drawn before the card is presented).
     *   perLineSeconds <= 0   -> every line at once (the "reduce text hold" setting can drive the
     *                            per-line delay to zero; this must not divide by zero).
     *   totalLines <= 0       -> 0.
     *   A very large elapsed cannot overflow: the quotient is clamped before the cast is used.
     */
    public static int bootCardRevealedLineCount(float elapsedSeconds, float perLineSeconds,
                                                int totalLines) {
        if (totalLines <= 0 || elapsedSeconds < 0f) return 0;
        if (perLineSeconds <= 0f) return totalLines;
        float revealedSpans = elapsedSeconds / perLineSeconds;
        if (revealedSpans >= totalLines) return totalLines;
        return Math.min(totalLines, (int) revealedSpans + 1);
    }

    /*
     * Formula: instance counter text ("INSTANCE #0048", Story UI order-3)
     * Derivation:
     *   Renders a reprint number zero-padded to a fixed width so the counter's glyph box never
     *   changes size as the number climbs — a counter that grows a digit mid-session reads as a
     *   layout bug rather than as the quiet horror it is.
     *       padding = max(0, minimumDigits - digitsIn(value))
     *       text    = '0' * padding + value
     * Edge cases:
     *   Negative input clamps to 0 (a reprint count is never negative).
     *   A value with MORE digits than minimumDigits is printed in full — never truncated, because
     *   truncating would silently reset the number the whole beat is built on.
     *   minimumDigits <= 0 -> no padding.
     */
    public static String formatInstanceCounter(int instanceNumber, int minimumDigits) {
        int safeNumber = Math.max(0, instanceNumber);
        String digits  = Integer.toString(safeNumber);
        int padding    = minimumDigits - digits.length();
        if (padding <= 0) return digits;
        StringBuilder padded = new StringBuilder(minimumDigits);
        for (int padIndex = 0; padIndex < padding; padIndex++) padded.append('0');
        return padded.append(digits).toString();
    }

    /*
     * Formula: stacked button bottom edge (the Story UI order-4 choice stack)
     * Derivation:
     *   Buttons of equal height are stacked DOWNWARD from a fixed top edge, separated by a constant
     *   gap.  Slot k therefore starts one full pitch (height + gap) below slot k-1:
     *       topY(k)    = stackTopY - k * (buttonHeight + buttonGap)
     *       bottomY(k) = topY(k) - buttonHeight
     *                  = stackTopY - k * (buttonHeight + buttonGap) - buttonHeight
     *   World coordinates are Y-UP with the origin bottom-left, so "down the stack" is subtraction
     *   and bottomY is the LibGDX rect origin the caller draws and hit-tests from.
     *   The stack is top-anchored on purpose: a two-answer exchange and a three-answer one put
     *   their first button in the same place, so the thumb only ever learns one spot.
     * Edge cases:
     *   buttonIndex 0 returns stackTopY - buttonHeight (the first plate hangs from the top edge).
     *   A negative index is clamped to 0 rather than drawing a plate above the stack.
     *   Zero gap simply abuts the plates; zero height degenerates to a line at the anchor — both are
     *   layout choices, not error states, so neither is special-cased beyond the clamp.
     */
    public static float stackedButtonBottomY(float stackTopY, float buttonHeight, float buttonGap,
                                             int buttonIndex) {
        int safeIndex = Math.max(0, buttonIndex);
        return stackTopY - safeIndex * (buttonHeight + buttonGap) - buttonHeight;
    }

    /*
     * Formula: clamped scroll offset (the Story UI order-6 codex body)
     * Derivation:
     *   A scrollable view shows a window of height V onto content of height C.  The offset is how
     *   far the content has been pushed UP past the window's top edge, so the first visible unit of
     *   content is at offset and the last is at offset + V:
     *       0 <= offset <= C - V
     *   When the content is shorter than the window there is nothing to scroll and the only legal
     *   offset is 0, which is exactly what max(0, C - V) yields — so one expression covers both
     *   cases and the "content shorter than the view" branch needs no special handling.
     *   Sign convention: increasing offset reveals content FURTHER DOWN the document.  That is
     *   independent of the world's Y-up axis; the renderer converts an offset into a Y by
     *   subtracting it from the body's top edge.
     * Edge cases:
     *   Negative content or viewport heights clamp to 0 rather than producing a negative range.
     *   A NaN request would propagate, so it is mapped to 0 — a scroll position must always be a
     *   real number the renderer can draw from.
     */
    public static float clampScrollOffset(float requestedOffset, float contentHeight,
                                          float viewportHeight) {
        if (Float.isNaN(requestedOffset)) return 0f;
        float maximumOffset = Math.max(0f, contentHeight - viewportHeight);
        if (requestedOffset <= 0f)            return 0f;
        if (requestedOffset >= maximumOffset) return maximumOffset;
        return requestedOffset;
    }

    /*
     * Formula: whole hours between two epoch timestamps (narrative-rework order-9's re-entry gap)
     * Derivation:
     *   Both instants are milliseconds since the epoch on the same clock, so the elapsed span is a
     *   plain difference and the conversion is one division by the number of milliseconds in an
     *   hour:
     *       hours = floor((now - earlier) / (1000 * 60 * 60))
     *   Integer division truncates towards zero and the numerator here is non-negative (see the
     *   edge cases), so truncation IS the floor.  Whole hours are the right grain: the threshold
     *   this feeds is measured in hours, and a re-entry line that turned on a minute earlier or
     *   later would read identically to the player.
     * Edge cases:
     *   earlierEpochMillis <= 0 means "never recorded" — a brand-new or freshly wiped save — and
     *   returns 0 rather than the ~57 million hours since 1970, so a first-ever launch can never be
     *   mistaken for a long absence.
     *   A clock that moved BACKWARDS (a manual change, a timezone-less device correcting itself)
     *   would give a negative span; it clamps to 0, because "the future" is not an absence and the
     *   only safe reading of a broken clock is that the player is here now.
     */
    public static long hoursBetweenEpochMillis(long earlierEpochMillis, long laterEpochMillis) {
        if (earlierEpochMillis <= 0L) return 0L;
        long elapsedMillis = laterEpochMillis - earlierEpochMillis;
        if (elapsedMillis <= 0L) return 0L;
        return elapsedMillis / (1000L * 60L * 60L);
    }

    // =========================================================================================
    // GAMEPLAY SOUND SYNTHESIS (procedural-sound-effects order 1)
    //
    // The story layer's storyStingSample() above is a sine/square oscillator times a SYMMETRIC
    // linear fade — correct for a UI blip and completely wrong for gunfire, which is broadband
    // noise with a hard transient and an exponential decay.  The primitives below are the toolkit
    // audio/SoundSynthesizer composes into every weapon, impact and damage sound.
    //
    // All of them are pure and allocation-free: they run once per sample during synthesis (tens of
    // thousands of calls per sound, at startup only) and never during render or a game turn.
    // =========================================================================================

    /*
     * Formula: deterministic white-noise sample
     * Derivation:
     *   A gunshot is broadband noise, so the synthesiser needs a uniform random value per sample.
     *   java.util.Random is NOT usable here: the WAV cache is keyed by a filename derived from the
     *   recipe, so the same recipe must produce byte-identical PCM on every device and every
     *   launch, forever.  A stateless integer hash of (seed, sampleIndex) gives that determinism
     *   while still being spectrally flat enough to read as noise.
     *   This is the SplitMix64 finaliser, seeded by the golden-ratio odd constant already used by
     *   floorSeed() above:
     *       mixed = seed * PHI64 + sampleIndex * GAMMA64
     *       mixed ^= mixed >>> 33 ; mixed *= MIX_A
     *       mixed ^= mixed >>> 33
     *   The top 24 bits are then mapped onto [-1, 1):
     *       value = (mixed >>> 40) / 2^23 - 1
     *   Taking the HIGH bits matters — the low bits of a multiplicative hash are far less uniform.
     * Edge cases:
     *   No division by zero is possible; the divisor is a compile-time constant.
     *   long overflow wraps, which is intended (the arithmetic is modulo 2^64).
     *   Every sampleIndex, including 0 and negatives, yields a valid value.
     */
    public static float whiteNoiseSample(long noiseSeed, int sampleIndex) {
        long mixed = noiseSeed * 0x9E3779B97F4A7C15L + sampleIndex * 0xBF58476D1CE4E5B9L;
        mixed ^= (mixed >>> 33);
        mixed *= 0xFF51AFD7ED558CCDL;
        mixed ^= (mixed >>> 33);
        return ((int) (mixed >>> 40)) / 8388608f - 1f;
    }

    /*
     * Formula: one-pole IIR filter coefficient from a cutoff frequency
     * Derivation:
     *   Unfiltered white noise is a thin hiss with no body, so every noise layer in the catalog is
     *   filtered.  The one-pole (single sample of memory) filter's pole position for a given -3 dB
     *   cutoff is the standard impulse-invariant mapping:
     *       coefficient = exp(-2 * PI * cutoffHz / sampleRateHz)
     *   coefficient -> 1 means "almost all memory" (very low cutoff, heavy smoothing);
     *   coefficient -> 0 means "no memory" (cutoff at or above Nyquist, signal passes through).
     * Edge cases:
     *   sampleRateHz <= 0 is a degenerate recipe -> return 0 (pass-through) rather than dividing
     *   by zero.
     *   cutoffHz <= 0 would give coefficient 1 exactly, an integrator that never decays and can
     *   drift unboundedly, so it clamps to 0.9999.
     *   cutoffHz above Nyquist yields a coefficient near 0, which is the correct pass-through.
     */
    public static float onePoleCoefficient(float cutoffHz, int sampleRateHz) {
        if (sampleRateHz <= 0) return 0f;
        if (cutoffHz <= 0f) return 0.9999f;
        double coefficient = Math.exp(-2.0 * Math.PI * cutoffHz / sampleRateHz);
        if (coefficient > 0.9999) return 0.9999f;
        if (coefficient < 0.0)    return 0f;
        return (float) coefficient;
    }

    /*
     * Formula: one-pole low-pass step
     * Derivation:
     *   The difference equation of a one-pole low-pass, written so the coefficient is the pole:
     *       y[n] = (1 - coefficient) * x[n] + coefficient * y[n-1]
     *   The two weights sum to 1, so the filter has unity gain at DC and cannot amplify — which is
     *   what makes it safe to chain several of them before the soft clipper.
     *   The filter is STATEFUL, but the state belongs to the caller's loop (one float per layer),
     *   which is what keeps this method pure and the synthesiser free of hidden state.
     * Edge cases:
     *   coefficient outside [0, 1) would make the filter unstable; onePoleCoefficient() is the
     *   only intended source of the value and clamps to that range.
     */
    public static float onePoleLowPassStep(float previousOutput, float input, float coefficient) {
        return (1f - coefficient) * input + coefficient * previousOutput;
    }

    /*
     * Formula: one-pole high-pass step
     * Derivation:
     *   The matching high-pass for the same pole, the standard DC-blocker form:
     *       y[n] = coefficient * (y[n-1] + x[n] - x[n-1])
     *   It passes the CHANGE between consecutive samples and rejects the steady component, which is
     *   how the bright "crack" layer of a gunshot is separated from its low blast body.
     *   Two states are needed (previous output AND previous input), which is why this step takes
     *   one more argument than the low-pass.
     *   A BAND-PASS is simply this followed by onePoleLowPassStep with a higher cutoff.
     * Edge cases:
     *   As for the low-pass, coefficient is assumed to come from onePoleCoefficient().
     *   On the first sample the caller passes 0 for both previous values, which correctly yields
     *   coefficient * input and introduces no startup click.
     */
    public static float onePoleHighPassStep(float previousOutput, float previousInput, float input,
                                            float coefficient) {
        return coefficient * (previousOutput + input - previousInput);
    }

    /*
     * Formula: percussive attack/decay envelope, normalised to end at exactly zero
     * Derivation:
     *   Gunfire is a near-instant attack followed by an exponential decay.  A raw exp(-k*u) never
     *   reaches zero, and any non-zero value at the final sample is a step discontinuity, which is
     *   an audible click.  So the exponential is shifted and rescaled to hit zero exactly:
     *       attack phase (sampleIndex < attackSamples):
     *           envelope = sampleIndex / attackSamples                    (linear ramp 0 -> 1)
     *       decay phase:
     *           u        = (sampleIndex - attackSamples)
     *                      / (totalSamples - attackSamples)              (0 -> 1)
     *           envelope = (exp(-k*u) - exp(-k)) / (1 - exp(-k))
     *   At u = 0 that is (1 - exp(-k)) / (1 - exp(-k)) = 1, and at u = 1 it is exactly 0, for any
     *   decayRate k > 0.  decayRate is the SHAPE knob: 2 is a soft swell, 6 a gunshot, 12 a click.
     * Edge cases:
     *   attackSamples <= 0 -> the attack phase is skipped entirely and the envelope starts at 1.
     *   totalSamples <= attackSamples -> there is no decay phase; the result is the linear attack
     *   ramp alone, clamped to 1.
     *   decayRate <= 0 -> exp(0) = 1 would make the denominator zero, so it degrades to a plain
     *   linear decay (1 - u), which is well-defined and still ends at zero.
     *   sampleIndex beyond totalSamples clamps to 0 rather than going negative.
     */
    public static float percussiveEnvelope(int sampleIndex, int totalSamples, int attackSamples,
                                           float decayRate) {
        if (totalSamples <= 0)      return 0f;
        if (sampleIndex <= 0)       return attackSamples > 0 ? 0f : 1f;
        if (sampleIndex >= totalSamples) return 0f;

        if (attackSamples > 0 && sampleIndex < attackSamples) {
            return sampleIndex / (float) attackSamples;
        }

        int   decaySamples = totalSamples - Math.max(0, attackSamples);
        if (decaySamples <= 0) return 1f;
        float decayProgress = (sampleIndex - Math.max(0, attackSamples)) / (float) decaySamples;

        if (decayRate <= 0f) return 1f - decayProgress;

        double floorValue = Math.exp(-decayRate);
        double envelope   = (Math.exp(-decayRate * decayProgress) - floorValue) / (1.0 - floorValue);
        if (envelope < 0.0) return 0f;
        return (float) envelope;
    }

    /*
     * Formula: linear chirp phase (frequency sweep)
     * Derivation:
     *   The single most common synthesis bug is writing phase = 2*PI*f(t)*t for a swept frequency,
     *   which sweeps at DOUBLE the intended rate.  Instantaneous frequency is the DERIVATIVE of
     *   phase, so phase must be the INTEGRAL of frequency:
     *       f(t)     = startHz + (endHz - startHz) * t / T
     *       phase(t) = 2*PI * integral from 0 to t of f(s) ds
     *                = 2*PI * (startHz * t + (endHz - startHz) * t^2 / (2*T))
     *   Linear sweeps read as MECHANICAL, which is why the catalog uses this form for every
     *   ballistic weapon and reserves the exponential form for musical rises.
     * Edge cases:
     *   durationSeconds <= 0 -> return 0; there is no sweep to integrate over.
     *   startHz == endHz reduces to 2*PI*startHz*t, the correct constant-frequency phase.
     *   timeSeconds beyond durationSeconds still evaluates (the caller never asks, but the
     *   polynomial stays finite and continuous).
     */
    public static float linearChirpPhase(float startHz, float endHz, float timeSeconds,
                                         float durationSeconds) {
        if (durationSeconds <= 0f) return 0f;
        double sweptTerm = (endHz - startHz) * timeSeconds * timeSeconds / (2.0 * durationSeconds);
        return (float) (MathUtils.PI2 * (startHz * timeSeconds + sweptTerm));
    }

    /*
     * Formula: exponential chirp phase (geometric frequency sweep)
     * Derivation:
     *   A sweep the ear hears as EVENLY rising must be geometric, because pitch perception is
     *   logarithmic.  With ratio r = endHz / startHz:
     *       f(t)     = startHz * r^(t/T)
     *       phase(t) = 2*PI * integral from 0 to t of f(s) ds
     *                = 2*PI * startHz * T * (r^(t/T) - 1) / ln(r)
     *   Used for the railgun charge and the level-up, where the listener expects a musical rise.
     * Edge cases:
     *   durationSeconds <= 0 -> return 0.
     *   startHz <= 0 makes the ratio undefined or infinite, and ratio == 1 makes ln(r) == 0 (a
     *   division by zero).  Both degrade to linearChirpPhase(), which is the correct limit: as
     *   r -> 1 the geometric sweep IS the linear one.
     *   endHz <= 0 is likewise degenerate and takes the same fallback.
     */
    public static float exponentialChirpPhase(float startHz, float endHz, float timeSeconds,
                                              float durationSeconds) {
        if (durationSeconds <= 0f) return 0f;
        if (startHz <= 0f || endHz <= 0f) {
            return linearChirpPhase(startHz, endHz, timeSeconds, durationSeconds);
        }
        double ratio = endHz / (double) startHz;
        if (Math.abs(ratio - 1.0) < 1e-6) {
            return linearChirpPhase(startHz, endHz, timeSeconds, durationSeconds);
        }
        double logarithmOfRatio = Math.log(ratio);
        double sweep = (Math.pow(ratio, timeSeconds / durationSeconds) - 1.0) / logarithmOfRatio;
        return (float) (MathUtils.PI2 * startHz * durationSeconds * sweep);
    }

    /*
     * Formula: amplitude (ring) modulation factor
     * Derivation:
     *   Chopping a layer's amplitude at an audio-rate or near-audio-rate frequency is what turns a
     *   noise bed into a chainsaw and a square wave into electricity.  A unipolar modulator keeps
     *   the factor non-negative so the carrier is never phase-inverted:
     *       unipolar = 0.5 + 0.5 * sin(2*PI*modulatorHz*t)        in [0, 1]
     *       factor   = (1 - depth) + depth * unipolar             in [1 - depth, 1]
     *   depth 0 disables the effect (factor is exactly 1); depth 1 is full ring modulation, where
     *   the signal reaches zero between chops — which is precisely what "chainsaw" sounds like at
     *   35 Hz and "arc discharge" at 60 Hz.
     * Edge cases:
     *   depth is clamped to [0, 1]; outside that range the factor could go negative (inverting the
     *   carrier) or exceed 1 (amplifying into the clipper).
     *   modulatorHz <= 0 leaves sin(0) = 0, giving the constant factor 1 - depth/2, which is
     *   harmless but pointless; callers disable the effect with depth 0 instead.
     */
    public static float amplitudeModulation(float modulatorHz, float timeSeconds, float depth) {
        if (depth <= 0f) return 1f;
        float clampedDepth = depth > 1f ? 1f : depth;
        float unipolar = 0.5f + 0.5f * MathUtils.sin(MathUtils.PI2 * modulatorHz * timeSeconds);
        return (1f - clampedDepth) + clampedDepth * unipolar;
    }

    /*
     * Formula: soft clip to [-1, 1] (Pade approximation of tanh)
     * Derivation:
     *   Summed layers routinely exceed unit amplitude.  HARD clipping (a plain clamp) introduces
     *   infinite-order harmonics and is exactly what makes phone audio sound "broken".  tanh is the
     *   standard soft saturator, but Math.tanh per sample is needlessly expensive, so this is its
     *   well-known Pade [3/2] approximation:
     *       f(value) = value * (27 + value^2) / (27 + 9 * value^2)
     *   It matches tanh closely on [-3, 3] and, at |value| = 3, evaluates to exactly
     *   3 * 36 / 108 = 1 — so clamping to the sign outside that range joins the two pieces
     *   continuously, with no discontinuity to click on.
     * Edge cases:
     *   |value| >= 3 -> return the sign (the approximation diverges beyond its valid range).
     *   NaN cannot arise from the primitives above, but a degenerate recipe could produce one, and
     *   a NaN written into PCM would be an audible burst of noise, so it maps to 0.
     *   The denominator is never zero: 27 + 9*value^2 >= 27.
     */
    public static float softClipUnit(float value) {
        if (Float.isNaN(value)) return 0f;
        if (value >=  3f) return  1f;
        if (value <= -3f) return -1f;
        float squared = value * value;
        return value * (27f + squared) / (27f + 9f * squared);
    }

    /*
     * Formula: unit float to signed 16-bit PCM
     * Derivation:
     *   A 16-bit sample is a signed integer in [-32768, 32767].  The positive full scale is 32767,
     *   so a unit-amplitude signal scales by that:
     *       sample = round(value * 32767)
     *   Rounding (rather than truncating) halves the quantisation error and costs nothing here,
     *   since this runs at synthesis time only.
     * Edge cases:
     *   Float rounding can push the product one past the representable maximum, so the result is
     *   clamped to the signed 16-bit range in both directions.
     *   NaN maps to 0 (silence) rather than to an arbitrary bit pattern.
     */
    public static short toPcm16(float value) {
        if (Float.isNaN(value)) return 0;
        int scaled = Math.round(value * 32767f);
        if (scaled >  32767) scaled =  32767;
        if (scaled < -32768) scaled = -32768;
        return (short) scaled;
    }

    /*
     * Formula: struck-metal oscillator (free-free bar partials with frequency-dependent damping)
     * Derivation:
     *   A struck steel bar, pipe or plate does not ring at integer harmonics.  The transverse modes
     *   of a free-free bar sit at the INHARMONIC ratios 1 : 2.756 : 5.404 : 8.933 (Euler-Bernoulli
     *   beam theory, (beta_n L)^2 normalised to the first mode).  That inharmonicity is precisely what
     *   the ear hears as "metal" rather than "musical note".  Higher modes also lose energy faster,
     *   so each partial carries its own exponential decay proportional to its mode index:
     *       sample(t) = SUM_k  w_k * exp(-damping * k * t) * sin(2*PI * f0 * r_k * t)
     *                   / SUM_k w_k
     *   with r = {1, 2.756, 5.404, 8.933}, w = {1, 0.5, 0.25, 0.125}, k = 0..3.
     *   Dividing by SUM w (1.875) keeps the peak inside [-1, 1] at t = 0, when every partial is at
     *   full weight; afterwards the sum only shrinks.  The layer's own envelope still shapes the
     *   whole sound; this damping only makes the BRIGHT part of the clang die first, like a real
     *   strike does.
     * Edge cases:
     *   fundamentalHz <= 0 -> silence (a bar with no pitch makes no tone).
     *   damping <= 0 -> no per-partial decay; all four partials ring equally long (still bounded).
     *   Negative time is treated as 0 (the layer never asks for it, but it must not blow up).
     *   Partials above the Nyquist limit alias; the catalog keeps f0 * 8.933 under 11 kHz.
     */
    public static float metalBarSample(float fundamentalHz, float timeSeconds, float damping) {
        if (fundamentalHz <= 0f) return 0f;
        float time = Math.max(0f, timeSeconds);
        float effectiveDamping = Math.max(0f, damping);
        float twoPiTime = MathUtils.PI2 * fundamentalHz * time;
        float sum =
                  1.000f * MathUtils.sin(twoPiTime)
                + 0.500f * (float) Math.exp(-effectiveDamping * 1f * time)
                         * MathUtils.sin(twoPiTime * 2.756f)
                + 0.250f * (float) Math.exp(-effectiveDamping * 2f * time)
                         * MathUtils.sin(twoPiTime * 5.404f)
                + 0.125f * (float) Math.exp(-effectiveDamping * 3f * time)
                         * MathUtils.sin(twoPiTime * 8.933f);
        return sum / 1.875f;
    }

    /*
     * Formula: length of a feedback echo's audible tail
     * Derivation:
     *   A feedback delay line repeats the dry signal every delaySeconds, each repeat scaled by
     *   feedback (0 < feedback < 1) relative to the last.  After n repeats the level is feedback^n.
     *   The tail is audible until that falls below silenceLevel:
     *       feedback^n < silenceLevel   <=>   n > ln(silenceLevel) / ln(feedback)
     *   (both logarithms negative, so the quotient is positive).  So
     *       repeats = ceil( ln(silenceLevel) / ln(feedback) )
     *       tail    = repeats * delaySeconds
     *   The in-loop low-pass only ever LOSES energy, so this is a conservative (long) bound.
     * Edge cases:
     *   delaySeconds <= 0 -> 0 (no echo).
     *   feedback <= 0 -> a single repeat: tail = delaySeconds.
     *   feedback >= 1 would never decay (infinite tail); it is clamped to 0.95.
     *   silenceLevel outside (0, 1) is clamped into [0.0001, 0.5] so the log is finite and negative.
     */
    public static float echoTailSeconds(float delaySeconds, float feedback, float silenceLevel) {
        if (delaySeconds <= 0f) return 0f;
        if (feedback <= 0f)     return delaySeconds;
        double clampedFeedback = Math.min(0.95, feedback);
        double clampedSilence  = Math.min(0.5, Math.max(0.0001, silenceLevel));
        double repeats = Math.ceil(Math.log(clampedSilence) / Math.log(clampedFeedback));
        return (float) (repeats * delaySeconds);
    }

    /*
     * Formula: linear end fade that forces a buffer's final sample to exactly zero
     * Derivation:
     *   A buffer that stops on a non-zero sample steps to silence, which is an audible click.  Over
     *   the last fadeSamples samples the gain ramps linearly to zero:
     *       remaining = (totalSamples - 1) - sampleIndex
     *       gain      = clamp(remaining / fadeSamples, 0, 1)
     *   so the last sample (remaining = 0) is multiplied by exactly 0 and everything before the fade
     *   window by exactly 1.
     * Edge cases:
     *   fadeSamples <= 0 -> no fade (gain 1), except the very last sample, which is still zeroed.
     *   sampleIndex past the end -> 0.
     *   totalSamples <= 0 -> 0.
     */
    public static float endFadeGain(int sampleIndex, int totalSamples, int fadeSamples) {
        if (totalSamples <= 0) return 0f;
        int remaining = (totalSamples - 1) - sampleIndex;
        if (remaining <= 0) return 0f;
        if (fadeSamples <= 0) return 1f;
        if (remaining >= fadeSamples) return 1f;
        return remaining / (float) fadeSamples;
    }

    /*
     * Formula: sound volume by distance, with a taper to the audible edge
     * Derivation:
     *   Two factors multiplied:
     *       near  = 1 / (1 + (distanceTiles / referenceTiles)^2)
     *   which is the inverse-square falloff the ear expects, normalised so that a source AT the
     *   listener is exactly 1 and one referenceTiles away is exactly 0.5; and
     *       taper = clamp((maxAudibleTiles - distanceTiles) / taperTiles, 0, 1)
     *   a linear ramp over the last taperTiles before the cutoff.  Without the taper a sound
     *   POPS from a small but audible level to silence as the player steps across the cutoff,
     *   which is far more noticeable than the falloff itself.
     *   Distance here is EUCLIDEAN in tile space, deliberately NOT the Chebyshev metric that
     *   chebyshevDistanceTiles() uses for gameplay range: gameplay range is a RULE, audio distance
     *   is a PERCEPTION, and conflating them would make diagonal sources sound wrong.
     * Edge cases:
     *   distanceTiles <= 0 -> 1 (the source is at the ear).
     *   distanceTiles >= maxAudibleTiles -> 0, checked first so nothing downstream divides.
     *   referenceTiles <= 0 -> 1, treating a degenerate recipe as "at the ear" rather than
     *   dividing by zero.
     *   taperTiles <= 0 -> the taper is skipped (no ramp), leaving the plain inverse-square term.
     */
    public static float sfxDistanceVolume(float distanceTiles, float referenceTiles,
                                          float maxAudibleTiles, float taperTiles) {
        if (distanceTiles >= maxAudibleTiles) return 0f;
        if (distanceTiles <= 0f)   return 1f;
        if (referenceTiles <= 0f)  return 1f;

        float normalised = distanceTiles / referenceTiles;
        float near       = 1f / (1f + normalised * normalised);

        if (taperTiles <= 0f) return near;
        float taper = (maxAudibleTiles - distanceTiles) / taperTiles;
        if (taper > 1f) taper = 1f;
        if (taper < 0f) taper = 0f;
        return near * taper;
    }

    /*
     * Formula: stereo pan from a source offset and the player's facing
     * Derivation:
     *   This project's "right of facing" vector is (directionY, -directionX) — the same rotation
     *   the strafe-right table in CLAUDE.md uses.  The component of the source offset along that
     *   right vector is the dot product:
     *       rightComponent = differenceX * facingDirectionY
     *                      + differenceY * (-facingDirectionX)
     *   Dividing by the offset's length normalises it to [-1, 1], which is exactly LibGDX's pan
     *   convention (-1 hard left, 0 centre, +1 hard right).  The result is then scaled by
     *   panStrength, because a fully hard-panned sound VANISHES on one earbud, and a source the
     *   player cannot hear at all is worse than one that is merely mislocated.
     * Edge cases:
     *   length == 0 (the source is on the player's own tile) -> pan 0; there is no bearing.
     *   A non-unit facing vector would scale the result, so the quotient is clamped to [-1, 1]
     *   before the strength multiply.
     */
    public static float sfxStereoPan(float differenceX, float differenceY,
                                     float facingDirectionX, float facingDirectionY,
                                     float panStrength) {
        float length = (float) Math.sqrt(differenceX * differenceX + differenceY * differenceY);
        if (length <= 0f) return 0f;
        float rightComponent = differenceX * facingDirectionY - differenceY * facingDirectionX;
        float normalised = rightComponent / length;
        if (normalised >  1f) normalised =  1f;
        if (normalised < -1f) normalised = -1f;
        return normalised * panStrength;
    }

    /*
     * Formula: rear-source volume factor
     * Derivation:
     *   With cardinal-only facing, a source directly BEHIND the player pans to 0 — indistinguishable
     *   from one directly in front.  Real head-related transfer functions are out of scope, so the
     *   cue is carried by volume (and, at the call site, by a slightly lower pitch): a sound behind
     *   a head really is quieter and duller.
     *       forwardComponent = (differenceX*facingDirectionX + differenceY*facingDirectionY)
     *                          / length                                    in [-1, 1]
     *       in front (>= 0) -> 1
     *       behind          -> rearFactor + (1 - rearFactor) * (1 + forwardComponent)
     *   The behind branch interpolates smoothly: at forwardComponent -1 (directly behind) it is
     *   exactly rearFactor, and at 0 (directly beside) it is exactly 1, so a source sweeping around
     *   the player never jumps in level.
     * Edge cases:
     *   length == 0 -> 1 (a source on the player's tile is not "behind" anything).
     *   rearFactor outside [0, 1] would brighten rather than dull a rear source; the constant that
     *   feeds it is fixed in SoundConstants, and the result is clamped to [0, 1] regardless.
     */
    public static float sfxRearFactor(float differenceX, float differenceY,
                                      float facingDirectionX, float facingDirectionY,
                                      float rearFactor) {
        float length = (float) Math.sqrt(differenceX * differenceX + differenceY * differenceY);
        if (length <= 0f) return 1f;
        float forwardComponent =
                (differenceX * facingDirectionX + differenceY * facingDirectionY) / length;
        if (forwardComponent >= 0f) return 1f;
        if (forwardComponent < -1f) forwardComponent = -1f;
        float factor = rearFactor + (1f - rearFactor) * (1f + forwardComponent);
        if (factor > 1f) return 1f;
        if (factor < 0f) return 0f;
        return factor;
    }

    /*
     * Formula: stacked-voice volume within a single turn
     * Derivation:
     *   Turn-based combat has a failure mode real-time games do not: every enemy resolves in the
     *   SAME instant, so five swings are five identical sounds at the same millisecond — not five
     *   attacks but one loud smear.  Damping each successive voice geometrically separates them:
     *       volume = baseVolume * falloff^voicesAlreadyStartedThisTurn
     *   With falloff 0.7, three swings become one loud, one medium and one faint, which the ear
     *   resolves as three events.  Three equally loud ones read as noise.
     * Edge cases:
     *   voicesAlreadyStartedThisTurn <= 0 -> the base volume, unchanged (pow(x, 0) == 1).
     *   falloff <= 0 -> every stacked voice would be silenced outright, so it is treated as 0 for
     *   voices after the first, which is the intended degenerate reading.
     *   falloff >= 1 disables the rule (no damping), which is a legal way to turn it off.
     *   The exponent is small (bounded by the per-turn voice cap), so Math.pow is never hot.
     */
    public static float sfxStackedVolume(float baseVolume, int voicesAlreadyStartedThisTurn,
                                         float falloff) {
        if (voicesAlreadyStartedThisTurn <= 0) return baseVolume;
        if (falloff >= 1f) return baseVolume;
        if (falloff <= 0f) return 0f;
        return baseVolume * (float) Math.pow(falloff, voicesAlreadyStartedThisTurn);
    }

    /*
     * Formula: playback pitch from a repeating cycle plus deterministic jitter
     * Derivation:
     *   Every sound in the catalog is exactly ONE cached WAV, so all variation has to come from the
     *   playback rate.  Pure randomness is wrong here: a random sequence repeats values, and a
     *   chaingun that fires the same pitch twice in a row sounds broken.  So the base is a fixed
     *   CYCLE that is guaranteed never to repeat adjacent entries, and jitter only decorates it:
     *       base   = pitchCycle[playCount mod cycleLength]
     *       spread = 1 + (base - 1) * cycleSpread
     *       jitter = whiteNoiseSample(jitterSeed, playCount) * jitterAmount
     *       pitch  = spread + jitter
     *   cycleSpread scales the cycle's excursion per sound: 0.15 for a big gun that should sound
     *   identical every time, 1.0 for a chaingun that should obviously vary.  Reusing
     *   whiteNoiseSample() for the jitter keeps the whole system deterministic — the same play
     *   count always yields the same pitch, which is what makes an audio bug reproducible.
     * Edge cases:
     *   A null or empty pitchCycle -> return 1 (unmodified playback rate).
     *   Negative playCount is folded to a non-negative index, so a wrapped counter cannot throw.
     *   The result is clamped to [minimumPitch, maximumPitch]; both backends (Android SoundPool and
     *   desktop OpenAL) reject rates outside roughly [0.5, 2.0], and a mis-tuned cycleSpread must
     *   never be able to pass one through.
     */
    public static float sfxPitchFromCycle(int playCount, float cycleSpread, long jitterSeed,
                                          float[] pitchCycle, float jitterAmount,
                                          float minimumPitch, float maximumPitch) {
        if (pitchCycle == null || pitchCycle.length == 0) return 1f;
        int cyclePosition = playCount % pitchCycle.length;
        if (cyclePosition < 0) cyclePosition += pitchCycle.length;

        float base  = pitchCycle[cyclePosition];
        float pitch = 1f + (base - 1f) * cycleSpread
                    + whiteNoiseSample(jitterSeed, playCount) * jitterAmount;

        if (pitch < minimumPitch) return minimumPitch;
        if (pitch > maximumPitch) return maximumPitch;
        return pitch;
    }

    // =========================================================================
    // THE POWER LADDER (balance-overhaul order 1) — weapon level vs floor threat level,
    // rarity, enemy/player growth and the ONE expected-player model (BalanceConfig SECTION 20).
    // =========================================================================

    /*
     * Formula: ladderScale — compound weapon-level damage step (R2)
     * Derivation:
     *   Each weapon level multiplies damage by the ladder growth g:
     *       ladderScale(L) = g ^ (L - 1)
     *   so level 1 is the base stat block and every level is the same RELATIVE step (a level-10
     *   weapon's step is as noticeable as a level-2 weapon's), replacing the old +10% linear rule.
     *   Worked: g = 1.08, L = 5 -> 1.08^4 = 1.360.
     * Edge cases:
     *   level < 1 is clamped to 1 (-> 1.0); growth <= 0 is nonsensical config and returns 1.0.
     */
    public static float ladderScale(float growth, int level) {
        if (growth <= 0f) {
            return 1f;
        }
        return (float) Math.pow(growth, Math.max(1, level) - 1);
    }

    /*
     * Formula: levelGapMultiplier — the lag penalty / ahead bonus (R3)
     * Derivation:
     *   gap = weaponLevel - floorThreatLevel.
     *     gap < 0 : max(floor, penaltyBase ^ (-gap * steepness))   — compound per level behind
     *     gap = 0 : 1.0
     *     gap > 0 : 1 + bonusPerLevel * min(gap, bonusCapLevels)    — small, capped reward ahead
     *   steepness scales the exponent, so 0 disables the penalty and 1 is the designed ladder.
     *   Worked (0.82 / 0.25 / 0.05 / 2): gap -2 -> 0.672, gap -4 -> 0.452, gap -7 -> 0.25 (floor),
     *   gap +1 -> 1.05, gap +5 -> 1.10.
     * Edge cases:
     *   steepness <= 0 -> no penalty (1.0 for any gap <= 0).
     *   floor is applied only below the threat level; the ahead bonus is never negative.
     */
    public static float levelGapMultiplier(int gap, float penaltyBase, float floor,
                                           float bonusPerLevel, int bonusCapLevels, float steepness) {
        if (gap < 0) {
            if (steepness <= 0f) {
                return 1f;
            }
            float penalty = (float) Math.pow(penaltyBase, -gap * steepness);
            return Math.max(floor, penalty);
        }
        if (gap == 0) {
            return 1f;
        }
        return 1f + Math.max(0f, bonusPerLevel) * Math.min(gap, Math.max(0, bonusCapLevels));
    }

    /*
     * Formula: levelGapMultiplier (configured) — R3 with the SECTION 20 defaults
     * Derivation:
     *   Convenience overload reading BalanceConfig's LEVEL_GAP_* constants, so every call site (weapon
     *   damage, the HUD compare card, the audit) resolves the gap through the one configured curve.
     * Edge cases: as the primitive above.
     */
    public static float levelGapMultiplier(int gap) {
        return levelGapMultiplier(gap, BalanceConfig.LEVEL_GAP_PENALTY_BASE, BalanceConfig.LEVEL_GAP_FLOOR,
                BalanceConfig.LEVEL_GAP_BONUS_PER_LEVEL, BalanceConfig.LEVEL_GAP_BONUS_CAP_LEVELS,
                BalanceConfig.LEVEL_GAP_STEEPNESS);
    }

    /*
     * Formula: rarityDamageMultiplier — rarity multiplies damage (R5)
     * Derivation:
     *   A table lookup indexed by the WeaponTier ordinal (COMMON 0 .. LEGENDARY 4):
     *       rarityDamageMultiplier(t) = table[clamp(t, 0, table.length - 1)]
     *   Kept as data so the order-7 feel pass retunes the table, never this method.
     * Edge cases:
     *   empty/null table -> 1.0; an out-of-range ordinal is clamped to the nearest end.
     */
    public static float rarityDamageMultiplier(float[] table, int tierOrdinal) {
        if (table == null || table.length == 0) {
            return 1f;
        }
        int clamped = Math.max(0, Math.min(table.length - 1, tierOrdinal));
        return table[clamped];
    }

    /*
     * Formula: rarityDamageMultiplier (configured) — R5 with BalanceConfig.RARITY_DAMAGE_MULTIPLIER
     * Derivation: the primitive above over the SECTION 20 table.
     * Edge cases: as the primitive above.
     */
    public static float rarityDamageMultiplier(int tierOrdinal) {
        return rarityDamageMultiplier(BalanceConfig.RARITY_DAMAGE_MULTIPLIER, tierOrdinal);
    }

    /*
     * Formula: weaponLadderDamage — the ONE composition of a player hit's ladder factors (R2, R4)
     * Derivation:
     *   perHit = base * LADDER_GROWTH^(L-1) * rarityMultiplier(tier) * levelGapMultiplier(L - d)
     *   (then the existing modifiers — falloff, marksmanship, cards, crits — are applied by their owners).
     *   gapExempt (the FIST, R4) treats the weapon as exactly on-level: its level is the floor's threat
     *   level and it takes no gap penalty, so it is always the never-softlock backstop (S-SOFTLOCK).
     *   Worked: Assault Rifle 20, L4, UNCOMMON, floor 4 -> 20 * 1.2597 * 1.10 * 1.0 = 27.7.
     *           Same gun at L2 on floor 4 -> 20 * 1.08 * 1.10 * 0.6724 = 16.0.
     * Edge cases:
     *   floorThreatLevel < 1 is clamped to 1; weaponLevel < 1 is clamped to 1.
     *   baseDamage <= 0 -> 0 (a non-damaging weapon stays non-damaging).
     */
    public static float weaponLadderDamage(float baseDamage, int weaponLevel, int tierOrdinal,
                                           int floorThreatLevel, boolean gapExempt) {
        if (baseDamage <= 0f) {
            return 0f;
        }
        int threatLevel = Math.max(1, floorThreatLevel);
        int level = gapExempt ? threatLevel : Math.max(1, weaponLevel);
        float gapMultiplier = gapExempt ? 1f : levelGapMultiplier(level - threatLevel);
        return baseDamage * ladderScale(BalanceConfig.LADDER_GROWTH, level)
                * rarityDamageMultiplier(tierOrdinal) * gapMultiplier;
    }

    /*
     * Formula: enemyHealthAtDepth — compound enemy HP growth (R7)
     * Derivation:
     *   maxHP(d) = baseHP * ENEMY_HEALTH_GROWTH ^ (d - 1)
     *   Fitted (with enemyDamageAtDepth) so the on-curve player's turns-to-kill stays within
     *   +/-15% of depth 1 at every depth 1..RUN_FINAL_DEPTH (R-LADDER L1).
     * Edge cases: depth < 1 is clamped to 1 (-> baseHP).
     */
    public static float enemyHealthAtDepth(float baseHealth, int depth) {
        return baseHealth * compoundDepthMultiplier(BalanceConfig.ENEMY_HEALTH_GROWTH, Math.max(1, depth));
    }

    /*
     * Formula: enemyDamageAtDepth — compound enemy damage growth (R7)
     * Derivation:
     *   damage(d) = baseDamage * ENEMY_DAMAGE_GROWTH ^ (d - 1)
     *   Fitted against the player's vitality growth so on-curve turns-to-die stays within +/-15%.
     * Edge cases: depth < 1 is clamped to 1 (-> baseDamage).
     */
    public static float enemyDamageAtDepth(float baseDamage, int depth) {
        return baseDamage * compoundDepthMultiplier(BalanceConfig.ENEMY_DAMAGE_GROWTH, Math.max(1, depth));
    }

    /*
     * Formula: playerVitalityScale — max HP / max armour growth per character level (R9)
     * Derivation:
     *   vitality(L) = PLAYER_VITALITY_GROWTH ^ (L - 1); max HP = PLAYER_MAX_HEALTH * vitality(L)
     *   (+ card/stat bonuses, which stay flat), max armour likewise.
     * Edge cases: characterLevel < 1 is clamped to 1 (-> 1.0).
     */
    public static float playerVitalityScale(int characterLevel) {
        return ladderScale(BalanceConfig.PLAYER_VITALITY_GROWTH, characterLevel);
    }

    /*
     * Formula: fractionOfMaximum — a heal/armour pickup resolved against the CURRENT max (R10)
     * Derivation:
     *   restored = max(1, round(maximum * fraction))
     *   Resolved at the moment of use, so a level-up that raised max HP this turn is honoured.
     *   Worked: 130 max HP * 0.45 = 58.5 -> 59.
     * Edge cases:
     *   maximum <= 0 or fraction <= 0 -> 0 (nothing to restore); otherwise at least 1.
     */
    public static int fractionOfMaximum(int maximum, float fraction) {
        if (maximum <= 0 || fraction <= 0f) {
            return 0;
        }
        return Math.max(1, Math.round(maximum * fraction));
    }

    /*
     * Formula: expectedRarityMultiplierAtDepth — the rarity an on-curve player carries (R11)
     * Derivation:
     *   The region's dropped-weapon tier band [WEAPON_DROP_TIER_MIN, _MAX]_BY_REGION (ordinals clamped
     *   to the rarity table) is averaged at its two ends — the same (min + max) / 2 reading the
     *   order-4 ability budget uses:
     *       expectedRarity(d) = (rarity(minTier(r)) + rarity(maxTier(r))) / 2,  r = region(d)
     *   Worked: region 0 {COMMON, UNCOMMON} -> (1.00 + 1.10) / 2 = 1.05.
     * Edge cases: depths past the last band clamp to the last band (the deepest region's gear).
     */
    public static float expectedRarityMultiplierAtDepth(int depth) {
        int[] minimums = BalanceConfig.WEAPON_DROP_TIER_MIN_BY_REGION;
        int[] maximums = BalanceConfig.WEAPON_DROP_TIER_MAX_BY_REGION;
        int region = Math.min(minimums.length - 1,
                regionIndexAtDepth(Math.max(1, depth), BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE));
        return (rarityDamageMultiplier(minimums[region]) + rarityDamageMultiplier(maximums[region])) / 2f;
    }

    /*
     * Formula: ladderReferenceHitDamage — the R8 reference hit at depth 1
     * Derivation:
     *   The Assault Rifle (COMMON, L1) per-hit damage at LADDER_REFERENCE_RANGE_TILES, falloff included,
     *   through the same linear falloff the weapon uses in play:
     *       hit = ASSAULT_RIFLE_DAMAGE * clamp(1 - dropCoeff * range, DAMAGE_MIN_MULTIPLIER, 1)
     *   Worked: 20 * (1 - 0.08 * 3) = 15.2.
     * Edge cases: none (all inputs are constants).
     */
    public static float ladderReferenceHitDamage() {
        return BalanceConfig.ASSAULT_RIFLE_DAMAGE * damageDropMultiplier(BalanceConfig.ASSAULT_RIFLE_DAMAGE_DROP_COEFF,
                BalanceConfig.LADDER_REFERENCE_RANGE_TILES, BalanceConfig.DAMAGE_MIN_MULTIPLIER);
    }

    /*
     * Formula: expectedPlayer — the expected-player model at a depth, optionally off-curve (R11)
     * Derivation:
     *   Weapon side (per-hit, relative to depth 1):
     *       weaponFactor = LADDER_GROWTH^(wL-1) * rarity * levelGapMultiplier(wL - d)
     *       offenceLift  = 1 + OFFENCE_FRACTION * EXPECTED_PERK_POWER_PP_PER_PICK * (cL - 1) / 100   (PP = % DPT; the
     *                    expected perk power at the expected rank count — balance-overhaul order 5)
     *       relative     = weaponFactor * offenceLift / expectedRarity(1)
     *   so the depth-1 ON-CURVE player (wL 1, cL 1, rarity = expectedRarity(1)) reads exactly 1.0:
     *       damagePerTurn      = REFERENCE_PLAYER_DPT      * relative   (the fixed depth-1 anchor)
     *       referenceHitDamage = ladderReferenceHitDamage() * relative  (the R8 hits yardstick)
     *   Survival side (R9 — vitality multiplies max HP/armour, cards stay flat):
     *       maxHealth = PLAYER_MAX_HEALTH * vitality(cL), maxArmor = PLAYER_MAX_ARMOR * vitality(cL)
     *       eHP       = maxHealth + maxArmor + REFERENCE_PLAYER_EHP * DEFENCE_FRACTION * PP * (cL-1) / 100
     *   Offence cards are modelled multiplicatively (the established "PP = %-gain" convention the boss
     *   derivation already uses), so the lag ratios R-LADDER L2-L4 compare WEAPONS at equal character.
     * Edge cases:
     *   depth, weaponLevel, characterLevel < 1 are clamped to 1; a rarity multiplier <= 0 reads as 0 DPT.
     */
    public static ExpectedPlayer expectedPlayer(int depth, int weaponLevel, float rarityMultiplier,
                                                int characterLevel) {
        int clampedDepth = Math.max(1, depth);
        int clampedWeaponLevel = Math.max(1, weaponLevel);
        int clampedCharacterLevel = Math.max(1, characterLevel);
        float gapMultiplier = levelGapMultiplier(clampedWeaponLevel - clampedDepth);
        float weaponFactor = ladderScale(BalanceConfig.LADDER_GROWTH, clampedWeaponLevel)
                * Math.max(0f, rarityMultiplier) * gapMultiplier;
        float offenceLift = 1f + BalanceConfig.LADDER_EXPECTED_OFFENCE_BUDGET_FRACTION
                * BalanceConfig.EXPECTED_PERK_POWER_PP_PER_PICK * (clampedCharacterLevel - 1) / 100f;
        float relative = weaponFactor * offenceLift / expectedRarityMultiplierAtDepth(1);

        float vitality = playerVitalityScale(clampedCharacterLevel);
        float maxHealth = BalanceConfig.PLAYER_MAX_HEALTH * vitality;
        float maxArmor = BalanceConfig.PLAYER_MAX_ARMOR * vitality;
        float perkEffectiveHitPoints = BalanceConfig.REFERENCE_PLAYER_EHP
                * BalanceConfig.LADDER_EXPECTED_DEFENCE_BUDGET_FRACTION
                * BalanceConfig.EXPECTED_PERK_POWER_PP_PER_PICK * (clampedCharacterLevel - 1) / 100f;
        return new ExpectedPlayer(clampedDepth, clampedWeaponLevel, rarityMultiplier, gapMultiplier,
                clampedCharacterLevel, BalanceConfig.REFERENCE_PLAYER_DPT * relative,
                ladderReferenceHitDamage() * relative, maxHealth, maxArmor,
                maxHealth + maxArmor + perkEffectiveHitPoints);
    }

    /*
     * Formula: expectedCharacterLevelAtDepth — the on-curve character level (R11)
     * Derivation: cL(d) = round(1 + EXPECTED_LEVELS_PER_DEPTH * (d - 1)), at least 1.
     * Edge cases: depth < 1 is clamped to 1.
     */
    public static int expectedCharacterLevelAtDepth(int depth) {
        return Math.max(1, Math.round(1f + BalanceConfig.EXPECTED_LEVELS_PER_DEPTH * (Math.max(1, depth) - 1)));
    }

    /*
     * Formula: expectedPlayerAtDepth — THE on-curve player (R11)
     * Derivation:
     *   expectedPlayer(d, weaponLevel = d, rarity = expectedRarity(d), characterLevel = cL(d)).
     *   Read by the audit (R-LADDER), the simulator and the boss derivation — one model, one yardstick.
     * Edge cases: depth < 1 is clamped to 1.
     */
    public static ExpectedPlayer expectedPlayerAtDepth(int depth) {
        int clampedDepth = Math.max(1, depth);
        return expectedPlayer(clampedDepth, clampedDepth, expectedRarityMultiplierAtDepth(clampedDepth),
                expectedCharacterLevelAtDepth(clampedDepth));
    }

    /*
     * Formula: ladderTurnsToKill — continuous hits-to-kill for the ladder audit (R12)
     * Derivation:
     *   turns = targetEffectiveHitPoints / damagePerHit, UN-rounded, so ratio checks (lag, ahead,
     *   on-curve drift) are not hidden by the one-hit quantisation of a small kill. The integer
     *   hits a player actually counts is ceil(turns) (GameMath.turnsToKill).
     * Edge cases: damagePerHit <= 0 -> Float.POSITIVE_INFINITY (never dies).
     */
    public static float ladderTurnsToKill(float targetEffectiveHitPoints, float damagePerHit) {
        if (damagePerHit <= 0f) {
            return Float.POSITIVE_INFINITY;
        }
        return targetEffectiveHitPoints / damagePerHit;
    }

    /*
     * Formula: expectedHitGrowthAtDepth — how much harder the on-curve player hits than at depth 1 (R11)
     * Derivation:
     *   growth(d) = expectedPlayerAtDepth(d).referenceHitDamage / expectedPlayerAtDepth(1).referenceHitDamage
     *   = ladder x rarity x offence-card lift, normalised to 1.0 at depth 1. Ammo supply (each unit buys
     *   this much more damage) and the route model's guaranteed-box value ride it.
     * Edge cases: depth < 1 is clamped to 1 (-> 1.0).
     */
    public static float expectedHitGrowthAtDepth(int depth) {
        return expectedPlayerAtDepth(depth).referenceHitDamage / expectedPlayerAtDepth(1).referenceHitDamage;
    }

    /*
     * Formula: ladderCouplingAtCharacterLevel — a real character level vs the on-curve one (R11)
     * Derivation:
     *   With the weapon held on the ladder (level d, the region's expected rarity), compare the total
     *   power (DPT x eHP) of a player at the character level their XP actually bought against the
     *   on-curve player at the same depth:
     *       coupling = (dpt(d, cL) * eHP(cL)) / (dpt(d, cL(d)) * eHP(cL(d)))
     *   1.0 = exactly on the curve; below = under-levelled for the floor; above = ahead of it. The route
     *   trajectory audit reads it at the level a journey's XP bought (replaces the retired v2 coupling).
     * Edge cases: characterLevel < 1 is clamped to 1; depth < 1 is clamped to 1.
     */
    public static float ladderCouplingAtCharacterLevel(int depth, int characterLevel) {
        int clampedDepth = Math.max(1, depth);
        ExpectedPlayer onCurve = expectedPlayerAtDepth(clampedDepth);
        ExpectedPlayer actual = expectedPlayer(clampedDepth, clampedDepth,
                expectedRarityMultiplierAtDepth(clampedDepth), Math.max(1, characterLevel));
        float onCurvePower = onCurve.damagePerTurn * onCurve.effectiveHitPoints;
        return onCurvePower <= 0f ? 0f : (actual.damagePerTurn * actual.effectiveHitPoints) / onCurvePower;
    }


    // =========================================================================
    // SUPPLY & DENSITY (balance-overhaul order 2) — the planner's arithmetic.
    // -------------------------------------------------------------------------
    // Supply is DERIVED from the floor's planned roster (demand) and the node spec, never from the
    // player's current state. Every number the SupplyPlanner / EncounterBudgetPlanner / R-SUPPLY /
    // R-DENSITY audit compute goes through these methods.
    // =========================================================================

    /*
     * Formula: supplyDamagePerUnitAtDepth — what one ammo unit is worth to the on-curve player (S2)
     * Derivation:
     *   An ammo unit buys one shot of the weapon that eats it; on the ladder that shot grows with the
     *   on-curve player's hit growth (order 1, R11):
     *       damagePerUnit(d) = depthOneDamagePerUnit * expectedHitGrowthAtDepth(d)
     *   So the same planned DAMAGE needs fewer units as the player climbs — the box count stays
     *   depth-stable while demand and damage grow together.
     * Edge cases: depthOneDamagePerUnit <= 0 -> 0 (an ammo type with no damage value supplies nothing).
     */
    public static float supplyDamagePerUnitAtDepth(float depthOneDamagePerUnit, int depth) {
        if (depthOneDamagePerUnit <= 0f) {
            return 0f;
        }
        return depthOneDamagePerUnit * expectedHitGrowthAtDepth(depth);
    }

    /*
     * Formula: floorAmmoDemandUnits — ammo units a roster costs the expected player (S2)
     * Derivation:
     *   Killing the roster means dealing its total eHP; each unit of an ammo type deals that type's
     *   per-unit damage at this depth:
     *       demandUnits = rosterEffectiveHitPoints / damagePerUnitAtDepth
     *   The planner multiplies the DAMAGE plan by the node's ammoRatio and the type's share BEFORE
     *   converting to units, so this is the conversion step.
     *   Worked: 600 eHP / 20 damage per bullet = 30 bullets (3 boxes of 10).
     * Edge cases: damagePerUnitAtDepth <= 0 or eHP <= 0 -> 0 units (nothing to buy / no value per unit).
     */
    public static float floorAmmoDemandUnits(float rosterEffectiveHitPoints, float damagePerUnitAtDepth) {
        if (rosterEffectiveHitPoints <= 0f || damagePerUnitAtDepth <= 0f) {
            return 0f;
        }
        return rosterEffectiveHitPoints / damagePerUnitAtDepth;
    }

    /*
     * Formula: floorExpectedIncomingDamage — the heal-economy incoming model, re-based (S3)
     * Derivation:
     *   The order-3 model (incomingDamagePerFloor) read a FIXED model floor at depth 1. Re-based, it
     *   reads the floor's ACTUAL roster at its depth and is expressed against the order-1 expected
     *   player, in fractions of that player's max HP:
     *       rosterDpt(d) = sum over planned enemies of (attackDamage / attackCadence) * damageGrowth(d)
     *       incoming     = rosterDpt(d) * turnsEngagedPerEnemy * (1 - avoidanceFactor)
     *       fraction     = incoming / expectedPlayer.maxHealth
     *   The roster's depth-1 DPT is summed by the caller (rosterBaseDamagePerTurn); enemyDamageAtDepth
     *   is linear in its base, so scaling the sum equals summing the scaled members.
     * Edge cases: avoidance clamped to [0, 0.99]; turnsEngaged < 0 -> 0; maxHealth <= 0 -> 0.
     */
    public static float floorExpectedIncomingDamage(float rosterBaseDamagePerTurn, int depth,
                                                    ExpectedPlayer expectedPlayer,
                                                    float turnsEngagedPerEnemy, float avoidanceFactor) {
        if (expectedPlayer == null || expectedPlayer.maxHealth <= 0f) {
            return 0f;
        }
        float incoming = incomingDamagePerFloor(enemyDamageAtDepth(rosterBaseDamagePerTurn, depth),
                turnsEngagedPerEnemy, avoidanceFactor);
        return incoming / expectedPlayer.maxHealth;
    }

    /*
     * Formula: plannedHealValue — heal value a floor hands back, in fractions of max HP (S3 + S4)
     * Derivation:
     *   The node decides how much of the modelled incoming damage the floor deliberately leaves
     *   UNCOVERED (drainTarget, 0.20 on COMBAT, negative on CACHE = a net gain):
     *       value = incomingFraction * (1 - drainTarget)
     *   and the HEAL FLOOR (S4) lifts it to at least healFloorFraction on every non-exempt floor:
     *       value = max(value, healFloorFraction)   when the floor applies
     * Edge cases: negative incoming -> treated as 0; result never negative.
     */
    public static float plannedHealValue(float incomingFraction, float drainTarget,
                                         float healFloorFraction, boolean healFloorApplies) {
        float value = Math.max(0f, incomingFraction) * (1f - drainTarget);
        if (healFloorApplies) {
            value = Math.max(value, healFloorFraction);
        }
        return Math.max(0f, value);
    }

    /*
     * Formula: bodyTargetAtDepth — how many bodies a floor fills (E1)
     * Derivation:
     *   The target band rises LINEARLY from [minAtOne, maxAtOne] at depth 1 to [minDeep, maxDeep] at
     *   deepDepth and is held beyond it:
     *       t     = clamp((d - 1) / (deepDepth - 1), 0, 1)
     *       low   = minAtOne + t * (minDeep - minAtOne)
     *       high  = maxAtOne + t * (maxDeep - maxAtOne)
     *       value = low + unitRoll * (high - low)
     *   unitRoll in [0,1) is the floor's seeded roll, so the same floor always fills the same count.
     *   Independent of per-enemy cost: the Threat-Point budget is now a CAP, the body count the target.
     * Edge cases: deepDepth <= 1 -> t = 1; unitRoll clamped to [0, 1]; depth < 1 treated as 1.
     */
    public static float bodyTargetAtDepth(int depth, float unitRoll, int minAtOne, int maxAtOne,
                                          int minDeep, int maxDeep, int deepDepth) {
        float progress = deepDepth <= 1 ? 1f
                : Math.max(0f, Math.min(1f, (Math.max(1, depth) - 1) / (float) (deepDepth - 1)));
        float low  = minAtOne + progress * (minDeep - minAtOne);
        float high = maxAtOne + progress * (maxDeep - maxAtOne);
        float roll = Math.max(0f, Math.min(1f, unitRoll));
        return low + roll * (high - low);
    }

    /*
     * Formula: densityPerHundredTiles — enemies per 100 walkable tiles (E7)
     * Derivation:
     *       density = enemyCount * 100 / walkableTiles
     *   COMBAT floors sit in [2.2, 4.0], ELITE in [3.0, 5.0], CACHE / SHOP in [0.6, 1.5] — the measure of
     *   "big but empty" the footprint rule (E6) and the body target (E1) are tuned against together.
     * Edge cases: walkableTiles <= 0 -> 0 (no floor to measure).
     */
    public static float densityPerHundredTiles(int enemyCount, int walkableTiles) {
        if (walkableTiles <= 0) {
            return 0f;
        }
        return enemyCount * 100f / walkableTiles;
    }

    /*
     * Formula: footprintTargetWalkableTiles — the walkable-tile target a combat layout builds to (E6)
     * Derivation:
     *   The owner sets the size as a CUT from the layout's ORIGINAL walkable size, ramped by depth
     *   (deeper floors reuse the last entry of the table):
     *       cut    = reductionByDepth[min(depth, length) - 1]
     *       target = round(originalWalkableTiles * (1 - cut))
     *   e.g. ROOMS_MST 1130 -> 565 on floor 1 (cut 0.50), 904 from floor 5 (cut 0.20).
     * Edge cases: empty table or originalWalkableTiles <= 0 -> 0 (no target: natural size); depth < 1
     *   treated as 1; the cut is clamped to [0, 1].
     */
    public static int footprintTargetWalkableTiles(int originalWalkableTiles, float[] reductionByDepth, int depth) {
        if (reductionByDepth == null || reductionByDepth.length == 0 || originalWalkableTiles <= 0) {
            return 0;
        }
        int index = Math.min(reductionByDepth.length, Math.max(1, depth)) - 1;
        float cut = Math.max(0f, Math.min(1f, reductionByDepth[index]));
        return Math.round(originalWalkableTiles * (1f - cut));
    }

    // =====================================================================================
    // MATCHUPS (balance-overhaul order 3) — damage class x enemy trait
    // =====================================================================================

    /*
     * Formula: Matchup multiplier at a global strength
     * Derivation:
     *   The matchup table stores a raw multiplier r per (DamageClass, EnemyTrait) cell (M3).
     *   A single dial s (BalanceConfig.MATCHUP_STRENGTH) scales how pronounced EVERY matchup is
     *   without editing the table, symmetrically in log space:
     *       multiplier = r ^ s
     *   s = 1 -> the table as written; s = 0 -> r^0 = 1 for every cell (all matchups flattened);
     *   s = 0.5 -> sqrt(r), so 1.5 becomes ~1.22 and 0.65 becomes ~0.81 (bonus and penalty shrink
     *   by the same factor in log space, which a linear blend would not do).
     * Edge cases:
     *   r <= 0 is not a legal table value (it would zero or invert damage); it is treated as the
     *   neutral 1.0. s <= 0 returns 1.0 exactly (no pow call, no -0/NaN drift). r == 1 returns 1.0.
     */
    public static float matchupMultiplier(float rawMultiplier, float strength) {
        if (rawMultiplier <= 0f || strength <= 0f || rawMultiplier == 1f) {
            return 1f;
        }
        if (strength == 1f) {
            return rawMultiplier;
        }
        return (float) Math.pow(rawMultiplier, strength);
    }

    /*
     * Formula: Matchup classification (M4)
     * Derivation:
     *   The communication layer (hit words, glyph colour, switch hint, compare card) speaks in three
     *   words, never numbers. With effective threshold E and resisted threshold R (R < 1 < E):
     *       multiplier >= E          -> EFFECTIVE  (+1)
     *       multiplier <= R          -> RESISTED   (-1)
     *       R < multiplier < E       -> NEUTRAL    ( 0)
     *   The EFFECTIVE test runs first, so a mis-configured E <= R still yields a defined answer.
     * Edge cases:
     *   Pass the STRENGTH-ADJUSTED multiplier, so MATCHUP_STRENGTH = 0 classifies every cell NEUTRAL.
     *   NaN fails both comparisons and reads NEUTRAL. Boundaries are inclusive (1.3 is EFFECTIVE,
     *   0.8 is RESISTED) per M4.
     */
    public static int classifyMatchup(float multiplier, float effectiveThreshold, float resistedThreshold) {
        if (multiplier >= effectiveThreshold) {
            return MATCHUP_CLASS_EFFECTIVE;
        }
        if (multiplier <= resistedThreshold) {
            return MATCHUP_CLASS_RESISTED;
        }
        return MATCHUP_CLASS_NEUTRAL;
    }

    /*
     * Formula: Matchup reference multiplier (trait-aware TP pricing)
     * Derivation:
     *   An archetype's Threat Points price how long the REFERENCE player takes to kill it. The
     *   reference player's generalist (BALLISTIC) hits a trait at multiplier m, but the on-curve
     *   player often carries a second gun that is not resisted, so only a weight w in [0, 1] of the
     *   generalist's matchup is priced — a linear blend toward neutral:
     *       referenceMultiplier = 1 + w x (m - 1)
     *   w = 0 -> 1 (trait-blind, the pre-order-3 pricing); w = 1 -> m (rifle-only player).
     * Edge cases:
     *   w is clamped to [0, 1]. m <= 0 is not a legal table value and is treated as neutral 1.0, so
     *   the result is always > 0 and safe to divide by.
     */
    public static float matchupReferenceMultiplier(float generalistMultiplier, float referenceWeight) {
        if (generalistMultiplier <= 0f) {
            return 1f;
        }
        float weight = Math.max(0f, Math.min(1f, referenceWeight));
        return 1f + weight * (generalistMultiplier - 1f);
    }

    /*
     * Formula: Trait-adjusted enemy effective HP (trait-aware TP pricing)
     * Derivation:
     *   Damage into the enemy is multiplied by the reference multiplier r (above), so the reference
     *   player must deal eHP / r of its own nominal damage to kill it — equivalently, the enemy's
     *   eHP measured in the player's nominal (pre-matchup) damage is
     *       eHP_priced = eHP / r
     *   r < 1 (resisted) inflates the priced eHP; r > 1 (effective) deflates it; r = 1 leaves it.
     * Edge cases:
     *   r <= 0 returns eHP unchanged (treated as neutral) — never a division by zero or a negative.
     */
    public static float traitAdjustedEnemyEffectiveHitPoints(float effectiveHitPoints, float referenceMultiplier) {
        if (referenceMultiplier <= 0f) {
            return effectiveHitPoints;
        }
        return effectiveHitPoints / referenceMultiplier;
    }

    /*
     * Formula: Shotgun falloff by tile (W1/W2)
     * Derivation:
     *   A spread weapon's damage fraction is a designer TABLE indexed by tile distance, not a linear
     *   coefficient, because the role wants a cliff (100% / 85% at 1-2 tiles, then 55 / 30 / 15):
     *       fraction(d) = table[d - 1]   for 1 <= d <= table.length
     *       fraction(d) = 0              otherwise (outside the weapon's reach)
     *   The final hit is base x ladder x fraction(d) x fireCycleMultiplier (Weapon.damageWithFalloff).
     * Edge cases:
     *   d <= 0 (the player's own tile) or d > length -> 0. A null or empty table -> 0. Table values are
     *   used as written (no clamp), so a misconfigured value > 1 surfaces in the audit, not here.
     */
    public static float shotgunFalloffAtTile(float[] falloffByTile, int distanceTiles) {
        if (falloffByTile == null || distanceTiles < 1 || distanceTiles > falloffByTile.length) {
            return 0f;
        }
        return falloffByTile[distanceTiles - 1];
    }

    /*
     * Formula: Incinerator burn per stack (W3)
     * Derivation:
     *   Each burn stack ticks a fixed FRACTION of the weapon's ladder-scaled impact hit, so the burn
     *   scales with the weapon level exactly as the impact does:
     *       perStack = max(1, round(ladderScaledHit x burnFraction))
     *   Up to FLAME_BURN_MAX_STACKS stacks tick together: per-turn burn = perStack x stacks.
     * Edge cases:
     *   The floor of 1 keeps a burn from ever ticking 0 (an applied burn is always felt). A negative or
     *   zero fraction still returns 1 — a fraction of 0 is not a supported "burn off" switch.
     */
    public static int incineratorBurnPerStack(float ladderScaledHit, float burnFraction) {
        return Math.max(1, Math.round(ladderScaledHit * burnFraction));
    }

    // =====================================================================================
    // R-ROLE SCENARIO MODEL (balance-overhaul order 3) — turns to clear a reference scenario
    // =====================================================================================

    /*
     * Formula: Role-scenario mean hit over an engagement band
     * Derivation:
     *   A scenario engages at every tile of [distanceMin, distanceMax] with equal weight, so the hit one
     *   action lands on a target is the band MEAN of the weapon's per-tile base hit, times its
     *   expected-value accuracy, its hits per action, the ladder scale and the matchup:
     *       hit = (1 / n) x sum_{d = min..max} baseHit(d) x accuracy x hitsPerAction x scale x matchup
     *   with n = max - min + 1. A tile outside the weapon's reach contributes 0 (it cannot fire there).
     * Edge cases: an inverted band (max < min) is treated as the single tile min. A weapon with no reach
     *   anywhere in the band returns 0, which the clear model reads as "cannot clear".
     */
    public static float roleScenarioMeanHit(RoleScenarioWeapon weapon, int distanceMin, int distanceMax,
                                            float damageScale, float matchupMultiplier) {
        int upper = Math.max(distanceMin, distanceMax);
        float sum = 0f;
        for (int distance = distanceMin; distance <= upper; distance++) {
            sum += weapon.baseHitAt(distance);
        }
        float meanBaseHit = sum / (upper - distanceMin + 1);
        return meanBaseHit * weapon.accuracy * weapon.hitsPerAction * damageScale * matchupMultiplier;
    }

    /*
     * Formula: Role-scenario sustained damage per turn over a band (R-ROLE-4)
     * Derivation:
     *   One clip cycle is clipActions fire actions (each preceded by chargeTurns charge turns) plus
     *   reloadTurns reload turns, so the per-turn output a player sustains standing and firing is
     *       sustained = clipActions x hit / (clipActions x (1 + chargeTurns) + reloadTurns)
     *   with hit = roleScenarioMeanHit at matchup 1 (R-ROLE-4 compares equal level and rarity vs FLESH).
     * Edge cases: the cycle is never 0 turns (clipActions >= 1), so there is no division by zero.
     */
    public static float roleScenarioSustainedDamagePerTurn(RoleScenarioWeapon weapon, int distanceMin,
                                                           int distanceMax) {
        float hit = roleScenarioMeanHit(weapon, distanceMin, distanceMax, 1f, 1f);
        int cycleTurns = weapon.clipActions * (1 + weapon.chargeTurns) + weapon.reloadTurns;
        return weapon.clipActions * hit / cycleTurns;
    }

    /*
     * Formula: Role-scenario turns to clear (R-ROLE-1/2/3/5)
     * Derivation:
     *   A deterministic expected-value walk, one player turn at a time, until every member of the group
     *   is dead or the turn cap is reached. Every member starts with enemyHitPoints and stands in the
     *   engagement band (each hit uses the band-mean hit — roleScenarioMeanHit). A turn is exactly one of:
     *     RELOAD  — the clip is empty: reloadTurns turns pass, then the clip is full again;
     *     CHARGE  — the weapon must charge chargeTurns turns before each shot (Railgun: 1);
     *     FIRE    — one action, spread over the living members by the weapon's pattern:
     *                 SINGLE the first; PIERCE the first laneTargets; CONE all; SPLASH the first at the
     *                 centre hit and every other member at splashNeighbourHit; CHAIN the first, then up
     *                 to chainJumps others at primaryHit x chainMultiplier^jump x max(1, matchup).
     *               A burning weapon adds one stack (cap burnMaxStacks, timer reset to burnTurns) to every
     *               member it hits that survives.
     *   At the end of EVERY turn each living member with burn ticks
     *       stacks x max(1, round(burnImpactHit x scale x burnFraction)) x matchup
     *   (GameMath.incineratorBurnPerStack, matchup pre-multiplied as in EnemyManager).
     *   turnsToClear = (turn of the last kill - 1) + before / damage of the killing event, so the last turn
     *   counts only the fraction it needed — that is what keeps near-equal weapons from tying on integers.
     *   BUILD TERMS (balance-overhaul order 5; all neutral on the order-3 rows, so R-ROLE is unchanged):
     *     first hit on a member      hit + (hit / hitsPerAction) x firstHitBonus  (one bullet of a burst)
     *     PIERCE k-th member         hit x (1 + pierceBonusPerTarget x k)
     *     arc                        one more living member past the pattern takes primaryHit x arcFraction
     *     burning member             hit and tick x (1 + burningDamageBonus)
     *     Wildfire                   a burning member that dies gives every other living member its full stacks
     * Edge cases: a weapon with no reach in the band, or one that cannot clear inside turnCap, returns
     *   cleared = false and turnsToClear = turnCap. groupSize < 1 is treated as 1; laneTargets < 1 as 1.
     *   Neutral build terms multiply by exactly 1 / add exactly 0, so the order-3 table is bit-identical.
     */
    public static RoleScenarioOutcome roleScenarioClear(RoleScenarioWeapon weapon, int groupSize, int laneTargets,
                                                        int distanceMin, int distanceMax, float enemyHitPoints,
                                                        float damageScale, float matchupMultiplier, int turnCap) {
        int members = Math.max(1, groupSize);
        float[] health = new float[members];
        int[] burnStacks = new int[members];
        int[] burnRemaining = new int[members];
        int[] hitsLanded = new int[members];
        java.util.Arrays.fill(health, enemyHitPoints);
        float primaryHit = roleScenarioMeanHit(weapon, distanceMin, distanceMax, damageScale, matchupMultiplier);
        float neutralPrimaryHit = roleScenarioMeanHit(weapon, distanceMin, distanceMax, damageScale, 1f);
        float neighbourHit = weapon.splashNeighbourHit * weapon.accuracy * damageScale * matchupMultiplier;
        float burnPerStack = weapon.burns()
                ? incineratorBurnPerStack(weapon.burnImpactHit * damageScale, weapon.burnFraction) * matchupMultiplier
                : 0f;
        if (primaryHit <= 0f) {
            return new RoleScenarioOutcome(turnCap, 0, 0, false);
        }
        int clip = weapon.clipActions;
        int reloadRemaining = 0;
        int charged = 0;
        int actions = 0;
        float[] lastKill = {-1f};
        for (int turn = 1; turn <= turnCap; turn++) {
            if (reloadRemaining > 0) {
                reloadRemaining--;
                if (reloadRemaining == 0) clip = weapon.clipActions;
            } else if (charged < weapon.chargeTurns) {
                charged++;
            } else {
                charged = 0;
                fireRoleScenarioAction(weapon, health, burnStacks, burnRemaining, hitsLanded, members, laneTargets,
                        primaryHit, neutralPrimaryHit, neighbourHit, matchupMultiplier, turn, lastKill);
                actions++;
                clip--;
                if (clip == 0) {
                    if (weapon.reloadTurns == 0) clip = weapon.clipActions;
                    else reloadRemaining = weapon.reloadTurns;
                }
            }
            if (burnPerStack > 0f) {
                for (int member = 0; member < members; member++) {
                    if (health[member] <= 0f || burnRemaining[member] <= 0) continue;
                    // A ticking member is burning by definition: the PYROMANIAC bonus rides every tick.
                    float tick = burnPerStack * burnStacks[member] * (1f + weapon.burningDamageBonus);
                    applyRoleScenarioDamage(health, member, tick, members, turn, lastKill);
                    burnRemaining[member]--;
                    if (health[member] <= 0f) igniteRoleScenarioNeighbours(weapon, health, burnStacks, burnRemaining, members, burnStacks[member]);
                }
            }
            if (lastKill[0] >= 0f) {
                return new RoleScenarioOutcome(lastKill[0], actions, actions * weapon.ammoPerAction, true);
            }
        }
        return new RoleScenarioOutcome(turnCap, actions, actions * weapon.ammoPerAction, false);
    }

    private static void fireRoleScenarioAction(RoleScenarioWeapon weapon, float[] health, int[] burnStacks,
                                               int[] burnRemaining, int[] hitsLanded, int members, int laneTargets,
                                               float primaryHit, float neutralPrimaryHit, float neighbourHit,
                                               float matchupMultiplier, int turn, float[] lastKill) {
        int targetsHit = 0;
        int lane = Math.max(1, laneTargets);
        int member = 0;
        for (; member < members; member++) {
            if (health[member] <= 0f) continue;
            float hit;
            boolean patternDone = false;
            switch (weapon.pattern) {
                case SINGLE:
                    patternDone = targetsHit >= 1;
                    hit = primaryHit;
                    break;
                case PIERCE:
                    patternDone = targetsHit >= lane;
                    hit = primaryHit * (1f + weapon.pierceBonusPerTarget * targetsHit);
                    break;
                case SPLASH:
                    hit = targetsHit == 0 ? primaryHit : neighbourHit;
                    break;
                case CHAIN:
                    patternDone = targetsHit > weapon.chainJumps;
                    hit = targetsHit == 0 ? primaryHit
                            : neutralPrimaryHit * (float) Math.pow(weapon.chainMultiplier, targetsHit)
                                    * Math.max(1f, matchupMultiplier);
                    break;
                case CONE:
                default:
                    hit = primaryHit;
                    break;
            }
            if (patternDone) break;
            landRoleScenarioHit(weapon, health, burnStacks, burnRemaining, hitsLanded, members, member, hit,
                    turn, lastKill, true);
            targetsHit++;
        }
        // STORM CONDUIT: one more living member past the pattern takes a fraction of the primary hit.
        if (weapon.arcFraction > 0f) {
            for (int other = member; other < members; other++) {
                if (health[other] <= 0f) continue;
                landRoleScenarioHit(weapon, health, burnStacks, burnRemaining, hitsLanded, members, other,
                        primaryHit * weapon.arcFraction, turn, lastKill, false);
                break;
            }
        }
    }

    /**
     * One hit on one member: the build's first-hit and burning-target terms, the damage, Wildfire on a
     * burning kill, and (for the weapon's own pattern hits) the burn stack it applies to a survivor.
     */
    private static void landRoleScenarioHit(RoleScenarioWeapon weapon, float[] health, int[] burnStacks,
                                            int[] burnRemaining, int[] hitsLanded, int members, int member,
                                            float hit, int turn, float[] lastKill, boolean appliesBurn) {
        float damage = hit;
        if (hitsLanded[member] == 0) damage += hit / weapon.hitsPerAction * weapon.firstHitBonus;
        boolean burning = burnRemaining[member] > 0 && burnStacks[member] > 0;
        if (burning) damage *= 1f + weapon.burningDamageBonus;
        applyRoleScenarioDamage(health, member, damage, members, turn, lastKill);
        hitsLanded[member]++;
        if (burning && health[member] <= 0f) igniteRoleScenarioNeighbours(weapon, health, burnStacks, burnRemaining, members, burnStacks[member]);
        if (appliesBurn && weapon.burns() && health[member] > 0f) {
            burnStacks[member]    = Math.min(weapon.burnMaxStacks, burnStacks[member] + 1);
            burnRemaining[member] = weapon.burnTurns;
        }
    }

    /**
     * Wildfire (owner ruling D5): a burning member died — every other living member (its neighbours in the
     * clustered group) gains the dead member's FULL burn stacks (cap burnMaxStacks) and a fresh timer.
     */
    private static void igniteRoleScenarioNeighbours(RoleScenarioWeapon weapon, float[] health, int[] burnStacks,
                                                     int[] burnRemaining, int members, int spreadStacks) {
        if (!weapon.wildfire || !weapon.burns() || spreadStacks <= 0) return;
        for (int other = 0; other < members; other++) {
            if (health[other] <= 0f) continue;
            burnStacks[other]    = Math.min(weapon.burnMaxStacks, burnStacks[other] + spreadStacks);
            burnRemaining[other] = weapon.burnTurns;
        }
    }

    private static void applyRoleScenarioDamage(float[] health, int member, float damage, int members, int turn,
                                                float[] lastKill) {
        float before = health[member];
        health[member] = before - damage;
        if (before > 0f && health[member] <= 0f && damage > 0f) {
            for (int other = 0; other < members; other++) {
                if (health[other] > 0f) return;
            }
            lastKill[0] = (turn - 1) + before / damage;
        }
    }

    /*
     * Formula: Role-scenario S8 — hits a charging bruiser lands before it dies
     * Derivation:
     *   The bruiser starts startDistance tiles away; each turn the player acts first (reload / charge /
     *   fire, exactly as roleScenarioClear), then the bruiser acts: one tile closer while not adjacent,
     *   one landed hit while adjacent. A fire action hits with baseHit(distance) x accuracy x hits x scale
     *   x matchup at the CURRENT distance. Crediting the SPREAD payoff (W1): a surviving target hit at
     *   <= knockbackMaxTiles is pushed one tile back (free tile assumed); one hit at <= staggerMaxTiles
     *   cancels the bruiser's next action, never twice within staggerMinTurnsBetween turns. A weapon that
     *   self-damages on an adjacent target counts that shot as a hit taken. Result = hits taken.
     * Edge cases: returns turnCap when the bruiser survives the cap (the weapon cannot stop it). A weapon
     *   with no reach at the current distance simply wastes the action — the bruiser keeps closing.
     */
    public static int roleScenarioBruiserHitsTaken(RoleScenarioWeapon weapon, int startDistance,
                                                   float enemyHitPoints, float damageScale, float matchupMultiplier,
                                                   int staggerMinTurnsBetween, int turnCap) {
        return Math.round(roleScenarioBruiserWeightedHitsTaken(weapon, startDistance, enemyHitPoints, damageScale,
                matchupMultiplier, staggerMinTurnsBetween, turnCap, 1f));
    }

    /*
     * Formula: Role-scenario S8 with a weighted self-hit (balance-overhaul order 5)
     * Derivation:
     *   The same walk as roleScenarioBruiserHitsTaken, except a self-damage shot (a contact-fuse hit on an
     *   adjacent target) counts selfHitWeight hits instead of one — the share of the player's OWN explosion
     *   damage that still lands (Blast Shield: 1 - reduction; DEMOLITIONIST: 0):
     *       hitsTaken = bruiserHitsLanded + selfHitWeight x selfDamageShots
     *   selfHitWeight = 1 reproduces the integer S8 score exactly (the int method delegates here).
     * Edge cases: selfHitWeight is clamped to [0, 1]; returns turnCap when the bruiser survives the cap.
     */
    public static float roleScenarioBruiserWeightedHitsTaken(RoleScenarioWeapon weapon, int startDistance,
                                                             float enemyHitPoints, float damageScale,
                                                             float matchupMultiplier, int staggerMinTurnsBetween,
                                                             int turnCap, float selfHitWeight) {
        float selfWeight = Math.max(0f, Math.min(1f, selfHitWeight));
        int distance = Math.max(1, startDistance);
        float health = enemyHitPoints;
        int clip = weapon.clipActions;
        int reloadRemaining = 0;
        int charged = 0;
        float hitsTaken = 0f;
        boolean actionCancelled = false;
        int lastStaggerTurn = -staggerMinTurnsBetween - 1;
        for (int turn = 1; turn <= turnCap; turn++) {
            if (reloadRemaining > 0) {
                reloadRemaining--;
                if (reloadRemaining == 0) clip = weapon.clipActions;
            } else if (charged < weapon.chargeTurns) {
                charged++;
            } else {
                charged = 0;
                float hit = weapon.baseHitAt(distance) * weapon.accuracy * weapon.hitsPerAction
                        * damageScale * matchupMultiplier;
                if (hit > 0f) hit += hit / weapon.hitsPerAction * (turnHitsLanded(health, enemyHitPoints) ? 0f : weapon.firstHitBonus);
                health -= hit;
                if (weapon.selfDamageWhenAdjacent && distance == 1 && hit > 0f) hitsTaken += selfWeight;
                clip--;
                if (clip == 0) {
                    if (weapon.reloadTurns == 0) clip = weapon.clipActions;
                    else reloadRemaining = weapon.reloadTurns;
                }
                if (health > 0f && hit > 0f) {
                    int hitDistance = distance;
                    if (hitDistance <= weapon.knockbackMaxTiles) distance++;
                    if (hitDistance <= weapon.staggerMaxTiles && !actionCancelled
                            && turn - lastStaggerTurn >= staggerMinTurnsBetween) {
                        actionCancelled = true;
                        lastStaggerTurn = turn;
                    }
                }
            }
            if (health <= 0f) return hitsTaken;
            if (actionCancelled) {
                actionCancelled = false;
            } else if (distance > 1) {
                distance--;
            } else {
                hitsTaken += 1f;
            }
        }
        return turnCap;
    }

    /** True once the bruiser has taken any damage (the first-hit bonus is spent). */
    private static boolean turnHitsLanded(float health, float startHealth) {
        return health < startHealth;
    }

    // =====================================================================================
    // BUILD VALUATION (balance-overhaul order 5) — perks, keystones, R-PERK / R-KEYSTONE / R-CARRY.
    // util/BuildValuationModel orchestrates these over the order-3 scenario walk; every number it
    // compares is produced by one of the formulas below.
    // =====================================================================================

    /*
     * Formula: buildNicheGain — how much a build change improves a niche metric (R-PERK PK1/PK2, R-KEYSTONE)
     * Derivation:
     *   A gain is the RELATIVE improvement, always positive when the metric got better:
     *       lower is better  (turns, ammo, hits taken):  gain = before / after - 1
     *       higher is better (survivals, supply):         gain = after / before - 1
     *   so a 15% damage bonus on a smooth single-target kill reads 0.15 (turns x 1/1.15), and a 15% larger
     *   pool reads 0.15. A worse metric gives a negative gain; R-KEYSTONE's LOSS is -gain.
     * Edge cases:
     *   before == after (including both infinite) -> 0. A lower-better after <= 0 -> before > 0 ? +infinity : 0;
     *   a higher-better before <= 0 -> after > 0 ? +infinity : 0. Infinity on either side otherwise returns
     *   +infinity or -1 in the direction of the change, never NaN.
     */
    public static float buildNicheGain(float before, float after, boolean higherIsBetter) {
        if (before == after) return 0f;
        if (higherIsBetter) {
            if (Float.isInfinite(after)) return Float.POSITIVE_INFINITY;
            if (Float.isInfinite(before)) return -1f;
            if (before <= 0f) return after > 0f ? Float.POSITIVE_INFINITY : 0f;
            return after / before - 1f;
        }
        if (Float.isInfinite(before)) return Float.POSITIVE_INFINITY;
        if (Float.isInfinite(after)) return -1f;
        if (after <= 0f) return before > 0f ? Float.POSITIVE_INFINITY : 0f;
        return before / after - 1f;
    }

    /*
     * Formula: falloffReducedHit — Steady Aim closes the distance falloff (balance-overhaul order 5)
     * Derivation:
     *   A falloff weapon loses (undropped - hit) of its hit at a distance. Steady Aim removes a fraction r
     *   of that loss — equivalently it scales the drop coefficient by (1 - r) while the clamp is not hit:
     *       hit' = undropped - (undropped - hit) x (1 - r)
     *   Worked: Assault Rifle 20 undropped, 15.2 at 3 tiles, r = 0.30 -> 20 - 4.8 x 0.7 = 16.64.
     * Edge cases: r clamped to [0, 1] (r = 1 removes all falloff); a hit above the undropped value (a
     *   weapon whose undropped hit is not its maximum) is returned unchanged.
     */
    public static float falloffReducedHit(float hitAtDistance, float undroppedHit, float reduction) {
        if (hitAtDistance >= undroppedHit) return hitAtDistance;
        float clamped = Math.max(0f, Math.min(1f, reduction));
        return undroppedHit - (undroppedHit - hitAtDistance) * (1f - clamped);
    }

    /*
     * Formula: buildHitDamageMultiplier — every perk / keystone term on one player hit (balance-overhaul order 5)
     * Derivation:
     *   The build's terms are independent multipliers on the same hit (util/BuildValuationModel composes them
     *   the same way on the reference scenarios):
     *       multiplier = class x (1 + rangeBand) x (1 + firstHit) x (1 + burningTarget)
     *                          x (1 + meleeLowHealth) x (1 + riposteWindow)
     *   class          = (1 + summed perk class bonuses) x keystone class costs (BuildStats.damageMultiplierFor)
     *   rangeBand      = Point Man (SPREAD, point-blank) or Lane Discipline (BALLISTIC / RAIL, long range)
     *   firstHit       = combinedFirstHitBonus (First Blood x DEADEYE's critical)
     *   burningTarget  = PYROMANIAC's bonus vs a BURNING enemy
     *   meleeLowHealth = BERSERKER's low-HP melee bonus
     *   riposteWindow  = Riposte Drill's bonus while the riposte's VULNERABLE window is open
     *   Worked: SPREAD with Buckshot r1 (+0.20) and Point Man r1 (+0.20) at 1 tile: 1.2 x 1.2 = 1.44.
     * Edge cases: a negative class multiplier reads as 0; each bonus below -1 is floored so no term is negative.
     */
    public static float buildHitDamageMultiplier(float classMultiplier, float rangeBandBonus, float firstHitBonus,
                                                 float burningTargetBonus, float meleeLowHealthBonus,
                                                 float riposteWindowBonus) {
        return Math.max(0f, classMultiplier)
                * Math.max(0f, 1f + rangeBandBonus)
                * Math.max(0f, 1f + firstHitBonus)
                * Math.max(0f, 1f + burningTargetBonus)
                * Math.max(0f, 1f + meleeLowHealthBonus)
                * Math.max(0f, 1f + riposteWindowBonus);
    }

    /*
     * Formula: buildMaxPoolBonus — the build's flat share of a vitality-grown max pool (balance-overhaul order 5)
     * Derivation:
     *   Hardy / Plate Carrier (additive) and the keystone pool terms (multiplicative after) fold into one
     *   multiplier m on the pool the vitality ladder grants at the character level (R9):
     *       ladderPool = basePool x vitalityScale(level)
     *       bonus      = round(ladderPool x (m - 1))
     *   The caller applies the CHANGE in this bonus since it last applied it, so a level-up (which grows the
     *   ladder pool) and a pick (which moves m) both land as one signed delta on Player.adjustMax*.
     *   Worked: 100 HP, vitality 1.09 at level 2, Hardy r1 (m 1.147) -> round(109 x 0.147) = 16.
     * Edge cases: m < 0 reads as 0 (bonus = -ladderPool); a non-positive base pool returns 0.
     */
    public static int buildMaxPoolBonus(int basePool, float vitalityScale, float multiplier) {
        if (basePool <= 0) return 0;
        float ladderPool = basePool * Math.max(0f, vitalityScale);
        return Math.round(ladderPool * (Math.max(0f, multiplier) - 1f));
    }

    /*
     * Formula: buildScaledAmount — a heal, an ammo pickup or a price under a build multiplier
     * Derivation:
     *   scaled = round(base x multiplier); a positive base under a positive multiplier keeps at least
     *   minimumWhenPositive (a heal of 0 or a free item is never the intent of a percentage).
     *   Worked: STIM 18 HP x Field Medic 1.105 -> 20; a 10-shell box x Pack Rat 1.15 -> 12 (11.5 rounds up).
     * Edge cases: base <= 0 returns base unchanged; multiplier <= 0 returns 0.
     */
    public static int buildScaledAmount(int base, float multiplier, int minimumWhenPositive) {
        if (base <= 0) return base;
        if (multiplier <= 0f) return 0;
        return Math.max(minimumWhenPositive, Math.round(base * multiplier));
    }

    /*
     * Formula: guardReflectDamage — IRON BULWARK's reflection of one guarded hit (balance-overhaul order 5)
     * Derivation:
     *   The guard blocked (incoming - taken) of a front-arc hit; the keystone sends a fraction f of THAT back:
     *       reflected = round(blocked x f)        (f may exceed 1 — the shipped 1.4 does)
     *   This is the per-hit form of guardReflectDamagePerTurn (the valuation's per-turn rate).
     *   Worked: a 30 heavy guarded to 9 blocks 21; f 1.4 -> 29.
     * Edge cases: blocked <= 0 or f <= 0 -> 0.
     */
    public static int guardReflectDamage(int blockedDamage, float reflectFraction) {
        if (blockedDamage <= 0 || reflectFraction <= 0f) return 0;
        return Math.round(blockedDamage * reflectFraction);
    }

    /*
     * Formula: pierceOverchargeMultiplier — Overcharge on a piercing bolt (balance-overhaul order 5)
     * Derivation:
     *   The k-th body a bolt pierces (k = bodies already pierced by the same bolt, 0 for the first) takes
     *       multiplier = 1 + bonusPerEnemy x k
     *   (the PIERCE term of util/BuildValuationModel's scenario walk).
     *   Worked: o = 0.40, third body (k 2) -> 1.8.
     * Edge cases: k < 0 reads as 0; a negative bonus is floored at 0.
     */
    public static float pierceOverchargeMultiplier(float bonusPerEnemy, int enemiesAlreadyPierced) {
        return 1f + Math.max(0f, bonusPerEnemy) * Math.max(0, enemiesAlreadyPierced);
    }

    /*
     * Formula: wildfireSpreadStacks — the burn a neighbour holds after Wildfire passes a dead enemy's fire (D5)
     * Derivation:
     *   A BURNING enemy that dies passes its FULL current stack count to each living orthogonal neighbour:
     *       stacksAfter = min(cap, neighbourStacks + deadEnemyStacks)
     *   cap = FLAME_BURN_MAX_STACKS + Accelerant's bonus.
     *   Worked: dead 3 stacks, neighbour 1, cap 3 -> 3.
     * Edge cases: negative inputs read as 0; cap below 1 reads as 1 (a spread always lights at least one stack).
     */
    public static int wildfireSpreadStacks(int neighbourStacks, int deadEnemyStacks, int cap) {
        return Math.min(Math.max(1, cap), Math.max(0, neighbourStacks) + Math.max(0, deadEnemyStacks));
    }

    /*
     * Formula: combinedFirstHitBonus — First Blood and DEADEYE on the same opening hit
     * Derivation:
     *   First Blood multiplies a hit on a full-HP enemy by (1 + b); DEADEYE makes that same first hit a
     *   critical (x critMultiplier). Both are multipliers on one hit, so they compose:
     *       firstHitMultiplier = (1 + b) x (critical ? critMultiplier : 1)
     *       bonus              = firstHitMultiplier - 1
     *   Worked: b = 0.25, critical x2 -> 1.25 x 2 - 1 = 1.5.
     * Edge cases: negative b is floored at 0; critMultiplier < 1 is floored at 1 (a crit never weakens).
     */
    public static float combinedFirstHitBonus(float fullHealthBonus, boolean critical, float critMultiplier) {
        float multiplier = (1f + Math.max(0f, fullHealthBonus)) * (critical ? Math.max(1f, critMultiplier) : 1f);
        return multiplier - 1f;
    }

    /*
     * Formula: buildClipActions — fire actions per clip after DEADEYE's clip cut
     * Derivation:
     *   A clip holds clipActions x ammoPerAction rounds; the cut keeps floor(rounds x multiplier) of them,
     *   and an action still needs a whole ammoPerAction:
     *       actions = max(1, floor(clipActions x ammoPerAction x multiplier) / ammoPerAction)
     *   Worked: Chaingun 8 actions x 3 rounds x 0.7 = 16 rounds -> 5 actions; Railgun 1 x 0.7 -> 1 (never 0).
     * Edge cases: multiplier >= 1 returns clipActions unchanged (the cut never grows a clip); every clip
     *   keeps at least one action.
     */
    public static int buildClipActions(int clipActions, int ammoPerAction, float multiplier) {
        if (multiplier >= 1f) return clipActions;
        int perAction = Math.max(1, ammoPerAction);
        int rounds = (int) Math.floor(clipActions * perAction * Math.max(0f, multiplier) + 1e-4f);
        return Math.max(1, rounds / perAction);
    }

    /*
     * Formula: guardedHeavyShare — the share of committed heavies the player meets inside the guard arc
     * Derivation:
     *   A competent player faces fs of the heavies committed at them (HEAVY_EXPECTED_AVOIDANCE, the order-4
     *   pricing share). The (1 - fs) they did not face arrive from the other lanes, spread evenly over the
     *   (lanes - 1) unfaced cardinal lanes. A wider arc that covers extraLanes of those lanes guards that
     *   share of the misses too:
     *       share = fs + (1 - fs) x extraLanes / (lanes - 1)
     *   Worked: fs 0.6, the +/-90 arc covers both sides (2 of 3 unfaced lanes) -> 0.6 + 0.4 x 2/3 = 0.867.
     * Edge cases: lanes <= 1 -> fs; extraLanes clamped to [0, lanes - 1]; fs clamped to [0, 1].
     */
    public static float guardedHeavyShare(float facedShare, int extraLanes, int lanes) {
        float faced = Math.max(0f, Math.min(1f, facedShare));
        if (lanes <= 1) return faced;
        int extra = Math.max(0, Math.min(lanes - 1, extraLanes));
        return faced + (1f - faced) * extra / (lanes - 1);
    }

    /*
     * Formula: heavyTakenFactor — the share of a heavy's damage that lands, guard included
     * Derivation:
     *   A guarded heavy lands x guardMultiplier (GUARD_FRONT_HEAVY 0.20; Shield Wall r2 0.10), an unguarded
     *   one in full:
     *       factor = share x guardMultiplier + (1 - share)
     *   Worked: share 0.6, x0.20 -> 0.12 + 0.40 = 0.52.
     * Edge cases: share and guardMultiplier clamped to [0, 1].
     */
    public static float heavyTakenFactor(float guardedShare, float guardMultiplier) {
        float share = Math.max(0f, Math.min(1f, guardedShare));
        float multiplier = Math.max(0f, Math.min(1f, guardMultiplier));
        return share * multiplier + (1f - share);
    }

    /*
     * Formula: riposteDamageMultiplier — a riposte's VULNERABLE window as a fight-long damage multiplier
     * Derivation:
     *   Against a heavy-capable enemy, a heavy comes every P turns and the player guards (and so ripostes)
     *   a share g of them; each riposte leaves the enemy VULNERABLE for v turns at +x damage. Over a fight
     *   of T turns that is g x T / P ripostes covering g x T x v / P turns — a share of the fight
     *       coverage = min(1, g x v / P)                         (independent of T: self-consistent)
     *   during which every hit deals (1 + x), so the fight-long multiplier on the player's damage is
     *       multiplier = 1 + x x coverage
     *   Worked: base g 0.6, v 2, x 0.5, P 4 -> 1 + 0.5 x 0.3 = 1.15; Riposte Drill r2 + the wide arc
     *   (g 0.867, v 4, x 1.0) -> 1 + 1.0 x 0.867 = 1.867.
     * Edge cases: P <= 0 -> 1 (no heavies, no riposte); negative inputs read as 0; coverage capped at 1
     *   (windows cannot overlap into more than the whole fight).
     */
    public static float riposteDamageMultiplier(float guardedShare, float vulnerableTurns, float vulnerableBonus,
                                                float heavyPeriodTurns) {
        if (heavyPeriodTurns <= 0f) return 1f;
        float coverage = Math.min(1f, Math.max(0f, guardedShare) * Math.max(0f, vulnerableTurns) / heavyPeriodTurns);
        return 1f + Math.max(0f, vulnerableBonus) * coverage;
    }

    /*
     * Formula: guardReflectDamagePerTurn — IRON BULWARK's reflection as damage per turn
     * Derivation:
     *   A heavy of size h comes every P turns; a share g is guarded, blocking (1 - m) x h; the keystone
     *   returns a fraction r of the blocked part to the attacker. Per turn of the fight:
     *       reflected = g x r x h x (1 - m) / P
     *   It ADDS to the player's own damage per turn on that attacker, so the fight against it shortens.
     *   Worked: g 0.6, r 0.5, h 217, m 0.2, P 4 -> 13.0 a turn.
     * Edge cases: P <= 0 -> 0; negative inputs read as 0; m clamped to [0, 1].
     */
    public static float guardReflectDamagePerTurn(float guardedShare, float reflectFraction, float heavyHit,
                                                  float guardMultiplier, float heavyPeriodTurns) {
        if (heavyPeriodTurns <= 0f) return 0f;
        float blockedShare = 1f - Math.max(0f, Math.min(1f, guardMultiplier));
        return Math.max(0f, guardedShare) * Math.max(0f, reflectFraction) * Math.max(0f, heavyHit) * blockedShare
                / heavyPeriodTurns;
    }

    /*
     * Formula: attritionDamageTaken — what one fight costs the player (the BULWARK duel, PK2 survivals)
     * Derivation:
     *   An enemy acts every turn it lives: an ORDINARY hit on an ordinary turn, a HEAVY on a heavy turn,
     *   nothing on a turn a riposte stunned it. With ordinaryTurns ordinary hits of o and heavies of h
     *   landing heavyFactor of their size (heavyTakenFactor):
     *       damage = ordinaryTurns x o + heavies x h x heavyFactor
     * Edge cases: negative inputs read as 0.
     */
    public static float attritionDamageTaken(float ordinaryTurns, float ordinaryHit, float heavies, float heavyHit,
                                             float heavyFactor) {
        return Math.max(0f, ordinaryTurns) * Math.max(0f, ordinaryHit)
                + Math.max(0f, heavies) * Math.max(0f, heavyHit) * Math.max(0f, heavyFactor);
    }

    /*
     * Formula: fightsSurvived — TTD measured in fights of a given cost
     * Derivation:
     *   With an effective pool P and a fight that costs D, the player survives P / D such fights back to
     *   back (no healing between them) — turns-to-die divided by the fight's length, so a perk that makes
     *   the fight SHORTER (riposte, reflect) and one that makes the pool BIGGER (armour) move the same number:
     *       survivals = P / D
     * Edge cases: D <= 0 -> +infinity (the fight costs nothing); P <= 0 -> 0.
     */
    public static float fightsSurvived(float pool, float damagePerFight) {
        if (damagePerFight <= 0f) return Float.POSITIVE_INFINITY;
        return Math.max(0f, pool) / damagePerFight;
    }

    /*
     * Formula: floorSustain — everything that keeps the player standing through one COMBAT floor
     * Derivation (all in fractions of the ON-CURVE player's max HP, so builds compare on one scale):
     *   pool        = hpMult + armourRatio x arMult          (armourRatio = max armour / max HP)
     *   heals       = healFraction x healMult x hpMult       (heals are fractions of the BUILD's max HP, R10)
     *   armour      = armourPickupFraction x arMult          (armour pickups are fractions of max armour)
     *   sustain     = pool + heals + armour + meleeKillHeal + secondWindHit
     *   meleeKillHeal is kills x heal fraction x hpMult; secondWindHit is the lethal hit Second Wind absorbs
     *   (a heavy, the canonical killer — a fraction of the on-curve max HP).
     * Edge cases: negative terms read as 0.
     */
    public static float floorSustain(float healthMultiplier, float armourRatio, float armourMultiplier,
                                     float healFraction, float healMultiplier, float armourPickupFraction,
                                     float meleeKillHeal, float secondWindHit) {
        float health = Math.max(0f, healthMultiplier);
        return health + Math.max(0f, armourRatio) * Math.max(0f, armourMultiplier)
                + Math.max(0f, healFraction) * Math.max(0f, healMultiplier) * health
                + Math.max(0f, armourPickupFraction) * Math.max(0f, armourMultiplier)
                + Math.max(0f, meleeKillHeal) + Math.max(0f, secondWindHit);
    }

    /*
     * Formula: floorIncoming — one COMBAT floor's incoming damage under a build
     * Derivation (fractions of the on-curve max HP):
     *   The SupplyPlanner's modelled incoming I (floorExpectedIncomingDamage) splits into an ordinary part
     *   and a HEAVY part of share s. A build that guards heavies better scales only the heavy part, by its
     *   heavyTakenFactor over the base one; standing in fire adds its ticks:
     *       incoming = I x ((1 - s) + s x factorBuild / factorBase) + fireDamage
     * Edge cases: factorBase <= 0 -> the heavy part is left unscaled; negative inputs read as 0.
     */
    public static float floorIncoming(float baseIncoming, float heavyShare, float heavyFactorBuild,
                                      float heavyFactorBase, float fireDamage) {
        float share = Math.max(0f, Math.min(1f, heavyShare));
        float ratio = heavyFactorBase > 0f ? Math.max(0f, heavyFactorBuild) / heavyFactorBase : 1f;
        return Math.max(0f, baseIncoming) * ((1f - share) + share * ratio) + Math.max(0f, fireDamage);
    }

    /*
     * Formula: barrelOpenerTurns — a fight that opens on a barrel (Sapper, DEMOLITIONIST)
     * Derivation:
     *   The first turn shoots the barrel; its blast b (the ring hit, x the build's barrel multiplier and the
     *   EXPLOSIVE matchup) lands on every member standing beside it. If b kills them the fight ends inside
     *   that first turn at the fraction the blast needed (the order-3 last-turn rule); otherwise the weapon
     *   finishes the survivors from health - b:
     *       turns = b >= health ? health / b : 1 + followUpTurns(health - b)
     *   Worked: hulk 60, blast 30 -> 1 + turns(30); blast 75 -> 0.8.
     * Edge cases: b <= 0 -> 1 + followUpTurns (a wasted shot); health <= 0 -> 0.
     */
    public static float barrelOpenerTurns(float enemyHealth, float blast, float followUpTurns) {
        if (enemyHealth <= 0f) return 0f;
        if (blast >= enemyHealth) return enemyHealth / blast;
        return 1f + Math.max(0f, followUpTurns);
    }

    /*
     * Formula: netAmmoAfterRefunds — ammo a clear costs once kills refund shells (Breacher, SCATTERGUN)
     * Derivation:
     *   Each kill refunds one unit with probability p (Breacher, plus SCATTERGUN's point-blank refund), so
     *   in expectation:
     *       net = max(0, spent - kills x min(1, p))
     *   Worked: Shotgun 4 shells for 3 kills, p 0.33 -> 3.01.
     * Edge cases: kills < 0 or p < 0 read as 0.
     */
    public static float netAmmoAfterRefunds(float ammoSpent, int kills, float refundChancePerKill) {
        float refunds = Math.max(0, kills) * Math.max(0f, Math.min(1f, refundChancePerKill));
        return Math.max(0f, ammoSpent - refunds);
    }

    /*
     * Formula: supplyPowerPoints — a floor's supply in depth-1 power points (the SCAVENGER niche)
     * Derivation (the shop's own exchange rates, so every component reads in the same currency):
     *   ammo     = ammoDamageAtDepth / hitGrowth(d) / SHOP_AMMO_DAMAGE_PER_POWER_POINT
     *   heals    = healFraction x PLAYER_MAX_HEALTH / SHOP_HEAL_HP_PER_POWER_POINT     (depth-1 HP basis)
     *   credits  = credits / (SHOP_CREDITS_PER_POWER_POINT x depthFactor(d) x priceMultiplier)
     *   A cheaper shop (Haggler, QUARTERMASTER) makes the same credits buy more points.
     * Edge cases: a non-positive divisor returns 0 for that component.
     */
    public static float ammoPowerPoints(float ammoDamageAtDepth, float hitGrowth, float damagePerPowerPoint) {
        if (hitGrowth <= 0f || damagePerPowerPoint <= 0f) return 0f;
        return Math.max(0f, ammoDamageAtDepth) / hitGrowth / damagePerPowerPoint;
    }

    /*
     * Formula: healPowerPoints — heal / armour supply in depth-1 power points (supplyPowerPoints, heals)
     * Derivation: heals = healFraction x depthOneMaxHealth / healthPerPowerPoint — the shop's medkit price
     *   basis (SHOP_HEAL_HP_PER_POWER_POINT HP of a depth-1 player buys one point).
     *   Worked: 0.45 x 130 / 11 = 5.3 points (one field medkit).
     * Edge cases: healthPerPowerPoint <= 0 -> 0; negative inputs read as 0.
     */
    public static float healPowerPoints(float healFraction, float depthOneMaxHealth, float healthPerPowerPoint) {
        if (healthPerPowerPoint <= 0f) return 0f;
        return Math.max(0f, healFraction) * Math.max(0f, depthOneMaxHealth) / healthPerPowerPoint;
    }

    /*
     * Formula: creditPowerPoints — credits in depth-1 power points at the build's shop prices
     * Derivation: credits = credits / (creditsPerPowerPoint x depthFactor(d) x priceMultiplier), the inverse
     *   of shopPrice: what those credits buy at the Fabricator, so a cheaper shop buys more points.
     *   Worked: 465 credits at d15 (factor 2.4), 36 cr/PP, x1 -> 5.4 points; x0.75 -> 7.2.
     * Edge cases: a non-positive divisor -> 0; negative credits read as 0.
     */
    public static float creditPowerPoints(float credits, float creditsPerPowerPoint, int depth, float depthPriceScale,
                                          float priceMultiplier) {
        float divisor = creditsPerPowerPoint * shopDepthPriceFactor(depth, depthPriceScale) * priceMultiplier;
        if (divisor <= 0f) return 0f;
        return Math.max(0f, credits) / divisor;
    }

    /*
     * Formula: expectedPerkPicks / expectedTagRankCount — the on-curve player's build size (R-CARRY)
     * Derivation:
     *   Every character level after the first is one pick; the keystone levels (B4) pick a keystone
     *   instead of a perk:
     *       perkPicks(L) = (L - 1) - #{k in KEYSTONE_LEVELS : k <= L}
     *   A build spends a focus share f of them in its own tag (the rest pivot) and cannot exceed the tag's
     *   rank capacity C (the sum of its perks' max ranks):
     *       ranks = min(C, round(f x perkPicks))
     *   Worked: L 15, keystones {5,10,15} -> 14 - 3 = 11 picks; f 0.6 -> 7 ranks (PYRO caps at 5).
     * Edge cases: L < 1 -> 0 picks; f clamped to [0, 1].
     */
    public static int expectedPerkPicks(int characterLevel, int[] keystoneLevels) {
        int level = Math.max(1, characterLevel);
        int keystones = 0;
        if (keystoneLevels != null) {
            for (int keystoneLevel : keystoneLevels) {
                if (keystoneLevel > 1 && keystoneLevel <= level) keystones++;
            }
        }
        return Math.max(0, level - 1 - keystones);
    }

    /*
     * Formula: expectedTagRankCount — the ranks the expected build holds in its own tag (R-CARRY)
     * Derivation: ranks = min(C, round(f x perkPicks)) — a focus share f of the picks, capped at the tag's
     *   rank capacity C (see expectedPerkPicks).
     *   Worked: 11 picks, f 0.6 -> 7; PYRO (C = 5) -> 5.
     * Edge cases: f clamped to [0, 1]; negative picks read as 0.
     */
    public static int expectedTagRankCount(int perkPicks, float focusShare, int tagCapacity) {
        float share = Math.max(0f, Math.min(1f, focusShare));
        return Math.max(0, Math.min(tagCapacity, Math.round(share * Math.max(0, perkPicks))));
    }

    /*
     * Formula: scenarioLift — how much faster (or safer) one reference fight got (R-CARRY)
     * Derivation:
     *   Turns-to-clear scenarios: the order-3 walk scores the last turn by the FRACTION the killing event
     *   needed, so a fight that ends inside its first turn can read 0.72 or 0.01 depending on how much a
     *   burn tick overkilled — a 70x "lift" for a fight that took one turn either way. A fight costs the
     *   player at least minimumTurns, so both sides are floored there:
     *       lift = max(m, before) / max(m, after)
     *   S8 is scored in bruiser hits taken, where 0 is common; it compares hits + 1 (0 vs 1 hit = 2x):
     *       lift = (before + 1) / (after + 1)
     * Edge cases: an infinite before (no eligible answer) with a finite after -> the after's own floor
     *   ratio is meaningless, so it returns 1 + (the cap is the R-ROLE turn cap's job); both infinite -> 1.
     */
    public static float scenarioLift(float before, float after, boolean hitsTaken, float minimumTurns) {
        if (Float.isInfinite(before) || Float.isInfinite(after)) {
            return Float.isInfinite(before) == Float.isInfinite(after) ? 1f
                    : (Float.isInfinite(before) ? BalanceConfig.ROLE_SCENARIO_TURN_CAP / Math.max(minimumTurns, after)
                                                : Math.max(minimumTurns, before) / BalanceConfig.ROLE_SCENARIO_TURN_CAP);
        }
        if (hitsTaken) return (before + 1f) / (after + 1f);
        return Math.max(minimumTurns, before) / Math.max(minimumTurns, after);
    }

    /*
     * Formula: averageAliveMembers — how many of a group stand, on average, through its clear
     * Derivation:
     *   A group of n is killed down one by one over the clear, so the count alive falls linearly from n to
     *   1 (the last member dies at the end); the time average of that count is
     *       alive = (n + 1) / 2
     *   Worked: a pack of 3 -> 2 alive on average; a single -> 1.
     * Edge cases: n < 1 is treated as 1.
     */
    public static float averageAliveMembers(int groupSize) {
        return (Math.max(1, groupSize) + 1) / 2f;
    }

    /*
     * Formula: bandShareWithin — the share of an engagement band at or inside a reach
     * Derivation:
     *   A scenario engages at every tile of [min, max] with equal weight (roleScenarioMeanHit), so the share
     *   of its fight that happens within maxTiles is
     *       share = #{d in [min, max] : d <= maxTiles} / (max - min + 1)
     *   Worked: S2 (1-3) within 2 tiles -> 2/3.
     * Edge cases: an inverted band (max < min) is the single tile min.
     */
    public static float bandShareWithin(int distanceMin, int distanceMax, int maxTiles) {
        int upper = Math.max(distanceMin, distanceMax);
        int inside = 0;
        for (int distance = distanceMin; distance <= upper; distance++) {
            if (distance <= maxTiles) inside++;
        }
        return (float) inside / (upper - distanceMin + 1);
    }

    /*
     * Formula: powerPointsToHealFraction — what supply power points buy as heals (SCAVENGER's carry)
     * Derivation:
     *   The Fabricator prices a medkit at heal x depthOneMaxHealth / healthPerPowerPoint points
     *   (healPowerPoints), and a medkit restores a FRACTION of max HP at any depth (R10), so one point buys
     *       fraction = healthPerPowerPoint / depthOneMaxHealth   of max HP
     *   and points buy points x that.
     *   Worked: 11 HP / 130 -> 0.085 of max HP per point; 10 points -> 0.85.
     * Edge cases: depthOneMaxHealth <= 0 -> 0; negative points read as 0.
     */
    public static float powerPointsToHealFraction(float powerPoints, float healthPerPowerPoint, float depthOneMaxHealth) {
        if (depthOneMaxHealth <= 0f) return 0f;
        return Math.max(0f, powerPoints) * Math.max(0f, healthPerPowerPoint) / depthOneMaxHealth;
    }

    /*
     * Formula: duelOrdinaryTurns — the enemy turns of a duel that land an ORDINARY hit
     * Derivation:
     *   The enemy acts once per duel turn. On a heavy turn it throws the heavy; on the turn after a riposte
     *   it is stunned and does nothing; every other turn it lands an ordinary hit:
     *       ordinary = max(0, totalTurns - heavies - stunnedTurns)
     *   With totalTurns = fireTurns + guardTurns and one stun per guarded heavy, this is fireTurns - heavies.
     * Edge cases: never negative.
     */
    public static float duelOrdinaryTurns(float totalTurns, float heavies, float stunnedTurns) {
        return Math.max(0f, totalTurns - Math.max(0f, heavies) - Math.max(0f, stunnedTurns));
    }

    /*
     * Formula: survivalLift — a TTD ratio that never reads NaN (R-CARRY)
     * Derivation: lift = after / before for a higher-is-better survival measure.
     * Edge cases: before infinite (nothing hurts the player) -> 1 when after is infinite too, else 0;
     *   after infinite with a finite before -> +infinity is not a lift, it is clamped to the turn cap
     *   (ROLE_SCENARIO_TURN_CAP); before <= 0 -> 1 (no information).
     */
    public static float survivalLift(float before, float after) {
        if (Float.isInfinite(before)) return Float.isInfinite(after) ? 1f : 0f;
        if (Float.isInfinite(after)) return BalanceConfig.ROLE_SCENARIO_TURN_CAP;
        if (before <= 0f) return 1f;
        return after / before;
    }

    /*
     * Formula: geometricMeanLift — a build's lift over a roster of fights (R-CARRY)
     * Derivation:
     *   A lift is a ratio (> 1 = better). Averaging ratios arithmetically lets one huge niche hide seven
     *   untouched fights; the geometric mean is the per-fight lift that, applied to every fight, gives the
     *   same product:
     *       lift = (prod_i lift_i) ^ (1 / n)  = exp(mean(ln lift_i))
     * Edge cases: an empty roster -> 1; a non-positive or non-finite lift is skipped (it carries no
     *   information about the build).
     */
    public static float geometricMeanLift(float[] lifts) {
        if (lifts == null || lifts.length == 0) return 1f;
        double logSum = 0.0;
        int count = 0;
        for (float lift : lifts) {
            if (!(lift > 0f) || Float.isInfinite(lift)) continue;
            logSum += Math.log(lift);
            count++;
        }
        return count == 0 ? 1f : (float) Math.exp(logSum / count);
    }

    /*
     * Formula: buildCarryScore — one number for a build's total lift (R-CARRY)
     * Derivation:
     *   The codebase measures total power as damage-per-turn x effective HP (ladderCouplingAtCharacterLevel,
     *   the boss derivation). A turns-to-kill lift is a DPT ratio and a turns-to-die lift is an eHP ratio,
     *   so the build's power lift over the same player without it is their PRODUCT:
     *       carry = ttkLift x ttdLift
     *   An offence build lifts the first factor, a defence build the second; both land on one scale.
     * Edge cases: non-positive inputs read as 0 (a build that cannot clear or cannot survive carries nothing).
     */
    public static float buildCarryScore(float turnsToKillLift, float turnsToDieLift) {
        return Math.max(0f, turnsToKillLift) * Math.max(0f, turnsToDieLift);
    }

    // =====================================================================================
    // RELIC VALUATION (relics-run-changing-artifacts CP4) — every relic kind priced in the R-CARRY terms.
    // Each formula is the identity (x 1 / + 0) for a relic-free BuildStats, which keeps R-CARRY byte-identical.
    // =====================================================================================

    /*
     * Formula: relicLowHealthDamageMultiplier — LOW_HEALTH_DAMAGE as a whole-fight damage multiplier
     * Derivation:
     *   The relic adds a bonus m to every hit while the player is below BUILD_BERSERK_LOW_HEALTH_FRACTION of
     *   max HP. The scenario walk has no HP track, so the bonus is spread over the share s of a fight's turns
     *   the player is assumed to spend that low (BUILD_BLADE_LOW_HEALTH_TURN_SHARE — the BERSERKER term, now
     *   for every class):
     *       multiplier = 1 + m x s
     *   Worked: m 0.60, s 0.25 -> x1.15.
     * Edge cases: negative m or s read as 0 (-> exactly 1, the relic-free identity); s clamped to [0, 1].
     */
    public static float relicLowHealthDamageMultiplier(float lowHealthBonus, float lowHealthTurnShare) {
        float share = Math.max(0f, Math.min(1f, lowHealthTurnShare));
        return 1f + Math.max(0f, lowHealthBonus) * share;
    }

    /*
     * Formula: relicIncomingMultiplier — DAMAGE_TAKEN_MULTIPLIER and ENEMY_HEALTH_MULTIPLIER on a floor's incoming
     * Derivation:
     *   Every enemy hit lands x damageTaken. A fight's length is proportional to the enemy's HP (the walk
     *   clears HP at a fixed damage rate), and incoming damage is proportional to the turns enemies live,
     *   so enemy HP x e scales the incoming by e too:
     *       incoming' = incoming x damageTaken x enemyHealth
     *   Worked: kevlar 0.88 -> x0.88; tithe engine 1.15 -> x1.15; both -> x1.012.
     * Edge cases: negative inputs read as 0. Relic-free (1, 1) -> exactly 1.
     */
    public static float relicIncomingMultiplier(float damageTakenMultiplier, float enemyHealthMultiplier) {
        return Math.max(0f, damageTakenMultiplier) * Math.max(0f, enemyHealthMultiplier);
    }

    /*
     * Formula: relicShieldHitFraction — FIRST_HIT_SHIELD as sustain (fraction of the on-curve max HP)
     * Derivation:
     *   The first enemy hit of each floor does nothing. The canonical first hit is one reference-soldier
     *   hit h, which would have landed x damageTaken; as a share of the on-curve max HP H:
     *       shield = h x damageTaken / H        (once per floor)
     *   Worked: h 24, H 260, damageTaken 1 -> 0.092 of max HP.
     * Edge cases: H <= 0 -> 0; negative inputs read as 0.
     */
    public static float relicShieldHitFraction(float referenceHit, float damageTakenMultiplier, float maxHealth) {
        if (maxHealth <= 0f) return 0f;
        return Math.max(0f, referenceHit) * Math.max(0f, damageTakenMultiplier) / maxHealth;
    }

    /*
     * Formula: relicFloorSustain — the relic kinds that add sustain to one COMBAT floor
     * Derivation (fractions of the on-curve max HP, the floorSustain scale; heals are fractions of the
     * BUILD's max HP, armour of the BUILD's max armour, so they ride hpMult / armourRatio x arMult):
     *   kills       = bodies x killShare                                  (kills a kill-relic fires on)
     *   killHeal    = kills x KILL_HEAL x hpMult
     *   killArmour  = kills x KILL_ARMOR x armourRatio x arMult
     *   doorHeal    = doorsPerFloor x DOOR_OPEN_HEAL x hpMult              (once per door, R10)
     *   arrival     = FLOOR_ARRIVAL_HEAL x hpMult + FLOOR_ARRIVAL_ARMOR x armourRatio x arMult  (once a floor)
     *   sustain    += killHeal + killArmour + doorHeal + arrival + shieldHit
     *   Relic heals are PLAIN healing at runtime (not a treatment), so healMultiplier does not touch them.
     *   Overheal (a heal at full HP) is not modelled — the same assumption floorSustain makes for medkits.
     *   Worked: 8 bodies x 0.6 x 0.04 x 1 = 0.192 (vampiric core).
     * Edge cases: negative inputs read as 0; relic-free (all zero) -> exactly 0.
     */
    public static float relicFloorSustain(float bodiesPerFloor, float killShare, float killHealFraction,
                                          float killArmourFraction, float doorsPerFloor, float doorHealFraction,
                                          float arrivalHealFraction, float arrivalArmourFraction,
                                          float shieldHitFraction, float healthMultiplier, float armourRatio,
                                          float armourMultiplier) {
        float health = Math.max(0f, healthMultiplier);
        float armour = Math.max(0f, armourRatio) * Math.max(0f, armourMultiplier);
        float kills = Math.max(0f, bodiesPerFloor) * Math.max(0f, Math.min(1f, killShare));
        return kills * (Math.max(0f, killHealFraction) * health + Math.max(0f, killArmourFraction) * armour)
                + Math.max(0f, doorsPerFloor) * Math.max(0f, doorHealFraction) * health
                + Math.max(0f, arrivalHealFraction) * health
                + Math.max(0f, arrivalArmourFraction) * armour
                + Math.max(0f, shieldHitFraction);
    }

    /*
     * Formula: relicDuelDamage — what the BULWARK duel costs under DAMAGE_TAKEN_MULTIPLIER and FIRST_HIT_SHIELD
     * Derivation:
     *   The duel's damage D is a sum of enemy hits; each lands x m. The shield absorbs the first hit (one
     *   ordinary hit o, the enemy's opening attack) before it lands:
     *       damage = max(0, D - absorbedHit) x m
     *   Worked: D 300, o 24, m 0.88 -> 242.9.
     * Edge cases: negative inputs read as 0; relic-free (absorbed 0, m 1) -> exactly D.
     */
    public static float relicDuelDamage(float damage, float damageTakenMultiplier, float absorbedHit) {
        return Math.max(0f, damage - Math.max(0f, absorbedHit)) * Math.max(0f, damageTakenMultiplier);
    }

    /*
     * Formula: relicBlastSurvivorCount / relicBlastSurvivorMeanHealth — a group after a door blast
     * Derivation:
     *   A DOOR_BLAST puts its centre c on the lead member and its ring r (c x RELIC_BLAST_RING_SHARE) on every
     *   other member (the ring covers the four neighbours, and a scenario group stands bunched in a lane).
     *   With member health h and n members:
     *       lead left   = max(0, h - c);   others left = max(0, h - r)
     *       survivors   = [lead left > 0] + (n - 1) x [others left > 0]
     *       mean health = (lead left + (n - 1) x others left) / survivors
     *   The walk takes one uniform member health, so the survivors are re-walked at their MEAN health — an
     *   approximation that is exact when the blast kills the lead (or nobody) and n = 1.
     *   Worked: h 100, c 60, r 36, n 3 -> 3 survivors at (40 + 2 x 64) / 3 = 56.
     * Edge cases: n < 1 is 1; negative blasts read as 0; no survivor -> count 0, mean 0.
     */
    public static int relicBlastSurvivorCount(float memberHealth, float centreBlast, float ringBlast, int groupSize) {
        int members = Math.max(1, groupSize);
        int count = memberHealth - Math.max(0f, centreBlast) > 0f ? 1 : 0;
        if (memberHealth - Math.max(0f, ringBlast) > 0f) count += members - 1;
        return count;
    }

    public static float relicBlastSurvivorMeanHealth(float memberHealth, float centreBlast, float ringBlast, int groupSize) {
        int survivors = relicBlastSurvivorCount(memberHealth, centreBlast, ringBlast, groupSize);
        if (survivors <= 0) return 0f;
        int members = Math.max(1, groupSize);
        float leadLeft = Math.max(0f, memberHealth - Math.max(0f, centreBlast));
        float otherLeft = Math.max(0f, memberHealth - Math.max(0f, ringBlast));
        return (leadLeft + (members - 1) * otherLeft) / survivors;
    }

    /*
     * Formula: relicDoorOpenerTurns — DOOR_BLAST as expected turns to clear a reference fight
     * Derivation:
     *   A share p of fights (RELIC_VALUATION_DOOR_FIGHT_SHARE) opens through a door the player just opened.
     *   Opening a door is turn-less (the blast resolves inside requestOpen, before any enemy turn), so such a
     *   fight is the ordinary walk on the blast's survivors (relicBlastSurvivorCount / MeanHealth) with no
     *   extra turn — unlike barrelOpenerTurns, which spends the first turn on the barrel. Expected:
     *       turns = (1 - p) x turnsWithoutBlast + p x openerTurns
     *   Worked: 3.0 turns, opener 1.6, p 0.30 -> 2.58.
     *   APPROXIMATION: the blast is priced on the walk's OUTPUT for the opener fight only; ammo and the
     *   cleared flag stay the no-blast fight's (a blast only ever saves both).
     * Edge cases: p clamped to [0, 1]; negative turns read as 0. p 0 -> exactly turnsWithoutBlast.
     */
    public static float relicDoorOpenerTurns(float turnsWithoutBlast, float openerTurns, float doorFightShare) {
        float share = Math.max(0f, Math.min(1f, doorFightShare));
        return (1f - share) * Math.max(0f, turnsWithoutBlast) + share * Math.max(0f, openerTurns);
    }

    /*
     * Formula: relicCorpseBurstScenarioTurns — CORPSE_BLAST as turns to clear a group
     * Derivation:
     *   A group of n members of health h holds H = n x h. Each of the first n - 1 kills bursts b (fraction x
     *   the reference soldier's HP x the EXPLOSIVE matchup) into its neighbours, and an adjacency share a of
     *   it (RELIC_VALUATION_CORPSE_ADJACENCY) lands on members still standing. The last kill has nobody left
     *   to hit, and the first member must die to the weapon, so at most (n - 1) x h of it is useful:
     *       burst   = min((n - 1) x b x a, (n - 1) x h)
     *       turns'  = turns x (H - burst) / H
     *   The weapon clears HP at a steady rate in the walk, so removing a share of the group's HP removes the
     *   same share of its turns.
     *   APPROXIMATION: priced on the walk's OUTPUT (RISKS) — the burst's timing inside the fight (it lands
     *   only after the first kill) and its splitting across members are not walked.
     *   Worked: n 3, h 100, b 55, a 0.5 -> burst 55, turns x 245/300 = x0.817.
     * Edge cases: n <= 1, h <= 0 or b <= 0 -> exactly turns; a clamped to [0, 1].
     */
    public static float relicCorpseBurstScenarioTurns(float turns, int groupSize, float memberHealth,
                                                      float burstDamage, float adjacencyShare) {
        if (groupSize <= 1 || memberHealth <= 0f || burstDamage <= 0f) return turns;
        float adjacency = Math.max(0f, Math.min(1f, adjacencyShare));
        float groupHealth = groupSize * memberHealth;
        float burst = Math.min((groupSize - 1) * burstDamage * adjacency, (groupSize - 1) * memberHealth);
        return turns * (groupHealth - burst) / groupHealth;
    }

    /*
     * Formula: relicKillCredits — KILL_CREDIT_BONUS on a floor's credits
     * Derivation: credits' = credits + killCredits x bonus — the bonus pays only on the kill share of a
     *   floor's credits (pickup chips are untouched).
     *   Worked: 465 credits of which 300 from kills, bonus 0.40 -> 585.
     * Edge cases: negative bonus or kill credits read as 0; bonus 0 -> exactly credits.
     */
    public static float relicKillCredits(float credits, float killCredits, float killCreditBonus) {
        return credits + Math.max(0f, killCredits) * Math.max(0f, killCreditBonus);
    }

    /*
     * Formula: relicAmmoPowerPoints — KILL_AMMO_REFUND and FLOOR_ARRIVAL_AMMO as ammo power points per floor
     * Derivation (depth-1 power points, the supplyPowerPoints currency):
     *   refund   = bodies x killShare x chance x ppPerUnit     (one round per refunding kill, priced at the
     *              floor's mean planned ammo power points per unit)
     *   arrival  = arrivalFraction x arrivalCapPowerPoints     (the fraction of each carried type's reserve
     *              cap, the cap already priced per type and summed over the kit)
     *   ammo    += refund + arrival
     *   Worked: 8 x 0.6 x 0.3 x 0.12 + 0.1 x 30 = 3.17 PP.
     * Edge cases: negative inputs read as 0; relic-free -> exactly 0.
     */
    public static float relicAmmoPowerPoints(float bodiesPerFloor, float killShare, float refundChance,
                                             float powerPointsPerUnit, float arrivalFraction,
                                             float arrivalCapPowerPoints) {
        return Math.max(0f, bodiesPerFloor) * Math.max(0f, Math.min(1f, killShare))
                * Math.max(0f, Math.min(1f, refundChance)) * Math.max(0f, powerPointsPerUnit)
                + Math.max(0f, arrivalFraction) * Math.max(0f, arrivalCapPowerPoints);
    }

    /*
     * Formula: relicKitReserveCapPowerPoints — the reserve caps FLOOR_ARRIVAL_AMMO refills a fraction of
     * Derivation:
     *   The relic refills a fraction of EACH carried ammo type's reserve cap. The valuation's kit carries
     *   one gun per slot: the generalist (BULLETS, cap priced g) and slots - 1 others, whose type is not
     *   known to the supply model, each priced at the mean of the other types' caps o:
     *       capPoints = g + max(0, slots - 1) x o
     *   APPROXIMATION: two guns of one type would be refilled once at runtime; the kit model assumes distinct
     *   types (the R-CARRY kit pairs a class with the generalist, so it nearly always is).
     *   Worked: g 20, o 35, 2 slots -> 55 PP of caps; 0.10 of it -> 5.5 PP a floor.
     * Edge cases: slots < 1 reads as 1; negative inputs read as 0.
     */
    public static float relicKitReserveCapPowerPoints(float generalistCapPowerPoints, float otherCapPowerPoints,
                                                      int gunSlots) {
        return Math.max(0f, generalistCapPowerPoints) + Math.max(0, gunSlots - 1) * Math.max(0f, otherCapPowerPoints);
    }

    /*
     * Formula: relicExperiencePowerPoints — XP_MULTIPLIER as power points per floor (SupplyValue.experience)
     * Derivation:
     *   The on-curve player gains EXPECTED_LEVELS_PER_DEPTH levels a floor, each a pick worth
     *   EXPECTED_PERK_POWER_PP_PER_PICK. +x% experience from kills is x% more levels:
     *       experience = bonus x levelsPerDepth x ppPerPick
     *   Worked: 0.20 x 1.0 x 12 = 2.4 PP a floor.
     *   Assumes kill XP is the level clock (the XP curve is tuned so it is); compounding of the extra levels
     *   is ignored.
     * Edge cases: negative inputs read as 0; bonus 0 -> exactly 0.
     */
    public static float relicExperiencePowerPoints(float experienceBonus, float levelsPerDepth, float powerPointsPerPick) {
        return Math.max(0f, experienceBonus) * Math.max(0f, levelsPerDepth) * Math.max(0f, powerPointsPerPick);
    }

    /*
     * Formula: relicPowerPoints — a relic's carry lift in power points (offer pricing on the route)
     * Derivation:
     *   One perk rank is worth a carry lift of L (RELIC_PERK_RANK_EQUIVALENT_LIFT, measured ~1.08 at depth
     *   15) and costs SHOP_PERK_RANK_POWER_POINTS at the shop. Lifts compose by product, so a lift x is
     *   ln(x) / ln(L) ranks' worth:
     *       PP = SHOP_PERK_RANK_POWER_POINTS x ln(lift) / ln(L)
     *   Worked: lift 1.08 -> 12 PP; 1.166 (= 1.08^2) -> 24 PP; 1.04 -> 6.1 PP.
     * Edge cases: lift <= 1 or non-finite -> 0 (a relic that does not lift is worth nothing); L <= 1 -> 0.
     *   L and the per-rank points are read from BalanceConfig (constants: the method stays pure).
     */
    public static float relicPowerPoints(float lift) {
        float rankEquivalentLift = BalanceConfig.RELIC_PERK_RANK_EQUIVALENT_LIFT;
        if (!(lift > 1f) || Float.isInfinite(lift) || !(rankEquivalentLift > 1f)) return 0f;
        return Math.max(0f, BalanceConfig.SHOP_PERK_RANK_POWER_POINTS)
                * (float) (Math.log(lift) / Math.log(rankEquivalentLift));
    }

    /*
     * Formula: relicHandLift — the carry lift of a held hand of relics (R-RELIC-STACK)
     * Derivation:
     *   Lifts are ratios on the same carry scale (buildCarryScore), so independent relics compose by product.
     *   A card whose lift is below 1 is a card the player LEAVES (the TACTICAL rule), whatever its rarity — a
     *   CURSED relic's lift is already NET of its cost — so every term is clamped at 1:
     *       hand = prod_{i < count} max(1, lift_i)
     *   The clamp only ever raises the hand, so it is the conservative side of an upper-bound rule.
     *   Worked: {1.05, 1.10, 0.97} -> 1.155.
     * Edge cases: count clamped to [0, lifts.length]; null -> 1; a non-finite term is skipped.
     */
    public static float relicHandLift(float[] lifts, int count) {
        if (lifts == null) return 1f;
        int terms = Math.max(0, Math.min(count, lifts.length));
        double product = 1.0;
        for (int index = 0; index < terms; index++) {
            float lift = lifts[index];
            if (Float.isNaN(lift) || Float.isInfinite(lift)) continue;
            product *= Math.max(1f, lift);
        }
        return (float) product;
    }

    /*
     * Formula: relicExpectedHandLift — the expected hand as mean lifts raised to expected counts
     * Derivation:
     *   An acquisition source s contributes E_s expected relics (a take share, a boss, a node rate x floors),
     *   each drawn at its rarity-weighted mean lift m_s. With independent draws the expected log-lift adds,
     *   so the hand is (to first order)
     *       hand = prod_s max(1, m_s) ^ E_s
     *   The clamp is relicHandLift's (a below-1 card is left).
     *   Worked: start 1.05^1, two bosses 1.10^1 each, nodes 1.06^0.8 -> 1.33.
     * Edge cases: count clamped to the shorter array; negative exponents read as 0; null -> 1.
     */
    public static float relicExpectedHandLift(float[] meanLifts, float[] expectedCounts, int count) {
        if (meanLifts == null || expectedCounts == null) return 1f;
        int terms = Math.max(0, Math.min(count, Math.min(meanLifts.length, expectedCounts.length)));
        double logSum = 0.0;
        for (int index = 0; index < terms; index++) {
            float lift = meanLifts[index];
            if (Float.isNaN(lift) || Float.isInfinite(lift)) continue;
            logSum += Math.log(Math.max(1f, lift)) * Math.max(0f, expectedCounts[index]);
        }
        return (float) Math.exp(logSum);
    }

    /*
     * Formula: relicNodeOfferRate — expected relic offers per non-boss floor from route nodes
     * Derivation:
     *   A floor's node is drawn from the weighted node pool. A MYSTERY node rolls RELIC_CACHE with its share
     *   of the mystery pool; an EVENT node rolls quarantine_locker with its share of the event pool, and the
     *   player breaks the seal (takes the offer) with probability t:
     *       rate = (wMystery / wNodes) x (wCache / wMysteryPool)
     *            + (wEvent / wNodes) x (wLocker / wEventPool) x t
     *   Worked: 10/100 x 10/110 + 6/100 x 1/6 x 0.5 = 0.0141.
     * Edge cases: a non-positive pool total contributes 0; t clamped to [0, 1].
     */
    public static float relicNodeOfferRate(float mysteryNodeWeight, float eventNodeWeight, float nodeWeightTotal,
                                           float cacheWeight, float mysteryWeightTotal, float lockerWeight,
                                           float eventWeightTotal, float eventTakeShare) {
        if (nodeWeightTotal <= 0f) return 0f;
        float mystery = mysteryWeightTotal > 0f
                ? Math.max(0f, mysteryNodeWeight) / nodeWeightTotal * Math.max(0f, cacheWeight) / mysteryWeightTotal : 0f;
        float event = eventWeightTotal > 0f
                ? Math.max(0f, eventNodeWeight) / nodeWeightTotal * Math.max(0f, lockerWeight) / eventWeightTotal
                  * Math.max(0f, Math.min(1f, eventTakeShare)) : 0f;
        return mystery + event;
    }

    /** {@link #classifyMatchup} result: the multiplier is at or above the effective threshold. */
    public static final int MATCHUP_CLASS_EFFECTIVE = 1;
    /** {@link #classifyMatchup} result: the multiplier lies strictly between the thresholds. */
    public static final int MATCHUP_CLASS_NEUTRAL   = 0;
    /** {@link #classifyMatchup} result: the multiplier is at or below the resisted threshold. */
    public static final int MATCHUP_CLASS_RESISTED  = -1;
}
