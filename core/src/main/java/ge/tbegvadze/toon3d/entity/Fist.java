package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.util.GameBalance;

/**
 * The Fist — always-available melee fallback, never dropped.
 * Low base damage (chip only); the player's last resort when out of ammo.
 * Assigned to the melee slot at run start by World; cannot be replaced.
 */
public final class Fist extends MeleeWeapon {

    public Fist() {
        super("FIST", GameBalance.MELEE_FIST_DAMAGE);
    }

    @Override public ItemType getItemType() { return ItemType.WEAPON_FIST; }
}
