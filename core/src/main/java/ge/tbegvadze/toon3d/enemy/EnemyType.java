package ge.tbegvadze.toon3d.enemy;

import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameBalance;

/** Per-type configuration for each enemy archetype. Values drawn from Constants and GameBalance. */
public enum EnemyType {

    CORRUPTOR {
        @Override public int    maxHealth()         { return Constants.CORRUPTOR_MAX_HEALTH; }
        @Override public int    attackDamage()       { return Constants.CORRUPTOR_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()   { return 1; }
        @Override public int    moveEveryNTurns()    { return Constants.CORRUPTOR_MOVE_EVERY_N_TURNS; }
        @Override public boolean isRanged()          { return false; }
        @Override public float  heightMultiplier()   { return Constants.CORRUPTOR_HEIGHT_MULTIPLIER; }
        @Override public String texturePath()        { return Constants.ENEMY_CORRUPTOR_PATH; }
        @Override public int    baseXpReward()       { return GameBalance.XP_REWARD_CORRUPTOR; }
    },

    VORTEX_EYE {
        @Override public int    maxHealth()         { return Constants.VORTEX_EYE_MAX_HEALTH; }
        @Override public int    attackDamage()       { return Constants.VORTEX_EYE_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()   { return Constants.VORTEX_EYE_RANGE_TILES; }
        @Override public int    moveEveryNTurns()    { return 1; }
        @Override public boolean isRanged()          { return true; }
        @Override public float  heightMultiplier()   { return Constants.VORTEX_EYE_HEIGHT_MULTIPLIER; }
        @Override public String texturePath()        { return Constants.ENEMY_VORTEX_EYE_PATH; }
        @Override public int    baseXpReward()       { return GameBalance.XP_REWARD_VORTEX_EYE; }
    },

    GHOUL {
        @Override public int    maxHealth()         { return Constants.LIGHT_MELEE_MAX_HEALTH; }
        @Override public int    attackDamage()       { return Constants.LIGHT_MELEE_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()   { return 1; }
        @Override public int    moveEveryNTurns()    { return Constants.LIGHT_MELEE_MOVE_EVERY_N_TURNS; }
        @Override public boolean isRanged()          { return false; }
        @Override public float  heightMultiplier()   { return Constants.LIGHT_MELEE_HEIGHT_MULTIPLIER; }
        @Override public String texturePath()        { return Constants.ENEMY_GHOUL_PATH; }
        @Override public int    baseXpReward()       { return GameBalance.XP_REWARD_GHOUL; }
    },

    CRAWLER {
        @Override public int    maxHealth()         { return Constants.LIGHT_MELEE_MAX_HEALTH; }
        @Override public int    attackDamage()       { return Constants.LIGHT_MELEE_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()   { return 1; }
        @Override public int    moveEveryNTurns()    { return Constants.LIGHT_MELEE_MOVE_EVERY_N_TURNS; }
        @Override public boolean isRanged()          { return false; }
        @Override public float  heightMultiplier()   { return Constants.LIGHT_MELEE_HEIGHT_MULTIPLIER; }
        @Override public String texturePath()        { return Constants.ENEMY_CRAWLER_PATH; }
        @Override public int    baseXpReward()       { return GameBalance.XP_REWARD_CRAWLER; }
    },

    REVENANT {
        @Override public int    maxHealth()         { return Constants.LIGHT_MELEE_MAX_HEALTH; }
        @Override public int    attackDamage()       { return Constants.LIGHT_MELEE_ATTACK_DAMAGE; }
        @Override public int    attackRangeTiles()   { return 1; }
        @Override public int    moveEveryNTurns()    { return Constants.LIGHT_MELEE_MOVE_EVERY_N_TURNS; }
        @Override public boolean isRanged()          { return false; }
        @Override public float  heightMultiplier()   { return Constants.LIGHT_MELEE_HEIGHT_MULTIPLIER; }
        @Override public String texturePath()        { return Constants.ENEMY_REVENANT_PATH; }
        @Override public int    baseXpReward()       { return GameBalance.XP_REWARD_REVENANT; }
    };

    public abstract int     maxHealth();
    public abstract int     attackDamage();
    public abstract int     attackRangeTiles();
    public abstract int     moveEveryNTurns();
    public abstract boolean isRanged();
    public abstract float   heightMultiplier();
    public abstract String  texturePath();
    /** XP awarded to the player when this enemy archetype is killed at dungeon depth 1. */
    public abstract int     baseXpReward();
}
