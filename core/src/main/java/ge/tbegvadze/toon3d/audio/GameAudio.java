package ge.tbegvadze.toon3d.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Disposable;
import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.util.Constants;
import ge.tbegvadze.toon3d.util.GameMath;
import ge.tbegvadze.toon3d.util.SoundConstants;

/**
 * The gameplay sound facade — the whole surface every call site sees
 * (procedural-sound-effects orders 1 and 2).
 *
 * <p>Owns one {@link Sound} per {@link GameSoundId}, all synthesised at construction from the
 * recipes in {@link GameSoundCatalog}, cached on disk by a hash of the recipe so only the very
 * first launch after an install or a retune pays for synthesis.
 *
 * <p><b>One owner, one dispose.</b> {@code World} constructs exactly one of these beside its
 * {@code StoryAudio} and disposes it in the same chain; every system that fires a sound receives it
 * by setter injection. Four systems each synthesising their own copy would be four times the device
 * voices and four times the file writes, for identical audio.
 *
 * <p><b>Degrades to complete silence</b> when {@code Gdx.audio} is unavailable — headless test
 * runs, the balance simulator, a device with no audio — exactly as {@code StoryAudio} does. Every
 * play method becomes a no-op rather than throwing.
 *
 * <p><b>Never called from {@code render()}.</b> Sounds are fired from the simulation and the mixer
 * clock is advanced from {@code World.update}.
 */
public final class GameAudio implements Disposable {

    private final SoundRegistry     registry     = new SoundRegistry();
    private final SoundMixer        mixer        = new SoundMixer();
    private final Sound[]           soundsById   = new Sound[GameSoundId.COUNT];

    private float playerWorldX     = 0f;
    private float playerWorldY     = 0f;
    private float facingDirectionX = 1f;
    private float facingDirectionY = 0f;

    /** The impact voice of the last weapon fired; see {@link #playWeaponImpactAt}. */
    private GameSoundId lastWeaponImpact = GameSoundId.IMPACT_BALLISTIC;

    private SfxVolumeSetting volumeSetting = SfxVolumeSetting.ON;
    /** True while a hard-pause overlay owns the screen: the world is not ticking, so nothing plays. */
    private boolean suppressed = false;

    public GameAudio() {
        GameSoundCatalog.bootstrap(registry);
        synthesizeAll();
    }

    private void synthesizeAll() {
        if (Gdx.audio == null || Gdx.files == null) return;   // headless / no audio -> silence
        SoundSynthesizer synthesizer =
                new SoundSynthesizer(SoundConstants.GAME_SFX_SAMPLE_RATE_HZ);
        for (GameSoundId id : GameSoundId.values()) {
            SoundDefinition definition = registry.get(id);
            if (definition == null) continue;   // an id may exist a commit ahead of its recipe
            short[] pcm = synthesizer.synthesize(definition);
            soundsById[id.ordinal()] = PcmWavWriter.toCachedSound(
                    SoundConstants.GAME_SFX_GENERATED_DIRECTORY,
                    definition.buildCacheKey(),
                    pcm,
                    SoundConstants.GAME_SFX_SAMPLE_RATE_HZ);
        }
    }

    // -------------------------------------------------------------------------------------
    // Lifecycle, fed from World
    // -------------------------------------------------------------------------------------

    /** Pushed once per update so every positional sound can be attenuated and panned. */
    public void setPlayerState(float playerWorldX, float playerWorldY,
                               float facingDirectionX, float facingDirectionY) {
        this.playerWorldX     = playerWorldX;
        this.playerWorldY     = playerWorldY;
        this.facingDirectionX = facingDirectionX;
        this.facingDirectionY = facingDirectionY;
    }

    public void update(float deltaTime) {
        mixer.update(deltaTime);
    }

    public void setVolumeSetting(SfxVolumeSetting setting) {
        this.volumeSetting = setting != null ? setting : SfxVolumeSetting.ON;
    }

    public SfxVolumeSetting getVolumeSetting() {
        return volumeSetting;
    }

    /** While true, nothing plays — a hard-pause overlay owns the screen and the world is frozen. */
    public void setSuppressed(boolean value) {
        this.suppressed = value;
    }

    /** Stamped by the story layer so gameplay sound ducks briefly under a spoken line. */
    public void noteStoryCuePlayed() {
        mixer.noteStoryCuePlayed();
    }

    /** Drops timing state after an OS suspend, so the first sounds on resume are not swallowed. */
    public void onApplicationResume() {
        mixer.resetTransientState();
    }

    // -------------------------------------------------------------------------------------
    // Play entry points
    // -------------------------------------------------------------------------------------

    /**
     * The gun in the player's hands: full volume, centre pan, never attenuated.
     *
     * <p>A separate entry point from {@link #playAt} precisely so no caller has to remember to
     * pass the player's own tile for a sound that is, by definition, at the ear.
     */
    public void playPlayerWeapon(ItemType weaponItemType) {
        // Remembered so an impact resolving moments later knows what hit it, without every
        // effect system having to be told which weapon is equipped. An impact ALWAYS follows a
        // shot, so the last weapon fired is by construction the one that landed it.
        lastWeaponImpact = registry.impactForWeapon(weaponItemType);
        playCentred(registry.forWeapon(weaponItemType));
    }

    /**
     * A hit landing on an enemy, in the voice of whatever weapon fired.
     *
     * @param sizeMultiplier the target's billboard height fraction — a bigger thing is pitched
     *                       down, which is the cheapest cue available that it is bigger
     */
    public void playWeaponImpactAt(float worldX, float worldY, float sizeMultiplier) {
        playAtWorld(lastWeaponImpact, worldX, worldY, sizeMultiplier);
    }

    /** Non-diegetic confirmations and anything else that is simply "at the ear". */
    public void playUi(GameSoundId soundId) {
        playCentred(soundId);
    }

    private void playCentred(GameSoundId soundId) {
        SoundDefinition definition = resolvePlayable(soundId);
        if (definition == null) return;
        float volume = mixer.admit(definition,
                definition.getBaseVolume() * volumeSetting.getVolumeMultiplier());
        if (volume <= 0f) return;
        soundsById[soundId.ordinal()].play(volume, mixer.nextPitch(definition), 0f);
    }

    /** A sound originating at a tile — attenuated by distance and panned by bearing. */
    public void playAt(GameSoundId soundId, int originTileColumn, int originTileRow) {
        playAtWorld(soundId,
                originTileColumn * Constants.CELL_SIZE + Constants.CELL_SIZE * 0.5f,
                originTileRow    * Constants.CELL_SIZE + Constants.CELL_SIZE * 0.5f,
                1f);
    }

    /**
     * A sound originating anywhere in the world.
     *
     * @param sizeMultiplier a physically larger source is pitched DOWN, which is the cheapest
     *                       available cue that the thing that made the noise is big
     */
    public void playAtWorld(GameSoundId soundId, float originWorldX, float originWorldY,
                            float sizeMultiplier) {
        SoundDefinition definition = resolvePlayable(soundId);
        if (definition == null) return;

        float differenceX = originWorldX - playerWorldX;
        float differenceY = originWorldY - playerWorldY;
        float distanceTiles = (float) Math.sqrt(
                differenceX * differenceX + differenceY * differenceY) / Constants.CELL_SIZE;

        float distanceVolume = GameMath.sfxDistanceVolume(
                distanceTiles,
                SoundConstants.GAME_SFX_REFERENCE_TILES,
                SoundConstants.GAME_SFX_MAX_AUDIBLE_TILES,
                SoundConstants.GAME_SFX_TAPER_TILES);
        if (distanceVolume <= 0f) return;

        float rearFactor = GameMath.sfxRearFactor(differenceX, differenceY,
                facingDirectionX, facingDirectionY, SoundConstants.GAME_SFX_REAR_VOLUME_FACTOR);

        float requestedVolume = definition.getBaseVolume()
                * volumeSetting.getVolumeMultiplier()
                * distanceVolume
                * rearFactor;

        float volume = mixer.admit(definition, requestedVolume);
        if (volume <= 0f) return;

        float pan = GameMath.sfxStereoPan(differenceX, differenceY,
                facingDirectionX, facingDirectionY, SoundConstants.GAME_SFX_PAN_STRENGTH);

        float pitch = mixer.nextPitch(definition);
        // A source behind the head is duller as well as quieter; a larger source is lower still.
        if (rearFactor < 1f) pitch *= SoundConstants.GAME_SFX_REAR_PITCH_FACTOR;
        if (sizeMultiplier > 0f && sizeMultiplier != 1f) pitch /= sizeMultiplier;
        if (pitch < SoundConstants.GAME_SFX_PITCH_MINIMUM) {
            pitch = SoundConstants.GAME_SFX_PITCH_MINIMUM;
        }
        if (pitch > SoundConstants.GAME_SFX_PITCH_MAXIMUM) {
            pitch = SoundConstants.GAME_SFX_PITCH_MAXIMUM;
        }

        soundsById[soundId.ordinal()].play(volume, pitch, pan);
    }

    /** Null when this sound cannot play at all, which collapses every guard into one check. */
    private SoundDefinition resolvePlayable(GameSoundId soundId) {
        if (suppressed || volumeSetting.isSilent() || soundId == null) return null;
        Sound sound = soundsById[soundId.ordinal()];
        if (sound == null) return null;
        return registry.get(soundId);
    }

    @Override
    public void dispose() {
        for (int soundIndex = 0; soundIndex < soundsById.length; soundIndex++) {
            if (soundsById[soundIndex] != null) {
                soundsById[soundIndex].dispose();
                soundsById[soundIndex] = null;
            }
        }
    }
}
