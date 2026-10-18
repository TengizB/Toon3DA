package ge.tbegvadze.toon3d.hazard;

/**
 * Where a relic blast lands (relics-run-changing-artifacts R9): ENEMIES ONLY, never the player and never a
 * barrel. Implemented by {@code ExplosiveBarrelManager}, which shares its per-tile enemy hit code with the
 * barrel blast. Fractions are of the reference soldier's HP at the floor's depth.
 */
public interface RelicBlastTarget {
    /**
     * @param tileColumn     blast centre column
     * @param tileRow        blast centre row
     * @param centreFraction fraction of reference HP dealt on the centre tile (0 = nothing there)
     * @param ringFraction   fraction dealt on each of the four orthogonal neighbours
     * @param visualScale    height multiplier of the burst drawn at the centre (EffectConstants.RELIC_BLAST_VISUAL_SCALE_*)
     */
    void detonateRelicBlast(int tileColumn, int tileRow, float centreFraction, float ringFraction, float visualScale);
}
