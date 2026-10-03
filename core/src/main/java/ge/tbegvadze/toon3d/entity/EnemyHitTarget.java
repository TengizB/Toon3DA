package ge.tbegvadze.toon3d.entity;

/**
 * Narrow interface passed into Weapon.fire() so the shot can query and damage enemies
 * without creating a direct dependency from entity → enemy.
 * EnemyManager implements this interface.
 */
public interface EnemyHitTarget {

    /**
     * Returns the token representing the living enemy occupying the given tile,
     * or null if no living enemy is there.
     */
    Object enemyAt(int tileColumn, int tileRow);

    /** Applies the given damage amount to the enemy returned by enemyAt(). */
    void applyDamageTo(Object enemy, int amount);

    /**
     * Attempts to move the given enemy to (targetColumn, targetRow).
     * Implementations should check wall collision, prop collision, other-enemy occupancy,
     * and bounds before moving. Returns true if the push succeeded.
     * Default: no-op (returns false); override in EnemyManager.
     */
    default boolean tryPushEnemy(Object enemy, int targetColumn, int targetRow) {
        return false;
    }

    /**
     * Signals that the next applyDamageTo() call originates from a melee weapon.
     * Must be called immediately before applyDamageTo() on a confirmed hit.
     * Default: no-op; EnemyManager overrides to set an internal flag used in killEnemy().
     */
    default void notifyMeleeAttack() {}

    /**
     * Returns true if the given enemy is currently at maximum HP.
     * Called before applyDamageTo() to determine whether the target was at full health
     * before the shot's primary damage is applied (used for OPENING_SALVO ability).
     * Default returns false; EnemyManager overrides with a real check.
     */
    default boolean isAtFullHp(Object enemyObject) {
        return false;
    }

    /**
     * Applies (or refreshes) a BURNING damage-over-time status on the given enemy.
     * Used by the Incinerator so its cone leaves enemies on fire for several turns
     * after the trigger pull. Routes into the shared StatusEffectController so the
     * burn ticks, respects fire resistance/immunity, and attributes DoT kills.
     * Default: no-op; EnemyManager overrides with the real status application.
     *
     * @param enemy            the token returned by enemyAt()
     * @param turns            burn duration in world turns
     * @param magnitudePerTurn burn damage applied each turn
     */
    default void applyBurningStatus(Object enemy, int turns, int magnitudePerTurn) {}

    /**
     * Adds one STACK of the Incinerator's burn (balance-overhaul order 3, W3): up to {@code maxStacks}
     * stacks tick together, each {@code magnitudePerStack} per turn; a new stack refreshes the shared
     * timer. Default: falls back to a plain (non-stacking) burn.
     *
     * @param enemy             the token returned by enemyAt()
     * @param turns             burn duration in turns (refreshed by every stack)
     * @param magnitudePerStack damage per turn per stack (before the matchup / fire resistance)
     * @param maxStacks         stack cap
     */
    default void applyBurningStack(Object enemy, int turns, int magnitudePerStack, int maxStacks) {
        applyBurningStatus(enemy, turns, magnitudePerStack);
    }

    /**
     * Tries to STAGGER the enemy (balance-overhaul order 3, W1): its next committed action is cancelled
     * and its intent reads STUNNED. The owner refuses bosses, an already-cancelled action, and a
     * stagger two turns running. Default: no-op (false).
     *
     * @return true if the stagger landed
     */
    default boolean tryStaggerEnemy(Object enemy) { return false; }

    /**
     * Tries to knock the enemy one tile along (stepColumn, stepRow) — away from the player (W1). The
     * owner refuses BOSS / MINI_ELITE targets and any wall, door, prop or occupied destination; hazard
     * tiles are allowed. Default: no-op (false).
     *
     * @return true if the enemy moved
     */
    default boolean tryKnockbackEnemy(Object enemy, int stepColumn, int stepRow) { return false; }

    /**
     * Applies (or refreshes) a VULNERABLE mark on the given enemy (strategy-combat-order-6): while it
     * lasts the target takes more damage from every incoming hit, and each application adds a stack up
     * to the balance-capped maximum. The "marking shot" setup — land a cheap mark, then dump the big
     * weapon for a burst that clears the golden-ratio TTK a turn early.
     * Default: no-op; EnemyManager overrides with the real status application.
     *
     * @param enemy the token returned by enemyAt()
     * @param turns mark duration in world turns
     */
    default void applyVulnerableStatus(Object enemy, int turns) {}

    /**
     * Applies (or refreshes) an EXPOSED flag on the given enemy (strategy-combat-order-6): the next hit
     * into the target ignores its Block. The dedicated answer to turtling, Block-stacking enemies for a
     * build without an armor-piercing weapon. Default: no-op; EnemyManager overrides.
     *
     * @param enemy the token returned by enemyAt()
     * @param turns how many world turns the flag stays armed before it lapses unused
     */
    default void applyExposedStatus(Object enemy, int turns) {}

    /**
     * Arms a Block-pierce fraction for every applyDamageTo() call until it is cleared with 0f
     * (ARMOR_PIERCE ability). While armed, each incoming hit ignores that fraction of the
     * target's Block before it is absorbed, letting damage bleed through to HP on shielding
     * enemies. Set once at the start of a fire activation and cleared (0f) at the end, so DoT
     * ticks and barrel damage — which resolve outside the activation — are never affected.
     * Default: no-op; EnemyManager overrides to store the fraction.
     *
     * @param fraction fraction of the target's Block to bypass, in [0, 1]; 0 disarms
     */
    default void setActivationBlockPierce(float fraction) {}

    /**
     * Arms the DAMAGE CLASS (balance-overhaul order 3, M3) every applyDamageTo() / applyBurningStatus()
     * call resolves with until it is cleared with null. Set by Weapon.fire() at the start of an
     * activation (beside the Block pierce) and cleared at its end, so every synchronous hit of the
     * activation — base shot, burst extras, resolver bonuses — takes the weapon's matchup. Barrels set
     * EXPLOSIVE around their own blast and restore the previous value. Null = no matchup (1.0).
     * Default: no-op; EnemyManager overrides to store it.
     */
    default void setActivationDamageClass(DamageClass damageClass) {}

    /** The damage class currently armed by {@link #setActivationDamageClass}, or null. Default null. */
    default DamageClass getActivationDamageClass() { return null; }

    /**
     * While armed, a matchup multiplier below 1.0 is raised to 1.0 (W5: the Arc Cannon's chain always
     * does at least neutral damage, so it stays a group tool). Armed by ArcCannon around its chain
     * leaps only. Default: no-op.
     */
    default void setActivationMatchupFloorNeutral(boolean floorNeutral) {}

    /** Whether {@link #setActivationMatchupFloorNeutral} is currently armed. Default false. */
    default boolean isActivationMatchupFloorNeutral() { return false; }

    /**
     * True when a live CRYSTAL SPIRE (grown by a Verdant Spiresower) occupies the given tile
     * (.claude/agents/ideas/elemental-golem-verdant-spiresower.txt). A spire is SOLID terrain, not an
     * enemy — it has no health bar, no AI, no XP — so it is reached through this shot seam rather than
     * {@link #enemyAt}: a weapon whose shot stops on a spire's cover tile queries this and, if true,
     * routes the hit through {@link #damageSpireAt} instead of into a wall. Default false; EnemyManager
     * overrides to delegate to the SpireManager. Extends the EXISTING hit-target seam rather than adding a
     * parallel damage path.
     */
    default boolean isSpireAt(int tileColumn, int tileRow) { return false; }

    /**
     * Applies {@code amount} damage to a spire on the given tile (no-op if none is there). A spire has flat
     * HP and no mitigation, so this carries the raw weapon damage; destroying it snaps the golem's heal
     * link. Default no-op; EnemyManager overrides to delegate to the SpireManager.
     */
    default void damageSpireAt(int tileColumn, int tileRow, int amount) {}
}
