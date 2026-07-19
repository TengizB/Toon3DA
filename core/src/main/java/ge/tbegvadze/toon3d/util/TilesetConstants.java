package ge.tbegvadze.toon3d.util;

/**
 * The FIXED / FLEXIBLE symbol partition for the tileset symbol/sprite-reuse migration (order-2), held
 * as named string tables so the split is data, not scattered inline char lists. {@code SymbolBudget}
 * ({@code …toon3d.tileset}) is the only reader; changing the partition means editing one constant here,
 * never touching code. Headless — no LibGDX.
 *
 * <p>SAFETY DIRECTION: flexibility is declared EXPLICITLY and everything else is treated as FIXED
 * (untouchable by the allocator). Under-declaring flexibility is safe (you just get less variety);
 * over-declaring — marking a gameplay symbol flexible — is the dangerous direction, so a symbol only
 * becomes reassignable by being listed below.
 *
 * <p>Each flexible symbol string is a character list where every char is one level-file symbol; the
 * matching sprite id lives in the environment-sprite registry (order-1). The chars here mirror
 * {@code Level}'s predicates and {@code TilesetRegistries}'s registration order, minus the fixed
 * exceptions called out in {@link #FIXED_SPRITE_SYMBOLS}.
 */
public final class TilesetConstants {

    private TilesetConstants() {}

    // ── FIXED WALL (the plain-wall default) ──────────────────────────────────────────────────────
    // 'x' is the 80-90% default wall and every hand-crafted level leans on it heavily; it stays a
    // FIXED WALL slot permanently bound to "wall_plain" so generic corridors always read the same and
    // existing hand levels render their bulk walls unchanged. Flexibility applies to the ACCENT walls.
    /** The single plain-wall symbol, permanently bound to {@link #FIXED_WALL_SPRITE_ID}. */
    public static final String FIXED_WALL_SYMBOL    = "x";
    /**
     * The DEFAULT sprite 'x' is bound to (registered in order-1's wall pool). Hand-crafted levels and the
     * legacy palette always render 'x' as this. GENERATED levels may instead pick one of the alternate
     * base walls below, seeded from the floor seed (see {@link #BASE_WALL_ALTERNATE_SPRITE_IDS}).
     */
    public static final String FIXED_WALL_SPRITE_ID = "wall_plain";

    /**
     * Extra base-wall sprites (beyond {@link #FIXED_WALL_SPRITE_ID}) the per-level allocator may pick for
     * the bulk wall symbol 'x' on GENERATED levels, so two seeds no longer share one base wall — the fix
     * for "every level looks the same". Each carries a distinct FACILITY IDENTITY (material + hue) and is
     * LIGHT, not gray: "wall_plain_sandstone" (a warm sandstone-block facility) and "wall_plain_clean" (a
     * bright clinical-lab facility). Comma-separated stable sprite ids, all registered in the WALL pool
     * with their own procedural texture generators (and listed in
     * {@code TilesetRegistries.VARIETY_ONLY_SPRITE_IDS}, since no legacy symbol maps to them). Together
     * with {@link #FIXED_WALL_SPRITE_ID} these form the ordered base-wall candidate list the allocator
     * rolls against. Adding another base wall is: register the sprite + generator, then append its id here.
     * Hand-crafted / legacy levels ignore this list entirely and keep {@link #FIXED_WALL_SPRITE_ID}.
     */
    public static final String BASE_WALL_ALTERNATE_SPRITE_IDS = "wall_plain_sandstone,wall_plain_clean";

    // ── FLEXIBLE slots (category fixed, sprite assigned per level by the allocator, order-4) ──────
    /**
     * Reassignable WALL accent symbols — every {@code Level.isWall} char EXCEPT the plain 'x'. Includes
     * the three STELLAR_OBSERVATORY walls ('"' viewport, ''' magrail, '`' hull plate); the allocator
     * keeps room-contained art in its room via the sprite theme tags, not via this budget.
     */
    public static final String FLEXIBLE_WALL_SYMBOLS      = "cvtwhjGkNQSMZUXDF\"'`";

    /** Reassignable COLUMN symbol: 'P'. Its MEANING (column) is fixed; the filling sprite is flexible. */
    public static final String FLEXIBLE_COLUMN_SYMBOLS    = "P";

    /**
     * Reassignable SOLID_PROP decoration symbols — every {@code Level.isPropSolid} char EXCEPT the
     * fixed-sprite exceptions in {@link #FIXED_SPRITE_SYMBOLS} (the shop '@'). Includes the four
     * STELLAR_OBSERVATORY props (';' '\' '|' '<'), kept in-room by theme tags at allocation time.
     */
    public static final String FLEXIBLE_SOLIDPROP_SYMBOLS = "gETLC#%&=IJW;\\|<";

    /**
     * Reassignable FLOOR_DECAL decoration symbols — the "pure" decals ({@code Level.isPropDecal} minus
     * the FIXED pickup / hazard / stairs symbols, which are not sprites in the registry). Includes the
     * three STELLAR_OBSERVATORY decals ('?' ']' '-').
     */
    public static final String FLEXIBLE_DECAL_SYMBOLS     = "ms.Oe?]-";

    // ── FIXED-SPRITE exceptions (flexible-looking category, but a permanent gameplay-bound sprite) ──
    // These chars live in a FLEXIBLE category yet MUST keep one fixed sprite because they carry hard
    // gameplay meaning the allocator must never repaint. Listed explicitly, char-for-char aligned with
    // FIXED_SPRITE_IDS below (same index = same char/sprite pair).
    /**
     * Symbols in a flexible category that stay bound to one fixed sprite. Today: the shop '@', which
     * {@code World.placeMachine} stamps solid and which must always render as the vendor machine.
     */
    public static final String FIXED_SPRITE_SYMBOLS  = "@";
    /** Sprite ids for {@link #FIXED_SPRITE_SYMBOLS}, aligned by index (comma-separated). */
    public static final String FIXED_SPRITE_IDS      = "prop_vendor";

    // ── Reserved for later orders ────────────────────────────────────────────────────────────────
    /**
     * Salt mixed into the per-level palette seed derivation (order-4). Reserved here so it lives with
     * the other tileset constants; unused until the allocator lands. An arbitrary fixed constant —
     * changing it reshuffles every level's flexible-sprite assignment, so it must stay stable once the
     * allocator ships.
     */
    public static final long PALETTE_SEED_SALT = 0x7011_5E7_ADA_C0DEL;
}
