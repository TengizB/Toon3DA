package ge.tbegvadze.toon3d.tileset;

/**
 * Whether a level-file symbol's SPRITE may be reassigned per level. This is the safety boundary of the
 * whole symbol/sprite-reuse migration (order-2): the allocator (order-4) may only vary the art of a
 * {@link #FLEXIBLE} symbol and must never touch a {@link #FIXED} one.
 *
 * <p>{@link #FIXED} means permanent meaning AND sprite (player start, exit, doors, keycards, pickups,
 * ammo, hazards, enemy spawns, the plain wall 'x', and gameplay-bound props like the shop '@').
 * {@link #FLEXIBLE} means the category/meaning is still fixed but the filling sprite is chosen per
 * level from the category's pool. Any symbol not explicitly declared flexible is treated as FIXED —
 * the fail-safe direction (see {@link SymbolBudget}). Headless — no LibGDX.
 */
public enum SymbolClass {

    /** Permanent meaning AND sprite; the allocator must never reassign it. The fail-safe default. */
    FIXED,

    /** Fixed category/meaning, but the filling sprite is assigned per level from the category pool. */
    FLEXIBLE
}
