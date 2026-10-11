package ge.tbegvadze.toon3d.enemy;

/**
 * Observation seam for the balance simulator (balance-overhaul order 4, CP7b): what became of every HEAVY
 * attack committed at the player, so the S-HEAVY band can measure whether a reading player answers them.
 *
 * <p>Headless and one-way — {@link EnemyManager} fires it, nothing in the game reacts to it, and with no
 * listener installed it costs a null check. {@code enemy/} never imports the simulator.
 */
public interface HeavyOutcomeListener {

    /**
     * A heavy was committed and the player's CURRENT tile is in its danger set. Fired once per committed
     * instance (a self-destruct that re-telegraphs its countdown every turn is one instance).
     */
    void onHeavyCommittedAtPlayer(Enemy enemy, HeavyAttack kind);

    /** The instance announced by {@link #onHeavyCommittedAtPlayer} ended; fired exactly once per instance. */
    void onHeavyResolved(Enemy enemy, HeavyAttack kind, HeavyOutcome outcome);

    /** Armour spent itself holding an ENEMY-applied status off the player (the H9 shield blocked one). */
    void onArmourBlockedStatus();
}
