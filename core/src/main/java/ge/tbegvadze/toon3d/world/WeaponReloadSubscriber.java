package ge.tbegvadze.toon3d.world;

import ge.tbegvadze.toon3d.entity.PlayerInventory;
import ge.tbegvadze.toon3d.entity.Weapon;

/**
 * Adapter that forwards each game tick to the currently equipped weapon's reload counter.
 * Keeps the entity package free of a world dependency.
 */
final class WeaponReloadSubscriber implements TickSubscriber {

    private final PlayerInventory inventory;

    WeaponReloadSubscriber(PlayerInventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public void onTick(TickContext context) {
        Weapon equippedWeapon = inventory.getEquippedWeapon();
        if (equippedWeapon != null) {
            equippedWeapon.onTick();
        }
    }
}
