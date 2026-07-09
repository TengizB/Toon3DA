package ge.tbegvadze.toon3d.route;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The data-driven lookup of every {@link NodeLevelProfile}, keyed by its {@code profileId} string
 * (which matches {@code NodeTypeDefinition.levelProfileId()}). This is the third registry, the twin
 * of {@link NodeTypeRegistry} (identity) and {@link GeneratorRegistry} (generators): it maps a
 * node's declared profile id to the code that turns that node into a {@link LevelPlan}.
 *
 * <p>A single {@link #fallback} profile answers for any id that has not been registered yet, so the
 * game keeps building playable floors while the special profiles (order-7) are still absent. Adding a
 * special profile later is one {@link #register} line in {@link RouteRegistries#bootstrap()} — with
 * zero {@code World} edits, the extensibility promise the vision makes for node CONTENTS.
 *
 * <p>Populated once at startup; a shared instance is exposed by {@link RouteRegistries#levelProfiles()}.
 */
public final class NodeLevelProfileRegistry {

    private final Map<String, NodeLevelProfile> profiles = new HashMap<>();
    private NodeLevelProfile fallback;

    /**
     * Registers a profile under its {@link NodeLevelProfile#profileId()}.
     *
     * @throws IllegalStateException if that id is already registered
     */
    public void register(NodeLevelProfile profile) {
        String id = Objects.requireNonNull(profile, "profile").profileId();
        if (profiles.containsKey(id)) {
            throw new IllegalStateException("Level profile already registered: " + id);
        }
        profiles.put(id, profile);
    }

    /**
     * Sets the fallback profile returned for any unregistered id. Order-3 wires
     * {@link DefaultCombatProfile} here so every node type resolves to a playable floor.
     */
    public void setFallback(NodeLevelProfile fallback) {
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    /** Whether a profile has been registered for the given id (ignoring the fallback). */
    public boolean isRegistered(String profileId) {
        return profiles.containsKey(profileId);
    }

    /**
     * The profile for a given id, or the {@link #setFallback fallback} when none is registered.
     *
     * @throws IllegalStateException if the id is unregistered AND no fallback has been set
     */
    public NodeLevelProfile getOrDefault(String profileId) {
        NodeLevelProfile profile = profiles.get(profileId);
        if (profile != null) {
            return profile;
        }
        if (fallback == null) {
            throw new IllegalStateException(
                    "No level profile registered for id '" + profileId + "' and no fallback set");
        }
        return fallback;
    }
}
