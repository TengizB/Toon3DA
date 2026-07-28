package ge.tbegvadze.toon3d.entity.boss;

import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.level.BossArenaLayout;
import ge.tbegvadze.toon3d.util.BossBalance;
import ge.tbegvadze.toon3d.util.BossStats;
import ge.tbegvadze.toon3d.util.EnemyConstants;

/**
 * Depth -&gt; boss factory: the single place a {@link Boss} instance is built from its derived stat
 * block (new-game-balancr order 6 ruleset) and its phase-1 / phase-2 attack patterns.
 */
public final class BossFactory {

    private BossFactory() {}

    /**
     * Builds the boss for a boss floor. HP and every verb's damage are DERIVED from depth
     * (new-game-balancr order 6) via {@link BossBalance} — never a flat constant — and the archetype
     * rotation matches {@link BossBalance#archetypeForDepth}.
     *
     * <p>Headless by construction (no LibGDX state), so both {@code World} and the order-9 balance
     * simulator build the SAME boss from the same depth + seed. It used to be a private static in
     * {@code World}; the simulator needs real boss fights (the S-GATE band is about the FIRST boss),
     * so it moved here rather than being duplicated.
     *
     * @param depth       the boss floor's depth (a positive multiple of BOSS_FLOOR_INTERVAL)
     * @param bossSeed    the floor seed — drives the tactic pools' rolls
     * @param arenaLayout the arena the boss spawns into (supplies its spawn tile)
     */
    public static Boss createForDepth(int depth, long bossSeed, BossArenaLayout arenaLayout) {
        int spawnColumn = arenaLayout.bossColumn;
        int spawnRow    = arenaLayout.bossRow;
        // HP and every verb's damage are DERIVED from depth (order 6): the boss's whole stat block comes
        // from BossBalance, never a flat constant. The archetype rotation matches BossBalance.archetypeForDepth.
        BossBalance.Archetype archetype = BossBalance.archetypeForDepth(depth);
        BossStats stats = BossBalance.statsForDepth(archetype, depth);
        Boss boss;
        switch (archetype) {
            case OVERSEER:
                boss = new Boss(EnemyType.OVERSEER, spawnColumn, spawnRow,
                        "The Overseer", "Eye of the Abyss", "OVERSEER DESTROYED",
                        EnemyConstants.OVERSEER_ACCENT_R, EnemyConstants.OVERSEER_ACCENT_G,
                        EnemyConstants.OVERSEER_ACCENT_B, 1.80f,
                        new HunterKillerPattern(HunterKillerPattern.Pool.PHASE1, bossSeed),
                        new HunterKillerPattern(HunterKillerPattern.Pool.PHASE2, bossSeed));
                boss.nameTag = "The Overseer LVL " + depth;
                break;
            case CORRUPTOR:
                boss = new Boss(EnemyType.CORRUPTOR, spawnColumn, spawnRow,
                        "The Corruptor", "Herald of Decay", "CORRUPTOR PURGED",
                        EnemyConstants.CORRUPTOR_ACCENT_R, EnemyConstants.CORRUPTOR_ACCENT_G,
                        EnemyConstants.CORRUPTOR_ACCENT_B, 1.60f,
                        new CorruptorPhase1Pattern(), new CorruptorPhase2Pattern());
                boss.nameTag = "The Corruptor LVL " + depth;
                break;
            case HELL_BARON:
            default:
                boss = new Boss(EnemyType.HELL_BARON, spawnColumn, spawnRow,
                        "Hell Baron", "Lord of Flame", "HELL BARON FALLS",
                        EnemyConstants.HELL_BARON_ACCENT_R, EnemyConstants.HELL_BARON_ACCENT_G,
                        EnemyConstants.HELL_BARON_ACCENT_B, 2.00f,
                        new HellBaronPhase1Pattern(), new HellBaronPhase2Pattern());
                boss.nameTag = "Hell Baron LVL " + depth;
                break;
        }
        boss.stats        = stats;
        boss.maxHealth    = stats.effectiveHitPoints;
        boss.health       = stats.effectiveHitPoints;
        boss.dungeonLevel = depth;
        return boss;
    }
}
