package ge.tbegvadze.toon3d.audio;

import ge.tbegvadze.toon3d.util.SoundConstants;

/**
 * One immutable voice inside a sound recipe (procedural-sound-effects order 1).
 *
 * <p>A gunshot is never one waveform.  A shotgun is a low-passed noise BODY, a high-passed noise
 * CRACK and a descending sine PUNCH, summed — so a {@link SoundDefinition} holds an array of these
 * and {@code SoundSynthesizer} adds them together before the soft clipper.
 *
 * <p>Every field is a plain number so the whole recipe is data: adding a sound never means writing
 * synthesis code, only describing layers.  Headless — no LibGDX import.
 *
 * <p>The filters are described here but their STATE is not: a one-pole filter needs memory between
 * samples, and that memory belongs to the synthesiser's local loop, which is what keeps both this
 * class immutable and the {@code GameMath} filter steps pure.
 */
public final class SoundLayer {

    private final WaveformKind waveform;
    private final float startHz;
    private final float endHz;
    private final float durationSeconds;
    private final float delaySeconds;
    private final float amplitude;
    private final float attackSeconds;
    private final float decayRate;
    private final float lowPassStartHz;
    private final float lowPassEndHz;
    private final float highPassHz;
    private final float amplitudeModulationHz;
    private final float amplitudeModulationDepth;
    private final long  noiseSeed;

    private SoundLayer(Builder builder) {
        this.waveform                 = builder.waveform;
        this.startHz                  = builder.startHz;
        this.endHz                    = builder.endHz;
        this.durationSeconds          = builder.durationSeconds;
        this.delaySeconds             = builder.delaySeconds;
        this.amplitude                = builder.amplitude;
        this.attackSeconds            = builder.attackSeconds;
        this.decayRate                = builder.decayRate;
        this.lowPassStartHz           = builder.lowPassStartHz;
        this.lowPassEndHz             = builder.lowPassEndHz;
        this.highPassHz               = builder.highPassHz;
        this.amplitudeModulationHz    = builder.amplitudeModulationHz;
        this.amplitudeModulationDepth = builder.amplitudeModulationDepth;
        this.noiseSeed                = builder.noiseSeed;
    }

    public WaveformKind getWaveform()              { return waveform; }
    public float getStartHz()                      { return startHz; }
    public float getEndHz()                        { return endHz; }
    public float getDurationSeconds()              { return durationSeconds; }
    public float getDelaySeconds()                 { return delaySeconds; }
    public float getAmplitude()                    { return amplitude; }
    public float getAttackSeconds()                { return attackSeconds; }
    public float getDecayRate()                    { return decayRate; }
    public float getLowPassStartHz()               { return lowPassStartHz; }
    public float getLowPassEndHz()                 { return lowPassEndHz; }
    public float getHighPassHz()                   { return highPassHz; }
    public float getAmplitudeModulationHz()        { return amplitudeModulationHz; }
    public float getAmplitudeModulationDepth()     { return amplitudeModulationDepth; }
    public long  getNoiseSeed()                    { return noiseSeed; }

    public boolean hasLowPass()  { return lowPassStartHz > 0f; }
    public boolean hasHighPass() { return highPassHz > 0f; }
    /** True when the low-pass cutoff moves across the sound (the flamethrower, the death wash). */
    public boolean hasSweptLowPass() {
        return hasLowPass() && lowPassEndHz > 0f && lowPassEndHz != lowPassStartHz;
    }

    /** Total time this layer occupies, including the delay before it starts. */
    public float getEndTimeSeconds() {
        return delaySeconds + durationSeconds;
    }

    /**
     * The recipe as a short string, folded into the cached WAV's filename so a RETUNED layer
     * writes a new file instead of silently re-loading the previous one.
     */
    public String describeForCacheKey() {
        return waveform.name()
                + '_' + Math.round(startHz)
                + '_' + Math.round(endHz)
                + '_' + Math.round(durationSeconds * 1000f)
                + '_' + Math.round(delaySeconds * 1000f)
                + '_' + Math.round(amplitude * 100f)
                + '_' + Math.round(attackSeconds * 10000f)
                + '_' + Math.round(decayRate * 10f)
                + '_' + Math.round(lowPassStartHz)
                + '_' + Math.round(lowPassEndHz)
                + '_' + Math.round(highPassHz)
                + '_' + Math.round(amplitudeModulationHz)
                + '_' + Math.round(amplitudeModulationDepth * 100f)
                + '_' + Long.toHexString(noiseSeed);
    }

    public static Builder builder(WaveformKind waveform, float durationSeconds) {
        return new Builder(waveform, durationSeconds);
    }

    /** Fluent recipe builder — house style, cf. {@code narrative/CodexEntry.Builder}. */
    public static final class Builder {

        private final WaveformKind waveform;
        private final float durationSeconds;

        private float startHz                  = 0f;
        private float endHz                    = 0f;
        private float delaySeconds             = 0f;
        private float amplitude                = 1f;
        private float attackSeconds            = SoundConstants.GAME_SFX_MINIMUM_ATTACK_SECONDS;
        private float decayRate                = 6f;
        private float lowPassStartHz           = 0f;
        private float lowPassEndHz             = 0f;
        private float highPassHz               = 0f;
        private float amplitudeModulationHz    = 0f;
        private float amplitudeModulationDepth = 0f;
        private long  noiseSeed                = 1L;

        private Builder(WaveformKind waveform, float durationSeconds) {
            this.waveform        = waveform;
            this.durationSeconds = durationSeconds;
        }

        /** Constant-frequency oscillator. */
        public Builder frequency(float hertz) {
            this.startHz = hertz;
            this.endHz   = hertz;
            return this;
        }

        /** Swept oscillator; the chirp waveforms integrate this properly rather than scaling time. */
        public Builder sweep(float fromHertz, float toHertz) {
            this.startHz = fromHertz;
            this.endHz   = toHertz;
            return this;
        }

        /** Starts this layer late, which is how a double barrel gets its second shot. */
        public Builder delay(float seconds) {
            this.delaySeconds = seconds;
            return this;
        }

        public Builder amplitude(float value) {
            this.amplitude = value;
            return this;
        }

        /** Attack ramp and exponential decay shape: 2 a soft swell, 6 a gunshot, 12 a click. */
        public Builder envelope(float attackSeconds, float decayRate) {
            this.attackSeconds = Math.max(SoundConstants.GAME_SFX_MINIMUM_ATTACK_SECONDS,
                                          attackSeconds);
            this.decayRate     = decayRate;
            return this;
        }

        public Builder lowPass(float cutoffHertz) {
            this.lowPassStartHz = cutoffHertz;
            this.lowPassEndHz   = cutoffHertz;
            return this;
        }

        /** A cutoff that travels across the sound — flame opening up, a death wash closing down. */
        public Builder sweptLowPass(float fromHertz, float toHertz) {
            this.lowPassStartHz = fromHertz;
            this.lowPassEndHz   = toHertz;
            return this;
        }

        public Builder highPass(float cutoffHertz) {
            this.highPassHz = cutoffHertz;
            return this;
        }

        /** Band-pass is a high-pass followed by a low-pass; this sets both in one call. */
        public Builder bandPass(float lowerHertz, float upperHertz) {
            this.highPassHz     = lowerHertz;
            this.lowPassStartHz = upperHertz;
            this.lowPassEndHz   = upperHertz;
            return this;
        }

        /** Depth 1 zeroes the signal between chops — "chainsaw" at 35 Hz, "electricity" at 60 Hz. */
        public Builder amplitudeModulation(float modulatorHertz, float depth) {
            this.amplitudeModulationHz    = modulatorHertz;
            this.amplitudeModulationDepth = depth;
            return this;
        }

        /** Distinguishes two otherwise identical noise layers so they do not phase-cancel. */
        public Builder noiseSeed(long seed) {
            this.noiseSeed = seed;
            return this;
        }

        public SoundLayer build() {
            return new SoundLayer(this);
        }
    }
}
