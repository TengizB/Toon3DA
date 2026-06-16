package ge.tbegvadze.toon3d.entity.boss;

import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.EnemyConstants;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.ArrayList;
import java.util.List;

/**
 * Overseer phase 2 — ripped free, melee charge.
 *
 * Sequence (repeating every RAM_COOLDOWN ticks):
 *   Turns 0 … RAM_COOLDOWN-2: REPOSITION (step toward player)
 *   Turn  RAM_COOLDOWN-1:     TELEGRAPH ram — marks up to 3 tiles ahead
 *   (Next turn):              RESOLVE ram — tiles deal CHARGE_DAMAGE
 *
 * Smashing into a column stuns the Overseer for one tick (handled in BossFloorController).
 */
public final class OverseerPhase2Pattern implements BossAttackPattern {

    private int stepIndex = 0;
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
            return BossMove.resolve();
        }

        int positionInCycle = stepIndex % EnemyConstants.OVERSEER_RAM_COOLDOWN;
        stepIndex++;

        if (positionInCycle == EnemyConstants.OVERSEER_RAM_COOLDOWN - 1) {
            int playerColumn = GameMath.worldToTile(player.positionX);
            int playerRow    = GameMath.worldToTile(player.positionY);
            buildRamTiles(scratchTiles, boss, playerColumn, playerRow, level);
            boss.dangerTileSet.arm(new ArrayList<>(scratchTiles), EnemyConstants.OVERSEER_CHARGE_DAMAGE);
            pendingResolve = true;
            return BossMove.telegraph(EnemyConstants.OVERSEER_CHARGE_DAMAGE);
        }

        return BossMove.reposition();
    }

    private void buildRamTiles(List<DangerTileSet.MarkedTile> out,
                               Boss boss, int playerColumn, int playerRow, Level level) {
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
        for (int reach = 1; reach <= 3; reach++) {
            int targetColumn = boss.tileColumn + stepColumn * reach;
            int targetRow    = boss.tileRow    + stepRow    * reach;
            if (targetColumn < 0 || targetColumn >= level.getWidth()) break;
            if (targetRow    < 0 || targetRow    >= level.getHeight()) break;
            char cell = level.getCell(targetColumn, targetRow);
            if (Level.isWall(cell) || Level.isColumn(cell)) break;
            out.add(new DangerTileSet.MarkedTile(targetColumn, targetRow));
        }
    }
}
