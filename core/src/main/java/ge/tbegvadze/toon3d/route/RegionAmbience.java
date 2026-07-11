package ge.tbegvadze.toon3d.route;

import java.util.Objects;

/**
 * The IDENTITY of one region / act band (route-map order-10) — the data row that makes descending
 * FEEL like moving through a changing facility rather than a reskinned treadmill. Where
 * {@link RegionSpec} owns a region's DISTRIBUTION biases (which node types and generators the roll
 * leans toward), this owns its THEMING and AMBIENCE: the crest and overlay tint the render layer
 * draws, the wall palette its generators lean on, the enemy faction that dominates, the ambience
 * hooks the audio + red-alert systems read later, the boss that closes the region, and the
 * REGION_GATE announce flavour.
 *
 * <p>Registered by {@code themeId} in {@link RegionAmbienceRegistry} and looked up wherever the
 * theme id is known (a {@link RouteRegion} carries it). NOTHING hard-codes the region list; adding a
 * region is one {@link Builder} row plus its crest painter — the registry does the rest.
 *
 * <p>Deliberately free of any LibGDX import: crest / overlay are referenced by string id and
 * resolved to painters / colours in the render layer (order-4/5). Ambience hooks are ids and flags —
 * no audio assets are added here — so the audio and lighting systems can read them when they ship.
 * Pure / headless value object built via {@link Builder}.
 */
public final class RegionAmbience {

    private final String themeId;
    private final String displayName;
    private final String crestPainterId;
    private final String overlayThemeId;
    private final String wallPalette;
    private final String enemyFactionId;
    private final String lightingMoodId;
    private final String musicId;
    private final float  redAlertFrequency;
    private final String bossId;
    private final String gateFlavour;

    private RegionAmbience(Builder builder) {
        this.themeId           = Objects.requireNonNull(builder.themeId, "themeId");
        this.displayName       = Objects.requireNonNull(builder.displayName, "displayName");
        this.crestPainterId    = Objects.requireNonNull(builder.crestPainterId, "crestPainterId");
        this.overlayThemeId    = Objects.requireNonNull(builder.overlayThemeId, "overlayThemeId");
        this.wallPalette       = Objects.requireNonNull(builder.wallPalette, "wallPalette");
        this.enemyFactionId    = Objects.requireNonNull(builder.enemyFactionId, "enemyFactionId");
        this.lightingMoodId    = Objects.requireNonNull(builder.lightingMoodId, "lightingMoodId");
        this.musicId           = Objects.requireNonNull(builder.musicId, "musicId");
        this.redAlertFrequency = builder.redAlertFrequency;
        this.bossId            = Objects.requireNonNull(builder.bossId, "bossId");
        this.gateFlavour       = Objects.requireNonNull(builder.gateFlavour, "gateFlavour");
    }

    /** The theme key this identity is registered under (matches {@link RouteRegion#themeId()}). */
    public String themeId() {
        return themeId;
    }

    /** Human-facing act name (e.g. "REACTOR DEPTHS"). */
    public String displayName() {
        return displayName;
    }

    /** Which region-crest painter draws this act's title-plate badge (order-5). */
    public String crestPainterId() {
        return crestPainterId;
    }

    /** The overlay frame/scrim tint key the nav console reads (order-4). */
    public String overlayThemeId() {
        return overlayThemeId;
    }

    /** The wall symbols (from {@code docs/tile-symbols.txt}) this act's generators lean on. */
    public String wallPalette() {
        return wallPalette;
    }

    /** The enemy faction id that dominates this act's spawns (descriptive hook for EnemyManager). */
    public String enemyFactionId() {
        return enemyFactionId;
    }

    /** The lighting-mood id the lighting system reads for this act (no assets added here). */
    public String lightingMoodId() {
        return lightingMoodId;
    }

    /** The music id placeholder the audio system reads for this act (no assets added here). */
    public String musicId() {
        return musicId;
    }

    /** How often (0..1) the ambient red-alert pulse flares in this act — escalates with depth. */
    public float redAlertFrequency() {
        return redAlertFrequency;
    }

    /** The boss id that closes this region at its final depth. */
    public String bossId() {
        return bossId;
    }

    /** The ceremonial REGION_GATE announce flavour line for crossing INTO this region. */
    public String gateFlavour() {
        return gateFlavour;
    }

    /** Starts a builder for a region identity keyed by {@code themeId}. */
    public static Builder builder(String themeId) {
        return new Builder(themeId);
    }

    /** Fluent builder — keeps the many identity fields legible at the registration call site. */
    public static final class Builder {
        private final String themeId;
        private String displayName;
        private String crestPainterId;
        private String overlayThemeId;
        private String wallPalette;
        private String enemyFactionId;
        private String lightingMoodId;
        private String musicId;
        private float  redAlertFrequency;
        private String bossId;
        private String gateFlavour;

        private Builder(String themeId) {
            this.themeId = themeId;
            // Sensible defaults so a partial row still builds; every v1 region sets all of them.
            this.overlayThemeId = themeId;
            this.crestPainterId = "crest_" + themeId;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder crestPainterId(String crestPainterId) {
            this.crestPainterId = crestPainterId;
            return this;
        }

        public Builder overlayThemeId(String overlayThemeId) {
            this.overlayThemeId = overlayThemeId;
            return this;
        }

        public Builder wallPalette(String wallPalette) {
            this.wallPalette = wallPalette;
            return this;
        }

        public Builder enemyFactionId(String enemyFactionId) {
            this.enemyFactionId = enemyFactionId;
            return this;
        }

        public Builder lightingMoodId(String lightingMoodId) {
            this.lightingMoodId = lightingMoodId;
            return this;
        }

        public Builder musicId(String musicId) {
            this.musicId = musicId;
            return this;
        }

        public Builder redAlertFrequency(float redAlertFrequency) {
            this.redAlertFrequency = redAlertFrequency;
            return this;
        }

        public Builder bossId(String bossId) {
            this.bossId = bossId;
            return this;
        }

        public Builder gateFlavour(String gateFlavour) {
            this.gateFlavour = gateFlavour;
            return this;
        }

        public RegionAmbience build() {
            return new RegionAmbience(this);
        }
    }
}
