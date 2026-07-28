package ge.tbegvadze.toon3d.sim;

import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.util.BalanceConfig;

/**
 * Which enemy a scripted player is currently fighting, and the commitment to keep fighting it
 * (new-game-balancr order 9).
 *
 * <p>Picking "the nearest enemy" fresh every turn is not how anyone plays: two enemies a tile apart
 * swap places in that ranking as the marine moves, and the marine ends up stepping toward one, then
 * the other, forever. A target is therefore LOCKED once chosen and held until it dies, leaves the
 * engage range, or becomes impossible to reach or shoot.
 */
final class TargetMemory {

    private static final int NO_TARGET = -1;

    private int lockedEnemyId = NO_TARGET;

    /** The enemy to fight this turn, or null when nothing is worth engaging. */
    Enemy resolve(SimView view) {
        Enemy locked = findLocked(view);
        if (locked != null && stillWorthFighting(view, locked)) return locked;

        Enemy fresh = SimPolicySupport.reachableTarget(view);
        lockedEnemyId = fresh == null ? NO_TARGET : fresh.getId();
        return fresh;
    }

    /** Forgets the current target — used when the policy stops fighting (retreat, floor change). */
    void clear() {
        lockedEnemyId = NO_TARGET;
    }

    private Enemy findLocked(SimView view) {
        if (lockedEnemyId == NO_TARGET) return null;
        for (Enemy enemy : view.enemies()) {
            if (enemy.getId() == lockedEnemyId) return enemy.isAlive() ? enemy : null;
        }
        return null;
    }

    /** A locked target stays locked while it is alive, in range, and still reachable or shootable. */
    private boolean stillWorthFighting(SimView view, Enemy target) {
        if (view.tileDistanceTo(target) > BalanceConfig.SIM_ENGAGE_RANGE_TILES) return false;
        if (view.hasClearShotAt(target.tileColumn, target.tileRow)) return true;
        return view.navigator() != null
                && view.navigator().isReachable(view.playerTileColumn(), view.playerTileRow(),
                                                target.tileColumn, target.tileRow);
    }
}
