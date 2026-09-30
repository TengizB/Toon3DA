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

    /**
     * The EXPECTED player's sustained DPT at a boss depth (RULE 1/5) — the honest curve the boss HP is tied to.
     * Balance-overhaul order 1: read from THE one expected-player model (GameMath.expectedPlayerAtDepth — the
     * weapon on the ladder, the region's rarity, the on-curve character level), not a boss-only card curve.
     */
    public static float expectedPlayerDamagePerTurn(int depth) {
        return GameMath.expectedPlayerAtDepth(depth).damagePerTurn;
    }

    /** The EXPECTED player's eHP at a boss depth — the pool the survival check and the verb caps are read against. */
    public static float expectedPlayerEffectiveHitPoints(int depth) {
        return GameMath.expectedPlayerAtDepth(depth).effectiveHitPoints;
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

        // Rounded UP, so the expected player's fight is never a fraction of a turn shorter than its target
        // (R-BOSS-FAIR's lower bound is the target itself).
        int effectiveHitPoints = (int) Math.ceil(GameMath.bossEffectiveHitPoints(
                expectedDpt, targetTurns, BalanceConfig.BOSS_MULTI_PHASE_FACTOR_PER_PHASE));

        float damagePerTurn = GameMath.bossDamagePerTurnForSurvivalCheck(
                expectedPlayerEffectiveHitPoints(depth), targetTurns, BalanceConfig.BOSS_SURVIVAL_CHECK_RATIO_TARGET);

        float upperFightTurnsCap = GameMath.bossUpperFightTurnsCap(
                targetTurns, BalanceConfig.BOSS_UPPER_FIGHT_TURNS_MULTIPLIER);

        int creditReward = Math.round(modelledConsumptionCredits(effectiveHitPoints, depth)
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
     * The HP a player must heal back over a FAIR fight, in DEPTH-1 hit points. From the survival-check identity:
     * incoming over the fight ≈ bossDpt * fightTurns = eHP / survivalRatio (with survivalRatio 0.5 → 2 * eHP), the
     * player's own eHP absorbs one eHP, so ≈ one eHP must be bought back with heals — at depth-1 scale that is the
     * reference eHP (every heal is a fraction of max, so the SHARE is depth-independent; balance-overhaul order 1).
     */
    public static float modelledHealHitPoints() {
        return BalanceConfig.REFERENCE_PLAYER_EHP;
    }

    /**
     * Modelled fight consumption in CREDIT units at the boss's depth (ammo to deal eHP damage + heals to survive),
     * premium 1. Balance-overhaul order 1: the ammo is read in DEPTH-1 damage terms (divided by the expected hit
     * growth — every ammo unit hits that much harder deep) and the whole bill is priced at the depth's shop price
     * level (GameMath.shopPrice's depth factor), so a deep boss refunds a deep fight in the credits a deep shop
     * charges, instead of scaling with the raw (ladder-inflated) HP number.
     */
    public static float modelledConsumptionCredits(int effectiveHitPoints, int depth) {
        float depthOneAmmoDamage = effectiveHitPoints / Math.max(1e-3f, GameMath.expectedHitGrowthAtDepth(depth));
        float depthPriceLevel = GameMath.shopDepthPriceFactor(depth, BalanceConfig.SHOP_DEPTH_PRICE_SCALE);
        return GameMath.bossReward(depthOneAmmoDamage, creditsPerAmmoDamage(),
                modelledHealHitPoints(), creditsPerHealHitPoint(), 1f) * depthPriceLevel;
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
