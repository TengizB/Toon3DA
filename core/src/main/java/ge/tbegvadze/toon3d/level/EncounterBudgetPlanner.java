package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.route.NodeSupplySpec;
import ge.tbegvadze.toon3d.route.RouteRegistries;
import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

/**
 * THE ENCOUNTER PLANNER (balance-overhaul order 2, rules E1-E4) — what a floor fights.
 *
 * <p>A floor fills a BODY TARGET that grows with depth (E1: 12-16 bodies at depth 1 rising to 22-28 at
 * depth 25, times the node's body scale) out of registered GROUP TEMPLATES (E2, reshaped by
 * mixed-encounter-groups: RABBLE, SKIRMISH, FIRETEAM, ESCORT, BATTERY, WARBAND, HUNTER, BROOD, and on CALM
 * floors PAIR and LOOKOUT). The depth-scaled Threat-Point budget — times the region danger dial, the
 * node's threat scale and any extra multiplier the route hands over — is now a CAP on total threat, not
 * the thing being filled. Rules:
 * <ul>
 *   <li>COMBAT and ELITE floors open with ONE anchor group (ESCORT; an ELITE floor from depth 3 a
 *       WARBAND), exempt from the per-group cap and placed deepest.</li>
 *   <li>Every other group spends at most {@link BalanceConfig#GROUP_TP_FRACTION_CAP} of the cap (E3).</li>
 *   <li>Each pick aims at the threat-per-body the floor still has to spend, so the roster ends near the
 *       cap at the body target: an ELITE's larger cap buys heavier groups, not a crowd.</li>
 *   <li>HUNTERs (lone bruisers / flankers) are at most {@link BalanceConfig#GROUP_HUNTER_MAX_FRACTION} of
 *       the groups; chaff come in PAIRS (two of one archetype, the pairs of a group all different).</li>
 *   <li>MIXED GROUPS (V1-V6): no archetype appears more than
 *       {@link BalanceConfig#GROUP_MAX_SAME_TYPE_PER_GROUP} times in a group or fills two slots of it; a
 *       group of {@link BalanceConfig#GROUP_MIXED_BEHAVIOUR_MIN_SIZE}+ mixes ranged / rusher / plodder; the
 *       only exception is one BROOD of {@link BalanceConfig#GROUP_BROOD_SIZE} on
 *       {@link BalanceConfig#GROUP_BROOD_FLOOR_CHANCE} of floors; the top-up adds a DIFFERENT archetype.</li>
 *   <li>No archetype spends more than {@link BalanceConfig#GROUP_MAX_SINGLE_TYPE_FRACTION} of the cap, nor
 *       fields more than {@link BalanceConfig#GROUP_MAX_SINGLE_TYPE_BODY_FRACTION} of the bodies, while an
 *       alternative exists (variety).</li>
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

        // V4: the brood roll is consumed on EVERY floor that fields enemies, so the draws after it never depend
        // on its outcome (and a floor that fields none keeps its stream untouched).
        boolean broodFloor = random.nextFloat() < BalanceConfig.GROUP_BROOD_FLOOR_CHANCE;
        float groupCap   = cap * BalanceConfig.GROUP_TP_FRACTION_CAP;
        float fillTarget = cap * BalanceConfig.ENCOUNTER_BUDGET_FILL_TARGET_FRACTION;
        Ledger ledger = new Ledger(cap, bodyTarget);
        float spent  = 0f;
        int   bodies = 0;

        // --- The anchor group (COMBAT / ELITE): the heaviest eligible anchor shape that fits the cap.
        EncounterGroup anchor = planAnchor(cap, bodyTarget, ledger);
        if (anchor != null) {
            groups.add(anchor);
            spent  += anchor.threat;
            bodies += anchor.size();
            ledger.record(anchor.members);
        }

        // --- Fill the body target, each pick aiming at the threat-per-body still to spend.
        int hunters = 0;
        int broods  = 0;
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
                if (template.brood() && (!broodFloor || broods >= BalanceConfig.GROUP_BROOD_MAX_PER_FLOOR
                        || !ledger.admitsBrood())) continue;
                if (template.maximumPerFloor() > 0 && countOf(groups, template.id()) >= template.maximumPerFloor()) continue;
                // A shape's archetypes are drawn at random, so one expensive draw must not rule the shape
                // out: try a few draws and keep the first that fits the cap with room for the cheapest
                // possible fill of the bodies still to come.
                EncounterGroup candidate = null;
                for (int attempt = 0; attempt < BalanceConfig.GROUP_INSTANTIATION_ATTEMPTS && candidate == null; attempt++) {
                    EncounterGroup drawn = instantiate(template, remainingBodies, false,
                            Math.min(groupCap, cap - spent), ledger);
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
            ledger.record(best.members);
            if (EncounterGroupTemplates.HUNTER.equals(best.templateId)) hunters++;
            EncounterGroupTemplate picked = EncounterGroupTemplateRegistry.shared().get(best.templateId);
            if (picked != null && picked.brood()) broods++;
        }

        // --- Top-up (V5): bodies short of the target (fewer than any shape's minimum left) join a group as a
        // DIFFERENT archetype — a new chaff pair first (RABBLE's third pair, ESCORT's second), then a new
        // single (BATTERY's second gun, SKIRMISH's second soldier). Never a BROOD or a lone HUNTER, never a
        // lone chaff, never past a slot's maximum; a floor may end a body short of its roll.
        for (int wantedStep = BalanceConfig.GROUP_CHAFF_SLOT_MIN; wantedStep >= 1; wantedStep--) {
            boolean grew = true;
            while (grew && bodies < bodyTarget) {
                grew = false;
                for (int index = 0; index < groups.size() && bodies < bodyTarget; index++) {
                    EncounterGroup grown = topUp(groups.get(index), wantedStep, bodyTarget - bodies, cap - spent, ledger);
                    if (grown == null) continue;
                    EncounterGroup before = groups.get(index);
                    groups.set(index, grown);
                    spent  += grown.threat - before.threat;
                    bodies += grown.size() - before.size();
                    grew = true;
                }
            }
        }
        return new Plan(groups, cap, spent, bodyTarget, threatLookup);
    }

    /**
     * One V5 top-up step on a group: one more archetype (of {@code wantedStep} members) in a slot of that
     * step with headroom, an archetype the group does not field yet and the cap can afford. Returns the
     * grown group, or {@code null} when nothing fits. Records the added bodies in the ledger.
     */
    private EncounterGroup topUp(EncounterGroup group, int wantedStep, int remainingBodies, float remainingThreat,
                                 Ledger ledger) {
        EncounterGroupTemplate template = EncounterGroupTemplateRegistry.shared().get(group.templateId);
        if (template == null || template.lone() || template.brood() || wantedStep > remainingBodies) return null;
        List<EncounterGroupTemplate.Slot> slots = template.slots();
        int offset = 0;
        for (int index = 0; index < slots.size(); offset += group.slotCounts[index], index++) {
            EncounterGroupTemplate.Slot slot = slots.get(index);
            int step = slot.fill.membersPerArchetype();
            if (step != wantedStep || group.slotCounts[index] + step > slot.maximumCount) continue;
            // A group grown to GROUP_MIXED_BEHAVIOUR_MIN_SIZE+ must still mix behaviours (V3).
            EnumSet<EncounterBehaviour> behaviours = EnumSet.noneOf(EncounterBehaviour.class);
            for (EnemyType member : group.members) behaviours.add(EncounterBehaviour.of(member));
            boolean needsMix = behaviours.size() < 2
                    && group.size() + step >= BalanceConfig.GROUP_MIXED_BEHAVIOUR_MIN_SIZE;
            List<EnemyType> candidates = new ArrayList<>();
            for (EnemyType type : ledger.admitted(slot.role, step)) {
                if (group.members.contains(type) || threatOf(type) * step > remainingThreat) continue;
                if (needsMix && behaviours.contains(EncounterBehaviour.of(type))) continue;
                candidates.add(type);
            }
            if (candidates.isEmpty()) continue;
            EnemyType added = candidates.get(random.nextInt(candidates.size()));
            List<EnemyType> members = new ArrayList<>(group.members);
            int insertAt = offset + group.slotCounts[index];
            for (int member = 0; member < step; member++) members.add(insertAt, added);
            int[] slotCounts = group.slotCounts.clone();
            slotCounts[index] += step;
            ledger.record(java.util.Collections.nCopies(step, added));
            return new EncounterGroup(group.templateId, members, group.anchor, group.threat + threatOf(added) * step,
                    slotCounts);
        }
        return null;
    }

    /** The anchor group: the eligible anchor-capable shape with the highest threat that fits the cap. */
    private EncounterGroup planAnchor(float cap, int bodyTarget, Ledger ledger) {
        if (spec.encounterKind() != NodeSupplySpec.EncounterKind.COMBAT
                && spec.encounterKind() != NodeSupplySpec.EncounterKind.ELITE) {
            return null;
        }
        EncounterGroup best = null;
        for (EncounterGroupTemplate template : EncounterGroupTemplateRegistry.shared().all()) {
            if (!template.anchorCapable() || !template.isEligible(spec.encounterKind(), depth)) continue;
            if (template.minimumSize() > bodyTarget) continue;
            EncounterGroup candidate = instantiate(template, bodyTarget, true, cap, ledger);
            if (candidate == null) continue;
            if (best == null || candidate.threat > best.threat) best = candidate;
        }
        return best;
    }

    /**
     * Fills a template's slots with archetypes from the depth's pool, never above {@code maximumBodies}
     * members, then SHRINKS it (last slot first, never below a slot's minimum) until it costs at most
     * {@code maximumThreat}. Counts move in whole {@link SlotFill#membersPerArchetype()} units, so a pair
     * is never split. V1/V2: every archetype fills one slot of one group only, in its slot's unit; V3: a
     * non-BROOD group of {@link BalanceConfig#GROUP_MIXED_BEHAVIOUR_MIN_SIZE}+ spans two behaviours (its
     * last draw is restricted to a missing behaviour while the pool allows). Returns {@code null} when a
     * slot cannot reach its minimum or even the smallest instance does not fit.
     */
    private EncounterGroup instantiate(EncounterGroupTemplate template, int maximumBodies, boolean anchor,
                                       float maximumThreat, Ledger ledger) {
        List<EncounterGroupTemplate.Slot> slots = template.slots();
        int[] units = new int[slots.size()];
        int total = 0;
        for (int index = 0; index < slots.size(); index++) {
            EncounterGroupTemplate.Slot slot = slots.get(index);
            units[index] = slot.minimumArchetypes()
                    + random.nextInt(slot.maximumArchetypes() - slot.minimumArchetypes() + 1);
            total += units[index] * slot.fill.membersPerArchetype();
        }
        for (int index = slots.size() - 1; index >= 0 && total > maximumBodies; index--) {
            EncounterGroupTemplate.Slot slot = slots.get(index);
            while (total > maximumBodies && units[index] > slot.minimumArchetypes()) {
                units[index]--;
                total -= slot.fill.membersPerArchetype();
            }
        }
        if (total > maximumBodies) return null;
        // Leave a remainder some shape can still field: grow the group to fill exactly, else shrink it so
        // at least GROUP_MINIMUM_FILLABLE_REMAINDER bodies stay (a 7-body CALM floor is 4 + 3, never 5 + 2).
        int leftover = maximumBodies - total;
        if (leftover > 0 && leftover < BalanceConfig.GROUP_MINIMUM_FILLABLE_REMAINDER) {
            for (int index = slots.size() - 1; index >= 0; index--) {
                EncounterGroupTemplate.Slot slot = slots.get(index);
                int step = slot.fill.membersPerArchetype();
                while (units[index] < slot.maximumArchetypes() && total + step <= maximumBodies) {
                    units[index]++;
                    total += step;
                }
            }
            int gap = maximumBodies - total;
            for (int index = slots.size() - 1; index >= 0; index--) {
                EncounterGroupTemplate.Slot slot = slots.get(index);
                while (gap > 0 && gap < BalanceConfig.GROUP_MINIMUM_FILLABLE_REMAINDER
                        && units[index] > slot.minimumArchetypes()) {
                    units[index]--;
                    total -= slot.fill.membersPerArchetype();
                    gap = maximumBodies - total;
                }
            }
        }

        // Draw distinct archetypes, slot by slot (V2), restricting the group's last draw to a behaviour it
        // lacks when every member so far fights the same way (V3).
        boolean mixRule = !template.brood() && total >= BalanceConfig.GROUP_MIXED_BEHAVIOUR_MIN_SIZE;
        int plannedDraws = 0;
        for (int count : units) plannedDraws += count;
        EnumSet<EnemyType> used = EnumSet.noneOf(EnemyType.class);
        EnumSet<EncounterBehaviour> behaviours = EnumSet.noneOf(EncounterBehaviour.class);
        List<List<EnemyType>> chosenBySlot = new ArrayList<>();
        int drawn = 0;
        float threat = 0f;
        for (int index = 0; index < slots.size(); index++) {
            EncounterGroupTemplate.Slot slot = slots.get(index);
            int step = slot.fill.membersPerArchetype();
            List<EnemyType> candidates = new ArrayList<>(ledger.admitted(slot.role, step));
            candidates.removeAll(used);
            List<EnemyType> chosen = new ArrayList<>();
            for (int unit = 0; unit < units[index]; unit++) {
                List<EnemyType> pickFrom = candidates;
                if (mixRule && drawn == plannedDraws - 1 && behaviours.size() == 1) {
                    List<EnemyType> missing = new ArrayList<>();
                    for (EnemyType type : candidates) {
                        if (!behaviours.contains(EncounterBehaviour.of(type))) missing.add(type);
                    }
                    if (!missing.isEmpty()) {
                        pickFrom = missing;
                    } else if (poolHasBehaviourOutside(slot.role, behaviours, used)) {
                        // The variety caps, not the pool, block the mix: fail this draw (the planner tries
                        // another draw or shape) rather than break V3 or V6. Only a pool that truly lacks the
                        // behaviour lets the group stay single-behaviour.
                        return null;
                    }
                }
                if (pickFrom.isEmpty()) break;
                EnemyType type = pickFrom.get(random.nextInt(pickFrom.size()));
                candidates.remove(type);
                used.add(type);
                behaviours.add(EncounterBehaviour.of(type));
                chosen.add(type);
                threat += threatOf(type) * step;
                drawn++;
            }
            if (chosen.size() < slot.minimumArchetypes()) return null;
            plannedDraws -= units[index] - chosen.size();
            chosenBySlot.add(chosen);
        }

        // Shrink to fit the threat, one archetype unit at a time, last slot first — dropping the unit
        // whose loss keeps the group's behaviour mix (V3) wherever one exists.
        int size = 0;
        for (int index = 0; index < slots.size(); index++) {
            size += chosenBySlot.get(index).size() * slots.get(index).fill.membersPerArchetype();
        }
        for (int index = slots.size() - 1; index >= 0 && threat > maximumThreat; index--) {
            EncounterGroupTemplate.Slot slot = slots.get(index);
            int step = slot.fill.membersPerArchetype();
            List<EnemyType> chosen = chosenBySlot.get(index);
            while (chosen.size() > slot.minimumArchetypes() && threat > maximumThreat) {
                int dropAt = -1;
                for (int candidate = chosen.size() - 1; candidate >= 0 && dropAt < 0; candidate--) {
                    if (!mixRule || size - step < BalanceConfig.GROUP_MIXED_BEHAVIOUR_MIN_SIZE
                            || staysMixedWithout(chosenBySlot, index, candidate)) {
                        dropAt = candidate;
                    }
                }
                if (dropAt < 0) break;
                threat -= threatOf(chosen.remove(dropAt)) * step;
                size   -= step;
            }
        }
        if (threat > maximumThreat) return null;
        List<EnemyType> members = new ArrayList<>();
        int[] slotCounts = new int[slots.size()];
        for (int index = 0; index < slots.size(); index++) {
            int step = slots.get(index).fill.membersPerArchetype();
            for (EnemyType type : chosenBySlot.get(index)) {
                for (int member = 0; member < step; member++) members.add(type);
            }
            slotCounts[index] = chosenBySlot.get(index).size() * step;
        }
        return new EncounterGroup(template.id(), members, anchor, threat, slotCounts);
    }

    /** How many of the planned groups were drawn from the template {@code templateId}. */
    private static int countOf(List<EncounterGroup> groups, String templateId) {
        int count = 0;
        for (EncounterGroup group : groups) {
            if (group.templateId.equals(templateId)) count++;
        }
        return count;
    }

    /** Whether the depth's pool holds an archetype for {@code role}, outside {@code used}, of a behaviour not in {@code behaviours}. */
    private boolean poolHasBehaviourOutside(EncounterGroupRole role, EnumSet<EncounterBehaviour> behaviours,
                                            EnumSet<EnemyType> used) {
        for (EnemyType type : pool) {
            if (role.admits(type) && !used.contains(type) && !behaviours.contains(EncounterBehaviour.of(type))) return true;
        }
        return false;
    }

    /** Whether the drawn archetypes still span two behaviours without the one at {@code slotIndex/position}. */
    private static boolean staysMixedWithout(List<List<EnemyType>> chosenBySlot, int slotIndex, int position) {
        EnumSet<EncounterBehaviour> behaviours = EnumSet.noneOf(EncounterBehaviour.class);
        for (int index = 0; index < chosenBySlot.size(); index++) {
            List<EnemyType> chosen = chosenBySlot.get(index);
            for (int member = 0; member < chosen.size(); member++) {
                if (index == slotIndex && member == position) continue;
                behaviours.add(EncounterBehaviour.of(chosen.get(member)));
            }
        }
        return behaviours.size() >= 2;
    }

    /** The cheapest chaff archetype's threat on this floor (the reserve each remaining body needs). */
    private float cheapestChaffThreat() {
        float cheapest = Float.MAX_VALUE;
        for (EnemyType type : pool) {
            if (EncounterGroupRole.CHAFF.admits(type)) cheapest = Math.min(cheapest, threatOf(type));
        }
        return cheapest == Float.MAX_VALUE ? 0f : cheapest;
    }

    /**
     * What one plan has spent per archetype, in Threat Points and in bodies, plus the two variety caps
     * that read it: no archetype past {@link BalanceConfig#GROUP_MAX_SINGLE_TYPE_FRACTION} of the threat
     * cap, and (V6) none past {@link BalanceConfig#GROUP_MAX_SINGLE_TYPE_BODY_FRACTION} of the body target.
     */
    private final class Ledger {
        private final EnumMap<EnemyType, Float>   spentByType  = new EnumMap<>(EnemyType.class);
        private final EnumMap<EnemyType, Integer> bodiesByType = new EnumMap<>(EnemyType.class);
        private final float typeThreatCap;
        private final int   typeBodyCap;

        Ledger(float cap, int bodyTarget) {
            this.typeThreatCap = cap * BalanceConfig.GROUP_MAX_SINGLE_TYPE_FRACTION;
            this.typeBodyCap   = Math.max(BalanceConfig.GROUP_CHAFF_SLOT_MIN,
                    (int) Math.ceil(BalanceConfig.GROUP_MAX_SINGLE_TYPE_BODY_FRACTION * bodyTarget));
        }

        /** Whether a whole BROOD fits under the floor's body-share cap (V6 binds before V4 on small floors). */
        boolean admitsBrood() {
            return BalanceConfig.GROUP_BROOD_SIZE <= typeBodyCap;
        }

        void record(List<EnemyType> members) {
            for (EnemyType type : members) {
                spentByType.merge(type, threatOf(type), Float::sum);
                bodiesByType.merge(type, 1, Integer::sum);
            }
        }

        /**
         * The pool's archetypes a slot admits, in pool order, preferring those that stay under BOTH variety
         * caps after {@code members} more, then the body cap alone, then the threat cap alone, then all.
         */
        List<EnemyType> admitted(EncounterGroupRole role, int members) {
            List<EnemyType> all        = new ArrayList<>();
            List<EnemyType> both       = new ArrayList<>();
            List<EnemyType> bodyOnly   = new ArrayList<>();
            List<EnemyType> threatOnly = new ArrayList<>();
            for (EnemyType type : pool) {
                if (!role.admits(type)) continue;
                all.add(type);
                boolean underThreat = spentByType.getOrDefault(type, 0f) + threatOf(type) * members <= typeThreatCap;
                boolean underBodies = bodiesByType.getOrDefault(type, 0) + members <= typeBodyCap;
                if (underThreat && underBodies) both.add(type);
                if (underBodies) bodyOnly.add(type);
                if (underThreat) threatOnly.add(type);
            }
            if (!both.isEmpty())       return both;
            if (!bodyOnly.isEmpty())   return bodyOnly;
            if (!threatOnly.isEmpty()) return threatOnly;
            return all;
        }
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
