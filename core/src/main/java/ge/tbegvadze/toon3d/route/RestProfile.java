package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.util.GameMath;

import java.util.Collections;

/**
 * The MED-BAY / REST node's profile (route-map order-8). It names the bespoke
 * {@link GeneratorId#MED_BAY} generator ({@code MedBayGenerator}) — a curated clinic whose auto-doc
 * heal station and zero-enemy layout ARE the node's content — so the profile itself carries no
 * post-generation guarantees. The one-time heal is wired by {@code World}, which reads the station
 * tile the generator registered on the {@link ge.tbegvadze.toon3d.level.Level}.
 *
 * <p>The clinic emits no enemy spawn points, so the floor is genuinely empty regardless of budget;
 * {@link EnemyBudgetOverride#calm()} is still attached to document intent and to keep the config path
 * consistent with the other CALM profiles. Boss depths defer to the arena — {@link GameMath#isBossFloor(int)}
 * stays the single authority (a REST node is never forced onto a boss depth, but the guard keeps every
 * profile honest). Pure / headless — no LibGDX imports.
 */
public final class RestProfile implements NodeLevelProfile {

    /** The stable id REST nodes reference (matches the node definition's {@code levelProfileId}). */
    public static final String PROFILE_ID = "rest_medbay";

    public RestProfile() {
    }

    @Override
    public String profileId() {
        return PROFILE_ID;
    }

    @Override
    public LevelPlan resolve(RouteNode node, int depth, long seed) {
        if (GameMath.isBossFloor(depth)) {
            return new LevelPlan(GeneratorId.BOSS_ARENA, null, Collections.emptyList());
        }
        // The med-bay generator is self-contained: it stamps the auto-doc, pods, and exit itself and
        // registers the heal-station tile on the Level for World to activate. No config, no guarantees.
        return new LevelPlan(GeneratorId.MED_BAY, null, Collections.emptyList(), EnemyBudgetOverride.calm());
    }
}
