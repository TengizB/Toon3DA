package ge.tbegvadze.toon3d.enemy;

import ge.tbegvadze.toon3d.door.DoorManager;
import ge.tbegvadze.toon3d.entity.EnemyHitTarget;
import ge.tbegvadze.toon3d.entity.ImpactEventListener;
import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.level.EnemySpawnPoint;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.progression.KillEventListener;
import ge.tbegvadze.toon3d.progression.KillXpListener;
import ge.tbegvadze.toon3d.status.StatusEffectController;
import ge.tbegvadze.toon3d.status.StatusResistance;
import ge.tbegvadze.toon3d.status.StatusType;
import ge.tbegvadze.toon3d.util.EffectConstants;
import ge.tbegvadze.toon3d.util.EnemyConstants;
import ge.tbegvadze.toon3d.util.GameBalance;
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

    /** Notified whenever a drop tile is stamped onto the level grid after an enemy dies. */
    public interface DropPlacedListener {
        void onDropPlaced(int tileColumn, int tileRow, char dropChar);
    }

    private final List<Enemy> enemies;
    private final Level       level;
    private final DoorManager doorManager;
    private ImpactEventListener impactEventListener;
    private KillXpListener      killXpListener;
    private KillEventListener   killEventListener;
    private DropPlacedListener  dropPlacedListener;

    /** Flat damage bonus from player level-up DAMAGE_BOOST choices; added to every hit. */
    private int playerFlatDamageBonus = 0;

    // Pre-allocated scratch state — never re-allocated after construction
    private final boolean[][]  occupancy;           // [column][row] — true if an enemy is there this turn
    private final int[]        chainAlertQueue;     // BFS queue of enemy indices for chain-alert
    private final int[]        wiggleLegalColumns;  // reused in wiggleStep — avoids allocation in takeTurn
    private final int[]        wiggleLegalRows;
    private final Random       wiggleRandom;
    private final Random       dropRandom;

    private boolean anyAlertedEver = false;

    private StatusEffectController statusEffectController = null;
    private final Random effectRandom = new Random();

    /**
     * @param dungeonDepth current floor number (1-based); drives enemy health and damage scaling.
     *                     Pass 1 for the first floor.
     */
    public EnemyManager(Level level, DoorManager doorManager, int dungeonDepth) {
        this.level       = level;
        this.doorManager = doorManager;
        this.enemies     = buildInitialEnemies(level.getEnemySpawnPoints(), dungeonDepth, new Random());
        int levelWidth   = level.getWidth();
        int levelHeight  = level.getHeight();
        int enemyCount   = enemies.size();
        this.occupancy          = new boolean[levelWidth][levelHeight];
        this.chainAlertQueue    = new int[Math.max(1, enemyCount)];
        this.wiggleLegalColumns = new int[4];
        this.wiggleLegalRows    = new int[4];
        this.wiggleRandom       = new Random(12345L);
        this.dropRandom         = new Random();
    }

    /** Injects the status effect controller so ranged enemies can inflict DoT on the player. */
    public void setStatusEffectController(StatusEffectController controller) {
        this.statusEffectController = controller;
    }

    private static List<Enemy> buildInitialEnemies(List<EnemySpawnPoint> spawnPoints,
                                                    int dungeonDepth, Random spawnVariance) {
        List<Enemy> list = new ArrayList<>(spawnPoints.size());
        for (EnemySpawnPoint spawnPoint : spawnPoints) {
            EnemyType type;
            switch (spawnPoint.spawnChar) {
                case '1': type = EnemyType.PLAGUE_HULK;   break;
                case '2': type = EnemyType.EYE_TYRANT;    break;
                case '3': type = EnemyType.GORE_BITER;    break;
                case '4': type = EnemyType.SHELL_BRUTE;   break;
                case '5': type = EnemyType.MIRE_WRAITH;   break;
                case '!': type = EnemyType.IRON_STALKER;  break;
                case '$': type = EnemyType.ACID_DRONE;    break;
                case '^': type = EnemyType.VOID_SHROUD;   break;
                default:  type = EnemyType.PLAGUE_HULK;   break;
            }
            // Small chance to spawn a lower-level enemy for variety at higher depths.
            int effectiveDepth = dungeonDepth;
            if (dungeonDepth > 2) {
                float roll = spawnVariance.nextFloat();
                if (roll < 0.05f) {
                    effectiveDepth = Math.max(1, dungeonDepth - 2);
                } else if (roll < 0.15f) {
                    effectiveDepth = Math.max(1, dungeonDepth - 1);
                }
            }
            float healthScale = GameBalance.enemyHealthScaleForDepth(effectiveDepth);
            float damageScale = GameBalance.enemyDamageScaleForDepth(effectiveDepth);
            Enemy enemy = new Enemy(type, spawnPoint.tileColumn, spawnPoint.tileRow);
            int scaledHealth = Math.max(1, Math.round(type.maxHealth() * healthScale));
            enemy.maxHealth              = scaledHealth;
            enemy.health                 = scaledHealth;
            enemy.attackDamageMultiplier = damageScale;
            enemy.dungeonLevel           = effectiveDepth;
            enemy.nameTag                = type.displayName() + " LVL " + effectiveDepth;
            enemy.setStatusResistance(buildArchetypeResistance(type));
            list.add(enemy);
        }
        return list;
    }

    /*
     * Builds the per-archetype StatusResistance table.
     * Values sourced from roguelike_order_8 and roguelike_order_13 design docs:
     *   VOID_SHROUD  — fire-immune (shadow entity; fire slides off)
     *   MIRE_WRAITH  — poison-immune (saturated in acid; own toxin does nothing)
     *   ACID_DRONE   — poison-immune (mechanical; impervious to biological agents)
     *   SHELL_BRUTE  — stun duration halved (heavy armoured frame resists concussive effects)
     *   EYE_TYRANT   — half fire damage (demon-origin; partially acclimated to heat)
     */
    private static StatusResistance buildArchetypeResistance(EnemyType type) {
        switch (type) {
            case VOID_SHROUD:
                return StatusResistance.builder()
                        .immune(StatusType.BURNING)
                        .build();
            case MIRE_WRAITH:
                return StatusResistance.builder()
                        .immune(StatusType.POISONED)
                        .build();
            case ACID_DRONE:
                return StatusResistance.builder()
                        .immune(StatusType.POISONED)
                        .damageMultiplier(StatusType.BURNING, 0.5f)
                        .build();
            case SHELL_BRUTE:
                return StatusResistance.builder()
                        .durationMultiplier(StatusType.STUNNED, 0.5f)
                        .build();
            case EYE_TYRANT:
                return StatusResistance.builder()
                        .damageMultiplier(StatusType.BURNING, 0.5f)
                        .build();
            default:
                return StatusResistance.defaultResistance();
        }
    }

    /** Wires the visual-effect system so every hit/kill fires cosmetic events. */
    public void setImpactEventListener(ImpactEventListener listener) {
        this.impactEventListener = listener;
    }

    /** Wires the XP system so every kill awards experience to the player. */
    public void setKillXpListener(KillXpListener listener) {
        this.killXpListener = listener;
    }

    /** Wires the kill-message system so every kill fires a display notification with name + XP. */
    public void setKillEventListener(KillEventListener listener) {
        this.killEventListener = listener;
    }

    /** Notified when a drop tile is stamped on the grid so renderers can display it immediately. */
    public void setDropPlacedListener(DropPlacedListener listener) {
        this.dropPlacedListener = listener;
    }

    /**
     * Sets the flat damage bonus from player progression.  Added to every weapon shot
     * that hits an enemy.  Call after each level-up reward and after each floor rebuild.
     */
    public void setPlayerFlatDamageBonus(int bonus) {
        this.playerFlatDamageBonus = bonus;
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
        float worldX           = enemy.worldCenterX();
        float worldY           = enemy.worldCenterY();
        float heightMultiplier = enemy.type.heightMultiplier();

        int totalDamage = amount + playerFlatDamageBonus;
        enemy.applyDamage(totalDamage);
        enemy.triggerHitFlash();

        if (!enemy.isAlive()) {
            int xpAwarded = enemy.type.baseXpReward();
            if (killXpListener != null) {
                killXpListener.onEnemyKilledForXp(xpAwarded);
            }
            if (killEventListener != null) {
                killEventListener.onEnemyKilled(enemy.nameTag, xpAwarded);
            }
            killEnemy(enemy);
            if (impactEventListener != null) {
                impactEventListener.onEnemyKilled(worldX, worldY, heightMultiplier, totalDamage);
            }
        } else {
            if (impactEventListener != null) {
                impactEventListener.onEnemyHit(worldX, worldY, heightMultiplier, totalDamage);
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
            if (chebyshev <= EnemyConstants.ALERT_RADIUS_TILES) {
                shouldAlert = true;
            } else if (chebyshev <= EnemyConstants.LOS_MAX_RANGE_TILES) {
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
                if (chainDist <= EnemyConstants.CHAIN_ALERT_RADIUS_TILES) {
                    candidate.alert();
                    anyAlertedEver = true;
                    chainAlertQueue[chainQueueSize++] = index;
                }
            }
        }
    }

    // Phase B: every alerted enemy acts once (stunned enemies skip their action this turn)
    private void phaseB(int playerColumn, int playerRow, Player player) {
        for (int index = 0; index < enemies.size(); index++) {
            Enemy enemy = enemies.get(index);
            if (!enemy.isAlive() || !enemy.isAlerted()) continue;
            if (enemy.skipNextAction) {
                enemy.skipNextAction = false;
                enemy.turnCounter++;
                continue;
            }
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
                player.applyDamage(enemy.scaledAttackDamage());
                enemy.state = EnemyState.ATTACKING;
            } else {
                if (enemy.shouldMoveThisTurn()) {
                    stepToward(enemy, playerColumn, playerRow);
                }
                enemy.state = EnemyState.CHASING;
            }
        } else {
            // Ranged kiting — used by EYE_TYRANT, MIRE_WRAITH, ACID_DRONE
            actRangedEnemy(enemy, playerColumn, playerRow, player, chebyshev);
        }
    }

    private void actRangedEnemy(Enemy enemy, int playerColumn, int playerRow, Player player, int distanceToPlayer) {
        int rangeLimit = enemy.type.attackRangeTiles();
        boolean hasLOS = hasLineOfSight(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);

        if (distanceToPlayer < EnemyConstants.RANGED_KITE_MIN_TILES) {
            // Too close — flee first, then re-evaluate
            stepAway(enemy, playerColumn, playerRow);
            hasLOS = hasLineOfSight(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);
            distanceToPlayer = GameMath.chebyshevDistanceTiles(
                    enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);
        } else if (distanceToPlayer <= rangeLimit && hasLOS
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

        boolean canFire = distanceToPlayer <= rangeLimit
                && hasLOS
                && isSameCardinalLine(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow)
                && !hasEnemyBlockingShot(enemy.tileColumn, enemy.tileRow, playerColumn, playerRow);
        if (canFire) {
            player.applyDamage(enemy.scaledAttackDamage());
            enemy.state = EnemyState.ATTACKING;
            applyRangedAttackStatusEffect(enemy, player);
        } else {
            enemy.state = EnemyState.CHASING;
        }
    }

    private void applyRangedAttackStatusEffect(Enemy enemy, Player player) {
        if (statusEffectController == null) return;
        float poisonChance;
        switch (enemy.type) {
            case MIRE_WRAITH: poisonChance = EffectConstants.MIRE_WRAITH_POISON_CHANCE; break;
            case ACID_DRONE:  poisonChance = EffectConstants.ACID_DRONE_POISON_CHANCE;  break;
            default: return;
        }
        if (effectRandom.nextFloat() < poisonChance) {
            statusEffectController.apply(player, StatusType.POISONED,
                    EffectConstants.POISON_DURATION,
                    EffectConstants.POISON_DAMAGE_PER_STACK,
                    enemy);
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

        if (!moved && EnemyConstants.ENEMY_GREEDY_WIGGLE_ENABLED
                && enemy.stuckTurns >= EnemyConstants.STUCK_TURNS_BEFORE_WIGGLE) {
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
        if (chebyshev > EnemyConstants.LOS_MAX_RANGE_TILES) return false;
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

    /**
     * Handles an enemy death delivered by a status effect DoT tick (Burning / Poison).
     * Awards XP to the player and stamps the corpse decal / drop, same as a weapon kill.
     * Called by StatusEffectController after it finishes iterating enemies for the turn,
     * so the enemy list is safe to modify (no ConcurrentModificationException risk).
     */
    public void processDoTKill(Enemy enemy) {
        int xpAwarded = enemy.type.baseXpReward();
        if (killXpListener   != null) killXpListener.onEnemyKilledForXp(xpAwarded);
        if (killEventListener != null) killEventListener.onEnemyKilled(enemy.nameTag, xpAwarded);
        killEnemy(enemy);
    }

    private void killEnemy(Enemy enemy) {
        occupancy[enemy.tileColumn][enemy.tileRow] = false;
        char currentCell = level.getCell(enemy.tileColumn, enemy.tileRow);
        if (!Level.isStairsDown(currentCell)
                && !Level.isMedicalPickup(currentCell)
                && !Level.isArmourPickup(currentCell)
                && !Level.isKeycardPickup(currentCell)
                && !Level.isAmmoPickup(currentCell)) {
            char drop = rollEnemyDrop(enemy.type);
            level.setCell(enemy.tileColumn, enemy.tileRow, drop);
            if (dropPlacedListener != null) {
                dropPlacedListener.onDropPlaced(enemy.tileColumn, enemy.tileRow, drop);
            }
        }
        enemies.remove(enemy);
    }

    private char rollEnemyDrop(EnemyType type) {
        if (dropRandom.nextFloat() >= EnemyConstants.ENEMY_AMMO_DROP_CHANCE) {
            return 'm'; // corpse decal, no item
        }
        switch (type) {
            case PLAGUE_HULK:  return '6'; // bullets — basic melee tank
            case EYE_TYRANT:   return '8'; // cells   — energy-based ranged
            case GORE_BITER:   return '7'; // shells  — brawler
            case SHELL_BRUTE:  return '6'; // bullets — heavy charger
            case MIRE_WRAITH:  return '8'; // cells   — acid ranged
            case IRON_STALKER: return '6'; // bullets — armored elite
            case ACID_DRONE:   return '8'; // cells   — mechanical ranged
            case VOID_SHROUD:  return '6'; // bullets — stealth melee
            default:           return '6';
        }
    }
}
