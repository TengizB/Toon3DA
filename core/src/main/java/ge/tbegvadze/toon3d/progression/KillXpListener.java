package ge.tbegvadze.toon3d.progression;

/**
 * Receives notification when an enemy is killed so that the XP system can
 * award experience to the player.  Kept in the {@code progression} package to
 * avoid coupling {@code EnemyManager} directly to {@code PlayerProgress}.
 */
public interface KillXpListener {

    /**
     * Called by {@code EnemyManager} immediately after an enemy's health reaches zero.
     *
     * @param xpAwarded positive XP amount derived from the enemy's type base reward
     */
    void onEnemyKilledForXp(int xpAwarded);
}
