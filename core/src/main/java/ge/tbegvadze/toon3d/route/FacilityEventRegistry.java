package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.util.GameMath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The data-driven catalog of narrative {@link FacilityEvent}s (route-map order-10). Mirrors the other
 * route registries: populated once at bootstrap ({@link FacilityEvents#registerAll}), then read where
 * an EVENT node needs its beat — {@link EventProfile} rolls one event per node from the node seed and
 * attaches its id to the {@link LevelPlan}; {@code World} looks the event back up by id to present the
 * choice overlay and apply the picked {@link EventEffect}.
 *
 * <p>Adding an event is one {@link #register} line — because every choice's behaviour is a typed
 * {@link EventEffect}, no other code changes. The roll is deterministic from the node seed (the same
 * {@link GameMath#weightedChoiceIndex} + {@link RouteRng} pattern MYSTERY uses), so a seed always
 * meets the same events. Pure / headless — no LibGDX imports.
 */
public final class FacilityEventRegistry {

    /** Salt mixed into the node seed so the event roll is a stream independent of other per-node rolls. */
    private static final long EVENT_STREAM_SALT = 0x4556454E54524F4CL; // "EVENTROL"

    // Insertion-ordered so the roll pool and its weights are deterministic run to run.
    private final Map<String, FacilityEvent> byId = new LinkedHashMap<>();

    /**
     * Registers an event under its {@link FacilityEvent#id()}.
     *
     * @throws IllegalStateException if that id is already registered
     */
    public void register(FacilityEvent event) {
        String id = Objects.requireNonNull(event, "event").id();
        if (byId.containsKey(id)) {
            throw new IllegalStateException("Event already registered: " + id);
        }
        byId.put(id, event);
    }

    /** The event with the given id, or {@code null} if none is registered. */
    public FacilityEvent get(String id) {
        return id == null ? null : byId.get(id);
    }

    /** How many events are registered. */
    public int size() {
        return byId.size();
    }

    /** Whether the catalog is empty (no event can be rolled yet). */
    public boolean isEmpty() {
        return byId.isEmpty();
    }

    /**
     * Rolls one event deterministically from a node seed, weighted by each event's
     * {@link FacilityEvent#weight()}. Returns {@code null} only when the catalog is empty (so a caller
     * can degrade gracefully — a partially-populated pool never crashes a run).
     */
    public FacilityEvent rollFor(long nodeSeed) {
        if (byId.isEmpty()) {
            return null;
        }
        List<FacilityEvent> pool = new ArrayList<>(byId.values());
        float[] weights = new float[pool.size()];
        for (int index = 0; index < pool.size(); index++) {
            weights[index] = pool.get(index).weight();
        }
        RouteRng rng = new RouteRng(GameMath.mixSeed(nodeSeed, EVENT_STREAM_SALT));
        return pool.get(GameMath.weightedChoiceIndex(weights, rng.nextFloat01()));
    }
}
