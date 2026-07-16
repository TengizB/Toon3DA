package ge.tbegvadze.toon3d.tileset;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The immutable art demand of ONE signature room actually placed on a level — the unit the
 * {@link SymbolAllocator} reserves sprites for (tileset order-4). A demand declares two things:
 *
 * <ul>
 *   <li>{@link #requiredSprites()} — symbol → the exact sprite id this room MUST render for that symbol
 *       (e.g. a CRYO room reserves its flexible wall slot 'Z' → "wall_cryo"). Step 2 of the allocator
 *       binds these verbatim so per-level variety can never overwrite a room's signature look.</li>
 *   <li>{@link #categoryCounts()} — how many GENERIC (unspecified) slots of each category the room also
 *       wants. This is metadata order-5's {@code RoomBlueprint} fills in and order-8 consumes when it
 *       stamps generic room tiles; order-4 defines the shape and carries it through, but the allocator's
 *       reservation logic keys only off {@link #requiredSprites()}.</li>
 * </ul>
 *
 * <p>THE FREED-SYMBOL MECHANIC IS EMERGENT FROM THIS TYPE: only rooms actually placed contribute a
 * demand, so a room that was NOT placed reserves nothing and its symbols fall through to the allocator's
 * free variety pool automatically — no special "free this symbol" call anywhere. Headless — no LibGDX.
 *
 * <p>order-5's {@code RoomBlueprint} produces these; the type lives in {@code tileset} (not {@code level}
 * or {@code render}) so the pure-logic allocator can consume it without a dependency leak.
 */
public final class RoomSymbolDemand {

    private final Map<Character, String> requiredSprites;
    private final Map<TileCategory, Integer> categoryCounts;

    private RoomSymbolDemand(Map<Character, String> requiredSprites,
                             Map<TileCategory, Integer> categoryCounts) {
        // Defensive, insertion-ordered copies so a demand is frozen the moment it is built and iteration
        // order is reproducible (determinism depends on it — see SymbolAllocator).
        this.requiredSprites = Collections.unmodifiableMap(new LinkedHashMap<>(requiredSprites));
        Map<TileCategory, Integer> copiedCounts = new EnumMap<>(TileCategory.class);
        copiedCounts.putAll(categoryCounts);
        this.categoryCounts = Collections.unmodifiableMap(copiedCounts);
    }

    /**
     * A demand that only reserves specific symbol → sprite bindings and needs no generic slots — the
     * common case for the existing signature rooms.
     */
    public static RoomSymbolDemand of(Map<Character, String> requiredSprites) {
        return new RoomSymbolDemand(requiredSprites, Collections.emptyMap());
    }

    /** A demand that reserves specific bindings AND asks for a number of generic slots per category. */
    public static RoomSymbolDemand of(Map<Character, String> requiredSprites,
                                      Map<TileCategory, Integer> categoryCounts) {
        return new RoomSymbolDemand(
                Objects.requireNonNull(requiredSprites, "requiredSprites"),
                Objects.requireNonNull(categoryCounts, "categoryCounts"));
    }

    /**
     * Symbol → the sprite id this room MUST render for it. Unmodifiable; iteration order is the order the
     * bindings were supplied. Empty is allowed (a room with only generic demand).
     */
    public Map<Character, String> requiredSprites() {
        return requiredSprites;
    }

    /**
     * How many generic (allocator-chosen) slots of each category this room wants, beyond its required
     * sprites. Unmodifiable; empty when the room reserves everything explicitly. order-8 consumes this;
     * order-4 only carries it.
     */
    public Map<TileCategory, Integer> categoryCounts() {
        return categoryCounts;
    }
}
