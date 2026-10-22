package ge.tbegvadze.toon3d.tileset;

import ge.tbegvadze.toon3d.util.LevelGenConstants;
import ge.tbegvadze.toon3d.util.TilesetConstants;

/**
 * THE REGION WALL SET CATALOG (generated-wall-art order 2): one {@code register()} row per region set.
 * Set A (outer_facility) is real — its ids are the shipped manifest's (order 1); B-E ALIAS set A until
 * their own sets ship in orders 3-4 (decision D8), when each row becomes {@code RegionWallSet.of(...)}.
 * Never a switch; the breach growth numbers live in {@link LevelGenConstants} (WALL_SET_*). Headless.
 */
public final class RegionWallSets {

    private static final RegionWallSetRegistry REGISTRY = new RegionWallSetRegistry();
    private static boolean bootstrapped = false;

    private RegionWallSets() {}

    public static synchronized RegionWallSetRegistry registry() {
        bootstrap();
        return REGISTRY;
    }

    public static synchronized void bootstrap() {
        if (bootstrapped) return;
        registerAll(REGISTRY);
        REGISTRY.validate();
        bootstrapped = true;
    }

    /** The real set a floor of {@code setId} uses; an unknown or null id falls back to set A (R7). */
    public static RegionWallSet resolveOrDefault(String setId) {
        RegionWallSetRegistry registry = registry();
        return registry.resolve(registry.contains(setId) ? setId : TilesetConstants.WALL_SET_DEFAULT);
    }

    static void registerAll(RegionWallSetRegistry registry) {
        registry.register(RegionWallSet.of(TilesetConstants.WALL_SET_OUTER_FACILITY, 'a',
                LevelGenConstants.WALL_SET_A_BREACH_BASE, LevelGenConstants.WALL_SET_A_BREACH_STEP));
        registry.register(RegionWallSet.alias(TilesetConstants.WALL_SET_RESEARCH_WING,  TilesetConstants.WALL_SET_OUTER_FACILITY));
        registry.register(RegionWallSet.alias(TilesetConstants.WALL_SET_REACTOR_DEPTHS, TilesetConstants.WALL_SET_OUTER_FACILITY));
        registry.register(RegionWallSet.alias(TilesetConstants.WALL_SET_THE_BREACH,     TilesetConstants.WALL_SET_OUTER_FACILITY));
        registry.register(RegionWallSet.alias(TilesetConstants.WALL_SET_THE_CORE,       TilesetConstants.WALL_SET_OUTER_FACILITY));
    }
}
