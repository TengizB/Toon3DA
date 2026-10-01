package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.route.NodeSupplySpec;
import ge.tbegvadze.toon3d.route.RouteRegistries;
import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;

/**
 * THE ENCOUNTER PLANNER (balance-overhaul order 2, rules E1-E4) — what a floor fights.
 *
 * <p>A floor fills a BODY TARGET that grows with depth (E1: 12-16 bodies at depth 1 rising to 22-28 at
 * depth 25, times the node's body scale) out of registered GROUP TEMPLATES (E2: PACK, FIRETEAM, ESCORT,
 * BATTERY, WARBAND, HUNTER). The depth-scaled Threat-Point budget — times the region danger dial, the
 * node's threat scale and any extra multiplier the route hands over — is now a CAP on total threat, not
 * the thing being filled. Rules:
 * <ul>
 *   <li>COMBAT and ELITE floors open with ONE anchor group (ESCORT; an ELITE floor from depth 3 a
 *       WARBAND), exempt from the per-group cap and placed deepest.</li>
 *   <li>Every other group spends at most {@link BalanceConfig#GROUP_TP_FRACTION_CAP} of the cap (E3).</li>
 *   <li>Each pick aims at the threat-per-body the floor still has to spend, so the roster ends near the
 *       cap at the body target: an ELITE's larger cap buys heavier groups, not a crowd.</li>
 *   <li>HUNTERs (lone bruisers / flankers) are at most {@link BalanceConfig#GROUP_HUNTER_MAX_FRACTION} of
 *       the groups; every other shape fields at least two members, chaff at least two of one archetype.</li>
 *   <li>No archetype spends more than {@link BalanceConfig#GROUP_MAX_SINGLE_TYPE_FRACTION} of the cap
 *       while an alternative exists (variety).</li>
 * </ul>
 * WHERE the groups stand is the generator's job, through {@link EncounterPlacer}.
 *
 * <p>Pure logic: no LibGDX state, no grid access; every draw comes from the supplied {@link Random}.
 */
public final class EncounterBudgetPlanner {

    /**
     * The archetypes a planned floor may field (the encounter pool). Bosses and the Verdant Spiresower
     * (placed by its own systems) are not in it. Order is the deterministic tie-break order.
     */
    private static final EnemyType[] SPAWN_POOL = {
            EnemyType.GORE_BITER, EnemyType.EYE_TYRANT, EnemyType.GHOUL, EnemyType.CRAWLER, EnemyType.VORTEX_EYE,
            EnemyType.ACID_DRONE, EnemyType.VOID_SHROUD, EnemyType.MIRE_WRAITH, EnemyType.PLAGUE_HULK,
            EnemyType.BLIGHT_CORRUPTOR, EnemyType.AURIC_SENTINEL, EnemyType.RIMESHELL_LANCER,
            EnemyType.SHELL_BRUTE, EnemyType.CINDERFORGE_COLOSSUS, EnemyType.REVENANT,
            EnemyType.IRON_STALKER
    };

    /** Divergence guard: a floor never plans more groups than this. */
    private static final int GROUP_SAFETY_CAP = 40;

    private final int            depth;
    private final Random         random;
    private final float          budgetScale;
    private final NodeSupplySpec spec;
    /** {@link #SPAWN_POOL} minus every archetype banded below this depth ({@link EnemyType#minSpawnDepth()}). */
    private final List<EnemyType> pool;

    public EncounterBudgetPlanner(int depth, Random random) {
        this(depth, random, 1f);
    }

    /** A COMBAT floor at {@code budgetScale} (the route's extra multiplier; 0 = an empty floor). */
    public EncounterBudgetPlanner(int depth, Random random, float budgetScale) {
        this(depth, random, budgetScale, null);
    }

    /**
     * @param depth       floor depth (1-based)
     * @param random      the generator's seeded stream
     * @param budgetScale the route's EXTRA multiplier on threat and bodies (affix, event bonus, mystery
     *                    outcome); 1.0 = none, exactly 0 = an empty floor, negative = 1.0
     * @param spec        the node's supply spec ({@code null} = the COMBAT row)
     */
    public EncounterBudgetPlanner(int depth, Random random, float budgetScale, NodeSupplySpec spec) {
        this.depth       = Math.max(1, depth);
        this.random      = random;
        this.budgetScale = budgetScale >= 0f ? budgetScale : 1f;
        this.spec        = spec != null ? spec : RouteRegistries.nodeSupplySpecs().getOrCombat(null);
        this.pool        = new ArrayList<>();
        for (EnemyType type : SPAWN_POOL) {
            if (type.minSpawnDepth() <= this.depth) pool.add(type);
        }
    }

    /** Plans the floor's groups. Deterministic for a given {@link Random} state. */
    public Plan plan() {
        float cap = GameMath.regionScaledFloorThreatPointBudget(
                BalanceConfig.FLOOR_BASE_THREAT_POINT_BUDGET,
                BalanceConfig.ENEMY_HEALTH_GROWTH,
                BalanceConfig.ENEMY_DAMAGE_GROWTH,
                depth, BalanceConfig.REGION_TP_BUDGET_MULTIPLIER,
                BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE) * spec.threatScale() * budgetScale;
        float rolledTarget = GameMath.bodyTargetAtDepth(depth, random.nextFloat(),
                BalanceConfig.BODY_TARGET_MIN_DEPTH_ONE, BalanceConfig.BODY_TARGET_MAX_DEPTH_ONE,
                BalanceConfig.BODY_TARGET_MIN_DEEP, BalanceConfig.BODY_TARGET_MAX_DEEP,
                BalanceConfig.BODY_TARGET_REFERENCE_DEEP_DEPTH);
        int bodyTarget = Math.round(rolledTarget * spec.bodyScale() * budgetScale);

        EnumMap<EnemyType, Float> threatLookup = new EnumMap<>(EnemyType.class);
        for (EnemyType type : EnemyType.values()) threatLookup.put(type, threatOf(type));

        List<EncounterGroup> groups = new ArrayList<>();
        if (spec.encounterKind() == NodeSupplySpec.EncounterKind.NONE || cap <= 0f || bodyTarget <= 0
                || pool.isEmpty()) {
            return new Plan(groups, cap, 0f, bodyTarget, threatLookup);
        }

        float groupCap   = cap * BalanceConfig.GROUP_TP_FRACTION_CAP;
        float fillTarget = cap * BalanceConfig.ENCOUNTER_BUDGET_FILL_TARGET_FRACTION;
        EnumMap<EnemyType, Float> spentByType = new EnumMap<>(EnemyType.class);
        float spent  = 0f;
        int   bodies = 0;

        // --- The anchor group (COMBAT / ELITE): the heaviest eligible anchor shape that fits the cap.
        EncounterGroup anchor = planAnchor(cap, bodyTarget, spentByType);
        if (anchor != null) {
            groups.add(anchor);
            spent  += anchor.threat;
            bodies += anchor.size();
            record(spentByType, anchor);
        }

        // --- Fill the body target, each pick aiming at the threat-per-body still to spend.
        int hunters = 0;
        List<EncounterGroupTemplate> templates = EncounterGroupTemplateRegistry.shared().all();
        float cheapestBody = cheapestChaffThreat();
        while (bodies < bodyTarget && groups.size() < GROUP_SAFETY_CAP) {
            int   remainingBodies = bodyTarget - bodies;
            float desiredPerBody  = Math.max(0f, fillTarget - spent) / remainingBodies;
            EncounterGroup best      = null;
            float          bestScore = Float.MAX_VALUE;
            for (EncounterGroupTemplate template : templates) {
                if (!template.isEligible(spec.encounterKind(), depth)) continue;
                if (template.minimumSize() > remainingBodies) continue;
                if (template.lone() && (hunters + 1) > Math.floor(
                        BalanceConfig.GROUP_HUNTER_MAX_FRACTION * (groups.size() + 1))) continue;
                // A shape's archetypes are drawn at random, so one expensive draw must not rule the shape
                // out: try a few draws and keep the first that fits the cap with room for the cheapest
                // possible fill of the bodies still to come.
                EncounterGroup candidate = null;
                for (int attempt = 0; attempt < BalanceConfig.GROUP_INSTANTIATION_ATTEMPTS && candidate == null; attempt++) {
                    EncounterGroup drawn = instantiate(template, remainingBodies, false, cap,
                            Math.min(groupCap, cap - spent), spentByType);
                    if (drawn == null) continue;
                    int bodiesAfter = remainingBodies - drawn.size();
                    if (spent + drawn.threat + bodiesAfter * cheapestBody > cap) continue;
                    candidate = drawn;
                }
                if (candidate == null) continue;
                float perBody  = candidate.threat / candidate.size();
                float mismatch = Math.abs(perBody - desiredPerBody) / Math.max(1f, desiredPerBody);
                // Never strand one or two bodies no shape can field (every non-lone shape needs >= 3).
                int left = remainingBodies - candidate.size();
                float gapPenalty = left > 0 && left < BalanceConfig.GROUP_MINIMUM_FILLABLE_REMAINDER ? 1f : 0f;
                float score    = mismatch + gapPenalty
                        + random.nextFloat() * BalanceConfig.GROUP_SELECTION_NOISE / template.weight();
                if (score < bestScore) {
                    bestScore = score;
                    best      = candidate;
                }
            }
            if (best == null) break;
            groups.add(best);
            spent  += best.threat;
            bodies += best.size();
            record(spentByType, best);
            if (EncounterGroupTemplates.HUNTER.equals(best.templateId)) hunters++;
        }

        // --- Top-up: bodies short of the target (fewer than any shape's minimum left) join a group's
        // same-type chaff slot, up to that slot's maximum — a PACK grows to five, an ESCORT's screen to three.
        for (int index = 0; index < groups.size() && bodies < bodyTarget; index++) {
            EncounterGroup group = groups.get(index);
            EncounterGroupTemplate template = EncounterGroupTemplateRegistry.shared().get(group.templateId);
            if (template == null) continue;
            int headroom = template.maximumSize() - group.size();
            EnemyType chaff = null;
            for (EnemyType member : group.members) {
                if (EncounterGroupRole.CHAFF.admits(member)) chaff = member;
            }
            if (chaff == null || headroom <= 0) continue;
            float cost = threatOf(chaff);
            List<EnemyType> members = new ArrayList<>(group.members);
            while (headroom > 0 && bodies < bodyTarget && spent + cost <= cap) {
                members.add(chaff);
                spent  += cost;
                bodies += 1;
                headroom--;
                spentByType.merge(chaff, cost, Float::sum);
            }
            groups.set(index, new EncounterGroup(group.templateId, members, group.anchor,
                    group.threat + cost * (members.size() - group.size())));
        }
        return new Plan(groups, cap, spent, bodyTarget, threatLookup);
    }

    /** The anchor group: the eligible anchor-capable shape with the highest threat that fits the cap. */
    private EncounterGroup planAnchor(float cap, int bodyTarget, EnumMap<EnemyType, Float> spentByType) {
        if (spec.encounterKind() != NodeSupplySpec.EncounterKind.COMBAT
                && spec.encounterKind() != NodeSupplySpec.EncounterKind.ELITE) {
            return null;
        }
        EncounterGroup best = null;
        for (EncounterGroupTemplate template : EncounterGroupTemplateRegistry.shared().all()) {
            if (!template.anchorCapable() || !template.isEligible(spec.encounterKind(), depth)) continue;
            if (template.minimumSize() > bodyTarget) continue;
            EncounterGroup candidate = instantiate(template, bodyTarget, true, cap, cap, spentByType);
            if (candidate == null) continue;
            if (best == null || candidate.threat > best.threat) best = candidate;
        }
        return best;
    }

    /**
     * Fills a template's slots with archetypes from the depth's pool, never above {@code maximumBodies}
     * members, then SHRINKS it (last slot first, never below a slot's minimum) until it costs at most
     * {@code maximumThreat}. Returns {@code null} when a slot has no admissible archetype or even the
     * smallest instance does not fit.
     */
    private EncounterGroup instantiate(EncounterGroupTemplate template, int maximumBodies, boolean anchor,
                                       float cap, float maximumThreat, EnumMap<EnemyType, Float> spentByType) {
        List<EncounterGroupTemplate.Slot> slots = template.slots();
        int[] counts = new int[slots.size()];
        int total = 0;
        for (int index = 0; index < slots.size(); index++) {
            EncounterGroupTemplate.Slot slot = slots.get(index);
            counts[index] = slot.minimumCount + random.nextInt(slot.maximumCount - slot.minimumCount + 1);
            total += counts[index];
        }
        for (int index = slots.size() - 1; index >= 0 && total > maximumBodies; index--) {
            int reducible = counts[index] - slots.get(index).minimumCount;
            int cut = Math.min(reducible, total - maximumBodies);
            counts[index] -= cut;
            total -= cut;
        }
        if (total > maximumBodies) return null;
        // Leave a remainder some shape can still field: grow the group to fill exactly, else shrink it so
        // at least GROUP_MINIMUM_FILLABLE_REMAINDER bodies stay (a 7-body CALM floor is 4 + 3, never 5 + 2).
        int leftover = maximumBodies - total;
        if (leftover > 0 && leftover < BalanceConfig.GROUP_MINIMUM_FILLABLE_REMAINDER) {
            for (int index = slots.size() - 1; index >= 0 && maximumBodies - total > 0; index--) {
                int growable = slots.get(index).maximumCount - counts[index];
                int grow = Math.min(growable, maximumBodies - total);
                counts[index] += grow;
                total += grow;
            }
            int gap = maximumBodies - total;
            for (int index = slots.size() - 1; index >= 0
                    && gap > 0 && gap < BalanceConfig.GROUP_MINIMUM_FILLABLE_REMAINDER; index--) {
                int reducible = counts[index] - slots.get(index).minimumCount;
                int cut = Math.min(reducible, BalanceConfig.GROUP_MINIMUM_FILLABLE_REMAINDER - gap);
                counts[index] -= cut;
                total -= cut;
                gap = maximumBodies - total;
            }
        }

        List<List<EnemyType>> bySlot = new ArrayList<>();
        float threat = 0f;
        for (int index = 0; index < slots.size(); index++) {
            EncounterGroupTemplate.Slot slot = slots.get(index);
            List<EnemyType> admitted = admitted(slot.role, cap, spentByType);
            if (admitted.isEmpty()) return null;
            EnemyType shared = admitted.get(random.nextInt(admitted.size()));
            List<EnemyType> slotMembers = new ArrayList<>();
            for (int member = 0; member < counts[index]; member++) {
                EnemyType type = slot.sameType ? shared : admitted.get(random.nextInt(admitted.size()));
                slotMembers.add(type);
                threat += threatOf(type);
            }
            bySlot.add(slotMembers);
        }
        for (int index = slots.size() - 1; index >= 0 && threat > maximumThreat; index--) {
            List<EnemyType> slotMembers = bySlot.get(index);
            while (slotMembers.size() > slots.get(index).minimumCount && threat > maximumThreat) {
                threat -= threatOf(slotMembers.remove(slotMembers.size() - 1));
            }
        }
        if (threat > maximumThreat) return null;
        List<EnemyType> members = new ArrayList<>();
        for (List<EnemyType> slotMembers : bySlot) members.addAll(slotMembers);
        return new EncounterGroup(template.id(), members, anchor, threat);
    }

    /** The pool's archetypes a slot admits, preferring those still under the single-type variety cap. */
    private List<EnemyType> admitted(EncounterGroupRole role, float cap, EnumMap<EnemyType, Float> spentByType) {
        List<EnemyType> all       = new ArrayList<>();
        List<EnemyType> preferred = new ArrayList<>();
        float typeCap = cap * BalanceConfig.GROUP_MAX_SINGLE_TYPE_FRACTION;
        for (EnemyType type : pool) {
            if (!role.admits(type)) continue;
            all.add(type);
            if (spentByType.getOrDefault(type, 0f) + threatOf(type) <= typeCap) preferred.add(type);
        }
        return preferred.isEmpty() ? all : preferred;
    }

    /** The cheapest chaff archetype's threat on this floor (the reserve each remaining body needs). */
    private float cheapestChaffThreat() {
        float cheapest = Float.MAX_VALUE;
        for (EnemyType type : pool) {
            if (EncounterGroupRole.CHAFF.admits(type)) cheapest = Math.min(cheapest, threatOf(type));
        }
        return cheapest == Float.MAX_VALUE ? 0f : cheapest;
    }

    private void record(EnumMap<EnemyType, Float> spentByType, EncounterGroup group) {
        for (EnemyType type : group.members) spentByType.merge(type, threatOf(type), Float::sum);
    }

    /** This archetype's depth-scaled Threat-Point cost on the planned floor. */
    private float threatOf(EnemyType type) {
        return GameMath.enemyThreatAtDepth(type.baseThreatPoints(),
                BalanceConfig.ENEMY_HEALTH_GROWTH,
                BalanceConfig.ENEMY_DAMAGE_GROWTH, depth);
    }

    // -------------------------------------------------------------------------
    // Result
    // -------------------------------------------------------------------------

    /** The planned groups plus the numbers the generator and the audit read. Immutable. */
    public static final class Plan {
        private final List<EncounterGroup>      groups;
        private final List<EnemyType>           enemies;
        private final float                     floorBudget;
        private final float                     spentThreatPoints;
        private final int                       bodyTarget;
        private final EnumMap<EnemyType, Float> threatByType;

        Plan(List<EncounterGroup> groups, float floorBudget, float spentThreatPoints, int bodyTarget,
             EnumMap<EnemyType, Float> threatByType) {
            this.groups            = java.util.Collections.unmodifiableList(new ArrayList<>(groups));
            List<EnemyType> flat   = new ArrayList<>();
            for (EncounterGroup group : groups) flat.addAll(group.members);
            this.enemies           = java.util.Collections.unmodifiableList(flat);
            this.floorBudget       = floorBudget;
            this.spentThreatPoints = spentThreatPoints;
            this.bodyTarget        = bodyTarget;
            this.threatByType      = threatByType;
        }

        /** The planned groups, anchor group first. */
        public List<EncounterGroup> groups() { return groups; }

        /** Every planned enemy (all groups flattened, anchor group first). */
        public List<EnemyType> enemies() { return enemies; }

        /** The anchor group's heaviest member, or null when the floor has no anchor group. */
        public EnemyType anchor() {
            for (EncounterGroup group : groups) {
                if (!group.anchor) continue;
                EnemyType heaviest = null;
                for (EnemyType type : group.members) {
                    if (heaviest == null || threatOf(type) > threatOf(heaviest)) heaviest = type;
                }
                return heaviest;
            }
            return null;
        }

        /** The floor's Threat-Point CAP (depth-scaled, region-dialled, node-scaled). */
        public float floorBudget() { return floorBudget; }

        /** Threat Points the groups spend (<= floorBudget). */
        public float spentThreatPoints() { return spentThreatPoints; }

        /** The E1 body target the plan filled toward. */
        public int bodyTarget() { return bodyTarget; }

        /** Depth-scaled Threat-Point cost of one enemy of the given type. */
        public float threatOf(EnemyType type) { return threatByType.getOrDefault(type, 0f); }
    }
}
