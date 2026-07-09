package ge.tbegvadze.toon3d.route;

import ge.tbegvadze.toon3d.level.LevelGenConfig;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The fully-resolved recipe for ONE floor, produced by a {@link NodeLevelProfile} from the node the
 * player committed to. It is the hand-off between the pure route layer and {@code World}'s existing
 * level-build machinery: {@code World} reads {@link #generatorId()} + {@link #config()} to build the
 * generator, then replays {@link #guarantees()} over the generated {@link ge.tbegvadze.toon3d.level.Level}.
 *
 * <p>Immutable value object. Pure / headless — no LibGDX imports.
 */
public final class LevelPlan {

    private final GeneratorId generatorId;
    private final LevelGenConfig config;
    private final List<GuaranteedContent> guarantees;

    /**
     * @param generatorId which registered generator builds the floor (never null)
     * @param config      the generation config handed to that generator (may be null; generators
     *                    that ignore config default internally)
     * @param guarantees  post-generation content promises; may be empty, never null. Defensively copied.
     */
    public LevelPlan(GeneratorId generatorId, LevelGenConfig config, List<GuaranteedContent> guarantees) {
        this.generatorId = Objects.requireNonNull(generatorId, "generatorId");
        this.config      = config;
        this.guarantees  = Collections.unmodifiableList(new java.util.ArrayList<>(
                Objects.requireNonNull(guarantees, "guarantees")));
    }

    /** Which registered generator builds this floor. */
    public GeneratorId generatorId() {
        return generatorId;
    }

    /** The generation config, or {@code null} to let the generator default internally. */
    public LevelGenConfig config() {
        return config;
    }

    /** Post-generation content promises, in application order. Unmodifiable; possibly empty. */
    public List<GuaranteedContent> guarantees() {
        return guarantees;
    }
}
