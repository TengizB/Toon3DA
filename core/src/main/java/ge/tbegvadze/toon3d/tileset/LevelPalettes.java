package ge.tbegvadze.toon3d.tileset;

import java.util.List;

/**
 * Static factory for {@link LevelPalette}s. Today it builds exactly one: {@link #legacy()}, the palette
 * that reproduces the game's historic global 1:1 symbol → sprite mapping. This is the MIGRATION LEVER of
 * the symbol/sprite-reuse series (order-3): the renderers and {@code Level} predicates are still the
 * runtime authority until order-7, so to switch them safely every level must carry a palette that yields
 * EXACTLY today's art. With the legacy palette as {@code Level}'s default, order-3 attaches a palette to
 * every level with zero visible change; order-7 can then flip the renderers to read the palette and,
 * because the default IS legacy, nothing changes until order-4/8 start producing varied palettes for
 * generated levels. Hand-crafted {@code assets/levels/*.txt} keep the legacy palette forever.
 *
 * <p>This is the ONLY place that still encodes the historic 1:1 symbol → sprite table. It does so
 * mechanically: for each category it zips a registration-ordered symbol string against the matching pool
 * in the {@link EnvironmentSpriteRegistry} (order-1), which registered every existing sprite. The symbol
 * strings below therefore mirror {@code TilesetRegistries}'s registration order per category, and a
 * length mismatch throws loudly so registry drift cannot silently corrupt the mapping. Everything else in
 * the game asks the palette, never this table.
 */
public final class LevelPalettes {

    // Registration-ordered environment symbols per category — index-aligned with the matching
    // EnvironmentSpriteRegistry pool (allInCategory), so char[i] renders pool[i]. These strings are the
    // historic 1:1 table; keep them in lockstep with TilesetRegistries' registration order.
    // WALL: x c v t w h j G k N Q S M Z U X D F  + observatory "  '  `
    private static final String LEGACY_WALL_SYMBOLS       = "xcvtwhjGkNQSMZUXDF\"'`";
    // COLUMN: P (round column — the only column today).
    private static final String LEGACY_COLUMN_SYMBOLS     = "P";
    // SOLID_PROP: g E T L C # % & = @ I J W  + observatory ;  \  |  <  (@ vendor sits in registration
    // order between = and I; it is a FIXED-sprite symbol but still an environment sprite bound here).
    private static final String LEGACY_SOLID_PROP_SYMBOLS = "gETLC#%&=@IJW;\\|<";
    // FLOOR_DECAL: m s . O e  + observatory ?  ]  -
    private static final String LEGACY_DECAL_SYMBOLS      = "ms.Oe?]-";

    // Memoised shared singleton — the legacy mapping never varies, so hand levels pay no per-level cost.
    // Double-checked locking with a volatile field: safe to build lazily from any thread.
    private static volatile LevelPalette legacyInstance;

    private LevelPalettes() {}

    /**
     * The shared, immutable palette reproducing today's exact global symbol → sprite mapping (e.g. 'Z' →
     * "wall_cryo", 'P' → "column_round", '@' → "prop_vendor"). Memoised: one instance for the whole run.
     * Ensures the sprite registry is bootstrapped before reading it.
     */
    public static LevelPalette legacy() {
        LevelPalette cached = legacyInstance;
        if (cached == null) {
            synchronized (LevelPalettes.class) {
                cached = legacyInstance;
                if (cached == null) {
                    cached = buildLegacy();
                    legacyInstance = cached;
                }
            }
        }
        return cached;
    }

    /**
     * A per-level palette identical to {@link #legacy()} EXCEPT the bulk wall symbol 'x' is a base wall
     * chosen from the floor seed. This is how EVERY procedural generator (caverns, corridors, boss arenas,
     * special rooms, …) — not just the main dungeon {@code LevelGenerator} — gets BASE-WALL VARIETY without
     * disturbing its other art: the generator passes its own seed here and hands the result to
     * {@code new Level(grid, spawns, weapons, palette)}. Hand-crafted levels loaded by {@code LevelLoader}
     * keep {@link #legacy()} and never vary. Deterministic (same seed ⇒ same palette); the base-wall pick
     * is {@link SymbolAllocator#chooseBaseWall} — the same single source of truth the full allocator uses,
     * on an RNG stream independent of the generator's own {@code Random}, so the generated grid is
     * unchanged. NOT memoised (the seed varies per level); building it is cheap.
     */
    public static LevelPalette generatedWithBaseWall(long levelSeed) {
        SymbolBudget budget = SymbolBudget.standard();
        EnvironmentSpriteRegistry registry = bootstrappedRegistry();
        LevelPalette.Builder builder = legacyBuilder(registry);
        String baseWall = SymbolAllocator.chooseBaseWall(levelSeed, budget, registry);
        if (baseWall != null) {
            builder.bind(budget.baseWallSymbol(), TileCategory.WALL, baseWall);
        }
        return builder.build();
    }

    private static LevelPalette buildLegacy() {
        return legacyBuilder(bootstrappedRegistry()).build();
    }

    // Fills a builder with the historic 1:1 legacy bindings for every category. Shared by buildLegacy()
    // and generatedWithBaseWall() so both encode the legacy table exactly once.
    private static LevelPalette.Builder legacyBuilder(EnvironmentSpriteRegistry registry) {
        LevelPalette.Builder builder = LevelPalette.builder();
        bindCategory(builder, registry, TileCategory.WALL,        LEGACY_WALL_SYMBOLS);
        bindCategory(builder, registry, TileCategory.COLUMN,      LEGACY_COLUMN_SYMBOLS);
        bindCategory(builder, registry, TileCategory.SOLID_PROP,  LEGACY_SOLID_PROP_SYMBOLS);
        bindCategory(builder, registry, TileCategory.FLOOR_DECAL, LEGACY_DECAL_SYMBOLS);
        return builder;
    }

    // Idempotent; guarantees the shared registry holds the full v1 inventory before it is read.
    private static EnvironmentSpriteRegistry bootstrappedRegistry() {
        TilesetRegistries.bootstrap();
        return TilesetRegistries.sprites();
    }

    // Zips a registration-ordered symbol string against a category's registry pool: char[i] binds to
    // pool[i].id(). The historic environment sprites are registered FIRST in each category, so binding
    // the legacy symbols to pool[0..symbols.length()-1] reproduces today's exact art. Since order-8 the
    // catalog may carry MORE sprites than the legacy table has symbols (variety-only art that no
    // legacy symbol maps to, e.g. "column_square" in the COLUMN category — REQUIREMENT PROOF 2): those
    // extras are simply never bound by the legacy palette, which is correct — a level only ever uses as
    // many distinct sprites as it has symbols. FEWER registered sprites than symbols still means the
    // table and registry drifted apart, so that direction still fails loudly.
    private static void bindCategory(LevelPalette.Builder builder, EnvironmentSpriteRegistry registry,
                                     TileCategory category, String symbols) {
        List<EnvironmentSpriteDefinition> pool = registry.allInCategory(category);
        if (pool.size() < symbols.length()) {
            throw new IllegalStateException(
                    "Legacy palette symbol/sprite count mismatch for " + category + ": "
                            + symbols.length() + " symbols vs only " + pool.size() + " registered sprites");
        }
        for (int index = 0; index < symbols.length(); index++) {
            builder.bind(symbols.charAt(index), category, pool.get(index).id());
        }
    }
}
