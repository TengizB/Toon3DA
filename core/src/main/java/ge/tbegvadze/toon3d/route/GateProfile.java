package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.RouteMapConstants;

import java.util.Collections;

/**
 * The REGION_GATE node's profile (route-map order-10): a forced convergence node at a region boundary
 * that is NOT a boss depth. Rather than a fight, it builds a SHORT ceremonial airlock/bulkhead floor —
 * a keycard-gated 'B' blue-door approach, a threshold room, then the exit — so crossing a region reads
 * as a visible CHAPTER BREAK and a pacing breath. The airlock reuses {@code DoorManager} (a blue
 * keycard waits on the approach so the bulkhead always opens); the region ANNOUNCE ("ENTERING: …") and
 * its theming are applied generically by {@code World} when the descent crosses into the new region,
 * so they fire on every region transition, boss or gate.
 *
 * <p>Recipe: the bespoke {@link GeneratorId#GATE_AIRLOCK} floor at a near-zero budget
 * ({@link RouteMapConstants#GATE_BUDGET_SCALE}, still depth-scaled first per the order-3 invariant),
 * with a generic "bulkhead" sting and a "gate" telemetry token. Boss depths still defer to the arena
 * ({@link GameMath#isBossFloor(int)} is the single authority). Pure / headless — no LibGDX imports.
 */
public final class GateProfile implements NodeLevelProfile {

    /** The stable id REGION_GATE nodes reference (matches the node definition's {@code levelProfileId}). */
    public static final String PROFILE_ID = "region_gate";

    @Override
    public String profileId() {
        return PROFILE_ID;
    }

    @Override
    public LevelPlan resolve(RouteNode node, int depth, long seed) {
        if (GameMath.isBossFloor(depth)) {
            // A gate is only injected on a non-boss boundary, but stay consistent: the arena wins.
            return new LevelPlan(GeneratorId.BOSS_ARENA, null, Collections.emptyList());
        }
        FloorEffects effects = FloorEffects.NONE.withStatLog("gate");
        return new LevelPlan(GeneratorId.GATE_AIRLOCK, null, Collections.emptyList(),
                EnemyBudgetOverride.scaled(RouteMapConstants.GATE_BUDGET_SCALE), null, effects);
    }
}
