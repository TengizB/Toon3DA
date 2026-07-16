package ge.tbegvadze.toon3d.level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The registry the level generator selects rooms from (symbol/sprite-reuse order-5) — the ROOM twin of
 * {@code route/GeneratorRegistry}. Every v1 {@link RoomBlueprint} registers here via
 * {@link RoomBlueprints#bootstrap()}; the generator asks the registry for eligible rooms instead of
 * switching on a hardcoded {@code enum} ("never a switch statement").
 *
 * <p>Insertion-ordered so enumeration and seeded selection are reproducible. Duplicate id throws
 * (route-registry discipline); {@link #get(String)} throws for an unknown id. Headless — no LibGDX.
 */
public final class RoomBlueprintRegistry {

    private final Map<String, RoomBlueprint> byId = new LinkedHashMap<>();

    /** Registers a blueprint. Throws {@link IllegalStateException} on a duplicate id. */
    public void register(RoomBlueprint blueprint) {
        if (blueprint == null) {
            throw new IllegalArgumentException("blueprint must not be null");
        }
        String id = blueprint.id();
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("blueprint id must not be null or empty");
        }
        if (byId.containsKey(id)) {
            throw new IllegalStateException("duplicate RoomBlueprint id: " + id);
        }
        byId.put(id, blueprint);
    }

    /** The blueprint registered under {@code id}. Throws {@link IllegalArgumentException} if unknown. */
    public RoomBlueprint get(String id) {
        RoomBlueprint blueprint = byId.get(id);
        if (blueprint == null) {
            throw new IllegalArgumentException("no RoomBlueprint registered for id: " + id);
        }
        return blueprint;
    }

    /** Whether a blueprint is registered under {@code id}. */
    public boolean contains(String id) {
        return byId.containsKey(id);
    }

    /** Number of registered blueprints. */
    public int size() {
        return byId.size();
    }

    /** All registered blueprints, in registration order (unmodifiable). */
    public List<RoomBlueprint> all() {
        return Collections.unmodifiableList(new ArrayList<>(byId.values()));
    }

    /** All {@link RoomKind#SIGNATURE} blueprints, in registration order (unmodifiable). */
    public List<RoomBlueprint> allSignature() {
        return filterByKind(RoomKind.SIGNATURE);
    }

    /** All {@link RoomKind#GENERIC} blueprints, in registration order (unmodifiable). */
    public List<RoomBlueprint> allGeneric() {
        return filterByKind(RoomKind.GENERIC);
    }

    /**
     * The blueprints eligible for a candidate room described by {@code context}, in registration order
     * (unmodifiable). This is the seeded pick the generator draws from in order-8; order-5 only exposes
     * it and unit-tests the eligibility gates.
     */
    public List<RoomBlueprint> selectable(RoomContext context) {
        List<RoomBlueprint> eligible = new ArrayList<>();
        for (RoomBlueprint blueprint : byId.values()) {
            if (blueprint.eligible(context)) {
                eligible.add(blueprint);
            }
        }
        return Collections.unmodifiableList(eligible);
    }

    private List<RoomBlueprint> filterByKind(RoomKind kind) {
        List<RoomBlueprint> matches = new ArrayList<>();
        for (RoomBlueprint blueprint : byId.values()) {
            if (blueprint.kind() == kind) {
                matches.add(blueprint);
            }
        }
        return Collections.unmodifiableList(matches);
    }
}
