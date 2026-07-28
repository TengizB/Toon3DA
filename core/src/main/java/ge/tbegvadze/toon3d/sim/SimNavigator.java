package ge.tbegvadze.toon3d.sim;

import java.util.ArrayDeque;

import ge.tbegvadze.toon3d.door.DoorManager;
import ge.tbegvadze.toon3d.enemy.EnemyManager;
import ge.tbegvadze.toon3d.input.touch.TouchAction;
import ge.tbegvadze.toon3d.level.Level;

/**
 * Tile-space pathing for the scripted policies (new-game-balancr order 9).
 *
 * <p>Navigation is NOT a game rule — the game has no auto-walk — so this lives in the simulator, not
 * in the gameplay packages. It answers one question: "standing here, which cardinal step gets me
 * closest to there?", using a breadth-first flood over the same passability the real
 * {@code PlayerController} enforces ({@link Level#isBlockedAt}), so a policy can never plan through
 * a wall the game would refuse to let it walk through.
 *
 * <p>One instance per floor; the flood arrays are allocated once and reused every query.
 */
final class SimNavigator {

    /**
     * Whether the marine may currently walk through a door tile — the lock-and-key question only the
     * run knows the answer to (does it hold the matching keycard?). Supplied by {@link SimWorld} so
     * the navigator stays free of inventory knowledge.
     */
    interface DoorPassabilityRule {
        boolean canPassDoor(int tileColumn, int tileRow);
    }

    /** Marks an unreached tile in the distance field. */
    private static final int UNREACHED = -1;

    /** Cardinal steps in a fixed order so the same map always yields the same path (determinism). */
    private static final int[] STEP_COLUMNS = { 1, -1, 0,  0 };
    private static final int[] STEP_ROWS    = { 0,  0, 1, -1 };

    private final Level       level;
    private final DoorManager doorManager;
    private final int         levelWidth;
    private final int         levelHeight;

    private final int[][]           distanceField;
    private final ArrayDeque<int[]> floodQueue = new ArrayDeque<>();

    /**
     * Tiles a move into was REFUSED by the game (a keycard door with no key, a blocker the level
     * data does not describe). The navigator learns them as the floor is played, so a policy can
     * never spend the rest of its turn budget walking into the same locked door. Cleared per floor
     * with the navigator itself.
     */
    private final boolean[][] refusedTiles;

    /**
     * Remaining PATH distance to the goal of the last {@link #nextStepToward} call. Walls make
     * straight-line distance a lie, so this is what "am I getting closer?" must be measured against.
     */
    private int lastPathDistance = Integer.MAX_VALUE;

    private final DoorPassabilityRule doorPassabilityRule;

    SimNavigator(Level level, DoorManager doorManager, DoorPassabilityRule doorPassabilityRule) {
        this.level               = level;
        this.doorManager         = doorManager;
        this.doorPassabilityRule = doorPassabilityRule;
        this.levelWidth    = level.getWidth();
        this.levelHeight   = level.getHeight();
        this.distanceField = new int[levelWidth][levelHeight];
        this.refusedTiles  = new boolean[levelWidth][levelHeight];
    }

    /**
     * The next cardinal step from ({@code fromColumn}, {@code fromRow}) along a shortest walkable
     * path to ({@code toColumn}, {@code toRow}), written into {@code stepOut} as {column, row}.
     * A step is chosen even when the goal tile itself is occupied by an enemy — the caller stops
     * adjacent and attacks.
     *
     * @return true when a step was found; false when the goal is unreachable (or already reached)
     */
    boolean nextStepToward(int fromColumn, int fromRow, int toColumn, int toRow,
                           EnemyManager enemyManager, int[] stepOut) {
        if (!inBounds(toColumn, toRow)) return false;
        if (fromColumn == toColumn && fromRow == toRow) return false;

        // Flood OUT FROM THE GOAL so the distance field can be read as "steps still to walk", and a
        // goal tile that is itself blocked (an enemy standing on it) still yields a useful gradient.
        floodFrom(toColumn, toRow, enemyManager);

        // Only steps that STRICTLY reduce the remaining distance are offered. Without this a policy
        // standing in a dead end can be handed a step that moves it away from the goal, and two such
        // tiles will pass the marine back and forth until the floor's turn cap runs out.
        int distanceHere   = inBounds(fromColumn, fromRow) ? distanceField[fromColumn][fromRow] : UNREACHED;
        int bestDistance   = distanceHere == UNREACHED ? Integer.MAX_VALUE : distanceHere;
        int bestStepColumn = 0;
        int bestStepRow    = 0;
        boolean found      = false;
        for (int direction = 0; direction < STEP_COLUMNS.length; direction++) {
            int neighborColumn = fromColumn + STEP_COLUMNS[direction];
            int neighborRow    = fromRow    + STEP_ROWS[direction];
            if (!inBounds(neighborColumn, neighborRow)) continue;
            if (isBlockedForWalking(neighborColumn, neighborRow, enemyManager)) continue;
            int distance = distanceField[neighborColumn][neighborRow];
            if (distance == UNREACHED) continue;
            if (distance < bestDistance) {
                bestDistance   = distance;
                bestStepColumn = STEP_COLUMNS[direction];
                bestStepRow    = STEP_ROWS[direction];
                found          = true;
            }
        }
        if (!found) return false;
        lastPathDistance = bestDistance + 1;   // one step from here to that neighbour, then its path
        stepOut[0] = bestStepColumn;
        stepOut[1] = bestStepRow;
        return true;
    }

    /** Remaining path distance to the goal of the last successful {@link #nextStepToward}. */
    int lastPathDistance() { return lastPathDistance; }

    /** Records that the game refused a step into this tile, so no later path plans through it. */
    void markRefused(int tileColumn, int tileRow) {
        if (inBounds(tileColumn, tileRow)) refusedTiles[tileColumn][tileRow] = true;
    }

    /**
     * True when a walkable path exists from one tile to another (ignoring enemies as blockers).
     *
     * <p>The marine's OWN tile is not required to be walkable — it can legitimately be standing on
     * something the walk rules treat as blocked (a hazard tile, a tile written off after a refused
     * step). What matters is whether any step out of it leads to the goal, so a neighbour with a
     * distance counts as reachable.
     */
    boolean isReachable(int fromColumn, int fromRow, int toColumn, int toRow) {
        if (!inBounds(fromColumn, fromRow) || !inBounds(toColumn, toRow)) return false;
        floodFrom(toColumn, toRow, null);
        if (distanceField[fromColumn][fromRow] != UNREACHED) return true;
        for (int direction = 0; direction < STEP_COLUMNS.length; direction++) {
            int neighborColumn = fromColumn + STEP_COLUMNS[direction];
            int neighborRow    = fromRow    + STEP_ROWS[direction];
            if (!inBounds(neighborColumn, neighborRow)) continue;
            if (distanceField[neighborColumn][neighborRow] != UNREACHED) return true;
        }
        return false;
    }

    /** Breadth-first flood of walk distances out from one tile. Enemies block when supplied. */
    private void floodFrom(int originColumn, int originRow, EnemyManager enemyManager) {
        for (int tileColumn = 0; tileColumn < levelWidth; tileColumn++) {
            java.util.Arrays.fill(distanceField[tileColumn], UNREACHED);
        }
        floodQueue.clear();
        distanceField[originColumn][originRow] = 0;
        floodQueue.add(new int[]{originColumn, originRow});
        while (!floodQueue.isEmpty()) {
            int[] tile      = floodQueue.poll();
            int   distance  = distanceField[tile[0]][tile[1]];
            for (int direction = 0; direction < STEP_COLUMNS.length; direction++) {
                int neighborColumn = tile[0] + STEP_COLUMNS[direction];
                int neighborRow    = tile[1] + STEP_ROWS[direction];
                if (!inBounds(neighborColumn, neighborRow)) continue;
                if (distanceField[neighborColumn][neighborRow] != UNREACHED) continue;
                if (isBlockedForWalking(neighborColumn, neighborRow, enemyManager)) continue;
                distanceField[neighborColumn][neighborRow] = distance + 1;
                floodQueue.add(new int[]{neighborColumn, neighborRow});
            }
        }
    }

    /**
     * Walk passability. A plain closed door is PASSABLE for planning — the controller opens it by
     * stepping into it (one extra turn), exactly as a player does. A LOCKED door is passable only
     * while the run holds its keycard, so a path never plans through a door the game will refuse.
     */
    private boolean isBlockedForWalking(int tileColumn, int tileRow, EnemyManager enemyManager) {
        if (refusedTiles[tileColumn][tileRow]) return true;
        char cell = level.getCell(tileColumn, tileRow);
        if (Level.isDoor(cell)) {
            return doorPassabilityRule != null && !doorPassabilityRule.canPassDoor(tileColumn, tileRow);
        }
        if (level.isBlockedAt(tileColumn, tileRow, doorManager)) return true;
        return enemyManager != null && enemyManager.isTileOccupiedByEnemy(tileColumn, tileRow);
    }

    private boolean inBounds(int tileColumn, int tileRow) {
        return tileColumn >= 0 && tileColumn < levelWidth && tileRow >= 0 && tileRow < levelHeight;
    }

    /**
     * Translates a desired cardinal STEP into the button a player would press, given the current
     * facing. Facing the step direction means walking FORWARD; otherwise the marine turns toward it
     * (one 90° rotation per turn, exactly like the real controller allows).
     */
    static TouchAction actionForStep(int facingStepColumn, int facingStepRow,
                                     int desiredStepColumn, int desiredStepRow) {
        if (desiredStepColumn == facingStepColumn && desiredStepRow == facingStepRow) {
            return TouchAction.FORWARD;
        }
        // The 90° CCW of the current facing is (-facingRow, facingColumn) — matching the strafe-left
        // vector in the movement contract. If the desired step is that way, turning left is one step.
        if (desiredStepColumn == -facingStepRow && desiredStepRow == facingStepColumn) {
            return TouchAction.ROTATE_LEFT;
        }
        if (desiredStepColumn == facingStepRow && desiredStepRow == -facingStepColumn) {
            return TouchAction.ROTATE_RIGHT;
        }
        // Directly behind: stepping BACK covers the tile without spending two turns rotating.
        return TouchAction.BACK;
    }

    /** The button that turns the marine to FACE the given cardinal direction, or NONE if already facing it. */
    static TouchAction actionToFace(int facingStepColumn, int facingStepRow,
                                    int desiredStepColumn, int desiredStepRow) {
        if (desiredStepColumn == facingStepColumn && desiredStepRow == facingStepRow) {
            return TouchAction.NONE;
        }
        if (desiredStepColumn == -facingStepRow && desiredStepRow == facingStepColumn) {
            return TouchAction.ROTATE_LEFT;
        }
        return TouchAction.ROTATE_RIGHT;
    }
}
