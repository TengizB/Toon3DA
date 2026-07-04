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
}
