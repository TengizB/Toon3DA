package ge.tbegvadze.toon3d.input;

/**
 * Notified when a player step is rejected because the target tile is solid
 * (wall / solid prop) or occupied by an enemy. Purely for cosmetic feedback —
 * a blocked move consumes no turn and changes no game state.
 *
 * @see ge.tbegvadze.toon3d.render.ImpactEffectSystem#triggerBump(float, float)
 */
public interface MoveBlockedListener {
    /**
     * @param directionX attempted move direction X (cardinal unit step: -1, 0, or 1)
     * @param directionY attempted move direction Y (cardinal unit step: -1, 0, or 1)
     */
    void onMoveBlocked(float directionX, float directionY);
}
