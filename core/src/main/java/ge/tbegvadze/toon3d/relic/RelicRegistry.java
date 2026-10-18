package ge.tbegvadze.toon3d.relic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The relic catalog's store. Iteration order is registration order. Populated by {@link RelicCatalog#bootstrap}. */
public final class RelicRegistry {

    private final List<RelicDefinition>        ordered = new ArrayList<>();
    private final Map<String, RelicDefinition> byId    = new HashMap<>();

    /** Registers a relic; rejects a duplicate id. */
    public void register(RelicDefinition definition) {
        if (byId.containsKey(definition.id())) {
            throw new IllegalArgumentException("duplicate relic id: " + definition.id());
        }
        byId.put(definition.id(), definition);
        ordered.add(definition);
    }

    /** The relic with this id, or null. */
    public RelicDefinition get(String id) { return byId.get(id); }

    /** Every relic in registration order (unmodifiable view). */
    public List<RelicDefinition> all() { return Collections.unmodifiableList(ordered); }

    /** Every relic of one rarity, in registration order (a fresh list: call at setup, never per frame). */
    public List<RelicDefinition> allOfRarity(RelicRarity rarity) {
        List<RelicDefinition> matching = new ArrayList<>();
        for (RelicDefinition definition : ordered) {
            if (definition.rarity() == rarity) matching.add(definition);
        }
        return matching;
    }

    public int size() { return ordered.size(); }
}
