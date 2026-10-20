package ge.tbegvadze.toon3d.render;

import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.util.ArtDirection;
import ge.tbegvadze.toon3d.util.EffectConstants;
import ge.tbegvadze.toon3d.util.GameMath;

/**
 * The per-floor colour grade evaluator shared by every world renderer (rule R1/R6 of
 * visual-identity-order-1). Holds the floor's current {@link LightingMood}; renderers ask it for an RGB
 * multiplier for a surface at a distance with a surface-class strength.
 *
 * <p>Two ways to evaluate, both allocation-free:
 * <ul>
 *   <li>{@link #evaluate} / {@link #evaluateWeaponTint} write the public {@link #red}/{@link #green}/
 *       {@link #blue} fields. NOT thread-safe: render-thread use only (per-column in WallRenderer, per
 *       sprite in billboard renderers).</li>
 *   <li>{@link #evaluateInto} is a pure static that writes into a caller-owned array and touches no shared
 *       state, so a renderer can fill per-row tint arrays (FloorCeilingRenderer, R13) without going
 *       through the mutable fields. Worker threads may read only the immutable mood and arrays filled
 *       before they start.</li>
 * </ul>
 * The mood is set when a floor is built, before its first frame, and never while workers run.
 *
 * <p>MUZZLE LIGHT (visual-identity-order-2, R8/R9). The player's shot adds a short light term. THREADING
 * CONTRACT: the light state ({@link #lightIntensity} and the light colour) is a per-frame SNAPSHOT. It is
 * mutated ONLY by {@link #pulseMuzzleLight}, {@link #update} and {@link #resetLight}, all of which run on
 * the render thread inside World.update, strictly BEFORE any renderer starts its frame (and therefore
 * before worker tasks are dispatched; the executor hand-off publishes it). Workers and render code only
 * READ it, via {@link #lightAddInto} (pure, thread-safe given that contract) or the render-thread fields
 * written by {@link #evaluate}. While the world is hard-paused update() is not called, so the snapshot
 * simply holds its last value. The light is NOT folded into the clamped colour multiplier (near
 * surfaces already sit at ~1.0, so it would be clamped away). The base colour stays
 * {@code clamp01(shade x tint)}; the light is applied AFTER that clamp as an additive albedo pass,
 * {@code albedo x lightAdd}: a second draw with additive blending (GL_SRC_ALPHA, GL_ONE) for walls, doors,
 * props, the shop body and enemies, and, for the GPU-span floor and ceiling (the same additive pass),
 * {@code GameMath.floorSpanRedFactor} / {@code floorSpanGreenBlueFactor}. Only the final colour clamps. See
 * docs/art-direction.txt section 11.
 */
public final class AtmosphereGrade {

    /** Result channels of the last {@link #evaluate} / {@link #evaluateWeaponTint} call. */
    public float red = 1f;
    public float green = 1f;
    public float blue = 1f;

    /** Light-add channels of the last {@link #evaluate} call (zero when no pulse). Render thread only. */
    public float lightAddRed;
    public float lightAddGreen;
    public float lightAddBlue;

    // Muzzle-light snapshot (see class javadoc for the threading contract).
    private float lightIntensity;
    private float lightRed;
    private float lightGreen;
    private float lightBlue;
    private float lightPeak;
    private float lightElapsedSeconds;

    private LightingMood mood;

    public AtmosphereGrade() {}

    public void setMood(LightingMood mood) {
        this.mood = mood;
    }

    public LightingMood getMood() {
        return mood;
    }

    /** R1: fog, lerp near/far, lerp towards white by strength. Writes red/green/blue; white if no mood. */
    public void evaluate(float distanceTiles, float strength) {
        LightingMood current = mood;
        if (current == null) {
            red = 1f;
            green = 1f;
            blue = 1f;
        } else {
            float fog = GameMath.atmosphereFogFactor(distanceTiles, current.fogStartTiles(), current.fogEndTiles());
            red   = whiteToTint(GameMath.lerp(current.nearRed(),   current.farRed(),   fog), strength);
            green = whiteToTint(GameMath.lerp(current.nearGreen(), current.farGreen(), fog), strength);
            blue  = whiteToTint(GameMath.lerp(current.nearBlue(),  current.farBlue(),  fog), strength);
        }
        if (lightIntensity == 0f) {
            lightAddRed = 0f;
            lightAddGreen = 0f;
            lightAddBlue = 0f;
        } else {
            float light = lightAt(lightIntensity, distanceTiles);
            lightAddRed   = light * lightRed;
            lightAddGreen = light * lightGreen;
            lightAddBlue  = light * lightBlue;
        }
    }

    /** True while a muzzle pulse is lighting the world (branch-skip for the light term, R10). */
    public boolean isLightActive() {
        return lightIntensity != 0f;
    }

    /** Starts (or restarts, never stacks) a muzzle pulse for a weapon; peak 0 (melee) is ignored. */
    public void pulseMuzzleLight(ItemType weaponItemType) {
        float peak = MuzzleLightCatalog.peakOf(weaponItemType);
        if (peak <= 0f) return;
        lightPeak = peak;
        lightRed = MuzzleLightCatalog.redOf(weaponItemType);
        lightGreen = MuzzleLightCatalog.greenOf(weaponItemType);
        lightBlue = MuzzleLightCatalog.blueOf(weaponItemType);
        lightElapsedSeconds = 0f;
        lightIntensity = peak;
    }

    /** Advances the pulse clock (R9). World calls it only when the world is not hard-paused. */
    public void update(float deltaTime) {
        if (lightIntensity == 0f) return;
        lightElapsedSeconds += deltaTime;
        lightIntensity = Math.max(0f,
                lightPeak * (1f - lightElapsedSeconds / EffectConstants.MUZZLE_LIGHT_DURATION_SECONDS));
    }

    /** Clears any pulse (floor build). */
    public void resetLight() {
        lightIntensity = 0f;
        lightElapsedSeconds = 0f;
        lightPeak = 0f;
    }

    /** Light multiplier for a surface {@code distanceTiles} away: falloff x intensity. */
    public static float lightAt(float intensity, float distanceTiles) {
        return GameMath.muzzleLightFalloff(distanceTiles, EffectConstants.MUZZLE_LIGHT_RADIUS_TILES) * intensity;
    }

    /**
     * Thread-safe (read-only on the frame snapshot): writes the light-add RGB for a surface at
     * {@code distanceTiles} into {@code outRgb[outOffset..+2]}; zeros when no pulse is active.
     */
    public void lightAddInto(float distanceTiles, float[] outRgb, int outOffset) {
        float intensity = lightIntensity;
        if (intensity == 0f) {
            outRgb[outOffset] = 0f;
            outRgb[outOffset + 1] = 0f;
            outRgb[outOffset + 2] = 0f;
            return;
        }
        float light = lightAt(intensity, distanceTiles);
        outRgb[outOffset]     = light * lightRed;
        outRgb[outOffset + 1] = light * lightGreen;
        outRgb[outOffset + 2] = light * lightBlue;
    }

    /** The frame's current pulse intensity (0 when idle). */
    public float getLightIntensity() { return lightIntensity; }
    public float getLightRed()   { return lightRed; }
    public float getLightGreen() { return lightGreen; }
    public float getLightBlue()  { return lightBlue; }

    /**
     * R6: weapon colour = lerp(WHITE, nearTint, GRADE_STRENGTH_WEAPON) x clamp(playerTileBrightness,
     * WEAPON_LIGHT_MIN, WEAPON_LIGHT_MAX), each channel clamped to 1. No distance term.
     */
    public void evaluateWeaponTint(float playerTileBrightness) {
        LightingMood current = mood;
        float light = Math.max(ArtDirection.WEAPON_LIGHT_MIN,
                Math.min(ArtDirection.WEAPON_LIGHT_MAX, playerTileBrightness));
        float strength = ArtDirection.GRADE_STRENGTH_WEAPON;
        float tintRed   = current == null ? 1f : whiteToTint(current.nearRed(),   strength);
        float tintGreen = current == null ? 1f : whiteToTint(current.nearGreen(), strength);
        float tintBlue  = current == null ? 1f : whiteToTint(current.nearBlue(),  strength);
        red   = Math.min(1f, tintRed   * light);
        green = Math.min(1f, tintGreen * light);
        blue  = Math.min(1f, tintBlue  * light);
    }

    /**
     * Pure, thread-safe variant of {@link #evaluate}: writes the graded tint for {@code mood} into
     * {@code outRgb[outOffset .. outOffset+2]}. A null mood writes white.
     */
    public static void evaluateInto(LightingMood mood, float distanceTiles, float strength,
                                    float[] outRgb, int outOffset) {
        if (mood == null) {
            outRgb[outOffset] = 1f;
            outRgb[outOffset + 1] = 1f;
            outRgb[outOffset + 2] = 1f;
            return;
        }
        float fog = GameMath.atmosphereFogFactor(distanceTiles, mood.fogStartTiles(), mood.fogEndTiles());
        outRgb[outOffset]     = whiteToTint(GameMath.lerp(mood.nearRed(),   mood.farRed(),   fog), strength);
        outRgb[outOffset + 1] = whiteToTint(GameMath.lerp(mood.nearGreen(), mood.farGreen(), fog), strength);
        outRgb[outOffset + 2] = whiteToTint(GameMath.lerp(mood.nearBlue(),  mood.farBlue(),  fog), strength);
    }

    private static float whiteToTint(float tintChannel, float strength) {
        return GameMath.lerp(1f, tintChannel, strength);
    }
}
