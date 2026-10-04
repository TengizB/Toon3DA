package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.enemy.EnemyTrait;
import ge.tbegvadze.toon3d.util.BalanceConfig;

import java.util.Arrays;

/**
 * The MATCHUP CATALOG (balance-overhaul order 3, M3): the place matchup rows are registered — one
 * {@link #register(DamageClass, EnemyTrait, float)} per (class, trait) cell — and frozen into the
 * shared {@link MatchupTable}. Same house idiom as {@code tileset/TilesetRegistries}: a static
 * {@link #bootstrap()} that is idempotent, plus a package-private {@link #registerAll} so a fresh
 * catalog can be populated without touching the shared one.
 *
 * <p>The numbers are NOT here: {@link #registerAll} loops {@code EnemyTrait.values()} x
 * {@code DamageClass.values()} and READS each cell from the {@code BalanceConfig.MATCHUP_ROW_*}
 * arrays (SECTION 22), so the table is tuned in one place. A cell nobody registers stays at the
 * neutral 1.0.
 */
public final class MatchupCatalog {

    /**
     * The SECTION 22 rows in {@link EnemyTrait} ordinal order. Each row is in {@link DamageClass}
     * ordinal order. Lengths are checked by {@link #registerAll}, so adding a trait or a class
     * without widening the table fails loudly at bootstrap rather than mis-reading a column.
     */
    private static final float[][] CONFIGURED_ROWS_BY_TRAIT = {
            BalanceConfig.MATCHUP_ROW_FLESH,
            BalanceConfig.MATCHUP_ROW_SHIELDED,
            BalanceConfig.MATCHUP_ROW_PLATED,
            BalanceConfig.MATCHUP_ROW_BURNABLE,
            BalanceConfig.MATCHUP_ROW_CHITIN,
            BalanceConfig.MATCHUP_ROW_INFERNAL,
    };

    private static final Object LOCK = new Object();
    private static volatile MatchupTable sharedTable;

    private final float[][] rawByTraitAndClass;

    /** A fresh catalog with every cell at the neutral 1.0. */
    public MatchupCatalog() {
        rawByTraitAndClass = new float[EnemyTrait.values().length][DamageClass.values().length];
        for (float[] row : rawByTraitAndClass) {
            Arrays.fill(row, 1f);
        }
    }

    /**
     * Registers one matchup row: hits of {@code damageClass} on a {@code trait} enemy are multiplied
     * by {@code multiplier} (before MATCHUP_STRENGTH). Re-registering a cell replaces it.
     *
     * @throws IllegalArgumentException if {@code multiplier} is not positive
     */
    public MatchupCatalog register(DamageClass damageClass, EnemyTrait trait, float multiplier) {
        if (!(multiplier > 0f)) {
            throw new IllegalArgumentException("Matchup multiplier must be positive: "
                    + damageClass + " x " + trait + " = " + multiplier);
        }
        rawByTraitAndClass[trait.ordinal()][damageClass.ordinal()] = multiplier;
        return this;
    }

    /** Freezes the registered rows into an immutable table at the given strength and thresholds. */
    public MatchupTable build(float strength, float effectiveThreshold, float resistedThreshold) {
        return new MatchupTable(rawByTraitAndClass, strength, effectiveThreshold, resistedThreshold);
    }

    /** Freezes the registered rows at the shipped SECTION 22 strength and thresholds. */
    public MatchupTable build() {
        return build(BalanceConfig.MATCHUP_STRENGTH,
                BalanceConfig.MATCHUP_EFFECTIVE_THRESHOLD,
                BalanceConfig.MATCHUP_RESISTED_THRESHOLD);
    }

    /**
     * Registers all 48 cells (6 traits x 8 classes) from the SECTION 22 rows into {@code catalog}.
     */
    static void registerAll(MatchupCatalog catalog) {
        EnemyTrait[] traits = EnemyTrait.values();
        DamageClass[] damageClasses = DamageClass.values();
        if (CONFIGURED_ROWS_BY_TRAIT.length != traits.length) {
            throw new IllegalStateException("BalanceConfig SECTION 22 has " + CONFIGURED_ROWS_BY_TRAIT.length
                    + " matchup rows but EnemyTrait has " + traits.length + " constants");
        }
        for (EnemyTrait trait : traits) {
            float[] row = CONFIGURED_ROWS_BY_TRAIT[trait.ordinal()];
            if (row.length != damageClasses.length) {
                throw new IllegalStateException("BalanceConfig matchup row for " + trait + " has "
                        + row.length + " columns but DamageClass has " + damageClasses.length);
            }
            for (DamageClass damageClass : damageClasses) {
                catalog.register(damageClass, trait, row[damageClass.ordinal()]);
            }
        }
    }

    /** Builds the shared table if it is not built yet. Idempotent and thread-safe. */
    public static void bootstrap() {
        if (sharedTable != null) {
            return;
        }
        synchronized (LOCK) {
            if (sharedTable == null) {
                MatchupCatalog catalog = new MatchupCatalog();
                registerAll(catalog);
                sharedTable = catalog.build();
            }
        }
    }

    /** Whether {@link #bootstrap()} has run. */
    public static boolean isBootstrapped() {
        return sharedTable != null;
    }

    /** The shared, immutable matchup table; bootstraps on first call (latched). */
    public static MatchupTable shared() {
        MatchupTable table = sharedTable;
        if (table == null) {
            bootstrap();
            table = sharedTable;
        }
        return table;
    }
}
