package ge.tbegvadze.toon3d.shop;

import ge.tbegvadze.toon3d.entity.WeaponProfile;

import java.util.Collections;
import java.util.List;

/**
 * A read-only snapshot of the player state a {@link ShopOfferSource} needs to build valid offers
 * for one machine (shop_order_2). Keeps the roller and offer sources decoupled from World internals.
 *
 * <p>{@code ownedWeapons} are the weapons the player currently carries (loadout slots + melee) —
 * the only things eligible for tier/level upgrade offers, and the source of "usable ammo types".
 */
public final class ShopContext {

    /** Dungeon floor depth — drives depth price scaling. */
    public final int depth;

    /** Weapons the player owns (loadout + melee); never null, may be empty. */
    public final List<WeaponProfile> ownedWeapons;

    public ShopContext(int depth, List<WeaponProfile> ownedWeapons) {
        this.depth        = depth;
        this.ownedWeapons = (ownedWeapons != null)
                ? Collections.unmodifiableList(ownedWeapons)
                : Collections.emptyList();
    }
}
