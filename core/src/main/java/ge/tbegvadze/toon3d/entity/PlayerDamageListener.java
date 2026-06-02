package ge.tbegvadze.toon3d.entity;

/** Receives notification when the player takes damage. */
public interface PlayerDamageListener {
    /** Called when the player's HP decreases by the given net amount (after armour absorption). */
    void onPlayerDamaged(int netDamage);
}
