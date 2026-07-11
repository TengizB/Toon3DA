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

    /** Which terrain hazard a CAST_HAZARD move seeds when it resolves (ORDER 3). */
    public enum HazardKind {
        /** Short-lived burning lane/patch — HazardManager.igniteFire per tile. */
        FIRE,
        /** Wider, longer-lingering toxic cloud — HazardManager.igniteToxic per tile. */
        TOXIC
    }

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
        /**
         * Boss LUNGES several tiles along a cardinal axis, resolving the armed corridor DangerTileSet
         * (the telegraphed hit) and then sliding to the far end of the now-cleared lane (ORDER 3). The
         * signature "hit then flee" opener. Forces a one-turn RECOVERY plant afterwards so the player
         * always gets a punish window (fairness F1/F2).
         */
        CHARGE,
        /**
         * Boss seeds a terrain hazard (fire or toxic) on the tiles armed by the previous turn's
         * telegraph (ORDER 3). Places the hazard via HazardManager on each armed tile — it is NOT a
         * direct hit; the hazard system then owns the lingering damage-over-time.
         */
        CAST_HAZARD,
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
    /** For SUMMON: hard ceiling on live minions — the summon no-ops once this many are already alive. */
    public final int       summonCap;

    /** For DASH: the absolute target tile column the boss wants to reach this turn. */
    public final int       dashTargetColumn;
    /** For DASH: the absolute target tile row the boss wants to reach this turn. */
    public final int       dashTargetRow;

    /** For CHARGE: the cardinal lunge axis, column component (-1, 0 or +1). */
    public final int       chargeDirectionColumn;
    /** For CHARGE: the cardinal lunge axis, row component (-1, 0 or +1). */
    public final int       chargeDirectionRow;
    /** For CHARGE: how many tiles the boss may slide along the axis (clamped to the first blocked tile). */
    public final int       chargeRangeTiles;

    /** For CAST_HAZARD: which terrain hazard to seed on the armed tiles. */
    public final HazardKind hazardKind;

    private BossMove(Kind kind, int tileDamage, int summonCount, EnemyType summonType, int summonCap,
                     int dashTargetColumn, int dashTargetRow,
                     int chargeDirectionColumn, int chargeDirectionRow, int chargeRangeTiles,
                     HazardKind hazardKind) {
        this.kind                  = kind;
        this.tileDamage            = tileDamage;
        this.summonCount           = summonCount;
        this.summonType            = summonType;
        this.summonCap             = summonCap;
        this.dashTargetColumn      = dashTargetColumn;
        this.dashTargetRow         = dashTargetRow;
        this.chargeDirectionColumn = chargeDirectionColumn;
        this.chargeDirectionRow    = chargeDirectionRow;
        this.chargeRangeTiles      = chargeRangeTiles;
        this.hazardKind            = hazardKind;
    }

    /** Boss telegraphs danger tiles carrying the given damage value. */
    public static BossMove telegraph(int damage) {
        return new BossMove(Kind.TELEGRAPH, damage, 0, null, 0, 0, 0, 0, 0, 0, null);
    }

    /** Resolves the previously armed DangerTileSet. */
    public static BossMove resolve() {
        return new BossMove(Kind.RESOLVE, 0, 0, null, 0, 0, 0, 0, 0, 0, null);
    }

    /** Boss repositions one tile (direction decided by BossFloorController). */
    public static BossMove reposition() {
        return new BossMove(Kind.REPOSITION, 0, 0, null, 0, 0, 0, 0, 0, 0, null);
    }

    /**
     * Boss dashes toward the absolute target tile (ORDER 2), path-stepping up to BOSS_MAX_DASH_TILES
     * cardinal tiles and animating a visible slide across them. BossFloorController resolves the exact
     * reachable path and stops at the first blocked tile.
     */
    public static BossMove dash(int targetColumn, int targetRow) {
        return new BossMove(Kind.DASH, 0, 0, null, 0, targetColumn, targetRow, 0, 0, 0, null);
    }

    /**
     * Boss LUNGES along a cardinal axis (ORDER 3). The previous turn's TELEGRAPH must have armed the
     * boss's DangerTileSet with the corridor tiles + damage; this move resolves that hit (any player on
     * a marked tile is struck) and then slides the boss up to {@code rangeTiles} along
     * ({@code directionColumn}, {@code directionRow}), stopping at the first blocked tile.
     * BossFloorController forces a one-turn recovery plant after the lunge (fairness F1/F2).
     */
    public static BossMove charge(int directionColumn, int directionRow, int rangeTiles) {
        return new BossMove(Kind.CHARGE, 0, 0, null, 0, 0, 0,
                directionColumn, directionRow, rangeTiles, null);
    }

    /**
     * Boss seeds a terrain hazard on the tiles armed by the previous turn's TELEGRAPH (ORDER 3). The
     * controller places {@code hazardKind} via HazardManager on every currently-armed DangerTileSet tile
     * so the placed hazard exactly matches the telegraph, then clears the marks. Not a direct hit — the
     * hazard system owns the lingering damage.
     */
    public static BossMove castHazard(HazardKind hazardKind) {
        return new BossMove(Kind.CAST_HAZARD, 0, 0, null, 0, 0, 0, 0, 0, 0, hazardKind);
    }

    /**
     * Boss summons the given count of the given enemy type near itself, no-oping once {@code cap} live
     * minions already exist (fairness F5 — the room never floods).
     */
    public static BossMove summon(EnemyType type, int count, int cap) {
        return new BossMove(Kind.SUMMON, 0, count, type, cap, 0, 0, 0, 0, 0, null);
    }

    /** Boss strikes the player directly for the given damage (only if adjacent). */
    public static BossMove melee(int damage) {
        return new BossMove(Kind.MELEE, damage, 0, null, 0, 0, 0, 0, 0, 0, null);
    }

    /** Phase-transition placeholder — boss is invulnerable this turn. */
    public static BossMove transition() {
        return new BossMove(Kind.TRANSITION, 0, 0, null, 0, 0, 0, 0, 0, 0, null);
    }

    /** Boss takes no action this turn. */
    public static BossMove none() {
        return new BossMove(Kind.NONE, 0, 0, null, 0, 0, 0, 0, 0, 0, null);
    }
}
