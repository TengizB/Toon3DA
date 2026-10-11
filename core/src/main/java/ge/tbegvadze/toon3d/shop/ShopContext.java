package ge.tbegvadze.toon3d.shop;

import ge.tbegvadze.toon3d.entity.WeaponProfile;
import ge.tbegvadze.toon3d.progression.PlayerBuild;

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

    /** The weapon the player currently has EQUIPPED (the LEVEL UP rung's target); nullable. */
    public final WeaponProfile equippedWeapon;

    public ShopContext(int depth, List<WeaponProfile> ownedWeapons) {
        this(depth, ownedWeapons, null);
    }

    /**
     * The run's build (balance-overhaul order 5), nullable: the PLAYER-ABILITY offer sells one rank of one of
     * its unmaxed perks, and its {@link #priceMultiplier} prices every offer.
     */
    public final PlayerBuild playerBuild;

    /**
     * Multiplier on every price this machine rolls — the build's shop price multiplier (Haggler's discount,
     * QUARTERMASTER's cut; balance-overhaul order 5). 1.0 without a build. Prices are fixed at roll time, so
     * this is the price seam.
     */
    public final float priceMultiplier;

    public ShopContext(int depth, List<WeaponProfile> ownedWeapons, WeaponProfile equippedWeapon) {
        this(depth, ownedWeapons, equippedWeapon, null);
    }

    public ShopContext(int depth, List<WeaponProfile> ownedWeapons, WeaponProfile equippedWeapon,
                       PlayerBuild playerBuild) {
        this.depth          = depth;
        this.equippedWeapon = equippedWeapon;
        this.ownedWeapons = (ownedWeapons != null)
                ? Collections.unmodifiableList(ownedWeapons)
                : Collections.emptyList();
        this.playerBuild     = playerBuild;
        this.priceMultiplier = playerBuild != null ? playerBuild.stats().shopPriceMultiplier() : 1f;
    }
}
