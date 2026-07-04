package ge.tbegvadze.toon3d.entity;

/**
 * Receives notification when a hit resolves against a GUARDING player
 * (strategy-combat-order-4). Fired once per incoming attack while the guard stance is
 * active, BEFORE armour/toughness so the reported number matches the intent-telegraph
 * damage the player was reading. Drives the directional GUARDED (front) vs FLANKED
 * (side/back) feedback so the arc teaches itself.
 */
public interface GuardHitListener {
    /**
     * @param frontArc          true when the hit landed inside the protected front arc (reduced);
     *                          false when it came from a side or the back (full damage — a flank).
     * @param guardedDamage     the incoming damage after the guard multiplier, before armour.
     * @param attackerWorldX    attacker origin X in world units (for directional feedback).
     * @param attackerWorldY    attacker origin Y in world units (for directional feedback).
     */
    void onGuardedHit(boolean frontArc, int guardedDamage, float attackerWorldX, float attackerWorldY);
}
