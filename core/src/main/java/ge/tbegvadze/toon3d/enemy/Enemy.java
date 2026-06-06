package ge.tbegvadze.toon3d.enemy;

import ge.tbegvadze.toon3d.util.Constants;

/** Runtime state for a single enemy instance. Pure data + lightweight behavior helpers. */
public final class Enemy {

    public final EnemyType type;

    /** Authoritative tile position (integer grid coordinates). */
    public int tileColumn;
    public int tileRow;

    public int        health;
    /** Effective max HP for this instance — may exceed type.maxHealth() on deeper floors. */
    public int        maxHealth;
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

    public Enemy(EnemyType type, int tileColumn, int tileRow) {
        this.type       = type;
        this.tileColumn = tileColumn;
        this.tileRow    = tileRow;
        this.maxHealth  = type.maxHealth();
        this.health     = this.maxHealth;
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
        hitFlashTimerSeconds = Constants.ENEMY_HIT_FLASH_DURATION_SECONDS;
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
        if (Constants.ENEMY_HIT_FLASH_DURATION_SECONDS <= 0f) return 0f;
        return hitFlashTimerSeconds / Constants.ENEMY_HIT_FLASH_DURATION_SECONDS;
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
