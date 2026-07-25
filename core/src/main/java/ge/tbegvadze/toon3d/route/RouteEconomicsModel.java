package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.ArrayList;
import java.util.List;

/**
 * THE PRICED MAP (new-game-balancr order 7). Turns a {@link NodeEconomics} ledger row into the
 * numbers the balance contract bands — what a node COSTS in Threat Points, what it is WORTH in
 * power points, and what a whole JOURNEY through the DAG accumulates — and walks real generated
 * {@link RouteMap}s under deterministic path policies so the audited unit becomes the journey
 * instead of the floor.
 *
 * <p>Everything is derived from the models orders 3/4/5 already own; nothing is re-invented here:
 * <ul>
 *   <li>THREAT — {@code GameMath.regionScaledFloorThreatPointBudget} x the node's budget scale
 *       (and its affix multiplier), via {@code GameMath.nodeThreatCost}.</li>
 *   <li>AMMO — the order-3 supply/demand curves ({@code ammoSupplyAtDepth},
 *       {@code floorDemandAtDepth}), split into the ROOM-sourced share (constant per floor) and the
 *       KILL-sourced share (which follows the roster, so it follows the budget scale).</li>
 *   <li>HP — the order-3 heal economy ({@code incomingDamagePerFloor} / {@code healSupplyPerFloor}
 *       baselines), measured as a fraction of the player's CURRENT-difficulty eHP, which is why the
 *       shared enemy-damage depth factor is divided back out.</li>
 *   <li>CREDITS + XP — the order-3 income model and the order-4 derived-XP model.</li>
 * </ul>
 * The common currency is POWER POINTS (the order-2/3 unit): ammo damage /
 * {@code SHOP_AMMO_DAMAGE_PER_POWER_POINT}, HP / {@code SHOP_HEAL_HP_PER_POWER_POINT}, credits /
 * {@code SHOP_CREDITS_PER_POWER_POINT}, one level / one weapon-class buy = its PP value.
 *
 * <p>Pure / headless — no LibGDX imports, no mutation of anything it is handed. The order-3
 * MODEL-FLOOR baselines are passed IN ({@link ModelFloor}) rather than recomputed, so the map's
 * prices and the floor contract can never drift apart.
 */
public final class RouteEconomicsModel {

    private RouteEconomicsModel() {}

    // =====================================================================================
    // INPUTS — the depth-1 model floor (order 3) the whole map is priced against.
    // =====================================================================================

    /**
     * The order-3 MODEL FLOOR baselines, supplied by {@code BalanceSchema} so the map is priced
     * against the very same reference encounter the floor rules use. All values are depth-1.
     */
    public static final class ModelFloor {
        public final float demandDamage;
        public final float roomSourcedSupplyDamage;
        public final float killSourcedSupplyDamage;
        public final float averageAmmoBoxDamage;
        public final float incomingHitPoints;
        public final float healSupplyHitPoints;
        public final float killCredits;
        public final float chipCredits;
        public final float[] regionAbilityBudgetPoints;

        /**
         * @param demandDamage             sum of the model floor's enemy eHP (what a floor costs in ammo)
         * @param roomSourcedSupplyDamage  ammo damage the floor's ROOMS supply (independent of the roster)
         * @param killSourcedSupplyDamage  ammo damage the floor's KILLS supply (follows the roster)
         * @param averageAmmoBoxDamage     damage one guaranteed ammo box is worth
         * @param incomingHitPoints        HP the model floor's roster lands on the player
         * @param healSupplyHitPoints      HP the model floor's medkit/armour pickups restore
         * @param killCredits              credits the model floor's roster pays out
         * @param chipCredits              credit-chip income placed on any floor (roster-independent)
         * @param regionAbilityBudgetPoints per-region weapon-ability PP (order-4 honest power input)
         */
        public ModelFloor(float demandDamage, float roomSourcedSupplyDamage, float killSourcedSupplyDamage,
                          float averageAmmoBoxDamage, float incomingHitPoints, float healSupplyHitPoints,
                          float killCredits, float chipCredits, float[] regionAbilityBudgetPoints) {
            this.demandDamage              = demandDamage;
            this.roomSourcedSupplyDamage   = roomSourcedSupplyDamage;
            this.killSourcedSupplyDamage   = killSourcedSupplyDamage;
            this.averageAmmoBoxDamage      = averageAmmoBoxDamage;
            this.incomingHitPoints         = incomingHitPoints;
            this.healSupplyHitPoints       = healSupplyHitPoints;
            this.killCredits               = killCredits;
            this.chipCredits               = chipCredits;
            this.regionAbilityBudgetPoints = regionAbilityBudgetPoints;
        }
    }

    // =====================================================================================
    // OUTPUT — one node's full price tag at one depth.
    // =====================================================================================

    /** Everything the audit and the report need about one priced node at one depth. */
    public static final class NodePrice {
        public final String id;
        public final String displayName;
        /** Threat Points the node's roster is worth at this depth (0 for a zero-spawn sanctuary). */
        public final float  threatCost;
        /** Ammo damage the floor hands over (ordinary room/kill loot + guaranteed boxes). */
        public final float  supplyDamage;
        /** Ammo damage the floor's roster demands. */
        public final float  demandDamage;
        /** HP the floor lands on the player, normalised to depth-1 eHP terms. */
        public final float  incomingHitPoints;
        /** HP the floor restores (ordinary pickups + guarantees), normalised to depth-1 eHP terms. */
        public final float  healHitPoints;
        /** XP the floor's roster (plus any guaranteed grant) is worth. */
        public final float  experiencePoints;
        /** Resources gained minus resources consumed, in power points. */
        public final float  resourceDeltaPowerPoints;
        /** XP value plus upgrade-opportunity value, in power points (BEFORE the durability weight). */
        public final float  progressPowerPoints;
        /** What the node HANDS OVER (gains only, progress already durability-weighted). */
        public final float  rewardPowerPoints;
        /** The folded expected value — the one comparable number (GameMath.nodeExpectedValue). */
        public final float  expectedValue;

        NodePrice(String id, String displayName, float threatCost, float supplyDamage, float demandDamage,
                  float incomingHitPoints, float healHitPoints, float experiencePoints,
                  float resourceDeltaPowerPoints, float progressPowerPoints,
                  float rewardPowerPoints, float expectedValue) {
            this.id                       = id;
            this.displayName              = displayName;
            this.threatCost               = threatCost;
            this.supplyDamage             = supplyDamage;
            this.demandDamage             = demandDamage;
            this.incomingHitPoints        = incomingHitPoints;
            this.healHitPoints            = healHitPoints;
            this.experiencePoints         = experiencePoints;
            this.resourceDeltaPowerPoints = resourceDeltaPowerPoints;
            this.progressPowerPoints      = progressPowerPoints;
            this.rewardPowerPoints        = rewardPowerPoints;
            this.expectedValue            = expectedValue;
        }
    }

    // =====================================================================================
    // PRICING
    // =====================================================================================

    /** The reference every route band is measured against: a standard COMBAT node at this depth. */
    public static NodePrice standardCombat(NodeEconomicsRegistry ledger, ModelFloor floor, int depth) {
        NodeEconomics combat = ledger.get(RouteEconomics.STANDARD_COMBAT_ID);
        return price(ledger, combat, null, floor, depth);
    }

    /** Prices one ledger row (node, or a mystery outcome) at a depth, with no affix. */
    public static NodePrice price(NodeEconomicsRegistry ledger, NodeEconomics row,
                                  ModelFloor floor, int depth) {
        return price(ledger, row, null, floor, depth);
    }

    /**
     * Prices one ledger row at a depth, optionally with an AFFIX row folded on (its budget
     * multiplier raises the threat; its vault premium raises the reward).
     *
     * <p>Two rows resolve indirectly, and both are handled here rather than at every call site:
     * a row with a {@link NodeEconomics#delegateId()} (a delegating MYSTERY outcome) is priced as
     * its delegate, and a row with a {@link NodeEconomics#hiddenOutcomeTable()} (the MYSTERY node
     * itself) is priced as the WEIGHTED AGGREGATE of the outcome table — which is exactly the
     * quantity R-MYSTERY-EV bands.
     */
    public static NodePrice price(NodeEconomicsRegistry ledger, NodeEconomics row, NodeEconomics affix,
                                  ModelFloor floor, int depth) {
        if (row == null) {
            return null;
        }
        if (row.delegateId() != null) {
            NodeEconomics delegate = ledger.get(row.delegateId());
            NodePrice delegated = price(ledger, delegate, affix, floor, depth);
            return delegated == null ? null
                    : new NodePrice(row.id(), row.displayName(), delegated.threatCost,
                            delegated.supplyDamage, delegated.demandDamage, delegated.incomingHitPoints,
                            delegated.healHitPoints, delegated.experiencePoints,
                            delegated.resourceDeltaPowerPoints, delegated.progressPowerPoints,
                            delegated.rewardPowerPoints, delegated.expectedValue);
        }
        if (row.hiddenOutcomeTable()) {
            return weightedOutcomeTablePrice(ledger, row, floor, depth);
        }
        return priceDirect(ledger, row, affix, floor, depth);
    }

    /** The weighted mean over every registered MYSTERY outcome — the hidden table's honest price. */
    private static NodePrice weightedOutcomeTablePrice(NodeEconomicsRegistry ledger, NodeEconomics row,
                                                       ModelFloor floor, int depth) {
        List<NodeEconomics> outcomes = ledger.allOfKind(NodeEconomics.Kind.MYSTERY_OUTCOME);
        float totalWeight = 0f;
        float threat = 0f, supply = 0f, demand = 0f, incoming = 0f, heal = 0f, experience = 0f;
        float resourceDelta = 0f, progress = 0f, reward = 0f, expectedValue = 0f;
        for (NodeEconomics outcome : outcomes) {
            NodePrice outcomePrice = price(ledger, outcome, null, floor, depth);
            if (outcomePrice == null) {
                continue;
            }
            float weight = outcome.tableWeight();
            totalWeight   += weight;
            threat        += weight * outcomePrice.threatCost;
            supply        += weight * outcomePrice.supplyDamage;
            demand        += weight * outcomePrice.demandDamage;
            incoming      += weight * outcomePrice.incomingHitPoints;
            heal          += weight * outcomePrice.healHitPoints;
            experience    += weight * outcomePrice.experiencePoints;
            resourceDelta += weight * outcomePrice.resourceDeltaPowerPoints;
            progress      += weight * outcomePrice.progressPowerPoints;
            reward        += weight * outcomePrice.rewardPowerPoints;
            expectedValue += weight * outcomePrice.expectedValue;
        }
        if (totalWeight <= 0f) {
            return new NodePrice(row.id(), row.displayName(), 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
        }
        return new NodePrice(row.id(), row.displayName(), threat / totalWeight, supply / totalWeight,
                demand / totalWeight, incoming / totalWeight, heal / totalWeight, experience / totalWeight,
                resourceDelta / totalWeight, progress / totalWeight, reward / totalWeight,
                expectedValue / totalWeight);
    }

    /** The actual pricing arithmetic for a row that neither delegates nor hides an outcome table. */
    private static NodePrice priceDirect(NodeEconomicsRegistry ledger, NodeEconomics row,
                                         NodeEconomics affix, ModelFloor floor, int depth) {
        int   band       = BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE;
        float affixScale = affix == null ? 1f : affix.budgetScale();
        float rosterScale = row.budgetScale() * affixScale;
        float lootScale   = row.ordinaryLootScale();

        // --- THREAT: the depth- and region-ramped budget this node's roster spends.
        float floorBudget = GameMath.regionScaledFloorThreatPointBudget(
                BalanceConfig.FLOOR_BASE_THREAT_POINT_BUDGET, BalanceConfig.ENEMY_HEALTH_SCALE_PER_DEPTH,
                BalanceConfig.ENEMY_DAMAGE_SCALE_PER_DEPTH, depth, BalanceConfig.REGION_TP_BUDGET_MULTIPLIER,
                band) * BalanceConfig.ENCOUNTER_BUDGET_FILL_TARGET_FRACTION;
        float threatCost = GameMath.nodeThreatCost(floorBudget, row.budgetScale(), affixScale);

        // --- AMMO: room-sourced supply is roster-independent, kill-sourced supply follows the roster.
        float ordinarySupplyBase = floor.roomSourcedSupplyDamage + floor.killSourcedSupplyDamage * rosterScale;
        float supplyDamage = GameMath.ammoSupplyAtDepth(ordinarySupplyBase * lootScale,
                BalanceConfig.GEAR_CURVE_PER_REGION, BalanceConfig.AMMO_SUPPLY_REGION_MULTIPLIER, depth, band);
        float guaranteedBoxes = row.guaranteedAmmoBoxes() + (affix == null ? 0f : affix.guaranteedAmmoBoxes());
        supplyDamage += guaranteedBoxes * floor.averageAmmoBoxDamage
                * GameMath.gearCurveAtDepth(BalanceConfig.GEAR_CURVE_PER_REGION, depth, band);
        float demandDamage = GameMath.floorDemandAtDepth(floor.demandDamage,
                BalanceConfig.ENEMY_HEALTH_SCALE_PER_DEPTH, depth) * rosterScale;

        // --- HP: incoming and ordinary heal supply both ride the enemy-DAMAGE curve, as does the
        // player's current-difficulty eHP (order 3), so the shared factor is divided back out and the
        // HP side is read in depth-1 eHP terms. A FLAT guaranteed medkit therefore correctly decays in
        // relative value as depth scales, while the MED-BAY's max-HP-share heal correctly does not.
        float damageDepthFactor = GameMath.compoundDepthMultiplier(
                BalanceConfig.ENEMY_DAMAGE_SCALE_PER_DEPTH, depth);
        float healRegionMultiplier = GameMath.perRegionMultiplierAtDepth(
                BalanceConfig.HEAL_SUPPLY_REGION_MULTIPLIER, depth, band);
        float incomingHitPoints = floor.incomingHitPoints * rosterScale;
        float healHitPoints = floor.healSupplyHitPoints * lootScale * healRegionMultiplier
                + (row.guaranteedHealHitPoints() + (affix == null ? 0f : affix.guaranteedHealHitPoints()))
                        / damageDepthFactor
                + row.guaranteedHealEffectiveHitPointFraction() * BalanceConfig.REFERENCE_PLAYER_EHP;

        // --- CREDITS: chips are placed on any floor; kill bounty follows the roster and grows with depth.
        float credits = floor.chipCredits * lootScale
                + floor.killCredits * rosterScale * (1f + BalanceConfig.CREDIT_DEPTH_SCALE * (depth - 1))
                + row.creditDelta();

        // --- XP: order 4's derived model — roster XP is the knob times the Threat actually faced. A
        // hand-set grant (an EVENT choice's payout) is authored at depth 1 and re-expressed as the same
        // share of a level here, exactly as World grants it (GameMath.depthScaledRewardXp).
        int   expectedLevel = GameMath.expectedLevelAtDepth(BalanceConfig.EXPECTED_LEVELS_PER_DEPTH, depth);
        int   levelCost     = Math.max(1, GameMath.xpRequiredForLevelGeometric(
                BalanceConfig.XP_BASE_REQUIREMENT, BalanceConfig.XP_CURVE_GROWTH_PER_LEVEL, expectedLevel));
        float experiencePoints = BalanceConfig.XP_PER_THREAT_POINT * threatCost
                + row.guaranteedExperiencePoints() * levelCost / BalanceConfig.XP_BASE_REQUIREMENT;

        // --- Fold into power points.
        float supplyPowerPoints = supplyDamage / BalanceConfig.SHOP_AMMO_DAMAGE_PER_POWER_POINT;
        float demandPowerPoints = demandDamage / BalanceConfig.SHOP_AMMO_DAMAGE_PER_POWER_POINT;
        float healPowerPoints     = healHitPoints     / BalanceConfig.SHOP_HEAL_HP_PER_POWER_POINT;
        float incomingPowerPoints = incomingHitPoints / BalanceConfig.SHOP_HEAL_HP_PER_POWER_POINT;
        float creditPowerPoints   = credits / BalanceConfig.SHOP_CREDITS_PER_POWER_POINT;
        float experiencePowerPoints = experiencePoints / levelCost * BalanceConfig.LEVEL_UP_BUDGET_PP;
        float upgradePowerPoints    = row.upgradeOpportunity()
                * BalanceConfig.ROUTE_UPGRADE_OPPORTUNITY_POWER_POINTS;

        float resourceDelta = (supplyPowerPoints - demandPowerPoints)
                + (healPowerPoints - incomingPowerPoints) + creditPowerPoints;
        float progress = experiencePowerPoints + upgradePowerPoints;
        float reward = supplyPowerPoints + healPowerPoints + creditPowerPoints
                + BalanceConfig.ROUTE_PERMANENT_POWER_WEIGHT * progress;
        // The danger charge rides the threat RATIO (this roster over a standard combat floor's at the
        // same depth), which is exactly rosterScale — so one standard floor always costs one standard
        // floor's worth of risk however deep the run has gone.
        float expectedValue = GameMath.nodeExpectedValue(resourceDelta, progress, rosterScale,
                BalanceConfig.ROUTE_PERMANENT_POWER_WEIGHT,
                BalanceConfig.ROUTE_RISK_POWER_POINTS_PER_STANDARD_FLOOR);

        return new NodePrice(row.id(), row.displayName(), threatCost, supplyDamage, demandDamage,
                incomingHitPoints, healHitPoints, experiencePoints, resourceDelta, progress,
                reward, expectedValue);
    }

    /** The threat ratio a node reads against a standard COMBAT node at the same depth (drives the pips). */
    public static float threatRatioVsStandardCombat(NodeEconomicsRegistry ledger, NodeEconomics row,
                                                    ModelFloor floor, int depth) {
        NodePrice standard = standardCombat(ledger, floor, depth);
        NodePrice node     = price(ledger, row, floor, depth);
        if (standard == null || node == null || standard.threatCost <= 0f) {
            return 0f;
        }
        return node.threatCost / standard.threatCost;
    }

    // =====================================================================================
    // THE TRAJECTORY AUDIT — the JOURNEY becomes the audited unit.
    // =====================================================================================

    /**
     * The three deterministic route policies the audit walks. Their band ENDS are the game's real
     * difficulty range: SAFEST rides the generous edge, DEADLIEST the starved edge, and BOTH must
     * still land inside the trajectory bands for the map to be fair however the player plays it.
     */
    public enum PathPolicy {
        /** Always the lowest-threat selectable node (calm-chaining). */
        SAFEST,
        /** Always the highest-threat selectable node (the gauntlet run). */
        DEADLIEST,
        /** Alternates: the mid-threat option, biased low then high (a normal, mixed route). */
        BALANCED
    }

    /** One cumulative reading of a walked journey, taken at a region boundary. */
    public static final class TrajectorySample {
        public final int   depth;
        public final int   regionIndex;
        public final int   floorsWalked;
        /** Cumulative SUPPLY / DEMAND over the nodes actually visited (GameMath.trajectoryResourceLedger). */
        public final float scarcityRatio;
        /** Average per-floor net HP drain, as a fraction of the player's current-difficulty eHP. */
        public final float netDrainFraction;
        /** Banked XP / XP needed to stand at the depth's expected level. */
        public final float experiencePace;
        /** Depth coupling measured at the level the journey's XP actually bought. */
        public final float couplingRatio;
        /** The level that banked XP actually buys. */
        public final int   playerLevel;

        TrajectorySample(int depth, int regionIndex, int floorsWalked, float scarcityRatio,
                         float netDrainFraction, float experiencePace, float couplingRatio, int playerLevel) {
            this.depth            = depth;
            this.regionIndex      = regionIndex;
            this.floorsWalked     = floorsWalked;
            this.scarcityRatio    = scarcityRatio;
            this.netDrainFraction = netDrainFraction;
            this.experiencePace   = experiencePace;
            this.couplingRatio    = couplingRatio;
            this.playerLevel      = playerLevel;
        }
    }

    /**
     * Walks one deterministic path through a real generated map and samples the cumulative order-3/4
     * quantities at every region boundary — over the ACTUAL nodes visited, each priced by its own
     * ledger row, never by the combat-floor average.
     *
     * @param map      a generated route map (its raw DAG is walked; no cursor state is mutated)
     * @param policy   which lane the walker takes at every branch
     * @param ledger   the node EV ledger
     * @param floor    the order-3 model-floor baselines
     * @param maxDepth stop after this depth (the audited range)
     */
    public static List<TrajectorySample> walk(RouteMap map, PathPolicy policy,
                                              NodeEconomicsRegistry ledger, ModelFloor floor, int maxDepth) {
        List<TrajectorySample> samples = new ArrayList<>();
        RouteNode current = map.getCurrent();
        if (current == null) {
            return samples;
        }
        int   band = BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE;
        float cumulativeSupply = 0f;
        float cumulativeDemand = 0f;
        float cumulativeDrainFraction = 0f;
        float cumulativeExperience = 0f;
        int   floorsWalked = 0;
        int   step = 0;

        while (true) {
            RouteNode next = chooseNext(current, policy, ledger, floor, step);
            if (next == null || next.depth > maxDepth) {
                break;
            }
            NodeEconomics row = ledger.forNodeType(next.type);
            NodePrice nodePrice = price(ledger, row, affixRowFor(ledger, next), floor, next.depth);
            if (nodePrice != null) {
                cumulativeSupply += nodePrice.supplyDamage;
                cumulativeDemand += nodePrice.demandDamage;
                cumulativeDrainFraction += (nodePrice.incomingHitPoints - nodePrice.healHitPoints)
                        / BalanceConfig.REFERENCE_PLAYER_EHP;
                cumulativeExperience += nodePrice.experiencePoints;
            }
            floorsWalked++;
            current = next;
            step++;

            boolean regionBoundary = next.depth % band == 0;
            if (regionBoundary) {
                samples.add(sample(next.depth, floorsWalked, cumulativeSupply, cumulativeDemand,
                        cumulativeDrainFraction, cumulativeExperience, floor, band));
            }
        }
        return samples;
    }

    private static TrajectorySample sample(int depth, int floorsWalked, float cumulativeSupply,
                                           float cumulativeDemand, float cumulativeDrainFraction,
                                           float cumulativeExperience, ModelFloor floor, int band) {
        float scarcity = GameMath.trajectoryResourceLedger(cumulativeSupply, cumulativeDemand);
        float drain    = floorsWalked <= 0 ? 0f : cumulativeDrainFraction / floorsWalked;
        int   expectedLevel = GameMath.expectedLevelAtDepth(BalanceConfig.EXPECTED_LEVELS_PER_DEPTH, depth);
        float expectedExperience = Math.max(1f, GameMath.cumulativeXpRequiredToReachLevel(
                BalanceConfig.XP_BASE_REQUIREMENT, BalanceConfig.XP_CURVE_GROWTH_PER_LEVEL, expectedLevel));
        float experiencePace = cumulativeExperience / expectedExperience;
        int   playerLevel = GameMath.levelForCumulativeXp(BalanceConfig.XP_BASE_REQUIREMENT,
                BalanceConfig.XP_CURVE_GROWTH_PER_LEVEL, cumulativeExperience);
        float playerPower = GameMath.playerPowerAtLevelAndDepth(BalanceConfig.LEVEL_UP_BUDGET_PP,
                playerLevel, BalanceConfig.GEAR_CURVE_PER_REGION, floor.regionAbilityBudgetPoints, depth, band);
        float enemyThreat = GameMath.depthThreatScale(BalanceConfig.ENEMY_HEALTH_SCALE_PER_DEPTH,
                BalanceConfig.ENEMY_DAMAGE_SCALE_PER_DEPTH, depth);
        float coupling = GameMath.depthCouplingRatio(playerPower, enemyThreat);
        return new TrajectorySample(depth, GameMath.regionIndexAtDepth(depth, band), floorsWalked,
                scarcity, drain, experiencePace, coupling, playerLevel);
    }

    /** The affix ledger row a node carries, or {@code null} when it rolled none. */
    private static NodeEconomics affixRowFor(NodeEconomicsRegistry ledger, RouteNode node) {
        return node.affix == null ? null : ledger.get(node.affix.id());
    }

    /**
     * The policy's pick among a node's forward edges. Ties break on lane order, so a walk is fully
     * deterministic for a given map (the audit's "same seed, same journey" requirement).
     */
    private static RouteNode chooseNext(RouteNode current, PathPolicy policy,
                                        NodeEconomicsRegistry ledger, ModelFloor floor, int step) {
        if (current.outgoing.isEmpty()) {
            return null;
        }
        RouteNode best = null;
        float bestThreat = 0f;
        List<RouteNode> candidates = current.outgoing;
        // BALANCED alternates between the safe and the dangerous end, which is what a real player
        // does: bank on a calm node, then spend the banked resources on a hot one.
        boolean preferHigh = policy == PathPolicy.DEADLIEST
                || (policy == PathPolicy.BALANCED && step % 2 == 1);
        for (RouteNode candidate : candidates) {
            NodeEconomics row = ledger.forNodeType(candidate.type);
            NodePrice candidatePrice = price(ledger, row, affixRowFor(ledger, candidate), floor, candidate.depth);
            float threat = candidatePrice == null ? 0f : candidatePrice.threatCost;
            if (best == null
                    || (preferHigh && threat > bestThreat)
                    || (!preferHigh && threat < bestThreat)) {
                best       = candidate;
                bestThreat = threat;
            }
        }
        return best;
    }

    // =====================================================================================
    // R-ROUTE-GUARANTEES — the reachability checks the generator's post-pass must satisfy.
    // =====================================================================================

    /** Node types that can hand the player a weapon-class upgrade (the order-2 pity rule's currency). */
    public static boolean isUpgradeBearing(RouteNodeType type) {
        return type == RouteNodeType.ELITE || type == RouteNodeType.SHOP;
    }

    /** Node types that are a genuine pressure-release valve (the resource-relief currency). */
    public static boolean isCalm(RouteNodeType type) {
        return type == RouteNodeType.CACHE || type == RouteNodeType.REST;
    }

    /**
     * The deepest node in a region from which the per-region UPGRADE and CALM guarantees are required
     * (and repaired). A source needs ROOM ahead of it to carry both promises: with only one rollable
     * layer left, satisfying both from every lane would collapse that layer into a single node type —
     * a fake choice, which is itself a guarantee violation. So the requirement covers every node with
     * at least two rollable layers ahead:
     * <pre>lastGuaranteedSourceDepth = region.lastDepth - 3</pre>
     * (the region's last depth is the forced convergence node, the one before it is the pre-boss layer,
     * and one more layer of room is what lets a repair place an upgrade AND a calm option on different
     * lanes). Deeper nodes are covered by the PRE-BOSS PROVISIONING guarantee — a reachable SHOP/CACHE
     * in the layer before the boss — so no lane is ever left without a resupply before the wall.
     */
    public static int lastGuaranteedSourceDepth(int regionLastDepth) {
        return regionLastDepth - 3;
    }

    /**
     * Verifies R-ROUTE-GUARANTEES on a generated map and returns one line per violation (empty when
     * the map is fair). A branching map can strand a floor-level promise on the lane not taken, so
     * each guarantee is checked as a GRAPH property — from EVERY reachable node, the REMAINING DAG
     * must still satisfy it:
     * <ul>
     *   <li>from every node of a region, at least one forward path inside that region still reaches
     *       an upgrade-bearing node, and one still reaches a calm node (no lane strands a run);</li>
     *   <li>the layer before every boss offers a reachable SHOP or CACHE (order-6 provisioning);</li>
     *   <li>every selectable layer offers at least
     *       {@link BalanceConfig#ROUTE_MIN_NODE_TYPES_PER_LAYER} distinct node types (no fake choices).</li>
     * </ul>
     */
    public static List<String> guaranteeViolations(RouteMap map) {
        List<String> violations = new ArrayList<>();
        List<List<RouteNode>> layers = map.getLayers();
        int band = BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE;

        for (List<RouteNode> layer : layers) {
            if (layer.size() < 2) {
                continue; // forced convergence layers (boss / gate / the start node) offer no choice
            }
            java.util.EnumSet<RouteNodeType> distinct = java.util.EnumSet.noneOf(RouteNodeType.class);
            for (RouteNode node : layer) {
                distinct.add(node.type);
            }
            if (distinct.size() < BalanceConfig.ROUTE_MIN_NODE_TYPES_PER_LAYER) {
                violations.add("depth " + layer.get(0).depth + ": only " + distinct.size()
                        + " distinct node type(s) offered (" + distinct + ")");
            }
        }

        for (RouteRegion region : map.getRegions()) {
            for (List<RouteNode> layer : layers) {
                if (layer.isEmpty() || !region.containsDepth(layer.get(0).depth)) {
                    continue;
                }
                for (RouteNode node : layer) {
                    if (node.type == RouteNodeType.BOSS || node.type == RouteNodeType.REGION_GATE
                            || node.depth > lastGuaranteedSourceDepth(region.lastDepth())) {
                        // A node in the band's tail has too little room ahead to carry both promises
                        // without collapsing a layer into one type; it is covered by the pre-boss
                        // PROVISIONING guarantee checked below instead. See lastGuaranteedSourceDepth.
                        continue;
                    }
                    if (!canStillReach(node, region.lastDepth(), true)) {
                        violations.add("region " + region.regionIndex() + ": depth " + node.depth
                                + " lane " + node.laneIndex + " can no longer reach an UPGRADE node");
                    }
                    if (!canStillReach(node, region.lastDepth(), false)) {
                        violations.add("region " + region.regionIndex() + ": depth " + node.depth
                                + " lane " + node.laneIndex + " can no longer reach a CALM node");
                    }
                }
            }
        }

        for (List<RouteNode> layer : layers) {
            if (layer.size() != 1 || layer.get(0).type != RouteNodeType.BOSS) {
                continue;
            }
            RouteNode boss = layer.get(0);
            boolean provisioned = false;
            boolean hasPreBossLayer = false;
            for (List<RouteNode> candidateLayer : layers) {
                if (candidateLayer.isEmpty() || candidateLayer.get(0).depth != boss.depth - 1) {
                    continue;
                }
                hasPreBossLayer = true;
                for (RouteNode node : candidateLayer) {
                    boolean provisions = node.type == RouteNodeType.SHOP || node.type == RouteNodeType.CACHE;
                    if (provisions && node.outgoing.contains(boss)) {
                        provisioned = true;
                    }
                }
            }
            if (hasPreBossLayer && !provisioned) {
                violations.add("depth " + boss.depth + ": no reachable SHOP/CACHE in the pre-boss layer");
            }
        }
        // The band size is only read to document that regions align with the gear/boss bands.
        if (band <= 0) {
            violations.add("region band size must be positive");
        }
        return violations;
    }

    /**
     * Whether SOME forward path from {@code node}, staying inside the region (depth &lt;= lastDepth),
     * still reaches a node of the required kind — the "no lane strands a run" property the generator's
     * post-pass enforces. Depth-first over the small band DAG; the node itself counts.
     */
    private static boolean canStillReach(RouteNode node, int lastDepth, boolean upgradeKind) {
        if (upgradeKind ? isUpgradeBearing(node.type) : isCalm(node.type)) {
            return true;
        }
        if (node.depth >= lastDepth) {
            return false; // ran out of region without ever meeting the requirement
        }
        for (RouteNode next : node.outgoing) {
            if (next.depth <= lastDepth && canStillReach(next, lastDepth, upgradeKind)) {
                return true;
            }
        }
        return false;
    }
}
