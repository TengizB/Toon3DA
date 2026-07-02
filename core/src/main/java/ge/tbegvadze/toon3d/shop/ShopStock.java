package ge.tbegvadze.toon3d.shop;

/**
 * A vending machine's fixed shelf (shop_order_2): the 4-6 offers rolled once at floor generation.
 * The array is never resized — parts 5 (UI) and the buy transaction read it directly, and entries
 * flip to sold-out in place (no restock).
 */
public final class ShopStock {

    private final ShopEntry[] entries;

    public ShopStock(ShopEntry[] entries) {
        this.entries = (entries != null) ? entries : new ShopEntry[0];
    }

    public int size() { return entries.length; }

    public ShopEntry get(int index) { return entries[index]; }

    public ShopEntry[] getEntries() { return entries; }

    /** True once every entry has been bought (machine shows as depleted). */
    public boolean isDepleted() {
        for (ShopEntry entry : entries) {
            if (!entry.isSoldOut()) return false;
        }
        return true;
    }

    /** Number of entries already bought — drives the machine's IN-STOCK / LOW visual state (shop_order_6). */
    public int soldCount() {
        int count = 0;
        for (ShopEntry entry : entries) {
            if (entry.isSoldOut()) count++;
        }
        return count;
    }
}
