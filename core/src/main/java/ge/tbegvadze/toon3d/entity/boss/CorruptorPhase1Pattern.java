package ge.tbegvadze.toon3d.entity.boss;

import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.entity.Player;
<br>import ge.tbegvadze.toon3d.level.Level;
import ge.tbegvadze.toon3d.util.EnemyConstants;

/**
 * Corruptor phase 1 — cowardly summoner.
 *
 * Each tick the Corruptor flees (REPOSITION away from player).
 * Every SUMMON_COOLDOWN ticks it instead summons 2 Gore Biter minions adjacent to itself,
 * capped at MINION_CAP total live enemies (enforced in BossFloorController.spawnMinions).
 */
public final class CorruptorPhase1Pattern implements BossAttackPattern {

    private int stepIndex = 0;

    @Override
    public void onPhaseStart() {
        stepIndex = 0;
    }

    @Override
    public BossMove nextMove(Boss boss, Player player, Level level, long tickIndex) {
        BossMove move;
        if (stepIndex % EnemyConstants.CORRUPTOR_SUMMON_COOLDOWN
                == EnemyConstants.CORRUPTOR_SUMMON_COOLDOWN - 1) {
            move = BossMove.summon(EnemyType.GORE_BITER, 2);
        } else {
            move = BossMove.reposition();
        }
        stepIndex++;
        return move;
    }
}
