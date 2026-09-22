package ge.tbegvadze.toon3d.world;

import ge.tbegvadze.toon3d.audio.GameAudio;
import ge.tbegvadze.toon3d.audio.GameSoundId;
import ge.tbegvadze.toon3d.entity.PlayerInventory;
import ge.tbegvadze.toon3d.entity.Weapon;

/**
 * Adapter that forwards each game tick to the currently equipped weapon's reload counter.
 * Keeps the entity package free of a world dependency.
 */
final class WeaponReloadSubscriber implements TickSubscriber {

    private final PlayerInventory inventory;
    /** Gameplay audio; null in headless runs. */
    private GameAudio gameAudio = null;

    WeaponReloadSubscriber(PlayerInventory inventory) {
        this.inventory = inventory;
    }

    void setGameAudio(GameAudio audio) {
        this.gameAudio = audio;
    }

    @Override
    public void onTick(TickContext context) {
        Weapon equippedWeapon = inventory.getEquippedWeapon();
        if (equippedWeapon == null) return;

        // A reload COMPLETING is not published by Weapon, but it is fully observable from outside:
        // it is the tick on which isReloading() goes true -> false. Watching the edge here means
        // the player hears the gun come back without Weapon needing to know audio exists.
        boolean wasReloading = equippedWeapon.isReloading();
        equippedWeapon.onTick();
        if (wasReloading && !equippedWeapon.isReloading() && gameAudio != null) {
            gameAudio.playUi(GameSoundId.WEAPON_RELOAD_COMPLETE);
        }
    }
}
