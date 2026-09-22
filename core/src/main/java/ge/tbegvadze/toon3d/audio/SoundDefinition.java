package ge.tbegvadze.toon3d.audio;

import ge.tbegvadze.toon3d.util.SoundConstants;

/**
 * One complete sound RECIPE — the content unit of the audio layer
 * (procedural-sound-effects order 1).
 *
 * <p>A definition is pure data: which layers to sum, how loud, how much it may vary, how often it
 * may re-trigger, and how it behaves when the mix is crowded.  {@code SoundSynthesizer} turns it
 * into PCM once at startup; {@link SoundMixer} reads the rest at play time.
 *
 * <p>Adding a sound is ONE {@code register()} call in {@code GameSoundCatalog} — never an edit to
 * the synthesiser, the mixer or a switch statement.
 */
public final class SoundDefinition {

    private final GameSoundId    id;
    private final SoundCategory  category;
    private final SoundLayer[]   layers;
    private final float          baseVolume;
    private final int            priority;
    private final float          cycleSpread;
    private final float          minimumRetriggerSeconds;
    private final int            loudness;

    private SoundDefinition(Builder builder) {
        this.id                      = builder.id;
        this.category                = builder.category;
        this.layers                  = builder.layers;
        this.baseVolume              = builder.baseVolume;
        this.priority                = builder.priority;
        this.cycleSpread             = builder.cycleSpread;
        this.minimumRetriggerSeconds = builder.minimumRetriggerSeconds;
        this.loudness                = builder.loudness;
    }

    public GameSoundId   getId()                      { return id; }
    public SoundCategory getCategory()                { return category; }
    public float         getBaseVolume()              { return baseVolume; }
    public int           getPriority()                { return priority; }
    public float         getCycleSpread()             { return cycleSpread; }
    public float         getMinimumRetriggerSeconds() { return minimumRetriggerSeconds; }

    /**
     * How far this sound would CARRY, 0..100 — read by nothing today.
     *
     * <p>It is filled in from day one as the forward-compatibility seam for the separate
     * noise-propagation design (see that idea file): that system decides what ENEMIES hear, this
     * one decides what the PLAYER hears, and keeping the number on the definition means the future
     * system inherits every firing site in this layer for free rather than growing its own.
     */
    public int getLoudness() { return loudness; }

    public int getLayerCount() {
        return layers.length;
    }

    /**
     * Indexed access rather than a {@code getLayers()} array getter: handing out the array would
     * hand out a way to mutate an otherwise immutable recipe, and copying it on every call would
     * allocate. The synthesiser only ever walks it by index.
     */
    public SoundLayer getLayer(int layerIndex) {
        return layers[layerIndex];
    }

    /** Total sound length: the furthest point any layer reaches, including its delay. */
    public float getTotalDurationSeconds() {
        float longest = 0f;
        for (SoundLayer layer : layers) {
            float layerEnd = layer.getEndTimeSeconds();
            if (layerEnd > longest) longest = layerEnd;
        }
        return longest;
    }

    /**
     * A filename-safe digest of the whole recipe.  Synthesis is deterministic, so a cached WAV from
     * an earlier run is already correct — but only for the SAME recipe, and a retuned layer must
     * write a new file rather than silently re-loading a stale one.  Folding every parameter into
     * the name is what makes that automatic.
     */
    public String buildCacheKey() {
        StringBuilder recipe = new StringBuilder(id.name());
        recipe.append('|').append(Math.round(baseVolume * 100f));
        for (SoundLayer layer : layers) {
            recipe.append('|').append(layer.describeForCacheKey());
        }
        // The full recipe string is far too long for a filename, so the name is the stable id plus
        // a digest of the recipe. The digest is an EXPLICIT 64-bit FNV-1a rather than
        // String.hashCode(): the JDK's is only 32 bits, and the whole point of the digest is that
        // two different recipes must not be able to collide onto one cached WAV — which would play
        // the wrong sound, silently, forever, on every device that had already cached it.
        return id.name().toLowerCase() + '-' + Long.toHexString(fnv1a64(recipe));
    }

    /**
     * FNV-1a, 64-bit. Defined here rather than borrowed so the cache key cannot change underneath
     * us: this value ends up in filenames on players' devices, so its definition is a contract.
     */
    private static long fnv1a64(CharSequence text) {
        long hash = 0xCBF29CE484222325L;
        for (int characterIndex = 0; characterIndex < text.length(); characterIndex++) {
            hash ^= text.charAt(characterIndex);
            hash *= 0x100000001B3L;
        }
        return hash;
    }

    public static Builder builder(GameSoundId id, SoundCategory category) {
        return new Builder(id, category);
    }

    public static final class Builder {

        private final GameSoundId   id;
        private final SoundCategory category;

        private SoundLayer[] layers     = new SoundLayer[0];
        private float baseVolume        = 0.6f;
        private int   priority          = SoundConstants.GAME_SFX_PRIORITY_ENVIRONMENT;
        private float cycleSpread       = 1f;
        private float minimumRetriggerSeconds = SoundConstants.GAME_SFX_DEFAULT_RETRIGGER_SECONDS;
        private int   loudness          = 0;

        private Builder(GameSoundId id, SoundCategory category) {
            this.id       = id;
            this.category = category;
            this.priority = defaultPriorityFor(category);
        }

        public Builder layers(SoundLayer... soundLayers) {
            this.layers = soundLayers;
            return this;
        }

        public Builder volume(float value) {
            this.baseVolume = value;
            return this;
        }

        public Builder priority(int value) {
            this.priority = value;
            return this;
        }

        /** 0.15 for a big gun that should sound identical every time, 1.0 for an obvious variety. */
        public Builder cycleSpread(float value) {
            this.cycleSpread = value;
            return this;
        }

        public Builder minimumRetriggerSeconds(float value) {
            this.minimumRetriggerSeconds = value;
            return this;
        }

        public Builder loudness(int value) {
            this.loudness = value;
            return this;
        }

        public SoundDefinition build() {
            return new SoundDefinition(this);
        }

        private static int defaultPriorityFor(SoundCategory category) {
            switch (category) {
                case PLAYER_WEAPON: return SoundConstants.GAME_SFX_PRIORITY_PLAYER_WEAPON;
                case PLAYER_STATE:  return SoundConstants.GAME_SFX_PRIORITY_PLAYER_STATE;
                case ENEMY:         return SoundConstants.GAME_SFX_PRIORITY_ENEMY;
                case INTERFACE:     return SoundConstants.GAME_SFX_PRIORITY_INTERFACE;
                default:            return SoundConstants.GAME_SFX_PRIORITY_ENVIRONMENT;
            }
        }
    }
}
