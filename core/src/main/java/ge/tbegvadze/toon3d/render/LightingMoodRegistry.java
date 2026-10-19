package ge.tbegvadze.toon3d.render;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Catalog of {@link LightingMood}s keyed by id, same shape as {@code route/RegionAmbienceRegistry}.
 * Populated once by {@link LightingMoods#bootstrap()}; a fallback keeps an unknown id renderable.
 * Headless, no LibGDX imports.
 */
public final class LightingMoodRegistry {

    private final Map<String, LightingMood> byId = new LinkedHashMap<>();
    private LightingMood fallback;

    /** @throws IllegalStateException if the id is already registered */
    public void register(LightingMood mood) {
        String id = Objects.requireNonNull(mood, "mood").id();
        if (byId.containsKey(id)) {
            throw new IllegalStateException("Lighting mood already registered: " + id);
        }
        byId.put(id, mood);
    }

    public void setFallback(LightingMood fallback) {
        this.fallback = fallback;
    }

    /** The mood for an id, or {@code null} (no fallback applied). */
    public LightingMood get(String id) {
        return id == null ? null : byId.get(id);
    }

    public boolean has(String id) {
        return id != null && byId.containsKey(id);
    }

    /** The mood for an id, or the fallback when unknown or null. */
    public LightingMood getOrFallback(String id) {
        LightingMood mood = get(id);
        return mood != null ? mood : fallback;
    }

    public LightingMood getFallback() {
        return fallback;
    }

    /** Registered ids in registration order (unmodifiable). */
    public Set<String> ids() {
        return Collections.unmodifiableSet(byId.keySet());
    }

    public int size() {
        return byId.size();
    }
}
