package ge.tbegvadze.toon3d.item;

import ge.tbegvadze.toon3d.util.Constants;

/**
 * Reserve ammo pool for the marine — one int per AmmoType, zero allocations.
 *
 * The pool is pre-sized to AmmoType.values().length at construction and never
 * reallocated. add() clamps to the type's reserveCap; spend() clamps to zero.
 * Weapons draw from this pool when reloading their clip.
 *
 * Starting reserves are set to the AMMO_START_* constants; these are the values
 * at run start before any pickups are collected.
 */
public final class AmmoPool {

    private final int[] reserves;

    public AmmoPool() {
        reserves = new int[AmmoType.values().length];
        reserves[AmmoType.BULLETS.ordinal()] = Constants.AMMO_START_BULLETS;
        reserves[AmmoType.SHELLS.ordinal()]  = Constants.AMMO_START_SHELLS;
        reserves[AmmoType.CELLS.ordinal()]   = Constants.AMMO_START_CELLS;
        reserves[AmmoType.ROCKETS.ordinal()] = Constants.AMMO_START_ROCKETS;
    }

    /**
     * Returns the current reserve for the given ammo type.
     *
     * @param type must not be null
     */
    public int get(AmmoType type) {
        return reserves[type.ordinal()];
    }

    /**
     * Adds up to amount units of the given type, clamping at the reserve cap.
     * Returns the number of units actually absorbed (may be less if near cap).
     *
     * @param type   must not be null
     * @param amount must be >= 1
     */
    public int add(AmmoType type, int amount) {
        if (amount < 1) throw new IllegalArgumentException("amount must be >= 1");
        int current  = reserves[type.ordinal()];
        int absorbed = Math.min(amount, type.getReserveCap() - current);
        if (absorbed > 0) reserves[type.ordinal()] = current + absorbed;
        return absorbed;
    }

    /**
     * Spends up to amount units, clamping at zero.
     * Returns the number of units actually spent (may be less if reserve is low).
     *
     * @param type   must not be null
     * @param amount must be >= 1
     */
    public int spend(AmmoType type, int amount) {
        if (amount < 1) throw new IllegalArgumentException("amount must be >= 1");
        int current = reserves[type.ordinal()];
        int spent   = Math.min(amount, current);
        if (spent > 0) reserves[type.ordinal()] = current - spent;
        return spent;
    }

    /** Returns true when the reserve for the given type is completely empty. */
    public boolean isEmpty(AmmoType type) {
        return reserves[type.ordinal()] == 0;
    }
}
