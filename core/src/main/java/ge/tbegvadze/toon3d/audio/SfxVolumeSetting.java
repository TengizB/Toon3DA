package ge.tbegvadze.toon3d.audio;

import ge.tbegvadze.toon3d.util.SoundConstants;

/**
 * The player-facing SOUND EFFECTS knob — three states, not a slider.
 *
 * <p>Three states are one tap and fit the existing settings-strip geometry exactly; a slider would
 * need new widget code on a screen that has none.  QUIET exists because the most common real
 * complaint about phone game audio is not "too loud" or "off" but "I want it in the background".
 *
 * <p>This is independent of the story layer's STORY AUDIO knob: somebody may well want ORA's voice
 * stings without gunfire, or gunfire without them.
 */
public enum SfxVolumeSetting {

    OFF  (SoundConstants.GAME_SFX_VOLUME_OFF),
    QUIET(SoundConstants.GAME_SFX_VOLUME_QUIET),
    ON   (SoundConstants.GAME_SFX_VOLUME_FULL);

    private final float volumeMultiplier;

    SfxVolumeSetting(float volumeMultiplier) {
        this.volumeMultiplier = volumeMultiplier;
    }

    public float getVolumeMultiplier() {
        return volumeMultiplier;
    }

    public boolean isSilent() {
        return volumeMultiplier <= 0f;
    }

    /** Cycles ON -> QUIET -> OFF -> ON, which is the order a single settings plate taps through. */
    public SfxVolumeSetting next() {
        switch (this) {
            case ON:    return QUIET;
            case QUIET: return OFF;
            default:    return ON;
        }
    }

    /** Never throws: an unreadable or renamed persisted value falls back to ON. */
    public static SfxVolumeSetting fromPersistedName(String persistedName) {
        if (persistedName == null) return ON;
        for (SfxVolumeSetting setting : values()) {
            if (setting.name().equals(persistedName)) return setting;
        }
        return ON;
    }
}
