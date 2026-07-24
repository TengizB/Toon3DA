package ge.tbegvadze.toon3d.util;

import ge.tbegvadze.toon3d.enemy.EnemyRole;
import ge.tbegvadze.toon3d.enemy.EnemyType;
import ge.tbegvadze.toon3d.entity.AbilityInstance;
import ge.tbegvadze.toon3d.entity.WeaponAbility;
import ge.tbegvadze.toon3d.entity.WeaponRoller;
import ge.tbegvadze.toon3d.entity.WeaponTier;
import ge.tbegvadze.toon3d.item.AmmoType;
import ge.tbegvadze.toon3d.item.ItemCategory;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.util.ItemConstants;
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
        CARD_BREAKPOINT
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
                "Re-checked by order-3 R-SCARCITY-DEPTH: slug reserve banking stays the TIGHTEST gate "
                        + "(~1.0 floor at depth 1, tightest of all ammo types) after the shop re-pricing.");
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
        results.addAll(gearGateResults());
        results.addAll(abilityBudgetResults());
        results.addAll(scarcityDepthResults());
        results.addAll(healDrainDepthResults());
        results.addAll(creditResults());
        results.addAll(xpPaceResults());
        results.addAll(cardBreakpointResults());
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
        float soldierEffectiveHitPoints = GameMath.effectiveHitPoints(soldier.maxHealth(), 0f, 0f, 0f, 0f)
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
        float enemyEffectiveHitPoints = GameMath.effectiveHitPoints(enemyType.maxHealth(), 0f, 0f, 0f, 0f)
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
