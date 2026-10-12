package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.BossBalance;
import ge.tbegvadze.toon3d.util.GameMath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * THE BOSS TABLE (balance-overhaul order 6, F3): which boss fights on which boss floor of the finite run,
 * and the two semantic dials its stats are derived from — the RULE-1 target fight length and the RULE-3
 * survival ratio. One {@link #register(Row)} row per boss floor; {@code util/BossBalance} reads the row for a
 * depth instead of the old modulo rotation, and every R-BOSS-* rule audits every row.
 *
 * <p>The default table is the old cycle made explicit (Overseer 5, Corruptor 10, Hell Baron 15, Overseer 20,
 * Corruptor 25), so the Core-ending boss is unchanged. A new boss floor — or, later, a dedicated final boss —
 * is one row here plus its numbers in BalanceConfig SECTION 25.
 *
 * <p>Headless: no LibGDX.
 */
public final class BossTable {

    /** One boss floor: its depth, its archetype and the two dials its stats derive from. */
    public static final class Row {
        public final int                   depth;
        public final BossBalance.Archetype archetype;
        public final float                 targetFightTurns;
        public final float                 survivalRatio;

        public Row(int depth, BossBalance.Archetype archetype, float targetFightTurns, float survivalRatio) {
            if (!GameMath.isBossFloor(depth)) {
                throw new IllegalArgumentException("BossTable row at depth " + depth + " is not a boss floor");
            }
            this.depth            = depth;
            this.archetype        = archetype;
            this.targetFightTurns = targetFightTurns;
            this.survivalRatio    = survivalRatio;
        }
    }

    private static BossTable standard;

    private final List<Row> rows = new ArrayList<>();

    /** The shipped table (lazily bootstrapped once; the table is immutable data after that). */
    public static synchronized BossTable standard() {
        if (standard == null) {
            BossTable table = new BossTable();
            bootstrap(table);
            standard = table;
        }
        return standard;
    }

    /** Registers the five shipped rows (F3) — index-aligned with the SECTION 25 BOSS_TABLE_* arrays. */
    static void bootstrap(BossTable table) {
        BossBalance.Archetype[] archetypes = {
                BossBalance.Archetype.OVERSEER, BossBalance.Archetype.CORRUPTOR, BossBalance.Archetype.HELL_BARON,
                BossBalance.Archetype.OVERSEER, BossBalance.Archetype.CORRUPTOR};
        for (int rowIndex = 0; rowIndex < BalanceConfig.BOSS_TABLE_DEPTHS.length; rowIndex++) {
            table.register(new Row(BalanceConfig.BOSS_TABLE_DEPTHS[rowIndex], archetypes[rowIndex],
                    BalanceConfig.BOSS_TABLE_TARGET_FIGHT_TURNS[rowIndex],
                    BalanceConfig.BOSS_TABLE_SURVIVAL_RATIO[rowIndex]));
        }
    }

    /** Adds a row; rows are kept in ascending depth and a depth may appear only once. */
    public void register(Row row) {
        for (Row existing : rows) {
            if (existing.depth == row.depth) {
                throw new IllegalArgumentException("BossTable already has a row at depth " + row.depth);
            }
        }
        int insertAt = 0;
        while (insertAt < rows.size() && rows.get(insertAt).depth < row.depth) insertAt++;
        rows.add(insertAt, row);
    }

    /** Every row, shallowest first. Unmodifiable. */
    public List<Row> rows() {
        return Collections.unmodifiableList(rows);
    }

    /**
     * The row that governs a boss at {@code depth}: the exact row, else the deepest row above it (a boss
     * the debug jump or a route-map-less world spawns off-table re-derives from the nearest real row's
     * archetype and dials, at its own depth), else the first row.
     */
    public Row rowForDepth(int depth) {
        Row governing = rows.get(0);
        for (Row row : rows) {
            if (row.depth <= depth) governing = row;
        }
        return governing;
    }

    /** The shallowest row an archetype fights on — its CANONICAL row (the EnemyType reference stats). */
    public Row firstRowFor(BossBalance.Archetype archetype) {
        for (Row row : rows) {
            if (row.archetype == archetype) return row;
        }
        throw new IllegalArgumentException("No BossTable row for " + archetype);
    }
}
