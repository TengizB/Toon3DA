package ge.tbegvadze.toon3d.entity.boss;

import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.EnemyConstants;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.ArrayList;
import java.util.List;

/**
 * Hell Baron phase 1 — slow advance, firewall, cleave.
 *
 * Cadence:
 *   - Repositions every other turn (heavy — slower than the player).
 *   - Every FIREWALL_COOLDOWN_P1 turns: TELEGRAPH a full wall-of-fire row or column
 *     chosen to cut the arena and herd the player → next tick: RESOLVE.
 *   - If the player is adjacent (Manhattan distance 1) in any turn: TELEGRAPH cleave
 *     (reach 1 tile forward) → next tick: RESOLVE with CLEAVE_DAMAGE_P1.
 *   - Cleave takes priority over firewall when both conditions would fire.
 */
public final class HellBaronPhase1Pattern implements BossAttackPattern {

    private int     stepIndex      = 0;
    private boolean pendingResolve = false;
    private final List<DangerTileSet.MarkedTile> scratchTiles = new ArrayList<>();

    @Override
    public void onPhaseStart() {
        stepIndex      = 0;
        pendingResolve = false;
    }

    @Override
    public BossMove nextMove(Boss boss, Player player, Level level, long tickIndex) {
        if (pendingResolve) {
            pendingResolve = false;
            boss.dangerTileSet.clear();
            stepIndex++;
            return BossMove.resolve();
        }

        int playerColumn = GameMath.worldToTile(player.positionX);
        int playerRow    = GameMath.worldToTile(player.positionY);
        int manhattan    = GameMath.manhattanDistanceTiles(
                boss.tileColumn, boss.tileRow, playerColumn, playerRow);

        BossMove move;

        if (manhattan == 1) {
            buildCleaveTiles(scratchTiles, boss, playerColumn, playerRow, level, 1);
            int cleaveDamage = boss.verbDamage(EnemyConstants.HELL_BARON_CLEAVE_P1_DPT_FRACTION);
            boss.dangerTileSet.arm(new ArrayList<>(scratchTiles), cleaveDamage);
            pendingResolve = true;
            move = BossMove.telegraph(cleaveDamage);
        } else if (stepIndex > 0 && stepIndex % EnemyConstants.HELL_BARON_FIREWALL_COOLDOWN_P1 == 0) {
            buildFirewall(scratchTiles, boss, playerColumn, playerRow, level);
            int fireDamage = boss.verbDamage(EnemyConstants.HELL_BARON_FIRE_DPT_FRACTION);
            boss.dangerTileSet.arm(new ArrayList<>(scratchTiles), fireDamage);
            pendingResolve = true;
            move = BossMove.telegraph(fireDamage);
        } else if (stepIndex % 2 == 0) {
            move = BossMove.reposition();
        } else {
            move = BossMove.none();
        }

        stepIndex++;
        return move;
    }

    private void buildCleaveTiles(List<DangerTileSet.MarkedTile> out,
                                  Boss boss, int playerColumn, int playerRow,
                                  Level level, int reach) {
        out.clear();
        int differenceColumn = playerColumn - boss.tileColumn;
        int differenceRow    = playerRow    - boss.tileRow;
        int stepColumn = Integer.signum(differenceColumn);
        int stepRow    = Integer.signum(differenceRow);
        if (Math.abs(differenceColumn) >= Math.abs(differenceRow)) {
            stepRow = 0;
        } else {
            stepColumn = 0;
        }
        for (int distance = 1; distance <= reach; distance++) {
            int targetColumn = boss.tileColumn + stepColumn * distance;
            int targetRow    = boss.tileRow    + stepRow    * distance;
            if (targetColumn < 0 || targetColumn >= level.getWidth()) break;
            if (targetRow    < 0 || targetRow    >= level.getHeight()) break;
            char cell = level.getCell(targetColumn, targetRow);
            if (Level.isWall(cell) || Level.isColumn(cell)) break;
            out.add(new DangerTileSet.MarkedTile(targetColumn, targetRow));
        }
    }

    private void buildFirewall(List<DangerTileSet.MarkedTile> out,
                               Boss boss, int playerColumn, int playerRow, Level level) {
        out.clear();
        int midColumn = (boss.tileColumn + playerColumn) / 2;
        int midRow    = (boss.tileRow    + playerRow)    / 2;
        boolean usesRow = Math.abs(boss.tileColumn - playerColumn)
                        < Math.abs(boss.tileRow    - playerRow);
        if (usesRow) {
            for (int column = 0; column < level.getWidth(); column++) {
                char cell = level.getCell(column, midRow);
                if (!Level.isWall(cell) && !Level.isColumn(cell)) {
                    out.add(new DangerTileSet.MarkedTile(column, midRow));
                }
            }
        } else {
            for (int row = 0; row < level.getHeight(); row++) {
                char cell = level.getCell(midColumn, row);
                if (!Level.isWall(cell) && !Level.isColumn(cell)) {
                    out.add(new DangerTileSet.MarkedTile(midColumn, row));
                }
            }
        }
    }
}
