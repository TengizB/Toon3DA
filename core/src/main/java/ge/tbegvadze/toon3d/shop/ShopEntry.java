package ge.tbegvadze.toon3d.shop;

/**
 * One purchasable line on a vending machine's shelf (shop_order_2).
 *
 * <p>Immutable except for {@link #soldOut}: the machine never restocks, so a bought entry is marked
 * sold and stays sold for the floor's lifetime. The concrete PURCHASE EFFECT is applied by parts
 * 3-4 via a {@link ShopEffectApplier}; this class only carries the display + pricing + payload data.
 *
 * <p>{@code payload} is the typed handle the effect applier consumes (a {@code WeaponProfile} for
 * weapon upgrades, an {@code AmmoType} for ammo, an {@code ItemType} for medkits, a boon id for
 * abilities). {@code quantity} carries the amount for stackable offers (ammo box size; 1 for a
 * medkit; unused for weapon/ability offers).
 */
public final class ShopEntry {

    public final OfferCategory category;
    public final OfferRarity   rarity;
    public final String        displayName;
    public final String        description;
    public final int           price;       // Credits, fixed at roll time
    public final int           quantity;    // ammo box size / 1 for medkit / unused otherwise
    public final Object        payload;     // typed handle for the effect applier (parts 3-4)
    /** Identity used to reject stocking the same concrete offer twice on one machine. */
    public final String        dedupKey;

    private boolean soldOut = false;

    public ShopEntry(OfferCategory category, OfferRarity rarity, String displayName,
                     String description, int price, int quantity, Object payload, String dedupKey) {
        this.category    = category;
        this.rarity      = rarity;
        this.displayName = displayName;
        this.description = description;
        this.price       = price;
        this.quantity    = quantity;
        this.payload     = payload;
        this.dedupKey    = dedupKey;
    }

    public boolean isSoldOut() { return soldOut; }

    /** Marks the entry sold after a successful purchase (no restock). */
    public void markSoldOut() { this.soldOut = true; }
}
