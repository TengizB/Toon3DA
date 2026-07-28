package ge.tbegvadze.toon3d.sim;

import java.util.Random;

import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.entity.Weapon;
import ge.tbegvadze.toon3d.input.touch.TouchAction;
import ge.tbegvadze.toon3d.util.BalanceConfig;

/**
 * The moves every scripted policy shares (new-game-balancr order 9): walk there, face that, shoot
 * this. Kept in one place so NAIVE and TACTICAL differ only in JUDGEMENT — when to fight, when to
 * back off, when to spend a resource — and never in mechanical competence.
 *
 * <p>Nothing here decides a game rule; each helper ends in a {@link TouchAction} the real
 * {@code PlayerController} is free to accept or reject.
 */
final class SimPolicySupport {

    private SimPolicySupport() {}

    /** Cardinal steps, in a fixed order, so a tie always breaks the same way (determinism). */
    private static final int[] STEP_COLUMNS = { 1, -1, 0,  0 };
    private static final int[] STEP_ROWS    = { 0,  0, 1, -1 };

    /**
     * What to do with the gun when a target is lined up. The order matters, and one case is easy to
     * get wrong: a weapon that is RELOADING cannot be told to reload again, and its reload only
     * advances on TICKS — so the right move is to spend a turn (which is exactly what tapping FIRE
     * mid-reload does in the real controller). Returning NONE means the gun is useless this turn and
     * the caller should switch weapons or close to melee.
     */
    static TouchAction serviceWeapon(SimView view, Weapon weapon) {
        if (weapon == null) return TouchAction.NONE;
        if (weapon.canFire()) return TouchAction.FIRE;
        if (weapon.isReloading()) return TouchAction.SKIP_TURN;
        if (weapon.getShotsInClip() < weapon.getEffectiveClipSize()
                && view.reserveAmmoForEquipped() > 0) {
            return TouchAction.RELOAD;
        }
        return TouchAction.NONE;
    }

    /**
     * The fight the marine is ALREADY in: something in the gun line, something adjacent, or an awake
     * shooter holding a clear line on it. Returns the button to press, or NONE when nothing here needs
     * answering this turn.
     *
     * <p>This is deliberately reactive. Walking across a floor toward a distant enemy is what makes a
     * scripted player dither (the ranking of "nearest" flips as it moves), and it is not how the game
     * is meant to be played either — you descend, and you answer what comes at you.
     */
    static TouchAction answerImmediateThreat(SimView view) {
        Weapon weapon = view.equippedWeapon();
        int    range  = weapon != null ? weapon.getEffectiveRange() : 1;

        // Something already lined up: shoot it, reload, or ride out a reload.
        if (weapon != null && view.enemyInFacingLine(range) != null) {
            TouchAction gunAction = serviceWeapon(view, weapon);
            if (gunAction != TouchAction.NONE) return gunAction;
        }

        // Otherwise turn to face the most pressing threat: whatever is adjacent, else an awake enemy
        // holding a clear line inside weapon range. One rotation, then the branch above fires.
        Enemy pressing = mostPressingThreat(view, range);
        if (pressing != null) {
            int differenceColumn = pressing.tileColumn - view.playerTileColumn();
            int differenceRow    = pressing.tileRow    - view.playerTileRow();
            TouchAction turn = SimNavigator.actionToFace(view.facingStepColumn(), view.facingStepRow(),
                                                         Integer.signum(differenceColumn),
                                                         Integer.signum(differenceRow));
            if (turn != TouchAction.NONE) return turn;
            // Already facing it but the line is blocked or the gun is empty — nothing to do here.
        }
        return TouchAction.NONE;
    }

    /** The adjacent enemy, else the closest awake enemy on a clear cardinal line inside range. */
    private static Enemy mostPressingThreat(SimView view, int range) {
        Enemy best = null;
        int   bestDistance = Integer.MAX_VALUE;
        for (Enemy enemy : view.enemies()) {
            if (!enemy.isAlive()) continue;
            int distance = view.tileDistanceTo(enemy);
            if (distance > Math.max(1, range) || distance >= bestDistance) continue;
            boolean adjacent = distance <= 1;
            boolean onLine   = (enemy.tileColumn == view.playerTileColumn()
                                || enemy.tileRow == view.playerTileRow())
                               && view.hasClearShotAt(enemy.tileColumn, enemy.tileRow);
            if (!adjacent && !(onLine && enemy.isAlerted())) continue;
            bestDistance = distance;
            best         = enemy;
        }
        return best;
    }

    /**
     * Closes on a target and attacks it: fire when it is already in the gun line, turn when it is
     * on a line the marine is not facing, otherwise walk one tile closer.
     */
    static TouchAction approachAndAttack(SimView view, Enemy target, int[] stepBuffer) {
        Weapon weapon = view.equippedWeapon();
        int range = weapon != null ? weapon.getEffectiveRange() : 1;

        // Already lined up — shoot, reload, or wait out a reload in progress.
        if (weapon != null && view.enemyInFacingLine(range) != null) {
            TouchAction gunAction = serviceWeapon(view, weapon);
            if (gunAction != TouchAction.NONE) return gunAction;
        }

        // On a shared row/column within range, with nothing in between — one rotation puts it in the
        // line. The clear-line test matters: without it a policy rotates toward a target it cannot
        // actually shoot, then walks, then rotates back, forever.
        int differenceColumn = target.tileColumn - view.playerTileColumn();
        int differenceRow    = target.tileRow    - view.playerTileRow();
        boolean sharesLine = (differenceColumn == 0 || differenceRow == 0);
        if (sharesLine && Math.max(Math.abs(differenceColumn), Math.abs(differenceRow)) <= range
                && view.hasClearShotAt(target.tileColumn, target.tileRow)) {
            int desiredStepColumn = Integer.signum(differenceColumn);
            int desiredStepRow    = Integer.signum(differenceRow);
            TouchAction turn = SimNavigator.actionToFace(view.facingStepColumn(), view.facingStepRow(),
                                                         desiredStepColumn, desiredStepRow);
            if (turn != TouchAction.NONE) return turn;
        }

        // Otherwise walk toward it.
        return stepToward(view, target.tileColumn, target.tileRow, stepBuffer);
    }

    /**
     * The enemy worth fighting right now: the closest living one inside the engage range that the
     * marine can actually GET TO or SHOOT. Filtering by reachability matters — a policy that fixates
     * on something behind a wall walks in circles instead of finishing the floor.
     */
    static Enemy reachableTarget(SimView view) {
        Enemy best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Enemy enemy : view.enemies()) {
            if (!enemy.isAlive()) continue;
            int distance = view.tileDistanceTo(enemy);
            if (distance > BalanceConfig.SIM_ENGAGE_RANGE_TILES || distance >= bestDistance) continue;
            boolean canEngage = view.hasClearShotAt(enemy.tileColumn, enemy.tileRow)
                    || (view.navigator() != null
                        && view.navigator().isReachable(view.playerTileColumn(), view.playerTileRow(),
                                                        enemy.tileColumn, enemy.tileRow));
            if (!canEngage) continue;
            bestDistance = distance;
            best         = enemy;
        }
        return best;
    }

    /** One step along the shortest walkable path to a tile, or NONE when it cannot be reached. */
    static TouchAction stepToward(SimView view, int targetColumn, int targetRow, int[] stepBuffer) {
        SimNavigator navigator = view.navigator();
        if (navigator == null) return TouchAction.NONE;
        boolean found = navigator.nextStepToward(view.playerTileColumn(), view.playerTileRow(),
                                                 targetColumn, targetRow,
                                                 view.enemyManager(), stepBuffer);
        if (!found) return TouchAction.NONE;
        return SimNavigator.actionForStep(view.facingStepColumn(), view.facingStepRow(),
                                          stepBuffer[0], stepBuffer[1]);
    }

    /**
     * One step along the way OUT: the stairs when they can be reached, otherwise the nearest keycard
     * (the way down is behind a locked door — going to find the key is the intended play). NONE when
     * neither can be reached.
     */
    static TouchAction stepTowardExit(SimView view, int[] stepBuffer) {
        int[] exitTile = view.exitTile();
        if (exitTile != null) {
            TouchAction toExit = stepToward(view, exitTile[0], exitTile[1], stepBuffer);
            if (toExit != TouchAction.NONE) return toExit;
        }
        int[] keycardTile = view.nearestKeycardTile();
        if (keycardTile != null) {
            return stepToward(view, keycardTile[0], keycardTile[1], stepBuffer);
        }
        return TouchAction.NONE;
    }

    /** One step toward the nearest ammo/medkit/armour pickup, or NONE when none can be reached. */
    static TouchAction stepTowardSupplies(SimView view, int[] stepBuffer) {
        int[] supplyTile = view.nearestSupplyTile();
        if (supplyTile == null) return TouchAction.NONE;
        return stepToward(view, supplyTile[0], supplyTile[1], stepBuffer);
    }

    /**
     * The fallback when no goal is reachable: try each cardinal step in a fixed order from a seeded
     * start so the marine keeps moving without ever becoming non-deterministic. Returns SKIP_TURN
     * when every neighbour is blocked (the world will still advance, which is what matters).
     */
    static TouchAction wander(SimView view, Random tieBreak) {
        int offset = tieBreak.nextInt(STEP_COLUMNS.length);
        for (int attempt = 0; attempt < STEP_COLUMNS.length; attempt++) {
            int direction         = (offset + attempt) % STEP_COLUMNS.length;
            int neighborColumn    = view.playerTileColumn() + STEP_COLUMNS[direction];
            int neighborRow       = view.playerTileRow()    + STEP_ROWS[direction];
            if (view.level().isBlockedAt(neighborColumn, neighborRow, view.doorManager())) continue;
            if (view.enemyManager().isTileOccupiedByEnemy(neighborColumn, neighborRow)) continue;
            return SimNavigator.actionForStep(view.facingStepColumn(), view.facingStepRow(),
                                              STEP_COLUMNS[direction], STEP_ROWS[direction]);
        }
        return TouchAction.SKIP_TURN;
    }

    /**
     * A step that BREAKS every shared row/column with a living ranged enemy — the counterplay the
     * cardinal-line constraint is built around. Returns NONE when no such step exists.
     */
    static TouchAction stepOffRangedLines(SimView view) {
        for (int direction = 0; direction < STEP_COLUMNS.length; direction++) {
            int neighborColumn = view.playerTileColumn() + STEP_COLUMNS[direction];
            int neighborRow    = view.playerTileRow()    + STEP_ROWS[direction];
            if (view.level().isBlockedAt(neighborColumn, neighborRow, view.doorManager())) continue;
            if (view.enemyManager().isTileOccupiedByEnemy(neighborColumn, neighborRow)) continue;
            if (view.rangedEnemyThreatensTile(neighborColumn, neighborRow)) continue;
            return SimNavigator.actionForStep(view.facingStepColumn(), view.facingStepRow(),
                                              STEP_COLUMNS[direction], STEP_ROWS[direction]);
        }
        return TouchAction.NONE;
    }

}
