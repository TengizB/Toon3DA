package ge.tbegvadze.toon3d.world;

/** Reason the game tick fired. Passed to TickSubscriber via TickContext. */
public enum TickCause {
    /** Player completed a tile step (W/S/Q/E strafe). */
    MOVE,
    /** Player fired a weapon (SPACE). */
    FIRE,
    /** Player explicitly skipped their turn (Tab). */
    SKIP_TURN,
    /** Player used a medical item to recover HP (R). */
    HEAL,
    /** Player braced into a directional GUARD stance (strategy-combat-order-4). */
    GUARD
}
