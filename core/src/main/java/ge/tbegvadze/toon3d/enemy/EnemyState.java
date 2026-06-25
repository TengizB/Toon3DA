package ge.tbegvadze.toon3d.enemy;

/** AI state for a single enemy. Once past DORMANT, an enemy never returns to it. */
public enum EnemyState {
    /** Not yet activated; ignores the player. Rendered darker. */
    DORMANT,
    /** Just woke this turn; transitions immediately to CHASING or ATTACKING. */
    ALERTED,
    /** Alerted and moving toward the player. */
    CHASING,
    /** Charger telegraphing a rush: holding position for one readable turn before it lands (Pillar 2). */
    WINDING_UP,
    /** Alerted and in attack range; dealt damage this turn. */
    ATTACKING
}
