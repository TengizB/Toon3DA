package ge.tbegvadze.toon3d.sim;

import ge.tbegvadze.toon3d.input.touch.TouchAction;
import ge.tbegvadze.toon3d.util.BalanceConfig;

/**
 * Notices a scripted player going in circles and tells it to PUSH (balance-overhaul order 7, P8).
 *
 * <p>Every policy rule is sensible on its own — step off a ranged lane, step off a heavy's red floor, walk
 * the shortest path to the stairs — but two of them can undo each other forever: the dodge steps off the
 * lane, the walk plan steps back on, the heavy re-commits. A human notices after a few bounces and does
 * something different. This holds exactly that much memory: the last {@code SIM_LOOP_WINDOW_TURNS} tiles and
 * whether any of those turns did something productive (fired, reloaded, healed, guarded). Bouncing among at
 * most {@code SIM_LOOP_MAX_DISTINCT_TILES} tiles with nothing productive opens a {@code SIM_LOOP_PUSH_TURNS}
 * push window, in which the policy skips its sidesteps and routes through enemies instead of around them.
 *
 * <p>Navigation competence only — it never decides a game rule. One instance per policy; it notices a floor
 * change on its own and forgets the previous floor.
 */
final class LoopBreaker {

    private final long[] recentTiles = new long[BalanceConfig.SIM_LOOP_WINDOW_TURNS];
    private int recordedTurns;
    private int turnsSinceProductiveAction = Integer.MAX_VALUE / 2;
    private int pushTurnsLeft;
    private int lastSeenDepth = -1;

    /**
     * Records the marine's tile this turn and answers whether the policy should push THIS turn. Call once per
     * turn, before deciding; report the decision with {@link #recordAction}.
     */
    boolean beginTurn(SimView view) {
        if (view.depth() != lastSeenDepth) {
            lastSeenDepth = view.depth();
            recordedTurns = 0;
            pushTurnsLeft = 0;
            turnsSinceProductiveAction = Integer.MAX_VALUE / 2;
        }
        recentTiles[recordedTurns % recentTiles.length] = tileKey(view.playerTileColumn(), view.playerTileRow());
        recordedTurns++;
        if (pushTurnsLeft > 0) {
            pushTurnsLeft--;
            return true;
        }
        if (recordedTurns >= recentTiles.length
                && turnsSinceProductiveAction >= recentTiles.length
                && distinctRecentTiles() <= BalanceConfig.SIM_LOOP_MAX_DISTINCT_TILES) {
            pushTurnsLeft = BalanceConfig.SIM_LOOP_PUSH_TURNS - 1;
            recordedTurns = 0;   // a fresh window after the push, so one loop opens one push
            return true;
        }
        return false;
    }

    /** Reports the action taken this turn: firing, reloading, healing or guarding is not "going in circles". */
    void recordAction(TouchAction action) {
        boolean productive = action == TouchAction.FIRE || action == TouchAction.RELOAD
                || action == TouchAction.HEAL || action == TouchAction.GUARD;
        turnsSinceProductiveAction = productive ? 0 : turnsSinceProductiveAction + 1;
    }

    private int distinctRecentTiles() {
        int distinct = 0;
        for (int index = 0; index < recentTiles.length; index++) {
            boolean seenBefore = false;
            for (int earlier = 0; earlier < index; earlier++) {
                if (recentTiles[earlier] == recentTiles[index]) {
                    seenBefore = true;
                    break;
                }
            }
            if (!seenBefore) distinct++;
        }
        return distinct;
    }

    private static long tileKey(int tileColumn, int tileRow) {
        return ((long) tileColumn << 32) ^ (tileRow & 0xFFFFFFFFL);
    }
}
