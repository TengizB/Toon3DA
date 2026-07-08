package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.level.ILevelGenerator;
import ge.tbegvadze.toon3d.level.LevelGenConfig;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The generator-side twin of {@link NodeTypeRegistry}. Maps a {@link GeneratorId} to a
 * {@link GeneratorFactory}, replacing the old hard-coded {@code switch} in {@code World}.
 *
 * <p>Extensibility promise for GENERATORS: adding one is a single {@link #register} line in
 * {@link RouteRegistries#bootstrap()} plus (optionally) referencing its id from a node's level
 * profile. It can then appear on the map with zero edits to {@code World} or the overlay.
 *
 * <p>The {@link #standardPool()} is the set of ids eligible for ordinary COMBAT nodes. Bespoke
 * generators (e.g. {@link GeneratorId#BOSS_ARENA}) are NOT in the standard pool — they are addressed
 * by id from a level profile (order-7).
 */
public final class GeneratorRegistry {

    private final Map<GeneratorId, GeneratorFactory> factories = new EnumMap<>(GeneratorId.class);

    /**
     * Registers a factory for a generator id.
     *
     * @throws IllegalStateException if that id is already registered
     */
    public void register(GeneratorId id, GeneratorFactory factory) {
        if (factories.containsKey(id)) {
            throw new IllegalStateException("Generator already registered: " + id);
        }
        factories.put(id, factory);
    }

    /** Whether a factory has been registered for the given id. */
    public boolean isRegistered(GeneratorId id) {
        return factories.containsKey(id);
    }

    /**
     * Builds a generator by id.
     *
     * @param config optional generation config; may be {@code null} for generators that ignore it
     * @throws IllegalArgumentException if the id was never registered
     */
    public ILevelGenerator create(GeneratorId id, long seed, LevelGenConfig config) {
        GeneratorFactory factory = factories.get(id);
        if (factory == null) {
            throw new IllegalArgumentException("Generator not registered: " + id);
        }
        return factory.create(seed, config);
    }

    /**
     * The generator ids eligible for ordinary COMBAT nodes. Deliberately excludes bespoke
     * generators such as {@link GeneratorId#BOSS_ARENA}. Only ids that are actually registered are
     * returned, so the pool never yields an id {@link #create} would reject.
     *
     * @return an unmodifiable list in a stable order
     */
    public List<GeneratorId> standardPool() {
        java.util.List<GeneratorId> pool = new java.util.ArrayList<>();
        for (GeneratorId id : STANDARD_POOL_ORDER) {
            if (factories.containsKey(id)) {
                pool.add(id);
            }
        }
        return Collections.unmodifiableList(pool);
    }

    // The standard-pool membership, in a stable order for reproducible rolls. BOSS_ARENA is
    // intentionally absent — it is a bespoke generator addressed by id from a level profile.
    private static final GeneratorId[] STANDARD_POOL_ORDER = {
        GeneratorId.ROOMS_MST,
        GeneratorId.LINEAR_CORRIDOR,
        GeneratorId.CAVERN
    };
}
