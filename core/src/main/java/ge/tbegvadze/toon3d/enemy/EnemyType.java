package ge.tbegvadze.toon3d.enemy;

import ge.tbegvadze.toon3d.util.BalanceConfig;
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
        @Override public int    baseXpReward()       { return GameBalance.XP_REWARD_PLAGUE_HULK; }
        @Override public int    baseCreditReward()   { return GameBalance.CREDIT_REWARD_PLAGUE_HULK; }
        @Override public String displayName()        { return "Plague Hulk"; }
        @Override public EnemyRole role()            { return EnemyRole.SOLDIER; }
        @Override public float  positionalMultiplier() { return BalanceConfig.POSITIONAL_MULT_MELEE; }
        @Override public int    attackCadenceTurns() { return EnemyConstants.PLAGUE_HULK_MOVE_EVERY_N_TURNS; }
        @Override public char   spawnChar()          { return '1'; }
        @Override public String tacticalVerb()       { return "TANK: kill at range — don't grind it down in melee."; }
    },

    EYE_TYRANT {
        @Override public int    maxHealth()         { return EnemyConstants.EYE_TYRANT_MAX_HEALTH; }
        @Override public int    attackDamage()       { return EnemyConstants.EYE_TYRANT_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()   { return EnemyConstants.EYE_TYRANT_RANGE_TILES; }
        @Override public int    moveEveryNTurns()    { return 1; }
        @Override public boolean isRanged()          { return true; }
        @Override public float  heightMultiplier()   { return EnemyConstants.EYE_TYRANT_HEIGHT_MULTIPLIER; }
        @Override public int    baseXpReward()       { return GameBalance.XP_REWARD_EYE_TYRANT; }
        @Override public int    baseCreditReward()   { return GameBalance.CREDIT_REWARD_EYE_TYRANT; }
        @Override public String displayName()        { return "Eye Tyrant"; }
        @Override public EnemyRole role()            { return EnemyRole.CHAFF; }
        @Override public float  positionalMultiplier() { return BalanceConfig.POSITIONAL_MULT_RANGED; }
        @Override public int    attackCadenceTurns() { return 1; }
        @Override public char   spawnChar()          { return '2'; }
        @Override public String tacticalVerb()       { return "SNIPER: break its line — step off its row or column to deny the shot."; }
    },

    GORE_BITER {
        @Override public int    maxHealth()         { return EnemyConstants.GORE_BITER_MAX_HEALTH; }
        @Override public int    attackDamage()       { return EnemyConstants.GORE_BITER_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()   { return 1; }
        @Override public int    moveEveryNTurns()    { return EnemyConstants.GORE_BITER_MOVE_EVERY_N_TURNS; }
        @Override public boolean isRanged()          { return false; }
        @Override public float  heightMultiplier()   { return EnemyConstants.GORE_BITER_HEIGHT_MULTIPLIER; }
        @Override public int    baseXpReward()       { return GameBalance.XP_REWARD_GORE_BITER; }
        @Override public int    baseCreditReward()   { return GameBalance.CREDIT_REWARD_GORE_BITER; }
        @Override public String displayName()        { return "Gore Biter"; }
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
        @Override public int    baseXpReward()       { return GameBalance.XP_REWARD_SHELL_BRUTE; }
        @Override public int    baseCreditReward()   { return GameBalance.CREDIT_REWARD_SHELL_BRUTE; }
        @Override public String displayName()        { return "Shell Brute"; }
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
        @Override public int    baseXpReward()       { return GameBalance.XP_REWARD_MIRE_WRAITH; }
        @Override public int    baseCreditReward()   { return GameBalance.CREDIT_REWARD_MIRE_WRAITH; }
        @Override public String displayName()        { return "Mire Wraith"; }
        @Override public EnemyRole role()            { return EnemyRole.SOLDIER; }
        @Override public float  positionalMultiplier() { return BalanceConfig.POSITIONAL_MULT_RANGED; }
        @Override public int    attackCadenceTurns() { return 1; }
        @Override public char   spawnChar()          { return '5'; }
        @Override public String tacticalVerb()       { return "ARTILLERY: prioritise it — don't let its acid DOT stack."; }
    },

    IRON_STALKER {
        @Override public int    maxHealth()         { return EnemyConstants.IRON_STALKER_MAX_HEALTH; }
        @Override public int    attackDamage()       { return EnemyConstants.IRON_STALKER_MELEE_DAMAGE; }
        @Override public int    attackRangeTiles()   { return 1; }
        @Override public int    moveEveryNTurns()    { return EnemyConstants.IRON_STALKER_MOVE_EVERY_N_TURNS; }
        @Override public boolean isRanged()          { return false; }
        @Override public float  heightMultiplier()   { return EnemyConstants.IRON_STALKER_HEIGHT_MULTIPLIER; }
        @Override public int    baseXpReward()       { return GameBalance.XP_REWARD_IRON_STALKER; }
        @Override public int    baseCreditReward()   { return GameBalance.CREDIT_REWARD_IRON_STALKER; }
        @Override public String displayName()        { return "Iron Stalker"; }
        @Override public EnemyRole role()            { return EnemyRole.MINI_ELITE; }
        @Override public float  positionalMultiplier() { return BalanceConfig.POSITIONAL_MULT_FAST_MELEE; }
        @Override public int    attackCadenceTurns() { return 1; }
        @Override public char   spawnChar()          { return '!'; }
        @Override public String tacticalVerb()       { return "MINI-ELITE: commit resources or avoid — a deliberate mid-floor spike."; }
    },

    ACID_DRONE {
        @Override public int    maxHealth()         { return EnemyConstants.ACID_DRONE_MAX_HEALTH; }
        @Override public int    attackDamage()       { return EnemyConstants.ACID_DRONE_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()   { return EnemyConstants.ACID_DRONE_RANGE_TILES; }
        @Override public int    moveEveryNTurns()    { return EnemyConstants.ACID_DRONE_MOVE_EVERY_N_TURNS; }
        @Override public boolean isRanged()          { return true; }
        @Override public float  heightMultiplier()   { return EnemyConstants.ACID_DRONE_HEIGHT_MULTIPLIER; }
        @Override public int    baseXpReward()       { return GameBalance.XP_REWARD_ACID_DRONE; }
        @Override public int    baseCreditReward()   { return GameBalance.CREDIT_REWARD_ACID_DRONE; }
        @Override public String displayName()        { return "Acid Drone"; }
        @Override public EnemyRole role()            { return EnemyRole.SOLDIER; }
        @Override public float  positionalMultiplier() { return BalanceConfig.POSITIONAL_MULT_RANGED; }
        @Override public int    attackCadenceTurns() { return 1; }
        @Override public char   spawnChar()          { return '$'; }
        @Override public String tacticalVerb()       { return "HARASSER: corner it — cut off its kite path with level geometry."; }
    },

    VOID_SHROUD {
        @Override public int    maxHealth()         { return EnemyConstants.VOID_SHROUD_MAX_HEALTH; }
        @Override public int    attackDamage()       { return EnemyConstants.VOID_SHROUD_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()   { return 1; }
        @Override public int    moveEveryNTurns()    { return EnemyConstants.VOID_SHROUD_MOVE_EVERY_N_TURNS; }
        @Override public boolean isRanged()          { return false; }
        @Override public float  heightMultiplier()   { return EnemyConstants.VOID_SHROUD_HEIGHT_MULTIPLIER; }
        @Override public int    baseXpReward()       { return GameBalance.XP_REWARD_VOID_SHROUD; }
        @Override public int    baseCreditReward()   { return GameBalance.CREDIT_REWARD_VOID_SHROUD; }
        @Override public String displayName()        { return "Void Shroud"; }
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
        @Override public int    baseXpReward()        { return GameBalance.XP_REWARD_GHOUL; }
        @Override public int    baseCreditReward()    { return GameBalance.CREDIT_REWARD_GHOUL; }
        @Override public String displayName()         { return "Ghoul"; }
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
        @Override public int    baseXpReward()        { return GameBalance.XP_REWARD_CRAWLER; }
        @Override public int    baseCreditReward()    { return GameBalance.CREDIT_REWARD_CRAWLER; }
        @Override public String displayName()         { return "Crawler"; }
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
        @Override public int    baseXpReward()        { return GameBalance.XP_REWARD_REVENANT; }
        @Override public int    baseCreditReward()    { return GameBalance.CREDIT_REWARD_REVENANT; }
        @Override public String displayName()         { return "Revenant"; }
        @Override public EnemyRole role()             { return EnemyRole.SOLDIER; }
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
        @Override public int    baseXpReward()        { return GameBalance.XP_REWARD_VORTEX_EYE; }
        @Override public int    baseCreditReward()    { return GameBalance.CREDIT_REWARD_VORTEX_EYE; }
        @Override public String displayName()         { return "Vortex Eye"; }
        @Override public EnemyRole role()             { return EnemyRole.CHAFF; }
        @Override public float  positionalMultiplier() { return BalanceConfig.POSITIONAL_MULT_RANGED; }
        @Override public int    attackCadenceTurns()  { return 1; }
        @Override public char   spawnChar()           { return 'V'; }
        @Override public String tacticalVerb()        { return "EYE: a short-range caster — break its line or close the gap to shut it down."; }
    },

    BLIGHT_CORRUPTOR {
        @Override public int    maxHealth()          { return EnemyConstants.BLIGHT_CORRUPTOR_MAX_HEALTH; }
        @Override public int    attackDamage()        { return EnemyConstants.BLIGHT_CORRUPTOR_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()    { return 1; }
        @Override public int    moveEveryNTurns()     { return EnemyConstants.BLIGHT_CORRUPTOR_MOVE_EVERY_N_TURNS; }
        @Override public boolean isRanged()           { return false; }
        @Override public float  heightMultiplier()    { return EnemyConstants.BLIGHT_CORRUPTOR_HEIGHT_MULTIPLIER; }
        @Override public int    baseXpReward()        { return GameBalance.XP_REWARD_BLIGHT_CORRUPTOR; }
        @Override public int    baseCreditReward()    { return GameBalance.CREDIT_REWARD_BLIGHT_CORRUPTOR; }
        @Override public String displayName()         { return "Blight Corruptor"; }
        @Override public EnemyRole role()             { return EnemyRole.SOLDIER; }
        @Override public float  positionalMultiplier() { return BalanceConfig.POSITIONAL_MULT_MELEE; }
        @Override public int    attackCadenceTurns()  { return 1; }
        @Override public char   spawnChar()           { return '*'; }
        @Override public String tacticalVerb()        { return "CARRIER: a durable infected brute — grind it down from range; don't trade blows."; }
    },

    // -------------------------------------------------------------------------
    // Boss archetypes — BossFloorController sets actual scaled HP/damage at spawn;
    // values here are used for initial Enemy construction and XP budget.
    // AI is driven by BossAttackPattern, not moveEveryNTurns() / isRanged().
    // -------------------------------------------------------------------------

    OVERSEER {
        @Override public int    maxHealth()         { return EnemyConstants.OVERSEER_MAX_HP; }
        @Override public int    attackDamage()       { return EnemyConstants.OVERSEER_LASER_DAMAGE; }
        @Override public int    attackRangeTiles()   { return 8; }
        @Override public int    moveEveryNTurns()    { return 1; }
        @Override public boolean isRanged()          { return true; }
        @Override public float  heightMultiplier()   { return 1.80f; }
        @Override public int    baseXpReward()       { return GameBalance.XP_REWARD_BOSS_BASE; }
        @Override public int    baseCreditReward()   { return GameBalance.CREDIT_REWARD_BOSS_BASE; }
        @Override public String displayName()        { return "The Overseer"; }
        @Override public EnemyRole role()            { return EnemyRole.BOSS; }
        @Override public float  positionalMultiplier() { return BalanceConfig.POSITIONAL_MULT_RANGED; }
        @Override public int    attackCadenceTurns() { return 1; }
        @Override public char   spawnChar()          { return 'n'; }
        @Override public String tacticalVerb()       { return "BOSS: learn its attack pattern — telegraphed phases."; }
    },

    CORRUPTOR {
        @Override public int    maxHealth()         { return EnemyConstants.CORRUPTOR_MAX_HP; }
        @Override public int    attackDamage()       { return EnemyConstants.CORRUPTOR_ACID_DAMAGE; }
        @Override public int    attackRangeTiles()   { return 6; }
        @Override public int    moveEveryNTurns()    { return 1; }
        @Override public boolean isRanged()          { return true; }
        @Override public float  heightMultiplier()   { return 1.60f; }
        @Override public int    baseXpReward()       { return GameBalance.XP_REWARD_BOSS_BASE; }
        @Override public int    baseCreditReward()   { return GameBalance.CREDIT_REWARD_BOSS_BASE; }
        @Override public String displayName()        { return "The Corruptor"; }
        @Override public EnemyRole role()            { return EnemyRole.BOSS; }
        @Override public float  positionalMultiplier() { return BalanceConfig.POSITIONAL_MULT_RANGED; }
        @Override public int    attackCadenceTurns() { return 1; }
        @Override public char   spawnChar()          { return 'n'; }
        @Override public String tacticalVerb()       { return "BOSS: learn its attack pattern — telegraphed phases."; }
    },

    HELL_BARON {
        @Override public int    maxHealth()         { return EnemyConstants.HELL_BARON_MAX_HP; }
        @Override public int    attackDamage()       { return EnemyConstants.HELL_BARON_CLEAVE_DAMAGE_P1; }
        @Override public int    attackRangeTiles()   { return 3; }
        @Override public int    moveEveryNTurns()    { return 1; }
        @Override public boolean isRanged()          { return false; }
        @Override public float  heightMultiplier()   { return 2.00f; }
        @Override public int    baseXpReward()       { return GameBalance.XP_REWARD_BOSS_BASE; }
        @Override public int    baseCreditReward()   { return GameBalance.CREDIT_REWARD_BOSS_BASE; }
        @Override public String displayName()        { return "Hell Baron"; }
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
    /** XP awarded to the player when this enemy archetype is killed at dungeon depth 1. */
    public abstract int     baseXpReward();
    /** Base credits awarded at dungeon depth 1; scaled by depth in EnemyManager. */
    public abstract int     baseCreditReward();
    /** Human-readable name shown in HUD name tags (e.g. "Plague Hulk LVL 2") and kill messages. */
    public abstract String  displayName();

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

    /**
     * This archetype's depth-1 Threat-Point value — the "danger number" the encounter budget
     * spends. Computed from its own stats via the balance contract's Threat-Point primitive
     * (GameMath.threatPoints). Enemies carry no armour/dodge, so effective HP == raw HP.
     * Scale to a given floor with GameMath.enemyThreatAtDepth.
     */
    public float baseThreatPoints() {
        return GameMath.threatPoints(attackDamage(), attackCadenceTurns(),
                maxHealth(), BalanceConfig.REFERENCE_PLAYER_DPT, positionalMultiplier());
    }
}
