package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.level.LevelGenConfig;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.Collections;
import java.util.List;

/**
 * The order-3 baseline {@link NodeLevelProfile}: it turns any node into an ordinary combat floor and
 * is registered as the registry FALLBACK, so every node type (CACHE / SHOP / MYSTERY / ...) still
 * yields a playable floor before order-7 ships their bespoke profiles.
 *
 * <p>Generator selection:
 * <ul>
 *   <li>Boss depths return {@link GeneratorId#BOSS_ARENA} — {@link GameMath#isBossFloor(int)} stays
 *       the single authority, so the arena is built even when a forced BOSS node routes through this
 *       fallback.</li>
 *   <li>COMBAT / ELITE nodes carry a pre-rolled {@link RouteNode#chosenGeneratorId} (order-2); it is
 *       used verbatim to keep the map deterministic.</li>
 *   <li>Any other node with no pre-rolled generator picks deterministically from the registry's
 *       {@link GeneratorRegistry#standardPool()} using the floor seed.</li>
 * </ul>
 *
 * <p>The config is a plain depth-agnostic default ({@link LevelGenConfig}); difficulty scaling is
 * driven elsewhere by the RAW depth, never by this profile. ELITE hazard/affix bumps are reserved
 * for order-9. Guarantees are empty until order-7. Pure / headless — no LibGDX imports.
 */
public final class DefaultCombatProfile implements NodeLevelProfile {

    /** The stable id combat nodes reference; also serves as the registry fallback profile. */
    public static final String PROFILE_ID = "combat_standard";

    private final GeneratorRegistry generators;

    public DefaultCombatProfile(GeneratorRegistry generators) {
        this.generators = generators;
    }

    @Override
    public String profileId() {
        return PROFILE_ID;
    }

    @Override
    public LevelPlan resolve(RouteNode node, int depth, long seed) {
        GeneratorId generatorId = resolveGeneratorId(node, depth, seed);
        // A fresh default config per floor; kept depth-agnostic so the depth ramp (roguelike_order_16)
        // is never bypassed here. Order-9 will bump ELITE hazard weights + set an affix flag.
        LevelGenConfig config = new LevelGenConfig();
        List<GuaranteedContent> guarantees = Collections.emptyList();
        return new LevelPlan(generatorId, config, guarantees);
    }

    private GeneratorId resolveGeneratorId(RouteNode node, int depth, long seed) {
        // Boss floors are authoritative regardless of node type: the arena always wins on a boss depth.
        if (GameMath.isBossFloor(depth)) {
            return GeneratorId.BOSS_ARENA;
        }
        // Combat family nodes carry a deterministically pre-rolled generator (order-2).
        if (node != null && node.chosenGeneratorId != null) {
            return node.chosenGeneratorId;
        }
        // Fallback for special nodes with no pre-rolled generator: deterministic pick from the pool.
        List<GeneratorId> pool = generators.standardPool();
        if (pool.isEmpty()) {
            // No standard generators registered — last-resort to the room generator so a floor still builds.
            return GeneratorId.ROOMS_MST;
        }
        int index = (int) Math.floorMod(seed, pool.size());
        return pool.get(index);
    }
}
