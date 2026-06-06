package ge.tbegvadze.toon3d.util;

/**
 * Central balance configuration file for the XP progression system, enemy depth scaling,
 * and level-up stat rewards.  All numeric coefficients live here so a designer can tune
 * game-feel in a single place without touching game-logic classes.
 *
 * <h2>Relationship to Constants.java</h2>
 * {@link Constants} owns hard-wired structural values (tile size, render dimensions, key codes).
 * {@code GameBalance} owns tunable balance values (XP curves, enemy scaling, reward magnitudes).
 *
 * <h2>Formula delegation</h2>
 * Non-trivial math is delegated to {@link GameMath} static methods.  The helper methods in
 * this class are thin wrappers that supply the tuning constants and document the expected
 * outputs with example tables.
 */
public final class GameBalance {

    private GameBalance() {}

    // =========================================================================
    // XP REWARDS — XP dropped by each enemy archetype on death
    // =========================================================================

    /** CORRUPTOR (tile '1') — heavyweight melee; scarce but high XP. */
    public static final int XP_REWARD_CORRUPTOR  = 50;

    /** VORTEX_EYE (tile '2') — ranged kiter; medium XP for tricky targeting. */
    public static final int XP_REWARD_VORTEX_EYE = 35;

    /** GHOUL (tile '3') — fast light-melee; low XP but spawns in packs. */
    public static final int XP_REWARD_GHOUL      = 20;

    /** CRAWLER (tile '4') — light-melee variant; same XP tier as Ghoul. */
    public static final int XP_REWARD_CRAWLER    = 20;

    /** REVENANT (tile '5') — light-melee with armour; slightly more XP. */
    public static final int XP_REWARD_REVENANT   = 25;

    // =========================================================================
    // XP CURVE — how much XP is needed to reach each next player level
    //
    // Formula:  xpRequired(level) = XP_BASE * level ^ XP_CURVE_EXPONENT
    //   level 1 → 2:   100 * 1^1.5  =  100 XP   (~2 Corruptors)
    //   level 2 → 3:   100 * 2^1.5  =  283 XP   (~5-6 Corruptors)
    //   level 3 → 4:   100 * 3^1.5  =  520 XP   (~10 Corruptors)
    //   level 4 → 5:   100 * 4^1.5  =  800 XP
    //   level 5 → 6:   100 * 5^1.5  = 1118 XP
    // =========================================================================

    /** Base XP needed to advance from level 1 to level 2. */
    public static final int   XP_BASE_REQUIREMENT = 100;

    /** Exponent in the power curve.  1.5 = moderate acceleration (not linear, not exponential). */
    public static final float XP_CURVE_EXPONENT   = 1.5f;

    // =========================================================================
    // LEVEL-UP STAT BONUSES — applied once per level-up per chosen reward
    // =========================================================================

    /** Flat increase to the player's maximum HP when HP_BOOST is chosen. */
    public static final int LEVEL_UP_HP_BONUS     = 15;

    /** Flat increase to the player's maximum armour when ARMOR_BOOST is chosen. */
    public static final int LEVEL_UP_ARMOR_BONUS  = 10;

    /** Flat damage bonus added to every shot when DAMAGE_BOOST is chosen. */
    public static final int LEVEL_UP_DAMAGE_BONUS = 5;

    // =========================================================================
    // ENEMY DEPTH SCALING — enemies grow stronger on each new dungeon floor
    //
    // Health formula:  baseHP * HEALTH_SCALE ^ (depth − 1)
    // Damage formula:  baseDmg * DAMAGE_SCALE ^ (depth − 1)
    //   depth 1: ×1.00   depth 2: ×1.15   depth 3: ×1.32
    //   depth 4: ×1.52   depth 5: ×1.75   depth 10: ×3.52
    // =========================================================================

    /** Per-floor HP multiplier applied as a compound factor. */
    public static final float ENEMY_HEALTH_SCALE_PER_DEPTH = 1.15f;

    /** Per-floor damage multiplier applied as a compound factor. */
    public static final float ENEMY_DAMAGE_SCALE_PER_DEPTH = 1.10f;

    // =========================================================================
    // Derived-value helpers — thin wrappers delegating math to GameMath
    // =========================================================================

    /**
     * Returns the XP needed to advance from {@code currentLevel} to the next level.
     * Delegates to {@link GameMath#xpRequiredForLevel(int, float, int)}.
     */
    public static int xpRequiredForLevel(int currentLevel) {
        return GameMath.xpRequiredForLevel(XP_BASE_REQUIREMENT, XP_CURVE_EXPONENT, currentLevel);
    }

    /**
     * Returns the compound HP scale multiplier for the given dungeon depth.
     * depth=1 → 1.0 (no scaling); depth=2 → {@value #ENEMY_HEALTH_SCALE_PER_DEPTH}; etc.
     */
    public static float enemyHealthScaleForDepth(int dungeonDepth) {
        return GameMath.compoundScaleForDepth(ENEMY_HEALTH_SCALE_PER_DEPTH, dungeonDepth);
    }

    /**
     * Returns the compound damage scale multiplier for the given dungeon depth.
     * depth=1 → 1.0 (no scaling); depth=2 → {@value #ENEMY_DAMAGE_SCALE_PER_DEPTH}; etc.
     */
    public static float enemyDamageScaleForDepth(int dungeonDepth) {
        return GameMath.compoundScaleForDepth(ENEMY_DAMAGE_SCALE_PER_DEPTH, dungeonDepth);
    }
}
