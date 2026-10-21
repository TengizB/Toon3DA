package ge.tbegvadze.toon3d.enemy;

import ge.tbegvadze.toon3d.entity.MatchupOutcome;
import ge.tbegvadze.toon3d.status.StatusType;

/**
 * The payload of an {@link EnemyVisualMoment} (generated-enemy-art order 2). ONE instance per
 * {@code EnemyManager}, reset and refilled on every fire, so reporting a moment never allocates.
 * A listener reads it inside the call and never keeps it. Which fields a moment fills is stated by the
 * moment's own data ({@link EnemyVisualMoment#carriesHit}, {@link EnemyVisualMoment#carriesTileMotion});
 * every other field holds its reset value.
 */
public final class EnemyVisualEvent {

    /** How an enemy died — the visual layer may die differently by cause (order 3). */
    public enum DeathCause {
        /** A player weapon hit (direct, splash, arc). */
        WEAPON,
        /** A barrel or relic blast — an activation flagged as a hazard. */
        HAZARD,
        /** A status damage-over-time tick. */
        STATUS,
        /** Its own self-destruct. */
        SELF_DESTRUCT,
        /** Anything else: an ally's lance, a reflected hit. */
        OTHER
    }

    public Enemy             enemy;
    public EnemyVisualMoment moment;

    // DAMAGED
    /** HP actually lost to this hit (0 when Block or a shard ate it). */
    public int            amount;
    /** {@code amount / maxHealth}, in [0, 1] — the visual layer's damage tier reads this. */
    public float          healthFraction;
    public MatchupOutcome outcome = MatchupOutcome.NEUTRAL;
    /** True when a shard absorbed the whole hit (nothing reached the body). */
    public boolean        absorbed;
    /** True when the hit landed but Block swallowed all of it. */
    public boolean        blocked;
    /** Tile the hit came from (the player's tile), or -1 when unknown. */
    public int            sourceColumn;
    public int            sourceRow;

    // MOVE_STARTED / KNOCKED_BACK
    public int fromColumn;
    public int fromRow;
    public int toColumn;
    public int toRow;

    // ABILITY_USED / STATUS_APPLIED / DIED
    public SpecialAbility ability;
    public StatusType     status;
    public DeathCause     deathCause = DeathCause.OTHER;

    /** Clears every field, then names the enemy and the moment. */
    EnemyVisualEvent reset(Enemy subject, EnemyVisualMoment visualMoment) {
        enemy          = subject;
        moment         = visualMoment;
        amount         = 0;
        healthFraction = 0f;
        outcome        = MatchupOutcome.NEUTRAL;
        absorbed       = false;
        blocked        = false;
        sourceColumn   = -1;
        sourceRow      = -1;
        fromColumn     = subject.tileColumn;
        fromRow        = subject.tileRow;
        toColumn       = subject.tileColumn;
        toRow          = subject.tileRow;
        ability        = null;
        status         = null;
        deathCause     = DeathCause.OTHER;
        return this;
    }
}
