package ge.tbegvadze.toon3d.level;

/**
 * The supply categories the {@link SupplyPlanner} plans and the R-SUPPLY audit tracks independently
 * (balance-overhaul order 2, S5 / S7). Every placed pickup belongs to exactly one.
 */
public enum SupplyCategory {
    /** Ammo boxes ('6' bullets, '7' shells, '8' cells, '9' rockets, '0' slugs). Value = damage at depth. */
    AMMO,
    /** Heal pickups ('H' field medkit, '+' stim pack). Value = fraction of max HP restored. */
    HEAL,
    /** Armour pickups ('a' shard, 'A' vest). Value = HP-equivalent fraction of max HP. */
    ARMOUR,
    /** Credit chips (entity-side ground items). Value = credits. */
    CREDITS,
    /** Weapon drops (entity-side ground items). Value = 1 per drop. */
    WEAPON
}
