package ge.tbegvadze.toon3d.enemy;

/**
 * The single action an enemy has committed to perform on its NEXT turn (strategy-combat-order-1).
 *
 * <p>One instance is created per {@link Enemy} at construction and reused for the enemy's whole
 * lifetime — the commit step mutates these fields in place so no allocation ever happens inside
 * {@code EnemyManager.takeTurn()} (project rule: no allocations in the turn loop).
 *
 * <p>{@link #committed} is false until the enemy first commits (a freshly-woken enemy has no plan
 * and does nothing on its wake turn except commit). Once true it stays true; the fields are simply
 * overwritten each commit.
 */
public final class PlannedAction {

    /** False until the first commit. A freshly-woken enemy skips its EXECUTE step while this is false. */
    public boolean committed = false;

    /** The category of the committed action. */
    public IntentVerb verb = IntentVerb.WAIT;

    /** Where a MOVE steps to, or the tile an attack/charge is aimed at (usually the player's tile). */
    public int targetColumn = 0;
    public int targetRow = 0;

    /**
     * True when a MOVE is a retreat: the enemy steps AWAY from {@link #targetColumn}/{@link #targetRow}
     * (a kiting ranged enemy backing off) rather than toward it. False for approach/flank moves.
     */
    public boolean fleeFromTarget = false;

    /**
     * True when a MOVE heads to a FIXED goal tile ({@link #targetColumn}/{@link #targetRow}) — the
     * flanker's blind-side tile — rather than chasing the player's live position. A plain chase MOVE
     * leaves this false so the executor tracks the player where they actually are this turn (and keeps
     * the stuck/wiggle handling). Ignored when {@link #fleeFromTarget} is true.
     */
    public boolean goalIsFixedTile = false;

    /** Damage the player would take if the committed attack lands — the Slay-the-Spire intent number. */
    public int predictedDamage = 0;

    /** Block the enemy would gain from a DEFEND (order-3); unused by this layer. */
    public int blockGain = 0;

    /** Turns of telegraph left before a WIND_UP attack lands; order-2 may render this as a countdown pip. */
    public int windUpTurnsRemaining = 0;

    /** Archetype ability reference for a SPECIAL verb (order-5); null otherwise. */
    public Object abilityRef = null;

    /**
     * Overwrites all fields for a plain (non-attack) verb and marks the plan committed. A MOVE set
     * this way is a live chase toward {@code targetColumn}/{@code targetRow} (the player's tile).
     */
    public void set(IntentVerb verb, int targetColumn, int targetRow) {
        this.committed            = true;
        this.verb                 = verb;
        this.targetColumn         = targetColumn;
        this.targetRow            = targetRow;
        this.fleeFromTarget       = false;
        this.goalIsFixedTile      = false;
        this.predictedDamage      = 0;
        this.blockGain            = 0;
        this.windUpTurnsRemaining = 0;
        this.abilityRef           = null;
    }

    /**
     * Overwrites all fields for a MOVE toward a FIXED goal tile (the flanker's blind-side tile),
     * as opposed to chasing the player's live position. {@link #goalIsFixedTile} is set.
     */
    public void setMoveToTile(int goalColumn, int goalRow) {
        this.committed            = true;
        this.verb                 = IntentVerb.MOVE;
        this.targetColumn         = goalColumn;
        this.targetRow            = goalRow;
        this.fleeFromTarget       = false;
        this.goalIsFixedTile      = true;
        this.predictedDamage      = 0;
        this.blockGain            = 0;
        this.windUpTurnsRemaining = 0;
        this.abilityRef           = null;
    }

    /**
     * Overwrites all fields for a retreat MOVE: the enemy steps AWAY from the given tile (a kiting
     * ranged enemy backing off). {@link #fleeFromTarget} is set so the executor uses stepAway.
     */
    public void setFlee(int awayFromColumn, int awayFromRow) {
        this.committed            = true;
        this.verb                 = IntentVerb.MOVE;
        this.targetColumn         = awayFromColumn;
        this.targetRow            = awayFromRow;
        this.fleeFromTarget       = true;
        this.goalIsFixedTile      = false;
        this.predictedDamage      = 0;
        this.blockGain            = 0;
        this.windUpTurnsRemaining = 0;
        this.abilityRef           = null;
    }

    /** Overwrites all fields for an attack verb carrying a predicted-damage number. */
    public void setAttack(IntentVerb verb, int targetColumn, int targetRow, int predictedDamage) {
        this.committed            = true;
        this.verb                 = verb;
        this.targetColumn         = targetColumn;
        this.targetRow            = targetRow;
        this.fleeFromTarget       = false;
        this.goalIsFixedTile      = false;
        this.predictedDamage      = predictedDamage;
        this.blockGain            = 0;
        this.windUpTurnsRemaining = 0;
        this.abilityRef           = null;
    }

    /** Overwrites all fields for a WIND_UP telegraph carrying its predicted damage and countdown. */
    public void setWindUp(int targetColumn, int targetRow, int predictedDamage, int windUpTurnsRemaining) {
        this.committed            = true;
        this.verb                 = IntentVerb.WIND_UP;
        this.targetColumn         = targetColumn;
        this.targetRow            = targetRow;
        this.fleeFromTarget       = false;
        this.goalIsFixedTile      = false;
        this.predictedDamage      = predictedDamage;
        this.blockGain            = 0;
        this.windUpTurnsRemaining = windUpTurnsRemaining;
        this.abilityRef           = null;
    }
}
