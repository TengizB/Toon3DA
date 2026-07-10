package ge.tbegvadze.toon3d.route;

/**
 * A SUPPLY CACHE's request for owned-ammo boxes on its floor (route-map order-8). Unlike an ordinary
 * {@link GuaranteedContent} — which is fully resolved inside a {@link NodeLevelProfile} that has no
 * view of the player's arsenal — an ammo cache must know which ammo the player actually USES so it
 * never dumps useless boxes. Owned-weapon state lives only in {@code World}, so the profile declares
 * this INTENT (how many boxes, where) and {@code World} fulfils it generically after the floor is
 * built, spreading the boxes across the owned ammo types (falling back to all types if the player
 * carries no ammo weapon).
 *
 * <p>Keeping the fulfilment in {@code World} (which legitimately owns the arsenal) rather than in a
 * per-node-type {@code switch} preserves the order-3 seam: any profile may attach an ammo request,
 * and {@code World} handles it without knowing the node type. Immutable value object. Pure / headless
 * — no LibGDX imports.
 */
public final class AmmoCacheRequest {

    private final int boxCount;
    private final Placement placement;

    /**
     * @param boxCount  how many ammo boxes to stamp (values &lt;= 0 make this request a no-op)
     * @param placement where on the floor the boxes are chosen from (e.g. {@link Placement#FARTHEST_ROOM}
     *                  for the deep "cargo bay")
     */
    public AmmoCacheRequest(int boxCount, Placement placement) {
        this.boxCount  = boxCount;
        this.placement = placement;
    }

    /** How many ammo boxes to stamp. */
    public int boxCount() {
        return boxCount;
    }

    /** Where on the floor the boxes are chosen from. */
    public Placement placement() {
        return placement;
    }
}
