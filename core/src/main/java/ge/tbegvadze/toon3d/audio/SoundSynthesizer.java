package ge.tbegvadze.toon3d.audio;

import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.SoundConstants;

/**
 * Turns a {@link SoundDefinition} recipe into 16-bit PCM (procedural-sound-effects order 1).
 *
 * <p>The pipeline, per layer, per sample:
 *
 * <pre>
 *   oscillator  (WaveformKind.sampleAt — no switch; each constant has its own body)
 *     -> high-pass    (optional; one-pole, state held locally)
 *     -> low-pass     (optional; one-pole, cutoff may sweep across the sound)
 *     -> amplitude modulation (optional; "chainsaw" at 35 Hz, "electricity" at 60 Hz)
 *     -> percussive envelope  (attack ramp + normalised exponential decay, ends at exactly zero)
 *     -> layer amplitude
 *     -> accumulate into the shared buffer at the layer's delay offset
 * </pre>
 *
 * <p>Then, once for the whole sound: soft clip (a Pade tanh, so summed layers saturate rather than
 * hard-clip into harsh harmonics) and quantise to signed 16-bit.
 *
 * <p><b>Filtering happens BEFORE the envelope, deliberately.</b>  Filtering an already-enveloped
 * signal smears its attack transient, and the transient is exactly what makes a gunshot read as a
 * gunshot on a small speaker.
 *
 * <p>Allocation happens here freely: this runs once per sound at startup, never during play.
 */
public final class SoundSynthesizer {

    private final int sampleRateHz;

    public SoundSynthesizer(int sampleRateHz) {
        this.sampleRateHz = sampleRateHz;
    }

    /** Renders the whole recipe. Returns at least one sample, so a WAV is always well-formed. */
    public short[] synthesize(SoundDefinition definition) {
        int totalSamples = Math.max(1,
                Math.round(definition.getTotalDurationSeconds() * sampleRateHz));

        float[] accumulator = new float[totalSamples];
        for (int layerIndex = 0; layerIndex < definition.getLayerCount(); layerIndex++) {
            renderLayer(definition.getLayer(layerIndex), accumulator, totalSamples);
        }

        if (definition.hasRoomEcho()) {
            applyRoomEcho(definition, accumulator, totalSamples);
        }

        short[] pcm = new short[totalSamples];
        for (int sampleIndex = 0; sampleIndex < totalSamples; sampleIndex++) {
            pcm[sampleIndex] = GameMath.toPcm16(GameMath.softClipUnit(accumulator[sampleIndex]));
        }
        return pcm;
    }

    /**
     * The room (order 6): a feedback delay line with a one-pole low-pass in the loop, so every
     * repeat is quieter AND duller than the last — which is what concrete does to a gunshot.
     *
     * <pre>
     *   echo[n] = lowPass( dry[n - D] + feedback * echo[n - D] )
     *   out[n]  = dry[n] + wetMix * echo[n]
     * </pre>
     *
     * Then, because the tail is cut at the duration cap rather than decaying to exactly zero, the
     * whole buffer takes a short linear end fade so the last sample is 0 (no click).
     */
    private void applyRoomEcho(SoundDefinition definition, float[] accumulator, int totalSamples) {
        int delaySamples = Math.max(1, Math.round(definition.getEchoDelaySeconds() * sampleRateHz));
        float feedback   = Math.min(0.95f, Math.max(0f, definition.getEchoFeedback()));
        float wetMix     = definition.getEchoWetMix();
        boolean damped   = definition.getEchoDampingHz() > 0f;
        float dampingCoefficient = damped
                ? GameMath.onePoleCoefficient(definition.getEchoDampingHz(), sampleRateHz) : 0f;

        float[] echo = new float[totalSamples];
        float lowPassPrevious = 0f;
        for (int sampleIndex = delaySamples; sampleIndex < totalSamples; sampleIndex++) {
            int sourceIndex = sampleIndex - delaySamples;
            float fedBack = accumulator[sourceIndex] + feedback * echo[sourceIndex];
            if (damped) {
                lowPassPrevious = GameMath.onePoleLowPassStep(
                        lowPassPrevious, fedBack, dampingCoefficient);
                fedBack = lowPassPrevious;
            }
            echo[sampleIndex] = fedBack;
        }
        // The echo reads the DRY buffer above, so the wet mix is added in a second pass.
        int fadeSamples = Math.round(SoundConstants.GAME_SFX_END_FADE_SECONDS * sampleRateHz);
        for (int sampleIndex = 0; sampleIndex < totalSamples; sampleIndex++) {
            accumulator[sampleIndex] = (accumulator[sampleIndex] + wetMix * echo[sampleIndex])
                    * GameMath.endFadeGain(sampleIndex, totalSamples, fadeSamples);
        }
    }

    private void renderLayer(SoundLayer layer, float[] accumulator, int totalSamples) {
        int delaySamples = Math.round(layer.getDelaySeconds() * sampleRateHz);
        int layerSamples = Math.max(1, Math.round(layer.getDurationSeconds() * sampleRateHz));
        int attackSamples = Math.max(1, Math.round(
                Math.max(layer.getAttackSeconds(), SoundConstants.GAME_SFX_MINIMUM_ATTACK_SECONDS)
                        * sampleRateHz));

        // One-pole filter memory. Local to this layer, which is what keeps the GameMath steps pure.
        float lowPassPrevious        = 0f;
        float highPassPreviousOutput = 0f;
        float highPassPreviousInput  = 0f;

        boolean hasHighPass    = layer.hasHighPass();
        boolean hasLowPass     = layer.hasLowPass();
        boolean sweepsLowPass  = layer.hasSweptLowPass();
        boolean hasModulation  = layer.getAmplitudeModulationDepth() > 0f;

        float highPassCoefficient = hasHighPass
                ? GameMath.onePoleCoefficient(layer.getHighPassHz(), sampleRateHz) : 0f;
        // For a fixed low-pass the coefficient is computed once; a swept one recomputes per sample.
        float fixedLowPassCoefficient = (hasLowPass && !sweepsLowPass)
                ? GameMath.onePoleCoefficient(layer.getLowPassStartHz(), sampleRateHz) : 0f;

        for (int layerSampleIndex = 0; layerSampleIndex < layerSamples; layerSampleIndex++) {
            int targetIndex = delaySamples + layerSampleIndex;
            if (targetIndex >= totalSamples) break;

            float timeSeconds = layerSampleIndex / (float) sampleRateHz;
            float value = layer.getWaveform().sampleAt(layer, timeSeconds, layerSampleIndex);

            if (hasHighPass) {
                float filtered = GameMath.onePoleHighPassStep(
                        highPassPreviousOutput, highPassPreviousInput, value, highPassCoefficient);
                highPassPreviousInput  = value;
                highPassPreviousOutput = filtered;
                value = filtered;
            }

            if (hasLowPass) {
                float coefficient = fixedLowPassCoefficient;
                if (sweepsLowPass) {
                    float sweepProgress = layerSampleIndex / (float) layerSamples;
                    float cutoffHz = layer.getLowPassStartHz()
                            + (layer.getLowPassEndHz() - layer.getLowPassStartHz()) * sweepProgress;
                    coefficient = GameMath.onePoleCoefficient(cutoffHz, sampleRateHz);
                }
                lowPassPrevious = GameMath.onePoleLowPassStep(lowPassPrevious, value, coefficient);
                value = lowPassPrevious;
            }

            if (hasModulation) {
                value *= GameMath.amplitudeModulation(layer.getAmplitudeModulationHz(),
                        timeSeconds, layer.getAmplitudeModulationDepth());
            }

            value *= GameMath.percussiveEnvelope(
                    layerSampleIndex, layerSamples, attackSamples, layer.getDecayRate());

            accumulator[targetIndex] += value * layer.getAmplitude();
        }
    }
}
