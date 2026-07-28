package ge.tbegvadze.toon3d.sim;

import java.util.List;

import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.entity.Weapon;
import ge.tbegvadze.toon3d.input.touch.TouchAction;
import ge.tbegvadze.toon3d.route.RouteNode;
import ge.tbegvadze.toon3d.util.BalanceConfig;

/**
 * The floor of the skill range (new-game-balancr order 9).
 *
 * <p>NAIVE attacks the nearest enemy, never guards, ignores hazards and cardinal lines, heals only
 * when nearly dead, never switches weapons, walks the shortest route to the exit, and picks route
 * nodes without reading them. It is not a bad player so much as an UNTHINKING one: it presses the
 * buttons the situation obviously suggests and nothing else.
 *
 * <p>Its purpose is comparative. S-SKILL asserts that TACTICAL out-lives it by at least
 * {@code SIM_SKILL_DEPTH_GAP_MIN} depths — if the gap ever collapses, the game stopped rewarding
 * play, and one fixed difficulty (order 8) stops being a design and becomes a wall.
 */
public final class NaivePolicy implements PlayerPolicy {

    /** Reusable step buffer — the policy is called once per turn and must not allocate per turn. */
    private final int[] stepBuffer = new int[2];
    private final TravelPlanner travelPlanner = new TravelPlanner();
    private final TargetMemory  targetMemory  = new TargetMemory();

    /** Deterministic tie-breaker for the "which way now?" case; seeded per run by the simulator. */
    private final java.util.Random tieBreak;

    public NaivePolicy(long seed) {
        this.tieBreak = new java.util.Random(seed);
    }

    @Override
    public String id() {
        return "NAIVE";
    }

    @Override
    public TouchAction chooseAction(SimView view) {
        // 1. Heal only when nearly dead — the classic beginner mistake of banking medkits.
        if (view.vitalityFraction() < BalanceConfig.SIM_NAIVE_HEAL_FRACTION && view.medkitCount() > 0) {
            return TouchAction.HEAL;
        }

        // 2. Answer whatever is on top of the marine. No guarding, no line-breaking, no kiting.
        TouchAction immediate = SimPolicySupport.answerImmediateThreat(view);
        if (immediate != TouchAction.NONE) {
            travelPlanner.interrupt();
            return immediate;
        }

        // 3. Walk at the nearest enemy it can actually reach and hit it. Chasing is this policy's
        //    defining mistake: it spends turns and health on fights it could have walked past.
        Enemy chased = targetMemory.resolve(view);
        if (chased != null) {
            TouchAction toward = SimPolicySupport.approachAndAttack(view, chased, stepBuffer);
            if (toward != TouchAction.NONE) return toward;
        }

        // 4. Nothing to fight: head for the stairs by the shortest path. NAIVE never detours for
        //    supplies — it walks the route and takes only what happens to be underfoot.
        TouchAction toExit = travelPlanner.travel(view, false, stepBuffer);
        if (toExit != TouchAction.NONE) return toExit;

        // 5. Lost (an unreachable exit or a doorway it cannot resolve): wander deterministically.
        return SimPolicySupport.wander(view, tieBreak);
    }

    /** Takes whatever card is offered first — no build planning. */
    @Override
    public ge.tbegvadze.toon3d.progression.UpgradeCard chooseCard(
            ge.tbegvadze.toon3d.progression.UpgradeCard[] offeredCards, int count, SimView view) {
        return count > 0 ? offeredCards[0] : null;
    }

    /** Picks a route node at random — the map's pips and prices are ignored entirely. */
    @Override
    public RouteNode chooseNode(List<RouteNode> candidates, SimView view) {
        return candidates.get(tieBreak.nextInt(candidates.size()));
    }
}
