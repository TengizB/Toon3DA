package ge.tbegvadze.toon3d.enemy;

import ge.tbegvadze.toon3d.util.EnemyConstants;
import ge.tbegvadze.toon3d.util.GameBalance;

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
        @Override public String displayName()        { return "Plague Hulk"; }
    },

    EYE_TYRANT {
        @Override public int    maxHealth()         { return EnemyConstants.EYE_TYRANT_MAX_HEALTH; }
        @Override public int    attackDamage()       { return EnemyConstants.EYE_TYRANT_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()   { return EnemyConstants.EYE_TYRANT_RANGE_TILES; }
        @Override public int    moveEveryNTurns()    { return 1; }
        @Override public boolean isRanged()          { return true; }
        @Override public float  heightMultiplier()   { return EnemyConstants.EYE_TYRANT_HEIGHT_MULTIPLIER; }
        @Override public int    baseXpReward()       { return GameBalance.XP_REWARD_EYE_TYRANT; }
        @Override public String displayName()        { return "Eye Tyrant"; }
    },

    GORE_BITER {
        @Override public int    maxHealth()         { return EnemyConstants.GORE_BITER_MAX_HEALTH; }
        @Override public int    attackDamage()       { return EnemyConstants.GORE_BITER_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()   { return 1; }
        @Override public int    moveEveryNTurns()    { return EnemyConstants.GORE_BITER_MOVE_EVERY_N_TURNS; }
        @Override public boolean isRanged()          { return false; }
        @Override public float  heightMultiplier()   { return EnemyConstants.GORE_BITER_HEIGHT_MULTIPLIER; }
        @Override public int    baseXpReward()       { return GameBalance.XP_REWARD_GORE_BITER; }
        @Override public String displayName()        { return "Gore Biter"; }
    },

    SHELL_BRUTE {
        @Override public int    maxHealth()         { return EnemyConstants.SHELL_BRUTE_MAX_HEALTH; }
        @Override public int    attackDamage()       { return EnemyConstants.SHELL_BRUTE_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()   { return 1; }
        @Override public int    moveEveryNTurns()    { return EnemyConstants.SHELL_BRUTE_MOVE_EVERY_N_TURNS; }
        @Override public boolean isRanged()          { return false; }
        @Override public float  heightMultiplier()   { return EnemyConstants.SHELL_BRUTE_HEIGHT_MULTIPLIER; }
        @Override public int    baseXpReward()       { return GameBalance.XP_REWARD_SHELL_BRUTE; }
        @Override public String displayName()        { return "Shell Brute"; }
    },

    MIRE_WRAITH {
        @Override public int    maxHealth()         { return EnemyConstants.MIRE_WRAITH_MAX_HEALTH; }
        @Override public int    attackDamage()       { return EnemyConstants.MIRE_WRAITH_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()   { return EnemyConstants.MIRE_WRAITH_RANGE_TILES; }
        @Override public int    moveEveryNTurns()    { return EnemyConstants.MIRE_WRAITH_MOVE_EVERY_N_TURNS; }
        @Override public boolean isRanged()          { return true; }
        @Override public float  heightMultiplier()   { return EnemyConstants.MIRE_WRAITH_HEIGHT_MULTIPLIER; }
        @Override public int    baseXpReward()       { return GameBalance.XP_REWARD_MIRE_WRAITH; }
        @Override public String displayName()        { return "Mire Wraith"; }
    },

    IRON_STALKER {
        @Override public int    maxHealth()         { return EnemyConstants.IRON_STALKER_MAX_HEALTH; }
        @Override public int    attackDamage()       { return EnemyConstants.IRON_STALKER_MELEE_DAMAGE; }
        @Override public int    attackRangeTiles()   { return 1; }
        @Override public int    moveEveryNTurns()    { return EnemyConstants.IRON_STALKER_MOVE_EVERY_N_TURNS; }
        @Override public boolean isRanged()          { return false; }
        @Override public float  heightMultiplier()   { return EnemyConstants.IRON_STALKER_HEIGHT_MULTIPLIER; }
        @Override public int    baseXpReward()       { return GameBalance.XP_REWARD_IRON_STALKER; }
        @Override public String displayName()        { return "Iron Stalker"; }
    },

    ACID_DRONE {
        @Override public int    maxHealth()         { return EnemyConstants.ACID_DRONE_MAX_HEALTH; }
        @Override public int    attackDamage()       { return EnemyConstants.ACID_DRONE_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()   { return EnemyConstants.ACID_DRONE_RANGE_TILES; }
        @Override public int    moveEveryNTurns()    { return EnemyConstants.ACID_DRONE_MOVE_EVERY_N_TURNS; }
        @Override public boolean isRanged()          { return true; }
        @Override public float  heightMultiplier()   { return EnemyConstants.ACID_DRONE_HEIGHT_MULTIPLIER; }
        @Override public int    baseXpReward()       { return GameBalance.XP_REWARD_ACID_DRONE; }
        @Override public String displayName()        { return "Acid Drone"; }
    },

    VOID_SHROUD {
        @Override public int    maxHealth()         { return EnemyConstants.VOID_SHROUD_MAX_HEALTH; }
        @Override public int    attackDamage()       { return EnemyConstants.VOID_SHROUD_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()   { return 1; }
        @Override public int    moveEveryNTurns()    { return EnemyConstants.VOID_SHROUD_MOVE_EVERY_N_TURNS; }
        @Override public boolean isRanged()          { return false; }
        @Override public float  heightMultiplier()   { return EnemyConstants.VOID_SHROUD_HEIGHT_MULTIPLIER; }
        @Override public int    baseXpReward()       { return GameBalance.XP_REWARD_VOID_SHROUD; }
        @Override public String displayName()        { return "Void Shroud"; }
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
        @Override public String displayName()        { return "The Overseer"; }
    },

    CORRUPTOR {
        @Override public int    maxHealth()         { return EnemyConstants.CORRUPTOR_MAX_HP; }
        @Override public int    attackDamage()       { return EnemyConstants.CORRUPTOR_ACID_DAMAGE; }
        @Override public int    attackRangeTiles()   { return 6; }
        @Override public int    moveEveryNTurns()    { return 1; }
        @Override public boolean isRanged()          { return true; }
        @Override public float  heightMultiplier()   { return 1.60f; }
        @Override public int    baseXpReward()       { return GameBalance.XP_REWARD_BOSS_BASE; }
        @Override public String displayName()        { return "The Corruptor"; }
    },

    HELL_BARON {
        @Override public int    maxHealth()         { return EnemyConstants.HELL_BARON_MAX_HP; }
        @Override public int    attackDamage()       { return EnemyConstants.HELL_BARON_CLEAVE_DAMAGE_P1; }
        @Override public int    attackRangeTiles()   { return 3; }
        @Override public int    moveEveryNTurns()    { return 1; }
        @Override public boolean isRanged()          { return false; }
        @Override public float  heightMultiplier()   { return 2.00f; }
        @Override public int    baseXpReward()       { return GameBalance.XP_REWARD_BOSS_BASE; }
        @Override public String displayName()        { return "Hell Baron"; }
    };

    public abstract int     maxHealth();
    public abstract int     attackDamage();
    public abstract int     attackRangeTiles();
    public abstract int     moveEveryNTurns();
    public abstract boolean isRanged();
    public abstract float   heightMultiplier();
    /** XP awarded to the player when this enemy archetype is killed at dungeon depth 1. */
    public abstract int     baseXpReward();
    /** Human-readable name shown in HUD name tags (e.g. "Plague Hulk LVL 2") and kill messages. */
    public abstract String  displayName();
}
