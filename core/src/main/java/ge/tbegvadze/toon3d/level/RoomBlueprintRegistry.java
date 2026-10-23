package ge.tbegvadze.toon3d.level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The room catalog's registry (the ROOM twin of {@code route/GeneratorRegistry}). Every {@link RoomBlueprint}
 * registers here via {@link RoomBlueprints#bootstrap()}; the generators read rooms from it (the special pool is
 * {@link SpecialRoomPlanner#specialPool}) instead of switching on a hardcoded {@code enum}.
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
