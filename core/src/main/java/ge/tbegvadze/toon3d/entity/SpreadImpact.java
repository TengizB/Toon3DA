package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.util.BalanceConfig;

/**
 * The SPREAD role's close-range payoff (balance-overhaul order 3, W1/W2), shared by the Shotgun and the
 * Double-Barrel: a hit at {@code SHOTGUN_KNOCKBACK_MAX_TILES} knocks the target one tile straight away
 * from the player, and a hit at or inside {@code SHOTGUN_STAGGER_MAX_TILES} STAGGERS it (its next
 * committed action is lost and its intent reads STUNNED). Both are deterministic — no chance roll.
 *
 * <p>Which targets may be moved or staggered (no BOSS / MINI_ELITE knockback, bosses immune to stagger,
 * no stagger two turns running, no knockback into walls / doors / props / enemies) is decided by the
 * {@link EnemyHitTarget} that owns the enemies, never here.
 */
public final class SpreadImpact {

    private SpreadImpact() {}

    /**
     * Applies knockback, then stagger, to an enemy just hit by a spread blast.
     *
     * @param enemyHitTarget   the enemy owner (null-safe)
     * @param hitEnemy         the token returned by {@code enemyAt()}
     * @param distanceTiles    tiles from the player to the hit enemy (1 = adjacent)
     * @param facingStepColumn the shot's cardinal step — the knockback direction
     * @param facingStepRow    the shot's cardinal step — the knockback direction
     */
    public static void applyCloseRangeImpact(EnemyHitTarget enemyHitTarget, Object hitEnemy, int distanceTiles,
                                             int facingStepColumn, int facingStepRow) {
        if (enemyHitTarget == null || hitEnemy == null) return;
        if (distanceTiles <= BalanceConfig.SHOTGUN_KNOCKBACK_MAX_TILES) {
            enemyHitTarget.tryKnockbackEnemy(hitEnemy, facingStepColumn, facingStepRow);
        }
        if (distanceTiles <= BalanceConfig.SHOTGUN_STAGGER_MAX_TILES) {
            enemyHitTarget.tryStaggerEnemy(hitEnemy);
        }
    }
}
