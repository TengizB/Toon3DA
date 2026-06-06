package ge.tbegvadze.toon3d.progression;

/**
 * Receives full kill-event details so UI systems can display a kill message.
 * Separate from {@link KillXpListener} so progression and presentation concerns stay decoupled.
 */
public interface KillEventListener {

    /**
     * Called immediately after an enemy's health reaches zero.
     *
     * @param nameTag    pre-built display string, e.g. "Corruptor LVL 2"
     * @param xpAwarded  XP this kill awards (same value passed to KillXpListener)
     */
    void onEnemyKilled(String nameTag, int xpAwarded);
}
