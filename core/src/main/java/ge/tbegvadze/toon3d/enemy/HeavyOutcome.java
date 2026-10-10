package ge.tbegvadze.toon3d.enemy;

/**
 * How one HEAVY attack that was committed AT the player ended (balance-overhaul order 4, CP7b).
 *
 * <p>Every committed-at-player heavy resolves to exactly one of these — the balance simulator's S-HEAVY
 * band is built on that invariant. Pure observation data: nothing in the game reads it.
 */
public enum HeavyOutcome {

    /** The heavy resolved and the player was not hit (they left the danger tiles). */
    AVOIDED(true),

    /** The heavy landed but in the guarding player's front arc (reduced hit, riposte). */
    GUARDED(true),

    /** The heavy was cancelled before it resolved — stagger, riposte stun or status stun. */
    STAGGERED(true),

    /** The source died before the heavy resolved (shot dead, burned down, hazard). */
    KILLED_FIRST(true),

    /** The heavy hit the player unguarded, or from the side or back. */
    LANDED(false);

    private final boolean success;

    HeavyOutcome(boolean success) {
        this.success = success;
    }

    /** True when the player answered the heavy (everything except LANDED) — S-HEAVY's numerator. */
    public boolean isSuccess() {
        return success;
    }
}
