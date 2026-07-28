package ge.tbegvadze.toon3d.sim;

import java.util.HashSet;
import java.util.Set;

import ge.tbegvadze.toon3d.input.touch.TouchAction;
import ge.tbegvadze.toon3d.util.BalanceConfig;

/**
 * Where a scripted player is WALKING to, and the commitment to keep walking there (order 9).
 *
 * <p>Without commitment a policy re-decides its destination every single turn, and two neighbouring
 * tiles that prefer different destinations pass the marine back and forth until the floor's turn cap
 * runs out. Humans do not play that way: you pick the medkit across the room, you go get it, and you
 * only change your mind when something happens. This holds exactly that much memory — one goal tile,
 * a progress check, and a per-floor list of goals that turned out to be a waste of time.
 *
 * <p>One instance per policy; it notices a floor change on its own (the depth in the view) and
 * forgets everything from the previous floor.
 */
final class TravelPlanner {

    /** No goal held. */
    private static final int NO_GOAL = -1;

    private int goalColumn = NO_GOAL;
    private int goalRow    = NO_GOAL;

    /** Turns spent on the current goal without the distance to it shrinking. */
    private int turnsWithoutProgress;
    private int lastDistanceToGoal = Integer.MAX_VALUE;

    /** Goals abandoned on this floor (unreachable, or never got closer). Cleared per floor. */
    private final Set<Long> abandonedGoals = new HashSet<>();
    private int lastSeenDepth = NO_GOAL;

    /**
     * One step along the way to wherever this policy is currently headed.
     *
     * @param collectSupplies whether the floor's ammo/medkits are worth a detour before the stairs
     * @return the movement button to press, or {@link TouchAction#NONE} when nowhere is reachable
     */
    TouchAction travel(SimView view, boolean collectSupplies, int[] stepBuffer) {
        forgetPreviousFloor(view);

        for (int attempt = 0; attempt <= 3; attempt++) {
            if (!holdsUsableGoal(view)) {
                chooseGoal(view, collectSupplies);
            }
            if (goalColumn == NO_GOAL) {
                // Everything worth walking to has been written off. Forget the blacklist and look
                // again rather than leaving the marine with nothing to do — a written-off goal is a
                // heuristic's guess, not a fact about the floor.
                if (abandonedGoals.isEmpty()) break;
                abandonedGoals.clear();
                continue;
            }

            TouchAction step = SimPolicySupport.stepToward(view, goalColumn, goalRow, stepBuffer);
            if (step != TouchAction.NONE) {
                trackProgress(view);
                return step;
            }
            // Reachable a moment ago but not steppable now (an enemy in the corridor, a door mid-
            // swing): drop the goal WITHOUT blacklisting it and try the next kind.
            interrupt();
        }
        return TouchAction.NONE;
    }

    /** Drops the current goal, e.g. because a fight started and the plan is stale afterwards. */
    void interrupt() {
        goalColumn = NO_GOAL;
        goalRow    = NO_GOAL;
        lastDistanceToGoal   = Integer.MAX_VALUE;
        turnsWithoutProgress = 0;
    }

    private void forgetPreviousFloor(SimView view) {
        if (view.depth() != lastSeenDepth) {
            lastSeenDepth = view.depth();
            abandonedGoals.clear();
            interrupt();
        }
    }

    /** True when the held goal is still somewhere worth walking to. */
    private boolean holdsUsableGoal(SimView view) {
        if (goalColumn == NO_GOAL) return false;
        if (goalColumn == view.playerTileColumn() && goalRow == view.playerTileRow()) return false;
        if (turnsWithoutProgress > BalanceConfig.SIM_GOAL_PATIENCE_TURNS) {
            abandonGoal();
            return false;
        }
        return true;
    }

    /**
     * Picks the next destination: supplies worth a detour, then the way out, then the keycard that
     * opens the way out. Reachability is checked at ADOPTION time and never cached as a verdict — the
     * stairs are usually locked behind a keycard door, so "unreachable" is a statement about right
     * now, not about the floor. Blacklisting is reserved for goals that stop making progress.
     */
    private void chooseGoal(SimView view, boolean collectSupplies) {
        if (collectSupplies && adopt(view, view.nearestSupplyTile())) return;
        if (adopt(view, view.exitTile())) return;
        adopt(view, view.nearestKeycardTile());
    }

    /** Adopts a candidate goal unless it is null, blacklisted, or not reachable from here. */
    private boolean adopt(SimView view, int[] candidate) {
        if (candidate == null) return false;
        if (abandonedGoals.contains(tileKey(candidate[0], candidate[1]))) return false;
        if (view.navigator() != null
                && !view.navigator().isReachable(view.playerTileColumn(), view.playerTileRow(),
                                                 candidate[0], candidate[1])) {
            return false;
        }
        goalColumn = candidate[0];
        goalRow    = candidate[1];
        lastDistanceToGoal   = Integer.MAX_VALUE;
        turnsWithoutProgress = 0;
        return true;
    }

    private void abandonGoal() {
        if (goalColumn != NO_GOAL) abandonedGoals.add(tileKey(goalColumn, goalRow));
        interrupt();
    }

    /**
     * Counts turns in which the marine failed to get any closer, so a hopeless goal is dropped.
     * Progress is measured along the PATH, not as the crow flies: walking around a wall increases
     * straight-line distance while genuinely making progress, and writing a goal off for that would
     * strand the marine on exactly the floors that need the most walking.
     */
    private void trackProgress(SimView view) {
        int distance = view.navigator() != null ? view.navigator().lastPathDistance()
                                                : Math.abs(goalColumn - view.playerTileColumn())
                                                  + Math.abs(goalRow - view.playerTileRow());
        if (distance < lastDistanceToGoal) {
            lastDistanceToGoal   = distance;
            turnsWithoutProgress = 0;
        } else {
            turnsWithoutProgress++;
        }
    }

    private static long tileKey(int tileColumn, int tileRow) {
        return ((long) tileColumn << 32) ^ (tileRow & 0xFFFFFFFFL);
    }
}
