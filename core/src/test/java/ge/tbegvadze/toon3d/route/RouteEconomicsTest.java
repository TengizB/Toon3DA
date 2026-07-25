package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.RouteMapConstants;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless tests for the ROUTE ECONOMICS subsystem (new-game-balancr order 7): the node EV ledger,
 * the map-gen guarantees it depends on, and — most importantly — that a ledger row can never drift
 * from the floor the profile actually builds.
 *
 * <p>The BAND checks (R-RISK-PREMIUM, R-CALM-COST, R-MYSTERY-EV, R-PIPS-DERIVED, R-HONEST-SAFE,
 * R-TRAJECTORY, R-ROUTE-GUARANTEES) live in {@code BalanceAuditTest} with the rest of the balance
 * contract; these tests cover the STRUCTURE the audit reads.
 */
class RouteEconomicsTest {

    private NodeEconomicsRegistry freshLedger() {
        NodeEconomicsRegistry registry = new NodeEconomicsRegistry();
        RouteEconomics.registerAll(registry);
        return registry;
    }

    private RouteMapGenerator generator() {
        NodeTypeRegistry nodeTypes = new NodeTypeRegistry();
        RouteRegistries.registerNodeTypes(nodeTypes);
        GeneratorRegistry generators = new GeneratorRegistry();
        RouteRegistries.registerGenerators(generators);
        NodeAffixRegistry affixes = new NodeAffixRegistry();
        RouteRegistries.registerAffixes(affixes);
        RouteMapGenerator generator = new RouteMapGenerator(nodeTypes, generators);
        generator.setEliteAffixPool(affixes.elitePool());
        return generator;
    }

    // ---------------------------------------------------------------------
    // The ledger covers the map, and its threat prices match the real floors
    // ---------------------------------------------------------------------

    @Test
    void everyNodeTypeAffixAndOutcomeHasALedgerRow() {
        NodeEconomicsRegistry ledger = freshLedger();
        for (RouteNodeType type : RouteNodeType.values()) {
            assertNotNull(ledger.forNodeType(type), "unpriced node type: " + type);
        }
        NodeAffixRegistry affixes = new NodeAffixRegistry();
        RouteRegistries.registerAffixes(affixes);
        for (String affixId : affixes.ids()) {
            assertNotNull(ledger.get(affixId), "unpriced affix: " + affixId);
        }
        for (MysteryOutcome outcome : MysteryOutcome.values()) {
            assertNotNull(ledger.get(outcome.name()), "unpriced mystery outcome: " + outcome);
        }
    }

    /**
     * The load-bearing anti-drift check: a ledger row's {@code budgetScale} must equal the budget the
     * node's PROFILE actually asks the encounter planner for. If a profile is re-tuned without its
     * price, every route rule silently audits a floor that no longer exists.
     */
    @Test
    void ledgerThreatMatchesTheBudgetTheProfileRequests() {
        NodeEconomicsRegistry ledger = freshLedger();
        NodeAffixRegistry affixes = new NodeAffixRegistry();
        RouteRegistries.registerAffixes(affixes);

        // CACHE: a calm depot.
        LevelPlan cachePlan = new CacheProfile().resolve(node(RouteNodeType.CACHE), 3, 11L);
        assertEquals(ledger.forNodeType(RouteNodeType.CACHE).budgetScale(),
                cachePlan.enemyBudget().budgetScale(), 1e-4f,
                "CACHE ledger price must match CacheProfile's requested budget");

        // ELITE: the priced mini-setpiece.
        LevelPlan elitePlan = new EliteProfile(new GeneratorRegistry(), affixes)
                .resolve(node(RouteNodeType.ELITE), 3, 11L);
        assertEquals(ledger.forNodeType(RouteNodeType.ELITE).budgetScale(),
                elitePlan.enemyBudget().budgetScale(), 1e-4f,
                "ELITE ledger price must match EliteProfile's requested budget");

        // SHOP: light resistance.
        LevelPlan shopPlan = new ShopProfile(new GeneratorRegistry()).resolve(node(RouteNodeType.SHOP), 3, 11L);
        assertEquals(ledger.forNodeType(RouteNodeType.SHOP).budgetScale(),
                shopPlan.enemyBudget().budgetScale(), 1e-4f,
                "SHOP ledger price must match ShopProfile's requested budget");

        // REST / EVENT / GATE are priced at ZERO threat because their bespoke generators emit no enemy
        // spawn points at all — the honest read of a sanctuary, whatever budget the plan carries.
        assertEquals(0f, ledger.forNodeType(RouteNodeType.REST).budgetScale(), 1e-4f);
        assertEquals(0f, ledger.forNodeType(RouteNodeType.EVENT).budgetScale(), 1e-4f);
        assertEquals(0f, ledger.forNodeType(RouteNodeType.REGION_GATE).budgetScale(), 1e-4f);
    }

    /**
     * R-RISK-PREMIUM's supply side: an affix that raises the ELITE budget must raise the vault with
     * it. SWARM and OVERCLOCKED therefore stamp MORE owned-ammo boxes than a plain elite vault.
     */
    @Test
    void threatRaisingAffixesRaiseTheVaultTheyArePricedAgainst() {
        NodeAffixRegistry affixes = new NodeAffixRegistry();
        RouteRegistries.registerAffixes(affixes);
        EliteProfile profile = new EliteProfile(new GeneratorRegistry(), affixes);

        int plainBoxes = profile.resolve(node(RouteNodeType.ELITE), 3, 11L).ammoCache().boxCount();
        for (String affixId : new String[]{RouteMapConstants.AFFIX_SWARM_ID,
                RouteMapConstants.AFFIX_OVERCLOCKED_ID}) {
            RouteNode affixed = node(RouteNodeType.ELITE);
            affixed.affix = affixes.definition(affixId).toToken();
            LevelPlan plan = profile.resolve(affixed, 3, 11L);
            assertTrue(plan.enemyBudget().budgetScale() > BalanceConfig.ELITE_BUDGET_SCALE - 1e-4f,
                    affixId + " must raise (or hold) the elite budget");
            assertTrue(plan.ammoCache().boxCount() > plainBoxes,
                    affixId + " raises the threat, so its vault must price up with it");
        }
    }

    /**
     * The MED-BAY's take-away stock scales with depth — a flat stock would decay into a dead node as
     * incoming damage compounds. {@code RestProfile} stamps
     * {@code GameMath.depthScaledPickupCount(...)} copies of each supply, so the guarantee LIST is the
     * same shape at every depth while the number of pickups inside it grows.
     */
    @Test
    void clinicStockScalesWithDepth() {
        RestProfile profile = new RestProfile();
        assertTrue(profile.resolve(node(RouteNodeType.REST), 1, 7L).guarantees().size() > 0,
                "the clinic must stock take-away supplies");
        int shallowMedkits = GameMath.depthScaledPickupCount(BalanceConfig.REST_MEDKITS,
                BalanceConfig.ENEMY_DAMAGE_SCALE_PER_DEPTH, 1);
        int deepMedkits = GameMath.depthScaledPickupCount(BalanceConfig.REST_MEDKITS,
                BalanceConfig.ENEMY_DAMAGE_SCALE_PER_DEPTH, 14);
        assertEquals(BalanceConfig.REST_MEDKITS, shallowMedkits, "depth 1 stocks the authored count");
        assertTrue(deepMedkits > shallowMedkits, "a deep clinic must stock more medkits than a shallow one");
    }

    // ---------------------------------------------------------------------
    // R-ROUTE-GUARANTEES at the generator level
    // ---------------------------------------------------------------------

    @Test
    void generatedMapsSatisfyTheRouteGuarantees() {
        for (long seed = 0; seed < 25; seed++) {
            RouteMap map = generator().generate(seed, RegionPlan.defaultPlan());
            List<String> violations = RouteEconomicsModel.guaranteeViolations(map);
            assertTrue(violations.isEmpty(),
                    () -> "seed " + (map.getRunSeed()) + " route guarantees: " + violations);
        }
    }

    /** The guarantee post-pass must be deterministic — it is a no-RNG repair, so seeds stay shareable. */
    @Test
    void guaranteeRepairIsDeterministic() {
        for (long seed = 0; seed < 10; seed++) {
            RouteMap first  = generator().generate(seed, RegionPlan.defaultPlan());
            RouteMap second = generator().generate(seed, RegionPlan.defaultPlan());
            List<List<RouteNode>> firstLayers  = first.getLayers();
            List<List<RouteNode>> secondLayers = second.getLayers();
            assertEquals(firstLayers.size(), secondLayers.size(), "layer count must be deterministic");
            for (int layerIndex = 0; layerIndex < firstLayers.size(); layerIndex++) {
                for (int lane = 0; lane < firstLayers.get(layerIndex).size(); lane++) {
                    assertEquals(firstLayers.get(layerIndex).get(lane).type,
                            secondLayers.get(layerIndex).get(lane).type,
                            "same seed must repair to the same node types");
                }
            }
        }
    }

    /** Endless extension must be repaired exactly like a band built up front. */
    @Test
    void lazilyExtendedBandsCarryTheSameGuarantees() {
        RouteMapGenerator extendingGenerator = generator();
        RegionPlan plan = RegionPlan.defaultPlan();
        RouteMap map = extendingGenerator.generate(4242L, plan);
        extendingGenerator.extendWithNextRegion(map, plan);
        assertTrue(RouteEconomicsModel.guaranteeViolations(map).isEmpty(),
                "an appended THE BREACH band must be as fair as one generated up front");
    }

    private RouteNode node(RouteNodeType type) {
        RouteNode node = new RouteNode(type, 3, 0);
        node.nodeSeed = 12345L;
        return node;
    }
}
