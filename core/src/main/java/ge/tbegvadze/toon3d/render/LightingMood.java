package ge.tbegvadze.toon3d.render;

import java.util.Objects;

/**
 * One region's light: an immutable value describing the multiplicative colour grade (near and far tint,
 * fog band), the backdrop horizon colours, the corner vignette strength and the floor/ceiling materials.
 * Pure data, no LibGDX imports; registered through {@link LightingMoods#bootstrap()}.
 *
 * <p>Tints are R,G,B multipliers (channels may exceed 1); horizon colours are packed RGBA8888.
 */
public final class LightingMood {

    private final String id;
    private final float nearRed, nearGreen, nearBlue;
    private final float farRed, farGreen, farBlue;
    private final float fogStartTiles, fogEndTiles;
    private final int horizonFloorPacked, horizonCeilingPacked;
    private final float vignetteAlpha;
    private final String floorMaterialId, ceilingMaterialId;

    private LightingMood(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id");
        this.nearRed = builder.nearRed;
        this.nearGreen = builder.nearGreen;
        this.nearBlue = builder.nearBlue;
        this.farRed = builder.farRed;
        this.farGreen = builder.farGreen;
        this.farBlue = builder.farBlue;
        this.fogStartTiles = builder.fogStartTiles;
        this.fogEndTiles = builder.fogEndTiles;
        this.horizonFloorPacked = builder.horizonFloorPacked;
        this.horizonCeilingPacked = builder.horizonCeilingPacked;
        this.vignetteAlpha = builder.vignetteAlpha;
        this.floorMaterialId = Objects.requireNonNull(builder.floorMaterialId, "floorMaterialId");
        this.ceilingMaterialId = Objects.requireNonNull(builder.ceilingMaterialId, "ceilingMaterialId");
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String id() { return id; }
    public float nearRed() { return nearRed; }
    public float nearGreen() { return nearGreen; }
    public float nearBlue() { return nearBlue; }
    public float farRed() { return farRed; }
    public float farGreen() { return farGreen; }
    public float farBlue() { return farBlue; }
    public float fogStartTiles() { return fogStartTiles; }
    public float fogEndTiles() { return fogEndTiles; }
    public int horizonFloorPacked() { return horizonFloorPacked; }
    public int horizonCeilingPacked() { return horizonCeilingPacked; }
    public float vignetteAlpha() { return vignetteAlpha; }
    public String floorMaterialId() { return floorMaterialId; }
    public String ceilingMaterialId() { return ceilingMaterialId; }

    /** Builder for {@link LightingMood}. */
    public static final class Builder {
        private final String id;
        private float nearRed = 1f, nearGreen = 1f, nearBlue = 1f;
        private float farRed = 1f, farGreen = 1f, farBlue = 1f;
        private float fogStartTiles, fogEndTiles;
        private int horizonFloorPacked, horizonCeilingPacked;
        private float vignetteAlpha;
        private String floorMaterialId, ceilingMaterialId;

        private Builder(String id) {
            this.id = id;
        }

        public Builder nearTint(float red, float green, float blue) {
            this.nearRed = red; this.nearGreen = green; this.nearBlue = blue;
            return this;
        }

        public Builder farTint(float red, float green, float blue) {
            this.farRed = red; this.farGreen = green; this.farBlue = blue;
            return this;
        }

        public Builder fog(float startTiles, float endTiles) {
            this.fogStartTiles = startTiles; this.fogEndTiles = endTiles;
            return this;
        }

        public Builder horizon(int floorPacked, int ceilingPacked) {
            this.horizonFloorPacked = floorPacked; this.horizonCeilingPacked = ceilingPacked;
            return this;
        }

        public Builder vignetteAlpha(float vignetteAlpha) {
            this.vignetteAlpha = vignetteAlpha;
            return this;
        }

        public Builder materials(String floorMaterialId, String ceilingMaterialId) {
            this.floorMaterialId = floorMaterialId; this.ceilingMaterialId = ceilingMaterialId;
            return this;
        }

        public LightingMood build() {
            return new LightingMood(this);
        }
    }
}
