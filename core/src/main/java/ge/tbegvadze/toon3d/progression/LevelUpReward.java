package ge.tbegvadze.toon3d.progression;

/**
 * The three mutually-exclusive upgrade choices offered to the player on every level-up.
 * Amounts applied per choice are defined in {@link ge.tbegvadze.toon3d.util.GameBalance}.
 */
public enum LevelUpReward {

    /** Permanently increases the player's maximum HP. */
    HP_BOOST,

    /** Permanently increases the player's maximum armour pool. */
    ARMOR_BOOST,

    /** Permanently increases flat damage added to every weapon shot. */
    DAMAGE_BOOST
}
