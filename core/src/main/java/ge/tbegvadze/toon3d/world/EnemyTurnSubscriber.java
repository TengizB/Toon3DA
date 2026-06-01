package ge.tbegvadze.toon3d.world;

import ge.tbegvadze.toon3d.enemy.EnemyManager;

/**
 * Adapter that drives the enemy AI once per game tick.
 * Keeps EnemyManager unaware of the event bus and avoids the enemy package
 * depending on the world package.
 */
final class EnemyTurnSubscriber implements TickSubscriber {

    private final EnemyManager enemyManager;
    private final GameState    gameState;

    EnemyTurnSubscriber(EnemyManager enemyManager, GameState gameState) {
        this.enemyManager = enemyManager;
        this.gameState    = gameState;
    }

    @Override
    public void onTick(TickContext context) {
        enemyManager.takeTurn(context.getPlayerTileColumn(), context.getPlayerTileRow(), context.getPlayer());
        if (enemyManager.anyAlerted()) {
            gameState.redAlert = true;
        }
    }
}
