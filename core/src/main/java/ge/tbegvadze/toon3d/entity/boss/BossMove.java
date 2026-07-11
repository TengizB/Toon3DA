package ge.tbegvadze.toon3d.entity.boss;

import ge.tbegvadze.toon3d.enemy.EnemyType;

/**
 * A single action produced by a BossAttackPattern for one game turn.
 * The boss acts exactly once per player-action tick — this is the result of that one action.
 *
 * Factory methods cover every move kind; the payload fields are only meaningful
 * for the kinds that use them (see each factory method's javadoc).
 */
public final class BossMove {

    public enum Kind {
        /** Boss telegraphs upcoming area-damage tiles; marks the DangerTileSet this turn. */
        TELEGRAPH,
        /** Resolves the active DangerTileSet — deals tile damage, clears marks. */
        RESOLVE,
        /** Boss moves one tile toward or away from the player (per-boss logic in BossFloorController). */
        REPOSITION,
        /**
         * Boss relocates MULTIPLE tiles in one turn toward an absolute target tile, path-stepping
         * through free cells (ORDER 2). The move is visibly animated as a slide across the crossed
         * tiles — never a teleport (fairness contract F3). Capped at BOSS_MAX_DASH_TILES.
         */
        DASH,
        /** Boss summons minion enemies adjacent to itself. */
        SUMMON,
        /** Boss strikes an adjacent player directly (non-telegraphed melee). */
        MELEE,
        /** Placeholder emitted during phase-transition invulnerability. */
        TRANSITION,
        /** No action this turn. */
        NONE
    }

    public final Kind      kind;
    /** For TELEGRAPH/MELEE: damage dealt when tiles resolve or on direct strike. */
    public final int       tileDamage;
    /** For SUMMON: how many minions to attempt to spawn. */
    public final int       summonCount;
    /** For SUMMON: which enemy archetype to spawn. */
    public final EnemyType summonType;

    /** For DASH: the absolute target tile column the boss wants to reach this turn. */
    public final int       dashTargetColumn;
    /** For DASH: the absolute target tile row the boss wants to reach this turn. */
    public final int       dashTargetRow;

    private BossMove(Kind kind, int tileDamage, int summonCount, EnemyType summonType,
                     int dashTargetColumn, int dashTargetRow) {
        this.kind             = kind;
        this.tileDamage       = tileDamage;
        this.summonCount      = summonCount;
        this.summonType       = summonType;
        this.dashTargetColumn = dashTargetColumn;
        this.dashTargetRow    = dashTargetRow;
    }

    /** Boss telegraphs danger tiles carrying the given damage value. */
    public static BossMove telegraph(int damage) {
        return new BossMove(Kind.TELEGRAPH, damage, 0, null, 0, 0);
    }

    /** Resolves the previously armed DangerTileSet. */
    public static BossMove resolve() {
        return new BossMove(Kind.RESOLVE, 0, 0, null, 0, 0);
    }

    /** Boss repositions one tile (direction decided by BossFloorController). */
    public static BossMove reposition() {
        return new BossMove(Kind.REPOSITION, 0, 0, null, 0, 0);
    }

    /**
     * Boss dashes toward the absolute target tile (ORDER 2), path-stepping up to BOSS_MAX_DASH_TILES
     * cardinal tiles and animating a visible slide across them. BossFloorController resolves the exact
     * reachable path and stops at the first blocked tile.
     */
    public static BossMove dash(int targetColumn, int targetRow) {
        return new BossMove(Kind.DASH, 0, 0, null, targetColumn, targetRow);
    }

    /** Boss summons the given count of the given enemy type near itself. */
    public static BossMove summon(EnemyType type, int count) {
        return new BossMove(Kind.SUMMON, 0, count, type, 0, 0);
    }

    /** Boss strikes the player directly for the given damage (only if adjacent). */
    public static BossMove melee(int damage) {
        return new BossMove(Kind.MELEE, damage, 0, null, 0, 0);
    }

    /** Phase-transition placeholder — boss is invulnerable this turn. */
    public static BossMove transition() {
        return new BossMove(Kind.TRANSITION, 0, 0, null, 0, 0);
    }

    /** Boss takes no action this turn. */
    public static BossMove none() {
        return new BossMove(Kind.NONE, 0, 0, null, 0, 0);
    }
}
