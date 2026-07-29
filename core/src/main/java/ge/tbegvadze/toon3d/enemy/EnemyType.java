package ge.tbegvadze.toon3d.enemy;

import ge.tbegvadze.toon3d.status.StatusType;
import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.BossBalance;
import ge.tbegvadze.toon3d.util.EnemyConstants;
import ge.tbegvadze.toon3d.util.GameBalance;
import ge.tbegvadze.toon3d.util.GameMath;

/** Per-type configuration for each enemy archetype. Values drawn from Constants and GameBalance. */
public enum EnemyType {

    PLAGUE_HULK {
        @Override public int    maxHealth()         { return EnemyConstants.PLAGUE_HULK_MAX_HEALTH; }
        @Override public int    attackDamage()       { return EnemyConstants.PLAGUE_HULK_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()   { return 1; }
        @Override public int    moveEveryNTurns()    { return EnemyConstants.PLAGUE_HULK_MOVE_EVERY_N_TURNS; }
        @Override public boolean isRanged()          { return false; }
        @Override public float  heightMultiplier()   { return EnemyConstants.PLAGUE_HULK_HEIGHT_MULTIPLIER; }
        @Override public int    baseCreditReward()   { return GameBalance.CREDIT_REWARD_PLAGUE_HULK; }
        @Override public String displayName()        { return "Plague Hulk"; }
        @Override public EnemyFamily family()        { return EnemyFamily.ABERRATION; }
        @Override public EnemyRole role()            { return EnemyRole.SOLDIER; }
        @Override public float  positionalMultiplier() { return BalanceConfig.POSITIONAL_MULT_MELEE; }
        @Override public int    attackCadenceTurns() { return EnemyConstants.PLAGUE_HULK_MOVE_EVERY_N_TURNS; }
        @Override public char   spawnChar()          { return '1'; }
        @Override public String tacticalVerb()       { return "TANK: kill at range — don't grind it down in melee."; }
        // Slow, positional area slam (order-5): a telegraphed cross-strike to step out of.
        @Override public SpecialAbility[] moveSet()          { return MOVESET_AREA; }
        @Override public int             specialAbilityCadenceTurns() { return EnemyConstants.SPECIAL_CADENCE_SLOW; }
    },

    EYE_TYRANT {
        @Override public int    maxHealth()         { return EnemyConstants.EYE_TYRANT_MAX_HEALTH; }
        @Override public int    attackDamage()       { return EnemyConstants.EYE_TYRANT_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()   { return EnemyConstants.EYE_TYRANT_RANGE_TILES; }
        @Override public int    moveEveryNTurns()    { return 1; }
        @Override public boolean isRanged()          { return true; }
        @Override public float  heightMultiplier()   { return EnemyConstants.EYE_TYRANT_HEIGHT_MULTIPLIER; }
        @Override public int    baseCreditReward()   { return GameBalance.CREDIT_REWARD_EYE_TYRANT; }
        @Override public String displayName()        { return "Eye Tyrant"; }
        @Override public EnemyFamily family()        { return EnemyFamily.ABERRATION; }
        @Override public EnemyRole role()            { return EnemyRole.CHAFF; }
        @Override public float  positionalMultiplier() { return BalanceConfig.POSITIONAL_MULT_RANGED; }
        @Override public int    attackCadenceTurns() { return 1; }
        @Override public char   spawnChar()          { return '2'; }
        @Override public String tacticalVerb()       { return "SNIPER: break its line — step off its row or column to deny the shot."; }
        // Rare long-cadence BLINDED debuff for flavour (order-5) — chaff stays mostly simple.
        @Override public SpecialAbility[] moveSet()          { return MOVESET_DEBUFF; }
        @Override public int             specialAbilityCadenceTurns() { return EnemyConstants.SPECIAL_CADENCE_SLOW; }
    },

    GORE_BITER {
        @Override public int    maxHealth()         { return EnemyConstants.GORE_BITER_MAX_HEALTH; }
        @Override public int    attackDamage()       { return EnemyConstants.GORE_BITER_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()   { return 1; }
        @Override public int    moveEveryNTurns()    { return EnemyConstants.GORE_BITER_MOVE_EVERY_N_TURNS; }
        @Override public boolean isRanged()          { return false; }
        @Override public float  heightMultiplier()   { return EnemyConstants.GORE_BITER_HEIGHT_MULTIPLIER; }
        @Override public int    baseCreditReward()   { return GameBalance.CREDIT_REWARD_GORE_BITER; }
        @Override public String displayName()        { return "Gore Biter"; }
        @Override public EnemyFamily family()        { return EnemyFamily.ABERRATION; }
        @Override public EnemyRole role()            { return EnemyRole.CHAFF; }
        @Override public float  positionalMultiplier() { return BalanceConfig.POSITIONAL_MULT_FAST_MELEE; }
        @Override public int    attackCadenceTurns() { return 1; }
        @Override public char   spawnChar()          { return '3'; }
        @Override public String tacticalVerb()       { return "SWARMER: don't get surrounded — funnel the pack into a chokepoint."; }
    },

    SHELL_BRUTE {
        @Override public int    maxHealth()         { return EnemyConstants.SHELL_BRUTE_MAX_HEALTH; }
        @Override public int    attackDamage()       { return EnemyConstants.SHELL_BRUTE_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()   { return 1; }
        @Override public int    moveEveryNTurns()    { return EnemyConstants.SHELL_BRUTE_MOVE_EVERY_N_TURNS; }
        @Override public boolean isRanged()          { return false; }
        @Override public float  heightMultiplier()   { return EnemyConstants.SHELL_BRUTE_HEIGHT_MULTIPLIER; }
        @Override public int    baseCreditReward()   { return GameBalance.CREDIT_REWARD_SHELL_BRUTE; }
        @Override public String displayName()        { return "Shell Brute"; }
        @Override public EnemyFamily family()        { return EnemyFamily.INSECT; }
        @Override public EnemyRole role()            { return EnemyRole.BRUISER; }
        @Override public float  positionalMultiplier() { return BalanceConfig.POSITIONAL_MULT_MELEE; }
        @Override public int    attackCadenceTurns() { return 1; }
        @Override public char   spawnChar()          { return '4'; }
        @Override public String tacticalVerb()       { return "CHARGER: sidestep its charge, then punish the recovery."; }
    },

    MIRE_WRAITH {
        @Override public int    maxHealth()         { return EnemyConstants.MIRE_WRAITH_MAX_HEALTH; }
        @Override public int    attackDamage()       { return EnemyConstants.MIRE_WRAITH_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()   { return EnemyConstants.MIRE_WRAITH_RANGE_TILES; }
        @Override public int    moveEveryNTurns()    { return EnemyConstants.MIRE_WRAITH_MOVE_EVERY_N_TURNS; }
        @Override public boolean isRanged()          { return true; }
        @Override public float  heightMultiplier()   { return EnemyConstants.MIRE_WRAITH_HEIGHT_MULTIPLIER; }
        @Override public int    baseCreditReward()   { return GameBalance.CREDIT_REWARD_MIRE_WRAITH; }
        @Override public String displayName()        { return "Mire Wraith"; }
        @Override public EnemyFamily family()        { return EnemyFamily.ABERRATION; }
        @Override public EnemyRole role()            { return EnemyRole.SOLDIER; }
        @Override public float  positionalMultiplier() { return BalanceConfig.POSITIONAL_MULT_RANGED; }
        @Override public int    attackCadenceTurns() { return 1; }
        @Override public char   spawnChar()          { return '5'; }
        @Override public String tacticalVerb()       { return "ARTILLERY: prioritise it — don't let its acid DOT stack."; }
        // Ranged SLOWED debuff to pin the player in its firing lane (order-5).
        @Override public SpecialAbility[] moveSet()          { return MOVESET_DEBUFF; }
        @Override public int             specialAbilityCadenceTurns() { return EnemyConstants.SPECIAL_CADENCE_CASTER; }
    },

    IRON_STALKER {
        @Override public int    maxHealth()         { return EnemyConstants.IRON_STALKER_MAX_HEALTH; }
        @Override public int    attackDamage()       { return EnemyConstants.IRON_STALKER_MELEE_DAMAGE; }
        @Override public int    attackRangeTiles()   { return 1; }
        @Override public int    moveEveryNTurns()    { return EnemyConstants.IRON_STALKER_MOVE_EVERY_N_TURNS; }
        @Override public boolean isRanged()          { return false; }
        @Override public float  heightMultiplier()   { return EnemyConstants.IRON_STALKER_HEIGHT_MULTIPLIER; }
        @Override public int    baseCreditReward()   { return GameBalance.CREDIT_REWARD_IRON_STALKER; }
        @Override public String displayName()        { return "Iron Stalker"; }
        @Override public EnemyFamily family()        { return EnemyFamily.INSECT; }
        @Override public EnemyRole role()            { return EnemyRole.MINI_ELITE; }
        @Override public float  positionalMultiplier() { return BalanceConfig.POSITIONAL_MULT_FAST_MELEE; }
        @Override public int    attackCadenceTurns() { return 1; }
        @Override public char   spawnChar()          { return '!'; }
        @Override public String tacticalVerb()       { return "MINI-ELITE: commit resources or avoid — a deliberate mid-floor spike."; }
        // The "solve me" script (order-5): rotates BUFF_SELF (strength) and AREA_STRIKE on a fast cadence.
        @Override public SpecialAbility[] moveSet()          { return MOVESET_ELITE; }
        @Override public int             specialAbilityCadenceTurns() { return EnemyConstants.SPECIAL_CADENCE_ELITE; }
    },

    ACID_DRONE {
        @Override public int    maxHealth()         { return EnemyConstants.ACID_DRONE_MAX_HEALTH; }
        @Override public int    attackDamage()       { return EnemyConstants.ACID_DRONE_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()   { return EnemyConstants.ACID_DRONE_RANGE_TILES; }
        @Override public int    moveEveryNTurns()    { return EnemyConstants.ACID_DRONE_MOVE_EVERY_N_TURNS; }
        @Override public boolean isRanged()          { return true; }
        @Override public float  heightMultiplier()   { return EnemyConstants.ACID_DRONE_HEIGHT_MULTIPLIER; }
        @Override public int    baseCreditReward()   { return GameBalance.CREDIT_REWARD_ACID_DRONE; }
        @Override public String displayName()        { return "Acid Drone"; }
        @Override public EnemyFamily family()        { return EnemyFamily.MACHINE; }
        @Override public EnemyRole role()            { return EnemyRole.SOLDIER; }
        @Override public float  positionalMultiplier() { return BalanceConfig.POSITIONAL_MULT_RANGED; }
        @Override public int    attackCadenceTurns() { return 1; }
        @Override public char   spawnChar()          { return '$'; }
        @Override public String tacticalVerb()       { return "HARASSER: corner it — cut off its kite path with level geometry."; }
        // Readable caster puzzle (order-5): a SLOWED debuff woven between its shots.
        @Override public SpecialAbility[] moveSet()          { return MOVESET_DEBUFF; }
        @Override public int             specialAbilityCadenceTurns() { return EnemyConstants.SPECIAL_CADENCE_CASTER; }
    },

    VOID_SHROUD {
        @Override public int    maxHealth()         { return EnemyConstants.VOID_SHROUD_MAX_HEALTH; }
        @Override public int    attackDamage()       { return EnemyConstants.VOID_SHROUD_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()   { return 1; }
        @Override public int    moveEveryNTurns()    { return EnemyConstants.VOID_SHROUD_MOVE_EVERY_N_TURNS; }
        @Override public boolean isRanged()          { return false; }
        @Override public float  heightMultiplier()   { return EnemyConstants.VOID_SHROUD_HEIGHT_MULTIPLIER; }
        @Override public int    baseCreditReward()   { return GameBalance.CREDIT_REWARD_VOID_SHROUD; }
        @Override public String displayName()        { return "Void Shroud"; }
        @Override public EnemyFamily family()        { return EnemyFamily.DEMON; }
        @Override public EnemyRole role()            { return EnemyRole.SOLDIER; }
        @Override public float  positionalMultiplier() { return BalanceConfig.POSITIONAL_MULT_FAST_MELEE; }
        @Override public int    attackCadenceTurns() { return 1; }
        @Override public char   spawnChar()          { return '^'; }
        @Override public String tacticalVerb()       { return "FLANKER: keep your back covered — rotate to face it before it hits your blind side."; }
    },

    // -------------------------------------------------------------------------
    // Necrotic faction — five archetypes reusing the legacy individual-sprite PNGs
    // (corruptor / vortex_eye / ghoul / crawler / revenant). Distinct stat niches and
    // tactical verbs keep them from duplicating the blight/infernal roster above. They
    // reuse the shared melee/ranged AI in EnemyManager (no bespoke behaviour branch).
    // -------------------------------------------------------------------------

    GHOUL {
        @Override public int    maxHealth()          { return EnemyConstants.GHOUL_MAX_HEALTH; }
        @Override public int    attackDamage()        { return EnemyConstants.GHOUL_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()    { return 1; }
        @Override public int    moveEveryNTurns()     { return EnemyConstants.GHOUL_MOVE_EVERY_N_TURNS; }
        @Override public boolean isRanged()           { return false; }
        @Override public float  heightMultiplier()    { return EnemyConstants.GHOUL_HEIGHT_MULTIPLIER; }
        @Override public int    baseCreditReward()    { return GameBalance.CREDIT_REWARD_GHOUL; }
        @Override public String displayName()         { return "Ghoul"; }
        @Override public EnemyFamily family()         { return EnemyFamily.UNDEAD; }
        @Override public EnemyRole role()             { return EnemyRole.CHAFF; }
        @Override public float  positionalMultiplier() { return BalanceConfig.POSITIONAL_MULT_MELEE; }
        @Override public int    attackCadenceTurns()  { return 1; }
        @Override public char   spawnChar()           { return '~'; }
        @Override public String tacticalVerb()        { return "SHAMBLER: slow but relentless — keep moving and never let it corner you."; }
    },

    CRAWLER {
        @Override public int    maxHealth()          { return EnemyConstants.CRAWLER_MAX_HEALTH; }
        @Override public int    attackDamage()        { return EnemyConstants.CRAWLER_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()    { return 1; }
        @Override public int    moveEveryNTurns()     { return EnemyConstants.CRAWLER_MOVE_EVERY_N_TURNS; }
        @Override public boolean isRanged()           { return false; }
        @Override public float  heightMultiplier()    { return EnemyConstants.CRAWLER_HEIGHT_MULTIPLIER; }
        @Override public int    baseCreditReward()    { return GameBalance.CREDIT_REWARD_CRAWLER; }
        @Override public String displayName()         { return "Crawler"; }
        @Override public EnemyFamily family()         { return EnemyFamily.UNDEAD; }
        @Override public EnemyRole role()             { return EnemyRole.CHAFF; }
        @Override public float  positionalMultiplier() { return BalanceConfig.POSITIONAL_MULT_FAST_MELEE; }
        @Override public int    attackCadenceTurns()  { return 1; }
        @Override public char   spawnChar()           { return 'z'; }
        @Override public String tacticalVerb()        { return "SCUTTLER: fast and fragile — it closes the gap quickly, so drop it before the pack piles in."; }
    },

    REVENANT {
        @Override public int    maxHealth()          { return EnemyConstants.REVENANT_MAX_HEALTH; }
        @Override public int    attackDamage()        { return EnemyConstants.REVENANT_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()    { return 1; }
        @Override public int    moveEveryNTurns()     { return EnemyConstants.REVENANT_MOVE_EVERY_N_TURNS; }
        @Override public boolean isRanged()           { return false; }
        @Override public float  heightMultiplier()    { return EnemyConstants.REVENANT_HEIGHT_MULTIPLIER; }
        @Override public int    baseCreditReward()    { return GameBalance.CREDIT_REWARD_REVENANT; }
        @Override public String displayName()         { return "Revenant"; }
        @Override public EnemyFamily family()         { return EnemyFamily.UNDEAD; }
        // BRUISER, not SOLDIER: its stats (TP 79, golden ratio 2.4) are honestly bruiser-tier — a
        // fast, non-charging bruiser counterpart to the Shell Brute charger. Reclassified in the
        // game-balance-tuning contract pass so its Threat-Point cost is priced correctly by the
        // EncounterBudgetPlanner and it braces on the bruiser cadence. See docs/game-balance-authority.txt.
        @Override public EnemyRole role()             { return EnemyRole.BRUISER; }
        @Override public float  positionalMultiplier() { return BalanceConfig.POSITIONAL_MULT_MELEE; }
        @Override public int    attackCadenceTurns()  { return 1; }
        @Override public char   spawnChar()           { return 'K'; }
        @Override public String tacticalVerb()        { return "REVENANT: a fast, heavy hitter — stagger or kill it before it reaches melee."; }
    },

    VORTEX_EYE {
        @Override public int    maxHealth()          { return EnemyConstants.VORTEX_EYE_MAX_HEALTH; }
        @Override public int    attackDamage()        { return EnemyConstants.VORTEX_EYE_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()    { return EnemyConstants.VORTEX_EYE_RANGE_TILES; }
        @Override public int    moveEveryNTurns()     { return EnemyConstants.VORTEX_EYE_MOVE_EVERY_N_TURNS; }
        @Override public boolean isRanged()           { return true; }
        @Override public float  heightMultiplier()    { return EnemyConstants.VORTEX_EYE_HEIGHT_MULTIPLIER; }
        @Override public int    baseCreditReward()    { return GameBalance.CREDIT_REWARD_VORTEX_EYE; }
        @Override public String displayName()         { return "Vortex Eye"; }
        @Override public EnemyFamily family()         { return EnemyFamily.UNDEAD; }
        @Override public EnemyRole role()             { return EnemyRole.CHAFF; }
        @Override public float  positionalMultiplier() { return BalanceConfig.POSITIONAL_MULT_RANGED; }
        @Override public int    attackCadenceTurns()  { return 1; }
        @Override public char   spawnChar()           { return 'V'; }
        @Override public String tacticalVerb()        { return "EYE: a short-range caster — break its line or close the gap to shut it down."; }
        // Short-range caster (order-5): a BLINDED debuff along its line to soften the player.
        @Override public SpecialAbility[] moveSet()          { return MOVESET_DEBUFF; }
        @Override public int             specialAbilityCadenceTurns() { return EnemyConstants.SPECIAL_CADENCE_CASTER; }
    },

    BLIGHT_CORRUPTOR {
        @Override public int    maxHealth()          { return EnemyConstants.BLIGHT_CORRUPTOR_MAX_HEALTH; }
        @Override public int    attackDamage()        { return EnemyConstants.BLIGHT_CORRUPTOR_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()    { return 1; }
        @Override public int    moveEveryNTurns()     { return EnemyConstants.BLIGHT_CORRUPTOR_MOVE_EVERY_N_TURNS; }
        @Override public boolean isRanged()           { return false; }
        @Override public float  heightMultiplier()    { return EnemyConstants.BLIGHT_CORRUPTOR_HEIGHT_MULTIPLIER; }
        @Override public int    baseCreditReward()    { return GameBalance.CREDIT_REWARD_BLIGHT_CORRUPTOR; }
        @Override public String displayName()         { return "Blight Corruptor"; }
        @Override public EnemyFamily family()         { return EnemyFamily.UNDEAD; }
        @Override public EnemyRole role()             { return EnemyRole.SOLDIER; }
        @Override public float  positionalMultiplier() { return BalanceConfig.POSITIONAL_MULT_MELEE; }
        @Override public int    attackCadenceTurns()  { return 1; }
        @Override public char   spawnChar()           { return '*'; }
        @Override public String tacticalVerb()        { return "CARRIER: a durable infected brute — grind it down from range; don't trade blows."; }
        // Carrier (order-5): summons chaff from its spores — kill it before the room floods.
        @Override public SpecialAbility[] moveSet()          { return MOVESET_SUMMON; }
        @Override public int             specialAbilityCadenceTurns() { return EnemyConstants.SPECIAL_CADENCE_SLOW; }
    },

    // -------------------------------------------------------------------------
    // Boss archetypes — BossFloorController sets actual scaled HP/damage at spawn;
    // values here are used for initial Enemy construction and XP budget.
    // AI is driven by BossAttackPattern, not moveEveryNTurns() / isRanged().
    // -------------------------------------------------------------------------

    OVERSEER {
        @Override public int    maxHealth()         { return BossBalance.statsForDepth(BossBalance.Archetype.OVERSEER, EnemyConstants.OVERSEER_DEPTH).effectiveHitPoints; }
        @Override public int    attackDamage()       { return BossBalance.statsForDepth(BossBalance.Archetype.OVERSEER, EnemyConstants.OVERSEER_DEPTH).verbDamage(EnemyConstants.OVERSEER_LASER_DPT_FRACTION); }
        @Override public int    attackRangeTiles()   { return 8; }
        @Override public int    moveEveryNTurns()    { return 1; }
        @Override public boolean isRanged()          { return true; }
        @Override public float  heightMultiplier()   { return 1.80f; }
        @Override public int    baseXpReward()       { return BossBalance.statsForDepth(BossBalance.Archetype.OVERSEER, EnemyConstants.OVERSEER_DEPTH).xpReward; }
        @Override public int    baseCreditReward()   { return BossBalance.statsForDepth(BossBalance.Archetype.OVERSEER, EnemyConstants.OVERSEER_DEPTH).creditReward; }
        @Override public String displayName()        { return "The Overseer"; }
        @Override public EnemyFamily family()        { return EnemyFamily.MACHINE; }
        @Override public EnemyRole role()            { return EnemyRole.BOSS; }
        @Override public float  positionalMultiplier() { return BalanceConfig.POSITIONAL_MULT_RANGED; }
        @Override public int    attackCadenceTurns() { return 1; }
        @Override public char   spawnChar()          { return 'n'; }
        @Override public String tacticalVerb()       { return "BOSS: learn its attack pattern — telegraphed phases."; }
    },

    CORRUPTOR {
        @Override public int    maxHealth()         { return BossBalance.statsForDepth(BossBalance.Archetype.CORRUPTOR, EnemyConstants.CORRUPTOR_DEPTH).effectiveHitPoints; }
        @Override public int    attackDamage()       { return BossBalance.statsForDepth(BossBalance.Archetype.CORRUPTOR, EnemyConstants.CORRUPTOR_DEPTH).verbDamage(EnemyConstants.CORRUPTOR_ACID_DPT_FRACTION); }
        @Override public int    attackRangeTiles()   { return 6; }
        @Override public int    moveEveryNTurns()    { return 1; }
        @Override public boolean isRanged()          { return true; }
        @Override public float  heightMultiplier()   { return 1.60f; }
        @Override public int    baseXpReward()       { return BossBalance.statsForDepth(BossBalance.Archetype.CORRUPTOR, EnemyConstants.CORRUPTOR_DEPTH).xpReward; }
        @Override public int    baseCreditReward()   { return BossBalance.statsForDepth(BossBalance.Archetype.CORRUPTOR, EnemyConstants.CORRUPTOR_DEPTH).creditReward; }
        @Override public String displayName()        { return "The Corruptor"; }
        @Override public EnemyFamily family()        { return EnemyFamily.ABERRATION; }
        @Override public EnemyRole role()            { return EnemyRole.BOSS; }
        @Override public float  positionalMultiplier() { return BalanceConfig.POSITIONAL_MULT_RANGED; }
        @Override public int    attackCadenceTurns() { return 1; }
        @Override public char   spawnChar()          { return 'n'; }
        @Override public String tacticalVerb()       { return "BOSS: learn its attack pattern — telegraphed phases."; }
    },

    HELL_BARON {
        @Override public int    maxHealth()         { return BossBalance.statsForDepth(BossBalance.Archetype.HELL_BARON, EnemyConstants.HELL_BARON_DEPTH).effectiveHitPoints; }
        @Override public int    attackDamage()       { return BossBalance.statsForDepth(BossBalance.Archetype.HELL_BARON, EnemyConstants.HELL_BARON_DEPTH).verbDamage(EnemyConstants.HELL_BARON_CLEAVE_P1_DPT_FRACTION); }
        @Override public int    attackRangeTiles()   { return 3; }
        @Override public int    moveEveryNTurns()    { return 1; }
        @Override public boolean isRanged()          { return false; }
        @Override public float  heightMultiplier()   { return 2.00f; }
        @Override public int    baseXpReward()       { return BossBalance.statsForDepth(BossBalance.Archetype.HELL_BARON, EnemyConstants.HELL_BARON_DEPTH).xpReward; }
        @Override public int    baseCreditReward()   { return BossBalance.statsForDepth(BossBalance.Archetype.HELL_BARON, EnemyConstants.HELL_BARON_DEPTH).creditReward; }
        @Override public String displayName()        { return "Hell Baron"; }
        @Override public EnemyFamily family()        { return EnemyFamily.DEMON; }
        @Override public EnemyRole role()            { return EnemyRole.BOSS; }
        @Override public float  positionalMultiplier() { return BalanceConfig.POSITIONAL_MULT_MELEE; }
        @Override public int    attackCadenceTurns() { return 1; }
        @Override public char   spawnChar()          { return 'n'; }
        @Override public String tacticalVerb()       { return "BOSS: learn its attack pattern — telegraphed phases."; }
    };

    public abstract int     maxHealth();
    public abstract int     attackDamage();
    public abstract int     attackRangeTiles();
    public abstract int     moveEveryNTurns();
    public abstract boolean isRanged();
    public abstract float   heightMultiplier();
    /**
     * XP awarded to the player when this enemy archetype is killed at dungeon depth 1. DERIVED, not
     * hand-set (new-game-balancr order 4): every non-boss archetype's depth-1 XP is its Threat Points
     * times the single knob XP_PER_THREAT_POINT, so dangerous enemies automatically pay more and a new
     * archetype can never ship with a forgotten XP value. EnemyManager scales this to the enemy's depth
     * via GameMath.xpRewardAtDepth. Bosses OVERRIDE this to keep their flat placeholder reward.
     */
    public int baseXpReward() {
        return Math.round(BalanceConfig.XP_PER_THREAT_POINT * baseThreatPoints());
    }
    /** Base credits awarded at dungeon depth 1; scaled by depth in EnemyManager. */
    public abstract int     baseCreditReward();
    /** Human-readable name shown in HUD name tags (e.g. "Plague Hulk LVL 2") and kill messages. */
    public abstract String  displayName();

    /**
     * The bestiary FAMILY this archetype belongs to — undead, insect, machine, demon, aberration,
     * golem. Orthogonal to {@link #role()}: the family says WHAT the thing is, the role says what job
     * it does in an encounter.
     *
     * <p>DESCRIPTIVE ONLY today — no system reads it, so declaring it changed no behaviour. It is
     * abstract (rather than defaulted) deliberately: a new archetype must state its family at compile
     * time, so the taxonomy can never silently fall out of date. See {@link EnemyFamily} for the
     * family-coherent-floor rule this metadata is being staged for.
     */
    public abstract EnemyFamily family();

    // -------------------------------------------------------------------------
    // Tactical metadata (balance idea 4 — Tactical Combat Depth)
    // role() / positionalMultiplier() / attackCadenceTurns() feed the Threat-Point
    // formula and the encounter-budget planner (EncounterBudgetPlanner). spawnChar()
    // is the level-file/spawn-point character LevelLoader maps back to this type.
    // tacticalVerb() documents the one-sentence reason the player must fight this
    // archetype DIFFERENTLY — the design rule that a new enemy is not approved until
    // it has a distinct verb. See docs/enemy-system.txt.
    // -------------------------------------------------------------------------

    /** The encounter role (chaff / soldier / bruiser / mini-elite / boss) this archetype fills. */
    public abstract EnemyRole role();
    /**
     * Designer positional multiplier for the Threat-Point formula
     * (1.00 melee, 1.15 fast-melee, 1.30 ranged). NOT auto-derived; see
     * GameMath.threatPoints and BalanceConfig.POSITIONAL_MULT_*.
     */
    public abstract float   positionalMultiplier();
    /** Turns between this archetype's attacks (the cadence term of its Threat-Point value). */
    public abstract int     attackCadenceTurns();
    /** Spawn-point character this archetype is created from (LevelLoader / EnemyManager mapping). */
    public abstract char    spawnChar();
    /** One-sentence tactical VERB: the reason the player must fight this archetype differently. */
    public abstract String  tacticalVerb();

    // -------------------------------------------------------------------------
    // Special-ability move-sets (strategy-combat-order-5)
    // moveSet() lists the SPECIAL abilities this archetype can script; specialAbilityCadenceTurns()
    // is the rhythm (enemy turns between specials). Chaff and bosses keep the trivial empty set — not
    // every enemy needs a script. The arrays are shared, pre-allocated singletons so consulting a
    // move-set inside the COMMIT turn loop allocates nothing (project rule). Default = no specials.
    // -------------------------------------------------------------------------

    private static final SpecialAbility[] MOVESET_NONE   = new SpecialAbility[0];
    private static final SpecialAbility[] MOVESET_DEBUFF = { SpecialAbility.DEBUFF_PLAYER };
    private static final SpecialAbility[] MOVESET_AREA   = { SpecialAbility.AREA_STRIKE };
    private static final SpecialAbility[] MOVESET_SUMMON = { SpecialAbility.SUMMON };
    private static final SpecialAbility[] MOVESET_ELITE  = { SpecialAbility.BUFF_SELF, SpecialAbility.AREA_STRIKE };

    /** The SPECIAL abilities this archetype may commit (strategy-combat-order-5). Empty = simple chaff. */
    public SpecialAbility[] moveSet() { return MOVESET_NONE; }

    /**
     * Enemy turns between SPECIAL-ability attempts; 0 disables specials entirely. A special is only
     * considered on turns where {@code turnCounter % cadence == 0}, giving each scripted archetype a
     * readable, learnable rhythm.
     */
    public int specialAbilityCadenceTurns() { return 0; }

    // -------------------------------------------------------------------------
    // Enemy survivability inputs (new-game-balancr order 5, Pillar B) — generalised eHP.
    // Enemy eHP now runs through the SAME GameMath.effectiveHitPoints the player uses, so the
    // survivability model stops ASSUMING enemies never mitigate. No non-boss archetype supplies a
    // non-zero value YET (all default to 0 -> eHP == raw HP, byte-identical to before); Iron Stalker
    // is the designated first user of flat reduction in a later content pass. Overriding any of these
    // re-prices that archetype's TP / TTK / golden ratio for free.
    // -------------------------------------------------------------------------

    /** Extra absorbed-HP pool (player-style armour). Default 0 — no non-boss archetype uses it yet. */
    public float armorPool()     { return 0f; }
    /** Dodge probability applied to eHP as 1/(1-dodge). Default 0 — no non-boss archetype uses it yet. */
    public float dodgeChance()   { return 0f; }
    /** Flat damage shaved off each incoming hit (punishes chip damage). Default 0 — Iron Stalker later. */
    public float flatReduction() { return 0f; }

    /**
     * This archetype's effective HP through the shared survivability primitive (order 5). With the
     * default all-zero mitigation this equals raw {@code maxHealth()}; the flat-reduction term is priced
     * against the player's sustained reference DPT (the same yardstick the TP survivalTurns uses).
     */
    public float effectiveHitPoints() {
        return GameMath.enemyEffectiveHitPoints(maxHealth(), armorPool(), dodgeChance(),
                flatReduction(), BalanceConfig.REFERENCE_PLAYER_DPT);
    }

    /**
     * The control status this archetype's DEBUFF_PLAYER special inflicts (order 5): the eyes BLIND, the
     * acid casters (Mire Wraith / Acid Drone) apply WEAK, everything else SLOWS. Single source of truth —
     * {@code EnemyManager.debuffStatusFor} delegates here so the TP pricing and the live effect can never
     * drift. Archetypes with no DEBUFF_PLAYER verb never call this; SLOWED is the harmless default.
     */
    public StatusType debuffStatusType() {
        switch (this) {
            case EYE_TYRANT:
            case VORTEX_EYE:
                return StatusType.BLINDED;
            case MIRE_WRAITH:
            case ACID_DRONE:
                return StatusType.WEAK;
            default:
                return StatusType.SLOWED;
        }
    }

    /**
     * The chaff type this archetype's SUMMON special spawns (order 5): the Blight Corruptor bleaks
     * corrupted eyes (Vortex Eye), everything else a fast fragile Crawler. Single source of truth —
     * {@code EnemyManager.summonTypeFor} delegates here so the summon TP pricing matches what spawns.
     */
    public EnemyType summonType() {
        switch (this) {
            case BLIGHT_CORRUPTOR:
                return VORTEX_EYE;
            default:
                return CRAWLER;
        }
    }

    /**
     * This archetype's depth-1 Threat-Point value — the "danger number" the encounter budget spends.
     *
     * <p>CYCLE-AVERAGED (new-game-balancr order 5): the DPT is blended over the archetype's special-ability
     * cadence cycle (GameMath.cycleAveragedDamagePerTurn) so casters, buffers and summoners are priced by
     * their EFFECTIVE output, not just their stat block. Per-verb equivalences are pure GameMath
     * conversions (specialEquivalent*): AREA_STRIKE = predicted slam damage, BUFF_SELF = extra damage the
     * EMPOWERED buff adds over its life, DEBUFF = the player output it steals priced at DEBUFF_TP_EQUIVALENCE,
     * SUMMON = the amortised threat of the bodies it adds. One special fires per cadence cycle (rotating
     * move-set), so the non-summon verbs are AVERAGED into the per-cast damage while SUMMON adds a separate
     * amortised per-turn term (it adds bodies, not caster output). eHP runs through the shared survivability
     * primitive (order 5, Pillar B). Scale to a given floor with GameMath.enemyThreatAtDepth.
     */
    public float baseThreatPoints() {
        float survivalTurns = effectiveHitPoints() / BalanceConfig.REFERENCE_PLAYER_DPT;
        return cycleAveragedDamagePerTurn() * survivalTurns * positionalMultiplier();
    }

    /**
     * This archetype's TRUE effective damage-per-turn (order 5), blended over its special-ability cadence
     * cycle: basic attacks plus the per-cast equivalent of its scripted specials (AREA_STRIKE / BUFF_SELF /
     * DEBUFF averaged one-per-cycle) plus a summon's amortised per-turn threat. An enemy with no move-set
     * returns its plain stat-block DPT (attackDamage / cadence). This is the numerator {@link
     * #baseThreatPoints()} multiplies by survival turns and the positional multiplier; BalanceReport prints
     * it alongside the resulting Threat Points.
     */
    public float cycleAveragedDamagePerTurn() {
        float survivalTurns = effectiveHitPoints() / BalanceConfig.REFERENCE_PLAYER_DPT;
        float basicDamagePerTurn = (float) attackDamage() / Math.max(1, attackCadenceTurns());

        SpecialAbility[] moveSet = moveSet();
        int specialCadenceTurns = specialAbilityCadenceTurns();

        // Non-summon verbs (AREA_STRIKE / BUFF_SELF / DEBUFF) rotate one-per-cycle, so their per-cast
        // equivalent damage is averaged; SUMMON adds a separate amortised per-turn term (whole extra
        // bodies, not caster output). An empty move-set or zero cadence yields the plain stat-block DPT.
        float perCastSpecialDamageSum = 0f;
        int   perCastSpecialCount     = 0;
        float summonDamagePerTurn     = 0f;
        for (SpecialAbility ability : moveSet) {
            if (ability == SpecialAbility.SUMMON) {
                summonDamagePerTurn += GameMath.specialEquivalentSummon(
                        summonType().baseThreatPoints(),
                        BalanceConfig.SUMMON_EXPECTED_PER_FIGHT, survivalTurns);
            } else {
                float perCast = specialEquivalentDamageOf(ability, basicDamagePerTurn);
                if (perCast > 0f) {
                    perCastSpecialDamageSum += perCast;
                    perCastSpecialCount++;
                }
            }
        }
        // A special of ANY kind occupies its cast turn (displacing a basic attack), so the cycle
        // displacement applies whenever the move-set is non-empty — including a lone summoner, whose
        // single cast lands on its cadence turn. Non-summon verbs supply the averaged per-cast damage;
        // the summon adds its amortised per-turn threat on top (whole extra bodies, not caster output).
        float perCastSpecialDamage = perCastSpecialCount > 0
                ? perCastSpecialDamageSum / perCastSpecialCount : 0f;
        int cycleTurns = moveSet.length > 0 ? specialCadenceTurns : 0;
        return GameMath.cycleAveragedDamagePerTurn(
                basicDamagePerTurn, perCastSpecialDamage, summonDamagePerTurn, cycleTurns);
    }

    /**
     * Per-cast equivalent DAMAGE of a non-summon special verb (order 5), routed to its pure GameMath
     * pricing formula. SUMMON is priced separately (amortised per-turn) in {@link #baseThreatPoints()};
     * SELF_DESTRUCT is a death-triggered finisher governed by the telegraph rule, not sustained DPT, so it
     * prices at zero here. Every catalogued verb is covered — {@code BalanceSchema}'s COVERAGE rule fails
     * the build if a new verb reaches the default branch with no registered equivalence.
     */
    private float specialEquivalentDamageOf(SpecialAbility ability, float basicDamagePerTurn) {
        switch (ability) {
            case AREA_STRIKE:
                return GameMath.specialEquivalentAreaStrike(attackDamage(),
                        EnemyConstants.AREA_STRIKE_DAMAGE_MULTIPLIER);
            case BUFF_SELF:
                return GameMath.specialEquivalentBuffSelf(basicDamagePerTurn,
                        EnemyConstants.BUFF_SELF_EMPOWERED_PERCENT, EnemyConstants.BUFF_SELF_DURATION_TURNS);
            case DEBUFF_PLAYER:
                return debuffEquivalentDamage(basicDamagePerTurn);
            case SUMMON:         // priced as amortised per-turn threat in baseThreatPoints()
            case SELF_DESTRUCT:  // death-triggered finisher — priced by the telegraph rule, not cycle DPT
            default:
                return 0f;
        }
    }

    /** Per-cast equivalent damage of this archetype's DEBUFF_PLAYER, resolved by the control status it applies. */
    private float debuffEquivalentDamage(float basicDamagePerTurn) {
        switch (debuffStatusType()) {
            case WEAK:
                return GameMath.specialEquivalentDebuffWeak(BalanceConfig.REFERENCE_PLAYER_DPT,
                        BalanceConfig.WEAK_DAMAGE_PERCENT, EnemyConstants.DEBUFF_WEAK_DURATION_TURNS,
                        BalanceConfig.DEBUFF_TP_EQUIVALENCE);
            case BLINDED:
                return GameMath.specialEquivalentDebuffControl(basicDamagePerTurn,
                        EnemyConstants.DEBUFF_BLIND_DURATION_TURNS, BalanceConfig.DEBUFF_TP_EQUIVALENCE);
            case SLOWED:
            default: {
                float bonusEnemyTurns = (BalanceConfig.SLOW_FACTOR - 1f)
                        * EnemyConstants.DEBUFF_SLOW_DURATION_TURNS;
                return GameMath.specialEquivalentDebuffControl(basicDamagePerTurn, bonusEnemyTurns,
                        BalanceConfig.DEBUFF_TP_EQUIVALENCE);
            }
        }
    }
}
