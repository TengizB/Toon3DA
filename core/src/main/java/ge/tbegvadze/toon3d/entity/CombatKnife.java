package ge.tbegvadze.toon3d.entity;

import ge.tbegvadze.toon3d.item.ItemType;
import ge.tbegvadze.toon3d.util.GameBalance;

/**
 * Combat Knife — fast light melee; effective against low-HP chaff.
 * Higher damage than the Fist, same single-turn attack speed.
 */
public final class CombatKnife extends MeleeWeapon {

    public CombatKnife() {
        super("KNIFE", GameBalance.MELEE_KNIFE_DAMAGE);
    }

    @Override public ItemType getItemType() { return ItemType.WEAPON_KNIFE; }
}
