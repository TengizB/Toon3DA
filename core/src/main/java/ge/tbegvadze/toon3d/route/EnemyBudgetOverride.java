package ge.tbegvadze.toon3d.route;

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

    /** Hard floor on the scale so a node can never zero out (or invert) the encounter. */
    public static final float MIN_SCALE = 0.1f;
    /** Hard ceiling so a node can never balloon the budget beyond a sane multiple of the depth base. */
    public static final float MAX_SCALE = 4.0f;

    /** The share of the depth budget a nearly-empty CALM floor spends (CACHE / REST). */
    public static final float CALM_SCALE = 0.35f;
    /** A gentler reduction for nodes that are calm but not empty (SHOP keeps light resistance). */
    public static final float LIGHT_SCALE = 0.5f;

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

    /** The multiplier applied to the raw depth-scaled Threat-Point budget. Always within bounds. */
    public float budgetScale() {
        return budgetScale;
    }

    private static float clamp(float scale) {
        if (scale < MIN_SCALE) return MIN_SCALE;
        if (scale > MAX_SCALE) return MAX_SCALE;
        return scale;
    }
}
