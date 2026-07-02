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

    /**
     * Facility-clock timestamp when the last purchase kicked off the one-shot DISPENSE animation
     * (shop_order_6). Starts far in the past so the machine is never "dispensing" before its first
     * sale. Set via {@link #triggerDispense(float)}; read by ShopMachineRenderer.
     */
    public float dispenseStartSeconds = Float.NEGATIVE_INFINITY;

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

    /** True when at least one entry is sold but the shelf is not yet empty (LOW-STOCK visual state). */
    public boolean isLowStock() {
        return stock != null && !stock.isDepleted() && stock.soldCount() > 0;
    }

    /** Starts the one-shot dispense animation; {@code nowSeconds} is the shared facility clock. */
    public void triggerDispense(float nowSeconds) {
        this.dispenseStartSeconds = nowSeconds;
    }

    /**
     * Fraction (0..1) through the current dispense animation, or a value {@code >= 1} once it has
     * finished. {@code nowSeconds} is the shared facility clock; {@code durationSeconds} the length.
     */
    public float dispenseProgress(float nowSeconds, float durationSeconds) {
        if (durationSeconds <= 0f) return 1f;
        return (nowSeconds - dispenseStartSeconds) / durationSeconds;
    }

    /** True if the given tile is the tile this machine occupies. */
    public boolean isAtTile(int column, int row) {
        return column == tileColumn && row == tileRow;
    }
}
