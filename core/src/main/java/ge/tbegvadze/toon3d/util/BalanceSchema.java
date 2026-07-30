package ge.tbegvadze.toon3d.util;

import ge.tbegvadze.toon3d.enemy.EnemyRole;
import ge.tbegvadze.toon3d.enemy.EnemyType;
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
import ge.tbegvadze.toon3d.progression.PlayerStats;
import ge.tbegvadze.toon3d.progression.UpgradeCard;
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
 * ({@link EnemyType}, {@link UpgradeCard}, {@link ItemType}, {@link AmmoType}) so content that
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
        /** R-WEAPON: every ranged weapon declares a ROLE; weaponPowerScore must land in the role band. */
        WEAPON_POWER,
        /** R-ENEMY (part 1): every archetype declares a ROLE; threatPoints must land in the role TP band. */
        ENEMY_THREAT_POINTS,
        /** R-ENEMY (part 2): golden ratio (TTD/TTK) in the role band. CHAFF is pack-exempt; MINI_ELITE is a deliberate spike. */
        ENEMY_GOLDEN_RATIO,
        /** R-CARD: every level-up card prices into LEVEL_UP_BUDGET_PP ± tolerance. */
        CARD_BUDGET,
        /** R-HEAL: every heal/armour pickup prices into its survival-turns-bought band. */
        HEAL_PRICING,
        /** R-TELEGRAPH: no attack > 25% reference eHP un-telegraphed; boss hard cap 35%. */
        TELEGRAPH,
        /** R-DEPTH: depth-coupling ratio in [MIN, MAX] for depths 1..15. */
        DEPTH_COUPLING,
        /** R-SCARCITY: model-floor S in [0.75, 0.95] floor-wide, < 0.60 per weapon; heal net-drain in band. */
        SCARCITY,
        /** R-DOT: exactly one definition per status; shim files must re-export BalanceConfig byte-for-byte. */
        DOT_UNIQUENESS,
        /** R-FLAGS: no live test/debug flags (any TEST/DEBUG boolean must be false). */
        FLAGS,
        /** COVERAGE: every content entry (weapon item, consumable, ammo type, enemy role) is classified/priced. */
        COVERAGE,
        /** R-GEARGATE (order 2): the starting loadout is fair in region 1 but reads underpowered by the gate depth. */
        GEAR_GATE,
        /** R-ABILITY (order 2): every ability has a priced PP value; every rollable tier fits its ability-PP budget. */
        ABILITY_BUDGET,
        /** R-SCARCITY-DEPTH (order 3): the scarcity ratio S holds [0.75, 0.95] at EVERY depth 1..15. */
        SCARCITY_DEPTH,
        /** R-HEALDRAIN-DEPTH (order 3): the per-floor net HP drain holds [5%, 15%] of eHP at EVERY depth 1..15. */
        HEALDRAIN_DEPTH,
        /** R-CREDITS (order 3): expected region income / price of the expected purchase bundle in [0.9, 1.4]. */
        CREDITS,
        /** R-XP-PACE (order 4): a floor's available XP / xpRequired(expectedLevel) holds [1.0, 1.3] at every depth 1..15. */
        XP_PACE,
        /** R-CARD-BREAKPOINT (order 4): every level-up card crosses >= 1 TTK/TTD breakpoint vs the region soldier. */
        CARD_BREAKPOINT,
        /** R-REGION (order 5): region TP dial is monotonic + bounded; C/D spend more than A; coupling holds per lane. */
        REGION_DANGER,
        /** R-BOSS-GATE (order 6): the DEPTH-1 starting loadout cannot win — turnsToKill >= 2x survivableTurns (with max heals). */
        BOSS_GATE,
        /** R-BOSS-FAIR (order 6): the EXPECTED loadout gets a real but winnable fight — length + survival ratio in band. */
        BOSS_FAIR,
        /** R-BOSS-VERB-CAP (order 6): every derived boss verb <= 35% eHP; any verb > 25% must be telegraphed. */
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
        /** S-GATE (order 9): 0% of HOARDER-START-WEAPON seeds clear the first boss. */
        SIM_GATE,
        /** S-FAIR (order 9): TACTICAL median death depth in band; deaths readable. */
        SIM_FAIR,
        /** S-SKILL (order 9): TACTICAL out-lives NAIVE by at least the skill gap. */
        SIM_SKILL,
        /** S-ROUTE (order 9): played per-floor drain matches the order-7 modelled trajectory band. */
        SIM_ROUTE,
        /** S-ECONOMY (order 9): experienced S tracks the modelled S; the emergency lifeline stays rare. */
        SIM_ECONOMY,
        /** S-SOFTLOCK (order 9): zero seeds end in a "cannot damage anything" state. */
        SIM_SOFTLOCK
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
        // The ONLY waiver shipping with order 1 (acceptance criterion): the Railgun's full-charge
        // power score (45.0) deliberately exceeds the heavy band (24-32). Nerfing the raw 90 would
        // make the weapon worthless rather than merely scarce — slug SCARCITY is the gate
        // (supply ~1.1 slugs/floor, tightest reserve banking ~1.0 floor).
        waive(RuleKind.WEAPON_POWER, "Railgun (full charge)",
                "Gated by slug scarcity, not raw damage: the 90-per-slug elite-buster hit is the "
                        + "heavy role's identity and supply (~1.1 slugs/floor, tightest reserve cap) "
                        + "is the real limiter.",
                "Re-checked by order-3 R-SCARCITY-DEPTH: slug reserve banking stays the TIGHTEST gate "
                        + "(~1.0 floor at depth 1, tightest of all ammo types) after the shop re-pricing.");
        waiveNavigationLimitedBands();
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
        waive(RuleKind.SIM_FAIR,    "TACTICAL median death depth", reason, expiry);
        waive(RuleKind.SIM_FAIR,    "TACTICAL readable deaths",    reason, expiry);
        waive(RuleKind.SIM_SKILL,   "TACTICAL vs NAIVE depth gap", reason, expiry);
        waive(RuleKind.SIM_ROUTE,   "played per-floor net drain",  reason, expiry);
        waive(RuleKind.SIM_ECONOMY, "experienced S vs modelled S", reason, expiry);
        waive(RuleKind.SIM_ECONOMY, "emergency lifeline floors",   reason, expiry);
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
    // R-WEAPON — the ranged-weapon registry (roles are designer DATA, bands from BalanceConfig).
    // =====================================================================================

    /** Weapon roles with their power-score bands (higher rarity never raises a band — it buys abilities). */
    public enum WeaponRole {
        SIDEARM   (BalanceConfig.WEAPON_POWER_SIDEARM_MIN,   BalanceConfig.WEAPON_POWER_SIDEARM_MAX),
        WORKHORSE (BalanceConfig.WEAPON_POWER_WORKHORSE_MIN, BalanceConfig.WEAPON_POWER_WORKHORSE_MAX),
        BURST     (BalanceConfig.WEAPON_POWER_BURST_MIN,     BalanceConfig.WEAPON_POWER_BURST_MAX),
        HEAVY     (BalanceConfig.WEAPON_POWER_HEAVY_MIN,     BalanceConfig.WEAPON_POWER_HEAVY_MAX);

        public final float bandMinimum;
        public final float bandMaximum;

        WeaponRole(float bandMinimum, float bandMaximum) {
            this.bandMinimum = bandMinimum;
            this.bandMaximum = bandMaximum;
        }
    }

    /** One registered ranged weapon: its role plus the stat block its power score is computed from. */
    public static final class RangedWeaponSpec {
        public final String     displayName;
        public final ItemType   itemType;
        public final WeaponRole role;
        public final int        clipSize;
        public final int        damagePerShot;
        public final int        reloadTicks;
        public final int        ammoPerShot;
        public final String     creditingNote;

        RangedWeaponSpec(String displayName, ItemType itemType, WeaponRole role,
                         int clipSize, int damagePerShot, int reloadTicks, int ammoPerShot,
                         String creditingNote) {
            this.displayName   = displayName;
            this.itemType      = itemType;
            this.role          = role;
            this.clipSize      = clipSize;
            this.damagePerShot = damagePerShot;
            this.reloadTicks   = reloadTicks;
            this.ammoPerShot   = ammoPerShot;
            this.creditingNote = creditingNote;
        }

        public float sustainedDamagePerTurn() {
            return GameMath.sustainedDamagePerTurn(clipSize, damagePerShot, reloadTicks);
        }

        public float ammoEfficiency() {
            return GameMath.ammoEfficiency(damagePerShot, ammoPerShot);
        }

        public float powerScore() {
            return GameMath.weaponPowerScore(sustainedDamagePerTurn(),
                    ammoEfficiency() / BalanceConfig.REFERENCE_AMMO_EFFICIENCY);
        }
    }

    private static final List<RangedWeaponSpec> RANGED_WEAPONS = buildRangedWeaponRegistry();

    private static List<RangedWeaponSpec> buildRangedWeaponRegistry() {
        List<RangedWeaponSpec> registry = new ArrayList<>();
        registry.add(new RangedWeaponSpec("Shotgun", ItemType.WEAPON_SHOTGUN, WeaponRole.BURST,
                BalanceConfig.SHOTGUN_CLIP_SIZE, BalanceConfig.SHOTGUN_DAMAGE,
                BalanceConfig.SHOTGUN_RELOAD_TIME_TICKS, 1, null));
        registry.add(new RangedWeaponSpec("Double-Barrel Shotgun", ItemType.WEAPON_DOUBLE_BARREL, WeaponRole.BURST,
                BalanceConfig.DBL_SHOTGUN_CLIP_SIZE, BalanceConfig.DBL_SHOTGUN_DAMAGE,
                BalanceConfig.DBL_SHOTGUN_RELOAD_TIME_TICKS, 1, null));
        registry.add(new RangedWeaponSpec("Plasma Rifle", ItemType.WEAPON_PLASMA, WeaponRole.BURST,
                BalanceConfig.PLASMA_RIFLE_CLIP_SIZE, BalanceConfig.PLASMA_RIFLE_DAMAGE,
                BalanceConfig.PLASMA_RIFLE_RELOAD_TIME_TICKS, 1, null));
        registry.add(new RangedWeaponSpec("Assault Rifle", ItemType.WEAPON_ASSAULT_RIFLE, WeaponRole.WORKHORSE,
                BalanceConfig.ASSAULT_RIFLE_CLIP_SIZE, BalanceConfig.ASSAULT_RIFLE_DAMAGE,
                BalanceConfig.ASSAULT_RIFLE_RELOAD_TIME_TICKS, 1, null));
        registry.add(new RangedWeaponSpec("Chaingun", ItemType.WEAPON_CHAINGUN, WeaponRole.WORKHORSE,
                BalanceConfig.CHAINGUN_CLIP_SIZE, BalanceConfig.CHAINGUN_DAMAGE,
                BalanceConfig.CHAINGUN_RELOAD_TIME_TICKS, 1, null));
        // Railgun scored at FULL charge (its intended engagement state). WAIVED over the heavy band.
        registry.add(new RangedWeaponSpec("Railgun (full charge)", ItemType.WEAPON_RAILGUN, WeaponRole.HEAVY,
                BalanceConfig.RAILGUN_CLIP_SIZE,
                BalanceConfig.RAILGUN_DAMAGE_BY_CHARGE[BalanceConfig.RAILGUN_DAMAGE_BY_CHARGE.length - 1],
                BalanceConfig.RAILGUN_RELOAD_TIME_TICKS, 1,
                "scored at full charge; power-band exception waived (slug scarcity is the gate)"));
        // Grenade Launcher scored on its centre splash damage (the lateral falloff ring is uncredited).
        registry.add(new RangedWeaponSpec("Grenade Launcher", ItemType.WEAPON_ROCKET, WeaponRole.HEAVY,
                BalanceConfig.GRENADE_CLIP_SIZE, BalanceConfig.GRENADE_SPLASH_DAMAGE,
                BalanceConfig.GRENADE_RELOAD_TIME_TICKS, 1,
                "credited on centre splash; neighbour falloff is uncredited bonus AoE"));
        // Arc Cannon credited on the single-target primary line (the lateral chain is uncredited).
        registry.add(new RangedWeaponSpec("Arc Cannon", ItemType.WEAPON_ARC_CANNON, WeaponRole.BURST,
                BalanceConfig.ARC_CANNON_CLIP_SIZE, BalanceConfig.ARC_CANNON_DAMAGE,
                BalanceConfig.ARC_CANNON_RELOAD_TIME_TICKS, 1,
                "credited on the primary bolt; the decaying lateral chain is uncredited bonus AoE"));
        // Incinerator credited per shot as impact + one full burn application (DoT is damage and
        // counts toward TTK — knowledge doc SECTION 15). Successive shots REFRESH rather than stack
        // the burn, so this is a muzzle-style over-credit, acknowledged like weapon falloff crediting.
        registry.add(new RangedWeaponSpec("Incinerator", ItemType.WEAPON_INCINERATOR, WeaponRole.HEAVY,
                BalanceConfig.FLAME_CLIP_SIZE,
                BalanceConfig.FLAME_IMPACT_DAMAGE
                        + BalanceConfig.FLAME_BURN_DAMAGE_PER_TURN * BalanceConfig.FLAME_BURN_TURNS,
                BalanceConfig.FLAME_RELOAD_TICKS, 1,
                "credited as impact + one full burn application per shot (DoT counts toward TTK)"));
        return Collections.unmodifiableList(registry);
    }

    /** The registered ranged-weapon specs — BalanceReport's WEAPONS table iterates exactly this list. */
    public static List<RangedWeaponSpec> rangedWeapons() {
        return RANGED_WEAPONS;
    }

    // Classification of every WEAPON-category ItemType for the coverage rule. A weapon item must be
    // RANGED (registered above), MELEE (no power-score rule — melee swings once per turn and is
    // priced through its flat damage), or AMMO_FALLBACK (legacy item with no weapon class behind it;
    // picking it up grants ammo — see PlayerController's WEAPON_PISTOL handling).
    public enum WeaponItemClassification { RANGED, MELEE, AMMO_FALLBACK }

    private static final Map<ItemType, WeaponItemClassification> WEAPON_ITEM_CLASSIFICATIONS =
            buildWeaponItemClassifications();

    private static Map<ItemType, WeaponItemClassification> buildWeaponItemClassifications() {
        Map<ItemType, WeaponItemClassification> classifications = new EnumMap<>(ItemType.class);
        for (RangedWeaponSpec spec : RANGED_WEAPONS) {
            classifications.put(spec.itemType, WeaponItemClassification.RANGED);
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
        Map<EnemyRole, float[]> bands = new EnumMap<>(EnemyRole.class);
        bands.put(EnemyRole.CHAFF,
                new float[]{BalanceConfig.ENEMY_TP_CHAFF_MIN,      BalanceConfig.ENEMY_TP_CHAFF_MAX});
        bands.put(EnemyRole.SOLDIER,
                new float[]{BalanceConfig.ENEMY_TP_SOLDIER_MIN,    BalanceConfig.ENEMY_TP_SOLDIER_MAX});
        bands.put(EnemyRole.BRUISER,
                new float[]{BalanceConfig.ENEMY_TP_BRUISER_MIN,    BalanceConfig.ENEMY_TP_BRUISER_MAX});
        bands.put(EnemyRole.MINI_ELITE,
                new float[]{BalanceConfig.ENEMY_TP_MINI_ELITE_MIN, BalanceConfig.ENEMY_TP_MINI_ELITE_MAX});
        // BOSS deliberately absent: bosses are governed by the SECTION 14 ruleset, not TP bands.
        return Collections.unmodifiableMap(bands);
    }

    /** The [min, max] TP band for a role, or null when the role has none registered (BOSS). */
    public static float[] threatPointBand(EnemyRole role) {
        float[] band = ENEMY_THREAT_POINT_BANDS.get(role);
        return band == null ? null : band.clone();
    }

    /** The [min, max] golden-ratio band for a role, or null when the role is exempt by contract. */
    public static float[] goldenRatioBand(EnemyRole role) {
        float[] band = ENEMY_GOLDEN_RATIO_BANDS.get(role);
        return band == null ? null : band.clone();
    }

    // Golden-ratio bands per role. CHAFF and MINI_ELITE are exempt BY CONTRACT: chaff is budgeted
    // by pack TP (a lone unit reads ~9), and a mini-elite is a deliberate spike the player avoids
    // or spends heavies on (reads under the duel band by design).
    private static final Map<EnemyRole, float[]> ENEMY_GOLDEN_RATIO_BANDS = buildEnemyGoldenRatioBands();

    private static Map<EnemyRole, float[]> buildEnemyGoldenRatioBands() {
        Map<EnemyRole, float[]> bands = new EnumMap<>(EnemyRole.class);
        bands.put(EnemyRole.SOLDIER,
                new float[]{BalanceConfig.GOLDEN_RATIO_TRASH_MIN,   BalanceConfig.GOLDEN_RATIO_TRASH_MAX});
        bands.put(EnemyRole.BRUISER,
                new float[]{BalanceConfig.GOLDEN_RATIO_BRUISER_MIN, BalanceConfig.GOLDEN_RATIO_BRUISER_MAX});
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
        /** Priced as per-cast equivalent DAMAGE folded through the cycle average (GameMath.specialEquivalent{AreaStrike,BuffSelf,Debuff*}). */
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
        registry.add(new TelegraphAttackSpec("Plague Hulk blast",  BalanceConfig.PLAGUE_HULK_SELF_DESTRUCT_BLAST_DAMAGE_MAX, "TELE", false));
        registry.add(new TelegraphAttackSpec("Void Shroud strike", BalanceConfig.VOID_SHROUD_ATTACK_DAMAGE, "NONE", false));
        registry.add(new TelegraphAttackSpec("Void Shroud flank",
                Math.round(BalanceConfig.VOID_SHROUD_ATTACK_DAMAGE * BalanceConfig.VOID_SHROUD_FLANK_DAMAGE_MULTIPLIER),
                "FACE", false));
        registry.add(new TelegraphAttackSpec("Shell Brute melee",  BalanceConfig.SHELL_BRUTE_ATTACK_DAMAGE, "NONE", false));
        registry.add(new TelegraphAttackSpec("Shell Brute CHARGE",
                Math.round(BalanceConfig.SHELL_BRUTE_ATTACK_DAMAGE * BalanceConfig.SHELL_BRUTE_CHARGE_DAMAGE_MULTIPLIER),
                "TELE", false));
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
        public final int     restoredValue;
        public final boolean isLargePickup;

        HealPickupSpec(String displayName, int restoredValue, boolean isLargePickup) {
            this.displayName   = displayName;
            this.restoredValue = restoredValue;
            this.isLargePickup = isLargePickup;
        }
    }

    private static final List<HealPickupSpec> HEAL_PICKUPS = buildHealPickupRegistry();

    private static List<HealPickupSpec> buildHealPickupRegistry() {
        List<HealPickupSpec> registry = new ArrayList<>();
        registry.add(new HealPickupSpec("Stim pack '+'",    BalanceConfig.MEDKIT_STIM_HEAL,   false));
        registry.add(new HealPickupSpec("Field medkit 'H'", BalanceConfig.MEDKIT_FULL_HEAL,   true));
        registry.add(new HealPickupSpec("Armour shard 'a'", BalanceConfig.ARMOUR_SHARD_VALUE, false));
        registry.add(new HealPickupSpec("Security vest 'A'", BalanceConfig.ARMOUR_VEST_VALUE, true));
        return Collections.unmodifiableList(registry);
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
        results.addAll(weaponPowerResults());
        results.addAll(enemyThreatPointResults());
        results.addAll(enemyGoldenRatioResults());
        results.addAll(cardBudgetResults());
        results.addAll(healPricingResults());
        results.addAll(telegraphResults());
        results.addAll(depthCouplingResults());
        results.addAll(scarcityResults());
        results.addAll(dotUniquenessResults());
        results.addAll(flagResults());
        results.addAll(coverageResults());
        results.addAll(gearGateResults());
        results.addAll(abilityBudgetResults());
        results.addAll(scarcityDepthResults());
        results.addAll(healDrainDepthResults());
        results.addAll(creditResults());
        results.addAll(xpPaceResults());
        results.addAll(cardBreakpointResults());
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
        return results;
    }

    /** R-WEAPON: powerScore in the declared role band for every registered ranged weapon. */
    public static List<RuleResult> weaponPowerResults() {
        List<RuleResult> results = new ArrayList<>();
        for (RangedWeaponSpec spec : RANGED_WEAPONS) {
            float powerScore = spec.powerScore();
            boolean inBand = powerScore >= spec.role.bandMinimum && powerScore <= spec.role.bandMaximum;
            results.add(new RuleResult(RuleKind.WEAPON_POWER, spec.displayName, powerScore,
                    spec.role.bandMinimum, spec.role.bandMaximum, inBand,
                    "role " + spec.role + (spec.creditingNote == null ? "" : "; " + spec.creditingNote)));
        }
        return results;
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
     * R-ENEMY: golden ratio (turns-to-die / turns-to-kill at the reference player) in the role band.
     * TTK uses the SUSTAINED reference DPT (contract decision); CHAFF and MINI_ELITE are exempt by
     * contract (pack budgeting / deliberate spike).
     */
    public static List<RuleResult> enemyGoldenRatioResults() {
        List<RuleResult> results = new ArrayList<>();
        for (EnemyType enemyType : EnemyType.values()) {
            float[] band = ENEMY_GOLDEN_RATIO_BANDS.get(enemyType.role());
            if (band == null) continue; // CHAFF/MINI_ELITE/BOSS: exempt by contract
            float goldenRatio = goldenRatioOf(enemyType);
            boolean inBand = goldenRatio >= band[0] && goldenRatio <= band[1];
            results.add(new RuleResult(RuleKind.ENEMY_GOLDEN_RATIO, enemyType.displayName(),
                    goldenRatio, band[0], band[1], inBand, "role " + enemyType.role()));
        }
        return results;
    }

    /** Golden ratio of one archetype at the reference player (shared by the audit and the report). */
    public static float goldenRatioOf(EnemyType enemyType) {
        // Order 5: enemy eHP runs through the shared survivability primitive (EnemyType.effectiveHitPoints
        // -> GameMath.enemyEffectiveHitPoints), not a hard-coded raw-HP shortcut, so the first mitigating
        // archetype re-prices its own golden ratio for free.
        float enemyEffectiveHitPoints = enemyType.effectiveHitPoints();
        int turnsToKill = GameMath.turnsToKill(enemyEffectiveHitPoints, BalanceConfig.REFERENCE_PLAYER_DPT);
        float enemyDamagePerTurn = (float) enemyType.attackDamage() / Math.max(1, enemyType.attackCadenceTurns());
        int turnsToDie = GameMath.turnsToKill(BalanceConfig.REFERENCE_PLAYER_EHP, enemyDamagePerTurn);
        return GameMath.goldenRatio(turnsToDie, turnsToKill);
    }

    /** R-CARD: every level-up card prices into the power-point budget band. */
    public static List<RuleResult> cardBudgetResults() {
        float bandMinimum = BalanceConfig.LEVEL_UP_BUDGET_PP * (1f - BalanceConfig.LEVEL_UP_BUDGET_TOLERANCE);
        float bandMaximum = BalanceConfig.LEVEL_UP_BUDGET_PP * (1f + BalanceConfig.LEVEL_UP_BUDGET_TOLERANCE);
        List<RuleResult> results = new ArrayList<>();
        for (UpgradeCard card : UpgradeCard.values()) {
            float powerPoints = card.estimatedPowerPoints();
            boolean inBand = powerPoints >= bandMinimum && powerPoints <= bandMaximum;
            results.add(new RuleResult(RuleKind.CARD_BUDGET, card.displayName, powerPoints,
                    bandMinimum, bandMaximum, inBand, "pool " + card.pool + ", lever " + card.lever));
        }
        return results;
    }

    /** R-HEAL: every heal/armour pickup buys a survival-turn count inside its size band. */
    public static List<RuleResult> healPricingResults() {
        float averageIncomingDamagePerTurn = modelFloorAverageIncomingDamagePerTurn();
        List<RuleResult> results = new ArrayList<>();
        for (HealPickupSpec pickup : HEAL_PICKUPS) {
            float survivalTurns = GameMath.survivalTurnsBought(pickup.restoredValue, averageIncomingDamagePerTurn);
            float bandMinimum = pickup.isLargePickup
                    ? BalanceConfig.HEAL_LARGE_SURVIVAL_TURNS_MIN : BalanceConfig.HEAL_SMALL_SURVIVAL_TURNS_MIN;
            float bandMaximum = pickup.isLargePickup
                    ? BalanceConfig.HEAL_LARGE_SURVIVAL_TURNS_MAX : BalanceConfig.HEAL_SMALL_SURVIVAL_TURNS_MAX;
            boolean inBand = survivalTurns >= bandMinimum && survivalTurns <= bandMaximum;
            results.add(new RuleResult(RuleKind.HEAL_PRICING, pickup.displayName, survivalTurns,
                    bandMinimum, bandMaximum, inBand,
                    (pickup.isLargePickup ? "large" : "small") + " pickup; survival turns at "
                            + String.format("%.1f", averageIncomingDamagePerTurn) + " avg incoming dmg/turn"));
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
     * The expected weapon ABILITY-PP budget per region (new-game-balancr order 4), derived from order 2's
     * data: the average of the tier ability-PP budgets over the region's dropped-weapon tier band
     * (WEAPON_DROP_TIER_MIN/MAX_BY_REGION). This is the honest ability-power input to playerPowerAtDepthV2;
     * a purely data-driven read of the priced catalogue, never a switch. Indexed 0-based by region.
     */
    private static final float[] REGION_ABILITY_BUDGET_POINTS = computeRegionAbilityBudgetPoints();

    private static float[] computeRegionAbilityBudgetPoints() {
        int regionCount = BalanceConfig.WEAPON_DROP_TIER_MIN_BY_REGION.length;
        WeaponTier[] tiers = WeaponTier.values();
        float[] budgets = new float[regionCount];
        for (int region = 0; region < regionCount; region++) {
            int minOrdinal = Math.max(0, Math.min(tiers.length - 1, BalanceConfig.WEAPON_DROP_TIER_MIN_BY_REGION[region]));
            int maxOrdinal = Math.max(0, Math.min(tiers.length - 1, BalanceConfig.WEAPON_DROP_TIER_MAX_BY_REGION[region]));
            float minBudget = WeaponRoller.tierAbilityPowerPointBudget(tiers[minOrdinal]);
            float maxBudget = WeaponRoller.tierAbilityPowerPointBudget(tiers[maxOrdinal]);
            budgets[region] = (minBudget + maxBudget) / 2f;
        }
        return budgets;
    }

    public static float[] regionAbilityBudgetPoints() {
        return REGION_ABILITY_BUDGET_POINTS;
    }

    /** The player's honest total-power multiplier at a depth (order 4 v2 model) — shared by the rule and the report. */
    public static float playerPowerV2(int depth) {
        return GameMath.playerPowerAtDepthV2(BalanceConfig.LEVEL_UP_BUDGET_PP,
                BalanceConfig.EXPECTED_LEVELS_PER_DEPTH, BalanceConfig.GEAR_CURVE_PER_REGION,
                regionAbilityBudgetPoints(), depth, BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE);
    }

    /**
     * R-DEPTH: the depth-coupling ratio holds its band across depths 1..15, measured against the HONEST
     * total-power model (new-game-balancr order 4) — cardPower * gearRamp * abilityPower, not the old
     * cards-only fiction. Depths 16+ are surfaced by BalanceReport (may degrade gracefully to the EASY
     * side); the rule enforces the tuned range 1..15.
     */
    public static List<RuleResult> depthCouplingResults() {
        List<RuleResult> results = new ArrayList<>();
        for (int depth = 1; depth <= 15; depth++) {
            float playerPower = playerPowerV2(depth);
            float enemyThreat = GameMath.depthThreatScale(BalanceConfig.ENEMY_HEALTH_SCALE_PER_DEPTH,
                    BalanceConfig.ENEMY_DAMAGE_SCALE_PER_DEPTH, depth);
            float ratio = GameMath.depthCouplingRatio(playerPower, enemyThreat);
            boolean inBand = ratio >= BalanceConfig.DEPTH_COUPLING_RATIO_MIN
                    && ratio <= BalanceConfig.DEPTH_COUPLING_RATIO_MAX;
            results.add(new RuleResult(RuleKind.DEPTH_COUPLING, "depth " + depth, ratio,
                    BalanceConfig.DEPTH_COUPLING_RATIO_MIN, BalanceConfig.DEPTH_COUPLING_RATIO_MAX,
                    inBand, "v2 honest power model"));
        }
        return results;
    }

    /**
     * R-REGION (new-game-balancr order 5): the REGION DANGER DIAL is a budgeted, provably-fair number, not
     * a vibe. It checks that (1) every region's TP multiplier sits in [MIN, MAX]; (2) the dial is monotonic
     * non-decreasing across regions (a deeper region is never easier); (3) the two lethal regions (C/D)
     * out-dial region A by a measurable margin — a lethal region is EXPLICITLY, budgeted-ly lethal; and
     * (4) the depth-coupling audit still holds for depths 1..15 in every region lane. Point (4) is the key
     * fairness proof: the region dial scales the BUDGET (how many bodies), not the per-enemy threat scale,
     * so the per-fight coupling ratio is region-INDEPENDENT and stays in band in every lane — region danger
     * is an attrition tax of extra bodies, not an unfair per-duel spike.
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

        // 2. Monotonic non-decreasing across regions (deeper is never easier).
        boolean monotonic = true;
        for (int region = 1; region < multipliers.length; region++) {
            if (multipliers[region] < multipliers[region - 1]) monotonic = false;
        }
        results.add(new RuleResult(RuleKind.REGION_DANGER, "region dial monotonic non-decreasing",
                monotonic ? 1f : 0f, 1f, 1f, monotonic, "A <= B <= C <= D — a deeper region never trivialises"));

        // 3. The lethal regions (C=2, D=3) out-dial region A by a measurable margin.
        float regionAMultiplier = multipliers.length > 0 ? multipliers[0] : 1f;
        int[] lethalRegions = {2, 3};
        for (int region : lethalRegions) {
            if (region >= multipliers.length) continue;
            float threshold = regionAMultiplier + BalanceConfig.REGION_TP_LETHAL_MARGIN;
            boolean measurablyHarder = multipliers[region] >= threshold;
            results.add(new RuleResult(RuleKind.REGION_DANGER,
                    "region " + region + " measurably harder than A", multipliers[region],
                    threshold, Float.POSITIVE_INFINITY, measurablyHarder,
                    regionDisplayName(region) + " dial " + multipliers[region] + " vs A " + regionAMultiplier));
        }

        // 4. Depth-coupling holds 1..15 in every region lane (region-independent by construction — the
        //    dial scales budget, not per-enemy threat). One result per lane confirms the whole sweep stays
        //    in band, so the depth-coupling audit provably "includes" the region dial as fair.
        for (int region = 0; region < multipliers.length; region++) {
            boolean laneHolds = true;
            for (int depth = 1; depth <= 15; depth++) {
                float ratio = GameMath.depthCouplingRatio(playerPowerV2(depth),
                        GameMath.depthThreatScale(BalanceConfig.ENEMY_HEALTH_SCALE_PER_DEPTH,
                                BalanceConfig.ENEMY_DAMAGE_SCALE_PER_DEPTH, depth));
                if (ratio < BalanceConfig.DEPTH_COUPLING_RATIO_MIN
                        || ratio > BalanceConfig.DEPTH_COUPLING_RATIO_MAX) laneHolds = false;
            }
            results.add(new RuleResult(RuleKind.REGION_DANGER,
                    "region " + region + " lane coupling 1..15", laneHolds ? 1f : 0f, 1f, 1f, laneHolds,
                    regionDisplayName(region) + " — per-fight coupling is region-independent and in band"));
        }
        return results;
    }

    /** The route region name for a 0-based region index (A/B/C/D, clamped past D — endless "The Breach"). */
    private static String regionDisplayName(int region) {
        switch (region) {
            case 0:  return RouteMapConstants.REGION_A_NAME;
            case 1:  return RouteMapConstants.REGION_B_NAME;
            case 2:  return RouteMapConstants.REGION_C_NAME;
            default: return RouteMapConstants.REGION_D_NAME;
        }
    }

    /** R-SCARCITY: floor-wide S in band, every per-weapon S under its cap, heal net-drain in band. */
    public static List<RuleResult> scarcityResults() {
        float demand = modelFloorDemand();
        float expectedBoxes = GameMath.expectedAmmoBoxesPerFloor(
                BalanceConfig.MODEL_FLOOR_ROOM_COUNT, BalanceConfig.LEVEL_GEN_AMMO_CHANCE_PER_ROOM,
                modelFloorEnemyCount(), BalanceConfig.ENEMY_AMMO_DROP_CHANCE);
        float boxesPerType = expectedBoxes / BalanceConfig.MODEL_FLOOR_AMMO_TYPE_COUNT;

        List<RuleResult> results = new ArrayList<>();
        float totalSupply = 0f;
        for (ScarcityRowSpec row : SCARCITY_ROWS) {
            float supply = GameMath.ammoSupplyDamage(boxesPerType, row.boxSize, row.damagePerUnit);
            totalSupply += supply;
            float perWeaponShare = GameMath.scarcityRatio(supply, demand);
            boolean underCap = perWeaponShare < BalanceConfig.SCARCITY_PER_WEAPON_MAX;
            results.add(new RuleResult(RuleKind.SCARCITY, "per-weapon S: " + row.ammoType.name(),
                    perWeaponShare, 0f, BalanceConfig.SCARCITY_PER_WEAPON_MAX, underCap, null));
        }
        float floorWideScarcityRatio = GameMath.scarcityRatio(totalSupply, demand);
        boolean floorWideInBand = floorWideScarcityRatio >= BalanceConfig.SCARCITY_RATIO_FLOOR_MIN
                && floorWideScarcityRatio <= BalanceConfig.SCARCITY_RATIO_FLOOR_MAX;
        results.add(new RuleResult(RuleKind.SCARCITY, "floor-wide S", floorWideScarcityRatio,
                BalanceConfig.SCARCITY_RATIO_FLOOR_MIN, BalanceConfig.SCARCITY_RATIO_FLOOR_MAX,
                floorWideInBand, "model floor DEMAND=" + Math.round(demand)));

        float incoming = GameMath.incomingDamagePerFloor(modelFloorEnemyDamagePerTurn(),
                BalanceConfig.MODEL_FLOOR_TURNS_ENGAGED_PER_ENEMY, BalanceConfig.MODEL_FLOOR_AVOIDANCE_FACTOR);
        float averageMedkitHeal = (BalanceConfig.MEDKIT_STIM_HEAL + BalanceConfig.MEDKIT_FULL_HEAL) / 2f;
        float averageArmourValue = (BalanceConfig.ARMOUR_SHARD_VALUE + BalanceConfig.ARMOUR_VEST_VALUE) / 2f;
        float healSupply = GameMath.healSupplyPerFloor(
                BalanceConfig.MODEL_FLOOR_EXPECTED_MEDKITS, averageMedkitHeal,
                BalanceConfig.MODEL_FLOOR_EXPECTED_ARMOUR_PICKUPS, averageArmourValue);
        float netDrainFraction = GameMath.netHpDrainPerFloor(incoming, healSupply)
                / BalanceConfig.REFERENCE_PLAYER_EHP;
        boolean drainInBand = netDrainFraction >= BalanceConfig.HEAL_NET_DRAIN_FRACTION_MIN
                && netDrainFraction <= BalanceConfig.HEAL_NET_DRAIN_FRACTION_MAX;
        results.add(new RuleResult(RuleKind.SCARCITY, "heal net-drain fraction", netDrainFraction,
                BalanceConfig.HEAL_NET_DRAIN_FRACTION_MIN, BalanceConfig.HEAL_NET_DRAIN_FRACTION_MAX,
                drainInBand, "fraction of reference eHP lost per floor"));
        return results;
    }

    // =====================================================================================
    // ORDER-3 depth-sweep helpers — shared "one source" numbers for R-SCARCITY-DEPTH,
    // R-HEALDRAIN-DEPTH and R-CREDITS (the same model-floor primitives the SECTION-10 table uses).
    // =====================================================================================

    /** Expected ammo boxes handed out on the model floor (LEVER 1 sources: rooms + kills). */
    private static float modelFloorExpectedBoxes() {
        return GameMath.expectedAmmoBoxesPerFloor(
                BalanceConfig.MODEL_FLOOR_ROOM_COUNT, BalanceConfig.LEVEL_GEN_AMMO_CHANCE_PER_ROOM,
                modelFloorEnemyCount(), BalanceConfig.ENEMY_AMMO_DROP_CHANCE);
    }

    /** Total ranged-ammo SUPPLY damage on the depth-1 model floor (the depth-sweep's baseline). */
    public static float modelFloorTotalRangedSupply() {
        float boxesPerType = modelFloorExpectedBoxes() / BalanceConfig.MODEL_FLOOR_AMMO_TYPE_COUNT;
        float totalSupply = 0f;
        for (ScarcityRowSpec row : SCARCITY_ROWS) {
            totalSupply += GameMath.ammoSupplyDamage(boxesPerType, row.boxSize, row.damagePerUnit);
        }
        return totalSupply;
    }

    /** Worst single-weapon SUPPLY damage on the model floor (the tightest per-weapon share driver). */
    private static float modelFloorWorstWeaponSupply() {
        float boxesPerType = modelFloorExpectedBoxes() / BalanceConfig.MODEL_FLOOR_AMMO_TYPE_COUNT;
        float worst = 0f;
        for (ScarcityRowSpec row : SCARCITY_ROWS) {
            worst = Math.max(worst, GameMath.ammoSupplyDamage(boxesPerType, row.boxSize, row.damagePerUnit));
        }
        return worst;
    }

    /**
     * R-SCARCITY-DEPTH (order 3): the scarcity ratio S holds [0.75, 0.95] at EVERY depth 1..15, and no
     * single weapon's share exceeds the per-weapon cap at any depth. DEMAND rides the enemy-eHP curve;
     * SUPPLY rides the EXPECTED-arsenal gear curve times the per-region supply multiplier. This is the
     * whole-run generalisation of the depth-1 R-SCARCITY model-floor check.
     */
    /**
     * The scarcity ratio S the order-3 model predicts for a depth — the single arithmetic the
     * depth-scarcity rule and the order-9 simulator both compare against, so a played run and a
     * modelled floor are measured with the same yardstick.
     */
    public static float modelledScarcityAtDepth(int depth) {
        int band = BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE;
        float demand = GameMath.floorDemandAtDepth(modelFloorDemand(),
                BalanceConfig.ENEMY_HEALTH_SCALE_PER_DEPTH, depth);
        float supply = GameMath.ammoSupplyAtDepth(modelFloorTotalRangedSupply(),
                BalanceConfig.GEAR_CURVE_PER_REGION,
                BalanceConfig.AMMO_SUPPLY_REGION_MULTIPLIER, depth, band);
        return GameMath.scarcityRatioAtDepth(supply, demand);
    }

    public static List<RuleResult> scarcityDepthResults() {
        List<RuleResult> results = new ArrayList<>();
        float modelDemand = modelFloorDemand();
        float modelSupply = modelFloorTotalRangedSupply();
        float worstWeaponSupply = modelFloorWorstWeaponSupply();
        int band = BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE;
        for (int depth = 1; depth <= 15; depth++) {
            float demand = GameMath.floorDemandAtDepth(modelDemand,
                    BalanceConfig.ENEMY_HEALTH_SCALE_PER_DEPTH, depth);
            float supply = GameMath.ammoSupplyAtDepth(modelSupply, BalanceConfig.GEAR_CURVE_PER_REGION,
                    BalanceConfig.AMMO_SUPPLY_REGION_MULTIPLIER, depth, band);
            float scarcity = GameMath.scarcityRatioAtDepth(supply, demand);
            boolean inBand = scarcity >= BalanceConfig.SCARCITY_RATIO_FLOOR_MIN
                    && scarcity <= BalanceConfig.SCARCITY_RATIO_FLOOR_MAX;
            // Worst per-weapon share at this depth (SUPPLY of the single biggest weapon over DEMAND).
            float worstSupply = GameMath.ammoSupplyAtDepth(worstWeaponSupply,
                    BalanceConfig.GEAR_CURVE_PER_REGION, BalanceConfig.AMMO_SUPPLY_REGION_MULTIPLIER, depth, band);
            float worstShare = GameMath.scarcityRatioAtDepth(worstSupply, demand);
            results.add(new RuleResult(RuleKind.SCARCITY_DEPTH, "S at depth " + depth, scarcity,
                    BalanceConfig.SCARCITY_RATIO_FLOOR_MIN, BalanceConfig.SCARCITY_RATIO_FLOOR_MAX, inBand,
                    "region " + GameMath.regionIndexAtDepth(depth, band)
                            + "; worst per-weapon share " + String.format("%.2f", worstShare)));
        }
        return results;
    }

    /**
     * R-HEALDRAIN-DEPTH (order 3): the per-floor net HP drain holds [5%, 15%] of the player's
     * current-difficulty eHP at EVERY depth 1..15. Incoming damage, heal supply and eHP all ride the
     * enemy-damage curve, so the drain FRACTION is depth-stable at the region multipliers' 1.0 default.
     */
    public static List<RuleResult> healDrainDepthResults() {
        List<RuleResult> results = new ArrayList<>();
        float incomingBase = GameMath.incomingDamagePerFloor(modelFloorEnemyDamagePerTurn(),
                BalanceConfig.MODEL_FLOOR_TURNS_ENGAGED_PER_ENEMY, BalanceConfig.MODEL_FLOOR_AVOIDANCE_FACTOR);
        float averageMedkitHeal  = (BalanceConfig.MEDKIT_STIM_HEAL + BalanceConfig.MEDKIT_FULL_HEAL) / 2f;
        float averageArmourValue = (BalanceConfig.ARMOUR_SHARD_VALUE + BalanceConfig.ARMOUR_VEST_VALUE) / 2f;
        float healBase = GameMath.healSupplyPerFloor(
                BalanceConfig.MODEL_FLOOR_EXPECTED_MEDKITS, averageMedkitHeal,
                BalanceConfig.MODEL_FLOOR_EXPECTED_ARMOUR_PICKUPS, averageArmourValue);
        int band = BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE;
        for (int depth = 1; depth <= 15; depth++) {
            float healRegionMultiplier = GameMath.perRegionMultiplierAtDepth(
                    BalanceConfig.HEAL_SUPPLY_REGION_MULTIPLIER, depth, band);
            float drainFraction = GameMath.netHpDrainFractionAtDepth(incomingBase, healBase,
                    healRegionMultiplier, BalanceConfig.REFERENCE_PLAYER_EHP);
            boolean inBand = drainFraction >= BalanceConfig.HEAL_NET_DRAIN_FRACTION_MIN
                    && drainFraction <= BalanceConfig.HEAL_NET_DRAIN_FRACTION_MAX;
            results.add(new RuleResult(RuleKind.HEALDRAIN_DEPTH, "net drain at depth " + depth, drainFraction,
                    BalanceConfig.HEAL_NET_DRAIN_FRACTION_MIN, BalanceConfig.HEAL_NET_DRAIN_FRACTION_MAX,
                    inBand, "region " + GameMath.regionIndexAtDepth(depth, band)
                            + " heal mult " + String.format("%.2f", healRegionMultiplier)));
        }
        return results;
    }

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
        float expectedChips = (BalanceConfig.CREDIT_CHIPS_PER_FLOOR_MIN
                + BalanceConfig.CREDIT_CHIPS_PER_FLOOR_MAX) / 2f;
        float weightSmall  = BalanceConfig.CREDIT_SPAWN_WEIGHT_SMALL;
        float weightMedium = BalanceConfig.CREDIT_SPAWN_WEIGHT_MEDIUM;
        float weightLarge  = BalanceConfig.CREDIT_SPAWN_WEIGHT_LARGE;
        float weightTotal  = weightSmall + weightMedium + weightLarge;
        float averageChipValue = (weightSmall * ItemConstants.CREDIT_SMALL_BASE
                + weightMedium * ItemConstants.CREDIT_MEDIUM_BASE
                + weightLarge * ItemConstants.CREDIT_LARGE_BASE) / weightTotal;
        return expectedChips * averageChipValue;
    }

    /** The credit price of the expected per-region purchase BUNDLE (one weapon-class buy + supplies). */
    public static int regionPurchaseBundlePrice(int representativeDepth) {
        int significant = GameMath.shopPrice(BalanceConfig.SHOP_SIGNIFICANT_BUY_POWER_POINTS,
                BalanceConfig.SHOP_CREDITS_PER_POWER_POINT, representativeDepth,
                BalanceConfig.SHOP_DEPTH_PRICE_SCALE) * BalanceConfig.SHOP_EXPECTED_SIGNIFICANT_BUYS_PER_REGION;
        int small = GameMath.shopPrice(BalanceConfig.SHOP_SMALL_BUY_POWER_POINTS,
                BalanceConfig.SHOP_CREDITS_PER_POWER_POINT, representativeDepth,
                BalanceConfig.SHOP_DEPTH_PRICE_SCALE);
        return Math.round(significant + BalanceConfig.SHOP_EXPECTED_SMALL_BUYS_PER_REGION * small);
    }

    /**
     * R-CREDITS (order 3): the credit sink is a real lever — expected income per region divided by the
     * price of the expected purchase bundle (one weapon-class buy + a couple of supplies) lands in
     * [0.9, 1.4] for every region across depths 1..15. Currency is thus neither a runaway score counter
     * (income &gt;&gt; sink) nor unaffordable (income &lt;&lt; sink).
     */
    public static List<RuleResult> creditResults() {
        List<RuleResult> results = new ArrayList<>();
        int band = BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE;
        float killCreditBase = modelFloorKillCreditReward();
        float chipIncome = chipIncomePerFloor();
        int regionCount = (15 + band - 1) / band; // regions spanning depths 1..15
        for (int region = 0; region < regionCount; region++) {
            int firstDepth = region * band + 1;
            int representativeDepth = firstDepth + (band - 1) / 2; // region's middle floor
            float income = GameMath.creditIncomePerRegion(killCreditBase,
                    BalanceConfig.CREDIT_DEPTH_SCALE, chipIncome, firstDepth, band);
            int bundlePrice = regionPurchaseBundlePrice(representativeDepth);
            float ratio = bundlePrice <= 0 ? 0f : income / bundlePrice;
            boolean inBand = ratio >= BalanceConfig.CREDIT_INCOME_RATIO_MIN
                    && ratio <= BalanceConfig.CREDIT_INCOME_RATIO_MAX;
            results.add(new RuleResult(RuleKind.CREDITS, "region " + region + " income/bundle", ratio,
                    BalanceConfig.CREDIT_INCOME_RATIO_MIN, BalanceConfig.CREDIT_INCOME_RATIO_MAX, inBand,
                    "income " + Math.round(income) + " / bundle " + bundlePrice
                            + " (rep depth " + representativeDepth + ")"));
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
                BalanceConfig.ENEMY_HEALTH_SCALE_PER_DEPTH, BalanceConfig.ENEMY_DAMAGE_SCALE_PER_DEPTH, depth);
        return BalanceConfig.XP_PER_THREAT_POINT
                * BalanceConfig.ENCOUNTER_BUDGET_FILL_TARGET_FRACTION * floorBudget;
    }

    /** R-XP-PACE: available XP / xpRequired(expectedLevel) in [MIN, MAX] for every depth 1..15. */
    public static List<RuleResult> xpPaceResults() {
        List<RuleResult> results = new ArrayList<>();
        for (int depth = 1; depth <= 15; depth++) {
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
    // R-CARD-BREAKPOINT (new-game-balancr order 4) — a level-up must FEEL different in the next fight,
    // not just on a character sheet. Every card must cross at least one INTEGER combat breakpoint vs the
    // region soldier in some region band: an OFFENCE card shaves a whole turn off the kill (turnsToKill-
    // BreakpointGain), a DEFENCE card survives a whole extra enemy hit (turnsToDie gain). Cards keep their
    // equal PP budget (R-CARD); this rule proves the budget is spent where it changes the fight.
    // =====================================================================================

    /** Max integer breakpoint a card crosses at a depth: offence turns-shaved OR defence turns-survived, vs the region soldier. */
    public static int cardBreakpointGainAtDepth(UpgradeCard card, int depth) {
        EnemyType soldier = GEAR_GATE_REFERENCE_SOLDIER;
        float soldierEffectiveHitPoints = soldier.effectiveHitPoints()
                * GameMath.compoundDepthMultiplier(BalanceConfig.ENEMY_HEALTH_SCALE_PER_DEPTH, depth);
        float soldierDamagePerTurn = ((float) soldier.attackDamage() / Math.max(1, soldier.attackCadenceTurns()))
                * GameMath.compoundDepthMultiplier(BalanceConfig.ENEMY_DAMAGE_SCALE_PER_DEPTH, depth);
        float expectedPlayerDamagePerTurn = GameMath.expectedPlayerDamagePerTurn(BalanceConfig.REFERENCE_PLAYER_DPT,
                BalanceConfig.GEAR_CURVE_PER_REGION, depth, BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE);
        // OFFENCE: whole turns shaved off killing the soldier when the card's DPT is added.
        int offenceBreakpoint = GameMath.turnsToKillBreakpointGain(soldierEffectiveHitPoints,
                expectedPlayerDamagePerTurn, expectedPlayerDamagePerTurn + card.damagePerTurnGain());
        // DEFENCE: whole extra turns the player survives the soldier when the card's eHP is added.
        int turnsToDieBefore = GameMath.turnsToKill(BalanceConfig.REFERENCE_PLAYER_EHP, soldierDamagePerTurn);
        int turnsToDieAfter  = GameMath.turnsToKill(BalanceConfig.REFERENCE_PLAYER_EHP + card.effectiveHitPointGain(),
                soldierDamagePerTurn);
        int defenceBreakpoint = turnsToDieAfter - turnsToDieBefore;
        return Math.max(offenceBreakpoint, defenceBreakpoint);
    }

    /** The deepest breakpoint a card crosses anywhere in depths 1..15 (shared by the rule and the report). */
    public static int cardBestBreakpoint(UpgradeCard card) {
        int best = 0;
        for (int depth = 1; depth <= 15; depth++) {
            best = Math.max(best, cardBreakpointGainAtDepth(card, depth));
        }
        return best;
    }

    /** R-CARD-BREAKPOINT: every card crosses >= 1 combat breakpoint vs the region soldier in some region. */
    public static List<RuleResult> cardBreakpointResults() {
        List<RuleResult> results = new ArrayList<>();
        for (UpgradeCard card : UpgradeCard.values()) {
            int best = cardBestBreakpoint(card);
            boolean crosses = best >= 1;
            results.add(new RuleResult(RuleKind.CARD_BREAKPOINT, card.displayName, best, 1f, Float.POSITIVE_INFINITY,
                    crosses, crosses ? "crosses a breakpoint in some region"
                            : "NEVER crosses a breakpoint — re-price so a level-up changes the next fight"));
        }
        return results;
    }

    /**
     * R-DOT: exactly one definition per status. The weapon-ability BURN base must BE the unified
     * base, and every same-named field in a shim class must re-export BalanceConfig byte-for-byte
     * (the generalised structural rule; a re-introduced divergent literal fails here).
     */
    public static List<RuleResult> dotUniquenessResults() {
        List<RuleResult> results = new ArrayList<>();
        boolean unifiedBurn = BalanceConfig.INCENDIARY_BURN_PER_TURN_BASE == BalanceConfig.BURN_DAMAGE_PER_TURN;
        results.add(new RuleResult(RuleKind.DOT_UNIQUENESS, "BURN base unified",
                BalanceConfig.INCENDIARY_BURN_PER_TURN_BASE,
                BalanceConfig.BURN_DAMAGE_PER_TURN, BalanceConfig.BURN_DAMAGE_PER_TURN, unifiedBurn,
                "INCENDIARY_BURN_PER_TURN_BASE must reference the single BURN_DAMAGE_PER_TURN base"));

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
        if (results.size() == 1) {
            results.add(new RuleResult(RuleKind.DOT_UNIQUENESS, "shim integrity",
                    0f, 0f, 0f, true, "every same-named shim field re-exports BalanceConfig"));
        }
        return results;
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
    // R-GEARGATE (new-game-balancr order 2) — the starting loadout is FAIR at the start but reads
    // UNDERPOWERED once the gear curve has stepped, while an ON-CURVE player stays fair. Proven on a
    // reference SOLDIER at three points, all through the same golden-ratio lens (TTD / TTK):
    //   1. START FAIR   — stagnant (reference-DPT) golden ratio at depth 1 is IN the soldier band.
    //   2. GATE FIRES   — the SAME stagnant player's golden ratio at the region-2 entry depth is BELOW
    //                     the band (the start weapon now reads underpowered — stagnation is punished).
    //   3. ON-CURVE FAIR— the EXPECTED player (reference DPT * gearCurve) golden ratio at that same
    //                     depth is back IN band (upgrading keeps you fair — "find better gear or die").
    // Region-1 fairness for the WHOLE roster is already guaranteed by R-ENEMY (same reference-DPT lens
    // at depth 1); this rule adds the depth axis the gear gate introduces.
    // =====================================================================================

    /** The reference "region-2 entry soldier" the gate is proven on (a standard, common SOLDIER). */
    private static final EnemyType GEAR_GATE_REFERENCE_SOLDIER = EnemyType.VOID_SHROUD;

    /** The reference soldier the gear gate is proven on (BalanceReport's GEAR CURVE table). */
    public static EnemyType gearGateReferenceSoldier() { return GEAR_GATE_REFERENCE_SOLDIER; }

    /** Display name of the gear-gate reference soldier. */
    public static String gearGateReferenceSoldierName() { return GEAR_GATE_REFERENCE_SOLDIER.displayName(); }

    /** Golden ratio of an archetype at a given depth against a player of the given sustained DPT. */
    private static float gearGateGoldenRatio(EnemyType enemyType, int depth, float playerDamagePerTurn) {
        float enemyEffectiveHitPoints = enemyType.effectiveHitPoints()
                * GameMath.compoundDepthMultiplier(BalanceConfig.ENEMY_HEALTH_SCALE_PER_DEPTH, depth);
        int turnsToKill = GameMath.turnsToKill(enemyEffectiveHitPoints, playerDamagePerTurn);
        float enemyDamagePerTurn = ((float) enemyType.attackDamage() / Math.max(1, enemyType.attackCadenceTurns()))
                * GameMath.compoundDepthMultiplier(BalanceConfig.ENEMY_DAMAGE_SCALE_PER_DEPTH, depth);
        int turnsToDie = GameMath.turnsToKill(BalanceConfig.REFERENCE_PLAYER_EHP, enemyDamagePerTurn);
        return GameMath.goldenRatio(turnsToDie, turnsToKill);
    }

    /** R-GEARGATE: the start is fair, the gate fires, and the on-curve player stays fair. */
    public static List<RuleResult> gearGateResults() {
        List<RuleResult> results = new ArrayList<>();
        EnemyType soldier   = GEAR_GATE_REFERENCE_SOLDIER;
        int region2Depth    = BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE + 1; // first floor of region 2
        float bandMin       = BalanceConfig.GOLDEN_RATIO_TRASH_MIN;
        float bandMax       = BalanceConfig.GOLDEN_RATIO_TRASH_MAX;
        float stagnantDpt   = BalanceConfig.REFERENCE_PLAYER_DPT; // never upgrades — keeps the depth-1 anchor
        float expectedDpt   = GameMath.expectedPlayerDamagePerTurn(BalanceConfig.REFERENCE_PLAYER_DPT,
                BalanceConfig.GEAR_CURVE_PER_REGION, region2Depth, BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE);

        // 1. START FAIR — stagnant golden ratio at depth 1 is inside the soldier band.
        float startGolden = gearGateGoldenRatio(soldier, 1, stagnantDpt);
        results.add(new RuleResult(RuleKind.GEAR_GATE, soldier.displayName() + " region-1 start fair",
                startGolden, bandMin, bandMax, startGolden >= bandMin && startGolden <= bandMax,
                "starting loadout (reference DPT " + BalanceConfig.REFERENCE_PLAYER_DPT + ") vs depth-1 soldier"));

        // 2. GATE FIRES — the same stagnant player is now UNDER the band at the region-2 entry depth.
        float stagnantGateGolden = gearGateGoldenRatio(soldier, region2Depth, stagnantDpt);
        results.add(new RuleResult(RuleKind.GEAR_GATE, soldier.displayName() + " region-2 gate fires",
                stagnantGateGolden, 0f, bandMin, stagnantGateGolden < bandMin,
                "stagnant starting loadout vs region-2 (depth " + region2Depth + ") soldier must read UNDER the band"));

        // 3. ON-CURVE FAIR — the expected (geared) player is back in band at the same depth.
        float expectedGateGolden = gearGateGoldenRatio(soldier, region2Depth, expectedDpt);
        results.add(new RuleResult(RuleKind.GEAR_GATE, soldier.displayName() + " region-2 on-curve fair",
                expectedGateGolden, bandMin, bandMax,
                expectedGateGolden >= bandMin && expectedGateGolden <= bandMax,
                "expected DPT " + String.format("%.1f", expectedDpt) + " (reference * gearCurve) vs the same soldier"));

        // Anchor check: gearCurve is 1.0 across all of region 1, so expected == reference at depth 1..5.
        boolean anchorHeld = true;
        for (int depth = 1; depth <= BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE; depth++) {
            float expected = GameMath.expectedPlayerDamagePerTurn(BalanceConfig.REFERENCE_PLAYER_DPT,
                    BalanceConfig.GEAR_CURVE_PER_REGION, depth, BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE);
            if (expected != BalanceConfig.REFERENCE_PLAYER_DPT) anchorHeld = false;
        }
        results.add(new RuleResult(RuleKind.GEAR_GATE, "region-1 anchor (expected == reference)",
                anchorHeld ? 1f : 0f, 1f, 1f, anchorHeld,
                "expectedPlayerDamagePerTurn(1.." + BalanceConfig.GEAR_CURVE_REGION_BAND_SIZE
                        + ") must equal REFERENCE_PLAYER_DPT exactly (the run begins on the curve)"));
        return results;
    }

    // =====================================================================================
    // R-ABILITY (new-game-balancr order 2) — TIER = a priced ABILITY BUDGET. Two guarantees:
    //   (a) COVERAGE — every catalogued ability (standard and legendary signature) has a registered,
    //       finite, non-negative PP price at every weapon level.
    //   (b) BUDGET — no ROLLABLE ability alone exceeds the ceiling of the CHEAPEST tier that can roll
    //       it, and the full roll never exceeds its tier ceiling (the WeaponRoller enforces the sum by
    //       construction; BalanceAuditTest exercises real rolls across seeds/levels for the sum).
    // The tier budgets (COMMON 0 | UNCOMMON 6 | RARE 12 | EPIC 20 | LEGENDARY 30, ±20%) live in
    // BalanceConfig SECTION 15. Magnitudes may be tuned freely — the price recomputes from them.
    // =====================================================================================

    /** R-ABILITY: every ability is priced (coverage) and prices are finite/non-negative at all levels. */
    public static List<RuleResult> abilityBudgetResults() {
        List<RuleResult> results = new ArrayList<>();
        float legendaryCeiling = BalanceConfig.TIER_ABILITY_PP_BUDGET_LEGENDARY
                * (1f + BalanceConfig.TIER_ABILITY_PP_TOLERANCE);
        for (WeaponAbility ability : WeaponAbility.values()) {
            float maxPriceAcrossLevels = 0f;
            boolean priced = true;
            for (int weaponLevel = 1; weaponLevel <= WeaponConstants.MAX_WEAPON_LEVEL; weaponLevel++) {
                AbilityInstance instance = WeaponRoller.buildAbilityInstance(ability, weaponLevel);
                float price = GameMath.abilityPowerPoints(ability, instance.magnitude, instance.countValue);
                if (Float.isNaN(price) || Float.isInfinite(price) || price < 0f) priced = false;
                maxPriceAcrossLevels = Math.max(maxPriceAcrossLevels, price);
            }
            // No single ability may exceed the richest tier's ceiling — otherwise it could never roll.
            boolean fitsSomewhere = maxPriceAcrossLevels <= legendaryCeiling;
            results.add(new RuleResult(RuleKind.ABILITY_BUDGET, "ability " + ability.name(),
                    maxPriceAcrossLevels, 0f, legendaryCeiling, priced && fitsSomewhere,
                    priced ? (fitsSomewhere ? "priced; max-level PP fits the legendary ceiling"
                                            : "max-level PP EXCEEDS the legendary ceiling — no tier can roll it")
                           : "UNPRICED — GameMath.abilityPowerPoints returned a non-finite/negative value"));
        }
        // Tier budgets must be monotonic non-decreasing with rarity (a richer tier is never poorer).
        float previousBudget = -1f;
        boolean monotonic = true;
        for (WeaponTier tier : WeaponTier.values()) {
            float budget = WeaponRoller.tierAbilityPowerPointBudget(tier);
            if (budget < previousBudget) monotonic = false;
            previousBudget = budget;
        }
        results.add(new RuleResult(RuleKind.ABILITY_BUDGET, "tier budgets monotonic",
                monotonic ? 1f : 0f, 1f, 1f, monotonic,
                "TIER_ABILITY_PP_BUDGET_* must not decrease as rarity rises"));
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
     * R-BOSS-GATE: with the DEPTH-1 starting loadout (reference DPT), the turns to kill the boss must be at
     * least {@link BalanceConfig#BOSS_GATE_MIN_DAMAGE_MARGIN} times the turns the player survives even WITH
     * the maximum modelled heal supply — so the start weapon can never win. The margin is depth-independent
     * of the fight length and rises with the expected-DPT gate, so the shallowest boss is the tightest case.
     */
    public static List<RuleResult> bossGateResults() {
        List<RuleResult> results = new ArrayList<>();
        float minMargin  = BalanceConfig.BOSS_GATE_MIN_DAMAGE_MARGIN;
        float maxHeals   = BalanceConfig.REFERENCE_PLAYER_EHP
                * BalanceConfig.BOSS_GATE_MODELED_HEAL_SUPPLY_EHP_FRACTION;
        for (BossBalance.Archetype archetype : BossBalance.Archetype.values()) {
            BossStats stats = BossBalance.statsForDepth(archetype, archetype.canonicalDepth);
            float startTurnsToKill = GameMath.bossFightTurnsForPlayerDamagePerTurn(
                    stats.effectiveHitPoints, BalanceConfig.REFERENCE_PLAYER_DPT);
            float survivableTurns  = GameMath.bossFightTurnsForPlayerDamagePerTurn(
                    BalanceConfig.REFERENCE_PLAYER_EHP + maxHeals, stats.damagePerTurn);
            float margin = survivableTurns > 0f ? startTurnsToKill / survivableTurns : Float.POSITIVE_INFINITY;
            results.add(new RuleResult(RuleKind.BOSS_GATE, archetype.displayName + " depth " + archetype.canonicalDepth,
                    margin, minMargin, Float.POSITIVE_INFINITY, margin >= minMargin,
                    String.format("start-weapon TTK %.1f vs survivable %.1f turns (with max heals) — must be >= %.1fx",
                            startTurnsToKill, survivableTurns, minMargin)));
        }
        return results;
    }

    /**
     * R-BOSS-FAIR: the EXPECTED player at each boss depth gets a fight whose LENGTH lands in
     * [target, 1.5*target] turns and whose SURVIVAL RATIO lands in [0.4, 0.7]. Both hold by construction of
     * the derivation, so a broken input (a bad target or ratio) surfaces here rather than in a live fight.
     */
    public static List<RuleResult> bossFairResults() {
        List<RuleResult> results = new ArrayList<>();
        for (BossBalance.Archetype archetype : BossBalance.Archetype.values()) {
            BossStats stats     = BossBalance.statsForDepth(archetype, archetype.canonicalDepth);
            float expectedDpt   = BossBalance.expectedPlayerDamagePerTurn(archetype.canonicalDepth);
            float fightTurns    = GameMath.bossFightTurnsForPlayerDamagePerTurn(
                    stats.effectiveHitPoints, expectedDpt);
            float lengthMin     = stats.targetFightTurns;
            float lengthMax     = stats.upperFightTurnsCap; // == 1.5 * target
            results.add(new RuleResult(RuleKind.BOSS_FAIR, archetype.displayName + " fight length",
                    fightTurns, lengthMin, lengthMax, fightTurns >= lengthMin && fightTurns <= lengthMax,
                    "expected-loadout fight turns must land in [target, 1.5*target]"));

            float survivalRatio = GameMath.bossSurvivalCheckRatio(
                    BalanceConfig.REFERENCE_PLAYER_EHP, stats.damagePerTurn, fightTurns);
            results.add(new RuleResult(RuleKind.BOSS_FAIR, archetype.displayName + " survival ratio",
                    survivalRatio, BalanceConfig.BOSS_SURVIVAL_CHECK_RATIO_MIN,
                    BalanceConfig.BOSS_SURVIVAL_CHECK_RATIO_MAX,
                    survivalRatio >= BalanceConfig.BOSS_SURVIVAL_CHECK_RATIO_MIN
                            && survivalRatio <= BalanceConfig.BOSS_SURVIVAL_CHECK_RATIO_MAX,
                    "a no-heal player survives this fraction of the fight"));
        }
        return results;
    }

    /**
     * R-BOSS-VERB-CAP: every derived boss verb, evaluated at the boss's canonical depth, respects the
     * single-hit fairness caps — no hit over 35% of reference eHP (hard cap, telegraphed or not), and any
     * hit over 25% must be telegraphed a turn ahead. The band max is 25% for an un-telegraphed verb, 35%
     * for a telegraphed one; the value is the verb's fraction of reference eHP.
     */
    public static List<RuleResult> bossVerbCapResults() {
        List<RuleResult> results = new ArrayList<>();
        for (BossVerbSpec verb : BOSS_VERBS) {
            BossStats stats = BossBalance.statsForDepth(verb.archetype, verb.archetype.canonicalDepth);
            int damage = stats.verbDamage(verb.dptFraction);
            float fraction = GameMath.bossSingleHitFractionOfEffectiveHitPoints(
                    damage, BalanceConfig.REFERENCE_PLAYER_EHP);
            float bandMax = verb.telegraphed
                    ? BalanceConfig.BOSS_HARD_SINGLE_HIT_FRACTION
                    : BalanceConfig.TELEGRAPH_MAX_UNTELEGRAPHED_HIT_FRACTION;
            results.add(new RuleResult(RuleKind.BOSS_VERB_CAP, verb.verbName, fraction, 0f, bandMax,
                    fraction <= bandMax,
                    String.format("derived %d dmg = %.0f%% eHP; %s cap %.0f%%", damage, fraction * 100f,
                            verb.telegraphed ? "telegraphed" : "un-telegraphed", bandMax * 100f)));
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
        for (BossBalance.Archetype archetype : BossBalance.Archetype.values()) {
            BossStats stats = BossBalance.statsForDepth(archetype, archetype.canonicalDepth);
            float consumption = BossBalance.modelledConsumptionCredits(stats.effectiveHitPoints);
            float ratio = consumption > 0f ? stats.creditReward / consumption : Float.POSITIVE_INFINITY;
            results.add(new RuleResult(RuleKind.BOSS_REWARD, archetype.displayName + " reward/consumption",
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
        for (BossBalance.Archetype archetype : BossBalance.Archetype.values()) {
            BossStats stats = BossBalance.statsForDepth(archetype, archetype.canonicalDepth);
            float demand       = BossBalance.modelledAmmoDemandDamage(stats.effectiveHitPoints);
            float reserveDamage = BalanceConfig.RESERVE_BANKING_FLOORS_TARGET
                    * GameMath.floorDemandAtDepth(modelFloorDemand(),
                            BalanceConfig.ENEMY_HEALTH_SCALE_PER_DEPTH, archetype.canonicalDepth);
            float arenaDamage  = BossBalance.arenaAmmoBudgetDamage(stats.effectiveHitPoints);
            float coverage     = demand > 0f ? (reserveDamage + arenaDamage) / demand : Float.POSITIVE_INFINITY;
            results.add(new RuleResult(RuleKind.BOSS_AMMO, archetype.displayName + " ammo coverage",
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

    /** The audited depth range shared by the order-7 route rules (matches R-DEPTH / R-SCARCITY-DEPTH). */
    private static final int ROUTE_AUDIT_MAX_DEPTH = 15;

    /**
     * The order-3 MODEL FLOOR, handed to the route model so the map is priced against the very same
     * reference encounter the floor rules use (one source — the map can never drift from the floor).
     */
    public static RouteEconomicsModel.ModelFloor routeModelFloor() {
        float roomBoxes = BalanceConfig.MODEL_FLOOR_ROOM_COUNT * BalanceConfig.LEVEL_GEN_AMMO_CHANCE_PER_ROOM;
        float killBoxes = modelFloorEnemyCount() * BalanceConfig.ENEMY_AMMO_DROP_CHANCE;
        float totalBoxes = Math.max(1e-3f, roomBoxes + killBoxes);
        float totalSupply = modelFloorTotalRangedSupply();
        float averageBoxDamage = totalSupply / totalBoxes;
        float incoming = GameMath.incomingDamagePerFloor(modelFloorEnemyDamagePerTurn(),
                BalanceConfig.MODEL_FLOOR_TURNS_ENGAGED_PER_ENEMY, BalanceConfig.MODEL_FLOOR_AVOIDANCE_FACTOR);
        float averageMedkitHeal  = (BalanceConfig.MEDKIT_STIM_HEAL + BalanceConfig.MEDKIT_FULL_HEAL) / 2f;
        float averageArmourValue = (BalanceConfig.ARMOUR_SHARD_VALUE + BalanceConfig.ARMOUR_VEST_VALUE) / 2f;
        float healSupply = GameMath.healSupplyPerFloor(
                BalanceConfig.MODEL_FLOOR_EXPECTED_MEDKITS, averageMedkitHeal,
                BalanceConfig.MODEL_FLOOR_EXPECTED_ARMOUR_PICKUPS, averageArmourValue);
        return new RouteEconomicsModel.ModelFloor(modelFloorDemand(),
                totalSupply * (roomBoxes / totalBoxes), totalSupply * (killBoxes / totalBoxes),
                averageBoxDamage, incoming, healSupply, modelFloorKillCreditReward(), chipIncomePerFloor(),
                regionAbilityBudgetPoints());
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
}
