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

    /**
     * Called when a Cinderforge Colossus's COOLING CRUST gains a stack
     * (.claude/agents/ideas/elemental-golem-cinderforge-colossus.txt) — i.e. the player let a whole
     * enemy turn pass without hurting it. A cold, quiet tell by design: the loud beat belongs to the
     * SHATTER, and a hardening enemy should feel like something the player let happen. Default: no-op.
     *
     * @param tileColumn       the enemy's tile column
     * @param tileRow          the enemy's tile row
     * @param heightMultiplier the enemy type's billboard height fraction
     * @param crustStacks      the stack count AFTER the gain (1..max)
     */
    default void onCrustCooled(int tileColumn, int tileRow, float heightMultiplier, int crustStacks) {}

    /**
     * Called when a cooled crust SHATTERS under a hit — the payoff beat for coming back out and firing
     * (.claude/agents/ideas/elemental-golem-cinderforge-colossus.txt). Scaled by how much shell broke:
     * breaking a full three-stack shell must feel bigger than chipping a single stack, because the
     * player earned more. Default: no-op.
     *
     * @param tileColumn       the enemy's tile column
     * @param tileRow          the enemy's tile row
     * @param heightMultiplier the enemy type's billboard height fraction
     * @param stacksBroken     how many crust stacks broke at once (1..max)
     * @param wasFullCrust     true when the shell was FULLY cooled, i.e. this hit also earned the
     *                         shatter damage bonus — the cue deserves the bigger burst and the flash
     */
    default void onCrustShattered(int tileColumn, int tileRow, float heightMultiplier,
                                  int stacksBroken, boolean wasFullCrust) {}

    /**
     * Called when something enormous completes a step — today only the Cinderforge Colossus. The room
     * should announce that it is coming even when it is out of view, so the shake is scaled by distance
     * rather than gated on visibility. Default: no-op.
     *
     * @param tileColumn        the tile the enemy stepped ONTO
     * @param tileRow           the tile the enemy stepped ONTO
     * @param heightMultiplier  the enemy type's billboard height fraction
     * @param distanceTiles     Chebyshev distance from the player, used to attenuate the shake
     */
    default void onHeavyFootfall(int tileColumn, int tileRow, float heightMultiplier,
                                 int distanceTiles) {}

    /**
     * Called when a magma construct dies and collapses into embers
     * (.claude/agents/ideas/elemental-golem-cinderforge-colossus.txt). Distinct from the generic death
     * burst {@link #onEnemyKilled} already fires: this is the archetype's own colour, so a Colossus
     * going out reads as a furnace being extinguished. Default: no-op.
     *
     * @param tileColumn       the tile the enemy died on
     * @param tileRow          the tile the enemy died on
     * @param heightMultiplier the enemy type's billboard height fraction
     */
    default void onEmberCollapse(int tileColumn, int tileRow, float heightMultiplier) {}

    /**
     * Called when a Rimeshell Lancer discharges its RIME LANCE down a cardinal lane
     * (.claude/agents/ideas/elemental-golem-rimeshell-lancer.txt), so the effect system can sweep an
     * orange-white beam and puff a frost ring off the muzzle — the ice/fire contrast that is the
     * archetype's identity. The lane is given as a unit cardinal step so the effect can walk the exact
     * tiles the beam resolved. Default: no-op.
     *
     * @param originColumn     the Lancer's tile column (the muzzle)
     * @param originRow        the Lancer's tile row (the muzzle)
     * @param stepColumn       unit cardinal lane direction in columns (-1, 0, or 1)
     * @param stepRow          unit cardinal lane direction in rows (-1, 0, or 1)
     * @param reachTiles       how many tiles the beam travelled before a wall stopped it
     * @param heightMultiplier the enemy type's billboard height fraction (scales effect placement)
     */
    default void onLanceBeam(int originColumn, int originRow, int stepColumn, int stepRow,
                             int reachTiles, float heightMultiplier) {}

    /**
     * Called when a Rimeshell Lancer dies and its ice shell SHATTERS
     * (.claude/agents/ideas/elemental-golem-rimeshell-lancer.txt): a cold spray of blue-white shards plus
     * one hot orange core flash as the furnace goes out. Distinct from the generic death burst
     * {@link #onEnemyKilled} already fires, so a Lancer going out reads as its own event. Default: no-op.
     *
     * @param tileColumn       the tile the enemy died on
     * @param tileRow          the tile the enemy died on
     * @param heightMultiplier the enemy type's billboard height fraction
     */
    default void onFrostShatter(int tileColumn, int tileRow, float heightMultiplier) {}
}
