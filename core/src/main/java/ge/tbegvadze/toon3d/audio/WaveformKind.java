package ge.tbegvadze.toon3d.audio;

import com.badlogic.gdx.math.MathUtils;
import ge.tbegvadze.toon3d.util.GameMath;

/**
 * The oscillator behind a {@link SoundLayer} (procedural-sound-effects order 1).
 *
 * <p>Each constant implements {@link #sampleAt} with its own body — the same idiom {@code EnemyType}
 * uses — so the synthesiser contains NO switch on waveform and adding an oscillator is one enum
 * constant rather than an edit to a dispatch block.
 *
 * <p>This produces the RAW oscillator value only.  Filtering, the envelope, the delay and the
 * amplitude are applied by {@code SoundSynthesizer}, because those stages are shared by every
 * waveform and two of them carry state.
 *
 * <p>{@code MathUtils} is imported for its fast trig tables; this is the one class in the headless
 * half that touches LibGDX, and only for pure math that {@code sim/} can call freely.
 */
public enum WaveformKind {

    /**
     * Broadband hash noise — the body of every gunshot, impact and explosion.  Deterministic, so
     * the WAV cache stays valid: the same recipe yields byte-identical PCM forever.
     */
    NOISE {
        @Override
        public float sampleAt(SoundLayer layer, float timeSeconds, int sampleIndex) {
            return GameMath.whiteNoiseSample(layer.getNoiseSeed(), sampleIndex);
        }
    },

    /** Pure tone — energy weapons, the heal chime, sub-bass punch. */
    SINE {
        @Override
        public float sampleAt(SoundLayer layer, float timeSeconds, int sampleIndex) {
            return MathUtils.sin(MathUtils.PI2 * layer.getStartHz() * timeSeconds);
        }
    },

    /** Hard-edged tone — grit, machinery, the guarded clang's harmonics. */
    SQUARE {
        @Override
        public float sampleAt(SoundLayer layer, float timeSeconds, int sampleIndex) {
            return MathUtils.sin(MathUtils.PI2 * layer.getStartHz() * timeSeconds) >= 0f ? 1f : -1f;
        }
    },

    /**
     * Linearly swept tone.  Reads as MECHANICAL, which is why every ballistic weapon's punch layer
     * uses this rather than the geometric sweep below.
     */
    CHIRP_SINE {
        @Override
        public float sampleAt(SoundLayer layer, float timeSeconds, int sampleIndex) {
            return MathUtils.sin(GameMath.linearChirpPhase(
                    layer.getStartHz(), layer.getEndHz(), timeSeconds, layer.getDurationSeconds()));
        }
    },

    /** The same sweep, hard-edged — the plasma rifle's harmonic body. */
    CHIRP_SQUARE {
        @Override
        public float sampleAt(SoundLayer layer, float timeSeconds, int sampleIndex) {
            float phase = GameMath.linearChirpPhase(
                    layer.getStartHz(), layer.getEndHz(), timeSeconds, layer.getDurationSeconds());
            return MathUtils.sin(phase) >= 0f ? 1f : -1f;
        }
    },

    /**
     * Geometrically swept tone — the sweep the ear hears as EVENLY rising, because pitch perception
     * is logarithmic.  Reserved for the railgun charge and the player's death, where the listener
     * expects a musical movement rather than a machine.
     */
    EXPONENTIAL_CHIRP_SINE {
        @Override
        public float sampleAt(SoundLayer layer, float timeSeconds, int sampleIndex) {
            return MathUtils.sin(GameMath.exponentialChirpPhase(
                    layer.getStartHz(), layer.getEndHz(), timeSeconds, layer.getDurationSeconds()));
        }
    };

    /**
     * One raw oscillator sample in [-1, 1].
     *
     * @param layer        the recipe this oscillator belongs to
     * @param timeSeconds  seconds since THIS layer started (after its delay), never negative
     * @param sampleIndex  absolute sample counter, used only by {@link #NOISE} for its hash
     */
    public abstract float sampleAt(SoundLayer layer, float timeSeconds, int sampleIndex);
}
