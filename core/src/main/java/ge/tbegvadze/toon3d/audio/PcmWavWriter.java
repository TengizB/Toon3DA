package ge.tbegvadze.toon3d.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;

/**
 * The one place in the codebase that turns PCM samples into a playable {@link Sound}
 * (procedural-sound-effects order 1).
 *
 * <p>{@code Gdx.audio.newSound} needs a FILE, and neither backend can load raw PCM, so every
 * procedurally generated sound in this game is wrapped in a minimal 44-byte RIFF/WAVE header,
 * written once to local storage and loaded back.  This logic was originally inline in
 * {@code render/StoryAudio}; it was extracted here when the gameplay layer needed the same thing,
 * so there is ONE wav writer in the codebase rather than two that can drift apart.
 *
 * <p><b>Caching.</b> Synthesis is deterministic, so a file written by an earlier run is already
 * correct and a fresh run after a death re-loads it instead of rebuilding identical bytes.  That
 * only holds while the filename encodes the RECIPE — callers are responsible for passing a
 * cacheName that changes whenever the sound does (see {@code SoundDefinition.buildCacheKey}).
 *
 * <p><b>Degrades to silence.</b> Every method returns null rather than throwing when the audio
 * backend or the filesystem is unavailable (headless test runs, a device with no audio, storage
 * permission refused). A game with no sound is playable; a game that crashes on startup is not.
 */
public final class PcmWavWriter {

    private static final int   WAV_HEADER_BYTES = 44;
    private static final short PCM_FORMAT       = 1;   // linear PCM
    private static final short MONO_CHANNELS    = 1;
    private static final short BITS_PER_SAMPLE  = 16;

    private PcmWavWriter() {}

    /**
     * Writes the samples as a cached WAV if absent, then loads it.
     *
     * @return the loaded sound, or null when audio or storage is unavailable
     */
    public static Sound toCachedSound(String directory, String cacheName, short[] pcmSamples,
                                      int sampleRateHz) {
        if (Gdx.audio == null || Gdx.files == null) return null;
        try {
            String filePath = directory.endsWith("/") ? directory + cacheName + ".wav"
                                                      : directory + "/" + cacheName + ".wav";
            FileHandle handle = Gdx.files.local(filePath);
            if (!handle.exists()) {
                handle.writeBytes(buildWav(pcmSamples, sampleRateHz), false);
            }
            return Gdx.audio.newSound(handle);
        } catch (Exception synthesisFailure) {
            // Never let a missing or locked audio device break construction — degrade to silence.
            return null;
        }
    }

    /** Assembles a complete 16-bit mono WAV file from the given samples. */
    public static byte[] buildWav(short[] pcmSamples, int sampleRateHz) {
        if (sampleRateHz <= 0) {
            throw new IllegalArgumentException("sampleRateHz must be positive, got " + sampleRateHz);
        }
        long dataBytes = (long) pcmSamples.length * (BITS_PER_SAMPLE / 8);
        if (dataBytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                "pcmSamples array too large: " + pcmSamples.length + " samples exceed maximum");
        }
        byte[] wav = new byte[WAV_HEADER_BYTES + (int) dataBytes];

        writeWavHeader(wav, sampleRateHz, (int) dataBytes);

        int writeIndex = WAV_HEADER_BYTES;
        for (short sample : pcmSamples) {
            wav[writeIndex++] = (byte) (sample & 0xFF);
            wav[writeIndex++] = (byte) ((sample >> 8) & 0xFF);
        }
        return wav;
    }

    /** Writes the 44-byte RIFF/WAVE header (little-endian) for a mono 16-bit PCM stream. */
    private static void writeWavHeader(byte[] wav, int sampleRateHz, int dataBytes) {
        int byteRate   = sampleRateHz * MONO_CHANNELS * (BITS_PER_SAMPLE / 8);
        int blockAlign = MONO_CHANNELS * (BITS_PER_SAMPLE / 8);

        writeAscii(wav, 0, "RIFF");
        writeIntLittleEndian(wav, 4, 36 + dataBytes);   // chunk size = file size - 8
        writeAscii(wav, 8, "WAVE");
        writeAscii(wav, 12, "fmt ");
        writeIntLittleEndian(wav, 16, 16);              // fmt chunk size
        writeShortLittleEndian(wav, 20, PCM_FORMAT);
        writeShortLittleEndian(wav, 22, MONO_CHANNELS);
        writeIntLittleEndian(wav, 24, sampleRateHz);
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
}
