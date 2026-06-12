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

    /** PLAGUE_HULK (tile '1') — slow tank melee; tanky so yields solid XP. */
    public static final int XP_REWARD_PLAGUE_HULK   = 8;

    /** EYE_TYRANT (tile '2') — fast ranged kiter; low XP, common annoyance. */
    public static final int XP_REWARD_EYE_TYRANT    = 6;

    /** GORE_BITER (tile '3') — fast light melee; low XP, spawns in packs. */
    public static final int XP_REWARD_GORE_BITER    = 6;

    /** SHELL_BRUTE (tile '4') — heavy charger melee; more XP for the threat. */
    public static final int XP_REWARD_SHELL_BRUTE   = 10;

    /** MIRE_WRAITH (tile '5') — slow hovering acid ranged; high XP, tanky. */
    public static final int XP_REWARD_MIRE_WRAITH   = 16;

    /** IRON_STALKER (tile '!') — armored elite melee+ranged; the big reward. */
    public static final int XP_REWARD_IRON_STALKER  = 35;

    /** ACID_DRONE (tile '$') — ranged mechanical; medium XP. */
    public static final int XP_REWARD_ACID_DRONE    = 8;

    /** VOID_SHROUD (tile '^') — fast stealth melee; medium XP. */
    public static final int XP_REWARD_VOID_SHROUD   = 12;

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

    // =========================================================================
    // STAT SYSTEM — per-difficulty base values, caps, and per-point effect rates
    //
    // Convention: STAT_REFERENCE = 0 means the formula is anchored at zero so a
    // fresh MARINE (STR 2) already has a small bonus (+10% melee).  This matches
    // the literal user spec "STR 5 = +25% melee".
    // =========================================================================

    // ---- Per-difficulty base values (16 entries: 4 difficulties × 4 attributes) ----

    /** RECRUIT (easy) starting STRENGTH — all-round generous padding. */
    public static final int STAT_BASE_RECRUIT_STR  = 3;
    /** RECRUIT (easy) starting AGILITY. */
    public static final int STAT_BASE_RECRUIT_AGI  = 3;
    /** RECRUIT (easy) starting TOUGHNESS — extra HP budget. */
    public static final int STAT_BASE_RECRUIT_TGH  = 4;
    /** RECRUIT (easy) starting MARKSMANSHIP. */
    public static final int STAT_BASE_RECRUIT_MRK  = 3;

    /** MARINE (normal) starting STRENGTH — the tuning reference loadout. */
    public static final int STAT_BASE_MARINE_STR   = 2;
    /** MARINE (normal) starting AGILITY. */
    public static final int STAT_BASE_MARINE_AGI   = 2;
    /** MARINE (normal) starting TOUGHNESS. */
    public static final int STAT_BASE_MARINE_TGH   = 2;
    /** MARINE (normal) starting MARKSMANSHIP. */
    public static final int STAT_BASE_MARINE_MRK   = 2;

    /** NIGHTMARE (hard) starting STRENGTH — lean start; growth depends on perk choices. */
    public static final int STAT_BASE_NIGHTMARE_STR = 1;
    /** NIGHTMARE (hard) starting AGILITY. */
    public static final int STAT_BASE_NIGHTMARE_AGI = 1;
    /** NIGHTMARE (hard) starting TOUGHNESS. */
    public static final int STAT_BASE_NIGHTMARE_TGH = 1;
    /** NIGHTMARE (hard) starting MARKSMANSHIP. */
    public static final int STAT_BASE_NIGHTMARE_MRK = 1;

    /** ULTRA (brutal) starting STRENGTH — minimal; mastery required. */
    public static final int STAT_BASE_ULTRA_STR    = 0;
    /** ULTRA (brutal) starting AGILITY — one point to enable some dodge. */
    public static final int STAT_BASE_ULTRA_AGI    = 1;
    /** ULTRA (brutal) starting TOUGHNESS — one point of flat reduction. */
    public static final int STAT_BASE_ULTRA_TGH    = 1;
    /** ULTRA (brutal) starting MARKSMANSHIP — zero bonus ranged. */
    public static final int STAT_BASE_ULTRA_MRK    = 0;

    // ---- Stat caps (each attribute independently capped) ----

    /** Maximum effective STRENGTH; caps melee bonus at +60%. */
    public static final int STAT_CAP_STRENGTH      = 12;

    /**
     * Maximum effective AGILITY; gated by DODGE_CAP and AGI_MIN_DURATION_MULT
     * before this ceiling is actually reached.
     */
    public static final int STAT_CAP_AGILITY       = 10;

    /** Maximum effective TOUGHNESS; caps at +60 max HP and -12 flat damage reduction. */
    public static final int STAT_CAP_TOUGHNESS     = 12;

    /** Maximum effective MARKSMANSHIP; caps ranged bonus at +48% and accuracy at +36%. */
    public static final int STAT_CAP_MARKSMANSHIP  = 12;

    /** Minimum effective value for any attribute (floor for getEffective). */
    public static final int STAT_MIN               = 0;

    /**
     * Reference stat value at which every multiplier equals exactly 1.0 (no bonus).
     * Set to 0 so the literal spec "STR 5 = +25% melee" holds: 1.0 + 5 × 0.05 = 1.25.
     * A fresh MARINE (STR 2) therefore starts at 1.10× melee — small but visible bonus.
     */
    public static final int STAT_REFERENCE         = 0;

    // ---- Per-point effect rates ----

    /** Each STRENGTH point adds this fraction to the melee damage multiplier. */
    public static final float STR_MELEE_PER_POINT     = 0.05f;

    /** Each MARKSMANSHIP point adds this fraction to the ranged damage multiplier. */
    public static final float MRK_DAMAGE_PER_POINT    = 0.04f;

    /**
     * Each MARKSMANSHIP point adds this fraction to the accuracy multiplier.
     * Used to tighten shotgun spread or reduce future miss-chance rolls.
     */
    public static final float MRK_ACCURACY_PER_POINT  = 0.03f;

    /**
     * Each AGILITY point reduces the action duration multiplier by this fraction.
     * Duration multiplier = 1.0 − (AGI − STAT_REFERENCE) × AGI_SPEED_PER_POINT,
     * clamped to [AGI_MIN_DURATION_MULT, 1.0].
     */
    public static final float AGI_SPEED_PER_POINT     = 0.03f;

    /**
     * Hard floor for the action duration multiplier (55% of base duration).
     * Prevents animation from becoming too fast to read at high AGILITY values.
     */
    public static final float AGI_MIN_DURATION_MULT   = 0.55f;

    /** Each AGILITY point adds this fraction to the raw dodge chance before capping. */
    public static final float AGI_DODGE_PER_POINT     = 0.02f;

    /**
     * Maximum dodge probability regardless of AGILITY.
     * 35% cap keeps high-AGI builds slippery without becoming immune to hits.
     */
    public static final float DODGE_CAP               = 0.35f;

    /** Each TOUGHNESS point adds this many HP to the player's maximum HP pool. */
    public static final int   TGH_HP_PER_POINT        = 5;

    /** Each TOUGHNESS point shaves this many points off every HP-bound incoming hit. */
    public static final int   TGH_REDUCTION_PER_POINT = 1;

    /**
     * Minimum HP damage after TOUGHNESS flat reduction.
     * Chip damage (poison ticks, explosions) always deals at least 1 HP.
     */
    public static final int   TGH_MIN_DAMAGE          = 1;
}
