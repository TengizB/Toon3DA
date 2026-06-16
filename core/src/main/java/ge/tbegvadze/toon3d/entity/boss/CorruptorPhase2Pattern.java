package ge.tbegvadze.toon3d.entity.boss;

import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.EnemyConstants;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.ArrayList;
import java.util.List;

/**
 * Corruptor phase 2 — acid burst area denial.
 *
 * Alternating two-step sequence:
 *   Even turns: REPOSITION (still kites away, but less aggressively)
 *   Odd turns:  TELEGRAPH acid burst 3×3 centered on the player → next tick: RESOLVE
 *
 * The acid burst marks a 3×3 block around the player's current tile.
 * Minions still alive from phase 1 also take acid damage if standing in the burst zone,
 * giving the player a tactical option to lure the Corruptor into nuking his own swarm.
 */
public final class CorruptorPhase2Pattern implements BossAttackPattern {

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

        BossMove move;
        if (stepIndex % 2 == 0) {
            move = BossMove.reposition();
        } else {
            int playerColumn = GameMath.worldToTile(player.positionX);
            int playerRow    = GameMath.worldToTile(player.positionY);
            buildAcidBurst(scratchTiles, playerColumn, playerRow, level);
            boss.dangerTileSet.arm(new ArrayList<>(scratchTiles), EnemyConstants.CORRUPTOR_ACID_DAMAGE);
            pendingResolve = true;
            move = BossMove.telegraph(EnemyConstants.CORRUPTOR_ACID_DAMAGE);
        }
        stepIndex++;
        return move;
    }

    private void buildAcidBurst(List<DangerTileSet.MarkedTile> out,
                                int centerColumn, int centerRow, Level level) {
        out.clear();
        for (int deltaRow = -1; deltaRow <= 1; deltaRow++) {
            for (int deltaColumn = -1; deltaColumn <= 1; deltaColumn++) {
                int targetColumn = centerColumn + deltaColumn;
                int targetRow    = centerRow    + deltaRow;
                if (targetColumn < 0 || targetColumn >= level.getWidth()) continue;
                if (targetRow    < 0 || targetRow    >= level.getHeight()) continue;
                char cell = level.getCell(targetColumn, targetRow);
                if (Level.isWall(cell) || Level.isColumn(cell)) continue;
                out.add(new DangerTileSet.MarkedTile(targetColumn, targetRow));
            }
        }
    }
}
