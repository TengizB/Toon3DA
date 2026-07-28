package ge.tbegvadze.toon3d.sim;

import java.util.List;

import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.entity.Weapon;
import ge.tbegvadze.toon3d.entity.WeaponRoll;
import ge.tbegvadze.toon3d.input.touch.TouchAction;
import ge.tbegvadze.toon3d.progression.UpgradeCard;
import ge.tbegvadze.toon3d.route.NodeEconomics;
import ge.tbegvadze.toon3d.route.RouteEconomicsModel;
import ge.tbegvadze.toon3d.route.RouteNode;
import ge.tbegvadze.toon3d.route.RouteNodeType;
import ge.tbegvadze.toon3d.route.RouteRegistries;
import ge.tbegvadze.toon3d.shop.ShopEntry;
import ge.tbegvadze.toon3d.util.BalanceConfig;

/**
 * The ceiling of the skill range (new-game-balancr order 9): a player who has read the manual.
 *
 * <p>TACTICAL keeps its range, breaks the cardinal line against ranged enemies, guards a telegraphed
 * spike, manages ammo across weapons instead of dry-firing one, heals before it is desperate, buys
 * the shop backstop, takes the upgrades it is offered, and routes by the order-7 node economics —
 * calm nodes when it is hurt, elites when it is strong, provisions before a boss.
 *
 * <p>It is still only a heuristic. It plays the game the way the design says the game should be
 * played, which is exactly what the bands need: if a competent, rule-following player cannot reach
 * the intended depth band, the model is wrong about its own game.
 */
public final class TacticalPolicy implements PlayerPolicy {

    // ---- Scripted-player PREFERENCES ------------------------------------------------------
    // These are weights of the simulated player's judgement, not game values: they describe how
    // this policy shops the route map, not what a node is worth. The node's WORTH comes from the
    // order-7 ledger (NodeEconomics); these only say how much this player cares about each term.
    /** How strongly a hurt player is drawn to a calm node. */
    private static final float CALM_NODE_RELIEF_BONUS   = 5f;
    /** How strongly a healthy player avoids spending a floor on calm (calm costs tempo). */
    private static final float CALM_NODE_TEMPO_PENALTY  = 2f;
    /** Extra pull toward a SHOP when supplies are the thing that is missing. */
    private static final float SHOP_NODE_RELIEF_BONUS   = 2f;
    /** Scales hit-point and credit terms into the same range as the ammo-box/upgrade terms. */
    private static final float PER_POINT_TERM_WEIGHT    = 0.01f;

    private final int[]  stepBuffer = new int[2];
    private final TravelPlanner travelPlanner = new TravelPlanner();
    /** True when the previous turn was spent guarding — a brace covers one turn, never a stalemate. */
    private boolean guardedPreviousTurn;
    private final java.util.Random tieBreak;

    public TacticalPolicy(long seed) {
        this.tieBreak = new java.util.Random(seed);
    }

    @Override
    public String id() {
        return "TACTICAL";
    }

    @Override
    public TouchAction chooseAction(SimView view) {
        boolean guardedLastTurn = guardedPreviousTurn;
        guardedPreviousTurn = false;

        // 1. Survive first: heal well before the last hit, not after it.
        if (view.vitalityFraction() < BalanceConfig.SIM_TACTICAL_HEAL_FRACTION && view.medkitCount() > 0) {
            return TouchAction.HEAL;
        }

        Weapon weapon = view.equippedWeapon();

        // 2. Ammo discipline: switch off a dry gun rather than walking into melee with it.
        if (view.equippedWeaponIsDry()) {
            Weapon better = bestArmedAlternative(view);
            if (better != null && better != weapon) return TouchAction.SWITCH_WEAPON;
        }

        // 3. Answer the fight in front of the marine — shoot what is lined up, turn to what is on it.
        //    KILLING the threat beats bracing against it, so this sits above the guard check.
        TouchAction immediate = SimPolicySupport.answerImmediateThreat(view);
        if (immediate != TouchAction.NONE) {
            travelPlanner.interrupt();   // the walk plan is stale once a fight starts
            return immediate;
        }

        // 4. Nothing to shoot this turn and a committed intent would take a fifth of what is left:
        //    brace. Never twice in a row — guard buys ONE turn, and a player who only guards dies
        //    standing up.
        int incoming = view.incomingTelegraphedDamage();
        if (!guardedLastTurn && incoming > 0 && view.telegraphedThreatVisible()) {
            float remaining = Math.max(1, view.health() + view.armor());
            if ((float) incoming / remaining >= BalanceConfig.SIM_TACTICAL_GUARD_DAMAGE_FRACTION) {
                guardedPreviousTurn = true;
                return TouchAction.GUARD;
            }
        }

        // 5. Deny the snipers their line — the cardinal-line constraint IS the counterplay.
        if (view.standingOnARangedEnemyLine()) {
            TouchAction sidestep = SimPolicySupport.stepOffRangedLines(view);
            if (sidestep != TouchAction.NONE) return sidestep;
        }

        // 6. Top the clip off in a lull, so the next contact starts loaded.
        if (weapon != null && view.liveEnemyCount() >= 0 && !weapon.isReloading()
                && weapon.getShotsInClip() < weapon.getEffectiveClipSize()
                && view.reserveAmmoForEquipped() > 0
                && SimPolicySupport.answerImmediateThreat(view) == TouchAction.NONE
                && weapon.getShotsInClip() == 0) {
            return TouchAction.RELOAD;
        }

        // 7. Travel. Supplies are worth a detour while the marine is healthy enough to want them
        //    (ammo and medkits ARE the run); a hurt marine with nothing left to heal with heads
        //    straight for the stairs. The planner holds the destination across turns so the marine
        //    cannot dither between two.
        boolean shouldDisengage = view.vitalityFraction() < BalanceConfig.SIM_TACTICAL_RETREAT_FRACTION
                                  && view.medkitCount() == 0;
        TouchAction travel = travelPlanner.travel(view, !shouldDisengage, stepBuffer);
        if (travel != TouchAction.NONE) return travel;

        return SimPolicySupport.wander(view, tieBreak);
    }

    /** The loadout slot with the most rounds behind it, or null when every gun is dry. */
    private Weapon bestArmedAlternative(SimView view) {
        Weapon best = null;
        int    bestRounds = 0;
        ge.tbegvadze.toon3d.entity.Loadout loadout = view.loadout();
        if (loadout == null) return null;
        for (int slotIndex = 0; slotIndex < loadout.getSlotCount(); slotIndex++) {
            Weapon candidate = loadout.getSlot(slotIndex);
            if (candidate == null) continue;
            int rounds = view.roundsAvailableFor(candidate);
            if (rounds > bestRounds) {
                bestRounds = rounds;
                best       = candidate;
            }
        }
        return best;
    }

    /**
     * Takes the card with the largest priced power gain, breaking ties toward survivability. Cards
     * are budget-equal by contract (R-CARD), so this is a shape preference, not a power grab.
     */
    @Override
    public UpgradeCard chooseCard(UpgradeCard[] offeredCards, int count, SimView view) {
        UpgradeCard best = null;
        float bestScore  = Float.NEGATIVE_INFINITY;
        for (int index = 0; index < count; index++) {
            UpgradeCard card = offeredCards[index];
            if (card == null) continue;
            float score = card.estimatedPowerPoints()
                          + card.effectiveHitPointGain() * PER_POINT_TERM_WEIGHT;
            if (score > bestScore) {
                bestScore = score;
                best      = card;
            }
        }
        return best;
    }

    /** Upgrades when the ground roll is a genuine improvement on what the slot already holds. */
    @Override
    public boolean acceptGroundWeapon(WeaponRoll groundRoll, WeaponRoll heldRoll, SimView view) {
        if (groundRoll == null) return false;
        if (heldRoll == null)   return true;                       // a new weapon type is pure gain
        if (groundRoll.tier != null && heldRoll.tier != null
                && groundRoll.tier.ordinal() != heldRoll.tier.ordinal()) {
            return groundRoll.tier.ordinal() > heldRoll.tier.ordinal();
        }
        return groundRoll.weaponLevel > heldRoll.weaponLevel;
    }

    /**
     * Routes by the order-7 node ledger: when hurt or short of supplies, take the CALM node that
     * refills; otherwise take the node whose priced expected value is highest (which is what makes
     * an ELITE worth its threat premium). Falls back to the first candidate before the first floor
     * exists, when there is no view to read.
     */
    @Override
    public RouteNode chooseNode(List<RouteNode> candidates, SimView view) {
        if (view == null) return candidates.get(0);
        boolean needsRelief = view.vitalityFraction() < BalanceConfig.SIM_TACTICAL_HEAL_FRACTION
                || view.medkitCount() == 0;
        RouteNode best      = candidates.get(0);
        float     bestScore = Float.NEGATIVE_INFINITY;
        for (RouteNode candidate : candidates) {
            float score = scoreNode(candidate, needsRelief);
            if (score > bestScore) {
                bestScore = score;
                best      = candidate;
            }
        }
        return best;
    }

    /**
     * A node's desirability: its ledger row's expected value, with calm nodes promoted while the
     * marine is hurt and demoted while it is healthy (the "calm costs tempo" trade of R-CALM-COST).
     */
    private float scoreNode(RouteNode candidate, boolean needsRelief) {
        NodeEconomics row = RouteRegistries.nodeEconomics().forNodeType(candidate.type);
        boolean calm = RouteEconomicsModel.isCalm(candidate.type);
        float score  = row == null ? 0f
                : row.guaranteedAmmoBoxes()
                  + row.guaranteedHealHitPoints() * PER_POINT_TERM_WEIGHT
                  + row.upgradeOpportunity()
                  + row.creditDelta() * PER_POINT_TERM_WEIGHT;
        if (calm) score += needsRelief ? CALM_NODE_RELIEF_BONUS : -CALM_NODE_TEMPO_PENALTY;
        if (candidate.type == RouteNodeType.SHOP && needsRelief) score += SHOP_NODE_RELIEF_BONUS;
        return score;
    }

    /** Buys whatever the fabricator offers that the wallet can cover — the ammo/heal backstop. */
    @Override
    public boolean buyShopEntry(ShopEntry entry, SimView view) {
        return true;
    }
}
