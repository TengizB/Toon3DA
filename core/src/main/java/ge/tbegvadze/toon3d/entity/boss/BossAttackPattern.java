package ge.tbegvadze.toon3d.entity.boss;

import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.level.Level;

/**
 * Strategy interface for one phase of a boss encounter.
 *
 * Called by BossFloorController once per tick — must not allocate on the heap,
 * must not block, and must not mutate the Level or Player directly.
 * The boss's DangerTileSet is armed inside nextMove() when TELEGRAPH is returned.
 */
public interface BossAttackPattern {

    /**
     * Returns the next move for the boss to execute this tick.
     *
     * @param boss       the boss entity (position, health, dangerTileSet)
     * @param player     the player (positionX/Y only — do not call applyDamage here)
     * @param level      current level grid for tile queries
     * @param tickIndex  monotonically increasing counter of ticks since the boss awakened
     * @return the BossMove the controller should execute this turn
     */
    BossMove nextMove(Boss boss, Player player, Level level, long tickIndex);

    /**
     * Called once when this phase becomes active (either at awaken for phase 1, or
     * at the phase transition for phase 2). Implementations reset their internal
     * step counters here.
     */
    default void onPhaseStart() {}
}
