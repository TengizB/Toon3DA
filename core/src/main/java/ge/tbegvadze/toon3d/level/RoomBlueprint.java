package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.tileset.RoomSymbolDemand;

/**
 * A registered, self-describing room — the ROOM extensibility layer of the symbol/sprite-reuse series
 * (order-5). Every existing {@code LevelGenerator.RoomType} is wrapped as one registered blueprint (see
 * {@link RoomBlueprints}); {@link RoomBlueprintRegistry} is what the generator selects from instead of a
 * hardcoded {@code enum} switch — the same "never a switch statement" registry discipline as
 * {@code route/RouteRegistries}.
 *
 * <p>A blueprint answers four questions and knows how to stamp itself:
 * <ul>
 *   <li>{@link #id()} / {@link #kind()} — stable identity + SIGNATURE vs GENERIC tier.</li>
 *   <li>{@link #eligible(RoomContext)} — may this room type occupy a candidate of the given size/depth,
 *       given how many are already placed (the current {@code LevelGenConstants} size/cap/depth gates).</li>
 *   <li>{@link #selectionWeight(RoomContext)} — the roll weight when eligible (today's {@code *_CHANCE}
 *       constants). order-8 uses this to replace the hardcoded probability bands.</li>
 *   <li>{@link #symbolDemand()} — the sprite ids this room CLAIMS when placed, which the order-4
 *       {@code SymbolAllocator} reserves (and, when the room is absent, does NOT — the freed-symbol
 *       mechanic). SIGNATURE rooms declare their signature walls/props/decals; GENERIC rooms declare an
 *       empty demand because their look is allocator-driven.</li>
 *   <li>{@link #build(RoomBuildContext)} — stamps the room's architecture (themed walls, floor lighting,
 *       columns, props) into the grid. The build logic is the EXISTING per-room generator code, invoked
 *       through {@link RoomBuildContext} rather than copied, so behaviour is byte-for-byte identical.</li>
 * </ul>
 *
 * <p>ORDER-5 SCOPE: this order adds the registry seam and guarantees every blueprint can PRODUCE its
 * demand and build its architecture; it does NOT rewire {@code LevelGenerator.generate()} (that is
 * order-8). The generator still selects room types via its internal {@code RoomType} tag and runs its
 * existing passes; {@code build()} here is exercised by the order-5 tests and becomes the generator's
 * room-stamping entry point in order-8. Headless — no LibGDX.
 */
public interface RoomBlueprint {

    /**
     * Stable string id (e.g. {@code "medical_bay"}, {@code "cryo_chamber"}, {@code "standard"}). Used as
     * the registry key and MUST NOT be renamed once shipped (seed/reproducibility contract).
     */
    String id();

    /** SIGNATURE (fixed identity + look) or GENERIC (allocator-driven filler). */
    RoomKind kind();

    /**
     * Whether this room may occupy a candidate described by {@code context} — mirrors today's
     * {@code LevelGenConstants} minimum-size, depth-window, and per-level hard-cap gates.
     */
    boolean eligible(RoomContext context);

    /**
     * The roll weight for this room when {@link #eligible(RoomContext)} is true (today's {@code *_CHANCE}
     * constants). Zero means "never rolled even when eligible" (e.g. a room only placed by a dedicated
     * finder pass). order-8 feeds these to the seeded selection; order-5 only declares them.
     */
    float selectionWeight(RoomContext context);

    /**
     * The sprite ids + category slot counts this room CLAIMS when placed. The order-4
     * {@code SymbolAllocator} reserves {@link RoomSymbolDemand#requiredSprites()} verbatim so per-level
     * variety cannot overwrite the room's signature look; an absent room contributes no demand, freeing
     * its symbols. GENERIC rooms return an empty demand.
     */
    RoomSymbolDemand symbolDemand();

    /**
     * Stamps this room's architecture (themed walls, floor lighting, columns, props) into the grid via
     * the generator helpers exposed by {@code context}. Writes SYMBOLS, not sprites — the palette maps
     * symbols to sprites at render time, so {@code build()} stays about layout.
     */
    void build(RoomBuildContext context);
}
