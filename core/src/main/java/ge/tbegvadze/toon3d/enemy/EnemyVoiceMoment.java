package ge.tbegvadze.toon3d.enemy;

/**
 * The three moments an enemy makes a noise with its own voice (procedural-sound-effects order 3).
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
    DEATH;

    public static final int COUNT = values().length;
}
