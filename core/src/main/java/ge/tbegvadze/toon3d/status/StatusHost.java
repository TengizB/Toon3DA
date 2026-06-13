package ge.tbegvadze.toon3d.status;

import java.util.EnumMap;

/**
 * Implemented by any actor that can carry status effects — currently Player and Enemy.
 * The EnumMap returned by getActiveEffects() must be pre-populated with one StatusEffect
 * instance per StatusType, allocated once at host construction (zero per-tick allocation).
 */
public interface StatusHost {

    /** Pre-allocated map of live effect instances. Never null; always contains all StatusType keys. */
    EnumMap<StatusType, StatusEffect> getActiveEffects();

    /** Per-archetype or per-player resist/immunity table. Consulted during apply(). */
    StatusResistance getStatusResistance();

    /**
     * Apply damage-over-time directly to this host's HP pool.
     * Bypasses the dodge roll (you can't dodge fire you're standing in).
     * Armour absorption and flat toughness reduction still apply for the player.
     */
    void applyDoTDamage(int amount);

    boolean isAlive();
}
