package ge.tbegvadze.toon3d.world;

import ge.tbegvadze.toon3d.enemy.EnemyManager;
import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.entity.PlayerInventory;
import ge.tbegvadze.toon3d.hazard.HazardManager;
import ge.tbegvadze.toon3d.hazard.SpireManager;
import ge.tbegvadze.toon3d.progression.PlayerStats;
import ge.tbegvadze.toon3d.status.StatusEffectController;

/**
 * The ONE place a floor's turn pipeline is assembled (see {@code docs/tick-system.txt}).
 *
 * <p>Dispatch ORDER is a gameplay rule — hazards must tick before status so a tile's burn lands the
 * same turn you stand in it, the boss must act before the ordinary roster, and reload must advance
 * before anything can shoot back. {@code World} builds the pipeline for a played floor and
 * {@code sim.SimWorld} builds it for a simulated one; both call this, so a simulated turn resolves
 * in exactly the order a played turn does. That is what lets the order-9 behavioural bands claim
 * they measured the real game.
 */
public final class TickPipeline {

    private TickPipeline() {}

    /**
     * Builds a fresh bus with the standard per-floor subscribers, in dispatch order:
     * <ol>
     *   <li>weapon reload — a clip fills before enemies get to act</li>
     *   <li>terrain hazards — fire/toxic tick before status, so standing in fire burns this turn</li>
     *   <li>status effects — player and enemy DoT / stun bookkeeping</li>
     *   <li>temporary armour decay (Bulwark Rounds)</li>
     *   <li>the boss controller, when this floor has one — the boss acts before its minions</li>
     *   <li>the enemy roster's turn</li>
     * </ol>
     *
     * @param spireManager  the floor's crystal-spire simulation (Verdant Spiresower), or null when the
     *                      floor / caller has none — its lifetime decay ticks alongside hazards, before the
     *                      enemy turn, so a spire sown during an enemy turn keeps its full lifetime
     * @param bossController the floor's boss controller, or null on an ordinary floor
     */
    public static TickEventBus standardFloor(PlayerInventory inventory,
                                             HazardManager hazardManager,
                                             SpireManager spireManager,
                                             StatusEffectController statusEffectController,
                                             Player player,
                                             EnemyManager enemyManager,
                                             PlayerStats playerStats,
                                             TickSubscriber bossController,
                                             GameState gameState) {
        TickEventBus bus = new TickEventBus();
        bus.subscribe(new WeaponReloadSubscriber(inventory));
        bus.subscribe(new HazardTickSubscriber(hazardManager));
        // Crystal-spire decay (elemental-golem-verdant-spiresower) — ticks with hazards, before the enemy
        // turn, so a spire sown this turn is not immediately aged. Null on floors/callers without spires.
        if (spireManager != null) {
            bus.subscribe(context -> spireManager.tickLifetimes());
        }
        bus.subscribe(new StatusEffectSubscriber(statusEffectController, player, enemyManager));
        bus.subscribe(context -> playerStats.tickTempArmor());
        if (bossController != null) {
            bus.subscribe(bossController);
        }
        bus.subscribe(new EnemyTurnSubscriber(enemyManager, gameState));
        return bus;
    }
}
