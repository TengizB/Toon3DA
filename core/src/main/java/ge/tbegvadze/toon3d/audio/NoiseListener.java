package ge.tbegvadze.toon3d.audio;

/**
 * The seam the future NOISE PROPAGATION system hangs off (procedural-sound-effects order 5; see
 * {@code .claude/agents/ideas/noise-propagation-and-3d-sound-cues.txt}). Nothing implements it yet —
 * the hook is the deliverable.
 *
 * <p>Two systems, one EVENT. {@link GameAudio} decides what the PLAYER hears (presentation,
 * switchable off); a noise system decides what ENEMIES hear (gameplay, deterministic). They must
 * never merge, but every place a sound is made is also the place a noise is made, so the noise
 * system inherits all of this layer's firing sites instead of growing its own.
 *
 * <p><b>Gameplay-safe by construction:</b> {@code GameAudio} reports a noise BEFORE any
 * presentation gate — the EFFECTS setting (even OFF), the hard-pause suppression, the distance
 * cutoff, the mixer's caps and drops, and whether a WAV could be synthesised at all. Muting the
 * game can therefore never change what an enemy hears. A sound whose definition declares
 * {@code loudness(0)} makes no noise.
 *
 * <p>Primitives rather than an event object, so reporting allocates nothing on a firing path. The
 * category lets a consumer ignore, for instance, the enemies' own voices.
 */
public interface NoiseListener {
    void onNoise(int originTileColumn, int originTileRow, int loudness, SoundCategory category);
}
