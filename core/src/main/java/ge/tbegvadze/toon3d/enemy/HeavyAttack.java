package ge.tbegvadze.toon3d.enemy;

import ge.tbegvadze.toon3d.util.BalanceConfig;
import ge.tbegvadze.toon3d.util.EnemyConstants;
import ge.tbegvadze.toon3d.util.GameMath;

/**
 * The HEAVY flag as data (balance-overhaul order 4, H1): the existing commit-then-resolve attacks that
 * are sized as a fraction of the ON-CURVE player's max HP (so they must be avoided, not tanked).
 * Each constant carries its own numbers — nothing switches on it. An archetype or special ability that
 * resolves one reports it through {@link EnemyType#heavyWindUp()} / {@link SpecialAbility#heavyAttack()}.
 */
public enum HeavyAttack {

    /** A WIND_UP rush / lunge down a locked cardinal lane (Shell Brute today). */
    CHARGE("Charge", BalanceConfig.HEAVY_DAMAGE_FRACTION, 1,
            DangerShape.LANE, EnemyConstants.SHELL_BRUTE_CHARGE_TRIGGER_MAX_TILES + 1),

    /** The Rimeshell Lancer's piercing lance down a locked lane. */
    LANCE("Lance", BalanceConfig.HEAVY_DAMAGE_FRACTION, 1,
            DangerShape.LANE, EnemyConstants.RIMESHELL_LANCER_RANGE_TILES),

    /** A slam on every tile within {@code EnemyConstants.AREA_STRIKE_RADIUS_TILES} (square) of the caster. */
    AREA_STRIKE("Area strike", BalanceConfig.HEAVY_AREA_STRIKE_DAMAGE_FRACTION, 1,
            DangerShape.SQUARE, EnemyConstants.AREA_STRIKE_RADIUS_TILES),

    /** The Plague Hulk's wall-stopped cross blast after its brace countdown. */
    SELF_DESTRUCT("Self-destruct", BalanceConfig.HEAVY_SELF_DESTRUCT_DAMAGE_FRACTION,
            BalanceConfig.PLAGUE_HULK_SELF_DESTRUCT_BRACE_TURNS,
            DangerShape.CROSS, EnemyConstants.PLAGUE_HULK_SELF_DESTRUCT_BLAST_RADIUS_TILES);

    /** The footprint family of a heavy's danger tiles (H3): which GameMath builder lists them. */
    public enum DangerShape {
        /** Tiles 1..reach along a locked cardinal direction, stopping before the first blocker. */
        LANE {
            @Override public int build(int originColumn, int originRow, int stepColumn, int stepRow, int reach,
                                       GameMath.TileBlockedQuery blocked, int[] outColumns, int[] outRows,
                                       int outCapacity) {
                return GameMath.dangerTilesForLane(originColumn, originRow, stepColumn, stepRow, reach,
                        blocked, outColumns, outRows, outCapacity);
            }
        },
        /** Every open tile within Chebyshev radius of the caster, excluding the caster. */
        SQUARE {
            @Override public int build(int originColumn, int originRow, int stepColumn, int stepRow, int reach,
                                       GameMath.TileBlockedQuery blocked, int[] outColumns, int[] outRows,
                                       int outCapacity) {
                return GameMath.dangerTilesForSquare(originColumn, originRow, reach, blocked,
                        outColumns, outRows, outCapacity);
            }
        },
        /** The caster's tile plus four cardinal arms out to the radius, each stopping at a wall. */
        CROSS {
            @Override public int build(int originColumn, int originRow, int stepColumn, int stepRow, int reach,
                                       GameMath.TileBlockedQuery blocked, int[] outColumns, int[] outRows,
                                       int outCapacity) {
                return GameMath.dangerTilesForCross(originColumn, originRow, reach, blocked,
                        outColumns, outRows, outCapacity);
            }
        };

        /**
         * Writes this shape's danger tiles into the caller's buffers and returns the count. The step is
         * used only by LANE; reach is the lane length or the radius.
         */
        public abstract int build(int originColumn, int originRow, int stepColumn, int stepRow, int reach,
                                  GameMath.TileBlockedQuery blocked, int[] outColumns, int[] outRows,
                                  int outCapacity);
    }

    private final String displayName;
    private final float  damageFraction;
    private final int    minimumCommitTurns;
    private final DangerShape dangerShape;
    private final int    dangerReachTiles;

    HeavyAttack(String displayName, float damageFraction, int minimumCommitTurns,
                DangerShape dangerShape, int dangerReachTiles) {
        this.dangerShape        = dangerShape;
        this.dangerReachTiles   = dangerReachTiles;
        this.displayName        = displayName;
        this.damageFraction     = damageFraction;
        this.minimumCommitTurns = minimumCommitTurns;
    }

    /** Fraction of the on-curve player's max HP an unguarded, unarmoured hit deals (before HEAVY_DAMAGE_SCALE). */
    public float damageFraction() { return damageFraction; }

    /**
     * Full player turns between the commit (intent shown) and the resolution — what the code actually
     * guarantees: 1 for a rush / lance / slam, the brace countdown for the self-destruct. Always >= 1 (H3).
     */
    public int minimumCommitTurns() { return minimumCommitTurns; }

    /** The footprint family of this heavy's danger tiles. */
    public DangerShape dangerShape() { return dangerShape; }

    /** Lane length (LANE) or radius (SQUARE / CROSS) in tiles; always >= 1. */
    public int dangerReachTiles() { return dangerReachTiles; }

    /** Short name for reports and tables. */
    public String displayName() { return displayName; }
}
