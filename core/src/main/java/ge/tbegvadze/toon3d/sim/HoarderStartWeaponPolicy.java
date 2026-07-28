package ge.tbegvadze.toon3d.sim;

import ge.tbegvadze.toon3d.entity.WeaponRoll;

/**
 * The permanent regression case (new-game-balancr order 9).
 *
 * <p>Identical to {@link TacticalPolicy} in every respect but ONE: it never equips a weapon it finds.
 * It descends the whole run on the loadout it started with — the exact behaviour that once let a
 * player grind the first boss down with the starting shotgun, which is the bug this whole balance
 * series exists to make impossible.
 *
 * <p>The S-GATE band asserts that ZERO of {@code SIM_SEED_COUNT} seeds clear the first boss under
 * this policy. R-BOSS-GATE (order 6) proves that on paper — turnsToKill is at least twice the
 * survivable turns — and this proves it in play, against real rosters, real drops and a real fight.
 * If the two ever disagree, the paper is wrong.
 */
public final class HoarderStartWeaponPolicy implements PlayerPolicy {

    private final TacticalPolicy tacticalBehaviour;

    public HoarderStartWeaponPolicy(long seed) {
        this.tacticalBehaviour = new TacticalPolicy(seed);
    }

    @Override
    public String id() {
        return "HOARDER-START-WEAPON";
    }

    @Override
    public ge.tbegvadze.toon3d.input.touch.TouchAction chooseAction(SimView view) {
        return tacticalBehaviour.chooseAction(view);
    }

    @Override
    public ge.tbegvadze.toon3d.progression.UpgradeCard chooseCard(
            ge.tbegvadze.toon3d.progression.UpgradeCard[] offeredCards, int count, SimView view) {
        return tacticalBehaviour.chooseCard(offeredCards, count, view);
    }

    @Override
    public ge.tbegvadze.toon3d.route.RouteNode chooseNode(
            java.util.List<ge.tbegvadze.toon3d.route.RouteNode> candidates, SimView view) {
        return tacticalBehaviour.chooseNode(candidates, view);
    }

    /**
     * Buys supplies like TACTICAL does, but never a weapon upgrade — a bought tier is still a new
     * gun, and this policy exists to prove the STARTING gun cannot carry the run.
     */
    @Override
    public boolean buyShopEntry(ge.tbegvadze.toon3d.shop.ShopEntry entry, SimView view) {
        if (entry.category == ge.tbegvadze.toon3d.shop.OfferCategory.WEAPON_TIER_UPGRADE
                || entry.category == ge.tbegvadze.toon3d.shop.OfferCategory.WEAPON_LEVEL_UP) {
            return false;
        }
        return tacticalBehaviour.buyShopEntry(entry, view);
    }

    /** THE defining trait: the run's starting guns are the run's only guns. */
    @Override
    public boolean acceptGroundWeapon(WeaponRoll groundRoll, WeaponRoll heldRoll, SimView view) {
        return false;
    }
}
