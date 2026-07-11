package ge.tbegvadze.toon3d.route;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The data-driven catalog of {@link RegionAmbience} identities (route-map order-10), keyed by
 * {@code themeId}. Mirrors the other route registries: populated once at bootstrap, then read wherever
 * a region's theming is needed — the overlay for crest / tint (order-4/5), {@link GateProfile} for the
 * gate announce flavour, and (later) the audio + red-alert systems for the ambience hooks.
 *
 * <p>Adding a region is one {@link #register} line in {@link RouteRegistries} plus its crest painter —
 * NOTHING hard-codes the region list. A {@link #fallback} keeps an unthemed theme id playable so a
 * partially-populated catalog never breaks the descent. Pure / headless — no LibGDX imports.
 */
public final class RegionAmbienceRegistry {

    // Insertion-ordered so iteration (docs, debug) is deterministic run to run.
    private final Map<String, RegionAmbience> byThemeId = new LinkedHashMap<>();
    private RegionAmbience fallback;

    /**
     * Registers a region identity under its {@link RegionAmbience#themeId()}.
     *
     * @throws IllegalStateException if that theme id is already registered
     */
    public void register(RegionAmbience ambience) {
        String themeId = Objects.requireNonNull(ambience, "ambience").themeId();
        if (byThemeId.containsKey(themeId)) {
            throw new IllegalStateException("Region ambience already registered: " + themeId);
        }
        byThemeId.put(themeId, ambience);
    }

    /**
     * Sets the fallback identity returned by {@link #getOrFallback} for an unregistered theme id.
     * Keeps an unthemed region readable (its default crest / tint) rather than throwing.
     */
    public void setFallback(RegionAmbience fallback) {
        this.fallback = fallback;
    }

    /** The identity for a theme id, or {@code null} if none is registered (no fallback applied). */
    public RegionAmbience get(String themeId) {
        return themeId == null ? null : byThemeId.get(themeId);
    }

    /** Whether a theme id has a registered identity. */
    public boolean has(String themeId) {
        return themeId != null && byThemeId.containsKey(themeId);
    }

    /**
     * The identity for a theme id, or the {@link #setFallback} default when none is registered. May
     * still be {@code null} if no fallback was set.
     */
    public RegionAmbience getOrFallback(String themeId) {
        RegionAmbience ambience = get(themeId);
        return ambience != null ? ambience : fallback;
    }

    /** How many region identities are registered. */
    public int size() {
        return byThemeId.size();
    }
}
