package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.RouteMapConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        // registers the heal-station tile on the Level for World to activate. On top of that the clinic
        // is STOCKED (new-game-balancr order 7): a REST floor spawns nothing, so it earns no roster XP
        // and almost no ordinary loot — priced against a combat floor it was far below the calm band
        // (a sucker node). A medical bay that hands you a spare medkit and a vest on the way out is both
        // thematic and what R-CALM-COST requires to make routing here a real trade rather than a trap.
        return new LevelPlan(GeneratorId.MED_BAY, null, buildClinicStock(depth, seed),
                EnemyBudgetOverride.calm());
    }

    /**
     * The clinic's take-away supplies, placed near the exit so they are on the natural path out. The
     * stock COUNT rides the enemy-damage depth curve (GameMath.depthScaledPickupCount): a REST node's
     * whole payoff is healing, and flat hit points buy a shrinking share of the fight as depth scales,
     * so a fixed stock would make the deep clinic a dead choice. One medkit early, three at depth 15 —
     * "the deeper the facility, the better stocked its medical bay" (order 7, R-CALM-COST).
     */
    private List<GuaranteedContent> buildClinicStock(int depth, long seed) {
        List<GuaranteedContent> stock = new ArrayList<>();
        int medkits = GameMath.depthScaledPickupCount(RouteMapConstants.REST_MEDKITS,
                BalanceConfig.ENEMY_DAMAGE_SCALE_PER_DEPTH, depth);
        if (medkits > 0) {
            stock.add(Guarantees.pickup('H', medkits, Placement.NEAR_EXIT, seed));
        }
        int armour = GameMath.depthScaledPickupCount(RouteMapConstants.REST_ARMOUR,
                BalanceConfig.ENEMY_DAMAGE_SCALE_PER_DEPTH, depth);
        if (armour > 0) {
            char armourSymbol = depth >= RouteMapConstants.REST_ARMOUR_VEST_DEPTH ? 'A' : 'a';
            stock.add(Guarantees.pickup(armourSymbol, armour, Placement.NEAR_EXIT, seed));
        }
        return stock;
    }
}
