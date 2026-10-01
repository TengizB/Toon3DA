package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.level.LevelGenConfig;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.RouteMapConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * The SUPPLY CACHE node's profile (route-map order-8): a UAC forward depot the incursion hasn't fully
 * overrun. Where {@link ShopProfile}/{@link BossProfile} are thin forwarders, this profile is the
 * first to lean on the order-7 content framework in earnest — it configures a bright, tidy depot and
 * PROMISES a concrete consumable payoff so the player who paid an ELITE reward to route here is
 * rewarded unambiguously.
 *
 * <p>Recipe:
 * <ul>
 *   <li>Generator fixed to {@link GeneratorId#ROOMS_MST} for a consistent, tidy "rooms of crates"
 *       depot read (boss depths still defer to the arena — {@link GameMath#isBossFloor(int)} is the
 *       single authority, matching the other profiles).</li>
 *   <li>Config: bright (no unlit/flicker floors), NO hazards (radioactive barrels off), crates +
 *       lockers on, one large cargo bay, columns for depth.</li>
 *   <li>Threat + supply: the CACHE {@link NodeSupplySpec} (balance-overhaul order 2) — a light
 *       roster (0.30x threat, 0.35x bodies, never zero: a cache is not a free heal) and a net-GAIN
 *       plan of ammo (split by the weapons the player carries), heals, armour and chips, placed by
 *       the shared FloorPopulator.</li>
 *   <li>Guarantees (post-generation, deterministic from the floor seed): brighten floors and
 *       crates/lockers as set dressing.</li>
 * </ul>
 *
 * <p>The reliable WEAPON payout is the ELITE node; the cache stays about consumables so the two node
 * types read distinctly. Pure / headless — no LibGDX imports.
 */
public final class CacheProfile implements NodeLevelProfile {

    /** The stable id CACHE nodes reference (matches the node definition's {@code levelProfileId}). */
    public static final String PROFILE_ID = "supply_cache";

    public CacheProfile() {
    }

    @Override
    public String profileId() {
        return PROFILE_ID;
    }

    @Override
    public LevelPlan resolve(RouteNode node, int depth, long seed) {
        if (GameMath.isBossFloor(depth)) {
            // A cache is never forced onto a boss depth, but stay consistent with the other profiles:
            // the arena always wins on a boss floor.
            return new LevelPlan(GeneratorId.BOSS_ARENA, null, java.util.Collections.emptyList());
        }

        LevelGenConfig config = buildDepotConfig();
        // Calm-but-not-empty and a net supply GAIN: the CACHE NodeSupplySpec (balance-overhaul order 2)
        // sets the light roster (0.30x threat, 0.35x bodies) and the depot's planned ammo / heals / armour.
        config.supplySpec = RouteRegistries.nodeSupplySpecs().get(RouteNodeType.CACHE);
        return new LevelPlan(GeneratorId.ROOMS_MST, config, buildSetDressing(seed));
    }

    /** A bright, hazard-free depot config: crates + lockers + a cargo bay. */
    private LevelGenConfig buildDepotConfig() {
        LevelGenConfig config = new LevelGenConfig();
        // Keep it bright — a safe read within 2 seconds of entering.
        config.unlitFloors      = false;
        config.flickeringFloors = false;
        config.normalFloors     = true;
        // No hazards in a safe room.
        config.radioactiveBarrels = false;
        // The depot look: crates + lockers, one big cargo bay, columns for cover/visual depth.
        config.crates           = true;
        config.lockers          = true;
        config.enableLargeRooms = true;
        config.enableServerRooms = false;
        config.columns          = true;
        return config;
    }

    /**
     * The depot's look: brightened floors and crates + lockers. The payoff is the CACHE spec's plan
     * (balance-overhaul order 2), placed by the shared FloorPopulator.
     */
    private List<GuaranteedContent> buildSetDressing(long seed) {
        List<GuaranteedContent> guarantees = new ArrayList<>();
        // Brighten first so a standard generator's dark tiles can't leave the depot reading unsafe.
        guarantees.add(Guarantees.brightenFloors());
        // Depot set-dressing: crates + lockers scattered so the space reads as a supply cache.
        guarantees.add(Guarantees.prop('C', RouteMapConstants.CACHE_CRATES, Placement.SCATTERED, seed));
        guarantees.add(Guarantees.prop('L', RouteMapConstants.CACHE_LOCKERS, Placement.SCATTERED, seed));
        return guarantees;
    }
}
