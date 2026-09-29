package ge.tbegvadze.toon3d.enemy;

/**
 * The moments an enemy makes a noise (procedural-sound-effects orders 3 and 6).  ALERT, ATTACK and
 * DEATH are in the enemy's FAMILY voice; WIND_UP (order 6) is one shared telegraph sound.
 *
 * <p>HURT is deliberately absent: the impact sound already covers being hit, and a hurt grunt on
 * every pellet of a shotgun blast is the fastest way to turn combat into mush.
 *
 * <p>Lives in the enemy package, beside {@link EnemyVoiceListener}, so that {@code EnemyManager} can
 * name a moment without importing the audio layer — the same reason {@link EnemyAttackListener}
 * lives here. The audio layer reads it; the enemy layer never reads audio.
 */
public enum EnemyVoiceMoment {

    /** Something woke up: the DORMANT -> ALERTED transition. */
    ALERT,
    /** It swung. (A ranged shot keeps the shared launch sound — see {@code EnemyAttackFanout}.) */
    ATTACK,
    /** It died, by any route: weapon, damage-over-time, splash, self-destruct. */
    DEATH,
    /**
     * It COMMITTED a wind-up (order 6): a charger's rush, a sower's spire, a generic heavy swing, a
     * self-destruct's first prime — the turn the rim-flash telegraph appears.  Never a family voice:
     * the catalog resolves it through the per-moment fallback to one shared sound.
     */
    WIND_UP;

    public static final int COUNT = values().length;
}
