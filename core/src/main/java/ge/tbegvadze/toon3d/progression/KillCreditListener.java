package ge.tbegvadze.toon3d.progression;

/**
 * Receives notification when an enemy is killed so the credit system can award
 * currency to the player.  Mirrors {@link KillXpListener} but for credits.
 */
public interface KillCreditListener {

    /**
     * Called by {@code EnemyManager} immediately after an enemy's health reaches zero.
     *
     * @param baseReward   credits at dungeon depth 1 for this enemy type
     * @param dungeonDepth current floor number (1-based); caller passes it so the listener
     *                     can apply depth scaling without holding a reference to the world
     */
    void onEnemyKilledForCredits(int baseReward, int dungeonDepth);
}
