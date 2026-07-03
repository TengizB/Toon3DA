package ge.tbegvadze.toon3d.enemy;

/**
 * The category of action an enemy has COMMITTED to perform on its next turn.
 *
 * <p>This is the spine of the pre-committed intent model (strategy-combat-order-1): at the end of
 * every enemy turn each alerted enemy decides its next action from the resolved board and stores it
 * as a {@link PlannedAction}. The player reads that intent during their own turn and can plan a
 * counter. The following enemy turn EXECUTES the committed verb (with fairness re-validation), then
 * commits the next one — "decide-ahead, show, then execute."
 *
 * <p>On-screen icon rendering for these verbs is owned by strategy-combat-order-2; the DEFEND and
 * SPECIAL verbs are placeholders consumed by later docs (order-3 / order-5). This layer only
 * produces the intent — it draws nothing.
 */
public enum IntentVerb {
    /** Will bite/smash the player next turn (must still be cardinally adjacent to connect). */
    ATTACK_MELEE,
    /** Will fire down a cardinal line next turn (must still share the player's line to connect). */
    ATTACK_RANGED,
    /** Telegraphing a charge/big attack that lands in one or more turns (Shell Brute rush). */
    WIND_UP,
    /** Will step toward the player (or a flank tile); deals no damage this turn. */
    MOVE,
    /** Will gain Block and not attack. Reserved for the Block mechanic (order-3); unused here. */
    DEFEND,
    /** Will use an archetype ability (buff/summon/area). Reserved for move-sets (order-5); unused here. */
    SPECIAL,
    /** In range of nothing useful and no worthwhile move; holds position. */
    WAIT,
    /** Cannot act (stunned); the committed action is cancelled and shown greyed (order-2). */
    STUNNED
}
