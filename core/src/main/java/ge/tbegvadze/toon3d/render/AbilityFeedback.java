package ge.tbegvadze.toon3d.render;

import ge.tbegvadze.toon3d.entity.WeaponAbility;
import ge.tbegvadze.toon3d.util.EffectConstants;

/**
 * Facade that routes weapon ability proc events to the correct visual tier:
 * event-text banner (EventTextSystem) plus an optional ring pulse (ImpactEffectSystem).
 *
 * Tier routing is encoded in static lookup tables built once at class load — O(1) dispatch,
 * zero per-announce allocation.  TIER_TAG abilities are silently skipped (suffix glyph only,
 * Phase 2).  ON_KILL abilities at TIER_SLAM or higher are held for
 * KILL_PROC_CASCADE_DELAY_SECONDS so the banner appears after the kill burst, not during it.
 *
 * announceCrit() builds dynamic "CRITICAL -N" text using a pre-allocated StringBuilder;
 * toString() allocation is acceptable at sim time (called from game-logic tick, not render()).
 */
public final class AbilityFeedback {

    // ── Static palette (never mutated) ────────────────────────────────────────
    private static final float WHITE_HOT_R = 1.00f, WHITE_HOT_G = 0.95f, WHITE_HOT_B = 0.90f;
    private static final float ORANGE_R    = 1.00f, ORANGE_G    = 0.55f, ORANGE_B    = 0.15f;
    private static final float CRIMSON_R   = 1.00f, CRIMSON_G   = 0.15f, CRIMSON_B   = 0.20f;
    private static final float EMBER_R     = 1.00f, EMBER_G     = 0.35f, EMBER_B     = 0.05f;
    private static final float CYAN_R      = 0.45f, CYAN_G      = 0.85f, CYAN_B      = 1.00f;
    private static final float GREEN_R     = 0.25f, GREEN_G     = 1.00f, GREEN_B     = 0.25f;
    private static final float AMBER_R     = 1.00f, AMBER_G     = 0.75f, AMBER_B     = 0.10f;
    private static final float GOLD_R      = 1.00f, GOLD_G      = 0.69f, GOLD_B      = 0.125f;
    private static final float ELECTRIC_R  = 0.55f, ELECTRIC_G  = 0.90f, ELECTRIC_B  = 1.00f;
    private static final float WHITE_R     = 1.00f, WHITE_G     = 1.00f, WHITE_B     = 1.00f;

    // ── Lookup tables indexed by WeaponAbility.ordinal() ─────────────────────
    private static final int ABILITY_COUNT = WeaponAbility.values().length;
    private static final byte[]    ABILITY_TIER;
    private static final float[]   ABILITY_RED;
    private static final float[]   ABILITY_GREEN;
    private static final float[]   ABILITY_BLUE;
    private static final String[]  ABILITY_TEXT;
    /** True for abilities whose effect is restoring player HP — triggers heal vignette and particles. */
    private static final boolean[] ABILITY_IS_HEAL;

    static {
        ABILITY_TIER    = new byte   [ABILITY_COUNT];
        ABILITY_RED     = new float  [ABILITY_COUNT];
        ABILITY_GREEN   = new float  [ABILITY_COUNT];
        ABILITY_BLUE    = new float  [ABILITY_COUNT];
        ABILITY_TEXT    = new String [ABILITY_COUNT];
        ABILITY_IS_HEAL = new boolean[ABILITY_COUNT];

        set(WeaponAbility.BURST_FIRE,         EventTextSystem.TIER_PROC,      ORANGE_R,    ORANGE_G,    ORANGE_B,    "BURST");
        set(WeaponAbility.CRITICAL_STRIKE,    EventTextSystem.TIER_SLAM,      WHITE_HOT_R, WHITE_HOT_G, WHITE_HOT_B, "CRITICAL");
        set(WeaponAbility.ARMOR_PIERCE,       EventTextSystem.TIER_TAG,       CYAN_R,      CYAN_G,      CYAN_B,      "PIERCE");
        set(WeaponAbility.EXECUTIONER,        EventTextSystem.TIER_PROC,      ORANGE_R,    ORANGE_G,    ORANGE_B,    "EXECUTE!");
        set(WeaponAbility.REND,               EventTextSystem.TIER_PROC,      CRIMSON_R,   CRIMSON_G,   CRIMSON_B,   "[R] BLEED");
        set(WeaponAbility.OVERPENETRATION,    EventTextSystem.TIER_TAG,       CYAN_R,      CYAN_G,      CYAN_B,      "PEN");
        set(WeaponAbility.STAGGER_ROUNDS,     EventTextSystem.TIER_PROC,      ORANGE_R,    ORANGE_G,    ORANGE_B,    "[S] STAGGER");
        set(WeaponAbility.KINETIC_SLAM,       EventTextSystem.TIER_PROC,      ORANGE_R,    ORANGE_G,    ORANGE_B,    "SLAM!");
        set(WeaponAbility.CLEAVE,             EventTextSystem.TIER_PROC,      ORANGE_R,    ORANGE_G,    ORANGE_B,    "CLEAVE!");
        set(WeaponAbility.INCENDIARY,         EventTextSystem.TIER_PROC,      EMBER_R,     EMBER_G,     EMBER_B,     "[I] BURN");
        set(WeaponAbility.LIFESTEAL,          EventTextSystem.TIER_PROC,      GREEN_R,     GREEN_G,     GREEN_B,     "LIFESTEAL");
        set(WeaponAbility.HEMORRHAGE_HARVEST, EventTextSystem.TIER_SLAM,      CRIMSON_R,   CRIMSON_G,   CRIMSON_B,   "HARVEST!");
        set(WeaponAbility.VAMPIRIC_CRIT,      EventTextSystem.TIER_SLAM,      GREEN_R,     GREEN_G,     GREEN_B,     "VAMP CRIT");
        set(WeaponAbility.ADRENAL_SURGE,      EventTextSystem.TIER_SLAM,      GREEN_R,     GREEN_G,     GREEN_B,     "SURGE!");
        set(WeaponAbility.BULWARK_ROUNDS,     EventTextSystem.TIER_PROC,      CYAN_R,      CYAN_G,      CYAN_B,      "BULWARK");
        set(WeaponAbility.SECOND_WIND,        EventTextSystem.TIER_PROC,      GREEN_R,     GREEN_G,     GREEN_B,     "SECOND WIND");
        set(WeaponAbility.SCAVENGER_ROUNDS,   EventTextSystem.TIER_SLAM,      AMBER_R,     AMBER_G,     AMBER_B,     "SCAVENGE!");
        set(WeaponAbility.SALVAGE_STRIKE,     EventTextSystem.TIER_PROC,      AMBER_R,     AMBER_G,     AMBER_B,     "SALVAGE!");
        set(WeaponAbility.SCHOLARS_EDGE,      EventTextSystem.TIER_PROC,      AMBER_R,     AMBER_G,     AMBER_B,     "SCHOLAR'S EDGE");
        set(WeaponAbility.QUICK_HANDS,        EventTextSystem.TIER_TAG,       WHITE_R,     WHITE_G,     WHITE_B,     "QUICK");
        set(WeaponAbility.EXTENDED_MAG,       EventTextSystem.TIER_TAG,       WHITE_R,     WHITE_G,     WHITE_B,     "EXT.MAG");
        set(WeaponAbility.FIELD_MEDIC_ROUNDS, EventTextSystem.TIER_SLAM,      GREEN_R,     GREEN_G,     GREEN_B,     "MEDKIT DROP!");
        set(WeaponAbility.CREDIT_FANG,        EventTextSystem.TIER_PROC,      AMBER_R,     AMBER_G,     AMBER_B,     "CREDIT FANG");
        set(WeaponAbility.POINT_BLANK,        EventTextSystem.TIER_TAG,       ORANGE_R,    ORANGE_G,    ORANGE_B,    "POINT BLANK");
        set(WeaponAbility.MARKSMANS_PATIENCE, EventTextSystem.TIER_TAG,       WHITE_R,     WHITE_G,     WHITE_B,     "PATIENCE");
        set(WeaponAbility.OPENING_SALVO,      EventTextSystem.TIER_PROC,      WHITE_R,     WHITE_G,     WHITE_B,     "OPENING!");
        set(WeaponAbility.RHYTHM,             EventTextSystem.TIER_TAG,       WHITE_R,     WHITE_G,     WHITE_B,     "RHYTHM");
        set(WeaponAbility.STATIC_DISCHARGE,   EventTextSystem.TIER_SLAM,      ELECTRIC_R,  ELECTRIC_G,  ELECTRIC_B,  "[~] DISCHARGE");
        set(WeaponAbility.RESONANT_ROUNDS,    EventTextSystem.TIER_PROC,      ELECTRIC_R,  ELECTRIC_G,  ELECTRIC_B,  "RESONANCE");
        set(WeaponAbility.SOULFORGE,          EventTextSystem.TIER_LEGENDARY, GOLD_R,      GOLD_G,      GOLD_B,      "SOULFORGE");
        set(WeaponAbility.JUDGMENT,           EventTextSystem.TIER_LEGENDARY, GOLD_R,      GOLD_G,      GOLD_B,      "JUDGMENT!");
        set(WeaponAbility.HELLFIRE_NOVA,      EventTextSystem.TIER_LEGENDARY, EMBER_R,     EMBER_G,     EMBER_B,     "HELLFIRE NOVA");
        set(WeaponAbility.BERSERKERS_OATH,    EventTextSystem.TIER_LEGENDARY, CRIMSON_R,   CRIMSON_G,   CRIMSON_B,   "BERSERKER'S OATH");

        // Abilities that directly restore player HP — spawn green particles + vignette.
        // SECOND_WIND boosts damage but does not heal. FIELD_MEDIC_ROUNDS drops a pickup.
        ABILITY_IS_HEAL[WeaponAbility.LIFESTEAL.ordinal()]           = true;
        ABILITY_IS_HEAL[WeaponAbility.VAMPIRIC_CRIT.ordinal()]       = true;
        ABILITY_IS_HEAL[WeaponAbility.ADRENAL_SURGE.ordinal()]       = true;
        ABILITY_IS_HEAL[WeaponAbility.HEMORRHAGE_HARVEST.ordinal()]  = true;
        ABILITY_IS_HEAL[WeaponAbility.SOULFORGE.ordinal()]           = true;
    }

    private static void set(WeaponAbility ability, byte tier,
                             float red, float green, float blue, String text) {
        int ordinal            = ability.ordinal();
        ABILITY_TIER  [ordinal] = tier;
        ABILITY_RED   [ordinal] = red;
        ABILITY_GREEN [ordinal] = green;
        ABILITY_BLUE  [ordinal] = blue;
        ABILITY_TEXT  [ordinal] = text;
    }

    // ── Instance fields ───────────────────────────────────────────────────────
    private final EventTextSystem    eventTextSystem;
    private final ImpactEffectSystem impactEffectSystem;
    // Nullable — wired by World after construction; null-checked before use.
    private HitVignetteRenderer healVignetteRenderer = null;

    /** Wires the green vignette renderer so heal procs can flash the screen edges. */
    public void setHealVignetteRenderer(HitVignetteRenderer renderer) {
        this.healVignetteRenderer = renderer;
    }

    // Pending kill-cascade ring buffer — ON_KILL TIER_SLAM+ events delayed briefly
    private static final int PENDING_POOL_SIZE   = 8;
    private final int[]      pendingOrdinal       = new int  [PENDING_POOL_SIZE];
    private final int[]      pendingTileColumn    = new int  [PENDING_POOL_SIZE];
    private final int[]      pendingTileRow       = new int  [PENDING_POOL_SIZE];
    private final float[]    pendingHeightMult    = new float[PENDING_POOL_SIZE];
    private final float[]    pendingDelay         = new float[PENDING_POOL_SIZE];
    private final boolean[]  pendingActive        = new boolean[PENDING_POOL_SIZE];

    // Pre-allocated scratch buffer for announceCrit() dynamic text — sim-thread only
    private final StringBuilder buildBuffer = new StringBuilder(32);

    public AbilityFeedback(EventTextSystem eventTextSystem, ImpactEffectSystem impactEffectSystem) {
        this.eventTextSystem    = eventTextSystem;
        this.impactEffectSystem = impactEffectSystem;
    }

    /**
     * Routes an ability proc event to its visual tier.
     * TIER_TAG abilities are silently skipped (no banner in Phase 1).
     * ON_KILL abilities at TIER_SLAM or higher are cascade-delayed before display.
     *
     * @param ability    the ability that triggered
     * @param tileColumn enemy tile column; pass -1 when there is no target (e.g. ON_RELOAD)
     * @param tileRow    enemy tile row; pass -1 when there is no target
     * @param heightMult enemy height multiplier for ring pulse Y projection
     */
    public void announce(WeaponAbility ability, int tileColumn, int tileRow, float heightMult) {
        int  ordinal = ability.ordinal();
        byte tier    = ABILITY_TIER[ordinal];

        if (tier == 0 || tier == EventTextSystem.TIER_TAG) return;

        if (ability.trigger == WeaponAbility.Trigger.ON_KILL
                && tier >= EventTextSystem.TIER_SLAM) {
            schedulePending(ordinal, tileColumn, tileRow, heightMult);
            return;
        }
        fireImmediate(ordinal, tileColumn, tileRow, heightMult);
    }

    /**
     * Announces a critical hit as a TIER_SLAM "CRITICAL -N" banner.
     * Also triggers the crit edge-flash overlay and a white ring pulse at the target.
     *
     * @param tileColumn  enemy tile column
     * @param tileRow     enemy tile row
     * @param heightMult  enemy height multiplier
     * @param totalDamage combined crit damage (base + bonus) — appended to the banner text
     */
    public void announceCrit(int tileColumn, int tileRow, float heightMult, int totalDamage) {
        buildBuffer.setLength(0);
        buildBuffer.append("CRITICAL -");
        buildBuffer.append(totalDamage);
        // toString() allocates a new String; acceptable at sim time, not in render()
        eventTextSystem.spawnBanner(buildBuffer.toString(),
                WHITE_HOT_R, WHITE_HOT_G, WHITE_HOT_B, EventTextSystem.TIER_SLAM);

        impactEffectSystem.triggerCritFlash();

        if (tileColumn >= 0 && tileRow >= 0) {
            impactEffectSystem.spawnColoredRingPulse(tileColumn, tileRow, heightMult,
                    WHITE_HOT_R, WHITE_HOT_G, WHITE_HOT_B);
        }
    }

    /** Advances the cascade queue, firing events whose delay has elapsed. */
    public void update(float deltaTime) {
        for (int slotIndex = 0; slotIndex < PENDING_POOL_SIZE; slotIndex++) {
            if (!pendingActive[slotIndex]) continue;
            pendingDelay[slotIndex] -= deltaTime;
            if (pendingDelay[slotIndex] <= 0f) {
                pendingActive[slotIndex] = false;
                fireImmediate(pendingOrdinal  [slotIndex],
                              pendingTileColumn[slotIndex],
                              pendingTileRow   [slotIndex],
                              pendingHeightMult[slotIndex]);
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void schedulePending(int ordinal, int tileColumn, int tileRow, float heightMult) {
        for (int slotIndex = 0; slotIndex < PENDING_POOL_SIZE; slotIndex++) {
            if (!pendingActive[slotIndex]) {
                pendingOrdinal   [slotIndex] = ordinal;
                pendingTileColumn[slotIndex] = tileColumn;
                pendingTileRow   [slotIndex] = tileRow;
                pendingHeightMult[slotIndex] = heightMult;
                pendingDelay     [slotIndex] = EffectConstants.KILL_PROC_CASCADE_DELAY_SECONDS;
                pendingActive    [slotIndex] = true;
                return;
            }
        }
        // Pool full — fire immediately rather than silently dropping the event
        fireImmediate(ordinal, tileColumn, tileRow, heightMult);
    }

    private void fireImmediate(int ordinal, int tileColumn, int tileRow, float heightMult) {
        byte   tier  = ABILITY_TIER [ordinal];
        float  red   = ABILITY_RED  [ordinal];
        float  green = ABILITY_GREEN[ordinal];
        float  blue  = ABILITY_BLUE [ordinal];
        String text  = ABILITY_TEXT [ordinal];

        eventTextSystem.spawnBanner(text, red, green, blue, tier);

        if (ABILITY_IS_HEAL[ordinal]) {
            impactEffectSystem.spawnHealParticles();
            if (healVignetteRenderer != null) {
                healVignetteRenderer.triggerHeal();
            }
        }

        if (tier >= EventTextSystem.TIER_SLAM && tileColumn >= 0 && tileRow >= 0) {
            impactEffectSystem.spawnColoredRingPulse(tileColumn, tileRow, heightMult,
                    red, green, blue);
        }
    }
}
