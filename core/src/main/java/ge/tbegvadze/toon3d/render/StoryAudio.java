package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.audio.PcmWavWriter;
import ge.tbegvadze.toon3d.narrative.Speaker;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * ALL of the story layer's sound, in one place (Story UI order-1 speaker stings, completed by
 * order-7 Part D).  There are no voice actors; there are two tiny families of non-verbal tones:
 *
 * <ul>
 *   <li>a per-{@link Speaker} STING, played the moment a line appears, so the player knows who is
 *       talking before reading a word — ORA a soft friendly blip, the Planet a low organic tone,
 *       the Organization a cold square-wave beep, SYSTEM a neutral click;</li>
 *   <li>a small set of interface CUES ({@link StoryCue}) — a panel opening, an answer committed,
 *       a status line printing.</li>
 * </ul>
 *
 * <p><b>The rule that governs the whole class: the game must be fully understandable with audio
 * off.</b>  Every sound here is redundant with something already on screen (the speaker chip carries
 * name + icon + colour; the panel is visibly there; the plate visibly presses), so
 * {@link #setEnabled(boolean)} — the codex's SOUND setting — can silence the layer with nothing
 * lost.  Nothing in the story UI is ever conveyed by sound alone.
 *
 * <p><b>One owner, one dispose.</b>  {@link Sound} is {@link Disposable} and these are synthesised,
 * so the world owns exactly one instance and hands it to every renderer that needs it
 * ({@code setStoryAudio}).  Renderers never construct their own — four channels each building their
 * own copies of the same seven sounds is four times the device voices and four times the file
 * writes, for identical audio.
 *
 * <p>Tones are generated procedurally, with no asset files: each one's PCM comes from
 * {@link GameMath#storyStingSample} using the frequency / waveform / volume in
 * {@link StoryUiConstants}, wrapped in a minimal 16-bit mono WAV and loaded through
 * {@code Gdx.audio.newSound}.  Robust by design: if the audio backend is unavailable (headless,
 * no device), synthesis is skipped and every play method is a no-op.
 */
public final class StoryAudio implements Disposable {

    /** Where the generated WAVs are written once, so {@code Gdx.audio.newSound} has a file to read. */
    private static final String GENERATED_DIRECTORY = "story-audio/";

    private final Sound[] speakerStings = new Sound[StoryUiConstants.SPEAKER_COUNT];
    private final Sound[] interfaceCues = new Sound[StoryUiConstants.STORY_CUE_COUNT];
    /** The codex's SOUND setting.  Defaults on; silences every play call when off. */
    private boolean enabled = true;

    public StoryAudio() {
        if (Gdx.audio == null || Gdx.files == null) return;   // headless / no audio → silence
        for (int speakerIndex = 0; speakerIndex < speakerStings.length; speakerIndex++) {
            speakerStings[speakerIndex] = synthesize(
                    "sting-" + speakerIndex,
                    StoryUiConstants.STORY_STING_FREQUENCY_HZ[speakerIndex],
                    StoryUiConstants.STORY_STING_DURATION_SEC[speakerIndex],
                    StoryUiConstants.STORY_STING_IS_SQUARE[speakerIndex]);
        }
        for (int cueIndex = 0; cueIndex < interfaceCues.length; cueIndex++) {
            interfaceCues[cueIndex] = synthesize(
                    "cue-" + cueIndex,
                    StoryUiConstants.STORY_CUE_FREQUENCY_HZ[cueIndex],
                    StoryUiConstants.STORY_CUE_DURATION_SEC[cueIndex],
                    StoryUiConstants.STORY_CUE_IS_SQUARE[cueIndex]);
        }
    }

    /** Turns the whole story-sound layer on or off (the codex's SOUND setting, order-7 Part D). */
    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Plays the sting for this speaker at its configured volume; no-ops when off or unavailable. */
    public void playSpeakerSting(Speaker speaker) {
        if (!enabled || speaker == null) return;
        int speakerIndex = speaker.ordinal();
        if (speakerIndex < 0 || speakerIndex >= speakerStings.length) return;
        Sound sound = speakerStings[speakerIndex];
        if (sound != null) sound.play(StoryUiConstants.STORY_STING_VOLUME[speakerIndex]);
    }

    /** Plays one interface cue at its configured volume; no-ops when off or unavailable. */
    public void playCue(StoryCue cue) {
        if (!enabled || cue == null) return;
        int cueIndex = cue.ordinal();
        if (cueIndex < 0 || cueIndex >= interfaceCues.length) return;
        Sound sound = interfaceCues[cueIndex];
        if (sound != null) sound.play(StoryUiConstants.STORY_CUE_VOLUME[cueIndex]);
    }

    // -------------------------------------------------------------------------
    // Synthesis
    // -------------------------------------------------------------------------

    /** Builds one tone's WAV, writes it to a local file and loads it as a Sound; null on failure. */
    private Sound synthesize(String fileName, float frequencyHz, float durationSeconds,
                             boolean square) {
        // The tone's own parameters are part of the file name, so a retuned constant writes a
        // NEW file rather than silently re-loading the old sound.
        String cacheName = fileName + "-" + Math.round(frequencyHz)
                + "-" + Math.round(durationSeconds * 1000f) + (square ? "-square" : "-sine");
        return PcmWavWriter.toCachedSound(
                GENERATED_DIRECTORY,
                cacheName,
                buildTone(frequencyHz, durationSeconds, square),
                StoryUiConstants.STORY_STING_SAMPLE_RATE_HZ);
    }

    /** Renders one procedural tone's PCM; the WAV wrapping and caching belong to PcmWavWriter. */
    private short[] buildTone(float frequencyHz, float durationSeconds, boolean square) {
        int sampleRate   = StoryUiConstants.STORY_STING_SAMPLE_RATE_HZ;
        int totalSamples = Math.max(1, Math.round(durationSeconds * sampleRate));
        short[] samples  = new short[totalSamples];
        // Volume is applied at play() time; synthesise at unit volume so the WAV keeps full range.
        for (int sampleIndex = 0; sampleIndex < totalSamples; sampleIndex++) {
            samples[sampleIndex] = GameMath.storyStingSample(
                    frequencyHz, square, 1f, sampleIndex, totalSamples, sampleRate,
                    StoryUiConstants.STORY_STING_ENVELOPE_FRACTION);
        }
        return samples;
    }

    @Override
    public void dispose() {
        for (int speakerIndex = 0; speakerIndex < speakerStings.length; speakerIndex++) {
            if (speakerStings[speakerIndex] != null) {
                speakerStings[speakerIndex].dispose();
                speakerStings[speakerIndex] = null;
            }
        }
        for (int cueIndex = 0; cueIndex < interfaceCues.length; cueIndex++) {
            if (interfaceCues[cueIndex] != null) {
                interfaceCues[cueIndex].dispose();
                interfaceCues[cueIndex] = null;
            }
        }
    }
}
