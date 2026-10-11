package ge.tbegvadze.toon3d.sim;

import java.util.List;

import ge.tbegvadze.toon3d.enemy.Enemy;
import ge.tbegvadze.toon3d.entity.Weapon;
import ge.tbegvadze.toon3d.entity.WeaponRoll;
import ge.tbegvadze.toon3d.input.touch.TouchAction;
import ge.tbegvadze.toon3d.progression.Perk;
import ge.tbegvadze.toon3d.progression.PerkDeck;
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
 * <p>TACTICAL keeps its range, breaks the cardinal line against ranged enemies, reads the red floor of a
 * committed heavy (stagger / guard / step off), manages ammo across weapons instead of dry-firing one, heals before it is desperate, buys
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
    /** A SYNERGY perk outranks any rank count (no tag holds this many ranks). */
    private static final int   SYNERGY_PICK_WEIGHT      = 1000;

    private final int[]  stepBuffer = new int[2];
    private final TravelPlanner travelPlanner = new TravelPlanner();
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
        // 0. READ THE RED FLOOR (balance-overhaul order 4, CP7b; replaces the old "guard at a fifth of what is
        //    left" trigger): a HEAVY is committed and this tile is one of its danger tiles. Answer it before
        //    anything else — a heavy is half the on-curve health pool, and a heal spent in front of it is a
        //    heal and a half-pool lost. Step off, else stagger it, else guard it.
        TouchAction heavyAnswer = answerHeavyThreat(view);
        if (heavyAnswer != TouchAction.NONE) {
            travelPlanner.interrupt();   // the walk plan is stale once a fight starts
            return heavyAnswer;
        }

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

        // 2b. Matchup (balance-overhaul order 3, C4): the gun in hand is RESISTED by what it faces and the
        //     bag holds an EFFECTIVE one with ammo — take the SWITCH hint (one turn, straight to that gun).
        if (view.switchHintWeapon() != null) {
            return TouchAction.SWITCH_WEAPON;
        }

        // 3. Answer the fight in front of the marine — shoot what is lined up, turn to what is on it.
        //    KILLING the threat beats bracing against it, so this sits above the guard check.
        TouchAction immediate = SimPolicySupport.answerImmediateThreat(view);
        if (immediate != TouchAction.NONE) {
            travelPlanner.interrupt();   // the walk plan is stale once a fight starts
            return immediate;
        }

        // 4. (The old "guard when a telegraphed hit would take a fifth of what is left" brace is gone: the
        //    heavy answer at the top of this method is the guard now, and it only fires on a danger tile.)

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

    /**
     * The HEAVY answer (balance-overhaul order 4, H4/H5/H7): when the marine's tile is a danger tile of a
     * committed heavy, one action, in this order —
     * (1) STAGGER: a spread gun that can fire, the source in the facing line inside the stagger range: FIRE
     *     (a staggered heavy is cancelled, not delayed);
     * (2) GUARD a heavy that is already FACED (AS4 — the riposte stuns and exposes it and the marine is already
     *     lined up for the free shots; measured: a marine that sidesteps every heavy it faces never shoots back
     *     and loops until the turn cap);
     * (3) STEP or strafe to a walkable, unoccupied, NON-danger neighbour (preferring a tile no ranged enemy can
     *     shoot, then one that keeps a cardinal line to the source);
     * (4) else GUARD anyway (cornered) — or, when the source is a primed self-destruct with a turn to spare,
     *     rotate toward it first.
     * Returns NONE when the tile is not a danger tile, or when a primed self-destruct still has a turn to spare
     * and the marine already faces it (the normal fight logic then shoots it; the answer repeats next turn).
     */
    private TouchAction answerHeavyThreat(SimView view) {
        Enemy source = view.heavyThreatOnMyTile();
        if (source == null) return TouchAction.NONE;

        Weapon weapon = view.equippedWeapon();
        boolean spreadGun = weapon instanceof ge.tbegvadze.toon3d.entity.Shotgun
                || weapon instanceof ge.tbegvadze.toon3d.entity.DoubleBarrelShotgun;
        if (spreadGun && weapon.canFire() && source.isAlerted() && !source.skipNextAction
                && view.enemyInFacingLine(BalanceConfig.SHOTGUN_STAGGER_MAX_TILES) == source) {
            return TouchAction.FIRE;
        }

        boolean turnRemains = source.selfDestructPrimed && source.selfDestructTurnsRemaining > 1;
        if (view.isInGuardFrontArc(source)) return turnRemains ? TouchAction.NONE : TouchAction.GUARD;

        TouchAction sidestep = stepOffHeavyDanger(view, source);
        if (sidestep != TouchAction.NONE) return sidestep;

        if (turnRemains) {
            TouchAction turn = SimNavigator.actionToFace(view.facingStepColumn(), view.facingStepRow(),
                    Integer.signum(source.tileColumn - view.playerTileColumn()),
                    Integer.signum(source.tileRow - view.playerTileRow()));
            if (turn != TouchAction.NONE) return turn;
        }
        return TouchAction.GUARD;
    }

    /**
     * One MOVE (never a rotation) to a cardinal neighbour that is walkable, unoccupied and not a danger tile of
     * ANY committed heavy. Best score wins: +2 for a tile no ranged enemy can shoot, +1 for a tile sharing a
     * row or column with the heavy's source (a firing line stays cheap). NONE when the marine is cornered.
     */
    private TouchAction stepOffHeavyDanger(SimView view, Enemy source) {
        int facingColumn = view.facingStepColumn();
        int facingRow    = view.facingStepRow();
        TouchAction bestAction = TouchAction.NONE;
        int bestScore = -1;
        for (int direction = 0; direction < HEAVY_STEP_COLUMNS.length; direction++) {
            int stepColumn   = HEAVY_STEP_COLUMNS[direction];
            int stepRow      = HEAVY_STEP_ROWS[direction];
            int targetColumn = view.playerTileColumn() + stepColumn;
            int targetRow    = view.playerTileRow()    + stepRow;
            if (view.level().isBlockedAt(targetColumn, targetRow, view.doorManager())) continue;
            if (view.enemyManager().isTileOccupiedByEnemy(targetColumn, targetRow)) continue;
            if (view.isHeavyDangerTile(targetColumn, targetRow)) continue;
            int score = 0;
            if (!view.rangedEnemyThreatensTile(targetColumn, targetRow)) score += 2;
            if (targetColumn == source.tileColumn || targetRow == source.tileRow) score += 1;
            if (score > bestScore) {
                bestScore  = score;
                bestAction = moveActionForStep(facingColumn, facingRow, stepColumn, stepRow);
            }
        }
        return bestAction;
    }

    /** The button that MOVES the marine one tile along a cardinal step (strafe for the sides — no turn spent). */
    private static TouchAction moveActionForStep(int facingColumn, int facingRow, int stepColumn, int stepRow) {
        if (stepColumn == facingColumn && stepRow == facingRow)   return TouchAction.FORWARD;
        if (stepColumn == -facingColumn && stepRow == -facingRow) return TouchAction.BACK;
        // Strafe left is 90 degrees CCW of the facing: (-facingRow, facingColumn).
        if (stepColumn == -facingRow && stepRow == facingColumn)  return TouchAction.STRAFE_LEFT;
        return TouchAction.STRAFE_RIGHT;
    }

    /** Cardinal steps in a fixed order, so a tie always breaks the same way (determinism). */
    private static final int[] HEAVY_STEP_COLUMNS = { 1, -1, 0,  0 };
    private static final int[] HEAVY_STEP_ROWS    = { 0,  0, 1, -1 };

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
     * Leans into the build (balance-overhaul order 5 — the default CP6 adds build-bias variants to): prefers a
     * SYNERGY perk (its tag is a carried weapon's), then the perk whose tag already holds the most ranks, then
     * the first offered. Deterministic: ties keep the earlier plate.
     */
    @Override
    public Perk choosePerk(Perk[] offeredPerks, int count, SimView view) {
        Perk best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int index = 0; index < count; index++) {
            Perk perk = offeredPerks[index];
            if (perk == null) continue;
            int score = (PerkDeck.isSynergy(perk, view.carriedBuildTags()) ? SYNERGY_PICK_WEIGHT : 0)
                      + view.playerBuild().ranksInTag(perk.tag);
            if (score > bestScore) {
                bestScore = score;
                best      = perk;
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
     * KIT DIVERSITY (balance-overhaul order 3, CP7): a found gun of a DamageClass the loadout lacks is worth
     * a slot — the matchup layer only pays a player who carries more than one answer. Gives up the slot
     * whose class is REDUNDANT (shared with another carried gun) first, otherwise the gun with the fewest
     * rounds behind it — but never the last gun that still has ammo. A gun of a class already carried is
     * left on the floor (-1).
     */
    @Override
    public int slotToReplaceForGroundWeapon(Weapon foundWeapon, ge.tbegvadze.toon3d.entity.Loadout loadout,
                                            SimView view) {
        if (foundWeapon == null || loadout == null) return -1;
        int slotCount = loadout.getSlotCount();
        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            Weapon held = loadout.getSlot(slotIndex);
            if (held != null && held.damageClass() == foundWeapon.damageClass()) return -1;   // not new
        }
        int armedGuns = 0;
        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            Weapon held = loadout.getSlot(slotIndex);
            if (held != null && view.roundsAvailableFor(held) > 0) armedGuns++;
        }
        int chosen = -1;
        boolean chosenRedundant = false;
        int chosenRounds = Integer.MAX_VALUE;
        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            if (loadout.isSlotLocked(slotIndex)) continue;
            Weapon held = loadout.getSlot(slotIndex);
            if (held == null) continue;
            int rounds = view.roundsAvailableFor(held);
            if (rounds > 0 && armedGuns <= 1) continue;   // never the last gun with ammo
            boolean redundant = false;
            for (int otherIndex = 0; otherIndex < slotCount; otherIndex++) {
                Weapon other = loadout.getSlot(otherIndex);
                if (otherIndex != slotIndex && other != null && other.damageClass() == held.damageClass()) {
                    redundant = true;
                }
            }
            boolean better = chosen < 0
                    || (redundant && !chosenRedundant)
                    || (redundant == chosenRedundant && rounds < chosenRounds);
            if (better) {
                chosen = slotIndex;
                chosenRedundant = redundant;
                chosenRounds = rounds;
            }
        }
        return chosen;
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

    /**
     * Buys whatever the fabricator offers that the wallet can cover — the ammo/heal backstop. R15
     * (balance-overhaul order 1): the LEVEL UP rung for the equipped (main) weapon is the machine's first,
     * guaranteed slot, so a TACTICAL player BEHIND the ladder (level below the floor's threat level) buys it
     * first when it can afford it; an on-level or ahead weapon's level-up is declined.
     */
    @Override
    public boolean buyShopEntry(ShopEntry entry, SimView view) {
        if (entry.category == ge.tbegvadze.toon3d.shop.OfferCategory.WEAPON_LEVEL_UP
                && entry.payload == view.equippedWeapon()) {
            return view.equippedWeapon().getWeaponLevel() < view.depth();
        }
        return true;
    }
}
