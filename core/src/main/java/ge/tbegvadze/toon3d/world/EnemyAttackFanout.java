package ge.tbegvadze.toon3d.world;

import ge.tbegvadze.toon3d.audio.GameAudio;
import ge.tbegvadze.toon3d.audio.GameSoundId;
import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.enemy.EnemyAttackListener;

/**
 * Lets an enemy's attack drive BOTH the existing visual effect system and gameplay audio
 * (procedural-sound-effects order 2).
 *
 * <p>{@code EnemyManager.setEnemyAttackListener} takes a single listener, and that slot is already
 * held by {@code EnemyAttackEffectSystem}. Rather than edit {@code EnemyManager} to hold a list,
 * this wraps the existing listener and adds the sound beside it — the manager keeps its single-slot
 * API and neither it nor the effect system changes at all.
 *
 * <p>Safe to wrap because {@link EnemyAttackListener} is a two-method interface with no defaults:
 * every method it will ever have is forwarded here by construction. The much wider
 * {@code ImpactEventListener} is deliberately NOT handled this way — see
 * {@code ImpactEffectSystem}'s note.
 *
 * <p>Lives in {@code world} because that is where the wiring lives; the enemy package keeps no
 * dependency on audio.
 */
public final class EnemyAttackFanout implements EnemyAttackListener {

    private final EnemyAttackListener delegate;
    private final GameAudio           gameAudio;

    public EnemyAttackFanout(EnemyAttackListener delegate, GameAudio gameAudio) {
        this.delegate  = delegate;
        this.gameAudio = gameAudio;
    }

    @Override
    public void onMeleeAttack(Enemy enemy) {
        if (delegate != null) delegate.onMeleeAttack(enemy);
        playAtEnemy(GameSoundId.ENEMY_ATTACK_MELEE, enemy);
    }

    @Override
    public void onRangedAttack(Enemy enemy, int playerColumn, int playerRow) {
        if (delegate != null) delegate.onRangedAttack(enemy, playerColumn, playerRow);
        // Deliberately placed at the SHOOTER, not at the player: the whole value of hearing a
        // ranged attack is learning which direction it came from.
        playAtEnemy(GameSoundId.ENEMY_ATTACK_RANGED, enemy);
    }

    private void playAtEnemy(GameSoundId soundId, Enemy enemy) {
        if (gameAudio == null || enemy == null) return;
        gameAudio.playAtWorld(soundId, enemy.worldCenterX(), enemy.worldCenterY(), 1f);
    }
}
