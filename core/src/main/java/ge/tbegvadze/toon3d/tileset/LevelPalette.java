package ge.tbegvadze.toon3d.tileset;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The PER-LEVEL BINDING for the symbol/sprite-reuse migration (order-3): the immutable map that says,
 * for ONE level, which {@link TileCategory} each environment symbol belongs to and which
 * {@link EnvironmentSpriteDefinition} (by stable id) that symbol renders. A level's SYMBOL is a SLOT in a
 * fixed category, but WHICH sprite fills that slot is chosen per level — for a FIXED symbol it is a
 * permanent binding ('@' → "prop_vendor"); for FLEXIBLE symbols it is whatever the allocator (order-4)
 * assigned; for a wall symbol and 'P' it is the floor's region wall set face (generated-wall-art order 2,
 * {@link RegionPaletteBuilder}). Every level carries one of these (see {@code Level.getPalette()});
 * hand-crafted levels use the shared {@link LevelPalettes#legacy()} instance.
 *
 * <p>ID-ONLY, HEADLESS: LevelPalette resolves symbol → category + sprite id (a string). It holds NO
 * textures — the render layer turns sprite ids into textures later (order-6). Keeping it id-only keeps
 * the whole {@code tileset} package free of LibGDX types.
 *
 * <p>ONLY ENVIRONMENT SYMBOLS: the palette binds walls, columns, solid props, and floor decals. Doors,
 * pickups, keycards, ammo, hazards, enemy spawns, player-start, and exit are FIXED gameplay symbols
 * handled by the existing dedicated paths, NOT the palette — {@link #categoryOf(char)} and
 * {@link #spriteIdOf(char)} return {@code null} for them.
 *
 * <p>IMMUTABLE / THREAD-SAFE: built once via the package-private {@link Builder} and never mutated, so it
 * is safe to read from {@code WallRenderer}'s worker threads (order-7) via the happens-before edge from
 * level construction.
 */
public final class LevelPalette {

    // char -> its fixed semantic category. Set together with spriteIds, so a char present in one map is
    // present in the other. Read-only after construction.
    private final Map<Character, TileCategory> categoryBySymbol;
    // char -> the stable EnvironmentSpriteDefinition id this level renders for the symbol.
    private final Map<Character, String> spriteIdBySymbol;
    // The REAL region wall set (generated-wall-art order 2) whose faces the wall + column bindings name;
    // null for a palette that binds no region set.
    private final String wallSetId;

    private LevelPalette(Builder builder) {
        this.categoryBySymbol = Collections.unmodifiableMap(new HashMap<>(builder.categoryBySymbol));
        this.spriteIdBySymbol = Collections.unmodifiableMap(new HashMap<>(builder.spriteIdBySymbol));
        this.wallSetId        = builder.wallSetId;
    }

    /**
     * The category the given symbol belongs to on this level, or {@code null} if the symbol is not an
     * environment symbol (doors, pickups, ammo, hazards, enemy spawns, start, exit — handled by the
     * existing fixed paths, not the palette).
     */
    public TileCategory categoryOf(char symbol) {
        return categoryBySymbol.get(symbol);
    }

    /**
     * The stable {@link EnvironmentSpriteDefinition} id this level renders for the given symbol, or
     * {@code null} for a non-environment symbol. FIXED symbols return their permanent binding; FLEXIBLE
     * symbols return whatever the allocator assigned (the legacy palette returns today's fixed art).
     */
    public String spriteIdOf(char symbol) {
        return spriteIdBySymbol.get(symbol);
    }

    /** Whether this palette binds a category + sprite to the given symbol. */
    public boolean hasBinding(char symbol) {
        return categoryBySymbol.containsKey(symbol);
    }

    /**
     * The DISTINCT sprite ids this level actually renders — the small set the render layer realizes into
     * textures for this level (order-6). De-duplicated: several symbols bound to the same sprite id (e.g.
     * every BASE wall symbol of a family collapsing onto its variant-0 face) contribute that id once, so exactly one
     * texture is built per shared sprite. Unmodifiable snapshot; order is unspecified.
     */
    public Set<String> distinctSpriteIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(spriteIdBySymbol.values()));
    }

    /** Starts a fresh palette builder. Package-private: only the allocator and {@link LevelPalettes}. */
    /** The region wall set the wall + column bindings come from (order 2), or null. */
    public String wallSetId() {
        return wallSetId;
    }

    static Builder builder() {
        return new Builder();
    }

    /**
     * Accumulates symbol → (category, sprite id) bindings, then freezes them into an immutable
     * {@link LevelPalette}. Package-private so the LEGACY factory and the order-4 allocator are the only
     * builders of palettes.
     */
    static final class Builder {
        private final Map<Character, TileCategory> categoryBySymbol = new HashMap<>();
        private final Map<Character, String> spriteIdBySymbol = new HashMap<>();
        private String wallSetId;

        private Builder() {}

        /**
         * Binds one environment symbol to its category and the sprite id it renders. A later bind of the
         * same symbol overwrites the earlier one.
         */
        Builder bind(char symbol, TileCategory category, String spriteId) {
            // Validate BEFORE mutating either map so a bad argument can never leave one map bound and the
            // other not — the documented invariant that a symbol is in both maps or neither.
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(spriteId, "spriteId");
            categoryBySymbol.put(symbol, category);
            spriteIdBySymbol.put(symbol, spriteId);
            return this;
        }

        /** Copies every binding of {@code source} (its wall set too), to be overridden per symbol. */
        Builder copyOf(LevelPalette source) {
            categoryBySymbol.putAll(source.categoryBySymbol);
            spriteIdBySymbol.putAll(source.spriteIdBySymbol);
            wallSetId = source.wallSetId;
            return this;
        }

        Builder wallSetId(String value) {
            this.wallSetId = value;
            return this;
        }

        LevelPalette build() {
            return new LevelPalette(this);
        }
    }
}
