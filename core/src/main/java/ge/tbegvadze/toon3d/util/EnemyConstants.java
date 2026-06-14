package ge.tbegvadze.toon3d.util;

/** Enemy system constants — textures, stats, AI parameters, hit flash, health bar. */
public final class EnemyConstants {

    private EnemyConstants() {}

    // Enemy sprite sheet textures — two 2x2 grid sheets, each holding 4 enemy types.
    // Blight sheet Q1=PLAGUE_HULK, Q2=EYE_TYRANT, Q3=IRON_STALKER, Q4=MIRE_WRAITH.
    // Infernal sheet Q1=GORE_BITER, Q2=SHELL_BRUTE, Q3=ACID_DRONE, Q4=VOID_SHROUD.
    public static final String  ENEMY_SHEET_BLIGHT_PATH          = "textures/enemies/enemy_sheet_blight.png";
    public static final String  ENEMY_SHEET_INFERNAL_PATH        = "textures/enemies/enemy_sheet_infernal.png";

    // Boss texture — not used in regular combat; reserved for the boss encounter.
    public static final String  ENEMY_BOSS_OVERSEER_PATH         = "textures/enemies/enemy_boss_overseer.png";

    // Enemy system — AI and combat
    // Probability (0–1) that a killed enemy drops an ammo pickup on its tile.
    public static final float   ENEMY_AMMO_DROP_CHANCE           = 0.25f;
    public static final int     ALERT_RADIUS_TILES               = 4;
    public static final int     CHAIN_ALERT_RADIUS_TILES         = 5;
    public static final int     LOS_MAX_RANGE_TILES              = 16;

    // PLAGUE_HULK — slow tank melee (spawn '1')
    public static final int     PLAGUE_HULK_MAX_HEALTH           = 60;
    public static final int     PLAGUE_HULK_ATTACK_DAMAGE        = 14;
    public static final int     PLAGUE_HULK_MOVE_EVERY_N_TURNS   = 2;
    public static final float   PLAGUE_HULK_HEIGHT_MULTIPLIER    = 0.95f;

    // EYE_TYRANT — fast ranged kiter (spawn '2'); hover offset keeps it floating
    public static final int     EYE_TYRANT_MAX_HEALTH            = 18;
    public static final int     EYE_TYRANT_ATTACK_DAMAGE         = 8;
    public static final int     EYE_TYRANT_RANGE_TILES           = 5;
    public static final int     EYE_TYRANT_KITE_MIN_TILES        = 2;
    public static final float   EYE_TYRANT_HEIGHT_MULTIPLIER     = 0.55f;
    public static final float   EYE_TYRANT_HOVER_OFFSET_FRACTION = 0.25f;

    // GORE_BITER — fast light melee (spawn '3')
    public static final int     GORE_BITER_MAX_HEALTH            = 20;
    public static final int     GORE_BITER_ATTACK_DAMAGE         = 10;
    public static final int     GORE_BITER_MOVE_EVERY_N_TURNS    = 1;
    public static final float   GORE_BITER_HEIGHT_MULTIPLIER     = 0.85f;

    // SHELL_BRUTE — heavy charger melee (spawn '4')
    public static final int     SHELL_BRUTE_MAX_HEALTH           = 45;
    public static final int     SHELL_BRUTE_ATTACK_DAMAGE        = 18;
    public static final int     SHELL_BRUTE_MOVE_EVERY_N_TURNS   = 1;
    public static final float   SHELL_BRUTE_HEIGHT_MULTIPLIER    = 1.05f;

    // MIRE_WRAITH — slow hovering ranged acid (spawn '5')
    public static final int     MIRE_WRAITH_MAX_HEALTH           = 70;
    public static final int     MIRE_WRAITH_ATTACK_DAMAGE        = 12;
    public static final int     MIRE_WRAITH_RANGE_TILES          = 4;
    public static final int     MIRE_WRAITH_MOVE_EVERY_N_TURNS   = 2;
    public static final float   MIRE_WRAITH_HEIGHT_MULTIPLIER    = 0.80f;
    public static final float   MIRE_WRAITH_HOVER_OFFSET_FRACTION = 0.15f;

    // IRON_STALKER — armored elite, melee + ranged (spawn '!')
    public static final int     IRON_STALKER_MAX_HEALTH          = 120;
    public static final int     IRON_STALKER_MELEE_DAMAGE        = 22;
    public static final int     IRON_STALKER_RANGED_DAMAGE       = 16;
    public static final int     IRON_STALKER_RANGE_TILES         = 4;
    public static final int     IRON_STALKER_MOVE_EVERY_N_TURNS  = 1;
    public static final float   IRON_STALKER_HEIGHT_MULTIPLIER   = 1.10f;

    // ACID_DRONE — ranged mechanical (spawn '$')
    public static final int     ACID_DRONE_MAX_HEALTH            = 25;
    public static final int     ACID_DRONE_ATTACK_DAMAGE         = 10;
    public static final int     ACID_DRONE_RANGE_TILES           = 4;
    public static final int     ACID_DRONE_MOVE_EVERY_N_TURNS    = 1;
    public static final float   ACID_DRONE_HEIGHT_MULTIPLIER     = 0.70f;

    // VOID_SHROUD — fast stealth melee (spawn '^')
    public static final int     VOID_SHROUD_MAX_HEALTH           = 30;
    public static final int     VOID_SHROUD_ATTACK_DAMAGE        = 12;
    public static final int     VOID_SHROUD_MOVE_EVERY_N_TURNS   = 1;
    public static final float   VOID_SHROUD_HEIGHT_MULTIPLIER    = 0.80f;

    // Shared ranged AI — kiting constants reused across ranged types
    public static final int     RANGED_KITE_MIN_TILES            = 2;

    public static final float   DORMANT_SHADE_DAMPEN             = 0.7f;
    public static final int     STUCK_TURNS_BEFORE_WIGGLE        = 2;
    public static final boolean ENEMY_GREEDY_WIGGLE_ENABLED      = true;
    public static final float   MAX_ENEMY_DRAW_DISTANCE_TILES    = 14f;

    // Enemy hit flash — white blanch on damage contact (purely cosmetic, wall-clock timed)
    public static final float   ENEMY_HIT_FLASH_DURATION_SECONDS = 0.18f;

    // Enemy health bar — floating billboard above each alerted enemy sprite
    public static final float   ENEMY_HEALTH_BAR_WIDTH_FRACTION     = 0.9f;
    public static final float   ENEMY_HEALTH_BAR_HEIGHT_FRACTION    = 0.06f;
    public static final float   ENEMY_HEALTH_BAR_GAP_FRACTION       = 0.04f;
    public static final float   ENEMY_HEALTH_BAR_MIN_PIXELS         = 3f;
    public static final float   ENEMY_HEALTH_BAR_BORDER_PIXELS      = 1f;
    public static final float   ENEMY_HEALTH_BAR_MAX_DISTANCE_TILES = 12f;
    // Border / backdrop tint (semi-transparent near-black frame)
    public static final float   ENEMY_HEALTH_BAR_BORDER_RED         = 0.05f;
    public static final float   ENEMY_HEALTH_BAR_BORDER_GREEN       = 0.05f;
    public static final float   ENEMY_HEALTH_BAR_BORDER_BLUE        = 0.05f;
    public static final float   ENEMY_HEALTH_BAR_BORDER_ALPHA       = 0.85f;
    // Empty-track tint (missing health, dark red-gray)
    public static final float   ENEMY_HEALTH_BAR_TRACK_RED          = 0.18f;
    public static final float   ENEMY_HEALTH_BAR_TRACK_GREEN        = 0.05f;
    public static final float   ENEMY_HEALTH_BAR_TRACK_BLUE         = 0.05f;
    // Health gradient colour stops: full=green, half=yellow, empty=red
    public static final float   ENEMY_HEALTH_FULL_RED               = 0.10f;
    public static final float   ENEMY_HEALTH_FULL_GREEN             = 0.85f;
    public static final float   ENEMY_HEALTH_FULL_BLUE              = 0.10f;
    public static final float   ENEMY_HEALTH_HALF_RED               = 1.00f;
    public static final float   ENEMY_HEALTH_HALF_GREEN             = 0.85f;
    public static final float   ENEMY_HEALTH_HALF_BLUE              = 0.10f;
    public static final float   ENEMY_HEALTH_EMPTY_RED              = 1.00f;
    public static final float   ENEMY_HEALTH_EMPTY_GREEN            = 0.10f;
    public static final float   ENEMY_HEALTH_EMPTY_BLUE             = 0.10f;

    // HP text drawn inside the health bar: "current/max" in white for legibility
    public static final float   ENEMY_HP_TEXT_FONT_SCALE         = 0.65f;
    public static final float   ENEMY_HP_TEXT_RED                = 1.00f;
    public static final float   ENEMY_HP_TEXT_GREEN              = 1.00f;
    public static final float   ENEMY_HP_TEXT_BLUE               = 1.00f;

    // Enemy name tag — shown above health bar only when close enough
    public static final float ENEMY_NAME_TAG_MAX_DISTANCE_TILES = 8f;
    // Name tag font scale applied to the default BitmapFont
    public static final float ENEMY_NAME_TAG_FONT_SCALE         = 0.90f;
    // Vertical gap between name tag baseline and top of health bar (screen pixels)
    public static final float ENEMY_NAME_TAG_BAR_GAP            = 4f;
    // Level-tier colors for the name tag text (determined by dungeonLevel at spawn)
    // Tier 1 LVL 1-2: white
    public static final float ENEMY_NAME_TAG_TIER1_R = 1.00f;
    public static final float ENEMY_NAME_TAG_TIER1_G = 1.00f;
    public static final float ENEMY_NAME_TAG_TIER1_B = 1.00f;
    // Tier 2 LVL 3-4: green
    public static final float ENEMY_NAME_TAG_TIER2_R = 0.25f;
    public static final float ENEMY_NAME_TAG_TIER2_G = 1.00f;
    public static final float ENEMY_NAME_TAG_TIER2_B = 0.25f;
    // Tier 3 LVL 5: blue
    public static final float ENEMY_NAME_TAG_TIER3_R = 0.25f;
    public static final float ENEMY_NAME_TAG_TIER3_G = 0.60f;
    public static final float ENEMY_NAME_TAG_TIER3_B = 1.00f;
    // Tier 4 LVL 6-7: violet
    public static final float ENEMY_NAME_TAG_TIER4_R = 0.80f;
    public static final float ENEMY_NAME_TAG_TIER4_G = 0.25f;
    public static final float ENEMY_NAME_TAG_TIER4_B = 1.00f;
    // Tier 5 LVL 8+: red
    public static final float ENEMY_NAME_TAG_TIER5_R = 1.00f;
    public static final float ENEMY_NAME_TAG_TIER5_G = 0.18f;
    public static final float ENEMY_NAME_TAG_TIER5_B = 0.18f;
}
