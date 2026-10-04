package ge.tbegvadze.toon3d.util;

/**
 * The result of one R-ROLE clear scenario (balance-overhaul order 3): fractional turns to clear the
 * group, fire actions spent, and ammo units spent. Immutable value type produced by
 * {@link GameMath#roleScenarioClear}.
 */
public final class RoleScenarioOutcome {

    /** Turns to clear; the last turn counts only the fraction of it the killing blow needed. */
    public final float turnsToClear;
    /** Fire actions taken (a Railgun charge turn is not a fire action). */
    public final int   actions;
    /** Ammo units spent (actions x ammo per action). */
    public final int   ammoSpent;
    /** False when the group was still standing at the scenario turn cap (the weapon cannot clear it). */
    public final boolean cleared;

    public RoleScenarioOutcome(float turnsToClear, int actions, int ammoSpent, boolean cleared) {
        this.turnsToClear = turnsToClear;
        this.actions      = actions;
        this.ammoSpent    = ammoSpent;
        this.cleared      = cleared;
    }
}
