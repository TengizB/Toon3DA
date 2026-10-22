package ge.tbegvadze.toon3d.tileset;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The registered region wall sets (generated-wall-art order 2), populated by
 * {@link RegionWallSets#bootstrap()}. A real set or an alias is one {@code register()} row. Headless.
 */
public final class RegionWallSetRegistry {

    private final Map<String, RegionWallSet> sets = new LinkedHashMap<>();

    public void register(RegionWallSet set) {
        if (sets.containsKey(set.id())) {
            throw new IllegalStateException("Region wall set already registered: " + set.id());
        }
        sets.put(set.id(), set);
    }

    public boolean contains(String setId) {
        return setId != null && sets.containsKey(setId);
    }

    /** The row as registered (an alias stays an alias). */
    public RegionWallSet get(String setId) {
        RegionWallSet set = sets.get(setId);
        if (set == null) throw new IllegalArgumentException("Region wall set not registered: " + setId);
        return set;
    }

    /** The REAL set {@code setId} names, following alias rows; fails loud on an unknown id or a cycle. */
    public RegionWallSet resolve(String setId) {
        RegionWallSet current = get(setId);
        for (int hop = 0; current.isAlias(); hop++) {
            if (hop > sets.size()) throw new IllegalStateException("Region wall set alias cycle at " + setId);
            current = get(current.borrowsId());
        }
        return current;
    }

    /** Fails loud when an alias borrows an unregistered set (checked once at bootstrap). */
    public void validate() {
        for (RegionWallSet set : sets.values()) resolve(set.id());
    }

    public Set<String> ids() {
        return Collections.unmodifiableSet(sets.keySet());
    }
}
