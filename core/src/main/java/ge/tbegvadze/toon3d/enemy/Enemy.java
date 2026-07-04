package ge.tbegvadze.toon3d.enemy;

import ge.tbegvadze.toon3d.status.StatusEffect;
import ge.tbegvadze.toon3d.status.StatusHost;
import ge.tbegvadze.toon3d.status.StatusResistance;
import ge.tbegvadze.toon3d.status.StatusType;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.EffectConstants;
import ge.tbegvadze.toon3d.util.EnemyConstants;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.IntentConstants;

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
     * Temporary damage-absorbing buffer (strategy-combat-order-3). Incoming damage is subtracted
     * from Block before it reaches HP (and before flat armor) — see {@link #applyDamage}. Gained
     * when the enemy EXECUTEs a committed DEFEND ({@link #gainBlock}); zeroed after
     * {@link #decayBlock} counts down (driven by the StatusEffectController tick). Default 0.
     * Direct HP loss from status DoT ({@link #applyDoTDamage}) deliberately BYPASSES Block.
     */
    public int        block = 0;
    /**
     * World turns of life remaining on the current Block before it expires. Set by {@link #gainBlock};
     * decremented once per world turn by {@link #decayBlock}. Meaningless while {@code block == 0}.
     */
    public int        blockDecayTurns = 0;
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
     * No-repeat memory for scripted SPECIAL abilities (strategy-combat-order-5). One reused instance;
     * the COMMIT step records each chosen ability so a multi-ability caster rotates its script rather
     * than spamming one move. Never allocated inside the turn loop.
     */
    public final MoveHistory moveHistory = new MoveHistory();

    /**
     * How many chaff this summoner has spawned so far (strategy-combat-order-5). Bounded by
     * {@code EnemyConstants.SUMMON_PER_ENEMY_CAP} so a single summoner cannot flood the room and blow
     * the encounter's Threat-Point budget. Non-summoner archetypes leave this at 0.
     */
    public int summonsSpawned = 0;

    /**
     * The action this enemy has COMMITTED to perform on its next turn (strategy-combat-order-1).
     * A single reused instance — never null after construction, but {@code committed} stays false
     * until the enemy first commits (a freshly-woken enemy shows no intent and only commits on its
     * wake turn). The intent-icon renderer (order-2) reads this during the player's turn.
     */
    public final PlannedAction plannedAction = new PlannedAction();

    /**
     * Wall-clock seconds remaining in the intent-icon "pop" (strategy-combat-order-2): a brief
     * scale-in played when the enemy re-commits to a DIFFERENT verb than last turn, so the player's
     * eye is drawn to the change. Cosmetic only — never read by the simulation.
     */
    public float intentPopTimerSeconds = 0f;

    /**
     * The verb committed on the PREVIOUS commit, used solely to detect a verb change and fire the
     * intent-icon pop. Null until the enemy's first commit. Cosmetic bookkeeping for order-2.
     */
    private IntentVerb previouslyCommittedVerb = null;

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
        // Status DoT (burning/poison/bleed) BYPASSES Block by design (strategy-combat-order-3):
        // status builds are the rock-paper-scissors answer to defensive, Block-stacking enemies.
        health = Math.max(0, health - amount);
    }

    /**
     * Grants Block (strategy-combat-order-3): braces the enemy for {@code amount}, added to any
     * residual Block and capped at {@code blockMax}. A fresh DEFEND also REFRESHES the decay window
     * to a full {@code decayTurns} — re-bracing keeps the shield alive for another window rather than
     * inheriting the old timer (the REFRESH_DURATION semantics the status layer uses for burns). With
     * the default 1-turn decay, Block always expires between two of an enemy's turns, so this refresh
     * only ever re-arms a freshly-emptied buffer in practice. Called from EnemyManager when a committed
     * DEFEND executes.
     */
    public void gainBlock(int amount, int blockMax, int decayTurns) {
        if (amount <= 0) return;
        block           = Math.min(blockMax, block + amount);
        blockDecayTurns = decayTurns;
    }

    /**
     * Counts the active Block down by one world turn and zeroes it on expiry (strategy-combat-order-3).
     * Called once per living enemy per turn from the StatusEffectController tick so Block expiry shares
     * the deterministic status-phase ordering. No-op while the enemy holds no Block.
     */
    public void decayBlock() {
        if (block <= 0) {
            blockDecayTurns = 0;
            return;
        }
        blockDecayTurns--;
        if (blockDecayTurns <= 0) {
            block           = 0;
            blockDecayTurns = 0;
        }
    }

    /**
     * Returns this enemy's attack damage scaled by the depth multiplier AND its own EMPOWERED buff
     * (strategy-combat-order-5: a caster that used BUFF_SELF hits harder), minimum 1. Reading the buff
     * here means the same value drives both the committed predicted-damage number and the executed hit.
     */
    public int scaledAttackDamage() {
        return Math.max(1, Math.round(type.attackDamage() * attackDamageMultiplier * empoweredDamageMultiplier()));
    }

    /**
     * Returns the outgoing-damage multiplier from an active EMPOWERED buff (1.0 when unbuffed), mirroring
     * {@code Player.getEmpoweredDamageMultiplier()}: {@code 1 + magnitude / 100}. Lets an enemy that cast
     * BUFF_SELF (order-5) actually swing harder while the buff lasts.
     */
    private float empoweredDamageMultiplier() {
        StatusEffect empoweredEffect = activeEffects.get(StatusType.EMPOWERED);
        if (empoweredEffect != null && empoweredEffect.isActive()) {
            return 1f + empoweredEffect.getMagnitude() / 100f;
        }
        return 1f;
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

    /**
     * Applies a weapon/ability hit through the Block → armor → HP portion of the shared mitigation
     * pipeline (strategy-combat-order-3; the directional/power modifiers that precede these steps are
     * resolved by the caller). Block absorbs first (a hit fully swallowed by fresh Block deals no HP
     * damage — overkill into Block is lost), then flat armor reduces the overflow, then HP takes the
     * remainder. See docs/balance-rule-system.txt for the full 5-step order.
     */
    public void applyDamage(int amount) {
        int absorbed = GameMath.blockAbsorbed(block, amount);
        block -= absorbed;
        int remaining = amount - absorbed;
        if (remaining <= 0) return;             // fully braced — HP untouched this turn
        int afterArmor = Math.max(0, remaining - armor);
        health = Math.max(0, health - afterArmor);
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

    /**
     * Records the verb just committed and, if it differs from the previously committed verb, triggers
     * the intent-icon pop (order-2). Call once per commit from EnemyManager's COMMIT phase. The first
     * commit never pops (there is no earlier verb to differ from).
     */
    public void notifyCommitted(IntentVerb committedVerb) {
        if (previouslyCommittedVerb != null && committedVerb != previouslyCommittedVerb) {
            intentPopTimerSeconds = IntentConstants.INTENT_POP_DURATION_SECONDS;
        }
        previouslyCommittedVerb = committedVerb;
    }

    /** Decrements the intent-pop timer by deltaTime, clamping at zero. Call once per frame. */
    public void advanceIntentPop(float deltaTime) {
        if (intentPopTimerSeconds > 0f) {
            intentPopTimerSeconds -= deltaTime;
            if (intentPopTimerSeconds < 0f) intentPopTimerSeconds = 0f;
        }
    }

    /** Returns intent-pop strength in [0, 1]: 1 at the instant of a verb change, 0 when settled. */
    public float getIntentPopStrength() {
        if (IntentConstants.INTENT_POP_DURATION_SECONDS <= 0f) return 0f;
        return intentPopTimerSeconds / IntentConstants.INTENT_POP_DURATION_SECONDS;
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
