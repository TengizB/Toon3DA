package ge.tbegvadze.toon3d.shop;

/**
 * A stationary, non-hostile UAC Fabricator vending machine (shop_order_1).
 *
 * <p>The machine occupies one floor tile that is made solid (a '@' vendor prop is stamped into the
 * level grid at placement) so the player cannot stand on it — they interact from an adjacent open
 * tile the machine faces. It is NOT an {@code Enemy}: no HP, no AI, no turn behaviour. Its rolled
 * stock, purchase effects, buy UI, and detailed animated sprite are owned by shop parts 2-6; this
 * class only carries the tile position, facing, and cosmetic animation clock the interaction loop
 * needs.
 */
public final class VendingMachine {

    /** Grid tile the machine occupies (made solid at placement). */
    public final int tileColumn;
    public final int tileRow;

    /** Cardinal step from the machine toward the open tile a player stands on to use it (may be 0,0). */
    public final int facingStepColumn;
    public final int facingStepRow;

    /** Cosmetic idle-animation clock, advanced by the renderer; no gameplay effect. */
    public float animationTimeSeconds = 0f;

    /** The fixed shelf rolled at floor generation (shop_order_2); may be null until rolled. */
    public ShopStock stock = null;

    public VendingMachine(int tileColumn, int tileRow, int facingStepColumn, int facingStepRow) {
        this.tileColumn       = tileColumn;
        this.tileRow          = tileRow;
        this.facingStepColumn = facingStepColumn;
        this.facingStepRow    = facingStepRow;
    }

    /** True once every stock entry is sold (machine shows as depleted). */
    public boolean isDepleted() {
        return stock != null && stock.isDepleted();
    }

    /** True if the given tile is the tile this machine occupies. */
    public boolean isAtTile(int column, int row) {
        return column == tileColumn && row == tileRow;
    }
}
