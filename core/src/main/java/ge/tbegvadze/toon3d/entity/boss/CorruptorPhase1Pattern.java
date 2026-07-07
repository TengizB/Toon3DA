package ge.tbegvadze.toon3d.entity.boss;

import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.EnemyConstants;

/**
 * Corruptor phase 1 — cowardly summoner.
 *
 * Each tick the Corruptor flees (REPOSITION away from player). At SUMMON_COOLDOWN ticks in,
 * it summons 2 Gore Biter minions adjacent to itself — once, for the whole phase — capped at
 * MINION_CAP total live enemies (enforced in BossFloorController.spawnMinions). After that
 * single cast it only REPOSITIONs for the rest of phase 1, so killing the minions ends the
 * flood for good instead of just buying three turns until the next cast.
 */
public final class CorruptorPhase1Pattern implements BossAttackPattern {

    private int stepIndex = 0;
    private boolean hasSummoned = false;

    @Override
    public void onPhaseStart() {
        stepIndex   = 0;
        hasSummoned = false;
    }

    @Override
    public BossMove nextMove(Boss boss, Player player, Level level, long tickIndex) {
        BossMove move;
        if (!hasSummoned && stepIndex % EnemyConstants.CORRUPTOR_SUMMON_COOLDOWN
                == EnemyConstants.CORRUPTOR_SUMMON_COOLDOWN - 1) {
            move        = BossMove.summon(EnemyType.GORE_BITER, 2);
            hasSummoned = true;
        } else {
            move = BossMove.reposition();
        }
        stepIndex++;
        return move;
    }
}
