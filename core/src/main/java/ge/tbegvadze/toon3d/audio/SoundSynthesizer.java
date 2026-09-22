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

        short[] pcm = new short[totalSamples];
        for (int sampleIndex = 0; sampleIndex < totalSamples; sampleIndex++) {
            pcm[sampleIndex] = GameMath.toPcm16(GameMath.softClipUnit(accumulator[sampleIndex]));
        }
        return pcm;
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
