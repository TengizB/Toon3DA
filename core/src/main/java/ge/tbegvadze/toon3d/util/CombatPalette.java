package ge.tbegvadze.toon3d.util;

/**
 * The single shared COLOUR LANGUAGE for the whole strategy-combat series (orders 1-6).
 *
 * <p>The onboarding/clarity pass (strategy-combat-order-6) mandates one consistent palette so a
 * player learns "red = it hurts me, blue = defence, purple = special" ONCE and it never drifts
 * between the intent icons (order-2), the Block/Guard shields (order-3/4), the enemy sprite tints,
 * and the order-6 power indicators. Every combat-communication colour should be sourced from here
 * rather than being re-typed as a literal at each call site.
 *
 * <p>Colour language:
 * <ul>
 *   <li>RED     — incoming damage / offensive threat (attacks, VULNERABLE)</li>
 *   <li>BLUE    — defence: Block, Guard, DEFEND intent</li>
 *   <li>PURPLE  — special abilities and the EXPOSED anti-turtle power</li>
 *   <li>YELLOW  — movement / repositioning intent</li>
 *   <li>GREEN   — a beneficial buff (on whoever holds it)</li>
 *   <li>GREY-PURPLE — a dampening debuff (WEAK): the host's output is dimmed</li>
 * </ul>
 *
 * <p>Values are plain floats in [0,1] (not LibGDX {@code Color} objects) so this class stays a
 * dependency-free member of {@code util} and callers can feed the channels straight into
 * {@code SpriteBatch.setColor(...)} / {@code GameMath.lerp(...)} without allocating.
 */
public final class CombatPalette {

    private CombatPalette() {}

    // RED — incoming damage / VULNERABLE (the host takes more).
    public static final float DAMAGE_RED   = 0.90f;
    public static final float DAMAGE_GREEN = 0.16f;
    public static final float DAMAGE_BLUE  = 0.12f;

    // Order-6 VULNERABLE tint (red — "you take more / hit this now"). This is the single source for
    // the VULNERABLE entry in the HudRenderer and StatusEffectVignetteRenderer colour arrays.
    public static final float VULNERABLE_RED   = 0.90f;
    public static final float VULNERABLE_GREEN = 0.30f;
    public static final float VULNERABLE_BLUE  = 0.10f;

    // BLUE — defence (Block / Guard / DEFEND).
    public static final float DEFENSE_RED   = 0.20f;
    public static final float DEFENSE_GREEN = 0.48f;
    public static final float DEFENSE_BLUE  = 0.95f;

    // PURPLE — special abilities.
    public static final float SPECIAL_RED   = 0.62f;
    public static final float SPECIAL_GREEN = 0.24f;
    public static final float SPECIAL_BLUE  = 0.86f;

    // PURPLE — EXPOSED (anti-turtle power). Single source for the EXPOSED colour-array entries.
    public static final float EXPOSED_RED   = 0.70f;
    public static final float EXPOSED_GREEN = 0.20f;
    public static final float EXPOSED_BLUE  = 0.90f;

    // GREY-PURPLE — WEAK (the host's output is dampened). Single source for the WEAK array entries.
    public static final float WEAK_RED   = 0.55f;
    public static final float WEAK_GREEN = 0.50f;
    public static final float WEAK_BLUE  = 0.65f;

    // GREEN — a beneficial buff.
    public static final float BUFF_RED   = 0.25f;
    public static final float BUFF_GREEN = 0.85f;
    public static final float BUFF_BLUE  = 0.30f;
}
