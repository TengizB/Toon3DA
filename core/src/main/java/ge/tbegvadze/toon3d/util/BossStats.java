package ge.tbegvadze.toon3d.util;

/**
 * The DERIVED stat block for one boss at one depth (new-game-balancr order 6).
 *
 * <p>A boss's HP and damage are NEVER flat constants — they are re-derived every spawn by
 * {@link BossBalance#statsForDepth} from the fight-length target and survival ratio in
 * {@link BalanceConfig} SECTION 14, through the GameMath BOSS BALANCE RULESET. This value object is the
 * immutable result: the numbers the {@code Boss} entity and its attack patterns read at runtime, and the
 * numbers {@code BalanceReport}/{@code BalanceSchema} audit. It carries only computed data — it never
 * mutates and never touches LibGDX state.
 *
 * <p>Every per-verb damage is a FRACTION of {@link #damagePerTurn} (RULE 3), so tuning the survival ratio
 * retunes every verb coherently. The pattern classes call {@link #verbDamage(float)} with the boss's own
 * fraction constants; the audit checks each derived verb against the single-hit fairness caps.
 */
public final class BossStats {

    /** The boss depth these stats were derived for (endless mode re-derives deeper, stronger stats for free). */
    public final int depth;
    /** Derived effective HP (RULE 1): expectedPlayerSustainedDpt(depth) * targetFightTurns * multiPhaseFactor. */
    public final int effectiveHitPoints;
    /** Derived sustained boss damage-per-turn (RULE 3): the survival-check output the verb fractions scale. */
    public final float damagePerTurn;
    /** The RULE-1 fight-length dial this boss was tuned to (turns). */
    public final float targetFightTurns;
    /** The no-sponge ceiling (RULE 2): past this many turns the fight SOFT-ENRAGES (never a fail timer). */
    public final float upperFightTurnsCap;
    /** Equal HP phases the bar is split into (RULE 4). */
    public final int phaseCount;
    /** Derived XP reward (RULE 6): boss Threat Points * XP_PER_THREAT_POINT — huge because boss TP is huge. */
    public final int xpReward;
    /** Derived credit reward (RULE 6): modelled fight consumption (ammo + heal value) * risk premium. */
    public final int creditReward;

    BossStats(int depth, int effectiveHitPoints, float damagePerTurn,
              float targetFightTurns, float upperFightTurnsCap, int phaseCount,
              int xpReward, int creditReward) {
        this.depth              = depth;
        this.effectiveHitPoints = effectiveHitPoints;
        this.damagePerTurn      = damagePerTurn;
        this.targetFightTurns   = targetFightTurns;
        this.upperFightTurnsCap = upperFightTurnsCap;
        this.phaseCount         = phaseCount;
        this.xpReward           = xpReward;
        this.creditReward       = creditReward;
    }

    /**
     * The single-hit damage of a boss verb, derived as a fraction of the boss's DPT (RULE 3). Floored at 1
     * so a low-DPT boss verb still lands a point of damage. The caller passes the verb's own DPT-fraction
     * constant from {@link BalanceConfig} SECTION 14 — the verb tables live there, the multiply lives here.
     */
    public int verbDamage(float damagePerTurnFraction) {
        return Math.max(1, Math.round(damagePerTurnFraction * damagePerTurn));
    }

    /**
     * Per-turn HP restored by the one-time repair, derived as a fraction of the boss's DERIVED max HP spread
     * over {@code turns} ticks — so the heal scales with the boss's HP instead of a flat number that would
     * be a full reset on a shallow boss and a rounding error on a deep one. Floored at 1.
     */
    public int healPerTurn(float totalHealHpFraction, int turns) {
        int safeTurns = Math.max(1, turns);
        return Math.max(1, Math.round(effectiveHitPoints * totalHealHpFraction / safeTurns));
    }

    /**
     * Soft-enrage damage multiplier (RULE 3, soft enrage): 1.0 until the fight passes the upper turns cap,
     * then +{@link BalanceConfig#BOSS_ENRAGE_DPT_RAMP_PER_TURN} per turn beyond it. Turtling past the
     * survival-check math stops being a strategy; escalation enforces the R2 ceiling, not a fail state.
     */
    public float enrageDamageMultiplier(long ticksSinceAwaken) {
        float turnsBeyondCap = ticksSinceAwaken - upperFightTurnsCap;
        if (turnsBeyondCap <= 0f) {
            return 1f;
        }
        return 1f + turnsBeyondCap * BalanceConfig.BOSS_ENRAGE_DPT_RAMP_PER_TURN;
    }
}
