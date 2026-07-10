package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.level.LevelGenConfig;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.Collections;
import java.util.List;

/**
 * The SHOP node's profile (route-map order-7) — deliberately THIN. The entire shop system
 * (shop_order_1..6) already guarantees a vending machine on every floor via
 * {@code World.seedVendingMachines}, so this profile does not need to stamp one; it just picks an
 * ordinary floor and CALMS it so the black market reads as a safe pit stop, not a fight.
 *
 * <p>What it does:
 * <ul>
 *   <li>Builds a normal combat-style floor (a deterministic pick from the standard generator pool, or
 *       the pre-rolled generator if the node carries one).</li>
 *   <li>Lowers the encounter budget via {@link EnemyBudgetOverride#light()} — light resistance, never
 *       depth-frozen (the depth ramp is still applied first).</li>
 * </ul>
 *
 * <p>Boss depths still defer to the arena: {@link GameMath#isBossFloor(int)} stays the single
 * authority, matching {@link DefaultCombatProfile}. Pure / headless — no LibGDX imports.
 */
public final class ShopProfile implements NodeLevelProfile {

    /** The stable id SHOP nodes reference (matches the node definition's {@code levelProfileId}). */
    public static final String PROFILE_ID = "shop";

    private final GeneratorRegistry generators;

    public ShopProfile(GeneratorRegistry generators) {
        this.generators = generators;
    }

    @Override
    public String profileId() {
        return PROFILE_ID;
    }

    @Override
    public LevelPlan resolve(RouteNode node, int depth, long seed) {
        GeneratorId generatorId = resolveGeneratorId(node, depth, seed);
        LevelGenConfig config = new LevelGenConfig();
        List<GuaranteedContent> guarantees = Collections.emptyList();
        // Calm-but-not-empty: the shop keeps light resistance so it isn't a free heal, but never a
        // full fight. The budget is still a function of raw depth (scaled, not frozen).
        return new LevelPlan(generatorId, config, guarantees, EnemyBudgetOverride.light());
    }

    private GeneratorId resolveGeneratorId(RouteNode node, int depth, long seed) {
        if (GameMath.isBossFloor(depth)) {
            return GeneratorId.BOSS_ARENA;
        }
        if (node != null && node.chosenGeneratorId != null) {
            return node.chosenGeneratorId;
        }
        List<GeneratorId> pool = generators.standardPool();
        if (pool.isEmpty()) {
            return GeneratorId.ROOMS_MST;
        }
        return pool.get((int) Math.floorMod(seed, pool.size()));
    }
}
