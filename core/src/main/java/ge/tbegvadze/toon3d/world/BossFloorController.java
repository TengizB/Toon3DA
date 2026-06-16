package ge.tbegvadze.toon3d.world;

import ge.tbegvadze.toon3d.door.DoorManager;
import ge.tbegvadze.toon3d.enemy.EnemyManager;
import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.entity.boss.Boss;
import ge.tbegvadze.toon3d.entity.boss.BossMove;
import ge.tbegvadze.toon3d.entity.boss.DangerTileSet;
import ge.tbegvadze.toon3d.level.BossArenaGenerator;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.render.BossHudRenderer;
import ge.tbegvadze.toon3d.render.EventTextSystem;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.EnemyConstants;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.List;

/**
 * TickSubscriber that drives the boss encounter for one dungeon floor.
 *
 * Responsibilities per tick:
 *   1. Awaken check — once the player enters BOSS_AWAKEN_RADIUS_TILES, lock the arena door
 *      and start the intro sequence.
 *   2. Boss AI — delegate to Boss.activePattern().nextMove(); execute the resulting BossMove.
 *   3. Phase transition — when HP crosses the 50% threshold, trigger phase 2 banner and
 *      one-tick invulnerability.
 *   4. Death check — unlock arena door, re-enable the exit, fire kill events, hide HUD.
 *
 * The BossFloorController is subscribed to the TickEventBus before EnemyTurnSubscriber so
 * the boss acts first each turn.
 */
public final class BossFloorController implements TickSubscriber {

    // Cardinal step offsets for boss repositioning (N, S, E, W)
    private static final int[] STEP_COLUMNS = {  0,  0,  1, -1 };
    private static final int[] STEP_ROWS    = {  1, -1,  0,  0 };

    private final Boss           boss;
    private final Level          level;
    private final DoorManager    doorManager;
    private final EnemyManager   enemyManager;
    private final BossHudRenderer bossHudRenderer;
    private final EventTextSystem eventTextSystem;

    // Arena door position — set from BossArenaGenerator constants
    private final int arenaDoorColumn = BossArenaGenerator.ARENA_DOOR_COLUMN;
    private final int arenaDoorRow    = BossArenaGenerator.ARENA_DOOR_ROW;

    // Exit tile position — disabled until boss dies
    private final int exitColumn = BossArenaGenerator.getExitColumn();
    private final int exitRow    = BossArenaGenerator.getExitRow();

    private boolean bossDefeated       = false;
    private boolean phase2Triggered    = false;
    private int     invulnerableTurnsLeft = 0;

    public BossFloorController(Boss boss, Level level, DoorManager doorManager,
                               EnemyManager enemyManager, BossHudRenderer bossHudRenderer,
                               EventTextSystem eventTextSystem) {
        this.boss            = boss;
        this.level           = level;
        this.doorManager     = doorManager;
        this.enemyManager    = enemyManager;
        this.bossHudRenderer = bossHudRenderer;
        this.eventTextSystem = eventTextSystem;

        // Block the exit until the boss is defeated
        level.setCell(exitColumn, exitRow, 'x');
    }

    @Override
    public void onTick(TickContext context) {
        if (bossDefeated) return;

        Player player         = context.getPlayer();
        int    playerColumn   = context.getPlayerTileColumn();
        int    playerRow      = context.getPlayerTileRow();
        long   tickIndex      = context.getTickIndex();

        // Awaken check — triggers once when the player first enters range
        if (!boss.hasAwakened) {
            int distance = GameMath.chebyshevDistanceTiles(
                    boss.tileColumn, boss.tileRow, playerColumn, playerRow);
            if (distance <= Constants.BOSS_AWAKEN_RADIUS_TILES) {
                awakeBoss();
            } else {
                return; // player hasn't entered the arena yet
            }
        }

        // Phase-transition invulnerability countdown
        if (invulnerableTurnsLeft > 0) {
            invulnerableTurnsLeft--;
            if (invulnerableTurnsLeft == 0) {
                boss.invulnerable = false;
            }
            return;
        }

        // Check for phase 2 transition
        if (!phase2Triggered && boss.isAtPhase2Threshold()) {
            triggerPhase2();
            return;
        }

        // Run boss AI pattern
        BossMove move = boss.activePattern().nextMove(boss, player, level, tickIndex);
        boss.ticksSinceAwaken++;

        executeBossMove(move, player, playerColumn, playerRow);

        // Death check (damage may have been applied by the player's weapon before this tick)
        if (!boss.isAlive()) {
            defeatBoss(player);
        }
    }

    private void awakeBoss() {
        boss.hasAwakened = true;
        boss.alert();
        doorManager.lockArenaDoor(arenaDoorColumn, arenaDoorRow);
        bossHudRenderer.showIntro();
        boss.phase1Pattern.onPhaseStart();
        if (eventTextSystem != null) {
            eventTextSystem.spawnWithColor(boss.bossName.toUpperCase(), EventTextSystem.COLOR_GREEN);
        }
    }

    private void triggerPhase2() {
        phase2Triggered       = true;
        boss.phase            = 2;
        boss.invulnerable     = true;
        invulnerableTurnsLeft = Constants.BOSS_PHASE_TRANSITION_TURNS;
        boss.phase2Pattern.onPhaseStart();
        bossHudRenderer.showBanner("PHASE 2");
        if (eventTextSystem != null) {
            eventTextSystem.spawnWithColor("ENRAGED!", EventTextSystem.COLOR_GREEN);
        }
    }

    private void executeBossMove(BossMove move, Player player,
                                 int playerColumn, int playerRow) {
        switch (move.kind) {
            case TELEGRAPH:
                // DangerTileSet already armed by the pattern; nothing more to do this tick
                break;

            case RESOLVE:
                resolveDangerTiles(player, playerColumn, playerRow);
                break;

            case REPOSITION:
                repositionBoss(playerColumn, playerRow);
                break;

            case SUMMON:
                summonMinions(move, playerColumn, playerRow);
                break;

            case MELEE:
                if (GameMath.manhattanDistanceTiles(
                        boss.tileColumn, boss.tileRow, playerColumn, playerRow) == 1) {
                    player.applyDamage(move.tileDamage);
                }
                break;

            case TRANSITION:
            case NONE:
            default:
                break;
        }
    }

    private void resolveDangerTiles(Player player, int playerColumn, int playerRow) {
        DangerTileSet dangerTileSet = boss.dangerTileSet;
        if (!dangerTileSet.isActive()) return;
        if (dangerTileSet.contains(playerColumn, playerRow)) {
            player.applyDamage(dangerTileSet.getDamage());
        }
    }

    private void repositionBoss(int playerColumn, int playerRow) {
        int bestColumn   = boss.tileColumn;
        int bestRow      = boss.tileRow;
        int bestDistance = GameMath.manhattanDistanceTiles(
                boss.tileColumn, boss.tileRow, playerColumn, playerRow);
        boolean moved    = false;

        for (int directionIndex = 0; directionIndex < 4; directionIndex++) {
            int targetColumn = boss.tileColumn + STEP_COLUMNS[directionIndex];
            int targetRow    = boss.tileRow    + STEP_ROWS[directionIndex];
            if (!isBossStepPassable(targetColumn, targetRow, playerColumn, playerRow)) continue;
            int distance = GameMath.manhattanDistanceTiles(targetColumn, targetRow, playerColumn, playerRow);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestColumn   = targetColumn;
                bestRow      = targetRow;
                moved        = true;
            }
        }

        if (moved) {
            enemyManager.tryPushEnemy(boss, bestColumn, bestRow);
        }
    }

    private boolean isBossStepPassable(int targetColumn, int targetRow,
                                       int playerColumn, int playerRow) {
        if (targetColumn < 0 || targetColumn >= level.getWidth())  return false;
        if (targetRow    < 0 || targetRow    >= level.getHeight()) return false;
        if (level.isBlockedAt(targetColumn, targetRow, doorManager)) return false;
        if (targetColumn == playerColumn && targetRow == playerRow) return false;
        if (enemyManager.isTileOccupiedByEnemy(targetColumn, targetRow)) return false;
        return true;
    }

    private void summonMinions(BossMove move, int playerColumn, int playerRow) {
        int minionsBefore = enemyManager.countLiveEnemies() - 1; // subtract the boss itself
        if (minionsBefore >= EnemyConstants.CORRUPTOR_MINION_CAP) return;

        int spawned = 0;
        for (int directionIndex = 0; directionIndex < 4 && spawned < move.summonCount; directionIndex++) {
            int targetColumn = boss.tileColumn + STEP_COLUMNS[directionIndex];
            int targetRow    = boss.tileRow    + STEP_ROWS[directionIndex];
            if (targetColumn == playerColumn && targetRow == playerRow) continue;
            if (level.isBlockedAt(targetColumn, targetRow, doorManager)) continue;
            if (enemyManager.isTileOccupiedByEnemy(targetColumn, targetRow)) continue;
            enemyManager.spawnEnemy(move.summonType, targetColumn, targetRow, boss.dungeonLevel);
            spawned++;
        }
    }

    private void defeatBoss(Player player) {
        bossDefeated = true;
        boss.dangerTileSet.clear();

        // Restore the exit stairs
        level.setCell(exitColumn, exitRow, '>');

        // Unlock the arena door
        doorManager.unlockArenaDoor(arenaDoorColumn, arenaDoorRow);

        // Hide the boss HUD overlay
        bossHudRenderer.hide();

        if (eventTextSystem != null) {
            eventTextSystem.spawnWithColor(boss.killLine, EventTextSystem.COLOR_GREEN);
        }
    }

    /** True after the boss has been defeated; used by World to skip further updates. */
    public boolean isBossDefeated() { return bossDefeated; }
}
