package ge.tbegvadze.toon3d.util;

/**
 * Every tunable number for the GAMEPLAY sound layer (procedural-sound-effects order 1) — synthesis
 * format, the variation cycle, distance/direction perception, and the mixing rules.
 *
 * <p>This is deliberately separate from {@code StoryUiConstants}' {@code STORY_STING_*} block: the
 * story layer's seven tones are a UI voice (sine/square blips at 44100 Hz), while this layer is
 * percussive broadband sound at 22050 Hz.  The two never share a number, and each generated WAV
 * declares its own sample rate in its header, so the rates never have to agree.
 *
 * <p>Per-sound recipes (which layers, which frequencies, how loud) are NOT here — they live in
 * {@code audio/GameSoundCatalog}, because a sound is CONTENT and adding one must stay a single
 * {@code register()} call.  What lives here is everything that applies across the whole layer.
 */
public final class SoundConstants {

    private SoundConstants() {}

    // -------------------------------------------------------------------------------------
    // Synthesis format
    // -------------------------------------------------------------------------------------
    /**
     * 22050 Hz, deliberately half the story layer's rate.  A phone speaker reproduces almost
     * nothing usable above ~10 kHz, every noise layer in the catalog is low-passed below 6 kHz
     * anyway, and halving the rate halves both synthesis cost and resident memory.
     */
    public static final int   GAME_SFX_SAMPLE_RATE_HZ        = 22050;
    /**
     * No sound may attack instantaneously.  A full-scale step on a cheap phone speaker reads as
     * "broken plastic" rather than "loud", so every envelope ramps over at least this long
     * (~18 samples at 22050 Hz) even when its recipe asks for zero.
     */
    public static final float GAME_SFX_MINIMUM_ATTACK_SECONDS = 0.0008f;
    /** Longest sound the catalog may hold (the player's death); everything else stays under 0.6 s. */
    public static final float GAME_SFX_MAX_DURATION_SECONDS   = 1.0f;
    /** Hard ceiling on distinct synthesised sounds, guarding the resident-memory budget. */
    public static final int   GAME_SFX_MAX_DISTINCT_SOUNDS    = 64;
    /** Directory under the local storage root where generated WAVs are cached. */
    public static final String GAME_SFX_GENERATED_DIRECTORY   = "game-audio/";

    // -------------------------------------------------------------------------------------
    // Variation — one WAV per sound, all variety from Sound.play(volume, pitch, pan)
    // -------------------------------------------------------------------------------------
    /**
     * The repeating pitch cycle.  Chosen so no two adjacent entries are equal and the sequence
     * does not drift — pure randomness repeats values, and a gun that fires the same pitch twice
     * in a row sounds broken.
     */
    public static final float[] GAME_SFX_PITCH_CYCLE = { 1.00f, 0.97f, 1.04f, 0.99f, 1.02f };
    /** Deterministic decoration on top of the cycle, so a long burst never repeats a pair. */
    public static final float GAME_SFX_PITCH_JITTER          = 0.015f;
    /** Both backends (Android SoundPool, desktop OpenAL) reject playback rates outside this range. */
    public static final float GAME_SFX_PITCH_MINIMUM         = 0.5f;
    public static final float GAME_SFX_PITCH_MAXIMUM         = 2.0f;
    /** Amplitude rides the same cycle at half the spread, so shots differ in loudness too. */
    public static final float GAME_SFX_AMPLITUDE_CYCLE_SCALE = 0.5f;
    /** Salt for the deterministic pitch jitter hash. */
    public static final long  GAME_SFX_PITCH_JITTER_SEED     = 0x51F3A7C2D9B14E6DL;

    // -------------------------------------------------------------------------------------
    // Distance and direction
    // -------------------------------------------------------------------------------------
    /** Distance at which a source plays at half volume (the inverse-square reference). */
    public static final float GAME_SFX_REFERENCE_TILES       = 4f;
    /** Beyond this the source is silent — roughly the mini-map's useful radius. */
    public static final float GAME_SFX_MAX_AUDIBLE_TILES     = 16f;
    /** Linear ramp over the last tiles before the cutoff, so nothing POPS to silence. */
    public static final float GAME_SFX_TAPER_TILES           = 2f;
    /** Hard panning makes a sound vanish on one earbud; this keeps both ears fed. */
    public static final float GAME_SFX_PAN_STRENGTH          = 0.8f;
    /** A source behind the player is quieter and duller — the only front/back cue available. */
    public static final float GAME_SFX_REAR_VOLUME_FACTOR    = 0.78f;
    public static final float GAME_SFX_REAR_PITCH_FACTOR     = 0.94f;

    // -------------------------------------------------------------------------------------
    // Mixing discipline — the turn-based-specific rules
    // -------------------------------------------------------------------------------------
    /**
     * Every enemy resolves in the same instant, so five swings would be five identical sounds at
     * the same millisecond.  The nearest few are kept and the rest DROPPED — never queued, because
     * a growl arriving a second after its turn is a lie about where things are.
     */
    public static final int   GAME_SFX_MAX_ENEMY_VOICES_PER_TURN = 3;
    /**
     * How long after a turn's first voice further voices still count as "the same turn".
     *
     * <p>The turn boundary is detected by TIME rather than by an explicit call from the tick bus.
     * Every sound a turn produces is fired within the same frame — enemies all resolve in one
     * instant — so a short window identifies a turn exactly as well, and cannot be silently missed
     * the way a hook on one of several dispatch sites can. A missed reset would leave the per-turn
     * counters climbing forever and silence every enemy after the third sound of the run.
     */
    public static final float GAME_SFX_TURN_WINDOW_SECONDS       = 0.20f;
    /** The Nth voice of a category within one turn plays at falloff^(N-1) of its volume. */
    public static final float GAME_SFX_STACK_VOLUME_FALLOFF      = 0.7f;
    /** Approximate ceiling on simultaneous voices; sits well under SoundPool's default of 16. */
    public static final int   GAME_SFX_MAX_CONCURRENT_VOICES     = 8;
    /** How long a started voice counts towards the pressure estimate. */
    public static final float GAME_SFX_VOICE_WINDOW_SECONDS      = 0.35f;
    /** Gameplay sound ducks briefly after a story cue, never the other way round. */
    public static final float GAME_SFX_STORY_DUCK_SECONDS        = 0.35f;
    public static final float GAME_SFX_STORY_DUCK_FACTOR         = 0.55f;

    // -------------------------------------------------------------------------------------
    // Per-sound behaviour the catalog references
    // -------------------------------------------------------------------------------------
    /** Damage at or above this fraction of max HP plays the heavy hurt sound instead of the light one. */
    public static final float GAME_SFX_HEAVY_HIT_HP_FRACTION     = 0.15f;
    /** Default minimum gap between two plays of the same sound; per-definition overrides exist. */
    public static final float GAME_SFX_DEFAULT_RETRIGGER_SECONDS = 0.05f;

    // Priorities — when voice pressure forces a drop, the LOWEST priority request loses.
    public static final int GAME_SFX_PRIORITY_PLAYER_WEAPON = 100;
    public static final int GAME_SFX_PRIORITY_PLAYER_STATE  = 90;
    public static final int GAME_SFX_PRIORITY_EXPLOSION     = 90;
    public static final int GAME_SFX_PRIORITY_ENEMY         = 60;
    public static final int GAME_SFX_PRIORITY_ENVIRONMENT   = 40;
    public static final int GAME_SFX_PRIORITY_INTERFACE     = 20;

    // -------------------------------------------------------------------------------------
    // The player-facing setting
    // -------------------------------------------------------------------------------------
    /** Volume multiplier for each {@code SfxVolumeSetting}; QUIET is for a phone in a quiet room. */
    public static final float GAME_SFX_VOLUME_FULL  = 1.0f;
    public static final float GAME_SFX_VOLUME_QUIET = 0.45f;
    public static final float GAME_SFX_VOLUME_OFF   = 0f;
    /** Preferences key for the persisted setting, sharing the story layer's store and prefix. */
    public static final String GAME_SFX_SETTING_KEY = "story.setting.sfx.volume";
}
