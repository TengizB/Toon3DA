package ge.tbegvadze.toon3d.render;

import ge.tbegvadze.toon3d.util.ArtDirection;
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
 */
public final class AtmosphereGrade {

    /** Result channels of the last {@link #evaluate} / {@link #evaluateWeaponTint} call. */
    public float red = 1f;
    public float green = 1f;
    public float blue = 1f;

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
            return;
        }
        float fog = GameMath.atmosphereFogFactor(distanceTiles, current.fogStartTiles(), current.fogEndTiles());
        red   = whiteToTint(GameMath.lerp(current.nearRed(),   current.farRed(),   fog), strength);
        green = whiteToTint(GameMath.lerp(current.nearGreen(), current.farGreen(), fog), strength);
        blue  = whiteToTint(GameMath.lerp(current.nearBlue(),  current.farBlue(),  fog), strength);
    }

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
