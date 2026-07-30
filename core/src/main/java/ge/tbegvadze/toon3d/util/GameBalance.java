package ge.tbegvadze.toon3d.util;

import ge.tbegvadze.toon3d.entity.WeaponTier;

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
    // XP REWARDS — DERIVED, not hand-set (new-game-balancr order 4)
    // =========================================================================

    // The thirteen per-archetype XP_REWARD_* re-exports that lived here are GONE. Per-enemy XP is now
    // DERIVED from Threat Points (EnemyType.baseXpReward -> GameMath.xpRewardAtDepth, one knob
    // BalanceConfig.XP_PER_THREAT_POINT). Boss XP is DERIVED too (order 6): BossBalance prices it from the
    // boss's depth-scaled Threat Points, so the flat XP_REWARD_BOSS_BASE placeholder is DELETED — a boss's
    // XP flows through EnemyType.baseXpReward -> BossBalance.statsForDepth(depth).xpReward.

    // =========================================================================
    // XP CURVE — GEOMETRIC, coupled to the enemy threat compound (order 4)
    //
    // Formula:  xpRequired(level) = XP_BASE * XP_CURVE_GROWTH_PER_LEVEL ^ (level - 1)
    //   level 1 → 2:   150 * 1.118^0 =  150 XP
    //   level 5 → 6:   150 * 1.118^4 =  234 XP
    //   level 10 → 11: 150 * 1.118^9 =  409 XP
    // Because per-enemy XP is DERIVED from depth-scaled Threat Points and the requirement grows at the
    // same geometric rate as enemy threat, the player gains ~1 level/floor at EVERY depth (R-XP-PACE).
    // =========================================================================

    /** Base XP needed to advance from level 1 to level 2. (Balance: BalanceConfig.) */
    public static final int   XP_BASE_REQUIREMENT = BalanceConfig.XP_BASE_REQUIREMENT;

    /** Per-level geometric growth of the XP requirement, tied to the enemy threat compound. (Balance: BalanceConfig.) */
    public static final float XP_CURVE_GROWTH_PER_LEVEL = BalanceConfig.XP_CURVE_GROWTH_PER_LEVEL;

    // =========================================================================
    // LEVEL-UP STAT BONUSES — applied once per level-up per chosen reward
    // =========================================================================

    // Level-up reward magnitudes are balance values — see BalanceConfig.
    /** Flat max-HP gained from the Vitality card (legacy HP boon, re-priced to budget). */
    public static final int LEVEL_UP_HP_BONUS     = BalanceConfig.LEVEL_UP_HP_BONUS;

    /** Flat max-armour gained from the Combat Armour card (legacy armour boon, re-priced to budget). */
    public static final int LEVEL_UP_ARMOR_BONUS  = BalanceConfig.LEVEL_UP_ARMOR_BONUS;

    /** Flat per-shot damage gained from the Hollow Points card (legacy damage boon, re-priced to budget). */
    public static final int LEVEL_UP_DAMAGE_BONUS = BalanceConfig.LEVEL_UP_DAMAGE_BONUS;

    // =========================================================================
    // LEVEL-UP CARD SYSTEM — power budget & card magnitudes (idea 5)
    // Each card costs the same budget; the total-PP invariant keeps every build
    // on the depth-coupling curve. See BalanceConfig for the full contract.
    // =========================================================================

    /** Fixed power budget (power points) every level-up card costs. (Balance: BalanceConfig.) */
    public static final float LEVEL_UP_BUDGET_PP          = BalanceConfig.LEVEL_UP_BUDGET_PP;
    /** Allowed ±fraction a single card's PP may stray from the budget. (Balance: BalanceConfig.) */
    public static final float LEVEL_UP_BUDGET_TOLERANCE   = BalanceConfig.LEVEL_UP_BUDGET_TOLERANCE;
    /** Number of cards drawn and offered per level-up. (Balance: BalanceConfig.) */
    public static final int   LEVEL_UP_CARDS_OFFERED      = BalanceConfig.LEVEL_UP_CARDS_OFFERED;
    /** Draw-weight bonus per prior pick that biases offers toward the emerging build. (Balance: BalanceConfig.) */
    public static final float LEVEL_UP_DRAW_BIAS_PER_PICK = BalanceConfig.LEVEL_UP_DRAW_BIAS_PER_PICK;

    /** Fraction of a flat per-shot damage bonus that lands as sustained DPT at the reference weapon. (Balance: BalanceConfig.) */
    public static final float CARD_FLAT_DAMAGE_DPT_FRACTION = BalanceConfig.CARD_FLAT_DAMAGE_DPT_FRACTION;
    /** Average fraction of attacks made with a melee weapon — discounts STRENGTH-card pricing. (Balance: BalanceConfig.) */
    public static final float CARD_MELEE_UTILIZATION        = BalanceConfig.CARD_MELEE_UTILIZATION;
    /** Reference incoming hit used when pricing TOUGHNESS flat reduction into eHP. (Balance: BalanceConfig.) */
    public static final int   CARD_PRICING_AVERAGE_HIT      = BalanceConfig.CARD_PRICING_AVERAGE_HIT;

    /** STRENGTH points granted by the Brutal Strength card. (Balance: BalanceConfig.) */
    public static final int CARD_STRENGTH_STEP     = BalanceConfig.CARD_STRENGTH_STEP;
    /** MARKSMANSHIP points granted by the Marksman Training card. (Balance: BalanceConfig.) */
    public static final int CARD_MARKSMANSHIP_STEP = BalanceConfig.CARD_MARKSMANSHIP_STEP;
    /** AGILITY points granted by the Evasion Training card. (Balance: BalanceConfig.) */
    public static final int CARD_AGILITY_STEP      = BalanceConfig.CARD_AGILITY_STEP;
    /** TOUGHNESS points granted by the Toughened Hide card. (Balance: BalanceConfig.) */
    public static final int CARD_TOUGHNESS_STEP    = BalanceConfig.CARD_TOUGHNESS_STEP;

    /** Glass Cannon trade-off: flat per-shot damage gained. (Balance: BalanceConfig.) */
    public static final int CARD_GLASS_CANNON_DAMAGE      = BalanceConfig.CARD_GLASS_CANNON_DAMAGE;
    /** Glass Cannon trade-off: Max-HP sacrificed. (Balance: BalanceConfig.) */
    public static final int CARD_GLASS_CANNON_HP_COST     = BalanceConfig.CARD_GLASS_CANNON_HP_COST;
    /** Iron Constitution trade-off: Max-HP gained. (Balance: BalanceConfig.) */
    public static final int CARD_IRON_CONSTITUTION_HP     = BalanceConfig.CARD_IRON_CONSTITUTION_HP;
    /** Iron Constitution trade-off: Max-armour sacrificed. (Balance: BalanceConfig.) */
    public static final int CARD_IRON_CONSTITUTION_ARMOR  = BalanceConfig.CARD_IRON_CONSTITUTION_ARMOR;
    /** Reckless Charge trade-off: AGILITY gained. (Balance: BalanceConfig.) */
    public static final int CARD_RECKLESS_CHARGE_AGILITY  = BalanceConfig.CARD_RECKLESS_CHARGE_AGILITY;
    /** Reckless Charge trade-off: Max-armour sacrificed. (Balance: BalanceConfig.) */
    public static final int CARD_RECKLESS_CHARGE_ARMOR    = BalanceConfig.CARD_RECKLESS_CHARGE_ARMOR;

    /** Reference player DPT — denominator for damage-based card power points. (Balance: BalanceConfig.) */
    public static final float REFERENCE_PLAYER_DPT = BalanceConfig.REFERENCE_PLAYER_DPT;
    /** Reference player eHP — denominator for survivability-based card power points. (Balance: BalanceConfig.) */
    public static final float REFERENCE_PLAYER_EHP = BalanceConfig.REFERENCE_PLAYER_EHP;
    /** Reference player Max-HP — base for pricing TOUGHNESS reduction into eHP. (Balance: BalanceConfig.) */
    public static final int   PLAYER_MAX_HEALTH    = BalanceConfig.PLAYER_MAX_HEALTH;
    /** Reference player Max-armour — base for pricing TOUGHNESS reduction into eHP. (Balance: BalanceConfig.) */
    public static final int   PLAYER_MAX_ARMOR     = BalanceConfig.PLAYER_MAX_ARMOR;

    // =========================================================================
    // ENEMY DEPTH SCALING — enemies grow stronger on each new dungeon floor
    //
    // Health formula:  baseHP * HEALTH_SCALE ^ (depth − 1)
    //   depth 1: ×1.00   depth 2: ×1.08   depth 3: ×1.17
    //   depth 4: ×1.26   depth 5: ×1.36   depth 10: ×2.00
    //
    // Damage formula:  baseDmg * DAMAGE_SCALE ^ (depth − 1)
    //   depth 1: ×1.00   depth 2: ×1.06   depth 3: ×1.12
    //   depth 4: ×1.19   depth 5: ×1.26   depth 10: ×1.69
    // =========================================================================

    // Depth scaling factors are balance values — see BalanceConfig.
    /** Per-floor HP multiplier applied as a compound factor. */
    public static final float ENEMY_HEALTH_SCALE_PER_DEPTH = BalanceConfig.ENEMY_HEALTH_SCALE_PER_DEPTH;

    /** Per-floor damage multiplier applied as a compound factor. */
    public static final float ENEMY_DAMAGE_SCALE_PER_DEPTH = BalanceConfig.ENEMY_DAMAGE_SCALE_PER_DEPTH;

    // =========================================================================
    // Derived-value helpers — thin wrappers delegating math to GameMath
    // =========================================================================

    /**
     * Returns the XP needed to advance from {@code currentLevel} to the next level.
     * Delegates to {@link GameMath#xpRequiredForLevelGeometric(int, float, int)} — the geometric curve
     * coupled to the enemy threat compound (new-game-balancr order 4).
     */
    public static int xpRequiredForLevel(int currentLevel) {
        return GameMath.xpRequiredForLevelGeometric(XP_BASE_REQUIREMENT, XP_CURVE_GROWTH_PER_LEVEL, currentLevel);
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
    // MELEE WEAPON STAT BLOCKS — base damage per weapon type
    // All four v1 melee weapons have turnsPerAttack=1 (one swing = one world turn).
    // =========================================================================

    // Melee base damage values are balance — see BalanceConfig.
    /** FIST — always-equipped fallback; chip damage only, never dropped. */
    public static final int MELEE_FIST_DAMAGE          = BalanceConfig.MELEE_FIST_DAMAGE;

    /** COMBAT KNIFE — fast light melee, good vs low-HP chaff. */
    public static final int MELEE_KNIFE_DAMAGE         = BalanceConfig.MELEE_KNIFE_DAMAGE;

    /** HAMMER — heavy crowd-control melee; knockback eligible targets by HAMMER_KNOCKBACK_TILES. */
    public static final int MELEE_HAMMER_DAMAGE          = BalanceConfig.MELEE_HAMMER_DAMAGE;

    /** How many tiles a knockback-eligible enemy is pushed on a Hammer hit. */
    public static final int   MELEE_HAMMER_KNOCKBACK_TILES  = 1;
    /** Probability (0–1) that a Hammer hit triggers knockback; 50% per swing. */
    public static final float MELEE_HAMMER_KNOCKBACK_CHANCE = 0.50f;

    /** CHAINSAW — high sustained damage; no knockback (grinds in place). */
    public static final int MELEE_CHAINSAW_DAMAGE      = BalanceConfig.MELEE_CHAINSAW_DAMAGE;

    /** Probability (0–1) that a melee kill drops an ammo pickup; higher than the ranged baseline. (Balance: BalanceConfig.) */
    public static final float MELEE_KILL_AMMO_DROP_CHANCE = BalanceConfig.MELEE_KILL_AMMO_DROP_CHANCE;

    // =========================================================================
    // STAT SYSTEM — caps and per-point effect rates
    //
    // Convention: STAT_REFERENCE = 0 means the formula is anchored at zero so a
    // fresh player (STR 2) already has a small bonus (+10% melee).  This matches
    // the literal user spec "STR 5 = +25% melee".
    //
    // ONE DIFFICULTY (new-game-balancr order 8): the four per-mode starting-
    // attribute tables that used to live here are DELETED.  The game has one
    // starting attribute block and it lives with the other progression anchors in
    // BalanceConfig SECTION 7 (PLAYER_START_STRENGTH / _AGILITY / _TOUGHNESS /
    // _MARKSMANSHIP).  BalanceSchema's R-SINGLE-DIFFICULTY rule fails the build if
    // mode-family naming ever reappears in a constant file.
    // =========================================================================

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
     * A fresh player (STR 2) therefore starts at 1.10× melee — small but visible bonus.
     */
    public static final int STAT_REFERENCE         = 0;

    // ---- Per-point effect rates ----

    /** Each STRENGTH point adds this fraction to the melee damage multiplier. */
    public static final float STR_MELEE_PER_POINT     = BalanceConfig.STR_MELEE_PER_POINT;

    /** Each MARKSMANSHIP point adds this fraction to the ranged damage multiplier. */
    public static final float MRK_DAMAGE_PER_POINT    = BalanceConfig.MRK_DAMAGE_PER_POINT;

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
    public static final float AGI_SPEED_PER_POINT     = BalanceConfig.AGI_SPEED_PER_POINT;

    /**
     * Hard floor for the action duration multiplier (55% of base duration).
     * Prevents animation from becoming too fast to read at high AGILITY values.
     */
    public static final float AGI_MIN_DURATION_MULT   = 0.55f;

    /** Each AGILITY point adds this fraction to the raw dodge chance before capping. */
    public static final float AGI_DODGE_PER_POINT     = BalanceConfig.AGI_DODGE_PER_POINT;

    /**
     * Maximum dodge probability regardless of AGILITY.
     * 35% cap keeps high-AGI builds slippery without becoming immune to hits.
     */
    public static final float DODGE_CAP               = BalanceConfig.DODGE_CAP;

    /** Each TOUGHNESS point adds this many HP to the player's maximum HP pool. */
    public static final int   TGH_HP_PER_POINT        = BalanceConfig.TGH_HP_PER_POINT;

    /** Each TOUGHNESS point shaves this many points off every HP-bound incoming hit. */
    public static final int   TGH_REDUCTION_PER_POINT = BalanceConfig.TGH_REDUCTION_PER_POINT;

    /**
     * Minimum HP damage after TOUGHNESS flat reduction.
     * Chip damage (poison ticks, explosions) always deals at least 1 HP.
     */
    public static final int   TGH_MIN_DAMAGE          = 1;

    // ---- Player GUARD stance (strategy-combat-order-4) — balance lives in BalanceConfig ----
    /** Front-arc incoming-damage multiplier while guarding (big reduction). */
    public static final float GUARD_FRONT_MULTIPLIER         = BalanceConfig.GUARD_FRONT_MULTIPLIER;
    /** Side-arc multiplier while guarding (full damage by design). */
    public static final float GUARD_SIDE_MULTIPLIER          = BalanceConfig.GUARD_SIDE_MULTIPLIER;
    /** Back-arc multiplier while guarding (full damage by design). */
    public static final float GUARD_BACK_MULTIPLIER          = BalanceConfig.GUARD_BACK_MULTIPLIER;
    /** Half-angle (degrees) of the protected front arc. */
    public static final float GUARD_FRONT_HALF_ANGLE_DEGREES = BalanceConfig.GUARD_FRONT_HALF_ANGLE_DEGREES;
    /** Half-angle (degrees) of the rear arc (from the reverse-facing vector). */
    public static final float GUARD_BACK_HALF_ANGLE_DEGREES  = BalanceConfig.GUARD_BACK_HALF_ANGLE_DEGREES;

    // =========================================================================
    // ABILITY CATALOGUE — BASE / PER_LEVEL / CAP
    // All values are PLACEHOLDERS — flag for playtesting.
    // RE-EXPORT SHIMS (Balance Authority, order 1): the magnitudes moved into
    // BalanceConfig SECTION 15 (the single source of truth). Tune them THERE.
    // AbilityResolver reads pre-scaled magnitudes from AbilityInstance at runtime;
    // these constants are used by WeaponRoller at weapon-spawn time (order-11).
    // =========================================================================

    // ── Critical Strike ────────────────────────────────────────────────────
    /** Level-1 crit chance for CRITICAL_STRIKE ability (5%). */
    public static final float CRIT_CHANCE_BASE            = BalanceConfig.CRIT_CHANCE_BASE;
    /** Crit chance added per weapon level above 1. */
    public static final float CRIT_CHANCE_PER_LEVEL       = BalanceConfig.CRIT_CHANCE_PER_LEVEL;
    /** Maximum crit chance regardless of weapon level (30%). */
    public static final float CRIT_CHANCE_CAP             = BalanceConfig.CRIT_CHANCE_CAP;

    // ── Armor Pierce ────────────────────────────────────────────────────────
    // ARMOR_PIERCE bypasses this fraction of the target's Block (its active damage shield) on every
    // hit — see Weapon.armBlockPierce() and Enemy.applyDamage(int,boolean,float).
    /** Level-1 Block-pierce fraction for ARMOR_PIERCE ability (20%). */
    public static final float ARMOR_PIERCE_BASE           = BalanceConfig.ARMOR_PIERCE_BASE;
    /** Pierce fraction added per weapon level above 1. */
    public static final float ARMOR_PIERCE_PER_LEVEL      = BalanceConfig.ARMOR_PIERCE_PER_LEVEL;
    /** Maximum pierce fraction regardless of weapon level (60%). */
    public static final float ARMOR_PIERCE_CAP            = BalanceConfig.ARMOR_PIERCE_CAP;

    // ── Executioner ─────────────────────────────────────────────────────────
    /** HP-fraction threshold below which EXECUTIONER fires (targets at 25% HP or lower). Fixed value. */
    public static final float EXECUTIONER_THRESHOLD       = BalanceConfig.EXECUTIONER_THRESHOLD;
    /** Level-1 bonus multiplier for EXECUTIONER (30% of base damage added as bonus). */
    public static final float EXECUTIONER_BONUS_BASE      = BalanceConfig.EXECUTIONER_BONUS_BASE;
    /** Bonus multiplier added per weapon level above 1. */
    public static final float EXECUTIONER_BONUS_PER_LEVEL = BalanceConfig.EXECUTIONER_BONUS_PER_LEVEL;
    /** Maximum EXECUTIONER bonus multiplier regardless of weapon level (80%). */
    public static final float EXECUTIONER_BONUS_CAP       = BalanceConfig.EXECUTIONER_BONUS_CAP;

    // ── Stagger Rounds ──────────────────────────────────────────────────────
    /** Level-1 stagger chance for STAGGER_ROUNDS ability (8%). */
    public static final float STAGGER_CHANCE_BASE         = BalanceConfig.STAGGER_CHANCE_BASE;
    /** Stagger chance added per weapon level above 1. */
    public static final float STAGGER_CHANCE_PER_LEVEL    = BalanceConfig.STAGGER_CHANCE_PER_LEVEL;
    /** Maximum stagger chance regardless of weapon level (35%). */
    public static final float STAGGER_CHANCE_CAP          = BalanceConfig.STAGGER_CHANCE_CAP;

    // ── Overpenetration ─────────────────────────────────────────────────────
    /** Base number of additional enemies the shot can pierce (1). */
    public static final int   OVERPENETRATION_BASE_COUNT                = BalanceConfig.OVERPENETRATION_BASE_COUNT;
    /** Weapon levels needed to gain each additional pierce count (+1 per 3 levels). */
    public static final int   OVERPENETRATION_LEVELS_PER_STEP          = BalanceConfig.OVERPENETRATION_LEVELS_PER_STEP;
    /** Maximum extra-pierce count regardless of weapon level (3 additional enemies). */
    public static final int   OVERPENETRATION_MAX_COUNT                 = BalanceConfig.OVERPENETRATION_MAX_COUNT;
    /**
     * For already-piercing weapons (PlasmaRifle, Railgun): fraction of getEffectiveDamage()
     * applied as a bonus for each enemy hit beyond the first (25% bonus per extra pierce).
     */
    public static final float OVERPENETRATION_ALREADY_PIERCING_BONUS   = BalanceConfig.OVERPENETRATION_ALREADY_PIERCING_BONUS;

    // =========================================================================
    // SUSTAIN ABILITIES — weapon-system-order-5
    // All values are PLACEHOLDERS — flag for playtesting.
    // =========================================================================

    // ── Lifesteal (ON_HIT) ──────────────────────────────────────────────────
    /** Level-1 lifesteal fraction: 6% of damage dealt returned as HP. */
    public static final float LIFESTEAL_BASE              = BalanceConfig.LIFESTEAL_BASE;
    /** Lifesteal fraction added per weapon level above 1. */
    public static final float LIFESTEAL_PER_LEVEL         = BalanceConfig.LIFESTEAL_PER_LEVEL;
    /** Maximum lifesteal fraction regardless of weapon level (20%). */
    public static final float LIFESTEAL_CAP               = BalanceConfig.LIFESTEAL_CAP;
    /** Minimum heal from lifesteal to show event text (avoids single-point spam). */
    public static final int   LIFESTEAL_TEXT_THRESHOLD    = 5;

    // ── Hemorrhage Harvest (ON_KILL) ─────────────────────────────────────────
    /** HP restored on kill at level 1. */
    public static final float HEMORRHAGE_HP_BASE          = BalanceConfig.HEMORRHAGE_HP_BASE;
    /** HP per level scaling for Hemorrhage Harvest. */
    public static final float HEMORRHAGE_HP_PER_LEVEL     = BalanceConfig.HEMORRHAGE_HP_PER_LEVEL;
    /** Maximum HP per kill for Hemorrhage Harvest. */
    public static final int   HEMORRHAGE_HP_CAP           = BalanceConfig.HEMORRHAGE_HP_CAP;

    // ── Vampiric Crit (ON_CRIT) ──────────────────────────────────────────────
    /** HP restored on crit at level 1. */
    public static final float VAMPIRIC_CRIT_HP_BASE       = BalanceConfig.VAMPIRIC_CRIT_HP_BASE;
    /** HP per level scaling for Vampiric Crit. */
    public static final float VAMPIRIC_CRIT_HP_PER_LEVEL  = BalanceConfig.VAMPIRIC_CRIT_HP_PER_LEVEL;
    /** Maximum HP per crit for Vampiric Crit. */
    public static final int   VAMPIRIC_CRIT_HP_CAP        = BalanceConfig.VAMPIRIC_CRIT_HP_CAP;

    // ── Adrenal Surge (ON_KILL) ───────────────────────────────────────────────
    /** Level-1 proc chance for Adrenal Surge (10%). */
    public static final float ADRENAL_SURGE_CHANCE_BASE      = BalanceConfig.ADRENAL_SURGE_CHANCE_BASE;
    /** Proc chance added per weapon level above 1. */
    public static final float ADRENAL_SURGE_CHANCE_PER_LEVEL = BalanceConfig.ADRENAL_SURGE_CHANCE_PER_LEVEL;
    /** Maximum proc chance for Adrenal Surge regardless of weapon level (40%). */
    public static final float ADRENAL_SURGE_CHANCE_CAP       = BalanceConfig.ADRENAL_SURGE_CHANCE_CAP;
    /** Outgoing damage bonus multiplier applied to the next attack after Surge procs (+30%). */
    public static final float ADRENAL_SURGE_DAMAGE_BONUS     = BalanceConfig.ADRENAL_SURGE_DAMAGE_BONUS;

    // ── Bulwark Rounds (ON_RELOAD) ────────────────────────────────────────────
    /** Number of fixed temp-armor slots in PlayerStats (determines max concurrent Bulwark stacks). */
    public static final int   BULWARK_TEMP_ARMOR_SLOTS    = 4;
    /** Temporary armor points granted on reload completion at level 1. */
    public static final float BULWARK_ARMOR_BASE          = BalanceConfig.BULWARK_ARMOR_BASE;
    /** Temp armor added per weapon level above 1. */
    public static final float BULWARK_ARMOR_PER_LEVEL     = BalanceConfig.BULWARK_ARMOR_PER_LEVEL;
    /** Maximum temp armor per reload for Bulwark Rounds. */
    public static final int   BULWARK_ARMOR_CAP           = BalanceConfig.BULWARK_ARMOR_CAP;
    /** Number of player-action turns the Bulwark temp armor persists. */
    public static final int   BULWARK_ARMOR_DURATION      = BalanceConfig.BULWARK_ARMOR_DURATION;

    // ── Second Wind (PASSIVE) ─────────────────────────────────────────────────
    /** HP fraction at or below which Second Wind activates (30% HP = critically low). */
    public static final float SECOND_WIND_HP_THRESHOLD    = BalanceConfig.SECOND_WIND_HP_THRESHOLD;
    /** Level-1 outgoing damage bonus while Second Wind is active (+25%). */
    public static final float SECOND_WIND_BONUS_BASE      = BalanceConfig.SECOND_WIND_BONUS_BASE;
    /** Damage bonus multiplier added per weapon level above 1. */
    public static final float SECOND_WIND_BONUS_PER_LEVEL = BalanceConfig.SECOND_WIND_BONUS_PER_LEVEL;
    /** Maximum Second Wind damage bonus regardless of weapon level (75%). */
    public static final float SECOND_WIND_BONUS_CAP       = BalanceConfig.SECOND_WIND_BONUS_CAP;

    // =========================================================================
    // MELEE-SPECIFIC ABILITIES — weapon-system-order-6
    // All values are PLACEHOLDERS — flag for playtesting.
    // =========================================================================

    // ── Kinetic Slam (ON_HIT, melee only) ────────────────────────────────────
    public static final float KINETIC_SLAM_CHANCE_BASE       = BalanceConfig.KINETIC_SLAM_CHANCE_BASE;
    public static final float KINETIC_SLAM_CHANCE_PER_LEVEL  = BalanceConfig.KINETIC_SLAM_CHANCE_PER_LEVEL;
    public static final float KINETIC_SLAM_CHANCE_CAP        = BalanceConfig.KINETIC_SLAM_CHANCE_CAP;
    public static final int   KINETIC_SLAM_WALL_BONUS_DAMAGE = BalanceConfig.KINETIC_SLAM_WALL_BONUS_DAMAGE;

    // ── Cleave (ON_HIT, melee only) ───────────────────────────────────────────
    public static final float CLEAVE_FRACTION_BASE      = BalanceConfig.CLEAVE_FRACTION_BASE;
    public static final float CLEAVE_FRACTION_PER_LEVEL = BalanceConfig.CLEAVE_FRACTION_PER_LEVEL;
    public static final float CLEAVE_FRACTION_CAP       = BalanceConfig.CLEAVE_FRACTION_CAP;

    // ── Salvage Strike (ON_KILL, melee only) ─────────────────────────────────
    public static final float SALVAGE_CHANCE_BASE      = BalanceConfig.SALVAGE_CHANCE_BASE;
    public static final float SALVAGE_CHANCE_PER_LEVEL = BalanceConfig.SALVAGE_CHANCE_PER_LEVEL;
    public static final float SALVAGE_CHANCE_CAP       = BalanceConfig.SALVAGE_CHANCE_CAP;
    public static final char  SALVAGE_AMMO_DROP_CHAR   = '6';

    // ── Scholar's Edge (ON_KILL, melee only) ─────────────────────────────────
    public static final float SCHOLARS_XP_BONUS_BASE      = BalanceConfig.SCHOLARS_XP_BONUS_BASE;
    public static final float SCHOLARS_XP_BONUS_PER_LEVEL = BalanceConfig.SCHOLARS_XP_BONUS_PER_LEVEL;
    public static final float SCHOLARS_XP_BONUS_CAP       = BalanceConfig.SCHOLARS_XP_BONUS_CAP;

    // ── Berserker's Oath (PASSIVE/ON_KILL, legendary, melee only) ────────────
    public static final float BERSERKER_DAMAGE_PER_STACK  = BalanceConfig.BERSERKER_DAMAGE_PER_STACK;
    public static final int   BERSERKER_HP_TICK_PER_STACK = BalanceConfig.BERSERKER_HP_TICK_PER_STACK;
    public static final int   BERSERKER_MAX_STACKS        = BalanceConfig.BERSERKER_MAX_STACKS;

    // =========================================================================
    // UTILITY & ECONOMY ABILITIES — weapon-system-order-7
    // All values are PLACEHOLDERS — flag for playtesting.
    // =========================================================================

    // ── Scavenger Rounds (ON_KILL, gun only) ────────────────────────────────
    /** Level-1 proc chance for Scavenger Rounds (15%). */
    public static final float SCAVENGER_CHANCE_BASE          = BalanceConfig.SCAVENGER_CHANCE_BASE;
    /** Proc chance added per weapon level above 1. */
    public static final float SCAVENGER_CHANCE_PER_LEVEL     = BalanceConfig.SCAVENGER_CHANCE_PER_LEVEL;
    /** Maximum proc chance for Scavenger Rounds regardless of weapon level (50%). */
    public static final float SCAVENGER_CHANCE_CAP           = BalanceConfig.SCAVENGER_CHANCE_CAP;
    /** Ammo units refunded to reserve at weapon level below SCAVENGER_HIGH_LEVEL_THRESHOLD. */
    public static final int   SCAVENGER_REFUND_BASE          = BalanceConfig.SCAVENGER_REFUND_BASE;
    /** Ammo units refunded to reserve at weapon level >= SCAVENGER_HIGH_LEVEL_THRESHOLD. */
    public static final int   SCAVENGER_REFUND_HIGH_LEVEL    = BalanceConfig.SCAVENGER_REFUND_HIGH_LEVEL;
    /** Weapon level threshold at which the refund amount increases from BASE to HIGH_LEVEL. */
    public static final int   SCAVENGER_HIGH_LEVEL_THRESHOLD = BalanceConfig.SCAVENGER_HIGH_LEVEL_THRESHOLD;

    // ── Field Medic Rounds (ON_KILL, universal) ──────────────────────────────
    /** Level-1 proc chance for Field Medic Rounds (5%). */
    public static final float FIELD_MEDIC_CHANCE_BASE        = BalanceConfig.FIELD_MEDIC_CHANCE_BASE;
    /** Proc chance added per weapon level above 1. */
    public static final float FIELD_MEDIC_CHANCE_PER_LEVEL   = BalanceConfig.FIELD_MEDIC_CHANCE_PER_LEVEL;
    /** Maximum proc chance for Field Medic Rounds regardless of weapon level (25%). */
    public static final float FIELD_MEDIC_CHANCE_CAP         = BalanceConfig.FIELD_MEDIC_CHANCE_CAP;
    /** Tile character placed at the killed enemy's tile when Field Medic Rounds procs. '+' = MEDKIT_SMALL. */
    public static final char  FIELD_MEDIC_DROP_CHAR          = '+';

    // =========================================================================
    // CREDIT REWARDS — credits dropped by each enemy archetype on death
    // Kill formula: round(base * (1 + (depth - 1) * CREDIT_DEPTH_SCALE))
    //   depth 1: ×1.00   depth 2: ×1.12   depth 3: ×1.24   depth 5: ×1.48
    // =========================================================================

    // Per-enemy credit rewards, depth scale, and chips-per-floor are balance values —
    // see BalanceConfig. Boss credit is DERIVED (order 6): the flat CREDIT_REWARD_BOSS_BASE placeholder is
    // DELETED — a boss's credit reward flows through EnemyManager.depthScaledCreditReward ->
    // BossBalance.statsForDepth(depth).creditReward (RULE 6: consumption * risk premium).
    public static final int   CREDIT_REWARD_GORE_BITER    = BalanceConfig.CREDIT_REWARD_GORE_BITER;
    public static final int   CREDIT_REWARD_EYE_TYRANT    = BalanceConfig.CREDIT_REWARD_EYE_TYRANT;
    public static final int   CREDIT_REWARD_PLAGUE_HULK   = BalanceConfig.CREDIT_REWARD_PLAGUE_HULK;
    public static final int   CREDIT_REWARD_ACID_DRONE    = BalanceConfig.CREDIT_REWARD_ACID_DRONE;
    public static final int   CREDIT_REWARD_SHELL_BRUTE   = BalanceConfig.CREDIT_REWARD_SHELL_BRUTE;
    public static final int   CREDIT_REWARD_VOID_SHROUD   = BalanceConfig.CREDIT_REWARD_VOID_SHROUD;
    public static final int   CREDIT_REWARD_MIRE_WRAITH   = BalanceConfig.CREDIT_REWARD_MIRE_WRAITH;
    public static final int   CREDIT_REWARD_IRON_STALKER  = BalanceConfig.CREDIT_REWARD_IRON_STALKER;
    public static final int   CREDIT_REWARD_GHOUL            = BalanceConfig.CREDIT_REWARD_GHOUL;
    public static final int   CREDIT_REWARD_CRAWLER          = BalanceConfig.CREDIT_REWARD_CRAWLER;
    public static final int   CREDIT_REWARD_REVENANT         = BalanceConfig.CREDIT_REWARD_REVENANT;
    public static final int   CREDIT_REWARD_VORTEX_EYE       = BalanceConfig.CREDIT_REWARD_VORTEX_EYE;
    public static final int   CREDIT_REWARD_BLIGHT_CORRUPTOR = BalanceConfig.CREDIT_REWARD_BLIGHT_CORRUPTOR;
    public static final int   CREDIT_REWARD_AURIC_SENTINEL    = BalanceConfig.CREDIT_REWARD_AURIC_SENTINEL;
    public static final int   CREDIT_REWARD_CINDERFORGE_COLOSSUS = BalanceConfig.CREDIT_REWARD_CINDERFORGE_COLOSSUS;
    public static final int   CREDIT_REWARD_RIMESHELL_LANCER  = BalanceConfig.CREDIT_REWARD_RIMESHELL_LANCER;
    public static final int   CREDIT_REWARD_VERDANT_SPIRESOWER = BalanceConfig.CREDIT_REWARD_VERDANT_SPIRESOWER;
    public static final float CREDIT_DEPTH_SCALE          = BalanceConfig.CREDIT_DEPTH_SCALE;
    public static final int   CREDIT_CHIPS_PER_FLOOR_MIN  = BalanceConfig.CREDIT_CHIPS_PER_FLOOR_MIN;
    public static final int   CREDIT_CHIPS_PER_FLOOR_MAX  = BalanceConfig.CREDIT_CHIPS_PER_FLOOR_MAX;

    // =========================================================================
    // SHOP — UAC Fabricator vending machine placement (shop_order_1)
    // Every non-boss floor gets 1 or 2 machines (guaranteed presence, not a chance),
    // so credits always have a sink. Stock/pricing/effects are shop parts 2-4.
    // =========================================================================
    public static final int   SHOP_MIN_PER_FLOOR            = BalanceConfig.SHOP_MIN_PER_FLOOR;
    public static final int   SHOP_MAX_PER_FLOOR            = BalanceConfig.SHOP_MAX_PER_FLOOR;
    /** Probability a floor rolls the SECOND machine (a two-shop floor is a small treat). */
    public static final float SHOP_SECOND_MACHINE_CHANCE    = BalanceConfig.SHOP_SECOND_MACHINE_CHANCE;
    /** When two machines are placed, keep them at least this many tiles apart (Manhattan). */
    public static final int   SHOP_TWO_MACHINE_MIN_SPACING  = 8;

    // ── Shop stock roll (shop_order_2) — how many entries and which categories ────────────────
    /**
     * Each machine stocks a fixed 9 offers, rolled once at floor generation. MIN == MAX so every
     * shop is a full 9-item shelf (the roller fills to this many distinct offers whenever the pool
     * of distinct offers allows — abilities alone provide enough to reach 9 on any floor).
     */
    public static final int   SHOP_ENTRY_MIN                 = BalanceConfig.SHOP_ENTRY_MIN;
    public static final int   SHOP_ENTRY_MAX                 = BalanceConfig.SHOP_ENTRY_MAX;
    // Weighted category pool for the "remainder" slots (after the guaranteed supply + upgrade slot).
    public static final int   SHOP_CAT_WEIGHT_WEAPON_LEVELUP = BalanceConfig.SHOP_CAT_WEIGHT_WEAPON_LEVELUP;
    public static final int   SHOP_CAT_WEIGHT_AMMO           = BalanceConfig.SHOP_CAT_WEIGHT_AMMO;
    public static final int   SHOP_CAT_WEIGHT_MEDKIT         = BalanceConfig.SHOP_CAT_WEIGHT_MEDKIT;
    public static final int   SHOP_CAT_WEIGHT_ABILITY        = BalanceConfig.SHOP_CAT_WEIGHT_ABILITY;
    public static final int   SHOP_CAT_WEIGHT_TIER_UPGRADE   = BalanceConfig.SHOP_CAT_WEIGHT_TIER_UPGRADE;
    /** Two-shop floors: lean one machine toward upgrades and the other toward supplies. */
    public static final boolean SHOP_TWO_MACHINE_BIAS        = true;
    /** Weight multiplier applied to the favoured category group when a machine is biased. */
    public static final float SHOP_BIAS_WEIGHT_MULTIPLIER    = BalanceConfig.SHOP_BIAS_WEIGHT_MULTIPLIER;

    // ── Shop pricing (new-game-balancr order 3) — every price is formula-derived from an offer's
    // VALUE IN POWER POINTS via GameMath.shopPrice; zero hand-set per-offer base prices. ──────────
    /** Depth price scaling: +10% per floor beyond the first (mirrors credit depth scaling). */
    public static final float SHOP_DEPTH_PRICE_SCALE           = BalanceConfig.SHOP_DEPTH_PRICE_SCALE;
    /** Credits per power point of an offer's priced value — the single price-level knob. */
    public static final float SHOP_CREDITS_PER_POWER_POINT     = BalanceConfig.SHOP_CREDITS_PER_POWER_POINT;
    /** Damage supplied per power point when pricing an ammo box. */
    public static final float SHOP_AMMO_DAMAGE_PER_POWER_POINT = BalanceConfig.SHOP_AMMO_DAMAGE_PER_POWER_POINT;
    /** HP restored per power point when pricing a medkit. */
    public static final float SHOP_HEAL_HP_PER_POWER_POINT     = BalanceConfig.SHOP_HEAL_HP_PER_POWER_POINT;
    /** PP value of a single weapon level-up offer. */
    public static final float SHOP_WEAPON_LEVEL_UP_POWER_POINTS = BalanceConfig.SHOP_WEAPON_LEVEL_UP_POWER_POINTS;
    /** Ammo "large box" multiplier over the standard box size (price derives from the larger supply). */
    public static final int   SHOP_AMMO_LARGE_BOX_MULTIPLIER  = BalanceConfig.SHOP_AMMO_LARGE_BOX_MULTIPLIER;

    // ── Credit Fang (ON_KILL, universal) ─────────────────────────────────────
    /** Credits awarded at level 1 for each Credit Fang kill. */
    public static final float CREDIT_FANG_BASE               = BalanceConfig.CREDIT_FANG_BASE;
    /** Additional credits per weapon level above 1. */
    public static final float CREDIT_FANG_PER_LEVEL          = BalanceConfig.CREDIT_FANG_PER_LEVEL;
    /** Maximum credits awarded per kill regardless of weapon level. */
    public static final int   CREDIT_FANG_CAP                = BalanceConfig.CREDIT_FANG_CAP;

    // =========================================================================
    // DOT & STATUS ABILITIES — weapon-system-order-8
    // All values are PLACEHOLDERS — flag for playtesting.
    // =========================================================================

    // ── Rend (BLEED DoT — ON_HIT) ────────────────────────────────────────────
    // DoT magnitudes are balance values (DoT damage counts toward TTK) — see BalanceConfig.
    /** BLEED damage per turn at weapon level 1. */
    public static final float REND_DAMAGE_PER_TURN_BASE      = BalanceConfig.REND_DAMAGE_PER_TURN_BASE;
    /** Additional BLEED damage per turn for each weapon level above 1. */
    public static final float REND_DAMAGE_PER_TURN_PER_LEVEL = BalanceConfig.REND_DAMAGE_PER_TURN_PER_LEVEL;
    /** Maximum BLEED damage per turn regardless of weapon level. */
    public static final float REND_DAMAGE_PER_TURN_CAP       = BalanceConfig.REND_DAMAGE_PER_TURN_CAP;
    /** Number of world turns the BLEED effect persists. */
    public static final int   REND_DURATION_TURNS            = BalanceConfig.REND_DURATION_TURNS;

    // ── Incendiary (BURN DoT — ON_HIT) ───────────────────────────────────────
    /** BURNING damage per turn at weapon level 1 for INCENDIARY. */
    public static final float INCENDIARY_BURN_PER_TURN_BASE      = BalanceConfig.INCENDIARY_BURN_PER_TURN_BASE;
    /** Additional burn damage per turn for each weapon level above 1. */
    public static final float INCENDIARY_BURN_PER_TURN_PER_LEVEL = BalanceConfig.INCENDIARY_BURN_PER_TURN_PER_LEVEL;
    /** Maximum burn damage per turn regardless of weapon level. */
    public static final float INCENDIARY_BURN_PER_TURN_CAP       = BalanceConfig.INCENDIARY_BURN_PER_TURN_CAP;
    /** Base number of world turns the burn effect persists for INCENDIARY. */
    public static final int   INCENDIARY_BURN_DURATION           = BalanceConfig.INCENDIARY_BURN_DURATION;
    /** Extra turns added to burn duration when the Incinerator weapon fires INCENDIARY. */
    public static final int   INCENDIARY_INCINERATOR_EXTRA_TURNS = BalanceConfig.INCENDIARY_INCINERATOR_EXTRA_TURNS;

    // ── Stagger Rounds (STUN — ON_HIT) ───────────────────────────────────────
    /** Number of world turns the STUNNED effect persists when STAGGER_ROUNDS procs. (Balance: BalanceConfig.) */
    public static final int   STAGGER_STUN_DURATION = BalanceConfig.STAGGER_STUN_DURATION;

    // =========================================================================
    // POSITIONAL & SITUATIONAL ABILITIES — weapon-system-order-9
    // All values are PLACEHOLDERS — flag for playtesting.
    // =========================================================================

    // ── Point Blank (ON_HIT, GUN only) ───────────────────────────────────────
    /** Level-1 bonus damage fraction when target is at POINT_BLANK_MAX_DISTANCE or closer. */
    public static final float POINT_BLANK_BONUS_BASE      = BalanceConfig.POINT_BLANK_BONUS_BASE;
    /** Bonus fraction added per weapon level above 1. */
    public static final float POINT_BLANK_BONUS_PER_LEVEL = BalanceConfig.POINT_BLANK_BONUS_PER_LEVEL;
    /** Maximum Point Blank bonus fraction regardless of weapon level. */
    public static final float POINT_BLANK_BONUS_CAP       = BalanceConfig.POINT_BLANK_BONUS_CAP;
    /** Maximum tile distance at which Point Blank fires (adjacent tile only). */
    public static final int   POINT_BLANK_MAX_DISTANCE    = BalanceConfig.POINT_BLANK_MAX_DISTANCE;

    // ── Marksman's Patience (ON_HIT, GUN only) ────────────────────────────────
    /** Level-1 bonus fraction per tile beyond MARKSMAN_MIN_DISTANCE. */
    public static final float MARKSMAN_PER_TILE_BASE      = BalanceConfig.MARKSMAN_PER_TILE_BASE;
    /** Per-tile bonus fraction added per weapon level above 1. */
    public static final float MARKSMAN_PER_TILE_PER_LEVEL = BalanceConfig.MARKSMAN_PER_TILE_PER_LEVEL;
    /** Maximum per-tile bonus fraction regardless of weapon level. */
    public static final float MARKSMAN_PER_TILE_CAP       = BalanceConfig.MARKSMAN_PER_TILE_CAP;
    /** Minimum tile distance before the per-tile bonus accumulates (distance > this). */
    public static final int   MARKSMAN_MIN_DISTANCE       = BalanceConfig.MARKSMAN_MIN_DISTANCE;
    /** Hard cap on total Marksman's Patience bonus regardless of distance. */
    public static final float MARKSMAN_TOTAL_BONUS_CAP    = BalanceConfig.MARKSMAN_TOTAL_BONUS_CAP;

    // ── Opening Salvo (ON_HIT, UNIVERSAL) ─────────────────────────────────────
    /** Level-1 bonus damage fraction when target is at full HP. */
    public static final float OPENING_SALVO_BONUS_BASE      = BalanceConfig.OPENING_SALVO_BONUS_BASE;
    /** Bonus fraction added per weapon level above 1. */
    public static final float OPENING_SALVO_BONUS_PER_LEVEL = BalanceConfig.OPENING_SALVO_BONUS_PER_LEVEL;
    /** Maximum Opening Salvo bonus fraction regardless of weapon level. */
    public static final float OPENING_SALVO_BONUS_CAP       = BalanceConfig.OPENING_SALVO_BONUS_CAP;

    // ── Rhythm / Heat-Up (ON_HIT, GUN only) ───────────────────────────────────
    /** Level-1 ramp bonus per consecutive hit on the same target (added per extra stack). */
    public static final float RHYTHM_RAMP_PER_HIT_BASE      = BalanceConfig.RHYTHM_RAMP_PER_HIT_BASE;
    /** Ramp bonus added per weapon level above 1. */
    public static final float RHYTHM_RAMP_PER_HIT_PER_LEVEL = BalanceConfig.RHYTHM_RAMP_PER_HIT_PER_LEVEL;
    /** Maximum ramp-per-hit value regardless of weapon level. */
    public static final float RHYTHM_RAMP_PER_HIT_CAP       = BalanceConfig.RHYTHM_RAMP_PER_HIT_CAP;
    /** Maximum consecutive-hit stacks before the bonus plateaus. */
    public static final int   RHYTHM_MAX_STACKS              = BalanceConfig.RHYTHM_MAX_STACKS;

    // ── Static Discharge (ON_KILL, UNIVERSAL) ────────────────────────────────
    /** Level-1 splash damage dealt to enemies adjacent to the killed target. */
    public static final float STATIC_SPLASH_BASE      = BalanceConfig.STATIC_SPLASH_BASE;
    /** Splash damage added per weapon level above 1. */
    public static final float STATIC_SPLASH_PER_LEVEL = BalanceConfig.STATIC_SPLASH_PER_LEVEL;
    /** Maximum splash damage regardless of weapon level. */
    public static final int   STATIC_SPLASH_CAP       = BalanceConfig.STATIC_SPLASH_CAP;

    // ── Resonant Rounds (ON_HIT, UNIVERSAL) ──────────────────────────────────
    /** Level-1 bonus as a fraction of the target's MAX HP applied as flat bonus damage. */
    public static final float RESONANT_PCT_BASE      = BalanceConfig.RESONANT_PCT_BASE;
    /** PCT fraction added per weapon level above 1. */
    public static final float RESONANT_PCT_PER_LEVEL = BalanceConfig.RESONANT_PCT_PER_LEVEL;
    /** Maximum Resonant Rounds PCT fraction regardless of weapon level. */
    public static final float RESONANT_PCT_CAP       = BalanceConfig.RESONANT_PCT_CAP;

    // =========================================================================
    // LEGENDARY SIGNATURE ABILITIES — weapon-system-order-10
    // All values are PLACEHOLDERS — flag for playtesting.
    // =========================================================================

    // ── Soulforge (ON_KILL, legendary, universal) ─────────────────────────────
    /** Kills with this weapon needed to permanently raise its weapon level by 1. */
    public static final int   SOULFORGE_KILLS_PER_LEVEL_UP  = BalanceConfig.SOULFORGE_KILLS_PER_LEVEL_UP;

    // ── Judgment (ON_FIRE, legendary, gun only) ───────────────────────────────
    /** Number of fires between consecutive Judgment lance shots. */
    public static final int   JUDGMENT_COOLDOWN_FIRES        = BalanceConfig.JUDGMENT_COOLDOWN_FIRES;
    /** Maximum tiles the Judgment lance travels before stopping. */
    public static final int   JUDGMENT_LANCE_RANGE           = BalanceConfig.JUDGMENT_LANCE_RANGE;
    /** Effective-damage multiplier applied to the Judgment lance hit. */
    public static final float JUDGMENT_DAMAGE_MULTIPLIER     = BalanceConfig.JUDGMENT_DAMAGE_MULTIPLIER;

    // ── Hellfire Nova (ON_CRIT, legendary, universal) ─────────────────────────
    /** Chebyshev tile radius of the AoE explosion triggered by a crit. */
    public static final int   HELLFIRE_NOVA_RADIUS           = BalanceConfig.HELLFIRE_NOVA_RADIUS;
    /** Fraction of critDamage dealt as AoE damage to enemies within the nova radius. */
    public static final float HELLFIRE_NOVA_DAMAGE_FRACTION  = BalanceConfig.HELLFIRE_NOVA_DAMAGE_FRACTION;

    // =========================================================================
    // WEAPON ROLLER — weapon-system-order-11
    // =========================================================================

    /** Tier assigned to all run-start weapons (the default loadout at game start). */
    public static final WeaponTier RUN_START_WEAPON_TIER  = WeaponTier.COMMON;

    /** Level assigned to all run-start weapons. */
    public static final int        RUN_START_WEAPON_LEVEL = 1;

    /** Minimum tier for weapons offered in the start-room selection. */
    public static final WeaponTier START_ROOM_OFFER_MIN_TIER = WeaponTier.COMMON;

    /** Maximum tier for weapons offered in the start-room selection. */
    public static final WeaponTier START_ROOM_OFFER_MAX_TIER = WeaponTier.UNCOMMON;

    /**
     * TEST FLAG — MUST STAY {@code false} in every real build (Balance Authority R-FLAGS rule;
     * {@code BalanceAuditTest} fails the build otherwise). When true, start-room weapon offers can
     * roll any tier (COMMON through LEGENDARY) — the direct enabler of "beat the boss with the
     * starting-room weapon". When false, offers roll the DESIGNED band
     * {@link #START_ROOM_OFFER_MIN_TIER}..{@link #START_ROOM_OFFER_MAX_TIER} (COMMON..UNCOMMON).
     */
    public static final boolean START_ROOM_ANY_TIER_ENABLED = false;

    /** Level-1 clip expansion from EXTENDED_MAG ability (+1 clip slot). */
    public static final int EXTENDED_MAG_BASE_COUNT      = BalanceConfig.EXTENDED_MAG_BASE_COUNT;

    /** Weapon levels needed to gain each additional clip slot (+1 per 3 levels). */
    public static final int EXTENDED_MAG_LEVELS_PER_STEP = BalanceConfig.EXTENDED_MAG_LEVELS_PER_STEP;

    /** Maximum clip expansion from EXTENDED_MAG regardless of weapon level. */
    public static final int EXTENDED_MAG_MAX_COUNT       = BalanceConfig.EXTENDED_MAG_MAX_COUNT;

    // =========================================================================
    // OVERSEER HUNTER-KILLER BRAIN (boss-fight-mobile-overseer ORDER 4)
    // -------------------------------------------------------------------------
    // Tunables for HunterKillerPattern — the tactic-selecting boss brain. A designer
    // can retune the Overseer's aggression here WITHOUT touching pattern code:
    //   * SCORE_* weights bias which tactic wins in a given situation.
    //   * *_GAP values space out how often each heavy verb (charge/summon/hazard)
    //     can headline a tactic — added on top of the tactic's own length so the
    //     spacing is measured from when the last tactic that used the verb finished.
    //   * Phase 2 gaps are shorter (enraged: faster, layered threats) but the
    //     controller's Constants.BOSS_MAX_CONSECUTIVE_MOVE_TURNS backstop still
    //     guarantees the fairness F1 punish-window cadence regardless of tuning.
    // =========================================================================

    /** Baseline score every eligible tactic starts from before situational bonuses. */
    public static final int   BOSS_TACTIC_SCORE_BASE            = BalanceConfig.BOSS_TACTIC_SCORE_BASE;
    /** Bonus for melee-opener tactics (AMBUSH/BLITZ) when the player is close. */
    public static final int   BOSS_TACTIC_SCORE_CLOSE_RANGE     = BalanceConfig.BOSS_TACTIC_SCORE_CLOSE_RANGE;
    /** Bonus for gap-closing / zoning tactics (CHARGER/ZONER/FIRESTORM) when the player is far. */
    public static final int   BOSS_TACTIC_SCORE_FAR_RANGE       = BalanceConfig.BOSS_TACTIC_SCORE_FAR_RANGE;
    /** Bonus for summon tactics (ZONER/FIRESTORM) while there is room under the adds cap. */
    public static final int   BOSS_TACTIC_SCORE_ADDS_ROOM       = BalanceConfig.BOSS_TACTIC_SCORE_ADDS_ROOM;
    /** Bonus for line-hit tactics (CHARGER/BLITZ) when the player is boxed against cover/walls. */
    public static final int   BOSS_TACTIC_SCORE_CORNERED        = BalanceConfig.BOSS_TACTIC_SCORE_CORNERED;
    /** Bonus for hazard/dash tactics that punish a player who has been camping one tile. */
    public static final int   BOSS_TACTIC_SCORE_CAMPING         = BalanceConfig.BOSS_TACTIC_SCORE_CAMPING;

    /** Chebyshev range (tiles) at or below which the player counts as "close" for scoring. */
    public static final int   BOSS_TACTIC_CLOSE_RANGE_TILES     = BalanceConfig.BOSS_TACTIC_CLOSE_RANGE_TILES;
    /** Player counts as "camping" once its tile is unchanged for this many boss turns. */
    public static final int   BOSS_TACTIC_CAMP_TURNS            = BalanceConfig.BOSS_TACTIC_CAMP_TURNS;
    /** Passable cardinal neighbours at or below which the player counts as "cornered". */
    public static final int   BOSS_TACTIC_CORNERED_EXITS        = BalanceConfig.BOSS_TACTIC_CORNERED_EXITS;
    /** Upper bound (exclusive) of the seeded random tiebreak added to every tactic score. */
    public static final int   BOSS_TACTIC_RANDOM_TIEBREAK       = BalanceConfig.BOSS_TACTIC_RANDOM_TIEBREAK;

    /** Phase-1 spacing (turns beyond the tactic's own length) before CHARGE may headline again. */
    public static final int   BOSS_TACTIC_CHARGE_GAP_PHASE1     = BalanceConfig.BOSS_TACTIC_CHARGE_GAP_PHASE1;
    /** Phase-1 spacing before SUMMON may headline again. */
    public static final int   BOSS_TACTIC_SUMMON_GAP_PHASE1     = BalanceConfig.BOSS_TACTIC_SUMMON_GAP_PHASE1;
    /** Phase-1 spacing before a hazard (fire/toxic) tactic may headline again. */
    public static final int   BOSS_TACTIC_HAZARD_GAP_PHASE1     = BalanceConfig.BOSS_TACTIC_HAZARD_GAP_PHASE1;
    /** Phase-2 (enraged) charge spacing — tighter than phase 1. */
    public static final int   BOSS_TACTIC_CHARGE_GAP_PHASE2     = BalanceConfig.BOSS_TACTIC_CHARGE_GAP_PHASE2;
    /** Phase-2 (enraged) summon spacing. */
    public static final int   BOSS_TACTIC_SUMMON_GAP_PHASE2     = BalanceConfig.BOSS_TACTIC_SUMMON_GAP_PHASE2;
    /** Phase-2 (enraged) hazard spacing. */
    public static final int   BOSS_TACTIC_HAZARD_GAP_PHASE2     = BalanceConfig.BOSS_TACTIC_HAZARD_GAP_PHASE2;

    /**
     * "Last stand": once phase 2 HP falls to or below this fraction the boss drops its flee
     * behaviour and commits to BLITZ every selection — a readable, climactic finish where the
     * player finally gets to corner it (reward for surviving).
     */
    public static final float BOSS_TACTIC_LAST_STAND_HP_FRACTION = BalanceConfig.BOSS_TACTIC_LAST_STAND_HP_FRACTION;

    /** When true, HunterKillerPattern logs each tactic selection (dev fairness instrumentation). */
    public static final boolean BOSS_TACTIC_DEBUG_LOG            = false;

    // =========================================================================
    // TILESET PALETTE VARIETY — SymbolAllocator weighting knobs (tileset order-4)
    // =========================================================================
    // These tune the RULE-GOVERNED variety of the per-level symbol/sprite allocator. They are pure
    // selection WEIGHTS fed to GameMath.weightedChoiceIndex — never a switch on a specific char/sprite,
    // so a designer can dial coherence and depth flavour without touching allocator logic. Coherence
    // (R1) and depth influence (R5) only bite once catalog sprites carry the matching theme tags; with
    // an untagged pool they collapse to a plain seeded pick, which is still fully deterministic.

    /** Baseline selection weight every eligible variety sprite starts with (R1/R3/R5 add on top). */
    public static final float PALETTE_VARIETY_BASE_WEIGHT          = 1f;

    /**
     * COHERENCE (R1) multiplier: a variety sprite whose theme tags overlap the level's chosen theme is
     * this many times likelier to be picked, so a level trends toward one look instead of a random
     * quilt. 1.0 disables coherence; larger values pull harder toward the theme.
     */
    public static final float PALETTE_COHERENCE_WEIGHT_MULTIPLIER  = 4f;

    /**
     * DEPTH INFLUENCE (R5, kept mild): extra additive weight per dungeon depth for a sprite carrying a
     * {@link #PALETTE_HARSH_THEME_TAGS harsh} tag, so deeper floors trend grittier. Mild by design —
     * depth 10 adds only 10× this. Set to 0 to disable depth flavour entirely.
     */
    public static final float PALETTE_DEPTH_HARSH_WEIGHT_PER_DEPTH = 0.12f;

    /**
     * The theme tags treated as "harsh" for the depth-influence rule (R5). Data, not code: a sprite is
     * weighted grittier at depth purely by carrying one of these tags — the allocator never names a
     * sprite. Tune the set here to change which flavours deep floors favour.
     */
    public static final String[] PALETTE_HARSH_THEME_TAGS = { "gore", "rust", "radiation" };
}
