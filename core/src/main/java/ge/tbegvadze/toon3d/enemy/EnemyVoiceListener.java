package ge.tbegvadze.toon3d.enemy;

/**
 * Cosmetic callback notified when an enemy makes a noise in its own voice
 * (procedural-sound-effects order 3).
 *
 * <p>{@code EnemyManager} fires it at two existing points: the DORMANT -> ALERTED transition in the
 * perception phase ({@link EnemyVoiceMoment#ALERT}) and {@code killEnemy(...)}, the single funnel
 * every death passes through ({@link EnemyVoiceMoment#DEATH}). The ATTACK moment rides the existing
 * {@link EnemyAttackListener} instead, so an attack is never announced twice. Order 6 adds a third
 * point: every committed wind-up ({@link EnemyVoiceMoment#WIND_UP}) — the turn the telegraph shows.
 *
 * <p>Kept in the enemy package so the manager has no dependency on the audio layer — the same rule
 * {@link EnemyAttackListener} states. Implemented by a lambda in {@code World}.
 */
public interface EnemyVoiceListener {
    void onEnemyVoice(Enemy enemy, EnemyVoiceMoment moment);
}
