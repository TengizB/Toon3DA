package ge.tbegvadze.toon3d.tileset;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * One concrete piece of environment art the game can draw — a wall texture, a column, a solid-prop
 * billboard, or a floor decal. This is the immutable value object registered in the
 * {@link EnvironmentSpriteRegistry}; it is pure DATA, so the allocator (later order) and renderers can
 * enumerate the art inventory without reading {@code WallRenderer}'s table or {@code PropRenderer}'s
 * switch. Headless — no LibGDX / GL types anywhere in this class; colours are NOT stored here (they
 * live in the texture generators, a later order). Built once at startup via {@link Builder}.
 *
 * <p>The {@link #id()} is STABLE: it is the key palettes / seeds use to stay reproducible across code
 * changes, so ids must never be renamed once shipped.
 */
public final class EnvironmentSpriteDefinition {

    private final String       id;
    private final TileCategory category;
    private final String       displayName;
    private final float        heightMultiplier;
    private final boolean      occluder;
    private final String       textureGeneratorId;
    private final Set<String>  themeTags;

    private EnvironmentSpriteDefinition(Builder builder) {
        this.id                 = Objects.requireNonNull(builder.id, "id");
        this.category           = Objects.requireNonNull(builder.category, "category");
        this.displayName        = Objects.requireNonNull(builder.displayName, "displayName");
        this.heightMultiplier   = builder.heightMultiplier;
        this.occluder           = builder.occluder;
        // textureGeneratorId defaults to the sprite id (v1: one generator per sprite) but is kept
        // separate so several sprites could share one generator later.
        this.textureGeneratorId = builder.textureGeneratorId != null ? builder.textureGeneratorId : builder.id;
        this.themeTags          = Collections.unmodifiableSet(new LinkedHashSet<>(builder.themeTags));
    }

    /** Stable sprite id (e.g. "wall_plain", "prop_locker", "decal_blood"). Never rename once shipped. */
    public String id() {
        return id;
    }

    /** The fixed semantic category this sprite belongs to. */
    public TileCategory category() {
        return category;
    }

    /** Human-facing label for tooling / logging only (e.g. "Cryo Wall"). */
    public String displayName() {
        return displayName;
    }

    /**
     * Billboard height as a fraction of a full wall stripe (e.g. locker 0.90, crate 0.50); 1.0 for
     * WALL / COLUMN. Copied literal-for-literal from the existing render constants so nothing changes
     * when a renderer reads it later.
     */
    public float heightMultiplier() {
        return heightMultiplier;
    }

    /**
     * Whether this sprite writes to the z-buffer (mirrors {@code Level.isPropOccluder}). Solid props,
     * walls, and columns occlude; flat floor stains do not.
     */
    public boolean occluder() {
        return occluder;
    }

    /**
     * Which procedural texture generator draws this sprite (the generator registry lands in a later
     * order). For v1 this equals the sprite {@link #id()}.
     */
    public String textureGeneratorId() {
        return textureGeneratorId;
    }

    /**
     * Coherence hints the allocator uses for variety / containment (e.g. "observatory"). Unmodifiable;
     * may be empty. Room-contained art (STELLAR_OBSERVATORY) is tagged so the allocator only pulls it
     * for its room, preserving today's containment rule.
     */
    public Set<String> themeTags() {
        return themeTags;
    }

    /** Whether this sprite carries the given theme tag. */
    public boolean hasThemeTag(String tag) {
        return themeTags.contains(tag);
    }

    /** Starts a builder for a sprite with the given stable id, category, and display name. */
    public static Builder builder(String id, TileCategory category, String displayName) {
        return new Builder(id, category, displayName);
    }

    /**
     * Fluent builder. {@code heightMultiplier} defaults to 1.0 (a full stripe, correct for WALL /
     * COLUMN); {@code occluder} defaults to true (correct for walls, columns, and solid props — only
     * flat decals override it to false); {@code textureGeneratorId} defaults to the sprite id.
     */
    public static final class Builder {
        private final String       id;
        private final TileCategory category;
        private final String       displayName;
        private float        heightMultiplier   = 1f;
        private boolean      occluder           = true;
        private String       textureGeneratorId = null;
        private final Set<String> themeTags     = new LinkedHashSet<>();

        private Builder(String id, TileCategory category, String displayName) {
            this.id          = id;
            this.category    = category;
            this.displayName = displayName;
        }

        public Builder heightMultiplier(float value)  { this.heightMultiplier = value; return this; }
        public Builder occluder(boolean value)        { this.occluder = value; return this; }
        public Builder textureGeneratorId(String value) { this.textureGeneratorId = value; return this; }

        /** Adds one coherence tag (repeatable). */
        public Builder themeTag(String tag) {
            this.themeTags.add(Objects.requireNonNull(tag, "tag"));
            return this;
        }

        public EnvironmentSpriteDefinition build() {
            return new EnvironmentSpriteDefinition(this);
        }
    }
}
