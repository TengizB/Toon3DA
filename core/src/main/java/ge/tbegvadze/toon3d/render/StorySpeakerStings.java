package ge.tbegvadze.toon3d.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.narrative.Speaker;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.StoryUiConstants;

/**
 * Story UI order-1 — the four speaker "stings": one short, non-verbal tone per speaker that
 * plays when a line appears, pre-attentively signalling WHO is talking (Story UI order-7's
 * audio-cue rule, honoured from order-1).  The game is fully understandable with audio OFF —
 * these only reinforce the name chip + icon + colour, never replace them.
 *
 * The tones are generated procedurally (no asset files): each speaker's PCM is synthesised
 * from {@link GameMath#storyStingSample} using the per-speaker frequency / waveform / volume
 * in {@link StoryUiConstants}, wrapped in a minimal 16-bit mono WAV and loaded through
 * {@code Gdx.audio.newSound}.  All {@link Sound}s are owned here and {@link #dispose()}d.
 *
 * Robust by design: if the audio backend is unavailable (headless, no device), synthesis is
 * skipped and {@link #play(Speaker)} is a no-op — the story UI still works without sound.
 */
public final class StorySpeakerStings implements Disposable {

    private static final int   WAV_HEADER_BYTES = 44;
    private static final short PCM_FORMAT        = 1;   // linear PCM
    private static final short MONO_CHANNELS     = 1;
    private static final short BITS_PER_SAMPLE   = 16;

    private final Sound[] stings = new Sound[StoryUiConstants.SPEAKER_COUNT];

    public StorySpeakerStings() {
        if (Gdx.audio == null || Gdx.files == null) return;   // headless / no audio → no stings
        for (int speakerIndex = 0; speakerIndex < stings.length; speakerIndex++) {
            stings[speakerIndex] = synthesizeSting(speakerIndex);
        }
    }

    /** Plays the sting for this speaker at its configured volume; silently no-ops if unavailable. */
    public void play(Speaker speaker) {
        if (speaker == null) return;
        int speakerIndex = speaker.ordinal();
        if (speakerIndex < 0 || speakerIndex >= stings.length) return;
        Sound sound = stings[speakerIndex];
        if (sound != null) sound.play(StoryUiConstants.STORY_STING_VOLUME[speakerIndex]);
    }

    /** Builds one speaker's WAV, writes it to a local file and loads it as a Sound; null on failure. */
    private Sound synthesizeSting(int speakerIndex) {
        try {
            byte[] wav = buildWav(speakerIndex);
            // newSound needs a FileHandle — write the generated WAV to a local temp file once.
            FileHandle handle = Gdx.files.local("story-stings/sting-" + speakerIndex + ".wav");
            handle.writeBytes(wav, false);
            return Gdx.audio.newSound(handle);
        } catch (Exception synthFailure) {
            // Never let a missing/locked audio device break construction — degrade to silence.
            return null;
        }
    }

    /** Assembles a minimal 16-bit mono WAV for the speaker's procedural tone. */
    private byte[] buildWav(int speakerIndex) {
        int   sampleRate   = StoryUiConstants.STORY_STING_SAMPLE_RATE_HZ;
        float frequency    = StoryUiConstants.STORY_STING_FREQUENCY_HZ[speakerIndex];
        float duration     = StoryUiConstants.STORY_STING_DURATION_SEC[speakerIndex];
        boolean square     = StoryUiConstants.STORY_STING_IS_SQUARE[speakerIndex];
        float envelope     = StoryUiConstants.STORY_STING_ENVELOPE_FRACTION;

        int totalSamples = Math.max(1, Math.round(duration * sampleRate));
        int dataBytes    = totalSamples * (BITS_PER_SAMPLE / 8);
        byte[] wav       = new byte[WAV_HEADER_BYTES + dataBytes];

        writeWavHeader(wav, sampleRate, dataBytes);

        int writeIndex = WAV_HEADER_BYTES;
        // Volume is applied at play() time; synthesise at unit volume so the WAV keeps full range.
        for (int sampleIndex = 0; sampleIndex < totalSamples; sampleIndex++) {
            short sample = GameMath.storyStingSample(
                    frequency, square, 1f, sampleIndex, totalSamples, sampleRate, envelope);
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
        for (int speakerIndex = 0; speakerIndex < stings.length; speakerIndex++) {
            if (stings[speakerIndex] != null) {
                stings[speakerIndex].dispose();
                stings[speakerIndex] = null;
            }
        }
    }
}
