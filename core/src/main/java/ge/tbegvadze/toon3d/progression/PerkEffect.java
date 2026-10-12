package ge.tbegvadze.toon3d.progression;

/**
 * One typed effect of a {@link Perk} or {@link Keystone} row (balance-overhaul order 5): a
 * {@link PerkModifier} kind plus its magnitude as DATA. Two shapes:
 * <ul>
 *   <li>{@link #byRank} — a TOTAL magnitude per rank (index 0 = rank 1), so a perk's ladder may grow
 *       evenly (0.15 / 0.30 / 0.45) or escalate (Hardy) without any per-rank code;</li>
 *   <li>{@link #fromRank} — one fixed magnitude unlocked at a given rank and kept above it (Point Man's
 *       r2 knockback, Shield Wall's r2 heavy guard).</li>
 * </ul>
 * Immutable; the arrays are copied in.
 */
public final class PerkEffect {

    public final PerkModifier kind;
    public final int          fromRank;
    private final float[]     totalByRank;

    private PerkEffect(PerkModifier kind, int fromRank, float[] totalByRank) {
        this.kind        = kind;
        this.fromRank    = Math.max(1, fromRank);
        this.totalByRank = totalByRank.clone();
    }

    /** An effect whose TOTAL magnitude at rank r is {@code totals[r - 1]} (ranks past the array keep the last). */
    public static PerkEffect byRank(PerkModifier kind, float... totals) {
        return new PerkEffect(kind, 1, totals);
    }

    /** A fixed magnitude unlocked at {@code rank} and held at every higher rank. */
    public static PerkEffect fromRank(PerkModifier kind, int rank, float magnitude) {
        return new PerkEffect(kind, rank, new float[]{magnitude});
    }

    /** A boolean or rank-1 effect (keystones). */
    public static PerkEffect of(PerkModifier kind, float magnitude) {
        return new PerkEffect(kind, 1, new float[]{magnitude});
    }

    /** The total magnitude at a rank, or 0 when the effect is not yet unlocked. */
    public float magnitudeAt(int rank) {
        if (rank < fromRank || totalByRank.length == 0) return 0f;
        int index = Math.min(totalByRank.length, Math.max(1, rank - fromRank + 1)) - 1;
        return totalByRank[index];
    }

    /** Folds this effect at the given rank into the aggregate (nothing below fromRank). */
    void applyAt(BuildStats stats, int rank) {
        if (rank < fromRank) return;
        kind.apply(stats, magnitudeAt(rank));
    }
}
