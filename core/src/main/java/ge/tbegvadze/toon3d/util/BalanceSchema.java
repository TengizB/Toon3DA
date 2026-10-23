package ge.tbegvadze.toon3d.util;

import ge.tbegvadze.toon3d.audio.GameSoundCatalog;
import ge.tbegvadze.toon3d.audio.SoundRegistry;
import ge.tbegvadze.toon3d.enemy.EnemyFamily;
import ge.tbegvadze.toon3d.enemy.EnemyTrait;
import ge.tbegvadze.toon3d.enemy.EnemyVoiceMoment;
import ge.tbegvadze.toon3d.entity.DamageClass;
import ge.tbegvadze.toon3d.entity.MatchupCatalog;
import ge.tbegvadze.toon3d.level.EncounterGroupTemplate;
import ge.tbegvadze.toon3d.level.EncounterGroupTemplateRegistry;
import ge.tbegvadze.toon3d.narrative.BestiaryCatalog;
import ge.tbegvadze.toon3d.narrative.StoryStrings;
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
import ge.tbegvadze.toon3d.relic.RelicCatalog;
import ge.tbegvadze.toon3d.relic.RelicDefinition;
import ge.tbegvadze.toon3d.relic.RelicRarity;
import ge.tbegvadze.toon3d.relic.RelicSource;
import ge.tbegvadze.toon3d.relic.RelicValuation;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

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
 * <p><b>Admission.</b> The schema iterates the game's own content collections
 * ({@link EnemyType}, {@link EnemyFamily}, {@link SpecialAbility}, {@link BossTable}, ...) so content
 * that skips a declaration, a pricing row or a registry can no longer ship silently (the
 * anti-"shipped unpriced" rule — the necrotic faction once corrupted every floor budget for weeks
 * this way). Each {@link AdmissionRule} (C-*, {@link RuleKind#ADMISSION}) emits ONE result per content
 * item whose detail lists EVERY missing item and where to add it. An admission result is a missing
 * declaration, never a band verdict (that stays with the band rule), so it can NEVER be waived (G5).
 * The old COVERAGE kind is retired: its rows live in C-WEAPON / C-ITEM / C-AMMO and R-RELIC-SOURCE.
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
        /**
         * R-RELIC (relics-run-changing-artifacts): every non-CURSED relic's GAIN (carry lift; SUPPLY_TOTAL ratio for a
         * SUPPLY relic) lands in its rarity x valuation band at every RELIC_REFERENCE_DEPTHS depth. The floor is the
         * "felt" test: a relic whose kinds the valuation does not read prices at 1.00 and fails.
         */
        RELIC,
        /**
         * R-RELIC-CURSE: every CURSED relic's gain lands in the CURSED band for its valuation at every reference
         * depth, AND its COST effects alone cost >= RELIC_CURSED_MIN_LOSS on some probe at some reference depth.
         */
        RELIC_CURSE,
        /**
         * R-RELIC-STACK: at every BossTable depth the expected hand never out-carries the WORST non-waived R-CARRY
         * build, and the luckiest affinity-consistent hand stays within the BEST x (1 + BUILD_CARRY_TOLERANCE).
         */
        RELIC_STACK,
        /**
         * R-RELIC-SOURCE (structural): every rarity fills an offer with non-affine relics, every weight row sums to 1,
         * ids are unique, every CURSED relic has a cost line and a cost kind, RELIC_PERK_RANK_EQUIVALENT_LIFT matches
         * the worst committed build's per-rank lift, and the route offers relics at all.
         */
        RELIC_SOURCE,
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
        /**
         * R-TWIST (floor-twists): a COMBAT FLOOR TWIST priced against plain COMBAT — a threat-raising twist pays
         * inside the R-RISK-PREMIUM band (T1), a threat-neutral one sits within TWIST_NEUTRAL_EV_TOLERANCE of COMBAT's
         * EV (T2), and a twist that prints a hunter declares at least the hunter's real Threat-Point share (T3).
         */
        TWIST,
        /** R-CALM-COST (order 7): a calm node's EV sits 10-30% BELOW a standard combat node's. */
        CALM_COST,
        /**
         * R-MYSTERY-EV v2 (balance-overhaul order 6, F15): the weighted mystery table's EV is >= a combat node's (and
         * <= ROUTE_MYSTERY_EV_PREMIUM_MAX above it), its outcomes SPREAD (higher variance than a combat node's
         * zero), and the worst outcome stays bounded.
         */
        MYSTERY_EV,
        /** R-EVENT-VALUE (balance-overhaul order 6, F15): every EVENT offers a choice worth >= a COMBAT floor's supply. */
        EVENT_VALUE,
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
        /**
         * S-NAVIGATION (balance-overhaul order 7, P8): every policy leaves at most SIM_MAX_STALLED_FLOOR_FRACTION of
         * its played floors stalled. Never waived — it is what the depth-derived bands stand on.
         */
        SIM_NAVIGATION,
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
         * within SIM_BUILD_DEPTH_SPREAD of each other (depth-derived; enforced since balance-overhaul order 7), and plain
         * TACTICAL's end-of-run dominant tag is no single tag on more than SIM_BUILD_MAX_DOMINANT_TAG_SHARE of
         * its runs (over at least SIM_BUILD_MIN_SAMPLE runs that held a perk rank). The depth-spread and dominant-tag
         * share rides the order-7 round-pending waiver; the sample line is never waived.
         */
        SIM_BUILD,
        /**
         * S-SHOP (balance-overhaul order 6, F14): TACTICAL buys at least SIM_SHOP_MIN_OFFERS_PER_VISIT offers per
         * Fabricator visit (one visit per exited floor; a SHOP node's two machines count as one visit), and its
         * median credits held when a lost run ends are at most SIM_SHOP_MAX_UNSPENT_FLOORS floors of income.
         */
        SIM_SHOP,
        /**
         * S-BOSS (balance-overhaul order 6, F10): for every BossTable row, over the TACTICAL seeds that REACH it — the
         * clear rate in [SIM_BOSS_CLEAR_RATE_MIN, MAX] (the first boss at least SIM_FIRST_BOSS_TACTICAL_CLEAR_MIN),
         * the fight-length coefficient of variation <= SIM_BOSS_MAX_FIGHT_TURNS_COV, and the median player health
         * fraction at the kill in [SIM_BOSS_KILL_HEALTH_MIN, MAX]. Depth-derived: rides the order-7 round-pending waiver until a round meets it.
         */
        SIM_BOSS,
        /**
         * S-RELIC (relics-run-changing-artifacts, CP8): no relic is a trap and none is mandatory, read from the five
         * TACTICAL relic probe rows' MEAN ending depths over the NO-RELIC baseline — SR1 each COMMON / RARE /
         * LEGENDARY row in [1 - SIM_RELIC_ROW_TRAP_TOLERANCE, 1 + SIM_RELIC_ROW_MAX_LIFT]; SR2 the CURSED row in
         * [1 - SIM_RELIC_CURSED_ROW_TOLERANCE, 1 + SIM_RELIC_ROW_MAX_LIFT]; SR3 LEGENDARY >= COMMON; SR4 every
         * non-affine relic with >= SIM_RELIC_MIN_RUNS_PER_RELIC probe runs in [1 - SIM_RELIC_PER_RELIC_TRAP_TOLERANCE,
         * 1 + SIM_RELIC_PER_RELIC_MAX_LIFT]; SR5 every non-affine relic got that many runs; SR6 standard TACTICAL holds
         * at least one relic at run end on average. Tag-affine relics are banded by the audit only (AS17).
         */
        SIM_RELIC,
        /**
         * S-TWIST (floor-twists, CP8): no FLOOR TWIST is a death trap, read from the TACTICAL row — ST1 for each twist
         * with >= TWIST_SIM_MIN_FLOORS played floors, its floor-death rate <= TWIST_SIM_MAX_DEATH_RATE_RATIO x the rate
         * on plain (untwisted) COMBAT floors over the same depth range (an under-sampled twist reports n/a and does not
         * fail); ST2 every twist appears on at least one played floor (the roll is live). Never waived.
         */
        SIM_TWIST,
        /**
         * ADMISSION (balance-overhaul order 8, K1-K18 / the C-* rules): one result per content item saying whether
         * it is DECLARED, PRICED and reached by its band rule. Never waivable (G5): a failure is a missing
         * declaration or pricing row, so {@link #waive} refuses this kind and {@link #findWaiver} never returns one.
         */
        ADMISSION
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
            if (kind == RuleKind.ADMISSION) {
                return "ADMISSION " + subject + " — " + (detail == null ? "" : detail);
            }
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
        waiveRoundPendingBands();
        // Balance-overhaul order 2 (CP6): the route ledger is now priced on what each floor is BUILT with
        // (its NodeSupplySpec plan). The old ledger credited the bespoke MED-BAY and EVENT rooms with 35% of
        // a combat floor's ordinary loot they never placed; priced honestly, REST sits below the calm band
        // deep and EVENT a point above it shallow. Their contents are order 6's to retune (SCOPE OUT).
        String bespokeReason = "Priced honestly on its NodeSupplySpec plan since balance-overhaul order 2: "
                + "the bespoke room never placed the ordinary loot the old ledger credited it with, and "
                + "its contents are balance-overhaul order 6's to retune.";
        // Balance-overhaul order 6: EVENT contents were re-expressed (F15) and the EVENT node left R-CALM-COST for
        // R-EVENT-VALUE, so the event waiver is RETIRED. REST is out of order 6's scope (owner: "REST is ok"), so its
        // waiver's expiry moves to the order-7 feel pass.
        String bespokeExpiry = "Expires with the balance-overhaul order 7 feel pass (REST contents; order 6 left REST alone).";
        waive(RuleKind.CALM_COST, "rest EV discount",  bespokeReason, bespokeExpiry);
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
     * ROUND-PENDING waivers (balance-overhaul order 7, CP1 — owner decision 2026-10-14). They REPLACE the order-9
     * navigation waiver, which is deleted: the scripted policies now finish their floors (S-NAVIGATION, enforced),
     * so these bands finally measure the GAME — and the game, measured, is out of band (TACTICAL median death on
     * floor 3, 0 first-boss clears of 54 reached, win rate 0). Bringing it into band is the job of the order's
     * playtest rounds (owner PLAY NOTES + the sim, at most ~5 master knobs a round, P5/P6), not of a blind sim-only
     * retune before the owner has played. Narrower than the waiver it replaces: S-LAG and S-BUILD's depth spread
     * are in band now and are ENFORCED. Each line is deleted the round its band is met; all expire at CP6.
     */
    private static void waiveRoundPendingBands() {
        String reason = "Measured out of band by the now-navigating simulator (balance-overhaul order 7 CP1: TACTICAL "
                + "median death floor 3, 0 of 54 first-boss clears); tuning is owed by the order's playtest rounds "
                + "(owner PLAY NOTES + sim), not by a blind sim-only retune.";
        String expiry = "Expires at balance-overhaul order 7 CP6: each line is deleted the round its band is met; "
                + "any line still out of band after round 3 goes to the follow-up idea request with its evidence.";
        waive(RuleKind.SIM_FAIR,    "TACTICAL win rate",            reason, expiry);
        waive(RuleKind.SIM_FAIR,    "TACTICAL reaches depth " + BalanceConfig.SIM_TACTICAL_REACH_DEPTH, reason, expiry);
        waive(RuleKind.SIM_FAIR,    "TACTICAL median death depth (losses)", reason, expiry);
        waive(RuleKind.SIM_FAIR,    "TACTICAL first-boss clears (of seeds reaching it)", reason, expiry);
        waive(RuleKind.SIM_FAIR,    "NAIVE median death depth",     reason, expiry);
        waive(RuleKind.SIM_FAIR,    "NAIVE reaches the first boss", reason, expiry);
        waive(RuleKind.SIM_FAIR,    "NAIVE first-boss clears",      reason, expiry);
        waive(RuleKind.SIM_SKILL,   "TACTICAL vs NAIVE depth gap", reason, expiry);
        waive(RuleKind.SIM_ECONOMY, "experienced vs planned supply", reason, expiry);
        waive(RuleKind.SIM_ECONOMY, "emergency lifeline floors",   reason, expiry);
        waive(RuleKind.SIM_SHOP,    "TACTICAL offers bought per Fabricator visit", reason, expiry);
        // Owner ruling D7 (order 5) tied the dominant-tag share to the old waiver; with the runs now playing whole
        // floors the share reads 0.47 over 278 ranked runs — still the first synergy pick off the start kit, still
        // a round-1 question (J2 perk picks), so it moves to this waiver rather than being enforced red.
        waive(RuleKind.SIM_BUILD,   "TACTICAL dominant-tag share", reason, expiry);
        for (int depth : BalanceConfig.BOSS_TABLE_DEPTHS) {
            waive(RuleKind.SIM_BOSS, "boss depth " + depth + " TACTICAL clear rate", reason, expiry);
            waive(RuleKind.SIM_BOSS, "boss depth " + depth + " fight turns CoV", reason, expiry);
            waive(RuleKind.SIM_BOSS, "boss depth " + depth + " median health at the kill", reason, expiry);
        }
    }

    /** Registers an explicit waiver. Every call must be mirrored in docs/game-balance-authority.txt. */
    private static void waive(RuleKind kind, String subject, String reason, String expiryCondition) {
        if (kind == RuleKind.ADMISSION) {
            throw new IllegalArgumentException("ADMISSION results are never waivable (G5): " + subject
                    + " — add the missing declaration instead");
        }
        WAIVERS.add(new Waiver(kind, subject, reason, expiryCondition));
    }

    /** All active waivers, for BalanceReport's WAIVERS table. */
    public static List<Waiver> activeWaivers() {
        return Collections.unmodifiableList(WAIVERS);
    }

    private static Waiver findWaiver(RuleKind kind, String subject) {
        if (kind == RuleKind.ADMISSION) return null; // G5: an admission result is never waived
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
    // entry here is a C-ENEMY admission failure, never a silent pass.
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
    // model (EnemyType.baseThreatPoints) prices every enemy SPECIAL verb. C-VERB now fails the
    // build if a catalogued SpecialAbility has NO registered equivalence — the anti-"shipped
    // unpriced" discipline extended to enemy moves. DATA (an EnumMap), never a switch. A new verb
    // added to SpecialAbility without a row here is a C-VERB admission failure.
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

    /** The pricing classification for an enemy SPECIAL verb, or null when none is registered (C-VERB fails). */
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
        registry.add(new TelegraphAttackSpec("Sump Wretch spit",   BalanceConfig.SUMP_WRETCH_ATTACK_DAMAGE, "NONE", false));
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
        results.addAll(admissionResults());
        results.addAll(abilityBudgetResults());
        results.addAll(abilityRollSweepResults());
        results.addAll(creditResults());
        results.addAll(xpPaceResults());
        results.addAll(perkResults());
        results.addAll(keystoneResults());
        results.addAll(buildCarryResults());
        results.addAll(relicResults());
        results.addAll(relicCurseResults());
        results.addAll(relicStackResults());
        results.addAll(relicSourceResults());
        results.addAll(regionResults());
        results.addAll(bossGateResults());
        results.addAll(bossFairResults());
        results.addAll(bossVerbCapResults());
        results.addAll(bossRewardResults());
        results.addAll(bossAmmoResults());
        results.addAll(routePricedResults());
        results.addAll(riskPremiumResults());
        results.addAll(twistResults());
        results.addAll(calmCostResults());
        results.addAll(mysteryExpectedValueResults());
        results.addAll(eventValueResults());
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
        results.addAll(densityVarietyResults());
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
            if (band == null) continue; // reported by C-ENEMY (admissionEnemyResults)
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
            if (band == null) continue; // BOSS: SECTION 14 ruleset; unknown roles are a C-ENEMY admission failure
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

    // =====================================================================================
    // R-RELIC / R-RELIC-CURSE / R-RELIC-STACK / R-RELIC-SOURCE (relics-run-changing-artifacts CP5)
    // — util/RelicValuationModel. A relic is priced in R-CARRY's own terms; the bands are SECTION 26.
    // =====================================================================================

    /** The rarity x valuation band table (SECTION 26). A pair with no row fails R-RELIC-SOURCE resolves. */
    private static final Map<RelicRarity, Map<RelicValuation, float[]>> RELIC_BANDS = buildRelicBands();

    private static Map<RelicRarity, Map<RelicValuation, float[]>> buildRelicBands() {
        Map<RelicRarity, Map<RelicValuation, float[]>> bands = new EnumMap<>(RelicRarity.class);
        for (RelicRarity rarity : RelicRarity.values()) bands.put(rarity, new EnumMap<>(RelicValuation.class));
        bands.get(RelicRarity.COMMON).put(RelicValuation.CARRY, BalanceConfig.RELIC_CARRY_BAND_COMMON);
        bands.get(RelicRarity.COMMON).put(RelicValuation.SUPPLY, BalanceConfig.RELIC_SUPPLY_BAND_COMMON);
        bands.get(RelicRarity.RARE).put(RelicValuation.CARRY, BalanceConfig.RELIC_CARRY_BAND_RARE);
        bands.get(RelicRarity.RARE).put(RelicValuation.SUPPLY, BalanceConfig.RELIC_SUPPLY_BAND_RARE);
        bands.get(RelicRarity.LEGENDARY).put(RelicValuation.CARRY, BalanceConfig.RELIC_CARRY_BAND_LEGENDARY);
        bands.get(RelicRarity.CURSED).put(RelicValuation.CARRY, BalanceConfig.RELIC_CARRY_BAND_CURSED);
        bands.get(RelicRarity.CURSED).put(RelicValuation.SUPPLY, BalanceConfig.RELIC_SUPPLY_BAND_CURSED);
        return bands;
    }

    /** A relic's gain band {min, max} for its rarity x valuation, or null when none is registered. */
    public static float[] relicBandOf(RelicDefinition relic) {
        if (relic.rarity() == null || relic.valuation() == null) return null;
        return RELIC_BANDS.get(relic.rarity()).get(relic.valuation());
    }

    /** R-RELIC: every non-CURSED relic's gain in its rarity x valuation band at every reference depth. */
    public static List<RuleResult> relicResults() {
        List<RuleResult> results = new ArrayList<>();
        for (RelicDefinition relic : RelicCatalog.registry().all()) {
            if (relic.rarity().isCursed()) continue;
            results.addAll(relicBandResults(RuleKind.RELIC, "R-RELIC", relic));
        }
        return results;
    }

    /** One band row per reference depth (a relic with no band gets one failing row — R-RELIC-SOURCE resolves names it too). */
    private static List<RuleResult> relicBandResults(RuleKind kind, String rule, RelicDefinition relic) {
        List<RuleResult> results = new ArrayList<>();
        float[] band = relicBandOf(relic);
        if (band == null) {
            results.add(new RuleResult(kind, rule + " " + relic.id() + " band", 0f, 1f, 1f, false,
                    "no band registered for " + relic.rarity() + " x " + relic.valuation()));
            return results;
        }
        for (int depth : BalanceConfig.RELIC_REFERENCE_DEPTHS) {
            float gain = RelicValuationModel.gain(relic, depth);
            boolean inBand = gain >= band[0] - 1e-6f && gain <= band[1] + 1e-6f;
            results.add(new RuleResult(kind, rule + " " + relic.id() + " d" + depth, gain, band[0], band[1], inBand,
                    String.format("%s %s gain %.4f %s band [%.2f, %.2f] at depth %d (carry %.4f)",
                            relic.rarity(), relic.valuation(), gain, inBand ? "inside" : "OUTSIDE", band[0], band[1],
                            depth, RelicValuationModel.carryLift(relic, depth))));
        }
        return results;
    }

    /**
     * R-RELIC-CURSE: every CURSED relic's gain in its CURSED band at every reference depth, and its COST effects
     * alone ({@link RelicValuationModel#costLoss}) cost >= RELIC_CURSED_MIN_LOSS at some reference depth.
     */
    public static List<RuleResult> relicCurseResults() {
        List<RuleResult> results = new ArrayList<>();
        for (RelicDefinition relic : RelicCatalog.registry().all()) {
            if (!relic.rarity().isCursed()) continue;
            results.addAll(relicBandResults(RuleKind.RELIC_CURSE, "R-RELIC-CURSE", relic));
            BuildValuationModel.Loss worst = null;
            int worstDepth = 0;
            for (int depth : BalanceConfig.RELIC_REFERENCE_DEPTHS) {
                BuildValuationModel.Loss loss = RelicValuationModel.costLoss(relic, depth);
                if (loss != null && (worst == null || loss.loss > worst.loss)) {
                    worst = loss;
                    worstDepth = depth;
                }
            }
            float value = worst == null ? 0f : worst.loss;
            boolean real = value >= BalanceConfig.RELIC_CURSED_MIN_LOSS;
            results.add(new RuleResult(RuleKind.RELIC_CURSE, "R-RELIC-CURSE " + relic.id() + " cost",
                    value, BalanceConfig.RELIC_CURSED_MIN_LOSS, Float.MAX_VALUE, real,
                    worst == null ? "no cost effect at all"
                            : String.format("cost effects alone lose %.3f (worst: %s at depth %d; needs >= %.2f)",
                                    value, worst.probe, worstDepth, BalanceConfig.RELIC_CURSED_MIN_LOSS)));
        }
        return results;
    }

    /**
     * R-RELIC-STACK: at every BossTable depth, expectedHandLift <= the WORST non-waived R-CARRY score and
     * bestHandLift <= the BEST non-waived R-CARRY score x (1 + BUILD_CARRY_TOLERANCE). Both ceilings are read live.
     */
    public static List<RuleResult> relicStackResults() {
        List<RuleResult> results = new ArrayList<>();
        float[] ceilings = relicStackCeilings();
        float expectedCeiling = ceilings[0];
        float bestCeiling = ceilings[1];
        for (BossTable.Row row : BossTable.standard().rows()) {
            int depth = row.depth;
            float expected = RelicValuationModel.expectedHandLift(depth);
            float best = RelicValuationModel.bestHandLift(depth);
            float count = RelicValuationModel.expectedRelicCount(depth);
            results.add(new RuleResult(RuleKind.RELIC_STACK, "R-RELIC-STACK expected hand d" + depth,
                    expected, 1f, expectedCeiling, expected <= expectedCeiling + 1e-6f,
                    String.format("%.2f expected relics lift %.4f vs the worst committed build %.4f at depth %d",
                            count, expected, expectedCeiling, depth)));
            results.add(new RuleResult(RuleKind.RELIC_STACK, "R-RELIC-STACK best hand d" + depth,
                    best, 1f, bestCeiling, best <= bestCeiling + 1e-6f,
                    String.format("luckiest %d-relic hand lifts %.4f vs best build x (1 + tolerance) %.4f at depth %d",
                            Math.min(BalanceConfig.RELIC_MAX_HELD, Math.round(count)), best, bestCeiling, depth)));
        }
        return results;
    }

    /**
     * {worst non-waived R-CARRY score, best non-waived score x (1 + BUILD_CARRY_TOLERANCE)} — R-RELIC-STACK's
     * ceilings, read live from the same per-tag carry R-CARRY ({@link #buildCarryResults}) evaluates.
     */
    public static float[] relicStackCeilings() {
        float best = 0f;
        float worst = Float.MAX_VALUE;
        for (BuildTag tag : BuildTag.values()) {
            if (isCarryWaived(tag)) continue;
            float score = BuildValuationModel.carry(tag).score;
            best = Math.max(best, score);
            worst = Math.min(worst, score);
        }
        return new float[]{worst, best * (1f + BalanceConfig.BUILD_CARRY_TOLERANCE)};
    }

    /** The worst non-waived R-CARRY tag (the build RELIC_PERK_RANK_EQUIVALENT_LIFT is derived from). */
    public static BuildTag worstCommittedBuildTag() {
        BuildTag worstTag = null;
        float worst = Float.MAX_VALUE;
        for (BuildTag tag : BuildTag.values()) {
            if (isCarryWaived(tag)) continue;
            float score = BuildValuationModel.carry(tag).score;
            if (score < worst) {
                worst = score;
                worstTag = tag;
            }
        }
        return worstTag;
    }

    /** The measured per-rank lift: (worst committed carry)^(1 / (its ranks + keystone count)). */
    public static float measuredPerkRankEquivalentLift() {
        BuildValuationModel.Carry carry = BuildValuationModel.carry(worstCommittedBuildTag());
        int steps = carry.ranks + (carry.keystone == null ? 0 : 1);
        return steps <= 0 ? 1f : (float) Math.pow(carry.score, 1.0 / steps);
    }

    /** R-RELIC-SOURCE: the structural checks on the pool, the offer weights and the price anchor. */
    public static List<RuleResult> relicSourceResults() {
        List<RuleResult> results = new ArrayList<>();
        List<RelicDefinition> relics = RelicCatalog.registry().all();
        // An offer always fills: every rarity holds at least RELIC_OFFER_MAX_CARDS relics nobody's tag can exclude.
        for (RelicRarity rarity : RelicRarity.values()) {
            int nonAffine = 0;
            for (RelicDefinition relic : relics) {
                if (relic.rarity() == rarity && relic.affinity() == null) nonAffine++;
            }
            results.add(new RuleResult(RuleKind.RELIC_SOURCE, "R-RELIC-SOURCE " + rarity + " offer fills",
                    nonAffine, BalanceConfig.RELIC_OFFER_MAX_CARDS, Float.MAX_VALUE,
                    nonAffine >= BalanceConfig.RELIC_OFFER_MAX_CARDS,
                    nonAffine + " non-affine " + rarity + " relics; an offer shows up to " + BalanceConfig.RELIC_OFFER_MAX_CARDS));
        }
        // Every weight row a source can draw sums to 1 and has one weight per allowed rarity.
        for (RelicSource source : RelicSource.values()) {
            float[] previous = null;
            for (int bossIndex = 0; bossIndex < BalanceConfig.RELIC_BOSS_OFFER_RARITY_WEIGHTS.length; bossIndex++) {
                float[] weights = source.rarityWeightsFor(bossIndex);
                if (weights == previous) break; // a single-row source clamps every index to its one row
                previous = weights;
                float sum = 0f;
                for (float weight : weights) sum += weight;
                boolean shaped = weights.length == source.allowedRarities().length;
                results.add(new RuleResult(RuleKind.RELIC_SOURCE, "R-RELIC-SOURCE " + source + " weights row " + bossIndex,
                        sum, 1f - 1e-4f, 1f + 1e-4f, shaped && Math.abs(sum - 1f) <= 1e-4f,
                        String.format("sums to %.5f over %d weights for %d rarities", sum, weights.length,
                                source.allowedRarities().length)));
            }
        }
        // relics-run-changing-artifacts CP5 (folded from the retired COVERAGE kind): every relic resolves its rarity, its
        // trigger and its valuation, and the rarity x valuation pair resolves a band (an unbanded pair, e.g. a SUPPLY
        // LEGENDARY, is unpriced content).
        for (RelicDefinition relic : relics) {
            boolean resolved = relic.rarity() != null && relic.trigger() != null && relic.valuation() != null
                    && relicBandOf(relic) != null;
            results.add(new RuleResult(RuleKind.RELIC_SOURCE, "R-RELIC-SOURCE resolves " + relic.id(),
                    resolved ? 1f : 0f, 1f, 1f, resolved,
                    resolved ? relic.rarity() + " / " + relic.trigger() + " / " + relic.valuation() + " banded"
                             : "UNRESOLVED — a relic needs a rarity, a trigger, a valuation and a band for that rarity x valuation"));
        }
        // No duplicate ids (the registry already refuses one; this pins it as a rule).
        Set<String> ids = new HashSet<>();
        int duplicates = 0;
        for (RelicDefinition relic : relics) {
            if (!ids.add(relic.id())) duplicates++;
        }
        results.add(new RuleResult(RuleKind.RELIC_SOURCE, "R-RELIC-SOURCE unique ids", duplicates, 0f, 0f,
                duplicates == 0, relics.size() + " relics, " + duplicates + " duplicate ids"));
        // Every CURSED relic states its price and carries a cost kind.
        for (RelicDefinition relic : relics) {
            if (!relic.rarity().isCursed()) continue;
            boolean costLine = relic.costText() != null && !relic.costText().isEmpty();
            int costKinds = RelicValuationModel.costEffects(relic).length;
            results.add(new RuleResult(RuleKind.RELIC_SOURCE, "R-RELIC-SOURCE " + relic.id() + " has a cost",
                    costKinds, 1f, Float.MAX_VALUE, costLine && costKinds >= 1,
                    (costLine ? "cost line present" : "NO cost line") + ", " + costKinds + " cost effect(s)"));
        }
        // The price anchor: one perk rank's carry, measured on the worst committed build.
        float measured = measuredPerkRankEquivalentLift();
        BuildValuationModel.Carry worstCarry = BuildValuationModel.carry(worstCommittedBuildTag());
        float anchor = BalanceConfig.RELIC_PERK_RANK_EQUIVALENT_LIFT;
        float tolerance = BalanceConfig.RELIC_PERK_RANK_EQUIVALENT_TOLERANCE;
        results.add(new RuleResult(RuleKind.RELIC_SOURCE, "R-RELIC-SOURCE perk-rank equivalent lift",
                measured, anchor - tolerance, anchor + tolerance, Math.abs(measured - anchor) <= tolerance + 1e-6f,
                String.format("%s carry %.4f ^ (1 / (%d ranks + %d keystone)) = %.4f vs RELIC_PERK_RANK_EQUIVALENT_LIFT %.2f +/- %.2f",
                        worstCarry.tag, worstCarry.score, worstCarry.ranks, worstCarry.keystone == null ? 0 : 1,
                        measured, anchor, tolerance)));
        // The route offers relics at all.
        float nodeRate = RelicValuationModel.nodeOfferRatePerFloor();
        results.add(new RuleResult(RuleKind.RELIC_SOURCE, "R-RELIC-SOURCE node offer rate", nodeRate, 1e-6f,
                Float.MAX_VALUE, nodeRate > 0f, String.format("%.5f relic offers taken per floor from route nodes", nodeRate)));
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

    // =====================================================================================
    // ADMISSION (balance-overhaul order 8, K1-K18 / the C-* rules). One result per content item:
    // subject "<ruleId>: <contentId>", value 1 = admitted / 0 = not, band 1..1, detail = what was
    // declared, or EVERY missing item ("; "-joined, each naming what is missing and where to add it).
    // An ADMISSION rule checks the item is DECLARED, PRICED and REACHED by its band rule — never the
    // band verdict itself (that stays with R-ENEMY, R-HEAVY, R-BOSS-*, ...). So no admission result is
    // waivable (G5). Rules are DATA: the enum below carries each rule's evaluator, never a switch.
    // =====================================================================================

    /** The admission sub-rules, in E1 order. A new rule cannot compile without its evaluator. */
    public enum AdmissionRule {
        ENEMY("C-ENEMY", "K1 ENEMY ARCHETYPE", BalanceSchema::admissionEnemyResults),
        FAMILY("C-FAMILY", "K2 ENEMY FAMILY / TRAIT", BalanceSchema::admissionFamilyResults),
        VERB("C-VERB", "K3 ENEMY SPECIAL VERB", BalanceSchema::admissionVerbResults),
        BOSS("C-BOSS", "K4 BOSS", BalanceSchema::admissionBossResults),
        WEAPON("C-WEAPON", "K5 WEAPON", BalanceSchema::admissionWeaponResults),
        ABILITY("C-ABILITY", "K6 WEAPON ABILITY", BalanceSchema::admissionAbilityResults),
        PERK("C-PERK", "K7 PERK / KEYSTONE", BalanceSchema::admissionPerkResults),
        ITEM("C-ITEM", "K8 ITEM / PICKUP / CONSUMABLE", BalanceSchema::admissionItemResults),
        AMMO("C-AMMO", "K9 AMMO TYPE", BalanceSchema::admissionAmmoResults),
        STATUS("C-STATUS", "K10 STATUS EFFECT", BalanceSchema::admissionStatusResults),
        HAZARD("C-HAZARD", "K11 HAZARD / GAMEPLAY PROP", BalanceSchema::admissionHazardResults),
        ROOM("C-ROOM", "K12 ROOM BLUEPRINT", BalanceSchema::admissionRoomResults),
        GENERATOR("C-GENERATOR", "K13 LEVEL GENERATOR", BalanceSchema::admissionGeneratorResults),
        NODE("C-NODE", "K14 ROUTE NODE TYPE", BalanceSchema::admissionNodeResults),
        EVENT("C-EVENT", "K15 ROUTE EVENT / MYSTERY OUTCOME / NODE AFFIX", BalanceSchema::admissionEventResults),
        SHOP("C-SHOP", "K16 SHOP OFFER TYPE", BalanceSchema::admissionShopResults),
        REGION("C-REGION", "K17 REGION", BalanceSchema::admissionRegionResults),
        CONSTANT("C-CONSTANT", "K18 ANY NEW BALANCE NUMBER", BalanceSchema::admissionConstantResults);

        public final String ruleId;
        public final String kindLabel;
        private final Supplier<List<RuleResult>> evaluator;

        AdmissionRule(String ruleId, String kindLabel, Supplier<List<RuleResult>> evaluator) {
            this.ruleId    = ruleId;
            this.kindLabel = kindLabel;
            this.evaluator = java.util.Objects.requireNonNull(evaluator, "evaluator for " + ruleId);
        }
    }

    /** One admission result for one content item: {@code missing} empty means admitted. */
    private static RuleResult admission(AdmissionRule rule, String contentId, List<String> missing,
                                        String admittedDetail) {
        boolean admitted = missing.isEmpty();
        return new RuleResult(RuleKind.ADMISSION, rule.ruleId + ": " + contentId,
                admitted ? 1f : 0f, 1f, 1f, admitted,
                admitted ? admittedDetail : String.join("; ", missing));
    }

    /** The results of one admission rule. Every rule carries its evaluator (the constructor requires it — E3). */
    public static List<RuleResult> admissionResults(AdmissionRule rule) {
        return rule.evaluator.get();
    }

    /** Every admission rule's results, in {@link AdmissionRule} order. */
    public static List<RuleResult> admissionResults() {
        List<RuleResult> results = new ArrayList<>();
        for (AdmissionRule rule : AdmissionRule.values()) {
            results.addAll(admissionResults(rule));
        }
        return results;
    }

    /** Whether a text string id resolves in the shipped-defaults story table (the headless check of a bestiary line). */
    private static boolean storyLineResolves(StoryStrings strings, String barkRowId) {
        return strings.has("story." + barkRowId);
    }

    /** Whether any result's subject starts with the prefix (proves a band rule actually reaches a content item). */
    private static boolean anySubjectStartsWith(List<RuleResult> results, String prefix) {
        for (RuleResult result : results) {
            if (result.subject.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * C-ENEMY (K1): every {@link EnemyType} declares role / family / trait, positive stats, a known positional
     * multiplier, a tactical verb, a priced move-set and (if any) an R-HEAVY-evaluated wind-up, a legal first
     * depth; every non-boss also has TP + hit bands for its role, finite positive threat, derived XP, a unique
     * spawn symbol, a group-template slot that admits its role and an ORA archetype line.
     */
    public static List<RuleResult> admissionEnemyResults() {
        List<RuleResult> results = new ArrayList<>();
        List<RuleResult> heavyRuleResults = heavyResults();
        StoryStrings storyStrings = StoryStrings.defaults();
        List<EncounterGroupTemplate> templates = EncounterGroupTemplateRegistry.shared().all();
        for (EnemyType enemyType : EnemyType.values()) {
            boolean boss = enemyType.role() == EnemyRole.BOSS;
            List<String> missing = new ArrayList<>();
            if (enemyType.role() == null)   missing.add("no role() — declare an EnemyRole in the EnemyType row");
            if (enemyType.family() == null) missing.add("no family() — declare an EnemyFamily in the EnemyType row");
            if (enemyType.trait() == null)  missing.add("no trait() — give its EnemyFamily a trait()");
            if (enemyType.maxHealth() <= 0) missing.add("maxHealth() <= 0 — set a positive health constant in EnemyConstants");
            if (enemyType.attackDamage() <= 0) missing.add("attackDamage() <= 0 — set a positive damage constant in EnemyConstants");
            if (enemyType.attackRangeTiles() < 1) missing.add("attackRangeTiles() < 1 — a reach of at least 1 tile");
            if (enemyType.attackCadenceTurns() < 1) missing.add("attackCadenceTurns() < 1 — at least 1 turn between attacks");
            if (enemyType.moveEveryNTurns() < 1) missing.add("moveEveryNTurns() < 1 — at least 1 turn per move");
            float positional = enemyType.positionalMultiplier();
            if (positional != BalanceConfig.POSITIONAL_MULT_MELEE
                    && positional != BalanceConfig.POSITIONAL_MULT_FAST_MELEE
                    && positional != BalanceConfig.POSITIONAL_MULT_RANGED) {
                missing.add("positionalMultiplier() " + positional + " is not one of BalanceConfig POSITIONAL_MULT_MELEE / _FAST_MELEE / _RANGED");
            }
            String verb = enemyType.tacticalVerb();
            if (verb == null || verb.trim().isEmpty()) missing.add("blank tacticalVerb() — ORA's archetype line is built from it");
            for (SpecialAbility ability : enemyType.moveSet()) {
                if (SPECIAL_VERB_PRICING.get(ability) == null) {
                    missing.add("moveSet() ability " + ability + " has no SpecialVerbPricing — register it in BalanceSchema SPECIAL_VERB_PRICING");
                }
            }
            HeavyAttack windUp = enemyType.heavyWindUp();
            if (windUp != null && !anySubjectStartsWith(heavyRuleResults, windUp.displayName() + " d")) {
                missing.add("heavyWindUp() " + windUp + " is not evaluated by R-HEAVY — heavyResults() must iterate it");
            }
            if (enemyType.minSpawnDepth() < 1 || enemyType.minSpawnDepth() > BalanceConfig.RUN_FINAL_DEPTH) {
                missing.add("minSpawnDepth() " + enemyType.minSpawnDepth() + " outside 1.." + BalanceConfig.RUN_FINAL_DEPTH);
            }
            if (!storyLineResolves(storyStrings, BestiaryCatalog.archetypeBarkId(enemyType))) {
                missing.add("ORA archetype line story." + BestiaryCatalog.archetypeBarkId(enemyType)
                        + " is missing — add it to BarkStrings.registerDefaults and assets/story/story-strings.properties");
            }
            if (!boss) {
                if (enemyType.role() != null && !ENEMY_THREAT_POINT_BANDS.containsKey(enemyType.role())) {
                    missing.add("role " + enemyType.role() + " has no TP band — add it to BalanceSchema buildEnemyThreatPointBands via ENEMY_HIT_BANDS");
                }
                if (enemyType.role() != null && !ENEMY_HIT_BANDS.containsKey(enemyType.role())) {
                    missing.add("role " + enemyType.role() + " has no hit band — add it to BalanceSchema buildEnemyHitBands");
                }
                float threat = enemyType.baseThreatPoints();
                if (Float.isNaN(threat) || Float.isInfinite(threat) || threat <= 0f) {
                    missing.add("baseThreatPoints() " + threat + " is not finite and positive — check its stats and move-set pricing");
                }
                if (enemyType.baseXpReward() != Math.round(BalanceConfig.XP_PER_THREAT_POINT * threat)) {
                    missing.add("baseXpReward() " + enemyType.baseXpReward() + " is not derived from threat (round(XP_PER_THREAT_POINT x baseThreatPoints())) — remove the override");
                }
                if (EnemyType.fromSpawnChar(enemyType.spawnChar()) != enemyType) {
                    missing.add("spawnChar() '" + enemyType.spawnChar() + "' is not unique to this archetype — pick an unused symbol from docs/tile-symbols.txt");
                }
                boolean slotted = false;
                for (EncounterGroupTemplate template : templates) {
                    for (EncounterGroupTemplate.Slot slot : template.slots()) {
                        if (slot.role.admits(enemyType)) slotted = true;
                    }
                }
                if (!slotted) {
                    missing.add("no EncounterGroupTemplate slot admits it — add or widen a template in EncounterGroupTemplates");
                }
            }
            results.add(admission(AdmissionRule.ENEMY, enemyType.name(), missing,
                    (boss ? "BOSS " : "role " + enemyType.role() + " ") + enemyType.family()
                            + " / " + enemyType.trait() + "; stats, verb, move-set and ORA line declared"));
        }
        return results;
    }

    /**
     * C-FAMILY (K2): every {@link EnemyFamily} has a trait, its own ALERT / ATTACK / DEATH voice in the audio
     * catalog (WIND_UP is the shared fallback by design) and both bestiary lines; every {@link EnemyTrait} has a
     * positive matchup cell for every {@link DamageClass}, a glyph unless it is FLESH, and a reference scenario
     * whose archetype presents it.
     */
    public static List<RuleResult> admissionFamilyResults() {
        List<RuleResult> results = new ArrayList<>();
        SoundRegistry soundRegistry = new SoundRegistry();
        GameSoundCatalog.bootstrap(soundRegistry);
        StoryStrings storyStrings = StoryStrings.defaults();
        for (EnemyFamily family : EnemyFamily.values()) {
            List<String> missing = new ArrayList<>();
            if (family.trait() == null) missing.add("no trait — pass an EnemyTrait in the EnemyFamily constant");
            for (EnemyVoiceMoment moment : EnemyVoiceMoment.values()) {
                if (moment == EnemyVoiceMoment.WIND_UP) continue; // one shared telegraph sound, never a family voice
                if (!soundRegistry.hasEnemyFamilyVoice(family, moment)) {
                    missing.add("no " + moment + " voice — add registerFamilyVoice(" + family + ", ...) in audio/GameSoundCatalog");
                }
            }
            if (!storyLineResolves(storyStrings, BestiaryCatalog.familyBarkId(family))) {
                missing.add("early bestiary line story." + BestiaryCatalog.familyBarkId(family) + " is missing from BarkStrings / story-strings.properties");
            }
            if (!storyLineResolves(storyStrings, BestiaryCatalog.deepFamilyBarkId(family))) {
                missing.add("deep bestiary line story." + BestiaryCatalog.deepFamilyBarkId(family) + " is missing from BarkStrings / story-strings.properties");
            }
            results.add(admission(AdmissionRule.FAMILY, family.name(), missing,
                    "trait " + family.trait() + "; ALERT/ATTACK/DEATH voices and both ORA lines declared"));
        }
        for (EnemyTrait trait : EnemyTrait.values()) {
            List<String> missing = new ArrayList<>();
            for (DamageClass damageClass : DamageClass.values()) {
                float raw = MatchupCatalog.shared().rawMultiplier(damageClass, trait);
                if (Float.isNaN(raw) || raw <= 0f) {
                    missing.add("matchup cell " + damageClass + " x " + trait + " is " + raw
                            + " — register it in entity/MatchupCatalog / BalanceConfig SECTION 22");
                }
            }
            if (trait != EnemyTrait.FLESH && !trait.hasGlyph()) {
                missing.add("no health-bar glyph — set hasGlyph() in EnemyTrait and draw it in the health-bar glyph renderer");
            }
            boolean presented = false;
            for (WeaponRoleModel.RoleScenario scenario : WeaponRoleModel.scenarios()) {
                if (scenario.archetype.trait() == trait) presented = true;
            }
            if (!presented) {
                missing.add("no WeaponRoleModel reference scenario presents it — add a scenario whose archetype has this trait");
            }
            results.add(admission(AdmissionRule.FAMILY, trait.name(), missing,
                    "matchup cells for all " + DamageClass.values().length + " damage classes, glyph and a reference scenario declared"));
        }
        return results;
    }

    /**
     * C-VERB (K3): every {@link SpecialAbility} has a {@link SpecialVerbPricing}; a damaging verb is a declared
     * HEAVY committed >= 1 turn ahead and evaluated by R-HEAVY; a summon verb has positive summon caps; and ORA
     * has an ability line for it.
     */
    public static List<RuleResult> admissionVerbResults() {
        List<RuleResult> results = new ArrayList<>();
        List<RuleResult> heavyRuleResults = heavyResults();
        StoryStrings storyStrings = StoryStrings.defaults();
        for (SpecialAbility ability : SpecialAbility.values()) {
            List<String> missing = new ArrayList<>();
            SpecialVerbPricing pricing = SPECIAL_VERB_PRICING.get(ability);
            if (pricing == null) {
                missing.add("UNPRICED — register a SpecialVerbPricing equivalence in BalanceSchema SPECIAL_VERB_PRICING");
            }
            HeavyAttack heavy = ability.heavyAttack();
            if (ability.dealsDamage() && heavy == null) {
                missing.add("dealsDamage() but heavyAttack() is null — a damaging verb must be a declared HeavyAttack");
            }
            if (heavy != null) {
                if (heavy.minimumCommitTurns() < 1) {
                    missing.add("heavy " + heavy + " commits " + heavy.minimumCommitTurns() + " turns ahead — needs >= 1");
                }
                if (!anySubjectStartsWith(heavyRuleResults, heavy.displayName() + " d")) {
                    missing.add("heavy " + heavy + " is not evaluated by R-HEAVY — heavyResults() must iterate it");
                }
            }
            if (pricing == SpecialVerbPricing.AMORTISED_SUMMON
                    && (EnemyConstants.SUMMON_COUNT_MAX <= 0 || EnemyConstants.SUMMON_ROOM_LIVE_CAP <= 0)) {
                missing.add("summon caps must be > 0 — set EnemyConstants.SUMMON_COUNT_MAX and SUMMON_ROOM_LIVE_CAP");
            }
            if (!storyLineResolves(storyStrings, BestiaryCatalog.abilityBarkId(ability))) {
                missing.add("ORA ability line story." + BestiaryCatalog.abilityBarkId(ability)
                        + " is missing from BarkStrings / story-strings.properties");
            }
            results.add(admission(AdmissionRule.VERB, ability.name(), missing,
                    "priced " + pricing + (heavy != null ? "; heavy " + heavy + " banded by R-HEAVY" : "")
                            + "; ORA line declared"));
        }
        return results;
    }

    /**
     * C-BOSS (K4): every {@link BossTable#standard()} row has a legal boss depth, positive dials, boss verb specs,
     * derived {@link BossStats} and R-BOSS-GATE / R-BOSS-FAIR results; every BOSS-role {@link EnemyType} is the
     * boss of some row's archetype (matched by name, as {@code EnemyType.OVERSEER} / {@code Archetype.OVERSEER}).
     */
    public static List<RuleResult> admissionBossResults() {
        List<RuleResult> results = new ArrayList<>();
        List<RuleResult> gateResults = bossGateResults();
        List<RuleResult> fairResults = bossFairResults();
        Set<Integer> tableDepths = new HashSet<>();
        for (int depth : BalanceConfig.BOSS_TABLE_DEPTHS) tableDepths.add(depth);
        for (BossTable.Row row : BossTable.standard().rows()) {
            List<String> missing = new ArrayList<>();
            if (row.depth < 1 || row.depth > BalanceConfig.RUN_FINAL_DEPTH) {
                missing.add("depth " + row.depth + " outside 1.." + BalanceConfig.RUN_FINAL_DEPTH);
            }
            if (!tableDepths.contains(row.depth)) {
                missing.add("depth " + row.depth + " is not in BalanceConfig.BOSS_TABLE_DEPTHS");
            }
            if (row.targetFightTurns <= 0f) missing.add("targetFightTurns <= 0 — set the RULE-1 dial on the BossTable row");
            if (row.survivalRatio <= 0f) missing.add("survivalRatio <= 0 — set the survival dial on the BossTable row");
            boolean hasVerbs = false;
            for (BossVerbSpec verb : BOSS_VERBS) {
                if (verb.archetype == row.archetype) hasVerbs = true;
            }
            if (!hasVerbs) {
                missing.add("archetype " + row.archetype + " has no BossVerbSpec — register its verbs in BalanceSchema buildBossVerbRegistry");
            }
            BossStats stats = BossBalance.statsForRow(row);
            if (stats == null || stats.effectiveHitPoints <= 0 || Float.isNaN(stats.damagePerTurn)
                    || Float.isInfinite(stats.damagePerTurn) || stats.damagePerTurn <= 0f) {
                missing.add("BossStats do not derive to finite positive HP / DPT at depth " + row.depth);
            }
            String gateSubject = row.archetype.displayName + " depth " + row.depth;
            boolean gated = false;
            for (RuleResult result : gateResults) {
                if (result.subject.equals(gateSubject)) gated = true;
            }
            if (!gated) missing.add("no R-BOSS-GATE result for " + gateSubject + " — bossGateResults() must iterate the BossTable row");
            boolean fair = false;
            for (RuleResult result : fairResults) {
                if (result.subject.equals(gateSubject + " fight length")) fair = true;
            }
            if (!fair) missing.add("no R-BOSS-FAIR result for " + gateSubject + " — bossFairResults() must iterate the BossTable row");
            results.add(admission(AdmissionRule.BOSS, row.archetype.name() + " depth " + row.depth, missing,
                    "BossTable row, boss verbs, derived stats and R-BOSS-GATE / R-BOSS-FAIR declared"));
        }
        for (EnemyType enemyType : EnemyType.values()) {
            if (enemyType.role() != EnemyRole.BOSS) continue;
            boolean reachable = false;
            for (BossTable.Row row : BossTable.standard().rows()) {
                if (row.archetype.name().equals(enemyType.name())) reachable = true;
            }
            List<String> missing = new ArrayList<>();
            if (!reachable) {
                missing.add("no BossTable row has an archetype named " + enemyType.name()
                        + " — add a BossTable row (route/BossTable) and a BossBalance.Archetype for it");
            }
            results.add(admission(AdmissionRule.BOSS, enemyType.name(), missing, "fought on a BossTable row"));
        }
        return results;
    }

    /** The melee Weapon instances C-WEAPON reads a damage class from (no headless factory exists; mirrors WeaponRoleModel's direct construction). */
    private static List<ge.tbegvadze.toon3d.entity.Weapon> meleeWeaponInstances() {
        List<ge.tbegvadze.toon3d.entity.Weapon> weapons = new ArrayList<>();
        weapons.add(new ge.tbegvadze.toon3d.entity.Fist());
        weapons.add(new ge.tbegvadze.toon3d.entity.CombatKnife());
        weapons.add(new ge.tbegvadze.toon3d.entity.Hammer());
        weapons.add(new ge.tbegvadze.toon3d.entity.MeleeChainsaw());
        return weapons;
    }

    /**
     * C-WEAPON (K5): every WEAPON {@link ItemType} is classified (the old COVERAGE "weapon item" row). RANGED: a
     * {@link WeaponRoleModel.RoleWeapon} row with damage class + ammo type and positive base hit, clip and range;
     * its class is a ranged class with a declared niche naming an existing scenario (or is BALLISTIC, the
     * generalist) that R-ROLE results reach; and it is in {@code SupplyPlanner.weaponDropPool()}. MELEE: its
     * weapon's damage class is BLADE or BLUNT. AMMO_FALLBACK: admitted as a legacy ammo pickup.
     */
    public static List<RuleResult> admissionWeaponResults() {
        List<RuleResult> results = new ArrayList<>();
        List<RuleResult> roleResults = weaponRoleResults();
        List<ge.tbegvadze.toon3d.entity.Weapon> meleeWeapons = meleeWeaponInstances();
        List<ItemType> dropPool = ge.tbegvadze.toon3d.level.SupplyPlanner.weaponDropPool();
        for (ItemType itemType : ItemType.values()) {
            if (itemType.getCategory() != ItemCategory.WEAPON) continue;
            List<String> missing = new ArrayList<>();
            WeaponItemClassification classification = WEAPON_ITEM_CLASSIFICATIONS.get(itemType);
            String admittedDetail = "classified " + classification;
            if (classification == null) {
                missing.add("UNCLASSIFIED — register it in BalanceSchema WEAPON_ITEM_CLASSIFICATIONS (ranged role, melee, or fallback)");
            } else if (classification == WeaponItemClassification.RANGED) {
                WeaponRoleModel.RoleWeapon roleWeapon = null;
                for (WeaponRoleModel.RoleWeapon candidate : WeaponRoleModel.weapons()) {
                    if (candidate.itemType == itemType) roleWeapon = candidate;
                }
                if (roleWeapon == null) {
                    missing.add("no WeaponRoleModel.RoleWeapon row — add it in WeaponRoleModel.buildWeapons");
                } else {
                    ge.tbegvadze.toon3d.entity.DamageClass damageClass = roleWeapon.damageClass;
                    if (damageClass == null) missing.add("RoleWeapon has no damageClass — declare damageClass() on the Weapon subclass");
                    if (roleWeapon.ammoType == null) missing.add("RoleWeapon has no ammoType — declare getAmmoType() on the Weapon subclass");
                    if (roleWeapon.undroppedHit <= 0f) missing.add("base damage <= 0 — set a positive base damage constant");
                    if (roleWeapon.model.clipActions <= 0) missing.add("clip <= 0 — set a positive base clip size");
                    if (roleWeapon.model.baseHitByTile.length <= 0) missing.add("range <= 0 — set a positive base range");
                    if (damageClass != null) {
                        if (!WeaponRoleModel.rangedClasses().contains(damageClass)) {
                            missing.add("class " + damageClass + " is not a ranged class in WeaponRoleModel");
                        }
                        String[] niches = BalanceConfig.ROLE_DECLARED_NICHE_BY_CLASS;
                        String niche = damageClass.ordinal() < niches.length ? niches[damageClass.ordinal()] : null;
                        boolean generalist = damageClass == ge.tbegvadze.toon3d.entity.DamageClass.BALLISTIC;
                        boolean nicheExists = false;
                        for (WeaponRoleModel.RoleScenario scenario : WeaponRoleModel.scenarios()) {
                            if (scenario.id.equals(niche)) nicheExists = true;
                        }
                        if (!generalist && niche == null) {
                            missing.add("no declared niche for " + damageClass + " (ROLE_DECLARED_NICHE_BY_CLASS) — it must be best in >= 1 reference scenario (R-ROLE-1) or add one");
                        } else if (!generalist && !nicheExists) {
                            missing.add("declared niche " + niche + " for " + damageClass + " is not a WeaponRoleModel scenario id");
                        }
                        if (!anySubjectStartsWith(roleResults, "R-ROLE-3 " + damageClass + " ")) {
                            missing.add("weaponRoleResults() has no R-ROLE result reaching class " + damageClass);
                        }
                    }
                    admittedDetail = "RANGED " + damageClass + " / " + roleWeapon.ammoType
                            + "; niche, stats and drop pool declared";
                }
                if (!dropPool.contains(itemType)) {
                    missing.add("not in SupplyPlanner.weaponDropPool() — add it to SupplyPlanner WEAPON_POOL");
                }
            } else if (classification == WeaponItemClassification.MELEE) {
                ge.tbegvadze.toon3d.entity.Weapon instance = null;
                for (ge.tbegvadze.toon3d.entity.Weapon candidate : meleeWeapons) {
                    if (candidate.getItemType() == itemType) instance = candidate;
                }
                if (instance == null) {
                    missing.add("no melee Weapon instance — add it to BalanceSchema.meleeWeaponInstances");
                } else if (instance.damageClass() != ge.tbegvadze.toon3d.entity.DamageClass.BLADE
                        && instance.damageClass() != ge.tbegvadze.toon3d.entity.DamageClass.BLUNT) {
                    missing.add("melee weapon damageClass() is " + instance.damageClass() + " — must be BLADE or BLUNT");
                } else {
                    admittedDetail = "MELEE " + instance.damageClass();
                }
            } else {
                admittedDetail = "legacy ammo pickup, no weapon class";
            }
            results.add(admission(AdmissionRule.WEAPON, itemType.name(), missing, admittedDetail));
        }
        return results;
    }

    /**
     * C-ABILITY (K6): every {@link WeaponAbility} has family, trigger, rank and build tag, a finite non-negative
     * priced PP at every weapon level 1..MAX_WEAPON_LEVEL, and a SIGNATURE ability can be rolled onto some weapon
     * (the legendary slot is rolled by family eligibility, not bound to one weapon item — see the report).
     */
    public static List<RuleResult> admissionAbilityResults() {
        List<RuleResult> results = new ArrayList<>();
        for (WeaponAbility ability : WeaponAbility.values()) {
            List<String> missing = new ArrayList<>();
            if (ability.family == null)   missing.add("no family — declare Family in the WeaponAbility row");
            if (ability.trigger == null)  missing.add("no trigger — declare Trigger in the WeaponAbility row");
            if (ability.rank == null)     missing.add("no rank — declare Rank in the WeaponAbility row");
            if (ability.buildTag == null) missing.add("no buildTag — declare a BuildTag in the WeaponAbility row");
            for (int level = 1; level <= BalanceConfig.MAX_WEAPON_LEVEL; level++) {
                float points = abilityPowerPointsAt(ability, level);
                if (Float.isNaN(points) || Float.isInfinite(points) || points < 0f) {
                    missing.add("abilityPowerPointsAt level " + level + " = " + points
                            + " — price it in GameMath.abilityPowerPoints / WeaponRoller.buildAbilityInstance");
                    break;
                }
            }
            if (ability.rank == WeaponAbility.Rank.SIGNATURE
                    && !ability.eligibleFor(true) && !ability.eligibleFor(false)) {
                missing.add("SIGNATURE ability is eligible for no weapon family — fix its Family");
            }
            results.add(admission(AdmissionRule.ABILITY, ability.name(), missing,
                    ability.rank + " " + ability.family + " / " + ability.trigger + "; priced at levels 1.."
                            + BalanceConfig.MAX_WEAPON_LEVEL));
        }
        return results;
    }

    /**
     * C-PERK (K7): every {@link Perk} has a tag, rank >= 1, an effect, display + effect text and PK1 FELT results;
     * every {@link Keystone} has a tag, a gain and a cost effect, both lines and R-KEYSTONE results; every
     * {@link BuildTag} has a perk, exactly one keystone and an R-CARRY row. Content ids are prefixed
     * PERK / KEYSTONE / TAG because the three enums may share names.
     */
    public static List<RuleResult> admissionPerkResults() {
        List<RuleResult> results = new ArrayList<>();
        List<RuleResult> feltResults = perkFeltResults();
        List<RuleResult> keystoneRuleResults = keystoneResults();
        List<RuleResult> carryResults = buildCarryResults();
        for (Perk perk : Perk.values()) {
            List<String> missing = new ArrayList<>();
            if (perk.tag == null) missing.add("no tag — declare a BuildTag in the Perk row");
            if (perk.maxRank < 1) missing.add("maxRank < 1");
            if (perk.effectCount() < 1) missing.add("no PerkEffect — give the Perk row at least one effect");
            if (perk.displayName == null || perk.displayName.trim().isEmpty()) missing.add("blank display name");
            if (perk.effectLine == null || perk.effectLine.trim().isEmpty()) missing.add("blank effect line");
            if (!anySubjectStartsWith(feltResults, "PK1 " + perk.name() + " r")) {
                missing.add("no PK1 FELT result — perkResults() must iterate it");
            }
            results.add(admission(AdmissionRule.PERK, "PERK " + perk.name(), missing,
                    perk.tag + " rank " + perk.maxRank + "; effects, text and PK1 declared"));
        }
        for (Keystone keystone : Keystone.values()) {
            List<String> missing = new ArrayList<>();
            if (keystone.tag == null) missing.add("no tag — declare a BuildTag in the Keystone row");
            if (keystone.gainEffectCount() < 1) missing.add("no gain effect — a keystone needs >= 1 gain");
            if (keystone.costEffectCount() < 1) missing.add("no cost effect — a keystone needs >= 1 cost");
            if (keystone.gainLine == null || keystone.gainLine.trim().isEmpty()) missing.add("blank gain line");
            if (keystone.costLine == null || keystone.costLine.trim().isEmpty()) missing.add("blank cost line");
            if (!anySubjectStartsWith(keystoneRuleResults, "R-KEYSTONE " + keystone.name() + " ")) {
                missing.add("no R-KEYSTONE result — keystoneResults() must iterate it");
            }
            results.add(admission(AdmissionRule.PERK, "KEYSTONE " + keystone.name(), missing,
                    keystone.tag + "; gain, cost, text and R-KEYSTONE declared"));
        }
        for (BuildTag tag : BuildTag.values()) {
            List<String> missing = new ArrayList<>();
            int perkCount = 0;
            for (Perk perk : Perk.values()) if (perk.tag == tag) perkCount++;
            int keystoneCount = 0;
            for (Keystone keystone : Keystone.values()) if (keystone.tag == tag) keystoneCount++;
            if (perkCount < 1) missing.add("no perk — add a Perk row with this tag");
            if (keystoneCount != 1) missing.add(keystoneCount + " keystones — a tag has exactly one (Keystone)");
            boolean hasCarry = false;
            for (RuleResult result : carryResults) {
                if (result.subject.equals(carrySubject(tag))) hasCarry = true;
            }
            if (!hasCarry) missing.add("no R-CARRY row — buildCarryResults() must iterate the tag");
            results.add(admission(AdmissionRule.PERK, "TAG " + tag.name(), missing,
                    perkCount + " perks, one keystone and an R-CARRY row declared"));
        }
        return results;
    }

    /**
     * C-AMMO (K9): every {@link AmmoType} has a scarcity row, positive generosity, reserve cap, box size and
     * banking target, an AMMO {@link ItemType} pickup, and the shop converts ammo damage at a positive rate.
     */
    public static List<RuleResult> admissionAmmoResults() {
        List<RuleResult> results = new ArrayList<>();
        for (AmmoType ammoType : AmmoType.values()) {
            List<String> missing = new ArrayList<>();
            boolean hasRow = false;
            for (ScarcityRowSpec row : SCARCITY_ROWS) {
                if (row.ammoType == ammoType) hasRow = true;
            }
            if (!hasRow) missing.add("NOT priced — register a scarcity row in BalanceSchema buildScarcityRowRegistry");
            if (ammoType.getSupplyGenerosity() <= 0f) missing.add("supply generosity <= 0 — set AMMO_SUPPLY_GENEROSITY_" + ammoType.name() + " in BalanceConfig");
            if (ammoType.getReserveCap() <= 0) missing.add("reserve cap <= 0 — set the reserve cap constant");
            if (ammoType.getAmountPerBox() <= 0) missing.add("box size <= 0 — set the box amount constant");
            if (ammoType.getBankingFloorsTarget() <= 0f) missing.add("banking target <= 0 — set AMMO_BANKING_FLOORS_" + ammoType.name() + " in BalanceConfig");
            ItemType pickup = ammoPickupItemOrNull(ammoType);
            if (pickup == null || pickup.getCategory() != ItemCategory.AMMO) {
                missing.add("no AMMO ItemType pickup — add an AMMO_" + ammoType.name()
                        + " ItemType and map it in AmmoType.getItemType()");
            }
            if (BalanceConfig.SHOP_AMMO_DAMAGE_PER_POWER_POINT <= 0f) {
                missing.add("SHOP_AMMO_DAMAGE_PER_POWER_POINT <= 0 — the shop cannot price ammo");
            }
            results.add(admission(AdmissionRule.AMMO, ammoType.name(), missing,
                    "scarcity row, caps, box, banking target and pickup item declared"));
        }
        return results;
    }

    // -------------------------------------------------------------------------------------
    // K8 ITEM SUPPLY STANCES — where each non-weapon ItemType comes from. DATA, not a switch. A stance with a
    // SupplyCategory means the SupplyPlanner / a placer puts the item on the floor in that category's budget; a
    // null category (NOT_FLOOR_SUPPLIED) carries the one-line reason it is not floor supplied.
    // -------------------------------------------------------------------------------------

    /** One item's declared supply stance: the planner category that places it, or null plus the reason it is not floor supplied. */
    public static final class ItemSupplyStance {
        public final ge.tbegvadze.toon3d.level.SupplyCategory category;
        public final String reason;

        ItemSupplyStance(ge.tbegvadze.toon3d.level.SupplyCategory category, String reason) {
            this.category = category;
            this.reason   = reason;
        }

        /** True for NOT_FLOOR_SUPPLIED (no planner category). */
        public boolean isNotFloorSupplied() { return category == null; }
    }

    private static final Map<ItemType, ItemSupplyStance> ITEM_SUPPLY_STANCES = buildItemSupplyStances();

    private static Map<ItemType, ItemSupplyStance> buildItemSupplyStances() {
        Map<ItemType, ItemSupplyStance> stances = new EnumMap<>(ItemType.class);
        ge.tbegvadze.toon3d.level.SupplyCategory heal = ge.tbegvadze.toon3d.level.SupplyCategory.HEAL;
        ge.tbegvadze.toon3d.level.SupplyCategory credits = ge.tbegvadze.toon3d.level.SupplyCategory.CREDITS;
        ge.tbegvadze.toon3d.level.SupplyCategory ammo = ge.tbegvadze.toon3d.level.SupplyCategory.AMMO;
        stances.put(ItemType.MEDKIT_SMALL, new ItemSupplyStance(heal, "stim pack '+' — SupplyPlanner HEAL pickup, also a shop offer"));
        stances.put(ItemType.MEDKIT_LARGE, new ItemSupplyStance(heal, "field medkit 'H' — SupplyPlanner HEAL pickup, also a shop offer"));
        stances.put(ItemType.STIMPACK, new ItemSupplyStance(null,
                "no placer, shop or reward references it — an inventory/UI identity only; the small medkit is the stim"));
        stances.put(ItemType.KEYCARD_RED, new ItemSupplyStance(null,
                "hand-level floor decal 'r' (Level.keycardColorOfPickup); no generator or planner places keycards"));
        stances.put(ItemType.KEYCARD_YELLOW, new ItemSupplyStance(null,
                "hand-level floor decal 'y' (Level.keycardColorOfPickup); no generator or planner places keycards"));
        stances.put(ItemType.KEYCARD_BLUE, new ItemSupplyStance(null,
                "hand-level floor decal 'b' (Level.keycardColorOfPickup); no generator or planner places keycards"));
        stances.put(ItemType.CREDITS, new ItemSupplyStance(null,
                "the wallet stack's inventory identity; floor credits are the CREDIT_SMALL/MEDIUM/LARGE chips"));
        stances.put(ItemType.CREDIT_SMALL, new ItemSupplyStance(credits, "credit chip — SupplyPlanner CREDITS, spawned by World.seedCreditChips"));
        stances.put(ItemType.CREDIT_MEDIUM, new ItemSupplyStance(credits, "credit chip — SupplyPlanner CREDITS, spawned by World.seedCreditChips"));
        stances.put(ItemType.CREDIT_LARGE, new ItemSupplyStance(credits, "credit chip — SupplyPlanner CREDITS, spawned by World.seedCreditChips"));
        for (AmmoType ammoType : AmmoType.values()) {
            ItemType pickup = ammoPickupItemOrNull(ammoType);
            if (pickup == null) continue; // C-AMMO reports the unmapped type; it must never break class init
            stances.put(pickup, new ItemSupplyStance(ammo,
                    "ammo box '" + ammoType.getPickupTileChar() + "' — SupplyPlanner AMMO pickup"));
        }
        return Collections.unmodifiableMap(stances);
    }

    /**
     * The AMMO pickup an ammo type is held as, or null when {@link AmmoType#getItemType()} does not map it (it throws
     * for an unmapped type). Found by the CP6 negative drill: the schema's static data must survive any new content,
     * so an unmapped type reaches C-AMMO as a named miss instead of crashing BalanceSchema's class initialiser.
     */
    private static ItemType ammoPickupItemOrNull(AmmoType ammoType) {
        try {
            return ammoType.getItemType();
        } catch (IllegalStateException unmapped) {
            return null;
        }
    }

    // -------------------------------------------------------------------------------------
    // K10 STATUS DECLARATIONS — one row per StatusType: its shape, the BalanceConfig / EffectConstants /
    // GameBalance constants (by NAME, resolved by reflection) holding its magnitude and duration, how the player's
    // armour treats it (Player.tryArmourShieldStatus) and which consumable cleanses it (World H11).
    // -------------------------------------------------------------------------------------

    /** The shape of a status's magnitude. */
    public enum StatusShape {
        /** Damage per turn as a FRACTION of the host's max HP (burn, poison). */
        DOT_FRACTION,
        /** Damage per turn as a flat amount (bleed, from the Rend ability). */
        DOT_FLAT,
        /** A damage multiplier expressed as a percent or factor (empowered, vulnerable, weak). */
        MULTIPLIER,
        /** A control effect with no damage magnitude of its own (stun, blind, slow, expose). */
        CONTROL
    }

    /** How the player's armour treats an ENEMY -> player application (Player.tryArmourShieldStatus). */
    public enum StatusArmourInteraction { BLOCKED_BY_ARMOUR, NOT_SHIELDED }

    /** One declared status. magnitudeConstant is null only for a CONTROL status with no magnitude. */
    public static final class StatusDeclaration {
        public final StatusShape shape;
        public final String magnitudeConstant;
        public final String durationConstant;
        public final StatusArmourInteraction armourInteraction;
        public final ItemType cleanseItem;

        StatusDeclaration(StatusShape shape, String magnitudeConstant, String durationConstant,
                          StatusArmourInteraction armourInteraction, ItemType cleanseItem) {
            this.shape             = shape;
            this.magnitudeConstant = magnitudeConstant;
            this.durationConstant  = durationConstant;
            this.armourInteraction = armourInteraction;
            this.cleanseItem       = cleanseItem;
        }
    }

    private static final Map<ge.tbegvadze.toon3d.status.StatusType, StatusDeclaration> STATUS_DECLARATIONS =
            buildStatusDeclarations();

    private static Map<ge.tbegvadze.toon3d.status.StatusType, StatusDeclaration> buildStatusDeclarations() {
        Map<ge.tbegvadze.toon3d.status.StatusType, StatusDeclaration> rows =
                new EnumMap<>(ge.tbegvadze.toon3d.status.StatusType.class);
        StatusArmourInteraction blocked = StatusArmourInteraction.BLOCKED_BY_ARMOUR;
        StatusArmourInteraction open = StatusArmourInteraction.NOT_SHIELDED;
        // Armour interaction: Player.tryArmourShieldStatus blocks WEAK / SLOWED / BLINDED / POISONED, nothing else.
        // Cleanse: World (H11) — only the SMALL medkit, only POISONED and WEAK (Player.cleansePoisonAndWeak).
        rows.put(ge.tbegvadze.toon3d.status.StatusType.BURNING, new StatusDeclaration(StatusShape.DOT_FRACTION,
                "BURN_TICK_FRACTION", "BURN_DURATION_MIN", open, null));
        rows.put(ge.tbegvadze.toon3d.status.StatusType.POISONED, new StatusDeclaration(StatusShape.DOT_FRACTION,
                "POISON_TICK_FRACTION", "POISON_MAX_STACKS", blocked, ItemType.MEDKIT_SMALL));
        rows.put(ge.tbegvadze.toon3d.status.StatusType.BLEED, new StatusDeclaration(StatusShape.DOT_FLAT,
                "REND_DAMAGE_PER_TURN_BASE", "REND_DURATION_TURNS", open, null));
        rows.put(ge.tbegvadze.toon3d.status.StatusType.STUNNED, new StatusDeclaration(StatusShape.CONTROL,
                null, "STUN_DURATION_DEFAULT", open, null));
        rows.put(ge.tbegvadze.toon3d.status.StatusType.BLINDED, new StatusDeclaration(StatusShape.CONTROL,
                null, "BLIND_DURATION", blocked, null));
        rows.put(ge.tbegvadze.toon3d.status.StatusType.SLOWED, new StatusDeclaration(StatusShape.CONTROL,
                "SLOW_FACTOR", "SLOW_DURATION", blocked, null));
        rows.put(ge.tbegvadze.toon3d.status.StatusType.EMPOWERED, new StatusDeclaration(StatusShape.MULTIPLIER,
                "EMPOWERED_DAMAGE_PERCENT", "EMPOWERED_DURATION", open, null));
        rows.put(ge.tbegvadze.toon3d.status.StatusType.VULNERABLE, new StatusDeclaration(StatusShape.MULTIPLIER,
                "VULNERABLE_DAMAGE_PERCENT", "VULNERABLE_DURATION", open, null));
        rows.put(ge.tbegvadze.toon3d.status.StatusType.WEAK, new StatusDeclaration(StatusShape.MULTIPLIER,
                "WEAK_DAMAGE_PERCENT", "WEAK_DURATION", blocked, ItemType.MEDKIT_SMALL));
        rows.put(ge.tbegvadze.toon3d.status.StatusType.EXPOSED, new StatusDeclaration(StatusShape.CONTROL,
                null, "EXPOSED_DURATION", open, null));
        return Collections.unmodifiableMap(rows);
    }

    /** Resolves a constant by NAME across BalanceConfig, EffectConstants and GameBalance; null when none has it. */
    private static Float resolveBalanceConstant(String constantName) {
        Class<?>[] holders = {BalanceConfig.class, EffectConstants.class, GameBalance.class};
        for (Class<?> holder : holders) {
            try {
                Object value = holder.getField(constantName).get(null);
                if (value instanceof Number) return ((Number) value).floatValue();
            } catch (ReflectiveOperationException ignored) {
                // try the next holder
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------------------
    // K11 HAZARD DECLARATIONS — every damaging prop / tile. The registry IS the list of hazards (no enum exists).
    // -------------------------------------------------------------------------------------

    /** One declared hazard. Player-side constants must be fractions in (0, 1]; enemy-side ones only positive. */
    public static final class HazardDeclaration {
        public final String id;
        public final char symbol;
        public final String[] playerFractionConstants;
        public final String[] enemyConstants;
        public final ge.tbegvadze.toon3d.status.StatusType appliedStatus;
        public final ge.tbegvadze.toon3d.entity.DamageClass damageClass;
        public final String damageClassReason;
        public final String placementOwnerClass;

        HazardDeclaration(String id, char symbol, String[] playerFractionConstants, String[] enemyConstants,
                          ge.tbegvadze.toon3d.status.StatusType appliedStatus,
                          ge.tbegvadze.toon3d.entity.DamageClass damageClass, String damageClassReason,
                          String placementOwnerClass) {
            this.id                      = id;
            this.symbol                  = symbol;
            this.playerFractionConstants = playerFractionConstants;
            this.enemyConstants          = enemyConstants;
            this.appliedStatus           = appliedStatus;
            this.damageClass             = damageClass;
            this.damageClassReason       = damageClassReason;
            this.placementOwnerClass     = placementOwnerClass;
        }
    }

    private static final List<HazardDeclaration> HAZARD_DECLARATIONS = buildHazardDeclarations();

    private static List<HazardDeclaration> buildHazardDeclarations() {
        List<HazardDeclaration> rows = new ArrayList<>();
        String[] barrelPlayer = {"HAZARD_BARREL_PLAYER_CENTRE_FRACTION", "HAZARD_BARREL_PLAYER_RING_FRACTION"};
        String[] barrelEnemy = {"HAZARD_BARREL_CENTRE_FRACTION", "HAZARD_BARREL_RING_FRACTION"};
        String hazardPackage = "ge.tbegvadze.toon3d.";
        rows.add(new HazardDeclaration("explosive_barrel", 'E', barrelPlayer, barrelEnemy, null,
                ge.tbegvadze.toon3d.entity.DamageClass.EXPLOSIVE, null, hazardPackage + "level.GroupBarrelPlacer"));
        rows.add(new HazardDeclaration("radioactive_barrel", 'g', barrelPlayer, barrelEnemy, null,
                ge.tbegvadze.toon3d.entity.DamageClass.EXPLOSIVE, null, hazardPackage + "level.RoomBlueprints"));
        rows.add(new HazardDeclaration("fire", 'i', new String[]{"BURN_TICK_FRACTION"},
                new String[]{"HAZARD_FIRE_ENEMY_DAMAGE_MULTIPLIER"}, ge.tbegvadze.toon3d.status.StatusType.BURNING,
                ge.tbegvadze.toon3d.entity.DamageClass.FIRE, null, hazardPackage + "hazard.HazardManager"));
        rows.add(new HazardDeclaration("toxic", 'q', new String[]{"POISON_TICK_FRACTION"},
                new String[]{"HAZARD_TOXIC_ENEMY_STACKS_PER_TURN"}, ge.tbegvadze.toon3d.status.StatusType.POISONED,
                null, "poison stacks are not a weapon damage class — the matchup table has no poison column",
                hazardPackage + "hazard.HazardManager"));
        // sump-wretch-acid-death K11: the death splash (POISONED on the 4 orthogonal neighbours) + the toxic puddle
        // it leaves; never placed by a generator, created only by HazardManager.applyEnemyDeathHazard.
        rows.add(new HazardDeclaration("sump_wretch_acid_splash", 'q', new String[]{"POISON_TICK_FRACTION"},
                new String[]{"SUMP_WRETCH_SPLASH_ENEMY_STACKS"}, ge.tbegvadze.toon3d.status.StatusType.POISONED,
                null, "poison stacks are not a weapon damage class — the matchup table has no poison column",
                hazardPackage + "hazard.HazardManager"));
        return Collections.unmodifiableList(rows);
    }

    /** The declared hazards — BalanceReport's HAZARDS table prints exactly this list. */
    public static List<HazardDeclaration> hazardDeclarations() {
        return HAZARD_DECLARATIONS;
    }

    /** Whether the authoritative tile classifier recognises the symbol as this hazard's tile. */
    private static boolean hazardSymbolRecognised(char symbol) {
        return ge.tbegvadze.toon3d.hazard.ExplosiveBarrelManager.isBarrelCell(symbol)
                || ge.tbegvadze.toon3d.level.Level.isHazardFire(symbol)
                || ge.tbegvadze.toon3d.level.Level.isHazardToxic(symbol);
    }

    /** True when the symbol is a pickup (medical, armour, ammo, keycard) or an enemy-spawn tile — supply, not architecture. */
    private static boolean isSupplySymbol(char symbol) {
        return ge.tbegvadze.toon3d.level.Level.isMedicalPickup(symbol)
                || ge.tbegvadze.toon3d.level.Level.isArmourPickup(symbol)
                || ge.tbegvadze.toon3d.level.Level.isAmmoPickup(symbol)
                || ge.tbegvadze.toon3d.level.Level.isKeycardPickup(symbol)
                || EnemyType.fromSpawnChar(symbol) != null;
    }

    /** Seeds C-ROOM sweeps per SUPPLY_NEUTRAL blueprint (an audit-local count, not a balance number). */
    private static final int ROOM_STAMP_AUDIT_SEEDS = 120;

    /**
     * C-ITEM (K8): every non-WEAPON {@link ItemType} declares a supply stance (a planner category, or not floor
     * supplied with a reason); a CONSUMABLE is a priced heal (a HealPickupSpec) or a declared utility; a HEAL stance
     * has a HealPickupSpec; a stackable item has a positive stack size; and the description is non-blank.
     */
    public static List<RuleResult> admissionItemResults() {
        List<RuleResult> results = new ArrayList<>();
        for (ItemType itemType : ItemType.values()) {
            if (itemType.getCategory() == ItemCategory.WEAPON) continue;
            List<String> missing = new ArrayList<>();
            ItemSupplyStance stance = ITEM_SUPPLY_STANCES.get(itemType);
            if (stance == null) {
                missing.add("no supply stance — declare it in BalanceSchema ITEM_SUPPLY_STANCES (a SupplyCategory, or NOT_FLOOR_SUPPLIED + reason)");
            } else if (stance.isNotFloorSupplied() && (stance.reason == null || stance.reason.trim().isEmpty())) {
                missing.add("NOT_FLOOR_SUPPLIED stance has no reason — state where the item comes from");
            }
            String classification = CONSUMABLE_CLASSIFICATIONS.get(itemType);
            boolean pricedHeal = false;
            if (classification != null) {
                for (HealPickupSpec pickup : HEAL_PICKUPS) {
                    if (pickup.displayName.equals(classification)) pricedHeal = true;
                }
            }
            if (itemType.getCategory() == ItemCategory.CONSUMABLE
                    && !pricedHeal && (classification == null || !classification.startsWith("UTILITY"))) {
                missing.add("consumable is neither a priced heal nor a declared utility — price it as a HealPickupSpec or declare it UTILITY in BalanceSchema CONSUMABLE_CLASSIFICATIONS");
            }
            if (stance != null && stance.category == ge.tbegvadze.toon3d.level.SupplyCategory.HEAL && !pricedHeal) {
                missing.add("HEAL stance but no HealPickupSpec — register one in BalanceSchema buildHealPickupRegistry and map it in CONSUMABLE_CLASSIFICATIONS");
            }
            if (itemType.isStackable() && itemType.getMaxStackSize() <= 0) {
                missing.add("stackable but maxStackSize <= 0 — set the ITEM_STACK_MAX constant");
            }
            if (itemType.getDescription() == null || itemType.getDescription().trim().isEmpty()) {
                missing.add("blank description — write one in ItemType");
            }
            results.add(admission(AdmissionRule.ITEM, itemType.name(), missing,
                    stance == null ? "" : (stance.isNotFloorSupplied() ? "NOT_FLOOR_SUPPLIED: " : stance.category + ": ")
                            + stance.reason));
        }
        return results;
    }

    /**
     * C-STATUS (K10): every {@link ge.tbegvadze.toon3d.status.StatusType} has a declaration whose named magnitude
     * and duration constants resolve (BalanceConfig / EffectConstants / GameBalance) and are positive; a
     * DOT_FRACTION magnitude is a fraction in (0, 1); a CONTROL status may omit its magnitude only; a named cleanse
     * item is a CONSUMABLE.
     */
    public static List<RuleResult> admissionStatusResults() {
        List<RuleResult> results = new ArrayList<>();
        for (ge.tbegvadze.toon3d.status.StatusType status : ge.tbegvadze.toon3d.status.StatusType.values()) {
            List<String> missing = new ArrayList<>();
            StatusDeclaration declaration = STATUS_DECLARATIONS.get(status);
            if (declaration == null) {
                missing.add("no declaration — add a row to BalanceSchema STATUS_DECLARATIONS (shape, constants, armour interaction, cleanse)");
            } else {
                if (declaration.magnitudeConstant == null) {
                    if (declaration.shape != StatusShape.CONTROL) {
                        missing.add("a " + declaration.shape + " status needs a magnitude constant");
                    }
                } else {
                    Float magnitude = resolveBalanceConstant(declaration.magnitudeConstant);
                    if (magnitude == null) {
                        missing.add("magnitude constant " + declaration.magnitudeConstant + " not found in BalanceConfig / EffectConstants / GameBalance");
                    } else if (magnitude <= 0f) {
                        missing.add("magnitude " + declaration.magnitudeConstant + " = " + magnitude + " must be > 0");
                    } else if (declaration.shape == StatusShape.DOT_FRACTION && magnitude >= 1f) {
                        missing.add("DOT_FRACTION magnitude " + declaration.magnitudeConstant + " = " + magnitude + " must be a fraction in (0, 1)");
                    }
                }
                Float duration = declaration.durationConstant == null ? null : resolveBalanceConstant(declaration.durationConstant);
                if (duration == null) {
                    missing.add("duration constant " + declaration.durationConstant + " not found in BalanceConfig / EffectConstants / GameBalance");
                } else if (duration <= 0f) {
                    missing.add("duration " + declaration.durationConstant + " = " + duration + " must be > 0");
                }
                if (declaration.armourInteraction == null) {
                    missing.add("no armour-shield interaction declared");
                }
                if (declaration.cleanseItem != null && declaration.cleanseItem.getCategory() != ItemCategory.CONSUMABLE) {
                    missing.add("cleanse item " + declaration.cleanseItem + " is not a CONSUMABLE");
                }
            }
            results.add(admission(AdmissionRule.STATUS, status.name(), missing,
                    declaration == null ? "" : declaration.shape + ", " + declaration.armourInteraction
                            + (declaration.cleanseItem == null ? ", no cleanse" : ", cleansed by " + declaration.cleanseItem)));
        }
        return results;
    }

    /**
     * C-HAZARD (K11): every {@link HazardDeclaration} names constants that resolve — player-side ones fractions in
     * (0, 1], enemy-side ones positive — a symbol the tile classifiers recognise, an applied status that is itself
     * declared in C-STATUS, a damage class (or a stated reason there is none), and a placement owner class that
     * exists.
     */
    public static List<RuleResult> admissionHazardResults() {
        List<RuleResult> results = new ArrayList<>();
        for (HazardDeclaration hazard : HAZARD_DECLARATIONS) {
            List<String> missing = new ArrayList<>();
            for (String name : hazard.playerFractionConstants) {
                Float value = resolveBalanceConstant(name);
                if (value == null) missing.add("player-side constant " + name + " not found in BalanceConfig / EffectConstants / GameBalance");
                else if (value <= 0f || value > 1f) missing.add("player-side " + name + " = " + value + " must be a fraction in (0, 1]");
            }
            for (String name : hazard.enemyConstants) {
                Float value = resolveBalanceConstant(name);
                if (value == null) missing.add("enemy-side constant " + name + " not found in BalanceConfig / EffectConstants / GameBalance");
                else if (value <= 0f) missing.add("enemy-side " + name + " = " + value + " must be > 0");
            }
            if (!hazardSymbolRecognised(hazard.symbol)) {
                missing.add("symbol '" + hazard.symbol + "' is not recognised by ExplosiveBarrelManager.isBarrelCell / Level.isHazardFire / Level.isHazardToxic");
            }
            if (hazard.appliedStatus != null && !STATUS_DECLARATIONS.containsKey(hazard.appliedStatus)) {
                missing.add("applied status " + hazard.appliedStatus + " is not declared in STATUS_DECLARATIONS (C-STATUS)");
            }
            if (hazard.damageClass == null
                    && (hazard.damageClassReason == null || hazard.damageClassReason.trim().isEmpty())) {
                missing.add("no DamageClass and no stated reason — declare one");
            }
            try {
                Class.forName(hazard.placementOwnerClass);
            } catch (ClassNotFoundException exception) {
                missing.add("placement owner " + hazard.placementOwnerClass + " does not exist");
            }
            results.add(admission(AdmissionRule.HAZARD, hazard.id, missing,
                    "'" + hazard.symbol + "' " + hazard.damageClass + " / " + hazard.appliedStatus
                            + "; placed by " + hazard.placementOwnerClass));
        }
        return results;
    }

    /**
     * C-ROOM (K12): every registered {@link ge.tbegvadze.toon3d.level.RoomBlueprint} declares a supply stance; a
     * SUPPLY_NEUTRAL room's build(), run in isolation over a seed sweep, stamps no pickup and no enemy-spawn symbol.
     */
    public static List<RuleResult> admissionRoomResults() {
        List<RuleResult> results = new ArrayList<>();
        ge.tbegvadze.toon3d.level.RoomBlueprints.bootstrap();
        ge.tbegvadze.toon3d.level.RoomBlueprintRegistry registry = ge.tbegvadze.toon3d.level.RoomBlueprints.rooms();
        for (ge.tbegvadze.toon3d.level.RoomBlueprint blueprint : registry.all()) {
            List<String> missing = new ArrayList<>();
            ge.tbegvadze.toon3d.level.RoomSupplyStance stance = blueprint.supplyStance();
            if (stance == null) {
                missing.add("no supplyStance() — declare SUPPLY_NEUTRAL or SLOT_PROVIDER in RoomBlueprints");
            } else if (stance == ge.tbegvadze.toon3d.level.RoomSupplyStance.SUPPLY_NEUTRAL) {
                Set<Character> offending = new java.util.TreeSet<>();
                for (long seed = 1; seed <= ROOM_STAMP_AUDIT_SEEDS; seed++) {
                    for (char symbol : ge.tbegvadze.toon3d.level.RoomBlueprints.symbolsStampedInIsolation(blueprint, seed)) {
                        if (isSupplySymbol(symbol)) offending.add(symbol);
                    }
                }
                if (!offending.isEmpty()) {
                    missing.add("declared SUPPLY_NEUTRAL but build() stamps supply symbols " + offending
                            + " — remove them or declare SLOT_PROVIDER");
                }
            }
            results.add(admission(AdmissionRule.ROOM, blueprint.id(), missing, "stance " + stance));
        }
        return results;
    }

    /**
     * C-GENERATOR (K13): every {@link ge.tbegvadze.toon3d.route.GeneratorId} is registered, builds headlessly, serves
     * at least one node type (generatorServes), and for each served type the R-SUPPLY generator sweep produced
     * results — i.e. its floors went through the shared supply pipeline, directly as a SupplySlotProvider or through
     * PocketSupplyRegions (the bespoke single-chamber generators).
     */
    public static List<RuleResult> admissionGeneratorResults() {
        List<RuleResult> results = new ArrayList<>();
        List<RuleResult> supplyResults = supplySweepResults();
        for (ge.tbegvadze.toon3d.route.GeneratorId generatorId : ge.tbegvadze.toon3d.route.GeneratorId.values()) {
            List<String> missing = new ArrayList<>();
            String provider = "unbuilt";
            if (!RouteRegistries.generators().isRegistered(generatorId)) {
                missing.add("not registered — RouteRegistries.registerGenerators");
            } else {
                Object instance = RouteRegistries.generators().create(generatorId, 1L,
                        new ge.tbegvadze.toon3d.level.LevelGenConfig());
                provider = instance instanceof ge.tbegvadze.toon3d.level.SupplySlotProvider
                        ? "SupplySlotProvider" : "PocketSupplyRegions";
            }
            RouteNodeType[] served = GENERATOR_SERVES.get(generatorId);
            if (served == null || served.length == 0) {
                missing.add("serves no node type — add it to BalanceSchema GENERATOR_SERVES");
            } else {
                for (RouteNodeType type : served) {
                    if (!anySubjectStartsWith(supplyResults, generatorId.stableId() + " " + type + " d")) {
                        missing.add("no R-SUPPLY sweep result for " + type + " — the generator must populate through FloorPopulator");
                    }
                }
            }
            results.add(admission(AdmissionRule.GENERATOR, generatorId.name(), missing,
                    "registered, supply via " + provider + ", serves " + java.util.Arrays.toString(served)));
        }
        return results;
    }

    // -------------------------------------------------------------------------------------
    // K16 SHOP OFFER SPECS — one row per shop OfferCategory: how its value in power points is declared, and the
    // NAME of the category-weight constant ShopRoller reads. The values are declared here independently of
    // DefaultShopOfferSource, so a literal price slipped into the shop is caught by the roll tripwire in C-SHOP.
    // -------------------------------------------------------------------------------------

    /** The declared power-point value of a rolled entry, given the weapons the context owned. */
    private interface ShopEntryValue {
        float powerPointsOf(ge.tbegvadze.toon3d.shop.ShopEntry entry,
                            List<ge.tbegvadze.toon3d.entity.WeaponProfile> ownedWeapons);
    }

    /** One shop category's declaration. */
    private static final class ShopOfferSpec {
        final String weightConstant;
        final java.util.function.IntToDoubleFunction referenceValueAtDepth;
        final ShopEntryValue entryValue;

        ShopOfferSpec(String weightConstant, java.util.function.IntToDoubleFunction referenceValueAtDepth,
                      ShopEntryValue entryValue) {
            this.weightConstant        = weightConstant;
            this.referenceValueAtDepth = referenceValueAtDepth;
            this.entryValue            = entryValue;
        }
    }

    private static final Map<ge.tbegvadze.toon3d.shop.OfferCategory, ShopOfferSpec> SHOP_OFFER_SPECS =
            buildShopOfferSpecs();

    private static Map<ge.tbegvadze.toon3d.shop.OfferCategory, ShopOfferSpec> buildShopOfferSpecs() {
        Map<ge.tbegvadze.toon3d.shop.OfferCategory, ShopOfferSpec> specs =
                new EnumMap<>(ge.tbegvadze.toon3d.shop.OfferCategory.class);
        // Tier upgrade: the ability-PP budget of the destination tier (the next tier above the weapon's).
        specs.put(ge.tbegvadze.toon3d.shop.OfferCategory.WEAPON_TIER_UPGRADE, new ShopOfferSpec(
                "SHOP_CAT_WEIGHT_TIER_UPGRADE",
                depth -> WeaponRoller.tierAbilityPowerPointBudget(WeaponTier.UNCOMMON),
                (entry, owned) -> {
                    WeaponTier source = ((ge.tbegvadze.toon3d.entity.WeaponProfile) entry.payload).getTier();
                    WeaponTier[] tiers = WeaponTier.values();
                    return WeaponRoller.tierAbilityPowerPointBudget(
                            tiers[Math.min(source.ordinal() + 1, tiers.length - 1)]);
                }));
        // Level-up: one rung of the power ladder.
        specs.put(ge.tbegvadze.toon3d.shop.OfferCategory.WEAPON_LEVEL_UP, new ShopOfferSpec(
                "SHOP_CAT_WEIGHT_WEAPON_LEVELUP",
                depth -> BalanceConfig.LADDER_LEVEL_UP_POWER_POINTS,
                (entry, owned) -> BalanceConfig.LADDER_LEVEL_UP_POWER_POINTS));
        // Player ability: one perk rank.
        specs.put(ge.tbegvadze.toon3d.shop.OfferCategory.PLAYER_ABILITY, new ShopOfferSpec(
                "SHOP_CAT_WEIGHT_ABILITY",
                depth -> BalanceConfig.SHOP_PERK_RANK_POWER_POINTS,
                (entry, owned) -> BalanceConfig.SHOP_PERK_RANK_POWER_POINTS));
        // Ammo: the damage a box supplies (box amount x the best per-shot damage among owned weapons that eat it).
        specs.put(ge.tbegvadze.toon3d.shop.OfferCategory.AMMO, new ShopOfferSpec(
                "SHOP_CAT_WEIGHT_AMMO",
                depth -> AmmoType.BULLETS.getAmountPerBox() * BalanceConfig.ASSAULT_RIFLE_DAMAGE
                        / BalanceConfig.SHOP_AMMO_DAMAGE_PER_POWER_POINT,
                (entry, owned) -> {
                    AmmoType ammoType = (AmmoType) entry.payload;
                    int bestDamage = 1;
                    for (ge.tbegvadze.toon3d.entity.WeaponProfile weapon : owned) {
                        if (weapon != null && weapon.getAmmoType() == ammoType) {
                            bestDamage = Math.max(bestDamage, weapon.getEffectiveDamage());
                        }
                    }
                    return entry.quantity * (float) bestDamage / BalanceConfig.SHOP_AMMO_DAMAGE_PER_POWER_POINT;
                }));
        // Medkit: the HP it restores at the base player max HP.
        specs.put(ge.tbegvadze.toon3d.shop.OfferCategory.MEDKIT, new ShopOfferSpec(
                "SHOP_CAT_WEIGHT_MEDKIT",
                depth -> GameMath.fractionOfMaximum(BalanceConfig.PLAYER_MAX_HEALTH,
                        BalanceConfig.MEDKIT_STIM_HEAL_FRACTION) / BalanceConfig.SHOP_HEAL_HP_PER_POWER_POINT,
                (entry, owned) -> GameMath.fractionOfMaximum(BalanceConfig.PLAYER_MAX_HEALTH,
                        entry.payload == ItemType.MEDKIT_LARGE
                                ? BalanceConfig.MEDKIT_FULL_HEAL_FRACTION : BalanceConfig.MEDKIT_STIM_HEAL_FRACTION)
                        / BalanceConfig.SHOP_HEAL_HP_PER_POWER_POINT));
        return Collections.unmodifiableMap(specs);
    }

    /** Depths and seeds the C-SHOP roll tripwire rolls each category at (audit-local counts, not balance numbers). */
    private static final int[] SHOP_TRIPWIRE_DEPTHS = {1, 5, 15, 25};
    private static final int   SHOP_TRIPWIRE_SEEDS  = 12;

    /** Ladder-currency problems of one EventEffect: every field is a fraction, a floors-of-income count or a flag. */
    private static void effectCurrencyProblems(String owner, ge.tbegvadze.toon3d.route.EventEffect effect,
                                               List<String> problems) {
        if (effect == null) {
            problems.add(owner + " has no EventEffect — give it ge.tbegvadze.toon3d.route.EventEffect.NONE at least");
            return;
        }
        float[] fractions = {effect.healFraction(), effect.armourFraction(), effect.healthCostFraction(),
                effect.gambleLootChance(), effect.gambleFailDamageFraction()};
        String[] fractionNames = {"heal", "armour", "healthCost", "gambleLootChance", "gambleFailDamage"};
        for (int index = 0; index < fractions.length; index++) {
            if (Float.isNaN(fractions[index]) || fractions[index] < 0f || fractions[index] > 1f) {
                problems.add(owner + " " + fractionNames[index] + " " + fractions[index]
                        + " is not a fraction in [0, 1] — express it as a fraction of max, not a literal amount");
            }
        }
        float[] floors = {effect.creditFloors(), effect.creditCostFloors(), effect.nextFloorBudgetBonus()};
        String[] floorNames = {"credits", "creditCost", "nextFloorBudgetBonus"};
        for (int index = 0; index < floors.length; index++) {
            if (Float.isNaN(floors[index]) || Float.isInfinite(floors[index]) || floors[index] < 0f) {
                problems.add(owner + " " + floorNames[index] + " " + floors[index]
                        + " must be finite and >= 0 (floors of income)");
            }
        }
        if (effect.weaponLevels() < 0) problems.add(owner + " weaponLevels < 0");
    }

    /** True when a ledger row's reward side is non-zero (a threat-raising modifier must declare one). */
    private static boolean economicsDeclaresReward(NodeEconomics row,
                                                   ge.tbegvadze.toon3d.route.NodeAffixDefinition affix) {
        boolean declared = row.guaranteedAmmoBoxes() > 0f || row.guaranteedHealHitPoints() > 0f
                || row.guaranteedHealEffectiveHitPointFraction() > 0f || row.creditDelta() > 0f
                || row.guaranteedExperiencePoints() > 0f || row.upgradeOpportunity() > 0f;
        ge.tbegvadze.toon3d.route.EventEffect ladder = row.ladderReward();
        if (ladder != null) {
            declared |= ladder.weaponLevels() > 0 || ladder.perkRank() || ladder.healFraction() > 0f
                    || ladder.armourFraction() > 0f || ladder.creditFloors() > 0f || ladder.relicOffer() != null;
        }
        if (affix != null) {
            declared |= affix.extraVaultAmmoBoxes() > 0 || affix.scatteredAmmoBoxes() > 0
                    || affix.extraStims() > 0 || affix.extraArmourShards() > 0;
        }
        return declared;
    }

    /**
     * C-NODE (K14): every {@link RouteNodeType} has a NodeTypeDefinition, a NodeSupplySpec, a registered level
     * profile for its profile id, a NodeEconomics row, a route-card promise that is whole (threat word and reward
     * promise both present or both absent — only the supply-bearing nodes carry one), and a finite positive weight
     * multiplier in every region of the default plan (an unlisted type is the documented unbiased 1.0).
     */
    public static List<RuleResult> admissionNodeResults() {
        RouteRegistries.bootstrap();
        List<RuleResult> results = new ArrayList<>();
        ge.tbegvadze.toon3d.route.RegionPlan plan = RegionPlan.defaultPlan();
        for (RouteNodeType type : RouteNodeType.values()) {
            List<String> missing = new ArrayList<>();
            String promise = "no route-card promise";
            if (!RouteRegistries.nodeTypes().isRegistered(type)) {
                missing.add("no NodeTypeDefinition — register it in RouteRegistries.registerNodeTypes");
            } else {
                ge.tbegvadze.toon3d.route.NodeTypeDefinition definition = RouteRegistries.nodeTypes().get(type);
                if (!RouteRegistries.levelProfiles().isRegistered(definition.levelProfileId())) {
                    missing.add("level profile '" + definition.levelProfileId() + "' is not registered — RouteRegistries.registerLevelProfiles");
                }
                boolean hasThreat = definition.threatWord() != null && !definition.threatWord().trim().isEmpty();
                boolean hasReward = definition.rewardPromise() != null && !definition.rewardPromise().trim().isEmpty();
                if (hasThreat != hasReward) {
                    missing.add("route-card promise is half-declared (threatWord / rewardPromise) — set both via supplyPromise()");
                }
                if (hasThreat && hasReward) promise = "promise '" + definition.threatWord() + " / " + definition.rewardPromise() + "'";
            }
            if (!RouteRegistries.nodeSupplySpecs().isRegistered(type)) {
                missing.add("no NodeSupplySpec — add a row in route/NodeSupplySpecs.registerAll");
            }
            if (routeLedger().forNodeType(type) == null) {
                missing.add("no NodeEconomics row — register one in route/RouteEconomics");
            }
            for (ge.tbegvadze.toon3d.route.RegionSpec region : plan.regions()) {
                float multiplier = region.nodeTypeMultiplier(type);
                if (Float.isNaN(multiplier) || Float.isInfinite(multiplier) || multiplier <= 0f) {
                    missing.add("region " + region.regionIndex() + " weight multiplier " + multiplier + " must be finite and > 0");
                }
            }
            results.add(admission(AdmissionRule.NODE, type.name(), missing,
                    "definition, supply spec, level profile, economics row, " + promise
                            + ", region weights over " + plan.regionCount() + " regions"));
        }
        return results;
    }

    /**
     * C-EVENT (K15): every FacilityEvent has choices whose EventEffects are in ladder currencies and is reached by
     * R-EVENT-VALUE; every MysteryOutcome has a NodeEconomics row, a registered delegate profile and a ladder-currency
     * reward; every registered affix / floor twist has a NodeEconomics row, and one that raises threat (budget scale
     * above 1) declares a reward premium.
     */
    public static List<RuleResult> admissionEventResults() {
        RouteRegistries.bootstrap();
        List<RuleResult> results = new ArrayList<>();
        List<RuleResult> eventValue = eventValueResults();
        for (ge.tbegvadze.toon3d.route.FacilityEvent event : RouteRegistries.events().all()) {
            List<String> missing = new ArrayList<>();
            if (event.choices().isEmpty()) missing.add("no choices — add at least one choice() to the event");
            for (ge.tbegvadze.toon3d.route.EventChoice choice : event.choices()) {
                effectCurrencyProblems("choice '" + choice.label() + "'", choice.effect(), missing);
            }
            if (!anySubjectStartsWith(eventValue, event.id() + " ")) {
                missing.add("no R-EVENT-VALUE result — eventValueResults() must iterate it");
            }
            results.add(admission(AdmissionRule.EVENT, "EVENT " + event.id(), missing,
                    event.choices().size() + " choices in ladder currencies, priced by R-EVENT-VALUE"));
        }
        for (MysteryOutcome outcome : MysteryOutcome.values()) {
            List<String> missing = new ArrayList<>();
            if (routeLedger().get(outcome.name()) == null) {
                missing.add("no NodeEconomics row — register a mysteryOutcome row in route/RouteEconomics");
            }
            if (outcome.delegateProfileId() != null
                    && !RouteRegistries.levelProfiles().isRegistered(outcome.delegateProfileId())) {
                missing.add("delegate profile '" + outcome.delegateProfileId() + "' is not a registered level profile");
            }
            if (outcome.reward() != null) effectCurrencyProblems("reward", outcome.reward(), missing);
            results.add(admission(AdmissionRule.EVENT, "MYSTERY " + outcome.name(), missing,
                    "economics row, delegate " + outcome.delegateProfileId() + ", ladder-currency reward"));
        }
        for (String affixId : RouteRegistries.affixes().ids()) {
            List<String> missing = new ArrayList<>();
            NodeEconomics row = routeLedger().get(affixId);
            ge.tbegvadze.toon3d.route.NodeAffixDefinition definition = RouteRegistries.affixes().definition(affixId);
            if (row == null) {
                missing.add("no NodeEconomics row — register an affix / twist row in route/RouteEconomics");
            } else if (row.budgetScale() > 1f && !economicsDeclaresReward(row, definition)) {
                missing.add("raises threat (budget scale " + row.budgetScale() + ") but declares no reward premium — add a reward to its NodeEconomics row");
            }
            results.add(admission(AdmissionRule.EVENT, "AFFIX " + affixId, missing,
                    "economics row" + (row != null && row.budgetScale() > 1f ? " with a reward premium" : " (not threat-raising)")));
        }
        return results;
    }

    /**
     * C-SHOP (K16): every shop {@link ge.tbegvadze.toon3d.shop.OfferCategory} has a declared value spec, a category
     * weight constant that resolves and is >= 0, a positive shopPrice at every depth 1..RUN_FINAL_DEPTH, and — the
     * literal-price tripwire — every offer rolled headlessly by DefaultShopOfferSource carries exactly the
     * shopPrice of its independently declared value (rolled at several depths and seeds for every category).
     */
    public static List<RuleResult> admissionShopResults() {
        List<RuleResult> results = new ArrayList<>();
        ge.tbegvadze.toon3d.shop.DefaultShopOfferSource source = new ge.tbegvadze.toon3d.shop.DefaultShopOfferSource();
        for (ge.tbegvadze.toon3d.shop.OfferCategory category : ge.tbegvadze.toon3d.shop.OfferCategory.values()) {
            List<String> missing = new ArrayList<>();
            ShopOfferSpec spec = SHOP_OFFER_SPECS.get(category);
            int rolled = 0;
            if (spec == null) {
                missing.add("no SHOP_OFFER_SPECS row — declare its value and weight constant in BalanceSchema");
            } else {
                Float weight = resolveBalanceConstant(spec.weightConstant);
                if (weight == null) missing.add("weight constant " + spec.weightConstant + " not found in GameBalance / BalanceConfig");
                else if (weight < 0f) missing.add("weight " + spec.weightConstant + " = " + weight + " must be >= 0");
                for (int depth = 1; depth <= BalanceConfig.RUN_FINAL_DEPTH; depth++) {
                    float value = (float) spec.referenceValueAtDepth.applyAsDouble(depth);
                    int price = GameMath.shopPrice(value, GameBalance.SHOP_CREDITS_PER_POWER_POINT, depth,
                            GameBalance.SHOP_DEPTH_PRICE_SCALE);
                    if (price <= 0) {
                        missing.add("shopPrice of value " + value + " PP at depth " + depth + " is " + price + " — must be > 0");
                        break;
                    }
                }
                for (int depth : SHOP_TRIPWIRE_DEPTHS) {
                    for (long seed = 1; seed <= SHOP_TRIPWIRE_SEEDS; seed++) {
                        List<ge.tbegvadze.toon3d.entity.WeaponProfile> owned = new ArrayList<>();
                        owned.add(new ge.tbegvadze.toon3d.entity.AssaultRifle());
                        ge.tbegvadze.toon3d.progression.PlayerBuild build =
                                category == ge.tbegvadze.toon3d.shop.OfferCategory.PLAYER_ABILITY
                                        ? new ge.tbegvadze.toon3d.progression.PlayerBuild() : null;
                        ge.tbegvadze.toon3d.shop.ShopContext context = new ge.tbegvadze.toon3d.shop.ShopContext(
                                depth, owned, owned.get(0), build);
                        java.util.Random random = new java.util.Random(seed * 31L + depth);
                        ge.tbegvadze.toon3d.shop.ShopEntry entry = rollShopCategory(source, category, context, random);
                        if (entry == null) continue;
                        rolled++;
                        int expected = GameMath.shopPrice(spec.entryValue.powerPointsOf(entry, owned),
                                GameBalance.SHOP_CREDITS_PER_POWER_POINT, depth, GameBalance.SHOP_DEPTH_PRICE_SCALE);
                        if (entry.price != expected) {
                            missing.add("rolled offer '" + entry.displayName + "' at depth " + depth + " costs " + entry.price
                                    + " but its declared value prices to " + expected
                                    + " — a literal or drifted price; derive it through GameMath.shopPrice");
                            break;
                        }
                    }
                }
                if (rolled == 0) {
                    missing.add("the headless roll tripwire could not roll a single " + category + " offer");
                }
            }
            results.add(admission(AdmissionRule.SHOP, category.name(), missing,
                    "value spec, weight constant and price tripwire over " + rolled + " rolled offers"));
        }
        return results;
    }

    private static ge.tbegvadze.toon3d.shop.ShopEntry rollShopCategory(
            ge.tbegvadze.toon3d.shop.DefaultShopOfferSource source, ge.tbegvadze.toon3d.shop.OfferCategory category,
            ge.tbegvadze.toon3d.shop.ShopContext context, java.util.Random random) {
        switch (category) {
            case WEAPON_TIER_UPGRADE: return source.rollTierUpgradeOffer(context, random);
            case WEAPON_LEVEL_UP:     return source.rollLevelUpOffer(context, random);
            case PLAYER_ABILITY:      return source.rollAbilityOffer(context, random);
            case AMMO:                return source.rollAmmoOffer(context, random);
            case MEDKIT:              return source.rollMedkitOffer(context, random);
            // A new OfferCategory rolls nothing here until it gets a case, so the tripwire names it
            // ("could not roll a single X offer") instead of silently pricing a medkit in its place.
            default:                  return null;
        }
    }

    /**
     * C-REGION (K17): every RegionSpec of the default plan has a REGION_TP_BUDGET_MULTIPLIER entry, a resolving
     * ELITE multiplier (an unlisted one is the documented unbiased 1.0), a BossTable row on its last floor, a
     * registered ambience with a crest painter, and a StoryRegion.
     */
    public static List<RuleResult> admissionRegionResults() {
        RouteRegistries.bootstrap();
        List<RuleResult> results = new ArrayList<>();
        for (ge.tbegvadze.toon3d.route.RegionSpec region : RegionPlan.defaultPlan().regions()) {
            List<String> missing = new ArrayList<>();
            if (region.regionIndex() >= BalanceConfig.REGION_TP_BUDGET_MULTIPLIER.length) {
                missing.add("no REGION_TP_BUDGET_MULTIPLIER entry for index " + region.regionIndex() + " — extend the array in BalanceConfig");
            }
            float elite = region.nodeTypeMultiplier(RouteNodeType.ELITE);
            if (Float.isNaN(elite) || elite <= 0f) missing.add("ELITE multiplier " + elite + " must be > 0");
            boolean bossOnLastFloor = false;
            for (BossTable.Row row : BossTable.standard().rows()) {
                if (row.depth == region.lastDepth()) bossOnLastFloor = true;
            }
            if (!bossOnLastFloor) {
                missing.add("no BossTable row on its last floor " + region.lastDepth() + " — register one in route/BossTable");
            }
            if (!RouteRegistries.regionAmbience().has(region.themeId())) {
                missing.add("ambience '" + region.themeId() + "' is not registered — RouteRegistries.registerRegionAmbience");
            } else {
                String crest = RouteRegistries.regionAmbience().get(region.themeId()).crestPainterId();
                if (crest == null || crest.trim().isEmpty()) missing.add("ambience has no crest painter id");
            }
            if (region.regionIndex() >= ge.tbegvadze.toon3d.narrative.StoryRegion.values().length) {
                missing.add("no StoryRegion for index " + region.regionIndex() + " — add one to narrative/StoryRegion");
            }
            results.add(admission(AdmissionRule.REGION, "REGION " + region.regionIndex() + " " + region.displayName(), missing,
                    "TP dial, elite weight, boss row, ambience + crest and story region declared"));
        }
        return results;
    }

    // -------------------------------------------------------------------------------------
    // K18 C-CONSTANT — every numeric BalanceConfig number is the subject of a schema rule or declared unbanded.
    // A NEW public static final numeric (or numeric array) field of BalanceConfig must be listed in exactly ONE of:
    //   CONSTANT_SUBJECTS — field name -> the RuleKind that bands it (a declaration: C-CONSTANT checks it exists and
//                       names a kind; that the rule really bands the field is the reviewer's check, SECTION 5), or
    //   UNBANDED          — field name -> a one-line reason no rule bands it.
    // GRANDFATHERED (the end of this file) holds every field that existed before order 8; it is SHRINK-ONLY.
    // -------------------------------------------------------------------------------------

    /** Fields a rule bands: field name -> the {@link RuleKind} that reads it. */
    private static final Map<String, RuleKind> CONSTANT_SUBJECTS = new java.util.LinkedHashMap<>();
    static {
        // mixed-encounter-groups: the group-variety numbers, banded by R-DENSITY's variety half.
        CONSTANT_SUBJECTS.put("GROUP_MAX_SAME_TYPE_PER_GROUP",       RuleKind.DENSITY);
        CONSTANT_SUBJECTS.put("GROUP_BROOD_SIZE",                    RuleKind.DENSITY);
        CONSTANT_SUBJECTS.put("GROUP_BROOD_FLOOR_CHANCE",            RuleKind.DENSITY);
        CONSTANT_SUBJECTS.put("GROUP_BROOD_MAX_PER_FLOOR",           RuleKind.DENSITY);
        CONSTANT_SUBJECTS.put("GROUP_MIXED_BEHAVIOUR_MIN_SIZE",      RuleKind.DENSITY);
        CONSTANT_SUBJECTS.put("GROUP_MAX_SINGLE_TYPE_BODY_FRACTION", RuleKind.DENSITY);
        CONSTANT_SUBJECTS.put("GROUP_ROOM_MAX_SAME_TYPE",            RuleKind.DENSITY);
        CONSTANT_SUBJECTS.put("GROUP_LOOKOUT_MAX_PER_FLOOR",         RuleKind.DENSITY);
        // ...and salvage_hook's re-composition (its CARRIER term sits at the 1.00 clamp), banded by R-RELIC.
        CONSTANT_SUBJECTS.put("RELIC_SALVAGE_HOOK_KILL_CREDIT_BONUS", RuleKind.RELIC);
        // sump-wretch-acid-death (K1 + K11 + K18): the archetype's stat block is banded by R-ENEMY's hits-to-kill /
        // hits-to-die read, its credits by R-CREDITS, and its death splash + puddle by R-HAZARD (HAZARD_DECLARATIONS).
        CONSTANT_SUBJECTS.put("SUMP_WRETCH_MAX_HEALTH",               RuleKind.ENEMY_HITS);
        CONSTANT_SUBJECTS.put("SUMP_WRETCH_ATTACK_DAMAGE",            RuleKind.ENEMY_HITS);
        CONSTANT_SUBJECTS.put("SUMP_WRETCH_MOVE_EVERY_N_TURNS",       RuleKind.ENEMY_THREAT_POINTS);
        CONSTANT_SUBJECTS.put("CREDIT_REWARD_SUMP_WRETCH",            RuleKind.CREDITS);
        CONSTANT_SUBJECTS.put("SUMP_WRETCH_SPLASH_PLAYER_STACKS",     RuleKind.HAZARD);
        CONSTANT_SUBJECTS.put("SUMP_WRETCH_SPLASH_ENEMY_STACKS",      RuleKind.HAZARD);
        CONSTANT_SUBJECTS.put("SUMP_WRETCH_PUDDLE_LIFETIME_TURNS",    RuleKind.HAZARD);
        CONSTANT_SUBJECTS.put("DEATH_SPLASH_ADJACENT_SHARE",          RuleKind.ENEMY_THREAT_POINTS);
    }

    /** Fields deliberately banded by no rule: field name -> a one-line reason. Empty until a new field needs it. */
    private static final Map<String, String> UNBANDED = new java.util.LinkedHashMap<>();

    /** True for a numeric primitive or an (nested) array of them. */
    private static boolean isNumericConstantType(Class<?> type) {
        Class<?> element = type;
        while (element.isArray()) element = element.getComponentType();
        return element == int.class || element == long.class || element == float.class || element == double.class
                || element == short.class || element == byte.class;
    }

    /** The name of every public static final numeric (or numeric array) field of BalanceConfig, by reflection. */
    private static Set<String> balanceConfigNumericFieldNames() {
        Set<String> names = new java.util.TreeSet<>();
        for (Field field : BalanceConfig.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)
                    && isNumericConstantType(field.getType())) {
                names.add(field.getName());
            }
        }
        return names;
    }

    /**
     * C-CONSTANT (K18): every numeric BalanceConfig field is in exactly one of CONSTANT_SUBJECTS, UNBANDED or the
     * shrink-only GRANDFATHERED list; every name in any of them still exists. Emits one result per offending field
     * or stale name, plus one GRANDFATHERED summary so the rule never returns an empty list.
     */
    public static List<RuleResult> admissionConstantResults() {
        List<RuleResult> results = new ArrayList<>();
        Set<String> fields = balanceConfigNumericFieldNames();
        Set<String> grandfathered = new HashSet<>(java.util.Arrays.asList(GRANDFATHERED));
        for (String name : fields) {
            int listings = (CONSTANT_SUBJECTS.containsKey(name) ? 1 : 0) + (UNBANDED.containsKey(name) ? 1 : 0)
                    + (grandfathered.contains(name) ? 1 : 0);
            List<String> missing = new ArrayList<>();
            if (listings == 0) {
                missing.add("a new BalanceConfig number must be the subject of a schema rule (CONSTANT_SUBJECTS) "
                        + "or listed in UNBANDED with a one-line reason");
            } else if (listings > 1) {
                missing.add("listed in more than one of CONSTANT_SUBJECTS / UNBANDED / GRANDFATHERED — keep exactly one");
            } else {
                continue; // admitted: one row per OFFENDING field only (there are > 1000 numbers)
            }
            results.add(admission(AdmissionRule.CONSTANT, name, missing, ""));
        }
        List<String> listed = new ArrayList<>(CONSTANT_SUBJECTS.keySet());
        listed.addAll(UNBANDED.keySet());
        listed.addAll(grandfathered);
        for (String name : new java.util.TreeSet<>(listed)) {
            if (fields.contains(name)) continue;
            List<String> missing = new ArrayList<>();
            missing.add("stale name — no such numeric BalanceConfig field; remove it from "
                    + (grandfathered.contains(name) ? "GRANDFATHERED" : "CONSTANT_SUBJECTS / UNBANDED"));
            results.add(admission(AdmissionRule.CONSTANT, name, missing, ""));
        }
        for (Map.Entry<String, RuleKind> subject : CONSTANT_SUBJECTS.entrySet()) {
            if (subject.getValue() == null) {
                results.add(admission(AdmissionRule.CONSTANT, subject.getKey(),
                        java.util.Collections.singletonList("CONSTANT_SUBJECTS names no RuleKind for this field"), ""));
            }
        }
        results.add(admission(AdmissionRule.CONSTANT, "GRANDFATHERED", new ArrayList<String>(),
                grandfathered.size() + " pre-order-8 fields, shrink-only"));
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
     * R-TWIST (floor-twists): every COMBAT FLOOR TWIST, priced as COMBAT with the twist row folded on through
     * {@link RouteEconomicsModel#price(NodeEconomicsRegistry, NodeEconomics, NodeEconomics,
     * RouteEconomicsModel.ModelFloor, int)}, against plain COMBAT at the same depth, over every depth from the
     * twist's minimum to the run's last, worst depth reported:
     * <ul>
     *   <li>T1 — a twist that raises the threat (budget scale above 1) pays a reward premium / threat premium
     *       inside the SAME [ROUTE_RISK_PREMIUM_MIN, ROUTE_RISK_PREMIUM_MAX] band R-RISK-PREMIUM uses (AS25);</li>
     *   <li>T2 — a threat-neutral twist's total EV sits within +-TWIST_NEUTRAL_EV_TOLERANCE of COMBAT's;</li>
     *   <li>T3 — a twist that prints a hunter (HUNTED) declares at least the hunter's real price: the archetype's
     *       depth-scaled Threat Points (the encounter planner's own charge) over the COMBAT floor's Threat-Point
     *       budget may not exceed TWIST_HUNTED_THREAT_SHARE — the ledger may not under-price the hunter.</li>
     * </ul>
     * Coverage (a twist with no row) is R-ROUTE-PRICED's, which already iterates every registered affix id.
     */
    public static List<RuleResult> twistResults() {
        List<RuleResult> results = new ArrayList<>();
        NodeEconomicsRegistry ledger = routeLedger();
        RouteEconomicsModel.ModelFloor floor = routeModelFloor();
        NodeEconomics combat = ledger.get(ge.tbegvadze.toon3d.route.RouteEconomics.STANDARD_COMBAT_ID);
        for (ge.tbegvadze.toon3d.route.NodeAffixDefinition twist
                : RouteRegistries.affixes().definitionsFor(ge.tbegvadze.toon3d.route.AffixScope.FLOOR_TWIST)) {
            NodeEconomics row = ledger.get(twist.id());
            if (row == null || combat == null) {
                continue; // R-ROUTE-PRICED reports the missing row
            }
            int firstDepth = Math.max(1, twist.minDepth());
            if (row.budgetScale() > 1f) {
                results.add(worstTwistPremium(ledger, floor, combat, row, firstDepth));
            } else {
                results.add(worstTwistNeutrality(ledger, floor, combat, row, firstDepth));
            }
            if (twist.hunterEnemyTypeName() != null) {
                results.add(worstHunterShare(ledger, floor, row, twist, firstDepth));
            }
        }
        return results;
    }

    /** T1: the depth whose premium ratio sits furthest outside (or nearest the edge of) the risk-premium band. */
    private static RuleResult worstTwistPremium(NodeEconomicsRegistry ledger, RouteEconomicsModel.ModelFloor floor,
                                                NodeEconomics combat, NodeEconomics twist, int firstDepth) {
        float worstValue = Float.NaN;
        int   worstDepth = firstDepth;
        float worstExtremity = -1f;
        for (int depth = firstDepth; depth <= ROUTE_AUDIT_MAX_DEPTH; depth++) {
            RouteEconomicsModel.NodePrice standard = RouteEconomicsModel.standardCombat(ledger, floor, depth);
            RouteEconomicsModel.NodePrice priced   = RouteEconomicsModel.price(ledger, combat, twist, floor, depth);
            float ratio = GameMath.rewardPremiumRatio(priced.rewardPowerPoints, standard.rewardPowerPoints,
                    priced.threatCost, standard.threatCost);
            float extremity = bandExtremity(ratio, BalanceConfig.ROUTE_RISK_PREMIUM_MIN,
                    BalanceConfig.ROUTE_RISK_PREMIUM_MAX);
            if (extremity > worstExtremity) {
                worstExtremity = extremity;
                worstValue     = ratio;
                worstDepth     = depth;
            }
        }
        return bandedResult(RuleKind.TWIST, "T1 combat + " + twist.id() + " risk premium", worstValue,
                BalanceConfig.ROUTE_RISK_PREMIUM_MIN, BalanceConfig.ROUTE_RISK_PREMIUM_MAX,
                "worst depth " + worstDepth + " — reward premium / threat premium vs plain COMBAT");
    }

    /** T2: the depth whose EV departs furthest from plain COMBAT's, as a signed share of COMBAT's EV. */
    private static RuleResult worstTwistNeutrality(NodeEconomicsRegistry ledger, RouteEconomicsModel.ModelFloor floor,
                                                   NodeEconomics combat, NodeEconomics twist, int firstDepth) {
        float worstValue = 0f;
        int   worstDepth = firstDepth;
        for (int depth = firstDepth; depth <= ROUTE_AUDIT_MAX_DEPTH; depth++) {
            RouteEconomicsModel.NodePrice standard = RouteEconomicsModel.standardCombat(ledger, floor, depth);
            RouteEconomicsModel.NodePrice priced   = RouteEconomicsModel.price(ledger, combat, twist, floor, depth);
            float departure = GameMath.relativeDeparture(priced.expectedValue, standard.expectedValue);
            if (Float.isNaN(departure) || Math.abs(departure) > Math.abs(worstValue)) {
                worstValue = departure;
                worstDepth = depth;
                if (Float.isNaN(departure)) break;
            }
        }
        return bandedResult(RuleKind.TWIST, "T2 combat + " + twist.id() + " EV vs combat", worstValue,
                -BalanceConfig.TWIST_NEUTRAL_EV_TOLERANCE, BalanceConfig.TWIST_NEUTRAL_EV_TOLERANCE,
                "worst depth " + worstDepth + " — (twist EV - combat EV) / |combat EV|");
    }

    /** T3: the depth at which the hunter's real Threat-Point share of the COMBAT budget is largest. */
    private static RuleResult worstHunterShare(NodeEconomicsRegistry ledger, RouteEconomicsModel.ModelFloor floor,
                                               NodeEconomics twistRow,
                                               ge.tbegvadze.toon3d.route.NodeAffixDefinition twist, int firstDepth) {
        EnemyType hunter;
        try {
            hunter = EnemyType.valueOf(twist.hunterEnemyTypeName());
        } catch (IllegalArgumentException unknown) {
            return bandedResult(RuleKind.TWIST, "T3 " + twist.id() + " hunter share", Float.NaN, 0f,
                    BalanceConfig.TWIST_HUNTED_THREAT_SHARE, "unknown hunter archetype " + twist.hunterEnemyTypeName());
        }
        float worstShare = 0f;
        int   worstDepth = firstDepth;
        for (int depth = firstDepth; depth <= ROUTE_AUDIT_MAX_DEPTH; depth++) {
            RouteEconomicsModel.NodePrice standard = RouteEconomicsModel.standardCombat(ledger, floor, depth);
            float hunterThreat = GameMath.enemyThreatAtDepth(hunter.baseThreatPoints(),
                    BalanceConfig.ENEMY_HEALTH_GROWTH, BalanceConfig.ENEMY_DAMAGE_GROWTH, depth);
            float share = GameMath.threatShareOfBudget(hunterThreat, standard.threatCost);
            if (share > worstShare) {
                worstShare = share;
                worstDepth = depth;
            }
        }
        return bandedResult(RuleKind.TWIST, "T3 " + twist.id() + " hunter TP share", worstShare, 0f,
                BalanceConfig.TWIST_HUNTED_THREAT_SHARE,
                "worst depth " + worstDepth + " — " + hunter.name() + " TP / COMBAT floor TP (declared "
                        + String.format("%.2f", twistRow.budgetScale() - 1f) + ")");
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
            // OVERRIDE (balance-overhaul order 6, F15): an EVENT's worth is a CHOICE in ladder currencies and must be
            // at least a COMBAT floor's supply — R-EVENT-VALUE governs it, so it is out of R-CALM-COST's domain.
            if (node.eventCatalogReward()) {
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
     * R-MYSTERY-EV v2 (balance-overhaul order 6, F15 — OVERRIDES "the weighted table within +/-15% of a combat
     * node"): every mystery outcome now pays in LADDER CURRENCIES (MysteryOutcome.reward), and at every depth
     * 1..RUN_FINAL_DEPTH (1) the weighted table's EV is AT LEAST a standard combat node's — a gamble is worth
     * taking — and at most ROUTE_MYSTERY_EV_PREMIUM_MAX above it — never the strictly-better pick; (2) its
     * outcomes SPREAD: their weighted standard deviation is at least ROUTE_MYSTERY_MIN_SPREAD_FRACTION of the
     * combat node's EV (a combat node's variance is zero, so this is "higher variance" with teeth); and (3) the
     * worst outcome (MALFUNCTION) stays "bad but survivable" as arithmetic: its expected NET resource loss may
     * not exceed one floor's maximum modelled drain times {@code ROUTE_MYSTERY_WORST_LOSS_FLOOR_MULTIPLIER}.
     */
    public static List<RuleResult> mysteryExpectedValueResults() {
        List<RuleResult> results = new ArrayList<>();
        NodeEconomicsRegistry ledger = routeLedger();
        RouteEconomicsModel.ModelFloor floor = routeModelFloor();
        NodeEconomics mystery = ledger.forNodeType(RouteNodeType.MYSTERY);
        List<NodeEconomics> outcomes = ledger.allOfKind(NodeEconomics.Kind.MYSTERY_OUTCOME);

        float worstRatio = Float.NaN;
        float worstExtremity = -1f;
        int   worstDepth = 1;
        float smallestSpread = Float.POSITIVE_INFINITY;
        int   spreadDepth = 1;
        float ratioMax = 1f + BalanceConfig.ROUTE_MYSTERY_EV_PREMIUM_MAX;
        for (int depth = 1; depth <= ROUTE_AUDIT_MAX_DEPTH; depth++) {
            RouteEconomicsModel.NodePrice standard = RouteEconomicsModel.standardCombat(ledger, floor, depth);
            RouteEconomicsModel.NodePrice table    = RouteEconomicsModel.price(ledger, mystery, floor, depth);
            float ratio = standard.expectedValue == 0f ? 1f : table.expectedValue / standard.expectedValue;
            float extremity = bandExtremity(ratio, 1f, ratioMax);
            if (extremity > worstExtremity) {
                worstExtremity = extremity;
                worstRatio     = ratio;
                worstDepth     = depth;
            }
            float totalWeight = 0f, weightedSquares = 0f;
            for (NodeEconomics outcome : outcomes) {
                float deviation = RouteEconomicsModel.price(ledger, outcome, floor, depth).expectedValue
                        - table.expectedValue;
                totalWeight     += outcome.tableWeight();
                weightedSquares += outcome.tableWeight() * deviation * deviation;
            }
            float spread = totalWeight <= 0f || standard.expectedValue == 0f ? 0f
                    : (float) Math.sqrt(weightedSquares / totalWeight) / standard.expectedValue;
            if (spread < smallestSpread) {
                smallestSpread = spread;
                spreadDepth    = depth;
            }
        }
        results.add(new RuleResult(RuleKind.MYSTERY_EV, "weighted table EV / combat EV", worstRatio,
                1f, ratioMax, worstRatio >= 1f && worstRatio <= ratioMax,
                "worst depth " + worstDepth + " — a gamble worth taking, never the strictly-better pick"));
        results.add(new RuleResult(RuleKind.MYSTERY_EV, "outcome spread / combat EV", smallestSpread,
                BalanceConfig.ROUTE_MYSTERY_MIN_SPREAD_FRACTION, Float.POSITIVE_INFINITY,
                smallestSpread >= BalanceConfig.ROUTE_MYSTERY_MIN_SPREAD_FRACTION,
                "smallest at depth " + spreadDepth + " — weighted std of outcome EVs (combat's is zero)"));

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
     * R-EVENT-VALUE (balance-overhaul order 6, F15): every registered EVENT offers at least one choice whose
     * ladder-currency value (RouteEconomicsModel.bestChoicePrice — resources + durability-weighted progress, a
     * gamble at its expectation) is worth at least EVENT_BEST_CHOICE_MIN_SUPPLY_RATIO x what ONE standard COMBAT
     * floor SUPPLIES (ammo + heal + credits), at every depth 1..RUN_FINAL_DEPTH. An event is never a choice
     * between two scraps.
     */
    public static List<RuleResult> eventValueResults() {
        List<RuleResult> results = new ArrayList<>();
        NodeEconomicsRegistry ledger = routeLedger();
        RouteEconomicsModel.ModelFloor floor = routeModelFloor();
        for (ge.tbegvadze.toon3d.route.FacilityEvent event : RouteRegistries.events().all()) {
            float worstRatio = Float.POSITIVE_INFINITY;
            int   worstDepth = 1;
            for (int depth = 1; depth <= ROUTE_AUDIT_MAX_DEPTH; depth++) {
                float supply = RouteEconomicsModel.combatFloorSupplyPowerPoints(ledger, floor, depth);
                float best   = RouteEconomicsModel.bestChoicePrice(event, floor, depth).expectedValue();
                float ratio  = supply <= 0f ? Float.POSITIVE_INFINITY : best / supply;
                if (ratio < worstRatio) {
                    worstRatio = ratio;
                    worstDepth = depth;
                }
            }
            results.add(new RuleResult(RuleKind.EVENT_VALUE, event.id() + " best choice / combat supply", worstRatio,
                    BalanceConfig.EVENT_BEST_CHOICE_MIN_SUPPLY_RATIO, Float.POSITIVE_INFINITY,
                    worstRatio >= BalanceConfig.EVENT_BEST_CHOICE_MIN_SUPPLY_RATIO,
                    "worst depth " + worstDepth + " — ladder-currency value of its best choice"));
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
        generator.setTwistPool(RouteRegistries.affixes().definitionsFor(ge.tbegvadze.toon3d.route.AffixScope.FLOOR_TWIST));
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
            // V7 (mixed-encounter-groups): no room holds more than GROUP_ROOM_MAX_SAME_TYPE of one archetype.
            int crowdedRoom = 0;
            for (ge.tbegvadze.toon3d.level.FloorContentReport report : reports) {
                crowdedRoom = Math.max(crowdedRoom, report.roomMaximumSameArchetype);
            }
            results.add(new RuleResult(RuleKind.DENSITY, where + "room variety (V7)", crowdedRoom, 0f,
                    BalanceConfig.GROUP_ROOM_MAX_SAME_TYPE, crowdedRoom <= BalanceConfig.GROUP_ROOM_MAX_SAME_TYPE,
                    "most of one archetype in one room, worst floor"));
        }
        return results;
    }

    // =====================================================================================
    // R-DENSITY VARIETY (mixed-encounter-groups, V1-V6) — what the PLANNER fields per group: no archetype
    // past a pair in a group (a BROOD of exactly GROUP_BROOD_SIZE excepted, at most one a floor), every group
    // of GROUP_MIXED_BEHAVIOUR_MIN_SIZE+ mixing behaviours, few single-archetype groups, several archetypes
    // per group, and no archetype past its share of a floor's bodies. A planner sweep (no layouts), so cheap.
    // =====================================================================================

    /** Depths the variety sweep audits. */
    private static final int[] VARIETY_DEPTHS = {1, 5, 10, 15, 20, 25};
    /** Seeds per spec x depth cell of the variety sweep. */
    private static final int   VARIETY_SEEDS  = 100;
    /** A3: at most this share of groups (size >= 2) field a single archetype on a COMBAT floor. */
    private static final float VARIETY_MONO_SHARE_MAX     = 0.10f;
    /** A4: at least this many distinct archetypes per group, on average, on COMBAT / ELITE floors. */
    private static final float VARIETY_DISTINCT_MEAN_MIN  = 2.3f;

    /** R-DENSITY's variety half over the planner: one result per check per spec x depth cell. */
    public static List<RuleResult> densityVarietyResults() {
        List<RuleResult> results = new ArrayList<>();
        ge.tbegvadze.toon3d.route.NodeSupplySpec[] specs = {
                ge.tbegvadze.toon3d.route.NodeSupplySpecs.combat(),
                ge.tbegvadze.toon3d.route.NodeSupplySpecs.elite(),
                ge.tbegvadze.toon3d.route.NodeSupplySpecs.cache()};
        for (ge.tbegvadze.toon3d.route.NodeSupplySpec spec : specs) {
            boolean combat  = spec.encounterKind() == ge.tbegvadze.toon3d.route.NodeSupplySpec.EncounterKind.COMBAT;
            boolean fights  = combat || spec.encounterKind() == ge.tbegvadze.toon3d.route.NodeSupplySpec.EncounterKind.ELITE;
            for (int depth : VARIETY_DEPTHS) {
                int worstSameInGroup = 0;
                int worstBroodSize   = 0;
                int mostBroods       = 0;
                int mixedGroups = 0, mixCandidates = 0;
                int monoGroups  = 0, monoCandidates = 0;
                int distinctSum = 0, distinctGroups = 0;
                int bodyShareBreaches = 0;
                int broodFloors = 0;
                for (long seed = 0; seed < VARIETY_SEEDS; seed++) {
                    ge.tbegvadze.toon3d.level.EncounterBudgetPlanner.Plan plan =
                            new ge.tbegvadze.toon3d.level.EncounterBudgetPlanner(
                                    depth, new java.util.Random(seed * 97L + depth), 1f, spec).plan();
                    int broods = 0;
                    for (ge.tbegvadze.toon3d.level.EncounterGroup group : plan.groups()) {
                        ge.tbegvadze.toon3d.level.EncounterGroupTemplate template =
                                ge.tbegvadze.toon3d.level.EncounterGroupTemplateRegistry.shared().get(group.templateId);
                        boolean brood = template != null && template.brood();
                        if (brood) {
                            broods++;
                            worstBroodSize = Math.max(worstBroodSize, group.size());
                        } else {
                            worstSameInGroup = Math.max(worstSameInGroup, group.maximumSameArchetype());
                            if (group.size() >= BalanceConfig.GROUP_MIXED_BEHAVIOUR_MIN_SIZE) {
                                mixCandidates++;
                                if (group.distinctBehaviours() >= 2) mixedGroups++;
                            }
                        }
                        if (group.size() >= 2) {
                            monoCandidates++;
                            if (group.distinctArchetypes() == 1) monoGroups++;
                        }
                        if (template == null || !template.lone()) {
                            distinctSum += group.distinctArchetypes();
                            distinctGroups++;
                        }
                    }
                    mostBroods = Math.max(mostBroods, broods);
                    if (broods > 0) broodFloors++;
                    int bodyLimit = Math.max(BalanceConfig.GROUP_CHAFF_SLOT_MIN,
                            (int) Math.ceil(BalanceConfig.GROUP_MAX_SINGLE_TYPE_BODY_FRACTION * plan.bodyTarget()));
                    java.util.EnumMap<ge.tbegvadze.toon3d.enemy.EnemyType, Integer> bodies =
                            new java.util.EnumMap<>(ge.tbegvadze.toon3d.enemy.EnemyType.class);
                    for (ge.tbegvadze.toon3d.enemy.EnemyType type : plan.enemies()) {
                        if (bodies.merge(type, 1, Integer::sum) == bodyLimit + 1) bodyShareBreaches++;
                    }
                }
                String where = "variety " + spec.type() + " d" + depth + " ";
                results.add(new RuleResult(RuleKind.DENSITY, where + "group cap (V1)", worstSameInGroup, 0f,
                        BalanceConfig.GROUP_MAX_SAME_TYPE_PER_GROUP,
                        worstSameInGroup <= BalanceConfig.GROUP_MAX_SAME_TYPE_PER_GROUP,
                        "most of one archetype in a non-BROOD group"));
                results.add(new RuleResult(RuleKind.DENSITY, where + "brood (V4)", mostBroods, 0f,
                        BalanceConfig.GROUP_BROOD_MAX_PER_FLOOR,
                        mostBroods <= BalanceConfig.GROUP_BROOD_MAX_PER_FLOOR
                                && (worstBroodSize == 0 || worstBroodSize == BalanceConfig.GROUP_BROOD_SIZE),
                        "broods per floor (max); largest brood " + worstBroodSize + "; floors with one "
                                + String.format(java.util.Locale.ROOT, "%.0f%%", 100f * broodFloors / VARIETY_SEEDS)));
                float mixShare = mixCandidates == 0 ? 1f : mixedGroups / (float) mixCandidates;
                results.add(new RuleResult(RuleKind.DENSITY, where + "mix (V3)", mixShare, 1f, 1f, mixShare >= 1f,
                        "share of non-BROOD groups of " + BalanceConfig.GROUP_MIXED_BEHAVIOUR_MIN_SIZE
                                + "+ spanning two behaviours"));
                results.add(new RuleResult(RuleKind.DENSITY, where + "body share (V6)", bodyShareBreaches, 0f, 0f,
                        bodyShareBreaches == 0, "floors x archetypes past their share of the bodies"));
                if (combat) {
                    float monoShare = monoCandidates == 0 ? 0f : monoGroups / (float) monoCandidates;
                    results.add(new RuleResult(RuleKind.DENSITY, where + "mono (A3)", monoShare, 0f,
                            VARIETY_MONO_SHARE_MAX, monoShare <= VARIETY_MONO_SHARE_MAX,
                            "share of groups of 2+ with one archetype"));
                }
                if (fights) {
                    float distinctMean = distinctGroups == 0 ? 0f : distinctSum / (float) distinctGroups;
                    results.add(new RuleResult(RuleKind.DENSITY, where + "distinct (A4)", distinctMean,
                            VARIETY_DISTINCT_MEAN_MIN, Float.POSITIVE_INFINITY,
                            distinctMean >= VARIETY_DISTINCT_MEAN_MIN, "mean archetypes per group (lone excluded)"));
                }
            }
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

    /**
     * C-CONSTANT's GRANDFATHERED list: every numeric BalanceConfig field that existed before balance-overhaul order 8.
     * SHRINK-ONLY: never add a name here; a field that becomes banded moves to CONSTANT_SUBJECTS; a deleted
     * field's name must be removed (C-CONSTANT fails a stale name). Sorted. A new field goes in CONSTANT_SUBJECTS
     * or UNBANDED, never here.
     */
    private static final String[] GRANDFATHERED = {
        "ABILITY_MIN_FELT_PP", "ABILITY_PP_LEGENDARY_SIGNATURE", "ABILITY_PP_PER_COUNT",
        "ABILITY_PP_PER_EXPECTED_REFUND_ROUND", "ABILITY_PP_PER_EXTRA_BURST_ROUND", "ABILITY_ROLL_SWEEP_DEPTHS",
        "ABILITY_ROLL_SWEEP_SEEDS", "ABILITY_UTIL_ALWAYS_ON", "ABILITY_UTIL_CONDITIONAL",
        "ABILITY_UTIL_CROWD_CONTROL", "ABILITY_UTIL_DAMAGE_OVER_TIME", "ABILITY_UTIL_FLAT_EVENT",
        "ABILITY_UTIL_PENETRATION", "ABILITY_UTIL_SUSTAIN_FRACTION", "ACID_DRONE_ATTACK_DAMAGE",
        "ACID_DRONE_MAX_HEALTH", "ACID_DRONE_MOVE_EVERY_N_TURNS", "ACID_DRONE_POISON_CHANCE",
        "ACID_DRONE_RANGE_TILES", "AFFIX_FORTIFIED_EXTRA_ARMOUR", "AFFIX_FORTIFIED_EXTRA_COLUMNS",
        "AFFIX_IRRADIATED_BARREL_WEIGHT_BONUS", "AFFIX_IRRADIATED_EXTRA_BARRELS", "AFFIX_OVERCLOCKED_BUDGET_MULT",
        "AFFIX_OVERCLOCKED_VAULT_AMMO_BOXES", "AFFIX_ROLL_CHANCE_ELITE", "AFFIX_ROLL_CHANCE_MYSTERY",
        "AFFIX_SWARM_BUDGET_MULT", "AFFIX_SWARM_VAULT_AMMO_BOXES", "AFFIX_VOLATILE_EXTRA_BARRELS",
        "AGI_DODGE_PER_POINT", "AGI_SPEED_PER_POINT", "ALERT_RADIUS_TILES", "AMMO_BANKING_FLOORS_BULLETS",
        "AMMO_BANKING_FLOORS_CELLS", "AMMO_BANKING_FLOORS_ROCKETS", "AMMO_BANKING_FLOORS_SHELLS",
        "AMMO_BANKING_FLOORS_SLUGS", "AMMO_BANKING_FLOORS_TOLERANCE", "AMMO_BOX_BULLETS", "AMMO_BOX_CELLS",
        "AMMO_BOX_ROCKETS", "AMMO_BOX_SHELLS", "AMMO_RESERVE_CAP_BULLETS", "AMMO_RESERVE_CAP_CELLS",
        "AMMO_RESERVE_CAP_ROCKETS", "AMMO_RESERVE_CAP_SHELLS", "AMMO_SUPPLY_GENEROSITY_BULLETS",
        "AMMO_SUPPLY_GENEROSITY_CELLS", "AMMO_SUPPLY_GENEROSITY_ROCKETS", "AMMO_SUPPLY_GENEROSITY_SHELLS",
        "AMMO_SUPPLY_GENEROSITY_SLUGS", "ARC_CANNON_CHAIN_DAMAGE_MULTIPLIER", "ARC_CANNON_CHAIN_JUMPS",
        "ARC_CANNON_CLIP_SIZE", "ARC_CANNON_DAMAGE", "ARC_CANNON_DAMAGE_DROP_COEFF", "ARC_CANNON_RANGE_TILES",
        "ARC_CANNON_RELOAD_TIME_TICKS", "ARMOR_PIERCE_ALREADY_PIERCING_BONUS", "ARMOR_PIERCE_BASE",
        "ARMOR_PIERCE_CAP", "ARMOR_PIERCE_EXTRA_TARGETS", "ARMOR_PIERCE_PER_LEVEL", "ARMOUR_ABSORB_FRACTION",
        "ARMOUR_HEAVY_SHRED_MULTIPLIER", "ARMOUR_SHARD_FRACTION", "ARMOUR_STATUS_COST_FRACTION",
        "ARMOUR_VEST_FRACTION", "ASSAULT_RIFLE_CLIP_SIZE", "ASSAULT_RIFLE_DAMAGE", "ASSAULT_RIFLE_DAMAGE_DROP_COEFF",
        "ASSAULT_RIFLE_RANGE_TILES", "ASSAULT_RIFLE_RELOAD_TIME_TICKS", "AURIC_MAX_SHARDS",
        "AURIC_SENTINEL_ATTACK_CADENCE_TURNS", "AURIC_SENTINEL_ATTACK_DAMAGE", "AURIC_SENTINEL_MAX_HEALTH",
        "AURIC_SENTINEL_MIN_SPAWN_DEPTH", "AURIC_SENTINEL_MOVE_EVERY_N_TURNS", "AURIC_SENTINEL_RANGE_TILES",
        "AURIC_SHARD_ARMOR_POOL", "AURIC_SHARD_REGEN_TURNS", "BACKSTAB_DAMAGE_PERCENT", "BERSERKER_DAMAGE_PER_STACK",
        "BERSERKER_HP_TICK_PER_STACK", "BERSERKER_MAX_STACKS", "BLIGHT_CORRUPTOR_ATTACK_DAMAGE",
        "BLIGHT_CORRUPTOR_MAX_HEALTH", "BLIGHT_CORRUPTOR_MOVE_EVERY_N_TURNS", "BLIND_DURATION", "BLOCK_DECAY_TURNS",
        "BLOCK_MAX", "BODY_TARGET_MAX_DEEP", "BODY_TARGET_MAX_DEPTH_ONE", "BODY_TARGET_MIN_DEEP",
        "BODY_TARGET_MIN_DEPTH_ONE", "BODY_TARGET_REFERENCE_DEEP_DEPTH", "BOSS_ACT_PHASE_COUNT_MAX",
        "BOSS_ACT_PHASE_COUNT_MIN", "BOSS_ARENA_AMMO_BUDGET_FRACTION", "BOSS_ENRAGE_DPT_RAMP_PER_TURN",
        "BOSS_EXPECTED_LEVELS_PER_DEPTH", "BOSS_EXPECTED_OFFENCE_BUDGET_FRACTION", "BOSS_FIGHT_TURNS_TOLERANCE",
        "BOSS_GATE_MIN_DAMAGE_MARGIN", "BOSS_GATE_MODELED_HEAL_SUPPLY_EHP_FRACTION", "BOSS_HARD_SINGLE_HIT_FRACTION",
        "BOSS_MULTI_PHASE_FACTOR_PER_PHASE", "BOSS_PHASE_COUNT", "BOSS_REWARD_RISK_PREMIUM",
        "BOSS_SURVIVAL_CHECK_RATIO_MAX", "BOSS_SURVIVAL_CHECK_RATIO_MIN", "BOSS_SURVIVAL_CHECK_RATIO_TARGET",
        "BOSS_TABLE_DEPTHS", "BOSS_TABLE_SURVIVAL_RATIO", "BOSS_TABLE_TARGET_FIGHT_TURNS", "BOSS_TACTIC_CAMP_TURNS",
        "BOSS_TACTIC_CHARGE_GAP_PHASE1", "BOSS_TACTIC_CHARGE_GAP_PHASE2", "BOSS_TACTIC_CLOSE_RANGE_TILES",
        "BOSS_TACTIC_CORNERED_EXITS", "BOSS_TACTIC_HAZARD_GAP_PHASE1", "BOSS_TACTIC_HAZARD_GAP_PHASE2",
        "BOSS_TACTIC_LAST_STAND_HP_FRACTION", "BOSS_TACTIC_RANDOM_TIEBREAK", "BOSS_TACTIC_SCORE_ADDS_ROOM",
        "BOSS_TACTIC_SCORE_BASE", "BOSS_TACTIC_SCORE_CAMPING", "BOSS_TACTIC_SCORE_CLOSE_RANGE",
        "BOSS_TACTIC_SCORE_CORNERED", "BOSS_TACTIC_SCORE_FAR_RANGE", "BOSS_TACTIC_SUMMON_GAP_PHASE1",
        "BOSS_TACTIC_SUMMON_GAP_PHASE2", "BOSS_TARGET_FIGHT_TURNS_ACT_MAX", "BOSS_TARGET_FIGHT_TURNS_ACT_MIN",
        "BOSS_TARGET_FIGHT_TURNS_RUN_FINAL_MAX", "BOSS_TARGET_FIGHT_TURNS_RUN_FINAL_MIN",
        "BOSS_TELEGRAPHED_HEAVY_MAX_FRACTION", "BOSS_UPPER_FIGHT_TURNS_MULTIPLIER", "BRANCH_SPREAD_CHANCE",
        "BRANCH_WIDTH_MAXIMUM", "BRANCH_WIDTH_MINIMUM", "BUILD_BERSERK_LOW_HEALTH_FRACTION",
        "BUILD_BLADE_LOW_HEALTH_TURN_SHARE", "BUILD_BLADE_MELEE_KILL_SHARE", "BUILD_CARRY_DEPTH",
        "BUILD_CARRY_MIN_TURNS", "BUILD_CARRY_TOLERANCE", "BUILD_EXPECTED_FOCUS_SHARE",
        "BUILD_FLOOR_HEAVY_DAMAGE_SHARE", "BUILD_GUARD_LANES", "BUILD_HEAVY_PERIOD_TURNS", "BUILD_KIT_GUN_SLOTS",
        "BUILD_LONG_RANGE_MIN_TILES", "BUILD_PERK_REFERENCE_DEPTHS", "BUILD_POINT_BLANK_MAX_TILES",
        "BUILD_PYRO_FIRE_TURNS_PER_FLOOR", "BUILD_SUPPLY_SEED_COUNT", "BUILD_SUPPLY_SEED_SALT",
        "BUILD_WIDE_GUARD_EXTRA_LANES", "BURN_DURATION_MAX", "BURN_DURATION_MIN", "BURN_TICK_FRACTION",
        "BURST_FIRE_ROUND_COUNT", "CARRIER_DROP_SEARCH_RADIUS", "CHAINGUN_CLIP_SIZE", "CHAINGUN_DAMAGE",
        "CHAINGUN_DAMAGE_DROP_COEFF", "CHAINGUN_RANGE_TILES", "CHAINGUN_RELOAD_TIME_TICKS",
        "CHAIN_ALERT_RADIUS_TILES", "CINDERFORGE_COLOSSUS_ATTACK_DAMAGE", "CINDERFORGE_COLOSSUS_MAX_HEALTH",
        "CINDERFORGE_COLOSSUS_MIN_SPAWN_DEPTH", "CINDERFORGE_COLOSSUS_MOVE_EVERY_N_TURNS",
        "CINDERFORGE_CRUST_ARMOR_PER_STACK", "CINDERFORGE_CRUST_AVERAGED_FLAT_REDUCTION",
        "CINDERFORGE_CRUST_MAX_STACKS", "CINDERFORGE_CRUST_SHATTER_MULTIPLIER", "CINDERFORGE_TRAIL_FIRE_TURNS",
        "CINDERFORGE_TRAIL_MAX_LIVE_TILES", "CLEAVE_FRACTION_BASE", "CLEAVE_FRACTION_CAP",
        "CLEAVE_FRACTION_PER_LEVEL", "CORRUPTOR_ACID_DPT_FRACTION", "CORRUPTOR_ACID_POOL_DURATION",
        "CORRUPTOR_MINION_CAP", "CORRUPTOR_SUMMON_COOLDOWN", "CRAWLER_ATTACK_DAMAGE", "CRAWLER_MAX_HEALTH",
        "CRAWLER_MOVE_EVERY_N_TURNS", "CREDITS_NEEDED_CONSUMABLES_PER_FLOOR", "CREDITS_V2_RATIO_MAX",
        "CREDITS_V2_RATIO_MIN", "CREDIT_BANK_TARGET_FLOORS", "CREDIT_DEPTH_SCALE", "CREDIT_REWARD_ACID_DRONE",
        "CREDIT_REWARD_AURIC_SENTINEL", "CREDIT_REWARD_BLIGHT_CORRUPTOR", "CREDIT_REWARD_CINDERFORGE_COLOSSUS",
        "CREDIT_REWARD_CRAWLER", "CREDIT_REWARD_EYE_TYRANT", "CREDIT_REWARD_GHOUL", "CREDIT_REWARD_GORE_BITER",
        "CREDIT_REWARD_IRON_STALKER", "CREDIT_REWARD_MIRE_WRAITH", "CREDIT_REWARD_PLAGUE_HULK",
        "CREDIT_REWARD_REVENANT", "CREDIT_REWARD_RIMESHELL_LANCER", "CREDIT_REWARD_SHELL_BRUTE",
        "CREDIT_REWARD_VERDANT_SPIRESOWER", "CREDIT_REWARD_VOID_SHROUD", "CREDIT_REWARD_VORTEX_EYE",
        "CREDIT_SPAWN_WEIGHT_LARGE", "CREDIT_SPAWN_WEIGHT_MEDIUM", "CREDIT_SPAWN_WEIGHT_SMALL", "CRIT_CHANCE_BASE",
        "CRIT_CHANCE_CAP", "CRIT_CHANCE_PER_LEVEL", "CRIT_DAMAGE_MULTIPLIER", "DAMAGE_MIN_MULTIPLIER",
        "DBL_SHOTGUN_CLIP_SIZE", "DBL_SHOTGUN_DAMAGE", "DBL_SHOTGUN_RANGE_TILES", "DBL_SHOTGUN_RELOAD_TIME_TICKS",
        "DEBUFF_TP_EQUIVALENCE", "DEFEND_BLOCK_GAIN_BRUISER", "DEFEND_BLOCK_GAIN_MINI_ELITE",
        "DEFEND_BLOCK_GAIN_SOLDIER", "DEFEND_BRUISER_CADENCE_TURNS", "DEFEND_HP_THRESHOLD_FRACTION",
        "DEFEND_MAX_CONSECUTIVE_TURNS", "DENSITY_CALM_MAX", "DENSITY_CALM_MIN", "DENSITY_COMBAT_MAX",
        "DENSITY_COMBAT_MIN", "DENSITY_ELITE_MAX", "DENSITY_ELITE_MIN", "DODGE_CAP", "DOUBLE_BARREL_FALLOFF_BY_TILE",
        "ELITE_ARMOUR", "ELITE_ARMOUR_VEST_DEPTH", "ELITE_MEDKITS", "ELITE_THREAT_RATIO_MAX",
        "ELITE_THREAT_RATIO_MIN", "EMERGENCY_SUPPLY_FRACTION", "EMPOWERED_DAMAGE_PERCENT", "EMPOWERED_DURATION",
        "ENCOUNTER_BUDGET_FILL_TARGET_FRACTION", "ENEMY_DAMAGE_GROWTH", "ENEMY_HEALTH_GROWTH",
        "EVENT_BEST_CHOICE_MIN_SUPPLY_RATIO", "EVENT_BUDGET_SCALE", "EVENT_CREDIT_FLOORS_LARGE",
        "EVENT_CREDIT_FLOORS_SMALL", "EVENT_GAMBLE_FAIL_HEALTH_FRACTION", "EVENT_GAMBLE_HEAL_FRACTION",
        "EVENT_NEXT_FLOOR_BUDGET_BONUS", "EVENT_SHRINE_HEAL_FRACTION", "EVENT_STASH_CREDIT_FLOORS",
        "EVENT_STASH_LOOT_CHANCE", "EVENT_TECH_ARMOUR_FRACTION", "EXECUTIONER_BONUS_BASE", "EXECUTIONER_BONUS_CAP",
        "EXECUTIONER_BONUS_PER_LEVEL", "EXECUTIONER_THRESHOLD", "EXPECTED_LEVELS_PER_DEPTH",
        "EXPECTED_PERK_POWER_PP_PER_PICK", "EXPLOSION_CHAIN_MAX", "EXPOSED_DURATION", "EYE_TYRANT_ATTACK_DAMAGE",
        "EYE_TYRANT_MAX_HEALTH", "EYE_TYRANT_RANGE_TILES", "FIRST_CONTACT_FIRST_FLOOR_MAX_WALK_TILES",
        "FIRST_CONTACT_MAX_WALK_TILES", "FIRST_CONTACT_MIN_WALK_TILES", "FLAME_BURN_FRACTION",
        "FLAME_BURN_MAX_STACKS", "FLAME_BURN_TURNS", "FLAME_CLIP_SIZE", "FLAME_DAMAGE_DROP_COEFF", "FLAME_FALLOFF",
        "FLAME_IMPACT_DAMAGE", "FLAME_MAX_FUEL", "FLAME_PICKUP_FUEL", "FLAME_RANGE_TILES", "FLAME_RELOAD_TICKS",
        "FLOOR_BASE_THREAT_POINT_BUDGET", "FOOTPRINT_ORIGINAL_WALKABLE_CAVERN", "FOOTPRINT_ORIGINAL_WALKABLE_LINEAR",
        "FOOTPRINT_ORIGINAL_WALKABLE_ROOMS", "FOOTPRINT_REDUCTION_BY_DEPTH", "FOOTPRINT_TOLERANCE",
        "GATE_BUDGET_SCALE", "GEAR_CURVE_REGION_BAND_SIZE", "GHOUL_ATTACK_DAMAGE", "GHOUL_MAX_HEALTH",
        "GHOUL_MOVE_EVERY_N_TURNS", "GORE_BITER_ATTACK_DAMAGE", "GORE_BITER_MAX_HEALTH",
        "GORE_BITER_MOVE_EVERY_N_TURNS", "GRENADE_CLIP_SIZE", "GRENADE_DAMAGE_DROP_COEFF", "GRENADE_FALLOFF_DAMAGE",
        "GRENADE_MAX_AMMO", "GRENADE_PICKUP_AMMO", "GRENADE_RANGE_TILES", "GRENADE_RELOAD_TIME_TICKS",
        "GRENADE_SELF_DAMAGE", "GRENADE_SPLASH_DAMAGE", "GROUP_CHAFF_SLOT_MIN", "GROUP_HUNTER_MAX_FRACTION",
        "GROUP_INSTANTIATION_ATTEMPTS", "GROUP_MAX_SINGLE_TYPE_FRACTION", "GROUP_MINIMUM_FILLABLE_REMAINDER",
        "GROUP_SELECTION_NOISE", "GROUP_TP_FRACTION_CAP", "GROUP_WARBAND_MIN_DEPTH", "GUARD_BACK_HALF_ANGLE_DEGREES",
        "GUARD_BACK_MULTIPLIER", "GUARD_FRONT_HALF_ANGLE_DEGREES", "GUARD_FRONT_HEAVY", "GUARD_FRONT_ORDINARY",
        "GUARD_SIDE_MULTIPLIER", "HAZARD_BARREL_CENTRE_FRACTION", "HAZARD_BARREL_PLAYER_CENTRE_FRACTION",
        "HAZARD_BARREL_PLAYER_RING_FRACTION", "HAZARD_BARREL_REFERENCE_SOLDIER_BASE_HEALTH",
        "HAZARD_BARREL_RING_FRACTION", "HAZARD_EXPLOSION_IGNITE_CHANCE", "HAZARD_FIRE_BURN_TURNS",
        "HAZARD_FIRE_ENEMY_DAMAGE_MULTIPLIER", "HAZARD_FIRE_LIFETIME_TURNS", "HAZARD_FIRE_SPREAD_CHANCE",
        "HAZARD_GROUP_BARRELS_MAX", "HAZARD_GROUP_BARRELS_MIN", "HAZARD_GROUP_BARREL_RADIUS_TILES",
        "HAZARD_GROUP_MIN_SIZE", "HAZARD_GROUP_ROOM_SHARE", "HAZARD_PLAGUE_HULK_DEATH_CLOUD_RADIUS",
        "HAZARD_TOXIC_ENEMY_STACKS_PER_TURN", "HAZARD_TOXIC_LIFETIME_TURNS", "HAZARD_TOXIC_SPREAD_CHANCE",
        "HEAL_LARGE_MAX_FRACTION_MAX", "HEAL_LARGE_MAX_FRACTION_MIN", "HEAL_NET_DRAIN_FRACTION_MAX",
        "HEAL_NET_DRAIN_FRACTION_MIN", "HEAL_SMALL_MAX_FRACTION_MAX", "HEAL_SMALL_MAX_FRACTION_MIN",
        "HEAVY_AREA_STRIKE_DAMAGE_FRACTION", "HEAVY_DAMAGE_FRACTION", "HEAVY_DAMAGE_FRACTION_MAX",
        "HEAVY_DAMAGE_FRACTION_MIN", "HEAVY_DAMAGE_SCALE", "HEAVY_EXPECTED_AVOIDANCE", "HEAVY_GUARDED_MAX_FRACTION",
        "HEAVY_SELF_DESTRUCT_DAMAGE_FRACTION", "HELLFIRE_NOVA_DAMAGE_FRACTION", "HELLFIRE_NOVA_RADIUS",
        "HELL_BARON_CLEAVE_P1_DPT_FRACTION", "HELL_BARON_CLEAVE_P2_DPT_FRACTION", "HELL_BARON_FIREWALL_COOLDOWN_P1",
        "HELL_BARON_FIREWALL_COOLDOWN_P2", "HELL_BARON_FIREWALL_DURATION", "HELL_BARON_FIRE_DPT_FRACTION",
        "INCENDIARY_BURN_DURATION", "INCENDIARY_BURN_PER_TURN_BASE", "INCENDIARY_BURN_PER_TURN_CAP",
        "INCENDIARY_BURN_PER_TURN_PER_LEVEL", "INCENDIARY_INCINERATOR_EXTRA_TURNS", "IRON_STALKER_MAX_HEALTH",
        "IRON_STALKER_MELEE_DAMAGE", "IRON_STALKER_MOVE_EVERY_N_TURNS", "IRON_STALKER_RANGED_DAMAGE",
        "IRON_STALKER_RANGE_TILES", "JUDGMENT_COOLDOWN_FIRES", "JUDGMENT_DAMAGE_MULTIPLIER", "JUDGMENT_LANCE_RANGE",
        "KEYSTONES_OFFERED", "KEYSTONE_BERSERKER_HEAL_MULTIPLIER", "KEYSTONE_BERSERKER_KILL_HEAL",
        "KEYSTONE_BERSERKER_LOW_HEALTH_MELEE", "KEYSTONE_CHARACTER_LEVELS", "KEYSTONE_COST_MIN_LOSS",
        "KEYSTONE_DEADEYE_CLIP_MULTIPLIER", "KEYSTONE_DEMOLITIONIST_BARREL_MULTIPLIER",
        "KEYSTONE_DEMOLITIONIST_HEALTH_MULTIPLIER", "KEYSTONE_IRONHIDE_HEALTH_MULTIPLIER",
        "KEYSTONE_IRONHIDE_HEAL_MULTIPLIER", "KEYSTONE_IRON_BULWARK_DAMAGE_MULTIPLIER",
        "KEYSTONE_IRON_BULWARK_REFLECT_FRACTION", "KEYSTONE_NICHE_MIN_GAIN", "KEYSTONE_PYROMANIAC_ARMOR_MULTIPLIER",
        "KEYSTONE_PYROMANIAC_BURNING_DAMAGE", "KEYSTONE_QUARTERMASTER_EXTRA_SLOTS",
        "KEYSTONE_QUARTERMASTER_HEALTH_MULTIPLIER", "KEYSTONE_QUARTERMASTER_PRICE_MULTIPLIER",
        "KEYSTONE_SCATTERGUN_NON_SPREAD_MULTIPLIER", "KEYSTONE_STORM_CONDUIT_ARC_FRACTION",
        "KEYSTONE_STORM_CONDUIT_BALLISTIC_MULTIPLIER", "KINETIC_SLAM_CHANCE_BASE", "KINETIC_SLAM_CHANCE_CAP",
        "KINETIC_SLAM_CHANCE_PER_LEVEL", "KINETIC_SLAM_WALL_BONUS_DAMAGE", "LADDER_AFFORD_FRACTION",
        "LADDER_AHEAD_TWO_MIN_TTK_RATIO", "LADDER_EXPECTED_DEFENCE_BUDGET_FRACTION",
        "LADDER_EXPECTED_OFFENCE_BUDGET_FRACTION", "LADDER_GROWTH", "LADDER_LAG_FOUR_MIN_TTK_RATIO",
        "LADDER_LAG_TWO_MIN_TTK_RATIO", "LADDER_LEGENDARY_MIN_GAIN", "LADDER_LEVEL_FELT_MIN_GAIN",
        "LADDER_LEVEL_UP_POWER_POINTS", "LADDER_ON_CURVE_TOLERANCE", "LADDER_RARITY_STEP_MIN_GAIN",
        "LADDER_REFERENCE_RANGE_TILES", "LADDER_START_WEAPON_FROM_DEPTH", "LADDER_START_WEAPON_MIN_TTK_RATIO",
        "LADDER_TP_BAND_HIGH_FACTOR", "LADDER_TP_BAND_LOW_FACTOR", "LADDER_TTD_HITS_BRUISER_MAX",
        "LADDER_TTD_HITS_BRUISER_MIN", "LADDER_TTD_HITS_CHAFF_MIN", "LADDER_TTD_HITS_MINI_ELITE_MAX",
        "LADDER_TTD_HITS_MINI_ELITE_MIN", "LADDER_TTD_HITS_SOLDIER_MAX", "LADDER_TTD_HITS_SOLDIER_MIN",
        "LADDER_TTD_OPEN_BAND_CENTRE_FACTOR", "LADDER_TTK_HITS_BRUISER_MAX", "LADDER_TTK_HITS_BRUISER_MIN",
        "LADDER_TTK_HITS_CHAFF_MAX", "LADDER_TTK_HITS_CHAFF_MIN", "LADDER_TTK_HITS_MINI_ELITE_MAX",
        "LADDER_TTK_HITS_MINI_ELITE_MIN", "LADDER_TTK_HITS_SOLDIER_MAX", "LADDER_TTK_HITS_SOLDIER_MIN",
        "LADDER_VITALITY_LAG_LEVELS", "LADDER_VITALITY_LAG_MAX_TTD_RATIO", "LANCE_SLOW_DURATION_TURNS",
        "LEVEL_GAP_BONUS_CAP_LEVELS", "LEVEL_GAP_BONUS_PER_LEVEL", "LEVEL_GAP_FLOOR", "LEVEL_GAP_PENALTY_BASE",
        "LEVEL_GAP_STEEPNESS", "LEVEL_GEN_DEPTH_ENEMY_BONUS_MAX", "LEVEL_GEN_DEPTH_ENEMY_UPGRADE_CHANCE",
        "LEVEL_GEN_MAX_ENEMIES_PER_ROOM", "LIFESTEAL_BASE", "LIFESTEAL_CAP",
        "LIFESTEAL_PER_LEVEL", "LOS_MAX_RANGE_TILES", "MATCHUP_EFFECTIVE_THRESHOLD", "MATCHUP_RESISTED_THRESHOLD",
        "MATCHUP_ROW_BURNABLE", "MATCHUP_ROW_CHITIN", "MATCHUP_ROW_FLESH", "MATCHUP_ROW_INFERNAL",
        "MATCHUP_ROW_PLATED", "MATCHUP_ROW_SHIELDED", "MATCHUP_STRENGTH", "MATCHUP_TP_REFERENCE_WEIGHT",
        "MAX_WEAPON_LEVEL", "MEDKIT_FULL_HEAL_FRACTION", "MEDKIT_STIM_HEAL_FRACTION", "MELEE_CHAINSAW_DAMAGE",
        "MELEE_FIST_DAMAGE", "MELEE_HAMMER_DAMAGE", "MELEE_KNIFE_DAMAGE", "MIRE_WRAITH_ATTACK_DAMAGE",
        "MIRE_WRAITH_MAX_HEALTH", "MIRE_WRAITH_MOVE_EVERY_N_TURNS", "MIRE_WRAITH_POISON_CHANCE",
        "MIRE_WRAITH_RANGE_TILES", "MODEL_FLOOR_AVOIDANCE_FACTOR", "MODEL_FLOOR_EXPECTED_ARMOUR_PICKUPS",
        "MODEL_FLOOR_EXPECTED_MEDKITS", "MODEL_FLOOR_EYE_TYRANT_COUNT", "MODEL_FLOOR_GORE_BITER_COUNT",
        "MODEL_FLOOR_PLAGUE_HULK_COUNT", "MODEL_FLOOR_SHELL_BRUTE_COUNT", "MODEL_FLOOR_TURNS_ENGAGED_PER_ENEMY",
        "MRK_DAMAGE_PER_POINT", "MYSTERY_REWARD_AMBUSH_CREDIT_FLOORS", "MYSTERY_REWARD_LORE_CREDIT_FLOORS",
        "MYSTERY_REWARD_MALFUNCTION_CREDIT_COST_FLOORS", "MYSTERY_REWARD_TRAP_CREDIT_FLOORS",
        "MYSTERY_REWARD_VAULT_CREDIT_FLOORS", "MYSTERY_TRAP_AMMO_BOXES", "MYSTERY_TRAP_BARRELS",
        "MYSTERY_TRAP_MEDKITS", "MYSTERY_VAULT_AMMO_BOXES", "MYSTERY_WARREN_AMMO_BOXES", "MYSTERY_WARREN_MEDKITS",
        "MYSTERY_WEIGHT_AMBUSH", "MYSTERY_WEIGHT_LORE_SIGNAL", "MYSTERY_WEIGHT_MALFUNCTION",
        "MYSTERY_WEIGHT_RELIC_CACHE", "MYSTERY_WEIGHT_SECRET_WARREN", "MYSTERY_WEIGHT_TRAP_GAUNTLET",
        "MYSTERY_WEIGHT_VAULT", "NODE_SUPPLY_CACHE_AMMO_RATIO", "NODE_SUPPLY_CACHE_ARMOUR_SHARE",
        "NODE_SUPPLY_CACHE_BODIES", "NODE_SUPPLY_CACHE_CREDITS", "NODE_SUPPLY_CACHE_DRAIN_TARGET",
        "NODE_SUPPLY_CACHE_THREAT", "NODE_SUPPLY_CACHE_WEAPONS", "NODE_SUPPLY_CACHE_WEAPON_CHANCE",
        "NODE_SUPPLY_COMBAT_AMMO_RATIO", "NODE_SUPPLY_COMBAT_ARMOUR_SHARE", "NODE_SUPPLY_COMBAT_BODIES",
        "NODE_SUPPLY_COMBAT_CREDITS", "NODE_SUPPLY_COMBAT_DRAIN_TARGET", "NODE_SUPPLY_COMBAT_THREAT",
        "NODE_SUPPLY_COMBAT_WEAPONS", "NODE_SUPPLY_COMBAT_WEAPON_OFFSET_MAX", "NODE_SUPPLY_COMBAT_WEAPON_OFFSET_MIN",
        "NODE_SUPPLY_ELITE_AMMO_RATIO", "NODE_SUPPLY_ELITE_ARMOUR_SHARE", "NODE_SUPPLY_ELITE_BODIES",
        "NODE_SUPPLY_ELITE_CREDITS", "NODE_SUPPLY_ELITE_DRAIN_TARGET", "NODE_SUPPLY_ELITE_THREAT",
        "NODE_SUPPLY_ELITE_VAULT_AMMO_RATIO", "NODE_SUPPLY_ELITE_WEAPONS", "NODE_SUPPLY_ELITE_WEAPON_OFFSET_MAX",
        "NODE_SUPPLY_ELITE_WEAPON_OFFSET_MIN", "NODE_SUPPLY_ELITE_WEAPON_TIER_BONUS", "NODE_SUPPLY_EVENT_THREAT",
        "NODE_SUPPLY_GATE_THREAT", "NODE_SUPPLY_MYSTERY_BODIES", "NODE_SUPPLY_MYSTERY_THREAT",
        "NODE_SUPPLY_SHOP_AMMO_RATIO", "NODE_SUPPLY_SHOP_ARMOUR_SHARE", "NODE_SUPPLY_SHOP_BODIES",
        "NODE_SUPPLY_SHOP_CREDITS", "NODE_SUPPLY_SHOP_DRAIN_TARGET", "NODE_SUPPLY_SHOP_THREAT",
        "OPENING_SALVO_BONUS_BASE", "OPENING_SALVO_BONUS_CAP", "OPENING_SALVO_BONUS_PER_LEVEL", "OVERSEER_ADDS_CAP",
        "OVERSEER_CHARGE_DPT_FRACTION", "OVERSEER_CHARGE_RANGE_TILES", "OVERSEER_CHARGE_RECOVERY_TURNS",
        "OVERSEER_FIRE_LANE_LENGTH", "OVERSEER_HEAL_HP_THRESHOLD", "OVERSEER_HEAL_TOTAL_HP_FRACTION",
        "OVERSEER_HEAL_TURNS", "OVERSEER_LASER_DPT_FRACTION", "OVERSEER_MELEE_DPT_FRACTION", "OVERSEER_RAM_COOLDOWN",
        "OVERSEER_SUMMON_COUNT", "OVERSEER_TOXIC_RADIUS", "PERKS_OFFERED", "PERK_ACCELERANT_BURN_STACKS_BY_RANK",
        "PERK_ACCELERANT_FIRE_DAMAGE_BY_RANK", "PERK_ARC_JUMP_CHAIN_JUMPS_BY_RANK",
        "PERK_ARC_JUMP_CHAIN_MULTIPLIER_BY_RANK", "PERK_BLAST_SHIELD_REDUCTION_BY_RANK",
        "PERK_BLOODLETTING_HEAL_BY_RANK", "PERK_BREACHER_REFUND_CHANCE_BY_RANK",
        "PERK_BUCKSHOT_SPREAD_DAMAGE_BY_RANK", "PERK_BUTCHER_MELEE_DAMAGE_BY_RANK",
        "PERK_CAPACITOR_ENERGY_DAMAGE_BY_RANK", "PERK_DRAW_BIAS_PER_PICK", "PERK_FELT_MIN_GAIN",
        "PERK_FIELD_MEDIC_HEAL_BY_RANK", "PERK_FIRST_BLOOD_DAMAGE_BY_RANK", "PERK_FOCUSED_MAX_GAIN",
        "PERK_HAGGLER_DISCOUNT_BY_RANK", "PERK_HARDY_MAX_HEALTH_BY_RANK", "PERK_LANE_DISCIPLINE_DAMAGE_BY_RANK",
        "PERK_LANE_DISCIPLINE_RANGE_BY_RANK", "PERK_MAX_RANK", "PERK_OVERCHARGE_PIERCE_BONUS_BY_RANK",
        "PERK_PACK_RAT_AMMO_BY_RANK", "PERK_PLATE_CARRIER_ARMOR_BY_RANK", "PERK_POINT_MAN_KNOCKBACK_BONUS_TILES",
        "PERK_POINT_MAN_POINT_BLANK_DAMAGE_BY_RANK", "PERK_POINT_MAN_STAGGER_REACH_TILES",
        "PERK_RIPOSTE_DRILL_DAMAGE_BY_RANK", "PERK_RIPOSTE_DRILL_TURNS_BY_RANK", "PERK_SALVAGER_EXTRA_DROP_BY_RANK",
        "PERK_SAPPER_BARREL_DAMAGE_BY_RANK", "PERK_SHAPED_CHARGES_DAMAGE_BY_RANK",
        "PERK_SHIELD_WALL_HEAVY_GUARD_MULTIPLIER", "PERK_STEADY_AIM_FALLOFF_REDUCTION_BY_RANK",
        "PERK_VITALITY_MAX_GAIN_PER_RANK", "PERK_WILDFIRE_BURN_TURNS_BY_RANK", "PLAGUE_HULK_ATTACK_DAMAGE",
        "PLAGUE_HULK_MAX_HEALTH", "PLAGUE_HULK_MINIMAL_TOXIC_RADIUS_TILES", "PLAGUE_HULK_MOVE_EVERY_N_TURNS",
        "PLAGUE_HULK_SELF_DESTRUCT_BLAST_RADIUS_TILES", "PLAGUE_HULK_SELF_DESTRUCT_BRACE_TURNS",
        "PLAGUE_HULK_SELF_DESTRUCT_HP_PERCENT", "PLAGUE_HULK_SELF_DESTRUCT_TOXIC_RADIUS_TILES",
        "PLASMA_RIFLE_CLIP_SIZE", "PLASMA_RIFLE_DAMAGE", "PLASMA_RIFLE_DAMAGE_DROP_COEFF",
        "PLASMA_RIFLE_RANGE_TILES", "PLASMA_RIFLE_RELOAD_TIME_TICKS", "PLAYER_MAX_ARMOR", "PLAYER_MAX_HEALTH",
        "PLAYER_MOVE_DURATION", "PLAYER_ROTATE_DURATION", "PLAYER_START_AGILITY", "PLAYER_START_MARKSMANSHIP",
        "PLAYER_START_STRENGTH", "PLAYER_START_TOUGHNESS", "PLAYER_VITALITY_GROWTH", "POINT_BLANK_BONUS_BASE",
        "POINT_BLANK_BONUS_CAP", "POINT_BLANK_BONUS_PER_LEVEL", "POINT_BLANK_MAX_DISTANCE", "POISON_MAX_STACKS",
        "POISON_STACKS_PER_ACID_HIT", "POISON_STACKS_PER_TOXIC_TURN", "POISON_TICK_FRACTION",
        "POSITIONAL_MULT_FAST_MELEE", "POSITIONAL_MULT_MELEE", "POSITIONAL_MULT_RANGED",
        "POSITIONAL_MULT_STATUS_BONUS", "RAILGUN_CLIP_SIZE", "RAILGUN_DAMAGE_BY_CHARGE",
        "RAILGUN_DAMAGE_MIN_MULTIPLIER", "RAILGUN_DROP_COEFF", "RAILGUN_MAX_SLUGS", "RAILGUN_PICKUP_SLUGS",
        "RAILGUN_RANGE_TILES", "RAILGUN_RELOAD_TIME_TICKS", "RANGED_KITE_MIN_TILES", "RARITY_DAMAGE_MULTIPLIER",
        "REFERENCE_PLAYER_DPT", "REFERENCE_PLAYER_EHP", "REGION_TP_BUDGET_MULTIPLIER",
        "REGION_TP_BUDGET_MULTIPLIER_MAX", "REGION_TP_BUDGET_MULTIPLIER_MIN", "REGION_TP_LETHAL_MARGIN",
        "RELIC_ASBESTOS_LINING_BURN_TURNS", "RELIC_ASBESTOS_LINING_FIRE_DAMAGE",
        "RELIC_ATROPHY_FIELD_ENEMY_HEALTH_MULTIPLIER", "RELIC_BANDOLIER_FLOOR_ARRIVAL_AMMO",
        "RELIC_BLAST_RING_SHARE", "RELIC_BLOOD_PACT_ALL_WEAPON_DAMAGE", "RELIC_BLOOD_PACT_DAMAGE_TAKEN_MULTIPLIER",
        "RELIC_BONE_SAW_MELEE_DAMAGE", "RELIC_BONE_SAW_MELEE_KILL_HEAL", "RELIC_BOSS_OFFER_CARDS",
        "RELIC_BOSS_OFFER_RARITY_WEIGHTS", "RELIC_BOUNTY_CHIP_KILL_CREDIT_BONUS",
        "RELIC_BREACHING_CHARGE_DOOR_BLAST", "RELIC_CAPACITOR_BANK_ARC_CHAIN_JUMP",
        "RELIC_CAPACITOR_BANK_ARC_CHAIN_MULTIPLIER", "RELIC_CARRY_BAND_COMMON", "RELIC_CARRY_BAND_CURSED",
        "RELIC_CARRY_BAND_LEGENDARY", "RELIC_CARRY_BAND_RARE", "RELIC_CHAIN_DETONATOR_CORPSE_BLAST",
        "RELIC_CHAIN_DETONATOR_DOOR_BLAST", "RELIC_CHOKE_BORE_KILL_ARMOR", "RELIC_CHOKE_BORE_KNOCKBACK_BONUS",
        "RELIC_CHOKE_BORE_SPREAD_DAMAGE", "RELIC_COUNTERWEIGHT_GUARD_REFLECT",
        "RELIC_CULLING_SIGNAL_ENEMY_HEALTH_MULTIPLIER", "RELIC_CURSED_MIN_LOSS", "RELIC_DAMAGE_TAKEN_MULTIPLIER_MIN",
        "RELIC_DEADMAN_TRIGGER_LOW_HEALTH_DAMAGE", "RELIC_DEATHS_DOOR_LOW_HEALTH_DAMAGE",
        "RELIC_DEATHS_DOOR_MAX_HEALTH_COST", "RELIC_DOG_TAGS_XP_MULTIPLIER", "RELIC_ENEMY_HEALTH_MULTIPLIER_MIN",
        "RELIC_FEVER_CORE_HEAL_COST", "RELIC_FEVER_CORE_KILL_HEAL", "RELIC_FIELD_DRESSING_FLOOR_ARRIVAL_HEAL",
        "RELIC_FOURTH_HOLSTER_EXTRA_LOADOUT_SLOT", "RELIC_FOURTH_HOLSTER_FLOOR_ARRIVAL_AMMO",
        "RELIC_GRIEF_ENGINE_CORPSE_BLAST", "RELIC_GRIEF_ENGINE_KILL_ARMOR", "RELIC_HUNGER_LOOP_KILL_ARMOR",
        "RELIC_HUNGER_LOOP_KILL_HEAL", "RELIC_IRON_LUNG_ALL_WEAPON_DAMAGE_COST",
        "RELIC_IRON_LUNG_DAMAGE_TAKEN_MULTIPLIER", "RELIC_KEVLAR_WEAVE_DAMAGE_TAKEN_MULTIPLIER", "RELIC_MAX_HELD",
        "RELIC_MEDICS_GLOVE_HEAL_BONUS", "RELIC_MYSTERY_OFFER_RARITY_WEIGHTS", "RELIC_NODE_OFFER_CARDS",
        "RELIC_OFFER_MAX_CARDS", "RELIC_OFFER_SEED_SALT", "RELIC_OVERCLOCKED_SERVOS_ALL_WEAPON_DAMAGE",
        "RELIC_OVERCLOCKED_SERVOS_RANGE_BONUS", "RELIC_PENDING_OFFER_CAPACITY", "RELIC_PERK_RANK_EQUIVALENT_LIFT",
        "RELIC_PERK_RANK_EQUIVALENT_TOLERANCE", "RELIC_RECOIL_SPRING_AMMO_PICKUP_BONUS",
        "RELIC_RECOIL_SPRING_KILL_AMMO_REFUND", "RELIC_REFERENCE_DEPTHS", "RELIC_SALVAGE_HOOK_CARRIER_EXTRA_DROP",
        "RELIC_SALVAGE_PLATING_KILL_ARMOR", "RELIC_SHAPED_CASINGS_EXPLOSIVE_DAMAGE",
        "RELIC_SHAPED_CASINGS_OWN_EXPLOSION_REDUCTION", "RELIC_SPARE_PLATES_FLOOR_ARRIVAL_ARMOR",
        "RELIC_SPOTTERS_LENS_DAMAGE_TAKEN_MULTIPLIER", "RELIC_SPOTTERS_LENS_FULL_HEALTH_TARGET_DAMAGE",
        "RELIC_START_OFFER_CARDS", "RELIC_SUPPLY_BAND_COMMON", "RELIC_SUPPLY_BAND_CURSED", "RELIC_SUPPLY_BAND_RARE",
        "RELIC_THRESHOLD_STIM_DOOR_OPEN_HEAL", "RELIC_TITHE_ENGINE_ENEMY_HEALTH_MULTIPLIER",
        "RELIC_TITHE_ENGINE_KILL_CREDIT_BONUS", "RELIC_TITHE_ENGINE_XP_MULTIPLIER",
        "RELIC_VALUATION_CORPSE_ADJACENCY", "RELIC_VALUATION_DOORS_PER_FLOOR", "RELIC_VALUATION_DOOR_FIGHT_SHARE",
        "RELIC_VALUATION_EVENT_TAKE_SHARE", "RELIC_VALUATION_KILL_SHARE", "RELIC_VALUATION_LOCKER_EVENT_WEIGHT",
        "RELIC_VALUATION_START_TAKE_SHARE", "RELIC_VAMPIRIC_CORE_KILL_HEAL", "RELIC_VOLATILE_RIG_CORPSE_BLAST",
        "RELIC_VOLATILE_RIG_DOOR_BLAST", "RELIC_VOLATILE_RIG_MAX_ARMOR_COST", "REND_DAMAGE_PER_TURN_BASE",
        "REND_DAMAGE_PER_TURN_CAP", "REND_DAMAGE_PER_TURN_PER_LEVEL", "REND_DURATION_TURNS",
        "RESERVE_BANKING_FLOORS_TARGET", "RESOURCE_RELIEF_WINDOW", "REST_ARMOUR", "REST_ARMOUR_VEST_DEPTH",
        "REST_HEAL_FRACTION", "REST_MEDKITS", "REST_STIMS", "REVENANT_ATTACK_DAMAGE",
        "REVENANT_LUNGE_COOLDOWN_TURNS", "REVENANT_LUNGE_TRIGGER_MAX_TILES", "REVENANT_LUNGE_TRIGGER_MIN_TILES",
        "REVENANT_MAX_HEALTH", "REVENANT_MOVE_EVERY_N_TURNS", "RHYTHM_MAX_STACKS", "RHYTHM_RAMP_PER_HIT_BASE",
        "RHYTHM_RAMP_PER_HIT_CAP", "RHYTHM_RAMP_PER_HIT_PER_LEVEL", "RIMESHELL_LANCER_ATTACK_DAMAGE",
        "RIMESHELL_LANCER_MAX_HEALTH", "RIMESHELL_LANCER_MOVE_EVERY_N_TURNS", "RIMESHELL_LANCER_RANGE_TILES",
        "RIMESHELL_LANCE_COOLDOWN_TURNS", "RIMESHELL_LANCE_FRIENDLY_FIRE_FRACTION", "RIMESHELL_SHELL_ARMOR",
        "RIMESHELL_SHELL_AVERAGED_FLAT_REDUCTION", "ROLE_GENERALIST_BOUND", "ROLE_GROUP_TARGETS_IN_LANE",
        "ROLE_INCINERATOR_S2_MAX_SPRAYS", "ROLE_MAX_SCENARIOS_BEST", "ROLE_NICHE_MARGIN",
        "ROLE_RISK_GENERALIST_TILES", "ROLE_RISK_PAYS_RATIO", "ROLE_RISK_SPREAD_MAX_TILES",
        "ROLE_RISK_SPREAD_MIN_TILES", "ROLE_S1_GROUP", "ROLE_S1_MAX_TILES", "ROLE_S1_MIN_TILES", "ROLE_S2_GROUP",
        "ROLE_S2_MAX_TILES", "ROLE_S2_MIN_TILES", "ROLE_S3_GROUP", "ROLE_S3_MAX_TILES", "ROLE_S3_MIN_TILES",
        "ROLE_S4_GROUP", "ROLE_S4_MAX_TILES", "ROLE_S4_MIN_TILES", "ROLE_S5_GROUP", "ROLE_S5_MAX_TILES",
        "ROLE_S5_MIN_TILES", "ROLE_S6_GROUP", "ROLE_S6_MAX_TILES", "ROLE_S6_MIN_TILES", "ROLE_S7_GROUP",
        "ROLE_S7_MAX_TILES", "ROLE_S7_MIN_TILES", "ROLE_S8_START_TILES", "ROLE_SCENARIO_DEPTHS",
        "ROLE_SCENARIO_TURN_CAP", "ROLE_SUPPLY_SEED_COUNT", "ROUND_G_RESERVE_MAX", "ROUND_G_RESERVE_MIN",
        "ROUND_J2_MAX_PERK_PICK_RATE", "ROUND_J2_MIN_PERK_OFFERS", "ROUND_MIN_RUNS", "ROUND_T2_LAG_HITS_RATIO_MIN",
        "ROUND_T3_EARLY_DEPTH_MAX", "ROUND_T3_MAX_TURNS_TO_FIRST_EXCHANGE", "ROUND_T3_MIN_ENEMIES",
        "ROUND_T3_MIN_GROUPS", "ROUND_T4_EXIT_HEALTH_MAX", "ROUND_T4_EXIT_HEALTH_MIN",
        "ROUND_T4_MAX_STARVED_DEATH_SHARE", "ROUND_T4_STARVED_AMMO_FRACTION", "ROUND_T6_MAX_CLASS_DAMAGE_SHARE",
        "ROUND_T6_MIN_SWITCHES_PER_HINTED_FLOOR", "ROUND_T7_MAX_KEYSTONE_SHARE", "ROUTE_BOSS_THREAT_SCALE",
        "ROUTE_CALM_BUDGET_SCALE", "ROUTE_CALM_EV_DISCOUNT_MAX", "ROUTE_CALM_EV_DISCOUNT_MIN",
        "ROUTE_CALM_GUARANTEE_LAYER", "ROUTE_HONEST_SAFE_MAX_THREAT_RATIO", "ROUTE_LIGHT_BUDGET_SCALE",
        "ROUTE_MALFUNCTION_HAZARD_HIT_POINTS", "ROUTE_MIN_NODE_TYPES_PER_LAYER", "ROUTE_MYSTERY_EV_PREMIUM_MAX",
        "ROUTE_MYSTERY_MIN_SPREAD_FRACTION", "ROUTE_MYSTERY_WORST_LOSS_FLOOR_MULTIPLIER",
        "ROUTE_PERMANENT_POWER_WEIGHT", "ROUTE_PIP_CALM_MAX_THREAT_RATIO", "ROUTE_PIP_STANDARD_MAX_THREAT_RATIO",
        "ROUTE_RISK_POWER_POINTS_PER_STANDARD_FLOOR", "ROUTE_RISK_PREMIUM_MAX", "ROUTE_RISK_PREMIUM_MIN",
        "ROUTE_TRAJECTORY_COUPLING_MAX", "ROUTE_TRAJECTORY_COUPLING_MIN", "ROUTE_TRAJECTORY_DRAIN_MAX",
        "ROUTE_TRAJECTORY_DRAIN_MIN", "ROUTE_TRAJECTORY_SCARCITY_MAX", "ROUTE_TRAJECTORY_SCARCITY_MIN",
        "ROUTE_TRAJECTORY_SEED_COUNT", "ROUTE_TRAJECTORY_XP_PACE_MAX", "ROUTE_TRAJECTORY_XP_PACE_MIN",
        "ROUTE_TRAP_GAUNTLET_HAZARD_HIT_POINTS", "ROUTE_UPGRADE_GUARANTEE_LAYER", "ROUTE_UPGRADE_OPPORTUNITY_CACHE",
        "ROUTE_UPGRADE_OPPORTUNITY_COMBAT", "ROUTE_UPGRADE_OPPORTUNITY_ELITE",
        "ROUTE_UPGRADE_OPPORTUNITY_PER_ON_LEVEL_WEAPON", "ROUTE_UPGRADE_OPPORTUNITY_POWER_POINTS",
        "ROUTE_UPGRADE_OPPORTUNITY_SHOP", "ROUTE_WEAPON_LEVEL_POWER_POINTS", "RUN_FINAL_DEPTH",
        "SCARCITY_PER_WEAPON_MAX", "SCARCITY_RATIO_FLOOR_MAX", "SCARCITY_RATIO_FLOOR_MIN",
        "SCARCITY_RATIO_FLOOR_TARGET", "SCAVENGER_CHANCE_BASE", "SCAVENGER_CHANCE_CAP", "SCAVENGER_CHANCE_PER_LEVEL",
        "SCAVENGER_REFUND_ROUNDS", "SHAPE_BIG_GROUP_SIZE", "SHAPE_GROUPED_MIN_FRACTION", "SHAPE_MAX_LONE_ENEMIES",
        "SHAPE_MIN_BIG_GROUPS", "SHELL_BRUTE_ATTACK_DAMAGE", "SHELL_BRUTE_CHARGE_TRIGGER_MAX_TILES",
        "SHELL_BRUTE_CHARGE_TRIGGER_MIN_TILES", "SHELL_BRUTE_MAX_HEALTH", "SHELL_BRUTE_MOVE_EVERY_N_TURNS",
        "SHOP_AMMO_DAMAGE_PER_POWER_POINT", "SHOP_AMMO_LARGE_BOX_MULTIPLIER", "SHOP_BIAS_WEIGHT_MULTIPLIER",
        "SHOP_CAT_WEIGHT_ABILITY", "SHOP_CAT_WEIGHT_AMMO", "SHOP_CAT_WEIGHT_MEDKIT", "SHOP_CAT_WEIGHT_TIER_UPGRADE",
        "SHOP_CAT_WEIGHT_WEAPON_LEVELUP", "SHOP_CREDITS_PER_POWER_POINT", "SHOP_DEPTH_PRICE_SCALE", "SHOP_ENTRY_MAX",
        "SHOP_ENTRY_MIN", "SHOP_HEAL_HP_PER_POWER_POINT", "SHOP_MACHINES_PER_FLOOR", "SHOP_MAX_PER_WINDOW",
        "SHOP_NODE_MACHINE_COUNT", "SHOP_NODE_PRICE_MULTIPLIER", "SHOP_PERK_RANK_POWER_POINTS",
        "SHOP_SIGNIFICANT_BUY_POWER_POINTS", "SHOP_SMALL_BUY_POWER_POINTS", "SHOP_WINDOW_LAYERS",
        "SHOTGUN_CLIP_SIZE", "SHOTGUN_DAMAGE", "SHOTGUN_FALLOFF_BY_TILE", "SHOTGUN_KNOCKBACK_MAX_TILES",
        "SHOTGUN_RANGE_TILES", "SHOTGUN_RELOAD_TIME_TICKS", "SHOTGUN_STAGGER_MAX_TILES",
        "SHOTGUN_STAGGER_MIN_TURNS_BETWEEN", "SIM_BOSS_CLEAR_RATE_MAX", "SIM_BOSS_CLEAR_RATE_MIN",
        "SIM_BOSS_KILL_HEALTH_MAX", "SIM_BOSS_KILL_HEALTH_MIN", "SIM_BOSS_MAX_FIGHT_TURNS_COV",
        "SIM_BOSS_MIN_SAMPLE", "SIM_BUILD_DEPTH_SPREAD", "SIM_BUILD_MAX_DOMINANT_TAG_SHARE", "SIM_BUILD_MIN_SAMPLE",
        "SIM_BUILD_SAMPLE_SEED_COUNT", "SIM_BUILD_SEED_COUNT", "SIM_DEPTH_CEILING",
        "SIM_ECONOMY_EMERGENCY_MAX_FRACTION", "SIM_ECONOMY_SUPPLY_SHARE_MAX", "SIM_ECONOMY_SUPPLY_SHARE_MIN",
        "SIM_ENGAGE_RANGE_TILES", "SIM_FAIR_READABLE_DEATH_MIN", "SIM_FIRST_BOSS_NAIVE_CLEAR_MIN",
        "SIM_FIRST_BOSS_NAIVE_REACH_MIN", "SIM_FIRST_BOSS_TACTICAL_CLEAR_MIN", "SIM_GATE_MAX_CLEAR_FRACTION",
        "SIM_GOAL_PATIENCE_TURNS", "SIM_HEAVY_MIN_SAMPLE", "SIM_HEAVY_NAIVE_MAX_SUCCESS",
        "SIM_HEAVY_REPORT_BAND_LAST_DEPTHS", "SIM_HEAVY_TACTICAL_MIN_SUCCESS", "SIM_LADDER_PROBE_SEEDS_PER_DEPTH",
        "SIM_LAG_MAX_MEDIAN_DEPTH", "SIM_LOOP_MAX_DISTINCT_TILES", "SIM_LOOP_PUSH_TURNS", "SIM_LOOP_WINDOW_TURNS",
        "SIM_MAX_STALLED_FLOOR_FRACTION", "SIM_MAX_STEPS_PER_ACTION", "SIM_NAIVE_DEPTH_MAX", "SIM_NAIVE_DEPTH_MIN",
        "SIM_NAIVE_HEAL_FRACTION", "SIM_NAIVE_WIN_RATE_MAX", "SIM_REFUSALS_BEFORE_TILE_WRITTEN_OFF",
        "SIM_RELIC_CURSED_ROW_TOLERANCE", "SIM_RELIC_MIN_RUNS_PER_RELIC", "SIM_RELIC_PER_RELIC_MAX_LIFT",
        "SIM_RELIC_PER_RELIC_TRAP_TOLERANCE", "SIM_RELIC_PROBE_SEED_COUNT", "SIM_RELIC_ROW_MAX_LIFT",
        "SIM_RELIC_ROW_TRAP_TOLERANCE", "SIM_RESOURCE_CRISIS_FRACTION", "SIM_ROUTE_TRAJECTORY_TOLERANCE",
        "SIM_SEED_COUNT", "SIM_SHOP_MAX_UNSPENT_FLOORS", "SIM_SHOP_MIN_OFFERS_PER_VISIT", "SIM_SKILL_DEPTH_GAP_MIN",
        "SIM_SOFTLOCK_MAX_FRACTION", "SIM_STARTING_DEPTH", "SIM_SUPPLY_DETOUR_RANGE_TILES",
        "SIM_SUPPLY_EXIT_HEALTH_MAX", "SIM_SUPPLY_EXIT_HEALTH_MIN", "SIM_SWITCH_MIN_HINTED_COMBAT_FLOORS",
        "SIM_SWITCH_MIN_PER_HINTED_COMBAT_FLOOR", "SIM_SWITCH_MIN_TAKE_RATE", "SIM_TACTICAL_ENGAGE_RANGE_TILES",
        "SIM_TACTICAL_HEAL_FRACTION", "SIM_TACTICAL_LOSS_DEPTH_MAX", "SIM_TACTICAL_LOSS_DEPTH_MIN",
        "SIM_TACTICAL_REACH_DEPTH", "SIM_TACTICAL_REACH_FRACTION_MIN", "SIM_TACTICAL_RETREAT_FRACTION",
        "SIM_TACTICAL_WIN_RATE_MAX", "SIM_TACTICAL_WIN_RATE_MIN", "SIM_TIME_STEP_SECONDS", "SIM_TURNS_PER_FLOOR_CAP",
        "SLOW_DURATION", "SLOW_FACTOR", "SOULFORGE_KILLS_PER_LEVEL_UP", "SPIRESOWER_LEY_RANGE_TILES",
        "SPIRESOWER_MAX_LIVE_SPIRES", "SPIRESOWER_REGEN_ARMOR_POOL", "SPIRESOWER_REGEN_PER_SPIRE",
        "SPIRESOWER_SOW_CADENCE_TURNS", "SPIRESOWER_SOW_RADIUS_TILES", "SPIRESOWER_SPIRES_PER_SOW",
        "SPIRESOWER_SPIRE_HIT_POINTS", "SPIRESOWER_SPIRE_LIFETIME_TURNS", "STAGGER_CHANCE_BASE",
        "STAGGER_CHANCE_CAP", "STAGGER_CHANCE_PER_LEVEL", "STAGGER_STUN_DURATION", "STATIC_SPLASH_BASE",
        "STATIC_SPLASH_CAP", "STATIC_SPLASH_PER_LEVEL", "STR_MELEE_PER_POINT", "STUCK_TURNS_BEFORE_WIGGLE",
        "STUN_DURATION_DEFAULT", "STUN_DURATION_HEAVY", "SUMMON_EXPECTED_PER_FIGHT", "SUPPLY_AUDIT_DEPTHS",
        "SUPPLY_AUDIT_SEED_COUNT", "SUPPLY_AVOIDANCE_FACTOR", "SUPPLY_CARRIED_SHARE", "SUPPLY_CARRIER_SHARE",
        "SUPPLY_CREDIT_CHIPS_PER_FLOOR", "SUPPLY_EXIT_PATH_BONUS", "SUPPLY_GROUP_ROOM_BONUS",
        "SUPPLY_HEAL_EARLY_SHARE", "SUPPLY_HEAL_FLOOR_FRACTION", "SUPPLY_MAX_ROOM_SHARE", "SUPPLY_OFF_TYPE_SHARE",
        "SUPPLY_TRACK_TOLERANCE", "SUPPLY_TURNS_ENGAGED_PER_ENEMY", "TELEGRAPH_MAX_UNTELEGRAPHED_HIT_FRACTION",
        "TGH_HP_PER_POINT", "TGH_REDUCTION_PER_POINT", "TIER_ABILITY_COUNT_COMMON", "TIER_ABILITY_COUNT_EPIC",
        "TIER_ABILITY_COUNT_LEGENDARY", "TIER_ABILITY_COUNT_RARE", "TIER_ABILITY_COUNT_UNCOMMON",
        "TIER_ABILITY_PP_BUDGET_COMMON", "TIER_ABILITY_PP_BUDGET_EPIC", "TIER_ABILITY_PP_BUDGET_LEGENDARY",
        "TIER_ABILITY_PP_BUDGET_RARE", "TIER_ABILITY_PP_BUDGET_UNCOMMON", "TIER_ABILITY_PP_TOLERANCE",
        "TIER_MAJOR_COUNT_COMMON", "TIER_MAJOR_COUNT_EPIC", "TIER_MAJOR_COUNT_LEGENDARY", "TIER_MAJOR_COUNT_RARE",
        "TIER_MAJOR_COUNT_UNCOMMON", "TWIST_BLACKOUT_WAKE_RADIUS_TILES", "TWIST_BLACKOUT_WAKE_SIGHT_TILES",
        "TWIST_HUNTED_ARRIVAL_TURNS", "TWIST_HUNTED_BOUNTY_AMMO_BOXES", "TWIST_HUNTED_MIN_SPAWN_DISTANCE_TILES",
        "TWIST_HUNTED_THREAT_SHARE", "TWIST_HUNTED_WARNING_TURNS", "TWIST_MIN_DEPTH_BLACKOUT",
        "TWIST_MIN_DEPTH_HUNTED", "TWIST_MIN_DEPTH_SALVAGE", "TWIST_NEUTRAL_EV_TOLERANCE",
        "TWIST_ROLL_CHANCE_COMBAT", "TWIST_SALVAGE_AMMO_BOXES", "TWIST_SALVAGE_ARMOUR_SHARDS",
        "TWIST_SALVAGE_BUDGET_MULT", "TWIST_SALVAGE_STIMS", "TWIST_SIM_MAX_DEATH_RATE_RATIO", "TWIST_SIM_MIN_FLOORS",
        "TWIST_WEIGHT_BLACKOUT", "TWIST_WEIGHT_HUNTED", "TWIST_WEIGHT_SALVAGE", "VERDANT_SPIRESOWER_ATTACK_DAMAGE",
        "VERDANT_SPIRESOWER_MAX_HEALTH", "VERDANT_SPIRESOWER_MOVE_EVERY_N_TURNS", "VOID_SHROUD_ATTACK_DAMAGE",
        "VOID_SHROUD_FLANK_DAMAGE_MULTIPLIER", "VOID_SHROUD_MAX_HEALTH", "VOID_SHROUD_MOVE_EVERY_N_TURNS",
        "VORTEX_EYE_ATTACK_DAMAGE", "VORTEX_EYE_MAX_HEALTH", "VORTEX_EYE_MOVE_EVERY_N_TURNS",
        "VORTEX_EYE_RANGE_TILES", "VULNERABLE_DAMAGE_PERCENT", "VULNERABLE_DURATION", "VULNERABLE_MAX_STACKS",
        "WEAK_DAMAGE_PERCENT", "WEAK_DURATION", "WEAPON_BURN_BASE_PER_TURN", "WEAPON_DROP_TIER_MAX_BY_REGION",
        "WEAPON_DROP_TIER_MIN_BY_REGION", "WEAPON_LEVEL_ROLL_OFFSETS", "WEAPON_LEVEL_ROLL_WEIGHTS",
        "XP_BASE_REQUIREMENT", "XP_CATCHUP_MULTIPLIER", "XP_CURVE_GROWTH_PER_LEVEL", "XP_FLOOR_YIELD_MAX",
        "XP_FLOOR_YIELD_MIN", "XP_PER_THREAT_POINT"
    };
}
