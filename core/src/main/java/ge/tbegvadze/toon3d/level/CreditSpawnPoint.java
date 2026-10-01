package ge.tbegvadze.toon3d.level;

/**
 * A credit chip the {@link SupplyPlanner} placed (balance-overhaul order 2, S8): its tile and the
 * credits it pays. World turns each into a credit ground item. Immutable.
 */
public final class CreditSpawnPoint {

    public final int tileColumn;
    public final int tileRow;
    public final int amount;

    public CreditSpawnPoint(int tileColumn, int tileRow, int amount) {
        this.tileColumn = tileColumn;
        this.tileRow    = tileRow;
        this.amount     = amount;
    }
}
