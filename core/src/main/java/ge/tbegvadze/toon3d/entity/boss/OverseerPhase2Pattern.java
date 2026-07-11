package ge.tbegvadze.toon3d.entity.boss;

import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.EnemyConstants;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.ArrayList;
import java.util.List;

/**
 * Overseer phase 2 — ripped free, hit-and-run charge (ORDER 2: now mobile).
 *
 * Sequence (repeating every RAM_COOLDOWN ticks):
 *   Turns 0 … RAM_COOLDOWN-2: REPOSITION (step toward player — the STALK)
 *   Turn  RAM_COOLDOWN-1:     TELEGRAPH ram — marks up to 3 tiles ahead (the STRIKE wind-up)
 *   (Next turn):              RESOLVE ram — tiles deal CHARGE_DAMAGE (the STRIKE lands)
 *   (Next turn):              DASH away — a visible multi-tile sprint to a flee tile (the FLEE)
 *
 * The STRIKE plants the boss (telegraph + resolve) so the player gets a punish window, then it
 * DASHES several tiles clear before retaliation — the cat-and-mouse cadence from ORDER 1. The
 * telegraph/resolve plants also satisfy the F1 consecutive-move cap enforced by BossFloorController.
 * Smashing into a column stuns the Overseer for one tick (handled in BossFloorController).
 *
 * NOTE (ORDER 2): the flee-target chooser here is level-only (bounds + walls/columns). It is a
 * stand-in "brain" until ORDER 4's HunterKillerPattern; BossFloorController.executeDash re-validates
 * full passability (occupancy/doors) and clamps the path, so an optimistic target is always safe.
 */
public final class OverseerPhase2Pattern implements BossAttackPattern {

    private int stepIndex = 0;
    private boolean pendingResolve = false;
    private boolean pendingFlee    = false;
    private final List<DangerTileSet.MarkedTile> scratchTiles = new ArrayList<>();
    /** Reusable {column, row} scratch for the flee-target chooser — never allocated per turn. */
    private final int[] fleeTarget = new int[2];

    @Override
    public void onPhaseStart() {
        stepIndex      = 0;
        pendingResolve = false;
        pendingFlee    = false;
    }

    @Override
    public BossMove nextMove(Boss boss, Player player, Level level, long tickIndex) {
        int playerColumn = GameMath.worldToTile(player.positionX);
        int playerRow    = GameMath.worldToTile(player.positionY);

        // FLEE: the turn after the ram lands, sprint several tiles clear so the player must chase.
        if (pendingFlee) {
            pendingFlee = false;
            if (chooseFleeTarget(boss, playerColumn, playerRow, level, fleeTarget)) {
                return BossMove.dash(fleeTarget[0], fleeTarget[1]);
            }
            return BossMove.reposition();   // nowhere better to flee — settle for a nudge
        }

        if (pendingResolve) {
            pendingResolve = false;
            pendingFlee    = true;   // flee on the turn after the strike lands
            boss.dangerTileSet.clear();
            return BossMove.resolve();
        }

        int positionInCycle = stepIndex % EnemyConstants.OVERSEER_RAM_COOLDOWN;
        stepIndex++;

        if (positionInCycle == EnemyConstants.OVERSEER_RAM_COOLDOWN - 1) {
            buildRamTiles(scratchTiles, boss, playerColumn, playerRow, level);
            boss.dangerTileSet.arm(new ArrayList<>(scratchTiles), EnemyConstants.OVERSEER_CHARGE_DAMAGE);
            pendingResolve = true;
            return BossMove.telegraph(EnemyConstants.OVERSEER_CHARGE_DAMAGE);
        }

        return BossMove.reposition();
    }

    /**
     * Picks a flee tile MAXIMISING Manhattan distance from the player (ORDER 2, Part D), scanning the
     * bounded diamond within BOSS_MAX_DASH_TILES of the boss. Among equally-distant candidates it prefers
     * one whose straight cardinal line to the player is broken by a 'P' cover column (sets up the ORDER 5
     * heal). Level-only passability (bounds + walls/columns); allocation-free — writes {column, row} into
     * {@code outColumnRow} and returns true when a tile strictly farther than standing still was found.
     */
    private boolean chooseFleeTarget(Boss boss, int playerColumn, int playerRow,
                                     Level level, int[] outColumnRow) {
        int     bestColumn   = boss.tileColumn;
        int     bestRow      = boss.tileRow;
        int     bestDistance = GameMath.manhattanDistanceTiles(
                boss.tileColumn, boss.tileRow, playerColumn, playerRow);
        boolean bestHasCover = false;
        boolean found        = false;

        for (int columnOffset = -Constants.BOSS_MAX_DASH_TILES;
                 columnOffset <= Constants.BOSS_MAX_DASH_TILES; columnOffset++) {
            for (int rowOffset = -Constants.BOSS_MAX_DASH_TILES;
                     rowOffset <= Constants.BOSS_MAX_DASH_TILES; rowOffset++) {
                if (Math.abs(columnOffset) + Math.abs(rowOffset) > Constants.BOSS_MAX_DASH_TILES) continue;
                if (columnOffset == 0 && rowOffset == 0) continue;
                int candidateColumn = boss.tileColumn + columnOffset;
                int candidateRow    = boss.tileRow    + rowOffset;
                if (candidateColumn < 0 || candidateColumn >= level.getWidth())  continue;
                if (candidateRow    < 0 || candidateRow    >= level.getHeight()) continue;
                char cell = level.getCell(candidateColumn, candidateRow);
                if (Level.isWall(cell) || Level.isColumn(cell)) continue;
                if (candidateColumn == playerColumn && candidateRow == playerRow) continue;

                int     distance = GameMath.manhattanDistanceTiles(
                        candidateColumn, candidateRow, playerColumn, playerRow);
                boolean hasCover = coverColumnBetween(
                        candidateColumn, candidateRow, playerColumn, playerRow, level);
                if (distance > bestDistance || (distance == bestDistance && hasCover && !bestHasCover)) {
                    bestDistance = distance;
                    bestColumn   = candidateColumn;
                    bestRow      = candidateRow;
                    bestHasCover = hasCover;
                    found        = true;
                }
            }
        }

        outColumnRow[0] = bestColumn;
        outColumnRow[1] = bestRow;
        return found;
    }

    /**
     * True when a 'P' cover column sits on the straight cardinal line between the candidate tile and the
     * player. Only the same-row / same-column cases have a single cardinal line; a diagonal offset
     * reports no cover. Allocation-free.
     */
    private boolean coverColumnBetween(int fromColumn, int fromRow,
                                       int toColumn, int toRow, Level level) {
        if (fromRow == toRow) {
            int stepColumn = Integer.signum(toColumn - fromColumn);
            if (stepColumn == 0) return false;
            for (int column = fromColumn + stepColumn; column != toColumn; column += stepColumn) {
                if (Level.isColumn(level.getCell(column, fromRow))) return true;
            }
        } else if (fromColumn == toColumn) {
            int stepRow = Integer.signum(toRow - fromRow);
            if (stepRow == 0) return false;
            for (int row = fromRow + stepRow; row != toRow; row += stepRow) {
                if (Level.isColumn(level.getCell(fromColumn, row))) return true;
            }
        }
        return false;
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
