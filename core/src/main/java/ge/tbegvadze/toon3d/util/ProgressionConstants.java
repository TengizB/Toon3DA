package ge.tbegvadze.toon3d.util;

/** Progression and permadeath constants — debug flag, death beat, persistent stats key. */
public final class ProgressionConstants {

    private ProgressionConstants() {}

    // Permadeath — death beat and persistent stats
    public static boolean debug                         = false;  // immortal mode; flip to true for testing
    public static final float  DEATH_BEAT_DURATION_SECONDS = 1.0f;
    public static final String STATS_PREFS_NAME             = "toon3d_records";

    // ---- Build tag chip colours (balance-overhaul order 5, CP5) ------------------------------------------
    // The six weapon tags reuse the order-3 damage-class palette (WeaponConstants.DAMAGE_CLASS_COLOR_*) through
    // render/BuildTagPalette; only the three tags that own no weapon class need a colour of their own.
    public static final float BUILD_TAG_BULWARK_RED     = 0.30f;  // blue
    public static final float BUILD_TAG_BULWARK_GREEN   = 0.55f;
    public static final float BUILD_TAG_BULWARK_BLUE    = 1.00f;
    public static final float BUILD_TAG_VITALITY_RED    = 0.30f;  // green
    public static final float BUILD_TAG_VITALITY_GREEN  = 0.90f;
    public static final float BUILD_TAG_VITALITY_BLUE   = 0.40f;
    public static final float BUILD_TAG_SCAVENGER_RED   = 0.80f;  // brass
    public static final float BUILD_TAG_SCAVENGER_GREEN = 0.62f;
    public static final float BUILD_TAG_SCAVENGER_BLUE  = 0.25f;

    /** SYNERGY ribbon / label colour on a level-up plate (amber). */
    public static final float BUILD_SYNERGY_RED   = 1.00f;
    public static final float BUILD_SYNERGY_GREEN = 0.72f;
    public static final float BUILD_SYNERGY_BLUE  = 0.05f;

    /** A keystone's cost line (the order-5 spec's cost colour, red). */
    public static final float BUILD_COST_RED   = 0.95f;
    public static final float BUILD_COST_GREEN = 0.30f;
    public static final float BUILD_COST_BLUE  = 0.25f;

    /** Keystone gold frame. */
    public static final float BUILD_KEYSTONE_GOLD_RED   = 1.00f;
    public static final float BUILD_KEYSTONE_GOLD_GREEN = 0.84f;
    public static final float BUILD_KEYSTONE_GOLD_BLUE  = 0.20f;
}
