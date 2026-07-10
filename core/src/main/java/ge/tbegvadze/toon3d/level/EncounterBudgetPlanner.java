package ge.tbegvadze.toon3d.level;

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
        // Depth ramp FIRST (roguelike_order_16 invariant), THEN the route-map node's scale.
        float budget = GameMath.floorThreatPointBudget(
                BalanceConfig.FLOOR_BASE_THREAT_POINT_BUDGET,
                BalanceConfig.ENEMY_HEALTH_SCALE_PER_DEPTH,
                BalanceConfig.ENEMY_DAMAGE_SCALE_PER_DEPTH,
                depth) * budgetScale;

        List<EnemyType>           roster      = new ArrayList<>();
        EnumMap<EnemyType, Float> spentByType = new EnumMap<>(EnemyType.class);
        float spent = 0f;

        // --- Composition rule 1: reserve budget for exactly one anchor.
        EnemyType anchor = chooseAnchor(budget);
        if (anchor != null) {
            float anchorCost = threatOf(anchor);
            roster.add(anchor);
            spentByType.merge(anchor, anchorCost, Float::sum);
            spent += anchorCost;
        }

        // --- Fill the rest of the budget with a mix (rules 2 and 3).
        float fillTarget = budget * BalanceConfig.ENCOUNTER_BUDGET_FILL_TARGET_FRACTION;
        float maxPerType = budget * BalanceConfig.ENCOUNTER_MAX_SINGLE_TYPE_FRACTION;
        while (spent < fillTarget && roster.size() < ROSTER_SAFETY_CAP) {
            EnemyType pick = chooseFill(spentByType, spent, budget, maxPerType,
                    rosterContainsRanged(roster), rosterContainsMelee(roster));
            if (pick == null) break;
            float cost = threatOf(pick);
            roster.add(pick);
            spentByType.merge(pick, cost, Float::sum);
            spent += cost;
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
            if (typeSpent + cost > maxPerType) continue;   // rule 2: per-type cap
            if (spent + cost > budget)         continue;   // never overshoot the floor budget
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

        /** Maximum Threat points a single non-anchor room may hold. */
        public float perRoomThreatPointCap() { return perRoomThreatPointCap; }

        /** Depth-scaled Threat-Point cost of one enemy of the given type. */
        public float threatOf(EnemyType type) { return threatByType.getOrDefault(type, 0f); }
    }
}
