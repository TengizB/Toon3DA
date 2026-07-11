package ge.tbegvadze.toon3d.entity.boss;

import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.EnemyConstants;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.ArrayList;
import java.util.List;

/**
 * Overseer phase 2 — ripped free, hit-and-run CHARGE (ORDER 3: the lunge is now one verb).
 *
 * Sequence (repeating every RAM_COOLDOWN ticks):
 *   Turns 0 … RAM_COOLDOWN-2: REPOSITION (step toward player — the STALK)
 *   Turn  RAM_COOLDOWN-1:     TELEGRAPH — arms the charge corridor toward the player (the wind-up)
 *   (Next turn):              CHARGE — resolves the corridor hit AND lunges to the far end of the
 *                             now-cleared lane in a single verb (the STRIKE + FLEE-enabling reposition)
 *   (Next turn):              forced RECOVERY plant, enforced by BossFloorController (the punish window)
 *
 * ORDER 2 modelled this beat by hand as TELEGRAPH → RESOLVE → DASH-away; ORDER 3 folds the hit and the
 * relocation into one CHARGE verb, and the controller guarantees the one-turn recovery so the player
 * always gets a window. This remains a stand-in "brain" until ORDER 4's HunterKillerPattern sequences
 * the full verb kit (charge, summon, fire, toxic, melee) tactically.
 */
public final class OverseerPhase2Pattern implements BossAttackPattern {

    private int stepIndex = 0;
    private boolean pendingCharge = false;
    /** Cardinal charge axis captured at wind-up, replayed when the lunge resolves next turn. */
    private int chargeDirectionColumn = 0;
    private int chargeDirectionRow    = 0;
    private final List<DangerTileSet.MarkedTile> scratchTiles = new ArrayList<>();

    @Override
    public void onPhaseStart() {
        stepIndex     = 0;
        pendingCharge = false;
    }

    @Override
    public BossMove nextMove(Boss boss, Player player, Level level, long tickIndex) {
        int playerColumn = GameMath.worldToTile(player.positionX);
        int playerRow    = GameMath.worldToTile(player.positionY);

        // STRIKE: the turn after the wind-up, lunge along the telegraphed corridor. BossFloorController
        // resolves the armed DangerTileSet hit, slides the boss to the far end, and arms the recovery.
        if (pendingCharge) {
            pendingCharge = false;
            return BossMove.charge(chargeDirectionColumn, chargeDirectionRow,
                    EnemyConstants.OVERSEER_CHARGE_RANGE_TILES);
        }

        int positionInCycle = stepIndex % EnemyConstants.OVERSEER_RAM_COOLDOWN;
        stepIndex++;

        if (positionInCycle == EnemyConstants.OVERSEER_RAM_COOLDOWN - 1) {
            if (buildChargeCorridor(scratchTiles, boss, playerColumn, playerRow, level)) {
                boss.dangerTileSet.arm(new ArrayList<>(scratchTiles), EnemyConstants.OVERSEER_CHARGE_DAMAGE);
                pendingCharge = true;
                return BossMove.telegraph(EnemyConstants.OVERSEER_CHARGE_DAMAGE);
            }
            // No clear cardinal lane (boss already on the player's tile / boxed at the wall) — stalk instead.
            return BossMove.reposition();
        }

        return BossMove.reposition();
    }

    /**
     * Marks the charge corridor: the straight cardinal lane from the boss toward the player, up to
     * OVERSEER_CHARGE_RANGE_TILES tiles or the first wall/cover column (whichever comes first). Picks the
     * dominant axis to the player so the lane points the way the boss will lunge, and records that axis in
     * {@link #chargeDirectionColumn}/{@link #chargeDirectionRow} for the resolve turn. Allocation-free
     * (writes into {@code out}); returns true when at least one lane tile was marked.
     */
    private boolean buildChargeCorridor(List<DangerTileSet.MarkedTile> out,
                                        Boss boss, int playerColumn, int playerRow, Level level) {
        out.clear();
        int differenceColumn = playerColumn - boss.tileColumn;
        int differenceRow    = playerRow    - boss.tileRow;
        if (differenceColumn == 0 && differenceRow == 0) return false;

        int stepColumn = Integer.signum(differenceColumn);
        int stepRow    = Integer.signum(differenceRow);
        if (Math.abs(differenceColumn) >= Math.abs(differenceRow)) {
            stepRow = 0;
        } else {
            stepColumn = 0;
        }
        chargeDirectionColumn = stepColumn;
        chargeDirectionRow    = stepRow;

        for (int reach = 1; reach <= EnemyConstants.OVERSEER_CHARGE_RANGE_TILES; reach++) {
            int targetColumn = boss.tileColumn + stepColumn * reach;
            int targetRow    = boss.tileRow    + stepRow    * reach;
            if (targetColumn < 0 || targetColumn >= level.getWidth())  break;
            if (targetRow    < 0 || targetRow    >= level.getHeight()) break;
            char cell = level.getCell(targetColumn, targetRow);
            if (Level.isWall(cell) || Level.isColumn(cell)) break;
            out.add(new DangerTileSet.MarkedTile(targetColumn, targetRow));
        }
        return !out.isEmpty();
    }
}
