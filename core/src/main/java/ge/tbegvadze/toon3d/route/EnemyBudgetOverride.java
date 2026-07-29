package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.util.BalanceConfig;

/**
 * A node's request to reshape the encounter budget the generator spends on this floor, WITHOUT ever
 * escaping the depth ramp (roguelike_order_16 / order-3 invariant). It is a multiplier on the raw,
 * depth-scaled Threat-Point budget — an ELITE at depth 8 stays depth-8-hard AND elite; a CACHE at
 * depth 8 stays depth-8-scaled but spends fewer points. It never freezes the floor at an earlier
 * depth.
 *
 * <p>CALM nodes (CACHE / REST / SHOP) lower the scale so the floor is a pressure-release valve; DANGER
 * nodes (ELITE, order-9) can raise it. A {@code null} override on a {@link LevelPlan} means "no change
 * — use the normal depth budget".
 *
 * <p>Immutable value object. Pure / headless — no LibGDX imports. The scale is threaded through
 * {@code LevelGenConfig.enemyBudgetScale} into {@code EncounterBudgetPlanner}, the existing system
 * that already spends the depth-scaled budget.
 */
public final class EnemyBudgetOverride {

    /**
     * Hard floor on any POSITIVE scale, so a node that wants a fight can never accidentally shrink it
     * to nothing. Exactly {@code 0f} is a distinct, legal value meaning "this floor has NO enemies"
     * (see {@link #empty()}) and is passed through un-clamped; negative scales clamp up to here.
     */
    public static final float MIN_SCALE = 0.1f;
    /** Hard ceiling so a node can never balloon the budget beyond a sane multiple of the depth base. */
    public static final float MAX_SCALE = 4.0f;

    /**
     * The share of the depth budget a nearly-empty CALM floor spends (CACHE / REST). Balance shim:
     * the value is priced threat (R-HONEST-SAFE bounds it at 0.30x a combat floor), so it lives in
     * {@code BalanceConfig} SECTION 19 — new-game-balancr order 7.
     */
    public static final float CALM_SCALE  = BalanceConfig.ROUTE_CALM_BUDGET_SCALE;
    /** A gentler reduction for nodes that are calm but not empty (SHOP keeps light resistance). */
    public static final float LIGHT_SCALE = BalanceConfig.ROUTE_LIGHT_BUDGET_SCALE;

    private final float budgetScale;

    private EnemyBudgetOverride(float budgetScale) {
        this.budgetScale = clamp(budgetScale);
    }

    /** An override that multiplies the depth-scaled budget by {@code scale} (clamped to sane bounds). */
    public static EnemyBudgetOverride scaled(float scale) {
        return new EnemyBudgetOverride(scale);
    }

    /** A near-empty floor: {@link #CALM_SCALE} of the depth budget (CACHE / REST). */
    public static EnemyBudgetOverride calm() {
        return new EnemyBudgetOverride(CALM_SCALE);
    }

    /** A calm-but-not-empty floor: {@link #LIGHT_SCALE} of the depth budget (SHOP). */
    public static EnemyBudgetOverride light() {
        return new EnemyBudgetOverride(LIGHT_SCALE);
    }

    /**
     * A floor with NO enemies at all — a curated story beat (EVENT), a ceremonial airlock
     * (REGION_GATE) or a sanctuary (REST). Distinct from {@link #calm()}: calm means "a few light
     * stragglers", this means "none".
     *
     * <p>Before this existed the system could not EXPRESS an empty floor: {@code scaled(0f)} clamped
     * up to {@link #MIN_SCALE}, and {@code EncounterBudgetPlanner} read a non-positive scale as
     * "unset" and fell back to the FULL depth budget — so a zero was silently the most dangerous
     * value in the range. The three bespoke generators each worked around it by hardcoding an empty
     * spawn list, which left their declared budget scales dead and contradicting the ledger rows that
     * priced them at zero. Zero is now a first-class value that every layer agrees on.
     */
    public static EnemyBudgetOverride empty() {
        return new EnemyBudgetOverride(0f);
    }

    /** The multiplier applied to the raw depth-scaled Threat-Point budget. Always within bounds. */
    public float budgetScale() {
        return budgetScale;
    }

    private static float clamp(float scale) {
        // Exactly zero is the legal "no enemies on this floor" value (see empty()) and must survive
        // the clamp; every other sub-minimum scale (including negatives) is a node asking for a fight
        // too small to be one, and is raised to MIN_SCALE.
        if (scale == 0f)      return 0f;
        if (scale < MIN_SCALE) return MIN_SCALE;
        if (scale > MAX_SCALE) return MAX_SCALE;
        return scale;
    }
}
