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

    /** Base XP reward for killing any boss (before depth scaling applied by BossFloorController). */
    public static final int XP_REWARD_BOSS_BASE     = 500;

    // =========================================================================
    // XP CURVE — how much XP is needed to reach each next player level
    //
    // Formula:  xpRequired(level) = XP_BASE * level ^ XP_CURVE_EXPONENT
    //   level 1 → 2:    80 * 1^1.5  =   80 XP   (~2-3 stalkers)
    //   level 2 → 3:    80 * 2^1.5  =  226 XP   (~6-7 stalkers)
    //   level 3 → 4:    80 * 3^1.5  =  416 XP
    //   level 4 → 5:    80 * 4^1.5  =  640 XP
    //   level 5 → 6:    80 * 5^1.5  =  894 XP
    // =========================================================================

    /** Base XP needed to advance from level 1 to level 2. */
    public static final int   XP_BASE_REQUIREMENT = 80;

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
    //   depth 1: ×1.00   depth 2: ×1.12   depth 3: ×1.25
    //   depth 4: ×1.40   depth 5: ×1.57   depth 10: ×2.77
    //
    // Damage formula:  baseDmg * DAMAGE_SCALE ^ (depth − 1)
    //   depth 1: ×1.00   depth 2: ×1.10   depth 3: ×1.21
    //   depth 4: ×1.33   depth 5: ×1.46   depth 10: ×2.36
    // =========================================================================

    /** Per-floor HP multiplier applied as a compound factor. */
    public static final float ENEMY_HEALTH_SCALE_PER_DEPTH = 1.12f;

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
    // MELEE WEAPON STAT BLOCKS — base damage per weapon type
    // All four v1 melee weapons have turnsPerAttack=1 (one swing = one world turn).
    // =========================================================================

    /** FIST — always-equipped fallback; chip damage only, never dropped. */
    public static final int MELEE_FIST_DAMAGE          = 4;

    /** COMBAT KNIFE — fast light melee, good vs low-HP chaff. */
    public static final int MELEE_KNIFE_DAMAGE         = 8;

    /** HAMMER — heavy crowd-control melee; knockback eligible targets by HAMMER_KNOCKBACK_TILES. */
    public static final int MELEE_HAMMER_DAMAGE          = 14;

    /** How many tiles a knockback-eligible enemy is pushed on a Hammer hit. */
    public static final int   MELEE_HAMMER_KNOCKBACK_TILES  = 1;
    /** Probability (0–1) that a Hammer hit triggers knockback; 50% per swing. */
    public static final float MELEE_HAMMER_KNOCKBACK_CHANCE = 0.50f;

    /** CHAINSAW — high sustained damage; no knockback (grinds in place). */
    public static final int MELEE_CHAINSAW_DAMAGE      = 12;

    /** Probability (0–1) that a melee kill drops an ammo pickup; higher than the ranged baseline. */
    public static final float MELEE_KILL_AMMO_DROP_CHANCE = 0.60f;

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

    // =========================================================================
    // ABILITY CATALOGUE — BASE / PER_LEVEL / CAP
    // All values are PLACEHOLDERS — flag for playtesting.
    // AbilityResolver reads pre-scaled magnitudes from AbilityInstance at runtime;
    // these constants are used by WeaponRoller at weapon-spawn time (order-11).
    // =========================================================================

    // ── Critical Strike ────────────────────────────────────────────────────
    /** Level-1 crit chance for CRITICAL_STRIKE ability (5%). */
    public static final float CRIT_CHANCE_BASE            = 0.05f;
    /** Crit chance added per weapon level above 1. */
    public static final float CRIT_CHANCE_PER_LEVEL       = 0.015f;
    /** Maximum crit chance regardless of weapon level (30%). */
    public static final float CRIT_CHANCE_CAP             = 0.30f;

    // ── Armor Pierce ────────────────────────────────────────────────────────
    /** Level-1 pierce fraction for ARMOR_PIERCE ability (20%). */
    public static final float ARMOR_PIERCE_BASE           = 0.20f;
    /** Pierce fraction added per weapon level above 1. */
    public static final float ARMOR_PIERCE_PER_LEVEL      = 0.05f;
    /** Maximum pierce fraction regardless of weapon level (60%). */
    public static final float ARMOR_PIERCE_CAP            = 0.60f;

    // ── Executioner ─────────────────────────────────────────────────────────
    /** HP-fraction threshold below which EXECUTIONER fires (targets at 25% HP or lower). Fixed value. */
    public static final float EXECUTIONER_THRESHOLD       = 0.25f;
    /** Level-1 bonus multiplier for EXECUTIONER (30% of base damage added as bonus). */
    public static final float EXECUTIONER_BONUS_BASE      = 0.30f;
    /** Bonus multiplier added per weapon level above 1. */
    public static final float EXECUTIONER_BONUS_PER_LEVEL = 0.06f;
    /** Maximum EXECUTIONER bonus multiplier regardless of weapon level (80%). */
    public static final float EXECUTIONER_BONUS_CAP       = 0.80f;

    // ── Stagger Rounds ──────────────────────────────────────────────────────
    /** Level-1 stagger chance for STAGGER_ROUNDS ability (8%). */
    public static final float STAGGER_CHANCE_BASE         = 0.08f;
    /** Stagger chance added per weapon level above 1. */
    public static final float STAGGER_CHANCE_PER_LEVEL    = 0.02f;
    /** Maximum stagger chance regardless of weapon level (35%). */
    public static final float STAGGER_CHANCE_CAP          = 0.35f;

    // ── Overpenetration ─────────────────────────────────────────────────────
    /** Base number of additional enemies the shot can pierce (1). */
    public static final int   OVERPENETRATION_BASE_COUNT                = 1;
    /** Weapon levels needed to gain each additional pierce count (+1 per 3 levels). */
    public static final int   OVERPENETRATION_LEVELS_PER_STEP          = 3;
    /** Maximum extra-pierce count regardless of weapon level (3 additional enemies). */
    public static final int   OVERPENETRATION_MAX_COUNT                 = 3;
    /**
     * For already-piercing weapons (PlasmaRifle, Railgun): fraction of getEffectiveDamage()
     * applied as a bonus for each enemy hit beyond the first (25% bonus per extra pierce).
     */
    public static final float OVERPENETRATION_ALREADY_PIERCING_BONUS   = 0.25f;

    // =========================================================================
    // SUSTAIN ABILITIES — weapon-system-order-5
    // All values are PLACEHOLDERS — flag for playtesting.
    // =========================================================================

    // ── Lifesteal (ON_HIT) ──────────────────────────────────────────────────
    /** Level-1 lifesteal fraction: 6% of damage dealt returned as HP. */
    public static final float LIFESTEAL_BASE              = 0.06f;
    /** Lifesteal fraction added per weapon level above 1. */
    public static final float LIFESTEAL_PER_LEVEL         = 0.015f;
    /** Maximum lifesteal fraction regardless of weapon level (20%). */
    public static final float LIFESTEAL_CAP               = 0.20f;
    /** Minimum heal from lifesteal to show event text (avoids single-point spam). */
    public static final int   LIFESTEAL_TEXT_THRESHOLD    = 5;

    // ── Hemorrhage Harvest (ON_KILL) ─────────────────────────────────────────
    /** HP restored on kill at level 1. */
    public static final float HEMORRHAGE_HP_BASE          = 3f;
    /** HP per level scaling for Hemorrhage Harvest. */
    public static final float HEMORRHAGE_HP_PER_LEVEL     = 0.7f;
    /** Maximum HP per kill for Hemorrhage Harvest. */
    public static final int   HEMORRHAGE_HP_CAP           = 12;

    // ── Vampiric Crit (ON_CRIT) ──────────────────────────────────────────────
    /** HP restored on crit at level 1. */
    public static final float VAMPIRIC_CRIT_HP_BASE       = 4f;
    /** HP per level scaling for Vampiric Crit. */
    public static final float VAMPIRIC_CRIT_HP_PER_LEVEL  = 1.0f;
    /** Maximum HP per crit for Vampiric Crit. */
    public static final int   VAMPIRIC_CRIT_HP_CAP        = 14;

    // ── Adrenal Surge (ON_KILL) ───────────────────────────────────────────────
    /** Level-1 proc chance for Adrenal Surge (10%). */
    public static final float ADRENAL_SURGE_CHANCE_BASE      = 0.10f;
    /** Proc chance added per weapon level above 1. */
    public static final float ADRENAL_SURGE_CHANCE_PER_LEVEL = 0.03f;
    /** Maximum proc chance for Adrenal Surge regardless of weapon level (40%). */
    public static final float ADRENAL_SURGE_CHANCE_CAP       = 0.40f;
    /** Outgoing damage bonus multiplier applied to the next attack after Surge procs (+30%). */
    public static final float ADRENAL_SURGE_DAMAGE_BONUS     = 0.30f;

    // ── Bulwark Rounds (ON_RELOAD) ────────────────────────────────────────────
    /** Number of fixed temp-armor slots in PlayerStats (determines max concurrent Bulwark stacks). */
    public static final int   BULWARK_TEMP_ARMOR_SLOTS    = 4;
    /** Temporary armor points granted on reload completion at level 1. */
    public static final float BULWARK_ARMOR_BASE          = 2f;
    /** Temp armor added per weapon level above 1. */
    public static final float BULWARK_ARMOR_PER_LEVEL     = 0.5f;
    /** Maximum temp armor per reload for Bulwark Rounds. */
    public static final int   BULWARK_ARMOR_CAP           = 8;
    /** Number of player-action turns the Bulwark temp armor persists. */
    public static final int   BULWARK_ARMOR_DURATION      = 3;

    // ── Second Wind (PASSIVE) ─────────────────────────────────────────────────
    /** HP fraction at or below which Second Wind activates (30% HP = critically low). */
    public static final float SECOND_WIND_HP_THRESHOLD    = 0.30f;
    /** Level-1 outgoing damage bonus while Second Wind is active (+25%). */
    public static final float SECOND_WIND_BONUS_BASE      = 0.25f;
    /** Damage bonus multiplier added per weapon level above 1. */
    public static final float SECOND_WIND_BONUS_PER_LEVEL = 0.05f;
    /** Maximum Second Wind damage bonus regardless of weapon level (75%). */
    public static final float SECOND_WIND_BONUS_CAP       = 0.75f;

    // =========================================================================
    // MELEE-SPECIFIC ABILITIES — weapon-system-order-6
    // All values are PLACEHOLDERS — flag for playtesting.
    // =========================================================================

    // ── Kinetic Slam (ON_HIT, melee only) ────────────────────────────────────
    public static final float KINETIC_SLAM_CHANCE_BASE       = 0.20f;
    public static final float KINETIC_SLAM_CHANCE_PER_LEVEL  = 0.04f;
    public static final float KINETIC_SLAM_CHANCE_CAP        = 0.60f;
    public static final int   KINETIC_SLAM_WALL_BONUS_DAMAGE = 3;

    // ── Cleave (ON_HIT, melee only) ───────────────────────────────────────────
    public static final float CLEAVE_FRACTION_BASE      = 0.40f;
    public static final float CLEAVE_FRACTION_PER_LEVEL = 0.05f;
    public static final float CLEAVE_FRACTION_CAP       = 0.80f;

    // ── Salvage Strike (ON_KILL, melee only) ─────────────────────────────────
    public static final float SALVAGE_CHANCE_BASE      = 0.50f;
    public static final float SALVAGE_CHANCE_PER_LEVEL = 0.06f;
    public static final float SALVAGE_CHANCE_CAP       = 1.00f;
    public static final char  SALVAGE_AMMO_DROP_CHAR   = '6';

    // ── Scholar's Edge (ON_KILL, melee only) ─────────────────────────────────
    public static final float SCHOLARS_XP_BONUS_BASE      = 0.15f;
    public static final float SCHOLARS_XP_BONUS_PER_LEVEL = 0.05f;
    public static final float SCHOLARS_XP_BONUS_CAP       = 0.75f;

    // ── Berserker's Oath (PASSIVE/ON_KILL, legendary, melee only) ────────────
    public static final float BERSERKER_DAMAGE_PER_STACK  = 0.10f;
    public static final int   BERSERKER_HP_TICK_PER_STACK = 1;
    public static final int   BERSERKER_MAX_STACKS        = 5;

    // =========================================================================
    // UTILITY & ECONOMY ABILITIES — weapon-system-order-7
    // All values are PLACEHOLDERS — flag for playtesting.
    // =========================================================================

    // ── Scavenger Rounds (ON_KILL, gun only) ────────────────────────────────
    /** Level-1 proc chance for Scavenger Rounds (15%). */
    public static final float SCAVENGER_CHANCE_BASE          = 0.15f;
    /** Proc chance added per weapon level above 1. */
    public static final float SCAVENGER_CHANCE_PER_LEVEL     = 0.03f;
    /** Maximum proc chance for Scavenger Rounds regardless of weapon level (50%). */
    public static final float SCAVENGER_CHANCE_CAP           = 0.50f;
    /** Ammo units refunded to reserve at weapon level below SCAVENGER_HIGH_LEVEL_THRESHOLD. */
    public static final int   SCAVENGER_REFUND_BASE          = 1;
    /** Ammo units refunded to reserve at weapon level >= SCAVENGER_HIGH_LEVEL_THRESHOLD. */
    public static final int   SCAVENGER_REFUND_HIGH_LEVEL    = 2;
    /** Weapon level threshold at which the refund amount increases from BASE to HIGH_LEVEL. */
    public static final int   SCAVENGER_HIGH_LEVEL_THRESHOLD = 7;

    // ── Field Medic Rounds (ON_KILL, universal) ──────────────────────────────
    /** Level-1 proc chance for Field Medic Rounds (5%). */
    public static final float FIELD_MEDIC_CHANCE_BASE        = 0.05f;
    /** Proc chance added per weapon level above 1. */
    public static final float FIELD_MEDIC_CHANCE_PER_LEVEL   = 0.02f;
    /** Maximum proc chance for Field Medic Rounds regardless of weapon level (25%). */
    public static final float FIELD_MEDIC_CHANCE_CAP         = 0.25f;
    /** Tile character placed at the killed enemy's tile when Field Medic Rounds procs. '+' = MEDKIT_SMALL. */
    public static final char  FIELD_MEDIC_DROP_CHAR          = '+';

    // ── Credit Fang (ON_KILL, universal) ─────────────────────────────────────
    /** Credits awarded at level 1 for each Credit Fang kill. */
    public static final float CREDIT_FANG_BASE               = 2f;
    /** Additional credits per weapon level above 1. */
    public static final float CREDIT_FANG_PER_LEVEL          = 1f;
    /** Maximum credits awarded per kill regardless of weapon level. */
    public static final int   CREDIT_FANG_CAP                = 12;

    // =========================================================================
    // DOT & STATUS ABILITIES — weapon-system-order-8
    // All values are PLACEHOLDERS — flag for playtesting.
    // =========================================================================

    // ── Rend (BLEED DoT — ON_HIT) ────────────────────────────────────────────
    /** BLEED damage per turn at weapon level 1. */
    public static final float REND_DAMAGE_PER_TURN_BASE      = 2f;
    /** Additional BLEED damage per turn for each weapon level above 1. */
    public static final float REND_DAMAGE_PER_TURN_PER_LEVEL = 0.5f;
    /** Maximum BLEED damage per turn regardless of weapon level. */
    public static final float REND_DAMAGE_PER_TURN_CAP       = 6f;
    /** Number of world turns the BLEED effect persists. */
    public static final int   REND_DURATION_TURNS            = 4;

    // ── Incendiary (BURN DoT — ON_HIT) ───────────────────────────────────────
    /** BURNING damage per turn at weapon level 1 for INCENDIARY. */
    public static final float INCENDIARY_BURN_PER_TURN_BASE      = 3f;
    /** Additional burn damage per turn for each weapon level above 1. */
    public static final float INCENDIARY_BURN_PER_TURN_PER_LEVEL = 0.5f;
    /** Maximum burn damage per turn regardless of weapon level. */
    public static final float INCENDIARY_BURN_PER_TURN_CAP       = 7f;
    /** Base number of world turns the burn effect persists for INCENDIARY. */
    public static final int   INCENDIARY_BURN_DURATION           = 3;
    /** Extra turns added to burn duration when the Incinerator weapon fires INCENDIARY. */
    public static final int   INCENDIARY_INCINERATOR_EXTRA_TURNS = 1;

    // ── Stagger Rounds (STUN — ON_HIT) ───────────────────────────────────────
    /** Number of world turns the STUNNED effect persists when STAGGER_ROUNDS procs. */
    public static final int   STAGGER_STUN_DURATION = 1;

    // =========================================================================
    // POSITIONAL & SITUATIONAL ABILITIES — weapon-system-order-9
    // All values are PLACEHOLDERS — flag for playtesting.
    // =========================================================================

    // ── Point Blank (ON_HIT, GUN only) ───────────────────────────────────────
    /** Level-1 bonus damage fraction when target is at POINT_BLANK_MAX_DISTANCE or closer. */
    public static final float POINT_BLANK_BONUS_BASE      = 0.20f;
    /** Bonus fraction added per weapon level above 1. */
    public static final float POINT_BLANK_BONUS_PER_LEVEL = 0.05f;
    /** Maximum Point Blank bonus fraction regardless of weapon level. */
    public static final float POINT_BLANK_BONUS_CAP       = 0.70f;
    /** Maximum tile distance at which Point Blank fires (adjacent tile only). */
    public static final int   POINT_BLANK_MAX_DISTANCE    = 1;

    // ── Marksman's Patience (ON_HIT, GUN only) ────────────────────────────────
    /** Level-1 bonus fraction per tile beyond MARKSMAN_MIN_DISTANCE. */
    public static final float MARKSMAN_PER_TILE_BASE      = 0.05f;
    /** Per-tile bonus fraction added per weapon level above 1. */
    public static final float MARKSMAN_PER_TILE_PER_LEVEL = 0.01f;
    /** Maximum per-tile bonus fraction regardless of weapon level. */
    public static final float MARKSMAN_PER_TILE_CAP       = 0.12f;
    /** Minimum tile distance before the per-tile bonus accumulates (distance > this). */
    public static final int   MARKSMAN_MIN_DISTANCE       = 2;
    /** Hard cap on total Marksman's Patience bonus regardless of distance. */
    public static final float MARKSMAN_TOTAL_BONUS_CAP    = 0.60f;

    // ── Opening Salvo (ON_HIT, UNIVERSAL) ─────────────────────────────────────
    /** Level-1 bonus damage fraction when target is at full HP. */
    public static final float OPENING_SALVO_BONUS_BASE      = 0.30f;
    /** Bonus fraction added per weapon level above 1. */
    public static final float OPENING_SALVO_BONUS_PER_LEVEL = 0.07f;
    /** Maximum Opening Salvo bonus fraction regardless of weapon level. */
    public static final float OPENING_SALVO_BONUS_CAP       = 0.90f;

    // ── Rhythm / Heat-Up (ON_HIT, GUN only) ───────────────────────────────────
    /** Level-1 ramp bonus per consecutive hit on the same target (added per extra stack). */
    public static final float RHYTHM_RAMP_PER_HIT_BASE      = 0.06f;
    /** Ramp bonus added per weapon level above 1. */
    public static final float RHYTHM_RAMP_PER_HIT_PER_LEVEL = 0.01f;
    /** Maximum ramp-per-hit value regardless of weapon level. */
    public static final float RHYTHM_RAMP_PER_HIT_CAP       = 0.15f;
    /** Maximum consecutive-hit stacks before the bonus plateaus. */
    public static final int   RHYTHM_MAX_STACKS              = 5;

    // ── Static Discharge (ON_KILL, UNIVERSAL) ────────────────────────────────
    /** Level-1 splash damage dealt to enemies adjacent to the killed target. */
    public static final float STATIC_SPLASH_BASE      = 4f;
    /** Splash damage added per weapon level above 1. */
    public static final float STATIC_SPLASH_PER_LEVEL = 1f;
    /** Maximum splash damage regardless of weapon level. */
    public static final int   STATIC_SPLASH_CAP       = 14;

    // ── Resonant Rounds (ON_HIT, UNIVERSAL) ──────────────────────────────────
    /** Level-1 bonus as a fraction of the target's MAX HP applied as flat bonus damage. */
    public static final float RESONANT_PCT_BASE      = 0.04f;
    /** PCT fraction added per weapon level above 1. */
    public static final float RESONANT_PCT_PER_LEVEL = 0.008f;
    /** Maximum Resonant Rounds PCT fraction regardless of weapon level. */
    public static final float RESONANT_PCT_CAP       = 0.10f;

    // =========================================================================
    // LEGENDARY SIGNATURE ABILITIES — weapon-system-order-10
    // All values are PLACEHOLDERS — flag for playtesting.
    // =========================================================================

    // ── Soulforge (ON_KILL, legendary, universal) ─────────────────────────────
    /** Kills with this weapon needed to permanently raise its weapon level by 1. */
    public static final int   SOULFORGE_KILLS_PER_LEVEL_UP  = 5;

    // ── Judgment (ON_FIRE, legendary, gun only) ───────────────────────────────
    /** Number of fires between consecutive Judgment lance shots. */
    public static final int   JUDGMENT_COOLDOWN_FIRES        = 5;
    /** Maximum tiles the Judgment lance travels before stopping. */
    public static final int   JUDGMENT_LANCE_RANGE           = 20;
    /** Effective-damage multiplier applied to the Judgment lance hit. */
    public static final float JUDGMENT_DAMAGE_MULTIPLIER     = 3.0f;

    // ── Hellfire Nova (ON_CRIT, legendary, universal) ─────────────────────────
    /** Chebyshev tile radius of the AoE explosion triggered by a crit. */
    public static final int   HELLFIRE_NOVA_RADIUS           = 2;
    /** Fraction of critDamage dealt as AoE damage to enemies within the nova radius. */
    public static final float HELLFIRE_NOVA_DAMAGE_FRACTION  = 0.75f;

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
     * When true, start-room weapon offers can roll any tier (COMMON through LEGENDARY).
     * When false, only COMMON-tier weapons are offered in the start room.
     * Set to true for testing to verify higher-tier weapon spawns work correctly.
     */
    public static final boolean START_ROOM_ANY_TIER_ENABLED = true;

    /** Level-1 clip expansion from EXTENDED_MAG ability (+1 clip slot). */
    public static final int EXTENDED_MAG_BASE_COUNT      = 1;

    /** Weapon levels needed to gain each additional clip slot (+1 per 3 levels). */
    public static final int EXTENDED_MAG_LEVELS_PER_STEP = 3;

    /** Maximum clip expansion from EXTENDED_MAG regardless of weapon level. */
    public static final int EXTENDED_MAG_MAX_COUNT       = 4;
}
