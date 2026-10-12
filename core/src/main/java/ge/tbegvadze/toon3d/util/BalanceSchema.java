package ge.tbegvadze.toon3d.util;

import ge.tbegvadze.toon3d.route.BossTable;

import ge.tbegvadze.toon3d.enemy.EnemyRole;
import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.enemy.HeavyAttack;
import ge.tbegvadze.toon3d.enemy.SpecialAbility;
import ge.tbegvadze.toon3d.entity.AbilityInstance;
import ge.tbegvadze.toon3d.entity.WeaponAbility;
import ge.tbegvadze.toon3d.entity.WeaponRoller;
import ge.tbegvadze.toon3d.entity.WeaponTier;
import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.item.ItemCategory;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.util.ItemConstants;
import ge.tbegvadze.toon3d.progression.Attribute;
import ge.tbegvadze.toon3d.progression.BuildStats;
import ge.tbegvadze.toon3d.progression.BuildTag;
import ge.tbegvadze.toon3d.progression.Keystone;
import ge.tbegvadze.toon3d.progression.Perk;
import ge.tbegvadze.toon3d.progression.PlayerStats;
// The order-7 ROUTE ECONOMICS rules read the (headless, LibGDX-free) route subsystem: the priced
// node ledger, the pricing/trajectory model, and the real map generator the audit walks.
import ge.tbegvadze.toon3d.route.DangerTier;
import ge.tbegvadze.toon3d.route.MysteryOutcome;
import ge.tbegvadze.toon3d.route.NodeEconomics;
import ge.tbegvadze.toon3d.route.NodeEconomicsRegistry;
import ge.tbegvadze.toon3d.route.NodeTypeRegistry;
import ge.tbegvadze.toon3d.route.RegionPlan;
import ge.tbegvadze.toon3d.route.RouteEconomicsModel;
import ge.tbegvadze.toon3d.route.RouteMap;
import ge.tbegvadze.toon3d.route.RouteMapGenerator;
import ge.tbegvadze.toon3d.route.RouteNodeType;
import ge.tbegvadze.toon3d.route.RouteRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * LAYER 2 of the Balance Authority — the RULE SCHEMA (see {@code docs/game-balance-authority.txt}).
 *
 * <p>The three layers, cleanly separated:
 * <ol>
 *   <li><b>VALUES</b> (tunable) — {@link BalanceConfig}. Designers tune freely.</li>
 *   <li><b>RULES</b> (this class) — a declarative registry of every constraint the values must
 *       satisfy. Rules are DATA (registered subjects + bands evaluated through {@link GameMath}),
 *       never switch statements — the same discipline as {@code route/RouteRegistries}.</li>
 *   <li><b>ENFORCEMENT</b> — {@code BalanceAuditTest} (core test source set) runs every rule under
 *       {@code ./gradlew test} and FAILS THE BUILD on any unwaived violation. {@link BalanceReport}
 *       stays the human-readable table printer — a VIEW of this schema, not the only check.</li>
 * </ol>
 *
 * <p><b>Waivers.</b> When a value must deliberately sit outside its band it needs an explicit
 * {@link Waiver} — visible, reasoned, auditable — registered via {@link #waive}. Never a silent
 * exception. {@link BalanceReport} prints all active waivers in their own table.
 *
 * <p><b>Coverage.</b> The schema iterates the game's own content enumerations
 * ({@link EnemyType}, {@link Perk}, {@link ItemType}, {@link AmmoType}) so content that
 * skips pricing can no longer ship silently: an unclassified weapon item, an enemy role without a
 * band, or an unpriced heal fails the audit (the anti-"shipped unpriced" rule — the necrotic
 * faction once corrupted every floor budget for weeks this way).
 *
 * <p>Pure JVM: no LibGDX state, only {@link BalanceConfig} + {@link GameMath} + the headless
 * content enums, so it runs in CI. Every rule addition belongs here (later orders of the
 * new-game-balancr series register theirs into this same registry) and must be mirrored in
 * {@code docs/game-balance-authority.txt} in the same commit.
 */
public final class BalanceSchema {

    private BalanceSchema() {}

    // =====================================================================================
    // RULE KINDS — one enum constant per registered rule family (R-* in the authority doc).
    // =====================================================================================

    public enum RuleKind {
        /**
         * R-ROLE (balance-overhaul order 3; REPLACES R-WEAPON's power-score bands): the reference scenarios —
         * niche (R-ROLE-1), generalist (2), no dominance (3), risk pays (4), ammo feasible (5).
         */
        WEAPON_ROLE,
        /** R-ENEMY (part 1): every archetype declares a ROLE; threatPoints must land in the role TP band. */
        ENEMY_THREAT_POINTS,
        /** R-ENEMY (part 2, balance-overhaul order 1 R8): depth-1 hits-to-kill / hits-to-die land in the role's R8 bands. */
        ENEMY_HITS,
        /** R-HEAL: every heal/armour pickup prices into its survival-turns-bought band. */
        HEAL_PRICING,
        /** R-TELEGRAPH: no attack > 25% reference eHP un-telegraphed; boss hard cap 35%. */
        TELEGRAPH,
        /**
         * R-HEAVY (balance-overhaul order 4): every HeavyAttack x depth 1..25 deals [MIN, MAX] of the on-curve
         * player's max HP unguarded, is committed >= 1 turn ahead, and costs <= HEAVY_GUARDED_MAX_FRACTION guarded.
         */
        HEAVY,
        /**
         * R-HAZARD (balance-overhaul order 4, H13/H14): barrel blasts are fractions that kill every CHAFF at every
         * depth and never out-hit a heavy on the player; barrels sit in >= HAZARD_GROUP_ROOM_SHARE of the group
         * rooms on COMBAT / ELITE floors and nowhere else.
         */
        HAZARD,
        /** R-DOT: exactly one definition per status; shim files must re-export BalanceConfig byte-for-byte. */
        DOT_UNIQUENESS,
        /** R-FLAGS: no live test/debug flags (any TEST/DEBUG boolean must be false). */
        FLAGS,
        /** COVERAGE: every content entry (weapon item, consumable, ammo type, enemy role) is classified/priced. */
        COVERAGE,
        /** R-ABILITY (order 2): every ability has a priced PP value; every rollable tier fits its ability-PP budget. */
        ABILITY_BUDGET,
        /** R-CREDITS v2 (balance-overhaul order 6): income per floor / NEEDED BUNDLE per floor in [1.10, 1.40], every region. */
        CREDITS,
        /** R-XP-PACE (order 4): a floor's available XP / xpRequired(expectedLevel) holds [1.0, 1.3] at every depth 1..15. */
        XP_PACE,
        /**
         * R-PERK (balance-overhaul order 5; REPLACED R-CARD and R-CARD-BREAKPOINT in CP2): PK1 FELT — every perk
         * rank improves its niche metric by >= PERK_FELT_MIN_GAIN at every BUILD_PERK_REFERENCE_DEPTHS depth and,
         * by its max rank, crosses >= 1 integer TTK / TTD breakpoint there at some depth; PK2 FOCUSED — no
         * non-VITALITY perk at max rank improves all eight reference scenarios by > PERK_FOCUSED_MAX_GAIN, and
         * no VITALITY rank improves any scenario by > PERK_VITALITY_MAX_GAIN_PER_RANK.
         */
        PERK,
        /** R-KEYSTONE (balance-overhaul order 5): niche gain >= KEYSTONE_NICHE_MIN_GAIN and some other probe worse by >= KEYSTONE_COST_MIN_LOSS. */
        KEYSTONE,
        /**
         * R-CARRY (balance-overhaul order 5; replaces the total-PP invariant): every tag's expected depth-15
         * build (perks at the expected rank count + its keystone) lifts the player's TTK x TTD within
         * 1 + BUILD_CARRY_TOLERANCE of every other tag's (best / worst).
         */
        BUILD_CARRY,
        /** R-REGION (order 5): region TP dial is monotonic + bounded; C/D spend more than A; coupling holds per lane. */
        REGION_DANGER,
        /** R-BOSS-GATE (order 6): the DEPTH-1 starting loadout cannot win — turnsToKill >= 2x survivableTurns (with max heals). */
        BOSS_GATE,
        /** R-BOSS-FAIR (order 6): the EXPECTED loadout gets a real but winnable fight — length + survival ratio in band. */
        BOSS_FAIR,
        /** R-BOSS-VERB-CAP (order 6; telegraphed cap raised to 50% by balance-overhaul order 4): un-telegraphed verbs <= 25% eHP, telegraphed <= 50%. */
        BOSS_VERB_CAP,
        /** R-BOSS-REWARD (order 6): a boss's reward refunds the modelled fight consumption times the risk premium. */
        BOSS_REWARD,
        /** R-BOSS-AMMO (order 6): the fight's ammo demand is coverable by reserve caps + the boss-arena ammo budget. */
        BOSS_AMMO,
        /** R-ROUTE-PRICED (order 7): every node type, affix and mystery outcome has a NodeEconomics row. */
        ROUTE_PRICED,
        /** R-RISK-PREMIUM (order 7): (reward premium)/(threat premium) in [1.0, 1.2] for every danger node. */
        RISK_PREMIUM,
        /** R-CALM-COST (order 7): a calm node's EV sits 10-30% BELOW a standard combat node's. */
        CALM_COST,
        /** R-MYSTERY-EV (order 7): the weighted mystery table is within ±15% of a combat node; worst case bounded. */
        MYSTERY_EV,
        /** R-PIPS-DERIVED (order 7): the displayed DangerTier equals the tier DERIVED from priced threat. */
        PIPS_DERIVED,
        /** R-HONEST-SAFE (order 7): a safe-looking node IS safe, and scan tones partition by price. */
        HONEST_SAFE,
        /** R-TRAJECTORY (order 7): SAFEST/DEADLIEST/BALANCED journeys all stay inside the route bands. */
        TRAJECTORY,
        /** R-ROUTE-GUARANTEES (order 7): no lane strands a run (upgrade/calm/pre-boss/real choices). */
        ROUTE_GUARANTEES,
        /** R-SINGLE-DIFFICULTY (order 8): exactly one starting-attribute block; no mode-family naming anywhere. */
        SINGLE_DIFFICULTY,
        /** R-LADDER (balance-overhaul order 1): the power ladder — on-curve flat, lag punished, ahead bounded, level/rarity/vitality felt. */
        LADDER,
        /** R-LADDER-AFFORD (balance-overhaul order 1): the shop LEVEL UP rung costs <= LADDER_AFFORD_FRACTION of one COMBAT floor's income. */
        LADDER_AFFORD,
        /** R-SUPPLY (balance-overhaul order 2): supply tracks the roster's demand per node spec; heal floor; spread. */
        SUPPLY,
        /** R-DENSITY (balance-overhaul order 2): bodies, groups, first contact, footprint and density per node type. */
        DENSITY,
        /** S-GATE (order 9): 0% of HOARDER-START-WEAPON seeds clear the first boss. */
        SIM_GATE,
        /** S-FAIR (order 9): TACTICAL median death depth in band; deaths readable. */
        SIM_FAIR,
        /** S-SKILL (order 9): TACTICAL out-lives NAIVE by at least the skill gap. */
        SIM_SKILL,
        /** S-ROUTE (order 9): played per-floor drain matches the order-7 modelled trajectory band. */
        SIM_ROUTE,
        /**
         * S-ECONOMY (order 9; re-based by balance-overhaul order 2): the supply a played floor actually
         * yields tracks the supply its planner put down; the emergency lifeline stays rare.
         */
        SIM_ECONOMY,
        /**
         * S-SUPPLY (balance-overhaul order 2): TACTICAL leaves a COMBAT floor at a mean health fraction in
         * [SIM_SUPPLY_EXIT_HEALTH_MIN, MAX] ("not full"), and no played floor is below its heal floor.
         */
        SIM_SUPPLY,
        /** S-SOFTLOCK (order 9): zero seeds end in a "cannot damage anything" state. */
        SIM_SOFTLOCK,
        /** S-LAG (balance-overhaul order 1): the start-weapon hoarder dies by SIM_LAG_MAX_MEDIAN_DEPTH (median). */
        SIM_LAG,
        /**
         * S-SWITCH (balance-overhaul order 3, A8 re-stated by D3): the C4 hint works — enough COMBAT floors
         * show one, TACTICAL takes it, and each hinted COMBAT floor yields a switch. Never waived.
         */
        SIM_SWITCH,
        /**
         * S-HEAVY (balance-overhaul order 4, A8): TACTICAL answers (avoids / guards / staggers / kills first) at
         * least SIM_HEAVY_TACTICAL_MIN_SUCCESS of the heavies committed at it, NAIVE at most
         * SIM_HEAVY_NAIVE_MAX_SUCCESS, and TACTICAL met at least SIM_HEAVY_MIN_SAMPLE of them. Never waived.
         */
        SIM_HEAVY,
        /**
         * S-BUILD (balance-overhaul order 5, A6): the four build-biased TACTICAL variants' median death depths
         * within SIM_BUILD_DEPTH_SPREAD of each other (depth-derived: rides the navigation waiver), and plain
         * TACTICAL's end-of-run dominant tag is no single tag on more than SIM_BUILD_MAX_DOMINANT_TAG_SHARE of
         * its runs (over at least SIM_BUILD_MIN_SAMPLE runs that held a perk rank). The depth-spread and dominant-tag
         * lines ride the navigation waiver (the latter by owner ruling D7); the sample line is never waived.
         */
        SIM_BUILD,
        /**
         * S-SHOP (balance-overhaul order 6, F14): TACTICAL buys at least SIM_SHOP_MIN_OFFERS_PER_VISIT offers per
         * Fabricator visit (one visit per exited floor; a SHOP node's two machines count as one visit), and its
         * median credits held when a lost run ends are at most SIM_SHOP_MAX_UNSPENT_FLOORS floors of income.
         */
        SIM_SHOP
    }

    // =====================================================================================
    // RESULT + WAIVER data types.
    // =====================================================================================

    /** One evaluated rule instance: a subject, its computed value, its band, and the verdict. */
    public static final class RuleResult {
        public final RuleKind kind;
        public final String   subject;
        public final float    value;
        public final float    bandMinimum;
        public final float    bandMaximum;
        public final boolean  satisfied;
        public final boolean  waived;
        public final String   detail;

        RuleResult(RuleKind kind, String subject, float value,
                   float bandMinimum, float bandMaximum, boolean satisfied, String detail) {
            this.kind        = kind;
            this.subject     = subject;
            this.value       = value;
            this.bandMinimum = bandMinimum;
            this.bandMaximum = bandMaximum;
            this.satisfied   = satisfied;
            this.waived      = !satisfied && findWaiver(kind, subject) != null;
            this.detail      = detail;
        }

        /** True when this result must fail the audit: out of band AND not explicitly waived. */
        public boolean isViolation() { return !satisfied && !waived; }

        @Override public String toString() {
            return String.format("[%s] %s: value=%.2f band=%.2f..%.2f %s%s%s",
                    kind, subject, value, bandMinimum, bandMaximum,
                    satisfied ? "OK" : "OUT-OF-BAND",
                    waived ? " (WAIVED)" : "",
                    detail == null || detail.isEmpty() ? "" : " — " + detail);
        }
    }

    /**
     * Builds a rule result from OUTSIDE this class — the seam the order-9 behavioural bands use.
     * Those bands are evaluated against SIMULATED PLAY (sim/BehavioralBands), which needs a played
     * matrix as input and therefore cannot live in this pure, input-free schema; routing their
     * results through here keeps them subject to the same waiver lookup and the same report tables
     * as every other rule.
     */
    public static RuleResult result(RuleKind kind, String subject, float value,
                                    float bandMinimum, float bandMaximum,
                                    boolean satisfied, String detail) {
        return new RuleResult(kind, subject, value, bandMinimum, bandMaximum, satisfied, detail);
    }

    /** An explicit, reasoned exception to one rule for one subject. Never silent. */
    public static final class Waiver {
        public final RuleKind kind;
        public final String   subject;
        public final String   reason;
        public final String   expiryCondition;

        Waiver(RuleKind kind, String subject, String reason, String expiryCondition) {
            this.kind            = kind;
            this.subject         = subject;
            this.reason          = reason;
            this.expiryCondition = expiryCondition;
        }
    }

    // =====================================================================================
    // WAIVER REGISTRY — populated at class-init via waive(); read-only afterwards.
    // =====================================================================================

    private static final List<Waiver> WAIVERS = new ArrayList<>();

    static {
        // The Railgun's [WEAPON_POWER] "scarcity-gated over-band" waiver was DELETED by balance-overhaul
        // order 3 (AS8): R-WEAPON is retired and the Railgun is governed by R-ROLE (the PLATED niche plus
        // its own ammo-feasibility check, R-ROLE-5).
        waiveNavigationLimitedBands();
        // Balance-overhaul order 2 (CP6): the route ledger is now priced on what each floor is BUILT with
        // (its NodeSupplySpec plan). The old ledger credited the bespoke MED-BAY and EVENT rooms with 35% of
        // a combat floor's ordinary loot they never placed; priced honestly, REST sits below the calm band
        // deep and EVENT a point above it shallow. Their contents are order 6's to retune (SCOPE OUT).
        String bespokeReason = "Priced honestly on its NodeSupplySpec plan since balance-overhaul order 2: "
                + "the bespoke room never placed the ordinary loot the old ledger credited it with, and "
                + "its contents are balance-overhaul order 6's to retune.";
        String bespokeExpiry = "Expires with balance-overhaul order 6 (REST / EVENT / MYSTERY contents).";
        waive(RuleKind.CALM_COST, "rest EV discount",  bespokeReason, bespokeExpiry);
        // Balance-overhaul order 6 (CP5 -> CP6, a deliberate temporary state): the shop re-fit makes a credit worth
        // ~2.3x the power, so COMBAT's bounty income out-grows the credit-less mystery outcomes (table 0.82x COMBAT
        // at d25). CP6 re-expresses every mystery outcome in ladder currencies and re-derives R-MYSTERY-EV (F15:
        // the table's EV >= COMBAT's, with higher variance), which replaces this rule and retires this waiver.
        waive(RuleKind.MYSTERY_EV, "weighted table vs combat",
                "The CP5 shop re-fit re-priced credits; mystery outcomes carry none until CP6 re-expresses them (F15).",
                "Expires at balance-overhaul order 6 CP6 (R-MYSTERY-EV re-derived in ladder currencies).");
        waive(RuleKind.CALM_COST, "event EV discount", bespokeReason, bespokeExpiry);
        // Balance-overhaul order 5, owner ruling D3: SCAVENGER is an ECONOMY tag. Its perks change supply,
        // shop prices and carrier drops — none of which is a turn to kill or a hit to die in a reference
        // fight — so R-CARRY's TTK x TTD reads it through a heal-at-shop-price conversion that cannot reach
        // the band at any sane magnitude (1.09 against the combat tags' ~1.75-2.1). Its row is still
        // computed and printed; it is excluded from the eight-tag best / worst and flagged WAIVED.
        waive(RuleKind.BUILD_CARRY, "R-CARRY " + BuildTag.SCAVENGER.name(),
                "SCAVENGER's value is supply, shop and carrier economy, not reference-fight TTK / TTD; "
                        + "R-CARRY's conversion under-reads it (owner ruling D3, balance-overhaul order 5).",
                "Expires when order 6 re-derives the economy with SCAVENGER in the expected player.");
        // Balance-overhaul order 5, owner ruling D6: Wildfire (D5) ranks 2-3 add burn TURNS and its D4
        // breakpoint reads fuel; no reference group fight outlasts a burn or saves a spray. Exact subjects
        // only — rank-1 FELT and PK2 stay enforced.
        String wildfireReason = "every reference group fight ends inside the first spray or two, so burn duration "
                + "is invisible to the scenario math";
        String wildfireExpiry = "a long-group reference scenario exists (order 7 feel pass)";
        for (int depth : BalanceConfig.BUILD_PERK_REFERENCE_DEPTHS) {
            waive(RuleKind.PERK, "PK1 WILDFIRE r2 felt d" + depth, wildfireReason, wildfireExpiry);
            waive(RuleKind.PERK, "PK1 WILDFIRE r3 felt d" + depth, wildfireReason, wildfireExpiry);
        }
        waive(RuleKind.PERK, "PK1 WILDFIRE breakpoint by r3 (D4 resource: SCENARIO_AMMO S5 Incinerator)",
                wildfireReason, wildfireExpiry);
    }

    /** True when a tag's R-CARRY row carries an explicit waiver (excluded from the best / worst spread). */
    public static boolean isCarryWaived(BuildTag tag) {
        return findWaiver(RuleKind.BUILD_CARRY, carrySubject(tag)) != null;
    }

    private static String carrySubject(BuildTag tag) {
        return "R-CARRY " + tag.name();
    }

    /**
     * The order-9 behavioural bands whose measurement currently depends on the SCRIPTED PLAYER's
     * pathing competence rather than on the game's balance. The simulator plays the real systems, but
     * its heuristic navigation still fails to finish a sizeable share of generated floors (it runs the
     * floor's turn cap out instead of reaching the stairs), which drags every depth-derived statistic
     * down. Waiving them keeps the numbers VISIBLE in every report while being honest that they do not
     * yet measure what they claim to; S-GATE and S-SOFTLOCK are unaffected and stay enforced.
     */
    private static void waiveNavigationLimitedBands() {
        String reason = "Measures the scripted policy's floor-completion rate, not the game's balance: "
                + "the order-9 navigation heuristic still stalls on a large share of generated floors, "
                + "so every depth-derived statistic reads low.";
        String expiry = "Expires when the balance simulator's scripted policies reach the exit on >90% "
                + "of floors (BalanceSimTest reports the stall rate on every run).";
        // Balance-overhaul order 6 (F8): the run-outcome lines that replaced "TACTICAL median death depth" are all
        // depth-derived and inherit this waiver and expiry (A6: reported until it expires, then enforced).
        waive(RuleKind.SIM_FAIR,    "TACTICAL win rate",            reason, expiry);
        waive(RuleKind.SIM_FAIR,    "TACTICAL reaches depth " + BalanceConfig.SIM_TACTICAL_REACH_DEPTH, reason, expiry);
        waive(RuleKind.SIM_FAIR,    "TACTICAL median death depth (losses)", reason, expiry);
        waive(RuleKind.SIM_FAIR,    "NAIVE win rate",               reason, expiry);
        waive(RuleKind.SIM_FAIR,    "NAIVE median death depth",     reason, expiry);
        waive(RuleKind.SIM_FAIR,    "TACTICAL first-boss clears (of seeds reaching it)", reason, expiry);
        waive(RuleKind.SIM_FAIR,    "NAIVE reaches the first boss", reason, expiry);
        waive(RuleKind.SIM_FAIR,    "NAIVE first-boss clears",      reason, expiry);
        waive(RuleKind.SIM_FAIR,    "TACTICAL readable deaths",    reason, expiry);
        waive(RuleKind.SIM_SKILL,   "TACTICAL vs NAIVE depth gap", reason, expiry);
        waive(RuleKind.SIM_ROUTE,   "played per-floor net drain",  reason, expiry);
        waive(RuleKind.SIM_ECONOMY, "experienced vs planned supply", reason, expiry);
        waive(RuleKind.SIM_ECONOMY, "emergency lifeline floors",   reason, expiry);
        // S-LAG (balance-overhaul order 1) inherits this waiver and its expiry: a hoarder that STALLS on
        // floor 1 reads as "dies early" for navigation reasons, so the band is reported but not yet a
        // statement about the ladder. R-LADDER L2 proves the same property on paper meanwhile.
        waive(RuleKind.SIM_LAG,     "HOARDER-START-WEAPON median death depth", reason, expiry);
        // S-BUILD's depth part (balance-overhaul order 5, A6) is depth-derived too: it inherits this exact waiver
        // and expiry, and is enforced the day SIM_FAIR's median depth is.
        waive(RuleKind.SIM_BUILD,   "build-bias median depth spread", reason, expiry);
        // Owner ruling D7 (balance-overhaul order 5): the dominant-tag share inherits the same waiver and expiry —
        // ~91% of sim runs never level up and every ranked run holds exactly one rank, so the "dominant tag" is the
        // first synergy pick off the Chaingun + Shotgun start kit (measured 0.47, MARKSMAN 23 of 49). The sample
        // line ("TACTICAL runs holding a perk rank") stays ENFORCED.
        waive(RuleKind.SIM_BUILD,   "TACTICAL dominant-tag share", reason, expiry);
        // S-SHOP (balance-overhaul order 6, F14): buys per visit are bounded by the credits a run COLLECTS, and the
        // stalling policy picks up under half its planned supply (S-ECONOMY) — measured 0.64 offers per visit with
        // a median 0.13 floors of income left unspent at death, i.e. it spends everything it has. Same waiver and
        // expiry; the unspent-credits line stays ENFORCED.
        waive(RuleKind.SIM_SHOP,    "TACTICAL offers bought per Fabricator visit", reason, expiry);
    }

    /** Registers an explicit waiver. Every call must be mirrored in docs/game-balance-authority.txt. */
    private static void waive(RuleKind kind, String subject, String reason, String expiryCondition) {
        WAIVERS.add(new Waiver(kind, subject, reason, expiryCondition));
    }

    /** All active waivers, for BalanceReport's WAIVERS table. */
    public static List<Waiver> activeWaivers() {
        return Collections.unmodifiableList(WAIVERS);
    }

    private static Waiver findWaiver(RuleKind kind, String subject) {
        for (Waiver waiver : WAIVERS) {
            if (waiver.kind == kind && waiver.subject.equals(subject)) return waiver;
        }
        return null;
    }

    // =====================================================================================
    // R-ROLE — the ranged-weapon registry and the reference scenarios live in WeaponRoleModel
    // (balance-overhaul order 3). R-WEAPON's WeaponRole power bands and RangedWeaponSpec were RETIRED
    // with it (override record: docs/game-balance-authority.txt R-ROLE).
    // =====================================================================================

    // Classification of every WEAPON-category ItemType for the coverage rule. A weapon item must be
    // RANGED (registered above), MELEE (no power-score rule — melee swings once per turn and is
    // priced through its flat damage), or AMMO_FALLBACK (legacy item with no weapon class behind it;
    // picking it up grants ammo — see PlayerController's WEAPON_PISTOL handling).
    public enum WeaponItemClassification { RANGED, MELEE, AMMO_FALLBACK }

    private static final Map<ItemType, WeaponItemClassification> WEAPON_ITEM_CLASSIFICATIONS =
            buildWeaponItemClassifications();

    private static Map<ItemType, WeaponItemClassification> buildWeaponItemClassifications() {
        Map<ItemType, WeaponItemClassification> classifications = new EnumMap<>(ItemType.class);
        for (WeaponRoleModel.RoleWeapon weapon : WeaponRoleModel.weapons()) {
            classifications.put(weapon.itemType, WeaponItemClassification.RANGED);
        }
        classifications.put(ItemType.WEAPON_FIST,     WeaponItemClassification.MELEE);
        classifications.put(ItemType.WEAPON_KNIFE,    WeaponItemClassification.MELEE);
        classifications.put(ItemType.WEAPON_HAMMER,   WeaponItemClassification.MELEE);
        classifications.put(ItemType.WEAPON_CHAINSAW, WeaponItemClassification.MELEE);
        classifications.put(ItemType.WEAPON_PISTOL,   WeaponItemClassification.AMMO_FALLBACK);
        return Collections.unmodifiableMap(classifications);
    }

    // =====================================================================================
    // R-ENEMY — role bands as DATA (an EnumMap, not a switch): a new EnemyRole without an
    // entry here is a COVERAGE violation, never a silent pass.
    // =====================================================================================

    private static final Map<EnemyRole, float[]> ENEMY_THREAT_POINT_BANDS = buildEnemyThreatPointBands();

    private static Map<EnemyRole, float[]> buildEnemyThreatPointBands() {
        // Re-derived from the R8 hit targets (balance-overhaul order 1): the TP of the centre of each
        // role's R8 box, spread by LADDER_TP_BAND_LOW/HIGH_FACTOR. BOSS deliberately absent (SECTION 14).
        Map<EnemyRole, float[]> bands = new EnumMap<>(EnemyRole.class);
        for (Map.Entry<EnemyRole, float[]> entry : buildEnemyHitBands().entrySet()) {
            float[] hitBand = entry.getValue();
            float centreHitsToKill = (hitBand[0] + hitBand[1]) / 2f;
            float centreHitsToDie = Float.isInfinite(hitBand[3])
                    ? hitBand[2] * BalanceConfig.LADDER_TTD_OPEN_BAND_CENTRE_FACTOR
                    : (hitBand[2] + hitBand[3]) / 2f;
            float centreEffectiveHitPoints = centreHitsToKill * GameMath.ladderReferenceHitDamage();
            float centreHit = BalanceConfig.REFERENCE_PLAYER_EHP / centreHitsToDie;
            float centreThreatPoints = GameMath.threatPoints(centreHit, 1, centreEffectiveHitPoints,
                    BalanceConfig.REFERENCE_PLAYER_DPT, BalanceConfig.POSITIONAL_MULT_MELEE);
            bands.put(entry.getKey(), new float[]{
                    centreThreatPoints * BalanceConfig.LADDER_TP_BAND_LOW_FACTOR,
                    centreThreatPoints * BalanceConfig.LADDER_TP_BAND_HIGH_FACTOR});
        }
        return Collections.unmodifiableMap(bands);
    }

    /** The [min, max] TP band for a role, or null when the role has none registered (BOSS). */
    public static float[] threatPointBand(EnemyRole role) {
        float[] band = ENEMY_THREAT_POINT_BANDS.get(role);
        return band == null ? null : band.clone();
    }

    /**
     * The R8 hit bands for a role — {hitsToKillMin, hitsToKillMax, hitsToDieMin, hitsToDieMax} — or null
     * for BOSS (bosses follow the SECTION 14 ruleset). CHAFF has no hits-to-die ceiling (+infinity).
     */
    public static float[] enemyHitBand(EnemyRole role) {
        float[] band = ENEMY_HIT_BANDS.get(role);
        return band == null ? null : band.clone();
    }

    // R8 (balance-overhaul order 1) hit bands per role, as DATA: depth-1 hits the on-curve reference
    // (Assault Rifle, COMMON L1, at LADDER_REFERENCE_RANGE_TILES) needs to kill the archetype, and the
    // archetype's ordinary hits the 205-eHP start player survives. Replaces the golden-ratio bands (the
    // CHAFF pack exemption and the MINI_ELITE spike exemption are gone: every role now has a target).
    private static final Map<EnemyRole, float[]> ENEMY_HIT_BANDS = buildEnemyHitBands();

    private static Map<EnemyRole, float[]> buildEnemyHitBands() {
        Map<EnemyRole, float[]> bands = new EnumMap<>(EnemyRole.class);
        bands.put(EnemyRole.CHAFF, new float[]{
                BalanceConfig.LADDER_TTK_HITS_CHAFF_MIN, BalanceConfig.LADDER_TTK_HITS_CHAFF_MAX,
                BalanceConfig.LADDER_TTD_HITS_CHAFF_MIN, Float.POSITIVE_INFINITY});
        bands.put(EnemyRole.SOLDIER, new float[]{
                BalanceConfig.LADDER_TTK_HITS_SOLDIER_MIN, BalanceConfig.LADDER_TTK_HITS_SOLDIER_MAX,
                BalanceConfig.LADDER_TTD_HITS_SOLDIER_MIN, BalanceConfig.LADDER_TTD_HITS_SOLDIER_MAX});
        bands.put(EnemyRole.BRUISER, new float[]{
                BalanceConfig.LADDER_TTK_HITS_BRUISER_MIN, BalanceConfig.LADDER_TTK_HITS_BRUISER_MAX,
                BalanceConfig.LADDER_TTD_HITS_BRUISER_MIN, BalanceConfig.LADDER_TTD_HITS_BRUISER_MAX});
        bands.put(EnemyRole.MINI_ELITE, new float[]{
                BalanceConfig.LADDER_TTK_HITS_MINI_ELITE_MIN, BalanceConfig.LADDER_TTK_HITS_MINI_ELITE_MAX,
                BalanceConfig.LADDER_TTD_HITS_MINI_ELITE_MIN, BalanceConfig.LADDER_TTD_HITS_MINI_ELITE_MAX});
        return Collections.unmodifiableMap(bands);
    }

    // =====================================================================================
    // SPECIAL-VERB PRICING COVERAGE (new-game-balancr order 5). The cycle-averaged Threat-Point
    // model (EnemyType.baseThreatPoints) prices every enemy SPECIAL verb. COVERAGE now fails the
    // build if a catalogued SpecialAbility has NO registered equivalence — the anti-"shipped
    // unpriced" discipline extended to enemy moves. DATA (an EnumMap), never a switch. A new verb
    // added to SpecialAbility without a row here is a COVERAGE violation.
    // =====================================================================================

    /** How each enemy SPECIAL verb is priced into cycle-averaged Threat Points (order 5). */
    public enum SpecialVerbPricing {
        /** Priced as per-cast equivalent DAMAGE folded through the cycle average (GameMath.specialEquivalent{BuffSelf,Debuff*} and heavyExpectedDamageForPricing). */
        CYCLE_DAMAGE,
        /** Priced as amortised per-turn threat of the summoned bodies (GameMath.specialEquivalentSummon). */
        AMORTISED_SUMMON,
        /** Exempt from cycle-DPT pricing — a death-triggered finisher governed by the telegraph rule, not sustained output. */
        TELEGRAPH_EXEMPT
    }

    private static final Map<SpecialAbility, SpecialVerbPricing> SPECIAL_VERB_PRICING =
            buildSpecialVerbPricing();

    private static Map<SpecialAbility, SpecialVerbPricing> buildSpecialVerbPricing() {
        Map<SpecialAbility, SpecialVerbPricing> pricing = new EnumMap<>(SpecialAbility.class);
        pricing.put(SpecialAbility.AREA_STRIKE,   SpecialVerbPricing.CYCLE_DAMAGE);
        pricing.put(SpecialAbility.BUFF_SELF,     SpecialVerbPricing.CYCLE_DAMAGE);
        pricing.put(SpecialAbility.DEBUFF_PLAYER, SpecialVerbPricing.CYCLE_DAMAGE);
        pricing.put(SpecialAbility.SUMMON,        SpecialVerbPricing.AMORTISED_SUMMON);
        pricing.put(SpecialAbility.SELF_DESTRUCT, SpecialVerbPricing.TELEGRAPH_EXEMPT);
        return Collections.unmodifiableMap(pricing);
    }

    /** The pricing classification for an enemy SPECIAL verb, or null when none is registered (COVERAGE fails). */
    public static SpecialVerbPricing specialVerbPricing(SpecialAbility ability) {
        return SPECIAL_VERB_PRICING.get(ability);
    }

    // =====================================================================================
    // R-TELEGRAPH — the registered attack list (data). readableKind: TELE = wind-up telegraph,
    // LANE = ranged cardinal-line tell, FACE = positional counter, NONE = un-telegraphed burst.
    // =====================================================================================

    /** One registered attack for the telegraph fairness audit. */
    public static final class TelegraphAttackSpec {
        public final String  attackName;
        public final int     baseHit;
        public final String  readableKind;
        public final boolean isBossAttack;

        TelegraphAttackSpec(String attackName, int baseHit, String readableKind, boolean isBossAttack) {
            this.attackName   = attackName;
            this.baseHit      = baseHit;
            this.readableKind = readableKind;
            this.isBossAttack = isBossAttack;
        }

        public boolean isReadable() { return !"NONE".equals(readableKind); }
    }

    private static final List<TelegraphAttackSpec> TELEGRAPH_ATTACKS = buildTelegraphAttackRegistry();

    private static List<TelegraphAttackSpec> buildTelegraphAttackRegistry() {
        List<TelegraphAttackSpec> registry = new ArrayList<>();
        registry.add(new TelegraphAttackSpec("Gore Biter bite",    BalanceConfig.GORE_BITER_ATTACK_DAMAGE,  "NONE", false));
        registry.add(new TelegraphAttackSpec("Plague Hulk smash",  BalanceConfig.PLAGUE_HULK_ATTACK_DAMAGE, "NONE", false));
        registry.add(new TelegraphAttackSpec("Void Shroud strike", BalanceConfig.VOID_SHROUD_ATTACK_DAMAGE, "NONE", false));
        registry.add(new TelegraphAttackSpec("Void Shroud flank",
                Math.round(BalanceConfig.VOID_SHROUD_ATTACK_DAMAGE * BalanceConfig.VOID_SHROUD_FLANK_DAMAGE_MULTIPLIER),
                "FACE", false));
        registry.add(new TelegraphAttackSpec("Shell Brute melee",  BalanceConfig.SHELL_BRUTE_ATTACK_DAMAGE, "NONE", false));
        registry.add(new TelegraphAttackSpec("Iron Stalker melee", BalanceConfig.IRON_STALKER_MELEE_DAMAGE, "NONE", false));
        registry.add(new TelegraphAttackSpec("Iron Stalker shot",  BalanceConfig.IRON_STALKER_RANGED_DAMAGE, "LANE", false));
        registry.add(new TelegraphAttackSpec("Eye Tyrant beam",    BalanceConfig.EYE_TYRANT_ATTACK_DAMAGE,  "LANE", false));
        registry.add(new TelegraphAttackSpec("Acid Drone spit",    BalanceConfig.ACID_DRONE_ATTACK_DAMAGE,  "LANE", false));
        registry.add(new TelegraphAttackSpec("Mire Wraith acid",   BalanceConfig.MIRE_WRAITH_ATTACK_DAMAGE, "LANE", false));
        registry.add(new TelegraphAttackSpec("Ghoul claw",         BalanceConfig.GHOUL_ATTACK_DAMAGE,       "NONE", false));
        registry.add(new TelegraphAttackSpec("Crawler bite",       BalanceConfig.CRAWLER_ATTACK_DAMAGE,     "NONE", false));
        registry.add(new TelegraphAttackSpec("Revenant strike",    BalanceConfig.REVENANT_ATTACK_DAMAGE,    "NONE", false));
        registry.add(new TelegraphAttackSpec("Vortex Eye bolt",    BalanceConfig.VORTEX_EYE_ATTACK_DAMAGE,  "LANE", false));
        registry.add(new TelegraphAttackSpec("Blight Corr. smash", BalanceConfig.BLIGHT_CORRUPTOR_ATTACK_DAMAGE, "NONE", false));
        registry.add(new TelegraphAttackSpec("Auric shard launch", BalanceConfig.AURIC_SENTINEL_ATTACK_DAMAGE, "LANE", false));
        registry.add(new TelegraphAttackSpec("Cinderforge slam",   BalanceConfig.CINDERFORGE_COLOSSUS_ATTACK_DAMAGE, "NONE", false));
        // The Shell Brute CHARGE and the Plague Hulk blast rows were REMOVED by balance-overhaul order 4
        // (override): heavies are R-HEAVY's domain (heavyResults), sized as a fraction of on-curve max HP.
        // Boss verbs are NOT listed here (order 6): their damage is DERIVED per depth (a fraction of the
        // boss's DPT), not a flat constant scaled by the trash-mob depth curve, so the generic telegraph
        // audit's depth scaling does not apply. Boss verbs get their own single-hit-cap rule instead —
        // R-BOSS-VERB-CAP (bossVerbCapResults), which checks each derived verb against the 35%/25% caps.
        return Collections.unmodifiableList(registry);
    }

    /** The registered telegraph audit rows — BalanceReport's TELEGRAPH table iterates exactly this list. */
    public static List<TelegraphAttackSpec> telegraphAttacks() {
        return TELEGRAPH_ATTACKS;
    }

    // =====================================================================================
    // R-HEAL — the priced heal/armour pickups (data).
    // =====================================================================================

    /** One priced heal/armour pickup: value restored + which survival-turns band it must land in. */
    public static final class HealPickupSpec {
        public final String  displayName;
        public final float   maxFraction;
        public final int     restoredValue;
        public final boolean isLargePickup;

        HealPickupSpec(String displayName, float maxFraction, int restoredValue, boolean isLargePickup) {
            this.displayName   = displayName;
            this.maxFraction   = maxFraction;
            this.restoredValue = restoredValue;
            this.isLargePickup = isLargePickup;
        }
    }

    private static final List<HealPickupSpec> HEAL_PICKUPS = buildHealPickupRegistry();

    private static List<HealPickupSpec> buildHealPickupRegistry() {
        List<HealPickupSpec> registry = new ArrayList<>();
        // R10 (balance-overhaul order 1): heals are FRACTIONS of max; priced here at the depth-1 player.
        registry.add(new HealPickupSpec("Stim pack '+'",     BalanceConfig.MEDKIT_STIM_HEAL_FRACTION,
                modelStimHeal(),         false));
        registry.add(new HealPickupSpec("Field medkit 'H'",  BalanceConfig.MEDKIT_FULL_HEAL_FRACTION,
                modelFullMedkitHeal(),   true));
        registry.add(new HealPickupSpec("Armour shard 'a'",  BalanceConfig.ARMOUR_SHARD_FRACTION,
                modelArmourShardValue(), false));
        registry.add(new HealPickupSpec("Security vest 'A'", BalanceConfig.ARMOUR_VEST_FRACTION,
                modelArmourVestValue(),  true));
        return Collections.unmodifiableList(registry);
    }

    /** Stim pack at the depth-1 player: MEDKIT_STIM_HEAL_FRACTION of PLAYER_MAX_HEALTH (R10). */
    public static int modelStimHeal() {
        return GameMath.fractionOfMaximum(BalanceConfig.PLAYER_MAX_HEALTH, BalanceConfig.MEDKIT_STIM_HEAL_FRACTION);
    }

    /** Field medkit at the depth-1 player: MEDKIT_FULL_HEAL_FRACTION of PLAYER_MAX_HEALTH (R10). */
    public static int modelFullMedkitHeal() {
        return GameMath.fractionOfMaximum(BalanceConfig.PLAYER_MAX_HEALTH, BalanceConfig.MEDKIT_FULL_HEAL_FRACTION);
    }

    /** Armour shard at the depth-1 player: ARMOUR_SHARD_FRACTION of PLAYER_MAX_ARMOR (R10). */
    public static int modelArmourShardValue() {
        return GameMath.fractionOfMaximum(BalanceConfig.PLAYER_MAX_ARMOR, BalanceConfig.ARMOUR_SHARD_FRACTION);
    }

    /** Security vest at the depth-1 player: ARMOUR_VEST_FRACTION of PLAYER_MAX_ARMOR (R10). */
    public static int modelArmourVestValue() {
        return GameMath.fractionOfMaximum(BalanceConfig.PLAYER_MAX_ARMOR, BalanceConfig.ARMOUR_VEST_FRACTION);
    }

    /** Depth-1 heal SUPPLY the model floor hands out (expected medkits + armour pickups, R10 values). */
    public static float modelFloorHealSupply() {
        float averageMedkitHeal  = (modelStimHeal() + modelFullMedkitHeal()) / 2f;
        float averageArmourValue = (modelArmourShardValue() + modelArmourVestValue()) / 2f;
        return GameMath.healSupplyPerFloor(
                BalanceConfig.MODEL_FLOOR_EXPECTED_MEDKITS, averageMedkitHeal,
                BalanceConfig.MODEL_FLOOR_EXPECTED_ARMOUR_PICKUPS, averageArmourValue);
    }

    /** The registered heal pickups — BalanceReport's HEAL PRICING rows iterate exactly this list. */
    public static List<HealPickupSpec> healPickups() {
        return HEAL_PICKUPS;
    }

    // Classification of every CONSUMABLE ItemType for the coverage rule: it is either a priced
    // heal (mapped to a HealPickupSpec) or an explicitly-declared utility consumable.
    private static final Map<ItemType, String> CONSUMABLE_CLASSIFICATIONS = buildConsumableClassifications();

    private static Map<ItemType, String> buildConsumableClassifications() {
        Map<ItemType, String> classifications = new EnumMap<>(ItemType.class);
        classifications.put(ItemType.MEDKIT_SMALL, "Stim pack '+'");
        classifications.put(ItemType.MEDKIT_LARGE, "Field medkit 'H'");
        classifications.put(ItemType.STIMPACK,     "UTILITY (temporary buff, not an HP/armour resource)");
        return Collections.unmodifiableMap(classifications);
    }

    // =====================================================================================
    // MODEL-FLOOR helpers shared by R-HEAL and R-SCARCITY (same math as the living tables).
    // =====================================================================================

    /** DEMAND: sum of every model-floor enemy's eHP (enemies carry no dodge/reduction — eHP == raw HP). */
    public static float modelFloorDemand() {
        return    BalanceConfig.MODEL_FLOOR_GORE_BITER_COUNT
                        * GameMath.effectiveHitPoints(BalanceConfig.GORE_BITER_MAX_HEALTH, 0f, 0f, 0f, 0f)
                + BalanceConfig.MODEL_FLOOR_EYE_TYRANT_COUNT
                        * GameMath.effectiveHitPoints(BalanceConfig.EYE_TYRANT_MAX_HEALTH, 0f, 0f, 0f, 0f)
                + BalanceConfig.MODEL_FLOOR_SHELL_BRUTE_COUNT
                        * GameMath.effectiveHitPoints(BalanceConfig.SHELL_BRUTE_MAX_HEALTH, 0f, 0f, 0f, 0f)
                + BalanceConfig.MODEL_FLOOR_PLAGUE_HULK_COUNT
                        * GameMath.effectiveHitPoints(BalanceConfig.PLAGUE_HULK_MAX_HEALTH, 0f, 0f, 0f, 0f);
    }

    /** Model-floor enemy count. */
    public static int modelFloorEnemyCount() {
        return BalanceConfig.MODEL_FLOOR_GORE_BITER_COUNT + BalanceConfig.MODEL_FLOOR_EYE_TYRANT_COUNT
                + BalanceConfig.MODEL_FLOOR_SHELL_BRUTE_COUNT + BalanceConfig.MODEL_FLOOR_PLAGUE_HULK_COUNT;
    }

    /** Total enemy damage-per-turn across the model floor (melee cadence folded in). */
    public static float modelFloorEnemyDamagePerTurn() {
        return    BalanceConfig.MODEL_FLOOR_GORE_BITER_COUNT  * (BalanceConfig.GORE_BITER_ATTACK_DAMAGE  / 1f)
                + BalanceConfig.MODEL_FLOOR_EYE_TYRANT_COUNT  * (BalanceConfig.EYE_TYRANT_ATTACK_DAMAGE  / 1f)
                + BalanceConfig.MODEL_FLOOR_SHELL_BRUTE_COUNT * (BalanceConfig.SHELL_BRUTE_ATTACK_DAMAGE / 1f)
                + BalanceConfig.MODEL_FLOOR_PLAGUE_HULK_COUNT
                        * (BalanceConfig.PLAGUE_HULK_ATTACK_DAMAGE / (float) BalanceConfig.PLAGUE_HULK_MOVE_EVERY_N_TURNS);
    }

    /** Average incoming damage per engagement turn on the model floor — the R-HEAL pricing denominator. */
    public static float modelFloorAverageIncomingDamagePerTurn() {
        float incoming = GameMath.incomingDamagePerFloor(modelFloorEnemyDamagePerTurn(),
                BalanceConfig.MODEL_FLOOR_TURNS_ENGAGED_PER_ENEMY, BalanceConfig.MODEL_FLOOR_AVOIDANCE_FACTOR);
        int floorEngagementTurns = Math.max(1,
                modelFloorEnemyCount() * BalanceConfig.MODEL_FLOOR_TURNS_ENGAGED_PER_ENEMY);
        return incoming / floorEngagementTurns;
    }

    // =====================================================================================
    // R-SCARCITY — the per-ammo-type scarcity rows (data; every AmmoType must appear — coverage).
    // =====================================================================================

    /** One scarcity row: an ammo type with the damage-per-unit of the weapon that eats it. */
    public static final class ScarcityRowSpec {
        public final AmmoType ammoType;
        public final int      boxSize;
        public final float    damagePerUnit;
        public final int      reserveCap;

        ScarcityRowSpec(AmmoType ammoType, int boxSize, float damagePerUnit, int reserveCap) {
            this.ammoType      = ammoType;
            this.boxSize       = boxSize;
            this.damagePerUnit = damagePerUnit;
            this.reserveCap    = reserveCap;
        }
    }

    private static final List<ScarcityRowSpec> SCARCITY_ROWS = buildScarcityRowRegistry();

    private static List<ScarcityRowSpec> buildScarcityRowRegistry() {
        float railgunFullChargeDamage =
                BalanceConfig.RAILGUN_DAMAGE_BY_CHARGE[BalanceConfig.RAILGUN_DAMAGE_BY_CHARGE.length - 1];
        List<ScarcityRowSpec> registry = new ArrayList<>();
        registry.add(new ScarcityRowSpec(AmmoType.BULLETS, BalanceConfig.AMMO_BOX_BULLETS,
                BalanceConfig.ASSAULT_RIFLE_DAMAGE, BalanceConfig.AMMO_RESERVE_CAP_BULLETS));
        registry.add(new ScarcityRowSpec(AmmoType.SHELLS, BalanceConfig.AMMO_BOX_SHELLS,
                BalanceConfig.SHOTGUN_DAMAGE, BalanceConfig.AMMO_RESERVE_CAP_SHELLS));
        registry.add(new ScarcityRowSpec(AmmoType.CELLS, BalanceConfig.AMMO_BOX_CELLS,
                BalanceConfig.PLASMA_RIFLE_DAMAGE, BalanceConfig.AMMO_RESERVE_CAP_CELLS));
        registry.add(new ScarcityRowSpec(AmmoType.ROCKETS, BalanceConfig.AMMO_BOX_ROCKETS,
                BalanceConfig.GRENADE_SPLASH_DAMAGE, BalanceConfig.AMMO_RESERVE_CAP_ROCKETS));
        registry.add(new ScarcityRowSpec(AmmoType.SLUGS, BalanceConfig.RAILGUN_PICKUP_SLUGS,
                railgunFullChargeDamage, BalanceConfig.RAILGUN_MAX_SLUGS));
        return Collections.unmodifiableList(registry);
    }

    /** The registered scarcity rows — BalanceReport's SCARCITY table iterates exactly this list. */
    public static List<ScarcityRowSpec> scarcityRows() {
        return SCARCITY_ROWS;
    }

    // =====================================================================================
    // R-DOT + shim integrity + R-FLAGS — structural rules (reflection over the constant files).
    // =====================================================================================

    // Every *Constants file that re-exports balance fields from BalanceConfig. Any field in one of
    // these classes that SHARES A NAME with a BalanceConfig field must re-export it byte-for-byte —
    // a re-introduced divergent literal (the root cause of the two-BURN-definitions bug) fails here.
    private static final Class<?>[] SHIM_CLASSES = {
            GameBalance.class, EffectConstants.class, EnemyConstants.class, WeaponConstants.class,
            ItemConstants.class, LevelGenConstants.class, Constants.class
    };

    // R-SINGLE-DIFFICULTY (order 8): the game has EXACTLY ONE difficulty. Any constant, nested
    // type or enum constant whose NAME belongs to the mode family fails the audit the same way a
    // live test flag does (R-FLAGS) — the tripwire that stops modes creeping back in.
    //
    // These two live HERE, not in a *Constants file, for the same reason SHIM_CLASSES and
    // FLAG_SCAN_CLASSES do: they are inputs to a STRUCTURAL rule (names the schema forbids /
    // requires), not gameplay values a designer tunes. Putting mode-family tokens into a scanned
    // constants file would also make that file trip its own rule.
    private static final String[] MODE_FAMILY_TOKENS = {
            "RECRUIT", "NIGHTMARE", "ULTRA", "DIFFICULTY", "STAT_BASE_", "EASY_MODE", "HARD_MODE"
    };

    // The ONE starting-attribute block: BalanceConfig must declare exactly this many
    // PLAYER_START_* constants — one per Attribute, no second block for a second mode.
    private static final String STARTING_ATTRIBUTE_PREFIX = "PLAYER_START_";

    // Every constant-bearing class scanned for live test/debug flags (R-FLAGS).
    private static final Class<?>[] FLAG_SCAN_CLASSES = {
            BalanceConfig.class, GameBalance.class, Constants.class, WeaponConstants.class,
            EnemyConstants.class, ItemConstants.class, LevelGenConstants.class, EffectConstants.class,
            RouteMapConstants.class, IntentConstants.class, TilesetConstants.class,
            ProgressionConstants.class, HudConstants.class, RenderConstants.class, TouchConstants.class
    };

    // Classes swept for mode-family naming (R-SINGLE-DIFFICULTY): every constant file plus the
    // player-progression types a mode system would have to touch to exist at all — the starting
    // stat carrier and the attribute enum it seeds.
    private static final Class<?>[] MODE_SCAN_CLASSES = buildModeScanClasses();

    private static Class<?>[] buildModeScanClasses() {
        Class<?>[] progressionClasses = { PlayerStats.class, Attribute.class, GameMath.class };
        Class<?>[] scanned = new Class<?>[FLAG_SCAN_CLASSES.length + progressionClasses.length];
        System.arraycopy(FLAG_SCAN_CLASSES, 0, scanned, 0, FLAG_SCAN_CLASSES.length);
        System.arraycopy(progressionClasses, 0, scanned, FLAG_SCAN_CLASSES.length, progressionClasses.length);
        return scanned;
    }

    // =====================================================================================
    // EVALUATION — every registered rule, evaluated against the CURRENT BalanceConfig.
    // =====================================================================================

    /** Evaluates every registered rule. The audit test fails the build on any isViolation() result. */
    public static List<RuleResult> evaluate() {
        List<RuleResult> results = new ArrayList<>();
        results.addAll(weaponRoleResults());
        results.addAll(enemyThreatPointResults());
        results.addAll(enemyHitResults());
        results.addAll(healPricingResults());
        results.addAll(telegraphResults());
        results.addAll(heavyResults());
        results.addAll(hazardResults());
        results.addAll(dotUniquenessResults());
        results.addAll(flagResults());
        results.addAll(coverageResults());
        results.addAll(abilityBudgetResults());
        results.addAll(abilityRollSweepResults());
        results.addAll(creditResults());
        results.addAll(xpPaceResults());
        results.addAll(perkResults());
        results.addAll(keystoneResults());
        results.addAll(buildCarryResults());
        results.addAll(regionResults());
        results.addAll(bossGateResults());
        results.addAll(bossFairResults());
        results.addAll(bossVerbCapResults());
        results.addAll(bossRewardResults());
        results.addAll(bossAmmoResults());
        results.addAll(routePricedResults());
        results.addAll(riskPremiumResults());
        results.addAll(calmCostResults());
        results.addAll(mysteryExpectedValueResults());
        results.addAll(derivedPipResults());
        results.addAll(honestSafeResults());
        results.addAll(trajectoryResults());
        results.addAll(routeGuaranteeResults());
        results.addAll(singleDifficultyResults());
        results.addAll(ladderResults());
        results.addAll(ladderAffordResults());
        results.addAll(supplyPlannerResults());
        results.addAll(ammoBankingResults());
        results.addAll(supplySweepResults());
        results.addAll(eliteRewardResults());
        results.addAll(densitySweepResults());
        results.addAll(hazardPlacementResults());
        return results;
    }

    /**
     * R-ROLE (balance-overhaul order 3) — every weapon is the best answer somewhere and never everywhere,
     * evaluated on WeaponRoleModel's reference scenarios at every ROLE_SCENARIO_DEPTHS depth:
     * <ul>
     *   <li>R-ROLE-1 NICHE: every ranged class but BALLISTIC is best (fewest turns, ammo-feasible) in at
     *       least one scenario, by >= ROLE_NICHE_MARGIN over the best BALLISTIC weapon there. Melee
     *       (BLADE / BLUNT) is exempt: no ammo, adjacent only, the fallback (not modelled).</li>
     *   <li>R-ROLE-2 GENERALIST: BALLISTIC within ROLE_GENERALIST_BOUND of the best in S1, and never best
     *       in S3 or S4.</li>
     *   <li>R-ROLE-3 NO DOMINANCE: no class best in more than ROLE_MAX_SCENARIOS_BEST scenarios.</li>
     *   <li>R-ROLE-4 RISK PAYS: every SPREAD weapon's sustained per-turn damage at 1-2 tiles is >=
     *       ROLE_RISK_PAYS_RATIO x the Assault Rifle's at 3 tiles (equal level and rarity, vs FLESH).</li>
     *   <li>R-ROLE-5 AMMO FEASIBLE: every class clears its DECLARED niche (ROLE_DECLARED_NICHE_BY_CLASS) with <= one average
     *       COMBAT floor's planned supply of its ammo type.</li>
     * </ul>
     */
    public static List<RuleResult> weaponRoleResults() {
        List<RuleResult> results = new ArrayList<>();
        List<WeaponRoleModel.RoleScenario> scenarios = WeaponRoleModel.scenarios();
        List<ge.tbegvadze.toon3d.entity.DamageClass> classes = WeaponRoleModel.rangedClasses();
        ge.tbegvadze.toon3d.entity.DamageClass ballistic = ge.tbegvadze.toon3d.entity.DamageClass.BALLISTIC;
        for (int depth : BalanceConfig.ROLE_SCENARIO_DEPTHS) {
            WeaponRoleModel.Table table = WeaponRoleModel.table(depth);
            String at = " d" + depth;
            // R-ROLE-1 + R-ROLE-3 + R-ROLE-5, per class.
            for (ge.tbegvadze.toon3d.entity.DamageClass damageClass : classes) {
                int bestCount = 0;
                float bestMargin = 0f;
                String nicheScenario = "none";
                for (int index = 0; index < scenarios.size(); index++) {
                    float classBest = table.classBest(damageClass, index);
                    if (!table.isClassBest(damageClass, index)) continue;
                    bestCount++;
                    float ballisticBest = table.classBest(ballistic, index);
                    float margin = roleMargin(ballisticBest, classBest, scenarios.get(index).bruiserCharge);
                    if (margin > bestMargin) {
                        bestMargin = margin;
                        nicheScenario = scenarios.get(index).id;
                    }
                }
                if (damageClass != ballistic) {
                    results.add(new RuleResult(RuleKind.WEAPON_ROLE, "R-ROLE-1 " + damageClass + " niche" + at,
                            bestMargin, BalanceConfig.ROLE_NICHE_MARGIN, Float.MAX_VALUE,
                            bestMargin >= BalanceConfig.ROLE_NICHE_MARGIN,
                            "best in " + nicheScenario + "; margin = best BALLISTIC / class best"));
                }
                results.add(new RuleResult(RuleKind.WEAPON_ROLE, "R-ROLE-3 " + damageClass + " scenarios best" + at,
                        bestCount, 0, BalanceConfig.ROLE_MAX_SCENARIOS_BEST,
                        bestCount <= BalanceConfig.ROLE_MAX_SCENARIOS_BEST, "of " + scenarios.size()));
                String declaredNiche = BalanceConfig.ROLE_DECLARED_NICHE_BY_CLASS[damageClass.ordinal()];
                if (declaredNiche != null) {
                    results.add(roleAmmoResult(table, damageClass, scenarioIndex(declaredNiche), at));
                } else {
                    results.add(new RuleResult(RuleKind.WEAPON_ROLE, "R-ROLE-5 " + damageClass + " ammo feasible" + at,
                            0f, 0f, 0f, false, "ranged class has no declared niche (ROLE_DECLARED_NICHE_BY_CLASS)"));
                }
            }
            // A6: the Incinerator clears S2 (three on-curve chaff) in at most ROLE_INCINERATOR_S2_MAX_SPRAYS.
            int s2 = scenarioIndex("S2");
            List<WeaponRoleModel.RoleWeapon> roleWeapons = WeaponRoleModel.weapons();
            for (int weaponIndex = 0; weaponIndex < roleWeapons.size(); weaponIndex++) {
                if (roleWeapons.get(weaponIndex).itemType != ItemType.WEAPON_INCINERATOR) continue;
                WeaponRoleModel.Cell cell = table.cells[weaponIndex][s2];
                results.add(new RuleResult(RuleKind.WEAPON_ROLE, "A6 Incinerator sprays to clear S2" + at,
                        cell.actions, 1, BalanceConfig.ROLE_INCINERATOR_S2_MAX_SPRAYS,
                        cell.cleared && cell.actions <= BalanceConfig.ROLE_INCINERATOR_S2_MAX_SPRAYS,
                        String.format("%.2f turns", cell.score)));
            }
            // R-ROLE-2: S1 bound, never best in S3 / S4.
            int s1 = scenarioIndex("S1");
            float generalistRatio = table.classBest(ballistic, s1) / table.overallBest(s1);
            results.add(new RuleResult(RuleKind.WEAPON_ROLE, "R-ROLE-2 BALLISTIC vs best in S1" + at,
                    generalistRatio, 1f, BalanceConfig.ROLE_GENERALIST_BOUND,
                    generalistRatio <= BalanceConfig.ROLE_GENERALIST_BOUND, "best BALLISTIC / best overall"));
            for (String armouredId : new String[]{"S3", "S4"}) {
                int index = scenarioIndex(armouredId);
                float lead = table.classBest(ballistic, index) / table.bestExcluding(ballistic, index);
                results.add(new RuleResult(RuleKind.WEAPON_ROLE, "R-ROLE-2 BALLISTIC not best in " + armouredId + at,
                        lead, 1f, Float.MAX_VALUE, lead > 1f,
                        "best BALLISTIC / best other class (must exceed 1)"));
            }
        }
        // R-ROLE-4 (depth-free: equal level and rarity, vs FLESH).
        WeaponRoleModel.RoleWeapon generalist = null;
        for (WeaponRoleModel.RoleWeapon weapon : WeaponRoleModel.weapons()) {
            if (weapon.itemType == ItemType.WEAPON_ASSAULT_RIFLE) generalist = weapon;
        }
        float generalistPerTurn = GameMath.roleScenarioSustainedDamagePerTurn(generalist.model,
                BalanceConfig.ROLE_RISK_GENERALIST_TILES, BalanceConfig.ROLE_RISK_GENERALIST_TILES);
        for (WeaponRoleModel.RoleWeapon weapon : WeaponRoleModel.weapons()) {
            if (weapon.damageClass != ge.tbegvadze.toon3d.entity.DamageClass.SPREAD) continue;
            float perTurn = GameMath.roleScenarioSustainedDamagePerTurn(weapon.model,
                    BalanceConfig.ROLE_RISK_SPREAD_MIN_TILES, BalanceConfig.ROLE_RISK_SPREAD_MAX_TILES);
            float ratio = perTurn / generalistPerTurn;
            results.add(new RuleResult(RuleKind.WEAPON_ROLE, "R-ROLE-4 " + weapon.displayName + " risk pays",
                    ratio, BalanceConfig.ROLE_RISK_PAYS_RATIO, Float.MAX_VALUE,
                    ratio >= BalanceConfig.ROLE_RISK_PAYS_RATIO,
                    String.format("%.1f/turn at 1-2 tiles vs Assault Rifle %.1f/turn at 3", perTurn, generalistPerTurn)));
        }
        return results;
    }

    /**
     * R-ROLE-1's margin: how many times longer the best BALLISTIC takes. S8 is scored in whole hits taken,
     * where 0 is common, so its ratio is taken on (hits + 1) — 0 hits vs 1 hit reads 2.0.
     */
    private static float roleMargin(float ballisticBest, float classBest, boolean hitsTakenScenario) {
        if (Float.isInfinite(ballisticBest)) return BalanceConfig.ROLE_SCENARIO_TURN_CAP;
        if (hitsTakenScenario) return (ballisticBest + 1f) / (classBest + 1f);
        return classBest <= 0f ? 0f : ballisticBest / classBest;
    }

    /**
     * R-ROLE-5 for one class: ammo its fastest ammo-feasible weapon (else its fastest) spends in the scenario
     * vs one average COMBAT floor's planned supply of that ammo type.
     */
    private static RuleResult roleAmmoResult(WeaponRoleModel.Table table, ge.tbegvadze.toon3d.entity.DamageClass damageClass,
                                             int scenarioIndex, String at) {
        List<WeaponRoleModel.RoleWeapon> weapons = WeaponRoleModel.weapons();
        int chosen = -1;
        for (int weaponIndex = 0; weaponIndex < weapons.size(); weaponIndex++) {
            if (weapons.get(weaponIndex).damageClass != damageClass) continue;
            WeaponRoleModel.Cell cell = table.cells[weaponIndex][scenarioIndex];
            if (!cell.cleared) continue;
            if (chosen < 0) {
                chosen = weaponIndex;
                continue;
            }
            WeaponRoleModel.Cell current = table.cells[chosen][scenarioIndex];
            // Prefer a weapon that clears on one floor's supply; among equals, the faster one.
            boolean better = cell.ammoFeasible != current.ammoFeasible
                    ? cell.ammoFeasible : cell.score < current.score;
            if (better) chosen = weaponIndex;
        }
        WeaponRoleModel.RoleScenario scenario = WeaponRoleModel.scenarios().get(scenarioIndex);
        if (chosen < 0) {
            return new RuleResult(RuleKind.WEAPON_ROLE, "R-ROLE-5 " + damageClass + " ammo feasible" + at,
                    0f, 0f, 0f, false, "no weapon of the class clears " + scenario.id);
        }
        WeaponRoleModel.RoleWeapon weapon = weapons.get(chosen);
        WeaponRoleModel.Cell cell = table.cells[chosen][scenarioIndex];
        float supply = table.supplyPerFloor.getOrDefault(weapon.ammoType, 0f);
        return new RuleResult(RuleKind.WEAPON_ROLE, "R-ROLE-5 " + damageClass + " ammo feasible" + at,
                cell.ammoSpent, 0f, supply, cell.ammoSpent <= supply,
                weapon.displayName + " in " + scenario.id + " (" + weapon.ammoType + " per floor "
                        + String.format("%.1f", supply) + ")");
    }

    private static int scenarioIndex(String id) {
        List<WeaponRoleModel.RoleScenario> scenarios = WeaponRoleModel.scenarios();
        for (int index = 0; index < scenarios.size(); index++) {
            if (scenarios.get(index).id.equals(id)) return index;
        }
        throw new IllegalStateException("No R-ROLE scenario " + id);
    }

    /** R-ENEMY: threatPoints in the role TP band for every non-boss archetype. */
    public static List<RuleResult> enemyThreatPointResults() {
        List<RuleResult> results = new ArrayList<>();
        for (EnemyType enemyType : EnemyType.values()) {
            if (enemyType.role() == EnemyRole.BOSS) continue; // bosses follow the SECTION 14 ruleset
            float[] band = ENEMY_THREAT_POINT_BANDS.get(enemyType.role());
            if (band == null) continue; // reported by coverageResults()
            float threatPoints = enemyType.baseThreatPoints();
            boolean inBand = threatPoints >= band[0] && threatPoints <= band[1];
            results.add(new RuleResult(RuleKind.ENEMY_THREAT_POINTS, enemyType.displayName(),
                    threatPoints, band[0], band[1], inBand, "role " + enemyType.role()));
        }
        return results;
    }

    /**
     * R-ENEMY part 2 (balance-overhaul order 1, R8): every non-boss archetype's depth-1 fight length lands in
     * its role's hit bands — hits the on-curve reference weapon needs to kill it (eHP / the R8 reference hit,
     * rounded up) and ordinary hits of it the start player survives (REFERENCE_PLAYER_EHP / its hit, up).
     */
    public static List<RuleResult> enemyHitResults() {
        List<RuleResult> results = new ArrayList<>();
        for (EnemyType enemyType : EnemyType.values()) {
            float[] band = ENEMY_HIT_BANDS.get(enemyType.role());
            if (band == null) continue; // BOSS: SECTION 14 ruleset; unknown roles are a COVERAGE violation
            int hitsToKill = enemyHitsToKill(enemyType);
            int hitsToDie = enemyHitsToDie(enemyType);
            results.add(new RuleResult(RuleKind.ENEMY_HITS, enemyType.displayName() + " hits to kill",
                    hitsToKill, band[0], band[1], hitsToKill >= band[0] && hitsToKill <= band[1],
                    "role " + enemyType.role() + String.format("; eHP %.0f / %.1f per reference hit",
                            enemyType.neutralEffectiveHitPoints(), GameMath.ladderReferenceHitDamage())));
            results.add(new RuleResult(RuleKind.ENEMY_HITS, enemyType.displayName() + " hits to die",
                    hitsToDie, band[2], band[3], hitsToDie >= band[2] && hitsToDie <= band[3],
                    "role " + enemyType.role() + "; " + BalanceConfig.REFERENCE_PLAYER_EHP + " eHP / "
                            + enemyType.attackDamage() + " per hit"));
        }
        return results;
    }

    /** Depth-1 hits the R8 reference weapon needs to kill the archetype (shared by the audit and the report). */
    public static int enemyHitsToKill(EnemyType enemyType) {
        // Matchup-NEUTRAL eHP (balance-overhaul order 3 re-statement): R8 boxes the fight with an answer
        // that is not resisted; the generalist's matchup cost is priced in TP, not in the box.
        return GameMath.turnsToKill(enemyType.neutralEffectiveHitPoints(), GameMath.ladderReferenceHitDamage());
    }

    /** Depth-1 ordinary hits of the archetype the 205-eHP start player survives (shared by the audit and the report). */
    public static int enemyHitsToDie(EnemyType enemyType) {
        return GameMath.turnsToKill(BalanceConfig.REFERENCE_PLAYER_EHP, enemyType.attackDamage());
    }

    /**
     * R-HEAL (re-stated by balance-overhaul order 1): every heal/armour pickup restores a FRACTION of the
     * max it refills (R10), and that fraction lands in its size band — small pickups top up a slice, large
     * ones buy most of a fight but never a full reset. Survival turns bought on the model floor ride along
     * in the detail (information; they fell with the R8 rebase's harder hits).
     */
    public static List<RuleResult> healPricingResults() {
        float averageIncomingDamagePerTurn = modelFloorAverageIncomingDamagePerTurn();
        List<RuleResult> results = new ArrayList<>();
        for (HealPickupSpec pickup : HEAL_PICKUPS) {
            float bandMinimum = pickup.isLargePickup
                    ? BalanceConfig.HEAL_LARGE_MAX_FRACTION_MIN : BalanceConfig.HEAL_SMALL_MAX_FRACTION_MIN;
            float bandMaximum = pickup.isLargePickup
                    ? BalanceConfig.HEAL_LARGE_MAX_FRACTION_MAX : BalanceConfig.HEAL_SMALL_MAX_FRACTION_MAX;
            boolean inBand = pickup.maxFraction >= bandMinimum && pickup.maxFraction <= bandMaximum;
            float survivalTurns = GameMath.survivalTurnsBought(pickup.restoredValue, averageIncomingDamagePerTurn);
            results.add(new RuleResult(RuleKind.HEAL_PRICING, pickup.displayName, pickup.maxFraction,
                    bandMinimum, bandMaximum, inBand,
                    (pickup.isLargePickup ? "large" : "small") + " pickup; " + pickup.restoredValue
                            + " at the depth-1 player = " + String.format("%.1f", survivalTurns)
                            + " survival turns at " + String.format("%.1f", averageIncomingDamagePerTurn)
                            + " avg incoming dmg/turn"));
        }
        return results;
    }

    /**
     * R-TELEGRAPH: an UN-readable attack may never exceed the un-telegraphed cap
     * (25% of reference eHP); NO boss attack may exceed the hard cap (35%), telegraphed or not.
     */
    public static List<RuleResult> telegraphResults() {
        float untelegraphedCap = BalanceConfig.REFERENCE_PLAYER_EHP
                * BalanceConfig.TELEGRAPH_MAX_UNTELEGRAPHED_HIT_FRACTION;
        float bossHardCap = BalanceConfig.REFERENCE_PLAYER_EHP * BalanceConfig.BOSS_HARD_SINGLE_HIT_FRACTION;
        List<RuleResult> results = new ArrayList<>();
        for (TelegraphAttackSpec attack : TELEGRAPH_ATTACKS) {
            float effectiveCap = attack.isReadable()
                    ? (attack.isBossAttack ? bossHardCap : Float.MAX_VALUE)
                    : untelegraphedCap;
            boolean satisfied = attack.baseHit <= effectiveCap;
            results.add(new RuleResult(RuleKind.TELEGRAPH, attack.attackName, attack.baseHit,
                    0f, effectiveCap == Float.MAX_VALUE ? Float.POSITIVE_INFINITY : effectiveCap, satisfied,
                    "readable=" + attack.readableKind + (attack.isBossAttack ? " (boss)" : "")));
        }
        return results;
    }

    /**
     * R-HEAVY (balance-overhaul order 4, H2/H3/H4): for every {@link HeavyAttack} at every depth 1..25 —
     * (a) the unguarded, unarmoured hit is [HEAVY_DAMAGE_FRACTION_MIN, MAX] of the on-curve player's max HP;
     * (b) it is committed at least one full player turn before it resolves; (c) a GUARDING player (front arc,
     * GUARD_FRONT_HEAVY) takes at most HEAVY_GUARDED_MAX_FRACTION of max HP — the cornered-player cap of H4.
     * Geometric avoidability (H4's "a safe tile exists") is asserted by the danger-tile checkpoint (CP2).
     */
    public static List<RuleResult> heavyResults() {
        List<RuleResult> results = new ArrayList<>();
        for (HeavyAttack heavy : HeavyAttack.values()) {
            for (int depth = 1; depth <= BalanceConfig.RUN_FINAL_DEPTH; depth++) {
                float expectedMaxHealth = GameMath.expectedPlayerAtDepth(depth).maxHealth;
                int damage = GameMath.heavyDamageAtDepth(heavy.damageFraction(), expectedMaxHealth,
                        BalanceConfig.HEAVY_DAMAGE_SCALE);
                float fraction = damage / expectedMaxHealth;
                String subject = heavy.displayName() + " d" + depth;
                results.add(new RuleResult(RuleKind.HEAVY, subject + " damage", fraction,
                        BalanceConfig.HEAVY_DAMAGE_FRACTION_MIN, BalanceConfig.HEAVY_DAMAGE_FRACTION_MAX,
                        fraction >= BalanceConfig.HEAVY_DAMAGE_FRACTION_MIN
                                && fraction <= BalanceConfig.HEAVY_DAMAGE_FRACTION_MAX,
                        damage + " of " + Math.round(expectedMaxHealth) + " on-curve max HP"));
                results.add(new RuleResult(RuleKind.HEAVY, subject + " commit", heavy.minimumCommitTurns(),
                        1f, Float.POSITIVE_INFINITY, heavy.minimumCommitTurns() >= 1,
                        "committed turns before resolution"));
                float guardedFraction = fraction * BalanceConfig.GUARD_FRONT_HEAVY;
                results.add(new RuleResult(RuleKind.HEAVY, subject + " guarded", guardedFraction,
                        0f, BalanceConfig.HEAVY_GUARDED_MAX_FRACTION,
                        guardedFraction <= BalanceConfig.HEAVY_GUARDED_MAX_FRACTION,
                        "unguarded " + String.format("%.0f%%", fraction * 100f) + " x guard "
                                + BalanceConfig.GUARD_FRONT_HEAVY));
            }
        }
        results.addAll(heavyAvoidabilityResults());
        return results;
    }

    /** The reference SOLDIER's max HP at a depth, unrounded (the barrel's yardstick, H13). */
    public static float barrelReferenceSoldierMaxHealth(int depth) {
        return GameMath.enemyHealthAtDepth(BalanceConfig.HAZARD_BARREL_REFERENCE_SOLDIER_BASE_HEALTH, depth);
    }

    /** What a barrel's ring / centre blast deals an enemy at a depth (the same call ExplosiveBarrelManager makes). */
    public static int barrelEnemyDamage(float fraction, int depth) {
        return GameMath.barrelBlastDamage(fraction, barrelReferenceSoldierMaxHealth(depth));
    }

    /** An enemy archetype's max HP as the game spawns it at a depth. */
    public static int scaledEnemyMaxHealth(EnemyType type, int depth) {
        return Math.max(1, Math.round(GameMath.enemyHealthAtDepth(type.maxHealth(), depth)));
    }

    /**
     * R-HAZARD (balance-overhaul order 4, H13/H14), depths 1..25:
     * (a) the reference soldier named by the constants IS the R-LADDER SOLDIER reference archetype;
     * (b) a barrel's RING blast kills every CHAFF archetype outright (the pack-clearing promise of A6);
     * (c) the ring never one-shots the reference soldier (a barrel is a turn-turner, not a kill button);
     * (d) the player's own centre / ring damage never exceeds the heavy band's floor (HEAVY_DAMAGE_FRACTION_MIN)
     *     of the on-curve max HP — a hazard must not out-hit a telegraphed heavy.
     * Placement (H14) is audited from the generator sweep by {@link #hazardPlacementResults()}.
     */
    public static List<RuleResult> hazardResults() {
        List<RuleResult> results = new ArrayList<>();
        EnemyType soldier = ladderReferenceArchetype(EnemyRole.SOLDIER);
        results.add(new RuleResult(RuleKind.HAZARD, "reference soldier base HP is the ladder SOLDIER",
                BalanceConfig.HAZARD_BARREL_REFERENCE_SOLDIER_BASE_HEALTH, soldier.maxHealth(), soldier.maxHealth(),
                BalanceConfig.HAZARD_BARREL_REFERENCE_SOLDIER_BASE_HEALTH == soldier.maxHealth(),
                soldier.name()));
        for (int depth = 1; depth <= BalanceConfig.RUN_FINAL_DEPTH; depth++) {
            int ring = barrelEnemyDamage(BalanceConfig.HAZARD_BARREL_RING_FRACTION, depth);
            int worstChaffHealth = 0;
            String worstChaff = "none";
            for (EnemyType type : EnemyType.values()) {
                if (type.role() != EnemyRole.CHAFF) continue;
                int health = scaledEnemyMaxHealth(type, depth);
                if (health > worstChaffHealth) {
                    worstChaffHealth = health;
                    worstChaff = type.name();
                }
            }
            results.add(new RuleResult(RuleKind.HAZARD, "d" + depth + " ring kills every chaff", ring,
                    worstChaffHealth, Float.POSITIVE_INFINITY, ring >= worstChaffHealth,
                    "ring " + ring + " vs " + worstChaff + " " + worstChaffHealth + " HP"));
            int soldierHealth = scaledEnemyMaxHealth(soldier, depth);
            results.add(new RuleResult(RuleKind.HAZARD, "d" + depth + " ring spares the soldier", ring,
                    0f, soldierHealth - 1f, ring < soldierHealth, "ring " + ring + " vs soldier " + soldierHealth + " HP"));
            int playerMaxHealth = Math.round(GameMath.expectedPlayerAtDepth(depth).maxHealth);
            int playerCentre = GameMath.barrelBlastDamage(BalanceConfig.HAZARD_BARREL_PLAYER_CENTRE_FRACTION, playerMaxHealth);
            float playerCentreFraction = playerCentre / (float) playerMaxHealth;
            results.add(new RuleResult(RuleKind.HAZARD, "d" + depth + " player centre blast", playerCentreFraction,
                    0f, BalanceConfig.HEAVY_DAMAGE_FRACTION_MIN,
                    playerCentreFraction <= BalanceConfig.HEAVY_DAMAGE_FRACTION_MIN,
                    playerCentre + " of " + playerMaxHealth + " max HP"));
        }
        return results;
    }

    /**
     * R-HAZARD placement (H14), from the generator sweep: on every COMBAT / ELITE floor at least
     * ceil(HAZARD_GROUP_ROOM_SHARE x eligible) of the group rooms hold barrels (worst floor per cell), and no
     * other node type ever gets one.
     */
    public static synchronized List<RuleResult> hazardPlacementResults() {
        List<RuleResult> results = new ArrayList<>();
        Map<String, List<ge.tbegvadze.toon3d.level.FloorContentReport>> cells = new java.util.LinkedHashMap<>();
        for (ge.tbegvadze.toon3d.level.FloorContentReport report : supplySweepReports()) {
            cells.computeIfAbsent(report.generatorName + " " + report.spec.type() + " d" + report.depth,
                    key -> new ArrayList<>()).add(report);
        }
        for (Map.Entry<String, List<ge.tbegvadze.toon3d.level.FloorContentReport>> cell : cells.entrySet()) {
            RouteNodeType type = cell.getValue().get(0).spec.type();
            boolean gets = type == RouteNodeType.COMBAT || type == RouteNodeType.ELITE;
            int worstShortfall = 0;
            int strayBarrels = 0;
            for (ge.tbegvadze.toon3d.level.FloorContentReport report : cell.getValue()) {
                if (!gets) {
                    strayBarrels += report.barrelCount();
                    continue;
                }
                int needed = GameMath.groupBarrelRoomCount(report.barrelEligibleRooms(),
                        BalanceConfig.HAZARD_GROUP_ROOM_SHARE);
                worstShortfall = Math.max(worstShortfall, needed - report.barrelRooms());
            }
            if (gets) {
                results.add(new RuleResult(RuleKind.HAZARD, cell.getKey() + " barrel rooms (H14)", worstShortfall,
                        0f, 0f, worstShortfall <= 0, "worst floor's shortfall vs the 40% rule"));
            } else {
                results.add(new RuleResult(RuleKind.HAZARD, cell.getKey() + " no barrels (H14)", strayBarrels,
                        0f, 0f, strayBarrels == 0, null));
            }
        }
        return results;
    }

    /**
     * H4 geometry per heavy: on an open board (nothing blocked) the shape built by the same GameMath
     * function the manager uses at commit has, for every danger tile, a one-step sidestep out of danger.
     * LANE is built along step (1, 0) at its real reach; SQUARE / CROSS at their real radii.
     */
    private static List<RuleResult> heavyAvoidabilityResults() {
        List<RuleResult> results = new ArrayList<>();
        GameMath.TileBlockedQuery openBoard = (tileColumn, tileRow) -> false;
        int capacity = EnemyConstants.HEAVY_DANGER_TILE_CAPACITY;
        int[] columns = new int[capacity];
        int[] rows    = new int[capacity];
        for (HeavyAttack heavy : HeavyAttack.values()) {
            int count = heavy.dangerShape().build(0, 0, 1, 0, heavy.dangerReachTiles(), openBoard,
                    columns, rows, capacity);
            boolean holds = count > 0
                    && GameMath.everyDangerTileHasSafeNeighbour(columns, rows, count,
                            heavy.dangerShape() == HeavyAttack.DangerShape.CROSS ? 1 : 0, openBoard);
            results.add(new RuleResult(RuleKind.HEAVY, heavy.displayName() + " avoidable", holds ? 1f : 0f,
                    1f, 1f, holds, heavy.dangerShape() + " " + count + " danger tiles on an open board"));
        }
        return results;
    }

    /**
     * R-REGION (new-game-balancr order 5; re-shaped by balance-overhaul order 6, F7): the REGION DANGER DIAL
     * is a budgeted, provably-fair number, not a vibe. It checks that (1) every region's TP multiplier sits in
     * [MIN, MAX] (0.8–1.5); (2) the dial has the finite run's SHAPE — C >= B >= A and E >= D (OVERRIDES
     * "monotonic non-decreasing": the peak sits at REACTOR DEPTHS, floors 11-15, and THE BREACH may dip as the
     * build comes online); (3) the lethal regions (C/D/E) out-dial region A by a measurable margin — a lethal region is EXPLICITLY, budgeted-ly lethal; and
     * (4) the power ladder (R-LADDER L1) still holds in every region lane. Point (4) is the key fairness
     * proof: the region dial scales the BUDGET (how many bodies), not the per-enemy growth, so the on-curve
     * fight length is region-INDEPENDENT in every lane — region danger is an attrition tax of extra bodies,
     * not an unfair per-duel spike.
     */
    public static List<RuleResult> regionResults() {
        List<RuleResult> results = new ArrayList<>();
        float[] multipliers = BalanceConfig.REGION_TP_BUDGET_MULTIPLIER;
        int band = BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE;

        // 1. Each region multiplier in [MIN, MAX].
        for (int region = 0; region < multipliers.length; region++) {
            boolean inBand = multipliers[region] >= BalanceConfig.REGION_TP_BUDGET_MULTIPLIER_MIN
                    && multipliers[region] <= BalanceConfig.REGION_TP_BUDGET_MULTIPLIER_MAX;
            results.add(new RuleResult(RuleKind.REGION_DANGER, "region " + region + " TP dial",
                    multipliers[region], BalanceConfig.REGION_TP_BUDGET_MULTIPLIER_MIN,
                    BalanceConfig.REGION_TP_BUDGET_MULTIPLIER_MAX, inBand, regionDisplayName(region)));
        }

        // 2. The finite run's shape (F7): C >= B >= A and E >= D — the peak at floors 11-15, the finale on top.
        boolean shaped = multipliers.length == RouteMapConstants.FIXED_REGION_COUNT
                && multipliers[2] >= multipliers[1] && multipliers[1] >= multipliers[0]
                && multipliers[4] >= multipliers[3];
        results.add(new RuleResult(RuleKind.REGION_DANGER, "region dial shape C>=B>=A, E>=D",
                shaped ? 1f : 0f, 1f, 1f, shaped,
                "one entry per region of the finite run; the peak at REACTOR DEPTHS, the finale at least THE BREACH"));

        // 3. The lethal regions (C=2, D=3, E=4) out-dial region A by a measurable margin.
        float regionAMultiplier = multipliers.length > 0 ? multipliers[0] : 1f;
        int[] lethalRegions = {2, 3, 4};
        for (int region : lethalRegions) {
            if (region >= multipliers.length) continue;
            float threshold = regionAMultiplier + BalanceConfig.REGION_TP_LETHAL_MARGIN;
            boolean measurablyHarder = multipliers[region] >= threshold;
            results.add(new RuleResult(RuleKind.REGION_DANGER,
                    "region " + region + " measurably harder than A", multipliers[region],
                    threshold, Float.POSITIVE_INFINITY, measurablyHarder,
                    regionDisplayName(region) + " dial " + multipliers[region] + " vs A " + regionAMultiplier));
        }

        // 4. The power ladder (R-LADDER L1) holds in every region lane: the dial scales the BUDGET (how
        //    many bodies), never the per-enemy growth, so the on-curve fight length is region-independent.
        //    One result per lane confirms the whole 1..RUN_FINAL_DEPTH sweep stays inside the L1 tolerance.
        //    (balance-overhaul order 1: replaces the retired R-DEPTH coupling check.)
        float tolerance = BalanceConfig.LADDER_ON_CURVE_TOLERANCE;
        boolean ladderFlat = true;
        for (RuleResult ladder : ladderResults()) {
            if (ladder.subject.startsWith("L1 ") && !ladder.satisfied) ladderFlat = false;
        }
        for (int region = 0; region < multipliers.length; region++) {
            results.add(new RuleResult(RuleKind.REGION_DANGER,
                    "region " + region + " lane ladder 1.." + BalanceConfig.RUN_FINAL_DEPTH, ladderFlat ? 1f : 0f,
                    1f, 1f, ladderFlat, regionDisplayName(region)
                            + " — per-fight length is region-independent and within +/-" + tolerance));
        }
        return results;
    }

    /** The route region name for a 0-based region index (A..E — the finite run, balance-overhaul order 6). */
    private static String regionDisplayName(int region) {
        switch (region) {
            case 0:  return RouteMapConstants.REGION_A_NAME;
            case 1:  return RouteMapConstants.REGION_B_NAME;
            case 2:  return RouteMapConstants.REGION_C_NAME;
            case 3:  return RouteMapConstants.REGION_D_NAME;
            default: return RouteMapConstants.REGION_E_NAME;
        }
    }

    // =====================================================================================
    // ORDER-3 depth-sweep helpers — shared "one source" numbers for R-SCARCITY-DEPTH,
    // R-HEALDRAIN-DEPTH and R-CREDITS (the same model-floor primitives the SECTION-10 table uses).
    // =====================================================================================

    /** Total ranged-ammo SUPPLY damage on the depth-1 model floor (the depth-sweep's baseline). */
    public static float modelFloorTotalRangedSupply() {
        return modelFloorPlan(1).roundedValue(ge.tbegvadze.toon3d.level.SupplyCategory.AMMO);
    }

    /** Seed the model-floor example is planned with (any fixed value: only weapon / carrier rolls use it). */
    private static final long MODEL_FLOOR_PLAN_SEED = 0x0DE1F100L;

    /** The SECTION 10 model floor's roster (the printed example the planner is shown against). */
    public static List<EnemyType> modelFloorRoster() {
        List<EnemyType> roster = new ArrayList<>();
        for (int count = 0; count < BalanceConfig.MODEL_FLOOR_GORE_BITER_COUNT; count++)  roster.add(EnemyType.GORE_BITER);
        for (int count = 0; count < BalanceConfig.MODEL_FLOOR_EYE_TYRANT_COUNT; count++)  roster.add(EnemyType.EYE_TYRANT);
        for (int count = 0; count < BalanceConfig.MODEL_FLOOR_SHELL_BRUTE_COUNT; count++) roster.add(EnemyType.SHELL_BRUTE);
        for (int count = 0; count < BalanceConfig.MODEL_FLOOR_PLAGUE_HULK_COUNT; count++) roster.add(EnemyType.PLAGUE_HULK);
        return roster;
    }

    /**
     * The supply the order-2 planner hands the model floor's roster on a COMBAT node at a depth — the
     * model floor survives only as this printed example (its old per-room / per-kill expected-box model
     * is gone with the dice it described).
     */
    public static ge.tbegvadze.toon3d.level.SupplyPlan modelFloorPlan(int depth) {
        return ge.tbegvadze.toon3d.level.SupplyPlanner.plan(new ge.tbegvadze.toon3d.level.SupplyRequest(
                depth, ge.tbegvadze.toon3d.route.NodeSupplySpecs.combat(), modelFloorRoster(),
                GameMath.expectedPlayerAtDepth(depth), null, MODEL_FLOOR_PLAN_SEED, false, 0));
    }

    /**
     * R-SCARCITY-DEPTH (order 3): the scarcity ratio S holds [0.75, 0.95] at EVERY depth 1..15, and no
     * single weapon's share exceeds the per-weapon cap at any depth. DEMAND rides the enemy-eHP curve;
     * SUPPLY rides the EXPECTED-arsenal gear curve times the per-region supply multiplier. This is the
     * whole-run generalisation of the depth-1 R-SCARCITY model-floor check.
     */
    // --- CREDIT ECONOMY inputs (order 3, part C) — deterministic "one source" income model. ---

    /** Total per-kill credit reward the model-floor roster pays out (the floor's kill-income base). */
    public static float modelFloorKillCreditReward() {
        return    BalanceConfig.MODEL_FLOOR_GORE_BITER_COUNT  * BalanceConfig.CREDIT_REWARD_GORE_BITER
                + BalanceConfig.MODEL_FLOOR_EYE_TYRANT_COUNT  * BalanceConfig.CREDIT_REWARD_EYE_TYRANT
                + BalanceConfig.MODEL_FLOOR_SHELL_BRUTE_COUNT * BalanceConfig.CREDIT_REWARD_SHELL_BRUTE
                + BalanceConfig.MODEL_FLOOR_PLAGUE_HULK_COUNT * BalanceConfig.CREDIT_REWARD_PLAGUE_HULK;
    }

    /** Expected credit-chip income on a single floor (expected chip count * weighted average chip value). */
    public static float chipIncomePerFloor() {
        // S8 (balance-overhaul order 2): a COMBAT floor's planned chips, each worth the old table's mean.
        int chips = Math.round(BalanceConfig.NODE_SUPPLY_COMBAT_CREDITS * BalanceConfig.SUPPLY_CREDIT_CHIPS_PER_FLOOR);
        return chips * (float) Math.round(ge.tbegvadze.toon3d.level.SupplyPlanner.averageCreditChipValue());
    }

    /**
     * The tier a region's one budgeted TIER UPGRADE buys (F12): the region's top weapon-drop tier, so the bundle
     * prices the step that takes the on-curve weapon to the top of the region's rarity band.
     */
    public static WeaponTier regionTierUpgradeDestination(int region) {
        int[] maximums = BalanceConfig.WEAPON_DROP_TIER_MAX_BY_REGION;
        int tier = maximums[Math.min(Math.max(0, region), maximums.length - 1)];
        return WeaponTier.values()[Math.min(tier, WeaponTier.values().length - 1)];
    }

    /** The NEEDED BUNDLE's price per floor at a depth (F12; GameMath.neededBundlePricePerFloor). */
    public static float neededBundlePricePerFloor(int depth) {
        int band   = BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE;
        int region = GameMath.regionIndexAtDepth(Math.max(1, depth), band);
        int consumablePrice = GameMath.shopPrice(BalanceConfig.SHOP_SMALL_BUY_POWER_POINTS,
                BalanceConfig.SHOP_CREDITS_PER_POWER_POINT, depth, BalanceConfig.SHOP_DEPTH_PRICE_SCALE);
        int tierPrice = GameMath.shopPrice(WeaponRoller.tierAbilityPowerPointBudget(regionTierUpgradeDestination(region)),
                BalanceConfig.SHOP_CREDITS_PER_POWER_POINT, depth, BalanceConfig.SHOP_DEPTH_PRICE_SCALE);
        return GameMath.neededBundlePricePerFloor(ladderLevelUpPrice(depth),
                BalanceConfig.CREDITS_NEEDED_CONSUMABLES_PER_FLOOR, consumablePrice, tierPrice, band);
    }

    /**
     * R-CREDITS v2 (balance-overhaul order 6, F12 — REPLACES the order-3 rule "income per region / one weapon-class
     * buy + 1.5 supplies in [0.9, 1.4]"): for every region of the finite run, the modelled on-curve credit income
     * per floor divided by the NEEDED BUNDLE's price per floor — both averaged over the region's floors — lands in
     * [CREDITS_V2_RATIO_MIN, MAX] = [1.10, 1.40]. A SURPLUS by design (owner D4: "shops are too expensive"): an
     * on-curve player can always afford the rung, the consumables and the region's tier step, with change.
     */
    public static List<RuleResult> creditResults() {
        List<RuleResult> results = new ArrayList<>();
        int band = BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE;
        int regionCount = (BalanceConfig.RUN_FINAL_DEPTH + band - 1) / band; // regions spanning 1..RUN_FINAL_DEPTH
        for (int region = 0; region < regionCount; region++) {
            int firstDepth = region * band + 1;
            float income = 0f;
            float bundle = 0f;
            for (int depth = firstDepth; depth < firstDepth + band; depth++) {
                income += combatFloorCreditIncome(depth);
                bundle += neededBundlePricePerFloor(depth);
            }
            income /= band;
            bundle /= band;
            float ratio = bundle <= 0f ? 0f : income / bundle;
            boolean inBand = ratio >= BalanceConfig.CREDITS_V2_RATIO_MIN && ratio <= BalanceConfig.CREDITS_V2_RATIO_MAX;
            results.add(new RuleResult(RuleKind.CREDITS, "region " + region + " income/needed bundle per floor", ratio,
                    BalanceConfig.CREDITS_V2_RATIO_MIN, BalanceConfig.CREDITS_V2_RATIO_MAX, inBand,
                    regionDisplayName(region) + ": income " + Math.round(income) + " / bundle " + Math.round(bundle)
                            + " per floor (tier step to " + regionTierUpgradeDestination(region) + ")"));
        }
        return results;
    }

    // =====================================================================================
    // R-XP-PACE (new-game-balancr order 4) — XP pacing is a RULE, not a hope. A floor must award
    // enough XP for ~1 level-up but not a runaway, at EVERY depth. Roster XP = XP_PER_THREAT_POINT *
    // (fill-target * floor TP budget) — because per-enemy XP is DERIVED from depth-scaled TP, the whole
    // roster's XP equals the knob times the spent budget. Divided by the geometric level requirement at
    // the expected level, the yield must land in [XP_FLOOR_YIELD_MIN, MAX].
    // =====================================================================================

    /** The XP a floor's roster is worth at a depth (order 4): XP_PER_THREAT_POINT * fill-target * budget. */
    public static float floorRosterXp(int depth) {
        float floorBudget = GameMath.floorThreatPointBudget(BalanceConfig.FLOOR_BASE_THREAT_POINT_BUDGET,
                BalanceConfig.ENEMY_HEALTH_GROWTH, BalanceConfig.ENEMY_DAMAGE_GROWTH, depth);
        return BalanceConfig.XP_PER_THREAT_POINT
                * BalanceConfig.ENCOUNTER_BUDGET_FILL_TARGET_FRACTION * floorBudget;
    }

    /** R-XP-PACE: available XP / xpRequired(expectedLevel) in [MIN, MAX] for every depth 1..RUN_FINAL_DEPTH. */
    public static List<RuleResult> xpPaceResults() {
        List<RuleResult> results = new ArrayList<>();
        for (int depth = 1; depth <= BalanceConfig.RUN_FINAL_DEPTH; depth++) {
            int expectedLevel = GameMath.expectedLevelAtDepth(BalanceConfig.EXPECTED_LEVELS_PER_DEPTH, depth);
            int requiredXp = GameMath.xpRequiredForLevelGeometric(BalanceConfig.XP_BASE_REQUIREMENT,
                    BalanceConfig.XP_CURVE_GROWTH_PER_LEVEL, expectedLevel);
            float yield = requiredXp <= 0 ? 0f : floorRosterXp(depth) / requiredXp;
            boolean inBand = yield >= BalanceConfig.XP_FLOOR_YIELD_MIN && yield <= BalanceConfig.XP_FLOOR_YIELD_MAX;
            results.add(new RuleResult(RuleKind.XP_PACE, "depth " + depth, yield,
                    BalanceConfig.XP_FLOOR_YIELD_MIN, BalanceConfig.XP_FLOOR_YIELD_MAX, inBand,
                    "expected level " + expectedLevel + ", req " + requiredXp));
        }
        return results;
    }

    // =====================================================================================
    // R-PERK / R-KEYSTONE / R-CARRY (balance-overhaul order 5) — util/BuildValuationModel
    // =====================================================================================

    /**
     * R-PERK: PK1 FELT (every rank vs the previous, at every reference depth, on the perk's niche) plus the
     * PK1 breakpoint (max rank vs none crosses a whole TTK or TTD step at some reference depth), and PK2
     * FOCUSED (non-VITALITY: max rank does not improve all eight scenarios by more than the bound;
     * VITALITY: no rank improves any scenario by more than the per-rank cap).
     */
    public static List<RuleResult> perkResults() {
        List<RuleResult> results = new ArrayList<>();
        for (Perk perk : Perk.values()) {
            BuildValuationModel.Niche niche = BuildValuationModel.nicheOf(perk);
            if (niche == null) {
                results.add(new RuleResult(RuleKind.PERK, "PK1 " + perk.name() + " niche", 0f, 1f, 1f, false,
                        "no niche registered for tag " + perk.tag));
                continue;
            }
            boolean higher = niche.metric.higherIsBetter();
            boolean crossed = false;
            String crossedAt = "never";
            for (int depth : BalanceConfig.BUILD_PERK_REFERENCE_DEPTHS) {
                float previous = BuildValuationModel.measure(niche, new BuildStats(), depth);
                for (int rank = 1; rank <= perk.maxRank; rank++) {
                    float value = BuildValuationModel.measure(niche, BuildValuationModel.statsWithPerk(perk, rank), depth);
                    float gain = GameMath.buildNicheGain(previous, value, higher);
                    results.add(new RuleResult(RuleKind.PERK, "PK1 " + perk.name() + " r" + rank + " felt d" + depth,
                            gain, BalanceConfig.PERK_FELT_MIN_GAIN, Float.MAX_VALUE,
                            gain >= BalanceConfig.PERK_FELT_MIN_GAIN,
                            String.format("%s %.3f -> %.3f", niche.label(), previous, value)));
                    previous = value;
                }
                BuildValuationModel.Niche breakpointNiche = BuildValuationModel.breakpointNicheOf(perk);
                int[] before = BuildValuationModel.integers(breakpointNiche, new BuildStats(), depth);
                int[] after = BuildValuationModel.integers(breakpointNiche, BuildValuationModel.statsWithPerk(perk, perk.maxRank), depth);
                if (!crossed && (after[0] < before[0] || after[1] > before[1])) {
                    crossed = true;
                    crossedAt = String.format("d%d TTK %d->%d TTD %d->%d", depth, before[0], after[0], before[1], after[1]);
                }
            }
            String breakpointSubject = "PK1 " + perk.name() + " breakpoint by r" + perk.maxRank
                    + (BuildValuationModel.hasBreakpointOverride(perk)
                            ? " (D4 resource: " + BuildValuationModel.breakpointNicheOf(perk).label() + ")" : "");
            results.add(new RuleResult(RuleKind.PERK, breakpointSubject, crossed ? 1f : 0f, 1f, 1f, crossed, crossedAt));
            results.addAll(perkFocusResults(perk));
        }
        return results;
    }

    /**
     * The PK1 FELT rows of R-PERK alone (every rank's niche gain, at every reference depth) — the rule that
     * REPLACED R-CARD's budget band (balance-overhaul order 5, OVERRIDE CLAUSE). The audit's re-pointed
     * card-budget test asserts these.
     */
    public static List<RuleResult> perkFeltResults() {
        List<RuleResult> felt = new ArrayList<>();
        for (RuleResult result : perkResults()) {
            if (result.subject.startsWith("PK1 ") && result.subject.contains(" felt d")) felt.add(result);
        }
        return felt;
    }

    /**
     * The PK1 BREAKPOINT rows of R-PERK alone (each perk's max rank crosses a whole TTK / TTD step, or its D4
     * resource step) — the rule R-CARD-BREAKPOINT was folded into (balance-overhaul order 5). The audit's
     * re-pointed card-breakpoint test asserts these.
     */
    public static List<RuleResult> perkBreakpointResults() {
        List<RuleResult> breakpoints = new ArrayList<>();
        for (RuleResult result : perkResults()) {
            if (result.subject.startsWith("PK1 ") && result.subject.contains(" breakpoint by r")) breakpoints.add(result);
        }
        return breakpoints;
    }

    /** PK2 for one perk at every reference depth. */
    private static List<RuleResult> perkFocusResults(Perk perk) {
        List<RuleResult> results = new ArrayList<>();
        boolean vitality = perk.tag == BuildTag.VITALITY;
        for (int depth : BalanceConfig.BUILD_PERK_REFERENCE_DEPTHS) {
            BuildValuationModel.Context context = BuildValuationModel.context(depth);
            if (vitality) {
                for (int rank = 1; rank <= perk.maxRank; rank++) {
                    float[] gains = perkScenarioGains(context, BuildValuationModel.statsWithPerk(perk, rank - 1),
                            BuildValuationModel.statsWithPerk(perk, rank));
                    int worst = indexOfMax(gains);
                    results.add(new RuleResult(RuleKind.PERK, "PK2 " + perk.name() + " r" + rank + " capped d" + depth,
                            gains[worst], 0f, BalanceConfig.PERK_VITALITY_MAX_GAIN_PER_RANK,
                            gains[worst] <= BalanceConfig.PERK_VITALITY_MAX_GAIN_PER_RANK,
                            "largest scenario gain in " + WeaponRoleModel.scenarios().get(worst).id));
                }
            } else {
                float[] gains = perkScenarioGains(context, new BuildStats(),
                        BuildValuationModel.statsWithPerk(perk, perk.maxRank));
                int least = indexOfMin(gains);
                results.add(new RuleResult(RuleKind.PERK, "PK2 " + perk.name() + " focused d" + depth,
                        gains[least], 0f, BalanceConfig.PERK_FOCUSED_MAX_GAIN,
                        gains[least] <= BalanceConfig.PERK_FOCUSED_MAX_GAIN,
                        "least-improved scenario " + WeaponRoleModel.scenarios().get(least).id
                                + " (all eight above the bound = not focused)"));
            }
        }
        return results;
    }

    /**
     * Per-scenario improvement of the player's best answer from one build to another: the larger of the
     * offence gain (best eligible weapon's score) and the defence gain (scenario survivals).
     */
    public static float[] perkScenarioGains(BuildValuationModel.Context context, BuildStats before, BuildStats after) {
        float[] answersBefore = BuildValuationModel.bestAnswers(context, before);
        float[] answersAfter = BuildValuationModel.bestAnswers(context, after);
        float[] survivalsBefore = BuildValuationModel.scenarioSurvivals(context, before);
        float[] survivalsAfter = BuildValuationModel.scenarioSurvivals(context, after);
        List<WeaponRoleModel.RoleScenario> scenarios = WeaponRoleModel.scenarios();
        float[] gains = new float[answersBefore.length];
        for (int index = 0; index < gains.length; index++) {
            boolean hitsTaken = scenarios.get(index).bruiserCharge;
            float offence = hitsTaken
                    ? GameMath.buildNicheGain(answersBefore[index] + 1f, answersAfter[index] + 1f, false)
                    : GameMath.buildNicheGain(answersBefore[index], answersAfter[index], false);
            float defence = Float.isInfinite(survivalsBefore[index]) || survivalsBefore[index] <= 0f ? 0f
                    : GameMath.buildNicheGain(survivalsBefore[index], survivalsAfter[index], true);
            gains[index] = Math.max(offence, defence);
        }
        return gains;
    }

    private static int indexOfMax(float[] values) {
        int best = 0;
        for (int index = 1; index < values.length; index++) if (values[index] > values[best]) best = index;
        return best;
    }

    private static int indexOfMin(float[] values) {
        int best = 0;
        for (int index = 1; index < values.length; index++) if (values[index] < values[best]) best = index;
        return best;
    }

    /** R-KEYSTONE: niche gain at every reference depth, and the worst other probe's loss at some depth. */
    public static List<RuleResult> keystoneResults() {
        List<RuleResult> results = new ArrayList<>();
        for (Keystone keystone : Keystone.values()) {
            BuildValuationModel.Niche niche = BuildValuationModel.nicheOf(keystone);
            BuildStats stats = BuildValuationModel.statsWithKeystone(keystone);
            BuildValuationModel.Loss worst = null;
            int worstDepth = 0;
            for (int depth : BalanceConfig.BUILD_PERK_REFERENCE_DEPTHS) {
                float before = BuildValuationModel.measure(niche, new BuildStats(), depth);
                float after = BuildValuationModel.measure(niche, stats, depth);
                float gain = GameMath.buildNicheGain(before, after, niche.metric.higherIsBetter());
                results.add(new RuleResult(RuleKind.KEYSTONE, "R-KEYSTONE " + keystone.name() + " niche d" + depth,
                        gain, BalanceConfig.KEYSTONE_NICHE_MIN_GAIN, Float.MAX_VALUE,
                        gain >= BalanceConfig.KEYSTONE_NICHE_MIN_GAIN,
                        String.format("%s %.3f -> %.3f", niche.label(), before, after)));
                BuildValuationModel.Loss loss = BuildValuationModel.worstLoss(stats, niche, depth);
                if (worst == null || loss.loss > worst.loss) {
                    worst = loss;
                    worstDepth = depth;
                }
            }
            results.add(new RuleResult(RuleKind.KEYSTONE, "R-KEYSTONE " + keystone.name() + " cost",
                    worst.loss, BalanceConfig.KEYSTONE_COST_MIN_LOSS, Float.MAX_VALUE,
                    worst.loss >= BalanceConfig.KEYSTONE_COST_MIN_LOSS,
                    "worst: " + worst.probe + " at d" + worstDepth));
        }
        return results;
    }

    /** R-CARRY: every tag's expected build score within 1 + BUILD_CARRY_TOLERANCE of every other's. */
    public static List<RuleResult> buildCarryResults() {
        List<RuleResult> results = new ArrayList<>();
        float best = 0f;
        float worst = Float.MAX_VALUE;
        for (BuildTag tag : BuildTag.values()) {
            if (isCarryWaived(tag)) continue; // a waived tag stays visible but never sets the band
            float score = BuildValuationModel.carry(tag).score;
            best = Math.max(best, score);
            worst = Math.min(worst, score);
        }
        float ceiling = worst * (1f + BalanceConfig.BUILD_CARRY_TOLERANCE);
        float floor = best / (1f + BalanceConfig.BUILD_CARRY_TOLERANCE);
        for (BuildTag tag : BuildTag.values()) {
            BuildValuationModel.Carry carry = BuildValuationModel.carry(tag);
            results.add(new RuleResult(RuleKind.BUILD_CARRY, carrySubject(tag), carry.score, floor, ceiling,
                    carry.score >= floor - 1e-6f && carry.score <= ceiling + 1e-6f,
                    String.format("TTK x%.3f TTD x%.3f (%d ranks + %s)", carry.turnsToKillLift, carry.turnsToDieLift,
                            carry.ranks, carry.keystone == null ? "no keystone" : carry.keystone.name())));
        }
        float spread = worst > 0f ? best / worst : Float.POSITIVE_INFINITY;
        results.add(new RuleResult(RuleKind.BUILD_CARRY, "R-CARRY spread best / worst", spread, 1f,
                1f + BalanceConfig.BUILD_CARRY_TOLERANCE, spread <= 1f + BalanceConfig.BUILD_CARRY_TOLERANCE + 1e-6f,
                "no trap build, no dominant build (waived tags excluded)"));
        return results;
    }

    /**
     * R-DOT (restated by balance-overhaul order 4): exactly one definition per DoT. There are TWO
     * families, each defined once and re-exported byte-for-byte: the STATUS DoTs are fractions of the
     * host's max HP (BURN_TICK_FRACTION, POISON_TICK_FRACTION — hazard/acid/status magnitudes), and the
     * WEAPON burn base (WEAPON_BURN_BASE_PER_TURN — Incendiary/Incinerator, R-ROLE-fitted damage). The
     * pre-order-4 flat BURN_DAMAGE_PER_TURN / POISON_DAMAGE_PER_STACK must not exist anywhere, and every
     * same-named field in a shim class must equal BalanceConfig (a divergent literal fails here).
     */
    public static List<RuleResult> dotUniquenessResults() {
        List<RuleResult> results = new ArrayList<>();
        boolean unifiedBurn = BalanceConfig.INCENDIARY_BURN_PER_TURN_BASE == BalanceConfig.WEAPON_BURN_BASE_PER_TURN;
        results.add(new RuleResult(RuleKind.DOT_UNIQUENESS, "weapon BURN base unified",
                BalanceConfig.INCENDIARY_BURN_PER_TURN_BASE,
                BalanceConfig.WEAPON_BURN_BASE_PER_TURN, BalanceConfig.WEAPON_BURN_BASE_PER_TURN, unifiedBurn,
                "INCENDIARY_BURN_PER_TURN_BASE must reference the single WEAPON_BURN_BASE_PER_TURN base"));
        String[] retiredFlatBases = {"BURN_DAMAGE_PER_TURN", "POISON_DAMAGE_PER_STACK"};
        for (String retiredName : retiredFlatBases) {
            boolean absent = !declaresField(BalanceConfig.class, retiredName)
                    && !declaresField(EffectConstants.class, retiredName);
            results.add(new RuleResult(RuleKind.DOT_UNIQUENESS, "retired flat base " + retiredName,
                    absent ? 0f : 1f, 0f, 0f, absent,
                    "status DoTs are fractions of max HP (SECTION 23) — a flat per-turn base must not return"));
        }
        final int baselineResultCount = results.size();

        for (Class<?> shimClass : SHIM_CLASSES) {
            for (Field shimField : shimClass.getDeclaredFields()) {
                if (!isComparableConstant(shimField)) continue;
                Field configField = findBalanceConfigField(shimField.getName());
                if (configField == null || !isComparableConstant(configField)) continue;
                float shimValue   = readConstantAsFloat(shimField);
                float configValue = readConstantAsFloat(configField);
                boolean matches = shimValue == configValue;
                if (!matches) {
                    results.add(new RuleResult(RuleKind.DOT_UNIQUENESS,
                            shimClass.getSimpleName() + "." + shimField.getName(), shimValue,
                            configValue, configValue, false,
                            "shim field diverged from BalanceConfig — a second definition exists"));
                }
            }
        }
        if (results.size() == baselineResultCount) {
            results.add(new RuleResult(RuleKind.DOT_UNIQUENESS, "shim integrity",
                    0f, 0f, 0f, true, "every same-named shim field re-exports BalanceConfig"));
        }
        return results;
    }

    private static boolean declaresField(Class<?> holder, String fieldName) {
        for (Field field : holder.getDeclaredFields()) {
            if (field.getName().equals(fieldName)) return true;
        }
        return false;
    }

    /** R-FLAGS: no live test/debug flags — any TEST/DEBUG boolean must be false. */
    public static List<RuleResult> flagResults() {
        List<RuleResult> results = new ArrayList<>();
        for (Class<?> constantClass : FLAG_SCAN_CLASSES) {
            for (Field field : constantClass.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType() != boolean.class) continue;
                String fieldName = field.getName();
                boolean isTestFlag = fieldName.contains("TEST") || fieldName.contains("DEBUG");
                if (!isTestFlag) continue;
                boolean flagValue = readBooleanConstant(field);
                results.add(new RuleResult(RuleKind.FLAGS,
                        constantClass.getSimpleName() + "." + fieldName, flagValue ? 1f : 0f,
                        0f, 0f, !flagValue, "test/debug flags must be false in every real build"));
            }
        }
        // Named flag outside the naming convention, checked explicitly (the order-1 leak).
        results.add(new RuleResult(RuleKind.FLAGS, "GameBalance.START_ROOM_ANY_TIER_ENABLED",
                GameBalance.START_ROOM_ANY_TIER_ENABLED ? 1f : 0f, 0f, 0f,
                !GameBalance.START_ROOM_ANY_TIER_ENABLED,
                "start-room offers must roll the designed COMMON..UNCOMMON band"));
        return results;
    }

    /**
     * R-SINGLE-DIFFICULTY (order 8): the game has EXACTLY ONE difficulty and must never grow modes.
     *
     * <p>Two structural checks, both by reflection (no gameplay values involved):
     * <ol>
     *   <li>BalanceConfig declares exactly ONE starting-attribute block — one {@code PLAYER_START_*}
     *       constant per {@link Attribute}, no more (a second block is how a second mode starts).</li>
     *   <li>No constant, nested type or enum constant in any scanned class carries mode-family
     *       naming ({@link #MODE_FAMILY_TOKENS}), the same tripwire R-FLAGS uses for live test flags.</li>
     * </ol>
     *
     * <p>WHY: difficulty in this roguelike is chosen INSIDE the run (route, region danger, depth,
     * loot), never from a menu. The four old modes changed only starting stats while every enemy
     * number, band and telegraph cap stayed anchored to one of them, so three of the four were never
     * actually balanced. Deleting them makes the whole contract exact for the one real game.
     */
    public static List<RuleResult> singleDifficultyResults() {
        List<RuleResult> results = new ArrayList<>();

        int startingBlockSize = 0;
        for (Field field : BalanceConfig.class.getDeclaredFields()) {
            if (isComparableConstant(field) && field.getName().startsWith(STARTING_ATTRIBUTE_PREFIX)) {
                startingBlockSize++;
            }
        }
        int expectedBlockSize = Attribute.values().length;
        results.add(new RuleResult(RuleKind.SINGLE_DIFFICULTY, "one starting-attribute block",
                startingBlockSize, expectedBlockSize, expectedBlockSize,
                startingBlockSize == expectedBlockSize,
                "BalanceConfig must declare exactly one " + STARTING_ATTRIBUTE_PREFIX
                        + "<ATTRIBUTE> constant per attribute — a second block means a second mode"));

        int modeNameHits = 0;
        for (Class<?> scannedClass : MODE_SCAN_CLASSES) {
            for (Field field : scannedClass.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) continue;
                modeNameHits += addModeNamingViolation(results, scannedClass, "constant", field.getName());
            }
            for (Class<?> nestedClass : scannedClass.getDeclaredClasses()) {
                modeNameHits += addModeNamingViolation(results, scannedClass, "nested type", nestedClass.getSimpleName());
                if (!nestedClass.isEnum()) continue;
                for (Object enumConstant : nestedClass.getEnumConstants()) {
                    modeNameHits += addModeNamingViolation(results, scannedClass, "enum constant",
                            ((Enum<?>) enumConstant).name());
                }
            }
        }
        if (modeNameHits == 0) {
            results.add(new RuleResult(RuleKind.SINGLE_DIFFICULTY, "no mode-family naming",
                    0f, 0f, 0f, true,
                    "no RECRUIT/NIGHTMARE/ULTRA/DIFFICULTY/STAT_BASE_/*_MODE naming in any scanned class"));
        }
        return results;
    }

    /**
     * Records a violation when {@code name} carries mode-family naming.
     * Returns 1 when a violation was added, 0 otherwise, so the caller can emit the single
     * "clean" row only when the whole sweep found nothing.
     */
    private static int addModeNamingViolation(List<RuleResult> results, Class<?> scannedClass,
                                              String memberKind, String name) {
        for (String token : MODE_FAMILY_TOKENS) {
            if (!name.contains(token)) continue;
            results.add(new RuleResult(RuleKind.SINGLE_DIFFICULTY,
                    scannedClass.getSimpleName() + " " + memberKind + " " + name, 1f, 0f, 0f, false,
                    "mode-family naming (matched \"" + token + "\") — the game has exactly ONE "
                            + "difficulty; tune THE dials (scarcity, heal drain, region danger) instead"));
            return 1;
        }
        return 0;
    }

    /** COVERAGE: every content entry is classified/priced — un-priced content fails the build. */
    public static List<RuleResult> coverageResults() {
        List<RuleResult> results = new ArrayList<>();
        for (ItemType itemType : ItemType.values()) {
            if (itemType.getCategory() == ItemCategory.WEAPON) {
                boolean classified = WEAPON_ITEM_CLASSIFICATIONS.containsKey(itemType);
                results.add(new RuleResult(RuleKind.COVERAGE, "weapon item " + itemType.name(),
                        classified ? 1f : 0f, 1f, 1f, classified,
                        classified ? "classified " + WEAPON_ITEM_CLASSIFICATIONS.get(itemType)
                                   : "UNCLASSIFIED — register it in BalanceSchema (ranged role, melee, or fallback)"));
            } else if (itemType.getCategory() == ItemCategory.CONSUMABLE) {
                boolean classified = CONSUMABLE_CLASSIFICATIONS.containsKey(itemType);
                results.add(new RuleResult(RuleKind.COVERAGE, "consumable " + itemType.name(),
                        classified ? 1f : 0f, 1f, 1f, classified,
                        classified ? "classified " + CONSUMABLE_CLASSIFICATIONS.get(itemType)
                                   : "UNCLASSIFIED — price it as a heal or declare it utility in BalanceSchema"));
            }
        }
        for (EnemyType enemyType : EnemyType.values()) {
            if (enemyType.role() == EnemyRole.BOSS) continue;
            boolean hasBand = ENEMY_THREAT_POINT_BANDS.containsKey(enemyType.role());
            results.add(new RuleResult(RuleKind.COVERAGE, "enemy " + enemyType.displayName(),
                    hasBand ? 1f : 0f, 1f, 1f, hasBand,
                    hasBand ? "role " + enemyType.role() + " banded"
                            : "role " + enemyType.role() + " has NO TP band registered in BalanceSchema"));
        }
        for (AmmoType ammoType : AmmoType.values()) {
            boolean hasRow = false;
            for (ScarcityRowSpec row : SCARCITY_ROWS) {
                if (row.ammoType == ammoType) { hasRow = true; break; }
            }
            results.add(new RuleResult(RuleKind.COVERAGE, "ammo type " + ammoType.name(),
                    hasRow ? 1f : 0f, 1f, 1f, hasRow,
                    hasRow ? "priced in the scarcity model"
                           : "NOT priced — register a scarcity row in BalanceSchema"));
        }
        // Order 5: every enemy SPECIAL verb must have a registered equivalence (or an explicit exempt
        // classification) so the cycle-averaged Threat-Point model can never silently under-price a caster.
        for (SpecialAbility ability : SpecialAbility.values()) {
            SpecialVerbPricing pricing = SPECIAL_VERB_PRICING.get(ability);
            boolean priced = pricing != null;
            results.add(new RuleResult(RuleKind.COVERAGE, "special verb " + ability.name(),
                    priced ? 1f : 0f, 1f, 1f, priced,
                    priced ? "priced " + pricing
                           : "UNPRICED — register a SpecialVerbPricing equivalence in BalanceSchema"));
        }
        return results;
    }

    // =====================================================================================
    // R-ABILITY — REAL RARITY (balance-overhaul order 5, B5/B6; replaces new-game-balancr order 2's
    // "tier = a priced ability CEILING", OVERRIDE CLAUSE). Every guarantee is now two-sided:
    //   (a) FELT (B6)      — every catalogued ability is priced (finite, >= 0) at every weapon level AND prices
    //                        at least ABILITY_MIN_FELT_PP at its EXPECTED weapon level (expectedAbilityLevel).
    //   (b) TAGGED (B6)    — every ability carries a BuildTag (B3 synergy reads it).
    //   (c) SHAPE (B5)     — each tier's count / MAJOR count equals the B5 table, and the catalogue holds enough
    //                        eligible abilities of each rank for a gun AND a melee weapon to roll that shape.
    //   (d) BUDGET (B5)    — the tier budgets equal the B5 table (UNCOMMON 12 / RARE 25 / EPIC 40 / LEGENDARY 60)
    //                        and rise monotonically with rarity.
    //   (e) BAND, PROVEN   — at EVERY weapon level 1..MAX, the cheapest and the priciest draw of each tier's shape
    //                        (for a gun and for a melee weapon) both land inside budget × (1 ± tolerance): no seed
    //                        can roll outside the band, by construction.
    //   (f) BAND, SAMPLED (A4) — abilityRollSweepResults(): real seeded rolls, ABILITY_ROLL_SWEEP_SEEDS seeds ×
    //                        5 tiers × ABILITY_ROLL_SWEEP_DEPTHS, gun and melee, each with exactly the tier's
    //                        count, its MAJORs, its signature, and a total inside the band.
    // =====================================================================================

    /** B5 as written in the spec: abilities per tier (signature not counted), indexed by WeaponTier ordinal. */
    private static final int[]   B5_TIER_ABILITY_COUNTS  = {0, 1, 2, 3, 3};
    /** B5 as written in the spec: ability-PP budget per tier, indexed by WeaponTier ordinal. */
    private static final float[] B5_TIER_ABILITY_BUDGETS = {0f, 12f, 25f, 40f, 60f};

    /**
     * The EXPECTED weapon level an ability is valued at (B6): the on-curve weapon level
     * ({@code expectedPlayerAtDepth(d).weaponLevel}) at the shallowest depth {@code d} whose region can DROP the
     * lowest tier that may roll the ability — UNCOMMON for a STANDARD ability (the first tier with a standard
     * slot), the first tier with a MAJOR slot for a MAJOR (EPIC), LEGENDARY for a signature. "Can drop" reads
     * the region drop band {@code WEAPON_DROP_TIER_MAX_BY_REGION}; region r starts at depth r × band + 1.
     */
    public static int expectedAbilityLevel(WeaponAbility ability) {
        int lowestTierOrdinal = lowestTierOrdinalThatRolls(ability.rank);
        int[] regionMaximumTier = BalanceConfig.WEAPON_DROP_TIER_MAX_BY_REGION;
        int bandSize = Math.max(1, BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE);
        int region = regionMaximumTier.length - 1;
        for (int regionIndex = 0; regionIndex < regionMaximumTier.length; regionIndex++) {
            if (regionMaximumTier[regionIndex] >= lowestTierOrdinal) { region = regionIndex; break; }
        }
        return GameMath.expectedPlayerAtDepth(region * bandSize + 1).weaponLevel;
    }

    /** The drop depth {@link #expectedAbilityLevel} values an ability at (report helper). */
    public static int expectedAbilityDepth(WeaponAbility ability) {
        int lowestTierOrdinal = lowestTierOrdinalThatRolls(ability.rank);
        int[] regionMaximumTier = BalanceConfig.WEAPON_DROP_TIER_MAX_BY_REGION;
        int bandSize = Math.max(1, BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE);
        for (int regionIndex = 0; regionIndex < regionMaximumTier.length; regionIndex++) {
            if (regionMaximumTier[regionIndex] >= lowestTierOrdinal) return regionIndex * bandSize + 1;
        }
        return (regionMaximumTier.length - 1) * bandSize + 1;
    }

    /** Lowest WeaponTier ordinal whose shape draws an ability of this rank (data-driven from WeaponTier). */
    private static int lowestTierOrdinalThatRolls(WeaponAbility.Rank rank) {
        for (WeaponTier tier : WeaponTier.values()) {
            boolean rolls = rank == WeaponAbility.Rank.SIGNATURE ? tier.rollsSignature
                          : rank == WeaponAbility.Rank.MAJOR     ? tier.majorCount > 0
                          :                                        tier.standardCount() > 0;
            if (rolls) return tier.ordinal();
        }
        return WeaponTier.LEGENDARY.ordinal();
    }

    /** Priced PP of an ability at a weapon level (the one pricing path the rule, roller and report share). */
    public static float abilityPowerPointsAt(WeaponAbility ability, int weaponLevel) {
        AbilityInstance instance = WeaponRoller.buildAbilityInstance(ability, weaponLevel);
        return GameMath.abilityPowerPoints(ability, instance.magnitude, instance.countValue);
    }

    /** R-ABILITY (a)-(e): felt, tagged, shape, budgets, and the band proven at every level. */
    public static List<RuleResult> abilityBudgetResults() {
        List<RuleResult> results = new ArrayList<>();
        // (a) FELT + (b) TAGGED, per ability.
        for (WeaponAbility ability : WeaponAbility.values()) {
            boolean priced = true;
            for (int weaponLevel = 1; weaponLevel <= WeaponConstants.MAX_WEAPON_LEVEL; weaponLevel++) {
                float price = abilityPowerPointsAt(ability, weaponLevel);
                if (Float.isNaN(price) || Float.isInfinite(price) || price < 0f) priced = false;
            }
            int expectedLevel = expectedAbilityLevel(ability);
            float expectedPrice = abilityPowerPointsAt(ability, expectedLevel);
            boolean felt = priced && expectedPrice >= BalanceConfig.ABILITY_MIN_FELT_PP;
            results.add(new RuleResult(RuleKind.ABILITY_BUDGET, "ability " + ability.name() + " felt",
                    expectedPrice, BalanceConfig.ABILITY_MIN_FELT_PP, Float.MAX_VALUE, felt,
                    !priced ? "UNPRICED — GameMath.abilityPowerPoints returned a non-finite/negative value"
                            : String.format("%.1f PP at expected weapon level %d (%s, depth %d)%s", expectedPrice,
                                    expectedLevel, ability.rank, expectedAbilityDepth(ability),
                                    felt ? "" : " — BELOW ABILITY_MIN_FELT_PP: cut or merge it (B6)")));
            boolean tagged = ability.buildTag != null;
            results.add(new RuleResult(RuleKind.ABILITY_BUDGET, "ability " + ability.name() + " tagged",
                    tagged ? 1f : 0f, 1f, 1f, tagged,
                    tagged ? "counts toward " + ability.buildTag.name() + " synergy"
                           : "NO BuildTag — every ability must count toward a build (B6)"));
        }
        // (c) SHAPE + (d) BUDGET, per tier.
        float previousBudget = -1f;
        boolean monotonic = true;
        for (WeaponTier tier : WeaponTier.values()) {
            int ordinal = tier.ordinal();
            boolean countMatches = tier.abilityCount == B5_TIER_ABILITY_COUNTS[ordinal];
            results.add(new RuleResult(RuleKind.ABILITY_BUDGET, "tier " + tier.name() + " count",
                    tier.abilityCount, B5_TIER_ABILITY_COUNTS[ordinal], B5_TIER_ABILITY_COUNTS[ordinal], countMatches,
                    tier.abilityCount + " abilities (" + tier.majorCount + " MAJOR)"
                            + (tier.rollsSignature ? " + signature" : "") + (countMatches ? "" : " — B5 says "
                            + B5_TIER_ABILITY_COUNTS[ordinal])));
            boolean budgetMatches = Math.abs(tier.abilityPowerPointBudget - B5_TIER_ABILITY_BUDGETS[ordinal]) < 1e-3f;
            results.add(new RuleResult(RuleKind.ABILITY_BUDGET, "tier " + tier.name() + " budget",
                    tier.abilityPowerPointBudget, B5_TIER_ABILITY_BUDGETS[ordinal], B5_TIER_ABILITY_BUDGETS[ordinal],
                    budgetMatches, String.format("band %.1f..%.1f PP", tier.abilityPowerPointFloor(),
                            tier.abilityPowerPointCeiling())));
            if (tier.abilityPowerPointBudget < previousBudget) monotonic = false;
            previousBudget = tier.abilityPowerPointBudget;
            for (boolean isMelee : new boolean[]{false, true}) {
                int standardPool  = eligibleCount(WeaponAbility.Rank.STANDARD, isMelee);
                int majorPool     = eligibleCount(WeaponAbility.Rank.MAJOR, isMelee);
                int signaturePool = eligibleCount(WeaponAbility.Rank.SIGNATURE, isMelee);
                boolean enough = standardPool >= tier.standardCount() && majorPool >= tier.majorCount
                        && (!tier.rollsSignature || signaturePool >= 1);
                results.add(new RuleResult(RuleKind.ABILITY_BUDGET,
                        "tier " + tier.name() + " pool " + (isMelee ? "melee" : "gun"),
                        enough ? 1f : 0f, 1f, 1f, enough,
                        String.format("eligible: %d standard, %d MAJOR, %d signature", standardPool, majorPool,
                                signaturePool)));
                // (e) BAND, PROVEN — the extreme draws of the shape at every level.
                float lowestTotal  = Float.MAX_VALUE;
                float highestTotal = 0f;
                for (int weaponLevel = 1; weaponLevel <= WeaponConstants.MAX_WEAPON_LEVEL; weaponLevel++) {
                    float cheapest  = extremeShapeTotal(tier, isMelee, weaponLevel, false);
                    float priciest  = extremeShapeTotal(tier, isMelee, weaponLevel, true);
                    lowestTotal  = Math.min(lowestTotal, cheapest);
                    highestTotal = Math.max(highestTotal, priciest);
                }
                boolean inBand = WeaponRoller.isWithinTierBand(tier, lowestTotal)
                        && WeaponRoller.isWithinTierBand(tier, highestTotal);
                results.add(new RuleResult(RuleKind.ABILITY_BUDGET,
                        "tier " + tier.name() + " band " + (isMelee ? "melee" : "gun"),
                        highestTotal, tier.abilityPowerPointFloor(), tier.abilityPowerPointCeiling(), inBand,
                        String.format("any draw at any level prices %.1f..%.1f PP", lowestTotal, highestTotal)));
            }
        }
        results.add(new RuleResult(RuleKind.ABILITY_BUDGET, "tier budgets monotonic",
                monotonic ? 1f : 0f, 1f, 1f, monotonic,
                "TIER_ABILITY_PP_BUDGET_* must not decrease as rarity rises"));
        return results;
    }

    /** Eligible abilities of one rank for a gun or a melee weapon. */
    private static int eligibleCount(WeaponAbility.Rank rank, boolean isMelee) {
        int count = 0;
        for (WeaponAbility ability : WeaponAbility.values()) {
            if (ability.rank == rank && ability.eligibleFor(isMelee)) count++;
        }
        return count;
    }

    /**
     * The cheapest ({@code priciest == false}) or priciest priced total any draw of a tier's shape can carry at
     * one weapon level: the k cheapest / dearest eligible abilities of each rank, summed. Allocation is fine —
     * this is audit code, run once.
     */
    private static float extremeShapeTotal(WeaponTier tier, boolean isMelee, int weaponLevel, boolean priciest) {
        return extremeRankTotal(WeaponAbility.Rank.STANDARD, tier.standardCount(), isMelee, weaponLevel, priciest)
             + extremeRankTotal(WeaponAbility.Rank.MAJOR, tier.majorCount, isMelee, weaponLevel, priciest)
             + extremeRankTotal(WeaponAbility.Rank.SIGNATURE, tier.rollsSignature ? 1 : 0, isMelee, weaponLevel,
                                priciest);
    }

    private static float extremeRankTotal(WeaponAbility.Rank rank, int count, boolean isMelee, int weaponLevel,
                                          boolean priciest) {
        if (count <= 0) return 0f;
        List<Float> prices = new ArrayList<>();
        for (WeaponAbility ability : WeaponAbility.values()) {
            if (ability.rank == rank && ability.eligibleFor(isMelee)) prices.add(abilityPowerPointsAt(ability, weaponLevel));
        }
        prices.sort(priciest ? java.util.Collections.reverseOrder() : java.util.Comparator.naturalOrder());
        float total = 0f;
        for (int index = 0; index < Math.min(count, prices.size()); index++) total += prices.get(index);
        return total;
    }

    /**
     * R-ABILITY (f) — A4's SAMPLED roll sweep: for every tier and every ABILITY_ROLL_SWEEP_DEPTHS depth, real
     * {@link WeaponRoller#rollAbilitySet} rolls over ABILITY_ROLL_SWEEP_SEEDS seeds, for a gun and for a melee
     * weapon, at the on-curve weapon level for that depth. One row per (tier, depth): its value is the number
     * of rolls that broke the shape (wrong count, wrong MAJOR count, missing signature) or the band; the detail
     * carries the sampled min..max PP. The band is independent of the region drop table on purpose — a shop
     * tier upgrade can put any tier at any depth.
     */
    public static List<RuleResult> abilityRollSweepResults() {
        List<RuleResult> results = new ArrayList<>();
        for (WeaponTier tier : WeaponTier.values()) {
            for (int depth : BalanceConfig.ABILITY_ROLL_SWEEP_DEPTHS) {
                int weaponLevel = GameMath.expectedPlayerAtDepth(depth).weaponLevel;
                int violations = 0;
                int rolls = 0;
                float sampledMinimum = Float.MAX_VALUE;
                float sampledMaximum = 0f;
                String firstViolation = "";
                for (long seed = 0; seed < BalanceConfig.ABILITY_ROLL_SWEEP_SEEDS; seed++) {
                    WeaponRoller roller = new WeaponRoller(seed * 7919L + depth);
                    for (boolean isMelee : new boolean[]{false, true}) {
                        AbilityInstance[] abilities = roller.rollAbilitySet(isMelee, tier, weaponLevel);
                        rolls++;
                        int majors = 0;
                        int signatures = 0;
                        for (AbilityInstance instance : abilities) {
                            if (instance.ability.isMajor()) majors++;
                            if (instance.ability.legendaryOnly) signatures++;
                        }
                        float total = WeaponRoller.totalAbilityPowerPoints(abilities);
                        sampledMinimum = Math.min(sampledMinimum, total);
                        sampledMaximum = Math.max(sampledMaximum, total);
                        boolean shapeHolds = abilities.length == tier.totalAbilityCount()
                                && majors == tier.majorCount
                                && signatures == (tier.rollsSignature ? 1 : 0);
                        if (!shapeHolds || !WeaponRoller.isWithinTierBand(tier, total)) {
                            if (violations == 0) {
                                firstViolation = String.format("; first: seed %d %s %d abilities, %d MAJOR, %.1f PP",
                                        seed, isMelee ? "melee" : "gun", abilities.length, majors, total);
                            }
                            violations++;
                        }
                    }
                }
                results.add(new RuleResult(RuleKind.ABILITY_BUDGET,
                        "roll sweep " + tier.name() + " depth " + depth,
                        violations, 0f, 0f, violations == 0,
                        String.format("%d rolls at weapon level %d: %.1f..%.1f PP (band %.1f..%.1f)%s", rolls,
                                weaponLevel, sampledMinimum, sampledMaximum, tier.abilityPowerPointFloor(),
                                tier.abilityPowerPointCeiling(), firstViolation)));
            }
        }
        return results;
    }

    // =====================================================================================
    // R-BOSS-* (new-game-balancr order 6) — BOSSES AS BUILD CHECKS. A boss's HP, DPT, per-verb damage and
    // reward are all DERIVED (BossBalance) from SECTION 14 inputs, never flat constants, so these rules
    // assert the derivation upholds the contract at every act-boss depth:
    //   R-BOSS-GATE     the DEPTH-1 starting loadout CANNOT win — even a perfect-play hoarder dies at less
    //                   than 1/MARGIN of the damage needed, WITH the maximum heal supply. Beating a boss
    //                   with the start weapon is arithmetically impossible, not merely hard.
    //   R-BOSS-FAIR     the EXPECTED loadout gets a real but winnable fight — length in [target, 1.5*target]
    //                   and survival ratio in [0.4, 0.7].
    //   R-BOSS-VERB-CAP every derived verb <= 35% eHP; any verb > 25% is telegraphed (boss verbs are the
    //                   likeliest fairness-bypass path, so every one is checked at its derived value).
    //   R-BOSS-REWARD   reward >= modelled fight consumption * risk premium (a boss is a net-positive payday).
    //   R-BOSS-AMMO     the fight's ammo demand is coverable by reserve caps + the arena ammo budget, so the
    //                   check tests your BUILD, not whether you entered with full pockets.
    // =====================================================================================

    /** One boss verb the fairness caps are proven on: which boss, its display name, DPT fraction, telegraph flag. */
    public static final class BossVerbSpec {
        public final BossBalance.Archetype archetype;
        public final String  verbName;
        public final float   dptFraction;
        public final boolean telegraphed;

        BossVerbSpec(BossBalance.Archetype archetype, String verbName, float dptFraction, boolean telegraphed) {
            this.archetype   = archetype;
            this.verbName    = verbName;
            this.dptFraction = dptFraction;
            this.telegraphed = telegraphed;
        }
    }

    private static final List<BossVerbSpec> BOSS_VERBS = buildBossVerbRegistry();

    private static List<BossVerbSpec> buildBossVerbRegistry() {
        List<BossVerbSpec> registry = new ArrayList<>();
        registry.add(new BossVerbSpec(BossBalance.Archetype.OVERSEER,   "Overseer melee",
                BalanceConfig.OVERSEER_MELEE_DPT_FRACTION,  false));
        registry.add(new BossVerbSpec(BossBalance.Archetype.OVERSEER,   "Overseer charge",
                BalanceConfig.OVERSEER_CHARGE_DPT_FRACTION, true));
        registry.add(new BossVerbSpec(BossBalance.Archetype.OVERSEER,   "Overseer laser",
                BalanceConfig.OVERSEER_LASER_DPT_FRACTION,  true));
        registry.add(new BossVerbSpec(BossBalance.Archetype.CORRUPTOR,  "Corruptor acid",
                BalanceConfig.CORRUPTOR_ACID_DPT_FRACTION,  true));
        registry.add(new BossVerbSpec(BossBalance.Archetype.HELL_BARON, "Hell Baron fire",
                BalanceConfig.HELL_BARON_FIRE_DPT_FRACTION,      true));
        registry.add(new BossVerbSpec(BossBalance.Archetype.HELL_BARON, "Hell Baron cleave1",
                BalanceConfig.HELL_BARON_CLEAVE_P1_DPT_FRACTION, true));
        registry.add(new BossVerbSpec(BossBalance.Archetype.HELL_BARON, "Hell Baron cleave2",
                BalanceConfig.HELL_BARON_CLEAVE_P2_DPT_FRACTION, true));
        return Collections.unmodifiableList(registry);
    }

    /** The registered boss verbs — BalanceReport's BOSS RULESET table iterates exactly this list. */
    public static List<BossVerbSpec> bossVerbs() {
        return BOSS_VERBS;
    }

    /**
     * R-BOSS-GATE: with the STARTING weapon (L1 COMMON, on the ladder at the boss's threat level — balance-overhaul
     * order 1; the character is otherwise on-curve), the turns to kill the boss must be at
     * least {@link BalanceConfig#BOSS_GATE_MIN_DAMAGE_MARGIN} times the turns the player survives even WITH
     * the maximum modelled heal supply — so the start weapon can never win. The margin is depth-independent
     * of the fight length and rises with the expected-DPT gate, so the shallowest boss is the tightest case.
     */
    public static List<RuleResult> bossGateResults() {
        List<RuleResult> results = new ArrayList<>();
        float minMargin  = BalanceConfig.BOSS_GATE_MIN_DAMAGE_MARGIN;
        for (BossTable.Row row : BossTable.standard().rows()) {
            BossBalance.Archetype archetype = row.archetype;
            BossStats stats = BossBalance.statsForRow(row);
            // Balance-overhaul order 1: the START weapon (L1 COMMON) on the boss floor, carried by an otherwise
            // on-curve character — the generous reading; the ladder's lag penalty applies to the weapon.
            ExpectedPlayer startWeapon = ladderStartWeaponPlayer(row.depth);
            float maxHeals = startWeapon.effectiveHitPoints * BalanceConfig.BOSS_GATE_MODELED_HEAL_SUPPLY_EHP_FRACTION;
            float startTurnsToKill = GameMath.bossFightTurnsForPlayerDamagePerTurn(
                    stats.effectiveHitPoints, startWeapon.damagePerTurn);
            float survivableTurns  = GameMath.bossFightTurnsForPlayerDamagePerTurn(
                    startWeapon.effectiveHitPoints + maxHeals, stats.damagePerTurn);
            float margin = survivableTurns > 0f ? startTurnsToKill / survivableTurns : Float.POSITIVE_INFINITY;
            results.add(new RuleResult(RuleKind.BOSS_GATE, archetype.displayName + " depth " + row.depth,
                    margin, minMargin, Float.POSITIVE_INFINITY, margin >= minMargin,
                    String.format("start-weapon TTK %.1f vs survivable %.1f turns (with max heals) — must be >= %.1fx",
                            startTurnsToKill, survivableTurns, minMargin)));
        }
        return results;
    }

    /**
     * R-BOSS-FAIR (v2, balance-overhaul order 6 F9): for EVERY BossTable row, the EXPECTED player at the row's
     * depth gets a fight whose LENGTH lands in the row's target +/- BOSS_FIGHT_TURNS_TOLERANCE (OVERRIDES the
     * order-6 band [target, 1.5*target]) and whose SURVIVAL RATIO lands in [0.4, 0.7]; the row's own survival
     * dial must sit in that band too. All hold by construction of the derivation, so a broken input (a bad
     * target or ratio on a row) surfaces here rather than in a live fight.
     */
    public static List<RuleResult> bossFairResults() {
        List<RuleResult> results = new ArrayList<>();
        float tolerance = BalanceConfig.BOSS_FIGHT_TURNS_TOLERANCE;
        for (BossTable.Row row : BossTable.standard().rows()) {
            String subject      = row.archetype.displayName + " depth " + row.depth;
            BossStats stats     = BossBalance.statsForRow(row);
            float expectedDpt   = BossBalance.expectedPlayerDamagePerTurn(row.depth);
            float fightTurns    = GameMath.bossFightTurnsForPlayerDamagePerTurn(
                    stats.effectiveHitPoints, expectedDpt);
            float lengthMin     = stats.targetFightTurns * (1f - tolerance);
            float lengthMax     = stats.targetFightTurns * (1f + tolerance);
            results.add(new RuleResult(RuleKind.BOSS_FAIR, subject + " fight length",
                    fightTurns, lengthMin, lengthMax, fightTurns >= lengthMin && fightTurns <= lengthMax,
                    "expected-loadout fight turns must land in target +/- " + Math.round(tolerance * 100f) + "%"));

            float survivalRatio = GameMath.bossSurvivalCheckRatio(
                    BossBalance.expectedPlayerEffectiveHitPoints(row.depth), stats.damagePerTurn, fightTurns);
            results.add(new RuleResult(RuleKind.BOSS_FAIR, subject + " survival ratio",
                    survivalRatio, BalanceConfig.BOSS_SURVIVAL_CHECK_RATIO_MIN,
                    BalanceConfig.BOSS_SURVIVAL_CHECK_RATIO_MAX,
                    survivalRatio >= BalanceConfig.BOSS_SURVIVAL_CHECK_RATIO_MIN
                            && survivalRatio <= BalanceConfig.BOSS_SURVIVAL_CHECK_RATIO_MAX,
                    "a no-heal player survives this fraction of the fight (row dial " + row.survivalRatio + ")"));

            boolean targetInActBand = row.targetFightTurns >= BalanceConfig.BOSS_TARGET_FIGHT_TURNS_ACT_MIN
                    && row.targetFightTurns <= BalanceConfig.BOSS_TARGET_FIGHT_TURNS_ACT_MAX;
            results.add(new RuleResult(RuleKind.BOSS_FAIR, subject + " target turns",
                    row.targetFightTurns, BalanceConfig.BOSS_TARGET_FIGHT_TURNS_ACT_MIN,
                    BalanceConfig.BOSS_TARGET_FIGHT_TURNS_ACT_MAX, targetInActBand,
                    "the row's RULE-1 dial sits inside the act band"));
        }
        return results;
    }

    /**
     * R-BOSS-VERB-CAP (OVERRIDDEN by balance-overhaul order 4: the telegraphed cap is now
     * BOSS_TELEGRAPHED_HEAVY_MAX_FRACTION 50% instead of 35%; un-telegraphed stays 25%): every derived boss
     * verb, evaluated at the boss's canonical depth, respects the
     * single-hit fairness caps — no hit over 35% (now 50% when telegraphed) of the EXPECTED player's eHP at that depth (balance-overhaul order 1;
     * was the fixed depth-1 reference eHP) (hard cap, telegraphed or not), and any
     * hit over 25% must be telegraphed a turn ahead. The band max is 25% for an un-telegraphed verb, 35%
     * for a telegraphed one; the value is the verb's fraction of reference eHP.
     */
    public static List<RuleResult> bossVerbCapResults() {
        List<RuleResult> results = new ArrayList<>();
        for (BossVerbSpec verb : BOSS_VERBS) {
            // Balance-overhaul order 6: every BossTable row the verb's archetype fights on (the Overseer at 5 AND 20).
            for (BossTable.Row row : BossTable.standard().rows()) {
                if (row.archetype != verb.archetype) continue;
                BossStats stats = BossBalance.statsForRow(row);
                int damage = stats.verbDamage(verb.dptFraction);
                float fraction = GameMath.bossSingleHitFractionOfEffectiveHitPoints(
                        damage, BossBalance.expectedPlayerEffectiveHitPoints(row.depth));
                float bandMax = verb.telegraphed
                        ? BalanceConfig.BOSS_TELEGRAPHED_HEAVY_MAX_FRACTION
                        : BalanceConfig.TELEGRAPH_MAX_UNTELEGRAPHED_HIT_FRACTION;
                results.add(new RuleResult(RuleKind.BOSS_VERB_CAP, verb.verbName + " depth " + row.depth, fraction, 0f, bandMax,
                        fraction <= bandMax,
                        String.format("derived %d dmg = %.0f%% eHP; %s cap %.0f%%", damage, fraction * 100f,
                                verb.telegraphed ? "telegraphed" : "un-telegraphed", bandMax * 100f)));
            }
        }
        return results;
    }

    /**
     * R-BOSS-REWARD: a boss's credit reward must be at least the modelled fight consumption (ammo + heal
     * value) times the risk premium — so the fight is a net-positive payday, never a resource loss for
     * progressing. Holds by construction; the tiny tolerance absorbs integer rounding of the reward.
     */
    public static List<RuleResult> bossRewardResults() {
        List<RuleResult> results = new ArrayList<>();
        float premium = BalanceConfig.BOSS_REWARD_RISK_PREMIUM;
        for (BossTable.Row row : BossTable.standard().rows()) {
            BossStats stats = BossBalance.statsForRow(row);
            float consumption = BossBalance.modelledConsumptionCredits(stats.effectiveHitPoints, row.depth);
            float ratio = consumption > 0f ? stats.creditReward / consumption : Float.POSITIVE_INFINITY;
            results.add(new RuleResult(RuleKind.BOSS_REWARD, row.archetype.displayName + " depth " + row.depth + " reward/consumption",
                    ratio, premium - 0.02f, Float.POSITIVE_INFINITY, ratio >= premium - 0.02f,
                    String.format("reward %d credits vs modelled consumption %.0f (>= %.2fx)",
                            stats.creditReward, consumption, premium)));
        }
        return results;
    }

    /**
     * R-BOSS-AMMO: the fight's ammo DEMAND (the eHP a build must burn through) must be coverable by a full
     * reserve at the boss depth PLUS the arena's placed ammo budget — a build check tests the BUILD, not
     * whether the player happened to arrive with full pockets. Coverage = (reserve + arena) / demand >= 1.
     */
    public static List<RuleResult> bossAmmoResults() {
        List<RuleResult> results = new ArrayList<>();
        for (BossTable.Row row : BossTable.standard().rows()) {
            BossStats stats = BossBalance.statsForRow(row);
            float demand       = BossBalance.modelledAmmoDemandDamage(stats.effectiveHitPoints);
            float reserveDamage = BalanceConfig.RESERVE_BANKING_FLOORS_TARGET
                    * GameMath.floorDemandAtDepth(modelFloorDemand(),
                            BalanceConfig.ENEMY_HEALTH_GROWTH, row.depth);
            float arenaDamage  = BossBalance.arenaAmmoBudgetDamage(stats.effectiveHitPoints);
            float coverage     = demand > 0f ? (reserveDamage + arenaDamage) / demand : Float.POSITIVE_INFINITY;
            results.add(new RuleResult(RuleKind.BOSS_AMMO, row.archetype.displayName + " depth " + row.depth + " ammo coverage",
                    coverage, 1f, Float.POSITIVE_INFINITY, coverage >= 1f,
                    String.format("reserve %.0f + arena %.0f vs demand %.0f damage", reserveDamage, arenaDamage, demand)));
        }
        return results;
    }

    // =====================================================================================
    // R-ROUTE-* (new-game-balancr order 7) — ROUTE ECONOMICS: the branching MAP joins the contract.
    // Orders 1-6 price FLOORS, but the player plays a JOURNEY through the route map's DAG, and that
    // macro layer is where run difficulty is actually decided. Every node type, ELITE affix and
    // MYSTERY outcome carries a priced NodeEconomics row (route/RouteEconomics); RouteEconomicsModel
    // folds a row into one comparable EV; these rules band the results:
    //   R-ROUTE-PRICED      coverage — un-priced route content can never ship (order-5 discipline).
    //   R-RISK-PREMIUM      reward must scale with priced risk: premium ratio in [1.0, 1.2].
    //   R-CALM-COST         safety is bought with tempo + loot: calm EV 10-30% BELOW combat.
    //   R-MYSTERY-EV        the "EV ~= a combat floor" claim is AUDITED, and the worst pull bounded.
    //   R-PIPS-DERIVED      the map may tempt, never lie: displayed pips == pips derived from price.
    //   R-HONEST-SAFE       a safe-looking node IS safe; scan tones partition by their priced EV.
    //   R-TRAJECTORY        the JOURNEY replaces the floor as the audited unit (3 path policies).
    //   R-ROUTE-GUARANTEES  no lane strands a run (upgrade/calm per region, pre-boss, real choices).
    // The audited depth range matches every other order-3/4 depth rule: 1..15.
    // =====================================================================================

    /**
     * The audited depth range shared by the order-7 route rules: the whole finite run (balance-overhaul order 6,
     * A5 — was the order-7 horizon 15, which left regions D and E unaudited).
     */
    private static final int ROUTE_AUDIT_MAX_DEPTH = BalanceConfig.RUN_FINAL_DEPTH;

    /**
     * The order-3 MODEL FLOOR, handed to the route model so the map is priced against the very same
     * reference encounter the floor rules use (one source — the map can never drift from the floor).
     */
    public static RouteEconomicsModel.ModelFloor routeModelFloor() {
        // Balance-overhaul order 2 (CP6): the floor's SUPPLY is no longer a model-floor input — every node
        // row is priced on its NodeSupplySpec's plan (route/RouteEconomicsModel), the same SupplyPlanner
        // formulas the generators build with. Only the roster side (demand, incoming, bounty) and the value
        // of one guaranteed box (the mean over the ammo types at depth 1) come from here.
        float boxDamage = 0f;
        for (AmmoType ammoType : AmmoType.values()) {
            boxDamage += ge.tbegvadze.toon3d.level.SupplyPlanner.boxDamageAtDepth(ammoType, 1);
        }
        float averageBoxDamage = boxDamage / AmmoType.values().length;
        float incoming = GameMath.incomingDamagePerFloor(modelFloorEnemyDamagePerTurn(),
                BalanceConfig.MODEL_FLOOR_TURNS_ENGAGED_PER_ENEMY, BalanceConfig.MODEL_FLOOR_AVOIDANCE_FACTOR);
        return new RouteEconomicsModel.ModelFloor(modelFloorDemand(), averageBoxDamage, incoming,
                modelFloorKillCreditReward());
    }

    /** The priced ledger, with its registration latched exactly once (headless — no LibGDX touched). */
    public static NodeEconomicsRegistry routeLedger() {
        return RouteRegistries.nodeEconomics();
    }

    /**
     * R-ROUTE-PRICED (COVERAGE for the map): every {@link RouteNodeType}, every registered ELITE
     * affix and every {@link MysteryOutcome} must carry a {@link NodeEconomics} row. This is the same
     * anti-"shipped unpriced" rule that fixed enemies in order 5, applied to route content: the map
     * can never again gain a node, affix or outcome the balance contract cannot see.
     */
    public static List<RuleResult> routePricedResults() {
        List<RuleResult> results = new ArrayList<>();
        NodeEconomicsRegistry ledger = routeLedger();
        for (RouteNodeType type : RouteNodeType.values()) {
            boolean priced = ledger.forNodeType(type) != null;
            results.add(new RuleResult(RuleKind.ROUTE_PRICED, "node " + type.name(),
                    priced ? 1f : 0f, 1f, 1f, priced,
                    priced ? "priced in the node EV ledger"
                           : "UNPRICED — register a NodeEconomics row in route/RouteEconomics"));
        }
        for (String affixId : RouteRegistries.affixes().ids()) {
            boolean priced = ledger.get(affixId) != null;
            results.add(new RuleResult(RuleKind.ROUTE_PRICED, "affix " + affixId,
                    priced ? 1f : 0f, 1f, 1f, priced,
                    priced ? "priced in the node EV ledger"
                           : "UNPRICED — register a NodeEconomics.affix row in route/RouteEconomics"));
        }
        for (MysteryOutcome outcome : MysteryOutcome.values()) {
            boolean priced = ledger.get(outcome.name()) != null;
            results.add(new RuleResult(RuleKind.ROUTE_PRICED, "mystery outcome " + outcome.name(),
                    priced ? 1f : 0f, 1f, 1f, priced,
                    priced ? "priced in the node EV ledger"
                           : "UNPRICED — register a NodeEconomics.mysteryOutcome row in route/RouteEconomics"));
        }
        return results;
    }

    /**
     * R-RISK-PREMIUM: for the ELITE node and for EVERY affixed variant of it, the reward premium
     * divided by the threat premium (both measured against a standard COMBAT node at the same depth)
     * must land in [1.0, 1.2] — danger pays, slightly better than fair, never free and never a sucker
     * bet. SWARM / OVERCLOCKED raise the threat, so their vaults must price up with them. Reported as
     * the WORST depth in 1..15 for each subject, so one row names the exact failing case.
     */
    public static List<RuleResult> riskPremiumResults() {
        List<RuleResult> results = new ArrayList<>();
        NodeEconomicsRegistry ledger = routeLedger();
        RouteEconomicsModel.ModelFloor floor = routeModelFloor();
        for (NodeEconomics node : ledger.allOfKind(NodeEconomics.Kind.NODE)) {
            if (node.forced() || node.budgetScale() <= 1f) {
                continue; // only DANGER nodes (threat above a standard combat floor) buy a premium
            }
            results.add(worstRiskPremium(ledger, floor, node, null));
            for (NodeEconomics affix : ledger.allOfKind(NodeEconomics.Kind.AFFIX)) {
                results.add(worstRiskPremium(ledger, floor, node, affix));
            }
        }
        return results;
    }

    /** The depth in 1..15 whose risk premium sits furthest outside the band (or nearest its edge). */
    private static RuleResult worstRiskPremium(NodeEconomicsRegistry ledger,
                                               RouteEconomicsModel.ModelFloor floor,
                                               NodeEconomics node, NodeEconomics affix) {
        String subject = node.id() + (affix == null ? "" : " + " + affix.id());
        float worstValue = Float.NaN;
        int   worstDepth = 1;
        float worstExtremity = -1f;
        for (int depth = 1; depth <= ROUTE_AUDIT_MAX_DEPTH; depth++) {
            RouteEconomicsModel.NodePrice standard = RouteEconomicsModel.standardCombat(ledger, floor, depth);
            RouteEconomicsModel.NodePrice priced   = RouteEconomicsModel.price(ledger, node, affix, floor, depth);
            float ratio = GameMath.rewardPremiumRatio(priced.rewardPowerPoints, standard.rewardPowerPoints,
                    priced.threatCost, standard.threatCost);
            float extremity = bandExtremity(ratio, BalanceConfig.ROUTE_RISK_PREMIUM_MIN,
                    BalanceConfig.ROUTE_RISK_PREMIUM_MAX);
            if (extremity > worstExtremity) {
                worstExtremity = extremity;
                worstValue    = ratio;
                worstDepth    = depth;
            }
        }
        boolean inBand = worstValue >= BalanceConfig.ROUTE_RISK_PREMIUM_MIN
                && worstValue <= BalanceConfig.ROUTE_RISK_PREMIUM_MAX;
        return new RuleResult(RuleKind.RISK_PREMIUM, subject, worstValue,
                BalanceConfig.ROUTE_RISK_PREMIUM_MIN, BalanceConfig.ROUTE_RISK_PREMIUM_MAX, inBand,
                "worst depth " + worstDepth + " — reward premium / threat premium");
    }

    /**
     * R-CALM-COST: every CALM node's total EV must sit BELOW a standard combat node's by 10-30%.
     * Safety is bought with TEMPO and LOOT — the route doc's "costs loot/tempo, never the clock"
     * invariant, now a number. Cache supply and the med-bay heal are therefore order-3 economy inputs:
     * routing calm is a real strategic spend of the heal/ammo budget, not free income on top.
     */
    public static List<RuleResult> calmCostResults() {
        List<RuleResult> results = new ArrayList<>();
        NodeEconomicsRegistry ledger = routeLedger();
        RouteEconomicsModel.ModelFloor floor = routeModelFloor();
        NodeTypeRegistry nodeTypes = RouteRegistries.nodeTypes();
        for (NodeEconomics node : ledger.allOfKind(NodeEconomics.Kind.NODE)) {
            if (node.forced() || nodeTypes.get(node.nodeType()).dangerTier() != DangerTier.CALM) {
                continue;
            }
            float worstDiscount = Float.NaN;
            int   worstDepth = 1;
            float worstExtremity = -1f;
            for (int depth = 1; depth <= ROUTE_AUDIT_MAX_DEPTH; depth++) {
                RouteEconomicsModel.NodePrice standard =
                        RouteEconomicsModel.standardCombat(ledger, floor, depth);
                RouteEconomicsModel.NodePrice priced = RouteEconomicsModel.price(ledger, node, floor, depth);
                float discount = standard.expectedValue == 0f ? 0f
                        : 1f - priced.expectedValue / standard.expectedValue;
                float extremity = bandExtremity(discount, BalanceConfig.ROUTE_CALM_EV_DISCOUNT_MIN,
                        BalanceConfig.ROUTE_CALM_EV_DISCOUNT_MAX);
                if (extremity > worstExtremity) {
                    worstExtremity = extremity;
                    worstDiscount = discount;
                    worstDepth    = depth;
                }
            }
            boolean inBand = worstDiscount >= BalanceConfig.ROUTE_CALM_EV_DISCOUNT_MIN
                    && worstDiscount <= BalanceConfig.ROUTE_CALM_EV_DISCOUNT_MAX;
            results.add(new RuleResult(RuleKind.CALM_COST, node.id() + " EV discount", worstDiscount,
                    BalanceConfig.ROUTE_CALM_EV_DISCOUNT_MIN, BalanceConfig.ROUTE_CALM_EV_DISCOUNT_MAX,
                    inBand, "worst depth " + worstDepth + " — 1 - EV(calm)/EV(combat)"));
        }
        return results;
    }

    /**
     * R-MYSTERY-EV: the WEIGHTED EV of the mystery outcome table must sit within ±15% of a standard
     * combat node at every audited depth (the order-9 doc ASSERTED this; now it is audited and can no
     * longer drift when any payoff or upstream re-tune moves), and the worst outcome (MALFUNCTION)
     * must stay "bad but survivable" as arithmetic: its expected NET resource loss may not exceed one
     * floor's maximum modelled drain times {@code ROUTE_MYSTERY_WORST_LOSS_FLOOR_MULTIPLIER}.
     */
    public static List<RuleResult> mysteryExpectedValueResults() {
        List<RuleResult> results = new ArrayList<>();
        NodeEconomicsRegistry ledger = routeLedger();
        RouteEconomicsModel.ModelFloor floor = routeModelFloor();
        NodeEconomics mystery = ledger.forNodeType(RouteNodeType.MYSTERY);

        float worstDeviation = 0f;
        int   worstDepth = 1;
        for (int depth = 1; depth <= ROUTE_AUDIT_MAX_DEPTH; depth++) {
            RouteEconomicsModel.NodePrice standard = RouteEconomicsModel.standardCombat(ledger, floor, depth);
            RouteEconomicsModel.NodePrice table    = RouteEconomicsModel.price(ledger, mystery, floor, depth);
            float deviation = standard.expectedValue == 0f ? 0f
                    : Math.abs(table.expectedValue / standard.expectedValue - 1f);
            if (deviation > worstDeviation) {
                worstDeviation = deviation;
                worstDepth     = depth;
            }
        }
        results.add(new RuleResult(RuleKind.MYSTERY_EV, "weighted table vs combat", worstDeviation,
                0f, BalanceConfig.ROUTE_MYSTERY_EV_TOLERANCE,
                worstDeviation <= BalanceConfig.ROUTE_MYSTERY_EV_TOLERANCE,
                "worst depth " + worstDepth + " — |EV(table)/EV(combat) - 1|"));

        // Worst-outcome bound, measured at depth 1 where a loss hurts a thin run the most.
        float maximumLoss = oneFloorMaximumResourceLossPowerPoints()
                * BalanceConfig.ROUTE_MYSTERY_WORST_LOSS_FLOOR_MULTIPLIER;
        for (NodeEconomics outcome : ledger.allOfKind(NodeEconomics.Kind.MYSTERY_OUTCOME)) {
            float worstLoss = 0f;
            int   lossDepth = 1;
            for (int depth = 1; depth <= ROUTE_AUDIT_MAX_DEPTH; depth++) {
                RouteEconomicsModel.NodePrice priced =
                        RouteEconomicsModel.price(ledger, outcome, floor, depth);
                float loss = -priced.resourceDeltaPowerPoints; // positive == a net drain
                if (loss > worstLoss) {
                    worstLoss = loss;
                    lossDepth = depth;
                }
            }
            results.add(new RuleResult(RuleKind.MYSTERY_EV, "worst-case loss " + outcome.id(), worstLoss,
                    0f, maximumLoss, worstLoss <= maximumLoss,
                    "worst depth " + lossDepth + " — net resource loss in power points"));
        }
        return results;
    }

    /**
     * One floor's MAXIMUM modelled resource drain in power points: the order-3 heal net-drain band
     * ceiling plus the share of a floor's ammo demand the scarcity band leaves uncovered. This is the
     * "one bad floor" yardstick R-MYSTERY-EV measures the worst pull against.
     */
    public static float oneFloorMaximumResourceLossPowerPoints() {
        float healLoss = BalanceConfig.HEAL_NET_DRAIN_FRACTION_MAX * BalanceConfig.REFERENCE_PLAYER_EHP
                / BalanceConfig.SHOP_HEAL_HP_PER_POWER_POINT;
        float ammoLoss = (1f - BalanceConfig.SCARCITY_RATIO_FLOOR_MIN) * modelFloorDemand()
                / BalanceConfig.SHOP_AMMO_DAMAGE_PER_POWER_POINT;
        return healLoss + ammoLoss;
    }

    /**
     * R-PIPS-DERIVED: the overlay's risk pips are the player's ONLY information for a route decision,
     * so a hand-assigned {@link DangerTier} that drifts from the priced threat makes the map LIE and
     * informed choice collapses. Every node type's displayed tier must equal the tier DERIVED from its
     * threat ratio (GameMath.derivedDangerTierIndex) against a standard combat node.
     */
    public static List<RuleResult> derivedPipResults() {
        List<RuleResult> results = new ArrayList<>();
        NodeEconomicsRegistry ledger = routeLedger();
        RouteEconomicsModel.ModelFloor floor = routeModelFloor();
        NodeTypeRegistry nodeTypes = RouteRegistries.nodeTypes();
        for (NodeEconomics node : ledger.allOfKind(NodeEconomics.Kind.NODE)) {
            DangerTier declared = nodeTypes.get(node.nodeType()).dangerTier();
            int derivedIndex = derivedDangerTierIndexOf(ledger, floor, node);
            DangerTier derived = DangerTier.values()[derivedIndex];
            boolean matches = declared == derived;
            results.add(new RuleResult(RuleKind.PIPS_DERIVED, node.id() + " danger tier",
                    derivedIndex, declared.ordinal(), declared.ordinal(), matches,
                    "declared " + declared + " vs derived " + derived + " (threat ratio "
                            + String.format("%.2f", routeThreatRatio(ledger, floor, node)) + "x combat)"));
        }
        return results;
    }

    /** The DangerTier index a node's PRICE implies (shared by the rule and BalanceReport's table). */
    public static int derivedDangerTierIndexOf(NodeEconomicsRegistry ledger,
                                               RouteEconomicsModel.ModelFloor floor, NodeEconomics node) {
        return GameMath.derivedDangerTierIndex(routeThreatRatio(ledger, floor, node), node.forced(),
                node.hiddenOutcomeTable(), BalanceConfig.ROUTE_PIP_CALM_MAX_THREAT_RATIO,
                BalanceConfig.ROUTE_PIP_STANDARD_MAX_THREAT_RATIO);
    }

    /** A node's threat as a multiple of a standard combat node's, at the reference audit depth. */
    public static float routeThreatRatio(NodeEconomicsRegistry ledger,
                                         RouteEconomicsModel.ModelFloor floor, NodeEconomics node) {
        return RouteEconomicsModel.threatRatioVsStandardCombat(ledger, node, floor, 1);
    }

    /**
     * R-HONEST-SAFE: the honest-safe-node design rule as an enforced bound plus a scan-tone check.
     * <ol>
     *   <li>A node whose icon promises safety (CACHE / REST) may face at most
     *       {@code ROUTE_HONEST_SAFE_MAX_THREAT_RATIO} of a combat floor's Threat Points — a "med-bay
     *       ambush" must be a DIFFERENT node (a MYSTERY), never a REST wearing REST's face.</li>
     *   <li>The MYSTERY scan-tone buckets must partition consistently with their priced values:
     *       REWARD-RICH really is the richest bucket by EV, and HIGH RISK really is the most dangerous
     *       by threat — so a scan narrows honestly instead of flavour-texting.</li>
     * </ol>
     */
    public static List<RuleResult> honestSafeResults() {
        List<RuleResult> results = new ArrayList<>();
        NodeEconomicsRegistry ledger = routeLedger();
        RouteEconomicsModel.ModelFloor floor = routeModelFloor();

        for (RouteNodeType type : new RouteNodeType[]{RouteNodeType.CACHE, RouteNodeType.REST}) {
            NodeEconomics node = ledger.forNodeType(type);
            float ratio = routeThreatRatio(ledger, floor, node);
            results.add(new RuleResult(RuleKind.HONEST_SAFE, node.id() + " threat ratio", ratio,
                    0f, BalanceConfig.ROUTE_HONEST_SAFE_MAX_THREAT_RATIO,
                    ratio <= BalanceConfig.ROUTE_HONEST_SAFE_MAX_THREAT_RATIO,
                    "a node whose icon promises safety must BE safe"));
        }

        float richValue  = scanToneMean(ledger, floor, MysteryOutcome.ScanTone.REWARD_RICH, true);
        float quietValue = scanToneMean(ledger, floor, MysteryOutcome.ScanTone.QUIET, true);
        float riskValue  = scanToneMean(ledger, floor, MysteryOutcome.ScanTone.HIGH_RISK, true);
        results.add(new RuleResult(RuleKind.HONEST_SAFE, "scan tone REWARD-RICH is the richest",
                richValue, Math.max(quietValue, riskValue), Float.POSITIVE_INFINITY,
                richValue > quietValue && richValue > riskValue,
                String.format("EV rich %.1f vs quiet %.1f, high-risk %.1f", richValue, quietValue, riskValue)));

        float richThreat  = scanToneMean(ledger, floor, MysteryOutcome.ScanTone.REWARD_RICH, false);
        float quietThreat = scanToneMean(ledger, floor, MysteryOutcome.ScanTone.QUIET, false);
        float riskThreat  = scanToneMean(ledger, floor, MysteryOutcome.ScanTone.HIGH_RISK, false);
        results.add(new RuleResult(RuleKind.HONEST_SAFE, "scan tone HIGH RISK is the most dangerous",
                riskThreat, Math.max(quietThreat, richThreat), Float.POSITIVE_INFINITY,
                riskThreat > quietThreat && riskThreat > richThreat,
                String.format("threat high-risk %.0f vs quiet %.0f, rich %.0f",
                        riskThreat, quietThreat, richThreat)));
        return results;
    }

    /** Mean EV (or mean threat) of the mystery outcomes sharing one scan tone, at the audit depth. */
    private static float scanToneMean(NodeEconomicsRegistry ledger, RouteEconomicsModel.ModelFloor floor,
                                      MysteryOutcome.ScanTone tone, boolean expectedValue) {
        float total = 0f;
        int   count = 0;
        for (NodeEconomics outcome : ledger.allOfKind(NodeEconomics.Kind.MYSTERY_OUTCOME)) {
            if (!tone.name().equals(outcome.scanToneId())) {
                continue;
            }
            RouteEconomicsModel.NodePrice priced = RouteEconomicsModel.price(ledger, outcome, floor, 1);
            total += expectedValue ? priced.expectedValue : priced.threatCost;
            count++;
        }
        return count == 0 ? 0f : total / count;
    }

    // --- The trajectory + reachability audits: real maps, walked headlessly. ----------------

    /** A freshly generated route map for one seed, using the real generator and the real registries. */
    private static RouteMap generateAuditMap(long seed) {
        RouteMapGenerator generator = new RouteMapGenerator(RouteRegistries.nodeTypes(),
                RouteRegistries.generators());
        generator.setEliteAffixPool(RouteRegistries.affixes().elitePool());
        return generator.generate(seed, RegionPlan.defaultPlan());
    }

    /**
     * R-TRAJECTORY: the JOURNEY is the audited unit. Real maps are generated for
     * {@code ROUTE_TRAJECTORY_SEED_COUNT} seeds and walked under three deterministic policies
     * (SAFEST / DEADLIEST / BALANCED); at every region boundary the cumulative order-3/4 quantities —
     * scarcity S, per-floor net HP drain, XP pace and depth coupling AT THE LEVEL THE ROUTE ACTUALLY
     * BOUGHT — are measured over the nodes actually visited, each priced by its own ledger row rather
     * than by the combat-floor average. All three policies must stay in band: SAFEST may ride the
     * generous edge and DEADLIEST the starved edge, and those ENDS are the game's real difficulty
     * range, but both must stay fair.
     */
    public static List<RuleResult> trajectoryResults() {
        List<RuleResult> results = new ArrayList<>();
        NodeEconomicsRegistry ledger = routeLedger();
        RouteEconomicsModel.ModelFloor floor = routeModelFloor();
        for (RouteEconomicsModel.PathPolicy policy : RouteEconomicsModel.PathPolicy.values()) {
            float worstScarcity = Float.NaN, worstDrain = Float.NaN;
            float worstPace = Float.NaN, worstCoupling = Float.NaN;
            float scarcityExtremity = -1f, drainExtremity = -1f, paceExtremity = -1f, couplingExtremity = -1f;
            for (long seed = 0; seed < BalanceConfig.ROUTE_TRAJECTORY_SEED_COUNT; seed++) {
                RouteMap map = generateAuditMap(seed);
                for (RouteEconomicsModel.TrajectorySample sample
                        : RouteEconomicsModel.walk(map, policy, ledger, floor, ROUTE_AUDIT_MAX_DEPTH)) {
                    float extremity = bandExtremity(sample.scarcityRatio,
                            BalanceConfig.ROUTE_TRAJECTORY_SCARCITY_MIN, BalanceConfig.ROUTE_TRAJECTORY_SCARCITY_MAX);
                    if (extremity > scarcityExtremity) {
                        scarcityExtremity = extremity;
                        worstScarcity    = sample.scarcityRatio;
                    }
                    extremity = bandExtremity(sample.netDrainFraction,
                            BalanceConfig.ROUTE_TRAJECTORY_DRAIN_MIN, BalanceConfig.ROUTE_TRAJECTORY_DRAIN_MAX);
                    if (extremity > drainExtremity) {
                        drainExtremity = extremity;
                        worstDrain    = sample.netDrainFraction;
                    }
                    extremity = bandExtremity(sample.experiencePace,
                            BalanceConfig.ROUTE_TRAJECTORY_XP_PACE_MIN, BalanceConfig.ROUTE_TRAJECTORY_XP_PACE_MAX);
                    if (extremity > paceExtremity) {
                        paceExtremity = extremity;
                        worstPace    = sample.experiencePace;
                    }
                    extremity = bandExtremity(sample.couplingRatio,
                            BalanceConfig.ROUTE_TRAJECTORY_COUPLING_MIN, BalanceConfig.ROUTE_TRAJECTORY_COUPLING_MAX);
                    if (extremity > couplingExtremity) {
                        couplingExtremity = extremity;
                        worstCoupling    = sample.couplingRatio;
                    }
                }
            }
            String policyName = policy.name();
            results.add(bandedResult(RuleKind.TRAJECTORY, policyName + " cumulative scarcity S",
                    worstScarcity, BalanceConfig.ROUTE_TRAJECTORY_SCARCITY_MIN,
                    BalanceConfig.ROUTE_TRAJECTORY_SCARCITY_MAX, "over the nodes actually visited"));
            results.add(bandedResult(RuleKind.TRAJECTORY, policyName + " net HP drain / floor",
                    worstDrain, BalanceConfig.ROUTE_TRAJECTORY_DRAIN_MIN,
                    BalanceConfig.ROUTE_TRAJECTORY_DRAIN_MAX, "fraction of eHP lost per visited floor"));
            results.add(bandedResult(RuleKind.TRAJECTORY, policyName + " XP pace",
                    worstPace, BalanceConfig.ROUTE_TRAJECTORY_XP_PACE_MIN,
                    BalanceConfig.ROUTE_TRAJECTORY_XP_PACE_MAX, "banked XP / XP for the expected level"));
            results.add(bandedResult(RuleKind.TRAJECTORY, policyName + " depth coupling",
                    worstCoupling, BalanceConfig.ROUTE_TRAJECTORY_COUPLING_MIN,
                    BalanceConfig.ROUTE_TRAJECTORY_COUPLING_MAX, "measured at the level the route bought"));
        }
        return results;
    }

    /**
     * R-ROUTE-GUARANTEES: over the same seed sweep, EVERY generated map must satisfy the reachability
     * guarantees the generator's post-pass enforces — from every node an upgrade-bearing node and a
     * calm node are still reachable inside the region, the layer before each boss offers reachable
     * provisioning, and every selectable layer offers at least two distinct node types. A branching
     * map must never strand a floor-level promise on the lane not taken.
     */
    public static List<RuleResult> routeGuaranteeResults() {
        List<String> violations = new ArrayList<>();
        for (long seed = 0; seed < BalanceConfig.ROUTE_TRAJECTORY_SEED_COUNT; seed++) {
            for (String violation : RouteEconomicsModel.guaranteeViolations(generateAuditMap(seed))) {
                violations.add("seed " + seed + ": " + violation);
            }
        }
        List<RuleResult> results = new ArrayList<>();
        String detail = violations.isEmpty()
                ? BalanceConfig.ROUTE_TRAJECTORY_SEED_COUNT + " seeds: every lane reaches an upgrade, "
                        + "a calm node and pre-boss provisioning; every layer offers a real choice"
                : violations.size() + " violation(s), first: " + violations.get(0);
        results.add(new RuleResult(RuleKind.ROUTE_GUARANTEES, "reachability over "
                + BalanceConfig.ROUTE_TRAJECTORY_SEED_COUNT + " seeds",
                violations.size(), 0f, 0f, violations.isEmpty(), detail));
        return results;
    }

    /**
     * How EXTREME a reading is: its distance from the band's centre. Used to pick which depth (or which
     * seed/sample) a rule reports, because the reading furthest from the centre is out of band exactly
     * when ANY reading is — so one row can carry both the verdict and the most informative number
     * (a plain "is it outside?" distance reports 0 for every in-band reading and would print whichever
     * sample happened to come first).
     */
    private static float bandExtremity(float value, float bandMinimum, float bandMaximum) {
        if (Float.isNaN(value)) {
            return Float.MAX_VALUE;
        }
        float bandCentre = (bandMinimum + bandMaximum) / 2f;
        if (Float.isInfinite(bandCentre)) {
            return Math.abs(value - bandMinimum); // one-sided band: distance from its only edge
        }
        return Math.abs(value - bandCentre);
    }

    /** A RuleResult whose verdict is simply "is the value inside the band?". */
    private static RuleResult bandedResult(RuleKind kind, String subject, float value,
                                           float bandMinimum, float bandMaximum, String detail) {
        boolean inBand = !Float.isNaN(value) && value >= bandMinimum && value <= bandMaximum;
        return new RuleResult(kind, subject, value, bandMinimum, bandMaximum, inBand, detail);
    }

    // =====================================================================================
    // Reflection helpers (structural rules only — never used in the game loop).
    // =====================================================================================

    private static boolean isComparableConstant(Field field) {
        int modifiers = field.getModifiers();
        if (!Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)) return false;
        Class<?> type = field.getType();
        return type == int.class || type == float.class || type == boolean.class;
    }

    private static Field findBalanceConfigField(String fieldName) {
        try {
            return BalanceConfig.class.getDeclaredField(fieldName);
        } catch (NoSuchFieldException noSuchField) {
            return null;
        }
    }

    private static float readConstantAsFloat(Field field) {
        try {
            Class<?> type = field.getType();
            if (type == int.class)     return field.getInt(null);
            if (type == float.class)   return field.getFloat(null);
            if (type == boolean.class) return field.getBoolean(null) ? 1f : 0f;
            throw new IllegalArgumentException("not a comparable constant: " + field);
        } catch (IllegalAccessException illegalAccess) {
            throw new IllegalStateException("cannot read public constant " + field, illegalAccess);
        }
    }

    private static boolean readBooleanConstant(Field field) {
        try {
            return field.getBoolean(null);
        } catch (IllegalAccessException illegalAccess) {
            throw new IllegalStateException("cannot read public constant " + field, illegalAccess);
        }
    }

    // =====================================================================================
    // R-LADDER + R-LADDER-AFFORD (balance-overhaul order 1) — THE POWER LADDER.
    // Every floor has a THREAT LEVEL = depth; weapon damage and enemy HP climb the same fitted compound
    // ladder; a weapon under the floor's level takes the R3 penalty. All six sub-checks read the ONE
    // expected-player model (GameMath.expectedPlayerAtDepth / expectedPlayer), for every depth
    // 1..RUN_FINAL_DEPTH and every non-boss role's reference archetype. Turns are CONTINUOUS
    // (GameMath.ladderTurnsToKill) so a ratio is never hidden by one-hit rounding.
    //   L1 ON-CURVE FLAT  on-curve TTK and TTD within +/-LADDER_ON_CURVE_TOLERANCE of depth 1.
    //   L2 LAG PUNISHED   weapon level d-2 -> TTK >= 1.5x; d-4 -> >= 2.5x; start weapon (L1 COMMON) at
    //                     d >= 5 -> >= 3.0x on-curve.
    //   L3 AHEAD BOUNDED  weapon level d+2 -> TTK >= 0.75x on-curve.
    //   L4 LEVEL FELT     on floor d, level d vs d-1: DPT gain >= +25%.
    //   L5 RARITY FELT    each tier step >= +8% DPT; LEGENDARY >= +40% vs COMMON (equal level).
    //   L6 VITALITY FELT  character level d-3 -> TTD <= 0.80x on-curve.
    // Replaces R-DEPTH (depth coupling) and R-GEARGATE (region gear step) — see the authority doc's
    // override record.
    // =====================================================================================

    /** The reference archetype each non-boss role is proven on (data, not a switch). */
    private static final Map<EnemyRole, EnemyType> LADDER_ROLE_REFERENCE = buildLadderRoleReference();

    private static Map<EnemyRole, EnemyType> buildLadderRoleReference() {
        Map<EnemyRole, EnemyType> references = new EnumMap<>(EnemyRole.class);
        references.put(EnemyRole.CHAFF,      EnemyType.GORE_BITER);
        references.put(EnemyRole.SOLDIER,    EnemyType.VOID_SHROUD);
        references.put(EnemyRole.BRUISER,    EnemyType.SHELL_BRUTE);
        references.put(EnemyRole.MINI_ELITE, EnemyType.IRON_STALKER);
        return Collections.unmodifiableMap(references);
    }

    /** The R-LADDER reference archetype for a role, or null for BOSS (bosses follow SECTION 14). */
    public static EnemyType ladderReferenceArchetype(EnemyRole role) {
        return LADDER_ROLE_REFERENCE.get(role);
    }

    /** Continuous hits the given player needs to kill the archetype at a depth (R-LADDER TTK). */
    public static float ladderTurnsToKill(EnemyType enemyType, int depth, ExpectedPlayer player) {
        // R-LADDER's reference is the Assault Rifle vs a NEUTRAL target (balance-overhaul order 3 keeps
        // it matchup-free): read the neutral eHP, never the trait-priced one.
        float enemyEffectiveHitPoints = GameMath.enemyHealthAtDepth(enemyType.neutralEffectiveHitPoints(), depth);
        return GameMath.ladderTurnsToKill(enemyEffectiveHitPoints, player.referenceHitDamage);
    }

    /** Continuous ordinary hits of the archetype the given player survives at a depth (R-LADDER TTD). */
    public static float ladderTurnsToDie(EnemyType enemyType, int depth, ExpectedPlayer player) {
        float enemyHit = GameMath.enemyDamageAtDepth(enemyType.attackDamage(), depth);
        return GameMath.ladderTurnsToKill(player.effectiveHitPoints, enemyHit);
    }

    /** The start weapon's player on a floor: weapon L1, COMMON, but the on-curve character level. */
    public static ExpectedPlayer ladderStartWeaponPlayer(int depth) {
        return GameMath.expectedPlayer(depth, 1, GameMath.rarityDamageMultiplier(0),
                GameMath.expectedCharacterLevelAtDepth(depth));
    }

    /** An on-curve player whose weapon sits {@code levelOffset} levels off the floor (rarity unchanged). */
    public static ExpectedPlayer ladderWeaponOffsetPlayer(int depth, int levelOffset) {
        int weaponLevel = Math.max(1, Math.min(BalanceConfig.RUN_FINAL_DEPTH + 2, depth + levelOffset));
        return GameMath.expectedPlayer(depth, weaponLevel, GameMath.expectedRarityMultiplierAtDepth(depth),
                GameMath.expectedCharacterLevelAtDepth(depth));
    }

    private static RuleResult ladderMinimum(String subject, float value, float minimum, String detail) {
        return new RuleResult(RuleKind.LADDER, subject, value, minimum, Float.POSITIVE_INFINITY,
                value >= minimum, detail);
    }

    private static RuleResult ladderMaximum(String subject, float value, float maximum, String detail) {
        return new RuleResult(RuleKind.LADDER, subject, value, 0f, maximum, value <= maximum, detail);
    }

    /** R-LADDER: the six sub-checks at every depth 1..RUN_FINAL_DEPTH for every non-boss role. */
    public static List<RuleResult> ladderResults() {
        List<RuleResult> results = new ArrayList<>();
        float tolerance = BalanceConfig.LADDER_ON_CURVE_TOLERANCE;
        ExpectedPlayer depthOnePlayer = GameMath.expectedPlayerAtDepth(1);
        for (Map.Entry<EnemyRole, EnemyType> entry : LADDER_ROLE_REFERENCE.entrySet()) {
            EnemyRole role = entry.getKey();
            EnemyType reference = entry.getValue();
            float depthOneKill = ladderTurnsToKill(reference, 1, depthOnePlayer);
            float depthOneDie  = ladderTurnsToDie(reference, 1, depthOnePlayer);
            for (int depth = 1; depth <= BalanceConfig.RUN_FINAL_DEPTH; depth++) {
                String where = role + " (" + reference.displayName() + ") depth " + depth;
                ExpectedPlayer onCurve = GameMath.expectedPlayerAtDepth(depth);
                float onCurveKill = ladderTurnsToKill(reference, depth, onCurve);
                float onCurveDie  = ladderTurnsToDie(reference, depth, onCurve);

                // L1 — on-curve fights feel the same length at every depth.
                float killDrift = onCurveKill / depthOneKill;
                float dieDrift  = onCurveDie / depthOneDie;
                results.add(new RuleResult(RuleKind.LADDER, "L1 on-curve TTK " + where, killDrift,
                        1f - tolerance, 1f + tolerance,
                        killDrift >= 1f - tolerance && killDrift <= 1f + tolerance,
                        String.format("%.2f hits vs %.2f at depth 1", onCurveKill, depthOneKill)));
                results.add(new RuleResult(RuleKind.LADDER, "L1 on-curve TTD " + where, dieDrift,
                        1f - tolerance, 1f + tolerance,
                        dieDrift >= 1f - tolerance && dieDrift <= 1f + tolerance,
                        String.format("%.2f hits vs %.2f at depth 1", onCurveDie, depthOneDie)));

                // L2 — falling behind is punished.
                if (depth - 2 >= 1) {
                    float ratio = ladderTurnsToKill(reference, depth, ladderWeaponOffsetPlayer(depth, -2)) / onCurveKill;
                    results.add(ladderMinimum("L2 lag-2 TTK " + where, ratio,
                            BalanceConfig.LADDER_LAG_TWO_MIN_TTK_RATIO, "weapon level d-2 vs on-curve"));
                }
                if (depth - 4 >= 1) {
                    float ratio = ladderTurnsToKill(reference, depth, ladderWeaponOffsetPlayer(depth, -4)) / onCurveKill;
                    results.add(ladderMinimum("L2 lag-4 TTK " + where, ratio,
                            BalanceConfig.LADDER_LAG_FOUR_MIN_TTK_RATIO, "weapon level d-4 vs on-curve"));
                }
                if (depth >= BalanceConfig.LADDER_START_WEAPON_FROM_DEPTH) {
                    float ratio = ladderTurnsToKill(reference, depth, ladderStartWeaponPlayer(depth)) / onCurveKill;
                    results.add(ladderMinimum("L2 start-weapon TTK " + where, ratio,
                            BalanceConfig.LADDER_START_WEAPON_MIN_TTK_RATIO, "L1 COMMON vs on-curve"));
                }

                // L3 — running ahead is bounded.
                float aheadRatio = ladderTurnsToKill(reference, depth, ladderWeaponOffsetPlayer(depth, 2)) / onCurveKill;
                results.add(ladderMinimum("L3 ahead-2 TTK " + where, aheadRatio,
                        BalanceConfig.LADDER_AHEAD_TWO_MIN_TTK_RATIO, "weapon level d+2 vs on-curve"));

                // L6 — character level is felt in survival.
                int laggingLevel = GameMath.expectedCharacterLevelAtDepth(depth) - BalanceConfig.LADDER_VITALITY_LAG_LEVELS;
                if (laggingLevel >= 1) {
                    ExpectedPlayer lagging = GameMath.expectedPlayer(depth, depth,
                            GameMath.expectedRarityMultiplierAtDepth(depth), laggingLevel);
                    float ratio = ladderTurnsToDie(reference, depth, lagging) / onCurveDie;
                    results.add(ladderMaximum("L6 vitality lag TTD " + where, ratio,
                            BalanceConfig.LADDER_VITALITY_LAG_MAX_TTD_RATIO, "character level " + laggingLevel
                                    + " vs " + GameMath.expectedCharacterLevelAtDepth(depth)));
                }
            }
        }

        // L4 — a level is felt: on floor d, level d vs level d-1 (role-independent: pure weapon DPT).
        for (int depth = 2; depth <= BalanceConfig.RUN_FINAL_DEPTH; depth++) {
            float gain = GameMath.expectedPlayerAtDepth(depth).damagePerTurn
                    / ladderWeaponOffsetPlayer(depth, -1).damagePerTurn - 1f;
            results.add(ladderMinimum("L4 level felt depth " + depth, gain,
                    BalanceConfig.LADDER_LEVEL_FELT_MIN_GAIN, "level d vs d-1 on floor d"));
        }

        // L5 — rarity is felt (equal level; abilities excluded).
        float[] rarity = BalanceConfig.RARITY_DAMAGE_MULTIPLIER;
        for (int tier = 1; tier < rarity.length; tier++) {
            float gain = GameMath.rarityDamageMultiplier(tier) / GameMath.rarityDamageMultiplier(tier - 1) - 1f;
            results.add(ladderMinimum("L5 rarity step tier " + (tier - 1) + "->" + tier, gain,
                    BalanceConfig.LADDER_RARITY_STEP_MIN_GAIN, "equal level, abilities excluded"));
        }
        float legendaryGain = GameMath.rarityDamageMultiplier(rarity.length - 1) / GameMath.rarityDamageMultiplier(0) - 1f;
        results.add(ladderMinimum("L5 LEGENDARY vs COMMON", legendaryGain,
                BalanceConfig.LADDER_LEGENDARY_MIN_GAIN, "equal level, abilities excluded"));
        return results;
    }

    /** The shop's price for the LEVEL UP rung at a depth (the one price DefaultShopOfferSource charges). */
    public static int ladderLevelUpPrice(int depth) {
        return GameMath.shopPrice(BalanceConfig.LADDER_LEVEL_UP_POWER_POINTS,
                BalanceConfig.SHOP_CREDITS_PER_POWER_POINT, depth, BalanceConfig.SHOP_DEPTH_PRICE_SCALE);
    }

    /** Modelled credit income of one COMBAT floor at a depth: depth-scaled kill bounties + credit chips. */
    public static float combatFloorCreditIncome(int depth) {
        return modelFloorKillCreditReward() * (1f + BalanceConfig.CREDIT_DEPTH_SCALE * (Math.max(1, depth) - 1))
                + chipIncomePerFloor();
    }

    /** R-LADDER-AFFORD: the level-up rung costs <= LADDER_AFFORD_FRACTION of one combat floor's income. */
    public static List<RuleResult> ladderAffordResults() {
        List<RuleResult> results = new ArrayList<>();
        for (int depth = 1; depth <= BalanceConfig.RUN_FINAL_DEPTH; depth++) {
            float income = combatFloorCreditIncome(depth);
            float fraction = income <= 0f ? Float.POSITIVE_INFINITY : ladderLevelUpPrice(depth) / income;
            results.add(new RuleResult(RuleKind.LADDER_AFFORD, "level-up rung depth " + depth, fraction,
                    0f, BalanceConfig.LADDER_AFFORD_FRACTION, fraction <= BalanceConfig.LADDER_AFFORD_FRACTION,
                    "price " + ladderLevelUpPrice(depth) + " vs one combat floor " + Math.round(income)));
        }
        return results;
    }


    // =====================================================================================
    // R-SUPPLY (balance-overhaul order 2) — the SUPPLY PLANNER, checked on its own.
    // Every registered NodeSupplySpec x SUPPLY_AUDIT_DEPTHS x SUPPLY_AUDIT_SEED_COUNT seeds is planned
    // against a real encounter roster and placed on a SYNTHETIC floor (eight rooms walking away from the
    // start, the last behind a keycard), so the planner's own rules — heal floor, half-early, tracking,
    // spread, carriers — are proven independently of any generator. The GENERATOR sweep (every
    // generator x node type x depth) lives in supplySweepResults / densitySweepResults.
    // =====================================================================================

    /** Rooms on the synthetic audit floor (room index = walk order from the start). */
    private static final int SYNTHETIC_ROOM_COUNT       = 8;
    /** Ground slots per synthetic room. */
    private static final int SYNTHETIC_SLOTS_PER_ROOM   = 14;
    /** Walk tiles between consecutive synthetic rooms. */
    private static final int SYNTHETIC_ROOM_SPACING     = 7;

    /**
     * R-SUPPLY "reserve banks" (balance-overhaul order 3, A-1): every ammo type's FULL reserve banks its
     * AmmoType.getBankingFloorsTarget() floors of the model floor's demand (GameMath.reserveBankingFloors),
     * within AMMO_BANKING_FLOORS_TOLERANCE — the generalist's bullets ~1.0, shells and cells ~1.5.
     */
    public static List<RuleResult> ammoBankingResults() {
        List<RuleResult> results = new ArrayList<>();
        float demand = modelFloorDemand();
        for (ScarcityRowSpec row : SCARCITY_ROWS) {
            float banked = GameMath.reserveBankingFloors(row.reserveCap, row.damagePerUnit, demand);
            float target = row.ammoType.getBankingFloorsTarget();
            float low  = target - BalanceConfig.AMMO_BANKING_FLOORS_TOLERANCE;
            float high = target + BalanceConfig.AMMO_BANKING_FLOORS_TOLERANCE;
            results.add(new RuleResult(RuleKind.SUPPLY, "reserve banks " + row.ammoType.name(), banked, low, high,
                    banked >= low && banked <= high,
                    String.format("cap %d x %.0f dmg / %.0f model-floor demand (target %.1f floors)",
                            row.reserveCap, row.damagePerUnit, demand, target)));
        }
        return results;
    }

    /** R-SUPPLY (planner level): every spec x audit depth, worst case over the audit seeds. */
    public static List<RuleResult> supplyPlannerResults() {
        List<RuleResult> results = new ArrayList<>();
        List<ge.tbegvadze.toon3d.level.SupplySlot> slots = syntheticSupplyFloor();
        int halfDistance = syntheticHalfDistance(slots);
        int anchorRegion = SYNTHETIC_ROOM_COUNT - 2;
        for (ge.tbegvadze.toon3d.route.NodeSupplySpec spec : RouteRegistries.nodeSupplySpecs().all()) {
            for (int depth : BalanceConfig.SUPPLY_AUDIT_DEPTHS) {
                SupplyAuditAccumulator accumulator = new SupplyAuditAccumulator(spec, depth);
                for (int seedIndex = 0; seedIndex < BalanceConfig.SUPPLY_AUDIT_SEED_COUNT; seedIndex++) {
                    long seed = GameMath.floorSeed(0x5_0991L + seedIndex, depth);
                    List<EnemyType> roster = syntheticRoster(spec, depth, seed);
                    int bossEffectiveHitPoints = spec.bossArenaAmmo()
                            ? BossBalance.statsForDepth(depth).effectiveHitPoints : 0;
                    ge.tbegvadze.toon3d.level.SupplyPlan plan = ge.tbegvadze.toon3d.level.SupplyPlanner.plan(
                            new ge.tbegvadze.toon3d.level.SupplyRequest(depth, spec, roster,
                                    GameMath.expectedPlayerAtDepth(depth), null, seed, false, bossEffectiveHitPoints));
                    List<Integer> carriers = new ArrayList<>();
                    for (int index = 0; index < roster.size(); index++) carriers.add(index);
                    ge.tbegvadze.toon3d.level.SupplyPlacement placement = ge.tbegvadze.toon3d.level.SupplyPlanner
                            .place(plan, slots, carriers, anchorRegion, -1, halfDistance, seed);
                    accumulator.add(plan, placement);
                }
                accumulator.emit(results, "plan ");
            }
        }
        return results;
    }

    /** A roster for the planner-level audit: what the encounter planner fields for this spec and depth. */
    static List<EnemyType> syntheticRoster(ge.tbegvadze.toon3d.route.NodeSupplySpec spec, int depth, long seed) {
        if (spec.encounterKind() == ge.tbegvadze.toon3d.route.NodeSupplySpec.EncounterKind.NONE) {
            return Collections.emptyList();
        }
        return new ge.tbegvadze.toon3d.level.EncounterBudgetPlanner(depth, new java.util.Random(seed),
                spec.threatScale()).plan().enemies();
    }

    /** Eight rooms walking away from the start plus a corridor run; the last room sits behind a keycard. */
    private static List<ge.tbegvadze.toon3d.level.SupplySlot> syntheticSupplyFloor() {
        List<ge.tbegvadze.toon3d.level.SupplySlot> slots = new ArrayList<>();
        for (int room = 0; room < SYNTHETIC_ROOM_COUNT; room++) {
            boolean gated = room == SYNTHETIC_ROOM_COUNT - 1;
            for (int slot = 0; slot < SYNTHETIC_SLOTS_PER_ROOM; slot++) {
                int distance = 3 + room * SYNTHETIC_ROOM_SPACING + slot % 5;
                ge.tbegvadze.toon3d.level.SupplySlot supplySlot = new ge.tbegvadze.toon3d.level.SupplySlot(
                        room * 10 + slot % 5, slot / 5, room, distance, !gated, slot == 2);
                slots.add(supplySlot);
            }
        }
        for (int corridor = 0; corridor < 10; corridor++) {
            slots.add(new ge.tbegvadze.toon3d.level.SupplySlot(corridor, 40,
                    ge.tbegvadze.toon3d.level.SupplySlotProvider.CONNECTOR_REGION, 5 + corridor * 4, true, true));
        }
        return slots;
    }

    private static int syntheticHalfDistance(List<ge.tbegvadze.toon3d.level.SupplySlot> slots) {
        int farthest = 0;
        for (ge.tbegvadze.toon3d.level.SupplySlot slot : slots) {
            if (slot.reachableWithoutKeycard) farthest = Math.max(farthest, slot.walkDistance);
        }
        return farthest / 2;
    }

    /**
     * Accumulates the R-SUPPLY measurements of one (spec, depth) cell over its seeds and emits the
     * worst case of each check — shared by the planner-level audit and the generator sweep.
     */
    public static final class SupplyAuditAccumulator {
        private final ge.tbegvadze.toon3d.route.NodeSupplySpec spec;
        private final int depth;
        private int   floors;
        private float minimumHealFloor   = Float.MAX_VALUE;
        private float minimumEarlyShare  = Float.MAX_VALUE;
        private float worstTracking      = 0f;
        private String worstTrackingWhat = "";
        private int   unplaced;
        private int   spreadViolations;
        private float worstRoomShare;
        private int   carried;
        private int   carrierEligible;

        public SupplyAuditAccumulator(ge.tbegvadze.toon3d.route.NodeSupplySpec spec, int depth) {
            this.spec  = spec;
            this.depth = depth;
        }

        /** Folds one planned + placed floor in. */
        public void add(ge.tbegvadze.toon3d.level.SupplyPlan plan, ge.tbegvadze.toon3d.level.SupplyPlacement placement) {
            floors++;
            if (spec.healFloorApplies()) {
                float floorValue = placement.keycardFreeHealFloorValue();
                minimumHealFloor  = Math.min(minimumHealFloor, floorValue);
                float earlyShare  = floorValue <= 0f ? 0f : placement.earlyHealFloorValue() / floorValue;
                minimumEarlyShare = Math.min(minimumEarlyShare, earlyShare);
            }
            for (ge.tbegvadze.toon3d.level.SupplyCategory category : new ge.tbegvadze.toon3d.level.SupplyCategory[]{
                    ge.tbegvadze.toon3d.level.SupplyCategory.AMMO, ge.tbegvadze.toon3d.level.SupplyCategory.HEAL,
                    ge.tbegvadze.toon3d.level.SupplyCategory.ARMOUR, ge.tbegvadze.toon3d.level.SupplyCategory.CREDITS}) {
                float planned   = plan.plannedValue(category);
                float placed    = placement.placedValue(category);
                float allowance = Math.max(BalanceConfig.SUPPLY_TRACK_TOLERANCE * planned,
                        0.5f * plan.largestPickupValue(category));
                float error     = allowance <= 0f ? (Math.abs(placed - planned) > 1e-3f ? Float.MAX_VALUE : 0f)
                                                  : Math.abs(placed - planned) / allowance;
                if (error > worstTracking) {
                    worstTracking     = error;
                    worstTrackingWhat = category + " planned " + Math.round(planned) + " placed " + Math.round(placed);
                }
                int count = 0;
                for (ge.tbegvadze.toon3d.level.SupplyPlacement.GroundPlacement ground : placement.ground()) {
                    if (ground.pickup.category == category) count++;
                }
                float share = placement.maximumRoomShare(category);
                if (placement.maximumRoomCount(category) > 1) {
                    worstRoomShare = Math.max(worstRoomShare, share);
                    if (share > BalanceConfig.SUPPLY_MAX_ROOM_SHARE + 1e-4f
                            && placement.maximumRoomCount(category)
                                    > Math.max(1, (int) Math.floor(BalanceConfig.SUPPLY_MAX_ROOM_SHARE * count))) {
                        spreadViolations++;
                    }
                }
            }
            unplaced += placement.unplaced().size();
            int eligible = 0;
            for (ge.tbegvadze.toon3d.level.PlannedPickup pickup : plan.pickups()) {
                boolean ammo = pickup.category == ge.tbegvadze.toon3d.level.SupplyCategory.AMMO && !pickup.behindAnchor;
                boolean heal = pickup.category == ge.tbegvadze.toon3d.level.SupplyCategory.HEAL && !pickup.healFloor;
                if (ammo || heal) eligible++;
            }
            carrierEligible += eligible;
            carried         += placement.carriers().size();
        }

        /** Emits one result per check, worst case over every folded floor. */
        public void emit(List<RuleResult> results, String prefix) {
            if (floors == 0) return;
            String cell = prefix + spec.type() + " d" + depth;
            if (spec.healFloorApplies()) {
                results.add(new RuleResult(RuleKind.SUPPLY, cell + " heal floor (keycard-free)", minimumHealFloor,
                        BalanceConfig.SUPPLY_HEAL_FLOOR_FRACTION - 1e-3f, Float.POSITIVE_INFINITY,
                        minimumHealFloor >= BalanceConfig.SUPPLY_HEAL_FLOOR_FRACTION - 1e-3f,
                        "worst of " + floors + " floors, fraction of max HP"));
                results.add(new RuleResult(RuleKind.SUPPLY, cell + " heal floor first-half share", minimumEarlyShare,
                        BalanceConfig.SUPPLY_HEAL_EARLY_SHARE, 1f,
                        minimumEarlyShare >= BalanceConfig.SUPPLY_HEAL_EARLY_SHARE - 1e-4f, null));
            }
            results.add(new RuleResult(RuleKind.SUPPLY, cell + " tracking error / allowance", worstTracking, 0f, 1f,
                    worstTracking <= 1f, worstTrackingWhat));
            results.add(new RuleResult(RuleKind.SUPPLY, cell + " unplaced pickups", unplaced, 0f, 0f,
                    unplaced == 0, null));
            results.add(new RuleResult(RuleKind.SUPPLY, cell + " room-share violations", spreadViolations, 0f, 0f,
                    spreadViolations == 0, "worst multi-pickup room share " + String.format("%.2f", worstRoomShare)));
            // S6 applies where the floor has a carrier ROSTER. A boss arena has none by design (the planner caps
            // carriers at the roster size, 0), so it is out of the rule's domain — not a waiver: before the
            // finite run's 36-turn depth-25 arena its eligible count simply never reached the emit threshold
            // (balance-overhaul order 6).
            if (!spec.bossArenaAmmo() && carrierEligible >= 4 * floors) {
                float share = carried / (float) carrierEligible;
                results.add(new RuleResult(RuleKind.SUPPLY, cell + " carrier share", share,
                        BalanceConfig.SUPPLY_CARRIER_SHARE - 0.08f, BalanceConfig.SUPPLY_CARRIER_SHARE + 0.08f,
                        Math.abs(share - BalanceConfig.SUPPLY_CARRIER_SHARE) <= 0.08f,
                        carried + " of " + carrierEligible + " eligible pickups"));
            }
        }
    }


    // =====================================================================================
    // R-SUPPLY — the GENERATOR SWEEP (balance-overhaul order 2, A2): every generator x every node type it
    // serves x SUPPLY_AUDIT_DEPTHS x SUPPLY_AUDIT_SEED_COUNT seeds, built through the real generator and
    // read back from the FloorContentReport the shared populator attaches. Computed once per JVM and
    // cached: the audit's three supply tests, the density rules and BalanceReport all read the same pass.
    // =====================================================================================

    /** Which node types each generator builds (data, never a switch): the sweep's matrix. */
    private static final Map<ge.tbegvadze.toon3d.route.GeneratorId, RouteNodeType[]> GENERATOR_SERVES =
            buildGeneratorServes();

    private static Map<ge.tbegvadze.toon3d.route.GeneratorId, RouteNodeType[]> buildGeneratorServes() {
        Map<ge.tbegvadze.toon3d.route.GeneratorId, RouteNodeType[]> serves = new java.util.LinkedHashMap<>();
        RouteNodeType[] standard = {RouteNodeType.COMBAT, RouteNodeType.ELITE, RouteNodeType.SHOP, RouteNodeType.MYSTERY};
        serves.put(ge.tbegvadze.toon3d.route.GeneratorId.ROOMS_MST, new RouteNodeType[]{RouteNodeType.COMBAT,
                RouteNodeType.ELITE, RouteNodeType.CACHE, RouteNodeType.SHOP, RouteNodeType.MYSTERY});
        serves.put(ge.tbegvadze.toon3d.route.GeneratorId.LINEAR_CORRIDOR, standard);
        serves.put(ge.tbegvadze.toon3d.route.GeneratorId.CAVERN, standard);
        serves.put(ge.tbegvadze.toon3d.route.GeneratorId.BOSS_ARENA, new RouteNodeType[]{RouteNodeType.BOSS});
        serves.put(ge.tbegvadze.toon3d.route.GeneratorId.MED_BAY, new RouteNodeType[]{RouteNodeType.REST});
        serves.put(ge.tbegvadze.toon3d.route.GeneratorId.EVENT_ROOM, new RouteNodeType[]{RouteNodeType.EVENT});
        serves.put(ge.tbegvadze.toon3d.route.GeneratorId.GATE_AIRLOCK, new RouteNodeType[]{RouteNodeType.REGION_GATE});
        return Collections.unmodifiableMap(serves);
    }

    /** The generator x node-type matrix the sweep builds. */
    public static Map<ge.tbegvadze.toon3d.route.GeneratorId, RouteNodeType[]> generatorServes() {
        return GENERATOR_SERVES;
    }

    private static List<ge.tbegvadze.toon3d.level.FloorContentReport> cachedSweepReports;

    /**
     * Every floor of the sweep, in matrix order (generator, node type, depth, seed). Built once per JVM.
     * The config is the one a node of that type hands the generator: the node's supply spec, the expected
     * player's ammo types.
     */
    public static synchronized List<ge.tbegvadze.toon3d.level.FloorContentReport> supplySweepReports() {
        if (cachedSweepReports != null) return cachedSweepReports;
        List<ge.tbegvadze.toon3d.level.FloorContentReport> reports = new ArrayList<>();
        for (Map.Entry<ge.tbegvadze.toon3d.route.GeneratorId, RouteNodeType[]> entry : GENERATOR_SERVES.entrySet()) {
            for (RouteNodeType type : entry.getValue()) {
                ge.tbegvadze.toon3d.route.NodeSupplySpec spec = RouteRegistries.nodeSupplySpecs().getOrCombat(type);
                for (int depth : BalanceConfig.SUPPLY_AUDIT_DEPTHS) {
                    for (int seedIndex = 0; seedIndex < BalanceConfig.SUPPLY_AUDIT_SEED_COUNT; seedIndex++) {
                        long seed = GameMath.floorSeed(0x5EE7L + seedIndex * 7919L, depth);
                        ge.tbegvadze.toon3d.level.LevelGenConfig config = sweepConfig(spec);
                        ge.tbegvadze.toon3d.level.Level level = RouteRegistries.generators()
                                .create(entry.getKey(), seed, config).generate(depth);
                        ge.tbegvadze.toon3d.level.FloorContentReport report = level.getFloorContentReport();
                        if (report != null) reports.add(report);
                    }
                }
            }
        }
        cachedSweepReports = Collections.unmodifiableList(reports);
        return cachedSweepReports;
    }

    /** The config a node of this spec's type hands its generator during the sweep. */
    private static ge.tbegvadze.toon3d.level.LevelGenConfig sweepConfig(ge.tbegvadze.toon3d.route.NodeSupplySpec spec) {
        ge.tbegvadze.toon3d.level.LevelGenConfig config = new ge.tbegvadze.toon3d.level.LevelGenConfig();
        config.supplySpec = spec;
        return config;
    }

    /** R-SUPPLY (generator sweep): every cell of the matrix, worst case over its seeds. */
    public static synchronized List<RuleResult> supplySweepResults() {
        List<RuleResult> results = new ArrayList<>();
        Map<String, SupplyAuditAccumulator> cells = new java.util.LinkedHashMap<>();
        for (ge.tbegvadze.toon3d.level.FloorContentReport report : supplySweepReports()) {
            String key = report.generatorName + "|" + report.spec.type() + "|" + report.depth;
            SupplyAuditAccumulator accumulator = cells.get(key);
            if (accumulator == null) {
                accumulator = new SupplyAuditAccumulator(report.spec, report.depth);
                cells.put(key, accumulator);
            }
            accumulator.add(report.plan, report.placement);
        }
        for (Map.Entry<String, SupplyAuditAccumulator> cell : cells.entrySet()) {
            cell.getValue().emit(results, cell.getKey().substring(0, cell.getKey().indexOf('|')) + " ");
        }
        return results;
    }

    /**
     * R-SUPPLY, the ELITE promise (balance-overhaul order 2, A6), over the same cached sweep: per
     * generator and depth, the ELITE floor's mean spent Threat Points over the COMBAT floor's sits in
     * [ELITE_THREAT_RATIO_MIN, MAX] (~1.6x), and EVERY ELITE floor places a reward weapon at level >= d+1
     * in or past its anchor group's room (C2 — "behind the anchor").
     */
    public static synchronized List<RuleResult> eliteRewardResults() {
        List<RuleResult> results = new ArrayList<>();
        Map<String, float[]> threat = new java.util.LinkedHashMap<>();   // key -> {eliteSum, eliteN, combatSum, combatN}
        Map<String, int[]>   reward = new java.util.LinkedHashMap<>();   // key -> {floors, rewarded}
        for (ge.tbegvadze.toon3d.level.FloorContentReport report : supplySweepReports()) {
            RouteNodeType type = report.spec.type();
            if (type != RouteNodeType.ELITE && type != RouteNodeType.COMBAT) continue;
            String key = report.generatorName + " d" + report.depth;
            float[] sums = threat.computeIfAbsent(key, k -> new float[4]);
            int offset = type == RouteNodeType.ELITE ? 0 : 2;
            sums[offset]     += report.threatSpent;
            sums[offset + 1] += 1f;
            if (type != RouteNodeType.ELITE) continue;
            int[] counts = reward.computeIfAbsent(key, k -> new int[2]);
            counts[0]++;
            for (ge.tbegvadze.toon3d.level.SupplyPlacement.GroundPlacement ground : report.placement.ground()) {
                ge.tbegvadze.toon3d.level.PlannedPickup pickup = ground.pickup;
                if (pickup.category != ge.tbegvadze.toon3d.level.SupplyCategory.WEAPON) continue;
                boolean onLevelPlusOne = pickup.weaponLevelOffset >= 1;
                boolean behindAnchor = report.anchorRegionId < 0
                        || ground.regionId == report.anchorRegionId
                        || ground.walkDistance >= report.anchorWalkDistance;
                if (onLevelPlusOne && behindAnchor) {
                    counts[1]++;
                    break;
                }
            }
        }
        for (Map.Entry<String, float[]> cell : threat.entrySet()) {
            float[] sums = cell.getValue();
            if (sums[1] <= 0f || sums[3] <= 0f) continue;
            float ratio = (sums[0] / sums[1]) / Math.max(1e-3f, sums[2] / sums[3]);
            results.add(new RuleResult(RuleKind.SUPPLY, cell.getKey() + " elite threat ratio", ratio,
                    BalanceConfig.ELITE_THREAT_RATIO_MIN, BalanceConfig.ELITE_THREAT_RATIO_MAX,
                    ratio >= BalanceConfig.ELITE_THREAT_RATIO_MIN && ratio <= BalanceConfig.ELITE_THREAT_RATIO_MAX,
                    "mean spent TP, ELITE over COMBAT"));
        }
        for (Map.Entry<String, int[]> cell : reward.entrySet()) {
            int[] counts = cell.getValue();
            float share = counts[1] / (float) Math.max(1, counts[0]);
            results.add(new RuleResult(RuleKind.SUPPLY, cell.getKey() + " elite reward weapon", share, 1f, 1f,
                    counts[1] == counts[0],
                    counts[1] + "/" + counts[0] + " floors with a level >= d+1 weapon behind the anchor"));
        }
        return results;
    }

    /** The R-SUPPLY sweep results whose subject contains any of the given fragments (the per-test views). */
    public static List<RuleResult> supplySweepResults(String... subjectFragments) {
        List<RuleResult> selected = new ArrayList<>();
        for (RuleResult result : supplySweepResults()) {
            for (String fragment : subjectFragments) {
                if (result.subject.contains(fragment)) {
                    selected.add(result);
                    break;
                }
            }
        }
        return selected;
    }


    // =====================================================================================
    // R-DENSITY (balance-overhaul order 2, A3 / A5) — what the generator sweep FIELDS: bodies against the
    // E1 band, group shape (E4) and first contact (E5) on COMBAT / ELITE, the walkable footprint (E6) on
    // the three combat layouts (E7 density is reported, not enforced), read from the same cached
    // FloorContentReports as R-SUPPLY.
    // =====================================================================================

    /** The E1 body band for a spec at a depth: the body-target band x the spec's body scale. */
    public static int[] bodyBand(ge.tbegvadze.toon3d.route.NodeSupplySpec spec, int depth) {
        float low  = GameMath.bodyTargetAtDepth(depth, 0f,
                BalanceConfig.BODY_TARGET_MIN_DEPTH_ONE, BalanceConfig.BODY_TARGET_MAX_DEPTH_ONE,
                BalanceConfig.BODY_TARGET_MIN_DEEP, BalanceConfig.BODY_TARGET_MAX_DEEP,
                BalanceConfig.BODY_TARGET_REFERENCE_DEEP_DEPTH) * spec.bodyScale();
        float high = GameMath.bodyTargetAtDepth(depth, 1f,
                BalanceConfig.BODY_TARGET_MIN_DEPTH_ONE, BalanceConfig.BODY_TARGET_MAX_DEPTH_ONE,
                BalanceConfig.BODY_TARGET_MIN_DEEP, BalanceConfig.BODY_TARGET_MAX_DEEP,
                BalanceConfig.BODY_TARGET_REFERENCE_DEEP_DEPTH) * spec.bodyScale();
        return new int[]{Math.round(low), Math.round(high)};
    }

    /** R-DENSITY over the generator sweep: one result per check per cell, worst case over its seeds. */
    public static synchronized List<RuleResult> densitySweepResults() {
        List<RuleResult> results = new ArrayList<>();
        Map<String, List<ge.tbegvadze.toon3d.level.FloorContentReport>> cells = new java.util.LinkedHashMap<>();
        for (ge.tbegvadze.toon3d.level.FloorContentReport report : supplySweepReports()) {
            ge.tbegvadze.toon3d.route.NodeSupplySpec spec = report.spec;
            boolean footprintedLayout = ge.tbegvadze.toon3d.level.FootprintPlanner.isFootprinted(report.generatorName);
            if (!spec.shapeRulesApply() && !spec.hasDensityBand() && !footprintedLayout) continue;
            cells.computeIfAbsent(report.generatorName + " " + spec.type() + " d" + report.depth,
                    key -> new ArrayList<>()).add(report);
        }
        for (Map.Entry<String, List<ge.tbegvadze.toon3d.level.FloorContentReport>> cell : cells.entrySet()) {
            List<ge.tbegvadze.toon3d.level.FloorContentReport> reports = cell.getValue();
            ge.tbegvadze.toon3d.level.FloorContentReport first = reports.get(0);
            ge.tbegvadze.toon3d.route.NodeSupplySpec spec = first.spec;
            int[] band = bodyBand(spec, first.depth);
            // E6 (owner override 2026-10-01): every floor of a combat layout within +/-15% of its target — the
            // cut from that layout's original size at this depth.
            int target = ge.tbegvadze.toon3d.level.FootprintPlanner.targetWalkableTiles(first.generatorName, first.depth);
            boolean footprinted = target > 0;
            float footprintLow  = target * (1f - BalanceConfig.FOOTPRINT_TOLERANCE);
            float footprintHigh = target * (1f + BalanceConfig.FOOTPRINT_TOLERANCE);
            int smallestFootprint = Integer.MAX_VALUE;
            int largestFootprint  = 0;
            int fewest = Integer.MAX_VALUE;
            int most   = 0;
            float leastGrouped = Float.MAX_VALUE;
            int fewestBigGroups = Integer.MAX_VALUE;
            int mostLone = 0;
            int farthestContact = 0;
            int missingContact = 0;
            int inStartRoom = 0;
            for (ge.tbegvadze.toon3d.level.FloorContentReport report : reports) {
                fewest = Math.min(fewest, report.enemyCount);
                most   = Math.max(most, report.enemyCount);
                smallestFootprint = Math.min(smallestFootprint, report.walkableTiles);
                largestFootprint  = Math.max(largestFootprint, report.walkableTiles);
                if (!spec.shapeRulesApply()) continue;
                float grouped = report.enemyCount == 0 ? 1f : report.groupedEnemies() / (float) report.enemyCount;
                leastGrouped    = Math.min(leastGrouped, grouped);
                fewestBigGroups = Math.min(fewestBigGroups, report.groupsOfAtLeast(BalanceConfig.SHAPE_BIG_GROUP_SIZE));
                mostLone        = Math.max(mostLone, report.loneEnemies);
                if (report.firstContactWalkTiles < 0) missingContact++;
                else farthestContact = Math.max(farthestContact, report.firstContactWalkTiles);
                if (report.groupInStartRegion) inStartRoom++;
            }
            String where = cell.getKey() + " ";
            results.add(new RuleResult(RuleKind.DENSITY, where + "bodies fewest (E1)", fewest, band[0], band[1],
                    fewest >= band[0], reports.size() + " floors"));
            results.add(new RuleResult(RuleKind.DENSITY, where + "bodies most (E1)", most, band[0], band[1],
                    most <= band[1], reports.size() + " floors"));
            if (footprinted) {
                results.add(new RuleResult(RuleKind.DENSITY, where + "footprint smallest (E6)", smallestFootprint,
                        footprintLow, footprintHigh, smallestFootprint >= footprintLow,
                        "walkable tiles, target " + target));
                results.add(new RuleResult(RuleKind.DENSITY, where + "footprint largest (E6)", largestFootprint,
                        footprintLow, footprintHigh, largestFootprint <= footprintHigh,
                        "walkable tiles, target " + target));
            }
            // E7 density is REPORTED (BalanceReport's SUPPLY / DENSITY table), no longer enforced: the owner's
            // footprint override sizes floors by their original layout, and the body targets were not raised
            // with it, so the old [2.2, 4.0] band cannot hold on the larger floors.
            if (!spec.shapeRulesApply()) continue;
            results.add(new RuleResult(RuleKind.DENSITY, where + "grouped share (E4)", leastGrouped,
                    BalanceConfig.SHAPE_GROUPED_MIN_FRACTION, 1f,
                    leastGrouped >= BalanceConfig.SHAPE_GROUPED_MIN_FRACTION, "worst floor"));
            results.add(new RuleResult(RuleKind.DENSITY, where + "big groups (E4)", fewestBigGroups,
                    BalanceConfig.SHAPE_MIN_BIG_GROUPS, Float.POSITIVE_INFINITY,
                    fewestBigGroups >= BalanceConfig.SHAPE_MIN_BIG_GROUPS,
                    "groups of >= " + BalanceConfig.SHAPE_BIG_GROUP_SIZE + ", worst floor"));
            results.add(new RuleResult(RuleKind.DENSITY, where + "lone enemies (E4)", mostLone, 0f,
                    BalanceConfig.SHAPE_MAX_LONE_ENEMIES, mostLone <= BalanceConfig.SHAPE_MAX_LONE_ENEMIES, "worst floor"));
            int limit = first.depth <= 1 ? BalanceConfig.FIRST_CONTACT_FIRST_FLOOR_MAX_WALK_TILES
                                         : BalanceConfig.FIRST_CONTACT_MAX_WALK_TILES;
            results.add(new RuleResult(RuleKind.DENSITY, where + "first contact walk (E5)", farthestContact, 0f, limit,
                    farthestContact <= limit && missingContact == 0,
                    missingContact == 0 ? "worst floor" : missingContact + " floors had no group of two or more"));
            results.add(new RuleResult(RuleKind.DENSITY, where + "group in start room (E5)", inStartRoom, 0f, 0f,
                    inStartRoom == 0, null));
        }
        return results;
    }

    /** The R-DENSITY results whose subject contains any of the given fragments (the per-test views). */
    public static List<RuleResult> densitySweepResults(String... subjectFragments) {
        List<RuleResult> selected = new ArrayList<>();
        for (RuleResult result : densitySweepResults()) {
            for (String fragment : subjectFragments) {
                if (result.subject.contains(fragment)) {
                    selected.add(result);
                    break;
                }
            }
        }
        return selected;
    }
}
