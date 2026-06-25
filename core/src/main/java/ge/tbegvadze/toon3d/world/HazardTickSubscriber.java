package ge.tbegvadze.toon3d.world;

import ge.tbegvadze.toon3d.hazard.HazardManager;

/**
 * Adapter that advances the terrain-hazard simulation once per world turn
 * (balance idea 4, Pillar 3). Registered BEFORE StatusEffectSubscriber so a hazard tile's
 * BURNING / POISONED application lands and ticks on the SAME turn the host stands in it —
 * stepping into fire bites immediately, which is what makes the terrain a real decision.
 *
 * Keeps HazardManager unaware of the tick bus, mirroring EnemyTurnSubscriber /
 * StatusEffectSubscriber. Created fresh each floor (level-dependent), like the manager it drives.
 */
final class HazardTickSubscriber implements TickSubscriber {

    private final HazardManager hazardManager;

    HazardTickSubscriber(HazardManager hazardManager) {
        this.hazardManager = hazardManager;
    }

    @Override
    public void onTick(TickContext context) {
        hazardManager.tick(context.getPlayerTileColumn(), context.getPlayerTileRow(), context.getPlayer());
    }
}
