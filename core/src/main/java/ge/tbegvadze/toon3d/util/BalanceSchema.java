package ge.tbegvadze.toon3d.util;

import ge.tbegvadze.toon3d.enemy.EnemyRole;
import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.item.ItemCategory;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.progression.UpgradeCard;

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
        COVERAGE
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
                "Expires when the order-3 scarcity audit proves the slug gate in real play.");
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
        // Boss attacks: every damaging boss verb is telegraphed one turn ahead by the intent system.
        registry.add(new TelegraphAttackSpec("Overseer laser",     BalanceConfig.OVERSEER_LASER_DAMAGE,     "TELE", true));
        registry.add(new TelegraphAttackSpec("Overseer charge",    BalanceConfig.OVERSEER_CHARGE_DAMAGE,    "TELE", true));
        registry.add(new TelegraphAttackSpec("Overseer melee",     BalanceConfig.OVERSEER_MELEE_DAMAGE,     "NONE", true));
        registry.add(new TelegraphAttackSpec("Corruptor acid",     BalanceConfig.CORRUPTOR_ACID_DAMAGE,     "TELE", true));
        registry.add(new TelegraphAttackSpec("Hell Baron cleave1", BalanceConfig.HELL_BARON_CLEAVE_DAMAGE_P1, "TELE", true));
        registry.add(new TelegraphAttackSpec("Hell Baron cleave2", BalanceConfig.HELL_BARON_CLEAVE_DAMAGE_P2, "TELE", true));
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

    // Every constant-bearing class scanned for live test/debug flags (R-FLAGS).
    private static final Class<?>[] FLAG_SCAN_CLASSES = {
            BalanceConfig.class, GameBalance.class, Constants.class, WeaponConstants.class,
            EnemyConstants.class, ItemConstants.class, LevelGenConstants.class, EffectConstants.class,
            RouteMapConstants.class, IntentConstants.class, TilesetConstants.class,
            ProgressionConstants.class, HudConstants.class, RenderConstants.class, TouchConstants.class
    };

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
        float enemyEffectiveHitPoints = GameMath.effectiveHitPoints(enemyType.maxHealth(), 0f, 0f, 0f, 0f);
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

    /** R-DEPTH: the depth-coupling ratio holds its band across depths 1..15. */
    public static List<RuleResult> depthCouplingResults() {
        List<RuleResult> results = new ArrayList<>();
        for (int depth = 1; depth <= 15; depth++) {
            float playerPower = GameMath.playerPowerAtDepth(BalanceConfig.LEVEL_UP_BUDGET_PP,
                    BalanceConfig.EXPECTED_LEVELS_PER_DEPTH, depth);
            float enemyThreat = GameMath.depthThreatScale(BalanceConfig.ENEMY_HEALTH_SCALE_PER_DEPTH,
                    BalanceConfig.ENEMY_DAMAGE_SCALE_PER_DEPTH, depth);
            float ratio = GameMath.depthCouplingRatio(playerPower, enemyThreat);
            boolean inBand = ratio >= BalanceConfig.DEPTH_COUPLING_RATIO_MIN
                    && ratio <= BalanceConfig.DEPTH_COUPLING_RATIO_MAX;
            results.add(new RuleResult(RuleKind.DEPTH_COUPLING, "depth " + depth, ratio,
                    BalanceConfig.DEPTH_COUPLING_RATIO_MIN, BalanceConfig.DEPTH_COUPLING_RATIO_MAX,
                    inBand, null));
        }
        return results;
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
        return results;
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
