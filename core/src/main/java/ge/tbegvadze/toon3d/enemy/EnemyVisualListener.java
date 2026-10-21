package ge.tbegvadze.toon3d.enemy;

/**
 * Cosmetic callback notified of every {@link EnemyVisualMoment} (generated-enemy-art order 2) — the
 * seam the animated enemy layer hangs off.
 *
 * <p>Follows {@link EnemyVoiceListener} exactly: kept in the enemy package so {@code EnemyManager} never
 * imports a renderer, implemented in {@code World}, and null by default so the balance simulator and the
 * tests see no change. The {@link EnemyVisualEvent} passed in is ONE reused object: read it inside the
 * call and never keep it.
 *
 * <p>Reactions are cosmetic (series rule G9): an implementation may never change a rule, a number, a
 * turn or the action lock, and never writes back to the enemy or the manager.
 */
public interface EnemyVisualListener {
    void onEnemyVisualMoment(EnemyVisualEvent event);
}
