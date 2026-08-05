package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;
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

    private static final int    WAV_HEADER_BYTES = 44;
    private static final short  PCM_FORMAT       = 1;   // linear PCM
    private static final short  MONO_CHANNELS    = 1;
    private static final short  BITS_PER_SAMPLE  = 16;
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
        try {
            // The tone's own parameters are part of the file name, so a retuned constant writes a
            // NEW file rather than silently re-loading the old sound.
            String cacheName = fileName + "-" + Math.round(frequencyHz)
                    + "-" + Math.round(durationSeconds * 1000f) + (square ? "-square" : "-sine");
            FileHandle handle = Gdx.files.local(GENERATED_DIRECTORY + cacheName + ".wav");
            // Synthesis is deterministic, so a file written by an earlier run is already correct:
            // a new run after a death re-loads it instead of rebuilding the same bytes.
            if (!handle.exists()) {
                handle.writeBytes(buildWav(frequencyHz, durationSeconds, square), false);
            }
            return Gdx.audio.newSound(handle);
        } catch (Exception synthesisFailure) {
            // Never let a missing/locked audio device break construction — degrade to silence.
            return null;
        }
    }

    /** Assembles a minimal 16-bit mono WAV for one procedural tone. */
    private byte[] buildWav(float frequencyHz, float durationSeconds, boolean square) {
        int sampleRate   = StoryUiConstants.STORY_STING_SAMPLE_RATE_HZ;
        int totalSamples = Math.max(1, Math.round(durationSeconds * sampleRate));
        int dataBytes    = totalSamples * (BITS_PER_SAMPLE / 8);
        byte[] wav       = new byte[WAV_HEADER_BYTES + dataBytes];

        writeWavHeader(wav, sampleRate, dataBytes);

        int writeIndex = WAV_HEADER_BYTES;
        // Volume is applied at play() time; synthesise at unit volume so the WAV keeps full range.
        for (int sampleIndex = 0; sampleIndex < totalSamples; sampleIndex++) {
            short sample = GameMath.storyStingSample(
                    frequencyHz, square, 1f, sampleIndex, totalSamples, sampleRate,
                    StoryUiConstants.STORY_STING_ENVELOPE_FRACTION);
            wav[writeIndex++] = (byte) (sample & 0xFF);
            wav[writeIndex++] = (byte) ((sample >> 8) & 0xFF);
        }
        return wav;
    }

    /** Writes the 44-byte RIFF/WAVE header (little-endian) for a mono 16-bit PCM stream. */
    private void writeWavHeader(byte[] wav, int sampleRate, int dataBytes) {
        int byteRate   = sampleRate * MONO_CHANNELS * (BITS_PER_SAMPLE / 8);
        int blockAlign = MONO_CHANNELS * (BITS_PER_SAMPLE / 8);

        writeAscii(wav, 0, "RIFF");
        writeIntLittleEndian(wav, 4, 36 + dataBytes);   // chunk size = file size - 8
        writeAscii(wav, 8, "WAVE");
        writeAscii(wav, 12, "fmt ");
        writeIntLittleEndian(wav, 16, 16);              // fmt chunk size
        writeShortLittleEndian(wav, 20, PCM_FORMAT);
        writeShortLittleEndian(wav, 22, MONO_CHANNELS);
        writeIntLittleEndian(wav, 24, sampleRate);
        writeIntLittleEndian(wav, 28, byteRate);
        writeShortLittleEndian(wav, 32, (short) blockAlign);
        writeShortLittleEndian(wav, 34, BITS_PER_SAMPLE);
        writeAscii(wav, 36, "data");
        writeIntLittleEndian(wav, 40, dataBytes);
    }

    private static void writeAscii(byte[] buffer, int offset, String text) {
        for (int characterIndex = 0; characterIndex < text.length(); characterIndex++) {
            buffer[offset + characterIndex] = (byte) text.charAt(characterIndex);
        }
    }

    private static void writeIntLittleEndian(byte[] buffer, int offset, int value) {
        buffer[offset]     = (byte) (value & 0xFF);
        buffer[offset + 1] = (byte) ((value >> 8) & 0xFF);
        buffer[offset + 2] = (byte) ((value >> 16) & 0xFF);
        buffer[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    private static void writeShortLittleEndian(byte[] buffer, int offset, short value) {
        buffer[offset]     = (byte) (value & 0xFF);
        buffer[offset + 1] = (byte) ((value >> 8) & 0xFF);
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
