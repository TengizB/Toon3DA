package ge.tbegvadze.toon3d.util;

/** Enemy system constants — textures, stats, AI parameters, hit flash, health bar. */
public final class EnemyConstants {

    private EnemyConstants() {}

    // Enemy sprite sheet textures — two 2x2 grid sheets, each holding 4 enemy types.
    // Blight sheet Q1=PLAGUE_HULK, Q2=EYE_TYRANT, Q3=IRON_STALKER, Q4=MIRE_WRAITH.
    // Infernal sheet Q1=GORE_BITER, Q2=SHELL_BRUTE, Q3=ACID_DRONE, Q4=VOID_SHROUD.
    public static final String  ENEMY_SHEET_BLIGHT_PATH          = "textures/enemies/enemy_sheet_blight.png";
    public static final String  ENEMY_SHEET_INFERNAL_PATH        = "textures/enemies/enemy_sheet_infernal.png";

    // Necrotic faction — individual single-sprite PNGs (one texture per archetype, drawn
    // full-frame rather than sliced from a 2x2 sheet). EnemyRenderer loads each as its own
    // Texture and maps the whole image as the enemy's TextureRegion.
    public static final String  ENEMY_BLIGHT_CORRUPTOR_PATH      = "textures/enemies/enemy_corruptor.png";
    public static final String  ENEMY_VORTEX_EYE_PATH            = "textures/enemies/enemy_vortex_eye.png";
    public static final String  ENEMY_GHOUL_PATH                 = "textures/enemies/enemy_ghoul.png";
    public static final String  ENEMY_CRAWLER_PATH               = "textures/enemies/enemy_crawler.png";
    public static final String  ENEMY_REVENANT_PATH              = "textures/enemies/enemy_revenant.png";

    // Boss texture — not used in regular combat; reserved for the boss encounter.
    public static final String  ENEMY_BOSS_OVERSEER_PATH         = "textures/enemies/enemy_boss_overseer.png";

    // Enemy system — AI and combat
    // Balance values (HP, damage, range, cadence, AI knobs) live in BalanceConfig —
    // the SINGLE SOURCE OF TRUTH. Only cosmetic fields (height/hover) stay here.
    // Probability (0–1) that a killed enemy drops an ammo pickup on its tile.
    public static final float   ENEMY_AMMO_DROP_CHANCE           = BalanceConfig.ENEMY_AMMO_DROP_CHANCE;
    public static final int     ALERT_RADIUS_TILES               = BalanceConfig.ALERT_RADIUS_TILES;
    public static final int     CHAIN_ALERT_RADIUS_TILES         = BalanceConfig.CHAIN_ALERT_RADIUS_TILES;
    public static final int     LOS_MAX_RANGE_TILES              = BalanceConfig.LOS_MAX_RANGE_TILES;

    // PLAGUE_HULK — slow tank melee (spawn '1')
    public static final int     PLAGUE_HULK_MAX_HEALTH           = BalanceConfig.PLAGUE_HULK_MAX_HEALTH;
    public static final int     PLAGUE_HULK_ATTACK_DAMAGE        = BalanceConfig.PLAGUE_HULK_ATTACK_DAMAGE;
    public static final int     PLAGUE_HULK_MOVE_EVERY_N_TURNS   = BalanceConfig.PLAGUE_HULK_MOVE_EVERY_N_TURNS;
    public static final float   PLAGUE_HULK_HEIGHT_MULTIPLIER    = 0.95f;

    // EYE_TYRANT — fast ranged kiter (spawn '2'); hover offset keeps it floating
    public static final int     EYE_TYRANT_MAX_HEALTH            = BalanceConfig.EYE_TYRANT_MAX_HEALTH;
    public static final int     EYE_TYRANT_ATTACK_DAMAGE         = BalanceConfig.EYE_TYRANT_ATTACK_DAMAGE;
    public static final int     EYE_TYRANT_RANGE_TILES           = BalanceConfig.EYE_TYRANT_RANGE_TILES;
    public static final float   EYE_TYRANT_HEIGHT_MULTIPLIER     = 0.55f;
    public static final float   EYE_TYRANT_HOVER_OFFSET_FRACTION = 0.25f;

    // GORE_BITER — fast light melee (spawn '3')
    public static final int     GORE_BITER_MAX_HEALTH            = BalanceConfig.GORE_BITER_MAX_HEALTH;
    public static final int     GORE_BITER_ATTACK_DAMAGE         = BalanceConfig.GORE_BITER_ATTACK_DAMAGE;
    public static final int     GORE_BITER_MOVE_EVERY_N_TURNS    = BalanceConfig.GORE_BITER_MOVE_EVERY_N_TURNS;
    public static final float   GORE_BITER_HEIGHT_MULTIPLIER     = 0.85f;

    // SHELL_BRUTE — heavy charger melee (spawn '4')
    public static final int     SHELL_BRUTE_MAX_HEALTH           = BalanceConfig.SHELL_BRUTE_MAX_HEALTH;
    public static final int     SHELL_BRUTE_ATTACK_DAMAGE        = BalanceConfig.SHELL_BRUTE_ATTACK_DAMAGE;
    public static final int     SHELL_BRUTE_MOVE_EVERY_N_TURNS   = BalanceConfig.SHELL_BRUTE_MOVE_EVERY_N_TURNS;
    public static final float   SHELL_BRUTE_HEIGHT_MULTIPLIER    = 1.05f;
    // Charger AI (Pillar 2) — telegraphed rush after a one-turn wind-up.
    public static final float   SHELL_BRUTE_CHARGE_DAMAGE_MULTIPLIER = BalanceConfig.SHELL_BRUTE_CHARGE_DAMAGE_MULTIPLIER;
    public static final int     SHELL_BRUTE_CHARGE_TRIGGER_MIN_TILES = BalanceConfig.SHELL_BRUTE_CHARGE_TRIGGER_MIN_TILES;
    public static final int     SHELL_BRUTE_CHARGE_TRIGGER_MAX_TILES = BalanceConfig.SHELL_BRUTE_CHARGE_TRIGGER_MAX_TILES;

    // MIRE_WRAITH — slow ground-based ranged acid (spawn '5')
    public static final int     MIRE_WRAITH_MAX_HEALTH           = BalanceConfig.MIRE_WRAITH_MAX_HEALTH;
    public static final int     MIRE_WRAITH_ATTACK_DAMAGE        = BalanceConfig.MIRE_WRAITH_ATTACK_DAMAGE;
    public static final int     MIRE_WRAITH_RANGE_TILES          = BalanceConfig.MIRE_WRAITH_RANGE_TILES;
    public static final int     MIRE_WRAITH_MOVE_EVERY_N_TURNS   = BalanceConfig.MIRE_WRAITH_MOVE_EVERY_N_TURNS;
    public static final float   MIRE_WRAITH_HEIGHT_MULTIPLIER    = 0.80f;

    // IRON_STALKER — armored elite, melee + ranged (spawn '!')
    public static final int     IRON_STALKER_MAX_HEALTH          = BalanceConfig.IRON_STALKER_MAX_HEALTH;
    public static final int     IRON_STALKER_MELEE_DAMAGE        = BalanceConfig.IRON_STALKER_MELEE_DAMAGE;
    public static final int     IRON_STALKER_RANGED_DAMAGE       = BalanceConfig.IRON_STALKER_RANGED_DAMAGE;
    public static final int     IRON_STALKER_RANGE_TILES         = BalanceConfig.IRON_STALKER_RANGE_TILES;
    public static final int     IRON_STALKER_MOVE_EVERY_N_TURNS  = BalanceConfig.IRON_STALKER_MOVE_EVERY_N_TURNS;
    public static final float   IRON_STALKER_HEIGHT_MULTIPLIER   = 1.10f;

    // ACID_DRONE — ranged mechanical (spawn '$')
    public static final int     ACID_DRONE_MAX_HEALTH            = BalanceConfig.ACID_DRONE_MAX_HEALTH;
    public static final int     ACID_DRONE_ATTACK_DAMAGE         = BalanceConfig.ACID_DRONE_ATTACK_DAMAGE;
    public static final int     ACID_DRONE_RANGE_TILES           = BalanceConfig.ACID_DRONE_RANGE_TILES;
    public static final int     ACID_DRONE_MOVE_EVERY_N_TURNS    = BalanceConfig.ACID_DRONE_MOVE_EVERY_N_TURNS;
    public static final float   ACID_DRONE_HEIGHT_MULTIPLIER     = 0.70f;

    // VOID_SHROUD — fast stealth melee FLANKER (spawn '^')
    public static final int     VOID_SHROUD_MAX_HEALTH           = BalanceConfig.VOID_SHROUD_MAX_HEALTH;
    public static final int     VOID_SHROUD_ATTACK_DAMAGE        = BalanceConfig.VOID_SHROUD_ATTACK_DAMAGE;
    public static final int     VOID_SHROUD_MOVE_EVERY_N_TURNS   = BalanceConfig.VOID_SHROUD_MOVE_EVERY_N_TURNS;
    public static final float   VOID_SHROUD_HEIGHT_MULTIPLIER    = 0.80f;
    // Flanker AI (Pillar 2) — prefers the tile behind the player's facing and hits harder there.
    public static final float   VOID_SHROUD_FLANK_DAMAGE_MULTIPLIER = BalanceConfig.VOID_SHROUD_FLANK_DAMAGE_MULTIPLIER;

    // GHOUL — slow shambling melee chaff (spawn '~')
    public static final int     GHOUL_MAX_HEALTH                 = BalanceConfig.GHOUL_MAX_HEALTH;
    public static final int     GHOUL_ATTACK_DAMAGE              = BalanceConfig.GHOUL_ATTACK_DAMAGE;
    public static final int     GHOUL_MOVE_EVERY_N_TURNS         = BalanceConfig.GHOUL_MOVE_EVERY_N_TURNS;
    public static final float   GHOUL_HEIGHT_MULTIPLIER          = 0.90f;

    // CRAWLER — fast, fragile low melee chaff (spawn 'z')
    public static final int     CRAWLER_MAX_HEALTH               = BalanceConfig.CRAWLER_MAX_HEALTH;
    public static final int     CRAWLER_ATTACK_DAMAGE            = BalanceConfig.CRAWLER_ATTACK_DAMAGE;
    public static final int     CRAWLER_MOVE_EVERY_N_TURNS       = BalanceConfig.CRAWLER_MOVE_EVERY_N_TURNS;
    public static final float   CRAWLER_HEIGHT_MULTIPLIER        = 0.55f;

    // REVENANT — fast, hard-hitting undead soldier melee (spawn 'K')
    public static final int     REVENANT_MAX_HEALTH              = BalanceConfig.REVENANT_MAX_HEALTH;
    public static final int     REVENANT_ATTACK_DAMAGE           = BalanceConfig.REVENANT_ATTACK_DAMAGE;
    public static final int     REVENANT_MOVE_EVERY_N_TURNS      = BalanceConfig.REVENANT_MOVE_EVERY_N_TURNS;
    public static final float   REVENANT_HEIGHT_MULTIPLIER       = 1.05f;

    // VORTEX_EYE — short-range ranged chaff caster (spawn 'V'); hovers like the Eye Tyrant
    public static final int     VORTEX_EYE_MAX_HEALTH            = BalanceConfig.VORTEX_EYE_MAX_HEALTH;
    public static final int     VORTEX_EYE_ATTACK_DAMAGE         = BalanceConfig.VORTEX_EYE_ATTACK_DAMAGE;
    public static final int     VORTEX_EYE_RANGE_TILES           = BalanceConfig.VORTEX_EYE_RANGE_TILES;
    public static final int     VORTEX_EYE_MOVE_EVERY_N_TURNS    = BalanceConfig.VORTEX_EYE_MOVE_EVERY_N_TURNS;
    public static final float   VORTEX_EYE_HEIGHT_MULTIPLIER     = 0.55f;

    // BLIGHT_CORRUPTOR — durable slow infected brute soldier melee (spawn '*')
    public static final int     BLIGHT_CORRUPTOR_MAX_HEALTH         = BalanceConfig.BLIGHT_CORRUPTOR_MAX_HEALTH;
    public static final int     BLIGHT_CORRUPTOR_ATTACK_DAMAGE      = BalanceConfig.BLIGHT_CORRUPTOR_ATTACK_DAMAGE;
    public static final int     BLIGHT_CORRUPTOR_MOVE_EVERY_N_TURNS = BalanceConfig.BLIGHT_CORRUPTOR_MOVE_EVERY_N_TURNS;
    public static final float   BLIGHT_CORRUPTOR_HEIGHT_MULTIPLIER  = 1.00f;

    // Shared ranged AI — kiting constants reused across ranged types
    public static final int     RANGED_KITE_MIN_TILES            = BalanceConfig.RANGED_KITE_MIN_TILES;

    public static final float   DORMANT_SHADE_DAMPEN             = 0.7f;
    public static final int     STUCK_TURNS_BEFORE_WIGGLE        = BalanceConfig.STUCK_TURNS_BEFORE_WIGGLE;
    public static final boolean ENEMY_GREEDY_WIGGLE_ENABLED      = true;
    public static final float   MAX_ENEMY_DRAW_DISTANCE_TILES    = 14f;

    // Enemy hit flash — white blanch on damage contact (purely cosmetic, wall-clock timed)
    public static final float   ENEMY_HIT_FLASH_DURATION_SECONDS = 0.18f;

    // Enemy health bar — floating billboard above each alerted enemy sprite
    public static final float   ENEMY_HEALTH_BAR_WIDTH_FRACTION     = 0.9f;
    public static final float   ENEMY_HEALTH_BAR_HEIGHT_FRACTION    = 0.06f;
    public static final float   ENEMY_HEALTH_BAR_GAP_FRACTION       = 0.04f;
    public static final float   ENEMY_HEALTH_BAR_MIN_PIXELS         = 3f;
    // Upper clamps so a point-blank enemy (huge billboard) does not draw a screen-spanning bar.
    public static final float   ENEMY_HEALTH_BAR_MAX_PIXELS         = 24f;
    public static final float   ENEMY_HEALTH_BAR_MAX_WIDTH_PIXELS   = 240f;
    public static final float   ENEMY_HEALTH_BAR_BORDER_PIXELS      = 1f;
    public static final float   ENEMY_HEALTH_BAR_MAX_DISTANCE_TILES = 12f;

    // On-screen safe area for the floating enemy UI cluster (health bar + name tag + intent icon).
    // When an enemy stands right next to the player its billboard fills the screen and the anchor
    // would push the whole cluster off the top / off the sides; these keep it clamped inside view.
    // Left/right/top gap (screen pixels) kept clear so the cluster never touches the screen edge.
    public static final float   ENEMY_UI_SCREEN_EDGE_MARGIN         = 8f;
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
    // Text is hidden beyond this distance to de-clutter; rely on color gradient at range.
    public static final float   ENEMY_HP_TEXT_MAX_DISTANCE_TILES = 5f;
    public static final float   ENEMY_HP_TEXT_FONT_SCALE         = 0.65f;
    public static final float   ENEMY_HP_TEXT_RED                = 1.00f;
    public static final float   ENEMY_HP_TEXT_GREEN              = 1.00f;
    public static final float   ENEMY_HP_TEXT_BLUE               = 1.00f;

    // Active-Block visuals (strategy-combat-order-3). While an enemy holds Block > 0:
    //   - a steely-blue plating tint is blended over the billboard (bracing shimmer), and
    //   - the current Block value is drawn just left of the health bar in shield-blue.
    // The intent-icon shield (order-2) telegraphs the UPCOMING Block; these show ACTIVE Block.
    /** Strength of the blue plating tint blended over a blocking enemy's sprite [0,1]. */
    public static final float   ENEMY_BLOCK_TINT_STRENGTH        = 0.42f;
    public static final float   ENEMY_BLOCK_TINT_RED             = 0.38f;
    public static final float   ENEMY_BLOCK_TINT_GREEN           = 0.60f;
    public static final float   ENEMY_BLOCK_TINT_BLUE            = 1.00f;
    /** Gentle shimmer added to the plating tint: strength varies by this ± around the base. */
    public static final float   ENEMY_BLOCK_TINT_SHIMMER_AMOUNT  = 0.12f;
    /** Shimmer oscillation frequency (Hz). */
    public static final float   ENEMY_BLOCK_TINT_SHIMMER_HZ      = 1.4f;
    /** Beyond this distance the active-Block number is hidden (matches HP text de-clutter). */
    public static final float   ENEMY_BLOCK_NUMBER_MAX_DISTANCE_TILES = 6f;
    /** Font scale for the active-Block number drawn beside the health bar. */
    public static final float   ENEMY_BLOCK_NUMBER_FONT_SCALE    = 0.62f;
    /** Horizontal gap between the active-Block number's right edge and the health bar's left edge. */
    public static final float   ENEMY_BLOCK_NUMBER_BAR_GAP       = 5f;
    public static final float   ENEMY_BLOCK_NUMBER_RED           = 0.55f;
    public static final float   ENEMY_BLOCK_NUMBER_GREEN         = 0.80f;
    public static final float   ENEMY_BLOCK_NUMBER_BLUE          = 1.00f;

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

    // -------------------------------------------------------------------------
    // Boss encounter stats
    //
    // PLACEHOLDER NUMBERS — TO BE RE-DERIVED VIA THE BOSS BALANCE RULESET (idea 6).
    // Every HP / damage / phase number below is a flat placeholder, exactly the "big
    // trash mob with arbitrary HP" the boss ruleset replaces. When boss FIGHTS are
    // actually built/retuned (deferred — they need story/run structure), re-derive these
    // by formula, NOT by guesswork:
    //   * HP   = GameMath.bossEffectiveHitPoints(expectedPlayerSustainedDamagePerTurn(depth),
    //            targetFightTurns, multiPhaseFactor)   — never a literal (RULE 1).
    //   * DPT  = GameMath.bossDamagePerTurnForSurvivalCheck(playerEHP, fightTurns, ratio) (RULE 3),
    //            with NO single hit over BalanceConfig.BOSS_HARD_SINGLE_HIT_FRACTION of eHP,
    //            and any hit over 25% telegraphed one turn ahead (RULE 3 fairness caps).
    //   * phases at GameMath.bossPhaseHealthThreshold(i, n) (RULE 4).
    // Targets/bands live in BalanceConfig SECTION 14; see docs/balance-rule-system.txt
    // (Boss appendix). BalanceReport's BOSS RULESET section prints the derived HP each
    // current boss SHOULD have at its depth next to the placeholder literals below.
    // -------------------------------------------------------------------------

    // The Overseer (depth 5) — security core robot; laser lanes + melee charge
    public static final int OVERSEER_MAX_HP        = 250;
    public static final int OVERSEER_DEPTH         = 5;
    public static final int OVERSEER_LASER_DAMAGE  = 20;
    public static final int OVERSEER_CHARGE_DAMAGE = 30;
    public static final int OVERSEER_RAM_COOLDOWN  = 3;
    // Accent color (cyan-white)
    public static final float OVERSEER_ACCENT_R = 0.60f;
    public static final float OVERSEER_ACCENT_G = 0.90f;
    public static final float OVERSEER_ACCENT_B = 1.00f;

    // The Corruptor (depth 10) — mutated scientist; summoner + acid burst
    public static final int CORRUPTOR_MAX_HP             = 450;
    public static final int CORRUPTOR_DEPTH              = 10;
    public static final int CORRUPTOR_SUMMON_COOLDOWN    = 3;
    public static final int CORRUPTOR_MINION_CAP         = 5;
    public static final int CORRUPTOR_ACID_DAMAGE        = 15;
    public static final int CORRUPTOR_ACID_POOL_DURATION = 3;
    // Accent color (toxic green)
    public static final float CORRUPTOR_ACCENT_R = 0.40f;
    public static final float CORRUPTOR_ACCENT_G = 1.00f;
    public static final float CORRUPTOR_ACCENT_B = 0.10f;

    // =====================================================================
    // Special-ability move-sets (strategy-combat-order-5)
    // A scripted archetype consults its move-set on a fixed cadence, producing a readable rhythm the
    // player learns and plans around. Each damaging special is telegraphed one turn ahead (order-1),
    // so no fairness rule is broken. All numbers here so nothing is hardcoded (split-constants rule).
    // =====================================================================

    /** Enemy turns between special-ability attempts for a caster (Acid Drone / Mire Wraith / Vortex Eye). */
    public static final int SPECIAL_CADENCE_CASTER = 3;
    /** Frequent, layered elite script (Iron Stalker) — a special roughly every other turn. */
    public static final int SPECIAL_CADENCE_ELITE  = 2;
    /** Slow, heavy specials (Plague Hulk area slam, Blight Corruptor summon, Eye Tyrant blind). */
    public static final int SPECIAL_CADENCE_SLOW   = 4;

    /** How many recent picks the per-enemy no-repeat history tracks (StS "cannot repeat" window). */
    public static final int MOVE_HISTORY_SIZE = 4;

    // BUFF_SELF — the caster gains EMPOWERED (outgoing-damage buff). Enemy-specific values so the buff
    // prices into the enemy's Threat Points independently of the player's stim buff.
    public static final int BUFF_SELF_EMPOWERED_PERCENT = 40;
    public static final int BUFF_SELF_DURATION_TURNS    = 3;

    // DEBUFF_PLAYER — control applied down a cardinal line. Duration in world turns.
    public static final int DEBUFF_SLOW_DURATION_TURNS  = 3;
    public static final int DEBUFF_BLIND_DURATION_TURNS = 2;
    /**
     * WEAK debuff duration (strategy-combat-order-6): the acid casters (Mire Wraith / Acid Drone)
     * corrode the marine's output so its own HUD damage numbers dim — the symmetric, player-facing
     * side of the Weak power. Kept short so it softens a couple of hits, not the whole fight.
     */
    public static final int DEBUFF_WEAK_DURATION_TURNS  = 2;

    // SUMMON — spawn chaff on empty adjacent tiles. The hard caps bound the encounter-budget headroom
    // (docs/balance-rule-system.txt): a per-summoner ceiling AND a per-room live-enemy ceiling.
    public static final int SUMMON_COUNT_MIN     = 1;
    public static final int SUMMON_COUNT_MAX     = 2;
    public static final int SUMMON_PER_ENEMY_CAP = 4;
    public static final int SUMMON_ROOM_LIVE_CAP = 14;

    // AREA_STRIKE — telegraphed slam hitting every tile within this cross radius of the enemy.
    public static final int   AREA_STRIKE_RADIUS_TILES      = 1;
    public static final float AREA_STRIKE_DAMAGE_MULTIPLIER = 1.35f;

    // Hell Baron (depth 15) — armored greater demon; firewall + enrage
    public static final int HELL_BARON_MAX_HP               = 700;
    public static final int HELL_BARON_DEPTH                = 15;
    public static final int HELL_BARON_FIREWALL_COOLDOWN_P1 = 4;
    public static final int HELL_BARON_FIREWALL_COOLDOWN_P2 = 2;
    public static final int HELL_BARON_FIREWALL_DURATION    = 4;
    public static final int HELL_BARON_FIRE_DAMAGE          = 12;
    public static final int HELL_BARON_CLEAVE_DAMAGE_P1     = 35;
    public static final int HELL_BARON_CLEAVE_DAMAGE_P2     = 52;
    // Accent color (ember orange-red)
    public static final float HELL_BARON_ACCENT_R = 1.00f;
    public static final float HELL_BARON_ACCENT_G = 0.30f;
    public static final float HELL_BARON_ACCENT_B = 0.05f;
}
