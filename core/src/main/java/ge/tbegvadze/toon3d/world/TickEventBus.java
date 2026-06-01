package ge.tbegvadze.toon3d.world;

import ge.tbegvadze.toon3d.entity.Player;
import ge.tbegvadze.toon3d.util.Constants;

/**
 * Zero-allocation pub/sub bus for game ticks.
 *
 * Subscribers are stored in a fixed-capacity array and iterated by index — no
 * Iterator allocation per tick. The single TickContext is reused across all
 * dispatches. Register all subscribers once during setup; never subscribe
 * or unsubscribe from inside an onTick() call.
 *
 * Dispatch order equals registration order. The intended order is:
 *   1. WeaponReloadSubscriber  — reload advances before enemies act
 *   2. EnemyTurnSubscriber     — enemy AI runs after reload
 *   (future subscribers appended after)
 */
public final class TickEventBus {

    private final TickSubscriber[] subscribers;
    private int subscriberCount = 0;
    private final TickContext context = new TickContext();
    private long nextTickIndex = 0L;

    public TickEventBus() {
        this(Constants.MAX_TICK_SUBSCRIBERS);
    }

    TickEventBus(int capacity) {
        this.subscribers = new TickSubscriber[capacity];
    }

    /** Register in dispatch order. Call only during setup, not per frame. */
    public void subscribe(TickSubscriber subscriber) {
        if (subscriberCount >= subscribers.length) {
            throw new IllegalStateException("TickEventBus capacity exceeded: " + subscribers.length);
        }
        subscribers[subscriberCount++] = subscriber;
    }

    /** Remove a subscriber (compacts the array). Rare; must not be called from onTick(). */
    public void unsubscribe(TickSubscriber subscriber) {
        for (int index = 0; index < subscriberCount; index++) {
            if (subscribers[index] == subscriber) {
                System.arraycopy(subscribers, index + 1, subscribers, index, subscriberCount - index - 1);
                subscribers[--subscriberCount] = null;
                return;
            }
        }
    }

    /**
     * Fire one tick. Mutates the reused context, then dispatches to all subscribers in order.
     * No allocations. Must be called from the update thread only.
     */
    public void fireTick(int playerTileColumn, int playerTileRow, Player player, TickCause cause) {
        context.playerTileColumn = playerTileColumn;
        context.playerTileRow    = playerTileRow;
        context.player           = player;
        context.cause            = cause;
        context.tickIndex        = nextTickIndex++;
        for (int index = 0; index < subscriberCount; index++) {
            subscribers[index].onTick(context);
        }
    }
}
