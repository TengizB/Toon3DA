package ge.tbegvadze.toon3d.entity.boss;

import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.EnemyConstants;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.ArrayList;
import java.util.List;

/**
 * Overseer phase 1 — stationary laser lanes.
 *
 * Repeating four-step sequence:
 *   Step 0: TELEGRAPH row lane (every tile in the player's current row)
 *   Step 1: RESOLVE row lane — player must have stepped off the row
 *   Step 2: TELEGRAPH plus lane (player's row AND column simultaneously)
 *   Step 3: RESOLVE plus lane
 *
 * The Overseer does not move in phase 1. Cover columns ('P') interrupt lanes;
 * the player learns to fight from behind them.
 */
public final class OverseerPhase1Pattern implements BossAttackPattern {

    private int stepIndex = 0;
    private final List<DangerTileSet.MarkedTile> scratchTiles = new ArrayList<>();

    @Override
    public void onPhaseStart() {
        stepIndex = 0;
    }

    @Override
    public BossMove nextMove(Boss boss, Player player, Level level, long tickIndex) {
        int playerColumn = GameMath.worldToTile(player.positionX);
        int playerRow    = GameMath.worldToTile(player.positionY);

        BossMove move;
        switch (stepIndex % 4) {
            case 0:
                buildRowLane(scratchTiles, playerRow, level);
                boss.dangerTileSet.arm(new ArrayList<>(scratchTiles), EnemyConstants.OVERSEER_LASER_DAMAGE);
                move = BossMove.telegraph(EnemyConstants.OVERSEER_LASER_DAMAGE);
                break;
            case 1:
                boss.dangerTileSet.clear();
                move = BossMove.resolve();
                break;
            case 2:
                buildPlusLane(scratchTiles, playerColumn, playerRow, level);
                boss.dangerTileSet.arm(new ArrayList<>(scratchTiles), EnemyConstants.OVERSEER_LASER_DAMAGE);
                move = BossMove.telegraph(EnemyConstants.OVERSEER_LASER_DAMAGE);
                break;
            default:
                boss.dangerTileSet.clear();
                move = BossMove.resolve();
                break;
        }
        stepIndex++;
        return move;
    }

    private void buildRowLane(List<DangerTileSet.MarkedTile> out, int playerRow, Level level) {
        out.clear();
        for (int column = 0; column < level.getWidth(); column++) {
            if (!Level.isWall(level.getCell(column, playerRow))
                    && !Level.isColumn(level.getCell(column, playerRow))) {
                out.add(new DangerTileSet.MarkedTile(column, playerRow));
            }
        }
    }

    private void buildPlusLane(List<DangerTileSet.MarkedTile> out,
                               int playerColumn, int playerRow, Level level) {
        out.clear();
        for (int column = 0; column < level.getWidth(); column++) {
            if (!Level.isWall(level.getCell(column, playerRow))
                    && !Level.isColumn(level.getCell(column, playerRow))) {
                out.add(new DangerTileSet.MarkedTile(column, playerRow));
            }
        }
        for (int row = 0; row < level.getHeight(); row++) {
            if (row == playerRow) continue;
            if (!Level.isWall(level.getCell(playerColumn, row))
                    && !Level.isColumn(level.getCell(playerColumn, row))) {
                out.add(new DangerTileSet.MarkedTile(playerColumn, row));
            }
        }
    }
}
