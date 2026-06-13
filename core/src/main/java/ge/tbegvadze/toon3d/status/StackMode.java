package ge.tbegvadze.toon3d.status;

/** Determines how re-applying an already-active status effect behaves. */
public enum StackMode {
    /** Each new application adds +1 stack; remainingTurns refreshes to the longer value. Used by Poison. */
    STACK_MAGNITUDE,
    /** Re-applying refreshes the timer to the longer of the two; takes the higher magnitude. Used by Burning, Empowered. */
    REFRESH_DURATION,
    /** New application only wins if its duration exceeds what remains. Used by Stun, Blind, Slow. */
    REPLACE_IF_LONGER
}
