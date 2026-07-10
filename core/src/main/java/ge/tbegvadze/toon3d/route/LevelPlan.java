package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.level.LevelGenConfig;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The fully-resolved recipe for ONE floor, produced by a {@link NodeLevelProfile} from the node the
 * player committed to. It is the hand-off between the pure route layer and {@code World}'s existing
 * level-build machinery: {@code World} reads {@link #generatorId()} + {@link #config()} to build the
 * generator, then replays {@link #guarantees()} over the generated {@link ge.tbegvadze.toon3d.level.Level}.
 *
 * <p>Immutable value object. Pure / headless — no LibGDX imports.
 */
public final class LevelPlan {

    private final GeneratorId generatorId;
    private final LevelGenConfig config;
    private final List<GuaranteedContent> guarantees;
    private final EnemyBudgetOverride enemyBudget;
    private final AmmoCacheRequest ammoCache;
    private final FloorEffects floorEffects;

    /**
     * Fullest-fidelity plan: explicit encounter-budget override AND an owned-ammo cache request.
     *
     * @param generatorId which registered generator builds the floor (never null)
     * @param config      the generation config handed to that generator (may be null; generators
     *                    that ignore config default internally)
     * @param guarantees  post-generation content promises; may be empty, never null. Defensively copied.
     * @param enemyBudget scales the depth-based encounter budget, or {@code null} for the normal
     *                    depth budget. Never bypasses the depth ramp — see {@link EnemyBudgetOverride}.
     * @param ammoCache   an owned-ammo cache request {@code World} fulfils after build, or {@code null}
     *                    for none. See {@link AmmoCacheRequest}.
     */
    public LevelPlan(GeneratorId generatorId, LevelGenConfig config, List<GuaranteedContent> guarantees,
                     EnemyBudgetOverride enemyBudget, AmmoCacheRequest ammoCache) {
        this(generatorId, config, guarantees, enemyBudget, ammoCache, FloorEffects.NONE);
    }

    /**
     * Fullest-fidelity plan, additionally carrying {@link FloorEffects} (order-9): presentation /
     * telemetry flags {@code World} applies after the floor is built (red alert, first-frame sting,
     * outcome telemetry). Used by the DANGER / GAMBLE profiles; prefer {@link #withFloorEffects} on an
     * already-built plan when only the effects change.
     *
     * @param floorEffects the post-build effect flags; never null (pass {@link FloorEffects#NONE}).
     */
    public LevelPlan(GeneratorId generatorId, LevelGenConfig config, List<GuaranteedContent> guarantees,
                     EnemyBudgetOverride enemyBudget, AmmoCacheRequest ammoCache, FloorEffects floorEffects) {
        this.generatorId  = Objects.requireNonNull(generatorId, "generatorId");
        this.config       = config;
        this.guarantees   = Collections.unmodifiableList(new java.util.ArrayList<>(
                Objects.requireNonNull(guarantees, "guarantees")));
        this.enemyBudget  = enemyBudget;
        this.ammoCache    = ammoCache;
        this.floorEffects = Objects.requireNonNull(floorEffects, "floorEffects");
    }

    /**
     * Full-fidelity plan with an explicit encounter-budget override and no ammo cache.
     *
     * @param generatorId which registered generator builds the floor (never null)
     * @param config      the generation config handed to that generator (may be null; generators
     *                    that ignore config default internally)
     * @param guarantees  post-generation content promises; may be empty, never null. Defensively copied.
     * @param enemyBudget scales the depth-based encounter budget, or {@code null} for the normal
     *                    depth budget. Never bypasses the depth ramp — see {@link EnemyBudgetOverride}.
     */
    public LevelPlan(GeneratorId generatorId, LevelGenConfig config, List<GuaranteedContent> guarantees,
                     EnemyBudgetOverride enemyBudget) {
        this(generatorId, config, guarantees, enemyBudget, null);
    }

    /**
     * Convenience plan with the normal depth budget (no {@link EnemyBudgetOverride}).
     *
     * @param generatorId which registered generator builds the floor (never null)
     * @param config      the generation config handed to that generator (may be null)
     * @param guarantees  post-generation content promises; may be empty, never null. Defensively copied.
     */
    public LevelPlan(GeneratorId generatorId, LevelGenConfig config, List<GuaranteedContent> guarantees) {
        this(generatorId, config, guarantees, null, null);
    }

    /** Which registered generator builds this floor. */
    public GeneratorId generatorId() {
        return generatorId;
    }

    /** The generation config, or {@code null} to let the generator default internally. */
    public LevelGenConfig config() {
        return config;
    }

    /** Post-generation content promises, in application order. Unmodifiable; possibly empty. */
    public List<GuaranteedContent> guarantees() {
        return guarantees;
    }

    /**
     * The encounter-budget override, or {@code null} to spend the normal depth budget. Always a
     * multiplier on the raw depth budget — a profile changes floor pressure, never the depth ramp.
     */
    public EnemyBudgetOverride enemyBudget() {
        return enemyBudget;
    }

    /**
     * The owned-ammo cache request {@code World} fulfils after the floor is built, or {@code null}
     * when this floor requests no ammo cache. See {@link AmmoCacheRequest}.
     */
    public AmmoCacheRequest ammoCache() {
        return ammoCache;
    }

    /**
     * The presentation / telemetry effects {@code World} applies after building this floor (order-9).
     * Never null — {@link FloorEffects#NONE} for an ordinary floor.
     */
    public FloorEffects floorEffects() {
        return floorEffects;
    }

    /**
     * Returns a copy of this plan carrying {@code effects}, preserving every generator / config /
     * guarantee / budget / ammo field. Lets a meta-profile (MYSTERY) resolve a delegate plan and then
     * stamp its own reveal sting + telemetry without rebuilding the whole plan.
     */
    public LevelPlan withFloorEffects(FloorEffects effects) {
        return new LevelPlan(generatorId, config, guarantees, enemyBudget, ammoCache, effects);
    }
}
