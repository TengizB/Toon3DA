package ge.tbegvadze.toon3d.sim;

import java.util.List;

import ge.tbegvadze.toon3d.entity.WeaponRoll;
import ge.tbegvadze.toon3d.input.touch.TouchAction;
import ge.tbegvadze.toon3d.progression.UpgradeCard;
import ge.tbegvadze.toon3d.route.RouteNode;
import ge.tbegvadze.toon3d.shop.ShopEntry;

/**
 * A scripted stand-in for the player's thumbs (new-game-balancr order 9).
 *
 * <p>The game is strictly turn-based — one action = one turn, no physics, no aim skill — so a whole
 * run is SIMULABLE. A policy answers exactly the questions a human answers: which button to press
 * this turn, which level-up card to take, whether to swap to a weapon found on the floor, and which
 * route node to descend into. Everything else (movement legality, damage, drops, XP, enemy AI) is
 * resolved by the REAL systems through {@link SimWorld}; a policy never computes a game rule.
 *
 * <p>Policies are deliberately simple heuristics, not AI. Their job is to bracket the skill range:
 * the gap between the worst and the best policy IS the game's difficulty range (there is exactly one
 * difficulty — order 8 — so skill decides depth).
 */
public interface PlayerPolicy {

    /** Stable id used in report tables and band assertions (e.g. {@code "TACTICAL"}). */
    String id();

    /**
     * The button this policy presses this turn. Returning {@link TouchAction#NONE} is treated as
     * {@link TouchAction#SKIP_TURN} so a policy can never stall the simulation.
     */
    TouchAction chooseAction(SimView view);

    /** Picks one of the {@code count} offered level-up cards. Default: the first offered. */
    default UpgradeCard chooseCard(UpgradeCard[] offeredCards, int count, SimView view) {
        return count > 0 ? offeredCards[0] : null;
    }

    /**
     * Whether to take the weapon the marine is standing on. {@code heldRoll} is the roll of the
     * weapon currently in the matching slot, or null when the type is new to the run.
     */
    default boolean acceptGroundWeapon(WeaponRoll groundRoll, WeaponRoll heldRoll, SimView view) {
        return true;
    }

    /** Picks the next route node from the offered candidates. Default: the first candidate. */
    default RouteNode chooseNode(List<RouteNode> candidates, SimView view) {
        return candidates.get(0);
    }

    /**
     * Whether to spend credits on the offered shop entry (its price, category and payload are all on
     * the entry). Default: no — buying is the TACTICAL policy's backstop, and the NAIVE policy is
     * defined by not using it.
     */
    default boolean buyShopEntry(ShopEntry entry, SimView view) {
        return false;
    }
}
