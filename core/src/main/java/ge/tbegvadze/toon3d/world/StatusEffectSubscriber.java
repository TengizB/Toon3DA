package ge.tbegvadze.toon3d.world;

import ge.tbegvadze.toon3d.enemy.EnemyManager;
import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.status.StatusEffectController;

/**
 * Tick subscriber that advances all active status effects once per world turn.
 * Registered BEFORE EnemyTurnSubscriber so that:
 *   - DoT damage is applied before enemies act (corpses never move).
 *   - STUNNED flags are set before phaseB so stunned enemies skip their action.
 *   - Player stun flag is available when PlayerController calls pollInput() next frame.
 *
 * Created fresh each floor (level-dependent) because EnemyManager is rebuilt per floor.
 * The StatusEffectController itself is run-persistent (owned by World).
 */
final class StatusEffectSubscriber implements TickSubscriber {

    private final StatusEffectController statusEffectController;
    private final Player                 player;
    private final EnemyManager           enemyManager;

    StatusEffectSubscriber(StatusEffectController controller, Player player, EnemyManager enemyManager) {
        this.statusEffectController = controller;
        this.player                 = player;
        this.enemyManager           = enemyManager;
    }

    @Override
    public void onTick(TickContext context) {
        statusEffectController.tickAll(player, enemyManager);
    }
}
