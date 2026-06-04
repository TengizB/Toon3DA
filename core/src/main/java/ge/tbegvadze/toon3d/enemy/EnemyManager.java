package ge.tbegvadze.toon3d.enemy;

import ge.tbegvadze.toon3d.door.DoorManager;
import ge.tbegvadze.toon3d.entity.EnemyHitTarget;
import ge.tbegvadze.toon3d.entity.ImpactEventListener;
import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.level.EnemySpawnPoint;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Owns all enemy instances and drives the turn-based enemy simulation.
 *
 * Each call to takeTurn() runs three phases:
 *   A  — Perception: dormant enemies check proximity and LOS; newly alerted are queued.
 *   A2 — Chain alert: BFS propagation from newly-alerted seeds.
 *   B  — Action: every alerted enemy moves or attacks.
 *
 * Zero allocations inside takeTurn() or render() — all scratch arrays pre-allocated in constructor.
 * Implements EnemyHitTarget so Weapon.fire() can query and damage enemies.
 */
public final class EnemyManager implements EnemyHitTarget {

    private final List<Enemy> enemies;
    private final Level       level;
    private final DoorManager doorManager;
    private ImpactEventListener impactEventListener;

    // Pre-allocated scratch state — never re-allocated after construction
    private final boolean[][]  occupancy;           // [column][row] — true if an enemy is there this turn
    private final int[]        chainAlertQueue;     // BFS queue of enemy indices for chain-alert
    private final int[]        wiggleLegalColumns;  // reused in wiggleStep — avoids allocation in takeTurn
    private final int[]        wiggleLegalRows;
    private final Random       wiggleRandom;

    private boolean anyAlertedEver = false;

    public EnemyManager(Level level, DoorManager doorManager) {
        this.level       = level;
        this.doorManager = doorManager;
        this.enemies     = buildInitialEnemies(level.getEnemySpawnPoints());
        int levelWidth   = level.getWidth();
        int levelHeight  = level.getHeight();
        int enemyCount   = enemies.size();
        this.occupancy          = new boolean[levelWidth][levelHeight];
        this.chainAlertQueue    = new int[Math.max(1, enemyCount)];
        this.wiggleLegalColumns = new int[4];
        this.wiggleLegalRows    = new int[4];
        this.wiggleRandom       = new Random(12345L);
    }

    private static List<Enemy> buildInitialEnemies(List<EnemySpawnPoint> spawnPoints) {
        List<Enemy> list = new ArrayList<>(spawnPoints.size());
        for (EnemySpawnPoint spawnPoint : spawnPoints) {
            EnemyType type;
            switch (spawnPoint.spawnChar) {
                case '1': type = EnemyType.CORRUPTOR;  break;
                case '2': type = EnemyType.VORTEX_EYE; break;
                case '3': type = EnemyType.GHOUL;       break;
                case '4': type = EnemyType.CRAWLER;     break;
                case '5': type = EnemyType.REVENANT;    break;
                default:  type = EnemyType.CORRUPTOR;  break;
            }
            list.add(new Enemy(type, spawnPoint.tileColumn, spawnPoint.tileRow));
        }
        return list;
    }

    /** Wires the visual-effect system so every hit/kill fires cosmetic events. */
    public void setImpactEventListener(ImpactEventListener listener) {
        this.impactEventListener = listener;
    }

    /** Read-only view of the live enemy list; used by EnemyRenderer each frame. */
    public List<Enemy> getEnemies() {
        return enemies;
    }

    /** True as soon as any enemy has been alerted; drives World.gameState.redAlert. */
    public boolean anyAlerted() {
        return anyAlertedEver;
    }

    // -------------------------------------------------------------------------
    // EnemyHitTarget implementation — called by Weapon.fire()
    // -------------------------------------------------------------------------

    @Override
    public Object enemyAt(int tileColumn, int tileRow) {
        for (int index = 0; index < enemies.size(); index++) {
            Enemy enemy = enemies.get(index);
            if (enemy.isAlive() && enemy.tileColumn == tileColumn && enemy.tileRow == tileRow) {
                return enemy;
            }
        }
        return null;
    }

    @Override
    public void applyDamageTo(Object enemyObject, int amount) {
        Enemy enemy = (Enemy) enemyObject;
        // Snapshot world position before killEnemy() removes the enemy from the list
        float worldX          = enemy.worldCenterX();
        float worldY          = enemy.worldCenterY();
        float heightMultiplier = enemy.type.heightMultiplier();

        enemy.applyDamage(amount);
        enemy.triggerHitFlash();

        if (!enemy.isAlive()) {
            killEnemy(enemy);
            // Fire after killEnemy: enemy position/type are still valid (killEnemy only
            // mutates the manager's list and occupancy grid, not the Enemy object itself)
            if (impactEventListener != null) {
                impactEventListener.onEnemyKilled(worldX, worldY, heightMultiplier, amount);
            }
        } else {
            if (impactEventListener != null) {
                impactEventListener.onEnemyHit(worldX, worldY, heightMultiplier, amount);
            }
        }
    }

    /** Advances hit-flash timers for all living enemies. Call once per frame from World.update(). */
    public void advanceHitFlash(float deltaTime) {
        for (int index = 0; index < enemies.size(); index++) {
            enemies.get(index).advanceHitFlash(deltaTime);
        }
    }

    // -------------------------------------------------------------------------
    // Turn simulation
    // -------------------------------------------------------------------------

    public void takeTurn(int playerColumn, int playerRow, Player player) {
        rebuildOccupancy();
        phaseA(playerColumn, playerRow);
        phaseB(playerColumn, playerRow, player);
    }

    private void rebuildOccupancy() {
        int levelWidth  = level.getWidth();
        int levelHeight = level.getHeight();
        for (int column = 0; column < levelWidth; column++) {
            for (int row = 0; row < levelHeight; row++) {
                occupancy[column][row] = false;
            }
        }
        for (int index = 0; index < enemies.size(); index++) {
            Enemy enemy = enemies.get(index);
            if (enemy.isAlive()) {
                occupancy[enemy.tileColumn][enemy.tileRow] = true;
            }
        }
    }

    // Phase A: proximity + LOS wake, then chain-alert BFS
    private void phaseA(int playerColumn, int playerRow) {
        int chainQueueSize = 0;

        for (int index = 0; index < enemies.size(); index++) {
            Enemy enemy = enemies.get(index);
            if (!enemy.isAlive() || enemy.isAlerted()) continue;

            int chebyshev = GameMath.chebyshevDistanceTiles(
                    enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);

            boolean shouldAlert = false;
            if (chebyshev <= Constants.ALERT_RADIUS_TILES) {
                shouldAlert = true;
            } else if (chebyshev <= Constants.LOS_MAX_RANGE_TILES) {
                shouldAlert = hasLineOfSight(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);
            }

            if (shouldAlert) {
                enemy.alert();
                anyAlertedEver = true;
                chainAlertQueue[chainQueueSize++] = index;
            }
        }

        // BFS chain propagation
        int chainQueueHead = 0;
        while (chainQueueHead < chainQueueSize) {
            Enemy alerter = enemies.get(chainAlertQueue[chainQueueHead++]);
            for (int index = 0; index < enemies.size(); index++) {
                Enemy candidate = enemies.get(index);
                if (!candidate.isAlive() || candidate.isAlerted()) continue;
                int chainDist = GameMath.chebyshevDistanceTiles(
                        candidate.tileColumn, candidate.tileRow,
                        alerter.tileColumn,   alerter.tileRow);
                if (chainDist <= Constants.CHAIN_ALERT_RADIUS_TILES) {
                    candidate.alert();
                    anyAlertedEver = true;
                    chainAlertQueue[chainQueueSize++] = index;
                }
            }
        }
    }

    // Phase B: every alerted enemy acts once
    private void phaseB(int playerColumn, int playerRow, Player player) {
        for (int index = 0; index < enemies.size(); index++) {
            Enemy enemy = enemies.get(index);
            if (!enemy.isAlive() || !enemy.isAlerted()) continue;
            enemy.turnCounter++;
            actEnemy(enemy, playerColumn, playerRow, player);
        }
    }

    private void actEnemy(Enemy enemy, int playerColumn, int playerRow, Player player) {
        int chebyshev = GameMath.chebyshevDistanceTiles(
                enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);

        if (!enemy.type.isRanged()) {
            // Melee — attack only when player is in an adjacent cardinal tile (no diagonal)
            boolean cardinalAdjacent = GameMath.manhattanDistanceTiles(
                    enemy.tileColumn, enemy.tileRow, playerColumn, playerRow) == 1;
            if (cardinalAdjacent) {
                player.applyDamage(enemy.type.attackDamage());
                enemy.state = EnemyState.ATTACKING;
            } else {
                if (enemy.shouldMoveThisTurn()) {
                    stepToward(enemy, playerColumn, playerRow);
                }
                enemy.state = EnemyState.CHASING;
            }
        } else {
            // VORTEX EYE — ranged kiting
            actVortexEye(enemy, playerColumn, playerRow, player, chebyshev);
        }
    }

    private void actVortexEye(Enemy enemy, int playerColumn, int playerRow, Player player, int distanceToPlayer) {
        boolean hasLOS = hasLineOfSight(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);

        if (distanceToPlayer < Constants.VORTEX_EYE_KITE_MIN_TILES) {
            // Too close — flee first, then re-evaluate
            stepAway(enemy, playerColumn, playerRow);
            hasLOS = hasLineOfSight(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);
            distanceToPlayer = GameMath.chebyshevDistanceTiles(
                    enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);
        } else if (distanceToPlayer <= Constants.VORTEX_EYE_RANGE_TILES && hasLOS
                && isSameCardinalLine(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow)) {
            // Perfect kiting range on a cardinal line — hold position and fire; no movement
        } else {
            // Too far, LOS blocked, or not on a cardinal line — advance toward player
            if (enemy.shouldMoveThisTurn()) {
                stepToward(enemy, playerColumn, playerRow);
            }
            hasLOS = hasLineOfSight(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);
            distanceToPlayer = GameMath.chebyshevDistanceTiles(
                    enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);
        }

        boolean canFire = distanceToPlayer <= Constants.VORTEX_EYE_RANGE_TILES
                && hasLOS
                && isSameCardinalLine(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow)
                && !hasEnemyBlockingShot(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);
        if (canFire) {
            player.applyDamage(enemy.type.attackDamage());
            enemy.state = EnemyState.ATTACKING;
        } else {
            enemy.state = EnemyState.CHASING;
        }
    }

    private static boolean isSameCardinalLine(int column1, int row1, int column2, int row2) {
        return column1 == column2 || row1 == row2;
    }

    private boolean hasEnemyBlockingShot(int fromColumn, int fromRow, int toColumn, int toRow) {
        if (fromColumn == toColumn) {
            int minRow = Math.min(fromRow, toRow) + 1;
            int maxRow = Math.max(fromRow, toRow) - 1;
            for (int row = minRow; row <= maxRow; row++) {
                if (occupancy[fromColumn][row]) return true;
            }
        } else {
            int minColumn = Math.min(fromColumn, toColumn) + 1;
            int maxColumn = Math.max(fromColumn, toColumn) - 1;
            for (int column = minColumn; column <= maxColumn; column++) {
                if (occupancy[column][fromRow]) return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Movement helpers
    // -------------------------------------------------------------------------

    // Cardinal direction offsets: East, West, North, South
    private static final int[] STEP_COLUMNS = {  1, -1,  0,  0 };
    private static final int[] STEP_ROWS    = {  0,  0,  1, -1 };

    private void stepToward(Enemy enemy, int playerColumn, int playerRow) {
        int bestColumn   = enemy.tileColumn;
        int bestRow      = enemy.tileRow;
        int bestDistance = Integer.MAX_VALUE;
        boolean moved    = false;

        for (int directionIndex = 0; directionIndex < 4; directionIndex++) {
            int targetColumn = enemy.tileColumn + STEP_COLUMNS[directionIndex];
            int targetRow    = enemy.tileRow    + STEP_ROWS[directionIndex];
            if (!isPassableForEnemy(targetColumn, targetRow, playerColumn, playerRow)) continue;
            int distance = GameMath.manhattanDistanceTiles(targetColumn, targetRow, playerColumn, playerRow);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestColumn   = targetColumn;
                bestRow      = targetRow;
                moved        = true;
            }
        }

        if (!moved && Constants.ENEMY_GREEDY_WIGGLE_ENABLED
                && enemy.stuckTurns >= Constants.STUCK_TURNS_BEFORE_WIGGLE) {
            // Greedy is stuck — try a random legal cardinal step to escape a concave wall
            wiggleStep(enemy, playerColumn, playerRow);
            enemy.stuckTurns = 0;
            return;
        }

        if (moved) {
            int previousManhattan = GameMath.manhattanDistanceTiles(
                    enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);
            if (bestDistance >= previousManhattan) {
                enemy.stuckTurns++;
            } else {
                enemy.stuckTurns = 0;
            }
            commitMove(enemy, bestColumn, bestRow);
        } else {
            enemy.stuckTurns++;
        }
    }

    private void stepAway(Enemy enemy, int playerColumn, int playerRow) {
        int bestColumn   = enemy.tileColumn;
        int bestRow      = enemy.tileRow;
        int bestDistance = -1;
        boolean moved    = false;

        for (int directionIndex = 0; directionIndex < 4; directionIndex++) {
            int targetColumn = enemy.tileColumn + STEP_COLUMNS[directionIndex];
            int targetRow    = enemy.tileRow    + STEP_ROWS[directionIndex];
            if (!isPassableForEnemy(targetColumn, targetRow, playerColumn, playerRow)) continue;
            int distance = GameMath.manhattanDistanceTiles(targetColumn, targetRow, playerColumn, playerRow);
            if (distance > bestDistance) {
                bestDistance = distance;
                bestColumn   = targetColumn;
                bestRow      = targetRow;
                moved        = true;
            }
        }

        if (moved) {
            commitMove(enemy, bestColumn, bestRow);
        }
    }

    private void wiggleStep(Enemy enemy, int playerColumn, int playerRow) {
        // Collect all legal cardinal steps into pre-allocated arrays, pick one at random
        int legalCount = 0;
        for (int directionIndex = 0; directionIndex < 4; directionIndex++) {
            int targetColumn = enemy.tileColumn + STEP_COLUMNS[directionIndex];
            int targetRow    = enemy.tileRow    + STEP_ROWS[directionIndex];
            if (isPassableForEnemy(targetColumn, targetRow, playerColumn, playerRow)) {
                wiggleLegalColumns[legalCount] = targetColumn;
                wiggleLegalRows[legalCount]    = targetRow;
                legalCount++;
            }
        }
        if (legalCount > 0) {
            int chosenIndex = wiggleRandom.nextInt(legalCount);
            commitMove(enemy, wiggleLegalColumns[chosenIndex], wiggleLegalRows[chosenIndex]);
        }
    }

    private void commitMove(Enemy enemy, int targetColumn, int targetRow) {
        occupancy[enemy.tileColumn][enemy.tileRow] = false;
        enemy.tileColumn = targetColumn;
        enemy.tileRow    = targetRow;
        occupancy[targetColumn][targetRow] = true;
    }

    /** Returns true if any live enemy currently occupies the given tile. Safe to call at any time. */
    public boolean isTileOccupiedByEnemy(int tileColumn, int tileRow) {
        for (int enemyIndex = 0; enemyIndex < enemies.size(); enemyIndex++) {
            Enemy enemy = enemies.get(enemyIndex);
            if (enemy.tileColumn == tileColumn && enemy.tileRow == tileRow) return true;
        }
        return false;
    }

    private boolean isPassableForEnemy(int targetColumn, int targetRow, int playerColumn, int playerRow) {
        if (level.isBlockedAt(targetColumn, targetRow, doorManager)) return false;
        if (occupancy[targetColumn][targetRow]) return false;
        if (targetColumn == playerColumn && targetRow == playerRow) return false;
        return true;
    }

    // -------------------------------------------------------------------------
    // LOS
    // -------------------------------------------------------------------------

    private boolean hasLineOfSight(int fromColumn, int fromRow, int toColumn, int toRow) {
        int chebyshev = GameMath.chebyshevDistanceTiles(fromColumn, fromRow, toColumn, toRow);
        if (chebyshev > Constants.LOS_MAX_RANGE_TILES) return false;
        return GameMath.tileLineOfSightClear(fromColumn, fromRow, toColumn, toRow,
                (column, row) -> {
                    char cell = level.getCell(column, row);
                    return Level.isWall(cell)
                            || doorManager.blocksSight(column, row)
                            || Level.isPropSolid(cell);
                });
    }

    // -------------------------------------------------------------------------
    // Enemy death
    // -------------------------------------------------------------------------

    private void killEnemy(Enemy enemy) {
        occupancy[enemy.tileColumn][enemy.tileRow] = false;
        // Stamp a corpse decal onto the level grid so PropRenderer renders it
        level.setCell(enemy.tileColumn, enemy.tileRow, 'm');
        enemies.remove(enemy);
    }
}
