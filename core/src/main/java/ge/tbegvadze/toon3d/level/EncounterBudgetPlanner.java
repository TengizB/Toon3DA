package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.enemy.EnemyRole;
import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;

/**
 * Encounter-budget planner (balance idea 4, Pillar 1 — "difficulty as a dial").
 *
 * Instead of rolling enemies at random per room, a floor SPENDS a Threat-Point budget
 * (GameMath.floorThreatPointBudget) on a roster of archetypes that obeys idea 4's composition
 * rules:
 *
 *   1. Reserve 15–30% of the budget for ONE anchor (bruiser / mini-elite) that defines the
 *      floor's hardest room. Occasionally (depth-gated) a floor is a deliberate elite gauntlet
 *      whose mini-elite anchor exceeds that reserve.
 *   2. No single enemy TYPE may exceed ~40% of the budget — forces variety so a floor is not a
 *      solved-once mono-type room.
 *   3. The roster contains both RANGED and MELEE archetypes — the ranged/melee tension is the
 *      core of the tactical idea (close the gap on the ranged enemy while the melee closes on
 *      you).
 *
 * Pure logic: no LibGDX state, no grid access. The caller (LevelGenerator) takes the resulting
 * roster and distributes it across rooms, honouring {@link Plan#perRoomThreatPointCap}. All
 * randomness flows through the supplied {@link Random} so a given floor seed is reproducible.
 */
public final class EncounterBudgetPlanner {

    /** Anchor-role archetypes (bruiser / mini-elite), cheapest first. */
    private static final EnemyType[] ANCHOR_TYPES = {
            EnemyType.SHELL_BRUTE,   // bruiser  — the standard anchor
            EnemyType.IRON_STALKER   // mini-elite — rare elite-gauntlet anchor
    };

    /** Non-anchor archetypes (chaff + soldiers) the budget is filled with. */
    private static final EnemyType[] FILL_TYPES = {
            EnemyType.GORE_BITER,       // swarmer   (fast melee)
            EnemyType.EYE_TYRANT,       // sniper    (ranged)
            EnemyType.ACID_DRONE,       // harasser  (ranged)
            EnemyType.VOID_SHROUD,      // flanker   (fast melee)
            EnemyType.MIRE_WRAITH,      // artillery (ranged)
            EnemyType.PLAGUE_HULK,      // tank      (melee)
            // Necrotic faction — chaff + soldiers reusing the legacy individual sprites.
            EnemyType.GHOUL,            // shambler  (slow melee chaff)
            EnemyType.CRAWLER,          // scuttler  (fast melee chaff)
            EnemyType.VORTEX_EYE,       // eye       (short-range ranged chaff)
            EnemyType.REVENANT,         // reanimator(fast melee soldier)
            EnemyType.BLIGHT_CORRUPTOR  // carrier   (durable melee soldier)
    };

    /** Divergence guard: every added enemy raises spent TP by a positive cost, but cap roster size anyway. */
    private static final int ROSTER_SAFETY_CAP = 64;

    private final int    depth;
    private final Random random;
    private final float  budgetScale;

    public EncounterBudgetPlanner(int depth, Random random) {
        this(depth, random, 1f);
    }

    /**
     * @param budgetScale route-map order-7 multiplier on the depth-scaled budget (1.0 = normal).
     *                    CALM nodes lower it, DANGER nodes raise it. It scales the budget AFTER the
     *                    depth ramp is applied, so difficulty is never frozen at an earlier depth.
     *                    Non-positive values fall back to 1.0.
     */
    public EncounterBudgetPlanner(int depth, Random random, float budgetScale) {
        this.depth       = Math.max(1, depth);
        this.random      = random;
        this.budgetScale = budgetScale > 0f ? budgetScale : 1f;
    }

    /** Plans the floor's enemy roster by spending the depth-scaled Threat-Point budget. */
    public Plan plan() {
        // Depth ramp FIRST (roguelike_order_16 invariant), THEN the REGION DANGER DIAL (order 5) —
        // the region's Threat-Point multiplier applied ON TOP of the depth curve so a lethal region
        // spends more TP at the same depth — THEN the route-map node's per-node scale (order 7).
        float budget = GameMath.regionScaledFloorThreatPointBudget(
                BalanceConfig.FLOOR_BASE_THREAT_POINT_BUDGET,
                BalanceConfig.ENEMY_HEALTH_SCALE_PER_DEPTH,
                BalanceConfig.ENEMY_DAMAGE_SCALE_PER_DEPTH,
                depth, BalanceConfig.REGION_TP_BUDGET_MULTIPLIER,
                BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE) * budgetScale;

        List<EnemyType>           roster      = new ArrayList<>();
        EnumMap<EnemyType, Float> spentByType = new EnumMap<>(EnemyType.class);
        float spent = 0f;

        // --- Composition rule 1: reserve budget for exactly one anchor. An elite-gauntlet anchor must
        // still fit the (region-scaled) budget — an elite floor is a composition choice, not a budget
        // override (order 5, C.4) — which chooseAnchor already enforces via threatOf(elite) <= budget.
        EnemyType anchor = chooseAnchor(budget);
        if (anchor != null) {
            float anchorCost = threatOf(anchor);
            roster.add(anchor);
            spentByType.merge(anchor, anchorCost, Float::sum);
            spent += anchorCost;
        }

        // --- Fill the rest of the budget with a mix (rules 2 and 3). CHAFF is added in PACKS
        // (order 5, PACK COHERENCE): the golden-band chaff exemption ASSUMES packs (a lone chaff reads a
        // harmless ~9 golden ratio, balanced by pack TP), so the spawner now guarantees the assumption
        // instead of hoping for it — every chaff type enters in a group of CHAFF_PACK_MIN..MAX.
        float fillTarget = budget * BalanceConfig.ENCOUNTER_BUDGET_FILL_TARGET_FRACTION;
        float maxPerType = budget * BalanceConfig.ENCOUNTER_MAX_SINGLE_TYPE_FRACTION;
        while (spent < fillTarget && roster.size() < ROSTER_SAFETY_CAP) {
            EnemyType pick = chooseFill(spentByType, spent, budget, maxPerType,
                    rosterContainsRanged(roster), rosterContainsMelee(roster));
            if (pick == null) break;
            float cost = threatOf(pick);
            int addCount = pick.role() == EnemyRole.CHAFF
                    ? chaffPackSize(pick, cost, spentByType.getOrDefault(pick, 0f), spent, budget, maxPerType)
                    : 1;
            for (int copy = 0; copy < addCount && roster.size() < ROSTER_SAFETY_CAP; copy++) {
                roster.add(pick);
                spentByType.merge(pick, cost, Float::sum);
                spent += cost;
            }
        }

        float perRoomCap = budget * BalanceConfig.ENCOUNTER_PER_ROOM_TP_FRACTION_CAP;
        EnumMap<EnemyType, Float> threatLookup = new EnumMap<>(EnemyType.class);
        for (EnemyType type : EnemyType.values()) {
            threatLookup.put(type, threatOf(type));
        }
        return new Plan(roster, anchor, budget, spent, perRoomCap, threatLookup);
    }

    // -------------------------------------------------------------------------
    // Anchor selection
    // -------------------------------------------------------------------------

    private EnemyType chooseAnchor(float budget) {
        if (budget <= 0f) return null;

        // Deliberate elite-gauntlet floor: a mini-elite anchor that exceeds the normal reserve.
        boolean eliteFloor = depth >= BalanceConfig.ENCOUNTER_ELITE_ANCHOR_MIN_DEPTH
                && random.nextFloat() < BalanceConfig.ENCOUNTER_ELITE_ANCHOR_FLOOR_CHANCE;
        if (eliteFloor) {
            EnemyType elite = ANCHOR_TYPES[ANCHOR_TYPES.length - 1]; // most expensive anchor
            if (threatOf(elite) <= budget) return elite;
        }

        // Pick a random reserve fraction inside the [MIN, MAX] band, then take the largest
        // anchor whose cost fits that reserve.
        float reserveFraction = BalanceConfig.ENCOUNTER_ANCHOR_BUDGET_FRACTION_MIN
                + (BalanceConfig.ENCOUNTER_ANCHOR_BUDGET_FRACTION_MAX
                   - BalanceConfig.ENCOUNTER_ANCHOR_BUDGET_FRACTION_MIN) * random.nextFloat();
        float anchorBudget = budget * reserveFraction;

        EnemyType best     = null;
        float     bestCost = -1f;
        for (EnemyType type : ANCHOR_TYPES) {
            float cost = threatOf(type);
            if (cost <= anchorBudget && cost > bestCost) {
                best     = type;
                bestCost = cost;
            }
        }
        if (best != null) return best;

        // Nothing fits the reserve — place the cheapest anchor that fits the whole budget so the
        // floor still has a defining threat; null only if even that is unaffordable.
        EnemyType cheapest    = ANCHOR_TYPES[0];
        return threatOf(cheapest) <= budget ? cheapest : null;
    }

    // -------------------------------------------------------------------------
    // Fill selection
    // -------------------------------------------------------------------------

    private EnemyType chooseFill(EnumMap<EnemyType, Float> spentByType, float spent, float budget,
                                 float maxPerType, boolean hasRanged, boolean hasMelee) {
        List<EnemyType> affordable = new ArrayList<>(FILL_TYPES.length);
        for (EnemyType type : FILL_TYPES) {
            float cost      = threatOf(type);
            float typeSpent = spentByType.getOrDefault(type, 0f);
            // CHAFF is only offered when a full minimum PACK fits (order 5 pack coherence): the golden-band
            // chaff exemption assumes packs, so a lone chaff is never added — a chaff type that can only fit
            // one more copy is skipped in favour of a non-chaff filler.
            int required = type.role() == EnemyRole.CHAFF ? BalanceConfig.CHAFF_PACK_MIN : 1;
            if (typeSpent + required * cost > maxPerType) continue;   // rule 2: per-type cap
            if (spent + required * cost > budget)         continue;   // never overshoot the floor budget
            affordable.add(type);
        }
        if (affordable.isEmpty()) return null;

        // Rule 3: bias the pick toward whichever attack class the roster still lacks.
        List<EnemyType> preferred = affordable;
        if (!hasRanged) {
            preferred = filterByRanged(affordable, true);
        } else if (!hasMelee) {
            preferred = filterByRanged(affordable, false);
        }
        if (preferred.isEmpty()) preferred = affordable;

        return preferred.get(random.nextInt(preferred.size()));
    }

    /**
     * How many copies of a chosen chaff type to add as one pack (order 5 pack coherence): a random target
     * in [CHAFF_PACK_MIN, CHAFF_PACK_MAX], grown one copy at a time only while the NEXT copy still fits
     * under BOTH the floor budget and the per-type cap. The pack STARTS at {@code CHAFF_PACK_MIN}, which
     * {@link #chooseFill} has already guaranteed fits (it offers a chaff type only when
     * {@code spent + CHAFF_PACK_MIN * cost <= budget} and the analogous per-type check hold), so a chaff
     * pack is never smaller than the minimum. Growth uses the SAME additive arithmetic as those checks, so
     * no copy is ever added that would overshoot either cap (no float-floor mismatch).
     */
    private int chaffPackSize(EnemyType chaff, float cost, float typeSpent, float spent,
                              float budget, float maxPerType) {
        int packSpan = BalanceConfig.CHAFF_PACK_MAX - BalanceConfig.CHAFF_PACK_MIN + 1;
        int desired  = BalanceConfig.CHAFF_PACK_MIN + random.nextInt(Math.max(1, packSpan));
        int packSize = BalanceConfig.CHAFF_PACK_MIN;    // guaranteed to fit by chooseFill's precondition
        // Grow one copy at a time, testing the NEXT size with the SAME multiplicative form and <= boundary
        // chooseFill uses for its cap checks (count * cost, not an accumulated sum), so the two never
        // disagree at an exact budget boundary and no float drift can slip an over-cap copy in.
        while (packSize < desired
                && spent + (packSize + 1) * cost <= budget
                && typeSpent + (packSize + 1) * cost <= maxPerType) {
            packSize++;
        }
        return packSize;
    }

    private static List<EnemyType> filterByRanged(List<EnemyType> source, boolean ranged) {
        List<EnemyType> result = new ArrayList<>(source.size());
        for (EnemyType type : source) {
            if (type.isRanged() == ranged) result.add(type);
        }
        return result;
    }

    private static boolean rosterContainsRanged(List<EnemyType> roster) {
        for (EnemyType type : roster) {
            if (type.isRanged()) return true;
        }
        return false;
    }

    private static boolean rosterContainsMelee(List<EnemyType> roster) {
        for (EnemyType type : roster) {
            if (!type.isRanged()) return true;
        }
        return false;
    }

    /** This archetype's depth-scaled Threat-Point cost on the planned floor. */
    private float threatOf(EnemyType type) {
        return GameMath.enemyThreatAtDepth(type.baseThreatPoints(),
                BalanceConfig.ENEMY_HEALTH_SCALE_PER_DEPTH,
                BalanceConfig.ENEMY_DAMAGE_SCALE_PER_DEPTH, depth);
    }

    // -------------------------------------------------------------------------
    // Result
    // -------------------------------------------------------------------------

    /**
     * The planned roster plus the numbers the generator needs to distribute it across rooms.
     * Immutable snapshot — the enemy list is unmodifiable.
     */
    public static final class Plan {
        private final List<EnemyType>           enemies;
        private final EnemyType                 anchor;
        private final float                     floorBudget;
        private final float                     spentThreatPoints;
        private final float                     perRoomThreatPointCap;
        private final EnumMap<EnemyType, Float> threatByType;

        Plan(List<EnemyType> enemies, EnemyType anchor, float floorBudget, float spentThreatPoints,
             float perRoomThreatPointCap, EnumMap<EnemyType, Float> threatByType) {
            this.enemies               = java.util.Collections.unmodifiableList(enemies);
            this.anchor                = anchor;
            this.floorBudget           = floorBudget;
            this.spentThreatPoints     = spentThreatPoints;
            this.perRoomThreatPointCap = perRoomThreatPointCap;
            this.threatByType          = threatByType;
        }

        /** The full roster (anchor first), in spend order. */
        public List<EnemyType> enemies() { return enemies; }

        /** The single anchor archetype, or null if the budget could not afford one. */
        public EnemyType anchor() { return anchor; }

        /** Total Threat-Point budget for the floor (depth-scaled). */
        public float floorBudget() { return floorBudget; }

        /** Threat points actually spent by the roster (<= floorBudget). */
        public float spentThreatPoints() { return spentThreatPoints; }

        /** Maximum Threat points a single non-anchor room may hold (neutral geometry). */
        public float perRoomThreatPointCap() { return perRoomThreatPointCap; }

        /**
         * The per-room TP cap scaled by a room's GEOMETRY (order 5 tactical room caps): an OPEN room caps
         * LOWER (nowhere to break ranged cardinal lines), a corridor-adjacent CHOKEPOINT caps a little
         * HIGHER (the player can funnel the pack). The caller passes the flags it reads from the generator's
         * room metadata (footprint / corridor adjacency); a neutral room (both false) returns the base cap.
         */
        public float perRoomThreatPointCap(boolean isOpenRoom, boolean isChokepointRoom) {
            return perRoomThreatPointCap * GameMath.roomGeometryThreatCapMultiplier(
                    isOpenRoom, isChokepointRoom,
                    BalanceConfig.ROOM_OPEN_TP_MULTIPLIER, BalanceConfig.ROOM_CHOKEPOINT_TP_MULTIPLIER);
        }

        /** Depth-scaled Threat-Point cost of one enemy of the given type. */
        public float threatOf(EnemyType type) { return threatByType.getOrDefault(type, 0f); }
    }
}
