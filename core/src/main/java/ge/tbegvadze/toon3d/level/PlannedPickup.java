package ge.tbegvadze.toon3d.level;

import ge.tbegvadze.toon3d.item.ItemType;

/**
 * One discrete pickup the {@link SupplyPlanner} decided this floor carries (balance-overhaul order 2).
 * Where it lands — a ground slot, or a CARRIER enemy's drop — is decided by
 * {@link SupplyPlanner#place}. Immutable.
 */
public final class PlannedPickup {

    public final SupplyCategory category;
    /** Grid symbol for AMMO / HEAL / ARMOUR; {@code 0} for CREDITS and WEAPON (entity-side items). */
    public final char     symbol;
    /** AMMO: damage at depth; HEAL / ARMOUR: fraction of max HP; CREDITS: credits; WEAPON: 1. */
    public final float    value;
    /** Credits a CREDITS chip pays (0 for every other category). */
    public final int      creditAmount;
    /** The weapon a WEAPON drop offers ({@code null} for every other category). */
    public final ItemType weaponType;
    /** Weapon level offset from the floor depth (WEAPON only). */
    public final int      weaponLevelOffset;
    /** Tier floor above the region's minimum drop tier, or -1 for the plain band (WEAPON only). */
    public final int      weaponTierBonus;
    /** Part of the S4 heal floor: always on the ground, keycard-free, half of it in the first half. */
    public final boolean  healFloor;
    /** Handed to an enemy as its drop (S6) instead of placed on the ground. */
    public final boolean  carried;
    /** Placed in or past the anchor group's room (ELITE vault ammo and reward weapon, C2). */
    public final boolean  behindAnchor;

    private PlannedPickup(SupplyCategory category, char symbol, float value, int creditAmount,
                          ItemType weaponType, int weaponLevelOffset, int weaponTierBonus,
                          boolean healFloor, boolean carried, boolean behindAnchor) {
        this.category          = category;
        this.symbol            = symbol;
        this.value             = value;
        this.creditAmount      = creditAmount;
        this.weaponType        = weaponType;
        this.weaponLevelOffset = weaponLevelOffset;
        this.weaponTierBonus   = weaponTierBonus;
        this.healFloor         = healFloor;
        this.carried           = carried;
        this.behindAnchor      = behindAnchor;
    }

    static PlannedPickup grid(SupplyCategory category, char symbol, float value,
                              boolean healFloor, boolean behindAnchor) {
        return new PlannedPickup(category, symbol, value, 0, null, 0, -1, healFloor, false, behindAnchor);
    }

    static PlannedPickup credits(int amount) {
        return new PlannedPickup(SupplyCategory.CREDITS, (char) 0, amount, amount, null, 0, -1,
                false, false, false);
    }

    static PlannedPickup weapon(ItemType weaponType, int levelOffset, int tierBonus, boolean behindAnchor) {
        return new PlannedPickup(SupplyCategory.WEAPON, (char) 0, 1f, 0, weaponType, levelOffset, tierBonus,
                false, false, behindAnchor);
    }

    /** A copy of this pickup handed to a carrier enemy instead of the ground. */
    PlannedPickup asCarried() {
        return new PlannedPickup(category, symbol, value, creditAmount, weaponType, weaponLevelOffset,
                weaponTierBonus, healFloor, true, behindAnchor);
    }

    /** A copy of this weapon drop at a different level offset (the S9 cadence lifts it to on-level). */
    PlannedPickup withWeaponLevelOffset(int levelOffset) {
        return new PlannedPickup(category, symbol, value, creditAmount, weaponType, levelOffset,
                weaponTierBonus, healFloor, carried, behindAnchor);
    }

    @Override public String toString() {
        return category + (symbol != 0 ? "'" + symbol + "'" : "") + String.format("=%.2f", value)
                + (healFloor ? " floor" : "") + (carried ? " carried" : "") + (behindAnchor ? " anchor" : "");
    }
}
