package ge.tbegvadze.toon3d.enemy;

import ge.tbegvadze.toon3d.util.EnemyConstants;
import ge.tbegvadze.toon3d.util.GameMath;

/**
 * THE HUNTER CLOCK of a HUNTED floor (floor-twists R9): WAITING -> WARNED -> ARRIVED -> DONE, advanced once
 * per COMPLETED player turn by {@link EnemyManager#takeTurn} — turns, never seconds, so a hard-pause overlay
 * (which advances no turn) freezes it. Headless and per-floor: it lives on the floor's EnemyManager and is
 * discarded with it, so leaving the floor in any state ends the hunt (R9a, A9).
 *
 * <p>Arrival is retried every turn from {@code TWIST_HUNTED_ARRIVAL_TURNS} until the spawn is legal: the
 * arrival tile is free and at least {@code TWIST_HUNTED_MIN_SPAWN_DISTANCE_TILES} (Manhattan) from the
 * player. One hunter per floor, never respawned (R9b).
 */
public final class HuntedPursuit {

    public enum State { INACTIVE, WAITING, WARNED, ARRIVED, DONE }

    private State                 state = State.INACTIVE;
    private int                   turnsOnFloor;
    private int                   arrivalColumn;
    private int                   arrivalRow;
    private EnemyType             hunterType;
    private int                   depth;
    private HuntedPursuitListener listener;

    /** Starts the clock on a HUNTED floor. {@code listener} may be null. */
    void begin(EnemyType type, int arrivalTileColumn, int arrivalTileRow, int dungeonDepth,
               HuntedPursuitListener pursuitListener) {
        state         = State.WAITING;
        turnsOnFloor  = 0;
        arrivalColumn = arrivalTileColumn;
        arrivalRow    = arrivalTileRow;
        hunterType    = type;
        depth         = dungeonDepth;
        listener      = pursuitListener;
    }

    public State state() {
        return state;
    }

    public boolean isActive() {
        return state != State.INACTIVE;
    }

    /** Completed player turns counted on this floor. */
    public int turnsOnFloor() {
        return turnsOnFloor;
    }

    /** Called once at the end of every enemy turn (= one completed player turn). */
    void onPlayerTurnCompleted(EnemyManager manager, int playerColumn, int playerRow) {
        if (state == State.INACTIVE || state == State.ARRIVED || state == State.DONE) return;
        turnsOnFloor++;
        int warningTurn = EnemyConstants.TWIST_HUNTED_ARRIVAL_TURNS - EnemyConstants.TWIST_HUNTED_WARNING_TURNS;
        if (state == State.WAITING && turnsOnFloor >= warningTurn) {
            state = State.WARNED;
            if (listener != null) listener.onHunterWarning();
        }
        if (state == State.WARNED && turnsOnFloor >= EnemyConstants.TWIST_HUNTED_ARRIVAL_TURNS) {
            int distance = GameMath.manhattanDistanceTiles(arrivalColumn, arrivalRow, playerColumn, playerRow);
            if (distance < EnemyConstants.TWIST_HUNTED_MIN_SPAWN_DISTANCE_TILES) return;   // retry next turn
            if (!manager.trySpawnTrailHunter(hunterType, arrivalColumn, arrivalRow, depth)) return;
            state = State.ARRIVED;
            if (listener != null) listener.onHunterArrived(arrivalColumn, arrivalRow);
        }
    }

    /** The hunter died (any route). */
    void onHunterKilled(int tileColumn, int tileRow) {
        if (state != State.ARRIVED) return;
        state = State.DONE;
        if (listener != null) listener.onHunterKilled(tileColumn, tileRow);
    }
}
