package ge.tbegvadze.toon3d.route;

import java.util.Collections;

/**
 * The BOSS node's profile (route-map order-7) — a THIN forwarder. It names the bespoke
 * {@link GeneratorId#BOSS_ARENA} generator (already registered) and stops there: {@code World}'s
 * {@code BossFloorController} seeds the depth-keyed boss into the arena, so the profile carries no
 * guarantees and no budget override.
 *
 * <p>Because BOSS nodes are FORCED onto boss depths and {@link ge.tbegvadze.toon3d.util.GameMath#isBossFloor(int)}
 * is the single authority, this profile and {@link DefaultCombatProfile} agree: a boss depth always
 * yields the arena. Registering this profile just makes that explicit for the BOSS node id rather
 * than relying on the fallback. Pure / headless — no LibGDX imports.
 */
public final class BossProfile implements NodeLevelProfile {

    /** The stable id BOSS nodes reference (matches the node definition's {@code levelProfileId}). */
    public static final String PROFILE_ID = "boss";

    @Override
    public String profileId() {
        return PROFILE_ID;
    }

    @Override
    public LevelPlan resolve(RouteNode node, int depth, long seed) {
        // The arena is a fixed bespoke layout; it ignores config and needs no post-generation
        // guarantees — the boss itself is seeded by BossFloorController in World.
        return new LevelPlan(GeneratorId.BOSS_ARENA, null, Collections.emptyList());
    }
}
