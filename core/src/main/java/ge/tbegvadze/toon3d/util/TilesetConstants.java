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
 *
 * <p>Walls and the column are not in the partition (generated-wall-art order 2): a wall symbol names a SLOT of
 * the floor's region wall set — see {@link #WALL_SLOT_TABLE} and the REGION WALL SETS block below.
 */
public final class TilesetConstants {

    private TilesetConstants() {}

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

    // ── REGION WALL SETS (generated-wall-art order 2) ─────────────────────────────────────────────
    // A wall symbol no longer names a picture: it names a SLOT (group + slot), and the floor's region
    // wall set names the picture. The 21 wall symbols cover the slots exactly (guide section 6.4; the
    // master idea's SYMBOL TABLE). One entry per symbol, ';'-separated: "<symbol> <WallGroup> <WallSlot>".
    // Parsed once by tileset/WallSlotTable. Every symbol stays a WALL (Level.isWall) exactly as before.
    public static final String WALL_SLOT_TABLE =
            "x HALL BASE;k HALL STRUCTURE;S HALL LIGHT;c HALL SERVICE;' HALL VENT;h HALL MARKER;G HALL BREACH;"
          + "M MEDICAL BASE;Q MEDICAL ACCENT;X SECURITY BASE;w SECURITY ACCENT;"
          + "Z CONTAINMENT BASE;N CONTAINMENT ACCENT;U MACHINERY BASE;j MACHINERY ACCENT;"
          + "t RESEARCH BASE;D RESEARCH ACCENT;v STORAGE BASE;\" STORAGE ACCENT;"
          + "F SIGNATURE BASE;` SIGNATURE ACCENT";

    /** The column symbol; its pictures (round / square) come from the floor's region set. */
    public static final char WALL_SET_COLUMN_SYMBOL = 'P';
    /** The four door symbols, in the order of {@link #WALL_SET_DOOR_KIND_TOKENS}. */
    public static final String WALL_SET_DOOR_SYMBOLS = "dRYB";
    /** Door id tokens (door_&lt;letter&gt;_&lt;token&gt;, guide section 12), one per door symbol. */
    public static final String WALL_SET_DOOR_KIND_TOKENS = "plain,red,yellow,blue";
    /** Column id tokens (column_&lt;letter&gt;_&lt;token&gt;), in registry order. */
    public static final String WALL_SET_COLUMN_KIND_TOKENS = "round,square";

    // Region wall set ids = the manifest's "region" value and the file stem under
    // assets/textures/walls/generated/ (<id>.walls.json / .walls.png / .glow.png / .floor.png / .ceiling.png).
    public static final String WALL_SET_OUTER_FACILITY = "outer_facility";
    public static final String WALL_SET_RESEARCH_WING  = "research_wing";
    public static final String WALL_SET_REACTOR_DEPTHS = "reactor_depths";
    public static final String WALL_SET_THE_BREACH     = "the_breach";
    public static final String WALL_SET_THE_CORE       = "the_core";
    /** The set every floor falls back to (start room, hand-made levels, a set that fails to load: R7). */
    public static final String WALL_SET_DEFAULT        = WALL_SET_OUTER_FACILITY;

    /** Where the shipped region set files live (internal asset path, trailing slash). */
    public static final String WALL_SET_ASSET_DIRECTORY = "textures/walls/generated/";

    /**
     * Salt of the level finisher's own random stream (GameMath.wallSetSeed): the placement rules P1-P9
     * never draw from the generator's java.util.Random, so layout, spawns and supply stay untouched (R1).
     */
    public static final long WALL_SET_SEED_SALT = 0x3A11_5E75_EED5_A17EL;
}
