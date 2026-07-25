package ge.tbegvadze.toon3d.util;

/**
 * The boss derivation ENGINE (new-game-balancr order 6) — the single place that turns a boss archetype +
 * depth into a {@link BossStats} block, composing the {@link GameMath} BOSS BALANCE RULESET methods with the
 * {@link BalanceConfig} SECTION 14 inputs. There are NO flat boss HP or damage constants anywhere anymore:
 * {@code World} calls {@link #statsForDepth} at spawn, {@code BalanceReport}/{@code BalanceSchema} call it to
 * audit, and the boss attack patterns read the derived verb damages off the {@code Boss} it produced. Same
 * archetype + depth ⇒ byte-identical stats (pure function of the config), and a deeper endless-mode boss
 * re-derives a larger HP pool and reward automatically.
 *
 * <p>Headless: only {@link GameMath} + {@link BalanceConfig}, no LibGDX, so it runs in the balance audit.
 */
public final class BossBalance {

    private BossBalance() {}

    /**
     * The three act bosses, each a DATA row: its canonical depth, its RULE-1 fight-length dial, and the
     * positional multiplier its Threat Points (and thus XP reward) price at. Adding a boss = one row here
     * plus its verb-fraction constants in SECTION 14 — no switch statement in the derivation.
     */
    public enum Archetype {
        OVERSEER  (BalanceConfig.OVERSEER_DEPTH,   BalanceConfig.OVERSEER_TARGET_FIGHT_TURNS,
                   BalanceConfig.POSITIONAL_MULT_RANGED, "The Overseer"),
        CORRUPTOR (BalanceConfig.CORRUPTOR_DEPTH,  BalanceConfig.CORRUPTOR_TARGET_FIGHT_TURNS,
                   BalanceConfig.POSITIONAL_MULT_RANGED, "The Corruptor"),
        HELL_BARON(BalanceConfig.HELL_BARON_DEPTH, BalanceConfig.HELL_BARON_TARGET_FIGHT_TURNS,
                   BalanceConfig.POSITIONAL_MULT_MELEE,  "Hell Baron");

        public final int    canonicalDepth;
        public final float  targetFightTurns;
        public final float  positionalMultiplier;
        public final String displayName;

        Archetype(int canonicalDepth, float targetFightTurns, float positionalMultiplier, String displayName) {
            this.canonicalDepth       = canonicalDepth;
            this.targetFightTurns     = targetFightTurns;
            this.positionalMultiplier = positionalMultiplier;
            this.displayName          = displayName;
        }
    }

    /**
     * Which archetype fights on a given boss floor. Matches the boss rotation the world uses: the first boss
     * floor is the Overseer, then Corruptor, then Hell Baron, cycling every three boss floors (endless mode).
     * {@code depth} must be a boss floor (a positive multiple of {@link Constants#BOSS_FLOOR_INTERVAL}).
     */
    public static Archetype archetypeForDepth(int depth) {
        int bossIndex = ((depth / Constants.BOSS_FLOOR_INTERVAL) - 1) % Archetype.values().length;
        if (bossIndex < 0) {
            bossIndex = 0;
        }
        return Archetype.values()[bossIndex];
    }

    /** The EXPECTED player's sustained DPT at a boss depth (RULE 1/5) — the honest curve the boss HP is tied to. */
    public static float expectedPlayerDamagePerTurn(int depth) {
        float expectedOffencePowerPoints = BalanceConfig.BOSS_EXPECTED_OFFENCE_BUDGET_FRACTION
                * GameBalance.LEVEL_UP_BUDGET_PP
                * (BalanceConfig.BOSS_EXPECTED_LEVELS_PER_DEPTH * depth);
        return GameMath.expectedPlayerSustainedDamagePerTurn(
                BalanceConfig.REFERENCE_PLAYER_DPT, expectedOffencePowerPoints);
    }

    /** The derived stat block for the boss that fights at {@code depth}. */
    public static BossStats statsForDepth(int depth) {
        return statsForDepth(archetypeForDepth(depth), depth);
    }

    /**
     * The derived stat block for a specific archetype at a specific depth. HP is derived from the fight-length
     * target against the EXPECTED player's DPT (RULE 1); DPT is the survival-check output (RULE 3); reward is
     * the modelled fight consumption times the risk premium (RULE 6). One HP bar split across phases (the boss
     * is not RE-fought), so the multi-phase factor is 1.0 per the ruleset.
     */
    public static BossStats statsForDepth(Archetype archetype, int depth) {
        float targetTurns  = archetype.targetFightTurns;
        float expectedDpt  = expectedPlayerDamagePerTurn(depth);

        int effectiveHitPoints = Math.round(GameMath.bossEffectiveHitPoints(
                expectedDpt, targetTurns, BalanceConfig.BOSS_MULTI_PHASE_FACTOR_PER_PHASE));

        float damagePerTurn = GameMath.bossDamagePerTurnForSurvivalCheck(
                BalanceConfig.REFERENCE_PLAYER_EHP, targetTurns, BalanceConfig.BOSS_SURVIVAL_CHECK_RATIO_TARGET);

        float upperFightTurnsCap = GameMath.bossUpperFightTurnsCap(
                targetTurns, BalanceConfig.BOSS_UPPER_FIGHT_TURNS_MULTIPLIER);

        int creditReward = Math.round(modelledConsumptionCredits(effectiveHitPoints)
                * BalanceConfig.BOSS_REWARD_RISK_PREMIUM);

        float bossThreatPoints = GameMath.threatPoints(damagePerTurn, 1, effectiveHitPoints,
                BalanceConfig.REFERENCE_PLAYER_DPT, archetype.positionalMultiplier);
        int xpReward = Math.round(bossThreatPoints * BalanceConfig.XP_PER_THREAT_POINT);

        return new BossStats(depth, effectiveHitPoints, damagePerTurn, targetTurns, upperFightTurnsCap,
                BalanceConfig.BOSS_PHASE_COUNT, xpReward, creditReward);
    }

    // ---------------------------------------------------------------------------------------------
    // Consumption / reward model (RULE 6) — priced in the SAME power-point→credit units the shop uses,
    // so a boss REFUNDS the fight it costs plus a profit margin, closing the economy loop from order 3.
    // ---------------------------------------------------------------------------------------------

    /** Credits charged per point of ammo DAMAGE (shop pricing: damage → power points → credits). */
    public static float creditsPerAmmoDamage() {
        return BalanceConfig.SHOP_CREDITS_PER_POWER_POINT / BalanceConfig.SHOP_AMMO_DAMAGE_PER_POWER_POINT;
    }

    /** Credits charged per point of HP HEALED (shop pricing: HP → power points → credits). */
    public static float creditsPerHealHitPoint() {
        return BalanceConfig.SHOP_CREDITS_PER_POWER_POINT / BalanceConfig.SHOP_HEAL_HP_PER_POWER_POINT;
    }

    /**
     * The HP a player must heal back over a FAIR fight, in HP. From the survival-check identity: incoming
     * over the fight ≈ bossDpt * fightTurns = eHP / survivalRatio (with survivalRatio 0.5 → 2 * eHP), the
     * player's own eHP absorbs one eHP, so ≈ one eHP must be bought back with heals. Modelled at the
     * reference eHP (the yardstick the whole survival check is priced against).
     */
    public static float modelledHealHitPoints() {
        return BalanceConfig.REFERENCE_PLAYER_EHP;
    }

    /** Modelled fight consumption in CREDIT units (ammo to deal eHP damage + heals to survive), premium 1. */
    public static float modelledConsumptionCredits(int effectiveHitPoints) {
        return GameMath.bossReward(effectiveHitPoints, creditsPerAmmoDamage(),
                modelledHealHitPoints(), creditsPerHealHitPoint(), 1f);
    }

    /** The DAMAGE a build must output to kill the boss — the ammo-check demand (RULE 5 / AMMO CHECK). */
    public static float modelledAmmoDemandDamage(int effectiveHitPoints) {
        return effectiveHitPoints;
    }

    /** Ammo DAMAGE the boss arena guarantees via placed pickups, so the check tests the BUILD, not full pockets. */
    public static float arenaAmmoBudgetDamage(int effectiveHitPoints) {
        return effectiveHitPoints * BalanceConfig.BOSS_ARENA_AMMO_BUDGET_FRACTION;
    }
}
