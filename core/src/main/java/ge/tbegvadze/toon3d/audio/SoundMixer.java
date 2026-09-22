package ge.tbegvadze.toon3d.audio;

import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.SoundConstants;

/**
 * The rules that stop many simultaneous sounds turning to mush
 * (procedural-sound-effects orders 1 and 2).
 *
 * <p>Turn-based combat has a failure mode real-time games do not: EVERY enemy resolves in the same
 * instant.  Five enemies swinging is five identical sounds at the same millisecond, which is not
 * five attacks — it is one loud smear.  Four rules address that:
 *
 * <ol>
 *   <li><b>Per-turn category cap.</b> The {@link SoundCategory#ENEMY} bus plays at most
 *       {@code GAME_SFX_MAX_ENEMY_VOICES_PER_TURN} voices per turn and DROPS the rest — never
 *       queues them, because a growl arriving a second after its turn is a lie about where things
 *       are.</li>
 *   <li><b>Stack falloff.</b> The Nth voice of a category within one turn is damped
 *       geometrically, so three swings read as three events instead of noise.</li>
 *   <li><b>Minimum re-trigger interval,</b> per sound, from its definition.</li>
 *   <li><b>Global voice pressure.</b> An approximate cap, since the backend's live voice count
 *       cannot be queried; when exceeded, the LOWEST-priority request loses.</li>
 * </ol>
 *
 * <p>Pure state plus a clock — no LibGDX, no {@code Sound}, no I/O.  It decides and reports; the
 * facade plays.  The clock is advanced from the update path, never from {@code render()}.
 */
public final class SoundMixer {

    /** Sentinel for "this sound has never played", far enough back to never gate the first play. */
    private static final float NEVER_PLAYED = -1000f;

    private final float[] lastPlayTimeBySound   = new float[GameSoundId.COUNT];
    private final int[]   playCountBySound      = new int[GameSoundId.COUNT];
    private final int[]   voicesThisTurnByCategory = new int[SoundCategory.COUNT];

    /** Start times of recently begun voices, used for the decaying pressure estimate. */
    private final float[] recentVoiceStartTimes =
            new float[SoundConstants.GAME_SFX_MAX_CONCURRENT_VOICES * 4];
    private int recentVoiceWriteIndex = 0;

    private float elapsedSeconds      = 0f;
    private float lastStoryCueTime    = NEVER_PLAYED;
    /** When the current turn's first voice started, used to detect the turn boundary by time. */
    private float currentTurnStartTime = NEVER_PLAYED;

    public SoundMixer() {
        for (int soundIndex = 0; soundIndex < lastPlayTimeBySound.length; soundIndex++) {
            lastPlayTimeBySound[soundIndex] = NEVER_PLAYED;
        }
        for (int voiceIndex = 0; voiceIndex < recentVoiceStartTimes.length; voiceIndex++) {
            recentVoiceStartTimes[voiceIndex] = NEVER_PLAYED;
        }
    }

    /** Advances the mixer clock. Fed from {@code World.update}, never from a render call. */
    public void update(float deltaTime) {
        elapsedSeconds += deltaTime;
    }

    /**
     * Resets the per-turn voice counters.
     *
     * <p>Correctness does NOT depend on this being called: {@code admit} expires a stale turn by
     * time on its own. It exists so an explicit boundary — a floor change, a run restart — can end
     * a turn immediately rather than waiting out the window.
     */
    public void beginTurn() {
        for (int categoryIndex = 0; categoryIndex < voicesThisTurnByCategory.length; categoryIndex++) {
            voicesThisTurnByCategory[categoryIndex] = 0;
        }
        currentTurnStartTime = NEVER_PLAYED;
    }

    /** Stamps the moment a story cue played, so gameplay sound ducks briefly under it. */
    public void noteStoryCuePlayed() {
        lastStoryCueTime = elapsedSeconds;
    }

    /**
     * Clears transient timing state after the application was suspended.
     *
     * <p>The clock does not advance while the app is backgrounded, but wall time does, so without
     * this every re-trigger window would still be "recently played" on resume and the first sounds
     * after returning would be silently dropped.
     */
    public void resetTransientState() {
        for (int soundIndex = 0; soundIndex < lastPlayTimeBySound.length; soundIndex++) {
            lastPlayTimeBySound[soundIndex] = NEVER_PLAYED;
        }
        for (int voiceIndex = 0; voiceIndex < recentVoiceStartTimes.length; voiceIndex++) {
            recentVoiceStartTimes[voiceIndex] = NEVER_PLAYED;
        }
        lastStoryCueTime = NEVER_PLAYED;
        beginTurn();
    }

    /**
     * Decides whether a request may play, and at what volume.
     *
     * @param requestedVolume the volume after distance, rear and setting scaling
     * @return the final volume to play at, or 0 when the request is dropped
     */
    public float admit(SoundDefinition definition, float requestedVolume) {
        if (requestedVolume <= 0f) return 0f;

        expireTurnIfElapsed();

        int soundIndex = definition.getId().ordinal();

        // Rule 3 — minimum re-trigger interval.
        if (elapsedSeconds - lastPlayTimeBySound[soundIndex]
                < definition.getMinimumRetriggerSeconds()) {
            return 0f;
        }

        int categoryIndex  = definition.getCategory().ordinal();
        int voicesThisTurn = voicesThisTurnByCategory[categoryIndex];

        // Rule 1 — per-turn cap, currently only meaningful for the ENEMY bus.
        if (definition.getCategory() == SoundCategory.ENEMY
                && voicesThisTurn >= SoundConstants.GAME_SFX_MAX_ENEMY_VOICES_PER_TURN) {
            return 0f;
        }

        // Rule 4 — global voice pressure; the lowest-priority request loses when crowded.
        if (countRecentVoices() >= SoundConstants.GAME_SFX_MAX_CONCURRENT_VOICES
                && definition.getPriority() < SoundConstants.GAME_SFX_PRIORITY_PLAYER_STATE) {
            return 0f;
        }

        // Rule 2 — stack falloff within the turn.
        float admittedVolume = GameMath.sfxStackedVolume(
                requestedVolume, voicesThisTurn, SoundConstants.GAME_SFX_STACK_VOLUME_FALLOFF);

        // Rule 5 — one-way duck under a story cue.
        if (elapsedSeconds - lastStoryCueTime < SoundConstants.GAME_SFX_STORY_DUCK_SECONDS) {
            admittedVolume *= SoundConstants.GAME_SFX_STORY_DUCK_FACTOR;
        }

        if (admittedVolume <= 0f) return 0f;

        lastPlayTimeBySound[soundIndex] = elapsedSeconds;
        voicesThisTurnByCategory[categoryIndex] = voicesThisTurn + 1;
        if (currentTurnStartTime <= NEVER_PLAYED) currentTurnStartTime = elapsedSeconds;
        recordVoiceStart();
        return admittedVolume > 1f ? 1f : admittedVolume;
    }

    /**
     * Ends the current turn's voice accounting once its window has passed.
     *
     * <p>Every sound a turn produces is fired within the same frame — the player acts, then every
     * enemy resolves in one instant — so a short elapsed window identifies a turn boundary exactly
     * as well as an explicit call from the tick bus would, without depending on one being made at
     * every dispatch site. That matters because a MISSED reset is not a small bug: the per-turn
     * counters would climb forever and every enemy sound after the third would be dropped for the
     * rest of the run.
     */
    private void expireTurnIfElapsed() {
        if (currentTurnStartTime <= NEVER_PLAYED) return;
        if (elapsedSeconds - currentTurnStartTime
                >= SoundConstants.GAME_SFX_TURN_WINDOW_SECONDS) {
            beginTurn();
        }
    }

    /**
     * The playback rate for this sound's next play, advancing its variation cycle.
     *
     * <p>Call exactly once per admitted play: it has the side effect of incrementing the play
     * counter, which is what makes consecutive shots differ.
     */
    public float nextPitch(SoundDefinition definition) {
        int soundIndex = definition.getId().ordinal();
        int playCount  = playCountBySound[soundIndex];
        // Wrap well before overflow; the cycle is modular anyway, so the wrap is inaudible.
        playCountBySound[soundIndex] = (playCount + 1) & 0x3FFFFFFF;
        return GameMath.sfxPitchFromCycle(
                playCount,
                definition.getCycleSpread(),
                SoundConstants.GAME_SFX_PITCH_JITTER_SEED + soundIndex,
                SoundConstants.GAME_SFX_PITCH_CYCLE,
                SoundConstants.GAME_SFX_PITCH_JITTER,
                SoundConstants.GAME_SFX_PITCH_MINIMUM,
                SoundConstants.GAME_SFX_PITCH_MAXIMUM);
    }

    private void recordVoiceStart() {
        recentVoiceStartTimes[recentVoiceWriteIndex] = elapsedSeconds;
        recentVoiceWriteIndex = (recentVoiceWriteIndex + 1) % recentVoiceStartTimes.length;
    }

    /** Voices started within the pressure window; an approximation, and deliberately so. */
    private int countRecentVoices() {
        int   count       = 0;
        float windowStart = elapsedSeconds - SoundConstants.GAME_SFX_VOICE_WINDOW_SECONDS;
        for (float startTime : recentVoiceStartTimes) {
            if (startTime >= windowStart) count++;
        }
        return count;
    }
}
