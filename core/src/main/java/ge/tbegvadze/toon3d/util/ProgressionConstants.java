package ge.tbegvadze.toon3d.util;

/** Progression and permadeath constants — debug flag, death beat, persistent stats key. */
public final class ProgressionConstants {

    private ProgressionConstants() {}

    // Permadeath — death beat and persistent stats
    public static boolean debug                         = false;  // immortal mode; flip to true for testing
    public static final float  DEATH_BEAT_DURATION_SECONDS = 1.0f;
    public static final String STATS_PREFS_NAME             = "toon3d_records";
}
