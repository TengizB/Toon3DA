package ge.tbegvadze.toon3d.entity;

/**
 * Receives notification of enemy hit and kill events so visual-effect systems
 * can respond without coupling EnemyManager to a specific renderer.
 * Placed in entity alongside the analogous EnemyHitTarget interface.
 */
public interface ImpactEventListener {

    /**
     * Called when a non-lethal hit lands on an enemy.
     *
     * @param worldX          world-space X of the enemy's centre tile
     * @param worldY          world-space Y of the enemy's centre tile
     * @param heightMultiplier the enemy type's billboard height fraction (used to scale effects)
     * @param damageDealt     actual damage applied this hit
     */
    void onEnemyHit(float worldX, float worldY, float heightMultiplier, int damageDealt);

    /**
     * Called when the killing blow drops enemy health to zero.
     * The enemy object is still valid for position/type reads; it will be
     * removed from the live list after this call returns.
     *
     * @param worldX          world-space X of the enemy's centre tile
     * @param worldY          world-space Y of the enemy's centre tile
     * @param heightMultiplier the enemy type's billboard height fraction
     * @param killingBlowDamage damage that caused the death (may exceed remaining HP)
     */
    void onEnemyKilled(float worldX, float worldY, float heightMultiplier, int killingBlowDamage);

    /**
     * Called when an enemy's Block absorbs part or all of an incoming hit (strategy-combat-order-3).
     * Lets the effect system play the shield-specific "clink" — blue sparks, and a shatter ring when
     * the buffer broke — visually distinct from a flesh hit. Default: no-op.
     *
     * @param tileColumn       enemy tile column (projected to screen at spawn time)
     * @param tileRow          enemy tile row
     * @param heightMultiplier the enemy type's billboard height fraction (scales effect placement)
     * @param absorbedAmount   how much damage the Block ate this hit
     * @param shattered        true when the hit exceeded the buffer and Block broke to zero
     */
    default void onBlockAbsorbed(int tileColumn, int tileRow, float heightMultiplier,
                                 int absorbedAmount, boolean shattered) {}

    /**
     * Called when a SUMMON ability (strategy-combat-order-5) materializes a new enemy on a
     * previously empty tile, so the effect system can play a portal-burst animation there —
     * without this, a summoned enemy would just silently appear. Default: no-op.
     *
     * @param tileColumn       the empty tile the new enemy spawned on
     * @param tileRow          the empty tile the new enemy spawned on
     * @param heightMultiplier the spawned enemy type's billboard height fraction
     */
    default void onEnemySpawned(int tileColumn, int tileRow, float heightMultiplier) {}

    /**
     * Called when an orbiting shard is destroyed (elemental-golem-auric-sentinel), whether it was spent
     * absorbing a hit, spent launching a shot, or lost when the ring collapsed on death. Lets the effect
     * system play the gold "clink" — a spark burst plus a ring pulse — which, together with the gold
     * billboard flash that REPLACES the usual red hit flash, is what tells the player the hit did
     * nothing to the enemy's health. Default: no-op.
     *
     * @param tileColumn       the shard-bearing enemy's tile column
     * @param tileRow          the shard-bearing enemy's tile row
     * @param heightMultiplier the enemy type's billboard height fraction (scales effect placement)
     * @param shardsShattered  how many shards broke at once (1 for an absorb or a launch; the whole
     *                         remaining ring when the enemy dies)
     */
    default void onShardShattered(int tileColumn, int tileRow, float heightMultiplier,
                                  int shardsShattered) {}

    /**
     * Called when a shard-bearing enemy re-grows a shard (elemental-golem-auric-sentinel), so the
     * effect system can play the gold crystallise flash. The player who sees it should feel the clock
     * running: the ring is coming back. Default: no-op.
     *
     * @param tileColumn       the enemy's tile column
     * @param tileRow          the enemy's tile row
     * @param heightMultiplier the enemy type's billboard height fraction
     */
    default void onShardRegrown(int tileColumn, int tileRow, float heightMultiplier) {}
}
