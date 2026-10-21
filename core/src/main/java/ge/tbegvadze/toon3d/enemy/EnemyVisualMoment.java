package ge.tbegvadze.toon3d.enemy;

/**
 * The moments the simulation reports to the enemy VISUAL layer (generated-enemy-art order 2). Each is
 * fired at the site where the simulation COMMITS it, inside the enemy turn or the player's hit; the
 * visual layer plays the matching clip in real time afterwards.
 *
 * <p>Per-constant data says which part of the reused {@link EnemyVisualEvent} payload a moment fills,
 * so a reader never has to guess (the {@link EnemyVoiceMoment} idiom: an enum the enemy layer can name
 * without importing a renderer). The clip a moment maps to is NOT here — that is the visual layer's
 * rule table, because the simulation must not know what a clip is.
 */
public enum EnemyVisualMoment {

    /** DORMANT -> ALERTED (perception, chain alert, the hunter, a spawned minion). */
    ALERTED(false, false),
    /** A tile step resolved: {@code fromColumn/Row} -> {@code toColumn/Row} (a charge's whole rush is one). */
    MOVE_STARTED(true, false),
    /** A wind-up was committed: the telegraph that is held for the whole player turn. */
    WIND_UP_STARTED(false, false),
    /** A melee swing, ranged shot, charge, lance, sow or blast resolved (whether or not it connected). */
    ATTACK_COMMITTED(false, false),
    /** A special ability resolved: {@code ability} names it. */
    ABILITY_USED(false, false),
    /** A player hit reached it: {@code amount}, {@code healthFraction}, {@code outcome}, {@code absorbed}, source tile. */
    DAMAGED(false, true),
    /** Its next action was cancelled by a stagger or a guard riposte. */
    STAGGERED(false, false),
    /** It was pushed one tile: {@code fromColumn/Row} -> {@code toColumn/Row}. */
    KNOCKED_BACK(true, false),
    /** It braced (DEFENDING) this turn. */
    DEFEND_STARTED(false, false),
    /** A status landed on it: {@code status} names it. */
    STATUS_APPLIED(false, false),
    /** It died, by any route: {@code deathCause} says which. The enemy is removed right after. */
    DIED(false, false);

    /** True when the payload's from/to tile fields are filled. */
    public final boolean carriesTileMotion;
    /** True when the payload's hit fields (amount, fraction, outcome, source) are filled. */
    public final boolean carriesHit;

    EnemyVisualMoment(boolean carriesTileMotion, boolean carriesHit) {
        this.carriesTileMotion = carriesTileMotion;
        this.carriesHit        = carriesHit;
    }

    public static final int COUNT = values().length;
}
