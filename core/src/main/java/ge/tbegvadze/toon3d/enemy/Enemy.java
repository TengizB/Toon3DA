package ge.tbegvadze.toon3d.enemy;

import ge.tbegvadze.toon3d.status.StatusEffect;
import ge.tbegvadze.toon3d.status.StatusHost;
import ge.tbegvadze.toon3d.status.StatusResistance;
import ge.tbegvadze.toon3d.status.StatusType;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.EffectConstants;
import ge.tbegvadze.toon3d.util.EnemyConstants;

import java.util.EnumMap;

/** Runtime state for a single enemy instance. Pure data + lightweight behavior helpers. */
public class Enemy implements StatusHost {

    // Stable integer identity for ability tracking (e.g. Rhythm target ID).
    // Incrementing at construction time guarantees uniqueness within a JVM session.
    private static int nextId = 0;
    private final  int id     = nextId++;

    /** Returns the stable identity integer for this enemy instance. Used by Rhythm ability. */
    public int getId() { return id; }

    /** Resets the ID counter; call when starting a completely new game run. */
    public static void resetIdCounter() { nextId = 0; }

    public final EnemyType type;

    /** Authoritative tile position (integer grid coordinates). */
    public int tileColumn;
    public int tileRow;

    public int        health;
    /** Effective max HP for this instance — may exceed type.maxHealth() on deeper floors. */
    public int        maxHealth;
    /**
     * Flat damage reduction from physical armour (0 = unarmoured).
     * Used by GameMath.armorPierceDamage() when a weapon has the ARMOR_PIERCE ability.
     * Default 0; depth-scaled enemies may receive a non-zero value at spawn time.
     */
    public int        armor = 0;
    /**
     * Depth-scaling multiplier applied to type.attackDamage() on each turn.
     * Set to {@code GameBalance.enemyDamageScaleForDepth(depth)} at spawn time.
     * Floor 1 = 1.0 (no scaling).
     */
    public float      attackDamageMultiplier = 1f;
    public EnemyState state              = EnemyState.DORMANT;
    public int        turnCounter        = 0;
    public int        stuckTurns         = 0;
    /** Wall-clock seconds remaining in the white hit-flash. Purely cosmetic — does not affect simulation. */
    public float      hitFlashTimerSeconds = 0f;
    /** Wall-clock seconds remaining in the attack animation. Cosmetic only — never affects simulation. */
    public float      attackAnimTimerSeconds = 0f;
    /** Wall-clock seconds remaining in the pre-hit telegraph (same-turn flinch). Cosmetic only. */
    public float      telegraphTimerSeconds = 0f;

    /** Dungeon floor on which this enemy spawned (1-based). Used for the name-tag display "Type LVL N". */
    public int        dungeonLevel = 1;

    /** Pre-built display string shown above the health bar, e.g. "Corruptor LVL 2". Set at spawn time. */
    public String     nameTag = "";

    /** Set by StatusEffectController when STUNNED ticks; consumed in EnemyManager's EXECUTE phase (R6). */
    public boolean skipNextAction = false;

    /**
     * The action this enemy has COMMITTED to perform on its next turn (strategy-combat-order-1).
     * A single reused instance — never null after construction, but {@code committed} stays false
     * until the enemy first commits (a freshly-woken enemy shows no intent and only commits on its
     * wake turn). The intent-icon renderer (order-2) reads this during the player's turn.
     */
    public final PlannedAction plannedAction = new PlannedAction();

    /**
     * Committed cardinal rush direction captured when the charger commits a WIND_UP intent (Pillar 2).
     * Read by EnemyManager.performCharge on the following turn when the WIND_UP plan executes.
     */
    public int chargeDirectionColumn = 0;
    public int chargeDirectionRow    = 0;

    // Status effect storage — pre-allocated at construction, never replaced
    private final EnumMap<StatusType, StatusEffect> activeEffects;
    private StatusResistance statusResistance = StatusResistance.defaultResistance();

    public Enemy(EnemyType type, int tileColumn, int tileRow) {
        this.type          = type;
        this.tileColumn    = tileColumn;
        this.tileRow       = tileRow;
        this.maxHealth     = type.maxHealth();
        this.health        = this.maxHealth;
        this.activeEffects = buildEffectsMap();
    }

    private static EnumMap<StatusType, StatusEffect> buildEffectsMap() {
        EnumMap<StatusType, StatusEffect> map = new EnumMap<>(StatusType.class);
        for (StatusType type : StatusType.values()) {
            map.put(type, new StatusEffect(type));
        }
        return map;
    }

    /** Assigns the archetype-specific resist/immunity table. Call once after construction at spawn time. */
    public void setStatusResistance(StatusResistance resistance) {
        this.statusResistance = resistance;
    }

    @Override
    public EnumMap<StatusType, StatusEffect> getActiveEffects() { return activeEffects; }

    @Override
    public StatusResistance getStatusResistance() { return statusResistance; }

    @Override
    public void applyDoTDamage(int amount) {
        // Enemies have no dodge or toughness reduction — DoT damage applies directly.
        health = Math.max(0, health - amount);
    }

    /** Returns this enemy's attack damage scaled by the depth multiplier, minimum 1. */
    public int scaledAttackDamage() {
        return Math.max(1, Math.round(type.attackDamage() * attackDamageMultiplier));
    }

    public boolean isAlive() {
        return health > 0;
    }

    public boolean isAlerted() {
        return state != EnemyState.DORMANT;
    }

    public void alert() {
        if (state == EnemyState.DORMANT) {
            state = EnemyState.ALERTED;
        }
    }

    public void applyDamage(int amount) {
        health = Math.max(0, health - amount);
    }

    /** Resets the hit-flash timer to full duration. Calling again before it expires re-triggers cleanly. */
    public void triggerHitFlash() {
        hitFlashTimerSeconds = EnemyConstants.ENEMY_HIT_FLASH_DURATION_SECONDS;
    }

    /** Decrements the hit-flash timer by deltaTime, clamping at zero. Call once per frame from World.update(). */
    public void advanceHitFlash(float deltaTime) {
        if (hitFlashTimerSeconds > 0f) {
            hitFlashTimerSeconds -= deltaTime;
            if (hitFlashTimerSeconds < 0f) hitFlashTimerSeconds = 0f;
        }
    }

    /** Returns flash strength in [0, 1]: 1 = freshly hit (full white), 0 = expired (normal shade). */
    public float getHitFlashStrength() {
        if (EnemyConstants.ENEMY_HIT_FLASH_DURATION_SECONDS <= 0f) return 0f;
        return hitFlashTimerSeconds / EnemyConstants.ENEMY_HIT_FLASH_DURATION_SECONDS;
    }

    /** Resets the attack animation timer to full duration. Mirrors triggerHitFlash(). */
    public void triggerAttackAnim() {
        attackAnimTimerSeconds = EffectConstants.ENEMY_ATTACK_ANIM_DURATION_SECONDS;
        telegraphTimerSeconds  = EffectConstants.ENEMY_TELEGRAPH_DURATION_SECONDS;
    }

    /**
     * Triggers the pre-hit telegraph pulse WITHOUT the attack/lunge animation. Used for the
     * charger wind-up (Pillar 2): a readable rim flash on the turn before the rush lands.
     */
    public void triggerTelegraph() {
        telegraphTimerSeconds = EffectConstants.ENEMY_TELEGRAPH_DURATION_SECONDS;
    }

    /** Advances both attack animation and telegraph timers by deltaTime, clamping at zero. */
    public void advanceAttackAnim(float deltaTime) {
        if (attackAnimTimerSeconds > 0f) {
            attackAnimTimerSeconds -= deltaTime;
            if (attackAnimTimerSeconds < 0f) attackAnimTimerSeconds = 0f;
        }
        if (telegraphTimerSeconds > 0f) {
            telegraphTimerSeconds -= deltaTime;
            if (telegraphTimerSeconds < 0f) telegraphTimerSeconds = 0f;
        }
    }

    /** Returns attack animation strength in [0, 1]: 1 at trigger, 0 when expired. */
    public float getAttackAnimStrength() {
        if (EffectConstants.ENEMY_ATTACK_ANIM_DURATION_SECONDS <= 0f) return 0f;
        return attackAnimTimerSeconds / EffectConstants.ENEMY_ATTACK_ANIM_DURATION_SECONDS;
    }

    /** Returns telegraph strength in [0, 1]: 1 at trigger, 0 when expired. */
    public float getTelegraphStrength() {
        if (EffectConstants.ENEMY_TELEGRAPH_DURATION_SECONDS <= 0f) return 0f;
        return telegraphTimerSeconds / EffectConstants.ENEMY_TELEGRAPH_DURATION_SECONDS;
    }

    /** True when this enemy's move cooldown allows it to step on the current turn. */
    public boolean shouldMoveThisTurn() {
        return turnCounter % type.moveEveryNTurns() == 0;
    }

    /** World-space X center of this enemy's tile (for rendering). */
    public float worldCenterX() {
        return tileColumn * Constants.CELL_SIZE + Constants.CELL_SIZE / 2f;
    }

    /** World-space Y center of this enemy's tile (for rendering). */
    public float worldCenterY() {
        return tileRow * Constants.CELL_SIZE + Constants.CELL_SIZE / 2f;
    }
}
