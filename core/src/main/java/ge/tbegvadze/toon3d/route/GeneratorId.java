package ge.tbegvadze.toon3d.route;

/**
 * Stable identifier for a level generator, used as the key into {@link GeneratorRegistry}.
 *
 * <p>The {@link #stableId()} string is what gets written to save files, shared seeds, and run-stats
 * logs, so it MUST never change once shipped — reordering or renaming a constant is fine as long as
 * its stable id string stays put. That keeps a run reproducible across code changes.
 *
 * <p>EXTENSIBILITY: a new generator is one new constant here (with a fresh stable id) plus one
 * {@code registry.register(...)} line in {@link RouteRegistries}. It can then appear on the map with
 * zero edits to {@code World} or the overlay — the requirement made concrete.
 */
public enum GeneratorId {
    /** Scattered rooms connected by an MST corridor network ({@code LevelGenerator}). */
    ROOMS_MST("rooms_mst"),
    /** Linear spine corridor with side rooms ({@code LinearCorridorGenerator}). */
    LINEAR_CORRIDOR("linear_corridor"),
    /** Cellular-automata organic caves ({@code CavernGenerator}). */
    CAVERN("cavern"),
    /** Bespoke boss arena ({@code BossArenaGenerator}). Not in the standard pool. */
    BOSS_ARENA("boss_arena");

    private final String stableId;

    GeneratorId(String stableId) {
        this.stableId = stableId;
    }

    /** The persisted string form. Stable across code changes for seed/run-stats reproducibility. */
    public String stableId() {
        return stableId;
    }

    /**
     * Resolves a stable id string back to its constant.
     *
     * @throws IllegalArgumentException if no generator carries that stable id
     */
    public static GeneratorId fromStableId(String stableId) {
        for (GeneratorId generatorId : values()) {
            if (generatorId.stableId.equals(stableId)) {
                return generatorId;
            }
        }
        throw new IllegalArgumentException("Unknown generator stable id: " + stableId);
    }
}
